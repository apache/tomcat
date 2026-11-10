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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.NotificationListener;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A connection is reported as suspect - logged and notified through JMX -
 * exactly once, which is what the {@code suspect} flag on the pooled
 * connection exists to guarantee, and what the later return-site reporting
 * keys off as well. But the flag's dedup guard in
 * {@code ConnectionPool.suspect()} ran outside the connection lock, and
 * {@code checkAbandoned()} has more than one driver: the cleaner timer thread
 * and a JMX-invoked one run it side by side routinely. Both pass the guard
 * while the flag is still false, and both then queue onto the lock that does
 * the marking - without waiting for it to re-check - so the connection is
 * reported twice: two warnings and two {@code SUSPECT_ABANDONED}
 * notifications for one borrow overrun, double-alerting anything wired to
 * the notification.
 * <p>
 * The test parks the first driver inside the guard's read of the flag - a
 * seam through the connection's own overridable {@code isSuspect()} - and
 * lets the second driver run to completion, reproducing the interleaving in
 * which both have passed the unlocked guard. The witness is the JMX
 * notification listener: one suspect marking may produce exactly one
 * notification.
 */
class TestSuspectReportedOnce extends JdbcPoolBaseTest {

    /*
     * A connection whose first unlocked guard read parks, standing in for the
     * first driver being preempted between the guard and the lock. The park
     * is one-shot: any later read - the re-check under the lock the fix adds
     * - passes straight through to the real flag.
     */
    static class GuardParkingConnection extends PooledConnection {

        final CountDownLatch guardEntered = new CountDownLatch(1);
        final CountDownLatch guardReleased = new CountDownLatch(1);
        private final AtomicBoolean parkNextRead = new AtomicBoolean(true);

        GuardParkingConnection(PoolConfiguration prop, ConnectionPool parent) {
            super(prop, parent);
        }

        @Override
        public boolean isSuspect() {
            // The flag is read before the park; the parked call returns that
            // value. The driver being parked is what stands preempted - the
            // flag itself moves on while it is out of the way.
            boolean read = super.isSuspect();
            if (parkNextRead.compareAndSet(true, false)) {
                guardEntered.countDown();
                try {
                    guardReleased.await(AWAIT_TIMEOUT_MS * 4L, TimeUnit.MILLISECONDS);
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                }
            }
            return read;
        }
    }

    private static PoolConfiguration newPoolProperties() {
        PoolProperties props = new PoolProperties();
        props.setUrl(getUrl());
        props.setDriverClassName("org.h2.Driver");
        props.setUsername("sa");
        props.setPassword("");
        props.setName("suspect-dedup-" + System.nanoTime());
        // The wrapper's existence is what lets suspect() notify at all;
        // publishing it to the platform server is not needed.
        props.setJmxEnabled(true);
        props.setInitialSize(0);
        props.setMinIdle(0);
        return props;
    }

    @Test
    void twoConcurrentSuspectsReportTheConnectionOnce() throws Exception {
        ConnectionPool pool = new ConnectionPool(newPoolProperties());
        org.apache.tomcat.jdbc.pool.jmx.ConnectionPool wrapper = pool.getJmxPool();
        Assertions.assertNotNull(wrapper, "the pool must have a JMX object to notify through");
        AtomicInteger suspectNotifications = new AtomicInteger();
        NotificationListener listener = (notification, handback) -> {
            if (org.apache.tomcat.jdbc.pool.jmx.ConnectionPool.SUSPECT_ABANDONED_NOTIFICATION
                    .equals(notification.getType())) {
                suspectNotifications.incrementAndGet();
            }
        };
        wrapper.addListener(listener);
        try {
            // suspect() reports whatever it is given; the dedup is about the
            // flag, not about a live session, so a pooled connection that was
            // never connected is enough to drive both drivers.
            GuardParkingConnection con = new GuardParkingConnection(
                    pool.getPoolProperties(), pool);

            CountDownLatch firstDone = new CountDownLatch(1);
            final Throwable[] firstFailure = new Throwable[1];
            Thread first = new Thread(() -> {
                try {
                    pool.suspect(con);
                } catch (Throwable t) {
                    firstFailure[0] = t;
                } finally {
                    firstDone.countDown();
                }
            }, "suspect-first");
            first.start();
            Assertions.assertTrue(con.guardEntered.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                    "the first driver must reach the unlocked guard read");

            // The second driver passes the same guard - the flag is still
            // false - runs to completion under the lock, and marks the
            // connection.
            Thread second = new Thread(() -> pool.suspect(con), "suspect-second");
            second.start();
            second.join(AWAIT_TIMEOUT_MS);
            Assertions.assertFalse(second.isAlive(), "the second driver must complete");
            // Delivery rides the pool's single notifier thread, so waiting for
            // the notification is how the test learns the second driver
            // reported; from here on the count only grows if another one was
            // accepted, and the notifier drains them in order, so a short
            // grace after the first driver finishes is enough to see it.
            awaitUntil(() -> suspectNotifications.get() >= 1);
            Assertions.assertTrue(con.isSuspect(), "the connection must be marked suspect");

            // Now let the first driver walk the rest of its path - the path
            // that reports without consulting the flag again.
            con.guardReleased.countDown();
            Assertions.assertTrue(firstDone.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                    "the first driver must complete");
            first.join(AWAIT_TIMEOUT_MS);

            Assertions.assertNull(firstFailure[0],
                    "the first driver must not fail, failed with: " + firstFailure[0]);
            // Whatever the first driver's suspect() accepted is already on
            // the ordered notifier by now; let it drain before counting.
            sleep(250L);
            Assertions.assertEquals(1, suspectNotifications.get(),
                    "one suspect marking must produce exactly one notification");
        } finally {
            wrapper.removeListener(listener);
            pool.close(true);
        }
    }
}
