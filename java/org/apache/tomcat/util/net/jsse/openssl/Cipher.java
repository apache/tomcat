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

package org.apache.tomcat.util.net.jsse.openssl;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * All the standard cipher suites for SSL/TSL.
 *
 * @see <a href="https://github.com/openssl/openssl/blob/master/ssl/s3_lib.c"
 *      >OpenSSL cipher definitions</a>
 * @see <a href="http://www.iana.org/assignments/tls-parameters/tls-parameters.xhtml#tls-parameters-4"
 *      >The cipher suite registry</a>
 * @see <a href="https://www.thesprawl.org/research/tls-and-ssl-cipher-suites/"
 *      >Another list of cipher suites with some non-standard IDs</a>
 * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#ciphersuites"
 *      >Oracle standard names for cipher suites</a>
 * @see <a href="https://www.openssl.org/docs/apps/ciphers.html"
 *      >Mapping of OpenSSL cipher suites names to registry names</a>
 * @see <a href="https://github.com/ssllabs/sslhaf/blob/0.1.x/suites.csv"
 *      >SSL Labs tool - list of ciphers</a>
 * @see <a href="http://hg.openjdk.java.net/jdk9/jdk9/jdk/file/e30cd0d37abf/src/java.base/share/classes/sun/security/ssl/CipherSuite.java"
 *      >OpenJDK source code</a>
 */
public enum Cipher {

    /* Cipher 0
     * TLS_NULL_WITH_NULL_NULL
     * Must never be negotiated. Used internally to represent the initial
     * unprotected state of a connection.
     */

