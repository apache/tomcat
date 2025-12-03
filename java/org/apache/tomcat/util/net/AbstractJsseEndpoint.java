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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.NetworkChannel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.net.openssl.ciphers.Cipher;
import org.apache.tomcat.util.net.openssl.ciphers.Group;
import org.apache.tomcat.util.net.openssl.ciphers.SignatureScheme;

public abstract class AbstractJsseEndpoint<S, U> extends AbstractEndpoint<S,U> {

    // Thread local use to pass additional arguments to createSSLEngine without changing the protected method signature
    static final ThreadLocal<List<String>> clientRequestedProtocolsThreadLocal = new ThreadLocal<>();
    static final ThreadLocal<List<Group>> clientSupportedGroupsThreadLocal = new ThreadLocal<>();
    static final ThreadLocal<List<SignatureScheme>> clientSignatureSchemesThreadLocal = new ThreadLocal<>();

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


    protected void initialiseSsl() throws Exception {
        if (isSSLEnabled()) {
            sslImplementation = SSLImplementation.getInstance(getSslImplementationName());

            for (SSLHostConfig sslHostConfig : sslHostConfigs.values()) {
                createSSLContext(sslHostConfig);
            }

            // Validate default SSLHostConfigName
            if (sslHostConfigs.get(getDefaultSSLHostConfigName()) == null) {
                throw new IllegalArgumentException(
                        sm.getString("endpoint.noSslHostConfig", getDefaultSSLHostConfigName(), getName()));
            }

        }
    }


    @Override
    protected void createSSLContext(SSLHostConfig sslHostConfig) throws IllegalArgumentException {

        boolean firstCertificate = true;
        for (SSLHostConfigCertificate certificate : sslHostConfig.getCertificates(true)) {
            SSLUtil sslUtil = sslImplementation.getSSLUtil(certificate);
            if (firstCertificate) {
                firstCertificate = false;
                sslHostConfig.setEnabledProtocols(sslUtil.getEnabledProtocols());
                sslHostConfig.setEnabledCiphers(sslUtil.getEnabledCiphers());
            }

            SSLContext sslContext = certificate.getSslContext();
            SSLContext sslContextGenerated = certificate.getSslContextGenerated();
            // Generate the SSLContext from configuration unless (e.g. embedded) an SSLContext has been provided.
            // Need to handle both initial configuration and reload.
            // Initial, SSLContext provided - sslContext will be non-null and sslContextGenerated will be null
            // Initial, SSLContext not provided - sslContext null and sslContextGenerated will be null
            // Reload, SSLContext provided - sslContext will be non-null and sslContextGenerated will be null
            // Reload, SSLContext not provided - sslContext non-null and equal to sslContextGenerated
            if (sslContext == null || sslContext == sslContextGenerated) {
                try {
                    sslContext = sslUtil.createSSLContext(negotiableProtocols);
                } catch (Exception e) {
                    throw new IllegalArgumentException(sm.getString("endpoint.errorCreatingSSLContext"), e);
                }

                certificate.setSslContextGenerated(sslContext);
            }

            logCertificate(certificate);
        }

    }


