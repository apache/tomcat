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

import java.util.ArrayList;
import java.util.List;

public class DefaultClientConfiguration implements ClientEndpointConfiguration {
    private List<String> preferredSubprotocols = new ArrayList<>();
    private List<String> extensions = new ArrayList<>();
    private List<Encoder> encoders = new ArrayList<>();
    private List<Decoder> decoders = new ArrayList<>();

    @Override
    public List<String> getPreferredSubprotocols() {
        return preferredSubprotocols;
    }

    public DefaultClientConfiguration setPreferredSubprotocols(
            List<String> preferredSubprotocols) {
        this.preferredSubprotocols = preferredSubprotocols;
        return this;
    }

    @Override
    public List<String> getExtensions() {
        return extensions;
    }

    public ClientEndpointConfiguration setExtensions(
            List<String> extensions) {
        this.extensions = extensions;
        return this;
    }

    @Override
    public List<Encoder> getEncoders() {
        return encoders;
    }

    public ClientEndpointConfiguration setEncoders(List<Encoder> encoders) {
        this.encoders = encoders;
        return this;
    }

    @Override
    public List<Decoder> getDecoders() {
        return decoders;
    }

    public ClientEndpointConfiguration setDecoders(List<Decoder> decoders) {
        this.decoders = decoders;
        return this;
    }
}
