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

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.ProxyConnection;

/**
 * The {@code resetTimer()} operation of the {@code ResetAbandonedTimer}
 * interceptor MBean, driven the way a management client drives it - through the
 * MBean server, on a connection the application is not holding.
 *
 * <p>The interceptor publishes one MBean per pooled connection, below that
 * connection's own MBean, and the binding the operation acts through is settled
 * at the borrow and released only when the chain is disposed: the chain lives
 * with the pooled connection, so handing it back leaves the instance bound to
 * it for the whole idle period. A stamp taken on that binding lands on a
 * connection that is not checked out. There is nothing for such a stamp to
 * avert - the abandoned check looks only at connections that are held - but
 * {@link PooledConnection#getTimestamp()} is also the age the idle check
 * measures an idle connection by
 * ({@link org.apache.tomcat.jdbc.pool.PoolConfiguration#setMinEvictableIdleTimeMillis(int)
 * minEvictableIdleTimeMillis}), so the operation rewrites that age. An operator
 * poking it on a schedule - which is what one does for a connection believed
 * stuck - keeps the connection in the pool for as long as the poking lasts,
 * past every idle limit the pool is configured with.</p>
 *
 * <p>These tests drive the operation against a published pool and pin that it
 * acts only while the pooled connection it is bound to is checked out: it
 * resets during a borrow, it resets nothing once the connection is back in the
 * pool, and idle eviction goes through while the operation is poked at the
 * interval that defeated it.</p>
 */
class TestResetAbandonedTimerMBeanIdle extends JdbcPoolBaseTest {

    /* The MBean operation under test, invoked as a client invokes it. */
    private static final String RESET_TIMER = "resetTimer";

    /* The age past which the idle check releases a connection. */
    private static final int EVICTABLE_IDLE_MILLIS = 1000;

    /* How often the evicter looks at the idle connections. */
    private static final int EVICTION_RUN_MILLIS = 100;

    /* How long the last test keeps poking the operation at an idle connection. */
    private static final long POKING_MILLIS = 2500;

    /* The interval between two pokes - a quarter of the idle limit above. */
    private static final long POKING_INTERVAL_MILLIS = 100;

    /*
     * A lower bound on the pokes that must have landed. The connection cannot
     * go before it is an idle period old, so a loop that ran at all has poked
     * far more often than this; the bound only catches a loop that never ran.
     */
    private static final int MIN_POKES = 5;

    /* The pool under test, for the forced teardown below. */
    private DataSource pool;

    /*
     * These tests can end with the connection still borrowed (a failed
     * assertion between the borrow and its close). The regular data source
     * teardown only disconnects idle connections, so the session of a borrowed
     * one would linger until the garbage collector finalizes it; force-close
     * disconnects the busy ones as well, right away. The pool's publication is
     * released with it, so nothing of these tests stays registered.
     */
    @AfterEach
    void disconnectLeakedBusyConnections() {
        if (pool != null) {
            pool.close(true);
            pool.postDeregister();
        }
    }

    /*
     * A pool running nothing but the timer interceptor, published the way a
     * container publishes one. The publication is what makes the operation
     * reachable at all: the interceptor registers below the pooled connection,
     * which registers below the pool's own MBean, so an unpublished pool has no
     * name for them to sit under. The uid is carried down into every name
     * derived from the registration, which is how the interceptor's name is
     * found below a pool of this test's own and nothing else.
     */
    private final class Published {
        private final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        private final DataSource ds;
        private final String uid;

        private Published(DataSource ds, String role) throws Exception {
            this.ds = ds;
            this.uid = Long.toString(System.nanoTime());
            ds.createPool();
            ds.preRegister(server, new ObjectName("tomcat:type=ResetAbandonedTimerMBeanTest,role=" + role
                    + ",uid=" + uid));
            Assertions.assertNotNull(ds.getPool().getJmxPool().getObjectName(),
                    "an enabled pool publishes its MBean together with its registration");
            pool = ds;
        }

