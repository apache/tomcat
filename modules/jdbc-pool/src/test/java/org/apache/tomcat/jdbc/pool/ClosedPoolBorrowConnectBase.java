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
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A borrow that observes the pool closure while it is setting up the idle
 * connection it took out hands that connection back and returns empty. The
 * synchronous borrow loop then has no connection and reserves a slot to create
 * one. Without a closed check before the connect, that slot is spent on a full
 * physical connect against a pool that has already shut down - a connect that
 * can block against a hung database, defeating fail-fast shutdown.
 */
abstract class ClosedPoolBorrowConnectBase extends JdbcPoolBaseTest {

    private static final class CountingConnection extends PooledConnection {

        CountingConnection(PoolConfiguration properties, ConnectionPool parent) {
            super(properties, parent);
        }

        @Override
        public void connect() throws SQLException {
            CountingPool.connectCalls.incrementAndGet();
            super.connect();
        }

        @Override
        public boolean validate(int validateAction) {
            if (validateAction == VALIDATE_BORROW) {
                CountingPool.borrowValidating.countDown();
                try {
                    if (!CountingPool.allowValidation.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out waiting to resume the borrow validation");
                    }
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(x);
                }
            }
            return super.validate(validateAction);
        }
    }

    /*
     * The counts and latches are static because the pool constructor calls
     * create(false) before this subclass's instance fields are initialized, and
     * that pre-load is the first connect the test needs to count.
     */
    private static final class CountingPool extends ConnectionPool {

        static final AtomicInteger connectCalls = new AtomicInteger();
        static final CountDownLatch borrowValidating = new CountDownLatch(1);
        static final CountDownLatch allowValidation = new CountDownLatch(1);

        CountingPool(PoolProperties properties) throws SQLException {
            super(properties);
        }

        @Override
        protected PooledConnection create(boolean incrementCounter) {
            return new CountingConnection(getPoolProperties(), this);
        }
    }

    private static PoolProperties properties(String url) {
        PoolProperties props = new PoolProperties();
        props.setUrl(url);
        props.setDriverClassName("org.h2.Driver");
        props.setUsername("sa");
        props.setPassword("");
        props.setJmxEnabled(false);
        props.setInitialSize(1);
        props.setMinIdle(0);
        props.setMaxActive(2);
        props.setTimeBetweenEvictionRunsMillis(-1);
        props.setValidationInterval(0);
        return props;
    }

    @Test
    void aClosedPoolDoesNotOpenAPhysicalConnectionFromTheBorrowLoop() throws Exception {
        CountingPool pool = new CountingPool(properties(getUrl()));
        try {
            int connectsForThePreload = CountingPool.connectCalls.get();
            Assertions.assertEquals(1, connectsForThePreload,
                    "precondition: the pre-load opened exactly one physical session");

            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread borrower = new Thread(() -> {
                try (@SuppressWarnings("unused") Connection pooled = pool.getConnection()) {
                    Assertions.fail("a closed pool must not hand out a connection");
                } catch (SQLException x) {
                    failure.set(x);
                }
            }, "borrower");
            borrower.start();
            Assertions.assertTrue(CountingPool.borrowValidating.await(10, TimeUnit.SECONDS),
                    "the borrower must be setting up the idle connection");
            pool.close(true);
            CountingPool.allowValidation.countDown();

            borrower.join(TimeUnit.SECONDS.toMillis(10));
            Assertions.assertFalse(borrower.isAlive(), "the borrow must have ended");
            Assertions.assertNotNull(failure.get(), "the closed pool must fail the borrow");
            Assertions.assertTrue(failure.get() instanceof SQLException,
                    "the failed borrow must report an SQLException");
            Assertions.assertEquals(connectsForThePreload, CountingPool.connectCalls.get(),
                    "a closed pool must not open a physical session from the borrow loop");
            Assertions.assertEquals(0, pool.getSize(), "the refused borrow must leave no reserved slot behind");
            Assertions.assertEquals(0, pool.getIdle(), "the closed pool must hold no idle connection");
        } finally {
            pool.close(true);
        }
    }
}
