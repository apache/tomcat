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

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A thread that is counted as a waiter is not necessarily able to consume a
 * wake-up placeholder: after an interrupted or timed out wait its queue latch
 * is already deregistered while the wait count is still held, and a borrower
 * that creates its own connection does not wait on the queue at all. A
 * placeholder queued while such a thread is counted can only be taken by the
 * shutdown drain - which drains that very queue, destroying and re-queueing
 * one placeholder per poll. The drain must tear the pool down instead of
 * spinning for as long as the counted thread holds its wait count.
 */
class TestCloseVsWaiters extends JdbcPoolBaseTest {

    /*
     * Pool properties that notice - and widen - the window inside
     * borrowConnection() after an interrupted queue wait has removed this
     * thread's latch from the queue but before the pool wait count is
     * decremented. getPropagateInterruptState() is consulted exactly there,
     * and (with JMX off and no interrupted pool close in flight) nowhere
     * else.
     */
    private static final class InterruptWindowProbe extends PoolProperties {

        private static final long serialVersionUID = 1L;

        private final CountDownLatch inWindow = new CountDownLatch(1);
        private final CountDownLatch windowDone = new CountDownLatch(1);
        private final AtomicBoolean armed = new AtomicBoolean();

        @Override
        public boolean getPropagateInterruptState() {
            if (armed.compareAndSet(true, false)) {
                inWindow.countDown();
                try {
                    Thread.sleep(4000);
                } catch (InterruptedException ignored) {
                    // ignored
                }
                windowDone.countDown();
            }
            return super.getPropagateInterruptState();
        }

        void arm() {
            armed.set(true);
        }

        boolean awaitInWindow() throws InterruptedException {
            return inWindow.await(5, TimeUnit.SECONDS);
        }

        boolean windowDone() {
            return windowDone.getCount() == 0;
        }
    }

    /*
     * Pool properties that widen the window inside borrowConnection() between
     * the maxActive check that failed and the re-check that registers the
     * waiter; getMaxActive() is consulted in both. The hold lets the test
     * destroy a connection exactly there, so that the re-check finds a free
     * slot and sends the borrower into createConnection() while it holds a
     * wait count without a queue latch.
     */
    private static final class RecheckProbe extends PoolProperties {

        private static final long serialVersionUID = 1L;

        private final CountDownLatch inWindow = new CountDownLatch(1);
        private final AtomicBoolean armed = new AtomicBoolean();

        @Override
        public int getMaxActive() {
            if (armed.compareAndSet(true, false)) {
                inWindow.countDown();
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ignored) {
                    // ignored
                }
            }
            return super.getMaxActive();
        }

        void arm() {
            armed.set(true);
        }

