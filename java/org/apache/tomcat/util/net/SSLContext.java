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

import java.security.KeyManagementException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;

/**
 * This interface is needed to override the default SSLContext class to allow SSL implementation pluggability without
 * having to use JCE. With regular JSSE it will do nothing but delegate to the SSLContext.
 */
public interface SSLContext {

    /**
     * Initializes the SSL context with the given key managers, trust managers, and random number generator.
     *
     * @param kms The key managers
     * @param tms The trust managers
     * @param sr The secure random number generator
     * @throws KeyManagementException if initialization fails
     */
    void init(KeyManager[] kms, TrustManager[] tms, SecureRandom sr) throws KeyManagementException;

    /**
     * Destroys this SSL context and releases any associated resources.
     */
    void destroy();

    /**
     * Returns the server session context.
     *
     * @return The server session context
     */
    SSLSessionContext getServerSessionContext();

    /**
     * Creates a new SSL engine.
     *
     * @return The new SSL engine
     */
    SSLEngine createSSLEngine();

    /**
     * Creates a new SSL engine for the requested mode.
     *
     * @param clientMode {@code true} for client mode, otherwise server mode
     *
     * @return The new SSL engine
     */
    default SSLEngine createSSLEngine(boolean clientMode) {
        SSLEngine result = createSSLEngine();
        result.setUseClientMode(clientMode);
        return result;
    }

    /**
     * Returns the server socket factory.
     *
     * @return The server socket factory
     */
    SSLServerSocketFactory getServerSocketFactory();

    /**
     * Returns the supported SSL parameters.
     *
     * @return The supported SSL parameters
     */
    SSLParameters getSupportedSSLParameters();

    /**
     * Returns the certificate chain for the given key store alias. This is a low-level lookup by exact alias; callers
     * that want the certificate actually served for a configured {@link SSLHostConfigCertificate} should use
     * {@link #getCertificateChain(SSLHostConfigCertificate)}, which resolves the served alias (it can differ from the
     * configured alias, for example after SunMSCAPI Windows-MY numbered-duplicate resolution).
     *
     * @param alias The alias
     *
     * @return The certificate chain
     */
    X509Certificate[] getCertificateChain(String alias);


    /**
     * Returns the alias the key manager would select to serve for the given certificate configuration during a
     * handshake. This can differ from {@link SSLHostConfigCertificate#getCertificateKeyAlias()}, for example after
     * SunMSCAPI Windows-MY numbered-duplicate resolution. The default returns the configured alias (or
     * {@link SSLUtilBase#DEFAULT_KEY_ALIAS} when none is configured); implementations backed by a key manager override
     * it to resolve the alias actually served.
     *
     * @param certificate The certificate configuration
     *
     * @return The served alias, never {@code null}
     */
    default String getServedAlias(SSLHostConfigCertificate certificate) {
        String alias = certificate.getCertificateKeyAlias();
        return alias == null ? SSLUtilBase.DEFAULT_KEY_ALIAS : alias;
    }


    /**
     * Returns the certificate chain actually served for the given certificate configuration. Unlike
     * {@link #getCertificateChain(String)}, which looks up a fixed alias, this uses {@link #getServedAlias}, so callers
     * such as the certificate reload check observe the certificate that is really in use. This can differ from the
     * configured alias, for example after SunMSCAPI Windows-MY duplicate resolution.
     *
     * @param certificate The certificate configuration
     *
     * @return The served certificate chain, or {@code null} if none can be determined
     */
    default X509Certificate[] getCertificateChain(SSLHostConfigCertificate certificate) {
        return getCertificateChain(getServedAlias(certificate));
    }

    /**
     * Returns the accepted issuers.
     *
     * @return The accepted issuers
     */
    X509Certificate[] getAcceptedIssuers();
}
