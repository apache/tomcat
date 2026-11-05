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
 * pool started with, and reads them back by that name. The name identifies the
 * pool - a container names every pool it manages uniquely, and the default name
 * of a programmatically built pool is unique as well - so each pool has one
 * store and one close releases only that store.
 */
class TestSlowQueryReportPoolNames extends JdbcPoolBaseTest {

    private static final String QUERY_FIRST = "SELECT COUNT(*) FROM SYSTEM_RANGE(1,3)";
    private static final String QUERY_SECOND = "SELECT COUNT(*) FROM SYSTEM_RANGE(1,5)";
    private static final String QUERY_AFTER_CLOSE = "SELECT COUNT(*) FROM SYSTEM_RANGE(1,7)";

    /* A pool of the given name reporting every query it sees. */
    private DataSource reportedPoolNamed(String name) {
        DataSource ds = newDataSource();
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
    void twoLivePoolsKeepTheirStatisticsApart() throws Exception {
        String firstName = "pool-first-" + System.nanoTime();
        String secondName = "pool-second-" + System.nanoTime();
        DataSource first = reportedPoolNamed(firstName);
        DataSource second = reportedPoolNamed(secondName);
        first.createPool();
        second.createPool();

        run(first, QUERY_FIRST);
        run(second, QUERY_SECOND);

        ConcurrentHashMap<String, SlowQueryReport.QueryStats> firstView =
                SlowQueryReport.getPoolStats(firstName);
        Assertions.assertNotNull(firstView, "the running pool must publish a store under its name");
        Assertions.assertEquals(Collections.singleton(QUERY_FIRST), firstView.keySet(),
                "a pool's name must answer with that pool's own statistics: " + firstView.keySet());

        ConcurrentHashMap<String, SlowQueryReport.QueryStats> secondView =
                SlowQueryReport.getPoolStats(secondName);
        Assertions.assertNotNull(secondView, "the running pool must publish a store under its name");
        Assertions.assertEquals(Collections.singleton(QUERY_SECOND), secondView.keySet(),
                "a pool's name must answer with that pool's own statistics: " + secondView.keySet());

        first.close();

        Assertions.assertNull(SlowQueryReport.getPoolStats(firstName),
                "closing one pool must release its own statistics");
        Assertions.assertNotNull(SlowQueryReport.getPoolStats(secondName),
                "closing one pool must not throw another pool's statistics away");

        run(second, QUERY_AFTER_CLOSE);

        ConcurrentHashMap<String, SlowQueryReport.QueryStats> surviving =
                SlowQueryReport.getPoolStats(secondName);
        Assertions.assertNotNull(surviving,
                "the surviving pool must keep collecting after the other pool closed");
        Assertions.assertTrue(surviving.containsKey(QUERY_AFTER_CLOSE),
                "a query run after the other pool closed must be collected, saw "
                        + surviving.keySet());
        Assertions.assertEquals(1, surviving.get(QUERY_SECOND).getNrOfInvocations(),
                "the statistics the surviving pool booked before the other pool closed must"
                        + " survive it");

        second.close();

        Assertions.assertNull(SlowQueryReport.getPoolStats(secondName),
                "closing the pool that holds the name must release its statistics");
    }

    @Test
    void aPoolStartedUnderAFreedNameCollectsIntoAFreshStore() throws Exception {
        String poolName = "reuse-" + System.nanoTime();
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
