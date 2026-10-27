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
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * The {@code CacheSize} attribute of the {@code StatementCache}
 * interceptor MBean as a JMX client sees it. The count it reports comes
 * from the counter the whole pool shares to enforce the {@code max} limit,
 * and the attribute is the only place where that internal state crosses
 * the management boundary. A monitoring read must therefore yield a
 * number: a client that reads it from inside the JVM and ends up holding
 * the counter itself can write through it and permanently skew the
 * accounting of the pool it only came to look at. A number is also what a
 * client limited to JMX open types needs in order to represent the value.
 * <p>
 * The interceptor publishes one MBean per pooled connection, below the
 * pool's own MBean, so these tests drive the registration the way a
 * container does: publish the DataSource, then let the pool hand out a
 * connection.
 */
class TestStatementCacheJmxCacheSize extends JdbcPoolBaseTest {

    private static ObjectName uniqueDataSourceName() throws Exception {
        return new ObjectName("tomcat:type=StatementCacheTestDataSource,uid="
                + System.nanoTime());
    }

    private static Set<ObjectName> poolMBeans(MBeanServer server) throws Exception {
        return server.queryNames(new ObjectName("tomcat.jdbc:*"), null);
    }

    /* A pool that caches prepared statements and publishes its MBeans. */
    private DataSource cachingPool(String properties) throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJmxEnabled(true);
        ds.setJdbcInterceptors(StatementCache.class.getName() + properties);
        ds.getPoolProperties().setInitialSize(0);
        ds.createPool();
        return ds;
    }

    /*
     * The interceptor MBeans that appeared since the given snapshot - one
     * for each pooled connection that has been handed out meanwhile.
     */
    private static Set<ObjectName> interceptorMBeans(MBeanServer server,
            Set<ObjectName> before) throws Exception {
        Set<ObjectName> added = new HashSet<>(poolMBeans(server));
        added.removeAll(before);
        added.removeIf(name -> name.getKeyProperty("JdbcInterceptor") == null);
        return added;
    }

    @Test
    void theCacheSizeAttributeIsADescribedNumber() throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        DataSource ds = cachingPool("(prepared=true,max=5)");
        Set<ObjectName> before = poolMBeans(server);
        ds.preRegister(server, uniqueDataSourceName());

        try (Connection con = ds.getConnection()) {
            Set<ObjectName> interceptors = interceptorMBeans(server, before);
            Assertions.assertEquals(1, interceptors.size(),
                    "the pooled connection must have published one interceptor MBean, found: "
                            + interceptors);
            ObjectName oname = interceptors.iterator().next();

            MBeanInfo info = server.getMBeanInfo(oname);
            MBeanAttributeInfo cacheSize = null;
            for (MBeanAttributeInfo attribute : info.getAttributes()) {
                if ("CacheSize".equals(attribute.getName())) {
                    cacheSize = attribute;
                }
            }
            Assertions.assertNotNull(cacheSize, "CacheSize must stay an attribute of the "
                    + "interceptor MBean, which exposes "
                    + info.getAttributes().length + " attributes");
            Assertions.assertEquals(int.class.getName(), cacheSize.getType(),
                    "the attribute must be described as a number, so that a client limited to "
                            + "JMX open types can represent it, but its type is: "
                            + cacheSize.getType());
            Assertions.assertFalse(cacheSize.isWritable(),
                    "the pool's cache accounting is not something a management client configures");

            PreparedStatement cached = con.prepareStatement("SELECT 1");
            cached.close();

            Object read = server.getAttribute(oname, "CacheSize");
            Assertions.assertInstanceOf(Integer.class, read,
                    "a monitoring read must yield the number of cached statements rather than "
                            + "an object the client can change");
            Assertions.assertEquals(1, read,
                    "the statement that has just been closed is cached and must be counted");
        } finally {
            ds.close();
            ds.postDeregister();
        }
    }

    @Test
    void readingCacheSizeCannotChangeWhatThePoolCaches() throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        DataSource ds = cachingPool("(prepared=true,max=2)");
        Set<ObjectName> before = poolMBeans(server);
        ds.preRegister(server, uniqueDataSourceName());

        try (Connection con = ds.getConnection()) {
            StatementCache cache = interceptorOf(con, StatementCache.class);
            ObjectName oname = interceptorMBeans(server, before).iterator().next();

            con.prepareStatement("SELECT 1").close();
            Assertions.assertEquals(1, cache.getCacheSizePerConnection(),
                    "the first statement must have been cached");

            // Inflate: a client that came for a number, but which ends up
            // holding the pool's own counter, writes to what it read.
            Object inflated = server.getAttribute(oname, "CacheSize");
            if (inflated instanceof AtomicInteger) {
                ((AtomicInteger) inflated).set(1000);
            }
            PreparedStatement second = con.prepareStatement("SELECT 2");
            Statement physicalSecond = driverStatement(second);
            second.close();
            Assertions.assertFalse(physicalSecond.isClosed(),
                    "reading the attribute must not stop the pool caching: a count inflated past "
                            + "max closes every further statement instead of recycling it");
            Assertions.assertEquals(2, cache.getCacheSizePerConnection(),
                    "the second statement must still have reached the cache");

            // Deflate: the same client writing downwards must not lift the
            // limit either - that would let the cache grow past max.
            Object deflated = server.getAttribute(oname, "CacheSize");
            if (deflated instanceof AtomicInteger) {
                ((AtomicInteger) deflated).set(-1000);
            }
            con.prepareStatement("SELECT 3").close();
            con.prepareStatement("SELECT 4").close();
            Assertions.assertEquals(2, cache.getCacheSizePerConnection(),
                    "reading the attribute must not let the cache grow past max=2, it holds: "
                            + cache.getCacheSizePerConnection());
            Assertions.assertEquals(2,
                    ((Number) server.getAttribute(oname, "CacheSize")).intValue(),
                    "the count the attribute reports must stay the one the limit is enforced with");
        } finally {
            ds.close();
            ds.postDeregister();
        }
    }

    @Test
    void anInterceptorBoundToNoConnectionReportsMinusOne() throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = new ObjectName("tomcat:type=UnboundStatementCache,uid="
                + System.nanoTime());
        StatementCache unbound = new StatementCache();
        server.registerMBean(unbound, oname);
        try {
            // This instance has never been given a pooled connection, so
            // there is no pool whose accounting it could report; 0 would be
            // indistinguishable from an empty cache.
            Assertions.assertEquals(-1, server.getAttribute(oname, "CacheSize"),
                    "an interceptor that is not bound to a pooled connection must report the "
                            + "documented sentinel instead of a value that looks like a count");
        } finally {
            server.unregisterMBean(oname);
        }
    }
}
