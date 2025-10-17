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
 * @see <a href="https://www.iana.org/assignments/tls-parameters/tls-parameters.xhtml#tls-signaturescheme" >The signature schemes
 *          registry</a>
 */
public enum SignatureScheme {

    // RSASSA-PKCS1-v1_5 algorithms
    rsa_pkcs1_sha256(0x0401, Authentication.RSA),
    rsa_pkcs1_sha384(0x0501, Authentication.RSA),
    rsa_pkcs1_sha512(0x0601, Authentication.RSA),

    // ECDSA algorithms
    ecdsa_secp256r1_sha256(0x0403, Authentication.ECDSA),
    ecdsa_secp384r1_sha384(0x0503, Authentication.ECDSA),
    ecdsa_secp521r1_sha512(0x0603, Authentication.ECDSA),

    // RSASSA-PSS algorithms with public key OID rsaEncryption
    rsa_pss_rsae_sha256(0x0804, Authentication.RSA),
    rsa_pss_rsae_sha384(0x0805, Authentication.RSA),
    rsa_pss_rsae_sha512(0x0806, Authentication.RSA),

    // EdDSA algorithms
    ed25519(0x0807, Authentication.EdDSA),
    ed448(0x0808, Authentication.EdDSA),

    // RSASSA-PSS algorithms with public key OID RSASSA-PSS
    rsa_pss_pss_sha256(0x0809, Authentication.RSA),
    rsa_pss_pss_sha384(0x080a, Authentication.RSA),
    rsa_pss_pss_sha512(0x080b, Authentication.RSA),

    // Legacy algorithms
    rsa_pkcs1_sha1(0x0201, Authentication.RSA),
    ecdsa_sha1(0x0203, Authentication.ECDSA),

    // ML-DSA algorithms
    mldsa44(0x0904, Authentication.MLDSA),
    mldsa65(0x0905, Authentication.MLDSA),
    mldsa87(0x0906, Authentication.MLDSA);

    private final int id;
    private final Authentication auth;

    SignatureScheme(int id, Authentication auth) {
        this.id = id;
        this.auth = auth;
    }

    /**
     * @return the id
     */
    public int getId() {
        return this.id;
    }

    /**
     * @return the auth
     */
    public Authentication getAuth() {
        return this.auth;
    }

    private static final Map<Integer,SignatureScheme> idMap = new HashMap<>();

    static {
        for (SignatureScheme scheme : values()) {
            int id = scheme.getId();

            if (id > 0 && id < 0xFFFF) {
                idMap.put(Integer.valueOf(id), scheme);
            }
        }
    }


    public static SignatureScheme valueOf(int schemeId) {
        return idMap.get(Integer.valueOf(schemeId));
    }
}