        boolean awaitInWindow() throws InterruptedException {
            return inWindow.await(5, TimeUnit.SECONDS);
        }
    }

    /*
     * A driver that passes connects straight through until the test arms it;
     * the next connect() then parks until the test releases it, so the pool
     * close runs against a borrower that holds a wait count inside
     * createConnection().
     */
    public static class ParkNextConnectDriver implements Driver {

        static final AtomicBoolean PARK_NEXT = new AtomicBoolean();
        static final CountDownLatch CONNECT_STARTED = new CountDownLatch(1);
        static final CountDownLatch CONNECT_RELEASE = new CountDownLatch(1);

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            if (PARK_NEXT.compareAndSet(true, false)) {
                CONNECT_STARTED.countDown();
                try {
                    CONNECT_RELEASE.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SQLException(e);
                }
            }
            return new org.h2.Driver().connect(url, info);
        }

        @Override
        public boolean acceptsURL(String url) {
            return url != null && url.startsWith("jdbc:h2:");
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 0;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }

    @Test
    void forceCloseWhileAnInterruptedWaiterIsStillCountedDoesNotSpin() throws Exception {
        InterruptWindowProbe props = new InterruptWindowProbe();
        props.setUrl(getUrl());
        props.setDriverClassName("org.h2.Driver");
        props.setUsername("sa");
        props.setPassword("");
        props.setJmxEnabled(false);
        props.setInitialSize(0);
        props.setMinIdle(0);
        props.setMinEvictableIdleTimeMillis(-1);
        props.setMaxActive(1);
        props.setMaxIdle(1);
        // <= 0 means: wait forever for a notification
        props.setMaxWait(-1);
        props.setTimeBetweenEvictionRunsMillis(-1); // sweeper off
        DataSource ds = manage(new DataSource());
        ds.setPoolProperties(props);
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection holder = ds.getConnection(); // the only slot is taken

        Thread waiter = new Thread(() -> {
            try {
                ds.getConnection();
            } catch (Throwable expected) {
                // the interrupted borrow fails - as intended
            }
        }, "interrupted-waiter");
        waiter.setDaemon(true);
        props.arm();
        waiter.start();
        awaitUntil(() -> ds.getWaitCount() == 1);

        waiter.interrupt();
        Assertions.assertTrue(props.awaitInWindow(),
                "waiter should have reached the window between removing its "
                        + "queue latch and decrementing the pool wait count");

        // Destroy the only connection while the interrupted waiter still
        // counts although its latch is gone: a wake-up placeholder is queued
        // into the idle queue that no borrower will ever take.
        pool.purgeOnReturn();
        holder.close();
        Assertions.assertEquals(1, ds.getIdle(), "the orphaned placeholder");

        // The shutdown drain must not feed on that placeholder: it has to
        // tear the pool down while the counted waiter is still in its window.
        Thread closer = new Thread(() -> ds.close(true), "closer");
        closer.setDaemon(true);
        closer.start();
        closer.join(2000);
        Assertions.assertFalse(closer.isAlive(),
                "close() must not spin on the placeholder while a waiter "
                        + "whose latch is deregistered is still counted");
        Assertions.assertFalse(props.windowDone(),
                "close() must have completed while the counted waiter was "
                        + "still inside its window");

        waiter.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(waiter.isAlive(), "the waiter must not linger");
        // assert on the pool itself: the data source rejects state access
        // once it is closed
        Assertions.assertEquals(0, pool.getIdle());
        Assertions.assertEquals(0, pool.getSize());
        awaitSessions(0);
    }

    @Test
    void forceCloseWhileAWaiterIsStillConnectingDoesNotSpin() throws Exception {
        RecheckProbe props = new RecheckProbe();
        props.setUrl(getUrl());
        props.setDriverClassName(ParkNextConnectDriver.class.getName());
        props.setUsername("sa");
        props.setPassword("");
        props.setJmxEnabled(false);
        props.setInitialSize(0);
        props.setMinIdle(0);
        props.setMinEvictableIdleTimeMillis(-1);
        props.setMaxActive(2);
        props.setMaxIdle(2);
        // <= 0 means: wait forever for a notification
        props.setMaxWait(-1);
        props.setTimeBetweenEvictionRunsMillis(-1); // sweeper off
        DataSource ds = manage(new DataSource());
        ds.setPoolProperties(props);
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection holder1 = ds.getConnection();
        Connection holder2 = ds.getConnection();

        ParkNextConnectDriver.PARK_NEXT.set(true);
        AtomicReference<Throwable> waiterFailure = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            try {
                ds.getConnection().close();
            } catch (Throwable x) {
                waiterFailure.set(x);
            }
        }, "connecting-waiter");
        waiter.setDaemon(true);
        props.arm();
        waiter.start();
        Assertions.assertTrue(props.awaitInWindow(),
                "borrower should have reached the window between the failed "
                        + "maxActive check and the re-check that registers it");
        try {
            // Destroy one connection exactly in that window: the re-check
            // finds a free slot and sends the counted borrower into
            // createConnection(), where it waits in the driver, without a
            // queue latch that could consume a placeholder.
            pool.purgeOnReturn();
            holder1.close();

            Assertions.assertTrue(ParkNextConnectDriver.CONNECT_STARTED
                            .await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                    "the borrower must be parked inside the driver's connect()");
            Assertions.assertEquals(1, ds.getWaitCount(),
                    "the connecting borrower must still hold a wait count");

            // Destroy the second connection: the release sees the wait count
            // and queues a wake-up placeholder for the counted borrower -
            // which cannot take it, being parked inside the connect.
            holder2.close();
            Assertions.assertEquals(1, ds.getIdle(), "the orphaned placeholder");

            // The shutdown drain must not feed on that placeholder: it has to
            // tear the pool down while the counted borrower is still parked.
            Thread closer = new Thread(() -> ds.close(true), "closer");
            closer.setDaemon(true);
            closer.start();
            closer.join(2000);
            Assertions.assertFalse(closer.isAlive(),
                    "close() must not spin on the placeholder while a thread "
                            + "is counted inside createConnection()");

            // The parked borrower must not be handed a connection from the
            // closed pool; its connect completes against a pool that rejects
            // it and disconnects the new physical connection.
            ParkNextConnectDriver.CONNECT_RELEASE.countDown();
            waiter.join(AWAIT_TIMEOUT_MS);
            Assertions.assertFalse(waiter.isAlive(), "the borrower must not linger");
            Assertions.assertNotNull(waiterFailure.get(),
                    "the borrower must not be handed a connection from the closed pool");
            awaitSessions(0);
        } finally {
            // Never leave the borrower parked inside connect(): it would
            // outlive the class teardown and the H2 server stop.
            ParkNextConnectDriver.CONNECT_RELEASE.countDown();
            waiter.join(AWAIT_TIMEOUT_MS);
        }
    }
}
