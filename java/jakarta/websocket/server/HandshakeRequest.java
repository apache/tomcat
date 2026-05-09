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
import java.util.List;
import java.util.Map;

/**
 * Represents the HTTP request that asked to be upgraded to WebSocket.
 */
public interface HandshakeRequest {

    /**
     * Name of the Sec-WebSocket-Key HTTP header.
     */
    String SEC_WEBSOCKET_KEY = "Sec-WebSocket-Key";
    /**
     * Name of the Sec-WebSocket-Protocol HTTP header.
     */
    String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";
    /**
     * Name of the Sec-WebSocket-Version HTTP header.
     */
    String SEC_WEBSOCKET_VERSION = "Sec-WebSocket-Version";
    /**
     * Name of the Sec-WebSocket-Extensions HTTP header.
     */
    String SEC_WEBSOCKET_EXTENSIONS = "Sec-WebSocket-Extensions";

    /**
     * Returns the HTTP headers from the handshake request.
     *
     * @return The HTTP headers
     */
    Map<String,List<String>> getHeaders();

    /**
     * Returns the principal of the user associated with this request, or {@code null} if the
     * user is not authenticated.
     *
     * @return The user principal
     */
    Principal getUserPrincipal();

    /**
     * Returns the URI of the handshake request.
     *
     * @return The request URI
     */
    URI getRequestURI();

    /**
     * Returns whether the user associated with this request is in the given role.
     *
     * @param role The role to check
     * @return {@code true} if the user is in the specified role
     */
    boolean isUserInRole(String role);

    /**
     * Get the HTTP Session object associated with this request. Object is used to avoid a direct dependency on the
     * Servlet API.
     *
     * @return The jakarta.servlet.http.HttpSession object associated with this request, if any.
     */
    Object getHttpSession();

    /**
     * Returns the query parameters from the handshake request.
     *
     * @return The query parameter map
     */
    Map<String,List<String>> getParameterMap();

    /**
     * Returns the query string from the handshake request.
     *
     * @return The query string
     */
    String getQueryString();
}
