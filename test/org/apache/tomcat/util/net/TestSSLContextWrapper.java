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
import java.net.Socket;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Set;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests expiry monitoring for a supplied {@code javax.net.ssl.SSLContext} (see
 * {@link SSLUtil#createSSLContext}) whose custom key manager selects different aliases for the socket and engine
 * server-alias callbacks. Tomcat serves over an {@code SSLEngine}, so monitoring must track the engine selection, not
 * the socket one.
 */
public class TestSSLContextWrapper {

    private static final char[] PASSWORD = TesterSupport.JKS_PASS.toCharArray();

    private static final String ENGINE_KEYSTORE = "test/org/apache/tomcat/util/net/localhost-rsa-serverauth.jks";
    private static final String SOCKET_KEYSTORE = "test/org/apache/tomcat/util/net/localhost-rsa-serverauth2.jks";

    private static final X509TrustManager TRUST = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // No-op: the test never validates a peer.
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // No-op: the test never validates a peer.
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    /*
     * A key manager that deliberately disagrees between the socket and engine server-alias callbacks, as the JDK
     * contract permits. The engine callback reflects the alias an SSLEngine handshake actually serves.
     */
    private static final class SplitKeyManager extends X509ExtendedKeyManager {
        private final X509KeyManager delegate;

        SplitKeyManager(X509KeyManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return "RSA".equals(keyType) ? "socket" : null;
        }

        @Override
        public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
            return "RSA".equals(keyType) ? "engine" : null;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return delegate.getCertificateChain(alias);
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return delegate.getPrivateKey(alias);
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return delegate.getServerAliases(keyType, issuers);
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return delegate.getClientAliases(keyType, issuers);
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            return delegate.chooseClientAlias(keyType, issuers, socket);
        }
    }

    private static void copyKey(KeyStore target, String alias, String keystoreFile) throws Exception {
        KeyStore source = KeyStore.getInstance(new File(keystoreFile), PASSWORD);
        String original = source.aliases().nextElement();
        target.setKeyEntry(alias, source.getKey(original, PASSWORD), PASSWORD, source.getCertificateChain(original));
    }

    @Test
    public void testExpiryMonitoringTracksEngineServedCertificate() throws Exception {
        KeyStore store = KeyStore.getInstance("JKS");
        store.load(null, null);
        copyKey(store, "engine", ENGINE_KEYSTORE);
        copyKey(store, "socket", SOCKET_KEYSTORE);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(store, PASSWORD);
        X509ExtendedKeyManager keyManager = new SplitKeyManager((X509KeyManager) kmf.getKeyManagers()[0]);

        javax.net.ssl.SSLContext jsse = javax.net.ssl.SSLContext.getInstance("TLS");
        jsse.init(new KeyManager[] { keyManager }, new TrustManager[] { TRUST }, null);

        SSLContext context = SSLUtil.createSSLContext(jsse, keyManager, TRUST);
        SSLHostConfig host = new SSLHostConfig();
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(host, SSLHostConfigCertificate.Type.RSA);
        certificate.setCertificateKeyAlias("engine");
        certificate.setSslContext(context);
        host.addCertificate(certificate);

        X509Certificate engineCertificate = context.getCertificateChain("engine")[0];
        X509Certificate socketCertificate = context.getCertificateChain("socket")[0];
        Assert.assertNotEquals("Fixtures must provide two distinct certificates", engineCertificate,
                socketCertificate);

        Set<X509Certificate> monitored = host.certificatesExpiringBefore(new Date(Long.MAX_VALUE));

        Assert.assertTrue("The engine-served certificate must be monitored for expiry",
                monitored.contains(engineCertificate));
        Assert.assertFalse("The socket-selected certificate must not be monitored",
                monitored.contains(socketCertificate));
    }
}
