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

/** SSL
 *
 * @author Mladen Turk
 */
public final class SSL {

    /*
     * Type definitions mostly from mod_ssl
     */
    public static final int UNSET            = -1;
    /*
     * Define the certificate algorithm types
     */
    public static final int SSL_ALGO_UNKNOWN = 0;
    public static final int SSL_ALGO_RSA     = (1<<0);
    public static final int SSL_ALGO_DSA     = (1<<1);
    public static final int SSL_ALGO_ALL     = (SSL_ALGO_RSA|SSL_ALGO_DSA);

    public static final int SSL_AIDX_RSA     = 0;
    public static final int SSL_AIDX_DSA     = 1;
    public static final int SSL_AIDX_ECC     = 3;
    public static final int SSL_AIDX_MAX     = 4;
    /*
     * Define IDs for the temporary RSA keys and DH params
     */

    public static final int SSL_TMP_KEY_RSA_512  = 0;
    public static final int SSL_TMP_KEY_RSA_1024 = 1;
    public static final int SSL_TMP_KEY_RSA_2048 = 2;
    public static final int SSL_TMP_KEY_RSA_4096 = 3;
    public static final int SSL_TMP_KEY_DH_512   = 4;
    public static final int SSL_TMP_KEY_DH_1024  = 5;
    public static final int SSL_TMP_KEY_DH_2048  = 6;
    public static final int SSL_TMP_KEY_DH_4096  = 7;
    public static final int SSL_TMP_KEY_MAX      = 8;

    /*
     * Define the SSL options
     */
    public static final int SSL_OPT_NONE           = 0;
    public static final int SSL_OPT_RELSET         = (1<<0);
    public static final int SSL_OPT_STDENVVARS     = (1<<1);
    public static final int SSL_OPT_EXPORTCERTDATA = (1<<3);
    public static final int SSL_OPT_FAKEBASICAUTH  = (1<<4);
    public static final int SSL_OPT_STRICTREQUIRE  = (1<<5);
    public static final int SSL_OPT_OPTRENEGOTIATE = (1<<6);
    public static final int SSL_OPT_ALL            = (SSL_OPT_STDENVVARS|SSL_OPT_EXPORTCERTDATA|SSL_OPT_FAKEBASICAUTH|SSL_OPT_STRICTREQUIRE|SSL_OPT_OPTRENEGOTIATE);

    /*
     * Define the SSL Protocol options
     */
    public static final int SSL_PROTOCOL_NONE  = 0;
    public static final int SSL_PROTOCOL_SSLV2 = (1<<0);
    public static final int SSL_PROTOCOL_SSLV3 = (1<<1);
    public static final int SSL_PROTOCOL_TLSV1 = (1<<2);
    public static final int SSL_PROTOCOL_TLSV1_1 = (1<<3);
    public static final int SSL_PROTOCOL_TLSV1_2 = (1<<4);
    public static final int SSL_PROTOCOL_TLSV1_3 = (1<<5);
    public static final int SSL_PROTOCOL_ALL;

    static {
        if (SSL.version() >= 0x1010100f) {
            SSL_PROTOCOL_ALL = (SSL_PROTOCOL_TLSV1 | SSL_PROTOCOL_TLSV1_1 | SSL_PROTOCOL_TLSV1_2 |
                    SSL_PROTOCOL_TLSV1_3);
        } else {
            SSL_PROTOCOL_ALL = (SSL_PROTOCOL_TLSV1 | SSL_PROTOCOL_TLSV1_1 | SSL_PROTOCOL_TLSV1_2);
        }
    }


    /*
     * Define the SSL verify levels
     */
    public static final int SSL_CVERIFY_UNSET          = UNSET;
    public static final int SSL_CVERIFY_NONE           = 0;
    public static final int SSL_CVERIFY_OPTIONAL       = 1;
    public static final int SSL_CVERIFY_REQUIRE        = 2;
    public static final int SSL_CVERIFY_OPTIONAL_NO_CA = 3;

    /* Use either SSL_VERIFY_NONE or SSL_VERIFY_PEER, the last 2 options
     * are 'ored' with SSL_VERIFY_PEER if they are desired
     */
    public static final int SSL_VERIFY_NONE                 = 0;
    public static final int SSL_VERIFY_PEER                 = 1;
    public static final int SSL_VERIFY_FAIL_IF_NO_PEER_CERT = 2;
    public static final int SSL_VERIFY_CLIENT_ONCE          = 4;
    public static final int SSL_VERIFY_PEER_STRICT          = (SSL_VERIFY_PEER|SSL_VERIFY_FAIL_IF_NO_PEER_CERT);

