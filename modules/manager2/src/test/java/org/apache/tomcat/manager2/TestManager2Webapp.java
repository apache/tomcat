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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.Assert;
import org.junit.Test;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.Context;
import org.apache.catalina.Server;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.HostConfig;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.users.MemoryUserDatabase;
import org.apache.catalina.valves.AccessLogValve;
import org.apache.tomcat.util.descriptor.web.ContextResource;

/**
 * Integration tests for the manager2 web application. The tests deploy the {@code manager2.war} built by this module
 * (via the {@code deploy} target) into a throw-away Tomcat instance and drive it over HTTP with
 * {@link SimpleHttpClient}, exercising FORM login, CSRF protection, role based access control and the JSON API.
 */
public class TestManager2Webapp extends TomcatBaseTest {

    private static final String MANAGER2 = "/manager2";
    private static final String TESTAPP = "/testapp";

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
    public void testStaticAssetsArePublic() throws Exception {
        setup(false);

        // Regression test: a security constraint with the pattern "/" matches
        // every request in the context. When the SPA shell was protected with
        // such a constraint, the login page's own CSS and JS requests were
        // sent through FORM authentication (leaving the login page unstyled)
        // and the saved request pointed at an asset file.
        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();

        requestRaw(client, "GET", MANAGER2 + "/css/manager2.css", 200);
        boolean css = false;
        for (String header : client.getResponseHeaders()) {
            if (header.toLowerCase().startsWith("content-type:") && header.contains("text/css")) {
                css = true;
            }
        }
        Assert.assertTrue("Expected text/css for the stylesheet", css);

        requestRaw(client, "GET", MANAGER2 + "/js/main.js", 200);

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
    public void testAuthenticatedRootServesShell() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        // The context root serves the SPA shell to authenticated users.
        requestRaw(client, "GET", MANAGER2 + "/", 200);
        Assert.assertTrue(client.getResponseBody().contains("shell-main"));
        Assert.assertTrue(client.getResponseBody().contains("js/main.js"));

        // The SPA deep-link routes serve the shell as well (deep links survive
        // a reload).
        requestRaw(client, "GET", MANAGER2 + "/apps", 200);
        Assert.assertTrue(client.getResponseBody().contains("shell-main"));

        // A multi-segment deep link (e.g. an application detail page) must also
        // serve the shell, with a base element so the shell's relative asset URLs
        // resolve against the context instead of the deep path.
        requestRaw(client, "GET", MANAGER2 + "/apps/localhost/myapp", 200);
        Assert.assertTrue(client.getResponseBody().contains("shell-main"));
        Assert.assertTrue(client.getResponseBody().contains("<base href=\"" + MANAGER2 + "/\">"));

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


    @Test
    public void testAppLifecycleAndList() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        // The list of applications includes the test app.
        request(client, "GET", MANAGER2 + "/api/apps", null, null, 200);
        Assert.assertTrue(client.getResponseBody().contains("\"path\":\"" + TESTAPP + "\""));

        // The test app is deployed and serving.
        requestRaw(client, "GET", TESTAPP + "/", 200);

        // Stop the test app.
        request(client, "POST", MANAGER2 + "/api/apps/testapp/stop?path=%2Ftestapp", token, "{}", 200);
        Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
        requestRaw(client, "GET", TESTAPP + "/", 404);

        // Start it again.
        request(client, "POST", MANAGER2 + "/api/apps/testapp/start?path=%2Ftestapp", token, "{}", 200);
        Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
        requestRaw(client, "GET", TESTAPP + "/", 200);

        // Undeploy it.
        request(client, "DELETE", MANAGER2 + "/api/apps/testapp?path=%2Ftestapp", token, null, 200);
        Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
        requestRaw(client, "GET", TESTAPP + "/", 404);

        client.disconnect();
    }


    @Test
    public void testStatusEndpoints() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        request(client, "GET", MANAGER2 + "/api/status", null, null, 200);
        Assert.assertTrue(client.getResponseBody().contains("\"jvm\""));
        Assert.assertTrue(client.getResponseBody().contains("\"memory\""));

        request(client, "GET", MANAGER2 + "/api/status/workers", null, null, 200);
        Assert.assertTrue(client.getResponseBody().startsWith("["));

        request(client, "GET", MANAGER2 + "/api/status/apps/testapp?path=%2Ftestapp", null, null, 200);
        Assert.assertTrue(client.getResponseBody().contains("\"wrappers\""));
        Assert.assertTrue(client.getResponseBody().contains("\"state\":\"STARTED\""));

