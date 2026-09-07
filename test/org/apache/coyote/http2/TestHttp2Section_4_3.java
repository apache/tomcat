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

import org.apache.tomcat.util.http.Method;

/**
 * Unit tests for Section 4.3 of <a href="https://tools.ietf.org/html/rfc7540">RFC 7540</a>. <br>
 * The order of tests in this class is aligned with the order of the requirements in the RFC.
 */
public class TestHttp2Section_4_3 extends Http2TestBase {

    private static final String MARKER_HEADER_NAME = "x-marker";
    private static final String MARKER_HEADER_VALUE = "marker-value";

    @Test
    public void testHeaderDecodingError() throws Exception {
        // HTTP2 upgrade
        http2Connect();

        // Build the simple request
        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildSimpleGetRequest(frameHeader, headersPayload, null, 3);

        // Try and corrupt the headerPayload
        headersPayload.put(0, (byte) (headersPayload.get(0) + 128));

        // Process the request
        writeFrame(frameHeader, headersPayload);

        handleGoAwayResponse(3, Http2Error.COMPRESSION_ERROR);
    }


    /*
     * HTTP/2 field validation rejects invalid characters in field names and field values. The field name check has two
     * distinct ways to fail (not a token character; a valid but upper case token character) and the field value check
     * has three distinct ways to fail depending on the position of the invalid character (first, middle, last). Each
     * of those five triggers has tests below for both the non-Huffman and Huffman code paths. HpackEncoder uses Huffman
     * encoding for strings longer than five characters, provided that the encoded form is not longer than the original.
     */

    @Test
    public void testHeaderDecodingErrorFieldNameInvalidCharacter() throws Exception {
        // ':' is a separator so is not a valid token character.
        doTestHeaderDecodingErrorKeepsTableInSync("x:y", "ok");
    }


    @Test
    public void testHeaderDecodingErrorFieldNameUpperCase() throws Exception {
        // Upper case letters are valid token characters but are not permitted in HTTP/2 header field names.
        doTestHeaderDecodingErrorKeepsTableInSync("Xy", "ok");
    }


    @Test
    public void testHeaderDecodingErrorFieldValueLeadingInvalidCharacter() throws Exception {
        // The first character of a field value must be a field-vchar. Space is not.
        doTestHeaderDecodingErrorKeepsTableInSync("x-bad", " x");
    }


    @Test
    public void testHeaderDecodingErrorFieldValueTrailingInvalidCharacter() throws Exception {
        // The last character of a field value must be a field-vchar. Space is not.
        doTestHeaderDecodingErrorKeepsTableInSync("x-bad", "x ");
    }


    @Test
    public void testHeaderDecodingErrorFieldValueMiddleInvalidCharacter() throws Exception {
        // A character in the middle of a field value must be field-content. A control character is not.
        doTestHeaderDecodingErrorKeepsTableInSync("x-bad", "a\u0001b");
    }


    @Test
    public void testHeaderDecodingErrorHuffmanFieldNameTrailingSpace() throws Exception {
        // Space is not a valid token character in a field name.
        doTestHeaderDecodingErrorKeepsTableInSync("x-bad ", "ok");
    }


    @Test
    public void testHeaderDecodingErrorHuffmanFieldNameInvalidCharacter() throws Exception {
        // ':' is a separator so is not a valid token character.
        doTestHeaderDecodingErrorKeepsTableInSync("x-te:st", "ok");
    }


    @Test
    public void testHeaderDecodingErrorHuffmanFieldNameUpperCase() throws Exception {
        // Upper case letters are valid token characters but are not permitted in HTTP/2 header field names.
        doTestHeaderDecodingErrorKeepsTableInSync("x-Te-st", "ok");
    }


    @Test
    public void testHeaderDecodingErrorHuffmanFieldValueLeadingInvalidCharacter() throws Exception {
        // The first character of a field value must be a field-vchar. Space is not.
        doTestHeaderDecodingErrorKeepsTableInSync("x-bad", " x-value");
    }


    @Test
    public void testHeaderDecodingErrorHuffmanFieldValueTrailingInvalidCharacter() throws Exception {
        // The last character of a field value must be a field-vchar. Space is not.
        doTestHeaderDecodingErrorKeepsTableInSync("x-bad", "x-value ");
    }


    @Test
    public void testHeaderDecodingErrorHuffmanFieldValueMiddleInvalidCharacter() throws Exception {
        // A character in the middle of a field value must be field-content. A control character is not.
        doTestHeaderDecodingErrorKeepsTableInSync("x-bad", "x-value-test-\u0001-value-test");
    }


