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
package org.apache.tomcat.jdbc.pool;

import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Exercises the externally-invoked idle maintenance (the documented
 * {@link DataSourceProxy#checkIdle()} API) on a running pool while the
 * per-connection lock is disabled, which is the default configuration when
 * the pool sweeper is switched off. Without a real lock, the maintenance
 * methods guard their mutations with checks that a concurrent borrower can
 * invalidate at any time. Before the fix, the sweep could release a
 * connection that a borrower had already taken out of the idle queue, and
 * the borrower's first statement on it then failed with
 * "A connection once released, can't be reestablished".
 */
public class TestUnlockedMaintenanceSweep extends JdbcPoolBaseTest {

    /*
     * The window under test is hit once per borrow/sweep collision, and a
     * hot borrow loop produces hundreds of thousands of them per second.
     * The run therefore stops at an iteration budget that even a slow CI
     * covers inside the wall-clock cap, and an error ends it at once,
     * instead of always burning the full cap.
     */
    private static final long RUN_MILLIS = 6000;
    private static final long ITERATIONS = 250_000;
    private static final int BORROWERS = 4;

    @Test
    public void unlockedIdleSweepMustNotReleaseConnectionsAlreadyBorrowed()
            throws Exception {
        DataSource ds = newDataSource();
        PoolConfiguration p = ds.getPoolProperties();
        // the configuration under test: no sweeper, no locking
        p.setTimeBetweenEvictionRunsMillis(0);
        p.setUseLock(false);
        p.setMaxActive(8);
        p.setInitialSize(8);
        p.setMinIdle(0);
        // every returned connection is immediately old enough to evict, so
        // the sweep is constantly trying to release connections
        p.setMinEvictableIdleTimeMillis(1);
        p.setTestWhileIdle(false);
        p.setRemoveAbandoned(false);
        p.setSuspectTimeout(-1);
        p.setMaxAge(0);
        p.setValidationQuery("SELECT 1");
        p.setTestOnBorrow(true);
        ds.createPool();
        // let the initial connections age out
        sleep(200);

        AtomicBoolean stop = new AtomicBoolean();
        AtomicLong uses = new AtomicLong();
        AtomicLong errors = new AtomicLong();
        AtomicReference<Exception> firstError = new AtomicReference<>();

        Thread sweeper = new Thread(() -> {
            while (!stop.get()) {
                ds.checkIdle();
            }
        }, "sweep-under-test");
        Runnable borrowerBody = () -> {
            while (!stop.get()) {
                try (Connection c = ds.getConnection();
                        Statement st = c.createStatement()) {
                    st.execute("SELECT 1");
                    uses.incrementAndGet();
                } catch (Exception x) {
                    firstError.compareAndSet(null, x);
                    errors.incrementAndGet();
                }
            }
        };
        Thread[] borrowers = new Thread[BORROWERS];
        for (int i = 0; i < BORROWERS; i++) {
            borrowers[i] = new Thread(borrowerBody, "borrower-" + i);
        }

        try {
            sweeper.start();
            for (Thread b : borrowers) {
                b.start();
            }
            long deadline = System.currentTimeMillis() + RUN_MILLIS;
            while (System.currentTimeMillis() < deadline
                    && uses.get() + errors.get() < ITERATIONS
                    && firstError.get() == null) {
                sleep(25);
            }
            stop.set(true);
            sweeper.join();
            for (Thread b : borrowers) {
                b.join();
            }
            awaitUntil(() -> ds.getActive() == 0);

            Assertions.assertTrue(ds.getReleasedIdleCount() > 0,
                    "the sweep must actually have released aged connections, "
                            + "otherwise this test exercises nothing");
            Assertions.assertEquals(0, errors.get(), () -> "the unlocked idle sweep broke "
                    + errors.get() + " of " + uses.get()
                    + " successful borrows, first failure: " + firstError.get());
            Assertions.assertEquals(ds.getSize(), ds.getIdle(),
                    "the idle sweep must not strand connections outside both queues");
        } finally {
            stop.set(true);
            ds.close(true);
        }
    }
}
