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
package javax.websocket.server;

import java.net.URI;
import java.util.List;

import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;

public interface ServerEndpointConfiguration extends EndpointConfiguration {

    Class<? extends Endpoint> getEndpointClass();

    String getNegotiatedSubprotocol(List<String> requestedSubprotocols);

    List<Extension> getNegotiatedExtensions(List<Extension> requestedExtensions);

    /**
     * Enables the WebSocket endpoint to acceot or reject connections based on
     * the HTTP origin header.
     *
     * @param originHeaderValue The HTTP origin header provided by the client.
     *
     * @return  <code>true</code> if the request should be accepted otherwise
     *          <code>false</false>
     */
    boolean checkOrigin(String originHeaderValue);

    boolean matchesURI(URI uri);

    void modifyHandshake(HandshakeRequest request, HandshakeResponse response);

    /**
     * Returns the path at which this WebSocket server endpoint has been
     * registered. It may be a path or a level 0 URI template.
     */
    String getPath();
}