    public static final int SSL_OP_MICROSOFT_SESS_ID_BUG            = 0x00000001;
    public static final int SSL_OP_NETSCAPE_CHALLENGE_BUG           = 0x00000002;
    public static final int SSL_OP_NETSCAPE_REUSE_CIPHER_CHANGE_BUG = 0x00000008;
    public static final int SSL_OP_SSLREF2_REUSE_CERT_TYPE_BUG      = 0x00000010;
    public static final int SSL_OP_MICROSOFT_BIG_SSLV3_BUFFER       = 0x00000020;
    public static final int SSL_OP_MSIE_SSLV2_RSA_PADDING           = 0x00000040;
    public static final int SSL_OP_SSLEAY_080_CLIENT_DH_BUG         = 0x00000080;
    public static final int SSL_OP_TLS_D5_BUG                       = 0x00000100;
    public static final int SSL_OP_TLS_BLOCK_PADDING_BUG            = 0x00000200;

    /* Disable SSL 3.0/TLS 1.0 CBC vulnerability workaround that was added
     * in OpenSSL 0.9.6d.  Usually (depending on the application protocol)
     * the workaround is not needed.  Unfortunately some broken SSL/TLS
     * implementations cannot handle it at all, which is why we include
     * it in SSL_OP_ALL. */
    public static final int SSL_OP_DONT_INSERT_EMPTY_FRAGMENTS      = 0x00000800;

    /* SSL_OP_ALL: various bug workarounds that should be rather harmless.
     *             This used to be 0x000FFFFFL before 0.9.7. */
    public static final int SSL_OP_ALL                              = 0x00000FFF;
    /* As server, disallow session resumption on renegotiation */
    public static final int SSL_OP_NO_SESSION_RESUMPTION_ON_RENEGOTIATION = 0x00010000;
    /* Don't use compression even if supported */
    public static final int SSL_OP_NO_COMPRESSION                         = 0x00020000;
    /* Permit unsafe legacy renegotiation */
    public static final int SSL_OP_ALLOW_UNSAFE_LEGACY_RENEGOTIATION      = 0x00040000;
    /* If set, always create a new key when using tmp_eddh parameters */
    public static final int SSL_OP_SINGLE_ECDH_USE                  = 0x00080000;
    /* If set, always create a new key when using tmp_dh parameters */
    public static final int SSL_OP_SINGLE_DH_USE                    = 0x00100000;
    /* Set to always use the tmp_rsa key when doing RSA operations,
     * even when this violates protocol specs */
    public static final int SSL_OP_EPHEMERAL_RSA                    = 0x00200000;
    /* Set on servers to choose the cipher according to the server's
     * preferences */
    public static final int SSL_OP_CIPHER_SERVER_PREFERENCE         = 0x00400000;
    /* If set, a server will allow a client to issue an SSLv3.0 version number
     * as latest version supported in the premaster secret, even when TLSv1.0
     * (version 3.1) was announced in the client hello. Normally this is
     * forbidden to prevent version rollback attacks. */
    public static final int SSL_OP_TLS_ROLLBACK_BUG                 = 0x00800000;

    public static final int SSL_OP_NO_SSLv2                         = 0x01000000;
    public static final int SSL_OP_NO_SSLv3                         = 0x02000000;
    public static final int SSL_OP_NO_TLSv1                         = 0x04000000;
    public static final int SSL_OP_NO_TLSv1_2                       = 0x08000000;
    public static final int SSL_OP_NO_TLSv1_1                       = 0x10000000;

    public static final int SSL_OP_NO_TICKET                        = 0x00004000;

    // SSL_OP_PKCS1_CHECK_1 and SSL_OP_PKCS1_CHECK_2 flags are unsupported
    // in the current version of OpenSSL library. See ssl.h changes in commit
    // 7409d7ad517650db332ae528915a570e4e0ab88b (30 Apr 2011) of OpenSSL.
    /**
     * @deprecated Unsupported in the current version of OpenSSL
     */
    @Deprecated
    public static final int SSL_OP_PKCS1_CHECK_1                    = 0x08000000;
    /**
     * @deprecated Unsupported in the current version of OpenSSL
     */
    @Deprecated
    public static final int SSL_OP_PKCS1_CHECK_2                    = 0x10000000;
    public static final int SSL_OP_NETSCAPE_CA_DN_BUG               = 0x20000000;
    public static final int SSL_OP_NETSCAPE_DEMO_CIPHER_CHANGE_BUG  = 0x40000000;

