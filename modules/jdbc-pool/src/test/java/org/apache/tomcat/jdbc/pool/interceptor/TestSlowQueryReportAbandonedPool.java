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

import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolProperties;

/**
 * The per-name statistics registration lists the live pools of the name to
 * decide when the statistics - and with them any JMX state - are released,
 * but the list must not pin the pools it lists. Held strongly, a pool the
 * application abandoned without closing (programmatic misuse, or a crashed
 * container that never runs the shutdown) was retained by a JVM-lifetime
 * static together with everything it reaches: its queues, its pooled
 * connections and their open physical sessions. Before the live-pool
 * bookkeeping, the same misuse leaked only the bounded statistics map
 * under the name, and the pool stayed collectable. The registrations are
 * held weakly again, so an abandoned pool drains out on its own and can
 * never hold the name registered past its own collection.
 *
 * <p>Reproducer for the live-pool retention defect of
 * {@code defects-reports/defects-org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport.md}.</p>
 */
class TestSlowQueryReportAbandonedPool extends JdbcPoolBaseTest {

    private static ConnectionPool reportedPoolNamed(String name) throws SQLException {
        PoolProperties properties = new PoolProperties();
        properties.setUrl(getUrl());
        properties.setDriverClassName("org.h2.Driver");
        properties.setUsername("sa");
        properties.setPassword("");
        // JMX off: the pool's own MBean registered with the platform server
        // would be a second strong root, unrelated to the registration under test
        properties.setJmxEnabled(false);
        properties.setName(name);
        properties.setInitialSize(0);
        properties.setMinIdle(0);
        properties.setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport(threshold=-1)");
        return new ConnectionPool(properties);
    }

    /* Runs one query through a borrow and returns it; the pool is then
     * the only thing left holding the session graph. */
    private static void use(ConnectionPool pool) throws SQLException {
        try (Connection pooled = pool.getConnection();
                Statement st = pooled.createStatement()) {
            st.execute("SELECT 1");
        }
    }

    /*
     * Polls until the reference clears. A registration that holds strongly
     * never clears, whatever the GC does, so a timeout here is the failure
     * signal; a weakly held registration clears at the first full collection.
     */
    private static boolean stillReachable(WeakReference<ConnectionPool> ref) throws InterruptedException {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
        while (ref.get() != null && System.currentTimeMillis() < deadline) {
            System.gc();
            Thread.sleep(20);
        }
        return ref.get() != null;
    }

    @Test
    void anAbandonedPoolIsCollectedInsteadOfPinnedByItsRegistration() throws Exception {
        String poolName = "abandoned-" + System.nanoTime();
        ConnectionPool pool = reportedPoolNamed(poolName);
        use(pool);
        // abandoned: no close() ever runs for this pool
        WeakReference<ConnectionPool> registrationProbe = new WeakReference<>(pool);
        pool = null;

        Assertions.assertFalse(stillReachable(registrationProbe),
                "the static statistics registration must not pin a pool that was"
                        + " abandoned without closing: the pool drags its queues, its"
                        + " pooled connections and their open physical sessions with it");

        // what the registration legitimately keeps is the bounded statistics
        Assertions.assertNotNull(SlowQueryReport.getPoolStats(poolName),
                "the statistics of the name stay published - they are bounded and the"
                        + " pool's own data, not the pool itself");
    }

    @Test
    void anAbandonedPoolCannotHoldItsNameRegisteredPastItsOwnCollection() throws Exception {
        String poolName = "abandoned-heir-" + System.nanoTime();
        ConnectionPool abandoned = reportedPoolNamed(poolName);
        use(abandoned);
        WeakReference<ConnectionPool> probe = new WeakReference<>(abandoned);
        abandoned = null;
        Assertions.assertFalse(stillReachable(probe), "precondition: the abandoned pool is collected");

        // A fresh pool under the name, closed properly, releases the name:
        // a collected pool cannot sit in the live set and keep the statistics
        // registered forever
        DataSource survivor = newDataSource();
        survivor.getPoolProperties().setName(poolName);
        survivor.getPoolProperties().setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport(threshold=-1)");
        survivor.getPoolProperties().setInitialSize(0);
        survivor.createPool();
        use(survivor.getPool());
        survivor.close();

        Assertions.assertNull(SlowQueryReport.getPoolStats(poolName),
                "closing the last pool of the name must release the statistics even"
                        + " though an abandoned same-named pool preceded it");
    }
}
