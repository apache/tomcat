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

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import org.apache.coyote.http11.AbstractHttp11Protocol;

/**
 * Unit tests for Section 6.8 of <a href="https://tools.ietf.org/html/rfc7540">RFC 7540</a>. <br>
 * The order of tests in this class is aligned with the order of the requirements in the RFC.
 */
public class TestHttp2Section_6_8 extends Http2TestBase {

    private static final boolean RELAX_TIMING = Boolean.getBoolean("tomcat.test.relaxTiming");

    private static final long PING_ACK_DELAY_MS = 2000;
    // On slow systems (Gump) may need to be higher
    private static final long TIMING_MARGIN_MS = RELAX_TIMING ? 1000 : 200;

    @Test
    public void testGoawayIgnoreNewStreams() throws Exception {
        setPingAckDelayMillis(PING_ACK_DELAY_MS);

        http2Connect();

        http2Protocol.setMaxConcurrentStreams(200);

        Thread.sleep(PING_ACK_DELAY_MS + TIMING_MARGIN_MS);

        getTomcatInstance().getConnector().pause();

        // Go away
        parser.readFrame();
        Assert.assertEquals("0-Goaway-[2147483647]-[0]-[null]", output.getTrace());
        output.clearTrace();

        // Should be processed
        sendSimpleGetRequest(3);

        Thread.sleep(PING_ACK_DELAY_MS + TIMING_MARGIN_MS);

        // Should be ignored
        sendSimpleGetRequest(5);

        parser.readFrame();
        parser.readFrame();

        Assert.assertEquals(getSimpleResponseTrace(3), output.getTrace());
        output.clearTrace();

        // Finally the go away frame
        parser.readFrame();
        Assert.assertEquals("0-Goaway-[3]-[0]-[null]", output.getTrace());
    }


    @Test
    public void testGoawayIgnoreNewStreamsAllowTrailers() throws Exception {
        setPingAckDelayMillis(PING_ACK_DELAY_MS);

        http2Connect();

        http2Protocol.setMaxConcurrentStreams(200);
        // Ensure we see all the Window updates
        http2Protocol.setOverheadWindowUpdateThreshold(0);
        // Enable trailer headers
        ((AbstractHttp11Protocol<?>) http2Protocol.getHttp11Protocol()).setAllowedTrailerHeaders(TRAILER_HEADER_NAME);

        Thread.sleep(PING_ACK_DELAY_MS + TIMING_MARGIN_MS);

        getTomcatInstance().getConnector().pause();

        // Go away
        parser.readFrame();
        Assert.assertEquals("0-Goaway-[2147483647]-[0]-[null]", output.getTrace());
        output.clearTrace();

        // Should be processed
        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        byte[] dataFrameHeader = new byte[9];
        ByteBuffer dataPayload = ByteBuffer.allocate(256);
        byte[] trailerFrameHeader = new byte[9];
        ByteBuffer trailerPayload = ByteBuffer.allocate(256);

        buildPostRequest(headersFrameHeader, headersPayload, false, dataFrameHeader, dataPayload, null, true, 3);

        // Write the headers
        writeFrame(headersFrameHeader, headersPayload);
        // Body
        writeFrame(dataFrameHeader, dataPayload);

        Thread.sleep(PING_ACK_DELAY_MS + TIMING_MARGIN_MS);

        // Should be ignored
        sendSimpleGetRequest(5);

        // Trailers
        buildTrailerHeaders(trailerFrameHeader, trailerPayload, 3);
        writeFrame(trailerFrameHeader, trailerPayload);

        // Window updates after reading Stream 3 body
        parser.readFrame();
        parser.readFrame();
        Assert.assertEquals("0-WindowSize-[256]\n3-WindowSize-[256]\n", output.getTrace());
        output.clearTrace();

        // Go away frame after trying to send a GET on stream 5
        parser.readFrame();
        Assert.assertEquals("0-Goaway-[3]-[0]-[null]", output.getTrace());
        output.clearTrace();

        // Stream 3 headers and body once trailer has been read (it is used to create body)
        parser.readFrame();
        parser.readFrame();

        String len = Integer.toString(256 + TRAILER_HEADER_VALUE.length());
        String expected = "3-HeadersStart\n" + "3-Header-[:status]-[200]\n" +
                "3-Header-[content-length]-[" + len + "]\n" + "3-Header-[date]-[" + DEFAULT_DATE + "]\n" +
                "3-HeadersEnd\n3-Body-" + len + "\n" + "3-EndOfStream\n";


        Assert.assertEquals(expected, output.getTrace());
        output.clearTrace();
    }


    @Test
    public void testGoawayFrameNonZeroStream() throws Exception {
        // HTTP2 upgrade
        http2Connect();

        sendGoaway(1, 1, Http2Error.NO_ERROR.getCode());

        handleGoAwayResponse(1);
    }


    // TODO Test header processing and window size processing for ignored
    // streams
}
