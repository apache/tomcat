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
import java.io.IOException;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.apache.tomcat.util.net.SSLHostConfigCertificate.Type;

/**
 * Tests that, when no {@code certificateKeyAlias} is configured, {@link SSLUtilBase#getKeyManagers()} auto-selects the
 * first key entry whose certificate is valid for TLS server authentication (by Extended Key Usage) and ignores the
 * entries that are not. Applies to every key store type.
 */
@RunWith(Parameterized.class)
public class TestSSLUtilBaseAutomaticCertificateSelection extends SSLUtilBaseCertificateSelectionTestBase {

    @Test
    public void testAutoSelectPrefersServerAuthOverClientAuth() throws Exception {
        X509Certificate presented = present(null,
                key("first", CertType.CLIENT_AUTH),
                key("second", CertType.SERVER_AUTH));
        assertEku(presented, Constants.SERVER_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testAutoSelectAcceptsAnyExtendedKeyUsage() throws Exception {
        X509Certificate presented = present(null,
                key("first", CertType.CLIENT_AUTH),
                key("any", CertType.ANY_AUTH));
        assertEku(presented, Constants.ANY_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testAutoSelectAcceptsCertificateWithoutExtendedKeyUsage() throws Exception {
        X509Certificate presented = present(null,
                key("first", CertType.CLIENT_AUTH),
                key("none", CertType.NO_EKU));
        Assert.assertNull("A certificate with no Extended Key Usage is unrestricted and must be selectable",
                presented.getExtendedKeyUsage());
    }


    @Test
    public void testAutoSelectSkipsTrustedEntries() {
        Assert.assertThrows(IOException.class, () -> present(null,
                KeystoreEntry.trusted("ca"),
                key("key", CertType.CLIENT_AUTH)));
    }


    @Test
    public void testAutoSelectFailsWhenNoneServerAuth() {
        Assert.assertThrows(IOException.class, () -> present(null,
                key("first", CertType.CLIENT_AUTH),
                key("second", CertType.CLIENT_AUTH)));
    }


    @Test
    public void testAutoSelectSkipsMalformedExtendedKeyUsage() throws Exception {
        KeyStore keystore = orderedKeystore("malformed", key("malformed", CertType.CLIENT_AUTH), key("server", CertType.SERVER_AUTH));
        assertEku(present(null, keystore), Constants.SERVER_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testAutoSelectFailsWhenOnlyMalformedExtendedKeyUsage() throws Exception {
        KeyStore keystore = orderedKeystore("malformed", key("malformed", CertType.CLIENT_AUTH));
        Assert.assertThrows(IOException.class, () -> present(null, keystore));
    }


    @Test
    public void testAutoSelectUsesFirstEligibleKey() throws Exception {
        KeyStore keystore = orderedKeystore(null, key("client", CertType.CLIENT_AUTH), key("first", CertType.ANY_AUTH),
                key("second", CertType.SERVER_AUTH));
        Assert.assertEquals(keystore.getCertificate("first"), present(null, keystore));
    }


    @Test
    public void testAutoSelectDoesNotSkipKeyReadFailure() throws Exception {
        KeyStore keystore = orderedKeystore(null, key("first", CertType.SERVER_AUTH), key("second", CertType.SERVER_AUTH));
        SSLHostConfig hostConfig = new SSLHostConfig();
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(hostConfig, Type.UNDEFINED);
        certificate.setCertificateKeystore(keystore);
        certificate.setCertificateKeystoreType("JKS");
        certificate.setCertificateKeyPassword("incorrect");
        SSLUtil util = SSLImplementation.getInstance(sslImplementationName).getSSLUtil(certificate);
        Assert.assertThrows(UnrecoverableKeyException.class, util::getKeyManagers);
    }


    @Test
    public void testSSLContextUsesSelectedCertificate() throws Exception {
        KeyStore keystore = orderedKeystore(null, key("client", CertType.CLIENT_AUTH), key("server", CertType.SERVER_AUTH));
        SSLContext context = sslUtil(null, keystore).createSSLContext(List.of());
        try {
            Assert.assertEquals(keystore.getCertificate("server"), context.getCertificateChain("server")[0]);
        } finally {
            context.destroy();
        }
    }


    @Test
    public void testSSLContextLoadsSelectedCertificateFromKeystoreFile() throws Exception {
        File file = keystoreFile(key(SSLUtilBase.DEFAULT_KEY_ALIAS, CertType.CLIENT_AUTH), key("server", CertType.SERVER_AUTH));
        KeyStore keystore = KeyStore.getInstance(file, TesterSupport.JKS_PASS.toCharArray());
        SSLHostConfig hostConfig = new SSLHostConfig();
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(hostConfig, Type.UNDEFINED);
        certificate.setCertificateKeystoreFile(file.getAbsolutePath());
        certificate.setCertificateKeystorePassword(TesterSupport.JKS_PASS);
        SSLContext context = sslUtil(certificate).createSSLContext(List.of());
        try {
            Assert.assertEquals(keystore.getCertificate("server"), context.getCertificateChain("server")[0]);
        } finally {
            context.destroy();
        }
    }
}
