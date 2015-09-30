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
 * Unit tests for Section 8.1 of
 * <a href="https://tools.ietf.org/html/rfc7540">RFC 7540</a>.
 * <br>
 * The order of tests in this class is aligned with the order of the
 * examples in the RFC.
 */
public class TestHttp2Section_8_1 extends Http2TestBase {

    @Test
    public void testPostWithContinuation() throws Exception {
        http2Connect();

    }

    @Test
    public void testSendAck() throws Exception {
        http2Connect();

        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        byte[] dataFrameHeader = new byte[9];
        ByteBuffer dataPayload = ByteBuffer.allocate(256);

        buildPostRequest(headersFrameHeader, headersPayload, true,
                dataFrameHeader, dataPayload, null, 3);

        // Write the headers
        writeFrame(headersFrameHeader, headersPayload);

        parser.readFrame(true);

        Assert.assertEquals("3-HeadersStart\n" +
                "3-Header-[:status]-[101]\n" +
                "3-HeadersEnd\n",
                output.getTrace());
        output.clearTrace();

        // Write the body
        writeFrame(dataFrameHeader, dataPayload);

        parser.readFrame(true);
        parser.readFrame(true);
        parser.readFrame(true);
        parser.readFrame(true);

        Assert.assertEquals("0-WindowSize-[256]\n" +
                "3-WindowSize-[256]\n" +
                "3-HeadersStart\n" +
                "3-Header-[:status]-[200]\n" +
                "3-HeadersEnd\n" +
                "3-Body-256\n" +
                "3-EndOfStream\n",
                output.getTrace());

    }
}
