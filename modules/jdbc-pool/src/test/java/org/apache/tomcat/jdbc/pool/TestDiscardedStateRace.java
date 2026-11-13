/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.jdbc.pool;

import java.sql.SQLException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * {@link PooledConnection#setDiscarded(boolean)} documents that once the connection has been marked discarded, a later
 * request to clear the mark must fail. The check and the write must therefore be one atomic step: a request that reads
 * the flag before another sets it, and writes afterwards, silently resurrects a connection the pool retired.
 */
class TestDiscardedStateRace extends JdbcPoolBaseTest {

    private static final int ROUNDS = 20000;
    private static final int CLEARERS = 3;

    /**
     * Marks itself discarded while its replacement session is being established, the way an interceptor can while
     * the pool is inside the driver call.
     */
    private static final class DiscardWhileConnectingConnection extends PooledConnection {
        DiscardWhileConnectingConnection(PoolConfiguration prop, ConnectionPool parent) {
            super(prop, parent);
        }

        @Override
        protected void connectUsingDriver() throws SQLException {
            super.connectUsingDriver();
            setDiscarded(true);
        }
    }

    @Test
    void connectDoesNotClearADiscardRaisedWhileTheNewSessionIsEstablished() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1);
        ds.createPool();

        DiscardWhileConnectingConnection con =
                new DiscardWhileConnectingConnection(ds.getPoolProperties(), ds.getPool());
        try {
            con.connect();
            Assertions.assertTrue(con.isDiscarded(),
                    "a discard raised during the new session's connect must survive connect() completing");
            Assertions.assertNotNull(con.getConnection(), "the new physical session must be in place");
        } finally {
            con.release();
            ds.close(true);
        }
    }

    @Test
    void aConcurrentClearCannotResurrectADiscardedConnection() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJmxEnabled(false);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1);
        ds.createPool();
        ExecutorService executor = Executors.newFixedThreadPool(CLEARERS + 1);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                PooledConnection con = new PooledConnection(ds.getPoolProperties(), ds.getPool());
                CyclicBarrier start = new CyclicBarrier(CLEARERS + 1);
                AtomicBoolean cleared = new AtomicBoolean();
                AtomicReference<Throwable> unexpected = new AtomicReference<>();

                Future<?> discard = executor.submit(() -> {
                    try {
                        start.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        con.setDiscarded(true);
                    } catch (Throwable x) {
                        unexpected.compareAndSet(null, x);
                    }
                });
                Future<?>[] clearers = new Future<?>[CLEARERS];
                for (int i = 0; i < CLEARERS; i++) {
                    clearers[i] = executor.submit(() -> {
                        try {
                            start.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                            con.setDiscarded(false);
                            cleared.set(true);
                        } catch (IllegalStateException expected) {
                            // The connection was already discarded.
                        } catch (Throwable x) {
                            unexpected.compareAndSet(null, x);
                        }
                    });
                }
                discard.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                for (Future<?> clearer : clearers) {
                    clearer.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                }

                Assertions.assertNull(unexpected.get(),
                        "round " + round + ": neither request may fail unexpectedly: " + unexpected.get());
                if (cleared.get()) {
                    Assertions.assertTrue(con.isDiscarded(),
                            "round " + round + ": a successful clear must have run before the discard,"
                                    + " so the discard must leave the connection marked");
                }
            }
        } finally {
            executor.shutdownNow();
            ds.close(true);
        }
    }
}
