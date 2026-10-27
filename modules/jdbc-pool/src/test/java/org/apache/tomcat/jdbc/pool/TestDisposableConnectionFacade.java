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

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The disposable facade must sever its link to the interceptor chain
 * <em>before</em> the delegated close hands the physical connection back to
 * the pool. Sealing in a finally after the delegation returns leaves the
 * window between {@code pool.returnConnection()} and {@code setNext(null)}
 * in which new calls still pass the disposed guard and reach the shared
 * chain - which a new borrower may already have re-armed through
 * {@code reset()} - so the call executes against the next borrower's
 * connection, and a concurrent second close can drive the return a second
 * time.
 */
class TestDisposableConnectionFacade {

    private static final long AWAIT_MS = 15000;

    /* Stands in for the cached per-connection chain below the facade. Its
       close() marks the connection as returned to the pool and then parks,
       reproducing the window in which, in the real chain, the connection
       already sits in the idle queue while the facade is not yet sealed. */
    private static final class StubChain extends JdbcInterceptor {

        final CountDownLatch closeEntered = new CountDownLatch(1);
        final CountDownLatch closeRelease = new CountDownLatch(1);
        final AtomicInteger closeCount = new AtomicInteger();
        final CopyOnWriteArrayList<String> callsAfterReturn = new CopyOnWriteArrayList<>();
        volatile boolean returnedToPool;

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // NOOP
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("close".equals(name)) {
                closeCount.incrementAndGet();
                // ProxyConnection performs pool.returnConnection() at this
                // exact point in the delegation
                returnedToPool = true;
                closeEntered.countDown();
                closeRelease.await();
                return null;
            }
            if (returnedToPool) {
                callsAfterReturn.add(name);
            }
            Class<?> returnType = method.getReturnType();
            if (returnType == boolean.class) {
                return Boolean.FALSE;
            } else if (returnType == int.class) {
                return Integer.valueOf(0);
            } else if (returnType == long.class) {
                return Long.valueOf(0L);
            }
            return null;
        }
    }

    private static Connection proxyFor(StubChain chain) {
        DisposableConnectionFacade facade = new DisposableConnectionFacade(chain);
        return (Connection) Proxy.newProxyInstance(
                TestDisposableConnectionFacade.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                facade);
    }

    @Test
    void noCallPassesTheFacadeOnceTheConnectionIsReturned() throws Exception {
        StubChain chain = new StubChain();
        Connection proxy = proxyFor(chain);

        AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        Thread closer = new Thread(() -> {
            try {
                proxy.close();
            } catch (Throwable t) {
                closeFailure.set(t);
            }
        }, "close-in-progress");
        closer.setDaemon(true);
        closer.start();
        try {
            Assertions.assertTrue(chain.closeEntered.await(AWAIT_MS, TimeUnit.MILLISECONDS),
                    "the chain close must be entered");
            Assertions.assertTrue(chain.returnedToPool,
                    "precondition: the connection has already been handed back");

            // From this point on nothing may pass through the facade: the
            // physical connection is the pool's to hand out again.
            Assertions.assertEquals(Boolean.TRUE, Boolean.valueOf(proxy.isClosed()),
                    "the facade must report closed while the close is still in flight");
            Assertions.assertEquals(Boolean.FALSE, Boolean.valueOf(proxy.isValid(0)),
                    "the facade must report invalid while the close is still in flight");
            Assertions.assertThrows(SQLException.class, proxy::getAutoCommit,
                    "post-return calls must be rejected instead of reaching the chain");
            Assertions.assertEquals(0, chain.callsAfterReturn.size(),
                    "no call may reach the chain after the connection was returned, saw "
                            + chain.callsAfterReturn);
        } finally {
            chain.closeRelease.countDown();
            closer.join(AWAIT_MS);
            Assertions.assertFalse(closer.isAlive(), "the closing thread must have finished");
        }

        Assertions.assertNull(closeFailure.get(),
                () -> "close() must not fail: " + closeFailure.get());
        Assertions.assertEquals(1, chain.closeCount.get(), "the chain must see exactly one close");
        // A later close stays a no-op and never re-enters the chain
        proxy.close();
        Assertions.assertEquals(1, chain.closeCount.get());
    }

    @Test
    void concurrentCloseIsSealedOnceIntoTheChain() throws Exception {
        StubChain chain = new StubChain();
        Connection proxy = proxyFor(chain);

        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        Thread first = new Thread(() -> {
            try {
                proxy.close();
            } catch (Throwable t) {
                firstFailure.set(t);
            }
        }, "first close");
        first.setDaemon(true);
        first.start();
        Assertions.assertTrue(chain.closeEntered.await(AWAIT_MS, TimeUnit.MILLISECONDS),
                "the first close must be inside the chain");

        AtomicReference<Throwable> second = new AtomicReference<>();
        Thread secondClose = new Thread(() -> {
            try {
                proxy.close();
            } catch (Throwable t) {
                second.set(t);
            }
        }, "second close");
        secondClose.setDaemon(true);
        secondClose.start();
        try {
            secondClose.join(AWAIT_MS);
            Assertions.assertFalse(secondClose.isAlive(),
                    "the second closing thread must not park in the chain");

            Assertions.assertNull(second.get(),
                    () -> "the concurrent close must not fail: " + second.get());
            Assertions.assertEquals(1, chain.closeCount.get(),
                    "the concurrent close must observe the disposed facade instead of "
                            + "driving the return a second time");
        } finally {
            chain.closeRelease.countDown();
        }

        first.join(AWAIT_MS);
        Assertions.assertFalse(first.isAlive(), "the first closing thread must have finished");
        Assertions.assertNull(firstFailure.get(),
                () -> "the first close must not fail: " + firstFailure.get());
        Assertions.assertEquals(1, chain.closeCount.get());
    }

    /* The serialized second close above cannot cover the interleaving that
       matters most: two closes released into proxy.close() at the same
       instant. Capture-and-seal must be atomic so exactly one of them ever
       reaches the chain - the other must observe the disposed facade. */
    @Test
    void simultaneousDoubleCloseNeverDelegatesTwice() throws Exception {
        // Detection of the non-atomic variant measures ~5*10^-5 per round,
        // so the round count is chosen for ~200k to make the guard
        // dependable; the whole loop costs roughly two seconds.
        final int rounds = 200000;
        // Both closers and the test thread meet at each barrier per round
        final CyclicBarrier intoClose = new CyclicBarrier(3);
        final CyclicBarrier roundDone = new CyclicBarrier(3);
        final AtomicReference<Connection> proxyRef = new AtomicReference<>();
        final AtomicReference<Throwable> closerFailure = new AtomicReference<>();

        Runnable closer = () -> {
            try {
                for (int i = 0; i < rounds; i++) {
                    intoClose.await(AWAIT_MS, TimeUnit.MILLISECONDS);
                    proxyRef.get().close();
                    roundDone.await(AWAIT_MS, TimeUnit.MILLISECONDS);
                }
            } catch (Throwable t) {
                closerFailure.compareAndSet(null, t);
            }
        };
        ExecutorService closers = Executors.newFixedThreadPool(2);
        boolean completed = false;
        try {
            Future<?> first = closers.submit(closer);
            Future<?> second = closers.submit(closer);

            for (int round = 0; round < rounds; round++) {
                StubChain chain = new StubChain();
                chain.closeRelease.countDown(); // the chain close returns at once
                proxyRef.set(proxyFor(chain));
                intoClose.await(AWAIT_MS, TimeUnit.MILLISECONDS);
                roundDone.await(AWAIT_MS, TimeUnit.MILLISECONDS);
                final int r = round;
                Assertions.assertNull(closerFailure.get(),
                        () -> "round " + r + ": a closer failed: " + closerFailure.get());
                Assertions.assertEquals(1, chain.closeCount.get(),
                        () -> "round " + r
                                + ": both concurrent closes reached the chain; capture and seal"
                                + " must be atomic");
            }
            first.get(AWAIT_MS, TimeUnit.MILLISECONDS);
            second.get(AWAIT_MS, TimeUnit.MILLISECONDS);
            completed = true;
        } finally {
            closers.shutdownNow();
            if (!completed) {
                // A closer that died leaves its barriers untripartite, so the
                // main thread times out rather than seeing its exception;
                // surface the root failure instead.
                Throwable t = closerFailure.get();
                if (t != null) {
                    throw new AssertionError("a closer thread failed: " + t, t);
                }
            }
        }
    }
}
