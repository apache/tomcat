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
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

/*
 * There are lots of uses of statics in this class that you'd never use in production but are used in these tests as the
 * tests will only be executed once per JVM and never concurrently.
 */
public class TestAsyncContextIoError extends TomcatBaseTest {

    private static final String RESPONSE_DATA = "AAAA";

    @Test
    public void testDispatchToApplication() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        AsyncServlet asyncServlet = new AsyncServlet();
        Wrapper asyncWrapper = Tomcat.addServlet(ctx, "async", asyncServlet);
        asyncWrapper.setAsyncSupported(true);
        ctx.addServletMappingDecoded("/async", "async");

        ErrorServlet errorServlet = new ErrorServlet();
        Tomcat.addServlet(ctx, "error", errorServlet);
        ctx.addServletMappingDecoded("/error", "error");

        tomcat.start();

        AsyncClient client = new AsyncClient();
        client.setPort(getPort());
        client.setRequest(new String[] { "GET /async HTTP/1.1" + SimpleHttpClient.CRLF +
                                         "Host: localhost:" + getPort() + SimpleHttpClient.CRLF +
                                         SimpleHttpClient.CRLF});
        client.connect();
        client.sendRequest();

        int responseDataCount = 0;

        // Response will have blank lines between chunks and chunk headers. Just count the data lines.
        while (responseDataCount < 3) {
            String responseLine = client.readLine();
            if (RESPONSE_DATA.equals(responseLine)) {
                responseDataCount++;
            }
        }

        client.disconnect();

        // Wait up to 5s for dispatch to error servlet
        int count = 0;
        while (ErrorServlet.getInvocationCount() == 0 && count < 50) {
            count++;
            Thread.sleep(100);
        }

        Assert.assertEquals(1, ErrorServlet.getInvocationCount());

        // Wait up to 5s for the async thread to stop
        count = 0;
        while (AsyncServlet.getThread().isAlive() && count < 50) {
            count++;
            Thread.sleep(100);
        }

    }


    private static class AsyncServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        private static Thread t;

        public static Thread getThread() {
            return t;
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            AsyncContext ac = request.startAsync();
            ac.setTimeout(0);

            AsyncRunnable runnable = new AsyncRunnable(ac);

            AsyncListener listener = new ErrorListener(ac, runnable);
            ac.addListener(listener);

            t = new Thread(runnable);
            t.start();
        }
    }


    private static class AsyncRunnable implements Runnable {
        private final AsyncContext ac;
        private volatile boolean stop = false;

        AsyncRunnable(AsyncContext ac) {
            this.ac = ac;
        }

        @Override
        public void run() {
            try {
                while (!stop) {
                    ac.getResponse().getWriter().println(RESPONSE_DATA);
                    ac.getResponse().getWriter().flush();
                    Thread.sleep(500);
                }
            } catch (IOException ioe) {
                // Ignore
            } catch (InterruptedException e) {
                // Ignore
            }
        }

        public void stop() {
            this.stop = true;
        }
    }


    private static class ErrorListener implements AsyncListener {
        private final AsyncContext ac;
        private final AsyncRunnable runnable;

        ErrorListener(AsyncContext ac, AsyncRunnable runnable) {
            this.ac = ac;
            this.runnable = runnable;
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
            runnable.stop();
            ac.dispatch("/error");
        }

        @Override
        public void onStartAsync(AsyncEvent event) throws IOException {
            // NO-OP
        }
    }


    private static class ErrorServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        private static AtomicInteger invocationCount = new AtomicInteger(0);

        public static int getInvocationCount() {
            return invocationCount.get();
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            invocationCount.incrementAndGet();
        }
    }


    private static class AsyncClient extends SimpleHttpClient {
        @Override
        public boolean isResponseBodyOK() {
            return true;
        }
    }
}
