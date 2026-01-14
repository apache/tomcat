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
package org.apache.tomcat.security;

import java.io.IOException;
import java.net.SocketException;

import javax.net.ssl.SSLHandshakeException;
import javax.servlet.http.HttpServletResponse;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.TesterSupport;
import org.apache.tomcat.util.net.TesterSupport.SimpleServlet;
import org.apache.tomcat.util.net.ocsp.OcspBaseTest;
import org.apache.tomcat.util.net.ocsp.TesterOcspResponder;
import org.apache.tomcat.util.openssl.openssl_h_Compatibility;

@RunWith(Parameterized.class)
public class TestSecurity2017Ocsp extends OcspBaseTest {

    private static TesterOcspResponder ocspResponder;

    @BeforeClass
    public static void startOcspResponder() {
        ocspResponder = new TesterOcspResponder();
        try {
            ocspResponder.start();
        } catch (IOException ioe) {
            ocspResponder = null;
        }
    }


    @AfterClass
    public static void stopOcspResponder() {
        if (ocspResponder != null) {
            ocspResponder.stop();
            ocspResponder = null;
        }
    }


    /*
     * In addition to testing Tomcat Native (where the CVE occurred), this also tests JSSE and OpenSSl via FFM.
     */
    @Test(expected=SSLHandshakeException.class)
    public void testCVE_2017_15698() throws Exception {
        if ("OpenSSL-FFM".equals(connectorName)) {
            Assume.assumeFalse(openssl_h_Compatibility.BORINGSSL || openssl_h_Compatibility.isLibreSSLPre35());
        }
        Assume.assumeNotNull(ocspResponder);

        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        Tomcat.addServlet(ctx, "simple", new SimpleServlet());
        ctx.addServletMappingDecoded("/simple", "simple");

        // User a valid (non-revoked) server certificate
        TesterSupport.initSsl(tomcat, TesterSupport.LOCALHOST_RSA_JKS, useOpenSSLTrust);

        // Require client certificates and enable verification
        SSLHostConfig sslHostConfig = tomcat.getConnector().findSslHostConfigs()[0];
        sslHostConfig.setOcspEnabled(true);
        sslHostConfig.setCertificateVerification("required");

        // Configure a revoked client certificate with a long AIA
        // Don't verify the server certificate
        TesterSupport.configureClientSsl(false, TesterSupport.CLIENT_CRL_LONG_JKS);

        // Disable soft-fail
        sslHostConfig.setOcspSoftFail(false);

        tomcat.start();

        int rc;
        try {
            rc = getUrl("https://localhost:" + getPort() + "/simple", new ByteChunk(), false);
        } catch (SocketException se) {
            throw new SSLHandshakeException(se.getMessage());
        }

        // If the TLS handshake fails, the test won't get this far.
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
    }
}
