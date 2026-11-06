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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A malformed {@code jdbcInterceptors} value that is installed on a live pool
 * must fail the borrow or the close cleanly. It must not leave the borrowed
 * connection counted as busy, and it must not stop the pool from delivering
 * the interceptor close notifications.
 */
class TestMalformedInterceptorConfiguration extends JdbcPoolBaseTest {

    private static final String MALFORMED = "StatementCache(password=secret,bad)";

    public static class CloseCountingInterceptor extends JdbcInterceptor {

        static final AtomicInteger CLOSED = new AtomicInteger();

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // NO-OP
        }

        @Override
        public void poolClosed(ConnectionPool parent) {
            CLOSED.incrementAndGet();
        }
    }

    @Test
    void malformedRuntimeInterceptorListDoesNotLeakTheBorrowedConnection() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(1);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setMaxActive(2);
        ds.getPoolProperties().setMaxIdle(2);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1);
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        // The pre-loaded connection has no interceptor chain yet, so the next
        // borrow builds one and parses the (now malformed) property.
        ds.getPoolProperties().setJdbcInterceptors(MALFORMED);

        Assertions.assertThrows(SQLException.class, ds::getConnection,
                "a malformed interceptor list must fail the borrow");
        Assertions.assertEquals(0, pool.getActive(),
                "the failed setup must not leave the connection in busy");
        Assertions.assertEquals(1, pool.getIdle(),
                "the connection must be returned to idle");
        Assertions.assertEquals(1, pool.getSize(),
                "the failed setup must not leak the pool slot");

        // The same connection is still usable once the configuration is fixed.
        ds.getPoolProperties().setJdbcInterceptors(null);
        try (Connection second = ds.getConnection()) {
            Assertions.assertNotNull(second);
        }
    }

    @Test
    void malformedRuntimeInterceptorListDoesNotAbortCloseNotifications() throws Exception {
        CloseCountingInterceptor.CLOSED.set(0);
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1);
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        // Install an interceptor after the pool started and use it once, so
        // the definition is recorded as part of a connection chain.
        ds.getPoolProperties().setJdbcInterceptors(CloseCountingInterceptor.class.getName());
        try (Connection connection = ds.getConnection()) {
            Assertions.assertNotNull(connection);
        }

        // Break the property before the close. The close still has to deliver
        // the notification to the definitions the chain was built from.
        ds.getPoolProperties().setJdbcInterceptors(MALFORMED);

        Assertions.assertDoesNotThrow(() -> pool.close(true),
                "a malformed interceptor list must not abort the pool close");
        Assertions.assertEquals(1, CloseCountingInterceptor.CLOSED.get(),
                "the close must still notify the interceptor chain's definitions");
    }
}
