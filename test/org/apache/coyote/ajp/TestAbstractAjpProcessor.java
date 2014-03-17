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
package org.apache.coyote.ajp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestAbstractAjpProcessor extends TomcatBaseTest {

    @Override
    protected String getProtocol() {
        /*
         * The tests are all setup for HTTP so need to convert the protocol
         * values to AJP.
         */
        // Has a protocol been specified
        String protocol = System.getProperty("tomcat.test.protocol");

        // Use BIO by default
        if (protocol == null) {
            protocol = "org.apache.coyote.ajp.AjpProtocol";
        } else if (protocol.contains("Nio2")) {
            protocol = "org.apache.coyote.ajp.AjpNio2Protocol";
        } else if (protocol.contains("Nio")) {
            protocol = "org.apache.coyote.ajp.AjpNioProtocol";
        } else if (protocol.contains("Apr")) {
            protocol = "org.apache.coyote.ajp.AjpAprProtocol";
        } else {
            protocol = "org.apache.coyote.ajp.AjpProtocol";
        }

        return protocol;
    }

    @Test
    public void testKeepAlive() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.getConnector().setProperty("connectionTimeout", "-1");
        tomcat.start();

        // Must have a real docBase - just use temp
        Context ctx = tomcat.addContext("", System.getProperty("java.io.tmpdir"));
        Tomcat.addServlet(ctx, "helloWorld", new HelloWorldServlet());
        ctx.addServletMapping("/", "helloWorld");

        SimpleAjpClient ajpClient = new SimpleAjpClient();

        ajpClient.setPort(getPort());

        ajpClient.connect();

        validateCpong(ajpClient.cping());

        TesterAjpMessage forwardMessage = ajpClient.createForwardMessage("/");
        // Complete the message - no extra headers required.
        forwardMessage.end();

        // Two requests
        for (int i = 0; i < 2; i++) {
            TesterAjpMessage responseHeaders = ajpClient.sendMessage(forwardMessage);
            // Expect 3 packets: headers, body, end
            validateResponseHeaders(responseHeaders, 200);
            TesterAjpMessage responseBody = ajpClient.readMessage();
            validateResponseBody(responseBody, HelloWorldServlet.RESPONSE_TEXT);
            validateResponseEnd(ajpClient.readMessage(), true);

            // Give connections plenty of time to time out
            Thread.sleep(2000);

            // Double check the connection is still open
            validateCpong(ajpClient.cping());
        }

        ajpClient.disconnect();
    }

    @Test
    public void testPost() throws Exception {
        doTestPost(false, HttpServletResponse.SC_OK);
    }


    @Test
    public void testPostMultipleContentLength() throws Exception {
        // Multiple content lengths
        doTestPost(true, HttpServletResponse.SC_BAD_REQUEST);
    }


    public void doTestPost(boolean multipleCL, int expectedStatus) throws Exception {

        Tomcat tomcat = getTomcatInstance();

        // Use the normal Tomcat ROOT context
        File root = new File("test/webapp");
        tomcat.addWebapp("", root.getAbsolutePath());

        tomcat.start();

        SimpleAjpClient ajpClient = new SimpleAjpClient();
        ajpClient.setPort(getPort());
        ajpClient.connect();

        validateCpong(ajpClient.cping());

        TesterAjpMessage forwardMessage =
                ajpClient.createForwardMessage("/echo-params.jsp", 4);
        forwardMessage.addHeader(0xA008, "9");
        if (multipleCL) {
            forwardMessage.addHeader(0xA008, "99");
        }
        forwardMessage.addHeader(0xA007, "application/x-www-form-urlencoded");
        forwardMessage.end();

        TesterAjpMessage bodyMessage =
                ajpClient.createBodyMessage("test=data".getBytes());

        TesterAjpMessage responseHeaders =
                ajpClient.sendMessage(forwardMessage, bodyMessage);

        validateResponseHeaders(responseHeaders, expectedStatus);
        if (expectedStatus == HttpServletResponse.SC_OK) {
            // Expect 3 messages: headers, body, end for a valid request
            TesterAjpMessage responseBody = ajpClient.readMessage();
            validateResponseBody(responseBody, "test - data");
            validateResponseEnd(ajpClient.readMessage(), true);

            // Double check the connection is still open
            validateCpong(ajpClient.cping());
        } else {
            // Expect 2 messages: headers, end for an invalid request
            validateResponseEnd(ajpClient.readMessage(), false);
        }


        ajpClient.disconnect();
    }


    /*
     * Bug 55453
     */
    @Test
    public void test304WithBody() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        Context ctx = tomcat.addContext("", System.getProperty("java.io.tmpdir"));
        Tomcat.addServlet(ctx, "bug55453", new Tester304WithBodyServlet());
        ctx.addServletMapping("/", "bug55453");

        tomcat.start();

        SimpleAjpClient ajpClient = new SimpleAjpClient();
        ajpClient.setPort(getPort());
        ajpClient.connect();

        validateCpong(ajpClient.cping());

        TesterAjpMessage forwardMessage = ajpClient.createForwardMessage("/");
        forwardMessage.end();

        TesterAjpMessage responseHeaders =
                ajpClient.sendMessage(forwardMessage, null);

        // Expect 2 messages: headers, end
        validateResponseHeaders(responseHeaders, 304);
        validateResponseEnd(ajpClient.readMessage(), true);

        // Double check the connection is still open
        validateCpong(ajpClient.cping());

        ajpClient.disconnect();
    }


    @Test
    public void testZeroLengthRequestBodyGetA() throws Exception {
        doTestZeroLengthRequestBody(2, true);
    }

    @Test
    public void testZeroLengthRequestBodyGetB() throws Exception {
        doTestZeroLengthRequestBody(2, false);
    }

    @Test
    public void testZeroLengthRequestBodyPostA() throws Exception {
        doTestZeroLengthRequestBody(4, true);
    }

    @Test
    public void testZeroLengthRequestBodyPostB() throws Exception {
        doTestZeroLengthRequestBody(4, false);
    }

    private void doTestZeroLengthRequestBody(int method, boolean callAvailable)
            throws Exception {

        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        Context ctx = tomcat.addContext("", System.getProperty("java.io.tmpdir"));
        ReadBodyServlet servlet = new ReadBodyServlet(callAvailable);
        Tomcat.addServlet(ctx, "ReadBody", servlet);
        ctx.addServletMapping("/", "ReadBody");

        tomcat.start();

        SimpleAjpClient ajpClient = new SimpleAjpClient();
        ajpClient.setPort(getPort());
        ajpClient.connect();

        validateCpong(ajpClient.cping());

        TesterAjpMessage forwardMessage = ajpClient.createForwardMessage("/", method);
        forwardMessage.addHeader(0xA008, "0");
        forwardMessage.end();

        TesterAjpMessage responseHeaders =
                ajpClient.sendMessage(forwardMessage, null);

        // Expect 3 messages: headers, body, end
        validateResponseHeaders(responseHeaders, 200);
        validateResponseBody(ajpClient.readMessage(),
                "Request Body length in bytes: 0");
        validateResponseEnd(ajpClient.readMessage(), true);

        // Double check the connection is still open
        validateCpong(ajpClient.cping());

        ajpClient.disconnect();

        if (callAvailable) {
            boolean success = true;
            Iterator<Integer> itAvailable = servlet.availableList.iterator();
            Iterator<Integer> itRead = servlet.readList.iterator();
            while (success && itAvailable.hasNext()) {
                success = ((itRead.next().intValue() > 0) == (itAvailable.next().intValue() > 0));
            }
            if (!success) {
                Assert.fail("available() and read() results do not match.\nAvailable: "
                        + servlet.availableList + "\nRead: " + servlet.readList);
            }
        }
    }


    /**
     * Process response header packet and checks the status. Any other data is
     * ignored.
     */
    private void validateResponseHeaders(TesterAjpMessage message,
            int expectedStatus) throws Exception {
        // First two bytes should always be AB
        Assert.assertEquals((byte) 'A', message.buf[0]);
        Assert.assertEquals((byte) 'B', message.buf[1]);

        // Set the start position and read the length
        message.processHeader(false);

        // Check the length
        Assert.assertTrue(message.len > 0);

        // Should be a header message
        Assert.assertEquals(0x04, message.readByte());

        // Check status
        Assert.assertEquals(expectedStatus, message.readInt());

        // Read the status message
        message.readString();

        // Get the number of headers
        int headerCount = message.readInt();

        for (int i = 0; i < headerCount; i++) {
            // Read the header name
            message.readHeaderName();
            // Read the header value
            message.readString();
        }
    }

    /**
     * Validates that the response message is valid and contains the expected
     * content.
     */
    private void validateResponseBody(TesterAjpMessage message,
            String expectedBody) throws Exception {

        Assert.assertEquals((byte) 'A', message.buf[0]);
        Assert.assertEquals((byte) 'B', message.buf[1]);

        // Set the start position and read the length
        message.processHeader(false);

        // Should be a body chunk message
        Assert.assertEquals(0x03, message.readByte());

        int len = message.readInt();
        Assert.assertTrue(len > 0);
        String body = message.readString(len);

        Assert.assertTrue(body.contains(expectedBody));
    }

    private void validateResponseEnd(TesterAjpMessage message,
            boolean expectedReuse) {
        Assert.assertEquals((byte) 'A', message.buf[0]);
        Assert.assertEquals((byte) 'B', message.buf[1]);

        message.processHeader(false);

        // Should be an end body message
        Assert.assertEquals(0x05, message.readByte());

        // Check the length
        Assert.assertEquals(2, message.getLen());

        boolean reuse = false;
        if (message.readByte() > 0) {
            reuse = true;
        }

        Assert.assertEquals(Boolean.valueOf(expectedReuse), Boolean.valueOf(reuse));
    }

    private void validateCpong(TesterAjpMessage message) throws Exception {
        // First two bytes should always be AB
        Assert.assertEquals((byte) 'A', message.buf[0]);
        Assert.assertEquals((byte) 'B', message.buf[1]);
        // CPONG should have a message length of 1
        // This effectively checks the next two bytes
        Assert.assertEquals(1, message.getLen());
        // Data should be the value 9
        Assert.assertEquals(9, message.buf[4]);
    }


    private static class Tester304WithBodyServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            resp.setStatus(304);
            resp.getWriter().print("Body not permitted for 304 response");
        }
    }


    private static class ReadBodyServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final boolean callAvailable;
        final List<Integer> availableList;
        final List<Integer> readList;

        public ReadBodyServlet(boolean callAvailable) {
            this.callAvailable = callAvailable;
            this.availableList = callAvailable ? new ArrayList<Integer>() : null;
            this.readList = callAvailable ? new ArrayList<Integer>() : null;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            doRequest(req, resp, false);
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            doRequest(req, resp, true);
        }

        private void doRequest(HttpServletRequest request, HttpServletResponse response,
                boolean isPost) throws IOException {

            long readCount = 0;

            try (InputStream s = request.getInputStream()) {
                byte[] buf = new byte[4096];
                int read;
                do {
                    if (callAvailable) {
                        int available = s.available();
                        read = s.read(buf);
                        availableList.add(Integer.valueOf(available));
                        readList.add(Integer.valueOf(read));
                    } else {
                        read = s.read(buf);
                    }
                    if (read > 0) {
                        readCount += read;
                    }
                } while (read > 0);
            }

            response.setContentType("text/plain");
            response.setCharacterEncoding("UTF-8");

            try (PrintWriter w = response.getWriter()) {
                w.println("Method: " + (isPost ? "POST" : "GET") + ". Reading request body...");
                w.println("Request Body length in bytes: " + readCount);
            }
        }
    }
}
