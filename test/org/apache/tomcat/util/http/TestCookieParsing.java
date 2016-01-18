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
package org.apache.tomcat.util.http;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;


public class TestCookieParsing extends TomcatBaseTest {

    private static final String[] COOKIES_WITH_EQUALS = new String[] {
            "name=equals=middle", "name==equalsstart", "name=equalsend=" };
    private static final String COOKIES_WITH_EQUALS_TRUNC = "name=equalsname=name=equalsend";

    private static final String[] COOKIES_WITH_NAME_ONLY = new String[] {
            "bob", "bob=" };
    private static final String COOKIES_WITH_NAME_ONLY_CONCAT = "bob=bob=";

    private static final String[] COOKIES_WITH_SEPS = new String[] {
            "name=val/ue" };
    private static final String COOKIES_WITH_SEPS_TRUNC = "name=val";

    private static final String[] COOKIES_WITH_QUOTES = new String[] {
            "name=\"val\\\"ue\"" };

    @Test
    public void testLegacyWithEquals() throws Exception {
        doTestLegacyEquals(true);
    }


    @Test
    public void testLegacyWithoutEquals() throws Exception {
        doTestLegacyEquals(false);
    }


    private void doTestLegacyEquals(boolean allowEquals) throws Exception {
        LegacyCookieProcessor legacyCookieProcessor = new LegacyCookieProcessor();
        legacyCookieProcessor.setAllowEqualsInValue(allowEquals);
        // Need to allow name only cookies to handle equals at the start of
        // the value
        legacyCookieProcessor.setAllowNameOnly(true);

        String expected;
        if (allowEquals) {
            expected = concat(COOKIES_WITH_EQUALS);
        } else {
            expected = COOKIES_WITH_EQUALS_TRUNC;
        }
        TestCookieParsingClient client = new TestCookieParsingClient(
                legacyCookieProcessor, COOKIES_WITH_EQUALS, expected);
        client.doRequest();
    }


    @Test
    public void testRfc6265Equals() throws Exception {
        // Always allows equals
        TestCookieParsingClient client = new TestCookieParsingClient(
                new Rfc6265CookieProcessor(), COOKIES_WITH_EQUALS, concat(COOKIES_WITH_EQUALS));
        client.doRequest();
    }


    @Test
    public void testLegacyWithNameOnly() throws Exception {
        doTestLegacyNameOnly(true);
    }


    @Test
    public void testLegacyWithoutNameOnly() throws Exception {
        doTestLegacyNameOnly(false);
    }


    private void doTestLegacyNameOnly(boolean nameOnly) throws Exception {
        LegacyCookieProcessor legacyCookieProcessor = new LegacyCookieProcessor();
        legacyCookieProcessor.setAllowNameOnly(nameOnly);

        String expected;
        if (nameOnly) {
            expected = COOKIES_WITH_NAME_ONLY_CONCAT;
        } else {
            expected = "";
        }
        TestCookieParsingClient client = new TestCookieParsingClient(
                legacyCookieProcessor, COOKIES_WITH_NAME_ONLY, expected);
        client.doRequest();
    }


    @Test
    public void testRfc6265NameOnly() throws Exception {
        // Always allows equals
        TestCookieParsingClient client = new TestCookieParsingClient(
                new Rfc6265CookieProcessor(), COOKIES_WITH_NAME_ONLY,
                COOKIES_WITH_NAME_ONLY_CONCAT);
        client.doRequest();
    }


    @Test
    public void testLegacyWithSeps() throws Exception {
        doTestLegacySeps(true, true);
    }


    @Test
    public void testLegacyWithoutSeps() throws Exception {
        doTestLegacySeps(false, true);
    }


    @Test
    public void testLegacyWithFwdSlash() throws Exception {
        doTestLegacySeps(true, false);
    }


    @Test
    public void testLegacyWithoutFwdSlash() throws Exception {
        doTestLegacySeps(false, false);
    }


