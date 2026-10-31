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
import java.sql.SQLException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * {@code Connection.close()} on a pooled handle is idempotent - "May be
 * called multiple times" - and the specification prescribes no failure for
 * it. Nothing changes when the pool is in the middle of taking the session
 * out from under the borrower: the abandoned-connection check, a forced pool
 * close and the release of a borrowed session each walk the cached
 * interceptor chain with {@code reset(null, null)} to tear it down, and that
 * walk is not atomic against the close of a handle that is still borrowed.
 * The close of such a handle may legitimately complete - reading the borrow
 * while the walk had not yet reached the bottom of the chain, it finishes
 * the return on the state it captured, and the pool's busy claim and released
 * guard count the two paths' one terminal step exactly once - or it may find
 * the borrow already ended and answer the no-op. What it must never do is
 * dereference a pool the teardown has already nulled and surface a raw
 * {@code NullPointerException} out of close(): the disposable facade
 * delegates close outside its own catch of that exception, and on these
 * paths it was never sealed by the teardown, so nothing else converts it
 * either. This class pins that contract at each position the close can meet
 * the walk - mid-walk, after it, and from inside it - and pins that the
 * teardown walk ends the borrow exactly once whichever position the close
 * took.
 */
class TestCloseVsTeardownWalk extends JdbcPoolBaseTest {

    /*
     * A pool whose borrow the abandoned-connection check claims on demand:
     * the timeout is one second and the tests push the borrow past it with
     * setTimestamp(0) rather than sleeping, so every run drives a real
     * release - disconnect(true), the ConnectionPool.finalize walk and the
     * setHandler(null) walk, each resetting every cached interceptor to
     * (null, null) - without depending on a timer thread.
     */
    private DataSource abandonedCheckPool() throws SQLException {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setRemoveAbandoned(true);
        ds.getPoolProperties().setRemoveAbandonedTimeout(1);
        ds.createPool();
        return ds;
    }

    @Test
    void aCloseHalfwayThroughTheTeardownWalkIsQuietNotANullPointerException()
            throws Exception {
        // The state the walk's own write order used to expose mid-teardown:
        // the pool field already nulled while the session still stands and
        // the ending borrow's handle still matches - what a close concurrent
        // with reset(null, null) could read between the walk's first write
        // and its second. Here the walk is not racing but reproduced through
        // the pool's own setter, so the close is deterministically on the
        // receiving end of it. Its branch read the fields again after its
        // guard back then, so the guard passed on this half-moved state and
        // the return it let run met the nulled pool as a raw
        // NullPointerException out of close(); the branch now acts only on
        // the locals its guard checked, and a borrow whose pool is gone is
        // the ended borrow whose close is the specification's no-op.
        DataSource ds = newDataSource();
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();
        ProxyConnection tail = interceptorOf(held, ProxyConnection.class);
        Assertions.assertNotNull(tail, "the fixture must reach the tail of the chain");
        Assertions.assertSame(pool, tail.getPool(), "precondition: the tail still names its pool");
        Assertions.assertNotNull(tail.getConnection(), "precondition: the borrow is armed");
        Assertions.assertSame(held, tail.getBorrowHandle(), "precondition: the tail serves this handle");

        // Stand in for the mid-walk state the old order exposed, session and
        // handle left as the walking thread found them.
        tail.setPool(null);
        Assertions.assertDoesNotThrow(held::close,
                "a close landing mid-teardown must be the quiet no-op of an "
                        + "ending borrow, not a NullPointerException");
        // The refusal happens ahead of any seal: the guard that answers a
        // borrow without a pool never touches the state the teardown walk is
        // moving underneath.
        Assertions.assertNotNull(tail.getConnection(),
                "the refused close must not have sealed the session field");
        Assertions.assertSame(held, tail.getBorrowHandle(),
                "the refused close must not have disturbed the borrow handle");
        Assertions.assertEquals(1, pool.getActive(),
                "the refused close must not have run a return either");

        // End the still-intact borrow as its borrower would have, now that
        // the fabricated teardown is stood down. The handle's facade was
        // disposed by the refused close, so the return runs on the pool's
        // own path; nothing about the borrow was damaged by the refusal.
        PooledConnection internal = tail.getConnection();
        tail.setPool(pool);
        pool.returnConnection(internal);
        Assertions.assertEquals(0, pool.getActive());
        Assertions.assertEquals(1, pool.getIdle(),
                "the borrow the interrupted close left intact returns normally");

        ds.close();
        awaitSessions(0);
    }

