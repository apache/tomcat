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
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.ConnectionEventListener;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.StatementEventListener;
import javax.sql.XAConnection;
import javax.sql.XADataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A physical handle must stay reachable when obtaining its logical connection
 * fails and closing the handle also fails. The caller's release path then gets
 * a chance to retry the physical close instead of leaking the session.
 */
class TestPooledConnectionHandleLeak extends JdbcPoolBaseTest {

    private static abstract class FailingHandle {

        int closeAttempts;
        boolean closed;

        void close() throws SQLException {
            closeAttempts++;
            if (closeAttempts == 1) {
                throw new SQLException("close boom");
            }
            closed = true;
        }

        Connection getConnection() throws SQLException {
            throw new SQLException("logical connection boom");
        }
    }

    public static class FailingXaHandle extends FailingHandle implements XAConnection {

        @Override
        public Connection getConnection() throws SQLException {
            return super.getConnection();
        }

        @Override
        public javax.transaction.xa.XAResource getXAResource() {
            return null;
        }

        @Override
        public void close() throws SQLException {
            super.close();
        }

        @Override
        public void addConnectionEventListener(ConnectionEventListener listener) {
            // NO-OP
        }

        @Override
        public void removeConnectionEventListener(ConnectionEventListener listener) {
            // NO-OP
        }

        @Override
        public void addStatementEventListener(StatementEventListener listener) {
            // NO-OP
        }

        @Override
        public void removeStatementEventListener(StatementEventListener listener) {
            // NO-OP
        }
    }

    public static class FailingXaDataSource implements XADataSource {

        private final XAConnection connection;

        public FailingXaDataSource(XAConnection connection) {
            this.connection = connection;
        }

        @Override
        public XAConnection getXAConnection() {
            return connection;
        }

        @Override
        public XAConnection getXAConnection(String user, String password) {
            return connection;
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            // NO-OP
        }

        @Override
        public void setLoginTimeout(int seconds) {
            // NO-OP
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }

    public static class FailingCpdsHandle extends FailingHandle implements javax.sql.PooledConnection {

        @Override
        public Connection getConnection() throws SQLException {
            return super.getConnection();
        }

        @Override
        public void close() throws SQLException {
            super.close();
        }

        @Override
        public void addConnectionEventListener(ConnectionEventListener listener) {
            // NO-OP
        }

        @Override
        public void removeConnectionEventListener(ConnectionEventListener listener) {
            // NO-OP
        }

        @Override
        public void addStatementEventListener(StatementEventListener listener) {
            // NO-OP
        }

        @Override
        public void removeStatementEventListener(StatementEventListener listener) {
            // NO-OP
        }
    }

    public static class FailingConnectionPoolDataSource implements ConnectionPoolDataSource {

        private final javax.sql.PooledConnection connection;

        public FailingConnectionPoolDataSource(javax.sql.PooledConnection connection) {
            this.connection = connection;
        }

        @Override
        public javax.sql.PooledConnection getPooledConnection() {
            return connection;
        }

        @Override
        public javax.sql.PooledConnection getPooledConnection(String user, String password) {
            return connection;
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            // NO-OP
        }

        @Override
        public void setLoginTimeout(int seconds) {
            // NO-OP
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }

    private PooledConnection newPooledConnection(PoolProperties properties) {
        DataSource ds = newDataSource();
        properties.setJmxEnabled(false);
        return new PooledConnection(properties, ds.getPool());
    }

    @Test
    void aFailedXaHandleCloseIsRetriedByTheRelease() throws Exception {
        FailingXaHandle handle = new FailingXaHandle();
        PoolProperties properties = new PoolProperties();
        properties.setDataSource(new FailingXaDataSource(handle));
        PooledConnection pc = newPooledConnection(properties);

        Assertions.assertThrows(SQLException.class, pc::connect,
                "the logical connection failure must be reported");
        Assertions.assertEquals(1, handle.closeAttempts,
                "the handle must have been closed once before the failure was reported");
        Assertions.assertSame(handle, pc.getXAConnection(),
                "a handle whose close failed must remain reachable for a retry");

        pc.release();
        Assertions.assertEquals(2, handle.closeAttempts,
                "the release must retry the physical close");
        Assertions.assertTrue(handle.closed, "the retried close must have succeeded");
        Assertions.assertNull(pc.getXAConnection(), "the released handle must be cleared");
    }

    @Test
    void aFailedCpdsHandleCloseIsRetriedByTheRelease() throws Exception {
        FailingCpdsHandle handle = new FailingCpdsHandle();
        PoolProperties properties = new PoolProperties();
        properties.setDataSource(new FailingConnectionPoolDataSource(handle));
        PooledConnection pc = newPooledConnection(properties);

        Assertions.assertThrows(SQLException.class, pc::connect,
                "the logical connection failure must be reported");
        Assertions.assertEquals(1, handle.closeAttempts,
                "the handle must have been closed once before the failure was reported");

        pc.release();
        Assertions.assertEquals(2, handle.closeAttempts,
                "the release must retry the physical close");
        Assertions.assertTrue(handle.closed, "the retried close must have succeeded");
    }
}
