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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.net.SocketFactory;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.coyote.http2.HpackDecoder.HeaderEmitter;
import org.apache.coyote.http2.Http2Parser.Input;
import org.apache.coyote.http2.Http2Parser.Output;
import org.apache.tomcat.util.codec.binary.Base64;


/**
 * Tests for compliance with the <a href="https://tools.ietf.org/html/rfc7540">
 * HTTP/2 specification</a>.
 */
public abstract class Http2TestBase extends TomcatBaseTest {

    static final String EMPTY_HTTP2_SETTINGS;

    static {
        byte[] empty = new byte[0];
        EMPTY_HTTP2_SETTINGS = "HTTP2-Settings: " + Base64.encodeBase64String(empty) + "\r\n";
    }

    private Socket s;
    protected Input input;
    protected TestOutput output;
    protected Http2Parser parser;
    protected OutputStream os;


    /**
     * Standard setup. Creates HTTP/2 connection via HTTP upgrade and ensures
     * that the first response is correctly received.
     */
    protected void http2Connect() throws Exception {
        enableHttp2();
        configureAndStartWebApplication();
        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        // Need to read 3 frames (settings, headers and response body)
        parser.readFrame(true);
        parser.readFrame(true);
        parser.readFrame(true);

        Assert.assertEquals("0-Settings-Empty\n" +
                "1-HeadersStart\n" +
                "1-Header-[:status]-[200]\n" +
                "1-HeadersEnd\n" +
                "1-Body-8192\n" +
                "1-EndOfStream", output.getTrace());
        output.clearTrace();
    }


    protected void enableHttp2() {
        Connector connector = getTomcatInstance().getConnector();
        Http2Protocol http2Protocol = new Http2Protocol();
        // Short timeouts for now. May need to increase these for CI systems.
        http2Protocol.setReadTimeout(2000);
        http2Protocol.setKeepAliveTimeout(5000);
        http2Protocol.setWriteTimeout(2000);
        connector.addUpgradeProtocol(http2Protocol);
    }


    protected void configureAndStartWebApplication() throws LifecycleException {
        Tomcat tomcat = getTomcatInstance();

        Context ctxt = tomcat.addContext("", null);
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMapping("/*", "simple");

        tomcat.start();
    }


    protected void openClientConnection() throws IOException {
        // Open a connection
        s = SocketFactory.getDefault().createSocket("localhost", getPort());
        s.setSoTimeout(30000);

        os = s.getOutputStream();
        InputStream is = s.getInputStream();

        input = new TestInput(is);
        output = new TestOutput();
        parser = new Http2Parser("0", input, output);
    }


    protected void doHttpUpgrade() throws IOException {
        doHttpUpgrade("h2c", EMPTY_HTTP2_SETTINGS, true);
    }

    protected void doHttpUpgrade(String upgrade, String settings, boolean validate)
            throws IOException {
        byte[] upgradeRequest = ("GET / HTTP/1.1\r\n" +
                "Host: localhost:" + getPort() + "\r\n" +
                "Connection: Upgrade, HTTP2-Settings\r\n" +
                "Upgrade: " + upgrade + "\r\n" +
                settings +
                "\r\n").getBytes(StandardCharsets.ISO_8859_1);
        os.write(upgradeRequest);
        os.flush();

        if (validate) {
            Assert.assertTrue("Failed to read HTTP Upgrade response",
                    readHttpUpgradeResponse());
        }
    }


    boolean readHttpUpgradeResponse() throws IOException {
        String[] responseHeaders = readHttpResponseHeaders();

        if (responseHeaders.length < 3) {
            return false;
        }
        if (!responseHeaders[0].startsWith("HTTP/1.1 101")) {
            return false;
        }
        // TODO: There may be other headers.
        if (!responseHeaders[1].equals("Connection: Upgrade")) {
            return false;
        }
        if (!responseHeaders[2].startsWith("Upgrade: h2c")) {
            return false;
        }

        return true;
    }


    String[] readHttpResponseHeaders() throws IOException {
        // Only used by test code so safe to keep this just a little larger than
        // we are expecting.
        ByteBuffer data = ByteBuffer.allocate(256);
        byte[] singleByte = new byte[1];
        // Looking for \r\n\r\n
        int seen = 0;
        while (seen < 4) {
            input.fill(true, singleByte);
            switch (seen) {
            case 0:
            case 2: {
                if (singleByte[0] == '\r') {
                    seen++;
                } else {
                    seen = 0;
                }
                break;
            }
            case 1:
            case 3: {
                if (singleByte[0] == '\n') {
                    seen++;
                } else {
                    seen = 0;
                }
                break;
            }
            }
            data.put(singleByte[0]);
        }

        if (seen != 4) {
            throw new IOException("End of headers not found");
        }

        String response = new String(data.array(), data.arrayOffset(),
                data.arrayOffset() + data.position(), StandardCharsets.ISO_8859_1);

        return response.split("\r\n");
    }


