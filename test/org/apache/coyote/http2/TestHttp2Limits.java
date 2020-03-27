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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.hamcrest.Description;
import org.hamcrest.MatcherAssert;
import org.hamcrest.TypeSafeMatcher;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http2.HpackEncoder.State;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.res.StringManager;

public class TestHttp2Limits extends Http2TestBase {

    private static final StringManager sm = StringManager.getManager(TestHttp2Limits.class);

    @Test
    public void testHeaderLimits1x128() throws Exception {
        // Well within limits
        doTestHeaderLimits(1, 128, FailureMode.NONE);
    }


    @Test
    public void testHeaderLimits100x32() throws Exception {
        // Just within default maxHeaderCount
        // Note request has 4 standard headers
        doTestHeaderLimits(96, 32, FailureMode.NONE);
    }


    @Test
    public void testHeaderLimits101x32() throws Exception {
        // Just above default maxHeaderCount
        doTestHeaderLimits(97, 32, FailureMode.STREAM_RESET);
    }


    @Test
    public void testHeaderLimits20x32WithLimit10() throws Exception {
        // Check lower count limit is enforced
        doTestHeaderLimits(20, 32, -1, 10, Constants.DEFAULT_MAX_HEADER_SIZE, 0,
                FailureMode.STREAM_RESET);
    }


    @Test
    public void testHeaderLimits8x1144() throws Exception {
        // Just within default maxHttpHeaderSize
        // per header overhead plus standard 3 headers
        doTestHeaderLimits(7, 1144, FailureMode.NONE);
    }


    @Test
    public void testHeaderLimits8x1145() throws Exception {
        // Just above default maxHttpHeaderSize
        doTestHeaderLimits(7, 1145, FailureMode.STREAM_RESET);
    }


    @Test
    public void testHeaderLimits3x1024WithLimit2048() throws Exception {
        // Check lower size limit is enforced
        doTestHeaderLimits(3, 1024, -1, Constants.DEFAULT_MAX_HEADER_COUNT, 2 * 1024, 0,
                FailureMode.STREAM_RESET);
    }


    @Test
    public void testHeaderLimits1x12k() throws Exception {
        // Bug 60232
        doTestHeaderLimits(1, 12*1024, FailureMode.STREAM_RESET);
    }


    @Test
    public void testHeaderLimits1x12kin1kChunks() throws Exception {
        // Bug 60232
        doTestHeaderLimits(1, 12*1024, 1024, FailureMode.STREAM_RESET);
    }


    @Test
    public void testHeaderLimits1x12kin1kChunksThenNewRequest() throws Exception {
        // Bug 60232
        doTestHeaderLimits(1, 12*1024, 1024, FailureMode.STREAM_RESET);


        output.clearTrace();
        sendSimpleGetRequest(5);
        parser.readFrame(true);
        parser.readFrame(true);
        Assert.assertEquals(getSimpleResponseTrace(5), output.getTrace());
    }


    @Test
    public void testHeaderLimits1x32k() throws Exception {
        // Bug 60232
        doTestHeaderLimits(1, 32*1024, FailureMode.CONNECTION_RESET);
    }


    @Test
    public void testHeaderLimits1x32kin1kChunks() throws Exception {
        // Bug 60232
        // 500ms per frame write delay to give server a chance to process the
        // stream reset and the connection reset before the request is fully
        // sent.
        doTestHeaderLimits(1, 32*1024, 1024, 500, FailureMode.CONNECTION_RESET);
    }


    @Test
    public void testHeaderLimits1x128k() throws Exception {
        // Bug 60232
        doTestHeaderLimits(1, 128*1024, FailureMode.CONNECTION_RESET);
    }


    @Test
    public void testHeaderLimits1x512k() throws Exception {
        // Bug 60232
        doTestHeaderLimits(1, 512*1024, FailureMode.CONNECTION_RESET);
    }


    @Test
    public void testHeaderLimits10x512k() throws Exception {
        // Bug 60232
        doTestHeaderLimits(10, 512*1024, FailureMode.CONNECTION_RESET);
    }


    private void doTestHeaderLimits(int headerCount, int headerSize, FailureMode failMode)
            throws Exception {
        doTestHeaderLimits(headerCount, headerSize, -1, failMode);
    }


