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

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for Section 6.1 of
 * <a href="https://tools.ietf.org/html/rfc7540">RFC 7540</a>.
 * <br>
 * The order of tests in this class is aligned with the order of the
 * requirements in the RFC.
 */
public class TestHttp2Section_6_1 extends Http2TestBase {

    @Test
    public void testDataFrame() throws Exception {
        http2Connect();

        sendSimplePostRequest(3, null);
        readSimplePostResponse(false);

        Assert.assertEquals("0-WindowSize-[128]\n" +
                "3-WindowSize-[128]\n" +
                "3-HeadersStart\n" +
                "3-Header-[:status]-[200]\n" +
                "3-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n" +
                "3-HeadersEnd\n" +
                "3-Body-128\n" +
                "3-EndOfStream\n", output.getTrace());
    }


    @Test
    public void testDataFrameWithPadding() throws Exception {
        http2Connect();

        byte[] padding = new byte[8];

        sendSimplePostRequest(3, padding);
        readSimplePostResponse(true);


        // The window update for the padding could occur anywhere since it
        // happens on a different thead to the response.
        String trace = output.getTrace();
        String paddingWindowUpdate = "0-WindowSize-[9]\n3-WindowSize-[9]\n";

        Assert.assertTrue(trace, trace.contains(paddingWindowUpdate));
        trace = trace.replace(paddingWindowUpdate, "");

        Assert.assertEquals("0-WindowSize-[119]\n" +
                "3-WindowSize-[119]\n" +
                "3-HeadersStart\n" +
                "3-Header-[:status]-[200]\n" +
                "3-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n" +
                "3-HeadersEnd\n" +
                "3-Body-119\n" +
                "3-EndOfStream\n", trace);
    }


    @Test
    public void testDataFrameWithNonZeroPadding() throws Exception {
        http2Connect();

        byte[] padding = new byte[8];
        padding[4] = 0x01;

        sendSimplePostRequest(3, padding);
        parser.readFrame(true);
        // May see Window updates depending on timing
        while (output.getTrace().contains("WindowSize")) {
            output.clearTrace();
            parser.readFrame(true);
        }

        String trace = output.getTrace();
        Assert.assertTrue(trace, trace.startsWith("0-Goaway-[3]-[1]-["));
    }


    @Test
    public void testDataFrameOnStreamZero() throws Exception {
        http2Connect();

        byte[] dataFrame = new byte[10];

        // Header
        // length
        ByteUtil.setThreeBytes(dataFrame, 0, 1);
        // type (0 for data)
        // flags (0)
        // stream (0)
        // payload (0)

        os.write(dataFrame);
        os.flush();

        parser.readFrame(true);

        String trace = output.getTrace();
        Assert.assertTrue(trace, trace.startsWith("0-Goaway-[1]-[1]-["));
    }


    @Test
    public void testDataFrameTooMuchPadding() throws Exception {
        http2Connect();

        byte[] dataFrame = new byte[10];

        // Header
        // length
        ByteUtil.setThreeBytes(dataFrame, 0, 1);
        // type 0 (data)
        // flags 8 (padded)
        dataFrame[4] = 0x08;
        // stream 3
        ByteUtil.set31Bits(dataFrame, 5, 3);
        // payload (pad length of 1)
        dataFrame[9] = 1;

        os.write(dataFrame);
        os.flush();

        parser.readFrame(true);

        String trace = output.getTrace();
        Assert.assertTrue(trace, trace.startsWith("0-Goaway-[1]-[1]-["));
    }


    @Test
    public void testDataFrameWithZeroLengthPadding() throws Exception {
        http2Connect();

        byte[] padding = new byte[0];

        sendSimplePostRequest(3, padding);
        // Since padding is zero length, response looks like there is none.
        readSimplePostResponse(false);

        Assert.assertEquals("0-WindowSize-[127]\n" +
                "3-WindowSize-[127]\n" +
                "3-HeadersStart\n" +
                "3-Header-[:status]-[200]\n" +
                "3-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n" +
                "3-HeadersEnd\n" +
                "3-Body-127\n" +
                "3-EndOfStream\n", output.getTrace());
    }
}
