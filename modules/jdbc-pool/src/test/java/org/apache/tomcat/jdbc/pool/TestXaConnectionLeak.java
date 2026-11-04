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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.transaction.xa.XAResource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * When the pool obtains its connections from a
 * {@link javax.sql.XADataSource}, the {@link javax.sql.XAConnection}
 * handle obtained from it owns the physical connection - the pool keeps
 * the handle precisely so that disconnecting can close the physical
 * connection through it. If the logical connection cannot be obtained
 * from a freshly opened handle, the pool must close the handle rather
 * than drop the reference to it, or every failed borrow leaks a
 * physical database session while the pool's counters look healthy.
 */
class TestXaConnectionLeak extends JdbcPoolBaseTest {

    /**
     * A spec-faithful XADataSource over real H2 connections: the handle
     * owns a real session (a genuine H2 XAConnection opened through
     * {@code org.h2.jdbcx.JdbcDataSource#getXAConnection()}), the
     * logical connection obtained from it survives the logical close,
     * and only closing the handle closes the physical connection.
     */
    private static final class TestXaDs implements javax.sql.XADataSource {

        private final org.h2.jdbcx.JdbcDataSource delegate;
        private final List<Handle> handles =
                Collections.synchronizedList(new ArrayList<>());
        /** Once set, the next handle refuses to hand out its connection. */
        private volatile boolean failNextGetConnection;
        /** Once set, the next handle throws a runtime failure instead. */
        private volatile boolean runtimeFailNextGetConnection;
        /** Once set, the next handle hands out a null connection. */
        private volatile boolean nullNextGetConnection;
        /** Once set, the next handle fails (after performing) its close. */
        private volatile boolean failNextClose;
        /**
         * Once set, the next attempt to obtain a handle fails before a
         * session is opened.
         */
        private volatile boolean failNextGetXaConnection;
        /** Set when a caller asked for a handle with credentials. */
        private volatile boolean sawCredentialsOverload;

        TestXaDs(String url) {
            delegate = new org.h2.jdbcx.JdbcDataSource();
            delegate.setUrl(url);
            delegate.setUser("sa");
            delegate.setPassword("");
        }

        @Override
        public javax.sql.XAConnection getXAConnection() throws SQLException {
            if (failNextGetXaConnection) {
                // A provider can refuse the handle itself: no session was
                // opened, so there is nothing for the pool to close.
                failNextGetXaConnection = false;
                throw new SQLException("simulated handle-open failure");
            }
            boolean failGetConnection = failNextGetConnection;
            failNextGetConnection = false;
            boolean runtimeFailGetConnection = runtimeFailNextGetConnection;
            runtimeFailNextGetConnection = false;
            boolean nullGetConnection = nullNextGetConnection;
            nullNextGetConnection = false;
            boolean failClose = failNextClose;
            failNextClose = false;
            // The physical session is opened even on the failing paths:
            // a provider that opens the session and then fails to hand out
            // the logical connection is exactly the stranding scenario.
            Handle handle = new Handle(delegate.getXAConnection(),
                    failGetConnection, runtimeFailGetConnection,
                    nullGetConnection, failClose);
            handles.add(handle);
            return handle;
        }

        @Override
        public javax.sql.XAConnection getXAConnection(String user,
                String password) throws SQLException {
            sawCredentialsOverload = true;
            // Credentials are deliberately ignored: this fake exercises the
            // XA handle lifecycle, not provider authentication, and the
            // alternate-username reconnect test passes credentials that
            // exist only for the pool, not for the database.
            return getXAConnection();
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
            return Logger.getLogger(TestXaDs.class.getName());
        }

        private static final class Handle implements javax.sql.XAConnection {

            private final javax.sql.XAConnection physical;
            private final boolean failGetConnection;
            private final boolean runtimeFailGetConnection;
            private final boolean nullGetConnection;
            private final boolean failClose;
            private final AtomicInteger closeCount = new AtomicInteger();
            private final AtomicBoolean physicalClosed = new AtomicBoolean();
            private final AtomicBoolean logicalClosed = new AtomicBoolean();

