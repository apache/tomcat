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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A return that is still in flight when the pool closes must not hand its
 * connection to the idle queue: close() drains the queues once and a closed
 * pool never drains them again, so a connection offered after the drain
 * strands there - a live session outside every shutdown path, unreached by
 * any counter and pinned for the life of the classloader. The return path
 * honours the closed flag at the hand-over into the idle queue, exactly as
 * {@code testAllIdle()} already does for its own offer-back.
 * <p>
 * A check before an offer cannot close the race completely: a close that
 * arrives after the check drains the queues before the offer lands. The
 * paths that enqueue - the return, the sweep's offer-back, and the publish
 * of a freshly created connection - therefore re-read the closed flag
 * <em>after</em> the offer and take back what they handed to a queue that
 * has already been drained for the last time.
 */
class TestCloseVsReturn extends JdbcPoolBaseTest {

    /*
     * A validator that parks the return path once armed, standing in for the
     * driver-side work of a return - the validation query, the transaction
     * termination, the reconnect after maxAge - whose duration decides how
     * wide the close race is. It must stay a public static class with a
     * no-arg constructor, as the pool instantiates validators configured by
     * class name; the test installs the instance directly.
     */
    public static class ParkOnReturnValidator implements Validator {

        static final AtomicBoolean ARMED = new AtomicBoolean();
        static final CountDownLatch VALIDATING = new CountDownLatch(1);
        static final CountDownLatch RELEASE = new CountDownLatch(1);

