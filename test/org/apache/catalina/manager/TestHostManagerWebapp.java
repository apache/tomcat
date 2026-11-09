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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.Context;
import org.apache.catalina.authenticator.TestBasicAuthParser.BasicAuthHeader;
import org.apache.catalina.realm.MemoryRealm;
import org.apache.catalina.realm.MessageDigestCredentialHandler;
import org.apache.catalina.startup.Catalina;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.storeconfig.StoreConfigLifecycleListener;
import org.apache.catalina.util.IOTools;

public class TestHostManagerWebapp extends TomcatBaseTest {

    private static final String CSP_HEADER = "Content-Security-Policy:";
    // A nonce source in the style-src directive, with the nonce limited to the base64-value syntax of CSP
    private static final Pattern STYLE_SRC_NONCE = Pattern.compile("style-src [^;]*'nonce-([A-Za-z0-9+/_-]+={0,2})'");
    private static final Pattern STYLE_NONCE = Pattern.compile("<style\\b[^>]*\\bnonce=\"([^\"]*)\"");

    @Test
    public void testServlet() throws Exception {
        System.setProperty("catalina.home", getBuildDirectory().getAbsolutePath());
        Tomcat tomcat = getTomcatInstance();
        tomcat.setAddDefaultWebXmlToWebapp(false);
        tomcat.getServer().addLifecycleListener(new StoreConfigLifecycleListener());

        File configFile = new File(getTemporaryDirectory(), "tomcat-users-host-manager.xml");
        try (PrintWriter writer = new PrintWriter(configFile)) {
            writer.write(TestManagerWebapp.CONFIG);
        }
        addDeleteOnTearDown(configFile);

        MemoryRealm memoryRealm = new MemoryRealm();
        memoryRealm.setCredentialHandler(new MessageDigestCredentialHandler());
        memoryRealm.setPathname(configFile.getAbsolutePath());
        tomcat.getEngine().setRealm(memoryRealm);

        File confFolder = new File(getTemporaryDirectory(), "conf");
        confFolder.mkdirs();
        File webappDir = new File(getBuildDirectory(), "webapps");

        // Add host-manager webapp
        File appDir = new File(webappDir, "host-manager");
        tomcat.addWebapp(null, "/host-manager", appDir.getAbsolutePath());

        Context context = getProgrammaticRootContext();
        Tomcat.addServlet(context, "hello", new HelloWorldServlet());
        context.addServletMapping("/", "hello");

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
                "GET / HTTP/1.1" + CRLF +
                    "Host: newhost" + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

        // @formatter:off
        client.setRequest(new String[] {
                "GET /host-manager/html HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

        String html = client.getResponseBody();
        Assert.assertTrue(html.contains("/host-manager/css/manager.css"));
        Assert.assertTrue(html.contains("<!DOCTYPE html>"));
        Assert.assertTrue(html.contains("<html lang="));
        Assert.assertFalse(html.contains("<font"));
        Assert.assertFalse(html.contains("bgcolor="));
        Assert.assertFalse(html.contains("NOWRAP"));
        Assert.assertFalse(html.contains(" border="));
        Assert.assertFalse(html.contains(" align="));
        Assert.assertFalse(html.contains(" nowrap>"));
        Assert.assertFalse(html.contains(" width="));
        Assert.assertFalse(html.contains("style=\""));
        // Table borders and padding are provided by CSS
        Assert.assertTrue(html.contains("<table class=\"data-table\">"));

        // Strict Content-Security-Policy header present
        String csp = getCspHeader(client);
        Assert.assertNotNull("CSP header not present on /host-manager/html", csp);
        for (String directive : new String[] { "default-src 'none'", "style-src 'self'", "img-src 'self'",
                "form-action 'self'", "frame-ancestors 'none'", "base-uri 'none'" }) {
            Assert.assertTrue(csp, csp.contains(directive));
        }

        // The lang attribute follows the language the page is rendered in
        for (String language : new String[] { "fr", "en" }) {
            // @formatter:off
            client.setRequest(new String[] {
                    "GET /host-manager/html HTTP/1.1" + CRLF +
                        "Host: localhost" + CRLF +
                        "Authorization: " + basicHeader + CRLF +
                        "Accept-Language: " + language + CRLF +
                        "Connection: Close" + CRLF +
                        CRLF
                    });
            // @formatter:on
            client.connect();
            client.processRequest(true);
            Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
            Assert.assertTrue(client.getResponseBody().contains("<html lang=\"" + language + "\">"));
        }

        // @formatter:off
        client.setRequest(new String[] {
                "GET /host-manager/text/list HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("[localhost]:[]"));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /host-manager/text/add?name=newhost&aliases=bar&manager=true HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("[newhost]"));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /host-manager/text/list HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("[newhost]:[bar]"));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text/list HTTP/1.1" + CRLF +
                    "Host: newhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("/manager:running"));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /host-manager/text/stop?name=newhost HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("[newhost]"));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text/list HTTP/1.1" + CRLF +
                    "Host: newhost" + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("Hello"));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /host-manager/text/start?name=newhost HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("[newhost]"));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text/list HTTP/1.1" + CRLF +
                    "Host: bar" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("/manager:running"));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /host-manager/text/persist HTTP/1.1" + CRLF +
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
        String serverXmlDump = "";
        try (FileReader reader = new FileReader(serverXml); StringWriter writer = new StringWriter()) {
            IOTools.flow(reader, writer);
            serverXmlDump = writer.toString();
        }
        Assert.assertTrue(serverXmlDump.contains("<Alias>bar</Alias>"));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /host-manager/text/remove?name=newhost HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("[newhost]"));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text/list HTTP/1.1" + CRLF +
                    "Host: newhost" + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("Hello"));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /host-manager/text/start?name=newhost HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("[newhost]"));

        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/text/list HTTP/1.1" + CRLF +
                    "Host: newhost" + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("Hello"));

        tomcat.stop();
    }


    /*
     * Removing a host leaves the configuration directory in place. Re-adding
     * a host with the same name using manager=true must then replace the
     * leftover manager.xml rather than failing to install it.
     */
    @Test
    public void testAddRemoveReAddWithManager() throws Exception {
        System.setProperty("catalina.home", getBuildDirectory().getAbsolutePath());
        Tomcat tomcat = getTomcatInstance();
        tomcat.setAddDefaultWebXmlToWebapp(false);

        File configFile = new File(getTemporaryDirectory(), "tomcat-users-host-manager-readd.xml");
        try (PrintWriter writer = new PrintWriter(configFile)) {
            writer.write(TestManagerWebapp.CONFIG);
        }
        addDeleteOnTearDown(configFile);

        MemoryRealm memoryRealm = new MemoryRealm();
        memoryRealm.setCredentialHandler(new MessageDigestCredentialHandler());
        memoryRealm.setPathname(configFile.getAbsolutePath());
        tomcat.getEngine().setRealm(memoryRealm);

        File confFolder = new File(getTemporaryDirectory(), "conf");
        confFolder.mkdirs();
        File webappDir = new File(getBuildDirectory(), "webapps");

        File appDir = new File(webappDir, "host-manager");
        tomcat.addWebapp(null, "/host-manager", appDir.getAbsolutePath());

        getProgrammaticRootContext();

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
                "GET /host-manager/text/add?name=rehost&manager=true HTTP/1.1" + CRLF +
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
                client.getResponseBody().contains("OK - Host [rehost] added"));
        File managerXml = new File(confFolder, tomcat.getEngine().getName() + "/rehost/manager.xml");
        Assert.assertTrue(managerXml.getAbsolutePath(), managerXml.isFile());

        // @formatter:off
        client.setRequest(new String[] {
                "GET /host-manager/text/remove?name=rehost HTTP/1.1" + CRLF +
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
                client.getResponseBody().contains("OK - Removed host [rehost]"));
        // The configuration directory is intentionally left in place
        Assert.assertTrue(managerXml.getAbsolutePath(), managerXml.isFile());

        // @formatter:off
        client.setRequest(new String[] {
                "GET /host-manager/text/add?name=rehost&manager=true HTTP/1.1" + CRLF +
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
                client.getResponseBody().contains("OK - Host [rehost] added"));

        tomcat.stop();
    }


    /*
     * The Content-Security-Policy allows the inline styles of the error pages and of the default error report by
     * nonce. Responses that do not pass through the HTTP header security filter have no policy and an empty nonce.
     */
    @Test
    public void testErrorPageCspNonce() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.addUser("admin", "sekr3t");
        tomcat.addRole("admin", "admin-gui");

        // Keep the default and JSP servlets so that a missing resource reaches the JSP error page
        File webappDir = new File(getBuildDirectory(), "webapps");
        File appDir = new File(webappDir, "host-manager");
        tomcat.addWebapp(null, "/host-manager", appDir.getAbsolutePath());

        tomcat.start();

        SimpleHttpClient client = new SimpleHttpClient() {
            @Override
            public boolean isResponseBodyOK() {
                return true;
            }
        };
        client.setPort(getPort());
        String basicHeader = (new BasicAuthHeader("Basic", "admin", "sekr3t")).getHeader().toString();

        // The 404 error page uses the nonce from the policy
        // @formatter:off
        client.setRequest(new String[] {
                "GET /host-manager/does-not-exist HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, client.getStatusCode());
        String errorPageNonce = getStyleSrcNonce(client);
        Assert.assertTrue(client.getResponseBody(),
                client.getResponseBody().contains("<style nonce=\"" + errorPageNonce + "\">"));

        // The CSRF filter runs after the HTTP header security filter so its 403 error page uses the nonce from the
        // policy
        // @formatter:off
        client.setRequest(new String[] {
                "GET /host-manager/html/does-not-exist HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_FORBIDDEN, client.getStatusCode());
        String csrfNonce = getStyleSrcNonce(client);
        Assert.assertTrue(client.getResponseBody(),
                client.getResponseBody().contains("<style nonce=\"" + csrfNonce + "\">"));

        // There is no error page for 405 so the default error report uses the nonce from the policy
        // @formatter:off
        client.setRequest(new String[] {
                "DELETE /host-manager/does-not-exist HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_METHOD_NOT_ALLOWED, client.getStatusCode());
        String errorReportNonce = getStyleSrcNonce(client);
        Matcher m = STYLE_NONCE.matcher(client.getResponseBody());
        Assert.assertTrue(client.getResponseBody(), m.find());
        Assert.assertEquals(errorReportNonce, m.group(1));

        // Each response has a new nonce
        Assert.assertNotEquals(errorPageNonce, errorReportNonce);

        // The authenticator rejects the request before the filter runs so there is no policy and the 401 error page
        // has an empty nonce
        // @formatter:off
        client.setRequest(new String[] {
                "GET /host-manager/html HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_UNAUTHORIZED, client.getStatusCode());
        Assert.assertNull("CSP header present on the 401 response", getCspHeader(client));
        Assert.assertTrue(client.getResponseBody(), client.getResponseBody().contains("<style nonce=\"\">"));

        tomcat.stop();
    }


    private static String getCspHeader(SimpleHttpClient client) {
        for (String header : client.getResponseHeaders()) {
            if (header.regionMatches(true, 0, CSP_HEADER, 0, CSP_HEADER.length())) {
                return header.substring(CSP_HEADER.length()).trim();
            }
        }
        return null;
    }


    private static String getStyleSrcNonce(SimpleHttpClient client) {
        String csp = getCspHeader(client);
        Assert.assertNotNull("CSP header not present", csp);
        // Every placeholder has been replaced
        Assert.assertFalse(csp, csp.contains("{nonce}"));
        Matcher m = STYLE_SRC_NONCE.matcher(csp);
        Assert.assertTrue(csp, m.find());
        return m.group(1);
    }
}
