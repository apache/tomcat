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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Closing a pool is a claim, not a check followed by a write: two callers that observe the open state at the same
 * time must not both run the drain and deliver the interceptor close notifications. The counting interceptor below
 * makes the duplicate delivery visible; the rounds make the claim race meet the window often enough to catch a
 * non-atomic close.
 */
abstract class ConcurrentCloseBase extends JdbcPoolBaseTest {

    private static final int ROUNDS = 50000;
    private static final int CLOSERS = 8;

    /** Counts the close notifications the pool delivers to its definition. */
    public static class CountingCloseInterceptor extends JdbcInterceptor {

        static final AtomicInteger CLOSED = new AtomicInteger();

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // no chain in these tests
        }

        @Override
        public void poolClosed(ConnectionPool pool) {
            CLOSED.incrementAndGet();
        }
    }

    @Test
    void concurrentCloseCallsDeliverOnePoolClosedNotification() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CLOSERS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                CountingCloseInterceptor.CLOSED.set(0);
                PoolProperties properties = new PoolProperties();
                properties.setUrl("jdbc:unused");
                properties.setDriverClassName("org.h2.Driver");
                properties.setJmxEnabled(false);
                properties.setInitialSize(0);
                properties.setMinIdle(0);
                properties.setMaxActive(1);
                properties.setMaxIdle(1);
                properties.setTimeBetweenEvictionRunsMillis(-1);
                properties.setJdbcInterceptors(CountingCloseInterceptor.class.getName());
                ConnectionPool pool = new ConnectionPool(properties);

                CyclicBarrier barrier = new CyclicBarrier(CLOSERS);
                CountDownLatch done = new CountDownLatch(CLOSERS);
                for (int i = 0; i < CLOSERS; i++) {
                    executor.execute(() -> {
                        try {
                            barrier.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                            pool.close(true);
                        } catch (Throwable x) {
                            // A failed close still counts as a completed attempt;
                            // the assertion is about the notifications delivered.
                        } finally {
                            done.countDown();
                        }
                    });
                }
                Assertions.assertTrue(done.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                        "the close callers must finish in round " + round);
                Assertions.assertEquals(1, CountingCloseInterceptor.CLOSED.get(),
                        "concurrent close calls must deliver one poolClosed notification in round " + round);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