        @Override
        public boolean validate(Connection connection, int validateAction) {
            if (ARMED.compareAndSet(true, false)) {
                VALIDATING.countDown();
                try {
                    RELEASE.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    @Test
    void returnConcurrentWithCloseRetiresTheConnectionInsteadOfOfferingIt() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxIdle(1);
        ds.getPoolProperties().setTestOnReturn(true);
        // without this the interval short-circuit swallows the first return
        // validation, as the borrow stamped the validation timestamp
        ds.getPoolProperties().setValidationInterval(0);
        ds.getPoolProperties().setValidator(new ParkOnReturnValidator());
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1); // sweeper off
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();
        awaitSessions(1);

        ParkOnReturnValidator.ARMED.set(true);
        AtomicReference<Throwable> returnFailure = new AtomicReference<>();
        Thread returner = new Thread(() -> {
            try {
                held.close();
            } catch (Throwable x) {
                returnFailure.set(x);
            }
        }, "returning-thread");
        returner.setDaemon(true);
        returner.start();
        Assertions.assertTrue(ParkOnReturnValidator.VALIDATING.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "the return must have reached the driver work of its validation");

        // The standard, non-forced close. The pool holds no idle connection -
        // the one it has is the one being returned, parked mid-validation -
        // so the drain passes over both queues and the close completes while
        // the return is still in flight, long past the closed check at the
        // top of the return path.
        ds.close();

        ParkOnReturnValidator.RELEASE.countDown();
        returner.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(returner.isAlive(), "the return must complete");
        Assertions.assertNull(returnFailure.get(),
                "the return itself must not fail, got " + returnFailure.get());

        // What the return does after its validation is the point of the test:
        // the closed pool must not receive the connection in its idle queue.
        Assertions.assertEquals(0, pool.getIdle(),
                "a closed pool must hold no idle connection");
        Assertions.assertEquals(0, pool.getSize(),
                "the connection racing the close must have been retired, "
                        + "not parked in the drained idle queue");
        awaitSessions(0);
    }

    /*
     * Pool properties that park the path into the idle queue once armed,
     * inside the hand-over itself: the room check that precedes the offer is
     * the last thing evaluated before it, so parking on the getMaxIdle() call
     * holds the offer back until a full pool close has completed - the window
     * that no check before the offer can close, because a close arriving
     * after that check still drains the queues before the offer lands.
     */
    private static final class ParkBeforeOfferProbe extends PoolProperties {

        private static final long serialVersionUID = 1L;

        private final AtomicBoolean armed = new AtomicBoolean();
        private final CountDownLatch parked = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        @Override
        public int getMaxIdle() {
            if (armed.compareAndSet(true, false)) {
                parked.countDown();
                try {
                    released.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return super.getMaxIdle();
        }

        void arm() {
            armed.set(true);
        }

        boolean awaitParked() throws InterruptedException {
            return parked.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        void release() {
            released.countDown();
        }
    }

    /*
     * A validator that parks the creation of a new connection once armed,
     * standing in for whatever the borrower thread is doing between the
     * creation path's closed check and the moment it publishes the new
     * connection into the busy queue. It must stay a public static class
     * with a no-arg constructor; the test installs the instance directly.
     */
    public static class ParkOnConnectValidator implements Validator {

        static final AtomicBoolean ARMED = new AtomicBoolean();
        static final CountDownLatch PARKED = new CountDownLatch(1);
        static final CountDownLatch RELEASE = new CountDownLatch(1);

        @Override
        public boolean validate(Connection connection, int validateAction) {
            if (validateAction == PooledConnection.VALIDATE_INIT && ARMED.compareAndSet(true, false)) {
                PARKED.countDown();
                try {
                    RELEASE.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    @Test
    void returnOfferedAfterTheDrainIsReclaimedAndRetired() throws Exception {
        ParkBeforeOfferProbe props = new ParkBeforeOfferProbe();
        props.setUrl(getUrl());
        props.setDriverClassName("org.h2.Driver");
        props.setUsername("sa");
        props.setPassword("");
        props.setJmxEnabled(false);
        props.setInitialSize(0);
        props.setMinIdle(0);
        props.setMaxActive(1);
        props.setMaxIdle(1);
        props.setTimeBetweenEvictionRunsMillis(-1); // sweeper off
        DataSource ds = manage(new DataSource());
        ds.setPoolProperties(props);
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();
        awaitSessions(1);

        props.arm();
        AtomicReference<Throwable> returnFailure = new AtomicReference<>();
        Thread returner = new Thread(() -> {
            try {
                held.close();
            } catch (Throwable x) {
                returnFailure.set(x);
            }
        }, "offering-thread");
        returner.setDaemon(true);
        returner.start();
        Assertions.assertTrue(props.awaitParked(),
                "the return must have reached the hand-over check before the offer");

        // Complete a full non-forced close while the offer is held back: the
        // pool holds no idle connection to drain, so close() finishes and the
        // queues are never scanned again.
        ds.close();

        props.release();
        returner.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(returner.isAlive(), "the return must complete");
        Assertions.assertNull(returnFailure.get(),
                "the return itself must not fail, got " + returnFailure.get());

        // The offer landed after the drain. Whoever owns the connection once
        // the offer completes must see to its closure: here the return itself,
        // by re-claiming what it offered to a queue nothing drains any more.
        Assertions.assertEquals(0, pool.getIdle(),
                "the late offer must be taken out of the queue again");
        Assertions.assertEquals(0, pool.getSize(),
                "the re-claimed connection must count as retired");
        awaitSessions(0);
    }

    @Test
    void idleTestOfferBackLandingAfterTheDrainIsReclaimedAndRetired() throws Exception {
        ParkBeforeOfferProbe props = new ParkBeforeOfferProbe();
        props.setUrl(getUrl());
        props.setDriverClassName("org.h2.Driver");
        props.setUsername("sa");
        props.setPassword("");
        props.setJmxEnabled(false);
        props.setInitialSize(0);
        props.setMinIdle(0);
        props.setMaxActive(1);
        props.setMaxIdle(2);
        props.setTestWhileIdle(true);
        props.setTimeBetweenEvictionRunsMillis(-1); // sweep manually
        DataSource ds = manage(new DataSource());
        ds.setPoolProperties(props);
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();
        held.close(); // back in the idle queue, probe unarmed
        awaitSessions(1);

        props.arm();
        AtomicReference<Throwable> sweepFailure = new AtomicReference<>();
        Thread sweeper = new Thread(() -> {
            try {
                pool.testAllIdle(false);
            } catch (Throwable x) {
                sweepFailure.set(x);
            }
        }, "sweeping-thread");
        sweeper.setDaemon(true);
        sweeper.start();
        Assertions.assertTrue(props.awaitParked(),
                "the sweep must have reached the offer-back check after its own closed check");

        // The sweep holds the only connection out of the queues, so the close
        // drains nothing and completes while the sweep is still in flight.
        ds.close();

        props.release();
        sweeper.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(sweeper.isAlive(), "the sweep must complete");
        Assertions.assertNull(sweepFailure.get(),
                "the sweep itself must not fail, got " + sweepFailure.get());

        Assertions.assertEquals(0, pool.getIdle(),
                "the sweep's offer-back must not survive the completed close");
        Assertions.assertEquals(0, pool.getSize(),
                "the re-claimed connection must count as retired");
        awaitSessions(0);
    }

    @Test
    void newConnectionPublishedAfterTheDrainFailsTheBorrowAndIsDisconnected() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setTestOnConnect(true);
        ds.getPoolProperties().setValidationInterval(0);
        ds.getPoolProperties().setValidator(new ParkOnConnectValidator());
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1); // sweeper off
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        ParkOnConnectValidator.ARMED.set(true);
        AtomicReference<Throwable> borrowFailure = new AtomicReference<>();
        AtomicReference<Connection> served = new AtomicReference<>();
        Thread creator = new Thread(() -> {
            try {
                served.set(ds.getConnection());
            } catch (Throwable x) {
                borrowFailure.set(x);
            }
        }, "creating-thread");
        creator.setDaemon(true);
        creator.start();
        Assertions.assertTrue(ParkOnConnectValidator.PARKED.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "the creation must have reached the validation after its closed check");
        awaitSessions(1); // the physical session is up, held only by the creator

        // Nothing is in the queues for a drain to take - the session lives in
        // the creator's local - so the close completes before the creation
        // publishes the connection.
        ds.close();

        ParkOnConnectValidator.RELEASE.countDown();
        creator.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(creator.isAlive(), "the borrow must complete");
        Assertions.assertNotNull(borrowFailure.get(),
                "a closed pool must not hand out the connection, got " + served.get());
        Assertions.assertNull(served.get(), "no connection may be served after the close");
        Assertions.assertEquals(0, pool.getSize(),
                "the published-then-reclaimed connection must count as retired");
        awaitSessions(0);
    }
}
