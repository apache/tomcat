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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.interceptor.StatementFinalizer;

/**
 * Expected functionality of the transaction that {@code initSQL} leaves
 * open when the pool runs with auto commit off (Bugzilla 56586): the init
 * SQL is not a health check but the pool's own deliberate initialization of
 * a physical session it has just established, and its documented purpose is
 * to leave effects that persist for the connection's lifetime. So the
 * transaction it executes into must be terminated by commit at the init-time
 * validation, while every genuine read-only validation - the validation
 * query or {@code isValid()} at connect, borrow, return or idle - must still
 * terminate any open transaction by rollback, never committing work a
 * previous borrower abandoned.
 *
 * <p>The oracles are the committed data observed from an admin connection
 * outside the pool's sessions, and the {@code commit()}/rollback() calls
 * reaching the driver: an initialization that was rolled back again, and a
 * health check that committed somebody's work, both fail here. A plain
 * {@code SET} session statement is pinned as well - on H2 it survives a
 * rollback regardless, which is why the pre-existing session-variable init
 * SQL tests never noticed the missing commit.</p>
 */
class TestInitSqlCommittedAtInitialization extends JdbcPoolBaseTest {

    private static final String INIT_MARKER = "SET @POOL_INIT_STATE = 'applied'";

    /*
     * The Connection method the wrapping driver below should fail from with
     * a RuntimeException, or null to let everything through. Static, because
     * the driver is instantiated by class name and the tests alone decide
     * when the failure window opens and closes.
     */
    private static final AtomicReference<String> FAIL_FROM = new AtomicReference<>();

    /*
     * Driver-level commit()/rollback() counters. The tests are sequential
     * and reset what they observe.
     */
    private static final AtomicInteger COMMITS = new AtomicInteger();
    private static final AtomicInteger ROLLBACKS = new AtomicInteger();

    private static void resetCounters() {
        COMMITS.set(0);
        ROLLBACKS.set(0);
        FAIL_FROM.set(null);
    }

    private static void resetCommitCounter() {
        COMMITS.set(0);
    }

    /*
     * A pool on the counting driver with auto commit off and no state
     * interceptor, so connect() applies the auto commit itself and the init
     * SQL executes into an open transaction. The sweeper is off: these tests
     * park idle connections and drive the validations themselves.
     */
    private DataSource autoCommitOffPool(String initSql) {
        DataSource ds = newDataSource();
        ds.setDriverClassName(InitSqlProbingDriver.class.getName());
        // a chain without the state interceptor, so connect() applies the
        // auto commit state itself and the init SQL executes into an open
        // transaction on every (re)connect
        ds.getPoolProperties().setJdbcInterceptors(StatementFinalizer.class.getName());
        ds.getPoolProperties().setDefaultAutoCommit(Boolean.FALSE);
        ds.getPoolProperties().setInitSQL(initSql);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(0);
        ds.getPoolProperties().setValidationInterval(0);
        return ds;
    }

    private String createTable(String table) throws SQLException {
        try (Connection admin = adminConnection(); Statement st = admin.createStatement()) {
            st.execute("CREATE TABLE " + table + " (v INT)");
        }
        return table;
    }

    private void createUser(String user, String grantTable) throws SQLException {
        try (Connection admin = adminConnection(); Statement st = admin.createStatement()) {
            st.execute("CREATE USER " + user + " PASSWORD 'secret'");
            st.execute("GRANT SELECT, INSERT ON " + grantTable + " TO " + user);
        }
    }

    private int rowCount(Connection c, String table) throws SQLException {
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String initMarker(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT @POOL_INIT_STATE")) {
            rs.next();
            return rs.getString(1);
        }
    }

    /* The pooled connection behind a borrowed handle. */
    private static PooledConnection pooled(Connection borrowed) {
        return interceptorOf(borrowed, ProxyConnection.class).getConnection();
    }

    @Test
    void theInitSqlDmlPersistsForTheFirstBorrowerWhenAutoCommitIsOff() throws Exception {
        resetCounters();
        String table = createTable(uniqueTable());
        DataSource ds = autoCommitOffPool("INSERT INTO " + table + " VALUES (1)");
        ds.createPool();

        try (Connection borrower = ds.getConnection()) {
            Assertions.assertFalse(borrower.getAutoCommit(),
                    "the first borrower must see the configured auto commit");
            Assertions.assertEquals(1, rowCount(borrower, table),
                    "the first borrower must find the initialization in effect");
        }
        // Observed from outside the pool's session: the effect must be
        // committed, not merely visible to the session that wrote it.
        Assertions.assertEquals(1, adminRowCount(table),
                "the init SQL must persist for the connection's lifetime, not be "
                        + "rolled back by the init-time validation");
        Assertions.assertEquals(1, COMMITS.get(),
                "the pool must terminate its own initialization transaction by commit");
    }