    public static final int SSL_CRT_FORMAT_UNDEF    = 0;
    public static final int SSL_CRT_FORMAT_ASN1     = 1;
    public static final int SSL_CRT_FORMAT_TEXT     = 2;
    public static final int SSL_CRT_FORMAT_PEM      = 3;
    public static final int SSL_CRT_FORMAT_NETSCAPE = 4;
    public static final int SSL_CRT_FORMAT_PKCS12   = 5;
    public static final int SSL_CRT_FORMAT_SMIME    = 6;
    public static final int SSL_CRT_FORMAT_ENGINE   = 7;

    public static final int SSL_MODE_CLIENT         = 0;
    public static final int SSL_MODE_SERVER         = 1;
    public static final int SSL_MODE_COMBINED       = 2;

    public static final int SSL_CONF_FLAG_CMDLINE       = 0x0001;
    public static final int SSL_CONF_FLAG_FILE          = 0x0002;
    public static final int SSL_CONF_FLAG_CLIENT        = 0x0004;
    public static final int SSL_CONF_FLAG_SERVER        = 0x0008;
    public static final int SSL_CONF_FLAG_SHOW_ERRORS   = 0x0010;
    public static final int SSL_CONF_FLAG_CERTIFICATE   = 0x0020;

    public static final int SSL_CONF_TYPE_UNKNOWN   = 0x0000;
    public static final int SSL_CONF_TYPE_STRING    = 0x0001;
    public static final int SSL_CONF_TYPE_FILE      = 0x0002;
    public static final int SSL_CONF_TYPE_DIR       = 0x0003;

    public static final int SSL_SHUTDOWN_TYPE_UNSET    = 0;
    public static final int SSL_SHUTDOWN_TYPE_STANDARD = 1;
    public static final int SSL_SHUTDOWN_TYPE_UNCLEAN  = 2;
    public static final int SSL_SHUTDOWN_TYPE_ACCURATE = 3;

    public static final int SSL_INFO_SESSION_ID                = 0x0001;
    public static final int SSL_INFO_CIPHER                    = 0x0002;
    public static final int SSL_INFO_CIPHER_USEKEYSIZE         = 0x0003;
    public static final int SSL_INFO_CIPHER_ALGKEYSIZE         = 0x0004;
    public static final int SSL_INFO_CIPHER_VERSION            = 0x0005;
    public static final int SSL_INFO_CIPHER_DESCRIPTION        = 0x0006;
    public static final int SSL_INFO_PROTOCOL                  = 0x0007;

    /* To obtain the CountryName of the Client Certificate Issuer
     * use the SSL_INFO_CLIENT_I_DN + SSL_INFO_DN_COUNTRYNAME
     */
    public static final int SSL_INFO_CLIENT_S_DN               = 0x0010;
    public static final int SSL_INFO_CLIENT_I_DN               = 0x0020;
    public static final int SSL_INFO_SERVER_S_DN               = 0x0040;
    public static final int SSL_INFO_SERVER_I_DN               = 0x0080;

    public static final int SSL_INFO_DN_COUNTRYNAME            = 0x0001;
    public static final int SSL_INFO_DN_STATEORPROVINCENAME    = 0x0002;
    public static final int SSL_INFO_DN_LOCALITYNAME           = 0x0003;
    public static final int SSL_INFO_DN_ORGANIZATIONNAME       = 0x0004;
    public static final int SSL_INFO_DN_ORGANIZATIONALUNITNAME = 0x0005;
    public static final int SSL_INFO_DN_COMMONNAME             = 0x0006;
    public static final int SSL_INFO_DN_TITLE                  = 0x0007;
    public static final int SSL_INFO_DN_INITIALS               = 0x0008;
    public static final int SSL_INFO_DN_GIVENNAME              = 0x0009;
    public static final int SSL_INFO_DN_SURNAME                = 0x000A;
    public static final int SSL_INFO_DN_DESCRIPTION            = 0x000B;
    public static final int SSL_INFO_DN_UNIQUEIDENTIFIER       = 0x000C;
    public static final int SSL_INFO_DN_EMAILADDRESS           = 0x000D;

