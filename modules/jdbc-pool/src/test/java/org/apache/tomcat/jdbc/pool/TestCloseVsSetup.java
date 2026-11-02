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

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A borrower that already has a connection in hand - polled from the idle
 * queue or handed straight over a waiter's latch, neither of which observes
 * the closed flag again - must not use a close that arrives mid-setup to
 * start a fresh physical session. The setup path re-reads the closed flag
 * before its reconnect, exactly as the creation path does, and the
 * destroy-side wake-up placeholder re-reads it after its offer: a close
 * arriving in either gap retires the connection instead of circulating it
 * past the shutdown drain that has already passed.
 */
class TestCloseVsSetup extends JdbcPoolBaseTest {

    /*
     * A validator that fails the first borrow-side validation once armed,
     * and parks it first: the parked moment is the gap between the setup
     * path taking the connection out of the queue and its last look at the
     * closed flag before the reconnect. The failing validation is what
     * drives the setup into that reconnect branch. The test's initial borrow
     * creates its connection and is validated on connect, not on borrow, so
     * the queued borrower's setup is the first borrow validation. It must
     * stay a public static class with a no-arg constructor; the test
     * installs the instance directly.
     */
    public static class ParkThenFailSecondBorrowValidator implements Validator {

        static final AtomicBoolean ARMED = new AtomicBoolean();
        static final CountDownLatch PARKED = new CountDownLatch(1);
        static final CountDownLatch RELEASE = new CountDownLatch(1);
        static final AtomicInteger CALLS = new AtomicInteger();

        @Override
        public boolean validate(Connection connection, int validateAction) {
            if (validateAction == PooledConnection.VALIDATE_BORROW && CALLS.incrementAndGet() == 1
                    && ARMED.compareAndSet(true, false)) {
                PARKED.countDown();
                try {
                    RELEASE.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return false;
            }
            return true;
        }
    }

    @Test
    void setupReachingTheReconnectAfterTheCloseIsRetiredAndFailsTheBorrow() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxIdle(1);
        ds.getPoolProperties().setFairQueue(true);
        ds.getPoolProperties().setTestOnBorrow(true);
        ds.getPoolProperties().setValidationInterval(0);
        ds.getPoolProperties().setValidator(new ParkThenFailSecondBorrowValidator());
        ds.getPoolProperties().setMaxWait(2000); // bounded, never expected to elapse
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1); // sweeper off
        ds.createPool();
        ConnectionPool pool = ds.getPool();
        ParkThenFailSecondBorrowValidator.ARMED.set(true);

        Connection held = ds.getConnection(); // validation call 1, passes
        awaitSessions(1);

