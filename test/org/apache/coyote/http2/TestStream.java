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
import java.nio.ByteBuffer;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.ActionCode;
import org.apache.coyote.AsyncContextCallback;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.easymock.EasyMock;

import trailers.ResponseTrailers;


public class TestStream extends Http2TestBase {

    /*
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=61120
     */
    @Test
    public void testPathParam() throws Exception {

        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMapping("/simple", "simple");
        Tomcat.addServlet(ctxt, "pathparam", new PathParam());
        ctxt.addServletMapping("/pathparam", "pathparam");

        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildGetRequest(frameHeader, headersPayload, null, 3, "/pathparam;jsessionid=" + PathParam.EXPECTED_SESSION_ID);
        writeFrame(frameHeader, headersPayload);

        readSimpleGetResponse();

        Assert.assertEquals("3-HeadersStart\n" + "3-Header-[:status]-[200]\n" +
                "3-Header-[content-type]-[text/plain;charset=UTF-8]\n" + "3-Header-[content-length]-[2]\n" +
                "3-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n" + "3-HeadersEnd\n" + "3-Body-2\n" +
                "3-EndOfStream\n", output.getTrace());
    }


    @Test
    public void testResponseTrailerFields() throws Exception {

        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMapping("/simple", "simple");
        Tomcat.addServlet(ctxt, "trailers", new ResponseTrailers());
        ctxt.addServletMapping("/trailers", "trailers");

        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildGetRequest(frameHeader, headersPayload, null, 3, "/trailers");
        writeFrame(frameHeader, headersPayload);

        // Headers
        parser.readFrame();
        // Body
        parser.readFrame();
        // Trailers
        parser.readFrame();

        Assert.assertEquals(
                "3-HeadersStart\n" + "3-Header-[:status]-[200]\n" +
                        "3-Header-[content-type]-[text/plain;charset=UTF-8]\n" + "3-Header-[content-length]-[44]\n" +
                        "3-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n" + "3-HeadersEnd\n" + "3-Body-44\n" +
                        "3-HeadersStart\n" + "3-Header-[x-trailer-2]-[Trailer value two]\n" +
                        "3-Header-[x-trailer-1]-[Trailer value one]\n" + "3-HeadersEnd\n" + "3-EndOfStream\n",
                output.getTrace());
    }


