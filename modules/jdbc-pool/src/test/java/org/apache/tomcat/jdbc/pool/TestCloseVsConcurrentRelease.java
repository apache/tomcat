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

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A pooled connection reaches its end from several hands: the abandon check
 * and a return whose claim on the busy queue lost both call
 * {@link PooledConnection#release()}, and a JMX-invoked abandoned check makes
 * that overlap reachable with no lock held anywhere - {@code con.lock()} is
 * the no-op it is unless the sweeper or useLock is on. The terminal sequence
 * release() drives - the chain's {@code disconnected(finalizing=true)}, the
 * physical close, the chain teardown at {@link ConnectionPool#finalize} - is
 * not a sequence a session can be taken through twice, so the single winner
 * must be claimed before any of it runs, not merely settled for the counters
 * afterwards. And the claim must precede the teardown, not follow it: the
 * flag that tells a {@link PooledConnection#connect()} this connection is
 * gone has to read true while the session is still visibly open, or a racing
 * reconnect resurrects the very session being closed.
 *
 * <p>The pool here is a counting subclass that parks the first terminal run
 * inside the chain notice - the exact window a second hand used to slip into -
 * so each of these properties is checked against a real overlap rather than a
 * lucky interleaving.</p>
 */
class TestCloseVsConcurrentRelease {

    /** How long the first terminal run is held inside the chain notice. */
    private static final long HOLD_MS = 1500;

    private static final class Recording {
        final AtomicInteger closeCalls = new AtomicInteger();
    }

    /** Counts the terminal sequence's steps and holds the first one partway through. */
    private static final class CountingPool extends ConnectionPool {
        final PoolProperties props;
        final AtomicInteger disconnectEvents = new AtomicInteger();
        final AtomicInteger finalizations = new AtomicInteger();
        final CountDownLatch insideFinalizing = new CountDownLatch(1);
        final CountDownLatch proceed = new CountDownLatch(1);

        CountingPool(PoolProperties properties) throws SQLException {
            super(properties);
            this.props = properties;
        }

        @Override
        protected void disconnectEvent(PooledConnection con, boolean finalizing) {
            disconnectEvents.incrementAndGet();
            if (finalizing) {
                insideFinalizing.countDown();
                try {
                    proceed.await(HOLD_MS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            super.disconnectEvent(con, finalizing);
        }

        @Override
        protected void finalize(PooledConnection con) {
            finalizations.incrementAndGet();
            super.finalize(con);
        }
    }

    private static Connection recordingConnection(Recording rec) {
        return (Connection) Proxy.newProxyInstance(TestCloseVsConcurrentRelease.class.getClassLoader(),
                new Class<?>[] { Connection.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "close" :
                            rec.closeCalls.incrementAndGet();
                            return null;
                        case "isClosed" :
                            return Boolean.FALSE;
                        case "getAutoCommit" :
                            return Boolean.TRUE;
                        case "equals" :
                            return Boolean.valueOf(proxy == args[0]);
                        case "hashCode" :
                            return Integer.valueOf(System.identityHashCode(proxy));
                        case "toString" :
                            return "recording-connection";
                        default :
                            return defaultValue(method.getReturnType());
                    }
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.FALSE;
        }
        if (type == int.class || type == Integer.class) {
            return Integer.valueOf(0);
        }
        if (type == long.class || type == Long.class) {
            return Long.valueOf(0);
        }
        return null;
    }

    /** A connected session whose physical close is counted, on a counting pool. */
    private static PooledConnection connectedTo(CountingPool pool) throws SQLException {
        PooledConnection con = new PooledConnection(pool.props, pool);
        con.connect();
        return con;
    }

    private static CountingPool countingPool(Recording rec) throws SQLException {
        PoolProperties props = new PoolProperties();
        props.setJmxEnabled(false);
        props.setInitialSize(0);
        props.setTimeBetweenEvictionRunsMillis(-1);
        props.setValidationInterval(0);
        props.setDataSource(Proxy.newProxyInstance(TestCloseVsConcurrentRelease.class.getClassLoader(),
                new Class<?>[] { javax.sql.DataSource.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getConnection" :
                            return recordingConnection(rec);
                        case "equals" :
                            return Boolean.valueOf(proxy == args[0]);
                        case "hashCode" :
                            return Integer.valueOf(System.identityHashCode(proxy));
                        case "toString" :
                            return "recording-datasource";
                        default :
                            return defaultValue(method.getReturnType());
                    }
                }));
        return new CountingPool(props);
    }

    @Test
    void aSecondReleaseDuringTheTerminalSequenceActsOnNothing() throws Exception {
        Recording rec = new Recording();
        CountingPool pool = countingPool(rec);
        PooledConnection con = connectedTo(pool);

        AtomicBoolean firstResult = new AtomicBoolean();
        Thread releaser = new Thread(() -> {
            pool.release(con);
            firstResult.set(con.isReleased());
        }, "first-releaser");
        releaser.start();
        Assertions.assertTrue(pool.insideFinalizing.await(10, TimeUnit.SECONDS),
                "the first release must have reached the chain's terminal notice");

        // The window the abandon check and a losing return fall into: the
        // first hand is inside the notice, the session is marked discarded
        // but still visibly open. This hand must claim nothing and touch
        // nothing.
        pool.release(con);
        Assertions.assertTrue(con.isReleased(),
                "a release that arrives during the terminal sequence loses the claim");
        Assertions.assertEquals(1, pool.disconnectEvents.get(),
                "the terminal notice must have gone through the chain once, not once per hand");
        Assertions.assertEquals(0, rec.closeCalls.get(), "the loser must not close the session");
        Assertions.assertEquals(0, pool.finalizations.get(), "the loser must not tear the chain down");

        pool.proceed.countDown();
        releaser.join(10000);
        Assertions.assertFalse(releaser.isAlive(), "the first release must complete");
        Assertions.assertTrue(firstResult.get(), "the first release is the winner");
        Assertions.assertEquals(1, pool.disconnectEvents.get(), "the terminal notice ran exactly once");
        Assertions.assertEquals(1, pool.finalizations.get(), "the chain teardown ran exactly once");
        Assertions.assertEquals(1, rec.closeCalls.get(), "the session was physically closed exactly once");
    }

    @Test
    void aReconnectCannotResurrectASessionBeingTornDown() throws Exception {
        Recording rec = new Recording();
        CountingPool pool = countingPool(rec);
        PooledConnection con = connectedTo(pool);

        Thread releaser = new Thread(() -> pool.release(con), "first-releaser");
        releaser.start();
        Assertions.assertTrue(pool.insideFinalizing.await(10, TimeUnit.SECONDS),
                "the first release must have reached the chain's terminal notice");

        // While the first hand is still inside the teardown, the flag that
        // says this connection is gone must already tell connect() so: settled
        // only after the teardown, a connect() reading it now would pass and
        // put a new session behind a connection being destroyed - the
        // resurrection this ordering is what closes.
        Assertions.assertThrows(SQLException.class, con::connect,
                "a connection whose release has begun must refuse a reconnect attempt");

        pool.proceed.countDown();
        releaser.join(10000);
        Assertions.assertFalse(releaser.isAlive(), "the first release must complete");
        Assertions.assertEquals(1, pool.disconnectEvents.get(),
                "the refused connect must have added nothing to the terminal sequence");
    }

    @Test
    void aReleaseAfterTheFirstStaysALossWithoutTouchingAnything() throws Exception {
        Recording rec = new Recording();
        CountingPool pool = countingPool(rec);
        PooledConnection con = connectedTo(pool);

        // This test is about the sequential second hand, not the held-up
        // first one, so the notice does not need to park anyone.
        pool.proceed.countDown();

        pool.release(con);
        Assertions.assertTrue(con.isReleased(), "the first release wins");
        pool.release(con);
        Assertions.assertEquals(1, pool.disconnectEvents.get(), "the terminal notice ran once, though two hands came");
        Assertions.assertEquals(1, pool.finalizations.get());
        Assertions.assertEquals(1, rec.closeCalls.get(), "the session was closed once, though two hands came");
    }
}