    public static final int SSL_INFO_CLIENT_M_VERSION          = 0x0101;
    public static final int SSL_INFO_CLIENT_M_SERIAL           = 0x0102;
    public static final int SSL_INFO_CLIENT_V_START            = 0x0103;
    public static final int SSL_INFO_CLIENT_V_END              = 0x0104;
    public static final int SSL_INFO_CLIENT_A_SIG              = 0x0105;
    public static final int SSL_INFO_CLIENT_A_KEY              = 0x0106;
    public static final int SSL_INFO_CLIENT_CERT               = 0x0107;
    public static final int SSL_INFO_CLIENT_V_REMAIN           = 0x0108;

    public static final int SSL_INFO_SERVER_M_VERSION          = 0x0201;
    public static final int SSL_INFO_SERVER_M_SERIAL           = 0x0202;
    public static final int SSL_INFO_SERVER_V_START            = 0x0203;
    public static final int SSL_INFO_SERVER_V_END              = 0x0204;
    public static final int SSL_INFO_SERVER_A_SIG              = 0x0205;
    public static final int SSL_INFO_SERVER_A_KEY              = 0x0206;
    public static final int SSL_INFO_SERVER_CERT               = 0x0207;
    /* Return client certificate chain.
     * Add certificate chain number to that flag (0 ... verify depth)
     */
    public static final int SSL_INFO_CLIENT_CERT_CHAIN         = 0x0400;

    /* Only support OFF and SERVER for now */
    public static final long SSL_SESS_CACHE_OFF = 0x0000;
    public static final long SSL_SESS_CACHE_SERVER = 0x0002;

    public static final int SSL_SELECTOR_FAILURE_NO_ADVERTISE = 0;
    public static final int SSL_SELECTOR_FAILURE_CHOOSE_MY_LAST_PROTOCOL = 1;

    /* Return OpenSSL version number (compile time version, if version < 1.1.0) */
    public static native int version();

    /* Return OpenSSL version string (run time version) */
    public static native String versionString();

    /**
     * Initialize OpenSSL support.
     * This function needs to be called once for the
     * lifetime of JVM. Library.init() has to be called before.
     * @param engine Support for external a Crypto Device ("engine"),
     *                usually
     * a hardware accelerator card for crypto operations.
     * @return APR status code
     */
    public static native int initialize(String engine);

    /**
     * Get the status of FIPS Mode.
     *
     * @return FIPS_mode return code. It is <code>0</code> if OpenSSL is not
     *  in FIPS mode, <code>1</code> if OpenSSL is in FIPS Mode.
     * @throws Exception If tcnative was not compiled with FIPS Mode available.
     * @see <a href="http://wiki.openssl.org/index.php/FIPS_mode%28%29">OpenSSL method FIPS_mode()</a>
     */
    public static native int fipsModeGet() throws Exception;

    /**
     * Enable/Disable FIPS Mode.
     *
     * @param mode 1 - enable, 0 - disable
     *
     * @return FIPS_mode_set return code
     * @throws Exception If tcnative was not compiled with FIPS Mode available,
     *  or if {@code FIPS_mode_set()} call returned an error value.
     * @see <a href="http://wiki.openssl.org/index.php/FIPS_mode_set%28%29">OpenSSL method FIPS_mode_set()</a>
     */
    public static native int fipsModeSet(int mode) throws Exception;

    /**
     * Add content of the file to the PRNG
     * @param filename Filename containing random data.
     *        If null the default file will be tested.
     *        The seed file is $RANDFILE if that environment variable is
     *        set, $HOME/.rnd otherwise.
     *        In case both files are unavailable builtin
     *        random seed generator is used.
     * @return <code>true</code> if the operation was successful
     *
     * @deprecated Unused. Will be removed in Tomcat 10.1
     */
    @Deprecated
    public static native boolean randLoad(String filename);

    /**
     * Writes a number of random bytes (currently 1024) to
     * file <code>filename</code> which can be used to initialize the PRNG
     * by calling randLoad in a later session.
     * @param filename Filename to save the data
     * @return <code>true</code> if the operation was successful
     *
     * @deprecated Unused. Will be removed in Tomcat 10.1
     */
    @Deprecated
    public static native boolean randSave(String filename);

