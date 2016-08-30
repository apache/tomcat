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
package org.apache.coyote.http11;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;

public class TestHttp11Processor extends TomcatBaseTest {

    @Test
    public void testResponseWithErrorChunked() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // This setting means the connection will be closed at the end of the
        // request
        tomcat.getConnector().setAttribute("maxKeepAliveRequests", "1");

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        // Add protected servlet
        Tomcat.addServlet(ctx, "ChunkedResponseWithErrorServlet",
                new ResponseWithErrorServlet(true));
        ctx.addServletMappingDecoded("/*", "ChunkedResponseWithErrorServlet");

        tomcat.start();

        String request =
                "GET /anything HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: any" + SimpleHttpClient.CRLF +
                 SimpleHttpClient.CRLF;

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();

        // Expected response is a 200 response followed by an incomplete chunked
        // body.
        assertTrue(client.isResponse200());
        // Should use chunked encoding
        String transferEncoding = null;
        for (String header : client.getResponseHeaders()) {
             if (header.startsWith("Transfer-Encoding:")) {
                transferEncoding = header.substring(18).trim();
            }
        }
        Assert.assertEquals("chunked", transferEncoding);
        // There should not be an end chunk
        assertFalse(client.getResponseBody().endsWith("0"));
        // The last portion of text should be there
        assertTrue(client.getResponseBody().endsWith("line03"));
    }

    private static class ResponseWithErrorServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final boolean useChunks;

        public ResponseWithErrorServlet(boolean useChunks) {
            this.useChunks = useChunks;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            if (!useChunks) {
                // Longer than it needs to be because response will fail before
                // it is complete
                resp.setContentLength(100);
            }
            PrintWriter pw = resp.getWriter();
            pw.print("line01");
            pw.flush();
            resp.flushBuffer();
            pw.print("line02");
            pw.flush();
            resp.flushBuffer();
            pw.print("line03");

            // Now throw a RuntimeException to end this request
            throw new ServletException("Deliberate failure");
        }
    }


    @Test
    public void testWithUnknownExpectation() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        String request =
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Expect: unknown" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF;

        Client client = new Client(getPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();
        assertTrue(client.isResponse417());
    }


    @Test
    public void testWithTEVoid() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        String request =
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: void" + SimpleHttpClient.CRLF +
            "Content-Length: 9" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
                    SimpleHttpClient.CRLF +
            "test=data";

        Client client = new Client(getPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();
        assertTrue(client.isResponse501());
    }


    @Test
    public void testWithTEBuffered() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        String request =
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: buffered" + SimpleHttpClient.CRLF +
            "Content-Length: 9" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
                    SimpleHttpClient.CRLF +
            "test=data";

        Client client = new Client(getPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();
        assertTrue(client.isResponse501());
    }


    @Test
    public void testWithTEChunked() throws Exception {
        doTestWithTEChunked(false);
    }


    @Test
    public void testWithTEChunkedWithCL() throws Exception {
        // Should be ignored
        doTestWithTEChunked(true);
    }


    private void doTestWithTEChunked(boolean withCL) throws Exception {

        getTomcatInstanceTestWebapp(false, true);

        String request =
            "POST /test/echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            (withCL ? "Content-length: 1" + SimpleHttpClient.CRLF : "") +
            "Transfer-encoding: chunked" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
            "Connection: close" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "9" + SimpleHttpClient.CRLF +
            "test=data" + SimpleHttpClient.CRLF +
            "0" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF;

        Client client = new Client(getPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();
        assertTrue(client.isResponse200());
        assertTrue(client.getResponseBody().contains("test - data"));
    }


    @Test
    public void testWithTEIdentity() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        String request =
            "POST /test/echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: identity" + SimpleHttpClient.CRLF +
            "Content-Length: 9" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
            "Connection: close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF +
            "test=data";

        Client client = new Client(getPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();
        assertTrue(client.isResponse200());
        assertTrue(client.getResponseBody().contains("test - data"));
    }


    @Test
    public void testWithTESavedRequest() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        String request =
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: savedrequest" + SimpleHttpClient.CRLF +
            "Content-Length: 9" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
                    SimpleHttpClient.CRLF +
            "test=data";

        Client client = new Client(getPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();
        assertTrue(client.isResponse501());
    }


    @Test
    public void testWithTEUnsupported() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        String request =
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: unsupported" + SimpleHttpClient.CRLF +
            "Content-Length: 9" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
                    SimpleHttpClient.CRLF +
            "test=data";

        Client client = new Client(getPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();
        assertTrue(client.isResponse501());
    }


    @Test
    public void testPipelining() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        // Add protected servlet
        Tomcat.addServlet(ctx, "TesterServlet", new TesterServlet());
        ctx.addServletMappingDecoded("/foo", "TesterServlet");

        tomcat.start();

        String requestPart1 =
            "GET /foo HTTP/1.1" + SimpleHttpClient.CRLF;
        String requestPart2 =
            "Host: any" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF;

        final Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] {requestPart1, requestPart2});
        client.setRequestPause(1000);
        client.setUseContentLength(true);
        client.connect();

        Runnable send = new Runnable() {
            @Override
            public void run() {
                try {
                    client.sendRequest();
                    client.sendRequest();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        Thread t = new Thread(send);
        t.start();

        // Sleep for 1500 ms which should mean the all of request 1 has been
        // sent and half of request 2
        Thread.sleep(1500);

        // Now read the first response
        client.readResponse(true);
        assertFalse(client.isResponse50x());
        assertTrue(client.isResponse200());
        assertEquals("OK", client.getResponseBody());

        // Read the second response. No need to sleep, read will block until
        // there is data to process
        client.readResponse(true);
        assertFalse(client.isResponse50x());
        assertTrue(client.isResponse200());
        assertEquals("OK", client.getResponseBody());
    }


    @Test
    public void testChunking11NoContentLength() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        Tomcat.addServlet(ctx, "NoContentLengthFlushingServlet",
                new NoContentLengthFlushingServlet());
        ctx.addServletMappingDecoded("/test", "NoContentLengthFlushingServlet");

        tomcat.start();

        ByteChunk responseBody = new ByteChunk();
        Map<String,List<String>> responseHeaders = new HashMap<>();
        int rc = getUrl("http://localhost:" + getPort() + "/test", responseBody,
                responseHeaders);

        assertEquals(HttpServletResponse.SC_OK, rc);
        assertTrue(responseHeaders.containsKey("Transfer-Encoding"));
        List<String> encodings = responseHeaders.get("Transfer-Encoding");
        assertEquals(1, encodings.size());
        assertEquals("chunked", encodings.get(0));
    }

    @Test
    public void testNoChunking11NoContentLengthConnectionClose()
            throws Exception {

        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        Tomcat.addServlet(ctx, "NoContentLengthConnectionCloseFlushingServlet",
                new NoContentLengthConnectionCloseFlushingServlet());
        ctx.addServletMappingDecoded("/test",
                "NoContentLengthConnectionCloseFlushingServlet");

        tomcat.start();

        ByteChunk responseBody = new ByteChunk();
        Map<String,List<String>> responseHeaders = new HashMap<>();
        int rc = getUrl("http://localhost:" + getPort() + "/test", responseBody,
                responseHeaders);

        assertEquals(HttpServletResponse.SC_OK, rc);

        assertTrue(responseHeaders.containsKey("Connection"));
        List<String> connections = responseHeaders.get("Connection");
        assertEquals(1, connections.size());
        assertEquals("close", connections.get(0));

        assertFalse(responseHeaders.containsKey("Transfer-Encoding"));

        assertEquals("OK", responseBody.toString());
    }

    @Test
    public void testBug53677a() throws Exception {
        doTestBug53677(false);
    }

    @Test
    public void testBug53677b() throws Exception {
        doTestBug53677(true);
    }

    private void doTestBug53677(boolean flush) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        Tomcat.addServlet(ctx, "LargeHeaderServlet",
                new LargeHeaderServlet(flush));
        ctx.addServletMappingDecoded("/test", "LargeHeaderServlet");

        tomcat.start();

        ByteChunk responseBody = new ByteChunk();
        Map<String,List<String>> responseHeaders = new HashMap<>();
        int rc = getUrl("http://localhost:" + getPort() + "/test", responseBody,
                responseHeaders);

        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);
        if (responseBody.getLength() > 0) {
            // It will be >0 if the standard error page handling has been
            // triggered
            assertFalse(responseBody.toString().contains("FAIL"));
        }
    }


    private static CountDownLatch bug55772Latch1 = new CountDownLatch(1);
    private static CountDownLatch bug55772Latch2 = new CountDownLatch(1);
    private static CountDownLatch bug55772Latch3 = new CountDownLatch(1);
    private static boolean bug55772IsSecondRequest = false;
    private static boolean bug55772RequestStateLeaked = false;


    @Test
    public void testBug55772() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.getConnector().setProperty("processorCache", "1");
        tomcat.getConnector().setProperty("maxThreads", "1");

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        Tomcat.addServlet(ctx, "async", new Bug55772Servlet());
        ctx.addServletMappingDecoded("/*", "async");

        tomcat.start();

        String request1 = "GET /async?1 HTTP/1.1\r\n" +
                "Host: localhost:" + getPort() + "\r\n" +
                "Connection: keep-alive\r\n" +
                "Cache-Control: max-age=0\r\n" +
                "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\r\n" +
                "User-Agent: Request1\r\n" +
                "Accept-Encoding: gzip,deflate,sdch\r\n" +
                "Accept-Language: en-US,en;q=0.8,fr;q=0.6,es;q=0.4\r\n" +
                "Cookie: something.that.should.not.leak=true\r\n" +
                "\r\n";

        String request2 = "GET /async?2 HTTP/1.1\r\n" +
                "Host: localhost:" + getPort() + "\r\n" +
                "Connection: keep-alive\r\n" +
                "Cache-Control: max-age=0\r\n" +
                "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\r\n" +
                "User-Agent: Request2\r\n" +
                "Accept-Encoding: gzip,deflate,sdch\r\n" +
                "Accept-Language: en-US,en;q=0.8,fr;q=0.6,es;q=0.4\r\n" +
                "\r\n";

        try (final Socket connection = new Socket("localhost", getPort())) {
            connection.setSoLinger(true, 0);
            Writer writer = new OutputStreamWriter(connection.getOutputStream(),
                    StandardCharsets.US_ASCII);
            writer.write(request1);
            writer.flush();

            bug55772Latch1.await();
            connection.close();
        }

        bug55772Latch2.await();
        bug55772IsSecondRequest = true;

        try (final Socket connection = new Socket("localhost", getPort())) {
            connection.setSoLinger(true, 0);
            Writer writer = new OutputStreamWriter(connection.getOutputStream(),
                    B2CConverter.getCharset("US-ASCII"));
            writer.write(request2);
            writer.flush();
            connection.getInputStream().read();
        }

        bug55772Latch3.await();
        if (bug55772RequestStateLeaked) {
            Assert.fail("State leaked between requests!");
        }
    }


    // https://bz.apache.org/bugzilla/show_bug.cgi?id=57324
    @Test
    public void testNon2xxResponseWithExpectation() throws Exception {
        doTestNon2xxResponseAndExpectation(true);
    }

    @Test
    public void testNon2xxResponseWithoutExpectation() throws Exception {
        doTestNon2xxResponseAndExpectation(false);
    }

    private void doTestNon2xxResponseAndExpectation(boolean useExpectation) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        Tomcat.addServlet(ctx, "echo", new EchoBodyServlet());
        ctx.addServletMappingDecoded("/echo", "echo");

        SecurityCollection collection = new SecurityCollection("All", "");
        collection.addPatternDecoded("/*");
        SecurityConstraint constraint = new SecurityConstraint();
        constraint.addAuthRole("Any");
        constraint.addCollection(collection);
        ctx.addConstraint(constraint);

        tomcat.start();

        byte[] requestBody = "HelloWorld".getBytes(StandardCharsets.UTF_8);
        Map<String,List<String>> reqHeaders = null;
        if (useExpectation) {
            reqHeaders = new HashMap<>();
            List<String> expectation = new ArrayList<>();
            expectation.add("100-continue");
            reqHeaders.put("Expect", expectation);
        }
        ByteChunk responseBody = new ByteChunk();
        Map<String,List<String>> responseHeaders = new HashMap<>();
        int rc = postUrl(requestBody, "http://localhost:" + getPort() + "/echo",
                responseBody, reqHeaders, responseHeaders);

        Assert.assertEquals(HttpServletResponse.SC_FORBIDDEN, rc);
        List<String> connectionHeaders = responseHeaders.get("Connection");
        if (useExpectation) {
            Assert.assertEquals(1, connectionHeaders.size());
            Assert.assertEquals("close", connectionHeaders.get(0).toLowerCase(Locale.ENGLISH));
        } else {
            Assert.assertNull(connectionHeaders);
        }
    }


    private static class Bug55772Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            if (bug55772IsSecondRequest) {
                Cookie[] cookies = req.getCookies();
                if (cookies != null && cookies.length > 0) {
                    for (Cookie cookie : req.getCookies()) {
                        if (cookie.getName().equalsIgnoreCase("something.that.should.not.leak")) {
                            bug55772RequestStateLeaked = true;
                        }
                    }
                }
                bug55772Latch3.countDown();
            } else {
                req.getCookies(); // We have to do this so Tomcat will actually parse the cookies from the request
            }

            req.setAttribute("org.apache.catalina.ASYNC_SUPPORTED", Boolean.TRUE);
            AsyncContext asyncContext = req.startAsync();
            asyncContext.setTimeout(5000);

            bug55772Latch1.countDown();

            PrintWriter writer = asyncContext.getResponse().getWriter();
            writer.print('\n');
            writer.flush();

            bug55772Latch2.countDown();
        }
    }


    private static final class LargeHeaderServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        boolean flush = false;

        public LargeHeaderServlet(boolean flush) {
            this.flush = flush;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            String largeValue =
                    CharBuffer.allocate(10000).toString().replace('\0', 'x');
            resp.setHeader("x-Test", largeValue);
            if (flush) {
                resp.flushBuffer();
            }
            resp.setContentType("text/plain");
            resp.getWriter().print("FAIL");
        }

    }

    // flushes with no content-length set
    // should result in chunking on HTTP 1.1
    private static final class NoContentLengthFlushingServlet
            extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("text/plain");
            resp.getWriter().write("OK");
            resp.flushBuffer();
        }
    }

    // flushes with no content-length set but sets Connection: close header
    // should no result in chunking on HTTP 1.1
    private static final class NoContentLengthConnectionCloseFlushingServlet
            extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("text/event-stream");
            resp.addHeader("Connection", "close");
            resp.flushBuffer();
            resp.getWriter().write("OK");
            resp.flushBuffer();
        }
    }

    private static final class Client extends SimpleHttpClient {

        public Client(int port) {
            setPort(port);
        }

        @Override
        public boolean isResponseBodyOK() {
            return getResponseBody().contains("test - data");
        }
    }


    /*
     * Partially read chunked input is not swallowed when it is read during
     * async processing.
     */
    @Test
    public void testBug57621a() throws Exception {
        doTestBug57621(true);
    }


    @Test
    public void testBug57621b() throws Exception {
        doTestBug57621(false);
    }


    private void doTestBug57621(boolean delayAsyncThread) throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", null);
        Wrapper w = Tomcat.addServlet(root, "Bug57621", new Bug57621Servlet(delayAsyncThread));
        w.setAsyncSupported(true);
        root.addServletMappingDecoded("/test", "Bug57621");

        tomcat.start();

        Bug57621Client client = new Bug57621Client();
        client.setPort(tomcat.getConnector().getLocalPort());

        client.setUseContentLength(true);

        client.connect();

        client.doRequest();
        assertTrue(client.getResponseLine(), client.isResponse200());
        assertTrue(client.isResponseBodyOK());

        // Do the request again to ensure that the remaining body was swallowed
        client.resetResponse();
        client.processRequest();
        assertTrue(client.isResponse200());
        assertTrue(client.isResponseBodyOK());

        client.disconnect();
    }


    private static class Bug57621Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final boolean delayAsyncThread;


        public Bug57621Servlet(boolean delayAsyncThread) {
            this.delayAsyncThread = delayAsyncThread;
        }


        @Override
        protected void doPut(HttpServletRequest req, final HttpServletResponse resp)
                throws ServletException, IOException {
            final AsyncContext ac = req.startAsync();
            ac.start(new Runnable() {
                @Override
                public void run() {
                    if (delayAsyncThread) {
                        // Makes the difference between calling complete before
                        // the request body is received of after.
                        try {
                            Thread.sleep(1500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    resp.setContentType("text/plain");
                    resp.setCharacterEncoding("UTF-8");
                    try {
                        resp.getWriter().print("OK");
                    } catch (IOException e) {
                        // Should never happen. Test will fail if it does.
                    }
                    ac.complete();
                }
            });
        }
    }


    private static class Bug57621Client extends SimpleHttpClient {

        private Exception doRequest() {
            try {
                String[] request = new String[2];
                request[0] =
                    "PUT http://localhost:8080/test HTTP/1.1" + CRLF +
                    "Transfer-encoding: chunked" + CRLF +
                    CRLF +
                    "2" + CRLF +
                    "OK";

                request[1] =
                    CRLF +
                    "0" + CRLF +
                    CRLF;

                setRequest(request);
                processRequest(); // blocks until response has been read
            } catch (Exception e) {
                return e;
            }
            return null;
        }

        @Override
        public boolean isResponseBodyOK() {
            if (getResponseBody() == null) {
                return false;
            }
            if (!getResponseBody().contains("OK")) {
                return false;
            }
            return true;
        }
    }


    @Test
    public void testBug59310() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        Tomcat.addServlet(ctx, "Bug59310", new Bug59310Servlet());
        ctx.addServletMappingDecoded("/test", "Bug59310");

        tomcat.start();

        ByteChunk responseBody = new ByteChunk();
        Map<String,List<String>> responseHeaders = new HashMap<>();

        int rc = headUrl("http://localhost:" + getPort() + "/test", responseBody,
                responseHeaders);

        assertEquals(HttpServletResponse.SC_OK, rc);
        assertEquals(0, responseBody.getLength());
        assertFalse(responseHeaders.containsKey("Content-Length"));
    }


    private static class Bug59310Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            super.doGet(req, resp);
        }

        @Override
        protected void doHead(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
        }
    }


    /*
     * Tests what happens if a request is completed during a dispatch but the
     * request body has not been fully read.
     */
    @Test
    public void testRequestBodySwallowing() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        DispatchingServlet servlet = new DispatchingServlet();
        Wrapper w = Tomcat.addServlet(ctx, "Test", servlet);
        w.setAsyncSupported(true);
        ctx.addServletMappingDecoded("/test", "Test");

        tomcat.start();

        // Hand-craft the client so we have complete control over the timing
        SocketAddress addr = new InetSocketAddress("localhost", getPort());
        Socket socket = new Socket();
        socket.setSoTimeout(300000);
        socket.connect(addr,300000);
        OutputStream os = socket.getOutputStream();
        Writer writer = new OutputStreamWriter(os, "ISO-8859-1");
        InputStream is = socket.getInputStream();
        Reader r = new InputStreamReader(is, "ISO-8859-1");
        BufferedReader reader = new BufferedReader(r);

        // Write the headers
        writer.write("POST /test HTTP/1.1\r\n");
        writer.write("Host: localhost:8080\r\n");
        writer.write("Transfer-Encoding: chunked\r\n");
        writer.write("\r\n");
        writer.flush();

        validateResponse(reader);

        // Write the request body
        writer.write("2\r\n");
        writer.write("AB\r\n");
        writer.write("0\r\n");
        writer.write("\r\n");
        writer.flush();

        // Write the 2nd request
        writer.write("POST /test HTTP/1.1\r\n");
        writer.write("Host: localhost:8080\r\n");
        writer.write("Transfer-Encoding: chunked\r\n");
        writer.write("\r\n");
        writer.flush();

        // Read the 2nd response
        validateResponse(reader);

        // Write the 2nd request body
        writer.write("2\r\n");
        writer.write("AB\r\n");
        writer.write("0\r\n");
        writer.write("\r\n");
        writer.flush();

        // Done
        socket.close();
    }


    private void validateResponse(BufferedReader reader) throws IOException {
        // First line has the response code and should always be 200
        String line = reader.readLine();
        Assert.assertEquals("HTTP/1.1 200 ", line);
        while (!"OK".equals(line)) {
            line = reader.readLine();
        }
    }


    private static class DispatchingServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            if (DispatcherType.ASYNC.equals(req.getDispatcherType())) {
                resp.setContentType("text/plain");
                resp.setCharacterEncoding("UTF-8");
                resp.getWriter().write("OK\n");
            } else {
                req.startAsync().dispatch();
            }
        }
    }
}
