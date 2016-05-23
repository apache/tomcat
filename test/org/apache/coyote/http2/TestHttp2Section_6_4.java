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
 * Unit tests for Section 6.4 of
 * <a href="https://tools.ietf.org/html/rfc7540">RFC 7540</a>.
 * <br>
 * The order of tests in this class is aligned with the order of the
 * requirements in the RFC.
 */
public class TestHttp2Section_6_4 extends Http2TestBase {

    @Test
    public void testResetFrameOnStreamZero() throws Exception {
        // HTTP2 upgrade
        http2Connect();

        sendRst(0, Http2Error.NO_ERROR.getCode());

        // Go away
        parser.readFrame(true);

        Assert.assertTrue(output.getTrace(), output.getTrace().startsWith(
                "0-Goaway-[1]-[" + Http2Error.PROTOCOL_ERROR.getCode() + "]-["));
    }


    @Test
    public void testResetFrameOnIdleStream() throws Exception {
        // HTTP2 upgrade
        http2Connect();

        sendPriority(3, 0, 15);
        sendRst(3, Http2Error.NO_ERROR.getCode());

        // Go away
        parser.readFrame(true);

        Assert.assertTrue(output.getTrace(), output.getTrace().startsWith(
                "0-Goaway-[1]-[" + Http2Error.PROTOCOL_ERROR.getCode() + "]-["));
    }


    @Test
    public void testResetFrameWrongLength() throws Exception {
        // HTTP2 upgrade
        http2Connect();

        byte[] resetFrame = new byte[10];
        // length
        ByteUtil.setThreeBytes(resetFrame, 0, 1);
        // type
        resetFrame[3] = FrameType.RST.getIdByte();
        // No flags
        // Stream ID
        ByteUtil.set31Bits(resetFrame, 5, 3);

        // Payload - left as zero

        os.write(resetFrame);
        os.flush();

        // Read GOAWAY frame
        parser.readFrame(true);

        Assert.assertEquals("3-RST-[" + Http2Error.FRAME_SIZE_ERROR.getCode() + "]\n",
                output.getTrace());
    }
}
