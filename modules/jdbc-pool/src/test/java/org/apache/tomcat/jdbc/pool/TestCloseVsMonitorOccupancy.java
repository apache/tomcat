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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The data source monitor serializes pool publication, closure and the JMX
 * registration lifecycle - and it must be held for those steps only. A
 * driver call against a hung database is unbounded and uninterruptible, so
 * anything the monitor may not straddle is exactly such a call.
 * <p>
 * Before this fix, {@code close(boolean)} held the monitor across the whole
 * teardown - the physical disconnect of every pooled session - so a close
 * racing a hung database parked every later first borrow (which needs the
 * monitor at its publication step) and every container JMX callback behind
 * an uninterruptible driver wait. Note that the other waits taken under this
 * monitor cannot reach a driver call: the connection-MBean sweep's
 * {@code con.lock()} only ever meets queue-resident lock regions, and every
 * lock region that spans a driver call has claimed the connection out of
 * the queues first, so the sweep waits at most on queue bookkeeping.
 * <p>
 * The test parks the close drain inside the driver's disconnect and then
 * checks that the paths needing the monitor run to their outcome meanwhile:
 * a borrow fails fast with the closed rejection instead of parking, and the
 * container's registration completes, publishing nothing of the pool the
 * close already took.
 */
class TestCloseVsMonitorOccupancy extends JdbcPoolBaseTest {

    private static MBeanServer server() {
        return ManagementFactory.getPlatformMBeanServer();
    }

    /* A name no other test of the suite uses, so nothing collides by accident. */
    private static ObjectName uniqueName(String role) throws Exception {
        return new ObjectName("tomcat:type=MonitorOccupancyTest,role=" + role
                + ",uid=" + System.nanoTime());
    }

    /* Test hygiene: take away anything a broken build left published for this
       registration - the uid is carried down into every name derived from it. */
    private static void sweepNames(MBeanServer server, ObjectName dsName) throws Exception {
        String uid = dsName.getKeyProperty("uid");
        for (ObjectName name : server.queryNames(new ObjectName("tomcat.jdbc:*"), null)) {
            if (uid.equals(name.getKeyProperty("uid"))) {
                server.unregisterMBean(name);
            }
        }
    }

    /*
     * A modeler-style wrapper of the kind a container registers in place of a
     * DataSource, forwarding the registration callbacks to it.
     */
    public interface PoolHostMBean {
        String getMarker();
    }

    public static class PoolHost implements PoolHostMBean, MBeanRegistration {
        private final DataSource ds;

        PoolHost(DataSource ds) {
            this.ds = ds;
        }

        @Override
        public String getMarker() {
            return "pool-host";
        }

        @Override
        public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
            ds.preRegister(server, name);
            return name;
        }

        @Override
        public void postRegister(Boolean registrationDone) {
            ds.postRegister(registrationDone);
        }

        @Override
        public void preDeregister() throws Exception {
            ds.preDeregister();
        }

