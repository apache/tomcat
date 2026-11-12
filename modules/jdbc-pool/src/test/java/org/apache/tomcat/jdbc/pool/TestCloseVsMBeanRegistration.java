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
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.ObjectName;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.jmx.JmxUtil;

/**
 * The pool publishes a per-connection MBean from the sweep that runs when the
 * pool's own MBean is registered, and takes that MBean back in
 * {@code PooledConnection.release()}. The registration runs under the
 * connection's monitor, but until this test the release unregistered under no
 * lock at all - and the {@code con.lock()} the registration sites also take is
 * a documented no-op unless {@code useLock} or the sweeper is enabled, so in
 * the default configuration the two ran wide open. A shutdown that drains the
 * connection while its registration is still in flight reads the still-null
 * name, unregisters nothing, and the registration then lands on a released,
 * disconnected connection: nothing is left ever to take it back, so an MBean
 * of a dead connection - and the class loader it pins - stays registered for
 * the life of the JVM.
 * <p>
 * The test drives that interleaving for real. A connection parks inside its
 * own monitor mid-registration (holding the monitor is the point: the fix is
 * to make the release acquire it too, so a close then waits for the
 * registration it would otherwise miss). The registration is armed only for
 * the sweep, which the test runs on its own thread the way a DataSource
 * registration does. Once the park is reached, a second thread closes the
 * pool. Before the fix the close sails past the in-flight registration and
 * returns; after it, the close is held at the connection's release until the
 * registration completes, and then takes the MBean back. The final witness is
 * the platform MBean server itself: no {@code connections=*} MBean under this
 * pool's name may remain after the close.
 * <p>
 * The same monitor step also settles the released flag - settled only after
 * leaving it, a registration that won the monitor in between would see
 * neither the name nor the flag and publish the orphan anyway. That window
 * sits between two statements inside {@code release()} itself, with no seam a
 * test can hold; like the window in
 * {@link TestSetupConnectionMBeanFailure}, its closure is carried by the
 * synchronized block and by review rather than by a test of its own.
 */
class TestCloseVsMBeanRegistration extends JdbcPoolBaseTest {

    /*
     * A connection whose JMX registration parks inside this monitor once the
     * test arms it. Parking inside the monitor is what makes the race
     * observable: the parked thread sits exactly between the released-check
     * and the name assignment of createMBean(), holding nothing but the
     * monitor - which is precisely the lock the release must come to share.
     */
    static class ParkedRegistrationConnection extends PooledConnection {

        private final ParkedRegistrationPool pool;

        ParkedRegistrationConnection(PoolConfiguration prop, ParkedRegistrationPool parent) {
            super(prop, parent);
            this.pool = parent;
        }

        @Override
        public synchronized void createMBean() {
            // The hooks are resolved at call time: this connection is built
            // while the pool's constructor is still running, so the pool's own
            // fields are null for this first registration - which must simply
            // pass through unparked.
            AtomicBoolean armed = pool.armed;
            if (armed != null && armed.get()) {
                pool.parkEntered.countDown();
                try {
                    // Bounded: the test always lets the registration finish;
                    // the bound only stops the JVM if the test itself dies.
                    pool.parkReleased.await(AWAIT_TIMEOUT_MS * 4L, TimeUnit.MILLISECONDS);
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                }
            }
            super.createMBean();
        }
    }

    /*
     * A pool handing out the connections above. The base create() is still
     * invoked for its pool-size bookkeeping; it does not connect, so nothing
     * reaches a registration - and the park - before the test arms one.
     */
    static class ParkedRegistrationPool extends ConnectionPool {

        final AtomicBoolean armed = new AtomicBoolean(false);
        final CountDownLatch parkEntered = new CountDownLatch(1);
        final CountDownLatch parkReleased = new CountDownLatch(1);

        ParkedRegistrationPool(PoolConfiguration prop) throws SQLException {
            super(prop);
        }

        @Override
        protected PooledConnection create(boolean incrementCounter) {
            super.create(incrementCounter);
            return new ParkedRegistrationConnection(getPoolProperties(), this);
        }
    }

    private static PoolConfiguration newPoolProperties(String name) {
        PoolProperties props = new PoolProperties();
        props.setUrl(getUrl());
        props.setDriverClassName("org.h2.Driver");
        props.setUsername("sa");
        props.setPassword("");
        props.setName(name);
        props.setJmxEnabled(true);
        props.setInitialSize(1);
        props.setMinIdle(0);
        props.setMaxActive(1);
        // The connection lock is only taken while the pool sweeper is
        // enabled (or useLock is on); with the eviction timer off - a
        // supported configuration - con.lock() is the documented no-op and
        // registration and unregistration are left with nothing but the
        // monitor to pair them.
        props.setTimeBetweenEvictionRunsMillis(0);
        return props;
    }

