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
 * The slow-query WARN must not depend on the statistics entry being
 * resolvable. {@link SlowQueryReport#reportSlowQuery} booked the query and
 * logged the WARN inside the same {@code getQueryStats(sql) != null}
 * guard, while its sibling {@code reportFailedQuery} logs outside its
 * guard - so on every window in which the instance cannot resolve its
 * statistics, the slow WARN silently stopped being written while
 * {@link SlowQueryReportJmx#reportSlowQuery} went on sending the JMX
 * notification behind only {@code isLogSlow()}: the two reporting
 * channels disagreed on the same event.
 * <p>
 * {@code getQueryStats} legitimately returns {@code null} whenever this
 * instance's {@code queries} reference is gone - the pool delivers
 * {@code reset(null, null)} to every link of a chain it releases, which
 * is where the reference is dropped - and also on the transient loss of
 * the post-putIfAbsent get race against capacity eviction. The tests here
 * drive that very release callback, which {@code ConnectionPool.release}
 * delivers through {@code PooledConnection.setHandler(null)}, onto the
 * live interceptor instance of a still-borrowed connection: the state a
 * borrow racing the close of the last live pool of the name lands in as
 * well. The application is executing on a session that is still open, so
 * the slow query really runs and really reports - only the statistics
 * lookup is gone.
 * </p>
 */
class TestSlowQueryReportSlowLogAfterChainRelease extends JdbcPoolBaseTest {

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

    /*
     * Releases the statistics reference the way the pool's own chain
     * release does - reset(null, null) on the interceptor link, the call
     * PooledConnection.setHandler(null) walks the chain with - while the
     * borrow, the session and the application's statement all stay live,
     * and pins that the window really opened: the instance can no longer
     * resolve any statistics entry.
     */
    private static void releaseStatisticsOf(SlowQueryReport report) {
        report.reset(null, null);
        Assertions.assertNull(report.queries,
                "the chain release must have taken the statistics reference away"
                        + " from this interceptor instance");
    }

    @Test
    void slowQueryIsWarnedAboutWhenTheStatisticsCannotBeResolved() throws Exception {
        String poolName = "SlowLogNoStats-" + System.nanoTime();
        DataSource ds = newDataSource();
        ds.setName(poolName);
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport"
                + "(logSlow=true,threshold=0)");
        ds.getPoolProperties().setInitialSize(0);

        try (PoolLogCapture logs = new PoolLogCapture();
                Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            SlowQueryReport report = interceptorOf(con, SlowQueryReport.class);
            Assertions.assertNotNull(report,
                    "the chain must hold the configured SlowQueryReport instance");

            releaseStatisticsOf(report);

            // The execute succeeds (no exception out of it) and waits in
            // the database, so this is the slow path of a live statement
            // whose interceptor lost its statistics - the pre-fix shape
            // logged the WARN only when getQueryStats handed back an
            // entry, and here it hands back none
            st.execute(slowSql(1));

            logs.assertSlowQueryWarned("SELECT 1 FROM");
        }
    }

    @Test
    void slowQueryWarnsAndNotifiesTogetherWhenTheStatisticsCannotBeResolved() throws Exception {
        String poolName = "SlowLogChannels-" + System.nanoTime();
        DataSource ds = newDataSource();
        ds.setName(poolName);
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                + "(notifyPool=false,threshold=0,logSlow=true)");
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

            releaseStatisticsOf(report);

            st.execute(slowSql(2));

            // The JMX channel fired in this window even before the fix -
            // half of the reported disagreement. The fix must not silence
            // it, and the log channel must join it: one reported slow
            // query, both channels.
            Assertions.assertTrue(received.stream().anyMatch(n ->
                            SlowQueryReportJmx.SLOW_QUERY_NOTIFICATION.equals(n.getType())
                                    && n.getMessage() != null
                                    && n.getMessage().contains("SELECT 2 FROM")),
                    "the slow query must still notify through the name's broadcaster"
                            + " in this window, received: " + received);
            logs.assertSlowQueryWarned("SELECT 2 FROM");
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
        private static final String CANARY = "slow-query-log-handler-wiring-canary";

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

        private static boolean isSlowQueryWarning(LogRecord record) {
            String message = record.getMessage();
            return record.getLevel().intValue() == Level.WARNING.intValue()
                    && message != null
                    && message.contains("Slow Query Report SQL=")
                    && message.contains("; time=")
                    && message.endsWith(" ms;");
        }

        void assertSlowQueryWarned(String sqlFragment) {
            Assertions.assertTrue(Arrays.stream(snapshot())
                            .anyMatch(r -> isSlowQueryWarning(r) && r.getMessage().contains(sqlFragment)),
                    "a slow query whose statistics cannot be resolved must still be"
                            + " warned about in the log, records: " + Arrays.toString(snapshot()));
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
