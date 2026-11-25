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
import org.apache.catalina.Globals;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.AbstractProtocol;

/**
 * Tests that a request that ends up in the CLOSE_CLEAN error state (here, a form POST larger than maxPostSize with
 * swallowAbortedUploads disabled) while the application has started asynchronous processing does not have its Coyote
 * request and response recycled and returned to the pool while the application still owns them, and that the stream is
 * cleaned up correctly once asynchronous processing ends, however it ends.
 */
public class TestStreamProcessorAsyncCloseClean extends Http2TestBase {

    private static final long WAIT_LIMIT = 15000;
    private static final String ASYNC_PATH = "/async-post";
    // Default initial stream flow control window
    private static final int WINDOW_SIZE = 65535;

    private final CountDownLatch inService = new CountDownLatch(1);
    private final CountDownLatch completed = new CountDownLatch(1);
    private final AtomicReference<AsyncContext> asyncContext = new AtomicReference<>();
    private final AtomicReference<Throwable> asyncError = new AtomicReference<>();

    private long asyncTimeout = 0;
    // Body size declared by the request. Always larger than maxPostSize.
    private long contentLength = 1000;


    /*
     * The request must not be recycled, or re-used for another stream, while the application owns it.
     */
    @Test
    public void testCloseCleanWhileAsyncDoesNotRecycleRequest() throws Exception {
        AsyncContext ac = startCloseCleanAsyncRequest();
        HttpServletRequest asyncRequest = (HttpServletRequest) ac.getRequest();
        Assert.assertEquals(ASYNC_PATH, asyncRequest.getRequestURI());

        // Use another stream. If the request and response for stream 3 were returned to the pool, this stream is
        // given them.
        sendSimpleGetRequest(5);
        readUntil("5-EndOfStream");

        // The application still owns the request for stream 3. It must not have been recycled or re-used.
        Assert.assertEquals(ASYNC_PATH, asyncRequest.getRequestURI());

        // The application can still complete the request and the processor then cleans up. The request body was
        // not fully received so the stream is reset to tell the client to stop sending.
        ac.complete();
        assertCompletedAndCleanedUp();
        readUntil("3-RST-");
        assertConnectionUsable(7);
    }


    /*
     * The client keeps sending request body, up to the flow control window, after the request was put into
     * CLOSE_CLEAN. HTTP/2 has no maxSwallowSize equivalent, the amount of data is bounded by flow control. The data
     * must not disturb the request the application owns and must be released (with the window updates sent) once the
     * stream is closed.
     */
    @Test
    public void testDataWithinWindowAfterCloseCleanWhileAsync() throws Exception {
        contentLength = 100000;
        AsyncContext ac = startCloseCleanAsyncRequest();
        HttpServletRequest asyncRequest = (HttpServletRequest) ac.getRequest();

        sendData(3, WINDOW_SIZE);
        waitForPingAck();

        Assert.assertEquals(ASYNC_PATH, asyncRequest.getRequestURI());
        Assert.assertEquals(1, waitingProcessors());

        ac.complete();
        assertCompletedAndCleanedUp();
        // The swallowed data must have been acknowledged else the connection flow control window stays closed
        readUntil("0-WindowSize-[" + WINDOW_SIZE + "]");
        readUntil("3-RST-");
        assertConnectionUsable(5);
    }


    /*
     * The client sends more request body than it declared. That is a stream error raised by the connection thread
     * while the application owns the request. The processor must be cleaned up even though the application never
     * completes the request, and the connection must remain usable.
     */
    @Test
    public void testDataExceedingContentLengthAfterCloseCleanWhileAsync() throws Exception {
        startCloseCleanAsyncRequest();

        // contentLength is 1000
        sendData(3, 1001);
        readUntil("3-RST-");

        assertStreamErrorCleanedUp();
        assertConnectionUsable(5);
    }


    /*
     * The client exceeds the flow control window, while the declared content length is not exceeded. The result must
     * be that the processor is cleaned up, whether the server treats it as a stream or as a connection error.
     */
    @Test
    public void testDataBeyondWindowAfterCloseCleanWhileAsync() throws Exception {
        contentLength = 100000;
        startCloseCleanAsyncRequest();

        sendData(3, WINDOW_SIZE);
        // One byte too many
        sendData(3, 1);
        readUntil("3-RST-");

        assertStreamErrorCleanedUp();
        assertConnectionUsable(5);
    }


