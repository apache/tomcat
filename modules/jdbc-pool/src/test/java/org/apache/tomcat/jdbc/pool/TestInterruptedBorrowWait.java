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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * What an interrupted borrow wait fails with, and what the asynchronous
 * entry point treats as its conversion signal.
 *
 * <p>The property contract used to promise a
 * {@link PoolExhaustedException} for an interrupted wait while the pool
 * threw a plain {@link SQLException}. The type was made authoritative
 * toward the code, not the promise: an interrupted wait is not pool
 * exhaustion - the exception's own definition reserves it for "the pool
 * is exhausted and no connections are available" - so an interrupted
 * wait must fail with a SQLException carrying the InterruptedException
 * as its cause, and only the two real exhaustion exits throw the named
 * type.</p>
 *
 * <p>The asynchronous entry point converts a "the pool is empty right
 * now" refusal into an async wait. That decision now reads the
 * exception's type rather than a marker inside its localized message, so
 * a refusal that means "wait" is still recognized as such whatever the
 * message says, and any other failure still reaches the caller.</p>
 */
class TestInterruptedBorrowWait extends JdbcPoolBaseTest {

    private static final class BorrowOutcome {
        private volatile Throwable failure;
        private volatile boolean interruptedAfterFailure;
    }

    /**
     * A borrow that blocks in the pool's wait (the pool is fully
     * borrowed), parked until the pool itself counts it as a waiter, then
     * interrupted.
     */
    private Thread startInterruptedBorrow(final DataSource ds,
            final BorrowOutcome outcome) throws InterruptedException {
        Thread borrower = new Thread(() -> {
            try {
                ds.getConnection();
                outcome.failure = null; // a connection should never arrive here
            } catch (Throwable t) {
                outcome.failure = t;
                outcome.interruptedAfterFailure =
                        Thread.currentThread().isInterrupted();
            }
        }, "interrupted-borrower");
        borrower.setDaemon(true);
        borrower.start();
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
        while (ds.getWaitCount() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        Assertions.assertEquals(1, ds.getWaitCount(),
                "the borrow must be registered as a pool waiter before it "
                        + "is interrupted");
        borrower.interrupt();
        return borrower;
    }

    /**
     * Pins the contract the property javadoc now states: the interrupted
     * wait fails with a SQLException naming the interrupted wait, with
     * the InterruptedException as its cause, and it is deliberately not
     * the exhaustion type.
     */
    private void assertInterruptedWaitFailsAsDocumented(
            DataSource ds, boolean propagateInterruptState)
            throws Exception {
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setPropagateInterruptState(
                propagateInterruptState);
        ds.createPool();
        Connection held = manage(ds).getConnection();

        BorrowOutcome outcome = new BorrowOutcome();
        Thread borrower = startInterruptedBorrow(ds, outcome);
        borrower.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(borrower.isAlive(),
                "the interrupted borrow must fail, not keep waiting");

        Throwable failure = outcome.failure;
        Assertions.assertNotNull(failure, "the interrupted borrow must fail");
        Assertions.assertInstanceOf(SQLException.class, failure,
                "the interrupted borrow must fail with a SQLException");
        Assertions.assertFalse(failure instanceof PoolExhaustedException,
                "an interrupted wait is not pool exhaustion and must not "
                        + "carry the exhaustion type");
        Assertions.assertInstanceOf(InterruptedException.class,
                failure.getCause(),
                "the interrupted wait must carry the InterruptedException "
                        + "as its cause");

        // The property governs only the thread's flag, never the failure.
        Assertions.assertEquals(propagateInterruptState,
                outcome.interruptedAfterFailure,
                "propagateInterruptState=" + propagateInterruptState
                        + " must decide whether the interrupt flag stays set");

        held.close();
    }

    @Test
    void interruptedWaitFailsWithDocumentedTypeAndClearsTheFlagByDefault()
            throws Exception {
        assertInterruptedWaitFailsAsDocumented(newDataSource(), false);
    }

    @Test
    void propagatedInterruptKeepsTheFlagButNotTheFailureType()
            throws Exception {
        assertInterruptedWaitFailsAsDocumented(newDataSource(), true);
    }

    /**
     * The async conversion signal is the exception's type: an exhausted
     * pool turns the failed no-wait attempt into a waiter, the
     * exhaustion never reaches the caller of {@code getConnectionAsync},
     * and the future is served by the connection that frees up while it
     * waits.
     */
    @Test
    void asyncBorrowConvertsTheExhaustionTypeIntoAWait() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.createPool();
        Connection held = ds.getConnection();

        Future<Connection> future = ds.getConnectionAsync();

        held.close();

        Connection served = future.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        Assertions.assertTrue(served.isValid(2),
                "the converted wait must hand out a live connection");
        served.close();
    }
}