    /**
     * Creates random data to filename
     * @param filename Filename to save the data
     * @param len The length of random sequence in bytes
     * @param base64 Output the data in Base64 encoded format
     * @return <code>true</code> if the operation was successful
     *
     * @deprecated Unused. Will be removed in Tomcat 10.1
     */
    @Deprecated
    public static native boolean randMake(String filename, int len,
                                          boolean base64);

    /**
     * Sets global random filename.
     *
     * @param filename Filename to use.
     *        If set it will be used for SSL initialization
     *        and all contexts where explicitly not set.
     */
    public static native void randSet(String filename);

    /**
     * Initialize new BIO
     * @param pool The pool to use.
     * @param callback BIOCallback to use
     * @return New BIO handle
     * @throws Exception An error occurred
     *
     * @deprecated Unused. Will be removed in Tomcat 10.1
     */
    @Deprecated
    public static native long newBIO(long pool, BIOCallback callback)
            throws Exception;

    /**
     * Close BIO and dereference callback object
     * @param bio BIO to close and destroy.
     * @return APR Status code
     *
     * @deprecated Unused. Will be removed in Tomcat 10.1
     */
    @Deprecated
    public static native int closeBIO(long bio);

    /**
     * Set global Password callback for obtaining passwords.
     * @param callback PasswordCallback implementation to use.
     *
     * @deprecated Unused. Will be removed in Tomcat 10.1
     */
    @Deprecated
    public static native void setPasswordCallback(PasswordCallback callback);

    /**
     * Set global Password for decrypting certificates and keys.
     * @param password Password to use.
     *
     * @deprecated Unused. Will be removed in Tomcat 10.1
     */
    @Deprecated
    public static native void setPassword(String password);

    /**
     * Return last SSL error string
     * @return the error string
     *
     * @deprecated Unused. Will be removed in Tomcat 10.1
     */
    @Deprecated
    public static native String getLastError();

    /**
     * Return true if all the requested SSL_OP_* are supported by OpenSSL.
     *
     * <i>Note that for versions of tcnative &lt; 1.1.25, this method will
     * return <code>true</code> if and only if <code>op</code>=
     * {@link #SSL_OP_ALLOW_UNSAFE_LEGACY_RENEGOTIATION} and tcnative
     * supports that flag.</i>
     *
     * @param op Bitwise-OR of all SSL_OP_* to test.
     *
     * @return true if all SSL_OP_* are supported by OpenSSL library.
     *
     * @deprecated Unused. Will be removed in Tomcat 10.1
     */
    @Deprecated
    public static native boolean hasOp(int op);

    /**
     * Return the handshake completed count.
     * @param ssl SSL pointer
     * @return the count
     */
    public static native int getHandshakeCount(long ssl);

    /*
     * Begin Twitter API additions
     */

    public static final int SSL_SENT_SHUTDOWN = 1;
    public static final int SSL_RECEIVED_SHUTDOWN = 2;

    public static final int SSL_ERROR_NONE             = 0;
    public static final int SSL_ERROR_SSL              = 1;
    public static final int SSL_ERROR_WANT_READ        = 2;
    public static final int SSL_ERROR_WANT_WRITE       = 3;
    public static final int SSL_ERROR_WANT_X509_LOOKUP = 4;
    public static final int SSL_ERROR_SYSCALL          = 5; /* look at error stack/return value/errno */
    public static final int SSL_ERROR_ZERO_RETURN      = 6;
    public static final int SSL_ERROR_WANT_CONNECT     = 7;
    public static final int SSL_ERROR_WANT_ACCEPT      = 8;

    /**
     * SSL_new
     * @param ctx Server or Client context to use.
     * @param server if true configure SSL instance to use accept handshake routines
     *               if false configure SSL instance to use connect handshake routines
     * @return pointer to SSL instance (SSL *)
     */
    public static native long newSSL(long ctx, boolean server);

    /**
     * SSL_set_bio
     * @param ssl SSL pointer (SSL *)
     * @param rbio read BIO pointer (BIO *)
     * @param wbio write BIO pointer (BIO *)
     *
     * @deprecated Unused. Will be removed in Tomcat 10.1
     */
    @Deprecated
    public static native void setBIO(long ssl, long rbio, long wbio);

