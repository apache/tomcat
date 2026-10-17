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
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.X509KeyManager;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.net.SSLHostConfigCertificate.Type;
import org.apache.tomcat.util.net.TesterKeystoreGenerator.CertificateExtensionsCustomizer;
import org.apache.tomcat.util.net.TesterKeystoreGenerator.KeystoreEntry;
import org.apache.tomcat.util.net.jsse.JSSEUtil;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;

/**
 * Tests selection of a TLS server authentication certificate when a key store holds several certificates that share a
 * Subject.
 */
public class TestSSLUtilBaseKeyUsageSelection {

    private static final String HOST = "localhost";

    @SuppressWarnings("unused")
    private static final CertificateExtensionsCustomizer SERVER_AUTH = (keyPair, builder) -> builder
            .addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
    @SuppressWarnings("unused")
    private static final CertificateExtensionsCustomizer CLIENT_AUTH = (keyPair, builder) -> builder
            .addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));
    @SuppressWarnings("unused")
    private static final CertificateExtensionsCustomizer ANY_AUTH = (keyPair, builder) -> builder
            .addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.anyExtendedKeyUsage));


    private static KeystoreEntry key(String alias, CertificateExtensionsCustomizer eku) {
        return KeystoreEntry.key(alias, HOST, eku);
    }


    /*
     * Builds a key store from the given entries, configures a JSSE certificate pointed at alias (null for none), and
     * returns the leaf certificate the key manager actually presents.
     */
    private static X509Certificate present(String alias, KeystoreEntry... entries) throws Exception {
        File keystore = TesterKeystoreGenerator.generateKeystore(List.of(entries));

        SSLHostConfig hostConfig = new SSLHostConfig();
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(hostConfig, Type.UNDEFINED);
        certificate.setCertificateKeystoreFile(keystore.getAbsolutePath());
        certificate.setCertificateKeystorePassword(TesterSupport.JKS_PASS);
        if (alias != null) {
            certificate.setCertificateKeyAlias(alias);
        }

        X509KeyManager keyManager = (X509KeyManager) new JSSEUtil(certificate).getKeyManagers()[0];
        X509Certificate[] chain = keyManager.getCertificateChain(keyManager.chooseServerAlias("RSA", null, null));
        Assert.assertNotNull("Key manager returned no certificate chain", chain);
        return chain[0];
    }


    private static void assertEku(X509Certificate certificate, String oid) throws Exception {
        List<String> extendedKeyUsage = certificate.getExtendedKeyUsage();
        Assert.assertNotNull("Presented certificate has no Extended Key Usage", extendedKeyUsage);
        Assert.assertTrue("Presented certificate must be valid for Extended Key Usage " + oid,
                extendedKeyUsage.contains(oid));
    }


    @Test
    public void testSelectsServerAuthCertificateOverConfiguredClientAuthAlias() throws Exception {
        X509Certificate presented = present(HOST,
                key(HOST, CLIENT_AUTH),
                key("localhost-server", SERVER_AUTH));
        assertEku(presented, SSLUtilBase.SERVER_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testKeepsConfiguredAliasWhenItIsAlreadyServerAuth() throws Exception {
        X509Certificate presented = present(HOST,
                key(HOST, SERVER_AUTH),
                key("localhost-client", CLIENT_AUTH));
        assertEku(presented, SSLUtilBase.SERVER_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testSelectsCertificateWithAnyExtendedKeyUsage() throws Exception {
        X509Certificate presented = present(HOST,
                key(HOST, CLIENT_AUTH),
                key("localhost-any", ANY_AUTH));
        assertEku(presented, SSLUtilBase.ANY_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testSelectsCertificateWithoutExtendedKeyUsage() throws Exception {
        X509Certificate presented = present(HOST,
                key(HOST, CLIENT_AUTH),
                key("localhost-none", null));
        Assert.assertNull("A certificate with no Extended Key Usage is unrestricted and must be selectable",
                presented.getExtendedKeyUsage());
    }


    @Test
    public void testSelectsServerAuthCertificateWhenNoAliasConfigured() throws Exception {
        X509Certificate presented = present(null,
                key("first", CLIENT_AUTH),
                key("second", SERVER_AUTH));
        assertEku(presented, SSLUtilBase.SERVER_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testSelectsServerAuthCertificateWhenMultipleMatch() throws Exception {
        // Two server-auth certificates share the Subject. Which one wins depends on key-store enumeration order,
        // which JKS does not guarantee, so this only asserts that the ambiguity resolves to a server-auth
        // certificate rather than the client-auth one - not which of the two is chosen.
        X509Certificate presented = present(HOST,
                key(HOST, CLIENT_AUTH),
                key("localhost-s1", SERVER_AUTH),
                key("localhost-s2", SERVER_AUTH));
        assertEku(presented, SSLUtilBase.SERVER_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testFallsBackToConfiguredAliasWhenNoServerAuthCertificate() throws Exception {
        X509Certificate presented = present(HOST,
                key(HOST, CLIENT_AUTH),
                key("localhost-2", CLIENT_AUTH));
        assertEku(presented, SSLUtilBase.CLIENT_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testIgnoresServerAuthCertificateWithDifferentSubject() throws Exception {
        X509Certificate presented = present(HOST,
                key(HOST, CLIENT_AUTH),
                KeystoreEntry.key("other-server", "other.example", SERVER_AUTH));
        assertEku(presented, SSLUtilBase.CLIENT_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testIgnoresTrustedCertificateEntries() throws Exception {
        // The only server-auth-capable certificate is a trusted (keyless) entry, so it cannot be used as a server key.
        // Selection must skip it and fall back to the configured key entry. If trusted entries were NOT skipped, the
        // keyless "ca" would be chosen and the resulting key manager would have no chain for it.
        X509Certificate presented = present(HOST,
                KeystoreEntry.trusted("ca", HOST, SERVER_AUTH),
                key(HOST, CLIENT_AUTH));
        assertEku(presented, SSLUtilBase.CLIENT_AUTH_EXTENDED_KEY_USAGE_OID);
    }
}
