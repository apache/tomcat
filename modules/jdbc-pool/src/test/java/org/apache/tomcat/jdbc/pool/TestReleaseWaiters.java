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
import java.sql.ResultSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The {@code waitcount} protocol between {@code release()} and
 * {@code borrowConnection()} must neither lose a wake-up nor double-count
 * one. A borrower that registers as a waiter just after the last connection
 * was destroyed must still get served (the releasing thread saw no waiter and
 * sends no notification), and only a release that actually destroyed a
 * connection may hand out a wake-up placeholder - a second release of the
 * same connection would otherwise run the pool over maxActive.
 */
class TestReleaseWaiters extends JdbcPoolBaseTest {

    /*
     * Pool properties that notice - and widen - the window inside
     * borrowConnection() between the maxActive check that failed and the
     * registration of the waiter. The maxWait computation at that spot is the
     * only pool call that runs there, so the probe fires exactly in the
     * window the release() notification can miss. The window signal is a
     * one-shot: whoever arms it gets exactly the next getMaxWait() call, so
     * nothing else may poll the property between arm() and the borrower's
     * arrival.
     */
    private static final class SlotReleaseProbe extends PoolProperties {

        private static final long serialVersionUID = 1L;

        private final CountDownLatch inWindow = new CountDownLatch(1);
        private final AtomicBoolean armed = new AtomicBoolean();

        @Override
        public int getMaxWait() {
            if (armed.compareAndSet(true, false)) {
                inWindow.countDown();
                sleep(300);
            }
            return super.getMaxWait();
        }

        void arm() {
            armed.set(true);
        }

        boolean awaitInWindow() throws InterruptedException {
            return inWindow.await(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void destroyingTheLastConnectionJustBeforeAWaiterRegistersStillLeavesTheSlotUsable()
            throws Exception {
        SlotReleaseProbe props = new SlotReleaseProbe();
        props.setUrl(getUrl());
        props.setDriverClassName("org.h2.Driver");
        props.setUsername("sa");
        props.setPassword("");
        props.setJmxEnabled(false);
        props.setInitialSize(0);
        props.setMinIdle(0);
        props.setMaxActive(1);
        // <= 0 means: wait forever for a notification
        props.setMaxWait(-1);
        DataSource ds = manage(new DataSource());
        ds.setPoolProperties(props);
        ds.createPool();

        Connection holder = ds.getConnection(); // the only slot is taken

        final Connection[] served = new Connection[1];
        final Throwable[] failure = new Throwable[1];
        Thread borrower = new Thread(() -> {
            try {
                served[0] = ds.getConnection();
            } catch (Throwable x) {
                failure[0] = x;
            }
        }, "probe-borrower");
        props.arm();
        borrower.start();
        Assertions.assertTrue(props.awaitInWindow(),
                "borrower should have reached the window between the maxActive "
                        + "check and the waiter registration");

        // Destroy the only connection while no waiter is registered yet: the
        // release sees waitcount == 0 and sends no notification. Bumping the
        // pool version makes the return path discard the connection instead
        // of parking it in idle.
        ds.getPool().purgeOnReturn();
        holder.close();

        borrower.join(AWAIT_TIMEOUT_MS);
        if (borrower.isAlive()) {
            borrower.interrupt(); // unstick it before failing
            Assertions.fail("a borrower whose notification was lost waits forever even "
                    + "though a slot is free");
        }
        Assertions.assertNull(failure[0], "the borrower should have been served");
        try (ResultSet rs = served[0].createStatement().executeQuery("SELECT 1")) {
            Assertions.assertTrue(rs.next());
        }
        awaitSessions(1); // the old one destroyed, the borrower's created
        served[0].close();
        Assertions.assertEquals(1, ds.getIdle());
    }

    @Test
    void duplicateReleaseOfTheSameConnectionDoesNotCreateAnExtraPlaceholder()
            throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxIdle(1);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setMaxWait(4000);
        ds.createPool();

        Connection holder = ds.getConnection(); // maxActive is reached
        PooledConnection pc =
                interceptorOf(holder, ProxyConnection.class).getConnection();
        ConnectionPool pool = ds.getPool();

        final Thread[] waiters = new Thread[2];
        final Connection[] served = new Connection[2];
        for (int i = 0; i < waiters.length; i++) {
            final int slot = i;
            waiters[i] = new Thread(() -> {
                try {
                    served[slot] = ds.getConnection();
                } catch (Throwable ignore) {
                    // timing out is the correct outcome for the loser
                }
            }, "waiter-" + i);
            waiters[i].start();
        }
        awaitUntil(() -> ds.getWaitCount() == 2);

        // Destroy the connection: exactly one waiter wakes up on the one
        // wake-up placeholder.
        pool.release(pc);
        awaitUntil(() -> ds.getWaitCount() == 1);

        // A second release of the same, already destroyed connection must not
        // wake the second waiter too - there is only one slot.
        pool.release(pc);

        // The wake-up placeholder was the notification for the one real
        // destruction; consuming it must leave exactly one waiter behind. If
        // the duplicate release handed out its own placeholder, the remaining
        // waiter wakes up too - one slot cannot serve two borrowers. Give it
        // a good fraction of maxWait, it is handed the placeholder directly
        // and would wake within milliseconds if it were woken at all.
        sleep(500);
        Assertions.assertEquals(1, ds.getWaitCount(),
                "the duplicate release must not wake the remaining waiter");

        for (Thread waiter : waiters) {
            waiter.join(AWAIT_TIMEOUT_MS);
            if (waiter.isAlive()) {
                waiter.interrupt();
                Assertions.fail("waiter is stuck");
            }
        }
        int servedCount = (served[0] == null ? 0 : 1) + (served[1] == null ? 0 : 1);
        Assertions.assertEquals(1, servedCount,
                "only the destruction of the connection should have opened a slot");
        awaitSessions(1);
        for (Connection c : served) {
            if (c != null) {
                c.close();
            }
        }
    }
}
