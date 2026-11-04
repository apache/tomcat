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
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * {@code Connection.abort(Executor)} ends a borrow. The specification has a
 * successful abort mark the connection closed, close the physical session and
 * release its resources, so a handle that has been aborted must report itself
 * closed, answer the liveness probe with false, refuse further use, and - the
 * pool-side half, Bugzilla #65929 - must not leave its session in
 * {@code busy} and its slot counted: an application that trusts the
 * specification will not return a handle it believes closed, so intercepting
 * abort only to forward it to the driver strands one maxActive slot per call
 * until the pool reports exhaustion. Whatever the driver made of the call -
 * and a driver's abort is not to be trusted for a yes: H2's own abort leaves
 * the session open, while a driver that refuses may still have begun closing
 * the session on the caller's executor - the session cannot go back to the
 * pool after an abort was attempted on it: it is taken out of service, the
 * slot is freed, and the next borrower is served. Calling abort on a handle
 * the handle reports closed - returned, or discarded while borrowed - is the
 * no-op the specification prescribes, and it touches no pool state.
 */
class TestAbortEndsTheBorrow extends JdbcPoolBaseTest {

    /* Runs the submitted task on the calling thread; the spec-compliant
       driver below only requires a non-null executor. */
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    private DataSource abortPool(Class<? extends Driver> driver, int maxActive) throws SQLException {
        DataSource ds = newDataSource();
        ds.setDriverClassName(driver.getName());
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMaxActive(maxActive);
        ds.createPool();
        return ds;
    }

    @Test
    void abortingAtMaxActiveOneFreesTheSlotForTheNextBorrow() throws Exception {
        SpecAbortDriver.ABORTS.set(0);
        DataSource ds = abortPool(SpecAbortDriver.class, 1);
        ConnectionPool pool = ds.getPool();

        Connection first = ds.getConnection();
        Assertions.assertEquals(1, pool.getSize());
        Assertions.assertEquals(1, pool.getActive());
        Assertions.assertEquals(1, poolSessions());

        first.abort(DIRECT_EXECUTOR);
        Assertions.assertEquals(1, SpecAbortDriver.ABORTS.get(),
                "the driver must have been asked to abort the session");
        Assertions.assertEquals(0, pool.getActive(),
                "an aborted borrow must leave nothing counted in busy");
        Assertions.assertEquals(0, pool.getSize(),
                "and no slot may stay consumed: the specification says the "
                        + "application will not return this handle");
        Assertions.assertEquals(0, pool.getIdle());
        awaitSessions(0); // the aborted session really left the database

        // The very symptom of Bugzilla #65929: before the fix this threw
        // PoolExhaustedException [size:1; busy:1; idle:0].
        Connection second = ds.getConnection();
        Assertions.assertEquals(1, poolSessions(),
                "the next borrow at maxActive=1 must be served by a fresh session");
        second.close();
    }

    @Test
    void theAbortedSessionIsNeverHandedToAnotherBorrower() throws Exception {
        // Plain H2 here on purpose: its abort is a no-op that leaves the
        // physical session open (measured), so this is the honest test that
        // the pool takes a session an abort was attempted on out of service
        // rather than recycling it - which the pool must do whatever the
        // driver's abort did, because the caller holding the handle now
        // believes the session is closed.
        DataSource ds = abortPool(org.h2.Driver.class, 2);
        ConnectionPool pool = ds.getPool();

        Connection aborted = ds.getConnection();
        setSessionMarker(aborted, 31);
        aborted.abort(DIRECT_EXECUTOR);

        Assertions.assertEquals(0, pool.getSize());
        Assertions.assertEquals(0, pool.getActive());
        awaitSessions(0); // the pool closed the session H2's abort left open

        try (Connection fresh = ds.getConnection()) {
            Assertions.assertNull(sessionMarker(fresh),
                    "the next borrower must get a new session, never the one an "
                            + "abort was attempted on");
        }
    }

