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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * The {@code ResetAbandonedTimer} interceptor must treat reading a result
 * set as activity: an application iterating a large result set - doing
 * its per-row work between the reads - is active and must not have its
 * connection abandoned underneath it, not even when the reads it makes
 * fail, while a borrowed connection that really is idle must still be
 * reclaimed. The keep-alive tests drive a streaming connection and an
 * idle control connection against a one-second abandoned timeout and
 * assert which of the two physical sessions the cleaner killed. The
 * composition test pins that the result set handles the interceptor
 * hands out stay usable, obey the identity contract of a handle, and
 * compose with the statement facade on every access path that yields a
 * result set.
 */
class TestResetAbandonedTimerStreaming extends JdbcPoolBaseTest {

    /*
     * Time the busy connection is kept streaming rows. It must stay well
     * above removeAbandonedTimeout plus one sweep interval so that the
     * idle control connection - and, before the fetch traffic reset the
     * timer, the streaming one too - is certain to expire while the
     * iteration is still running.
     */
    private static final long STREAM_MILLIS = 2500;

    /*
     * Lower bound on streamed rows. An app iterating rows between the
     * per-row pauses makes far more than this; the bound only catches an
     * iteration that never really ran.
     */
    private static final int MIN_STREAM_ROWS = 20;

    /*
     * Lower bound on failing result set reads. At a one-second abandoned
     * timeout a genuine retry loop makes far more calls than this; the
     * bound only catches a storm that never really ran.
     */
    private static final int MIN_FAILURE_CALLS = 5;