    /**
     * SSL_get_error
     * @param ssl SSL pointer (SSL *)
     * @param ret TLS/SSL I/O return value
     * @return the error status
     *
     * @deprecated Unused. Will be removed in Tomcat 10.1
     */
    @Deprecated
    public static native int getError(long ssl, int ret);

    /**
     * BIO_ctrl_pending.
     * @param bio BIO pointer (BIO *)
     * @return the pending bytes count
     */
    public static native int pendingWrittenBytesInBIO(long bio);

    /**
     * SSL_pending.
     * @param ssl SSL pointer (SSL *)
     * @return the pending bytes count
     */
    public static native int pendingReadableBytesInSSL(long ssl);

    /**
     * BIO_write.
     * @param bio BIO pointer
     * @param wbuf Buffer pointer
     * @param wlen Write length
     * @return the bytes count written
     */
    public static native int writeToBIO(long bio, long wbuf, int wlen);

    /**
     * BIO_read.
     * @param bio BIO pointer
     * @param rbuf Buffer pointer
     * @param rlen Read length
     * @return the bytes count read
     */
    public static native int readFromBIO(long bio, long rbuf, int rlen);

    /**
     * SSL_write.
     * @param ssl the SSL instance (SSL *)
     * @param wbuf Buffer pointer
     * @param wlen Write length
     * @return the bytes count written
     */
    public static native int writeToSSL(long ssl, long wbuf, int wlen);

    /**
     * SSL_read
     * @param ssl the SSL instance (SSL *)
     * @param rbuf Buffer pointer
     * @param rlen Read length
     * @return the bytes count read
     */
    public static native int readFromSSL(long ssl, long rbuf, int rlen);

    /**
     * SSL_get_shutdown
     * @param ssl the SSL instance (SSL *)
     * @return the operation status
     */
    public static native int getShutdown(long ssl);

    /**
     * SSL_set_shutdown
     * @param ssl the SSL instance (SSL *)
     * @param mode Shutdown mode
     *
     * @deprecated Unused. Will be removed in Tomcat 10.1
     */
    @Deprecated
    public static native void setShutdown(long ssl, int mode);

    /**
     * SSL_free
     * @param ssl the SSL instance (SSL *)
     */
    public static native void freeSSL(long ssl);

    /**
     * Wire up internal and network BIOs for the given SSL instance.
     *
     * <b>Warning: you must explicitly free this resource by calling freeBIO</b>
     *
     * While the SSL's internal/application data BIO will be freed when freeSSL is called on
     * the provided SSL instance, you must call freeBIO on the returned network BIO.
     *
     * @param ssl the SSL instance (SSL *)
     * @return pointer to the Network BIO (BIO *)
     */
    public static native long makeNetworkBIO(long ssl);

    /**
     * BIO_free
     * @param bio BIO pointer
     */
    public static native void freeBIO(long bio);

    /**
     * SSL_shutdown
     * @param ssl the SSL instance (SSL *)
     * @return the operation status
     */
    public static native int shutdownSSL(long ssl);

    /**
     * Get the error number representing the last error OpenSSL encountered on
     * this thread.
     * @return the last error number
     */
    public static native int getLastErrorNumber();

    /**
     * SSL_get_cipher.
     * @param ssl the SSL instance (SSL *)
     * @return the cipher name
     */
    public static native String getCipherForSSL(long ssl);

    /**
     * SSL_get_version
     * @param ssl the SSL instance (SSL *)
     * @return the SSL version in use
     */
    public static native String getVersion(long ssl);

    /**
     * SSL_do_handshake
     * @param ssl the SSL instance (SSL *)
     * @return the handshake status
     */
    public static native int doHandshake(long ssl);

    /**
     * SSL_renegotiate
     * @param ssl the SSL instance (SSL *)
     * @return the operation status
     */
    public static native int renegotiate(long ssl);

    /**
     * SSL_renegotiate_pending
     * @param ssl the SSL instance (SSL *)
     * @return the operation status
     */
    public static native int renegotiatePending(long ssl);

    /**
     * SSL_verify_client_post_handshake
     * @param ssl the SSL instance (SSL *)
     * @return the operation status
     */
    public static native int verifyClientPostHandshake(long ssl);

    /**
     * Is post handshake authentication in progress on this connection?
     * @param ssl the SSL instance (SSL *)
     * @return the operation status
     */
    public static native int getPostHandshakeAuthInProgress(long ssl);

