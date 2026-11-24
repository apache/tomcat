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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A reconnect that fails after the replacement session has been established
 * restores the served session and its bookkeeping. The discarded flag is part
 * of that bookkeeping: the failed attempt must not leave a discarded served
 * session looking live.
 */
abstract class ReconnectWithDiscardedRestoreBase extends JdbcPoolBaseTest {

    /** A pooled connection whose post-connect JMX registration fails after the new session is up. */
    private static final class ThrowingCreateMBeanConnection extends PooledConnection {
        volatile boolean failCreateMBean = false;

        ThrowingCreateMBeanConnection(PoolConfiguration prop, ConnectionPool parent) {
            super(prop, parent);
        }

        @Override
        public synchronized void createMBean() {
            if (failCreateMBean) {
                throw new LinkageError("createMBean failure after the replacement session was established");
            }
            super.createMBean();
        }
    }

    /**
     * Blocks in the interceptor notification a reconnect sends for the served
     * session, so a test can observe what the connection monitor is doing
     * while that hook runs. Configured through the jdbcInterceptors class-name
     * list, so it must stay a public static class with a no-arg constructor.
     */
    public static class BlockingDisconnectedInterceptor extends JdbcInterceptor {

        static volatile CountDownLatch entered = new CountDownLatch(0);
        static volatile CountDownLatch release = new CountDownLatch(0);

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // NOOP
        }

        @Override
        public void disconnected(ConnectionPool parent, PooledConnection con, boolean finalizing) {
            entered.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException x) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void theServedSessionNotificationDoesNotHoldTheConnectionMonitor() throws Exception {
        BlockingDisconnectedInterceptor.entered = new CountDownLatch(1);
        BlockingDisconnectedInterceptor.release = new CountDownLatch(1);
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1);
        ds.getPoolProperties().setJdbcInterceptors(BlockingDisconnectedInterceptor.class.getName());
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        PooledConnection connection = new PooledConnection(pool.getPoolProperties(), pool);
        try {
            connection.connect();
            Assertions.assertNotNull(pool.setupConnection(connection),
                    "precondition: the connection must have an interceptor chain");

            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread reconnector = new Thread(() -> {
                try {
                    connection.reconnectWith(null, null);
                } catch (Throwable x) {
                    failure.set(x);
                }
            }, "reconnect-with-notification");
            CountDownLatch setterDone = new CountDownLatch(1);
            Thread setter = new Thread(() -> {
                connection.setDiscarded(false);
                setterDone.countDown();
            }, "synchronized-state-write");
            try {
                reconnector.start();
                Assertions.assertTrue(BlockingDisconnectedInterceptor.entered.await(10, TimeUnit.SECONDS),
                        "the served session's notification must have started");
                setter.start();
                Assertions.assertTrue(setterDone.await(2, TimeUnit.SECONDS),
                        "a synchronized state transition must not wait behind a blocking "
                                + "interceptor notification");
                BlockingDisconnectedInterceptor.release.countDown();
                reconnector.join(TimeUnit.SECONDS.toMillis(10));
                setter.join(TimeUnit.SECONDS.toMillis(10));
                Assertions.assertFalse(reconnector.isAlive(), "the reconnect must finish");
                Assertions.assertNull(failure.get(), () -> "the reconnect failed: " + failure.get());
                Assertions.assertNotNull(connection.getConnection(),
                        "the reconnect must publish the replacement session");
            } finally {
                BlockingDisconnectedInterceptor.release.countDown();
                reconnector.join(TimeUnit.SECONDS.toMillis(10));
                setter.join(TimeUnit.SECONDS.toMillis(10));
            }
        } finally {
            connection.release();
            pool.close(true);
        }
    }

    @Test
    void aFailureAfterConnectRestoresTheDiscardedStateOfTheServedSession() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1);
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        ThrowingCreateMBeanConnection connection =
                new ThrowingCreateMBeanConnection(pool.getPoolProperties(), pool);
        try {
            connection.connect();
            connection.setDiscarded(true);

            // Give connect() a JMX wrapper to reach, then make its creation fail after the new session is up.
            pool.createMBean();
            connection.failCreateMBean = true;

            Assertions.assertThrows(LinkageError.class, () -> connection.reconnectWith(null, null),
                    "the post-connect failure must be propagated to the caller");

            Assertions.assertTrue(connection.isDiscarded(),
                    "the failed reconnect must restore the discarded state of the served session");
            Assertions.assertNotNull(connection.getConnection(),
                    "the served session must still be in place after the failed reconnect");
        } finally {
            connection.release();
            pool.close(true);
        }
    }
}
