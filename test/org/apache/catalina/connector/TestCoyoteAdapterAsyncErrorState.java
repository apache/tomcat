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
package org.apache.catalina.connector;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.valves.ValveBase;
import org.apache.coyote.ActionCode;

/**
 * Tests the handling in {@link CoyoteAdapter#service} of a request that starts async processing and requests a
 * dispatch during the initial service call while the connection is, or goes into, an error state. The error is applied
 * by the servlet between {@code startAsync()} and {@code dispatch()} so the state machine passes through the same
 * states in the same order every time. No client reset, timing or large response is required.
 */
public class TestCoyoteAdapterAsyncErrorState extends TomcatBaseTest {

    private static final String REQUEST_ATTRIBUTE = TestCoyoteAdapterAsyncErrorState.class.getName() + ".request";

    private StandardContext context;

    /*
     * BZ 70290: CLOSE_NOW prevents any further IO so the dispatch requested after the error can not be performed
     * (MUST_ERROR -> MUST_DISPATCH). onComplete() has to be called once and the in-progress async count has to be
     * balanced. Previously the request was recycled while still in MUST_DISPATCH and the count leaked.
     */
    @Test
    public void testBz70290DispatchAfterCloseNowIsCompleted() throws Exception {
        TestServlet servlet = new TestServlet(ActionCode.CLOSE_NOW);
        startTomcat(servlet);

        sendRequest();

        assertCompletedOnce(servlet);
        Assert.assertEquals("The dispatch can not be performed", 0, servlet.dispatches.get());
    }


    /*
     * BZ 70290: CLOSE_CLEAN is an error state but it still permits IO. Whatever the outcome for the dispatch, the
     * request must not be recycled with the async processing neither completed nor dispatched: onComplete() has to be
     * called once and the in-progress async count has to be balanced.
     */
    @Test
    public void testBz70290DispatchAfterCloseCleanIsCompleted() throws Exception {
        TestServlet servlet = new TestServlet(ActionCode.DISABLE_SWALLOW_INPUT);
        startTomcat(servlet);

        sendRequest();

        assertCompletedOnce(servlet);
    }


    /*
     * BZ 70290: Control. With no error the dispatch has to be performed and the client has to receive the response.
     */
    @Test
    public void testBz70290DispatchWithoutErrorIsPerformed() throws Exception {
        TestServlet servlet = new TestServlet(null);
        startTomcat(servlet);

        String response = sendRequest();

        Assert.assertTrue(response, response.startsWith("HTTP/1.1 200"));
        Assert.assertTrue(response, response.contains("DISPATCHED"));
        Assert.assertEquals(1, servlet.dispatches.get());
        assertCompletedOnce(servlet);
    }


    /*
     * BZ 70290: Parity with the dispatch case. complete() requested after CLOSE_NOW has to result in onComplete() being
     * called once and the in-progress async count being balanced.
     */
    @Test
    public void testBz70290CompleteAfterCloseNowIsCompleted() throws Exception {
        TestServlet servlet = new TestServlet(ActionCode.CLOSE_NOW, true);
        startTomcat(servlet);

        sendRequest();

        assertCompletedOnce(servlet);
    }


    /*
     * BZ 70290: Parity with the dispatch case. complete() requested after CLOSE_CLEAN has to result in onComplete()
     * being called once and the in-progress async count being balanced.
     */
    @Test
    public void testBz70290CompleteAfterCloseCleanIsCompleted() throws Exception {
        TestServlet servlet = new TestServlet(ActionCode.DISABLE_SWALLOW_INPUT, true);
        startTomcat(servlet);

        sendRequest();

        assertCompletedOnce(servlet);
    }


    /*
     * BZ 70290: Control. complete() with no error completes normally.
     */
    @Test
    public void testBz70290CompleteWithoutErrorIsCompleted() throws Exception {
        TestServlet servlet = new TestServlet(null, true);
        startTomcat(servlet);

        String response = sendRequest();

        Assert.assertTrue(response, response.startsWith("HTTP/1.1 200"));
        assertCompletedOnce(servlet);
    }