        /* The MBean the timer has published for the connection it is bound to. */
        private ObjectName timerName() throws Exception {
            ObjectName found = null;
            for (ObjectName name : server.queryNames(new ObjectName("tomcat.jdbc:*"), null)) {
                if (ResetAbandonedTimer.class.getSimpleName().equals(name.getKeyProperty("JdbcInterceptor"))
                        && uid.equals(name.getKeyProperty("uid"))) {
                    Assertions.assertNull(found, "this test hands out one pooled connection, but a second timer is"
                            + " published as " + name);
                    found = name;
                }
            }
            Assertions.assertNotNull(found, "the timer of a borrowed connection publishes itself below that"
                    + " connection's MBean, and none was found below the pool published as uid " + uid);
            return found;
        }

        /* One call of the operation, as a management client makes it. */
        private Object poke(ObjectName oname) throws Exception {
            return server.invoke(oname, RESET_TIMER, null, null);
        }
    }

    /* A pool with the timer interceptor, with the given extra configuration. */
    private DataSource timerPool(int minEvictableIdleTimeMillis, int evictionRunMillis) {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJmxEnabled(true);
        ds.getPoolProperties().setJdbcInterceptors(ResetAbandonedTimer.class.getName());
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(evictionRunMillis);
        return ds;
    }

    /* A pool with nothing but the interceptor's own defaults. */
    private DataSource timerPool() {
        return timerPool(EVICTABLE_IDLE_MILLIS, EVICTION_RUN_MILLIS);
    }

    /*
     * The half of the operation that is not the defect, and the control for the
     * two tests below: the same call on the same registration, on a connection
     * that really is held, still does what the operation says it does.
     */
    @Test
    void theOperationResetsTheTimerOfACheckedOutConnection() throws Exception {
        Published published = new Published(timerPool(), "borrowed");
        Connection con = published.ds.getConnection();
        ObjectName oname = published.timerName();
        PooledConnection pcon = interceptorOf(con, ProxyConnection.class).getConnection();
        Assertions.assertNotNull(pcon, "precondition: the tail of the chain knows its pooled connection");

        // Not a window the fix has to hit: the sleep only makes the two reads
        // of the timestamp distinguishable, since a reset that cannot be told
        // apart from the stamp it replaced would prove nothing either way.
        sleep(50);
        long before = pcon.getTimestamp();

        Assertions.assertEquals(Boolean.TRUE, published.poke(oname),
                "the operation on a checked-out connection must report that it reset the timer");
        Assertions.assertTrue(pcon.getTimestamp() > before,
                "the reset must have moved the timestamp of the connection that is being held, read " + before
                        + " and found " + pcon.getTimestamp());

        con.close();
    }

    /*
     * The defect: the same operation, on a connection that has been handed back
     * and is sitting idle in the pool. The chain is still bound to it - which is
     * what makes the connection reachable to management at all - so the
     * operation reaches it, resets a timer that only counts for a borrow, and
     * moves the timestamp the idle check measures this connection by.
     */
    @Test
    void theOperationResetsNothingForAConnectionIdleInThePool() throws Exception {
        Published published = new Published(timerPool(), "idle");
        Connection con = published.ds.getConnection();
        ObjectName oname = published.timerName();
        PooledConnection pcon = interceptorOf(con, ProxyConnection.class).getConnection();

        con.close();

        Assertions.assertEquals(1, published.ds.getReturnedCount(),
                "precondition: the pool has taken this connection back");
        Assertions.assertEquals(1, published.ds.getPool().getSize(),
                "precondition: the connection is idle in the pool rather than released");
        // The name still answers: the registration is released with the chain,
        // not with the borrow, so this is the connection the operation reaches.
        Assertions.assertEquals(oname, published.timerName(),
                "precondition: the timer of an idle connection stays published");

        sleep(50);
        long before = pcon.getTimestamp();

        Assertions.assertEquals(Boolean.FALSE, published.poke(oname),
                "an idle connection is held by nobody, and the abandoned timer this operation resets is a property"
                        + " of a borrow that is not running");
        Assertions.assertEquals(before, pcon.getTimestamp(),
                "the age the idle check measures an idle connection by must not move under this operation, it was "
                        + before + " and is " + pcon.getTimestamp() + " now");
    }

