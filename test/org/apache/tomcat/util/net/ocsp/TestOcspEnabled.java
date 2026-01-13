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
import javax.servlet.http.HttpServletResponse;

import org.junit.AfterClass;
import org.junit.Assert;
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
    public void testValidClientValidServerVerifyNone() throws Exception {
        doTest(true, true, false, false, HttpServletResponse.SC_OK);
    }

    @Test
    public void testValidClientRevokedServerVerifyNone() throws Exception {
        doTest(true, false, false, false, HttpServletResponse.SC_OK);
    }

    @Test
    public void testRevokedClientValidServerVerifyNone() throws Exception {
        doTest(false, true, false, false, HttpServletResponse.SC_OK);
    }

    @Test
    public void testRevokedClientRevokedServerVerifyNone() throws Exception {
        doTest(false, false, false, false, HttpServletResponse.SC_OK);
    }


    @Test
    public void testValidClientValidServerVerifyClient() throws Exception {
        doTest(true, true, true, false, HttpServletResponse.SC_OK);
    }

    @Test(expected = SSLHandshakeException.class)
    public void testRevokedClientValidServerVerifyClient() throws Exception {
        doTest(false, true, true, false, HttpServletResponse.SC_OK);
    }


    private void doTest(boolean clientCertValid, boolean serverCertValid, boolean verifyClientCert,
            boolean verifyServerCert, int expectedStatusCode) throws Exception {
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
        sslHostConfig.setCertificateVerification("required");
        sslHostConfig.setOcspEnabled(verifyClientCert);

        if (clientCertValid) {
            TesterSupport.configureClientSsl(TesterSupport.CLIENT_JKS);
        } else {
            TesterSupport.configureClientSsl(TesterSupport.CLIENT_CRL_JKS);
        }
        // TODO enable client-side OCSP checks

        tomcat.start();

        int rc = getUrl("https://localhost:" + getPort() + "/simple", new ByteChunk(), false);

        Assert.assertEquals(expectedStatusCode, rc);
    }
}
