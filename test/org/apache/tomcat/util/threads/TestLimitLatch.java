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

import junit.framework.TestCase;

public class TestLimitLatch extends TestCase {

    private volatile LimitLatch latch = null;

    @Override
    public void tearDown() {
        LimitLatch temp = latch;
        if (temp!=null) temp.releaseAll();
        latch = null;
    }

    public void testNoThreads() throws Exception {
        latch = new LimitLatch(0);
        assertEquals("No threads should be waiting", false,
                latch.hasQueuedThreads());
    }

    public void testOneThreadNoWait() throws Exception {
        latch = new LimitLatch(1);
        assertEquals("No threads should be waiting", false,
                latch.hasQueuedThreads());
        Thread testThread = new TestThread();
        testThread.start();
        Thread.sleep(50);
        assertEquals("0 threads should be waiting", 0,
                latch.getQueuedThreads().size());
        latch.countUpOrAwait();
        Thread.sleep(50);
        assertEquals("No threads should be waiting", false,
                latch.hasQueuedThreads());
    }

    public void testOneThreadWaitCountUp() throws Exception {
        latch = new LimitLatch(1);
        assertEquals("No threads should be waiting", false,
                latch.hasQueuedThreads());
        Thread testThread = new TestThread();
        latch.countUpOrAwait();
        testThread.start();
        Thread.sleep(50);
        assertEquals("1 threads should be waiting", 1,
                latch.getQueuedThreads().size());
        latch.countDown();
        Thread.sleep(50);
        assertEquals("No threads should be waiting", false,
                latch.hasQueuedThreads());
    }

    public void testOneRelease() throws Exception {
        latch = new LimitLatch(1);
        assertEquals("No threads should be waiting", false,
                latch.hasQueuedThreads());
        Thread testThread = new TestThread();
        latch.countUpOrAwait();
        testThread.start();
        Thread.sleep(50);
        assertEquals("1 threads should be waiting", 1,
                latch.getQueuedThreads().size());
        latch.releaseAll();
        Thread.sleep(50);
        assertEquals("No threads should be waiting", false,
                latch.hasQueuedThreads());
    }

    public void testTenWait() throws Exception {
        latch = new LimitLatch(10);
        assertEquals("No threads should be waiting", false,
                latch.hasQueuedThreads());
        Thread[] testThread = new TestThread[30];
        for (int i = 0; i < 30; i++) {
            testThread[i] = new TestThread(1000);
            testThread[i].start();
        }
        Thread.sleep(50);
        assertEquals("20 threads should be waiting", 20,
                latch.getQueuedThreads().size());
        Thread.sleep(1000);
        assertEquals("10 threads should be waiting", 10,
                latch.getQueuedThreads().size());
        Thread.sleep(1000);
        assertEquals("No threads should be waiting", false,
                latch.hasQueuedThreads());
    }

    private class TestThread extends Thread {
        
        private int holdTime;
        
        public TestThread() {
            this(100);
        }
        
        public TestThread(int holdTime) {
            this.holdTime = holdTime;
        }
 
        @Override
        public void run() {
            try {
                latch.countUpOrAwait();
                Thread.sleep(holdTime);
                latch.countDown();
            } catch (InterruptedException x) {
                x.printStackTrace();
            }
        }
    }
}
