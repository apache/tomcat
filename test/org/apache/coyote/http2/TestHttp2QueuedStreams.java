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
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.Adapter;
import org.apache.coyote.Request;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.tomcat.util.net.SocketWrapperBase;

/*
 * The number of streams that can be executing at the same time is limited for each connection. If a stream has to wait
 * for an execution slot and the client then resets it, the reset must not leave the stream (and the memory it uses)
 * waiting in the queue.
 */
public class TestHttp2QueuedStreams extends Http2TestBase {

    private static final int EXECUTION_LIMIT = 20;

    private static final AtomicInteger blocked = new AtomicInteger();
    private static final AtomicInteger counted = new AtomicInteger();
    private static final AtomicInteger asyncStarted = new AtomicInteger();
    private static final AtomicInteger asyncErrors = new AtomicInteger();

    private volatile Http2UpgradeHandler handler;


    @Test
    public void testResetStreamsAreRemovedFromQueue() throws Exception {
        connectAndFillExecutionSlots();

        int streamId = 3 + 2 * EXECUTION_LIMIT;
        int streams = 50;
        for (int i = 0; i < streams; i++) {
            sendPostRequestWithBody(streamId);
            sendRst(streamId, Http2Error.CANCEL.getCode());
            streamId += 2;
        }

        // The slots are still blocked so these streams could not have been processed
        waitForQueuedStreams(0);
        // Discarded streams must not continue to count towards the limit on concurrent streams
        waitForRetainedStreams(0);

        // Let the blocked requests complete
        sendSettings(0, false, new SettingValue(4, 65535));
        long end = System.nanoTime() + 10_000_000_000L;
        while (blocked.get() > 0 && System.nanoTime() < end) {
            Thread.sleep(50);
        }
        Assert.assertEquals(0, blocked.get());
        // The streams were reset before processing started so the application should not have been called
        Thread.sleep(500);
        Assert.assertEquals(0, counted.get());
    }


    /*
     * Streams that are waiting for an execution slot and that are not reset must still be processed once a slot is
     * available.
     */
    @Test
    public void testQueuedStreamsAreProcessed() throws Exception {
        connectAndFillExecutionSlots();

        int streamId = 3 + 2 * EXECUTION_LIMIT;
        int streams = 10;
        for (int i = 0; i < streams; i++) {
            sendPostRequestWithBody(streamId);
            streamId += 2;
        }
        waitForQueuedStreams(streams);

        sendSettings(0, false, new SettingValue(4, 65535));
        long end = System.nanoTime() + 10_000_000_000L;
        while (counted.get() < streams && System.nanoTime() < end) {
            Thread.sleep(50);
        }
        Assert.assertEquals(streams, counted.get());
        waitForQueuedStreams(0);
    }


    /*
     * A stream that has started processing and is then reset may have work (the error notification) waiting in the
     * queue. The reset frees the stream's slot in the limit on the number of active streams but the stream must still
     * count towards the limit on concurrent streams until that work has been processed.
     */
    @Test
    public void testResetStreamWithQueuedErrorIsRetained() throws Exception {
        connect();

        // Start an asynchronous request. Processing of the stream starts but the request does not complete.
        sendGetRequest(3, "/async");
        long end = System.nanoTime() + 10_000_000_000L;
        while (asyncStarted.get() < 1 && System.nanoTime() < end) {
            Thread.sleep(50);
        }
        Assert.assertEquals(1, asyncStarted.get());

        fillExecutionSlots(5);

        // Reset the asynchronous stream. The error notification has to wait for an execution slot.
        sendRst(3, Http2Error.CANCEL.getCode());
        waitForQueuedStreams(1);
        waitForRetainedStreams(1);

        // Once the slots are available the notification is processed and the stream is no longer retained
        sendSettings(0, false, new SettingValue(4, 65535));
        end = System.nanoTime() + 10_000_000_000L;
        while (blocked.get() > 0 && System.nanoTime() < end) {
            Thread.sleep(50);
        }
        Assert.assertEquals(0, blocked.get());
        waitForQueuedStreams(0);
        waitForRetainedStreams(0);
    }


    /*
     * Any close of a stream that is waiting for an execution slot must remove it from the queue, not just the paths
     * that are currently used for resets and stream errors.
     */
    @Test
    public void testCloseRemovesQueuedStream() throws Exception {
        connectAndFillExecutionSlots();

        int firstStreamId = 3 + 2 * EXECUTION_LIMIT;
        int streams = 5;
        for (int i = 0; i < streams; i++) {
            sendPostRequestWithBody(firstStreamId + 2 * i);
        }
        waitForQueuedStreams(streams);

        int streamId = firstStreamId + 2;
        handler.getStream(streamId).close(new StreamException("Test", Http2Error.CANCEL, streamId));

        waitForQueuedStreams(streams - 1);
        waitForRetainedStreams(0);

        // The remaining streams are still processed once slots are available
        sendSettings(0, false, new SettingValue(4, 65535));
        long end = System.nanoTime() + 10_000_000_000L;
        while (counted.get() < streams - 1 && System.nanoTime() < end) {
            Thread.sleep(50);
        }
        Assert.assertEquals(streams - 1, counted.get());
        waitForQueuedStreams(0);
    }


