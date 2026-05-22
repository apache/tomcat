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
 * All the supported named groups for TLS 1.3.
 *
 * @see <a href="https://www.iana.org/assignments/tls-parameters/tls-parameters.xhtml#tls-parameters-8" >The supported
 *          groups registry</a>
 */
public enum Group {

    // Elliptic Curve Groups (ECDHE)
    /**
     * NIST P-256 (secp256r1) elliptic curve group.
     */
    secp256r1(0x0017),
    /**
     * NIST P-384 (secp384r1) elliptic curve group.
     */
    secp384r1(0x0018),
    /**
     * NIST P-521 (secp521r1) elliptic curve group.
     */
    secp521r1(0x0019),
    /**
     * Curve25519 elliptic curve group.
     */
    x25519(0x001D),
    /**
     * Curve448 elliptic curve group.
     */
    x448(0x001E),

    // Finite Field Groups (DHE)
    /**
     * 2048-bit finite field Diffie-Hellman group.
     */
    ffdhe2048(0x0100),
    /**
     * 3072-bit finite field Diffie-Hellman group.
     */
    ffdhe3072(0x0101),
    /**
     * 4096-bit finite field Diffie-Hellman group.
     */
    ffdhe4096(0x0102),
    /**
     * 6144-bit finite field Diffie-Hellman group.
     */
    ffdhe6144(0x0103),
    /**
     * 8192-bit finite field Diffie-Hellman group.
     */
    ffdhe8192(0x0104),

    // SM2 Curve
    /**
     * SM2 elliptic curve group.
     */
    curveSM2(0x0029),

    // Post-Quantum Key Exchange
    /**
     * ML-KEM-512 post-quantum key exchange group.
     */
    MLKEM512(0x0200),
    /**
     * ML-KEM-768 post-quantum key exchange group.
     */
    MLKEM768(0x0201),
    /**
     * ML-KEM-1024 post-quantum key exchange group.
     */
    MLKEM1024(0x0202),

    // Hybrid Key Exchange
    /**
     * Hybrid secp256r1 + ML-KEM-768 key exchange group.
     */
    SecP256r1MLKEM768(0x11EB),
    /**
     * Hybrid x25519 + ML-KEM-768 key exchange group.
     */
    X25519MLKEM768(0x11EC),
    /**
     * Hybrid secp384r1 + ML-KEM-1024 key exchange group.
     */
    SecP384r1MLKEM1024(0x11ED),
    /**
     * Hybrid curveSM2 + ML-KEM-768 key exchange group.
     */
    curveSM2MLKEM768(0x11EE);

    private final int id;

    Group(int id) {
        this.id = id;
    }

    /**
     * Returns the numeric identifier for this group as defined in the TLS supported groups registry.
     *
     * @return the numeric group identifier
     */
    public int getId() {
        return this.id;
    }

    private static final Map<Integer,Group> idMap = new HashMap<>();

    static {
        for (Group group : values()) {
            int id = group.getId();

            if (id > 0 && id < 0xFFFF) {
                idMap.put(Integer.valueOf(id), group);
            }
        }
    }


    /**
     * Returns the Group enum constant for the given numeric group identifier.
     *
     * @param groupId The numeric group identifier
     * @return The corresponding Group, or {@code null} if not found
     */
    public static Group valueOf(int groupId) {
        return idMap.get(Integer.valueOf(groupId));
    }
}
