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
 * Is called during handshake and hooked into openssl via {@code SSL_CTX_set_cert_verify_callback}.
 */
public interface CertificateVerifier {

    /**
     * Returns {@code true} if the passed in certificate chain could be verified and so the handshake
     * should be successful, {@code false} otherwise.
     *
     * @param ssl               the SSL instance
     * @param x509              the {@code X509} certificate chain
     * @param authAlgorithm     the auth algorithm
     * @return verified         {@code true} if verified successful, {@code false} otherwise
     */
    boolean verify(long ssl, byte[][] x509, String authAlgorithm);
}
