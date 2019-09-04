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

package org.apache.tomcat.jni;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** SSL Context
 *
 * @author Mladen Turk
 */
public final class SSLContext {

    public static final byte[] DEFAULT_SESSION_ID_CONTEXT =
            new byte[] { 'd', 'e', 'f', 'a', 'u', 'l', 't' };

    /**
     * Create a new SSL context.
     *
     * @param pool The pool to use.
     * @param protocol The SSL protocol to use. It can be any combination of
     * the following:
     * <PRE>
     * {@link SSL#SSL_PROTOCOL_SSLV2}
     * {@link SSL#SSL_PROTOCOL_SSLV3}
     * {@link SSL#SSL_PROTOCOL_TLSV1}
     * {@link SSL#SSL_PROTOCOL_TLSV1_1}
     * {@link SSL#SSL_PROTOCOL_TLSV1_2}
     * {@link SSL#SSL_PROTOCOL_TLSV1_3}
     * {@link SSL#SSL_PROTOCOL_ALL} ( == all TLS versions, no SSL)
     * </PRE>
     * @param mode SSL mode to use
     * <PRE>
     * SSL_MODE_CLIENT
     * SSL_MODE_SERVER
     * SSL_MODE_COMBINED
     * </PRE>
     *
     * @return The Java representation of a pointer to the newly created SSL
     *         Context
     *
     * @throws Exception If the SSL Context could not be created
     */
    public static native long make(long pool, int protocol, int mode) throws Exception;

    /**
     * Free the resources used by the Context
     * @param ctx Server or Client context to free.
     * @return APR Status code.
     */
    public static native int free(long ctx);

    /**
     * Set Session context id. Usually host:port combination.
     * @param ctx Context to use.
     * @param id  String that uniquely identifies this context.
     */
    public static native void setContextId(long ctx, String id);

    /**
     * Associate BIOCallback for input or output data capture.
     * <br>
     * First word in the output string will contain error
     * level in the form:
     * <PRE>
     * [ERROR]  -- Critical error messages
     * [WARN]   -- Warning messages
     * [INFO]   -- Informational messages
     * [DEBUG]  -- Debugging messaged
     * </PRE>
     * Callback can use that word to determine application logging level
     * by intercepting <b>write</b> call.
     * If the <b>bio</b> is set to 0 no error messages will be displayed.
     * Default is to use the stderr output stream.
     * @param ctx Server or Client context to use.
     * @param bio BIO handle to use, created with SSL.newBIO
     * @param dir BIO direction (1 for input 0 for output).
     */
    public static native void setBIO(long ctx, long bio, int dir);

    /**
     * Set OpenSSL Option.
     * @param ctx Server or Client context to use.
     * @param options  See SSL.SSL_OP_* for option flags.
     */
    public static native void setOptions(long ctx, int options);

    /**
     * Get OpenSSL Option.
     * @param ctx Server or Client context to use.
     * @return options  See SSL.SSL_OP_* for option flags.
     */
    public static native int getOptions(long ctx);

    /**
     * Clears OpenSSL Options.
     * @param ctx Server or Client context to use.
     * @param options  See SSL.SSL_OP_* for option flags.
     */
    public static native void clearOptions(long ctx, int options);

    /**
     * Returns all cipher suites that are enabled for negotiation in an SSL handshake.
     * @param ctx Server or Client context to use.
     * @return ciphers
     */
    public static native String[] getCiphers(long ctx);

    /**
     * Sets the "quiet shutdown" flag for <b>ctx</b> to be
     * <b>mode</b>. SSL objects created from <b>ctx</b> inherit the
     * <b>mode</b> valid at the time and may be 0 or 1.
     * <br>
     * Normally when an SSL connection is finished, the parties must send out
     * "close notify" alert messages using L&lt;SSL_shutdown(3)|SSL_shutdown(3)&gt;
     * for a clean shutdown.
     * <br>
     * When setting the "quiet shutdown" flag to 1, <b>SSL.shutdown</b>
     * will set the internal flags to SSL_SENT_SHUTDOWN|SSL_RECEIVED_SHUTDOWN.
     * (<b>SSL_shutdown</b> then behaves like called with
     * SSL_SENT_SHUTDOWN|SSL_RECEIVED_SHUTDOWN.)
     * The session is thus considered to be shutdown, but no "close notify" alert
     * is sent to the peer. This behaviour violates the TLS standard.
     * The default is normal shutdown behaviour as described by the TLS standard.
     * @param ctx Server or Client context to use.
     * @param mode True to set the quiet shutdown.
     */
    public static native void setQuietShutdown(long ctx, boolean mode);

