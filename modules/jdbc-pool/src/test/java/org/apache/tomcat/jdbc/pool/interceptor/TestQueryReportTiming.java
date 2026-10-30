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
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The duration and the timestamp an execution is reported with, checked
 * in isolation. The duration is a milliseconds interval measured with the
 * monotonic clock, so no step of the wall clock can make it negative - a
 * negative duration is never over the threshold and so loses the slow
 * query report, and the statistics book it as if it were real. The start
 * stays a wall clock timestamp, because that is what the reports record
 * the invocations against.
 */
class TestQueryReportTiming {

    private static final String SQL = "SELECT 1";

    /**
     * Report that records what the statement proxy hands its report hooks.
     */
    private static class CapturingReport extends AbstractQueryReport {
        private boolean fastReported;
        private boolean slowReported;
        private long fastStart;
        private long fastDelta;
        private long slowStart;
        private long slowDelta;

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
        // every execution goes down the slow path, even a trivially fast one
        report.setThreshold(-1);
        Object proxy = instrumentedProxy(report, slowDelegate(0));
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
