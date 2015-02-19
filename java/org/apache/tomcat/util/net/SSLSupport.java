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

package org.apache.tomcat.util.net;

import java.io.IOException;
import java.security.cert.X509Certificate;

/**
 * Defines an interface to interact with SSL sessions.
 */
public interface SSLSupport {
    /**
     * The Request attribute key for the cipher suite.
     */
    public static final String CIPHER_SUITE_KEY =
            "javax.servlet.request.cipher_suite";

    /**
     * The Request attribute key for the key size.
     */
    public static final String KEY_SIZE_KEY = "javax.servlet.request.key_size";

    /**
     * The Request attribute key for the client certificate chain.
     */
    public static final String CERTIFICATE_KEY =
            "javax.servlet.request.X509Certificate";

    /**
     * The Request attribute key for the session id.
     * This one is a Tomcat extension to the Servlet spec.
     */
    public static final String SESSION_ID_KEY =
            "javax.servlet.request.ssl_session_id";

    /**
     * The request attribute key for the session manager.
     * This one is a Tomcat extension to the Servlet spec.
     */
    public static final String SESSION_MGR =
            "javax.servlet.request.ssl_session_mgr";

    /**
     * The request attribute key under which the String indicating the protocol
     * that created the SSL socket is recorded - e.g. TLSv1 or TLSv1.2 etc.
     */
    public static final String PROTOCOL_VERSION_KEY =
            "org.apache.tomcat.util.net.secure_protocol_version";

    /**
     * The cipher suite being used on this connection.
     *
     * @return The name of the cipher suite as returned by the SSL/TLS
     *        implementation
     *
     * @throws IOException If an error occurs trying to obtain the cipher suite
     */
    public String getCipherSuite() throws IOException;

    /**
     * The client certificate chain (if any).
     *
     * @return The certificate chain presented by the client with the peer's
     *         certificate first, followed by those of any certificate
     *         authorities
     *
     * @throws IOException If an error occurs trying to obtain the certificate
     *                     chain
     */
    public X509Certificate[] getPeerCertificateChain() throws IOException;

    /**
     * Get the keysize.
     *
     * What we're supposed to put here is ill-defined by the
     * Servlet spec (S 4.7 again). There are at least 4 potential
     * values that might go here:
     *
     * (a) The size of the encryption key
     * (b) The size of the MAC key
     * (c) The size of the key-exchange key
     * (d) The size of the signature key used by the server
     *
     * Unfortunately, all of these values are nonsensical.
     *
     * @return The effective key size for the current cipher suite
     *
     * @throws IOException If an error occurs trying to obtain the key size
     */
    public Integer getKeySize() throws IOException;

    /**
     * The current session Id.
     *
     * @return The current SSL/TLS session ID
     *
     * @throws IOException If an error occurs trying to obtain the session ID
     */
    public String getSessionId() throws IOException;

    /**
     * @return the protocol String indicating how the SSL socket was created
     *  e.g. TLSv1 or TLSv1.2 etc.
     */
    public String getProtocol() throws IOException;
}

