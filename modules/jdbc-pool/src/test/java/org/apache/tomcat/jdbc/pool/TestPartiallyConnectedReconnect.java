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

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.ConnectionEventListener;
import javax.sql.StatementEventListener;
import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A {@link PooledConnection} can hold an XA/CPDS handle without a logical connection after a failed
 * {@code getConnection()} on that handle. A second connect or a reconnect-with must close the handle it replaces,
 * rather than overwrite the field and lose the physical session.
 */
class TestPartiallyConnectedReconnect extends JdbcPoolBaseTest {

    /**
     * An XA handle that records its close and never returns a logical connection.
     */
    public static class TrackingXaConnection implements XAConnection {
        final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("no logical connection");
        }

        @Override
        public XAResource getXAResource() {
            return null;
        }

        @Override
        public void close() {
            closed.set(true);
        }

        @Override
        public void addConnectionEventListener(ConnectionEventListener listener) {
            // nothing to notify
        }

        @Override
        public void removeConnectionEventListener(ConnectionEventListener listener) {
            // nothing to notify
        }

        @Override
        public void addStatementEventListener(StatementEventListener listener) {
            // nothing to notify
        }

        @Override
        public void removeStatementEventListener(StatementEventListener listener) {
            // nothing to notify
        }
    }

    @Test
    void aSecondConnectClosesAHandleLeftWithoutALogicalConnection() throws Exception {
        ConnectionPool pool = newPool();
        try {
            PooledConnection con = new PooledConnection(pool.getPoolProperties(), pool);
            TrackingXaConnection handle = new TrackingXaConnection();
            setField(con, "xaConnection", handle);

            con.connect();

            Assertions.assertTrue(handle.closed.get(),
                    "a connect that replaces a partially connected object must close its old XA handle");
            Assertions.assertNotNull(con.getConnection(), "the connect must establish the new session");
            con.release();
        } finally {
            pool.close(true);
        }
    }

    @Test
    void aReconnectWithClosesAHandleLeftWithoutALogicalConnection() throws Exception {
        ConnectionPool pool = newPool();
        try {
            PooledConnection con = new PooledConnection(pool.getPoolProperties(), pool);
            TrackingXaConnection handle = new TrackingXaConnection();
            setField(con, "xaConnection", handle);

            con.reconnectWith(null, null);

            Assertions.assertTrue(handle.closed.get(),
                    "a reconnect-with that replaces a partially connected object must close its old XA handle");
            Assertions.assertNotNull(con.getConnection(), "the reconnect must establish the new session");
            con.release();
        } finally {
            pool.close(true);
        }
    }

    private ConnectionPool newPool() throws SQLException {
        PoolProperties properties = new PoolProperties();
        properties.setUrl(getUrl());
        properties.setDriverClassName("org.h2.Driver");
        properties.setUsername("sa");
        properties.setPassword("");
        properties.setJmxEnabled(false);
        properties.setInitialSize(0);
        properties.setMinIdle(0);
        properties.setTimeBetweenEvictionRunsMillis(-1);
        return new ConnectionPool(properties);
    }

    private static void setField(PooledConnection con, String name, Object value) throws Exception {
        Field field = PooledConnection.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(con, value);
    }
}
