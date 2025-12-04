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
package org.apache.tomcat.util.net.ocsp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.net.ssl.SSLHandshakeException;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.TesterSupport;
import org.apache.tomcat.util.net.TesterSupport.SimpleServlet;


@RunWith(Parameterized.class)
public class TestOcspEnabled extends TomcatBaseTest {

    private static TesterOcspResponder ocspResponder;
    private static final File lockFile = new File("test/org/apache/tomcat/util/net/ocsp/ocsp-responder.lock");
    private static FileLock lock = null;

    @BeforeClass
    public static void obtainOcspResponderLock() throws IOException {
        @SuppressWarnings("resource")
        FileOutputStream fos = new FileOutputStream(lockFile);
        lock = fos.getChannel().lock();
    }

    @AfterClass
    public static void releaseOcspResponderLock() throws IOException {
        // Should not be null be in case obtaining the lock fails, avoid a second error.
        if (lock != null) {
            lock.release();
        }
    }


    @Parameterized.Parameters(name = "{0} with OpenSSL trust {2}")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();
        parameterSets.add(new Object[] { "JSSE", Boolean.FALSE, Boolean.FALSE,
                "org.apache.tomcat.util.net.jsse.JSSEImplementation"});
        parameterSets.add(new Object[] { "OpenSSL", Boolean.TRUE, Boolean.TRUE,
                "org.apache.tomcat.util.net.openssl.OpenSSLImplementation" });
        parameterSets.add(new Object[] { "OpenSSL", Boolean.TRUE, Boolean.FALSE,
                "org.apache.tomcat.util.net.openssl.OpenSSLImplementation" });
        parameterSets.add(new Object[] { "OpenSSL-FFM", Boolean.TRUE, Boolean.TRUE,
                "org.apache.tomcat.util.net.openssl.panama.OpenSSLImplementation" });
        parameterSets.add(new Object[] { "OpenSSL-FFM", Boolean.TRUE, Boolean.FALSE,
                "org.apache.tomcat.util.net.openssl.panama.OpenSSLImplementation" });

