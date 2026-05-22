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
package jakarta.websocket;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;

/**
 * Provides configuration information for WebSocket client endpoints.
 */
public interface ClientEndpointConfig extends EndpointConfig {

    /**
     * Returns the list of sub-protocols that this client endpoint supports, in order of preference.
     *
     * @return The list of preferred sub-protocols
     */
    List<String> getPreferredSubprotocols();

    /**
     * Returns the list of extensions that this client endpoint supports.
     *
     * @return The list of supported extensions
     */
    List<Extension> getExtensions();

    /**
     * Returns the SSL context to use for secure WebSocket connections.
     *
     * @return The SSL context, or {@code null} if no custom SSL context is configured
     */
    SSLContext getSSLContext();

    /**
     * Returns the configurator used to customize the WebSocket handshake.
     *
     * @return The configurator for this client endpoint
     */
    Configurator getConfigurator();

    /**
     * Builder for creating {@link ClientEndpointConfig} instances.
     */
    final class Builder {

        private static final Configurator DEFAULT_CONFIGURATOR = new Configurator() {
        };


        /**
         * Creates a new {@link Builder} instance for building a {@link ClientEndpointConfig}.
         *
         * @return A new builder instance
         */
        public static Builder create() {
            return new Builder();
        }


        private Builder() {
            // Hide default constructor
        }

        private Configurator configurator = DEFAULT_CONFIGURATOR;
        private List<String> preferredSubprotocols = Collections.emptyList();
        private List<Extension> extensions = Collections.emptyList();
        private List<Class<? extends Encoder>> encoders = Collections.emptyList();
        private List<Class<? extends Decoder>> decoders = Collections.emptyList();
        private SSLContext sslContext = null;

        /**
         * Builds and returns a {@link ClientEndpointConfig} with the configured settings.
         *
         * @return The configured client endpoint configuration
         */
        public ClientEndpointConfig build() {
            return new DefaultClientEndpointConfig(preferredSubprotocols, extensions, encoders, decoders, sslContext,
                    configurator);
        }


        /**
         * Sets the configurator to use for the WebSocket handshake.
         *
         * @param configurator The configurator, or {@code null} to use the default
         * @return This builder instance
         */
        public Builder configurator(Configurator configurator) {
            if (configurator == null) {
                this.configurator = DEFAULT_CONFIGURATOR;
            } else {
                this.configurator = configurator;
            }
            return this;
        }


        /**
         * Sets the list of preferred sub-protocols for the WebSocket connection.
         *
         * @param preferredSubprotocols The list of sub-protocols in order of preference
         * @return This builder instance
         */
        public Builder preferredSubprotocols(List<String> preferredSubprotocols) {
            if (preferredSubprotocols == null || preferredSubprotocols.isEmpty()) {
                this.preferredSubprotocols = Collections.emptyList();
            } else {
                this.preferredSubprotocols = Collections.unmodifiableList(preferredSubprotocols);
            }
            return this;
        }


        /**
         * Sets the list of extensions to use for the WebSocket connection.
         *
         * @param extensions The list of extensions
         * @return This builder instance
         */
        public Builder extensions(List<Extension> extensions) {
            if (extensions == null || extensions.isEmpty()) {
                this.extensions = Collections.emptyList();
            } else {
                this.extensions = Collections.unmodifiableList(extensions);
            }
            return this;
        }


        /**
         * Sets the list of encoder classes to use for the WebSocket connection.
         *
         * @param encoders The list of encoder classes
         * @return This builder instance
         */
        public Builder encoders(List<Class<? extends Encoder>> encoders) {
            if (encoders == null || encoders.isEmpty()) {
                this.encoders = Collections.emptyList();
            } else {
                this.encoders = Collections.unmodifiableList(encoders);
            }
            return this;
        }


        /**
         * Sets the list of decoder classes to use for the WebSocket connection.
         *
         * @param decoders The list of decoder classes
         * @return This builder instance
         */
        public Builder decoders(List<Class<? extends Decoder>> decoders) {
            if (decoders == null || decoders.isEmpty()) {
                this.decoders = Collections.emptyList();
            } else {
                this.decoders = Collections.unmodifiableList(decoders);
            }
            return this;
        }


        /**
         * Sets the SSL context to use for secure WebSocket connections.
         *
         * @param sslContext The SSL context, or {@code null} for default SSL behavior
         * @return This builder instance
         */
        public Builder sslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }
    }


    /**
     * Provides a mechanism to customize the WebSocket handshake for client endpoints.
     * Applications may subclass this to inspect or modify the handshake.
     */
    class Configurator {

        /**
         * Creates a new instance of the default Configurator.
         */
        public Configurator() {
        }

        /**
         * Provides the client with a mechanism to inspect and/or modify the headers that are sent to the server to
         * start the WebSocket handshake.
         *
         * @param headers The HTTP headers
         */
        public void beforeRequest(Map<String,List<String>> headers) {
            // NO-OP
        }

        /**
         * Provides the client with a mechanism to inspect the handshake response that is returned from the server.
         *
         * @param handshakeResponse The response
         */
        public void afterResponse(HandshakeResponse handshakeResponse) {
            // NO-OP
        }
    }
}
