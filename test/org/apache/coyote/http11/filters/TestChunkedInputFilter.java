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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestChunkedInputFilter extends TomcatBaseTest {

    private static final int EXT_SIZE_LIMIT = 10;

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
        doTestChunkingCRLF(true, true, false, true, true, false);
    }

    @Test
    public void testSecondTrailingHeadersCRLF() throws Exception {
        doTestChunkingCRLF(true, true, true, true, true, true);
    }

    @Test
    public void testSecondTrailingHeadersLF() throws Exception {
        doTestChunkingCRLF(true, true, true, false, true, false);
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

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Configure allowed trailer headers
        Assert.assertTrue(tomcat.getConnector().setProperty("allowedTrailerHeaders", "x-trailer1,x-trailer2"));

        EchoHeaderServlet servlet = new EchoHeaderServlet(expectPass);
        Tomcat.addServlet(ctx, "servlet", servlet);
        ctx.addServletMappingDecoded("/", "servlet");

        tomcat.start();

        String[] request = new String[]{
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: chunked" + SimpleHttpClient.CRLF +
            SimpleHttpClient.HTTP_HEADER_CONTENT_TYPE_FORM_URL_ENCODING +
            "Connection: close" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "3" + (chunkHeaderUsesCRLF ? SimpleHttpClient.CRLF : SimpleHttpClient.LF) +
            "a=0" + (chunkUsesCRLF ? SimpleHttpClient.CRLF : SimpleHttpClient.LF) +
            "4" + SimpleHttpClient.CRLF +
            "&b=1" + SimpleHttpClient.CRLF +
            "0" + SimpleHttpClient.CRLF +
            "x-trailer1: Test", "Value1" +
            (firstheaderUsesCRLF ? SimpleHttpClient.CRLF : SimpleHttpClient.LF) +
            "x-trailer2: TestValue2" +
            (secondheaderUsesCRLF ? SimpleHttpClient.CRLF : SimpleHttpClient.LF) +
            (endUsesCRLF ? SimpleHttpClient.CRLF : SimpleHttpClient.LF) };

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
            Assert.assertTrue(client.isResponse200());
            Assert.assertEquals("nullnull7TestValue1TestValue2",
                    client.getResponseBody());
            Assert.assertNull(processException);
            Assert.assertFalse(servlet.getExceptionDuringRead());
        } else {
            if (processException == null) {
                Assert.assertTrue(client.getResponseLine(), client.isResponse500());
            } else {
                // Use fall-back for checking the error occurred
                Assert.assertTrue(servlet.getExceptionDuringRead());
            }
        }
    }


    @Test
    public void testTrailingHeadersSizeLimitBelowLimit() throws Exception {
        doTestTrailingHeadersSizeLimit(17, "x-trailer: Test", false);
    }


    @Test
    public void testTrailingHeadersSizeLimitAtLimit() throws Exception {
        doTestTrailingHeadersSizeLimit(18, "x-trailer: Test", false);
    }


    @Test
    public void testTrailingHeadersSizeLimitAboveLimit() throws Exception {
        doTestTrailingHeadersSizeLimit(19, "x-trailer: Test", true);
    }


    /*
     * This test uses the fact that the header is simply concatenated to insert a pipelined request. The pipelined
     * request should not trigger the trailing header size limit. Note that 19 is just enough for the first request.
     */
    @Test
    public void testTrailingHeadersSizeLimitPipelining() throws Exception {
        doTestTrailingHeadersSizeLimit(19,
                "x-trailer: Test" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF +
                "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: any" + SimpleHttpClient.CRLF +
                "Transfer-encoding: chunked" + SimpleHttpClient.CRLF +
                SimpleHttpClient.HTTP_HEADER_CONTENT_TYPE_FORM_URL_ENCODING +
                "Connection: close" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF +
                "3" + SimpleHttpClient.CRLF +
                "a=0" + SimpleHttpClient.CRLF +
                "4" + SimpleHttpClient.CRLF +
                "&b=1" + SimpleHttpClient.CRLF +
                "0" + SimpleHttpClient.CRLF +
                "x-trailer: Test",
                true);
    }


    /*
     * Since limit includes CRLF at end of trailer and final CRLF
     */
    private void doTestTrailingHeadersSizeLimit(int trailerSizeLimit, String trailerHeader, boolean pass) throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "servlet", new EchoHeaderServlet(false));
        ctx.addServletMappingDecoded("/", "servlet");

        // Limit the size of the trailing header
        Assert.assertTrue(tomcat.getConnector().setProperty("maxTrailerSize", Integer.toString(trailerSizeLimit)));
        tomcat.start();

        String[] request = new String[]{
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: chunked" + SimpleHttpClient.CRLF +
            SimpleHttpClient.HTTP_HEADER_CONTENT_TYPE_FORM_URL_ENCODING +
            "Connection: close" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "3" + SimpleHttpClient.CRLF +
            "a=0" + SimpleHttpClient.CRLF +
            "4" + SimpleHttpClient.CRLF +
            "&b=1" + SimpleHttpClient.CRLF +
            "0" + SimpleHttpClient.CRLF +
            trailerHeader + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF };

        TrailerClient client =
                new TrailerClient(tomcat.getConnector().getLocalPort());
        client.setRequest(request);

        client.connect();
        client.processRequest();
        if (pass) {
            Assert.assertTrue(client.isResponse200());
        } else {
            Assert.assertTrue(client.isResponse500());
        }
    }


    @Test
    public void testExtensionSizeLimitOneBelow() throws Exception {
        doTestExtensionSizeLimit(EXT_SIZE_LIMIT - 1, true);
    }


    @Test
    public void testExtensionSizeLimitExact() throws Exception {
        doTestExtensionSizeLimit(EXT_SIZE_LIMIT, true);
    }


    @Test
    public void testExtensionSizeLimitOneOver() throws Exception {
        doTestExtensionSizeLimit(EXT_SIZE_LIMIT + 1, false);
    }


    private void doTestExtensionSizeLimit(int len, boolean ok) throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        Assert.assertTrue(tomcat.getConnector().setProperty(
                "maxExtensionSize", Integer.toString(EXT_SIZE_LIMIT)));

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "servlet", new EchoHeaderServlet(ok));
        ctx.addServletMappingDecoded("/", "servlet");

        tomcat.start();

        String extName = ";foo=";
        StringBuilder extValue = new StringBuilder(len);
        for (int i = 0; i < (len - extName.length()); i++) {
            extValue.append('x');
        }

        String[] request = new String[]{
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: chunked" + SimpleHttpClient.CRLF +
            SimpleHttpClient.HTTP_HEADER_CONTENT_TYPE_FORM_URL_ENCODING +
            "Connection: close" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "3" + extName + extValue.toString() + SimpleHttpClient.CRLF +
            "a=0" + SimpleHttpClient.CRLF +
            "4" + SimpleHttpClient.CRLF +
            "&b=1" + SimpleHttpClient.CRLF +
            "0" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF };

        TrailerClient client =
                new TrailerClient(tomcat.getConnector().getLocalPort());
        client.setRequest(request);

        client.connect();
        client.processRequest();

        if (ok) {
            Assert.assertTrue(client.isResponse200());
        } else {
            Assert.assertTrue(client.isResponse500());
        }
    }

    @Test
    public void testNoTrailingHeaders() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "servlet", new EchoHeaderServlet(true));
        ctx.addServletMappingDecoded("/", "servlet");

        tomcat.start();

        String request =
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: chunked" + SimpleHttpClient.CRLF +
            SimpleHttpClient.HTTP_HEADER_CONTENT_TYPE_FORM_URL_ENCODING +
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
        Assert.assertEquals("nullnull7nullnull", client.getResponseBody());
    }

    @Test
    public void testChunkSizeZero() throws Exception {
        doTestChunkSize(true, true, "", 10, 0);
    }

    @Test
    public void testChunkSizeAbsent() throws Exception {
        doTestChunkSize(false, false, SimpleHttpClient.CRLF, 10, 0);
    }

    @Test
    public void testChunkSizeTwentyFive() throws Exception {
        doTestChunkSize(true, true, "19" + SimpleHttpClient.CRLF
                + "Hello World!Hello World!!" + SimpleHttpClient.CRLF, 40, 25);
    }

    @Test
    public void testChunkSizeEightDigit() throws Exception {
        doTestChunkSize(true, true, "0000000C" + SimpleHttpClient.CRLF
                + "Hello World!" + SimpleHttpClient.CRLF, 20, 12);
    }

    @Test
    public void testChunkSizeNineDigit() throws Exception {
        doTestChunkSize(false, false, "00000000C" + SimpleHttpClient.CRLF
                + "Hello World!" + SimpleHttpClient.CRLF, 20, 12);
    }

    @Test
    public void testChunkSizeLong() throws Exception {
        doTestChunkSize(true, false, "7fFFffFF" + SimpleHttpClient.CRLF
                + "Hello World!" + SimpleHttpClient.CRLF, 10, 10);
    }

    @Test
    public void testChunkSizeIntegerMinValue() throws Exception {
        doTestChunkSize(false, false, "80000000" + SimpleHttpClient.CRLF
                + "Hello World!" + SimpleHttpClient.CRLF, 10, 10);
    }

    @Test
    public void testChunkSizeMinusOne() throws Exception {
        doTestChunkSize(false, false, "ffffffff" + SimpleHttpClient.CRLF
                + "Hello World!" + SimpleHttpClient.CRLF, 10, 10);
    }

    /**
     * @param expectPass
     *            If the servlet is expected to process the request
     * @param expectReadWholeBody
     *            If the servlet is expected to fully read the body and reliably
     *            deliver a response
     * @param chunks
     *            Text of chunks
     * @param readLimit
     *            Do not read more than this many bytes
     * @param expectReadCount
     *            Expected count of read bytes
     * @throws Exception
     *             Unexpected
     */
    private void doTestChunkSize(boolean expectPass,
            boolean expectReadWholeBody, String chunks, int readLimit,
            int expectReadCount) throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        BodyReadServlet servlet = new BodyReadServlet(expectPass, readLimit);
        Tomcat.addServlet(ctx, "servlet", servlet);
        ctx.addServletMappingDecoded("/", "servlet");

        tomcat.start();

        String request = "POST /echo-params.jsp HTTP/1.1"
                + SimpleHttpClient.CRLF + "Host: any" + SimpleHttpClient.CRLF
                + "Transfer-encoding: chunked" + SimpleHttpClient.CRLF
                + "Content-Type: text/plain" + SimpleHttpClient.CRLF;
        if (expectPass) {
            request += "Connection: close" + SimpleHttpClient.CRLF;
        }
        request += SimpleHttpClient.CRLF + chunks + "0" + SimpleHttpClient.CRLF
                + SimpleHttpClient.CRLF;

        TrailerClient client = new TrailerClient(tomcat.getConnector().getLocalPort());
        // Need to use the content length here as variations in Connector and
        // JVM+OS behaviour mean that in some circumstances the client may see
        // an IOException rather than the response body when the server closes
        // the connection.
        client.setUseContentLength(true);
        client.setRequest(new String[] { request });

        Exception processException = null;
        client.connect();
        try {
            client.processRequest();
            client.disconnect();
        } catch (Exception e) {
            // Socket was probably closed before client had a chance to read
            // response
            processException = e;
        }
        if (expectPass) {
            if (expectReadWholeBody) {
                Assert.assertNull(processException);
            }
            if (processException == null) {
                Assert.assertTrue(client.getResponseLine(), client.isResponse200());
                Assert.assertEquals(String.valueOf(expectReadCount),
                        client.getResponseBody());
            }
            Assert.assertEquals(expectReadCount, servlet.getCountRead());
        } else {
            if (processException == null) {
                Assert.assertTrue(client.getResponseLine(), client.isResponse500());
            }
            Assert.assertEquals(0, servlet.getCountRead());
            Assert.assertTrue(servlet.getExceptionDuringRead());
        }
    }


    @Test
    public void testTrailerHeaderNameNotTokenThrowException() throws Exception {
        doTestTrailerHeaderNameNotToken(false);
    }

    @Test
    public void testTrailerHeaderNameNotTokenSwallowException() throws Exception {
        doTestTrailerHeaderNameNotToken(true);
    }

    private void doTestTrailerHeaderNameNotToken(boolean swallowException) throws Exception {

        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "servlet", new SwallowBodyServlet(swallowException));
        ctx.addServletMappingDecoded("/", "servlet");

        tomcat.start();

        String[] request = new String[]{
            "POST / HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: localhost" + SimpleHttpClient.CRLF +
            "Transfer-encoding: chunked" + SimpleHttpClient.CRLF +
            SimpleHttpClient.HTTP_HEADER_CONTENT_TYPE_FORM_URL_ENCODING +
            "Connection: close" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "3" + SimpleHttpClient.CRLF +
            "a=0" + SimpleHttpClient.CRLF +
            "4" + SimpleHttpClient.CRLF +
            "&b=1" + SimpleHttpClient.CRLF +
            "0" + SimpleHttpClient.CRLF +
            "x@trailer: Test" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF };

        TrailerClient client = new TrailerClient(tomcat.getConnector().getLocalPort());
        client.setRequest(request);

        client.connect();
        client.processRequest();
        // Expected to fail because of invalid trailer header name
        Assert.assertTrue(client.getResponseLine(), client.isResponse400());
    }

    private static class SwallowBodyServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        private final boolean swallowException;

        SwallowBodyServlet(boolean swallowException) {
            this.swallowException = swallowException;
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            PrintWriter pw = resp.getWriter();

            // Read the body
            InputStream is = req.getInputStream();
            try {
                while (is.read() > -1) {
                }
                pw.write("OK");
            } catch (IOException ioe) {
                if (!swallowException) {
                    throw ioe;
                }
            }
        }
    }

    private static class EchoHeaderServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        private boolean exceptionDuringRead = false;

        private final boolean expectPass;

        EchoHeaderServlet(boolean expectPass) {
            this.expectPass = expectPass;
        }

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
                if (!expectPass) { // as expected
                    log(ioe.toString());
                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    return;
                }
                throw ioe;
            }

            pw.write(Integer.toString(count));

            // Headers should be visible now
            dumpHeader("x-trailer1", req, pw);
            dumpHeader("x-trailer2", req, pw);
        }

        public boolean getExceptionDuringRead() {
            return exceptionDuringRead;
        }

        private void dumpHeader(String headerName, HttpServletRequest req,
                PrintWriter pw) {
            String value = req.getTrailerFields().get(headerName);
            if (value == null) {
                value = "null";
            }
            pw.write(value);
        }
    }

    private static class BodyReadServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        private boolean exceptionDuringRead = false;
        private int countRead = 0;
        private final boolean expectPass;
        private final int readLimit;

        BodyReadServlet(boolean expectPass, int readLimit) {
            this.expectPass = expectPass;
            this.readLimit = readLimit;
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            PrintWriter pw = resp.getWriter();

            // Read the body - quick and dirty
            InputStream is = req.getInputStream();
            try {
                while (is.read() > -1 && countRead < readLimit) {
                    countRead++;
                }
            } catch (IOException ioe) {
                exceptionDuringRead = true;
                if (!expectPass) { // as expected
                    log(ioe.toString());
                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    return;
                }
                throw ioe;
            }

            pw.write(Integer.toString(countRead));
        }

        public boolean getExceptionDuringRead() {
            return exceptionDuringRead;
        }

        public int getCountRead() {
            return countRead;
        }
    }

    private static class TrailerClient extends SimpleHttpClient {

        TrailerClient(int port) {
            setPort(port);
        }

        @Override
        public boolean isResponseBodyOK() {
            return getResponseBody().contains("TestTestTest");
        }
    }


    @Test
    public void doTestIncompleteChunkedBody() throws Exception {

        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "servlet", new SwallowBodyServlet(false));
        ctx.addServletMappingDecoded("/", "servlet");

        tomcat.start();

        String[] request = new String[]{
            "POST / HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: localhost" + SimpleHttpClient.CRLF +
            "Transfer-encoding: chunked" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "3" + SimpleHttpClient.CRLF };

        TrailerClient client = new TrailerClient(tomcat.getConnector().getLocalPort());
        client.setUseContentLength(true);

        client.setRequest(request);
        client.connect();
        try {
            client.processRequest();
        } catch (IOException ioe) {
            // Ignore - Triggered by connection being dropped after error
        }
        // NIO2 may (will?) return null here
        String responseLine = client.getResponseLine();
        if (responseLine == null) {
            // 400 response not read(/written?) before connection was dropped.
        } else {
            Assert.assertTrue(client.getResponseLine(), client.isResponse400());
        }
    }


    @Test
    public void doTestMaxSwallowSizeBelow() throws Exception {
        doTestMaxSwallowSize(1000, true);
    }


    @Test
    public void doTestMaxSwallowSizeAbove() throws Exception {
        doTestMaxSwallowSize(10, false);
    }


    private void doTestMaxSwallowSize(int maxSwallowSize, boolean pass) throws Exception {

        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        tomcat.getConnector().setProperty("connectionTimeout", "300000");
        // Reduce limits to facilitate testing
        tomcat.getConnector().setProperty("maxSwallowSize", Integer.toString(maxSwallowSize));

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "servlet", new TesterServlet(false));
        ctx.addServletMappingDecoded("/", "servlet");

        tomcat.start();

        String[] request = new String[]{
            "GET / HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: localhost" + SimpleHttpClient.CRLF +
            "Transfer-encoding: chunked" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "20" + SimpleHttpClient.CRLF +
            "01234567890123456789012345678901" + SimpleHttpClient.CRLF +
            "0" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF };

        TrailerClient client = new TrailerClient(tomcat.getConnector().getLocalPort());
        client.setUseContentLength(true);

        client.setRequest(request);
        client.connect();
        client.sendRequest();
        client.readResponse(true);

        // Response is committed before connection is closed.
        Assert.assertTrue(client.getResponseLine(), client.isResponse200());

        // Repeat request - should fail
        client.resetResponse();
        client.sendRequest();
        try {
            client.readResponse(true);
        } catch (IOException ioe) {
            // Ignore - in case the read fails due to a closed connection
        }
        if (pass) {
            Assert.assertTrue(client.getResponseLine(), client.isResponse200());
        } else {
            // Connection reset
            Assert.assertNull(client.getResponseLine());
        }
    }


    private static class BodyReadLineServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            int lineCount = 0;
            int pauseCount = 0;

            // Read the body one line at a time. There should be ~1s between reads.
            try (InputStream is = req.getInputStream();
                    InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                    BufferedReader br = new BufferedReader(isr)) {
                long lastRead = 0;
                while (br.readLine() != null) {
                    long thisRead = System.nanoTime();
                    if (lineCount > 0) {
                       /*
                        * After the first line, look for a pause of at least 800ms between reads.
                        */
                       if ((thisRead - lastRead) > TimeUnit.MILLISECONDS.toNanos(800)) {
                           pauseCount++;
                       }
                    }
                    lastRead = thisRead;
                    lineCount++;
                }
            }

            resp.setContentType("text/plain");
            PrintWriter pw = resp.getWriter();
            pw.write(Integer.toString(lineCount) + "," + Integer.toString(pauseCount));
        }
    }


    private static class NonBlockingReadLineServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        int lineCount = 0;
        int pauseCount = 0;
        long lastRead = 0;

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            final AsyncContext ctx = req.startAsync();
            ServletInputStream is = req.getInputStream();
            is.setReadListener(new ReadListener() {
                @Override
                public void onDataAvailable() throws IOException {
                    byte[] buf = new byte[1024];
                    do {
                        int n = is.read(buf);
                        if (n < 0) {
                            break;
                        } else if (n > 0) {
                            String line = new String(buf, 0, n, StandardCharsets.UTF_8);
                            Assert.assertTrue(line.length() > 0);
                            long thisRead = System.nanoTime();
                            if (lineCount > 0) {
                                /*
                                 * After the first line, look for a pause of at least 800ms between reads.
                                 */
                                if ((thisRead - lastRead) > TimeUnit.MILLISECONDS.toNanos(800)) {
                                    pauseCount++;
                                }
                            }
                            lastRead = thisRead;
                            lineCount++;
                        }
                    } while (is.isReady());
                }

                @Override
                public void onAllDataRead() throws IOException {
                    resp.setContentType("text/plain");
                    PrintWriter pw = resp.getWriter();
                    pw.write(Integer.toString(lineCount) + "," + Integer.toString(pauseCount));
                    ctx.complete();
                }

                @Override
                public void onError(Throwable throwable) {
                    throwable.printStackTrace();
                }

            });

        }
    }


    private static class ReadLineClient extends SimpleHttpClient {

        ReadLineClient(int port) {
            setPort(port);
        }

        @Override
        public boolean isResponseBodyOK() {
            return getResponseBody().equals("5");
        }
    }


    @Test
    public void testChunkedSplitWithReader() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        BodyReadLineServlet servlet = new BodyReadLineServlet();
        Tomcat.addServlet(ctx, "servlet", servlet);
        ctx.addServletMappingDecoded("/test", "servlet");

        tomcat.getConnector().setProperty("connectionTimeout", "300000");
        tomcat.start();

        String[] request = new String[]{
            "POST /test HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: chunked" + SimpleHttpClient.CRLF +
            SimpleHttpClient.HTTP_HEADER_CONTENT_TYPE_FORM_URL_ENCODING +
            "Connection: close" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "7" + SimpleHttpClient.CRLF +
            "DATA01\n", SimpleHttpClient.CRLF +
            "7", SimpleHttpClient.CRLF +
            "DATA02\n" + SimpleHttpClient.CRLF,
            "7" + SimpleHttpClient.CRLF +
            // Split the CRLF between writes
            "DATA03\n" + SimpleHttpClient.CR,
            SimpleHttpClient.LF +
            "7" + SimpleHttpClient.CRLF +
            "DATA04\n", SimpleHttpClient.CRLF +
            "13" + SimpleHttpClient.CRLF,
            "DATA05DATA05DATA05\n" + SimpleHttpClient.CRLF +
            "0" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF};

        ReadLineClient client = new ReadLineClient(tomcat.getConnector().getLocalPort());
        client.setRequest(request);

        client.connect(300000,300000);
        client.processRequest();
        Assert.assertTrue(client.isResponse200());
        /*
         * Output is "<lines read>,<pauses observer>" so there should be 5 lines read with a pause between each.
         */
        Assert.assertEquals("5,4", client.getResponseBody());
    }


    @Test
    public void testChunkedSplitWithNonBlocking() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        NonBlockingReadLineServlet servlet = new NonBlockingReadLineServlet();
        Wrapper wrapper = Tomcat.addServlet(ctx, "servlet", servlet);
        wrapper.setAsyncSupported(true);
        ctx.addServletMappingDecoded("/test", "servlet");

        tomcat.getConnector().setProperty("connectionTimeout", "300000");
        tomcat.start();

        String[] request = new String[]{
            "POST /test HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: chunked" + SimpleHttpClient.CRLF +
            SimpleHttpClient.HTTP_HEADER_CONTENT_TYPE_FORM_URL_ENCODING +
            "Connection: close" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "7" + SimpleHttpClient.CRLF +
            "DATA01\n", SimpleHttpClient.CRLF +
            "7", SimpleHttpClient.CRLF +
            "DATA02\n" + SimpleHttpClient.CRLF,
            "7" + SimpleHttpClient.CRLF +
            // Split the CRLF between writes
            "DATA03\n" + SimpleHttpClient.CR,
            SimpleHttpClient.LF +
            "7" + SimpleHttpClient.CRLF +
            "DATA04\n", SimpleHttpClient.CRLF +
            "13" + SimpleHttpClient.CRLF,
            "DATA05DATA05DATA05\n" + SimpleHttpClient.CRLF +
            "0" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF};

        ReadLineClient client = new ReadLineClient(tomcat.getConnector().getLocalPort());
        client.setRequest(request);

        client.connect(300000,300000);
        client.processRequest();
        Assert.assertTrue(client.isResponse200());
        /*
         * Output is "<lines read>,<pauses observer>" so there should be 5 lines read with a pause between each.
         */
        Assert.assertEquals("5,4", client.getResponseBody());
    }
}