        return parameterSets;
    }

    @Parameter(0)
    public String connectorName;

    @Parameter(1)
    public boolean useOpenSSL;

    @Parameter(2)
    public boolean useOpenSSLTrust;

    @Parameter(3)
    public String sslImplementationName;


    @BeforeClass
    public static void startOcspResponder() throws IOException {
        ocspResponder = new TesterOcspResponder();
        ocspResponder.start();
    }


    @Override
    public void setUp() throws Exception {
        super.setUp();
        Tomcat tomcat = getTomcatInstance();
        TesterSupport.configureSSLImplementation(tomcat, sslImplementationName, useOpenSSL);
    }


    @AfterClass
    public static void stopOcspResponder() {
        ocspResponder.stop();
        ocspResponder = null;
    }


    @Test
    public void testRevokedClientRevokedServerVerifyNone() throws Exception {
        doTest(false, false, ClientCertificateVerification.DISABLED, false);
    }

    @Test(expected = SSLHandshakeException.class)
    public void testRevokedClientRevokedServerVerifyServer() throws Exception {
        doTest(false, false, ClientCertificateVerification.DISABLED, true);
    }

    @Test
    public void testRevokedClientRevokedServerVerifyClientOptional() throws Exception {
        doTest(false, false, ClientCertificateVerification.OPTIONAL_NO_CA, false);
    }

    @Test(expected = SSLHandshakeException.class)
    public void testRevokedClientRevokedServerVerifyClientOpionalVerifyServer() throws Exception {
        // Same as false, false, false, true since server certificate is verified before client certificate
        doTest(false, false, ClientCertificateVerification.OPTIONAL_NO_CA, true);
    }

    @Test(expected = SSLHandshakeException.class)
    public void testRevokedClientRevokedServerVerifyClient() throws Exception {
        doTest(false, false, ClientCertificateVerification.ENABLED, false);
    }

    @Test(expected = SSLHandshakeException.class)
    public void testRevokedClientRevokedServerVerifyBoth() throws Exception {
        // Same as false, false, false, true since server certificate is verified before client certificate
        doTest(false, false, ClientCertificateVerification.ENABLED, true);
    }

    @Test
    public void testRevokedClientValidServerVerifyNone() throws Exception {
        doTest(false, true, ClientCertificateVerification.DISABLED, false);
    }

    @Test
    public void testRevokedClientValidServerVerifyServer() throws Exception {
        doTest(false, true, ClientCertificateVerification.DISABLED, true);
    }

    @Test
    public void testRevokedClientValidServerVerifyClientOptional() throws Exception {
        doTest(false, true, ClientCertificateVerification.OPTIONAL_NO_CA, false);
    }

    @Test
    public void testRevokedClientValidServerVerifyClientOptionalVerifyServer() throws Exception {
        doTest(false, true, ClientCertificateVerification.OPTIONAL_NO_CA, true);
    }

    @Test(expected = SSLHandshakeException.class)
    public void testRevokedClientValidServerVerifyClient() throws Exception {
        doTest(false, true, ClientCertificateVerification.ENABLED, false);
    }

    @Test(expected = SSLHandshakeException.class)
    public void testRevokedClientValidServerVerifyBoth() throws Exception {
        doTest(false, true, ClientCertificateVerification.ENABLED, true);
    }

    @Test
    public void testValidClientRevokedServerVerifyNone() throws Exception {
        doTest(true, false, ClientCertificateVerification.DISABLED, false);
    }

    @Test(expected = SSLHandshakeException.class)
    public void testValidClientRevokedServerVerifyServer() throws Exception {
        doTest(true, false, ClientCertificateVerification.DISABLED, true);
    }

    @Test
    public void testValidClientRevokedServerVerifyClientOptional() throws Exception {
        doTest(true, false, ClientCertificateVerification.OPTIONAL_NO_CA, false);
    }

    @Test(expected = SSLHandshakeException.class)
    public void testValidClientRevokedServerVerifyClientOptionalVerifyServer() throws Exception {
        doTest(true, false, ClientCertificateVerification.OPTIONAL_NO_CA, true);
    }

    @Test
    public void testValidClientRevokedServerVerifyClient() throws Exception {
        doTest(true, false, ClientCertificateVerification.ENABLED, false);
    }

    @Test(expected = SSLHandshakeException.class)
    public void testValidClientRevokedServerVerifyBoth() throws Exception {
        doTest(true, false, ClientCertificateVerification.ENABLED, true);
    }

    @Test
    public void testValidClientValidServerVerifyNone() throws Exception {
        doTest(true, true, ClientCertificateVerification.DISABLED, false);
    }

    @Test
    public void testValidClientValidServerVerifyServer() throws Exception {
        doTest(true, true, ClientCertificateVerification.DISABLED, true);
    }

    @Test
    public void testValidClientValidServerVerifyClientOptional() throws Exception {
        doTest(true, true, ClientCertificateVerification.OPTIONAL_NO_CA, false);
    }

    @Test
    public void testValidClientValidServerVerifyClientOptionalVerifyServer() throws Exception {
        doTest(true, true, ClientCertificateVerification.OPTIONAL_NO_CA, true);
    }

    @Test
    public void testValidClientValidServerVerifyClient() throws Exception {
        doTest(true, true, ClientCertificateVerification.ENABLED, false);
    }

    @Test
    public void testValidClientValidServerVerifyBoth() throws Exception {
        doTest(true, true, ClientCertificateVerification.ENABLED, true);
    }


    private void doTest(boolean clientCertValid, boolean serverCertValid, ClientCertificateVerification verifyClientCert,
            boolean verifyServerCert) throws Exception {

        Assume.assumeFalse(!useOpenSSLTrust && verifyClientCert == ClientCertificateVerification.OPTIONAL_NO_CA);

        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        Tomcat.addServlet(ctx, "simple", new SimpleServlet());
        ctx.addServletMappingDecoded("/simple", "simple");

        if (serverCertValid) {
            TesterSupport.initSsl(tomcat, TesterSupport.LOCALHOST_RSA_JKS, useOpenSSLTrust);
        } else {
            TesterSupport.initSsl(tomcat, TesterSupport.LOCALHOST_CRL_RSA_JKS, useOpenSSLTrust);
        }
        SSLHostConfig sslHostConfig = tomcat.getConnector().findSslHostConfigs()[0];
        switch (verifyClientCert) {
            case DISABLED:
                sslHostConfig.setOcspEnabled(false);
                sslHostConfig.setCertificateVerification("required");
                break;
            case ENABLED:
                sslHostConfig.setOcspEnabled(true);
                sslHostConfig.setCertificateVerification("required");
                break;
            case OPTIONAL_NO_CA:
                sslHostConfig.setOcspEnabled(true);
                sslHostConfig.setCertificateVerification("optionalNoCA");
                break;

        }

        if (clientCertValid) {
            TesterSupport.configureClientSsl(verifyServerCert, TesterSupport.CLIENT_JKS);
        } else {
            TesterSupport.configureClientSsl(verifyServerCert, TesterSupport.CLIENT_CRL_JKS);
        }

        tomcat.start();

        int rc = getUrl("https://localhost:" + getPort() + "/simple", new ByteChunk(), false);

        // If the TLS handshake fails, the test won't get this far.
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
    }


    private enum ClientCertificateVerification {
        ENABLED,
        OPTIONAL_NO_CA,
        DISABLED
    }
}
