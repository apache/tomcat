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
package org.apache.tomcat.jdbc.pool.interceptor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * Enforcing a query timeout is something only the driver can do, so a driver
 * that refuses {@link Statement#setQueryTimeout(int)} leaves the statements of
 * that physical connection without the configured timeout - the application
 * gets an unbounded query either way. What the pool can be held to is what it
 * tells the operator: the statement must stay usable (the refusal is not
 * escalated into a creation failure), the absent timeout must be visible on the
 * handle, and the warning must be one readable line per physical connection -
 * neither repeated for every statement that connection creates, nor said once
 * per pool and never again.
 */
class TestQueryTimeoutDriverRefusal extends JdbcPoolBaseTest {

    /** Counts the {@code setQueryTimeout} calls that reach the driver. */
    private static final AtomicInteger TIMEOUT_ATTEMPTS = new AtomicInteger();

    private static final String QUERY_TIMEOUT_WARNING = "Unable to set the query timeout of 5 seconds";

    /* Substring identifying the unexpected-failure warning among the records. */
    private static final String UNEXPECTED_FAILURE_WARNING = "an exception other than a refusal";

    /* A pool whose driver refuses the timeout the interceptor configures. */
    private DataSource refusingTimeoutPool() {
        DataSource ds = newDataSource();
        ds.setDriverClassName(RefusingTimeoutDriver.class.getName());
        ds.getPoolProperties().setJdbcInterceptors(
                QueryTimeoutInterceptor.class.getName() + "(queryTimeout=5)");
        return ds;
    }

    /* The same, with a driver that refuses the timeout unchecked, as a
     * non-spec-compliant wrapper driver does. */
    private DataSource uncheckedRefusingTimeoutPool() {
        DataSource ds = newDataSource();
        ds.setDriverClassName(UncheckedRefusingTimeoutDriver.class.getName());
        ds.getPoolProperties().setJdbcInterceptors(
                QueryTimeoutInterceptor.class.getName() + "(queryTimeout=5)");
        return ds;
    }

    /*
     * Statements created and used through a pooled connection. Every creation
     * runs the interceptor's hook, so this is what generates refusals; each
     * statement also has to answer a query, which is the point of the driver
     * handing it out at all.
     */
    private void useStatements(Connection pooled, int count) throws SQLException {
        for (int i = 0; i < count; i++) {
            try (Statement st = pooled.createStatement()) {
                Assertions.assertTrue(st.execute("SELECT 1"),
                        "a statement whose timeout the driver refused must still run its query");
            }
        }
    }

    @Test
    void aRefusedTimeoutStillLeavesAUsableStatementAndNoTimeout() throws Exception {
        DataSource ds = refusingTimeoutPool();
        ds.createPool();
        TIMEOUT_ATTEMPTS.set(0);

        try (Connection pooled = ds.getConnection()) {
            Statement plain = pooled.createStatement();
            PreparedStatement prepared = pooled.prepareStatement("SELECT 1");
            Assertions.assertEquals(2, TIMEOUT_ATTEMPTS.get(),
                    "the hook must try the configured timeout on every created statement");

            // Nothing in the chain caches a query timeout, so the handle says
            // what the driver says - and the driver was never told.
            Assertions.assertEquals(0, plain.getQueryTimeout(),
                    "a statement the driver refused must not claim a timeout");
            Assertions.assertEquals(0, prepared.getQueryTimeout(),
                    "a statement the driver refused must not claim a timeout");
            Assertions.assertEquals(0, driverStatement(plain).getQueryTimeout(),
                    "the driver's own statement must carry no timeout either");

            Assertions.assertTrue(plain.execute("SELECT 1"),
                    "the refused plain statement must still work");
            try (ResultSet rs = prepared.executeQuery()) {
                Assertions.assertTrue(rs.next(), "the refused prepared statement must still work");
                Assertions.assertEquals(1, rs.getInt(1));
            }
            plain.close();
            prepared.close();
        }
    }

    /**
     * The report's job is the driver's reason for the refusal, so a driver
     * that refuses without stating one must still produce a readable line:
     * the exception's own name fills the reason slot, rather than the literal
     * "null" the raw message would render there - a line the operator is told
     * they will not get again should not be unreadable.
     */
    @Test
    void aRefusalWithoutAMessageIsStillReportedWithAReadableReason() throws Exception {
        DataSource ds = newDataSource();
        ds.setDriverClassName(SilentRefusingTimeoutDriver.class.getName());
        ds.getPoolProperties().setJdbcInterceptors(
                QueryTimeoutInterceptor.class.getName() + "(queryTimeout=5)");
        ds.createPool();

        try (PoolLogCapture logs = new PoolLogCapture()) {
            try (Connection pooled = ds.getConnection();
                    Statement st = pooled.createStatement()) {
                Assertions.assertTrue(st.execute("SELECT 1"),
                        "a message-less refusal must cost the timeout only, not the statement");
            }
            List<String> refusals = logs.refusalMessages();
            Assertions.assertEquals(1, refusals.size(), "the message-less refusal is reported once");
            String message = refusals.get(0);
            Assertions.assertFalse(message.contains(": null."),
                    "the reason slot must not render the raw null message: " + message);
            Assertions.assertTrue(message.contains("java.sql.SQLException"),
                    "the exception's own name must stand in for the missing message: " + message);
        }
    }

    /**
     * The widening that lets an unchecked refusal cost the timeout rather
     * than the statement must not turn every unrelated driver stumble in
     * that call into the assertion "the driver does not apply the timeout",
     * and above all must not let one odd failure spend the single report the
     * physical connection will ever get: a driver that fails the call oddly
     * once and genuinely refuses it later has to be heard refusing it, or
     * the one line the operator does get says the wrong thing about the
     * wrong exception.
     */
    @Test
    void anUnexpectedFailureIsNeitherCalledARefusalNorSilencesOne() throws Exception {
        DataSource ds = newDataSource();
        ds.setDriverClassName(MixedFailureTimeoutDriver.class.getName());
        ds.getPoolProperties().setJdbcInterceptors(
                QueryTimeoutInterceptor.class.getName() + "(queryTimeout=5)");
        ds.createPool();
        MixedFailureTimeoutDriver.reset();

        try (PoolLogCapture logs = new PoolLogCapture()) {
            try (Connection pooled = ds.getConnection()) {
                for (int creation = 1; creation <= 3; creation++) {
                    try (Statement st = pooled.createStatement()) {
                        Assertions.assertTrue(st.execute("SELECT 1"),
                                "an unexpected failure in the timeout call must still cost only the timeout");
                    }
                }
            }
            List<String> unexpected = logs.unexpectedFailures();
            List<String> refusals = logs.refusalMessages();
            Assertions.assertEquals(1, unexpected.size(),
                    "the unexpected failure is reported once per connection like a refusal");
            Assertions.assertEquals(1, refusals.size(),
                    "the genuine refusal after the unexpected failure must still be reported");
            Assertions.assertTrue(refusals.get(0).contains("genuine refusal"),
                    "the refusal line must carry the refusal's own reason: " + refusals.get(0));
            Assertions.assertFalse(unexpected.get(0).contains("does not apply"),
                    "the unexpected failure must not assert a driver refusal: " + unexpected.get(0));
        }
    }

    /**
     * A driver that refuses the timeout with an unchecked exception - the shape
     * a non-spec-compliant wrapper driver uses, where the spec-compliant
     * refusal is an exception the hook has to see coming - must fail exactly
     * like the checked refusal. The interceptor's promise is that a refusal
     * costs the timeout and not the statement, and that promise does not depend
     * on whether the driver's refusal happens to be a checked exception; a
     * refusal the hook lets escape closes the statement behind the failed
     * creation and leaves the pool unable to create any statement at all, in
     * silence.
     */
    @Test
    void anUncheckedRefusalStillLeavesAUsableStatementAndReportsItOnce() throws Exception {
        DataSource ds = uncheckedRefusingTimeoutPool();
        ds.createPool();
        TIMEOUT_ATTEMPTS.set(0);

        try (PoolLogCapture logs = new PoolLogCapture()) {
            try (Connection pooled = ds.getConnection()) {
                try (Statement st = pooled.createStatement()) {
                    Assertions.assertTrue(st.execute("SELECT 1"),
                            "a statement whose timeout the driver refused unchecked must still run its query");
                    Assertions.assertEquals(0, st.getQueryTimeout(),
                            "a statement the driver refused must not claim a timeout");
                }
                Assertions.assertEquals(1, TIMEOUT_ATTEMPTS.get(),
                        "the hook must try the configured timeout before noticing the refusal");
                logs.assertRefusals(1, UnsupportedOperationException.class,
                        "the unchecked refusal is as reportable as the checked one");
                try (Statement again = pooled.createStatement()) {
                    Assertions.assertTrue(again.execute("SELECT 1"),
                            "the refusal must not poison the connection for later statements");
                }
                logs.assertRefusals(1, UnsupportedOperationException.class,
                        "the unchecked refusal is reported once per physical connection like the checked one");
            }
        }
    }

    /**
     * The pool caches one interceptor chain per pooled connection, so the
     * statements of one physical session all meet the same interceptor instance
     * and its refusal has one line of report; a second session has its own
     * interceptor and says so for itself.
     */
    @Test
    void onePhysicalSessionReportsItsRefusalOnlyOnce() throws Exception {
        DataSource ds = refusingTimeoutPool();
        ds.getPoolProperties().setMaxActive(2);
        ds.createPool();

        try (PoolLogCapture logs = new PoolLogCapture()) {
            Connection first = ds.getConnection();
            setSessionMarker(first, 7);
            useStatements(first, 3);
            first.close();

            Connection again = ds.getConnection();
            Assertions.assertEquals(Integer.valueOf(7), sessionMarker(again),
                    "the pool must have handed out the same physical session again");
            useStatements(again, 3);
            logs.assertRefusals(1, "many statements on one session are one report");
            again.close();

            Connection held = ds.getConnection();
            Assertions.assertEquals(Integer.valueOf(7), sessionMarker(held),
                    "the returned session must have been borrowed again");
            Connection other = ds.getConnection();
            Assertions.assertNull(sessionMarker(other),
                    "the second borrowed connection must sit on its own physical session");
            useStatements(other, 3);
            useStatements(held, 2);
            logs.assertRefusals(2, "two physical sessions are two reports, whatever the statement count");

            other.close();
            held.close();
        }
    }

    /**
     * A session replaced underneath the same pooled connection is a session that
     * has to answer the call again, so it gets its own report: the deduplication
     * belongs to the physical connection and is not a once-per-pool silence.
     */
    @Test
    void aReplacedSessionReportsItsOwnRefusal() throws Exception {
        DataSource ds = refusingTimeoutPool();
        ds.getPoolProperties().setMaxActive(1);
        // a session older than maxAge is replaced as it comes back, so the next
        // borrower finds a new session behind the same pooled connection
        ds.getPoolProperties().setMaxAge(1);
        // and the sweeper is switched off: it would replace sessions on its own
        // clock, racing the borrows this test counts (any interval above maxAge
        // is clamped down to maxAge anyway)
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(0);
        ds.createPool();

        try (PoolLogCapture logs = new PoolLogCapture()) {
            for (int borrow = 1; borrow <= 3; borrow++) {
                try (Connection pooled = ds.getConnection()) {
                    if (borrow > 1) {
                        Assertions.assertNull(sessionMarker(pooled),
                                "the borrower must sit on a session that replaced the previous one");
                    }
                    useStatements(pooled, 2);
                    setSessionMarker(pooled, borrow);
                }
                logs.assertRefusals(borrow, "the session that replaces another reports its own refusal");
                sleep(10);
            }
        }
        awaitSessions(1);
    }

    /**
     * Captures what {@link QueryTimeoutInterceptor} logs, following the handler
     * idiom of the pool's other diagnostic tests. The canary record - written
     * through the very JULI logger the interceptor logs through, not through JUL
     * directly - proves the wiring observes those records before the pool under
     * test gets its chance to log.
     */
    private static final class PoolLogCapture implements AutoCloseable {
        private static final String CANARY = "query-timeout-refusal-handler-wiring-canary";

        private final Logger logger = Logger.getLogger(QueryTimeoutInterceptor.class.getName());
        private final Level originalLevel = logger.getLevel();
        private final List<LogRecord> records = new ArrayList<>();
        private final Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                synchronized (records) {
                    records.add(record);
                }
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

        PoolLogCapture() {
            logger.addHandler(handler);
            logger.setLevel(Level.ALL);
            LogFactory.getLog(QueryTimeoutInterceptor.class).error(CANARY);
            synchronized (records) {
                if (records.stream().noneMatch(r -> CANARY.equals(r.getMessage()))) {
                    close();
                    throw new AssertionError("the test handler must observe what this class logs");
                }
            }
        }

        /*
         * Every report of a driver refusal, with the volume itself pinned: one
         * warning naming the timeout that is not in effect and saying that this
         * connection will not mention it again. An empty list fails these tests
         * as loudly as a chatty one - the assertions are not met by the
         * interceptor having gone quiet.
         */
        void assertRefusals(int expected, String why) {
            assertRefusals(expected, SQLException.class, why);
        }

        /*
         * As above, with the shape of the driver's refusal pinned per case:
         * the spec-compliant refusal is checked, the wrapper-driver one is
         * not, and the report must carry the driver's own exception either
         * way.
         */
        void assertRefusals(int expected, Class<? extends Throwable> thrownType, String why) {
            List<LogRecord> seen;
            synchronized (records) {
                seen = new ArrayList<>(records);
            }
            List<LogRecord> refusals = seen.stream()
                    .filter(r -> r.getMessage() != null && r.getMessage().contains(QUERY_TIMEOUT_WARNING))
                    .collect(Collectors.toList());
            Assertions.assertEquals(expected, refusals.size(),
                    why + ", messages seen: " + seen.stream().map(LogRecord::getMessage)
                            .collect(Collectors.toList()));
            for (LogRecord record : refusals) {
                Assertions.assertEquals(Level.WARNING.intValue(), record.getLevel().intValue(),
                        "the refusal is a warning, not something quieter: " + record.getMessage());
                Assertions.assertTrue(record.getMessage().contains("without a query timeout"),
                        "the report must say that the timeout is not in effect: " + record.getMessage());
                Assertions.assertTrue(record.getMessage().contains("not logged again"),
                        "the report must say what the log will not show later: " + record.getMessage());
                Assertions.assertInstanceOf(thrownType, record.getThrown(),
                        "the driver's refusal must survive as the cause: " + record.getMessage());
            }
        }

        @Override
        public void close() {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }

        /* The refusal warning messages captured, for content assertions. */
        List<String> refusalMessages() {
            synchronized (records) {
                return records.stream()
                        .map(LogRecord::getMessage)
                        .filter(m -> m != null && m.contains(QUERY_TIMEOUT_WARNING))
                        .collect(Collectors.toList());
            }
        }

        /* The unexpected-failure warning messages captured. */
        List<String> unexpectedFailures() {
            synchronized (records) {
                return records.stream()
                        .map(LogRecord::getMessage)
                        .filter(m -> m != null && m.contains(UNEXPECTED_FAILURE_WARNING))
                        .collect(Collectors.toList());
            }
        }
    }

    /**
     * Driver handing out the real H2 connections wrapped so that
     * {@code setQueryTimeout} is refused with a SQLException, the way a driver
     * that does not implement query timeouts refuses it, and counting the calls
     * that reach it. Configured through {@code driverClassName}, so it must stay
     * public with a no-arg constructor.
     */
    public static class RefusingTimeoutDriver implements Driver {

        private final Driver delegate = new org.h2.Driver();

        /**
         * The refusal this driver's statements hand out. Checked, the way the
         * JDBC spec says a driver refuses a feature; the subclass below swaps
         * in the unchecked shape a non-compliant wrapper uses.
         *
         * @return the supplier of the exception the wrapped statements throw
         *         from {@code setQueryTimeout}
         */
        protected Supplier<Throwable> refusal() {
            return () -> new SQLException("this driver does not support query timeouts");
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            Connection connection = delegate.connect(url, info);
            if (connection == null) {
                return null;
            }
            return (Connection) Proxy.newProxyInstance(
                    RefusingTimeoutDriver.class.getClassLoader(),
                    new Class<?>[] { Connection.class },
                    new RefusingConnectionHandler(connection, refusal()));
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
     * Hands out the vendor statements wrapped so that they refuse the timeout,
     * and delegates everything else to H2.
     */
    private static class RefusingConnectionHandler implements InvocationHandler {

        private final Connection vendor;
        private final Supplier<Throwable> refusal;

        RefusingConnectionHandler(Connection vendor, Supplier<Throwable> refusal) {
            this.vendor = vendor;
            this.refusal = refusal;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result = delegate(vendor, method, args);
            String name = method.getName();
            if ("createStatement".equals(name)) {
                return refusing((Statement) result, Statement.class);
            }
            if ("prepareStatement".equals(name)) {
                return refusing((PreparedStatement) result, PreparedStatement.class);
            }
            if ("prepareCall".equals(name)) {
                return refusing((CallableStatement) result, CallableStatement.class);
            }
            return result;
        }

        private Object refusing(Statement statement, Class<?> api) {
            return Proxy.newProxyInstance(RefusingTimeoutDriver.class.getClassLoader(),
                    new Class<?>[] { api }, new RefusingStatementHandler(statement, refusal));
        }
    }

    /*
     * Refuses setQueryTimeout() and delegates everything else, so that the
     * statement is otherwise an ordinary H2 statement.
     */
    private static class RefusingStatementHandler implements InvocationHandler {

        private final Statement vendor;
        private final Supplier<Throwable> refusal;

        RefusingStatementHandler(Statement vendor, Supplier<Throwable> refusal) {
            this.vendor = vendor;
            this.refusal = refusal;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("setQueryTimeout".equals(method.getName())) {
                TIMEOUT_ATTEMPTS.incrementAndGet();
                throw refusal.get();
            }
            return delegate(vendor, method, args);
        }
    }

    /*
     * The same refusal as {@link RefusingTimeoutDriver}, but thrown unchecked -
     * a shape no compliant driver uses and wrapper drivers do. Configured
     * through {@code driverClassName}, so it must stay public with a no-arg
     * constructor.
     */
    public static class UncheckedRefusingTimeoutDriver extends RefusingTimeoutDriver {

        @Override
        protected Supplier<Throwable> refusal() {
            return () -> new UnsupportedOperationException("query timeouts are not implemented");
        }
    }

    /*
     * Refuses the timeout with a checked exception that carries no message -
     * a driver stating that it refuses without saying why. Configured through
     * {@code driverClassName}, so it must stay public with a no-arg
     * constructor.
     */
    public static class SilentRefusingTimeoutDriver extends RefusingTimeoutDriver {

        @Override
        protected Supplier<Throwable> refusal() {
            return SQLException::new;
        }
    }

    /*
     * A driver whose setQueryTimeout fails three ways in a row: once with an
     * unrelated unchecked exception (a driver bug, not a refusal), then with a
     * genuine checked refusal, then with the unrelated failure again. The
     * interceptor must tell the bug from the refusal, and the bug must not
     * spend the refusal's single report. Configured through
     * {@code driverClassName}, so it must stay public with a no-arg
     * constructor.
     */
    public static class MixedFailureTimeoutDriver extends RefusingTimeoutDriver {

        private static final AtomicInteger ATTEMPTS = new AtomicInteger();

        static void reset() {
            ATTEMPTS.set(0);
        }

        @Override
        protected Supplier<Throwable> refusal() {
            return () -> {
                int attempt = ATTEMPTS.incrementAndGet();
                if (attempt == 2) {
                    return new SQLException("genuine refusal");
                }
                return new IllegalStateException("driver bug number " + attempt);
            };
        }
    }

    /* Calls the vendor method, letting its own exception out unchanged. */
    private static Object delegate(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException x) {
            throw x.getCause();
        }
    }
}
