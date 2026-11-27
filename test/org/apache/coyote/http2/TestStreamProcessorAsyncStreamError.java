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
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
import org.apache.coyote.AbstractProtocol;

/**
 * Tests that the resources associated with a request that is in asynchronous mode are released when the connection
 * thread raises a stream level error for the stream.
 */
public class TestStreamProcessorAsyncStreamError extends Http2TestBase {

    // Generous so slow CI systems do not cause false failures. The test returns as soon as the expected state is seen.
    private static final long WAIT_LIMIT = 15000;

    private long asyncTimeout = 2000;
    private long maxConcurrentStreams = 200;
    private int maxConcurrentStreamExecution = -1;
    private final CountDownLatch blockStarted = new CountDownLatch(1);
    private final CountDownLatch blockLatch = new CountDownLatch(1);
    private final CountDownLatch delayedInService = new CountDownLatch(1);
    private final CountDownLatch delayedRelease = new CountDownLatch(1);
    private final CountDownLatch asyncCompleted = new CountDownLatch(1);
    private final CountDownLatch syncInService = new CountDownLatch(1);
    private final CountDownLatch syncRelease = new CountDownLatch(1);

    private final AtomicReference<AsyncContext> asyncContext = new AtomicReference<>();
    private final AtomicReference<Throwable> asyncError = new AtomicReference<>();


    /*
     * Control. No error. The async timeout fires and the application completes the request.
     */
    @Test
    public void testTimeoutWithoutStreamError() throws Exception {
        startAsyncRequest();
        waitForWaitingProcessors(getBaseline());
    }


    /*
     * An empty DATA frame on a half-closed (remote) stream is a stream error that is detected by the connection thread.
     * The async timeout must still be processed so the processor is removed from the waiting processors and recycled.
     */
    @Test
    public void testStreamErrorThenTimeout() throws Exception {
        startAsyncRequest();
        sendStreamError();
        waitForWaitingProcessors(getBaseline());
        assertApplicationSawStreamError();
    }


    /*
     * As above but the application completes the request after the stream error rather than waiting for the timeout.
     *
     * Only one stream may be processed at a time and that is occupied, so the ERROR event for the stream error is
     * queued. That makes the order deterministic: the application completes the request before the container has
     * processed the error, so complete() must succeed. Both events then have to be handled correctly.
     */
    @Test
    public void testStreamErrorThenComplete() throws Exception {
        maxConcurrentStreamExecution = 1;
        startAsyncRequest();

        // Occupy the only execution slot
        sendSimpleGetRequestTo(5, "/block");
        Assert.assertTrue("Blocking request did not start",
                blockStarted.await(WAIT_LIMIT, TimeUnit.MILLISECONDS));

        // The ERROR event is queued because there is no free execution slot
        sendStreamError();

        AsyncContext ac = asyncContext.get();
        Assert.assertNotNull(ac);
        ac.complete();

        // Free the execution slot. The queued events are now processed.
        blockLatch.countDown();
        waitForWaitingProcessors(getBaseline());
    }


    /*
     * The ERROR event for a stream error is processed on a container thread. If the number of streams being processed
     * has reached the limit, the event is queued. While it is queued the closed stream is replaced and may be pruned
     * from the connection's stream map. The processor is a waiting processor so it must still process the event when
     * it is eventually run and clean up, even though its stream is no longer in the map.
     */
    @Test
    public void testStreamErrorThenStreamPrunedBeforeEventProcessed() throws Exception {
        // Retain at most 5 * 3 streams. One more than the active (blocking) stream and the retained (errored, ERROR event
        // queued) stream so that new streams are not refused
        maxConcurrentStreams = 3;
        // Only one stream may be processed at a time
        maxConcurrentStreamExecution = 1;
        startAsyncRequest();

        // Occupy the only execution slot
        sendSimpleGetRequestTo(5, "/block");
        Assert.assertTrue("Blocking request did not start",
                blockStarted.await(WAIT_LIMIT, TimeUnit.MILLISECONDS));

        // Stream error for the async stream. The ERROR event is queued because there is no free execution slot.
        sendStreamError();

        // Open enough streams for the closed async stream to be a candidate for pruning. Reset each one immediately so
        // they don't count against the concurrent streams limit.
        for (int streamId = 7; streamId < 7 + 2 * 30; streamId += 2) {
            sendSimpleGetRequestTo(streamId, "/simple");
            sendRst(streamId, Http2Error.CANCEL.getCode());
        }
        // The connection thread processes frames in order so, once it has answered a ping, it has processed every
        // frame sent before it (including the creation and pruning of the streams above).
        waitForPingAck();

        // Free the execution slot. The queued events are now processed.
        blockLatch.countDown();

        // The blocking request ends and the stream 3 processor must clean up. Expect only the connection's own
        // processor to remain.
        waitForWaitingProcessors(getBaseline());
    }


