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

/**
 * Exception thrown when an error occurs on a WebSocket session.
 */
public class SessionException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The session on which the error occurred.
     */
    private final Session session;


    /**
     * Creates a SessionException with the specified detail message, cause, and session.
     *
     * @param message The detail message
     * @param cause   The underlying cause of the exception
     * @param session The session on which the error occurred
     */
    public SessionException(String message, Throwable cause, Session session) {
        super(message, cause);
        this.session = session;
    }


    /**
     * Returns the session on which the error occurred.
     *
     * @return The session
     */
    public Session getSession() {
        return session;
    }
}