    /*
     * Closing a stream that has started processing must not discard the work it still has queued. That work is what
     * cleans up the processor.
     */
    @Test
    public void testCloseKeepsQueuedWorkOfStartedStream() throws Exception {
        connect();

        sendGetRequest(3, "/async");
        long end = System.nanoTime() + 10_000_000_000L;
        while (asyncStarted.get() < 1 && System.nanoTime() < end) {
            Thread.sleep(50);
        }
        Assert.assertEquals(1, asyncStarted.get());

        fillExecutionSlots(5);

        sendRst(3, Http2Error.CANCEL.getCode());
        waitForQueuedStreams(1);
        waitForRetainedStreams(1);

        handler.getStream(3).close(null);

        // Nothing is discarded
        waitForQueuedStreams(1);
        waitForRetainedStreams(1);

        sendSettings(0, false, new SettingValue(4, 65535));
        end = System.nanoTime() + 10_000_000_000L;
        while ((blocked.get() > 0 || asyncErrors.get() < 1) && System.nanoTime() < end) {
            Thread.sleep(50);
        }
        Assert.assertEquals("onError() calls", 1, asyncErrors.get());
        waitForQueuedStreams(0);
        waitForRetainedStreams(0);
    }


    /*
     * Closing the connection must not discard the error notifications that started asynchronous requests are waiting
     * for since processing them is what cleans up the processor.
     */
    @Test
    public void testCloseProcessesQueuedErrorNotifications() throws Exception {
        connect();

        sendGetRequest(3, "/async");
        long end = System.nanoTime() + 10_000_000_000L;
        while (asyncStarted.get() < 1 && System.nanoTime() < end) {
            Thread.sleep(50);
        }
        Assert.assertEquals(1, asyncStarted.get());

        fillExecutionSlots(5);

        // The close resets every stream which queues the error notification for the asynchronous request
        handler.close();

        // Closing releases the blocked requests which frees the execution slots
        end = System.nanoTime() + 10_000_000_000L;
        while ((blocked.get() > 0 || asyncErrors.get() < 1) && System.nanoTime() < end) {
            Thread.sleep(50);
        }
        Assert.assertEquals(0, blocked.get());
        Assert.assertEquals("onError() calls", 1, asyncErrors.get());
        waitForQueuedStreams(0);
        waitForRetainedStreams(0);
    }


    /*
     * Nothing in the queue will be processed once the connection has closed so it must be released.
     */
    @Test
    public void testQueueIsClearedOnClose() throws Exception {
        connectAndFillExecutionSlots();

        int streamId = 3 + 2 * EXECUTION_LIMIT;
        int streams = 10;
        for (int i = 0; i < streams; i++) {
            sendPostRequestWithBody(streamId);
            streamId += 2;
        }
        waitForQueuedStreams(streams);

        handler.close();

        waitForQueuedStreams(0);
    }


    private void connectAndFillExecutionSlots() throws Exception {
        connect();
        fillExecutionSlots(3);
    }


    private void connect() throws Exception {
        blocked.set(0);
        counted.set(0);
        asyncStarted.set(0);
        asyncErrors.set(0);

        enableHttp2WithHandlerCapture();
        configureAndStartWebApplication();
        openClientConnection(false);
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse(200);
    }


    private void fillExecutionSlots(int firstStreamId) throws Exception {
        // A client window size of zero blocks the responses
        sendSettings(0, false, new SettingValue(4, 0));
        int streamId = firstStreamId;
        for (int i = 0; i < EXECUTION_LIMIT; i++) {
            sendGetRequest(streamId, "/block");
            streamId += 2;
        }
        long end = System.nanoTime() + 10_000_000_000L;
        while (blocked.get() < EXECUTION_LIMIT && System.nanoTime() < end) {
            Thread.sleep(50);
        }
        Assert.assertEquals(EXECUTION_LIMIT, blocked.get());
    }


    private void sendGetRequest(int streamId, String path) throws IOException {
        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildGetRequest(frameHeader, headersPayload, null, streamId, path);
        writeFrame(frameHeader, headersPayload);
    }


