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
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.ContinueResponseTiming;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.apache.tomcat.util.http.Method;
import org.apache.tomcat.util.http.MimeHeaders;

/**
 * Unit tests for Section 8.1 of <a href="https://tools.ietf.org/html/rfc7540">RFC 7540</a>. <br>
 * The order of tests in this class is aligned with the order of the examples in the RFC.
 */
public class TestHttp2Section_8_1 extends Http2TestBase {

    @Test
    public void testPostWithTrailerHeaders() throws Exception {
        doTestPostWithTrailerHeaders(true);
    }


    @Test
    public void testPostWithTrailerHeadersBlocked() throws Exception {
        doTestPostWithTrailerHeaders(false);
    }


    private void doTestPostWithTrailerHeaders(boolean allowTrailerHeader) throws Exception {
        http2Connect();
        if (allowTrailerHeader) {
            ((AbstractHttp11Protocol<?>) http2Protocol.getHttp11Protocol())
                    .setAllowedTrailerHeaders(TRAILER_HEADER_NAME);
        }

        // Disable overhead protection for window update as it breaks some tests
        http2Protocol.setOverheadWindowUpdateThreshold(0);

        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        byte[] dataFrameHeader = new byte[9];
        ByteBuffer dataPayload = ByteBuffer.allocate(256);
        byte[] trailerFrameHeader = new byte[9];
        ByteBuffer trailerPayload = ByteBuffer.allocate(256);

        buildPostRequest(headersFrameHeader, headersPayload, false, dataFrameHeader, dataPayload, null, true, 3);

        // Write the headers
        writeFrame(headersFrameHeader, headersPayload);
        // Body
        writeFrame(dataFrameHeader, dataPayload);

        sendSimpleGetRequest(5);

        // Trailers
        buildTrailerHeaders(trailerFrameHeader, trailerPayload, 3);
        writeFrame(trailerFrameHeader, trailerPayload);

        parser.readFrame();
        parser.readFrame();
        parser.readFrame();
        parser.readFrame();
        parser.readFrame();
        parser.readFrame();

        String len;
        if (allowTrailerHeader) {
            len = Integer.toString(256 + TRAILER_HEADER_VALUE.length());
        } else {
            len = "256";
        }

        /*
         * There is no guaranteed order between stream 3 and stream 5. It all depends on server side timing. Also need
         * to ensure no unexpected frames are received.
         */
        String stream0WindowSize = "0-WindowSize-[256]\n";
        String stream3WindowSize = "3-WindowSize-[256]\n";
        String stream3Headers = "3-HeadersStart\n" + "3-Header-[:status]-[200]\n" +
                "3-Header-[content-length]-[" + len + "]\n" + "3-Header-[date]-[" + DEFAULT_DATE + "]\n" +
                "3-HeadersEnd\n";
        String stream3Body = "3-Body-" + len + "\n" + "3-EndOfStream\n";
        String stream5Headers = "5-HeadersStart\n" + "5-Header-[:status]-[200]\n" +
                "5-Header-[content-type]-[application/octet-stream]\n" + "5-Header-[content-length]-[8192]\n" +
                "5-Header-[date]-[" + DEFAULT_DATE + "]\n" + "5-HeadersEnd\n";
        String stream5Body = "5-Body-8192\n" + "5-EndOfStream\n";

        String trace = output.getTrace();
        System.out.println(trace);

        Assert.assertTrue(trace, trace.contains(stream0WindowSize));
        Assert.assertTrue(trace, trace.contains(stream3WindowSize));
        Assert.assertTrue(trace, trace.contains(stream3Headers));
        Assert.assertTrue(trace, trace.contains(stream3Body));
        Assert.assertTrue(trace, trace.contains(stream5Headers));
        Assert.assertTrue(trace, trace.contains(stream5Body));
        Assert.assertEquals("Unexpected data in trace - " + trace, stream0WindowSize.length() +
                stream3WindowSize.length() + stream3Headers.length() + stream3Body.length() + stream5Headers.length() +
                stream5Body.length(), trace.length());
    }


    @Test
    public void testSendAckWithDefaultPolicy() throws Exception {
        testSendAck();
    }