    @Test
    void theInitSqlDmlPersistsWhenAutoCommitIsOn() throws Exception {
        resetCounters();
        String table = createTable(uniqueTable());
        DataSource ds = autoCommitOffPool("INSERT INTO " + table + " VALUES (1)");
        ds.getPoolProperties().setDefaultAutoCommit(Boolean.TRUE);
        ds.createPool();

        try (Connection borrower = ds.getConnection()) {
            Assertions.assertTrue(borrower.getAutoCommit(),
                    "the first borrower must see the configured auto commit");
        }
        Assertions.assertEquals(1, adminRowCount(table),
                "the auto-committed init SQL must persist as it always did");
        Assertions.assertEquals(0, COMMITS.get(),
                "with auto commit on there is nothing to commit and nothing may be rolled back");
        Assertions.assertEquals(0, ROLLBACKS.get(),
                "with auto commit on there is no transaction to terminate");
    }

    @Test
    void theInitSqlDmlPersistsAfterAReconnectAtBorrow() throws Exception {
        resetCounters();
        String table = createTable(uniqueTable());
        DataSource ds = autoCommitOffPool("INSERT INTO " + table + " VALUES (1)");
        // A changed identity forces the borrow's reconnect fallback, which
        // re-establishes the physical session without waiting on any timer;
        // it is the same reconnect block a maxAge expiry drives.
        ds.getPoolProperties().setAlternateUsernameAllowed(true);
        createUser("re_borrower", table);
        ds.createPool();

        // The init SQL also runs on every reconnect that re-establishes the
        // physical session (the credential change here); the same rule must
        // hold there, or the initialization silently disappears mid-life.
        Connection first = ds.getConnection();
        setSessionMarker(first, 61);
        first.close();

        try (Connection second = ds.getConnection("re_borrower", "secret")) {
            Assertions.assertNull(sessionMarker(second),
                    "the re-borrowed session must have been replaced by a reconnect");
        }
        Assertions.assertEquals(2, adminRowCount(table),
                "the re-established session's init SQL must persist just like the first session's");
    }

    @Test
    void aSessionSettingMadeByTheInitSqlIsVisibleToTheFirstBorrower() throws Exception {
        resetCounters();
        DataSource ds = autoCommitOffPool(INIT_MARKER);
        ds.createPool();

        // H2 session variables are not transactional, so this holds whether
        // or not the initialization transaction is terminated correctly - it
        // is exactly why session-setting-flavoured init SQL hid the missing
        // commit from the pre-existing tests.
        try (Connection borrower = ds.getConnection()) {
            Assertions.assertEquals("applied", initMarker(borrower),
                    "the init SQL must have run on the session the borrower gets");
        }
    }

    @Test
    void borrowValidationDoesNotCommitAbandonedWorkWhenInitSqlIsConfigured() throws Exception {
        resetCounters();
        String table = createTable(uniqueTable());
        DataSource ds = autoCommitOffPool("INSERT INTO " + table + " VALUES (1)");
        ds.getPoolProperties().setTestOnBorrow(true);
        ds.getPoolProperties().setValidationQuery("SELECT 1");
        ds.createPool();
        // one committed row so far: the pool's own initialization
        Assertions.assertEquals(1, COMMITS.get());

        Connection first = ds.getConnection();
        setSessionMarker(first, 51);
        try (Statement st = first.createStatement()) {
            st.executeUpdate("INSERT INTO " + table + " VALUES (1)");
        }
        first.close(); // returned while still in its transaction

        resetCommitCounter();
        try (Connection second = ds.getConnection()) {
            // The borrow's validation runs the validation query, not the init
            // SQL: the same pool that commits its own initialization must
            // still discard a previous borrower's pending work.
            Assertions.assertEquals(Integer.valueOf(51), sessionMarker(second),
                    "the same physical session must have been reused");
            Assertions.assertEquals(1, rowCount(second, table),
                    "the next borrower must not find the abandoned row");
        }
        Assertions.assertEquals(0, COMMITS.get(),
                "the borrow validation must never commit, not even in a pool "
                        + "configured with init SQL");
        Assertions.assertEquals(1, adminRowCount(table),
                "only the committed initialization may survive");
    }

