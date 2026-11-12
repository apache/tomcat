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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.TestAsyncBorrowCancelRace.ParkingValidator;

/**
 * Expected behaviour of a timed asynchronous borrow whose calling thread is
 * interrupted while the connection it produced is being set up.
 */
class TestAsyncBorrowInterrupt extends JdbcPoolBaseTest {

    @Test
    void interruptingTheTimedGetDuringSetupReturnsTheProducedConnection() throws Exception {
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
        ConnectionPool pool = ds.getPool();

        Connection holder = ds.getConnection();
        Future<Connection> future = ds.getConnectionAsync();
        Assertions.assertFalse(future.isDone(), "the poll must wait for the held connection");

        CountDownLatch insideSetup = new CountDownLatch(1);
        CountDownLatch resumeSetup = new CountDownLatch(1);
        validator.insideSetup = insideSetup;
        validator.resumeSetup = resumeSetup;

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                future.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (Throwable x) {
                failure.set(x);
            }
        }, "interrupted-async-borrower");
        caller.setDaemon(true);
        caller.start();

        holder.close();
        Assertions.assertTrue(insideSetup.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "the borrower must have taken the returned connection and entered setup");
        pool.resetStats();

        caller.interrupt();
        caller.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(caller.isAlive(), "the interrupted get() must return");
        Assertions.assertTrue(failure.get() instanceof InterruptedException,
                "the timed get() must report InterruptedException, got " + failure.get());

        resumeSetup.countDown();
        awaitUntil(() -> ds.getActive() == 0 && ds.getIdle() == 1);

        Assertions.assertTrue(future.isDone(), "an abandoned setup must complete the future");
        Assertions.assertEquals(1, ds.getSize(), "the connection must remain in the pool");
        Assertions.assertEquals(0, ds.getActive(), "the interrupted caller must not keep the connection busy");
        Assertions.assertEquals(1, ds.getIdle(), "the produced connection must be returned to the pool");
        Assertions.assertEquals(0, pool.getBorrowedCount(), "the interrupted setup never reached a caller");
        Assertions.assertEquals(0, pool.getReturnedCount(), "the interrupted setup's cleanup is not a borrower's return");
    }
}
