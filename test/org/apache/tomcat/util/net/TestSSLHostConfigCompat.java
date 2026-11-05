/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.util.net;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.experimental.categories.Category;

import org.apache.tomcat.categories.SmokeTest;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.StoreType;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.Type;
import org.apache.tomcat.util.net.TesterSupport.ClientSSLSocketFactory;
import org.apache.tomcat.util.net.openssl.OpenSSLStatus;

/*
 * Tests compatibility of JSSE and OpenSSL settings.
 */
@Category(SmokeTest.class)
@RunWith(Parameterized.class)
public class TestSSLHostConfigCompat extends TomcatBaseTest {

    @Parameterized.Parameters(name = "{0}-{3}")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        for (StoreType storeType : new StoreType[] { StoreType.KEYSTORE, StoreType.PEM } ) {
            parameterSets.add(new Object[] {
                    "JSSE", Boolean.FALSE, "org.apache.tomcat.util.net.jsse.JSSEImplementation", storeType});
            parameterSets.add(new Object[] {
                    "OpenSSL", Boolean.TRUE, "org.apache.tomcat.util.net.openssl.OpenSSLImplementation", storeType});
            parameterSets.add(new Object[] {
                    "OpenSSL-FFM", Boolean.TRUE, "org.apache.tomcat.util.net.openssl.panama.OpenSSLImplementation", storeType});
        }

