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

package org.apache.coyote.http2;

import java.io.IOException;
import java.nio.ByteBuffer;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;

public class TestAsyncReadListener extends Http2TestBase {

    private AsyncServlet asyncServlet;

    @Before
    public void before() {
        asyncServlet = new AsyncServlet();
    }


    @Test
    public void testEmptyWindowDefaultReadTimeout() throws Exception {
        doTestEmptyWindowMaximumTimeout(false);
    }


    @Test
    public void testEmptyWindowMaximumReadTimeout() throws Exception {
        doTestEmptyWindowMaximumTimeout(true);
    }


    public void doTestEmptyWindowMaximumTimeout(boolean useMaxReadTimeout) throws Exception {
        http2Connect();

        if (useMaxReadTimeout) {
            http2Protocol.setStreamReadTimeout(Integer.MAX_VALUE);
            http2Protocol.setReadTimeout(Integer.MAX_VALUE);
        }

        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        byte[] dataFrameHeader = new byte[9];
        ByteBuffer dataPayload = ByteBuffer.allocate(256);
        byte[] trailerFrameHeader = new byte[9];
        ByteBuffer trailerPayload = ByteBuffer.allocate(256);


        buildPostRequest(headersFrameHeader, headersPayload, false, null, -1, "/async", dataFrameHeader, dataPayload,
            null, true, 3);
        buildTrailerHeaders(trailerFrameHeader, trailerPayload, 3);

        synchronized (asyncServlet) {
            // Write the headers
            writeFrame(headersFrameHeader, headersPayload);
            asyncServlet.wait(4000);
            s.close();
            asyncServlet.wait(4000);
        }
        Assert.assertNotNull(asyncServlet.t);
    }

    @Override
    protected void configureAndStartWebApplication() throws LifecycleException {
        Tomcat tomcat = getTomcatInstance();

        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");
        Wrapper w = Tomcat.addServlet(ctxt, "async", asyncServlet);
        w.setAsyncSupported(true);
        ctxt.addServletMappingDecoded("/async", "async");
        tomcat.start();
    }

    public static class AsyncServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;
        private volatile Throwable t;
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            final AsyncContext asyncContext = req.startAsync();
            asyncContext.getRequest().getInputStream().setReadListener(new ReadListener() {
                @Override
                public void onDataAvailable() throws IOException {
                    System.out.println("RL-onDataAvailable");
                    synchronized (AsyncServlet.this) {
                        AsyncServlet.this.notifyAll();
                    }
                }

                @Override
                public void onAllDataRead() throws IOException {
                    System.out.println("RL-onAllDataRead");
                }

                @Override
                public void onError(Throwable throwable) {
                    System.out.println("RL-onError ");
                    System.out.println(throwable);
                    synchronized (AsyncServlet.this) {
                        t = throwable;
                        AsyncServlet.this.notifyAll();
                        asyncContext.complete();
                    }
                }
            });
            System.out.println("setReadListener");
            asyncContext.addListener(new AsyncListener() {

                @Override
                public void onComplete(AsyncEvent event) throws IOException {
                    System.out.println("AL-onComplete");
                }

                @Override
                public void onTimeout(AsyncEvent event) throws IOException {
                    System.out.println("AL-onTimeout");
                }

                @Override
                public void onError(AsyncEvent event) throws IOException {
                    System.out.println("AL-onError");
                }

                @Override
                public void onStartAsync(AsyncEvent event) throws IOException {
                    System.out.println("AL-onStartAsync");
                }
            });
            System.out.println("setAsyncListener");
        }
    }
}