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
package org.apache.catalina.tribes.io;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Test;

public class TestBufferPool {

    private static final int MAX = 512 * 1024;

    /*
     * Concurrent returns must not all pass the limit check against the same stale total and admit
     * more than maxSize bytes between them. The pool is parked just below the limit and repeatedly
     * offered to the racing threads, so the limit check is exercised over and over on the edge.
     */
    @Test
    public void testConcurrentReturnsRespectMaxSize() throws Exception {
        BufferPool pool = BufferPool.getBufferPool();
        int original = pool.getMaxSize();
        try {
            pool.clear();
            pool.setMaxSize(MAX);
            final AtomicBoolean go = new AtomicBoolean();
            final AtomicBoolean stop = new AtomicBoolean();
            List<Thread> threads = new ArrayList<>();
            try {
                for (int t = 0; t < 4; t++) {
                    final XByteBuffer[] ring = new XByteBuffer[32];
                    for (int i = 0; i < ring.length; i++) {
                        ring[i] = new XByteBuffer(64 * 1024, false);
                    }
                    Thread thread = new Thread(() -> {
                        int index = 0;
                        while (!stop.get()) {
                            while (!go.get() && !stop.get()) {
                                Thread.onSpinWait();
                            }
                            while (go.get() && !stop.get()) {
                                pool.returnBuffer(ring[index++ & 31]);
                            }
                        }
                    });
                    thread.setDaemon(true);
                    threads.add(thread);
                    thread.start();
                }
                int overshoot = 0;
                int trials = 750;
                for (int trial = 0; trial < trials; trial++) {
                    drain(pool);
                    for (int i = 0; i < MAX / (64 * 1024) - 1; i++) {
                        pool.returnBuffer(new XByteBuffer(64 * 1024, false));
                    }
                    go.set(true);
                    Thread.sleep(0, 200000);
                    go.set(false);
                    if (pool.size.get() > MAX) {
                        overshoot++;
                    }
                }
                Assert.assertEquals("pool size exceeded maxSize in " + overshoot + " of the concurrent trials", 0,
                        overshoot);
            } finally {
                stop.set(true);
                for (Thread thread : threads) {
                    thread.join();
                }
            }
        } finally {
            pool.setMaxSize(original);
            pool.clear();
        }
    }

    private static void drain(BufferPool pool) {
        while (!pool.queue.isEmpty()) {
            pool.getBuffer(1, false);
        }
    }

    /*
     * Clearing the pool while buffers are returned must not skew the size accounting. Once all
     * activity has stopped, the size must again match the buffers that the queue actually holds.
     */
    @Test
    public void testClearKeepsAccountingConsistent() throws Exception {
        BufferPool pool = BufferPool.getBufferPool();
        int original = pool.getMaxSize();
        try {
            pool.clear();
            pool.setMaxSize(MAX);
            AtomicBoolean stop = new AtomicBoolean();
            Thread returns = new Thread(() -> {
                while (!stop.get()) {
                    pool.returnBuffer(new XByteBuffer(1024, false));
                }
            });
            returns.setDaemon(true);
            Thread[] clears = new Thread[2];
            try {
                returns.start();
                for (int t = 0; t < clears.length; t++) {
                    clears[t] = new Thread(() -> {
                        while (!stop.get()) {
                            pool.clear();
                        }
                    });
                    clears[t].setDaemon(true);
                    clears[t].start();
                }
                Thread.sleep(250);
            } finally {
                stop.set(true);
                returns.join();
                for (Thread thread : clears) {
                    thread.join();
                }
            }
            // Once all activity has stopped, the size must again match the
            // buffers that the queue actually holds
            long queued = 0;
            for (XByteBuffer buffer : pool.queue) {
                queued += buffer.getCapacity();
            }
            Assert.assertEquals("pool size must match the capacity of the queued buffers", queued, pool.size.get());
            pool.clear();
            Assert.assertEquals(0, pool.size.get());
            Assert.assertTrue(pool.queue.isEmpty());
        } finally {
            pool.setMaxSize(original);
            pool.clear();
        }
    }
}
