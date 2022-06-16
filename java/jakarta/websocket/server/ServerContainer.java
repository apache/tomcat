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

import java.io.IOException;
import java.util.Map;

import jakarta.websocket.DeploymentException;
import jakarta.websocket.WebSocketContainer;

/**
 * Provides the ability to deploy endpoints programmatically.
 */
public interface ServerContainer extends WebSocketContainer {
    public abstract void addEndpoint(Class<?> clazz) throws DeploymentException;

    public abstract void addEndpoint(ServerEndpointConfig sec) throws DeploymentException;

    /**
     * Upgrade the HTTP connection represented by the {@code HttpServletRequest} and {@code HttpServletResponse} to the
     * WebSocket protocol and establish a WebSocket connection as per the provided {@link ServerEndpointConfig}.
     * <p>
     * This method is primarily intended to be used by frameworks that implement the front-controller pattern. It does
     * not deploy the provided endpoint.
     * <p>
     * If the WebSocket implementation is not deployed as part of a Jakarta Servlet container, this method will throw an
     * {@link UnsupportedOperationException}.
     *
     * @param httpServletRequest    The {@code HttpServletRequest} to be processed as a WebSocket handshake as per
     *                              section 4.0 of RFC 6455.
     * @param httpServletResponse   The {@code HttpServletResponse} to be used when processing the
     *                              {@code httpServletRequest} as a WebSocket handshake as per section 4.0 of RFC 6455.
     * @param sec                   The server endpoint configuration to use to configure the WebSocket endpoint
     * @param pathParameters        Provides a mapping of path parameter names and values, if any, to be used for the
     *                              WebSocket connection established by the call to this method. If no such mapping is
     *                              defined, an empty Map must be passed.
     *
     * @throws IllegalStateException if the provided request does not meet the requirements of the WebSocket handshake
     * @throws UnsupportedOperationException if the WebSocket implementation is not deployed as part of a Jakarta
     *                                       Servlet container
     * @throws IOException if an I/O error occurs during the establishment of a WebSocket connection
     * @throws DeploymentException if a configuration error prevents the establishment of a WebSocket connection
     *
     * @since WebSocket 2.0
     */
    public void upgradeHttpToWebSocket(Object httpServletRequest, Object httpServletResponse, ServerEndpointConfig sec,
            Map<String,String> pathParameters) throws IOException, DeploymentException;
}
