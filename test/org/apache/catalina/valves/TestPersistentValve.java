/* Licensed to the Apache Software Foundation (ASF) under one or more
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
package org.apache.catalina.valves;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.ServletException;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.tomcat.unittest.TesterRequest;
import org.apache.tomcat.unittest.TesterResponse;

public class TestPersistentValve {

    @Test
    public void testSemaphore() throws Exception {
        // Create the test objects
        PersistentValve pv = new PersistentValve();
        Request request = new TesterRequest();
        Response response = new TesterResponse();
        TesterValve testerValve = new TesterValve();

        // Configure the test objects
        request.setRequestedSessionId("1234");

        // Plumb the test objects together
        pv.setContainer(request.getContext());
        pv.setNext(testerValve);

        // Call the necessary lifecycle methods
        pv.init();

        // Run the test
        Thread[] threads = new Thread[5];

        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try {
                    pv.invoke(request, response);
                } catch (IOException | ServletException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        for (int i = 0; i < threads.length; i++) {
            threads[i].start();
        }

        for (int i = 0; i < threads.length; i++) {
            threads[i].join();
        }

        Assert.assertEquals(1, testerValve.getMaximumConcurrency());
    }


    private static class TesterValve extends ValveBase {

        private static AtomicInteger maximumConcurrency = new AtomicInteger();
        private static AtomicInteger concurrency = new AtomicInteger();

        @Override
        public void invoke(Request request, Response response) throws IOException, ServletException {
            int c = concurrency.incrementAndGet();
            maximumConcurrency.getAndUpdate((v) -> c > v ? c : v);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // Ignore
            }
            concurrency.decrementAndGet();
        }

        public int getMaximumConcurrency() {
            return maximumConcurrency.get();
        }
    }
}
