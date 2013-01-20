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
package javax.websocket;

import java.net.URI;
import java.util.Set;

public interface WebSocketContainer {

    long getDefaultAsyncSendTimeout();

    void setAsyncSendTimeout(long timeout);

    Session connectToServer(Class<?> annotatedEndpointClass, URI path)
            throws DeploymentException;

    /**
     * Creates a new connection to the WebSocket.
     *
     * @param endpoint
     *            An instance of this class will be created to handle responses
     *            from the server
     * @param clientEndpointConfiguration
     *            Used to configure the new connection
     * @param path
     *            The full URL of the WebSocket endpoint to connect to
     *
     * @return The WebSocket session for the connection
     *
     * @throws DeploymentException  If the connection can not be established
     */
    Session connectToServer(Class<? extends Endpoint> endpoint,
            ClientEndpointConfiguration clientEndpointConfiguration, URI path)
            throws DeploymentException;

    Set<Session> getOpenSessions();

    long getMaxSessionIdleTimeout();

    void setMaxSessionIdleTimeout(long timeout);

    long getMaxBinaryMessageBufferSize();

    void setMaxBinaryMessageBufferSize(long max);

    long getMaxTextMessageBufferSize();

    void setMaxTextMessageBufferSize(long max);

    Set<Extension> getInstalledExtensions();
}