    /*
     * The application never completes the request. The asynchronous timeout must end it and clean up.
     */
    @Test
    public void testTimeoutAfterCloseCleanWhileAsync() throws Exception {
        asyncTimeout = 1000;
        startCloseCleanAsyncRequest();

        // The listener completes the request when the timeout fires
        assertCompletedAndCleanedUp();
        readUntil("3-RST-");
        assertConnectionUsable(5);
    }


    private AsyncContext startCloseCleanAsyncRequest() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();
        tomcat.getConnector().setMaxPostSize(100);

        Context ctxt = getProgrammaticRootContext();
        ctxt.setSwallowAbortedUploads(false);
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMapping("/simple", "simple");
        Wrapper w = Tomcat.addServlet(ctxt, "async", new HttpServlet() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                    throws ServletException, IOException {
                // Exceeds maxPostSize. With swallowAbortedUploads disabled this puts the processor in CLOSE_CLEAN.
                try {
                    req.getParameter("a");
                } catch (IllegalStateException ise) {
                    // Expected. The application chooses to continue anyway.
                }
                AsyncContext ac = req.startAsync();
                ac.setTimeout(asyncTimeout);
                ac.addListener(new AsyncListener() {
                    @Override
                    public void onComplete(AsyncEvent event) {
                        completed.countDown();
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
                inService.countDown();
            }
        });
        w.setAsyncSupported(true);
        ctxt.addServletMapping(ASYNC_PATH, "async");
        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        // Declare a body larger than maxPostSize (contentLength). The body itself is sent, if at all, by the test.
        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildPostRequest(headersFrameHeader, headersPayload, false, Globals.CONTENT_TYPE_FORM_URL_ENCODING, contentLength,
                ASYNC_PATH, new byte[9], ByteBuffer.allocate(0), null, false, 3);
        writeFrame(headersFrameHeader, headersPayload);

        if (!inService.await(WAIT_LIMIT, TimeUnit.MILLISECONDS)) {
            Assert.fail("Request did not reach the servlet");
        }
        return asyncContext.get();
    }


    /*
     * Send the given number of bytes of request body for the stream, split into frames no larger than the default
     * maximum frame size.
     */
    private void sendData(int streamId, int length) throws IOException {
        int remaining = length;
        while (remaining > 0) {
            int size = Math.min(remaining, 16384);
            byte[] data = new byte[size];
            java.util.Arrays.fill(data, (byte) 'x');
            sendData(streamId, data);
            remaining -= size;
        }
    }


    private void waitForPingAck() throws Exception {
        sendPing(0, false, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 });
        readUntil("0-Ping-Ack-[1,2,3,4,5,6,7,8]");
    }


    private void readUntil(String expected) throws Exception {
        long end = System.currentTimeMillis() + WAIT_LIMIT;
        while (!output.getTrace().contains(expected) && System.currentTimeMillis() < end) {
            parser.readFrame();
        }
        Assert.assertTrue(output.getTrace(), output.getTrace().contains(expected));
    }


    private void assertCompletedAndCleanedUp() throws Exception {
        Assert.assertTrue("onComplete() was not called", completed.await(WAIT_LIMIT, TimeUnit.MILLISECONDS));
        long end = System.currentTimeMillis() + WAIT_LIMIT;
        while (waitingProcessors() > 0 && System.currentTimeMillis() < end) {
            Thread.sleep(100);
        }
        Assert.assertEquals(0, waitingProcessors());
        Assert.assertNull(asyncError.get());
    }


    private void assertStreamErrorCleanedUp() throws Exception {
        long end = System.currentTimeMillis() + WAIT_LIMIT;
        while (waitingProcessors() > 0 && System.currentTimeMillis() < end) {
            Thread.sleep(100);
        }
        Assert.assertEquals(0, waitingProcessors());
    }


    private void assertConnectionUsable(int streamId) throws Exception {
        output.clearTrace();
        sendSimpleGetRequest(streamId);
        readUntil(streamId + "-EndOfStream");
    }


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
}
