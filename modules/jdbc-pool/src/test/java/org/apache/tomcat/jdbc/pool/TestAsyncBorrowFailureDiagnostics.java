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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * What the asynchronous borrow reports when it does not produce a connection.
 *
 * <p>A queue hand-off that turns out to be a released connection is the same
 * event the synchronous borrow loops over ("this poll got nothing to hand
 * out"), but an asynchronous poll is served by exactly the one connection its
 * wait produced, so it has to fail its caller instead of retrying. That
 * failure still has to say what happened: an
 * {@link ExecutionException} without a cause leaves the application - and its
 * log - with nothing to work from, and
 * {@link DataSource#getConnectionAsync()} hands the future straight to the
 * caller without compensating.</p>
 *
 * <p>The other half is the noise side of the same future: the cancellation
 * cleanup is a normal, expected completion - whether it reclaims a produced
 * connection and returns it to the pool, or finds the queued wait cancelled
 * by the closing of the pool and closes nothing - and neither case is an
 * error to log.</p>
 */
class TestAsyncBorrowFailureDiagnostics extends JdbcPoolBaseTest {

    /**
     * A queue future whose item has already been handed over, mirroring
     * {@code FairBlockingQueue.ItemFuture}: its {@code get()} re-delivers the
     * same pooled connection to every caller and its {@code cancel()} reports
     * that nothing could be taken back.
     */
    private static final class DeliveredFuture implements Future<PooledConnection> {
        private final PooledConnection pooled;

        DeliveredFuture(PooledConnection pooled) {
            this.pooled = pooled;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            // an item that was already handed over cannot be taken back
            return false;
        }

        @Override
        public PooledConnection get() throws InterruptedException, ExecutionException {
            return pooled;
        }

        @Override
        public PooledConnection get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
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
     * The queue future of a wait that the closing of the pool cancelled
     * around the arming of a cancellation cleanup - the interleaving that
     * cleanup's {@code CancellationException} case exists for. The
     * {@code cancel()} the future itself performs finds a wait it cannot take
     * back, because the closure won the race to it, so it reports the wait
     * cancelled while taking nothing back itself; {@code get()} then fails as
     * {@code FairBlockingQueue.ItemFuture} fails a cancelled wait, with a
     * {@link CancellationException}, and never produces a connection.
     */
    private static final class ClosedQueueWait implements Future<PooledConnection> {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            // the closure is already there, so this cancellation takes
            // nothing back and reports the wait as it is: cancelled
            cancelled.set(true);
            return false;
        }

        @Override
        public PooledConnection get() throws InterruptedException, ExecutionException {
            failAsCancelled();
            return null; // unreachable
        }

        @Override
        public PooledConnection get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            failAsCancelled();
            return null; // unreachable
        }

        private void failAsCancelled() {
            if (!cancelled.get()) {
                // an error here shows up as an error log record below, which
                // is how a cleanup that ran out of order fails this test
                throw new AssertionError("this mock only serves a cancelled wait");
            }
            throw new CancellationException();
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public boolean isDone() {
            return cancelled.get();
        }
    }

    /**
     * Captures what the pool class logs, for the tests that pin silence. The
     * handler goes in before anything runs, and a canary record - written
     * through the very JULI logger the pool logs through, not through JUL
     * directly - proves the wiring observes those records before anything
     * that must stay quiet gets its chance to log.
     */
    private static final class PoolLogCapture implements AutoCloseable {
        private static final String CANARY = "connectionpool-handler-wiring-canary";

        private final Logger logger = Logger.getLogger(ConnectionPool.class.getName());
        private final Level originalLevel = logger.getLevel();
        private final List<LogRecord> records = Collections.synchronizedList(new ArrayList<>());
        private final Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
                // no buffering
            }

            @Override
            public void close() {
                // nothing to release
            }
        };

        PoolLogCapture() {
            logger.addHandler(handler);
            logger.setLevel(Level.ALL);
            LogFactory.getLog(ConnectionPool.class).error(CANARY);
            if (records.stream().noneMatch(r -> CANARY.equals(r.getMessage()))) {
                close();
                throw new AssertionError("the test handler must observe what this class logs");
            }
        }

        void assertNoCancelErrorLogged() {
            LogRecord[] snapshot;
            synchronized (records) {
                snapshot = records.toArray(new LogRecord[0]);
            }
            for (LogRecord record : snapshot) {
                Assertions.assertFalse(record.getLevel().intValue() >= Level.SEVERE.intValue()
                                && record.getMessage() != null
                                && record.getMessage().contains("Unable to cancel ConnectionFuture"),
                        "a cancellation cleanup that completed must not be logged as an error: "
                                + record.getMessage() + " " + record.getThrown());
            }
        }

        @Override
        public void close() {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }
    }

    @Test
    void aConnectionReleasedUnderTheAsyncBorrowFailsWithADescriptiveCause() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setFairQueue(true);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setMaxActive(2);
        ds.getPoolProperties().setMaxIdle(2);
        ds.createPool();
        ConnectionPool pool = ds.getPool();
        long borrowedBefore = pool.getBorrowedCount();

        // the queue produced this connection for the poll...
        PooledConnection pooled = pool.create(true);
        Future<Connection> future = pool.new ConnectionFuture(new DeliveredFuture(pooled));

        // ...and it was released before the poll could set it up, exactly what
        // a racing abandon, purge or close-drain release does to a connection
        // on its way to a borrower; the synchronous borrow loops over such a
        // hand-off, an async poll has only the one connection it was served
        pool.release(pooled);

        ExecutionException failure = Assertions.assertThrows(ExecutionException.class,
                () -> future.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "a borrow whose connection went away must fail its caller");
        Throwable cause = failure.getCause();
        Assertions.assertNotNull(cause,
                "the caller must be told what happened, not handed an empty"
                        + " ExecutionException: " + failure);
        Assertions.assertInstanceOf(SQLException.class, cause,
                "a failed borrow must be reported as a SQL failure");
        String message = cause.getMessage();
        Assertions.assertNotNull(message, "the failure must carry a message");
        Assertions.assertTrue(message.contains("released"),
                "the failure must say that the connection was released, got: " + message);

        // a later get() finds the claim already taken and the wait already
        // over: it is told about the very same failure - the future is
        // single-shot, it neither retries the borrow nor parks forever, with
        // or without a timeout
        ExecutionException again = Assertions.assertThrows(ExecutionException.class,
                () -> future.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "a later get() must keep reporting the failure");
        Assertions.assertSame(cause, again.getCause(),
                "the same published cause must be reported, not an empty one");
        ExecutionException untimed = Assertions.assertThrows(ExecutionException.class,
                () -> future.get(),
                "the untimed get() must report the failure too, not hang");
        Assertions.assertSame(cause, untimed.getCause(),
                "the untimed get() must report the same cause");

        Assertions.assertFalse(future.isCancelled());
        Assertions.assertTrue(future.isDone());
        Assertions.assertEquals(borrowedBefore, pool.getBorrowedCount(),
                "a hand-off that was never set up must not count as a borrow");
        Assertions.assertEquals(0, ds.getIdle(),
                "the released connection must not have been put back in the queue");
        Assertions.assertEquals(0, ds.getActive());

        ds.close();
        awaitSessions(0);
    }

    @Test
    void aSuccessfulCancellationDoesNotLogAnError() throws Exception {
        try (PoolLogCapture logs = new PoolLogCapture()) {
            DataSource ds = newDataSource();
            ds.getPoolProperties().setFairQueue(true);
            ds.getPoolProperties().setInitialSize(0);
            ds.getPoolProperties().setMinIdle(0);
            ds.getPoolProperties().setMaxActive(2);
            ds.getPoolProperties().setMaxIdle(2);
            ds.createPool();
            ConnectionPool pool = ds.getPool();
            long returnedBefore = pool.getReturnedCount();

            // the queue produced its connection but no borrower claimed it:
            // cancel() wins the claim and arms the cleanup, which sets the
            // produced connection up and returns it - the documented, fully
            // successful cancellation, with nothing left to report
            PooledConnection pooled = pool.create(true);
            Future<Connection> future = pool.new ConnectionFuture(new DeliveredFuture(pooled));

            Assertions.assertTrue(future.cancel(true),
                    "a delivered poll nobody claimed must be cancellable");
            awaitUntil(() -> pool.getReturnedCount() - returnedBefore == 1);
            sleep(200); // a late error from the cleanup would show up now

            Assertions.assertEquals(1, pool.getReturnedCount() - returnedBefore,
                    "the cleanup must have returned the connection once");
            Assertions.assertEquals(1, ds.getIdle(),
                    "the reclaimed connection must be idle again");
            Assertions.assertTrue(future.isCancelled());
            Assertions.assertThrows(CancellationException.class,
                    () -> future.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                    "a completed cancellation must be reported as a cancellation");

            logs.assertNoCancelErrorLogged();

            // and the reclaimed connection is there for the next borrower
            try (Connection c = ds.getConnection()) {
                Assertions.assertEquals(1, ds.getActive());
            }
            ds.close();
        }
    }

    @Test
    void aCleanupThatFindsTheQueueWaitCancelledDoesNotLogAnError() throws Exception {
        try (PoolLogCapture logs = new PoolLogCapture()) {
            DataSource ds = newDataSource();
            ds.getPoolProperties().setFairQueue(true);
            ds.getPoolProperties().setInitialSize(0);
            ds.getPoolProperties().setMinIdle(0);
            ds.getPoolProperties().setMaxActive(2);
            ds.getPoolProperties().setMaxIdle(2);
            ds.createPool();
            ConnectionPool pool = ds.getPool();
            long returnedBefore = pool.getReturnedCount();

            // the cleanup task is armed because cancel() could not take the
            // queued wait back itself - the pool closure won that race - and
            // that wait never produced a connection. The cleanup finds the
            // cancellation, closes nothing and has nothing to report: an
            // expected outcome, not an error.
            Future<Connection> future = pool.new ConnectionFuture(new ClosedQueueWait());

            Assertions.assertTrue(future.cancel(true),
                    "the cleanup arms even when the queue future was lost");
            sleep(200); // a late error from the cleanup would show up now

            Assertions.assertEquals(0, pool.getReturnedCount() - returnedBefore,
                    "a wait that produced nothing has no connection to return");
            Assertions.assertEquals(0, ds.getIdle());
            Assertions.assertEquals(0, ds.getActive());
            Assertions.assertTrue(future.isCancelled());
            Assertions.assertTrue(future.isDone());
            Assertions.assertThrows(CancellationException.class,
                    () -> future.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                    "the caller is told about the cancellation");

            logs.assertNoCancelErrorLogged();

            ds.close();
        }
    }
}
