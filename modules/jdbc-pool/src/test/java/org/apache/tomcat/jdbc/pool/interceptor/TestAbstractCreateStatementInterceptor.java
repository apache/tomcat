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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
 * <p>The class initializer populates the cache with the canonical pairings;
 * the tests below only ever use those pairings and never replace an entry,
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

    /*
     * Regression for the unsynchronized check-then-act on the cache: threads
     * first hitting the same index used to race, each computing its own
     * constructor and handing out a different (though equivalent) object,
     * with no guarantee a reader ever saw a prior write. The canonical
     * entries are now published once by the class initializer, which the JVM
     * runs once and whose writes it makes visible to every thread, so every
     * concurrent caller of one index must receive the very same constructor
     * object.
     *
     * The assertion holds in any method order - a test that ran earlier can
     * only have read these same published entries - and it asserts the
     * outcome the published cache guarantees, never a timing-dependent count
     * of computations, so it is deterministic.
     */
    @Test
    void concurrentCallersOfOneIndexAllReceiveTheIdenticalConstructor() throws Exception {
        int rounds = 32;
        int threadsPerRound = 8;
        Class<?>[] canonical = { Statement.class, PreparedStatement.class, CallableStatement.class, ResultSet.class };

        for (int round = 0; round < rounds; round++) {
            final int idx = round % canonical.length;
            final Class<?> iface = canonical[idx];

            final CountDownLatch start = new CountDownLatch(1);
            final CountDownLatch done = new CountDownLatch(threadsPerRound);
            final Constructor<?>[] results = new Constructor<?>[threadsPerRound];
            final AtomicReference<Throwable> failure = new AtomicReference<>();

            try {
                for (int t = 0; t < threadsPerRound; t++) {
                    final int slot = t;
                    Thread thread = new Thread(() -> {
                        try {
                            start.await();
                            // Distinct interceptor instances: the cache is
                            // shared through the class loader, not the
                            // instance.
                            results[slot] = new CacheUser().getConstructor(idx, iface);
                        } catch (Throwable x) {
                            failure.compareAndSet(null, x);
                        } finally {
                            done.countDown();
                        }
                    });
                    thread.start();
                }
            } finally {
                // Release the threads even if a later start() failed, so a
                // failure cannot leak threads parked on the start latch.
                start.countDown();
            }
            Assertions.assertTrue(done.await(30, TimeUnit.SECONDS), "racing callers did not finish");

            Assertions.assertNull(failure.get(), "racing callers must not fail: " + failure.get());
            for (int t = 0; t < threadsPerRound; t++) {
                Assertions.assertSame(results[0], results[t],
                        "every concurrent caller of index " + idx + " must receive the same constructor");
            }
            Object proxy = results[0].newInstance(new Object[] { (InvocationHandler) (p, m, a) -> null });
            Assertions.assertTrue(iface.isInstance(proxy),
                    "the shared constructor must produce a proxy implementing " + iface.getName());
        }
    }

    /*
     * The canonical entries exist before any call to getConstructor: reading
     * the cache directly, without going through the method, observes what
     * class initialization published. The assertions are stable in any
     * method order - no test can replace a published entry, since every
     * getConstructor call uses the canonical pairing, which only ever reads
     * a populated slot - but the test can only distinguish eager from lazy
     * population when it reads the slots before anything in this JVM has
     * called getConstructor; a cache warmed by an earlier test satisfies
     * the same assertions either way.
     */
    @Test
    void theCanonicalEntriesArePublishedByClassInitialization() throws Exception {
        Class<?>[] canonical = { Statement.class, PreparedStatement.class, CallableStatement.class, ResultSet.class };
        InvocationHandler handler = (proxy, method, args) -> null;

        for (int idx = 0; idx < canonical.length; idx++) {
            Constructor<?> published = AbstractCreateStatementInterceptor.constructors[idx];
            Assertions.assertNotNull(published,
                    "index " + idx + " must hold an entry once the class is initialized");
            Assertions.assertSame(published, new CacheUser().getConstructor(idx, canonical[idx]),
                    "getConstructor must hand out the entry published for index " + idx);
            Assertions.assertTrue(canonical[idx].isInstance(published.newInstance(new Object[] { handler })),
                    "the published constructor must produce a proxy implementing " + canonical[idx].getName());
        }
    }
}