    /*
     * The stream error is raised while the request is being processed but before the application has started
     * asynchronous processing. There is no async timeout so the processor must be notified of the error, once the
     * application has started asynchronous processing, else nothing would ever clean it up.
     */
    @Test
    public void testStreamErrorBeforeStartAsync() throws Exception {
        asyncTimeout = 0;
        startAsyncRequest("/delayed", false);
        Assert.assertTrue("Request did not reach the servlet",
                delayedInService.await(WAIT_LIMIT, TimeUnit.MILLISECONDS));

        // The request is being processed but is not (yet) asynchronous
        Assert.assertEquals(getBaseline(), waitingProcessors());
        sendStreamError();

        // Now allow the application to start asynchronous processing. There is no timeout so the container can only
        // complete the async processing by processing the error notification. Waiting for that avoids checking the
        // waiting processors before the request has entered async mode.
        delayedRelease.countDown();
        Assert.assertTrue("Asynchronous processing was never completed",
                asyncCompleted.await(WAIT_LIMIT, TimeUnit.MILLISECONDS));

        // The processor must have cleaned up
        waitForWaitingProcessors(getBaseline());
        assertApplicationSawStreamError();
    }


    /*
     * The stream error is raised while the request is being processed and the application never starts asynchronous
     * processing. The processor must complete normally, replace the stream and leave the connection usable without
     * sending a second reset for the stream.
     */
    @Test
    public void testStreamErrorWithoutAsync() throws Exception {
        startAsyncRequest("/delayed-sync", false);
        Assert.assertTrue("Request did not reach the servlet",
                syncInService.await(WAIT_LIMIT, TimeUnit.MILLISECONDS));

        sendStreamError();

        // Let the application return without having used asynchronous processing
        syncRelease.countDown();

        // The connection must still be usable
        sendSimpleGetRequestTo(5, "/simple");
        StringBuilder trace = new StringBuilder();
        boolean ended = false;
        int frames = 0;
        while (!ended && frames++ < 100) {
            output.clearTrace();
            try {
                parser.readFrame();
            } catch (IOException ioe) {
                Assert.fail("Connection closed before the response was complete. Trace so far: " + trace + " " + ioe);
            }
            trace.append(output.getTrace());
            ended = output.getTrace().contains("5-EndOfStream") || output.getTrace().contains("5-RST");
        }
        String result = trace.toString();
        Assert.assertTrue(result, result.contains("5-EndOfStream"));
        Assert.assertFalse(result, result.contains("5-RST"));
        Assert.assertFalse(result, result.contains("Goaway"));
        // sendStreamError() has already consumed the reset for stream 3. There must not be another.
        Assert.assertFalse(result, result.contains("3-RST"));

        // No async processing so nothing should be left registered
        Assert.assertEquals(getBaseline(), waitingProcessors());
    }


    /*
     * The stream error is set as the error for the request by the thread that processes the ERROR event so the
     * application should have been told about it.
     */
    private void assertApplicationSawStreamError() {
        Throwable t = asyncError.get();
        Assert.assertTrue("The application should have been given the stream error but got [" + t + "]",
                t instanceof StreamException);
    }


