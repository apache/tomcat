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
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * Expected functionality of the interceptor configuration a resource
 * definition supplies: an interceptor named by its short name is found,
 * instantiated once with its properties applied, wired into the chain of
 * every borrowed connection, told about the pool starting and stopping -
 * and it cleans up after the pool is gone.
 *
 * <p>Scenarios harvested from the retired legacy
 * {@code TestInterceptorShortName} and the statistics-cleanup case of
 * {@code TestSlowQueryReport}. The built-in {@code SlowQueryReport} plays
 * the role of the configured interceptor, so nothing here needs a test
 * driver: the interceptor leaves evidence in the database work it sees and
 * in the statistics it publishes.</p>
 */
class TestInterceptorLifecycle extends JdbcPoolBaseTest {

    private static final String REPORT_SQL = "SELECT COUNT(*) FROM SYSTEM_RANGE(1,10)";

    private DataSource reportedPool() {
        DataSource ds = newDataSource();
        // deliberately the short name, as it appears in a resource definition
        ds.getPoolProperties().setJdbcInterceptors("SlowQueryReport(threshold=7,maxQueries=5)");
        return ds;
    }

    @Test
    void aShortInterceptorNameIsResolvedAndConfigured() throws Exception {
        DataSource ds = reportedPool();
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            SlowQueryReport interceptor =
                    interceptorOf(pooled, SlowQueryReport.class);
            assertNotNull(interceptor,
                    "the interceptor named by short name must sit in the chain of a "
                            + "borrowed connection");
            assertEquals(7L, interceptor.getThreshold(),
                    "the properties from the configuration string must be applied");
        }
    }

    @Test
    void theInterceptorSeesTheStatementsOfEveryBorrower() throws Exception {
        DataSource ds = reportedPool();
        ds.createPool();
        String poolName = ds.getPool().getName();

        for (int i = 0; i < 2; i++) {
            try (Connection pooled = ds.getConnection(); Statement st = pooled.createStatement();
                    ResultSet rs = st.executeQuery(REPORT_SQL)) {
                assertTrue(rs.next());
            }
        }

        Map<String, SlowQueryReport.QueryStats> stats =
                SlowQueryReport.getPoolStats(poolName);
        assertNotNull(stats, "a running pool must have its report registered");
        SlowQueryReport.QueryStats query = stats.get(REPORT_SQL);
        assertNotNull(query, "both executions must be reported, saw " + stats.keySet());
        assertEquals(2, query.getNrOfInvocations(),
                "the report must see the work of every borrower");
    }

    @Test
    void thePoolLifecycleIsReportedToTheInterceptor() throws Exception {
        DataSource ds = reportedPool();
        String poolName = ds.getPoolProperties().getPoolName();
        assertNull(SlowQueryReport.getPoolStats(poolName),
                "nothing may be published before the pool starts");

        ds.createPool();
        assertNotNull(SlowQueryReport.getPoolStats(poolName),
                "poolStarted must have reached the interceptor");

        try (Connection pooled = ds.getConnection()) {
            pooled.createStatement().execute(REPORT_SQL);
        }
        assertFalse(SlowQueryReport.getPoolStats(poolName).isEmpty(),
                "the report must hold data while the pool runs");

        ds.close();
        assertNull(SlowQueryReport.getPoolStats(poolName),
                "poolClosed must have made the interceptor give its statistics up");
    }
}
