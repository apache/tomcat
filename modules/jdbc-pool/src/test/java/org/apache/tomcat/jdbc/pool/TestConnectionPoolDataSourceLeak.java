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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * When the pool obtains its connections from a
 * {@link javax.sql.ConnectionPoolDataSource}, the JDBC specification keeps
 * the physical connection open until the {@link javax.sql.PooledConnection}
 * handle obtained from it is closed - closing the logical connection only
 * returns that handle to the provider. The pool must therefore keep the
 * handle and close it, or every disconnect leaks a database session.
 */
class TestConnectionPoolDataSourceLeak extends JdbcPoolBaseTest {

    /**
     * A spec-faithful ConnectionPoolDataSource over real H2 connections:
     * the logical connection obtained from a handle survives the logical
     * close, and only closing the handle closes the physical connection.
     */
    private static final class TestCpds implements javax.sql.ConnectionPoolDataSource {

        private final String url;
        private final List<Handle> handles =
                Collections.synchronizedList(new ArrayList<>());
        /** Once set, the next handle refuses to hand out its connection. */
        private volatile boolean failNextGetConnection;
        /** Once set, the next handle hands out a null connection. */
        private volatile boolean nullNextGetConnection;

        TestCpds(String url) {
            this.url = url;
        }

        @Override
        public javax.sql.PooledConnection getPooledConnection() throws SQLException {
            return getPooledConnection("sa", "");
        }

        @Override
        public javax.sql.PooledConnection getPooledConnection(String user,
                String password) throws SQLException {
            // Credentials are deliberately ignored: this fake exercises the
            // CPDS handle lifecycle, not provider authentication, and the
            // alternate-username reconnect test passes credentials that
            // exist only for the pool, not for the database.
            boolean failGetConnection = failNextGetConnection;
            failNextGetConnection = false;
            boolean nullGetConnection = nullNextGetConnection;
            nullNextGetConnection = false;
            // The physical connection is opened even on the failing paths:
            // a provider that opens the session and then fails to hand out
            // the logical connection is exactly the stranding scenario.
            Handle handle = new Handle(DriverManager.getConnection(url, "sa", ""),
                    failGetConnection, nullGetConnection);
            handles.add(handle);
            return handle;
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
            // no-op
        }

        @Override
        public void setLoginTimeout(int seconds) {
            // no-op
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getLogger(TestCpds.class.getName());
        }

        private static final class Handle implements javax.sql.PooledConnection {

            private final Connection physical;
            private final boolean failGetConnection;
            private final boolean nullGetConnection;
            private final AtomicBoolean physicalClosed = new AtomicBoolean();
            private final AtomicBoolean logicalClosed = new AtomicBoolean();

            Handle(Connection physical, boolean failGetConnection,
                    boolean nullGetConnection) {
                this.physical = physical;
                this.failGetConnection = failGetConnection;
                this.nullGetConnection = nullGetConnection;
            }

            @Override
            public Connection getConnection() throws SQLException {
                if (failGetConnection) {
                    throw new SQLException("simulated logical-connection failure");
                }
                if (nullGetConnection) {
                    return null;
                }
                return (Connection) Proxy.newProxyInstance(
                        TestCpds.class.getClassLoader(),
                        new Class<?>[] { Connection.class },
                        new LogicalConnection(physical, logicalClosed));
            }

            @Override
            public void close() throws SQLException {
                // Per the JDBC specification this closes the physical
                // connection; the logical connection alone does not.
                if (physicalClosed.compareAndSet(false, true)) {
                    physical.close();
                }
            }

            @Override
            public void addConnectionEventListener(javax.sql.ConnectionEventListener listener) {
                // no-op
            }

            @Override
            public void removeConnectionEventListener(javax.sql.ConnectionEventListener listener) {
                // no-op
            }

            @Override
            public void addStatementEventListener(javax.sql.StatementEventListener listener) {
                // no-op
            }

            @Override
            public void removeStatementEventListener(javax.sql.StatementEventListener listener) {
                // no-op
            }
        }

        private static final class LogicalConnection implements InvocationHandler {

            private final Connection physical;
            private final AtomicBoolean logicalClosed;

            LogicalConnection(Connection physical, AtomicBoolean logicalClosed) {
                this.physical = physical;
                this.logicalClosed = logicalClosed;
            }