    private void waitForPingAck() throws Exception {
        byte[] payload = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 };
        sendPing(0, false, payload);
        String expected = "0-Ping-Ack-[1,2,3,4,5,6,7,8]";
        StringBuilder trace = new StringBuilder();
        int frames = 0;
        while (frames++ < 1000) {
            output.clearTrace();
            try {
                parser.readFrame();
            } catch (IOException ioe) {
                Assert.fail("Connection closed before the ping was acknowledged. Trace so far: " + trace + " " + ioe);
            }
            trace.append(output.getTrace());
            if (output.getTrace().contains(expected)) {
                return;
            }
        }
        Assert.fail("Ping was not acknowledged. Trace: " + trace);
    }


    private void sendSimpleGetRequestTo(int streamId, String path) throws IOException {
        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildGetRequest(frameHeader, headersPayload, null, streamId, path);
        writeFrame(frameHeader, headersPayload);
    }


    private int baseline = -1;

    private int getBaseline() {
        return baseline;
    }


    /*
     * The number of stream processors that are registered as waiting processors. The total number of waiting
     * processors can't be used because it also includes the connection's own processor which is added and removed as
     * the connection is processed so it would make the checks in these tests unreliable.
     */
    private int waitingProcessors() throws Exception {
        Field field = AbstractProtocol.class.getDeclaredField("waitingProcessors");
        field.setAccessible(true);
        Set<?> waiting = (Set<?>) field.get(getTomcatInstance().getConnector().getProtocolHandler());
        int count = 0;
        for (Object processor : waiting) {
            if (processor instanceof StreamProcessor) {
                count++;
            }
        }
        return count;
    }


    private void startAsyncRequest() throws Exception {
        startAsyncRequest("/async", true);
    }


    private void startAsyncRequest(String path, boolean waitForAsync) throws Exception {
        enableHttp2(maxConcurrentStreams);
        if (maxConcurrentStreamExecution > 0) {
            http2Protocol.setMaxConcurrentStreamExecution(maxConcurrentStreamExecution);
            // The test resets a lot of streams. Don't let the overhead protection close the connection.
            http2Protocol.setOverheadResetFactor(0);
        }

        Tomcat tomcat = getTomcatInstance();
        Context ctxt = getProgrammaticRootContext();
        // Target of the HTTP/2 upgrade request
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMapping("/simple", "simple");
        Wrapper w = Tomcat.addServlet(ctxt, "async", new AsyncServlet(asyncContext, asyncTimeout, asyncCompleted, asyncError));
        w.setAsyncSupported(true);
        ctxt.addServletMapping("/async", "async");
        Tomcat.addServlet(ctxt, "block", new HttpServlet() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                    throws ServletException, IOException {
                blockStarted.countDown();
                try {
                    blockLatch.await(WAIT_LIMIT, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
        });
        ctxt.addServletMapping("/block", "block");
        Wrapper delayed = Tomcat.addServlet(ctxt, "delayed", new HttpServlet() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                    throws ServletException, IOException {
                // Not yet async. Wait until the test has triggered the stream error.
                delayedInService.countDown();
                try {
                    delayedRelease.await(WAIT_LIMIT, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    // Ignore
                }
                new AsyncServlet(asyncContext, asyncTimeout, asyncCompleted, asyncError).doGet(req, resp);
            }
        });
        delayed.setAsyncSupported(true);
        ctxt.addServletMapping("/delayed", "delayed");
        Tomcat.addServlet(ctxt, "delayedSync", new HttpServlet() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                    throws ServletException, IOException {
                // Never goes async. Wait until the test has triggered the stream error.
                syncInService.countDown();
                try {
                    syncRelease.await(WAIT_LIMIT, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
        });
        ctxt.addServletMapping("/delayed-sync", "delayedSync");
        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse(maxConcurrentStreams);

        // Only stream processors are counted
        baseline = waitingProcessors();

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildGetRequest(frameHeader, headersPayload, null, 3, path);
        writeFrame(frameHeader, headersPayload);

        if (!waitForAsync) {
            return;
        }

        // Wait for the request to enter async mode
        long end = System.currentTimeMillis() + WAIT_LIMIT;
        while (waitingProcessors() <= baseline && System.currentTimeMillis() < end) {
            Thread.sleep(50);
        }
        Assert.assertTrue("Request did not enter async mode", waitingProcessors() > baseline);
    }


    private void sendStreamError() throws Exception {
        // Stream 3 is half-closed (remote). An empty DATA frame is a stream error.
        sendData(3, new byte[0]);

        // Server should reset the stream
        output.clearTrace();
        parser.readFrame();
        Assert.assertTrue(output.getTrace(), output.getTrace().startsWith("3-RST-"));
    }


    private void waitForWaitingProcessors(int expected) throws Exception {
        long end = System.currentTimeMillis() + WAIT_LIMIT;
        while (waitingProcessors() != expected && System.currentTimeMillis() < end) {
            Thread.sleep(100);
        }
        Assert.assertEquals("Processor for the stream was not removed from the waiting processors", expected,
                waitingProcessors());
    }


    private static class AsyncServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final transient AtomicReference<AsyncContext> asyncContext;
        private final long asyncTimeout;
        private final transient CountDownLatch asyncCompleted;
        private final transient AtomicReference<Throwable> asyncError;

        AsyncServlet(AtomicReference<AsyncContext> asyncContext, long asyncTimeout, CountDownLatch asyncCompleted,
                AtomicReference<Throwable> asyncError) {
            this.asyncContext = asyncContext;
            this.asyncTimeout = asyncTimeout;
            this.asyncCompleted = asyncCompleted;
            this.asyncError = asyncError;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            AsyncContext ac = req.startAsync();
            ac.setTimeout(asyncTimeout);
            ac.addListener(new AsyncListener() {
                @Override
                public void onComplete(AsyncEvent event) {
                    asyncCompleted.countDown();
                }

                @Override
                public void onTimeout(AsyncEvent event) {
                    ac.complete();
                }

                @Override
                public void onError(AsyncEvent event) {
                    asyncError.set(event.getThrowable());
                }

                @Override
                public void onStartAsync(AsyncEvent event) {
                }
            });
            asyncContext.set(ac);
        }
    }
}
