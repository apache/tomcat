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

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestStuckThreadDetectionValve extends TomcatBaseTest {
    private StandardContext context;
    private Tomcat tomcat;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        tomcat = getTomcatInstance();
        File docBase = new File(System.getProperty("java.io.tmpdir"));
        context = (StandardContext) tomcat.addContext("", docBase.getAbsolutePath());
    }

    @Test
    public void testDetection() throws Exception {
        // second, we test the actual effect of the flag on the startup
        StickingServlet stickingServlet = new StickingServlet(8000L);
        Wrapper servlet = Tomcat.addServlet(context, "myservlet", stickingServlet);
        servlet.addMapping("/myservlet");

        StuckThreadDetectionValve valve = new StuckThreadDetectionValve();
        valve.setThreshold(2);
        context.addValve(valve);
        context.setBackgroundProcessorDelay(1);
        tomcat.start();

        Assert.assertEquals(0, valve.getStuckThreadIds().length);

        final ByteChunk result = new ByteChunk();
        Thread asyncThread = new Thread() {
            @Override
            public void run() {
                try {
                    getUrl("http://localhost:" + getPort() + "/myservlet", result, null);
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }

        };
        asyncThread.start();
        try {
            Thread.sleep(500L);
            Assert.assertEquals(0, valve.getStuckThreadIds().length);

            Thread.sleep(5000L);
            Assert.assertEquals(1, valve.getStuckThreadIds().length);
        } finally {
            asyncThread.join(20000);
            // check that we did not reach the join timeout
            Assert.assertFalse(asyncThread.isAlive());
        }
        Assert.assertFalse(stickingServlet.wasInterrupted);
        Assert.assertTrue(result.toString().startsWith("OK"));
    }

    @Test
    public void testInterruption() throws Exception {
        // second, we test the actual effect of the flag on the startup
        StickingServlet stickingServlet = new StickingServlet(TimeUnit.SECONDS.toMillis(20L));
        Wrapper servlet = Tomcat.addServlet(context, "myservlet", stickingServlet);
        servlet.addMapping("/myservlet");

        StuckThreadDetectionValve valve = new StuckThreadDetectionValve();
        valve.setThreshold(2);
        valve.setInterruptThreadThreshold(5);
        context.addValve(valve);
        context.setBackgroundProcessorDelay(1);
        tomcat.start();

        Assert.assertEquals(0, valve.getStuckThreadIds().length);

        final ByteChunk result = new ByteChunk();
        Thread asyncThread = new Thread() {
            @Override
            public void run() {
                try {
                    getUrl("http://localhost:" + getPort() + "/myservlet", result, null);
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }

        };
        asyncThread.start();
        try {
            Thread.sleep(4000L);
            Assert.assertEquals(1, valve.getStuckThreadIds().length);

        } finally {
            asyncThread.join(20000);
            // check that we did not reach the join timeout
            Assert.assertFalse(asyncThread.isAlive());
        }
        Assert.assertTrue(stickingServlet.wasInterrupted);
        Assert.assertEquals(0, valve.getStuckThreadIds().length);
        Assert.assertTrue(result.toString().startsWith("OK"));
    }

    @Test
    public void testInterruptStuckThreadOnlyOnce() throws Exception {
        // A servlet whose work is not interruptible: it stays busy even after
        // being interrupted and records every interrupt that is delivered
        UninterruptibleStickingServlet stickingServlet =
                new UninterruptibleStickingServlet(TimeUnit.SECONDS.toMillis(8L));
        Wrapper servlet = Tomcat.addServlet(context, "myservlet", stickingServlet);
        servlet.addMapping("/myservlet");

        StuckThreadDetectionValve valve = new StuckThreadDetectionValve();
        valve.setThreshold(2);
        valve.setInterruptThreadThreshold(3);
        context.addValve(valve);
        context.setBackgroundProcessorDelay(1);
        tomcat.start();

        final ByteChunk result = new ByteChunk();
        Thread asyncThread = new Thread() {
            @Override
            public void run() {
                try {
                    getUrl("http://localhost:" + getPort() + "/myservlet", result, null);
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }

        };
        asyncThread.start();
        asyncThread.join(20000);
        // check that we did not reach the join timeout
        Assert.assertFalse(asyncThread.isAlive());

        // The thread should have been interrupted, but only once. A thread
        // that remains stuck despite the interrupt must not be interrupted,
        // and logged, again on every background process cycle.
        Assert.assertEquals(1, stickingServlet.getInterruptCount());
        Assert.assertTrue(result.toString().startsWith("OK"));
    }

    @Test
    public void testInterruptFlagNotLeftSetForNextRequest() throws Exception {
        // A servlet that works until interrupted and then completes without
        // consuming the interrupt flag, as work that is not interruptible
        // would leave it
        InterruptUntilDoneServlet stickingServlet = new InterruptUntilDoneServlet();
        Wrapper servlet = Tomcat.addServlet(context, "myservlet", stickingServlet);
        servlet.addMapping("/myservlet");

        // A valve added before the detection valve sees the state of the
        // worker thread once the detection valve is done with the request,
        // which is the state the thread would carry into the next request
        AtomicReference<Boolean> flagAfterRequest = new AtomicReference<>();
        ValveBase observer = new ValveBase(true) {
            @Override
            public void invoke(Request request, Response response) throws IOException, ServletException {
                try {
                    getNext().invoke(request, response);
                } finally {
                    // Consume any flag so the pooled worker thread is left
                    // clean for the next request
                    flagAfterRequest.set(Boolean.valueOf(Thread.interrupted()));
                }
            }
        };

        context.addValve(observer);

        StuckThreadDetectionValve valve = new StuckThreadDetectionValve();
        valve.setThreshold(2);
        valve.setInterruptThreadThreshold(3);
        context.addValve(valve);
        context.setBackgroundProcessorDelay(1);
        tomcat.start();

        final ByteChunk result = new ByteChunk();
        Thread asyncThread = new Thread() {
            @Override
            public void run() {
                try {
                    getUrl("http://localhost:" + getPort() + "/myservlet", result, null);
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }

        };
        asyncThread.start();
        asyncThread.join(30000);
        // check that we did not reach the join timeout
        Assert.assertFalse(asyncThread.isAlive());

        Assert.assertTrue(stickingServlet.wasInterrupted);
        // The detection valve must not leave the interrupt flag it set on the
        // worker thread set once it is done with the request
        Assert.assertEquals(Boolean.FALSE, flagAfterRequest.get());
        Assert.assertTrue(result.toString().startsWith("OK"));
    }

    private static class InterruptUntilDoneServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;
        volatile boolean wasInterrupted = false;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            // Wait for the interrupt without consuming it, then complete.
            // Non-interruptible work that ignores the interrupt leaves the
            // flag set when it finishes.
            long end = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(25L);
            while (!Thread.currentThread().isInterrupted() && System.currentTimeMillis() < end) {
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException e) {
                    // The loop condition observes the flag below. Re-arm the
                    // flag since this test checks that the valve clears the
                    // interrupt it set.
                    Thread.currentThread().interrupt();
                }
            }
            wasInterrupted = Thread.currentThread().isInterrupted();
            // Deliberately do not consume the interrupt flag here
            resp.setContentType("text/plain");
            resp.getWriter().println("OK");
        }
    }

    private static class UninterruptibleStickingServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;
        private final long duration;
        private final AtomicInteger interruptCount = new AtomicInteger();

        UninterruptibleStickingServlet(long duration) {
            this.duration = duration;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            // Busy wait, like work that ignores the interrupt. Each delivered
            // interrupt is observed once and cleared, so a new interrupt of
            // the same request is counted separately.
            long end = System.currentTimeMillis() + duration;
            while (System.currentTimeMillis() < end) {
                if (Thread.interrupted()) {
                    interruptCount.incrementAndGet();
                }
            }
            // Do not hand an interrupt flag back to the container
            Thread.interrupted();
            resp.setContentType("text/plain");
            resp.getWriter().println("OK");
        }

        public int getInterruptCount() {
            return interruptCount.get();
        }
    }

    private static class StickingServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;
        private final long delay;
        boolean wasInterrupted = false;

        StickingServlet(long delay) {
            this.delay = delay;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                wasInterrupted = true;
            }
            resp.setContentType("text/plain");
            resp.getWriter().println("OK");
        }

    }
}
