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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.ProxyConnection;

/**
 * A borrow whose setup fails after the reset walk has already armed the
 * interceptor chain must retire that chain with the connection, not park the
 * connection idle under it.
 *
 * <p>{@code ConnectionPool.setupConnection()} walks the chain before it builds
 * the handle, and the walk is the arm: every interceptor takes the pooled
 * connection as its own and starts keeping per-borrow state - the timer
 * interceptor also publishes its MBean below the connection's. A failure in
 * the part of setup that comes after the walk therefore fails on a chain that
 * is already armed, and the borrow ends through the setup's own catch rather
 * than through a close: no close() will ever travel that chain from here, and
 * the return path does not walk the chain either. The catch hands the sequence
 * {@code setDiscarded(true)} and {@code returnConnection()}: the discarded flag
 * makes the return release the connection, and the release walks
 * {@code reset(null, null)} over the chain, which is the disposal every
 * interceptor implements - for the timer it lowers the checked-out flag, drops
 * the binding and takes its MBean back. Without the flag the same return parks
 * the connection idle with the chain still armed: the timer keeps reporting a
 * borrow nobody holds, and its operation rewrites the timestamp the idle check
 * measures that connection's age by.</p>
 *
 * <p>The natural trigger for that catch - a proxy constructor that fails -
 * cannot be forced from a test, so this test pins the premise the fix rests
 * on: the exact sequence the fixed catch performs leaves no armed interceptor
 * behind. It borrows from a published pool running nothing but the timer
 * interceptor, confirms the arm through the MBean, drives the sequence
 * reflectively, and finds the connection retired rather than parked: gone from
 * the pool and from the MBean server, its timer unbound and its flag down.</p>
 */
class TestResetAbandonedTimerFailedSetupDiscard extends JdbcPoolBaseTest {

    /* The MBean operation that reads the interceptor's armed state. */
    private static final String RESET_TIMER = "resetTimer";

    /* The pool under test, for the forced teardown below. */
    private DataSource pool;

    /*
     * The sequence the fixed catch runs: the mark that turns the return into a
     * release, then the return itself. Both are reached reflectively - the
     * catch is private to the pool's setup path and no borrower can fail a
     * proxy constructor on demand - and the test drives exactly what the catch
     * does, in that order.
     */
    private static void runAbandonSetup(ConnectionPool pool, PooledConnection con) throws Exception {
        con.setDiscarded(true);
        Method back = ConnectionPool.class.getDeclaredMethod("returnConnection", PooledConnection.class);
        back.setAccessible(true);
        back.invoke(pool, con);
    }

    /* The interceptor's own record of the borrow it is armed for. */
    private static boolean armed(ResetAbandonedTimer timer) throws Exception {
        Field checkedOut = ResetAbandonedTimer.class.getDeclaredField("checkedOut");
        checkedOut.setAccessible(true);
        return checkedOut.getBoolean(timer);
    }

    /* The pooled connection the interceptor instance is bound to, or null. */
    private static Object boundTo(ResetAbandonedTimer timer) throws Exception {
        Field pcon = ResetAbandonedTimer.class.getDeclaredField("pcon");
        pcon.setAccessible(true);
        return pcon.get(timer);
    }