    @Test
    void anAbortedHandleReportsItselfClosedAndRefusesUse() throws Exception {
        SpecAbortDriver.ABORTS.set(0);
        DataSource ds = abortPool(SpecAbortDriver.class, 1);

        Connection pooled = ds.getConnection();
        pooled.abort(DIRECT_EXECUTOR);

        Assertions.assertTrue(pooled.isClosed(),
                "the specification: calling abort marks the connection closed");
        Assertions.assertFalse(pooled.isValid(1),
                "an aborted handle answers the liveness probe with false, "
                        + "agreeing with isClosed as the two closed states do");
        Assertions.assertThrows(SQLException.class, pooled::createStatement,
                "a use of the aborted handle must fail as the use of a closed connection does");

        // and the borrower's close after its abort stays the harmless no-op
        // the code that skips it never sends
        Assertions.assertDoesNotThrow(pooled::close);
        Assertions.assertEquals(1, SpecAbortDriver.ABORTS.get(),
                "the late close must not reach the driver a second time");
    }

    @Test
    void abortingTheSameHandleTwiceAbortsOnce() throws Exception {
        SpecAbortDriver.ABORTS.set(0);
        DataSource ds = abortPool(SpecAbortDriver.class, 1);
        ConnectionPool pool = ds.getPool();

        Connection pooled = ds.getConnection();
        pooled.abort(DIRECT_EXECUTOR);
        Assertions.assertDoesNotThrow(() -> pooled.abort(DIRECT_EXECUTOR),
                "abort on a handle the abort itself closed is the no-op the "
                        + "specification prescribes, not a failure");
        Assertions.assertEquals(1, SpecAbortDriver.ABORTS.get(),
                "the driver was asked to abort once; the second call is answered by the pool");
        Assertions.assertEquals(0, pool.getSize());
        Assertions.assertEquals(0, pool.getActive());
        awaitSessions(0);
    }

    @Test
    void abortingAClosedHandleIsANoOp() throws Exception {
        SpecAbortDriver.ABORTS.set(0);
        DataSource ds = abortPool(SpecAbortDriver.class, 2);
        ConnectionPool pool = ds.getPool();

        Connection returned = ds.getConnection();
        returned.close();
        Assertions.assertEquals(0, SpecAbortDriver.ABORTS.get());

        Assertions.assertDoesNotThrow(() -> returned.abort(DIRECT_EXECUTOR),
                "calling abort on an already closed connection is a no-op");
        Assertions.assertEquals(0, SpecAbortDriver.ABORTS.get(),
                "and the no-op does not reach the driver");
        Assertions.assertEquals(1, pool.getIdle(),
                "the idle session the returned handle no longer owns stays in the pool");
        Assertions.assertEquals(1, poolSessions());
    }

    @Test
    void abortingADiscardedHandleSkipsTheDriverAndEndsTheBorrowQuietly() throws Exception {
        SpecAbortDriver.ABORTS.set(0);
        DataSource ds = abortPool(SpecAbortDriver.class, 2);
        // the shared interceptor marks the session to be closed when it is
        // returned, while the borrower still holds the handle
        ds.getPoolProperties().setJdbcInterceptors(DiscardMarkingInterceptor.class.getName());
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();
        Assertions.assertTrue(held.isValid(1), "precondition: the marking probe is answered live");
        Assertions.assertTrue(held.isClosed(),
                "precondition: the handle reports itself closed while the session "
                        + "waits for its return");

        Assertions.assertDoesNotThrow(() -> held.abort(DIRECT_EXECUTOR),
                "a handle that reports itself closed answers abort the way the "
                        + "specification prescribes, without a failure");
        Assertions.assertEquals(0, SpecAbortDriver.ABORTS.get(),
                "the closed handle's no-op does not act on the session at the driver");
        // The handle is now disposed, so the close that was to complete the
        // pool's discard will not come; the abort ends the borrow in its
        // place rather than leaving the session in busy behind a dead handle.
        Assertions.assertEquals(0, pool.getSize());
        Assertions.assertEquals(0, pool.getActive());
        awaitSessions(0);
        Assertions.assertDoesNotThrow(held::close);
    }