    /*
     * The harm that defect causes, on the pool's own bookkeeping. An operator
     * who pokes the operation on a short schedule while a connection is idle
     * keeps rewriting the age of that connection, and the evicter - which acts
     * on nothing but that age - never finds it old enough. The pool is configured
     * to release an idle connection after one second and to look every hundred
     * milliseconds; the loop pokes every hundred milliseconds for two and a half
     * seconds, and the connection has to be gone anyway.
     */
    @Test
    void pokingTheOperationWhileIdleDoesNotKeepTheConnectionOutOfEviction() throws Exception {
        awaitSessions(0);
        Published published = new Published(timerPool(EVICTABLE_IDLE_MILLIS, EVICTION_RUN_MILLIS), "eviction");

        // The borrow exists to get one connection into the pool; it is back in
        // the pool when the block ends, idle and evictable from then on.
        try (Connection con = published.ds.getConnection()) {
            Assertions.assertNotNull(con, "precondition: the pool handed out a connection");
        }
        ObjectName oname = published.timerName();

        int pokes = 0;
        long resets = 0;
        long deadline = System.currentTimeMillis() + POKING_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (Boolean.TRUE.equals(published.poke(oname))) {
                    resets++;
                }
            } catch (InstanceNotFoundException gone) {
                // The timer takes its name back with the connection it is bound
                // to - the release unbinds the chain - which is the outcome the
                // loop is waiting for.
                break;
            }
            pokes++;
            sleep(POKING_INTERVAL_MILLIS);
        }

        Assertions.assertTrue(pokes >= MIN_POKES,
                "the operation must have been poked on a schedule across the idle period, only " + pokes
                        + " calls landed");
        Assertions.assertEquals(0, published.ds.getPool().getSize(),
                "a connection idle past minEvictableIdleTimeMillis must be released while this operation is poked"
                        + " at it: " + resets + " of " + pokes + " pokes reset the timer of a connection nobody"
                        + " holds, and each of those rewrote the age the idle check measures it by");
        Assertions.assertEquals(0, resets,
                "none of the pokes above may reset a timer: the connection was idle in the pool throughout");
        Assertions.assertEquals(1, published.ds.getPool().getReleasedIdleCount(),
                "the release must be the idle check's own, which is the bookkeeping the pokes defeated");
        awaitSessions(0);
    }

    @Test
    void anAnonymousSubclassPublishesUnderTheBaseClassKey() throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        String uid = Long.toString(System.nanoTime());
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJmxEnabled(true);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(0);
        ds.createPool();
        ds.preRegister(server, new ObjectName(
                "tomcat:type=ResetAbandonedTimerMBeanTest,role=anonymous,uid=" + uid));
        pool = ds;

        try (Connection connection = ds.getConnection()) {
            PooledConnection pc = connection.unwrap(PooledConnection.class);
            ResetAbandonedTimer anonymous = new ResetAbandonedTimer() {
            };
            anonymous.reset(ds.getPool(), pc);
            try {
                ObjectName timerName = null;
                for (ObjectName name : server.queryNames(new ObjectName("tomcat.jdbc:*"), null)) {
                    if (ResetAbandonedTimer.class.getSimpleName().equals(name.getKeyProperty("JdbcInterceptor"))
                            && uid.equals(name.getKeyProperty("uid"))) {
                        timerName = name;
                    }
                }
                Assertions.assertNotNull(timerName,
                        "an anonymous timer has no simple name and must publish under the base class key");
            } finally {
                anonymous.reset(null, null);
            }
        }
    }
}
