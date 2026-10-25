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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Expected functionality of the asynchronous borrow API
 * ({@code getConnectionAsync()}): the same accounting, the same
 * database-side truth and the same delivery guarantees as the blocking
 * API, and a clear failure when the pool is configured with a queue that
 * cannot hand out futures.
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

        assertEquals(0, ds.getSize(), "an empty pool must hold no connections");
        assertEquals(0, ds.getIdle());
        assertEquals(0, ds.getActive());
        assertEquals(0, poolSessions(), "the database must not see any session yet");

        ds.getConnection().close();
        assertEquals(1, ds.getSize(), "the pool must keep the connection it created");
        assertEquals(1, ds.getIdle());
        assertEquals(0, ds.getActive());
        awaitSessions(1);

        Future<Connection> future = ds.getConnectionAsync();
        try (Connection c = future.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            assertNotNull(c, "the future must hand out a connection");
            assertEquals(1, ds.getActive(), "an async borrow must count as active");
            assertEquals(0, ds.getIdle());
            assertEquals(1, poolSessions(),
                    "the async borrow must reuse the pooled connection, not open a second one");
            try (ResultSet rs = c.createStatement().executeQuery("SELECT 1")) {
                assertTrue(rs.next(), "the asynchronously borrowed connection must work");
            }
        }
        assertEquals(0, ds.getActive(), "the returned connection must stop counting as active");
        assertEquals(1, ds.getIdle());
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
        assertEquals(0, poolSessions());

        Future<Connection> future = ds.getConnectionAsync();
        try (Connection c = future.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            assertNotNull(c, "an empty fair pool must create the connection on demand");
            try (ResultSet rs = c.createStatement().executeQuery("SELECT 1")) {
                assertTrue(rs.next());
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
            SQLException error = assertThrows(SQLException.class, ds::getConnectionAsync,
                    "a non-fair queue cannot hand out futures once the pool is exhausted");
            assertTrue(error.getMessage().toLowerCase().contains("fair"),
                    "the failure must name the missing configuration, was: " + error.getMessage());
        } finally {
            holder.close();
        }
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

        assertEquals(0, failed.get(), "no async borrower may fail");
        assertEquals(borrowers, served.get(), "every async borrower must be served");
        assertEquals(0, ds.getActive());
        assertEquals(2, ds.getIdle(), "the pool must keep at most maxActive connections");
        awaitSessions(2);
    }
}
