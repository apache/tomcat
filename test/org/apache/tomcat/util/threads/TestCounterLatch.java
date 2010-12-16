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

public class TestCounterLatch extends TestCase {

    private volatile CounterLatch latch = null;

    public void setUp() {

    }

    public void tearDown() {
        CounterLatch temp = latch;
        if (temp!=null) temp.releaseAll();
        latch = null;
    }

    public void testNoThreads() throws Exception {
        latch = new CounterLatch(0,0);
        assertEquals("No threads should be waiting", false, latch.hasQueuedThreads());
    }

    public void testOneThreadNoWait() throws Exception {
        latch = new CounterLatch(0,1);
        assertEquals("No threads should be waiting", false, latch.hasQueuedThreads());
        Thread testThread = new Thread() {
            public void run() {
                try {
                    latch.await();
                } catch (InterruptedException x) {
                    x.printStackTrace();
                }
            }
        };
        testThread.start();
        Thread.sleep(50);
        assertEquals("0 threads should be waiting", 0, latch.getQueuedThreads().size());
        latch.countUp();
        Thread.sleep(50);
        assertEquals("No threads should be waiting", false, latch.hasQueuedThreads());
    }

    public void testOneThreadWaitCountUp() throws Exception {
        latch = new CounterLatch(0,1);
        assertEquals("No threads should be waiting", false, latch.hasQueuedThreads());
        Thread testThread = new Thread() {
            public void run() {
                try {
                    latch.await();
                } catch (InterruptedException x) {
                    x.printStackTrace();
                }
            }
        };
        latch.countUp();
        testThread.start();
        Thread.sleep(50);
        assertEquals("1 threads should be waiting", 1, latch.getQueuedThreads().size());
        latch.countUp();
        Thread.sleep(50);
        assertEquals("No threads should be waiting", false, latch.hasQueuedThreads());
    }

    public void testOneThreadWaitCountDown() throws Exception {
        latch = new CounterLatch(1,0);
        assertEquals("No threads should be waiting", false, latch.hasQueuedThreads());
        Thread testThread = new Thread() {
            public void run() {
                try {
                    //System.out.println("Entering ["+Thread.currentThread().getName()+"]");
                    latch.await();
                } catch (InterruptedException x) {
                    x.printStackTrace();
                }
                //System.out.println("Exiting ["+Thread.currentThread().getName()+"]");
            }
        };
        latch.countDown();
        testThread.start();
        Thread.sleep(50);
        assertEquals("1 threads should be waiting", 1, latch.getQueuedThreads().size());
        latch.countDown();
        Thread.sleep(50);
        assertEquals("No threads should be waiting", false, latch.hasQueuedThreads());
    }
    
    public void testOneRelease() throws Exception {
        latch = new CounterLatch(1,0);
        assertEquals("No threads should be waiting", false, latch.hasQueuedThreads());
        Thread testThread = new Thread() {
            public void run() {
                try {
                    latch.await();
                } catch (InterruptedException x) {
                    x.printStackTrace();
                }
            }
        };
        latch.countDown();
        testThread.start();
        Thread.sleep(50);
        assertEquals("1 threads should be waiting", 1, latch.getQueuedThreads().size());
        latch.releaseAll();
        Thread.sleep(50);
        assertEquals("No threads should be waiting", false, latch.hasQueuedThreads());
    }    
}
