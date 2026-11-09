/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.manager;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.authenticator.TestBasicAuthParser.BasicAuthHeader;
import org.apache.catalina.filters.Constants;
import org.apache.catalina.realm.MemoryRealm;
import org.apache.catalina.realm.MessageDigestCredentialHandler;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.startup.Catalina;
import org.apache.catalina.startup.HostConfig;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.storeconfig.StoreConfigLifecycleListener;
import org.apache.catalina.util.IOTools;
import org.apache.catalina.util.ServerInfo;
import org.apache.catalina.util.URLEncoder;
import org.apache.tomcat.util.buf.ByteChunk;


public class TestManagerWebapp extends TomcatBaseTest {

    public static final String CONFIG = "<?xml version=\"1.0\" ?>" +
            "<tomcat-users xmlns=\"http://tomcat.apache.org/xml\"" +
            " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
            " xsi:schemaLocation=\"http://tomcat.apache.org/xml/tomcat-users.xsd\"" + " version=\"1.0\">" +
            "<role rolename=\"admin\" />" +
            "<user username=\"admin\" password=\"sekr3t\" roles=\"manager-gui,manager-script,manager-jmx,manager-status,admin-gui,admin-script\" />" +
            "</tomcat-users>";

    private static final Pattern NONCE =
            Pattern.compile(Pattern.quote(Constants.CSRF_NONCE_REQUEST_PARAM) + "=([A-F0-9]+)");
    private static final Pattern SESSIONS_FORM =
            Pattern.compile("<form\\b([^>]*\\bid=\"sessionsForm\"[^>]*)>(.*?)</form>", Pattern.DOTALL);
    private static final Pattern FORM_CONTROL = Pattern.compile("<(input|button)\\b([^>]*)>");
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w-]+)=\"([^\"]*)\"");
    private static final Pattern SESSION_ID = Pattern.compile("name=\"sessionIds\" value=\"([^\"]*)\"");
    // Groups: aria-sort value, sort key, label
    private static final Pattern ARIA_SORT = Pattern.compile(
            "<th\\b[^>]*\\baria-sort=\"([^\"]*)\"[^>]*>\\s*<button\\b[^>]*\\bvalue=\"([^\"]*)\"[^>]*>(.*?)</button>");

    /**
     * Integration test for the manager webapp (verify all main Servlets are working).
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void testServlets() throws Exception {
        // Avoid possible locking failure due to deployment with the manager in testDeploy
        ignoreTearDown = true;
        Tomcat tomcat = getTomcatInstance();
        tomcat.setAddDefaultWebXmlToWebapp(false);
        tomcat.addUser("admin", "sekr3t");
        tomcat.addRole("admin", "manager-gui");
        tomcat.addRole("admin", "manager-script");
        tomcat.addRole("admin", "manager-jmx");
        tomcat.addRole("admin", "manager-status");

        File webappDir = new File(getBuildDirectory(), "webapps");

        // Add manager webapp
        File appDir = new File(webappDir, "manager");
        tomcat.addWebapp(null, "/manager", appDir.getAbsolutePath());

        tomcat.start();

        SimpleHttpClient client = new SimpleHttpClient() {
            @Override
            public boolean isResponseBodyOK() {
                return true;
            }
        };
        client.setPort(getPort());
        String basicHeader = (new BasicAuthHeader("Basic", "admin", "sekr3t")).getHeader().toString();

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/html HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_UNAUTHORIZED, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/html HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("/manager/css/manager.css"));

        // Strict Content-Security-Policy header present
        boolean cspFound = false;
        for (String header : client.getResponseHeaders()) {
            if (header.equals("Content-Security-Policy: " +
                    "default-src 'none'; style-src 'self'; img-src 'self'; " +
                    "form-action 'self'; frame-ancestors 'none'; base-uri 'none'")) {
                cspFound = true;
            }
        }
        Assert.assertTrue("strict CSP header not present on /manager/html", cspFound);

        // Modernized, valid HTML5 markup (no quirks mode, no obsolete tags)
        String html = client.getResponseBody();
        Assert.assertTrue("missing doctype", html.contains("<!DOCTYPE html>"));
        Assert.assertTrue("missing lang attribute", html.contains("<html lang="));
        Assert.assertFalse("obsolete <font>", html.contains("<font"));
        Assert.assertFalse("obsolete bgcolor", html.contains("bgcolor="));
        Assert.assertFalse("obsolete <center>", html.contains("<center"));
        Assert.assertFalse("obsolete border", html.contains(" border="));
        Assert.assertFalse("obsolete align", html.contains(" align="));
        Assert.assertFalse("obsolete nowrap", html.contains(" nowrap>"));
        Assert.assertFalse("obsolete width", html.contains(" width="));
        Assert.assertFalse("inline style", html.contains("style=\""));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/status HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("MiB"));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/jmxproxy HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("Tomcat:type=ThreadPool,name="));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains(" - "));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text/sessions?path=/manager HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("[1]"));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text/resources HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains(" - "));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text/serverinfo HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("[" + ServerInfo.getServerInfo() + "]"));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text/vminfo HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("vmName: "));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text/threaddump HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("http-"));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text/list HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("/manager:running"));

        tomcat.stop();
    }

    @Test
    public void testDeploy() throws Exception {
        // Avoid possible locking failure due to deployment with the manager
        ignoreTearDown = true;
        Tomcat tomcat = getTomcatInstance();
        tomcat.setAddDefaultWebXmlToWebapp(false);
        tomcat.getServer().addLifecycleListener(new StoreConfigLifecycleListener());

        File configFile = new File(getTemporaryDirectory(), "tomcat-users-manager-deploy.xml");
        try (PrintWriter writer = new PrintWriter(configFile)) {
            writer.write(CONFIG);
        }
        addDeleteOnTearDown(configFile);

        MemoryRealm memoryRealm = new MemoryRealm();
        memoryRealm.setCredentialHandler(new MessageDigestCredentialHandler());
        memoryRealm.setPathname(configFile.getAbsolutePath());
        tomcat.getEngine().setRealm(memoryRealm);

        File webappDir = new File(getBuildDirectory(), "webapps");

        // Add manager webapp
        File appDir = new File(webappDir, "manager");
        Context ctx = tomcat.addWebapp(null, "/manager", appDir.getAbsolutePath());

        // Add host config otherwise there's no JMX deployer bean
        HostConfig hostConfig = new HostConfig();
        ctx.getParent().addLifecycleListener(hostConfig);

        tomcat.start();

        SimpleHttpClient client = new SimpleHttpClient() {
            // 10s default too low for some CI systems
            @Override
            public void connect() throws IOException {
                connect(30000, 30000);
            }

            @Override
            public boolean isResponseBodyOK() {
                return true;
            }
        };
        client.setPort(getPort());
        String basicHeader = (new BasicAuthHeader("Basic", "admin", "sekr3t")).getHeader().toString();

        appDir = new File(webappDir, "examples");

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text/deploy?war=" + URLEncoder.QUERY.encode(appDir.getAbsolutePath(), StandardCharsets.UTF_8) + " HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("OK - "));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /examples/servlets/servlet/RequestInfoExample HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("/examples/servlets/servlet/RequestInfoExample"));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text/stop?path=/examples HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "GET /examples/servlets/servlet/RequestInfoExample HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text/start?path=/examples HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "GET /examples/servlets/servlet/RequestInfoExample HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("/examples/servlets/servlet/RequestInfoExample"));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text/save HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text/save?path=/examples HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

        File serverXml = new File(tomcat.getServer().getCatalinaBase(), Catalina.SERVER_XML);
        Assert.assertTrue(serverXml.canRead());
        addDeleteOnTearDown(serverXml);
        String serverXmlDump;
        try (FileReader reader = new FileReader(serverXml); StringWriter writer = new StringWriter()) {
            IOTools.flow(reader, writer);
            serverXmlDump = writer.toString();
        }
        Assert.assertTrue(serverXmlDump.contains("StoreConfigLifecycleListener"));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text/reload?path=/examples HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text/list HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody(), client.getResponseBody().contains("/examples:running"));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /examples/servlets/servlet/RequestInfoExample HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody(), client.getResponseBody().contains("/examples/servlets/servlet/RequestInfoExample"));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text/list HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody(), client.getResponseBody().contains("/examples:running"));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text/undeploy?path=/examples HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text/list HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertFalse(client.getResponseBody(), client.getResponseBody().contains("/examples:running"));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text/findleaks?statusLine=true HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

        tomcat.stop();
    }


    /*
     * Test case for <a href="https://bz.apache.org/bugzilla/show_bug.cgi?id=57700">Bug 57700</a>.
     */
    @Test
    public void testBug57700() throws Exception {
        ignoreTearDown = true;
        Tomcat tomcat = getTomcatInstance();
        tomcat.setAddDefaultWebXmlToWebapp(false);
        File configFile = new File(getTemporaryDirectory(), "tomcat-users-manager-lifecycle.xml");
        try (PrintWriter writer = new PrintWriter(configFile)) {
            writer.write(CONFIG);
        }
        addDeleteOnTearDown(configFile);

        MemoryRealm memoryRealm = new MemoryRealm();
        memoryRealm.setCredentialHandler(new MessageDigestCredentialHandler());
        memoryRealm.setPathname(configFile.getAbsolutePath());
        tomcat.getEngine().setRealm(memoryRealm);

        File webappDir = new File(getBuildDirectory(), "webapps");

        File appDir = new File(webappDir, "manager");
        Context ctx = tomcat.addWebapp(null, "/manager", appDir.getAbsolutePath());

        HostConfig hostConfig = new HostConfig();
        ctx.getParent().addLifecycleListener(hostConfig);

        File appRoot = new File(webappDir, "bug57700");
        Assert.assertTrue(appRoot.mkdirs() && appRoot.isDirectory());
        addDeleteOnTearDown(appRoot);

        try (@SuppressWarnings("unused") TomcatBaseTest.ContainerInjector ignored =
                 TomcatBaseTest.ContainerInjector.inject(ctx.getParent(),
                     c -> c.getPath().equals("/bug57700"),
                     c -> {
                         c.addLifecycleListener(new FailOnceListener());
                         Tomcat.initWebappDefaults(c);
                     })) {

            tomcat.start();
            SimpleHttpClient client = new SimpleHttpClient() {
                @Override
                public void connect() throws IOException {
                    connect(30000, 30000);
                }

                @Override
                public boolean isResponseBodyOK() {
                    return true;
                }
            };

            client.setPort(getPort());
            String basicHeader = (new BasicAuthHeader("Basic", "admin", "sekr3t")).getHeader().toString();

            client.setRequest(new String[]{
                    "GET /manager/text/deploy?war=" + URLEncoder.QUERY.encode(appRoot.getAbsolutePath(), StandardCharsets.UTF_8) + " HTTP/1.1" + CRLF +
                        "Host: localhost" + CRLF +
                        "Authorization: " + basicHeader + CRLF +
                        "Connection: Close" + CRLF + CRLF
            });
            client.connect();
            client.processRequest(true);
            Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

            client.setRequest(new String[]{
                    "GET /bug57700 HTTP/1.1" + CRLF +
                        "Host: localhost" + CRLF +
                        "Connection: Close" + CRLF + CRLF
            });
            client.connect();
            client.processRequest(true);
            Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, client.getStatusCode());

            client.setRequest(new String[]{
                    "GET /manager/text/start?path=/bug57700 HTTP/1.1" + CRLF +
                        "Host: localhost" + CRLF +
                        "Authorization: " + basicHeader + CRLF +
                        "Connection: Close" + CRLF + CRLF
            });
            client.connect();
            client.processRequest(true);
            Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

            client.setRequest(new String[]{
                    "GET /bug57700 HTTP/1.1" + CRLF +
                        "Host: localhost" + CRLF +
                        "Connection: Close" + CRLF + CRLF
            });
            client.connect();
            client.processRequest(true);
            Assert.assertEquals(HttpServletResponse.SC_FOUND, client.getStatusCode());

            client.setRequest(new String[] {
                    "GET /manager/text/undeploy?path=/bug57700 HTTP/1.1" + CRLF +
                        "Host: localhost" + CRLF +
                        "Authorization: " + basicHeader + CRLF +
                        "Connection: Close" + CRLF +
                        CRLF
            });
            client.connect();
            client.processRequest(true);
            Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

            tomcat.stop();
        }
    }

    /*
     * A session with an unlimited timeout (maxInactiveInterval == -1) must
     * be counted in the "Unlimited time" bucket of the text interface
     * session histogram rather than in an idle-time bucket.
     */
    @Test
    public void testSessionsUnlimitedTimeout() throws Exception {
        ignoreTearDown = true;
        Tomcat tomcat = getTomcatInstance();
        tomcat.setAddDefaultWebXmlToWebapp(false);
        tomcat.addUser("admin", "sekr3t");
        tomcat.addRole("admin", "manager-script");

        File webappDir = new File(getBuildDirectory(), "webapps");
        File appDir = new File(webappDir, "manager");
        tomcat.addWebapp(null, "/manager", appDir.getAbsolutePath());

        Context ctx = tomcat.addContext("/testsession", null);
        // A session timeout of zero means sessions never time out. It is
        // stored as -1 minutes which becomes -60 seconds on the session.
        Context zeroCtx = tomcat.addContext("/testsession0", null);
        zeroCtx.setSessionTimeout(0);

        tomcat.start();

        ManagerBase manager = (ManagerBase) ctx.getManager();
        Session unlimited = manager.createSession(null);
        unlimited.setMaxInactiveInterval(-1);
        manager.add(unlimited);

        // An interval of zero also means the session never times out
        Session zeroInterval = manager.createSession(null);
        zeroInterval.setMaxInactiveInterval(0);
        manager.add(zeroInterval);

        Session limited = manager.createSession(null);
        manager.add(limited);

        ManagerBase zeroManager = (ManagerBase) zeroCtx.getManager();
        Session zeroTimeout = zeroManager.createSession(null);
        Assert.assertEquals(-60, zeroTimeout.getMaxInactiveInterval());
        zeroManager.add(zeroTimeout);

        SimpleHttpClient client = new SimpleHttpClient() {
            @Override
            public boolean isResponseBodyOK() {
                return true;
            }
        };
        client.setPort(getPort());
        String basicHeader =
                (new BasicAuthHeader("Basic", "admin", "sekr3t")).getHeader().toString();

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text/sessions?path=/testsession HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody(),
                client.getResponseBody().contains("Unlimited time: [2] sessions"));
        // The unlimited session must not also appear in an idle bucket
        Assert.assertTrue(client.getResponseBody(),
                client.getResponseBody().contains("Inactive for [<1] minutes: [1] sessions"));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text/sessions?path=/testsession0 HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody(),
                client.getResponseBody().contains("Unlimited time: [1] sessions"));

        tomcat.stop();
    }


    /*
     * Verify that manager JSPs compile and render correctly.
     */
    @Test
    public void testJsps() throws Exception {
        ignoreTearDown = true;
        Tomcat tomcat = getTomcatInstance();
        tomcat.addUser("admin", "sekr3t");
        tomcat.addRole("admin", "manager-gui");

        File webappDir = new File(getBuildDirectory(), "webapps");
        File appDir = new File(webappDir, "manager");
        tomcat.addWebapp(null, "/manager", appDir.getAbsolutePath());

        Context ctx = tomcat.addContext("/testapp", null);
        Tomcat.addServlet(ctx, "default", new org.apache.catalina.servlets.DefaultServlet());
        ctx.addServletMapping("/", "default");

        tomcat.start();

        SimpleHttpClient client = new SimpleHttpClient() {
            @Override
            public boolean isResponseBodyOK() {
                return true;
            }
        };
        client.setPort(getPort());
        String basicHeader =
                (new BasicAuthHeader("Basic", "admin", "sekr3t")).getHeader().toString();

        // Hit the HTML manager entry point to get a session and CSRF nonce
        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/html HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

        String body = client.getResponseBody();
        Matcher m = NONCE.matcher(body);
        Assert.assertTrue("CSRF nonce not found in manager HTML response", m.find());
        String nonce = m.group(1);

        String sessionCookie = null;
        for (String header : client.getResponseHeaders()) {
            if (header.startsWith("Set-Cookie:")) {
                String cookieValue = header.substring("Set-Cookie:".length()).trim();
                sessionCookie = cookieValue.split(";")[0];
                break;
            }
        }
        Assert.assertNotNull("Session cookie not found", sessionCookie);

        // Access sessions list page with the CSRF nonce
        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/html/sessions?path=/testapp&" +
                    Constants.CSRF_NONCE_REQUEST_PARAM + "=" + nonce +
                    " HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Cookie: " + sessionCookie + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("Sessions Administration"));
        Assert.assertTrue(client.getResponseBody().contains("active Sessions"));

        String sessionsPage = client.getResponseBody();
        Assert.assertFalse("inline JS present", sessionsPage.contains("onclick="));
        Assert.assertTrue("sort control not a button",
                sessionsPage.contains("<button type=\"submit\" class=\"sort\" name=\"sort\""));
        Assert.assertTrue("missing column scope", sessionsPage.contains("scope=\"col\""));
        Assert.assertFalse("obsolete border", sessionsPage.contains(" border="));
        Assert.assertFalse("obsolete align", sessionsPage.contains(" align="));
        Assert.assertFalse("obsolete nowrap", sessionsPage.contains(" nowrap>"));
        Assert.assertFalse("obsolete width", sessionsPage.contains(" width="));
        Assert.assertFalse("inline style", sessionsPage.contains("style=\""));
        Assert.assertTrue("missing stylesheet link", sessionsPage.contains("/manager/css/manager.css"));
        Assert.assertTrue("missing doctype", sessionsPage.contains("<!DOCTYPE html>"));

        tomcat.stop();
    }


    /*
     * Submit the sessions list form the way a browser does (see
     * ManagerBrowser.submitSessionsForm()) and check the result of sorting,
     * refreshing and invalidating sessions.
     */
    @Test
    public void testSessionsListForm() throws Exception {
        ignoreTearDown = true;
        Tomcat tomcat = getTomcatInstance();
        tomcat.addUser("admin", "sekr3t");
        tomcat.addRole("admin", "manager-gui");

        File appDir = new File(getBuildDirectory(), "webapps/manager");
        tomcat.addWebapp(null, "/manager", appDir.getAbsolutePath());
        Context ctx = tomcat.addContext("/testapp", null);

        tomcat.start();

        // More than 10 sessions so the column headers are repeated in <tfoot>
        Manager manager = ctx.getManager();
        long lastCreationTime = 0;
        for (int i = 0; i < 12; i++) {
            // Distinct creation times so there is only one creation time order
            while (System.currentTimeMillis() <= lastCreationTime) {
                Thread.sleep(1);
            }
            lastCreationTime = manager.createSession(null).getCreationTime();
        }

        ManagerBrowser browser = new ManagerBrowser(getPort());
        Matcher m = NONCE.matcher(browser.get("/manager/html"));
        Assert.assertTrue("CSRF nonce not found in manager HTML response", m.find());
        String page = browser.get("/manager/html/sessions?path=/testapp&" +
                Constants.CSRF_NONCE_REQUEST_PARAM + "=" + m.group(1));
        Assert.assertEquals(12, sessionIds(page).size());

        // Sort by creation time, click again to reverse the order
        page = browser.submitSessionsForm(page, null, "sort", "CreationTime");
        Assert.assertArrayEquals(new String[] { "ascending", "CreationTime", "Creation Time" }, sortedColumn(page));
        assertCreationTimeOrder(page, manager, true);
        page = browser.submitSessionsForm(page, null, "sort", "CreationTime");
        Assert.assertArrayEquals(new String[] { "descending", "CreationTime", "Creation Time" }, sortedColumn(page));
        assertCreationTimeOrder(page, manager, false);

        // Refresh keeps the sort column
        page = browser.submitSessionsForm(page, null, "refresh", null);
        String[] sortedColumn = sortedColumn(page);
        Assert.assertEquals("CreationTime", sortedColumn[1]);
        assertCreationTimeOrder(page, manager, "ascending".equals(sortedColumn[0]));

        // Invalidate the ticked session, and only that one, keeping the sort column
        List<String> sessionIds = sessionIds(page);
        String invalidatedId = sessionIds.get(0);
        page = browser.submitSessionsForm(page, invalidatedId, "action", "invalidateSessions");
        Assert.assertTrue(page, page.contains("1 sessions invalidated"));
        Assert.assertNull(manager.findSession(invalidatedId));
        for (String sessionId : sessionIds.subList(1, sessionIds.size())) {
            Assert.assertNotNull(manager.findSession(sessionId));
        }
        Assert.assertEquals(11, sessionIds(page).size());
        sortedColumn = sortedColumn(page);
        Assert.assertEquals("CreationTime", sortedColumn[1]);
        assertCreationTimeOrder(page, manager, "ascending".equals(sortedColumn[0]));

        // Session Id and Type both sort by id. Only Session Id has aria-sort.
        page = browser.submitSessionsForm(page, null, "sort", "id");
        sortedColumn = sortedColumn(page);
        Assert.assertEquals("id", sortedColumn[1]);
        Assert.assertEquals("Session Id", sortedColumn[2]);

        // Valid HTML5: <tfoot> follows <tbody> and no invalid pragma directives
        int tbodyEnd = page.indexOf("</tbody>");
        Assert.assertTrue("missing </tbody>", tbodyEnd > 0);
        Assert.assertTrue("missing <tfoot> or <tfoot> before </tbody>", page.indexOf("<tfoot") > tbodyEnd);
        String lowerCasePage = page.toLowerCase(Locale.ENGLISH);
        for (String pragma : new String[] { "pragma", "cache-control", "expires", "content-language" }) {
            Assert.assertFalse("http-equiv=\"" + pragma + "\" present",
                    lowerCasePage.contains("http-equiv=\"" + pragma + "\""));
        }

        tomcat.stop();
    }


    /*
     * The lang attribute of the HTML Manager page follows the request locale
     * if the page is available in that language.
     */
    @Test
    public void testHtmlLang() throws Exception {
        ignoreTearDown = true;
        Tomcat tomcat = getTomcatInstance();
        tomcat.addUser("admin", "sekr3t");
        tomcat.addRole("admin", "manager-gui");

        File appDir = new File(getBuildDirectory(), "webapps/manager");
        tomcat.addWebapp(null, "/manager", appDir.getAbsolutePath());

        tomcat.start();

        String basicHeader = (new BasicAuthHeader("Basic", "admin", "sekr3t")).getHeader().toString();
        for (String language : new String[] { "fr", "en" }) {
            Map<String,List<String>> reqHead = new HashMap<>();
            reqHead.put("Authorization", List.of(basicHeader));
            reqHead.put("Accept-Language", List.of(language));
            ByteChunk res = new ByteChunk();
            int rc = getUrl("http://localhost:" + getPort() + "/manager/html", res, reqHead, null);
            Assert.assertEquals(HttpServletResponse.SC_OK, rc);
            Assert.assertTrue("Accept-Language: " + language,
                    res.toString().contains("<html lang=\"" + language + "\">"));
        }

        tomcat.stop();
    }


    /*
     * Returns the aria-sort value, the sort key and the label of the sorted
     * column, checking that exactly one column header, in <thead>, has
     * aria-sort.
     */
    private static String[] sortedColumn(String page) {
        Assert.assertEquals("number of aria-sort attributes", 1, page.split("aria-sort=", -1).length - 1);
        Matcher m = ARIA_SORT.matcher(page);
        Assert.assertTrue("no column header with aria-sort", m.find());
        Assert.assertTrue("aria-sort outside <thead>", m.start() < page.indexOf("</thead>"));
        return new String[] { m.group(1), m.group(2), m.group(3) };
    }


    private static void assertCreationTimeOrder(String page, Manager manager, boolean ascending)
            throws IOException {
        long previous = ascending ? Long.MIN_VALUE : Long.MAX_VALUE;
        for (String sessionId : sessionIds(page)) {
            Session session = manager.findSession(sessionId);
            Assert.assertNotNull(sessionId, session);
            long creationTime = session.getCreationTime();
            Assert.assertTrue("sessions not in " + (ascending ? "ascending" : "descending") + " creation time order",
                    ascending ? creationTime > previous : creationTime < previous);
            previous = creationTime;
        }
    }


    private static List<String> sessionIds(String page) {
        List<String> sessionIds = new ArrayList<>();
        Matcher m = SESSION_ID.matcher(page);
        while (m.find()) {
            sessionIds.add(unescape(m.group(1)));
        }
        return sessionIds;
    }


    private static Map<String,String> attributes(String tag) {
        Map<String,String> attributes = new HashMap<>();
        Matcher m = ATTRIBUTE.matcher(tag);
        while (m.find()) {
            attributes.put(m.group(1), unescape(m.group(2)));
        }
        return attributes;
    }


    private static String unescape(String value) {
        return value.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&amp;", "&");
    }


    /*
     * Sends requests to the HTML Manager like a browser using BASIC
     * authentication. The session ID changes on the second authenticated
     * request (changeSessionIdOnAuthentication) and the CSRF nonces are stored
     * in the session so the latest session cookie must always be used.
     */
    private static class ManagerBrowser {

        private final String baseUrl;
        private final String authorization;
        private String sessionCookie;

        ManagerBrowser(int port) {
            baseUrl = "http://localhost:" + port;
            authorization = (new BasicAuthHeader("Basic", "admin", "sekr3t")).getHeader().toString();
        }

        String get(String path) throws IOException {
            ByteChunk res = new ByteChunk();
            Map<String,List<String>> resHead = new HashMap<>();
            int rc = getUrl(baseUrl + path, res, requestHeaders(), resHead);
            return response(rc, res, resHead);
        }

        String post(String path, String body) throws IOException {
            Map<String,List<String>> reqHead = requestHeaders();
            reqHead.put("Content-Type", List.of("application/x-www-form-urlencoded"));
            ByteChunk res = new ByteChunk();
            Map<String,List<String>> resHead = new HashMap<>();
            int rc = postUrl(body.getBytes(StandardCharsets.UTF_8), baseUrl + path, res, reqHead, resHead);
            return response(rc, res, resHead);
        }

        /*
         * Submits the sessions form of the given page as a browser would: the
         * form controls in tree order, hidden inputs always, a checkbox only
         * if it is the ticked one and, of the submit buttons, only the clicked
         * one, i.e. the first with the given name and value (if not null).
         */
        String submitSessionsForm(String page, String tickedSessionId, String buttonName, String buttonValue)
                throws IOException {
            Matcher form = SESSIONS_FORM.matcher(page);
            Assert.assertTrue("sessions form not found", form.find());
            StringBuilder body = new StringBuilder();
            boolean clicked = false;
            Matcher control = FORM_CONTROL.matcher(form.group(2));
            while (control.find()) {
                Map<String,String> attributes = attributes(control.group(2));
                String name = attributes.get("name");
                String value = attributes.getOrDefault("value", "");
                String type = attributes.getOrDefault("type", "input".equals(control.group(1)) ? "text" : "submit");
                boolean include = false;
                if ("hidden".equals(type) || "text".equals(type)) {
                    include = true;
                } else if ("checkbox".equals(type)) {
                    include = value.equals(tickedSessionId);
                } else if ("submit".equals(type) && !clicked) {
                    clicked = buttonName.equals(name) && (buttonValue == null || buttonValue.equals(value));
                    include = clicked;
                }
                if (include && name != null) {
                    if (body.length() > 0) {
                        body.append('&');
                    }
                    body.append(java.net.URLEncoder.encode(name, StandardCharsets.UTF_8)).append('=')
                            .append(java.net.URLEncoder.encode(value, StandardCharsets.UTF_8));
                }
            }
            Assert.assertTrue("button [" + buttonName + "] not found", clicked);
            return post(attributes(form.group(1)).get("action"), body.toString());
        }

        private Map<String,List<String>> requestHeaders() {
            Map<String,List<String>> reqHead = new HashMap<>();
            reqHead.put("Authorization", List.of(authorization));
            if (sessionCookie != null) {
                reqHead.put("Cookie", List.of(sessionCookie));
            }
            return reqHead;
        }

        private String response(int rc, ByteChunk res, Map<String,List<String>> resHead) {
            Assert.assertEquals(res.toString(), HttpServletResponse.SC_OK, rc);
            List<String> setCookies = resHead.get("Set-Cookie");
            if (setCookies != null) {
                for (String setCookie : setCookies) {
                    if (setCookie.startsWith("JSESSIONID=")) {
                        sessionCookie = setCookie.split(";")[0];
                    }
                }
            }
            return res.toString();
        }
    }

    private static class FailOnceListener implements LifecycleListener {
        private volatile boolean firstRun = true;
        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            if (event.getLifecycle() instanceof Context) {
                if (Lifecycle.CONFIGURE_START_EVENT.equals(event.getType()) && firstRun) {
                    firstRun = false;
                    throw new RuntimeException("Configuration failure in first run only");
                }
            }
        }
    }
}
