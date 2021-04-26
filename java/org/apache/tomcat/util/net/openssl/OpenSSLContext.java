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

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jni.CertificateVerifier;
import org.apache.tomcat.jni.Pool;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLConf;
import org.apache.tomcat.jni.SSLContext;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.Constants;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfig.CertificateVerification;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.Type;
import org.apache.tomcat.util.res.StringManager;

public class OpenSSLContext implements org.apache.tomcat.util.net.SSLContext {

    private static final Log log = LogFactory.getLog(OpenSSLContext.class);

    // Note: this uses the main "net" package strings as many are common with APR
    private static final StringManager netSm = StringManager.getManager(AbstractEndpoint.class);
    private static final StringManager sm = StringManager.getManager(OpenSSLContext.class);

    private static final String defaultProtocol = "TLS";

    private final SSLHostConfig sslHostConfig;
    private final SSLHostConfigCertificate certificate;
    private OpenSSLSessionContext sessionContext;
    private X509TrustManager x509TrustManager;

    private final List<String> negotiableProtocols;

    private String enabledProtocol;

    public String getEnabledProtocol() {
        return enabledProtocol;
    }

    public void setEnabledProtocol(String protocol) {
        enabledProtocol = (protocol == null) ? defaultProtocol : protocol;
    }

    private final long aprPool;
    private final AtomicInteger aprPoolDestroyed = new AtomicInteger(0);

    // OpenSSLConfCmd context
    protected final long cctx;
    // SSL context
    protected final long ctx;

    static final CertificateFactory X509_CERT_FACTORY;

    private static final String BEGIN_KEY = "-----BEGIN PRIVATE KEY-----\n";

    private static final Object END_KEY = "\n-----END PRIVATE KEY-----";
    private boolean initialized = false;

    static {
        try {
            X509_CERT_FACTORY = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new IllegalStateException(sm.getString("openssl.X509FactoryError"), e);
        }
    }

    public OpenSSLContext(SSLHostConfigCertificate certificate, List<String> negotiableProtocols)
            throws SSLException {
        this.sslHostConfig = certificate.getSSLHostConfig();
        this.certificate = certificate;
        aprPool = Pool.create(0);
        boolean success = false;
        try {
            // Create OpenSSLConfCmd context if used
            OpenSSLConf openSslConf = sslHostConfig.getOpenSslConf();
            if (openSslConf != null) {
                try {
                    if (log.isDebugEnabled())
                        log.debug(sm.getString("openssl.makeConf"));
                    cctx = SSLConf.make(aprPool,
                                        SSL.SSL_CONF_FLAG_FILE |
                                        SSL.SSL_CONF_FLAG_SERVER |
                                        SSL.SSL_CONF_FLAG_CERTIFICATE |
                                        SSL.SSL_CONF_FLAG_SHOW_ERRORS);
                } catch (Exception e) {
                    throw new SSLException(sm.getString("openssl.errMakeConf"), e);
                }
            } else {
                cctx = 0;
            }
            sslHostConfig.setOpenSslConfContext(Long.valueOf(cctx));

            // SSL protocol
            int value = SSL.SSL_PROTOCOL_NONE;
            for (String protocol : sslHostConfig.getEnabledProtocols()) {
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
                } else if (Constants.SSL_PROTO_TLSv1_3.equalsIgnoreCase(protocol)) {
                    value |= SSL.SSL_PROTOCOL_TLSV1_3;
                } else if (Constants.SSL_PROTO_ALL.equalsIgnoreCase(protocol)) {
                    value |= SSL.SSL_PROTOCOL_ALL;
                } else {
                    // Should not happen since filtering to build
                    // enabled protocols removes invalid values.
                    throw new Exception(netSm.getString(
                            "endpoint.apr.invalidSslProtocol", protocol));
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
                destroy();
            }
        }
    }

    @Override
    public synchronized void destroy() {
        // Guard against multiple destroyPools() calls triggered by construction exception and finalize() later
        if (aprPoolDestroyed.compareAndSet(0, 1)) {
            if (ctx != 0) {
                SSLContext.free(ctx);
            }
            if (cctx != 0) {
                SSLConf.free(cctx);
            }
            if (aprPool != 0) {
                Pool.destroy(aprPool);
            }
        }
    }

