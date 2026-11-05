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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.management.InstanceNotFoundException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.apache.tomcat.jdbc.pool.jmx.JmxUtil;

/**
 * JMX notifications of the {@code SlowQueryReportJmx} interceptor in
 * the documented {@code notifyPool=false} mode. Interceptor chains are
 * created per pooled connection, while JMX clients attach their
 * listeners to the single MBean registered per pool: a slow query
 * occurring on any connection must reach those listeners, not only the
 * ones occurring on the connection whose interceptor instance happened
 * to win the MBean registration.
 * <p>
 * The publication belongs to the pool, not to the interceptor instance
 * that made it and not to the pool name it is published under: evicting
 * the connection whose instance won the publication must leave the MBean
 * and its listeners live, and every other instance of the same pool -
 * including the second definition of the recommended two-thresholds
 * configuration - reports through it.</p>
 * <p>
 * Because the publication is the pool's, a pool configured with a name
 * another live pool holds does not report through that other pool's
 * MBean: its own publication is refused by the MBeanServer, which is
 * reported once, retried on later borrows, and leaves the other pool's
 * monitoring untouched. Closing one of two such pools gives up only what
 * that pool published.</p>
 * <p>
 * Publication and release are ordered against the pool's close, so a
 * publish that lands behind the close of its own pool gives itself back
 * instead of leaving an MBean nobody owns, and the ObjectName a release
 * gives up is the one the publication recorded - whatever definition
 * running the close happens to compute.</p>
 * <p>
 * The notifications themselves must also be deliverable beyond this
 * JVM: {@code Notification} marshals its source, so the source has to be
 * serializable - the ObjectName the MBean is registered under, or, when
 * that name cannot be formed, a string of its shape - or a client behind
 * a JMX connector never receives the notification at all.</p>
 */
class TestSlowQueryJmxNotifications extends JdbcPoolBaseTest {

    /*
     * The delivery a serialized client sees: javax.management.Notification
     * writes its source into the stream, so this fails over a source that
     * is not serializable - exactly what marshalling the notification over
     * a JMX connector does.
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
     * The notification under test is the one a slow query raises, so the
     * query that must notify has to really run long: the pools here are
     * configured with a threshold of zero - a negative threshold records
     * every query as measured and never reports one as slow - and the
     * query is made slow for a wall-clock reason: the database itself
     * waits SLOW_WAIT_MILLIS while the WHERE clause is evaluated
     * (java.lang.Thread.sleep exposed to SQL by an alias), holding the
     * client's execute() open for at least that long on any hardware.
     * A CPU-heavy query would scale with the machine; the wait does not.
     */
    private static final long SLOW_WAIT_MILLIS = 60;

    /*
     * The SQL of a query that reports itself under the "SELECT marker"
     * name the assertions look for, while waiting inside the database.
     * The alias is registered once per database; IF NOT EXISTS keeps
     * every call cheap.
     */
    private String slowSql(int marker) throws SQLException {
        try (Connection admin = adminConnection();
                Statement st = admin.createStatement()) {
            st.execute("CREATE ALIAS IF NOT EXISTS SLOW_SLEEP FOR "
                    + "\"java.lang.Thread.sleep(long)\"");
        }
        return "SELECT " + marker + " FROM SYSTEM_RANGE(1,1) WHERE SLOW_SLEEP("
                + SLOW_WAIT_MILLIS + ") IS NULL";
    }

    @Test
    void notificationOfEveryConnectionSurvivesSerializationWithItsSource() throws Exception {
        String poolName = "JmxNotifySource-" + System.nanoTime();
        DataSource ds = newDataSource();
        ds.setName(poolName);
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                + "(notifyPool=false,threshold=0)");
        ds.getPoolProperties().setMaxActive(2);
        ds.getPoolProperties().setInitialSize(0);

        List<Notification> received = new CopyOnWriteArrayList<>();
        NotificationListener listener = (notification, handback) -> received.add(notification);

        // two concurrent borrows with no pre-created connections means two
        // physical connections, hence two interceptor instances, and only
        // one of them can own the MBean registration. The raw notifications
        // are captured from the emitter itself: a listener attached through
        // the MBean server could not observe the defect here, because the
        // JDK replaces the source with the ObjectName exactly and only when
        // it is the registered instance - and a connector client behind
        // that path would silently miss every notification an instance
        // other than the registration owner sent
        try (Connection first = ds.getConnection();
                Connection second = ds.getConnection()) {
            Assertions.assertEquals(2, ds.getNumActive());
            interceptorOf(first, SlowQueryReportJmx.class)
                    .addNotificationListener(listener, null, null);
            try (Statement st1 = first.createStatement();
                    Statement st2 = second.createStatement()) {
                st1.execute(slowSql(1));
                st2.execute(slowSql(2));
            }
        }

