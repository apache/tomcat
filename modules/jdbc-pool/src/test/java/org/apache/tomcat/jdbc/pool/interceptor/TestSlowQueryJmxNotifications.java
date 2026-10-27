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
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

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
}
