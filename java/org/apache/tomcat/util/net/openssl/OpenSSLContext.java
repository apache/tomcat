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
package org.apache.tomcat.util.net.openssl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jni.CertificateVerifier;
import org.apache.tomcat.jni.Pool;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLContext;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.Constants;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.jsse.openssl.OpenSSLCipherConfigurationParser;
import org.apache.tomcat.util.res.StringManager;

public class OpenSSLContext implements org.apache.tomcat.util.net.SSLContext {

    private static final Log log = LogFactory.getLog(OpenSSLContext.class);

    // Note: this uses the main "net" package strings as many are common with APR
    private static final StringManager netSm = StringManager.getManager(AbstractEndpoint.class);
    private static final StringManager sm = StringManager.getManager(OpenSSLContext.class);

    private static final String defaultProtocol = "TLS";

    // http/1.1 with preceding length
    private static final byte[] ALPN_DEFAULT =
            new byte[] { 0x08, 0x68, 0x74, 0x74, 0x70, 0x2f, 0x31, 0x2e, 0x31 };

    private final SSLHostConfig sslHostConfig;
    private final SSLHostConfigCertificate certificate;
    private OpenSSLServerSessionContext sessionContext;

    private final List<String> negotiableProtocols;

    private List<String> ciphers = new ArrayList<>();

    public List<String> getCiphers() {
        return ciphers;
    }

    private String enabledProtocol;

    public String getEnabledProtocol() {
        return enabledProtocol;
    }

    public void setEnabledProtocol(String protocol) {
        enabledProtocol = (protocol == null) ? defaultProtocol : protocol;
    }

    private final long aprPool;
    protected final long ctx;

    @SuppressWarnings("unused")
    private volatile int aprPoolDestroyed;
    private static final AtomicIntegerFieldUpdater<OpenSSLContext> DESTROY_UPDATER
            = AtomicIntegerFieldUpdater.newUpdater(OpenSSLContext.class, "aprPoolDestroyed");
    static final CertificateFactory X509_CERT_FACTORY;
    private boolean initialized = false;

    static {
        try {
            X509_CERT_FACTORY = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new IllegalStateException(sm.getString("openssl.X509FactoryError"), e);
        }
    }

    public OpenSSLContext(SSLHostConfig sslHostConfig, SSLHostConfigCertificate certificate, List<String> negotiableProtocols)
            throws SSLException {
        this.sslHostConfig = sslHostConfig;
        this.certificate = certificate;
        aprPool = Pool.create(0);
        boolean success = false;
        try {
            if (SSLHostConfig.adjustRelativePath(certificate.getCertificateFile()) == null) {
                // This is required
                throw new Exception(netSm.getString("endpoint.apr.noSslCertFile"));
            }

            // SSL protocol
            int value = SSL.SSL_PROTOCOL_NONE;
            if (sslHostConfig.getProtocols().size() == 0) {
                value = SSL.SSL_PROTOCOL_ALL;
            } else {
                for (String protocol : sslHostConfig.getProtocols()) {
                    if (Constants.SSL_PROTO_SSLv2Hello.equalsIgnoreCase(protocol)) {
                        // NO-OP. OpenSSL always supports SSLv2Hello
                    } else if (Constants.SSL_PROTO_SSLv2.equalsIgnoreCase(protocol)) {
                        value |= SSL.SSL_PROTOCOL_SSLV2;
                    } else if (Constants.SSL_PROTO_SSLv3.equalsIgnoreCase(protocol)) {
                        value |= SSL.SSL_PROTOCOL_SSLV3;
                    } else if (Constants.SSL_PROTO_TLSv1.equalsIgnoreCase(protocol)) {
                        value |= SSL.SSL_PROTOCOL_TLSV1;
                    } else if (Constants.SSL_PROTO_TLSv1_1.equalsIgnoreCase(protocol)) {
                        value |= SSL.SSL_PROTOCOL_TLSV1_1;
                    } else if (Constants.SSL_PROTO_TLSv1_2.equalsIgnoreCase(protocol)) {
                        value |= SSL.SSL_PROTOCOL_TLSV1_2;
                    } else if (Constants.SSL_PROTO_ALL.equalsIgnoreCase(protocol)) {
                        value |= SSL.SSL_PROTOCOL_ALL;
                    } else {
                        // Protocol not recognized, fail to start as it is safer than
                        // continuing with the default which might enable more than the
                        // is required
                        throw new Exception(netSm.getString(
                                "endpoint.apr.invalidSslProtocol", protocol));
                    }
                }
            }

            // Create SSL Context
            try {
                ctx = SSLContext.make(aprPool, value, SSL.SSL_MODE_SERVER);
            } catch (Exception e) {
                // If the sslEngine is disabled on the AprLifecycleListener
                // there will be an Exception here but there is no way to check
                // the AprLifecycleListener settings from here
                throw new Exception(
                        netSm.getString("endpoint.apr.failSslContextMake"), e);
            }

            this.negotiableProtocols = negotiableProtocols;

            success = true;
        } catch(Exception e) {
            throw new SSLException(sm.getString("openssl.errorSSLCtxInit"), e);
        } finally {
            if (!success) {
                destroyPools();
            }
        }
    }

