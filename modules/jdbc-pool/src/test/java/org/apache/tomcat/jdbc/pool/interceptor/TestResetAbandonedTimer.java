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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * The {@code ResetAbandonedTimer} interceptor must treat an attempted
 * operation as activity, whether the operation succeeded or failed: an
 * application kept busy by executions that fail (a retry loop against
 * transient SQL errors) is active and must not have its connection
 * abandoned underneath it, while a borrowed connection that really is
 * idle must still be reclaimed. Every test drives a busy connection and
 * an idle control connection against a one-second abandoned timeout and
 * asserts which of the two physical sessions the cleaner killed.
 */
class TestResetAbandonedTimer extends JdbcPoolBaseTest {

    /*
     * Time the busy connection is kept making calls. It must stay well
     * above removeAbandonedTimeout plus one sweep interval so that the
     * idle control connection - and, before the fix, the busy one too -
     * is certain to expire while the storm is still running.
     */
    private static final long STORM_MILLIS = 2500;

    /*
     * Lower bound on storm iterations. At a one-second abandoned
     * timeout a genuine retry loop makes far more calls than this; the
     * bound only catches a storm that never really ran.
     */
    private static final int MIN_STORM_CALLS = 5;

    private DataSource abandonedTimerPool() throws SQLException {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.ResetAbandonedTimer");
        ds.getPoolProperties().setRemoveAbandoned(true);
        ds.getPoolProperties().setRemoveAbandonedTimeout(1); // seconds
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(100);
        ds.createPool();
        pool = ds;
        return ds;
    }

    /* The pool under test, for forced teardown. */
    private DataSource pool;

    /*
     * These tests deliberately end with connections still borrowed. The
     * regular data source teardown only disconnects idle connections, so
     * the sessions of the borrowed ones would linger until the garbage
     * collector finalizes them and leak their session counts into the
     * starting point of the next test; force-close disconnects the busy
     * ones as well, right away.
     */
    @AfterEach
    void disconnectLeakedBusyConnections() {
        if (pool != null) {
            pool.close(true);
        }
    }

    /*
     * The busy connection ends with one live physical session carrying
     * the marker it was given before the storm, and the idle control
     * connection has lost its session to the abandoned sweep.
     */
    private void assertBusySurvivedAndIdleAbandoned(Connection busy) throws SQLException {
        Integer marker = sessionMarker(busy);
        Assertions.assertNotNull(marker,
                "the session of the busy connection must still be alive");
        Assertions.assertEquals(1, marker.intValue(),
                "the busy connection must still own its original session");
        awaitSessions(1);
    }

    /*
     * Borrows an idle control connection - left borrowed and untouched,
     * it exists only to prove the abandoned sweep is running - and a
     * busy connection, each identified by a session marker, and pins
     * the session count to two while both are still live. The caller
     * must have waited out any sessions dying from a previous test
     * first: with a one-second abandoned timeout there is no time to
     * absorb stale sessions once these connections have started their
     * countdown. Returns the busy connection.
     */
    private Connection borrowBusyWithIdleControl(DataSource ds) throws SQLException {
        Connection idle = ds.getConnection();
        setSessionMarker(idle, 2);
        Connection busy = ds.getConnection();
        setSessionMarker(busy, 1);
        Assertions.assertEquals(2, poolSessions(),
                "both borrowed connections must start on live physical sessions");
        return busy;
    }

    @Test
    void successfulQueriesKeepConnectionAliveWhileIdleControlIsAbandoned() throws Exception {
        awaitSessions(0);
        DataSource ds = abandonedTimerPool();
        Connection busy = borrowBusyWithIdleControl(ds);

        int queries = 0;
        long deadline = System.currentTimeMillis() + STORM_MILLIS;
        try (Statement st = busy.createStatement()) {
            do {
                try (ResultSet rs = st.executeQuery("SELECT 1")) {
                    Assertions.assertTrue(rs.next());
                }
                queries++;
            } while (System.currentTimeMillis() < deadline);
        }
        Assertions.assertTrue(queries >= MIN_STORM_CALLS,
                "the keep-alive loop must have run, only " + queries + " queries");

        assertBusySurvivedAndIdleAbandoned(busy);
    }

    /*
     * An application retrying statements that all fail - against lock
     * timeouts, transient SQL errors, or simply bad SQL - is active.
     * The failures arrive through the failed-query reporting hook,
     * which must reset the abandoned timer just like the successful
     * report hooks do; otherwise the pool cleaner kills the connection
     * under the app mid-retry.
     */
    @Test
    void failedStatementExecutionsKeepConnectionAlive() throws Exception {
        awaitSessions(0);
        DataSource ds = abandonedTimerPool();
        Connection busy = borrowBusyWithIdleControl(ds);

        String sql = "SELECT * FROM no_such_table_" + System.nanoTime();
        int failures = 0;
        long deadline = System.currentTimeMillis() + STORM_MILLIS;
        try (Statement st = busy.createStatement()) {
            do {
                SQLException failure = Assertions.assertThrows(SQLException.class,
                        () -> st.executeQuery(sql));
                // Fail on the missing table, never on a session lost mid-storm.
                Assertions.assertEquals("42S04", failure.getSQLState(),
                        "the storm must fail on the missing table: " + failure.getMessage());
                failures++;
            } while (System.currentTimeMillis() < deadline);
        }
        Assertions.assertTrue(failures >= MIN_STORM_CALLS,
                "the failure storm must have run, only " + failures + " failures");

        assertBusySurvivedAndIdleAbandoned(busy);
    }

    /*
     * A failing call made directly on the pooled connection - here a
     * prepare that the driver rejects because the SQL names a missing
     * table - is activity as much as a successful one. The reset was
     * performed only after the intercepted call returned normally, so
     * an application that stays active through failing connection-level
     * calls was abandoned like an idle one.
     */
    @Test
    void failedConnectionLevelCallsKeepConnectionAlive() throws Exception {
        awaitSessions(0);
        DataSource ds = abandonedTimerPool();
        Connection busy = borrowBusyWithIdleControl(ds);

        String sql = "SELECT * FROM no_such_table_" + System.nanoTime();
        int failures = 0;
        long deadline = System.currentTimeMillis() + STORM_MILLIS;
        do {
            // H2 compiles at prepare time, so the prepare itself throws
            SQLException failure = Assertions.assertThrows(SQLException.class,
                    () -> busy.prepareStatement(sql));
            // Fail on the missing table, never on a session lost mid-storm.
            Assertions.assertEquals("42S04", failure.getSQLState(),
                    "the storm must fail on the missing table: " + failure.getMessage());
            failures++;
        } while (System.currentTimeMillis() < deadline);
        Assertions.assertTrue(failures >= MIN_STORM_CALLS,
                "the failure storm must have run, only " + failures + " failures");

        assertBusySurvivedAndIdleAbandoned(busy);
    }
}
