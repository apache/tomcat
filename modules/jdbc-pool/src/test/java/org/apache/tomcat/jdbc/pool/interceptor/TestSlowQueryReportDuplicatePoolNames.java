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
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * The {@code SlowQueryReport} publishes its statistics under the pool
 * <em>name</em>, so pools configured with the same name (possible with
 * {@code jmxEnabled=false}, which removes the JMX ObjectName clash that
 * would otherwise catch it) share one statistics map. Closing one of
 * such pools must not destroy the statistics of the other one: the
 * statistics belong to the name only as long as a live pool uses it and
 * are released with the last one.
 *
 * <p>Reproducer for defect 2 of
 * {@code defects-pool/defects-org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport.md}.</p>
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
    void closingOneOfTwoPoolsOfTheSameNameKeepsTheOtherStatistics() throws Exception {
        String poolName = "dup-" + System.nanoTime();
        DataSource first = reportedPoolNamed(poolName);
        DataSource second = reportedPoolNamed(poolName);
        first.createPool();
        second.createPool();

        run(first, QUERY_FIRST);
        run(second, QUERY_SECOND);

        Map<String, SlowQueryReport.QueryStats> shared =
                SlowQueryReport.getPoolStats(poolName);
        Assertions.assertNotNull(shared,
                "the running pools must publish statistics under their name");
        // duplicate names share one map: the merging is the documented,
        // pre-existing behaviour - what must never happen is the map
        // disappearing while a pool of that name still runs
        Assertions.assertTrue(shared.containsKey(QUERY_FIRST) && shared.containsKey(QUERY_SECOND),
                "both pools must book into the shared map, saw " + shared.keySet());

        first.close();

        Assertions.assertNotNull(SlowQueryReport.getPoolStats(poolName),
                "closing one of two live pools of the same name must not throw the "
                        + "still-running pool's statistics away");

        run(second, QUERY_AFTER_CLOSE);

        Map<String, SlowQueryReport.QueryStats> surviving =
                SlowQueryReport.getPoolStats(poolName);
        Assertions.assertNotNull(surviving,
                "the surviving pool must keep collecting after the other pool closed");
        Assertions.assertTrue(surviving.containsKey(QUERY_AFTER_CLOSE),
                "a query run after the other pool closed must be collected, saw "
                        + surviving.keySet());
        Assertions.assertEquals(1, surviving.get(QUERY_FIRST).getNrOfInvocations(),
                "the statistics booked before the other pool closed must survive");

        second.close();

        Assertions.assertNull(SlowQueryReport.getPoolStats(poolName),
                "closing the last live pool of a name must release its statistics");
    }

    @Test
    void aPoolStartedUnderAFreedNameCollectsIntoAFreshMap() throws Exception {
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

        Map<String, SlowQueryReport.QueryStats> stats =
                SlowQueryReport.getPoolStats(poolName);
        Assertions.assertNotNull(stats, "the restarted name must be monitored again");
        Assertions.assertEquals(Collections.singleton(QUERY_SECOND), stats.keySet(),
                "a pool started after the name was freed must collect into a fresh map, "
                        + "not inherit the closed pool's statistics");

        second.close();
    }
}
