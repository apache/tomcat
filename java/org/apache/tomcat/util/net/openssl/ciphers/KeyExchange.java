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

enum KeyExchange {
    EECDH /* SSL_kECDHE - ephemeral ECDH */,
    RSA   /* SSL_kRSA   - RSA key exchange */,
    DHr   /* SSL_kDHr   - DH cert, RSA CA cert */ /* no such ciphersuites supported! */,
    DHd   /* SSL_kDHd   - DH cert, DSA CA cert */ /* no such ciphersuite supported! */,
    EDH   /* SSL_kDHE   - tmp DH key no DH cert */,
    PSK   /* SSK_kPSK   - PSK */,
    FZA   /* SSL_kFZA   - Fortezza */  /* no such ciphersuite supported! */,
    KRB5  /* SSL_kKRB5  - Kerberos 5 key exchange */,
    ECDHr /* SSL_kECDHr - ECDH cert, RSA CA cert */,
    ECDHe /* SSL_kECDHe - ECDH cert, ECDSA CA cert */,
    GOST  /* SSL_kGOST  - GOST key exchange */,
    SRP   /* SSL_kSRP   - SRP */,
    RSAPSK,
    ECDHEPSK,
    DHEPSK;
}
