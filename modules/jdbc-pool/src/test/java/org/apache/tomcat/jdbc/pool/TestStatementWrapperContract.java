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

import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.interceptor.StatementCache;
import org.apache.tomcat.jdbc.pool.interceptor.StatementDecoratorInterceptor;
import org.apache.tomcat.jdbc.pool.interceptor.StatementFinalizer;

/**
 * The {@link java.sql.Wrapper} contract of the statement handles the pool
 * hands out - the statement-side counterpart of {@link TestWrapperContract}.
 *
 * <p>Every statement handle the application receives is a dynamic proxy that
 * implements just the standard JDBC interface of its kind: the pool always
 * wraps statements in its {@link StatementFacade} (the
 * <code>useStatementFacade</code> property that used to switch the facade off
 * is deprecated and has no effect), and the proxies of the interceptors that
 * wrap statements beneath the facade implement the same standard interfaces.
 * The driver's own statement class is therefore not reachable by casting in
 * any configuration. What the pool guarantees instead is the honest Wrapper
 * answer: {@code isWrapperFor} says true exactly for what is behind the
 * handle and false for what is not, and {@code unwrap} - through every layer
 * the pool installs - returns the driver's very own statement object, on
 * which driver-only methods execute.</p>
 *
 * <p>What unwrap deliberately does not promise, and what these tests do not
 * pin: the unwrapped object is a driver object, not a handle, so the
 * retirement of the pool's handles at the end of the borrow does not reach
 * what was already unwrapped. That a {@link StatementFinalizer} is what
 * closes the statements underneath at the connection return is pinned here,
 * because the interceptor documentation promises exactly that.</p>
 */
class TestStatementWrapperContract extends JdbcPoolBaseTest {

    private static final String FINALIZER =
            StatementFinalizer.class.getSimpleName();
    private static final String CACHE =
            StatementCache.class.getSimpleName() + "(prepared=true,callable=true,max=10)";
    private static final String DECORATOR =
            StatementDecoratorInterceptor.class.getSimpleName();

    /* The handle is the standard-interface proxy, never the driver object. */
    private static void assertHandleIsTheStandardInterfaceProxy(Statement handle,
            Class<?> standardInterface, Class<?> driverClass, String what) {
        Assertions.assertTrue(Proxy.isProxyClass(handle.getClass()),
                what + ": the application handle must be a dynamic proxy, was "
                        + handle.getClass().getName());
        Assertions.assertTrue(standardInterface.isInstance(handle),
                what + ": the handle must implement its standard JDBC interface");
        Assertions.assertTrue(handle instanceof java.sql.Wrapper,
                what + ": the handle must be a Wrapper, as java.sql.Statement demands");
        // Pinning the exact interface list: the documented contract is the
        // standard interface and nothing else. Should the pool ever decide to
        // mirror driver interfaces onto its handles, this is the pin that
        // must be lifted deliberately, together with the documentation.
        Assertions.assertArrayEquals(new Class<?>[] { standardInterface },
                handle.getClass().getInterfaces(),
                what + ": the handle must implement the standard interface only, "
                        + "found " + Arrays.toString(handle.getClass().getInterfaces()));
        Assertions.assertFalse(driverClass.isInstance(handle),
                what + ": the driver's statement class must not be visible to instanceof");
        Assertions.assertThrows(ClassCastException.class, () -> driverClass.cast(handle),
                what + ": casting the handle to the driver's statement class must fail");
    }

    /* isWrapperFor/unwrap answer honestly through the layers, to the driver object. */
    private static org.h2.jdbc.JdbcStatement assertDriverReachableByUnwrapping(
            Statement handle, Class<? extends org.h2.jdbc.JdbcStatement> driverClass,
            String config, String kind) throws SQLException {
        String what = kind + " statement under " + config;
        Assertions.assertTrue(handle.isWrapperFor(driverClass),
                what + ": isWrapperFor must answer true for the driver's own class");
        Assertions.assertFalse(handle.isWrapperFor(Runnable.class),
                what + ": isWrapperFor must answer false for an interface nothing "
                        + "behind the handle provides");
        Assertions.assertThrows(SQLException.class, () -> handle.unwrap(Runnable.class),
                what + ": unwrap must fail as an SQLException, not hand back a silent null");
        org.h2.jdbc.JdbcStatement driver = handle.unwrap(driverClass);
        Assertions.assertSame(driverStatement(handle), driver,
                what + ": unwrap must return the very driver object that "
                        + "unwrap(Statement.class) reports");
        Assertions.assertSame(driver, handle.unwrap(driverClass),
                what + ": a repeated unwrap must return the same object, not a new wrapper");
        Assertions.assertTrue(driver.getClass().getName().startsWith("org.h2."),
                what + ": unwrap must reach the driver's own object, got "
                        + driver.getClass().getName());
        // A driver-only method - declared by no java.sql interface - must
        // compute on the object reached this way, proving it is live and is
        // really the driver's.
        Assertions.assertTrue(driver.isSimpleIdentifier("POOL_TEST"),
                what + ": the unwrapped driver object must answer driver-only methods");
        Assertions.assertFalse(driver.isSimpleIdentifier("pool test"),
                what + ": and answer them like the driver, not like a stub");
        return driver;
    }

