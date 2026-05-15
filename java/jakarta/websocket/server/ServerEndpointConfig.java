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
package jakarta.websocket.server;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

import jakarta.websocket.Decoder;
import jakarta.websocket.Encoder;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Extension;
import jakarta.websocket.HandshakeResponse;

/**
 * Provides configuration information for WebSocket endpoints published to a server. Applications may provide their own
 * implementation or use {@link Builder}.
 */
public interface ServerEndpointConfig extends EndpointConfig {

    /**
     * Returns the class of the endpoint.
     *
     * @return The endpoint class
     */
    Class<?> getEndpointClass();

    /**
     * Returns the path at which this WebSocket server endpoint has been registered. It may be a path or a level 0 URI
     * template.
     *
     * @return The registered path
     */
    String getPath();

    /**
     * Returns the list of sub-protocols supported by this endpoint.
     *
     * @return The list of supported sub-protocols
     */
    List<String> getSubprotocols();

    /**
     * Returns the list of extensions supported by this endpoint.
     *
     * @return The list of supported extensions
     */
    List<Extension> getExtensions();

    /**
     * Returns the configurator for this server endpoint.
     *
     * @return The configurator
     */
    Configurator getConfigurator();


    /**
     * Builder for creating {@link ServerEndpointConfig} instances.
     */
    final class Builder {

        /**
         * Creates a new builder for the given endpoint class and path.
         *
         * @param endpointClass The endpoint class
         * @param path          The path at which the endpoint will be registered
         * @return A new builder instance
         */
        public static Builder create(Class<?> endpointClass, String path) {
            return new Builder(endpointClass, path);
        }


        private final Class<?> endpointClass;
        private final String path;
        private List<Class<? extends Encoder>> encoders = Collections.emptyList();
        private List<Class<? extends Decoder>> decoders = Collections.emptyList();
        private List<String> subprotocols = Collections.emptyList();
        private List<Extension> extensions = Collections.emptyList();
        private Configurator configurator = Configurator.fetchContainerDefaultConfigurator();


        private Builder(Class<?> endpointClass, String path) {
            if (endpointClass == null) {
                throw new IllegalArgumentException("Endpoint class may not be null");
            }
            if (path == null) {
                throw new IllegalArgumentException("Path may not be null");
            }
            if (path.isEmpty()) {
                throw new IllegalArgumentException("Path may not be empty");
            }
            if (path.charAt(0) != '/') {
                throw new IllegalArgumentException("Path must start with '/'");
            }
            this.endpointClass = endpointClass;
            this.path = path;
        }

 /**
         * Builds and returns a {@link ServerEndpointConfig} with the configured settings.
         *
         * @return The configured server endpoint configuration
         */
        public ServerEndpointConfig build() {
            return new DefaultServerEndpointConfig(endpointClass, path, subprotocols, extensions, encoders, decoders,
                    configurator);
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
         * Sets the list of sub-protocols supported by the endpoint.
         *
         * @param subprotocols The list of sub-protocols
         * @return This builder instance
         */
        public Builder subprotocols(List<String> subprotocols) {
            if (subprotocols == null || subprotocols.isEmpty()) {
                this.subprotocols = Collections.emptyList();
            } else {
                this.subprotocols = Collections.unmodifiableList(subprotocols);
            }
            return this;
        }


        /**
         * Sets the list of extensions supported by the endpoint.
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
         * Sets the configurator to use for the WebSocket handshake.
         *
         * @param serverEndpointConfigurator The configurator, or {@code null} to use the default
         * @return This builder instance
         */
        public Builder configurator(Configurator serverEndpointConfigurator) {
            if (serverEndpointConfigurator == null) {
                this.configurator = Configurator.fetchContainerDefaultConfigurator();
            } else {
                this.configurator = serverEndpointConfigurator;
            }
            return this;
        }
    }


