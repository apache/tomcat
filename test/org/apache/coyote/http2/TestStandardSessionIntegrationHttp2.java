/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.coyote.http2;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.http.Method;
import org.apache.tomcat.util.http.MimeHeaders;

public class TestStandardSessionIntegrationHttp2 extends Http2TestBase {

    @Test
    public void testSessionIsNew() throws Exception {

        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        Context ctxt = getProgrammaticRootContext();
        // Need simple servlet for the HTTP upgrade
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");
        // Servlet for this test
        Tomcat.addServlet(ctxt, "session", new SessionServlet());
        ctxt.addServletMappingDecoded("/session", "session");
        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        output.setTraceBody(true);

        // Make first request
        // Generate headers
        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        MimeHeaders headers = new MimeHeaders();
        headers.addValue(":method").setString(Method.GET);
        headers.addValue(":scheme").setString("http");
        headers.addValue(":path").setString("/session");
        headers.addValue(":authority").setString("localhost:" + getPort());

        hpackEncoder.encode(headers, headersPayload);
        headersPayload.flip();

        ByteUtil.setThreeBytes(headersFrameHeader, 0, headersPayload.limit());
        headersFrameHeader[3] = FrameType.HEADERS.getIdByte();
        // Flags. end of headers (0x04). end of stream (0x01)
        headersFrameHeader[4] = 0x05;
        // Stream id
        ByteUtil.set31Bits(headersFrameHeader, 5, 3);

        writeFrame(headersFrameHeader, headersPayload);

        // Read headers from first request
        parser.readFrame();
        // extract the session ID
        String trace = output.getTrace();
        int index = trace.indexOf("JSESSIONID=");
        String sessionID = trace.substring(index + 11, index + 11 + 32);
        output.clearTrace();

        // Make second request - can just 'update' first request
        headersPayload.clear();
        headers.addValue("cookie").setString("JSESSIONID=" + sessionID);
        hpackEncoder.encode(headers, headersPayload);
        headersPayload.flip();

        ByteUtil.setThreeBytes(headersFrameHeader, 0, headersPayload.limit());
        // Stream id
        ByteUtil.set31Bits(headersFrameHeader, 5, 5);

        writeFrame(headersFrameHeader, headersPayload);

        // Request 2 headers
        parser.readFrame();
        // body (request 1 or 2)
        parser.readFrame();
        // body (request 1 or 2)
        parser.readFrame();

        trace = output.getTrace();
        Assert.assertTrue(trace, trace.contains("3-Body-OK"));
        Assert.assertTrue(trace, trace.contains("5-Body-OK"));
    }


    private static class SessionServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            PrintWriter pw = resp.getWriter();

            boolean pass = true;

            HttpSession s = req.getSession(false);
            CountDownLatch latch;
            if (s == null) {
                s = req.getSession(true);
                if (!s.isNew()) {
                    // This is first request so session must be new.
                    pass = false;
                }
                latch = new CountDownLatch(1);
                s.setAttribute("latch", latch);
                // Return the session ID to the client
                resp.flushBuffer();
                // Wait for the second request to this session
                while (latch.getCount() > 0) {
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        // Ignore. Only proceed one the latch has counted down.
                    }
                }
                // Second request has accessed session. Client has joined the session.
                if (s.isNew()) {
                    // Session should not be new once client has joined it.
                    pass = false;
                }
            } else {
                if (s.isNew()) {
                    // This is second (or later) request. Client has joined the session. Sessions should not be new.
                    pass = false;
                }
                // Release the first request if it is still waiting.
                latch = (CountDownLatch) s.getAttribute("latch");
                latch.countDown();
            }

            if (pass) {
                pw.print("OK");
            } else {
                pw.print("FAIL");
            }
        }
    }
}
