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
import java.io.Serial;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreSpi;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import javax.net.ssl.X509KeyManager;

import org.junit.Assert;
import org.junit.Before;
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
 * Shared fixtures for the {@link SSLUtilBase#getKeyManagers()} certificate-selection tests. The two behaviors are
 * split across the concrete subclasses: {@link TestSSLUtilBaseAutomaticCertificateSelection} covers Extended Key
 * Usage based selection when no alias is configured, and {@link TestSSLUtilBaseConfiguredAliasSelection} covers a
 * configured alias, including the SunMSCAPI Windows-MY numbered-duplicate resolution.
 * <p>
 * {@link #orderedKeystore(String, KeystoreEntry...)} returns a key store with a controlled alias enumeration order
 * that presents as a SunMSCAPI Windows-MY store; {@link #nonSunMSCAPIKeystore(KeystoreEntry...)} presents as a plain
 * JKS store, so that the SunMSCAPI-only duplicate search is skipped.
 */
public abstract class SSLUtilBaseCertificateSelectionTestBase extends TomcatBaseTest {

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

    protected static final String HOST = "localhost";

    protected static final String CLIENT_AUTH_EXTENDED_KEY_USAGE_OID = "1.3.6.1.5.5.7.3.2";

    @SuppressWarnings("unused")
    protected static final CertificateExtensionsCustomizer SERVER_AUTH = (keyPair, builder) -> builder
            .addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
    @SuppressWarnings("unused")
    protected static final CertificateExtensionsCustomizer CLIENT_AUTH = (keyPair, builder) -> builder
            .addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));
    @SuppressWarnings("unused")
    protected static final CertificateExtensionsCustomizer ANY_AUTH = (keyPair, builder) -> builder
            .addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.anyExtendedKeyUsage));


    protected static KeystoreEntry key(String alias, CertificateExtensionsCustomizer eku) {
        return KeystoreEntry.key(alias, HOST, eku);
    }


    protected X509Certificate present(String alias, KeystoreEntry... entries) throws Exception {
        return present(alias, orderedKeystore(null, entries));
    }


    protected X509Certificate present(String alias, KeyStore keystore) throws Exception {
        X509KeyManager keyManager = (X509KeyManager) sslUtil(alias, keystore).getKeyManagers()[0];
        X509Certificate[] chain = keyManager.getCertificateChain(keyManager.chooseServerAlias("RSA", null, null));
        Assert.assertNotNull("Key manager returned no certificate chain", chain);
        return chain[0];
    }


    protected SSLUtil sslUtil(String alias, KeyStore keystore) throws Exception {

        SSLHostConfig hostConfig = new SSLHostConfig();
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(hostConfig, Type.UNDEFINED);
        certificate.setCertificateKeystore(keystore);
        certificate.setCertificateKeystoreType("JKS");
        certificate.setCertificateKeystorePassword(TesterSupport.JKS_PASS);
        if (alias != null) {
            certificate.setCertificateKeyAlias(alias);
        }

        return sslUtil(certificate);
    }


    protected SSLUtil sslUtil(SSLHostConfigCertificate certificate) throws Exception {
        SSLUtil util = SSLImplementation.getInstance(sslImplementationName).getSSLUtil(certificate);
        SSLHostConfig hostConfig = certificate.getSSLHostConfig();
        hostConfig.setEnabledProtocols(util.getEnabledProtocols());
        hostConfig.setEnabledCiphers(util.getEnabledCiphers());
        return util;
    }


    /*
     * Builds and registers an SSLContext for the given configured alias and key store, then returns the certificates
     * that SSLHostConfig.certificatesExpiringBefore() - the input to the TLS certificate reload listener - would flag.
     * The cut-off date is far enough in the future that every certificate qualifies, so the result is exactly the set
     * of certificates the listener monitors for reload.
     */
    protected Set<X509Certificate> certificatesMonitoredForReload(String alias, KeyStore keystore) throws Exception {
        SSLHostConfigCertificate certificate = registeredCertificate(alias, keystore);
        SSLContext context = sslUtil(certificate).createSSLContext(List.of());
        try {
            certificate.setSslContext(context);
            Date farFuture = new Date(System.currentTimeMillis() + 3650L * 24 * 60 * 60 * 1000);
            return certificate.getSSLHostConfig().certificatesExpiringBefore(farFuture);
        } finally {
            context.destroy();
        }
    }


    /*
     * Builds and registers an SSLContext for the given configured alias and key store, then returns the alias the
     * context reports as actually served - which is what the certificate logging and reload paths resolve.
     */
    protected String servedAlias(String alias, KeyStore keystore) throws Exception {
        SSLHostConfigCertificate certificate = registeredCertificate(alias, keystore);
        SSLContext context = sslUtil(certificate).createSSLContext(List.of());
        try {
            certificate.setSslContext(context);
            return context.getServedAlias(certificate);
        } finally {
            context.destroy();
        }
    }


    private static SSLHostConfigCertificate registeredCertificate(String alias, KeyStore keystore) {
        SSLHostConfig hostConfig = new SSLHostConfig();
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(hostConfig, Type.UNDEFINED);
        certificate.setCertificateKeystore(keystore);
        certificate.setCertificateKeystoreType("JKS");
        certificate.setCertificateKeystorePassword(TesterSupport.JKS_PASS);
        if (alias != null) {
            certificate.setCertificateKeyAlias(alias);
        }
        hostConfig.addCertificate(certificate);
        return certificate;
    }


    protected static KeyStore orderedKeystore(String malformedAlias, KeystoreEntry... entries) throws Exception {
        return orderedKeystore(sunMSCAPIProvider(), "Windows-MY", true, malformedAlias, entries);
    }


    /*
     * As orderedKeystore(String, KeystoreEntry...) but the private keys report a null format, like a non-exportable
     * SunMSCAPI key. This routes SSLUtilBase.getKeyManagers() through its production path that keeps the original key
     * store (ksUsed == ks) and wraps each key manager, rather than the in-memory-copy branch the exportable JKS-backed
     * mock otherwise takes.
     */
    protected static KeyStore nonExportableKeystore(KeystoreEntry... entries) throws Exception {
        return orderedKeystore(sunMSCAPIProvider(), "Windows-MY", false, null, entries);
    }


    protected static KeyStore nonSunMSCAPIKeystore(KeystoreEntry... entries) throws Exception {
        return orderedKeystore(new Provider("SUN", "1.0", "test") {
            @Serial
            private static final long serialVersionUID = 4434321038151233913L;
        }, "JKS", true, null, entries);
    }


    private static Provider sunMSCAPIProvider() {
        return new Provider("SunMSCAPI", "1.0", "test") {
            @Serial
            private static final long serialVersionUID = -4141665633237722962L;
        };
    }


    protected static KeyStore orderedKeystore(Provider provider, String type, boolean exportableKeys,
            String malformedAlias, KeystoreEntry... entries) throws Exception {
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
        EasyMock.expect(spi.engineGetKey(EasyMock.anyString(), EasyMock.anyObject(char[].class))).andStubAnswer(() -> {
            Key key = delegate.getKey(EasyMock.getCurrentArgument(0), EasyMock.getCurrentArgument(1));
            return exportableKeys ? key : nonExportableView(key);
        });
        EasyMock.replay(spi);
        // A real JKS backs the mock. With exportableKeys the keys report PKCS#8, so getKeyManagers() takes its
        // in-memory-copy branch; with exportableKeys == false they report a null format (see nonExportableView), so it
        // takes SunMSCAPI's non-exportable-key path instead. The provider name and store type are overridden only so
        // isSunMSCAPIWindowsMY() can be exercised off Windows.
        KeyStore result = new KeyStore(spi, provider, type) { };
        result.load(null, null);
        return result;
    }


    /*
     * A view of a private key that reports a null format, as a non-exportable SunMSCAPI key does, while staying usable
     * by the key manager. getEncoded() returns null for the same reason; these selection tests never sign, so the key
     * material itself is never needed.
     */
    private static Key nonExportableView(Key key) {
        if (!(key instanceof PrivateKey privateKey)) {
            return key;
        }
        return new PrivateKey() {
            @Serial
            private static final long serialVersionUID = 1L;

            @Override
            public String getAlgorithm() {
                return privateKey.getAlgorithm();
            }

            @Override
            public String getFormat() {
                return null;
            }

            @Override
            public byte[] getEncoded() {
                return null;
            }
        };
    }


    protected static void assertEku(X509Certificate certificate, String oid) throws Exception {
        List<String> extendedKeyUsage = certificate.getExtendedKeyUsage();
        Assert.assertNotNull("Presented certificate has no Extended Key Usage", extendedKeyUsage);
        Assert.assertTrue("Presented certificate must be valid for Extended Key Usage " + oid,
                extendedKeyUsage.contains(oid));
    }
}
