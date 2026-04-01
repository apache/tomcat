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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestSemaphoreValve extends TomcatBaseTest {


    @Test
    public void testBasicConcurrency() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "ok", new OkServlet());
        ctx.addServletMappingDecoded("/", "ok");

        SemaphoreValve valve = new SemaphoreValve();
        valve.setConcurrency(10);
        ctx.getPipeline().addValve(valve);

        tomcat.start();

        ByteChunk res = new ByteChunk();
        res.setCharset(StandardCharsets.UTF_8);
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertEquals("OK", res.toString());
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
        valve.setHighConcurrencyStatus(503);
        ctx.getPipeline().addValve(valve);

        tomcat.start();

        // First request — should acquire the permit and block inside the servlet
        AtomicInteger firstRc = new AtomicInteger();
        Thread firstThread = new Thread(() -> {
            try {
                ByteChunk r = new ByteChunk();
                r.setCharset(StandardCharsets.UTF_8);
                firstRc.set(getUrl("http://localhost:" + getPort(), r, null));
            } catch (IOException e) {
                // Ignore
            }
        });
        firstThread.start();

        // Wait until the first request is inside the servlet
        Assert.assertTrue("First request should reach servlet",
                insideServlet.await(10, TimeUnit.SECONDS));

        // Second request — should be denied because concurrency=1 and block=false
        ByteChunk res2 = new ByteChunk();
        res2.setCharset(StandardCharsets.UTF_8);
        int rc2 = getUrl("http://localhost:" + getPort(), res2, null);

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
                ByteChunk r = new ByteChunk();
                getUrl("http://localhost:" + getPort(), r, null);
            } catch (IOException e) {
                // Ignore
            }
        });
        firstThread.start();

        Assert.assertTrue("First request should reach servlet",
                insideServlet.await(10, TimeUnit.SECONDS));

        // Second request — denied but no error status is sent
        ByteChunk res2 = new ByteChunk();
        int rc2 = getUrl("http://localhost:" + getPort(), res2, null);

        // With no highConcurrencyStatus, response is 200 with no body
        Assert.assertEquals(HttpServletResponse.SC_OK, rc2);

        canReturn.countDown();
        firstThread.join(10000);
    }


    @Test
    public void testGetSetProperties() throws Exception {
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

        valve.setHighConcurrencyStatus(429);
        Assert.assertEquals(429, valve.getHighConcurrencyStatus());
    }


    @Test
    public void testFairSemaphore() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "ok", new OkServlet());
        ctx.addServletMappingDecoded("/", "ok");

        SemaphoreValve valve = new SemaphoreValve();
        valve.setConcurrency(5);
        valve.setFairness(true);
        ctx.getPipeline().addValve(valve);

        tomcat.start();

        ByteChunk res = new ByteChunk();
        res.setCharset(StandardCharsets.UTF_8);
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertEquals("OK", res.toString());
    }


    private static final class OkServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.getWriter().print("OK");
        }
    }


    private static final class SlowServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;
        private final CountDownLatch insideServlet;
        private final CountDownLatch canReturn;

        private SlowServlet(CountDownLatch insideServlet, CountDownLatch canReturn) {
            this.insideServlet = insideServlet;
            this.canReturn = canReturn;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            insideServlet.countDown();
            try {
                canReturn.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // Ignore
            }
            resp.setContentType("text/plain");
            resp.getWriter().print("OK");
        }
    }
}
