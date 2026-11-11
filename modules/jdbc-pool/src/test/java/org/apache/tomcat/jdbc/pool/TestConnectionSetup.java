/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.jdbc.pool;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.interceptor.ConnectionState;

/**
 * Expected functionality of the per-connection setup the pool performs
 * while a physical session is being established: the configured init SQL
 * is applied to the session, unusable configuration is normalised or
 * tolerated instead of wedging the pool, a state the driver refuses to
 * restore fails the borrow instead of silently handing out the wrong
 * state, an isolation default the driver refuses for good converges at its
 * confirmed refusal instead of looping on every borrow, one refused only for
 * the session's transient state is retried and still applies, and a borrow
 * whose confirmation cannot free the session from that state fails like any
 * other failed transaction termination, leaving the configured level
 * unsuppressed; in every case the session the application receives is in a
 * defined state.
 *
 * <p>Scenarios harvested from the retired legacy classes
 * {@code Bug54225} (blank init SQL), {@code Bug54978} (init SQL used for
 * connect validation) and {@code Bug50571} (unusable configured
 * transaction isolation with the {@code ConnectionState} interceptor) -
 * none of which ran in the old build.</p>
 */
class TestConnectionSetup extends JdbcPoolBaseTest {

    private static final String INIT_MARKER = "SET @POOL_INIT_STATE = 'applied'";

    /*
     * The Connection method the wrapping driver below should fail from with
     * a SQLException, or null to let everything through. Static, because the
     * driver is instantiated by class name and the tests alone decide when
     * the failure window opens and closes.
     */
    private static final AtomicReference<String> THROW_FROM = new AtomicReference<>();

    /*
     * Driver-level call counters, fed by the same wrapping proxy: tests
     * that must pin what a borrow did to the physical session - not just
     * what the session ended up looking like - count through these. The
     * tests below are sequential and reset what they observe.
     */
    private static final AtomicInteger ROLLBACKS = new AtomicInteger();
    private static final AtomicInteger ISOLATION_ATTEMPTS = new AtomicInteger();

    /*
     * When positive, the wrapping proxy below refuses the next that many
     * setTransactionIsolation calls with MySQL's exact SQLSTATE-class-25
     * signature of "the session is mid-transaction" and lets the later
     * ones through, as the same session does once that transaction is
     * cleared: a refusal of the session's state rather than of the level.
     */
    private static final AtomicInteger TRANSIENT_ISOLATION_REFUSALS = new AtomicInteger();

    /*
     * When set to a state setter's name, the wrapping proxy below refuses
     * calls to that method with the same class-25 signature while the
     * modelled phantom transaction below is open, and lets them through
     * once a rollback has cleared it: a refusal of the session's state
     * rather than of the value being restored.
     */
    private static final AtomicReference<String> TRANSIENT_STATE_REFUSAL = new AtomicReference<>();

    /*
     * The phantom transaction: a transaction the driver reports no other
     * way than through these refusals. The terminate-first probe reads
     * auto-commit, which stays true for it, so only a rollback clears it.
     */
    private static final AtomicBoolean PHANTOM_TRANSACTION = new AtomicBoolean();