    /*
     * BZ 70290: Output written to the response, but not yet flushed, before complete() was called must still be
     * delivered to the client when the error state (CLOSE_CLEAN) permits IO. It must not be discarded when the request
     * is recycled.
     */
    @Test
    public void testBz70290CompleteAfterCloseCleanDeliversBufferedOutput() throws Exception {
        TestServlet servlet = new TestServlet(ActionCode.DISABLE_SWALLOW_INPUT, true).withOutput("BUFFERED-OUTPUT");
        startTomcat(servlet);

        String response = sendRequest();

        Assert.assertTrue(response, response.contains("BUFFERED-OUTPUT"));
        assertCompletedOnce(servlet);
    }


    /*
     * BZ 70290: As above but the dispatch requested by the application can not be performed because the connection is
     * going to be closed. The output already written must still be delivered.
     */
    @Test
    public void testBz70290DispatchAfterCloseCleanDeliversBufferedOutput() throws Exception {
        TestServlet servlet = new TestServlet(ActionCode.DISABLE_SWALLOW_INPUT).withOutput("BUFFERED-OUTPUT");
        startTomcat(servlet);

        String response = sendRequest();

        Assert.assertTrue(response, response.contains("BUFFERED-OUTPUT"));
        assertCompletedOnce(servlet);
    }


    /*
     * BZ 70290: Control. With no error the buffered output is delivered when complete() is called.
     */
    @Test
    public void testBz70290CompleteWithoutErrorDeliversBufferedOutput() throws Exception {
        TestServlet servlet = new TestServlet(null, true).withOutput("BUFFERED-OUTPUT");
        startTomcat(servlet);

        String response = sendRequest();

        Assert.assertTrue(response, response.contains("BUFFERED-OUTPUT"));
        assertCompletedOnce(servlet);
    }


    /*
     * BZ 70290: As above but complete() is called from AsyncListener.onTimeout() so the request is processed by
     * CoyoteAdapter.asyncDispatch() rather than by the container thread that made the initial service call.
     */
    @Test
    public void testBz70290CompleteFromTimeoutAfterCloseCleanDeliversBufferedOutput() throws Exception {
        TestServlet servlet = new TestServlet(ActionCode.DISABLE_SWALLOW_INPUT, true).withOutput("BUFFERED-OUTPUT")
                .completeFromTimeout();
        startTomcat(servlet);

        String response = sendRequest();

        Assert.assertTrue(response, response.contains("BUFFERED-OUTPUT"));
        assertCompletedOnce(servlet);
    }


    /*
     * BZ 70290: The response has been committed (the output was flushed to the client) before the connection went into
     * an error state that permits IO and dispatch() was called. The dispatch can not be performed so onComplete() has
     * to be called once, the in-progress async count has to be balanced and the output already sent has to have been
     * delivered.
     */
    @Test
    public void testBz70290DispatchAfterCloseCleanWithCommittedResponse() throws Exception {
        TestServlet servlet =
                new TestServlet(ActionCode.DISABLE_SWALLOW_INPUT).withOutput("FLUSHED-OUTPUT").withFlush();
        startTomcat(servlet);

        String response = sendRequest();

        Assert.assertTrue(response, response.contains("FLUSHED-OUTPUT"));
        assertCompletedOnce(servlet);
        Assert.assertEquals("onError() must be called once", 1, servlet.errors.get());
    }


    /*
     * BZ 70290: As above but complete() is called rather than dispatch().
     */
    @Test
    public void testBz70290CompleteAfterCloseCleanWithCommittedResponse() throws Exception {
        TestServlet servlet =
                new TestServlet(ActionCode.DISABLE_SWALLOW_INPUT, true).withOutput("FLUSHED-OUTPUT").withFlush();
        startTomcat(servlet);

        String response = sendRequest();

        Assert.assertTrue(response, response.contains("FLUSHED-OUTPUT"));
        assertCompletedOnce(servlet);
        Assert.assertEquals("onError() must be called once", 1, servlet.errors.get());
    }


