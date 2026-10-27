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
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * Expected functionality of the interceptor chain, exercised against
 * the real database: slow/failed query reporting, statement timeout
 * cancellation, per-connection statement caching and the decorated
 * statement proxy contract.
 */
class TestInterceptorSemantics extends JdbcPoolBaseTest {

    /**
     * Heavy pure-SQL aggregate that keeps running for well over a second.
     * It must stay heavy: it has to still be executing when the query
     * timeout interceptor's cancellation arrives.
     */
    private static final String HEAVY_SQL =
            "SELECT COUNT(*) FROM SYSTEM_RANGE(1,400000000) r1, SYSTEM_RANGE(1,3)";

    /*
     * A slow query that is slow for a wall-clock reason, not a CPU reason.
     * Burning cycles until the clock crosses the threshold scales with the
     * hardware - a billion-row COUNT cost a minute on a slow core and gives
     * no margin on a fast one. Instead the instrumented UPDATE is made to
     * wait on the row lock a second session holds open, and that session
     * commits a fixed window later: the execution time is the lock wait,
     * so it exceeds the threshold on any machine while the test itself
     * stays sub-second.
     */
    @Test
    void slowQueryIsRecordedInPoolStats() throws Exception {
        final long thresholdMillis = 100;
        final long lockHoldMillis = 5 * thresholdMillis;

        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport(threshold="
                        + thresholdMillis + ")");
        ds.createPool();
        String poolName = ds.getPoolProperties().getPoolName();
        String table = uniqueTable();

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE " + table + " (ID INT PRIMARY KEY, V INT)");
            st.execute("INSERT INTO " + table + " VALUES (1, 1)");
        }

        // hold the row lock open...
        Connection blocker = ds.getConnection();
        blocker.setAutoCommit(false);
        try (PreparedStatement ps = blocker.prepareStatement(
                "UPDATE " + table + " SET V = V + 10 WHERE ID = 1")) {
            Assertions.assertEquals(1, ps.executeUpdate());
        }
        // ...and release it while the instrumented statement is blocked on it
        Thread releaseLock = new Thread(() -> {
            sleep(lockHoldMillis);
            try {
                blocker.commit();
            } catch (SQLException ignore) {
                // the assertions below report the broken state
            }
        }, "release-row-lock");

