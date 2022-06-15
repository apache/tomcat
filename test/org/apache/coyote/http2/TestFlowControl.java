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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.LogManager;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.http.MimeHeaders;

public class TestFlowControl extends Http2TestBase {

    /*
     * https://tomcat.markmail.org/thread/lijsebphms7hr3zj
     */
    @Test
    public void testNotFound() throws Exception {

        LogManager.getLogManager().getLogger("org.apache.coyote.http2").setLevel(Level.ALL);
        try {
            http2Connect();

            // Connection and per-stream flow control default to 64k-1 bytes

            // Set up a POST request to a non-existent end-point
            // Generate headers
            byte[] headersFrameHeader = new byte[9];
            ByteBuffer headersPayload = ByteBuffer.allocate(128);

            MimeHeaders headers = new MimeHeaders();
            headers.addValue(":method").setString("POST");
            headers.addValue(":scheme").setString("http");
            headers.addValue(":path").setString("/path-does-not-exist");
            headers.addValue(":authority").setString("localhost:" + getPort());
            headers.addValue("content-length").setLong(65536);
            hpackEncoder.encode(headers, headersPayload);

            headersPayload.flip();

            ByteUtil.setThreeBytes(headersFrameHeader, 0, headersPayload.limit());
            headersFrameHeader[3] = FrameType.HEADERS.getIdByte();
            // Flags. end of headers (0x04)
            headersFrameHeader[4] = 0x04;
            // Stream id
            ByteUtil.set31Bits(headersFrameHeader, 5, 3);

            writeFrame(headersFrameHeader, headersPayload);

            // Generate body
            // Max data payload is 16k
            byte[] dataFrameHeader = new byte[9];
            ByteBuffer dataPayload = ByteBuffer.allocate(16 * 1024);

            while (dataPayload.hasRemaining()) {
                dataPayload.put((byte) 'x');
            }
            dataPayload.flip();

            // Size
            ByteUtil.setThreeBytes(dataFrameHeader, 0, dataPayload.limit());
            ByteUtil.set31Bits(dataFrameHeader, 5, 3);

            // Read the 404 error page
            // headers
            parser.readFrame(true);
            // body
            parser.readFrame(true);
            // reset (because the request body was not fully read)
            parser.readFrame(true);

            // Validate response
            // Response size varies as error page is generated and includes version
            // number
            String trace = output.getTrace();
            int start = trace.indexOf("[content-length]-[") + 18;
            int end = trace.indexOf("]", start);
            String contentLength = trace.substring(start, end);
            // Language will depend on locale
            String language = Locale.getDefault().getLanguage();

            Assert.assertEquals(
                    "3-HeadersStart\n" +
                    "3-Header-[:status]-[404]\n" +
                    "3-Header-[content-type]-[text/html;charset=utf-8]\n" +
                    "3-Header-[content-language]-[" + language + "]\n" +
                    "3-Header-[content-length]-[" + contentLength + "]\n" +
                    "3-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n" +
                    "3-HeadersEnd\n" +
                    "3-Body-" + contentLength + "\n" +
                    "3-EndOfStream\n" +
                    "3-RST-[8]\n", output.getTrace());
            output.clearTrace();

            // Write 3*16k=48k of request body
            int count = 0;
            while (count < 3) {
                writeFrame(dataFrameHeader, dataPayload);
                waitForWindowSize(0);
                dataPayload.position(0);
                count++;
            }

            // EOS
            dataFrameHeader[4] = 0x01;
            writeFrame(dataFrameHeader, dataPayload);
            waitForWindowSize(0);
        } finally {
            LogManager.getLogManager().getLogger("org.apache.coyote.http2").setLevel(Level.INFO);
        }
    }


    /*
     * This might be unnecessary but given the potential for timing differences
     * across different systems a more robust approach seems prudent.
     */
    private void waitForWindowSize(int streamId) throws Http2Exception, IOException {
        String prefix = streamId + "-WindowSize-";
        boolean found = false;
        String trace;
        do {
            parser.readFrame(true);
            trace = output.getTrace();
            output.clearTrace();
            found = trace.startsWith(prefix);
        } while (!found);
    }
}

