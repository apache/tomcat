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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A borrower that is interrupted while waiting for a connection must
 * leave the pool exactly as it was: the waiters it registered have to be
 * removed, otherwise the pool believes connections are still owed and
 * strands the later borrowers. Scenario harvested from the retired
 * legacy class {@code Bug53367}, which never ran in the old build.
 *
 * <p>Asserted with pool accounting <em>and</em> the database's own
 * session count, for both queue implementations.</p>
 */
class TestInterruptedWaiter extends JdbcPoolBaseTest {

    private static final int HOLDERS = 3;

    @ParameterizedTest(name = "fairQueue={0}")
    @ValueSource(booleans = { true, false })
    void interruptedWaitersLeaveThePoolUsable(boolean fairQueue) throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setFairQueue(fairQueue);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMaxActive(HOLDERS);
        ds.getPoolProperties().setMaxIdle(HOLDERS);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setMaxWait(5000);
        ds.getPoolProperties().setRollbackOnReturn(true);
        ds.createPool();

        CountDownLatch holdersBorrowed = new CountDownLatch(HOLDERS);
        CountDownLatch releaseHolders = new CountDownLatch(1);
        CountDownLatch holdersReturned = new CountDownLatch(HOLDERS);
        for (int i = 0; i < HOLDERS; i++) {
            Thread holder = new Thread(() -> {
                try {
                    Connection c = ds.getConnection();
                    holdersBorrowed.countDown();
                    if (releaseHolders.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                        c.close();
                        holdersReturned.countDown();
                    }
                } catch (Exception e) {
                    // the assertions below report the broken state
                }
            }, "connection-holder");
            holder.setDaemon(true);
            holder.start();
        }
        Assertions.assertTrue(holdersBorrowed.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "the holders must reach maxActive");
        Assertions.assertEquals(0, ds.getIdle());
        Assertions.assertEquals(HOLDERS, ds.getActive());
        Assertions.assertEquals(HOLDERS, ds.getSize());
        awaitSessions(HOLDERS);

        // Now the pool is exhausted: every further borrower is a waiter.
        AtomicInteger interruptedWithConnection = new AtomicInteger();
        AtomicInteger stillWaiting = new AtomicInteger();
        Thread[] waiters = new Thread[HOLDERS];
        for (int i = 0; i < HOLDERS; i++) {
            waiters[i] = new Thread(() -> {
                stillWaiting.incrementAndGet();
                try (@SuppressWarnings("unused") Connection c = ds.getConnection()) {
                    interruptedWithConnection.incrementAndGet();
                } catch (Exception expected) {
                    // interrupted or timed out while waiting - as intended
                }
            }, "interrupted-waiter");
            waiters[i].setDaemon(true);
            waiters[i].start();
        }
        awaitUntil(() -> ds.getWaitCount() == HOLDERS); // the waiters are registered with the queue
        for (Thread waiter : waiters) {
            waiter.interrupt();
        }
        for (Thread waiter : waiters) {
            waiter.join(AWAIT_TIMEOUT_MS);
        }

        Assertions.assertEquals(0, interruptedWithConnection.get(),
                "no connection was available, so no waiter may hold one");
        Assertions.assertEquals(HOLDERS, stillWaiting.get());
        Assertions.assertEquals(0, ds.getIdle(),
                "interrupted waiters must not leave connections checked out");
        Assertions.assertEquals(HOLDERS, ds.getActive(),
                "interrupted waiters must not change the active count");
        Assertions.assertEquals(HOLDERS, ds.getSize());
        Assertions.assertEquals(HOLDERS, poolSessions(),
                "interrupted waiters must not leave physical connections behind");

        releaseHolders.countDown();
        Assertions.assertTrue(holdersReturned.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "the holders must return their connections");
        Assertions.assertEquals(HOLDERS, ds.getIdle(),
                "the returned connections must be reusable again");
        Assertions.assertEquals(0, ds.getActive());

        // and the pool must serve a fresh wave of borrowers
        AtomicInteger served = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        Thread[] borrowers = new Thread[HOLDERS];
        for (int i = 0; i < HOLDERS; i++) {
            borrowers[i] = new Thread(() -> {
                try (Connection c = ds.getConnection()) {
                    c.createStatement().execute("SELECT 1");
                    served.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                }
            }, "later-borrower");
            borrowers[i].setDaemon(true);
            borrowers[i].start();
        }
        for (Thread borrower : borrowers) {
            borrower.join(AWAIT_TIMEOUT_MS);
        }
        Assertions.assertEquals(0, failed.get(),
                "a pool that only ever had interrupted waiters must still serve them");
        Assertions.assertEquals(HOLDERS, served.get());
        awaitSessions(HOLDERS);
    }
}
