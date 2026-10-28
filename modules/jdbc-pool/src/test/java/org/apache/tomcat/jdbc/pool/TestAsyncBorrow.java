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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Expected functionality of the asynchronous borrow API
 * ({@code getConnectionAsync()}): the same accounting, the same
 * database-side truth and the same delivery guarantees as the blocking
 * API, a clear failure when the pool is configured with a queue that
 * cannot hand out futures, and the termination of an outstanding poll
 * once the pool is closed.
 *
 * <p>Scenario harvested from the retired legacy classes
 * {@code Bug50805} (async borrow accounting) and
 * {@code Async0IdleTestBug50477} (async borrow from a fair pool that has
 * no idle connections yet), neither of which ran in the old build.</p>
 */
class TestAsyncBorrow extends JdbcPoolBaseTest {

    @Test
    void asyncBorrowKeepsAccountingConsistentWithTheDatabase() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(1);
        ds.getPoolProperties().setMaxActive(10);
        ds.createPool();

        Assertions.assertEquals(0, ds.getSize(), "an empty pool must hold no connections");
        Assertions.assertEquals(0, ds.getIdle());
        Assertions.assertEquals(0, ds.getActive());
        Assertions.assertEquals(0, poolSessions(), "the database must not see any session yet");

        ds.getConnection().close();
        Assertions.assertEquals(1, ds.getSize(), "the pool must keep the connection it created");
        Assertions.assertEquals(1, ds.getIdle());
        Assertions.assertEquals(0, ds.getActive());
        awaitSessions(1);