        List<String> messages = received.stream().map(Notification::getMessage)
                .collect(Collectors.toList());
        Assertions.assertEquals(2, messages.size(),
                "every slow query of every connection must notify, received: " + messages);
        for (Notification notification : received) {
            Notification deserialized = roundTrip(notification);
            Assertions.assertEquals(notification.getType(), deserialized.getType(),
                    "the type must survive the delivery a serialized client sees");
            Assertions.assertEquals(notification.getMessage(), deserialized.getMessage(),
                    "the message must survive that delivery");
            Assertions.assertEquals(notification.getSequenceNumber(),
                    deserialized.getSequenceNumber(),
                    "the sequence number must survive that delivery");
            Assertions.assertEquals(notification.getSource(), deserialized.getSource(),
                    "the source must survive that delivery naming the same pool");
        }
    }

    @Test
    void notificationSourceNamesTheRegisteredMBean() throws Exception {
        String poolName = "JmxSourceOname-" + System.nanoTime();
        DataSource ds = newDataSource();
        ds.setName(poolName);
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                + "(notifyPool=false,threshold=0)");
        ds.getPoolProperties().setInitialSize(0);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",name=" + poolName);
        List<Notification> received = new CopyOnWriteArrayList<>();
        NotificationListener listener = (notification, handback) -> received.add(notification);

        try (Connection con = ds.getConnection()) {
            Assertions.assertTrue(server.isRegistered(oname),
                    "the interceptor MBean must be registered once the pool is in use");
            interceptorOf(con, SlowQueryReportJmx.class)
                    .addNotificationListener(listener, null, null);
            try (Statement st = con.createStatement()) {
                st.execute(slowSql(1));
            }
        }

        Assertions.assertEquals(1, received.size(), "the query must have notified");
        Assertions.assertEquals(oname, received.get(0).getSource(),
                "the source must be the ObjectName the MBean is registered under: it is what"
                        + " a client behind a connector reads, and the only serializable name"
                        + " this MBean can send - it must come from the name the registration"
                        + " was published under, not from the emitting instance");
        Assertions.assertEquals(oname, roundTrip(received.get(0)).getSource(),
                "and the notification must be deliverable to a serialized client");
    }

    @Test
    void notificationSourceHonoursTheConfiguredObjectNameOverride() throws Exception {
        String poolName = "JmxSourceOverride-" + System.nanoTime();
        // the grammar of jdbcInterceptors keeps everything after the first
        // '=' as the property value, so an objectName without a comma is
        // configurable; a comma-starved multi-property name cannot be,
        // which is why this override carries a single key property
        String customName = "tomcat.jdbc:type=SlowQueryOverride_" + System.nanoTime();
        DataSource ds = newDataSource();
        ds.setName(poolName);
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                + "(notifyPool=false,threshold=0,objectName=" + customName + ")");
        ds.getPoolProperties().setInitialSize(0);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName customOname = new ObjectName(customName);
        List<Notification> received = new CopyOnWriteArrayList<>();
        NotificationListener listener = (notification, handback) -> received.add(notification);

        try (Connection con = ds.getConnection()) {
            Assertions.assertTrue(server.isRegistered(customOname),
                    "the objectName attribute must decide the name the MBean registers under");
            interceptorOf(con, SlowQueryReportJmx.class)
                    .addNotificationListener(listener, null, null);
            try (Statement st = con.createStatement()) {
                st.execute(slowSql(1));
            }
        }

        Assertions.assertEquals(1, received.size(), "the query must have notified");
        Assertions.assertEquals(customOname, received.get(0).getSource(),
                "the source must name the MBean by the configured override it was"
                        + " registered under, not by the default shape or the instance");
    }

    @Test
    void everyDefinitionOfANameSendsTheNameTheRegistrationWasPublishedUnder() throws Exception {
        String poolName = "JmxSourceTwoDefinitions-" + System.nanoTime();
        String customName = "tomcat.jdbc:type=SlowQueryOverride_" + System.nanoTime();
        DataSource ds = newDataSource();
        ds.setName(poolName);
        // Two reporting definitions in one chain - the shape the two-thresholds
        // configuration documents: the first definition in the chain takes the
        // registration slot and publishes under its configured name, and the
        // second definition's instances never publish - yet every instance of
        // the name reports its slow queries through the name's broadcaster,
        // which the published MBean answers on.
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                + "(notifyPool=false,threshold=0,objectName=" + customName + ");"
                + "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                + "(notifyPool=false,threshold=0)");
        ds.getPoolProperties().setInitialSize(0);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName customOname = new ObjectName(customName);
        ObjectName defaultOname = new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",name=" + poolName);
        List<Notification> received = new CopyOnWriteArrayList<>();
        NotificationListener listener = (notification, handback) -> received.add(notification);

        try (Connection con = ds.getConnection()) {
            Assertions.assertTrue(server.isRegistered(customOname),
                    "the first definition of the chain wins the name and publishes under"
                            + " its configured ObjectName");
            Assertions.assertFalse(server.isRegistered(defaultOname),
                    "the second definition must not publish a second MBean under the name");
            interceptorOf(con, SlowQueryReportJmx.class)
                    .addNotificationListener(listener, null, null);
            try (Statement st = con.createStatement()) {
                st.execute(slowSql(1));
            }
        }

        Assertions.assertEquals(2, received.size(),
                "both reporting definitions of the chain see the query and notify");
        for (Notification notification : received) {
            Assertions.assertEquals(customOname, notification.getSource(),
                    "every notification of a pool name must carry the ObjectName the"
                            + " registration was published under: a non-publishing instance"
                            + " recomputing the source from its own properties stamps the"
                            + " notification with a name nothing is registered under, so a"
                            + " client filtering by source silently misses it, and a client"
                            + " grouping by source sees a phantom MBean");
        }
    }

    @Test
    void notificationSourceFallsBackToAStringWhenTheObjectNameCannotBeFormed() throws Exception {
        // a pool name containing a colon cannot be quoted into the
        // ObjectName the registration would use: registerJmx fails, the
        // MBean is never registered - so no listener reached through a
        // connector can exist for it - while local listeners must still
        // get a source that names the pool and survives serialization
        String poolName = "JmxSourceMalformed-" + System.nanoTime() + ":x";
        DataSource ds = newDataSource();
        ds.setName(poolName);
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                + "(notifyPool=false,threshold=0)");
        ds.getPoolProperties().setInitialSize(0);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> registeredBefore = server.queryNames(new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",*"), null);
        List<Notification> received = new CopyOnWriteArrayList<>();
        NotificationListener listener = (notification, handback) -> received.add(notification);

        try (Connection con = ds.getConnection()) {
            Assertions.assertEquals(registeredBefore,
                    server.queryNames(new ObjectName("tomcat.jdbc:type="
                            + SlowQueryReportJmx.class.getName() + ",*"), null),
                    "no MBean may be registered while the name cannot be formed");
            Assertions.assertNull(SlowQueryReportJmx.jmxStateFor(ds.getPool()).publishedUnder(),
                    "nothing may be published for a pool whose ObjectName cannot be"
                            + " formed");
            interceptorOf(con, SlowQueryReportJmx.class)
                    .addNotificationListener(listener, null, null);
            try (Statement st = con.createStatement()) {
                st.execute(slowSql(1));
            }
        }

        Assertions.assertEquals(1, received.size(),
                "an unregistrable pool must still notify its local listeners");
        Object source = received.get(0).getSource();
        Assertions.assertInstanceOf(String.class, source,
                "the source must be a string when no ObjectName can be formed, was "
                        + (source == null ? "null" : source.getClass().getName()));
        Assertions.assertEquals("tomcat.jdbc:type=" + SlowQueryReportJmx.class.getName()
                + ",name=" + poolName, source,
                "the string must read like the shape of the name the MBean would have"
                        + " been registered under");
        Assertions.assertEquals(source, roundTrip(received.get(0)).getSource(),
                "and it must survive the delivery a serialized client sees");
    }

    @Test
    void notifyPoolFalseDeliversNotificationsOfEveryConnection() throws Exception {
        String poolName = "JmxNotify-" + System.nanoTime();
        DataSource ds = newDataSource();
        ds.setName(poolName);
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                + "(notifyPool=false,threshold=0)");
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
                    st1.execute(slowSql(1));
                    st2.execute(slowSql(2));
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
                + "(notifyPool=false,threshold=0)");
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

        // the first borrow publishes the name's registration; the second
        // concurrent borrow (hence second physical connection and second
        // interceptor instance) shares it and its broadcaster
        Connection ownerConnection = ds.getConnection();
        Connection otherConnection = ds.getConnection();
        try {
            Assertions.assertEquals(2, ds.getNumActive());
            Assertions.assertNotNull(
                    SlowQueryReportJmx.jmxStateFor(ds.getPool()).publishedUnder(),
                    "the first borrow of the pool must have published the pool's registration");

            server.addNotificationListener(oname, listener, null, null);
            try (Statement st1 = ownerConnection.createStatement();
                    Statement st2 = otherConnection.createStatement()) {
                st1.execute(slowSql(1));
                st2.execute(slowSql(2));
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
                st.execute(slowSql(3));
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
    void theConcurrentFirstBorrowsOfAPoolMakeASinglePublication() throws Exception {
        String poolName = "JmxOnePublication-" + System.nanoTime();
        DataSource ds = reportingPoolNamed(poolName,
                CountingFormationReport.class.getName() + "(notifyPool=false,threshold=0)");
        ds.getPoolProperties().setMaxActive(4);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = new ObjectName("tomcat.jdbc:type="
                + CountingFormationReport.class.getName() + ",name=" + poolName);

        // Four concurrent first borrows: four interceptor instances, all of
        // which find nothing published for the pool at once. Publication is
        // the pool's, so exactly one of them is handed it - the others read
        // the publication as in flight or made, and go and serve their borrow
        Connection[] borrowed = new Connection[4];
        CountDownLatch startGate = new CountDownLatch(1);
        Throwable[] failures = new Throwable[borrowed.length];
        Thread[] borrowers = new Thread[borrowed.length];
        for (int i = 0; i < borrowed.length; i++) {
            final int slot = i;
            borrowers[i] = new Thread(() -> {
                try {
                    startGate.await();
                    borrowed[slot] = ds.getConnection();
                } catch (Throwable x) {
                    failures[slot] = x;
                }
            }, "first-borrower-" + i);
            borrowers[i].start();
        }
        startGate.countDown();
        try {
            for (Thread borrower : borrowers) {
                borrower.join(AWAIT_TIMEOUT_MS);
                Assertions.assertFalse(borrower.isAlive(),
                        "no borrow may be left waiting on the publication - the gate must be"
                                + " a decision, not a queue");
            }
            for (Throwable failure : failures) {
                Assertions.assertNull(failure, "concurrent first borrows must not fail: " + failure);
            }
            Assertions.assertEquals(4, ds.getNumActive(),
                    "every borrow must have been served");
            Assertions.assertTrue(server.isRegistered(oname),
                    "and the pool must have come out of it published");
            Assertions.assertEquals(1, nameFormations(poolName),
                    "one pool means one publication, and one publication means the ObjectName"
                            + " is formed once: a borrow that cannot win the publication must be"
                            + " turned away before the name is formed and a resource built for the"
                            + " race it has already lost");
            Assertions.assertEquals(oname,
                    SlowQueryReportJmx.jmxStateFor(ds.getPool()).publishedUnder(),
                    "and what stands published is the name that one publication recorded");
        } finally {
            for (Connection con : borrowed) {
                if (con != null) {
                    try {
                        con.close();
                    } catch (SQLException ignore) {
                        // the pool is going anyway
                    }
                }
            }
            ds.close();
        }
    }

    /* A live pool that reports every query through the JMX interceptor under the given name. */
    private DataSource reportingPoolNamed(String name) {
        return reportingPoolNamed(name, "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                + "(notifyPool=false,threshold=0)");
    }

    /* A live pool whose reporting interceptor is configured by the given string. */
    private DataSource reportingPoolNamed(String name, String interceptorConfig) {
        DataSource ds = newDataSource(); // jmxEnabled=false removes the
        // pools' own ObjectName clash; the interceptor registers by name
        ds.getPoolProperties().setName(name);
        ds.setJdbcInterceptors(interceptorConfig);
        ds.getPoolProperties().setInitialSize(0);
        return ds;
    }

    @Test
    void eachPoolReportsThroughItsOwnPublicationAndNotTheOtherS() throws Exception {
        String ownerName = "JmxOwner-" + System.nanoTime();
        String otherName = "JmxOther-" + System.nanoTime();
        DataSource ownerPool = reportingPoolNamed(ownerName);
        DataSource otherPool = reportingPoolNamed(otherName);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName ownerOn = new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",name=" + ownerName);
        ObjectName otherOn = new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",name=" + otherName);
        List<Notification> published = new CopyOnWriteArrayList<>();
        List<Notification> otherPublished = new CopyOnWriteArrayList<>();
        NotificationListener publishedListener = (notification, handback) -> published.add(notification);
        NotificationListener otherListener = (notification, handback) -> otherPublished.add(notification);

        // Each pool publishes its own MBean under its own name, and its
        // queries reach only the listeners of that publication.
        Connection ownerConnection = ownerPool.getConnection();
        Connection otherConnection = otherPool.getConnection();
        try {
            server.addNotificationListener(ownerOn, publishedListener, null, null);
            server.addNotificationListener(otherOn, otherListener, null, null);

            try (Statement st = ownerConnection.createStatement()) {
                st.execute(slowSql(1));
            }
            Assertions.assertTrue(published.stream().anyMatch(n -> n.getMessage().contains("SELECT 1")),
                    "the pool's queries reach the listeners of its MBean, received: "
                            + published);

            try (Statement st = otherConnection.createStatement()) {
                st.execute(slowSql(2));
            }
            Assertions.assertTrue(otherPublished.stream().anyMatch(n -> n.getMessage().contains("SELECT 2")),
                    "the other pool must notify its own listeners, received: " + otherPublished);
            Assertions.assertFalse(published.stream().anyMatch(n -> n.getMessage().contains("SELECT 2")),
                    "and it must not report into the other pool's MBean: a client listening to the"
                            + " name would otherwise read another pool's queries as its own,"
                            + " received: " + published);

            // Closing one pool gives up its own publication and leaves the
            // other pool's publication alone.
            otherConnection.close();
            otherConnection = null;
            otherPool.close();

            try (Statement st = ownerConnection.createStatement()) {
                st.execute(slowSql(3));
            }
            Assertions.assertFalse(server.isRegistered(otherOn),
                    "closing a pool gives up its own publication");
            Assertions.assertTrue(server.isRegistered(ownerOn),
                    "closing one pool must leave the other pool's publication standing");
            Assertions.assertTrue(published.stream().anyMatch(n -> n.getMessage().contains("SELECT 3")),
                    "and its clients' listeners must still be served, received: " + published);
        } finally {
            if (ownerConnection != null) {
                try {
                    ownerConnection.close();
                } catch (SQLException ignore) {
                    // pool already closed
                }
            }
            if (otherConnection != null) {
                try {
                    otherConnection.close();
                } catch (SQLException ignore) {
                    // pool already closed
                }
            }
            ownerPool.close();
            try {
                server.removeNotificationListener(ownerOn, publishedListener);
            } catch (ListenerNotFoundException | InstanceNotFoundException ignore) {
                // the teardown under test may already have dropped the binding
            }
            try {
                server.removeNotificationListener(otherOn, otherListener);
            } catch (ListenerNotFoundException | InstanceNotFoundException ignore) {
                // the teardown under test may already have dropped the binding
            }
            Assertions.assertFalse(server.isRegistered(ownerOn),
                    "closing the pool that published the MBean gives it up");
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
                + "(notifyPool=false,threshold=0);"
                + "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                + "(notifyPool=false,threshold=0)");
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

            // the single publication of the pool stands for the two instances
            // of this chain - the one that made it and the one that only
            // reports through it
            SlowQueryReportJmx.PoolJmx publication = SlowQueryReportJmx.jmxStateFor(ds.getPool());
            Assertions.assertEquals(oname, publication.publishedUnder(),
                    "the pool's first borrow must have published the pool's registration, and the"
                            + " second definition of the chain must not have published a second one");
            int chainInstances = 0;
            JdbcInterceptor interceptor = (JdbcInterceptor) Proxy.getInvocationHandler(con);
            while (interceptor != null) {
                if (interceptor instanceof SlowQueryReportJmx) {
                    chainInstances++;
                }
                interceptor = interceptor.getNext();
            }
            Assertions.assertEquals(2, chainInstances,
                    "the chain must hold both configured interceptor instances");
            Assertions.assertEquals(poolName, server.getAttribute(oname, "PoolName"),
                    "the one registration of the pool must be the one published for this pool");

            st.execute(slowSql(1));
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

    /*
     * A reporting interceptor that counts the ObjectNames its borrows form.
     * Once a pool name holds its registration, a borrow cannot publish a
     * second MBean under it - the slot race is already lost to the pool's own
     * registration - so forming the name and building a Registration for that
     * losing race is work a healthy pool pays per connection, per borrow.
     */
    public static class CountingFormationReport extends SlowQueryReportJmx {
        @Override
        public ObjectName getObjectName(Class<?> clazz, String poolName)
                throws MalformedObjectNameException {
            NAME_FORMATIONS.computeIfAbsent(String.valueOf(poolName), k -> new AtomicInteger())
                    .incrementAndGet();
            return super.getObjectName(clazz, poolName);
        }
    }

    private static final Map<String, AtomicInteger> NAME_FORMATIONS = new ConcurrentHashMap<>();

    private static int nameFormations(String poolName) {
        AtomicInteger count = NAME_FORMATIONS.get(poolName);
        return count == null ? 0 : count.get();
    }

    @Test
    void aRegisteredNameCostsNoFurtherNameFormationPerBorrow() throws Exception {
        String poolName = "JmxFormation-" + System.nanoTime();
        DataSource ds = reportingPoolNamed(poolName,
                CountingFormationReport.class.getName() + "(notifyPool=false,threshold=0)");
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = new ObjectName("tomcat.jdbc:type="
                + CountingFormationReport.class.getName() + ",name=" + poolName);
        try {
            try (@SuppressWarnings("unused") Connection first = ds.getConnection()) {
                Assertions.assertTrue(server.isRegistered(oname),
                        "the premise: the first borrow published the MBean under the name");
                int formationsAtRegistration = nameFormations(poolName);
                Assertions.assertTrue(formationsAtRegistration >= 1,
                        "the borrow that publishes the name must have formed it");

                try (@SuppressWarnings("unused") Connection second = ds.getConnection()) {
                    // nothing to do: the borrow itself is the measured step
                }
                try (@SuppressWarnings("unused") Connection third = ds.getConnection()) {
                    // a reused connection exercises the same path again
                }

                Assertions.assertEquals(formationsAtRegistration, nameFormations(poolName),
                        "a name that already holds its registration must be noticed before"
                                + " the name is formed and a Registration built to lose the"
                                + " slot race: parsing a full ObjectName and allocating a"
                                + " publication that cannot happen on every borrow of a"
                                + " perfectly registered pool is cost with no possible use -"
                                + " and a refused registration still leaves the slot free, so"
                                + " the retry semantics of a failed publish are untouched");
            }
        } finally {
            ds.close();
        }
    }

    @Test
    void aRefusedPublicationKeepsRetryingAndAPublishTheServerTookStandsUntilTheClose()
            throws Exception {
        String poolName = "JmxRegFail-" + System.nanoTime();
        DataSource ds = newDataSource();
        ds.setName(poolName);
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                + "(notifyPool=false,threshold=0)");
        ds.getPoolProperties().setInitialSize(0);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",name=" + poolName);

        Obstacle obstacle = new Obstacle();
        server.registerMBean(obstacle, oname);
        try {
            ConnectionPool pool;
            try (@SuppressWarnings("unused") Connection first = ds.getConnection()) {
                pool = ds.getPool();
                // The borrow tried to publish and the MBeanServer refused it.
                Assertions.assertEquals("registration-obstacle",
                        server.getAttribute(oname, "HeldBy"),
                        "the interceptor's publish must have collided with the foreign MBean");
                SlowQueryReportJmx.PoolJmx state = SlowQueryReportJmx.jmxStateFor(pool);
                Assertions.assertNull(state.publishedUnder(),
                        "a publish the MBeanServer refused must not leave the pool claiming an"
                                + " MBean that was never published - every later borrow would find"
                                + " the pool published and never retry");

                // With the obstacle gone, a later borrow must still get the
                // MBean published: the refused attempt must not have latched
                // anything for the life of the pool.
                server.unregisterMBean(oname);
                try (@SuppressWarnings("unused") Connection next = ds.getConnection()) {
                    Assertions.assertTrue(server.isRegistered(oname),
                            "the borrow after the obstacle was removed must publish the MBean");
                    // the attribute of the interceptor's MBean interface,
                    // answered by the registered resource: not the obstacle,
                    // which exposes no such attribute
                    Assertions.assertDoesNotThrow(() -> server.getAttribute(oname, "SlowQueriesCD"),
                            "the published MBean must be the interceptor's, answering its statistics query");
                    Assertions.assertEquals(oname,
                            SlowQueryReportJmx.jmxStateFor(pool).publishedUnder(),
                            "the retrying borrow must have recorded what it published");
                }
            }
            ds.close();
            Assertions.assertFalse(server.isRegistered(oname),
                    "closing the pool must release the registration the retry published");
            Assertions.assertNull(SlowQueryReportJmx.jmxStateFor(pool),
                    "and give up the JMX state it hung from");
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
    void persistentPublicationRefusalKeepsRetryingAndCleansUpOnClose() throws Exception {
        String poolName = "JmxRegRefuse-" + System.nanoTime();
        DataSource refusingPool = reportingPoolNamed(poolName);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",name=" + poolName);

        Obstacle obstacle = new Obstacle();
        server.registerMBean(obstacle, oname);
        List<Notification> received = new CopyOnWriteArrayList<>();
        NotificationListener listener = (notification, handback) -> received.add(notification);
        try {
            // The first refused borrow: nothing is published for the pool, and
            // the failure is marked as reported - the loud report of this pool
            // (warn/error) has gone out, its repeats ride at debug without
            // a stack trace (not asserted: the module's test infrastructure
            // captures no log, so the throttle is pinned through its
            // bookkeeping only)
            Connection first = refusingPool.getConnection();
            ConnectionPool pool = refusingPool.getPool();
            SlowQueryReportJmx.PoolJmx state = SlowQueryReportJmx.jmxStateFor(pool);
            Assertions.assertNull(state.publishedUnder(),
                    "a refused publication must not leave the pool claiming an MBean");
            Assertions.assertEquals(oname, state.failureMarker(),
                    "the first refusal of this ObjectName must be marked as reported for the"
                            + " throttle");
            interceptorOf(first, SlowQueryReportJmx.class)
                    .addNotificationListener(listener, null, null);

            // Later borrows keep retrying: still refused (the obstacle holds
            // the name), still nothing published, and the identical marker
            // keeps their logging throttled
            try (@SuppressWarnings("unused") Connection second = refusingPool.getConnection();
                    @SuppressWarnings("unused") Connection third = refusingPool.getConnection()) {
                Assertions.assertNull(state.publishedUnder(),
                        "retried refusals must leave the pool unpublished, so that the next borrow"
                                + " can try again");
                Assertions.assertEquals(oname, state.failureMarker(),
                        "repeated refusals of the same ObjectName must not re-mark it loudly");
                Assertions.assertEquals("registration-obstacle",
                        server.getAttribute(oname, "HeldBy"),
                        "the retried publishes must still be colliding with the obstacle");
                // A slow query of the refused pool still reaches the pool's own
                // broadcaster - state the close has to give up even though no
                // publication of this pool ever succeeded
                try (Statement st = first.createStatement()) {
                    st.execute(slowSql(1));
                }
                Assertions.assertTrue(
                        received.stream().anyMatch(n -> n.getMessage().contains("SELECT 1")),
                        "a pool whose publication was refused must still notify its own listeners,"
                                + " received: " + received);
            }
            first.close();

            // Close a pool that never got published: its close gives up the
            // state it reported through - broadcaster and throttle marker and
            // all - so neither outlives the pool, and a later pool under the
            // same name starts its own failure cycle marked loudly again
            refusingPool.close();
            Assertions.assertNull(SlowQueryReportJmx.jmxStateFor(pool),
                    "the close of the pool gives up the JMX state it published nothing from");
            Assertions.assertEquals("registration-obstacle",
                    server.getAttribute(oname, "HeldBy"),
                    "and the name still belongs to the obstacle: the refused pool never took it");

            // the name stayed retryable through every refusal: once the
            // obstacle is gone, a fresh pool under the name publishes at once
            // and publishes cleanly - success marks nothing in the throttle
            server.unregisterMBean(oname);
            DataSource revivedPool = reportingPoolNamed(poolName);
            try (@SuppressWarnings("unused") Connection borrowed = revivedPool.getConnection()) {
                Assertions.assertTrue(server.isRegistered(oname),
                        "the first borrow of a pool started after the obstacle is gone"
                                + " must publish the MBean");
                Assertions.assertNull(SlowQueryReportJmx.jmxStateFor(revivedPool.getPool())
                                .failureMarker(),
                        "a succeeding publication must not leave a throttle marker behind");
            }
            revivedPool.close();
            Assertions.assertFalse(server.isRegistered(oname),
                    "closing the revived pool must release the publication again");
        } finally {
            try {
                server.unregisterMBean(oname);
            } catch (InstanceNotFoundException ignore) {
                // nothing registered left to clean up under this name
            }
        }
    }

    @Test
    void aNotificationSentAfterThePoolClosedDoesNotBringItsJmxStateBack() throws Exception {
        String poolName = "JmxLateNotify-" + System.nanoTime();
        DataSource ds = reportingPoolNamed(poolName);
        ObjectName oname = new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",name=" + poolName);
        SlowQueryReportJmx reporter;
        ConnectionPool pool;
        try (Connection con = ds.getConnection()) {
            pool = ds.getPool();
            reporter = interceptorOf(con, SlowQueryReportJmx.class);
            try (Statement st = con.createStatement()) {
                st.execute(slowSql(1));
            }
            SlowQueryReportJmx.PoolJmx state = SlowQueryReportJmx.jmxStateFor(pool);
            Assertions.assertEquals(oname, state.publishedUnder(),
                    "the premise: reporting a query of a live pool reports through the state"
                            + " published for that pool");
        }
        ds.close();
        Assertions.assertNull(SlowQueryReportJmx.jmxStateFor(pool),
                "the premise: closing the pool gave its JMX state up with its store");

        // A query that was already running while the pool came down reports
        // when it finishes, and the interceptor instance that served it outlives
        // the close holding the name it reported under.
        reporter.notifyJmx("SELECT 1", SlowQueryReportJmx.SLOW_QUERY_NOTIFICATION);

        Assertions.assertNull(SlowQueryReportJmx.jmxStateFor(pool),
                "a notification sent for a pool that has come down must not put its state back:"
                        + " nothing that runs for a closed pool will give it up again, so the"
                        + " state - and any listener a client still holds on the broadcaster it"
                        + " comes with, and the class loader behind it - would outlive the pool by"
                        + " the life of the JVM");
        Assertions.assertFalse(ManagementFactory.getPlatformMBeanServer().isRegistered(oname),
                "and the send must not leave anything published under the name either");
    }

    @Test
    void concurrentClosesOfDistinctPoolsLeaveNothingBehind() throws Exception {
        String[] names = { "JmxRace0-" + System.nanoTime(), "JmxRace1-" + System.nanoTime(),
                "JmxRace2-" + System.nanoTime() };
        DataSource[] sources = new DataSource[names.length];
        ObjectName[] onames = new ObjectName[names.length];

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        Connection[] connections = new Connection[names.length];
        for (int i = 0; i < names.length; i++) {
            sources[i] = reportingPoolNamed(names[i]);
            onames[i] = new ObjectName("tomcat.jdbc:type="
                    + SlowQueryReportJmx.class.getName() + ",name=" + names[i]);
            connections[i] = sources[i].getConnection();
            Assertions.assertTrue(server.isRegistered(onames[i]),
                    "the MBean must be registered while the pool runs");
        }
        ConnectionPool[] pools = { sources[0].getPool(), sources[1].getPool(), sources[2].getPool() };
        // return every connection first, so the concurrent closes race only
        // on the registries, not on pooled connections
        for (Connection connection : connections) {
            connection.close();
        }

        // a start gate releases all three closes together: each of them gives
        // up its own pool's store and its own publication - none of which may
        // block on, fail because of, or miss what one of the others is doing
        CountDownLatch startGate = new CountDownLatch(1);
        Throwable[] failures = new Throwable[sources.length];
        Thread[] closers = new Thread[sources.length];
        for (int i = 0; i < sources.length; i++) {
            final DataSource pool = sources[i];
            final int slot = i;
            closers[i] = new Thread(() -> {
                try {
                    startGate.await();
                    pool.close();
                } catch (Throwable x) {
                    failures[slot] = x;
                }
            }, "pool-closer-" + i);
            closers[i].start();
        }
        startGate.countDown();
        for (Thread closer : closers) {
            closer.join(AWAIT_TIMEOUT_MS);
            Assertions.assertFalse(closer.isAlive(),
                    "no close may be left waiting on the others - each close has its own pool's"
                            + " state to give up, so the closes cannot need each other");
        }
        for (Throwable failure : failures) {
            Assertions.assertNull(failure,
                    "concurrent close must not fail: " + failure);
        }

        awaitSessions(0); // every physical session of every pool is really gone
        for (int i = 0; i < names.length; i++) {
            Assertions.assertFalse(server.isRegistered(onames[i]),
                    "every close must release its own pool's MBean");
            Assertions.assertNull(SlowQueryReportJmx.jmxStateFor(pools[i]),
                    "and every one of the closes must have given up its own pool's JMX state");
            Assertions.assertNull(SlowQueryReport.getPoolStats(names[i]),
                    "the statistics of every name must be freed with its pool's close");
        }

        // the name was really freed, not merely flagged: a pool restarted under
        // it must be able to publish, and must release the publication again
        // when it closes
        DataSource restarted = reportingPoolNamed(names[0]);
        try (@SuppressWarnings("unused") Connection borrowed = restarted.getConnection()) {
            Assertions.assertTrue(server.isRegistered(onames[0]),
                    "a pool started under the concurrently freed name must publish its MBean");
        }
        restarted.close();
        Assertions.assertFalse(server.isRegistered(onames[0]),
                "closing the restarted pool must release the publication again");
    }

    @Test
    void theCloseUnregistersTheNameThePublicationWasMadeUnder() throws Exception {
        String poolName = "JmxOnameOwner-" + System.nanoTime();
        // the grammar of jdbcInterceptors keeps everything after the first
        // '=' as the property value, so - as in the override test above -
        // this single-key-property objectName is configurable
        String customName = "tomcat.jdbc:type=SlowQueryReleased_" + System.nanoTime();
        DataSource ds = reportingPoolNamed(poolName,
                "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                        + "(notifyPool=false,threshold=0,objectName=" + customName + ")");

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName customOname = new ObjectName(customName);
        ObjectName defaultOname = new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",name=" + poolName);
        try {
            // the first borrow takes the publication, under the ObjectName its
            // own definition configures
            try (@SuppressWarnings("unused") Connection borrowing = ds.getConnection()) {
                Assertions.assertTrue(server.isRegistered(customOname),
                        "the pool's borrow must publish under the configured override");
            }

            // The property is mutable at runtime, and the close is delivered by
            // a freshly built interceptor of whatever the property lists by then:
            // this definition computes the default ObjectName and published
            // nothing. The close must unregister the ObjectName the publication
            // recorded, not the one the instance running it happens to compute
            ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                    + "(notifyPool=false,threshold=0)");
            ds.close();

            Assertions.assertFalse(server.isRegistered(customOname),
                    "the close must unregister the ObjectName the publication was actually made"
                            + " under - the override of a definition this pool no longer lists -"
                            + " instead of giving up on a name the closing definition computes but"
                            + " never registered, which leaks the published MBean for ever");
            Assertions.assertFalse(server.isRegistered(defaultOname),
                    "and no registration may linger under the default shape either");
        } finally {
            for (ObjectName candidate : new ObjectName[] { customOname, defaultOname }) {
                try {
                    server.unregisterMBean(candidate);
                } catch (InstanceNotFoundException ignore) {
                    // nothing registered left to clean up under this name
                }
            }
        }
    }


    @Test
    void aPublicationThatCompletesAfterTheCloseOfItsPoolGivesItselfBack() throws Exception {
        String poolName = "JmxLatePublish-" + System.nanoTime();
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",name=" + poolName);

        // The publication runs as two steps: the borrow is handed it, and the
        // MBeanServer then takes the resource. The race this test pins has the
        // pool's close land between them - it gives up everything the pool's
        // state holds and unregisters the ObjectName that state records, which
        // the in-flight publish has not recorded yet - and the publication then
        // completes behind it, known to nobody but the thread that made it. The
        // window inside one borrow is a thread race no test can force, so the
        // steps are driven apart here, in the order the race leaves them.
        DataSource ds = reportingPoolNamed(poolName);
        ds.createPool();
        ConnectionPool pool = ds.getPool();
        SlowQueryReport.PoolStatistics statistics = SlowQueryReport.statisticsFor(pool);
        Assertions.assertNotNull(statistics,
                "the premise: the start of the pool registered its statistics");
        Assertions.assertNull(SlowQueryReportJmx.jmxStateFor(pool),
                "the premise: no borrow has needed the pool's JMX state yet");

        // The borrow's half of the publication: the state is built, the
        // publication is handed to it, and its resource is on its way to the
        // MBeanServer.
        SlowQueryReportJmx.PoolJmx state = new SlowQueryReportJmx.PoolJmx(poolName);
        statistics.setJmxState(state);
        Assertions.assertTrue(state.beginPublication(),
                "the first borrow of a pool that published nothing is handed the publication");
        server.registerMBean(new SlowQueryReportJmx.Registration(poolName, statistics, state), oname);

        // The close that lands inside the window: it gives the store up, and
        // with it the state - which records no publication, so it finds nothing
        // of its own to unregister.
        SlowQueryReport.releaseStatistics(pool);
        Assertions.assertTrue(state.isReleased(),
                "the premise: the close gives the state up before the publish lands");

        // The stalled publication now completes, and the state tells the borrow
        // that what it published belongs to nobody any more.
        Assertions.assertFalse(state.published(oname),
                "a publication that lands behind the close of its own pool must be told it stands"
                        + " outside anything that could ever give it up");
        Assertions.assertTrue(state.isReleased(),
                "and the state stays given up: no later borrow publishes through it");

        // The borrow's own undo (see registerJmx): the thread that made the
        // publication is the only one that knows of it, and it takes it down.
        Assertions.assertTrue(server.isRegistered(oname),
                "the premise: the MBeanServer did take the resource the stalled publish handed it");
        JmxUtil.unregisterJmx(oname);
        Assertions.assertFalse(server.isRegistered(oname),
                "an undone publication must not stay published for the life of the JVM under a"
                        + " name every later pool wants - nothing else in this JVM can reach it");

        // The name must stay usable: a pool started after the undone publish
        // publishes cleanly on its first borrow and gives the publication back
        // when it closes.
        DataSource revived = reportingPoolNamed(poolName);
        try (@SuppressWarnings("unused") Connection borrowed = revived.getConnection()) {
            Assertions.assertTrue(server.isRegistered(oname),
                    "the first borrow of a pool started after the undone publish must publish the"
                            + " name - the undone publication must not poison it");
        }
        revived.close();
        Assertions.assertFalse(server.isRegistered(oname),
                "and the close of that pool must release it again");
        ds.close();
    }




    @Test
    void aMalformedObjectNameOverrideFailsOnlyTheRegistration() throws Exception {
        // An override value that is not an ObjectName at all (no domain
        // separator) fails the name formation on every borrow, before the
        // registry slot and its refusal throttle are reachable. The first
        // failure is reported with its stack trace; later retries stay at
        // debug level, and the failure touches nothing but monitoring.
        String poolName = "JmxMalformedOname-" + System.nanoTime();
        DataSource ds = reportingPoolNamed(poolName,
                "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                        + "(notifyPool=false,threshold=-1,objectName=value-without-a-domain)");

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> registeredBefore = server.queryNames(new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",*"), null);
        Logger logger = Logger.getLogger(SlowQueryReportJmx.class.getName());
        Level originalLevel = logger.getLevel();
        List<LogRecord> records = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
                // no buffering
            }

            @Override
            public void close() {
                // nothing to release
            }
        };
        logger.addHandler(handler);
        logger.setLevel(Level.ALL);
        try {
            try (@SuppressWarnings("unused") Connection first = ds.getConnection()) {
                // the borrow itself served: a failed name formation cannot fail
                // the pool's operation, only its monitoring
                Assertions.assertEquals(registeredBefore, server.queryNames(new ObjectName("tomcat.jdbc:type="
                        + SlowQueryReportJmx.class.getName() + ",*"), null),
                        "no registration may appear while the name cannot be formed");
            }
            try (@SuppressWarnings("unused") Connection second = ds.getConnection()) {
                SlowQueryReportJmx.PoolJmx state = SlowQueryReportJmx.jmxStateFor(ds.getPool());
                Assertions.assertNull(state.publishedUnder(),
                        "a name that cannot be formed must never leave the pool claiming a publication");
                Assertions.assertNull(state.failureMarker(),
                        "a failure that cannot form its ObjectName has no name to mark as reported");
            }
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }

        long noDataErrors = records.stream()
                .filter(r -> r.getLevel().intValue() >= Level.SEVERE.intValue()
                        && r.getMessage() != null && r.getMessage().contains("no JMX data will be exposed"))
                .count();
        Assertions.assertEquals(1, noDataErrors,
                "a permanently malformed name must be reported once, not on every borrow");

        ConnectionPool pool = ds.getPool();
        ds.close();
        Assertions.assertNull(SlowQueryReportJmx.jmxStateFor(pool),
                "and closing the pool leaves no JMX state behind");
        Assertions.assertEquals(registeredBefore, server.queryNames(new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",*"), null),
                "and nothing under the type for this pool");
    }

    @Test
    void aNullObjectNamePropertyValueBehavesAsIfAbsent() throws Exception {
        // The property grammar cannot produce a value-less property, but the
        // public programmatic API can: InterceptorProperty carries a null
        // value, and a definition handed through it reaches the chain with
        // one. A null value names nothing to override - the same reading
        // every sibling property of this family gives a null value - so the
        // default name applies and everything else must hold as if the
        // property were absent. Before the guard the null reached the
        // ObjectName constructor as an NPE that registerJmx does not catch,
        // escaping through reset() to fail every borrow of the pool and
        // discard the physical connection each attempt had just opened.
        String poolName = "JmxNullOname-" + System.nanoTime();
        DataSource ds = reportingPoolNamed(poolName);
        for (PoolProperties.InterceptorDefinition definition
                : ds.getPoolProperties().getJdbcInterceptorsAsArray()) {
            if (definition.getClassName().endsWith("SlowQueryReportJmx")) {
                definition.addProperty("objectName", null);
            }
        }

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",name=" + poolName);
        try (@SuppressWarnings("unused") Connection first = ds.getConnection()) {
            Assertions.assertTrue(server.isRegistered(oname),
                    "a value-less objectName property must cost no more than any other"
                            + " unformable override - the borrow itself must be served, and"
                            + " the registration fall back to the default name of the pool");
        }
        ConnectionPool pool = ds.getPool();
        ds.close();
        Assertions.assertFalse(server.isRegistered(oname),
                "and closing the pool releases the name again");
        Assertions.assertNull(SlowQueryReportJmx.jmxStateFor(pool),
                "with no JMX state left behind for the pool");
    }


    @Test
    void aCloseWhoseOwnDefinitionCannotFormANameStillGivesThePublicationBack() throws Exception {
        String poolName = "JmxBadRelease-" + System.nanoTime();
        DataSource ds = reportingPoolNamed(poolName);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",name=" + poolName);
        try (Connection con = ds.getConnection()) {
            Assertions.assertTrue(server.isRegistered(oname),
                    "the premise: the borrow published the pool under the default name");
            // The interceptor property is mutable at runtime, so the close is
            // delivered to a definition that has nothing to do with the
            // publication. This one cannot form an ObjectName at all: the
            // release must not need one, because the ObjectName it gives up is
            // the one the publication itself recorded.
            ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                    + "(notifyPool=false,threshold=0,objectName=value-without-a-domain)");
        }
        ds.close();
        Assertions.assertFalse(server.isRegistered(oname),
                "a close that cannot work out a name of its own must still give the pool's"
                        + " publication back - a name the MBeanServer still holds while nothing"
                        + " left in this JVM knows who to give it up to is unrepairable");
    }

}
