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
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * The successful execution hooks book their statistics against a point in
 * time on the wall clock - the moment the execution is recorded as having
 * happened - the same way the failure hook books its event. Handing them
 * the execution's start instead left the recorded dates lagging by the
 * whole duration of the query: a long query that had just finished looked
 * last-invoked at its start time, aged out of the statistics ahead of its
 * turn by the eviction that orders on that timestamp, and reported its
 * max/min invocation dates early through JMX. The duration booked
 * alongside the timestamp is unaffected: it stays the monotonic elapsed
 * time the statement proxy measured.
 *
 * <p>Reproducer for the booking-timestamp defect of
 * {@code defects-reports/defects-org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport.md}.</p>
 */
class TestSlowQueryBookingTimestamp extends JdbcPoolBaseTest {

    /* A database-side wait long enough that a booking against the
     * execution start trails the correct timestamp by more than any
     * scheduling jitter in the test itself. */
    private static final long SLOW_WAIT_MILLIS = 300;

    private String slowSql() throws SQLException {
        try (Connection admin = adminConnection();
                Statement st = admin.createStatement()) {
            st.execute("CREATE ALIAS IF NOT EXISTS SLOW_SLEEP FOR "
                    + "\"java.lang.Thread.sleep(long)\"");
        }
        return "SELECT 1 FROM SYSTEM_RANGE(1,1) WHERE SLOW_SLEEP(" + SLOW_WAIT_MILLIS + ") IS NULL";
    }

    @Test
    void aSlowQueryIsBookedAgainstTheTimeItFinishedNotTheTimeItStarted() throws Exception {
        String poolName = "booking-" + System.nanoTime();
        DataSource ds = newDataSource();
        ds.getPoolProperties().setName(poolName);
        ds.getPoolProperties().setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport(threshold=0)");
        ds.createPool();

        String sql = slowSql();
        long beforeExecution = System.currentTimeMillis();
        try (Connection pooled = ds.getConnection();
                Statement st = pooled.createStatement()) {
            st.execute(sql);
        }

        Map<String, SlowQueryReport.QueryStats> stats = SlowQueryReport.getPoolStats(poolName);
        Assertions.assertNotNull(stats, "the running pool must publish its statistics");
        SlowQueryReport.QueryStats booked = stats.get(sql);
        Assertions.assertNotNull(booked, "the executed query must be booked");
        Assertions.assertEquals(1, booked.getNrOfInvocations());

        // The execution is recorded when it is booked, which is after the
        // database-side wait: the timestamps must therefore sit past the
        // point the execution started by at least the duration it slept.
        // A booking against the execution start sat at the start instead,
        // this many milliseconds too early.
        String message = "the invocation date must be the time the execution was "
                + "recorded, not the time it started";
        Assertions.assertTrue(booked.getMaxInvocationDate() >= beforeExecution + SLOW_WAIT_MILLIS,
                message + ", maxInvocationDate=" + booked.getMaxInvocationDate()
                        + " beforeExecution=" + beforeExecution);
        Assertions.assertTrue(booked.getMinInvocationDate() >= beforeExecution + SLOW_WAIT_MILLIS,
                message + ", minInvocationDate=" + booked.getMinInvocationDate()
                        + " beforeExecution=" + beforeExecution);
    }
}
