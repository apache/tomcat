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

import java.io.DataInputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.http2.Http2UpgradeHandler.HeaderFrameBuffers;
import org.apache.tomcat.util.http.Method;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.easymock.EasyMock;

public class TestHttp2UpgradeHandler extends Http2TestBase {

    @Test
    public void testLargeCompressedHeader() throws Exception {
        HeaderWriteTest test = new HeaderWriteTest(useAsyncIO, ConnectionSettingsBase.MAX_MAX_FRAME_SIZE);
        String value = "a".repeat(ConnectionSettingsBase.MAX_MAX_FRAME_SIZE);

        test.write(value);

        // The encoder needs space for the uncompressed value before it attempts Huffman encoding. The resulting
        // payload fits in one frame even though the workspace must be larger than the maximum frame payload.
        Assert.assertTrue(test.buffers.maxWorkspaceSize > ConnectionSettingsBase.MAX_MAX_FRAME_SIZE);
        MimeHeaders decoded = new MimeHeaders();
        HpackDecoder decoder = new HpackDecoder();
        decoder.setMaxHeaderSize(-1);
        decoder.setHeaderEmitter(new HpackDecoder.HeaderEmitter() {
            @Override
            public void emitHeader(String name, String headerValue) {
                decoded.addValue(name).setString(headerValue);
            }

            @Override
            public void setHeaderException(StreamException streamException) {
                Assert.fail(streamException.getMessage());
            }

            @Override
            public void validateHeaders() {
                // NO-OP
            }
        });
        for (ByteBuffer payload : test.buffers.payloads) {
            decoder.decode(payload);
        }
        Assert.assertEquals(value, decoded.getHeader("x"));
    }


    @Test
    public void testHeaderPayloadAtMaximumSize() throws Exception {
        int limit = ConnectionSettingsBase.MAX_MAX_FRAME_SIZE;
        HeaderWriteTest test = new HeaderWriteTest(useAsyncIO, limit);
        // 'X' does not benefit from Huffman encoding. The literal representation uses one byte for its type, two
        // bytes for the name and five bytes for the value length at this size.
        test.write("X".repeat(limit - 8));
        Assert.assertEquals(limit, test.buffers.maxPayloadSize);
    }


    @Test
    public void testHeaderPayloadExceedsMaximumSize() throws Exception {
        int limit = ConnectionSettingsBase.MAX_MAX_FRAME_SIZE;
        HeaderWriteTest test = new HeaderWriteTest(useAsyncIO, limit);
        Assert.assertThrows(IllegalStateException.class, () -> test.write("X".repeat(limit)));
        test.assertConnectionClosedWithInternalError();
        // Encoding must reach the larger workspace so the actual payload, rather than the uncompressed estimate,
        // is checked. The recorder also verifies that no oversized payload reaches endFrame().
        Assert.assertTrue(test.buffers.maxWorkspaceSize > limit);
    }


    @Test
    public void testHeaderPayloadAtPeerMaximumSize() throws Exception {
        int limit = ConnectionSettingsBase.MIN_MAX_FRAME_SIZE;
        HeaderWriteTest test = new HeaderWriteTest(useAsyncIO, limit);
        // At this size the value length occupies three bytes, giving six bytes of overhead in total.
        test.write("X".repeat(limit - 6));
        Assert.assertEquals(limit, test.buffers.maxPayloadSize);
    }


    @Test
    public void testHeaderPayloadExceedsPeerMaximumSize() throws Exception {
        HeaderWriteTest test = new HeaderWriteTest(useAsyncIO, ConnectionSettingsBase.MIN_MAX_FRAME_SIZE);
        Assert.assertThrows(IllegalStateException.class,
                () -> test.write("X".repeat(ConnectionSettingsBase.MIN_MAX_FRAME_SIZE)));
        test.assertConnectionClosedWithInternalError();
    }