    private int rowCount(Connection c, String table) throws Exception {
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String initMarker(Connection c) throws Exception {
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT @POOL_INIT_STATE")) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    void initSqlIsAppliedToEveryPhysicalConnection() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitSQL(INIT_MARKER);
        ds.getPoolProperties().setMaxActive(2);
        ds.createPool();

        try (Connection initial = ds.getConnection()) {
            Assertions.assertEquals("applied", initMarker(initial),
                    "the init SQL must have run on the initial connection");
        }
        awaitSessions(1);

        // a second physical connection gets the init SQL as well
        Connection secondPhysical;
        try (@SuppressWarnings("unused") Connection first = ds.getConnection()) {
            secondPhysical = ds.getConnection();
            Assertions.assertEquals(2, poolSessions(),
                    "two concurrent borrowers need two sessions");
        }
        Assertions.assertEquals("applied", initMarker(secondPhysical),
                "the init SQL must run on every session the pool establishes");
        secondPhysical.close();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void aBlankInitSqlIsNotConfiguredAtAll(String initSql) throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitSQL(initSql);
        Assertions.assertNull(ds.getPoolProperties().getInitSQL(),
                "a blank init SQL must be normalised to 'not configured'");
        ds.createPool();

        try (Connection c = ds.getConnection()) {
            Assertions.assertNull(initMarker(c), "nothing may have been executed on the session");
            c.createStatement().execute("SELECT 1");
        }
    }

    @Test
    void theInitSqlValidatesANewlyEstablishedConnection() throws Exception {
        // Documented in PoolConfiguration#setValidationQuery: "Setting an
        // initSQL will override this setting, as the init SQL will be used
        // instead of the validation query" - so for a freshly established
        // session the init SQL is the validation, and the (broken)
        // validationQuery below is never executed.
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitSQL(INIT_MARKER);
        ds.getPoolProperties().setTestOnBorrow(true);
        ds.getPoolProperties().setValidationQuery("SELECT * FROM NO_SUCH_VALIDATION_TABLE");
        ds.getPoolProperties().setValidationInterval(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.createPool();

        try (Connection c = ds.getConnection()) {
            Assertions.assertEquals("applied", initMarker(c),
                    "the configured init SQL must have validated the session");
        }
        Assertions.assertEquals(1, ds.getIdle());
    }

    @Test
    void anUnusableTransactionIsolationSettingDoesNotBlockThePool() throws Exception {
        DataSource ds = newDataSource();
        // not a JDBC isolation level at all; the pool must not strand borrowers over it
        ds.getPoolProperties().setDefaultTransactionIsolation(-55);
        ds.getPoolProperties().setJdbcInterceptors(ConnectionState.class.getName());
        ds.createPool();

        for (int i = 0; i < 2; i++) {
            try (Connection c = ds.getConnection()) {
                Assertions.assertEquals(Connection.TRANSACTION_READ_COMMITTED,
                        c.getTransactionIsolation(),
                        "the session keeps the driver's isolation level");
                Assertions.assertTrue(c.createStatement().execute("SELECT 1"),
                        "the connection must be usable despite the unusable setting");
            }
        }
        Assertions.assertEquals(1, ds.getIdle(), "the pool must still pool the connection");
    }

    /*
     * A permanently refused isolation default used to leave the pool in a
     * loop: the failed restore re-nulled the cache, so every later borrow
     * again saw a restore to do, again terminated whatever the previous
     * borrower left pending - work the doomed restore could never make right
     * - and again attempted, and logged, the same refused
     * setTransactionIsolation. The refusal is now recorded per session, for
     * the refused level (and logged there and then), so later borrows stop
     * treating the level as a restore need: nothing is terminated, attempted
     * or logged for it again, while a healthy restore still terminates as
     * ever, and a later configuration of a different level is applied as
     * usual. The honest
     * trade-off, pinned below: with the refused level as the only restore
     * need, a transaction the previous borrower left open now reaches the
     * next one - the pre-existing behavior of a pool that configures no
     * defaults at all, still covered by rollbackOnReturn or abandon handling
     * where those are configured.
     */
    @Test
    void aRefusedIsolationRestoreConvergesAfterTheFirstRefusal() throws Exception {
        DataSource ds = newDataSource();
        ds.setDriverClassName(FailingRestoreDriver.class.getName());
        ds.getPoolProperties().setJdbcInterceptors(ConnectionState.class.getName());
        // not a JDBC isolation level: H2 itself refuses it, nothing armed
        ds.getPoolProperties().setDefaultTransactionIsolation(-55);
        ds.createPool();
        String t = uniqueTable();
        try (Connection admin = adminConnection(); Statement st = admin.createStatement()) {
            st.execute("CREATE TABLE " + t + " (v INT)");
        }
        ROLLBACKS.set(0);
        ISOLATION_ATTEMPTS.set(0);

        // The refusal surfaces on this first borrow; the borrower then
        // returns the connection mid-transaction.
        Connection first = ds.getConnection();
        setSessionMarker(first, 51);
        first.setAutoCommit(false);
        try (Statement st = first.createStatement()) {
            st.executeUpdate("INSERT INTO " + t + " VALUES (1)");
        }
        first.close(); // still in a transaction
        Assertions.assertEquals(1, ISOLATION_ATTEMPTS.get(),
                "the configured level must be attempted once on the first borrow");

        // The two later borrows: pre-fix each of them rolled the abandoned
        // row away and re-attempted (and re-logged) the doomed restore; now
        // neither does, and the abandoned work survives to the new borrower.
        for (int borrow = 2; borrow <= 3; borrow++) {
            try (Connection later = ds.getConnection()) {
                Assertions.assertEquals(Integer.valueOf(51), sessionMarker(later),
                        "borrow " + borrow + " must reuse the same physical session");
                Assertions.assertEquals(Connection.TRANSACTION_READ_COMMITTED,
                        later.getTransactionIsolation(),
                        "borrow " + borrow + " must be told the session's real "
                                + "isolation level, never the refused default");
                Assertions.assertEquals(1, rowCount(later, t),
                        "borrow " + borrow + " must not terminate the transaction "
                                + "the previous borrower left open");
            }
        }
        Assertions.assertEquals(0, ROLLBACKS.get(),
                "no borrow after the refusal may roll the session back");
        Assertions.assertEquals(1, ISOLATION_ATTEMPTS.get(),
                "the refused level must not be re-attempted on later borrows");
        Assertions.assertEquals(1, poolSessions(), "the refused session stays in service");
        Assertions.assertEquals(1, ds.getIdle(), "and the pool keeps pooling it");

        // The refusal covers the refused level only: re-configuring a level
        // this session has not refused (the JMX setter just writes the pool
        // property, which the reset reads on every borrow) must be attempted
        // on the same session, and applied.
        ds.getPoolProperties().setDefaultTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        try (Connection corrected = ds.getConnection()) {
            Assertions.assertEquals(Integer.valueOf(51), sessionMarker(corrected),
                    "the re-configured level must be applied on the same physical session");
            Assertions.assertEquals(Connection.TRANSACTION_REPEATABLE_READ,
                    corrected.getTransactionIsolation(),
                    "a level the session never refused must not be suppressed by the earlier refusal");
        }
        Assertions.assertEquals(2, ISOLATION_ATTEMPTS.get(),
                "the re-configured level must be attempted exactly once");
        Assertions.assertEquals(1, ROLLBACKS.get(),
                "restoring the re-configured level must terminate the transaction left open");

        // and it sticks: the cache shows it in place, so later borrows neither
        // re-attempt nor terminate for it
        try (Connection later = ds.getConnection()) {
            Assertions.assertEquals(Integer.valueOf(51), sessionMarker(later),
                    "later borrows still reuse the same physical session");
            Assertions.assertEquals(Connection.TRANSACTION_REPEATABLE_READ,
                    later.getTransactionIsolation(), "the re-configured level must stick");
        }
        Assertions.assertEquals(2, ISOLATION_ATTEMPTS.get(),
                "the re-configured level must not be re-attempted once it is in place");
        Assertions.assertEquals(1, ROLLBACKS.get(),
                "and no borrow after the restore may terminate a transaction for it");
    }

    /*
     * Not every refusal is the level's own. MySQL (error 1568, SQLSTATE
     * 25001) and PostgreSQL refuse an isolation change while a transaction
     * is open, and the session can hold one the terminate-first probe does
     * not see: a transaction the previous borrower started with raw SQL
     * while auto-commit was still on, which the auto-commit probe therefore
     * reports as no transaction at all. Recording that one state-caused
     * refusal as a refusal of the level would suppress the configured level
     * for the lifetime of the physical session - every later borrower would
     * silently inherit a different isolation level than the pool configures.
     * The refusal is therefore confirmed - the session's transaction cleared
     * and the level attempted once more - before it may suppress anything,
     * which the refusal counter below pins: the transient refusal is retried
     * once and the configured level ends up in effect; the level then
     * converges exactly as if it had been accepted the first time, and the
     * session never carries a refusal record - a session moved off the level
     * is still restored to it on the next borrow.
     */
    @Test
    void aTransientlyRefusedIsolationRestoreIsConfirmedBeforeBeingSuppressed() throws Exception {
        DataSource ds = newDataSource();
        ds.setDriverClassName(FailingRestoreDriver.class.getName());
        ds.getPoolProperties().setJdbcInterceptors(ConnectionState.class.getName());
        // H2 defaults to READ_COMMITTED, so only SERIALIZABLE can tell
        // "the restore was applied" apart from "nothing happened"
        ds.getPoolProperties().setDefaultTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        ds.createPool();
        ROLLBACKS.set(0);
        ISOLATION_ATTEMPTS.set(0);
        TRANSIENT_ISOLATION_REFUSALS.set(1);
        try {
            // The first borrow: the restore hits the phantom open
            // transaction and is refused; the reset clears the session's
            // transaction and confirms the refusal is the level's own -
            // here it is not, and the retry applies the level.
            try (Connection first = ds.getConnection()) {
                setSessionMarker(first, 61);
                Assertions.assertEquals(Connection.TRANSACTION_SERIALIZABLE,
                        first.getTransactionIsolation(),
                        "a transiently refused restore must still deliver the "
                                + "configured level on the same borrow");
            }
            Assertions.assertEquals(2, ISOLATION_ATTEMPTS.get(),
                    "the transient refusal must be confirmed by exactly one retry");
            Assertions.assertEquals(1, ROLLBACKS.get(),
                    "the confirmation must first clear the transaction the probe missed");

            // A transient refusal left nothing recorded: a later borrow
            // that finds the level in place neither re-attempts nor
            // terminates anything.
            try (Connection later = ds.getConnection()) {
                Assertions.assertEquals(Integer.valueOf(61), sessionMarker(later),
                        "the later borrow must reuse the same physical session");
                Assertions.assertEquals(Connection.TRANSACTION_SERIALIZABLE,
                        later.getTransactionIsolation(),
                        "the applied level must stand on later borrows");
            }
            Assertions.assertEquals(2, ISOLATION_ATTEMPTS.get(),
                    "an applied level must not be re-attempted on later borrows");
            Assertions.assertEquals(1, ROLLBACKS.get(),
                    "and no borrow after the applied level may terminate a transaction for it");

            // And no refusal was recorded for this session: a session moved
            // off the configured level is restored to it like on a session
            // that never refused anything. A recorded refusal would suppress
            // this restore and leave the borrower at READ_COMMITTED.
            try (Connection moved = ds.getConnection()) {
                moved.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            }
            // The borrower's own write above is also a driver call; the
            // restore attempts below are counted against that baseline.
            int attemptsBeforeRestore = ISOLATION_ATTEMPTS.get();
            try (Connection restored = ds.getConnection()) {
                Assertions.assertEquals(Integer.valueOf(61), sessionMarker(restored),
                        "the restore must run on the same physical session");
                Assertions.assertEquals(Connection.TRANSACTION_SERIALIZABLE,
                        restored.getTransactionIsolation(),
                        "a state-caused refusal must not suppress the configured "
                                + "level on a later borrow");
            }
            Assertions.assertEquals(attemptsBeforeRestore + 1, ISOLATION_ATTEMPTS.get(),
                    "the configured level must be restored after a transient refusal");
            Assertions.assertEquals(1, ROLLBACKS.get(),
                    "and that restore must not terminate a transaction");
        } finally {
            TRANSIENT_ISOLATION_REFUSALS.set(0);
        }
    }

    /*
     * The confirmation only earns its record when the session actually got
     * freed of the state the refusal blamed. If the clearing rollback itself
     * fails, the transaction may still be open and the level was never
     * attempted on a freed session: recording that as the level's own would
     * suppress the configured level without any confirmation, and - worse -
     * keep in service a session whose transaction state is unknown, the very
     * thing the terminate-first rollback above fails the borrow for. The
     * borrow must fail on that same ground, the session retire with it, and
     * the never-confirmed refusal must leave no record: the replacement
     * session gets its ordinary attempt of the configured level.
     */
    @Test
    void aFailedConfirmationRollbackFailsTheBorrowAndRetiresTheSession() throws Exception {
        DataSource ds = newDataSource();
        ds.setDriverClassName(FailingRestoreDriver.class.getName());
        ds.getPoolProperties().setJdbcInterceptors(ConnectionState.class.getName());
        ds.getPoolProperties().setDefaultTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        ds.createPool();
        ROLLBACKS.set(0);
        ISOLATION_ATTEMPTS.set(0);
        TRANSIENT_ISOLATION_REFUSALS.set(1);
        THROW_FROM.set("rollback");
        SQLException borrowFailure;
        try {
            borrowFailure = Assertions.assertThrows(SQLException.class, ds::getConnection,
                    "a borrow whose confirmation rollback failed must not hand over a "
                            + "session whose transaction state is unknown");
        } finally {
            THROW_FROM.set(null);
            TRANSIENT_ISOLATION_REFUSALS.set(0);
        }
        Assertions.assertTrue(borrowFailure.getMessage().contains(ConnectionState.class.getName()),
                "the borrow failure must name the failing interceptor, but was: " + borrowFailure);
        Throwable terminateFailure = borrowFailure.getCause();
        Assertions.assertInstanceOf(IllegalStateException.class, terminateFailure,
                "the reset must report the failed transaction termination through the "
                        + "pool's discard path");
        Assertions.assertInstanceOf(SQLException.class, terminateFailure.getCause(),
                "the driver's rollback failure must survive as the cause");
        Assertions.assertEquals(1, ISOLATION_ATTEMPTS.get(),
                "the level must not be attempted again once the transaction could not "
                        + "be cleared");

        // the unknown-state session retired, not kept in service
        awaitSessions(0);
        Assertions.assertEquals(0, ds.getIdle());
        Assertions.assertEquals(0, ds.getSize());

        // and the unconfirmed refusal recorded nothing: the replacement
        // session is served the configured level like any first borrow
        try (Connection replacement = ds.getConnection()) {
            Assertions.assertEquals(Connection.TRANSACTION_SERIALIZABLE,
                    replacement.getTransactionIsolation(),
                    "a failed confirmation must not suppress the configured level on "
                            + "the replacement session");
        }
        Assertions.assertEquals(2, ISOLATION_ATTEMPTS.get(),
                "the replacement session must get exactly its one restore attempt");
    }

    /*
     * A class-25 refusal that repeats on the session freed of the state it
     * blames does not confirm the level's own refusal: the freed session is
     * still answering "not while a transaction is open", so what will not
     * clear is exactly the transaction the refusal blamed. Recording that
     * repeat as the level's permanent refusal let the borrow be served on a
     * session silently holding a different level than the pool configures,
     * and every later borrower inherited it while the pool believed the
     * configured level refused and accepted - a level the application could
     * still apply proves the record false. An unclearable transaction is
     * also the one state the terminate-first rollback above never hands to a
     * borrower. The repeat must therefore fail the borrow, retire the
     * session, and leave the level unrecorded.
     */
    @Test
    void aRepeatedlyRefusedIsolationRestoreFailsTheBorrowAndRecordsNothing() throws Exception {
        DataSource ds = newDataSource();
        ds.setDriverClassName(FailingRestoreDriver.class.getName());
        ds.getPoolProperties().setJdbcInterceptors(ConnectionState.class.getName());
        ds.getPoolProperties().setDefaultTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        ds.createPool();
        ROLLBACKS.set(0);
        ISOLATION_ATTEMPTS.set(0);

        // First borrow: the configured level is applied, then the
        // application moves the session off it and returns it.
        try (Connection first = ds.getConnection()) {
            first.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        }
        Assertions.assertEquals(2, ISOLATION_ATTEMPTS.get(),
                "the restore must have run once, the application's write once");

        // Second borrow: both the restore and its retry on the session
        // freed by the confirming rollback are refused with the
        // transaction-state signature - the blamed state will not clear.
        TRANSIENT_ISOLATION_REFUSALS.set(2);
        SQLException borrowFailure;
        try {
            borrowFailure = Assertions.assertThrows(SQLException.class, ds::getConnection,
                    "a restore that is still refused as mid-transaction on a session "
                            + "freed of its transaction must not hand over the session");
        } finally {
            TRANSIENT_ISOLATION_REFUSALS.set(0);
        }
        Assertions.assertTrue(borrowFailure.getMessage().contains(ConnectionState.class.getName()),
                "the borrow failure must name the failing interceptor, but was: " + borrowFailure);
        Throwable terminationFailure = borrowFailure.getCause();
        Assertions.assertInstanceOf(IllegalStateException.class, terminationFailure,
                "the reset must report the unclearable transaction through the pool's "
                        + "discard path, not record the refusal");
        Assertions.assertTrue(terminationFailure.getMessage().contains("pending transaction"),
                "the failure must be reported as the transaction termination it is, "
                        + "but was: " + terminationFailure);
        Assertions.assertInstanceOf(SQLException.class, terminationFailure.getCause(),
                "the repeating refusal must survive as the cause");
        Assertions.assertEquals(4, ISOLATION_ATTEMPTS.get(),
                "exactly the restore and its one confirmation attempt");
        Assertions.assertEquals(1, ROLLBACKS.get(),
                "the confirmation must have rolled the session back once");

        // the unclearable-state session retired, not kept in service
        awaitSessions(0);
        Assertions.assertEquals(0, ds.getIdle());
        Assertions.assertEquals(0, ds.getSize());

        // and the unconfirmed refusal recorded nothing: the replacement
        // session is served the configured level like any first borrow
        try (Connection replacement = ds.getConnection()) {
            Assertions.assertEquals(Connection.TRANSACTION_SERIALIZABLE,
                    replacement.getTransactionIsolation(),
                    "a repeated state refusal must not suppress the configured level "
                            + "as a permanent one");
        }
        Assertions.assertEquals(5, ISOLATION_ATTEMPTS.get(),
                "the replacement session must get exactly its one restore attempt");
    }

    /*
     * The read-only, auto-commit and catalog defaults the ConnectionState
     * interceptor restores on every borrow. Unlike the isolation level above,
     * a driver that refuses one of these leaves the session KNOWN to sit in a
     * state the pool was configured never to hand out - swallowing that
     * failure - the log-and-ignore of an unusable configured default that
     * Bugzilla #50571 reports - delivered the wrong state to the application
     * in silence. H2 accepts any catalog argument without a murmur, so the
     * refusal is injected through the wrapping driver below.
     */
    static Stream<Arguments> refusedRestores() {
        return Stream.of(
                Arguments.of("setCatalog", "no_such_catalog",
                        (Consumer<PoolConfiguration>) p -> p.setDefaultCatalog("no_such_catalog")),
                Arguments.of("setReadOnly", "true",
                        (Consumer<PoolConfiguration>) p -> p.setDefaultReadOnly(Boolean.TRUE)),
                Arguments.of("setAutoCommit", "false",
                        (Consumer<PoolConfiguration>) p -> p.setDefaultAutoCommit(Boolean.FALSE)));
    }

    @ParameterizedTest(name = "{0}({1})")
    @MethodSource("refusedRestores")
    void aRefusedStateRestoreFailsTheBorrowAndRetiresTheConnection(
            String method, String value, Consumer<PoolConfiguration> configureDefault) throws Exception {
        DataSource ds = newDataSource();
        ds.setDriverClassName(FailingRestoreDriver.class.getName());
        ds.getPoolProperties().setJdbcInterceptors(ConnectionState.class.getName());
        configureDefault.accept(ds.getPoolProperties());
        ds.createPool();
        awaitSessions(1);

        THROW_FROM.set(method);
        SQLException borrowFailure;
        try {
            borrowFailure = Assertions.assertThrows(SQLException.class, ds::getConnection,
                    "a borrow whose configured default could not be restored must fail");
        } finally {
            THROW_FROM.set(null);
        }
        Assertions.assertTrue(borrowFailure.getMessage().contains(ConnectionState.class.getName()),
                "the borrow failure must name the failing interceptor, but was: " + borrowFailure);
        Throwable restoreFailure = borrowFailure.getCause();
        Assertions.assertInstanceOf(IllegalStateException.class, restoreFailure,
                "the reset must report the failed restore through the pool's discard path");
        Assertions.assertTrue(restoreFailure.getMessage().contains(value),
                "the restore failure must name the configured value '" + value
                        + "', but was: " + restoreFailure);
        Assertions.assertInstanceOf(SQLException.class, restoreFailure.getCause(),
                "the driver's refusal must survive as the cause");

        // the wrong-state session was retired, and with it the pool kept
        // neither a misconfigured session nor the slot it occupied
        awaitSessions(0);
        Assertions.assertEquals(0, ds.getIdle());
        Assertions.assertEquals(0, ds.getActive());
        Assertions.assertEquals(0, ds.getSize());

        // one failed borrow does not wedge the pool: the next one is served
        // by a fresh session
        try (Connection again = ds.getConnection()) {
            Assertions.assertTrue(again.createStatement().execute("SELECT 1"),
                    "the replacement connection must work");
        }
        awaitSessions(1);
    }

    /*
     * A class-25 refusal says "not while a transaction is open", and that
     * transaction can be one the terminate-first probe cannot see: a
     * transaction the previous borrower started with raw SQL while
     * auto-commit was on, which the auto-commit probe therefore reports as
     * no transaction at all. Taking such a refusal at face value - failing
     * the borrow and retiring the session below - destroyed a healthy,
     * correctly-restorable connection over a state that this very reset
     * exists to clear: a rollback, which the major drivers tolerate even in
     * auto-commit mode, frees the session and the same restore then
     * succeeds. Every restore refusal that blames the session's transaction
     * state is therefore confirmed against a session freed of it first; the
     * rollback counter below pins the single confirming rollback, and the
     * session identity that the session stays in service.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedRestores")
    void aTransientlyRefusedStateRestoreIsConfirmedOnAFreedSession(
            String method, String value, Consumer<PoolConfiguration> configureDefault) throws Exception {
        DataSource ds = newDataSource();
        ds.setDriverClassName(FailingRestoreDriver.class.getName());
        ds.getPoolProperties().setJdbcInterceptors(ConnectionState.class.getName());
        configureDefault.accept(ds.getPoolProperties());
        ds.createPool();
        awaitSessions(1);
        ROLLBACKS.set(0);
        TRANSIENT_STATE_REFUSAL.set(method);
        try {
            // First borrow: the configured default is applied, the
            // application moves the session off it, and a phantom
            // transaction is left open behind the handle.
            Connection first = ds.getConnection();
            setSessionMarker(first, 71);
            moveOffConfiguredDefault(first, method, value);
            PHANTOM_TRANSACTION.set(true);
            first.close();
            Assertions.assertEquals(0, ROLLBACKS.get(),
                    "the terminate-first probe may not roll back: the driver "
                            + "reports auto-commit true for the phantom transaction");

            // Second borrow: the restore hits the phantom transaction and
            // is refused; the confirmation clears it and the freed session
            // accepts the restore, so the borrow is served on the same
            // session instead of failing over a state the reset clears.
            try (Connection second = ds.getConnection()) {
                Assertions.assertEquals(Integer.valueOf(71), sessionMarker(second),
                        "the confirmed restore must keep the session in service");
                assertConfiguredDefault(second, method, value);
            }
            Assertions.assertEquals(1, ROLLBACKS.get(),
                    "the confirmation must clear the phantom transaction with exactly one rollback");
            Assertions.assertEquals(1, ds.getIdle(), "and the session must be back in the idle pool");

            // The confirmed restore cached the state: a later borrow neither
            // restores nor terminates anything for it.
            try (Connection later = ds.getConnection()) {
                Assertions.assertEquals(Integer.valueOf(71), sessionMarker(later),
                        "the later borrow must reuse the same physical session");
                assertConfiguredDefault(later, method, value);
            }
            Assertions.assertEquals(1, ROLLBACKS.get(),
                    "an applied state must not make a later borrow terminate anything");
        } finally {
            TRANSIENT_STATE_REFUSAL.set(null);
            PHANTOM_TRANSACTION.set(false);
        }
    }

    /* Moves the session off the configured default the test exercises. */
    private static void moveOffConfiguredDefault(Connection c, String method, String value) throws SQLException {
        switch (method) {
            case "setReadOnly":
                c.setReadOnly(!Boolean.parseBoolean(value));
                break;
            case "setAutoCommit":
                c.setAutoCommit(!Boolean.parseBoolean(value));
                break;
            case "setCatalog":
                c.setCatalog("some_other_catalog");
                break;
            default:
                throw new IllegalArgumentException(method);
        }
    }

    /* Pins that the configured default, not the moved-off one, is in effect. */
    private static void assertConfiguredDefault(Connection c, String method, String value) throws SQLException {
        switch (method) {
            case "setReadOnly":
                Assertions.assertEquals(Boolean.valueOf(value), Boolean.valueOf(c.isReadOnly()),
                        "the restored read-only state must be the configured one");
                break;
            case "setAutoCommit":
                Assertions.assertEquals(Boolean.valueOf(value), Boolean.valueOf(c.getAutoCommit()),
                        "the restored auto-commit state must be the configured one");
                break;
            case "setCatalog":
                Assertions.assertEquals(value, c.getCatalog(),
                        "the restored catalog must be the configured one");
                break;
            default:
                throw new IllegalArgumentException(method);
        }
    }

    /**
     * Driver handing out the real H2 connections wrapped in a proxy that
     * fails the currently armed method with a SQLException, as a real driver
     * refuses a catalog it cannot select or a state it cannot apply, refuses
     * the armed number of isolation attempts as a mid-transaction refusal
     * would, and that counts the {@code rollback()} and
     * {@code setTransactionIsolation()} calls reaching the driver.
     * Configured through {@code driverClassName}, so it must stay public
     * with a no-arg constructor.
     */
    public static class FailingRestoreDriver implements Driver {

        private final Driver delegate = new org.h2.Driver();

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            Connection connection = delegate.connect(url, info);
            if (connection == null) {
                return null;
            }
            return (Connection) Proxy.newProxyInstance(
                    FailingRestoreDriver.class.getClassLoader(),
                    new Class<?>[] { Connection.class },
                    new FailingHandler(connection));
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return delegate.acceptsURL(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return delegate.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return delegate.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return delegate.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return delegate.jdbcCompliant();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }
    }

    /*
     * Fails the armed method by name, refuses the armed number of
     * setTransactionIsolation calls with a state-caused SQLSTATE 25001,
     * refuses calls to the armed state setter with the same signature while
     * the modelled phantom transaction is open, and counts the rollback()
     * and setTransactionIsolation() calls; delegates everything else to H2.
     */
    private static class FailingHandler implements InvocationHandler {

        private final Connection vendor;

        FailingHandler(Connection vendor) {
            this.vendor = vendor;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals(THROW_FROM.get())) {
                throw new SQLException("driver refuses " + method.getName() + " with these arguments");
            }
            if (PHANTOM_TRANSACTION.get() && method.getName().equals(TRANSIENT_STATE_REFUSAL.get())) {
                throw new SQLException(
                        "Cannot change the session state while a transaction is in progress",
                        "25001", 1568);
            }
            if ("rollback".equals(method.getName())) {
                ROLLBACKS.incrementAndGet();
                if (PHANTOM_TRANSACTION.getAndSet(false)) {
                    // The phantom transaction is a modelled driver quirk,
                    // not a real H2 transaction: clearing the flag is all
                    // this rollback has to do, and the call must not reach
                    // the real driver, which sits in auto-commit mode.
                    return null;
                }
            } else if ("setTransactionIsolation".equals(method.getName())) {
                ISOLATION_ATTEMPTS.incrementAndGet();
                if (TRANSIENT_ISOLATION_REFUSALS.get() > 0) {
                    TRANSIENT_ISOLATION_REFUSALS.decrementAndGet();
                    throw new SQLException(
                            "Transaction characteristics can't be changed while a transaction is in progress",
                            "25001", 1568);
                }
            }
            try {
                return method.invoke(vendor, args);
            } catch (InvocationTargetException x) {
                throw x.getCause();
            }
        }
    }
}
