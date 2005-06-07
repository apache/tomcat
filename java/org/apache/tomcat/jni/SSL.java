/*
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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
 * @version $Revision$, $Date$
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
    public static final int SSL_AIDX_MAX     = 2;
    /*
     * Define IDs for the temporary RSA keys and DH params
     */

    public static final int SSL_TMP_KEY_RSA_512  = 0;
    public static final int SSL_TMP_KEY_RSA_1024 = 1;
    public static final int SSL_TMP_KEY_DH_512   = 2;
    public static final int SSL_TMP_KEY_DH_1024  = 3;
    public static final int SSL_TMP_KEY_MAX      = 4;

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
    public static final int SSL_PROTOCOL_ALL   = (SSL_PROTOCOL_SSLV2|SSL_PROTOCOL_SSLV3|SSL_PROTOCOL_TLSV1);

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
    /* If set, always create a new key when using tmp_dh parameters */
    public static final int SSL_OP_SINGLE_DH_USE                    = 0x00100000;
    /* Set to always use the tmp_rsa key when doing RSA operations,
     * even when this violates protocol specs */
    public static final int SSL_OP_EPHEMERAL_RSA                    = 0x00200000;
    /* Set on servers to choose the cipher according to the server's
     * preferences */
    public static final int SSL_OP_CIPHER_SERVER_PREFERENCE         = 0x00400000;
    /* If set, a server will allow a client to issue a SSLv3.0 version number
     * as latest version supported in the premaster secret, even when TLSv1.0
     * (version 3.1) was announced in the client hello. Normally this is
     * forbidden to prevent version rollback attacks. */
    public static final int SSL_OP_TLS_ROLLBACK_BUG                 = 0x00800000;

    public static final int SSL_OP_NO_SSLv2                         = 0x01000000;
    public static final int SSL_OP_NO_SSLv3                         = 0x02000000;
    public static final int SSL_OP_NO_TLSv1                         = 0x04000000;

    /* The next flag deliberately changes the ciphertest, this is a check
     * for the PKCS#1 attack */
    public static final int SSL_OP_PKCS1_CHECK_1                    = 0x08000000;
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


    /* Return OpenSSL version number */
    public static native int version();

    /* Return OpenSSL version string */
    public static native String versionString();

    /**
     * Initialize OpenSSL support.
     * This function needs to be called once for the
     * lifetime of JVM. Library.init() has to be called before.
     * @param engine Support for external a Crypto Device ("engine"),
     *                usually
     * a hardware accellerator card for crypto operations.
     * @return APR status code
     */
    public static native int initialize(String engine);

    /**
     * Add content of the file to the PRNG
     * @param filename Filename containing random data.
     *        If null the default file will be tested.
     *        The seed file is $RANDFILE if that environment variable is
     *        set, $HOME/.rnd otherwise.
     *        In case both files are unavailable builtin
     *        random seed generator is used.
     */
    public static native boolean randLoad(String filename);

    /**
     * Writes a number of random bytes (currently 1024) to
     * file <code>filename</code> which can be used to initialize the PRNG
     * by calling randLoad in a later session.
     * @param filename Filename to save the data
     */
    public static native boolean randSave(String filename);

    /**
     * Creates random data to filename
     * @param filename Filename to save the data
     * @param len The length of random sequence in bytes
     * @param base64 Output the data in Base64 encoded format
     */
    public static native boolean randMake(String filename, int len,
                                          boolean base64);

    /**
     * Initialize new BIO
     * @param pool The pool to use.
     * @param callback BIOCallback to use
     * @return New BIO handle
     */
     public static native long newBIO(long pool, BIOCallback callback)
            throws Exception;

    /**
     * Close BIO and derefrence callback object
     * @param bio BIO to close and destroy.
     * @return APR Status code
     */
     public static native int closeBIO(long bio);

    /**
     * Set global Password callback BIO for obtaining passwords.
     * @param bio BIO to use.
     */
     public static native void setPasswordBIO(long bio);

}
