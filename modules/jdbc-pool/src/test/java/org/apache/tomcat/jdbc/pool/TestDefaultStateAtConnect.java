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
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.interceptor.ConnectionState;

/**
 * Expected functionality of who applies the configured default connection
 * state to a physical session: {@code connect()} sets the defaults directly
 * only when no {@link ConnectionState} interceptor is configured - the
 * interceptor applies them itself on every borrow, so applying them at connect
 * time as well is a second, redundant trip to the driver per configured
 * default. Recognition must cover every name the pool resolves: the
 * fully qualified name and the documented short name alike, with properties
 * and surrounding whitespace, and a subclass of the state interceptor
 * inherits its job under any name. An interceptor merely named after the
 * state interceptor does not.
 *
 * <p>The last test also pins that the {@code initSQL} persists for the
 * connection's lifetime whichever spelling configures the interceptor. That
 * persistence is an end-to-end invariant rather than a discriminator for the
 * redundancy itself: when {@code connect()} applies
 * {@code defaultAutoCommit=false} the freshly executed {@code initSQL} sits
 * in a transaction that the init-time validation terminates by commit, so it
 * survives even a redundant application. The driver call counts are what pin
 * the redundancy away.</p>
 *
 * <p>Driver-level call counts are the measurement: the wrapping driver below
 * sees the direct applications from {@code connect()} (during
 * {@code createPool()}, where the pool creates its initial connections
 * without borrowing them) and the interceptor's applications on each borrow,
 * and nothing in these tests calls a state setter from a borrower.</p>
 */
class TestDefaultStateAtConnect extends JdbcPoolBaseTest {

    /*
     * Driver-level call counters, fed by the wrapping driver below. The tests
     * are sequential and reset what they observe.
     */
    private static final AtomicInteger SET_AUTOCOMMIT = new AtomicInteger();
    private static final AtomicInteger SET_READ_ONLY = new AtomicInteger();
    private static final AtomicInteger SET_ISOLATION = new AtomicInteger();
    private static final AtomicInteger SET_CATALOG = new AtomicInteger();
    private static final AtomicInteger ROLLED_BACK = new AtomicInteger();

    private static void resetCounters() {
        SET_AUTOCOMMIT.set(0);
        SET_READ_ONLY.set(0);
        SET_ISOLATION.set(0);
        SET_CATALOG.set(0);
        ROLLED_BACK.set(0);
    }

    /* The catalog configuredPool() configures as the pool's default. */
    private String configuredCatalog;

    /*
     * A pool on the counting driver with every default configured, so each of
     * the four state settings is an observable round trip whenever it is
     * applied. The catalog is the database's real one: H2 accepts any catalog
     * name, but the borrower must still find a usable session.
     */
    private DataSource configuredPool(String interceptorConfig) throws SQLException {
        String catalog;
        try (Connection admin = adminConnection()) {
            catalog = admin.getCatalog();
        }
        configuredCatalog = catalog;
        DataSource ds = newDataSource();
        ds.setDriverClassName(StateCountingDriver.class.getName());
        ds.getPoolProperties().setJdbcInterceptors(interceptorConfig);
        ds.getPoolProperties().setDefaultAutoCommit(Boolean.FALSE);
        ds.getPoolProperties().setDefaultReadOnly(Boolean.FALSE);
        ds.getPoolProperties().setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        ds.getPoolProperties().setDefaultCatalog(catalog);
        return ds;
    }

    private static void assertNoDefaultsAppliedYet(String when) {
        Assertions.assertEquals(0, SET_AUTOCOMMIT.get(), when + ": setAutoCommit must not reach the driver");
        Assertions.assertEquals(0, SET_READ_ONLY.get(), when + ": setReadOnly must not reach the driver");
        Assertions.assertEquals(0, SET_ISOLATION.get(), when + ": setTransactionIsolation must not reach the driver");
        Assertions.assertEquals(0, SET_CATALOG.get(), when + ": setCatalog must not reach the driver");
        Assertions.assertEquals(0, ROLLED_BACK.get(), when + ": nothing may terminate a transaction");
    }

