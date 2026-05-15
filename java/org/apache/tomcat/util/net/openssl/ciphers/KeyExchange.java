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

/**
 * Key exchange algorithms supported by OpenSSL.
 */
public enum KeyExchange {
    /**
     * Ephemeral ECDH (SSL_kEECDH).
     */
    EECDH,
    /**
     * RSA key exchange (SSL_kRSA).
     */
    RSA,
    /**
     * DH cert, RSA CA cert (SSL_kDHr). Not supported.
     */
    DHr,
    /**
     * DH cert, DSA CA cert (SSL_kDHd). Not supported.
     */
    DHd,
    /**
     * Temporary DH key, no DH cert (SSL_kDHE).
     */
    EDH,
    /**
     * Pre-shared key (SSK_kPSK).
     */
    PSK,
    /**
     * Fortezza (SSL_kFZA). Not supported.
     */
    FZA,
    /**
     * Kerberos 5 key exchange (SSL_kKRB5).
     */
    KRB5,
    /**
     * ECDH cert, RSA CA cert (SSL_kECDHr).
     */
    ECDHr,
    /**
     * ECDH cert, ECDSA CA cert (SSL_kECDHe).
     */
    ECDHe,
    /**
     * GOST key exchange (SSL_kGOST).
     */
    GOST,
    /**
     * SRP (SSL_kSRP).
     */
    SRP,
    /**
     * RSA with pre-shared key.
     */
    RSAPSK,
    /**
     * ECDHE with pre-shared key.
     */
    ECDHEPSK,
    /**
     * DHE with pre-shared key.
     */
    DHEPSK,
    /**
     * Any key exchange (TLS 1.3).
     */
    ANY
}
