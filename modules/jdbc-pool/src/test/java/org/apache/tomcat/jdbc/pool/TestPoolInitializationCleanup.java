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

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A pool whose initialization fails after it has acquired resources - a
 * started interceptor or the pre-loaded physical sessions - must tear those
 * resources down before the constructor fails.
 */
class TestPoolInitializationCleanup extends JdbcPoolBaseTest {

    public static class StartCountingInterceptor extends JdbcInterceptor {

        static final AtomicInteger STARTED = new AtomicInteger();
        static final AtomicInteger CLOSED = new AtomicInteger();

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // NO-OP
        }

        @Override
        public void poolStarted(ConnectionPool parent) {
            STARTED.incrementAndGet();
        }

        @Override
        public void poolClosed(ConnectionPool parent) {
            CLOSED.incrementAndGet();
        }
    }

    public static class ErrorOnStartInterceptor extends JdbcInterceptor {

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // NO-OP
        }

        @Override
        public void poolStarted(ConnectionPool parent) {
            throw new LinkageError("boom from poolStarted()");
        }
    }

    @Test
    void nonFatalErrorFromPoolStartedClosesTheStartedInterceptors() throws Exception {
        StartCountingInterceptor.STARTED.set(0);
        StartCountingInterceptor.CLOSED.set(0);
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setJdbcInterceptors(
                StartCountingInterceptor.class.getName() + ";" + ErrorOnStartInterceptor.class.getName());

        Assertions.assertThrows(SQLException.class, ds::createPool,
                "a non-fatal Error from an interceptor start must fail pool creation");
        Assertions.assertEquals(1, StartCountingInterceptor.STARTED.get(),
                "the interceptor before the failing one must have started");
        Assertions.assertEquals(1, StartCountingInterceptor.CLOSED.get(),
                "the failed start must close the interceptors it already started");
        awaitSessions(0);
    }

    /**
     * A pool whose cleaner cannot be armed after the initial pre-load has
     * already established physical sessions.
     */
    public static class CleanerFailingPool extends ConnectionPool {

        public CleanerFailingPool(PoolConfiguration properties) throws SQLException {
            super(properties);
        }

        @Override
        public synchronized void initializePoolCleaner(PoolConfiguration properties) {
            throw new IllegalStateException("boom from initializePoolCleaner()");
        }
    }

    @Test
    void failureToArmTheCleanerAfterThePreLoadClosesTheConnections() throws Exception {
        PoolProperties properties = new PoolProperties();
        properties.setUrl(getUrl());
        properties.setDriverClassName("org.h2.Driver");
        properties.setUsername("sa");
        properties.setPassword("");
        properties.setJmxEnabled(false);
        properties.setInitialSize(1);
        properties.setMinIdle(0);
        properties.setMaxActive(1);
        properties.setTimeBetweenEvictionRunsMillis(-1);

        SQLException thrown = Assertions.assertThrows(SQLException.class,
                () -> new CleanerFailingPool(properties),
                "a cleaner failure after the pre-load must fail pool creation");
        Assertions.assertInstanceOf(IllegalStateException.class, thrown.getCause(),
                "the cleaner failure must remain the cause");
        awaitSessions(0);
    }
}
