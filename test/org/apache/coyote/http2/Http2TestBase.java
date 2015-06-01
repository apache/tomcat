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
import org.apache.coyote.http2.Http2Parser.Input;
import org.apache.tomcat.util.codec.binary.Base64;


/**
 * Tests for compliance with the <a href="https://tools.ietf.org/html/rfc7540">
 * HTTP/2 specification</a>.
 */
public abstract class Http2TestBase extends TomcatBaseTest {

    private static final String HTTP2_SETTINGS;

    static {
        byte[] empty = new byte[0];
        HTTP2_SETTINGS = Base64.encodeBase64String(empty);
    }

    private Socket s;
    protected Input input;
    protected Http2Parser parser;
    protected OutputStream os;


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
        parser = new Http2Parser(input);
    }


    protected void doHttpUpgrade(String upgrade, boolean validate) throws IOException {
        byte[] upgradeRequest = ("GET / HTTP/1.1\r\n" +
                "Host: localhost:" + getPort() + "\r\n" +
                "Connection: Upgrade, HTTP2-Settings\r\n" +
                "Upgrade: " + upgrade + "\r\n" +
                "HTTP2-Settings: "+ HTTP2_SETTINGS + "\r\n" +
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
            input.fill(singleByte, true);
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


    private static class TestInput implements Http2Parser.Input {

        private final InputStream is;


        public TestInput(InputStream is) {
            this.is = is;
        }


        @Override
        public boolean fill(byte[] data, boolean block) throws IOException {
            // Note: Block is ignored for this test class. Reads always block.
            int off = 0;
            int len = data.length;
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


    private static class SimpleServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            // Generate content with a simple known format.
            resp.setContentType("application/octet-stream");

            int count = 16 * 1024;
            // Two bytes per entry
            resp.setContentLengthLong(count * 2);

            OutputStream os = resp.getOutputStream();
            byte[] data = new byte[2];
            // 1024 * 16 * 2 bytes = 32k of content.
            for (int i = 0; i < count; i++) {
                data[0] = (byte) (i & 0xFF);
                data[1] = (byte) ((i >> 8) & 0xFF);
                os.write(data);
            }
        }
    }
}
