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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The JDBC liveness probe, {@code isValid(int)}, answers a closed
 * connection with {@code false}: it is the probe <em>for</em> liveness and
 * must not throw for a connection that is merely closed. The pool has two
 * closed states for a handle. The application returned it, which seals the
 * disposable facade; or the pool discarded the session while the handle was
 * still borrowed - through the abandoned check, through a forced pool
 * close, or through an interceptor's {@code setDiscarded(true)} - which
 * does not seal it. Both states must answer the probe with {@code false},
 * and only the sealed one was covered. A handle that is still live must
 * reach the driver instead: the driver's own answer - including the
 * {@code SQLException} the specification prescribes for a negative timeout,
 * and including a driver-side failure of the check itself - is the
 * caller's to receive, not one the pool may replace with its own.
 */
class TestIsValidOnDiscardedHandle extends JdbcPoolBaseTest {

    @Test
    void anAbandonedBorrowAnswersTheValidityProbeWithFalse() throws Exception {
        DataSource ds = newDataSource();
        PoolConfiguration properties = ds.getPoolProperties();
        properties.setRemoveAbandoned(true);
        properties.setRemoveAbandonedTimeout(1);
        properties.setInitialSize(0);
        ds.createPool();

        Connection abandoned = ds.getConnection();
        PooledConnection pooled =
                interceptorOf(abandoned, ProxyConnection.class).getDelegateConnection();
        // Backdate the borrow past the abandon timeout and run the check the
        // pool's cleaner thread runs.
        pooled.setTimestamp(0L);
        ds.getPool().checkAbandoned();

        Assertions.assertTrue(pooled.isDiscarded(),
                "precondition: the pool must have discarded the borrowed session");
        awaitSessions(0); // and taken the physical session out of service

        Assertions.assertFalse(abandoned.isValid(1),
                "a session the pool discarded while it was still borrowed must answer"
                        + " the validity probe with false instead of throwing");
        Assertions.assertFalse(abandoned.isValid(0),
                "the answer is the same whatever the timeout argument");
        Assertions.assertFalse(abandoned.isValid(-1),
                "and it does not look at a negative timeout either, exactly like the"
                        + " sealed facade's answer");
        Assertions.assertTrue(abandoned.isClosed(),
                "the handle keeps reporting itself closed");
        abandoned.close(); // the late return stays the documented no-op
    }