        client.disconnect();
    }


    @Test
    public void testStatusSystemEndpoint() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        // The instant CPU and memory snapshot.
        request(client, "GET", MANAGER2 + "/api/status/system", null, null, 200);
        String body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"cpu\""));
        Assert.assertTrue(body.contains("\"memory\""));
        Assert.assertTrue(body.contains("\"availableProcessors\""));
        Assert.assertTrue(body.contains("\"systemLoad\""));
        Assert.assertTrue(body.contains("\"processLoad\""));
        Assert.assertTrue(body.contains("\"loadAverage\""));
        Assert.assertTrue(body.contains("\"threads\""));
        Assert.assertTrue(body.contains("\"heap\""));
        Assert.assertTrue(body.contains("\"nonHeap\""));
        Assert.assertTrue(body.contains("\"pools\""));
        // The JVM runs on at least one core, so the snapshot must not report
        // an unusable machine.
        Assert.assertFalse(body.contains("\"availableProcessors\":0"));
        // The heap is always present and in use by the running webapp.
        Assert.assertFalse(body.contains("\"heap\":{\"used\":0,"));

        client.disconnect();
    }


    @Test
    public void testStatusHistory() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        // The StatusApi servlet is load-on-startup, so the background
        // collection already runs at deployment time. The response reports
        // the configured defaults (10 minute window, 2 second tick).
        request(client, "GET", MANAGER2 + "/api/status/history", null, null, 200);
        String body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"windowMs\":600000"));
        Assert.assertTrue(body.contains("\"tickMs\":2000"));
        Assert.assertTrue(body.contains("\"samples\""));

        // Wait for at least two more ticks: every sample after the first has
        // a baseline, so its rates must be non-null.
        Thread.sleep(4500);
        request(client, "GET", MANAGER2 + "/api/status/history", null, null, 200);
        body = client.getResponseBody();
        int sampleCount = body.split("\"ts\":", -1).length - 1;
        Assert.assertTrue("Expected at least 2 samples, found " + sampleCount, sampleCount >= 2);
        int nullRates = body.split("\"rps\":null", -1).length - 1;
        Assert.assertTrue("Expected at least one sample with a non-null rate", nullRates < sampleCount);

        client.disconnect();
    }


    @Test
    public void testHostsEndpoint() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        request(client, "GET", MANAGER2 + "/api/hosts", null, null, 200);
        Assert.assertTrue(client.getResponseBody().contains("\"name\":\"localhost\""));
        // The default host is up, so it must be reported as started
        // (state STARTED is not the same thing as the raw state name the
        // UI used to match on).
        Assert.assertTrue(client.getResponseBody().contains("\"state\":\"STARTED\""));
        Assert.assertTrue(client.getResponseBody().contains("\"started\":true"));
        Assert.assertTrue(client.getResponseBody().contains("\"self\":true"));

        client.disconnect();
    }


    @Test
    public void testResourcesEndpointDropsStatusLine() throws Exception {
        setup(false, true);

        File xmlFile = new File(getTemporaryDirectory(), "tomcat-users-resources.xml");
        writeUserDatabaseXml(xmlFile, "");
        addUserDatabase("UserDatabase", xmlFile, false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        request(client, "GET", MANAGER2 + "/api/resources", null, null, 200);
        String body = client.getResponseBody();
        // The registered resource is listed with its class name (the
        // resolved implementation class of the factory).
        Assert.assertTrue(body.contains("UserDatabase:org.apache.catalina.users.MemoryUserDatabase"));
        // The human readable status line the classic manager renders first
        // ("OK - Listed global resources of all types") is not part of the
        // resource list.
        Assert.assertFalse(body.contains("Listed global resources"));

        client.disconnect();
    }


    @Test
    public void testReadOnlyRoleCannotMutate() throws Exception {
        setup(true);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "status1");

        // Read access to the status endpoints is allowed.
        request(client, "GET", MANAGER2 + "/api/status", null, null, 200);
        Assert.assertTrue(client.getResponseBody().contains("\"jvm\""));

        // Mutations are not (the CSRF filter and the security constraints
        // both reject the request).
        String token = getCsrfToken(client);
        request(client, "POST", MANAGER2 + "/api/apps/testapp/stop?path=%2Ftestapp", token, "{}", 403);

        client.disconnect();
    }


    @Test
    public void testDeployFromServerWar() throws Exception {
        setup(false);

        File warFile = createTestWar();

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        // Deploy from a server-side WAR location.
        String body = "{\"path\":\"/deployed\",\"war\":\"file://" + warFile.getAbsolutePath() + "\"}";
        request(client, "POST", MANAGER2 + "/api/apps/deploy", token, body, 200);
        Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
        requestRaw(client, "GET", "/deployed/", 200);

        // Undeploy again.
        request(client, "DELETE", MANAGER2 + "/api/apps/deployed?path=%2Fdeployed", token, null, 200);
        Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));

        client.disconnect();
    }


    @Test
    public void testSessionsFlow() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();

        // Create a session in the test app.
        requestRaw(client, "GET", TESTAPP + "/session.jsp", 200);

        String token = loginAndGetToken(client, "manager1");

        // List sessions.
        request(client, "GET", MANAGER2 + "/api/apps/testapp/sessions?path=%2Ftestapp", null, null, 200);
        String body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"sessions\":["));
        int idStart = body.indexOf("\"id\":\"");
        Assert.assertTrue("Expected at least one session", idStart >= 0);
        String sessionId = body.substring(idStart + 6, body.indexOf('"', idStart + 6));

        // Session detail includes attributes.
        request(client, "GET", MANAGER2 + "/api/apps/testapp/sessions/" + sessionId + "?path=%2Ftestapp", null, null,
                200);
        Assert.assertTrue(client.getResponseBody().contains("\"name\":\"testAttr\""));

        // Invalidate the session.
        request(client, "POST", MANAGER2 + "/api/apps/testapp/sessions/invalidate?path=%2Ftestapp", token,
                "{\"ids\":[\"" + sessionId + "\"]}", 200);
        Assert.assertTrue(client.getResponseBody().contains("\"count\":1"));

        client.disconnect();
    }


    @Test
    public void testLogFilesListAndSeverityFilter() throws Exception {
        setup(false);

        File logsDir = new File(getTemporaryDirectory(), "logs");
        Assert.assertTrue(logsDir.isDirectory() || logsDir.mkdirs());
        writeLogFile(new File(logsDir, "catalina.2026-01-01.log"),
                "01-Jan-2026 10:00:00.001 INFO [main] org.apache.test.A.start Server starting\n" +
                        "01-Jan-2026 10:00:01.002 WARNING [http] org.apache.test.B.warn Something fishy\n" +
                        "01-Jan-2026 10:00:02.003 SEVERE [http] org.apache.test.C.fail It broke\n" +
                        "\tat org.apache.test.C.fail(C.java:10)\n" +
                        "01-Jan-2026 10:00:03.004 INFO [main] org.apache.test.D.done Server done\n");
        writeLogFile(new File(logsDir, "localhost.2026-01-02.log"),
                "{\"time\": \"2026-01-02T10:00:00.001Z\", \"level\": \"INFO\", \"thread\": \"main\"," +
                        " \"class\": \"org.apache.test.A\", \"method\": \"start\", \"message\": \"boot\"}\n" +
                        "{\"time\": \"2026-01-02T10:00:01.002Z\", \"level\": \"SEVERE\", \"thread\": \"http\"," +
                        " \"class\": \"org.apache.test.C\", \"method\": \"fail\", \"message\": \"boom\"," +
                        " \"throwable\": [\"java.lang.IllegalStateException: boom\"," +
                        " \" at org.apache.test.C.fail(C.java:10)\"]}\n");

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        // The list contains both files with their detected formats and not
        // the access log files.
        request(client, "GET", MANAGER2 + "/api/logs", null, null, 200);
        String list = client.getResponseBody();
        Assert.assertTrue(list.contains("\"name\":\"catalina.2026-01-01.log\""));
        Assert.assertTrue(list.contains("\"name\":\"localhost.2026-01-02.log\""));
        Assert.assertFalse(list.contains("access_log"));

        // The plain text log: all four records, the level counts and the
        // stack trace attached to the SEVERE record.
        request(client, "GET", MANAGER2 + "/api/logs/file?name=catalina.2026-01-01.log", null, null, 200);
        String body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"format\":\"text\""));
        Assert.assertTrue(body.contains("\"total\":4"));
        Assert.assertTrue(body.contains("\"SEVERE\":1"));
        Assert.assertTrue(body.contains("\"WARNING\":1"));
        Assert.assertTrue(body.contains("\"INFO\":2"));
        Assert.assertTrue(body.contains("at org.apache.test.C.fail"));
        // The records are ordered from most recent to least recent.
        Assert.assertTrue(body.indexOf("Server done") < body.indexOf("Server starting"));

        // The severity filter keeps only the SEVERE record.
        request(client, "GET", MANAGER2 + "/api/logs/file?name=catalina.2026-01-01.log&level=SEVERE", null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"matched\":1"));
        Assert.assertTrue(body.contains("It broke"));
        Assert.assertFalse(body.contains("Server done"));

        // The JSON log is parsed as well and can be filtered by level.
        request(client, "GET", MANAGER2 + "/api/logs/file?name=localhost.2026-01-02.log&level=SEVERE", null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"format\":\"json\""));
        Assert.assertTrue(body.contains("\"matched\":1"));
        Assert.assertTrue(body.contains("boom"));

        // A free text search works across the fields.
        request(client, "GET", MANAGER2 + "/api/logs/file?name=catalina.2026-01-01.log&search=fishy", null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"matched\":1"));
        Assert.assertTrue(body.contains("Something fishy"));

        // Invalid file names are rejected.
        request(client, "GET", MANAGER2 + "/api/logs/file?name=..%2Fweb.xml", null, null, 400);

        // The full raw file can be downloaded, unfiltered and without a line
        // limit.
        request(client, "GET", MANAGER2 + "/api/logs/download?name=catalina.2026-01-01.log", null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("Server starting"));
        Assert.assertTrue(body.contains("Server done"));
        // Invalid file names are rejected for downloads as well.
        request(client, "GET", MANAGER2 + "/api/logs/download?name=..%2Fweb.xml", null, null, 400);

        client.disconnect();
    }


    @Test
    public void testAccessLogTextPatternAndFilters() throws Exception {
        setup(false);

        File logsDir = new File(getTemporaryDirectory(), "logs");
        Assert.assertTrue(logsDir.isDirectory() || logsDir.mkdirs());
        writeLogFile(new File(logsDir, "localhost_access_log.2026-01-01.txt"),
                "127.0.0.1 - - [01/Jan/2026:10:00:00 +0000] \"GET /ok HTTP/1.1\" 200 100 ABCDEF\n" +
                        "127.0.0.1 - - [01/Jan/2026:10:00:01 +0000] \"GET /missing HTTP/1.1\" 404 55 -\n" +
                        "127.0.0.1 - manager1 [01/Jan/2026:10:00:02 +0000] \"POST /submit HTTP/1.1\" 500 10 GHIJKL\n");

        // Configure an access log valve with a pattern that also logs the
        // session ID: the available fields (and filters) must follow the
        // configured pattern.
        AccessLogValve valve = new AccessLogValve();
        valve.setDirectory(new File(getTemporaryDirectory(), "valve-logs").getAbsolutePath());
        valve.setPrefix("valve");
        valve.setPattern("%h %l %u %t \"%r\" %s %b %S");
        getTomcatInstance().getHost().getPipeline().addValve(valve);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        // The list reports the file, the pattern and the derived fields.
        request(client, "GET", MANAGER2 + "/api/access-log", null, null, 200);
        String list = client.getResponseBody();
        Assert.assertTrue(list.contains("\"name\":\"localhost_access_log.2026-01-01.txt\""));
        Assert.assertTrue(list.contains("%S"));
        Assert.assertTrue(list.contains("sessionId"));

        // All three records are parsed; the method, path and protocol are
        // derived from the request line and the status is a number.
        request(client, "GET", MANAGER2 + "/api/access-log/file?name=localhost_access_log.2026-01-01.txt", null, null,
                200);
        String body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"format\":\"text\""));
        Assert.assertTrue(body.contains("\"total\":3"));
        Assert.assertTrue(body.contains("\"2xx\":1"));
        Assert.assertTrue(body.contains("\"4xx\":1"));
        Assert.assertTrue(body.contains("\"5xx\":1"));
        Assert.assertTrue(body.contains("\"method\":\"GET\""));
        Assert.assertTrue(body.contains("\"path\":\"/missing\""));
        Assert.assertTrue(body.contains("\"protocol\":\"HTTP/1.1\""));
        Assert.assertTrue(body.contains("\"statusCode\":404"));
        Assert.assertTrue(body.contains("\"sessionId\":\"ABCDEF\""));
        Assert.assertTrue(body.contains("\"user\":\"manager1\""));
        // The records are ordered from most recent to least recent.
        Assert.assertTrue(body.indexOf("/submit") < body.indexOf("/ok"));

        // Filter by status class.
        request(client, "GET", MANAGER2 + "/api/access-log/file?name=localhost_access_log.2026-01-01.txt&status=4xx",
                null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"matched\":1"));
        Assert.assertTrue(body.contains("/missing"));

        // Filter by method.
        request(client, "GET", MANAGER2 + "/api/access-log/file?name=localhost_access_log.2026-01-01.txt&method=POST",
                null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"matched\":1"));
        Assert.assertTrue(body.contains("manager1"));

        // Filter by user.
        request(client, "GET", MANAGER2 + "/api/access-log/file?name=localhost_access_log.2026-01-01.txt&user=manager1",
                null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"matched\":1"));

        // Filter by session ID.
        request(client, "GET",
                MANAGER2 + "/api/access-log/file?name=localhost_access_log.2026-01-01.txt&session=GHIJKL", null, null,
                200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"matched\":1"));
        Assert.assertTrue(body.contains("/submit"));

        // The full raw file can be downloaded.
        request(client, "GET", MANAGER2 + "/api/access-log/download?name=localhost_access_log.2026-01-01.txt",
                null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("GET /ok HTTP/1.1"));
        Assert.assertTrue(body.contains("POST /submit HTTP/1.1"));

        client.disconnect();
    }


    @Test
    public void testAccessLogJsonAndSessionFilter() throws Exception {
        setup(false);

        File logsDir = new File(getTemporaryDirectory(), "logs");
        Assert.assertTrue(logsDir.isDirectory() || logsDir.mkdirs());
        writeLogFile(new File(logsDir, "localhost_access_log.2026-01-02.txt"),
                "{\"host\": \"127.0.0.1\", \"user\": \"manager1\"," +
                        " \"time\": \"[02/Jan/2026:10:00:00 +0000]\", \"method\": \"GET\"," +
                        " \"path\": \"/\", \"protocol\": \"HTTP/1.1\"," +
                        " \"statusCode\": \"200\", \"size\": \"10\", \"sessionId\": \"AAAA-1111\"}\n" +
                        "{\"host\": \"127.0.0.1\", \"user\": \"-\"," +
                        " \"time\": \"[02/Jan/2026:10:00:01 +0000]\", \"method\": \"GET\"," +
                        " \"path\": \"/forbidden\", \"protocol\": \"HTTP/1.1\"," +
                        " \"statusCode\": \"403\", \"size\": \"5\", \"sessionId\": \"CCCC-2222\"}\n");

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        // The JSON access log is detected and parsed; the status is
        // converted to a number and the "-" marker to null.
        request(client, "GET", MANAGER2 + "/api/access-log/file?name=localhost_access_log.2026-01-02.txt", null, null,
                200);
        String body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"format\":\"json\""));
        Assert.assertTrue(body.contains("\"total\":2"));
        Assert.assertTrue(body.contains("\"sessionId\""));
        Assert.assertTrue(body.contains("\"statusCode\":403"));
        Assert.assertTrue(body.contains("\"user\":null"));

        // Filter by session ID.
        request(client, "GET", MANAGER2 + "/api/access-log/file?name=localhost_access_log.2026-01-02.txt&session=AAAA",
                null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"matched\":1"));
        Assert.assertTrue(body.contains("\"user\":\"manager1\""));

        // Filter by status class.
        request(client, "GET", MANAGER2 + "/api/access-log/file?name=localhost_access_log.2026-01-02.txt&status=4xx",
                null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"matched\":1"));
        Assert.assertTrue(body.contains("/forbidden"));

        // The full raw file can be downloaded.
        request(client, "GET", MANAGER2 + "/api/access-log/download?name=localhost_access_log.2026-01-02.txt",
                null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("AAAA-1111"));
        Assert.assertTrue(body.contains("/forbidden"));

        client.disconnect();
    }


    @Test
    public void testLogApiReadOnlyRoleDenied() throws Exception {
        setup(true);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "status1");

        // The log API is not part of the read-only status endpoints.
        request(client, "GET", MANAGER2 + "/api/logs", null, null, 403);
        request(client, "GET", MANAGER2 + "/api/access-log", null, null, 403);
        request(client, "GET", MANAGER2 + "/api/logs/download?name=catalina.log", null, null, 403);
        request(client, "GET", MANAGER2 + "/api/access-log/download?name=localhost_access_log.txt", null, null, 403);

        client.disconnect();
    }


    @Test
    public void testUsersApiWithoutUserDatabase() throws Exception {
        setup(false);

        // The programmatic test instance has no UserDatabase JNDI resource
        // configured, so the API reports that no user database is available.
        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        request(client, "GET", MANAGER2 + "/api/users", null, null, 404);
        String body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"error\":\"USER_DATABASE_MISSING\""));

        // Mutations are rejected for the same reason.
        request(client, "POST", MANAGER2 + "/api/users", token, "{\"username\":\"x\",\"password\":\"y\"}", 404);

        client.disconnect();
    }


    @Test
    public void testUserDatabaseReadonlyRejectsMutations() throws Exception {
        setup(false, true);

        File xmlFile = new File(getTemporaryDirectory(), "tomcat-users-readonly.xml");
        writeUserDatabaseXml(xmlFile, "  <role rolename=\"manager-gui\"/>\n" +
                "  <user username=\"manager1\" password=\"secret\" roles=\"manager-gui\"/>\n");
        addUserDatabase("Users", xmlFile, true);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        // The list reports the database as read-only.
        request(client, "GET", MANAGER2 + "/api/users", null, null, 200);
        String body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"name\":\"Users\""));
        Assert.assertTrue(body.contains("\"readonly\":true"));
        Assert.assertTrue(body.contains("\"username\":\"manager1\""));
        Assert.assertTrue(body.contains("\"rolename\":\"manager-gui\""));

        // Mutations are rejected while the database is read-only.
        request(client, "POST", MANAGER2 + "/api/users", token, "{\"username\":\"bob\",\"password\":\"secret\"}", 400);
        Assert.assertTrue(client.getResponseBody().contains("USER_DATABASE_READONLY"));

        client.disconnect();
    }


    @Test
    public void testUsersAndGroupsCrudAndPersistence() throws Exception {
        setup(false, true);

        File xmlFile = new File(getTemporaryDirectory(), "tomcat-users-test.xml");
        writeUserDatabaseXml(xmlFile, "  <role rolename=\"manager-gui\"/>\n" +
                "  <user username=\"manager1\" password=\"secret\" roles=\"manager-gui\"/>\n");
        addUserDatabase("Users", xmlFile, false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        // Create a user with a new role; the change is persisted to the XML
        // file.
        request(client, "POST", MANAGER2 + "/api/users", token,
                "{\"username\":\"alice\",\"password\":\"wonderland\",\"fullName\":\"Alice\"," +
                        "\"roles\":[\"manager-gui\",\"ops\"]}",
                200);
        Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
        Assert.assertTrue(readFile(xmlFile).contains("username=\"alice\""));
        Assert.assertTrue(readFile(xmlFile).contains("rolename=\"ops\""));

        // The list shows the user, its roles and the new role definition.
        request(client, "GET", MANAGER2 + "/api/users", null, null, 200);
        String body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"username\":\"alice\""));
        Assert.assertTrue(body.contains("\"fullName\":\"Alice\""));
        Assert.assertTrue(body.contains("\"hasPassword\":true"));
        Assert.assertTrue(body.contains("\"rolename\":\"ops\""));

        // The password can be changed and is persisted.
        request(client, "POST", MANAGER2 + "/api/users/alice/password", token, "{\"password\":\"newsecret\"}", 200);
        Assert.assertTrue(readFile(xmlFile).contains("password=\"newsecret\""));

        // The roles of a user can be replaced.
        request(client, "POST", MANAGER2 + "/api/users/alice/roles", token, "{\"roles\":[\"ops\"]}", 200);
        request(client, "GET", MANAGER2 + "/api/users", null, null, 200);
        Assert.assertTrue(client.getResponseBody()
                .contains("\"username\":\"alice\",\"fullName\":\"Alice\",\"hasPassword\":true,\"roles\":[\"ops\"]"));

        // Groups can be created, their members and roles set, and removed.
        request(client, "POST", MANAGER2 + "/api/groups", token,
                "{\"groupname\":\"staff\",\"description\":\"The staff\",\"roles\":[\"ops\"]}", 200);
        request(client, "POST", MANAGER2 + "/api/groups/staff/members", token, "{\"members\":[\"alice\",\"manager1\"]}",
                200);
        request(client, "GET", MANAGER2 + "/api/users", null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"groupname\":\"staff\""));
        Assert.assertTrue(body.contains("\"description\":\"The staff\""));
        Assert.assertTrue(body.contains("\"members\":[\"alice\",\"manager1\"]"));
        // alice inherits the group role in addition to her direct roles.
        Assert.assertTrue(body.contains("\"effectiveRoles\":[\"manager-gui\",\"ops\"]"));
        // The group is persisted.
        Assert.assertTrue(readFile(xmlFile).contains("groupname=\"staff\""));

        // Unknown members are rejected.
        request(client, "POST", MANAGER2 + "/api/groups/staff/members", token, "{\"members\":[\"nobody\"]}", 400);
        Assert.assertTrue(client.getResponseBody().contains("UNKNOWN_GROUP_MEMBER"));

        // The group roles can be replaced.
        request(client, "POST", MANAGER2 + "/api/groups/staff/roles", token, "{\"roles\":[\"manager-status\"]}", 200);

        // Removing the group detaches it from its members.
        request(client, "DELETE", MANAGER2 + "/api/groups/staff", token, null, 200);
        request(client, "GET", MANAGER2 + "/api/users", null, null, 200);
        body = client.getResponseBody();
        Assert.assertFalse(body.contains("\"groupname\":\"staff\""));
        Assert.assertFalse(body.contains("\"members\":[\"alice\""));

        // Roles can be created explicitly (with a description) and are
        // persisted to the XML file.
        request(client, "POST", MANAGER2 + "/api/roles", token,
                "{\"rolename\":\"audit\",\"description\":\"Audit role\"}", 200);
        Assert.assertTrue(readFile(xmlFile).contains("rolename=\"audit\""));
        request(client, "GET", MANAGER2 + "/api/users", null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"rolename\":\"audit\",\"description\":\"Audit role\""));

        // An existing role name is rejected.
        request(client, "POST", MANAGER2 + "/api/roles", token, "{\"rolename\":\"audit\"}", 409);
        Assert.assertTrue(client.getResponseBody().contains("ROLE_EXISTS"));

        // Removing a role detaches it from the users that hold it.
        request(client, "POST", MANAGER2 + "/api/users/alice/roles", token, "{\"roles\":[\"ops\",\"audit\"]}", 200);
        request(client, "DELETE", MANAGER2 + "/api/roles/audit", token, null, 200);
        request(client, "GET", MANAGER2 + "/api/users", null, null, 200);
        body = client.getResponseBody();
        Assert.assertFalse(body.contains("\"rolename\":\"audit\""));
        Assert.assertTrue(body.contains("\"roles\":[\"ops\"]"));
        Assert.assertFalse(readFile(xmlFile).contains("rolename=\"audit\""));

        // An unknown role is reported.
        request(client, "DELETE", MANAGER2 + "/api/roles/ghost", token, null, 404);
        Assert.assertTrue(client.getResponseBody().contains("ROLE_NOT_FOUND"));

        // A role held by the signed-in account cannot be removed.
        request(client, "DELETE", MANAGER2 + "/api/roles/manager-gui", token, null, 400);
        Assert.assertTrue(client.getResponseBody().contains("SELF_ROLE_REMOVAL"));

        // Removing a user removes it from the database and the file.
        request(client, "DELETE", MANAGER2 + "/api/users/alice", token, null, 200);
        Assert.assertFalse(readFile(xmlFile).contains("username=\"alice\""));

        // Existing names are rejected, unknown ones reported.
        request(client, "POST", MANAGER2 + "/api/users", token, "{\"username\":\"manager1\",\"password\":\"x\"}", 409);
        Assert.assertTrue(client.getResponseBody().contains("USER_EXISTS"));
        request(client, "POST", MANAGER2 + "/api/groups", token, "{\"groupname\":\"staff\"}", 200);
        request(client, "POST", MANAGER2 + "/api/groups", token, "{\"groupname\":\"staff\"}", 409);
        Assert.assertTrue(client.getResponseBody().contains("GROUP_EXISTS"));
        request(client, "DELETE", MANAGER2 + "/api/users/ghost", token, null, 404);
        Assert.assertTrue(client.getResponseBody().contains("USER_NOT_FOUND"));
        request(client, "DELETE", MANAGER2 + "/api/groups/ghost", token, null, 404);
        Assert.assertTrue(client.getResponseBody().contains("GROUP_NOT_FOUND"));

        // The signed-in account cannot remove itself.
        request(client, "DELETE", MANAGER2 + "/api/users/manager1", token, null, 400);
        Assert.assertTrue(client.getResponseBody().contains("SELF_REMOVAL"));

        client.disconnect();
    }


    @Test
    public void testUsersApiReadOnlyRoleDenied() throws Exception {
        setup(true);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "status1");

        // The users API is not part of the read-only status endpoints.
        request(client, "GET", MANAGER2 + "/api/users", null, null, 403);
        request(client, "GET", MANAGER2 + "/api/groups", null, null, 403);

        client.disconnect();
    }


    // -----------------------------------------------------------------------

    private void setup(boolean withReadOnlyUser) throws Exception {
        setup(withReadOnlyUser, false);
    }


    private void setup(boolean withReadOnlyUser, boolean withNaming) throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.setAddDefaultWebXmlToWebapp(false);
        if (withNaming) {
            // Enable JNDI naming so that a UserDatabase JNDI resource can be
            // registered on the global naming context of the server, like the
            // file based one of the default server.xml.
            tomcat.enableNaming();
        }
        tomcat.addUser("manager1", "secret");
        tomcat.addRole("manager1", "manager-gui");
        if (withReadOnlyUser) {
            tomcat.addUser("status1", "secret");
            tomcat.addRole("status1", "manager-status");
        }

        File webapps = new File(getBuildDirectory(), "webapps");
        File appDir = new File(webapps, "manager2");
        File appWar = new File(webapps, "manager2.war");
        File source = appDir.exists() ? appDir : appWar;
        Assert.assertTrue("manager2 webapp missing - run 'ant deploy' first", source.exists());
        Context manager2 = tomcat.addWebapp(null, MANAGER2, source.getAbsolutePath());
        addDefaultServlet(manager2);
        // The programmatic test instance has no global web.xml, so the MIME
        // type mappings that a normal deployment gets from it are missing.
        // The default servlet needs them to set the correct content type for
        // the static resources.
        manager2.addMimeMapping("css", "text/css");
        manager2.addMimeMapping("js", "text/javascript");
        manager2.addMimeMapping("html", "text/html");

        // The programmatic test instance does not create a HostConfig for
        // the default host, so the Deployer MBean that the deploy API uses
        // (HostConfig registers itself under "type=Deployer") is missing.
        // The test app is deployed from the host app base, like a real
        // webapps/ directory, so that undeploy is allowed.
        File appBase = new File(TEMP_DIR, "manager2-test-appbase");
        deleteRecursive(appBase);
        Assert.assertTrue(appBase.mkdirs());
        createTestWebapp(new File(appBase, "testapp"));
        org.apache.catalina.Host host = tomcat.getHost();
        host.setAppBase(appBase.getAbsolutePath());
        host.addLifecycleListener(new HostConfig());

        tomcat.start();
    }


    /**
     * The programmatic test instance has no global web.xml, so the default servlet that serves static resources in a
     * normal deployment has to be added explicitly. Without it, FORM authentication cannot forward to the login page.
     */
    private void addDefaultServlet(Context context) {
        Tomcat.addServlet(context, "default", new DefaultServlet());
        context.addServletMapping("/", "default");
    }


    /**
     * Register a file based {@link MemoryUserDatabase} as a JNDI resource of the global naming context of the test
     * server, like the {@code UserDatabase} resource of the default {@code server.xml}. Requires a setup with
     * {@code withNaming = true}.
     */
    private MemoryUserDatabase addUserDatabase(String name, File xmlFile, boolean readonly) throws Exception {
        Server server = getTomcatInstance().getServer();
        ContextResource resource = new ContextResource();
        resource.setName(name);
        resource.setType("org.apache.catalina.UserDatabase");
        resource.setProperty("factory", "org.apache.catalina.users.MemoryUserDatabaseFactory");
        resource.setProperty("pathname", xmlFile.getAbsolutePath());
        if (!readonly) {
            resource.setProperty("readonly", "false");
        }
        server.getGlobalNamingResources().addResource(resource);
        return (MemoryUserDatabase) server.getGlobalNamingContext().lookup(name);
    }


    /**
     * Establish a login session for the given user via the FORM login.
     */
    private void login(SimpleHttpClient client, String user) throws Exception {
        // Establish a session (and the "saved request") via a protected
        // resource.
        client.setRequest(new String[] { "GET " + MANAGER2 + "/api/info HTTP/1.1" + CRLF,
                "Host: localhost:" + getPort() + CRLF, "Connection: Close" + CRLF, CRLF });
        client.connect();
        client.processRequest(true);

        Assert.assertNotNull("Expected a session to be established", client.getSessionId());

        // Submit the login form.
        String body = "j_username=" + user + "&j_password=secret";
        client.setRequest(new String[] { "POST " + MANAGER2 + "/j_security_check HTTP/1.1" + CRLF,
                "Host: localhost:" + getPort() + CRLF, "Cookie: JSESSIONID=" + client.getSessionId() + CRLF,
                "Content-Type: application/x-www-form-urlencoded" + CRLF,
                "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + CRLF, "Connection: Close" + CRLF,
                CRLF, body });
        client.connect();
        client.processRequest(true);

        Assert.assertEquals(303, client.getStatusCode());
    }


    /**
     * Log in and return the CSRF token for the new session. The login response (303) does not carry a token, so a
     * read-only endpoint is requested afterwards to issue one.
     */
    private String loginAndGetToken(SimpleHttpClient client, String user) throws Exception {
        login(client, user);
        request(client, "GET", MANAGER2 + "/api/info", null, null, 200);
        String token = getCsrfToken(client);
        Assert.assertNotNull("Expected an X-CSRF-Token header", token);
        return token;
    }


    private void request(SimpleHttpClient client, String method, String path, String token, String body,
            int expectedStatus) throws Exception {
        StringBuilder request = new StringBuilder();
        request.append(method).append(' ').append(path).append(" HTTP/1.1").append(CRLF);
        request.append("Host: localhost:").append(getPort()).append(CRLF);
        if (client.getSessionId() != null) {
            request.append("Cookie: JSESSIONID=").append(client.getSessionId()).append(CRLF);
        }
        if (token != null) {
            request.append("X-CSRF-Token: ").append(token).append(CRLF);
        }
        if (body != null) {
            request.append("Content-Type: application/json").append(CRLF);
            request.append("Content-Length: ").append(body.getBytes(StandardCharsets.UTF_8).length).append(CRLF);
        }
        request.append("Connection: Close").append(CRLF);
        request.append(CRLF);
        if (body != null) {
            request.append(body);
        }
        // Note: the request parts must keep their CRLF terminators, so the
        // whole request is sent as a single part.
        client.setRequest(new String[] { request.toString() });
        client.connect();
        client.processRequest(true);
        if (client.getStatusCode() != expectedStatus) {
            System.out.println("DBG unexpected: " + method + " " + path + " status=" + client.getStatusCode() +
                    " body=" + client.getResponseBody().substring(0, Math.min(300, client.getResponseBody().length())));
        }
        Assert.assertEquals(expectedStatus, client.getStatusCode());
    }


    private void requestRaw(SimpleHttpClient client, String method, String path, int expectedStatus) throws Exception {
        StringBuilder request = new StringBuilder();
        request.append(method).append(' ').append(path).append(" HTTP/1.1").append(CRLF);
        request.append("Host: localhost:").append(getPort()).append(CRLF);
        if (client.getSessionId() != null) {
            request.append("Cookie: JSESSIONID=").append(client.getSessionId()).append(CRLF);
        }
        request.append("Connection: Close").append(CRLF);
        request.append(CRLF);
        client.setRequest(new String[] { request.toString() });
        client.connect();
        client.processRequest(true);
        if (client.getStatusCode() != expectedStatus) {
            System.out.println("DBG unexpected: " + method + " " + path + " status=" + client.getStatusCode() +
                    " body=" + client.getResponseBody().substring(0, Math.min(300, client.getResponseBody().length())));
        }
        Assert.assertEquals(expectedStatus, client.getStatusCode());
    }


    private String getCsrfToken(SimpleHttpClient client) {
        for (String header : client.getResponseHeaders()) {
            if (header.toLowerCase().startsWith("x-csrf-token: ")) {
                return header.substring("X-CSRF-Token: ".length());
            }
        }
        return null;
    }


    private void createTestWebapp(File dir) throws IOException {
        deleteRecursive(dir);
        File webInf = new File(dir, "WEB-INF");
        Assert.assertTrue(webInf.mkdirs());

        try (PrintWriter pw = new PrintWriter(new File(webInf, "web.xml"), StandardCharsets.UTF_8)) {
            pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            pw.println("<web-app xmlns=\"https://jakarta.ee/xml/ns/jakartaee\"");
            pw.println("        version=\"6.2\" metadata-complete=\"true\">");
            pw.println("  <display-name>Manager2 Test App</display-name>");
            // The default servlet must be defined in web.xml: the test
            // instance has no global web.xml and context stop() resets all
            // wrappers, re-creating them only from web.xml on start().
            pw.println("  <servlet>");
            pw.println("    <servlet-name>default</servlet-name>");
            pw.println("    <servlet-class>org.apache.catalina.servlets.DefaultServlet</servlet-class>");
            pw.println("  </servlet>");
            pw.println("  <servlet-mapping>");
            pw.println("    <servlet-name>default</servlet-name>");
            pw.println("    <url-pattern>/</url-pattern>");
            pw.println("  </servlet-mapping>");
            // The test instance has no global web.xml, so the JSP servlet
            // has to be declared as well.
            pw.println("  <servlet>");
            pw.println("    <servlet-name>jsp</servlet-name>");
            pw.println("    <servlet-class>org.apache.jasper.servlet.JspServlet</servlet-class>");
            pw.println("    <init-param>");
            pw.println("      <param-name>fork</param-name>");
            pw.println("      <param-value>false</param-value>");
            pw.println("    </init-param>");
            pw.println("  </servlet>");
            pw.println("  <servlet-mapping>");
            pw.println("    <servlet-name>jsp</servlet-name>");
            pw.println("    <url-pattern>*.jsp</url-pattern>");
            pw.println("  </servlet-mapping>");
            pw.println("  <welcome-file-list>");
            pw.println("    <welcome-file>index.html</welcome-file>");
            pw.println("  </welcome-file-list>");
            pw.println("</web-app>");
        }

        try (PrintWriter pw = new PrintWriter(new File(dir, "index.html"), StandardCharsets.UTF_8)) {
            pw.println("<html><body>manager2 test app</body></html>");
        }

        try (PrintWriter pw = new PrintWriter(new File(dir, "session.jsp"), StandardCharsets.UTF_8)) {
            pw.println("<%@ page session=\"true\" %>");
            pw.println("<% request.getSession(true).setAttribute(\"testAttr\", \"testValue\"); %>");
            pw.println("session created");
        }
    }


    private File createTestWar() throws IOException {
        File warFile = new File(TEMP_DIR, "manager2-test.war");
        deleteRecursive(warFile);
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(warFile))) {
            jos.putNextEntry(new JarEntry("index.html"));
            jos.write("<html><body>manager2 war test</body></html>".getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
            jos.putNextEntry(new JarEntry("WEB-INF/web.xml"));
            jos.write(("<web-app xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" version=\"6.2\" " +
                    "metadata-complete=\"true\">" + "<servlet><servlet-name>default</servlet-name>" +
                    "<servlet-class>org.apache.catalina.servlets.DefaultServlet</servlet-class></servlet>" +
                    "<servlet-mapping><servlet-name>default</servlet-name>" +
                    "<url-pattern>/</url-pattern></servlet-mapping>" +
                    "<welcome-file-list><welcome-file>index.html</welcome-file></welcome-file-list>" + "</web-app>")
                    .getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
        return warFile;
    }


    private static void writeLogFile(File file, String content) throws IOException {
        try (PrintWriter pw = new PrintWriter(file, StandardCharsets.UTF_8)) {
            pw.print(content);
        }
    }


    /**
     * Write a {@code tomcat-users.xml} file with the given (already indented) role, group and user entries.
     */
    private static void writeUserDatabaseXml(File file, String entries) throws IOException {
        try (PrintWriter pw = new PrintWriter(file, StandardCharsets.UTF_8)) {
            pw.println("<?xml version='1.0' encoding='utf-8'?>");
            pw.println("<tomcat-users xmlns=\"http://tomcat.apache.org/xml\"");
            pw.println("              xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
            pw.println("              xsi:schemaLocation=\"http://tomcat.apache.org/xml tomcat-users.xsd\"");
            pw.println("              version=\"1.0\">");
            pw.print(entries);
            pw.println("</tomcat-users>");
        }
    }


    private static String readFile(File file) throws IOException {
        return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }


    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursive(child);
            }
        }
        file.delete();
    }


    private static class TestClient extends SimpleHttpClient {

        @Override
        public boolean isResponseBodyOK() {
            return true;
        }
    }
}
