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

package org.apache.catalina.tribes.membership.cloud;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.jsse.PEMFile;

public class CertificateStreamProvider extends AbstractStreamProvider {

    private static final Log log = LogFactory.getLog(CertificateStreamProvider.class);

    private final SSLSocketFactory factory;

    CertificateStreamProvider(String clientCertFile, String clientKeyFile, String clientKeyPassword, String clientKeyAlgo, String caCertFile) throws Exception {
        char[] password = (clientKeyPassword != null) ? clientKeyPassword.toCharArray() : new char[0];
        KeyManager[] keyManagers = configureClientCert(clientCertFile, clientKeyFile, password, clientKeyAlgo);
        TrustManager[] trustManagers = configureCaCert(caCertFile);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers, trustManagers, null);
        factory = context.getSocketFactory();
    }

    @Override
    public InputStream openStream(String url, Map<String, String> headers, int connectTimeout, int readTimeout) throws IOException {
        URLConnection connection = openConnection(url, headers, connectTimeout, readTimeout);
        if (connection instanceof HttpsURLConnection) {
            HttpsURLConnection httpsConnection = HttpsURLConnection.class.cast(connection);
            //httpsConnection.setHostnameVerifier(InsecureStreamProvider.INSECURE_HOSTNAME_VERIFIER);
            httpsConnection.setSSLSocketFactory(factory);
            if (log.isDebugEnabled()) {
                log.debug(String.format("Using HttpsURLConnection with SSLSocketFactory [%s] for url [%s].", factory, url));
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Using URLConnection for url [%s].", url));
            }
        }
        return connection.getInputStream();
    }

    private static KeyManager[] configureClientCert(String clientCertFile, String clientKeyFile, char[] clientKeyPassword, String clientKeyAlgo) throws Exception {
        try (InputStream certInputStream = new FileInputStream(clientCertFile)) {
            CertificateFactory certFactory = CertificateFactory.getInstance("X509");
            X509Certificate cert = (X509Certificate)certFactory.generateCertificate(certInputStream);

            PEMFile pemFile = new PEMFile(clientKeyFile, new String(clientKeyPassword), clientKeyAlgo);
            PrivateKey privKey = pemFile.getPrivateKey();

            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(null,  null);

            String alias = cert.getSubjectX500Principal().getName();
            keyStore.setKeyEntry(alias, privKey, clientKeyPassword, new Certificate[]{cert});

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, clientKeyPassword);

            return keyManagerFactory.getKeyManagers();
        } catch (IOException e) {
            log.error(sm.getString("certificateStream.clientCertError", clientCertFile, clientKeyFile), e);
            throw e;
        }
    }

    private static TrustManager[] configureCaCert(String caCertFile) throws Exception {
        if (caCertFile != null) {
            try (InputStream pemInputStream = new FileInputStream(caCertFile)) {
                CertificateFactory certFactory = CertificateFactory.getInstance("X509");
                X509Certificate cert = (X509Certificate) certFactory.generateCertificate(pemInputStream);

                KeyStore trustStore = KeyStore.getInstance("JKS");
                trustStore.load(null);

                String alias = cert.getSubjectX500Principal().getName();
                trustStore.setCertificateEntry(alias, cert);

                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(trustStore);

                return trustManagerFactory.getTrustManagers();
            } catch (Exception e) {
                log.error(sm.getString("certificateStream.CACertError", caCertFile), e);
                throw e;
            }
        } else {
            log.warn(sm.getString("certificateStream.CACertUndefined"));
            return InsecureStreamProvider.INSECURE_TRUST_MANAGERS;
        }
    }

}
