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
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for Section 5.2 of <a href="https://tools.ietf.org/html/rfc7540">RFC 7540</a>. <br>
 * The order of tests in this class is aligned with the order of the requirements in the RFC.
 */
public class TestHttp2Section_5_2 extends Http2TestBase {

    /*
     * Get the connection to a point where 1k of 8k response body has been read and the flow control for the stream has
     * no capacity left.
     */
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        http2Connect();

        // This test uses small window updates that will trigger the excessive
        // overhead protection so disable it.
        http2Protocol.setOverheadWindowUpdateThreshold(0);

        // Set the default window size to 1024 bytes
        sendSettings(0, false, new SettingValue(4, 1024));
        // Wait for the ack
        parser.readFrame();
        output.clearTrace();

        // Headers + 8k response
        sendSimpleGetRequest(3);

        // Headers
        parser.readFrame();
        // First 1k of body
        parser.readFrame();
        output.clearTrace();
    }


    @Test
    public void testFlowControlLimits01() throws Exception {
        readBytes(20);
        clearRemainder();
    }


    @Test
    public void testFlowControlLimits02() throws Exception {
        readBytes(1);
        readBytes(1);
        readBytes(1024);
        readBytes(1);
        clearRemainder();
    }


    @Test
    public void testFlowControlLimits03() throws Exception {
        readBytes(8192, 7168);
    }


    @Test
    public void testFlowControlLimits04() throws Exception {
        readBytes(7168, 7168, true);
    }


    private void readBytes(int len) throws Exception {
        readBytes(len, len);
    }


    private void readBytes(int len, int expected) throws Exception {
        readBytes(len, expected, len > expected);
    }


    private void readBytes(int len, int expected, boolean eos) throws Exception {
        sendWindowUpdate(3, len);
        parser.readFrame();
        String expectedTrace = "3-Body-" + expected + "\n";
        if (eos) {
            expectedTrace += "3-EndOfStream\n";
        }
        Assert.assertEquals(expectedTrace, output.getTrace());
        output.clearTrace();
    }


    private void clearRemainder() throws Exception {
        // Remainder
        sendWindowUpdate(3, 8192);
        parser.readFrame();
    }


    @Test
    public void testFlowControlAndStreamReset() throws Exception {
        // At start of test Stream 3 has 1k of 8k written and no capacity left

        // Empty the connection window
        sendWindowUpdate(3, 7 * 1024);
        parser.readFrame();

        sendWindowUpdate(0, 1);

        for (int i = 5; i < 17; i += 2) {
            sendSimpleGetRequest(i);
            parser.readFrame();
            parser.readFrame();
            sendWindowUpdate(i, 7 * 1024);
            parser.readFrame();
        }

        // Connection flow control window is now empty

        // Put a stream on the backlog and then immediately cancel it
        sendSimpleGetRequest(17);
        sendRst(17, Http2Error.NO_ERROR.getCode());
        // Read headers
        parser.readFrame();
        // Read reset from server
        parser.readFrame();

        // Increase default window size to 8k
        sendSettings(0, false, new SettingValue(4, 8 * 1024));
        // Settings ACK
        parser.readFrame();

        sendSimpleGetRequest(19);
        // Read headers
        parser.readFrame();

        // Clear trace as what happens from this point is of primary interest in this test
        output.clearTrace();

        // This should release the entire body for stream 19
        sendWindowUpdate(19, 7 * 1024);
        sendWindowUpdate(0, 8 * 1024);

        parser.readFrame();
        Assert.assertEquals("19-Body-8192\n19-EndOfStream\n", output.getTrace());
    }
}
