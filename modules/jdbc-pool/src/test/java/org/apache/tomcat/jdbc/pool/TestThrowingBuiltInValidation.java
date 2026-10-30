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
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
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
 * The built-in validation paths reach the database through the driver:
 * the {@code isValid()} check and the transaction termination that ends
 * the session the check leaves open. Both handle the checked
 * {@link SQLException} a well-behaved driver throws when a call fails,
 * but nothing in the JDBC contract stops a driver or a connection
 * wrapper from failing one of them with an unchecked exception instead.
 * Such a throw must be reported as a failed validation, not escape
 * {@code validate()}: a borrow polls the connection out of the idle
 * queue before validating and only offers it to the busy queue on
 * success, so a throw escaping midway leaves the connection in neither
 * queue while the size counter still counts it - invisible to
 * checkAbandoned(), to testAllIdle() and to the close() drain alike,
 * never disconnected, permanently shrinking the pool. The return path
 * and the idle sweep claim their connection the same way before they
 * validate, so they accept the same hazard.
 */
class TestThrowingBuiltInValidation extends JdbcPoolBaseTest {

    /*
     * The method the pooled physical connections should fail from with a
     * RuntimeException, suffixed with "=false" to fail by honestly
     * returning false instead, or null to let everything through. Static,
     * because the driver is instantiated by class name and the tests alone
     * decide when the failure window opens and closes.
     */
    private static final AtomicReference<String> FAIL_FROM = new AtomicReference<>();

    /* One-slot pool on the failing driver, with no competing sweeper. */
    private DataSource oneSlotPool(boolean autoCommitOff) throws SQLException {
        DataSource ds = newDataSource();
        ds.setDriverClassName(FailingDriver.class.getName());
        ds.getPoolProperties().setInitialSize(0);
        // exactly one slot: a stranded connection exhausts the pool at once
        ds.getPoolProperties().setMaxActive(1);
        // the tests drive validation themselves; a background sweep would
        // compete with the idle assertions below
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(0);
        ds.getPoolProperties().setValidationInterval(0);
        if (autoCommitOff) {
            // an interceptor chain without the state interceptor, so
            // connect() applies the autoCommit state to every (re)connect:
            // the transaction termination then reaches the armed rollback
            // on the new session of a reconnect as well as on the old one
            ds.getPoolProperties().setJdbcInterceptors(StatementFinalizer.class.getName());
            ds.getPoolProperties().setDefaultAutoCommit(Boolean.FALSE);
        }
        ds.createPool();
        return ds;
    }

    /*
     * Park one idle connection, fail the named built-in validation call
     * from the driver during the re-borrow, and require the failed borrow
     * to cost the pool nothing: a checked SQLException at the application,
     * the connection disconnected, its slot back in the size budget, and
     * the next borrow working. This is exactly what an honest false from
     * the same check already does.
     */
    private void assertFailingBuiltInCheckOnBorrowDoesNotStrand(String armedMethod,
            boolean autoCommitOff) throws Exception {
        DataSource ds = oneSlotPool(autoCommitOff);
        ds.getPoolProperties().setTestOnBorrow(true);
        // the creation path is not validated (testOnConnect is off), the
        // built-in check first runs when the returned connection is
        // re-borrowed out of the idle queue
        Connection first = ds.getConnection();
        awaitSessions(1);
        first.close();
        Assertions.assertEquals(1, ds.getPool().getIdle());

        FAIL_FROM.set(armedMethod);
        SQLException sqlX;
        try {
            // an unchecked driver failure must reach the application as
            // the checked SQLException the DataSource contract allows
            sqlX = Assertions.assertThrows(SQLException.class, ds::getConnection);
        } finally {
            FAIL_FROM.set(null);
        }
        Assertions.assertTrue(sqlX.getMessage().contains("Failed to validate"),
                "expected a validation failure, got: " + sqlX.getMessage());

        // nothing stranded: neither queue holds the connection, the size
        // budget got its slot back, and the physical session really was
        // closed rather than left open outside both queues
        awaitSessions(0);
        Assertions.assertEquals(0, ds.getPool().getIdle());
        Assertions.assertEquals(0, ds.getPool().getActive());
        Assertions.assertEquals(0, ds.getPool().getSize());

        // the single slot was not lost: the next borrow succeeds
        try (Connection again = ds.getConnection()) {
            Assertions.assertNotNull(again);
        }
        Assertions.assertEquals(0, ds.getPool().getActive());
        ds.close();
        awaitSessions(0);
    }

    @Test
    void uncheckedIsValidFailureOnBorrowDoesNotStrandTheSlot() throws Exception {
        // the isValid() branch of validate() ran guarded only against the
        // SQLException, so an unchecked throw escaped between the idle
        // poll and the busy offer
        assertFailingBuiltInCheckOnBorrowDoesNotStrand("isValid", false);
    }

    @Test
    void uncheckedAutoCommitCheckFailureOnBorrowDoesNotStrandTheSlot() throws Exception {
        // the transaction termination runs even after a successful
        // isValid(), through a helper that only handled the SQLException
        assertFailingBuiltInCheckOnBorrowDoesNotStrand("getAutoCommit", false);
    }