    private byte[] buildAlpnConfig(List<String> protocols) {
        /*
         * The expected format is zero or more of the following:
         * - Single byte for size
         * - Sequence of size bytes for the identifier
         */
        byte[][] protocolsBytes = new byte[protocols.size()][];
        int i = 0;
        int size = 0;
        for (String protocol : protocols) {
            protocolsBytes[i] = protocol.getBytes(StandardCharsets.UTF_8);
            size += protocolsBytes[i].length;
            // And one byte to store the size
            size++;
            i++;
        }

        size += ALPN_DEFAULT.length;

        byte[] result = new byte[size];
        int pos = 0;
        for (byte[] protocolBytes : protocolsBytes) {
            result[pos++] = (byte) (0xff & protocolBytes.length);
            System.arraycopy(protocolBytes, 0, result, pos, protocolBytes.length);
            pos += protocolBytes.length;
        }

        System.arraycopy(ALPN_DEFAULT, 0, result, pos, ALPN_DEFAULT.length);

        return result;
    }

    private void destroyPools() {
        // Guard against multiple destroyPools() calls triggered by construction exception and finalize() later
        if (aprPool != 0 && DESTROY_UPDATER.compareAndSet(this, 0, 1)) {
            Pool.destroy(aprPool);
        }
    }

    /**
     * Setup the SSL_CTX
     *
     * @param kms Must contain a KeyManager of the type
     * {@code OpenSSLKeyManager}
     * @param tms
     * @param sr Is not used for this implementation.
     */
    @Override
    public synchronized void init(KeyManager[] kms, TrustManager[] tms, SecureRandom sr) {
        if (initialized) {
            log.warn(sm.getString("openssl.doubleInit"));
            return;
        }
        try {
            boolean legacyRenegSupported = false;
            try {
                legacyRenegSupported = SSL.hasOp(SSL.SSL_OP_ALLOW_UNSAFE_LEGACY_RENEGOTIATION);
                if (legacyRenegSupported)
                    if (sslHostConfig.getInsecureRenegotiation()) {
                        SSLContext.setOptions(ctx, SSL.SSL_OP_ALLOW_UNSAFE_LEGACY_RENEGOTIATION);
                    } else {
                        SSLContext.clearOptions(ctx, SSL.SSL_OP_ALLOW_UNSAFE_LEGACY_RENEGOTIATION);
                    }
            } catch (UnsatisfiedLinkError e) {
                // Ignore
            }
            if (!legacyRenegSupported) {
                // OpenSSL does not support unsafe legacy renegotiation.
                log.warn(netSm.getString("endpoint.warn.noInsecureReneg",
                                      SSL.versionString()));
            }
            // Use server's preference order for ciphers (rather than
            // client's)
            boolean orderCiphersSupported = false;
            try {
                orderCiphersSupported = SSL.hasOp(SSL.SSL_OP_CIPHER_SERVER_PREFERENCE);
                if (orderCiphersSupported) {
                    if (sslHostConfig.getHonorCipherOrder()) {
                        SSLContext.setOptions(ctx, SSL.SSL_OP_CIPHER_SERVER_PREFERENCE);
                    } else {
                        SSLContext.clearOptions(ctx, SSL.SSL_OP_CIPHER_SERVER_PREFERENCE);
                    }
                }
            } catch (UnsatisfiedLinkError e) {
                // Ignore
            }
            if (!orderCiphersSupported) {
                // OpenSSL does not support ciphers ordering.
                log.warn(netSm.getString("endpoint.warn.noHonorCipherOrder",
                                      SSL.versionString()));
            }

            // Disable compression if requested
            boolean disableCompressionSupported = false;
            try {
                disableCompressionSupported = SSL.hasOp(SSL.SSL_OP_NO_COMPRESSION);
                if (disableCompressionSupported) {
                    if (sslHostConfig.getDisableCompression()) {
                        SSLContext.setOptions(ctx, SSL.SSL_OP_NO_COMPRESSION);
                    } else {
                        SSLContext.clearOptions(ctx, SSL.SSL_OP_NO_COMPRESSION);
                    }
                }
            } catch (UnsatisfiedLinkError e) {
                // Ignore
            }
            if (!disableCompressionSupported) {
                // OpenSSL does not support ciphers ordering.
                log.warn(netSm.getString("endpoint.warn.noDisableCompression",
                                      SSL.versionString()));
            }

            // Disable TLS Session Tickets (RFC4507) to protect perfect forward secrecy
            boolean disableSessionTicketsSupported = false;
            try {
                disableSessionTicketsSupported = SSL.hasOp(SSL.SSL_OP_NO_TICKET);
                if (disableSessionTicketsSupported) {
                    if (sslHostConfig.getDisableSessionTickets()) {
                        SSLContext.setOptions(ctx, SSL.SSL_OP_NO_TICKET);
                    } else {
                        SSLContext.clearOptions(ctx, SSL.SSL_OP_NO_TICKET);
                    }
                }
            } catch (UnsatisfiedLinkError e) {
                // Ignore
            }
            if (!disableSessionTicketsSupported) {
                // OpenSSL is too old to support TLS Session Tickets.
                log.warn(netSm.getString("endpoint.warn.noDisableSessionTickets",
                                      SSL.versionString()));
            }

            // Set session cache size, if specified
            if (sslHostConfig.getSessionCacheSize() > 0) {
                SSLContext.setSessionCacheSize(ctx, sslHostConfig.getSessionCacheSize());
            } else {
                // Get the default session cache size using SSLContext.setSessionCacheSize()
                long sessionCacheSize = SSLContext.setSessionCacheSize(ctx, 20480);
                // Revert the session cache size to the default value.
                SSLContext.setSessionCacheSize(ctx, sessionCacheSize);
            }

            // Set session timeout, if specified
            if (sslHostConfig.getSessionTimeout() > 0) {
                SSLContext.setSessionCacheTimeout(ctx, sslHostConfig.getSessionTimeout());
            } else {
                // Get the default session timeout using SSLContext.setSessionCacheTimeout()
                long sessionTimeout = SSLContext.setSessionCacheTimeout(ctx, 300);
                // Revert the session timeout to the default value.
                SSLContext.setSessionCacheTimeout(ctx, sessionTimeout);
            }

            // List the ciphers that the client is permitted to negotiate
            String ciphers = sslHostConfig.getCiphers();
            if (!("ALL".equals(ciphers)) && ciphers.indexOf(":") == -1) {
                StringTokenizer tok = new StringTokenizer(ciphers, ",");
                this.ciphers = new ArrayList<>();
                while (tok.hasMoreTokens()) {
                    String token = tok.nextToken().trim();
                    if (!"".equals(token)) {
                        this.ciphers.add(token);
                    }
                }
                ciphers = CipherSuiteConverter.toOpenSsl(ciphers);
            } else {
                this.ciphers = OpenSSLCipherConfigurationParser.parseExpression(ciphers);
            }
            SSLContext.setCipherSuite(ctx, ciphers);
            // Load Server key and certificate
            SSLContext.setCertificate(ctx,
                    SSLHostConfig.adjustRelativePath(certificate.getCertificateFile()),
                    SSLHostConfig.adjustRelativePath(certificate.getCertificateKeyFile()),
                    certificate.getCertificateKeyPassword(), SSL.SSL_AIDX_RSA);
            // Support Client Certificates
            SSLContext.setCACertificate(ctx,
                    SSLHostConfig.adjustRelativePath(sslHostConfig.getCaCertificateFile()),
                    SSLHostConfig.adjustRelativePath(sslHostConfig.getCaCertificatePath()));
            // Set revocation
            SSLContext.setCARevocation(ctx,
                    SSLHostConfig.adjustRelativePath(
                            sslHostConfig.getCertificateRevocationListFile()),
                    SSLHostConfig.adjustRelativePath(
                            sslHostConfig.getCertificateRevocationListPath()));
            // Client certificate verification
            int value = 0;
            switch (sslHostConfig.getCertificateVerification()) {
            case NONE:
                value = SSL.SSL_CVERIFY_NONE;
                break;
            case OPTIONAL:
                value = SSL.SSL_CVERIFY_OPTIONAL;
                break;
            case OPTIONAL_NO_CA:
                value = SSL.SSL_CVERIFY_OPTIONAL_NO_CA;
                break;
            case REQUIRED:
                value = SSL.SSL_CVERIFY_REQUIRE;
                break;
            }
            SSLContext.setVerify(ctx, value, sslHostConfig.getCertificateVerificationDepth());

            if (tms != null) {
                final X509TrustManager manager = chooseTrustManager(tms);
                SSLContext.setCertVerifyCallback(ctx, new CertificateVerifier() {
                    @Override
                    public boolean verify(long ssl, byte[][] chain, String auth) {
                        X509Certificate[] peerCerts = certificates(chain);
                        try {
                            manager.checkClientTrusted(peerCerts, auth);
                            return true;
                        } catch (Exception e) {
                            log.debug(sm.getString("openssl.certificateVerificationFailed"), e);
                        }
                        return false;
                    }
                });
            }
            String[] protos = new OpenSSLProtocols(enabledProtocol).getProtocols();
            SSLContext.setNpnProtos(ctx, protos, SSL.SSL_SELECTOR_FAILURE_CHOOSE_MY_LAST_PROTOCOL);

            sessionContext = new OpenSSLServerSessionContext(ctx);

            if (negotiableProtocols != null && negotiableProtocols.size() > 0) {
                byte[] protocols = buildAlpnConfig(negotiableProtocols);
                if (SSLContext.setALPN(ctx, protocols, protocols.length) != 0) {
                    log.warn(netSm.getString("endpoint.alpn.fail", negotiableProtocols));
                }
            }

            sslHostConfig.setOpenSslContext(Long.valueOf(ctx));
            initialized = true;
        } catch (Exception e) {
            log.warn(sm.getString("openssl.errorSSLCtxInit"), e);
            destroyPools();
        }
    }

