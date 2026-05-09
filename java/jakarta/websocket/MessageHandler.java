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
 * Base interface for WebSocket message handlers. Use {@link Whole} for handling complete
 * messages and {@link Partial} for handling messages that may arrive in fragments.
 */
public interface MessageHandler {

    /**
     * A message handler that receives partial (fragmented) messages. The handler is called
     * multiple times for a single WebSocket message if it arrives in fragments.
     *
     * @param <T> The type of message data
     */
    interface Partial<T> extends MessageHandler {

        /**
         * Called when part of a message is available to be processed.
         *
         * @param messagePart The message part
         * @param last        <code>true</code> if this is the last part of this message, else <code>false</code>
         */
        void onMessage(T messagePart, boolean last);
    }

   /**
     * A message handler that receives whole (complete) messages. The handler is called once
     * per WebSocket message.
     *
     * @param <T> The type of message data
     */
    interface Whole<T> extends MessageHandler {

        /**
         * Called when a whole message is available to be processed.
         *
         * @param message The message
         */
        void onMessage(T message);
    }
}