    @Test
    public void testSendAckWithImmediatelyPolicy() throws Exception {
        setContinueHandlingResponsePolicy(ContinueResponseTiming.IMMEDIATELY);
        testSendAck();
    }


    @Test
    public void testSendAckWithOnRequestBodyReadPolicy() throws Exception {
        setContinueHandlingResponsePolicy(ContinueResponseTiming.ON_REQUEST_BODY_READ);
        testSendAck();
    }


    public void setContinueHandlingResponsePolicy(ContinueResponseTiming policy) throws Exception {
        final Tomcat tomcat = getTomcatInstance();

        final Connector connector = tomcat.getConnector();
        connector.setProperty("continueHandlingResponsePolicy", policy.toString());
    }


    @Test
    public void testSendAck() throws Exception {
        // makes a request that expects a 100 Continue response and verifies
        // that the 100 Continue response is received. This does not check
        // that the correct ContinueHandlingResponsePolicy was followed, just
        // that a 100 Continue response is received. The unit tests for
        // Request verify that the various policies are implemented.
        http2Connect();

        // Disable overhead protection for window update as it breaks some tests
        http2Protocol.setOverheadWindowUpdateThreshold(0);

        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        byte[] dataFrameHeader = new byte[9];
        ByteBuffer dataPayload = ByteBuffer.allocate(256);

        buildPostRequest(headersFrameHeader, headersPayload, true, null, -1, "/simple", dataFrameHeader, dataPayload,
                null, false, 3);

        // Write the headers
        writeFrame(headersFrameHeader, headersPayload);

        parser.readFrame();

        Assert.assertEquals("3-HeadersStart\n" + "3-Header-[:status]-[100]\n" + "3-HeadersEnd\n", output.getTrace());
        output.clearTrace();

        // Write the body
        writeFrame(dataFrameHeader, dataPayload);

        parser.readFrame();
        parser.readFrame();
        parser.readFrame();
        parser.readFrame();

        Assert.assertEquals("0-WindowSize-[256]\n" + "3-WindowSize-[256]\n" + "3-HeadersStart\n" +
                "3-Header-[:status]-[200]\n" + "3-Header-[content-length]-[256]\n" + "3-Header-[date]-[" +
                DEFAULT_DATE + "]\n" + "3-HeadersEnd\n" + "3-Body-256\n" + "3-EndOfStream\n", output.getTrace());
    }