    private void doTestHeaderLimits(int headerCount, int headerSize, int maxHeaderPayloadSize,
            FailureMode failMode) throws Exception {
        doTestHeaderLimits(headerCount, headerSize, maxHeaderPayloadSize, 0, failMode);
    }


    private void doTestHeaderLimits(int headerCount, int headerSize, int maxHeaderPayloadSize,
            int delayms, FailureMode failMode) throws Exception {
        doTestHeaderLimits(headerCount, headerSize, maxHeaderPayloadSize,
                Constants.DEFAULT_MAX_HEADER_COUNT, Constants.DEFAULT_MAX_HEADER_SIZE, delayms,
                failMode);
    }


    private void doTestHeaderLimits(int headerCount, int headerSize, int maxHeaderPayloadSize,
            int maxHeaderCount, int maxHeaderSize, int delayms, FailureMode failMode)
            throws Exception {

        // Build the custom headers
        List<String[]> customHeaders = new ArrayList<>();
        StringBuilder headerValue = new StringBuilder(headerSize);
        // Does not need to be secure
        Random r = new Random();
        for (int i = 0; i < headerSize; i++) {
            // Random lower case characters
            headerValue.append((char) ('a' + r.nextInt(26)));
        }
        String v = headerValue.toString();
        for (int i = 0; i < headerCount; i++) {
            customHeaders.add(new String[] {"X-TomcatTest" + i, v});
        }

        enableHttp2();

        http2Protocol.setMaxHeaderCount(maxHeaderCount);
        http2Protocol.setMaxHeaderSize(maxHeaderSize);

        configureAndStartWebApplication();
        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        if (maxHeaderPayloadSize == -1) {
            maxHeaderPayloadSize = output.getMaxFrameSize();
        }

        // Build the simple request
        byte[] frameHeader = new byte[9];
        // Assumes at least one custom header and that all headers are the same
        // length. These assumptions are valid for these tests.
        ByteBuffer headersPayload = ByteBuffer.allocate(200 + (int) (customHeaders.size() *
                customHeaders.iterator().next()[1].length() * 1.2));

        populateHeadersPayload(headersPayload, customHeaders, "/simple");

        Exception e = null;
        try {
            int written = 0;
            int left = headersPayload.limit() - written;
            while (left > 0) {
                int thisTime = Math.min(left, maxHeaderPayloadSize);
                populateFrameHeader(frameHeader, written, left, thisTime, 3);
                writeFrame(frameHeader, headersPayload, headersPayload.limit() - left,
                        thisTime, delayms);
                left -= thisTime;
                written += thisTime;
            }
        } catch (IOException ioe) {
            e = ioe;
        }

        switch (failMode) {
        case NONE: {
            // Expect a normal response
            readSimpleGetResponse();
            Assert.assertEquals(getSimpleResponseTrace(3), output.getTrace());
            Assert.assertNull(e);
            break;
        }
        case STREAM_RESET: {
            // Expect a stream reset
            parser.readFrame(true);
            Assert.assertEquals("3-RST-[11]\n", output.getTrace());
            Assert.assertNull(e);
            break;
        }
        case CONNECTION_RESET: {
            // This message uses i18n and needs to be used in a regular
            // expression (since we don't know the connection ID). Generate the
            // string as a regular expression and then replace '[' and ']' with
            // the escaped values.
            String limitMessage = sm.getString("http2Parser.headerLimitSize", "\\d++", "3");
            limitMessage = limitMessage.replace("[", "\\[").replace("]", "\\]");
            // Connection reset. Connection ID will vary so use a pattern
            // On some platform / Connector combinations (e.g. Windows / APR),
            // the TCP connection close will be processed before the client gets
            // a chance to read the connection close frame which will trigger an
            // IOException when we try to read the frame.
            // Note: Some platforms will allow the read if if the write fails
            //       above.
            try {
                parser.readFrame(true);
                MatcherAssert.assertThat(output.getTrace(), RegexMatcher.matchesRegex(
                        "0-Goaway-\\[1\\]-\\[11\\]-\\[" + limitMessage + "\\]"));
            } catch (IOException se) {
                // Expected on some platforms
            }
            break;
        }
        }
    }


