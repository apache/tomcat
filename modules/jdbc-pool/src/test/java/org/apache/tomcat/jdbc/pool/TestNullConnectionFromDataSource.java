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
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The JDBC specification lets {@link javax.sql.DataSource#getConnection()}
 * return null when the provider cannot hand out a connection - unlike
 * {@link java.sql.Driver#connect(String, java.util.Properties)}, which must
 * throw instead. A pool drawing its connections from a plain DataSource must
 * therefore reject a null connection at once, the way the driver path and the
 * XA and ConnectionPoolDataSource handle paths already do: accepting it let
 * the connect "succeed" with no connection behind it, which the pool later
 * reported as a failed validation query for a connection that was never
 * created - or, once a default connection state (auto-commit, read-only,
 * isolation, catalog) was configured, as a raw NullPointerException from the
 * state setup. The null handling of the two handle-based providers is pinned
 * by {@code TestXaConnectionLeak} and {@code TestConnectionPoolDataSourceLeak}.
 */
class TestNullConnectionFromDataSource extends JdbcPoolBaseTest {

    /**
     * A spec-faithful DataSource entitled to hand back no connection: while
     * refusal is armed, both overloads return null; once disarmed they hand
     * out real H2 connections, so a test can also verify that a refusal is
     * not fatal to the pool. The call counters pin which overload the pool
     * used for the configured credentials.
     */
    private static final class NullReturningDataSource implements javax.sql.DataSource {

        private final String url;
        private final AtomicInteger noArgCalls = new AtomicInteger();
        private final AtomicInteger credentialedCalls = new AtomicInteger();
        private volatile boolean returnsNull = true;

        NullReturningDataSource(String url) {
            this.url = url;
        }

        @Override
        public Connection getConnection() throws SQLException {
            noArgCalls.incrementAndGet();
            return returnsNull ? null : DriverManager.getConnection(url, "sa", "");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            credentialedCalls.incrementAndGet();
            return returnsNull ? null : DriverManager.getConnection(url, username, password);
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
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
        public Logger getParentLogger() {
            return Logger.getLogger(NullReturningDataSource.class.getName());
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }

    private DataSource nullProviderPool(NullReturningDataSource provider) {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setDataSource(provider);
        ds.getPoolProperties().setInitialSize(0);
        return ds;
    }

    private static void assertNamesTheProvider(SQLException failure) {
        Assertions.assertTrue(
                failure.getMessage().contains(NullReturningDataSource.class.getName()),
                "the failure must name the offending provider, was: " + failure.getMessage());
        Assertions.assertTrue(failure.getMessage().contains("null connection"),
                "the failure must report that the provider returned no connection, was: "
                        + failure.getMessage());
    }

    @Test
    void aNullConnectionFailsTheBorrowAndNamesTheProvider() throws Exception {
        NullReturningDataSource provider = new NullReturningDataSource(getUrl());
        DataSource ds = nullProviderPool(provider);

        // Without any default connection state configured, a null connection
        // used to survive connect() and surface as a failed connect-time
        // validation of a connection that was never created.
        SQLException failure = Assertions.assertThrows(SQLException.class, ds::getConnection,
                "a provider that hands back no connection must fail the borrow");
        assertNamesTheProvider(failure);
        Assertions.assertFalse(failure.getMessage().contains("Validation Query"),
                "the failure must not blame validation for a connection that "
                        + "was never created, was: " + failure.getMessage());
        Assertions.assertEquals(1, provider.credentialedCalls.get(),
                "the configured credentials must be offered to the provider");
        Assertions.assertEquals(0, provider.noArgCalls.get());
        awaitSessions(0);
    }

    @Test
    void aNullConnectionWithAConfiguredDefaultFailsWithoutANullPointerException()
            throws Exception {
        NullReturningDataSource provider = new NullReturningDataSource(getUrl());
        DataSource ds = nullProviderPool(provider);
        ds.getPoolProperties().setDefaultAutoCommit(Boolean.TRUE);

        // Applying the configured default state used to dereference the null
        // connection: the borrow failed with the message of a bare
        // NullPointerException, wrapped only by the pool's generic error
        // translation.
        SQLException failure = Assertions.assertThrows(SQLException.class, ds::getConnection,
                "a provider that hands back no connection must fail the borrow");
        Assertions.assertFalse(failure.getCause() instanceof NullPointerException,
                "the borrow must not fail through the null-connection "
                        + "dereference of the default-state setup, cause was: "
                        + failure.getCause());
        assertNamesTheProvider(failure);
        awaitSessions(0);
    }

    @Test
    void aDirectConnectWithAConfiguredDefaultRejectsTheNullConnection() throws Exception {
        NullReturningDataSource provider = new NullReturningDataSource(getUrl());
        PoolProperties properties = new PoolProperties();
        properties.setUrl(getUrl());
        properties.setDriverClassName("org.h2.Driver");
        properties.setUsername("sa");
        properties.setPassword("");
        properties.setJmxEnabled(false);
        properties.setInitialSize(0);
        properties.setRemoveAbandoned(false);
        properties.setTimeBetweenEvictionRunsMillis(-1);
        properties.setDefaultReadOnly(Boolean.TRUE);
        properties.setDataSource(provider);

        ConnectionPool pool = new ConnectionPool(properties);
        PooledConnection con = new PooledConnection(properties, pool);
        // The contract of connect() is an SQLException on any failure to
        // establish the connection - never a NullPointerException from the
        // default-state setup that follows it.
        Assertions.assertThrows(SQLException.class, con::connect,
                "a null connection must be rejected by the connect itself");
        Assertions.assertFalse(con.isInitialized());
    }

    @Test
    void theNoArgumentOverloadIsGuardedAsTightly() throws Exception {
        NullReturningDataSource provider = new NullReturningDataSource(getUrl());
        DataSource ds = manage(new DataSource());
        ds.setUrl(getUrl());
        ds.getPoolProperties().setJmxEnabled(false);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setDataSource(provider);
        // No credentials configured: the pool must take the no-argument
        // overload, which is just as entitled to return null.
        SQLException failure = Assertions.assertThrows(SQLException.class, ds::getConnection,
                "a provider that hands back no connection must fail the borrow");
        assertNamesTheProvider(failure);
        Assertions.assertEquals(1, provider.noArgCalls.get(),
                "without configured credentials the no-argument overload must be used");
        Assertions.assertEquals(0, provider.credentialedCalls.get());
        awaitSessions(0);
    }

    @Test
    void thePoolStillServesBorrowersAfterTheProviderStopsReturningNull() throws Exception {
        NullReturningDataSource provider = new NullReturningDataSource(getUrl());
        DataSource ds = nullProviderPool(provider);

        Assertions.assertThrows(SQLException.class, ds::getConnection,
                "the refusal must fail the borrow");
        awaitSessions(0);

        provider.returnsNull = false;
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("SELECT 1");
        }
        awaitSessions(1);
    }
}
