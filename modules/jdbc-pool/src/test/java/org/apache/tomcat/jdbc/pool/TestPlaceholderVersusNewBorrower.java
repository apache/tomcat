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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A destroyed connection hands its slot back to the pool and the wake-up
 * placeholder for a waiting borrower re-claims that slot. The hand-back and
 * the re-claim are separate operations on the size counter, and in between
 * them the freed slot is fair game for a new borrower - if the placeholder
 * re-claims unconditionally, the pool runs one real session over its
 * maxActive limit while both claims stand.
 */
class TestPlaceholderVersusNewBorrower extends JdbcPoolBaseTest {

    /*
     * An interceptor whose chain is discarded - reset called with no parent -
     * exactly once per connection: when it is retired, right after the
     * release decremented the pool size and before that release offers its
     * wake-up placeholder. Armed once, it parks that moment so the test can
     * run a new borrower through the freed slot. It must stay a public
     * static class with a no-arg constructor, as the pool instantiates
     * interceptors configured by class name.
     */
    public static class ParkOnRetireInterceptor extends JdbcInterceptor {

        static final AtomicBoolean ARMED = new AtomicBoolean();
        static final AtomicInteger PARENTLESS_RESETS = new AtomicInteger();
        static final CountDownLatch PARKED = new CountDownLatch(1);
        static final CountDownLatch GO = new CountDownLatch(1);

        static void arm() {
            PARENTLESS_RESETS.set(0);
            ARMED.set(true);
        }

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // A retiring connection's chain is reset with no parent once,
            // when the release clears it after freeing the pool slot. That
            // firing sits exactly between the slot's release and the wake-up
            // placeholder.
            if (parent == null && ARMED.get() && PARENTLESS_RESETS.incrementAndGet() == 1) {
                PARKED.countDown();
                try {
                    GO.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Test
    void placeholderMustNotClaimASlotThatTheFreedOneAlreadyFound() throws Exception {
        DataSource ds = manage(new DataSource());
        ds.setUrl(getUrl());
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        PoolConfiguration props = ds.getPoolProperties();
        props.setJmxEnabled(false);
        props.setInitialSize(0);
        props.setMinIdle(0);
        props.setMaxActive(2);
        props.setMaxIdle(2);
        props.setMaxWait(30000);
        props.setTimeBetweenEvictionRunsMillis(-1); // sweeper off
        props.setJdbcInterceptors(ParkOnRetireInterceptor.class.getName());
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection first = ds.getConnection(); // slot one
        Connection second = ds.getConnection(); // slot two
        awaitSessions(2);

        AtomicReference<Connection> served = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            try {
                served.set(ds.getConnection());
            } catch (Throwable x) {
                failure.set(x);
            }
        }, "waiting-borrower");
        waiter.setDaemon(true);
        waiter.start();
        awaitUntil(() -> ds.getWaitCount() == 1);

        // Discard the first connection on its return; the retirement parks
        // after the slot was freed and before the placeholder is offered.
        interceptorOf(first, ProxyConnection.class).getConnection().setDiscarded(true);
        ParkOnRetireInterceptor.arm();
        Thread retirer = new Thread(() -> {
            try {
                first.close();
            } catch (Throwable ignored) {
                // the retirement itself must not fail; not what is observed
            }
        }, "retiring-thread");
        retirer.setDaemon(true);
        retirer.start();
        Assertions.assertTrue(ParkOnRetireInterceptor.PARKED.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "the retirement must have parked between freeing the slot and re-claiming it");

        // A new borrower legitimately claims the just-freed slot
        Connection third = ds.getConnection();
        Assertions.assertEquals(2, pool.getSize(),
                "the new borrower should have claimed the freed slot");

        // The retirement continues. The slot it meant for the placeholder is
        // taken, so the placeholder must not stand up a second one.
        ParkOnRetireInterceptor.GO.countDown();
        retirer.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(retirer.isAlive(), "the retirement must complete");
        sleep(250); // let any placeholder be offered and woken

        Assertions.assertEquals(2, pool.getSize(),
                "the placeholder must not double the slot alongside the borrower that took it");
        Assertions.assertEquals(2, poolSessions(),
                "the pool must not run over maxActive through the placeholder race");

        // The waiting borrower is served when the second connection returns.
        second.close();
        waiter.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(waiter.isAlive(), "the waiter must complete");
        Assertions.assertNull(failure.get(), "the waiter must be served, got " + failure.get());
        Assertions.assertNotNull(served.get(), "the waiter must have been served");

        served.get().close();
        third.close();
        ds.close();
        awaitSessions(0);
    }
}
