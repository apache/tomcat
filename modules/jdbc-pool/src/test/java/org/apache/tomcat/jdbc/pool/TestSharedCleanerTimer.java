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
import java.util.Timer;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * One {@link java.util.Timer} serves the maintenance of every pool the
 * class loader holds, and a timer cancels itself for good the moment a
 * task lets a throwable escape - so an escaping {@code Error} from the
 * cleaner's body is not one pool's accident but the end of eviction and
 * abandoned-check for every pool in the JVM, later pool starts included:
 * scheduling onto the dead timer fails with {@code IllegalStateException}
 * and the pool-side field still holds it as if it were live. These tests
 * pin the three parts of that contract: an ordinary {@code Error} inside
 * the swept work is contained and the shared timer goes on serving, a
 * timer that arrives dead is replaced rather than obeyed, and - so the
 * containment cannot silently grow into swallowing the unrecoverable -
 * the fatal conditions ExceptionUtils keeps fatal still propagate, from
 * a borrow and from the task alike.
 */
class TestSharedCleanerTimer extends JdbcPoolBaseTest {

    /**
     * Fails the idle sweep's check the way a stopped web-application class
     * loader fails an interceptor: a LinkageError the maintenance work can
     * neither predict nor need. Every other action answers normally, so
     * the sweep is the only place the error can arrive.
     */
    public static class SweepErrorValidator implements Validator {
        private final AtomicInteger idleCalls = new AtomicInteger();

        @Override
        public boolean validate(Connection connection, int validateAction) {
            if (validateAction == PooledConnection.VALIDATE_IDLE) {
                idleCalls.incrementAndGet();
                throw new NoClassDefFoundError("the context this validator was loaded from is stopped");
            }
            return true;
        }
    }

    /** Reports every check fatal, whatever asked for it. */
    public static class FatalValidator implements Validator {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public boolean validate(Connection connection, int validateAction) {
            calls.incrementAndGet();
            throw new OutOfMemoryError("the validator reports a dying JVM");
        }
    }

    @Test
    void anErrorInsideTheSweptWorkLeavesTheSharedTimerServing() throws Exception {
        SweepErrorValidator validator = new SweepErrorValidator();
        DataSource first = newDataSource();
        first.getPoolProperties().setInitialSize(0);
        // the task's checkIdle step evicts idle connections down to min
        // idle; park the floor at the size this test keeps so the sweep is
        // the only thing the task can do to the connection
        first.getPoolProperties().setMinIdle(1);
        first.getPoolProperties().setTestWhileIdle(true);
        first.getPoolProperties().setTimeBetweenEvictionRunsMillis(200);
        first.getPoolProperties().setValidationInterval(0);
        first.getPoolProperties().setValidator(validator);
        first.createPool();
        Timer timer = ConnectionPool.getPoolTimer();
        Assertions.assertNotNull(timer);

        Connection firstBorrowed = first.getConnection();
        firstBorrowed.close();
        // let the scheduled sweep really run into the error
        awaitUntil(() -> validator.idleCalls.get() >= 1);

        // the swept connection was read as a failed check and taken out of
        // service - not stranded mid-sweep
        Assertions.assertEquals(0, first.getPool().getIdle());
        Assertions.assertEquals(0, first.getPool().getSize());

        // the timer outlived the sweep: a second pool starts on the very
        // same timer a dying task would have cancelled
        DataSource next = newDataSource();
        next.getPoolProperties().setTimeBetweenEvictionRunsMillis(200);
        next.createPool();
        Assertions.assertSame(timer, ConnectionPool.getPoolTimer(),
                "the shared timer must still be the live one the second pool joined");
        try (Connection c = next.getConnection()) {
            Assertions.assertNotNull(c);
        }

        next.close();
        first.close();
    }

    @Test
    void afatalErrorInsideTheSweptWorkStillPropagatesFromTheTask() throws Exception {
        FatalValidator validator = new FatalValidator();
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(1);
        ds.getPoolProperties().setTestWhileIdle(true);
        // the sweep runs here, on this thread, exactly as the shared timer
        // would run it - and no background competitor for the idle queue
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(0);
        ds.getPoolProperties().setValidationInterval(0);
        ds.getPoolProperties().setValidator(validator);
        ds.createPool();
        Connection first = ds.getConnection();
        first.close();
        Assertions.assertEquals(1, ds.getPool().getIdle());

        ConnectionPool.PoolCleaner cleaner = new ConnectionPool.PoolCleaner(ds.getPool(), 100);

        // the task-wide guard reports and keeps the module's fatal policy:
        // a VirtualMachineError is beyond any per-call recovery and keeps
        // propagating from the pool, the task and the borrow alike
        Assertions.assertThrows(OutOfMemoryError.class, cleaner::run);
        Assertions.assertTrue(validator.calls.get() >= 1,
                "the sweep must have run into the error the guard is tested against");
    }

    @Test
    void aDeadSharedTimerIsReplacedWhenANewPoolNeedsOne() throws Exception {
        DataSource first = newDataSource();
        first.getPoolProperties().setTimeBetweenEvictionRunsMillis(500);
        first.createPool();
        Timer dead = ConnectionPool.getPoolTimer();
        Assertions.assertNotNull(dead, "a pool with a sweeper must hold the shared timer");

        // The state a task that died on a fatal error leaves behind: the
        // timer cancelled for good while the field still points at it as
        // if live.
        dead.cancel();

        DataSource next = newDataSource();
        next.getPoolProperties().setTimeBetweenEvictionRunsMillis(500);
        Assertions.assertDoesNotThrow(next::createPool,
                "a dead shared timer must not make every later pool fail to start");
        Assertions.assertNotSame(dead, ConnectionPool.getPoolTimer(),
                "the refusal must have been read as a dead timer and answered with a fresh one");
        try (Connection c = next.getConnection()) {
            Assertions.assertNotNull(c);
        }

        next.close();
        first.close();
    }

    @Test
    void afatalErrorStillPropagatesThroughABorrow() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setTestOnBorrow(true);
        ds.getPoolProperties().setValidationInterval(0);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(0);
        ds.getPoolProperties().setValidator(new FatalValidator());
        // the creating borrow is not validated; the re-borrow is
        Connection first = ds.getConnection();
        first.close();

        Assertions.assertThrows(OutOfMemoryError.class, ds::getConnection);
        ds.close();
    }
}
