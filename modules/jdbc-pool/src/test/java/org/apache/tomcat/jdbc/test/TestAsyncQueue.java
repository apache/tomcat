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

package org.apache.tomcat.jdbc.test;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import junit.framework.TestCase;

import org.apache.tomcat.jdbc.pool.FairBlockingQueue;

public class TestAsyncQueue extends TestCase {
    protected FairBlockingQueue<Object> queue = null;
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.queue = new FairBlockingQueue<Object>();
    }

    @Override
    protected void tearDown() throws Exception {
        this.queue = null;
        super.tearDown();
    }


    public void testAsyncPoll1() throws Exception {
        Object item = new Object();
        queue.offer(item);
        Future<Object> future = queue.pollAsync();
        assertEquals(future.get(),item);
    }

    public void testAsyncPoll2() throws Exception {
        Object item = new Object();
        OfferThread thread = new OfferThread(item,5000);
        thread.start();
        Future<Object> future = queue.pollAsync();
        try {
            future.get(2000, TimeUnit.MILLISECONDS);
            assertFalse("Request should have timed out",true);
        }catch (TimeoutException x) {
            assertTrue("Request timed out properly",true);
        }catch (Exception x) {
            assertTrue("Request threw an error",false);
            x.printStackTrace();
        }
        assertEquals(future.get(),item);
    }

    protected class OfferThread extends Thread {
        Object item = null;
        long delay = 5000;
        volatile boolean offered = false;
        public OfferThread(Object i, long d) {
            this.item = i;
            this.delay = d;
            this.setDaemon(false);
            this.setName(TestAsyncQueue.class.getName()+"-OfferThread");
        }
        @Override
        public void run() {
            try {
                sleep(delay);
            }catch (Exception ignore){}
            offered = true;
            TestAsyncQueue.this.queue.offer(item);
        }
    }
}
