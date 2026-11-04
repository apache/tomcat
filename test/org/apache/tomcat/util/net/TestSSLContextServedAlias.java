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

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.LogManager;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.KeyManagerFactorySpi;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.X509KeyManager;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.apache.tomcat.unittest.TesterLogValidationFilter;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.Type;

/**
 * Tests how {@link SSLContext#getServedAlias(SSLHostConfigCertificate)} resolves the served alias when Tomcat did not
 * wrap the key managers, so that the alias is chosen by the key manager itself rather than pinned by
 * {@link org.apache.tomcat.util.net.jsse.JSSEKeyManager}, and how the served alias is reported when an endpoint logs
 * the certificate. The served alias selects the certificate that OpenSSL loads and that is logged, reported by the
 * Manager and monitored for expiry.
 */
@RunWith(Parameterized.class)
public class TestSSLContextServedAlias extends SSLUtilBaseCertificateSelectionTestBase {

    private static final String FIPS_PROVIDER = "TEST_FIPS_KEY_MANAGER";
    private static final String FIPS_ALGORITHM = "TestFipsSunX509";


    @After
    public void removeFipsProvider() {
        Security.removeProvider(FIPS_PROVIDER);
    }


    /*
     * A FIPS key manager factory is used without wrapping, so its key manager picks a certificate by itself when asked.
     * When that key manager holds the configured alias, the configured alias must still be the one that OpenSSL loads
     * and that is logged and monitored, as it was before served aliases were resolved through the key manager. The
     * configured alias is the one SunX509 does not pick, so a key manager choice that takes precedence fails the test.
     */
    @Test
    public void testFipsKeyManagerServesConfiguredAlias() throws Exception {
        KeyStore keystore = KeyStore.getInstance("JKS");
        try (InputStream is = new FileInputStream(
                keystoreFile(key("first", CertType.SERVER_AUTH), key("second", CertType.SERVER_AUTH)))) {
            keystore.load(is, TesterSupport.JKS_PASS.toCharArray());
        }
        KeyManagerFactory sunX509 = KeyManagerFactory.getInstance("SunX509");
        sunX509.init(keystore, TesterSupport.JKS_PASS.toCharArray());
        String keyManagerChoice = ((X509KeyManager) sunX509.getKeyManagers()[0]).chooseServerAlias("RSA", null, null);
        String configuredAlias = "first".equals(keyManagerChoice) ? "second" : "first";
        Security.addProvider(new FipsKeyManagerFactoryProvider());

        SSLHostConfigCertificate certificate = certificate(keystore, configuredAlias, FIPS_ALGORITHM);
        SSLContext context = sslUtil(certificate).createSSLContext(Collections.emptyList());
        try {
            String servedAlias = context.getServedAlias(certificate);
            Assert.assertEquals(configuredAlias, servedAlias);
            Assert.assertEquals(keystore.getCertificate(configuredAlias), context.getCertificateChain(servedAlias)[0]);
        } finally {
            context.destroy();
        }
    }


    /*
     * The NewSunX509 key manager (algorithm PKIX) returns synthetic aliases of the form uid.index.alias and does not
     * recognize the plain configured alias. The alias that key manager chooses must then be used, so that the
     * configured certificate is still found rather than none at all.
     */
    @Test
    public void testSyntheticAliasResolvesConfiguredCertificate() throws Exception {
        KeyStore keystore = KeyStore.getInstance("JKS");
        try (InputStream is = new FileInputStream(
                keystoreFile(key(HOST, CertType.SERVER_AUTH), key("other", CertType.SERVER_AUTH)))) {
            keystore.load(is, TesterSupport.JKS_PASS.toCharArray());
        }

        SSLHostConfigCertificate certificate = certificate(keystore, HOST, "PKIX");
        SSLContext context = sslUtil(certificate).createSSLContext(Collections.emptyList());
        try {
            X509Certificate[] chain = context.getCertificateChain(context.getServedAlias(certificate));
            Assert.assertNotNull("No certificate chain for the served alias", chain);
            Assert.assertEquals(keystore.getCertificate(HOST), chain[0]);
        } finally {
            context.destroy();
        }
    }