    @Test
    void aCloseAfterTheAbandonedCheckTookTheChainDownIsAQuietNoOp()
            throws Exception {
        // The same contract driven end to end through the real pool path:
        // checkAbandoned claims the overdue borrow, releases it and walks
        // the chain down to (null, null); the borrower, none the wiser, then
        // returns its handle. The close finds the borrow fully ended - every
        // field the guard consults cleared - and answers the no-op; the
        // abandoned check's own claim is the one terminal step.
        DataSource ds = abandonedCheckPool();
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();
        ProxyConnection tail = interceptorOf(held, ProxyConnection.class);
        tail.getConnection().setTimestamp(0L); // past the one-second abandon timeout

        pool.checkAbandoned();

        // The teardown walk reached the bottom of the chain: the tail holds
        // neither session nor pool nor borrow handle any more - the clearing
        // of the handle being what refuses a foreign or late call as an
        // ended borrow.
        Assertions.assertNull(tail.getConnection(),
                "the teardown walk must clear the session field");
        Assertions.assertNull(tail.getPool(),
                "and the pool field");
        Assertions.assertNull(tail.getBorrowHandle(),
                "and the borrow handle, so no call of the ended borrow can "
                        + "pass the fence again");

        Assertions.assertDoesNotThrow(held::close,
                "the borrower's late close is the no-op of a borrow the pool "
                        + "has ended, not a failure");
        Assertions.assertTrue(held.isClosed());
        Assertions.assertEquals(0, pool.getActive());
        Assertions.assertEquals(0, pool.getSize());
        Assertions.assertEquals(0, pool.getIdle());
        Assertions.assertEquals(1, pool.getBorrowedCount());
        Assertions.assertEquals(1, pool.getRemoveAbandonedCount());
        Assertions.assertEquals(1, pool.getReleasedCount(),
                "the claim by the abandoned check is the one terminal step");
        awaitSessions(0);
    }

    @Test
    void aCloseFromInsideTheTeardownWalkEndsTheBorrowExactlyOnce()
            throws Exception {
        // The honest seam for a close landing while the walk is still in
        // progress: a configured interceptor above the tail is visited by the
        // teardown walk first, and from inside its reset(null, null) it
        // closes the still-borrowed handle - a close running mid-walk, with
        // the tail provably still armed, with no timing involved. That close
        // reads a fully live borrow and completes its return; the session,
        // however, was already claimed by checkAbandoned ahead of the walk,
        // so the return deduplicates against the claim and the borrow still
        // ends exactly once, quietly, from both sides.
        CloseDuringTeardownInterceptor.resetCount.set(0);
        CloseDuringTeardownInterceptor.attempted.set(false);
        CloseDuringTeardownInterceptor.closeFailure.set(null);
        DataSource ds = abandonedCheckPool();
        ds.getPoolProperties().setJdbcInterceptors(
                CloseDuringTeardownInterceptor.class.getName());
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();
        CloseDuringTeardownInterceptor.handleToClose = held;
        try {
            ProxyConnection tail = interceptorOf(held, ProxyConnection.class);
            tail.getConnection().setTimestamp(0L);

            Assertions.assertDoesNotThrow(pool::checkAbandoned,
                    "the abandoned check must not be spoiled by the close it "
                            + "meets mid-walk");
            Assertions.assertNull(CloseDuringTeardownInterceptor.closeFailure.get(),
                    () -> "the close from inside the walk must be answered quietly: "
                            + CloseDuringTeardownInterceptor.closeFailure.get());
            Assertions.assertTrue(CloseDuringTeardownInterceptor.attempted.get(),
                    "precondition: the teardown walk must have reached the "
                            + "interceptor and run the close from inside it");
            Assertions.assertTrue(held.isClosed(),
                    "the close disposed of the handle as any close does");

            Assertions.assertEquals(0, pool.getActive());
            Assertions.assertEquals(0, pool.getSize(),
                    "the size counter moved exactly once, whichever release "
                            + "won the released guard");
            Assertions.assertEquals(0, pool.getIdle());
            Assertions.assertEquals(1, pool.getBorrowedCount());
            Assertions.assertEquals(1, pool.getRemoveAbandonedCount());
            Assertions.assertEquals(1, pool.getReturnedCount(),
                    "the mid-walk close ran one return, deduplicated against "
                            + "the abandoned check's claim");
            awaitSessions(0);
        } finally {
            CloseDuringTeardownInterceptor.handleToClose = null;
        }
    }

