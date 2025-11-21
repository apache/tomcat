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
    secp256r1(0x0017),
    secp384r1(0x0018),
    secp521r1(0x0019),
    x25519(0x001D),
    x448(0x001E),

    // Finite Field Groups (DHE)
    ffdhe2048(0x0100),
    ffdhe3072(0x0101),
    ffdhe4096(0x0102),
    ffdhe6144(0x0103),
    ffdhe8192(0x0104),

    // Post-Quantum Key Exchange
    MLKEM512(0x0200),
    MLKEM768(0x0201),
    MLKEM1024(0x0202),

    // Hybrid Key Exchange
    SecP256r1MLKEM768(0x11EB),
    X25519MLKEM768(0x11EC),
    SecP384r1MLKEM1024(0x11ED);

    private final int id;

    Group(int id) {
        this.id = id;
    }

    /**
     * @return the id
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


    public static Group valueOf(int groupId) {
        return idMap.get(Integer.valueOf(groupId));
    }
}
