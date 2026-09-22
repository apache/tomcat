/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.jni;

/**
 * The interface for the Tomcat code that responds to the callback from the OpenSSL layer in Tomcat Native to allow
 * Tomcat to select a pre-shared key. It supports TLSv1.2 {@code SSL_CTX_set_psk_server_callback} and TLSv1.3
 * {@code SSL_CTX_set_psk_find_session_callback}.
 */
public interface PreSharedKeySelector {

    /**
     * Selects the TLSv1.2 pre-shared key on the server side given the provided client identity.
     *
     * @param ssl      the SSL instance
     * @param identity the PSK identity provided by the client
     *
     * @return the pre-shared key, or {@code null} if the identity is not recognized. OpenSSL limits the key to between
     *             1 and 512 bytes long (inclusive). If the byte sequence is truly random then 16 bytes are recommended
     *             for 128-bit ciphers and 32 bytes for 256-bit ciphers.
     */
    byte[] select(long ssl, String identity);

    /**
     * Selects the TLSv1.3 pre-shared key and digest on the server side given the provided client identity.
     * <p>
     * The callback is a little more complex for TLSv1.3. The return value is still the pre-shared key but OpenSSL also
     * needs to know which digest to use. Because the OpenSSL API only exposes a cipher for this, that is what Tomcat
     * populates the {@code cipherSuite} array with but only the digest is relevant.
     *
     * @param ssl         the SSL instance
     * @param identity    the PSK identity provided by the client
     * @param cipherSuite a single-element array that must be populated with a IANA TLSv1.3 cipher suite identifier
     *
     * @return the pre-shared key (strictly the input to the KDF), or {@code null} if the identity is not recognized.
     *             OpenSSL limits the key to between 1 and 48 bytes long (inclusive). If the byte sequence is truly
     *             random then 16 bytes are recommended for 128-bit ciphers and 32 bytes for 256-bit ciphers.
     */
    byte[] select(long ssl, byte[] identity, int[] cipherSuite);


    /**
     * Selects the TLS1v2 identity and pre-shared key that the client will present to a server.
     *
     * @param ssl      the SSL instance
     * @param identity a single-element array that must be populated with the PSK identity
     *
     * @return the pre-shared key, or {@code null} if no key is available
     */
    byte[] selectClient(long ssl, String[] identity);

}
