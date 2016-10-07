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
 * Unit tests for Section 6.9 of
 * <a href="https://tools.ietf.org/html/rfc7540">RFC 7540</a>.
 * <br>
 * The order of tests in this class is aligned with the order of the
 * requirements in the RFC.
 */
public class TestHttp2Section_6_9 extends Http2TestBase {

    @Test
    public void testZeroWindowUpdateConnection() throws Exception {
        http2Connect();

        sendWindowUpdate(0, 0);

        parser.readFrame(true);

        Assert.assertTrue(output.getTrace(), output.getTrace().startsWith(
                "0-Goaway-[1]-[" + Http2Error.PROTOCOL_ERROR.getCode() + "]-["));
    }


    @Test
    public void testZeroWindowUpdateStream() throws Exception {
        http2Connect();

        sendSimplePostRequest(3,  null,  false);
        sendWindowUpdate(3, 0);

        parser.readFrame(true);

        Assert.assertEquals("3-RST-[" + Http2Error.PROTOCOL_ERROR.getCode() + "]\n",
                output.getTrace());
    }


    @Test
    public void testWindowUpdateOnClosedStream() throws Exception {
        http2Connect();

        // Should not be an error so should be nothing to read
        sendWindowUpdate(1, 200);

        // So the next request should process normally
        sendSimpleGetRequest(3);
        readSimpleGetResponse();
        Assert.assertEquals(getSimpleResponseTrace(3), output.getTrace());
    }


    // TODO: Test always accounting for changes in flow control windows even if
    //       the frame is in error.


    @Test
    public void testWindowUpdateWrongLength() throws Exception {
        http2Connect();

        byte[] zeroLengthWindowFrame = new byte[9];
        // Length zero
        setOneBytes(zeroLengthWindowFrame, 3, FrameType.WINDOW_UPDATE.getIdByte());
        // No flags
        // Stream 1
        ByteUtil.set31Bits(zeroLengthWindowFrame, 5, 1);

        os.write(zeroLengthWindowFrame);
        os.flush();

        parser.readFrame(true);

        Assert.assertTrue(output.getTrace(), output.getTrace().startsWith(
                "0-Goaway-[1]-[" + Http2Error.FRAME_SIZE_ERROR.getCode() + "]-["));
    }


    @Test
    public void testEmptyDataFrameWithNoAvailableFlowControl() throws Exception {
        http2Connect();

        // Default connection window size is 64k - 1. Initial request will have
        // used 8k (56k -1).

        // Use up the remaining connection window. These requests require 56k
        // but there is only 56k - 1 available.
        for (int i = 3; i < 17; i += 2) {
            sendSimpleGetRequest(i);
            readSimpleGetResponse();
        }
        output.clearTrace();

        // It should be possible to send a request that generates an empty
        // response at this point
        sendEmptyGetRequest(17);
        // Headers
        parser.readFrame(true);
        // Body
        parser.readFrame(true);

        // Release Stream 15 which is waiting for a single byte.
        sendWindowUpdate(0,  1024);

        Assert.assertEquals(getEmptyResponseTrace(17), output.getTrace());
    }


    @Test
    public void testWindowSizeTooLargeStream() throws Exception {
        http2Connect();

        // Set up stream 3
        sendSimplePostRequest(3,  null,  false);

        // Super size the flow control window.
        sendWindowUpdate(3, (1 << 31) - 1);

        parser.readFrame(true);

        Assert.assertEquals("3-RST-[" + Http2Error.FLOW_CONTROL_ERROR.getCode() + "]\n",
                output.getTrace());
    }


    @Test
    public void testWindowSizeTooLargeConnection() throws Exception {
        http2Connect();

        // Super size the flow control window.
        sendWindowUpdate(0, (1 << 31) - 1);

        parser.readFrame(true);

        Assert.assertTrue(output.getTrace(), output.getTrace().startsWith(
                "0-Goaway-[1]-[" + Http2Error.FLOW_CONTROL_ERROR.getCode() + "]-["));
    }