    @Test
    void uncheckedRollbackFailureOnBorrowDoesNotStrandTheSlot() throws Exception {
        // with the session in a transaction, the termination calls
        // rollback() - the other failure point of the same helper
        assertFailingBuiltInCheckOnBorrowDoesNotStrand("rollback", true);
    }

    @Test
    void uncheckedRollbackFailureAfterValidationQueryDoesNotEscapeTheFinally()
            throws Exception {
        // the validation-query branch catches Exception around its body,
        // but the same termination runs from its finally block, outside
        // that catch - an unchecked throw there escaped even the branch
        // that was described as guarded
        DataSource ds = oneSlotPool(true);
        ds.getPoolProperties().setTestOnBorrow(true);
        ds.getPoolProperties().setValidationQuery("SELECT 1");
        Connection first = ds.getConnection();
        awaitSessions(1);
        first.close();
        Assertions.assertEquals(1, ds.getPool().getIdle());

        FAIL_FROM.set("rollback");
        SQLException sqlX;
        try {
            sqlX = Assertions.assertThrows(SQLException.class, ds::getConnection);
        } finally {
            FAIL_FROM.set(null);
        }
        Assertions.assertTrue(sqlX.getMessage().contains("Failed to validate"),
                "expected a validation failure, got: " + sqlX.getMessage());
        awaitSessions(0);
        Assertions.assertEquals(0, ds.getPool().getSize());
        try (Connection again = ds.getConnection()) {
            Assertions.assertNotNull(again);
        }
        ds.close();
        awaitSessions(0);
    }

    @Test
    void falseIsValidOnBorrowKeepsItsMeaning() throws Exception {
        // control for the widened guards: an honest false from isValid()
        // keeps its pre-existing meaning all the way through - the same
        // end state the new throw-handling must reproduce
        DataSource ds = oneSlotPool(false);
        ds.getPoolProperties().setTestOnBorrow(true);
        Connection first = ds.getConnection();
        awaitSessions(1);
        first.close();
        FAIL_FROM.set("isValid=false");
        try {
            Assertions.assertThrows(SQLException.class, ds::getConnection);
        } finally {
            FAIL_FROM.set(null);
        }
        awaitSessions(0);
        Assertions.assertEquals(0, ds.getPool().getSize());
        try (Connection again = ds.getConnection()) {
            Assertions.assertNotNull(again);
        }
        ds.close();
        awaitSessions(0);
    }

    @Test
    void uncheckedHelperFailureIsLoggedAtWarnByDefault() throws Exception {
        // the pool hands the borrower only a generic validation
        // SQLException, so an unchecked failure of the transaction
        // termination must leave a trace in the pool's own log at the
        // default settings: the helper logs it at warn with the driver's
        // exception attached
        DataSource ds = oneSlotPool(true);
        ds.getPoolProperties().setTestOnBorrow(true);
        Connection first = ds.getConnection();
        first.close();

        try (ValidationLogCapture logs = new ValidationLogCapture()) {
            FAIL_FROM.set("rollback");
            try {
                Assertions.assertThrows(SQLException.class, ds::getConnection);
            } finally {
                FAIL_FROM.set(null);
            }
            logs.assertLoggedAt(Level.WARNING, "boom from rollback()");
        }

        // pinning the log must not change the outcome: the slot was not
        // lost and the session really was closed
        awaitSessions(0);
        Assertions.assertEquals(0, ds.getPool().getSize());
        try (Connection again = ds.getConnection()) {
            Assertions.assertNotNull(again);
        }
        ds.close();
        awaitSessions(0);
    }

    @Test
    void uncheckedHelperFailureIsLoggedAtErrorWhenLogValidationErrors() throws Exception {
        // logValidationErrors promotes the failure the way it promotes the
        // sibling validation branches', with the driver's exception
        // attached either way
        DataSource ds = oneSlotPool(true);
        ds.getPoolProperties().setTestOnBorrow(true);
        ds.getPoolProperties().setLogValidationErrors(true);
        Connection first = ds.getConnection();
        first.close();

        try (ValidationLogCapture logs = new ValidationLogCapture()) {
            FAIL_FROM.set("rollback");
            try {
                Assertions.assertThrows(SQLException.class, ds::getConnection);
            } finally {
                FAIL_FROM.set(null);
            }
            logs.assertLoggedAt(Level.SEVERE, "boom from rollback()");
        }
        ds.close();
        awaitSessions(0);
    }

    @Test
    void uncheckedIsValidFailureIsLoggedAtWarnByDefault() throws Exception {
        // the return path no longer reaches the caller-level guard that
        // once warned about an unchecked validation throw, and the borrow
        // path never had one: the widened isValid() catch itself must
        // leave the driver's failure in the log at the default settings
        DataSource ds = oneSlotPool(false);
        ds.getPoolProperties().setTestOnBorrow(true);
        Connection first = ds.getConnection();
        first.close();

        try (ValidationLogCapture logs = new ValidationLogCapture()) {
            FAIL_FROM.set("isValid");
            try {
                Assertions.assertThrows(SQLException.class, ds::getConnection);
            } finally {
                FAIL_FROM.set(null);
            }
            logs.assertLoggedAt(Level.WARNING, "boom from isValid()");
        }
        ds.close();
        awaitSessions(0);
    }

