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
 * Closing a data source must be serialized against the publication of its
 * pool and must be terminal. Unsynchronized, a {@code close()} racing the
 * first {@code getConnection()} observed {@code pool == null}, returned
 * without closing anything, and the concurrently created pool was published
 * afterwards - live, unclosed, with its database sessions and scheduled
 * cleaner no longer reachable through the closed data source. Likewise, any
 * later borrow or statistics access silently resurrected a fresh pool on a
 * data source its owner had already stopped tracking. The construction
 * itself runs outside the monitor the close takes, so the close is never
 * parked behind a driver connect: a creation already in flight observes the
 * closure at its publication step and tears its result down itself.
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
        AtomicReference<SQLException> borrowFailure = new AtomicReference<>();
        Thread creator = new Thread(() -> {
            try {
                borrowed.set(ds.getConnection());
            } catch (SQLException e) {
                borrowFailure.set(e);
            }
        }, "creator racing close");
        creator.setDaemon(true);
        creator.start();
        try {
            Assertions.assertTrue(ParkingConnectDriver.CONNECT_STARTED
                    .await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                    "pool creation must be parked inside the driver's connect()");

            // close() while the pool is still being created: the close must
            // not be parked behind the driver's connect() the creation is
            // blocked in - the construction runs outside the monitor the
            // close takes - and the still-unpublished pool must not be lost
            // either: whichever of the two reaches the monitor first owns
            // the teardown, so the pool the parked creation later finishes
            // is closed by that creation itself when it finds the closure.
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

            closer.join(AWAIT_TIMEOUT_MS);
            Assertions.assertFalse(closer.isAlive(),
                    "close() must not block behind the in-flight pool creation:"
                            + " parking it on the driver's connect() against a hung"
                            + " database would park the shutdown forever");
            Assertions.assertNull(closeFailure.get(), "close() must not fail");

            ParkingConnectDriver.CONNECT_RELEASE.countDown();
            creator.join(AWAIT_TIMEOUT_MS);
            Assertions.assertFalse(creator.isAlive(),
                    "the creating thread must not outlive the test");

            if (borrowed.get() != null) {
                // The borrow published before the closure and won the race
                // by a hair; the connection must be releasable so the closed
                // pool drains it.
                borrowed.get().close();
            } else {
                // The closure won: the creation must have been rejected, and
                // told so as a closed data source, not left to hang.
                Assertions.assertNotNull(borrowFailure.get(),
                        "the losing creation must have been rejected");
                Assertions.assertTrue(borrowFailure.get().getMessage() != null
                                && borrowFailure.get().getMessage().contains("closed"),
                        "the rejection must say the data source is closed, was: "
                                + borrowFailure.get().getMessage());
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
            // Release and reap the parked creation even on a red run. It no
            // longer holds the data source monitor inside connect(), so the
            // base class teardown (ds.close()) cannot be blocked by it, but
            // it must not linger past the test still owning a half-built
            // pool whose connect() then completes and publishes nothing.
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
