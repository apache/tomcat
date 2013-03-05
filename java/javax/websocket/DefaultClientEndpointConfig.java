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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class DefaultClientEndpointConfig implements ClientEndpointConfig {

    private final List<String> preferredSubprotocols;
    private final List<Extension> extensions;
    private final List<Encoder> encoders;
    private final List<Decoder> decoders;
    private final Map<String,Object> userProperties = new HashMap<>();
    private final ClientEndpointConfigurator configurator;


    DefaultClientEndpointConfig(List<String> preferredSubprotocols,
            List<Extension> extensions, List<Encoder> encoders,
            List<Decoder> decoders, ClientEndpointConfigurator configurator) {
        this.preferredSubprotocols = preferredSubprotocols;
        this.extensions = extensions;
        this.decoders = decoders;
        this.encoders = encoders;
        this.configurator = configurator;
    }


    @Override
    public List<String> getPreferredSubprotocols() {
        return preferredSubprotocols;
    }


    @Override
    public List<Extension> getExtensions() {
        return extensions;
    }


    @Override
    public List<Encoder> getEncoders() {
        return encoders;
    }


    @Override
    public List<Decoder> getDecoders() {
        return decoders;
    }


    @Override
    public final Map<String, Object> getUserProperties() {
        return userProperties;
    }


    @Override
    public ClientEndpointConfigurator getClientEndpointConfigurator() {
        return configurator;
    }
}
