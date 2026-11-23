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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * {@link ConnectionPool#init(PoolConfiguration)} used to end with an
 * unconditional {@code closed = false}. A close performed while the pool was
 * still being initialized - from a subclass or a startup hook holding the
 * reference - had already torn the queues, cleaner, MBean and interceptor
 * state down; the write then resurrected the object with its flag saying open
 * over torn-down state. A pool that is closed during initialization must stay
 * closed.
 */
abstract class CloseDuringInitBase extends JdbcPoolBaseTest {

    /**
     * Closes the pool from the interceptor's start callback, which
     * {@code init()} invokes after the queues exist and before its final
     * closed-flag write. The class must stay public with a no-arg constructor,
     * as the pool instantiates it by name.
     */
    public static class ClosingOnStartInterceptor extends JdbcInterceptor {

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // Nothing to reset: this interceptor only acts on the pool start
        }

        @Override
        public void poolStarted(ConnectionPool pool) {
            pool.close(true);
            super.poolStarted(pool);
        }
    }

    @Test
    void aClosePerformedDuringInitializationIsNotOverwritten() throws Exception {
        PoolProperties properties = new PoolProperties();
        properties.setUrl(getUrl());
        properties.setDriverClassName("org.h2.Driver");
        properties.setUsername("sa");
        properties.setPassword("");
        properties.setJmxEnabled(false);
        properties.setInitialSize(0);
        properties.setMinIdle(0);
        properties.setTimeBetweenEvictionRunsMillis(-1);
        properties.setJdbcInterceptors(ClosingOnStartInterceptor.class.getName());

        ConnectionPool pool = new ConnectionPool(properties);
        try {
            Assertions.assertTrue(pool.isClosed(),
                    "a close performed while the pool was being initialized must stay in effect");
            Assertions.assertThrows(SQLException.class, pool::getConnection,
                    "a pool closed during initialization must refuse a borrow");
        } finally {
            pool.close(true);
        }
    }
}
