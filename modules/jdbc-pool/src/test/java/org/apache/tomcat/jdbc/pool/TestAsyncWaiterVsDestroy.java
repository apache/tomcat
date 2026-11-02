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
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A caller parked in the {@link Future#get()} of an asynchronous borrow is
 * registered as a waiter on the idle queue, but the pool's wait counter only
 * counts synchronous borrowers. When a destroyed connection opens a slot,
 * the wake-up placeholder that a counted thread would have been handed must
 * reach these waiters too - or they park forever although the pool sits
 * below its limit with nothing in its queues.
 */
class TestAsyncWaiterVsDestroy extends JdbcPoolBaseTest {

    /*
     * A fair queue that parks the first asynchronous poll once armed, in the
     * gap between the failed no-wait borrow attempt and the registration of
     * the queue waiter: a connection destroyed while the caller stands there
     * sees neither the wait counter (the attempt released its hold) nor a
     * registered waiter (the poll has not arrived yet), and the future would
     * park forever on a slot nobody wakes it for.
     */
    private static final class ParkOnPollAsyncQueue extends FairBlockingQueue<PooledConnection> {

        private final AtomicBoolean armed = new AtomicBoolean();
        private final CountDownLatch parked = new CountDownLatch(1);
        private final CountDownLatch go = new CountDownLatch(1);

        @Override
        public Future<PooledConnection> pollAsync() {
            if (armed.compareAndSet(true, false)) {
                parked.countDown();
                try {
                    go.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return super.pollAsync();
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
    void destroyingTheHeldConnectionWakesTheAsyncWaiter() throws Exception {
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
        props.setTimeBetweenEvictionRunsMillis(-1); // sweeper off
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection holder = ds.getConnection(); // takes the only slot
        PooledConnection pc = interceptorOf(holder, ProxyConnection.class).getConnection();
        awaitSessions(1);

        Future<Connection> future = ds.getConnectionAsync();

        // Destroy the held connection: its release opens the pool's only
        // slot, and the async waiter is the only consumer that could take
        // the wake-up placeholder for it. The pool's wait count sees nothing
        // - the async waiter never increments it.
        Assertions.assertEquals(0, pool.getWaitCount(),
                "the scenario only bites where the pool wait count misses the waiter");
        pool.release(pc);

        Connection served = future.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        Assertions.assertNotNull(served, "the async waiter must have been woken");
        try (Statement st = served.createStatement();
                ResultSet rs = st.executeQuery("SELECT 1")) {
            Assertions.assertTrue(rs.next(), "the woken waiter must hold a live session");
        }
        served.close();
        holder.close();
        ds.close();
        awaitSessions(0);
    }

    @Test
    void aDestroyLandingInTheGapBeforeAsyncRegistrationIsStillDelivered() throws Exception {
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
        props.setTimeBetweenEvictionRunsMillis(-1); // sweeper off
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        // Swap in the parking queue before any caller can poll it.
        ParkOnPollAsyncQueue queue = new ParkOnPollAsyncQueue();
        Field idleField = ConnectionPool.class.getDeclaredField("idle");
        idleField.setAccessible(true);
        idleField.set(pool, queue);

        Connection holder = ds.getConnection(); // takes the only slot
        PooledConnection pc = interceptorOf(holder, ProxyConnection.class).getConnection();
        awaitSessions(1);

        queue.arm();
        AtomicReference<Future<Connection>> futureRef = new AtomicReference<>();
        AtomicReference<Throwable> callFailure = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                futureRef.set(ds.getConnectionAsync());
            } catch (Throwable x) {
                callFailure.set(x);
            }
        }, "async-caller");
        caller.setDaemon(true);
        caller.start();
        Assertions.assertTrue(queue.awaitParked(),
                "the async poll must have reached the gap before registering its waiter");

        // Destroy the only connection right in that gap: this release can see
        // neither a wait count nor a registered waiter, yet the slot it frees
        // is exactly the one the parked caller's failed attempt could not take.
        pool.release(pc);

        queue.proceed();
        caller.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(caller.isAlive(), "the async call must complete");
        Assertions.assertNull(callFailure.get(),
                "the async borrow call must not fail, got " + callFailure.get());
        Future<Connection> future = futureRef.get();
        Assertions.assertNotNull(future, "the caller must have produced its future");

        Connection served = future.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        Assertions.assertNotNull(served,
                "the slot freed inside the gap must still reach the caller");
        try (Statement st = served.createStatement();
                ResultSet rs = st.executeQuery("SELECT 1")) {
            Assertions.assertTrue(rs.next(), "the served caller must hold a live session");
        }
        served.close();
        holder.close();
        ds.close();
        awaitSessions(0);
    }
}