            Handle(javax.sql.XAConnection physical, boolean failGetConnection,
                    boolean runtimeFailGetConnection, boolean nullGetConnection,
                    boolean failClose) {
                this.physical = physical;
                this.failGetConnection = failGetConnection;
                this.runtimeFailGetConnection = runtimeFailGetConnection;
                this.nullGetConnection = nullGetConnection;
                this.failClose = failClose;
            }

            @Override
            public Connection getConnection() throws SQLException {
                if (failGetConnection) {
                    throw new SQLException("simulated logical-connection failure");
                }
                if (runtimeFailGetConnection) {
                    throw new IllegalStateException(
                            "simulated runtime logical-connection failure");
                }
                if (nullGetConnection) {
                    return null;
                }
                return (Connection) Proxy.newProxyInstance(
                        TestXaDs.class.getClassLoader(),
                        new Class<?>[] { Connection.class },
                        new LogicalConnection(physical.getConnection(), logicalClosed));
            }

            @Override
            public void close() throws SQLException {
                closeCount.incrementAndGet();
                if (physicalClosed.compareAndSet(false, true)) {
                    // Per the JDBC/XA specification this closes the
                    // physical connection; the logical connection alone
                    // does not.
                    physical.close();
                }
                if (failClose) {
                    // A provider may fail while reporting the close, after
                    // having released the session it owned.
                    throw new SQLException("simulated handle close failure");
                }
            }

            @Override
            public XAResource getXAResource() throws SQLException {
                return physical.getXAResource();
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

    private DataSource newXaDataSource(TestXaDs xads) {
        DataSource ds = newDataSource();
        ds.setDataSource(xads);
        return ds;
    }

    @Test
    void closingTheHandleClosesThePhysicalSession() throws Exception {
        // Pins the premise of the defect: a spec-faithful XA provider keeps
        // the physical session open until the handle itself is closed.
        TestXaDs xads = new TestXaDs(getUrl());
        javax.sql.XAConnection handle = xads.getXAConnection();
        Connection logical = handle.getConnection();
        try (Statement st = logical.createStatement()) {
            st.execute("SELECT 1");
        }
        logical.close();
        Assertions.assertTrue(xads.handles.get(0).logicalClosed.get());
        Assertions.assertFalse(xads.handles.get(0).physicalClosed.get(),
                "closing only the logical connection must not close the physical one");
        awaitSessions(1);

        handle.close();
        Assertions.assertEquals(1, xads.handles.get(0).closeCount.get());
        Assertions.assertTrue(xads.handles.get(0).physicalClosed.get());
        awaitSessions(0);
    }

    @Test
    void releasingAnXaConnectionClosesThePhysicalSession() throws Exception {
        // Do-no-harm pin for the shape of the fix: an XA-backed pool that
        // never hits the failure path must keep closing the physical
        // session through the handle on release, as it does today.
        TestXaDs xads = new TestXaDs(getUrl());
        DataSource ds = newXaDataSource(xads);

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

        Assertions.assertEquals(1, xads.handles.size());
        Assertions.assertEquals(1, xads.handles.get(0).closeCount.get(),
                "releasing the connection must close it exactly once");
        Assertions.assertTrue(xads.handles.get(0).physicalClosed.get(),
                "releasing the connection must close the physical connection "
                        + "through the XA connection handle");
        awaitSessions(0);
    }

    @Test
    void reconnectClosesThePreviousPhysicalConnection() throws Exception {
        // Do-no-harm pin: reconnecting an idle XA-backed connection for a
        // different principal must disconnect the old session through its
        // handle before opening the new one.
        TestXaDs xads = new TestXaDs(getUrl());
        DataSource ds = newXaDataSource(xads);
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
            Assertions.assertEquals(2, xads.handles.size());
            Assertions.assertTrue(xads.handles.get(0).physicalClosed.get(),
                    "the reconnect must close the previous physical "
                            + "connection through its handle");
            Assertions.assertFalse(xads.handles.get(1).physicalClosed.get());
            awaitSessions(1);
        }
    }