    /**
     * Cipher Suite available for negotiation in SSL handshake.
     * <br>
     * This complex directive uses a colon-separated cipher-spec string consisting
     * of OpenSSL cipher specifications to configure the Cipher Suite the client
     * is permitted to negotiate in the SSL handshake phase. Notice that this
     * directive can be used both in per-server and per-directory context.
     * In per-server context it applies to the standard SSL handshake when a
     * connection is established. In per-directory context it forces an SSL
     * renegotiation with the reconfigured Cipher Suite after the HTTP request
     * was read but before the HTTP response is sent.
     * @param ctx Server or Client context to use.
     * @param ciphers An OpenSSL cipher specification.
     * @return <code>true</code> if the operation was successful
     * @throws Exception An error occurred
     */
    public static native boolean setCipherSuite(long ctx, String ciphers)
        throws Exception;

    /**
     * Set File of concatenated PEM-encoded CA CRLs or
     * directory of PEM-encoded CA Certificates for Client Auth
     * <br>
     * This directive sets the all-in-one file where you can assemble the
     * Certificate Revocation Lists (CRL) of Certification Authorities (CA)
     * whose clients you deal with. These are used for Client Authentication.
     * Such a file is simply the concatenation of the various PEM-encoded CRL
     * files, in order of preference.
     * <br>
     * The files in this directory have to be PEM-encoded and are accessed through
     * hash filenames. So usually you can't just place the Certificate files there:
     * you also have to create symbolic links named hash-value.N. And you should
     * always make sure this directory contains the appropriate symbolic links.
     * Use the Makefile which comes with mod_ssl to accomplish this task.
     * @param ctx Server or Client context to use.
     * @param file File of concatenated PEM-encoded CA CRLs for Client Auth.
     * @param path Directory of PEM-encoded CA Certificates for Client Auth.
     * @return <code>true</code> if the operation was successful
     * @throws Exception An error occurred
     */
    public static native boolean setCARevocation(long ctx, String file,
                                                 String path)
        throws Exception;

    /**
     * Set File of PEM-encoded Server CA Certificates
     * <br>
     * This directive sets the optional all-in-one file where you can assemble the
     * certificates of Certification Authorities (CA) which form the certificate
     * chain of the server certificate. This starts with the issuing CA certificate
     * of of the server certificate and can range up to the root CA certificate.
     * Such a file is simply the concatenation of the various PEM-encoded CA
     * Certificate files, usually in certificate chain order.
     * <br>
     * But be careful: Providing the certificate chain works only if you are using
     * a single (either RSA or DSA) based server certificate. If you are using a
     * coupled RSA+DSA certificate pair, this will work only if actually both
     * certificates use the same certificate chain. Else the browsers will be
     * confused in this situation.
     * @param ctx Server or Client context to use.
     * @param file File of PEM-encoded Server CA Certificates.
     * @param skipfirst Skip first certificate if chain file is inside
     *                  certificate file.
     * @return <code>true</code> if the operation was successful
     */
    public static native boolean setCertificateChainFile(long ctx, String file,
                                                         boolean skipfirst);

    /**
     * Set Certificate
     * <br>
     * Point setCertificateFile at a PEM encoded certificate.  If
     * the certificate is encrypted, then you will be prompted for a
     * pass phrase.  Note that a kill -HUP will prompt again. A test
     * certificate can be generated with `make certificate' under
     * built time. Keep in mind that if you've both a RSA and a DSA
     * certificate you can configure both in parallel (to also allow
     * the use of DSA ciphers, etc.)
     * <br>
     * If the key is not combined with the certificate, use key param
     * to point at the key file.  Keep in mind that if
     * you've both a RSA and a DSA private key you can configure
     * both in parallel (to also allow the use of DSA ciphers, etc.)
     * @param ctx Server or Client context to use.
     * @param cert Certificate file.
     * @param key Private Key file to use if not in cert.
     * @param password Certificate password. If null and certificate
     *                 is encrypted, password prompt will be displayed.
     * @param idx Certificate index SSL_AIDX_RSA or SSL_AIDX_DSA.
     * @return <code>true</code> if the operation was successful
     * @throws Exception An error occurred
     */
    public static native boolean setCertificate(long ctx, String cert,
                                                String key, String password,
                                                int idx)
        throws Exception;