        @Override
        public void postDeregister() {
            ds.postDeregister();
        }
    }

    /*
     * An H2 driver whose physical connections park inside close() once, so
     * the close drain can be held on the driver while the test observes the
     * paths that need the data source monitor.
     */
    public static class ParkingCloseDriver implements Driver {

        static final CountDownLatch CLOSE_ENTERED = new CountDownLatch(1);
        static final CountDownLatch CLOSE_RELEASED = new CountDownLatch(1);

        private final Driver delegate = new org.h2.Driver();

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            Connection connection = delegate.connect(url, info);
            if (connection == null) {
                return null;
            }
            return (Connection) Proxy.newProxyInstance(
                    ParkingCloseDriver.class.getClassLoader(),
                    new Class<?>[] { Connection.class },
                    new ParkingCloseHandler(connection));
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return delegate.acceptsURL(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return delegate.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return delegate.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return delegate.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return delegate.jdbcCompliant();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }
    }

    /* Parks the first close() on the data source monitor's account. */
    private static class ParkingCloseHandler implements InvocationHandler {

        private final Connection vendor;
        private boolean parked;

        ParkingCloseHandler(Connection vendor) {
            this.vendor = vendor;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("close") && !parked) {
                parked = true;
                ParkingCloseDriver.CLOSE_ENTERED.countDown();
                try {
                    ParkingCloseDriver.CLOSE_RELEASED.await(AWAIT_TIMEOUT_MS * 4L, TimeUnit.MILLISECONDS);
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                    throw new SQLException(x);
                }
            }
            try {
                return method.invoke(vendor, args);
            } catch (InvocationTargetException x) {
                throw x.getCause();
            }
        }
    }

    @Test
    void aCloseParkedInADriverDisconnectDoesNotParkBorrowsOrJmxCallbacks() throws Exception {
        MBeanServer server = server();
        DataSource ds = newDataSource();
        ds.setDriverClassName(ParkingCloseDriver.class.getName());
        ds.getPoolProperties().setJmxEnabled(true);
        ds.getPoolProperties().setInitialSize(1);
        ds.getPoolProperties().setMinIdle(0);
        ds.createPool();

        ObjectName dsName = uniqueName("close-vs-monitor");
        ObjectName poolOname = ds.createObjectName(dsName);

        // The drain: close takes the pool and reaches the driver's disconnect
        // of the one idle session, where it parks.
        AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        Thread closer = new Thread(() -> {
            try {
                ds.close(true);
            } catch (Throwable x) {
                closeFailure.set(x);
            }
        }, "close parked in disconnect");
        closer.setDaemon(true);
        Thread borrower = null;
        Thread registrar = null;
        try {
            closer.start();
            Assertions.assertTrue(ParkingCloseDriver.CLOSE_ENTERED.await(AWAIT_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS),
                    "the close must have reached the driver's disconnect");
            Assertions.assertTrue(closer.isAlive(), "the premise: the drain is parked on the driver");

            // The paths that need the data source monitor while the teardown
            // waits on the driver:
            // a first borrow must fail fast as closed, not park behind it...
            AtomicReference<SQLException> borrowFailure = new AtomicReference<>();
            AtomicReference<Connection> borrowed = new AtomicReference<>();
            borrower = new Thread(() -> {
                try {
                    borrowed.set(ds.getConnection());
                } catch (SQLException x) {
                    borrowFailure.set(x);
                }
            }, "borrow racing the parked teardown");
            borrower.setDaemon(true);
            borrower.start();
            borrower.join(AWAIT_TIMEOUT_MS);
            Assertions.assertFalse(borrower.isAlive(),
                    "a borrow must not park behind the driver disconnect of an "
                            + "in-flight close: against a hung database the close's "
                            + "wait is unbounded and uninterruptible, so it would "
                            + "park the borrow forever");
            Assertions.assertNotNull(borrowFailure.get(), "the borrow must have been rejected");
            Assertions.assertTrue(borrowFailure.get().getMessage() != null
                            && borrowFailure.get().getMessage().contains("closed"),
                    "the rejection must say the data source is closed, was: "
                            + borrowFailure.get().getMessage());

            // ...and the container's registration callback must complete (the
            // pool is already taken, so it publishes nothing of the dead pool).
            AtomicReference<Throwable> registrationFailure = new AtomicReference<>();
            registrar = new Thread(() -> {
                try {
                    server.registerMBean(new PoolHost(ds), dsName);
                } catch (Throwable x) {
                    registrationFailure.set(x);
                }
            }, "registration racing the parked teardown");
            registrar.setDaemon(true);
            registrar.start();
            registrar.join(AWAIT_TIMEOUT_MS);
            Assertions.assertFalse(registrar.isAlive(),
                    "the container's registration callback must not park behind "
                            + "the driver disconnect of an in-flight close");
            Assertions.assertNull(registrationFailure.get(), "the registration must not fail");
            Assertions.assertFalse(server.isRegistered(poolOname),
                    "a data source whose pool a running close already took has no "
                            + "MBean to publish and must publish nothing");

            // Let the teardown finish: everything the parked close owned is
            // released by it.
            ParkingCloseDriver.CLOSE_RELEASED.countDown();
            closer.join(AWAIT_TIMEOUT_MS);
            Assertions.assertFalse(closer.isAlive(), "the close must complete once released");
            Assertions.assertNull(closeFailure.get(), "the close must not fail");
            awaitSessions(0);
        } finally {
            ParkingCloseDriver.CLOSE_RELEASED.countDown();
            if (borrower != null) {
                borrower.join(AWAIT_TIMEOUT_MS);
            }
            if (registrar != null) {
                registrar.join(AWAIT_TIMEOUT_MS);
            }
            if (server.isRegistered(dsName)) {
                server.unregisterMBean(dsName);
            }
            ds.close();
            sweepNames(server, dsName);
        }
    }

}
