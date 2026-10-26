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
package org.apache.tomcat.jdbc.pool.interceptor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PooledConnection;

/**
 * The interceptors of a connection must be reset in the order in which they are declared, whether the connection is
 * new or is taken from the pool, and a failure to reset them must not leave the connection borrowed.
 */
class TestInterceptorResetOrder extends JdbcPoolBaseTest {

    private static final List<String> RESETS = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void clearResets() {
        RESETS.clear();
        Unconstructable.fail = false;
    }

    private DataSource pool(Class<?>... interceptors) {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMaxActive(1);
        StringBuilder chain = new StringBuilder();
        for (Class<?> interceptor : interceptors) {
            if (chain.length() > 0) {
                chain.append(';');
            }
            chain.append(interceptor.getName());
        }
        ds.getPoolProperties().setJdbcInterceptors(chain.toString());
        return ds;
    }

    @Test
    void resetOrderOnNewConnection() throws Exception {
        DataSource ds = pool(First.class, Second.class);

        ds.getConnection().close();

        Assertions.assertEquals(Arrays.asList("first", "second"), RESETS);
    }

    @Test
    void resetOrderOnReusedConnection() throws Exception {
        DataSource ds = pool(First.class, Second.class);
        try (Connection first = ds.getConnection()) {
            setSessionMarker(first, 1);
        }
        RESETS.clear();

        try (Connection reused = ds.getConnection()) {
            Assertions.assertEquals(Integer.valueOf(1), sessionMarker(reused),
                    "the cached interceptor chain must be exercised on the same physical session");
            Assertions.assertEquals(Arrays.asList("first", "second"), RESETS);
        }
    }

    @Test
    void resetFailureIsReportedAndTheConnectionDiscarded() throws Exception {
        DataSource ds = pool(First.class, Failing.class, Second.class);

        SQLException e = Assertions.assertThrows(SQLException.class, () -> ds.getConnection());

        Assertions.assertInstanceOf(IllegalStateException.class, e.getCause());
        Assertions.assertTrue(e.getMessage().contains(Failing.class.getName()), e.getMessage());
        // The interceptors after the one that failed are not reset
        Assertions.assertEquals(Arrays.asList("first"), RESETS);
        // The connection that could not be set up must not stay borrowed ...
        Assertions.assertEquals(0, ds.getPool().getActive());
        // ... and must have been closed rather than returned to the pool
        Assertions.assertEquals(0, ds.getPool().getIdle());
        awaitSessions(0);
    }

    @Test
    void instantiationFailureIsReportedAndTheConnectionReturned() throws Exception {
        DataSource ds = pool(Unconstructable.class);
        // With a single connection a leaked borrow would make the second attempt wait for the connection
        ds.getPoolProperties().setMaxWait(500);
        // The pool instantiates the interceptors when it starts so only fail after that
        ds.createPool();
        Unconstructable.fail = true;

        for (int i = 0; i < 2; i++) {
            SQLException e = Assertions.assertThrows(SQLException.class, () -> ds.getConnection());

            Assertions.assertTrue(e.getMessage().contains("instantiate"), e.getMessage());
            // Nothing was done to the connection so it goes back to the pool rather than being closed
            Assertions.assertEquals(0, ds.getPool().getActive());
            Assertions.assertEquals(1, ds.getPool().getIdle());
        }
        awaitSessions(1);
    }

    public static class First extends JdbcInterceptor {

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // the pool also resets interceptors without a parent when a connection is discarded
            if (parent != null) {
                RESETS.add("first");
            }
        }
    }

    public static class Second extends JdbcInterceptor {

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            if (parent != null) {
                RESETS.add("second");
            }
        }
    }

    public static class Unconstructable extends JdbcInterceptor {

        static volatile boolean fail;

        public Unconstructable() {
            if (fail) {
                throw new IllegalStateException();
            }
        }

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // NO-OP
        }
    }

    public static class Failing extends JdbcInterceptor {

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            if (parent != null) {
                throw new IllegalStateException();
            }
        }
    }
}
