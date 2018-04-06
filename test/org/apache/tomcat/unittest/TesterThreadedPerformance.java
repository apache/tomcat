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
package org.apache.tomcat.unittest;

import java.util.function.IntConsumer;
import java.util.function.Supplier;

public class TesterThreadedPerformance {

    private final int threadCount;
    private final int iterationCount;
    private final Supplier<IntConsumer> testInstanceSupplier;


    public TesterThreadedPerformance(int threadCount, int iterationCount,
            Supplier<IntConsumer> testInstanceSupplier) {
        this.threadCount = threadCount;
        this.iterationCount = iterationCount;
        this.testInstanceSupplier = testInstanceSupplier;
    }


    public long doTest() throws InterruptedException {
        long start = System.nanoTime();

        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            IntConsumer testTarget = testInstanceSupplier.get();
            threads[i] = new Thread(
                    new TesterThreadedPerformanceRunnable(testTarget, iterationCount));
        }

        for (int i = 0; i < threadCount; i++) {
            threads[i].start();
        }

        for (int i = 0; i < threadCount; i++) {
            threads[i].join();
        }

        return System.nanoTime() - start;
    }


    private static class TesterThreadedPerformanceRunnable implements Runnable {

        private final IntConsumer testTarget;
        private final int iterationCount;

        public TesterThreadedPerformanceRunnable(IntConsumer testTarget, int iterationCount) {
            this.testTarget = testTarget;
            this.iterationCount = iterationCount;
        }


        @Override
        public void run() {
            for (int i = 0; i < iterationCount; i++) {
                testTarget.accept(i);
            }
        }
    }
}
