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

/**
 * Unit tests for Section 5.§ of
 * <a href="https://tools.ietf.org/html/rfc7540">RFC 7540</a>.
 * <br>
 * The order of tests in this class is aligned with the order of the
 * requirements in the RFC.
 */
public class TestHttp2Section_5_1 extends Http2TestBase {

    @Test
    public void testIdleStateInvalidFrame01() throws Exception {
        http2Connect();

        sendWindowUpdate(3, 200);

        handleGoAwayResponse(1);
    }


    @Test
    public void testIdleStateInvalidFrame02() throws Exception {
        http2Connect();

        sendData(3, new byte[] {});

        handleGoAwayResponse(1);
    }


    // TODO: reserved local
    // TODO: reserved remote


    @Test
    public void halfClosedRemoteInvalidFrame() throws Exception {
        http2Connect();

        // This half-closes the stream since it includes the end of stream flag
        sendSimpleGetRequest(3);
        readSimpleGetResponse();
        Assert.assertEquals(getSimpleResponseTrace(3), output.getTrace());
        output.clearTrace();

        // This should trigger a connection error
        sendData(3, new byte[] {});

        handleGoAwayResponse(3,  Http2Error.STREAM_CLOSED);
    }


    @Test
    public void testClosedInvalidFrame01() throws Exception {
        // HTTP2 upgrade
        http2Connect();

        // Build the simple request
        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildSimpleGetRequest(frameHeader, headersPayload, null, 3);

        // Remove the end of stream and end of headers flags
        frameHeader[4] = 0;

        // Process the request
        writeFrame(frameHeader, headersPayload);

        // Send a rst
        sendRst(3, Http2Error.INTERNAL_ERROR.getCode());

        // Then try sending some data (which should fail)
        sendData(3, new byte[] {});
        parser.readFrame(true);

        Assert.assertTrue(output.getTrace(),
                output.getTrace().startsWith("3-RST-[" + Http2Error.STREAM_CLOSED.getCode() + "]"));
    }


    @Test
    public void testClosedInvalidFrame02() throws Exception {
        http2Connect();

        // Stream 1 is closed. This should trigger a connection error
        sendData(1, new byte[] {});

        handleGoAwayResponse(1,  Http2Error.STREAM_CLOSED);
    }


    // TODO: Invalid frames for each of the remaining states

    // Section 5.1.1

    @Test
    public void testClientSendEvenStream() throws Exception {
        // HTTP2 upgrade
        http2Connect();

        // Part 1
        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildSimpleGetRequestPart1(frameHeader, headersPayload, 4);
        writeFrame(frameHeader, headersPayload);

        handleGoAwayResponse(1);
    }


    @Test
    public void testClientSendOldStream() throws Exception {
        http2Connect();
        sendSimpleGetRequest(5);
        readSimpleGetResponse();
        Assert.assertEquals(getSimpleResponseTrace(5), output.getTrace());
        output.clearTrace();


        // Build the simple request on an old stream
        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildSimpleGetRequest(frameHeader, headersPayload, null, 3);

        os.write(frameHeader);
        os.flush();

        handleGoAwayResponse(5);
    }


    @Test
    public void testImplicitClose() throws Exception {
        http2Connect();

        sendPriority(3, 0, 16);
        sendPriority(5, 0, 16);

        sendSimpleGetRequest(5);
        readSimpleGetResponse();
        Assert.assertEquals(getSimpleResponseTrace(5), output.getTrace());
        output.clearTrace();

        // Should trigger an error since stream 3 should have been implicitly
        // closed.
        sendSimpleGetRequest(3);

        handleGoAwayResponse(5);
    }


    @Test
    public void testExceedMaxActiveStreams() throws Exception {
        // http2Connect() - modified
        enableHttp2(1);
        configureAndStartWebApplication();
        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();

        // validateHttp2InitialResponse() - modified
        parser.readFrame(true);
        parser.readFrame(true);
        parser.readFrame(true);
        parser.readFrame(true);
        parser.readFrame(true);

        Assert.assertEquals("0-Settings-[3]-[1]\n" +
                "0-Settings-End\n" +
                "0-Settings-Ack\n" +
                "0-Ping-[0,0,0,0,0,0,0,1]\n" +
                getSimpleResponseTrace(1)
                , output.getTrace());
        output.clearTrace();

        sendLargeGetRequest(3);

        sendSimpleGetRequest(5);

        // Default connection window size is 64k-1.
        // Initial request will have used 8k leaving 56k-1.
        // Stream window will be 64k-1.
        // Expecting
        // 1 * headers
        // 56k-1 of body (7 * ~8k)
        // 1 * error (could be in any order)
        for (int i = 0; i < 8; i++) {
            parser.readFrame(true);
        }
        parser.readFrame(true);

        Assert.assertTrue(output.getTrace(),
                output.getTrace().contains("5-RST-[" +
                        Http2Error.REFUSED_STREAM.getCode() + "]"));
        output.clearTrace();

        // Connection window is zero.
        // Stream window is 8k

        // Release the remaining body
        sendWindowUpdate(0, (1 << 31) - 2);
        // Allow for the 8k still in the stream window
        sendWindowUpdate(3, (1 << 31) - 8193);

        // 192k of body (24 * 8k)
        // 1 * error (could be in any order)
        for (int i = 0; i < 24; i++) {
            parser.readFrame(true);
        }
    }


    @Test
    public void testErrorOnWaitingStream() throws Exception {
        // http2Connect() - modified
        enableHttp2(1);
        configureAndStartWebApplication();
        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();

        // validateHttp2InitialResponse() - modified
        parser.readFrame(true);
        parser.readFrame(true);
        parser.readFrame(true);
        parser.readFrame(true);
        parser.readFrame(true);

        Assert.assertEquals("0-Settings-[3]-[1]\n" +
                "0-Settings-End\n" +
                "0-Settings-Ack\n" +
                "0-Ping-[0,0,0,0,0,0,0,1]\n" +
                getSimpleResponseTrace(1)
                , output.getTrace());
        output.clearTrace();

        sendLargeGetRequest(3);

        sendSimpleGetRequest(5);

        // Default connection window size is 64k-1.
        // Initial request will have used 8k leaving 56k-1.
        // Stream window will be 64k-1.
        // Expecting
        // 1 * headers
        // 56k-1 of body (7 * ~8k)
        // 1 * error (could be in any order)
        for (int i = 0; i < 8; i++) {
            parser.readFrame(true);
        }
        parser.readFrame(true);

        Assert.assertTrue(output.getTrace(),
                output.getTrace().contains("5-RST-[" +
                        Http2Error.REFUSED_STREAM.getCode() + "]"));
        output.clearTrace();

        // Connection window is zero.
        // Stream window is 8k

        // Expand the stream window too much to trigger an error
        // Allow for the 8k still in the stream window
        sendWindowUpdate(3, (1 << 31) - 1);

        parser.readFrame(true);
    }
}
