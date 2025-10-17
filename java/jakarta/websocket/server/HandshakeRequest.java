/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jakarta.websocket.server;

import java.net.URI;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Represents the HTTP request that asked to be upgraded to WebSocket.
 */
public interface HandshakeRequest {

    String SEC_WEBSOCKET_KEY = "Sec-WebSocket-Key";
    String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";
    String SEC_WEBSOCKET_VERSION = "Sec-WebSocket-Version";
    String SEC_WEBSOCKET_EXTENSIONS = "Sec-WebSocket-Extensions";

    Map<String,List<String>> getHeaders();

    Principal getUserPrincipal();

    URI getRequestURI();

    boolean isUserInRole(String role);

    /**
     * Get the HTTP Session object associated with this request. Object is used to avoid a direct dependency on the
     * Servlet API.
     *
     * @return The jakarta.servlet.http.HttpSession object associated with this request, if any.
     */
    Object getHttpSession();

    Map<String,List<String>> getParameterMap();

    String getQueryString();

    /**
     * Returns the client certificate chain associated with this request, if any. The array is ordered in ascending
     * order of trust. The first certificate in the array is the one that identifies the client. The next certificate is
     * is for the certificate authority that issued the first. And so on to the root certificate authority.
     *
     * @return An ordered array of client certificates, with the client's own certificate first followed by any
     *             certificate authorities or {@code null} if the client did not present a certificate.
     *
     * @since WebSocket 2.3
     */
    X509Certificate[] getUserX509CertificateChain();

    /**
     * Returns the address of the interface on which the WebSocket handshake request was received. The representation is
     * determined by the underlying connection features of the WebSocket implementation. It is not safe to assume that
     * it will always be an IP address (either IPv4 or IPv6). It could be some other connection representation such as a
     * Unix Socket.
     *
     * @return the address of the interface on which the WebSocket handshake request was received
     *
     * @since WebSocket 2.3
     */
    String getLocalAddress();

    /**
     * Returns the host name associated with the interface on which the WebSocket handshake request was received.
     *
     * @return the host name associated with the interface on which the WebSocket handshake request was received.
     *
     * @since WebSocket 2.3
     */
    String getLocalHostName();

    /**
     * Returns the Internet Protocol (IP) port number of the interface on which the WebSocket handshake request was
     * received. If the request was not received via an IP connection, -1 will be returned.
     *
     * @return the Internet Protocol (IP) port number of the interface on which the WebSocket handshake request was
     *             received or -1 if not applicable
     *
     * @since WebSocket 2.3
     */
    int getLocalPort();

    /**
     * Returns the address of the interface of the client or last proxy which sent the WebSocket handshake request. The
     * representation is determined by the underlying connection features of the WebSocket implementation. It is not
     * safe to assume that it will always be an IP address (either IPv4 or IPv6). It could be some other connection
     * representation such as a Unix Socket.
     *
     * @return the address of the interface of the client or last proxy which sent the WebSocket handshake request
     *
     * @since WebSocket 2.3
     */
    String getRemoteAddress();

    /**
     * Returns the host name associated with the client or last proxy which sent the WebSocket handshake request.
     *
     * @return the host name associated with the client or last proxy which sent the WebSocket handshake request.
     *
     * @since WebSocket 2.3
     */
    String getRemoteHostName();

    /**
     * Returns the Internet Protocol (IP) port number of the interface of the client or last proxy which sent the
     * WebSocket handshake request. If the request was not sent via an IP connection, -1 will be returned.
     *
     * @return the Internet Protocol (IP) port number of the interface of the client or last proxy which sent the
     *             WebSocket handshake request or -1 if not applicable
     *
     * @since WebSocket 2.3
     */
    int getRemotePort();

    /**
     * Returns the preferred <code>Locale</code> that the client will accept content in, based on the Accept-Language
     * header. If the WebSocket handshake request doesn't provide an Accept-Language header, this method returns the
     * default locale for the server.
     *
     * @return the preferred <code>Locale</code> for the client
     *
     * @since WebSocket 2.3
     */
    Locale getPreferredLocale();
}
