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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.interceptor.ConnectionState;

/**
 * A connection-level call dispatched through a handle races a close of that
 * same handle from another thread. The per-borrow facades seal only the calls
 * that arrive after the seal; a call already inside the interceptor chain
 * beneath them - the chain is cached on the pooled connection and re-armed for
 * the next borrower - meets only {@link ProxyConnection}'s own guard, and that
 * guard's answer, once read as live, must not be executed against a session the
 * pool has meanwhile taken back and handed to the next borrower. The dispatch
 * therefore runs on a snapshot of the pooled connection, fenced by a re-check
 * of the same state the guard consulted, exactly as the statement facade
 * fences its delegates - and, because a clean return re-arms that chain with
 * the very same pooled connection object the ended borrow held, the session's
 * state alone cannot tell a seal in progress from a borrow that ended and was
 * followed by a recycle: the fence for the recycle is the identity of the
 * handle the pool handed to the call's borrow, which the dispatch checks
 * beside the state. The two legal outcomes of the race are that the call
 * overtook the close - it completes against the session it was dispatched on,
 * indistinguishable from a call that simply ran first - or the close is
 * observed and the call fails the way every use of a closed handle fails, with
 * the closed-connection failure and no arrival at the driver. What is not
 * legal is anything else: not the NullPointerException of a receiver read torn
 * by the seal, and not a pool state that leaves a borrow counted outside its
 * queues because the two threads ended it twice or never.
 */
class TestCloseVsForwardedInvoke extends JdbcPoolBaseTest {

