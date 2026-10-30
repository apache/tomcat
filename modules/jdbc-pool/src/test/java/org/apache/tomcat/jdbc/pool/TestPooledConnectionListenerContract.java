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

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;
import javax.sql.StatementEvent;
import javax.sql.StatementEventListener;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Pins the contract the {@link ProxyConnection} class documentation states
 * for the four listener methods of {@link javax.sql.PooledConnection}, which
 * every handle the pool hands out implements. The pool keeps no connection or
 * statement event listeners of its own, and the handle is not the long-lived
 * producer object the specification's listener list belongs to - the pool is
 * itself the connection pool manager those events exist to notify, close
 * recycles a session that stays open, and getConnection() hands out the raw
 * physical session - so on every pool whose handle is not an
 * {@code XAConnection} each of the four methods must fail as the documented
 * refusal: an {@link SQLException} naming the missing XA configuration and the
 * method, wrapped by the proxy in an {@link UndeclaredThrowableException}
 * because the interface declares no checked exception, and leaving the handle
 * fully usable. That includes a pool backed by a
 * {@code ConnectionPoolDataSource}: the pool holds the driver's own pooled
 * connection in that configuration, but refuses these calls rather than route
 * them to it. On an XA pool the same calls must not be refused by the pool:
 * they reach the XA connection the driver created, and whatever that
 * implementation answers - accepting the registration or refusing in its own
 * words - is the driver's decision, never the pool's refusal.
 *
 * <p>The diagnosability of the refusal itself (SQLException rather than a raw
 * NullPointerException, commit 22c1b56836) is pinned in
 * {@code TestXaBranchDispatch}; this class pins the contract the documentation
 * promises, so the documented statement cannot silently drift.</p>
 */
class TestPooledConnectionListenerContract extends JdbcPoolBaseTest {

    private static final ConnectionEventListener NOOP_CONNECTION_LISTENER =
            new ConnectionEventListener() {
        @Override
        public void connectionClosed(ConnectionEvent e) {
            /* the contract under test never notifies a listener */
        }
        @Override
        public void connectionErrorOccurred(ConnectionEvent e) {
            /* the contract under test never notifies a listener */
        }
    };

    private static final StatementEventListener NOOP_STATEMENT_LISTENER =
            new StatementEventListener() {
        @Override
        public void statementClosed(StatementEvent e) {
            /* the contract under test never notifies a listener */
        }
        @Override
        public void statementErrorOccurred(StatementEvent e) {
            /* the contract under test never notifies a listener */
        }
    };

    /* The four listener methods, called the way a portable caller calls them. */
    private static Runnable[] listenerCalls(PooledConnection asPooled) {
        return new Runnable[] {
                () -> asPooled.addConnectionEventListener(NOOP_CONNECTION_LISTENER),
                () -> asPooled.removeConnectionEventListener(NOOP_CONNECTION_LISTENER),
                () -> asPooled.addStatementEventListener(NOOP_STATEMENT_LISTENER),
                () -> asPooled.removeStatementEventListener(NOOP_STATEMENT_LISTENER) };
    }

    private static String[] listenerMethodNames() {
        return new String[] {
                "addConnectionEventListener", "removeConnectionEventListener",
                "addStatementEventListener", "removeStatementEventListener" };
    }

    private XADataSource newXaDataSource() {
        org.h2.jdbcx.JdbcDataSource inner = new org.h2.jdbcx.JdbcDataSource();
        inner.setUrl(getUrl());
        inner.setUser("sa");
        inner.setPassword("");
        XADataSource ds = new XADataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.getPoolProperties().setJmxEnabled(false);
        ds.getPoolProperties().setInitialSize(1);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setDataSource(inner);
        manage(ds);
        return ds;
    }

    /*
     * A ConnectionPoolDataSource-only face of H2's data source. The pool
     * chooses the back end by an instanceof ladder that tries XA first and
     * H2's own data source implements all three faces, so hiding the other
     * two is what makes the pooled-connection branch reachable in tests.
     */
    private static final class PooledConnectionSourceOnly implements ConnectionPoolDataSource {
        private final org.h2.jdbcx.JdbcDataSource inner;

        PooledConnectionSourceOnly(org.h2.jdbcx.JdbcDataSource inner) {
            this.inner = inner;
        }

        @Override
        public PooledConnection getPooledConnection() throws SQLException {
            // H2 hands out its XA connection for either face of itself
            return inner.getXAConnection();
        }

        @Override
        public PooledConnection getPooledConnection(String user, String password) throws SQLException {
            return inner.getXAConnection(user, password);
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            // the wrapper configures nothing
        }

        @Override
        public void setLoginTimeout(int seconds) {
            // the wrapper configures nothing
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return null;
        }
    }

    /* Unwraps whatever a direct interface call surfaced down to its root. */
    private static Throwable rootCause(Throwable thrown) {
        Throwable root = thrown;
        while ((root instanceof UndeclaredThrowableException
                || root instanceof InvocationTargetException)
                && root.getCause() != null) {
            root = root.getCause();
        }
        return root;
    }

    @Test
    void thePlainPoolHandleRefusesAllFourListenerMethodsNamingTheMissingXaConnection()
            throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            // The premise the class javadoc is about: the handle really does
            // advertise the interface whose methods these are.
            Assertions.assertInstanceOf(PooledConnection.class, pooled,
                    "the plain-pool handle must implement javax.sql.PooledConnection");
            Assertions.assertFalse(pooled instanceof javax.sql.XAConnection,
                    "a plain pool must not hand out a handle that is an XA connection");

