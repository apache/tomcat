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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.TestCoyoteAdapter;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

/*
 * Derived from a test for https://bz.apache.org/bugzilla/show_bug.cgi?id=63816
 * Expanded to cover https://bz.apache.org/bugzilla/show_bug.cgi?id=63817 and
 * additional scenarios.
 */
@RunWith(Parameterized.class)
public class TestAsyncContextStateChanges extends TomcatBaseTest {

    @Parameterized.Parameters(name = "{index}: end [{0}], timing [{1}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();
        for (AsyncEnd asyncEnd : AsyncEnd.values()) {
            for (EndTiming endTiming : EndTiming.values()) {
                parameterSets.add(new Object[] { asyncEnd, endTiming });
            }
        }
        return parameterSets;
    }

    @Parameter(0)
    public AsyncEnd asyncEnd;

    @Parameter(1)
    public EndTiming endTiming;

    private ServletRequest servletRequest = null;
    private AsyncContext asyncContext = null;
    private AtomicBoolean failed = new AtomicBoolean();
    private CountDownLatch servletStartLatch;
    private CountDownLatch threadCompleteLatch;
    private CountDownLatch clientDisconnectLatch;
    private CountDownLatch endLatch;
    private boolean dispatch;

    @Test
    public void testAsync() throws Exception {
        dispatch = false;
        servletRequest = null;
        asyncContext = null;

        // Initialise tracking fields
        failed.set(true);
        servletStartLatch = new CountDownLatch(1);
        threadCompleteLatch = new CountDownLatch(1);
        clientDisconnectLatch = new CountDownLatch(1);
        endLatch = new CountDownLatch(1);

        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        AsyncServlet bug63816Servlet = new AsyncServlet();
        Wrapper wrapper = Tomcat.addServlet(ctx, "bug63816Servlet", bug63816Servlet);
        wrapper.setAsyncSupported(true);
        ctx.addServletMappingDecoded("/*", "bug63816Servlet");

        tomcat.start();

        Client client = new Client();
        client.setPort(getPort());
        client.setRequest(new String[] { "GET / HTTP/1.1" + SimpleHttpClient.CRLF +
                                         "Host: localhost:" + SimpleHttpClient.CRLF +
                                         SimpleHttpClient.CRLF});
        client.connect();
        client.sendRequest();

        // Wait for Servlet to start processing request
        servletStartLatch.await();

        if (asyncEnd.isError()) {
            client.disconnect();
            clientDisconnectLatch.countDown();
            try {
                endLatch.await();
            } catch (InterruptedException e) {
                // Ignore
            }
        } else {
            client.setUseContentLength(true);
            client.readResponse(true);
        }

        Assert.assertFalse(failed.get());
    }


    private static final class Client extends SimpleHttpClient {

        @Override
        public boolean isResponseBodyOK() {
            return true;
        }
    }


    private final class AsyncServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            servletStartLatch.countDown();

            if (dispatch) {
                return;
            }

            if (!asyncEnd.isError()) {
                resp.setContentType("text/plain");
                resp.setCharacterEncoding("UTF-8");
                resp.setContentLength(2);
                resp.getWriter().print("OK");
            }

            servletRequest = req;
            asyncContext = req.startAsync();
            asyncContext.addListener(new Listener());
            if (!asyncEnd.isError()) {
                // Use a short timeout so the test does not pause for too long
                // waiting for the timeout to be triggered.
                asyncContext.setTimeout(1000);
            }
            Thread t = new NonContainerThread();