        String nonce = "nonce_" + System.nanoTime();
        String sql = "UPDATE " + table + " SET V = V + 1 WHERE ID = 1 /* " + nonce + " */";
        long start;
        long elapsed;
        releaseLock.start();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            // the lock wait must not be cut short by the driver's own limit
            st.execute("SET LOCK_TIMEOUT 60000");
            start = System.currentTimeMillis();
            Assertions.assertFalse(st.execute(sql), "an UPDATE yields no result set");
            elapsed = System.currentTimeMillis() - start;
        }
        releaseLock.join(AWAIT_TIMEOUT_MS);
        blocker.close();
        Assertions.assertTrue(elapsed >= thresholdMillis,
                "the instrumented statement must have been slowed by the lock wait, took "
                        + elapsed + " ms");

        ConcurrentHashMap<String, ?> stats = SlowQueryReport.getPoolStats(poolName);
        Assertions.assertNotNull(stats, "stats must exist once a slow query was run");
        String key = statsKeySet(stats).stream().filter(k -> k.contains(nonce))
                .findFirst().orElse(null);
        Assertions.assertNotNull(key, "slow query must be reported under pool " + poolName
                + ", recorded keys: " + statsKeySet(stats));
        // the entry must carry the slowness the interceptor measured
        SlowQueryReport.QueryStats qs = (SlowQueryReport.QueryStats) stats.get(key);
        Assertions.assertEquals(1, qs.getNrOfInvocations());
        Assertions.assertTrue(qs.getMaxInvocationTime() >= thresholdMillis,
                "the recorded execution time must exceed the threshold, was "
                        + qs.getMaxInvocationTime() + " ms");
    }

    @Test
    void failedQueryIsRecordedInPoolStats() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport");
        ds.createPool();
        String poolName = ds.getPoolProperties().getPoolName();

        String nonce = "fail_" + System.nanoTime();
        String sql = "SELECT * FROM no_such_table_" + nonce;
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            Assertions.assertThrows(SQLException.class, () -> st.executeQuery(sql));
        }

        ConcurrentHashMap<String, ?> stats = SlowQueryReport.getPoolStats(poolName);
        Assertions.assertNotNull(stats, "stats must exist once a query failed");
        Assertions.assertTrue(statsKeySet(stats).stream().anyMatch(k -> k.contains(nonce)),
                "failed query must be reported under pool " + poolName
                        + ", recorded keys: " + statsKeySet(stats));
    }

    /*
     * A failing statement method that is not an execution has no query to
     * report. Reporting it anyway made the reporting code itself fail - the
     * query extraction cast the int argument of setFetchSize to String -
     * and the ClassCastException escaped in place of the SQLException the
     * driver raised, which no application catching SQLException expects.
     */
    @Test
    void failedNonExecuteMethodPropagatesTheDriverException() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport");
        ds.createPool();

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            Assertions.assertThrows(SQLException.class, () -> st.setFetchSize(-1),
                    "the driver's SQLException must reach the application");
        }
    }

    /*
     * Same for a prepared statement, where the extraction succeeds: the
     * failure used to be booked against the statement's SQL with a start
     * time of 0, so the recorded "execution time" was the epoch.
     */
    @Test
    void failedNonExecuteMethodIsNotBookedAsAFailedQuery() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport");
        ds.createPool();
        String poolName = ds.getPoolProperties().getPoolName();

        String nonce = "nontime_" + System.nanoTime();
        String sql = "SELECT 1 /* " + nonce + " */";
        Connection c = ds.getConnection();
        PreparedStatement ps = c.prepareStatement(sql);
        // a negative fetch size is a SQLException per the JDBC contract
        Assertions.assertThrows(SQLException.class, () -> ps.setFetchSize(-1));

        // preparing the statement legitimately creates the stats entry,
        // but a method that is not an execution must not be booked in it
        ConcurrentHashMap<String, ?> stats = SlowQueryReport.getPoolStats(poolName);
        Assertions.assertNotNull(stats, "the prepare must have created the stats map");
        String key = statsKeySet(stats).stream().filter(k -> k.contains(nonce))
                .findFirst().orElse(null);
        Assertions.assertNotNull(key, "the prepared statement must have a stats entry, "
                + "recorded keys: " + statsKeySet(stats));
        SlowQueryReport.QueryStats qs = (SlowQueryReport.QueryStats) stats.get(key);
        Assertions.assertEquals(0, qs.getNrOfInvocations(),
                "a method that is not an execution must not be booked as an "
                        + "invocation of " + key);
        Assertions.assertEquals(Long.MIN_VALUE, qs.getMaxInvocationTime(),
                "no invocation time may be recorded for a method that is not "
                        + "an execution");

        // positive control: the report must still be live for this statement,
        // so the assertions above cannot pass because the proxy is absent
        ps.execute();
        Assertions.assertEquals(1, qs.getNrOfInvocations(),
                "the execution must be booked in the same entry");

        ps.close();
        c.close();
    }

    private static java.util.Set<String> statsKeySet(ConcurrentHashMap<String, ?> stats) {
        return stats.keySet();
    }

    private static SlowQueryReport.QueryStats statsFor(ConcurrentHashMap<String, ?> stats,
            String key) {
        SlowQueryReport.QueryStats qs = (SlowQueryReport.QueryStats) stats.get(key);
        Assertions.assertNotNull(qs, "no stats entry for " + key
                + ", recorded keys: " + statsKeySet(stats));
        return qs;
    }

    /*
     * The JDBC 4.2 execution methods executeLargeUpdate and executeLargeBatch
     * were not recognised as executions, so they were neither timed nor booked
     * in the query statistics - a pool under monitoring silently ignored
     * exactly the long-running batch work the statistics exist to surface.
     */
    @Test
    void jdbc42ExecutionsAreBookedInQueryStats() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setName("large_" + System.nanoTime());
        ds.getPoolProperties().setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport");
        ds.createPool();
        String poolName = ds.getPoolProperties().getPoolName();
        String t = uniqueTable();

        String largeUpdateSql = "INSERT INTO " + t + " VALUES (1)";
        String batchedSql = "INSERT INTO " + t + " VALUES (2)";
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            c.createStatement().execute("CREATE TABLE " + t + " (v INT)");
            Assertions.assertEquals(1, st.executeLargeUpdate(largeUpdateSql));

            st.addBatch(batchedSql);
            Assertions.assertArrayEquals(new long[] { 1 }, st.executeLargeBatch());
        }

        ConcurrentHashMap<String, ?> stats = SlowQueryReport.getPoolStats(poolName);
        Assertions.assertNotNull(stats, "stats must exist once the executions ran");
        Assertions.assertEquals(1,
                statsFor(stats, largeUpdateSql).getNrOfInvocations(),
                "executeLargeUpdate must be booked under its SQL");
        Assertions.assertEquals(1, statsFor(stats, "batch").getNrOfInvocations(),
                "executeLargeBatch must be booked as a batch execution");
        Assertions.assertEquals(2, adminRowCount(t), "both rows must be inserted");
    }

    /*
     * A failure of a JDBC 4.2 execution method must be booked against the
     * executed SQL as a failed query, just like the JDBC 3 executions are.
     */
    @Test
    void failedJdbc42ExecutionIsBookedInQueryStats() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setName("largefail_" + System.nanoTime());
        ds.getPoolProperties().setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport");
        ds.createPool();
        String poolName = ds.getPoolProperties().getPoolName();

        String nonce = "largefail_" + System.nanoTime();
        String sql = "INSERT INTO no_such_table_" + nonce + " VALUES (1)";
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            Assertions.assertThrows(SQLException.class, () -> st.executeLargeUpdate(sql));
        }

        ConcurrentHashMap<String, ?> stats = SlowQueryReport.getPoolStats(poolName);
        Assertions.assertNotNull(stats, "stats must exist once a query failed");
        // QueryStats.failure() books the execution through add() before
        // counting the failure, so one booked invocation proves the failure
        // went through the failed-query reporting path
        Assertions.assertEquals(1, statsFor(stats, sql).getNrOfInvocations(),
                "a failed executeLargeUpdate must be booked as an execution of " + sql);
    }

    @Test
    void queryTimeoutInterceptorCancelsAndPoolStaysUsable() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.QueryTimeoutInterceptor(queryTimeout=1)");
        ds.getPoolProperties().setMaxActive(1);

        long start = System.currentTimeMillis();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            Assertions.assertThrows(SQLException.class, () -> st.execute(HEAVY_SQL));
        }
        long elapsed = System.currentTimeMillis() - start;
        Assertions.assertTrue(elapsed >= 900 && elapsed < 8000,
                "query must be cancelled around the configured timeout, took "
                        + elapsed + " ms");

        // the pool and its physical connection must survive the cancellation
        Assertions.assertEquals(1, poolSessions());
        try (Connection c = ds.getConnection();
                ResultSet rs = c.createStatement().executeQuery("SELECT 1")) {
            Assertions.assertTrue(rs.next());
        }
    }

    @Test
    void closedStatementFailsWithoutCacheControl() throws Exception {
        DataSource ds = newDataSource(); // no interceptors
        String t = uniqueTable();
        try (Connection c = ds.getConnection()) {
            c.createStatement().execute("CREATE TABLE " + t + " (v INT)");
            PreparedStatement ps = c.prepareStatement("INSERT INTO " + t + " VALUES (1)");
            Assertions.assertEquals(1, ps.executeUpdate());
            ps.close();
            Assertions.assertThrows(SQLException.class, ps::executeUpdate,
                    "a physically closed statement must fail");
        }
    }

    @Test
    void statementCacheServesRepeatedPreparesWithinOneBorrow() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.StatementCache(prepared=true,max=10)");
        ds.getPoolProperties().setMaxActive(1);
        String t = uniqueTable();
        try (Connection c = ds.getConnection()) {
            c.createStatement().execute("CREATE TABLE " + t + " (v INT)");
            String sql = "INSERT INTO " + t + " VALUES (1)";

            PreparedStatement first = c.prepareStatement(sql);
            Assertions.assertEquals(1, first.executeUpdate());
            first.close(); // returned to the cache, not physically closed

            PreparedStatement second = c.prepareStatement(sql);
            Assertions.assertEquals(1, second.executeUpdate(),
                    "the cached statement must be usable again after close");
            second.close();
        }
        Assertions.assertEquals(2, adminRowCount(t));
    }

    @Test
    void decoratedStatementProxyEnforcesClosedContract() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.StatementDecoratorInterceptor");
        String t = uniqueTable();
        try (Connection c = ds.getConnection()) {
            c.createStatement().execute("CREATE TABLE " + t + " (v INT)");

            Statement st = c.createStatement();
            Assertions.assertFalse(st.isClosed());
            try (ResultSet rs = st.executeQuery("SELECT v FROM " + t)) {
                Assertions.assertFalse(rs.next(), "empty table");
            }
            st.close();
            Assertions.assertTrue(st.isClosed());
            Assertions.assertThrows(SQLException.class,
                    () -> st.execute("INSERT INTO " + t + " VALUES (1)"),
                    "closed decorated statement must reject execution");
        }
        Assertions.assertEquals(0, adminRowCount(t),
                "execution attempted after close must not reach the database");
    }

    /*
     * Reporting must never disturb the application: the report hooks are
     * extension points, and a subclass whose reportQuery throws used to
     * turn a successful execution into a thrown exception at the
     * application (reportFailedQuery threw from inside the catch block,
     * replacing the driver's SQLException).
     */
    @Test
    void throwingSuccessReporterDoesNotAffectExecution() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setName("throw_" + System.nanoTime());
        ds.getPoolProperties().setJdbcInterceptors(
                ThrowingReportInterceptor.class.getName() + "(threshold=60000)");
        ds.createPool();
        String poolName = ds.getPoolProperties().getPoolName();
        String t = uniqueTable();
        String sql = "CREATE TABLE " + t + " (v INT)";
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            Assertions.assertDoesNotThrow(() -> Boolean.valueOf(st.execute(sql)),
                    "a throwing reporter must not disturb the application");
        }
        // positive control: the hook ran and booked the execution before
        // throwing, so the assertion above cannot pass because the proxy
        // is absent
        ConcurrentHashMap<String, ?> stats = SlowQueryReport.getPoolStats(poolName);
        Assertions.assertEquals(1, statsFor(stats, sql).getNrOfInvocations(),
                "the reporting hook must have run before it threw");
    }

    /*
     * Same for the failure path: the driver's SQLException, not the
     * reporting failure, is what the application catches.
     */
    @Test
    void throwingFailedReporterDoesNotMaskDriverException() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setName("throwfail_" + System.nanoTime());
        ds.getPoolProperties().setJdbcInterceptors(
                ThrowingReportInterceptor.class.getName() + "(threshold=60000)");
        ds.createPool();
        String poolName = ds.getPoolProperties().getPoolName();
        String nonce = "throwfail_" + System.nanoTime();
        String sql = "SELECT * FROM no_such_table_" + nonce;
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            Assertions.assertThrows(SQLException.class, () -> st.execute(sql),
                    "the driver's SQLException must reach the application "
                            + "even if the reporter throws");
        }
        // positive control as above: the failed-query hook ran and booked
        // the failure before throwing
        ConcurrentHashMap<String, ?> stats = SlowQueryReport.getPoolStats(poolName);
        Assertions.assertEquals(1, statsFor(stats, sql).getNrOfInvocations(),
                "the failed-query reporting hook must have run before it threw");
    }

    /**
     * Interceptor that always throws from its report hooks, after having
     * performed the regular booking, simulating a subclass that fails
     * unexpectedly.
     */
    public static class ThrowingReportInterceptor extends SlowQueryReport {
        @Override
        protected String reportQuery(String query, Object[] args,
                String name, long start, long delta) {
            super.reportQuery(query, args, name, start, delta);
            throw new IllegalStateException("reporter intentionally fails");
        }

        @Override
        protected String reportFailedQuery(String query, Object[] args,
                String name, long start, Throwable t) {
            super.reportFailedQuery(query, args, name, start, t);
            throw new IllegalStateException("reporter intentionally fails");
        }
    }
}
