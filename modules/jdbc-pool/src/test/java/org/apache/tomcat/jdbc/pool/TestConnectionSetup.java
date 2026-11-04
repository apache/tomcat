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
 * first refusal instead of looping on every borrow, and the session the
 * application receives is in a defined state.
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

    /**
     * Driver handing out the real H2 connections wrapped in a proxy that
     * fails the currently armed method with a SQLException, as a real driver
     * refuses a catalog it cannot select or a state it cannot apply, and
     * that counts the {@code rollback()} and {@code setTransactionIsolation()}
     * calls reaching the driver. Configured through {@code driverClassName},
     * so it must stay public with a no-arg constructor.
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
     * Fails the armed method by name and counts the rollback() and
     * setTransactionIsolation() calls; delegates everything else to H2.
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
            if ("rollback".equals(method.getName())) {
                ROLLBACKS.incrementAndGet();
            } else if ("setTransactionIsolation".equals(method.getName())) {
                ISOLATION_ATTEMPTS.incrementAndGet();
            }
            try {
                return method.invoke(vendor, args);
            } catch (InvocationTargetException x) {
                throw x.getCause();
            }
        }
    }
}
