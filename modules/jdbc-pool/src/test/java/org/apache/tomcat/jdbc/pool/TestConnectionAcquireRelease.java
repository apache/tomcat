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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Expected functionality of borrowing connections from the pool,
 * returning them, and reusing them. All assertions about physical
 * connections are made against the database, not pool statistics.
 */
class TestConnectionAcquireRelease extends JdbcPoolBaseTest {

    @Test
    void initialSizePreCreatesPhysicalConnections() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(2);
        ds.createPool();
        Assertions.assertEquals(2, poolSessions(),
                "initialSize connections must exist before any borrow");
    }

    @Test
    void getConnectionReturnsUsableConnection() throws Exception {
        DataSource ds = newDataSource();
        try (Connection c = ds.getConnection()) {
            Assertions.assertNotNull(c);
            Assertions.assertFalse(c.isClosed());
            Assertions.assertTrue(c.isValid(2));
            Assertions.assertEquals("H2", c.getMetaData().getDatabaseProductName());
            try (Statement st = c.createStatement();
                    ResultSet rs = st.executeQuery("SELECT 1")) {
                Assertions.assertTrue(rs.next());
                Assertions.assertEquals(1, rs.getInt(1));
            }
        }
    }

    @Test
    void closeReturnsConnectionToPoolWithoutPhysicalClose() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMaxActive(1);

        Connection c1 = ds.getConnection();
        setSessionMarker(c1, 777);
        c1.close();
        Assertions.assertTrue(c1.isClosed(), "closed proxy must report isClosed()");
        Assertions.assertEquals(1, poolSessions(),
                "close() must not physically close the pooled connection");

        try (Connection c2 = ds.getConnection()) {
            Integer marker = sessionMarker(c2);
            Assertions.assertNotNull(marker, "same physical session must have been reused");
            Assertions.assertEquals(777, marker.intValue());
            Assertions.assertEquals(1, poolSessions(), "reuse must not open a new session");
        }
    }

    @Test
    void doubleCloseIsHarmlessAndKeepsConnectionReusable() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMaxActive(1);
        Connection c = ds.getConnection();
        setSessionMarker(c, 11);
        c.close();
        c.close(); // documented to be a no-op
        try (Connection again = ds.getConnection()) {
            Integer marker = sessionMarker(again);
            Assertions.assertNotNull(marker);
            Assertions.assertEquals(11, marker.intValue());
        }
    }

    @Test
    void poolGrowsToMaxActiveAndRejectsBeyondItAfterMaxWait() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMaxActive(5);
        ds.getPoolProperties().setMaxWait(300);

        List<Connection> held = new ArrayList<>();
        try {
            for (int i = 0; i < 5; i++) {
                held.add(ds.getConnection());
            }
            Assertions.assertEquals(5, poolSessions(),
                    "each outstanding borrow must be its own physical session");
            Assertions.assertThrows(SQLException.class, ds::getConnection,
                    "exhausted pool must throw after maxWait");
        } finally {
            for (Connection c : held) {
                c.close();
            }
        }
    }

    @Test
    void waiterReceivesReleasedConnection() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxWait(10000);

        final Connection held = ds.getConnection();
        setSessionMarker(held, 42);

        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicInteger observedMarker = new AtomicInteger(-1);
        Thread waiter = new Thread(() -> {
            try (Connection c = ds.getConnection()) {
                Integer marker = sessionMarker(c);
                if (marker != null) {
                    observedMarker.set(marker.intValue());
                }
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        waiter.start();

        awaitUntil(() -> ds.getWaitCount() == 1); // the waiter is parked on the exhausted pool
        held.close();

        Assertions.assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS),
                "waiter should have obtained a connection");
        Assertions.assertNull(failure.get());
        Assertions.assertEquals(42, observedMarker.get(),
                "waiter must have received the released physical session");
    }

    @Test
    void exhaustionThrowsAfterMaxWaitAndRecoveryWorks() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxWait(300);

        Connection held = ds.getConnection();
        long start = System.currentTimeMillis();
        Assertions.assertThrows(SQLException.class, ds::getConnection);
        long elapsed = System.currentTimeMillis() - start;
        Assertions.assertTrue(elapsed >= 250 && elapsed < 5000,
                "timeout should be honoured approximately, was " + elapsed + " ms");

        held.close();
        try (Connection recovered = ds.getConnection()) {
            try (Statement st = recovered.createStatement();
                    ResultSet rs = st.executeQuery("SELECT 1")) {
                Assertions.assertTrue(rs.next());
            }
        }
    }

    @Test
    void dataSourceClosePhysicallyReleasesAllConnections() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMaxActive(2);
        ds.getConnection().close();
        ds.getConnection().close();
        Assertions.assertEquals(1, poolSessions()); // one pooled connection, reused
        ds.close();
        awaitSessions(0);
    }

    @Test
    void dataSourcesAreIsolatedAndClosingOneLeavesTheOther() throws Exception {
        DataSource ds1 = newDataSource();
        DataSource ds2 = newDataSource();
        ds1.getPoolProperties().setMaxActive(1);
        ds2.getPoolProperties().setMaxActive(1);

        ds1.getConnection();
        Connection c2 = ds2.getConnection();
        setSessionMarker(c2, 22);
        Assertions.assertEquals(2, poolSessions());

        ds1.close();
        awaitSessions(1);
        // the surviving pool is untouched by the other's shutdown
        Integer marker = sessionMarker(c2);
        Assertions.assertNotNull(marker);
        Assertions.assertEquals(22, marker.intValue());
        c2.close();
    }

    @Test
    void testOnBorrowWithValidationQuery() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setTestOnBorrow(true);
        ds.getPoolProperties().setValidationQuery("SELECT 1");
        try (Connection c = ds.getConnection()) {
            try (Statement st = c.createStatement();
                    ResultSet rs = st.executeQuery("SELECT 2")) {
                Assertions.assertTrue(rs.next());
                Assertions.assertEquals(2, rs.getInt(1));
            }
        }
        Assertions.assertEquals(1, poolSessions(), "validation must not consume sessions");
    }

    @Test
    void testOnBorrowWithIsValidWhenNoValidationQuery() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setTestOnBorrow(true);
        ds.getPoolProperties().setValidationQuery("");
        try (Connection c = ds.getConnection()) {
            Assertions.assertTrue(c.isValid(2));
        }
        Assertions.assertEquals(1, poolSessions());
    }

    @Test
    void testOnReturnValidatesWithoutBreakingReuse() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setTestOnReturn(true);
        ds.getPoolProperties().setValidationQuery("SELECT 1");
        ds.getPoolProperties().setMaxActive(1);
        Connection c1 = ds.getConnection();
        setSessionMarker(c1, 5);
        c1.close();
        try (Connection c2 = ds.getConnection()) {
            Integer marker = sessionMarker(c2);
            Assertions.assertNotNull(marker, "validated connection must be reused, not replaced");
            Assertions.assertEquals(5, marker.intValue());
        }
    }

    @Test
    void abandonedConnectionIsReclaimedByMaintenance() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setRemoveAbandoned(true);
        ds.getPoolProperties().setRemoveAbandonedTimeout(1); // seconds
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(200);
        ds.getPoolProperties().setMaxWait(15000);

        ds.getConnection(); // borrowed and never returned -> abandoned

        long start = System.currentTimeMillis();
        try (Connection reclaimed = ds.getConnection()) {
            long elapsed = System.currentTimeMillis() - start;
            Assertions.assertTrue(elapsed >= 800,
                    "reclaim must wait out the abandoned timeout, was " + elapsed + " ms");
            try (Statement st = reclaimed.createStatement();
                    ResultSet rs = st.executeQuery("SELECT 1")) {
                Assertions.assertTrue(rs.next());
            }
        }
        Assertions.assertTrue(poolSessions() <= 1,
                "the abandoned physical connection must not be left dangling");
    }

}
