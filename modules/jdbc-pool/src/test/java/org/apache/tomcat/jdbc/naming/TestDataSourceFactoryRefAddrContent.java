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
package org.apache.tomcat.jdbc.naming;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Properties;

import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.StringRefAddr;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.DataSourceFactory;
import org.apache.tomcat.jdbc.pool.PoolProperties;

/**
 * {@code getObjectInstance} reads the property values out of the
 * {@link RefAddr}s of the {@link Reference}. The {@code RefAddr}
 * contract makes {@code getContent()} nullable and {@code Object}-
 * valued, so the factory loop must survive a null content instead of
 * stringifying blindly, and a {@code dataSource} attribute carrying an
 * actual {@code javax.sql.DataSource} object must reach the pool as
 * that object rather than as its {@code toString()}.
 */
class TestDataSourceFactoryRefAddrContent extends JdbcPoolBaseTest {

    private Reference poolReference() {
        Reference ref = new Reference("javax.sql.DataSource", null, null);
        ref.add(new StringRefAddr("jmxEnabled", "false"));
        ref.add(new StringRefAddr("initialSize", "1"));
        ref.add(new StringRefAddr("minIdle", "0"));
        return ref;
    }

    private DataSource managedPool(Object result) {
        Assertions.assertInstanceOf(DataSource.class, result,
                "the factory must return the pool");
        return manage((DataSource) result);
    }

    private void assertPoolConnects(DataSource ds) throws Exception {
        try (Connection c = ds.getConnection();
                ResultSet rs = c.createStatement().executeQuery("SELECT 1")) {
            Assertions.assertTrue(rs.next());
        }
    }

    @Test
    void nullContentRefAddrIsSkippedAndThePoolStillComesUp() throws Exception {
        // RefAddr.getContent() is contractually nullable - a StringRefAddr
        // can be created with null content. Pre-fix, the toString() call on
        // the content aborted the whole factory call with a bare NPE.
        Reference ref = poolReference();
        ref.add(new StringRefAddr("driverClassName", "org.h2.Driver"));
        ref.add(new StringRefAddr("url", getUrl()));
        ref.add(new StringRefAddr("username", "sa"));
        ref.add(new StringRefAddr("password", ""));
        ref.add(new StringRefAddr("maxActive", null));

        DataSource ds = managedPool(new DataSourceFactory().getObjectInstance(
                ref, new CompositeName("jdbc/TestDS"), null, null));

        // The empty attribute is skipped, not applied as anything: maxActive
        // stays at its untouched default.
        Assertions.assertEquals(PoolProperties.DEFAULT_MAX_ACTIVE,
                ds.getPoolProperties().getMaxActive(),
                "a RefAddr with null content must not set the property");
        // The other RefAddrs must still have been applied, and the pool must
        // really work.
        Assertions.assertEquals("sa", ds.getPoolProperties().getUsername());
        assertPoolConnects(ds);
        Assertions.assertEquals(1, poolSessions(),
                "the pool must establish the configured initial connection");
    }

    @Test
    void objectValuedDataSourceRefAddrBacksThePool() throws Exception {
        // A Reference can carry a live object under an attribute. Pre-fix,
        // the content was stringified ("org.h2.jdbcx.JdbcDataSource@...")
        // and then rejected as "not a javax.sql.DataSource object" although
        // it was exactly that object.
        Reference ref = poolReference();
        org.h2.jdbcx.JdbcDataSource backing = new org.h2.jdbcx.JdbcDataSource();
        backing.setUrl(getUrl());
        backing.setUser("sa");
        backing.setPassword("");
        ref.add(new ObjectContentRefAddr("dataSource", backing));

        DataSource ds = managedPool(new DataSourceFactory().getObjectInstance(
                ref, new CompositeName("jdbc/TestDS"), null, null));

        Assertions.assertSame(backing, ds.getPoolProperties().getDataSource(),
                "the object carried by the dataSource RefAddr must back the pool");
        assertPoolConnects(ds);
        Assertions.assertEquals(1, poolSessions(),
                "the pool must establish connections through that data source");
    }

    @Test
    void objectValuedDataSourceRefAddrTakesPrecedenceOverDataSourceJNDI()
            throws Exception {
        // Both ways of giving the pool a backing data source are present.
        // The object handed through the Reference is the concrete one and
        // must win; pre-fix this configuration died on the stringified
        // dataSource attribute before any of that could be decided.
        Reference ref = poolReference();
        org.h2.jdbcx.JdbcDataSource handed = new org.h2.jdbcx.JdbcDataSource();
        handed.setUrl(getUrl());
        handed.setUser("sa");
        handed.setPassword("");
        org.h2.jdbcx.JdbcDataSource bound = new org.h2.jdbcx.JdbcDataSource();
        bound.setUrl(getUrl());
        bound.setUser("sa");
        bound.setPassword("");
        ref.add(new ObjectContentRefAddr("dataSource", handed));
        ref.add(new StringRefAddr("dataSourceJNDI", "jdbc/BoundBacking"));

        DataSource ds = managedPool(new DataSourceFactory().getObjectInstance(
                ref, new CompositeName("jdbc/TestDS"),
                contextReturning(bound), null));

        Assertions.assertSame(handed, ds.getPoolProperties().getDataSource(),
                "the object-valued dataSource attribute must win over dataSourceJNDI");
        assertPoolConnects(ds);
    }

