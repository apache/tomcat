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

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
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

    @Test
    public void testCustomSslImplementation() throws Exception {

        TesterSupport.configureClientSsl();

        Tomcat tomcat = getTomcatInstance();
        Connector connector = tomcat.getConnector();

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

        certificate.setCertificateKeyPassword(TesterSupport.JKS_PASS);

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
}
