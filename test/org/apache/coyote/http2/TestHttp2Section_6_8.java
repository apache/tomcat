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
 * Unit tests for Section 6.8 of
 * <a href="https://tools.ietf.org/html/rfc7540">RFC 7540</a>.
 * <br>
 * The order of tests in this class is aligned with the order of the
 * requirements in the RFC.
 */
public class TestHttp2Section_6_8 extends Http2TestBase {

    private static final long PNG_ACK_DELAY_MS = 1000;

    @Test
    public void testGoawayIgnoreNewStreams() throws Exception {
        setPingAckDelayMillis(PNG_ACK_DELAY_MS);

        // HTTP2 upgrade
        http2Connect();

        getTomcatInstance().getConnector().pause();

        // Go away
        parser.readFrame(true);
        Assert.assertEquals("0-Goaway-[2147483647]-[0]-[null]", output.getTrace());
        output.clearTrace();

        // Should be processed
        sendSimpleGetRequest(3);

        Thread.sleep(PNG_ACK_DELAY_MS + 200);

        // Should be ignored
        sendSimpleGetRequest(5);

        parser.readFrame(true);
        parser.readFrame(true);

        Assert.assertEquals(getSimpleResponseTrace(3),  output.getTrace());
        output.clearTrace();

        // Finally the go away frame
        parser.readFrame(true);
        Assert.assertEquals("0-Goaway-[3]-[0]-[null]", output.getTrace());
    }


    @Test
    public void testGoawayFrameNonZeroStream() throws Exception {
        // HTTP2 upgrade
        http2Connect();

        sendGoaway(1, 1, Http2Error.NO_ERROR.getCode(), null);

        // Go away
        parser.readFrame(true);

        Assert.assertTrue(output.getTrace(), output.getTrace().startsWith(
                "0-Goaway-[1]-[" + Http2Error.PROTOCOL_ERROR.getCode() + "]-["));
    }


    // TODO Test header processing and window size processing for ignored
    //      streams
}
