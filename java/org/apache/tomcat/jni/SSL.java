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

/**
 * JNI bindings for OpenSSL SSL functionality.
 */
public final class SSL {

    /**
     * Private constructor to prevent instantiation.
     */
    private SSL() {
    }

    /*
     * Type definitions mostly from mod_ssl
     */
    /**
     * Unset value.
     */
    public static final int UNSET = -1;
    /*
     * Define the certificate algorithm types
     */
    /**
     * Unknown algorithm type.
     */
    public static final int SSL_ALGO_UNKNOWN = 0;
    /**
     * RSA algorithm type.
     */
    public static final int SSL_ALGO_RSA = (1 << 0);
    /**
     * DSA algorithm type.
     */
    public static final int SSL_ALGO_DSA = (1 << 1);
    /**
     * All algorithm types.
     */
    public static final int SSL_ALGO_ALL = (SSL_ALGO_RSA | SSL_ALGO_DSA);

    /**
     * RSA algorithm index.
     */
    public static final int SSL_AIDX_RSA = 0;
    /**
     * DSA algorithm index.
     */
    public static final int SSL_AIDX_DSA = 1;
    /**
     * ECC algorithm index.
     */
    public static final int SSL_AIDX_ECC = 3;
    /**
     * Maximum algorithm index.
     */
    public static final int SSL_AIDX_MAX = 4;
    /*
     * Define IDs for the temporary RSA keys and DH params
     */

    /**
     * 512-bit temporary RSA key.
     */
    public static final int SSL_TMP_KEY_RSA_512 = 0;
    /**
     * 1024-bit temporary RSA key.
     */
    public static final int SSL_TMP_KEY_RSA_1024 = 1;
    /**
     * 2048-bit temporary RSA key.
     */
    public static final int SSL_TMP_KEY_RSA_2048 = 2;
    /**
     * 4096-bit temporary RSA key.
     */
    public static final int SSL_TMP_KEY_RSA_4096 = 3;
    /**
     * 512-bit temporary DH key.
     */
    public static final int SSL_TMP_KEY_DH_512 = 4;
    /**
     * 1024-bit temporary DH key.
     */
    public static final int SSL_TMP_KEY_DH_1024 = 5;
    /**
     * 2048-bit temporary DH key.
     */
    public static final int SSL_TMP_KEY_DH_2048 = 6;
    /**
     * 4096-bit temporary DH key.
     */
    public static final int SSL_TMP_KEY_DH_4096 = 7;
    /**
     * Maximum temporary key ID.
     */
    public static final int SSL_TMP_KEY_MAX = 8;

    /*
     * Define the SSL Protocol options
     */
    /**
     * No protocol options.
     */
    public static final int SSL_PROTOCOL_NONE = 0;
    /**
     * SSLv2 protocol.
     */
    public static final int SSL_PROTOCOL_SSLV2 = (1 << 0);
    /**
     * SSLv3 protocol.
     */
    public static final int SSL_PROTOCOL_SSLV3 = (1 << 1);
    /**
     * TLSv1.0 protocol.
     */
    public static final int SSL_PROTOCOL_TLSV1 = (1 << 2);
    /**
     * TLSv1.1 protocol.
     */
    public static final int SSL_PROTOCOL_TLSV1_1 = (1 << 3);
    /**
     * TLSv1.2 protocol.
     */
    public static final int SSL_PROTOCOL_TLSV1_2 = (1 << 4);
    /**
     * TLSv1.3 protocol.
     */
    public static final int SSL_PROTOCOL_TLSV1_3 = (1 << 5);
    /**
     * All TLS protocol versions combined.
     */
    public static final int SSL_PROTOCOL_ALL =
            (SSL_PROTOCOL_TLSV1 | SSL_PROTOCOL_TLSV1_1 | SSL_PROTOCOL_TLSV1_2 | SSL_PROTOCOL_TLSV1_3);


