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
package org.apache.tomcat.jdbc.pool.jmx;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.management.MBeanNotificationInfo;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationFilterSupport;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolExhaustedException;

/**
 * The notification types the pool MBean sends are part of the static
 * management contract, not only of the implementation.
 * <p>
 * A JMX client writes its listener and its
 * {@link NotificationFilterSupport} against the client-facing type,
 * {@link ConnectionPoolMBean}, so every type the MBean can send has to be
 * reachable through that interface: without a declaration there the client
 * must hardcode the wire string or compile against the implementation class.
 * The first test pins the compile-time contract - all eight types referenced
 * as {@code ConnectionPoolMBean.X}, with the exact wire strings they carry on
 * the JMX wire, and the same values as seen through the types a client may
 * hold: the implementation class and {@link DataSource}, which implements the
 * same interface. The second test pins the contract end to end the way the
 * defect describes a client using it: a listener registered with the MBean
 * server behind a filter built from the interface constant, receiving the
 * pool-empty notification a real exhausted pool raises. The third test pins
 * that the runtime discovery metadata name the very same constants.</p>
 * <p>
 * Delivery is asynchronous (on the pool's notification thread), so the tests
 * wait with the bounded
 * {@link JdbcPoolBaseTest#awaitUntil(java.util.function.BooleanSupplier)}
 * before asserting.</p>
 */
class TestJmxNotificationTypeContract extends JdbcPoolBaseTest {

    private static final String POOL_DOMAIN = "tomcat.jdbc";

    /*
     * The wire strings the pool's notifications carry. They are the published
     * JMX notification types: a client that filters on them outside this JVM
     * breaks if they change, so they are pinned as literals here, not derived
     * from the constants. The order matches the constants' declaration order
     * in ConnectionPoolMBean.
     */
    private static final String[] WIRE_TYPES = {
            "INIT FAILED",
            "CONNECTION FAILED",
            "CONNECTION ABANDONED",
            "SLOW QUERY",
            "FAILED QUERY",
            "SUSPECT CONNECTION ABANDONED",
            "POOL EMPTY",
            "SUSPECT CONNECTION RETURNED" };

    /* The names under which the types must be declared on the interface. */
    private static final String[] CONSTANT_NAMES = {
            "NOTIFY_INIT",
            "NOTIFY_CONNECT",
            "NOTIFY_ABANDON",
            "SLOW_QUERY_NOTIFICATION",
            "FAILED_QUERY_NOTIFICATION",
            "SUSPECT_ABANDONED_NOTIFICATION",
            "POOL_EMPTY",
            "SUSPECT_RETURNED_NOTIFICATION" };

    /*
     * Every type reached through the client-facing interface. Referencing
     * them as ConnectionPoolMBean.X is the contract pin itself: this class
     * stops compiling the moment a type leaves the interface, which is the
     * state the pool shipped with (no type declared, all eight sent).
     */
    private static String[] interfaceTypes() {
        return new String[] {
                ConnectionPoolMBean.NOTIFY_INIT,
                ConnectionPoolMBean.NOTIFY_CONNECT,
                ConnectionPoolMBean.NOTIFY_ABANDON,
                ConnectionPoolMBean.SLOW_QUERY_NOTIFICATION,
                ConnectionPoolMBean.FAILED_QUERY_NOTIFICATION,
                ConnectionPoolMBean.SUSPECT_ABANDONED_NOTIFICATION,
                ConnectionPoolMBean.POOL_EMPTY,
                ConnectionPoolMBean.SUSPECT_RETURNED_NOTIFICATION };
    }

