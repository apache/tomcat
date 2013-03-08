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

import javax.websocket.DecodeException;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.SessionException;

import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.WsSession;

/**
 * Common implementation code for the POJO whole message handlers. All the real
 * work is done in this class and in the superclass.
 *
 * @param <T>   The type of message to handle
 */
public abstract class PojoMessageHandlerWholeBase<T>
        extends PojoMessageHandlerBase<T> implements MessageHandler.Whole<T> {

    private static final StringManager sm =
            StringManager.getManager(Constants.PACKAGE_NAME);

    public PojoMessageHandlerWholeBase(Object pojo, Method method,
            Session session, Object[] params, int indexPayload,
            boolean unwrap, int indexSession) {
        super(pojo, method, session, params, indexPayload, unwrap,
                indexSession);
    }


    @Override
    public final void onMessage(T message) {

        // Can this message be decoded?
        Object payload;
        try {
            payload = decode(message);
        } catch (DecodeException de) {
            SessionException se = new SessionException(sm.getString(
                    "pojoMessageHandlerWhole.decodeFail"), de, session);
            ((WsSession) session).getLocal().onError(session, se);
            return;
        }

        if (payload == null) {
            // Not decoded. Unwrap if required. Unwrap only ever applies to
            // ByteBuffers
            if (unwrap) {
                ByteBuffer bb = (ByteBuffer) message;
                byte[] array = new byte[bb.remaining()];
                bb.get(array);
                payload = array;
            } else {
                payload = message;
            }
        }

        Object[] parameters = params.clone();
        if (indexSession != -1) {
            parameters[indexSession] = session;
        }
        parameters[indexPayload] = payload;

        Object result;
        try {
            result = method.invoke(pojo, parameters);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalArgumentException();
        }
        processResult(result);
    }


    protected abstract Object decode(T message) throws DecodeException;
    protected abstract void onClose();
}
