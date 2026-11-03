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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
 * pin the parts of that contract: an ordinary {@code Error} inside
 * the swept work is contained and the shared timer goes on serving, a
 * timer that arrives dead is replaced rather than obeyed and the pools
 * whose tasks it took with it are armed again on the replacement - a
 * timer task that has been scheduled once is spent, so a new timer
 * revives nothing by itself - while a task that no timer will ever take
 * again is refused rather than retried, and - so the containment cannot
 * silently grow into swallowing the unrecoverable - the fatal conditions
 * ExceptionUtils keeps fatal still propagate, from a borrow and from the
 * task alike.
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
        // service - not stranded mid-sweep. The size settles only once the
        // retired session's physical close has run, a step after the sweep
        // that decided it, so it is awaited rather than raced; a connection
        // left between the two queues never reaches zero.
        Assertions.assertEquals(0, first.getPool().getIdle());
        awaitUntil(() -> first.getPool().getSize() == 0);
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

    /** Vouches for every session it is asked about, counting the idle checks. */
    public static class IdleCheckCounter implements Validator {
        private final AtomicInteger idleCalls = new AtomicInteger();

        @Override
        public boolean validate(Connection connection, int validateAction) {
            if (validateAction == PooledConnection.VALIDATE_IDLE) {
                idleCalls.incrementAndGet();
            }
            return true;
        }
    }

    @Test
    void aDeadSharedTimerTakesDownNoOtherPoolSweeper() throws Exception {
        IdleCheckCounter counter = new IdleCheckCounter();
        DataSource swept = newDataSource();
        swept.getPoolProperties().setMinIdle(1);
        swept.getPoolProperties().setTestWhileIdle(true);
        swept.getPoolProperties().setTimeBetweenEvictionRunsMillis(200);
        swept.getPoolProperties().setValidationInterval(0);
        swept.getPoolProperties().setValidator(counter);
        swept.createPool();

        DataSource other = newDataSource();
        other.getPoolProperties().setTimeBetweenEvictionRunsMillis(500);
        other.createPool();

        Set<TimerTask> scheduled = new HashSet<>(ConnectionPool.getPoolCleaners());
        Assertions.assertEquals(2, scheduled.size(),
                "both pools must be served by the one shared timer");

        Connection borrowed = swept.getConnection();
        borrowed.close();
        // the idle sweep has really reached the parked session
        awaitUntil(() -> counter.idleCalls.get() >= 1);

        // The state a task that died on a fatal error leaves behind for
        // every pool: the shared timer cancelled for good, and the tasks that
        // were scheduled on it spent - a timer task that has been scheduled
        // once can never be scheduled again, so a replacement timer revives
        // nothing by itself.
        Timer dead = ConnectionPool.getPoolTimer();
        Assertions.assertNotNull(dead);
        dead.cancel();

        DataSource next = newDataSource();
        next.getPoolProperties().setTimeBetweenEvictionRunsMillis(500);
        next.createPool();
        Assertions.assertNotSame(dead, ConnectionPool.getPoolTimer(),
                "the refusal must have been read as a dead timer and answered with a fresh one");

        // The pool whose task the dead timer took has to be maintaining again,
        // not merely registered: its sweep reaches the parked session a second
        // time on the replacement timer.
        awaitUntil(() -> counter.idleCalls.get() >= 2);
        Assertions.assertTrue(Collections.disjoint(scheduled, ConnectionPool.getPoolCleaners()),
                "the tasks that lost the dead timer must have left the registry");
        Assertions.assertEquals(3, ConnectionPool.getPoolCleaners().size(),
                "every pool must be represented by the task that now serves it");

        next.close();
        other.close();
        swept.close();
    }

    @Test
    void aCleanerTaskStartedTwiceIsRefusedRatherThanLoopedOn() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(500);
        ds.createPool();
        Timer serving = ConnectionPool.getPoolTimer();
        Assertions.assertNotNull(serving);

        ConnectionPool.PoolCleaner twice =
                new ConnectionPool.PoolCleaner(ds.getPool(), 200);
        twice.start();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        try {
            // The second start is the arrival a new timer cannot cure: the task
            // itself has been scheduled and given up, and no timer will ever
            // take it again. Keyed on the refusal alone, this is a loop that
            // builds a fresh timer per turn, never schedules anything, and
            // holds the registry's monitor for the rest of the JVM's life - so
            // the refusal has to come back as the refusal it is, and shortly.
            Thread attempt = new Thread(() -> {
                try {
                    twice.start();
                } catch (Throwable x) {
                    thrown.set(x);
                }
            }, "second-start-of-one-cleaner");
            attempt.setDaemon(true);
            attempt.start();
            attempt.join(10000);
            Assertions.assertFalse(attempt.isAlive(),
                    "a refusal no timer can cure must be reported, not retried");
            Assertions.assertInstanceOf(IllegalStateException.class, thrown.get(),
                    "starting a spent task keeps answering with the refusal it always did");
        } finally {
            twice.stopRunning();
        }

        Assertions.assertFalse(ConnectionPool.getPoolCleaners().contains(twice),
                "the refused task must not be left claiming a registration");
        Assertions.assertSame(serving, ConnectionPool.getPoolTimer(),
                "the timer that was serving must be the one still serving");

        // and it is serving: a pool that needs a cleaner joins it unchanged
        DataSource next = newDataSource();
        next.getPoolProperties().setTimeBetweenEvictionRunsMillis(500);
        next.createPool();
        Assertions.assertSame(serving, ConnectionPool.getPoolTimer());
        try (Connection c = next.getConnection()) {
            Assertions.assertNotNull(c);
        }

        next.close();
        ds.close();
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
