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
import java.security.KeyStoreSpi;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import javax.net.ssl.X509KeyManager;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.Type;
import org.apache.tomcat.util.net.TesterKeystoreGenerator.CertificateExtensionsCustomizer;
import org.apache.tomcat.util.net.TesterKeystoreGenerator.KeystoreEntry;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.easymock.EasyMock;

/**
 * Tests that, when no alias is configured, a key entry whose certificate is valid for TLS server authentication is
 * auto-selected and entries that are not valid (by Extended Key Usage) are ignored. A configured alias is always used
 * unchanged, regardless of its Extended Key Usage.
 */
@RunWith(Parameterized.class)
public class TestSSLUtilBaseKeyUsageSelection extends TomcatBaseTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> parameters() {
        return List.of(new Object[] { "JSSE", Boolean.FALSE,
                "org.apache.tomcat.util.net.jsse.JSSEImplementation" },
                new Object[] { "OpenSSL", Boolean.TRUE,
                        "org.apache.tomcat.util.net.openssl.OpenSSLImplementation" },
                new Object[] { "OpenSSL-FFM", Boolean.TRUE,
                        "org.apache.tomcat.util.net.openssl.panama.OpenSSLImplementation" });
    }

    @Parameter(0)
    public String connectorName;

    @Parameter(1)
    public boolean useOpenSSL;

    @Parameter(2)
    public String sslImplementationName;


    @Before
    public void configureSSLImplementation() throws Exception {
        TesterSupport.configureSSLImplementation(getTomcatInstance(), sslImplementationName, useOpenSSL);
        getTomcatInstance().getServer().init();
    }

    private static final String HOST = "localhost";

    private static final String CLIENT_AUTH_EXTENDED_KEY_USAGE_OID = "1.3.6.1.5.5.7.3.2";

    private static final CertificateExtensionsCustomizer SERVER_AUTH = (keyPair, builder) -> builder
            .addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
    private static final CertificateExtensionsCustomizer CLIENT_AUTH = (keyPair, builder) -> builder
            .addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));
    private static final CertificateExtensionsCustomizer ANY_AUTH = (keyPair, builder) -> builder
            .addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.anyExtendedKeyUsage));


    private static KeystoreEntry key(String alias, CertificateExtensionsCustomizer eku) {
        return KeystoreEntry.key(alias, HOST, eku);
    }


    private X509Certificate present(String alias, KeystoreEntry... entries) throws Exception {
        return present(alias, orderedKeystore(null, entries));
    }


    private X509Certificate present(String alias, KeyStore keystore) throws Exception {
        X509KeyManager keyManager = (X509KeyManager) sslUtil(alias, keystore).getKeyManagers()[0];
        X509Certificate[] chain = keyManager.getCertificateChain(keyManager.chooseServerAlias("RSA", null, null));
        Assert.assertNotNull("Key manager returned no certificate chain", chain);
        return chain[0];
    }


    private SSLUtil sslUtil(String alias, KeyStore keystore) throws Exception {

        SSLHostConfig hostConfig = new SSLHostConfig();
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(hostConfig, Type.UNDEFINED);
        certificate.setCertificateKeystore(keystore);
        certificate.setCertificateKeystorePassword(TesterSupport.JKS_PASS);
        if (alias != null) {
            certificate.setCertificateKeyAlias(alias);
        }

        return sslUtil(certificate);
    }


    private SSLUtil sslUtil(SSLHostConfigCertificate certificate) throws Exception {
        SSLUtil util = SSLImplementation.getInstance(sslImplementationName).getSSLUtil(certificate);
        SSLHostConfig hostConfig = certificate.getSSLHostConfig();
        hostConfig.setEnabledProtocols(util.getEnabledProtocols());
        hostConfig.setEnabledCiphers(util.getEnabledCiphers());
        return util;
    }


    private static KeyStore orderedKeystore(String malformedAlias, KeystoreEntry... entries) throws Exception {
        File file = TesterKeystoreGenerator.generateKeystore(List.of(entries));
        KeyStore delegate = KeyStore.getInstance(file, TesterSupport.JKS_PASS.toCharArray());
        List<String> aliases = Stream.of(entries).map(KeystoreEntry::alias).toList();

        // JKS type of keystore enumeration order is unspecified.
        // Control that boundary while keeping real keys, certificate chains, and key managers.
        // Malformed EKU must be injected here because a JKS parser can reject it while loading.
        X509Certificate malformed = EasyMock.createMock(X509Certificate.class);
        EasyMock.expect(malformed.getExtendedKeyUsage()).andStubThrow(new CertificateParsingException("Invalid EKU"));
        EasyMock.replay(malformed);
        KeyStoreSpi spi = EasyMock.createMock(KeyStoreSpi.class);
        spi.engineLoad(null, null);
        EasyMock.expectLastCall().once();
        EasyMock.expect(spi.engineAliases()).andStubAnswer(() -> Collections.enumeration(aliases));
        EasyMock.expect(spi.engineIsKeyEntry(EasyMock.anyString()))
                .andStubAnswer(() -> delegate.isKeyEntry(EasyMock.getCurrentArgument(0)));
        EasyMock.expect(spi.engineGetCertificate(EasyMock.anyString())).andStubAnswer(() -> {
            String alias = EasyMock.getCurrentArgument(0);
            return alias.equals(malformedAlias) ? malformed : delegate.getCertificate(alias);
        });
        EasyMock.expect(spi.engineGetCertificateChain(EasyMock.anyString()))
                .andStubAnswer(() -> delegate.getCertificateChain(EasyMock.getCurrentArgument(0)));
        EasyMock.expect(spi.engineGetKey(EasyMock.anyString(), EasyMock.anyObject(char[].class)))
                .andStubAnswer(() -> delegate.getKey(EasyMock.getCurrentArgument(0), EasyMock.getCurrentArgument(1)));
        EasyMock.replay(spi);
        KeyStore result = new KeyStore(spi, delegate.getProvider(), "JKS") { };
        result.load(null, null);
        return result;
    }


    private static void assertEku(X509Certificate certificate, String oid) throws Exception {
        List<String> extendedKeyUsage = certificate.getExtendedKeyUsage();
        Assert.assertNotNull("Presented certificate has no Extended Key Usage", extendedKeyUsage);
        Assert.assertTrue("Presented certificate must be valid for Extended Key Usage " + oid,
                extendedKeyUsage.contains(oid));
    }


    /*
     * A configured alias must be honored exactly, even when its certificate is not valid for server authentication and
     * a server-auth certificate sharing the Subject is also present. Auto-selection must never override the user's
     * choice (see the uses of SSLHostConfigCertificate.getCertificateKeyAlias() across the connectors).
     */
    @Test
    public void testConfiguredAliasIsUsedUnchangedWhenNotServerAuth() throws Exception {
        X509Certificate presented = present(HOST,
                key(HOST, CLIENT_AUTH),
                key("localhost-server", SERVER_AUTH));
        assertEku(presented, CLIENT_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testConfiguredAliasIsUsedWhenServerAuth() throws Exception {
        X509Certificate presented = present(HOST,
                key(HOST, SERVER_AUTH),
                key("localhost-client", CLIENT_AUTH));
        assertEku(presented, Constants.SERVER_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testAutoSelectPrefersServerAuthOverClientAuth() throws Exception {
        X509Certificate presented = present(null,
                key("first", CLIENT_AUTH),
                key("second", SERVER_AUTH));
        assertEku(presented, Constants.SERVER_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testAutoSelectAcceptsAnyExtendedKeyUsage() throws Exception {
        X509Certificate presented = present(null,
                key("first", CLIENT_AUTH),
                key("any", ANY_AUTH));
        assertEku(presented, Constants.ANY_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testAutoSelectAcceptsCertificateWithoutExtendedKeyUsage() throws Exception {
        X509Certificate presented = present(null,
                key("first", CLIENT_AUTH),
                key("none", null));
        Assert.assertNull("A certificate with no Extended Key Usage is unrestricted and must be selectable",
                presented.getExtendedKeyUsage());
    }


    /*
     * Trusted (keyless) certificate entries are not key entries, so they can never be selected as the server key even
     * when they are the only server-auth-capable certificate.
     */
    @Test
    public void testAutoSelectSkipsTrustedEntries() {
        Assert.assertThrows(IOException.class, () -> present(null,
                KeystoreEntry.trusted("ca", HOST, SERVER_AUTH),
                key("key", CLIENT_AUTH)));
    }


    /*
     * No unsuitable key entry may be selected when all certificates exclude server authentication.
     */
    @Test
    public void testAutoSelectFailsWhenNoneServerAuth() {
        Assert.assertThrows(IOException.class, () -> present(null,
                key("first", CLIENT_AUTH),
                key("second", CLIENT_AUTH)));
    }


    @Test
    public void testAutoSelectSkipsMalformedExtendedKeyUsage() throws Exception {
        KeyStore keystore = orderedKeystore("malformed", key("malformed", CLIENT_AUTH), key("server", SERVER_AUTH));
        assertEku(present(null, keystore), Constants.SERVER_AUTH_EXTENDED_KEY_USAGE_OID);
    }


    @Test
    public void testConfiguredAliasWithMalformedExtendedKeyUsageIsUsed() throws Exception {
        KeyStore keystore = orderedKeystore("malformed", key("malformed", CLIENT_AUTH), key("server", SERVER_AUTH));
        Assert.assertEquals(keystore.getCertificateChain("malformed")[0], present("malformed", keystore));
    }


    @Test
    public void testAutoSelectFailsWhenOnlyMalformedExtendedKeyUsage() throws Exception {
        KeyStore keystore = orderedKeystore("malformed", key("malformed", CLIENT_AUTH));
        Assert.assertThrows(IOException.class, () -> present(null, keystore));
    }


    @Test
    public void testAutoSelectUsesFirstEligibleKey() throws Exception {
        KeyStore keystore = orderedKeystore(null, key("client", CLIENT_AUTH), key("first", ANY_AUTH),
                key("second", SERVER_AUTH));
        Assert.assertEquals(keystore.getCertificate("first"), present(null, keystore));
    }


    @Test
    public void testConfiguredMissingAliasFails() {
        Assert.assertThrows(IOException.class, () -> present("missing", key("server", SERVER_AUTH)));
    }


    @Test
    public void testConfiguredTrustedAliasFails() {
        Assert.assertThrows(IOException.class, () -> present("trusted", KeystoreEntry.trusted("trusted", HOST, SERVER_AUTH),
                key("server", SERVER_AUTH)));
    }


    @Test
    public void testAutoSelectDoesNotSkipKeyReadFailure() throws Exception {
        KeyStore keystore = orderedKeystore(null, key("first", SERVER_AUTH), key("second", SERVER_AUTH));
        SSLHostConfig hostConfig = new SSLHostConfig();
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(hostConfig, Type.UNDEFINED);
        certificate.setCertificateKeystore(keystore);
        certificate.setCertificateKeyPassword("incorrect");
        SSLUtil util = SSLImplementation.getInstance(sslImplementationName).getSSLUtil(certificate);
        Assert.assertThrows(UnrecoverableKeyException.class, util::getKeyManagers);
    }


    @Test
    public void testSSLContextUsesSelectedCertificate() throws Exception {
        KeyStore keystore = orderedKeystore(null, key("client", CLIENT_AUTH), key("server", SERVER_AUTH));
        SSLContext context = sslUtil(null, keystore).createSSLContext(List.of());
        try {
            Assert.assertEquals(keystore.getCertificate("server"), context.getCertificateChain("server")[0]);
        } finally {
            context.destroy();
        }
    }


    @Test
    public void testSSLContextLoadsSelectedCertificateFromKeystoreFile() throws Exception {
        File file = TesterKeystoreGenerator.generateKeystore(
                List.of(key(SSLUtilBase.DEFAULT_KEY_ALIAS, CLIENT_AUTH), key("server", SERVER_AUTH)));
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
