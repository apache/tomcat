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
package org.apache.tomcat.jdbc.pool.interceptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The contract of the constructor cache that every subclass of
 * {@code AbstractCreateStatementInterceptor} shares in the JVM: an index
 * is a slot for one interface, and every use of that index must pass that
 * interface. Before the guard was added, a subclass that paired an already
 * populated index with a different interface silently got the constructor
 * of the proxy implementing the first caller's interface; the resulting
 * proxy did not implement what the caller asked for and the failure only
 * surfaced later, as a {@code ClassCastException} against the application
 * or as a silently degraded, unproxied statement.
 *
 * <p>The tests below only ever populate an index with the interface every
 * in-tree call site pairs with it, and they leave the cache in that state,
 * so running them in the same JVM as the rest of the suite - and in any
 * method order - is safe.</p>
 */
class TestAbstractCreateStatementInterceptor {

    /* A minimal concrete interceptor; only the cache is exercised. */
    private static class CacheUser extends AbstractCreateStatementInterceptor {

        @Override
        public Object createStatement(Object proxy, Method method, Object[] args, Object statement, long time) {
            return statement;
        }

        @Override
        public void closeInvoked() {
            // NOOP
        }
    }

    /* A proxy built by the constructor the cache hands out. */
    private static Object newProxy(CacheUser user, int index, Class<?> iface) throws Exception {
        Constructor<?> constructor = user.getConstructor(index, iface);
        InvocationHandler handler = (proxy, method, args) -> null;
        return constructor.newInstance(new Object[] { handler });
    }

    @Test
    void theCanonicalPairingsAreCachedAndSharedAcrossInterceptorInstances() throws Exception {
        CacheUser first = new CacheUser();
        CacheUser second = new CacheUser();

        Class<?>[] canonical = { Statement.class, PreparedStatement.class, CallableStatement.class, ResultSet.class };
        for (int idx = 0; idx < canonical.length; idx++) {
            Constructor<?> cached = first.getConstructor(idx, canonical[idx]);
            Assertions.assertSame(cached, second.getConstructor(idx, canonical[idx]),
                    "an interceptor instance must reuse the constructor cached for index " + idx);
            Object proxy = newProxy(second, idx, canonical[idx]);
            Assertions.assertTrue(canonical[idx].isInstance(proxy),
                    "the cached constructor must produce a proxy implementing " + canonical[idx].getName());
        }
    }

    @Test
    void requestingAPopulatedIndexWithADifferentInterfaceFailsLoudly() throws Exception {
        CacheUser interceptor = new CacheUser();
        int idx = AbstractCreateStatementInterceptor.CREATE_STATEMENT_IDX;

        // Populate the index the way every in-tree call site does.
        Constructor<?> cached = interceptor.getConstructor(idx, Statement.class);

        IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class,
                () -> interceptor.getConstructor(idx, PreparedStatement.class),
                "a mismatched interface for a populated index must not return the cached constructor");

        // The message must name the index, the cached interface and the
        // requested one, so the offending pairing is identifiable.
        Assertions.assertTrue(failure.getMessage().contains("index " + idx),
                "the message must name the index: " + failure.getMessage());
        Assertions.assertTrue(failure.getMessage().contains("java.sql.Statement"),
                "the message must name the cached interface: " + failure.getMessage());
        Assertions.assertTrue(failure.getMessage().contains("java.sql.PreparedStatement"),
                "the message must name the requested interface: " + failure.getMessage());

        // The rejection must leave the cached entry untouched, so a caller
        // with the correct pairing is unaffected.
        Assertions.assertSame(cached, AbstractCreateStatementInterceptor.constructors[idx]);
        Assertions.assertSame(cached, interceptor.getConstructor(idx, Statement.class));
    }

    @Test
    void aRequestForAnInterfaceTheCachedProxyAlsoImplementsIsAccepted() throws Exception {
        CacheUser interceptor = new CacheUser();
        int idx = AbstractCreateStatementInterceptor.PREPARE_STATEMENT_IDX;

        // Populate the index with the canonical interface first.
        Constructor<?> cached = interceptor.getConstructor(idx, PreparedStatement.class);

        // A PreparedStatement proxy is also a Statement, so widening the
        // request to a superinterface of the cached interface is safe and
        // must be accepted.
        Assertions.assertSame(cached, interceptor.getConstructor(idx, Statement.class));
        Assertions.assertTrue(newProxy(interceptor, idx, Statement.class) instanceof Statement);
    }
}
