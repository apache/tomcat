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
 * Unit tests for Section 8.2.1 of <a href="https://tools.ietf.org/html/rfc9113">RFC 9113</a>. <br>
 * The order of the tests in this class is determined by the order of the requirements in the RFC.
 */
public class TestHttp2Section_8_2_1 extends Http2TestBase {

    /*
     * A response header field that cannot be sent in a valid HTTP/2 message must not break the stream, the connection
     * or the HPACK state shared by later responses. As for HTTP/1.1, a value with a character that is not in the range
     * 0 to 255 removes the header field and other invalid values are corrected.
     */
    @Test
    public void testInvalidResponseHeaders() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMapping("/simple", "simple");
        Tomcat.addServlet(ctxt, "invalid", new InvalidHeadersServlet());
        ctxt.addServletMapping("/invalid", "invalid");

        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        // The second request checks that the HPACK dynamic table is still consistent
        for (int streamId = 3; streamId < 7; streamId += 2) {
            byte[] frameHeader = new byte[9];
            ByteBuffer headersPayload = ByteBuffer.allocate(128);
            buildGetRequest(frameHeader, headersPayload, null, streamId, "/invalid");
            writeFrame(frameHeader, headersPayload);

            // Headers
            parser.readFrame();
            // Body
            parser.readFrame();

            String id = Integer.toString(streamId);
            String trace = output.getTrace();
            Assert.assertTrue(trace, trace.contains(id + "-Header-[x-latin1]-[caf\u00e9]\n"));
            Assert.assertTrue(trace, trace.contains(id + "-Header-[x-lf]-[a b]\n"));
            Assert.assertTrue(trace, trace.contains(id + "-Header-[x-cr]-[a b]\n"));
            Assert.assertTrue(trace, trace.contains(id + "-Header-[x-nul]-[a b]\n"));
            Assert.assertTrue(trace, trace.contains(id + "-Header-[x-space]-[padded]\n"));
            Assert.assertTrue(trace, trace.contains(id + "-Header-[x-last]-[ok]\n"));
            Assert.assertFalse(trace, trace.contains("x-long-unicode"));
            Assert.assertFalse(trace, trace.contains("x-short-unicode"));
            Assert.assertFalse(trace, trace.contains("x-bad"));
            Assert.assertTrue(trace, trace.contains(id + "-Body-2\n"));
            Assert.assertTrue(trace, trace.contains(id + "-EndOfStream\n"));
            output.clearTrace();
        }
    }


    private static class InvalidHeadersServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            // The default ISO-8859-1 response encoding cannot represent the euro sign
            resp.setContentType("text/plain");
            // Longer than five characters so the value is Huffman encoded
            resp.setHeader("X-Long-Unicode", "abcdefg\u20ac");
            resp.setHeader("X-Short-Unicode", "\u20acx");
            resp.setHeader("X Bad", "value");
            resp.setHeader("X-Latin1", "caf\u00e9");
            resp.setHeader("X-Lf", "a\nb");
            resp.setHeader("X-Cr", "a\rb");
            resp.setHeader("X-Nul", "a\u0000b");
            resp.setHeader("X-Space", " \tpadded \t");
            resp.setHeader("X-Last", "ok");
            resp.getWriter().print("OK");
        }
    }
}
