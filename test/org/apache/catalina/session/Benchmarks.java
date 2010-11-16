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
package org.apache.catalina.session;

import junit.framework.TestCase;

import org.apache.catalina.Session;

import org.apache.catalina.core.StandardContext;


/**
 * Named Benchmarks so it is not automatically executed as part of the unit
 * tests.
 */
public class Benchmarks extends TestCase {

    /*
     * Results on markt's 4-core Windows dev box
     *  1 thread  -   ~270ms
     *  2 threads -   ~400ms
     *  4 threads -   ~970ms
     * 16 threads - ~4,000ms
     */
    public void testManagerBaseGenerateSessionId() {
        doTestManagerBaseGenerateSessionId(1, 100000);
        doTestManagerBaseGenerateSessionId(1, 100000);
        doTestManagerBaseGenerateSessionId(1, 100000);
        doTestManagerBaseGenerateSessionId(2, 100000);
        doTestManagerBaseGenerateSessionId(2, 100000);
        doTestManagerBaseGenerateSessionId(2, 100000);
        doTestManagerBaseGenerateSessionId(4, 100000);
        doTestManagerBaseGenerateSessionId(4, 100000);
        doTestManagerBaseGenerateSessionId(4, 100000);
        doTestManagerBaseGenerateSessionId(16, 100000);
    }
    
    
    public void doTestManagerBaseGenerateSessionId(int threadCount,
            int iterCount) {

        // Create a default session manager
        StandardManager mgr = new StandardManager();
        
        Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(
                    new TestThreadGenerateSessionId(mgr, iterCount));
        }
        
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            threads[i].start();
        }
        for (int i = 0; i < threadCount; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                fail(e.getMessage());
            }
        }
        long end = System.currentTimeMillis();
        
        StringBuilder result = new StringBuilder();
        result.append("Threads: ");
        result.append(threadCount);
        result.append(",  Time(ms): ");
        result.append(end-start);
        System.out.println(result.toString());
    }
    
    
    private static final class TestThreadGenerateSessionId implements Runnable {

        private ManagerBase mgr;
        private int count;
        
        public TestThreadGenerateSessionId(ManagerBase mgr, int count) {
            this.mgr = mgr;
            this.count = count;
        }

        @Override
        public void run() {
            for (int i = 0; i < count; i++) {
                mgr.generateSessionId();
            }
        }
    }

    
    /*
     * Results on markt's 4-core Windows dev box
     *  1 thread  -   ~860ms
     *  2 threads -   ~800ms
     *  4 threads - ~1,600ms
     * 16 threads - ~6,900ms
     */
    public void testManagerBaseCreateSession() {
        doTestManagerBaseCreateSession(1, 100000);
        doTestManagerBaseCreateSession(2, 100000);
        doTestManagerBaseCreateSession(4, 100000);
        doTestManagerBaseCreateSession(16, 100000);
    }
    
    
    public void doTestManagerBaseCreateSession(int threadCount, int iterCount) {

        // Create a default session manager
        StandardManager mgr = new StandardManager();
        mgr.setContainer(new StandardContext());
        
        Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(
                    new TestThreadCreateSession(mgr, iterCount));
        }
        
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            threads[i].start();
        }
        for (int i = 0; i < threadCount; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                fail(e.getMessage());
            }
        }
        long end = System.currentTimeMillis();
        
        StringBuilder result = new StringBuilder();
        result.append("Threads: ");
        result.append(threadCount);
        result.append(",  Time(ms): ");
        result.append(end-start);
        System.out.println(result.toString());
    }
    
    
    private static final class TestThreadCreateSession implements Runnable {

        private ManagerBase mgr;
        private int count;
        
        public TestThreadCreateSession(ManagerBase mgr, int count) {
            this.mgr = mgr;
            this.count = count;
        }

        @Override
        public void run() {
            for (int i = 0; i < count; i++) {
                Session session = mgr.createSession(mgr.generateSessionId());
                session.expire();
            }
        }
    }
}