    @Test
    void theValidationQueryAtConnectStillTerminatesTheSessionTransactionByRollback() throws Exception {
        resetCounters();
        String table = createTable(uniqueTable());
        DataSource ds = autoCommitOffPool(null); // no init SQL: a plain health check
        ds.getPoolProperties().setTestOnConnect(true);
        ds.getPoolProperties().setValidationQuery("SELECT 1");
        ds.createPool();

        Connection borrower = ds.getConnection();
        setSessionMarker(borrower, 62);
        try (Statement st = borrower.createStatement()) {
            st.executeUpdate("INSERT INTO " + table + " VALUES (1)");
        }

        // The init action with no init SQL configured is still a read-only
        // health check: it must terminate the open transaction by rollback,
        // never by commit.
        Assertions.assertTrue(pooled(borrower).validate(PooledConnection.VALIDATE_INIT),
                "the validation must pass on a live session");
        Assertions.assertEquals(0, COMMITS.get(),
                "a validation query must not commit, whatever the action");
        Assertions.assertTrue(ROLLBACKS.get() > 0,
                "the validation must terminate the transaction by rollback");

        borrower.close();
        Assertions.assertEquals(0, adminRowCount(table),
                "the pending work must not have survived the health check");
    }

    @Test
    void aFailedCommitOfTheInitSqlIsAFailedInitializationThatDoesNotThrow() throws Exception {
        resetCounters();
        String table = createTable(uniqueTable());
        DataSource ds = autoCommitOffPool("INSERT INTO " + table + " VALUES (1)");
        ds.createPool();
        try (Connection borrower = ds.getConnection()) {
            Assertions.assertEquals(1, rowCount(borrower, table));
            FAIL_FROM.set("commit");
            try {
                // A driver that cannot commit leaves the session in an
                // unknown state: like a failed rollback, this is a failed
                // validation reported as false - it must not escape from
                // validate() unchecked and strand the connection.
                Assertions.assertFalse(pooled(borrower).validate(PooledConnection.VALIDATE_INIT),
                        "a commit that the driver refuses must fail the initialization");
            } finally {
                FAIL_FROM.set(null);
            }
        }
        Assertions.assertEquals(1, adminRowCount(table),
                "the init SQL execution that could not be committed must not leave its "
                        + "effects behind, only the one committed at creation remains");
        Assertions.assertEquals(1, ds.getPool().getSize(),
                "a direct failed validation must not strand the pooled connection by itself");
    }

    @Test
    void anUnreportableAutoCommitStateFailsTheInitCommitAndTheHelperOwnsIt() throws Exception {
        resetCounters();
        String table = createTable(uniqueTable());
        DataSource ds = autoCommitOffPool("INSERT INTO " + table + " VALUES (1)");
        ds.createPool();
        // the initialization at creation stands committed, and it is the
        // only transaction the driver was ever asked to end
        Assertions.assertEquals(1, adminRowCount(table));
        Assertions.assertEquals(1, COMMITS.get());

        try (ValidationLogCapture logs = new ValidationLogCapture()) {
            Connection borrower = ds.getConnection();
            try {
                FAIL_FROM.set("getAutoCommit");
                try {
                    // The guard that decides whether to commit must fail
                    // exactly like the commit it guards: a driver that
                    // cannot even report the transaction state yields a
                    // failed validation reported as false - never an
                    // unchecked throw escaping validate(), and never one
                    // that only the caller's catch happens to absorb,
                    // which would strand the connection wherever a caller
                    // terminates the transaction from a finally block
                    Assertions.assertFalse(pooled(borrower).validate(PooledConnection.VALIDATE_INIT),
                            "a transaction state the driver cannot report must fail the initialization");
                } finally {
                    FAIL_FROM.set(null);
                }
            } finally {
                borrower.close();
            }
            // Matching the helper's own log message is what pins which
            // helper owned the failure: only the commit helper may report
            // this, the armed getAutoCommit() call the rollback helper
            // later makes is its own (also warning) record
            logs.assertWarnedAbout("Failed to commit transaction", "boom from getAutoCommit()");
        }
        Assertions.assertEquals(1, COMMITS.get(),
                "the commit itself must never be reached when its guard fails");
        Assertions.assertEquals(1, adminRowCount(table),
                "the init SQL that could be neither committed nor rolled back must have stayed"
                        + " uncommitted, leaving only the row committed at creation");
        Assertions.assertEquals(1, ds.getPool().getSize(),
                "a direct failed validation must not strand the pooled connection by itself");
    }