    @Test
    void anotherThreadEndingABorrowWithAbortReleasesTheSlot() throws Exception {
        // The specification's use case for abort: the pool (or a watchdog)
        // ends a borrow a different thread is stuck holding.
        SpecAbortDriver.ABORTS.set(0);
        DataSource ds = abortPool(SpecAbortDriver.class, 1);
        ConnectionPool pool = ds.getPool();

        Connection stuck = ds.getConnection();
        AtomicReference<Throwable> abortFailure = new AtomicReference<>();
        Thread watchdog = new Thread(() -> {
            try {
                stuck.abort(DIRECT_EXECUTOR);
            } catch (Throwable t) {
                abortFailure.set(t);
            }
        }, "aborting-watchdog");
        watchdog.start();
        watchdog.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(watchdog.isAlive(), "the watchdog must have finished");
        Assertions.assertNull(abortFailure.get(), () -> "the abort must not fail: " + abortFailure.get());

        Assertions.assertTrue(stuck.isClosed(),
                "a session aborted from another thread must read closed to its holder");
        Assertions.assertEquals(0, pool.getSize());
        Assertions.assertEquals(0, pool.getActive());
        awaitSessions(0);

        Assertions.assertDoesNotThrow(stuck::close,
                "the stuck holder's late return of the aborted handle stays a quiet no-op");
        try (Connection next = ds.getConnection()) {
            Assertions.assertNotNull(next, "the slot is free for the next borrower");
        }
    }

