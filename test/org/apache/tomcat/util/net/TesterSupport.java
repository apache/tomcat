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
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.apache.catalina.startup.Tomcat;

public final class TesterSupport {
    
    protected static final boolean RFC_5746_SUPPORTED;

    static {
        boolean result = false;
        SSLContext context;
        try {
            context = SSLContext.getInstance("TLS");
            context.init(null, null, new SecureRandom());
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
        String protocol = tomcat.getConnector().getProtocolHandlerClassName();
        if (protocol.indexOf("Apr") == -1) {
            tomcat.getConnector().setProperty("sslProtocol", "tls");
            File keystoreFile = new File(
                    "test/org/apache/tomcat/util/net/localhost.jks");
            tomcat.getConnector().setAttribute("keystoreFile",
                    keystoreFile.getAbsolutePath());
            File truststoreFile = new File(
                    "test/org/apache/tomcat/util/net/ca.jks");
            tomcat.getConnector().setAttribute("truststoreFile",
                    truststoreFile.getAbsolutePath());
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
                    new java.security.SecureRandom());     
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(
                    sc.getSocketFactory());
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
}