    /**
     * Set the size of the internal session cache.
     * http://www.openssl.org/docs/ssl/SSL_CTX_sess_set_cache_size.html
     * @param ctx Server or Client context to use.
     * @param size The cache size
     * @return the value set
     */
    public static native long setSessionCacheSize(long ctx, long size);

    /**
     * Get the size of the internal session cache.
     * http://www.openssl.org/docs/ssl/SSL_CTX_sess_get_cache_size.html
     * @param ctx Server or Client context to use.
     * @return the size
     */
    public static native long getSessionCacheSize(long ctx);

    /**
     * Set the timeout for the internal session cache in seconds.
     * http://www.openssl.org/docs/ssl/SSL_CTX_set_timeout.html
     * @param ctx Server or Client context to use.
     * @param timeoutSeconds Timeout value
     * @return the value set
     */
    public static native long setSessionCacheTimeout(long ctx, long timeoutSeconds);

    /**
     * Get the timeout for the internal session cache in seconds.
     * http://www.openssl.org/docs/ssl/SSL_CTX_set_timeout.html
     * @param ctx Server or Client context to use.
     * @return the timeout
     */
    public static native long getSessionCacheTimeout(long ctx);

    /**
     * Set the mode of the internal session cache and return the previous used mode.
     * @param ctx Server or Client context to use.
     * @param mode The mode to set
     * @return the value set
     */
    public static native long setSessionCacheMode(long ctx, long mode);

    /**
     * Get the mode of the current used internal session cache.
     * @param ctx Server or Client context to use.
     * @return the value set
     */
    public static native long getSessionCacheMode(long ctx);

    /*
     * Session resumption statistics methods.
     * http://www.openssl.org/docs/ssl/SSL_CTX_sess_number.html
     */
    public static native long sessionAccept(long ctx);
    public static native long sessionAcceptGood(long ctx);
    public static native long sessionAcceptRenegotiate(long ctx);
    public static native long sessionCacheFull(long ctx);
    public static native long sessionCbHits(long ctx);
    public static native long sessionConnect(long ctx);
    public static native long sessionConnectGood(long ctx);
    public static native long sessionConnectRenegotiate(long ctx);
    public static native long sessionHits(long ctx);
    public static native long sessionMisses(long ctx);
    public static native long sessionNumber(long ctx);
    public static native long sessionTimeouts(long ctx);

    /**
     * Set TLS session keys. This allows us to share keys across TFEs.
     * @param ctx Server or Client context to use.
     * @param keys Some session keys
     */
    public static native void setSessionTicketKeys(long ctx, byte[] keys);

    /**
     * Set File and Directory of concatenated PEM-encoded CA Certificates
     * for Client Auth
     * <br>
     * This directive sets the all-in-one file where you can assemble the
     * Certificates of Certification Authorities (CA) whose clients you deal with.
     * These are used for Client Authentication. Such a file is simply the
     * concatenation of the various PEM-encoded Certificate files, in order of
     * preference. This can be used alternatively and/or additionally to
     * path.
     * <br>
     * The files in this directory have to be PEM-encoded and are accessed through
     * hash filenames. So usually you can't just place the Certificate files there:
     * you also have to create symbolic links named hash-value.N. And you should
     * always make sure this directory contains the appropriate symbolic links.
     * Use the Makefile which comes with mod_ssl to accomplish this task.
     * @param ctx Server or Client context to use.
     * @param file File of concatenated PEM-encoded CA Certificates for
     *             Client Auth.
     * @param path Directory of PEM-encoded CA Certificates for Client Auth.
     * @return <code>true</code> if the operation was successful
     * @throws Exception An error occurred
     */
    public static native boolean setCACertificate(long ctx, String file,
                                                  String path)
        throws Exception;

    /**
     * Set file for randomness
     * @param ctx Server or Client context to use.
     * @param file random file.
     */
    public static native void setRandom(long ctx, String file);

    /**
     * Set SSL connection shutdown type
     * <br>
     * The following levels are available for level:
     * <PRE>
     * SSL_SHUTDOWN_TYPE_STANDARD
     * SSL_SHUTDOWN_TYPE_UNCLEAN
     * SSL_SHUTDOWN_TYPE_ACCURATE
     * </PRE>
     * @param ctx Server or Client context to use.
     * @param type Shutdown type to use.
     */
    public static native void setShutdownType(long ctx, int type);

