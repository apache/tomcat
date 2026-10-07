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

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.Context;
import org.apache.catalina.Server;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.HostConfig;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.users.MemoryUserDatabase;
import org.apache.tomcat.util.descriptor.web.ContextResource;

/**
 * Shared base for the manager2 webapp integration tests. The tests deploy the {@code manager2.war} built by this
 * module (via the {@code deploy} target) into a throw-away Tomcat instance and drive it over HTTP with
 * {@link SimpleHttpClient}, exercising FORM login, CSRF protection, role based access control and the JSON API.
 *
 * <p>
 * The test methods are split over the concrete subclasses of this class so that they can run in parallel: the Ant
 * JUnit task parallelizes at the granularity of a test class (every method of one class runs sequentially in a single
 * thread), so one large class would serialize the whole webapp suite. Subclasses therefore must not start with
 * {@code Test}: the batch test of the module build only picks up {@code Test*.java}.
 *
 * <p>
 * All temporary state is kept under {@link #getTemporaryDirectory()} (unique per test class) so that concurrent test
 * classes never touch the same path. JVM-global state (the {@code manager2.store.base} system property, the JULI
 * loggers) is only mutated by the log configuration subclass, whose methods run sequentially.
 */
public abstract class Manager2WebappTestBase extends TomcatBaseTest {

    protected static final String MANAGER2 = "/manager2";
    protected static final String TESTAPP = "/testapp";


    // ------------------------------------------------------------------ setup


    protected void setup(boolean withReadOnlyUser) throws Exception {
        setup(withReadOnlyUser, false);
    }


    protected void setup(boolean withReadOnlyUser, boolean withNaming) throws Exception {
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
        // webapps/ directory, so that undeploy is allowed. The app base is
        // kept under the per-test-class temporary directory so that test
        // classes running in parallel never share (and delete) the same path.
        File appBase = new File(getTemporaryDirectory(), "manager2-test-appbase");
        deleteRecursive(appBase);
        Assert.assertTrue(appBase.mkdirs());
        addDeleteOnTearDown(appBase);
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
     *
     * @param name      Name of the JNDI resource
     * @param xmlFile   The tomcat-users.xml backing the database
     * @param readonly  Whether the database must reject mutations
     *
     * @return The looked-up user database
     *
     * @throws Exception If the resource cannot be registered or looked up
     */
    protected MemoryUserDatabase addUserDatabase(String name, File xmlFile, boolean readonly) throws Exception {
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


    // ------------------------------------------------------------- HTTP helper


    /**
     * Establish a login session for the given user via the FORM login.
     *
     * @param client    The client to log in
     * @param user      The user name (password is always "secret")
     *
     * @throws Exception If the login cannot be completed
     */
    protected void login(SimpleHttpClient client, String user) throws Exception {
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
     *
     * @param client    The client to log in
     * @param user      The user name (password is always "secret")
     *
     * @return The CSRF token of the new session
     *
     * @throws Exception If the login cannot be completed
     */
    protected String loginAndGetToken(SimpleHttpClient client, String user) throws Exception {
        login(client, user);
        request(client, "GET", MANAGER2 + "/api/info", null, null, 200);
        String token = getCsrfToken(client);
        Assert.assertNotNull("Expected an X-CSRF-Token header", token);
        return token;
    }


    protected void request(SimpleHttpClient client, String method, String path, String token, String body,
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
        Assert.assertEquals(expectedStatus, client.getStatusCode());
    }


    protected void requestRaw(SimpleHttpClient client, String method, String path, int expectedStatus)
            throws Exception {
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
        Assert.assertEquals(expectedStatus, client.getStatusCode());
    }


    protected String getCsrfToken(SimpleHttpClient client) {
        for (String header : client.getResponseHeaders()) {
            if (header.toLowerCase().startsWith("x-csrf-token: ")) {
                return header.substring("X-CSRF-Token: ".length());
            }
        }
        return null;
    }


    // ------------------------------------------------------------- test app(s)


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


    protected File createTestWar() throws IOException {
        // Under the per-test-class temporary directory: a fixed name in the
        // shared system temporary directory would be shared (and deleted) by
        // test classes running in parallel.
        File warFile = new File(getTemporaryDirectory(), "manager2-test.war");
        deleteRecursive(warFile);
        addDeleteOnTearDown(warFile);
        try (FileOutputStream fos = new FileOutputStream(warFile);
                JarOutputStream jos = new JarOutputStream(fos)) {
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


    // ------------------------------------------------------------------ files


    protected static void writeLogFile(File file, String content) throws IOException {
        try (PrintWriter pw = new PrintWriter(file, StandardCharsets.UTF_8)) {
            pw.print(content);
        }
    }


    /**
     * Write a {@code tomcat-users.xml} file with the given (already indented) role, group and user entries.
     *
     * @param file      The file to write
     * @param entries   The role, group and user entries
     *
     * @throws IOException If the file cannot be written
     */
    protected static void writeUserDatabaseXml(File file, String entries) throws IOException {
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


    protected static String readFile(File file) throws IOException {
        return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }


    protected static void deleteRecursive(File file) {
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


    protected static class TestClient extends SimpleHttpClient {

        @Override
        public boolean isResponseBodyOK() {
            return true;
        }
    }
}
