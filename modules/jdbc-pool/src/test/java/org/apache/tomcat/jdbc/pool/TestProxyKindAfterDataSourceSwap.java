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
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.XAConnection;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The proxy class cached for building connection facades must be selected
 * per connection kind. Swapping the backing data source of a live pool
 * (DataSourceProxy.setDataSource) can make later connections a different
 * kind than the first one; they must neither lose the XAConnection
 * capability nor claim it without an XA connection behind them.
 */
class TestProxyKindAfterDataSourceSwap extends JdbcPoolBaseTest {

    /* A plain, non-XA javax.sql.DataSource for H2. */
    private static final class PlainDataSource implements javax.sql.DataSource {

        private final String url;

        PlainDataSource(String url) {
            this.url = url;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, "sa", "");
        }

        @Override
        public Connection getConnection(String user, String password)
                throws SQLException {
            return DriverManager.getConnection(url, user, password);
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            // no logging
        }

        @Override
        public void setLoginTimeout(int seconds) {
            // no timeout
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
            throw new SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }

    private org.h2.jdbcx.JdbcDataSource newH2XaDataSource() {
        org.h2.jdbcx.JdbcDataSource inner = new org.h2.jdbcx.JdbcDataSource();
        inner.setUrl(getUrl());
        inner.setUser("sa");
        inner.setPassword("");
        return inner;
    }

    @Test
    void connectionsCreatedAfterSwapToXaExposeXaCapability() throws Exception {
        DataSource ds = newDataSource();
        // hold the only pooled connection so the next borrow must create a
        // fresh physical connection through the swapped data source
        Connection held = ds.getConnection();

        ds.setDataSource(newH2XaDataSource());
        try (Connection c2 = ds.getConnection()) {
            Assertions.assertTrue(c2 instanceof XAConnection,
                    "a connection created through an XA data source must be "
                            + "proxied as an XAConnection even though the "
                            + "pool started out non-XA");
            Assertions.assertNotNull(((XAConnection) c2).getXAResource(),
                    "the XA dispatch must reach the real XA connection");
        }
        held.close();
    }

    @Test
    void connectionsCreatedAfterSwapToNonXaDoNotClaimXaCapability()
            throws Exception {
        XADataSource ds = manage(new XADataSource());
        ds.setDriverClassName("org.h2.Driver");
        ds.getPoolProperties().setJmxEnabled(false);
        ds.getPoolProperties().setInitialSize(1);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setDataSource(newH2XaDataSource());
        // first borrow caches the proxy class for an XA connection
        Connection held = ds.getConnection();

        ds.setDataSource(new PlainDataSource(getUrl()));
        try (Connection c2 = ds.getConnection()) {
            Assertions.assertFalse(c2 instanceof XAConnection,
                    "a connection created through a plain data source must "
                            + "not be proxied as an XAConnection just "
                            + "because the pool started out XA");
        }
        Assertions.assertThrows(SQLException.class, ds::getXAConnection,
                "the XA data source facade must refuse to hand out a "
                        + "connection that no longer has an XA connection "
                        + "behind it");
        held.close();
    }
}
