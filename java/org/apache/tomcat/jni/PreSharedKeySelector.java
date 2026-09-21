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
 * Is called during a TLSv1.2 handshake and hooked into OpenSSL via {@code SSL_CTX_set_psk_server_callback}.
 */
public interface PreSharedKeySelector {

    /**
     * Selects the pre-shared key for the provided identity.
     *
     * @param ssl      the SSL instance
     * @param identity the PSK identity provided by the client
     *
     * @return the pre-shared key, or {@code null} if the identity is not recognized
     */
    byte[] select(long ssl, String identity);
}
