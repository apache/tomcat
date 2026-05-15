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
package org.apache.catalina.valves;

import java.io.IOException;
import java.io.Serial;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestSemaphoreValve extends TomcatBaseTest {


    @Test
    public void testBasicConcurrency() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "hello", new HelloWorldServlet());
        ctx.addServletMappingDecoded("/", "hello");

        SemaphoreValve valve = new SemaphoreValve();
        valve.setConcurrency(10);
        ctx.getPipeline().addValve(valve);

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertEquals(HelloWorldServlet.RESPONSE_TEXT, res.toString());
    }

    @Test
    public void testInterruptedConcurrency() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "hello", new HelloWorldServlet());
        ctx.addServletMappingDecoded("/", "hello");

        SemaphoreValve valve = new SemaphoreValve();
        valve.setConcurrency(10);
        valve.setInterruptible(true);
        ctx.getPipeline().addValve(valve);

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertEquals(HelloWorldServlet.RESPONSE_TEXT, res.toString());
    }


    @Test
    public void testNonBlockingDenied() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx = getProgrammaticRootContext();

        CountDownLatch insideServlet = new CountDownLatch(1);
        CountDownLatch canReturn = new CountDownLatch(1);
        Tomcat.addServlet(ctx, "slow", new SlowServlet(insideServlet, canReturn));
        ctx.addServletMappingDecoded("/", "slow");

        SemaphoreValve valve = new SemaphoreValve();
        valve.setConcurrency(1);
        valve.setBlock(false);
        valve.setHighConcurrencyStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        ctx.getPipeline().addValve(valve);

        tomcat.start();

        // First request — should acquire the permit and block inside the servlet
        AtomicInteger firstRc = new AtomicInteger();
        Thread firstThread = new Thread(() -> {
            try {
                firstRc.set(getUrl("http://localhost:" + getPort(), new ByteChunk(), null));
            } catch (IOException e) {
                // Ignore
            }
        });
        firstThread.start();

        // Wait until the first request is inside the servlet
        Assert.assertTrue("First request should reach servlet",
                insideServlet.await(10, TimeUnit.SECONDS));

        // Second request — should be denied because concurrency=1 and block=false
        int rc2 = getUrl("http://localhost:" + getPort(), new ByteChunk(), null);

        Assert.assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, rc2);

        // Release the first request
        canReturn.countDown();
        firstThread.join(10000);
        Assert.assertFalse(firstThread.isAlive());
        Assert.assertEquals(HttpServletResponse.SC_OK, firstRc.get());
    }


    @Test
    public void testHighConcurrencyStatusNotSet() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx = getProgrammaticRootContext();

        CountDownLatch insideServlet = new CountDownLatch(1);
        CountDownLatch canReturn = new CountDownLatch(1);
        Tomcat.addServlet(ctx, "slow", new SlowServlet(insideServlet, canReturn));
        ctx.addServletMappingDecoded("/", "slow");

        SemaphoreValve valve = new SemaphoreValve();
        valve.setConcurrency(1);
        valve.setBlock(false);
        // highConcurrencyStatus is -1 by default (no error sent)
        ctx.getPipeline().addValve(valve);

        tomcat.start();

        // First request holds the permit
        Thread firstThread = new Thread(() -> {
            try {
                getUrl("http://localhost:" + getPort(), new ByteChunk(), null);
            } catch (IOException e) {
                // Ignore
            }
        });
        firstThread.start();

        Assert.assertTrue("First request should reach servlet",
                insideServlet.await(10, TimeUnit.SECONDS));

        // Second request — denied but no error status is sent
        int rc2 = getUrl("http://localhost:" + getPort(), new ByteChunk(), null);

        // With no highConcurrencyStatus, response is 200 without body
        Assert.assertEquals(HttpServletResponse.SC_OK, rc2);

        canReturn.countDown();
        firstThread.join(10000);
    }


    @Test
    public void testGetSetProperties() {
        SemaphoreValve valve = new SemaphoreValve();

        // Defaults
        Assert.assertEquals(10, valve.getConcurrency());
        Assert.assertFalse(valve.getFairness());
        Assert.assertTrue(valve.getBlock());
        Assert.assertFalse(valve.getInterruptible());
        Assert.assertEquals(-1, valve.getHighConcurrencyStatus());

        // Setters
        valve.setConcurrency(5);
        Assert.assertEquals(5, valve.getConcurrency());

        valve.setFairness(true);
        Assert.assertTrue(valve.getFairness());

        valve.setBlock(false);
        Assert.assertFalse(valve.getBlock());

        valve.setInterruptible(true);
        Assert.assertTrue(valve.getInterruptible());

        valve.setHighConcurrencyStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
        Assert.assertEquals(HttpServletResponse.SC_TOO_MANY_REQUESTS, valve.getHighConcurrencyStatus());
    }


    @Test
    public void testFairSemaphore() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "hello", new HelloWorldServlet());
        ctx.addServletMappingDecoded("/", "hello");

        SemaphoreValve valve = new SemaphoreValve();
        valve.setConcurrency(5);
        valve.setFairness(true);
        ctx.getPipeline().addValve(valve);

        tomcat.start();

        Assert.assertNotNull(valve.semaphore);
        Assert.assertTrue(valve.semaphore.isFair());
        Assert.assertEquals(5, valve.semaphore.availablePermits());

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertEquals(HelloWorldServlet.RESPONSE_TEXT, res.toString());
    }

    @Test
    public void testBlockingWaitsForPermit() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx = getProgrammaticRootContext();

        CountDownLatch insideServlet = new CountDownLatch(1);
        CountDownLatch canReturn = new CountDownLatch(1);
        Tomcat.addServlet(ctx, "slow", new SlowServlet(insideServlet, canReturn));
        ctx.addServletMappingDecoded("/", "slow");

        SemaphoreValve valve = new SemaphoreValve();
        valve.setConcurrency(1);
        valve.setBlock(true);
        ctx.getPipeline().addValve(valve);

        tomcat.start();

        AtomicReference<Throwable> firstError = new AtomicReference<>();
        Thread firstThread = new Thread(() -> {
            try {
                getUrl("http://localhost:" + getPort(), new ByteChunk(), null);
            } catch (IOException e) {
                firstError.set(e);
            }
        });
        firstThread.start();

        Assert.assertTrue("First request should reach servlet",
                insideServlet.await(10, TimeUnit.SECONDS));

        AtomicInteger secondRc = new AtomicInteger();
        AtomicReference<Throwable> secondError = new AtomicReference<>();
        Thread secondThread = new Thread(() -> {
            try {
                secondRc.set(getUrl("http://localhost:" + getPort(), new ByteChunk(), null));
            } catch (IOException e) {
                secondError.set(e);
            }
        });
        secondThread.start();

        // Give the second request time to arrive and block on the semaphore
        Thread.sleep(500);

        Assert.assertTrue("Second request should be blocked waiting for permit", secondThread.isAlive());

        canReturn.countDown();
        firstThread.join(10000);
        Assert.assertNull(firstError.get());

        secondThread.join(10000);
        Assert.assertFalse(secondThread.isAlive());
        Assert.assertNull(secondError.get());
        Assert.assertEquals(HttpServletResponse.SC_OK, secondRc.get());
    }

    @Test
    public void testControlConcurrencyBypass() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx = getProgrammaticRootContext();

        CountDownLatch insideServlet = new CountDownLatch(1);
        CountDownLatch canReturn = new CountDownLatch(1);
        Tomcat.addServlet(ctx, "slow", new SlowServlet(insideServlet, canReturn));
        ctx.addServletMappingDecoded("/slow", "slow");

        Tomcat.addServlet(ctx, "hello", new HelloWorldServlet());
        ctx.addServletMappingDecoded("/bypass", "hello");

        SemaphoreValve valve = new SemaphoreValve() {
            @Override
            public boolean controlConcurrency(Request request, Response response) {
                return !request.getDecodedRequestURI().equals("/bypass");
            }
        };
        valve.setConcurrency(1);
        valve.setBlock(false);
        valve.setHighConcurrencyStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        ctx.getPipeline().addValve(valve);

        tomcat.start();

        Thread firstThread = new Thread(() -> {
            try {
                getUrl("http://localhost:" + getPort() + "/slow", new ByteChunk(), null);
            } catch (IOException e) {
                // Ignored
            }
        });
        firstThread.start();

        Assert.assertTrue("First request should reach servlet",
                insideServlet.await(10, TimeUnit.SECONDS));

        // Request to /bypass should succeed despite all permits being held,
        // because controlConcurrency() returns false for this path
        int bypassRc = getUrl("http://localhost:" + getPort() + "/bypass", new ByteChunk(), null);
        Assert.assertEquals(HttpServletResponse.SC_OK, bypassRc);

        int deniedRc = getUrl("http://localhost:" + getPort() + "/slow", new ByteChunk(), null);
        Assert.assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, deniedRc);

        canReturn.countDown();
        firstThread.join(10000);
    }

    @Test
    public void testInterruptibleDenied() throws Exception {
        SemaphoreValve semaphoreValve = new SemaphoreValve();
        semaphoreValve.setConcurrency(1);
        semaphoreValve.setBlock(true);
        semaphoreValve.setInterruptible(true);
        semaphoreValve.setHighConcurrencyStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);

        semaphoreValve.semaphore = new Semaphore(1, false);

        AtomicBoolean nextInvoked = new AtomicBoolean(false);
        semaphoreValve.setNext(new ValveBase() {
            @Override
            public void invoke(Request request, Response response) {
                nextInvoked.set(true);
            }
        });

        MockResponse response = new MockResponse();

        semaphoreValve.semaphore.acquire();

        // On a new thread, valve will block on semaphore.acquire() because the permit is already held.
        CountDownLatch invokeStarted = new CountDownLatch(1);
        Thread blocked = new Thread(() -> {
            invokeStarted.countDown();
            try {
                semaphoreValve.invoke(null, response);
            } catch (Throwable t) {
                // Ignored
            }
        });
        blocked.start();

        Assert.assertTrue(invokeStarted.await(10, TimeUnit.SECONDS));
        Thread.sleep(200);

        blocked.interrupt();
        blocked.join(10000);
        Assert.assertFalse(blocked.isAlive());

        Assert.assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatus());

        Assert.assertFalse("Next valve should not be invoked when permit denied", nextInvoked.get());

        Assert.assertEquals(0, semaphoreValve.semaphore.availablePermits());

        semaphoreValve.semaphore.release();
    }

    private static final class SlowServlet extends HttpServlet {

        @Serial
        private static final long serialVersionUID = 1L;
        private final CountDownLatch insideServlet;
        private final CountDownLatch canReturn;

        private SlowServlet(CountDownLatch insideServlet, CountDownLatch canReturn) {
            this.insideServlet = insideServlet;
            this.canReturn = canReturn;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws IOException {
            insideServlet.countDown();
            try {
                Assert.assertTrue(canReturn.await(30, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                // Ignore
            }
            resp.setContentType("text/plain");
            resp.getWriter().print("OK");
        }
    }

    public static class MockResponse extends Response {

        public MockResponse() {
            super(null);
        }

        private int status = HttpServletResponse.SC_OK;

        @Override
        public void sendError(int status) throws IOException {
            this.status = status;
        }

        @Override
        public int getStatus() {
            return status;
        }
    }

}
