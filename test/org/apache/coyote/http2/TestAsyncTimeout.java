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
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;

public class TestAsyncTimeout extends Http2TestBase {

    @Ignore // Until this HTTP/2 + async timeouts is fixed
    @Test
    public void testTimeout() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        Context ctxt = tomcat.addContext("", null);
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");
        Wrapper w = Tomcat.addServlet(ctxt, "async", new AsyncTimeoutServlet());
        w.setAsyncSupported(true);
        ctxt.addServletMappingDecoded("/async", "async");
        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        // Reset connection window size after intial response
        sendWindowUpdate(0, SimpleServlet.CONTENT_LENGTH);

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

        // Check that the right number of bytes were received
        String trace = output.getTrace();
        Assert.assertTrue(trace, trace.contains("PASS"));
    }


    public static class AsyncTimeoutServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException {

            final AsyncContext asyncContext = request.startAsync();

            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("text/plain");
            response.setCharacterEncoding("UTF-8");

            asyncContext.addListener(new AsyncListener() {

                AtomicBoolean ended = new AtomicBoolean(false);

                @Override
                public void onTimeout(AsyncEvent event) throws IOException {
                    if (ended.compareAndSet(false, true)) {
                        PrintWriter pw = event.getAsyncContext().getResponse().getWriter();
                        pw.write("PASS");
                        pw.flush();
                        event.getAsyncContext().complete();
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
                }
            });


            asyncContext.setTimeout(2000);

            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                // Ignore
            }
            asyncContext.complete();
        }
    }
}
