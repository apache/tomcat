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
 */
public class TestCertificateLoader {

    private static SSLHostConfigCertificate createPkcs12Certificate(String chainFile) throws Exception {
        SSLHostConfig sslHostConfig = new SSLHostConfig();
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(sslHostConfig, Type.RSA);
        certificate.setCertificateFile(TesterSupport.createLocalhostRsaLeafOnlyPkcs12().getAbsolutePath());
        certificate.setCertificateKeyPassword(TesterSupport.JKS_PASS);
        if (chainFile != null) {
            certificate.setCertificateChainFile(new File(chainFile).getAbsolutePath());
        }
        return certificate;
    }


    @Test
    public void testGetCertificateChainPkcs12() throws Exception {
        X509Certificate[] chain = CertificateLoader.getCertificateChain(createPkcs12Certificate(null));

        // The bundle only holds the leaf certificate
        Assert.assertNotNull(chain);
        Assert.assertEquals(1, chain.length);
    }


    /*
     * A separately configured chain file is appended to the chain of the bundle, as it is for a PEM certificate.
     */
    @Test
    public void testGetCertificateChainPkcs12WithChainFile() throws Exception {
        X509Certificate[] chain =
                CertificateLoader.getCertificateChain(createPkcs12Certificate(TesterSupport.CA_CERT_PEM));

        Assert.assertNotNull(chain);
        Assert.assertEquals(2, chain.length);
        Assert.assertEquals(chain[0].getIssuerX500Principal(), chain[1].getSubjectX500Principal());
    }


    @Test
    public void testGetCertificateChainPkcs12WithMissingChainFile() throws Exception {
        X509Certificate[] chain = CertificateLoader.getCertificateChain(
                createPkcs12Certificate("test/org/apache/tomcat/util/net/does-not-exist.pem"));

        // A chain file that cannot be read is ignored, as it is when loading the certificate
        Assert.assertNotNull(chain);
        Assert.assertEquals(1, chain.length);
    }
}
