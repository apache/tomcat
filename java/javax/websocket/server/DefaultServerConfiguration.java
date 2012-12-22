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
import java.util.ArrayList;
import java.util.List;

import javax.websocket.Decoder;
import javax.websocket.Encoder;
import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;

public class DefaultServerConfiguration implements ServerEndpointConfiguration {

    private Class<? extends Endpoint> endpointClass;
    private String path;
    private List<String> subprotocols = new ArrayList<>();
    private List<Extension> extensions = new ArrayList<>();
    private List<Encoder> encoders = new ArrayList<>();
    private List<Decoder> decoders = new ArrayList<>();

    public DefaultServerConfiguration(Class<? extends Endpoint> endpointClass,
            String path) {
        this.endpointClass = endpointClass;
        this.path = path;
    }

    public DefaultServerConfiguration setEncoders(List<Encoder> encoders) {
        this.encoders.clear();
        this.encoders.addAll(encoders);
        return this;
    }

    public DefaultServerConfiguration setDecoders(List<Decoder> decoders) {
        this.decoders.clear();
        this.decoders.addAll(decoders);
        return this;
    }

    public DefaultServerConfiguration setSubprotocols(
            List<String> subprotocols) {
        this.subprotocols.clear();
        this.subprotocols.addAll(subprotocols);
        return this;
    }

    public DefaultServerConfiguration setExtensions(
            List<Extension> extensions) {
        this.extensions.clear();
        this.extensions.addAll(extensions);
        return this;
    }


    @Override
    public Class<? extends Endpoint> getEndpointClass() {
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
    public String getNegotiatedSubprotocol(List<String> requestedSubprotocols) {
        // TODO
        return null;
    }

    @Override
    public List<Extension> getNegotiatedExtensions(
            List<Extension> requestedExtensions) {
        // TODO
        return null;
    }

    @Override
    public boolean checkOrigin(String originHeaderValue) {
        return true;
    }

    @Override
    public boolean matchesURI(URI uri) {
        // TODO
        return false;
    }

    @Override
    public void modifyHandshake(HandshakeRequest request,
            HandshakeResponse response) {
        // TODO
    }
}
