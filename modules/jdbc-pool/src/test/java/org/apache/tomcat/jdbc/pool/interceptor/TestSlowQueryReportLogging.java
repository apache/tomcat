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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.management.Notification;
import javax.management.NotificationListener;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * The slow/failed-query WARN logging is configured by
 * {@code logSlow}/{@code logFailed}, not by {@code maxQueries}.
 * {@code maxQueries} sizes the per-query statistics store - the javadoc
 * and the interceptor attributes document it as a memory setting, with
 * the log flags as independent settings - but
 * {@link SlowQueryReport#reportFailedQuery} and
 * {@link SlowQueryReport#reportSlowQuery} wrapped their whole bodies,
 * logging included, in {@code if (this.maxQueries > 0)}, so a pool
 * configured with {@code maxQueries=0} - "store no per-query
 * statistics" - silently lost the WARN lines even with the log flags
 * on, while {@link SlowQueryReportJmx} kept sending the JMX
 * notification behind the flag alone and the two channels disagreed on
 * the same event.
 * <p>
 * The tests pin the separated contract: with the store disabled the
 * WARN still goes out when (and only when) the log flag is on, the
 * statistics map stays empty, and with the store enabled booking and
 * logging behave exactly as before.
 * </p>
 */
class TestSlowQueryReportLogging extends JdbcPoolBaseTest {

    /*
     * The query under test must really run long, so it waits inside the
     * database while the WHERE clause is evaluated (java.lang.Thread.sleep
     * exposed to SQL by an alias), holding the client's execute() open for
     * at least that long on any hardware. The pools are configured with a
     * threshold of zero, which makes every successful execution slower
     * than zero milliseconds a reported slow query.
     */
    private static final long SLOW_WAIT_MILLIS = 60;

    /*
     * The SQL of a query that reports itself under the "SELECT marker"
     * name the assertions look for, while waiting inside the database.
     * The alias is registered once per database; IF NOT EXISTS keeps
     * every call cheap.
     */
    private String slowSql(int marker) throws SQLException {
        try (Connection admin = adminConnection();
                Statement st = admin.createStatement()) {
            st.execute("CREATE ALIAS IF NOT EXISTS SLOW_SLEEP FOR "
                    + "\"java.lang.Thread.sleep(long)\"");
        }
        return "SELECT " + marker + " FROM SYSTEM_RANGE(1,1) WHERE SLOW_SLEEP("
                + SLOW_WAIT_MILLIS + ") IS NULL";
    }

    /* A statement execution that fails in the database, reported under
     * the SQL the application passed to execute(). */
    private String failingSql() {
        return "SELECT * FROM " + uniqueTable() + "_NO_SUCH_TABLE";
    }

    private static void assertFails(Connection con, String sql) {
        try (Statement st = con.createStatement()) {
            Assertions.assertThrows(SQLException.class, () -> st.execute(sql),
                    "executing [" + sql + "] must fail");
        } catch (SQLException x) {
            throw new AssertionError(x);
        }
    }

    @Test
    void slowQueryIsWarnedAboutWhenTheStatisticsStoreIsDisabled() throws Exception {
        String poolName = "SlowLogStoreOff-" + System.nanoTime();
        DataSource ds = newDataSource();
        ds.setName(poolName);
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport"
                + "(maxQueries=0,threshold=0,logSlow=true)");
        ds.getPoolProperties().setInitialSize(0);

        try (PoolLogCapture logs = new PoolLogCapture();
                Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute(slowSql(1));

            // maxQueries sizes the statistics store; it must not switch
            // off the WARN that logSlow=true configures
            logs.assertSlowQueryWarned("SELECT 1 FROM");

            // the store itself stays off: nothing is booked
            Assertions.assertTrue(SlowQueryReport.getPoolStats(poolName).isEmpty(),
                    "maxQueries=0 must keep the statistics map empty");
        }
    }

    @Test
    void failedQueryIsWarnedAboutWhenTheStatisticsStoreIsDisabled() throws Exception {
        String poolName = "FailedLogStoreOff-" + System.nanoTime();
        DataSource ds = newDataSource();
        ds.setName(poolName);
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport"
                + "(maxQueries=0,logFailed=true)");
        ds.getPoolProperties().setInitialSize(0);

        try (PoolLogCapture logs = new PoolLogCapture();
                Connection con = ds.getConnection()) {
            assertFails(con, failingSql());

            logs.assertFailedQueryWarned("NO_SUCH_TABLE");

            Assertions.assertTrue(SlowQueryReport.getPoolStats(poolName).isEmpty(),
                    "maxQueries=0 must keep the statistics map empty");
        }
    }

    @Test
    void storeDisabledPoolStillLogsAndNotifiesTogether() throws Exception {
        String poolName = "StoreOffChannels-" + System.nanoTime();
        DataSource ds = newDataSource();
        ds.setName(poolName);
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                + "(notifyPool=false,maxQueries=0,threshold=0,logSlow=true)");
        ds.getPoolProperties().setInitialSize(0);

        List<Notification> received = new CopyOnWriteArrayList<>();
        NotificationListener listener = (notification, handback) -> received.add(notification);

        try (PoolLogCapture logs = new PoolLogCapture();
                Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            SlowQueryReportJmx report = interceptorOf(con, SlowQueryReportJmx.class);
            Assertions.assertNotNull(report,
                    "the chain must hold the configured SlowQueryReportJmx instance");
            report.addNotificationListener(listener, null, null);

            st.execute(slowSql(2));

            // The JMX notification fired even before the fix; the log
            // channel must join it - one reported slow query, both
            // channels, whatever the store capacity is configured to.
            Assertions.assertTrue(received.stream().anyMatch(n ->
                            SlowQueryReportJmx.SLOW_QUERY_NOTIFICATION.equals(n.getType())
                                    && n.getMessage() != null
                                    && n.getMessage().contains("SELECT 2 FROM")),
                    "the slow query must notify through the name's broadcaster,"
                            + " received: " + received);
            logs.assertSlowQueryWarned("SELECT 2 FROM");
        }
    }

    @Test
    void storeDisabledAndLoggingDisabledLogsNothing() throws Exception {
        String poolName = "StoreOffLogOff-" + System.nanoTime();
        DataSource ds = newDataSource();
        ds.setName(poolName);
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport"
                + "(maxQueries=0,threshold=0,logSlow=false,logFailed=false)");
        ds.getPoolProperties().setInitialSize(0);

        try (PoolLogCapture logs = new PoolLogCapture();
                Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute(slowSql(4));
            assertFails(con, failingSql());

            // Separating logging from the store must not make the WARN
            // unconditional: the log flags still gate it.
            logs.assertNoQueryReportWarnings();
        }
    }

    @Test
    void storeEnabledBookingAndLoggingAreUnchanged() throws Exception {
        String poolName = "StoreOn-" + System.nanoTime();
        DataSource ds = newDataSource();
        ds.setName(poolName);
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport"
                + "(maxQueries=10,threshold=0,logSlow=true,logFailed=true)");
        ds.getPoolProperties().setInitialSize(0);

        String slow = slowSql(3);
        try (PoolLogCapture logs = new PoolLogCapture();
                Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute(slow);
            assertFails(con, failingSql());

            // the positive-capacity configuration keeps the behaviour it
            // always had: both WARN lines and both bookings
            logs.assertSlowQueryWarned("SELECT 3 FROM");
            logs.assertFailedQueryWarned("NO_SUCH_TABLE");

            SlowQueryReport.QueryStats slowStats = SlowQueryReport.getPoolStats(poolName).get(slow);
            Assertions.assertNotNull(slowStats, "the slow query must be booked in the store");
            Assertions.assertTrue(slowStats.getNrOfInvocations() >= 1,
                    "the slow invocation must be counted");
        }
    }

    /**
     * Captures what {@link SlowQueryReport} logs, following the handler
     * idiom of the pool's other diagnostic tests. The canary record -
     * written through the very JULI logger the interceptor logs through,
     * not through JUL directly - proves the wiring observes those records
     * before the interceptor under test gets its chance to log.
     */
    private static final class PoolLogCapture implements AutoCloseable {
        private static final String CANARY = "slow-query-logging-handler-wiring-canary";

        private final Logger logger = Logger.getLogger(SlowQueryReport.class.getName());
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

        PoolLogCapture() {
            logger.addHandler(handler);
            logger.setLevel(Level.ALL);
            LogFactory.getLog(SlowQueryReport.class).error(CANARY);
            if (records.stream().noneMatch(r -> CANARY.equals(r.getMessage()))) {
                close();
                throw new AssertionError("the test handler must observe what this class logs");
            }
        }

        private static boolean isQueryReportWarning(LogRecord record, String marker) {
            String message = record.getMessage();
            return record.getLevel().intValue() == Level.WARNING.intValue()
                    && message != null
                    && message.contains(marker)
                    && message.contains("; time=")
                    && message.endsWith(" ms;");
        }

        void assertSlowQueryWarned(String sqlFragment) {
            Assertions.assertTrue(Arrays.stream(snapshot())
                            .anyMatch(r -> isQueryReportWarning(r, "Slow Query Report SQL=")
                                    && r.getMessage().contains(sqlFragment)),
                    "a slow query must be warned about in the log even when the"
                            + " statistics store is disabled, records: "
                            + Arrays.toString(snapshot()));
        }

        void assertFailedQueryWarned(String sqlFragment) {
            Assertions.assertTrue(Arrays.stream(snapshot())
                            .anyMatch(r -> isQueryReportWarning(r, "Failed Query Report SQL=")
                                    && r.getMessage().contains(sqlFragment)),
                    "a failed query must be warned about in the log even when the"
                            + " statistics store is disabled, records: "
                            + Arrays.toString(snapshot()));
        }

        void assertNoQueryReportWarnings() {
            Assertions.assertArrayEquals(new LogRecord[0],
                    Arrays.stream(snapshot())
                            .filter(r -> isQueryReportWarning(r, "Slow Query Report SQL=")
                                    || isQueryReportWarning(r, "Failed Query Report SQL="))
                            .toArray(LogRecord[]::new),
                    "no query must be warned about while the log flags are off");
        }

        private LogRecord[] snapshot() {
            synchronized (records) {
                return records.toArray(new LogRecord[0]);
            }
        }

        @Override
        public void close() {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }
    }
}
