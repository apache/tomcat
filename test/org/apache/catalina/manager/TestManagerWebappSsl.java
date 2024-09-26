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
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.Context;
import org.apache.catalina.authenticator.TestBasicAuthParser.BasicAuthHeader;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.TesterSupport;
import org.apache.tomcat.websocket.server.WsContextListener;

/**
 * The keys and certificates used in this file are all available in svn and were
 * generated using a test CA the files for which are in the Tomcat PMC private
 * repository since not all of them are AL2 licensed.
 */
@RunWith(Parameterized.class)
public class TestManagerWebappSsl extends TomcatBaseTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();
        parameterSets.add(new Object[] {
                "JSSE", Boolean.FALSE, "org.apache.tomcat.util.net.jsse.JSSEImplementation"});
        parameterSets.add(new Object[] {
                "OpenSSL", Boolean.TRUE, "org.apache.tomcat.util.net.openssl.OpenSSLImplementation"});
        parameterSets.add(new Object[] {
                "OpenSSL-FFM", Boolean.TRUE, "org.apache.tomcat.util.net.openssl.panama.OpenSSLImplementation"});

        return parameterSets;
    }

    @Parameter(0)
    public String connectorName;

    @Parameter(1)
    public boolean useOpenSSL;

    @Parameter(2)
    public String sslImplementationName;


    @Test
    public void testConnectors() throws Exception {
        TesterSupport.configureClientSsl();

        Tomcat tomcat = getTomcatInstance();
        tomcat.addUser("admin", "sekr3t");
        tomcat.addRole("admin", "manager-gui");
        tomcat.addRole("admin", "manager-script");
        tomcat.addRole("admin", "manager-jmx");
        tomcat.addRole("admin", "manager-status");

        File webappDir = new File(getBuildDirectory(), "webapps");

        // Add manager webapp
        File appDir = new File(webappDir, "manager");
        tomcat.addWebapp(null, "/manager", appDir.getAbsolutePath());

        appDir = new File(webappDir, "examples");
        Context ctxt  = tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());
        ctxt.addApplicationListener(WsContextListener.class.getName());

        TesterSupport.initSsl(tomcat);
        TesterSupport.configureSSLImplementation(tomcat, sslImplementationName, useOpenSSL);

        tomcat.start();

        ByteChunk res = getUrl("https://localhost:" + getPort() +
            "/examples/servlets/servlet/HelloWorldExample");
        Assert.assertTrue(res.toString().indexOf("<a href=\"../helloworld.html\">") > 0);

        // Add a regular connector
        String protocol = getProtocol();
        Connector connector = new Connector(protocol);
        // Listen only on localhost
        Assert.assertTrue(connector.setProperty("address", InetAddress.getByName("localhost").getHostAddress()));
        // Use random free port
        connector.setPort(0);
        // By default, a connector failure means a failed test
        connector.setThrowOnFailure(true);
        tomcat.setConnector(connector);

        SimpleHttpClient client = new SimpleHttpClient() {
            @Override
            public boolean isResponseBodyOK() {
                return true;
            }
        };
        client.setPort(connector.getLocalPort());
        String basicHeader = (new BasicAuthHeader("Basic", "admin", "sekr3t")).getHeader().toString();

        client.setRequest(new String[] {
                "GET /manager/text/sslConnectorCiphers HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains(" -"));

        client.setRequest(new String[] {
                "GET /manager/text/sslConnectorCerts HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("Subject: CN=localhost"));

        client.setRequest(new String[] {
                "GET /manager/text/sslConnectorTrustedCerts HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertFalse(client.getResponseBody().contains("Subject: CN=localhost"));
        Assert.assertTrue(client.getResponseBody().contains("Subject: CN=Apache Tomcat Test CA"));

        client.setRequest(new String[] {
                "GET /manager/text/sslReload HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains(" -"));

        client.setRequest(new String[] {
                "GET /manager/text/sslConnectorCerts HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Authorization: " + basicHeader + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("Subject: CN=localhost"));

        Assert.assertTrue(res.toString().indexOf("<a href=\"../helloworld.html\">") > 0);

    }

}
