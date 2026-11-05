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
import javax.net.ssl.X509KeyManager;

import org.apache.tomcat.util.net.jsse.JSSEKeyManager;

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
     * Returns the certificate chain for the given alias.
     *
     * @param alias The alias
     *
     * @return The certificate chain
     */
    X509Certificate[] getCertificateChain(String alias);


    /**
     * Returns the key managers that may serve the given certificate configuration, in preference order. Used by
     * {@link #getServedAlias} to resolve the alias actually served. The default returns an empty array (no key manager
     * known); implementations backed by one or more key managers override it to expose them.
     *
     * @param certificate The certificate configuration
     *
     * @return The key managers that may serve the certificate, possibly empty, never {@code null}
     */
    default X509KeyManager[] getServerKeyManagers(SSLHostConfigCertificate certificate) {
        return new X509KeyManager[0];
    }


    /**
     * Returns the alias of the certificate served for the given certificate configuration, for use with
     * {@link #getCertificateChain(String)}. The key managers from {@link #getServerKeyManagers} are consulted in
     * preference order and the first one that names an alias decides, just as JSSE only uses the first key manager:
     * <ul>
     * <li>A {@link JSSEKeyManager}, which Tomcat pins to an alias, serves that alias in every handshake. This can
     *     differ from {@link SSLHostConfigCertificate#getCertificateKeyAlias()}, for example after SunMSCAPI
     *     Windows-MY numbered-duplicate resolution.</li>
     * <li>Any other key manager chooses an alias itself, for example one from a FIPS key manager factory, which Tomcat
     *     does not wrap, or one over an in-memory key store that holds only the selected key. The configured alias
     *     (or {@link SSLUtilBase#DEFAULT_KEY_ALIAS} when none is configured) is used if that key manager holds it,
     *     otherwise the alias it would choose - for example for a key that an in-memory key store holds under another
     *     alias, or the synthetic alias generated by the {@code NewSunX509} key manager.</li>
     * </ul>
     * If no key manager names an alias, the configured (or default) alias is returned.
     *
     * @param certificate The certificate configuration
     *
     * @return The served alias, never {@code null}
     */
    default String getServedAlias(SSLHostConfigCertificate certificate) {
        String alias = certificate.getCertificateKeyAliasOrDefault();
        for (X509KeyManager keyManager : getServerKeyManagers(certificate)) {
            if (keyManager == null) {
                continue;
            }
            if (!(keyManager instanceof JSSEKeyManager)) {
                X509Certificate[] chain = keyManager.getCertificateChain(alias);
                if (chain != null && chain.length > 0) {
                    return alias;
                }
            }
            String chosenAlias = certificate.chooseServerAlias(keyManager);
            if (chosenAlias != null) {
                return chosenAlias;
            }
        }
        return alias;
    }


    /**
     * Returns the accepted issuers.
     *
     * @return The accepted issuers
     */
    X509Certificate[] getAcceptedIssuers();
}