    @Test
    public void testUndefinedPseudoHeader() throws Exception {
        http2Connect();

        List<Header> headers = new ArrayList<>(5);
        headers.add(new Header(":method", Method.GET));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":path", "/simple"));
        headers.add(new Header(":authority", "localhost:" + getPort()));
        headers.add(new Header(":foo", "bar"));

        doInvalidPseudoHeaderTest(headers);
    }


    @Test
    public void testInvalidPseudoHeader() throws Exception {
        http2Connect();

        List<Header> headers = new ArrayList<>(5);
        headers.add(new Header(":method", Method.GET));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":path", "/simple"));
        headers.add(new Header(":authority", "localhost:" + getPort()));
        headers.add(new Header(":status", "200"));

        doInvalidPseudoHeaderTest(headers);
    }


    @Test
    public void testPseudoHeaderOrder() throws Exception {
        // Need to do this in two frames because HPACK encoder automatically
        // re-orders fields

        http2Connect();

        List<Header> headers = new ArrayList<>(4);
        headers.add(new Header(":method", Method.GET));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":path", "/simple"));
        headers.add(new Header("x-test", "test"));

        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        buildSimpleGetRequestPart1(headersFrameHeader, headersPayload, headers, 3);

        writeFrame(headersFrameHeader, headersPayload);

        headers.clear();
        headers.add(new Header(":authority", "localhost:" + getPort()));
        headersPayload.clear();

        buildSimpleGetRequestPart2(headersFrameHeader, headersPayload, headers, 3);

        writeFrame(headersFrameHeader, headersPayload);


        parser.readFrame();

        Assert.assertEquals("3-RST-[1]\n", output.getTrace());
    }


    @Test
    public void testHostHeaderValid() throws Exception {
        http2Connect();

        List<Header> headers = new ArrayList<>(4);
        headers.add(new Header(":method", Method.GET));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":path", "/simple"));
        headers.add(new Header("host", "localhost:" + getPort()));

        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        buildGetRequest(headersFrameHeader, headersPayload, null, headers, 3);

        writeFrame(headersFrameHeader, headersPayload);

        parser.readFrame();

        String trace = output.getTrace();
        Assert.assertTrue(trace, trace.contains("3-Header-[:status]-[200]"));
    }


    @Test
    public void testHostHeaderInvalid() throws Exception {
        http2Connect();

        List<Header> headers = new ArrayList<>(4);
        headers.add(new Header(":method", Method.GET));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":path", "/simple"));
        headers.add(new Header("host", "local!host:" + getPort()));

        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        buildGetRequest(headersFrameHeader, headersPayload, null, headers, 3);

        writeFrame(headersFrameHeader, headersPayload);

        parser.readFrame();

        String trace = output.getTrace();
        Assert.assertTrue(trace, trace.contains("3-Header-[:status]-[400]"));
    }


    @Test
    public void testAuthorityHeaderInvalid() throws Exception {
        http2Connect();

        List<Header> headers = new ArrayList<>(4);
        headers.add(new Header(":method", Method.GET));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":path", "/simple"));
        headers.add(new Header(":authority", "local!host:" + getPort()));

        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        buildGetRequest(headersFrameHeader, headersPayload, null, headers, 3);

        writeFrame(headersFrameHeader, headersPayload);

        parser.readFrame();

        String trace = output.getTrace();
        Assert.assertTrue(trace, trace.contains("3-Header-[:status]-[400]"));
    }


    @Test
    public void testHostHeaderDuplicate() throws Exception {
        http2Connect();

        List<Header> headers = new ArrayList<>(4);
        headers.add(new Header(":method", Method.GET));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":path", "/simple"));
        headers.add(new Header("host", "localhost:" + getPort()));
        headers.add(new Header("host", "localhost:" + getPort()));

        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        buildGetRequest(headersFrameHeader, headersPayload, null, headers, 3);

        writeFrame(headersFrameHeader, headersPayload);

        parser.readFrame();

        String trace = output.getTrace();
        Assert.assertTrue(trace, trace.contains("3-RST-[1]"));
    }


    @Test
    public void testHostHeaderConsistent() throws Exception {
        http2Connect();

        List<Header> headers = new ArrayList<>(4);
        headers.add(new Header(":method", Method.GET));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":authority", "localhost:" + getPort()));
        headers.add(new Header(":path", "/simple"));
        headers.add(new Header("host", "localhost:" + getPort()));

        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        buildGetRequest(headersFrameHeader, headersPayload, null, headers, 3);

        writeFrame(headersFrameHeader, headersPayload);

        parser.readFrame();

        String trace = output.getTrace();
        Assert.assertTrue(trace, trace.contains("3-Header-[:status]-[200]"));
    }


    @Test
    public void testHostHeaderConsistentNoPort() throws Exception {
        http2Connect();

        List<Header> headers = new ArrayList<>(4);
        headers.add(new Header(":method", Method.GET));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":authority", "localhost"));
        headers.add(new Header(":path", "/simple"));
        headers.add(new Header("host", "localhost"));

        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        buildGetRequest(headersFrameHeader, headersPayload, null, headers, 3);

        writeFrame(headersFrameHeader, headersPayload);

        parser.readFrame();

        String trace = output.getTrace();
        Assert.assertTrue(trace, trace.contains("3-Header-[:status]-[200]"));
    }


    @Test
    public void testHostHeaderInconsistent01() throws Exception {
        http2Connect();

        doTestHostHeaderInconsistent("localhost:" + getPort(), "otherhost:" + getPort());
    }


    @Test
    public void testHostHeaderInconsistent02() throws Exception {
        http2Connect();

        doTestHostHeaderInconsistent("localhost", "otherhost");
    }


    @Test
    public void testHostHeaderInconsistent03() throws Exception {
        http2Connect();

        doTestHostHeaderInconsistent("localhost:" + getPort(), "localhost");
    }


    @Test
    public void testHostHeaderInconsistent04() throws Exception {
        http2Connect();

        doTestHostHeaderInconsistent("localhost", "localhost:" + getPort());
    }


    @Test
    public void testHostHeaderInconsistent05() throws Exception {
        http2Connect();

        doTestHostHeaderInconsistent("localhost:" + getPort(), "otherhost:" + (getPort() + 1));
    }


    private void doTestHostHeaderInconsistent(String authority, String host) throws Exception {
        List<Header> headers = new ArrayList<>(4);
        headers.add(new Header(":method", Method.GET));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":authority", authority));
        headers.add(new Header(":path", "/simple"));
        headers.add(new Header("host", host));

        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        buildGetRequest(headersFrameHeader, headersPayload, null, headers, 3);

        writeFrame(headersFrameHeader, headersPayload);

        parser.readFrame();

        String trace = output.getTrace();
        Assert.assertTrue(trace, trace.contains("3-RST-[1]"));
    }


    private void doInvalidPseudoHeaderTest(List<Header> headers) throws Exception {
        doInvalidPseudoHeaderTest(headers, "3-RST-[1]\n");
    }

    private void doInvalidPseudoHeaderTest(List<Header> headers, String expected) throws Exception {

        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        buildGetRequest(headersFrameHeader, headersPayload, null, headers, 3);

        // Write the headers
        writeFrame(headersFrameHeader, headersPayload);

        parser.readFrame();

        String trace = output.getTrace();
        if (trace.length() > expected.length()) {
            trace = trace.substring(0, expected.length());
        }
        Assert.assertEquals(output.getTrace(), expected, trace);
    }


    @Test
    public void testSchemeHeaderValid() throws Exception {
        http2Connect();

        List<Header> headers = new ArrayList<>(4);
        headers.add(new Header(":method", Method.GET));
        headers.add(new Header(":scheme", "abcd"));
        headers.add(new Header(":authority", "localhost:" + getPort()));
        headers.add(new Header(":path", "/simple"));
        headers.add(new Header("host", "localhost:" + getPort()));

        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        buildGetRequest(headersFrameHeader, headersPayload, null, headers, 3);

        writeFrame(headersFrameHeader, headersPayload);

        parser.readFrame();

        String trace = output.getTrace();
        Assert.assertTrue(trace, trace.contains("3-Header-[:status]-[200]"));
    }


    @Test
    public void testSchemeHeaderInvalid01() throws Exception {
        http2Connect();

        List<Header> headers = new ArrayList<>(4);
        headers.add(new Header(":method", Method.GET));
        headers.add(new Header(":scheme", "ab!cd"));
        headers.add(new Header(":authority", "localhost:" + getPort()));
        headers.add(new Header(":path", "/simple"));
        headers.add(new Header("host", "localhost:" + getPort()));

        doInvalidPseudoHeaderTest(headers, "3-HeadersStart\n3-Header-[:status]-[400]\n");
    }


    @Test
    public void testSchemeHeaderInvalid02() throws Exception {
        http2Connect();

        List<Header> headers = new ArrayList<>(4);
        headers.add(new Header(":method", Method.GET));
        headers.add(new Header(":scheme", ""));
        headers.add(new Header(":authority", "localhost:" + getPort()));
        headers.add(new Header(":path", "/simple"));
        headers.add(new Header("host", "localhost:" + getPort()));

        doInvalidPseudoHeaderTest(headers, "3-HeadersStart\n3-Header-[:status]-[400]\n");
    }


    @Test
    public void testSchemeHeaderMissing() throws Exception {
        http2Connect();

        List<Header> headers = new ArrayList<>(4);
        headers.add(new Header(":method", Method.GET));
        headers.add(new Header(":authority", "localhost:" + getPort()));
        headers.add(new Header(":path", "/simple"));
        headers.add(new Header("host", "localhost:" + getPort()));

        doInvalidPseudoHeaderTest(headers, "3-RST-[1]\n");
    }


    @Test
    public void testMethodHeaderInvalid() throws Exception {
        http2Connect();

        List<Header> headers = new ArrayList<>(4);
        headers.add(new Header(":method", "GET\r\n"));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":path", "/simple"));
        headers.add(new Header("host", "localhost:" + getPort()));

        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        buildGetRequest(headersFrameHeader, headersPayload, null, headers, 3);

        writeFrame(headersFrameHeader, headersPayload);

        parser.readFrame();

        String trace = output.getTrace();
        Assert.assertTrue(trace, trace.contains("3-RST-[1]"));
    }


    @Test
    public void testMalformedTrailers() throws Exception {
        http2Connect();

        ((AbstractHttp11Protocol<?>) http2Protocol.getHttp11Protocol())
                .setAllowedTrailerHeaders(TRAILER_HEADER_NAME);

        // Disable overhead protection for window update as it breaks some tests
        http2Protocol.setOverheadWindowUpdateThreshold(0);

        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        byte[] dataFrameHeader = new byte[9];
        ByteBuffer dataPayload = ByteBuffer.allocate(256);
        byte[] trailerFrameHeader = new byte[9];
        ByteBuffer trailerPayload = ByteBuffer.allocate(256);

        buildPostRequest(headersFrameHeader, headersPayload, false, null, dataPayload.capacity(), "/simple",
                dataFrameHeader,dataPayload, null, true, 3);

        // Write the headers
        writeFrame(headersFrameHeader, headersPayload);
        // Body
        writeFrame(dataFrameHeader, dataPayload);

        // Trailers
        buildTrailerHeaders(trailerFrameHeader, trailerPayload, 3);
        // Remove the end of stream flag (0x01) but leave end of headers (0x04)
        trailerFrameHeader[4] = 0x04;
        writeFrame(trailerFrameHeader, trailerPayload);

        // Expect 2 window updates and a reset due to a protocol error. Order may vary.
        boolean skip = true;
        while (skip) {
            parser.readFrame();
            if (output.getTrace().contains("WindowSize")) {
                // Ignore the window updates
                output.clearTrace();
            } else {
                skip = false;
            }
        }
        Assert.assertTrue(output.getTrace(), output.getTrace().contains("3-RST-[1]"));

        // Ensure connection is still valid
        sendSimpleGetRequest(5);
        // Read headers
        // In async mode, may see multiple resets for Stream 3 and/or further window updates
        skip = true;
        while (skip) {
            parser.readFrame();
            if (output.getTrace().startsWith("3-RST")) {
                // Ignore additional resets for stream 3
                output.clearTrace();
            } else if (output.getTrace().contains("WindowSize")) {
                // Ignore the window updates
                output.clearTrace();
            } else {
                skip = false;
            }
        }
        // Read body
        parser.readFrame();
        Assert.assertEquals(getSimpleResponseTrace(5), output.getTrace());
    }


    @Test
    public void testPayloadTooSmall() throws Exception {
        doTest1kPayload(2048);
    }


    @Test
    public void testPayloadTooBig() throws Exception {
        doTest1kPayload(512);
    }


    private void doTest1kPayload(int contentLength) throws Exception {
        http2Connect();

        // Set up a POST request
        // Generate headers
        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        MimeHeaders headers = new MimeHeaders();
        headers.addValue(":method").setString(Method.POST);
        headers.addValue(":scheme").setString("http");
        headers.addValue(":path").setString("/simple");
        headers.addValue(":authority").setString("localhost:" + getPort());
        headers.addValue("content-length").setLong(contentLength);
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
        byte[] dataFrameHeader = new byte[9];
        ByteBuffer dataPayload = ByteBuffer.allocate(1024);
        while (dataPayload.hasRemaining()) {
            dataPayload.put((byte) 'x');
        }
        dataPayload.flip();

        // Size
        ByteUtil.setThreeBytes(dataFrameHeader, 0, dataPayload.limit());
        // Flags - end of stream 0x01
        dataFrameHeader[4] = 0x01;
        // Stream ID
        ByteUtil.set31Bits(dataFrameHeader, 5, 3);

        writeFrame(dataFrameHeader, dataPayload);

        // Expect 2 window updates and a reset due to a protocol error - any order
        parser.readFrame();
        parser.readFrame();
        parser.readFrame();
        Assert.assertTrue(output.getTrace(), output.getTrace().contains("3-RST-[1]"));
        output.clearTrace();

        // Ensure connection is still valid
        sendSimpleGetRequest(5);
        // Read headers
        parser.readFrame();
        // Read body
        parser.readFrame();
        Assert.assertEquals(getSimpleResponseTrace(5), output.getTrace());
    }
}
