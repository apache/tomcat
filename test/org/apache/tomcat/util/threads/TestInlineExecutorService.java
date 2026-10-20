/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.threads;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

public class TestInlineExecutorService {

    /*
     * Tasks run by multiple threads can be running at the same time. The service must not report termination until
     * every running task has completed.
     */
    @Test
    public void testTerminationWaitsForAllRunningTasks() throws Exception {
        try (InlineExecutorService executor = new InlineExecutorService()) {
            CountDownLatch tasksStarted = new CountDownLatch(2);
            CountDownLatch firstTaskDone = new CountDownLatch(1);
            CountDownLatch releaseSecondTask = new CountDownLatch(1);
            CountDownLatch secondTaskDone = new CountDownLatch(1);

            Runnable firstTask = () -> {
                tasksStarted.countDown();
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    // Ignore
                }
                firstTaskDone.countDown();
            };
            Runnable secondTask = () -> {
                tasksStarted.countDown();
                try {
                    releaseSecondTask.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    // Ignore
                }
                secondTaskDone.countDown();
            };

            Thread firstThread = new Thread(() -> executor.execute(firstTask));
            Thread secondThread = new Thread(() -> executor.execute(secondTask));

            firstThread.start();
            secondThread.start();

            Assert.assertTrue("Tasks did not start", tasksStarted.await(10, TimeUnit.SECONDS));
            Assert.assertTrue("First task did not complete", firstTaskDone.await(10, TimeUnit.SECONDS));

            // The second task is still running at this point
            executor.shutdown();
            Assert.assertFalse("Terminated while a task was still running", executor.isTerminated());
            Assert.assertFalse("awaitTermination returned true while a task was still running",
                    executor.awaitTermination(100, TimeUnit.MILLISECONDS));

            releaseSecondTask.countDown();
            firstThread.join(TimeUnit.SECONDS.toMillis(10));
            secondThread.join(TimeUnit.SECONDS.toMillis(10));
            Assert.assertFalse("Second thread did not complete", secondThread.isAlive());

            Assert.assertTrue("Not terminated after all tasks completed", executor.isTerminated());
            Assert.assertTrue("awaitTermination returned false after termination",
                    executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }


    /*
     * A task that submits another task (which runs inline on the same thread) must leave the service in a state in
     * which it can terminate normally once both tasks have completed.
     */
    @Test
    public void testReentrantExecute() throws Exception {
        try (InlineExecutorService executor = new InlineExecutorService()) {
            executor.execute(() -> executor.execute(() -> {
                // Inner task. Does nothing.
            }));

            executor.shutdown();
            Assert.assertTrue("Not terminated after nested tasks completed", executor.isTerminated());
        }
    }


    /*
     * Shutting down a service with no running tasks terminates it. Threads already waiting for termination must be
     * woken rather than only noticing the termination when their timeout expires.
     */
    @Test
    public void testAwaitTerminationWakesOnShutdown() throws Exception {
        try (InlineExecutorService executor = new InlineExecutorService()) {
            CountDownLatch waiterReturned = new CountDownLatch(1);
            Boolean[] result = new Boolean[1];
            Thread waiter = new Thread(() -> {
                try {
                    result[0] = Boolean.valueOf(executor.awaitTermination(60, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    // Ignore
                }
                waiterReturned.countDown();
            });
            waiter.start();

            // Give the waiter thread time to start waiting
            Thread.sleep(100);

            executor.shutdown();

            Assert.assertTrue("Waiting thread was not woken", waiterReturned.await(10, TimeUnit.SECONDS));
            Assert.assertEquals(Boolean.TRUE, result[0]);
            waiter.join(TimeUnit.SECONDS.toMillis(10));
        }
    }


    /*
     * Tasks must be rejected once the service has been shut down.
     */
    @Test
    public void testRejectAfterShutdown() throws Exception {
        try (InlineExecutorService executor = new InlineExecutorService()) {
            executor.shutdown();

            Assert.assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {
                // Does nothing.
            }));
        }
    }
}
