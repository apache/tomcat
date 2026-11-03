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
package org.apache.tomcat.jdbc.pool.interceptor;

import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import javax.management.InstanceNotFoundException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.ProxyConnection;
import org.apache.tomcat.jdbc.pool.jmx.ConnectionPoolMBean;

/**
 * What a pool with the {@code ResetAbandonedTimer} interceptor still reports
 * when a connection comes back that was flagged suspect while borrowed.
 *
 * <p>The marking and the report are two ends of one story: the sweep that
 * finds a borrow past {@code suspectTimeout} marks the connection and reports
 * the marking, and {@code ConnectionPool.returnConnection} reads that same
 * flag when the application finally hands the session back, reporting the
 * return of a suspect connection to the pool's JMX listeners (and, with
 * logAbandoned, in the log). That read happens after the application's
 * {@link java.sql.Connection#close()} has travelled down the interceptor chain
 * to the pool's tail, and the interceptor base class fires
 * {@code closeInvoked()} on that way down - exactly once per borrow, at its
 * end, before the close reaches the pool. A timer reset at that point wipes
 * the report before it is made: {@link PooledConnection#setTimestamp(long)}
 * clears the suspect flag and moves the timestamp the elapsed time of that
 * report is measured from, so the return path reads a connection that never
 * was suspected and neither the notification nor the log ever speaks for the
 * connections that were. Nor is there anything left for such a stamp to
 * protect: the borrow is ending, and the timestamp the returned connection
 * needs - the one it goes idle under - is the return path's own to write, and
 * it writes it.</p>
 */
class TestResetAbandonedTimerSuspectReturnReport extends JdbcPoolBaseTest {

    /*
     * A notification type of this test's own, used as a barrier. The pool
     * delivers its notifications on a single thread, in the order the pool
     * queued them, so a marker queued once a borrow has ended is reached by
     * the listener only after everything that borrow reported - or failed to
     * report - has been delivered. That makes both the positive and the
     * negative assertion below a question of what is in the list rather than
     * of how long the test is willing to wait.
     */
    private static final String FLUSH = "TEST-NOTIFY-BARRIER";

    /* The pool under test, for forced teardown. */
    private DataSource pool;

    /*
     * These tests can end with the connection still borrowed (a failed
     * assertion between the borrow and its close). The regular data source
     * teardown only disconnects idle connections, so the session of a borrowed
     * one would linger until the garbage collector finalizes it and leak its
     * session count into the starting point of the next test; force-close
     * disconnects the busy ones as well, right away.
     */
    @AfterEach
    void disconnectLeakedBusyConnections() {
        if (pool != null) {
            pool.close(true);
        }
    }

    /*
     * An enabled pool with the given interceptor configuration - the pool's
     * default chain when no configuration is given - published the way a
     * container publishes it, with this test's listener attached to the pool's
     * own MBean. That MBean is what a monitoring client subscribes to, so it
     * is what the report has to reach.
     */
    private final class Subscribed {
        private final DataSource ds;
        private final ObjectName oname;
        private final List<Notification> received = new CopyOnWriteArrayList<>();
        /* The one instance registered, so that it is the one detached again. */
        private final NotificationListener listener = (notification, handback) -> received.add(notification);

        private Subscribed(String role, String interceptors) throws Exception {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ds = newDataSource();
            ds.getPoolProperties().setJmxEnabled(true);
            if (interceptors != null) {
                ds.getPoolProperties().setJdbcInterceptors(interceptors);
            }
            ds.createPool();
            // The pool's MBean name is derived from the name the registration
            // uses, so a uid of its own keeps this pool out of the names any
            // other test of the suite publishes.
            ds.preRegister(server, new ObjectName("tomcat:type=SuspectReturnReportTest,role=" + role
                    + ",uid=" + System.nanoTime()));
            oname = ds.getPool().getJmxPool().getObjectName();
            Assertions.assertNotNull(oname, "an enabled pool publishes its MBean with its registration");
            server.addNotificationListener(oname, listener, null, null);
            pool = ds;
        }

        /* Everything the pool reported so far, once its notifier caught up. */
        private List<Notification> delivered() {
            Assertions.assertTrue(ds.getPool().getJmxPool().notify(FLUSH, "everything this borrow reported"),
                    "the barrier notification must be accepted for delivery");
            awaitUntil(() -> received.stream().anyMatch(n -> FLUSH.equals(n.getType())));
            return received.stream().filter(n -> !FLUSH.equals(n.getType())).collect(Collectors.toList());
        }

