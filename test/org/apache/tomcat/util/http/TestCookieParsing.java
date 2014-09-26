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

    private static final String[] COOKIEs_WITH_NAME_ONLY = new String[] {
            "bob", "bob=" };


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
            StringBuilder sb = new StringBuilder();
            for (String cookie : COOKIES_WITH_EQUALS) {
                int end = cookie.indexOf('=', cookie.indexOf('=') + 1);
                sb.append(cookie.substring(0, end));
            }
            expected = sb.toString();
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
            expected = concat(COOKIEs_WITH_NAME_ONLY, true);
        } else {
            expected = "";
        }
        TestCookieParsingClient client = new TestCookieParsingClient(
                legacyCookieProcessor, COOKIEs_WITH_NAME_ONLY, expected);
        client.doRequest();
    }


    @Test
    public void testRfc6265NameOnly() throws Exception {
        // Always allows equals
        TestCookieParsingClient client = new TestCookieParsingClient(
                new Rfc6265CookieProcessor(), COOKIEs_WITH_NAME_ONLY,
                concat(COOKIEs_WITH_NAME_ONLY, true));
        client.doRequest();
    }


    private static String concat(String[] input) {
        return concat(input, false);
    }

    private static String concat(String[] input, boolean mustEndInEquals) {
        StringBuilder result = new StringBuilder();
        for (String s : input) {
            result.append(s);
            if (!s.endsWith("=") && mustEndInEquals) {
                result.append('=');
            }
        }
        return result.toString();
    }


    private class TestCookieParsingClient extends SimpleHttpClient {

        private final CookieProcessor cookieProcessor;
        private final String[] cookies;
        private final String expected;


        public TestCookieParsingClient(CookieProcessor cookieProcessor,
                String[] cookies, String expected) {
            this.cookieProcessor = cookieProcessor;
            this.cookies = cookies;
            this.expected = expected;
        }


        private void doRequest() throws Exception {
            Tomcat tomcat = getTomcatInstance();
            Context root = tomcat.addContext("", TEMP_DIR);
            root.setCookieProcessor(cookieProcessor);

            Tomcat.addServlet(root, "Simple", new SimpleServlet());
            root.addServletMapping("/test", "Simple");

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


    private static class SimpleServlet extends HttpServlet {

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
}
