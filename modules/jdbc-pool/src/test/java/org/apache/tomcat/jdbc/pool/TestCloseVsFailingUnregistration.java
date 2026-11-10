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
package org.apache.tomcat.jdbc.pool;

import java.lang.management.ManagementFactory;
import java.sql.SQLException;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The release of the pool MBean runs inside the same critical section as the
 * close's flag-and-take of the pool, and inside the deregistration callback.
 * The unregistration is best-effort: the handle is taken from its slot before
 * the unregistration is attempted, so a failure of the unregistration - a
 * subclass of {@code unregisterJmx} that throws, or an {@code Error} surfacing
 * through it - may cost a registered MBean, but must never break the path
 * that called it. Before this fix it did: the exception escaped the close
 * before the terminal flag was set and the pool was taken, so the caller's
 * close silently had not happened - the pool kept handing out connections
 * after being closed - while the handle was already gone, orphaning the very
 * MBean the failed release had failed to unregister.
 */
class TestCloseVsFailingUnregistration extends JdbcPoolBaseTest {

    /* A data source whose next unregistration attempt throws once. */
    private static final class UnreliableDataSource extends DataSource {
        private volatile boolean failNext = false;
        private volatile int releaseAttempts = 0;

        ObjectName heldHandle() {
            return oname;
        }

        void failNextRelease() {
            failNext = true;
        }

        int releaseAttempts() {
            return releaseAttempts;
        }

        @Override
        protected void unregisterJmx(ObjectName name) {
            releaseAttempts++;
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("simulated unregistration failure");
            }
            super.unregisterJmx(name);
        }
    }

    private static MBeanServer server() {
        return ManagementFactory.getPlatformMBeanServer();
    }

    private static ObjectName uniqueName(String role) throws Exception {
        return new ObjectName("tomcat:type=FailingUnregistrationTest,role=" + role
                + ",uid=" + System.nanoTime());
    }

    /* Test hygiene: take away anything a broken build left published for this
       registration - the uid is carried down into every name derived from it. */
    private static void sweep(MBeanServer server, ObjectName uidBearer) throws Exception {
        String uid = uidBearer.getKeyProperty("uid");
        for (ObjectName name : server.queryNames(new ObjectName("tomcat.jdbc:*"), null)) {
            if (uid.equals(name.getKeyProperty("uid"))) {
                server.unregisterMBean(name);
            }
        }
    }

    private UnreliableDataSource newUnreliableDataSource() {
        UnreliableDataSource ds = new UnreliableDataSource();
        ds.setUrl(getUrl());
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.getPoolProperties().setJmxEnabled(true);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        return manage(ds);
    }

    @Test
    void theCloseSurvivesAFailingMBeanReleaseAndStillClosesThePool() throws Exception {
        MBeanServer server = server();
        UnreliableDataSource ds = newUnreliableDataSource();
        ds.createPool();
        ObjectName dsName = uniqueName("close");
        ObjectName poolOname = ds.createObjectName(dsName);
        try {
            ds.preRegister(server, dsName);
            Assertions.assertTrue(server.isRegistered(poolOname),
                    "the registration must have published the pool MBean");

            ds.failNextRelease();
            // The close must ride over the failing release: before the fix the
            // exception escaped, the terminal flag was never set and the pool
            // was never taken, so this call closed nothing.
            ds.close(true);
            Assertions.assertNull(ds.heldHandle(),
                    "the failed release must still have taken the handle - it is "
                            + "what the release paths check, and a kept handle "
                            + "would invite a second release of a name this data "
                            + "source may no longer own");
            Assertions.assertThrows(SQLException.class, ds::getConnection,
                    "a close whose MBean release failed must still have closed "
                            + "the pool: a caller that asked for a close and "
                            + "still gets connections holds a pool nobody can "
                            + "close any more");
        } finally {
            ds.close();
            sweep(server, dsName);
        }
    }

    @Test
    void aFailingReleaseRunsOnceAndLaterPathsDoNotTryAgain() throws Exception {
        MBeanServer server = server();
        UnreliableDataSource ds = newUnreliableDataSource();
        ds.createPool();
        ObjectName dsName = uniqueName("once");
        ObjectName poolOname = ds.createObjectName(dsName);
        try {
            ds.preRegister(server, dsName);
            Assertions.assertTrue(server.isRegistered(poolOname));

            ds.failNextRelease();
            // The deregistration callback must ride over the failing release
            // too, and the handle it clears must stay cleared.
            ds.postDeregister();
            Assertions.assertNull(ds.heldHandle(),
                    "the callback must leave no handle behind even when the "
                            + "unregistration it attempted failed");

            // Every later release path finds the handle gone and attempts
            // nothing: exactly one attempt was made, by the callback.
            ds.close(true);
            Assertions.assertEquals(1, ds.releaseAttempts(),
                    "a failed release must be attempted exactly once: the "
                            + "handle is taken before the unregistration, so no "
                            + "later close or callback may re-attempt a name "
                            + "this data source has given up");
        } finally {
            ds.close();
            sweep(server, dsName);
        }
    }
}
