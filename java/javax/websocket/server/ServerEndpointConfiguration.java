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

import java.util.List;

import javax.websocket.EndpointConfiguration;
import javax.websocket.Extension;

/**
 * Provides configuration information for WebSocket endpoints published to a
 * server. Applications may provide their own implementation or use
 * {@link ServerEndpointConfigurationBuilder}.
 */
public interface ServerEndpointConfiguration extends EndpointConfiguration {

    Class<?> getEndpointClass();

    List<String> getSubprotocols();

    List<Extension> getExtensions();

    /**
     * Returns the path at which this WebSocket server endpoint has been
     * registered. It may be a path or a level 0 URI template.
     */
    String getPath();

    ServerEndpointConfigurator getServerEndpointConfigurator();
}
