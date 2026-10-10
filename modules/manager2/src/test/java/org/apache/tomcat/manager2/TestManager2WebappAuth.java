/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
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
package org.apache.tomcat.manager2;

import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.startup.SimpleHttpClient;

/**
 * FORM login (redirects, error handling) and CSRF protection of the manager2 webapp.
 */
public class TestManager2WebappAuth extends Manager2WebappTestBase {

    @Test
    public void testUnauthenticatedSeesLoginPage() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();

        // Request the SPA entry point: FORM authentication forwards to the
        // login page.
        client.setRequest(new String[] { "GET " + MANAGER2 + "/ HTTP/1.1" + CRLF, "Host: localhost:" + getPort() + CRLF,
                "Connection: Close" + CRLF, CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(200, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("j_security_check"));

        // API requests are protected as well.
        client.setRequest(new String[] { "GET " + MANAGER2 + "/api/apps HTTP/1.1" + CRLF,
                "Host: localhost:" + getPort() + CRLF, "Connection: Close" + CRLF, CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(200, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("j_security_check"));

        client.disconnect();
    }


    @Test
    public void testContextRootRedirectsToTrailingSlash() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();

        // The context root without a trailing slash is redirected to the
        // trailing-slash form, so that the browser resolves the
        // application's relative URLs (CSS, JS, images) against the
        // context instead of the server root.
        client.setRequest(new String[] { "GET " + MANAGER2 + " HTTP/1.1" + CRLF, "Host: localhost:" + getPort() + CRLF,
                "Connection: Close" + CRLF, CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(302, client.getStatusCode());
        String location = null;
        for (String header : client.getResponseHeaders()) {
            if (header.toLowerCase().startsWith("location: ")) {
                location = header.substring("Location: ".length());
            }
        }
        Assert.assertEquals(MANAGER2 + "/", location);

        client.disconnect();
    }


    @Test
    public void testLoginCsrfAndInfo() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        // Read-only endpoint; also issues the CSRF token.
        client.setRequest(
                new String[] { "GET " + MANAGER2 + "/api/info HTTP/1.1" + CRLF, "Host: localhost:" + getPort() + CRLF,
                        "Cookie: JSESSIONID=" + client.getSessionId() + CRLF, "Connection: Close" + CRLF, CRLF });
        client.connect();
        client.processRequest(true);

        Assert.assertEquals(200, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("javaRuntimeVersion"));
        String token = getCsrfToken(client);
        Assert.assertNotNull("Expected an X-CSRF-Token header", token);
        Assert.assertTrue("Expected a 32+ character CSRF token", token.length() >= 32);

        client.disconnect();
    }


    @Test
    public void testPostLoginRedirectsToAppRoot() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();

        // Establish a session (and the saved request) via a protected API.
        client.setRequest(new String[] { "GET " + MANAGER2 + "/api/info HTTP/1.1" + CRLF,
                "Host: localhost:" + getPort() + CRLF, "Connection: Close" + CRLF, CRLF });
        client.connect();
        client.processRequest(true);

        // Submit the login form.
        String body = "j_username=manager1&j_password=secret";
        client.setRequest(new String[] { "POST " + MANAGER2 + "/j_security_check HTTP/1.1" + CRLF,
                "Host: localhost:" + getPort() + CRLF, "Cookie: JSESSIONID=" + client.getSessionId() + CRLF,
                "Content-Type: application/x-www-form-urlencoded" + CRLF,
                "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + CRLF, "Connection: Close" + CRLF,
                CRLF, body });
        client.connect();
        client.processRequest(true);

        Assert.assertEquals(303, client.getStatusCode());

        // The post-login redirect must land at the application root, not at
        // the saved API request (or, historically, at a CSS/JS file).
        String location = null;
        for (String header : client.getResponseHeaders()) {
            if (header.toLowerCase().startsWith("location:")) {
                location = header.substring("location:".length()).trim();
            }
        }
        Assert.assertEquals(MANAGER2 + "/", location);

        // The redirect target is the SPA shell.
        requestRaw(client, "GET", MANAGER2 + "/", 200);
        Assert.assertTrue(client.getResponseBody().contains("shell-main"));

        client.disconnect();
    }


    @Test
    public void testPostLoginReturnsToSpaRoute() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();

        // Unauthenticated deep link to an SPA route: the server gates it to
        // the login page and must remember the route for the post-login
        // redirect (this is what the SPA does when the session expires
        // while the user is on that page).
        requestRaw(client, "GET", MANAGER2 + "/apps", 200);
        Assert.assertTrue(client.getResponseBody().contains("j_security_check"));
        Assert.assertNotNull("Expected a session to be established", client.getSessionId());

        // Submit the login form with the same session.
        String body = "j_username=manager1&j_password=secret";
        client.setRequest(new String[] { "POST " + MANAGER2 + "/j_security_check HTTP/1.1" + CRLF,
                "Host: localhost:" + getPort() + CRLF, "Cookie: JSESSIONID=" + client.getSessionId() + CRLF,
                "Content-Type: application/x-www-form-urlencoded" + CRLF,
                "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + CRLF, "Connection: Close" + CRLF,
                CRLF, body });
        client.connect();
        client.processRequest(true);

        Assert.assertEquals(303, client.getStatusCode());

        // The post-login redirect must return the user to the page they
        // were on, not to the application root.
        String location = null;
        for (String header : client.getResponseHeaders()) {
            if (header.toLowerCase().startsWith("location:")) {
                location = header.substring("location:".length()).trim();
            }
        }
        Assert.assertEquals(MANAGER2 + "/apps", location);

        // The redirect target serves the SPA shell.
        requestRaw(client, "GET", MANAGER2 + "/apps", 200);
        Assert.assertTrue(client.getResponseBody().contains("shell-main"));

        client.disconnect();
    }


    @Test
    public void testWrongPasswordShowsErrorPage() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();

        // Show the login page (establishes the session).
        requestRaw(client, "GET", MANAGER2 + "/", 200);
        Assert.assertTrue(client.getResponseBody().contains("j_security_check"));

        // Submit the login form with a bad password.
        String body = "j_username=manager1&j_password=wrong";
        client.setRequest(new String[] { "POST " + MANAGER2 + "/j_security_check HTTP/1.1" + CRLF,
                "Host: localhost:" + getPort() + CRLF, "Cookie: JSESSIONID=" + client.getSessionId() + CRLF,
                "Content-Type: application/x-www-form-urlencoded" + CRLF,
                "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + CRLF, "Connection: Close" + CRLF,
                CRLF, body });
        client.connect();
        client.processRequest(true);

        // The error page is the login page with the error message shown.
        Assert.assertEquals(200, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("j_security_check"));
        Assert.assertTrue(client.getResponseBody().contains("Sign in failed"));

        client.disconnect();
    }


