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
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Closing a data source must be serialized against pool creation and must
 * be terminal. Unsynchronized, a {@code close()} racing the first
 * {@code getConnection()} observed {@code pool == null}, returned without
 * closing anything, and the concurrently created pool was published
 * afterwards - live, unclosed, with its database sessions and scheduled
 * cleaner no longer reachable through the closed data source. Likewise,
 * any later borrow or statistics access silently resurrected a fresh pool
 * on a data source its owner had already stopped tracking.
 */
class TestCloseVsPoolCreation extends JdbcPoolBaseTest {

    /* A driver that parks inside connect() until the test releases it,
       so pool creation can be held mid-flight while close() is invoked.
       It signals when it enters connect(). */
    public static class ParkingConnectDriver implements Driver {

        static final CountDownLatch CONNECT_STARTED = new CountDownLatch(1);
        static final CountDownLatch CONNECT_RELEASE = new CountDownLatch(1);

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            CONNECT_STARTED.countDown();
            try {
                CONNECT_RELEASE.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SQLException(e);
            }
            return new org.h2.Driver().connect(url, info);
        }

        @Override
        public boolean acceptsURL(String url) {
            return url != null && url.startsWith("jdbc:h2:");
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 0;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }

    @Test
    void closeRacingPoolCreationClosesThePoolInsteadOfLeakingIt() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setDriverClassName(ParkingConnectDriver.class.getName());
        // initialSize=1 makes the physical connect part of pool creation,
        // which is the window the close must be serialized against
        ds.getPoolProperties().setInitialSize(1);

        AtomicReference<Connection> borrowed = new AtomicReference<>();
        Thread creator = new Thread(() -> {
            try {
                borrowed.set(ds.getConnection());
            } catch (SQLException e) {
                // Rejected by the pool-level closed guard - expected when
                // the close wins the race; see TestCloseDuringBorrow
            }
        }, "creator racing close");
        creator.setDaemon(true);
        creator.start();
        try {
            Assertions.assertTrue(ParkingConnectDriver.CONNECT_STARTED
                    .await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                    "pool creation must be parked inside the driver's connect()");

            // close() while the pool is still being created: it must not
            // observe the not-yet-published pool as "nothing to close" and
            // return - it has to wait for the creation and then tear it
            // down. (Whether the close also wins against a merely
            // unscheduled creator is not what this asserts; the session
            // and resurrection assertions below carry the regression.)
            AtomicReference<Throwable> closeFailure = new AtomicReference<>();
            Thread closer = new Thread(() -> {
                try {
                    ds.close(true);
                } catch (Throwable e) {
                    closeFailure.set(e);
                }
            }, "close racing creation");
            closer.setDaemon(true);
            closer.start();

            closer.join(1000);
            Assertions.assertTrue(closer.isAlive(),
                    "close() must wait for the in-flight pool creation instead of"
                            + " returning while the pool is still unpublished");

            ParkingConnectDriver.CONNECT_RELEASE.countDown();
            closer.join(AWAIT_TIMEOUT_MS);
            creator.join(AWAIT_TIMEOUT_MS);
            Assertions.assertFalse(creator.isAlive(),
                    "the creating thread must not outlive the test");
            Assertions.assertNull(closeFailure.get(), "close() must not fail");

            if (borrowed.get() != null) {
                // The borrow won the race against the closure by a hair; the
                // connection must be releasable so the closed pool drains it.
                borrowed.get().close();
            }
            // With the leak, this pool would still own its session: the close
            // ran against a null field and the published pool was never closed.
            // (Whether the racing borrow itself failed or merely got its
            // connection torn down is not deterministic here; TestCloseDuring-
            // Borrow pins that behavior down.)
            awaitSessions(0);

            // The closure is terminal: no later access may resurrect a pool
            SQLException x = Assertions.assertThrows(SQLException.class, ds::getConnection,
                    "a closed data source must reject borrows instead of recreating the pool");
            Assertions.assertTrue(x.getMessage() != null && x.getMessage().contains("closed"),
                    "the rejection must say the data source is closed, was: " + x.getMessage());
            Assertions.assertNull(ds.getPool(),
                    "getPool() must not create a pool on a closed data source");
            Assertions.assertEquals(0, poolSessions(),
                    "no database session may exist after the close");
        } finally {
            // Never leave the creator parked inside connect(): it holds
            // the data source monitor there, so the base class teardown
            // (ds.close()) would block forever and hang the suite.
            ParkingConnectDriver.CONNECT_RELEASE.countDown();
            creator.join(AWAIT_TIMEOUT_MS);
        }
    }

    @Test
    void closedDataSourceDoesNotResurrectItsPool() throws Exception {
        DataSource ds = newDataSource();
        ds.getConnection().close();
        Assertions.assertEquals(1, poolSessions(),
                "the pool must own its connection before the close");
        ds.close();
        awaitSessions(0);

        SQLException x = Assertions.assertThrows(SQLException.class, ds::getConnection,
                "a closed data source must reject borrows instead of recreating the pool");
        Assertions.assertTrue(x.getMessage() != null && x.getMessage().contains("closed"),
                "the rejection must say the data source is closed, was: " + x.getMessage());
        Assertions.assertEquals(0, poolSessions(), "no new pool may be created after close()");

        // A failing pool creation surfaces through getPool() as null...
        Assertions.assertNull(ds.getPool());
        // ...while the explicit creation attempt reports the reason
        Assertions.assertNotNull(Assertions.assertThrows(SQLException.class, ds::createPool));
    }
}