    /*
     * Define the SSL verify levels
     */
    /**
     * Client verification unset.
     */
    public static final int SSL_CVERIFY_UNSET = UNSET;
    /**
     * No client certificate verification.
     */
    public static final int SSL_CVERIFY_NONE = 0;
    /**
     * Optional client certificate verification.
     */
    public static final int SSL_CVERIFY_OPTIONAL = 1;
    /**
     * Required client certificate verification.
     */
    public static final int SSL_CVERIFY_REQUIRE = 2;
    /**
     * Optional client certificate verification without CA requirement.
     */
    public static final int SSL_CVERIFY_OPTIONAL_NO_CA = 3;

    /*
     * Use either SSL_VERIFY_NONE or SSL_VERIFY_PEER, the last 2 options are 'ored' with SSL_VERIFY_PEER if they are
     * desired
     */
    /**
     * No peer verification.
     */
    public static final int SSL_VERIFY_NONE = 0;
    /**
     * Verify peer certificate.
     */
    public static final int SSL_VERIFY_PEER = 1;
    /**
     * Fail if no peer certificate is presented.
     */
    public static final int SSL_VERIFY_FAIL_IF_NO_PEER_CERT = 2;
    /**
     * Only verify client certificate once per session.
     */
    public static final int SSL_VERIFY_CLIENT_ONCE = 4;
    /**
     * Strict peer verification including certificate requirement.
     */
    public static final int SSL_VERIFY_PEER_STRICT = (SSL_VERIFY_PEER | SSL_VERIFY_FAIL_IF_NO_PEER_CERT);

    /*
     * Option values are synchronized with OpenSSL master as of 2026-08-26. They are also confirmed valid for the final
     * OpenSSL 1.1.1 release so the values can be consistent for all Tomcat versions.
     */

    /**
     * Disable Extended master secret.
     */
    public static final long SSL_OP_NO_EXTENDED_MASTER_SECRET = 0x1L;
    /**
     * Cleanse plaintext copies of data delivered to the application.
     */
    public static final long SSL_OP_CLEANSE_PLAINTEXT = 0x2L;
    /**
     * Allow initial connection to servers that don't support RI.
     */
    public static final long SSL_OP_LEGACY_SERVER_CONNECT = 0x4L;
    /**
     * Enable support for Kernel TLS.
     */
    public static final long SSL_OP_ENABLE_KTLS = 0x8L;
    public static final long SSL_OP_TLSEXT_PADDING = 0x10L;
    // Unused = 0x20L
    public static final long SSL_OP_SAFARI_ECDHE_ECDSA_BUG = 0x40L;
    public static final long SSL_OP_IGNORE_UNEXPECTED_EOF = 0x80L;
    public static final long SSL_OP_ALLOW_CLIENT_RENEGOTIATION = 0x100L;
    public static final long SSL_OP_DISABLE_TLSEXT_CA_NAMES = 0x200L;
    public static final long SSL_OP_ALLOW_NO_DHE_KEX = 0x400L;