    private void populateHeadersPayload(ByteBuffer headersPayload, List<String[]> customHeaders,
            String path) throws Exception {
        MimeHeaders headers = new MimeHeaders();
        headers.addValue(":method").setString("GET");
        headers.addValue(":scheme").setString("http");
        headers.addValue(":path").setString(path);
        headers.addValue(":authority").setString("localhost:" + getPort());
        for (String[] customHeader : customHeaders) {
            headers.addValue(customHeader[0]).setString(customHeader[1]);
        }
        State state = hpackEncoder.encode(headers, headersPayload);
        if (state != State.COMPLETE) {
            throw new Exception("Unable to build headers");
        }
        headersPayload.flip();

        log.debug("Headers payload generated of size [" + headersPayload.limit() + "]");
    }


    private void populateFrameHeader(byte[] frameHeader, int written, int left, int thisTime,
            int streamId) throws Exception {
        ByteUtil.setThreeBytes(frameHeader, 0, thisTime);
        if (written == 0) {
            frameHeader[3] = FrameType.HEADERS.getIdByte();
            // Flags. End of stream
            frameHeader[4] = 0x01;
        } else {
            frameHeader[3] = FrameType.CONTINUATION.getIdByte();
        }
        if (left == thisTime) {
            // Flags. End of headers
            frameHeader[4] = (byte) (frameHeader[4] + 0x04);
        }

        // Stream id
        ByteUtil.set31Bits(frameHeader, 5, streamId);
    }


    @Test
    public void testCookieLimit1() throws Exception {
        doTestCookieLimit(1, 0);
    }


    @Test
    public void testCookieLimit2() throws Exception {
        doTestCookieLimit(2, 0);
    }


    @Test
    public void testCookieLimit100() throws Exception {
        doTestCookieLimit(100, 0);
    }


    @Test
    public void testCookieLimit100WithLimit50() throws Exception {
        doTestCookieLimit(100, 50, 1);
    }


    @Test
    public void testCookieLimit200() throws Exception {
        doTestCookieLimit(200, 0);
    }


    @Test
    public void testCookieLimit201() throws Exception {
        doTestCookieLimit(201, 1);
    }


    private void doTestCookieLimit(int cookieCount, int failMode) throws Exception {
        doTestCookieLimit(cookieCount, Constants.DEFAULT_MAX_COOKIE_COUNT, failMode);
    }


    private void doTestCookieLimit(int cookieCount, int maxCookieCount, int failMode)
            throws Exception {

        enableHttp2();

        Connector connector = getTomcatInstance().getConnector();
        connector.setMaxCookieCount(maxCookieCount);

        configureAndStartWebApplication();
        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        output.setTraceBody(true);

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(8192);

        List<String[]> customHeaders = new ArrayList<>();
        for (int i = 0; i < cookieCount; i++) {
            customHeaders.add(new String[] {"Cookie", "a" + cookieCount + "=b" + cookieCount});
        }

        populateHeadersPayload(headersPayload, customHeaders, "/cookie");
        populateFrameHeader(frameHeader, 0, headersPayload.limit(), headersPayload.limit(), 3);

        writeFrame(frameHeader, headersPayload);

        switch (failMode) {
        case 0: {
            parser.readFrame(true);
            parser.readFrame(true);
            parser.readFrame(true);
            System.out.println(output.getTrace());
            Assert.assertEquals(getCookieResponseTrace(3, cookieCount), output.getTrace());
            break;
        }
        case 1: {
            // Check status is 400
            parser.readFrame(true);
            Assert.assertTrue(output.getTrace(), output.getTrace().startsWith(
                    "3-HeadersStart\n3-Header-[:status]-[400]"));
            output.clearTrace();
            // Check EOS followed by error page body
            parser.readFrame(true);
            Assert.assertTrue(output.getTrace(), output.getTrace().startsWith("3-EndOfStream\n3-Body-<!doctype"));
            break;
        }
        default: {
            Assert.fail("Unknown failure mode specified");
        }
        }
    }