    private DataSource streamingPool(boolean useStatementFacade) throws SQLException {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.ResetAbandonedTimer");
        ds.getPoolProperties().setRemoveAbandoned(true);
        ds.getPoolProperties().setRemoveAbandonedTimeout(1); // seconds
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(100);
        ds.getPoolProperties().setUseStatementFacade(useStatementFacade);
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
     * Borrows an idle control connection - left borrowed and untouched,
     * it exists only to prove the abandoned sweep is running - and a
     * busy connection, each identified by a session marker, and pins the
     * session count to two while both are still live. The caller must
     * have waited out any sessions dying from a previous test first:
     * with a one-second abandoned timeout there is no time to absorb
     * stale sessions once these connections have started their
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

    /*
     * The busy connection ends with one live physical session carrying
     * the marker it was given before the iteration, and the idle control
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
     * Iterates a generated multi-row result set with a pause between the
     * reads - the batch processing between fetches that the abandoned
     * timeout is meant to look over - for STREAM_MILLIS, and returns the
     * number of rows read. The small fetch size keeps the driver pulling
     * from the server during the iteration.
     */
    private int streamSlowly(Connection busy) throws SQLException {
        int rows = 0;
        try (Statement st = busy.createStatement()) {
            st.setFetchSize(10);
            try (ResultSet rs = st.executeQuery(
                    "SELECT X FROM SYSTEM_RANGE(1,100000)")) {
                long deadline = System.currentTimeMillis() + STREAM_MILLIS;
                while (System.currentTimeMillis() < deadline && rs.next()) {
                    rows++;
                    sleep(10);
                }
            }
        }
        return rows;
    }

    private void assertStreamingKeepsConnectionAlive(boolean useStatementFacade) throws Exception {
        awaitSessions(0);
        DataSource ds = streamingPool(useStatementFacade);
        Connection busy = borrowBusyWithIdleControl(ds);

        int rows = streamSlowly(busy);
        Assertions.assertTrue(rows >= MIN_STREAM_ROWS,
                "the streaming loop must have run, only " + rows + " rows");

        assertBusySurvivedAndIdleAbandoned(busy);
    }

    /*
     * An application iterating a result set is active for as long as it
     * reads, not only until the query returns the cursor position. The
     * abandoned timer must be reset by the fetch traffic driven by
     * ResultSet.next() - through the statement facade that wraps the
     * interceptor's result set proxy by default.
     */
    @Test
    void streamingAResultSetKeepsConnectionAlive() throws Exception {
        assertStreamingKeepsConnectionAlive(true);
    }

    /*
     * The result set keep-alive belongs to the interceptor itself, not
     * to the statement facade layered around it: with the facade
     * switched off, fetching rows must keep the connection alive just
     * the same.
     */
    @Test
    void streamingKeepsConnectionAliveWithoutStatementFacade() throws Exception {
        assertStreamingKeepsConnectionAlive(false);
    }

    /*
     * An application kept busy by result set reads that all fail - a bad
     * column index in row-processing code, against a result set it still
     * iterates - is active. A read attempt must reset the abandoned
     * timer even when it failed, the same contract the connection-level
     * calls and the statement executions have since the failed-operation
     * fix: otherwise the pool cleaner kills the connection - and the
     * open cursor with it - under the app mid-loop.
     */
    @Test
    void failedResultSetReadsKeepConnectionAlive() throws Exception {
        awaitSessions(0);
        DataSource ds = streamingPool(true);
        Connection busy = borrowBusyWithIdleControl(ds);

        int failures = 0;
        String failureState = null;
        long deadline = System.currentTimeMillis() + STREAM_MILLIS;
        try (Statement st = busy.createStatement();
                ResultSet rs = st.executeQuery("SELECT X FROM SYSTEM_RANGE(1,2)")) {
            Assertions.assertTrue(rs.next());
            do {
                SQLException failure = Assertions.assertThrows(SQLException.class,
                        () -> rs.getBlob(999));
                if (failureState == null) {
                    failureState = failure.getSQLState();
                } else {
                    //Never fail on a session lost mid-storm - that would
                    //look like a different failure, pin the identity
                    Assertions.assertEquals(failureState, failure.getSQLState(),
                            "the storm must fail on the bad column index: "
                                    + failure.getMessage());
                }
                failures++;
            } while (System.currentTimeMillis() < deadline);
        }
        Assertions.assertTrue(failures >= MIN_FAILURE_CALLS,
                "the failure storm must have run, only " + failures + " failures");

        assertBusySurvivedAndIdleAbandoned(busy);
    }

    /*
     * The result set handle the interceptor inserts under (or, without
     * the facade, in place of) the facade's own handle must behave: it
     * must yield the rows, obey the identity contract of a handle (a
     * handle is equal to itself and to nothing else), hand out a working
     * statement through getStatement() - the handle in use by this
     * interceptor, which an interceptor configured around it may have
     * wrapped further - close cleanly, and do so on all three paths that
     * produce a result set - executeQuery, getResultSet and
     * getGeneratedKeys - with the facade composing around it.
     */
    @Test
    void resultSetsOnEveryAccessPathStayUsableAndHandleStable() throws Exception {
        awaitSessions(0);
        assertResultSetHandlesCompose(true);
        assertResultSetHandlesCompose(false);
    }

    private void assertResultSetHandlesCompose(boolean useStatementFacade) throws SQLException {
        DataSource ds = streamingPool(useStatementFacade);
        Connection con = ds.getConnection();
        String table = uniqueTable();
        try (Statement st = con.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT X FROM SYSTEM_RANGE(1,3)")) {
                Assertions.assertSame(st, rs.getStatement(),
                        "the statement behind the result set must be the handle in use");
                //The identity contract of a handle, as the facade's own
                //handles honour it (see TestProxyContract)
                Assertions.assertTrue(rs.equals(rs),
                        "a result set handle must be equal to itself");
                Assertions.assertFalse(rs.equals(null),
                        "a result set handle is not equal to null");
                Assertions.assertFalse(rs.equals(st),
                        "a result set handle is not equal to its statement");
                Assertions.assertFalse(rs.equals(new Object()),
                        "a result set handle is not equal to a foreign object");
                Assertions.assertTrue(st.equals(st),
                        "a statement handle must be equal to itself");
                Assertions.assertFalse(st.equals(null),
                        "a statement handle is not equal to null");
                Assertions.assertFalse(st.equals(rs),
                        "a statement handle is not equal to its result set");
                int rows = 0;
                while (rs.next()) {
                    Assertions.assertEquals(rows + 1, rs.getInt(1));
                    rows++;
                }
                Assertions.assertEquals(3, rows);
            }

            st.execute("CREATE TABLE " + table
                    + "(X INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY)");
            try (PreparedStatement ins = con.prepareStatement(
                    "INSERT INTO " + table + "(X) VALUES (DEFAULT)",
                    Statement.RETURN_GENERATED_KEYS)) {
                Assertions.assertEquals(1, ins.executeUpdate());
                try (ResultSet keys = ins.getGeneratedKeys()) {
                    Assertions.assertSame(ins, keys.getStatement());
                    Assertions.assertTrue(keys.next(),
                            "the generated key must come through the proxy");
                }
            }

            Assertions.assertTrue(st.execute("SELECT X FROM SYSTEM_RANGE(1,2)"));
            try (ResultSet rs = st.getResultSet()) {
                Assertions.assertNotNull(rs,
                        "the query result must be reachable through getResultSet");
                Assertions.assertSame(st, rs.getStatement());
                int rows = 0;
                while (rs.next()) {
                    rows++;
                }
                Assertions.assertEquals(2, rows);
            }
        }

        try (PreparedStatement ps = con.prepareStatement("SELECT X FROM SYSTEM_RANGE(4,6)")) {
            try (ResultSet rs = ps.executeQuery()) {
                Assertions.assertSame(ps, rs.getStatement());
                int rows = 0;
                while (rs.next()) {
                    rows++;
                }
                Assertions.assertEquals(3, rows);
            }
        }

        try (Statement st = con.createStatement()) {
            st.execute("DROP TABLE " + table);
        }
        con.close();
    }
}
