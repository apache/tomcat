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

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;

/**
 * Base interface for WebSocket message encoders. Encoders convert application-specific objects
 * into WebSocket messages in text or binary form.
 */
public interface Encoder {

    /**
     * Initialise the encoder. The default implementation is a NO-OP.
     *
     * @param endpointConfig The end-point configuration
     */
    default void init(EndpointConfig endpointConfig) {
    }

    /**
     * Destroy the encoder. The default implementation is a NO-OP.
     */
    default void destroy() {
    }

    /**
     * An encoder that encodes an object of type T into a text WebSocket message.
     *
     * @param <T> The type of object accepted by the encoder
     */
    interface Text<T> extends Encoder {

        /**
         * Encodes the given object into a text string.
         *
         * @param object The object to encode
         * @return The encoded text string
         * @throws EncodeException If the object cannot be encoded
         */
        String encode(T object) throws EncodeException;
    }

    /**
     * An encoder that encodes an object of type T into a text WebSocket message written to
     * a {@link Writer}.
     *
     * @param <T> The type of object accepted by the encoder
     */
    interface TextStream<T> extends Encoder {

        /**
         * Encodes the given object and writes the result to the writer.
         *
         * @param object The object to encode
         * @param writer The writer to write the encoded data to
         * @throws EncodeException If the object cannot be encoded
         * @throws IOException     If an I/O error occurs
         */
        void encode(T object, Writer writer) throws EncodeException, IOException;
    }

    /**
     * An encoder that encodes an object of type T into a binary WebSocket message.
     *
     * @param <T> The type of object accepted by the encoder
     */
    interface Binary<T> extends Encoder {

        /**
         * Encodes the given object into a ByteBuffer.
         *
         * @param object The object to encode
         * @return The encoded binary data
         * @throws EncodeException If the object cannot be encoded
         */
        ByteBuffer encode(T object) throws EncodeException;
    }

    /**
     * An encoder that encodes an object of type T into a binary WebSocket message written to
     * an {@link OutputStream}.
     *
     * @param <T> The type of object accepted by the encoder
     */
    interface BinaryStream<T> extends Encoder {

        /**
         * Encodes the given object and writes the result to the output stream.
         *
         * @param object The object to encode
         * @param os     The output stream to write the encoded data to
         * @throws EncodeException If the object cannot be encoded
         * @throws IOException     If an I/O error occurs
         */
        void encode(T object, OutputStream os) throws EncodeException, IOException;
    }
}
