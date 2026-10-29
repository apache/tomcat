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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Bugzilla 56805: borrowConnection() takes one connection from the head of
 * the idle queue and only advances to the next one after it has finished
 * working on it, so a borrower that polled the head connection had to wait
 * out whatever the PoolCleaner was doing to that connection while the
 * cleaner held its per-connection write lock - with testWhileIdle on, the
 * idle validation itself, whose duration belongs to the database and is
 * unbounded when validationQueryTimeout is unset. The second, immediately
 * available idle connection behind the head one was out of reach: classic
 * head-of-line blocking of getConnection() behind the PoolCleaner.
 *
 * <p>The idle test now claims each connection out of the idle queue before
 * the slow validation, so a published idle connection is never write-locked
 * across arbitrary SQL and the connection under validation is not even
 * pollable. This test pins that away: with the cleaner parked inside the
 * idle validation of the connection at the head of the queue, a borrower
 * must still be handed the second idle connection right away.
 *
 * <p>The construction is fully deterministic rather than timing based. The
 * idle queue hands out the most recently returned connection first, so the
 * head is known by the return order. A large validationInterval gates
 * idle validation per connection on its lastValidated timestamp, which
 * pins which of the two connections the sweep will validate: only the one
 * whose timestamp is rewound past the interval passes the gate, and the
 * sweep parks inside that connection's validation on a latch - the latch,
 * not a sleep, is the correctness signal throughout.
 */
class TestIdleValidationHeadOfLine extends JdbcPoolBaseTest {

    /*
     * Validation that parks the first idle validation it is asked for on a
     * latch for an unbounded time - what a hanging validationQuery looks
     * like from the pool's side. All other validations pass through.
     */
    static class ParkingValidator implements Validator {
        final AtomicBoolean firstIdleValidation = new AtomicBoolean();
        /* Announced once the sweep is inside validate(), released by the test. */
        final CountDownLatch insideValidation = new CountDownLatch(1);
        final CountDownLatch releaseValidation = new CountDownLatch(1);
        final CountDownLatch validationFinished = new CountDownLatch(1);

        @Override
        public boolean validate(Connection connection, int validateAction) {
            if (validateAction == PooledConnection.VALIDATE_IDLE &&
                    firstIdleValidation.compareAndSet(false, true)) {
                insideValidation.countDown();
                try {
                    releaseValidation.await(AWAIT_TIMEOUT_MS * 4, TimeUnit.MILLISECONDS);
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                }
                validationFinished.countDown();
            }
            return true;
        }
    }

    @Test
    void aBorrowerTakesTheNextIdleConnectionWhileTheCleanerValidatesTheHead() throws Exception {
        ParkingValidator validator = new ParkingValidator();
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        // exactly the two connections the pool holds: a borrower cannot
        // route around the queue by creating a third one
        ds.getPoolProperties().setMaxActive(2);
        // the reported configuration: sweeper on (which makes the
        // per-connection lock a real write lock) with testWhileIdle
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(200);
        ds.getPoolProperties().setTestWhileIdle(true);
        // a gate wide enough that only the timestamp the test rewinds
        // below opens the way for an idle validation, so the sweep always
        // works on the connection the test picked, whenever it runs
        ds.getPoolProperties().setValidationInterval(60000);
        ds.getPoolProperties().setValidator(validator);
        ds.getPoolProperties().setMaxWait(15000);
        ds.createPool();

        Connection head = ds.getConnection();
        Connection second = ds.getConnection();
        setSessionMarker(head, 11);
        setSessionMarker(second, 22);
        PooledConnection headPc = head.unwrap(PooledConnection.class);
        PooledConnection secondPc = second.unwrap(PooledConnection.class);
        long now = System.currentTimeMillis();
        // Gate both connections' idle validation for the next minute: no
        // sweep can touch either with its validation while the test sets
        // the queue up.
        headPc.setLastValidated(now);
        secondPc.setLastValidated(now);
        // The idle queue hands out the most recently returned connection
        // first, so returning `second` before `head` puts `head` at the
        // head of the queue where a borrower polls - the exact position
        // the Bugzilla scenario blocks a borrower in.
        second.close();
        head.close();
        // Open the gate for the head connection only: the sweep will
        // validate exactly that one and park on the validator's latch.
        headPc.setLastValidated(now - 120000);

        AtomicReference<Connection> handed = new AtomicReference<>();
        AtomicReference<Throwable> borrowFailure = new AtomicReference<>();
        Thread borrower = new Thread(() -> {
            try {
                handed.set(ds.getConnection());
            } catch (Throwable t) {
                borrowFailure.set(t);
            }
        }, "borrower");
        try {
            Assertions.assertTrue(validator.insideValidation.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                    "the pool cleaner never reached the idle validation of the head connection");

            // The connection under validation is claimed out of the queue, so
            // it was never even pollable: only the second connection is left
            // published. Pre-fix, the validated head stayed published and
            // both connections counted as idle.
            Assertions.assertEquals(1, ds.getPool().getIdle(),
                    "the connection under validation must be claimed out of the idle queue while it is tested");

            borrower.start();
            borrower.join(AWAIT_TIMEOUT_MS);
            // The head's validation is still parked - the latch has not
            // been released - so this join only ended because the borrow
            // finished on its own. Pre-fix, the borrower polled the head
            // connection out of the queue and was blocked on its lock
            // here until the validation was let go, ignoring the free
            // second connection behind it.
            Assertions.assertFalse(borrower.isAlive(),
                    "the borrower stayed blocked on the head connection's idle validation");
            Assertions.assertNull(borrowFailure.get(), "the borrow failed");

            // Not just any connection: the borrower took the second idle
            // one - the head is still parked in validation, and creating
            // a third is out of the maxActive budget.
            Connection borrowed = handed.get();
            Assertions.assertNotNull(borrowed);
            Assertions.assertEquals(22, sessionMarker(borrowed).intValue(),
                    "the borrower must have been handed the second idle connection, not the head's session");
            Assertions.assertEquals(0, ds.getPool().getIdle(),
                    "the second connection is borrowed and the head is still parked in validation");

            // Let the parked validation finish: the sweep hands the head
            // back and the borrower can put the second connection back too.
            validator.releaseValidation.countDown();
            Assertions.assertTrue(validator.validationFinished.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                    "the parked validation never finished after being released");
            borrowed.close();
            awaitUntil(() -> ds.getPool().getIdle() == 2);
            Assertions.assertEquals(2, ds.getPool().getSize());
            awaitSessions(2);
        } finally {
            validator.releaseValidation.countDown();
            borrower.join(AWAIT_TIMEOUT_MS);
            validator.validationFinished.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            Connection borrowed = handed.get();
            if (borrowed != null) {
                try {
                    borrowed.close();
                } catch (Exception ignore) {
                    // best effort; the @AfterEach close and the @AfterAll
                    // server stop are the hard reset
                }
            }
        }
        ds.close();
        awaitSessions(0);
    }
}