    /*
     * These tests can end with the connection still borrowed (a failed
     * assertion between the borrow and its retirement). The regular data
     * source teardown only disconnects idle connections, so the session of a
     * borrowed one would linger until the garbage collector finalizes it;
     * force-close disconnects the busy ones as well, right away. The pool's
     * publication is released with it, so nothing of these tests stays
     * registered.
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
     * container publishes one. The publication is what makes the interceptor's
     * registration possible at all: it registers below the pooled connection,
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
            ds.preRegister(server, new ObjectName("tomcat:type=ResetAbandonedTimerFailedSetupTest,role=" + role
                    + ",uid=" + uid));
            Assertions.assertNotNull(ds.getPool().getJmxPool().getObjectName(),
                    "an enabled pool publishes its MBean together with its registration");
            pool = ds;
        }

        /*
         * The MBean the timer has published for the connection it is bound to,
         * or {@code null} while no timer of this pool is registered. The test
         * reads the name in both directions: present to confirm the arm, and
         * gone to confirm the disposal the retirement owes.
         */
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
            return found;
        }

        /* One call of the operation, as a management client makes it. */
        private Object poke(ObjectName oname) throws Exception {
            return server.invoke(oname, RESET_TIMER, null, null);
        }
    }

    /*
     * A pool with nothing but the timer interceptor. The eviction sweep is
     * stilled: this test asserts who owns the connection at the moment it
     * inspects it, and nothing else may be releasing or re-stamping idle
     * connections while it looks.
     */
    private DataSource timerPool() {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJmxEnabled(true);
        ds.getPoolProperties().setJdbcInterceptors(ResetAbandonedTimer.class.getName());
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(0);
        return ds;
    }

    /*
     * The premise of the fix: the sequence the post-walk catch performs -
     * mark the connection discarded, then hand it to the return path - retires
     * the armed chain with the connection. The test arms it first the way a
     * borrow does, through the pool's own setup, and confirms the arm from the
     * outside: the timer's MBean answers and reports a checked-out connection.
     * After the sequence, every trace of that borrow has to be gone: the pool
     * holds no connection, the MBean server holds no timer, the instance is
     * unbound with its flag down, and the bookkeeping counts the hand-back and
     * the release it just performed.
     */
    @Test
    void theSequenceTheFixedCatchRunsRetiresTheArmedChainWithTheConnection() throws Exception {
        awaitSessions(0);
        Published published = new Published(timerPool(), "discard");
        Connection con = published.ds.getConnection();

        // The arm, confirmed as management sees it: the timer published itself
        // below this connection's MBean and the operation finds a borrow
        // running. This is the state the post-walk catch fails on.
        ObjectName oname = published.timerName();
        Assertions.assertNotNull(oname, "the timer of a borrowed connection publishes itself below that"
                + " connection's MBean, and none was found below the pool published as uid " + published.uid);
        Assertions.assertEquals(Boolean.TRUE, published.poke(oname),
                "precondition: the walk armed the timer, so the operation reports a checked-out connection");
        ResetAbandonedTimer timer = interceptorOf(con, ResetAbandonedTimer.class);
        Assertions.assertNotNull(timer, "precondition: the pool runs the timer interceptor");
        PooledConnection pcon = interceptorOf(con, ProxyConnection.class).getConnection();
        Assertions.assertNotNull(pcon, "precondition: the tail of the chain knows its pooled connection");

        // What the fixed catch does with this half-started borrow. The handle
        // dies here - the connection is retired - so the timer instance and the
        // pooled connection, read off the live chain above, are what the
        // assertions below inspect: the instance the disposal walks is the same
        // one the arm bound, cached on the pooled connection's handler.
        runAbandonSetup(published.ds.getPool(), pcon);

        // The chain went out of service with the connection: the release took
        // the pool's size down and took the timer's name back, and the same
        // walk unbound the instance and lowered its flag.
        Assertions.assertEquals(0, published.ds.getPool().getSize(),
                "the discarded connection must be released, not parked idle under its armed chain");
        Assertions.assertNull(published.timerName(),
                "the timer of a retired connection takes its MBean back with the disposal walk, so the"
                        + " operation can no longer reach a borrow nobody holds");
        Assertions.assertThrows(InstanceNotFoundException.class, () -> published.poke(oname),
                "the registration the arm made is the one the disposal must have released");
        Assertions.assertFalse(armed(timer), "the disposal walk must leave the timer of a retired connection"
                + " disarmed: an armed timer on an idle connection rewrites that connection's idle age");
        Assertions.assertNull(boundTo(timer), "and it must leave it bound to nothing");

        // The counters the sequence moved, kept straight by the same path: the
        // hand-back is a return, the retirement is a release, and the session
        // is gone with them.
        Assertions.assertEquals(1, published.ds.getReturnedCount(),
                "the hand-back after the failed setup is counted as a return");
        Assertions.assertEquals(1, published.ds.getPool().getReleasedCount(),
                "the discarded connection must have gone through the release that disposes the chain");
        awaitSessions(0);
    }
}