    @Test
    public void testWrongPasswordStaysUnauthenticated() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();

        // Establish a session via a protected resource.
        client.setRequest(new String[] { "GET " + MANAGER2 + "/api/info HTTP/1.1" + CRLF,
                "Host: localhost:" + getPort() + CRLF, "Connection: Close" + CRLF, CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertNotNull(client.getSessionId());

        // Submit the login form with a bad password.
        String body = "j_username=manager1&j_password=wrong";
        client.setRequest(new String[] { "POST " + MANAGER2 + "/j_security_check HTTP/1.1" + CRLF,
                "Host: localhost:" + getPort() + CRLF, "Cookie: JSESSIONID=" + client.getSessionId() + CRLF,
                "Content-Type: application/x-www-form-urlencoded" + CRLF,
                "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + CRLF, "Connection: Close" + CRLF,
                CRLF, body });
        client.connect();
        client.processRequest(true);
        Assert.assertNotEquals(303, client.getStatusCode());

        // Still unauthenticated: API requests are forwarded to the login
        // page.
        client.setRequest(
                new String[] { "GET " + MANAGER2 + "/api/info HTTP/1.1" + CRLF, "Host: localhost:" + getPort() + CRLF,
                        "Cookie: JSESSIONID=" + client.getSessionId() + CRLF, "Connection: Close" + CRLF, CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(200, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("j_security_check"));

        client.disconnect();
    }


    @Test
    public void testMutateWithoutCsrfTokenIsRejected() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        request(client, "POST", MANAGER2 + "/api/apps/testapp/stop?path=%2Ftestapp", null, "{}", 403);

        client.disconnect();
    }
}
