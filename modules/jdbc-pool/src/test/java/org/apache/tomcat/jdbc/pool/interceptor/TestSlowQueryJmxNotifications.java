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
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import javax.management.InstanceNotFoundException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;

/**
 * JMX notifications of the {@code SlowQueryReportJmx} interceptor in
 * the documented {@code notifyPool=false} mode. Interceptor chains are
 * created per pooled connection, while JMX clients attach their
 * listeners to the single MBean registered per pool: a slow query
 * occurring on any connection must reach those listeners, not only the
 * ones occurring on the connection whose interceptor instance happened
 * to win the MBean registration.
 * <p>
 * The MBean registration is owned by the per-pool-name registry, not by
 * the interceptor instance that won it: evicting the connection whose
 * instance owns the MBean must leave the MBean and its listeners live,
 * and a released registration slot must be taken over by the next
 * borrow.</p>
 * <p>
 * The same registry is shared by live pools configured with the same
 * name: closing one of those pools must not take the registered MBean -
 * and with it the broadcaster its clients' listeners are attached to -
 * away from the pool that is still running, while concurrent closes of
 * such pools must still release the name exactly once.</p>
 * <p>
 * The registry also holds the pool's claim to a registration, so a
 * publish the MBeanServer refused must not keep the claim: every later
 * borrow retries, and closing a pool that never got published must
 * release the name's broadcaster and failure-report bookkeeping along
 * with the slot.</p>
 */
class TestSlowQueryJmxNotifications extends JdbcPoolBaseTest {