    @Test
    public void doTestPostWithTrailerHeadersDefaultLimit() throws Exception{
        doTestPostWithTrailerHeaders(Constants.DEFAULT_MAX_TRAILER_COUNT,
                Constants.DEFAULT_MAX_TRAILER_SIZE, FailureMode.NONE);
    }


    @Test
    public void doTestPostWithTrailerHeadersCount0() throws Exception{
        doTestPostWithTrailerHeaders(0, Constants.DEFAULT_MAX_TRAILER_SIZE,
                FailureMode.STREAM_RESET);
    }


    @Test
    public void doTestPostWithTrailerHeadersSize0() throws Exception{
        doTestPostWithTrailerHeaders(Constants.DEFAULT_MAX_TRAILER_COUNT, 0,
                FailureMode.CONNECTION_RESET);
    }


    private void doTestPostWithTrailerHeaders(int maxTrailerCount, int maxTrailerSize,
            FailureMode failMode) throws Exception {
        enableHttp2();

        http2Protocol.setAllowedTrailerHeaders(TRAILER_HEADER_NAME);
        http2Protocol.setMaxTrailerCount(maxTrailerCount);
        http2Protocol.setMaxTrailerSize(maxTrailerSize);

        configureAndStartWebApplication();
        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        byte[] dataFrameHeader = new byte[9];
        ByteBuffer dataPayload = ByteBuffer.allocate(256);
        byte[] trailerFrameHeader = new byte[9];
        ByteBuffer trailerPayload = ByteBuffer.allocate(256);

        buildPostRequest(headersFrameHeader, headersPayload, false, dataFrameHeader, dataPayload,
                null, trailerFrameHeader, trailerPayload, 3);

        // Write the headers
        writeFrame(headersFrameHeader, headersPayload);
        // Body
        writeFrame(dataFrameHeader, dataPayload);
        // Trailers
        writeFrame(trailerFrameHeader, trailerPayload);

        switch (failMode) {
        case NONE: {
            parser.readFrame(true);
            parser.readFrame(true);
            parser.readFrame(true);
            parser.readFrame(true);

            String len = Integer.toString(256 + TRAILER_HEADER_VALUE.length());

            Assert.assertEquals("0-WindowSize-[256]\n" +
                    "3-WindowSize-[256]\n" +
                    "3-HeadersStart\n" +
                    "3-Header-[:status]-[200]\n" +
                    "3-Header-[content-length]-[" + len + "]\n" +
                    "3-Header-[date]-["+ DEFAULT_DATE + "]\n" +
                    "3-HeadersEnd\n" +
                    "3-Body-" +
                    len +
                    "\n" +
                    "3-EndOfStream\n",
                    output.getTrace());
            break;
        }
        case STREAM_RESET: {
            // NIO2 can sometimes send window updates depending timing
            skipWindowSizeFrames();

            Assert.assertEquals("3-RST-[11]\n", output.getTrace());
            break;
        }
        case CONNECTION_RESET: {
            // NIO2 can sometimes send window updates depending timing
            skipWindowSizeFrames();

            // This message uses i18n and needs to be used in a regular
            // expression (since we don't know the connection ID). Generate the
            // string as a regular expression and then replace '[' and ']' with
            // the escaped values.
            String limitMessage = sm.getString("http2Parser.headerLimitSize", "\\d++", "3");
            limitMessage = limitMessage.replace("[", "\\[").replace("]", "\\]");
            MatcherAssert.assertThat(output.getTrace(), RegexMatcher.matchesRegex(
                    "0-Goaway-\\[3\\]-\\[11\\]-\\[" + limitMessage + "\\]"));
            break;
        }
        }
    }


    private enum FailureMode {
        NONE,
        STREAM_RESET,
        CONNECTION_RESET,

    }


    private static class RegexMatcher extends TypeSafeMatcher<String> {

        private final String pattern;


        public RegexMatcher(String pattern) {
            this.pattern = pattern;
        }


        @Override
        public void describeTo(Description description) {
            description.appendText("match to regular expression pattern [" + pattern + "]");

        }

        @Override
        protected boolean matchesSafely(String item) {
            return item.matches(pattern);
        }


        public static RegexMatcher matchesRegex(String pattern) {
            return new RegexMatcher(pattern);
        }
    }
}