        Future<Connection> future = ds.getConnectionAsync();
        try (Connection c = future.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            Assertions.assertNotNull(c, "the future must hand out a connection");
            Assertions.assertEquals(1, ds.getActive(), "an async borrow must count as active");
            Assertions.assertEquals(0, ds.getIdle());
            Assertions.assertEquals(1, poolSessions(),
                    "the async borrow must reuse the pooled connection, not open a second one");
            try (ResultSet rs = c.createStatement().executeQuery("SELECT 1")) {
                Assertions.assertTrue(rs.next(),
                        "the asynchronously borrowed connection must work");
            }
        }
        Assertions.assertEquals(0, ds.getActive(),
                "the returned connection must stop counting as active");
        Assertions.assertEquals(1, ds.getIdle());
        awaitSessions(1);
    }

    @Test
    void asyncBorrowFromAnEmptyFairPoolEstablishesTheFirstConnection() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setFairQueue(true);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setMaxActive(5);
        ds.createPool();
        Assertions.assertEquals(0, poolSessions());

        Future<Connection> future = ds.getConnectionAsync();
        try (Connection c = future.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            Assertions.assertNotNull(c, "an empty fair pool must create the connection on demand");
            try (ResultSet rs = c.createStatement().executeQuery("SELECT 1")) {
                Assertions.assertTrue(rs.next());
            }
        }
        awaitSessions(1);
    }

    @Test
    void asyncBorrowOnAnExhaustedPoolNeedsTheFairQueue() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setFairQueue(false);
        ds.getPoolProperties().setMaxActive(1);
        ds.createPool();

        Connection holder = ds.getConnection();
        try {
            SQLException error = Assertions.assertThrows(SQLException.class, ds::getConnectionAsync,
                    "a non-fair queue cannot hand out futures once the pool is exhausted");
            Assertions.assertTrue(error.getMessage().toLowerCase().contains("fair"),
                    "the failure must name the missing configuration, was: " + error.getMessage());
        } finally {
            holder.close();
        }
    }

    @Test
    void asyncBorrowFailureWithoutAMessageStillSurfacesAsSqlException()
            throws Exception {
        // A failing connector whose exception carries no message: the pool
        // wraps it as a message-less SQLException, and the async borrow must
        // rethrow that failure rather than dereference its missing message
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.setDataSource(new javax.sql.DataSource() {
            @Override
            public Connection getConnection() {
                throw new IllegalStateException();
            }

            @Override
            public Connection getConnection(String user, String password) {
                throw new IllegalStateException();
            }

            @Override
            public java.io.PrintWriter getLogWriter() {
                return null;
            }

            @Override
            public void setLogWriter(java.io.PrintWriter out) {
                // no logging
            }

            @Override
            public void setLoginTimeout(int seconds) {
                // no timeout
            }

            @Override
            public int getLoginTimeout() {
                return 0;
            }

            @Override
            public java.util.logging.Logger getParentLogger()
                    throws java.sql.SQLFeatureNotSupportedException {
                throw new java.sql.SQLFeatureNotSupportedException();
            }

            @Override
            public <T> T unwrap(Class<T> iface) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) {
                return false;
            }
        });

        SQLException failure = Assertions.assertThrows(SQLException.class,
                ds::getConnectionAsync,
                "the borrow failure must reach the caller as a SQLException "
                        + "even when its message is null");
        Assertions.assertNull(failure.getMessage(),
                "the rethrown failure must keep its absent message");
        Assertions.assertTrue(failure.getCause() instanceof IllegalStateException,
                "the wrapped connector failure must be preserved as cause");
    }

    @Test
    void concurrentAsyncBorrowersAreAllServed() throws Exception {
        final int borrowers = 6;
        DataSource ds = newDataSource();
        ds.getPoolProperties().setFairQueue(true);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMaxActive(2);
        ds.createPool();

        final AtomicInteger served = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
        Thread[] threads = new Thread[borrowers];
        for (int i = 0; i < borrowers; i++) {
            threads[i] = new Thread(() -> {
                try {
                    Future<Connection> future = ds.getConnectionAsync();
                    try (Connection c = future.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                        c.createStatement().execute("SELECT 1");
                        served.incrementAndGet();
                    }
                } catch (Exception e) {
                    failed.incrementAndGet();
                }
            }, "async-borrower-" + i);
            threads[i].start();
        }
        for (Thread thread : threads) {
            thread.join(AWAIT_TIMEOUT_MS);
        }

        Assertions.assertEquals(0, failed.get(), "no async borrower may fail");
        Assertions.assertEquals(borrowers, served.get(), "every async borrower must be served");
        Assertions.assertEquals(0, ds.getActive());
        Assertions.assertEquals(2, ds.getIdle(),
                "the pool must keep at most maxActive connections");
        awaitSessions(2);
    }

    @Test
    void closingThePoolTerminatesAnAsyncPollThatIsBlockedInGet() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setFairQueue(true);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxIdle(1);
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        ds.getConnection(); // takes the only slot, and the pool is at maxActive

        // the borrow below cannot be served by a new connection, so it has to
        // register a waiter on the idle queue and wait for a returned one
        Future<Connection> future = ds.getConnectionAsync();
        Assertions.assertFalse(future.isDone(),
                "the poll must still be waiting for a connection");

        CountDownLatch parked = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            parked.countDown();
            try {
                future.get(); // the documented, untimed usage
            } catch (Throwable e) {
                failure.set(e);
            } finally {
                finished.countDown();
            }
        }, "blocked-async-borrower");
        caller.setDaemon(true);
        caller.start();
        Assertions.assertTrue(parked.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        sleep(200); // let the caller reach the wait on the idle queue

        ds.close(true);

        // The caller cannot observe the closed pool itself - it is inside the
        // queue wait, and a closed pool never offers a connection to that queue
        // again. Closing the pool has to terminate the future instead.
        Assertions.assertTrue(finished.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "closing the pool must release a caller blocked in get()");
        Assertions.assertTrue(failure.get() instanceof CancellationException,
                "a pool closure must fail an outstanding poll with a "
                        + "cancellation, got " + failure.get());
        Assertions.assertTrue(future.isDone());
        Assertions.assertTrue(future.isCancelled(),
                "an outstanding poll must be cancelled by the pool closure");
        Assertions.assertThrows(CancellationException.class, future::get,
                "a completed poll must keep reporting its cancellation");

        // and the borrow path itself reports the closure the same way a
        // synchronous borrow does
        Assertions.assertThrows(SQLException.class, pool::getConnectionAsync,
                "an async borrow on a closed pool must be rejected");
        awaitSessions(0);
    }

    @Test
    void cancellingAnAsyncPollAfterThePoolClosedReportsNothingCancelled() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setFairQueue(true);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxIdle(1);
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        ds.getConnection(); // takes the only slot, so the poll stays queued
        Future<Connection> future = ds.getConnectionAsync();

        ds.close(true);

        // the closure has already cancelled the queued poll
        Assertions.assertTrue(future.isCancelled());
        Assertions.assertTrue(future.isDone());

        // a cancel() after the fact must report nothing cancelled, as the
        // future is already complete, and must not arm the deferred cleanup
        // task, which would only run to observe that same cancellation
        Assertions.assertFalse(future.cancel(true),
                "a poll the pool closure already cancelled must not report "
                        + "a further cancellation");
        Assertions.assertTrue(future.isCancelled());
        Assertions.assertTrue(future.isDone());
        Assertions.assertThrows(CancellationException.class, future::get,
                "the completed cancellation must keep being reported");

        // the refused cancel must not act on any connection: beyond what the
        // closure itself already released, the pool released nothing
        long releasedAfterClose = pool.getReleasedCount();
        sleep(200); // a cleanup task scheduled by cancel() would have run
        Assertions.assertEquals(releasedAfterClose, pool.getReleasedCount(),
                "a refused cancel must not retrieve or release any connection");
        awaitSessions(0);
    }
}
