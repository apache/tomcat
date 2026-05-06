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
 * Enumerates the encryption algorithms supported by OpenSSL cipher suites.
 */
public enum Encryption {
    /** AES with 128-bit key. */
    AES128,
    /** AES with 128-bit key in CCM mode. */
    AES128CCM,
    /** AES with 128-bit key in CCM mode with 8-byte tag. */
    AES128CCM8,
    /** AES with 128-bit key in GCM mode. */
    AES128GCM,
    /** AES with 256-bit key. */
    AES256,
    /** AES with 256-bit key in CCM mode. */
    AES256CCM,
    /** AES with 256-bit key in CCM mode with 8-byte tag. */
    AES256CCM8,
    /** AES with 256-bit key in GCM mode. */
    AES256GCM,
    /** ARIA with 128-bit key in GCM mode. */
    ARIA128GCM,
    /** ARIA with 256-bit key in GCM mode. */
    ARIA256GCM,
    /** Camellia with 256-bit key. */
    CAMELLIA256,
    /** Camellia with 128-bit key. */
    CAMELLIA128,
    /** ChaCha20-Poly1305 AEAD cipher. */
    CHACHA20POLY1305,
    /** Triple DES encryption. */
    TRIPLE_DES,
    /** DES encryption. */
    DES,
    /** IDEA encryption. */
    IDEA,
    /** GOST 28147-89 with_CNT keyed hash. */
    eGOST2814789CNT,
    /** SEED encryption. */
    SEED,
    /** FZA encryption. */
    FZA,
    /** RC4 stream cipher. */
    RC4,
    /** RC2 encryption. */
    RC2,
    /** No encryption (NULL cipher). */
    eNULL
}
