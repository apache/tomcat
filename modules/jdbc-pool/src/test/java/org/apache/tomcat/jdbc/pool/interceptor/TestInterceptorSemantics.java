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

    /** Heavy pure-SQL aggregate; H2 honours query timeouts on it (~2 s). */
    private static final String HEAVY_SQL_PREFIX =
            "SELECT COUNT(*) FROM SYSTEM_RANGE(1,400000000) r1, SYSTEM_RANGE(1,3)";

    @Test
    void slowQueryIsRecordedInPoolStats() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport(threshold=100)");
        ds.createPool();
        String poolName = ds.getPoolProperties().getPoolName();

        String nonce = "nonce_" + System.nanoTime();
        String sql = HEAVY_SQL_PREFIX + " /* " + nonce + " */";
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            Assertions.assertTrue(st.execute(sql));
        }

        ConcurrentHashMap<String, ?> stats = SlowQueryReport.getPoolStats(poolName);
        Assertions.assertNotNull(stats, "stats must exist once a slow query was run");
        Assertions.assertTrue(statsKeySet(stats).stream().anyMatch(k -> k.contains(nonce)),
                "slow query must be reported under pool " + poolName
                        + ", recorded keys: " + statsKeySet(stats));
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

    @Test
    void queryTimeoutInterceptorCancelsAndPoolStaysUsable() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.QueryTimeoutInterceptor(queryTimeout=1)");
        ds.getPoolProperties().setMaxActive(1);

        long start = System.currentTimeMillis();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            Assertions.assertThrows(SQLException.class, () -> st.execute(HEAVY_SQL_PREFIX));
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
}
