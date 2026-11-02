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
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * How many connections a pool pre-loads is a count, and a negative count is not
 * a number of anything. The pool bounds the other ends of its counts - a limit
 * below one, an idle count above the active limit - in
 * {@link ConnectionPool#checkPoolConfiguration(PoolConfiguration)}, which warns
 * and repairs because the stores themselves are plain assignments; the count of
 * connections to pre-load had a rule for being too large and none for being
 * below zero. It is used to size an array while the pool is being built, so an
 * uncorrected negative one fails there, and the failure is one the creation
 * path does not handle: the pool has already told its interceptors that it
 * started by then, and the teardown that would tell them otherwise only runs on
 * the failures the path expects.
 * <p>
 * Correcting the count inside the check covers the stores the check sees; the
 * array it feeds is allocated mid-creation, after the construction check and
 * before the publication one, and this count's writer holds no monitor the
 * construction takes - so the consumer itself is what has to bound the number,
 * whatever ordering a store races in on.
 */
class TestInitialSizeBelowZero extends JdbcPoolBaseTest {

    /*
     * Hooks the construction check's first read of the pre-load count - the
     * stack must be checkPoolConfiguration called from init, which excludes
     * the publication check (called from pCreatePool) - releases a racer
     * parked before its setter call, waits for the store to land, and answers
     * the hooked read with the value from before the store: the view the check
     * carries away when a writer stores after it has already looked.
     */
    private static final class HookedInitialSizeProperties extends PoolProperties {

        private static final long serialVersionUID = 1L;

        volatile boolean armed;
        volatile boolean fired;
        volatile CountDownLatch release;
        volatile CountDownLatch finished;

        @Override
        public int getInitialSize() {
            if (armed && !fired && fromConstructionCheck()) {
                fired = true;
                int before = super.getInitialSize();
                release.countDown();
                try {
                    finished.await(500, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return before;
            }
            return super.getInitialSize();
        }

        private boolean fromConstructionCheck() {
            StackTraceElement[] frames = new Throwable().getStackTrace();
            for (int i = 1; i < frames.length - 1; i++) {
                if ("checkPoolConfiguration".equals(frames[i].getMethodName())
                        && "init".equals(frames[i + 1].getMethodName())) {
                    return true;
                }
            }
            return false;
        }
    }

    /*
     * A pool asked to pre-load a negative number of connections: the count is
     * corrected, the pool starts, and the interceptors that were told it started
     * are eventually told it stopped - which is what a pool that dies on this
     * count never gets as far as doing.
     */
    @Test
    void aNegativeInitialSizeDoesNotStopThePoolBeingCreated() throws Exception {
        LifecycleInterceptor.started.set(0);
        LifecycleInterceptor.closed.set(0);
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(LifecycleInterceptor.class.getName());
        ds.getPoolProperties().setInitialSize(-1);

        ds.createPool();

        Assertions.assertEquals(0, ds.getPoolProperties().getInitialSize(),
                "a negative number of connections to pre-load is a misconfiguration like the ones the pool "
                        + "already corrects, and the correction belongs where those are corrected");
        Assertions.assertEquals(1, LifecycleInterceptor.started.get(),
                "precondition: the pool's interceptors were told it started");
        Assertions.assertEquals(0, LifecycleInterceptor.closed.get(),
                "a pool that is running has not been closed");

        try (Connection pooled = ds.getConnection()) {
            Assertions.assertTrue(pooled.isValid(1),
                    "a pool that had to correct its pre-load count still hands out sessions");
        }

        ds.close();
        Assertions.assertEquals(1, LifecycleInterceptor.closed.get(),
                "the interceptors of a pool that started are told it closed");
    }

    /*
     * The control, and the reason the lifecycle above matters: a pre-load that
     * fails for a reason the creation path expects comes back as a failure to
     * connect, and not before the pool has been torn down properly - so the
     * interceptors that were started are closed, and the half-built pool is not
     * left running.
     */
    @Test
    void aPoolThatFailsItsPreLoadIsTornDownBeforeTheFailureReachesTheCaller() throws Exception {
        LifecycleInterceptor.started.set(0);
        LifecycleInterceptor.closed.set(0);
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(LifecycleInterceptor.class.getName());
        ds.getPoolProperties().setInitialSize(1);
        // Nothing listens there, so the driver's connect fails: the pre-load
        // cannot produce a session, which is a failure the creation path expects.
        ds.getPoolProperties().setUrl("jdbc:h2:tcp://localhost:1/mem:a_database_that_is_not_there");

        Assertions.assertThrows(SQLException.class, ds::createPool,
                "a pre-load that cannot produce a usable session reaches the caller as a failure to connect");
        Assertions.assertEquals(1, LifecycleInterceptor.started.get(),
                "precondition: the pool's interceptors were told it started");
        Assertions.assertEquals(1, LifecycleInterceptor.closed.get(),
                "a creation that gets as far as starting the interceptors tells them it closed again");
    }

    /*
     * The creation-window instance: a store that lands after the
     * construction check has read the count is past the clamp that lives in
     * the check, and the pre-load array is allocated from whatever the shared
     * configuration says at the allocation itself. The store is forced right
     * into that gap here, and creation must still complete as the contract
     * allows it to - corrected, started, and closeable - rather than dying
     * unchecked between the check that trusted the value and the teardown
     * that never learns to run.
     */
    @Test
    void aNegativeInitialSizeStoredWhileThePoolIsBeingBuiltDoesNotAbortTheCreation() throws Exception {
        LifecycleInterceptor.started.set(0);
        LifecycleInterceptor.closed.set(0);
        DataSource ds = new DataSource();
        HookedInitialSizeProperties properties = new HookedInitialSizeProperties();
        properties.setUrl(getUrl());
        properties.setDriverClassName("org.h2.Driver");
        properties.setUsername("sa");
        properties.setPassword("");
        properties.setJmxEnabled(false);
        properties.setInitialSize(1);
        properties.setMaxActive(4);
        properties.setMinIdle(0);
        properties.setMaxIdle(4);
        properties.setJdbcInterceptors(LifecycleInterceptor.class.getName());
        ds.setPoolProperties(properties);
        manage(ds);

        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        properties.release = release;
        properties.finished = finished;
        Thread racer = new Thread(() -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            ds.setInitialSize(-1);
            finished.countDown();
        }, "store-during-construction");
        racer.setDaemon(true);
        racer.start();

        properties.armed = true;
        ds.createPool();
        racer.join(TimeUnit.SECONDS.toMillis(10));

        Assertions.assertEquals(0, ds.getPoolProperties().getInitialSize(),
                "the pool must bound a pre-load count that raced in after the construction check, "
                        + "not die on it");
        Assertions.assertEquals(1, LifecycleInterceptor.started.get(),
                "precondition: the pool's interceptors were told it started");
        Assertions.assertEquals(0, LifecycleInterceptor.closed.get(),
                "a pool that is running has not been closed");
        try (Connection pooled = ds.getConnection()) {
            Assertions.assertTrue(pooled.isValid(1),
                    "a pool whose pre-load count arrived negative mid-creation still hands out sessions");
        }
        ds.close();
        Assertions.assertEquals(1, LifecycleInterceptor.closed.get(),
                "the interceptors of a pool that started are told it closed");
    }

    /**
     * Interceptor that counts the pool lifecycle callbacks it is given. The
     * counters are static because the pool instantiates interceptors itself, by
     * name, so no test holds the instance they run on.
     */
    public static class LifecycleInterceptor extends JdbcInterceptor {

        static final AtomicInteger started = new AtomicInteger();
        static final AtomicInteger closed = new AtomicInteger();

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // Nothing to track: this interceptor only listens for the lifecycle.
        }

        @Override
        public void poolStarted(ConnectionPool pool) {
            started.incrementAndGet();
            super.poolStarted(pool);
        }

        @Override
        public void poolClosed(ConnectionPool pool) {
            closed.incrementAndGet();
            super.poolClosed(pool);
        }
    }
}
