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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;

/*
 * Based on
 * https://bz.apache.org/bugzilla/show_bug.cgi?id=66841
 */
public class TestAsyncError extends Http2TestBase {

    @Test
    public void testError() throws Exception {

        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");
        Wrapper w = Tomcat.addServlet(ctxt, "async", new AsyncErrorServlet());
        w.setAsyncSupported(true);
        ctxt.addServletMappingDecoded("/async", "async");
        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        // Send request
        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildGetRequest(frameHeader, headersPayload, null, 3, "/async");
        writeFrame(frameHeader, headersPayload);

        // Read response
        // Headers
        parser.readFrame();

        // Read 3 'events'
        parser.readFrame();
        parser.readFrame();
        parser.readFrame();

        // Reset the stream
        sendRst(3, Http2Error.CANCEL.getCode());

        int count = 0;
        while (count < 50 && TestListener.getErrorCount() == 0) {
            count++;
            Thread.sleep(100);
        }

        Assert.assertEquals(1, TestListener.getErrorCount());
    }


    private static final class AsyncErrorServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

            final AsyncContext asyncContext = req.startAsync();
            TestListener testListener = new TestListener();
            asyncContext.addListener(testListener);

            MessageGenerator msgGenerator = new MessageGenerator(resp);
            asyncContext.start(msgGenerator);
        }
    }


    private static final class MessageGenerator implements Runnable {

        private final HttpServletResponse resp;

        MessageGenerator(HttpServletResponse resp) {
            this.resp = resp;
        }

        @Override
        public void run() {
            try {
                resp.setContentType("text/plain");
                resp.setCharacterEncoding(StandardCharsets.UTF_8);
                PrintWriter pw = resp.getWriter();

                while (true) {
                    pw.println("OK");
                    pw.flush();
                    if (pw.checkError()) {
                        throw new IOException();
                    }
                    Thread.sleep(1000);
                }
            } catch (IOException | InterruptedException e) {
                // Expect async error handler to handle clean-up
            }
        }
    }


    private static final class TestListener implements AsyncListener {

        private static final AtomicInteger errorCount = new AtomicInteger(0);

        public static int getErrorCount() {
            return errorCount.get();
        }

        @Override
        public void onComplete(AsyncEvent event) throws IOException {
            // NO-OP
        }

        @Override
        public void onTimeout(AsyncEvent event) throws IOException {
            // NO-OP
        }

        @Override
        public void onError(AsyncEvent event) throws IOException {
            errorCount.incrementAndGet();
            event.getAsyncContext().complete();
        }

        @Override
        public void onStartAsync(AsyncEvent event) throws IOException {
            // NO-OP
        }
    }
}