    static OpenSSLKeyManager chooseKeyManager(KeyManager[] managers) throws Exception {
        for (KeyManager manager : managers) {
            if (manager instanceof OpenSSLKeyManager) {
                return (OpenSSLKeyManager) manager;
            }
        }
        throw new IllegalStateException(sm.getString("openssl.keyManagerMissing"));
    }

    static X509TrustManager chooseTrustManager(TrustManager[] managers) {
        for (TrustManager m : managers) {
            if (m instanceof X509TrustManager) {
                return (X509TrustManager) m;
            }
        }
        throw new IllegalStateException(sm.getString("openssl.trustManagerMissing"));
    }

    private static X509Certificate[] certificates(byte[][] chain) {
        X509Certificate[] peerCerts = new X509Certificate[chain.length];
        for (int i = 0; i < peerCerts.length; i++) {
            peerCerts[i] = new OpenSslX509Certificate(chain[i]);
        }
        return peerCerts;
    }

    @Override
    public SSLSessionContext getServerSessionContext() {
        return sessionContext;
    }

    @Override
    public SSLEngine createSSLEngine() {
        return new OpenSSLEngine(ctx, defaultProtocol, false, sessionContext,
                (negotiableProtocols != null && negotiableProtocols.size() > 0));
    }

    @Override
    public SSLServerSocketFactory getServerSocketFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SSLParameters getSupportedSSLParameters() {
        throw new UnsupportedOperationException();
    }

