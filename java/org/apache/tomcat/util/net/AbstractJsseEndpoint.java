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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSessionContext;

import org.apache.tomcat.util.net.SSLHostConfig.Type;
import org.apache.tomcat.util.net.jsse.openssl.Cipher;
import org.apache.tomcat.util.net.openssl.OpenSSLImplementation;

public abstract class AbstractJsseEndpoint<S> extends AbstractEndpoint<S> {

    private String sslImplementationName = null;
    private int sniParseLimit = 64 * 1024;

    private SSLImplementation sslImplementation = null;


    public String getSslImplementationName() {
        return sslImplementationName;
    }


    public void setSslImplementationName(String s) {
        this.sslImplementationName = s;
    }


    public SSLImplementation getSslImplementation() {
        return sslImplementation;
    }


    public int getSniParseLimit() {
        return sniParseLimit;
    }


    public void setSniParseLimit(int sniParseLimit) {
        this.sniParseLimit = sniParseLimit;
    }


    @Override
    protected Type getSslConfigType() {
        // TODO: Add configuration to allow the OpenSSLImplementation to optionally use the JSSE configuration
        // (it should still default to OpenSSL style since it is the most logical and straightforward)
        if (OpenSSLImplementation.IMPLEMENTATION_NAME.equals(sslImplementationName)) {
            return SSLHostConfig.Type.OPENSSL;
        } else {
            return SSLHostConfig.Type.JSSE;
        }
    }


    protected void initialiseSsl() throws Exception {
        if (isSSLEnabled()) {
            sslImplementation = SSLImplementation.getInstance(getSslImplementationName());

            for (SSLHostConfig sslHostConfig : sslHostConfigs.values()) {
                for (SSLHostConfigCertificate certificate : sslHostConfig.getCertificates(true)) {
                    SSLUtil sslUtil = sslImplementation.getSSLUtil(sslHostConfig, certificate);

                    SSLContext sslContext = sslUtil.createSSLContext(negotiableProtocols);
                    sslContext.init(sslUtil.getKeyManagers(), sslUtil.getTrustManagers(), null);

                    SSLSessionContext sessionContext = sslContext.getServerSessionContext();
                    if (sessionContext != null) {
                        sslUtil.configureSessionContext(sessionContext);
                    }
                    SSLContextWrapper sslContextWrapper = new SSLContextWrapper(sslContext, sslUtil);
                    certificate.setSslContextWrapper(sslContextWrapper);
                }
            }

        }
    }


    protected SSLEngine createSSLEngine(String sniHostName, List<Cipher> clientRequestedCiphers) {
        SSLHostConfig sslHostConfig = getSSLHostConfig(sniHostName);

        SSLHostConfigCertificate certificate = selectCertificate(sslHostConfig, clientRequestedCiphers);

        SSLContextWrapper sslContextWrapper = certificate.getSslContextWrapper();
        if (sslContextWrapper == null) {
            throw new IllegalStateException(
                    sm.getString("endpoint.jsse.noSslContext", sniHostName));
        }

        SSLEngine engine = sslContextWrapper.getSSLContext().createSSLEngine();
        switch (sslHostConfig.getCertificateVerification()) {
        case NONE:
            engine.setNeedClientAuth(false);
            engine.setWantClientAuth(false);
            break;
        case OPTIONAL:
        case OPTIONAL_NO_CA:
            engine.setWantClientAuth(true);
            break;
        case REQUIRED:
            engine.setNeedClientAuth(true);
            break;
        }
        engine.setUseClientMode(false);
        engine.setEnabledCipherSuites(sslContextWrapper.getEnabledCiphers());
        engine.setEnabledProtocols(sslContextWrapper.getEnabledProtocols());

        SSLParameters sslParameters = engine.getSSLParameters();
        sslParameters.setUseCipherSuitesOrder(sslHostConfig.getHonorCipherOrder());
        // In case the getter returns a defensive copy
        engine.setSSLParameters(sslParameters);

        return engine;
    }


    private SSLHostConfigCertificate selectCertificate(
            SSLHostConfig sslHostConfig, List<Cipher> clientCiphers) {

        Set<SSLHostConfigCertificate> certificates = sslHostConfig.getCertificates(true);
        if (certificates.size() == 1) {
            return certificates.iterator().next();
        }

        LinkedHashSet<Cipher> serverCiphers = sslHostConfig.getCipherList();

        List<Cipher> candidateCiphers = new ArrayList<>();
        if (sslHostConfig.getHonorCipherOrder()) {
            candidateCiphers.addAll(serverCiphers);
            candidateCiphers.retainAll(clientCiphers);
        } else {
            candidateCiphers.addAll(clientCiphers);
            candidateCiphers.retainAll(serverCiphers);
        }

        Iterator<Cipher> candidateIter = candidateCiphers.iterator();
        while (candidateIter.hasNext()) {
            Cipher candidate = candidateIter.next();
            for (SSLHostConfigCertificate certificate : certificates) {
                if (certificate.getType().isCompatibleWith(candidate.getAu())) {
                    return certificate;
                }
            }
        }

        // No matches. Just return the first certificate. The handshake will
        // then fail due to no matching ciphers.
        return certificates.iterator().next();
    }


    @Override
    public void unbind() throws Exception {
        for (SSLHostConfig sslHostConfig : sslHostConfigs.values()) {
            for (SSLHostConfigCertificate certificate : sslHostConfig.getCertificates(true)) {
                certificate.setSslContextWrapper(null);
            }
        }
    }


    static class SSLContextWrapper {

        private final SSLContext sslContext;
        private final String[] enabledCiphers;
        private final String[] enabledProtocols;

        public SSLContextWrapper(SSLContext sslContext, SSLUtil sslUtil) {
            this.sslContext = sslContext;
            // Determine which cipher suites and protocols to enable
            enabledCiphers = sslUtil.getEnableableCiphers(sslContext);
            enabledProtocols = sslUtil.getEnableableProtocols(sslContext);
        }

        public SSLContext getSSLContext() { return sslContext;}
        public String[] getEnabledCiphers() { return enabledCiphers; }
        public String[] getEnabledProtocols() { return enabledProtocols; }
    }
}
