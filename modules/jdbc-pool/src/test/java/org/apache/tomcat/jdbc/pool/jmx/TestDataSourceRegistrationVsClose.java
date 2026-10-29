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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * The {@code DataSource}'s JMX lifecycle - {@code preRegister()},
 * {@code postRegister()}, {@code postDeregister()} and the publication and
 * release they perform - runs under the same monitor as {@code close(boolean)}
 * and pool creation, so closure and registration serialize against each other.
 * <p>
 * Unsynchronized, a {@code close()} could run completely between the
 * publication's read of the registration handle and the publish the MBean
 * server performs from it: the close's own unregister then found nothing to
 * take back (only an error log), the handle was cleared, and the pool was
 * closed - and the still-running publish registered the dead pool's MBean
 * under a live name afterwards. Since {@code close()}, {@code
 * postDeregister()} and {@code postRegister()} only ever release a handle this
 * data source holds, and the close had just cleared it, that registration had
 * nothing left to ever unregister it: statistics and notifications of a dead
 * pool under a live name, pinning the pool, its credentials and its JDBC
 * driver class loader - the exact outcome {@code close()}'s javadoc promises
 * to prevent. The milder sibling is a {@code close()} and a container's
 * {@code postDeregister()} both passing the handle's null check at once and
 * unregistering the same name twice, which in a narrow window can also take
 * back a name a third component registered in between.</p>
 * <p>
 * The tests drive the interleaving deterministically by parking one lifecycle
 * path inside its JMX seam - the seam only ever runs under the data source
 * monitor once the lifecycle methods are synchronized - and observing that the
 * other path does not make progress meanwhile. The first test additionally
 * records lock ownership directly and checks from a third thread that the
 * MBean server holds no lock across the registration callback it is executing,
 * which is what makes holding the data source monitor across the server's own
 * register/unregister calls deadlock-free.</p>
 */
class TestDataSourceRegistrationVsClose extends JdbcPoolBaseTest {

    private static MBeanServer server() {
        return ManagementFactory.getPlatformMBeanServer();
    }

    /* A name no other test of the suite uses, so nothing collides by accident. */
    private static ObjectName uniqueName(String role) throws Exception {
        return new ObjectName("tomcat:type=RegistrationVsCloseTest,role=" + role
                + ",uid=" + System.nanoTime());
    }

    /*
     * A data source whose two JMX seams can be armed for one pass each: both
     * always record whether they run under the data source monitor - the lock
     * close() takes - and an armed override additionally signals that it has
     * been entered and waits on a gate the test opens. Gates start open, so
     * only the armed pass is parked and the rest of the lifecycle runs
     * unhooked.
     */
    private static final class SeamDataSource extends DataSource {

        private final AtomicInteger releases = new AtomicInteger();
        private volatile CountDownLatch publicationEntered;
        private volatile CountDownLatch publicationGate = new CountDownLatch(0);
        private volatile boolean publicationUnderMonitor;
        private volatile CountDownLatch releaseEntered;
        private volatile CountDownLatch releaseGate = new CountDownLatch(0);
        private volatile boolean releaseUnderMonitor;

        /* The registration handle this data source currently holds. */
        ObjectName heldHandle() {
            return oname;
        }

        @Override
        protected void registerJmx() {
            publicationUnderMonitor = Thread.holdsLock(this);
            CountDownLatch entered = publicationEntered;
            if (entered != null) {
                publicationEntered = null;
                entered.countDown();
                awaitGate(publicationGate);
            }
            super.registerJmx();
        }

        @Override
        protected void unregisterJmx(ObjectName name) {
            releases.incrementAndGet();
            releaseUnderMonitor = Thread.holdsLock(this);
            CountDownLatch entered = releaseEntered;
            if (entered != null) {
                releaseEntered = null;
                entered.countDown();
                awaitGate(releaseGate);
            }
            super.unregisterJmx(name);
        }

