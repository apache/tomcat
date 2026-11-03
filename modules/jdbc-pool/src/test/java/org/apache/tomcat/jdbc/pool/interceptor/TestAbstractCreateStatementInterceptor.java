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
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.pool.JdbcInterceptor;

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
 * the tests below use only those pairings, and the one test that simulates a
 * slot the initializer failed to populate restores the published entry in a
 * finally block, so running them in the same JVM as the rest of the suite -
 * and in any method order - is safe.</p>
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
        Assertions.assertSame(cached, AbstractCreateStatementInterceptor.cachedConstructor(idx));
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
            Constructor<?> published = AbstractCreateStatementInterceptor.cachedConstructor(idx);
            Assertions.assertNotNull(published,
                    "index " + idx + " must hold an entry once the class is initialized");
            Assertions.assertSame(published, new CacheUser().getConstructor(idx, canonical[idx]),
                    "getConstructor must hand out the entry published for index " + idx);
            Assertions.assertTrue(canonical[idx].isInstance(published.newInstance(new Object[] { handler })),
                    "the published constructor must produce a proxy implementing " + canonical[idx].getName());
        }
    }

    /*
     * A slot the class initializer failed to populate must still yield a
     * working constructor, and the method must not write the shared slot from
     * the calling thread: the class initializer is the only writer of the
     * cache, which is what makes every read safely published without a lock.
     * Before the fix, a degraded slot was lazily written without any
     * happens-before edge - a data race against the concurrent readers of the
     * other entries.
     *
     * The test replaces a canonical slot, so it restores the exact published
     * reference in a finally block; the suite runs methods sequentially in a
     * single thread (no Jupiter parallel execution is configured), which is
     * what makes the temporary nulling safe.
     */
    @Test
    void anEmptySlotIsServedWithoutWritingTheCache() throws Exception {
        CacheUser interceptor = new CacheUser();
        int idx = AbstractCreateStatementInterceptor.CREATE_STATEMENT_IDX;
        Constructor<?> published = AbstractCreateStatementInterceptor.cachedConstructor(idx);

        AbstractCreateStatementInterceptor.replaceCachedConstructor(idx, null);
        try {
            Constructor<?> first = interceptor.getConstructor(idx, Statement.class);
            Constructor<?> second = interceptor.getConstructor(idx, Statement.class);

            Assertions.assertNull(AbstractCreateStatementInterceptor.cachedConstructor(idx),
                    "a degraded slot must not be written by the thread that served it");
            Assertions.assertTrue(first.getDeclaringClass().isInstance(
                            first.newInstance(new Object[] { (InvocationHandler) (p, m, a) -> null })),
                    "a constructor computed on demand must still produce the proxy");
            Assertions.assertEquals(first, second,
                    "the constructor computed on demand must be equivalent for the same request");
        } finally {
            AbstractCreateStatementInterceptor.replaceCachedConstructor(idx, published);
        }

        // The published entry is untouched by the degraded calls.
        Assertions.assertSame(published, interceptor.getConstructor(idx, Statement.class));
    }

    /* A hook that fails, as a third-party subclass or an Error can. */
    private static class ThrowingHook extends AbstractCreateStatementInterceptor {

        private final RuntimeException failure;

        ThrowingHook(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public Object createStatement(Object proxy, Method method, Object[] args, Object statement, long time) {
            throw failure;
        }

        @Override
        public void closeInvoked() {
            // NOOP
        }
    }

    /* A hook whose closeInvoked() does whatever the test handed it. */
    private static class CloseHook extends AbstractCreateStatementInterceptor {

        private final Runnable behaviour;
        final AtomicBoolean ran = new AtomicBoolean();

        CloseHook(Runnable behaviour) {
            this.behaviour = behaviour;
        }

        @Override
        public Object createStatement(Object proxy, Method method, Object[] args, Object statement, long time) {
            return statement;
        }

        @Override
        public void closeInvoked() {
            ran.set(true);
            behaviour.run();
        }
    }

    /* The link a hand-built chain ends in, recording whether the call arrived. */
    private static JdbcInterceptor terminal(AtomicBoolean reached) {
        return new JdbcInterceptor() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                reached.set(true);
                return null;
            }

            @Override
            public void reset(org.apache.tomcat.jdbc.pool.ConnectionPool parent,
                    org.apache.tomcat.jdbc.pool.PooledConnection con) {
                // NOOP
            }
        };
    }

    /* A hook that records the elapsed time handed to it. */
    private static class TimingHook extends AbstractCreateStatementInterceptor {

        final AtomicLong timeSeen = new AtomicLong(Long.MIN_VALUE);

        @Override
        public Object createStatement(Object proxy, Method method, Object[] args, Object statement, long time) {
            timeSeen.set(time);
            return statement;
        }

        @Override
        public void closeInvoked() {
            // NOOP
        }
    }

    /* A statement stand-in that records whether it was closed. */
    private static Statement recordingStatement(AtomicBoolean closed, RuntimeException closeFailure) {
        return (Statement) Proxy.newProxyInstance(
                TestAbstractCreateStatementInterceptor.class.getClassLoader(),
                new Class<?>[] { Statement.class },
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        closed.set(true);
                        if (closeFailure != null) {
                            throw closeFailure;
                        }
                    }
                    return null;
                });
    }

    /* A chain of one interceptor over a terminal that hands out the statement. */
    private static void chainOver(AbstractCreateStatementInterceptor interceptor, Statement real) {
        interceptor.setNext(new JdbcInterceptor() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                return real;
            }

            @Override
            public void reset(org.apache.tomcat.jdbc.pool.ConnectionPool parent,
                    org.apache.tomcat.jdbc.pool.PooledConnection con) {
                // NOOP
            }
        });
    }

    /*
     * Regression for the orphaned statement: when the createStatement hook
     * throws, the application never receives the statement, so the base must
     * not leave it open and unreachable on the pooled connection. Before the
     * fix the throwable propagated and the just-created statement stayed open
     * for the life of the physical connection.
     */
    @Test
    void aFailingCreateHookClosesTheStatementTheApplicationNeverReceives() throws Throwable {
        AtomicBoolean closed = new AtomicBoolean();
        Statement real = recordingStatement(closed, null);
        RuntimeException hookFailure = new RuntimeException("hook-failed");

        ThrowingHook interceptor = new ThrowingHook(hookFailure);
        chainOver(interceptor, real);

        Method create = java.sql.Connection.class.getMethod("createStatement");
        RuntimeException thrown = Assertions.assertThrows(RuntimeException.class,
                () -> interceptor.invoke(null, create, null),
                "the hook failure must still reach the application");

        Assertions.assertSame(hookFailure, thrown, "the original throwable must propagate unchanged");
        Assertions.assertTrue(closed.get(),
                "the statement the application never received must not be orphaned");
    }

    /*
     * A failing close of the orphaned statement must not mask the hook's
     * failure: the original throwable is still the one the application sees,
     * with the close failure suppressed.
     */
    @Test
    void aCloseFailureOfTheOrphanedStatementIsSuppressedNotThrown() throws Throwable {
        AtomicBoolean closed = new AtomicBoolean();
        RuntimeException closeFailure = new RuntimeException("close-failed");
        Statement real = recordingStatement(closed, closeFailure);
        RuntimeException hookFailure = new RuntimeException("hook-failed");

        ThrowingHook interceptor = new ThrowingHook(hookFailure);
        chainOver(interceptor, real);

        Method create = java.sql.Connection.class.getMethod("createStatement");
        RuntimeException thrown = Assertions.assertThrows(RuntimeException.class,
                () -> interceptor.invoke(null, create, null));

        Assertions.assertSame(hookFailure, thrown);
        Assertions.assertTrue(closed.get());
        Assertions.assertArrayEquals(new Throwable[] { closeFailure }, thrown.getSuppressed(),
                "the close failure must be attached to the exception the application receives");
    }

    /*
     * Regression for the unguarded close fan-out: a throwing closeInvoked()
     * used to short-circuit the dispatch before the close reached the chain
     * terminal, so the session was never returned to the pool - and, with the
     * handle already disposed, the application could not even retry the
     * close. A hook failure must instead be isolated: logged, the close still
     * travels the rest of the chain, and the borrow ends.
     */
    @Test
    void aFailingCloseHookStillEndsTheBorrow() throws Throwable {
        AtomicBoolean terminalReached = new AtomicBoolean();
        CloseHook throwing = new CloseHook(() -> {
            throw new IllegalStateException("close-hook-failed");
        });
        throwing.setNext(terminal(terminalReached));

        Method close = java.sql.Connection.class.getMethod("close");
        throwing.invoke(null, close, null);

        Assertions.assertTrue(throwing.ran.get(), "the hook must have been attempted");
        Assertions.assertTrue(terminalReached.get(),
                "the close must still reach the terminal so the borrow ends");
    }

    /*
     * Isolation is per link: a throwing hook may not stop the hooks below it
     * from receiving the close, just as the reset and disconnected fan-outs
     * isolate each interceptor individually.
     */
    @Test
    void aFailingCloseHookDoesNotStopTheHooksBelowIt() throws Throwable {
        AtomicBoolean terminalReached = new AtomicBoolean();
        CloseHook throwing = new CloseHook(() -> {
            throw new IllegalStateException("close-hook-failed");
        });
        CloseHook inner = new CloseHook(() -> {
        });
        throwing.setNext(inner);
        inner.setNext(terminal(terminalReached));

        Method close = java.sql.Connection.class.getMethod("close");
        throwing.invoke(null, close, null);

        Assertions.assertTrue(throwing.ran.get(), "the throwing hook ran and failed");
        Assertions.assertTrue(inner.ran.get(), "the hook beneath the thrower must still receive the close");
        Assertions.assertTrue(terminalReached.get(), "the close must still reach the terminal");
    }

    /*
     * A StackOverflowError is the recoverable Error, and TrapException does
     * not convert Errors, so the isolation must catch it; a
     * VirtualMachineError must instead propagate, per the policy
     * ExceptionUtils.handleThrowable enforces across the module.
     */
    @Test
    void anErrorThrownByTheCloseHookIsIsolatedToo() throws Throwable {
        AtomicBoolean terminalReached = new AtomicBoolean();
        CloseHook throwing = new CloseHook(() -> {
            throw new StackOverflowError("close-hook-failed");
        });
        throwing.setNext(terminal(terminalReached));

        Method close = java.sql.Connection.class.getMethod("close");
        throwing.invoke(null, close, null);

        Assertions.assertTrue(terminalReached.get(),
                "even a recoverable Error from the hook must not strand the session as busy");
    }

    @Test
    void aFatalErrorThrownByTheCloseHookPropagates() throws Exception {
        AtomicBoolean terminalReached = new AtomicBoolean();
        InternalError fatal = new InternalError("close-hook-fatal");
        CloseHook throwing = new CloseHook(() -> {
            throw fatal;
        });
        throwing.setNext(terminal(terminalReached));

        Method close = java.sql.Connection.class.getMethod("close");
        InternalError thrown = Assertions.assertThrows(InternalError.class,
                () -> throwing.invoke(null, close, null),
                "the fatal errors the pool never swallows must propagate from the close dispatch");
        Assertions.assertSame(fatal, thrown);
    }

    /*
     * The time handed to the hook is booked as a duration (prepare totals and
     * the averages derived from them), and measuring it on the wall clock let
     * a clock step between the two readings book a negative or wildly large
     * duration. Pin the contract: milliseconds, and from the monotonic clock
     * the never-negative guarantee comes from.
     */
    @Test
    void theElapsedTimeBookedByTheHookIsAMonotonicMillisecondDuration() throws Throwable {
        TimingHook hook = new TimingHook();
        hook.setNext(new JdbcInterceptor() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws InterruptedException {
                Thread.sleep(25);
                return null;
            }

            @Override
            public void reset(org.apache.tomcat.jdbc.pool.ConnectionPool parent,
                    org.apache.tomcat.jdbc.pool.PooledConnection con) {
                // NOOP
            }
        });

        Method prepare = java.sql.Connection.class.getMethod("prepareStatement", String.class);
        hook.invoke(null, prepare, new Object[] { "SELECT 1" });

        Assertions.assertTrue(hook.timeSeen.get() >= 20,
                "the hook must observe the creation duration in milliseconds, saw " + hook.timeSeen.get());
        Assertions.assertTrue(AbstractCreateStatementInterceptor.elapsedMillis(System.nanoTime()) >= 0,
                "a duration measured with the monotonic clock cannot come out negative");
    }
}
