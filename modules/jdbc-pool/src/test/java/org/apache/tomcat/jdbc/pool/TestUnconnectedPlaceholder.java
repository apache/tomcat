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
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A wake-up placeholder that no borrower took stays in the idle queue with
 * no underlying connection. Validating it during an idle sweep must report it
 * as unusable so it is released like any other bad connection - it must not
 * abort the whole sweep cycle, which would leave every idle connection
 * untested for as long as the placeholder lingers.
 */
class TestUnconnectedPlaceholder extends JdbcPoolBaseTest {

    /*
     * Pool properties that notice - and widen - the window inside
     * borrowConnection() after an interrupted queue wait has removed this
     * thread's latch from the queue but before the pool wait count is
     * decremented. getPropagateInterruptState() is consulted exactly there,
     * and (with JMX off and no pool close in flight) nowhere else.
     */
    private static final class InterruptWindowProbe extends PoolProperties {

        private static final long serialVersionUID = 1L;

        private final CountDownLatch inWindow = new CountDownLatch(1);
        private final AtomicBoolean armed = new AtomicBoolean();

        @Override
        public boolean getPropagateInterruptState() {
            if (armed.compareAndSet(true, false)) {
                inWindow.countDown();
                sleep(300);
            }
            return super.getPropagateInterruptState();
        }

        void arm() {
            armed.set(true);
        }