    @Test
    void aFailedLogicalConnectionDoesNotStrandTheHandle() throws Exception {
        TestXaDs xads = new TestXaDs(getUrl());
        DataSource ds = newXaDataSource(xads);
        ds.getPoolProperties().setInitialSize(0);

        // The handle opens a real session and then refuses to hand out the
        // logical connection; the session must not be stranded.
        xads.failNextGetConnection = true;
        SQLException e = Assertions.assertThrows(SQLException.class, ds::getConnection,
                "the simulated logical-connection failure must surface");
        Assertions.assertEquals("simulated logical-connection failure", e.getMessage(),
                "the original failure must reach the caller unchanged");
        Assertions.assertEquals(0, e.getSuppressed().length,
                "a successful handle close adds nothing to the failure");

        Assertions.assertEquals(1, xads.handles.size());
        Assertions.assertEquals(1, xads.handles.get(0).closeCount.get(),
                "a handle whose logical connection failed must be closed, "
                        + "not stranded");
        Assertions.assertTrue(xads.handles.get(0).physicalClosed.get());
        awaitSessions(0);
    }

    @Test
    void theHandleRequestedWithoutCredentialsIsGuardedToo() throws Exception {
        // The pool asks for a handle without credentials when it holds no
        // user name and password, which is a second, separate call to the
        // provider; the handle it hands back is guarded in the same way.
        TestXaDs xads = new TestXaDs(getUrl());
        DataSource ds = newDataSource();
        ds.setDataSource(xads);
        ds.getPoolProperties().setUsername(null);
        ds.getPoolProperties().setPassword(null);
        ds.getPoolProperties().setInitialSize(0);

        xads.failNextGetConnection = true;
        Assertions.assertThrows(SQLException.class, ds::getConnection,
                "the simulated logical-connection failure must surface");
        Assertions.assertFalse(xads.sawCredentialsOverload,
                "a pool without a user name and password must ask the provider"
                        + " for its handle through the overload that takes none");

        Assertions.assertEquals(1, xads.handles.size());
        Assertions.assertEquals(1, xads.handles.get(0).closeCount.get(),
                "the guard must not depend on which overload opened the handle");
        Assertions.assertTrue(xads.handles.get(0).physicalClosed.get());
        awaitSessions(0);
    }

    @Test
    void aHandleTheProviderRefusesToOpenLeavesNothingToClose() throws Exception {
        // A provider that refuses the handle itself never opened a session,
        // so there is nothing to close: the pool must report the refusal it
        // was given rather than a failure of its own, and stay usable.
        TestXaDs xads = new TestXaDs(getUrl());
        DataSource ds = newXaDataSource(xads);
        ds.getPoolProperties().setInitialSize(0);

        xads.failNextGetXaConnection = true;
        SQLException e = Assertions.assertThrows(SQLException.class,
                ds::getConnection, "the provider's refusal must surface");
        Assertions.assertEquals("simulated handle-open failure", e.getMessage(),
                "the provider's own refusal must reach the caller, not a"
                        + " failure the pool invented for it");
        Assertions.assertTrue(xads.handles.isEmpty(),
                "no handle was handed out, so there is none to close");
        awaitSessions(0);

        try (Connection c = ds.getConnection()) {
            try (Statement st = c.createStatement()) {
                st.execute("SELECT 1");
            }
            awaitSessions(1);
        }
    }

    @Test
    void aNullLogicalConnectionDoesNotStrandTheHandle() throws Exception {
        TestXaDs xads = new TestXaDs(getUrl());
        DataSource ds = newXaDataSource(xads);
        ds.getPoolProperties().setInitialSize(0);

        // A contract-violating handle that hands out a null connection for
        // a real session must be closed rather than kept.
        xads.nullNextGetConnection = true;
        SQLException e = Assertions.assertThrows(SQLException.class, ds::getConnection,
                "a null logical connection must be reported as a failure");
        Assertions.assertTrue(e.getMessage().contains(TestXaDs.class.getName()),
                "the failure must name the offending provider, was: " + e.getMessage());
        Assertions.assertTrue(e.getMessage().contains("null"),
                "the failure must report what the provider returned, was: "
                        + e.getMessage());

        Assertions.assertEquals(1, xads.handles.size());
        Assertions.assertEquals(1, xads.handles.get(0).closeCount.get(),
                "a handle that handed out a null connection must be closed, "
                        + "not stranded");
        Assertions.assertTrue(xads.handles.get(0).physicalClosed.get());
        awaitSessions(0);
    }