    @Test
    void aFailedCommitOfTheInitSqlStopsThePoolFromStarting() throws Exception {
        resetCounters();
        String table = createTable(uniqueTable());
        DataSource ds = autoCommitOffPool("INSERT INTO " + table + " VALUES (1)");

        FAIL_FROM.set("commit");
        SQLException sqlX;
        try {
            // The unchecked driver failure must reach the application as the
            // checked SQLException a failed pool start already conveys.
            sqlX = Assertions.assertThrows(SQLException.class, ds::createPool);
        } finally {
            FAIL_FROM.set(null);
        }
        Assertions.assertTrue(sqlX.getMessage().contains("Validation Query Failed"),
                "expected the initialization to fail the connect validation, got: " + sqlX.getMessage());
        awaitSessions(0);

        // nothing stranded: the same pool works once the driver cooperates
        DataSource working = autoCommitOffPool("INSERT INTO " + table + " VALUES (1)");
        working.createPool();
        try (Connection borrower = working.getConnection()) {
            Assertions.assertEquals(1, rowCount(borrower, table));
        }
        Assertions.assertEquals(1, adminRowCount(table),
                "the retried initialization must persist");
    }

    /**
     * Driver handing out the real H2 connections wrapped in a proxy that
     * counts the {@code commit()} and {@code rollback()} calls reaching the
     * driver and can fail a named method with a RuntimeException.
     * Configured through {@code driverClassName}, so it must stay public
     * with a no-arg constructor.
     */
    public static class InitSqlProbingDriver implements Driver {

        private final Driver delegate = new org.h2.Driver();

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            Connection connection = delegate.connect(url, info);
            if (connection == null) {
                return null;
            }
            return (Connection) Proxy.newProxyInstance(
                    InitSqlProbingDriver.class.getClassLoader(),
                    new Class<?>[] { Connection.class },
                    new ProbingHandler(connection));
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

    /* Counts transaction terminations; fails the armed method by name. */
    private static class ProbingHandler implements InvocationHandler {

        private final Connection vendor;

        ProbingHandler(Connection vendor) {
            this.vendor = vendor;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String armed = FAIL_FROM.get();
            if (armed != null && method.getName().equals(armed)) {
                throw new IllegalStateException("boom from " + armed + "()");
            }
            if ("commit".equals(method.getName())) {
                COMMITS.incrementAndGet();
            } else if ("rollback".equals(method.getName())) {
                ROLLBACKS.incrementAndGet();
            }
            try {
                return method.invoke(vendor, args);
            } catch (InvocationTargetException x) {
                throw x.getCause();
            }
        }
    }

    /*
     * Captures what PooledConnection itself logs, following the
     * handler-wiring approach of the pre-existing throwing-validation
     * tests; the canary fails the test up front if the handler would
     * observe nothing.
     */
    private static final class ValidationLogCapture implements AutoCloseable {
        private static final String CANARY = "pooledconnection-handler-wiring-canary";

        private final Logger logger = Logger.getLogger(PooledConnection.class.getName());
        private final Level originalLevel = logger.getLevel();
        private final List<LogRecord> records = Collections.synchronizedList(new ArrayList<>());
        private final Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
                // no buffering
            }

            @Override
            public void close() {
                // nothing to release
            }
        };

        ValidationLogCapture() {
            logger.addHandler(handler);
            logger.setLevel(Level.ALL);
            LogFactory.getLog(PooledConnection.class).error(CANARY);
            if (records.stream().noneMatch(r -> CANARY.equals(r.getMessage()))) {
                close();
                throw new AssertionError("the test handler must observe what this class logs");
            }
            // the canary is wiring proof, not pinned behavior
            records.clear();
        }

        /*
         * Requires a warning whose own message contains logText and whose
         * attached exception carries thrownMessage. Matching the logged
         * message, not just the driver's throw, is what pins which helper
         * owned the failure: the commit and the rollback helper report the
         * same armed call under different messages.
         */
        void assertWarnedAbout(String logText, String thrownMessage) {
            boolean matched = false;
            synchronized (records) {
                for (LogRecord record : records) {
                    Throwable thrown = record.getThrown();
                    if (thrown != null && String.valueOf(record.getMessage()).contains(logText)
                            && String.valueOf(thrown.getMessage()).contains(thrownMessage)) {
                        Assertions.assertEquals(Level.WARNING, record.getLevel(),
                                "the driver failure was logged at a level the pool's default"
                                        + " settings would hide or duplicate: " + record);
                        matched = true;
                    }
                }
            }
            Assertions.assertTrue(matched, "expected a warning about " + logText + " carrying the"
                    + " driver failure (" + thrownMessage + "), records: " + records);
        }

        @Override
        public void close() {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }
    }
}