    @Test
    public void testHeaderBlockExceedsPeerMaximumAfterWorkspaceExpansion() throws Exception {
        int limit = ConnectionSettingsBase.MIN_MAX_FRAME_SIZE;
        HeaderWriteTest test = new HeaderWriteTest(useAsyncIO, limit);
        MimeHeaders headers = new MimeHeaders();
        /*
         * The first field needs more workspace than the peer's frame size limit (its uncompressed length is reserved
         * before Huffman encoding) so the workspace is expanded beyond that limit. Once expanded, the encoder packs
         * the second field into the same frame. The two fields compress to more than the peer's limit in total so the
         * block is rejected rather than being split across frames.
         */
        headers.addValue("x").setString("a".repeat(20000));
        headers.addValue("y").setString("X".repeat(limit / 2));

        Assert.assertThrows(IllegalStateException.class, () -> test.write(headers));
        test.assertConnectionClosedWithInternalError();
        Assert.assertTrue("The workspace was not expanded beyond the peer's frame size limit",
                test.buffers.maxWorkspaceSize > limit);
        // The block must never have been completed
        Assert.assertFalse("A complete header block was written", test.buffers.endOfHeadersWritten);
    }


    @Test
    public void testHeaderPayloadTooLargeClosesConnection() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMapping("/simple", "simple");
        Tomcat.addServlet(ctxt, "oversized", new OversizedHeaderServlet());
        ctxt.addServletMapping("/oversized", "oversized");

        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildGetRequest(frameHeader, headersPayload, null, 3, "/oversized");
        writeFrame(frameHeader, headersPayload);

        /*
         * The response header can't be sent in a frame of the size the client permits. With synchronous I/O the
         * client may see the start of the header block but it must never see it completed. The server must close the
         * connection with a GOAWAY rather than leave the connection running with an unfinished header block.
         */
        DataInputStream in = new DataInputStream(s.getInputStream());
        try {
            boolean goaway = false;
            while (!goaway) {
                parser.readFrame();
                String trace = output.getTrace();
                Assert.assertFalse(trace, trace.contains("3-HeadersEnd"));
                goaway = trace.startsWith("0-Goaway-[");
                if (goaway) {
                    Assert.assertTrue(trace, trace.startsWith(
                            "0-Goaway-[3]-[" + Http2Error.INTERNAL_ERROR.getCode() + "]-["));
                }
                output.clearTrace();
            }
        } catch (ConnectionException ce) {
            /*
             * With synchronous I/O the client has received the start of the header block when the GOAWAY arrives. The
             * test parser (correctly) rejects any frame other than a CONTINUATION at that point, having read only the
             * frame header. Read the GOAWAY payload directly to check the last stream ID and the error code.
             */
            Assert.assertTrue(ce.getMessage(), ce.getMessage().contains("headers in progress for stream [3]"));
            Assert.assertEquals(3, in.readInt());
            Assert.assertEquals(Http2Error.INTERNAL_ERROR.getCode(), in.readInt() & 0xFFFFFFFFL);
        }