    @Test
    void theApplicationHandleIsAProxyOfTheStandardInterfaceNotTheDriverStatement()
            throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            try (Statement plain = pooled.createStatement()) {
                assertHandleIsTheStandardInterfaceProxy(plain, Statement.class,
                        org.h2.jdbc.JdbcStatement.class, "a plain statement");
            }
            try (PreparedStatement prepared = pooled.prepareStatement("SELECT 1")) {
                assertHandleIsTheStandardInterfaceProxy(prepared, PreparedStatement.class,
                        org.h2.jdbc.JdbcPreparedStatement.class, "a prepared statement");
            }
            try (CallableStatement call = pooled.prepareCall("CALL NOW()")) {
                assertHandleIsTheStandardInterfaceProxy(call, CallableStatement.class,
                        org.h2.jdbc.JdbcCallableStatement.class, "a callable statement");
            }
        }

        // With the finalizer configured, the application still sees only the
        // standard-interface handle: the interceptor's own tracking proxy sits
        // beneath the facade, out of the application's reach - which is why
        // preserving driver interfaces on that inner proxy could not restore
        // what casting lost, and why it is not done.
        DataSource tracked = newDataSource();
        tracked.getPoolProperties().setJdbcInterceptors(FINALIZER);
        tracked.createPool();
        try (Connection pooled = tracked.getConnection();
                PreparedStatement prepared = pooled.prepareStatement("SELECT 1")) {
            assertHandleIsTheStandardInterfaceProxy(prepared, PreparedStatement.class,
                    org.h2.jdbc.JdbcPreparedStatement.class,
                    "a prepared statement tracked by the finalizer");
            Object trackingProxy = proxiedObject(prepared);
            Assertions.assertNotSame(prepared, trackingProxy,
                    "the tracking proxy must still sit between the facade and the driver");
            Assertions.assertTrue(Proxy.isProxyClass(trackingProxy.getClass()),
                    "and the layer beneath the facade must be the interceptor's own proxy");
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void theDeprecatedFacadelessConfigurationCannotMakeTheDriverStatementVisible()
            throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setUseStatementFacade(false);
        ds.createPool();

        try (Connection pooled = ds.getConnection();
                PreparedStatement prepared = pooled.prepareStatement("SELECT 1")) {
            // The deprecated value asks for no facade; it no longer removes
            // it, so the handle keeps the same shape as everywhere else and
            // casting - the only behaviour that configuration ever bought -
            // stays unavailable.
            assertHandleIsTheStandardInterfaceProxy(prepared, PreparedStatement.class,
                    org.h2.jdbc.JdbcPreparedStatement.class,
                    "a prepared statement from the deprecated facadeless configuration");
            Assertions.assertSame(driverStatement(prepared),
                    prepared.unwrap(org.h2.jdbc.JdbcPreparedStatement.class),
                    "unwrap must still reach the driver object through the facade");
        }
    }

    @Test
    void unwrapReachesTheDriversOwnStatementThroughEveryWrappingInterceptor()
            throws Exception {
        // The third column marks the configurations in which the cache parks
        // the statement it hands out directly: a decorator listed beneath
        // the cache becomes the cached delegate, and that is the
        // statement-cache's own affair, not the Wrapper contract pinned here.
        String[][] configurations = {
            { "the statement facade alone", null, "false" },
            { "the statement finalizer beneath the facade", FINALIZER, "false" },
            { "the finalizer and the statement cache beneath the facade",
                    FINALIZER + ";" + CACHE, "true" },
            { "the statement decorator beneath the facade", DECORATOR, "false" },
            { "the finalizer, the cache and the decorator beneath the facade",
                    FINALIZER + ";" + CACHE + ";" + DECORATOR, "false" },
        };
        for (String[] configuration : configurations) {
            DataSource ds = newDataSource();
            if (configuration[1] != null) {
                ds.getPoolProperties().setJdbcInterceptors(configuration[1]);
            }
            ds.createPool();

            try (Connection pooled = ds.getConnection()) {
                try (Statement plain = pooled.createStatement()) {
                    assertDriverReachableByUnwrapping(plain,
                            org.h2.jdbc.JdbcStatement.class,
                            configuration[0], "plain");
                }
                PreparedStatement prepared = pooled.prepareStatement("SELECT 1");
                org.h2.jdbc.JdbcPreparedStatement preparedDriver =
                        (org.h2.jdbc.JdbcPreparedStatement) assertDriverReachableByUnwrapping(
                                prepared, org.h2.jdbc.JdbcPreparedStatement.class,
                                configuration[0], "prepared");
                // The object reached by unwrapping is usable: the prepared
                // query executes through it while the borrow is live.
                try (ResultSet rs = preparedDriver.executeQuery()) {
                    Assertions.assertTrue(rs.next(),
                            "prepared statement under " + configuration[0]
                                    + ": the unwrapped driver statement must still execute");
                }
                prepared.close();
                if ("true".equals(configuration[2])) {
                    // A second prepare of the same SQL is answered from the
                    // cache, under fresh handles of every layer: unwrap must
                    // still report the same driver object beneath them.
                    try (PreparedStatement reused = pooled.prepareStatement("SELECT 1")) {
                        Assertions.assertNotSame(prepared, reused,
                                "the cache hands the parked statement out under a fresh handle");
                        Assertions.assertSame(preparedDriver, driverStatement(reused),
                                "a reused cached statement must unwrap to the same "
                                        + "driver object");
                    }
                }
                try (CallableStatement call = pooled.prepareCall("CALL NOW()")) {
                    assertDriverReachableByUnwrapping(call,
                            org.h2.jdbc.JdbcCallableStatement.class,
                            configuration[0], "callable");
                }
            }
        }
    }

    /**
     * The documentation of {@link StatementFinalizer} promises that a
     * statement the application unwrapped during the borrow is still tracked:
     * unlike the bare facade, which retires only its own handles and leaves
     * the driver statement behind to serve whoever holds the physical session
     * next, the finalizer closes the statements underneath when the
     * connection is returned.
     */
    @Test
    void theFinalizerClosesTheDriverStatementBeneathTheHandleWhenTheBorrowEnds()
            throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(FINALIZER);
        // force the next borrow onto the same physical session
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxIdle(1);
        ds.createPool();

        Connection first = ds.getConnection();
        setSessionMarker(first, 21);
        PreparedStatement prepared = first.prepareStatement("SELECT 1");
        Statement plain = first.createStatement();
        org.h2.jdbc.JdbcPreparedStatement preparedDriver =
                prepared.unwrap(org.h2.jdbc.JdbcPreparedStatement.class);
        org.h2.jdbc.JdbcStatement plainDriver = plain.unwrap(org.h2.jdbc.JdbcStatement.class);
        Assertions.assertFalse(preparedDriver.isClosed(),
                "precondition: the driver statement is open during the borrow");
        first.close();

        Assertions.assertTrue(preparedDriver.isClosed(),
                "the finalizer must close the tracked prepared statement at the return");
        Assertions.assertTrue(plainDriver.isClosed(),
                "the finalizer must close the tracked plain statement at the return");
        Assertions.assertThrows(SQLException.class, preparedDriver::execute,
                "the unwrapped prepared statement must not run after the borrow ended");
        Assertions.assertThrows(SQLException.class,
                () -> plainDriver.execute("SET @POOL_TEST_MARKER = 999"),
                "the unwrapped plain statement must not run after the borrow ended");

        Connection second = ds.getConnection();
        Assertions.assertEquals(21, sessionMarker(second).intValue(),
                "the next borrower's session must be untouched");
        try (Statement st = second.createStatement();
                ResultSet rs = st.executeQuery("SELECT 1")) {
            Assertions.assertTrue(rs.next(),
                    "the current borrower must get working handles");
        }
        second.close();
    }
}
