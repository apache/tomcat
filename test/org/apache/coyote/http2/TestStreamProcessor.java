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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.ResponseFacade;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.compat.JrePlatform;
import org.apache.tomcat.util.http.FastHttpDateFormat;

public class TestStreamProcessor extends Http2TestBase {

    @Test
    public void testAsyncComplete() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        // Map the async servlet to /simple so we can re-use the HTTP/2 handling
        // logic from the super class.
        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");
        Wrapper w = Tomcat.addServlet(ctxt, "async", new AsyncComplete());
        w.setAsyncSupported(true);
        ctxt.addServletMappingDecoded("/async", "async");

        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildGetRequest(frameHeader, headersPayload, null, 3, "/async");
        writeFrame(frameHeader, headersPayload);

        readSimpleGetResponse();
        // Flush before startAsync means body is written in two packets so an
        // additional frame needs to be read
        parser.readFrame();

        Assert.assertEquals("3-HeadersStart\n" + "3-Header-[:status]-[200]\n" +
                "3-Header-[content-type]-[text/plain;charset=UTF-8]\n" +
                "3-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n" + "3-HeadersEnd\n" + "3-Body-17\n" + "3-Body-8\n" +
                "3-EndOfStream\n", output.getTrace());
    }


    @Test
    public void testAsyncDispatch() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        // Map the async servlet to /simple so we can re-use the HTTP/2 handling
        // logic from the super class.
        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");
        Wrapper w = Tomcat.addServlet(ctxt, "async", new AsyncDispatch());
        w.setAsyncSupported(true);
        ctxt.addServletMappingDecoded("/async", "async");

        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildGetRequest(frameHeader, headersPayload, null, 3, "/async");
        writeFrame(frameHeader, headersPayload);

        readSimpleGetResponse();
        Assert.assertEquals(getSimpleResponseTrace(3), output.getTrace());
    }


    @Test
    public void testPrepareHeaders() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        Context ctxt = tomcat.addWebapp(null, "", appDir.getAbsolutePath());

        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");

        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        List<Header> headers = new ArrayList<>(3);
        headers.add(new Header(":method", "GET"));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":path", "/index.html"));
        headers.add(new Header(":authority", "localhost:" + getPort()));
        headers.add(new Header("if-modified-since", FastHttpDateFormat.getCurrentDate()));

        buildGetRequest(frameHeader, headersPayload, null, headers, 3);

        writeFrame(frameHeader, headersPayload);

        parser.readFrame();

        StringBuilder expected = new StringBuilder();
        expected.append("3-HeadersStart\n");
        expected.append("3-Header-[:status]-[304]\n");
        // Different line-endings -> different files size -> different weak eTag
        if (JrePlatform.IS_WINDOWS) {
            expected.append("3-Header-[etag]-[W/\"957-1447269522000\"]\n");
        } else {
            expected.append("3-Header-[etag]-[W/\"934-1447269522000\"]\n");
        }
        expected.append("3-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n");
        expected.append("3-HeadersEnd\n");
        expected.append("3-EndOfStream\n");

        Assert.assertEquals(expected.toString(), output.getTrace());
    }


    @Test
    public void testPrepareHeadersNoContent() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        Context ctxt = tomcat.addWebapp(null, "", appDir.getAbsolutePath());

        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");
        Tomcat.addServlet(ctxt, "noContent", new NoContentServlet());
        ctxt.addServletMappingDecoded("/noContent", "noContent");


        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        List<Header> headers = new ArrayList<>(3);
        headers.add(new Header(":method", "GET"));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":path", "/noContent"));
        headers.add(new Header(":authority", "localhost:" + getPort()));

        buildGetRequest(frameHeader, headersPayload, null, headers, 3);

        writeFrame(frameHeader, headersPayload);

        parser.readFrame();

        StringBuilder expected = new StringBuilder();
        expected.append("3-HeadersStart\n");
        expected.append("3-Header-[:status]-[204]\n");
        expected.append("3-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n");
        expected.append("3-HeadersEnd\n");
        expected.append("3-EndOfStream\n");

        Assert.assertEquals(expected.toString(), output.getTrace());
    }


    @Test
    public void testValidateRequestMethod() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        Context ctxt = tomcat.addWebapp(null, "", appDir.getAbsolutePath());

        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");

        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        List<Header> headers = new ArrayList<>(4);
        headers.add(new Header(":method", "not,token"));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":path", "/index.html"));
        headers.add(new Header(":authority", "localhost:" + getPort()));

        buildGetRequest(frameHeader, headersPayload, null, headers, 3);

        writeFrame(frameHeader, headersPayload);

        parser.readFrame();

        StringBuilder expected = new StringBuilder();
        expected.append("3-HeadersStart\n");
        expected.append("3-Header-[:status]-[400]\n");
        expected.append("3-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n");
        expected.append("3-HeadersEnd\n");

        Assert.assertEquals(expected.toString(), output.getTrace());
    }


    @Test
    public void testValidateRequestHeaderName() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        Context ctxt = tomcat.addWebapp(null, "", appDir.getAbsolutePath());

        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");

        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        List<Header> headers = new ArrayList<>(5);
        headers.add(new Header(":method", "GET"));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":path", "/index.html"));
        headers.add(new Header(":authority", "localhost:" + getPort()));
        headers.add(new Header("not token", "value"));

        buildGetRequest(frameHeader, headersPayload, null, headers, 3);

        writeFrame(frameHeader, headersPayload);

        parser.readFrame();

        StringBuilder expected = new StringBuilder();
        expected.append("3-HeadersStart\n");
        expected.append("3-Header-[:status]-[400]\n");
        expected.append("3-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n");
        expected.append("3-HeadersEnd\n");

        Assert.assertEquals(expected.toString(), output.getTrace());
    }


    @Test
    public void testValidateRequestURI() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        Context ctxt = tomcat.addWebapp(null, "", appDir.getAbsolutePath());

        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");

        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        List<Header> headers = new ArrayList<>(4);
        headers.add(new Header(":method", "GET"));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":path", "/index^html"));
        headers.add(new Header(":authority", "localhost:" + getPort()));

        buildGetRequest(frameHeader, headersPayload, null, headers, 3);

        writeFrame(frameHeader, headersPayload);

        parser.readFrame();

        StringBuilder expected = new StringBuilder();
        expected.append("3-HeadersStart\n");
        expected.append("3-Header-[:status]-[400]\n");
        expected.append("3-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n");
        expected.append("3-HeadersEnd\n");

        Assert.assertEquals(expected.toString(), output.getTrace());
    }


    @Test
    public void testValidateRequestQueryString() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        Context ctxt = tomcat.addWebapp(null, "", appDir.getAbsolutePath());

        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");

        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        List<Header> headers = new ArrayList<>(4);
        headers.add(new Header(":method", "GET"));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":path", "/index.html?foo=[]"));
        headers.add(new Header(":authority", "localhost:" + getPort()));

        buildGetRequest(frameHeader, headersPayload, null, headers, 3);

        writeFrame(frameHeader, headersPayload);

        parser.readFrame();

        StringBuilder expected = new StringBuilder();
        expected.append("3-HeadersStart\n");
        expected.append("3-Header-[:status]-[400]\n");
        expected.append("3-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n");
        expected.append("3-HeadersEnd\n");

        Assert.assertEquals(expected.toString(), output.getTrace());
    }


    @Test
    public void testValidateRequestQueryStringRelaxed() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        Context ctxt = tomcat.addWebapp(null, "", appDir.getAbsolutePath());

        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");

        tomcat.getConnector().setProperty("relaxedQueryChars", "[]");

        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        List<Header> headers = new ArrayList<>(4);
        headers.add(new Header(":method", "GET"));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":path", "/index.html?foo=[]"));
        headers.add(new Header(":authority", "localhost:" + getPort()));

        buildGetRequest(frameHeader, headersPayload, null, headers, 3);

        writeFrame(frameHeader, headersPayload);

        parser.readFrame();

        StringBuilder expected = new StringBuilder();
        expected.append("3-HeadersStart\n");
        expected.append("3-Header-[:status]-[200]\n");

        // The status code is the most important thing to test
        Assert.assertTrue(output.getTrace().startsWith(expected.toString()));
    }


    private static final class AsyncComplete extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            response.setContentType("text/plain");
            response.setCharacterEncoding("UTF-8");

            PrintWriter pw = response.getWriter();
            pw.print("Enter-");

            final AsyncContext asyncContext = request.startAsync(request, response);
            pw.print("StartAsync-");
            pw.flush();

            asyncContext.start(new Runnable() {

                @Override
                public void run() {
                    try {
                        asyncContext.getResponse().getWriter().print("Complete");
                        asyncContext.complete();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }


    private static final class AsyncDispatch extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            final AsyncContext asyncContext = request.startAsync(request, response);
            asyncContext.start(new Runnable() {

                @Override
                public void run() {
                    try {
                        asyncContext.dispatch("/simple");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }


    @Test
    public void testCompression() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");
        Tomcat.addServlet(ctxt, "compression", new CompressionServlet());
        ctxt.addServletMappingDecoded("/compression", "compression");

        // Enable compression
        Connector connector = tomcat.getConnector();
        Assert.assertTrue(connector.setProperty("compression", "on"));

        tomcat.start();

        enableHttp2();
        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();


        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        List<Header> headers = new ArrayList<>(3);
        headers.add(new Header(":method", "GET"));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":path", "/compression"));
        headers.add(new Header(":authority", "localhost:" + getPort()));
        headers.add(new Header("accept-encoding", "gzip"));

        buildGetRequest(frameHeader, headersPayload, null, headers, 3);

        writeFrame(frameHeader, headersPayload);

        readSimpleGetResponse();

        Assert.assertEquals("3-HeadersStart\n" + "3-Header-[:status]-[200]\n" + "3-Header-[vary]-[accept-encoding]\n" +
                "3-Header-[content-encoding]-[gzip]\n" + "3-Header-[content-type]-[text/plain;charset=UTF-8]\n" +
                "3-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n" + "3-HeadersEnd\n" + "3-Body-97\n" +
                "3-EndOfStream\n", output.getTrace());
    }


    private static class CompressionServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            // Generate content type that is compressible
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");

            // Make ir large enough to trigger compression
            int count = 64 * 1024;

            // One bytes per entry
            resp.setContentLengthLong(count);

            OutputStream os = resp.getOutputStream();
            for (int i = 0; i < count; i++) {
                os.write('X');
            }
        }
    }


    @Test
    public void testConnect() throws Exception {
        http2Connect();

        List<Header> headers = new ArrayList<>(4);
        headers.add(new Header(":method", "CONNECT"));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":authority", "example.local"));

        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        buildGetRequest(headersFrameHeader, headersPayload, null, headers, 3);

        writeFrame(headersFrameHeader, headersPayload);

        parser.readFrame();

        String trace = output.getTrace();
        Assert.assertTrue(trace, trace.contains("3-Header-[:status]-[501]"));
    }


    @Test
    public void testEarlyHints() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");
        Tomcat.addServlet(ctxt, "ehs", new EarlyHintsServlet());
        ctxt.addServletMappingDecoded("/ehs", "ehs");
        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        // Disable overhead protection for window update as it breaks some tests
        http2Protocol.setOverheadWindowUpdateThreshold(0);

        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        buildGetRequest(headersFrameHeader, headersPayload, null, 3, "/ehs");

        // Write the headers
        writeFrame(headersFrameHeader, headersPayload);

        parser.readFrame();

        Assert.assertEquals("3-HeadersStart\n" + "3-Header-[:status]-[103]\n" +
                "3-Header-[link]-[</style.css>; rel=preload; as=style]\n" + "3-HeadersEnd\n", output.getTrace());
        output.clearTrace();

        parser.readFrame();
        parser.readFrame();

        Assert.assertEquals("3-HeadersStart\n" + "3-Header-[:status]-[200]\n" +
                "3-Header-[link]-[</style.css>; rel=preload; as=style]\n" +
                "3-Header-[content-type]-[text/plain;charset=UTF-8]\n" +
                "3-Header-[content-length]-[2]\n" +
                "3-Header-[date]-[" + DEFAULT_DATE + "]\n" + "3-HeadersEnd\n" + "3-Body-2\n" + "3-EndOfStream\n",
                output.getTrace());
    }


    private static class EarlyHintsServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.addHeader("Link", "</style.css>; rel=preload; as=style");

            ((ResponseFacade) resp).sendEarlyHints();

            resp.setCharacterEncoding(StandardCharsets.UTF_8);
            resp.setContentType("text/plain");

            resp.getWriter().write("OK");
        }
    }


    @Test
    public void testServerHeaderDefault() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");
        Tomcat.addServlet(ctxt, "server", new ServerHeaderServlet());
        ctxt.addServletMappingDecoded("/server", "server");
        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        // Disable overhead protection for window update as it breaks some tests
        http2Protocol.setOverheadWindowUpdateThreshold(0);

        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        buildGetRequest(headersFrameHeader, headersPayload, null, 3, "/server");

        // Write the headers
        writeFrame(headersFrameHeader, headersPayload);

        parser.readFrame();
        parser.readFrame();

        Assert.assertEquals("3-HeadersStart\n" + "3-Header-[:status]-[200]\n" +
                "3-Header-[server]-[TestServerApp]\n" +
                "3-Header-[content-type]-[text/plain;charset=UTF-8]\n" +
                "3-Header-[content-length]-[2]\n" +
                "3-Header-[date]-[" + DEFAULT_DATE + "]\n" + "3-HeadersEnd\n" + "3-Body-2\n" + "3-EndOfStream\n",
                output.getTrace());
    }


    @Test
    public void testServerHeaderRemove() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        tomcat.getConnector().setProperty("serverRemoveAppProvidedValues", "true");

        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");
        Tomcat.addServlet(ctxt, "server", new ServerHeaderServlet());
        ctxt.addServletMappingDecoded("/server", "server");
        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        // Disable overhead protection for window update as it breaks some tests
        http2Protocol.setOverheadWindowUpdateThreshold(0);

        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        buildGetRequest(headersFrameHeader, headersPayload, null, 3, "/server");

        // Write the headers
        writeFrame(headersFrameHeader, headersPayload);

        parser.readFrame();
        parser.readFrame();

        Assert.assertEquals("3-HeadersStart\n" + "3-Header-[:status]-[200]\n" +
                "3-Header-[content-type]-[text/plain;charset=UTF-8]\n" +
                "3-Header-[content-length]-[2]\n" +
                "3-Header-[date]-[" + DEFAULT_DATE + "]\n" + "3-HeadersEnd\n" + "3-Body-2\n" + "3-EndOfStream\n",
                output.getTrace());
    }


    @Test
    public void testServerHeaderForce() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");
        Tomcat.addServlet(ctxt, "server", new ServerHeaderServlet());
        ctxt.addServletMappingDecoded("/server", "server");
        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        /*
         * This adds the server header to every response. Set this after the initial response has been validated to
         * avoid having to update the validation code to account for the additional server header.
         */
        tomcat.getConnector().setProperty("server", "TestServerForce");

        // Disable overhead protection for window update as it breaks some tests
        http2Protocol.setOverheadWindowUpdateThreshold(0);

        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        buildGetRequest(headersFrameHeader, headersPayload, null, 3, "/server");

        // Write the headers
        writeFrame(headersFrameHeader, headersPayload);

        parser.readFrame();
        parser.readFrame();

        Assert.assertEquals("3-HeadersStart\n" + "3-Header-[:status]-[200]\n" +
                "3-Header-[server]-[TestServerForce]\n" +
                "3-Header-[content-type]-[text/plain;charset=UTF-8]\n" +
                "3-Header-[content-length]-[2]\n" +
                "3-Header-[date]-[" + DEFAULT_DATE + "]\n" + "3-HeadersEnd\n" + "3-Body-2\n" + "3-EndOfStream\n",
                output.getTrace());
    }


    private static class ServerHeaderServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.addHeader("server", "TestServerApp");

            resp.setCharacterEncoding(StandardCharsets.UTF_8);
            resp.setContentType("text/plain");

            resp.getWriter().write("OK");
        }
    }
}