            @Override
            public Object invoke(Object proxy, Method method, Object[] args)
                    throws Throwable {
                if ("close".equals(method.getName())) {
                    // Returning the logical connection to the provider must
                    // not close the physical connection.
                    logicalClosed.set(true);
                    return null;
                }
                if ("isClosed".equals(method.getName())) {
                    return Boolean.valueOf(logicalClosed.get());
                }
                if (logicalClosed.get()) {
                    throw new SQLException("Logical connection already closed");
                }
                try {
                    return method.invoke(physical, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        }
    }

    private DataSource newCpdsDataSource(TestCpds cpds) {
        DataSource ds = newDataSource();
        ds.setDataSource(cpds);
        return ds;
    }

    @Test
    void closingTheLogicalConnectionAloneDoesNotCloseThePhysicalOne() throws Exception {
        // Pins the premise of the defect: a spec-faithful CPDS keeps the
        // physical session open until the handle itself is closed.
        TestCpds cpds = new TestCpds(getUrl());
        javax.sql.PooledConnection handle = cpds.getPooledConnection("sa", "");
        Connection logical = handle.getConnection();
        try (Statement st = logical.createStatement()) {
            st.execute("SELECT 1");
        }
        logical.close();
        Assertions.assertTrue(cpds.handles.get(0).logicalClosed.get());
        Assertions.assertFalse(cpds.handles.get(0).physicalClosed.get(),
                "closing only the logical connection must not close the physical one");
        awaitSessions(1);

        handle.close();
        Assertions.assertTrue(cpds.handles.get(0).physicalClosed.get());
        awaitSessions(0);
    }

    @Test
    void releasingAConnectionClosesThePhysicalConnection() throws Exception {
        TestCpds cpds = new TestCpds(getUrl());
        DataSource ds = newCpdsDataSource(cpds);

        try (Connection c = ds.getConnection()) {
            try (Statement st = c.createStatement()) {
                st.execute("SELECT 1");
            }
        }
        awaitSessions(1);

        // Returning the connection to the pool and then closing the pool
        // releases the idle connection, which must close the physical
        // session through the handle, not just the logical connection.
        ds.close();

        Assertions.assertEquals(1, cpds.handles.size());
        Assertions.assertTrue(cpds.handles.get(0).physicalClosed.get(),
                "releasing the connection must close the physical connection "
                        + "through the PooledConnection handle");
        awaitSessions(0);
    }

    @Test
    void reconnectClosesThePreviousPhysicalConnection() throws Exception {
        TestCpds cpds = new TestCpds(getUrl());
        DataSource ds = newCpdsDataSource(cpds);
        // A different credential on the next borrow forces a reconnect of
        // the idle connection through disconnect(false).
        ds.getPoolProperties().setAlternateUsernameAllowed(true);

        try (Connection c = ds.getConnection()) {
            setSessionMarker(c, 1);
        }
        awaitSessions(1);

        try (Connection c = ds.getConnection("otheruser", "otherpass")) {
            // The reconnect took a new physical session...
            Assertions.assertNull(sessionMarker(c),
                    "the reconnect must not reuse the old session");
            Assertions.assertEquals(2, cpds.handles.size());
            Assertions.assertTrue(cpds.handles.get(0).physicalClosed.get(),
                    "the reconnect must close the previous physical "
                            + "connection through its handle");
            Assertions.assertFalse(cpds.handles.get(1).physicalClosed.get());
            awaitSessions(1);
        }
    }

    @Test
    void aFailedLogicalConnectionDoesNotStrandTheHandle() throws Exception {
        TestCpds cpds = new TestCpds(getUrl());
        DataSource ds = newCpdsDataSource(cpds);
        ds.getPoolProperties().setInitialSize(0);

        // The handle opens a real session and then refuses to hand out the
        // logical connection; the session must not be stranded.
        cpds.failNextGetConnection = true;
        Assertions.assertThrows(SQLException.class, ds::getConnection,
                "the simulated logical-connection failure must surface");

        Assertions.assertEquals(1, cpds.handles.size());
        Assertions.assertTrue(cpds.handles.get(0).physicalClosed.get(),
                "a handle whose logical connection failed must be closed, "
                        + "not stranded");
        awaitSessions(0);
    }

    @Test
    void aNullLogicalConnectionDoesNotStrandTheHandle() throws Exception {
        TestCpds cpds = new TestCpds(getUrl());
        DataSource ds = newCpdsDataSource(cpds);
        ds.getPoolProperties().setInitialSize(0);

        // A contract-violating handle that hands out a null connection for
        // a real session must be closed rather than kept.
        cpds.nullNextGetConnection = true;
        Assertions.assertThrows(SQLException.class, ds::getConnection,
                "a null logical connection must be reported as a failure");

        Assertions.assertEquals(1, cpds.handles.size());
        Assertions.assertTrue(cpds.handles.get(0).physicalClosed.get(),
                "a handle that handed out a null connection must be closed, "
                        + "not stranded");
        awaitSessions(0);
    }
}
