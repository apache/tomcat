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

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

/**
 * Unit tests for Section 8.2.2 of <a href="https://tools.ietf.org/html/rfc9113">RFC 9113</a>. <br>
 * The order of the tests in this class is determined by the order of the requirements in the RFC.
 */
public class TestHttp2Section_8_2_2 extends Http2TestBase {

    /*
     * An endpoint MUST NOT generate an HTTP/2 message containing connection-specific header fields. Applications that
     * set them (or proxy them from an HTTP/1.x origin) must not cause the response to be malformed.
     */
    @Test
    public void testConnectionSpecificResponseHeadersRemoved() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMapping("/simple", "simple");
        Tomcat.addServlet(ctxt, "connection", new ConnectionHeadersServlet());
        ctxt.addServletMapping("/connection", "connection");

        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildGetRequest(frameHeader, headersPayload, null, 3, "/connection");
        writeFrame(frameHeader, headersPayload);

        // Headers
        parser.readFrame();
        // Body
        parser.readFrame();

        String trace = output.getTrace();
        Assert.assertTrue(trace, trace.contains("3-Header-[x-other]-[value]\n"));
        Assert.assertFalse(trace, trace.contains("3-Header-[connection]-"));
        Assert.assertFalse(trace, trace.contains("3-Header-[proxy-connection]-"));
        Assert.assertFalse(trace, trace.contains("3-Header-[keep-alive]-"));
        Assert.assertFalse(trace, trace.contains("3-Header-[transfer-encoding]-"));
        Assert.assertFalse(trace, trace.contains("3-Header-[upgrade]-"));
    }


    private static class ConnectionHeadersServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            resp.setHeader("Connection", "close");
            resp.setHeader("Proxy-Connection", "keep-alive");
            resp.setHeader("Keep-Alive", "timeout=5");
            resp.setHeader("Transfer-Encoding", "chunked");
            resp.setHeader("Upgrade", "websocket");
            resp.setHeader("X-Other", "value");
            resp.getWriter().print("OK");
        }
    }
}
