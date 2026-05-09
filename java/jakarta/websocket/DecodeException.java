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

import java.io.Serial;
import java.nio.ByteBuffer;

/**
 * Exception thrown when a decoder fails to decode a WebSocket message.
 */
public class DecodeException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The binary data that could not be decoded.
     */
    private ByteBuffer bb;
    /**
     * The text data that could not be decoded.
     */
    private String encodedString;

    /**
     * Creates a DecodeException for binary data with the specified detail message and cause.
     *
     * @param bb      The binary data that could not be decoded
     * @param message The detail message
     * @param cause   The underlying cause of the exception
     */
    public DecodeException(ByteBuffer bb, String message, Throwable cause) {
        super(message, cause);
        this.bb = bb;
    }

    /**
     * Creates a DecodeException for text data with the specified detail message and cause.
     *
     * @param encodedString The text data that could not be decoded
     * @param message       The detail message
     * @param cause         The underlying cause of the exception
     */
    public DecodeException(String encodedString, String message, Throwable cause) {
        super(message, cause);
        this.encodedString = encodedString;
    }

    /**
     * Creates a DecodeException for binary data with the specified detail message.
     *
     * @param bb      The binary data that could not be decoded
     * @param message The detail message
     */
    public DecodeException(ByteBuffer bb, String message) {
        super(message);
        this.bb = bb;
    }

    /**
     * Creates a DecodeException for text data with the specified detail message.
     *
     * @param encodedString The text data that could not be decoded
     * @param message       The detail message
     */
    public DecodeException(String encodedString, String message) {
        super(message);
        this.encodedString = encodedString;
    }

    /**
     * Returns the binary data that could not be decoded.
     *
     * @return The binary data, or {@code null} if the exception was for text data
     */
    public ByteBuffer getBytes() {
        return bb;
    }

    /**
     * Returns the text data that could not be decoded.
     *
     * @return The text data, or {@code null} if the exception was for binary data
     */
    public String getText() {
        return encodedString;
    }
}
