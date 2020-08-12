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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;

import org.apache.catalina.Context;
import org.apache.catalina.authenticator.SSLAuthenticator;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.AprLifecycleListener;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.startup.TesterMapRealm;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.jni.Library;
import org.apache.tomcat.jni.LibraryNotFoundError;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.util.compat.JrePlatform;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.Type;

public final class TesterSupport {

    public static final String SSL_DIR = "test/org/apache/tomcat/util/net/";
    public static final String CA_ALIAS = "ca";
    public static final String CA_JKS = SSL_DIR + CA_ALIAS + ".jks";
    public static final String CLIENT_ALIAS = "user1";
    public static final String CLIENT_JKS = SSL_DIR + CLIENT_ALIAS + ".jks";
    public static final String LOCALHOST_EC_JKS = SSL_DIR + "localhost-ec.jks";
    public static final String LOCALHOST_RSA_JKS = SSL_DIR + "localhost-rsa.jks";
    public static final String LOCALHOST_KEYPASS_JKS = SSL_DIR + "localhost-rsa-copy1.jks";
    public static final String JKS_PASS = "changeit";
    public static final String JKS_KEY_PASS = "tomcatpass";
    public static final String CA_CERT_PEM = SSL_DIR + CA_ALIAS + "-cert.pem";
    public static final String LOCALHOST_EC_CERT_PEM = SSL_DIR + "localhost-ec-cert.pem";
    public static final String LOCALHOST_EC_KEY_PEM = SSL_DIR + "localhost-ec-key.pem";
    public static final String LOCALHOST_RSA_CERT_PEM = SSL_DIR + "localhost-rsa-cert.pem";
    public static final String LOCALHOST_RSA_KEY_PEM = SSL_DIR + "localhost-rsa-key.pem";
    public static final boolean OPENSSL_AVAILABLE;
    public static final int OPENSSL_VERSION;
    public static final String OPENSSL_ERROR;
    public static final boolean TLSV13_AVAILABLE;

    public static final String ROLE = "testrole";

    private static String clientAuthExpectedIssuer;
    private static String lastUsage = "NONE";
    private static Principal[] lastRequestedIssuers = new Principal[0];

    static {
        boolean available = false;
        int version = 0;
        String err = "";
        try {
            Library.initialize(null);
            available = true;
            version = SSL.version();
            Library.terminate();
        } catch (Exception | LibraryNotFoundError ex) {
            err = ex.getMessage();
        }
        OPENSSL_AVAILABLE = available;
        OPENSSL_VERSION = version;
        OPENSSL_ERROR = err;

        available = false;
        try {
            SSLContext.getInstance(Constants.SSL_PROTO_TLSv1_3);
            available = true;
        } catch (NoSuchAlgorithmException ex) {
        }
        TLSV13_AVAILABLE = available;
    }

    public static boolean isOpensslAvailable() {
        return OPENSSL_AVAILABLE;
    }

    public static int getOpensslVersion() {
        return OPENSSL_VERSION;
    }

    public static boolean isTlsv13Available() {
        return TLSV13_AVAILABLE;
    }

    public static void initSsl(Tomcat tomcat) {
        initSsl(tomcat, LOCALHOST_RSA_JKS, null, null);
    }

    protected static void initSsl(Tomcat tomcat, String keystore,
            String keystorePass, String keyPass) {

        Connector connector = tomcat.getConnector();
        connector.setSecure(true);
        Assert.assertTrue(connector.setProperty("SSLEnabled", "true"));

        SSLHostConfig sslHostConfig = new SSLHostConfig();
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(sslHostConfig, Type.UNDEFINED);
        sslHostConfig.addCertificate(certificate);
        connector.addSslHostConfig(sslHostConfig);

        String protocol = tomcat.getConnector().getProtocolHandlerClassName();
        if (!protocol.contains("Apr")) {
            String sslImplementation = System.getProperty("tomcat.test.sslImplementation");
            if (sslImplementation != null && !"${test.sslImplementation}".equals(sslImplementation)) {
                StandardServer server = (StandardServer) tomcat.getServer();
                AprLifecycleListener listener = new AprLifecycleListener();
                listener.setSSLRandomSeed("/dev/urandom");
                server.addLifecycleListener(listener);
                Assert.assertTrue(connector.setProperty("sslImplementationName", sslImplementation));
            }
            sslHostConfig.setSslProtocol("tls");
            certificate.setCertificateKeystoreFile(new File(keystore).getAbsolutePath());
            sslHostConfig.setTruststoreFile(new File(CA_JKS).getAbsolutePath());
            if (keystorePass != null) {
                certificate.setCertificateKeystorePassword(keystorePass);
            }
            if (keyPass != null) {
                certificate.setCertificateKeyPassword(keyPass);
            }
        } else {
            certificate.setCertificateFile(new File(LOCALHOST_RSA_CERT_PEM).getAbsolutePath());
            certificate.setCertificateKeyFile(new File(LOCALHOST_RSA_KEY_PEM).getAbsolutePath());
            sslHostConfig.setCaCertificateFile(new File(CA_CERT_PEM).getAbsolutePath());
        }
    }