    /**
     * Disable TLS 1.0 CBC vulnerability workaround. Usually (depending on the application protocol) the workaround is
     * not needed. Unfortunately some broken SSL/TLS implementations cannot handle it at all, which is why we include it
     * in SSL_OP_ALL.
     */
    public static final long SSL_OP_DONT_INSERT_EMPTY_FRAGMENTS = 0x800L;
    /**
     * DTLS options.
     */
    public static final long SSL_OP_NO_QUERY_MTU = 0x1000L;
    /**
     * Turn on Cookie Exchange (on relevant for servers).
     */
    public static final long SSL_OP_COOKIE_EXCHANGE = 0x2000L;
    /**
     * Don't use RFC4507 ticket extension.
     */
    public static final long SSL_OP_NO_TICKET = 0x4000L;
    /**
     * Use Cisco's version identifier of DTLS_BAD_VER (only with deprecated DTLSv1_client_method()).
     */
    public static final long SSL_OP_CISCO_ANYCONNECT = 0x8000L;
    /**
     * As server, disallow session resumption on renegotiation.
     */
    public static final long SSL_OP_NO_SESSION_RESUMPTION_ON_RENEGOTIATION = 0x10000L;
    /**
     * Don't use compression even if supported.
     */
    public static final long SSL_OP_NO_COMPRESSION = 0x20000L;
    /**
     * Permit unsafe legacy renegotiation.
     */
    public static final long SSL_OP_ALLOW_UNSAFE_LEGACY_RENEGOTIATION = 0x40000L;
    /**
     * Disable encrypt-then-mac.
     */
    public static final long SSL_OP_NO_ENCRYPT_THEN_MAC = 0x80000L;
    /**
     * Enable TLSv1.3 Compatibility mode. This is on by default. A future version of OpenSSL may have this disabled by
     * default.
     */
    public static final long SSL_OP_ENABLE_MIDDLEBOX_COMPAT = 0x100000L;
    /**
     * Prioritize Chacha20Poly1305 when client does. Modifies SSL_OP_SERVER_PREFERENCE.
     */
    public static final long SSL_OP_PRIORITIZE_CHACHA = 0x200000L;
    /**
     * Set on servers to choose cipher, curve or group according to server's preferences.
     */
    public static final long SSL_OP_SERVER_PREFERENCE = 0x400000L;
    /**
     * Equivalent definition for backwards compatibility:
     */
    public static final long SSL_OP_CIPHER_SERVER_PREFERENCE = SSL_OP_SERVER_PREFERENCE;
    /**
     * If set, a server will allow a client to issue an SSLv3.0 version number as latest version supported in the
     * premaster secret, even when TLSv1.0 (version 3.1) was announced in the client hello. Normally this is forbidden
     * to prevent version rollback attacks.
     */
    public static final long SSL_OP_TLS_ROLLBACK_BUG = 0x800000L;
    /**
     * Switches off automatic TLSv1.3 anti-replay protection for early data. This is a server-side option only (no effect
     * on the client).
     */
    public static final long SSL_OP_NO_ANTI_REPLAY = 0x1000000L;
    /**
     * Disable SSLv3 protocol.
     */
    public static final long SSL_OP_NO_SSLv3 = 0x2000000L;
    /**
     * Disable TLSv1.0 protocol.
     */
    public static final long SSL_OP_NO_TLSv1 = 0x4000000L;
    /**
     * Disable TLSv1.2 protocol.
     */
    public static final long SSL_OP_NO_TLSv1_2 = 0x8000000L;
    /**
     * Disable TLSv1.1 protocol.
     */
    public static final long SSL_OP_NO_TLSv1_1 = 0x10000000L;
    public static final long SSL_OP_NO_TLSv1_3 = 0x20000000L;
    public static final long SSL_OP_NO_DTLSv1 = SSL_OP_NO_TLSv1;
    public static final long SSL_OP_NO_DTLSv1_2 = SSL_OP_NO_TLSv1_2;
    public static final long SSL_OP_NO_DTLSv1_3 = SSL_OP_NO_TLSv1_3;
    /**
     * Disallow all renegotiation.
     */
    public static final long SSL_OP_NO_RENEGOTIATION = 0x40000000L;
    /**
     * Make server add server-hello extension from early version of cryptopro draft, when GOST ciphersuite is
     * negotiated. Required for interoperability with CryptoPro CSP 3.x
     */
    public static final long SSL_OP_CRYPTOPRO_TLSEXT_BUG = 0x80000000L;
    /**
     * Disable RFC8879 certificate compression. Don't send compressed certificates, and ignore the extension when
     * received.
     */
    public static final long SSL_OP_NO_TX_CERTIFICATE_COMPRESSION = 0x100000000L;
    /**
     * Disable RFC8879 certificate compression. Don't send the extension, and subsequently indicating that receiving is
     * not supported.
     */
    public static final long SSL_OP_NO_RX_CERTIFICATE_COMPRESSION = 0x200000000L;
    /**
     * Enable KTLS TX zerocopy on Linux.
     */
    public static final long SSL_OP_ENABLE_KTLS_TX_ZEROCOPY_SENDFILE = 0x400000000L;
    public static final long SSL_OP_PREFER_NO_DHE_KEX = 0x800000000L;
    public static final long SSL_OP_LEGACY_EC_POINT_FORMATS = 0x1000000000L;

