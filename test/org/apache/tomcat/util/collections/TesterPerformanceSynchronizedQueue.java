/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.collections;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.Test;

public class TesterPerformanceSynchronizedQueue {

    private static final int THREAD_COUNT = 4;
    private static final int ITERATIONS = 1000000;

    private static final SynchronizedQueue<Object> S_QUEUE =
            new SynchronizedQueue<>();

    private static final Queue<Object> QUEUE = new ConcurrentLinkedQueue<>();

    @Test
    public void testSynchronizedQueue() throws InterruptedException {
        Thread[] threads = new Thread[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; i++) {
            threads[i] = new StackThread();
        }

        long start = System.currentTimeMillis();

        for (int i = 0; i < THREAD_COUNT; i++) {
            threads[i].start();
        }

        for (int i = 0; i < THREAD_COUNT; i++) {
            threads[i].join();
        }

        long end = System.currentTimeMillis();

        System.out.println("SynchronizedQueue: " + (end - start) + "ms");
    }

    public static class StackThread extends Thread {

        @Override
        public void run() {
            for(int i = 0; i < ITERATIONS; i++) {
                Object obj = S_QUEUE.poll();
                if (obj == null) {
                    obj = new Object();
                }
                S_QUEUE.offer(obj);
            }
            super.run();
        }
    }

    @Test
    public void testConcurrentQueue() throws InterruptedException {
        Thread[] threads = new Thread[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; i++) {
            threads[i] = new QueueThread();
        }

        long start = System.currentTimeMillis();

        for (int i = 0; i < THREAD_COUNT; i++) {
            threads[i].start();
        }

        for (int i = 0; i < THREAD_COUNT; i++) {
            threads[i].join();
        }

        long end = System.currentTimeMillis();

        System.out.println("ConcurrentLinkedQueue: " + (end - start) + "ms");
    }

    public static class QueueThread extends Thread {

        @Override
        public void run() {
            for(int i = 0; i < ITERATIONS; i++) {
                Object obj = QUEUE.poll();
                if (obj == null) {
                    obj = new Object();
                }
                QUEUE.offer(obj);
            }
            super.run();
        }
    }
}
