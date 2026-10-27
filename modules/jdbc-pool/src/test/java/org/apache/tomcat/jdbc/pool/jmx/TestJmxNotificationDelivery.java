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
package org.apache.tomcat.jdbc.pool.jmx;

import java.sql.Connection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.management.Notification;
import javax.management.NotificationListener;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolExhaustedException;

/**
 * Pool notifications are delivered off the thread that reports the condition.
 * <p>
 * Several of the pool's notifications are raised from the connection
 * lifecycle, some of them while the pool holds the lock of the connection
 * concerned - the abandon, suspect and suspect-returned paths and the
 * connect-failure path - and a failed borrow on an exhausted pool reports
 * POOL_EMPTY once per miss. When delivery ran on the calling thread, one slow
 * or blocked monitoring listener therefore stalled connection lifecycle
 * operations and amplified the load precisely when the pool was saturated.
 * Delivery now goes through the pool's single notifier thread, and a
 * notification that no longer fits the bounded delivery queue is dropped and
 * logged rather than queued or thrown into the reporting thread.</p>
 * <p>
 * The stuck listener used below always bounds its own wait, so a regression
 * to synchronous delivery fails these tests on the timeout bound instead of
 * hanging the suite.</p>
 */
class TestJmxNotificationDelivery extends JdbcPoolBaseTest {

    /*
     * Upper bound on how long the thread that reports a condition may take,
     * measured against a listener that stays in handleNotification for
     * STUCK_LISTENER_TIMEOUT_S. Far below the stuck interval on a healthy
     * handoff, at or above it on a delivery that runs on the caller.
     */
    private static final long CALLER_MUST_RETURN_WITHIN_MS = 5000;

    /* How long the stuck listener holds on at most; bounds the stuck state. */
    private static final long STUCK_LISTENER_TIMEOUT_S = 10;

    /* How many notifications are pushed at a queue nobody is draining. */
    private static final int FLOOD_SIZE = 4000;

    /**
     * Every notification type the pool MBean advertises, in the order the
     * burst test reports them.
     */
    private static final String[] ALL_TYPES = {
            ConnectionPool.NOTIFY_INIT,
            ConnectionPool.NOTIFY_CONNECT,
            ConnectionPool.NOTIFY_ABANDON,
            ConnectionPool.SLOW_QUERY_NOTIFICATION,
            ConnectionPool.FAILED_QUERY_NOTIFICATION,
            ConnectionPool.SUSPECT_ABANDONED_NOTIFICATION,
            ConnectionPool.POOL_EMPTY,
            ConnectionPool.SUSPECT_RETURNED_NOTIFICATION };

    /*
     * A listener that records the notifications it receives, signals that it
     * has been reached, stays inside handleNotification until the test
     * releases it - and never longer than STUCK_LISTENER_TIMEOUT_S, so that a
     * delivery running on the caller thread of notify() stalls for that bound
     * and no further.
     */
    private static final class StuckListener implements NotificationListener {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch completed = new CountDownLatch(1);
        private final List<Notification> received = new CopyOnWriteArrayList<>();

        @Override
        public void handleNotification(Notification notification, Object handback) {
            received.add(notification);
            entered.countDown();
            try {
                release.await(STUCK_LISTENER_TIMEOUT_S, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            completed.countDown();
        }
    }

    @Test
    void aStuckLocalListenerDoesNotStallTheThreadThatReports() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setName("JmxDeliveryStuck-" + System.nanoTime());
        ds.createPool();
        // an unregistered MBean is enough: this is about the local fan-out
        ConnectionPool jmxPool = new ConnectionPool(ds.getPool());
        StuckListener stuck = new StuckListener();
        jmxPool.addListener(stuck);
        try {
            long start = System.currentTimeMillis();
            boolean reported = jmxPool.notify(ConnectionPool.NOTIFY_ABANDON,
                    "a connection was abandoned");
            long elapsedMs = System.currentTimeMillis() - start;

            Assertions.assertTrue(reported,
                    "the notification must be reported as handed off for delivery");
            Assertions.assertTrue(stuck.entered.await(5, TimeUnit.SECONDS),
                    "the notification must still reach the listener");
            Assertions.assertTrue(elapsedMs < CALLER_MUST_RETURN_WITHIN_MS,
                    "reporting a condition must not wait for the listener: notify() took "
                            + elapsedMs + " ms, of the " + STUCK_LISTENER_TIMEOUT_S
                            + " s the listener holds on, so the delivery ran on the"
                            + " thread that reported the condition");
            Assertions.assertEquals(1, stuck.completed.getCount(),
                    "the listener must still be stuck at the point the caller was released");
        } finally {
            stuck.release.countDown();
        }
        Assertions.assertTrue(stuck.completed.await(5, TimeUnit.SECONDS),
                "the released listener must have finished handling the notification");
        jmxPool.removeListener(stuck);
    }

