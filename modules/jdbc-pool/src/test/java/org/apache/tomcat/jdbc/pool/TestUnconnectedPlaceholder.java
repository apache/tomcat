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
}
