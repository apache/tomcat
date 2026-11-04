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

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The idle test validates and reconnects connections outside the borrow
 * and return paths. With the per-connection lock switched off - which is
 * exactly the configuration an administrative {@code testIdle()} runs in
 * when the eviction timer is off ({@code lock()} is a noop unless
 * {@code useLock} or the sweeper is on) a reconnect must not proceed while
 * the connection still sits in the idle queue: a borrower polling it out
 * in that window runs its own disconnect-then-connect over the same
 * pooled connection, and the loser's physical handle is overwritten by
 * the winner's field write - a session nothing references any more and
 * that therefore can never be closed. The sweep has to claim the
 * connection first, the way {@code checkIdle()} and
 * {@code checkAbandoned()} already claim before acting - and a pool that
 * closed while a connection was claimed must not get it offered back:
 * the shutdown drain cannot reach a claimed connection, so the claim
 * holder releases it directly.
 *
 * <p>Every ordering here is enforced through latches and the pool's own
 * wait-count, never through sleeps as the correctness signal.
 */
class TestTestAllIdleClaim extends JdbcPoolBaseTest {

    /*
     * What the driver should do with every connect() after the very first
     * one (the first always delegates, so the pool can be filled).
     * Static, because the driver is instantiated by class name.
     */
    private static final int DIRECT = 0;
    private static final int HOLD = 1;
    private static final int FAIL = 2;
    private static volatile int mode = DIRECT;
    private static final AtomicInteger CONNECTS = new AtomicInteger();
    /* Announced by a held connect() once it is parked, released by the test. */
    private static volatile CountDownLatch CONNECT_ATTEMPTED = new CountDownLatch(1);
    private static volatile CountDownLatch CONNECT_RELEASE = new CountDownLatch(1);

    private static void resetDriver() {
        mode = DIRECT;
        CONNECTS.set(0);
        CONNECT_ATTEMPTED = new CountDownLatch(1);
        CONNECT_RELEASE = new CountDownLatch(1);
    }

    /*
     * One-slot pool on the controlled driver, no sweeper and no lock -
     * the configuration in which lock() protects nothing and the queues
     * are the only serialisation left.
     */
    private DataSource oneSlotUnlockedPool() throws SQLException {
        DataSource ds = newDataSource();
        ds.setDriverClassName(LatchingDriver.class.getName());
        ds.getPoolProperties().setInitialSize(0);
        // exactly one slot: the claimed connection stays counted in size,
        // so a borrower cannot route around it by creating another one
        ds.getPoolProperties().setMaxActive(1);
        // a real maxAge, so the sweep really reconnects; the test expires
        // the connection itself, no clock waiting involved
        ds.getPoolProperties().setMaxAge(60000);
        // no timer: isPoolSweeperEnabled() stays false even with maxAge set,
        // so the per-connection lock is a noop, as useLock defaults to false
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(0);
        ds.getPoolProperties().setMaxWait(15000);
        ds.createPool();
        return ds;
    }

    /*
     * Expire a connection deterministically: rewind its last-connected
     * clock instead of waiting out a small maxAge.
     */
    private static void forceExpired(PooledConnection pc) throws Exception {
        Field lastConnected = PooledConnection.class.getDeclaredField("lastConnected");
        lastConnected.setAccessible(true);
        lastConnected.setLong(pc, System.currentTimeMillis() - 120000L);
    }