    /*
     * BZ 70290: As above but the connection is closed (CLOSE_NOW) rather than closed cleanly.
     */
    @Test
    public void testBz70290DispatchAfterCloseNowWithCommittedResponse() throws Exception {
        TestServlet servlet = new TestServlet(ActionCode.CLOSE_NOW).withOutput("FLUSHED-OUTPUT").withFlush();
        startTomcat(servlet);

        sendRequest();

        assertCompletedOnce(servlet);
    }


    /*
     * BZ 70290: When an error occurs after the response has been committed onError() must always be called and
     * whatever onError() does (nothing, dispatch() or complete()) has to be handled the way ErrorReportValve handles
     * errors for a committed response: the response that was sent is left as it is and the connection is closed. In all
     * cases onComplete() has to be called once and the in-progress async count has to be balanced.
     */
    private void doCommittedResponseOnError(ActionCode errorAction, String onErrorOperation) throws Exception {
        TestServlet servlet = new TestServlet(errorAction).withOutput("FLUSHED-OUTPUT").withFlush()
                .withoutInitialOperation().withOnErrorOperation(onErrorOperation);
        startTomcat(servlet);

        String response = sendRequest();

        Assert.assertTrue(response, response.contains("FLUSHED-OUTPUT"));
        Assert.assertEquals("onError() must be called once", 1, servlet.errors.get());
        Assert.assertEquals("The dispatch can not be performed", 0, servlet.dispatches.get());
        assertCompletedOnce(servlet);
    }


    @Test
    public void testBz70290CommittedCloseCleanOnErrorDoesNothing() throws Exception {
        doCommittedResponseOnError(ActionCode.DISABLE_SWALLOW_INPUT, null);
    }


    @Test
    public void testBz70290CommittedCloseCleanOnErrorDispatches() throws Exception {
        doCommittedResponseOnError(ActionCode.DISABLE_SWALLOW_INPUT, "dispatch");
    }


    @Test
    public void testBz70290CommittedCloseCleanOnErrorCompletes() throws Exception {
        doCommittedResponseOnError(ActionCode.DISABLE_SWALLOW_INPUT, "complete");
    }


    @Test
    public void testBz70290CommittedCloseNowOnErrorDoesNothing() throws Exception {
        doCommittedResponseOnError(ActionCode.CLOSE_NOW, null);
    }


    @Test
    public void testBz70290CommittedCloseNowOnErrorDispatches() throws Exception {
        doCommittedResponseOnError(ActionCode.CLOSE_NOW, "dispatch");
    }


    @Test
    public void testBz70290CommittedCloseNowOnErrorCompletes() throws Exception {
        doCommittedResponseOnError(ActionCode.CLOSE_NOW, "complete");
    }


    private void startTomcat(TestServlet servlet) throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = getProgrammaticRootContext();
        context = (StandardContext) ctx;

        Wrapper w = Tomcat.addServlet(ctx, "test", servlet);
        w.setAsyncSupported(true);
        ctx.addServletMapping("/test", "test");

        // Makes the Catalina request available to the servlet so it can put the connection into an error state
        ctx.getPipeline().addValve(new ValveBase(true) {
            @Override
            public void invoke(Request request, Response response) throws IOException, ServletException {
                request.setAttribute(REQUEST_ATTRIBUTE, request);
                getNext().invoke(request, response);
            }
        });

