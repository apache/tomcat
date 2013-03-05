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

import java.util.Collections;
import java.util.List;

public class ClientEndpointConfigurationBuilder {

    private static final ClientEndpointConfigurator DEFAULT_CONFIGURATOR =
            new ClientEndpointConfigurator() {};


    public static ClientEndpointConfigurationBuilder create() {
        return new ClientEndpointConfigurationBuilder();
    }


    private ClientEndpointConfigurator configurator = DEFAULT_CONFIGURATOR;
    private List<String> preferredSubprotocols = Collections.EMPTY_LIST;
    private List<Extension> extensions = Collections.EMPTY_LIST;
    private List<Encoder> encoders = Collections.EMPTY_LIST;
    private List<Decoder> decoders = Collections.EMPTY_LIST;


    public ClientEndpointConfig build() {
        return new DefaultClientEndpointConfig(preferredSubprotocols,
                extensions, encoders, decoders, configurator);
    }


    public ClientEndpointConfigurationBuilder clientHandshakeConfigurator(
            ClientEndpointConfigurator clientEndpointConfigurator) {
        if (clientEndpointConfigurator == null) {
            configurator = DEFAULT_CONFIGURATOR;
        } else {
            configurator = clientEndpointConfigurator;
        }
        return this;
    }


    public ClientEndpointConfigurationBuilder preferredSubprotocols(
            List<String> preferredSubprotocols) {
        if (preferredSubprotocols == null ||
                preferredSubprotocols.size() == 0) {
            this.preferredSubprotocols = Collections.EMPTY_LIST;
        } else {
            this.preferredSubprotocols =
                    Collections.unmodifiableList(preferredSubprotocols);
        }
        return this;
    }


    public ClientEndpointConfigurationBuilder extensions(
            List<Extension> extensions) {
        if (extensions == null || extensions.size() == 0) {
            this.extensions = Collections.EMPTY_LIST;
        } else {
            this.extensions = Collections.unmodifiableList(extensions);
        }
        return this;
    }


    public ClientEndpointConfigurationBuilder encoders(List<Encoder> encoders) {
        if (encoders == null || encoders.size() == 0) {
            this.encoders = Collections.EMPTY_LIST;
        } else {
            this.encoders = Collections.unmodifiableList(encoders);
        }
        return this;
    }


    public ClientEndpointConfigurationBuilder decoders(List<Decoder> decoders) {
        if (decoders == null || decoders.size() == 0) {
            this.decoders = Collections.EMPTY_LIST;
        } else {
            this.decoders = Collections.unmodifiableList(decoders);
        }
        return this;
    }
}