    protected static KeyManager[] getUser1KeyManagers() throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(getKeyStore(CLIENT_JKS), JKS_PASS.toCharArray());
        KeyManager[] managers = kmf.getKeyManagers();
        KeyManager manager;
        for (int i=0; i < managers.length; i++) {
            manager = managers[i];
            if (manager instanceof X509ExtendedKeyManager) {
                managers[i] = new TrackingExtendedKeyManager((X509ExtendedKeyManager)manager);
            } else if (manager instanceof X509KeyManager) {
                managers[i] = new TrackingKeyManager((X509KeyManager)manager);
            }
        }
        return managers;
    }

    protected static TrustManager[] getTrustManagers() throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(getKeyStore(CA_JKS));
        return tmf.getTrustManagers();
    }

    public static ClientSSLSocketFactory configureClientSsl() {
        ClientSSLSocketFactory clientSSLSocketFactory = null;
        try {
            SSLContext sc;
            if (TesterSupport.TLSV13_AVAILABLE) {
                 sc = SSLContext.getInstance("TLSv1.3");
            } else {
                sc = SSLContext.getInstance(Constants.SSL_PROTO_TLS);
            }
            sc.init(TesterSupport.getUser1KeyManagers(),
                    TesterSupport.getTrustManagers(),
                    null);
            clientSSLSocketFactory = new ClientSSLSocketFactory(sc.getSocketFactory());
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(clientSSLSocketFactory);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return clientSSLSocketFactory;
    }

    private static KeyStore getKeyStore(String keystore) throws Exception {
        File keystoreFile = new File(keystore);
        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream is = new FileInputStream(keystoreFile)) {
            ks.load(is, JKS_PASS.toCharArray());
        }
        return ks;
    }

    public static boolean isRenegotiationSupported(Tomcat tomcat) {
        String protocol = tomcat.getConnector().getProtocolHandlerClassName();
        if (protocol.contains("Apr")) {
            // Disabled by default in 1.1.20 windows binary (2010-07-27)
            return false;
        }

        return true;
    }

    protected static boolean isClientRenegotiationSupported(Tomcat tomcat) {
        String protocol = tomcat.getConnector().getProtocolHandlerClassName();
        if (protocol.contains("Apr")) {
            // Disabled by default in 1.1.20 windows binary (2010-07-27)
            return false;
        }
        if (protocol.contains("NioProtocol") || (protocol.contains("Nio2Protocol") && JrePlatform.IS_MAC_OS)) {
            // Doesn't work on all platforms - see BZ 56448.
            return false;
        }
        String sslImplementation = System.getProperty("tomcat.test.sslImplementation");
        if (sslImplementation != null && !"${test.sslImplementation}".equals(sslImplementation)) {
            // Assume custom SSL is not supporting this
            return false;
        }

        return true;
    }

    public static void configureClientCertContext(Tomcat tomcat) {
        TesterSupport.initSsl(tomcat);

        /* When running on Java 11, TLSv1.3 is enabled by default. The JSSE
         * implementation of TLSv1.3 does not support
         * certificateVerification="optional", a setting on which these tests
         * depend. Therefore, force these tests to use TLSv1.2 so that they pass
         * when running on TLSv1.3.
         */
        tomcat.getConnector().findSslHostConfigs()[0].setProtocols(Constants.SSL_PROTO_TLSv1_2);

        // Need a web application with a protected and unprotected URL
        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        Tomcat.addServlet(ctx, "simple", new SimpleServlet());
        ctx.addServletMappingDecoded("/unprotected", "simple");
        ctx.addServletMappingDecoded("/protected", "simple");

        // Security constraints
        SecurityCollection collection = new SecurityCollection();
        collection.addPatternDecoded("/protected");
        SecurityConstraint sc = new SecurityConstraint();
        sc.addAuthRole(ROLE);
        sc.addCollection(collection);
        ctx.addConstraint(sc);

        // Configure the Realm
        TesterMapRealm realm = new TesterMapRealm();

        // Get the CA subject the server should send us for client cert selection
        try {
            KeyStore ks = getKeyStore(CA_JKS);
            X509Certificate cert = (X509Certificate)ks.getCertificate(CA_ALIAS);
            clientAuthExpectedIssuer = cert.getSubjectDN().getName();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        String cn = "NOTFOUND";
        try {
            KeyStore ks = getKeyStore(CLIENT_JKS);
            X509Certificate cert = (X509Certificate)ks.getCertificate(CLIENT_ALIAS);
            cn = cert.getSubjectDN().getName();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        realm.addUser(cn, "not used");
        realm.addUserRole(cn, ROLE);
        ctx.setRealm(realm);

        // Configure the authenticator
        LoginConfig lc = new LoginConfig();
        lc.setAuthMethod("CLIENT-CERT");
        ctx.setLoginConfig(lc);
        ctx.getPipeline().addValve(new SSLAuthenticator());

        // Clear the tracking data
        lastUsage = "NONE";
        lastRequestedIssuers = new Principal[0];
    }

    protected static String getClientAuthExpectedIssuer() {
        return clientAuthExpectedIssuer;
    }

    protected static void trackTrackingKeyManagers(@SuppressWarnings("unused") KeyManager wrapper,
            @SuppressWarnings("unused") KeyManager wrapped, String usage, Principal[] issuers) {
        lastUsage = usage;
        lastRequestedIssuers = issuers;
    }

    protected static String getLastClientAuthKeyManagerUsage() {
        return lastUsage;
    }

    protected static int getLastClientAuthRequestedIssuerCount() {
        return lastRequestedIssuers == null ? 0 : lastRequestedIssuers.length;
    }

    protected static Principal getLastClientAuthRequestedIssuer(int index) {
        return lastRequestedIssuers[index];
    }

    protected static boolean checkLastClientAuthRequestedIssuers() {
        if (lastRequestedIssuers == null || lastRequestedIssuers.length != 1)
            return false;
        return (new X500Principal(clientAuthExpectedIssuer)).equals(
                    new X500Principal(lastRequestedIssuers[0].getName()));
    }

    public static final byte DATA = (byte)33;

    public static class SimpleServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.getWriter().print("OK");
            if (req.isUserInRole(ROLE)) {
                resp.getWriter().print("-" + ROLE);
            }
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            // Swallow any request body
            int read = 0;
            int len = 0;
            byte[] buffer = new byte[4096];
            InputStream is = req.getInputStream();
            boolean contentOK = true;
            while (len > -1) {
                len = is.read(buffer);
                read = read + len;
                for (int i=0; i<len && contentOK; i++) {
                    contentOK = (buffer[i] == DATA);
                }
            }
            // len will have been -1 on last iteration
            read++;

            // Report the number of bytes read
            resp.setContentType("text/plain");
            if (contentOK)
                resp.getWriter().print("OK-" + read);
            else
                resp.getWriter().print("CONTENT-MISMATCH-" + read);
        }
    }

    public static class TrackingKeyManager implements X509KeyManager {

        private X509KeyManager manager = null;

        public TrackingKeyManager(X509KeyManager manager) {
            this.manager = manager;
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            trackTrackingKeyManagers(this, manager, "chooseClientAlias", issuers);
            return manager.chooseClientAlias(keyType, issuers, socket);
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            trackTrackingKeyManagers(this, manager, "chooseServerAlias", issuers);
            return manager.chooseServerAlias(keyType, issuers, socket);
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return manager.getCertificateChain(alias);
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            trackTrackingKeyManagers(this, manager, "getClientAliases", issuers);
            return manager.getClientAliases(keyType, issuers);
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return manager.getPrivateKey(alias);
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            trackTrackingKeyManagers(this, manager, "getServerAliases", issuers);
            return manager.getServerAliases(keyType, issuers);
        }
    }

    public static class TrackingExtendedKeyManager extends X509ExtendedKeyManager {

        private X509ExtendedKeyManager manager = null;

        public TrackingExtendedKeyManager(X509ExtendedKeyManager manager) {
            this.manager = manager;
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            trackTrackingKeyManagers(this, manager, "chooseClientAlias", issuers);
            return manager.chooseClientAlias(keyType, issuers, socket);
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            trackTrackingKeyManagers(this, manager, "chooseServerAlias", issuers);
            return manager.chooseServerAlias(keyType, issuers, socket);
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return manager.getCertificateChain(alias);
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            trackTrackingKeyManagers(this, manager, "getClientAliases", issuers);
            return manager.getClientAliases(keyType, issuers);
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return manager.getPrivateKey(alias);
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            trackTrackingKeyManagers(this, manager, "getServerAliases", issuers);
            return manager.getServerAliases(keyType, issuers);
        }

        @Override
        public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
            trackTrackingKeyManagers(this, manager, "chooseEngineClientAlias", issuers);
            return manager.chooseEngineClientAlias(keyType, issuers, engine);
        }

        @Override
        public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
            trackTrackingKeyManagers(this, manager, "chooseEngineServerAlias", issuers);
            return manager.chooseEngineServerAlias(keyType, issuers, engine);
        }
    }

    public static class TrustAllCerts implements X509TrustManager {

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        @Override
        public void checkClientTrusted(X509Certificate[] certs,
                String authType) {
            // NOOP - Trust everything
        }

        @Override
        public void checkServerTrusted(X509Certificate[] certs,
                String authType) {
            // NOOP - Trust everything
        }
    }

    public static class SequentialTrustManager implements X509TrustManager {

        private static X509TrustManager[] tms;
        private static X509Certificate[] certs;

        static {
            try {
                TrustManager[] managers = getTrustManagers();
                int mcount = 0;
                int ccount = 0;
                for (TrustManager tm : managers) {
                    if (tm instanceof X509TrustManager) {
                        mcount++;
                        ccount += ((X509TrustManager)tm).getAcceptedIssuers().length;
                    }
                }
                tms = new X509TrustManager[mcount];
                certs = new X509Certificate[ccount];
                mcount = 0;
                ccount = 0;
                for (TrustManager tm : managers) {
                    if (tm instanceof X509TrustManager) {
                        tms[mcount] = (X509TrustManager)tm;
                        mcount++;
                        for (X509Certificate cert : ((X509TrustManager)tm).getAcceptedIssuers()) {
                            certs[ccount] = cert;
                            ccount++;
                        }
                    }
                }
            } catch (Exception ex) {
                tms = new X509TrustManager[1];
                tms[0] = new TrustAllCerts();
                certs = new X509Certificate[0];
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return certs;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] certs,
                String authType) throws CertificateException {
            boolean trust = false;
            for (X509TrustManager tm : tms) {
                try {
                    tm.checkClientTrusted(certs, authType);
                    trust = true;
                } catch (CertificateException ex) {
                    // Ignore
                }
            }
            if (!trust) {
                throw new CertificateException();
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] certs,
                String authType) throws CertificateException {
            boolean trust = false;
            for (X509TrustManager tm : tms) {
                try {
                    tm.checkServerTrusted(certs, authType);
                    trust = true;
                } catch (CertificateException ex) {
                    // Ignore
                }
            }
            if (!trust) {
                throw new CertificateException();
            }
        }
    }


    public static class ClientSSLSocketFactory extends SSLSocketFactory {

        private final SSLSocketFactory delegate;

        private String[] ciphers = null;


        public ClientSSLSocketFactory(SSLSocketFactory delegate) {
            this.delegate = delegate;
        }

        /**
         * Forces the use of the specified cipher.
         *
         * @param ciphers Array of standard JSSE names of ciphers to use
         */
        public void setCipher(String[] ciphers) {
            this.ciphers = ciphers;
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            Socket result = delegate.createSocket(s, host, port, autoClose);
            reconfigureSocket(result);
            return result;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
            Socket result = delegate.createSocket(host, port);
            reconfigureSocket(result);
            return result;
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            Socket result = delegate.createSocket(host, port);
            reconfigureSocket(result);
            return result;
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
                throws IOException, UnknownHostException {
            Socket result = delegate.createSocket(host, port, localHost, localPort);
            reconfigureSocket(result);
            return result;
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            Socket result = delegate.createSocket(address, port, localAddress, localPort);
            reconfigureSocket(result);
            return result;
        }

        private Socket reconfigureSocket(Socket socket) {
            if (ciphers != null) {
                ((SSLSocket) socket).setEnabledCipherSuites(ciphers);
            }
            return socket;
        }
    }


    /*
     * We want to use TLS 1.3 where we can but this requires TLS 1.3 to be
     * supported on the client and the server.
     */
    public static String getDefaultTLSProtocolForTesting(Connector connector) {
        // Clients always use JSSE
        if (!TLSV13_AVAILABLE) {
            // Client doesn't support TLS 1.3 so we have to use TLS 1.2
            return Constants.SSL_PROTO_TLSv1_2;
        }

        if (connector.getProtocolHandlerClassName().contains("Apr")) {
            // APR connector so OpenSSL is used for TLS.
            if (SSL.version() >= 0x1010100f) {
                return Constants.SSL_PROTO_TLSv1_3;
            } else {
                return Constants.SSL_PROTO_TLSv1_2;
            }
        } else {
            // NIO or NIO2. Tests do not use JSSE+OpenSSL so JSSE will be used.
            // Due to check above, it is known that TLS 1.3 is available
            return Constants.SSL_PROTO_TLSv1_3;
        }
    }


    public static boolean isDefaultTLSProtocolForTesting13(Connector connector) {
        return Constants.SSL_PROTO_TLSv1_3.equals(
                TesterSupport.getDefaultTLSProtocolForTesting(connector));
    }
}