    @Test
    void aFailedBorrowOnAnExhaustedPoolReportsWithoutStallingTheBorrowThread()
            throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJmxEnabled(true);
        ds.getPoolProperties().setName("JmxDeliveryEmpty-" + System.nanoTime());
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setInitialSize(1);
        // a small positive wait, so an exhausted pool reports the empty pool
        // once its borrower gives up - note that maxWait<=0 means to wait
        // forever, not to wait not at all, so 0 here would never time out
        ds.getPoolProperties().setMaxWait(250);
        ds.createPool();
        ConnectionPool jmxPool = ds.getPool().getJmxPool();
        Assertions.assertNotNull(jmxPool, "the fixture must have created the pool MBean");
        StuckListener stuck = new StuckListener();
        jmxPool.addListener(stuck);
        long elapsedMs;
        try (Connection held = ds.getConnection()) {
            PoolExhaustedException exhausted = null;
            long start = System.currentTimeMillis();
            try {
                ds.getConnection();
            } catch (PoolExhaustedException expected) {
                exhausted = expected;
            }
            elapsedMs = System.currentTimeMillis() - start;

            Assertions.assertNotNull(exhausted,
                    "a borrow that times out on an exhausted pool must fail");
            Assertions.assertTrue(stuck.entered.await(5, TimeUnit.SECONDS),
                    "the failed borrow must still report POOL EMPTY");
            Assertions.assertTrue(elapsedMs < CALLER_MUST_RETURN_WITHIN_MS,
                    "the failed borrow must not wait for the listener: it took " + elapsedMs
                            + " ms, of the " + STUCK_LISTENER_TIMEOUT_S + " s the listener"
                            + " holds on, so the notification ran on the borrow thread");
            Assertions.assertEquals(1, stuck.completed.getCount(),
                    "the listener must still be stuck at the point the borrow returned");
        } finally {
            stuck.release.countDown();
        }
        awaitUntil(() -> !stuck.received.isEmpty());
        Assertions.assertEquals(ConnectionPool.POOL_EMPTY, stuck.received.get(0).getType(),
                "what the failed borrow reported must be the pool-empty notification");
        jmxPool.removeListener(stuck);
    }

    /*
     * The order the pool reported its notifications in is the order the
     * listeners must observe them in, across a burst covering every type. A
     * contract pin rather than a regression pin: a synchronous fan-out also
     * delivers in order, and the fix must not lose that.
     */
    @Test
    void everyTypeOfABurstArrivesInReportingOrder() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setName("JmxDeliveryBurst-" + System.nanoTime());
        ds.createPool();
        ConnectionPool jmxPool = new ConnectionPool(ds.getPool());
        List<Notification> received = new CopyOnWriteArrayList<>();
        NotificationListener listener = (notification, handback) -> received.add(notification);
        jmxPool.addListener(listener);
        try {
            for (String type : ALL_TYPES) {
                Assertions.assertTrue(jmxPool.notify(type, "burst " + type),
                        "the notification of type [" + type + "] must be reported as sent");
            }

            awaitUntil(() -> received.size() >= ALL_TYPES.length);
            Assertions.assertEquals(ALL_TYPES.length, received.size(),
                    "every type of the burst must have reached the listener");
            long previousSequence = -1;
            for (int i = 0; i < ALL_TYPES.length; i++) {
                Notification notification = received.get(i);
                Assertions.assertEquals(ALL_TYPES[i], notification.getType(),
                        "position " + i + " of the burst must be the type reported at"
                                + " position " + i + ", delivery must not reorder");
                Assertions.assertTrue(notification.getSequenceNumber() > previousSequence,
                        "sequence numbers must keep increasing across the burst, got "
                                + notification.getSequenceNumber() + " after "
                                + previousSequence);
                previousSequence = notification.getSequenceNumber();
            }
        } finally {
            jmxPool.removeListener(listener);
        }
    }

    @Test
    void aDeliveryQueueNobodyDrainsDropsInsteadOfStallingOrFailingThePool()
            throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setName("JmxDeliveryOverflow-" + System.nanoTime());
        ds.createPool();
        ConnectionPool jmxPool = new ConnectionPool(ds.getPool());
        StuckListener stuck = new StuckListener();
        jmxPool.addListener(stuck);
        int handedOff = 0;
        int dropped = 0;
        try {
            for (int i = 0; i < FLOOD_SIZE; i++) {
                long start = System.currentTimeMillis();
                boolean reported;
                try {
                    reported = jmxPool.notify(ConnectionPool.POOL_EMPTY, "overflow " + i);
                } catch (RuntimeException x) {
                    throw new AssertionError("notify() must not throw into the thread that"
                            + " reports the condition", x);
                }
                long elapsedMs = System.currentTimeMillis() - start;
                Assertions.assertTrue(elapsedMs < CALLER_MUST_RETURN_WITHIN_MS,
                        "reporting a condition onto an undrainable queue must not block:"
                                + " notify() call " + i + " took " + elapsedMs + " ms");
                if (reported) {
                    handedOff++;
                } else {
                    dropped++;
                }
            }

            Assertions.assertTrue(stuck.entered.await(5, TimeUnit.SECONDS),
                    "the first notification must have reached the listener");
            // the queue is bounded, so a listener that does not drain it
            // cannot make the pool accept the entire flood
            Assertions.assertTrue(dropped > 0,
                    "a queue that nothing drains must start dropping once full: all "
                            + FLOOD_SIZE + " notifications were accepted, so the queue"
                            + " is unbounded");
            Assertions.assertTrue(handedOff > 0,
                    "before the queue is full, the notifications must be handed off");
        } finally {
            stuck.release.countDown();
        }
        awaitUntil(() -> !stuck.received.isEmpty());
        Assertions.assertEquals(ConnectionPool.POOL_EMPTY, stuck.received.get(0).getType(),
                "the first notification must have been delivered, not dropped");
        jmxPool.removeListener(stuck);
    }
}