    @Test
    void aCloseAfterJmxEnabledIsClearedStillReleasesThePoolMBean() throws Exception {
        String poolName = "close-vs-jmxenabled-" + System.nanoTime();
        ObjectName base = new ObjectName(
                ConnectionPool.POOL_JMX_TYPE_PREFIX + "ConnectionPool,name=" + poolName);
        ParkedRegistrationPool pool = new ParkedRegistrationPool(newPoolProperties(poolName));
        ObjectName wrapper = null;
        try {
            wrapper = JmxUtil.registerJmx(base, null, pool.getJmxPool());
            Assertions.assertNotNull(wrapper, "the pool MBean must be publishable");
            Assertions.assertTrue(ManagementFactory.getPlatformMBeanServer().isRegistered(wrapper));

            // Change the backing configuration directly, bypassing the DataSource and
            // pool MBean start-only guards, then close the pool directly.
            pool.getPoolProperties().setJmxEnabled(false);
            pool.close(true);

            Assertions.assertFalse(ManagementFactory.getPlatformMBeanServer().isRegistered(wrapper),
                    "a published pool MBean must be released on close even when jmxEnabled is changed directly");
            Assertions.assertNull(pool.getJmxPool(), "the closed pool must forget its JMX wrapper");
        } finally {
            pool.close(true);
            JmxUtil.unregisterJmx(wrapper);
        }
    }

    @Test
    void aCloseRacingAnInFlightRegistrationLeavesNoOrphanMBean() throws Exception {
        String poolName = "close-vs-mbean-" + System.nanoTime();
        ObjectName base = new ObjectName(
                ConnectionPool.POOL_JMX_TYPE_PREFIX + "ConnectionPool,name=" + poolName);
        ParkedRegistrationPool pool = new ParkedRegistrationPool(newPoolProperties(poolName));
        ObjectName wrapper = null;
        try {
            // Publish the pool's JMX object the way a DataSource registration
            // does; until now its name exists, neither the sweep gate nor any
            // connection registration can produce a name to register under.
            wrapper = JmxUtil.registerJmx(base, null, pool.getJmxPool());
            Assertions.assertNotNull(wrapper, "the pool MBean must be publishable");

            // The sweep a DataSource runs right after publishing the pool MBean.
            // Its one idle connection parks mid-registration, holding its
            // monitor, before the name is assigned.
            pool.armed.set(true);
            Thread sweep = new Thread(pool::registerConnectionMBeans, "sweep-thread");
            sweep.start();
            Assertions.assertTrue(pool.parkEntered.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                    "the sweep must reach the connection's registration");

            // Close the pool while that registration is in flight. The drain
            // finds the parked connection in the idle queue and releases it.
            CountDownLatch closeDone = new CountDownLatch(1);
            Thread close = new Thread(() -> {
                try {
                    pool.close(true);
                } finally {
                    closeDone.countDown();
                }
            }, "close-thread");
            close.start();
            // Before the fix the close walks past the still-null name and this
            // wait succeeds in milliseconds; with the fix the release waits
            // for the in-flight registration, so it cannot finish while the
            // park holds the monitor.
            boolean closedWhileRegistrationInFlight =
                    closeDone.await(1000L, TimeUnit.MILLISECONDS);
            Assertions.assertFalse(closedWhileRegistrationInFlight,
                    "the close must not pass a connection's release while its "
                            + "MBean registration is still in flight");

            // Let the registration finish and let both threads run out.
            pool.armed.set(false);
            pool.parkReleased.countDown();
            sweep.join(AWAIT_TIMEOUT_MS);
            close.join(AWAIT_TIMEOUT_MS);
            Assertions.assertFalse(sweep.isAlive(), "the sweep must complete");
            Assertions.assertFalse(close.isAlive(), "the close must complete");

            Set<ObjectName> leaked = ManagementFactory.getPlatformMBeanServer()
                    .queryNames(new ObjectName(base.getCanonicalName() + ",connections=*"), null);
            Assertions.assertTrue(leaked.isEmpty(),
                    "a released connection's MBean must not stay registered: " + leaked);
            awaitSessions(0);
        } finally {
            pool.close(true);
            JmxUtil.unregisterJmx(wrapper);
        }
    }
}
