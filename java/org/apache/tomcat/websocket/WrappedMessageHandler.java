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
package org.apache.tomcat.websocket;

import jakarta.websocket.MessageHandler;

/**
 * Interface for message handlers that wrap another message handler.
 */
public interface WrappedMessageHandler {
    /**
     * Returns the maximum message size supported by this handler.
     *
     * @return the maximum message size
     */
    long getMaxMessageSize();

    /**
     * Returns the wrapped message handler, or {@code null} if the underlying POJO
     * is not a message handler.
     *
     * @return the wrapped message handler
     */
    MessageHandler getWrappedHandler();
}