        return parameterSets;
    }

    @Parameter(0)
    public String connectorName;

    @Parameter(1)
    public boolean useOpenSSL;

    @Parameter(2)
    public String sslImplementationName;

    @Parameter(3)
    public StoreType storeType;


    @Test
    public void testHostEC() throws Exception {
        configureHostEC();
        doTest();
    }


    @Test
    public void testHostRSA() throws Exception {
        configureHostRSA();
        doTest();
    }


    @Test
    public void testHostRSAandECwithDefaultClient() throws Exception {
        configureHostRSA();
        configureHostEC();
        doTest();
    }


    /*
     * This test and the next just swap the order in which the server certs are
     * configured to ensure correct operation isn't dependent on order.
     */
    @Test
    public void testHostRSAandECwithRSAClient() throws Exception {
        configureHostRSA();
        configureHostEC();

        // Configure cipher suite that requires an RSA certificate on the server
        ClientSSLSocketFactory clientSSLSocketFactory = TesterSupport.configureClientSsl(true);
        clientSSLSocketFactory.setCipher(new String[] {"TLS_DHE_RSA_WITH_AES_256_GCM_SHA384"});

        doTest(false);
    }


    /*
     * This test and the previous just swap the order in which the server certs
     * are configured to ensure correct operation isn't dependent on order.
     */
    @Test
    public void testHostECandRSAwithRSAClient() throws Exception {
        configureHostEC();
        configureHostRSA();

        // Configure cipher suite that requires an RSA certificate on the server
        ClientSSLSocketFactory clientSSLSocketFactory = TesterSupport.configureClientSsl(true);
        clientSSLSocketFactory.setCipher(new String[] {"TLS_DHE_RSA_WITH_AES_256_GCM_SHA384"});

        doTest(false);
    }


    /*
     * This test and the next just swap the order in which the server certs are
     * configured to ensure correct operation isn't dependent on order.
     */
    @Test
    public void testHostRSAandECwithECClient() throws Exception {
        configureHostRSA();
        configureHostEC();

        // Configure cipher suite that requires an EC certificate on the server
        ClientSSLSocketFactory clientSSLSocketFactory = TesterSupport.configureClientSsl(true);
        clientSSLSocketFactory.setCipher(new String[] {"TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384"});

        doTest(false);
    }


    /*
     * This test and the previous just swap the order in which the server certs
     * are configured to ensure correct operation isn't dependent on order.
     */
    @Test
    public void testHostECandRSAwithECClient() throws Exception {
        configureHostEC();
        configureHostRSA();

        // Configure cipher suite that requires an EC certificate on the server
        ClientSSLSocketFactory clientSSLSocketFactory = TesterSupport.configureClientSsl(true);
        clientSSLSocketFactory.setCipher(new String[] {"TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384"});

        doTest(false);
    }


    @Test
    public void testHostRSAwithRSAClient() throws Exception {
        configureHostRSA();

        // Configure cipher suite that requires an RSA certificate on the server
        ClientSSLSocketFactory clientSSLSocketFactory = TesterSupport.configureClientSsl(true);
        clientSSLSocketFactory.setCipher(new String[] {"TLS_DHE_RSA_WITH_AES_256_GCM_SHA384"});

        doTest(false);
    }


    @Test(expected=javax.net.ssl.SSLHandshakeException.class)
    public void testHostRSAwithECClient() throws Exception {
        configureHostRSA();

        // Configure cipher suite that requires an EC certificate on the server
        ClientSSLSocketFactory clientSSLSocketFactory = TesterSupport.configureClientSsl(true);
        clientSSLSocketFactory.setCipher(new String[] {"TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384"});

        doTest(false);
    }


    @Test
    public void testHostRSAwithRSAandECClient() throws Exception {
        configureHostRSA();

        // Configure cipher suite that requires an EC certificate on the server
        ClientSSLSocketFactory clientSSLSocketFactory = TesterSupport.configureClientSsl(true);
        clientSSLSocketFactory.setCipher(new String[] {
                "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384"});

        doTest(false);
    }


    @Test(expected=javax.net.ssl.SSLHandshakeException.class)
    public void testHostECwithRSAClient() throws Exception {
        configureHostEC();

        // Configure cipher suite that requires an RSA certificate on the server
        ClientSSLSocketFactory clientSSLSocketFactory = TesterSupport.configureClientSsl(true);
        clientSSLSocketFactory.setCipher(new String[] {"TLS_DHE_RSA_WITH_AES_256_GCM_SHA384"});

        doTest(false);
    }


    @Test
    public void testHostECwithECClient() throws Exception {
        configureHostEC();

        // Configure cipher suite that requires an EC certificate on the server
        ClientSSLSocketFactory clientSSLSocketFactory = TesterSupport.configureClientSsl(true);
        clientSSLSocketFactory.setCipher(new String[] {"TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384"});

        doTest(false);
    }


    @Test
    public void testHostECwithRSAandECClient() throws Exception {
        configureHostEC();

        // Configure cipher suite that requires an RSA certificate on the server
        ClientSSLSocketFactory clientSSLSocketFactory = TesterSupport.configureClientSsl(true);
        clientSSLSocketFactory.setCipher(new String[] {
                "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
                "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384"});

        doTest(false);
    }


    /*
     * The certificate and key files are read using the ConfigFileLoader so, as well as file paths, URLs are accepted.
     * The OpenSSL implementation that uses Tomcat Native needs a file path so is not tested here.
     */
    @Test
    public void testHostRSAWithPemFileUrls() throws Exception {
        Assume.assumeTrue("Only applies to PEM files", storeType == StoreType.PEM);
        Assume.assumeFalse("Tomcat Native requires file paths",
                "org.apache.tomcat.util.net.openssl.OpenSSLImplementation".equals(sslImplementationName));

        SSLHostConfig sslHostConfig = getSSLHostConfig();
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(sslHostConfig, Type.RSA);
        certificate.setCertificateFile(getFileUrl(TesterSupport.LOCALHOST_RSA_CERT_PEM));
        certificate.setCertificateKeyFile(getFileUrl(TesterSupport.LOCALHOST_RSA_KEY_PEM));
        certificate.setCertificateKeyPassword(TesterSupport.JKS_PASS);
        sslHostConfig.addCertificate(certificate);

        doTest();
    }


    /*
     * The certificate chain file is only used if the certificate file (a PEM file or a PKCS#12 bundle) holds the
     * server certificate alone. If it already holds the certificate chain, the chain file is ignored.
     *
     * The chain file used in these tests holds the certificate of a different CA to the one in the certificate chain
     * so it is possible to tell which of the two the server sends after the server certificate.
     *
     * This applies to PEM files with all of the TLS implementations. Only the OpenSSL FFM implementation reads PKCS#12
     * bundles through the certificate file.
     */
    @Test
    public void testHostRSAPemLeafOnlyWithChainFile() throws Exception {
        configureHostRSAChainFileTest(new File(TesterSupport.LOCALHOST_RSA_CERT_PEM), false);
        // The chain file is used
        assertServerChain(TesterSupport.LOCALHOST_EC_CERT_PEM);
    }


    @Test
    public void testHostRSAPemWithChainAndChainFile() throws Exception {
        configureHostRSAChainFileTest(TesterSupport.createLocalhostRsaCertAndCaPem(), false);
        // The chain file is ignored
        assertServerChain(TesterSupport.CA_CERT_PEM);
    }


    @Test
    public void testHostRSAPkcs12LeafOnlyWithChainFile() throws Exception {
        configureHostRSAChainFileTest(TesterSupport.createLocalhostRsaPkcs12(false), true);
        // The chain file is used
        assertServerChain(TesterSupport.LOCALHOST_EC_CERT_PEM);
    }


    @Test
    public void testHostRSAPkcs12WithChainAndChainFile() throws Exception {
        configureHostRSAChainFileTest(TesterSupport.createLocalhostRsaPkcs12(true), true);
        // The chain file is ignored
        assertServerChain(TesterSupport.CA_CERT_PEM);
    }


    /*
     * The certificate file may also hold the private key and that key may be encrypted. That must not affect whether
     * the certificate file is found to hold the certificate chain. The certificate file is the certificate, the
     * certificate of a CA and the encrypted key.
     */
    @Test
    public void testHostRSAPemWithChainAndEncryptedKeyAndChainFile() throws Exception {
        doTestHostRSAPemWithChainAndEncryptedKeyAndChainFile(false);
    }


    @Test
    public void testHostRSAPemWithChainAndEncryptedKeyAndChainFileWithPasswordFile() throws Exception {
        doTestHostRSAPemWithChainAndEncryptedKeyAndChainFile(true);
    }


    private void doTestHostRSAPemWithChainAndEncryptedKeyAndChainFile(boolean passwordFile) throws Exception {
        Assume.assumeTrue("Only needs to be run once for each implementation", storeType == StoreType.PEM);

        SSLHostConfig sslHostConfig = getSSLHostConfig();
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(sslHostConfig, Type.RSA);
        certificate.setCertificateFile(
                TesterSupport.createLocalhostRsaCertCaAndEncryptedKeyPem(TesterSupport.JKS_PASS).getAbsolutePath());
        if (passwordFile) {
            certificate.setCertificateKeyPasswordFile(getPath(TesterSupport.JKS_PASS_FILE));
        } else {
            certificate.setCertificateKeyPassword(TesterSupport.JKS_PASS);
        }
        certificate.setCertificateChainFile(getPath(TesterSupport.LOCALHOST_EC_CERT_PEM));
        sslHostConfig.addCertificate(certificate);

        getTomcatInstance().start();

        // The chain file is ignored
        assertServerChain(TesterSupport.CA_CERT_PEM);
    }


    /*
     * A failure to read the server certificate or key is logged at ERROR with the failure to read the file logged at
     * DEBUG. A failure to read a chain file only needs to be logged at WARN. These are features of the OpenSSL FFM
     * implementation.
     */
    @Test
    public void testHostRSAPemMissingCertificateFile() throws Exception {
        assumeOpenSSLFFMAndPem();

        SSLHostConfigCertificate certificate = addHostRSAPem(getPath(TesterSupport.LOCALHOST_RSA_KEY_PEM));
        String missing = new File("test/org/apache/tomcat/util/net/missing-cert.pem").getAbsolutePath();
        certificate.setCertificateFile(missing);

        try (LogCapture loaderLog = new LogCapture(CERTIFICATE_LOADER_LOGGER, Level.FINE)) {
            Assert.assertThrows(LifecycleException.class, () -> getTomcatInstance().start());

            Assert.assertTrue("No ERROR for the certificate file", loaderLog.contains(Level.SEVERE, missing));
            Assert.assertTrue("No DEBUG for the failure to read the file",
                    loaderLog.contains(Level.FINE, missing));
        }
    }


    @Test
    public void testHostRSAPemMissingKeyFile() throws Exception {
        assumeOpenSSLFFMAndPem();

        String missing = new File("test/org/apache/tomcat/util/net/missing-key.pem").getAbsolutePath();
        addHostRSAPem(missing);

        try (LogCapture loaderLog = new LogCapture(CERTIFICATE_LOADER_LOGGER, Level.FINE)) {
            Assert.assertThrows(LifecycleException.class, () -> getTomcatInstance().start());

            Assert.assertTrue("No ERROR for the key file", loaderLog.contains(Level.SEVERE, missing));
            Assert.assertTrue("No DEBUG for the failure to read the file",
                    loaderLog.contains(Level.FINE, missing));
        }
    }


    @Test
    public void testHostRSAPemMissingChainFile() throws Exception {
        assumeOpenSSLFFMAndPem();

        SSLHostConfigCertificate certificate = addHostRSAPem(getPath(TesterSupport.LOCALHOST_RSA_KEY_PEM));
        certificate.setCertificateFile(getPath(TesterSupport.LOCALHOST_RSA_CERT_PEM));
        String missing = new File("test/org/apache/tomcat/util/net/missing-chain.pem").getAbsolutePath();
        certificate.setCertificateChainFile(missing);

        try (LogCapture loaderLog = new LogCapture(CERTIFICATE_LOADER_LOGGER, Level.FINE)) {
            getTomcatInstance().start();

            // The certificate is still used
            Assert.assertEquals(1, getServerCertificateCount());
            Assert.assertTrue("No WARN for the chain file", loaderLog.contains(Level.WARNING, missing));
            Assert.assertFalse("The chain file was logged at ERROR", loaderLog.contains(Level.SEVERE, missing));
        }
    }


    @Test
    public void testHostRSAPemUnreadableCaCertificateFile() throws Exception {
        assumeOpenSSLFFMAndPem();

        SSLHostConfigCertificate certificate = addHostRSAPem(getPath(TesterSupport.LOCALHOST_RSA_KEY_PEM));
        certificate.setCertificateFile(getPath(TesterSupport.LOCALHOST_RSA_CERT_PEM));

        // A file that exists but does not contain any CA certificates
        File caFile = File.createTempFile("tomcat-test-", ".pem");
        caFile.deleteOnExit();
        Files.writeString(caFile.toPath(), "This is not a certificate\n");
        getSSLHostConfig().setCaCertificateFile(caFile.getAbsolutePath());

        try (LogCapture contextLog = new LogCapture(CONTEXT_LOGGER, Level.INFO)) {
            getTomcatInstance().start();

            Assert.assertTrue("No ERROR for the CA certificate file", contextLog.contains(Level.SEVERE, null));
        }
    }


    private void assumeOpenSSLFFMAndPem() {
        Assume.assumeTrue("Only needs to be run once", storeType == StoreType.PEM);
        Assume.assumeTrue("Only applies to the OpenSSL FFM implementation",
                "org.apache.tomcat.util.net.openssl.panama.OpenSSLImplementation".equals(sslImplementationName));
    }


    private SSLHostConfigCertificate addHostRSAPem(String keyFile) {
        SSLHostConfig sslHostConfig = getSSLHostConfig();
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(sslHostConfig, Type.RSA);
        certificate.setCertificateFile(getPath(TesterSupport.LOCALHOST_RSA_CERT_PEM));
        certificate.setCertificateKeyFile(keyFile);
        certificate.setCertificateKeyPassword(TesterSupport.JKS_PASS);
        sslHostConfig.addCertificate(certificate);
        return certificate;
    }


    private static final String CERTIFICATE_LOADER_LOGGER =
            "org.apache.tomcat.util.net.openssl.panama.CertificateLoader";
    private static final String CONTEXT_LOGGER = "org.apache.tomcat.util.net.openssl.panama.OpenSSLContext";


    /*
     * Captures the log records of a logger. The logger is referenced so it is not garbage collected while the capture
     * is in use.
     */
    private static class LogCapture extends Handler implements AutoCloseable {

        private final Logger logger;
        private final Level originalLevel;
        private final List<LogRecord> records = new CopyOnWriteArrayList<>();

        LogCapture(String loggerName, Level level) {
            logger = Logger.getLogger(loggerName);
            originalLevel = logger.getLevel();
            logger.setLevel(level);
            setLevel(Level.ALL);
            logger.addHandler(this);
        }

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
            // NO-OP
        }

        @Override
        public void close() {
            logger.removeHandler(this);
            logger.setLevel(originalLevel);
        }

        /*
         * Is there a record at the given level that mentions the text (any text if null)?
         */
        boolean contains(Level level, String text) {
            for (LogRecord record : records) {
                if (record.getLevel().equals(level)) {
                    String message = record.getMessage();
                    Object[] parameters = record.getParameters();
                    if (text == null || message != null && message.contains(text)) {
                        return true;
                    }
                    if (parameters != null) {
                        for (Object parameter : parameters) {
                            if (parameter != null && parameter.toString().contains(text)) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }
    }


    private void configureHostRSAChainFileTest(File certificateFile, boolean pkcs12) throws Exception {
        Assume.assumeTrue("Only needs to be run once for each implementation", storeType == StoreType.PEM);
        if (pkcs12) {
            Assume.assumeTrue("Only the OpenSSL FFM implementation reads PKCS#12 bundles via the certificate file",
                    "org.apache.tomcat.util.net.openssl.panama.OpenSSLImplementation".equals(sslImplementationName));
        }

        SSLHostConfig sslHostConfig = getSSLHostConfig();
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(sslHostConfig, Type.RSA);
        certificate.setCertificateFile(certificateFile.getAbsolutePath());
        if (!pkcs12) {
            certificate.setCertificateKeyFile(getPath(TesterSupport.LOCALHOST_RSA_KEY_PEM));
        }
        certificate.setCertificateKeyPassword(TesterSupport.JKS_PASS);
        certificate.setCertificateChainFile(getPath(TesterSupport.LOCALHOST_EC_CERT_PEM));
        sslHostConfig.addCertificate(certificate);

        getTomcatInstance().start();
    }


    /*
     * Returns the number of certificates the server sends.
     */
    private int getServerCertificateCount() throws Exception {
        return getServerCertificates().length;
    }


    /*
     * Checks the server sends the server certificate followed by the certificate in the given file and nothing else.
     */
    private void assertServerChain(String expectedChainCertificate) throws Exception {
        X509Certificate expected;
        try (InputStream is = new FileInputStream(expectedChainCertificate)) {
            expected = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(is);
        }

        Certificate[] peerCertificates = getServerCertificates();

        Assert.assertEquals(2, peerCertificates.length);
        Assert.assertEquals(expected, peerCertificates[1]);
    }


    /*
     * Returns the certificates the server sends.
     */
    private Certificate[] getServerCertificates() throws Exception {
        // Trust everything, the test is about what the server sends
        SSLContext clientContext = SSLContext.getInstance("TLS");
        clientContext.init(null, new TrustManager[] { new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                // NO-OP
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                // NO-OP
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        } }, null);

        try (SSLSocket socket = (SSLSocket) clientContext.getSocketFactory().createSocket("localhost", getPort())) {
            socket.setSoTimeout(5000);
            socket.startHandshake();
            return socket.getSession().getPeerCertificates();
        }
    }


    private void configureHostRSA() {
        SSLHostConfig sslHostConfig = getSSLHostConfig();
        switch (storeType) {
            case KEYSTORE: {
                SSLHostConfigCertificate sslHostConfigCertificateRsa = new SSLHostConfigCertificate(sslHostConfig, Type.RSA);
                sslHostConfigCertificateRsa.setCertificateKeystoreFile(getPath(TesterSupport.LOCALHOST_RSA_JKS));
                sslHostConfigCertificateRsa.setCertificateKeystorePassword(TesterSupport.JKS_PASS);
                sslHostConfig.addCertificate(sslHostConfigCertificateRsa);
                break;
            }
            case PEM: {
                SSLHostConfigCertificate sslHostConfigCertificateRsa = new SSLHostConfigCertificate(sslHostConfig, Type.RSA);
                sslHostConfigCertificateRsa.setCertificateFile(getPath(TesterSupport.LOCALHOST_RSA_CERT_PEM));
                sslHostConfigCertificateRsa.setCertificateKeyFile(getPath(TesterSupport.LOCALHOST_RSA_KEY_PEM));
                sslHostConfigCertificateRsa.setCertificateKeyPassword(TesterSupport.JKS_PASS);
                sslHostConfig.addCertificate(sslHostConfigCertificateRsa);
                break;
            }
        }
    }


    private void configureHostEC() {
        SSLHostConfig sslHostConfig = getSSLHostConfig();
        switch (storeType) {
            case KEYSTORE: {
                SSLHostConfigCertificate sslHostConfigCertificateEc = new SSLHostConfigCertificate(sslHostConfig, Type.EC);
                sslHostConfigCertificateEc.setCertificateKeystoreFile(getPath(TesterSupport.LOCALHOST_EC_JKS));
                sslHostConfigCertificateEc.setCertificateKeystorePassword(TesterSupport.JKS_PASS);
                sslHostConfig.addCertificate(sslHostConfigCertificateEc);
                break;
            }
            case PEM: {
                SSLHostConfigCertificate sslHostConfigCertificateEc = new SSLHostConfigCertificate(sslHostConfig, Type.EC);
                sslHostConfigCertificateEc.setCertificateFile(getPath(TesterSupport.LOCALHOST_EC_CERT_PEM));
                sslHostConfigCertificateEc.setCertificateKeyFile(getPath(TesterSupport.LOCALHOST_EC_KEY_PEM));
                sslHostConfig.addCertificate(sslHostConfigCertificateEc);
                break;
            }
        }
    }


    private void doTest() throws Exception {
        // Use the default client TLS config
        doTest(true);
    }


    private void doTest(boolean configureClientSsl) throws Exception {
        if (configureClientSsl) {
            TesterSupport.configureClientSsl(true);
        }

        Tomcat tomcat = getTomcatInstance();
        tomcat.start();

        Assume.assumeFalse("BoringSSL removes support for many ciphers",
                TesterSupport.isOpenSSLVariant(sslImplementationName, OpenSSLStatus.Name.BORINGSSL));

        // Check a request can be made
        ByteChunk res = getUrl("https://localhost:" + getPort() + "/");
        Assert.assertEquals("OK", res.toString());
    }


    @Override
    public void setUp() throws Exception {
        super.setUp();

        Tomcat tomcat = getTomcatInstance();
        Connector connector = tomcat.getConnector();

        connector.setPort(0);
        connector.setScheme("https");
        connector.setSecure(true);
        Assert.assertTrue(connector.setProperty("SSLEnabled", "true"));
        SSLHostConfig sslHostConfig = new SSLHostConfig();
        sslHostConfig.setProtocols("TLSv1.2");
        connector.addSslHostConfig(sslHostConfig);

        TesterSupport.configureSSLImplementation(tomcat, sslImplementationName, useOpenSSL);

        // Simple webapp
        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "TesterServlet", new TesterServlet());
        ctxt.addServletMapping("/*", "TesterServlet");
    }


    private SSLHostConfig getSSLHostConfig() {
        Tomcat tomcat = getTomcatInstance();
        Connector connector = tomcat.getConnector();
        return connector.findSslHostConfigs()[0];
    }


    private static String getFileUrl(String relativePath) {
        return new File(relativePath).toURI().toString();
    }


    private static String getPath(String relativePath) {
        File f = new File(relativePath);
        return f.getAbsolutePath();
    }
}
