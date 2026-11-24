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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.http.Method;

public class TestRfc9218 extends Http2TestBase {

    @Test
    public void testBackLogRoundingRemainderAllocated() throws Exception {
        blockStreams();

        // Make all three streams incremental with the same urgency so the increment is shared proportionally
        sendPriorityUpdate(17, 3, true);
        sendPriorityUpdate(19, 3, true);
        sendPriorityUpdate(21, 3, true);

        /*
         * Add 10 bytes to the connection window. The proportional shares are 3.04, 3.47 and 3.47 bytes. Rounding each
         * down allocates 9 bytes. The remaining byte is part of the window the client made available so it must be
         * allocated rather than lost. It goes to 17 since that is first in priority order.
         */
        sendWindowUpdate(0, 10);
        parser.readFrame();
        parser.readFrame();
        parser.readFrame();

        String trace = output.getTrace();
        System.out.println(trace);
        Assert.assertTrue(trace, trace.contains("17-Body-4\n"));
        trace = trace.replace("17-Body-4\n", "");
        Assert.assertTrue(trace, trace.contains("19-Body-3\n"));
        trace = trace.replace("19-Body-3\n", "");
        Assert.assertTrue(trace, trace.contains("21-Body-3\n"));
        trace = trace.replace("21-Body-3\n", "");
        Assert.assertEquals(0, trace.length());
        output.clearTrace();
    }


    @Test
    public void testPriority() throws Exception {
        blockStreams();

        String trace;

        // 17 - 7k body left
        // 19 - 8k body left
        // 21 - 8k body left

        // Add 1k to the connection window. Should be used for stream 17.
        sendWindowUpdate(0, 1024);
        parser.readFrame();
        trace = output.getTrace();
        Assert.assertEquals("17-Body-1024\n", trace);
        System.out.println(trace);
        output.clearTrace();

        // 17 - 6k body left
        // 19 - 8k body left
        // 21 - 8k body left

        // Re-order the priorities
        sendPriorityUpdate(19, 2, false);
        sendPriorityUpdate(21, 1, false);

        // Add 1k to the connection window. Should be used for stream 21.
        sendWindowUpdate(0, 1024);
        parser.readFrame();

        trace = output.getTrace();
        Assert.assertEquals("21-Body-1024\n", trace);
        System.out.println(trace);
        output.clearTrace();

        // 17 - 6k body left
        // 19 - 8k body left
        // 21 - 7k body left

        // Re-order the priorities
        sendPriorityUpdate(17, 3, true);
        sendPriorityUpdate(19, 3, true);
        sendPriorityUpdate(21, 3, true);

        // Add 3k to the connection window. Should be split between 17, 19 and 21.
        sendWindowUpdate(0, 1024 * 3);
        parser.readFrame();
        parser.readFrame();
        parser.readFrame();

        trace = output.getTrace();
        System.out.println(trace);
        Assert.assertTrue(trace.contains("17-Body-878\n"));
        trace = trace.replace("17-Body-878\n", "");
        Assert.assertTrue(trace.contains("19-Body-1170\n"));
        trace = trace.replace("19-Body-1170\n", "");
        Assert.assertTrue(trace.contains("21-Body-1024\n"));
        trace = trace.replace("21-Body-1024\n", "");
        Assert.assertEquals(0, trace.length());
        output.clearTrace();

        // 17 - 5266 body left
        // 19 - 7022 body left
        // 21 - 6144 body left

        // Add 1 byte to the connection window. Rounding up means stream 17 takes the whole allocation. The other
        // streams remain in the back log since allocating to them as well would exceed the window the client made
        // available.
        sendWindowUpdate(0, 1);
        parser.readFrame();

        trace = output.getTrace();
        System.out.println(trace);
        Assert.assertEquals("17-Body-1\n", trace);
        output.clearTrace();

        // 17 - 5265 body left
        // 19 - 7022 body left
        // 21 - 6144 body left

        // BZ 69614 - invalid priority update frames should be ignored
        sendInvalidPriorityUpdate(17);

        // Re-order the priorities
        sendPriorityUpdate(17, 2, true);

        /*
         * Add 8k to the connection window. Should fully allocate 17 with the remainder split proportionally between 19
         * and 21.
         */
        sendWindowUpdate(0, 1024 * 8);
        // Use try/catch as third read has been failing on some tests runs
        try {
            parser.readFrame();
            parser.readFrame();
            parser.readFrame();
        } catch (IOException ioe) {
            // Dump for debugging purposes
            ioe.printStackTrace();
            // Continue - we'll get trace dumped to stdout below
        }

        trace = output.getTrace();
        System.out.println(trace);
        Assert.assertTrue(trace.contains("17-Body-5265\n"));
        trace = trace.replace("17-Body-5265\n", "");
        Assert.assertTrue(trace.contains("17-EndOfStream\n"));
        trace = trace.replace("17-EndOfStream\n", "");
        Assert.assertTrue(trace.contains("19-Body-1562\n"));
        trace = trace.replace("19-Body-1562\n", "");
        Assert.assertTrue(trace.contains("21-Body-1365\n"));
        trace = trace.replace("21-Body-1365\n", "");
        Assert.assertEquals(0, trace.length());

        // 19 - 5460 body left
        // 21 - 4779 body left

        // Add 16k to the connection window. Should fully allocate 19 and 21.
        sendWindowUpdate(0, 1024 * 16);

        try {
            parser.readFrame();
            parser.readFrame();
        } catch (IOException ioe) {
            // Dump for debugging purposes
            ioe.printStackTrace();
        }
    }


