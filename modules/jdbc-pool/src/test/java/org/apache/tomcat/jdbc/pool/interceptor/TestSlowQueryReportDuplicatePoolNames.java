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
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * The {@code SlowQueryReport} publishes a pool's statistics under the name the
 * pool started with, and reads them back by that name - but the statistics
 * themselves belong to the <em>pool</em>. Two live pools configured with the
 * same name (a configuration only programmatic users reach: the container names
 * every pool of its own, and {@code jmxEnabled=false} removes the JMX ObjectName
 * clash that would otherwise catch it) therefore keep two stores, and what the
 * name answers with is the store of the pool that started under it last.
 *
 * <p>Reproducer for defect 2 of
 * {@code defects-pool/defects-org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport.md}:
 * what must never happen is one pool's close destroying the statistics of
 * another pool, however the two were named.</p>
 */
class TestSlowQueryReportDuplicatePoolNames extends JdbcPoolBaseTest {

    private static final String QUERY_FIRST = "SELECT COUNT(*) FROM SYSTEM_RANGE(1,3)";
    private static final String QUERY_SECOND = "SELECT COUNT(*) FROM SYSTEM_RANGE(1,5)";
    private static final String QUERY_AFTER_CLOSE = "SELECT COUNT(*) FROM SYSTEM_RANGE(1,7)";

    /* A pool of the given name reporting every query it sees. */
    private DataSource reportedPoolNamed(String name) {
        DataSource ds = newDataSource(); // JMX disabled, as a same-named
        // second pool needs to dodge the JMX ObjectName clash
        ds.getPoolProperties().setName(name);
        ds.getPoolProperties().setJdbcInterceptors("SlowQueryReport(threshold=-1)");
        return ds;
    }

    private static void run(DataSource ds, String sql) throws SQLException {
        try (Connection pooled = ds.getConnection();
                Statement st = pooled.createStatement()) {
            st.execute(sql);
        }
    }

    @Test
    void twoLivePoolsOfOneNameKeepTheirStatisticsApart() throws Exception {
        String poolName = "dup-" + System.nanoTime();
        DataSource first = reportedPoolNamed(poolName);
        DataSource second = reportedPoolNamed(poolName);
        first.createPool();
        second.createPool();

        run(first, QUERY_FIRST);
        run(second, QUERY_SECOND);

        ConcurrentHashMap<String, SlowQueryReport.QueryStats> view =
                SlowQueryReport.getPoolStats(poolName);
        Assertions.assertNotNull(view,
                "the running pools must publish a store under their name");
        Assertions.assertEquals(Collections.singleton(QUERY_SECOND), view.keySet(),
                "the name answers with the pool that started under it last, and not"
                        + " with the numbers of the pool that was there before it: " + view.keySet());

        first.close();

        Assertions.assertNotNull(SlowQueryReport.getPoolStats(poolName),
                "closing one of two live pools of the same name must not throw the"
                        + " still-running pool's statistics away");

        run(second, QUERY_AFTER_CLOSE);

        ConcurrentHashMap<String, SlowQueryReport.QueryStats> surviving =
                SlowQueryReport.getPoolStats(poolName);
        Assertions.assertNotNull(surviving,
                "the surviving pool must keep collecting after the other pool closed");
        Assertions.assertTrue(surviving.containsKey(QUERY_AFTER_CLOSE),
                "a query run after the other pool closed must be collected, saw "
                        + surviving.keySet());
        Assertions.assertEquals(1, surviving.get(QUERY_SECOND).getNrOfInvocations(),
                "the statistics the surviving pool booked before the other pool closed must"
                        + " survive it");

        second.close();

        Assertions.assertNull(SlowQueryReport.getPoolStats(poolName),
                "closing the pool that holds the name must release its statistics");
    }

    @Test
    void aPoolWhoseNameWasTakenOverKeepsBookingIntoItsOwnStore() throws Exception {
        String poolName = "dup-displaced-" + System.nanoTime();
        DataSource displaced = reportedPoolNamed(poolName);
        displaced.createPool();
        try (Connection con = displaced.getConnection()) {
            SlowQueryReport reporter = interceptorOf(con, SlowQueryReport.class);
            Assertions.assertNotNull(reporter,
                    "the chain must hold the configured reporting instance");

            // A second pool starts under the name while the first is live: the
            // name now answers with the newcomer, and the pool that was there
            // first keeps running with a store no name reaches
            DataSource successor = reportedPoolNamed(poolName);
            successor.createPool();

            try (Statement st = con.createStatement()) {
                st.execute(QUERY_FIRST);
            }

            Assertions.assertTrue(reporter.queries.containsKey(QUERY_FIRST),
                    "the displaced pool's own connections must still collect what they run");
            Assertions.assertTrue(SlowQueryReport.getPoolStats(poolName).isEmpty(),
                    "while the name publishes the successor's own store: the displaced pool's"
                            + " numbers must not read as the successor's, saw "
                            + SlowQueryReport.getPoolStats(poolName).keySet());

            successor.close();
        }
        displaced.close();
    }

    @Test
    void aPoolStartedUnderAFreedNameCollectsIntoAFreshStore() throws Exception {
        String poolName = "dup-" + System.nanoTime();
        DataSource first = reportedPoolNamed(poolName);
        first.createPool();
        run(first, QUERY_FIRST);
        first.close();
        Assertions.assertNull(SlowQueryReport.getPoolStats(poolName),
                "closing the only pool of a name must release the statistics");

        DataSource second = reportedPoolNamed(poolName);
        second.createPool();
        run(second, QUERY_SECOND);

        ConcurrentHashMap<String, SlowQueryReport.QueryStats> stats =
                SlowQueryReport.getPoolStats(poolName);
        Assertions.assertNotNull(stats, "the restarted name must be monitored again");
        Assertions.assertEquals(Collections.singleton(QUERY_SECOND), stats.keySet(),
                "a pool started after the name was freed must collect into a fresh store, "
                        + "not inherit the closed pool's statistics");

        second.close();
    }
}
