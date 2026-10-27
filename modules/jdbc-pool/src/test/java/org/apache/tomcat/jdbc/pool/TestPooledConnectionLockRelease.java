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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The per-connection lock is only taken when the configuration says so, but
 * the configuration is mutable at runtime (JMX setters change useLock and
 * the pool sweeper predicates on the shared PoolProperties). A
 * {@code lock()}/{@code unlock()} pair must therefore release based on the
 * lock state, not on the configuration at release time: otherwise a flip
 * between the calls either leaves the connection write-locked forever or
 * throws IllegalMonitorStateException at the caller.
 */
class TestPooledConnectionLockRelease extends JdbcPoolBaseTest {

    /* Switches the per-connection locking fully off, both the useLock
     * flag and every predicate of the pool-sweeper computation. */
    private static void lockingDisabled(PoolConfiguration props) {
        props.setUseLock(false);
        props.setRemoveAbandoned(false);
        props.setSuspectTimeout(0);
        props.setTestWhileIdle(false);
        props.setMinEvictableIdleTimeMillis(0);
        props.setMaxAge(0);
        Assertions.assertFalse(props.isPoolSweeperEnabled());
    }

    /* The pool-internal PooledConnection behind a borrowed connection. */
    private static PooledConnection delegateOf(Connection pooled) {
        ProxyConnection proxy = interceptorOf(pooled, ProxyConnection.class);
        Assertions.assertNotNull(proxy);
        PooledConnection delegate = proxy.getDelegateConnection();
        Assertions.assertNotNull(delegate);
        return delegate;
    }

    /*
     * Tries to lock the connection from a fresh daemon thread while locking
     * is configured on, and reports whether the lock was taken and
     * released. A leftover write lock from this thread makes the attempt
     * block, so the unsuccessful case is reported after a bounded wait
     * rather than hanging the suite. Caller must have locking disabled
     * (lockingDisabled) before the call.
     */
    private static boolean takenAndReleasedByAnotherThread(PooledConnection pcon,
            PoolConfiguration props) throws InterruptedException {
        props.setUseLock(true);
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch released = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            pcon.lock();
            acquired.countDown();
            pcon.unlock();
            released.countDown();
        }, "test-lock-holder");
        holder.setDaemon(true); // a leftover lock pre-fix would block it forever
        holder.start();
        boolean taken = acquired.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        boolean done = taken && released.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        props.setUseLock(false);
        return done;
    }

    @Test
    void unlockingAfterTheLockWasSwitchedOffStillReleasesTheLock() throws Exception {
        DataSource ds = newDataSource();
        PoolConfiguration props = ds.getPoolProperties();
        lockingDisabled(props);

        try (Connection c = ds.getConnection()) {
            PooledConnection pcon = delegateOf(c);
            props.setUseLock(true);
            pcon.lock();
            props.setUseLock(false);
            pcon.unlock();
            // Pre-fix the unlock() above was a no-op and the write lock
            // stayed held by this thread forever.
            Assertions.assertTrue(takenAndReleasedByAnotherThread(pcon, props),
                    "unlock() must release the lock even when locking was "
                            + "switched off between the calls");
        }
    }

    @Test
    void unlockingAfterTheLockWasSwitchedOnDoesNotThrow() throws Exception {
        DataSource ds = newDataSource();
        PoolConfiguration props = ds.getPoolProperties();
        lockingDisabled(props);

        try (Connection c = ds.getConnection()) {
            PooledConnection pcon = delegateOf(c);
            pcon.lock(); // no-op: locking is off
            props.setUseLock(true);
            // Pre-fix this threw IllegalMonitorStateException from the
            // unpaired release.
            Assertions.assertDoesNotThrow(pcon::unlock,
                    "unlock() must be a no-op when lock() never acquired");
            Assertions.assertTrue(takenAndReleasedByAnotherThread(pcon, props),
                    "the connection lock must be free after a no-op "
                            + "lock/unlock pair");
        }
    }
}
