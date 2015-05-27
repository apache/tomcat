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
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.authenticator.SSLAuthenticator;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.AprLifecycleListener;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.deploy.SecurityCollection;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.startup.TestTomcat.MapRealm;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.jni.SSL;

public final class TesterSupport {

    public static final String ROLE = "testrole";

    protected static final boolean RFC_5746_SUPPORTED;

    static {
        boolean result = false;
        SSLContext context;
        try {
            context = SSLContext.getInstance("TLS");
            context.init(null, null, null);
            SSLServerSocketFactory ssf = context.getServerSocketFactory();
            String ciphers[] = ssf.getSupportedCipherSuites();
            for (String cipher : ciphers) {
                if ("TLS_EMPTY_RENEGOTIATION_INFO_SCSV".equals(cipher)) {
                    result = true;
                    break;
                }
            }
        } catch (NoSuchAlgorithmException e) {
            // Assume no RFC 5746 support
        } catch (KeyManagementException e) {
            // Assume no RFC 5746 support
        }
        RFC_5746_SUPPORTED = result;
    }

    public static void initSsl(Tomcat tomcat) {
        initSsl(tomcat, "localhost.jks", null, null);
    }

    protected static void initSsl(Tomcat tomcat, String keystore,
            String keystorePass, String keyPass) {

        ClassLoader cl = TesterSupport.class.getClassLoader();

        String protocol = tomcat.getConnector().getProtocolHandlerClassName();
        if (protocol.indexOf("Apr") == -1) {
            Connector connector = tomcat.getConnector();
            connector.setProperty("sslProtocol", "tls");
            
            java.net.URL keyStoreUrl =
                    cl.getResource("org/apache/tomcat/util/net/" + keystore);
            File keystoreFile = toFile(keyStoreUrl);
            connector.setAttribute("keystoreFile",
                    keystoreFile.getAbsolutePath());
            
            java.net.URL truststoreUrl =
                    cl.getResource("org/apache/tomcat/util/net/ca.jks");
            File truststoreFile = toFile(truststoreUrl);
            connector.setAttribute("truststoreFile",
                    truststoreFile.getAbsolutePath());
            
            if (keystorePass != null) {
                connector.setAttribute("keystorePass", keystorePass);
            }
            if (keyPass != null) {
                connector.setAttribute("keyPass", keyPass);
            }
        } else {
            java.net.URL keyStoreUrl =
                    cl.getResource("org/apache/tomcat/util/net/localhost-cert.pem");
            File keystoreFile = toFile(keyStoreUrl);
            tomcat.getConnector().setAttribute("SSLCertificateFile",
                    keystoreFile.getAbsolutePath());
            
            java.net.URL sslCertificateKeyUrl =
                    cl.getResource("org/apache/tomcat/util/net/localhost-key.pem");
            File sslCertificateKeyFile = toFile(sslCertificateKeyUrl);
            tomcat.getConnector().setAttribute("SSLCertificateKeyFile",
                    sslCertificateKeyFile.getAbsolutePath());
        }
        tomcat.getConnector().setSecure(true);
        tomcat.getConnector().setProperty("SSLEnabled", "true");
        // OpenSSL before 1.0.1 only supports TLSv1.
        // Our default SSLProtocol setting "all" includes unsupported TLSv1.1 and 1.2
        // and would produce an error during init.
        // Furthermore old Java 6 uses SSLv2Hello which OpenSSL only
        // supports if we choose multiple protocols.
        // Trigger loading of the native library and choose old protocol
        // if we use old OpenSSL.
        if (AprLifecycleListener.isAprAvailable() && SSL.version() < 0x10001000L) {
            tomcat.getConnector().setProperty("SSLProtocol", Constants.SSL_PROTO_TLSv1 + "+" + Constants.SSL_PROTO_SSLv3);
        }
    }

    private static File toFile(java.net.URL url) {
        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    protected static KeyManager[] getUser1KeyManagers() throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(getKeyStore("org/apache/tomcat/util/net/user1.jks"),
                "changeit".toCharArray());
        return kmf.getKeyManagers();
    }

    protected static TrustManager[] getTrustManagers() throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(getKeyStore("org/apache/tomcat/util/net/ca.jks"));
        return tmf.getTrustManagers();
    }


    protected static void configureClientSsl() {
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(TesterSupport.getUser1KeyManagers(),
                    TesterSupport.getTrustManagers(),
                    null);
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static KeyStore getKeyStore(String keystore) throws Exception {
        ClassLoader cl = TesterSupport.class.getClassLoader();
        java.net.URL keystoreUrl = cl.getResource(keystore);
        File keystoreFile = toFile(keystoreUrl);
        KeyStore ks = KeyStore.getInstance("JKS");
        InputStream is = null;
        try {
            is = new FileInputStream(keystoreFile);
            ks.load(is, "changeit".toCharArray());
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ioe) {
                    // Ignore
                }
            }
        }
        return ks;
    }

    protected static boolean isRenegotiationSupported(Tomcat tomcat) {
        String protocol = tomcat.getConnector().getProtocolHandlerClassName();
        if (protocol.contains("Apr")) {
            // Disabled by default in 1.1.20 windows binary (2010-07-27)
            return false;
        }
        return true;
    }

    protected static void configureClientCertContext(Tomcat tomcat) {
        TesterSupport.initSsl(tomcat);

        // Need a web application with a protected and unprotected URL
        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        Tomcat.addServlet(ctx, "simple", new SimpleServlet());
        ctx.addServletMapping("/unprotected", "simple");
        ctx.addServletMapping("/protected", "simple");

        // Security constraints
        SecurityCollection collection = new SecurityCollection();
        collection.addPattern("/protected");
        SecurityConstraint sc = new SecurityConstraint();
        sc.addAuthRole(ROLE);
        sc.addCollection(collection);
        ctx.addConstraint(sc);

        // Configure the Realm
        MapRealm realm = new MapRealm();
        realm.addUser("CN=user1, C=US", "not used");
        realm.addUserRole("CN=user1, C=US", ROLE);
        ctx.setRealm(realm);

        // Configure the authenticator
        LoginConfig lc = new LoginConfig();
        lc.setAuthMethod("CLIENT-CERT");
        ctx.setLoginConfig(lc);
        ctx.getPipeline().addValve(new SSLAuthenticator());
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
}
