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

import java.util.Hashtable;

import javax.naming.Binding;
import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameNotFoundException;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.OperationNotSupportedException;
import javax.naming.Reference;
import javax.naming.StringRefAddr;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.DataSourceFactory;
import org.apache.tomcat.jdbc.pool.PoolProperties;

/**
 * The {@code dataSourceJNDI} attribute is the documented way to let the
 * pool wrap another data source, so a name that cannot be resolved is a
 * configuration error the factory must report when it is asked to create
 * the data source - not a pool that only fails, opaquely, on the first
 * {@code getConnection()}.
 */
class TestDataSourceFactoryJndiLookup extends JdbcPoolBaseTest {

    /** A name that no lookup - local or initial - can resolve. */
    private static final String MISSING_JNDI_NAME = "jdbc/UnboundBackingDS";

    private Reference backingDataSourceReference(String initialSize) {
        Reference ref = new Reference("javax.sql.DataSource", null, null);
        ref.add(new StringRefAddr("dataSourceJNDI", MISSING_JNDI_NAME));
        if (initialSize != null) {
            ref.add(new StringRefAddr("initialSize", initialSize));
        }
        ref.add(new StringRefAddr("jmxEnabled", "false"));
        return ref;
    }

    /*
     * A local context whose lookup fails, as it does for a name that is
     * not bound there. The factory then falls back to the InitialContext,
     * which has no JNDI provider in the test environment and fails too.
     */
    private static Context contextWithoutTheName() {
        return new ContextStub() {
            @Override
            public Object lookup(String name) throws NamingException {
                throw new NameNotFoundException("Not bound in this context: " + name);
            }

            @Override
            public Object lookup(Name name) throws NamingException {
                return lookup(name.toString());
            }
        };
    }

    @Test
    void unresolvableDataSourceJNDIFailsTheFactory() {
        // initialSize="0" removes the eager pre-load that otherwise
        // accidentally trips over the missing data source: pre-fix, this
        // configuration produced a fully "created" DataSource that could
        // never provide a connection.
        Reference ref = backingDataSourceReference("0");

        NamingException e = Assertions.assertThrows(NamingException.class,
                () -> new DataSourceFactory().getObjectInstance(ref,
                        new CompositeName("jdbc/TestDS"),
                        contextWithoutTheName(), null),
                "an unresolvable dataSourceJNDI name must fail the factory call");
        Assertions.assertTrue(e.getMessage().contains(MISSING_JNDI_NAME),
                "the failure must name the unresolvable name, was: " + e.getMessage());
        Assertions.assertTrue(e.getMessage().contains("dataSourceJNDI"),
                "the failure must name the misconfigured attribute, was: "
                        + e.getMessage());
    }

    @Test
    void unresolvableDataSourceJNDIFailsExplicitlyWithDefaultInitialSize() {
        // Default initialSize: pre-fix, the eager pre-load failed, but
        // with a generic driver/URL error that named neither the
        // unresolved name nor the attribute behind it.
        Reference ref = backingDataSourceReference(null);

        NamingException e = Assertions.assertThrows(NamingException.class,
                () -> new DataSourceFactory().getObjectInstance(ref,
                        new CompositeName("jdbc/TestDS"),
                        contextWithoutTheName(), null));
        Assertions.assertTrue(e.getMessage().contains(MISSING_JNDI_NAME),
                "the failure must name the unresolvable name, was: " + e.getMessage());
    }

    @Test
    void unresolvableDataSourceJNDIFailsEvenWithoutALocalContext() {
        // No local context at all: the name still must not be swallowed,
        // whatever the InitialContext of the calling environment does.
        Reference ref = backingDataSourceReference("0");

        NamingException e = Assertions.assertThrows(NamingException.class,
                () -> new DataSourceFactory().getObjectInstance(ref,
                        new CompositeName("jdbc/TestDS"), null, null));
        Assertions.assertTrue(e.getMessage().contains(MISSING_JNDI_NAME),
                "the failure must name the unresolvable name, was: " + e.getMessage());
    }

    @Test
    void directLookupHelperFailsAndCarriesALookupFailure() {
        PoolProperties props = new PoolProperties();
        props.setDataSourceJNDI(MISSING_JNDI_NAME);

        NamingException e = Assertions.assertThrows(NamingException.class,
                () -> new DataSourceFactory().performJNDILookup(
                        contextWithoutTheName(), props));
        Assertions.assertTrue(e.getMessage().contains(MISSING_JNDI_NAME),
                "the failure must name the unresolvable name, was: " + e.getMessage());
        Assertions.assertInstanceOf(NamingException.class, e.getRootCause(),
                "the underlying lookup failure must travel with the error");
    }

    @Test
    void boundDataSourceJNDIProducesAWorkingPool() throws Exception {
        Reference ref = backingDataSourceReference("1");
        org.h2.jdbcx.JdbcDataSource backing = new org.h2.jdbcx.JdbcDataSource();
        backing.setUrl(getUrl());
        backing.setUser("sa");
        backing.setPassword("");
        Context context = new ContextStub() {
            @Override
            public Object lookup(String name) {
                return MISSING_JNDI_NAME.equals(name) ? backing : null;
            }

            @Override
            public Object lookup(Name name) {
                return lookup(name.toString());
            }
        };

        Object result = new DataSourceFactory().getObjectInstance(ref,
                new CompositeName("jdbc/TestDS"), context, null);
        Assertions.assertInstanceOf(DataSource.class, result,
                "a resolvable dataSourceJNDI name must still produce the pool");
        DataSource ds = manage((DataSource) result);
        Assertions.assertSame(backing, ds.getPoolProperties().getDataSource(),
                "the looked up data source must back the pool");

        try (java.sql.Connection c = ds.getConnection();
                java.sql.ResultSet rs = c.createStatement().executeQuery("SELECT 1")) {
            Assertions.assertTrue(rs.next());
        }
        Assertions.assertEquals(1, poolSessions(),
                "the pool must establish connections through the looked up data source");
    }

    /**
     * A JNDI context in which nothing is bound: every operation except
     * {@code lookup} is unsupported, and no {@code lookup} can succeed.
     * Subclasses override the two lookup forms they need.
     */
    private static class ContextStub implements Context {

        @Override
        public Object lookup(Name name) throws NamingException {
            throw new NameNotFoundException("Not bound: " + name);
        }

        @Override
        public Object lookup(String name) throws NamingException {
            throw new NameNotFoundException("Not bound: " + name);
        }

        @Override
        public void bind(Name name, Object obj) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public void bind(String name, Object obj) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public void rebind(Name name, Object obj) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public void rebind(String name, Object obj) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public void unbind(Name name) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public void unbind(String name) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public void rename(Name oldName, Name newName) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public void rename(String oldName, String newName) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public NamingEnumeration<NameClassPair> list(Name name) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public NamingEnumeration<NameClassPair> list(String name) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public NamingEnumeration<Binding> listBindings(Name name) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public NamingEnumeration<Binding> listBindings(String name) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public void destroySubcontext(Name name) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public void destroySubcontext(String name) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public Context createSubcontext(Name name) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public Context createSubcontext(String name) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public Object lookupLink(Name name) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public Object lookupLink(String name) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public NameParser getNameParser(Name name) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public NameParser getNameParser(String name) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public Name composeName(Name name, Name prefix) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public String composeName(String name, String prefix) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public Object addToEnvironment(String propName, Object propVal) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public Object removeFromEnvironment(String propName) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public Hashtable<?, ?> getEnvironment() throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public void close() {
            // no resources held
        }

        @Override
        public String getNameInNamespace() throws NamingException {
            return "";
        }
    }
}