    @Test
    void aConnectionTheIdleTestIsReconnectingIsOutOfTheQueueAndUnborrowable() throws Exception {
        resetDriver();
        DataSource ds = oneSlotUnlockedPool();

        // one idle connection on a known physical session, then expired
        Connection first = ds.getConnection();
        awaitSessions(1);
        setSessionMarker(first, 7);
        PooledConnection pc = (PooledConnection) first.unwrap(PooledConnection.class);
        first.close();
        forceExpired(pc);

        // the sweep reaches the driver's connect() and parks there: its
        // disconnect() has closed the old session and the new one is not
        // open yet, so whatever happens next, it happens over this window
        mode = HOLD;
        AtomicReference<Throwable> sweepFailure = new AtomicReference<>();
        Thread sweep = new Thread(() -> {
            try {
                ds.getPool().testAllIdle();
            } catch (Throwable t) {
                sweepFailure.set(t);
            }
        }, "idle-sweep");
        try {
            sweep.start();
            boolean reached = CONNECT_ATTEMPTED.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            Assertions.assertTrue(reached, () ->
                    "the sweep never reached the driver's connect(): idle=" + ds.getPool().getIdle()
                            + " size=" + ds.getPool().getSize() + " active=" + ds.getPool().getActive()
                            + " connects=" + CONNECTS.get()
                            + " expired=" + pc.isMaxAgeExpired() + " lastConnected=" + pc.getLastConnected()
                            + " sweepFailure=" + sweepFailure.get());
            awaitSessions(0);

            // the connection under test is claimed: it is out of the idle
            // queue and cannot be handed to anybody while it is half
            // reconnected
            Assertions.assertEquals(0, ds.getPool().getIdle(),
                    "the connection being reconnected must not stay published in the idle queue");

            // a borrower therefore waits rather than receiving it: the only
            // slot is held by the claim, so waiting is the only move left
            AtomicReference<Connection> handed = new AtomicReference<>();
            AtomicReference<Throwable> borrowFailure = new AtomicReference<>();
            Thread borrower = new Thread(() -> {
                try {
                    handed.set(ds.getConnection());
                } catch (Throwable t) {
                    borrowFailure.set(t);
                }
            }, "borrower");
            borrower.start();
            try {
                awaitUntil(() -> ds.getPool().getWaitCount() == 1);
                // pre-fix the borrower would have polled the published
                // connection and be inside a second connect() of its own
                // right now; here it is parked on the empty queue and the
                // driver has seen exactly one reconnect
                Assertions.assertEquals(2, CONNECTS.get(),
                        "only the sweep may connect while the borrower waits");

                // let the sweep's connect() finish: the claim hands the
                // reconnected connection back and the parked borrower wakes
                // on it
                CONNECT_RELEASE.countDown();
                sweep.join(AWAIT_TIMEOUT_MS);
                borrower.join(AWAIT_TIMEOUT_MS);
                Assertions.assertFalse(sweep.isAlive());
                Assertions.assertFalse(borrower.isAlive());
            } finally {
                CONNECT_RELEASE.countDown();
                borrower.interrupt();
                borrower.join(AWAIT_TIMEOUT_MS);
            }
            Assertions.assertNull(sweepFailure.get());
            Assertions.assertNull(borrowFailure.get(), "the borrower was not handed the reconnected connection");

            // the same pooled connection, now on a fresh physical session:
            // the marker set on the old session is gone
            Connection again = handed.get();
            Assertions.assertNotNull(again);
            Assertions.assertSame(pc, again.unwrap(PooledConnection.class));
            Assertions.assertNull(sessionMarker(again));

            // and nothing was stranded: two physical connects (fill and
            // reconnect) have left exactly one live session
            Assertions.assertEquals(2, CONNECTS.get());
            awaitSessions(1);
            again.close();
            Assertions.assertEquals(1, ds.getPool().getIdle());
            Assertions.assertEquals(1, ds.getPool().getSize());
        } finally {
            CONNECT_RELEASE.countDown();
            sweep.join(AWAIT_TIMEOUT_MS);
        }
        ds.close();
        awaitSessions(0);
    }

