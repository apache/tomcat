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
package org.apache.tomcat.websocket.pojo;

import java.lang.reflect.Method;

import jakarta.websocket.Session;

/**
 * Text specific concrete implementation for handling partial messages.
 */
public class PojoMessageHandlerPartialText extends PojoMessageHandlerPartialBase<String> {

    /**
     * Create a partial text message handler.
     *
     * @param pojo          POJO instance
     * @param method        Method to invoke
     * @param session       WebSocket session
     * @param params        Pre-populated parameter array
     * @param indexPayload  Index of the payload parameter
     * @param convert       Convert the message before passing to the method
     * @param indexBoolean  Index of the last flag parameter
     * @param indexSession  Index of the session parameter
     * @param maxMessageSize Maximum message size
     */
    public PojoMessageHandlerPartialText(Object pojo, Method method, Session session, Object[] params, int indexPayload,
            boolean convert, int indexBoolean, int indexSession, long maxMessageSize) {
        super(pojo, method, session, params, indexPayload, convert, indexBoolean, indexSession, maxMessageSize);
    }
}
