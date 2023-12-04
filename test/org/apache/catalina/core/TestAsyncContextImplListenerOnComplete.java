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
package org.apache.catalina.core;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestAsyncContextImplListenerOnComplete extends TomcatBaseTest {

    /*
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=68227
     */
    @Test
    public void testAfterNetworkErrorThenDispatch() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Latch to track that complete has been called
        CountDownLatch completeLatch = new CountDownLatch(1);

        Wrapper servletWrapper = tomcat.addServlet("", "repro-servlet", new ReproServlet(completeLatch));
        servletWrapper.addMapping("/repro");
        servletWrapper.setAsyncSupported(true);
        servletWrapper.setLoadOnStartup(1);

        ctx.addServletMappingDecoded("/", "repro-servlet");

        tomcat.start();
        Thread.sleep(2000);

        triggerBrokenPipe(getPort());

        Assert.assertTrue(completeLatch.await(30, TimeUnit.SECONDS));
    }


    private void triggerBrokenPipe(int port) throws IOException, InterruptedException {
        try (Socket socket = new Socket()) {
            socket.setReceiveBufferSize(1);
            socket.connect(new InetSocketAddress("localhost", port));

            try (Writer writer = new OutputStreamWriter(socket.getOutputStream())) {
                writer.write("GET /repro" + SimpleHttpClient.CRLF +
                        "Accept: text/event-stream" + SimpleHttpClient.CRLF +
                        SimpleHttpClient.CRLF);
                writer.flush();
            }
            Thread.sleep(1_000);
        }
    }


    private static class ReproServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;
        private final EventSource eventSource = new EventSource();

        private final CountDownLatch completeLatch;

        ReproServlet(CountDownLatch completeLatch) {
            this.completeLatch = completeLatch;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
            resp.setContentType("text/event-stream");
            resp.setCharacterEncoding("UTF-8");
            resp.setStatus(200);
            AsyncContext context = req.startAsync();
            context.addListener(new ReproAsyncListener());
            eventSource.add(context);
        }

        private class ReproAsyncListener implements AsyncListener {
            @Override
            public void onComplete(AsyncEvent event) {
                try {
                    eventSource.remove(event.getAsyncContext());
                } finally {
                    completeLatch.countDown();
                }
            }

            @Override
            public void onTimeout(AsyncEvent event) {
                // Not expected
                new AssertionError("onTimeout").printStackTrace();
            }

            @Override
            public void onError(AsyncEvent event) {
                event.getAsyncContext().dispatch(); // Mirroring Spring's behaviour
            }

            @Override
            public void onStartAsync(AsyncEvent event) {
                // NO-OP
            }
        }
    }


    private static class EventSource {

        private final Set<AsyncContext> contexts = new HashSet<>();

        private EventSource() {
            Runnable r = () -> {
                while (true) {
                    try {
                        Thread.sleep(2000);
                        send("PING");
                    } catch (InterruptedException e) {
                        System.out.println("Failed to sleep: " + e);
                    }
                }
            };
            Thread t = new Thread(r);
            t.start();
        }

        public void send(String message) {
            for (AsyncContext context : contexts) {
                try {
                    PrintWriter writer = context.getResponse().getWriter();
                    writer.write("event: " + message + "\n\n");
                    writer.flush();
                } catch (Exception e) {
                    System.out.println("Failed to send event: " + e);
                }
            }
        }

        public void add(AsyncContext context) {
            contexts.add(context);
        }

        public void remove(AsyncContext context) {
            contexts.remove(context);
        }
    }
}
