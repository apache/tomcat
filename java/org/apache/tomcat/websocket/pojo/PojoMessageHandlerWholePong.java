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

import jakarta.websocket.PongMessage;
import jakarta.websocket.Session;

/**
 * PongMessage specific concrete implementation for handling whole messages.
 */
public class PojoMessageHandlerWholePong extends PojoMessageHandlerWholeBase<PongMessage> {

    /**
     * Create a whole pong message handler.
     *
     * @param pojo          POJO instance
     * @param method        Method to invoke
     * @param session       WebSocket session
     * @param params        Pre-populated parameter array
     * @param indexPayload  Index of the payload parameter
     * @param convert       Convert the message before passing to the method
     * @param indexSession  Index of the session parameter
     */
    public PojoMessageHandlerWholePong(Object pojo, Method method, Session session, Object[] params, int indexPayload,
            boolean convert, int indexSession) {
        super(pojo, method, session, params, indexPayload, convert, indexSession, -1);
    }

    @Override
    protected Object decode(PongMessage message) {
        // Never decoded
        return null;
    }


    @Override
    protected void onClose() {
        // NO-OP
    }
}
