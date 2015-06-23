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
 * Unit tests for Section 6.2 of
 * <a href="https://tools.ietf.org/html/rfc7540">RFC 7540</a>.
 * <br>
 * The order of tests in this class is aligned with the order of the
 * requirements in the RFC.
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

        // Go away
        parser.readFrame(true);

        Assert.assertTrue(output.getTrace(), output.getTrace().startsWith(
                "0-Goaway-[1]-[" + Http2Error.PROTOCOL_ERROR.getCode() + "]-["));
    }


    @Test
    public void testHeaderFrameWithPadding() throws Exception {
        http2Connect();

        byte[] padding= new byte[8];

        sendSimpleGetRequest(3, padding);
        readSimpleGetResponse();
        Assert.assertEquals(getSimpleResponseTrace(3), output.getTrace());
    }


    @Test
    public void testHeaderFrameWithNonZeroPadding() throws Exception {
        http2Connect();

        byte[] padding= new byte[8];
        padding[4] = 1;

        sendSimpleGetRequest(3, padding);

        // Goaway
        parser.readFrame(true);

        Assert.assertTrue(output.getTrace(), output.getTrace().startsWith(
                "0-Goaway-[1]-[" + Http2Error.PROTOCOL_ERROR.getCode() + "]-["));
    }


    @Test
    public void testHeaderFrameTooMuchPadding() throws Exception {
        http2Connect();

        byte[] headerFrame = new byte[10];

        // Header
        // length
        ByteUtil.setThreeBytes(headerFrame, 0, 1);
        // type 1 (headers)
        headerFrame[3] = 0x01;
        // flags 8 (padded)
        headerFrame[4] = 0x08;
        // stream 3
        ByteUtil.set31Bits(headerFrame, 5, 3);
        // payload (pad length of 1)
        headerFrame[9] = 1;

        os.write(headerFrame);
        os.flush();

        parser.readFrame(true);

        String trace = output.getTrace();
        Assert.assertTrue(trace, trace.startsWith("0-Goaway-[1]-[1]-["));
    }


    @Test
    public void testHeaderFrameWithZeroLengthPadding() throws Exception {
        http2Connect();

        byte[] padding= new byte[0];

        sendSimpleGetRequest(3, padding);
        readSimpleGetResponse();
        Assert.assertEquals(getSimpleResponseTrace(3), output.getTrace());
    }
}
