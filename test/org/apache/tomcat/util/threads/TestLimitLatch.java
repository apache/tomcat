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
package org.apache.tomcat.util.threads;

import org.junit.Assert;
import org.junit.Test;

public class TestLimitLatch {

    // This should be plenty of time, even on slow systems.
    private static final long THREAD_WAIT_TIME = 60000;

    @Test
    public void testNoThreads() throws Exception {
        LimitLatch latch = new LimitLatch(0);
        Assert.assertFalse("No threads should be waiting", latch.hasQueuedThreads());
    }

    @Test
    public void testOneThreadNoWait() throws Exception {
        LimitLatch latch = new LimitLatch(1);
        Object lock = new Object();
        checkWaitingThreadCount(latch, 0);
        TestThread testThread = new TestThread(latch, lock);
        testThread.start();
        if (!waitForThreadToStart(testThread)) {
            Assert.fail("Test thread did not start");
        }
        checkWaitingThreadCount(latch, 0);
        if (!waitForThreadToStop(testThread, lock)) {
            Assert.fail("Test thread did not stop");
        }
        checkWaitingThreadCount(latch, 0);
    }

    @Test
    public void testOneThreadWaitCountDown() throws Exception {
        LimitLatch latch = new LimitLatch(1);
        Object lock = new Object();
        checkWaitingThreadCount(latch, 0);
        TestThread testThread = new TestThread(latch, lock);
        latch.countUpOrAwait();
        testThread.start();
        if (!waitForThreadToStart(testThread)) {
            Assert.fail("Test thread did not start");
        }
        checkWaitingThreadCount(latch, 1);
        latch.countDown();
        if (!waitForThreadToStop(testThread, lock)) {
            Assert.fail("Test thread did not stop");
        }
        checkWaitingThreadCount(latch, 0);
    }

    @Test
    public void testOneRelease() throws Exception {
        LimitLatch latch = new LimitLatch(1);
        Object lock = new Object();
        checkWaitingThreadCount(latch, 0);
        TestThread testThread = new TestThread(latch, lock);
        latch.countUpOrAwait();
        testThread.start();
        if (!waitForThreadToStart(testThread)) {
            Assert.fail("Test thread did not start");
        }
        checkWaitingThreadCount(latch, 1);
        latch.releaseAll();
        if (!waitForThreadToStop(testThread, lock)) {
            Assert.fail("Test thread did not stop");
        }
        checkWaitingThreadCount(latch, 0);
    }

    @Test
    public void testTenWait() throws Exception {
        LimitLatch latch = new LimitLatch(10);
        Object lock = new Object();
        checkWaitingThreadCount(latch, 0);

        TestThread[] testThreads = new TestThread[30];
        for (int i = 0; i < 30; i++) {
            testThreads[i] = new TestThread(latch, lock);
            testThreads[i].start();
        }

        // Should have 10 threads in stage 2 and 20 in stage 1

        for (int i = 0; i < 30; i++) {
            if (!waitForThreadToStart(testThreads[i])) {
                Assert.fail("Test thread [" + i + "] did not start");
            }
        }

        if (!waitForThreadsToReachStage(testThreads, 20, 10, 0)) {
            Assert.fail("Failed at 20-10-00");
        }
        checkWaitingThreadCount(latch, 20);

        synchronized (lock) {
            lock.notifyAll();
        }

        if (!waitForThreadsToReachStage(testThreads, 10, 10, 10)) {
            Assert.fail("Failed at 10-10-10");
        }
        checkWaitingThreadCount(latch, 10);

        synchronized (lock) {
            lock.notifyAll();
        }

        if (!waitForThreadsToReachStage(testThreads, 0, 10, 20)) {
            Assert.fail("Failed at 00-10-20");
        }
        checkWaitingThreadCount(latch, 0);

        synchronized (lock) {
            lock.notifyAll();
        }

        if (!waitForThreadsToReachStage(testThreads, 0, 0, 30)) {
            Assert.fail("Failed at 00-00-30");
        }
    }

    private boolean waitForThreadToStart(TestThread t) throws InterruptedException {
        long wait = 0;
        while (t.getStage() == 0 && wait < THREAD_WAIT_TIME) {
            Thread.sleep(100);
            wait += 100;
        }
        return t.getStage() > 0;
    }

    private boolean waitForThreadToStop(TestThread t, Object lock) throws InterruptedException {
        long wait = 0;
        while (t.getStage() < 3 && wait < THREAD_WAIT_TIME) {
            Thread.sleep(100);
            wait += 100;
            synchronized (lock) {
                lock.notifyAll();
            }
        }
        return t.getStage() == 3;
    }

    private void checkWaitingThreadCount(LimitLatch latch, int target) throws InterruptedException {
        long wait = 0;
        while (latch.getQueuedThreads().size() != target && wait < THREAD_WAIT_TIME) {
            Thread.sleep(100);
            wait += 100;
        }
        Assert.assertEquals(target,  latch.getQueuedThreads().size());
    }

    private boolean waitForThreadsToReachStage(TestThread[] testThreads,
            int stage1Target, int stage2Target, int stage3Target) throws InterruptedException {

        long wait = 0;

        int stage1 = 0;
        int stage2 = 0;
        int stage3 = 0;

        while((stage1 != stage1Target || stage2 != stage2Target || stage3 != stage3Target) &&
                wait < THREAD_WAIT_TIME) {
            stage1 = 0;
            stage2 = 0;
            stage3 = 0;
            for (TestThread testThread : testThreads) {
                switch(testThread.getStage()){
                    case 1:
                        stage1++;
                        break;
                    case 2:
                        stage2++;
                        break;
                    case 3:
                        stage3++;
                        break;
                }
            }
            Thread.sleep(100);
            wait += 100;
        }
        return stage1 == stage1Target && stage2 == stage2Target && stage3 == stage3Target;
    }

    private static class TestThread extends Thread {

        private final Object lock;
        private final LimitLatch latch;
        private volatile int stage = 0;

        public TestThread(LimitLatch latch, Object lock) {
            this.latch = latch;
            this.lock = lock;
        }

        public int getStage() {
            return stage;
        }

        @Override
        public void run() {
            try {
                stage = 1;
                latch.countUpOrAwait();
                stage = 2;
                if (lock != null) {
                    synchronized (lock) {
                        lock.wait();
                    }
                }
                latch.countDown();
                stage = 3;
            } catch (InterruptedException x) {
                x.printStackTrace();
            }
        }
    }
}
