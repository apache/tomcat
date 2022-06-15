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
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public abstract class AbstractStreamProvider implements StreamProvider {
    private static final Log log = LogFactory.getLog(AbstractStreamProvider.class);
    protected static final StringManager sm = StringManager.getManager(AbstractStreamProvider.class);

    protected static final TrustManager[] INSECURE_TRUST_MANAGERS = new TrustManager[] {
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            }
        };

    /**
     * @return the socket factory, or null if not needed
     */
    protected abstract SSLSocketFactory getSocketFactory();

    /**
     * Open URL connection to the specified URL.
     * @param url the url
     * @param headers the headers map
     * @param connectTimeout connection timeout in ms
     * @param readTimeout read timeout in ms
     * @return the URL connection
     * @throws IOException when an error occurs
     */
    public URLConnection openConnection(String url, Map<String, String> headers, int connectTimeout, int readTimeout) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(String.format("%s opening connection: url [%s], headers [%s], connectTimeout [%s], readTimeout [%s]",
                    getClass().getSimpleName(), url, headers, Integer.toString(connectTimeout), Integer.toString(readTimeout)));
        }
        URLConnection connection = new URL(url).openConnection();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.addRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        if (connectTimeout < 0 || readTimeout < 0) {
            throw new IllegalArgumentException(
                String.format("Neither connectTimeout [%s] nor readTimeout [%s] can be less than 0 for URLConnection.",
                        Integer.toString(connectTimeout), Integer.toString(readTimeout)));
        }
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        return connection;
    }

    @Override
    public InputStream openStream(String url, Map<String, String> headers,
            int connectTimeout, int readTimeout) throws IOException {
        URLConnection connection = openConnection(url, headers, connectTimeout, readTimeout);
        if (connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(getSocketFactory());
            if (log.isDebugEnabled()) {
                log.debug(String.format("Using HttpsURLConnection with SSLSocketFactory [%s] for url [%s].", getSocketFactory(), url));
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Using URLConnection for url [%s].", url));
            }
        }
        return connection.getInputStream();
    }

    protected static TrustManager[] configureCaCert(String caCertFile) throws Exception {
        if (caCertFile != null) {
            try (InputStream pemInputStream = new BufferedInputStream(new FileInputStream(caCertFile))) {
                CertificateFactory certFactory = CertificateFactory.getInstance("X509");

                KeyStore trustStore = KeyStore.getInstance("JKS");
                trustStore.load(null);

                Collection<? extends Certificate> c = certFactory.generateCertificates(pemInputStream);
                for (Certificate certificate : c) {
                    X509Certificate cert = (X509Certificate) certificate;
                    String alias = cert.getSubjectX500Principal().getName();
                    trustStore.setCertificateEntry(alias, cert);
                }

                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(trustStore);

                return trustManagerFactory.getTrustManagers();
            } catch (FileNotFoundException fnfe) {
                log.error(sm.getString("abstractStream.fileNotFound", caCertFile));
                throw fnfe;
            } catch (Exception e) {
                log.error(sm.getString("abstractStream.trustManagerError", caCertFile));
                throw e;
            }
        } else {
            log.warn(sm.getString("abstractStream.CACertUndefined"));
            return InsecureStreamProvider.INSECURE_TRUST_MANAGERS;
        }
    }
}