    /**
     * Set this to tell client to emit greased ECH values.
     */
    public static final long SSL_OP_ECH_GREASE = 0x2000000000L;
    /**
     * If this is set then the server side will attempt trial decryption of ECHs even if there is no matching ECH
     * config_id. That's a bit inefficient, but more privacy friendly.
     */
    public static final long SSL_OP_ECH_TRIALDECRYPT = 0x4000000000L;
    /**
     * If set, clients will ignore the supplied ECH config_id and replace that with a random value.
     */
    public static final long SSL_OP_ECH_IGNORE_CID = 0x8000000000L;
    /**
     * If set, servers will add GREASEy ECHConfig values to those sent in retry_configs.
     */
    public static final long SSL_OP_ECH_GREASE_RETRY_CONFIG = 0x10000000000L;

    /**
     * RFC 8701: Send GREASE values in ClientHello.
     */
    public static final long SSL_OP_GREASE = 0x20000000000L;

    /*
     * Option "collections."
     */
    public static final long SSL_OP_NO_SSL_MASK =
            SSL_OP_NO_SSLv3 | SSL_OP_NO_TLSv1 | SSL_OP_NO_TLSv1_1 | SSL_OP_NO_TLSv1_2 | SSL_OP_NO_TLSv1_3;

    public static final long SSL_OP_NO_DTLS_MASK = SSL_OP_NO_DTLSv1 | SSL_OP_NO_DTLSv1_2;

    /**
     * Various bug workarounds that should be rather harmless.
     */
    public static final long SSL_OP_ALL =
            SSL_OP_CRYPTOPRO_TLSEXT_BUG | SSL_OP_DONT_INSERT_EMPTY_FRAGMENTS | SSL_OP_SAFARI_ECDHE_ECDSA_BUG;


    /*
     * OBSOLETE OPTIONS retained for compatibility.
     */
    @Deprecated
    public static final long SSL_OP_MICROSOFT_SESS_ID_BUG = 0x0;
    @Deprecated
    public static final long SSL_OP_NETSCAPE_CHALLENGE_BUG = 0x0;
    @Deprecated
    public static final long SSL_OP_NETSCAPE_REUSE_CIPHER_CHANGE_BUG = 0x0;
    @Deprecated
    public static final long SSL_OP_SSLREF2_REUSE_CERT_TYPE_BUG = 0x0;
    @Deprecated
    public static final long SSL_OP_MICROSOFT_BIG_SSLV3_BUFFER = 0x0;
    @Deprecated
    public static final long SSL_OP_MSIE_SSLV2_RSA_PADDING = 0x0;
    @Deprecated
    public static final long SSL_OP_SSLEAY_080_CLIENT_DH_BUG = 0x0;
    @Deprecated
    public static final long SSL_OP_TLS_D5_BUG = 0x0;
    @Deprecated
    public static final long SSL_OP_TLS_BLOCK_PADDING_BUG = 0x0;
    @Deprecated
    public static final long SSL_OP_SINGLE_ECDH_USE = 0x0;
    @Deprecated
    public static final long SSL_OP_SINGLE_DH_USE = 0x0;
    @Deprecated
    public static final long SSL_OP_EPHEMERAL_RSA = 0x0;
    @Deprecated
    public static final long SSL_OP_NO_SSLv2 = 0x0;
    @Deprecated
    public static final long SSL_OP_NETSCAPE_CA_DN_BUG = 0x0;
    @Deprecated
    public static final long SSL_OP_NETSCAPE_DEMO_CIPHER_CHANGE_BUG = 0x0;


    /**
     * Undefined certificate format.
     */
    public static final int SSL_CRT_FORMAT_UNDEF = 0;
    /**
     * ASN.1 certificate format.
     */
    public static final int SSL_CRT_FORMAT_ASN1 = 1;
    /**
     * Text certificate format.
     */
    public static final int SSL_CRT_FORMAT_TEXT = 2;
    /**
     * PEM certificate format.
     */
    public static final int SSL_CRT_FORMAT_PEM = 3;
    /**
     * Netscape certificate format.
     */
    public static final int SSL_CRT_FORMAT_NETSCAPE = 4;
    /**
     * PKCS12 certificate format.
     */
    public static final int SSL_CRT_FORMAT_PKCS12 = 5;
    /**
     * S/MIME certificate format.
     */
    public static final int SSL_CRT_FORMAT_SMIME = 6;
    /**
     * Engine certificate format.
     */
    public static final int SSL_CRT_FORMAT_ENGINE = 7;