    /**
     * Setup the SSL_CTX.
     *
     * @param kms Must contain a KeyManager of the type
     *            {@code OpenSSLKeyManager}
     * @param tms Must contain a TrustManager of the type
     *            {@code X509TrustManager}
     * @param sr Is not used for this implementation.
     */
    @Override
    public synchronized void init(KeyManager[] kms, TrustManager[] tms, SecureRandom sr) {
        if (initialized) {
            log.warn(sm.getString("openssl.doubleInit"));
            return;
        }
        try {
            if (sslHostConfig.getInsecureRenegotiation()) {
                SSLContext.setOptions(ctx, SSL.SSL_OP_ALLOW_UNSAFE_LEGACY_RENEGOTIATION);
            } else {
                SSLContext.clearOptions(ctx, SSL.SSL_OP_ALLOW_UNSAFE_LEGACY_RENEGOTIATION);
            }

            // Use server's preference order for ciphers (rather than
            // client's)
            if (sslHostConfig.getHonorCipherOrder()) {
                SSLContext.setOptions(ctx, SSL.SSL_OP_CIPHER_SERVER_PREFERENCE);
            } else {
                SSLContext.clearOptions(ctx, SSL.SSL_OP_CIPHER_SERVER_PREFERENCE);
            }

            // Disable compression if requested
            if (sslHostConfig.getDisableCompression()) {
                SSLContext.setOptions(ctx, SSL.SSL_OP_NO_COMPRESSION);
            } else {
                SSLContext.clearOptions(ctx, SSL.SSL_OP_NO_COMPRESSION);
            }

            // Disable TLS Session Tickets (RFC4507) to protect perfect forward secrecy
            if (sslHostConfig.getDisableSessionTickets()) {
                SSLContext.setOptions(ctx, SSL.SSL_OP_NO_TICKET);
            } else {
                SSLContext.clearOptions(ctx, SSL.SSL_OP_NO_TICKET);
            }

            // List the ciphers that the client is permitted to negotiate
            SSLContext.setCipherSuite(ctx, sslHostConfig.getCiphers());

            if (certificate.getCertificateFile() == null) {
                certificate.setCertificateKeyManager(OpenSSLUtil.chooseKeyManager(kms));
            }

            addCertificate(certificate);

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
                // Client certificate verification based on custom trust managers
                x509TrustManager = chooseTrustManager(tms);
                SSLContext.setCertVerifyCallback(ctx, new CertificateVerifier() {
                    @Override
                    public boolean verify(long ssl, byte[][] chain, String auth) {
                        X509Certificate[] peerCerts = certificates(chain);
                        try {
                            x509TrustManager.checkClientTrusted(peerCerts, auth);
                            return true;
                        } catch (Exception e) {
                            log.debug(sm.getString("openssl.certificateVerificationFailed"), e);
                        }
                        return false;
                    }
                });
                // Pass along the DER encoded certificates of the accepted client
                // certificate issuers, so that their subjects can be presented
                // by the server during the handshake to allow the client choosing
                // an acceptable certificate
                for (X509Certificate caCert : x509TrustManager.getAcceptedIssuers()) {
                    SSLContext.addClientCACertificateRaw(ctx, caCert.getEncoded());
                    if (log.isDebugEnabled())
                        log.debug(sm.getString("openssl.addedClientCaCert", caCert.toString()));
                }
            } else {
                // Client certificate verification based on trusted CA files and dirs
                SSLContext.setCACertificate(ctx,
                        SSLHostConfig.adjustRelativePath(sslHostConfig.getCaCertificateFile()),
                        SSLHostConfig.adjustRelativePath(sslHostConfig.getCaCertificatePath()));
            }

            if (negotiableProtocols != null && negotiableProtocols.size() > 0) {
                List<String> protocols = new ArrayList<>(negotiableProtocols);
                protocols.add("http/1.1");
                String[] protocolsArray = protocols.toArray(new String[0]);
                SSLContext.setAlpnProtos(ctx, protocolsArray, SSL.SSL_SELECTOR_FAILURE_NO_ADVERTISE);
                SSLContext.setNpnProtos(ctx, protocolsArray, SSL.SSL_SELECTOR_FAILURE_NO_ADVERTISE);
            }

            // Apply OpenSSLConfCmd if used
            OpenSSLConf openSslConf = sslHostConfig.getOpenSslConf();
            if (openSslConf != null && cctx != 0) {
                // Check OpenSSLConfCmd if used
                if (log.isDebugEnabled())
                    log.debug(sm.getString("openssl.checkConf"));
                try {
                    if (!openSslConf.check(cctx)) {
                        log.error(sm.getString("openssl.errCheckConf"));
                        throw new Exception(sm.getString("openssl.errCheckConf"));
                    }
                } catch (Exception e) {
                    throw new Exception(sm.getString("openssl.errCheckConf"), e);
                }
                if (log.isDebugEnabled())
                    log.debug(sm.getString("openssl.applyConf"));
                try {
                    if (!openSslConf.apply(cctx, ctx)) {
                        log.error(sm.getString("openssl.errApplyConf"));
                        throw new SSLException(sm.getString("openssl.errApplyConf"));
                    }
                } catch (Exception e) {
                    throw new SSLException(sm.getString("openssl.errApplyConf"), e);
                }
                // Reconfigure the enabled protocols
                int opts = SSLContext.getOptions(ctx);
                List<String> enabled = new ArrayList<>();
                // Seems like there is no way to explicitly disable SSLv2Hello
                // in OpenSSL so it is always enabled
                enabled.add(Constants.SSL_PROTO_SSLv2Hello);
                if ((opts & SSL.SSL_OP_NO_TLSv1) == 0) {
                    enabled.add(Constants.SSL_PROTO_TLSv1);
                }
                if ((opts & SSL.SSL_OP_NO_TLSv1_1) == 0) {
                    enabled.add(Constants.SSL_PROTO_TLSv1_1);
                }
                if ((opts & SSL.SSL_OP_NO_TLSv1_2) == 0) {
                    enabled.add(Constants.SSL_PROTO_TLSv1_2);
                }
                if ((opts & SSL.SSL_OP_NO_SSLv2) == 0) {
                    enabled.add(Constants.SSL_PROTO_SSLv2);
                }
                if ((opts & SSL.SSL_OP_NO_SSLv3) == 0) {
                    enabled.add(Constants.SSL_PROTO_SSLv3);
                }
                sslHostConfig.setEnabledProtocols(
                        enabled.toArray(new String[0]));
                // Reconfigure the enabled ciphers
                sslHostConfig.setEnabledCiphers(SSLContext.getCiphers(ctx));
            }

            sessionContext = new OpenSSLSessionContext(this);
            // If client authentication is being used, OpenSSL requires that
            // this is set so always set it in case an app is configured to
            // require it
            sessionContext.setSessionIdContext(SSLContext.DEFAULT_SESSION_ID_CONTEXT);
            sslHostConfig.setOpenSslContext(Long.valueOf(ctx));
            initialized = true;
        } catch (Exception e) {
            log.warn(sm.getString("openssl.errorSSLCtxInit"), e);
            destroy();
        }
    }


    public void addCertificate(SSLHostConfigCertificate certificate) throws Exception {
        // Load Server key and certificate
        if (certificate.getCertificateFile() != null) {
            // Set certificate
            SSLContext.setCertificate(ctx,
                    SSLHostConfig.adjustRelativePath(certificate.getCertificateFile()),
                    SSLHostConfig.adjustRelativePath(certificate.getCertificateKeyFile()),
                    certificate.getCertificateKeyPassword(), getCertificateIndex(certificate));
            // Set certificate chain file
            SSLContext.setCertificateChainFile(ctx,
                    SSLHostConfig.adjustRelativePath(certificate.getCertificateChainFile()), false);
            // Set revocation
            SSLContext.setCARevocation(ctx,
                    SSLHostConfig.adjustRelativePath(
                            sslHostConfig.getCertificateRevocationListFile()),
                    SSLHostConfig.adjustRelativePath(
                            sslHostConfig.getCertificateRevocationListPath()));
        } else {
            String alias = certificate.getCertificateKeyAlias();
            X509KeyManager x509KeyManager = certificate.getCertificateKeyManager();
            if (alias == null) {
                alias = "tomcat";
            }
            X509Certificate[] chain = x509KeyManager.getCertificateChain(alias);
            if (chain == null) {
                alias = findAlias(x509KeyManager, certificate);
                chain = x509KeyManager.getCertificateChain(alias);
            }
            PrivateKey key = x509KeyManager.getPrivateKey(alias);
            StringBuilder sb = new StringBuilder(BEGIN_KEY);
            sb.append(Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(key.getEncoded()));
            sb.append(END_KEY);
            SSLContext.setCertificateRaw(ctx, chain[0].getEncoded(),
                    sb.toString().getBytes(StandardCharsets.US_ASCII),
                    getCertificateIndex(certificate));
            for (int i = 1; i < chain.length; i++) {
                SSLContext.addChainCertificateRaw(ctx, chain[i].getEncoded());
            }
        }
    }


    private static int getCertificateIndex(SSLHostConfigCertificate certificate) {
        int result;
        // If the type is undefined there will only be one certificate (enforced
        // in SSLHostConfig) so use the RSA slot.
        if (certificate.getType() == Type.RSA || certificate.getType() == Type.UNDEFINED) {
            result = SSL.SSL_AIDX_RSA;
        } else if (certificate.getType() == Type.EC) {
            result = SSL.SSL_AIDX_ECC;
        } else if (certificate.getType() == Type.DSA) {
            result = SSL.SSL_AIDX_DSA;
        } else {
            result = SSL.SSL_AIDX_MAX;
        }
        return result;
    }


    /*
     * Find a valid alias when none was specified in the config.
     */
    private static String findAlias(X509KeyManager keyManager,
            SSLHostConfigCertificate certificate) {

        Type type = certificate.getType();
        String result = null;

        List<Type> candidateTypes = new ArrayList<>();
        if (Type.UNDEFINED.equals(type)) {
            // Try all types to find an suitable alias
            candidateTypes.addAll(Arrays.asList(Type.values()));
            candidateTypes.remove(Type.UNDEFINED);
        } else {
            // Look for the specific type to find a suitable alias
            candidateTypes.add(type);
        }

        Iterator<Type> iter = candidateTypes.iterator();
        while (result == null && iter.hasNext()) {
            result = keyManager.chooseServerAlias(iter.next().toString(),  null,  null);
        }

        return result;
    }

    private static X509TrustManager chooseTrustManager(TrustManager[] managers) {
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
            peerCerts[i] = new OpenSSLX509Certificate(chain[i]);
        }
        return peerCerts;
    }


    long getSSLContextID() {
        return ctx;
    }


    @Override
    public SSLSessionContext getServerSessionContext() {
        return sessionContext;
    }

    @Override
    public SSLEngine createSSLEngine() {
        return new OpenSSLEngine(ctx, defaultProtocol, false, sessionContext,
                (negotiableProtocols != null && negotiableProtocols.size() > 0), initialized,
                sslHostConfig.getCertificateVerificationDepth(),
                sslHostConfig.getCertificateVerification() == CertificateVerification.OPTIONAL_NO_CA);
    }

    @Override
    public SSLServerSocketFactory getServerSocketFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SSLParameters getSupportedSSLParameters() {
        throw new UnsupportedOperationException();
    }

    @Override
    public X509Certificate[] getCertificateChain(String alias) {
        X509Certificate[] chain = null;
        X509KeyManager x509KeyManager = certificate.getCertificateKeyManager();
        if (x509KeyManager != null) {
            if (alias == null) {
                alias = "tomcat";
            }
            chain = x509KeyManager.getCertificateChain(alias);
            if (chain == null) {
                alias = findAlias(x509KeyManager, certificate);
                chain = x509KeyManager.getCertificateChain(alias);
            }
        }

        return chain;
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        X509Certificate[] acceptedCerts = null;
        if (x509TrustManager != null) {
            acceptedCerts = x509TrustManager.getAcceptedIssuers();
        }
        return acceptedCerts;
    }

    @Override
    protected void finalize() throws Throwable {
        /*
         * When an SSLHostConfig is replaced at runtime, it is not possible to
         * call destroy() on the associated OpenSSLContext since it is likely
         * that there will be in-progress connections using the OpenSSLContext.
         * A reference chain has been deliberately established (see
         * OpenSSLSessionContext) to ensure that the OpenSSLContext remains
         * ineligible for GC while those connections are alive. Once those
         * connections complete, the OpenSSLContext will become eligible for GC
         * and this method will ensure that the associated native resources are
         * cleaned up.
         */
        try {
            destroy();
        } finally {
            super.finalize();
        }
    }
}
