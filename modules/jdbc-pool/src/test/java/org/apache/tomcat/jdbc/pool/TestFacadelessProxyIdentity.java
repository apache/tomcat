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
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The pool's own {@code equals}/{@code hashCode} handling on the handle it
 * hands out, below the {@link DisposableConnectionFacade}. With the facade
 * enabled (the default) the facade answers these calls and
 * {@link ProxyConnection} never sees them; with the facade switched off the
 * calls reach {@code ProxyConnection}, which used to forward them down the
 * XA dispatch branch - on a plain pool that resolves the XA connection to
 * {@code null} and the call threw, and on an XA pool it compared against
 * the underlying {@code XAConnection} instead of the handle. Any
 * {@code HashSet<Connection>} of pooled connections broke, and even after
 * {@code close()} the contract still has to hold, so the calls must stay
 * answerable on a released handle too.
 *
 * <p>Identity semantics are pinned per borrow, matching the facade-on
 * behaviour asserted by {@link TestProxyContract}: a handle is equal only
 * to itself, even when a later borrow maps to the same physical session.</p>
 */
class TestFacadelessProxyIdentity extends JdbcPoolBaseTest {

    private DataSource newDataSourceWithoutFacade() {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setUseDisposableConnectionFacade(false);
        return ds;
    }

    @Test
    void aLiveHandleHonoursEqualsHashCodeContract() throws Exception {
        DataSource ds = newDataSourceWithoutFacade();
        ds.createPool();

        Connection pooled = ds.getConnection();
        // Pre-fix these threw because the Object methods fell into the XA
        // dispatch branch with a null XA connection.
        Assertions.assertTrue(pooled.equals(pooled), "a handle must be equal to itself");
        Assertions.assertFalse(pooled.equals(null),
                "Object.equals(null) must answer false, not throw");
        Assertions.assertFalse(pooled.equals("a foreign object"),
                "equals against a non-proxy must answer false, not throw");
        Object foreignProxy = java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] { Runnable.class },
                (p, m, a) -> null);
        Assertions.assertFalse(pooled.equals(foreignProxy),
                "equals against a foreign dynamic proxy must answer false");
        int hash = pooled.hashCode();
        Assertions.assertEquals(hash, pooled.hashCode(), "hashCode must be stable");
        pooled.close();
    }

    @Test
    void aReleasedHandleKeepsTheContract() throws Exception {
        DataSource ds = newDataSourceWithoutFacade();
        ds.createPool();

        Connection pooled = ds.getConnection();
        Assertions.assertTrue(pooled.equals(pooled));
        int hash = pooled.hashCode();
        pooled.close();

        // hashCode/equals are identity, not liveness - a released handle
        // still has to answer them (pre-fix: UndeclaredThrowableException
        // caused by the "Connection has already been closed" SQLException).
        Assertions.assertEquals(hash, pooled.hashCode(),
                "closing must not change the hash code of a handle");
        Assertions.assertTrue(pooled.equals(pooled),
                "closing must not change the identity of a handle");
        Assertions.assertFalse(pooled.equals(null),
                "a released handle must keep the equals(null) contract");
    }

    @Test
    void twoBorrowsOverOneSessionAreDistinctHandles() throws Exception {
        DataSource ds = newDataSourceWithoutFacade();
        ds.createPool();

        Connection first = ds.getConnection();
        setSessionMarker(first, 42);
        Assertions.assertTrue(first.equals(first));
        int firstHash = first.hashCode();
        first.close();

        try (Connection second = ds.getConnection()) {
            Assertions.assertEquals(Integer.valueOf(42), sessionMarker(second),
                    "the test must actually be looking at one reused physical session");
            Assertions.assertFalse(first.equals(second),
                    "two borrows are two handles even over one physical session");
            Assertions.assertFalse(second.equals(first));
            Assertions.assertNotEquals(firstHash, second.hashCode(),
                    "two handles must not share an identity hash");
        }
    }

    @Test
    void pooledConnectionsUsableAsHashCollectionKeys() throws Exception {
        DataSource ds = newDataSourceWithoutFacade();
        ds.createPool();

        Set<Connection> seen = new HashSet<>();
        Connection a = ds.getConnection();
        Connection b = ds.getConnection();
        Assertions.assertTrue(seen.add(a), "first insert must succeed");
        Assertions.assertTrue(seen.add(b), "second insert must succeed");
        Assertions.assertFalse(seen.add(a), "a handle already in the set must not re-insert");
        Assertions.assertTrue(seen.contains(a), "collection lookup must find the handle");
        Assertions.assertTrue(seen.contains(b));
        a.close();
        b.close();
        Assertions.assertTrue(seen.contains(a),
                "a released handle must still hash to its bucket");
    }

    @Test
    void anXaCapableHandleKeepsIdentityForItself() throws Exception {
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
        ds.getPoolProperties().setUseDisposableConnectionFacade(false);
        manage(ds);
        ds.createPool();

        Connection first = ds.getConnection();
        // Pre-fix the calls were dispatched at the live XAConnection:
        // a handle was not equal to itself (the XA connection was asked
        // to compare itself against the proxy) and two handles over one
        // session shared the XA connection's hash.
        Assertions.assertTrue(first.equals(first),
                "an XA-capable handle must be equal to itself");
        Assertions.assertFalse(first.equals(null));
        int firstHash = first.hashCode();
        Assertions.assertEquals(firstHash, first.hashCode(), "hashCode must be stable");
        first.close();

        try (Connection second = ds.getConnection()) {
            Assertions.assertTrue(second.equals(second));
            Assertions.assertFalse(first.equals(second),
                    "two borrows are two handles even over one physical session");
            Assertions.assertNotEquals(firstHash, second.hashCode(),
                    "two handles must not share the XA connection's hash");
        }
    }
}
