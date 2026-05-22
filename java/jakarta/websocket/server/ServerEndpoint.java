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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.websocket.Decoder;
import jakarta.websocket.Encoder;

/**
 * Annotates a class as a WebSocket server endpoint. The annotation provides configuration
 * information about the endpoint such as the URI path, subprotocols, encoders, decoders,
 * and configurator.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ServerEndpoint {

    /**
     * URI or URI-template that the annotated class should be mapped to.
     *
     * @return The URI or URI-template that the annotated class should be mapped to.
     */
    String value();

    /**
     * The sub-protocols that this server endpoint supports.
     *
     * @return The list of supported sub-protocols
     */
    String[] subprotocols() default {};

    /**
     * The decoder classes that this server endpoint uses.
     *
     * @return The list of decoder classes
     */
    Class<? extends Decoder>[] decoders() default {};

    /**
     * The encoder classes that this server endpoint uses.
     *
     * @return The list of encoder classes
     */
    Class<? extends Encoder>[] encoders() default {};

    /**
     * The configurator class used to customize the WebSocket handshake.
     *
     * @return The configurator class
     */
    Class<? extends ServerEndpointConfig.Configurator> configurator() default ServerEndpointConfig.Configurator.class;
}