    /*
     * Publishes an already created pool the way a container does and returns
     * the single pool MBean that appeared.
     */
    private ObjectName registerPoolMBean(MBeanServer server, DataSource ds, String role)
            throws Exception {
        Set<ObjectName> before = server.queryNames(new ObjectName(POOL_DOMAIN + ":*"), null);
        ds.preRegister(server, new ObjectName("tomcat:type=FunctionalTestDataSource,role=" + role
                + ",uid=" + System.nanoTime()));
        Set<ObjectName> added =
                new HashSet<>(server.queryNames(new ObjectName(POOL_DOMAIN + ":*"), null));
        added.removeAll(before);
        return added.isEmpty() ? null : added.iterator().next();
    }

    @Test
    void everyNotificationTypeIsDeclaredByTheClientFacingInterface() throws Exception {
        String[] iface = interfaceTypes();
        Assertions.assertEquals(WIRE_TYPES.length, iface.length,
                "the interface must declare every notification type the MBean sends");

        // The interface values are the published wire strings; existing
        // listeners match on them, so they must not drift.
        Assertions.assertArrayEquals(WIRE_TYPES, iface,
                "every notification type declared on the interface must carry the exact "
                        + "wire string the MBean sends it with");

        // The values a client sees through the types it may hold: the MBean
        // implementation class (which the pool's own notify call sites use)
        // and the DataSource, which implements the same management interface.
        String[] implVisible = {
                ConnectionPool.NOTIFY_INIT,
                ConnectionPool.NOTIFY_CONNECT,
                ConnectionPool.NOTIFY_ABANDON,
                ConnectionPool.SLOW_QUERY_NOTIFICATION,
                ConnectionPool.FAILED_QUERY_NOTIFICATION,
                ConnectionPool.SUSPECT_ABANDONED_NOTIFICATION,
                ConnectionPool.POOL_EMPTY,
                ConnectionPool.SUSPECT_RETURNED_NOTIFICATION };
        Assertions.assertArrayEquals(iface, implVisible,
                "the implementation must send with the values the interface declares");
        String[] dataSourceVisible = {
                DataSource.NOTIFY_INIT,
                DataSource.NOTIFY_CONNECT,
                DataSource.NOTIFY_ABANDON,
                DataSource.SLOW_QUERY_NOTIFICATION,
                DataSource.FAILED_QUERY_NOTIFICATION,
                DataSource.SUSPECT_ABANDONED_NOTIFICATION,
                DataSource.POOL_EMPTY,
                DataSource.SUSPECT_RETURNED_NOTIFICATION };
        Assertions.assertArrayEquals(iface, dataSourceVisible,
                "the DataSource, which implements the same interface, must expose the "
                        + "same notification types");

        // Declared on the interface itself, as public constants, not merely
        // reachable through some supertype a client may not compile against.
        for (int i = 0; i < CONSTANT_NAMES.length; i++) {
            Field field = ConnectionPoolMBean.class.getField(CONSTANT_NAMES[i]);
            Assertions.assertEquals(ConnectionPoolMBean.class, field.getDeclaringClass(),
                    CONSTANT_NAMES[i] + " must be declared on the client-facing interface");
            Assertions.assertTrue(Modifier.isPublic(field.getModifiers())
                            && Modifier.isStatic(field.getModifiers())
                            && Modifier.isFinal(field.getModifiers()),
                    CONSTANT_NAMES[i] + " must be a public static final String");
            Assertions.assertEquals(WIRE_TYPES[i], field.get(null),
                    CONSTANT_NAMES[i] + " must hold the published wire string");
        }
    }

    @Test
    void aClientFilterBuiltFromTheInterfaceConstantReceivesWhatThePoolSends()
            throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJmxEnabled(true);
        ds.getPoolProperties().setName("JmxTypeContract-" + System.nanoTime());
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setInitialSize(1);
        // a small positive wait, so an exhausted pool reports the empty pool
        // once its borrower gives up - note that maxWait<=0 means to wait
        // forever, not to wait not at all, so 0 here would never time out
        ds.getPoolProperties().setMaxWait(250);
        ds.createPool();

        ObjectName poolOname = registerPoolMBean(server, ds, "notification-type-contract");
        ConnectionPool jmxPool = ds.getPool().getJmxPool();
        Assertions.assertNotNull(jmxPool, "the fixture must have created the pool MBean");

