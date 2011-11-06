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
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
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
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.deploy.SecurityCollection;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.startup.TestTomcat.MapRealm;
import org.apache.catalina.startup.Tomcat;

public final class TesterSupport {

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

    protected static void initSsl(Tomcat tomcat) {
        initSsl(tomcat, "localhost.jks", null, null);
    }

    protected static void initSsl(Tomcat tomcat, String keystore,
            String keystorePass, String keyPass) {

        String protocol = tomcat.getConnector().getProtocolHandlerClassName();
        if (protocol.indexOf("Apr") == -1) {
            Connector connector = tomcat.getConnector();
            connector.setProperty("sslProtocol", "tls");
            File keystoreFile =
                new File("test/org/apache/tomcat/util/net/" + keystore);
            connector.setAttribute("keystoreFile",
                    keystoreFile.getAbsolutePath());
            File truststoreFile = new File(
                    "test/org/apache/tomcat/util/net/ca.jks");
            connector.setAttribute("truststoreFile",
                    truststoreFile.getAbsolutePath());
            if (keystorePass != null) {
                connector.setAttribute("keystorePass", keystorePass);
            }
            if (keyPass != null) {
                connector.setAttribute("keyPass", keyPass);
            }
        } else {
            File keystoreFile = new File(
                    "test/org/apache/tomcat/util/net/localhost-cert.pem");
            tomcat.getConnector().setAttribute("SSLCertificateFile",
                    keystoreFile.getAbsolutePath());
            keystoreFile = new File(
                    "test/org/apache/tomcat/util/net/localhost-key.pem");
            tomcat.getConnector().setAttribute("SSLCertificateKeyFile",
                    keystoreFile.getAbsolutePath());
        }
        tomcat.getConnector().setSecure(true);
        tomcat.getConnector().setProperty("SSLEnabled", "true");
    }

    protected static KeyManager[] getUser1KeyManagers() throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(getKeyStore("test/org/apache/tomcat/util/net/user1.jks"),
                "changeit".toCharArray());
        return kmf.getKeyManagers();
    }

    protected static TrustManager[] getTrustManagers() throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(getKeyStore("test/org/apache/tomcat/util/net/ca.jks"));
        return tmf.getTrustManagers();
    }


    protected static void configureClientSsl() {
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(TesterSupport.getUser1KeyManagers(),
                    TesterSupport.getTrustManagers(),
                    null);
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(
                    new TesterSSLSocketFactory(sc.getSocketFactory()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static KeyStore getKeyStore(String keystore) throws Exception {
        File keystoreFile = new File(keystore);
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
        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        Tomcat.addServlet(ctx, "simple", new SimpleServlet());
        ctx.addServletMapping("/unprotected", "simple");
        ctx.addServletMapping("/protected", "simple");

        // Security constraints
        SecurityCollection collection = new SecurityCollection();
        collection.addPattern("/protected");
        SecurityConstraint sc = new SecurityConstraint();
        sc.addAuthRole("testrole");
        sc.addCollection(collection);
        ctx.addConstraint(sc);

        // Configure the Realm
        MapRealm realm = new MapRealm();
        realm.addUser("CN=user1, C=US", "not used");
        realm.addUserRole("CN=user1, C=US", "testrole");
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

    private static class TesterSSLSocketFactory
            extends SSLSocketFactory {

        private SSLSocketFactory factory;

        public TesterSSLSocketFactory(SSLSocketFactory factory) {
            this.factory = factory;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return factory.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return factory.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket socket, String s, int i, boolean flag)
                throws IOException {
            SSLSocket result =
                (SSLSocket) factory.createSocket(socket, s, i, flag);
            result.setEnabledProtocols(new String[] { "SSLv3" } );
            return result;
        }

        @Override
        public Socket createSocket(String s, int i) throws IOException,
                UnknownHostException {
            SSLSocket result = (SSLSocket) factory.createSocket(s, i);
            result.setEnabledProtocols(new String[] { "SSLv3" } );
            return result;
        }

        @Override
        public Socket createSocket(String s, int i, InetAddress inetaddress,
                int j) throws IOException, UnknownHostException {
            SSLSocket result =
                (SSLSocket) factory.createSocket(s, i, inetaddress, j);
            result.setEnabledProtocols(new String[] { "SSLv3" } );
            return result;
        }

        @Override
        public Socket createSocket(InetAddress inetaddress, int i)
                throws IOException {
            SSLSocket result = (SSLSocket) factory.createSocket(inetaddress, i);
            result.setEnabledProtocols(new String[] { "SSLv3" } );
            return result;
        }

        @Override
        public Socket createSocket(InetAddress inetaddress, int i,
                InetAddress inetaddress1, int j) throws IOException {
            SSLSocket result = (SSLSocket) factory.createSocket(
                    inetaddress, i, inetaddress1, j);
            result.setEnabledProtocols(new String[] { "SSLv3" } );
            return result;
        }
    }
}
