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
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.PushBuilder;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;

/**
 * Unit tests for Section 6.5 of
 * <a href="https://tools.ietf.org/html/rfc7540">RFC 7540</a>.
 */
public class TestHttp2Section_6_6  extends Http2TestBase {


    @Test
    public void testPushPromise() throws Exception {
        http2Connect();

        // Build the push request
        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        List<Header> headers = new ArrayList<>(4);
        headers.add(new Header(":method", "GET"));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":path", "/push"));
        headers.add(new Header(":authority", "localhost:" + getPort()));

        buildGetRequest(frameHeader, headersPayload, null, headers, 3);

        // Send the request
        writeFrame(frameHeader, headersPayload);

        // Read the response
        // push promise
        parser.readFrame();
        // stream 3 response headers
        parser.readFrame();
        // stream 3 response body
        parser.readFrame();
        // stream 2 response headers
        parser.readFrame();
        // stream 2 response body
        parser.readFrame();

        String trace = output.getTrace();

        Assert.assertTrue(trace, trace.contains("3-PushPromise-2"));
        Assert.assertTrue(trace, trace.contains("2-Header-[:status]-[200]"));
        Assert.assertTrue(trace, trace.contains("2-Body-8192"));
        Assert.assertTrue(trace, trace.contains("3-Header-[:status]-[200]"));
        Assert.assertTrue(trace, trace.contains("3-Body-1024"));
    }


    @Override
    protected void configureAndStartWebApplication() throws LifecycleException {
        Tomcat tomcat = getTomcatInstance();

        Context ctxt = tomcat.addContext("", null);

        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");

        Tomcat.addServlet(ctxt, "push", new PushServlet());
        ctxt.addServletMappingDecoded("/push", "push");

        tomcat.start();
    }


    private static class PushServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            PushBuilder pb = req.newPushBuilder();
            pb.path("/simple").push();

            // Generate content with a simple known format.
            resp.setContentType("application/octet-stream");

            int count = 512;

            // Two bytes per entry (1k data)
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
