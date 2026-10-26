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
 * A borrower whose physical connect() is still in flight when the pool is
 * force-closed must not be handed the connection it is creating: the drain
 * has already passed over the queues, so that connection would sit in
 * {@code busy} of a closed pool, never closed again. The borrow side, like
 * the return side, has to honour the closed flag at its point of no return.
 */
class TestCloseDuringBorrow extends JdbcPoolBaseTest {

    /* A driver whose connect() is slow enough for a close() to complete
       while a connection attempt is in flight. It signals when it enters
       connect() so the test can close the pool mid-attempt deterministically. */
    public static class SlowConnectDriver implements Driver {

        static final CountDownLatch CONNECT_STARTED = new CountDownLatch(1);
        static final long CONNECT_DELAY_MS = 1500;

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            CONNECT_STARTED.countDown();
            try {
                Thread.sleep(CONNECT_DELAY_MS);
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
    void closeDuringInFlightCreateDoesNotHandOutTheNewConnection() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setDriverClassName(SlowConnectDriver.class.getName());
        ds.getPoolProperties().setInitialSize(0);
        ds.createPool();

        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Connection> borrowed = new AtomicReference<>();
        Thread borrower = new Thread(() -> {
            try {
                borrowed.set(ds.getConnection());
            } catch (Exception e) {
                failure.set(e);
            }
        }, "borrower-in-flight-during-close");
        borrower.setDaemon(true);
        borrower.start();

        // close while the physical connect() is in flight: the driver signals
        // when it enters connect() and sleeps there for CONNECT_DELAY_MS
        Assertions.assertTrue(SlowConnectDriver.CONNECT_STARTED.await(AWAIT_TIMEOUT_MS,
                        TimeUnit.MILLISECONDS),
                "the borrower must be inside the driver's connect()");
        ds.close(true);

        borrower.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(borrower.isAlive(), "the borrower must not outlive the close");

        Assertions.assertNotNull(failure.get(),
                "the borrower must not be handed a connection from the closed pool");
        Assertions.assertTrue(failure.get() instanceof SQLException,
                "the borrower must be rejected with a SQLException, not " + failure.get());
        Assertions.assertNull(borrowed.get());
        awaitSessions(0);
    }
}
