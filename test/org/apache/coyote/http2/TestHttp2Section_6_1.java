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

import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for Section 6.1 of <a href="https://tools.ietf.org/html/rfc7540">RFC 7540</a>. <br>
 * The order of tests in this class is aligned with the order of the requirements in the RFC.
 */
public class TestHttp2Section_6_1 extends Http2TestBase {

    @Test
    public void testDataFrame() throws Exception {
        http2Connect();

        // Disable overhead protection for window update as it breaks the test
        http2Protocol.setOverheadWindowUpdateThreshold(0);

        sendSimplePostRequest(3, null);
        readSimplePostResponse(false);

        Assert.assertEquals(
                "0-WindowSize-[128]\n" + "3-WindowSize-[128]\n" + "3-HeadersStart\n" + "3-Header-[:status]-[200]\n" +
                        "3-Header-[content-length]-[128]\n" + "3-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n" +
                        "3-HeadersEnd\n" + "3-Body-128\n" + "3-EndOfStream\n",
                output.getTrace());
    }


    @Test
    public void testDataFrameWithPadding() throws Exception {
        Logger.getLogger("org.apache.coyote").setLevel(Level.ALL);
        Logger.getLogger("org.apache.tomcat.util.net").setLevel(Level.ALL);
        try {
            http2Connect();

            // Disable overhead protection for window update as it breaks the
            // test
            http2Protocol.setOverheadWindowUpdateThreshold(0);

            byte[] padding = new byte[8];

            sendSimplePostRequest(3, padding);
            readSimplePostResponse(true);

            // The window updates for padding could occur anywhere since they
            // happen on a different thread to the response.
            // The connection window update is always present if there is
            // padding.
            String trace = output.getTrace();
            String paddingWindowUpdate = "0-WindowSize-[9]\n";
            Assert.assertTrue(trace, trace.contains(paddingWindowUpdate));
            trace = trace.replace(paddingWindowUpdate, "");

            // The stream window update may or may not be present depending on
            // timing. Remove it if present.
            if (trace.contains("3-WindowSize-[9]\n")) {
                trace = trace.replace("3-WindowSize-[9]\n", "");
            }

            Assert.assertEquals("0-WindowSize-[119]\n" + "3-WindowSize-[119]\n" + "3-HeadersStart\n" +
                    "3-Header-[:status]-[200]\n" + "3-Header-[content-length]-[119]\n" +
                    "3-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n" + "3-HeadersEnd\n" + "3-Body-119\n" +
                    "3-EndOfStream\n", trace);
        } finally {
            Logger.getLogger("org.apache.coyote").setLevel(Level.INFO);
            Logger.getLogger("org.apache.tomcat.util.net").setLevel(Level.INFO);
        }
    }


    @Test
    public void testDataFrameWithNonZeroPadding() throws Exception {
        http2Connect();

        byte[] padding = new byte[8];
        padding[4] = 0x01;

        sendSimplePostRequest(3, padding);

        // May see Window updates depending on timing
        skipWindowSizeFrames();

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

        handleGoAwayResponse(1);
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

        handleGoAwayResponse(1);
    }


    @Test
    public void testDataFrameWithZeroLengthPadding() throws Exception {
        http2Connect();

        // Disable overhead protection for window update as it breaks the test
        http2Protocol.setOverheadWindowUpdateThreshold(0);

        byte[] padding = new byte[0];

        sendSimplePostRequest(3, padding);
        readSimplePostResponse(true);

        // The window updates for padding could occur anywhere since they
        // happen on a different thread to the response.
        // The connection window update is always present if there is
        // padding.
        String trace = output.getTrace();
        String paddingWindowUpdate = "0-WindowSize-[1]\n";
        Assert.assertTrue(trace, trace.contains(paddingWindowUpdate));
        trace = trace.replace(paddingWindowUpdate, "");

        // The stream window update may or may not be present depending on
        // timing. Remove it if present.
        paddingWindowUpdate = "3-WindowSize-[1]\n";
        if (trace.contains(paddingWindowUpdate)) {
            trace = trace.replace(paddingWindowUpdate, "");
        }

        Assert.assertEquals(
                "0-WindowSize-[127]\n" + "3-WindowSize-[127]\n" + "3-HeadersStart\n" + "3-Header-[:status]-[200]\n" +
                        "3-Header-[content-length]-[127]\n" + "3-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n" +
                        "3-HeadersEnd\n" + "3-Body-127\n" + "3-EndOfStream\n",
                trace);
    }
}
