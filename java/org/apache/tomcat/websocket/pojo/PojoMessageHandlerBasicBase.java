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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import javax.websocket.MessageHandler;
import javax.websocket.Session;

/**
 * Common implementation code for the POJO basic message handlers. All the real
 * work is done in this class and in the superclass.
 *
 * @param <T>   The type of message to handle
 */
public abstract class PojoMessageHandlerBasicBase<T>
        extends PojoMessageHandlerBase<T> implements MessageHandler.Basic<T> {

    public PojoMessageHandlerBasicBase(Object pojo, Method method,
            Session session, Object[] params, int indexPayload,
            boolean unwrap, int indexSession) {
        super(pojo, method, session, params, indexPayload, unwrap,
                indexSession);
    }


    @Override
    public final void onMessage(T message) {
        Object[] parameters = params.clone();
        if (indexSession != -1) {
            parameters[indexSession] = session;
        }
        if (unwrap) {
            parameters[indexPayload] = ((ByteBuffer) message).array();
        } else {
            parameters[indexPayload] = message;
        }
        Object result;
        try {
            result = method.invoke(pojo, parameters);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalArgumentException();
        }
        processResult(result);
    }
}
