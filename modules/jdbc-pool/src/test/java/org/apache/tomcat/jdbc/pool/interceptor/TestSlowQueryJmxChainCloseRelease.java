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
import java.sql.SQLException;
import java.sql.Statement;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * The {@code SlowQueryReportJmx} interceptor publishes its MBean and
 * notification broadcaster for the pool <em>name</em> and must take them
 * down once the last live pool of that name closes. Pool closure delivers
 * the close to one freshly instantiated interceptor per configured
 * definition, in declaration order, and the statistics release hands its
 * "last pool of the name closed" signal to whichever definition runs
 * first - which a base {@link SlowQueryReport} definition above the JMX
 * one consumes before the definition that owns the JMX state ever sees it.
 * The JMX definition must therefore read the name's freed statistics as
 * the same signal, or the registration of a pool that is already gone
 * stays published forever, pinning the dead delegate and blocking a later
 * same-named pool from registering.
 *
 * <p>Reproducer for the release-signal ordering defect of
 * {@code defects-reports/defects-org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport.md}.</p>
 */
class TestSlowQueryJmxChainCloseRelease extends JdbcPoolBaseTest {

    private static final String SQR =
            "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport";
    private static final String SQRJ =
            "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx";

    /* A pool of the given name with the given chain, JMX off for the pool
     * itself so same-named pools dodge the pool MBean ObjectName clash. */
    private DataSource poolNamed(String name, String interceptors) {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setName(name);
        ds.getPoolProperties().setJdbcInterceptors(interceptors);
        ds.getPoolProperties().setInitialSize(0);
        return ds;
    }

    private static void run(DataSource ds, String sql) throws SQLException {
        try (Connection pooled = ds.getConnection();
                Statement st = pooled.createStatement()) {
            st.execute(sql);
        }
    }

    private static ObjectName interceptorName(String poolName) throws Exception {
        return new ObjectName("tomcat.jdbc:type=" + SlowQueryReportJmx.class.getName()
                + ",name=" + poolName);
    }

    @Test
    void closingTheOnlyPoolReleasesJmxBehindABaseReportDefinition() throws Exception {
        // The order that used to leak: the base definition runs first on
        // close, consumes the last-pool-of-name signal and the JMX
        // definition below it must still release from the freed statistics
        String poolName = "chain-base-first-" + System.nanoTime();
        DataSource ds = poolNamed(poolName, SQR + ";" + SQRJ + "(notifyPool=false)");
        ds.createPool();
        run(ds, "SELECT 1");

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = interceptorName(poolName);
        Assertions.assertTrue(server.isRegistered(oname),
                "the interceptor MBean must be registered once the pool is in use");

        ds.close();

        Assertions.assertFalse(server.isRegistered(oname),
                "closing the only pool of the name must take the interceptor MBean down,"
                        + " whatever position the JMX definition has in the chain");
    }

    @Test
    void closingTheOnlyPoolReleasesJmxAheadOfABaseReportDefinition() throws Exception {
        // The reverse order always worked: pin it so a fix for the other
        // order cannot move the release off the signalled path
        String poolName = "chain-jmx-first-" + System.nanoTime();
        DataSource ds = poolNamed(poolName, SQRJ + "(notifyPool=false);" + SQR);
        ds.createPool();
        run(ds, "SELECT 1");

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = interceptorName(poolName);
        Assertions.assertTrue(server.isRegistered(oname),
                "the interceptor MBean must be registered once the pool is in use");

        ds.close();

        Assertions.assertFalse(server.isRegistered(oname),
                "closing the only pool of the name must take the interceptor MBean down");
    }

    @Test
    void closingOneOfTwoSameNamedChainedPoolsKeepsTheRegistration() throws Exception {
        // The statistics-gone release must not fire while a same-named
        // survivor still runs: the registration belongs to the name
        String poolName = "chain-survivors-" + System.nanoTime();
        String chain = SQR + ";" + SQRJ + "(notifyPool=false)";
        DataSource first = poolNamed(poolName, chain);
        DataSource second = poolNamed(poolName, chain);
        first.createPool();
        second.createPool();
        run(first, "SELECT 1");
        run(second, "SELECT 2");

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = interceptorName(poolName);

        first.close();

        Assertions.assertTrue(server.isRegistered(oname),
                "closing one of two live pools of the same name must keep the shared"
                        + " registration for the survivor");
        Assertions.assertNotNull(SlowQueryReport.getPoolStats(poolName),
                "and the statistics of the name");

        second.close();

        Assertions.assertFalse(server.isRegistered(oname),
                "closing the last pool of the name must take the registration down");
        Assertions.assertNull(SlowQueryReport.getPoolStats(poolName),
                "and the statistics with it");
    }
}
