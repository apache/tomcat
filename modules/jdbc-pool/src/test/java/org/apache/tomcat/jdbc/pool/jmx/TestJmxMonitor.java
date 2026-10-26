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
import java.sql.Connection;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * Expected JMX monitoring functionality. A container registers the
 * DataSource as an MBean, which drives the {@code MBeanRegistration}
 * callbacks {@code preRegister} and {@code postDeregister}; those publish
 * the pool's own MBean under the {@code tomcat.jdbc} domain, and each
 * physical connection publishes itself below that name. The tests invoke
 * those same container callbacks and then read the published statistics
 * while real traffic flows to a real database.
 */
class TestJmxMonitor extends JdbcPoolBaseTest {

    private static final String POOL_DOMAIN = "tomcat.jdbc";

    private static long attr(MBeanServer server, ObjectName name, String attribute)
            throws Exception {
        return ((Number) server.getAttribute(name, attribute)).longValue();
    }

    private static ObjectName uniqueDataSourceName(String role)
            throws MalformedObjectNameException {
        return new ObjectName("tomcat:type=FunctionalTestDataSource,role=" + role
                + ",uid=" + System.nanoTime());
    }

    private static Set<ObjectName> poolMBeans(MBeanServer server) throws Exception {
        return server.queryNames(new ObjectName(POOL_DOMAIN + ":*"), null);
    }

    /**
     * Publishes an already created pool the way a container does and
     * returns the single pool MBean that appeared, or {@code null} if
     * nothing was published.
     */
    private ObjectName registerAndFindPoolMBean(MBeanServer server, DataSource ds,
            ObjectName dsName) throws Exception {
        Set<ObjectName> before = poolMBeans(server);
        ds.preRegister(server, dsName);
        Set<ObjectName> added = new HashSet<>(poolMBeans(server));
        added.removeAll(before);
        return added.isEmpty() ? null : added.iterator().next();
    }

    /** Counts the connection MBeans published below the given pool MBean. */
    private static int connectionMBeanCount(MBeanServer server, ObjectName poolOname)
            throws Exception {
        // the connection MBeans repeat the pool's key properties and add
        // "connections=PooledConnection[n]", so match on those keys rather
        // than on a string prefix (canonical names sort the properties)
        String uid = poolOname.getKeyProperty("uid");
        int count = 0;
        for (ObjectName name : poolMBeans(server)) {
            if (name.getKeyProperty("connections") != null
                    && uid.equals(name.getKeyProperty("uid"))) {
                count++;
            }
        }
        return count;
    }

    @Test
    void poolMBeanTracksLiveTraffic() throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJmxEnabled(true);
        ds.createPool();

