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
package org.apache.catalina.authenticator;

import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.TesterMapRealm;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.apache.tomcat.util.http.Method;

public class TestFormAuthenticatorD extends TomcatBaseTest {

    private static final String CRLF = SimpleHttpClient.CRLF;

    private static final String STANDARD_USER = "userA";
    private static final String ADMIN_USER = "userB";


    @Test
    public void testWithStandardUser() throws Exception {
        SimpleHttpClient client = setupTest();

        // Standard user requesting GET /standard
        // Initial request - receive login form
        requestStandard(client, null, true);
        // Submit login form - receive redirect
        submitFormAuth(client, STANDARD_USER);
        // Request original page
        requestStandard(client, STANDARD_USER, false);

        // As authenticated standard user, request GET /admin
        requestAdmin(client, STANDARD_USER, Method.GET, Method.GET, false);

        // As authenticated standard user, request POST /admin
        requestAdmin(client, STANDARD_USER, Method.POST, Method.POST, false);

        // Clear authentication
        client.setSessionId(null);

        // As unauthenticated user, request GET /admin
        requestAdmin(client, null, Method.GET, Method.GET, false);

        // As unauthenticated user, request POST /admin
        requestAdmin(client, null, Method.POST, Method.POST, true);
        // Submit login form - receive redirect
        submitFormAuth(client, STANDARD_USER);
        // As standard user, request POST /admin
        requestAdmin(client, STANDARD_USER, Method.GET, Method.POST, false);
    }


    @Test
    public void testWithAdminUser() throws Exception {
        SimpleHttpClient client = setupTest();

        // Standard user requesting GET /standard
        // Initial request - receive login form
        requestStandard(client, null, true);
        // Submit login form - receive redirect
        submitFormAuth(client, ADMIN_USER);
        // Request original page
        requestStandard(client, ADMIN_USER, false);

        // As authenticated standard user, request GET /admin
        requestAdmin(client, ADMIN_USER, Method.GET, Method.GET, false);

        // As authenticated standard user, request POST /admin
        requestAdmin(client, ADMIN_USER, Method.POST, Method.POST, false);

        // Clear authentication
        client.setSessionId(null);

        // As unauthenticated user, request GET /admin
        requestAdmin(client, null, Method.GET, Method.GET, false);

        // As unauthenticated user, request POST /admin
        requestAdmin(client, null, Method.POST, Method.POST, true);
        // Submit login form - receive redirect
        submitFormAuth(client, ADMIN_USER);
        // As standard user, request POST /admin
        requestAdmin(client, ADMIN_USER, Method.GET, Method.POST, false);
    }


    private SimpleHttpClient setupTest() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        // Add Servlets
        Tomcat.addServlet(ctx, "login", new LoginServlet());
        Tomcat.addServlet(ctx, "error", new ErrorServlet());
        Tomcat.addServlet(ctx, "target", new TargetServlet());
        // Map Servlets (target gets mapped twice)
        ctx.addServletMapping("/login", "login");
        ctx.addServletMapping("/error", "error");
        ctx.addServletMapping("/standard", "target");
        ctx.addServletMapping("/admin", "target");

        // Configure the Realm
        TesterMapRealm realm = new TesterMapRealm();
        realm.addUser(STANDARD_USER, STANDARD_USER);
        realm.addUserRole(STANDARD_USER, "standard");
        realm.addUser(ADMIN_USER, ADMIN_USER);
        realm.addUserRole(ADMIN_USER, "standard");
        realm.addUserRole(ADMIN_USER, "admin");
        ctx.setRealm(realm);

        // Configure the security constraints
        // /standard is protected for all methods
        SecurityConstraint constraintStandard = new SecurityConstraint();
        SecurityCollection collectionStandard = new SecurityCollection();
        collectionStandard.setName("Protect standard");
        collectionStandard.addPattern("/standard");
        constraintStandard.addCollection(collectionStandard);
        constraintStandard.addAuthRole("standard");
        ctx.addConstraint(constraintStandard);
        // /admin is only protected for POST
        SecurityConstraint constraintAdmin = new SecurityConstraint();
        SecurityCollection collectionAdmin = new SecurityCollection();
        collectionAdmin.addMethod("POST");
        collectionAdmin.setName("Protect admin");
        collectionAdmin.addPattern("/admin");
        constraintAdmin.addCollection(collectionAdmin);
        constraintAdmin.addAuthRole("admin");
        ctx.addConstraint(constraintAdmin);

        // Configure authentication
        LoginConfig lc = new LoginConfig();
        lc.setAuthMethod("FORM");
        lc.setLoginPage("/login");
        lc.setErrorPage("/error");
        ctx.setLoginConfig(lc);
        ctx.getPipeline().addValve(new FormAuthenticator());

