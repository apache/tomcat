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

/**
 * Represents the reason for the closure of a WebSocket connection, including a close code
 * and an optional reason phrase.
 */
public class CloseReason {

    private final CloseCode closeCode;
    private final String reasonPhrase;

    /**
     * Creates a new CloseReason with the given close code and reason phrase.
     *
     * @param closeCode    The close code
     * @param reasonPhrase The reason phrase explaining the closure
     */
    public CloseReason(CloseReason.CloseCode closeCode, String reasonPhrase) {
        this.closeCode = closeCode;
        this.reasonPhrase = reasonPhrase;
    }

    /**
     * Returns the close code.
     *
     * @return The close code
     */
    public CloseCode getCloseCode() {
        return closeCode;
    }

    /**
     * Returns the reason phrase.
     *
     * @return The reason phrase
     */
    public String getReasonPhrase() {
        return reasonPhrase;
    }

    @Override
    public String toString() {
        return "CloseReason: code [" + closeCode.getCode() + "], reason [" + reasonPhrase + "]";
    }

    /**
     * Represents a WebSocket close code.
     */
    public interface CloseCode {
        /**
         * Returns the numeric value of the close code.
         *
         * @return The numeric close code value
         */
        int getCode();
    }

    /**
     * Standard WebSocket close codes as defined by RFC 6455 and related specifications.
     */
    public enum CloseCodes implements CloseReason.CloseCode {

        /**
         * Indicates a normal closure, meaning that the purpose for which the connection was
         * established has been fulfilled.
         */
        NORMAL_CLOSURE(1000),
        /**
         * Indicates that the endpoint is going away, either because the browser is navigating
         * away from the page, or because the server is shutting down.
         */
        GOING_AWAY(1001),
        /**
         * Indicates that the endpoint is terminating the connection due to a protocol error.
         */
        PROTOCOL_ERROR(1002),
        /**
         * Indicates that the endpoint is terminating the connection because it has received a
         * type of data it cannot accept.
         */
        CANNOT_ACCEPT(1003),
        /**
         * Reserved. The specific meaning might be defined in the future.
         */
        RESERVED(1004),
        /**
         * Indicates that no status code was included in the closing frame. This value is
         * reserved and must not be set as a status code in a Close control frame.
         */
        NO_STATUS_CODE(1005),
        /**
         * Indicates that the connection was closed abnormally without receiving a close frame.
         * This value is reserved and must not be set as a status code in a Close control frame.
         */
        CLOSED_ABNORMALLY(1006),
        /**
         * Indicates that the endpoint is terminating the connection because it has received a
         * message that contains data that is not consistent with the type of the message.
         */
        NOT_CONSISTENT(1007),
        /**
         * Indicates that the endpoint is terminating the connection because it has received a
         * message that violates its policy.
         */
        VIOLATED_POLICY(1008),
        /**
         * Indicates that the endpoint is terminating the connection because it has received a
         * message that is too big to process.
         */
        TOO_BIG(1009),
        /**
         * Indicates that the client is terminating the connection because it expected the
         * server to negotiate one or more extension, but the server didn't return them in the
         * handshake response.
         */
        NO_EXTENSION(1010),
        /**
         * Indicates that the server is terminating the connection because it encountered an
         * unexpected condition that prevented it from fulfilling the request.
         */
        UNEXPECTED_CONDITION(1011),
        /**
         * Indicates that the server is restarting. A client may choose to reconnect.
         */
        SERVICE_RESTART(1012),
        /**
         * Indicates that the server is experiencing a temporary condition and the client should
         * try to connect again later.
         */
        TRY_AGAIN_LATER(1013),
        /**
         * Indicates that the connection was closed due to a failure to perform a TLS handshake.
         * This value is reserved and must not be set as a status code in a Close control frame.
         */
        TLS_HANDSHAKE_FAILURE(1015);

        private final int code;

        /**
         * Creates a close code with the given numeric value.
         *
         * @param code The numeric close code value
         */
        CloseCodes(int code) {
            this.code = code;
        }

        /**
         * Returns the {@link CloseCode} for the given numeric code value.
         *
         * @param code The numeric close code value
         * @return The corresponding {@link CloseCode}
         * @throws IllegalArgumentException if the code is not a valid close code
         */
        public static CloseCode getCloseCode(final int code) {
            if (code > 2999 && code < 5000) {
                return () -> code;
            }
            return switch (code) {
                case 1000 -> NORMAL_CLOSURE;
                case 1001 -> GOING_AWAY;
                case 1002 -> PROTOCOL_ERROR;
                case 1003 -> CANNOT_ACCEPT;
                case 1004 -> RESERVED;
                case 1005 -> NO_STATUS_CODE;
                case 1006 -> CLOSED_ABNORMALLY;
                case 1007 -> NOT_CONSISTENT;
                case 1008 -> VIOLATED_POLICY;
                case 1009 -> TOO_BIG;
                case 1010 -> NO_EXTENSION;
                case 1011 -> UNEXPECTED_CONDITION;
                case 1012 -> SERVICE_RESTART;
                case 1013 -> TRY_AGAIN_LATER;
                case 1015 -> TLS_HANDSHAKE_FAILURE;
                default -> throw new IllegalArgumentException("Invalid close code: [" + code + "]");
            };
        }

        /**
         * Returns the numeric value of this close code.
         *
         * @return The numeric close code value
         */
        @Override
        public int getCode() {
            return code;
        }
    }
}
