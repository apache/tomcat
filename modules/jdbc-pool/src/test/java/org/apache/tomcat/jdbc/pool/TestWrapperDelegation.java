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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The {@code Wrapper} contract of the objects the pool hands out, on the
 * layers below the first one.
 * <p>
 * A pooled handle must unwrap to the vendor's own connection even when the
 * driver's connection is not that connection but a wrapper around it - a
 * driver built around another driver's, like the AWS Advanced JDBC Wrapper
 * over the PostgreSQL driver. The wrapper, not the class of the object it
 * presents, is what knows which interfaces it can unwrap to, so the handle
 * has to delegate {@code isWrapperFor()} and {@code unwrap()} to it when
 * the object it presents does not implement the interface itself. The
 * pool's own {@code DataSource}, whose implementations of the two methods
 * always answered false and returned null, must recognise itself and the
 * configured {@code dataSource}, and must report a failed unwrap as the
 * {@code SQLException} the contract requires.</p>
 *
 * <p>Scenario harvested from pull request 1077 (bz 59569); where its test
 * ran against the retired stub driver, this one puts the wrapping layer
 * over real H2 connections, so the unwrapped vendor object is asserted
 * against the database as well.</p>
 */
class TestWrapperDelegation extends JdbcPoolBaseTest {

    @Test
    void aPooledHandleUnwrapsThroughADriverThatWrapsAnotherDriversConnection()
            throws Exception {
        DataSource ds = newDataSource();
        ds.setDriverClassName(WrappingDriver.class.getName());

        try (Connection con = ds.getConnection()) {
            // the layer the driver presents directly: as it always was
            Assertions.assertTrue(con.isWrapperFor(WrapperConnection.class));
            Assertions.assertInstanceOf(WrapperConnection.class,
                    con.unwrap(WrapperConnection.class));

            // the vendor layer underneath it: only reachable by asking the
            // presented wrapper about itself
            Assertions.assertTrue(con.isWrapperFor(org.h2.jdbc.JdbcConnection.class),
                    "the handle must reach through the driver's own wrapper");
            Object vendor = con.unwrap(org.h2.jdbc.JdbcConnection.class);
            Assertions.assertInstanceOf(org.h2.jdbc.JdbcConnection.class, vendor);

            // and the unwrapped vendor object is the real session, not a
            // look-alike: it answers the database
            try (Statement st = ((Connection) vendor).createStatement();
                    ResultSet rs = st.executeQuery("SELECT 1")) {
                Assertions.assertTrue(rs.next());
                Assertions.assertEquals(1, rs.getInt(1));
            }

            Assertions.assertFalse(con.isWrapperFor(Runnable.class),
                    "no layer of this connection is a Runnable");
            Assertions.assertThrows(SQLException.class,
                    () -> con.unwrap(Runnable.class));
        }
    }

    @Test
    void thePoolDataSourceRecognisesItselfAndTheConfiguredDataSource()
            throws Exception {
        DataSource ds = newDataSource();

        // the handle's own interfaces, whatever else is or is not configured
        Assertions.assertTrue(ds.isWrapperFor(javax.sql.DataSource.class));
        Assertions.assertSame(ds, ds.unwrap(org.apache.tomcat.jdbc.pool.DataSource.class));

        // the configured data source, and the layers that one itself wraps
        Object claimed = new Claimed();
        MarkerDataSource marker = (MarkerDataSource) Proxy.newProxyInstance(
                TestWrapperDelegation.class.getClassLoader(),
                new Class<?>[] { MarkerDataSource.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("isWrapperFor".equals(name)) {
                        Class<?> iface = (Class<?>) args[0];
                        return Boolean.valueOf(iface == UnderlyingDataSource.class
                                || iface.isInstance(proxy));
                    }
                    if ("unwrap".equals(name)) {
                        Class<?> iface = (Class<?>) args[0];
                        if (iface == UnderlyingDataSource.class) {
                            return claimed;
                        }
                        if (iface.isInstance(proxy)) {
                            return proxy;
                        }
                        throw new SQLException("Not a wrapper of " + iface.getName());
                    }
                    throw new UnsupportedOperationException(name);
                });
        DataSource configured = manage(new DataSource());
        configured.setDataSource(marker);

        Assertions.assertTrue(configured.isWrapperFor(MarkerDataSource.class),
                "the configured data source is what the proxy wraps");
        Assertions.assertSame(marker, configured.unwrap(MarkerDataSource.class));
        Assertions.assertTrue(configured.isWrapperFor(UnderlyingDataSource.class),
                "and a layer beneath the configured data source is reached "
                        + "through its own Wrapper answers");
        Assertions.assertSame(claimed, configured.unwrap(UnderlyingDataSource.class));

        Assertions.assertFalse(ds.isWrapperFor(Runnable.class));
        Assertions.assertThrows(SQLException.class, () -> ds.unwrap(Runnable.class),
                "a failed unwrap is the contract's SQLException, not a null");
    }

    /** The marker interface of the driver's presenting layer. */
    public interface WrapperConnection extends Connection {
    }

    /** A data source the pool can be configured with, of its own kind. */
    public interface MarkerDataSource extends javax.sql.DataSource {
    }

    /** Another marker, claimed by {@link #MarkerDataSource} as a layer. */
    public interface UnderlyingDataSource {
    }

    /** What the marker above claims to be underneath itself. */
    private static class Claimed implements UnderlyingDataSource {
    }

    /**
     * Driver that presents every H2 connection it returns as a wrapper of
     * it, answering {@code isWrapperFor()} and {@code unwrap()} about the
     * H2 connection underneath. Configured through {@code driverClassName},
     * so it must stay a public static class with a no-arg constructor.
     */
    public static class WrappingDriver implements Driver {

        private final Driver delegate = new org.h2.Driver();

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            Connection vendor = delegate.connect(url, info);
            if (vendor == null) {
                return null;
            }
            return (Connection) Proxy.newProxyInstance(
                    WrappingDriver.class.getClassLoader(),
                    new Class<?>[] { WrapperConnection.class },
                    new WrappingHandler(vendor));
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

    /*
     * Presents the H2 connection as its own layer plus the vendor below:
     * the Wrapper answers mirror the AWS wrapper's - the presented object
     * is a Connection and a marker, and unwrapping reaches into the vendor.
     */
    private static class WrappingHandler implements InvocationHandler {

        private final Connection vendor;

        WrappingHandler(Connection vendor) {
            this.vendor = vendor;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("isWrapperFor".equals(name)) {
                Class<?> iface = (Class<?>) args[0];
                return Boolean.valueOf(iface.isInstance(vendor) || iface.isInstance(proxy));
            }
            if ("unwrap".equals(name)) {
                Class<?> iface = (Class<?>) args[0];
                if (iface.isInstance(vendor)) {
                    return vendor;
                }
                if (iface.isInstance(proxy)) {
                    return proxy;
                }
                throw new SQLException("Not a wrapper of " + iface.getName());
            }
            try {
                return method.invoke(vendor, args);
            } catch (InvocationTargetException x) {
                throw x.getCause();
            }
        }
    }
}
