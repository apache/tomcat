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
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The JDBC 4.3 request boundary hints: the pool is expected to call
 * {@code Connection.beginRequest()} on the physical connection as it hands
 * that connection to the application and {@code Connection.endRequest()}
 * as the application returns it, so a driver that optimises session
 * handling around a unit of work can do so. A hint is advisory, so a
 * driver that refuses one must not fail the borrow or the return around
 * it, and the pool still has to hand out a working, reusable connection.
 *
 * <p>Scenario harvested from the contribution of pull request 677, which
 * counted the hints on a stub driver; here the counting proxy sits on the
 * H2 connection itself, so the assertions also prove the hints travelled
 * on the physical session and bracketed each borrow.</p>
 */
class TestRequestBoundaries extends JdbcPoolBaseTest {

    private static final AtomicInteger BEGIN_REQUEST_COUNT = new AtomicInteger();
    private static final AtomicInteger END_REQUEST_COUNT = new AtomicInteger();

    /*
     * Same lookup the pool itself performs: on a Java 8 runtime the hints
     * do not exist, the pool sends none, and there is nothing for this
     * class to observe.
     */
    private static void assumeRequestHintsExist() {
        boolean exists;
        try {
            Connection.class.getMethod("beginRequest");
            exists = true;
        } catch (NoSuchMethodException e) {
            exists = false;
        }
        Assumptions.assumeTrue(exists,
                "java.sql.Connection has no request boundary hints before Java 9");
    }

    /*
     * The hints must be counted where the pool sends them: at the physical
     * connection. A driver configured by class name wraps whatever the H2
     * driver returns in a proxy that counts the two hint methods before
     * delegating them to H2, and the pool is configured to use that driver
     * against the real database.
     */
    private DataSource hintCountingDataSource(Class<? extends Driver> driverClass)
            throws SQLException {
        BEGIN_REQUEST_COUNT.set(0);
        END_REQUEST_COUNT.set(0);
        DataSource ds = newDataSource();
        ds.setDriverClassName(driverClass.getName());
        // start empty: no physical connection, so no pool-start traffic to
        // discount from the counts asserted below
        ds.getPoolProperties().setInitialSize(0);
        ds.createPool();
        return ds;
    }

    @Test
    void hintsBracketEachBorrowOfTheSameSession() throws Exception {
        assumeRequestHintsExist();
        DataSource ds = hintCountingDataSource(HintCountingDriver.class);

        Assertions.assertEquals(0, BEGIN_REQUEST_COUNT.get(), "no borrow yet");
        Assertions.assertEquals(0, END_REQUEST_COUNT.get(), "no borrow yet");

        Connection first = ds.getConnection();
        Assertions.assertEquals(1, BEGIN_REQUEST_COUNT.get(),
                "beginRequest() must reach the driver as the connection is handed out");
        Assertions.assertEquals(0, END_REQUEST_COUNT.get(),
                "endRequest() belongs to the return, not the borrow");

        first.close();
        Assertions.assertEquals(1, END_REQUEST_COUNT.get(),
                "endRequest() must reach the driver as the connection is returned");

        // a second borrow on the same physical session hints again - the
        // hints bracket every unit of work, not just the first one
        Connection second = ds.getConnection();
        Assertions.assertEquals(2, BEGIN_REQUEST_COUNT.get());
        Assertions.assertEquals(1, END_REQUEST_COUNT.get());
        second.close();
        Assertions.assertEquals(2, END_REQUEST_COUNT.get());

        Assertions.assertEquals(1, poolSessions(),
                "both borrows ran on the same physical session");
    }

    @Test
    void aDriverThatRefusesTheHintsNeitherFailsTheBorrowNorTheReturn() throws Exception {
        assumeRequestHintsExist();
        DataSource ds = hintCountingDataSource(FailingHintDriver.class);

        try (Connection con = ds.getConnection()) {
            Assertions.assertEquals(1, BEGIN_REQUEST_COUNT.get(),
                    "the refused hint must still have been attempted");
            // the connection itself is unaffected by the refusal
            con.createStatement().close();
        }
        Assertions.assertEquals(1, END_REQUEST_COUNT.get(),
                "the refused hint must not strand the return of the connection");

        // the connection stayed poolable across the refused hints
        try (Connection con = ds.getConnection()) {
            Assertions.assertEquals(2, BEGIN_REQUEST_COUNT.get());
        }
        Assertions.assertEquals(1, poolSessions(),
                "the refused hints must not have cost the pool its session");
    }

    /**
     * Driver that counts the request boundary hints reaching the physical
     * connection it returns. Configured through {@code driverClassName},
     * so it must stay a public static class with a no-arg constructor.
     */
    public static class HintCountingDriver implements Driver {

        private final Driver delegate = new org.h2.Driver();

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            Connection connection = delegate.connect(url, info);
            if (connection == null) {
                return null;
            }
            return (Connection) Proxy.newProxyInstance(
                    HintCountingDriver.class.getClassLoader(),
                    new Class<?>[] { Connection.class },
                    hintCounter(connection, refuseHints()));
        }

        /*
         * Overridden by the subclass below; a driver instantiated by class
         * name can only be told apart by its own behaviour.
         */
        protected boolean refuseHints() {
            return false;
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

    /**
     * Driver that additionally refuses both hints, to pin that the pool
     * treats a refused hint as the advisory failure it is.
     */
    public static class FailingHintDriver extends HintCountingDriver {
        @Override
        protected boolean refuseHints() {
            return true;
        }
    }

    /*
     * Counts the two hint methods by name and delegates everything, the
     * hints included, to the H2 connection; by name, because this suite
     * still compiles against Java 8. The refusing configuration makes the
     * two hints throw instead of delegating, like a driver that declines
     * to take them.
     */
    private static InvocationHandler hintCounter(Connection vendor, boolean refuseHints) {
        return new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String name = method.getName();
                if ("beginRequest".equals(name)) {
                    BEGIN_REQUEST_COUNT.incrementAndGet();
                    if (refuseHints) {
                        throw new SQLException("beginRequest() refused by the test driver");
                    }
                } else if ("endRequest".equals(name)) {
                    END_REQUEST_COUNT.incrementAndGet();
                    if (refuseHints) {
                        throw new SQLException("endRequest() refused by the test driver");
                    }
                }
                try {
                    return method.invoke(vendor, args);
                } catch (InvocationTargetException x) {
                    throw x.getCause();
                }
            }
        };
    }
}
