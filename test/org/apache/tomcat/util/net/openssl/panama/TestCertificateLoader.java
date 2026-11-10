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
package org.apache.tomcat.util.net.openssl.panama;

import java.io.File;
import java.security.cert.X509Certificate;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.Type;
import org.apache.tomcat.util.net.TesterSupport;

/*
 * Only the parts of CertificateLoader that do not need the native OpenSSL library are tested here. Loading into
 * native objects is covered by the tests that start a connector that uses the OpenSSL FFM implementation.
 *
 * The certificate chain file is only used if the certificate file (a PEM file or a PKCS#12 bundle) holds the server
 * certificate alone. If it already holds the certificate chain, the chain file is ignored. The CA certificate is used
 * for the chain file in all of these tests so it would be in the chain twice if the chain file was not ignored.
 */
public class TestCertificateLoader {

    private static SSLHostConfigCertificate createCertificate(File certificateFile, String chainFile) {
        SSLHostConfig sslHostConfig = new SSLHostConfig();
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(sslHostConfig, Type.RSA);
        certificate.setCertificateFile(certificateFile.getAbsolutePath());
        certificate.setCertificateKeyPassword(TesterSupport.JKS_PASS);
        if (chainFile != null) {
            certificate.setCertificateChainFile(new File(chainFile).getAbsolutePath());
        }
        return certificate;
    }


    private static void assertChainOfLeafAndCa(X509Certificate[] chain) {
        Assert.assertNotNull(chain);
        Assert.assertEquals(2, chain.length);
        Assert.assertEquals(chain[0].getIssuerX500Principal(), chain[1].getSubjectX500Principal());
    }


    @Test
    public void testPemLeafOnly() {
        X509Certificate[] chain = CertificateLoader.getCertificateChain(
                createCertificate(new File(TesterSupport.LOCALHOST_RSA_CERT_PEM), null));

        Assert.assertNotNull(chain);
        Assert.assertEquals(1, chain.length);
    }


    @Test
    public void testPemLeafOnlyWithChainFile() {
        X509Certificate[] chain = CertificateLoader.getCertificateChain(
                createCertificate(new File(TesterSupport.LOCALHOST_RSA_CERT_PEM), TesterSupport.CA_CERT_PEM));

        // The chain file is used
        assertChainOfLeafAndCa(chain);
    }


    @Test
    public void testPemWithChainAndChainFile() throws Exception {
        X509Certificate[] chain = CertificateLoader.getCertificateChain(
                createCertificate(TesterSupport.createLocalhostRsaCertAndCaPem(), TesterSupport.CA_CERT_PEM));

        // The chain file is ignored
        assertChainOfLeafAndCa(chain);
    }


    @Test
    public void testPemLeafOnlyWithMissingChainFile() {
        X509Certificate[] chain = CertificateLoader.getCertificateChain(createCertificate(
                new File(TesterSupport.LOCALHOST_RSA_CERT_PEM), "test/org/apache/tomcat/util/net/missing.pem"));

        // A chain file that cannot be read is ignored
        Assert.assertNotNull(chain);
        Assert.assertEquals(1, chain.length);
    }


    @Test
    public void testPkcs12LeafOnly() throws Exception {
        X509Certificate[] chain = CertificateLoader.getCertificateChain(
                createCertificate(TesterSupport.createLocalhostRsaPkcs12(false), null));

        Assert.assertNotNull(chain);
        Assert.assertEquals(1, chain.length);
    }


    @Test
    public void testPkcs12LeafOnlyWithChainFile() throws Exception {
        X509Certificate[] chain = CertificateLoader.getCertificateChain(
                createCertificate(TesterSupport.createLocalhostRsaPkcs12(false), TesterSupport.CA_CERT_PEM));

        // The chain file is used
        assertChainOfLeafAndCa(chain);
    }


    @Test
    public void testPkcs12WithChainAndChainFile() throws Exception {
        X509Certificate[] chain = CertificateLoader.getCertificateChain(
                createCertificate(TesterSupport.createLocalhostRsaPkcs12(true), TesterSupport.CA_CERT_PEM));

        // The chain file is ignored
        assertChainOfLeafAndCa(chain);
    }


    @Test
    public void testPkcs12LeafOnlyWithMissingChainFile() throws Exception {
        X509Certificate[] chain = CertificateLoader.getCertificateChain(createCertificate(
                TesterSupport.createLocalhostRsaPkcs12(false), "test/org/apache/tomcat/util/net/missing.pem"));

        // A chain file that cannot be read is ignored
        Assert.assertNotNull(chain);
        Assert.assertEquals(1, chain.length);
    }
}