    /**
     * A header field ({@code badHeaderName} / {@code badHeaderValue}) that {@link Stream#emitHeader(String, String)}
     * rejects is a stream error (RFC 9113, section 8.2.1), not a connection error, so the connection must remain open
     * and usable. However, the invalid field is followed, in the same header block, by another header
     * ({@link #MARKER_HEADER_NAME}) that adds an entry to the HPACK dynamic table. The decoder must still process that
     * later header (i.e. keep going after detecting the invalid field) so its dynamic table stays in sync with the
     * encoder used by the client. This is verified with a second, otherwise unrelated, request that references that
     * dynamic table entry.
     *
     * @param badHeaderName  The (possibly invalid) name to use for the invalid header
     * @param badHeaderValue The (possibly invalid) value to use for the invalid header
     */
    private void doTestHeaderDecodingErrorKeepsTableInSync(String badHeaderName, String badHeaderValue)
            throws Exception {
        // HTTP2 upgrade
        http2Connect();

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        List<Header> headers = new ArrayList<>(6);
        headers.add(new Header(":method", Method.GET));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":path", "/simple"));
        headers.add(new Header(":authority", "localhost:" + getPort()));
        headers.add(new Header(badHeaderName, badHeaderValue));
        // Valid header that follows the invalid one in the same header
        // block. It must still be added to the dynamic table.
        headers.add(new Header(MARKER_HEADER_NAME, MARKER_HEADER_VALUE));

        buildGetRequest(frameHeader, headersPayload, null, headers, 3);
        writeFrame(frameHeader, headersPayload);

        // The stream must be reset. The connection must remain open.
        parser.readFrame();
        Assert.assertEquals("Stream (not connection) error expected for an invalid header field",
                "3-RST-[" + Http2Error.PROTOCOL_ERROR.getCode() + "]\n", output.getTrace());
        output.clearTrace();

        // A second, unrelated request on a new stream. Because it uses the
        // same name/value pair as the marker header above, the test's
        // HpackEncoder (correctly simulating a real HTTP/2 client) will
        // reference its dynamic table entry rather than re-sending it as a
        // literal. This will only decode correctly on the server if the
        // HPACK decoder kept processing the earlier header block far enough
        // to add that entry to its own dynamic table, despite the stream
        // being reset.
        byte[] frameHeader2 = new byte[9];
        ByteBuffer headersPayload2 = ByteBuffer.allocate(128);

        List<Header> headers2 = new ArrayList<>(5);
        headers2.add(new Header(":method", Method.GET));
        headers2.add(new Header(":scheme", "http"));
        headers2.add(new Header(":path", "/simple"));
        headers2.add(new Header(":authority", "localhost:" + getPort()));
        headers2.add(new Header(MARKER_HEADER_NAME, MARKER_HEADER_VALUE));

        buildGetRequest(frameHeader2, headersPayload2, null, headers2, 5);
        writeFrame(frameHeader2, headersPayload2);

        parser.readFrame();
        Assert.assertFalse(output.getTrace(), output.getTrace().contains("RST"));
        parser.readFrame();

        Assert.assertEquals("HPACK dynamic table should still be in sync with the client",
                getSimpleResponseTrace(5), output.getTrace());
    }


    @Test
    public void testHeaderContinuationContiguous() throws Exception {
        // HTTP2 upgrade
        http2Connect();

        // Part 1
        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildSimpleGetRequestPart1(frameHeader, headersPayload, 3);
        writeFrame(frameHeader, headersPayload);

        // Part 2
        headersPayload.clear();
        buildSimpleGetRequestPart2(frameHeader, headersPayload, 3);
        writeFrame(frameHeader, headersPayload);

        // headers, body
        parser.readFrame();
        Assert.assertFalse(output.getTrace(), output.getTrace().contains("RST"));
        parser.readFrame();

        Assert.assertEquals(getSimpleResponseTrace(3), output.getTrace());
    }


    @Test
    public void testHeaderContinuationNonContiguous() throws Exception {
        // HTTP2 upgrade
        http2Connect();

        // Part 1
        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildSimpleGetRequestPart1(frameHeader, headersPayload, 3);
        writeFrame(frameHeader, headersPayload);

        sendPing();

        handleGoAwayResponse(3, Http2Error.COMPRESSION_ERROR);
    }
}
