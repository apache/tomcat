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
import java.util.ArrayList;
import java.util.List;

import javax.naming.NamingException;
import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.InstanceManager;
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

    private final Log log = LogFactory.getLog(PojoMessageHandlerWholeBase.class);  // must not be static
    private static final StringManager sm = StringManager.getManager(PojoMessageHandlerWholeBase.class);

    protected final List<Decoder> decoders = new ArrayList<>();

    public PojoMessageHandlerWholeBase(Object pojo, Method method,
            Session session, Object[] params, int indexPayload,
            boolean convert, int indexSession, long maxMessageSize) {
        super(pojo, method, session, params, indexPayload, convert,
                indexSession, maxMessageSize);
    }


    protected Decoder createDecoderInstance(Class<? extends Decoder> clazz)
            throws ReflectiveOperationException, NamingException {
        InstanceManager instanceManager = ((WsSession) session).getInstanceManager();
        if (instanceManager == null) {
            return clazz.getConstructor().newInstance();
        } else {
            return (Decoder) instanceManager.newInstance(clazz);
        }
    }


    @Override
    public final void onMessage(T message) {

        if (params.length == 1 && params[0] instanceof DecodeException) {
            ((WsSession) session).getLocal().onError(session,
                    (DecodeException) params[0]);
            return;
        }

        // Can this message be decoded?
        Object payload;
        try {
            payload = decode(message);
        } catch (DecodeException de) {
            ((WsSession) session).getLocal().onError(session, de);
            return;
        }

        if (payload == null) {
            // Not decoded. Convert if required.
            if (convert) {
                payload = convert(message);
            } else {
                payload = message;
            }
        }

        Object[] parameters = params.clone();
        if (indexSession != -1) {
            parameters[indexSession] = session;
        }
        parameters[indexPayload] = payload;

        Object result = null;
        try {
            result = method.invoke(pojo, parameters);
        } catch (IllegalAccessException | InvocationTargetException e) {
            handlePojoMethodException(e);
        }
        processResult(result);
    }


    protected void onClose() {
        InstanceManager instanceManager = ((WsSession) session).getInstanceManager();

        for (Decoder decoder : decoders) {
            decoder.destroy();
            if (instanceManager != null) {
                try {
                    instanceManager.destroyInstance(decoder);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    log.warn(sm.getString("pojoMessageHandlerWholeBase.decodeDestoryFailed", decoder.getClass()), e);
                }
            }
        }
    }


    protected Object convert(T message) {
        return message;
    }


    protected abstract Object decode(T message) throws DecodeException;
}
