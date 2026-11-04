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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Test;

public class TestThreadPoolExecutor {

    /*
     * The submitted task count is used by TaskQueue.offer() to decide if a task should be queued or a new thread should
     * be created. A task that is counted when submitted but never reaches afterExecute() leaves the count permanently
     * inflated.
     */

    private static ThreadPoolExecutor createPool(ThreadPoolExecutor.RejectedExecutionHandler handler) {
        // Single thread with room for a single queued task. The task following
        // the queued one is rejected.
        return new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1),
                Executors.defaultThreadFactory(), handler);
    }

    private static Runnable blockingTask(String name, CountDownLatch release) {
        return blockingTask(name, null, release);
    }

    private static Runnable blockingTask(String name, CountDownLatch started, CountDownLatch release) {
        return new Runnable() {
            @Override
            public void run() {
                if (started != null) {
                    started.countDown();
                }
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    /*
     * Waits until the running task has started. getActiveCount() is not usable for this: it is approximate, and a
     * worker that the pool constructor prestarted counts as active before it has taken its first task.
     */
    private static void awaitStarted(CountDownLatch started) throws InterruptedException {
        Assert.assertTrue(started.await(5, TimeUnit.SECONDS));
    }

    private static void awaitSubmittedCount(ThreadPoolExecutor pool, int expected) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (pool.getSubmittedCount() != expected && System.currentTimeMillis() < end) {
            Thread.sleep(20);
        }
        Assert.assertEquals(expected, pool.getSubmittedCount());
    }

    /*
     * A built-in rejection policy that does not throw discards the task or runs it in the calling thread. Either way
     * the task never reaches afterExecute(), so the submitted count must be decremented when the handler returns.
     */
    @Test
    public void testNonThrowingRejectionHandlerKeepsSubmittedCount() throws Exception {
        // Test both policies, including inherited and delegated handling methods.
        for (ThreadPoolExecutor.RejectedExecutionHandler handler : new ThreadPoolExecutor.RejectedExecutionHandler[] {
                new ThreadPoolExecutor.DiscardPolicy(), new ThreadPoolExecutor.CallerRunsPolicy(),
                new ThreadPoolExecutor.DiscardPolicy() {}, new ThreadPoolExecutor.CallerRunsPolicy() {} }) {
            boolean expectRunInCaller = handler instanceof ThreadPoolExecutor.CallerRunsPolicy;
            doTestNonThrowingRejectionHandler(handler, expectRunInCaller);
            doTestNonThrowingRejectionHandler((task, executor) -> handler.rejectedExecution(task, executor),
                    expectRunInCaller);
        }
    }


    private void doTestNonThrowingRejectionHandler(ThreadPoolExecutor.RejectedExecutionHandler handler,
            boolean expectRunInCaller) throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolExecutor pool = createPool(handler);
        try {
            CountDownLatch started = new CountDownLatch(1);
            pool.execute(blockingTask("running", started, release));
            awaitStarted(started);
            pool.execute(blockingTask("queued", release));
            AtomicBoolean ran = new AtomicBoolean();
            pool.execute(() -> ran.set(true)); // rejected: discarded or run by this thread
            // Only the running and the queued task are still outstanding.
            Assert.assertEquals(handler.getClass().getName(), 2, pool.getSubmittedCount());
            Assert.assertEquals(handler.getClass().getName(), Boolean.valueOf(expectRunInCaller),
                    Boolean.valueOf(ran.get()));
            release.countDown();
            awaitSubmittedCount(pool, 0);
        } finally {
            pool.shutdownNow();
            Assert.assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }


    /*
     * A rejection handler may enqueue the task instead of discarding it or running it itself. Even a subclass of a
     * built-in policy may do this, so returning from the handler must not release the queued task's count.
     */
    @Test
    public void testQueuingRejectionHandlerKeepsSubmittedCount() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);
        CountDownLatch thirdCompleted = new CountDownLatch(1);
        TaskQueue queue = new TaskQueue(1);
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, queue,
                Executors.defaultThreadFactory(), new ThreadPoolExecutor.CallerRunsPolicy() {
                    @Override
                    public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
                        // Let the worker take the queued task so the handler can enqueue the rejected task.
                        releaseFirst.countDown();
                        try {
                            awaitStarted(secondStarted);
                            executor.getQueue().put(task);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RejectedExecutionException(e);
                        }
                    }
                });
        queue.setParent(pool);
        try {
            pool.execute(blockingTask("first", firstStarted, releaseFirst));
            awaitStarted(firstStarted);
            pool.execute(blockingTask("second", secondStarted, releaseSecond));
            pool.execute(thirdCompleted::countDown);

            Assert.assertEquals("One running task and one task queued by the handler", 2, pool.getSubmittedCount());
            Assert.assertEquals(1, queue.size());
            releaseSecond.countDown();
            Assert.assertTrue(thirdCompleted.await(5, TimeUnit.SECONDS));
            awaitSubmittedCount(pool, 0);
        } finally {
            releaseFirst.countDown();
            releaseSecond.countDown();
            pool.shutdownNow();
            Assert.assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }


    @Test
    public void testDiscardOldestPolicyKeepsSubmittedCount() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicBoolean discardedTaskRan = new AtomicBoolean();
        ThreadPoolExecutor pool = createPool(new ThreadPoolExecutor.DiscardOldestPolicy());
        try {
            pool.execute(blockingTask("running", started, release));
            awaitStarted(started);
            pool.execute(() -> discardedTaskRan.set(true));
            pool.execute(completed::countDown);
            // The original queued task was discarded. Only the running task and its replacement remain.
            Assert.assertEquals(2, pool.getSubmittedCount());
            release.countDown();
            Assert.assertTrue(completed.await(5, TimeUnit.SECONDS));
            awaitSubmittedCount(pool, 0);
            Assert.assertFalse(discardedTaskRan.get());
        } finally {
            release.countDown();
            pool.shutdownNow();
            Assert.assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }


    /*
     * A null command is rejected with a NullPointerException before it can be counted.
     */
    @Test
    public void testNullCommandKeepsSubmittedCount() throws Exception {
        ThreadPoolExecutor pool = createPool(new ThreadPoolExecutor.AbortPolicy());
        try {
            try {
                pool.execute(null);
                Assert.fail("Expected NullPointerException");
            } catch (NullPointerException e) {
                // Expected.
            }
            Assert.assertEquals(0, pool.getSubmittedCount());
        } finally {
            pool.shutdownNow();
        }
    }

    /*
     * A task removed from the queue is never executed and never reaches afterExecute(), so the removal must decrement
     * the submitted count.
     */
    @Test
    public void testRemoveKeepsSubmittedCount() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolExecutor pool = createPool(new ThreadPoolExecutor.AbortPolicy());
        try {
            Runnable queued = blockingTask("queued", release);
            CountDownLatch started = new CountDownLatch(1);
            pool.execute(blockingTask("running", started, release));
            awaitStarted(started);
            pool.execute(queued);
            Assert.assertTrue(pool.remove(queued));
            // Only the running task is still outstanding.
            Assert.assertEquals(1, pool.getSubmittedCount());
            release.countDown();
            awaitSubmittedCount(pool, 0);
        } finally {
            pool.shutdownNow();
        }
    }

    /*
     * A cancelled future removed by purge() is never executed and never reaches afterExecute(), so the removal must
     * decrement the submitted count.
     */
    @Test
    public void testPurgeKeepsSubmittedCount() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolExecutor pool = createPool(new ThreadPoolExecutor.AbortPolicy());
        try {
            CountDownLatch started = new CountDownLatch(1);
            pool.execute(blockingTask("running", started, release));
            awaitStarted(started);
            Future<?> queued = pool.submit(blockingTask("queued", release));
            // The submitted future is in the queue since the only worker is busy.
            Assert.assertTrue(queued.cancel(false));
            pool.purge();
            // Only the running task is still outstanding.
            Assert.assertEquals(1, pool.getSubmittedCount());
            release.countDown();
            awaitSubmittedCount(pool, 0);
        } finally {
            pool.shutdownNow();
        }
    }


    /*
     * A worker can dequeue a cancelled future after purge() has selected it but before purge() removes it. In that
     * case afterExecute() accounts for the future and purge() must not decrement the count again.
     */
    @Test
    public void testPurgeRacingWithWorkerKeepsSubmittedCount() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);
        TaskQueue queue = new TaskQueue();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, queue,
                Executors.defaultThreadFactory());
        queue.setParent(pool);
        FutureTask<Void> cancelled = new FutureTask<Void>(() -> null) {
            @Override
            public boolean isCancelled() {
                // The worker processes the cancelled future before starting the second task.
                releaseFirst.countDown();
                try {
                    awaitStarted(secondStarted);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
                return super.isCancelled();
            }
        };
        try {
            pool.execute(blockingTask("first", firstStarted, releaseFirst));
            awaitStarted(firstStarted);
            Assert.assertTrue(cancelled.cancel(false));
            pool.execute(cancelled);
            pool.execute(blockingTask("second", secondStarted, releaseSecond));
            pool.purge();

            Assert.assertEquals("Only the second task is outstanding", 1, pool.getSubmittedCount());
            releaseSecond.countDown();
            awaitSubmittedCount(pool, 0);
        } finally {
            releaseFirst.countDown();
            releaseSecond.countDown();
            pool.shutdownNow();
            Assert.assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }


    /*
     * If beforeExecute() throws, the worker dies without processing the task and without calling afterExecute(), so the
     * submitted count must be decremented for the lost task.
     */
    @Test
    public void testBeforeExecuteThrowKeepsSubmittedCount() throws Exception {
        CountDownLatch threw = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        class BeforeExecutorThrows extends ThreadPoolExecutor {
            BeforeExecutorThrows() {
                super(1, 1, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), Executors.defaultThreadFactory(),
                        new AbortPolicy());
            }

            @Override
            protected void beforeExecute(Thread t, Runnable r) {
                if ("marker".equals(r.toString())) {
                    threw.countDown();
                    throw new IllegalStateException("Test beforeExecute failure");
                }
            }
        }

        BeforeExecutorThrows pool = new BeforeExecutorThrows();
        try {
            pool.execute(blockingTask("marker", release));
            // The worker should die without executing the task.
            Assert.assertTrue(threw.await(5, TimeUnit.SECONDS));
            // The task is lost with the worker, so nothing is outstanding.
            awaitSubmittedCount(pool, 0);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    /*
     * If the pool is shut down after a task has been queued but before execute() re-checks the pool state, execute()
     * removes the task again and rejects it. The removal can empty the queue of a shut down pool that has no workers,
     * so it must trigger the transition to terminated since nothing else will.
     */
    @Test
    public void testRollbackOfQueuedTaskDuringShutdownTerminatesPool() throws Exception {
        class ShutdownAfterOfferQueue extends LinkedBlockingQueue<Runnable> {
            private static final long serialVersionUID = 1L;
            private transient volatile ThreadPoolExecutor pool;

            @Override
            public boolean offer(Runnable r) {
                boolean result = super.offer(r);
                // Shut down while the task is queued, as a concurrent shutdown() call could
                pool.shutdown();
                return result;
            }
        }

        ShutdownAfterOfferQueue queue = new ShutdownAfterOfferQueue();
        // No core threads so there is no worker to terminate the pool once the queue has been emptied
        ThreadPoolExecutor pool = new ThreadPoolExecutor(0, 1, 60, TimeUnit.SECONDS, queue,
                Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
        queue.pool = pool;
        try {
            Assert.assertThrows(RejectedExecutionException.class, () -> pool.execute(() -> {
                // Must not run
            }));
            Assert.assertTrue("Pool did not terminate", pool.awaitTermination(5, TimeUnit.SECONDS));
            Assert.assertEquals(0, pool.getSubmittedCount());
        } finally {
            pool.shutdownNow();
        }
    }


    /*
     * Tasks that complete normally or that throw must each decrement the submitted count exactly once.
     */
    @Test
    public void testTaskCompletionDecrementsOnce() throws Exception {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(2, 2, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(20),
                Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
        try {
            int taskCount = 10;
            CountDownLatch done = new CountDownLatch(taskCount);
            for (int i = 0; i < taskCount; i++) {
                final int index = i;
                pool.execute(() -> {
                    done.countDown();
                    if (index % 2 == 0) {
                        throw new IllegalStateException("Test task failure " + index);
                    }
                });
            }
            Assert.assertTrue(done.await(5, TimeUnit.SECONDS));
            awaitSubmittedCount(pool, 0);
            Assert.assertTrue(pool.getSubmittedCount() >= 0);
        } finally {
            pool.shutdownNow();
        }
    }
}