        // The exact client pattern the defect describes: a listener whose
        // filter is built from the interface constant, subscribed through
        // the MBean server.
        NotificationFilterSupport filter = new NotificationFilterSupport();
        filter.enableType(ConnectionPoolMBean.POOL_EMPTY);
        List<Notification> filtered = new CopyOnWriteArrayList<>();
        List<Notification> unfiltered = new CopyOnWriteArrayList<>();
        NotificationListener filteredListener =
                (notification, handback) -> filtered.add(notification);
        NotificationListener unfilteredListener =
                (notification, handback) -> unfiltered.add(notification);
        try {
            Assertions.assertNotNull(poolOname, "an enabled pool must publish its MBean");
            server.addNotificationListener(poolOname, filteredListener, filter, null);
            server.addNotificationListener(poolOname, unfilteredListener, null, null);

            // Raise POOL EMPTY the way the pool raises it: a failed borrow
            // on an exhausted pool, not a test hand-fed notify().
            try (Connection held = ds.getConnection()) {
                Assertions.assertThrows(PoolExhaustedException.class, ds::getConnection,
                        "a borrow that times out on an exhausted pool must fail");
            }
            // A second type that the client's filter must keep away from it.
            Assertions.assertTrue(
                    jmxPool.notify(ConnectionPoolMBean.NOTIFY_ABANDON, "must not reach the filter"),
                    "the second notification must be reported as sent");

            // Once the unfiltered listener has seen both, both have been
            // dispatched through the MBean server (delivery keeps order), so
            // what the filtered listener holds now is its final state.
            awaitUntil(() -> unfiltered.size() >= 2);
            awaitUntil(() -> !filtered.isEmpty());

            Assertions.assertEquals(1, filtered.size(),
                    "the client that filters on " + ConnectionPoolMBean.POOL_EMPTY
                            + " must receive that type only, got " + filtered.size()
                            + " notifications");
            Notification received = filtered.get(0);
            Assertions.assertEquals(ConnectionPoolMBean.POOL_EMPTY, received.getType(),
                    "the failed borrow must have reached the client through a filter built"
                            + " from the interface constant");
            Assertions.assertEquals("POOL EMPTY", received.getType(),
                    "the type that reaches a client filtering on the interface constant"
                            + " must be the published wire string");
            Assertions.assertEquals(poolOname, received.getSource(),
                    "the notification must name the pool the client subscribed to");
            Assertions.assertEquals(2, unfiltered.size(),
                    "a client that subscribes without a filter must receive both types");
        } finally {
            server.removeNotificationListener(poolOname, filteredListener);
            server.removeNotificationListener(poolOname, unfilteredListener);
            ds.postDeregister();
        }
    }

    @Test
    void theAdvertisedNotificationMetadataNameTheInterfaceConstants() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJmxEnabled(true);
        ds.getPoolProperties().setName("JmxTypeContractMetadata-" + System.nanoTime());
        ds.createPool();
        ConnectionPool jmxPool = ds.getPool().getJmxPool();
        Assertions.assertNotNull(jmxPool, "the fixture must have created the pool MBean");

        // What a JMX reader discovers through the MBeanInfo of the MBean.
        Set<String> advertised = new LinkedHashSet<>();
        int entries = 0;
        for (MBeanNotificationInfo info : jmxPool.getNotificationInfo()) {
            for (String type : info.getNotifTypes()) {
                entries++;
                Assertions.assertTrue(advertised.add(type),
                        "the type [" + type + "] must be advertised only once");
            }
        }
        Assertions.assertEquals(new HashSet<>(Arrays.asList(interfaceTypes())),
                advertised,
                "the runtime metadata must advertise exactly the types the interface"
                        + " declares");
        Assertions.assertEquals(WIRE_TYPES.length, entries,
                "every notification type must appear in the discovered metadata");
    }
}