        /* The reports of a returned suspect connection among a delivery. */
        private List<Notification> suspectReturned(List<Notification> delivered) {
            return delivered.stream()
                    .filter(n -> ConnectionPoolMBean.SUSPECT_RETURNED_NOTIFICATION.equals(n.getType()))
                    .collect(Collectors.toList());
        }

        private void unsubscribe() {
            try {
                ManagementFactory.getPlatformMBeanServer().removeNotificationListener(oname, listener);
            } catch (ListenerNotFoundException | InstanceNotFoundException ignore) {
                // never added, or the pool's own teardown already took the
                // MBean back - either way there is nothing left to detach
            }
        }
    }

    /*
     * The regression: the sweep has flagged this borrow, and the application
     * hands the session back anyway. The flag must survive the close all the
     * way to the read the return path makes of it, or the report that the
     * marking exists for never happens.
     */
    @Test
    void aConnectionClosedWhileFlaggedSuspectStillReportsItsReturn() throws Exception {
        Subscribed watched = new Subscribed("timer", ResetAbandonedTimer.class.getName());
        try {
            Connection con = watched.ds.getConnection();
            PooledConnection pcon = interceptorOf(con, ProxyConnection.class).getConnection();
            // Stands in for the sweep's marking - the state a borrow is in
            // once ConnectionPool.suspect() has reported it as running past
            // suspectTimeout - set directly here so that nothing in this test
            // depends on a timer expiring.
            pcon.setSuspect(true);
            Assertions.assertTrue(pcon.isSuspect(), "precondition: this borrow is flagged suspect");

            con.close();

            Assertions.assertEquals(1, watched.ds.getReturnedCount(),
                    "precondition: the pool must have taken this connection back");
            List<Notification> delivered = watched.delivered();
            List<Notification> reports = watched.suspectReturned(delivered);
            Assertions.assertEquals(1, reports.size(), "the return of a suspect connection must be reported to the"
                    + " pool's listeners - the hook running on the way to the pool must not clear the state the"
                    + " return path reports, received: " + delivered);
            Assertions.assertTrue(reports.get(0).getMessage().contains("marked suspect"),
                    "the report must say what it is reporting, read: " + reports.get(0).getMessage());
        } finally {
            watched.unsubscribe();
        }
    }

    /*
     * The other half of the contract, on a pool without this interceptor: the
     * same flow with the same marking does report, so the report this test
     * pins is real, and what the test above fails on is the interceptor in the
     * chain and nothing else.
     */
    @Test
    void theSameReturnReportsWhenNoTimerInterceptorIsConfigured() throws Exception {
        Subscribed watched = new Subscribed("plain", null);
        try {
            Connection con = watched.ds.getConnection();
            PooledConnection pcon = interceptorOf(con, ProxyConnection.class).getConnection();
            pcon.setSuspect(true);
            Assertions.assertTrue(pcon.isSuspect(), "precondition: this borrow is flagged suspect");

            con.close();

            Assertions.assertEquals(1, watched.ds.getReturnedCount(),
                    "precondition: the pool must have taken this connection back");
            List<Notification> delivered = watched.delivered();
            Assertions.assertEquals(1, watched.suspectReturned(delivered).size(),
                    "a pool without the interceptor must report the return of a suspect connection, received: "
                            + delivered);
        } finally {
            watched.unsubscribe();
        }
    }

    /*
     * Keeping the hook quiet must not turn the report into something that
     * fires on any return: a connection that was never suspected goes back
     * silently, active work under the interceptor included.
     */
    @Test
    void anOrdinaryReturnUnderTheInterceptorReportsNothingSuspect() throws Exception {
        Subscribed watched = new Subscribed("clean", ResetAbandonedTimer.class.getName());
        try {
            try (Connection con = watched.ds.getConnection(); Statement st = con.createStatement()) {
                st.execute("SELECT 1");
            }

            Assertions.assertEquals(1, watched.ds.getReturnedCount(),
                    "precondition: the pool must have taken this connection back");
            List<Notification> delivered = watched.delivered();
            Assertions.assertTrue(watched.suspectReturned(delivered).isEmpty(),
                    "an ordinary return must not be reported as a suspect one, received: " + delivered);
        } finally {
            watched.unsubscribe();
        }
    }
}
