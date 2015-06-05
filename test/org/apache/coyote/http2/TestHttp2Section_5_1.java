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
import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit tests for Section 5.§ of
 * <a href="https://tools.ietf.org/html/rfc7540">RFC 7540</a>.
 * <br>
 * The order of tests in this class is aligned with the order of the
 * requirements in the RFC.
 */
public class TestHttp2Section_5_1 extends Http2TestBase {

    @Test
    public void testIdleStateInvalidFrame01() throws Exception {
        http2Connect();

        sendWindowUpdate(3, 200);

        parser.readFrame(true);

        Assert.assertTrue(output.getTrace(),
                output.getTrace().startsWith("0-Goaway-[2147483647]-[" +
                        ErrorCode.PROTOCOL_ERROR.getErrorCode() + "]-["));
    }


    @Test
    public void testIdleStateInvalidFrame02() throws Exception {
        http2Connect();

        sendData(3, new byte[] {});

        parser.readFrame(true);

        Assert.assertTrue(output.getTrace(),
                output.getTrace().startsWith("0-Goaway-[2147483647]-[" +
                        ErrorCode.PROTOCOL_ERROR.getErrorCode() + "]-["));
    }


    // TODO: reserved local
    // TODO: reserved remote


    @Test
    public void halfClosedRemoteInvalidFrame() throws Exception {
        hpackEncoder = new HpackEncoder(ConnectionSettings.DEFAULT_HEADER_TABLE_SIZE);
        http2Connect();

        // This half-closes the stream since it includes the end of stream flag
        sendSimpleRequest(3);
        readSimpleResponse();
        Assert.assertEquals(getSimpleResponseTrace(3), output.getTrace());
        output.clearTrace();

        // This should trigger a stream error
        sendData(3, new byte[] {});
        parser.readFrame(true);

        Assert.assertTrue(output.getTrace(),
                output.getTrace().startsWith("0-Goaway-[2147483647]-[" +
                        ErrorCode.STREAM_CLOSED.getErrorCode() + "]-["));
    }


    @Test
    @Ignore // Need to handle stream closes
    public void testClosedInvalidFrame01() throws Exception {
        hpackEncoder = new HpackEncoder(ConnectionSettings.DEFAULT_HEADER_TABLE_SIZE);

        // HTTP2 upgrade
        http2Connect();

        // Build the simple request
        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildSimpleRequest(frameHeader, headersPayload, 3);

        // Remove the end of stream and end of headers flags
        frameHeader[4] = 0;

        // Process the request
        writeFrame(frameHeader, headersPayload);

        // Send a rst
        sendRst(3, ErrorCode.INTERNAL_ERROR.getErrorCode());

        // Then try sending some data (which should fail)
        sendData(3, new byte[] {});
        parser.readFrame(true);

        Assert.assertTrue(output.getTrace(),
                output.getTrace().startsWith("0-Goaway-[2147483647]-[" +
                        ErrorCode.STREAM_CLOSED.getErrorCode() + "]-["));
    }


    @Test
    public void testClosedInvalidFrame02() throws Exception {
        http2Connect();

        // Stream 1 is closed. This should trigger a stream error
        sendData(1, new byte[] {});
        parser.readFrame(true);

        Assert.assertTrue(output.getTrace(),
                output.getTrace().startsWith("0-Goaway-[2147483647]-[" +
                        ErrorCode.STREAM_CLOSED.getErrorCode() + "]-["));
    }


    // TODO: Invalid frames for each of the remaining states
}
