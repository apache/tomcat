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
 * Unit tests for Section 4.2 of
 * <a href="https://tools.ietf.org/html/rfc7540">RFC 7540</a>.
 * <br>
 * The order of tests in this class is aligned with the order of the
 * requirements in the RFC.
 */
public class TestHttp2Section_4_2 extends Http2TestBase {

    @Test
    public void testFrameSizeLimitsTooBig() throws Exception {
        // HTTP2 upgrade
        http2Connect();

        // Overly large settings
        // Settings have to be a multiple of six
        int settingsCount = (ConnectionSettingsBase.DEFAULT_MAX_FRAME_SIZE / 6) + 1;
        int size = settingsCount * 6;
        byte[] settings = new byte[size + 9];
        // Header
        // Length
        ByteUtil.setThreeBytes(settings, 0, size);
        // Type
        settings[3] = FrameType.SETTINGS.getIdByte();
        // No flags
        // Stream 0

        // payload
        for (int i = 0; i < settingsCount; i++) {
            // Enable server push over and over again
            ByteUtil.setTwoBytes(settings, (i * 6) + 9, 2);
            ByteUtil.setFourBytes(settings, (i * 6) + 9 + 2, 1);
        }

        os.write(settings);

        handleGoAwayResponse(1, Http2Error.FRAME_SIZE_ERROR);
    }

    @Test
    public void testFrameTypeLimitsTooBig() throws Exception {
        // HTTP2 upgrade
        http2Connect();

        // Overly large ping
        byte[] ping = new byte[109];

        // Header
        // Length
        ByteUtil.setThreeBytes(ping, 0, 100);
        // Type
        ping[3] = FrameType.PING.getIdByte();
        // No flags
        // Stream 0
        // Empty payload

        os.write(ping);

        handleGoAwayResponse(1,  Http2Error.FRAME_SIZE_ERROR);
    }


    @Test
    public void testFrameTypeLimitsTooSmall() throws Exception {
        // HTTP2 upgrade
        http2Connect();

        // Too small ping
        byte[] ping = new byte[9];

        // Header
        // Length 0
        // Type
        ping[3] = FrameType.PING.getIdByte();
        // No flags
        // Stream 0
        // Empty payload

        os.write(ping);

        handleGoAwayResponse(1,  Http2Error.FRAME_SIZE_ERROR);
    }


    @Test
    public void testFrameTypeLimitsStream() throws Exception {
        // HTTP2 upgrade
        http2Connect();

        // Invalid priority
        byte[] priority = new byte[9];

        // Header
        // Length 0
        // Type
        priority[3] = FrameType.PRIORITY.getIdByte();
        // No flags
        // Stream 3
        ByteUtil.set31Bits(priority, 5, 3);
        // Empty payload

        os.write(priority);

        // Read Stream reset frame
        parser.readFrame(true);

        Assert.assertTrue(output.getTrace(),
                output.getTrace().startsWith("3-RST-[6]"));
    }
}
