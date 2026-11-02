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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * Booking of the shared per-pool-name query statistics when more than
 * one reporting definition is configured on one pool.
 * <p>
 * Every configured reporting definition wraps the statement again, so
 * one logical execution passes through every definition's hooks. The
 * statistics map is shared by all definitions of the pool's name, so it
 * must be booked once per execution - by the deepest definition that
 * stores statistics - while the per-definition channels (logging, JMX
 * notifications, pinned by {@link TestSlowQueryJmxNotifications}) keep
 * firing for every definition.</p>
 */
public class TestSlowQueryReportSharedStatistics extends JdbcPoolBaseTest {

    private static final String SQL_ONE = "SELECT 17";
    private static final String SQL_TWO = "SELECT 18";

    private static final String SQ_REPORT = SlowQueryReport.class.getName();

    /**
     * A report whose {@code reportQuery} hook re-enters the pool: it runs
     * its own query on a second borrowed connection while the enclosing
     * execution's report is being delivered. Configured with
     * {@code maxQueries=0} it opts out of the shared statistics but stays
     * a live hook layer - the re-entry scenario in which a per-execution
     * claim travelling on a thread-wide scope can starve the enclosing
     * execution; the per-chain booking decision must not let it.
     */
    public static class ReentryReport extends SlowQueryReport {
        /** The pool the hook re-enters; set by the test before the borrow. */
        static volatile DataSource target;
        /** The SQL the hook runs. */
        static volatile String innerSql = SQL_TWO;
        /** One re-entrant execution per test, on any thread. */
        static final AtomicBoolean ENTERED = new AtomicBoolean();

        @Override
        protected String reportQuery(String query, Object[] args, final String name, long start, long delta) {
            String sql = super.reportQuery(query, args, name, start, delta);
            if (target != null && ENTERED.compareAndSet(false, true)) {
                DataSource ds = target;
                String sqlToRun = innerSql;
                // The re-entry runs on the reporting thread, while the
                // enclosing execution is inside this hook. The statement of
                // the re-entered execution must be booked exactly once by
                // its own chain, whatever the enclosing execution booked.
                try (Connection con = ds.getConnection();
                        Statement st = con.createStatement()) {
                    st.execute(sqlToRun);
                } catch (SQLException x) {
                    throw new IllegalStateException(x);
                }
            }
            return sql;
        }
    }

    private DataSource twoDefinitions(String interceptors, String poolName) {
        DataSource ds = newDataSource();
        ds.setName(poolName);
        ds.setJdbcInterceptors(interceptors);
        ds.getPoolProperties().setInitialSize(0);
        return ds;
    }

    private static Map<String, SlowQueryReport.QueryStats> stats(String poolName) {
        Map<String, SlowQueryReport.QueryStats> stats = SlowQueryReport.getPoolStats(poolName);
        Assertions.assertNotNull(stats, "the running pool must publish statistics under its name");
        return stats;
    }

    /**
     * Reads a counter off the query statistics the same way the JMX
     * {@code SlowQueriesCD} attribute publishes it.
     */
    private static Object compositeValue(SlowQueryReport.QueryStats qs, String field) throws Exception {
        CompositeType type = new CompositeType("QueryStats", "query statistics",
                SlowQueryReport.QueryStats.getFieldNames(), SlowQueryReport.QueryStats.getFieldDescriptions(),
                SlowQueryReport.QueryStats.getFieldTypes());
        CompositeData data = qs.getCompositeData(type);
        return data.get(field);
    }

    @Test
    void oneExecutionIsBookedOnceWhenTwoReportDefinitionsShareAPool() throws Exception {
        String poolName = "twoDefs-" + System.nanoTime();
        DataSource ds = twoDefinitions(SQ_REPORT + ";" + SQ_REPORT, poolName);
        try (Connection con = ds.getConnection();
                PreparedStatement pst = con.prepareStatement(SQL_ONE)) {
            pst.execute();
        }

        Map<String, SlowQueryReport.QueryStats> stats = stats(poolName);
        SlowQueryReport.QueryStats qs = stats.get(SQL_ONE);
        Assertions.assertNotNull(qs, "the executed query must be booked, saw " + stats.keySet());
        Assertions.assertEquals(1, qs.getNrOfInvocations(),
                "two report definitions must book the shared statistics once per execution, not multiply them");
        Assertions.assertEquals(1, compositeValue(qs, "prepareCount"),
                "the single preparation must be booked once, not once per definition");

        ds.close();
    }

