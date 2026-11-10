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
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * The management surface of the {@code SlowQueryReportJmx} interceptor
 * MBean. The interceptor is registered as a <em>Standard</em> MBean, so
 * its management interface is exactly what
 * {@link SlowQueryReportJmxMBean} declares (plus the notification
 * methods) - a public method on the resource that the interface does not
 * declare is invisible to a JMX client. The pool's own JMX metadata
 * ({@code mbeans-descriptors.xml}) and the implementation's javadoc
 * advertise {@code PoolName}, {@code PoolNames}, {@code SlowQueriesCD}
 * and the {@code resetStats()} operation, so those must be reachable
 * through the MBeanServer: an operator must be able to discover pool
 * names and clear recorded statistics without restarting the pool.
 * <p>
 * Statistics belong to a <em>pool</em>, so the MBean one pool published reads and clears what that pool recorded - and
 * nothing another pool collected, however it was named.
 */
class TestSlowQueryJmxMBeanSurface extends JdbcPoolBaseTest {

    /* A pool whose interceptor registers its MBean and records every query. */
    private DataSource reportingPoolNamed(String name) {
        DataSource ds = newDataSource(); // jmxEnabled=false keeps the pools'
        // own ObjectNames out of the way; the interceptor registers by name
        ds.getPoolProperties().setName(name);
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                + "(notifyPool=false,threshold=-1)");
        ds.getPoolProperties().setInitialSize(0);
        return ds;
    }

    private static ObjectName objectNameFor(String poolName) throws Exception {
        return new ObjectName("tomcat.jdbc:type=" + SlowQueryReportJmx.class.getName()
                + ",name=" + poolName);
    }

    private static Set<String> attributeNames(MBeanInfo info) {
        return Arrays.stream(info.getAttributes()).map(MBeanAttributeInfo::getName)
                .collect(Collectors.toSet());
    }

    private static Set<String> operationNames(MBeanInfo info) {
        return Arrays.stream(info.getOperations()).map(MBeanOperationInfo::getName)
                .collect(Collectors.toSet());
    }

    @Test
    void mBeanInfoExposesEveryAdvertisedAttributeAndOperation() throws Exception {
        String poolName = "JmxSurface-" + System.nanoTime();
        DataSource ds = reportingPoolNamed(poolName);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = objectNameFor(poolName);

        try (@SuppressWarnings("unused") Connection con = ds.getConnection()) {
            Assertions.assertTrue(server.isRegistered(oname),
                    "the interceptor MBean must be registered once the pool is in use");
            MBeanInfo info = server.getMBeanInfo(oname);
            Set<String> attributes = attributeNames(info);
            Assertions.assertEquals(new HashSet<>(Arrays.asList(
                            "PoolName", "PoolNames", "SlowQueriesCD")), attributes,
                    "the attributes the descriptor and the implementation javadoc advertise must "
                            + "be exactly the attributes of the Standard MBean interface");
            Assertions.assertFalse(attributes.contains("NotifyPool"),
                    "notifyPool is per-interceptor-instance configuration, not management state: "
                            + "every instance routes its own notifications from its own copy, so a "
                            + "JMX write would re-route only the connection owning the registration");

            Assertions.assertTrue(operationNames(info).contains("resetStats"),
                    "resetStats() must be an operation of the management interface, found: "
                            + operationNames(info));
            MBeanOperationInfo reset = Arrays.stream(info.getOperations())
                    .filter(op -> "resetStats".equals(op.getName())).findFirst()
                    .orElse(null);
            Assertions.assertNotNull(reset, "resetStats() must be invocable, found: "
                    + operationNames(info));
            Assertions.assertEquals("void", reset.getReturnType(),
                    "resetStats() is declared without a return value, as the descriptor states");
        }
    }

    @Test
    void mBeanInfoAdvertisesTheNotificationTypesTheMBeanSends() throws Exception {
        // JMX clients discover what an MBean can send through
        // MBeanInfo.getNotifications() - consoles, monitoring and bridge
        // agents, tooling that builds a NotificationFilterSupport from the
        // advertised types. An MBean that sends its queries as the two wire
        // types of this class but advertises none of them leaves that tooling
        // blind whatever delivery itself does (the pool's own MBean declares
        // its types the same way, see TestJmxNotificationTypeContract).
        String poolName = "JmxNotifInfo-" + System.nanoTime();
        DataSource ds = reportingPoolNamed(poolName);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = objectNameFor(poolName);

        try (@SuppressWarnings("unused") Connection con = ds.getConnection()) {
            Assertions.assertTrue(server.isRegistered(oname),
                    "the premise: the interceptor MBean must be registered");
            MBeanNotificationInfo[] advertisedInfo = server.getMBeanInfo(oname).getNotifications();
            Set<String> advertised = advertisedInfo == null ? new HashSet<>()
                    : Arrays.stream(advertisedInfo).flatMap(i -> Arrays.stream(i.getNotifTypes()))
                            .collect(Collectors.toSet());
            Assertions.assertEquals(new HashSet<>(Arrays.asList(
                            SlowQueryReportJmx.SLOW_QUERY_NOTIFICATION,
                            SlowQueryReportJmx.FAILED_QUERY_NOTIFICATION)), advertised,
                    "the published MBean must advertise exactly the notification types it sends");
        }
    }

    @Test
    void theDescriptorAdvertisesTheWireTypesTheNotificationsCarry() throws Exception {
        // mbeans-descriptors.xml is this MBean's legacy metadata, and like the
        // published MBeanInfo it is material a tool builds its filters from:
        // a notification type that differs from the wire string makes a filter
        // built from the metadata match nothing. The constants are the wire
        // contract (see TestJmxNotificationTypeContract), so the descriptor
        // must carry exactly them, and no half-written descriptions.
        Path descriptor = Paths.get("src", "main", "java", "org", "apache", "tomcat",
                "jdbc", "pool", "interceptor", "mbeans-descriptors.xml");
        String xml = new String(Files.readAllBytes(descriptor), StandardCharsets.UTF_8);

        Set<String> advertised = new HashSet<>();
        Matcher types = Pattern.compile("<notification-type>([^<]+)</notification-type>")
                .matcher(xml);
        while (types.find()) {
            advertised.add(types.group(1));
        }
        Assertions.assertEquals(new HashSet<>(Arrays.asList(
                        SlowQueryReportJmx.SLOW_QUERY_NOTIFICATION,
                        SlowQueryReportJmx.FAILED_QUERY_NOTIFICATION)), advertised,
                "the descriptor must name the notification types by the exact strings the"
                        + " notifications carry on the wire - a client building its filter"
                        + " from the metadata must match what the MBean actually sends");

        Assertions.assertFalse(Pattern.compile("description=\"[^\"]*\\s\"").matcher(xml).find(),
                "no description may dangle half-written on a trailing space");
    }

    @Test
    void poolNameAndPoolNamesAreReadableThroughTheServer() throws Exception {
        String poolName = "JmxPoolName-" + System.nanoTime();
        String otherPoolName = "JmxPoolName-Other-" + System.nanoTime();
        DataSource reporting = reportingPoolNamed(poolName);
        // a second pool under a different name, whose statistics exist but
        // which registers no MBean of its own: PoolNames is documented as
        // the names of all pools holding recorded statistics in this JVM
        DataSource statsOnly = newDataSource();
        statsOnly.getPoolProperties().setName(otherPoolName);
        statsOnly.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport"
                + "(threshold=-1)");

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = objectNameFor(poolName);

        try (@SuppressWarnings("unused") Connection con = reporting.getConnection();
                Connection other = statsOnly.getConnection();
                Statement st = other.createStatement()) {
            st.execute("SELECT 1");

            Assertions.assertEquals(poolName, server.getAttribute(oname, "PoolName"),
                    "the PoolName attribute must report the pool the MBean was registered for");

            Object poolNames = server.getAttribute(oname, "PoolNames");
            Assertions.assertInstanceOf(String[].class, poolNames,
                    "PoolNames must be readable as a String array");
            Set<String> names = new HashSet<>(Arrays.asList((String[]) poolNames));
            Assertions.assertTrue(names.contains(poolName),
                    "PoolNames must contain this MBean's pool, found: " + names);
            Assertions.assertTrue(names.contains(otherPoolName),
                    "PoolNames must contain every pool that holds recorded statistics, found: "
                            + names);
        }
    }

    @Test
    void resetStatsIsInvocableAndClearsTheRecordedStatistics() throws Exception {
        String poolName = "JmxResetStats-" + System.nanoTime();
        DataSource ds = reportingPoolNamed(poolName);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = objectNameFor(poolName);

        try (Connection con = ds.getConnection()) {
            try (Statement st = con.createStatement()) {
                st.execute("SELECT 1");
            }
            Assertions.assertEquals(1, ((CompositeData[]) server.getAttribute(oname,
                            "SlowQueriesCD")).length,
                    "the query must have been recorded before the reset");

            // the documented way for an operator to drop accumulated stats:
            // no pool restart needed
            Assertions.assertNull(server.invoke(oname, "resetStats", null, null),
                    "resetStats() is declared with a void return type");

            Assertions.assertEquals(0, ((CompositeData[]) server.getAttribute(oname,
                            "SlowQueriesCD")).length,
                    "invoking resetStats() must empty the statistics exposed through the MBean");
            Assertions.assertTrue(SlowQueryReport.getPoolStats(poolName).isEmpty(),
                    "invoking resetStats() must clear the pool's statistics map, still holding: "
                            + SlowQueryReport.getPoolStats(poolName).keySet());

            // clearing the contents must not take the statistics map away:
            // the still-running pool keeps reporting into it
            try (Statement st = con.createStatement()) {
                st.execute("SELECT 2");
            }
            Assertions.assertEquals(1, ((CompositeData[]) server.getAttribute(oname,
                            "SlowQueriesCD")).length,
                    "statistics must accumulate again after the reset");
        }
    }

    @Test
    void resetStatsClearsTheOwningPoolSStatistics() throws Exception {
        String poolName = "JmxResetPerPool-" + System.nanoTime();
        DataSource displaced = reportingPoolNamed(poolName);
        // A second live pool under the same name: an unsupported configuration
        // the pool only reaches programmatically. It takes the name over, so
        // the published MBean reports for it and the displaced pool's store is
        // released.
        DataSource owner = reportingPoolNamed(poolName);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = objectNameFor(poolName);

        try (Connection displacedCon = displaced.getConnection()) {
            try (Statement st = displacedCon.createStatement()) {
                st.execute("SELECT 1");
            }
            Assertions.assertTrue(server.isRegistered(oname),
                    "the premise: the first borrow published the MBean of the pool");

            try (Connection ownerCon = owner.getConnection();
                    Statement st = ownerCon.createStatement()) {
                st.execute("SELECT 2");

                CompositeData[] published = (CompositeData[]) server.getAttribute(oname,
                        "SlowQueriesCD");
                Assertions.assertEquals(1, published.length,
                        "the MBean reports the statistics of the pool that now owns the name");
                Assertions.assertEquals("SELECT 2", published[0].get("query"),
                        "and what it reports is that pool's own query");

                Assertions.assertEquals(new HashSet<>(Arrays.asList("SELECT 2")),
                        SlowQueryReport.getPoolStats(poolName).keySet(),
                        "the read by name answers with the pool that owns the name");

                server.invoke(oname, "resetStats", null, null);

                Assertions.assertEquals(0, ((CompositeData[]) server.getAttribute(oname,
                                "SlowQueriesCD")).length,
                        "resetStats() through the published MBean clears the store that"
                                + " registration reports for");
                ConcurrentHashMap<String, SlowQueryReport.QueryStats> cleared =
                        SlowQueryReport.getPoolStats(poolName);
                Assertions.assertNotNull(cleared, "the owning pool must keep its store");
                Assertions.assertFalse(cleared.containsKey("SELECT 2"),
                        "and the reset must clear that store");
                Assertions.assertFalse(cleared.containsKey("SELECT 1"),
                        "the displaced pool's statistics must not be reachable under the name");
            }
        }
    }

    @Test
    void aFailedCompositeTypeDegradesReadsToAnEmptyArray() throws Exception {
        Field typeField = SlowQueryReportJmx.class.getDeclaredField("SLOW_QUERY_TYPE");
        typeField.setAccessible(true);
        Field failedField = SlowQueryReportJmx.class.getDeclaredField("compositeTypeInitializationFailed");
        failedField.setAccessible(true);
        Object savedType = typeField.get(null);
        boolean savedFailed = failedField.getBoolean(null);

        String poolName = "JmxFailedCompositeType-" + System.nanoTime();
        DataSource ds = reportingPoolNamed(poolName);
        try {
            typeField.set(null, null);
            failedField.setBoolean(null, true);
            ds.createPool();

            try (Connection con = ds.getConnection();
                    Statement st = con.createStatement()) {
                st.execute("SELECT 1");
                SlowQueryReportJmx interceptor = interceptorOf(con, SlowQueryReportJmx.class);
                Assertions.assertNotNull(interceptor,
                        "precondition: the pool must run the reporting interceptor");
                Assertions.assertEquals(0, interceptor.getSlowQueriesCD().length,
                        "a failed composite type must degrade to the documented empty array");
            }
        } finally {
            typeField.set(null, savedType);
            failedField.setBoolean(null, savedFailed);
            ds.close();
        }
    }

    @Test
    void slowQueriesCDIsAnEmptyArrayOnceTheStatisticsBehindARegistrationAreReleased()
            throws Exception {
        String poolName = "JmxReleasedStats-" + System.nanoTime();
        DataSource ds = reportingPoolNamed(poolName);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = objectNameFor(poolName);
        try {
            ConnectionPool pool;
            try (Connection con = ds.getConnection();
                    Statement st = con.createStatement()) {
                st.execute("SELECT 1");
            }
            pool = ds.getPool();
            SlowQueryReport.PoolStatistics statistics = SlowQueryReport.statisticsFor(pool);
            Assertions.assertNotNull(statistics,
                    "the premise: the borrow must have registered a store for the pool");
            Assertions.assertEquals(1, ((CompositeData[]) server.getAttribute(oname,
                            "SlowQueriesCD")).length,
                    "the premise: the query must have been recorded while the pool was live");

            // The state a JMX client is left in when the unregistration of a
            // closed pool's MBean was refused and JmxUtil swallowed the
            // failure: the store behind the registration has been released and
            // the registration is still being read. The window of a single
            // close is a thread race no test can force, so the resource of that
            // state is built here and driven through the release directly.
            SlowQueryReportJmx.Registration registration = new SlowQueryReportJmx.Registration(
                    poolName, statistics, SlowQueryReportJmx.jmxStateFor(pool));
            Assertions.assertEquals(1, registration.getSlowQueriesCD().length,
                    "the premise: the registration reads the statistics of its pool");

            SlowQueryReport.releaseStatistics(pool);
            Assertions.assertTrue(statistics.isReleased(),
                    "the premise: the close of the pool gives its store up");

            Object value = registration.getSlowQueriesCD();
            Assertions.assertNotNull(value,
                    "a client that reads the attribute - advertised as "
                            + "[Ljavax.management.openmbean.CompositeData; - through a "
                            + "registration whose pool has come down must never get null in "
                            + "its hands");
            Assertions.assertEquals(0, ((CompositeData[]) value).length,
                    "with the statistics gone the attribute must degrade to the empty "
                            + "array, the way getPoolNames() degrades to an empty String array");
            Assertions.assertFalse(server.isRegistered(oname),
                    "the close also gave the published ObjectName back, so this is the "
                            + "state a refused unregistration leaves, not the normal one");
        } finally {
            ds.close();
        }
    }

    @Test
    void slowQueriesCDIsEmptyArrayForANameThatHoldsNoStatistics() throws Exception {
        // The same missing-map state pinned on the delegate method itself,
        // the way the attribute-race test drives it: an interceptor bound to
        // a name that holds no statistics map - the very state the registered
        // MBean's delegate is in between the release of the name's statistics
        // and its unregistration, and after an unregistration failure.
        SlowQueryReportJmx interceptor = new SlowQueryReportJmx();
        interceptor.poolName = "JmxNoStats-" + System.nanoTime();
        Assertions.assertNull(SlowQueryReport.getPoolStats(interceptor.poolName),
                "the premise: no statistics map may be registered under the name");
        Assertions.assertEquals(0, interceptor.getSlowQueriesCD().length,
                "with no statistics map the read must degrade to the empty array, "
                        + "not to a null a JMX client has to trip over");
    }
}