    @Test
    public void testErrorDoesNotNotifyRecycledRequest() throws Exception {
        SocketWrapperBase<?> socket = EasyMock.createNiceMock(SocketWrapperBase.class);
        AsyncContextCallback callback = EasyMock.createNiceMock(AsyncContextCallback.class);
        EasyMock.replay(socket, callback);

        CountDownLatch resetStarted = new CountDownLatch(1);
        CountDownLatch resumeReset = new CountDownLatch(1);
        Http2Protocol protocol = new Http2Protocol();
        Http2UpgradeHandler handler = new Http2UpgradeHandler(protocol, null, null) {
            @Override
            void sendStreamReset(StreamStateMachine state, StreamException error) throws IOException {
                // Pause after closeOnStreamError() has checked the processor's async state.
                resetStarted.countDown();
                try {
                    Assert.assertTrue("Reset was not released", resumeReset.await(15, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
                state.sendReset();
            }
        };
        Stream original = new Stream(Integer.valueOf(3), handler);
        original.state.receivedStartOfHeaders();
        original.receivedEndOfStream();
        AtomicBoolean errorNotified = new AtomicBoolean();
        StreamProcessor processor = new StreamProcessor(handler, original, null, socket) {
            @Override
            protected void processSocketEvent(SocketEvent event, boolean dispatch) {
                if (event == SocketEvent.ERROR) {
                    errorNotified.set(true);
                }
            }
        };
        processor.action(ActionCode.ASYNC_START, callback);
        processor.asyncPostProcess();
        Assert.assertTrue(processor.isAsync());

        AtomicReference<Throwable> failure = new AtomicReference<>();
        StreamException error = new StreamException("late DATA", Http2Error.STREAM_CLOSED, 3);
        Thread connectionThread = new Thread(() -> {
            try {
                original.closeOnStreamError(error);
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        Stream next;
        connectionThread.start();
        try {
            Assert.assertTrue("Reset did not start", resetStarted.await(15, TimeUnit.SECONDS));

            // Complete and recycle the async request while the connection thread is sending the reset.
            original.state.sentEndOfStream();
            processor.action(ActionCode.ASYNC_COMPLETE, null);
            processor.asyncPostProcess();
            Assert.assertFalse(processor.isAsync());
            processor.recycle();
            original.replace();
            original.recycle(true);

            // Another connection can re-use the request from the protocol's shared pool.
            Http2UpgradeHandler otherConnection = new Http2UpgradeHandler(protocol, null, null);
            next = new Stream(Integer.valueOf(5), otherConnection);
            Assert.assertSame(original.getCoyoteRequest(), next.getCoyoteRequest());
            Assert.assertNull(next.getCoyoteRequest().getAttribute(RequestDispatcher.ERROR_EXCEPTION));
        } finally {
            resumeReset.countDown();
            connectionThread.join(15000);
        }
        Assert.assertFalse("Connection thread did not finish", connectionThread.isAlive());
        Assert.assertNull(failure.get());
        Assert.assertNull("The old stream error must not be set on the re-used request",
                next.getCoyoteRequest().getAttribute(RequestDispatcher.ERROR_EXCEPTION));
        Assert.assertFalse("The recycled processor must not receive an error event", errorNotified.get());
    }


    private static final int STRESS_ITERATIONS = 20000;


    /*
     * Stress test for the race between the connection thread, which raises a stream error, and the thread processing
     * the request, which starts asynchronous processing. Whichever thread wins, the processor must be notified of the
     * error once, and only once.
     */
    @Test
    public void testStreamErrorNotificationRaceStress() throws Exception {
        SocketWrapperBase<?> socket = createStressSocket();
        AsyncContextCallback callback = EasyMock.createNiceMock(AsyncContextCallback.class);
        EasyMock.replay(callback);
        Http2UpgradeHandler handler = createStressHandler();
        StreamException error = new StreamException("late DATA", Http2Error.STREAM_CLOSED, 3);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < STRESS_ITERATIONS; i++) {
                Stream stream = new Stream(Integer.valueOf(3), handler);
                Assert.assertNull("Request re-used with a stale error on iteration " + i,
                        stream.getCoyoteRequest().getAttribute(RequestDispatcher.ERROR_EXCEPTION));
                stream.state.receivedStartOfHeaders();
                stream.receivedEndOfStream();
                AtomicInteger errorEvents = new AtomicInteger();
                StreamProcessor processor = createStressProcessor(handler, stream, socket, errorEvents);
                CyclicBarrier barrier = new CyclicBarrier(2);

                // Connection thread
                Future<?> connection = executor.submit(() -> {
                    awaitBarrier(barrier);
                    jitter();
                    stream.closeOnStreamError(error);
                });
                // Thread processing the request. Mirrors StreamProcessor.process() when the request becomes async.
                Future<?> processing = executor.submit(() -> {
                    awaitBarrier(barrier);
                    jitter();
                    startStressAsync(processor, callback);
                    stream.notifyProcessorOfStreamError(processor);
                });
                connection.get(15, TimeUnit.SECONDS);
                processing.get(15, TimeUnit.SECONDS);

                Assert.assertEquals("Iteration " + i, 1, errorEvents.get());
                // The threads notifying the processor must not write to the request. The processor does that when it
                // processes the event on the thread that owns the request.
                Assert.assertNull("Iteration " + i,
                        stream.getCoyoteRequest().getAttribute(RequestDispatcher.ERROR_EXCEPTION));

                // Clean up so the request can be re-used by the next iteration
                cleanUpStressProcessor(processor, stream);
            }
        } finally {
            executor.shutdownNow();
        }
    }


    /*
     * Stress test for the race between the connection thread, which raises a stream error for an asynchronous request,
     * and the thread completing and recycling the request. The connection thread must never write to a request that
     * has been recycled (and may have been re-used by another stream).
     */
    @Test
    public void testStreamErrorRecycleRaceStress() throws Exception {
        SocketWrapperBase<?> socket = createStressSocket();
        AsyncContextCallback callback = EasyMock.createNiceMock(AsyncContextCallback.class);
        EasyMock.replay(callback);
        Http2UpgradeHandler handler = createStressHandler();
        StreamException error = new StreamException("late DATA", Http2Error.STREAM_CLOSED, 3);

        int notifiedBeforeRecycle = 0;
        int skipped = 0;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < STRESS_ITERATIONS; i++) {
                Stream stream = new Stream(Integer.valueOf(3), handler);
                // Any error set on this request after it was recycled would still be visible here
                Assert.assertNull("Request re-used with a stale error on iteration " + i,
                        stream.getCoyoteRequest().getAttribute(RequestDispatcher.ERROR_EXCEPTION));
                stream.state.receivedStartOfHeaders();
                stream.receivedEndOfStream();
                AtomicInteger errorEvents = new AtomicInteger();
                StreamProcessor processor = createStressProcessor(handler, stream, socket, errorEvents);
                startStressAsync(processor, callback);
                CyclicBarrier barrier = new CyclicBarrier(2);

                Future<?> connection = executor.submit(() -> {
                    awaitBarrier(barrier);
                    stream.closeOnStreamError(error);
                });
                Future<?> processing = executor.submit(() -> {
                    awaitBarrier(barrier);
                    // Closing the stream takes longer than the clean-up so delay the clean-up to get a good mix of
                    // outcomes
                    jitter(8_000);
                    cleanUpStressProcessor(processor, stream);
                });
                connection.get(15, TimeUnit.SECONDS);
                processing.get(15, TimeUnit.SECONDS);

                Assert.assertTrue("Iteration " + i, errorEvents.get() <= 1);
                if (errorEvents.get() == 1) {
                    notifiedBeforeRecycle++;
                } else {
                    skipped++;
                }
            }
        } finally {
            executor.shutdownNow();
        }
        // Not an assertion on the exact split (it depends on timing) but both outcomes should have been exercised
        // for the stress test to be meaningful.
        System.out.println("Recycle race stress test: notified [" + notifiedBeforeRecycle + "], skipped [" + skipped + "]");
    }


    private SocketWrapperBase<?> createStressSocket() {
        SocketWrapperBase<?> socket = EasyMock.createNiceMock(SocketWrapperBase.class);
        EasyMock.replay(socket);
        return socket;
    }


    private Http2UpgradeHandler createStressHandler() {
        return new Http2UpgradeHandler(new Http2Protocol(), null, null) {
            @Override
            void sendStreamReset(StreamStateMachine state, StreamException error) throws IOException {
                state.sendReset();
            }
        };
    }


    private StreamProcessor createStressProcessor(Http2UpgradeHandler handler, Stream stream,
            SocketWrapperBase<?> socket, AtomicInteger errorEvents) {
        return new StreamProcessor(handler, stream, null, socket) {
            @Override
            protected void processSocketEvent(SocketEvent event, boolean dispatch) {
                if (event == SocketEvent.ERROR) {
                    errorEvents.incrementAndGet();
                }
            }
        };
    }


    /*
     * Mirrors what StreamProcessor.process() does when asynchronous processing completes.
     */
    private void cleanUpStressProcessor(StreamProcessor processor, Stream stream) {
        stream.state.sentEndOfStream();
        processor.action(ActionCode.ASYNC_COMPLETE, null);
        try {
            processor.asyncPostProcess();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        processor.recycle();
        stream.replace();
        stream.recycle(true);
    }


    private void startStressAsync(StreamProcessor processor, AsyncContextCallback callback) {
        processor.action(ActionCode.ASYNC_START, callback);
        try {
            processor.asyncPostProcess();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }


    /*
     * Random delay so that, over many iterations, the two threads interleave in many different ways.
     */
    private static void jitter() {
        jitter(50_000);
    }


    /*
     * Thread.onSpinWait() is not available on the Java version used by this branch so spin on the clock.
     */
    private static void jitter(long maxNanos) {
        long end = System.nanoTime() + ThreadLocalRandom.current().nextLong(maxNanos);
        while (System.nanoTime() < end) {
            // Spin
        }
    }


    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }


    private static final class PathParam extends HttpServlet {

        private static final long serialVersionUID = 1L;

        public static final String EXPECTED_SESSION_ID = "0123456789ABCDEF";

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            response.setContentType("text/plain");
            response.setCharacterEncoding("UTF-8");

            if (EXPECTED_SESSION_ID.equals(request.getRequestedSessionId())) {
                response.getWriter().write("OK");
            } else {
                response.getWriter().write("FAIL");
            }
        }
    }
}
