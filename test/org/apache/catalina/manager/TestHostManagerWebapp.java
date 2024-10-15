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
        context.addServletMappingDecoded("/", "hello");

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
                "GET / HTTP/1.1" + CRLF +
                "Host: newhost" + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

        client.setRequest(new String[] {
                "GET /host-manager/html HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("/host-manager/css/manager.css"));

        client.setRequest(new String[] {
                "GET /host-manager/text/list HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("[localhost]:[]"));

        client.setRequest(new String[] {
                "GET /host-manager/text/add?name=newhost&aliases=bar&manager=true HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("[newhost]"));

        client.setRequest(new String[] {
                "GET /host-manager/text/list HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("[newhost]:[bar]"));

        client.setRequest(new String[] {
                "GET /manager/text/list HTTP/1.1" + CRLF +
                "Host: newhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("/manager:running"));

        client.setRequest(new String[] {
                "GET /host-manager/text/stop?name=newhost HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("[newhost]"));

        client.setRequest(new String[] {
                "GET /manager/text/list HTTP/1.1" + CRLF +
                "Host: newhost" + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("Hello"));

        client.setRequest(new String[] {
                "GET /host-manager/text/start?name=newhost HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("[newhost]"));

        client.setRequest(new String[] {
                "GET /manager/text/list HTTP/1.1" + CRLF +
                "Host: bar" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("/manager:running"));

        client.setRequest(new String[] {
                "GET /host-manager/text/persist HTTP/1.1" + CRLF +
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
        Assert.assertTrue(serverXmlDump.contains("<Alias>bar</Alias>"));

        client.setRequest(new String[] {
                "GET /host-manager/text/remove?name=newhost HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("[newhost]"));

        client.setRequest(new String[] {
                "GET /manager/text/list HTTP/1.1" + CRLF +
                "Host: newhost" + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("Hello"));

        client.setRequest(new String[] {
                "GET /host-manager/text/start?name=newhost HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("[newhost]"));

        client.setRequest(new String[] {
                "GET /manager/text/list HTTP/1.1" + CRLF +
                "Host: newhost" + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("Hello"));

        tomcat.stop();
    }

}
