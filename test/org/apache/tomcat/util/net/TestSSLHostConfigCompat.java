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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.StoreType;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.Type;
import org.apache.tomcat.util.net.TesterSupport.ClientSSLSocketFactory;

/*
 * Tests compatibility of JSSE and OpenSSL settings.
 */
@RunWith(Parameterized.class)
public class TestSSLHostConfigCompat extends TomcatBaseTest {

    @Parameterized.Parameters(name = "{0}-{3}")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        for (StoreType storeType : new StoreType[] { StoreType.KEYSTORE, StoreType.PEM } ) {
            parameterSets.add(new Object[] {
                    "JSSE", Boolean.FALSE, "org.apache.tomcat.util.net.jsse.JSSEImplementation", storeType});
            parameterSets.add(new Object[] {
                    "OpenSSL", Boolean.TRUE, "org.apache.tomcat.util.net.openssl.OpenSSLImplementation", storeType});
            parameterSets.add(new Object[] {
                    "OpenSSL-FFM", Boolean.TRUE, "org.apache.tomcat.util.net.openssl.panama.OpenSSLImplementation", storeType});
        }

        return parameterSets;
    }

    @Parameter(0)
    public String connectorName;

    @Parameter(1)
    public boolean useOpenSSL;

    @Parameter(2)
    public String sslImplementationName;

    @Parameter(3)
    public StoreType storeType;


    @Test
    public void testHostEC() throws Exception {
        configureHostEC();
        doTest();
    }


    @Test
    public void testHostRSA() throws Exception {
        configureHostRSA();
        doTest();
    }


    @Test
    public void testHostRSAandECwithDefaultClient() throws Exception {
        configureHostRSA();
        configureHostEC();
        doTest();
    }


    /*
     * This test and the next just swap the order in which the server certs are
     * configured to ensure correct operation isn't dependent on order.
     */
    @Test
    public void testHostRSAandECwithRSAClient() throws Exception {
        configureHostRSA();
        configureHostEC();

        // Configure cipher suite that requires an RSA certificate on the server
        ClientSSLSocketFactory clientSSLSocketFactory = TesterSupport.configureClientSsl();
        clientSSLSocketFactory.setCipher(new String[] {"TLS_DHE_RSA_WITH_AES_256_GCM_SHA384"});

        doTest(false);
    }


    /*
     * This test and the previous just swap the order in which the server certs
     * are configured to ensure correct operation isn't dependent on order.
     */
    @Test
    public void testHostECandRSAwithRSAClient() throws Exception {
        configureHostEC();
        configureHostRSA();

        // Configure cipher suite that requires an RSA certificate on the server
        ClientSSLSocketFactory clientSSLSocketFactory = TesterSupport.configureClientSsl();
        clientSSLSocketFactory.setCipher(new String[] {"TLS_DHE_RSA_WITH_AES_256_GCM_SHA384"});

        doTest(false);
    }


    /*
     * This test and the next just swap the order in which the server certs are
     * configured to ensure correct operation isn't dependent on order.
     */
    @Test
    public void testHostRSAandECwithECClient() throws Exception {
        configureHostRSA();
        configureHostEC();

        // Configure cipher suite that requires an EC certificate on the server
        ClientSSLSocketFactory clientSSLSocketFactory = TesterSupport.configureClientSsl();
        clientSSLSocketFactory.setCipher(new String[] {"TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384"});

        doTest(false);
    }


    /*
     * This test and the previous just swap the order in which the server certs
     * are configured to ensure correct operation isn't dependent on order.
     */
    @Test
    public void testHostECandRSAwithECClient() throws Exception {
        configureHostEC();
        configureHostRSA();

        // Configure cipher suite that requires an EC certificate on the server
        ClientSSLSocketFactory clientSSLSocketFactory = TesterSupport.configureClientSsl();
        clientSSLSocketFactory.setCipher(new String[] {"TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384"});

        doTest(false);
    }


    @Test
    public void testHostRSAwithRSAClient() throws Exception {
        configureHostRSA();

        // Configure cipher suite that requires an RSA certificate on the server
        ClientSSLSocketFactory clientSSLSocketFactory = TesterSupport.configureClientSsl();
        clientSSLSocketFactory.setCipher(new String[] {"TLS_DHE_RSA_WITH_AES_256_GCM_SHA384"});

        doTest(false);
    }


    @Test(expected=javax.net.ssl.SSLHandshakeException.class)
    public void testHostRSAwithECClient() throws Exception {
        configureHostRSA();

        // Configure cipher suite that requires an EC certificate on the server
        ClientSSLSocketFactory clientSSLSocketFactory = TesterSupport.configureClientSsl();
        clientSSLSocketFactory.setCipher(new String[] {"TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384"});

        doTest(false);
    }


    @Test
    public void testHostRSAwithRSAandECClient() throws Exception {
        configureHostRSA();

        // Configure cipher suite that requires an EC certificate on the server
        ClientSSLSocketFactory clientSSLSocketFactory = TesterSupport.configureClientSsl();
        clientSSLSocketFactory.setCipher(new String[] {
                "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384"});

        doTest(false);
    }


    @Test(expected=javax.net.ssl.SSLHandshakeException.class)
    public void testHostECwithRSAClient() throws Exception {
        configureHostEC();

        // Configure cipher suite that requires an RSA certificate on the server
        ClientSSLSocketFactory clientSSLSocketFactory = TesterSupport.configureClientSsl();
        clientSSLSocketFactory.setCipher(new String[] {"TLS_DHE_RSA_WITH_AES_256_GCM_SHA384"});

        doTest(false);
    }


    @Test
    public void testHostECwithECClient() throws Exception {
        configureHostEC();

        // Configure cipher suite that requires an EC certificate on the server
        ClientSSLSocketFactory clientSSLSocketFactory = TesterSupport.configureClientSsl();
        clientSSLSocketFactory.setCipher(new String[] {"TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384"});

        doTest(false);
    }


    @Test
    public void testHostECwithRSAandECClient() throws Exception {
        configureHostEC();

        // Configure cipher suite that requires an RSA certificate on the server
        ClientSSLSocketFactory clientSSLSocketFactory = TesterSupport.configureClientSsl();
        clientSSLSocketFactory.setCipher(new String[] {
                "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
                "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384"});

        doTest(false);
    }


    private void configureHostRSA() {
        SSLHostConfig sslHostConfig = getSSLHostConfig();
        switch (storeType) {
        case KEYSTORE: {
            SSLHostConfigCertificate sslHostConfigCertificateRsa = new SSLHostConfigCertificate(sslHostConfig, Type.RSA);
            sslHostConfigCertificateRsa.setCertificateKeystoreFile(getPath(TesterSupport.LOCALHOST_RSA_JKS));
            sslHostConfig.addCertificate(sslHostConfigCertificateRsa);
            break;
        }
        case PEM: {
            SSLHostConfigCertificate sslHostConfigCertificateRsa = new SSLHostConfigCertificate(sslHostConfig, Type.RSA);
            sslHostConfigCertificateRsa.setCertificateFile(getPath(TesterSupport.LOCALHOST_RSA_CERT_PEM));
            sslHostConfigCertificateRsa.setCertificateKeyFile(getPath(TesterSupport.LOCALHOST_RSA_KEY_PEM));
            sslHostConfig.addCertificate(sslHostConfigCertificateRsa);
            break;
        }
        }
    }


    private void configureHostEC() {
        SSLHostConfig sslHostConfig = getSSLHostConfig();
        switch (storeType) {
        case KEYSTORE: {
            SSLHostConfigCertificate sslHostConfigCertificateEc = new SSLHostConfigCertificate(sslHostConfig, Type.EC);
            sslHostConfigCertificateEc.setCertificateKeystoreFile(getPath(TesterSupport.LOCALHOST_EC_JKS));
            sslHostConfig.addCertificate(sslHostConfigCertificateEc);
            break;
        }
        case PEM: {
            SSLHostConfigCertificate sslHostConfigCertificateEc = new SSLHostConfigCertificate(sslHostConfig, Type.EC);
            sslHostConfigCertificateEc.setCertificateFile(getPath(TesterSupport.LOCALHOST_EC_CERT_PEM));
            sslHostConfigCertificateEc.setCertificateKeyFile(getPath(TesterSupport.LOCALHOST_EC_KEY_PEM));
            sslHostConfig.addCertificate(sslHostConfigCertificateEc);
            break;
        }
        }
    }


    private void doTest() throws Exception {
        // Use the default client TLS config
        doTest(true);
    }


    private void doTest(boolean configureClientSsl) throws Exception {
        if (configureClientSsl) {
            TesterSupport.configureClientSsl();
        }

        Tomcat tomcat = getTomcatInstance();
        tomcat.start();

        // Check a request can be made
        ByteChunk res = getUrl("https://localhost:" + getPort() + "/");
        Assert.assertEquals("OK", res.toString());
    }


    @Override
    public void setUp() throws Exception {
        super.setUp();

        Tomcat tomcat = getTomcatInstance();
        Connector connector = tomcat.getConnector();

        connector.setPort(0);
        connector.setScheme("https");
        connector.setSecure(true);
        Assert.assertTrue(connector.setProperty("SSLEnabled", "true"));
        SSLHostConfig sslHostConfig = new SSLHostConfig();
        sslHostConfig.setProtocols("TLSv1.2");
        connector.addSslHostConfig(sslHostConfig);

        TesterSupport.configureSSLImplementation(tomcat, sslImplementationName, useOpenSSL);

        // Simple webapp
        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "TesterServlet", new TesterServlet());
        ctxt.addServletMappingDecoded("/*", "TesterServlet");
    }


    private SSLHostConfig getSSLHostConfig() {
        Tomcat tomcat = getTomcatInstance();
        Connector connector = tomcat.getConnector();
        return connector.findSslHostConfigs()[0];
    }


    private static String getPath(String relativePath) {
        File f = new File(relativePath);
        return f.getAbsolutePath();
    }
}