    /*
     * A configured alias replaced by a SunMSCAPI Windows-MY numbered duplicate is reported at INFO after the
     * certificate summary, which names the configured alias, so that it is not mistaken for the alias in use.
     */
    @Test
    public void testNumberedDuplicateIsReportedWhenLogged() throws Exception {
        KeyStore keystore = wrappedExportableKeystore(key(HOST, CertType.CLIENT_AUTH),
                key(HOST + " (1)", CertType.SERVER_AUTH));
        SSLHostConfigCertificate certificate = certificate(keystore, HOST, KeyManagerFactory.getDefaultAlgorithm());
        Assert.assertEquals(1, infoLinesWhenLogged(certificate, "[" + HOST + " (1)]"));
    }


    /*
     * A key manager that names entries itself, as NewSunX509 does with synthetic aliases of the form uid.index.alias,
     * did not replace the configured alias, so no INFO line may report its synthetic alias as the one in use.
     */
    @Test
    public void testSyntheticAliasIsNotReportedWhenLogged() throws Exception {
        KeyStore keystore = KeyStore.getInstance("JKS");
        try (InputStream is = new FileInputStream(
                keystoreFile(key(HOST, CertType.SERVER_AUTH), key("other", CertType.SERVER_AUTH)))) {
            keystore.load(is, TesterSupport.JKS_PASS.toCharArray());
        }
        SSLHostConfigCertificate certificate = certificate(keystore, HOST, "PKIX");
        Assert.assertEquals(0, infoLinesWhenLogged(certificate, "." + HOST + "]"));
    }


    /*
     * Logs the certificate as an endpoint does when it initializes TLS and returns the number of INFO lines that
     * contain the given text.
     */
    private int infoLinesWhenLogged(SSLHostConfigCertificate certificate, String text) throws Exception {
        SSLContext context = sslUtil(certificate).createSSLContext(Collections.emptyList());
        try {
            certificate.setSslContext(context);
            NioEndpoint endpoint = new NioEndpoint();
            // Creating the endpoint initialized NioEndpoint, so its certificate logger now exists
            String loggerName = NioEndpoint.class.getName() + ".certificate";
            TesterLogValidationFilter filter = TesterLogValidationFilter.add(Level.INFO, text, null, loggerName);
            try {
                endpoint.logCertificate(certificate);
            } finally {
                LogManager.getLogManager().getLogger(loggerName).setFilter(null);
            }
            return filter.getMessageCount();
        } finally {
            context.destroy();
        }
    }


    private static SSLHostConfigCertificate certificate(KeyStore keystore, String alias, String keyManagerAlgorithm) {
        SSLHostConfig hostConfig = new SSLHostConfig();
        hostConfig.setKeyManagerAlgorithm(keyManagerAlgorithm);
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(hostConfig, Type.UNDEFINED);
        certificate.setCertificateKeystore(keystore);
        certificate.setCertificateKeystoreType("JKS");
        certificate.setCertificateKeystorePassword(TesterSupport.JKS_PASS);
        certificate.setCertificateKeyAlias(alias);
        hostConfig.addCertificate(certificate);
        return certificate;
    }


    /*
     * Presents as a FIPS module (its provider information contains "FIPS"), so SSLUtilBase passes it the key store
     * unmodified and uses its key managers unwrapped, while SunX509 does the actual key management.
     */
    private static final class FipsKeyManagerFactoryProvider extends Provider {
        private static final long serialVersionUID = 1L;

        FipsKeyManagerFactoryProvider() {
            super(FIPS_PROVIDER, 1.0, "Test FIPS key manager factory");
            put("KeyManagerFactory." + FIPS_ALGORITHM, FipsKeyManagerFactorySpi.class.getName());
        }
    }


    public static final class FipsKeyManagerFactorySpi extends KeyManagerFactorySpi {

        private final KeyManagerFactory delegate;

        public FipsKeyManagerFactorySpi() throws NoSuchAlgorithmException {
            delegate = KeyManagerFactory.getInstance("SunX509");
        }

        @Override
        protected void engineInit(KeyStore ks, char[] password)
                throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
            delegate.init(ks, password);
        }

        @Override
        protected void engineInit(ManagerFactoryParameters spec) throws InvalidAlgorithmParameterException {
            delegate.init(spec);
        }

        @Override
        protected KeyManager[] engineGetKeyManagers() {
            return delegate.getKeyManagers();
        }
    }
}
