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
package org.apache.tomcat.jdbc.test;

import java.sql.SQLException;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.apache.tomcat.jdbc.pool.PooledConnection;

public class AbandonedTraceTest {

    @Test
    public void testTraceFormattingIsLazy() throws Exception {
        PoolProperties properties = new PoolProperties();
        properties.setInitialSize(0);
        properties.setLogAbandoned(true);
        properties.setTimeBetweenEvictionRunsMillis(-1);

        TesterConnectionPool pool = new TesterConnectionPool(properties);
        try {
            TesterPooledConnection connection = new TesterPooledConnection(properties, pool);

            Assert.assertSame(connection, pool.borrow(connection));
            Assert.assertFalse(connection.isStringTraceSet());

            String trace = connection.getStackTrace();
            Assert.assertTrue(trace, trace.contains("AbandonedTraceTest.testTraceFormattingIsLazy"));
        } finally {
            pool.closePool();
        }
    }

    private static class TesterConnectionPool extends ConnectionPool {

        TesterConnectionPool(PoolProperties properties) throws SQLException {
            super(properties);
        }

        PooledConnection borrow(PooledConnection connection) throws SQLException {
            return borrowConnection(System.currentTimeMillis(), connection, null, null);
        }

        void closePool() {
            close(true);
        }
    }

    private static class TesterPooledConnection extends PooledConnection {

        private boolean stringTraceSet;

        TesterPooledConnection(PoolProperties properties, ConnectionPool parent) {
            super(properties, parent);
        }

        @Override
        public boolean isDiscarded() {
            return false;
        }

        @Override
        public boolean isInitialized() {
            return true;
        }

        @Override
        public boolean isMaxAgeExpired() {
            return false;
        }

        @Override
        public boolean isReleased() {
            return false;
        }

        @Override
        public void setStackTrace(String trace) {
            stringTraceSet = true;
            super.setStackTrace(trace);
        }

        @Override
        public boolean shouldForceReconnect(String username, String password) {
            return false;
        }

        @Override
        public boolean validate(int validateAction) {
            return true;
        }

        boolean isStringTraceSet() {
            return stringTraceSet;
        }
    }
}