    /*
     * Opens the connection and leaves streams 17, 19 and 21 blocked because the connection window is zero.
     *
     * 17 - 7k body left
     * 19 - 8k body left
     * 21 - 8k body left
     */
    @Test
    public void testPriorityUpdateIdleStream() throws Exception {
        http2Connect();

        // RFC 9218, section 7 allows a PRIORITY_UPDATE frame to be sent before the stream it references is opened
        sendPriorityUpdate(3, 0, false);
        sendSimpleGetRequest(3);
        readSimpleGetResponse();

        Assert.assertEquals(getSimpleResponseTrace(3), output.getTrace());
    }


    @Test
    public void testPriorityUpdateIdleStreamApplied() throws Exception {
        blockStreams(true);

        /*
         * Stream 21 was given the highest urgency while idle. That overrides the lowest urgency in its priority header
         * field so it is allocated the window ahead of stream 17.
         */
        sendWindowUpdate(0, 1024);
        parser.readFrame();

        String trace = output.getTrace();
        Assert.assertEquals("21-Body-1024\n", trace);
        output.clearTrace();
    }


    @Test
    public void testPriorityUpdateIdleStreamLimit() throws Exception {
        connectWithMaxConcurrentStreams(2);

        sendPriorityUpdate(3, 0, false);
        sendPriorityUpdate(5, 0, false);
        // Updating a stream that has already been prioritized does not count towards the limit
        sendPriorityUpdate(3, 1, false);

        // The connection is still open
        sendPing();
        parser.readFrame();
        Assert.assertEquals("0-Ping-Ack-[0,0,0,0,0,0,0,0]\n", output.getTrace());
        output.clearTrace();

        // Two prioritized idle streams plus zero active streams is the limit. A third must be rejected.
        sendPriorityUpdate(7, 0, false);

        handleGoAwayResponse(1);
    }


    @Test
    public void testPriorityUpdateIdlePushStream() throws Exception {
        http2Connect();

        // An idle request stream is accepted
        sendPriorityUpdate(3, 0, false);
        sendPing();
        parser.readFrame();
        Assert.assertEquals("0-Ping-Ack-[0,0,0,0,0,0,0,0]\n", output.getTrace());
        output.clearTrace();

        // Server push is not supported so a push stream is always idle
        sendPriorityUpdate(2, 0, false);

        handleGoAwayResponse(1);
    }


    @Test
    public void testPriorityUpdateClosedStream() throws Exception {
        /*
         * Closed streams are pruned once there are more than five times the maximum concurrent streams. A limit of two
         * rather than one allows for the previous stream still being counted as active when the next one starts.
         */
        connectWithMaxConcurrentStreams(2);
        // Each response uses 8k of the connection window
        sendWindowUpdate(0, 1024 * 1024);

        // Use enough streams that the earliest closed streams are pruned
        for (int i = 3; i < 43; i += 2) {
            sendSimpleGetRequest(i);
            readSimpleGetResponse();
            output.clearTrace();
        }

        // A PRIORITY_UPDATE frame for a closed stream is ignored
        sendPriorityUpdate(3, 0, false);
        sendSimpleGetRequest(43);
        readSimpleGetResponse();

        Assert.assertEquals(getSimpleResponseTrace(43), output.getTrace());
    }


