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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.management.ManagementFactory;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * The source the pool's MBean sends its notifications with.
 * <p>
 * {@link Notification} marshals its source, so a client that receives a pool
 * notification through Java serialization - a client behind a JMX connector,
 * or an in-JVM listener that stores the notification or forwards it to another
 * process - only receives it at all if the source is serializable. The pool
 * MBean is not, and neither is the pool it wraps, so the notification has to
 * name the pool with something that is: the ObjectName the MBean is registered
 * under, or, for a pool MBean that was never registered, a string of the shape
 * that name would have had.</p>
 * <p>
 * The notifications are captured from the MBean's own listener queue, which
 * shows what the pool hands out as it stands. A listener attached through the
 * MBean server cannot observe that: where the notification names the registered
 * MBean as its own source, the MBean server replaces the source with the name
 * the MBean is registered under before it reaches its own listeners. That
 * rewriting is also what a client behind a connector effectively sees, which is
 * why the last test pins that the name the remote side reads does not change
 * with this fix.</p>
 * <p>
 * Delivery is asynchronous (on the pool's notification thread), so the tests
 * wait for the expected notifications to arrive with the bounded
 * {@link JdbcPoolBaseTest#awaitUntil(java.util.function.BooleanSupplier)}
 * before asserting; once they have all arrived, every pin below is asserted
 * exactly as if delivery had been immediate.</p>
 */
class TestJmxNotificationSource extends JdbcPoolBaseTest {

    private static final String POOL_DOMAIN = "tomcat.jdbc";

    /* Every notification type the pool MBean advertises. */
    private static final String[] ALL_TYPES = {
            ConnectionPool.NOTIFY_INIT,
            ConnectionPool.NOTIFY_CONNECT,
            ConnectionPool.NOTIFY_ABANDON,
            ConnectionPool.SLOW_QUERY_NOTIFICATION,
            ConnectionPool.FAILED_QUERY_NOTIFICATION,
            ConnectionPool.SUSPECT_ABANDONED_NOTIFICATION,
            ConnectionPool.POOL_EMPTY,
            ConnectionPool.SUSPECT_RETURNED_NOTIFICATION };

    /*
     * The delivery a serialized client sees: javax.management.Notification
     * writes its source into the stream, so this fails over a source that is
     * not serializable.
     */
    private static Notification roundTrip(Notification notification) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(notification);
        }
        try (ObjectInputStream in =
                new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (Notification) in.readObject();
        }
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
    void everyNotificationOfARegisteredPoolNamesItsObjectNameAndSerializes() throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJmxEnabled(true);
        ds.getPoolProperties().setName("JmxSource-" + System.nanoTime());
        ds.createPool();

        ObjectName poolOname = registerPoolMBean(server, ds, "notification-source");
        ConnectionPool jmxPool = ds.getPool().getJmxPool();
        List<Notification> received = new CopyOnWriteArrayList<>();
        NotificationListener listener = (notification, handback) -> received.add(notification);
        try {
            Assertions.assertNotNull(poolOname, "an enabled pool must publish its MBean");
            Assertions.assertEquals(poolOname, jmxPool.getObjectName(),
                    "the MBean must know the name it was registered under");
            jmxPool.addListener(listener);

            for (String type : ALL_TYPES) {
                Assertions.assertTrue(jmxPool.notify(type, "message for " + type),
                        "the notification of type [" + type + "] must be reported as sent");
            }

            awaitUntil(() -> received.size() >= ALL_TYPES.length);
            Assertions.assertEquals(ALL_TYPES.length, received.size(),
                    "every type must have reached the listener");
            long previousSequence = -1;
            for (int i = 0; i < ALL_TYPES.length; i++) {
                String type = ALL_TYPES[i];
                Notification notification = received.get(i);
                Assertions.assertEquals(type, notification.getType());
                Assertions.assertEquals("[" + type + "] message for " + type,
                        notification.getMessage());
                Assertions.assertTrue(notification.getSequenceNumber() > previousSequence,
                        "sequence numbers must keep increasing, got "
                                + notification.getSequenceNumber() + " after " + previousSequence);
                previousSequence = notification.getSequenceNumber();

                Assertions.assertEquals(poolOname, notification.getSource(),
                        "the notification of type [" + type + "] must name the pool by the "
                                + "ObjectName it is registered under, since a serialized client "
                                + "only receives a notification whose source is serializable");

                Notification deserialized = roundTrip(notification);
                Assertions.assertEquals(notification.getType(), deserialized.getType(),
                        "the type must survive the delivery a serialized client sees");
                Assertions.assertEquals(notification.getMessage(), deserialized.getMessage(),
                        "the message must survive that delivery");
                Assertions.assertEquals(notification.getSequenceNumber(),
                        deserialized.getSequenceNumber(),
                        "the sequence number must survive that delivery");
                Assertions.assertEquals(poolOname, deserialized.getSource(),
                        "the source must survive that delivery as the same pool name");
            }
        } finally {
            jmxPool.removeListener(listener);
            ds.postDeregister();
        }
    }

    @Test
    void everyNotificationOfAnUnregisteredPoolNamesThePoolAndSerializes() throws Exception {
        String poolName = "JmxSourceUnregistered-" + System.nanoTime();
        DataSource ds = newDataSource(); // the fixture disables JMX
        ds.getPoolProperties().setName(poolName);
        ds.createPool();
        Assertions.assertNull(ds.getPool().getJmxPool(),
                "the fixture must not have created a pool MBean to register");

        // the MBean the pool would hand to a container, never registered: it
        // has no ObjectName to name itself with, and must still send
        // notifications a serialized client can receive
        ConnectionPool unregistered = new ConnectionPool(ds.getPool());
        List<Notification> received = new CopyOnWriteArrayList<>();
        NotificationListener listener = (notification, handback) -> received.add(notification);
        unregistered.addListener(listener);

        for (String type : ALL_TYPES) {
            Assertions.assertTrue(unregistered.notify(type, "message for " + type),
                    "the notification of type [" + type + "] must be reported as sent");
        }

        awaitUntil(() -> received.size() >= ALL_TYPES.length);
        Assertions.assertEquals(ALL_TYPES.length, received.size(),
                "every type must have reached the listener");
        for (int i = 0; i < ALL_TYPES.length; i++) {
            String type = ALL_TYPES[i];
            Notification notification = received.get(i);
            Assertions.assertEquals(type, notification.getType());
            Assertions.assertNull(unregistered.getObjectName(),
                    "this MBean was never registered, so it has no ObjectName");
            Assertions.assertTrue(notification.getSource() instanceof String,
                    "the source of an unregistered pool MBean must be a string naming the pool,"
                            + " was " + (notification.getSource() == null ? "null"
                                    : notification.getSource().getClass().getName()));
            String source = (String) notification.getSource();
            Assertions.assertEquals("tomcat.jdbc:type=ConnectionPool,name=" + poolName, source,
                    "the source must read like the shape of the name the pool would have been "
                            + "registered under");

            Notification deserialized = roundTrip(notification);
            Assertions.assertEquals(notification.getType(), deserialized.getType());
            Assertions.assertEquals(notification.getMessage(), deserialized.getMessage());
            Assertions.assertEquals(notification.getSequenceNumber(),
                    deserialized.getSequenceNumber());
            Assertions.assertEquals(source, deserialized.getSource(),
                    "the source must survive the delivery a serialized client sees");
        }
        unregistered.removeListener(listener);
    }

    @Test
    void aClientSubscribedThroughTheMBeanServerReadsThePoolNameAsSource() throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJmxEnabled(true);
        ds.getPoolProperties().setName("JmxSourceSubscriber-" + System.nanoTime());
        ds.createPool();

        ObjectName poolOname = registerPoolMBean(server, ds, "notification-subscriber");
        ConnectionPool jmxPool = ds.getPool().getJmxPool();
        List<Notification> received = new CopyOnWriteArrayList<>();
        NotificationListener listener = (notification, handback) -> received.add(notification);
        try {
            Assertions.assertNotNull(poolOname, "an enabled pool must publish its MBean");
            server.addNotificationListener(poolOname, listener, null, null);
            try {
                Assertions.assertTrue(jmxPool.notify(ConnectionPool.POOL_EMPTY, "pool is empty"),
                        "the notification must be reported as sent");

                awaitUntil(() -> !received.isEmpty());
                Assertions.assertEquals(1, received.size(),
                        "a client subscribed through the MBean server must be reached");
                Notification notification = received.get(0);
                Assertions.assertEquals(ConnectionPool.POOL_EMPTY, notification.getType());
                Assertions.assertEquals("[POOL EMPTY] pool is empty", notification.getMessage());
                // what a client behind a connector reads: the name, before and
                // after the source stopped being the MBean instance
                Assertions.assertEquals(poolOname, notification.getSource(),
                        "the subscribed client must read the pool's ObjectName as the source");
                Assertions.assertEquals(poolOname, roundTrip(notification).getSource(),
                        "that notification must also be deliverable to a serialized client");
            } finally {
                server.removeNotificationListener(poolOname, listener);
            }
        } finally {
            ds.postDeregister();
        }
    }
}