        boolean awaitInWindow() throws InterruptedException {
            return inWindow.await(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void idleSweepRemovesAPlaceholderThatNoBorrowerTook() throws Exception {
        InterruptWindowProbe props = new InterruptWindowProbe();
        props.setUrl(getUrl());
        props.setDriverClassName("org.h2.Driver");
        props.setUsername("sa");
        props.setPassword("");
        props.setJmxEnabled(false);
        props.setInitialSize(0);
        // age-based eviction is switched off, so the placeholder can only
        // ever be removed by the idle validation this test is about
        props.setMinIdle(0);
        props.setMinEvictableIdleTimeMillis(-1);
        props.setMaxActive(1);
        props.setMaxIdle(1);
        // <= 0 means: wait forever for a notification
        props.setMaxWait(-1);
        props.setTestWhileIdle(true);
        props.setTimeBetweenEvictionRunsMillis(200);
        DataSource ds = manage(new DataSource());
        ds.setPoolProperties(props);
        ds.createPool();

        Connection holder = ds.getConnection(); // the only slot is taken
        PooledConnection pc =
                interceptorOf(holder, ProxyConnection.class).getConnection();
        ConnectionPool pool = ds.getPool();

        final Throwable[] waiterFailure = new Throwable[1];
        Thread waiter = new Thread(() -> {
            try {
                ds.getConnection();
            } catch (Throwable x) {
                waiterFailure[0] = x;
            }
        }, "interrupted-waiter");
        waiter.start();
        awaitUntil(() -> ds.getWaitCount() == 1);

        props.arm();
        waiter.interrupt();
        Assertions.assertTrue(props.awaitInWindow(),
                "waiter should have reached the window between removing its "
                        + "queue latch and decrementing the pool wait count");

        // Destroy the connection while the waiter still counts although its
        // latch is gone: the wake-up placeholder is queued into idle and no
        // borrower will ever take it. The holder is deliberately left open;
        // closing it would return the connection a second time through the
        // proxy and muddy what this test observes.
        pool.release(pc);
        waiter.join(AWAIT_TIMEOUT_MS);
        Assertions.assertNotNull(waiterFailure[0],
                "the interrupted waiter must have failed its borrow");

        // The sweeper runs every 200 ms. Validating the never-connected
        // placeholder must not abort the cycle; the placeholder must be
        // released like any other unusable idle connection, so both the
        // idle count and the pool size drop to zero.
        awaitUntil(() -> ds.getIdle() == 0 && ds.getSize() == 0);
        awaitSessions(0);
    }

    @Test
    void aPlaceholdersWholeLifeBalancesTheCreatedAndReleasedCounts() throws Exception {
        InterruptWindowProbe props = new InterruptWindowProbe();
        props.setUrl(getUrl());
        props.setDriverClassName("org.h2.Driver");
        props.setUsername("sa");
        props.setPassword("");
        props.setJmxEnabled(false);
        props.setInitialSize(0);
        props.setMinIdle(0);
        props.setMaxActive(1);
        props.setMaxIdle(1);
        props.setMaxWait(-1);
        props.setTimeBetweenEvictionRunsMillis(-1); // drain at the close instead
        DataSource ds = manage(new DataSource());
        ds.setPoolProperties(props);
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection holder = ds.getConnection(); // the only creation so far
        Assertions.assertEquals(1, pool.getCreatedCount());
        PooledConnection pc = interceptorOf(holder, ProxyConnection.class).getConnection();

        Thread waiter = new Thread(() -> {
            try {
                ds.getConnection();
            } catch (Throwable ignored) {
                // the interrupt ends this borrow; only the placeholder it
                // leaves behind matters here
            }
        }, "interrupted-waiter");
        waiter.start();
        awaitUntil(() -> pool.getWaitCount() == 1);

        props.arm();
        waiter.interrupt();
        Assertions.assertTrue(props.awaitInWindow(),
                "waiter should have reached the window between removing its "
                        + "queue latch and decrementing the pool wait count");

        // Destroy the held connection while the waiter still counts: a
        // wake-up placeholder enters the queue that no borrower will take.
        pool.release(pc);
        waiter.join(AWAIT_TIMEOUT_MS);
        Assertions.assertEquals(1, pool.getReleasedCount(), "the destroyed holder");
        Assertions.assertEquals(1, pool.getIdle(), "the wake-up placeholder is queued");

        // The close drain retires the placeholder through the same release
        // step every other pooled connection leaves through. Its release
        // must have a creation behind it: a placeholder counted nowhere on
        // the way in runs the released count one ahead of the created count
        // for every placeholder the pool ever made, and no lifetime of the
        // pool can reconcile the pair again.
        ds.close();
        Assertions.assertEquals(0, pool.getIdle());
        Assertions.assertEquals(pool.getCreatedCount(), pool.getReleasedCount(),
                "every released connection, the placeholder included, must "
                        + "have been created");
        awaitSessions(0);
    }

    /*
     * Pool properties that notice - and widen - the window inside
     * borrowConnection() between the wait-count increment and the
     * registration of the queue wait: the re-check of the limit inside the
     * counted section consults getMaxActive() exactly there. A borrower
     * held in that window is counted without holding a queue latch, so a
     * placeholder offered while it is held waits in the queue with a real
     * consumer still to come.
     */
    private static final class RegistrationWindowProbe extends PoolProperties {

        private static final long serialVersionUID = 1L;

        private final AtomicBoolean armed = new AtomicBoolean();
        private final CountDownLatch inWindow = new CountDownLatch(1);
        private final CountDownLatch windowDone = new CountDownLatch(1);
        private final AtomicBoolean firstSeen = new AtomicBoolean();

        @Override
        public int getMaxActive() {
            if (armed.get() && firstSeen.compareAndSet(false, true)) {
                // the limit check before the counted section, pass through
                return super.getMaxActive();
            }
            if (armed.compareAndSet(true, false)) {
                inWindow.countDown();
                try {
                    windowDone.await(5, TimeUnit.SECONDS);
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

        void releaseWindow() {
            windowDone.countDown();
        }
    }

    @Test
    void idleSweepMustNotReleaseAPlaceholderWhileItsWaiterIsStillComing() throws Exception {
        RegistrationWindowProbe props = new RegistrationWindowProbe();
        props.setUrl(getUrl());
        props.setDriverClassName("org.h2.Driver");
        props.setUsername("sa");
        props.setPassword("");
        props.setJmxEnabled(false);
        props.setInitialSize(0);
        props.setMinIdle(0);
        // the age rule is what races the placeholder, so keep it on
        props.setMinEvictableIdleTimeMillis(60000);
        props.setMaxActive(1);
        props.setMaxIdle(1);
        props.setMaxWait(3000);
        props.setTimeBetweenEvictionRunsMillis(-1); // sweeper off, sweep manually
        DataSource ds = manage(new DataSource());
        ds.setPoolProperties(props);
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection holder = ds.getConnection(); // the only slot is taken
        PooledConnection pc = interceptorOf(holder, ProxyConnection.class).getConnection();

        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Connection> served = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            try {
                served.set(ds.getConnection());
            } catch (Throwable x) {
                failure.set(x);
            }
        }, "coming-waiter");
        waiter.setDaemon(true);
        props.arm();
        waiter.start();
        Assertions.assertTrue(props.awaitInWindow(),
                "waiter should have reached the counted window before the queue wait");

        // Destroy the connection while the waiter is counted but has not
        // registered its queue latch yet: the wake-up placeholder waits in
        // the idle queue with its consumer still to come.
        pool.release(pc);
        Assertions.assertEquals(1, ds.getIdle(), "the wake-up placeholder");

        // A sweep now - the placeholder is seconds old at most, but its age
        // clock starts at the epoch unless it is stamped.
        pool.checkIdle();

        Assertions.assertEquals(0, pool.getReleasedIdleCount(),
                "the sweep must not release the placeholder meant for the counted waiter");
        Assertions.assertEquals(1, ds.getIdle(),
                "the placeholder must survive a sweep that finds it too young");
        Assertions.assertEquals(1, ds.getSize(),
                "the counted waiter's slot must stay out of the release counters");

        props.releaseWindow();
        waiter.join(AWAIT_TIMEOUT_MS);
        Assertions.assertNull(failure.get(), "the waiter must not fail, got " + failure.get());
        Connection taken = served.get();
        Assertions.assertNotNull(taken, "the waiter must have been served by the placeholder");
        try (Statement probeStatement = taken.createStatement();
                ResultSet rs = probeStatement.executeQuery("SELECT 1")) {
            Assertions.assertTrue(rs.next(), "the woken waiter must hold a live session");
        }
        taken.close();
        holder.close();
        // The connection the waiter was served by is back in the idle
        // queue; only closing the pool retires it, so do it here rather
        // than lean on the teardown.
        ds.close();
        awaitSessions(0);
    }
}
