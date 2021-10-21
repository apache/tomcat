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
package org.apache.catalina.connector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.Context;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

/*
 * Tests for the canonicalization clarifications in Servlet 6.0
 */
@RunWith(Parameterized.class)
public class TestCoyoteAdapterCanonicalization extends TomcatBaseTest {

    @Parameterized.Parameters(name = "{index}: requestURI[{0}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        // This should be consistent with the table in the Servlet specification
        parameterSets.add(new Object[] { "foo/bar", "/foo/bar", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/foo/bar", "/foo/bar", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/bar;jsessionid=1234", "/foo/bar", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/bar/", "/foo/bar/", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/bar/;jsessionid=1234", "/foo/bar/", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo;/bar;", "/foo/bar", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo;/bar;/;", "/foo/bar/", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo%00/bar/", "/foo\000/bar/", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/foo%7Fbar", "/foo\177bar", Boolean.TRUE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo%2Fbar", "/foo%2Fbar", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/foo%2Fb%25r", "/foo%2Fb%25r", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/foo/b%25r", "/foo/b%r", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo\\bar", "/foo\\bar", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/foo%5Cbar", "/foo\\bar", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/foo;%2F/bar", "/foo/bar", Boolean.TRUE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/./bar", "/foo/bar", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/././bar", "/foo/bar", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/./foo/bar", "/foo/bar", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/%2e/bar", "/foo/bar", Boolean.TRUE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/.;/bar", "/foo/bar", Boolean.TRUE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/%2e;/bar", "/foo/bar", Boolean.TRUE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/.%2Fbar", "/foo/.%2Fbar", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/foo/.%5Cbar", "/foo/.\\bar", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/foo/bar/.", "/foo/bar", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/bar/./", "/foo/bar/", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/bar/.;", "/foo/bar", Boolean.TRUE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/bar/./;", "/foo/bar/", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/.bar", "/foo/.bar", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/../bar", "/bar", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/../../bar", "/../bar", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/../foo/bar", "/../foo/bar", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/foo/%2e%2E/bar", "/bar", Boolean.TRUE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/%2e%2e/%2E%2E/bar", "/../bar", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/foo/./../bar", "/bar", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/..;/bar", "/bar", Boolean.TRUE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/%2e%2E;/bar", "/bar", Boolean.TRUE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/..%2Fbar", "/foo/..%2Fbar", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/foo/..%5Cbar", "/foo/..\\bar", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/foo/bar/..", "/foo", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/bar/../", "/foo/", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/bar/..;", "/foo", Boolean.TRUE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/bar/../;", "/foo/", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/..bar", "/foo/..bar", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/.../bar", "/foo/.../bar", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo//bar", "/foo/bar", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "//foo//bar//", "/foo/bar/", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/;/foo;/;/bar/;/;", "/foo/bar/", Boolean.TRUE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo//../bar", "/bar", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/;/../bar", "/bar", Boolean.TRUE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo%E2%82%ACbar", "/foo\u20acbar", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo%20bar", "/foo bar", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo%E2%82", "/foo%E2%82", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/foo%E2%82bar", "/foo%E2%82bar", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/foo%-1/bar", "/foo%-1/bar", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/foo%XX/bar", "/foo%XX/bar", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/foo%/bar", "/foo%/bar", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/foo/bar%0", "/foo/bar%0", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/good%20/bad%/%20mix%", "/good /bad%/%20mix%", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/foo/bar?q", "/foo/bar", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/bar#f", "/foo/bar", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/foo/bar?q#f", "/foo/bar", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/foo/bar/?q", "/foo/bar/", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/bar/#f", "/foo/bar/", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/foo/bar/?q#f", "/foo/bar/", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/foo/bar;?q", "/foo/bar", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/foo/bar;#f", "/foo/bar", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/foo/bar;?q#f", "/foo/bar", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/", "/", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "//", "/", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/;/", "/", Boolean.TRUE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/.", "/", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/..", "/..", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/./", "/", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "/../", "/../", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "foo/bar/", "/foo/bar/", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "./foo/bar/", "/foo/bar/", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "%2e/foo/bar/", "/foo/bar/", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "../foo/bar/", "/../foo/bar/", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { ".%2e/foo/bar/", "/../foo/bar/", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { ";/foo/bar/", "/foo/bar/", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/#f", "/", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "#f", "/", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { "/?q", "/", Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { "?q", "/", Boolean.TRUE, Boolean.TRUE });

        return parameterSets;
    }

    @Parameter(0)
    public String requestURI;

    @Parameter(1)
    public String canonicalizedURI;

    @Parameter(2)
    public boolean badRequestServlet;

    @Parameter(3)
    public boolean badRequestTomcat;


    @Test
    public void testCanonicalizationSpecification() throws Exception {
        doTestCanonicalization(true, badRequestServlet);
    }

    @Test
    public void testCanonicalizationTomcat() throws Exception {
        doTestCanonicalization(false, badRequestTomcat);
    }


    private void doTestCanonicalization(boolean specCompliant, boolean expectBadRequest) throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // ROOT web application so context path is ""
        Context root = tomcat.addContext("", TEMP_DIR);
        Tomcat.addServlet(root, "EchoServletPath", new EchoServletPath());
        // Map as default servlet so servlet path should be URI less context
        // path. Since the content path is "" the servlet path should be the
        // URI.
        root.addServletMappingDecoded("/", "EchoServletPath");

        if (specCompliant) {
            // Enabled options for stricter checking
            tomcat.getConnector().setRejectSuspiciousURIs(true);
        }

        tomcat.start();

        Client client = new Client(tomcat.getConnector().getLocalPort(), canonicalizedURI);
        client.setRequest(new String[] {
                "GET " + requestURI + " HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                CRLF
        });
        client.setResponseBodyEncoding(StandardCharsets.UTF_8);

        client.connect();
        client.processRequest();

        // Expected response
        String line = client.getResponseLine();
        String body = client.getResponseBody();
        if (expectBadRequest) {
            Assert.assertTrue(line + CRLF + body, line.startsWith("HTTP/1.1 " + HttpServletResponse.SC_BAD_REQUEST));
        } else {
            Assert.assertTrue(line + CRLF + body, line.startsWith("HTTP/1.1 " + HttpServletResponse.SC_OK));
            Assert.assertEquals(line + CRLF + body, canonicalizedURI, body);
        }
    }


    public class EchoServletPath extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write(req.getServletPath());
        }
    }


    private static final class Client extends SimpleHttpClient {

        private final String expected;

        public Client(int port, String expected) {
            this.expected = expected;
            setPort(port);
            setRequestPause(0);
            setUseContentLength(true);
        }

        @Override
        public boolean isResponseBodyOK() {
            return expected.equals(getResponseBody());
        }
    }
}
