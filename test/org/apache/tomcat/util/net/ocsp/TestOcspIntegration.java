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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
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
import org.apache.tomcat.util.net.openssl.OpenSSLConf;
import org.apache.tomcat.util.net.openssl.OpenSSLConfCmd;
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
    private static final String OCSP_GOOD_RESPONSE = "ocsp-good.der";
    private static final String OCSP_REVOKED_RESPONSE = "ocsp-revoked.der";
    @Parameterized.Parameters(name = "{0}")
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
            Assume.assumeTrue(JreCompat.isJre22Available());
        }
    }

    @Test
    public void testOcspGood() throws Exception {
        Assert.assertEquals(HttpServletResponse.SC_OK, testOCSP(OCSP_GOOD_RESPONSE, false, false, ffm));
    }
    @Test(expected = CertificateRevokedException.class)
    public void testOcspRevoked() throws Exception {
        try {
            testOCSP(OCSP_REVOKED_RESPONSE, false, false, ffm);
        }catch (SSLHandshakeException sslHandshakeException) {
            if (sslHandshakeException.getCause().getCause() instanceof CertPathValidatorException) {
                CertPathValidatorException cpe = (CertPathValidatorException) sslHandshakeException.getCause().getCause();
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
    }
    @Test
    public void testOcspNoCheck() throws Exception {
        Assert.assertEquals(HttpServletResponse.SC_OK, testOCSP(OCSP_REVOKED_RESPONSE, false, true, ffm));
    }
    @Test
    public void testOcspNoCheck_01() throws Exception {
        Assume.assumeTrue(isSslConfCtxNewAvailable());
        Assert.assertEquals(HttpServletResponse.SC_OK, testOCSP(OCSP_REVOKED_RESPONSE, true, true, ffm));
    }
    @Test(expected = SSLHandshakeException.class)
    public void testOcspNoCheck_02() throws Exception {
        Assume.assumeTrue(isSslConfCtxNewAvailable());
        testOCSP(OCSP_REVOKED_RESPONSE, true, false, ffm);
    }
    @Test
    public void testOcspNoCheck_03() throws Exception {
        Assert.assertEquals(HttpServletResponse.SC_OK, testOCSP(OCSP_REVOKED_RESPONSE, false, true, ffm));
    }
    @Test
    public void testOcspResponderUrlDiscoveryViaCertificateAIA() throws Exception {
        final int ocspPort = 8888;
        Assume.assumeTrue(isPortAvailable(ocspPort));
        Assert.assertEquals(HttpServletResponse.SC_OK, testOCSP(OCSP_GOOD_RESPONSE, false, false, ffm,
                true, "127.0.0.1", ocspPort));
    }
    //This test is a reference to CVE-2017-15698 of tomcat-native
    @Test
    public void testOcspWithLongResponderUrlViaProxy() throws Exception {
        final int ocspPort = 8889;
        Assume.assumeTrue(isPortAvailable(ocspPort));
        StringBuilder longHostname = new StringBuilder();
        longHostname.append("a".repeat(128));

        String originalProxyHost = System.getProperty("http.proxyHost");
        String originalProxyPort = System.getProperty("http.proxyPort");

        try (ForwardingProxy proxy = new ForwardingProxy("127.0.0.1", ocspPort)) {
            Thread proxyThread = new Thread(proxy);
            proxyThread.start();
            System.setProperty("http.proxyHost", "127.0.0.1");
            System.setProperty("http.proxyPort", String.valueOf(proxy.getPort()));
            try {
                testOCSP(OCSP_REVOKED_RESPONSE, false, false, ffm,
                        false, longHostname.toString(), ocspPort);
                Assert.fail("Should have thrown an exception");
            } catch (SSLHandshakeException sslHandshakeException) {
                Assert.assertTrue(true);
            }
        } finally {
            if (originalProxyHost == null) {
                System.clearProperty("http.proxyHost");
            } else {
                System.setProperty("http.proxyHost", originalProxyHost);
            }
            if (originalProxyPort == null) {
                System.clearProperty("http.proxyPort");
            } else {
                System.setProperty("http.proxyPort", originalProxyPort);
            }
        }
    }
    private int testOCSP(String pathToOcspResponse, boolean serverSideOcspVerificationDisabled, boolean clientSideOcspVerificationDisabled, boolean ffm) throws Exception {
        return testOCSP(pathToOcspResponse, serverSideOcspVerificationDisabled, clientSideOcspVerificationDisabled, ffm,
                false, "127.0.0.1", 0);
    }
    private int testOCSP(String pathToOcspResponse, boolean serverSideOcspVerificationDisabled, boolean clientSideOcspVerificationDisabled, boolean ffm,
                        boolean discoverResponderFromAIA, String ocspResponderHostname, int ocspResponderPort) throws Exception {
        File certificateFile = new File(getPath(SERVER_CERTIFICATE_PATH));
        File certificateKeyFile = new File(getPath(SERVER_CERTIFICATE_KEY_PATH));
        File certificateChainFile = new File(getPath(CA_CERTIFICATE_PATH));
        Tomcat tomcat = getTomcatInstance();
        initSsl(tomcat, certificateFile, certificateKeyFile, certificateChainFile);
        TesterSupport.configureSSLImplementation(tomcat,
                ffm ? "org.apache.tomcat.util.net.openssl.panama.OpenSSLImplementation" : OpenSSLImplementation.class.getName(),
                true);
        if (serverSideOcspVerificationDisabled) {
            SSLHostConfig sslHostConfig = tomcat.getConnector().findSslHostConfigs()[0];
            OpenSSLConf conf = new OpenSSLConf();
            OpenSSLConfCmd cmd = new OpenSSLConfCmd();
            cmd.setName("NO_OCSP_CHECK");
            cmd.setValue("true");
            conf.addCmd(cmd);
            sslHostConfig.setOpenSslConf(conf);
        }

        Context context = tomcat.addContext("", null);
        Tomcat.addServlet(context, "simple", new TesterSupport.SimpleServlet());
        context.addServletMappingDecoded("/", "simple");

        KeyStore trustStorePath = KeyStore.getInstance(KEYSTORE_TYPE);
        String trustStorePass = Files.readString(new File(getPath(TRUSTSTORE_PASS)).toPath()).trim();
        trustStorePath.load(new FileInputStream(new File(getPath(TRUSTSTORE_PATH)).getAbsolutePath()), trustStorePass.toCharArray());
        byte[] ocspResponse = Files.readAllBytes(new File(getPath(pathToOcspResponse)).toPath());
        try (FakeOcspResponder fakeOcspResponder = new FakeOcspResponder(ocspResponse, ocspResponderHostname, ocspResponderPort)) {
            fakeOcspResponder.start();
            tomcat.start();

            URL url = new URI("https://127.0.0.1:" + getPort() + "/").toURL();
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            SSLSocketFactory sslSocketFactory;
            if (clientSideOcspVerificationDisabled) {
                sslSocketFactory = buildClientSslSocketFactoryNoOcsp(trustStorePath);
            } else {
                sslSocketFactory = buildClientSslSocketFactoryWithOcsp(discoverResponderFromAIA ? null : fakeOcspResponder.url(), trustStorePath);
            }
            connection.setSSLSocketFactory(sslSocketFactory);
            connection.connect();
            return connection.getResponseCode();
        } finally {
            tomcat.stop();
        }
    }

    private static void initSsl(Tomcat tomcat, File certificateFile, File certificateKeyFile, File certificateChainFile) {
        Connector connector = tomcat.getConnector();
        connector.setSecure(true);
        Assert.assertTrue(connector.setProperty("SSLEnabled", "true"));

        SSLHostConfig sslHostConfig = new SSLHostConfig();
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(sslHostConfig, SSLHostConfigCertificate.Type.UNDEFINED);
        sslHostConfig.addCertificate(certificate);
        connector.addSslHostConfig(sslHostConfig);
        certificate.setCertificateFile(certificateFile.getAbsolutePath());
        certificate.setCertificateKeyFile(certificateKeyFile.getAbsolutePath());
        certificate.setCertificateChainFile(certificateChainFile.getAbsolutePath());
    }

    private static SSLSocketFactory buildClientSslSocketFactoryWithOcsp(String ocspUrl, KeyStore trustStore) throws Exception {
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
        return initSSLContext(trustManagerFactory).getSocketFactory();
    }
    private static SSLSocketFactory buildClientSslSocketFactoryNoOcsp(KeyStore trustStore) throws Exception {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        return initSSLContext(trustManagerFactory).getSocketFactory();
    }
    private static SSLContext initSSLContext(TrustManagerFactory trustManagerFactory) throws Exception {
        SSLContext sslContext;
        if (TesterSupport.isTlsv13Available()) {
            sslContext = SSLContext.getInstance(Constants.SSL_PROTO_TLSv1_3);
        } else {
            sslContext = SSLContext.getInstance(Constants.SSL_PROTO_TLSv1_2);
        }
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
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

    private static class FakeOcspResponder implements Closeable {
        private final byte[] ocspResponse;
        private HttpServer server;
        private int port;
        private final String hostname;

        FakeOcspResponder(byte[] ocspResponse, String hostname, int port) {
            this.ocspResponse = ocspResponse;
            this.hostname = hostname;
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
            return "http://" + hostname + ":" + port + "/ocsp";
        }
        @Override public void close() {
            if (server != null) {
                server.stop(0);
            }
        }
    }
    private static class ForwardingProxy implements Closeable, Runnable {
        private final ServerSocket serverSocket;
        private final String targetHost;
        private final int targetPort;
        private volatile boolean running = true;

        ForwardingProxy(String targetHost, int targetPort) throws IOException {
            this.serverSocket = new ServerSocket(0);
            this.targetHost = targetHost;
            this.targetPort = targetPort;
        }

        public int getPort() {
            return serverSocket.getLocalPort();
        }

        @Override
        public void close() throws IOException {
            running = false;
            serverSocket.close();
        }

        @Override
        public void run() {
            try {
                while (running) {
                    try (Socket clientSocket = serverSocket.accept();
                            Socket targetSocket = new Socket(targetHost, targetPort)) {

                        Thread clientToTarget = new Thread(() -> {
                            try {
                                transfer(clientSocket.getInputStream(), targetSocket.getOutputStream());
                            } catch (IOException ignored) {}
                        });

                        Thread targetToClient = new Thread(() -> {
                            try {
                                transfer(targetSocket.getInputStream(), clientSocket.getOutputStream());
                            } catch (IOException ignored) {}
                        });

                        clientToTarget.start();
                        targetToClient.start();
                        clientToTarget.join();
                        targetToClient.join();

                    } catch (IOException | InterruptedException ignored) {}
                }
            } finally {
                try {
                    close();
                } catch (IOException ignored) {}
            }
        }

        private void transfer(InputStream in, OutputStream out) throws IOException {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
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
    private boolean isSslConfCtxNewAvailable() {
        if (!ffm) {
            return true;
        }
        try {
            Class.forName("org.apache.tomcat.util.openssl.openssl_h$SSL_CONF_CTX_new");
            return true;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError | ClassNotFoundException | ExceptionInInitializerError e) {
            // This is the expected error on systems with an incompatible library (like LibreSSL).
            return false;
        }
    }
}
