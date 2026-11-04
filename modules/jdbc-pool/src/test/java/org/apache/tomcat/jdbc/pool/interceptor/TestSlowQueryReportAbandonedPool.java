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

import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolProperties;

/**
 * A pool that is never closed - programmatic misuse, or a crashed container
 * that never runs its shutdown - must not be retained by the registrations its
 * reporting interceptors made. Held strongly, the abandoned pool was kept alive
 * by a JVM-lifetime static together with everything it reaches: its queues, its
 * pooled connections and their open physical sessions. The registrations hold
 * the pool weakly, so an abandoned pool drains out on its own; what stays behind
 * is what the registrations legitimately keep - the bounded statistics map
 * published under the name and, for the JMX subclass, the MBean published under
 * it - neither of which reaches the pool.
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

        // A fresh pool under the name, closed properly, frees the name: the
        // statistics of a pool that was abandoned and collected are not what a
        // reader of the name is left with once a live pool has taken the name
        // over and come down again
        DataSource survivor = newDataSource();
        survivor.getPoolProperties().setName(poolName);
        survivor.getPoolProperties().setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport(threshold=-1)");
        survivor.getPoolProperties().setInitialSize(0);
        survivor.createPool();
        use(survivor.getPool());
        survivor.close();

        Assertions.assertNull(SlowQueryReport.getPoolStats(poolName),
                "closing the pool that holds the name must release the statistics of"
                        + " the name, whatever an abandoned same-named pool left behind");
    }

    /*
     * The same abandonment against the JMX subclass, whose registration is
     * published with the MBeanServer - a second JVM-lifetime root next to the
     * static registry. The published resource must hold no pool reference:
     * held strongly - as when the registering interceptor instance itself was
     * the MBean - the abandoned pool and everything it reaches stayed
     * reachable through the MBeanServer for the life of the JVM, since a
     * registration is only ever taken down by the close an abandoned pool
     * never runs (reproducer for finding F2 of the round-2 clean-room review,
     * probes P16a/P16b/P17).
     */
    private static ConnectionPool jmxReportedPoolNamed(String name) throws SQLException {
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
        // notifyPool=false is the documented configuration that publishes the
        // interceptor's own MBean on the first borrow
        properties.setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx(notifyPool=false,threshold=-1)");
        return new ConnectionPool(properties);
    }

    @Test
    void anAbandonedPoolIsCollectedInsteadOfPinnedByItsJmxRegistration() throws Exception {
        String poolName = "abandoned-jmx-" + System.nanoTime();
        ConnectionPool pool = jmxReportedPoolNamed(poolName);
        use(pool); // the borrow registers the pool name's MBean
        // abandoned: no close() ever runs for this pool
        WeakReference<ConnectionPool> probe = new WeakReference<>(pool);
        pool = null;

        Assertions.assertFalse(stillReachable(probe),
                "the published MBean registration must not pin a pool that was"
                        + " abandoned without closing: the registration is a"
                        + " JVM-lifetime root, and the pool drags its queues, its"
                        + " pooled connections and their open physical sessions"
                        + " with it");
    }

    @Test
    void anAbandonedPoolKeepsTheNameItPublishedAndRefusesItsSuccessor() throws Exception {
        String poolName = "abandoned-jmx-heir-" + System.nanoTime();
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",name=" + poolName);
        try {
            ConnectionPool abandoned = jmxReportedPoolNamed(poolName);
            use(abandoned);
            WeakReference<ConnectionPool> probe = new WeakReference<>(abandoned);
            abandoned = null;
            Assertions.assertFalse(stillReachable(probe),
                    "precondition: the abandoned pool must be collected - the"
                            + " registration it published must not reach it");
            Assertions.assertTrue(server.isRegistered(oname),
                    "premise: the abandoned pool's MBean is still published under the name -"
                            + " nothing but that pool's own close gives a publication up, and"
                            + " an abandoned pool never runs one");

            DataSource survivor = newDataSource();
            survivor.getPoolProperties().setName(poolName);
            survivor.getPoolProperties().setJmxEnabled(false);
            survivor.getPoolProperties().setJdbcInterceptors(
                    "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx(notifyPool=false,threshold=-1)");
            survivor.createPool();
            ConnectionPool successor = survivor.getPool();
            use(successor);

            Assertions.assertTrue(server.isRegistered(oname),
                    "the borrow of the successor must not have taken the abandoned pool's"
                            + " MBean down: what one pool published is another pool's to keep");
            SlowQueryReportJmx.PoolJmx state = SlowQueryReportJmx.jmxStateFor(successor);
            Assertions.assertNotNull(state,
                    "the borrow must have built the successor's own JMX state");
            Assertions.assertNull(state.publishedUnder(),
                    "and the successor must not report through the abandoned pool's MBean:"
                            + " its own publication is the one the MBeanServer refused");
            Assertions.assertNotNull(state.failureMarker(),
                    "the refusal is recorded against the ObjectName, reported once and retried"
                            + " on the next borrow");

            survivor.close();

            Assertions.assertTrue(server.isRegistered(oname),
                    "the close of the successor releases what the successor published, which is"
                            + " nothing - it leaves the abandoned pool's publication alone");
            Assertions.assertNull(SlowQueryReportJmx.jmxStateFor(successor),
                    "the successor's own JMX state is given up with its store");
            Assertions.assertNull(SlowQueryReport.getPoolStats(poolName),
                    "and so is the statistics map it held under the name");
        } finally {
            // The abandoned pool's registration is nobody's to release but its
            // own close, which by the design under test never runs: take it down
            // here, so the name this test invented is not left occupied.
            try {
                server.unregisterMBean(oname);
            } catch (Exception x) {
                // never registered, or already gone
            }
        }
    }
}