    @Test
    void aPoolClosedWhileAConnectionIsClaimedDoesNotGetItOfferedBack() throws Exception {
        resetDriver();
        DataSource ds = oneSlotUnlockedPool();

        Connection first = ds.getConnection();
        awaitSessions(1);
        PooledConnection pc = (PooledConnection) first.unwrap(PooledConnection.class);
        first.close();
        forceExpired(pc);

        // the sweep claims the connection and parks inside its reconnect:
        // the claim makes the connection invisible to the shutdown drain
        mode = HOLD;
        final ConnectionPool pool = ds.getPool();
        AtomicReference<Throwable> sweepFailure = new AtomicReference<>();
        Thread sweep = new Thread(() -> {
            try {
                pool.testAllIdle();
            } catch (Throwable t) {
                sweepFailure.set(t);
            }
        }, "idle-sweep");
        try {
            sweep.start();
            Assertions.assertTrue(CONNECT_ATTEMPTED.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                    "the sweep never reached the driver's connect()");
            Assertions.assertEquals(0, pool.getIdle(),
                    "the connection being reconnected must be out of the idle queue");

            // the pool closes over the sweep's window: ordered through the
            // latch, close has fully returned - drain included, over an
            // empty idle queue - before the reconnect is allowed to finish
            ds.close();

            // a closing pool offers no further item to the idle queue: the
            // reconnected connection must be disconnected directly, not
            // offered back into a dead queue that nothing will drain again
            // - there it would sit as a live session out of every count
            CONNECT_RELEASE.countDown();
            sweep.join(AWAIT_TIMEOUT_MS);
            Assertions.assertFalse(sweep.isAlive());
            Assertions.assertNull(sweepFailure.get());

            Assertions.assertEquals(0, pool.getIdle(),
                    "a closed pool must not hold a connection back in its idle queue");
            Assertions.assertEquals(0, pool.getSize());
            awaitSessions(0);
        } finally {
            CONNECT_RELEASE.countDown();
            sweep.join(AWAIT_TIMEOUT_MS);
            ds.close();
        }
    }

    @Test
    void aFailedReconnectReleasesTheClaimedConnectionInsteadOfStrandingIt() throws Exception {
        resetDriver();
        DataSource ds = oneSlotUnlockedPool();

        Connection first = ds.getConnection();
        awaitSessions(1);
        PooledConnection pc = (PooledConnection) first.unwrap(PooledConnection.class);
        first.close();
        forceExpired(pc);

        mode = FAIL;
        ds.getPool().testAllIdle();

        // the reconnect failed, so the connection was released rather than
        // put back: disconnected, out of both queues, and its slot returned
        // to the size budget
        Assertions.assertEquals(0, ds.getPool().getIdle());
        Assertions.assertEquals(0, ds.getPool().getActive());
        Assertions.assertEquals(0, ds.getPool().getSize());
        awaitSessions(0);

        // the single slot was not lost: the next borrow connects afresh
        mode = DIRECT;
        try (Connection again = ds.getConnection()) {
            Assertions.assertNotNull(again);
            Assertions.assertNotSame(pc, again.unwrap(PooledConnection.class));
        }
        Assertions.assertEquals(1, ds.getPool().getIdle());
        awaitSessions(1);
        ds.close();
        awaitSessions(0);
    }

    /**
     * Driver handing out the real H2 connections, with every connect after
     * the first one either parked on the release latch or failed, per the
     * currently armed mode. Configured through {@code driverClassName}, so
     * it must stay a public static class with a no-arg constructor.
     */
    public static class LatchingDriver implements Driver {

        private final Driver delegate = new org.h2.Driver();

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            if (CONNECTS.incrementAndGet() > 1) {
                int armed = mode;
                if (armed == HOLD) {
                    CONNECT_ATTEMPTED.countDown();
                    try {
                        CONNECT_RELEASE.await(AWAIT_TIMEOUT_MS * 4, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException x) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("held connect interrupted", x);
                    }
                } else if (armed == FAIL) {
                    throw new SQLException("simulated connect failure");
                }
            }
            return delegate.connect(url, info);
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return delegate.acceptsURL(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return delegate.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return delegate.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return delegate.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return delegate.jdbcCompliant();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }
    }
}
