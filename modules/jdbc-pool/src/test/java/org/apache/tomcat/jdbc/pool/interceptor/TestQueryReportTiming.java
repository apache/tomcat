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

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The duration and the timestamp an execution is reported with, checked
 * in isolation. The duration is a milliseconds interval measured with the
 * monotonic clock, so no step of the wall clock can make it negative - a
 * negative duration is never over the threshold and so loses the slow
 * query report, and the statistics book it as if it were real. The start
 * stays a wall clock timestamp, because that is what the reports record
 * the invocations against. A failed execution is reported on the same
 * rule: it is booked into the very same statistics, so its duration comes
 * from the same measurement rather than from the reported start.
 */
class TestQueryReportTiming {

    private static final String SQL = "SELECT 1";

    /**
     * Report that records what the statement proxy hands its report hooks.
     */
    private static class CapturingReport extends AbstractQueryReport {
        private boolean fastReported;
        private boolean slowReported;
        private boolean failedReported;
        private long fastStart;
        private long fastDelta;
        private long slowStart;
        private long slowDelta;
        private long failedStart;
        private long failedDelta;

        @Override
        protected void prepareStatement(String sql, long time) {
            // timing only - nothing to record
        }

        @Override
        protected void prepareCall(String query, long time) {
            // timing only - nothing to record
        }

        @Override
        public void closeInvoked() {
            // timing only - nothing to record
        }

        @Override
        protected String reportFailedQuery(String query, Object[] args, String name, long start, long delta,
                Throwable t) {
            failedReported = true;
            failedStart = start;
            failedDelta = delta;
            return super.reportFailedQuery(query, args, name, start, delta, t);
        }

        @Override
        protected String reportQuery(String query, Object[] args, String name, long start, long delta) {
            fastReported = true;
            fastStart = start;
            fastDelta = delta;
            return super.reportQuery(query, args, name, start, delta);
        }

        @Override
        protected String reportSlowQuery(String query, Object[] args, String name, long start, long delta) {
            slowReported = true;
            slowStart = start;
            slowDelta = delta;
            return super.reportSlowQuery(query, args, name, start, delta);
        }
    }

    /**
     * Statement whose executions take the given number of milliseconds.
     */
    private static Statement slowDelegate(long sleepMillis) {
        return (Statement) Proxy.newProxyInstance(TestQueryReportTiming.class.getClassLoader(),
                new Class<?>[] { Statement.class }, (proxy, method, args) -> {
                    if ("execute".equals(method.getName())) {
                        if (sleepMillis > 0) {
                            Thread.sleep(sleepMillis);
                        }
                        return Boolean.TRUE;
                    }
                    return null;
                });
    }

    private static Object instrumentedProxy(CapturingReport report, Statement delegate) {
        return Proxy.newProxyInstance(TestQueryReportTiming.class.getClassLoader(),
                new Class<?>[] { Statement.class }, report.new StatementProxy(delegate, SQL));
    }

    /**
     * Statement whose executions take the given number of milliseconds and
     * then fail, as a driver that refuses the statement does.
     */
    private static Statement failingDelegate(long sleepMillis) {
        return (Statement) Proxy.newProxyInstance(TestQueryReportTiming.class.getClassLoader(),
                new Class<?>[] { Statement.class }, (proxy, method, args) -> {
                    if ("execute".equals(method.getName())) {
                        if (sleepMillis > 0) {
                            Thread.sleep(sleepMillis);
                        }
                        throw new SQLException("the driver refused the statement");
                    }
                    return null;
                });
    }

    @Test
    void anExecutionReportsADurationMatchingItsDurationAndAWallClockStart() throws Throwable {
        final long sleepMillis = 40;
        CapturingReport report = new CapturingReport();
        long wallStart = System.currentTimeMillis();
        Object proxy = instrumentedProxy(report, slowDelegate(sleepMillis));
        Proxy.getInvocationHandler(proxy).invoke(proxy, Statement.class.getMethod("execute", String.class),
                new Object[] { SQL });
        long wallEnd = System.currentTimeMillis();

        Assertions.assertTrue(report.fastReported && !report.slowReported,
                "an execution below the threshold must be reported as a query, not as a slow query");
        // Thread.sleep is a lower bound and the duration is rounded to the
        // nearest millisecond, so the lower bound only takes a little slack
        // and the upper bound stays wide for a loaded machine
        Assertions.assertTrue(report.fastDelta >= sleepMillis - 10,
                "the reported duration must track the time the execution took, was "
                        + report.fastDelta + " ms for a " + sleepMillis + " ms execution");
        Assertions.assertTrue(report.fastDelta <= sleepMillis + 900,
                "the reported duration must stay near the time the execution took, was "
                        + report.fastDelta + " ms for a " + sleepMillis + " ms execution");
        Assertions.assertTrue(report.fastStart >= wallStart - 1000 && report.fastStart <= wallEnd + 1000,
                "the start the hooks carry must be the wall clock instant the execution "
                        + "began, was " + report.fastStart + " with the test at " + wallStart);
    }

    @Test
    void anExecutionIsNeverReportedWithANegativeDuration() throws Throwable {
        CapturingReport report = new CapturingReport();
        // an execution slower than a threshold of zero reaches the slow
        // path, which must receive a sound duration and start too
        report.setThreshold(0);
        Object proxy = instrumentedProxy(report, slowDelegate(40));
        Proxy.getInvocationHandler(proxy).invoke(proxy, Statement.class.getMethod("execute", String.class),
                new Object[] { SQL });

        Assertions.assertTrue(report.slowReported && !report.fastReported,
                "an execution over the threshold must be reported as a slow query");
        Assertions.assertTrue(report.slowDelta >= 0,
                "a duration handed to the report hooks can never be negative, was " + report.slowDelta);
        Assertions.assertTrue(report.slowStart > 0,
                "a reported execution must carry a wall clock start, was " + report.slowStart);
    }