    private static void assertDefaultsAppliedExactlyOnce(String when) {
        Assertions.assertEquals(1, SET_AUTOCOMMIT.get(), when + ": auto commit must reach the driver exactly once");
        Assertions.assertEquals(1, SET_READ_ONLY.get(), when + ": read-only must reach the driver exactly once");
        Assertions.assertEquals(1, SET_ISOLATION.get(), when + ": isolation must reach the driver exactly once");
        Assertions.assertEquals(1, SET_CATALOG.get(), when + ": catalog must reach the driver exactly once");
    }

    private void assertBorrowerSeesTheDefaults(Connection c) throws SQLException {
        Assertions.assertFalse(c.getAutoCommit(), "the configured auto commit must apply");
        Assertions.assertFalse(c.isReadOnly(), "the configured read-only state must apply");
        Assertions.assertEquals(Connection.TRANSACTION_READ_COMMITTED, c.getTransactionIsolation(),
                "the configured isolation level must apply");
        Assertions.assertEquals(configuredCatalog, c.getCatalog(),
                "the configured catalog must apply");
    }

    /*
     * With the interceptor configured, connect() must apply none of the
     * defaults itself: the interceptor's reset() on the first borrow applies
     * each exactly once, the warm cache means no later borrow repeats it, and
     * the borrower sees the configured state.
     */
    private void interceptorConfiguredAsAppliesTheDefaultsOnlyOnBorrow(String interceptorConfig) throws Exception {
        resetCounters();
        DataSource ds = configuredPool(interceptorConfig);
        ds.createPool();
        assertNoDefaultsAppliedYet("connect() of an interceptor-configured pool");

        try (Connection first = ds.getConnection()) {
            assertDefaultsAppliedExactlyOnce("the first borrow of an interceptor-configured pool");
            assertBorrowerSeesTheDefaults(first);
        }
        try (Connection second = ds.getConnection()) {
            assertDefaultsAppliedExactlyOnce("a later borrow must not repeat the application");
            assertBorrowerSeesTheDefaults(second);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ConnectionState",
            " ConnectionState ",
            "ConnectionState(useEquals=true)",
            "ConnectionState;ResetAbandonedTimer",
            "org.apache.tomcat.jdbc.pool.interceptor.ConnectionState",
            "org.apache.tomcat.jdbc.pool.interceptor.ConnectionState(useEquals=true)" })
    void everyFormOfTheInterceptorNameLeavesTheDefaultsToTheInterceptor(String interceptorConfig) throws Exception {
        interceptorConfiguredAsAppliesTheDefaultsOnlyOnBorrow(interceptorConfig);
    }

    @Test
    void withoutTheInterceptorTheDefaultsAreAppliedDirectlyAtConnect() throws Exception {
        resetCounters();
        DataSource ds = configuredPool(null);
        ds.createPool();
        // No interceptor can apply the defaults, so connect() must. With the
        // auto commit default applied at connect() there is never a pending
        // transaction, so nothing may roll back either.
        assertDefaultsAppliedExactlyOnce("connect() of an interceptor-less pool");
        assertNoRollbackReachedTheDriver();

        try (Connection first = ds.getConnection()) {
            assertDefaultsAppliedExactlyOnce("a borrow must not re-apply what connect() applied");
            assertBorrowerSeesTheDefaults(first);
        }
    }

    private static void assertNoRollbackReachedTheDriver() {
        Assertions.assertEquals(0, ROLLED_BACK.get(), "no rollback may reach the driver");
    }

    @Test
    void anInterceptorNamedAfterTheStateInterceptorButNotExtendingItDoesNotTakeOverItsJob() throws Exception {
        resetCounters();
        DataSource ds = configuredPool(ConnectionStateLookalike.class.getName());
        ds.createPool();
        assertDefaultsAppliedExactlyOnce("connect() with a non-state interceptor configured");
        try (Connection first = ds.getConnection()) {
            assertDefaultsAppliedExactlyOnce("the lookalike must not re-apply the defaults");
            assertBorrowerSeesTheDefaults(first);
        }
    }

