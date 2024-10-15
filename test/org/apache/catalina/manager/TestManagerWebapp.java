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
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.Context;
import org.apache.catalina.authenticator.TestBasicAuthParser.BasicAuthHeader;
import org.apache.catalina.realm.MemoryRealm;
import org.apache.catalina.realm.MessageDigestCredentialHandler;
import org.apache.catalina.startup.Catalina;
import org.apache.catalina.startup.HostConfig;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.storeconfig.StoreConfigLifecycleListener;
import org.apache.catalina.util.IOTools;
import org.apache.catalina.util.URLEncoder;

public class TestManagerWebapp extends TomcatBaseTest {

    public static final String CONFIG = "<?xml version=\"1.0\" ?>"
            + "<tomcat-users xmlns=\"http://tomcat.apache.org/xml\""
            + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
            + " xsi:schemaLocation=\"http://tomcat.apache.org/xml/tomcat-users.xsd\""
            + " version=\"1.0\">"
            + "<role rolename=\"admin\" />"
            + "<user username=\"admin\" password=\"sekr3t\" roles=\"manager-gui,manager-script,manager-jmx,manager-status,admin-gui,admin-script\" />"
            + "</tomcat-users>";

    /**
     * Integration test for the manager webapp (verify all main Servlets are working).
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

        client.setRequest(new String[] {
                "GET /manager/html HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_UNAUTHORIZED, client.getStatusCode());

        client.setRequest(new String[] {
                "GET /manager/html HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("/manager/css/manager.css"));

        client.setRequest(new String[] {
                "GET /manager/status HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("MiB"));

        client.setRequest(new String[] {
                "GET /manager/jmxproxy HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("Tomcat:type=ThreadPool,name="));

        client.setRequest(new String[] {
                "GET /manager/text HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains(" - "));

        client.setRequest(new String[] {
                "GET /manager/text/sessions?path=/manager HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("[1]"));

        client.setRequest(new String[] {
                "GET /manager/text/resources HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains(" - "));

        client.setRequest(new String[] {
                "GET /manager/text/serverinfo HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("[Apache Tomcat"));

        client.setRequest(new String[] {
                "GET /manager/text/vminfo HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("vmName: "));

        client.setRequest(new String[] {
                "GET /manager/text/threaddump HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("http-"));

        client.setRequest(new String[] {
                "GET /manager/text/list HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
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
            public void connect() throws UnknownHostException, IOException {
                connect(30000,30000);
            }

            @Override
            public boolean isResponseBodyOK() {
                return true;
            }
        };
        client.setPort(getPort());
        String basicHeader = (new BasicAuthHeader("Basic", "admin", "sekr3t")).getHeader().toString();

        appDir = new File(webappDir, "examples");

        client.setRequest(new String[] {
                "GET /manager/text/deploy?war=" + URLEncoder.QUERY.encode(appDir.getAbsolutePath(), StandardCharsets.UTF_8) + " HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("OK - "));

        client.setRequest(new String[] {
                "GET /examples/servlets/servlet/RequestInfoExample HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("/examples/servlets/servlet/RequestInfoExample"));

        client.setRequest(new String[] {
                "GET /manager/text/stop?path=/examples HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

        client.setRequest(new String[] {
                "GET /examples/servlets/servlet/RequestInfoExample HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, client.getStatusCode());

        client.setRequest(new String[] {
                "GET /manager/text/start?path=/examples HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

        client.setRequest(new String[] {
                "GET /examples/servlets/servlet/RequestInfoExample HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("/examples/servlets/servlet/RequestInfoExample"));

        client.setRequest(new String[] {
                "GET /manager/text/save HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

        client.setRequest(new String[] {
                "GET /manager/text/save?path=/examples HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

        File serverXml = new File(tomcat.getServer().getCatalinaBase(), Catalina.SERVER_XML);
        Assert.assertTrue(serverXml.canRead());
        addDeleteOnTearDown(serverXml);
        String serverXmlDump = "";
        try (FileReader reader = new FileReader(serverXml);
                StringWriter writer = new StringWriter()) {
            IOTools.flow(reader, writer);
            serverXmlDump = writer.toString();
        }
        Assert.assertTrue(serverXmlDump.contains("StoreConfigLifecycleListener"));

        client.setRequest(new String[] {
                "GET /manager/text/reload?path=/examples HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

        client.setRequest(new String[] {
                "GET /manager/text/list HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("/examples:running"));

        client.setRequest(new String[] {
                "GET /examples/servlets/servlet/RequestInfoExample HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("/examples/servlets/servlet/RequestInfoExample"));

        client.setRequest(new String[] {
                "GET /manager/text/list HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("/examples:running"));

        client.setRequest(new String[] {
                "GET /manager/text/undeploy?path=/examples HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

        client.setRequest(new String[] {
                "GET /manager/text/list HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertFalse(client.getResponseBody().contains("/examples:running"));

        client.setRequest(new String[] {
                "GET /manager/text/findleaks?statusLine=true HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

        tomcat.stop();
    }

}