    @Test
    void stringValuedDataSourceRefAddrIsRejectedWithAnActionableMessage() {
        // A genuinely string-valued dataSource attribute still cannot become
        // a data source, but the rejection must say what to do instead of
        // contradicting the object-valued path. Pre-fix the message did not
        // name the supported alternatives.
        Reference ref = poolReference();
        ref.add(new StringRefAddr("dataSource", "com.example.NotADataSource"));

        IllegalArgumentException e = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new DataSourceFactory().getObjectInstance(ref,
                        new CompositeName("jdbc/TestDS"), null, null));
        Assertions.assertTrue(e.getMessage().contains("dataSourceJNDI"),
                "the rejection must point at the dataSourceJNDI attribute, was: "
                        + e.getMessage());
        Assertions.assertTrue(e.getMessage().contains("com.example.NotADataSource"),
                "the rejection must name the offending value, was: " + e.getMessage());
    }

    @Test
    void nullContentDataSourceRefAddrIsSimplySkipped() throws Exception {
        // The null-content skip must run before the object-valued special
        // case, so a content-less attribute on the "dataSource" name is just
        // an unset property - not a rejection.
        Reference ref = poolReference();
        ref.add(new StringRefAddr("driverClassName", "org.h2.Driver"));
        ref.add(new StringRefAddr("url", getUrl()));
        ref.add(new StringRefAddr("username", "sa"));
        ref.add(new StringRefAddr("password", ""));
        ref.add(new StringRefAddr("dataSource", null));

        DataSource ds = managedPool(new DataSourceFactory().getObjectInstance(
                ref, new CompositeName("jdbc/TestDS"), null, null));

        Assertions.assertNull(ds.getPoolProperties().getDataSource(),
                "a null-content dataSource attribute must leave the property unset");
        assertPoolConnects(ds);
    }

    @Test
    void plainReferenceStillDispatchesToTheThreeArgumentCreateDataSource()
            throws Exception {
        // Subclasses customising the public three argument createDataSource
        // hook must stay in the loop for ordinary references; only a live
        // object-valued dataSource attribute takes the four argument route.
        class DispatchingFactory extends DataSourceFactory {
            private boolean threeArgCalled;

            @Override
            public javax.sql.DataSource createDataSource(Properties properties,
                    Context context, boolean xa) throws Exception {
                threeArgCalled = true;
                return super.createDataSource(properties, context, xa);
            }
        }
        DispatchingFactory factory = new DispatchingFactory();

        Reference ref = poolReference();
        ref.add(new StringRefAddr("driverClassName", "org.h2.Driver"));
        ref.add(new StringRefAddr("url", getUrl()));
        ref.add(new StringRefAddr("username", "sa"));
        ref.add(new StringRefAddr("password", ""));

        DataSource ds = managedPool(factory.getObjectInstance(ref,
                new CompositeName("jdbc/TestDS"), null, null));

        Assertions.assertTrue(factory.threeArgCalled,
                "a plain reference must go through the three argument hook");
        assertPoolConnects(ds);
    }

    @Test
    void objectValuedReferenceUsesTheFourArgumentCreateDataSource()
            throws Exception {
        // The flip side: only a live data source object diverts from the
        // legacy hook.
        class DispatchingFactory extends DataSourceFactory {
            private boolean threeArgCalled;

            @Override
            public javax.sql.DataSource createDataSource(Properties properties,
                    Context context, boolean xa) throws Exception {
                threeArgCalled = true;
                return super.createDataSource(properties, context, xa);
            }
        }
        DispatchingFactory factory = new DispatchingFactory();

        Reference ref = poolReference();
        org.h2.jdbcx.JdbcDataSource backing = new org.h2.jdbcx.JdbcDataSource();
        backing.setUrl(getUrl());
        backing.setUser("sa");
        backing.setPassword("");
        ref.add(new ObjectContentRefAddr("dataSource", backing));

        DataSource ds = managedPool(factory.getObjectInstance(ref,
                new CompositeName("jdbc/TestDS"), null, null));

        Assertions.assertFalse(factory.threeArgCalled,
                "an object-valued dataSource must use the four argument route");
        Assertions.assertSame(backing, ds.getPoolProperties().getDataSource());
        assertPoolConnects(ds);
    }

    /*
     * A RefAddr carrying an arbitrary live object as its content, as the
     * RefAddr contract allows (getContent() returns Object).
     */
    private static class ObjectContentRefAddr extends RefAddr {
        private static final long serialVersionUID = 1L;
        private final Object content;

        ObjectContentRefAddr(String type, Object content) {
            super(type);
            this.content = content;
        }

        @Override
        public Object getContent() {
            return content;
        }
    }

    /*
     * A JNDI context whose lookup returns the given object for any name,
     * standing in for a bound data source.
     */
    private static Context contextReturning(final Object bound) {
        return (Context) Proxy.newProxyInstance(
                TestDataSourceFactoryRefAddrContent.class.getClassLoader(),
                new Class<?>[] { Context.class },
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, java.lang.reflect.Method method,
                            Object[] args) {
                        switch (method.getName()) {
                            case "lookup":
                                return bound;
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            case "equals":
                                return proxy == args[0];
                            case "toString":
                                return "ContextStub";
                            case "close":
                                return null;
                            default:
                                throw new UnsupportedOperationException(method.getName());
                        }
                    }
                });
    }
}