        // The server must then close the TCP connection. Reading the end of the stream must not time out.
        byte[] discard = new byte[1024];
        try {
            while (in.read(discard) != -1) {
                // Discard any remaining data (the rest of the GOAWAY frame)
            }
        } catch (SocketTimeoutException ste) {
            Assert.fail("The connection was not closed after the GOAWAY");
        }
    }


    @Test
    public void testIndexedHeaderAfterFrameSizeReduction() throws Exception {
        int tableSize = ConnectionSettingsBase.MAX_HEADER_TABLE_SIZE;
        HeaderWriteTest test = new HeaderWriteTest(useAsyncIO, tableSize);
        test.handler.getRemoteSettings().set(Setting.HEADER_TABLE_SIZE, tableSize);
        String value = "X".repeat(40000);

        // Populate the dynamic table while the peer permits a frame large enough for the literal field.
        test.write(value);
        Assert.assertTrue(test.buffers.maxPayloadSize > ConnectionSettingsBase.MIN_MAX_FRAME_SIZE);

        // Reducing the frame limit does not evict dynamic table entries. The encoder still reserves space using
        // the field's uncompressed lengths, even though its indexed representation occupies only one byte.
        int frameSize = ConnectionSettingsBase.MIN_MAX_FRAME_SIZE;
        test.handler.getRemoteSettings().set(Setting.MAX_FRAME_SIZE, frameSize);
        test.write(value);

        Assert.assertTrue(test.buffers.maxWorkspaceSize > 2 * frameSize);
        Assert.assertEquals(tableSize, test.buffers.maxWorkspaceSize);
        ByteBuffer indexedPayload = test.buffers.payloads.get(test.buffers.payloads.size() - 1);
        Assert.assertEquals(1, indexedPayload.remaining());
        Assert.assertEquals(0x80 | (Hpack.STATIC_TABLE_LENGTH + 1), indexedPayload.get() & 0xFF);
    }


    @Test
    public void testHeaderWorkspaceExpansionIsCapped() throws Exception {
        HeaderWriteTest test = new HeaderWriteTest(useAsyncIO, ConnectionSettingsBase.MAX_MAX_FRAME_SIZE);
        HeaderFrameBuffers buffers = test.handler.getHeaderFrameBuffers(ConnectionSettingsBase.MAX_MAX_FRAME_SIZE);
        buffers.startFrame();
        buffers.expandPayload();
        Assert.assertEquals(2 * ConnectionSettingsBase.MAX_MAX_FRAME_SIZE, buffers.getPayload().capacity());
        Assert.assertThrows(IllegalStateException.class, buffers::expandPayload);
    }


    private static class HeaderWriteTest {
        private final Http2UpgradeHandler handler;
        private final Stream stream;
        private RecordingHeaderFrameBuffers buffers;
        private volatile Http2Exception closeException;

        HeaderWriteTest(boolean async, int payloadLimit) throws ConnectionException {
            SocketWrapperBase<?> socket = EasyMock.createNiceMock(SocketWrapperBase.class);
            ServletConnection connection = EasyMock.createNiceMock(ServletConnection.class);
            EasyMock.expect(socket.getServletConnection("h2c", "")).andStubReturn(connection);
            EasyMock.expect(connection.getConnectionId()).andStubReturn("0");
            EasyMock.replay(socket, connection);

            Http2Protocol protocol = new Http2Protocol();
            if (async) {
                handler = new Http2AsyncUpgradeHandler(protocol, null, null, socket) {
                    @Override
                    protected HeaderFrameBuffers getHeaderFrameBuffers(int initialPayloadSize) {
                        buffers = new RecordingHeaderFrameBuffers(super.getHeaderFrameBuffers(initialPayloadSize),
                                getRemoteSettings().getMaxFrameSize());
                        return buffers;
                    }

                    @Override
                    void closeConnection(Http2Exception ce) {
                        closeException = ce;
                    }
                };
            } else {
                handler = new Http2UpgradeHandler(protocol, null, null, socket) {
                    @Override
                    protected HeaderFrameBuffers getHeaderFrameBuffers(int initialPayloadSize) {
                        buffers = new RecordingHeaderFrameBuffers(super.getHeaderFrameBuffers(initialPayloadSize),
                                getRemoteSettings().getMaxFrameSize());
                        return buffers;
                    }

                    @Override
                    void closeConnection(Http2Exception ce) {
                        closeException = ce;
                    }
                };
            }
            handler.getRemoteSettings().set(Setting.MAX_FRAME_SIZE, payloadLimit);
            stream = new Stream(Integer.valueOf(3), handler);
            stream.state.receivedStartOfHeaders();
        }

        void write(String value) throws IOException {
            MimeHeaders headers = new MimeHeaders();
            headers.addValue("x").setString(value);
            write(headers);
        }

        void assertConnectionClosedWithInternalError() {
            Assert.assertNotNull("The connection was not closed", closeException);
            Assert.assertEquals(Http2Error.INTERNAL_ERROR, closeException.getError());
        }

        void write(MimeHeaders headers) throws IOException {
            handler.doWriteHeaders(stream, 0, headers, false, Constants.DEFAULT_HEADERS_FRAME_SIZE);
        }
    }


    private static class OversizedHeaderServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            // Does not compress to fit in a frame of the default maximum size (16384 bytes)
            resp.setHeader("x-oversized", "X".repeat(3 * ConnectionSettingsBase.MIN_MAX_FRAME_SIZE));
            resp.setContentType("text/plain");
            resp.getWriter().print("OK");
        }
    }


    private static class RecordingHeaderFrameBuffers implements HeaderFrameBuffers {
        private final HeaderFrameBuffers delegate;
        private final int payloadLimit;
        private final List<ByteBuffer> payloads = new ArrayList<>();
        private int maxWorkspaceSize;
        private int maxPayloadSize;
        private boolean endOfHeadersWritten;

        RecordingHeaderFrameBuffers(HeaderFrameBuffers delegate, int payloadLimit) {
            this.delegate = delegate;
            this.payloadLimit = payloadLimit;
        }

        @Override
        public void startFrame() {
            delegate.startFrame();
        }

        @Override
        public void endFrame() throws IOException {
            ByteBuffer payload = getPayload();
            Assert.assertTrue("Oversized payload passed to the writer", payload.remaining() <= payloadLimit);
            Assert.assertEquals(payload.remaining(), ByteUtil.getThreeBytes(getHeader(), 0));
            maxPayloadSize = Math.max(maxPayloadSize, payload.remaining());
            endOfHeadersWritten |= (getHeader()[4] & Http2UpgradeHandler.FLAG_END_OF_HEADERS) != 0;
            payloads.add(payload.asReadOnlyBuffer());
            delegate.endFrame();
        }

        @Override
        public void endHeaders() throws IOException {
            delegate.endHeaders();
        }

        @Override
        public byte[] getHeader() {
            return delegate.getHeader();
        }

        @Override
        public ByteBuffer getPayload() {
            ByteBuffer payload = delegate.getPayload();
            maxWorkspaceSize = Math.max(maxWorkspaceSize, payload.capacity());
            return payload;
        }

        @Override
        public void expandPayload() {
            delegate.expandPayload();
        }
    }


    // https://bz.apache.org/bugzilla/show_bug.cgi?id=60970
    @Test
    public void testLargeHeader() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMapping("/simple", "simple");
        Tomcat.addServlet(ctxt, "large", new LargeHeaderServlet());
        ctxt.addServletMapping("/large", "large");

        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildGetRequest(frameHeader, headersPayload, null, 3, "/large");
        writeFrame(frameHeader, headersPayload);

        // Headers
        parser.readFrame();
        parser.readFrame();
        // Body
        parser.readFrame();

        Assert.assertEquals("3-HeadersStart\n" + "3-Header-[:status]-[200]\n" + "3-Header-[x-ignore]-[...]\n" +
                "3-Header-[content-type]-[text/plain;charset=UTF-8]\n" + "3-Header-[content-length]-[2]\n" +
                "3-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n" + "3-HeadersEnd\n" + "3-Body-2\n" +
                "3-EndOfStream\n", output.getTrace());
    }


    @Test
    public void testUpgradeWithRequestBodyNoSavePostSize() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();
        tomcat.getConnector().setProperty("maxSavePostSize", "0");
        configureAndStartWebApplication();

        openClientConnection();

        byte[] upgradeRequest =
                ("GET /simple HTTP/1.1\r\n" + "Host: localhost:" + getPort() + "\r\n" + "Content-Length: 18\r\n" +
                        "Connection: Upgrade,HTTP2-Settings\r\n" + "Upgrade: h2c\r\n" + EMPTY_HTTP2_SETTINGS_HEADER +
                        "\r\n" + "Small request body").getBytes(StandardCharsets.ISO_8859_1);
        os.write(upgradeRequest);
        os.flush();

        Assert.assertTrue("Failed to read HTTP Upgrade response", readHttpUpgradeResponse());

        sendClientPreface();
        validateHttp2InitialResponse();
    }


    @Test
    public void testUpgradeWithRequestBodyGet() throws Exception {
        doTestUpgradeWithRequestBody(false, false, false);
    }


    @Test
    public void testUpgradeWithRequestBodyGetTooBig() throws Exception {
        doTestUpgradeWithRequestBody(false, false, true);
    }


    @Test
    public void testUpgradeWithRequestBodyPost() throws Exception {
        doTestUpgradeWithRequestBody(true, false, false);
    }


    @Test
    public void testUpgradeWithRequestBodyPostTooBig() throws Exception {
        doTestUpgradeWithRequestBody(true, false, true);
    }


    @Test
    public void testUpgradeWithRequestBodyGetReader() throws Exception {
        doTestUpgradeWithRequestBody(false, true, false);
    }


    @Test
    public void testUpgradeWithRequestBodyGetReaderTooBig() throws Exception {
        doTestUpgradeWithRequestBody(false, true, true);
    }


    @Test
    public void testUpgradeWithRequestBodyPostReader() throws Exception {
        doTestUpgradeWithRequestBody(true, true, false);
    }


    @Test
    public void testUpgradeWithRequestBodyPostReaderTooBig() throws Exception {
        doTestUpgradeWithRequestBody(true, true, true);
    }


    private void doTestUpgradeWithRequestBody(boolean usePost, boolean useReader, boolean tooBig) throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "ReadRequestBodyServlet", new ReadRequestBodyServlet());
        ctxt.addServletMapping("/", "ReadRequestBodyServlet");

        if (tooBig) {
            // Reduce maxSavePostSize rather than use a larger request body
            tomcat.getConnector().setProperty("maxSavePostSize", "10");
        }
        tomcat.start();

        openClientConnection();

        byte[] upgradeRequest = ((usePost ? Method.POST : Method.GET) + " /" + (useReader ? "?useReader=true " : " ") +
                "HTTP/1.1\r\n" + "Host: localhost:" + getPort() + "\r\n" + "Content-Length: 18\r\n" +
                "Connection: Upgrade,HTTP2-Settings\r\n" + "Upgrade: h2c\r\n" + EMPTY_HTTP2_SETTINGS_HEADER + "\r\n" +
                "Small request body").getBytes(StandardCharsets.ISO_8859_1);
        os.write(upgradeRequest);
        os.flush();

        if (tooBig) {
            String[] responseHeaders = readHttpResponseHeaders();
            Assert.assertNotNull(responseHeaders);
            Assert.assertNotEquals(0, responseHeaders.length);
            Assert.assertEquals("HTTP/1.1 413 ", responseHeaders[0]);
        } else {
            Assert.assertTrue("Failed to read HTTP Upgrade response", readHttpUpgradeResponse());

            sendClientPreface();

            // - 101 response acts as acknowledgement of the HTTP2-Settings header
            // Need to read 5 frames
            // - settings (server settings - must be first)
            // - settings ack (for the settings frame in the client preface)
            // - ping
            // - headers (for response)
            // - data (for response body)
            parser.readFrame();
            parser.readFrame();
            parser.readFrame();
            parser.readFrame();
            parser.readFrame();

            Assert.assertEquals("0-Settings-[3]-[200]\n" + "0-Settings-End\n" + "0-Settings-Ack\n" +
                    "0-Ping-[0,0,0,0,0,0,0,1]\n" + "1-HeadersStart\n" + "1-Header-[:status]-[200]\n" +
                    "1-Header-[content-type]-[text/plain;charset=UTF-8]\n" + "1-Header-[content-length]-[39]\n" +
                    "1-Header-[date]-[" + DEFAULT_DATE + "]\n" + "1-HeadersEnd\n" + "1-Body-39\n" + "1-EndOfStream\n",
                    output.getTrace());
        }
    }


    @Test
    public void testUpgradeAdvertisedInitialWindowSize() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMapping("/simple", "simple");

        tomcat.start();

        openClientConnection();

        // Advertise an initial window size that is smaller than the default
        int initialWindowSize = 4096;
        byte[] settingsPayload = new byte[6];
        ByteUtil.setTwoBytes(settingsPayload, 0, Setting.INITIAL_WINDOW_SIZE.getId());
        ByteUtil.setFourBytes(settingsPayload, 2, initialWindowSize);
        String settingsHeader = "HTTP2-Settings: " + Base64.getUrlEncoder().encodeToString(settingsPayload) + "\r\n";

        doHttpUpgrade(DEFAULT_CONNECTION_HEADER_VALUE, "h2c", settingsHeader, true);

        sendClientPreface();

        // - 101 response acts as acknowledgement of the HTTP2-Settings header
        // Need to read 5 frames
        // - settings (server settings - must be first)
        // - settings ack (for the settings frame in the client preface)
        // - ping
        // - headers (for response)
        // - data (for response body, limited to the advertised initial window)
        parser.readFrame();
        parser.readFrame();
        parser.readFrame();
        parser.readFrame();
        parser.readFrame();

        Assert.assertEquals("0-Settings-[3]-[200]\n" + "0-Settings-End\n" + "0-Settings-Ack\n" +
                "0-Ping-[0,0,0,0,0,0,0,1]\n" + "1-HeadersStart\n" + "1-Header-[:status]-[200]\n" +
                "1-Header-[content-type]-[application/octet-stream]\n" + "1-Header-[content-length]-[" +
                SimpleServlet.CONTENT_LENGTH + "]\n" + "1-Header-[date]-[" + DEFAULT_DATE + "]\n" + "1-HeadersEnd\n" +
                "1-Body-" + initialWindowSize + "\n", output.getTrace());
        output.clearTrace();

        // The advertised stream window is now exhausted. Expand it and the
        // rest of the response should follow.
        sendWindowUpdate(1, initialWindowSize);
        parser.readFrame();

        Assert.assertEquals("1-Body-" + (SimpleServlet.CONTENT_LENGTH - initialWindowSize) + "\n" + "1-EndOfStream\n",
                output.getTrace());
    }


    @Test
    public void testActiveConnectionCountAndClientTimeout() throws Exception {

        enableHttp2(2, false, 10000, 10000, 4000, 2000, 2000);

        Tomcat tomcat = getTomcatInstance();

        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMapping("/simple", "simple");

        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse(2);

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        byte[] dataFrameHeader = new byte[9];
        ByteBuffer dataFramePayload = ByteBuffer.allocate(128);

        // Should be able to make more than 2 requests even if they timeout
        // since they should be removed from active connections once they
        // timeout
        for (int stream = 3; stream < 8; stream += 2) {
            // Don't write the body. Allow the read to timeout.
            buildPostRequest(frameHeader, headersPayload, false, dataFrameHeader, dataFramePayload, null, stream);
            writeFrame(frameHeader, headersPayload);

            // 400 response (triggered by IOException trying to read body that never arrived)
            parser.readFrame();
            Assert.assertTrue(output.getTrace(),
                    output.getTrace().startsWith(stream + "-HeadersStart\n" + stream + "-Header-[:status]-[400]\n"));
            output.clearTrace();

            // reset frame
            parser.readFrame();
            Assert.assertEquals(stream + "-RST-[11]\n", output.getTrace());
            output.clearTrace();

            // Prepare buffers for re-use
            headersPayload.clear();
            dataFramePayload.clear();
        }
    }
}
