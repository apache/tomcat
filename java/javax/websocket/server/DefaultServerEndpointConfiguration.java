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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.websocket.Decoder;
import javax.websocket.Encoder;
import javax.websocket.Extension;

/**
 * Provides the default configuration for WebSocket server endpoints.
 */
public class DefaultServerEndpointConfiguration
        implements ServerEndpointConfiguration {

    private final Class<?> endpointClass;
    private final String path;
    private final List<String> subprotocols;
    private final List<Extension> extensions;
    private final List<Encoder> encoders;
    private final List<Decoder> decoders;
    private final ServerEndpointConfigurator serverEndpointConfigurator;
    private final Map<String,Object> userProperties = new HashMap<>();

    DefaultServerEndpointConfiguration(
            Class<?> endpointClass, String path,
            List<String> subprotocols, List<Extension> extensions,
            List<Encoder> encoders, List<Decoder> decoders,
            ServerEndpointConfigurator serverEndpointConfigurator) {
        this.endpointClass = endpointClass;
        this.path = path;
        this.subprotocols = subprotocols;
        this.extensions = extensions;
        this.encoders = encoders;
        this.decoders = decoders;
        this.serverEndpointConfigurator = serverEndpointConfigurator;
    }

    @Override
    public Class<?> getEndpointClass() {
        return endpointClass;
    }

    @Override
    public List<Encoder> getEncoders() {
        return this.encoders;
    }

    @Override
    public List<Decoder> getDecoders() {
        return this.decoders;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public ServerEndpointConfigurator getServerEndpointConfigurator() {
        return serverEndpointConfigurator;
    }

    @Override
    public final Map<String, Object> getUserProperties() {
        return userProperties;
    }

    @Override
    public List<String> getSubprotocols() {
        return subprotocols;
    }

    @Override
    public List<Extension> getExtensions() {
        return extensions;
    }
}
