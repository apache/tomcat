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

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class TokenStreamProvider extends AbstractStreamProvider {

    private static final Log log = LogFactory.getLog(TokenStreamProvider.class);

    private String token;
    private String caCertFile;
    private SSLSocketFactory factory;

    TokenStreamProvider(String token, String caCertFile) {
        this.token = token;
        this.caCertFile = caCertFile;
    }

    @Override
    public InputStream openStream(String url, Map<String, String> headers, int connectTimeout, int readTimeout)
            throws IOException {
        // Set token header
        if (token != null) {
            headers.put("Authorization", "Bearer " + token);
        }

        // Open HTTP connection
        URLConnection connection = openConnection(url, headers, connectTimeout, readTimeout);

        if (connection instanceof HttpsURLConnection) {
            HttpsURLConnection httpsConnection = HttpsURLConnection.class.cast(connection);
            //httpsConnection.setHostnameVerifier(InsecureStreamProvider.INSECURE_HOSTNAME_VERIFIER);
            httpsConnection.setSSLSocketFactory(getSSLSocketFactory());
            if (log.isDebugEnabled()) {
                log.debug(String.format("Using HttpsURLConnection with SSLSocketFactory [%s] for url [%s].", factory, url));
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Using URLConnection for url [%s].", url));
            }
        }

        try {
            return connection.getInputStream();
        } catch (IOException e) {
            throw new IOException(sm.getString("tokenStream.failedConnection", url, token, caCertFile), e);
        }
    }

    private TrustManager[] configureCaCert(String caCertFile) throws Exception {
        if (caCertFile != null) {
            try {
                InputStream pemInputStream = new BufferedInputStream(new FileInputStream(caCertFile));
                try {
                    CertificateFactory certFactory = CertificateFactory.getInstance("X509");
                    X509Certificate cert = (X509Certificate)certFactory.generateCertificate(pemInputStream);

                    KeyStore trustStore = KeyStore.getInstance("JKS");
                    trustStore.load(null);

                    String alias = cert.getSubjectX500Principal().getName();
                    trustStore.setCertificateEntry(alias, cert);

                    TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    trustManagerFactory.init(trustStore);

                    return trustManagerFactory.getTrustManagers();
                } finally {
                    pemInputStream.close();
                }
            } catch (FileNotFoundException fnfe) {
                log.error(sm.getString("tokenStream.fileNotFound", caCertFile));
                throw fnfe;
            } catch (Exception e) {
                log.error(sm.getString("tokenStream.trustManagerError", caCertFile), e);
                throw e;
            }
        } else {
            log.warn(sm.getString("tokenStream.CACertUndefined"));
            return InsecureStreamProvider.INSECURE_TRUST_MANAGERS;
        }
    }

    private SSLSocketFactory getSSLSocketFactory() throws IOException {
        if(this.factory == null) {
            synchronized(this) {
                if(this.factory == null) {
                    try {
                        TrustManager[] trustManagers = configureCaCert(this.caCertFile);
                        SSLContext context = SSLContext.getInstance("TLS");
                        context.init(null, trustManagers, null);
                        this.factory = context.getSocketFactory();
                    } catch(Exception e) {
                        throw new IOException(e);
                    }
                }
            }
        }
        return this.factory;
    }

}