    /**
     * Set Type of Client Certificate verification and Maximum depth of CA Certificates
     * in Client Certificate verification.
     * <br>
     * This directive sets the Certificate verification level for the Client
     * Authentication. Notice that this directive can be used both in per-server
     * and per-directory context. In per-server context it applies to the client
     * authentication process used in the standard SSL handshake when a connection
     * is established. In per-directory context it forces an SSL renegotiation with
     * the reconfigured client verification level after the HTTP request was read
     * but before the HTTP response is sent.
     * <br>
     * The following levels are available for level:
     * <PRE>
     * SSL_CVERIFY_NONE           - No client Certificate is required at all
     * SSL_CVERIFY_OPTIONAL       - The client may present a valid Certificate
     * SSL_CVERIFY_REQUIRE        - The client has to present a valid Certificate
     * SSL_CVERIFY_OPTIONAL_NO_CA - The client may present a valid Certificate
     *                              but it need not to be (successfully) verifiable
     * </PRE>
     * <br>
     * The depth actually is the maximum number of intermediate certificate issuers,
     * i.e. the number of CA certificates which are max allowed to be followed while
     * verifying the client certificate. A depth of 0 means that self-signed client
     * certificates are accepted only, the default depth of 1 means the client
     * certificate can be self-signed or has to be signed by a CA which is directly
     * known to the server (i.e. the CA's certificate is under
     * <code>setCACertificatePath</code>), etc.
     * @param ctx Server or Client context to use.
     * @param level Type of Client Certificate verification.
     * @param depth Maximum depth of CA Certificates in Client Certificate
     *              verification.
     */
    public static native void setVerify(long ctx, int level, int depth);

    public static native int setALPN(long ctx, byte[] proto, int len);

    /**
     * When tc-native encounters a SNI extension in the TLS handshake it will
     * call this method to determine which OpenSSL SSLContext to use for the
     * connection.
     *
     * @param currentCtx   The OpenSSL SSLContext that the handshake started to
     *                     use. This will be the default OpenSSL SSLContext for
     *                     the endpoint associated with the socket.
     * @param sniHostName  The host name requested by the client
     *
     * @return The Java representation of the pointer to the OpenSSL SSLContext
     *         to use for the given host or zero if no SSLContext could be
     *         identified
     */
    public static long sniCallBack(long currentCtx, String sniHostName) {
        SNICallBack sniCallBack = sniCallBacks.get(Long.valueOf(currentCtx));
        if (sniCallBack == null) {
            return 0;
        }
        return sniCallBack.getSslContext(sniHostName);
    }

    /**
     * A map of default SSL Contexts to SNICallBack instances (in Tomcat these
     * are instances of AprEndpoint) that will be used to determine the SSL
     * Context to use bases on the SNI host name. It is structured this way
     * since a Tomcat instance may have several TLS enabled endpoints that each
     * have different SSL Context mappings for the same host name.
     */
    private static final Map<Long,SNICallBack> sniCallBacks = new ConcurrentHashMap<>();

    /**
     * Register an OpenSSL SSLContext that will be used to initiate TLS
     * connections that may use the SNI extension with the component that will
     * be used to map the requested hostname to the correct OpenSSL SSLContext
     * for the remainder of the connection.
     *
     * @param defaultSSLContext The Java representation of a pointer to the
     *                          OpenSSL SSLContext that will be used to
     *                          initiate TLS connections
     * @param sniCallBack The component that will map SNI hosts names received
     *                    via connections initiated using
     *                    <code>defaultSSLContext</code> to the correct  OpenSSL
     *                    SSLContext
     */
    public static void registerDefault(Long defaultSSLContext,
            SNICallBack sniCallBack) {
        sniCallBacks.put(defaultSSLContext, sniCallBack);
    }

    /**
     * Unregister an OpenSSL SSLContext that will no longer be used to initiate
     * TLS connections that may use the SNI extension.
     *
     * @param defaultSSLContext The Java representation of a pointer to the
     *                          OpenSSL SSLContext that will no longer be used
     */
    public static void unregisterDefault(Long defaultSSLContext) {
        sniCallBacks.remove(defaultSSLContext);
    }


    /**
     * Interface implemented by components that will receive the call back to
     * select an OpenSSL SSLContext based on the host name requested by the
     * client.
     */
    public static interface SNICallBack {

        /**
         * This callback is made during the TLS handshake when the client uses
         * the SNI extension to request a specific TLS host.
         *
         * @param sniHostName The host name requested by the client
         *
         * @return The Java representation of the pointer to the OpenSSL
         *         SSLContext to use for the given host or zero if no SSLContext
         *         could be identified
         */
        public long getSslContext(String sniHostName);
    }

