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

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Objects;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

/**
 * Wrapper class to simplify using a pre-configured {@code javax.net.ssl.SSLContext} instance with an
 * {@code SSLHostConfigCertificate}.
 */
class SSLContextWrapper implements SSLContext {

    private final javax.net.ssl.SSLContext sslContext;
    private final X509KeyManager keyManager;
    private final X509TrustManager trustManager;

    SSLContextWrapper(javax.net.ssl.SSLContext sslContext, X509KeyManager keyManager, X509TrustManager trustManager) {
        this.sslContext = Objects.requireNonNull(sslContext);
        this.keyManager = Objects.requireNonNull(keyManager);
        this.trustManager = Objects.requireNonNull(trustManager);
    }

    @Override
    public void init(KeyManager[] kms, TrustManager[] tms, SecureRandom sr) {
        // NO-OP as it is already initialized
    }

    @Override
    public void destroy() {

    }

    @Override
    public SSLSessionContext getServerSessionContext() {
        return sslContext.getServerSessionContext();
    }

    @Override
    public SSLEngine createSSLEngine() {
        return sslContext.createSSLEngine();
    }

    @Override
    public SSLServerSocketFactory getServerSocketFactory() {
        return sslContext.getServerSocketFactory();
    }

    @Override
    public SSLParameters getSupportedSSLParameters() {
        return sslContext.getSupportedSSLParameters();
    }

    @Override
    public X509Certificate[] getCertificateChain(String alias) {
        return keyManager.getCertificateChain(alias);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return trustManager.getAcceptedIssuers();
    }
}