    /**
     * Client SSL mode.
     */
    public static final int SSL_MODE_CLIENT = 0;
    /**
     * Server SSL mode.
     */
    public static final int SSL_MODE_SERVER = 1;
    /**
     * Combined client and server SSL mode.
     */
    public static final int SSL_MODE_COMBINED = 2;

    /**
     * Configuration flag for command line.
     */
    public static final int SSL_CONF_FLAG_CMDLINE = 0x0001;
    /**
     * Configuration flag for file.
     */
    public static final int SSL_CONF_FLAG_FILE = 0x0002;
    /**
     * Configuration flag for client.
     */
    public static final int SSL_CONF_FLAG_CLIENT = 0x0004;
    /**
     * Configuration flag for server.
     */
    public static final int SSL_CONF_FLAG_SERVER = 0x0008;
    /**
     * Configuration flag to show errors.
     */
    public static final int SSL_CONF_FLAG_SHOW_ERRORS = 0x0010;
    /**
     * Configuration flag for certificate context.
     */
    public static final int SSL_CONF_FLAG_CERTIFICATE = 0x0020;

    /**
     * Unknown configuration type.
     */
    public static final int SSL_CONF_TYPE_UNKNOWN = 0x0000;
    /**
     * String configuration type.
     */
    public static final int SSL_CONF_TYPE_STRING = 0x0001;
    /**
     * File configuration type.
     */
    public static final int SSL_CONF_TYPE_FILE = 0x0002;
    /**
     * Directory configuration type.
     */
    public static final int SSL_CONF_TYPE_DIR = 0x0003;

    /* Only support OFF and SERVER for now */
    /**
     * Session cache disabled.
     */
    public static final long SSL_SESS_CACHE_OFF = 0x0000;
    /**
     * Session cache enabled for server.
     */
    public static final long SSL_SESS_CACHE_SERVER = 0x0002;

    /**
     * Do not advertise protocol on selector failure.
     */
    public static final int SSL_SELECTOR_FAILURE_NO_ADVERTISE = 0;
    /**
     * Choose last protocol on selector failure.
     */
    public static final int SSL_SELECTOR_FAILURE_CHOOSE_MY_LAST_PROTOCOL = 1;

    /**
     * Return OpenSSL version number (run time version).
     *
     * @return OpenSSL version number
     */
    public static native int version();

    /**
     * Return OpenSSL version string (run time version).
     *
     * @return OpenSSL version string
     */
    public static native String versionString();

    /**
     * Initialize OpenSSL support. This function needs to be called once for the lifetime of JVM. Library.init() has to
     * be called before.
     *
     * @param engine Support for external a Crypto Device ("engine"), usually a hardware accelerator card for crypto
     *                   operations.
     *
     * @return APR status code
     */
    public static native int initialize(String engine);

    /**
     * Get the status of FIPS Mode.
     *
     * @return FIPS_mode return code. It is <code>0</code> if OpenSSL is not in FIPS mode, <code>1</code> if OpenSSL is
     *             in FIPS Mode.
     *
     * @throws Exception If tcnative was not compiled with FIPS Mode available.
     *
     * @see <a href="http://wiki.openssl.org/index.php/FIPS_mode%28%29">OpenSSL method FIPS_mode()</a>
     */
    public static native int fipsModeGet() throws Exception;

    /**
     * Enable/Disable FIPS Mode.
     *
     * @param mode 1 - enable, 0 - disable
     *
     * @return FIPS_mode_set return code
     *
     * @throws Exception If tcnative was not compiled with FIPS Mode available, or if {@code FIPS_mode_set()} call
     *                       returned an error value.
     *
     * @see <a href="http://wiki.openssl.org/index.php/FIPS_mode_set%28%29">OpenSSL method FIPS_mode_set()</a>
     */
    public static native int fipsModeSet(int mode) throws Exception;