    protected SSLEngine createSSLEngine(String sniHostName, List<Cipher> clientRequestedCiphers,
            List<String> clientRequestedApplicationProtocols) {
        List<String> clientRequestedProtocols = clientRequestedProtocolsThreadLocal.get();
        if (clientRequestedProtocols == null) {
            clientRequestedProtocols = new ArrayList<String>();
        }
        List<Group> clientSupportedGroups = clientSupportedGroupsThreadLocal.get();
        if (clientSupportedGroups == null) {
            clientSupportedGroups = new ArrayList<Group>();
        }
        List<SignatureScheme> clientSignatureSchemes = clientSignatureSchemesThreadLocal.get();
        if (clientSignatureSchemes == null) {
            clientSignatureSchemes = new ArrayList<SignatureScheme>();
        }

        SSLHostConfig sslHostConfig = getSSLHostConfig(sniHostName);

        SSLHostConfigCertificate certificate = selectCertificate(sslHostConfig, clientRequestedCiphers,
                clientRequestedProtocols, clientSignatureSchemes);

        SSLContext sslContext = certificate.getSslContext();
        if (sslContext == null) {
            throw new IllegalStateException(sm.getString("endpoint.jsse.noSslContext", sniHostName));
        }

        SSLEngine engine = sslContext.createSSLEngine();
        engine.setUseClientMode(false);
        engine.setEnabledCipherSuites(sslHostConfig.getEnabledCiphers());
        engine.setEnabledProtocols(sslHostConfig.getEnabledProtocols());

        SSLParameters sslParameters = engine.getSSLParameters();
        sslParameters.setUseCipherSuitesOrder(sslHostConfig.getHonorCipherOrder());
        if (clientRequestedApplicationProtocols != null && !clientRequestedApplicationProtocols.isEmpty() &&
                !negotiableProtocols.isEmpty()) {
            // Only try to negotiate if both client and server have at least
            // one protocol in common
            // Note: Tomcat does not explicitly negotiate http/1.1
            List<String> commonProtocols = new ArrayList<>(negotiableProtocols);
            commonProtocols.retainAll(clientRequestedApplicationProtocols);
            if (!commonProtocols.isEmpty()) {
                String[] commonProtocolsArray = commonProtocols.toArray(new String[0]);
                sslParameters.setApplicationProtocols(commonProtocolsArray);
            }
        }
        // Merge server groups with the client groups
        if (JreCompat.isJre20Available()) {
            List<String> supportedGroups = new ArrayList<>();
            LinkedHashSet<Group> serverSupportedGroups = sslHostConfig.getGroupList();
            if (serverSupportedGroups != null) {
                if (!clientSupportedGroups.isEmpty()) {
                    for (Group group : clientSupportedGroups) {
                        if (serverSupportedGroups.contains(group)) {
                            supportedGroups.add(group.toString());
                        }
                    }
                } else {
                    for (Group group : serverSupportedGroups) {
                        supportedGroups.add(group.toString());
                    }
                }
                JreCompat.getInstance().setNamedGroupsMethod(sslParameters, supportedGroups.toArray(new String[0]));
            } else if (!clientSupportedGroups.isEmpty()) {
                for (Group group : clientSupportedGroups) {
                    supportedGroups.add(group.toString());
                }
                JreCompat.getInstance().setNamedGroupsMethod(sslParameters, supportedGroups.toArray(new String[0]));
            }
        }
        switch (sslHostConfig.getCertificateVerification()) {
            case NONE:
                sslParameters.setNeedClientAuth(false);
                sslParameters.setWantClientAuth(false);
                break;
            case OPTIONAL:
            case OPTIONAL_NO_CA:
                sslParameters.setWantClientAuth(true);
                break;
            case REQUIRED:
                sslParameters.setNeedClientAuth(true);
                break;
        }
        // The getter (at least in OpenJDK and derivatives) returns a defensive copy
        engine.setSSLParameters(sslParameters);

        return engine;
    }


    private SSLHostConfigCertificate selectCertificate(SSLHostConfig sslHostConfig, List<Cipher> clientCiphers,
            List<String> clientRequestedProtocols, List<SignatureScheme> clientSignatureSchemes) {

        Set<SSLHostConfigCertificate> certificates = sslHostConfig.getCertificates(true);
        if (certificates.size() == 1) {
            return certificates.iterator().next();
        }

        // Use signature algorithm for cipher matching with TLS 1.3
        if ((clientRequestedProtocols.contains(Constants.SSL_PROTO_TLSv1_3)) &&
                sslHostConfig.getProtocols().contains(Constants.SSL_PROTO_TLSv1_3)) {
            for (SignatureScheme signatureScheme : clientSignatureSchemes) {
                for (SSLHostConfigCertificate certificate : certificates) {
                    if (certificate.getType().isCompatibleWith(signatureScheme)) {
                        return certificate;
                    }
                }
            }
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

        for (Cipher candidate : candidateCiphers) {
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
            for (SSLHostConfigCertificate certificate : sslHostConfig.getCertificates()) {
                /*
                 * Only remove any generated SSLContext. If the SSLContext was provided it is left in place in case the
                 * endpoint is re-started.
                 */
                certificate.setSslContextGenerated(null);
            }
        }
    }


    protected abstract NetworkChannel getServerSocket();


    @Override
    protected final InetSocketAddress getLocalAddress() throws IOException {
        NetworkChannel serverSock = getServerSocket();
        if (serverSock == null) {
            return null;
        }
        SocketAddress sa = serverSock.getLocalAddress();
        if (sa instanceof InetSocketAddress) {
            return (InetSocketAddress) sa;
        }
        return null;
    }
}
