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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.management.Notification;
import javax.management.NotificationFilter;
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

    /*
     * How many notifications are pushed at the queue in the split test below.
     * Above the queue bound (2000 tasks), so the queue certainly overflows.
     */
    private static final int SPLIT_FLOOD_SIZE = 2500;

    /*
     * The name the pool MBean's notifier thread factory gives its thread; the
     * thread the whole delivery - filters and listeners alike - must run on.
     */
    private static final String NOTIFIER_THREAD = "JDBC-Pool-JMX-Notifier";

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

    /*
     * A JMX notification filter that records the thread evaluating it,
     * signals that it has been reached, stays inside isNotificationEnabled
     * until the test releases it - and never longer than
     * STUCK_LISTENER_TIMEOUT_S, so that a filter evaluated on the reporting
     * thread stalls notify() for that bound and no further.
     */
    private static final class StuckFilter implements NotificationFilter {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch completed = new CountDownLatch(1);
        private final AtomicReference<String> thread = new AtomicReference<>();

        @Override
        public boolean isNotificationEnabled(Notification notification) {
            thread.set(Thread.currentThread().getName());
            entered.countDown();
            try {
                release.await(STUCK_LISTENER_TIMEOUT_S, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            completed.countDown();
            return true;
        }
    }

    /*
     * A listener that records the notifications it receives and blocks the
     * notifier thread on the first one until the test opens the gate - for at
     * most STUCK_LISTENER_TIMEOUT_S, bounding the stuck state as the listener
     * above does - and from then on only records.
     */
    private static final class GateListener implements NotificationListener {
        private final CountDownLatch gate;
        private final CountDownLatch entered = new CountDownLatch(1);
        private final List<Notification> received = new CopyOnWriteArrayList<>();
        private boolean firstHolds = true;

        GateListener(CountDownLatch gate) {
            this.gate = gate;
        }

        @Override
        public void handleNotification(Notification notification, Object handback) {
            received.add(notification);
            if (firstHolds) {
                firstHolds = false;
                entered.countDown();
                try {
                    gate.await(STUCK_LISTENER_TIMEOUT_S, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /*
     * A listener that only records the notifications it receives.
     */
    private static final class RecordingListener implements NotificationListener {
        private final List<Notification> received = new CopyOnWriteArrayList<>();

        @Override
        public void handleNotification(Notification notification, Object handback) {
            received.add(notification);
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

    /*
     * The filter a caller attaches directly to the MBean through the
     * inherited addNotificationListener belongs to the notification's own
     * delivery: the JDK evaluates it inside the hand-off the broadcaster
     * makes for its listener, so while that hand-off ran on the thread which
     * reported the condition, a slow or blocked filter stalled the pool path
     * - with the listener handed off or not. Delivery is now one task that
     * calls sendNotification on the notifier thread, and every filter of the
     * MBean's own listener list is evaluated there, not on the caller.
     */
    @Test
    void aStuckFilterOnADirectlyAttachedListenerDoesNotStallTheThreadThatReports()
            throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setName("JmxDeliveryFilter-" + System.nanoTime());
        ds.createPool();
        // an unregistered MBean is enough: the filter sits on the MBean's own
        // listener list, no MBean server is involved
        ConnectionPool jmxPool = new ConnectionPool(ds.getPool());
        StuckFilter filter = new StuckFilter();
        RecordingListener listener = new RecordingListener();
        jmxPool.addNotificationListener(listener, filter, null);
        try {
            long start = System.currentTimeMillis();
            boolean reported = jmxPool.notify(ConnectionPool.NOTIFY_ABANDON,
                    "a connection was abandoned");
            long elapsedMs = System.currentTimeMillis() - start;

            Assertions.assertTrue(reported,
                    "the notification must be reported as handed off for delivery");
            Assertions.assertTrue(filter.entered.await(5, TimeUnit.SECONDS),
                    "the notification must still reach the filter");
            Assertions.assertTrue(elapsedMs < CALLER_MUST_RETURN_WITHIN_MS,
                    "reporting a condition must not wait for the filter: notify() took "
                            + elapsedMs + " ms, of the " + STUCK_LISTENER_TIMEOUT_S
                            + " s the filter holds on, so the filter ran on the"
                            + " thread that reported the condition");
            Assertions.assertEquals(1, filter.completed.getCount(),
                    "the filter must still be stuck at the point the caller was released");
        } finally {
            filter.release.countDown();
        }
        Assertions.assertTrue(filter.completed.await(5, TimeUnit.SECONDS),
                "the released filter must have finished evaluating");
        Assertions.assertEquals(NOTIFIER_THREAD, filter.thread.get(),
                "the filter must be evaluated on the pool's notification thread, not on the"
                        + " thread that reported the condition, which was "
                        + Thread.currentThread().getName());
        // the filter let the notification through, so the listener behind it
        // must have been reached on the same delivery
        awaitUntil(() -> !listener.received.isEmpty());
        Assertions.assertEquals(ConnectionPool.NOTIFY_ABANDON, listener.received.get(0).getType(),
                "a notification the filter enabled must reach its listener");
        jmxPool.removeNotificationListener(listener);
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

    /*
     * The overflow drop is a per-notification decision, never a per-listener
     * one. The JMX hand-off used to run on the thread that reported the
     * condition: the JDK broadcaster hands the notification to the delivery
     * queue once per selected JMX listener and lets a full queue throw from
     * the middle of that loop, so on the notification that filled the queue
     * between the JMX hand-off and the local one, notify() reported the
     * notification dropped although some JMX listeners were already
     * scheduled to receive it - and the local listeners never were. Delivery
     * is now one task per notification, whose JMX hand-offs run inline on the
     * notifier thread: the queue accepts or refuses the whole notification,
     * so a notification reported dropped reaches no listener at all, and one
     * reported accepted reaches every JMX listener and every local one once
     * the queue has drained.
     */
    @Test
    void aSaturatedQueueRefusesOrAcceptsTheWholeNotificationNotPartOfIt()
            throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setName("JmxDeliverySplit-" + System.nanoTime());
        ds.createPool();
        // an unregistered MBean is enough: a listener can join its JMX
        // listener list directly, no MBean server is involved
        ConnectionPool jmxPool = new ConnectionPool(ds.getPool());
        try {
            /*
             * The notifier and its queue are shared JVM-wide, so first let
             * whatever an earlier test queued on it drain: once a probe
             * notification is raised on its own wrapper and received, the
             * notifier thread has finished everything that was queued before
             * it. The probe wrapper keeps the probe deliveries off the
             * listener lists of the wrapper under test.
             */
            ConnectionPool probePool = new ConnectionPool(ds.getPool());
            RecordingListener probe = new RecordingListener();
            probePool.addListener(probe);
            awaitUntil(() -> {
                probePool.notify(ConnectionPool.POOL_EMPTY, "drain-probe");
                return !probe.received.isEmpty();
            });
            probePool.removeListener(probe);

            CountDownLatch gate = new CountDownLatch(1);
            GateListener jmxOne = new GateListener(gate);
            RecordingListener jmxTwo = new RecordingListener();
            RecordingListener jmxThree = new RecordingListener();
            RecordingListener local = new RecordingListener();
            // three JMX listeners: the old calling-thread fan-out queued four
            // hand-offs for every notification (one per JMX listener plus the
            // local one), so with the three hand-offs of the gate still
            // queued the bound was reached partway through a later
            // notification - some of its listeners scheduled although
            // notify() reported it dropped - rather than cleanly between two
            // notifications; that split is what the audit below trips on
            jmxPool.addNotificationListener(jmxOne, null, null);
            jmxPool.addNotificationListener(jmxTwo, null, null);
            jmxPool.addNotificationListener(jmxThree, null, null);
            jmxPool.addListener(local);
            List<Boolean> accepted = new ArrayList<>();
            try {
                // the gate notification gets the notifier thread out of its
                // idle state; it is then stuck delivering this very
                // notification, and by the time it signals that it got in,
                // the shared queue holds nothing but what this test queues
                Assertions.assertTrue(
                        jmxPool.notify(ConnectionPool.POOL_EMPTY, "gate"),
                        "the notification that gates this test must be accepted");
                Assertions.assertTrue(jmxOne.entered.await(5, TimeUnit.SECONDS),
                        "the notifier thread must be stuck on the gate notification");
                for (int i = 0; i < SPLIT_FLOOD_SIZE; i++) {
                    accepted.add(jmxPool.notify(ConnectionPool.POOL_EMPTY, "split-" + i));
                }
            } finally {
                gate.countDown();
            }
            int handedOff = 0;
            int dropped = 0;
            for (boolean reported : accepted) {
                if (reported) {
                    handedOff++;
                } else {
                    dropped++;
                }
            }
            Assertions.assertTrue(dropped > 0,
                    "a queue that nothing drains must start dropping: all " + SPLIT_FLOOD_SIZE
                            + " notifications were accepted, so the queue is unbounded");
            Assertions.assertTrue(handedOff > 0,
                    "before the queue is full, the notifications must be handed off");

            // drain what the queue took; the local listener is delivered in
            // the notification's own task, so once it has every accepted
            // notification, every task the queue took has run
            int expected = 1 + handedOff;
            awaitUntil(() -> local.received.size() >= expected);
            Assertions.assertEquals(expected, local.received.size(),
                    "every accepted notification must have reached the local listener, and no"
                            + " refused one may reach it");

            /*
             * Let the queue drain completely before reading the JMX
             * listeners' books: a per-listener hand-off that the old calling-
             * thread fan-out had already queued when the notification as a
             * whole was refused is itself queued behind the last accepted
             * notification and must have run by the time the checks below
             * look for it.
             */
            RecordingListener drained = new RecordingListener();
            probePool.addListener(drained);
            awaitUntil(() -> {
                probePool.notify(ConnectionPool.POOL_EMPTY, "drained-probe");
                return !drained.received.isEmpty();
            });
            probePool.removeListener(drained);

            /*
             * Every notification the queue accepted must have reached every
             * listener, and one it refused must have reached none: the
             * accepted task hands the notification to the JMX listeners
             * inline before the local ones, so no per-listener hand-off is
             * ever put at the queue's bound a second time, and the drain
             * above has by now run every task the queue took.
             */
            Set<String> byJmxOne = new HashSet<>(messages(jmxOne.received));
            Set<String> byJmxTwo = new HashSet<>(messages(jmxTwo.received));
            Set<String> byJmxThree = new HashSet<>(messages(jmxThree.received));
            Set<String> byLocal = new HashSet<>(messages(local.received));
            for (int i = 0; i < accepted.size(); i++) {
                String message = "[" + ConnectionPool.POOL_EMPTY + "] split-" + i;
                if (accepted.get(i)) {
                    Assertions.assertTrue(byJmxOne.contains(message),
                            "the notification reported as sent must reach every JMX listener: split-" + i
                                    + " was accepted but the first JMX listener did not receive it");
                    Assertions.assertTrue(byJmxTwo.contains(message),
                            "the notification reported as sent must reach every JMX listener: split-" + i
                                    + " was accepted but the second JMX listener did not receive it");
                    Assertions.assertTrue(byJmxThree.contains(message),
                            "the notification reported as sent must reach every JMX listener: split-" + i
                                    + " was accepted but the third JMX listener did not receive it");
                    Assertions.assertTrue(byLocal.contains(message),
                            "the notification reported as sent must reach the local listener: split-" + i
                                    + " was accepted but the local listener did not receive it");
                } else {
                    Assertions.assertFalse(byJmxOne.contains(message),
                            "the notification reported as dropped must reach no listener: the first JMX listener"
                                    + " received split-" + i + " although notify() reported it dropped");
                    Assertions.assertFalse(byJmxTwo.contains(message),
                            "the notification reported as dropped must reach no listener: the second JMX listener"
                                    + " received split-" + i + " although notify() reported it dropped");
                    Assertions.assertFalse(byJmxThree.contains(message),
                            "the notification reported as dropped must reach no listener: the third JMX listener"
                                    + " received split-" + i + " although notify() reported it dropped");
                    Assertions.assertFalse(byLocal.contains(message),
                            "the notification reported as dropped must reach no listener: the local listener"
                                    + " received split-" + i + " although notify() reported it dropped");
                }
            }
            Assertions.assertTrue(jmxPool.notify(ConnectionPool.POOL_EMPTY, "final"),
                    "a notification reported after the queue drained must be accepted");
            String finalMessage = "[" + ConnectionPool.POOL_EMPTY + "] final";
            awaitUntil(() -> containsMessage(jmxOne.received, finalMessage)
                    && containsMessage(jmxTwo.received, finalMessage)
                    && containsMessage(jmxThree.received, finalMessage)
                    && containsMessage(local.received, finalMessage));

            jmxPool.removeNotificationListener(jmxOne);
            jmxPool.removeNotificationListener(jmxTwo);
            jmxPool.removeNotificationListener(jmxThree);
            jmxPool.removeListener(local);
        } finally {
            ds.close();
        }
    }

    private static List<String> messages(List<Notification> received) {
        List<String> messages = new ArrayList<>(received.size());
        for (Notification n : received) {
            messages.add(n.getMessage());
        }
        return messages;
    }

    private static boolean containsMessage(List<Notification> received, String message) {
        for (Notification n : received) {
            if (message.equals(n.getMessage())) {
                return true;
            }
        }
        return false;
    }
}