            PooledConnection asPooled = (PooledConnection) pooled;
            Runnable[] calls = listenerCalls(asPooled);
            String[] names = listenerMethodNames();
            for (int i = 0; i < calls.length; i++) {
                UndeclaredThrowableException thrown = Assertions.assertThrows(
                        UndeclaredThrowableException.class, calls[i]::run,
                        names[i] + " on a plain pool must surface as the proxy's wrap of"
                        + " the checked refusal, the interface declaring no checked exception");
                Throwable root = rootCause(thrown);
                Assertions.assertInstanceOf(SQLException.class, root,
                        names[i] + " must fail as an SQLException, not "
                        + root.getClass().getName());
                Assertions.assertTrue(root.getMessage().contains("not an XA connection"),
                        names[i] + " must name the missing XA configuration: " + root.getMessage());
                Assertions.assertTrue(root.getMessage().contains(
                        "method [" + names[i] + "] cannot be dispatched"),
                        names[i] + " must name the method it cannot dispatch: "
                        + root.getMessage());
            }
        }
    }

    @Test
    void refusedListenerRegistrationsLeaveThePlainPoolHandleFullyUsable()
            throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        Connection pooled = ds.getConnection();
        PooledConnection asPooled = (PooledConnection) pooled;
        for (Runnable call : listenerCalls(asPooled)) {
            Assertions.assertThrows(UndeclaredThrowableException.class, call::run,
                    "the refusal must still be the documented refusal");
        }

        // A refused registration is a refused call, not a damaged handle:
        // the refusal throws from inside invoke() with nothing on the pool
        // side changed, so the borrower's unit of work must continue.
        Assertions.assertFalse(pooled.isClosed());
        try (Statement st = pooled.createStatement()) {
            Assertions.assertTrue(st.execute("SELECT 1"));
        }
        pooled.close();
        Assertions.assertEquals(1, ds.getIdle(),
                "the refusals must not have disturbed the return of the session");
        Assertions.assertEquals(1, poolSessions());
    }

    @Test
    void anXaPoolDispatchesTheListenerMethodsToTheDriverXaConnectionInsteadOfRefusing()
            throws Exception {
        XADataSource ds = newXaDataSource();
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            // The premise for the other half of the documented contract.
            Assertions.assertInstanceOf(javax.sql.XAConnection.class, pooled,
                    "an XA pool must hand out a handle that is an XA connection");

            PooledConnection asPooled = (PooledConnection) pooled;
            // The connection-event pair is accepted by H2's XA connection: a
            // normal return proves the call reached the driver implementation
            // and was not refused by the pool.
            asPooled.addConnectionEventListener(NOOP_CONNECTION_LISTENER);
            asPooled.removeConnectionEventListener(NOOP_CONNECTION_LISTENER);

            // H2's XA connection answers the statement-event pair with an
            // UnsupportedOperationException of its own. What the contract
            // requires is that the answer is the driver's, not the pool's
            // refusal; the driver refusing in its own words is the proof the
            // call was dispatched to the XA connection.
            Runnable[] calls = listenerCalls(asPooled);
            String[] names = listenerMethodNames();
            for (int i = 2; i < calls.length; i++) {
                Throwable thrown = Assertions.assertThrows(Throwable.class, calls[i]::run,
                        names[i] + " must reach the driver's implementation, which answers"
                        + " it, instead of returning against a driver that refuses it");
                Throwable root = rootCause(thrown);
                Assertions.assertNotEquals(SQLException.class, root.getClass(),
                        names[i] + " must not carry the pool's refusal on an XA pool: " + root);
                Assertions.assertInstanceOf(UnsupportedOperationException.class, root,
                        names[i] + " must surface the driver implementation's own answer,"
                        + " was " + root.getClass().getName() + ": " + root.getMessage());
            }
        }
    }

    @Test
    void aConnectionPoolDataSourceBackedPoolHoldsTheDriversPooledConnectionAndStillRefusesTheListenerMethods()
            throws Exception {
        org.h2.jdbcx.JdbcDataSource inner = new org.h2.jdbcx.JdbcDataSource();
        inner.setUrl(getUrl());
        inner.setUser("sa");
        inner.setPassword("");
        DataSource ds = newDataSource();
        // The configured data source outranks the URL: the pool takes the
        // pooled-connection branch of its configuration ladder.
        ds.getPoolProperties().setDataSource(new PooledConnectionSourceOnly(inner));
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            Assertions.assertFalse(pooled instanceof javax.sql.XAConnection,
                    "a pooled-connection data source must not make the handle an XA connection");

            // The premise of the documented outcome: unlike the plain pool,
            // this configuration really does hold the driver's own pooled
            // connection - the class documentation may not claim otherwise.
            ProxyConnection handler = interceptorOf(pooled, ProxyConnection.class);
            org.apache.tomcat.jdbc.pool.PooledConnection internal =
                    handler.getDelegateConnection();
            Field cpdsField = org.apache.tomcat.jdbc.pool.PooledConnection.class
                    .getDeclaredField("cpdsConnection");
            cpdsField.setAccessible(true);
            Assertions.assertInstanceOf(javax.sql.PooledConnection.class, cpdsField.get(internal),
                    "the pool must hold the driver's pooled connection in this configuration");

            // The documented outcome: the refusal stands as on the plain pool;
            // the calls are not routed to the held driver object.
            PooledConnection asPooled = (PooledConnection) pooled;
            UndeclaredThrowableException thrown = Assertions.assertThrows(
                    UndeclaredThrowableException.class,
                    () -> asPooled.addConnectionEventListener(NOOP_CONNECTION_LISTENER),
                    "addConnectionEventListener must fail as it does on the plain pool");
            Throwable root = rootCause(thrown);
            Assertions.assertInstanceOf(SQLException.class, root,
                    "the refusal must remain an SQLException, was " + root.getClass().getName());
        }
    }
}
