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
 * notification broadcaster for the pool it reports on, and gives them up
 * when that pool closes. Pool closure delivers the close to one freshly
 * instantiated interceptor per configured definition, in declaration
 * order, and the release hangs from the pool's statistics store rather
 * than from a signal one definition hands to the next: whichever
 * definition is asked to release the store gives up the JMX state
 * published for it, so the order the definitions come down in - and a
 * base {@link SlowQueryReport} definition above the JMX one - cannot
 * strand the registration of a pool that is already gone.
 *
 * <p>Reproducer for the release-signal ordering defect of
 * {@code defects-reports/defects-org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport.md},
 * which the per-pool release replaced.</p>
 */
class TestSlowQueryJmxChainCloseRelease extends JdbcPoolBaseTest {

    private static final String SQR =
            "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport";
    private static final String SQRJ =
            "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx";

    /* A pool of the given name with the given chain, JMX off for the pool
     * itself so the test only observes the interceptor's own publication. */
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
    void closingOneChainedPoolReleasesOnlyItsOwnRegistration() throws Exception {
        // The publication belongs to the pool that made it: the close of that
        // pool takes it down while another, differently named pool is still
        // running, and that other pool keeps its own statistics either way
        String publisherName = "chain-publisher-" + System.nanoTime();
        String survivorName = "chain-survivor-" + System.nanoTime();
        String chain = SQR + ";" + SQRJ + "(notifyPool=false)";
        DataSource publisher = poolNamed(publisherName, chain);
        DataSource survivor = poolNamed(survivorName, chain);
        publisher.createPool();
        survivor.createPool();
        run(publisher, "SELECT 1");
        run(survivor, "SELECT 2");

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = interceptorName(publisherName);
        Assertions.assertTrue(server.isRegistered(oname),
                "the premise: the first pool's borrow published the MBean");

        publisher.close();

        Assertions.assertFalse(server.isRegistered(oname),
                "the close of the pool that published the MBean takes it down, whatever"
                        + " position the JMX definition has in its chain");
        Assertions.assertNotNull(SlowQueryReport.getPoolStats(survivorName),
                "and the other pool's statistics are untouched by it");

        survivor.close();

        Assertions.assertNull(SlowQueryReport.getPoolStats(survivorName),
                "its own close releases the statistics it registered under the name");
    }
}
