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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.apache.tomcat.jdbc.pool.PooledConnection;

/**
 * Telling the interceptors about the start of the pool and telling them about
 * its close are two halves of one pairing: what a start registers, the close
 * releases. The pool did not keep them paired. It read the interceptor list
 * again when it closed and told whatever the property listed at that moment,
 * from freshly built instances, while the start had told the list as it was
 * when the pool started - and the property is mutable at runtime, through
 * {@code setJdbcInterceptors} on the pool's own MBean as much as in code.
 * <p>
 * A reporting definition removed from the list before the close was therefore
 * never asked to release what its start had registered: the pool name stayed in
 * the statistics registry with its map behind it, and a {@link SlowQueryReportJmx}
 * left its MBean registered on top of that, so the name and everything reachable
 * through the published registration outlived the pool by the life of the JVM.
 * A definition added after the start is still told about the close: it never
 * saw a start to pair with, but a borrow it served may have registered state
 * under the pool's name that only a close takes down.
 * </p>
 */
class TestInterceptorRemovalBeforePoolClose extends JdbcPoolBaseTest {

    /**
     * Records the pool-lifecycle callbacks it receives, under the marker its own
     * definition configures - which is what tells the two definitions of one
     * class apart. The pool builds a fresh instance per definition for each of
     * the two callbacks and configures it from that definition, so reading the
     * marker back out of the instance says which definition the callback was
     * delivered for.
     */
    public static class LifecycleRecordingInterceptor extends JdbcInterceptor {
        static final ConcurrentMap<String, AtomicInteger> STARTS = new ConcurrentHashMap<>();
        static final ConcurrentMap<String, AtomicInteger> CLOSES = new ConcurrentHashMap<>();

        private String marker;

        @Override
        public void setProperties(Map<String, PoolProperties.InterceptorProperty> properties) {
            super.setProperties(properties);
            PoolProperties.InterceptorProperty p = properties.get("marker");
            marker = p == null ? null : p.getValue();
        }

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // nothing to reset
        }

        @Override
        public void poolStarted(ConnectionPool pool) {
            STARTS.computeIfAbsent(String.valueOf(marker), k -> new AtomicInteger()).incrementAndGet();
        }

        @Override
        public void poolClosed(ConnectionPool pool) {
            CLOSES.computeIfAbsent(String.valueOf(marker), k -> new AtomicInteger()).incrementAndGet();
        }

        static void resetCounts() {
            STARTS.clear();
            CLOSES.clear();
        }

        static int starts(String marker) {
            AtomicInteger count = STARTS.get(marker);
            return count == null ? 0 : count.get();
        }

        static int closes(String marker) {
            AtomicInteger count = CLOSES.get(marker);
            return count == null ? 0 : count.get();
        }
    }

    private static final String RECORDING = LifecycleRecordingInterceptor.class.getName();

    private DataSource poolNamed(String name, String interceptorEntry) {
        DataSource ds = newDataSource();
        ds.setName(name);
        ds.setJdbcInterceptors(interceptorEntry);
        ds.getPoolProperties().setInitialSize(0);
        return ds;
    }

    @Test
    void aReportingDefinitionRemovedBeforeTheCloseStillReleasesItsRegistration() throws Exception {
        String poolName = "RemovedBeforeClose-" + System.nanoTime();
        DataSource ds = poolNamed(poolName, SlowQueryReport.class.getName() + "(threshold=0)");
        try {
            try (Connection con = ds.getConnection(); Statement st = con.createStatement()) {
                st.execute("SELECT 1 FROM SYSTEM_RANGE(1,1)");
            }
            Assertions.assertNotNull(SlowQueryReport.getPoolStats(poolName),
                    "the start of the pool must have registered its statistics under its name");

            // The swap the pool's own MBean offers an operator: the reporting
            // definition is gone from the list before the pool comes down
            ds.setJdbcInterceptors("");
            ds.close();

            Assertions.assertNull(SlowQueryReport.getPoolStats(poolName),
                    "a definition that registered statistics at its start must be asked to release"
                            + " them at the close, whatever the interceptor list says by then");
        } finally {
            ds.close();
        }
    }

    @Test
    void aJmxDefinitionRemovedBeforeTheCloseStillReleasesItsMBean() throws Exception {
        String poolName = "RemovedJmxBeforeClose-" + System.nanoTime();
        DataSource ds = poolNamed(poolName,
                SlowQueryReportJmx.class.getName() + "(notifyPool=false,threshold=0)");
        ObjectName oname = new ObjectName("tomcat.jdbc:type=" + SlowQueryReportJmx.class.getName()
                + ",name=" + poolName);
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        try {
            try (Connection con = ds.getConnection(); Statement st = con.createStatement()) {
                st.execute("SELECT 1 FROM SYSTEM_RANGE(1,1)");
            }
            Assertions.assertTrue(server.isRegistered(oname),
                    "the borrow must have published the MBean for the pool name");

            ds.setJdbcInterceptors("");
            ds.close();

            Assertions.assertFalse(server.isRegistered(oname),
                    "a definition that published an MBean at its start must take it down at the"
                            + " close, whatever the interceptor list says by then");
            Assertions.assertFalse(SlowQueryReportJmx.mbeans.containsKey(poolName),
                    "and the registry of published names must not keep the name either");
            Assertions.assertNull(SlowQueryReport.getPoolStats(poolName),
                    "nor may the statistics it registered under that name");
        } finally {
            ds.close();
        }
    }

    @Test
    void theDefinitionThatSawTheStartIsTheOneToldAboutTheClose() throws Exception {
        LifecycleRecordingInterceptor.resetCounts();
        DataSource ds = poolNamed("LifecyclePairing-" + System.nanoTime(),
                RECORDING + "(marker=started)");
        try {
            ds.createPool();
            // The list the pool now reads back at close: the definition that saw
            // the start is gone and a new one, which never did, is in its place
            ds.setJdbcInterceptors(RECORDING + "(marker=added)");
            ds.close();

            Assertions.assertEquals(1, LifecycleRecordingInterceptor.starts("started"),
                    "the definition configured at the start is told about it once");
            Assertions.assertEquals(1, LifecycleRecordingInterceptor.closes("started"),
                    "and exactly that definition is told about the close: releasing a start is the"
                            + " business of the definition that performed it, not of whatever the"
                            + " property lists when the pool comes down");
            Assertions.assertEquals(0, LifecycleRecordingInterceptor.starts("added"),
                    "a definition the pool never started must not be credited with a start");
            Assertions.assertEquals(1, LifecycleRecordingInterceptor.closes("added"),
                    "and a definition added after the start is told about the close as well: it has"
                            + " no start to release, but a borrow it served may have registered state"
                            + " under the pool's name that only a close takes down");
        } finally {
            ds.close();
        }
    }
}