        ObjectName dsName = uniqueDataSourceName("live");
        ObjectName poolOname = registerAndFindPoolMBean(server, ds, dsName);
        try {
            Assertions.assertNotNull(poolOname, "an enabled pool must publish its MBean");
            Assertions.assertEquals(POOL_DOMAIN, poolOname.getDomain(),
                    "the pool MBean must be published under the tomcat.jdbc domain");
            Assertions.assertEquals("ConnectionPool", poolOname.getKeyProperty("type"));
            Assertions.assertEquals(DataSource.class.getName(), poolOname.getKeyProperty("class"));

            // the fixture pre-creates one physical connection
            Assertions.assertEquals(0, attr(server, poolOname, "NumActive"));
            Assertions.assertEquals(1, attr(server, poolOname, "NumIdle"));

            Connection borrowed = ds.getConnection();
            Assertions.assertEquals(1, attr(server, poolOname, "NumActive"),
                    "NumActive must reflect the borrowed connection");
            Assertions.assertEquals(0, attr(server, poolOname, "NumIdle"));
            borrowed.close();
            Assertions.assertEquals(0, attr(server, poolOname, "NumActive"));
            Assertions.assertEquals(1, attr(server, poolOname, "NumIdle"),
                    "NumIdle must reflect the returned connection");

            Assertions.assertTrue(attr(server, poolOname, "BorrowedCount") >= 1,
                    "BorrowedCount must count borrows");
            Assertions.assertTrue(attr(server, poolOname, "ReturnedCount") >= 1,
                    "ReturnedCount must count returns");
        } finally {
            ds.postDeregister();
            Assertions.assertFalse(server.isRegistered(poolOname),
                    "postDeregister must unregister the pool MBean");
        }
    }

    @Test
    void connectionMBeansFollowThePhysicalConnections() throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJmxEnabled(true);
        ds.getPoolProperties().setMaxActive(2);
        ds.createPool();

        ObjectName dsName = uniqueDataSourceName("connections");
        Set<ObjectName> before = poolMBeans(server);
        ObjectName poolOname = registerAndFindPoolMBean(server, ds, dsName);
        try {
            Assertions.assertNotNull(poolOname);
            Connection first = ds.getConnection();
            Connection second = ds.getConnection();
            Assertions.assertEquals(2, connectionMBeanCount(server, poolOname),
                    "every physical connection in use must be published below the pool");

            first.close();
            second.close();
            Assertions.assertEquals(2, connectionMBeanCount(server, poolOname),
                    "returned connections stay published, they are still pooled");
        } finally {
            ds.close(); // releases the physical connections
            ds.postDeregister();
            Set<ObjectName> leftovers = new HashSet<>(poolMBeans(server));
            leftovers.removeAll(before);
            Assertions.assertTrue(leftovers.isEmpty(),
                    "closing the pool must unregister every MBean it published, left: "
                            + leftovers);
        }
    }

    @Test
    void theJmxViewNeverExposesThePassword() throws Exception {
        final String secret = "s3cr3t-pool-password";
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJmxEnabled(true);
        ds.createPool();
        // the pool is already connected; this only records what a real
        // configuration holds
        ds.getPoolProperties().setPassword(secret);

        ObjectName dsName = uniqueDataSourceName("credentials");
        ObjectName poolOname = registerAndFindPoolMBean(server, ds, dsName);
        try {
            Assertions.assertNotNull(poolOname, "an enabled pool must publish its MBean");
            ConnectionPoolMBean mbean =
                    JMX.newMBeanProxy(server, poolOname, ConnectionPoolMBean.class);

            Assertions.assertEquals(secret, ds.getPoolProperties().getPassword(),
                    "the pool itself must still hold the configured password");
            Assertions.assertFalse(String.valueOf(mbean.getPassword()).contains(secret),
                    "the MBean must not hand out the password");

            Properties dbProperties = mbean.getDbProperties();
            Assertions.assertNotNull(dbProperties);
            for (String name : dbProperties.stringPropertyNames()) {
                Assertions.assertFalse(secret.equals(dbProperties.getProperty(name)),
                        "the JMX view of the driver properties must not carry the"
                                + " password, property " + name);
            }
        } finally {
            ds.postDeregister();
        }
    }

    @Test
    void monitoredConnectionsStayFullyFunctional() throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJmxEnabled(true);
        ds.createPool();

        ObjectName dsName = uniqueDataSourceName("monitor");
        ObjectName poolOname = registerAndFindPoolMBean(server, ds, dsName);
        String table = uniqueTable();
        try {
            Assertions.assertNotNull(poolOname, "an enabled pool must publish its MBean");
            try (Connection c = ds.getConnection()) {
                Assertions.assertEquals("H2", c.getMetaData().getDatabaseProductName(),
                        "monitoring must not interfere with the connection");
                c.createStatement().execute("CREATE TABLE " + table + " (v INT)");
                Assertions.assertEquals(1, c.createStatement().executeUpdate("INSERT INTO " + table
                        + " VALUES (1)"));
            }
            Assertions.assertEquals(0, attr(server, poolOname, "NumActive"));
            Assertions.assertEquals(1, adminRowCount(table),
                    "work done on a monitored connection must reach the database");
        } finally {
            ds.postDeregister();
        }
    }

    @Test
    void disabledJmxPublishesNothing() throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        DataSource ds = newDataSource(); // the fixture disables JMX
        ds.createPool();

        ObjectName dsName = uniqueDataSourceName("disabled");
        Set<ObjectName> before = poolMBeans(server);
        ds.preRegister(server, dsName);
        try {
            Set<ObjectName> added = new HashSet<>(poolMBeans(server));
            added.removeAll(before);
            Assertions.assertTrue(added.isEmpty(),
                    "a JMX-disabled pool must not publish MBeans, added: " + added);
        } finally {
            ds.postDeregister();
        }
    }
}
