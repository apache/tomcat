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

import java.util.Collections;
import java.util.List;

import javax.websocket.Decoder;
import javax.websocket.Encoder;
import javax.websocket.Extension;

public class ServerEndpointConfigurationBuilder {

    public static ServerEndpointConfigurationBuilder create(
            Class<?> endpointClass, String path) {
        return new ServerEndpointConfigurationBuilder(endpointClass, path);
    }


    private final Class<?> endpointClass;
    private final String path;
    private List<Encoder> encoders = Collections.EMPTY_LIST;
    private List<Decoder> decoders = Collections.EMPTY_LIST;
    private List<String> subprotocols = Collections.EMPTY_LIST;
    private List<Extension> extensions = Collections.EMPTY_LIST;
    private ServerEndpointConfigurator configurator =
            ServerEndpointConfigurator.getDefault();


    private ServerEndpointConfigurationBuilder(Class<?> endpointClass,
            String path) {
        this.endpointClass = endpointClass;
        this.path = path;
    }

    public ServerEndpointConfiguration build() {
        return new DefaultServerEndpointConfiguration(endpointClass, path,
                subprotocols, extensions, encoders, decoders, configurator);
    }


    public ServerEndpointConfigurationBuilder encoders(List<Encoder> encoders) {
        if (encoders == null || encoders.size() == 0) {
            this.encoders = Collections.EMPTY_LIST;
        } else {
            this.encoders = Collections.unmodifiableList(encoders);
        }
        return this;
    }


    public ServerEndpointConfigurationBuilder decoders(List<Decoder> decoders) {
        if (decoders == null || decoders.size() == 0) {
            this.decoders = Collections.EMPTY_LIST;
        } else {
            this.decoders = Collections.unmodifiableList(decoders);
        }
        return this;
    }


    public ServerEndpointConfigurationBuilder subprotocols(
            List<String> subprotocols) {
        if (subprotocols == null || subprotocols.size() == 0) {
            this.subprotocols = Collections.EMPTY_LIST;
        } else {
            this.subprotocols = Collections.unmodifiableList(subprotocols);
        }
        return this;
    }


    public ServerEndpointConfigurationBuilder extensions(
            List<Extension> extensions) {
        if (extensions == null || extensions.size() == 0) {
            this.extensions = Collections.EMPTY_LIST;
        } else {
            this.extensions = Collections.unmodifiableList(extensions);
        }
        return this;
    }


    public ServerEndpointConfigurationBuilder serverEndpointConfigurator(
            ServerEndpointConfigurator serverEndpointConfigurator) {
        if (serverEndpointConfigurator == null) {
            this.configurator = ServerEndpointConfigurator.getDefault();
        } else {
            this.configurator = serverEndpointConfigurator;
        }
        return this;
    }
}