    /**
     * Generates a key specification for an (encrypted) private key.
     *
     * @param password characters, if {@code null} or empty an unencrypted key
     * is assumed
     * @param key bytes of the DER encoded private key
     *
     * @return a key specification
     *
     * @throws IOException if parsing {@code key} fails
     * @throws NoSuchAlgorithmException if the algorithm used to encrypt
     * {@code key} is unknown
     * @throws NoSuchPaddingException if the padding scheme specified in the
     * decryption algorithm is unknown
     * @throws InvalidKeySpecException if the decryption key based on
     * {@code password} cannot be generated
     * @throws InvalidKeyException if the decryption key based on
     * {@code password} cannot be used to decrypt {@code key}
     * @throws InvalidAlgorithmParameterException if decryption algorithm
     * parameters are somehow faulty
     */
    protected static PKCS8EncodedKeySpec generateKeySpec(char[] password, byte[] key)
            throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeySpecException,
            InvalidKeyException, InvalidAlgorithmParameterException {

        if (password == null || password.length == 0) {
            return new PKCS8EncodedKeySpec(key);
        }

        EncryptedPrivateKeyInfo encryptedPrivateKeyInfo = new EncryptedPrivateKeyInfo(key);
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(encryptedPrivateKeyInfo.getAlgName());
        PBEKeySpec pbeKeySpec = new PBEKeySpec(password);
        SecretKey pbeKey = keyFactory.generateSecret(pbeKeySpec);

        Cipher cipher = Cipher.getInstance(encryptedPrivateKeyInfo.getAlgName());
        cipher.init(Cipher.DECRYPT_MODE, pbeKey, encryptedPrivateKeyInfo.getAlgParameters());

        return encryptedPrivateKeyInfo.getKeySpec(cipher);
    }

    @Override
    protected final void finalize() throws Throwable {
        super.finalize();
        synchronized (OpenSSLContext.class) {
            if (ctx != 0) {
                SSLContext.free(ctx);
            }
        }
        destroyPools();
    }
}
