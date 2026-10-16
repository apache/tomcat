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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

public class TestTaskQueue {

    private static class CountingTaskQueue extends TaskQueue {

        private final AtomicInteger pollCount = new AtomicInteger();

        @Override
        public Runnable poll(long timeout, TimeUnit unit) throws InterruptedException {
            pollCount.incrementAndGet();
            return super.poll(timeout, unit);
        }
    }


    /*
     * A thread that is due to be stopped waits with poll() rather than take() so the stop check runs periodically. If
     * the keep-alive time is zero, a poll() with a zero timeout can not block and the worker spins while the queue is
     * empty. The wait must be bounded to a non-zero interval instead.
     */
    @Test
    public void testTakeWithZeroKeepAliveDoesNotSpin() throws Exception {
        CountingTaskQueue queue = new CountingTaskQueue();
        /*
         * Two workers so that one stops itself (the first to claim the renewal slot) while the other remains due for
         * renewal and waiting. A long renewal delay keeps the waiting thread renewing rather than stopping during the
         * observation window below.
         */
        ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, queue,
                new TaskThreadFactory("test-take-zero", false, Thread.NORM_PRIORITY));
        queue.setParent(executor);
        executor.setThreadRenewalDelay(10000);

        try {
            CountDownLatch executed = new CountDownLatch(4);
            executor.execute(executed::countDown);
            executor.execute(executed::countDown);
            // Wait for both workers to be created and to enter take()
            Thread.sleep(200);

            // Mark the (older) worker threads as due to be stopped
            executor.contextStopping();
            executor.execute(executed::countDown);
            executor.execute(executed::countDown);
            // The workers complete these tasks and then re-enter take() which, since the threads are due to be
            // stopped, switches to poll() with a bounded timeout
            Assert.assertTrue(executed.await(5, TimeUnit.SECONDS));

            int pollsAfterTask = queue.pollCount.get();
            Thread.sleep(500);
            int spinIterations = queue.pollCount.get() - pollsAfterTask;

            // Without the bounded timeout this is orders of magnitude larger (millions in this window)
            Assert.assertTrue("Unexpected poll count while idle: " + spinIterations, spinIterations < 100);

            // Both workers must have entered the renewal wait at least once
            Assert.assertTrue("Renewal wait was not exercised", queue.pollCount.get() >= 2);

            // The workers must still be able to process tasks
            CountDownLatch processed = new CountDownLatch(1);
            executor.execute(processed::countDown);
            Assert.assertTrue(processed.await(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            Assert.assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
