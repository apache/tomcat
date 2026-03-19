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
 * Unit tests for Section 6.2 of <a href="https://tools.ietf.org/html/rfc7540">RFC 7540</a>. <br>
 * The order of tests in this class is aligned with the order of the requirements in the RFC.
 */
public class TestHttp2Section_6_2 extends Http2TestBase {

    @Test
    public void testHeaderFrameOnStreamZero() throws Exception {
        // HTTP2 upgrade
        http2Connect();

        // Part 1
        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildSimpleGetRequestPart1(frameHeader, headersPayload, 0);
        writeFrame(frameHeader, headersPayload);

        handleGoAwayResponse(1);
    }


    @Test
    public void testHeaderFrameWithPadding() throws Exception {
        http2Connect();

        byte[] padding = new byte[8];

        sendSimpleGetRequest(3, padding);
        readSimpleGetResponse();
        Assert.assertEquals(getSimpleResponseTrace(3), output.getTrace());
    }


    @Test
    public void testHeaderFrameWithNonZeroPadding() throws Exception {
        http2Connect();

        byte[] padding = new byte[8];
        padding[4] = 1;

        sendSimpleGetRequest(3, padding);

        handleGoAwayResponse(1);
    }


    @Test
    public void testHeaderFrameTooMuchPadding() throws Exception {
        http2Connect();

        byte[] headerFrame = new byte[10];

        // Header
        // length
        ByteUtil.setThreeBytes(headerFrame, 0, 1);
        headerFrame[3] = FrameType.HEADERS.getIdByte();
        // flags 8 (padded)
        headerFrame[4] = 0x08;
        // stream 3
        ByteUtil.set31Bits(headerFrame, 5, 3);
        // payload (pad length of 1)
        headerFrame[9] = 1;

        os.write(headerFrame);
        os.flush();

        handleGoAwayResponse(1);
    }


    @Test
    public void testHeaderFrameTooMuchPaddingWithPriority() throws Exception {
        // Tests the case where both PADDED and PRIORITY flags are set and the
        // padding length is too large relative to the payload after accounting
        // for the optional bytes (1 byte pad length + 5 bytes priority = 6 bytes).
        // With payloadSize=8 and padLength=3, the actual available payload
        // after optional bytes is only 2, so padLength >= available triggers
        // a PROTOCOL_ERROR and a GOAWAY frame must be sent.
        http2Connect();

        // 9 bytes frame header + 8 bytes payload
        byte[] headerFrame = new byte[17];

        // Header
        // length = 8
        ByteUtil.setThreeBytes(headerFrame, 0, 8);
        headerFrame[3] = FrameType.HEADERS.getIdByte();
        // flags: PADDED (0x08) | PRIORITY (0x20)
        headerFrame[4] = 0x28;
        // stream 3
        ByteUtil.set31Bits(headerFrame, 5, 3);
        // payload:
        // pad length = 3 (too large: only 2 bytes remain after 6 optional bytes)
        headerFrame[9] = 3;
        // priority: 5 bytes (bytes 10-14, all zero)
        // remaining 2 bytes: bytes 15-16 (all zero)

        os.write(headerFrame);
        os.flush();

        // 1 is the last stream processed before the connection error (stream 1
        // from the initial HTTP/1.1 upgrade)
        handleGoAwayResponse(1);
    }


    @Test
    public void testHeaderFrameWithZeroLengthPadding() throws Exception {
        http2Connect();

        byte[] padding = new byte[0];

        sendSimpleGetRequest(3, padding);
        readSimpleGetResponse();
        Assert.assertEquals(getSimpleResponseTrace(3), output.getTrace());
    }
}
