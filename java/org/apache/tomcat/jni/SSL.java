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


}
