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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Expected behaviour of an asynchronous borrow that gets cancelled while
 * the connection it produced is on its way to a borrower.
 *
 * <p>The queue future hands its {@code PooledConnection} to every caller of
 * its {@code get()}, so the future that wraps it must make sure that exactly
 * one path claims that connection: either a {@code get()} caller, who then
 * owns the resulting {@code Connection}, or the cancellation cleanup, which
 * returns it to the pool. A {@code cancel()} that lost that race has to
 * report that nothing was cancelled, the way
 * {@link java.util.concurrent.Future#cancel(boolean)} reports nothing
 * cancelled for a task that has already completed.</p>
 */
class TestAsyncBorrowCancelRace extends JdbcPoolBaseTest {

    /**
     * A queue future whose item has already been handed over, mirroring
     * {@code FairBlockingQueue.ItemFuture}: its {@code get()} re-delivers the
     * same pooled connection to every caller and its {@code cancel()} reports
     * that nothing could be taken back. The first {@code get()} can be held
     * inside the call, which is how these tests pin down an interleaving that
     * would otherwise depend on thread scheduling.
     */
    private static final class DeliveredFuture implements Future<PooledConnection> {
        private final PooledConnection pooled;
        private final CountDownLatch enteredGet;
        private final CountDownLatch releaseGet;
        private final AtomicBoolean gatesFirstGet;

        DeliveredFuture(PooledConnection pooled, CountDownLatch enteredGet,
                CountDownLatch releaseGet, boolean gateFirstGet) {
            this.pooled = pooled;
            this.enteredGet = enteredGet;
            this.releaseGet = releaseGet;
            this.gatesFirstGet = new AtomicBoolean(gateFirstGet);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            // an item that was already handed over cannot be taken back
            return false;
        }

        @Override
        public PooledConnection get() throws InterruptedException, ExecutionException {
            return doGet();
        }

        @Override
        public PooledConnection get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return doGet();
        }

        private PooledConnection doGet() throws InterruptedException {
            if (gatesFirstGet.compareAndSet(true, false)) {
                enteredGet.countDown();
                releaseGet.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            }
            return pooled;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }
    }

    /**
     * A validator the pool calls while a borrower sets an idle connection up,
     * which is inside the window in which that borrower owns the connection
     * but has not handed it out yet. The first call marks the physical
     * session and parks the borrower until the test releases it. It is
     * configured through the pool properties, so it must stay a public static
     * class with a no-arg constructor.
     */
    public static class ParkingValidator implements Validator {
        volatile CountDownLatch insideSetup;
        volatile CountDownLatch resumeSetup;
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public boolean validate(Connection connection, int validateAction) {
            CountDownLatch inside = insideSetup;
            CountDownLatch resume = resumeSetup;
            if (inside != null && resume != null && calls.incrementAndGet() == 1) {
                try (Statement st = connection.createStatement()) {
                    st.execute("SET @BORROW_TEST_MARKER = 42");
                } catch (SQLException x) {
                    throw new IllegalStateException(x);
                }
                inside.countDown();
                try {
                    resume.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                }
            }
            return true;
        }
    }

    /*
     * The H2 session variable a ParkingValidator leaves behind, read through
     * a borrowed connection: it says which physical session that connection
     * maps to.
     */
    private static Integer borrowMarker(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT @BORROW_TEST_MARKER")) {
            rs.next();
            return rs.getObject(1) == null ? null : Integer.valueOf(rs.getInt(1));
        }
    }

    @Test
    void cancellingWhileTheBorrowerHoldsTheConnectionReportsNothingCancelled() throws Exception {
        ParkingValidator validator = new ParkingValidator();
        DataSource ds = newDataSource();
        ds.getPoolProperties().setFairQueue(true);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setMaxActive(3);
        ds.getPoolProperties().setMaxIdle(3);
        ds.getPoolProperties().setTestOnBorrow(true);
        ds.getPoolProperties().setValidationInterval(0);
        ds.getPoolProperties().setValidator(validator);
        ds.createPool();
        ConnectionPool pool = ds.getPool();
        long returnedBefore = pool.getReturnedCount();

        Connection holder = ds.getConnection();
        Connection other = ds.getConnection();
        Connection third = ds.getConnection(); // the pool is at maxActive now

        // this borrow can only be served by a returned connection
        Future<Connection> future = ds.getConnectionAsync();
        Assertions.assertFalse(future.isDone(),
                "the poll must still be waiting for a connection");

        CountDownLatch insideSetup = new CountDownLatch(1);
        CountDownLatch resumeSetup = new CountDownLatch(1);
        validator.insideSetup = insideSetup;
        validator.resumeSetup = resumeSetup;

        AtomicReference<Connection> borrowed = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                borrowed.set(future.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "async-borrower");
        caller.setDaemon(true);
        caller.start();

        holder.close(); // hands this borrower's connection to the queued poll
        Assertions.assertTrue(insideSetup.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "the borrower must have taken a connection out of the queue");
        Assertions.assertTrue(caller.isAlive(),
                "the borrower must still be setting that connection up");

        // a watchdog gives up on the borrow while its caller is using it
        Assertions.assertFalse(future.cancel(true),
                "the caller that claimed the connection must not be cancelled "
                        + "out from under it, and the connection must not go "
                        + "back to the pool underneath it");
        resumeSetup.countDown();
        caller.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(caller.isAlive(), "the borrower must have finished");

        Assertions.assertNull(failure.get(),
                "the borrower must still be served its connection, got " + failure.get());
        Connection connection = borrowed.get();
        Assertions.assertNotNull(connection, "the borrower must have received a connection");
        Assertions.assertEquals(Integer.valueOf(42), borrowMarker(connection),
                "the borrower must hold the session it took out of the queue");

        // that connection must not have been returned underneath its owner
        Assertions.assertEquals(1, pool.getReturnedCount() - returnedBefore,
                "only the borrower's connection may go back to the pool");
        Assertions.assertEquals(0, ds.getIdle(),
                "the connection a caller holds must not be up for grabs again");
        Assertions.assertEquals(3, ds.getActive());

        // the caller keeps a usable connection, and it goes back exactly once
        try (ResultSet rs = connection.createStatement().executeQuery("SELECT 1")) {
            Assertions.assertTrue(rs.next(), "the borrowed connection must still work");
        }
        connection.close();
        Assertions.assertEquals(2, pool.getReturnedCount() - returnedBefore,
                "the connection must be returned once, by the one who owns it");
        Assertions.assertEquals(1, ds.getIdle());

        other.close();
        third.close();
        ds.close();
        awaitSessions(0);
    }

    @Test
    void cancellingBeforeTheBorrowerClaimsTheConnectionReturnsItToThePool() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setFairQueue(true);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setMaxActive(2);
        ds.getPoolProperties().setMaxIdle(2);
        ds.createPool();
        ConnectionPool pool = ds.getPool();
        long returnedBefore = pool.getReturnedCount();

        // a pooled connection the queue has already handed to the poll
        PooledConnection pooled = pool.create(true);
        CountDownLatch insideQueueGet = new CountDownLatch(1);
        CountDownLatch releaseQueueGet = new CountDownLatch(1);
        Future<PooledConnection> queueFuture =
                new DeliveredFuture(pooled, insideQueueGet, releaseQueueGet, true);
        Future<Connection> future = pool.new ConnectionFuture(queueFuture);

        AtomicReference<Connection> borrowed = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                borrowed.set(future.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "async-borrower");
        caller.setDaemon(true);
        caller.start();
        Assertions.assertTrue(insideQueueGet.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "the borrower must hold the connection the queue produced");

        // the watchdog claims that connection, so it is the one that reclaims it
        Assertions.assertTrue(future.cancel(true),
                "a cancellation that claims the produced connection must report it");
        releaseQueueGet.countDown();
        caller.join(AWAIT_TIMEOUT_MS);

        Assertions.assertNull(borrowed.get(), "a cancelled borrow must not hand out the "
                + "connection the cleanup reclaimed, it handed out " + borrowed.get());
        Assertions.assertTrue(failure.get() instanceof CancellationException,
                "a cancelled borrow must fail its caller with a cancellation, got "
                        + failure.get());
        Assertions.assertTrue(future.isCancelled());
        Assertions.assertTrue(future.isDone());

        // the connection the cleanup claimed is back in the pool, exactly once
        awaitUntil(() -> pool.getReturnedCount() - returnedBefore == 1);
        sleep(200); // a second return by the same cleanup would show up now
        Assertions.assertEquals(1, pool.getReturnedCount() - returnedBefore,
                "the cancellation cleanup must return the connection exactly once");
        Assertions.assertEquals(1, ds.getIdle(),
                "the reclaimed connection must be idle again");
        Assertions.assertEquals(0, ds.getActive());

        // and it is still there for the next borrower
        try (Connection c = ds.getConnection()) {
            Assertions.assertEquals(1, ds.getActive());
        }
        ds.close();
    }

    @Test
    void cancellingADeliveredPollWithNoBorrowerReclaimsTheConnection() throws Exception {
        ParkingValidator validator = new ParkingValidator();
        DataSource ds = newDataSource();
        ds.getPoolProperties().setFairQueue(true);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setMaxActive(2);
        ds.getPoolProperties().setMaxIdle(2);
        ds.getPoolProperties().setTestOnBorrow(true);
        ds.getPoolProperties().setValidationInterval(0);
        ds.getPoolProperties().setValidator(validator);
        ds.createPool();
        ConnectionPool pool = ds.getPool();
        long returnedBefore = pool.getReturnedCount();

        // the queue produced its connection but nobody calls get(): this is
        // the documented watchdog case, cancelling must reclaim the connection
        PooledConnection pooled = pool.create(true);
        CountDownLatch unused = new CountDownLatch(0);
        Future<PooledConnection> queueFuture =
                new DeliveredFuture(pooled, unused, unused, false);
        Future<Connection> future = pool.new ConnectionFuture(queueFuture);

        // the validator must be armed before the cancellation, as the
        // cancellation starts the cleanup right away and that cleanup has to
        // park inside the borrow it does with the produced connection
        CountDownLatch insideCleanup = new CountDownLatch(1);
        CountDownLatch resumeCleanup = new CountDownLatch(1);
        validator.insideSetup = insideCleanup;
        validator.resumeSetup = resumeCleanup;

        Assertions.assertTrue(future.cancel(true),
                "a delivered poll nobody claimed must be cancellable");
        Assertions.assertTrue(insideCleanup.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "the cleanup must have taken the produced connection into its"
                        + " borrow before the timed getter runs");

        // a timed get() that loses the claim to that running cancellation is a
        // caller of a cancelled future: it must be told about the cancellation
        // rather than about its own deadline expiring
        Assertions.assertThrows(CancellationException.class,
                () -> future.get(100, TimeUnit.MILLISECONDS),
                "a timed get() that loses the claim to a won cancellation must"
                        + " report the cancellation, not a timeout");

        resumeCleanup.countDown();
        awaitUntil(() -> pool.getReturnedCount() - returnedBefore == 1);
        sleep(200); // a second return by the same cleanup would show up now
        Assertions.assertEquals(1, pool.getReturnedCount() - returnedBefore,
                "the cleanup must return the connection exactly once");
        Assertions.assertEquals(1, ds.getIdle());
        Assertions.assertEquals(0, ds.getActive());
        Assertions.assertTrue(future.isCancelled());

        // a caller that shows up late is told about the cancellation instead
        // of being handed the connection that already went back to the pool
        Assertions.assertThrows(CancellationException.class,
                () -> future.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "a completed cancellation must keep being reported");

        // and the connection is still there for the next borrower
        try (Connection c = ds.getConnection()) {
            Assertions.assertEquals(1, ds.getActive());
        }
        ds.close();
    }

    @Test
    void cancellingBeforeDeliveryDeregistersThePollAndFailsItsGetters() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setFairQueue(true);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxIdle(1);
        ds.createPool();

        Connection holder = ds.getConnection();
        setSessionMarker(holder, 7);
        Future<Connection> future = ds.getConnectionAsync();

        // the poll is still queued, so cancelling it takes nothing back and
        // hands nothing to anybody
        Assertions.assertTrue(future.cancel(true), "a queued poll must be cancellable");
        Assertions.assertTrue(future.isCancelled());
        Assertions.assertTrue(future.isDone());
        Assertions.assertThrows(CancellationException.class,
                () -> future.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "a cancelled poll must fail its caller with a cancellation");
        Assertions.assertFalse(future.cancel(true),
                "a second cancel must report nothing cancelled");

        // the cancelled poll must not consume the connection it waited for
        holder.close();
        Assertions.assertEquals(1, ds.getIdle());
        try (Connection c = ds.getConnection()) {
            Assertions.assertEquals(Integer.valueOf(7), sessionMarker(c),
                    "the connection must go to the next borrower, not to the "
                            + "cancelled poll");
        }
        ds.close();
    }

    @Test
    void concurrentGettersShareOneConnectionAndItIsReturnedOnce() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setFairQueue(true);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setMaxActive(2);
        ds.getPoolProperties().setMaxIdle(2);
        ds.createPool();
        ConnectionPool pool = ds.getPool();
        long returnedBefore = pool.getReturnedCount();

        PooledConnection pooled = pool.create(true);
        CountDownLatch unused = new CountDownLatch(0);
        Future<PooledConnection> queueFuture =
                new DeliveredFuture(pooled, unused, unused, false);
        final Future<Connection> future = pool.new ConnectionFuture(queueFuture);

        AtomicInteger served = new AtomicInteger();
        AtomicReference<Connection> handle = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable getter = () -> {
            try {
                Connection c = future.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                handle.compareAndSet(null, c);
                served.incrementAndGet();
            } catch (Throwable e) {
                failure.compareAndSet(null, e);
            }
        };
        Thread a = new Thread(getter, "getter-a");
        Thread b = new Thread(getter, "getter-b");
        a.setDaemon(true);
        b.setDaemon(true);
        a.start();
        b.start();
        a.join(AWAIT_TIMEOUT_MS);
        b.join(AWAIT_TIMEOUT_MS);

        Assertions.assertNull(failure.get(),
                "both getters must be served, got " + failure.get());
        Assertions.assertEquals(2, served.get());
        Assertions.assertEquals(1, ds.getActive(),
                "one connection must be borrowed, not two");
        handle.get().close();
        Assertions.assertEquals(1, pool.getReturnedCount() - returnedBefore,
                "the shared connection must be returned once");
        Assertions.assertEquals(1, ds.getIdle());
        ds.close();
    }
}