    @Test
    void aNegativeThresholdRecordsEveryExecutionAsMeasuredAndNoneAsSlow() throws Throwable {
        CapturingReport report = new CapturingReport();
        // the documented meaning of the negative threshold: record every
        // query - which means every execution reaches reportQuery, the
        // hook for an execution that was measured and is not slow; it
        // must not arrive as a slow query, which for SlowQueryReport is a
        // WARN log line for every query the pool executes
        report.setThreshold(-1);
        Object proxy = instrumentedProxy(report, slowDelegate(0));
        Proxy.getInvocationHandler(proxy).invoke(proxy, Statement.class.getMethod("execute", String.class),
                new Object[] { SQL });

        Assertions.assertTrue(report.fastReported && !report.slowReported,
                "a fast execution under a negative threshold must be recorded as a measured "
                        + "query and must never be reported as slow");
        Assertions.assertTrue(report.fastDelta >= 0,
                "the recorded duration must be a real duration, was " + report.fastDelta);
    }

    @Test
    void aFailingExecutionReportsADurationMatchingItsDurationAndAWallClockStart() throws Throwable {
        final long sleepMillis = 40;
        CapturingReport report = new CapturingReport();
        long wallStart = System.currentTimeMillis();
        Object proxy = instrumentedProxy(report, failingDelegate(sleepMillis));
        final Method execute = Statement.class.getMethod("execute", String.class);
        final Object[] args = new Object[] { SQL };
        Assertions.assertThrows(SQLException.class,
                () -> Proxy.getInvocationHandler(proxy).invoke(proxy, execute, args),
                "the driver's exception must reach the application, not the reporting");
        long wallEnd = System.currentTimeMillis();

        Assertions.assertTrue(report.failedReported,
                "a failed execution must reach the failed query hook");
        Assertions.assertFalse(report.fastReported || report.slowReported,
                "an execution that failed must not be reported as measured or as slow");
        // the same bounds the successful execution above is held to: the
        // failure is measured on the same clock, with the same rounding
        Assertions.assertTrue(report.failedDelta >= sleepMillis - 10,
                "the reported duration must track the time the failing execution took, was "
                        + report.failedDelta + " ms for a " + sleepMillis + " ms execution");
        Assertions.assertTrue(report.failedDelta <= sleepMillis + 900,
                "the reported duration must stay near the time the failing execution took, was "
                        + report.failedDelta + " ms for a " + sleepMillis + " ms execution");
        Assertions.assertTrue(report.failedStart >= wallStart - 1000 && report.failedStart <= wallEnd + 1000,
                "the start the failed query hook carries must be the wall clock instant the "
                        + "execution began, was " + report.failedStart + " with the test at " + wallStart);
    }

    @Test
    void aFailedExecutionIsBookedWithItsMeasuredDurationAndNotWithAClockStep() {
        // The hook is handed the wall clock instant the execution started and
        // the duration measured with the monotonic clock. A wall clock that
        // stepped backwards while the execution was running leaves the
        // reading taken after the failure behind that start, which is what a
        // start ahead of the moment the hook is called at stands for: a
        // duration recomputed from the start turns negative there, and this
        // is where such a duration used to be booked into the statistics and
        // written to the log.
        String sql = "SELECT stepped_back_clock_" + System.nanoTime();
        final long measuredMillis = 42;
        SlowQueryReport report = new SlowQueryReport();
        report.queries = new ConcurrentHashMap<>();
        report.setLogFailed(true);
        long steppedBackStart = System.currentTimeMillis() + 600000;

        Logger logger = Logger.getLogger(SlowQueryReport.class.getName());
        Level originalLevel = logger.getLevel();
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
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
        logger.addHandler(handler);
        logger.setLevel(Level.ALL);
        try {
            report.reportFailedQuery(sql, null, "executeQuery", steppedBackStart, measuredMillis,
                    new SQLException("the driver refused the statement"));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }

        SlowQueryReport.QueryStats qs = report.queries.get(sql);
        Assertions.assertNotNull(qs, "the failed execution must be booked in the store");
        Assertions.assertEquals(1, qs.getNrOfInvocations(),
                "the failed execution must count as one invocation");
        Assertions.assertEquals(measuredMillis, qs.getTotalInvocationTime(),
                "the booked total must be the measured duration, not the difference "
                        + "of two wall clock readings");
        Assertions.assertEquals(measuredMillis, qs.getMinInvocationTime(),
                "a clock step must not pull the booked minimum below the measured duration");
        Assertions.assertEquals(measuredMillis, qs.getMaxInvocationTime(),
                "the booked maximum must be the measured duration");
        Assertions.assertTrue(records.stream().anyMatch(r -> r.getLevel().intValue() == Level.WARNING.intValue()
                        && r.getMessage() != null && r.getMessage().contains(sql)
                        && r.getMessage().contains("; time=" + measuredMillis + " ms;")),
                "the failed query WARN must report the measured duration, records: " + records);
    }

    @Test
    void aNonExecutionReachesNeitherReportHook() throws Throwable {
        CapturingReport report = new CapturingReport();
        // even with every execution reported, a method that is not an
        // execution keeps its sentinel duration and reaches no hook at all
        report.setThreshold(-1);
        Object proxy = instrumentedProxy(report, slowDelegate(0));
        Proxy.getInvocationHandler(proxy).invoke(proxy, Statement.class.getMethod("clearBatch"), null);

        Assertions.assertFalse(report.fastReported || report.slowReported,
                "a method that is not an execution must not reach any report hook");
    }
}
