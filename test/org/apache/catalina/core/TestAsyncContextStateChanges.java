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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
        List<Object[]> parameterSets = new ArrayList<Object[]>();
        for (AsyncEnd asyncEnd : AsyncEnd.values()) {
            for (EndTiming endTiming : EndTiming.values()) {
                if (endTiming == EndTiming.THREAD_BEFORE_EXIT && asyncEnd.isError()) {
                    // Skip these tests for Tomcat 7 as they deadlock due to
                    // the write on the non-container thread being unable to
                    // progress until Servlet.service() exits since both
                    // require a lock on the socket.
                    // Note: Connector refactoring in 8.5.x onwards has removed
                    //       this limitation
                    continue;
                }
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
    private CountDownLatch servletLatch;
    private CountDownLatch threadLatch;
    private CountDownLatch closeLatch;
    private CountDownLatch endLatch;
    private boolean dispatch;

    @Test
    public void testAsync() throws Exception {
        dispatch = false;
        servletRequest = null;
        asyncContext = null;

        // Initialise tracking fields
        failed.set(true);
        servletLatch = new CountDownLatch(1);
        threadLatch = new CountDownLatch(1);
        closeLatch = new CountDownLatch(1);
        endLatch = new CountDownLatch(1);

        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        AsyncServlet bug63816Servlet = new AsyncServlet();
        Wrapper wrapper = Tomcat.addServlet(ctx, "bug63816Servlet", bug63816Servlet);
        wrapper.setAsyncSupported(true);
        ctx.addServletMapping("/*", "bug63816Servlet");

        tomcat.start();

        Client client = new Client();
        client.setPort(getPort());
        client.setRequest(new String[] { "GET / HTTP/1.1" + SimpleHttpClient.CRLF +
                                         "Host: localhost:" + SimpleHttpClient.CRLF +
                                         SimpleHttpClient.CRLF});
        client.connect();
        client.sendRequest();

        // Wait for Servlet to start processing request
        servletLatch.await();

        if (asyncEnd.isError()) {
            client.disconnect();
            closeLatch.countDown();

            Assert.assertTrue(
                    "Awaiting endLatch did not complete in 10 seconds",
                    endLatch.await(10, TimeUnit.SECONDS));
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
            servletLatch.countDown();

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
                case THREAD_AFTER_EXIT: {
                    t.start();
                    threadLatch.countDown();
                    break;
                }
                case THREAD_BEFORE_EXIT: {
                    t.start();
                    try {
                        threadLatch.await();
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
            if (endTiming == EndTiming.THREAD_AFTER_EXIT) {
                try {
                    threadLatch.await();
                    /*
                     * As much as I dislike it, I don't see any easy way around
                     * this hack. The latch above is released as the Servlet
                     * exits but we need to wait for the post processing to
                     * complete for the test to work as intended. In real-world
                     * applications this does mean that there is a real chance
                     * of an ISE. We may need to increase this delay for some CI
                     * systems.
                     */
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }

            // Trigger the error if necessary
            if (asyncEnd.isError()) {
                try {
                    closeLatch.await();
                } catch (InterruptedException e) {
                    // Ignore
                }
                try {
                    ServletResponse resp = asyncContext.getResponse();
                    resp.setContentType("text/plain");
                    resp.setCharacterEncoding("UTF-8");
                    OutputStream os = resp.getOutputStream();
                    resp.setContentType("text/plain");
                    for (int i = 0; i < 16; i++) {
                        os.write(TestCoyoteAdapter.TEXT_8K.getBytes("UTF-8"));
                    }
                } catch (IOException e) {
                    // Expected
                }
            }

            if (endTiming != EndTiming.THREAD_AFTER_EXIT) {
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
                    if (endTiming == EndTiming.THREAD_BEFORE_EXIT) {
                        threadLatch.countDown();
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
            // Need to handle timeouts for THREAD_AFTER_EXIT in the listener to
            // avoid concurrency issues.
            if (endTiming == EndTiming.THREAD_AFTER_EXIT) {
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
            // Need to handle errors for THREAD_AFTER_EXIT in the listener to
            // avoid concurrency issues.
            if (endTiming == EndTiming.THREAD_AFTER_EXIT) {
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

        private AsyncEnd(boolean none, boolean error) {
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
        THREAD_BEFORE_EXIT,
        THREAD_AFTER_EXIT
    }
}