    /*
     * Send the headers and body (as two DATA frames that are each large enough not to be treated as overhead) of a
     * request to a servlet that counts the number of requests it processes.
     */
    private void sendPostRequestWithBody(int streamId) throws IOException {
        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        byte[] dataFrameHeader = new byte[9];
        ByteBuffer dataPayload = ByteBuffer.allocate(128);
        buildPostRequest(headersFrameHeader, headersPayload, false, null, -1, "/count", dataFrameHeader, dataPayload,
                null, false, streamId);
        writeFrame(headersFrameHeader, headersPayload);
        sendData(streamId, new byte[1024]);
        sendData(streamId, new byte[1024]);
    }


    private void waitForRetainedStreams(int expected) throws Exception {
        Field retainedField = Http2UpgradeHandler.class.getDeclaredField("retainedQueuedStreams");
        retainedField.setAccessible(true);

        int retained = -1;
        long end = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < end) {
            retained = ((Set<?>) retainedField.get(handler)).size();
            if (retained == expected) {
                return;
            }
            Thread.sleep(50);
        }
        Assert.assertEquals("retained streams", expected, retained);
    }


    private void waitForQueuedStreams(int expected) throws Exception {
        Field queueField = Http2UpgradeHandler.class.getDeclaredField("queuedRunnable");
        queueField.setAccessible(true);
        Field indexField = Http2UpgradeHandler.class.getDeclaredField("queuedRunnableByStream");
        indexField.setAccessible(true);

        int queued = -1;
        int count = -1;
        long end = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < end) {
            queued = ((Queue<?>) queueField.get(handler)).size();
            count = ((Map<?,?>) indexField.get(handler)).size();
            if (queued == expected && count == expected) {
                return;
            }
            Thread.sleep(50);
        }
        Assert.assertEquals("queued runnables", expected, queued);
        Assert.assertEquals("queued streams", expected, count);
    }


    private void enableHttp2WithHandlerCapture() {
        Tomcat tomcat = getTomcatInstance();
        Connector connector = tomcat.getConnector();
        Assert.assertTrue(connector.setProperty("useAsyncIO", Boolean.toString(useAsyncIO)));
        http2Protocol = new Http2Protocol() {
            @Override
            public String getHttpUpgradeName(boolean isSSLEnabled) {
                return "h2c";
            }

            @Override
            public InternalHttpUpgradeHandler getInternalUpgradeHandler(SocketWrapperBase<?> socketWrapper,
                    Adapter adapter, Request coyoteRequest) {
                InternalHttpUpgradeHandler result =
                        super.getInternalUpgradeHandler(socketWrapper, adapter, coyoteRequest);
                handler = (Http2UpgradeHandler) result;
                return result;
            }
        };
        // Long timeouts so the blocked streams are not timed out
        http2Protocol.setReadTimeout(120000);
        http2Protocol.setWriteTimeout(120000);
        http2Protocol.setKeepAliveTimeout(300000);
        http2Protocol.setStreamReadTimeout(120000);
        http2Protocol.setStreamWriteTimeout(120000);
        http2Protocol.setMaxConcurrentStreams(200);
        http2Protocol.setMaxConcurrentStreamExecution(EXECUTION_LIMIT);
        http2Protocol.setHttp11Protocol((AbstractHttp11Protocol<?>) connector.getProtocolHandler());
        connector.addUpgradeProtocol(http2Protocol);
    }


    @Override
    protected void configureAndStartWebApplication() throws org.apache.catalina.LifecycleException {
        Tomcat tomcat = getTomcatInstance();

        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMapping("/simple", "simple");
        Tomcat.addServlet(ctxt, "block", new BlockServlet());
        ctxt.addServletMapping("/block", "block");
        Tomcat.addServlet(ctxt, "async", new AsyncServlet()).setAsyncSupported(true);
        ctxt.addServletMapping("/async", "async");
        Tomcat.addServlet(ctxt, "count", new CountServlet());
        ctxt.addServletMapping("/count", "count");

        tomcat.start();
    }


    private static class BlockServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            blocked.incrementAndGet();
            try {
                resp.setContentType("application/octet-stream");
                resp.getOutputStream().write(new byte[100]);
                // Blocks until the client provides a flow control window
                resp.flushBuffer();
            } finally {
                blocked.decrementAndGet();
            }
        }
    }


    private static class AsyncServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
            // Never completed by the application. Only the container (after the client reset) ends it.
            AsyncContext asyncContext = req.startAsync();
            asyncContext.setTimeout(120000);
            asyncContext.addListener(new AsyncListener() {
                @Override
                public void onComplete(AsyncEvent event) {
                }

                @Override
                public void onTimeout(AsyncEvent event) {
                }

                @Override
                public void onError(AsyncEvent event) {
                    asyncErrors.incrementAndGet();
                }

                @Override
                public void onStartAsync(AsyncEvent event) {
                }
            });
            asyncStarted.incrementAndGet();
        }
    }


    private static class CountServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            counted.incrementAndGet();
            resp.setContentType("text/plain");
            resp.getWriter().print("OK");
        }
    }
}
