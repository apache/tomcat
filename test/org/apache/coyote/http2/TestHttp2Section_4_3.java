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
 * Unit tests for Section 4.3 of
 * <a href="https://tools.ietf.org/html/rfc7540">RFC 7540</a>.
 * <br>
 * The order of tests in this class is aligned with the order of the
 * requirements in the RFC.
 */
public class TestHttp2Section_4_3 extends Http2TestBase {

    @Test
    public void testHeaderDecodingError() throws Exception {
        // HTTP2 upgrade
        http2Connect();

        // Build the simple request
        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildSimpleGetRequest(frameHeader, headersPayload, 3);

        // Try and corrupt the headerPayload
        headersPayload.put(0, (byte) (headersPayload.get(0) + 128));

        // Process the request
        writeFrame(frameHeader, headersPayload);

        // Read GOAWAY frame
        parser.readFrame(true);

        Assert.assertTrue(output.getTrace(), output.getTrace().startsWith(
                "0-Goaway-[1]-[" + Http2Error.COMPRESSION_ERROR.getCode() + "]-["));
    }


    @Test
    public void testHeaderContinuationContiguous() throws Exception {
        // HTTP2 upgrade
        http2Connect();

        // Part 1
        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildSimpleGetRequestPart1(frameHeader, headersPayload, 3);
        writeFrame(frameHeader, headersPayload);

        // Part 2
        headersPayload.clear();
        buildSimpleGetRequestPart2(frameHeader, headersPayload, 3);
        writeFrame(frameHeader, headersPayload);

        // headers, body
        parser.readFrame(true);
        parser.readFrame(true);

        Assert.assertEquals(getSimpleResponseTrace(3), output.getTrace());
    }


    @Test
    public void testHeaderContinuationNonContiguous() throws Exception {
        // HTTP2 upgrade
        http2Connect();

        // Part 1
        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildSimpleGetRequestPart1(frameHeader, headersPayload, 3);
        writeFrame(frameHeader, headersPayload);

        sendPing();

        // Read GOAWAY frame
        parser.readFrame(true);

        Assert.assertTrue(output.getTrace(), output.getTrace().startsWith(
                "0-Goaway-[1]-[" + Http2Error.COMPRESSION_ERROR.getCode() + "]-["));
    }
}