    /* The RSA ciphers */
    // Cipher 01
    TLS_RSA_WITH_NULL_MD5(
            0x0001,
            "NULL-MD5",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.eNULL,
            MessageDigest.MD5,
            Protocol.SSLv3,
            false,
            EncryptionLevel.STRONG_NONE,
            false,
            0,
            0,
            "SSL_RSA_WITH_NULL_MD5"
    ),
    // Cipher 02
    TLS_RSA_WITH_NULL_SHA(
            0x0002,
            "NULL-SHA",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.eNULL,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            false,
            EncryptionLevel.STRONG_NONE,
            true,
            0,
            0,
            "SSL_RSA_WITH_NULL_SHA"
    ),
    // Cipher 03
    TLS_RSA_EXPORT_WITH_RC4_40_MD5(
            0x0003,
            "EXP-RC4-MD5",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.RC4,
            MessageDigest.MD5,
            Protocol.SSLv3,
            true,
            EncryptionLevel.EXP40,
            false,
            40,
            128,
            "SSL_RSA_EXPORT_WITH_RC4_40_MD5"
    ),
    // Cipher 04
    TLS_RSA_WITH_RC4_128_MD5(
            0x0004,
            "RC4-MD5",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.RC4,
            MessageDigest.MD5,
            Protocol.SSLv3,
            false,
            EncryptionLevel.MEDIUM,
            false,
            128,
            128,
            "SSL_RSA_WITH_RC4_128_MD5"
    ),
    // Cipher 05
    TLS_RSA_WITH_RC4_128_SHA(
            0x0005,
            "RC4-SHA",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.RC4,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            false,
            EncryptionLevel.MEDIUM,
            false,
            128,
            128,
            "SSL_RSA_WITH_RC4_128_SHA"
    ),
    // Cipher 06
    TLS_RSA_EXPORT_WITH_RC2_CBC_40_MD5(
            0x0006,
            "EXP-RC2-CBC-MD5",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.RC2,
            MessageDigest.MD5,
            Protocol.SSLv3,
            true,
            EncryptionLevel.EXP40,
            false,
            40,
            128,
            "SSL_RSA_EXPORT_WITH_RC2_CBC_40_MD5"
    ),
    // Cipher 07
    TLS_RSA_WITH_IDEA_CBC_SHA(
            0x0007,
            "IDEA-CBC-SHA",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.IDEA,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            false,
            EncryptionLevel.MEDIUM,
            false,
            128,
            128,
            "SSL_RSA_WITH_IDEA_CBC_SHA"
    ),
    // Cipher 08
    TLS_RSA_EXPORT_WITH_DES40_CBC_SHA(
            0x0008,
            "EXP-DES-CBC-SHA",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.DES,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            true,
            EncryptionLevel.EXP40,
            false,
            40,
            56,
            "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA"
    ),
    // Cipher 09
    TLS_RSA_WITH_DES_CBC_SHA(
            0x0009,
            "DES-CBC-SHA",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.DES,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            false,
            EncryptionLevel.LOW,
            false,
            56,
            56,
            "SSL_RSA_WITH_DES_CBC_SHA"
    ),
    // Cipher 0A
    TLS_RSA_WITH_3DES_EDE_CBC_SHA(
            0x000A,
            "DES-CBC3-SHA",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.TRIPLE_DES,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            false,
            EncryptionLevel.HIGH,
            true,
            112,
            168,
            "SSL_RSA_WITH_3DES_EDE_CBC_SHA"
    ),
    /* The DH ciphers */
    // Cipher 0B
    TLS_DH_DSS_EXPORT_WITH_DES40_CBC_SHA(
            0x000B,
            "EXP-DH-DSS-DES-CBC-SHA",
            KeyExchange.DHd,
            Authentication.DH,
            Encryption.DES,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            true,
            EncryptionLevel.EXP40,
            false,
            40,
            56,
            "SSL_DH_DSS_EXPORT_WITH_DES40_CBC_SHA"
    ),
    // Cipher 0C
    TLS_DH_DSS_WITH_DES_CBC_SHA(
            0x000C,
            "DH-DSS-DES-CBC-SHA",
            KeyExchange.DHd,
            Authentication.DH,
            Encryption.DES,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            false,
            EncryptionLevel.LOW,
            false,
            56,
            56,
            "SSL_DH_DSS_WITH_DES_CBC_SHA"
    ),
    // Cipher 0D
    TLS_DH_DSS_WITH_3DES_EDE_CBC_SHA(
            0x000D,
            "DH-DSS-DES-CBC3-SHA",
            KeyExchange.DHd,
            Authentication.DH,
            Encryption.TRIPLE_DES,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            false,
            EncryptionLevel.HIGH,
            true,
            112,
            168,
            "SSL_DH_DSS_WITH_3DES_EDE_CBC_SHA"
    ),
    // Cipher 0E
    TLS_DH_RSA_EXPORT_WITH_DES40_CBC_SHA(
            0x000E,
            "EXP-DH-RSA-DES-CBC-SHA",
            KeyExchange.DHr,
            Authentication.DH,
            Encryption.DES,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            true,
            EncryptionLevel.EXP40,
            false,
            40,
            56,
            "SSL_DH_RSA_EXPORT_WITH_DES40_CBC_SHA"
    ),
    // Cipher 0F
    TLS_DH_RSA_WITH_DES_CBC_SHA(
            0x000F,
            "DH-RSA-DES-CBC-SHA",
            KeyExchange.DHr,
            Authentication.DH,
            Encryption.DES,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            false,
            EncryptionLevel.LOW,
            false,
            56,
            56,
            "SSL_DH_RSA_WITH_DES_CBC_SHA"
    ),
    // Cipher 10
    TLS_DH_RSA_WITH_3DES_EDE_CBC_SHA(
            0x0010,
            "DH-RSA-DES-CBC3-SHA",
            KeyExchange.DHr,
            Authentication.DH,
            Encryption.TRIPLE_DES,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            false,
            EncryptionLevel.HIGH,
            true,
            112,
            168,
            "SSL_DH_RSA_WITH_3DES_EDE_CBC_SHA"
    ),
    /* The Ephemeral DH ciphers */
    // Cipher 11
    TLS_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA(
            0x0011,
            "EXP-DHE-DSS-DES-CBC-SHA",
            KeyExchange.EDH,
            Authentication.DSS,
            Encryption.DES,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            true,
            EncryptionLevel.EXP40,
            false,
            40,
            56,
            "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA"
    ),
    // Cipher 12
    TLS_DHE_DSS_WITH_DES_CBC_SHA(
            0x0012,
            "DHE-DSS-DES-CBC-SHA",
            KeyExchange.EDH,
            Authentication.DSS,
            Encryption.DES,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            false,
            EncryptionLevel.LOW,
            false,
            56,
            56,
            "SSL_DHE_DSS_WITH_DES_CBC_SHA"
    ),
    // Cipher 13
    TLS_DHE_DSS_WITH_3DES_EDE_CBC_SHA(
            0x0013,
            "DHE-DSS-DES-CBC3-SHA",
            KeyExchange.EDH,
            Authentication.DSS,
            Encryption.TRIPLE_DES,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            false,
            EncryptionLevel.HIGH,
            true,
            112,
            168,
            "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA"
    ),
    // Cipher 14
    TLS_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA(
            0x0014,
            "EXP-DHE-RSA-DES-CBC-SHA",
            KeyExchange.EDH,
            Authentication.RSA,
            Encryption.DES,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            true,
            EncryptionLevel.EXP40,
            false,
            40,
            56,
            "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA"
    ),
    // Cipher 15
    TLS_DHE_RSA_WITH_DES_CBC_SHA(
            0x0015,
            "DHE-RSA-DES-CBC-SHA",
            KeyExchange.EDH,
            Authentication.RSA,
            Encryption.DES,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            false,
            EncryptionLevel.LOW,
            false,
            56,
            56,
            "SSL_DHE_RSA_WITH_DES_CBC_SHA"
    ),
    // Cipher 16
    TLS_DHE_RSA_WITH_3DES_EDE_CBC_SHA(
            0x0016,
            "DHE-RSA-DES-CBC3-SHA",
            KeyExchange.EDH,
            Authentication.RSA,
            Encryption.TRIPLE_DES,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            false,
            EncryptionLevel.HIGH,
            true,
            112,
            168,
            "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA"
    ),
    // Cipher 17
    TLS_DH_anon_EXPORT_WITH_RC4_40_MD5(
            0x0017,
            "EXP-ADH-RC4-MD5",
            KeyExchange.EDH,
            Authentication.aNULL,
            Encryption.RC4,
            MessageDigest.MD5,
            Protocol.SSLv3,
            true,
            EncryptionLevel.EXP40,
            false,
            40,
            128,
            "SSL_DH_anon_EXPORT_WITH_RC4_40_MD5"
    ),
    // Cipher 18
    TLS_DH_anon_WITH_RC4_128_MD5(
            0x0018,
            "ADH-RC4-MD5",
            KeyExchange.EDH,
            Authentication.aNULL,
            Encryption.RC4,
            MessageDigest.MD5,
            Protocol.SSLv3,
            false,
            EncryptionLevel.MEDIUM,
            false,
            128,
            128,
            "SSL_DH_anon_WITH_RC4_128_MD5"
    ),
    // Cipher 19
    TLS_DH_anon_EXPORT_WITH_DES40_CBC_SHA(
            0x0019,
            "EXP-ADH-DES-CBC-SHA",
            KeyExchange.EDH,
            Authentication.aNULL,
            Encryption.DES,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            true,
            EncryptionLevel.EXP40,
            false,
            40,
            128,
            "SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA"
    ),
    // Cipher 1A
    TLS_DH_anon_WITH_DES_CBC_SHA(
            0x001A,
            "ADH-DES-CBC-SHA",
            KeyExchange.EDH,
            Authentication.aNULL,
            Encryption.DES,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            false,
            EncryptionLevel.LOW,
            false,
            56,
            56,
            "SSL_DH_anon_WITH_DES_CBC_SHA"
    ),
    // Cipher 1B
    TLS_DH_anon_WITH_3DES_EDE_CBC_SHA(
            0x001B,
            "ADH-DES-CBC3-SHA",
            KeyExchange.EDH,
            Authentication.aNULL,
            Encryption.TRIPLE_DES,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            false,
            EncryptionLevel.HIGH,
            true,
            112,
            168,
            "SSL_DH_anon_WITH_3DES_EDE_CBC_SHA"
    ),
    /* Fortezza ciphersuite from SSL 3.0 spec
     * Neither OpenSSL nor Java implement these ciphers and the IDs used
     * overlap partially with the IDs used by the Kerberos ciphers
    // Cipher 1C
    SSL_FORTEZZA_DMS_WITH_NULL_SHA(
            "FZA-NULL-SHA",
            KeyExchange.FZA,
            Authentication.FZA,
            Encryption.eNULL,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            false,
            EncryptionLevel.STRONG_NONE,
            false,
            0,
            0
    ),
    // Cipher 1D
    SSL_FORTEZZA_DMS_WITH_FORTEZZA_CBC_SHA(
            "FZA-FZA-CBC-SHA",
            KeyExchange.FZA,
            Authentication.FZA,
            Encryption.FZA,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            false,
            EncryptionLevel.STRONG_NONE,
            false,
            0,
            0
    ),
    // Cipher 1E - overlaps with Kerberos below
    SSL_FORTEZZA_DMS_WITH_RC4_128_SHA(
            "FZA-RC4-SHA",
            KeyExchange.FZA,
            Authentication.FZA,
            Encryption.RC4,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            false,
            EncryptionLevel.MEDIUM,
            false,
            128,
            128
    ),
     */
    /* The Kerberos ciphers. OpenSSL doesn't support these. Java does but they
     * are used for Kerberos authentication.
     */
    // Cipher 1E - overlaps with Fortezza above
    /*TLS_KRB5_WITH_DES_CBC_SHA(
            "KRB5-DES-CBC-SHA",
            KeyExchange.KRB5,
            Authentication.KRB5,
            Encryption.DES,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            false,
            EncryptionLevel.LOW,
            false,
            56,
            56
    ),
    // Cipher 1F
    TLS_KRB5_WITH_3DES_EDE_CBC_SHA(
            "KRB5-DES-CBC3-SHA",
            KeyExchange.KRB5,
            Authentication.KRB5,
            Encryption.TRIPLE_DES,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            false,
            EncryptionLevel.HIGH,
            true,
            112,
            168
    ),
    // Cipher 20
    TLS_KRB5_WITH_RC4_128_SHA(
            "KRB5-RC4-SHA",
            KeyExchange.KRB5,
            Authentication.KRB5,
            Encryption.RC4,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            false,
            EncryptionLevel.MEDIUM,
            false,
            128,
            128
    ),
    // Cipher 21
    TLS_KRB5_WITH_IDEA_CBC_SHA(
            "KRB5-IDEA-CBC-SHA",
            KeyExchange.KRB5,
            Authentication.KRB5,
            Encryption.IDEA,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            false,
            EncryptionLevel.MEDIUM,
            false,
            128,
            128
    ),
    // Cipher 22
    TLS_KRB5_WITH_DES_CBC_MD5(
            "KRB5-DES-CBC-MD5",
            KeyExchange.KRB5,
            Authentication.KRB5,
            Encryption.DES,
            MessageDigest.MD5,
            Protocol.SSLv3,
            false,
            EncryptionLevel.LOW,
            false,
            56,
            56
    ),
    // Cipher 23
    TLS_KRB5_WITH_3DES_EDE_CBC_MD5(
            "KRB5-DES-CBC3-MD5",
            KeyExchange.KRB5,
            Authentication.KRB5,
            Encryption.TRIPLE_DES,
            MessageDigest.MD5,
            Protocol.SSLv3,
            false,
            EncryptionLevel.HIGH,
            false,
            112,
            168
    ),
    // Cipher 24
    TLS_KRB5_WITH_RC4_128_MD5(
            "KRB5-RC4-MD5",
            KeyExchange.KRB5,
            Authentication.KRB5,
            Encryption.RC4,
            MessageDigest.MD5,
            Protocol.SSLv3,
            false,
            EncryptionLevel.MEDIUM,
            false,
            128,
            128
    ),
    // Cipher 25
    TLS_KRB5_WITH_IDEA_CBC_MD5(
            "KRB5-IDEA-CBC-MD5",
            KeyExchange.KRB5,
            Authentication.KRB5,
            Encryption.IDEA,
            MessageDigest.MD5,
            Protocol.SSLv3,
            false,
            EncryptionLevel.MEDIUM,
            false,
            128,
            128
    ),
    // Cipher 26
    TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA(
            "EXP-KRB5-DES-CBC-SHA",
            KeyExchange.KRB5,
            Authentication.KRB5,
            Encryption.DES,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            true,
            EncryptionLevel.EXP40,
            false,
            40,
            56
    ),
    // Cipher 27
    TLS_KRB5_EXPORT_WITH_RC2_CBC_40_SHA(
            "EXP-KRB5-RC2-CBC-SHA",
            KeyExchange.KRB5,
            Authentication.KRB5,
            Encryption.RC2,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            true,
            EncryptionLevel.EXP40,
            false,
            40,
            128
    ),
    // Cipher 28
    TLS_KRB5_EXPORT_WITH_RC4_40_SHA(
            "EXP-KRB5-RC4-SHA",
            KeyExchange.KRB5,
            Authentication.KRB5,
            Encryption.RC4,
            MessageDigest.SHA1,
            Protocol.SSLv3,
            true,
            EncryptionLevel.EXP40,
            false,
            40,
            128
    ),
    // Cipher 29
    TLS_KRB5_EXPORT_WITH_DES_CBC_40_MD5(
            "EXP-KRB5-DES-CBC-MD5",
            KeyExchange.KRB5,
            Authentication.KRB5,
            Encryption.DES,
            MessageDigest.MD5,
            Protocol.SSLv3,
            true,
            EncryptionLevel.EXP40,
            false,
            40,
            56
    ),
    // Cipher 2A
    TLS_KRB5_EXPORT_WITH_RC2_CBC_40_MD5(
            "EXP-KRB5-RC2-CBC-MD5",
            KeyExchange.KRB5,
            Authentication.KRB5,
            Encryption.RC2,
            MessageDigest.MD5,
            Protocol.SSLv3,
            true,
            EncryptionLevel.EXP40,
            false,
            40,
            128
    ),
    // Cipher 2B
    TLS_KRB5_EXPORT_WITH_RC4_40_MD5(
            "EXP-KRB5-RC4-MD5",
            KeyExchange.KRB5,
            Authentication.KRB5,
            Encryption.RC4,
            MessageDigest.MD5,
            Protocol.SSLv3,
            true,
            EncryptionLevel.EXP40,
            false,
            40,
            128
    ),*/

