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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestCookiesAllowEquals extends TomcatBaseTest {

    private static final String COOKIE_WITH_EQUALS_1 = "name=equals=middle";
    private static final String COOKIE_WITH_EQUALS_2 = "name==equalsstart";
    private static final String COOKIE_WITH_EQUALS_3 = "name=equalsend=";

    @Test
    public void testLegacyWithEquals() throws Exception {
        TestCookieEqualsClient client = new TestCookieEqualsClient(true, false, false);
        client.doRequest();
    }

    @Test
    public void testLegacyWithoutEquals() throws Exception {
        TestCookieEqualsClient client = new TestCookieEqualsClient(false, false, true);
        client.doRequest();
    }

    @Test
    public void testRfc6265() throws Exception {
        // Always allows equals
        TestCookieEqualsClient client = new TestCookieEqualsClient(false, true, false);
        client.doRequest();
    }

    private class TestCookieEqualsClient extends SimpleHttpClient {

        private final boolean allowEquals;
        private final boolean useRfc6265;
        private final boolean expectTruncated;

        public TestCookieEqualsClient(boolean allowEquals, boolean useRfc6265,
                boolean expectTruncated) {
            this.allowEquals = allowEquals;
            this.useRfc6265 = useRfc6265;
            this.expectTruncated = expectTruncated;
        }


        private void doRequest() throws Exception {
            Tomcat tomcat = getTomcatInstance();
            Context root = tomcat.addContext("", TEMP_DIR);
            CookieProcessor cookieProcessor;
            if (useRfc6265) {
                cookieProcessor = new Rfc6265CookieProcessor();
            } else {
                LegacyCookieProcessor legacyCookieProcessor = new LegacyCookieProcessor();
                legacyCookieProcessor.setAllowEqualsInValue(allowEquals);
                // Need to allow name only cookies to handle equals at the start of
                // the value
                legacyCookieProcessor.setAllowNameOnly(true);
                cookieProcessor = legacyCookieProcessor;
            }
            root.setCookieProcessor(cookieProcessor);

            Tomcat.addServlet(root, "Simple", new SimpleServlet());
            root.addServletMapping("/test", "Simple");

            tomcat.start();
            // Open connection
            setPort(tomcat.getConnector().getLocalPort());
            connect();

            String[] request = new String[1];
            request[0] =
                "GET /test HTTP/1.0" + CRLF +
                "Cookie: " + COOKIE_WITH_EQUALS_1 + CRLF +
                "Cookie: " + COOKIE_WITH_EQUALS_2 + CRLF +
                "Cookie: " + COOKIE_WITH_EQUALS_3 + CRLF + CRLF;
            setRequest(request);
            processRequest(true); // blocks until response has been read
            String response = getResponseBody();

            // Close the connection
            disconnect();
            reset();
            tomcat.stop();

            StringBuilder expected = new StringBuilder();
            expected.append(truncate(COOKIE_WITH_EQUALS_1, expectTruncated));
            expected.append(truncate(COOKIE_WITH_EQUALS_2, expectTruncated));
            expected.append(truncate(COOKIE_WITH_EQUALS_3, expectTruncated));
            assertEquals(expected.toString(), response);
        }

        private final String truncate(String input, boolean doIt) {
            if (doIt) {
                int end = input.indexOf('=', input.indexOf('=') + 1);
                return input.substring(0, end);
            } else {
                return input;
            }
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
            for (Cookie cookie : cookies) {
                resp.getWriter().write(cookie.getName() + "=" +
                        cookie.getValue());
            }
            resp.flushBuffer();
        }

    }

}