    @Test
    void closesRacingTheAbandonedCheckThrowNothingAndMiscountNothing()
            throws Exception {
        // Every position a close can take against the walk, raced for real:
        // the borrower returns the handle while the pool checks and reclaims
        // the overdue borrow. Whichever of the two reaches the borrow first,
        // neither call may fail - before the fix this loop was the natural
        // way to meet the raw NullPointerException out of close() - and the
        // borrow must leave nothing counted outside the queues.
        DataSource ds = abandonedCheckPool();
        ConnectionPool pool = ds.getPool();

        final int rounds = 200;
        AtomicReference<Throwable> failure = new AtomicReference<>();
        for (int round = 0; round < rounds; round++) {
            final int r = round;
            Connection held = ds.getConnection();
            ProxyConnection tail = interceptorOf(held, ProxyConnection.class);
            tail.getConnection().setTimestamp(0L);

            CyclicBarrier bothReady = new CyclicBarrier(3);
            Thread closer = new Thread(() -> {
                try {
                    bothReady.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    held.close();
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }, "closer-" + r);
            Thread abandoner = new Thread(() -> {
                try {
                    bothReady.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    pool.checkAbandoned();
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }, "abandoner-" + r);
            closer.start();
            abandoner.start();
            bothReady.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            closer.join(AWAIT_TIMEOUT_MS);
            abandoner.join(AWAIT_TIMEOUT_MS);

            Assertions.assertNull(failure.get(),
                    () -> "round " + r + ": neither the close nor the abandoned "
                            + "check may fail: " + failure.get());
            Assertions.assertEquals(0, pool.getActive(),
                    () -> "round " + r + ": the borrow must be ended by whichever side claimed it");
            Assertions.assertEquals(pool.getIdle(), pool.getSize(),
                    () -> "round " + r + ": every counted connection must be idle "
                            + "or destroyed, never counted outside the queues");
        }
        Assertions.assertEquals(rounds, pool.getBorrowedCount());
        awaitSessions(pool.getIdle());
    }

    /*
     * An interceptor that closes the borrowed handle from inside its own
     * reset(null, null) - the point the teardown walk offers a test to run a
     * close mid-walk, ahead of the tail and with no timing to get right. It
     * ignores the re-arm walk (reset with a non-null parent and connection)
     * and acts on the configured handle exactly once per teardown, since the
     * release reaches the chain with two walks. Configured through the
     * jdbcInterceptors class-name list, so it must stay a public static
     * class with a no-arg constructor.
     */
    public static class CloseDuringTeardownInterceptor extends JdbcInterceptor {

        /* Set by the test before triggering the teardown; a chain
           instantiated by class name can only be reached through such
           shared statics. */
        static volatile Connection handleToClose;
        static final AtomicInteger resetCount = new AtomicInteger();
        static final AtomicBoolean attempted = new AtomicBoolean();
        static final AtomicReference<Throwable> closeFailure = new AtomicReference<>();

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            if (parent != null || con != null) {
                return; // the re-arm walk is not a teardown
            }
            resetCount.incrementAndGet();
            Connection held = handleToClose;
            if (held != null && attempted.compareAndSet(false, true)) {
                try {
                    held.close();
                } catch (Throwable t) {
                    closeFailure.compareAndSet(null, t);
                }
            }
        }
    }
}
