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
package org.apache.coyote.http2;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;

public class TestAsyncTimeout extends Http2TestBase {

    @Test
    public void testTimeout() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        Context ctxt = tomcat.addContext("", null);
        // This is the target of the HTTP/2 upgrade request
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");

        // This is the servlet that does that actual test
        // This latch is used to signal that that async thread used by the test
        // has ended. It isn't essential to the test but it allows the test to
        // complete without Tomcat logging an error about a still running thread.
        CountDownLatch latch = new CountDownLatch(1);
        Wrapper w = Tomcat.addServlet(ctxt, "async", new AsyncTimeoutServlet(latch));
        w.setAsyncSupported(true);
        ctxt.addServletMappingDecoded("/async", "async");
        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        // Reset connection window size after initial response
        sendWindowUpdate(0, SimpleServlet.CONTENT_LENGTH);

        // Include the response body in the trace so we can check for the PASS /
        // FAIL text.
        output.setTraceBody(true);

        // Send request
        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildGetRequest(frameHeader, headersPayload, null, 3, "/async");
        writeFrame(frameHeader, headersPayload);

        // Headers
        parser.readFrame(true);
        // Body
        parser.readFrame(true);

        // Check that the expected text was received
        String trace = output.getTrace();
        Assert.assertTrue(trace, trace.contains("PASS"));

        latch.await(10, TimeUnit.SECONDS);
    }


    public static class AsyncTimeoutServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final transient CountDownLatch latch;

        public AsyncTimeoutServlet(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException {

            // The idea of this test is that the timeout kicks in after 2
            // seconds and stops the async thread early rather than letting it
            // complete the full 5 seconds of processing.
            final AsyncContext asyncContext = request.startAsync();

            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("text/plain");
            response.setCharacterEncoding("UTF-8");

            // Only want to call complete() once (else we get stack traces in
            // the logs so use this to track when complete() is called).
            AtomicBoolean completeCalled = new AtomicBoolean(false);
            Ticker ticker = new Ticker(asyncContext, completeCalled);
            TimeoutListener listener = new TimeoutListener(latch, ticker, completeCalled);
            asyncContext.addListener(listener);
            asyncContext.setTimeout(2000);
            ticker.start();
        }
    }


    private static class Ticker extends Thread {

        private final AsyncContext asyncContext;
        private final AtomicBoolean completeCalled;
        private volatile boolean running = true;

        public Ticker(AsyncContext asyncContext, AtomicBoolean completeCalled) {
            this.asyncContext = asyncContext;
            this.completeCalled = completeCalled;
        }

        public void end() {
            running = false;
        }

        @Override
        public void run() {
            try {
                PrintWriter pw = asyncContext.getResponse().getWriter();
                int counter = 0;

                // If the test works running will be set too false before
                // counter reaches 50.
                while (running && counter < 50) {
                    Thread.sleep(100);
                    counter++;
                    pw.print("Tick " + counter);
                }
                // Need to call complete() here if the test fails but complete()
                // should have been called by the listener. Use the flag to make
                // sure we only call complete once.
                if (completeCalled.compareAndSet(false, true)) {
                    asyncContext.complete();
                }
            } catch (IOException | InterruptedException e) {
                // Ignore
            }
        }
    }


    private static class TimeoutListener implements AsyncListener {

        private final AtomicBoolean ended = new AtomicBoolean(false);
        private final CountDownLatch latch;
        private final Ticker ticker;
        private final AtomicBoolean completeCalled;

        public TimeoutListener(CountDownLatch latch, Ticker ticker, AtomicBoolean completeCalled) {
            this.latch = latch;
            this.ticker = ticker;
            this.completeCalled = completeCalled;
        }

        @Override
        public void onTimeout(AsyncEvent event) throws IOException {
            ticker.end();
            if (ended.compareAndSet(false, true)) {
                PrintWriter pw = event.getAsyncContext().getResponse().getWriter();
                pw.write("PASS");
                pw.flush();
                // If the timeout fires we should always need to call complete()
                // here but use the flag to be safe.
                if (completeCalled.compareAndSet(false, true)) {
                    event.getAsyncContext().complete();
                }
            }
        }

        @Override
        public void onStartAsync(AsyncEvent event) throws IOException {
            // NO-OP
        }

        @Override
        public void onError(AsyncEvent event) throws IOException {
            // NO-OP
        }

        @Override
        public void onComplete(AsyncEvent event) throws IOException {
            if (ended.compareAndSet(false, true)) {
                PrintWriter pw = event.getAsyncContext().getResponse().getWriter();
                pw.write("FAIL");
                pw.flush();
            }
            try {
                // Wait for the async thread to end before we signal that the
                // test is complete. This avoids logging an exception about a
                // still running thread when the unit test shuts down.
                ticker.join();
                latch.countDown();
            } catch (InterruptedException e) {
                // Ignore
            }
        }
    }
}
