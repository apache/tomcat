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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.TestAsyncBorrowCancelRace.ParkingValidator;

/**
 * Expected behaviour of an asynchronous borrow whose timed getter abandons a
 * setup that is still parked inside a driver call, while another getter waits
 * for the same setup.
 */
class TestAsyncBorrowAbandonedSetup extends JdbcPoolBaseTest {

    @Test
    void abandoningASetupWakesLaterGettersInsteadOfParkingThemOnTheSetupLatch()
            throws Exception {
        ParkingValidator validator = new ParkingValidator();
        DataSource ds = newDataSource();
        ds.getPoolProperties().setFairQueue(true);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxIdle(1);
        ds.getPoolProperties().setTestOnBorrow(true);
        ds.getPoolProperties().setValidationInterval(0);
        ds.getPoolProperties().setValidator(validator);
        ds.createPool();

        Connection holder = ds.getConnection();
        Future<Connection> future = ds.getConnectionAsync();

        CountDownLatch insideSetup = new CountDownLatch(1);
        CountDownLatch resumeSetup = new CountDownLatch(1);
        validator.insideSetup = insideSetup;
        validator.resumeSetup = resumeSetup;

        AtomicReference<Throwable> timedFailure = new AtomicReference<>();
        Thread timedCaller = new Thread(() -> {
            try {
                future.get(500, TimeUnit.MILLISECONDS);
            } catch (Throwable x) {
                timedFailure.set(x);
            }
        }, "timed-async-borrower");
        timedCaller.setDaemon(true);
        timedCaller.start();

        holder.close();
        Assertions.assertTrue(insideSetup.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "the timed borrower must have taken the returned connection and entered setup");

        AtomicReference<Throwable> waitingFailure = new AtomicReference<>();
        CountDownLatch waitingFinished = new CountDownLatch(1);
        Thread waitingCaller = new Thread(() -> {
            try {
                future.get();
            } catch (Throwable x) {
                waitingFailure.set(x);
            } finally {
                waitingFinished.countDown();
            }
        }, "waiting-async-borrower");
        waitingCaller.setDaemon(true);
        waitingCaller.start();

        timedCaller.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(timedCaller.isAlive(), "the timed get() must return");
        Assertions.assertTrue(timedFailure.get() instanceof TimeoutException,
                "the timed get() must report TimeoutException, got " + timedFailure.get());

        // The setup is still parked in the validator. Abandonment must wake the second getter
        // rather than leave it waiting for the parked worker.
        Assertions.assertTrue(waitingFinished.await(3000, TimeUnit.MILLISECONDS),
                "a getter waiting on an abandoned setup must not block on the setup latch");
        Assertions.assertTrue(waitingFailure.get() instanceof ExecutionException
                        && waitingFailure.get().getCause() instanceof TimeoutException,
                "the waiting getter must report the earlier timeout, got " + waitingFailure.get());
        Assertions.assertTrue(future.isDone(), "an abandoned setup must complete the future");

        resumeSetup.countDown();
        awaitUntil(() -> ds.getActive() == 0 && ds.getIdle() == 1);
        Assertions.assertEquals(1, ds.getSize(), "the connection must remain in the pool");
        Assertions.assertEquals(0, ds.getActive(), "no abandoned setup may leave the connection busy");
        Assertions.assertEquals(1, ds.getIdle(), "the produced connection must be returned to the pool");
    }
}
