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

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.CRLReason;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateRevokedException;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.x500.X500Principal;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.net.Constants;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.TesterSupport;
import org.apache.tomcat.util.net.openssl.OpenSSLImplementation;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;


@RunWith(Parameterized.class)
public class TestOcspIntegration extends TomcatBaseTest {
    private static final String CA_CERTIFICATE_PATH = "ca-cert.pem";
    private static final String SERVER_CERTIFICATE_PATH = "server-cert.pem";
    private static final String SERVER_CERTIFICATE_KEY_PATH = "server-key.pem";
    private static final String TRUSTSTORE_PATH = "trustStore.p12";
    private static final String TRUSTSTORE_PASS = "trust-password";
    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String OCSP_SERVER_CERT_GOOD_RESPONSE = "ocsp-good.der";
    private static final String OCSP_SERVER_CERT_REVOKED_RESPONSE = "ocsp-revoked.der";
    private static final String CLIENT_KEYSTORE_PATH = "client-keystore.p12";
    private static final String CLIENT_KEYSTORE_PASS = "client-password";
    private static final String OCSP_CLIENT_CERT_GOOD_RESPONSE = "ocsp-client-good.der";
    private static final String OCSP_CLIENT_CERT_REVOKED_RESPONSE = "ocsp-client-revoked.der";
    @Parameterized.Parameters(name = "useFFM: {0}")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();
        parameterSets.add(new Object[] { Boolean.FALSE });
        parameterSets.add(new Object[] { Boolean.TRUE });
        return parameterSets;
    }

    @Parameterized.Parameter
    public boolean ffm;
    @Before
    public void runtimeCheck() {
        if (ffm) {
            Assume.assumeTrue("FFM is not available.", JreCompat.isJre22Available());
        }
    }

    @Test
    public void testOcspGood_ClientVerifiesServerCertificateOnly() throws Exception {
        Assert.assertEquals(HttpServletResponse.SC_OK, testOCSP(OCSP_SERVER_CERT_GOOD_RESPONSE, false, true, ffm));
    }
    @Test
    public void testOcspGood_Mutual() throws Exception {
        final int ocspResponderPortForClient = 8889;
        Assume.assumeTrue("Port " + ocspResponderPortForClient + " is not available.", isPortAvailable(ocspResponderPortForClient));
        try (FakeOcspResponder fakeOcspResponder = new FakeOcspResponder(Files.readAllBytes(new File(getPath(OCSP_CLIENT_CERT_GOOD_RESPONSE)).toPath()), ocspResponderPortForClient)){
            fakeOcspResponder.start();
            Assert.assertEquals(HttpServletResponse.SC_OK, testOCSP(OCSP_SERVER_CERT_GOOD_RESPONSE, true, true, ffm));
        }
    }
    @Test
    public void testOcspGood_ServerVerifiesClientCertificateOnly() throws Exception {
        final int ocspResponderPortForClient = 8889;
        Assume.assumeTrue("Port " + ocspResponderPortForClient + " is not available.", isPortAvailable(ocspResponderPortForClient));
        try (FakeOcspResponder fakeOcspResponder = new FakeOcspResponder(Files.readAllBytes(new File(getPath(OCSP_CLIENT_CERT_GOOD_RESPONSE)).toPath()), ocspResponderPortForClient)){
            fakeOcspResponder.start();
            Assert.assertEquals(HttpServletResponse.SC_OK, testOCSP(OCSP_SERVER_CERT_REVOKED_RESPONSE, true, false, ffm));
        }
    }
    @Test(expected = CertificateRevokedException.class)
    public void testOcspRevoked_ClientVerifiesServerCertificateOnly() throws Exception {
        try {
            testOCSP(OCSP_SERVER_CERT_REVOKED_RESPONSE, false, true, ffm);
        }catch (SSLHandshakeException sslHandshakeException) {
            handleExceptionWhenRevoked(sslHandshakeException);
        }
    }
    @Test(expected = CertificateRevokedException.class)
    public void testOcspRevoked_Mutual() throws Exception {
        try {
            // The exception is thrown before server side verification, while client does OCSP verification.
            testOCSP(OCSP_SERVER_CERT_REVOKED_RESPONSE, true, true, ffm);
        }catch (SSLHandshakeException sslHandshakeException) {
            handleExceptionWhenRevoked(sslHandshakeException);
        }
    }
    @Test(expected = SSLHandshakeException.class)
    public void testOcspRevoked_ServerVerifiesClientCertificateOnly() throws Exception {
        final int ocspResponderPortForClient = 8889;
        Assume.assumeTrue("Port " + ocspResponderPortForClient + " is not available.", isPortAvailable(ocspResponderPortForClient));
        try (FakeOcspResponder fakeOcspResponder = new FakeOcspResponder(Files.readAllBytes(new File(getPath(OCSP_CLIENT_CERT_REVOKED_RESPONSE)).toPath()), ocspResponderPortForClient)){
            fakeOcspResponder.start();
            testOCSP(OCSP_SERVER_CERT_GOOD_RESPONSE, true, false, ffm);
        }
    }
    @Test
    public void testOcsp_NoVerification() throws Exception {
        final int ocspResponderPortForClient = 8889;
        Assume.assumeTrue("Port " + ocspResponderPortForClient + " is not available.", isPortAvailable(ocspResponderPortForClient));
        try (FakeOcspResponder fakeOcspResponder = new FakeOcspResponder(Files.readAllBytes(new File(getPath(OCSP_CLIENT_CERT_REVOKED_RESPONSE)).toPath()), ocspResponderPortForClient)){
            fakeOcspResponder.start();
            Assert.assertEquals(HttpServletResponse.SC_OK, testOCSP(OCSP_SERVER_CERT_REVOKED_RESPONSE, false, false, ffm));
        }
    }
    @Test
    public void testOcspResponderUrlDiscoveryViaCertificateAIA() throws Exception {
        final int ocspPort = 8888;
        Assume.assumeTrue("Port " + ocspPort + " is not available.", isPortAvailable(ocspPort));
        Assert.assertEquals(HttpServletResponse.SC_OK, testOCSP(OCSP_SERVER_CERT_GOOD_RESPONSE, false, true, ffm,
                true, ocspPort));
    }
    private int testOCSP(String pathToOcspResponse, boolean serverSideVerificationEnabled, boolean clientSideOcspVerificationEnabled, boolean ffm) throws Exception {
        return testOCSP(pathToOcspResponse, serverSideVerificationEnabled, clientSideOcspVerificationEnabled, ffm,
            false, 0);
    }
    private int testOCSP(String pathToOcspResponse, boolean serverSideVerificationEnabled, boolean clientSideOcspVerificationEnabled, boolean ffm,
                         boolean clientDiscoversResponderFromAIA, int ocspResponderPort) throws Exception {
        File certificateFile = new File(getPath(SERVER_CERTIFICATE_PATH));
        File certificateKeyFile = new File(getPath(SERVER_CERTIFICATE_KEY_PATH));
        File certificateChainFile = new File(getPath(CA_CERTIFICATE_PATH));
        Tomcat tomcat = getTomcatInstance();
        initSsl(tomcat, serverSideVerificationEnabled, certificateFile, certificateKeyFile, certificateChainFile);
        TesterSupport.configureSSLImplementation(tomcat,
                ffm ? "org.apache.tomcat.util.net.openssl.panama.OpenSSLImplementation" : OpenSSLImplementation.class.getName(),
                true);

        Context context = tomcat.addContext("", null);
        Tomcat.addServlet(context, "simple", new TesterSupport.SimpleServlet());
        context.addServletMappingDecoded("/", "simple");

        KeyStore trustStore = KeyStore.getInstance(KEYSTORE_TYPE);
        String trustStorePass = Files.readString(new File(getPath(TRUSTSTORE_PASS)).toPath()).trim();
        trustStore.load(new FileInputStream(new File(getPath(TRUSTSTORE_PATH)).getAbsolutePath()), trustStorePass.toCharArray());
        KeyStore clientKeystore = KeyStore.getInstance(KEYSTORE_TYPE);
        String clientKeystorePass = Files.readString(new File(getPath(CLIENT_KEYSTORE_PASS)).toPath()).trim();
        clientKeystore.load(new FileInputStream(new File(getPath(CLIENT_KEYSTORE_PATH)).getAbsolutePath()), clientKeystorePass.toCharArray());
        byte[] ocspResponse = Files.readAllBytes(new File(getPath(pathToOcspResponse)).toPath());
        try (FakeOcspResponder fakeOcspResponder = new FakeOcspResponder(ocspResponse, ocspResponderPort)) {
            fakeOcspResponder.start();
            tomcat.start();

            URL url = new URI("https://127.0.0.1:" + getPort() + "/").toURL();
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            SSLSocketFactory sslSocketFactory;
            if (clientSideOcspVerificationEnabled) {
                sslSocketFactory = buildClientSslSocketFactoryWithOcsp(clientDiscoversResponderFromAIA ? null : fakeOcspResponder.url(), trustStore, clientKeystore, clientKeystorePass);
            } else {
                sslSocketFactory = buildClientSslSocketFactoryNoOcsp(trustStore, clientKeystore, clientKeystorePass);
            }
            connection.setSSLSocketFactory(sslSocketFactory);
            connection.connect();
            return connection.getResponseCode();
        } finally {
            tomcat.stop();
        }
    }

    private static void initSsl(Tomcat tomcat, boolean serverSideVerificationEnabled, File certificateFile, File certificateKeyFile, File certificateChainFile) {
        Connector connector = tomcat.getConnector();
        connector.setSecure(true);
        connector.setProperty("SSLEnabled", "true");

        SSLHostConfig sslHostConfig = new SSLHostConfig();
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(sslHostConfig, SSLHostConfigCertificate.Type.UNDEFINED);
        sslHostConfig.addCertificate(certificate);
        certificate.setCertificateFile(certificateFile.getAbsolutePath());
        certificate.setCertificateKeyFile(certificateKeyFile.getAbsolutePath());
        certificate.setCertificateChainFile(certificateChainFile.getAbsolutePath());
        if (serverSideVerificationEnabled) {
            sslHostConfig.setCertificateVerification("required");
        } else {
            sslHostConfig.setCertificateVerification("optionalNoCA");
        }
        sslHostConfig.setCaCertificateFile(certificateChainFile.getAbsolutePath());
        connector.addSslHostConfig(sslHostConfig);
    }

    private static SSLSocketFactory buildClientSslSocketFactoryWithOcsp(String ocspUrl, KeyStore trustStore, KeyStore clientKeystore, String clientKeystorePass) throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(clientKeystore, clientKeystorePass.toCharArray());
        Set<TrustAnchor> trustAnchors = getTrustAnchorsFromKeystore(trustStore);
        PKIXRevocationChecker revocationChecker =(PKIXRevocationChecker) CertPathValidator.getInstance("PKIX").getRevocationChecker();
        if (ocspUrl != null) {
            revocationChecker.setOcspResponder(new URI(ocspUrl));
        }
        revocationChecker.setOptions(EnumSet.of(PKIXRevocationChecker.Option.NO_FALLBACK));

        PKIXBuilderParameters pkix = new PKIXBuilderParameters(trustAnchors, new X509CertSelector());
        pkix.addCertPathChecker(revocationChecker);

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("PKIX");
        trustManagerFactory.init(new CertPathTrustManagerParameters(pkix));
        return initSSLContext(kmf, trustManagerFactory).getSocketFactory();
    }
    private static SSLSocketFactory buildClientSslSocketFactoryNoOcsp(KeyStore trustStore, KeyStore clientKeystore, String clientKeystorePass) throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(clientKeystore, clientKeystorePass.toCharArray());
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        return initSSLContext(kmf, trustManagerFactory).getSocketFactory();
    }
    private static SSLContext initSSLContext(KeyManagerFactory keyManagerFactory, TrustManagerFactory trustManagerFactory) throws Exception {
        SSLContext sslContext;
        if (TesterSupport.isTlsv13Available()) {
            sslContext = SSLContext.getInstance(Constants.SSL_PROTO_TLSv1_3);
        } else {
            sslContext = SSLContext.getInstance(Constants.SSL_PROTO_TLSv1_2);
        }
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
        return sslContext;
    }
    private static Set<TrustAnchor> getTrustAnchorsFromKeystore(KeyStore keyStore) throws KeyStoreException {
        Set<TrustAnchor> trustAnchors = new HashSet<>();
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            Certificate certificate = keyStore.getCertificate(alias);
            if (certificate instanceof X509Certificate) {
                trustAnchors.add(new TrustAnchor((X509Certificate)certificate, null));
            }
        }
        return trustAnchors;
    }
    private static void handleExceptionWhenRevoked(Exception exception) throws Exception {
        if (exception.getCause().getCause() instanceof CertPathValidatorException) {
            CertPathValidatorException cpe = (CertPathValidatorException) exception.getCause().getCause();
            Assert.assertEquals("REVOKED", cpe.getReason().toString());
            Assert.assertTrue(cpe.toString().contains("reason: KEY_COMPROMISE"));
            // Some JDKs only expose CertPathValidatorException
            if (cpe.getCause() instanceof CertificateRevokedException) {
                throw (CertificateRevokedException) cpe.getCause();
            } else {
                throw new CertificateRevokedException(new Date(), CRLReason.KEY_COMPROMISE, new X500Principal(""), new HashMap<>());
            }
        }
    }

    private static class FakeOcspResponder implements Closeable {
        private final byte[] ocspResponse;
        private HttpServer server;
        private int port;

        FakeOcspResponder(byte[] ocspResponse, int port) {
            this.ocspResponse = ocspResponse;
            this.port = port;
        }

        void start() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            server.createContext("/ocsp", httpExchange -> {
                byte[] body = ocspResponse;
                Headers headers = httpExchange.getResponseHeaders();
                headers.add("Content-Type", "application/ocsp-response");
                httpExchange.sendResponseHeaders(HttpServletResponse.SC_OK, body.length);
                try (OutputStream os = httpExchange.getResponseBody()) {
                    os.write(body);
                }
            });
            server.start();
            port = server.getAddress().getPort();
        }

        String url() {
            return "http://127.0.0.1:" + port + "/ocsp";
        }
        @Override public void close() {
            if (server != null) {
                server.stop(0);
            }
        }
    }

    private String getPath(String file) throws IOException {
        if (file == null) {
            return null;
        }
        String packageName = this.getClass().getPackageName();
        String path = packageName.replace(".", File.separator);
        File f = new File("test" + File.separator + path + File.separator + file);

        return f.getCanonicalPath();
    }

    @SuppressWarnings("unused")
    private boolean isPortAvailable(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