    @Test
    void notifyPoolFalseDeliversNotificationsOfEveryConnection() throws Exception {
        String poolName = "JmxNotify-" + System.nanoTime();
        DataSource ds = newDataSource();
        ds.setName(poolName);
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                + "(notifyPool=false,threshold=-1)");
        ds.getPoolProperties().setMaxActive(2);
        ds.getPoolProperties().setInitialSize(0);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",name=" + poolName);
        List<Notification> received = new CopyOnWriteArrayList<>();
        NotificationListener listener = (notification, handback) -> received.add(notification);

        try (Connection first = ds.getConnection()) {
            Assertions.assertTrue(server.isRegistered(oname),
                    "the interceptor MBean must be registered once the pool is in use");
            server.addNotificationListener(oname, listener, null, null);
            try (Connection second = ds.getConnection()) {
                // two concurrent borrows with no pre-created connections
                // means two physical connections, hence two interceptor
                // instances - only one of which can own the MBean
                Assertions.assertEquals(2, ds.getNumActive());
                try (Statement st1 = first.createStatement();
                        Statement st2 = second.createStatement()) {
                    st1.execute("SELECT 1");
                    st2.execute("SELECT 2");
                }
            }
            server.removeNotificationListener(oname, listener);
        }

        List<String> messages = received.stream().map(Notification::getMessage)
                .collect(Collectors.toList());
        Assertions.assertEquals(2, messages.size(),
                "every slow query of every connection must notify, received: " + messages);
        Assertions.assertTrue(messages.stream().anyMatch(m -> m.contains("SELECT 1")),
                "the query run on the first connection must be reported, received: " + messages);
        Assertions.assertTrue(messages.stream().anyMatch(m -> m.contains("SELECT 2")),
                "the query run on the second physical connection must also reach the listener, received: "
                        + messages);
    }

    @Test
    void evictionOfMBeanOwningConnectionKeepsMBeanAndListenersLive() throws Exception {
        String poolName = "JmxEvict-" + System.nanoTime();
        DataSource ds = newDataSource();
        ds.setName(poolName);
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                + "(notifyPool=false,threshold=-1)");
        ds.getPoolProperties().setMaxActive(2);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMaxIdle(1);
        // sweeper disabled: the maxIdle cap must be enforced by the
        // return path itself, so the eviction of the owning connection is
        // synchronous with its close and needs no timing
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(0);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",name=" + poolName);
        List<Notification> received = new CopyOnWriteArrayList<>();
        NotificationListener listener = (notification, handback) -> received.add(notification);

        // the first borrow's interceptor instance wins the MBean registry
        // slot; the second concurrent borrow (hence second physical
        // connection and second interceptor instance) shares the notifier
        Connection ownerConnection = ds.getConnection();
        Connection otherConnection = ds.getConnection();
        try {
            Assertions.assertEquals(2, ds.getNumActive());
            Assertions.assertSame(SlowQueryReportJmx.mbeans.get(poolName),
                    interceptorOf(ownerConnection, SlowQueryReportJmx.class),
                    "the first connection's interceptor instance must own the MBean registration");

            server.addNotificationListener(oname, listener, null, null);
            try (Statement st1 = ownerConnection.createStatement();
                    Statement st2 = otherConnection.createStatement()) {
                st1.execute("SELECT 1");
                st2.execute("SELECT 2");
            }

            // return the non-owning connection first so it stays within
            // maxIdle; the owner then comes back over the cap and the
            // return path closes it - the reported defect's trigger: an
            // eviction of the very connection owning the MBean
            otherConnection.close();
            ownerConnection.close();
            ownerConnection = null;
            awaitSessions(1); // the owner's physical session is really gone

            Assertions.assertTrue(server.isRegistered(oname),
                    "evicting the MBean-owning connection must not unregister the pool's MBean");

            // the surviving connection is reused; a query on its (not
            // registering) interceptor instance must still notify the
            // listener that was attached before the eviction
            try (Connection survivor = ds.getConnection();
                    Statement st = survivor.createStatement()) {
                st.execute("SELECT 3");
            }

            List<String> messages = received.stream().map(Notification::getMessage)
                    .collect(Collectors.toList());
            Assertions.assertTrue(messages.stream().anyMatch(m -> m.contains("SELECT 3")),
                    "notifications raised after the owning connection was evicted must still "
                            + "reach the listener registered with the pool's MBean, received: "
                            + messages);
        } finally {
            if (ownerConnection != null) {
                ownerConnection.close();
            }
            try {
                server.removeNotificationListener(oname, listener);
            } catch (ListenerNotFoundException ignore) {
                // the listener was never added - the failure that matters
                // already surfaced in the try block
            }
        }
    }

    @Test
    void releasedRegistrationSlotIsTakenOverByTheNextBorrow() throws Exception {
        String poolName = "JmxTakeover-" + System.nanoTime();
        DataSource ds = newDataSource();
        ds.setName(poolName);
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                + "(notifyPool=false,threshold=-1)");
        ds.getPoolProperties().setInitialSize(0);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",name=" + poolName);

        try (Connection first = ds.getConnection()) {
            Assertions.assertTrue(server.isRegistered(oname),
                    "the interceptor MBean must be registered once the pool is in use");
            // release the registry slot the way the pool's own closing does
            // it, while another connection of the same pool stays live
            SlowQueryReportJmx owner = interceptorOf(first, SlowQueryReportJmx.class);
            owner.deregisterJmx();
            Assertions.assertFalse(server.isRegistered(oname),
                    "releasing the registry slot must unregister the MBean");

            // a borrow afterwards must republish the MBean: registration
            // ownership lives in the per-name registry, and must not latch
            // onto an instance flag that would make register() a no-op
            try (Connection next = ds.getConnection()) {
                Assertions.assertTrue(server.isRegistered(oname),
                        "the next borrow after the slot was released must re-register the MBean");
                Assertions.assertSame(SlowQueryReportJmx.mbeans.get(poolName),
                        interceptorOf(next, SlowQueryReportJmx.class),
                        "the borrowing connection's interceptor instance must take over the slot");
            }
        }
    }

    /* A live pool that reports every query through the JMX interceptor under the given name. */
    private DataSource reportingPoolNamed(String name) {
        DataSource ds = newDataSource(); // jmxEnabled=false removes the
        // pools' own ObjectName clash; the interceptor registers by name
        ds.getPoolProperties().setName(name);
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                + "(notifyPool=false,threshold=-1)");
        ds.getPoolProperties().setInitialSize(0);
        return ds;
    }

    @Test
    void closingOneSameNamedPoolKeepsTheSurvivorsMBeanAndListenerLive() throws Exception {
        String poolName = "JmxDuplicate-" + System.nanoTime();
        DataSource ownerPool = reportingPoolNamed(poolName);
        DataSource survivorPool = reportingPoolNamed(poolName);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",name=" + poolName);
        List<Notification> received = new CopyOnWriteArrayList<>();
        NotificationListener listener = (notification, handback) -> received.add(notification);

        // the owner pool's first borrow wins the per-name registry slot;
        // the survivor's interceptor instance shares the registered MBean
        // and its broadcaster
        Connection ownerConnection = ownerPool.getConnection();
        Connection survivorConnection = survivorPool.getConnection();
        SlowQueryReportJmx owningInstance = interceptorOf(ownerConnection, SlowQueryReportJmx.class);
        try {
            Assertions.assertSame(SlowQueryReportJmx.mbeans.get(poolName), owningInstance,
                    "the first pool's interceptor instance must own the MBean registration");
            server.addNotificationListener(oname, listener, null, null);

            try (Statement st = survivorConnection.createStatement()) {
                st.execute("SELECT 1");
            }
            Assertions.assertTrue(received.stream().anyMatch(n -> "SELECT 1".equals(n.getMessage())),
                    "while both pools are live, the sharing pool's queries must reach the "
                            + "listener attached to the owning pool's MBean, received: " + received);

            // close the pool whose instance happens to own the registration,
            // while the survivor keeps running
            ownerConnection.close();
            ownerConnection = null;
            ownerPool.close();
            awaitSessions(1); // the owner pool's physical session is really gone

            try (Statement st = survivorConnection.createStatement()) {
                st.execute("SELECT 2");
            }
            Assertions.assertTrue(received.stream().anyMatch(n -> "SELECT 2".equals(n.getMessage())),
                    "a query of the surviving pool must still reach the listener that "
                            + "subscribed before the other same-named pool closed, received: "
                            + received);
            Assertions.assertTrue(server.isRegistered(oname),
                    "closing one of two live pools of the same name must not unregister the "
                            + "MBean the surviving pool still reports through");
            Assertions.assertSame(SlowQueryReportJmx.mbeans.get(poolName), owningInstance,
                    "the registration must stay in place until the last pool of the name closes");

            survivorPool.close();
            Assertions.assertFalse(server.isRegistered(oname),
                    "closing the last live pool of the name must release the MBean");
        } finally {
            if (ownerConnection != null) {
                try {
                    ownerConnection.close();
                } catch (SQLException ignore) {
                    // pool already closed
                }
            }
            try {
                survivorConnection.close();
            } catch (SQLException ignore) {
                // pool already closed
            }
            try {
                server.removeNotificationListener(oname, listener);
            } catch (ListenerNotFoundException | InstanceNotFoundException ignore) {
                // the teardown under test may already have dropped the binding
            }
        }
    }

    @Test
    void bothInterceptorInstancesOfTheTwoThresholdsConfigurationNotifyThroughTheMBean()
            throws Exception {
        String poolName = "JmxTwoThresholds-" + System.nanoTime();
        DataSource ds = newDataSource();
        ds.setName(poolName);
        // the configuration the class javadoc recommends: the interceptor
        // twice on one pool; each connection's chain then holds two
        // instances of it, only one of which can win the registration
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                + "(notifyPool=false,threshold=-1);"
                + "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                + "(notifyPool=false,threshold=-1)");
        ds.getPoolProperties().setInitialSize(0);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",name=" + poolName);
        List<Notification> received = new CopyOnWriteArrayList<>();
        NotificationListener listener = (notification, handback) -> received.add(notification);

        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            Assertions.assertTrue(server.isRegistered(oname),
                    "the interceptor MBean must be registered once the pool is in use");
            server.addNotificationListener(oname, listener, null, null);

            // the registration must be owned by one of the two instances
            // of this very chain - the other shares its broadcaster
            SlowQueryReportJmxMBean owner = SlowQueryReportJmx.mbeans.get(poolName);
            int chainInstances = 0;
            boolean ownerIsFromThisChain = false;
            JdbcInterceptor interceptor = (JdbcInterceptor) Proxy.getInvocationHandler(con);
            while (interceptor != null) {
                if (interceptor instanceof SlowQueryReportJmx) {
                    chainInstances++;
                    ownerIsFromThisChain |= (interceptor == owner);
                }
                interceptor = interceptor.getNext();
            }
            Assertions.assertEquals(2, chainInstances,
                    "the chain must hold both configured interceptor instances");
            Assertions.assertTrue(ownerIsFromThisChain,
                    "the registration must be owned by one of the pool's own instances");

            st.execute("SELECT 1");
        }

        // both instances of the chain see the one query; both must notify
        // through the single registered MBean - none of them into a
        // broadcaster of its own
        List<String> messages = received.stream().map(Notification::getMessage)
                .collect(Collectors.toList());
        Assertions.assertEquals(2, messages.size(),
                "both interceptor instances of the two-thresholds configuration must "
                        + "report through the registered MBean, received: " + messages);
        Assertions.assertTrue(messages.stream().allMatch(m -> m.contains("SELECT 1")),
                "both notifications are for the one query, received: " + messages);

        ds.close();
        Assertions.assertFalse(server.isRegistered(oname),
                "closing the only pool of the name must release the MBean");
    }

    /*
     * A foreign MBean interface and resource. Registered under the very
     * ObjectName the interceptor computes, it makes the interceptor's
     * publish attempt fail with InstanceAlreadyExistsException even
     * though the per-name registry let it through - the least invasive
     * deterministic way to make registerMBean refuse the MBean.
     */
    public interface ObstacleMBean {
        String getHeldBy();
    }

    /* The resource behind {@link ObstacleMBean}. */
    public static class Obstacle implements ObstacleMBean {
        @Override
        public String getHeldBy() {
            return "registration-obstacle";
        }
    }

    @Test
    void failedRegistrationFreesTheSlotSoALaterBorrowCanStillPublish() throws Exception {
        String poolName = "JmxRegFail-" + System.nanoTime();
        DataSource ds = newDataSource();
        ds.setName(poolName);
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                + "(notifyPool=false,threshold=-1)");
        ds.getPoolProperties().setInitialSize(0);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",name=" + poolName);

        Obstacle obstacle = new Obstacle();
        server.registerMBean(obstacle, oname);
        try {
            try (Connection first = ds.getConnection()) {
                // The borrow tried to publish and the MBeanServer refused
                // it: the name was taken outside the per-name registry, so
                // registry and MBeanServer now disagree.
                Assertions.assertEquals("registration-obstacle",
                        server.getAttribute(oname, "HeldBy"),
                        "the interceptor's publish must have collided with the foreign MBean");
                Assertions.assertNull(SlowQueryReportJmx.mbeans.get(poolName),
                        "a registration that the MBeanServer refused must not leave the per-name "
                                + "registry claiming an MBean that was never published - the slot is "
                                + "the only registration gate, and every later borrow would skip "
                                + "re-registration behind it for the life of the pool");

                // With the obstacle gone, a later borrow must still get the
                // MBean published: the refused attempt must not have latched
                // the registration for the life of the pool.
                server.unregisterMBean(oname);
                try (Connection next = ds.getConnection()) {
                    Assertions.assertTrue(server.isRegistered(oname),
                            "the borrow after the obstacle was removed must publish the MBean");
                    // the attribute of the interceptor's MBean interface,
                    // answered by the registered resource: not the obstacle,
                    // which exposes no such attribute
                    Assertions.assertDoesNotThrow(() -> server.getAttribute(oname, "SlowQueriesCD"),
                            "the published MBean must be the interceptor's, answering its statistics query");
                    Assertions.assertSame(SlowQueryReportJmx.mbeans.get(poolName),
                            interceptorOf(next, SlowQueryReportJmx.class),
                            "the retrying borrow's interceptor instance must own the slot");
                }
            }
            ds.close();
            Assertions.assertFalse(server.isRegistered(oname),
                    "closing the pool must release the registration that the retry published");
            Assertions.assertNull(SlowQueryReportJmx.mbeans.get(poolName),
                    "closing the pool must release the per-name registry slot");
        } finally {
            // remove the obstacle if the test never got as far as removing
            // it itself; on the green path nothing is registered any more
            try {
                server.unregisterMBean(oname);
            } catch (InstanceNotFoundException ignore) {
                // nothing left to clean up under this name
            }
        }
    }

    @Test
    void persistentRegistrationRefusalKeepsRetryingAndCleansUpOnClose() throws Exception {
        String poolName = "JmxRegRefuse-" + System.nanoTime();
        DataSource refusingPool = reportingPoolNamed(poolName);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",name=" + poolName);

        Obstacle obstacle = new Obstacle();
        server.registerMBean(obstacle, oname);
        try {
            // the first refused borrow: the slot must be freed again, and
            // the failure marked as reported - the loud report of this name
            // (warn/error) has gone out, its repeats ride at debug without
            // a stack trace (not asserted: the module's test infrastructure
            // captures no log, so the throttle is pinned through its
            // bookkeeping only)
            Connection first = refusingPool.getConnection();
            Assertions.assertNull(SlowQueryReportJmx.mbeans.get(poolName),
                    "a refused registration must not latch the per-name slot");
            Assertions.assertEquals(oname, SlowQueryReportJmx.registrationFailuresLogged.get(poolName),
                    "the first refusal of the name must be marked as reported for the throttle");

            // later borrows keep retrying behind the freed slot: still
            // refused (the obstacle holds the name), still no latch, and the
            // identical marker keeps their logging throttled
            try (Connection second = refusingPool.getConnection();
                    Connection third = refusingPool.getConnection()) {
                Assertions.assertNull(SlowQueryReportJmx.mbeans.get(poolName),
                        "retried refusals must keep leaving the slot free for the next borrow");
                Assertions.assertEquals(oname, SlowQueryReportJmx.registrationFailuresLogged.get(poolName),
                        "repeated failures for the same name must not re-mark it loudly");
                Assertions.assertEquals("registration-obstacle",
                        server.getAttribute(oname, "HeldBy"),
                        "the retried publishes must still be colliding with the obstacle");
                // a slow query on the refused pool still reaches the name's
                // broadcaster through getNotifier() - state a closing pool
                // must release together with the name even though no MBean
                // registration of this pool ever succeeded
                try (Statement st = first.createStatement()) {
                    st.execute("SELECT 1");
                }
                Assertions.assertNotNull(SlowQueryReportJmx.notifiers.get(poolName),
                        "reporting a query must have created the name's broadcaster");
            }
            first.close();

            // close a pool that never got published: releasing the name must
            // free the broadcaster it reported through and the throttle
            // marker even though the registry slot was empty all along, so
            // neither outlives the name - a new failure cycle under the name
            // has to start marked loudly again
            refusingPool.close();
            Assertions.assertNull(SlowQueryReportJmx.mbeans.get(poolName),
                    "closing the pool must release the per-name registry slot");
            Assertions.assertNull(SlowQueryReportJmx.notifiers.get(poolName),
                    "closing the pool must release the name's broadcaster the refused"
                            + " registration had still made it report through");
            Assertions.assertNull(SlowQueryReportJmx.registrationFailuresLogged.get(poolName),
                    "releasing the name must release its throttle marker");

            // the name stayed retryable through every refusal: once the
            // obstacle is gone, a fresh pool under the name publishes at once
            // and publishes cleanly - success marks nothing in the throttle
            server.unregisterMBean(oname);
            DataSource revivedPool = reportingPoolNamed(poolName);
            try (Connection borrowed = revivedPool.getConnection()) {
                Assertions.assertTrue(server.isRegistered(oname),
                        "the first borrow of a pool started after the obstacle is gone"
                                + " must publish the MBean");
                Assertions.assertNull(SlowQueryReportJmx.registrationFailuresLogged.get(poolName),
                        "a succeeding registration must not leave a throttle marker behind");
            }
            revivedPool.close();
            Assertions.assertFalse(server.isRegistered(oname),
                    "closing the revived pool must release the registration again");
        } finally {
            try {
                server.unregisterMBean(oname);
            } catch (InstanceNotFoundException ignore) {
                // nothing registered left to clean up under this name
            }
        }
    }

    @Test
    void concurrentClosesOfSameNamedPoolsReleaseTheNameExactlyOnce() throws Exception {
        String poolName = "JmxRace-" + System.nanoTime();
        DataSource first = reportingPoolNamed(poolName);
        DataSource second = reportingPoolNamed(poolName);
        DataSource third = reportingPoolNamed(poolName);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",name=" + poolName);

        Connection one = first.getConnection();
        Connection two = second.getConnection();
        Connection three = third.getConnection();
        Assertions.assertTrue(server.isRegistered(oname),
                "the MBean must be registered while the same-named pools run");
        // return every connection first, so the concurrent closes race only
        // on the per-name bookkeeping, not on pooled connections
        one.close();
        two.close();
        three.close();

        // a start gate releases all three closes together: each closing
        // thread may well see one of the others still live when it checks,
        // so the release of the name must be decided atomically, not by
        // a check that every one of them can lose against the others
        CountDownLatch startGate = new CountDownLatch(1);
        final DataSource[] pools = {first, second, third};
        Throwable[] failures = new Throwable[pools.length];
        Thread[] closers = new Thread[pools.length];
        for (int i = 0; i < pools.length; i++) {
            final DataSource pool = pools[i];
            final int slot = i;
            closers[i] = new Thread(() -> {
                try {
                    startGate.await();
                    pool.close();
                } catch (Throwable x) {
                    failures[slot] = x;
                }
            }, "same-named-pool-closer-" + i);
            closers[i].start();
        }
        startGate.countDown();
        for (Thread closer : closers) {
            closer.join(AWAIT_TIMEOUT_MS);
            Assertions.assertFalse(closer.isAlive(),
                    "no close may be left waiting on the name - the release "
                            + "must be race-free, not deadlock- or livelock-prone");
        }
        for (Throwable failure : failures) {
            Assertions.assertNull(failure,
                    "concurrent close must not fail: " + failure);
        }

        awaitSessions(0); // every physical session of every pool is really gone
        Assertions.assertFalse(server.isRegistered(oname),
                "the last of the concurrent closes must release the MBean, even where "
                        + "every closing pool saw another live pool when it checked");
        Assertions.assertNull(SlowQueryReportJmx.mbeans.get(poolName),
                "the per-name MBean registry entry must be released by one of the closes");
        Assertions.assertNull(SlowQueryReportJmx.notifiers.get(poolName),
                "the per-name broadcaster entry must be released by one of the closes");
        Assertions.assertNull(SlowQueryReport.getPoolStats(poolName),
                "the statistics must be released with the last live pool of the name");

        // exactly-once release means the name's slots were really freed, not
        // merely flagged: a pool restarted under the freed name must be able
        // to re-register, and must release the name again when it closes
        DataSource restarted = reportingPoolNamed(poolName);
        try (Connection borrowed = restarted.getConnection()) {
            Assertions.assertTrue(server.isRegistered(oname),
                    "a pool started under the concurrently freed name must re-register the MBean");
        }
        restarted.close();
        Assertions.assertFalse(server.isRegistered(oname),
                "closing the restarted pool must release the registration again");
    }
}
