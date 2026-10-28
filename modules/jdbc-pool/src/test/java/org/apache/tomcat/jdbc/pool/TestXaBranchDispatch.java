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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.PooledConnection;
import javax.sql.StatementEvent;
import javax.sql.StatementEventListener;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The proxy the pool hands out implements {@link javax.sql.PooledConnection}
 * (and, on XA pools, {@link javax.sql.XAConnection}), so methods declared by
 * those interfaces must never be dispatched at a {@code null} target. The
 * bottom-of-chain {@link ProxyConnection} answers XA-family calls against the
 * wrapped {@code javax.sql.XAConnection}, which only exists when the pool is
 * backed by an XA data source; on a plain pool the call has to fail as a
 * diagnosable failure that names the unsupported method, not as a raw
 * {@link NullPointerException} from reflective dispatch at null.
 */
class TestXaBranchDispatch extends JdbcPoolBaseTest {

    /** The listener methods the plain-pool handle cannot honour. */
    private static final String[] LISTENER_METHODS = {
            "addConnectionEventListener", "removeConnectionEventListener",
            "addStatementEventListener", "removeStatementEventListener" };

    private static final ConnectionEventListener NOOP_CONNECTION_LISTENER =
            new ConnectionEventListener() {
        @Override
        public void connectionClosed(ConnectionEvent e) {
            /* the listener is never notified by the pool */
        }
        @Override
        public void connectionErrorOccurred(ConnectionEvent e) {
            /* the listener is never notified by the pool */
        }
    };

    private static final StatementEventListener NOOP_STATEMENT_LISTENER =
            new StatementEventListener() {
        @Override
        public void statementClosed(StatementEvent e) {
            /* the listener is never notified by the pool */
        }
        @Override
        public void statementErrorOccurred(StatementEvent e) {
            /* the listener is never notified by the pool */
        }
    };

    private static Object[] listenerArgsFor(Method m) {
        return new Object[] {
                StatementEventListener.class.equals(m.getParameterTypes()[0])
                        ? NOOP_STATEMENT_LISTENER : NOOP_CONNECTION_LISTENER };
    }

    @Test
    void listenerMethodsOnAPoolHandleFailingAtTheXaBranchThrowSqlExceptionNotNpe()
            throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            ProxyConnection handler = interceptorOf(pooled, ProxyConnection.class);
            Assertions.assertNotNull(handler, "the chain must end in a ProxyConnection");

            // Call the handler directly so the proxy machinery cannot mask
            // the exception type the interceptor itself raises.
            for (String method : LISTENER_METHODS) {
                Method m = PooledConnection.class.getMethod(method,
                        method.contains("Statement")
                                ? StatementEventListener.class
                                : ConnectionEventListener.class);
                Object[] args = listenerArgsFor(m);
                Throwable thrown = Assertions.assertThrows(Throwable.class,
                        () -> handler.invoke(pooled, m, args));
                Assertions.assertInstanceOf(SQLException.class, thrown,
                        method + " on a non-XA pool must fail as SQLException, "
                        + "not as the raw " + thrown.getClass().getName());
                Assertions.assertTrue(thrown.getMessage().contains(method),
                        "the failure must name the method that cannot be dispatched: "
                        + thrown.getMessage());
            }
        }
    }

    @Test
    void listenerRegistrationThroughTheProxyNeverEscapesAsANullPointerException()
            throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            // The handle itself implements javax.sql.PooledConnection.
            PooledConnection asPooled = (PooledConnection) pooled;
            // javax.sql.PooledConnection's listener methods declare no
            // checked exception, so a checked failure from the interceptor
            // surfaces wrapped - what must never surface is the bare NPE
            // from dispatching the call at a null XA connection.
            for (Runnable call : new Runnable[] {
                    () -> asPooled.addConnectionEventListener(NOOP_CONNECTION_LISTENER),
                    () -> asPooled.removeConnectionEventListener(NOOP_CONNECTION_LISTENER),
                    () -> asPooled.addStatementEventListener(NOOP_STATEMENT_LISTENER),
                    () -> asPooled.removeStatementEventListener(NOOP_STATEMENT_LISTENER)}) {
                Throwable thrown = Assertions.assertThrows(Throwable.class, call::run);
                Assertions.assertNotEquals(NullPointerException.class, thrown.getClass(),
                        "a raw NPE must not reach the application");
                Throwable root = thrown;
                while (root instanceof UndeclaredThrowableException
                        || root instanceof InvocationTargetException) {
                    Assertions.assertNotNull(root.getCause(),
                            "the wrapped failure must carry a diagnosable cause");
                    root = root.getCause();
                }
                Assertions.assertInstanceOf(SQLException.class, root);
            }
        }
    }

    @Test
    void aDiscardedHandleReportsTheConventionalClosedFailureNotAnXaComplaint()
            throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        Connection pooled = ds.getConnection();
        ProxyConnection handler = interceptorOf(pooled, ProxyConnection.class);
        org.apache.tomcat.jdbc.pool.PooledConnection internal =
                handler.getDelegateConnection();
        // A borrow whose connection is marked for discard on return is
        // reported as closed by every method of the handle; the XA branch
        // must not be the exception that blames the XA configuration for a
        // call that is really against a handle going away.
        internal.setDiscarded(true);

        Method m = PooledConnection.class.getMethod(
                "addConnectionEventListener", ConnectionEventListener.class);
        Throwable thrown = Assertions.assertThrows(Throwable.class,
                () -> handler.invoke(pooled, m, new Object[] {NOOP_CONNECTION_LISTENER}));
        Assertions.assertInstanceOf(SQLException.class, thrown);
        Assertions.assertEquals("Connection has already been closed.",
                thrown.getMessage());
        pooled.close();
    }

    @Test
    void anXaPoolStillDispatchesListenerMethodsToTheXaConnection()
            throws Exception {
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
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            Assertions.assertInstanceOf(javax.sql.XAConnection.class, pooled,
                    "an XA pool must hand out a handle that is an XA connection");
            PooledConnection asPooled = (PooledConnection) pooled;
            // On an XA pool the same branch dispatches the listener methods
            // to the real XA connection implementation; this must keep
            // working after the null-target guard is added.
            for (String method : new String[] {"addConnectionEventListener",
                    "removeConnectionEventListener"}) {
                Method m = PooledConnection.class.getMethod(method,
                        ConnectionEventListener.class);
                m.invoke(asPooled, NOOP_CONNECTION_LISTENER);
            }
            // H2's XA connection rejects statement listeners with an
            // UnsupportedOperationException of its own. That the driver
            // implementation's own refusal surfaces is the proof the call
            // was dispatched to the XA connection, not failed at the pool's
            // null-target guard.
            for (String method : new String[] {"addStatementEventListener",
                    "removeStatementEventListener"}) {
                Method m = PooledConnection.class.getMethod(method,
                        StatementEventListener.class);
                Throwable thrown = Assertions.assertThrows(Throwable.class,
                        () -> m.invoke(asPooled, NOOP_STATEMENT_LISTENER));
                Throwable root = thrown instanceof InvocationTargetException
                        && thrown.getCause() != null ? thrown.getCause() : thrown;
                Assertions.assertInstanceOf(UnsupportedOperationException.class, root,
                        "the driver XA connection's own refusal must surface, "
                        + "proving the call was dispatched there");
            }
        }
    }
}
