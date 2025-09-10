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

public enum SignatureAlgorithm {

    // RSASSA-PKCS1-v1_5 algorithms
    rsa_pkcs1_sha256(0x0401),
    rsa_pkcs1_sha384(0x0501),
    rsa_pkcs1_sha512(0x0601),

    // ECDSA algorithms
    ecdsa_secp256r1_sha256(0x0403),
    ecdsa_secp384r1_sha384(0x0503),
    ecdsa_secp521r1_sha512(0x0603),

    // RSASSA-PSS algorithms with public key OID rsaEncryption
    rsa_pss_rsae_sha256(0x0804),
    rsa_pss_rsae_sha384(0x0805),
    rsa_pss_rsae_sha512(0x0806),

    // EdDSA algorithms
    ed25519(0x0807),
    ed448(0x0808),

    // RSASSA-PSS algorithms with public key OID RSASSA-PSS
    rsa_pss_pss_sha256(0x0809),
    rsa_pss_pss_sha384(0x080a),
    rsa_pss_pss_sha512(0x080b),

    // Legacy algorithms
    rsa_pkcs1_sha1(0x0201),
    ecdsa_sha1(0x0203),

    // ML-DSA algorithms
    mldsa44(0x0904),
    mldsa65(0x0905),
    mldsa87(0x0906);

    private final int id;

    SignatureAlgorithm(int id) {
        this.id = id;
    }

    /**
     * @return the id
     */
    public int getId() {
        return this.id;
    }

    private static final Map<Integer,SignatureAlgorithm> idMap = new HashMap<>();

    static {
        for (SignatureAlgorithm group : values()) {
            int id = group.getId();

            if (id > 0 && id < 0xFFFF) {
                idMap.put(Integer.valueOf(id), group);
            }
        }
    }


    public static SignatureAlgorithm valueOf(int groupId) {
        return idMap.get(Integer.valueOf(groupId));
    }
}
