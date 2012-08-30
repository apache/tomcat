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

package org.apache.coyote.http11.filters;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestChunkedInputFilter extends TomcatBaseTest {

    private static final String LF = "\n";

    @Test
    public void testChunkHeaderCRLF() throws Exception {
        doTestChunkingCRLF(true, true, true, true, true, true);
    }

    @Test
    public void testChunkHeaderLF() throws Exception {
        doTestChunkingCRLF(false, true, true, true, true, false);
    }

    @Test
    public void testChunkCRLF() throws Exception {
        doTestChunkingCRLF(true, true, true, true, true, true);
    }

    @Test
    public void testChunkLF() throws Exception {
        doTestChunkingCRLF(true, false, true, true, true, false);
    }

    @Test
    public void testFirstTrailingHeadersCRLF() throws Exception {
        doTestChunkingCRLF(true, true, true, true, true, true);
    }

    @Test
    public void testFirstTrailingHeadersLF() throws Exception {
        doTestChunkingCRLF(true, true, false, true, true, true);
    }

    @Test
    public void testSecondTrailingHeadersCRLF() throws Exception {
        doTestChunkingCRLF(true, true, true, true, true, true);
    }

    @Test
    public void testSecondTrailingHeadersLF() throws Exception {
        doTestChunkingCRLF(true, true, true, false, true, true);
    }

    @Test
    public void testEndCRLF() throws Exception {
        doTestChunkingCRLF(true, true, true, true, true, true);
    }

    @Test
    public void testEndLF() throws Exception {
        doTestChunkingCRLF(true, true, true, true, false, false);
    }

    private void doTestChunkingCRLF(boolean chunkHeaderUsesCRLF,
            boolean chunkUsesCRLF, boolean firstheaderUsesCRLF,
            boolean secondheaderUsesCRLF, boolean endUsesCRLF,
            boolean expectPass) throws Exception {

        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        EchoHeaderServlet servlet = new EchoHeaderServlet();
        Tomcat.addServlet(ctx, "servlet", servlet);
        ctx.addServletMapping("/", "servlet");

        tomcat.start();

        String[] request = new String[]{
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: chunked" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
            "Connection: close" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "3" + (chunkHeaderUsesCRLF ? SimpleHttpClient.CRLF : LF) +
            "a=0" + (chunkUsesCRLF ? SimpleHttpClient.CRLF : LF) +
            "4" + SimpleHttpClient.CRLF +
            "&b=1" + SimpleHttpClient.CRLF +
            "0" + SimpleHttpClient.CRLF +
            "x-trailer1: Test", "Value1" +
            (firstheaderUsesCRLF ? SimpleHttpClient.CRLF : LF) +
            "x-trailer2: TestValue2" +
            (secondheaderUsesCRLF ? SimpleHttpClient.CRLF : LF) +
            (endUsesCRLF ? SimpleHttpClient.CRLF : LF) };

        TrailerClient client =
                new TrailerClient(tomcat.getConnector().getLocalPort());
        client.setRequest(request);

        client.connect();
        Exception processException = null;
        try {
            client.processRequest();
        } catch (Exception e) {
            // Socket was probably closed before client had a chance to read
            // response
            processException = e;
        }

        if (expectPass) {
            assertTrue(client.isResponse200());
            assertEquals("nullnull7TestValue1TestValue2",
                    client.getResponseBody());
            assertNull(processException);
            assertFalse(servlet.getExceptionDuringRead());
        } else {
            if (processException == null) {
                assertTrue(client.getResponseLine(), client.isResponse500());
            } else {
                // Use fall-back for checking the error occurred
                assertTrue(servlet.getExceptionDuringRead());
            }
        }
    }

    @Test
    public void testTrailingHeadersSizeLimit() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        Tomcat.addServlet(ctx, "servlet", new EchoHeaderServlet());
        ctx.addServletMapping("/", "servlet");

        // Limit the size of the trailing header
        tomcat.getConnector().setProperty("maxTrailerSize", "10");
        tomcat.start();

        String[] request = new String[]{
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: chunked" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
            "Connection: close" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "3" + SimpleHttpClient.CRLF +
            "a=0" + SimpleHttpClient.CRLF +
            "4" + SimpleHttpClient.CRLF +
            "&b=1" + SimpleHttpClient.CRLF +
            "0" + SimpleHttpClient.CRLF +
            "x-trailer: Test" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF };

        TrailerClient client =
                new TrailerClient(tomcat.getConnector().getLocalPort());
        client.setRequest(request);

        client.connect();
        client.processRequest();
        // Expected to fail because the trailers are longer
        // than the default limit of 8Kb
        assertTrue(client.isResponse500());
    }

    @Test
    public void testNoTrailingHeaders() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        Tomcat.addServlet(ctx, "servlet", new EchoHeaderServlet());
        ctx.addServletMapping("/", "servlet");

        tomcat.start();

        String request =
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: chunked" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
            "Connection: close" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "3" + SimpleHttpClient.CRLF +
            "a=0" + SimpleHttpClient.CRLF +
            "4" + SimpleHttpClient.CRLF +
            "&b=1" + SimpleHttpClient.CRLF +
            "0" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF;

        TrailerClient client =
                new TrailerClient(tomcat.getConnector().getLocalPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();
        assertEquals("nullnull7nullnull", client.getResponseBody());
    }

    private static class EchoHeaderServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        private boolean exceptionDuringRead = false;

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            PrintWriter pw = resp.getWriter();
            // Headers not visible yet, body not processed
            dumpHeader("x-trailer1", req, pw);
            dumpHeader("x-trailer2", req, pw);

            // Read the body - quick and dirty
            InputStream is = req.getInputStream();
            int count = 0;
            try {
                while (is.read() > -1) {
                    count++;
                }
            } catch (IOException ioe) {
                exceptionDuringRead = true;
                throw ioe;
            }

            pw.write(Integer.valueOf(count).toString());

            // Headers should be visible now
            dumpHeader("x-trailer1", req, pw);
            dumpHeader("x-trailer2", req, pw);
        }

        public boolean getExceptionDuringRead() {
            return exceptionDuringRead;
        }

        private void dumpHeader(String headerName, HttpServletRequest req,
                PrintWriter pw) {
            String value = req.getHeader(headerName);
            if (value == null) {
                value = "null";
            }
            pw.write(value);
        }
    }

    private static class TrailerClient extends SimpleHttpClient {

        public TrailerClient(int port) {
            setPort(port);
        }

        @Override
        public boolean isResponseBodyOK() {
            return getResponseBody().contains("TestTestTest");
        }
    }
}
