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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Expected functionality of the pool-level borrow and return counters
 * that monitoring reads as the {@code BorrowedCount} and
 * {@code ReturnedCount} attributes: every successful hand-off of a
 * connection to a borrower counts as one borrow, no matter which path
 * served it - a newly created connection, an idle one, or a connection a
 * queue hands to an asynchronous poll - while the physical creations
 * counted separately only follow the connections the pool actually opens.
 */
class TestBorrowReturnStatistics extends JdbcPoolBaseTest {

    @Test
    void aBorrowServedByANewConnectionCountsAsABorrow() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.createPool();
        ConnectionPool pool = ds.getPool();
        Assertions.assertEquals(0, pool.getBorrowedCount());
        Assertions.assertEquals(0, pool.getReturnedCount());
        Assertions.assertEquals(0, pool.getCreatedCount());

        // served by creating a connection: the pool has none idle to give
        Connection created = ds.getConnection();
        Assertions.assertEquals(1, pool.getBorrowedCount(),
                "a borrower that received a freshly created connection "
                        + "must count as a borrow, the same way one that "
                        + "received an idle connection does");
        Assertions.assertEquals(1, pool.getCreatedCount(),
                "the physical creation itself must still count as one creation");
        created.close();
        Assertions.assertEquals(1, pool.getReturnedCount());

        // served from idle: the counters must step exactly the same way
        Connection reused = ds.getConnection();
        Assertions.assertEquals(2, pool.getBorrowedCount());
        Assertions.assertEquals(1, pool.getCreatedCount(),
                "reusing the idle connection must not create anything");
        reused.close();
        Assertions.assertEquals(2, pool.getReturnedCount(),
                "once every connection has been returned, the returned "
                        + "count must equal the borrowed count");
        awaitSessions(1);
    }

    @Test
    void preCreatedConnectionsAreNotBorrows() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(2);
        ds.createPool();
        ConnectionPool pool = ds.getPool();
        Assertions.assertEquals(2, pool.getCreatedCount(),
                "the initial connections are physically created");
        Assertions.assertEquals(0, pool.getBorrowedCount(),
                "the pool pre-creating its initialSize connections hands "
                        + "them to no borrower, so none of them is a borrow");
    }

    @Test
    void anAsyncBorrowCountsAsABorrowOnEitherPath() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setFairQueue(true);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        // put one idle connection up for the pool to hand out (a synchronous
        // borrow served by creation)
        ds.getConnection().close();
        Assertions.assertEquals(1, pool.getBorrowedCount());

        // a future for an already available connection: this borrow is served
        // right away, the retrieval from the queue never runs
        Future<Connection> ready = ds.getConnectionAsync();
        Assertions.assertEquals(2, pool.getBorrowedCount(),
                "an async borrow that takes an idle connection is one borrow");
        ready.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS).close();
        Assertions.assertEquals(2, pool.getReturnedCount());

        // a queued future: the only connection is held, so the poll waits on
        // the queue and the connection is handed to it as it comes back
        Connection holder = ds.getConnection();
        Future<Connection> queued = ds.getConnectionAsync();
        Assertions.assertFalse(queued.isDone(),
                "the poll must wait for a connection the pool cannot spare");
        holder.close();
        try (Connection served = queued.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            Assertions.assertEquals(4, pool.getBorrowedCount(),
                    "the connection the queue handed to the waiting poll is "
                            + "one borrow, counted once it is set up for "
                            + "the borrower that retrieves it");
        }
        Assertions.assertEquals(4, pool.getReturnedCount(),
                "once every connection has been returned, the returned "
                        + "count must equal the borrowed count");
        Assertions.assertEquals(1, pool.getCreatedCount(),
                "every borrow here rode the same single physical connection");
        awaitSessions(1);
    }
}