    /* PSK cipher suites from RFC 4785
     * Unsupported by both Java and OpenSSL.
    // Cipher 2C
    TLS_PSK_WITH_NULL_SHA

    // Cipher 2D
    TLS_DHE_PSK_WITH_NULL_SHA

    // Cipher 2E
    TLS_RSA_PSK_WITH_NULL_SHA
    */

    /* New AES ciphersuites */
    // Cipher 2F
    TLS_RSA_WITH_AES_128_CBC_SHA(
            0x002f,
            "AES128-SHA",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.AES128,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher 30
    TLS_DH_DSS_WITH_AES_128_CBC_SHA(
            0x0030,
            "DH-DSS-AES128-SHA",
            KeyExchange.DHd,
            Authentication.DH,
            Encryption.AES128,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher 31
    TLS_DH_RSA_WITH_AES_128_CBC_SHA(
            0x0031,
            "DH-RSA-AES128-SHA",
            KeyExchange.DHr,
            Authentication.DH,
            Encryption.AES128,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher 32
    TLS_DHE_DSS_WITH_AES_128_CBC_SHA(
            0x0032,
            "DHE-DSS-AES128-SHA",
            KeyExchange.EDH,
            Authentication.DSS,
            Encryption.AES128,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher 33
    TLS_DHE_RSA_WITH_AES_128_CBC_SHA(
            0x0033,
            "DHE-RSA-AES128-SHA",
            KeyExchange.EDH,
            Authentication.RSA,
            Encryption.AES128,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher 34
    TLS_DH_anon_WITH_AES_128_CBC_SHA(
            0x0034,
            "ADH-AES128-SHA",
            KeyExchange.EDH,
            Authentication.aNULL,
            Encryption.AES128,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher 35
    TLS_RSA_WITH_AES_256_CBC_SHA(
            0x0035,
            "AES256-SHA",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.AES256,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher 36
    TLS_DH_DSS_WITH_AES_256_CBC_SHA(
            0x0036,
            "DH-DSS-AES256-SHA",
            KeyExchange.DHd,
            Authentication.DH,
            Encryption.AES256,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher 37
    TLS_DH_RSA_WITH_AES_256_CBC_SHA(
            0x0037,
            "DH-RSA-AES256-SHA",
            KeyExchange.DHr,
            Authentication.DH,
            Encryption.AES256,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher 38
    TLS_DHE_DSS_WITH_AES_256_CBC_SHA(
            0x0038,
            "DHE-DSS-AES256-SHA",
            KeyExchange.EDH,
            Authentication.DSS,
            Encryption.AES256,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher 39
    TLS_DHE_RSA_WITH_AES_256_CBC_SHA(
            0x0039,
            "DHE-RSA-AES256-SHA",
            KeyExchange.EDH,
            Authentication.RSA,
            Encryption.AES256,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher 3A
    TLS_DH_anon_WITH_AES_256_CBC_SHA(
            0x003A,
            "ADH-AES256-SHA",
            KeyExchange.EDH,
            Authentication.aNULL,
            Encryption.AES256,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    /* TLS v1.2 ciphersuites */
    // Cipher 3B
    TLS_RSA_WITH_NULL_SHA256(
            0x003B,
            "NULL-SHA256",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.eNULL,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.STRONG_NONE,
            true,
            0,
            0
    ),
    // Cipher 3C
    TLS_RSA_WITH_AES_128_CBC_SHA256(
            0x003C,
            "AES128-SHA256",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.AES128,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher 3D
    TLS_RSA_WITH_AES_256_CBC_SHA256(
            0x003D,
            "AES256-SHA256",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.AES256,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher 3E
    TLS_DH_DSS_WITH_AES_128_CBC_SHA256(
            0x003E,
            "DH-DSS-AES128-SHA256",
            KeyExchange.DHd,
            Authentication.DH,
            Encryption.AES128,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher 3F
    TLS_DH_RSA_WITH_AES_128_CBC_SHA256(
            0x003F,
            "DH-RSA-AES128-SHA256",
            KeyExchange.DHr,
            Authentication.DH,
            Encryption.AES128,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher 40
    TLS_DHE_DSS_WITH_AES_128_CBC_SHA256(
            0x0040,
            "DHE-DSS-AES128-SHA256",
            KeyExchange.EDH,
            Authentication.DSS,
            Encryption.AES128,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    /* Camellia ciphersuites from RFC4132 (
            128-bit portion) */
    // Cipher 41
    TLS_RSA_WITH_CAMELLIA_128_CBC_SHA(
            0x0041,
            "CAMELLIA128-SHA",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.CAMELLIA128,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            false,
            128,
            128
    ),
    // Cipher 42
    TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA(
            0x0042,
            "DH-DSS-CAMELLIA128-SHA",
            KeyExchange.DHd,
            Authentication.DH,
            Encryption.CAMELLIA128,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            false,
            128,
            128
    ),
    // Cipher 43
    TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA(
            0x0043,
            "DH-RSA-CAMELLIA128-SHA",
            KeyExchange.DHr,
            Authentication.DH,
            Encryption.CAMELLIA128,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            false,
            128,
            128
    ),
    // Cipher 44
    TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA(
            0x0044,
            "DHE-DSS-CAMELLIA128-SHA",
            KeyExchange.EDH,
            Authentication.DSS,
            Encryption.CAMELLIA128,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            false,
            128,
            128
    ),
    // Cipher 45
    TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA(
            0x0045,
            "DHE-RSA-CAMELLIA128-SHA",
            KeyExchange.EDH,
            Authentication.RSA,
            Encryption.CAMELLIA128,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            false,
            128,
            128
    ),
    // Cipher 46
    TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA(
            0x0046,
            "ADH-CAMELLIA128-SHA",
            KeyExchange.EDH,
            Authentication.aNULL,
            Encryption.CAMELLIA128,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            false,
            128,
            128
    ),
    /* Experimental (and now expired) TLSv1 versions of SSLv3 ciphers.
     * Unsupported by Java and OpenSSL.
    // Cipher 60
    TLS_RSA_EXPORT1024_WITH_RC4_56_MD5(
            "EXP1024-RC4-MD5",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.RC4,
            MessageDigest.MD5,
            Protocol.TLSv1,
            true,
            EncryptionLevel.EXP56,
            false,
            56,
            128,
            "SSL_RSA_EXPORT1024_WITH_RC4_56_MD5"
    ),
    // Cipher 61
    TLS_RSA_EXPORT1024_WITH_RC2_CBC_56_MD5(
            "EXP1024-RC2-CBC-MD5",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.RC2,
            MessageDigest.MD5,
            Protocol.TLSv1,
            true,
            EncryptionLevel.EXP56,
            false,
            56,
            128,
            "SSL_RSA_EXPORT1024_WITH_RC2_CBC_56_MD"
    ),
    // Cipher 62
    TLS_RSA_EXPORT1024_WITH_DES_CBC_SHA(
            "EXP1024-DES-CBC-SHA",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.DES,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            true,
            EncryptionLevel.EXP56,
            false,
            56,
            56,
            "SSL_RSA_EXPORT1024_WITH_DES_CBC_SHA"
    ),
    // Cipher 63
    TLS_DHE_DSS_EXPORT1024_WITH_DES_CBC_SHA(
            "EXP1024-DHE-DSS-DES-CBC-SHA",
            KeyExchange.EDH,
            Authentication.DSS,
            Encryption.DES,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            true,
            EncryptionLevel.EXP56,
            false,
            56,
            56,
            "SSL_DHE_DSS_EXPORT1024_WITH_DES_CBC_SHA"
    ),
    // Cipher 64
    TLS_RSA_EXPORT1024_WITH_RC4_56_SHA(
            "EXP1024-RC4-SHA",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.RC4,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            true,
            EncryptionLevel.EXP56,
            false,
            56,
            128,
            "SSL_RSA_EXPORT1024_WITH_RC4_56_SHA"
    ),
    // Cipher 65
    TLS_DHE_DSS_EXPORT1024_WITH_RC4_56_SHA(
            "EXP1024-DHE-DSS-RC4-SHA",
            KeyExchange.EDH,
            Authentication.DSS,
            Encryption.RC4,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            true,
            EncryptionLevel.EXP56,
            false,
            56,
            128,
            "SSL_DHE_DSS_EXPORT1024_WITH_RC4_56_SHA"
    ),
    // Cipher 66
    TLS_DHE_DSS_WITH_RC4_128_SHA(
            "DHE-DSS-RC4-SHA",
            KeyExchange.EDH,
            Authentication.DSS,
            Encryption.RC4,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.MEDIUM,
            false,
            128,
            128,
            "SSL_DHE_DSS_WITH_RC4_128_SHA"
    ),*/
    /* TLS v1.2 ciphersuites */
    // Cipher 67
    TLS_DHE_RSA_WITH_AES_128_CBC_SHA256(
            0x0067,
            "DHE-RSA-AES128-SHA256",
            KeyExchange.EDH,
            Authentication.RSA,
            Encryption.AES128,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher 68
    TLS_DH_DSS_WITH_AES_256_CBC_SHA256(
            0x0068,
            "DH-DSS-AES256-SHA256",
            KeyExchange.DHd,
            Authentication.DH,
            Encryption.AES256,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher 69
    TLS_DH_RSA_WITH_AES_256_CBC_SHA256(
            0x0069,
            "DH-RSA-AES256-SHA256",
            KeyExchange.DHr,
            Authentication.DH,
            Encryption.AES256,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher 6A
    TLS_DHE_DSS_WITH_AES_256_CBC_SHA256(
            0x006A,
            "DHE-DSS-AES256-SHA256",
            KeyExchange.EDH,
            Authentication.DSS,
            Encryption.AES256,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher 6B
    TLS_DHE_RSA_WITH_AES_256_CBC_SHA256(
            0x006B,
            "DHE-RSA-AES256-SHA256",
            KeyExchange.EDH,
            Authentication.RSA,
            Encryption.AES256,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher 6C
    TLS_DH_anon_WITH_AES_128_CBC_SHA256(
            0x006C,
            "ADH-AES128-SHA256",
            KeyExchange.EDH,
            Authentication.aNULL,
            Encryption.AES128,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher 6D
    TLS_DH_anon_WITH_AES_256_CBC_SHA256(
            0x006D,
            "ADH-AES256-SHA256",
            KeyExchange.EDH,
            Authentication.aNULL,
            Encryption.AES256,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    /* GOST Ciphersuites. Unsupported by Java. OpenSSl lists them with IDs
     * 0x3000080 to 0x3000083 */
    /*
    // Cipher 80
    TLS_GOSTR341094_WITH_28147_CNT_IMIT(
            "GOST94-GOST89-GOST89",
            KeyExchange.GOST,
            Authentication.GOST94,
            Encryption.eGOST2814789CNT,
            MessageDigest.GOST89MAC,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            false,
            256,
            256
    ),
    // Cipher 81
    TLS_GOSTR341001_WITH_28147_CNT_IMIT(
            "GOST2001-GOST89-GOST89",
            KeyExchange.GOST,
            Authentication.GOST01,
            Encryption.eGOST2814789CNT,
            MessageDigest.GOST89MAC,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            false,
            256,
            256
    ),
    // Cipher 82
    TLS_GOSTR341094_WITH_NULL_GOSTR3411(
            "GOST94-NULL-GOST94",
            KeyExchange.GOST,
            Authentication.GOST94,
            Encryption.eNULL,
            MessageDigest.GOST94,
            Protocol.TLSv1,
            false,
            EncryptionLevel.STRONG_NONE,
            false,
            0,
            0
    ),
    // Cipher 83
    TLS_GOSTR341001_WITH_NULL_GOSTR3411(
            "GOST2001-NULL-GOST94",
            KeyExchange.GOST,
            Authentication.GOST01,
            Encryption.eNULL,
            MessageDigest.GOST94,
            Protocol.TLSv1,
            false,
            EncryptionLevel.STRONG_NONE,
            false,
            0,
            0
    ),*/
    /* Camellia ciphersuites from RFC4132 (
            256-bit portion) */
    // Cipher 84
    TLS_RSA_WITH_CAMELLIA_256_CBC_SHA(
            0x0084,
            "CAMELLIA256-SHA",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.CAMELLIA256,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            false,
            256,
            256
    ),
    // Cipher 85
    TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA(
            0x0085,
            "DH-DSS-CAMELLIA256-SHA",
            KeyExchange.DHd,
            Authentication.DH,
            Encryption.CAMELLIA256,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            false,
            256,
            256
    ),
    // Cipher 86
    TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA(
            0x0086,
            "DH-RSA-CAMELLIA256-SHA",
            KeyExchange.DHr,
            Authentication.DH,
            Encryption.CAMELLIA256,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            false,
            256,
            256
    ),
    // Cipher 87
    TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA(
            0x0087,
            "DHE-DSS-CAMELLIA256-SHA",
            KeyExchange.EDH,
            Authentication.DSS,
            Encryption.CAMELLIA256,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            false,
            256,
            256
    ),
    // Cipher 88
    TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA(
            0x0088,
            "DHE-RSA-CAMELLIA256-SHA",
            KeyExchange.EDH,
            Authentication.RSA,
            Encryption.CAMELLIA256,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            false,
            256,
            256
    ),
    // Cipher 89
    TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA(
            0x0089,
            "ADH-CAMELLIA256-SHA",
            KeyExchange.EDH,
            Authentication.aNULL,
            Encryption.CAMELLIA256,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            false,
            256,
            256
    ),
    // Cipher 8A
    TLS_PSK_WITH_RC4_128_SHA(
            0x008A,
            "PSK-RC4-SHA",
            KeyExchange.PSK,
            Authentication.PSK,
            Encryption.RC4,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.MEDIUM,
            false,
            128,
            128
    ),
    // Cipher 8B
    TLS_PSK_WITH_3DES_EDE_CBC_SHA(
            0x008B,
            "PSK-3DES-EDE-CBC-SHA",
            KeyExchange.PSK,
            Authentication.PSK,
            Encryption.TRIPLE_DES,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            112,
            168
    ),
    // Cipher 8C
    TLS_PSK_WITH_AES_128_CBC_SHA(
            0x008C,
            "PSK-AES128-CBC-SHA",
            KeyExchange.PSK,
            Authentication.PSK,
            Encryption.AES128,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher 8D
    TLS_PSK_WITH_AES_256_CBC_SHA(
            0x008D,
            "PSK-AES256-CBC-SHA",
            KeyExchange.PSK,
            Authentication.PSK,
            Encryption.AES256,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),

    /* PSK ciphers 0x008E to 0x0095
     * Unsupported by both Java and OpenSSL
     */

    /* SEED ciphersuites from RFC4162 */
    // Cipher 96
    TLS_RSA_WITH_SEED_CBC_SHA(
            0x0096,
            "SEED-SHA",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.SEED,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.MEDIUM,
            false,
            128,
            128
    ),
    // Cipher 97
    TLS_DH_DSS_WITH_SEED_CBC_SHA(
            0x0097,
            "DH-DSS-SEED-SHA",
            KeyExchange.DHd,
            Authentication.DH,
            Encryption.SEED,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.MEDIUM,
            false,
            128,
            128
    ),
    // Cipher 98
    TLS_DH_RSA_WITH_SEED_CBC_SHA(
            0x0098,
            "DH-RSA-SEED-SHA",
            KeyExchange.DHr,
            Authentication.DH,
            Encryption.SEED,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.MEDIUM,
            false,
            128,
            128
    ),
    // Cipher 99
    TLS_DHE_DSS_WITH_SEED_CBC_SHA(
            0x0099,
            "DHE-DSS-SEED-SHA",
            KeyExchange.EDH,
            Authentication.DSS,
            Encryption.SEED,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.MEDIUM,
            false,
            128,
            128
    ),
    // Cipher 9A
    TLS_DHE_RSA_WITH_SEED_CBC_SHA(
            0x009A,
            "DHE-RSA-SEED-SHA",
            KeyExchange.EDH,
            Authentication.RSA,
            Encryption.SEED,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.MEDIUM,
            false,
            128,
            128
    ),
    // Cipher 9B
    TLS_DH_anon_WITH_SEED_CBC_SHA(
            0x009B,
            "ADH-SEED-SHA",
            KeyExchange.EDH,
            Authentication.aNULL,
            Encryption.SEED,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.MEDIUM,
            false,
            128,
            128
    ),
    /* GCM ciphersuites from RFC5288 */
    // Cipher 9C
    TLS_RSA_WITH_AES_128_GCM_SHA256(
            0x009C,
            "AES128-GCM-SHA256",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.AES128GCM,
            MessageDigest.AEAD,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher 9D
    TLS_RSA_WITH_AES_256_GCM_SHA384(
            0x009D,
            "AES256-GCM-SHA384",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.AES256GCM,
            MessageDigest.AEAD,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher 9E
    TLS_DHE_RSA_WITH_AES_128_GCM_SHA256(
            0x009E,
            "DHE-RSA-AES128-GCM-SHA256",
            KeyExchange.EDH,
            Authentication.RSA,
            Encryption.AES128GCM,
            MessageDigest.AEAD,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher 9F
    TLS_DHE_RSA_WITH_AES_256_GCM_SHA384(
            0x009F,
            "DHE-RSA-AES256-GCM-SHA384",
            KeyExchange.EDH,
            Authentication.RSA,
            Encryption.AES256GCM,
            MessageDigest.AEAD,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher A0
    TLS_DH_RSA_WITH_AES_128_GCM_SHA256(
            0x00A0,
            "DH-RSA-AES128-GCM-SHA256",
            KeyExchange.DHr,
            Authentication.DH,
            Encryption.AES128GCM,
            MessageDigest.AEAD,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher A1
    TLS_DH_RSA_WITH_AES_256_GCM_SHA384(
            0x00A1,
            "DH-RSA-AES256-GCM-SHA384",
            KeyExchange.DHr,
            Authentication.DH,
            Encryption.AES256GCM,
            MessageDigest.AEAD,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher A2
    TLS_DHE_DSS_WITH_AES_128_GCM_SHA256(
            0x00A2,
            "DHE-DSS-AES128-GCM-SHA256",
            KeyExchange.EDH,
            Authentication.DSS,
            Encryption.AES128GCM,
            MessageDigest.AEAD,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher A3
    TLS_DHE_DSS_WITH_AES_256_GCM_SHA384(
            0x00A3,
            "DHE-DSS-AES256-GCM-SHA384",
            KeyExchange.EDH,
            Authentication.DSS,
            Encryption.AES256GCM,
            MessageDigest.AEAD,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher A4
    TLS_DH_DSS_WITH_AES_128_GCM_SHA256(
            0x00A4,
            "DH-DSS-AES128-GCM-SHA256",
            KeyExchange.DHd,
            Authentication.DH,
            Encryption.AES128GCM,
            MessageDigest.AEAD,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher A5
    TLS_DH_DSS_WITH_AES_256_GCM_SHA384(
            0x00A5,
            "DH-DSS-AES256-GCM-SHA384",
            KeyExchange.DHd,
            Authentication.DH,
            Encryption.AES256GCM,
            MessageDigest.AEAD,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher A6
    TLS_DH_anon_WITH_AES_128_GCM_SHA256(
            0x00A6,
            "ADH-AES128-GCM-SHA256",
            KeyExchange.EDH,
            Authentication.aNULL,
            Encryption.AES128GCM,
            MessageDigest.AEAD,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher A7
    TLS_DH_anon_WITH_AES_256_GCM_SHA384(
            0x00A7,
            "ADH-AES256-GCM-SHA384",
            KeyExchange.EDH,
            Authentication.aNULL,
            Encryption.AES256GCM,
            MessageDigest.AEAD,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher A8
    TLS_PSK_WITH_AES_128_GCM_SHA256(
            0x00A8,
            "PSK-AES128-GCM-SHA256",
            KeyExchange.PSK,
            Authentication.PSK,
            Encryption.AES128GCM,
            MessageDigest.AEAD,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher A9
    TLS_PSK_WITH_AES_256_GCM_SHA384(
            0x00A9,
            "PSK-AES128-GCM-SHA384",
            KeyExchange.PSK,
            Authentication.PSK,
            Encryption.AES256GCM,
            MessageDigest.AEAD,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),

    /* PSK ciphers 0x00AA to 0x00B9
     * Unsupported by both Java and OpenSSL
     */

    // Cipher BA
    TLS_RSA_WITH_CAMELLIA_128_CBC_SHA256(
            0x00BA,
            "CAMELLIA128-SHA256",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.CAMELLIA128,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            false,
            128,
            128
    ),
    // Cipher BB
    TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA256(
            0x00BB,
            "DH-DSS-CAMELLIA128-SHA256",
            KeyExchange.DHd,
            Authentication.DH,
            Encryption.CAMELLIA128,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            false,
            128,
            128
    ),
    // Cipher BC
    TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA256(
            0x00BC,
            "DH-RSA-CAMELLIA128-SHA256",
            KeyExchange.DHr,
            Authentication.DH,
            Encryption.CAMELLIA128,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            false,
            128,
            128
    ),
    // Cipher BD
    TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA256(
            0x00BD,
            "DHE-DSS-CAMELLIA128-SHA256",
            KeyExchange.EDH,
            Authentication.DSS,
            Encryption.CAMELLIA128,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            false,
            128,
            128
    ),
    // Cipher BE
    TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA256(
            0x00BE,
            "DHE-RSA-CAMELLIA128-SHA256",
            KeyExchange.EDH,
            Authentication.RSA,
            Encryption.CAMELLIA128,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            false,
            128,
            128
    ),
    // Cipher BF
    TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA256(
            0x00BF,
            "ADH-CAMELLIA128-SHA256",
            KeyExchange.EDH,
            Authentication.aNULL,
            Encryption.CAMELLIA128,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            false,
            128,
            128
    ),
    // Cipher C0
    TLS_RSA_WITH_CAMELLIA_256_CBC_SHA256(
            0x00C0,
            "CAMELLIA256-SHA256",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.CAMELLIA256,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            false,
            256,
            256
    ),
    // Cipher C1
    TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA256(
            0x00C1,
            "DH-DSS-CAMELLIA256-SHA256",
            KeyExchange.DHd,
            Authentication.DH,
            Encryption.CAMELLIA256,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            false,
            256,
            256
    ),
    // Cipher C2
    TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA256(
            0x00C2,
            "DH-RSA-CAMELLIA256-SHA256",
            KeyExchange.DHr,
            Authentication.DH,
            Encryption.CAMELLIA256,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            false,
            256,
            256
    ),
    // Cipher C3
    TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA256(
            0x00C3,
            "DHE-DSS-CAMELLIA256-SHA256",
            KeyExchange.EDH,
            Authentication.DSS,
            Encryption.CAMELLIA256,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            false,
            256,
            256
    ),
    // Cipher C4
    TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA256(
            0x00C4,
            "DHE-RSA-CAMELLIA256-SHA256",
            KeyExchange.EDH,
            Authentication.RSA,
            Encryption.CAMELLIA256,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            false,
            256,
            256
    ),
    // Cipher C5
    TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA256(
            0x00C5,
            "ADH-CAMELLIA256-SHA256",
            KeyExchange.EDH,
            Authentication.aNULL,
            Encryption.CAMELLIA256,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            false,
            256,
            256
    ),

    /* Cipher 0x00FF  TLS_EMPTY_RENEGOTIATION_INFO_SCSV
     * Cipher 0x5600  TLS_FALLBACK_SCSV
     *
     * No other ciphers defined until 0xC001 below
     */

    /* ECC ciphersuites from draft-ietf-tls-ecc-01.txt (
            Mar 15, 2001) */
    // Cipher C001
    TLS_ECDH_ECDSA_WITH_NULL_SHA(
            0xC001,
            "ECDH-ECDSA-NULL-SHA",
            KeyExchange.ECDHe,
            Authentication.ECDH,
            Encryption.eNULL,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.STRONG_NONE,
            true,
            0,
            0
    ),
    // Cipher C002
    TLS_ECDH_ECDSA_WITH_RC4_128_SHA(
            0xC002,
            "ECDH-ECDSA-RC4-SHA",
            KeyExchange.ECDHe,
            Authentication.ECDH,
            Encryption.RC4,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.MEDIUM,
            false,
            128,
            128
    ),
    // Cipher C003
    TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA(
            0xC003,
            "ECDH-ECDSA-DES-CBC3-SHA",
            KeyExchange.ECDHe,
            Authentication.ECDH,
            Encryption.TRIPLE_DES,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            112,
            168
    ),
    // Cipher C004
    TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA(
            0xC004,
            "ECDH-ECDSA-AES128-SHA",
            KeyExchange.ECDHe,
            Authentication.ECDH,
            Encryption.AES128,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher C005
    TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA(
            0xC005,
            "ECDH-ECDSA-AES256-SHA",
            KeyExchange.ECDHe,
            Authentication.ECDH,
            Encryption.AES256,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher C006
    TLS_ECDHE_ECDSA_WITH_NULL_SHA(
            0xC006,
            "ECDHE-ECDSA-NULL-SHA",
            KeyExchange.EECDH,
            Authentication.ECDSA,
            Encryption.eNULL,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.STRONG_NONE,
            true,
            0,
            0
    ),
    // Cipher C007
    TLS_ECDHE_ECDSA_WITH_RC4_128_SHA(
            0xC007,
            "ECDHE-ECDSA-RC4-SHA",
            KeyExchange.EECDH,
            Authentication.ECDSA,
            Encryption.RC4,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.MEDIUM,
            false,
            128,
            128
    ),
    // Cipher C008
    TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA(
            0xC008,
            "ECDHE-ECDSA-DES-CBC3-SHA",
            KeyExchange.EECDH,
            Authentication.ECDSA,
            Encryption.TRIPLE_DES,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            112,
            168
    ),
    // Cipher C009
    TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA(
            0xC009,
            "ECDHE-ECDSA-AES128-SHA",
            KeyExchange.EECDH,
            Authentication.ECDSA,
            Encryption.AES128,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher C00A
    TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA(
            0xC00A,
            "ECDHE-ECDSA-AES256-SHA",
            KeyExchange.EECDH,
            Authentication.ECDSA,
            Encryption.AES256,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher C00B
    TLS_ECDH_RSA_WITH_NULL_SHA(
            0xC00B,
            "ECDH-RSA-NULL-SHA",
            KeyExchange.ECDHr,
            Authentication.ECDH,
            Encryption.eNULL,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.STRONG_NONE,
            true,
            0,
            0
    ),
    // Cipher C00C
    TLS_ECDH_RSA_WITH_RC4_128_SHA(
            0xC00C,
            "ECDH-RSA-RC4-SHA",
            KeyExchange.ECDHr,
            Authentication.ECDH,
            Encryption.RC4,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.MEDIUM,
            false,
            128,
            128
    ),
    // Cipher C00D
    TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA(
            0xC00D,
            "ECDH-RSA-DES-CBC3-SHA",
            KeyExchange.ECDHr,
            Authentication.ECDH,
            Encryption.TRIPLE_DES,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            112,
            168
    ),
    // Cipher C00E
    TLS_ECDH_RSA_WITH_AES_128_CBC_SHA(
            0xC00E,
            "ECDH-RSA-AES128-SHA",
            KeyExchange.ECDHr,
            Authentication.ECDH,
            Encryption.AES128,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher C00F
    TLS_ECDH_RSA_WITH_AES_256_CBC_SHA(
            0xC00F,
            "ECDH-RSA-AES256-SHA",
            KeyExchange.ECDHr,
            Authentication.ECDH,
            Encryption.AES256,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher C010
    TLS_ECDHE_RSA_WITH_NULL_SHA(
            0xC010,
            "ECDHE-RSA-NULL-SHA",
            KeyExchange.EECDH,
            Authentication.RSA,
            Encryption.eNULL,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.STRONG_NONE,
            true,
            0,
            0
    ),
    // Cipher C011
    TLS_ECDHE_RSA_WITH_RC4_128_SHA(
            0xC011,
            "ECDHE-RSA-RC4-SHA",
            KeyExchange.EECDH,
            Authentication.RSA,
            Encryption.RC4,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.MEDIUM,
            false,
            128,
            128
    ),
    // Cipher C012
    TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA(
            0xC012,
            "ECDHE-RSA-DES-CBC3-SHA",
            KeyExchange.EECDH,
            Authentication.RSA,
            Encryption.TRIPLE_DES,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            112,
            168
    ),
    // Cipher C013
    TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA(
            0xC013,
            "ECDHE-RSA-AES128-SHA",
            KeyExchange.EECDH,
            Authentication.RSA,
            Encryption.AES128,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher C014
    TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA(
            0xC014,
            "ECDHE-RSA-AES256-SHA",
            KeyExchange.EECDH,
            Authentication.RSA,
            Encryption.AES256,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher C015
    TLS_ECDH_anon_WITH_NULL_SHA(
            0xC015,
            "AECDH-NULL-SHA",
            KeyExchange.EECDH,
            Authentication.aNULL,
            Encryption.eNULL,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.STRONG_NONE,
            true,
            0,
            0
    ),
    // Cipher C016
    TLS_ECDH_anon_WITH_RC4_128_SHA(
            0xC016,
            "AECDH-RC4-SHA",
            KeyExchange.EECDH,
            Authentication.aNULL,
            Encryption.RC4,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.MEDIUM,
            false,
            128,
            128
    ),
    // Cipher C017
    TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA(
            0xC017,
            "AECDH-DES-CBC3-SHA",
            KeyExchange.EECDH,
            Authentication.aNULL,
            Encryption.TRIPLE_DES,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            112,
            168
    ),
    // Cipher C018
    TLS_ECDH_anon_WITH_AES_128_CBC_SHA(
            0xC018,
            "AECDH-AES128-SHA",
            KeyExchange.EECDH,
            Authentication.aNULL,
            Encryption.AES128,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher C019
    TLS_ECDH_anon_WITH_AES_256_CBC_SHA(
            0xC019,
            "AECDH-AES256-SHA",
            KeyExchange.EECDH,
            Authentication.aNULL,
            Encryption.AES256,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    /* SRP ciphersuite from RFC 5054 */
    // Cipher C01A
    TLS_SRP_SHA_WITH_3DES_EDE_CBC_SHA(
            0xC01A,
            "SRP-3DES-EDE-CBC-SHA",
            KeyExchange.SRP,
            Authentication.SRP,
            Encryption.TRIPLE_DES,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            false,
            112,
            168
    ),
    // Cipher C01B
    TLS_SRP_SHA_RSA_WITH_3DES_EDE_CBC_SHA(
            0xC01B,
            "SRP-RSA-3DES-EDE-CBC-SHA",
            KeyExchange.SRP,
            Authentication.RSA,
            Encryption.TRIPLE_DES,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            false,
            112,
            168
    ),
    // Cipher C01C
    TLS_SRP_SHA_DSS_WITH_3DES_EDE_CBC_SHA(
            0xC01C,
            "SRP-DSS-3DES-EDE-CBC-SHA",
            KeyExchange.SRP,
            Authentication.DSS,
            Encryption.TRIPLE_DES,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            false,
            112,
            168
    ),
    // Cipher C01D
    TLS_SRP_SHA_WITH_AES_128_CBC_SHA(
            0xC01D,
            "SRP-AES-128-CBC-SHA",
            KeyExchange.SRP,
            Authentication.SRP,
            Encryption.AES128,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            false,
            128,
            128
    ),
    // Cipher C01E
    TLS_SRP_SHA_RSA_WITH_AES_128_CBC_SHA(
            0xC01E,
            "SRP-RSA-AES-128-CBC-SHA",
            KeyExchange.SRP,
            Authentication.RSA,
            Encryption.AES128,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            false,
            128,
            128
    ),
    // Cipher C01F
    TLS_SRP_SHA_DSS_WITH_AES_128_CBC_SHA(
            0xC01F,
            "SRP-DSS-AES-128-CBC-SHA",
            KeyExchange.SRP,
            Authentication.DSS,
            Encryption.AES128,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            false,
            128,
            128
    ),
    // Cipher C020
    TLS_SRP_SHA_WITH_AES_256_CBC_SHA(
            0xC020,
            "SRP-AES-256-CBC-SHA",
            KeyExchange.SRP,
            Authentication.SRP,
            Encryption.AES256,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            false,
            256,
            256
    ),
    // Cipher C021
    TLS_SRP_SHA_RSA_WITH_AES_256_CBC_SHA(
            0xC021,
            "SRP-RSA-AES-256-CBC-SHA",
            KeyExchange.SRP,
            Authentication.RSA,
            Encryption.AES256,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            false,
            256,
            256
    ),
    // Cipher C022
    TLS_SRP_SHA_DSS_WITH_AES_256_CBC_SHA(
            0xC022,
            "SRP-DSS-AES-256-CBC-SHA",
            KeyExchange.SRP,
            Authentication.DSS,
            Encryption.AES256,
            MessageDigest.SHA1,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            false,
            256,
            256
    ),
    /* HMAC based TLS v1.2 ciphersuites from RFC5289 */
    // Cipher C023
    TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256(
            0xC023,
            "ECDHE-ECDSA-AES128-SHA256",
            KeyExchange.EECDH,
            Authentication.ECDSA,
            Encryption.AES128,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher C024
    TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384(
            0xC024,
            "ECDHE-ECDSA-AES256-SHA384",
            KeyExchange.EECDH,
            Authentication.ECDSA,
            Encryption.AES256,
            MessageDigest.SHA384,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher C025
    TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256(
            0xC025,
            "ECDH-ECDSA-AES128-SHA256",
            KeyExchange.ECDHe,
            Authentication.ECDH,
            Encryption.AES128,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher C026
    TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384(
            0xC026,
            "ECDH-ECDSA-AES256-SHA384",
            KeyExchange.ECDHe,
            Authentication.ECDH,
            Encryption.AES256,
            MessageDigest.SHA384,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher C027
    TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256(
            0xC027,
            "ECDHE-RSA-AES128-SHA256",
            KeyExchange.EECDH,
            Authentication.RSA,
            Encryption.AES128,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher C028
    TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384(
            0xC028,
            "ECDHE-RSA-AES256-SHA384",
            KeyExchange.EECDH,
            Authentication.RSA,
            Encryption.AES256,
            MessageDigest.SHA384,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher C029
    TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256(
            0xC029,
            "ECDH-RSA-AES128-SHA256",
            KeyExchange.ECDHr,
            Authentication.ECDH,
            Encryption.AES128,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher C02A
    TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384(
            0xC02A,
            "ECDH-RSA-AES256-SHA384",
            KeyExchange.ECDHr,
            Authentication.ECDH,
            Encryption.AES256,
            MessageDigest.SHA384,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    /* GCM based TLS v1.2 ciphersuites from RFC5289 */
    // Cipher C02B
    TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256(
            0xC02B,
            "ECDHE-ECDSA-AES128-GCM-SHA256",
            KeyExchange.EECDH,
            Authentication.ECDSA,
            Encryption.AES128GCM,
            MessageDigest.AEAD,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher C02C
    TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384(
            0xC02C,
            "ECDHE-ECDSA-AES256-GCM-SHA384",
            KeyExchange.EECDH,
            Authentication.ECDSA,
            Encryption.AES256GCM,
            MessageDigest.AEAD,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher C02D
    TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256(
            0xC02D,
            "ECDH-ECDSA-AES128-GCM-SHA256",
            KeyExchange.ECDHe,
            Authentication.ECDH,
            Encryption.AES128GCM,
            MessageDigest.AEAD,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher C02E
    TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384(
            0xC02E,
            "ECDH-ECDSA-AES256-GCM-SHA384",
            KeyExchange.ECDHe,
            Authentication.ECDH,
            Encryption.AES256GCM,
            MessageDigest.AEAD,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher C02F
    TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256(
            0xC02F,
            "ECDHE-RSA-AES128-GCM-SHA256",
            KeyExchange.EECDH,
            Authentication.RSA,
            Encryption.AES128GCM,
            MessageDigest.AEAD,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher C030
    TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384(
            0xC030,
            "ECDHE-RSA-AES256-GCM-SHA384",
            KeyExchange.EECDH,
            Authentication.RSA,
            Encryption.AES256GCM,
            MessageDigest.AEAD,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher C031
    TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256(
            0xC031,
            "ECDH-RSA-AES128-GCM-SHA256",
            KeyExchange.ECDHr,
            Authentication.ECDH,
            Encryption.AES128GCM,
            MessageDigest.AEAD,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher C032
    TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384(
            0xC032,
            "ECDH-RSA-AES256-GCM-SHA384",
            KeyExchange.ECDHr,
            Authentication.ECDH,
            Encryption.AES256GCM,
            MessageDigest.AEAD,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),

    /* PSK ciphers 0xC033 to 0xC03B
     * Unsupported by both Java and OpenSSL
     */

    /* ARIA ciphers 0xC03C to 0xC071
     * Unsupported by both Java and OpenSSL
     */
    // Cipher C072
    TLS_ECDHE_ECDSA_WITH_CAMELLIA_128_CBC_SHA256(
            0xC072,
            "ECDHE-ECDSA-CAMELLIA128-SHA256",
            KeyExchange.EECDH,
            Authentication.ECDSA,
            Encryption.CAMELLIA128,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher C073
    TLS_ECDHE_ECDSA_WITH_CAMELLIA_256_CBC_SHA384(
            0xC073,
            "ECDHE-ECDSA-CAMELLIA256-SHA384",
            KeyExchange.EECDH,
            Authentication.ECDSA,
            Encryption.CAMELLIA256,
            MessageDigest.SHA384,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher C074
    TLS_ECDH_ECDSA_WITH_CAMELLIA_128_CBC_SHA256(
            0xC074,
            "ECDH-ECDSA-CAMELLIA128-SHA256",
            KeyExchange.ECDHe,
            Authentication.ECDH,
            Encryption.CAMELLIA128,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher C075
    TLS_ECDH_ECDSA_WITH_CAMELLIA_256_CBC_SHA384(
            0xC075,
            "ECDH-ECDSA-CAMELLIA256-SHA384",
            KeyExchange.ECDHe,
            Authentication.ECDH,
            Encryption.CAMELLIA256,
            MessageDigest.SHA384,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher C076
    TLS_ECDHE_RSA_WITH_CAMELLIA_128_CBC_SHA256(
            0xC076,
            "ECDHE-RSA-CAMELLIA128-SHA256",
            KeyExchange.EECDH,
            Authentication.RSA,
            Encryption.CAMELLIA128,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher C077
    TLS_ECDHE_RSA_WITH_CAMELLIA_256_CBC_SHA384(
            0xC077,
            "ECDHE-RSA-CAMELLIA256-SHA384",
            KeyExchange.EECDH,
            Authentication.RSA,
            Encryption.CAMELLIA256,
            MessageDigest.SHA384,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),
    // Cipher C078
    TLS_ECDH_RSA_WITH_CAMELLIA_128_CBC_SHA256(
            0xC078,
            "ECDH-RSA-CAMELLIA128-SHA256",
            KeyExchange.ECDHr,
            Authentication.ECDH,
            Encryption.CAMELLIA128,
            MessageDigest.SHA256,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            128,
            128
    ),
    // Cipher C079
    TLS_ECDH_RSA_WITH_CAMELLIA_256_CBC_SHA384(
            0xC079,
            "ECDH-RSA-CAMELLIA256-SHA384",
            KeyExchange.ECDHr,
            Authentication.ECDH,
            Encryption.CAMELLIA256,
            MessageDigest.SHA384,
            Protocol.TLSv1_2,
            false,
            EncryptionLevel.HIGH,
            true,
            256,
            256
    ),

    // Cipher 0x010080 (SSLv2)
    // RC4_128_WITH_MD5
    SSL_CK_RC4_128_WITH_MD5(
            -1,
            "RC4-MD5",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.RC4,
            MessageDigest.MD5,
            Protocol.SSLv2,
            false,
            EncryptionLevel.MEDIUM,
            false,
            128,
            128
    ),
    // Cipher 0x020080 (SSLv2)
    SSL2_RC4_128_EXPORT40_WITH_MD5(
            -1,
            "EXP-RC4-MD5",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.RC4,
            MessageDigest.MD5,
            Protocol.SSLv2,
            true,
            EncryptionLevel.EXP40,
            false,
            40,
            128,
            "SSL_RC4_128_EXPORT40_WITH_MD5"
    ),
    // Cipher 0x030080 (SSLv2)
    // RC2_128_CBC_WITH_MD5
    SSL_CK_RC2_128_CBC_WITH_MD5(
            -1,
            "RC2-MD5",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.RC2,
            MessageDigest.MD5,
            Protocol.SSLv2,
            false,
            EncryptionLevel.MEDIUM,
            false,
            128,
            128
    ),
    // Cipher 0x040080 (SSLv2)
    // RC2_128_CBC_EXPORT40_WITH_MD5
    SSL_CK_RC2_128_CBC_EXPORT40_WITH_MD5(
            -1,
            "EXP-RC2-CBC-MD5",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.RC2,
            MessageDigest.MD5,
            Protocol.SSLv2,
            true,
            EncryptionLevel.EXP40,
            false,
            40,
            128
    ),
    // Cipher 0x050080 (SSLv2)
    // IDEA_128_CBC_WITH_MD5
    SSL2_IDEA_128_CBC_WITH_MD5(
            -1,
            "IDEA-CBC-MD5",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.IDEA,
            MessageDigest.MD5,
            Protocol.SSLv2,
            false, EncryptionLevel.MEDIUM,
            false,
            128,
            128,
            "SSL_CK_IDEA_128_CBC_WITH_MD5"
    ),
    // Cipher 0x060040 (SSLv2)
    // DES_64_CBC_WITH_MD5
    SSL2_DES_64_CBC_WITH_MD5(
            -1,
            "DES-CBC-MD5",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.DES,
            MessageDigest.MD5,
            Protocol.SSLv2,
            false,
            EncryptionLevel.LOW,
            false,
            56,
            56,
            "SSL_CK_DES_64_CBC_WITH_MD5"
    ),
    // Cipher 0x0700C0 (SSLv2)
    // DES_192_EDE3_CBC_WITH_MD5
    SSL2_DES_192_EDE3_CBC_WITH_MD5(
            -1,
            "DES-CBC3-MD5",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.TRIPLE_DES,
            MessageDigest.MD5,
            Protocol.SSLv2,
            false,
            EncryptionLevel.HIGH,
            false,
            112,
            168,
            "SSL_CK_DES_192_EDE3_CBC_WITH_MD5"
    );

    /* TEMP_GOST_TLS*/
    /*
    // Cipher FF00
    TLS_GOSTR341094_RSA_WITH_28147_CNT_MD5(
            "GOST-MD5",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.eGOST2814789CNT,
            MessageDigest.MD5,
            Protocol.TLSv1,
            false,
            EncryptionLevel.HIGH,
            false,
            256,
            256
     ),
     TLS_RSA_WITH_28147_CNT_GOST94(
            "GOST-GOST94",
            KeyExchange.RSA,
            Authentication.RSA,
            Encryption.eGOST2814789CNT,
            MessageDigest.GOST94,
            Protocol.TLSv1,
            false, EncryptionLevel.HIGH,false,
            256,
            256
     ),
     {
     1,
     "GOST-GOST89MAC",
     0x0300ff02,
     KeyExchange.RSA,
     Authentication.RSA,
     Encryption.eGOST2814789CNT,
     MessageDigest.GOST89MAC,
     Protocol.TLSv1,
     false, EncryptionLevel.HIGH,false,

     256,
     256
     ),
     {
     1,
     "GOST-GOST89STREAM",
     0x0300ff03,
     KeyExchange.RSA,
     Authentication.RSA,
     Encryption.eGOST2814789CNT,
     MessageDigest.GOST89MAC,
     Protocol.TLSv1,
     false, EncryptionLevel.HIGH,false,
     256,
     256
     },*/


    private final int id;
    private final String openSSLAlias;
    private final Set<String> jsseNames;
    private final KeyExchange kx;
    private final Authentication au;
    private final Encryption enc;
    private final MessageDigest mac;
    private final Protocol protocol;
    private final boolean export;
    private final EncryptionLevel level;
    private final boolean fipsCompatible;
    /**
     * Number of bits really used
     */
    private final int strength_bits;
    /**
     * Number of bits for algorithm
     */
    private final int alg_bits;

    private Cipher(int id, String openSSLAlias, KeyExchange kx, Authentication au, Encryption enc,
            MessageDigest mac, Protocol protocol, boolean export, EncryptionLevel level,
            boolean fipsCompatible, int strength_bits, int alg_bits, String... jsseAltNames) {
        this.id = id;
        this.openSSLAlias = openSSLAlias;
        Set<String> names = new HashSet<>();
        if (jsseAltNames != null) {
            names.addAll(Arrays.asList(jsseAltNames));
        }
        names.add(name());
        this.jsseNames = Collections.unmodifiableSet(names);
        this.kx = kx;
        this.au = au;
        this.enc = enc;
        this.mac = mac;
        this.protocol = protocol;
        this.export = export;
        this.level = level;
        this.fipsCompatible = fipsCompatible;
        this.strength_bits = strength_bits;
        this.alg_bits = alg_bits;
    }

    public int getId() {
        return id;
    }

    public String getOpenSSLAlias() {
        return openSSLAlias;
    }

    public Set<String> getJsseNames() {
        return jsseNames;
    }

    public KeyExchange getKx() {
        return kx;
    }

    public Authentication getAu() {
        return au;
    }

    public Encryption getEnc() {
        return enc;
    }

    public MessageDigest getMac() {
        return mac;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public boolean isExport() {
        return export;
    }

    public EncryptionLevel getLevel() {
        return level;
    }

    public boolean isFipsCompatible() {
        return fipsCompatible;
    }

    public int getStrength_bits() {
        return strength_bits;
    }

    public int getAlg_bits() {
        return alg_bits;
    }


    private static final Map<Integer,Cipher> idMap = new HashMap<>();

    static {
        for (Cipher cipher : Cipher.values()) {
            int id = cipher.getId();

            if (id > 0 && id < 0xFFFF) {
                idMap.put(Integer.valueOf(id), cipher);
            }
        }
    }


    public static Cipher valueOf(int cipherId) {
        return idMap.get(Integer.valueOf(cipherId));
    }
}