    @Test
    public void testWindowSizeAndSettingsFrame() throws Exception {
        http2Connect();

        // Set up a POST request that echoes the body back
        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        byte[] dataFrameHeader = new byte[9];
        ByteBuffer dataPayload = ByteBuffer.allocate(8 * 1024);

        buildPostRequest(headersFrameHeader, headersPayload, false,
                dataFrameHeader, dataPayload, null, 3);

        // Write the headers
        writeFrame(headersFrameHeader, headersPayload);

        // Now use a settings frame to reduce the size of the flow control
        // window.
        sendSettings(0, false, new SettingValue(4, 4 * 1024));
        // Ack
        parser.readFrame(true);
        Assert.assertEquals("0-Settings-Ack\n", output.getTrace());
        output.clearTrace();

        // Write the body
        writeFrame(dataFrameHeader, dataPayload);

        // Window size updates after reading POST body
        parser.readFrame(true);
        parser.readFrame(true);
        Assert.assertEquals(
                "0-WindowSize-[8192]\n" +
                "3-WindowSize-[8192]\n",
                output.getTrace());
        output.clearTrace();

        // Read stream 3 headers and first part of body
        parser.readFrame(true);
        parser.readFrame(true);
        Assert.assertEquals(
                "3-HeadersStart\n" +
                "3-Header-[:status]-[200]\n" +
                "3-Header-[date]-["+ DEFAULT_DATE + "]\n" +
                "3-HeadersEnd\n" +
                "3-Body-4096\n", output.getTrace());
                output.clearTrace();

        // Now use a settings frame to further reduce the size of the flow
        // control window. This should make the stream 3 window negative
        sendSettings(0, false, new SettingValue(4, 2 * 1024));
        // Ack
        parser.readFrame(true);
        Assert.assertEquals("0-Settings-Ack\n", output.getTrace());
        output.clearTrace();

        // Now use a settings frame to increase the size of the flow control
        // window. The stream 3 window should still be negative
        sendSettings(0, false, new SettingValue(4, 3 * 1024));
        // Ack
        parser.readFrame(true);
        Assert.assertEquals("0-Settings-Ack\n", output.getTrace());
        output.clearTrace();

        // Do a POST that won't be affected by the above limit
        sendSimplePostRequest(5, null);
        // Window size updates after reading POST body
        parser.readFrame(true);
        parser.readFrame(true);
        Assert.assertEquals(
                "0-WindowSize-[128]\n" +
                "5-WindowSize-[128]\n",
                output.getTrace());
        output.clearTrace();
        // Headers + body
        parser.readFrame(true);
        parser.readFrame(true);
        Assert.assertEquals(
                "5-HeadersStart\n" +
                "5-Header-[:status]-[200]\n" +
                "5-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n" +
                "5-HeadersEnd\n" +
                "5-Body-128\n" +
                "5-EndOfStream\n", output.getTrace());
                output.clearTrace();

        // Now use a settings frame to restore the size of the flow control
        // window.
        sendSettings(0, false, new SettingValue(4, 64 * 1024 - 1));

        // Settings ack and stream 3 body are written from different threads.
        // Order depends on server side timing. Handle both possibilities.
        parser.readFrame(true);
        String trace = output.getTrace();
        String settingsAck = "0-Settings-Ack\n";
        String endOfStreamThree = "3-Body-4096\n3-EndOfStream\n";

        if (settingsAck.equals(trace)) {
            // Ack the end of stream 3
            output.clearTrace();
            parser.readFrame(true);
            Assert.assertEquals(endOfStreamThree, output.getTrace());
        } else {
            // End of stream 3 thenack
            Assert.assertEquals(endOfStreamThree, output.getTrace());
            output.clearTrace();
            parser.readFrame(true);
            Assert.assertEquals(settingsAck, output.getTrace());
        }
        output.clearTrace();
    }


    @Test
    public void testWindowSizeTooLargeViaSettings() throws Exception {
        http2Connect();

        // Set up stream 3
        sendSimplePostRequest(3,  null,  false);

        // Increase the flow control window but keep it under the limit
        sendWindowUpdate(3, 1 << 30);

        // Now increase beyond the limit via a settings frame
        sendSettings(0, false, new SettingValue(4,  1 << 30));
        // Ack
        parser.readFrame(true);
        Assert.assertEquals("3-RST-[" + Http2Error.FLOW_CONTROL_ERROR.getCode() + "]\n",
                output.getTrace());

    }
}