    @Test
    void oneExecutionIsBookedOnceWhenAStatementWrapperSeparatesTheTwoDefinitions() throws Exception {
        // The chain-position test that decides who books must not be
        // blinded by a statement-wrapping interceptor between the two
        // reporting definitions - the statement cache also hides the
        // second preparation from the layers above it on a cache hit
        String poolName = "twoDefsWrapped-" + System.nanoTime();
        DataSource ds = twoDefinitions(SQ_REPORT + ";org.apache.tomcat.jdbc.pool.interceptor.StatementCache;"
                + SQ_REPORT, poolName);
        try (Connection con = ds.getConnection()) {
            try (PreparedStatement pst = con.prepareStatement(SQL_ONE)) {
                pst.execute();
            }
            try (PreparedStatement pst = con.prepareStatement(SQL_ONE)) {
                pst.execute();
            }
        }

        Map<String, SlowQueryReport.QueryStats> stats = stats(poolName);
        SlowQueryReport.QueryStats qs = stats.get(SQL_ONE);
        Assertions.assertNotNull(qs, "the executed query must be booked, saw " + stats.keySet());
        Assertions.assertEquals(2, qs.getNrOfInvocations(),
                "the two executions must book two invocations through the cache, one each");
        Assertions.assertEquals(1, compositeValue(qs, "prepareCount"),
                "the cache must book its single real preparation exactly once");

        ds.close();
    }

    @Test
    void theOuterDefinitionBooksWhenTheInnermostStoresNoStatistics() throws Exception {
        // A definition with maxQueries=0 stores nothing; it must not hold
        // the shared statistics silent for the definitions above it
        String poolName = "outerBooks-" + System.nanoTime();
        DataSource ds = twoDefinitions(SQ_REPORT + "(maxQueries=100);" + SQ_REPORT + "(maxQueries=0)", poolName);
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute(SQL_ONE);
        }

        Map<String, SlowQueryReport.QueryStats> stats = stats(poolName);
        SlowQueryReport.QueryStats qs = stats.get(SQL_ONE);
        Assertions.assertNotNull(qs,
                "the outer definition must keep booking while the innermost one stores nothing");
        Assertions.assertEquals(1, qs.getNrOfInvocations(),
                "the opted-out inner definition must not suppress the outer one's booking");

        ds.close();
    }

    @Test
    void theInnerDefinitionBooksWhenTheOuterStoresNoStatistics() throws Exception {
        String poolName = "innerBooks-" + System.nanoTime();
        DataSource ds = twoDefinitions(SQ_REPORT + "(maxQueries=0);" + SQ_REPORT + "(maxQueries=100)", poolName);
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute(SQL_ONE);
        }

        Map<String, SlowQueryReport.QueryStats> stats = stats(poolName);
        SlowQueryReport.QueryStats qs = stats.get(SQL_ONE);
        Assertions.assertNotNull(qs, "the inner definition that stores statistics must book");
        Assertions.assertEquals(1, qs.getNrOfInvocations(),
                "the opted-out outer definition must add nothing to the shared booking");

        ds.close();
    }

    @Test
    void aReEnteredExecutionIsBookedByItsOwnChain() throws Exception {
        String poolName = "reentry-" + System.nanoTime();
        DataSource ds = twoDefinitions(ReentryReport.class.getName() + "(maxQueries=0);"
                + SQ_REPORT + "(maxQueries=100)", poolName);
        ReentryReport.target = ds;
        ReentryReport.ENTERED.set(false);
        try {
            try (Connection con = ds.getConnection();
                    Statement st = con.createStatement()) {
                st.execute(SQL_ONE);
            }

            Map<String, SlowQueryReport.QueryStats> stats = stats(poolName);
            SlowQueryReport.QueryStats outer = stats.get(SQL_ONE);
            Assertions.assertNotNull(outer, "the enclosing execution must be booked, saw " + stats.keySet());
            Assertions.assertEquals(1, outer.getNrOfInvocations(),
                    "the enclosing execution must be booked exactly once");
            SlowQueryReport.QueryStats inner = stats.get(SQL_TWO);
            Assertions.assertNotNull(inner,
                    "the re-entered execution must be booked by its own chain, saw " + stats.keySet());
            Assertions.assertEquals(1, inner.getNrOfInvocations(),
                    "the re-entered execution must be booked exactly once, neither starved by the "
                            + "enclosing execution nor multiplied");
        } finally {
            ReentryReport.target = null;
            ds.close();
        }
    }
}