        tomcat.start();
    }


    /*
     * Raw socket rather than HttpURLConnection since the latter silently retries a GET if the connection is closed
     * without a response.
     */
    private String sendRequest() throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), getPort())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(("GET /test HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            InputStream is = socket.getInputStream();
            byte[] buf = new byte[1024];
            try {
                int n;
                while ((n = is.read(buf)) >= 0) {
                    baos.write(buf, 0, n);
                }
            } catch (IOException ioe) {
                // Connection reset or timeout. Whatever was received is returned.
            }
            return baos.toString(StandardCharsets.ISO_8859_1);
        }
    }


    private void assertCompletedOnce(TestServlet servlet) throws Exception {
        Assert.assertTrue("onComplete() was not called", servlet.completed.await(5, TimeUnit.SECONDS));
        Assert.assertEquals("onComplete() must be called exactly once", 1, servlet.completions.get());

        // The count is decremented just after onComplete() returns
        StandardContext ctx = context;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (ctx.getInProgressAsyncCount() != 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        Assert.assertEquals("In-progress async count must be balanced", 0, ctx.getInProgressAsyncCount());
    }


    private static class TestServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final ActionCode errorAction;
        private final boolean complete;
        private volatile String output;
        private volatile boolean flush;
        private volatile boolean noInitialOperation;
        private volatile String onErrorOperation;
        private volatile boolean completeFromTimeout;

        final AtomicInteger dispatches = new AtomicInteger();
        final AtomicInteger completions = new AtomicInteger();
        final AtomicInteger errors = new AtomicInteger();
        final CountDownLatch completed = new CountDownLatch(1);

        TestServlet(ActionCode errorAction) {
            this(errorAction, false);
        }

        /*
         * Do not call dispatch() or complete() from service(). The container is left to report the error.
         */
        TestServlet withoutInitialOperation() {
            this.noInitialOperation = true;
            return this;
        }

        /*
         * Call dispatch() ("dispatch") or complete() ("complete") from AsyncListener.onError()
         */
        TestServlet withOnErrorOperation(String operation) {
            this.onErrorOperation = operation;
            return this;
        }

        TestServlet withFlush() {
            this.flush = true;
            return this;
        }

        /*
         * Write the given output to the response before the error occurs. It is deliberately not flushed so it is
         * still held in the Catalina response buffer when the error occurs.
         */
        TestServlet withOutput(String output) {
            this.output = output;
            return this;
        }

        /*
         * Defer the output, the error and the call to complete() to AsyncListener.onTimeout() so they happen after the
         * initial service call has finished and are processed via CoyoteAdapter.asyncDispatch().
         */
        TestServlet completeFromTimeout() {
            this.completeFromTimeout = true;
            return this;
        }

        TestServlet(ActionCode errorAction, boolean complete) {
            this.errorAction = errorAction;
            this.complete = complete;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            if (req.getDispatcherType() == DispatcherType.ASYNC) {
                dispatches.incrementAndGet();
                resp.setContentType("text/plain");
                resp.getWriter().print("DISPATCHED");
                return;
            }

            AsyncContext ac = req.startAsync();
            ac.addListener(new AsyncListener() {
                @Override
                public void onComplete(AsyncEvent event) {
                    completions.incrementAndGet();
                    completed.countDown();
                }

                @Override
                public void onTimeout(AsyncEvent event) throws IOException {
                    if (completeFromTimeout) {
                        writeOutputAndFail(req, resp);
                        ac.complete();
                    }
                }

                @Override
                public void onError(AsyncEvent event) {
                    errors.incrementAndGet();
                    if ("dispatch".equals(onErrorOperation)) {
                        event.getAsyncContext().dispatch();
                    } else if ("complete".equals(onErrorOperation)) {
                        event.getAsyncContext().complete();
                    }
                }

                @Override
                public void onStartAsync(AsyncEvent event) {
                    // NO-OP
                }
            });

            if (completeFromTimeout) {
                ac.setTimeout(100);
                return;
            }

            writeOutputAndFail(req, resp);

            if (noInitialOperation) {
                return;
            }

            // Request the dispatch (or completion) on the container thread during the initial service call
            if (complete) {
                ac.complete();
            } else {
                ac.dispatch();
            }
        }

        private void writeOutputAndFail(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            if (output != null) {
                resp.setContentType("text/plain");
                resp.getWriter().print(output);
                if (flush) {
                    // Commits the response so the status and headers have been sent to the client
                    resp.flushBuffer();
                }
            }

            if (errorAction != null) {
                // The connection fails
                Request request = (Request) req.getAttribute(REQUEST_ATTRIBUTE);
                request.getCoyoteRequest().action(errorAction, null);
            }
        }
    }
}