    /**
     * SSL_in_init.
     * @param ssl the SSL instance (SSL *)
     * @return the status
     */
    public static native int isInInit(long ssl);

    /**
     * SSL_get0_next_proto_negotiated
     * @param ssl the SSL instance (SSL *)
     * @return the NPN protocol negotiated
     *
     * @deprecated Unused. Will be removed in Tomcat 10.1.x
     */
    @Deprecated
    public static native String getNextProtoNegotiated(long ssl);

    /*
     * End Twitter API Additions
     */

    /**
     * SSL_get0_alpn_selected
     * @param ssl the SSL instance (SSL *)
     * @return the ALPN protocol negotiated
     */
    public static native String getAlpnSelected(long ssl);

    /**
     * Get the peer certificate chain or {@code null} if non was send.
     * @param ssl the SSL instance (SSL *)
     * @return the certificate chain bytes
     */
    public static native byte[][] getPeerCertChain(long ssl);

    /**
     * Get the peer certificate or {@code null} if non was send.
     * @param ssl the SSL instance (SSL *)
     * @return the certificate bytes
     */
    public static native byte[] getPeerCertificate(long ssl);

    /**
     * Get the error number representing for the given {@code errorNumber}.
     * @param errorNumber The error code
     * @return an error message
     */
    public static native String getErrorString(long errorNumber);

    /**
     * SSL_get_time
     * @param ssl the SSL instance (SSL *)
     * @return returns the time at which the session ssl was established. The time is given in seconds since the Epoch
     */
    public static native long getTime(long ssl);

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
     * <pre>
     * SSL_CVERIFY_NONE           - No client Certificate is required at all
     * SSL_CVERIFY_OPTIONAL       - The client may present a valid Certificate
     * SSL_CVERIFY_REQUIRE        - The client has to present a valid Certificate
     * SSL_CVERIFY_OPTIONAL_NO_CA - The client may present a valid Certificate
     *                              but it need not to be (successfully) verifiable
     * </pre>
     * <br>
     * The depth actually is the maximum number of intermediate certificate issuers,
     * i.e. the number of CA certificates which are max allowed to be followed while
     * verifying the client certificate. A depth of 0 means that self-signed client
     * certificates are accepted only, the default depth of 1 means the client
     * certificate can be self-signed or has to be signed by a CA which is directly
     * known to the server (i.e. the CA's certificate is under
     * {@code setCACertificatePath}, etc.
     *
     * @param ssl the SSL instance (SSL *)
     * @param level Type of Client Certificate verification.
     * @param depth Maximum depth of CA Certificates in Client Certificate
     *              verification.
     */
    public static native void setVerify(long ssl, int level, int depth);

    /**
     * Set OpenSSL Option.
     * @param ssl the SSL instance (SSL *)
     * @param options  See SSL.SSL_OP_* for option flags.
     */
    public static native void setOptions(long ssl, int options);

    /**
     * Get OpenSSL Option.
     * @param ssl the SSL instance (SSL *)
     * @return options  See SSL.SSL_OP_* for option flags.
     */
    public static native int getOptions(long ssl);

    /**
     * Returns all cipher suites that are enabled for negotiation in an SSL handshake.
     * @param ssl the SSL instance (SSL *)
     * @return ciphers
     */
    public static native String[] getCiphers(long ssl);

    /**
     * Returns the cipher suites available for negotiation in SSL handshake.
     * <br>
     * This complex directive uses a colon-separated cipher-spec string consisting
     * of OpenSSL cipher specifications to configure the Cipher Suite the client
     * is permitted to negotiate in the SSL handshake phase. Notice that this
     * directive can be used both in per-server and per-directory context.
     * In per-server context it applies to the standard SSL handshake when a
     * connection is established. In per-directory context it forces an SSL
     * renegotiation with the reconfigured Cipher Suite after the HTTP request
     * was read but before the HTTP response is sent.
     * @param ssl the SSL instance (SSL *)
     * @param ciphers an SSL cipher specification
     * @return <code>true</code> if the operation was successful
     * @throws Exception An error occurred
     */
    public static native boolean setCipherSuites(long ssl, String ciphers)
            throws Exception;

    /**
     * Returns the ID of the session as byte array representation.
     *
     * @param ssl the SSL instance (SSL *)
     * @return the session as byte array representation obtained via SSL_SESSION_get_id.
     */
    public static native byte[] getSessionId(long ssl);
}