    /**
     * Allow to hook {@link CertificateVerifier} into the handshake processing.
     * This will call {@code SSL_CTX_set_cert_verify_callback} and so replace the default verification
     * callback used by openssl
     * @param ctx Server or Client context to use.
     * @param verifier the verifier to call during handshake.
     */
    public static native void setCertVerifyCallback(long ctx, CertificateVerifier verifier);

    /**
     * Set next protocol for next protocol negotiation extension
     * @param ctx Server context to use.
     * @param nextProtos comma delimited list of protocols in priority order
     *
     * @deprecated use {@link #setNpnProtos(long, String[], int)}
     */
    @Deprecated
    public static void setNextProtos(long ctx, String nextProtos) {
        setNpnProtos(ctx, nextProtos.split(","), SSL.SSL_SELECTOR_FAILURE_CHOOSE_MY_LAST_PROTOCOL);
    }

    /**
     * Set next protocol for next protocol negotiation extension
     * @param ctx Server context to use.
     * @param nextProtos protocols in priority order
     * @param selectorFailureBehavior see {@link SSL#SSL_SELECTOR_FAILURE_NO_ADVERTISE}
     *                                and {@link SSL#SSL_SELECTOR_FAILURE_CHOOSE_MY_LAST_PROTOCOL}
     */
    public static native void setNpnProtos(long ctx, String[] nextProtos, int selectorFailureBehavior);

    /**
     * Set application layer protocol for application layer protocol negotiation extension
     * @param ctx Server context to use.
     * @param alpnProtos protocols in priority order
     * @param selectorFailureBehavior see {@link SSL#SSL_SELECTOR_FAILURE_NO_ADVERTISE}
     *                                and {@link SSL#SSL_SELECTOR_FAILURE_CHOOSE_MY_LAST_PROTOCOL}
     */
    public static native void setAlpnProtos(long ctx, String[] alpnProtos, int selectorFailureBehavior);

    /**
     * Set DH parameters
     * @param ctx Server context to use.
     * @param cert DH param file (can be generated from e.g. {@code openssl dhparam -rand - 2048 > dhparam.pem} -
     *             see the <a href="https://www.openssl.org/docs/apps/dhparam.html">OpenSSL documentation</a>).
     * @throws Exception An error occurred
     */
    public static native void setTmpDH(long ctx, String cert)
            throws Exception;

    /**
     * Set ECDH elliptic curve by name
     * @param ctx Server context to use.
     * @param curveName the name of the elliptic curve to use
     *             (available names can be obtained from {@code openssl ecparam -list_curves}).
     * @throws Exception An error occurred
     */
    public static native void setTmpECDHByCurveName(long ctx, String curveName)
            throws Exception;

    /**
     * Set the context within which session be reused (server side only)
     * http://www.openssl.org/docs/ssl/SSL_CTX_set_session_id_context.html
     *
     * @param ctx Server context to use.
     * @param sidCtx can be any kind of binary data, it is therefore possible to use e.g. the name
     *               of the application and/or the hostname and/or service name
     * @return {@code true} if success, {@code false} otherwise.
     */
    public static native boolean setSessionIdContext(long ctx, byte[] sidCtx);

    /**
     * Set CertificateRaw
     * <br>
     * Use keystore a certificate and key to fill the BIOP
     * @param ctx Server or Client context to use.
     * @param cert Byte array with the certificate in DER encoding.
     * @param key Byte array with the Private Key file in PEM format.
     * @param sslAidxRsa Certificate index SSL_AIDX_RSA or SSL_AIDX_DSA.
     * @return {@code true} if success, {@code false} otherwise.
     */
    public static native boolean setCertificateRaw(long ctx, byte[] cert, byte[] key, int sslAidxRsa);

    /**
     * Add a certificate to the certificate chain. Certs should be added in
     * order starting with the issuer of the host certs and working up the
     * certificate chain to the CA.
     *
     * <br>
     * Use keystore a certificate chain to fill the BIOP
     * @param ctx Server or Client context to use.
     * @param cert Byte array with the certificate in DER encoding.
     * @return {@code true} if success, {@code false} otherwise.
     */
    public static native boolean addChainCertificateRaw(long ctx, byte[] cert);

    /**
     * Add a CA certificate we accept as issuer for peer certs
     * @param ctx Server or Client context to use.
     * @param cert Byte array with the certificate in DER encoding.
     * @return {@code true} if success, {@code false} otherwise.
     */
    public static native boolean addClientCACertificateRaw(long ctx, byte[] cert);
}
