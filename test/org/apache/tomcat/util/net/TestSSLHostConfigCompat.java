/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
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
package org.apache.tomcat.util.net;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.AprLifecycleListener;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.Type;

/*
 * Tests compatibility of JSSE and OpenSSL settings.
 */
@RunWith(Parameterized.class)
public class TestSSLHostConfigCompat extends TomcatBaseTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        parameterSets.add(new Object[] {"NIO-JSSE", "org.apache.coyote.http11.Http11NioProtocol",
                "org.apache.tomcat.util.net.jsse.JSSEImplementation"});

        parameterSets.add(new Object[] {"NIO-OpenSSL", "org.apache.coyote.http11.Http11NioProtocol",
                "org.apache.tomcat.util.net.openssl.OpenSSLImplementation"});

        parameterSets.add(new Object[] { "APR/Native", "org.apache.coyote.http11.Http11AprProtocol",
                "org.apache.tomcat.util.net.openssl.OpenSSLImplementation"});

        return parameterSets;
    }

    @Parameter(0)
    public String connectorName;

    @Parameter(1)
    public String protocolName;

    @Parameter(2)
    public String sslImplementationName;

    private SSLHostConfig sslHostConfig = new SSLHostConfig();


    @Test
    public void testHostECPEM() throws Exception {
        sslHostConfig.setCertificateFile(getPath(TesterSupport.LOCALHOST_EC_CERT_PEM));
        sslHostConfig.setCertificateKeyFile(getPath(TesterSupport.LOCALHOST_EC_KEY_PEM));
        doTest();
    }


    @Test
    public void testHostRSAPEM() throws Exception {
        sslHostConfig.setCertificateFile(getPath(TesterSupport.LOCALHOST_RSA_CERT_PEM));
        sslHostConfig.setCertificateKeyFile(getPath(TesterSupport.LOCALHOST_RSA_KEY_PEM));
        doTest();
    }


    @Test
    public void testHostRSAandECPEM() throws Exception {
        SSLHostConfigCertificate sslHostConfigCertificateRsa = new SSLHostConfigCertificate(sslHostConfig, Type.RSA);
        sslHostConfigCertificateRsa.setCertificateFile(getPath(TesterSupport.LOCALHOST_RSA_CERT_PEM));
        sslHostConfigCertificateRsa.setCertificateKeyFile(getPath(TesterSupport.LOCALHOST_RSA_KEY_PEM));
        SSLHostConfigCertificate sslHostConfigCertificateEc = new SSLHostConfigCertificate(sslHostConfig, Type.EC);
        sslHostConfigCertificateEc.setCertificateFile(getPath(TesterSupport.LOCALHOST_EC_CERT_PEM));
        sslHostConfigCertificateEc.setCertificateKeyFile(getPath(TesterSupport.LOCALHOST_EC_KEY_PEM));
        sslHostConfig.addCertificate(sslHostConfigCertificateEc);
        doTest();
    }


    @Test
    @Ignore // Currently the APR/native connector cannot be configured using a Keystore
    public void testHostKeystore() throws Exception {
        sslHostConfig.setCertificateKeystoreFile(getPath(TesterSupport.LOCALHOST_JKS));
        doTest();
    }


    private void doTest() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.start();

        // Check a request can be made
        ByteChunk res = getUrl("https://localhost:" + getPort() + "/");
        Assert.assertEquals("OK", res.toString());
    }


    @Override
    protected String getProtocol() {
        return protocolName;
    }


    @Override
    public void setUp() throws Exception {
        super.setUp();

        AprLifecycleListener listener = new AprLifecycleListener();
        Assume.assumeTrue(AprLifecycleListener.isAprAvailable());

        TesterSupport.configureClientSsl();

        Tomcat tomcat = getTomcatInstance();
        Connector connector = tomcat.getConnector();

        connector.setPort(0);
        connector.setScheme("https");
        connector.setSecure(true);
        connector.setProperty("SSLEnabled", "true");
        connector.setProperty("sslImplementationName", sslImplementationName);
        connector.addSslHostConfig(sslHostConfig);

        StandardServer server = (StandardServer) tomcat.getServer();
        server.addLifecycleListener(listener);

        // Simple webapp
        Context ctxt = tomcat.addContext("", null);
        Tomcat.addServlet(ctxt, "TesterServlet", new TesterServlet());
        ctxt.addServletMappingDecoded("/*", "TesterServlet");
    }


    private static String getPath(String relativePath) {
        File f = new File(relativePath);
        return f.getAbsolutePath();
    }
}
