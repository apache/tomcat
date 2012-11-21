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
import java.util.ArrayList;
import java.util.List;

public class DefaultServerConfiguration implements ServerEndpointConfiguration {
    private String path;
    @SuppressWarnings("unused") // TODO Remove this once implemented
    private List<String> subprotocols = new ArrayList<>();
    @SuppressWarnings("unused") // TODO Remove this once implemented
    private List<String> extensions = new ArrayList<>();
    private List<Encoder> encoders = new ArrayList<>();
    private List<Decoder> decoders = new ArrayList<>();

    protected DefaultServerConfiguration() {
    }

    public DefaultServerConfiguration(String path) {
        this.path = path;
    }

    public DefaultServerConfiguration setEncoders(List<Encoder> encoders) {
        this.encoders = encoders;
        return this;
    }

    public DefaultServerConfiguration setDecoders(List<Decoder> decoders) {
        this.decoders = decoders;
        return this;
    }

    public DefaultServerConfiguration setSubprotocols(
            List<String> subprotocols) {
        this.subprotocols = subprotocols;
        return this;
    }

    public DefaultServerConfiguration setExtensions(
            List<String> extensions) {
        this.extensions = extensions;
        return this;
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
    public List<String> getNegotiatedExtensions(
            List<String> requestedExtensions) {
        // TODO
        return null;
    }

    @Override
    public boolean checkOrigin(String originHeaderValue) {
        // TODO
        return false;
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
