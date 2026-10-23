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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
            assertTrue(st.execute(sql));
        }

        ConcurrentHashMap<String, ?> stats = SlowQueryReport.getPoolStats(poolName);
        assertNotNull(stats, "stats must exist once a slow query was run");
        assertTrue(statsKeySet(stats).stream().anyMatch(k -> k.contains(nonce)),
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
            assertThrows(SQLException.class, () -> st.executeQuery(sql));
        }

        ConcurrentHashMap<String, ?> stats = SlowQueryReport.getPoolStats(poolName);
        assertNotNull(stats, "stats must exist once a query failed");
        assertTrue(statsKeySet(stats).stream().anyMatch(k -> k.contains(nonce)),
                "failed query must be reported under pool " + poolName
                        + ", recorded keys: " + statsKeySet(stats));
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
            assertThrows(SQLException.class, () -> st.execute(HEAVY_SQL_PREFIX));
        }
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed >= 900 && elapsed < 8000,
                "query must be cancelled around the configured timeout, took "
                        + elapsed + " ms");

        // the pool and its physical connection must survive the cancellation
        assertEquals(1, poolSessions());
        try (Connection c = ds.getConnection();
                ResultSet rs = c.createStatement().executeQuery("SELECT 1")) {
            assertTrue(rs.next());
        }
    }

    @Test
    void closedStatementFailsWithoutCacheControl() throws Exception {
        DataSource ds = newDataSource(); // no interceptors
        String t = uniqueTable();
        try (Connection c = ds.getConnection()) {
            c.createStatement().execute("CREATE TABLE " + t + " (v INT)");
            PreparedStatement ps = c.prepareStatement("INSERT INTO " + t + " VALUES (1)");
            assertEquals(1, ps.executeUpdate());
            ps.close();
            assertThrows(SQLException.class, ps::executeUpdate,
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
            assertEquals(1, first.executeUpdate());
            first.close(); // returned to the cache, not physically closed

            PreparedStatement second = c.prepareStatement(sql);
            assertEquals(1, second.executeUpdate(),
                    "the cached statement must be usable again after close");
            second.close();
        }
        assertEquals(2, adminRowCount(t));
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
            assertFalse(st.isClosed());
            try (ResultSet rs = st.executeQuery("SELECT v FROM " + t)) {
                assertFalse(rs.next(), "empty table");
            }
            st.close();
            assertTrue(st.isClosed());
            assertThrows(SQLException.class,
                    () -> st.execute("INSERT INTO " + t + " VALUES (1)"),
                    "closed decorated statement must reject execution");
        }
        assertEquals(0, adminRowCount(t),
                "execution attempted after close must not reach the database");
    }
}
