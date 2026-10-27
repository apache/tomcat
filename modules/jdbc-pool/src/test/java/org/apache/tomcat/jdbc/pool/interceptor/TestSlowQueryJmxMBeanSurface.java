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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
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
 * Statistics are keyed by pool <em>name</em>, so the MBean of one name
 * reads and clears the statistics every live pool of that name shares.
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

        try (Connection con = ds.getConnection()) {
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

        try (Connection con = reporting.getConnection();
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
    void resetStatsClearsTheStatisticsEveryPoolOfTheNameShares() throws Exception {
        String poolName = "JmxResetShared-" + System.nanoTime();
        DataSource owner = reportingPoolNamed(poolName);
        DataSource sharer = reportingPoolNamed(poolName);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = objectNameFor(poolName);

        try (Connection ownerCon = owner.getConnection();
                Connection sharerCon = sharer.getConnection();
                Statement st = sharerCon.createStatement()) {
            st.execute("SELECT 1");

            Assertions.assertEquals(1, ((CompositeData[]) server.getAttribute(oname,
                            "SlowQueriesCD")).length,
                    "the query of the same-named pool must show up in the one registered MBean");

            server.invoke(oname, "resetStats", null, null);

            Assertions.assertTrue(SlowQueryReport.getPoolStats(poolName).isEmpty(),
                    "statistics are keyed by pool name, so resetStats() through that name's MBean "
                            + "clears the map all live pools of the name share, still holding: "
                            + SlowQueryReport.getPoolStats(poolName).keySet());
            Assertions.assertEquals(0, ((CompositeData[]) server.getAttribute(oname,
                            "SlowQueriesCD")).length,
                    "the exposed statistics must be empty for the owning pool as well");
        }
    }
}
