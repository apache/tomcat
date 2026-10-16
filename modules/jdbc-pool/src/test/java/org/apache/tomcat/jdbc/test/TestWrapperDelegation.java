/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.jdbc.test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.jdbc.test.driver.Driver;

/*
 * https://bz.apache.org/bugzilla/show_bug.cgi?id=59569
 *
 * Some drivers (e.g. the AWS advanced JDBC wrapper around the PostgreSQL
 * driver) return a connection that is itself a wrapper around the vendor
 * connection. Unwrapping the pooled connection should reach through that
 * extra layer.
 */
public class TestWrapperDelegation extends DefaultTestCase {

    @Override
    public org.apache.tomcat.jdbc.pool.DataSource createDefaultDataSource() {
        org.apache.tomcat.jdbc.pool.DataSource ds = super.createDefaultDataSource();
        ds.getPoolProperties().setDriverClassName(WrappingDriver.class.getName());
        ds.getPoolProperties().setUrl(Driver.url);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        return ds;
    }

    @Test
    public void testUnwrapDirect() throws Exception {
        try (Connection con = datasource.getConnection()) {
            Assert.assertTrue(con.isWrapperFor(WrapperConnection.class));
            Assert.assertNotNull(con.unwrap(WrapperConnection.class));
        }
    }

    @Test
    public void testUnwrapNested() throws Exception {
        try (Connection con = datasource.getConnection()) {
            Assert.assertTrue(con.isWrapperFor(org.apache.tomcat.jdbc.test.driver.Connection.class));
            Object vendor = con.unwrap(org.apache.tomcat.jdbc.test.driver.Connection.class);
            Assert.assertTrue(vendor instanceof org.apache.tomcat.jdbc.test.driver.Connection);
        }
    }

    @Test
    public void testIsWrapperForUnrelated() throws Exception {
        try (Connection con = datasource.getConnection()) {
            Assert.assertFalse(con.isWrapperFor(Runnable.class));
        }
    }

    @Test(expected = SQLException.class)
    public void testUnwrapUnrelated() throws Exception {
        try (Connection con = datasource.getConnection()) {
            con.unwrap(Runnable.class);
        }
    }


    @Test
    public void testDataSourceUnwrapSelf() throws Exception {
        // e.g. Spring's DelegatingDataSource delegates these calls to the pool
        Assert.assertTrue(datasource.isWrapperFor(org.apache.tomcat.jdbc.pool.DataSource.class));
        Assert.assertTrue(datasource.isWrapperFor(javax.sql.DataSource.class));
        Assert.assertSame(datasource, datasource.unwrap(org.apache.tomcat.jdbc.pool.DataSource.class));
        Assert.assertSame(datasource, datasource.unwrap(javax.sql.DataSource.class));
    }

    @Test
    public void testDataSourceIsWrapperForUnrelated() throws Exception {
        Assert.assertFalse(datasource.isWrapperFor(Runnable.class));
    }

    @Test(expected = SQLException.class)
    public void testDataSourceUnwrapUnrelated() throws Exception {
        datasource.unwrap(Runnable.class);
    }

    @Test
    public void testDataSourceUnwrapConfiguredDataSource() throws Exception {
        Driver vendor = new Driver();
        WrapperDataSource wrapped = (WrapperDataSource) Proxy.newProxyInstance(
                WrapperDataSource.class.getClassLoader(), new Class<?>[] { WrapperDataSource.class },
                new WrappingHandler(vendor));
        org.apache.tomcat.jdbc.pool.DataSource ds = new org.apache.tomcat.jdbc.pool.DataSource();
        ds.setDataSource(wrapped);

        Assert.assertTrue(ds.isWrapperFor(WrapperDataSource.class));
        Assert.assertSame(wrapped, ds.unwrap(WrapperDataSource.class));
        Assert.assertTrue(ds.isWrapperFor(Driver.class));
        Assert.assertSame(vendor, ds.unwrap(Driver.class));
        Assert.assertFalse(ds.isWrapperFor(Runnable.class));
    }


    /**
     * Marker interface for the intermediate wrapper layer.
     */
    public interface WrapperConnection extends Connection {
    }


    /**
     * Marker interface for a wrapping data source.
     */
    public interface WrapperDataSource extends javax.sql.DataSource {
    }


    public static class WrappingDriver extends Driver {

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            Connection vendor = super.connect(url, info);
            return (Connection) Proxy.newProxyInstance(WrappingDriver.class.getClassLoader(),
                    new Class<?>[] { WrapperConnection.class }, new WrappingHandler(vendor));
        }
    }


    private static class WrappingHandler implements InvocationHandler {

        private final Object vendor;

        WrappingHandler(Object vendor) {
            this.vendor = vendor;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("isWrapperFor".equals(method.getName())) {
                Class<?> iface = (Class<?>) args[0];
                return Boolean.valueOf(iface.isInstance(vendor));
            } else if ("unwrap".equals(method.getName())) {
                Class<?> iface = (Class<?>) args[0];
                if (iface.isInstance(vendor)) {
                    return vendor;
                }
                throw new SQLException("Not a wrapper of " + iface.getName());
            }
            try {
                return method.invoke(vendor, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