    @Test
    void uncheckedIsValidFailureOnReturnTakesTheConnectionOutOfService() throws Exception {
        // pin: the return path already catches an unchecked throw around
        // the whole claimed-connection section, and the fix in validate()
        // now routes it through the failed validation instead; both must
        // end the same way - close() complete(s) quietly and the
        // connection that cannot be vouched for is discarded
        DataSource ds = oneSlotPool(false);
        ds.getPoolProperties().setTestOnReturn(true);
        Connection first = ds.getConnection();
        awaitSessions(1);

        FAIL_FROM.set("isValid");
        try {
            Assertions.assertDoesNotThrow(first::close);
        } finally {
            FAIL_FROM.set(null);
        }
        awaitSessions(0);
        Assertions.assertEquals(0, ds.getPool().getIdle());
        Assertions.assertEquals(0, ds.getPool().getActive());
        Assertions.assertEquals(0, ds.getPool().getSize());
        try (Connection again = ds.getConnection()) {
            Assertions.assertNotNull(again);
        }
        ds.close();
        awaitSessions(0);
    }

    @Test
    void uncheckedIsValidFailureOnIdleValidationEvictsTheConnection() throws Exception {
        // pin: the idle sweep claims the connection before validating, so
        // an escaped throw would strand it out of every queue and out of
        // the next sweep; whether the sweep's own catch or the failed
        // validation takes it out of service, it must end disconnected
        DataSource ds = oneSlotPool(false);
        ds.getPoolProperties().setTestWhileIdle(true);
        Connection first = ds.getConnection();
        awaitSessions(1);
        first.close();
        Assertions.assertEquals(1, ds.getPool().getIdle());

        FAIL_FROM.set("isValid");
        try {
            Assertions.assertDoesNotThrow(ds::testIdle);
        } finally {
            FAIL_FROM.set(null);
        }
        awaitSessions(0);
        Assertions.assertEquals(0, ds.getPool().getIdle());
        Assertions.assertEquals(0, ds.getPool().getSize());
        // the evicted slot is available again
        try (Connection again = ds.getConnection()) {
            Assertions.assertNotNull(again);
        }
        ds.close();
        awaitSessions(0);
    }

    /**
     * Driver handing out the real H2 connections wrapped in a proxy that
     * fails the named method with a RuntimeException (or, when the name
     * is suffixed with "=false", by returning false). Configured through
     * {@code driverClassName}, so it must stay a public static class with
     * a no-arg constructor.
     */
    public static class FailingDriver implements Driver {

        private final Driver delegate = new org.h2.Driver();

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            Connection connection = delegate.connect(url, info);
            if (connection == null) {
                return null;
            }
            return (Connection) Proxy.newProxyInstance(
                    FailingDriver.class.getClassLoader(),
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

    /* Fails the armed method by name; delegates everything else to H2. */
    private static class FailingHandler implements InvocationHandler {

        private final Connection vendor;

        FailingHandler(Connection vendor) {
            this.vendor = vendor;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String armed = FAIL_FROM.get();
            if (armed != null) {
                boolean honestFalse = armed.endsWith("=false");
                String name = honestFalse ? armed.substring(0, armed.length() - "=false".length()) : armed;
                if (method.getName().equals(name)) {
                    if (honestFalse) {
                        return Boolean.FALSE;
                    }
                    throw new IllegalStateException("boom from " + name + "()");
                }
            }
            try {
                return method.invoke(vendor, args);
            } catch (InvocationTargetException x) {
                throw x.getCause();
            }
        }
    }

    /*
     * Captures what PooledConnection itself logs, following the handler-
     * wiring approach TestAsyncBorrowFailureDiagnostics uses against
     * ConnectionPool; the canary fails the test up front if the handler
     * would observe nothing.
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
         * Requires the driver's failure to appear at exactly the given
         * level, with the driver's exception attached. It may appear more
         * than once - a failed validation is retried once after a
         * reconnect, and a failed termination is attempted again from the
         * finally block - but every record of it must carry the level.
         */
        void assertLoggedAt(Level level, String thrownMessage) {
            boolean matched = false;
            synchronized (records) {
                for (LogRecord record : records) {
                    Throwable thrown = record.getThrown();
                    if (thrown != null && String.valueOf(thrown.getMessage()).contains(thrownMessage)) {
                        Assertions.assertEquals(level, record.getLevel(), "driver failure logged at an"
                                + " unexpected level, so the pool's default settings would hide it: "
                                + record);
                        matched = true;
                    }
                }
            }
            Assertions.assertTrue(matched, "expected the driver failure (" + thrownMessage
                    + ") to be logged with the exception attached, records: " + records);
        }

        @Override
        public void close() {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }
    }
}
