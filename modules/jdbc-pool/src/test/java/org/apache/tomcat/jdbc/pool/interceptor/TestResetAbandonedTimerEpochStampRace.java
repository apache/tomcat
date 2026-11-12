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

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PooledConnection;

/**
 * A late activity call whose borrow epoch was checked before the connection was
 * rebound for the next borrow must not go on to stamp that next borrower. The
 * epoch check and the stamp have to be one step against {@code reset()}'s
 * rebind.
 */
class TestResetAbandonedTimerEpochStampRace extends JdbcPoolBaseTest {

    /**
     * Parks between the epoch check in stampFor() and the stamp itself.
     */
    private static final class ParkedTimer extends ResetAbandonedTimer {
        private final CountDownLatch inside = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public synchronized boolean resetTimer() {
            inside.countDown();
            try {
                release.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException x) {
                Thread.currentThread().interrupt();
            }
            return super.resetTimer();
        }
    }

    @Test
    void aLateStampCannotFollowTheNextBorrowsRebind() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1);
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        PooledConnection first = new PooledConnection(pool.getPoolProperties(), pool);
        PooledConnection second = new PooledConnection(pool.getPoolProperties(), pool);
        first.setTimestamp(111L);
        second.setTimestamp(222L);

        ParkedTimer timer = new ParkedTimer();
        timer.reset(pool, first);

        Method stampFor = ResetAbandonedTimer.class.getDeclaredMethod("stampFor", long.class);
        stampFor.setAccessible(true);
        AtomicReference<Throwable> stampFailure = new AtomicReference<>();
        Thread lateCall = new Thread(() -> {
            try {
                stampFor.invoke(timer, Long.valueOf(1L));
            } catch (Throwable x) {
                stampFailure.set(x);
            }
        }, "late-activity-call");
        lateCall.setDaemon(true);
        lateCall.start();
        Assertions.assertTrue(timer.inside.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "the late call must be parked between the epoch check and the stamp");

        AtomicReference<Throwable> resetFailure = new AtomicReference<>();
        Thread rebind = new Thread(() -> {
            try {
                timer.reset(pool, second);
            } catch (Throwable x) {
                resetFailure.set(x);
            }
        }, "rebind-thread");
        rebind.setDaemon(true);
        rebind.start();

        Thread.sleep(100);
        Assertions.assertTrue(rebind.isAlive(),
                "the rebind must wait for the late call's epoch-check-and-stamp step");

        timer.release.countDown();
        lateCall.join(AWAIT_TIMEOUT_MS);
        rebind.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(lateCall.isAlive(), "the late call must complete");
        Assertions.assertFalse(rebind.isAlive(), "the rebind must complete");
        Assertions.assertNull(stampFailure.get(), "late stamp failure: " + stampFailure.get());
        Assertions.assertNull(resetFailure.get(), "rebind failure: " + resetFailure.get());

        Assertions.assertNotEquals(111L, first.getTimestamp(),
                "the late call must stamp the borrow it entered under");
        Assertions.assertEquals(222L, second.getTimestamp(),
                "the late call must not stamp the newly rebound borrow");
    }
}
