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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * The "no statistics store to book to" WARN that
 * {@link SlowQueryReport#getQueryStats} writes when its instance can no
 * longer resolve a statistics store describes the session, not the
 * execution: once the store is gone it stays gone - the reference is only
 * ever restored by a borrow - while an application holding the statement it
 * opened on that session can keep executing on it indefinitely. The line
 * was written once per execution, so a statement that outlived its pool
 * produced a WARN for every query it ran, forever, and no configuration
 * switched that off: {@code logSlow} and {@code logFailed} gate the query
 * reports, not this line, so a pool configured to report nothing still
 * filled the log with it.
 * <p>
 * The tests drive the release the way the pool itself delivers it -
 * {@code reset(null, null)} on the interceptor link, which the teardown walk
 * calls - onto the live interceptor instance of a connection that is still borrowed,
 * which is also the state a borrow racing the close of the pool's last live
 * same-named pool lands in. One loss of the store now earns one WARN per
 * interceptor instance; a booking made on a recovered store re-arms the
 * report, so a later loss is still told about.
 * </p>
 */
class TestSlowQueryReportDeadSessionWarning extends JdbcPoolBaseTest {

    /* How many queries an application runs on the session after its
     * statistics store was taken away from the interceptor serving it. */
    private static final int EXECUTIONS_AFTER_RELEASE = 25;

    /* The text of the WARN under test; see
     * slowQueryReport.connectionClosedOrAbandoned. */
    private static final String NO_STORE = "has no statistics store to book to";

    /*
     * A distinct statement per execution, so the executions do not share a
     * statistics entry and none of them can be served from a driver cache.
     * The threshold is far above the runtime of these queries, so the slow
     * and failed report channels stay out of the captured log entirely.
     */
    private static String sql(int marker) {
        return "SELECT " + marker + " FROM SYSTEM_RANGE(1,1)";
    }

    /*
     * Drops the statistics reference the way the pool's own chain release
     * does, and pins that the window really opened: the instance can no
     * longer resolve any statistics entry.
     */
    private static void releaseStatisticsOf(SlowQueryReport report) {
        report.reset(null, null);
        Assertions.assertNull(report.queries,
                "the chain release must have taken the statistics reference away"
                        + " from this interceptor instance");
    }

    private static SlowQueryReport reportOf(Connection con) {
        SlowQueryReport report = interceptorOf(con, SlowQueryReport.class);
        Assertions.assertNotNull(report,
                "the chain must hold the configured SlowQueryReport instance");
        return report;
    }

    @Test
    void aSessionThatOutlivedItsPoolWarnsAboutItselfOnce() throws Exception {
        DataSource ds = newDataSource();
        String poolName = "DeadSessionWarn-" + System.nanoTime();
        ds.setName(poolName);
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport"
                + "(threshold=60000,logSlow=false,logFailed=false)");
        ds.getPoolProperties().setInitialSize(0);
        // A single session in the pool: the borrow below the close is the
        // same pooled connection, and so is served by the same interceptor
        // instance the WARN was already spent on.
        ds.getPoolProperties().setMaxActive(1);

        SlowQueryReport report;
        try (DeadSessionLogCapture logs = new DeadSessionLogCapture()) {
            Connection con = ds.getConnection();
            try {
                report = reportOf(con);
                Statement st = con.createStatement();
                try {
                    // The healthy execution before the loss is booked and
                    // warns about nothing
                    st.execute(sql(0));
                    Assertions.assertNotNull(SlowQueryReport.getPoolStats(poolName).get(sql(0)),
                            "the execution before the release must be booked in the store");
                    logs.assertDeadSessionWarnings(0);

                    releaseStatisticsOf(report);

                    for (int i = 1; i <= EXECUTIONS_AFTER_RELEASE; i++) {
                        st.execute(sql(i));
                    }
                    logs.assertDeadSessionWarnings(1,
                            "one loss of the statistics store must produce one WARN, not one"
                                    + " per execution on the dead session");
                } finally {
                    st.close();
                }
            } finally {
                con.close();
            }

            // The session comes back: the same interceptor instance recovers
            // a store, and the recovered instance must neither warn again for
            // the loss it has already reported nor stay silent about a new one
            try (Connection again = ds.getConnection(); Statement st = again.createStatement()) {
                SlowQueryReport recovered = reportOf(again);
                Assertions.assertSame(report, recovered,
                        "the one session the pool holds must have been handed out again, served by"
                                + " the interceptor instance the WARN was already spent on");
                Assertions.assertNotNull(recovered.queries,
                        "the borrow must have given the interceptor instance its store back");

                st.execute(sql(100));
                logs.assertDeadSessionWarnings(1,
                        "executing on a recovered store must not repeat the earlier WARN");

                releaseStatisticsOf(recovered);
                for (int i = 1; i <= 5; i++) {
                    st.execute(sql(200 + i));
                }
                logs.assertDeadSessionWarnings(2,
                        "a second loss of the statistics store must be reported again");
            }
        }
    }

    @Test
    void concurrentLossesAreReportedOnce() throws Exception {
        DataSource ds = newDataSource();
        ds.setName("DeadSessionConcurrent-" + System.nanoTime());
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport"
                + "(threshold=60000,logSlow=false,logFailed=false)");
        ds.getPoolProperties().setInitialSize(0);

        Connection con = ds.getConnection();
        SlowQueryReport report = reportOf(con);
        try (DeadSessionLogCapture logs = new DeadSessionLogCapture()) {
            releaseStatisticsOf(report);

            int threads = 16;
            CyclicBarrier barrier = new CyclicBarrier(threads);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread[] workers = new Thread[threads];
            for (int i = 0; i < threads; i++) {
                workers[i] = new Thread(() -> {
                    try {
                        barrier.await();
                        report.getQueryStats("SELECT concurrent-loss");
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    }
                }, "dead-session-report-" + i);
                workers[i].start();
            }
            for (Thread worker : workers) {
                worker.join(AWAIT_TIMEOUT_MS);
            }
            Assertions.assertNull(failure.get(), () -> "the reporters must not fail: " + failure.get());
            logs.assertDeadSessionWarnings(1,
                    "concurrent executions on a lost store must still report the loss once");
        } finally {
            con.close();
        }
    }

    /**
     * Counts the WARNs {@link SlowQueryReport} writes for a session whose
     * statistics store is gone, following the handler idiom of the pool's
     * other diagnostic tests. The canary record - written through the very
     * JULI logger the interceptor logs through, not through JUL directly -
     * proves the wiring observes those records before the interceptor under
     * test gets its chance to log.
     */
    private static final class DeadSessionLogCapture implements AutoCloseable {
        private static final String CANARY = "dead-session-warning-handler-wiring-canary";

        private final Logger logger = Logger.getLogger(SlowQueryReport.class.getName());
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

        DeadSessionLogCapture() {
            logger.addHandler(handler);
            logger.setLevel(Level.ALL);
            LogFactory.getLog(SlowQueryReport.class).error(CANARY);
            synchronized (records) {
                if (records.stream().noneMatch(r -> CANARY.equals(r.getMessage()))) {
                    throw new AssertionError("the test handler must observe what this class logs");
                }
            }
        }

        private static boolean isDeadSessionWarning(LogRecord record) {
            String message = record.getMessage();
            return record.getLevel().intValue() == Level.WARNING.intValue()
                    && message != null
                    && message.contains(NO_STORE);
        }

        private void assertDeadSessionWarnings(int expected, String what) {
            String[] seen;
            synchronized (records) {
                seen = records.stream().filter(DeadSessionLogCapture::isDeadSessionWarning)
                        .map(LogRecord::getMessage).toArray(String[]::new);
            }
            Assertions.assertEquals(expected, seen.length,
                    what + ", warnings: " + Arrays.toString(seen));
        }

        private void assertDeadSessionWarnings(int expected) {
            assertDeadSessionWarnings(expected, "a live session must not be warned about");
        }

        @Override
        public void close() {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }
    }
}