    /**
     * Sets global random filename.
     *
     * @param filename Filename to use. If set it will be used for SSL initialization and all contexts where explicitly
     *                     not set.
     */
    public static native void randSet(String filename);

    /**
     * Return the handshake completed count.
     *
     * @param ssl SSL pointer
     *
     * @return the count
     */
    public static native int getHandshakeCount(long ssl);

    /*
     * Begin Twitter API additions
     */

    /**
     * Shutdown has been sent.
     */
    public static final int SSL_SENT_SHUTDOWN = 1;
    /**
     * Shutdown has been received.
     */
    public static final int SSL_RECEIVED_SHUTDOWN = 2;

    /**
     * No SSL error.
     */
    public static final int SSL_ERROR_NONE = 0;
    /**
     * SSL library error.
     */
    public static final int SSL_ERROR_SSL = 1;
    /**
     * SSL operation would block reading.
     */
    public static final int SSL_ERROR_WANT_READ = 2;
    /**
     * SSL operation would block writing.
     */
    public static final int SSL_ERROR_WANT_WRITE = 3;
    /**
     * SSL operation wants X.509 lookup.
     */
    public static final int SSL_ERROR_WANT_X509_LOOKUP = 4;
    /**
     * SSL syscall error.
     */
    public static final int SSL_ERROR_SYSCALL = 5; /* look at error stack/return value/errno */
    /**
     * SSL connection closed cleanly (zero return).
     */
    public static final int SSL_ERROR_ZERO_RETURN = 6;
    /**
     * SSL operation wants connect.
     */
    public static final int SSL_ERROR_WANT_CONNECT = 7;
    /**
     * SSL operation wants accept.
     */
    public static final int SSL_ERROR_WANT_ACCEPT = 8;

    /**
     * SSL_new
     *
     * @param ctx    Server or Client context to use.
     * @param server if true configure SSL instance to use accept handshake routines if false configure SSL instance to
     *                   use connect handshake routines
     *
     * @return pointer to SSL instance (SSL *)
     */
    public static native long newSSL(long ctx, boolean server);

    /**
     * BIO_ctrl_pending.
     *
     * @param bio BIO pointer (BIO *)
     *
     * @return the pending bytes count
     */
    public static native int pendingWrittenBytesInBIO(long bio);

    /**
     * SSL_pending.
     *
     * @param ssl SSL pointer (SSL *)
     *
     * @return the pending bytes count
     */
    public static native int pendingReadableBytesInSSL(long ssl);

    /**
     * BIO_write.
     *
     * @param bio  BIO pointer
     * @param wbuf Buffer pointer
     * @param wlen Write length
     *
     * @return the bytes count written
     */
    public static native int writeToBIO(long bio, long wbuf, int wlen);

    /**
     * BIO_read.
     *
     * @param bio  BIO pointer
     * @param rbuf Buffer pointer
     * @param rlen Read length
     *
     * @return the bytes count read
     */
    public static native int readFromBIO(long bio, long rbuf, int rlen);

    /**
     * SSL_write.
     *
     * @param ssl  the SSL instance (SSL *)
     * @param wbuf Buffer pointer
     * @param wlen Write length
     *
     * @return the bytes count written
     */
    public static native int writeToSSL(long ssl, long wbuf, int wlen);

    /**
     * SSL_read
     *
     * @param ssl  the SSL instance (SSL *)
     * @param rbuf Buffer pointer
     * @param rlen Read length
     *
     * @return the bytes count read
     */
    public static native int readFromSSL(long ssl, long rbuf, int rlen);

    /**
     * SSL_get_shutdown
     *
     * @param ssl the SSL instance (SSL *)
     *
     * @return the operation status
     */
    public static native int getShutdown(long ssl);

    /**
     * SSL_free
     *
     * @param ssl the SSL instance (SSL *)
     */
    public static native void freeSSL(long ssl);

