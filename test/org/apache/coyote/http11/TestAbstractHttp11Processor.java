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

import java.io.File;
import java.io.IOException;
import java.nio.CharBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestAbstractHttp11Processor extends TomcatBaseTest {

    @Test
    public void testWithTEVoid() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Use the normal Tomcat ROOT context
        File root = new File("test/webapp-3.0");
        tomcat.addWebapp("", root.getAbsolutePath());

        tomcat.start();

        String request =
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: void" + SimpleHttpClient.CRLF +
            "Content-Length: 9" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "test=data";

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();
        assertTrue(client.isResponse501());
    }

    @Test
    public void testWithTEBuffered() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Use the normal Tomcat ROOT context
        File root = new File("test/webapp-3.0");
        tomcat.addWebapp("", root.getAbsolutePath());

        tomcat.start();

        String request =
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: buffered" + SimpleHttpClient.CRLF +
            "Content-Length: 9" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "test=data";

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();
        assertTrue(client.isResponse501());
    }


    @Test
    public void testWithTEIdentity() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Use the normal Tomcat ROOT context
        File root = new File("test/webapp-3.0");
        tomcat.addWebapp("", root.getAbsolutePath());

        tomcat.start();

        String request =
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: identity" + SimpleHttpClient.CRLF +
            "Content-Length: 9" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
            "Connection: close" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "test=data";

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();
        assertTrue(client.isResponse200());
        assertTrue(client.getResponseBody().contains("test - data"));
    }


    @Test
    public void testWithTESavedRequest() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Use the normal Tomcat ROOT context
        File root = new File("test/webapp-3.0");
        tomcat.addWebapp("", root.getAbsolutePath());

        tomcat.start();

        String request =
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: savedrequest" + SimpleHttpClient.CRLF +
            "Content-Length: 9" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "test=data";

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();
        assertTrue(client.isResponse501());
    }


    @Test
    public void testWithTEUnsupported() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Use the normal Tomcat ROOT context
        File root = new File("test/webapp-3.0");
        tomcat.addWebapp("", root.getAbsolutePath());

        tomcat.start();

        String request =
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: unsupported" + SimpleHttpClient.CRLF +
            "Content-Length: 9" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "test=data";

        Client client = new Client(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();
        assertTrue(client.isResponse501());
    }


    @Test
    public void testPipelining() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        Context ctxt = tomcat.addContext("",
                System.getProperty("java.io.tmpdir"));

        // Add protected servlet
        Tomcat.addServlet(ctxt, "TesterServlet", new TesterServlet());
        ctxt.addServletMapping("/foo", "TesterServlet");

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

        // Must have a real docBase - just use temp
        Context ctxt = tomcat.addContext("",
                System.getProperty("java.io.tmpdir"));

        Tomcat.addServlet(ctxt, "NoContentLengthFlushingServlet",
                new NoContentLengthFlushingServlet());
        ctxt.addServletMapping("/test", "NoContentLengthFlushingServlet");

        tomcat.start();

        ByteChunk responseBody = new ByteChunk();
        Map<String,List<String>> responseHeaders =
                new HashMap<String,List<String>>();
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

        // Must have a real docBase - just use temp
        Context ctxt = tomcat.addContext("",
                System.getProperty("java.io.tmpdir"));

        Tomcat.addServlet(ctxt, "NoContentLengthConnectionCloseFlushingServlet",
                new NoContentLengthConnectionCloseFlushingServlet());
        ctxt.addServletMapping("/test",
                "NoContentLengthConnectionCloseFlushingServlet");

        tomcat.start();

        ByteChunk responseBody = new ByteChunk();
        Map<String,List<String>> responseHeaders =
                new HashMap<String,List<String>>();
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

        // Must have a real docBase - just use temp
        Context ctxt = tomcat.addContext("",
                System.getProperty("java.io.tmpdir"));

        Tomcat.addServlet(ctxt, "LargeHeaderServlet",
                new LargeHeaderServlet(flush));
        ctxt.addServletMapping("/test", "LargeHeaderServlet");

        tomcat.start();

        ByteChunk responseBody = new ByteChunk();
        Map<String,List<String>> responseHeaders =
                new HashMap<String,List<String>>();
        int rc = getUrl("http://localhost:" + getPort() + "/test", responseBody,
                responseHeaders);

        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);
        if (responseBody.getLength() > 0) {
            // It will be >0 if the standard error page handlign has been
            // triggered
            assertFalse(responseBody.toString().contains("FAIL"));
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
}