            switch (endTiming) {
                case INLINE: {
                    t.run();
                    break;
                }
                case THREAD_COMPLETES_AFTER_SERVLET_EXIT: {
                    t.start();
                    break;
                }
                case THREAD_COMPLETES_BEFORE_SERVLET_EXIT: {
                    t.start();
                    try {
                        threadCompleteLatch.await();
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                    endLatch.countDown();
                    break;
                }
            }
        }
    }


    private final class NonContainerThread extends Thread {

        @Override
        public void run() {
            if (endTiming == EndTiming.THREAD_COMPLETES_AFTER_SERVLET_EXIT) {
                try {
                    /*
                     * As much as I dislike it, I don't see any easy way around
                     * this hack. The thread is started as the Servlet exits but
                     * we need to wait for the post processing to complete for
                     * the test to work as intended. In real-world applications
                     * this does mean that there is a real chance of an ISE. We
                     * may need to increase this delay for some CI systems.
                     */
                    sleep(1000);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }

            // Trigger the error if necessary
            if (asyncEnd.isError()) {
                try {
                    clientDisconnectLatch.await();
                } catch (InterruptedException e) {
                    // Ignore
                }
                try {
                    ServletResponse resp = asyncContext.getResponse();
                    resp.setContentType("text/plain");
                    resp.setCharacterEncoding("UTF-8");
                    OutputStream os = resp.getOutputStream();
                    resp.setContentType("text/plain");
                    // Needs to be large enough to fill buffers and trigger
                    // the IOE for the test to work as expected.
                    for (int i = 0; i < 64; i++) {
                        os.write(TestCoyoteAdapter.TEXT_8K.getBytes(StandardCharsets.UTF_8));
                    }
                } catch (IOException e) {
                    // Expected
                }
            }

            if (endTiming != EndTiming.THREAD_COMPLETES_AFTER_SERVLET_EXIT) {
                try {
                    switch (asyncEnd) {
                        case COMPLETE:
                        case ERROR_COMPLETE: {
                            asyncContext.complete();
                            break;
                        }
                        case DISPATCH:
                        case ERROR_DISPATCH: {
                            dispatch = true;
                            asyncContext.dispatch();
                            break;
                        }
                        case NONE:
                        case ERROR_NONE: {
                            break;
                        }
                    }

                    // The request must stay in async mode until doGet() exists
                    if (servletRequest.isAsyncStarted()) {
                        failed.set(false);
                    }
                } finally {
                    if (endTiming == EndTiming.THREAD_COMPLETES_BEFORE_SERVLET_EXIT) {
                        threadCompleteLatch.countDown();
                    }
                }
            }
        }
    }


    private class Listener implements AsyncListener {

        @Override
        public void onComplete(AsyncEvent event) throws IOException {
            if (endTiming == EndTiming.INLINE) {
                endLatch.countDown();
            }
        }

        @Override
        public void onTimeout(AsyncEvent event) throws IOException {
            // Need to handle timeouts for THREAD_COMPLETES_AFTER_SERVLET_EXIT in the listener to
            // avoid concurrency issues.
            if (endTiming == EndTiming.THREAD_COMPLETES_AFTER_SERVLET_EXIT) {
                switch (asyncEnd) {
                    case COMPLETE: {
                        asyncContext.complete();
                        break;
                    }
                    case DISPATCH: {
                        dispatch = true;
                        asyncContext.dispatch();
                        break;
                    }
                    default:
                        // NO-OP
                }
            }
            if (servletRequest.isAsyncStarted() == asyncEnd.isNone()) {
                failed.set(false);
            }
            endLatch.countDown();
        }

        @Override
        public void onError(AsyncEvent event) throws IOException {
            // Need to handle errors for THREAD_COMPLETES_AFTER_SERVLET_EXIT in the listener to
            // avoid concurrency issues.
            if (endTiming == EndTiming.THREAD_COMPLETES_AFTER_SERVLET_EXIT) {
                switch (asyncEnd) {
                    case ERROR_COMPLETE: {
                        asyncContext.complete();
                        break;
                    }
                    case ERROR_DISPATCH: {
                        dispatch = true;
                        asyncContext.dispatch();
                        break;
                    }
                    default:
                        // NO-OP
                }
                if (servletRequest.isAsyncStarted() == asyncEnd.isNone()) {
                    failed.set(false);
                }
                endLatch.countDown();
            }
        }

        @Override
        public void onStartAsync(AsyncEvent event) throws IOException {
            // NO-OP
        }
    }


    public enum AsyncEnd {

        NONE          ( true, false),
        COMPLETE      (false, false),
        DISPATCH      (false, false),
        ERROR_NONE    ( true,  true),
        ERROR_COMPLETE(false,  true),
        ERROR_DISPATCH(false,  true);

        final boolean none;
        final boolean error;

        AsyncEnd(boolean none, boolean error) {
            this.none = none;
            this.error = error;
        }

        public boolean isNone() {
            return none;
        }

        public boolean isError() {
            return error;
        }
    }


    public enum EndTiming {
        INLINE,
        THREAD_COMPLETES_BEFORE_SERVLET_EXIT,
        THREAD_COMPLETES_AFTER_SERVLET_EXIT
    }
}