    @Test
    void aCallReleasedAfterItsBorrowWasClosedFailsClosedAtTheDispatch() throws Exception {
        // A gate interceptor parks a connection-level call inside the shared
        // chain - past the per-borrow facades' seals and above ProxyConnection,
        // where only this interceptor's own guard stands between the call and
        // the driver. The handle is closed and its session handed back while
        // the call is parked; when the gate opens after the borrow has ended,
        // the call must find the session gone and fail closed, without ever
        // reaching the driver.
        AutoCommitCountingDriver.CALLS.set(0);
        GateInterceptor.parked = new CountDownLatch(1);
        GateInterceptor.gate = new CountDownLatch(1);
        DataSource ds = newDataSource();
        ds.setDriverClassName(AutoCommitCountingDriver.class.getName());
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setJdbcInterceptors(GateInterceptor.class.getName());
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();
        Assertions.assertEquals(1, pool.getActive(), "precondition: the borrow is busy");

        AtomicReference<Throwable> callFailure = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                held.setAutoCommit(false);
            } catch (Throwable t) {
                callFailure.set(t);
            }
        }, "parked-caller");
        caller.start();
        Assertions.assertTrue(GateInterceptor.parked.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "precondition: the call must be parked above the dispatch while the close runs");

        // Seal the handle and hand the session back while the call waits.
        held.close();
        Assertions.assertEquals(0, pool.getActive());
        Assertions.assertEquals(1, pool.getIdle());
        Assertions.assertEquals(0, AutoCommitCountingDriver.CALLS.get(),
                "while parked the call has not reached the driver");

        // Let the call loose only now, after its borrow has provably ended.
        GateInterceptor.gate.countDown();
        caller.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(caller.isAlive(), "the call must have been answered");
        Throwable failure = callFailure.get();
        Assertions.assertNotNull(failure, "a call whose borrow ended while it waited must not succeed");
        Assertions.assertInstanceOf(SQLException.class, failure,
                () -> "the call must fail the way a closed connection fails, not with " + failure);
        Assertions.assertTrue(failure.getMessage() != null && failure.getMessage().contains("closed"),
                () -> "the refusal must name the closed connection: " + failure.getMessage());
        Assertions.assertEquals(0, AutoCommitCountingDriver.CALLS.get(),
                "the refused call must never reach the driver - not the session its "
                        + "borrow returned, nor any session served to a later borrower");

        // The session the closed handle returned is unharmed for the next borrow.
        try (Connection next = ds.getConnection()) {
            next.setAutoCommit(false);
        }
        Assertions.assertEquals(1, AutoCommitCountingDriver.CALLS.get(),
                "the next borrower's own call is the only one the driver was asked to run");
    }

    @Test
    void aCallParkedAcrossAReBorrowOfItsSessionFailsClosedAtTheDispatch() throws Exception {
        // Same parked call as the first test, but its session completes the
        // whole journey before the gate opens: the handle is closed, the
        // session goes back to the idle queue, and a second borrower takes it
        // out again. A clean return re-arms the cached chain through reset(),
        // with the same pooled connection object the ended borrow held, so by
        // the time the parked call descends into ProxyConnection every state
        // the dispatch's guard reads - the session field, the discarded flag,
        // the physical connection behind them - is the second borrower's live
        // state, and none of it mentions the borrow the call belongs to. Only
        // the handle the call carries still names that borrow, and the
        // dispatch must read it: the call has to fail closed, never reach the
        // driver, and leave the second borrower's session exactly as that
        // borrower left it.
        AutoCommitCountingDriver.CALLS.set(0);
        GateInterceptor.parked = new CountDownLatch(1);
        GateInterceptor.gate = new CountDownLatch(1);
        DataSource ds = newDataSource();
        ds.setDriverClassName(AutoCommitCountingDriver.class.getName());
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setJdbcInterceptors(GateInterceptor.class.getName());
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();

        AtomicReference<Throwable> callFailure = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                held.setAutoCommit(false);
            } catch (Throwable t) {
                callFailure.set(t);
            }
        }, "parked-caller");
        caller.start();
        Assertions.assertTrue(GateInterceptor.parked.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "precondition: the call must be parked above the dispatch while the close and the re-borrow run");

        // End the borrow and hand its session straight to a second borrower
        // while the call waits.
        held.close();
        Assertions.assertEquals(0, pool.getActive());
        Assertions.assertEquals(1, pool.getIdle());
        Connection second = ds.getConnection();
        Assertions.assertEquals(1, pool.getActive());
        Assertions.assertEquals(0, pool.getIdle(),
                "precondition: the parked call's session is now the second borrower's");

        // Let the call loose only now, after the chain has been re-armed for
        // the next borrower over the same session it was dispatched on.
        GateInterceptor.gate.countDown();
        caller.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(caller.isAlive(), "the call must have been answered");
        Throwable failure = callFailure.get();
        Assertions.assertNotNull(failure,
                "a call whose borrow ended while it waited must not succeed, not even against a "
                        + "session the pool re-armed for a later borrower");
        Assertions.assertInstanceOf(SQLException.class, failure,
                () -> "the call must fail the way a closed connection fails, not with " + failure);
        Assertions.assertTrue(failure.getMessage() != null && failure.getMessage().contains("closed"),
                () -> "the refusal must name the closed connection: " + failure.getMessage());
        Assertions.assertEquals(0, AutoCommitCountingDriver.CALLS.get(),
                "the refused call must never reach the driver");
        Assertions.assertTrue(second.getAutoCommit(),
                "the second borrower's session must read as that borrower left it, not as the "
                        + "ended borrow's parked call would have set it");

        second.setAutoCommit(false);
        Assertions.assertEquals(1, AutoCommitCountingDriver.CALLS.get(),
                "the second borrower's own call is the only one the driver was asked to run");
        second.close();
        Assertions.assertEquals(2, pool.getBorrowedCount());
        Assertions.assertEquals(2, pool.getReturnedCount(),
                "both borrows ended exactly once; the parked call ended neither a second time");
    }

    @Test
    void aGetConnectionParkedAcrossAReBorrowRefusesTheLiveSession() throws Exception {
        // The same journey with the handle's javax.sql.PooledConnection
        //#getConnection() parked in the chain. The accessor promises the
        // session behind a live connection; a call that reaches it after its
        // own borrow closed and the session went to the next borrower must
        // refuse, because the session the chain names then is the one the
        // second borrower is being served - handing it out here would put the
        // accessor above the closed contract every other use of the handle
        // already gives.
        GateInterceptor.parked = new CountDownLatch(1);
        GateInterceptor.gate = new CountDownLatch(1);
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setJdbcInterceptors(GateInterceptor.class.getName());
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();

        AtomicReference<Object> handed = new AtomicReference<>();
        AtomicReference<Throwable> callFailure = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                handed.set(((javax.sql.PooledConnection) held).getConnection());
            } catch (Throwable t) {
                callFailure.set(t);
            }
        }, "parked-getConnection");
        caller.start();
        Assertions.assertTrue(GateInterceptor.parked.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "precondition: the accessor must be parked above the dispatch while the close and the re-borrow run");

        held.close();
        Connection second = ds.getConnection();

        GateInterceptor.gate.countDown();
        caller.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(caller.isAlive(), "the call must have been answered");
        Assertions.assertNull(handed.get(),
                "no session may be handed out through a handle whose borrow ended");
        Throwable failure = callFailure.get();
        Assertions.assertInstanceOf(SQLException.class, failure,
                () -> "the accessor must fail the way a closed connection fails, not with " + failure);
        Assertions.assertTrue(failure.getMessage() != null && failure.getMessage().contains("closed"),
                () -> "the refusal must name the closed connection: " + failure.getMessage());

        // The second borrower's session is unharmed and still its own.
        second.isValid(1);
        second.close();
        Assertions.assertEquals(0, pool.getActive());
        Assertions.assertEquals(2, pool.getBorrowedCount());
        Assertions.assertEquals(2, pool.getReturnedCount());
    }

    @Test
    void aParkedCachedStateReadRefusesTheReBorrowedSession() throws Exception {
        // The same journey with a state read the ConnectionState interceptor
        // answers from its cache: such a read never reaches the dispatch, so
        // the only thing that can stand between it and a stale success is
        // the same handle identity the dispatch checks. The borrow sets
        // auto-commit off - a driver call that also lands in the cache,
        // giving the parked read something to answer with; the borrow closes
        // and a second borrower takes the session, whose reset restores the
        // configured auto-commit. Letting the parked read go would answer it
        // with a value only the second borrow made true, on a handle every
        // other use of fails as closed.
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setDefaultAutoCommit(Boolean.TRUE);
        ds.getPoolProperties().setJdbcInterceptors(
                GateInterceptor.class.getName() + ";" + ConnectionState.class.getName());
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();
        held.setAutoCommit(false); // reaches the driver and the interceptor cache

        // Arm the gate only for the read: the write above ran while the
        // gate of a previous test stood open.
        GateInterceptor.parked = new CountDownLatch(1);
        GateInterceptor.gate = new CountDownLatch(1);
        AtomicReference<Object> answered = new AtomicReference<>();
        AtomicReference<Throwable> readFailure = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try {
                answered.set(held.getAutoCommit());
            } catch (Throwable t) {
                readFailure.set(t);
            }
        }, "parked-cached-read");
        reader.start();
        Assertions.assertTrue(GateInterceptor.parked.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "precondition: the read must be parked above the state cache while the close and the re-borrow run");

        held.close();
        Connection second = ds.getConnection();
        Assertions.assertEquals(1, pool.getActive(),
                "precondition: the parked read's session now serves the second borrower");

        GateInterceptor.gate.countDown();
        reader.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(reader.isAlive(), "the read must have been answered");
        Assertions.assertNull(answered.get(),
                "a cached state read must not answer for a handle whose borrow ended -"
                        + " least of all with the value the next borrow's reset restored");
        Throwable failure = readFailure.get();
        Assertions.assertInstanceOf(SQLException.class, failure,
                () -> "the read must fail the way a closed connection fails, not with " + failure);
        Assertions.assertTrue(failure.getMessage() != null && failure.getMessage().contains("closed"),
                () -> "the refusal must name the closed connection: " + failure.getMessage());

        second.close();
        Assertions.assertEquals(2, pool.getBorrowedCount());
        Assertions.assertEquals(2, pool.getReturnedCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"getAutoCommit", "isReadOnly", "getCatalog", "getTransactionIsolation"})
    void aParkedCachedStateReadRefusesTheHandleOfAnIdleClosedBorrow(String read) throws Exception {
        // The window before the window above: a clean close ends the borrow
        // and returns the session with no teardown walk, so the cached chain
        // keeps its last borrow's arm - the closed handle included - for as
        // long as the session just sits idle. The state cache answers reads
        // while it believes that arm stands, and a read parked above it and
        // let loose in the idle stretch would be answered from the cache for
        // a handle whose every other call now fails as closed - and the gate
        // it stands on is the one gate for all four tracked reads, so each
        // of the four is the same violation with a different disclosure.
        // The close must seal the identity the cache checks, not only the
        // session.
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setDefaultAutoCommit(Boolean.TRUE);
        ds.getPoolProperties().setJdbcInterceptors(
                GateInterceptor.class.getName() + ";" + ConnectionState.class.getName());
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();
        // Put a value for the state the parameter names into the cache -
        // each set reaches the driver and is recorded from its success -
        // and read it back to prove the parked read below has a cached
        // value of its state to stand on.
        Object recorded;
        switch (read) {
            case "isReadOnly":
                held.setReadOnly(true);
                recorded = Boolean.TRUE;
                break;
            case "getCatalog":
                held.setCatalog("PUBLIC");
                recorded = "PUBLIC";
                break;
            case "getTransactionIsolation":
                held.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                recorded = Integer.valueOf(Connection.TRANSACTION_READ_COMMITTED);
                break;
            default: // getAutoCommit
                held.setAutoCommit(false);
                recorded = Boolean.FALSE;
                break;
        }
        Assertions.assertEquals(recorded, readState(held, read),
                "precondition: the cache answers the read with the value its set left");

        GateInterceptor.parked = new CountDownLatch(1);
        GateInterceptor.gate = new CountDownLatch(1);
        AtomicReference<Object> answered = new AtomicReference<>();
        AtomicReference<Throwable> readFailure = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try {
                answered.set(readState(held, read));
            } catch (Throwable t) {
                readFailure.set(t);
            }
        }, "parked-idle-cached-read");
        reader.start();
        Assertions.assertTrue(GateInterceptor.parked.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "precondition: the read must be parked above the state cache while the close runs");

        // End the borrow and let it stay ended: no second borrower, no walk,
        // nothing that could re-arm or reseal anything.
        held.close();
        Assertions.assertEquals(0, pool.getActive());
        Assertions.assertEquals(1, pool.getIdle(),
                "precondition: the returned session sits idle, chain and all");

        GateInterceptor.gate.countDown();
        reader.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(reader.isAlive(), "the read must have been answered");
        Assertions.assertNull(answered.get(),
                "a cached state read must not answer for a handle whose borrow ended,"
                        + " least of all while its session merely sits idle behind the seal");
        Throwable failure = readFailure.get();
        Assertions.assertInstanceOf(SQLException.class, failure,
                () -> "the read must fail the way a closed connection fails, not with " + failure);
        Assertions.assertTrue(failure.getMessage() != null && failure.getMessage().contains("closed"),
                () -> "the refusal must name the closed connection: " + failure.getMessage());

        Assertions.assertEquals(1, pool.getBorrowedCount());
        Assertions.assertEquals(1, pool.getReturnedCount());
    }

    @Test
    void aParkedCachedStateReadRefusesMidWalkOfTheNextBorrowersSetup() throws Exception {
        // The window inside the next borrow's setup: the reset walk reaches
        // the state cache - which re-arms and pushes the configured defaults
        // to the driver - before it reaches the tail, whose reset is what
        // clears the previous borrow's handle, and the new handle is recorded
        // only once the walk has completed. A read parked above the cache and
        // let loose inside that window meets a cache armed for the new borrow
        // and a tail still armed with the old handle: the value the cache
        // would answer is the one the next borrower's restore produced. The
        // ended handle must be refused there too, and this drives the walk
        // with real threads through the pool's own setupConnection.
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setDefaultAutoCommit(Boolean.TRUE);
        ds.getPoolProperties().setJdbcInterceptors(GateInterceptor.class.getName() + ";"
                + ConnectionState.class.getName() + ";" + WalkGateInterceptor.class.getName());
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();
        held.setAutoCommit(false); // reaches the driver and the interceptor cache
        ProxyConnection tail = interceptorOf(held, ProxyConnection.class);

        GateInterceptor.parked = new CountDownLatch(1);
        GateInterceptor.gate = new CountDownLatch(1);
        AtomicReference<Object> answered = new AtomicReference<>();
        AtomicReference<Throwable> readFailure = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try {
                answered.set(held.getAutoCommit());
            } catch (Throwable t) {
                readFailure.set(t);
            }
        }, "parked-midwalk-cached-read");
        reader.start();
        Assertions.assertTrue(GateInterceptor.parked.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "precondition: the read must be parked above the state cache while the close and the next borrow run");

        held.close();

        AtomicReference<Connection> secondBorrow = new AtomicReference<>();
        AtomicReference<Throwable> borrowFailure = new AtomicReference<>();
        Thread borrower = new Thread(() -> {
            try {
                secondBorrow.set(ds.getConnection());
            } catch (Throwable t) {
                borrowFailure.set(t);
            }
        }, "reborrow-parked-in-setup");
        // Arm the walk gate only now: the latch is consumed by whichever
        // walk reaches it first, and the first borrow's setup completed
        // long ago with the gate open - so the arrival awaited below can
        // only be the second borrow's walk, which is the one this test
        // holds in the window between the state cache and the tail.
        WalkGateInterceptor.inWalk = new CountDownLatch(1);
        WalkGateInterceptor.walkGate = new CountDownLatch(1);
        borrower.start();
        Assertions.assertTrue(WalkGateInterceptor.inWalk.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "precondition: the next borrow's reset walk must have passed the state cache"
                        + " and be held short of the tail");
        Assertions.assertNull(tail.getBorrowHandle(),
                "precondition: the close must have sealed the identity the interceptors read,"
                        + " ahead of any walk - the window may no longer offer it armed");

        GateInterceptor.gate.countDown();
        reader.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(reader.isAlive(), "the read must have been answered");
        Assertions.assertNull(answered.get(),
                "a cached state read must not answer for the ended borrow mid the next"
                        + " borrow's setup - the cache's arm here is one the walk will never"
                        + " let the ended handle match legitimately");
        Throwable failure = readFailure.get();
        Assertions.assertInstanceOf(SQLException.class, failure,
                () -> "the read must fail the way a closed connection fails, not with " + failure);
        Assertions.assertTrue(failure.getMessage() != null && failure.getMessage().contains("closed"),
                () -> "the refusal must name the closed connection: " + failure.getMessage());

        // The walk completes and the next borrower is served normally.
        WalkGateInterceptor.walkGate.countDown();
        borrower.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(borrower.isAlive(), "the second borrow must have completed");
        Assertions.assertNull(borrowFailure.get(),
                () -> "the second borrow may not fail: " + borrowFailure.get());
        Connection second = secondBorrow.get();
        second.isValid(1);
        second.close();
        Assertions.assertEquals(0, pool.getActive());
        Assertions.assertEquals(2, pool.getBorrowedCount());
        Assertions.assertEquals(2, pool.getReturnedCount());
    }

    @Test
    void aParkedCachedStateReadRefusesWhileTheDriversAbortRuns() throws Exception {
        // The abort branch seals the identity ahead of the driver call,
        // which the specification allows to run long. A driver that keeps
        // the abort in progress makes that stretch observable: the borrow
        // has ended but the session is not yet out of service, so the only
        // thing telling a read parked above the cache of this handle from
        // the current borrow is the seal the abort just made. It must
        // refuse while the driver is still inside the abort call - the
        // promise the branch's own comment makes, kept for every guard.
        BlockingAbortDriver.abortStarted = new CountDownLatch(1);
        BlockingAbortDriver.releaseAbort = new CountDownLatch(1);
        DataSource ds = newDataSource();
        ds.setDriverClassName(BlockingAbortDriver.class.getName());
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setDefaultAutoCommit(Boolean.TRUE);
        ds.getPoolProperties().setJdbcInterceptors(
                GateInterceptor.class.getName() + ";" + ConnectionState.class.getName());
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();
        held.setAutoCommit(false); // reaches the driver and the interceptor cache

        GateInterceptor.parked = new CountDownLatch(1);
        GateInterceptor.gate = new CountDownLatch(1);
        AtomicReference<Object> answered = new AtomicReference<>();
        AtomicReference<Throwable> readFailure = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try {
                answered.set(held.getAutoCommit());
            } catch (Throwable t) {
                readFailure.set(t);
            }
        }, "parked-during-abort-read");
        reader.start();
        Assertions.assertTrue(GateInterceptor.parked.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "precondition: the read must be parked above the state cache while the abort runs");

        AtomicReference<Throwable> abortFailure = new AtomicReference<>();
        Thread aborer = new Thread(() -> {
            try {
                held.abort(Runnable::run);
            } catch (Throwable t) {
                abortFailure.set(t);
            }
        }, "aborting-the-handle");
        aborer.start();
        Assertions.assertTrue(BlockingAbortDriver.abortStarted.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "precondition: the driver must be inside the abort call");

        GateInterceptor.gate.countDown();
        reader.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(reader.isAlive(), "the read must have been answered");
        Assertions.assertNull(answered.get(),
                "a cached state read must not answer for a handle the application is"
                        + " aborting out of service while the driver call is still running");
        Throwable failure = readFailure.get();
        Assertions.assertInstanceOf(SQLException.class, failure,
                () -> "the read must fail the way a closed connection fails, not with " + failure);
        Assertions.assertTrue(failure.getMessage() != null && failure.getMessage().contains("closed"),
                () -> "the refusal must name the closed connection: " + failure.getMessage());

        // The driver's answer, however late, completes the promise the
        // specification makes with it: the session is out of service and
        // never re-enters the pool.
        BlockingAbortDriver.releaseAbort.countDown();
        aborer.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(aborer.isAlive(), "the abort must have completed");
        Assertions.assertNull(abortFailure.get(),
                () -> "the abort may not fail: " + abortFailure.get());
        Assertions.assertEquals(0, pool.getActive());
        Assertions.assertEquals(0, pool.getSize(),
                "the aborted session must not go back to the pool");
    }

    @Test
    void theReArmedChainAnswersTheEndedBorrowAsEnded() throws Throwable {
        // The state the parked calls of the tests above meet, reached directly
        // and with no thread at all: the chain's tail, found before the close,
        // is called with the ended handle as its caller after the session has
        // been re-armed for a new borrower. That is exactly the position of a
        // call parked below the facades. The tail must answer the ended
        // handle as its borrow ended: its close is the closed contract's
        // no-op - and must not end the live borrow beside it - its closed
        // probe reports the handle closed, and its PooledConnection accessor
        // refuses the session it now serves to someone else. The new
        // borrower's own calls keep serving normally throughout.
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection stale = ds.getConnection();
        ProxyConnection tail = interceptorOf(stale, ProxyConnection.class);
        Assertions.assertNotNull(tail, "the chain must end in a ProxyConnection");
        Assertions.assertSame(stale, tail.getBorrowHandle(),
                "precondition: setup records the handle of the borrow the chain is armed for");
        stale.close();

        Connection live = ds.getConnection();
        Assertions.assertSame(tail, interceptorOf(live, ProxyConnection.class),
                "precondition: the recycle keeps one cached tail, re-armed with the same pooled connection");
        Assertions.assertSame(live, tail.getBorrowHandle(),
                "the setup of the new borrow must have recorded the new handle on the tail");

        // The ended handle reaches the re-armed tail the way a parked call does.
        Method close = Connection.class.getMethod("close");
        Assertions.assertNull(tail.invoke(stale, close, null),
                "the ended borrow's close is the closed contract's no-op...");
        Assertions.assertEquals(1, pool.getActive(),
                "...and above all must not end the borrow that is live now");
        Assertions.assertEquals(0, pool.getIdle(),
                "nor may its return hand the live session to the pool");

        Assertions.assertEquals(Boolean.TRUE, tail.invoke(stale,
                        Connection.class.getMethod("isClosed"), null),
                "the ended handle reports itself closed while the session behind the tail serves the next borrower");

        Method getPooledConnection = javax.sql.PooledConnection.class.getMethod("getConnection");
        Throwable thrown = Assertions.assertThrows(Throwable.class,
                () -> tail.invoke(stale, getPooledConnection, null));
        Assertions.assertInstanceOf(SQLException.class, thrown,
                "the accessor must refuse the next borrower's session, not hand it out");
        Assertions.assertTrue(thrown.getMessage() != null && thrown.getMessage().contains("closed"),
                () -> "the refusal must name the closed connection: " + thrown.getMessage());

        // The live borrow is untouched by any of it and ends normally, once.
        Assertions.assertEquals(Boolean.FALSE, tail.invoke(live,
                        Connection.class.getMethod("isClosed"), null),
                "the live borrow's own handle must keep reading live on the same tail");
        live.isValid(1);
        live.close();
        Assertions.assertEquals(0, pool.getActive());
        Assertions.assertEquals(2, pool.getBorrowedCount());
        Assertions.assertEquals(2, pool.getReturnedCount(),
                "the live borrow ended exactly once, whatever the ended handle attempted");
    }

    @Test
    void aCloseConcurrentWithADispatchedCallLeavesEitherOutcomeButNeverAnother() throws Exception {
        // Drive the race itself: one thread dispatches a connection-level call
        // on the handle while another closes the same handle, repeatedly. Each
        // round may end with the call overtaking the close or with the call
        // failing closed - but never with any other throw, and the pool must
        // end every borrow exactly once whoever won each race.
        AutoCommitCountingDriver.CALLS.set(0);
        DataSource ds = newDataSource();
        ds.setDriverClassName(AutoCommitCountingDriver.class.getName());
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        final int rounds = 200;
        for (int round = 0; round < rounds; round++) {
            final int r = round;
            Connection pooled = ds.getConnection();
            CyclicBarrier bothReady = new CyclicBarrier(3);
            AtomicReference<Throwable> callFailure = new AtomicReference<>();
            AtomicReference<Throwable> closeFailure = new AtomicReference<>();
            Thread caller = new Thread(() -> {
                try {
                    bothReady.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    pooled.setAutoCommit(false);
                } catch (Throwable t) {
                    callFailure.set(t);
                }
            }, "dispatched-call");
            Thread closer = new Thread(() -> {
                try {
                    bothReady.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    pooled.close();
                } catch (Throwable t) {
                    closeFailure.set(t);
                }
            }, "concurrent-close");
            caller.start();
            closer.start();
            bothReady.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            caller.join(AWAIT_TIMEOUT_MS);
            closer.join(AWAIT_TIMEOUT_MS);

            Assertions.assertFalse(caller.isAlive(), () -> "round " + r + ": the call must be answered");
            Assertions.assertFalse(closer.isAlive(), () -> "round " + r + ": the close must complete");
            Assertions.assertNull(closeFailure.get(),
                    () -> "round " + r + ": the close may not fail: " + closeFailure.get());
            Throwable failure = callFailure.get();
            if (failure != null) {
                Assertions.assertInstanceOf(SQLException.class, failure,
                        () -> "round " + r + ": a refused call must fail as a closed connection does, not with "
                                + failure);
                Assertions.assertTrue(failure.getMessage() != null && failure.getMessage().contains("closed"),
                        () -> "round " + r + ": the refusal must name the closed connection: "
                                + failure.getMessage());
            }
            Assertions.assertTrue(pooled.isClosed(),
                    () -> "round " + r + ": the handle must read closed once its close has completed");

            // The borrow is over whichever way the race went: a further use of
            // the handle is refused with no arrival at the driver beyond what
            // the race itself already ran.
            int observed = AutoCommitCountingDriver.CALLS.get();
            Assertions.assertThrows(SQLException.class, () -> pooled.setAutoCommit(true),
                    () -> "round " + r + ": a use of the closed handle must fail closed");
            Assertions.assertEquals(observed, AutoCommitCountingDriver.CALLS.get(),
                    () -> "round " + r + ": a use of the closed handle must not reach the driver");

            Assertions.assertEquals(0, pool.getActive(), () -> "round " + r + ": the borrow must be ended");
            Assertions.assertEquals(pool.getIdle(), pool.getSize(),
                    () -> "round " + r + ": no connection may sit counted outside the queues");
        }
        Assertions.assertEquals(rounds, pool.getBorrowedCount());
        Assertions.assertEquals(rounds, pool.getReturnedCount(),
                "every borrow ended exactly once, whichever side won each race");
    }

    /*
     * An interceptor that parks a setAutoCommit call - and, for the accessor
     * test, a getConnection call, and for the state-cache tests the four
     * cached state reads - and no other method, on a gate before descending
     * into the rest of the chain, and lets every other call, the close and
     * the abort among them, through. A test can thus hold a connection-level
     * call above ProxyConnection's dispatch - and above the caches of any
     * interceptor configured beneath it - while a close or an abort of the
     * same handle, and the re-borrow of its session, complete behind it.
     * Configured through the jdbcInterceptors class-name list, so it must
     * stay a public static class with a no-arg constructor; the gates are
     * static fields replaced by each test before use, for the same reason.
     */
    public static class GateInterceptor extends JdbcInterceptor {

        static volatile CountDownLatch gate;
        static volatile CountDownLatch parked;

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // NOOP
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            CountDownLatch currentGate = gate;
            String name = method.getName();
            if (currentGate != null && ("setAutoCommit".equals(name) || "getAutoCommit".equals(name)
                    || "getConnection".equals(name) || "isReadOnly".equals(name)
                    || "getCatalog".equals(name) || "getTransactionIsolation".equals(name))) {
                parked.countDown();
                currentGate.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            }
            return super.invoke(proxy, method, args);
        }
    }

    /*
     * The cached state read the parameter names, asked of the given
     * connection through the full chain.
     */
    private static Object readState(Connection c, String name) throws Exception {
        switch (name) {
            case "isReadOnly":
                return Boolean.valueOf(c.isReadOnly());
            case "getCatalog":
                return c.getCatalog();
            case "getTransactionIsolation":
                return Integer.valueOf(c.getTransactionIsolation());
            default: // getAutoCommit
                return Boolean.valueOf(c.getAutoCommit());
        }
    }

    /*
     * An interceptor that holds the reset walk - and only the walk, never a
     * call - at its place in the chain: configured below the state cache and
     * above the tail, it lets a test stop the next borrow's setup in the
     * window where the cache has already re-armed but the tail has not yet
     * been reset or recorded the new handle. Configured through the
     * jdbcInterceptors class-name list, so it must stay a public static class
     * with a no-arg constructor; the gates are static fields replaced by each
     * test before use, for the same reason.
     */
    public static class WalkGateInterceptor extends JdbcInterceptor {

        static volatile CountDownLatch inWalk;
        static volatile CountDownLatch walkGate;

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            CountDownLatch currentGate = walkGate;
            if (currentGate != null) {
                inWalk.countDown();
                try {
                    currentGate.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /*
     * H2 with a counter of the setAutoCommit calls that reach the driver, so a
     * test can tell a call the pool refused from one that ran on a session -
     * including a session this handle's borrow no longer owns. Configured
     * through driverClassName, so it must stay a public static class with a
     * no-arg constructor.
     */
    public static class AutoCommitCountingDriver implements Driver {

        static final AtomicInteger CALLS = new AtomicInteger();

        private final Driver delegate = new org.h2.Driver();

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            Connection real = delegate.connect(url, info);
            if (real == null) {
                return null;
            }
            return (Connection) Proxy.newProxyInstance(
                    AutoCommitCountingDriver.class.getClassLoader(),
                    new Class<?>[] { Connection.class },
                    (proxy, method, args) -> {
                        if ("setAutoCommit".equals(method.getName())) {
                            CALLS.incrementAndGet();
                        }
                        try {
                            return method.invoke(real, args);
                        } catch (InvocationTargetException x) {
                            throw x.getTargetException() != null ? x.getTargetException() : x;
                        }
                    });
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
            throw new SQLFeatureNotSupportedException();
        }
    }

    /*
     * H2 with an abort the test can hold in progress, so the stretch the
     * abort branch of the dispatch opens ahead of the driver call - borrow
     * ended, session not yet out of service - becomes observable, and a
     * test can tell what a call parked above the chain meets while the
     * driver is still inside it. Configured through driverClassName, so it
     * must stay a public static class with a no-arg constructor; the gates
     * are static fields replaced by each test before use, for the same
     * reason.
     */
    public static class BlockingAbortDriver implements Driver {

        static volatile CountDownLatch abortStarted;
        static volatile CountDownLatch releaseAbort;

        private final Driver delegate = new org.h2.Driver();

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            Connection real = delegate.connect(url, info);
            if (real == null) {
                return null;
            }
            return (Connection) Proxy.newProxyInstance(
                    BlockingAbortDriver.class.getClassLoader(),
                    new Class<?>[] { Connection.class },
                    (proxy, method, args) -> {
                        if ("abort".equals(method.getName())) {
                            // A slow driver: the caller waits inside the
                            // call until the test lets the driver answer.
                            abortStarted.countDown();
                            releaseAbort.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        }
                        try {
                            return method.invoke(real, args);
                        } catch (InvocationTargetException x) {
                            throw x.getTargetException() != null ? x.getTargetException() : x;
                        }
                    });
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
            throw new SQLFeatureNotSupportedException();
        }
    }
}
