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

import java.util.List;
import java.util.Map;

public abstract class ClientEndpointConfigurator {

    /**
     * Provides the client with a mechanism to inspect and/or modify the headers
     * that are sent to the server to start the WebSocket handshake.
     *
     * @param headers   The HTTP headers
     */
    public void beforeRequest(Map<String, List<String>> headers) {
        // NO-OP
    }

    /**
     * Provides the client with a mechanism to inspect the handshake response
     * that is returned from the server.
     *
     * @param handshakeResponse The response
     */
    public void afterResponse(HandshakeResponse handshakeResponse) {
        // NO-OP
    }
}