        tomcat.start();

        TestHttpClient client = new TestHttpClient();
        client.setPort(getPort());
        client.setUseContentLength(true);
        client.setUseCookies(true);
        client.setRequestPause(0);
        client.connect();

        return client;
    }

    private void submitFormAuth(SimpleHttpClient client, String user) throws Exception {
        client.setRequest(new String[] {
                "POST /j_security_check HTTP/1.1" + CRLF,
                "Host: localhost:" + getPort() + CRLF,
                "Cookie: JSESSIONID=" + client.getSessionId() + CRLF,
                "Content-Type: application/x-www-form-urlencoded" + CRLF,
                "Content-Length: " + (23 + user.length() * 2) + CRLF,
                CRLF,
                "j_username=" + user + "&j_password=" + user });
        client.processRequest();
        Assert.assertEquals(303, client.getStatusCode());
        client.resetResponse();
    }


    private void requestStandard(SimpleHttpClient client, String user, boolean authRedirectExpected) throws Exception {
        String sessionID = client.getSessionId();

        client.setRequest(new String[] {
                "GET /standard HTTP/1.1" + CRLF,
                "Host: localhost:" + getPort() + CRLF,
                "Cookie: " + (sessionID == null ? "a=b" : "JSESSIONID=" + sessionID) + CRLF,
                CRLF });
        client.processRequest();
        Assert.assertEquals(200, client.getStatusCode());
        if (authRedirectExpected) {
            Assert.assertTrue(client.getResponseBody().contains("j_security_check"));
        } else {
            Assert.assertEquals("GET" + System.lineSeparator() + user + System.lineSeparator() + ADMIN_USER.equals(user) +
                    System.lineSeparator() + "null" + System.lineSeparator(), client.getResponseBody());
        }
        client.resetResponse();
    }


    private void requestAdmin(SimpleHttpClient client, String user, String requestMethod, String resultMethod,
            boolean authRedirectExpected) throws Exception {
        String sessionID = client.getSessionId();

        client.setRequest(
                new String[] {
                        requestMethod + " /admin HTTP/1.1" + CRLF,
                        "Host: localhost:" + getPort() + CRLF,
                        "Cookie: " + (sessionID == null ? "a=b" : "JSESSIONID=" + sessionID) + CRLF,
                        CRLF });
        client.processRequest();
        String body = client.getResponseBody();
        if (authRedirectExpected) {
            Assert.assertEquals(body, 200, client.getStatusCode());
            Assert.assertTrue(body, body.contains("j_security_check"));
        } else {
            if (Method.POST.equals(resultMethod)) {
                if (ADMIN_USER.equals(user)) {
                    Assert.assertEquals(body, 200, client.getStatusCode());
                    Assert.assertEquals(body, resultMethod + System.lineSeparator() + user + System.lineSeparator() +
                            "true" + System.lineSeparator() + "null" + System.lineSeparator(),
                            client.getResponseBody());
                } else {
                    Assert.assertEquals(body, 403, client.getStatusCode());
                }
            } else {
                Assert.assertEquals(body, 200, client.getStatusCode());
                Assert.assertEquals(body, resultMethod + System.lineSeparator() + user + System.lineSeparator() +
                        ADMIN_USER.equals(user) + System.lineSeparator() + "null" + System.lineSeparator(),
                        client.getResponseBody());
            }
        }
        client.resetResponse();
    }


    private static class TestHttpClient extends SimpleHttpClient {

        @Override
        public boolean isResponseBodyOK() {
            return true;
        }
    }


    private static class LoginServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("text/html");
            resp.setCharacterEncoding("UTF-8");
            PrintWriter pw = resp.getWriter();
            pw.println("<html>");
            pw.println("<body>");
            pw.println("<form method=\"post\" action=\"j_security_check\">");
            pw.println("<input name=\"j_username\">");
            pw.println("<input name=\"j_password\" type=\"password\">");
            pw.println("<button type=\"submit\">Login</button>");
            pw.println("</form>");
            pw.println("</body>");
            pw.println("</html>");
        }
    }


    private static class ErrorServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            PrintWriter pw = resp.getWriter();
            pw.println("Login failed");
        }
    }


    private static class TargetServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            PrintWriter pw = resp.getWriter();
            pw.println(req.getMethod());
            pw.println(req.getRemoteUser());
            pw.println(req.isUserInRole("admin"));
            pw.println(req.getParameter("action"));
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            doGet(req, resp);
        }
    }
}
