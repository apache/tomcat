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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Expected XA functionality: with a real XA-capable data source
 * injected (H2's {@code org.h2.jdbcx.JdbcDataSource}), the pool hands
 * out pooled connections that implement {@link XAConnection}, and the
 * XA resource drives real two-phase commit against the database.
 */
class TestXaConnection extends JdbcPoolBaseTest {

    /** Simple test transaction id. */
    private static final class TestXid implements Xid {
        private final byte qualifier;
        TestXid(int qualifier) {
            this.qualifier = (byte) qualifier;
        }
        @Override
        public int getFormatId() {
            return 1;
        }
        @Override
        public byte[] getGlobalTransactionId() {
            return new byte[] { 42, qualifier };
        }
        @Override
        public byte[] getBranchQualifier() {
            return new byte[] { qualifier };
        }
    }

    private XADataSource newXaPool() {
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
        return manage(ds);
    }

    @Test
    void xaCommitPersistsThroughTheDatabase() throws Exception {
        XADataSource ds = newXaPool();
        String t = uniqueTable();

        XAConnection xc = ds.getXAConnection();
        Assertions.assertNotNull(xc);
        XAResource xa = xc.getXAResource();
        Assertions.assertNotNull(xa, "pooled connection must expose its XA resource");
        Xid xid = new TestXid(1);

        Connection c = xc.getConnection();
        c.setAutoCommit(false);
        xa.start(xid, XAResource.TMNOFLAGS);
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE " + t + " (v INT)");
            st.execute("INSERT INTO " + t + " VALUES (1)");
        }
        xa.end(xid, XAResource.TMSUCCESS);
        Assertions.assertEquals(XAResource.XA_OK, xa.prepare(xid));

        // not visible before commit
        Assertions.assertEquals(0, adminRowCount(t), "work must be invisible before commit");

        xa.commit(xid, false);
        Assertions.assertEquals(1, adminRowCount(t), "XA commit must persist to the database");

        // the branch is complete; returning the XA connection to the pool
        // must keep the physical connection alive for the next borrower
        xc.close();
        Assertions.assertEquals(1, poolSessions(),
                "the physical XA connection must stay in the pool");
        try (Connection again = ds.getConnection();
                ResultSet rs = again.createStatement().executeQuery("SELECT 1")) {
            Assertions.assertTrue(rs.next(), "the pooled connection must still be usable");
        }
    }

    @Test
    void xaRollbackDiscardsWorkAndConnectionIsReusable() throws Exception {
        XADataSource ds = newXaPool();
        String t = uniqueTable();

        XAConnection xc = ds.getXAConnection();
        XAResource xa = xc.getXAResource();
        Xid xid = new TestXid(2);

        Connection c = xc.getConnection();
        c.setAutoCommit(false);
        xa.start(xid, XAResource.TMNOFLAGS);
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE " + t + " (v INT)");
            st.execute("INSERT INTO " + t + " VALUES (1)");
        }
        xa.end(xid, XAResource.TMSUCCESS);
        xa.rollback(xid);
        Assertions.assertEquals(0, adminRowCount(t), "rolled back XA work must be gone");
        xc.close();

        // pool hands the very same physical connection out again, for plain
        // non-XA use, with the session state restored to the pool defaults
        try (Connection again = ds.getConnection()) {
            try (ResultSet rs = again.createStatement().executeQuery("SELECT 1")) {
                Assertions.assertTrue(rs.next());
            }
            Assertions.assertTrue(again.getAutoCommit(),
                    "a returned XA connection must come back in auto commit mode");
            setSessionMarker(again, 7);
        }
        try (Connection third = ds.getConnection()) {
            Assertions.assertEquals(Integer.valueOf(7), sessionMarker(third),
                    "the rolled back XA connection must have been pooled, not replaced");
        }
        Assertions.assertEquals(1, poolSessions(),
                "the XA pool must hold a single physical connection");
    }

    @Test
    void xaPoolHandedOutConnectionImplementsXaConnection() throws Exception {
        XADataSource ds = newXaPool();
        Connection c = ds.getConnection();
        Assertions.assertInstanceOf(XAConnection.class, c,
                "connections from an XA pool must implement XAConnection");
        c.close();
    }

    @Test
    void plainPoolRefusesXaConnections() throws Exception {
        DataSource ds = newDataSource(); // non-XA pool
        Assertions.assertThrows(SQLException.class, ds::getXAConnection,
                "a non-XA pool must refuse XA connections");
    }

    @Test
    void driverUrlBackedXaPoolFailsXaRequestWithActionableMessage() {
        // The documented "<Resource type=javax.sql.XADataSource .../>"
        // shape with only driver/URL: the marker class cannot make the
        // pooled connections XA-capable, so the request must fail with an
        // explicit hint at the missing configuration instead of a generic
        // refusal that only reads as such after the connection was already
        // borrowed, closed and thrown away.
        XADataSource ds = new XADataSource();
        ds.setUrl(getUrl());
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.getPoolProperties().setJmxEnabled(false);
        ds.getPoolProperties().setInitialSize(1);
        ds.getPoolProperties().setMinIdle(0);
        manage(ds);

        SQLException e = Assertions.assertThrows(SQLException.class, ds::getXAConnection,
                "a driver/URL backed XA pool must refuse XA connections");
        Assertions.assertTrue(e.getMessage().contains("dataSource"),
                "failure must name the missing dataSource/dataSourceJNDI configuration, was: "
                        + e.getMessage());
        SQLException e2 = Assertions.assertThrows(
                SQLException.class, () -> ds.getXAConnection("sa", ""),
                "the credential variant must refuse the same way");
        Assertions.assertTrue(e2.getMessage().contains("dataSource"),
                "failure must name the missing dataSource/dataSourceJNDI configuration, was: "
                        + e2.getMessage());
    }

    @Test
    void nonXaBackedDataSourceFailsXaRequestWithActionableMessage() {
        // A pool backed through the dataSource attribute by a data source
        // that is not XA-capable must also fail with the configuration hint.
        XADataSource ds = new XADataSource();
        ds.getPoolProperties().setJmxEnabled(false);
        ds.getPoolProperties().setInitialSize(1);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setDataSource(new PlainDataSource(getUrl()));
        manage(ds);

        SQLException e = Assertions.assertThrows(SQLException.class, ds::getXAConnection,
                "a pool backed by a non-XA data source must refuse XA connections");
        Assertions.assertTrue(e.getMessage().contains("PlainDataSource"),
                "failure must name the offending data source, was: " + e.getMessage());
        Assertions.assertTrue(e.getMessage().contains("javax.sql.XADataSource"),
                "failure must name the missing XA data source type, was: " + e.getMessage());
    }

    /**
     * A plain, non-XA data source, as one might reference by mistake
     * through the dataSource or dataSourceJNDI attribute.
     */
    private static final class PlainDataSource implements javax.sql.DataSource {

        private final org.h2.jdbcx.JdbcDataSource delegate = new org.h2.jdbcx.JdbcDataSource();

        PlainDataSource(String url) {
            delegate.setUrl(url);
            delegate.setUser("sa");
            delegate.setPassword("");
        }

        @Override
        public Connection getConnection() throws SQLException {
            return delegate.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return delegate.getConnection(username, password);
        }

        @Override
        public java.io.PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return iface.isInstance(this) || delegate.isWrapperFor(iface);
        }
    }
}