    void parseHttp11Response() throws IOException {
        String[] responseHeaders = readHttpResponseHeaders();
        Assert.assertTrue(responseHeaders[0], responseHeaders[0].startsWith("HTTP/1.1 200"));

        // Find the content length (chunked responses not handled)
        for (int i = 1; i < responseHeaders.length; i++) {
            if (responseHeaders[i].toLowerCase(Locale.ENGLISH).startsWith("content-length")) {
                String cl = responseHeaders[i];
                int pos = cl.indexOf(':');
                if (pos == -1) {
                    throw new IOException("Invalid: [" + cl + "]");
                }
                int len = Integer.parseInt(cl.substring(pos + 1).trim());
                byte[] content = new byte[len];
                input.fill(true, content);
                return;
            }
        }
        Assert.fail("No content-length in response");
    }


    private void sendClientPreface() throws IOException {
        os.write(Http2Parser.CLIENT_PREFACE_START);
        os.flush();
    }


    private static class TestInput implements Http2Parser.Input {

        private final InputStream is;


        public TestInput(InputStream is) {
            this.is = is;
        }


        @Override
        public boolean fill(boolean block, byte[] data, int offset, int length) throws IOException {
            // Note: Block is ignored for this test class. Reads always block.
            int off = offset;
            int len = length;
            while (len > 0) {
                int read = is.read(data, off, len);
                if (read == -1) {
                    throw new IOException("End of input stream");
                }
                off += read;
                len -= read;
            }
            return true;
        }
    }


    private static class TestOutput implements Output, HeaderEmitter {

        private StringBuffer trace = new StringBuffer();
        private String lastStreamId = "0";
        private ConnectionSettings remoteSettings = new ConnectionSettings();


        @Override
        public HpackDecoder getHpackDecoder() {
            return new HpackDecoder(remoteSettings.getHeaderTableSize());
        }


        @Override
        public ByteBuffer getInputByteBuffer(int streamId, int payloadSize) {
            lastStreamId = Integer.toString(streamId);
            trace.append(lastStreamId + "-Body-" + payloadSize + "\n");
            return null;
        }


        @Override
        public void endOfStream(int streamId) {
            lastStreamId = Integer.toString(streamId);
            trace.append(lastStreamId + "-EndOfStream");
        }


        @Override
        public HeaderEmitter headersStart(int streamId) {
            lastStreamId = Integer.toString(streamId);
            trace.append(lastStreamId + "-HeadersStart\n");
            return this;
        }

        @Override
        public void reprioritise(int streamId, int parentStreamId, boolean exclusive, int weight) {
            lastStreamId = Integer.toString(streamId);
            trace.append(lastStreamId + "-Reprioritise-[" + parentStreamId + "]-[" + exclusive +
                    "]-[" + weight + "]\n");
        }


        @Override
        public void emitHeader(String name, String value, boolean neverIndex) {
            trace.append(lastStreamId + "-Header-[" + name + "]-[" + value + "]\n");
        }


        @Override
        public void headersEnd(int streamId) {
            trace.append(streamId + "-HeadersEnd\n");
        }


        @Override
        public void settingsEmpty(boolean ack) {
            if (ack) {
                trace.append("0-Settings-Ack\n");
            } else {
                trace.append("0-Settings-Empty\n");
            }
        }


        @Override
        public void setting(int identifier, long value) throws IOException {
            trace.append("0-Settings-[" + identifier + "]-[" + value + "]\n");
            remoteSettings.set(identifier, value);
        }


        @Override
        public void pingReceive(byte[] payload) {
            trace.append("0-Ping-[");
            boolean first = true;
            for (byte b : payload) {
                if (first) {
                    first = false;
                } else {
                    trace.append(',');
                }
                trace.append(b & 0xFF);
            }
            trace.append("]\n");
        }


        @Override
        public void pingAck() {
            trace.append("0-Ping-Ack\n");
        }


        @Override
        public void incrementWindowSize(int streamId, int increment) {
            trace.append(streamId + "-WindowSize-[" + increment + "]\n");
        }


        @Override
        public void swallow(int streamId, int frameType, int flags, int size) {
            trace.append(streamId);
            trace.append(",");
            trace.append(frameType);
            trace.append(",");
            trace.append(flags);
            trace.append(",");
            trace.append(size);
            trace.append("\n");
        }

        public void clearTrace() {
            trace = new StringBuffer();
        }


        public String getTrace() {
            return trace.toString();
        }
    }


    private static class SimpleServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            // Generate content with a simple known format.
            resp.setContentType("application/octet-stream");

            int count = 4 * 1024;
            // Two bytes per entry
            resp.setContentLengthLong(count * 2);

            OutputStream os = resp.getOutputStream();
            byte[] data = new byte[2];
            for (int i = 0; i < count; i++) {
                data[0] = (byte) (i & 0xFF);
                data[1] = (byte) ((i >> 8) & 0xFF);
                os.write(data);
            }
        }
    }
}
