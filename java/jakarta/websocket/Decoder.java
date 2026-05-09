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
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;

/**
 * Base interface for WebSocket message decoders. Decoders convert incoming WebSocket messages
 * from their raw form (text or binary) into application-specific objects.
 */
public interface Decoder {

    /**
     * Initialise the decoder. The default implementation is a NO-OP.
     *
     * @param endpointConfig The end-point configuration
     */
    default void init(EndpointConfig endpointConfig) {
    }

    /**
     * Destroy the decoder. The default implementation is a NO-OP.
     */
    default void destroy() {
    }

    /**
     * A decoder that decodes entire binary WebSocket messages into an object of type T.
     *
     * @param <T> The type of object produced by the decoder
     */
    interface Binary<T> extends Decoder {

        /**
         * Decodes the binary data into an object.
         *
         * @param bytes The binary data to decode
         * @return The decoded object
         * @throws DecodeException If the data cannot be decoded
         */
        T decode(ByteBuffer bytes) throws DecodeException;

        /**
         * Determines whether this decoder can decode the given binary data.
         *
         * @param bytes The binary data to check
         * @return {@code true} if this decoder can decode the data
         */
        boolean willDecode(ByteBuffer bytes);
    }

    /**
     * A decoder that decodes entire binary WebSocket messages from an input stream into an
     * object of type T.
     *
     * @param <T> The type of object produced by the decoder
     */
    interface BinaryStream<T> extends Decoder {

        /**
         * Decodes the binary data from the input stream into an object.
         *
         * @param is The input stream containing the binary data
         * @return The decoded object
         * @throws DecodeException If the data cannot be decoded
         * @throws IOException     If an I/O error occurs
         */
        T decode(InputStream is) throws DecodeException, IOException;
    }

    /**
     * A decoder that decodes entire text WebSocket messages into an object of type T.
     *
     * @param <T> The type of object produced by the decoder
     */
    interface Text<T> extends Decoder {

        /**
         * Decodes the text data into an object.
         *
         * @param s The text data to decode
         * @return The decoded object
         * @throws DecodeException If the data cannot be decoded
         */
        T decode(String s) throws DecodeException;

        /**
         * Determines whether this decoder can decode the given text data.
         *
         * @param s The text data to check
         * @return {@code true} if this decoder can decode the data
         */
        boolean willDecode(String s);
    }

    /**
     * A decoder that decodes entire text WebSocket messages from a reader into an object of
     * type T.
     *
     * @param <T> The type of object produced by the decoder
     */
    interface TextStream<T> extends Decoder {

        /**
         * Decodes the text data from the reader into an object.
         *
         * @param reader The reader containing the text data
         * @return The decoded object
         * @throws DecodeException If the data cannot be decoded
         * @throws IOException     If an I/O error occurs
         */
        T decode(Reader reader) throws DecodeException, IOException;
    }
}
