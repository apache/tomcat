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
package org.apache.tomcat.util.net;

import java.io.File;
import java.net.SocketException;

import javax.net.ssl.SSLException;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.http11.AbstractHttp11JsseProtocol;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.Type;
import org.apache.tomcat.util.net.jsse.TesterBug50640SslImpl;
import org.apache.tomcat.websocket.server.WsContextListener;

/**
 * The keys and certificates used in this file are all available in svn and were
 * generated using a test CA the files for which are in the Tomcat PMC private
 * repository since not all of them are AL2 licensed.
 */
public class TestCustomSsl extends TomcatBaseTest {

    private static enum TrustType {
        ALL,
        CA,
        NONE
    }

    @Test
    public void testCustomSslImplementation() throws Exception {

        TesterSupport.configureClientSsl();

        Tomcat tomcat = getTomcatInstance();
        Connector connector = tomcat.getConnector();

        Assume.assumeFalse("This test is only for JSSE based SSL connectors",
                connector.getProtocolHandlerClassName().contains("Apr"));

        SSLHostConfig sslHostConfig = new SSLHostConfig();
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(sslHostConfig, Type.UNDEFINED);
        sslHostConfig.addCertificate(certificate);
        connector.addSslHostConfig(sslHostConfig);

        Assert.assertTrue(connector.setProperty(
                "sslImplementationName", "org.apache.tomcat.util.net.jsse.TesterBug50640SslImpl"));

        // This setting will break ssl configuration unless the custom
        // implementation is used.
        sslHostConfig.setProtocols(TesterBug50640SslImpl.PROPERTY_VALUE);

        sslHostConfig.setSslProtocol("tls");

        File keystoreFile = new File(TesterSupport.LOCALHOST_RSA_JKS);
        certificate.setCertificateKeystoreFile(keystoreFile.getAbsolutePath());

        connector.setSecure(true);
        Assert.assertTrue(connector.setProperty("SSLEnabled", "true"));

        File appDir = new File(getBuildDirectory(), "webapps/examples");
        Context ctxt  = tomcat.addWebapp(
                null, "/examples", appDir.getAbsolutePath());
        ctxt.addApplicationListener(WsContextListener.class.getName());

        tomcat.start();
        ByteChunk res = getUrl("https://localhost:" + getPort() +
            "/examples/servlets/servlet/HelloWorldExample");
        Assert.assertTrue(res.toString().indexOf("<a href=\"../helloworld.html\">") > 0);
    }

    @Test
    public void testCustomTrustManagerAll() throws Exception {
        doTestCustomTrustManager(TrustType.ALL);
    }

    @Test
    public void testCustomTrustManagerCA() throws Exception {
        doTestCustomTrustManager(TrustType.CA);
    }

    @Test
    public void testCustomTrustManagerNone() throws Exception {
        doTestCustomTrustManager(TrustType.NONE);
    }

    private void doTestCustomTrustManager(TrustType trustType)
            throws Exception {

        Tomcat tomcat = getTomcatInstance();

        Assume.assumeTrue("SSL renegotiation has to be supported for this test",
                TesterSupport.isRenegotiationSupported(tomcat));

        TesterSupport.configureClientCertContext(tomcat);

        Connector connector = tomcat.getConnector();

        // Override the defaults
        ProtocolHandler handler = connector.getProtocolHandler();
        if (handler instanceof AbstractHttp11JsseProtocol) {
            connector.findSslHostConfigs()[0].setTruststoreFile(null);
        } else {
            // Unexpected
            Assert.fail("Unexpected handler type");
        }
        if (trustType.equals(TrustType.ALL)) {
            connector.findSslHostConfigs()[0].setTrustManagerClassName(
                    "org.apache.tomcat.util.net.TesterSupport$TrustAllCerts");
        } else if (trustType.equals(TrustType.CA)) {
            connector.findSslHostConfigs()[0].setTrustManagerClassName(
                    "org.apache.tomcat.util.net.TesterSupport$SequentialTrustManager");
        }

        // Start Tomcat
        tomcat.start();

        TesterSupport.configureClientSsl();

        // Unprotected resource
        ByteChunk res = getUrl("https://localhost:" + getPort() + "/unprotected");
        Assert.assertEquals("OK", res.toString());

        // Protected resource
        res.recycle();
        int rc = -1;
        try {
            rc = getUrl("https://localhost:" + getPort() + "/protected", res, null, null);
        } catch (SocketException | SSLException e) {
            if (!trustType.equals(TrustType.NONE)) {
                Assert.fail(e.getMessage());
                e.printStackTrace();
            }
        }

        if (trustType.equals(TrustType.CA)) {
            if (log.isDebugEnabled()) {
                int count = TesterSupport.getLastClientAuthRequestedIssuerCount();
                log.debug("Last client KeyManager usage: " + TesterSupport.getLastClientAuthKeyManagerUsage() +
                          ", " + count + " requested Issuers, first one: " +
                          (count > 0 ? TesterSupport.getLastClientAuthRequestedIssuer(0).getName() : "NONE"));
                log.debug("Expected requested Issuer: " + TesterSupport.getClientAuthExpectedIssuer());
            }
            Assert.assertTrue("Checking requested client issuer against " +
                    TesterSupport.getClientAuthExpectedIssuer(),
                    TesterSupport.checkLastClientAuthRequestedIssuers());
        }

        if (trustType.equals(TrustType.NONE)) {
            Assert.assertTrue(rc != 200);
            Assert.assertEquals("", res.toString());
        } else {
            Assert.assertEquals(200, rc);
            Assert.assertEquals("OK-" + TesterSupport.ROLE, res.toString());
        }
    }
}