    @Test
    public void testPriorityUpdateIdleStreamMalformedRequest() throws Exception {
        connectWithMaxConcurrentStreams(2);

        sendPriorityUpdate(3, 0, false);

        // A connection-specific header field makes the request malformed so the stream is reset
        List<Header> headers = new ArrayList<>(5);
        headers.add(new Header(":method", Method.GET));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":path", "/simple"));
        headers.add(new Header(":authority", "localhost:" + getPort()));
        headers.add(new Header("connection", "keep-alive"));
        sendGetRequest(3, headers);
        parser.readFrame();
        Assert.assertEquals("3-RST-[1]\n", output.getTrace());
        output.clearTrace();

        // Stream 3 is closed so it no longer counts towards the limit
        sendPriorityUpdate(5, 0, false);
        sendPriorityUpdate(7, 0, false);
        sendSimpleGetRequest(5);
        readSimpleGetResponse();

        Assert.assertEquals(getSimpleResponseTrace(5), output.getTrace());
    }


    private void sendGetRequest(int streamId, List<Header> headers) throws IOException {
        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildGetRequest(frameHeader, headersPayload, null, headers, streamId);
        writeFrame(frameHeader, headersPayload);
    }


    private void connectWithMaxConcurrentStreams(long maxConcurrentStreams) throws Exception {
        enableHttp2(maxConcurrentStreams);
        configureAndStartWebApplication();
        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse(maxConcurrentStreams);
    }


    private void blockStreams() throws Exception {
        blockStreams(false);
    }


    private void blockStreams(boolean prioritizeIdleStream) throws Exception {
        http2Connect();

        /*
         * This test uses small window updates and data frames that will trigger the excessive overhead protection so
         * disable it.
         */
        http2Protocol.setOverheadWindowUpdateThreshold(0);
        http2Protocol.setOverheadDataThreshold(0);

        // Default connection window size is 64k - 1. Initial request will have used 8k (56k -1). Increase it to 57k.
        sendWindowUpdate(0, 1 + 1024);

        // Consume 56k of the connection window
        for (int i = 3; i < 17; i += 2) {
            sendSimpleGetRequest(i);
            readSimpleGetResponse();
        }

        String trace = output.getTrace();
        System.out.println(trace);
        output.clearTrace();

        // At this point the connection window should be 1k

        // Process a request on stream 17. This should consume the connection window.
        sendSimpleGetRequest(17);
        // 17-headers, 17-1k-body
        parser.readFrame();
        parser.readFrame();
        trace = output.getTrace();
        System.out.println(trace);
        output.clearTrace();

        // Send additional requests. Connection window is empty so only headers will be returned.
        sendSimpleGetRequest(19);
        if (prioritizeIdleStream) {
            // Stream 21 is idle at this point
            sendPriorityUpdate(21, 0, false);
            List<Header> headers = new ArrayList<>(5);
            headers.add(new Header(":method", Method.GET));
            headers.add(new Header(":scheme", "http"));
            headers.add(new Header(":path", "/simple"));
            headers.add(new Header(":authority", "localhost:" + getPort()));
            headers.add(new Header("priority", "u=7"));
            sendGetRequest(21, headers);
        } else {
            sendSimpleGetRequest(21);
        }

        // 19-headers, 21-headers
        parser.readFrame();
        parser.readFrame();
        trace = output.getTrace();
        System.out.println(trace);
        output.clearTrace();

        // At this point 17, 19 and 21 are all blocked because the connection window is zero.
    }

    private void sendInvalidPriorityUpdate(int streamId) throws IOException {
        byte[] payload = "u=1:i".getBytes(StandardCharsets.US_ASCII);

        byte[] priorityUpdateFrame = new byte[13 + payload.length];

        // length
        ByteUtil.setThreeBytes(priorityUpdateFrame, 0, 4 + payload.length);
        // type
        priorityUpdateFrame[3] = FrameType.PRIORITY_UPDATE.getIdByte();
        // Stream ID
        ByteUtil.set31Bits(priorityUpdateFrame, 5, 0);

        // Payload
        ByteUtil.set31Bits(priorityUpdateFrame, 9, streamId);
        System.arraycopy(payload, 0, priorityUpdateFrame, 13, payload.length);

        os.write(priorityUpdateFrame);
        os.flush();
    }
}