        AtomicReference<Throwable> borrowFailure = new AtomicReference<>();
        AtomicReference<Connection> served = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            try {
                served.set(ds.getConnection());
            } catch (Throwable x) {
                borrowFailure.set(x);
            }
        }, "queued-borrower");
        waiter.setDaemon(true);
        waiter.start();
        awaitUntil(() -> pool.getWaitCount() > 0);

        // The return hands the connection straight to the registered waiter.
        held.close();
        Assertions.assertTrue(ParkThenFailSecondBorrowValidator.PARKED.await(AWAIT_TIMEOUT_MS,
                TimeUnit.MILLISECONDS),
                "the borrower's setup must have reached the validation before the reconnect");

        // Complete a full close while the setup stands parked inside its own
        // driver work: the connection lives in neither queue, so the drain
        // passes over both and the queues are never scanned again.
        ds.close();

        ParkThenFailSecondBorrowValidator.RELEASE.countDown();
        waiter.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(waiter.isAlive(), "the borrow must complete");
        Assertions.assertNotNull(borrowFailure.get(),
                "a closed pool must not complete a reconnect for its borrower, got " + served.get());
        Assertions.assertNull(served.get(), "no connection may be served after the close");
        Assertions.assertEquals(0, pool.getSize(),
                "the connection taken out of the borrow must count as retired");
        awaitSessions(0);
    }

    /*
     * A validator that parks the first borrow-side validation once armed and
     * then passes it: the parked moment is the gap between the setup path
     * taking the connection out of the queue and its publish into busy. The
     * passing validation keeps the setup on the no-reconnect branch, the one
     * that used to hand the connection out without ever looking at the
     * closed flag again.
     */
    public static class ParkPassBorrowValidator implements Validator {

        static final AtomicBoolean ARMED = new AtomicBoolean();
        static final CountDownLatch PARKED = new CountDownLatch(1);
        static final CountDownLatch RELEASE = new CountDownLatch(1);
        static final AtomicInteger CALLS = new AtomicInteger();

        @Override
        public boolean validate(Connection connection, int validateAction) {
            if (validateAction == PooledConnection.VALIDATE_BORROW && CALLS.incrementAndGet() == 1
                    && ARMED.compareAndSet(true, false)) {
                PARKED.countDown();
                try {
                    RELEASE.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return true;
        }
    }

    @Test
    void aWokenBorrowerWhoseValidationPassesIsNotServedOffAClosedPool() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxIdle(1);
        ds.getPoolProperties().setFairQueue(true);
        ds.getPoolProperties().setTestOnBorrow(true);
        ds.getPoolProperties().setValidationInterval(0);
        ds.getPoolProperties().setValidator(new ParkPassBorrowValidator());
        ds.getPoolProperties().setMaxWait(2000); // bounded, never expected to elapse
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1); // sweeper off
        ds.createPool();
        ConnectionPool pool = ds.getPool();
        ParkPassBorrowValidator.ARMED.set(true);

        Connection held = ds.getConnection();
        awaitSessions(1);

        AtomicReference<Throwable> borrowFailure = new AtomicReference<>();
        AtomicReference<Connection> served = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            try {
                served.set(ds.getConnection());
            } catch (Throwable x) {
                borrowFailure.set(x);
            }
        }, "queued-borrower");
        waiter.setDaemon(true);
        waiter.start();
        awaitUntil(() -> pool.getWaitCount() > 0);

        // The return hands the connection straight to the registered waiter,
        // whose setup parks inside its (passing) validation.
        held.close();
        Assertions.assertTrue(ParkPassBorrowValidator.PARKED.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "the borrower's setup must have reached the validation before the busy publish");

        // The full close completes while the setup is parked: the connection
        // lives in neither queue, so the drain passes over both.
        ds.close();

        ParkPassBorrowValidator.RELEASE.countDown();
        waiter.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(waiter.isAlive(), "the borrow must complete");
        Assertions.assertNotNull(borrowFailure.get(),
                "a closed pool must not hand out the woken borrower's connection, got " + served.get());
        Assertions.assertNull(served.get(), "no connection may be served after the close");
        Assertions.assertEquals(0, pool.getSize(),
                "the retracted hand-off must count as retired");
        awaitSessions(0);
    }

    /*
     * A validator that fails the first borrow validation outright and parks
     * the second one - the validation of the session the setup path had just
     * reconnected - once armed: the parked moment is the gap between the
     * setup's closed check (which ran before the reconnect) and its publish
     * into busy.
     */
    public static class FailThenParkReconnectValidator implements Validator {

        static final AtomicBoolean ARMED = new AtomicBoolean();
        static final CountDownLatch PARKED = new CountDownLatch(1);
        static final CountDownLatch RELEASE = new CountDownLatch(1);
        static final AtomicInteger CALLS = new AtomicInteger();

        @Override
        public boolean validate(Connection connection, int validateAction) {
            if (validateAction == PooledConnection.VALIDATE_BORROW) {
                int call = CALLS.incrementAndGet();
                if (call == 1) {
                    // drive the setup into the reconnect branch
                    return false;
                }
                if (call == 2 && ARMED.compareAndSet(true, false)) {
                    PARKED.countDown();
                    try {
                        RELEASE.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            return true;
        }
    }

    @Test
    void aReconnectedSessionFinishingAfterTheCloseIsNotServedEither() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxIdle(1);
        ds.getPoolProperties().setFairQueue(true);
        ds.getPoolProperties().setTestOnBorrow(true);
        ds.getPoolProperties().setValidationInterval(0);
        ds.getPoolProperties().setValidator(new FailThenParkReconnectValidator());
        ds.getPoolProperties().setMaxWait(2000); // bounded, never expected to elapse
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1); // sweeper off
        ds.createPool();
        ConnectionPool pool = ds.getPool();
        FailThenParkReconnectValidator.ARMED.set(true);

        Connection held = ds.getConnection();
        awaitSessions(1);

        AtomicReference<Throwable> borrowFailure = new AtomicReference<>();
        AtomicReference<Connection> served = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            try {
                served.set(ds.getConnection());
            } catch (Throwable x) {
                borrowFailure.set(x);
            }
        }, "queued-borrower");
        waiter.setDaemon(true);
        waiter.start();
        awaitUntil(() -> pool.getWaitCount() > 0);

        // The hand-off fails its first validation, so the setup reconnects -
        // passing its closed check while the pool is still open - and parks
        // in the validation of the freshly established session.
        held.close();
        Assertions.assertTrue(FailThenParkReconnectValidator.PARKED.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "the setup must have reached the validation of the reconnected session");
        awaitSessions(1); // the new session is up, held only by the setup

        // The close arrives after that closed check: it drains nothing (the
        // connection lives in the setup's hand) and completes.
        ds.close();

        FailThenParkReconnectValidator.RELEASE.countDown();
        waiter.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(waiter.isAlive(), "the borrow must complete");
        Assertions.assertNotNull(borrowFailure.get(),
                "a session reconnected past the close must not be handed out, got " + served.get());
        Assertions.assertNull(served.get(), "no connection may be served after the close");
        Assertions.assertEquals(0, pool.getSize(),
                "the retracted reconnect must count as retired");
        awaitSessions(0);
    }

    /*
     * A fair queue that parks the first offer once armed: the offered item
     * is held inside the hand-over, so a full close can complete - with the
     * queue drained empty - before the offer actually lands.
     */
    private static final class ParkOnOfferQueue extends FairBlockingQueue<PooledConnection> {

        private final AtomicBoolean armed = new AtomicBoolean();
        private final CountDownLatch parked = new CountDownLatch(1);
        private final CountDownLatch go = new CountDownLatch(1);

        @Override
        public boolean offer(PooledConnection e) {
            if (armed.compareAndSet(true, false)) {
                parked.countDown();
                try {
                    go.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e2) {
                    Thread.currentThread().interrupt();
                }
            }
            return super.offer(e);
        }

        void arm() {
            armed.set(true);
        }

        boolean awaitParked() throws InterruptedException {
            return parked.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        void proceed() {
            go.countDown();
        }
    }

    @Test
    void placeholderOfferedAfterTheDrainIsReclaimedAndItsSlotHandedBack() throws Exception {
        DataSource ds = manage(new DataSource());
        ds.setUrl(getUrl());
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        PoolConfiguration props = ds.getPoolProperties();
        props.setJmxEnabled(false);
        props.setInitialSize(0);
        props.setMinIdle(0);
        props.setMaxActive(1);
        props.setMaxIdle(1);
        props.setFairQueue(true);
        props.setMaxWait(2000); // bounded; the wait is failed by the close itself
        props.setTimeBetweenEvictionRunsMillis(-1); // sweeper off
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        // Swap in the parking queue before any caller can touch it.
        ParkOnOfferQueue queue = new ParkOnOfferQueue();
        Field idleField = ConnectionPool.class.getDeclaredField("idle");
        idleField.setAccessible(true);
        idleField.set(pool, queue);

        Connection holder = ds.getConnection(); // takes the only slot
        PooledConnection pc = interceptorOf(holder, ProxyConnection.class).getConnection();
        awaitSessions(1);

        AtomicReference<Throwable> waitFailure = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            try {
                ds.getConnection(); // parks on the queue, holding a wait count
            } catch (Throwable x) {
                waitFailure.set(x);
            }
        }, "queued-borrower");
        waiter.setDaemon(true);
        waiter.start();
        awaitUntil(() -> pool.getWaitCount() > 0);

        // Destroy the held connection while the waiter is registered: the
        // release claims the freed slot for a wake-up placeholder and parks
        // inside the offer of that placeholder, after its closed check.
        queue.arm();
        AtomicReference<Throwable> destroyFailure = new AtomicReference<>();
        Thread destroyer = new Thread(() -> {
            try {
                pool.release(pc);
            } catch (Throwable x) {
                destroyFailure.set(x);
            }
        }, "destroying-thread");
        destroyer.setDaemon(true);
        destroyer.start();
        Assertions.assertTrue(queue.awaitParked(),
                "the destroy's placeholder offer must have reached the hand-over");

        // Complete the close: the waiter's latch is failed and it leaves with
        // the pool-closed error; the queues hold nothing for the drain.
        ds.close();
        waiter.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(waiter.isAlive(), "the wait must complete");
        Assertions.assertNotNull(waitFailure.get(), "the parked wait must fail on the closed pool");

        queue.proceed();
        destroyer.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(destroyer.isAlive(), "the destroy must complete");
        Assertions.assertNull(destroyFailure.get(),
                "the destroy itself must not fail, got " + destroyFailure.get());

        // The placeholder landed in a queue that had already been drained for
        // the last time: it must be taken back out and its claimed slot
        // handed straight back.
        Assertions.assertEquals(0, pool.getIdle(),
                "the late placeholder must not stay in the closed pool's queue");
        Assertions.assertEquals(0, pool.getSize(),
                "the reclaimed placeholder's slot must be handed back");
        awaitSessions(0);
    }
}
