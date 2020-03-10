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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestHttp11InputBuffer extends TomcatBaseTest {

    private static final String CR = "\r";
    private static final String LF = "\n";
    private  static final String CRLF = CR + LF;

    /**
     * Test case for https://bz.apache.org/bugzilla/show_bug.cgi?id=48839
     */
    @Test
    public void testBug48839() {

        Bug48839Client client = new Bug48839Client();

        client.doRequest();
        Assert.assertTrue(client.isResponse200());
        Assert.assertTrue(client.isResponseBodyOK());
    }


    /**
     * Bug 48839 test client.
     */
    private class Bug48839Client extends SimpleHttpClient {

        private Exception doRequest() {

            Tomcat tomcat = getTomcatInstance();

            Context root = tomcat.addContext("", TEMP_DIR);
            Tomcat.addServlet(root, "Bug48839", new Bug48839Servlet());
            root.addServletMappingDecoded("/test", "Bug48839");

            try {
                tomcat.start();
                setPort(tomcat.getConnector().getLocalPort());

                // Open connection
                connect();

                String[] request = new String[1];
                request[0] =
                    "GET http://localhost:8080/test HTTP/1.1" + CRLF +
                    "Host: localhost:8080" + CRLF +
                    "X-Bug48839: abcd" + CRLF +
                    "\tefgh" + CRLF +
                    "Connection: close" + CRLF +
                    CRLF;

                setRequest(request);
                processRequest(); // blocks until response has been read

                // Close the connection
                disconnect();
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
            if (!getResponseBody().contains("abcd\tefgh")) {
                return false;
            }
            return true;
        }

    }

    private static class Bug48839Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        /**
         * Only interested in the request headers from a GET request
         */
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            // Just echo the header value back as plain text
            resp.setContentType("text/plain");

            PrintWriter out = resp.getWriter();

            Enumeration<String> values = req.getHeaders("X-Bug48839");
            while (values.hasMoreElements()) {
                out.println(values.nextElement());
            }
        }
    }


    @Test
    public void testBug51557Valid() {

        Bug51557Client client = new Bug51557Client("X-Bug51557Valid", "1234");

        client.doRequest();
        Assert.assertTrue(client.isResponse200());
        Assert.assertEquals("1234abcd", client.getResponseBody());
        Assert.assertTrue(client.isResponseBodyOK());
    }


    @Test
    public void testBug51557Invalid() {

        Bug51557Client client = new Bug51557Client("X-Bug51557=Invalid", "1234", true);

        client.doRequest();
        Assert.assertTrue(client.isResponse400());
    }


    @Test
    public void testBug51557NoColon() {

        Bug51557Client client = new Bug51557Client("X-Bug51557NoColon");

        client.doRequest();
        Assert.assertTrue(client.isResponse200());
        Assert.assertEquals("abcd", client.getResponseBody());
        Assert.assertTrue(client.isResponseBodyOK());
    }


    @Test
    public void testBug51557SeparatorsInName() throws Exception {
        char httpSeparators[] = new char[] {
                '\t', ' ', '\"', '(', ')', ',', '/', ':', ';', '<',
                '=', '>', '?', '@', '[', '\\', ']', '{', '}' };

        for (char s : httpSeparators) {
            doTestBug51557CharInName(s);
            tearDown();
            setUp();
        }
    }


    @Test
    public void testBug51557CtlInName() throws Exception {
        for (int i = 0; i < 31; i++) {
            doTestBug51557CharInName((char) i);
            tearDown();
            setUp();
        }
        doTestBug51557CharInName((char) 127);
    }


    @Test
    public void testBug51557CtlInValue() throws Exception {
        for (int i = 0; i < 31; i++) {
            if (i == '\t') {
                // TAB is allowed
                continue;
            }
            doTestBug51557InvalidCharInValue((char) i);
            tearDown();
            setUp();
        }
        doTestBug51557InvalidCharInValue((char) 127);
    }


    @Test
    public void testBug51557ObsTextInValue() throws Exception {
        for (int i = 128; i < 255; i++) {
            doTestBug51557ValidCharInValue((char) i);
            tearDown();
            setUp();
        }
    }


    @Test
    public void testBug51557Continuation() {

        Bug51557Client client = new Bug51557Client("X-Bug=51557NoColon",
                "foo" + SimpleHttpClient.CRLF + " bar");

        client.doRequest();
        Assert.assertTrue(client.isResponse200());
        Assert.assertEquals("abcd", client.getResponseBody());
        Assert.assertTrue(client.isResponseBodyOK());
    }


    @Test
    public void testBug51557BoundaryStart() {

        Bug51557Client client = new Bug51557Client("=X-Bug51557",
                "invalid");

        client.doRequest();
        Assert.assertTrue(client.isResponse200());
        Assert.assertEquals("abcd", client.getResponseBody());
        Assert.assertTrue(client.isResponseBodyOK());
    }


    @Test
    public void testBug51557BoundaryEnd() {

        Bug51557Client client = new Bug51557Client("X-Bug51557=",
                "invalid");

        client.doRequest();
        Assert.assertTrue(client.isResponse200());
        Assert.assertEquals("abcd", client.getResponseBody());
        Assert.assertTrue(client.isResponseBodyOK());
    }


    private void doTestBug51557CharInName(char s) {
        Bug51557Client client =
            new Bug51557Client("X-Bug" + s + "51557", "invalid");

        client.doRequest();
        Assert.assertTrue(client.isResponse200());
        Assert.assertEquals("abcd", client.getResponseBody());
        Assert.assertTrue(client.isResponseBodyOK());
    }


    private void doTestBug51557InvalidCharInValue(char s) {
        Bug51557Client client =
            new Bug51557Client("X-Bug51557-Invalid", "invalid" + s + "invalid");

        client.doRequest();
        Assert.assertTrue("Testing [" + (int) s + "]", client.isResponse200());
        Assert.assertEquals("Testing [" + (int) s + "]", "abcd", client.getResponseBody());
        Assert.assertTrue(client.isResponseBodyOK());
    }


    private void doTestBug51557ValidCharInValue(char s) {
        Bug51557Client client =
            new Bug51557Client("X-Bug51557-Valid", "valid" + s + "valid");

        client.doRequest();
        Assert.assertTrue("Testing [" + (int) s + "]", client.isResponse200());
        Assert.assertEquals("Testing [" + (int) s + "]", "valid" + s + "validabcd", client.getResponseBody());
        Assert.assertTrue(client.isResponseBodyOK());
    }


    /**
     * Bug 51557 test client.
     */
    private class Bug51557Client extends SimpleHttpClient {

        private final String headerName;
        private final String headerLine;
        private final boolean rejectIllegalHeader;

        public Bug51557Client(String headerName) {
            this.headerName = headerName;
            this.headerLine = headerName;
            this.rejectIllegalHeader = false;
        }

        public Bug51557Client(String headerName, String headerValue) {
            this(headerName, headerValue, false);
        }

        public Bug51557Client(String headerName, String headerValue,
                boolean rejectIllegalHeader) {
            this.headerName = headerName;
            this.headerLine = headerName + ": " + headerValue;
            this.rejectIllegalHeader = rejectIllegalHeader;
        }

        private Exception doRequest() {

            Tomcat tomcat = getTomcatInstance();

            Context root = tomcat.addContext("", TEMP_DIR);
            Tomcat.addServlet(root, "Bug51557",
                    new Bug51557Servlet(headerName));
            root.addServletMappingDecoded("/test", "Bug51557");

            try {
                Connector connector = tomcat.getConnector();
                Assert.assertTrue(connector.setProperty(
                        "rejectIllegalHeader", Boolean.toString(rejectIllegalHeader)));
                tomcat.start();
                setPort(connector.getLocalPort());


                // Open connection
                connect();

                String[] request = new String[1];
                request[0] =
                    "GET http://localhost:8080/test HTTP/1.1" + CRLF +
                    "Host: localhost:8080" + CRLF +
                    headerLine + CRLF +
                    "X-Bug51557: abcd" + CRLF +
                    "Connection: close" + CRLF +
                    CRLF;

                setRequest(request);
                processRequest(); // blocks until response has been read

                // Close the connection
                disconnect();
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
            if (!getResponseBody().contains("abcd")) {
                return false;
            }
            return true;
        }

    }

    private static class Bug51557Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private String invalidHeaderName;

        /**
         * @param invalidHeaderName The header name should be invalid and
         *                          therefore ignored by the header parsing code
         */
        public Bug51557Servlet(String invalidHeaderName) {
            this.invalidHeaderName = invalidHeaderName;
        }

        /**
         * Only interested in the request headers from a GET request
         */
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            // Just echo the header value back as plain text
            resp.setContentType("text/plain");

            PrintWriter out = resp.getWriter();

            processHeaders(invalidHeaderName, req, out);
            processHeaders("X-Bug51557", req, out);
        }

        private void processHeaders(String header, HttpServletRequest req,
                PrintWriter out) {
            Enumeration<String> values = req.getHeaders(header);
            while (values.hasMoreElements()) {
                out.println(values.nextElement());
            }
        }
    }


    /**
     * Test case for new lines at the start of a request. RFC2616
     * does not permit any, but Tomcat is tolerant of them if they are present.
     */
    @Test
    public void testNewLines() {

        NewLinesClient client = new NewLinesClient(10);

        client.doRequest();
        Assert.assertTrue(client.isResponse200());
        Assert.assertTrue(client.isResponseBodyOK());
    }


    /**
     * Test case for new lines at the start of a request. RFC2616
     * does not permit any, but Tomcat is tolerant of them if they are present.
     */
    @Test
    public void testNewLinesExcessive() {

        NewLinesClient client = new NewLinesClient(10000);

        // If the connection is closed fast enough, writing the request will
        // fail and the response won't be read.
        Exception e = client.doRequest();
        if (e == null) {
            Assert.assertTrue(client.getResponseLine(), client.isResponse400());
        }
        Assert.assertFalse(client.isResponseBodyOK());
    }


    private class NewLinesClient extends SimpleHttpClient {

        private final String newLines;

        private NewLinesClient(int count) {
            StringBuilder sb = new StringBuilder(count * 2);
            for (int i = 0; i < count; i++) {
                sb.append(CRLF);
            }
            newLines = sb.toString();
        }

        private Exception doRequest() {

            Tomcat tomcat = getTomcatInstance();

            Context root = tomcat.addContext("", TEMP_DIR);
            Tomcat.addServlet(root, "test", new TesterServlet());
            root.addServletMappingDecoded("/test", "test");

            try {
                tomcat.start();
                setPort(tomcat.getConnector().getLocalPort());

                // Open connection
                connect();

                String[] request = new String[1];
                request[0] =
                    newLines +
                    "GET http://localhost:8080/test HTTP/1.1" + CRLF +
                    "Host: localhost:8080" + CRLF +
                    "X-Bug48839: abcd" + CRLF +
                    "\tefgh" + CRLF +
                    "Connection: close" + CRLF +
                    CRLF;

                setRequest(request);
                processRequest(); // blocks until response has been read

                // Close the connection
                disconnect();
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


    /**
     * Test case for https://bz.apache.org/bugzilla/show_bug.cgi?id=54947
     */
    @Test
    public void testBug54947() {

        Bug54947Client client = new Bug54947Client();

        client.doRequest();
        Assert.assertTrue(client.isResponse200());
        Assert.assertTrue(client.isResponseBodyOK());
    }


    /**
     * Bug 54947 test client.
     */
    private class Bug54947Client extends SimpleHttpClient {

        private Exception doRequest() {

            Tomcat tomcat = getTomcatInstance();

            Context root = tomcat.addContext("", TEMP_DIR);
            Tomcat.addServlet(root, "Bug54947", new TesterServlet());
            root.addServletMappingDecoded("/test", "Bug54947");

            try {
                tomcat.start();
                setPort(tomcat.getConnector().getLocalPort());

                // Open connection
                connect();

                String[] request = new String[2];
                request[0] = "GET http://localhost:8080/test HTTP/1.1" + CR;
                request[1] = LF +
                        "Host: localhost:8080" + CRLF +
                        "Connection: close" + CRLF +
                        CRLF;

                setRequest(request);
                processRequest(); // blocks until response has been read

                // Close the connection
                disconnect();
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


    /**
     * Test case for https://bz.apache.org/bugzilla/show_bug.cgi?id=59089
     */
    @Test
    public void testBug59089() {

        Bug59089Client client = new Bug59089Client();

        client.doRequest();
        Assert.assertTrue(client.isResponse200());
        Assert.assertTrue(client.isResponseBodyOK());
    }


    /**
     * Bug 59089 test client.
     */
    private class Bug59089Client extends SimpleHttpClient {

        private Exception doRequest() {

            // Ensure body is read correctly
            setUseContentLength(true);

            Tomcat tomcat = getTomcatInstance();

            Context root = tomcat.addContext("", TEMP_DIR);
            Tomcat.addServlet(root, "Bug59089", new TesterServlet());
            root.addServletMappingDecoded("/test", "Bug59089");

            try {
                Connector connector = tomcat.getConnector();
                Assert.assertTrue(connector.setProperty("rejectIllegalHeader", "false"));
                tomcat.start();
                setPort(connector.getLocalPort());

                // Open connection
                connect();

                String[] request = new String[1];
                request[0] = "GET http://localhost:8080/test HTTP/1.1" + CRLF +
                        "Host: localhost:8080" + CRLF +
                        "X-Header: Ignore" + CRLF +
                        "X-Header" + (char) 130 + ": Broken" + CRLF + CRLF;

                setRequest(request);
                processRequest(); // blocks until response has been read

                // Close the connection
                disconnect();
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
    public void testInvalidMethod() {

        String[] request = new String[1];
        request[0] =
            "GET" + (char) 0 + " /test HTTP/1.1" + CRLF +
            "Host: localhost:8080" + CRLF +
            "Connection: close" + CRLF +
            CRLF;

        InvalidClient client = new InvalidClient(request);

        client.doRequest();
        Assert.assertTrue(client.getResponseLine(), client.isResponse400());
        Assert.assertTrue(client.isResponseBodyOK());
    }


    @Test
    public void testInvalidHttp09() {

        String[] request = new String[1];
        request[0] = "GET /test" + CR + " " + LF;

        InvalidClient client = new InvalidClient(request);

        client.doRequest();
        Assert.assertTrue(client.getResponseLine(), client.isResponse400());
        Assert.assertTrue(client.isResponseBodyOK());
    }


    @Test
    public void testInvalidEndOfRequestLine01() {

        String[] request = new String[1];
        request[0] =
                "GET /test HTTP/1.1" + CR +
                "Host: localhost:8080" + CRLF +
                "Connection: close" + CRLF +
                CRLF;

        InvalidClient client = new InvalidClient(request);

        client.doRequest();
        Assert.assertTrue(client.getResponseLine(), client.isResponse400());
        Assert.assertTrue(client.isResponseBodyOK());
    }


    @Test
    public void testInvalidEndOfRequestLine02() {

        String[] request = new String[1];
        request[0] =
                "GET /test HTTP/1.1" + LF +
                "Host: localhost:8080" + CRLF +
                "Connection: close" + CRLF +
                CRLF;

        InvalidClient client = new InvalidClient(request);

        client.doRequest();
        Assert.assertTrue(client.getResponseLine(), client.isResponse400());
        Assert.assertTrue(client.isResponseBodyOK());
    }


    @Test
    public void testInvalidHeader01() {

        String[] request = new String[1];
        request[0] =
                "GET /test HTTP/1.1" + CRLF +
                "Host: localhost:8080" + CRLF +
                CR + "X-Header: xxx" + CRLF +
                "Connection: close" + CRLF +
                CRLF;

        InvalidClient client = new InvalidClient(request);

        client.doRequest();
        Assert.assertTrue(client.getResponseLine(), client.isResponse400());
        Assert.assertTrue(client.isResponseBodyOK());
    }


    /**
     * Invalid request test client.
     */
    private class InvalidClient extends SimpleHttpClient {

        private final String[] request;

        public InvalidClient(String[] request) {
            this.request = request;
        }

        private Exception doRequest() {

            Tomcat tomcat = getTomcatInstance();
            tomcat.getConnector().setProperty("rejectIllegalHeader", "true");

            tomcat.addContext("", TEMP_DIR);

            try {
                tomcat.start();
                setPort(tomcat.getConnector().getLocalPort());

                // Open connection
                connect();
                setRequest(request);
                processRequest(); // blocks until response has been read

                // Close the connection
                disconnect();
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
            return true;
        }
    }
}