        private static void awaitGate(CountDownLatch gate) {
            try {
                gate.await();
            } catch (InterruptedException x) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(x);
            }
        }
    }

    private SeamDataSource newSeamDataSource() {
        SeamDataSource ds = new SeamDataSource();
        ds.setUrl(getUrl());
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.getPoolProperties().setJmxEnabled(true);
        // No connections: the pool's own publication is the only MBean these
        // tests drive, so no connection MBeans cloud the accounting.
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        return manage(ds);
    }

    /* Test hygiene: take away anything a broken build left published for this
       registration - the uid is carried down into every name derived from it. */
    private static void sweep(MBeanServer server, ObjectName dsName) throws Exception {
        String uid = dsName.getKeyProperty("uid");
        for (ObjectName name : server.queryNames(new ObjectName("tomcat.jdbc:*"), null)) {
            if (uid.equals(name.getKeyProperty("uid"))) {
                server.unregisterMBean(name);
            }
        }
    }

    @Test
    void aCloseCannotOvertakeAnInFlightPoolPublicationAndLeavesNothingRegistered()
            throws Exception {
        MBeanServer server = server();
        SeamDataSource ds = newSeamDataSource();
        ds.createPool();
        ObjectName dsName = uniqueName("register-vs-close");
        ObjectName poolOname = ds.createObjectName(dsName);

        ds.publicationEntered = new CountDownLatch(1);
        ds.publicationGate = new CountDownLatch(1);
        AtomicReference<Throwable> registrationFailure = new AtomicReference<>();
        Thread registrar = new Thread(() -> {
            try {
                server.registerMBean(new TestJmxRegistration.Container(ds), dsName);
            } catch (Throwable x) {
                registrationFailure.set(x);
            }
        }, "registration racing close");
        registrar.setDaemon(true);
        Thread closer = null;
        try {
            registrar.start();
            Assertions.assertTrue(ds.publicationEntered.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                    "the container's registration must have reached the pool publication");

            // Direct evidence of the fix's mechanism: the publication runs
            // under the same monitor a close takes, so the two cannot be in
            // flight at once.
            Assertions.assertTrue(ds.publicationUnderMonitor,
                    "the pool publication must run under the data source monitor that close() "
                            + "takes: unsynchronized, a close could run completely between the "
                            + "publication's read of the handle and the publish, unregistering a "
                            + "name the pool never took and closing the pool under it");

            // The premise that makes holding that monitor across the server's
            // own registration calls deadlock-free: the server is inside a
            // registration callback right now, and must place no lock that
            // keeps other components' registrations and deregistrations from
            // completing meanwhile. Run on its own thread with a bound, so a
            // server that serialized on its callbacks would fail this test
            // rather than hang the suite.
            AtomicReference<Throwable> probeFailure = new AtomicReference<>();
            AtomicBoolean probeDone = new AtomicBoolean();
            ObjectName probeName = uniqueName("probe");
            Thread probe = new Thread(() -> {
                try {
                    server.registerMBean(new TestJmxRegistration.Probe("probe"), probeName);
                    server.unregisterMBean(probeName);
                } catch (Throwable x) {
                    probeFailure.set(x);
                } finally {
                    probeDone.set(true);
                }
            }, "server callback probe");
            probe.setDaemon(true);
            probe.start();
            probe.join(AWAIT_TIMEOUT_MS);
            Assertions.assertTrue(probeDone.get(),
                    "the MBean server must not hold a lock across the registration callback it "
                            + "is executing: a lifecycle path of this data source may hold its "
                            + "own monitor across a server call, and if the server then kept "
                            + "other callers out, the two would deadlock");
            Assertions.assertNull(probeFailure.get(), "the probe registration must have worked");

            // The interleaving the fix rules out: while the publication is in
            // flight, the close must wait for it instead of slipping its
            // (then-useless) unregister and the pool's teardown in ahead.
            AtomicReference<Throwable> closeFailure = new AtomicReference<>();
            closer = new Thread(() -> {
                try {
                    ds.close(true);
                } catch (Throwable x) {
                    closeFailure.set(x);
                }
            }, "close racing publication");
            closer.setDaemon(true);
            closer.start();
            closer.join(500);
            Assertions.assertTrue(closer.isAlive(),
                    "a close concurrent with the pool publication must serialize against it "
                            + "instead of completing around it");

            ds.publicationGate.countDown();
            registrar.join(AWAIT_TIMEOUT_MS);
            closer.join(AWAIT_TIMEOUT_MS);
            Assertions.assertFalse(registrar.isAlive(), "the registration must complete");
            Assertions.assertFalse(closer.isAlive(), "the close must complete once released");
            Assertions.assertNull(registrationFailure.get(), "the registration must not fail");
            Assertions.assertNull(closeFailure.get(), "the close must not fail");

            // The outcome close() promises: the publication the close waited
            // for is taken back by that same close - exactly once, under the
            // monitor - and nothing of the dead pool stays registered.
            Assertions.assertTrue(ds.releaseUnderMonitor,
                    "the release close() takes must run under the data source monitor too");
            Assertions.assertEquals(1, ds.releases.get(),
                    "the handle is checked, cleared and released as one step under the monitor");
            Assertions.assertFalse(server.isRegistered(poolOname),
                    "the pool MBean the in-flight publication published must not outlive the "
                            + "close: with the handle already cleared, no later close() or "
                            + "postDeregister() would ever release a stale registration, and it "
                            + "would pin the closed pool, its credentials and its driver class "
                            + "loader in the MBean server");
            Assertions.assertNull(ds.heldHandle(), "the close must leave no handle behind");

            // The container still holds this DataSource's own registration;
            // releasing it must find nothing left to release.
            server.unregisterMBean(dsName);
            Assertions.assertEquals(1, ds.releases.get(),
                    "the deregistration after the close must not release again");
        } finally {
            // Never leave a lifecycle thread parked inside the monitor: the
            // base class teardown (ds.close()) would block on it forever.
            ds.publicationGate.countDown();
            ds.releaseGate.countDown();
            registrar.join(AWAIT_TIMEOUT_MS);
            if (closer != null) {
                closer.join(AWAIT_TIMEOUT_MS);
            }
            if (server.isRegistered(dsName)) {
                server.unregisterMBean(dsName);
            }
            ds.close();
            sweep(server, dsName);
        }
    }

    @Test
    void aDeregistrationAndACloseReleaseThePoolMBeanExactlyOnce() throws Exception {
        MBeanServer server = server();
        SeamDataSource ds = newSeamDataSource();
        ds.createPool();
        ObjectName dsName = uniqueName("dereg-vs-close");
        ObjectName poolOname = ds.createObjectName(dsName);

        // The state a shutdown starts from: a published pool under an adopted
        // container.
        server.registerMBean(new TestJmxRegistration.Container(ds), dsName);
        Assertions.assertTrue(server.isRegistered(poolOname));
        Assertions.assertEquals(poolOname, ds.heldHandle());

        ds.releaseEntered = new CountDownLatch(1);
        ds.releaseGate = new CountDownLatch(1);
        AtomicReference<Throwable> deregistrationFailure = new AtomicReference<>();
        Thread deregistrar = new Thread(() -> {
            try {
                server.unregisterMBean(dsName);
            } catch (Throwable x) {
                deregistrationFailure.set(x);
            }
        }, "deregistration racing close");
        deregistrar.setDaemon(true);
        Thread closer = null;
        try {
            deregistrar.start();
            Assertions.assertTrue(ds.releaseEntered.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                    "the container's deregistration must have reached the pool MBean release");
            Assertions.assertTrue(ds.releaseUnderMonitor,
                    "the release the container's postDeregister() performs must run under the "
                            + "data source monitor close() takes: unsynchronized, the two can "
                            + "pass the handle's null check side by side and unregister the same "
                            + "name twice - in a narrow window taking back a name a third "
                            + "component published in between");

            AtomicReference<Throwable> closeFailure = new AtomicReference<>();
            closer = new Thread(() -> {
                try {
                    ds.close(true);
                } catch (Throwable x) {
                    closeFailure.set(x);
                }
            }, "close racing deregistration");
            closer.setDaemon(true);
            closer.start();
            closer.join(500);
            Assertions.assertTrue(closer.isAlive(),
                    "a close concurrent with the container's deregistration must wait behind "
                            + "the in-flight release instead of racing its check and clear");

            ds.releaseGate.countDown();
            deregistrar.join(AWAIT_TIMEOUT_MS);
            closer.join(AWAIT_TIMEOUT_MS);
            Assertions.assertFalse(deregistrar.isAlive(), "the deregistration must complete");
            Assertions.assertFalse(closer.isAlive(), "the close must complete once released");
            Assertions.assertNull(deregistrationFailure.get(), "the deregistration must not fail");
            Assertions.assertNull(closeFailure.get(), "the close must not fail");

            Assertions.assertEquals(1, ds.releases.get(),
                    "of the deregistration and the close, exactly one takes the release and the "
                            + "other finds the handle already gone");
            Assertions.assertFalse(server.isRegistered(poolOname),
                    "the pool MBean must be off the server after both lifecycle paths ran");
            Assertions.assertNull(ds.heldHandle(), "neither path may leave a stale handle behind");
        } finally {
            ds.releaseGate.countDown();
            deregistrar.join(AWAIT_TIMEOUT_MS);
            if (closer != null) {
                closer.join(AWAIT_TIMEOUT_MS);
            }
            if (server.isRegistered(dsName)) {
                server.unregisterMBean(dsName);
            }
            ds.close();
            sweep(server, dsName);
        }
    }
}