    @Test
    void concurrentAbortAndCloseEndTheBorrowExactlyOnce() throws Exception {
        // A watchdog aborting a handle while its borrower returns it is the
        // specification's own concurrency for abort; whichever of the two
        // disposes first, exactly one terminal step may run: the return
        // counter must stay one per borrow, and the pool must never end the
        // round with a session counted outside its queues.
        SpecAbortDriver.ABORTS.set(0);
        DataSource ds = abortPool(SpecAbortDriver.class, 1);
        ConnectionPool pool = ds.getPool();

        final int rounds = 200;
        for (int round = 0; round < rounds; round++) {
            Connection pooled = ds.getConnection();
            CyclicBarrier bothReady = new CyclicBarrier(3);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Runnable disposer = () -> {
                try {
                    bothReady.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    pooled.close();
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            };
            Thread aborter = new Thread(() -> {
                try {
                    bothReady.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    pooled.abort(DIRECT_EXECUTOR);
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }, "concurrent-abort");
            Thread closer = new Thread(disposer, "concurrent-close");
            aborter.start();
            closer.start();
            bothReady.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            aborter.join(AWAIT_TIMEOUT_MS);
            closer.join(AWAIT_TIMEOUT_MS);

            final int r = round;
            Assertions.assertNull(failure.get(),
                    () -> "round " + r + ": neither the abort nor the close may fail: " + failure.get());
            Assertions.assertEquals(0, pool.getActive(),
                    () -> "round " + r + ": the borrow must be ended");
            Assertions.assertEquals(pool.getIdle(), pool.getSize(),
                    () -> "round " + r + ": every counted connection must be idle "
                            + "or destroyed, never counted outside the queues");
            Assertions.assertTrue(pool.getSize() >= 0,
                    () -> "round " + r + ": the size counter must never run below zero");
        }
        Assertions.assertEquals(rounds, pool.getBorrowedCount());
        Assertions.assertEquals(rounds, pool.getReturnedCount(),
                "every borrow ended exactly once - neither twice nor never");
        // Whatever won each race, the physical sessions must match the
        // pool's idle queue exactly: none stranded, none destroyed yet
        // still counted.
        awaitSessions(pool.getIdle());
    }

    @Test
    void aConcurrentCloseDoesNotWaitBehindASlowDriverAbort() throws Exception {
        // The facade seals before it delegates and delegates outside its own
        // monitor, which the facade's comment says matters most for abort:
        // the specification allows the driver to take an extended period in
        // it, and a concurrent close must not wait on that. This pins that
        // claim by parking inside the driver's abort: while the abort is
        // provably still running, a close of the same handle must be
        // answered on its own, and only then may the abort finish. The close
        // is given a bounded await rather than a measured margin, so a
        // regression to a parked close fails loudly instead of flaking.
        SpecAbortDriver.ABORTS.set(0);
        CountDownLatch abortInDriver = new CountDownLatch(1);
        CountDownLatch releaseDriver = new CountDownLatch(1);
        SlowAbortDriver.abortInDriver = abortInDriver;
        SlowAbortDriver.releaseDriver = releaseDriver;
        DataSource ds = abortPool(SlowAbortDriver.class, 1);
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();
        AtomicReference<Throwable> abortFailure = new AtomicReference<>();
        Thread aborter = new Thread(() -> {
            try {
                held.abort(DIRECT_EXECUTOR);
            } catch (Throwable t) {
                abortFailure.set(t);
            }
        }, "slow-driver-abort");
        aborter.start();
        Assertions.assertTrue(abortInDriver.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "precondition: the driver must be inside the abort, holding the "
                        + "session, while the close is attempted");

        AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        Thread closer = new Thread(() -> {
            try {
                held.close();
            } catch (Throwable t) {
                closeFailure.set(t);
            }
        }, "close-during-slow-abort");
        closer.start();
        closer.join(AWAIT_TIMEOUT_MS);
        // The driver is still held here: a close that lined up behind the
        // abort - on a monitor, a lock, or the return - could not be back yet.
        Assertions.assertFalse(closer.isAlive(),
                "a close of a handle being aborted must not wait on the driver's abort");
        Assertions.assertNull(closeFailure.get(),
                () -> "the close of the aborting handle must be the quiet no-op "
                        + "of a disposed handle: " + closeFailure.get());
        Assertions.assertTrue(held.isClosed());

        releaseDriver.countDown();
        aborter.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(aborter.isAlive(), "the abort must have finished once the gate opened");
        Assertions.assertNull(abortFailure.get(), () -> "the abort must not fail: " + abortFailure.get());
        Assertions.assertEquals(1, SpecAbortDriver.ABORTS.get(),
                "the driver was asked to abort exactly once - the parked close "
                        + "must not have reached it with anything");
        Assertions.assertEquals(0, pool.getActive());
        Assertions.assertEquals(0, pool.getSize());
        Assertions.assertEquals(1, pool.getBorrowedCount());
        Assertions.assertEquals(1, pool.getReturnedCount(),
                "the borrow ended exactly once - the parked close added nothing");
        awaitSessions(0);
        try (Connection next = ds.getConnection()) {
            Assertions.assertNotNull(next, "the slow abort must not have stranded its slot");
        }
    }

    @Test
    void abortingABorrowThatTheAbandonedCheckAlsoClaimsEndsItExactlyOnce() throws Exception {
        // The abandoned-connection check and the borrower's abort are two
        // ways to end the same borrow, live at the same time: the pool's
        // watchdog claims a session it deems abandoned while the application
        // holding the handle aborts it. Whoever claims it must end it exactly
        // once - the other step must find it gone rather than double-count
        // the release or leave the slot counted-and-unqueued, and an aborted
        // session must never end up back in the idle queue. The check is run
        // on demand against an overdue borrow, so every round is a real race
        // rather than a timer coincidence, and the invariants are asserted
        // after every round.
        DataSource ds = newDataSource();
        ds.setDriverClassName(SpecAbortDriver.class.getName());
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setRemoveAbandoned(true);
        ds.getPoolProperties().setRemoveAbandonedTimeout(1);
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        final int rounds = 12;
        for (int round = 0; round < rounds; round++) {
            final int r = round;
            Connection pooled = ds.getConnection();
            Assertions.assertEquals(1, pool.getActive(),
                    () -> "round " + r + ": precondition: the borrow is busy");
            sleep(1100); // past the one-second abandon timeout: the check claims this borrow when it runs

            CyclicBarrier bothReady = new CyclicBarrier(3);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread aborter = new Thread(() -> {
                try {
                    bothReady.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    pooled.abort(DIRECT_EXECUTOR);
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }, "abort-vs-check");
            Thread checker = new Thread(() -> {
                try {
                    bothReady.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    pool.checkAbandoned();
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }, "check-vs-abort");
            aborter.start();
            checker.start();
            bothReady.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            aborter.join(AWAIT_TIMEOUT_MS);
            checker.join(AWAIT_TIMEOUT_MS);

            Assertions.assertNull(failure.get(),
                    () -> "round " + r + ": neither the abort nor the abandoned check may fail: "
                            + failure.get());
            Assertions.assertTrue(pooled.isClosed(),
                    () -> "round " + r + ": the handle must read closed, whichever step claimed the borrow");
            Assertions.assertEquals(0, pool.getActive(),
                    () -> "round " + r + ": the claimed borrow must be ended");
            Assertions.assertEquals(0, pool.getSize(),
                    () -> "round " + r + ": the session leaves the pool - a claimed "
                            + "or aborted session may never be recycled");
            Assertions.assertEquals(0, pool.getIdle(),
                    () -> "round " + r + ": nothing of this borrow may wait idle");
            awaitSessions(0); // and the physical session really left the database
        }
        Assertions.assertEquals(rounds, pool.getBorrowedCount());
        Assertions.assertEquals(rounds, pool.getReturnedCount(),
                "every borrow ended exactly once, whichever of the two steps won each race");
    }

    @Test
    void aDriverThatRefusesTheAbortStillCostsThePoolItsSessionNotTheSlot()
            throws Exception {
        SpecAbortDriver.ABORTS.set(0);
        DataSource ds = abortPool(RefusingAbortDriver.class, 1);
        ConnectionPool pool = ds.getPool();

        Connection pooled = ds.getConnection();
        SQLException refusal = Assertions.assertThrows(SQLException.class,
                () -> pooled.abort(DIRECT_EXECUTOR),
                "the driver's refusal must reach the caller unchanged");
        Assertions.assertEquals("this driver refuses to abort", refusal.getMessage());
        Assertions.assertEquals(1, SpecAbortDriver.ABORTS.get());

        // The pool cannot tell a refusal that left the session untouched from
        // one that died mid-close, and the caller will not return the handle;
        // the session goes out of service and the slot is freed regardless.
        Assertions.assertEquals(0, pool.getSize());
        Assertions.assertEquals(0, pool.getActive());
        awaitSessions(0); // the pool closes the session the driver would not abort
        Assertions.assertTrue(pooled.isClosed());

        try (Connection next = ds.getConnection()) {
            Assertions.assertNotNull(next, "the refused abort must not strand the slot either");
        }
    }

    @Test
    void aNullExecutorFailsTheAbortAndStillEndsTheBorrow() throws Exception {
        // The specification prescribes an SQLException for a null executor,
        // and this is the one abort failure that provably left the session
        // open - yet the handle is disposed all the same, because the pool
        // ends the borrow on any abort that was delegated: a handle whose
        // abort failed must not stay half-trusted by its borrower either,
        // and a slot must never end counted-but-unqueued.
        SpecAbortDriver.ABORTS.set(0);
        DataSource ds = abortPool(SpecAbortDriver.class, 1);
        ConnectionPool pool = ds.getPool();

        Connection pooled = ds.getConnection();
        SQLException failure = Assertions.assertThrows(SQLException.class, () -> pooled.abort(null));
        Assertions.assertEquals("executor is null", failure.getMessage());
        Assertions.assertEquals(1, SpecAbortDriver.ABORTS.get());

        Assertions.assertTrue(pooled.isClosed(),
                "the failed abort still disposed of the handle");
        Assertions.assertEquals(0, pool.getSize());
        Assertions.assertEquals(0, pool.getActive());
        awaitSessions(0);
        Assertions.assertDoesNotThrow(pooled::close);

        try (Connection next = ds.getConnection()) {
            Assertions.assertNotNull(next);
        }
    }

    @Test
    void jmxReportsTheSlotFreedByAnAbort() throws Exception {
        SpecAbortDriver.ABORTS.set(0);
        DataSource ds = newDataSource();
        ds.setDriverClassName(SpecAbortDriver.class.getName());
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setJmxEnabled(true);
        ds.getPoolProperties().setName("AbortEndsTheBorrow-" + System.nanoTime());
        ds.createPool();
        ConnectionPool pool = ds.getPool();
        org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmxPool = pool.getJmxPool();
        Assertions.assertNotNull(jmxPool, "the fixture must have created the pool MBean");

        Connection pooled = ds.getConnection();
        Assertions.assertEquals(1, pool.getSize());
        Assertions.assertEquals(1, jmxPool.getSize());
        Assertions.assertEquals(1, pool.getActive());
        Assertions.assertEquals(1, jmxPool.getNumActive(),
                "JMX must report the borrow as active before the abort");

        pooled.abort(DIRECT_EXECUTOR);

        Assertions.assertEquals(0, pool.getSize());
        Assertions.assertEquals(0, jmxPool.getSize(),
                "the MBean must see the slot freed, not a phantom active session");
        Assertions.assertEquals(0, jmxPool.getNumActive());
        Assertions.assertEquals(0, jmxPool.getNumIdle());
        Assertions.assertEquals(pool.getActive(), jmxPool.getNumActive());
        awaitSessions(0);
    }

    @Test
    void anAbortTakesTheSessionThroughTheDisconnectHookWithoutEndingTheRequest()
            throws Exception {
        assumeRequestHintsExist();
        SpecAbortDriver.ABORTS.set(0);
        SpecAbortDriver.BEGIN_REQUESTS.set(0);
        SpecAbortDriver.END_REQUESTS.set(0);
        DisconnectRecordingInterceptor.DISCONNECTS.set(0);
        DataSource ds = newDataSource();
        ds.setDriverClassName(SpecAbortDriver.class.getName());
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setJdbcInterceptors(
                DisconnectRecordingInterceptor.class.getName());
        ds.createPool();

        Connection pooled = ds.getConnection();
        Assertions.assertEquals(1, SpecAbortDriver.BEGIN_REQUESTS.get(),
                "precondition: the borrow opened its request at the driver");
        Assertions.assertEquals(0, SpecAbortDriver.END_REQUESTS.get());

        pooled.abort(DIRECT_EXECUTOR);

        Assertions.assertEquals(0, SpecAbortDriver.END_REQUESTS.get(),
                "a session taken out of service is not hinted - its close "
                        + "subsumes the hint, as on every other discard path");
        Assertions.assertEquals(1, DisconnectRecordingInterceptor.DISCONNECTS.get(),
                "the interceptors must be told the session is gone, once, "
                        + "the same disconnect the release path runs");
    }

    /* The hints do not exist before Java 9; same lookup the pool performs. */
    private static void assumeRequestHintsExist() {
        boolean exists;
        try {
            Connection.class.getMethod("beginRequest");
            exists = true;
        } catch (NoSuchMethodException e) {
            exists = false;
        }
        Assumptions.assumeTrue(exists,
                "java.sql.Connection has no request boundary hints before Java 9");
    }

    /*
     * A driver whose sessions honor java.sql.Connection.abort the way the
     * specification describes it, unlike H2's - H2's abort neither closes
     * the session nor rejects a null executor (both measured against
     * 2.2.220). These sessions count every abort that reaches the driver,
     * no-op when already closed, throw the prescribed SQLException for a
     * null executor, and otherwise close the physical session. They also
     * count the JDBC 4.3 request boundary hints, so a test can see that the
     * abort path sends no endRequest. Configured through driverClassName, so
     * it must stay a public static class with a no-arg constructor.
     */
    public static class SpecAbortDriver implements Driver {

        static final AtomicInteger ABORTS = new AtomicInteger();
        static final AtomicInteger BEGIN_REQUESTS = new AtomicInteger();
        static final AtomicInteger END_REQUESTS = new AtomicInteger();

        private final Driver delegate = new org.h2.Driver();

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            Connection real = delegate.connect(url, info);
            if (real == null) {
                return null;
            }
            return (Connection) Proxy.newProxyInstance(
                    SpecAbortDriver.class.getClassLoader(),
                    new Class<?>[] { Connection.class },
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "abort": {
                                ABORTS.incrementAndGet();
                                if (refusesAbort()) {
                                    throw new SQLException("this driver refuses to abort");
                                }
                                if (real.isClosed()) {
                                    return null; // the specification's no-op
                                }
                                if (args == null || args.length == 0 || args[0] == null) {
                                    throw new SQLException("executor is null");
                                }
                                parkInAbort();
                                real.close(); // marks closed, closes the session
                                return null;
                            }
                            case "beginRequest": {
                                BEGIN_REQUESTS.incrementAndGet();
                                break;
                            }
                            case "endRequest": {
                                END_REQUESTS.incrementAndGet();
                                break;
                            }
                            default: {
                                break;
                            }
                        }
                        try {
                            return method.invoke(real, args);
                        } catch (InvocationTargetException x) {
                            throw x.getTargetException() != null ? x.getTargetException() : x;
                        }
                    });
        }

        /* Overridden by the driver below; a driver instantiated by class name
           can only be told apart by its own behavior. */
        protected boolean refusesAbort() {
            return false;
        }

        /* Where a subclass may hold up the abort once the call is already
           inside the driver, the way a real driver's abort may run long; the
           specification allows it. The base driver's abort is not slowed. */
        protected void parkInAbort() throws InterruptedException {
            // NOOP
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
     * The spec-compliant driver above, with an abort that refuses: it throws
     * and leaves its session open, the outcome the pool must not trust for a
     * session it will never see returned.
     */
    public static class RefusingAbortDriver extends SpecAbortDriver {

        @Override
        protected boolean refusesAbort() {
            return true;
        }
    }

    /*
     * The spec-compliant driver above with an abort that stops inside the
     * driver on a gate and stays there until the test opens it - the
     * extended period the specification allows a driver to take in an abort,
     * made observable. The gate lets a test act while an abort is provably
     * still running, in particular close the very handle being aborted from
     * another thread. Configured through driverClassName, so it must stay a
     * public static class with a no-arg constructor.
     */
    public static class SlowAbortDriver extends SpecAbortDriver {

        /* Replaced by each test before use; a driver instantiated by class
           name can only be reached through such shared statics. */
        static volatile CountDownLatch abortInDriver = new CountDownLatch(1);
        static volatile CountDownLatch releaseDriver = new CountDownLatch(1);

        @Override
        protected void parkInAbort() throws InterruptedException {
            abortInDriver.countDown();
            releaseDriver.await();
        }
    }

    /*
     * Records how often the release path's disconnected() event reaches a
     * configured interceptor, so a test can see that aborting a borrow runs
     * the terminal step exactly once and notifies the chain the way any
     * other release does. Configured through the jdbcInterceptors
     * class-name list, so it must stay a public static class with a no-arg
     * constructor.
     */
    public static class DisconnectRecordingInterceptor extends JdbcInterceptor {

        static final AtomicInteger DISCONNECTS = new AtomicInteger();

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // NOOP
        }

        @Override
        public void disconnected(ConnectionPool parent, PooledConnection con, boolean finalizing) {
            DISCONNECTS.incrementAndGet();
        }
    }
}