    /**
     * Provides a mechanism to customize the WebSocket handshake for server endpoints.
     * Applications may subclass this to inspect or modify the handshake.
     */
    class Configurator {

        /**
         * Creates a new instance of the Configurator.
         */
        public Configurator() {
        }

        private static volatile Configurator defaultImpl = null;
        private static final Object defaultImplLock = new Object();

        private static final String DEFAULT_IMPL_CLASSNAME =
                "org.apache.tomcat.websocket.server.DefaultServerEndpointConfigurator";

        static Configurator fetchContainerDefaultConfigurator() {
            if (defaultImpl == null) {
                synchronized (defaultImplLock) {
                    if (defaultImpl == null) {
                        defaultImpl = loadDefault();
                    }
                }
            }
            return defaultImpl;
        }


        private static Configurator loadDefault() {
            Configurator result = null;

            ServiceLoader<Configurator> serviceLoader = ServiceLoader.load(Configurator.class);

            Iterator<Configurator> iter = serviceLoader.iterator();
            while (result == null && iter.hasNext()) {
                result = iter.next();
            }

            // Fall-back. Also used by unit tests
            if (result == null) {
                try {
                    @SuppressWarnings("unchecked")
                    Class<Configurator> clazz = (Class<Configurator>) Class.forName(DEFAULT_IMPL_CLASSNAME);
                    result = clazz.getConstructor().newInstance();
                } catch (ReflectiveOperationException | IllegalArgumentException | SecurityException ignore) {
                    // No options left. Just return null.
                }
            }
            return result;
        }


        /**
         * Return the platform default configurator.
         *
         * @return the platform default configurator
         *
         * @since WebSocket 2.1
         */
        public ServerEndpointConfig.Configurator getContainerDefaultConfigurator() {
            return fetchContainerDefaultConfigurator();
        }

        /**
         * Determines the sub-protocol to use for the WebSocket connection by comparing the
         * supported and requested sub-protocols.
         *
         * @param supported The list of sub-protocols supported by the endpoint
         * @param requested The list of sub-protocols requested by the client
         * @return The negotiated sub-protocol, or {@code null} if none matches
         */
        public String getNegotiatedSubprotocol(List<String> supported, List<String> requested) {
            return fetchContainerDefaultConfigurator().getNegotiatedSubprotocol(supported, requested);
        }

        /**
         * Determines the extensions to use for the WebSocket connection by comparing the
         * installed and requested extensions.
         *
         * @param installed The list of extensions installed on the server
         * @param requested The list of extensions requested by the client
         * @return The list of negotiated extensions
         */
        public List<Extension> getNegotiatedExtensions(List<Extension> installed, List<Extension> requested) {
            return fetchContainerDefaultConfigurator().getNegotiatedExtensions(installed, requested);
        }

        /**
         * Checks whether the given Origin header value is acceptable. The default implementation
         * always returns {@code true}.
         *
         * @param originHeaderValue The Origin header value to check
         * @return {@code true} if the origin is acceptable
         */
        public boolean checkOrigin(String originHeaderValue) {
            return fetchContainerDefaultConfigurator().checkOrigin(originHeaderValue);
        }

        /**
         * Provides the opportunity to modify the handshake before it is completed. This method
         * is called after the sub-protocol and extensions have been negotiated.
         *
         * @param sec     The server endpoint configuration
         * @param request The handshake request
         * @param response The handshake response
         */
        public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
            fetchContainerDefaultConfigurator().modifyHandshake(sec, request, response);
        }

        /**
         * Creates a new instance of the endpoint class.
         *
         * @param <T>    The type of the endpoint class
         * @param clazz  The endpoint class
         * @return A new instance of the endpoint
         * @throws InstantiationException If the endpoint cannot be instantiated
         */
        public <T extends Object> T getEndpointInstance(Class<T> clazz) throws InstantiationException {
            return fetchContainerDefaultConfigurator().getEndpointInstance(clazz);
        }
    }
}