    @Test
    void anInterceptorDiscardedSessionAnswersTheValidityProbeWithFalse() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                DiscardMarkingInterceptor.class.getName());
        ds.createPool();

        Connection held = ds.getConnection();
        // The configured interceptor marks the session to be closed when it
        // is returned, while the borrower still holds the handle; it does so
        // as the first probe passes through it, having first delegated that
        // probe to the driver, which is what the live session answers.
        Assertions.assertTrue(held.isValid(1),
                "precondition: the first probe finds the session live, at the driver");
        Assertions.assertTrue(held.isClosed(),
                "precondition: the interceptor's marking must have reached the handle");

        Assertions.assertFalse(held.isValid(1),
                "a session marked for discard must answer the validity probe with false"
                        + " to the borrower that still holds it");
        Assertions.assertEquals(1, poolSessions(),
                "precondition: the physical session is still open until it is returned");

        held.close();
        awaitSessions(0); // and out of service once it has been returned
    }

    @Test
    void aHandleTornAwayByAForcedPoolCloseAnswersTheValidityProbeWithFalse() throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        Connection held = ds.getConnection();
        // A forced shutdown tears busy sessions away from their borrowers.
        ds.close(true);
        awaitSessions(0);

        Assertions.assertFalse(held.isValid(1),
                "a handle whose session the shutdown discarded must answer the validity"
                        + " probe with false instead of throwing");
        Assertions.assertTrue(held.isClosed());
        held.close(); // and its close stays the documented no-op
    }

    @Test
    void aSealedHandleKeepsAnsweringTheValidityProbeWithFalse() throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        Connection returned = ds.getConnection();
        returned.close();

        Assertions.assertFalse(returned.isValid(1),
                "the sealed facade's false answer must be untouched by the closed"
                        + " guard added in the chain below it");
        Assertions.assertFalse(returned.isValid(-1),
                "the sealed answer does not look at the timeout argument");
        Assertions.assertTrue(returned.isClosed());
    }

    @Test
    void aLiveHandleAnswersTheValidityProbeThroughItsDriver() throws Exception {
        StrictValidityDataSource strict = new StrictValidityDataSource(getUrl());
        DataSource ds = newDataSource();
        ds.getPoolProperties().setDataSource(strict);
        ds.createPool();

        Connection pooled = ds.getConnection();
        try {
            Assertions.assertTrue(pooled.isValid(1),
                    "the driver's positive answer must reach the caller");

            // The closed guard added for discarded handles is a guard, not a
            // timeout validator: on a live handle the probe travels to the
            // driver, and a driver that honors the contract's throw for a
            // negative timeout must keep throwing it through the pool.
            Assertions.assertThrows(SQLException.class, () -> pooled.isValid(-1),
                    "the driver's refusal of a negative timeout must not be swallowed");

            strict.refuseValidity();
            Assertions.assertThrows(SQLException.class, () -> pooled.isValid(1),
                    "a driver that cannot vouch for its session must say so; the pool"
                            + " must not replace the failure with a quiet false");
            strict.acceptValidity();
            Assertions.assertTrue(pooled.isValid(1),
                    "and its recovery reaches the caller as well");

            // Once the handle is closed, the pool answers the probe itself.
            strict.refuseValidity();
            interceptorOf(pooled, ProxyConnection.class).getDelegateConnection().setDiscarded(true);
            int askedBefore = strict.validityProbes();
            Assertions.assertFalse(pooled.isValid(-1),
                    "the closed answer is the pool's own");
            Assertions.assertEquals(askedBefore, strict.validityProbes(),
                    "and a closed handle answers the probe without consulting the driver");
            strict.acceptValidity();
        } finally {
            pooled.close();
        }
    }

    /*
     * A backing data source whose sessions honor the JDBC contract more
     * strictly than H2 does - H2 answers isValid(-1) with true rather than
     * throwing what the specification prescribes. These sessions throw the
     * prescribed SQLException for a negative timeout, can be made to fail
     * the probe outright, and count every probe so a test can see whether
     * the driver was asked at all.
     */
    private static final class StrictValidityDataSource implements javax.sql.DataSource {

        private final String url;
        private final AtomicInteger probes = new AtomicInteger();
        private volatile boolean refuseValidity;

        StrictValidityDataSource(String url) {
            this.url = url;
        }

        void refuseValidity() {
            refuseValidity = true;
        }

        void acceptValidity() {
            refuseValidity = false;
        }

        int validityProbes() {
            return probes.get();
        }

        @Override
        public Connection getConnection() throws SQLException {
            return strictSession(DriverManager.getConnection(url));
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return strictSession(DriverManager.getConnection(url, username, password));
        }

        private Connection strictSession(Connection real) {
            return (Connection) Proxy.newProxyInstance(
                    StrictValidityDataSource.class.getClassLoader(),
                    new Class<?>[] { Connection.class },
                    (proxy, method, args) -> {
                        if ("isValid".equals(method.getName())) {
                            probes.incrementAndGet();
                            if (((Number) args[0]).intValue() < 0) {
                                throw new SQLException("this driver refuses a negative timeout");
                            }
                            if (refuseValidity) {
                                throw new SQLException("this driver cannot vouch for the session");
                            }
                        }
                        try {
                            return method.invoke(real, args);
                        } catch (InvocationTargetException x) {
                            throw x.getTargetException() != null ? x.getTargetException() : x;
                        }
                    });
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            // NOOP
        }

        @Override
        public void setLoginTimeout(int seconds) {
            // NOOP
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return false;
        }
    }
}
