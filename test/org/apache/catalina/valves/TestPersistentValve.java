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

import org.apache.catalina.Session;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.session.StandardSession;
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


    @Test
    public void testFilterMatchBypassesSession() throws Exception {
        PersistentValve pv = new PersistentValve();
        Request request = new TesterRequest();
        Response response = new TesterResponse();
        CountingValve countingValve = new CountingValve();

        // Set a filter that matches the default TesterRequest URI
        // TesterRequest.getDecodedRequestURI() returns /level1/level2/foo.html
        pv.setFilter(".*\\.html");
        request.setRequestedSessionId("1234");

        pv.setContainer(request.getContext());
        pv.setNext(countingValve);
        pv.init();

        pv.invoke(request, response);

        // The request should have gone through (bypassing session handling)
        Assert.assertEquals(1, countingValve.getCount());
    }


    @Test
    public void testFilterNoMatchProcesses() throws Exception {
        PersistentValve pv = new PersistentValve();
        Request request = new TesterRequest();
        Response response = new TesterResponse();
        CountingValve countingValve = new CountingValve();

        // Set a filter that does NOT match the default TesterRequest URI
        pv.setFilter(".*\\.css");

        pv.setContainer(request.getContext());
        pv.setNext(countingValve);
        pv.init();

        pv.invoke(request, response);

        // The request should still proceed (but through session handling path)
        Assert.assertEquals(1, countingValve.getCount());
    }


    @Test
    public void testGetSetFilter() throws Exception {
        PersistentValve pv = new PersistentValve();
        pv.setContainer(new TesterRequest().getContext());

        Assert.assertNull(pv.getFilter());

        pv.setFilter(".*\\.jpg");
        Assert.assertEquals(".*\\.jpg", pv.getFilter());
    }


    @Test
    public void testFilterNull() throws Exception {
        PersistentValve pv = new PersistentValve();
        pv.setContainer(new TesterRequest().getContext());

        pv.setFilter(".*\\.jpg");
        Assert.assertNotNull(pv.getFilter());

        pv.setFilter(null);
        Assert.assertNull(pv.getFilter());
    }


    @Test
    public void testFilterEmpty() throws Exception {
        PersistentValve pv = new PersistentValve();
        pv.setContainer(new TesterRequest().getContext());

        pv.setFilter(".*\\.jpg");
        Assert.assertNotNull(pv.getFilter());

        pv.setFilter("");
        Assert.assertNull(pv.getFilter());
    }


    @Test
    public void testGetSetSemaphoreProperties() throws Exception {
        PersistentValve pv = new PersistentValve();

        // semaphoreFairness
        Assert.assertTrue(pv.isSemaphoreFairness());
        pv.setSemaphoreFairness(false);
        Assert.assertFalse(pv.isSemaphoreFairness());

        // semaphoreBlockOnAcquire
        Assert.assertTrue(pv.isSemaphoreBlockOnAcquire());
        pv.setSemaphoreBlockOnAcquire(false);
        Assert.assertFalse(pv.isSemaphoreBlockOnAcquire());

        // semaphoreAcquireUninterruptibly
        Assert.assertTrue(pv.isSemaphoreAcquireUninterruptibly());
        pv.setSemaphoreAcquireUninterruptibly(false);
        Assert.assertFalse(pv.isSemaphoreAcquireUninterruptibly());
    }


    @Test
    public void testIsSessionStaleExpired() throws Exception {
        PersistentValve pv = new PersistentValve();
        StandardSession session = new StandardSession(null);
        session.setId("test-stale", false);
        session.setValid(true);
        session.setMaxInactiveInterval(1); // 1 second timeout

        // Set creation time to a long time ago so the session appears idle
        session.setCreationTime(
                System.currentTimeMillis() - (60 * 1000)); // 60 seconds ago

        Assert.assertTrue("Session should be stale",
                pv.isSessionStale(session, System.currentTimeMillis()));
    }


    @Test
    public void testIsSessionStaleValid() throws Exception {
        PersistentValve pv = new PersistentValve();
        StandardSession session = new StandardSession(null);
        session.setId("test-valid", false);
        session.setValid(true);
        session.setMaxInactiveInterval(3600); // 1 hour timeout

        // Session was just created so its access times are current
        session.setCreationTime(System.currentTimeMillis());

        Assert.assertFalse("Session should not be stale",
                pv.isSessionStale(session, System.currentTimeMillis()));
    }


    @Test
    public void testIsSessionStaleNoTimeout() throws Exception {
        PersistentValve pv = new PersistentValve();
        StandardSession session = new StandardSession(null);
        session.setId("test-notimeout", false);
        session.setValid(true);
        session.setMaxInactiveInterval(-1); // No timeout

        Assert.assertFalse("Session with no timeout should not be stale",
                pv.isSessionStale(session, System.currentTimeMillis()));
    }


    @Test
    public void testIsSessionStaleNullSession() throws Exception {
        PersistentValve pv = new PersistentValve();

        Assert.assertFalse("Null session should not be stale",
                pv.isSessionStale(null, System.currentTimeMillis()));
    }


    @Test
    public void testRequestWithoutSessionNoFilter() throws Exception {
        PersistentValve pv = new PersistentValve();

        // With no filter set, no request should be considered "without session"
        Assert.assertFalse(pv.isRequestWithoutSession("/index.html"));
        Assert.assertFalse(pv.isRequestWithoutSession("/style.css"));
    }


    @Test
    public void testRequestWithoutSessionWithFilter() throws Exception {
        PersistentValve pv = new PersistentValve();
        pv.setContainer(new TesterRequest().getContext());

        pv.setFilter(".*\\.(css|js|png|jpg)");

        Assert.assertTrue(pv.isRequestWithoutSession("/style.css"));
        Assert.assertTrue(pv.isRequestWithoutSession("/app.js"));
        Assert.assertTrue(pv.isRequestWithoutSession("/logo.png"));
        Assert.assertFalse(pv.isRequestWithoutSession("/index.html"));
        Assert.assertFalse(pv.isRequestWithoutSession("/api/data"));
    }


    @Test
    public void testNoSessionIdInvoke() throws Exception {
        PersistentValve pv = new PersistentValve();
        Request request = new TesterRequest();
        Response response = new TesterResponse();
        CountingValve countingValve = new CountingValve();

        // Don't set session id — null by default
        pv.setContainer(request.getContext());
        pv.setNext(countingValve);
        pv.init();

        pv.invoke(request, response);

        Assert.assertEquals(1, countingValve.getCount());
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


    private static class CountingValve extends ValveBase {

        private final AtomicInteger count = new AtomicInteger(0);

        @Override
        public void invoke(Request request, Response response)
                throws IOException, ServletException {
            count.incrementAndGet();
        }

        public int getCount() {
            return count.get();
        }
    }
}