    /**
     * Wire up internal and network BIOs for the given SSL instance.
     * <p>
     * <b>Warning: you must explicitly free this resource by calling freeBIO</b>
     * <p>
     * While the SSL's internal/application data BIO will be freed when freeSSL is called on the provided SSL instance,
     * you must call freeBIO on the returned network BIO.
     *
     * @param ssl the SSL instance (SSL *)
     *
     * @return pointer to the Network BIO (BIO *)
     */
    public static native long makeNetworkBIO(long ssl);

    /**
     * BIO_free
     *
     * @param bio BIO pointer
     */
    public static native void freeBIO(long bio);

    /**
     * SSL_shutdown
     *
     * @param ssl the SSL instance (SSL *)
     *
     * @return the operation status
     */
    public static native int shutdownSSL(long ssl);

    /**
     * Get the error number representing the last error OpenSSL encountered on this thread.
     *
     * @return the last error number
     */
    public static native int getLastErrorNumber();

    /**
     * SSL_get_cipher.
     *
     * @param ssl the SSL instance (SSL *)
     *
     * @return the cipher name
     */
    public static native String getCipherForSSL(long ssl);

    /**
     * SSL_get_version
     *
     * @param ssl the SSL instance (SSL *)
     *
     * @return the SSL version in use
     */
    public static native String getVersion(long ssl);

    /**
     * SSL_do_handshake
     *
     * @param ssl the SSL instance (SSL *)
     *
     * @return the handshake status
     */
    public static native int doHandshake(long ssl);

    /**
     * SSL_renegotiate
     *
     * @param ssl the SSL instance (SSL *)
     *
     * @return the operation status
     */
    public static native int renegotiate(long ssl);

    /**
     * SSL_renegotiate_pending
     *
     * @param ssl the SSL instance (SSL *)
     *
     * @return the operation status
     */
    public static native int renegotiatePending(long ssl);

    /**
     * SSL_verify_client_post_handshake
     *
     * @param ssl the SSL instance (SSL *)
     *
     * @return the operation status
     */
    public static native int verifyClientPostHandshake(long ssl);

    /**
     * Is post handshake authentication in progress on this connection?
     *
     * @param ssl the SSL instance (SSL *)
     *
     * @return the operation status
     */
    public static native int getPostHandshakeAuthInProgress(long ssl);

    /**
     * Marks post handshake authentication complete for the connection. Used when JSSE is performing certificate
     * verification for OpenSSL.
     *
     * @param ssl the SSL instance (SSL *)
     */
    public static native void markPostHandshakeAuthComplete(long ssl);

    /**
     * SSL_in_init.
     *
     * @param ssl the SSL instance (SSL *)
     *
     * @return the status
     */
    public static native int isInInit(long ssl);

    /*
     * End Twitter API Additions
     */

    /**
     * SSL_get0_alpn_selected
     *
     * @param ssl the SSL instance (SSL *)
     *
     * @return the ALPN protocol negotiated
     */
    public static native String getAlpnSelected(long ssl);

    /**
     * Get the peer certificate chain or {@code null} if none was sent.
     *
     * @param ssl the SSL instance (SSL *)
     *
     * @return the certificate chain bytes
     */
    public static native byte[][] getPeerCertChain(long ssl);

    /**
     * Get the peer certificate or {@code null} if none was sent.
     *
     * @param ssl the SSL instance (SSL *)
     *
     * @return the certificate bytes
     */
    public static native byte[] getPeerCertificate(long ssl);

    /**
     * Get the error number representing for the given {@code errorNumber}.
     *
     * @param errorNumber The error code
     *
     * @return an error message
     */
    public static native String getErrorString(long errorNumber);

    /**
     * SSL_get_time
     *
     * @param ssl the SSL instance (SSL *)
     *
     * @return returns the time at which the session ssl was established. The time is given in seconds since the Epoch
     */
    public static native long getTime(long ssl);

