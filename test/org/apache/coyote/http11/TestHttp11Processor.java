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
import java.io.StringReader;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.apache.tomcat.util.http.parser.TokenList;

public class TestHttp11Processor extends TomcatBaseTest {

    @Test
    public void testResponseWithErrorChunked() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // This setting means the connection will be closed at the end of the
        // request
        Assert.assertTrue(tomcat.getConnector().setProperty("maxKeepAliveRequests", "1"));

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add protected servlet
        Tomcat.addServlet(ctx, "ChunkedResponseWithErrorServlet", new ResponseWithErrorServlet(true));
        ctx.addServletMappingDecoded("/*", "ChunkedResponseWithErrorServlet");

        tomcat.start();

        String request = "GET /anything HTTP/1.1" + SimpleHttpClient.CRLF + "Host: any" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF;

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest();

        // Expected response is a 200 response followed by an incomplete chunked
        // body.
        Assert.assertTrue(client.isResponse200());
        // Should use chunked encoding
        String transferEncoding = null;
        for (String header : client.getResponseHeaders()) {
            if (header.startsWith("Transfer-Encoding:")) {
                transferEncoding = header.substring(18).trim();
            }
        }
        Assert.assertEquals("chunked", transferEncoding);
        // There should not be an end chunk
        Assert.assertFalse(client.getResponseBody().endsWith("0"));
        // The last portion of text should be there
        Assert.assertTrue(client.getResponseBody().endsWith("line03" + SimpleHttpClient.CRLF));
    }

    private static class ResponseWithErrorServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final boolean useChunks;

        ResponseWithErrorServlet(boolean useChunks) {
            this.useChunks = useChunks;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

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

        String request = "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF + "Host: any" +
                SimpleHttpClient.CRLF + "Expect: unknown" + SimpleHttpClient.CRLF + SimpleHttpClient.CRLF;

        Client client = new Client(getPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest();
        Assert.assertTrue(client.isResponse417());
    }


    @Test
    public void testWithTEVoid() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        String request = "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF + "Host: any" +
                SimpleHttpClient.CRLF + "Transfer-encoding: void" + SimpleHttpClient.CRLF + "Content-Length: 9" +
                SimpleHttpClient.CRLF + "Content-Type: application/x-www-form-urlencoded" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + "test=data";

        Client client = new Client(getPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest();
        Assert.assertTrue(client.isResponse501());
    }


    @Test
    public void testWithTEBuffered() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        String request = "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF + "Host: any" +
                SimpleHttpClient.CRLF + "Transfer-encoding: buffered" + SimpleHttpClient.CRLF + "Content-Length: 9" +
                SimpleHttpClient.CRLF + "Content-Type: application/x-www-form-urlencoded" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + "test=data";

        Client client = new Client(getPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest();
        Assert.assertTrue(client.isResponse501());
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

        String request = "POST /test/echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF + "Host: any" +
                SimpleHttpClient.CRLF + (withCL ? "Content-length: 1" + SimpleHttpClient.CRLF : "") +
                "Transfer-encoding: chunked" + SimpleHttpClient.CRLF +
                "Content-Type: application/x-www-form-urlencoded" + SimpleHttpClient.CRLF + "Connection: close" +
                SimpleHttpClient.CRLF + SimpleHttpClient.CRLF + "9" + SimpleHttpClient.CRLF + "test=data" +
                SimpleHttpClient.CRLF + "0" + SimpleHttpClient.CRLF + SimpleHttpClient.CRLF;

        Client client = new Client(getPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest();
        Assert.assertTrue(client.isResponse200());
        Assert.assertTrue(client.getResponseBody().contains("test - data"));
    }


    @Test
    public void testWithTESavedRequest() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        String request = "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF + "Host: any" +
                SimpleHttpClient.CRLF + "Transfer-encoding: savedrequest" + SimpleHttpClient.CRLF +
                "Content-Length: 9" + SimpleHttpClient.CRLF + "Content-Type: application/x-www-form-urlencoded" +
                SimpleHttpClient.CRLF + SimpleHttpClient.CRLF + "test=data";

        Client client = new Client(getPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest();
        Assert.assertTrue(client.isResponse501());
    }


    @Test
    public void testWithTEUnsupported() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        String request = "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF + "Host: any" +
                SimpleHttpClient.CRLF + "Transfer-encoding: unsupported" + SimpleHttpClient.CRLF + "Content-Length: 9" +
                SimpleHttpClient.CRLF + "Content-Type: application/x-www-form-urlencoded" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + "test=data";

        Client client = new Client(getPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest();
        Assert.assertTrue(client.isResponse501());
    }


    @Test
    public void testPipelining() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add protected servlet
        Tomcat.addServlet(ctx, "TesterServlet", new TesterServlet());
        ctx.addServletMappingDecoded("/foo", "TesterServlet");

        tomcat.start();

        String requestPart1 = "GET /foo HTTP/1.1" + SimpleHttpClient.CRLF;
        String requestPart2 = "Host: any" + SimpleHttpClient.CRLF + SimpleHttpClient.CRLF;

        final Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] { requestPart1, requestPart2 });
        client.setRequestPause(1000);
        client.setUseContentLength(true);
        client.connect();

        Runnable send = new Runnable() {
            @Override
            public void run() {
                try {
                    client.sendRequest();
                    client.sendRequest();
                } catch (InterruptedException | IOException e) {
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
        Assert.assertFalse(client.isResponse50x());
        Assert.assertTrue(client.isResponse200());
        Assert.assertEquals("OK", client.getResponseBody());

        // Read the second response. No need to sleep, read will block until
        // there is data to process
        client.readResponse(true);
        Assert.assertFalse(client.isResponse50x());
        Assert.assertTrue(client.isResponse200());
        Assert.assertEquals("OK", client.getResponseBody());
    }


    @Test
    public void testPipeliningBug64974() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add protected servlet
        Wrapper w = Tomcat.addServlet(ctx, "servlet", new Bug64974Servlet());
        w.setAsyncSupported(true);
        ctx.addServletMappingDecoded("/foo", "servlet");

        tomcat.start();

        String request = "GET /foo HTTP/1.1" + SimpleHttpClient.CRLF + "Host: any" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF + "GET /foo HTTP/1.1" + SimpleHttpClient.CRLF + "Host: any" +
                SimpleHttpClient.CRLF + SimpleHttpClient.CRLF;

        final Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] { request });
        client.setUseContentLength(true);
        client.connect();
        client.sendRequest();

        // Now read the first response
        client.readResponse(true);
        Assert.assertFalse(client.isResponse50x());
        Assert.assertTrue(client.isResponse200());
        Assert.assertEquals("OK", client.getResponseBody());

        // Read the second response. No need to sleep, read will block until
        // there is data to process
        client.readResponse(true);
        Assert.assertFalse(client.isResponse50x());
        Assert.assertTrue(client.isResponse200());
        Assert.assertEquals("OK", client.getResponseBody());
    }


    @Test
    public void testChunking11NoContentLength() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "NoContentLengthFlushingServlet", new NoContentLengthFlushingServlet());
        ctx.addServletMappingDecoded("/test", "NoContentLengthFlushingServlet");

        tomcat.start();

        ByteChunk responseBody = new ByteChunk();
        Map<String, List<String>> responseHeaders = new HashMap<>();
        int rc = getUrl("http://localhost:" + getPort() + "/test", responseBody, responseHeaders);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String transferEncoding = getSingleHeader("Transfer-Encoding", responseHeaders);
        Assert.assertEquals("chunked", transferEncoding);
    }

    @Test
    public void testNoChunking11NoContentLengthConnectionClose() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "NoContentLengthConnectionCloseFlushingServlet",
                new NoContentLengthConnectionCloseFlushingServlet());
        ctx.addServletMappingDecoded("/test", "NoContentLengthConnectionCloseFlushingServlet");

        tomcat.start();

        ByteChunk responseBody = new ByteChunk();
        Map<String, List<String>> responseHeaders = new HashMap<>();
        int rc = getUrl("http://localhost:" + getPort() + "/test", responseBody, responseHeaders);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String connection = getSingleHeader("Connection", responseHeaders);
        Assert.assertEquals("close", connection);

        Assert.assertFalse(responseHeaders.containsKey("Transfer-Encoding"));

        Assert.assertEquals("OK", responseBody.toString());
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
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "LargeHeaderServlet", new LargeHeaderServlet(flush));
        ctx.addServletMappingDecoded("/test", "LargeHeaderServlet");

        tomcat.start();

        ByteChunk responseBody = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/test", responseBody, null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);
        if (responseBody.getLength() > 0) {
            // It will be >0 if the standard error page handling has been
            // triggered
            Assert.assertFalse(responseBody.toString().contains("FAIL"));
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
        Assert.assertTrue(tomcat.getConnector().setProperty("processorCache", "1"));
        Assert.assertTrue(tomcat.getConnector().setProperty("maxThreads", "1"));

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "async", new Bug55772Servlet());
        ctx.addServletMappingDecoded("/*", "async");

        tomcat.start();

        String request1 = "GET /async?1 HTTP/1.1\r\n" + "Host: localhost:" + getPort() + "\r\n" +
                "Connection: keep-alive\r\n" + "Cache-Control: max-age=0\r\n" +
                "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\r\n" +
                "User-Agent: Request1\r\n" + "Accept-Encoding: gzip,deflate,sdch\r\n" +
                "Accept-Language: en-US,en;q=0.8,fr;q=0.6,es;q=0.4\r\n" +
                "Cookie: something.that.should.not.leak=true\r\n" + "\r\n";

        String request2 = "GET /async?2 HTTP/1.1\r\n" + "Host: localhost:" + getPort() + "\r\n" +
                "Connection: keep-alive\r\n" + "Cache-Control: max-age=0\r\n" +
                "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\r\n" +
                "User-Agent: Request2\r\n" + "Accept-Encoding: gzip,deflate,sdch\r\n" +
                "Accept-Language: en-US,en;q=0.8,fr;q=0.6,es;q=0.4\r\n" + "\r\n";

        try (Socket connection = new Socket("localhost", getPort())) {
            connection.setSoLinger(true, 0);
            Writer writer = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.US_ASCII);
            writer.write(request1);
            writer.flush();

            bug55772Latch1.await();
            connection.close();
        }

        bug55772Latch2.await();
        bug55772IsSecondRequest = true;

        try (Socket connection = new Socket("localhost", getPort())) {
            connection.setSoLinger(true, 0);
            Writer writer = new OutputStreamWriter(connection.getOutputStream(), B2CConverter.getCharset("US-ASCII"));
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
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "echo", new EchoBodyServlet());
        ctx.addServletMappingDecoded("/echo", "echo");

        SecurityCollection collection = new SecurityCollection("All", "");
        collection.addPatternDecoded("/*");
        SecurityConstraint constraint = new SecurityConstraint();
        constraint.addAuthRole("Any");
        constraint.addCollection(collection);
        ctx.addConstraint(constraint);

        tomcat.start();

        String request = "POST /echo HTTP/1.1" + SimpleHttpClient.CRLF + "Host: localhost:" + getPort() +
                SimpleHttpClient.CRLF + "Content-Length: 10" + SimpleHttpClient.CRLF;
        if (useExpectation) {
            request += "Expect: 100-continue" + SimpleHttpClient.CRLF;
        }
        request += SimpleHttpClient.CRLF + "HelloWorld";

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] { request });
        client.setUseContentLength(true);

        client.connect();
        client.processRequest();

        Assert.assertTrue(client.isResponse403());
        String connectionHeaderValue = null;
        for (String header : client.getResponseHeaders()) {
            if (header.startsWith("Connection:")) {
                connectionHeaderValue = header.substring(header.indexOf(':') + 1).trim();
                break;
            }
        }

        if (useExpectation) {
            List<String> connectionHeaders = new ArrayList<>();
            TokenList.parseTokenList(new StringReader(connectionHeaderValue), connectionHeaders);
            Assert.assertEquals(1, connectionHeaders.size());
            Assert.assertEquals("close", connectionHeaders.get(0));
        } else {
            Assert.assertNull(connectionHeaderValue);
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
                            break;
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

        LargeHeaderServlet(boolean flush) {
            this.flush = flush;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            String largeValue = CharBuffer.allocate(10000).toString().replace('\0', 'x');
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
    private static final class NoContentLengthFlushingServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("text/plain");
            resp.getWriter().write("OK");
            resp.flushBuffer();
        }
    }

    // flushes with no content-length set but sets Connection: close header
    // should no result in chunking on HTTP 1.1
    private static final class NoContentLengthConnectionCloseFlushingServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("text/event-stream");
            resp.addHeader("Connection", "close");
            resp.flushBuffer();
            resp.getWriter().write("OK");
            resp.flushBuffer();
        }
    }

    private static final class Client extends SimpleHttpClient {

        Client(int port) {
            setPort(port);
        }

        @Override
        public boolean isResponseBodyOK() {
            return getResponseBody().contains("test - data");
        }
    }


    /*
     * Partially read chunked input is not swallowed when it is read during async processing.
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
        Context root = getProgrammaticRootContext();
        Wrapper w = Tomcat.addServlet(root, "Bug57621", new Bug57621Servlet(delayAsyncThread));
        w.setAsyncSupported(true);
        root.addServletMappingDecoded("/test", "Bug57621");

        tomcat.start();

        Bug57621Client client = new Bug57621Client();
        client.setPort(tomcat.getConnector().getLocalPort());

        client.setUseContentLength(true);

        client.connect();

        client.doRequest();
        Assert.assertTrue(client.getResponseLine(), client.isResponse200());
        Assert.assertTrue(client.isResponseBodyOK());

        // Do the request again to ensure that the remaining body was swallowed
        client.resetResponse();
        client.processRequest();
        Assert.assertTrue(client.isResponse200());
        Assert.assertTrue(client.isResponseBodyOK());

        client.disconnect();
    }


    private static class Bug57621Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final boolean delayAsyncThread;


        Bug57621Servlet(boolean delayAsyncThread) {
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
                        // the request body is received or after.
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
                request[0] = "PUT http://localhost:8080/test HTTP/1.1" + CRLF + "Host: localhost:8080" + CRLF +
                        "Transfer-encoding: chunked" + CRLF + CRLF + "2" + CRLF + "OK";

                request[1] = CRLF + "0" + CRLF + CRLF;

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
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "Bug59310", new Bug59310Servlet());
        ctx.addServletMappingDecoded("/test", "Bug59310");

        tomcat.start();

        ByteChunk getBody = new ByteChunk();
        Map<String, List<String>> getHeaders = new HashMap<>();
        int getStatus = getUrl("http://localhost:" + getPort() + "/test", getBody, getHeaders);

        ByteChunk headBody = new ByteChunk();
        Map<String, List<String>> headHeaders = new HashMap<>();
        int headStatus = getUrl("http://localhost:" + getPort() + "/test", headBody, headHeaders);

        Assert.assertEquals(HttpServletResponse.SC_OK, getStatus);
        Assert.assertEquals(HttpServletResponse.SC_OK, headStatus);

        Assert.assertEquals(0, getBody.getLength());
        Assert.assertEquals(0, headBody.getLength());

        if (getHeaders.containsKey("Content-Length")) {
            Assert.assertEquals(getHeaders.get("Content-Length"), headHeaders.get("Content-Length"));
        } else {
            Assert.assertFalse(headHeaders.containsKey("Content-Length"));
        }
    }


    private static class Bug59310Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        }

        @Override
        protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        }
    }


    /*
     * Tests what happens if a request is completed during a dispatch but the request body has not been fully read.
     */
    @Test
    public void testRequestBodySwallowing() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        DispatchingServlet servlet = new DispatchingServlet();
        Wrapper w = Tomcat.addServlet(ctx, "Test", servlet);
        w.setAsyncSupported(true);
        ctx.addServletMappingDecoded("/test", "Test");

        tomcat.start();

        // Hand-craft the client so we have complete control over the timing
        SocketAddress addr = new InetSocketAddress("localhost", getPort());
        Socket socket = new Socket();
        socket.setSoTimeout(300000);
        socket.connect(addr, 300000);
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
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            if (DispatcherType.ASYNC.equals(req.getDispatcherType())) {
                resp.setContentType("text/plain");
                resp.setCharacterEncoding("UTF-8");
                resp.getWriter().write("OK\n");
            } else {
                req.startAsync().dispatch();
            }
        }
    }

    @Test
    public void testBug61086() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        Bug61086Servlet servlet = new Bug61086Servlet();
        Tomcat.addServlet(ctx, "Test", servlet);
        ctx.addServletMappingDecoded("/test", "Test");

        tomcat.start();

        ByteChunk responseBody = new ByteChunk();
        Map<String, List<String>> responseHeaders = new HashMap<>();
        int rc = getUrl("http://localhost:" + getPort() + "/test", responseBody, responseHeaders);

        Assert.assertEquals(HttpServletResponse.SC_RESET_CONTENT, rc);
        String contentLength = getSingleHeader("Content-Length", responseHeaders);
        Assert.assertEquals("0", contentLength);
        Assert.assertTrue(responseBody.getLength() == 0);
    }

    private static final class Bug61086Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setStatus(205);
        }
    }

    /*
     * Multiple, different Host headers
     */
    @Test
    public void testMultipleHostHeader01() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // This setting means the connection will be closed at the end of the
        // request
        Assert.assertTrue(tomcat.getConnector().setProperty("maxKeepAliveRequests", "1"));

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add servlet
        Tomcat.addServlet(ctx, "TesterServlet", new TesterServlet());
        ctx.addServletMappingDecoded("/foo", "TesterServlet");

        tomcat.start();

        String request = "GET /foo HTTP/1.1" + SimpleHttpClient.CRLF + "Host: a" + SimpleHttpClient.CRLF + "Host: b" +
                SimpleHttpClient.CRLF + SimpleHttpClient.CRLF;

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest();

        // Expected response is a 400 response.
        Assert.assertTrue(client.isResponse400());
    }

    /*
     * Multiple instances of the same Host header
     */
    @Test
    public void testMultipleHostHeader02() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // This setting means the connection will be closed at the end of the
        // request
        Assert.assertTrue(tomcat.getConnector().setProperty("maxKeepAliveRequests", "1"));

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add servlet
        Tomcat.addServlet(ctx, "TesterServlet", new TesterServlet());
        ctx.addServletMappingDecoded("/foo", "TesterServlet");

        tomcat.start();

        String request = "GET /foo HTTP/1.1" + SimpleHttpClient.CRLF + "Host: a" + SimpleHttpClient.CRLF + "Host: a" +
                SimpleHttpClient.CRLF + SimpleHttpClient.CRLF;

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest();

        // Expected response is a 400 response.
        Assert.assertTrue(client.isResponse400());
    }

    @Test
    public void testMissingHostHeader() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // This setting means the connection will be closed at the end of the
        // request
        Assert.assertTrue(tomcat.getConnector().setProperty("maxKeepAliveRequests", "1"));

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add servlet
        Tomcat.addServlet(ctx, "TesterServlet", new TesterServlet());
        ctx.addServletMappingDecoded("/foo", "TesterServlet");

        tomcat.start();

        String request = "GET /foo HTTP/1.1" + SimpleHttpClient.CRLF + SimpleHttpClient.CRLF;

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest();

        // Expected response is a 400 response.
        Assert.assertTrue(client.isResponse400());
    }

    @Test
    public void testInconsistentHostHeader01() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // This setting means the connection will be closed at the end of the
        // request
        Assert.assertTrue(tomcat.getConnector().setProperty("maxKeepAliveRequests", "1"));

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add servlet
        Tomcat.addServlet(ctx, "TesterServlet", new TesterServlet());
        ctx.addServletMappingDecoded("/foo", "TesterServlet");

        tomcat.start();

        String request = "GET http://a/foo HTTP/1.1" + SimpleHttpClient.CRLF + "Host: b" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF;

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest();

        // Expected response is a 400 response.
        Assert.assertTrue(client.isResponse400());
    }

    @Test
    public void testInconsistentHostHeader02() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // This setting means the connection will be closed at the end of the
        // request
        Assert.assertTrue(tomcat.getConnector().setProperty("maxKeepAliveRequests", "1"));

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add servlet
        Tomcat.addServlet(ctx, "TesterServlet", new TesterServlet());
        ctx.addServletMappingDecoded("/foo", "TesterServlet");

        tomcat.start();

        String request = "GET http://a:8080/foo HTTP/1.1" + SimpleHttpClient.CRLF + "Host: b:8080" +
                SimpleHttpClient.CRLF + SimpleHttpClient.CRLF;

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest();

        // Expected response is a 400 response.
        Assert.assertTrue(client.isResponse400());
    }

    @Test
    public void testInconsistentHostHeader03() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // This setting means the connection will be closed at the end of the
        // request
        Assert.assertTrue(tomcat.getConnector().setProperty("maxKeepAliveRequests", "1"));

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add servlet
        Tomcat.addServlet(ctx, "TesterServlet", new TesterServlet());
        ctx.addServletMappingDecoded("/foo", "TesterServlet");

        tomcat.start();

        String request = "GET http://user:pwd@a/foo HTTP/1.1" + SimpleHttpClient.CRLF + "Host: b" +
                SimpleHttpClient.CRLF + SimpleHttpClient.CRLF;

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest();

        // Expected response is a 400 response.
        Assert.assertTrue(client.isResponse400());
    }

    /*
     * Hostname (no port) is included in the request line, but Host header is empty. Added for bug 62739.
     */
    @Test
    public void testInconsistentHostHeader04() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // This setting means the connection will be closed at the end of the
        // request
        Assert.assertTrue(tomcat.getConnector().setProperty("maxKeepAliveRequests", "1"));

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add servlet
        Tomcat.addServlet(ctx, "TesterServlet", new TesterServlet());
        ctx.addServletMappingDecoded("/foo", "TesterServlet");

        tomcat.start();

        String request = "GET http://a/foo HTTP/1.1" + SimpleHttpClient.CRLF + "Host: " + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF;

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest();

        // Expected response is a 400 response.
        Assert.assertTrue(client.isResponse400());
    }

    /*
     * Hostname (with port) is included in the request line, but Host header is empty. Added for bug 62739.
     */
    @Test
    public void testInconsistentHostHeader05() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // This setting means the connection will be closed at the end of the
        // request
        Assert.assertTrue(tomcat.getConnector().setProperty("maxKeepAliveRequests", "1"));

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add servlet
        Tomcat.addServlet(ctx, "TesterServlet", new TesterServlet());
        ctx.addServletMappingDecoded("/foo", "TesterServlet");

        tomcat.start();

        String request = "GET http://a:8080/foo HTTP/1.1" + SimpleHttpClient.CRLF + "Host: " + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF;

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest();

        // Expected response is a 400 response.
        Assert.assertTrue(client.isResponse400());
    }

    /*
     * Hostname (with port and user) is included in the request line, but Host header is empty. Added for bug 62739.
     */
    @Test
    public void testInconsistentHostHeader06() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // This setting means the connection will be closed at the end of the
        // request
        Assert.assertTrue(tomcat.getConnector().setProperty("maxKeepAliveRequests", "1"));

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add servlet
        Tomcat.addServlet(ctx, "TesterServlet", new TesterServlet());
        ctx.addServletMappingDecoded("/foo", "TesterServlet");

        tomcat.start();

        String request = "GET http://user:pwd@a/foo HTTP/1.1" + SimpleHttpClient.CRLF + "Host: " +
                SimpleHttpClient.CRLF + SimpleHttpClient.CRLF;

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest();

        // Expected response is a 400 response.
        Assert.assertTrue(client.isResponse400());
    }


    /*
     * Request line host is an exact match for Host header (no port)
     */
    @Test
    public void testConsistentHostHeader01() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // This setting means the connection will be closed at the end of the
        // request
        Assert.assertTrue(tomcat.getConnector().setProperty("maxKeepAliveRequests", "1"));

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add servlet
        Tomcat.addServlet(ctx, "TesterServlet", new ServerNameTesterServlet());
        ctx.addServletMappingDecoded("/foo", "TesterServlet");

        tomcat.start();

        String request = "GET http://a/foo HTTP/1.1" + SimpleHttpClient.CRLF + "Host: a" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF;

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest();

        // Expected response is a 200 response.
        Assert.assertTrue(client.isResponse200());
        Assert.assertEquals("request.getServerName() is [a] and request.getServerPort() is 80",
                client.getResponseBody());
    }

    /*
     * Request line host is an exact match for Host header (with port)
     */
    @Test
    public void testConsistentHostHeader02() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // This setting means the connection will be closed at the end of the
        // request
        Assert.assertTrue(tomcat.getConnector().setProperty("maxKeepAliveRequests", "1"));

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add servlet
        Tomcat.addServlet(ctx, "TesterServlet", new ServerNameTesterServlet());
        ctx.addServletMappingDecoded("/foo", "TesterServlet");

        tomcat.start();

        String request = "GET http://a:8080/foo HTTP/1.1" + SimpleHttpClient.CRLF + "Host: a:8080" +
                SimpleHttpClient.CRLF + SimpleHttpClient.CRLF;

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest();

        // Expected response is a 200 response.
        Assert.assertTrue(client.isResponse200());
        Assert.assertEquals("request.getServerName() is [a] and request.getServerPort() is 8080",
                client.getResponseBody());

    }

    /*
     * Request line host is an exact match for Host header (no port, with user info)
     */
    @Test
    public void testConsistentHostHeader03() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // This setting means the connection will be closed at the end of the
        // request
        Assert.assertTrue(tomcat.getConnector().setProperty("maxKeepAliveRequests", "1"));

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add servlet
        Tomcat.addServlet(ctx, "TesterServlet", new ServerNameTesterServlet());
        ctx.addServletMappingDecoded("/foo", "TesterServlet");

        tomcat.start();

        String request = "GET http://user:pwd@a/foo HTTP/1.1" + SimpleHttpClient.CRLF + "Host: a" +
                SimpleHttpClient.CRLF + SimpleHttpClient.CRLF;

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest();

        // Expected response is a 200 response.
        Assert.assertTrue(client.isResponse200());
        Assert.assertEquals("request.getServerName() is [a] and request.getServerPort() is 80",
                client.getResponseBody());
    }

    /*
     * Request line host is case insensitive match for Host header (no port, no user info)
     */
    @Test
    public void testConsistentHostHeader04() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // This setting means the connection will be closed at the end of the
        // request
        Assert.assertTrue(tomcat.getConnector().setProperty("maxKeepAliveRequests", "1"));

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add servlet
        Tomcat.addServlet(ctx, "TesterServlet", new ServerNameTesterServlet());
        ctx.addServletMappingDecoded("/foo", "TesterServlet");

        tomcat.start();

        String request = "GET http://a/foo HTTP/1.1" + SimpleHttpClient.CRLF + "Host: A" +
                SimpleHttpClient.CRLF + SimpleHttpClient.CRLF;

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest();

        // Expected response is a 200 response.
        Assert.assertTrue(client.isResponse200());
        Assert.assertEquals("request.getServerName() is [A] and request.getServerPort() is 80",
                client.getResponseBody());
    }

    /*
     * Host header exists but its value is an empty string. This is valid if the request line does not include a
     * hostname/port. Added for bug 62739.
     */
    @Test
    public void testBlankHostHeader01() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // This setting means the connection will be closed at the end of the
        // request
        Assert.assertTrue(tomcat.getConnector().setProperty("maxKeepAliveRequests", "1"));

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add servlet
        Tomcat.addServlet(ctx, "TesterServlet", new ServerNameTesterServlet());
        ctx.addServletMappingDecoded("/foo", "TesterServlet");

        tomcat.start();

        String request = "GET /foo HTTP/1.1" + SimpleHttpClient.CRLF + "Host: " + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF;

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest();

        // Expected response is a 200 response.
        Assert.assertTrue(client.isResponse200());
        Assert.assertEquals("request.getServerName() is [] and request.getServerPort() is " + getPort(),
                client.getResponseBody());
    }

    /*
     * Host header exists but has its value is empty (and there are multiple spaces after the ':'. This is valid if the
     * request line does not include a hostname/port. Added for bug 62739.
     */
    @Test
    public void testBlankHostHeader02() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // This setting means the connection will be closed at the end of the
        // request
        Assert.assertTrue(tomcat.getConnector().setProperty("maxKeepAliveRequests", "1"));

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add servlet
        Tomcat.addServlet(ctx, "TesterServlet", new ServerNameTesterServlet());
        ctx.addServletMappingDecoded("/foo", "TesterServlet");

        tomcat.start();

        String request = "GET /foo HTTP/1.1" + SimpleHttpClient.CRLF + "Host:      " + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF;

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest();

        // Expected response is a 200 response.
        Assert.assertTrue(client.isResponse200());
        Assert.assertEquals("request.getServerName() is [] and request.getServerPort() is " + getPort(),
                client.getResponseBody());
    }


    @Test
    public void testKeepAliveHeader01() throws Exception {
        doTestKeepAliveHeader(false, 3000, 10, false);
    }

    @Test
    public void testKeepAliveHeader02() throws Exception {
        doTestKeepAliveHeader(true, 5000, 1, false);
    }

    @Test
    public void testKeepAliveHeader03() throws Exception {
        doTestKeepAliveHeader(true, 5000, 10, false);
    }

    @Test
    public void testKeepAliveHeader04() throws Exception {
        doTestKeepAliveHeader(true, -1, 10, false);
    }

    @Test
    public void testKeepAliveHeader05() throws Exception {
        doTestKeepAliveHeader(true, -1, 1, false);
    }

    @Test
    public void testKeepAliveHeader06() throws Exception {
        doTestKeepAliveHeader(true, -1, -1, false);
    }

    @Test
    public void testKeepAliveHeader07() throws Exception {
        doTestKeepAliveHeader(false, 3000, 10, true);
    }

    @Test
    public void testKeepAliveHeader08() throws Exception {
        doTestKeepAliveHeader(true, 5000, 1, true);
    }

    @Test
    public void testKeepAliveHeader09() throws Exception {
        doTestKeepAliveHeader(true, 5000, 10, true);
    }

    @Test
    public void testKeepAliveHeader10() throws Exception {
        doTestKeepAliveHeader(true, -1, 10, true);
    }

    @Test
    public void testKeepAliveHeader11() throws Exception {
        doTestKeepAliveHeader(true, -1, 1, true);
    }

    @Test
    public void testKeepAliveHeader12() throws Exception {
        doTestKeepAliveHeader(true, -1, -1, true);
    }

    private void doTestKeepAliveHeader(boolean sendKeepAlive, int keepAliveTimeout, int maxKeepAliveRequests,
            boolean explicitClose) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        tomcat.getConnector().setProperty("keepAliveTimeout", Integer.toString(keepAliveTimeout));
        tomcat.getConnector().setProperty("maxKeepAliveRequests", Integer.toString(maxKeepAliveRequests));

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add servlet
        Tomcat.addServlet(ctx, "TesterServlet", new TesterServlet(explicitClose));
        ctx.addServletMappingDecoded("/foo", "TesterServlet");

        tomcat.start();

        String request = "GET /foo HTTP/1.1" + SimpleHttpClient.CRLF + "Host: localhost:" + getPort() +
                SimpleHttpClient.CRLF;

        if (sendKeepAlive) {
            request += "Connection: keep-alive" + SimpleHttpClient.CRLF;
        }

        request += SimpleHttpClient.CRLF;

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest(false);

        Assert.assertTrue(client.isResponse200());

        String connectionHeaderValue = null;
        String keepAliveHeaderValue = null;
        for (String header : client.getResponseHeaders()) {
            if (header.startsWith("Connection:")) {
                connectionHeaderValue = header.substring(header.indexOf(':') + 1).trim();
            }
            if (header.startsWith("Keep-Alive:")) {
                keepAliveHeaderValue = header.substring(header.indexOf(':') + 1).trim();
            }
        }

        if (explicitClose) {
            Assert.assertEquals("close", connectionHeaderValue);
            Assert.assertNull(keepAliveHeaderValue);
        } else if (!sendKeepAlive || keepAliveTimeout < 0 && (maxKeepAliveRequests < 0 || maxKeepAliveRequests > 1)) {
            Assert.assertNull(connectionHeaderValue);
            Assert.assertNull(keepAliveHeaderValue);
        } else {
            List<String> connectionHeaders = new ArrayList<>();
            TokenList.parseTokenList(new StringReader(connectionHeaderValue), connectionHeaders);

            if (sendKeepAlive && keepAliveTimeout > 0 && (maxKeepAliveRequests < 0 || maxKeepAliveRequests > 1)) {
                Assert.assertEquals(1, connectionHeaders.size());
                Assert.assertEquals("keep-alive", connectionHeaders.get(0));
                Assert.assertEquals("timeout=" + keepAliveTimeout / 1000L, keepAliveHeaderValue);
            }

            if (sendKeepAlive && maxKeepAliveRequests == 1) {
                Assert.assertEquals(1, connectionHeaders.size());
                Assert.assertEquals("close", connectionHeaders.get(0));
                Assert.assertNull(keepAliveHeaderValue);
            }
        }
    }


    /**
     * Test servlet that prints out the values of HttpServletRequest.getServerName() and
     * HttpServletRequest.getServerPort() in the response body, e.g.: "request.getServerName() is [foo] and
     * request.getServerPort() is 8080" or: "request.getServerName() is null and request.getServerPort() is 8080"
     */
    private static class ServerNameTesterServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

            resp.setContentType("text/plain");
            PrintWriter out = resp.getWriter();

            if (null == req.getServerName()) {
                out.print("request.getServerName() is null");
            } else {
                out.print("request.getServerName() is [" + req.getServerName() + "]");
            }

            out.print(" and request.getServerPort() is " + req.getServerPort());
        }
    }


    @Test
    public void testSlowUploadTimeoutWithLongerUploadTimeout() throws Exception {
        doTestSlowUploadTimeout(true);
    }


    @Test
    public void testSlowUploadTimeoutWithoutLongerUploadTimeout() throws Exception {
        doTestSlowUploadTimeout(false);
    }


    private void doTestSlowUploadTimeout(boolean useLongerUploadTimeout) throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Connector connector = tomcat.getConnector();

        int connectionTimeout = ((Integer) connector.getProperty("connectionTimeout")).intValue();

        // These factors should make the differences large enough that the CI
        // tests pass consistently. If not, may need to reduce connectionTimeout
        // and increase delay and connectionUploadTimeout
        int delay = connectionTimeout * 2;
        int connectionUploadTimeout = connectionTimeout * 4;

        if (useLongerUploadTimeout) {
            connector.setProperty("connectionUploadTimeout", "" + connectionUploadTimeout);
            connector.setProperty("disableUploadTimeout", "false");
        }

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add servlet
        Tomcat.addServlet(ctx, "TesterServlet", new SwallowBodyTesterServlet());
        ctx.addServletMappingDecoded("/foo", "TesterServlet");

        tomcat.start();

        String request = "POST /foo HTTP/1.1" + SimpleHttpClient.CRLF + "Host: localhost:" + getPort() +
                SimpleHttpClient.CRLF + "Content-Length: 10" + SimpleHttpClient.CRLF + SimpleHttpClient.CRLF;

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] { request, "XXXXXXXXXX" });
        client.setRequestPause(delay);

        client.connect();
        try {
            client.processRequest();
        } catch (IOException ioe) {
            // Failure is expected on some platforms (notably Windows) if the
            // longer upload timeout is not used but record the exception in
            // case it is useful for debugging purposes.
            // The assertions below will check for the correct behaviour.
            ioe.printStackTrace();
        }

        if (useLongerUploadTimeout) {
            // Expected response is a 200 response.
            Assert.assertTrue(client.isResponse200());
            Assert.assertEquals("OK", client.getResponseBody());
        } else {
            // Different failure modes with different connectors
            Assert.assertFalse(client.isResponse200());
        }
    }


    private static class SwallowBodyTesterServlet extends TesterServlet {

        private static final long serialVersionUID = 1L;

        SwallowBodyTesterServlet() {
            super(true);
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

            // Swallow the body
            byte[] buf = new byte[1024];
            InputStream is = req.getInputStream();
            while (is.read(buf) > 0) {
                // Loop
            }

            // Standard response
            doGet(req, resp);
        }
    }


    private static class Bug64974Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

            // Get requests can have bodies although these requests don't.
            // Needs to be async to trigger the problematic code path
            AsyncContext ac = req.startAsync();
            ServletInputStream sis = req.getInputStream();
            // This triggers a call to Http11InputBuffer.available(true) which
            // did not handle the pipelining case.
            sis.setReadListener(new Bug64974ReadListener());
            ac.complete();

            resp.setContentType("text/plain");
            PrintWriter out = resp.getWriter();
            out.print("OK");
        }
    }


    private static class Bug64974ReadListener implements ReadListener {

        @Override
        public void onDataAvailable() throws IOException {
            // NO-OP
        }

        @Override
        public void onAllDataRead() throws IOException {
            // NO-OP
        }

        @Override
        public void onError(Throwable throwable) {
            // NO-OP
        }
    }


    @Test
    public void testTEHeaderUnknown01() throws Exception {
        doTestTEHeaderInvalid("identity", false);
    }


    @Test
    public void testTEHeaderUnknown02() throws Exception {
        doTestTEHeaderInvalid("identity, chunked", false);
    }


    @Test
    public void testTEHeaderUnknown03() throws Exception {
        doTestTEHeaderInvalid("unknown, chunked", false);
    }


    @Test
    public void testTEHeaderUnknown04() throws Exception {
        doTestTEHeaderInvalid("void", false);
    }


    @Test
    public void testTEHeaderUnknown05() throws Exception {
        doTestTEHeaderInvalid("void, chunked", false);
    }


    @Test
    public void testTEHeaderUnknown06() throws Exception {
        doTestTEHeaderInvalid("void, identity", false);
    }


    @Test
    public void testTEHeaderUnknown07() throws Exception {
        doTestTEHeaderInvalid("identity, void", false);
    }


    @Test
    public void testTEHeaderChunkedNotLast01() throws Exception {
        doTestTEHeaderInvalid("chunked, void", true);
    }


    private void doTestTEHeaderInvalid(String headerValue, boolean badRequest) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add servlet
        Tomcat.addServlet(ctx, "TesterServlet", new TesterServlet(false));
        ctx.addServletMappingDecoded("/foo", "TesterServlet");

        tomcat.start();

        String request = "GET /foo HTTP/1.1" + SimpleHttpClient.CRLF + "Host: localhost:" + getPort() +
                SimpleHttpClient.CRLF + "Transfer-Encoding: " + headerValue + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF;

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest(false);

        if (badRequest) {
            Assert.assertTrue(client.isResponse400());
        } else {
            Assert.assertTrue(client.isResponse501());
        }
    }


    @Test
    public void testWithTEChunkedHttp10() throws Exception {

        getTomcatInstanceTestWebapp(false, true);

        String request = "POST /test/echo-params.jsp HTTP/1.0" + SimpleHttpClient.CRLF + "Host: any" +
                SimpleHttpClient.CRLF + "Transfer-encoding: chunked" + SimpleHttpClient.CRLF +
                "Content-Type: application/x-www-form-urlencoded" + SimpleHttpClient.CRLF + "Connection: close" +
                SimpleHttpClient.CRLF + SimpleHttpClient.CRLF + "9" + SimpleHttpClient.CRLF + "test=data" +
                SimpleHttpClient.CRLF + "0" + SimpleHttpClient.CRLF + SimpleHttpClient.CRLF;

        Client client = new Client(getPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest();
        Assert.assertTrue(client.isResponse200());
        Assert.assertTrue(client.getResponseBody().contains("test - data"));
    }


    @Test
    public void test100ContinueWithNoAck() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        final Connector connector = tomcat.getConnector();
        connector.setProperty("continueResponseTiming", "onRead");

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add servlet
        Tomcat.addServlet(ctx, "TestPostNoReadServlet", new TestPostNoReadServlet());
        ctx.addServletMappingDecoded("/foo", "TestPostNoReadServlet");

        tomcat.start();

        String request = "POST /foo HTTP/1.1" + SimpleHttpClient.CRLF + "Host: localhost:" + getPort() +
                SimpleHttpClient.CRLF + "Expect: 100-continue" + SimpleHttpClient.CRLF + "Content-Length: 10" +
                SimpleHttpClient.CRLF + SimpleHttpClient.CRLF + "0123456789";

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest(false);

        Assert.assertTrue(client.isResponse200());

        if (client.getResponseHeaders().contains("Connection: close")) {
            client.connect();
        }

        client.processRequest(false);

        Assert.assertTrue(client.isResponse200());

    }


    @Test
    public void testConnect() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        String request = "CONNECT example.local HTTP/1.1" + SimpleHttpClient.CRLF + "Host: example.local" +
                SimpleHttpClient.CRLF + SimpleHttpClient.CRLF;

        Client client = new Client(getPort());
        client.setRequest(new String[] { request });

        client.connect();
        client.processRequest();
        Assert.assertTrue(client.isResponse501());
    }


    private static class TestPostNoReadServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        }
    }
}
