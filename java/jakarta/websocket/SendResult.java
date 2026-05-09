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
 * Represents the result of an asynchronous WebSocket message send operation.
 */
public final class SendResult {
    private final Session session;
    private final Throwable exception;
    private final boolean ok;

    /**
     * Create an instance for an unsuccessful message.
     *
     * @param session   the WebSocket session in which the message was sent
     * @param exception The exception describing the failure when trying to send the message.
     */
    public SendResult(Session session, Throwable exception) {
        this.session = session;
        this.exception = exception;
        this.ok = (exception == null);
    }

    /**
     * Create an instance for a successful message.
     *
     * @param session the WebSocket session in which the message was sent
     */
    public SendResult(Session session) {
        this(session, null);
    }

    /**
     * Create an instance for an unsuccessful message.
     *
     * @param exception The exception describing the failure when trying to send the message.
     *
     * @deprecated Deprecated in WebSocket 2.2 and will be removed in a future version. Use
     *                 {@link #SendResult(Session, Throwable)} as a replacement.
     */
    @Deprecated
    public SendResult(Throwable exception) {
        this(null, exception);
    }

    /**
     * Create an instance for a successful message.
     *
     * @deprecated Deprecated in WebSocket 2.2 and will be removed in a future version. Use
     *                 {@link #SendResult(Session, Throwable)} as a replacement.
     */
    @Deprecated
    public SendResult() {
        this(null, null);
    }

    /**
     * Returns the exception that occurred during the send operation, or {@code null} if the
     * operation was successful.
     *
     * @return The exception, or {@code null} if successful
     */
    public Throwable getException() {
        return exception;
    }

    /**
     * Returns whether the send operation was successful.
     *
     * @return {@code true} if the send was successful, {@code false} otherwise
     */
    public boolean isOK() {
        return ok;
    }

    /**
     * The WebSocket session in which the session was sent.
     *
     * @return the WebSocket session in which the session was sent or {@code null} if not known.
     */
    public Session getSession() {
        return session;
    }
}
