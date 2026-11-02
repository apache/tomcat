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
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The abandoned check measures the checkout age from the timestamp the
 * hand-off leaves on the connection. A borrower that queued past the abandon
 * timeout - a documented combination of a long (or unbounded) maxWait with
 * removeAbandoned - must therefore be armed at the moment it receives the
 * connection, not at the moment it gave up its first look at the queue:
 * otherwise the next sweep reads the fresh checkout as expired, counts a
 * phantom abandon, and disconnects the session under the application that
 * is legitimately using it.
 */
class TestAbandonedAfterLongWait extends JdbcPoolBaseTest {

    @Test
    void aBorrowerThatWaitedPastTheAbandonTimeoutIsNotBornAbandoned() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxIdle(1);
        ds.getPoolProperties().setFairQueue(true);
        ds.getPoolProperties().setRemoveAbandoned(true);
        ds.getPoolProperties().setRemoveAbandonedTimeout(2); // seconds
        // keeps the *holder* of the slot fresh while the queued borrower
        // waits: it is the borrower's stamp that is under test, and without
        // this the holder of the only slot would be legitimately abandoned
        // mid-hold and take the scenario with it
        ds.getPoolProperties().setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.ResetAbandonedTimer");
        ds.getPoolProperties().setMaxWait(10000);
        // sweep well below the timeout: the stale stamp would otherwise only
        // hide between sweeps; this makes the phantom abandon fire on the
        // first pass after the hand-off
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(300);
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection(); // takes the only slot
        awaitSessions(1);

        AtomicReference<Connection> served = new AtomicReference<>();
        AtomicReference<Throwable> borrowFailure = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            try {
                served.set(ds.getConnection());
            } catch (Throwable x) {
                borrowFailure.set(x);
            }
        }, "queued-borrower");
        waiter.setDaemon(true);
        waiter.start();
        awaitUntil(() -> pool.getWaitCount() > 0);

        // The waiter stays queued past the abandon timeout - the holder
        // heartbeats its own checkout so only the hand-off's stamp is at
        // issue here - and then the slot frees and the queue hands the
        // connection straight to the waiter.
        long holdUntil = System.currentTimeMillis() + 2500;
        while (System.currentTimeMillis() < holdUntil) {
            Thread.sleep(400);
            // any call on the handle re-arms the holder's abandoned timer
            Assertions.assertTrue(held.isValid(1), "the holder's session stays alive throughout");
        }
        held.close();
        waiter.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(waiter.isAlive(), "the wait must complete");
        Assertions.assertNull(borrowFailure.get(), "the borrow must succeed, got " + borrowFailure.get());
        Connection fresh = served.get();
        Assertions.assertNotNull(fresh);

        // Several sweeps have now run against a checkout whose elapsed time
        // - if it inherited the 2.5 s of queueing - already exceeds the
        // timeout. None of them may take this session out of service.
        long deadline = System.currentTimeMillis() + 1200;
        while (System.currentTimeMillis() < deadline && pool.getRemoveAbandonedCount() == 0) {
            Thread.sleep(100);
        }
        Assertions.assertEquals(0, pool.getRemoveAbandonedCount(),
                "a session handed over seconds ago is not an abandoned checkout");
        Assertions.assertTrue(fresh.isValid(1),
                "the abandoned sweep must not disconnect a session under its borrower");
        Assertions.assertEquals(1, pool.getActive(),
                "the sweep must not have claimed the borrower's busy entry");
        awaitSessions(1);

        fresh.close();
        ds.close();
        awaitSessions(0);
    }
}