    /**
     * Set Type of Client Certificate verification and Maximum depth of CA Certificates in Client Certificate
     * verification. <br>
     * This directive sets the Certificate verification level for the Client Authentication. Notice that this directive
     * can be used both in per-server and per-directory context. In per-server context it applies to the client
     * authentication process used in the standard SSL handshake when a connection is established. In per-directory
     * context it forces an SSL renegotiation with the reconfigured client verification level after the HTTP request was
     * read but before the HTTP response is sent. <br>
     * The following levels are available for level:
     *
     * <pre>
     * SSL_CVERIFY_NONE           - No client Certificate is required at all
     * SSL_CVERIFY_OPTIONAL       - The client may present a valid Certificate
     * SSL_CVERIFY_REQUIRE        - The client has to present a valid Certificate
     * SSL_CVERIFY_OPTIONAL_NO_CA - The client may present a valid Certificate
     *                              but it need not to be (successfully) verifiable
     * </pre>
     *
     * <br>
     * The depth actually is the maximum number of intermediate certificate issuers, i.e. the number of CA certificates
     * which are max allowed to be followed while verifying the client certificate. A depth of 0 means that self-signed
     * client certificates are accepted only, the default depth of 1 means the client certificate can be self-signed or
     * has to be signed by a CA which is directly known to the server (i.e. the CA's certificate is under
     * {@code setCACertificatePath}, etc).
     *
     * @param ssl   the SSL instance (SSL *)
     * @param level Type of Client Certificate verification.
     * @param depth Maximum depth of CA Certificates in Client Certificate verification.
     */
    public static native void setVerify(long ssl, int level, int depth);

    /**
     * Set OpenSSL Option.
     *
     * @param ssl     the SSL instance (SSL *)
     * @param options See SSL.SSL_OP_* for option flags.
     *
     * @deprecated Use {@link #setOptionsLong(long, long)}
     */
    @Deprecated
    public static native void setOptions(long ssl, int options);

    /**
     * Get OpenSSL Option.
     *
     * @param ssl the SSL instance (SSL *)
     *
     * @return options See SSL.SSL_OP_* for option flags.
     *
     * @deprecated Use {@link SSL#getOptionsLong(long)}
     */
    @Deprecated
    public static native int getOptions(long ssl);

    /**
     * Set OpenSSL Option.
     *
     * @param ssl     the SSL instance (SSL *)
     * @param options See SSL.SSL_OP_* for option flags.
     */
    public static native void setOptionsLong(long ssl, long options);

    /**
     * Get OpenSSL Option.
     *
     * @param ssl the SSL instance (SSL *)
     *
     * @return options See SSL.SSL_OP_* for option flags.
     */
    public static native long getOptionsLong(long ssl);

    /**
     * Returns all cipher suites that are enabled for negotiation in an SSL handshake.
     *
     * @param ssl the SSL instance (SSL *)
     *
     * @return ciphers
     */
    public static native String[] getCiphers(long ssl);

    /**
     * Set the TLSv1.2 and below ciphers available for negotiation the in TLS handshake.
     * <p>
     * This complex directive uses a colon-separated cipher-spec string consisting of OpenSSL cipher specifications to
     * configure the ciphers the client is permitted to negotiate in the TLS handshake phase.
     *
     * @param ssl        The SSL instance (SSL *)
     * @param cipherList An OpenSSL cipher specification.
     *
     * @return <code>true</code> if the operation was successful
     *
     * @throws Exception An error occurred
     */
    public static native boolean setCipherSuites(long ssl, String cipherList) throws Exception;

    /**
     * Set the TLSv1.3 cipher suites available for negotiation the in TLS handshake.
     * <p>
     * This uses a colon-separated list of TLSv1.3 cipher suite names in preference order.
     *
     * @param ssl          The SSL instance (SSL *)
     * @param cipherSuites An OpenSSL cipher suite list.
     *
     * @return <code>true</code> if the operation was successful
     *
     * @throws Exception An error occurred
     */
    public static native boolean setCipherSuitesEx(long ssl, String cipherSuites) throws Exception;

    /**
     * Returns the ID of the session as byte array representation.
     *
     * @param ssl the SSL instance (SSL *)
     *
     * @return the session as byte array representation obtained via SSL_SESSION_get_id.
     */
    public static native byte[] getSessionId(long ssl);
}
