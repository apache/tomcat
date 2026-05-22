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
package org.apache.tomcat.util.net.openssl.ciphers;

import java.util.HashMap;
import java.util.Map;

/**
 * All the signature schemes for TLS 1.3.
 *
 * @see <a href="https://www.iana.org/assignments/tls-parameters/tls-parameters.xhtml#tls-signaturescheme" >The
 *          signature schemes registry</a>
 */
public enum SignatureScheme {

    // RSASSA-PKCS1-v1_5 algorithms
    /** RSA with SHA-256 (PKCS1-v1_5). */
    rsa_pkcs1_sha256(0x0401, Authentication.RSA),
    /** RSA with SHA-384 (PKCS1-v1_5). */
    rsa_pkcs1_sha384(0x0501, Authentication.RSA),
    /** RSA with SHA-512 (PKCS1-v1_5). */
    rsa_pkcs1_sha512(0x0601, Authentication.RSA),

    // ECDSA algorithms
    /** ECDSA with P-256 and SHA-256. */
    ecdsa_secp256r1_sha256(0x0403, Authentication.ECDSA),
    /** ECDSA with P-384 and SHA-384. */
    ecdsa_secp384r1_sha384(0x0503, Authentication.ECDSA),
    /** ECDSA with P-521 and SHA-512. */
    ecdsa_secp521r1_sha512(0x0603, Authentication.ECDSA),

    // RSASSA-PSS algorithms with public key OID rsaEncryption
    /** RSA-PSS with RSA-E and SHA-256. */
    rsa_pss_rsae_sha256(0x0804, Authentication.RSA),
    /** RSA-PSS with RSA-E and SHA-384. */
    rsa_pss_rsae_sha384(0x0805, Authentication.RSA),
    /** RSA-PSS with RSA-E and SHA-512. */
    rsa_pss_rsae_sha512(0x0806, Authentication.RSA),

    // EdDSA algorithms
    /** EdDSA with Ed25519. */
    ed25519(0x0807, Authentication.EdDSA),
    /** EdDSA with Ed448. */
    ed448(0x0808, Authentication.EdDSA),

    // RSASSA-PSS algorithms with public key OID RSASSA-PSS
    /** RSA-PSS with SHA-256. */
    rsa_pss_pss_sha256(0x0809, Authentication.RSA),
    /** RSA-PSS with SHA-384. */
    rsa_pss_pss_sha384(0x080a, Authentication.RSA),
    /** RSA-PSS with SHA-512. */
    rsa_pss_pss_sha512(0x080b, Authentication.RSA),

    // Legacy algorithms
    /** RSA with SHA-1 (PKCS1-v1_5, legacy). */
    rsa_pkcs1_sha1(0x0201, Authentication.RSA),
    /** ECDSA with SHA-1 (legacy). */
    ecdsa_sha1(0x0203, Authentication.ECDSA),

    // ML-DSA algorithms
    /** ML-DSA level 2. */
    mldsa44(0x0904, Authentication.MLDSA),
    /** ML-DSA level 3. */
    mldsa65(0x0905, Authentication.MLDSA),
    /** ML-DSA level 5. */
    mldsa87(0x0906, Authentication.MLDSA),

    // SLH-DSA algorithms
    // Note: Mapped to ML-DSA for now, since not working
    /** SLH-DSA SHA2-128S. */
    slhdsa_sha2_128s(0x0911, Authentication.MLDSA),
    /** SLH-DSA SHA2-128F. */
    slhdsa_sha2_128f(0x0912, Authentication.MLDSA),
    /** SLH-DSA SHA2-192S. */
    slhdsa_sha2_192s(0x0913, Authentication.MLDSA),
    /** SLH-DSA SHA2-192F. */
    slhdsa_sha2_192f(0x0914, Authentication.MLDSA),
    /** SLH-DSA SHA2-256S. */
    slhdsa_sha2_256s(0x0915, Authentication.MLDSA),
    /** SLH-DSA SHA2-256F. */
    slhdsa_sha2_256f(0x0916, Authentication.MLDSA),
    /** SLH-DSA SHAKE-128S. */
    slhdsa_shake_128s(0x0917, Authentication.MLDSA),
    /** SLH-DSA SHAKE-128F. */
    slhdsa_shake_128f(0x0918, Authentication.MLDSA),
    /** SLH-DSA SHAKE-192S. */
    slhdsa_shake_192s(0x0919, Authentication.MLDSA),
    /** SLH-DSA SHAKE-192F. */
    slhdsa_shake_192f(0x091a, Authentication.MLDSA),
    /** SLH-DSA SHAKE-256S. */
    slhdsa_shake_256s(0x091b, Authentication.MLDSA),
    /** SLH-DSA SHAKE-256F. */
    slhdsa_shake_256f(0x091c, Authentication.MLDSA),

    // SM2 algorithms
    // Note: Mapped to ML-DSA for now, since not confirmed to be working
    /** SM2 with SM3. */
    sm2sig_sm3(0x0708, Authentication.MLDSA);

    /** The numeric ID of this signature scheme. */
    private final int id;
    /** The authentication type for this scheme. */
    private final Authentication auth;

    /**
     * Creates a new signature scheme.
     * @param id the numeric ID
     * @param auth the authentication type
     */
    SignatureScheme(int id, Authentication auth) {
        this.id = id;
        this.auth = auth;
    }

    /**
     * Returns the numeric ID of this signature scheme.
     * @return the ID
     */
    public int getId() {
        return this.id;
    }

    /**
     * Returns the authentication type for this scheme.
     * @return the authentication type
     */
    public Authentication getAuth() {
        return this.auth;
    }

    /** Mapping from numeric IDs to signature schemes. */
    private static final Map<Integer,SignatureScheme> idMap = new HashMap<>();

    static {
        for (SignatureScheme scheme : values()) {
            int id = scheme.getId();

            if (id > 0 && id < 0xFFFF) {
                idMap.put(Integer.valueOf(id), scheme);
            }
        }
    }

    /**
     * Looks up a signature scheme by its numeric ID.
     * @param schemeId the numeric ID
     * @return the signature scheme, or {@code null} if not found
     */
    public static SignatureScheme valueOf(int schemeId) {
        return idMap.get(Integer.valueOf(schemeId));
    }
}