    @Test
    void aSubclassOfTheStateInterceptorIsRecognizedAsDoingTheJob() throws Exception {
        // The subclass inherits reset(), so it applies the defaults on every
        // borrow under any name; connect() must recognize that and stay out.
        interceptorConfiguredAsAppliesTheDefaultsOnlyOnBorrow(StateKeeper.class.getName());
    }

    @Test
    void theInitSqlSurvivesTheFirstBorrowWhicheverNameFormConfiguresTheInterceptor() throws Exception {
        for (String interceptorConfig : new String[] { "ConnectionState",
                "org.apache.tomcat.jdbc.pool.interceptor.ConnectionState" }) {
            // Each name form gets its own pool and its own init target, so
            // what one pool's init SQL committed cannot stand in for the
            // other's.
            String table = uniqueTable();
            try (Connection admin = adminConnection(); Statement st = admin.createStatement()) {
                st.execute("CREATE TABLE " + table + " (v INT)");
            }
            resetCounters();
            DataSource ds = newDataSource();
            ds.setDriverClassName(StateCountingDriver.class.getName());
            ds.getPoolProperties().setJdbcInterceptors(interceptorConfig);
            ds.getPoolProperties().setDefaultAutoCommit(Boolean.FALSE);
            ds.getPoolProperties().setInitSQL("INSERT INTO " + table + " VALUES (1)");
            ds.createPool();
            try (Connection borrower = ds.getConnection()) {
                Assertions.assertFalse(borrower.getAutoCommit(),
                        "the first borrower must see the configured auto commit");
            }
            // The end-to-end invariant: both name forms must hand the first
            // borrower a session whose init SQL has persisted. A redundant
            // application by connect() would not break this - the init-time
            // validation now terminates an open transaction by commit - so
            // the call counters, not this oracle, are what pin the
            // redundancy away.
            Assertions.assertEquals(1, adminRowCount(table),
                    "the init SQL of a pool configured as '" + interceptorConfig
                            + "' must persist for the connection's lifetime");
        }
    }

    /**
     * An interceptor that is named after {@link ConnectionState} but does not
     * extend it: it must not be mistaken for the interceptor that applies the
     * default connection state.
     */
    public static class ConnectionStateLookalike extends JdbcInterceptor {
        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // caches nothing, restores nothing
        }
    }

    /**
     * A {@link ConnectionState} interceptor under a name that does not
     * contain the state interceptor's name: it inherits the job of applying
     * the defaults on every borrow.
     */
    public static class StateKeeper extends ConnectionState {
    }

    /**
     * Driver handing out the real H2 connections wrapped in a proxy that
     * counts the connection state setters and {@code rollback()} calls
     * reaching the driver. Configured through {@code driverClassName}, so it
     * must stay public with a no-arg constructor.
     */
    public static class StateCountingDriver implements Driver {

        private final Driver delegate = new org.h2.Driver();

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            Connection connection = delegate.connect(url, info);
            if (connection == null) {
                return null;
            }
            return (Connection) Proxy.newProxyInstance(
                    StateCountingDriver.class.getClassLoader(),
                    new Class<?>[] { Connection.class },
                    new CountingHandler(connection));
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return delegate.acceptsURL(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return delegate.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return delegate.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return delegate.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return delegate.jdbcCompliant();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }
    }

    /* Counts the state setters and rollback(); delegates everything else. */
    private static class CountingHandler implements InvocationHandler {

        private final Connection vendor;

        CountingHandler(Connection vendor) {
            this.vendor = vendor;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "setAutoCommit":
                    SET_AUTOCOMMIT.incrementAndGet();
                    break;
                case "setReadOnly":
                    SET_READ_ONLY.incrementAndGet();
                    break;
                case "setTransactionIsolation":
                    SET_ISOLATION.incrementAndGet();
                    break;
                case "setCatalog":
                    SET_CATALOG.incrementAndGet();
                    break;
                case "rollback":
                    ROLLED_BACK.incrementAndGet();
                    break;
                default: // NOOP
            }
            try {
                return method.invoke(vendor, args);
            } catch (InvocationTargetException x) {
                throw x.getCause();
            }
        }
    }
}