    @Test
    void aRuntimeFailureFromTheLogicalConnectionDoesNotStrandTheHandle() throws Exception {
        TestXaDs xads = new TestXaDs(getUrl());
        DataSource ds = newXaDataSource(xads);
        ds.getPoolProperties().setInitialSize(0);

        // A provider that translates its failure into a runtime exception
        // must not leave the handle (and the session) behind, and the
        // borrow must fail as a reported pool failure, not as a raw NPE
        // from the half-connected pooled connection later on.
        xads.runtimeFailNextGetConnection = true;
        SQLException e = Assertions.assertThrows(SQLException.class, ds::getConnection,
                "the simulated runtime failure must surface as a borrow failure");
        Assertions.assertInstanceOf(IllegalStateException.class, e.getCause(),
                "the reported failure must carry the provider's runtime failure");

        Assertions.assertEquals(1, xads.handles.size());
        Assertions.assertEquals(1, xads.handles.get(0).closeCount.get(),
                "a handle whose logical connection failed at runtime must be "
                        + "closed, not stranded");
        Assertions.assertTrue(xads.handles.get(0).physicalClosed.get());
        awaitSessions(0);
    }

    @Test
    void aFailingHandleCloseIsAttachedToTheOriginalFailure() throws Exception {
        TestXaDs xads = new TestXaDs(getUrl());
        DataSource ds = newXaDataSource(xads);
        ds.getPoolProperties().setInitialSize(0);

        // The cleanup must not hide a failing handle close behind the
        // original failure: the caller sees the logical-connection failure
        // and finds the close failure attached to it as suppressed.
        xads.failNextGetConnection = true;
        xads.failNextClose = true;
        SQLException e = Assertions.assertThrows(SQLException.class, ds::getConnection,
                "the simulated logical-connection failure must surface");
        Assertions.assertEquals("simulated logical-connection failure", e.getMessage(),
                "the close failure must not replace the original failure");
        Assertions.assertEquals(1, e.getSuppressed().length,
                "the close failure must be attached to the original failure");
        Assertions.assertEquals("simulated handle close failure",
                e.getSuppressed()[0].getMessage());

        Assertions.assertEquals(1, xads.handles.size());
        Assertions.assertEquals(1, xads.handles.get(0).closeCount.get(),
                "the cleanup must attempt the close exactly once");
        awaitSessions(0);
    }

    @Test
    void thePoolServesALaterBorrowAfterAFailedOne() throws Exception {
        TestXaDs xads = new TestXaDs(getUrl());
        DataSource ds = newXaDataSource(xads);
        ds.getPoolProperties().setInitialSize(0);

        // The failed borrow must be reported to the caller and leave the
        // pool's counters as healthy as its sessions: nothing counted,
        // nothing held, nothing leaked.
        xads.failNextGetConnection = true;
        Assertions.assertThrows(SQLException.class, ds::getConnection,
                "the simulated logical-connection failure must surface");
        Assertions.assertEquals(0, ds.getNumActive());
        Assertions.assertEquals(0, ds.getNumIdle());
        awaitSessions(0);

        // The very next borrow must succeed and hold exactly its own
        // session - the leaked session of the failed borrow would show up
        // here as one session too many.
        try (Connection c = ds.getConnection()) {
            try (Statement st = c.createStatement()) {
                st.execute("SELECT 1");
            }
            Assertions.assertEquals(1, ds.getNumActive());
            Assertions.assertEquals(0, ds.getNumIdle());
            awaitSessions(1);
        }
        Assertions.assertEquals(0, ds.getNumActive());
        Assertions.assertEquals(1, ds.getNumIdle());
        awaitSessions(1);
    }
}