    private void doTestLegacySeps(boolean seps, boolean fwdSlash) throws Exception {
        LegacyCookieProcessor legacyCookieProcessor = new LegacyCookieProcessor();
        legacyCookieProcessor.setAllowHttpSepsInV0(seps);
        legacyCookieProcessor.setForwardSlashIsSeparator(fwdSlash);

        String expected;
        if (!seps && fwdSlash) {
            expected = COOKIES_WITH_SEPS_TRUNC;
        } else {
            expected = concat(COOKIES_WITH_SEPS);
        }
        TestCookieParsingClient client = new TestCookieParsingClient(
                legacyCookieProcessor, COOKIES_WITH_SEPS, expected);
        client.doRequest();
    }


    @Test
    public void testRfc6265Seps() throws Exception {
        // Always allows equals
        TestCookieParsingClient client = new TestCookieParsingClient(
                new Rfc6265CookieProcessor(), COOKIES_WITH_SEPS, concat(COOKIES_WITH_SEPS));
        client.doRequest();
    }


    @Test
    public void testLegacyPreserveHeader() throws Exception {
        LegacyCookieProcessor legacyCookieProcessor = new LegacyCookieProcessor();

        String expected;
        expected = concat(COOKIES_WITH_QUOTES);
        TestCookieParsingClient client = new TestCookieParsingClient(
                legacyCookieProcessor, true, COOKIES_WITH_QUOTES, expected);
        client.doRequest();
    }


    @Test
    public void testRfc6265PreserveHeader() throws Exception {
        // Always allows equals
        TestCookieParsingClient client = new TestCookieParsingClient(new Rfc6265CookieProcessor(),
                true, COOKIES_WITH_QUOTES, concat(COOKIES_WITH_QUOTES));
        client.doRequest();
    }


    private static String concat(String[] input) {
        StringBuilder result = new StringBuilder();
        for (String s : input) {
            result.append(s);
        }
        return result.toString();
    }


    private class TestCookieParsingClient extends SimpleHttpClient {

        private final CookieProcessor cookieProcessor;
        private final String[] cookies;
        private final String expected;
        private final boolean echoHeader;


        public TestCookieParsingClient(CookieProcessor cookieProcessor,
                String[] cookies, String expected) {
            this(cookieProcessor, false, cookies, expected);
        }

        public TestCookieParsingClient(CookieProcessor cookieProcessor,
                boolean echoHeader, String[] cookies, String expected) {
            this.cookieProcessor = cookieProcessor;
            this.echoHeader = echoHeader;
            this.cookies = cookies;
            this.expected = expected;
        }


        private void doRequest() throws Exception {
            Tomcat tomcat = getTomcatInstance();
            Context root = tomcat.addContext("", TEMP_DIR);
            root.setCookieProcessor(cookieProcessor);

            if (echoHeader) {
                Tomcat.addServlet(root, "Cookies", new EchoCookieHeader());
            } else {
                Tomcat.addServlet(root, "Cookies", new EchoCookies());
            }
            root.addServletMapping("/test", "Cookies");

            tomcat.start();
            // Open connection
            setPort(tomcat.getConnector().getLocalPort());
            connect();

            StringBuilder request = new StringBuilder();
            request.append("GET /test HTTP/1.0");
            request.append(CRLF);
            for (String cookie : cookies) {
                request.append("Cookie: ");
                request.append(cookie);
                request.append(CRLF);
            }
            request.append(CRLF);
            setRequest(new String[] {request.toString()});
            processRequest(true); // blocks until response has been read
            String response = getResponseBody();

            // Close the connection
            disconnect();
            reset();
            tomcat.stop();

            Assert.assertEquals(expected, response);
        }


        @Override
        public boolean isResponseBodyOK() {
            return true;
        }
    }


    private static class EchoCookies extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
            Cookie cookies[] = req.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    resp.getWriter().write(cookie.getName() + "=" +
                            cookie.getValue());
                }
            }
            resp.flushBuffer();
        }
    }




    private static class EchoCookieHeader extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
            req.getCookies();
            // Never do this in production code. It triggers an XSS.
            resp.getWriter().write(req.getHeader("Cookie"));
            resp.flushBuffer();
        }
    }

}
