/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.websocket.pojo;

import java.io.InputStream;
import java.io.Reader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.websocket.CloseReason;
import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.Decoder.Binary;
import javax.websocket.Decoder.BinaryStream;
import javax.websocket.DeploymentException;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import javax.websocket.server.PathParam;

import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.Util;

/**
 * For a POJO class annotated with
 * {@link javax.websocket.server.ServerEndpoint}, an instance of this class
 * creates and caches the method handler, method information and parameter
 * information for the onXXX calls.
 */
public class PojoMethodMapping {

    private static final StringManager sm =
            StringManager.getManager(Constants.PACKAGE_NAME);

    private final Method onOpen;
    private final Method onClose;
    private final Method onError;
    private final PojoPathParam[] onOpenParams;
    private final PojoPathParam[] onCloseParams;
    private final PojoPathParam[] onErrorParams;
    private final Set<MessageMethod> onMessage = new HashSet<>();
    private final String wsPath;


    public PojoMethodMapping(Class<?> clazzPojo,
            Class<? extends Decoder>[] decoderClazzes, String wsPath)
                    throws DeploymentException {

        this.wsPath = wsPath;

        List<DecoderEntry> decoders = getDecoders(decoderClazzes);
        Method open = null;
        Method close = null;
        Method error = null;
        for (Method method : clazzPojo.getMethods()) {
            if (open == null &&
                    method.getAnnotation(OnOpen.class) != null) {
                open = method;
            } else if (close == null &&
                    method.getAnnotation(OnClose.class) != null) {
                close = method;
            } else if (error == null &&
                    method.getAnnotation(OnError.class) != null) {
                error = method;
            } else if (method.getAnnotation(OnMessage.class) != null) {
                onMessage.add(new MessageMethod(method, decoders));
            }
        }
        this.onOpen = open;
        this.onClose = close;
        this.onError = error;
        onOpenParams = getPathParams(onOpen, MethodType.ON_OPEN);
        onCloseParams = getPathParams(onClose, MethodType.ON_CLOSE);
        onErrorParams = getPathParams(onError, MethodType.ON_ERROR);
    }


    public String getWsPath() {
        return wsPath;
    }


    public Method getOnOpen() {
        return onOpen;
    }


    public Object[] getOnOpenArgs(Map<String,String> pathParameters,
            Session session) throws DecodeException {
        return buildArgs(onOpenParams, pathParameters, session, null, null);
    }


    public Method getOnClose() {
        return onClose;
    }


    public Object[] getOnCloseArgs(Map<String,String> pathParameters,
            Session session, CloseReason closeReason) throws DecodeException {
        return buildArgs(
                onCloseParams, pathParameters, session, null, closeReason);
    }


    public Method getOnError() {
        return onError;
    }


    public Object[] getOnErrorArgs(Map<String,String> pathParameters,
            Session session, Throwable throwable) throws DecodeException {
        return buildArgs(
                onErrorParams, pathParameters, session, throwable, null);
    }


    public Set<MessageHandler> getMessageHandlers(Object pojo,
            Map<String,String> pathParameters, Session session,
            EndpointConfig config) {
        Set<MessageHandler> result = new HashSet<>();
        for (MessageMethod messageMethod : onMessage) {
            result.add(messageMethod.getMessageHandler(pojo, pathParameters,
                    session, config));
        }
        return result;
    }


    private static List<DecoderEntry> getDecoders(
            Class<? extends Decoder>[] decoderClazzes)
                    throws DeploymentException{

        List<DecoderEntry> result = new ArrayList<>();
        for (Class<? extends Decoder> decoderClazz : decoderClazzes) {
            Decoder instance;
            try {
                instance = decoderClazz.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new DeploymentException(
                        sm.getString("wsRemoteEndpoint.invalidEncoder",
                                decoderClazz.getName()), e);
            }
            DecoderEntry entry = new DecoderEntry(
                    Util.getDecoderType(decoderClazz), instance);
            result.add(entry);
        }

        return result;
    }


    private static PojoPathParam[] getPathParams(Method m,
            MethodType methodType) {
        if (m == null) {
            return new PojoPathParam[0];
        }
        boolean foundThrowable = false;
        Class<?>[] types = m.getParameterTypes();
        Annotation[][] paramsAnnotations = m.getParameterAnnotations();
        PojoPathParam[] result = new PojoPathParam[types.length];
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            if (type.equals(Session.class)) {
                result[i] = new PojoPathParam(type, null);
            } else if (methodType == MethodType.ON_ERROR
                    && type.equals(Throwable.class)) {
                foundThrowable = true;
                result[i] = new PojoPathParam(type, null);
            } else if (methodType == MethodType.ON_CLOSE &&
                    type.equals(CloseReason.class)) {
                result[i] = new PojoPathParam(type, null);
            } else {
                Annotation[] paramAnnotations = paramsAnnotations[i];
                for (Annotation paramAnnotation : paramAnnotations) {
                    if (paramAnnotation.annotationType().equals(
                            PathParam.class)) {
                        result[i] = new PojoPathParam(type,
                                ((PathParam) paramAnnotation).value());
                        break;
                    }
                }
                // Parameters without annotations are not permitted
                if (result[i] == null) {
                    throw new IllegalArgumentException();
                }
            }
        }
        if (methodType == MethodType.ON_ERROR && !foundThrowable) {
            throw new IllegalArgumentException(sm.getString(
                    "pojoMethodMapping.onErrorNoThrowable",
                    m.getName(), m.getDeclaringClass().getName()));
        }
        return result;
    }


    private static Object[] buildArgs(PojoPathParam[] pathParams,
            Map<String,String> pathParameters, Session session,
            Throwable throwable, CloseReason closeReason)
            throws DecodeException {
        Object[] result = new Object[pathParams.length];
        for (int i = 0; i < pathParams.length; i++) {
            Class<?> type = pathParams[i].getType();
            if (type.equals(Session.class)) {
                result[i] = session;
            } else if (type.equals(Throwable.class)) {
                result[i] = throwable;
            } else if (type.equals(CloseReason.class)) {
                result[i] = closeReason;
            } else {
                String name = pathParams[i].getName();
                String value = pathParameters.get(name);
                try {
                    result[i] = Util.coerceToType(type, value);
                } catch (Exception e) {
                    throw new DecodeException(value, sm.getString(
                            "pojoMethodMapping.decodePathParamFail",
                            value, type), e);
                }
            }
        }
        return result;
    }


    private static class MessageMethod {

        private final Method m;
        private int indexString = -1;
        private int indexByteArray = -1;
        private int indexByteBuffer = -1;
        private int indexPong = -1;
        private int indexBoolean = -1;
        private int indexSession = -1;
        private int indexInputStream = -1;
        private int indexReader = -1;
        private int indexPrimitive = -1;
        private Map<Integer,PojoPathParam> indexPathParams = new HashMap<>();
        private int indexPayload = -1;


        public MessageMethod(Method m, List<DecoderEntry> decoderEntries) {
            this.m = m;

            Class<?>[] types = m.getParameterTypes();
            Annotation[][] paramsAnnotations = m.getParameterAnnotations();

            for (int i = 0; i < types.length; i++) {
                boolean paramFound = false;
                Annotation[] paramAnnotations = paramsAnnotations[i];
                for (Annotation paramAnnotation : paramAnnotations) {
                    if (paramAnnotation.annotationType().equals(
                            PathParam.class)) {
                        indexPathParams.put(
                                Integer.valueOf(i), new PojoPathParam(types[i],
                                        ((PathParam) paramAnnotation).value()));
                        paramFound = true;
                        break;
                    }
                }
                if (paramFound) {
                    continue;
                }
                if (String.class.isAssignableFrom(types[i])) {
                    if (indexString == -1) {
                        indexString = i;
                    } else {
                        throw new IllegalArgumentException(sm.getString(
                                "pojoMethodMapping.duplicateMessageParam",
                                m.getName(), m.getDeclaringClass().getName()));
                    }
                } else if (Reader.class.isAssignableFrom(types[i])) {
                    if (indexReader == -1) {
                        indexReader = i;
                    } else {
                        throw new IllegalArgumentException(sm.getString(
                                "pojoMethodMapping.duplicateMessageParam",
                                m.getName(), m.getDeclaringClass().getName()));
                    }
                } else if (boolean.class == types[i]) {
                    if (indexBoolean == -1) {
                        indexBoolean = i;
                    } else {
                        throw new IllegalArgumentException(sm.getString(
                                "pojoMethodMapping.duplicateLastParam",
                                m.getName(), m.getDeclaringClass().getName()));
                    }
                } else if (ByteBuffer.class.isAssignableFrom(types[i])) {
                    if (indexByteBuffer == -1) {
                        indexByteBuffer = i;
                    } else {
                        throw new IllegalArgumentException(sm.getString(
                                "pojoMethodMapping.duplicateMessageParam",
                                m.getName(), m.getDeclaringClass().getName()));
                    }
                } else if (byte[].class == types[i]) {
                    if (indexByteArray == -1) {
                        indexByteArray = i;
                    } else {
                        throw new IllegalArgumentException(sm.getString(
                                "pojoMethodMapping.duplicateMessageParam",
                                m.getName(), m.getDeclaringClass().getName()));
                    }
                } else if (InputStream.class.isAssignableFrom(types[i])) {
                    if (indexInputStream == -1) {
                        indexInputStream = i;
                    } else {
                        throw new IllegalArgumentException(sm.getString(
                                "pojoMethodMapping.duplicateMessageParam",
                                m.getName(), m.getDeclaringClass().getName()));
                    }
                } else if (Util.isPrimitive(types[i])) {
                    if (indexPrimitive == -1) {
                        indexPrimitive = i;
                    } else {
                        throw new IllegalArgumentException(sm.getString(
                                "pojoMethodMapping.duplicateMessageParam",
                                m.getName(), m.getDeclaringClass().getName()));
                    }
                } else if (Session.class.isAssignableFrom(types[i])) {
                    if (indexSession == -1) {
                        indexSession = i;
                    } else {
                        throw new IllegalArgumentException(sm.getString(
                                "pojoMethodMapping.duplicateSessionParam",
                                m.getName(), m.getDeclaringClass().getName()));
                    }
                } else if (PongMessage.class.isAssignableFrom(types[i])) {
                    if (indexPong == -1) {
                        indexPong = i;
                    } else {
                        throw new IllegalArgumentException(sm.getString(
                                "pojoMethodMapping.duplicatePongMessageParam",
                                m.getName(), m.getDeclaringClass().getName()));
                    }
                } else {
                    for (DecoderEntry decoderEntry : decoderEntries) {
                        if (decoderEntry.getClazz().isAssignableFrom(
                                types[i])) {
                            if (Binary.class.isAssignableFrom(
                                        decoderEntry.getDecoder().getClass()) ||
                                    BinaryStream.class.isAssignableFrom(
                                            decoderEntry.getDecoder().getClass())) {
                                if (indexByteBuffer == -1) {
                                    indexByteBuffer = i;
                                } else {
                                    throw new IllegalArgumentException(sm.getString(
                                            "pojoMethodMapping.duplicateMessageParam",
                                            m.getName(), m.getDeclaringClass().getName()));
                                }
                                break;
                            } else {
                                if (indexString == -1) {
                                    indexString = i;
                                } else {
                                    throw new IllegalArgumentException(sm.getString(
                                            "pojoMethodMapping.duplicateMessageParam",
                                            m.getName(), m.getDeclaringClass().getName()));
                                }
                            }
                        }
                    }
                }
            }
            // Additional checks required
            if (indexString != -1) {
                indexPayload = indexString;
            }
            if (indexReader != -1) {
                if (indexPayload != -1) {
                    throw new IllegalArgumentException(sm.getString(
                            "pojoMethodMapping.duplicateMessageParam",
                            m.getName(), m.getDeclaringClass().getName()));
                } else {
                    indexPayload = indexReader;
                }
            }
            if (indexByteArray != -1) {
                if (indexPayload != -1) {
                    throw new IllegalArgumentException(sm.getString(
                            "pojoMethodMapping.duplicateMessageParam",
                            m.getName(), m.getDeclaringClass().getName()));
                } else {
                    indexPayload = indexByteArray;
                }
            }
            if (indexByteBuffer != -1) {
                if (indexPayload != -1) {
                    throw new IllegalArgumentException(sm.getString(
                            "pojoMethodMapping.duplicateMessageParam",
                            m.getName(), m.getDeclaringClass().getName()));
                } else {
                    indexPayload = indexByteBuffer;
                }
            }
            if (indexInputStream != -1) {
                if (indexPayload != -1) {
                    throw new IllegalArgumentException(sm.getString(
                            "pojoMethodMapping.duplicateMessageParam",
                            m.getName(), m.getDeclaringClass().getName()));
                } else {
                    indexPayload = indexInputStream;
                }
            }
            if (indexPrimitive != -1) {
                if (indexPayload != -1) {
                    throw new IllegalArgumentException(sm.getString(
                            "pojoMethodMapping.duplicateMessageParam",
                            m.getName(), m.getDeclaringClass().getName()));
                } else {
                    indexPayload = indexPrimitive;
                }
            }
            if (indexPong != -1) {
                if (indexPayload != -1) {
                    throw new IllegalArgumentException(sm.getString(
                            "pojoMethodMapping.pongWithPayload",
                            m.getName(), m.getDeclaringClass().getName()));
                } else {
                    indexPayload = indexPong;
                }
            }
            if (indexPayload == -1 && indexPrimitive == -1 &&
                    indexBoolean != -1) {
                // The boolean we found is a payload, not a last flag
                indexPayload = indexBoolean;
                indexPrimitive = indexBoolean;
                indexBoolean = -1;
            }
            if (indexPayload == -1) {
                throw new IllegalArgumentException(sm.getString(
                        "pojoMethodMapping.noPayload",
                        m.getName(), m.getDeclaringClass().getName()));
            }
            if (indexPong != -1 && indexBoolean != -1) {
                throw new IllegalArgumentException(sm.getString(
                        "pojoMethodMapping.partialPong",
                        m.getName(), m.getDeclaringClass().getName()));
            }
            if(indexReader != -1 && indexBoolean != -1) {
                throw new IllegalArgumentException(sm.getString(
                        "pojoMethodMapping.partialReader",
                        m.getName(), m.getDeclaringClass().getName()));
            }
            if(indexInputStream != -1 && indexBoolean != -1) {
                throw new IllegalArgumentException(sm.getString(
                        "pojoMethodMapping.partialInputStream",
                        m.getName(), m.getDeclaringClass().getName()));
            }
        }


        public MessageHandler getMessageHandler(Object pojo,
                Map<String,String> pathParameters, Session session,
                EndpointConfig config) {
            Object[] params = new Object[m.getParameterTypes().length];

            for (Map.Entry<Integer,PojoPathParam> entry :
                    indexPathParams.entrySet()) {
                PojoPathParam pathParam = entry.getValue();
                String valueString = pathParameters.get(pathParam.getName());
                Object value = null;
                try {
                    value = Util.coerceToType(pathParam.getType(), valueString);
                } catch (Exception e) {
                    DecodeException de =  new DecodeException(valueString,
                            sm.getString(
                                    "pojoMethodMapping.decodePathParamFail",
                                    valueString, pathParam.getType()), e);
                    params = new Object[] { de };
                }
                params[entry.getKey().intValue()] = value;
            }

            MessageHandler mh = null;
            if (indexBoolean == -1) {
                // Basic
                if (indexString != -1) {
                    mh = new PojoMessageHandlerWholeText(pojo, m,  session,
                            config, params, indexString, false, indexSession);
                } else if (indexPrimitive != -1) {
                    mh = new PojoMessageHandlerWholeText(pojo, m, session,
                            config, params, indexPrimitive, false,
                            indexSession);
                } else if (indexByteArray != -1) {
                    mh = new PojoMessageHandlerWholeBinary(pojo, m, session,
                            config, params, indexByteArray, true, indexSession,
                            false);
                } else if (indexByteBuffer != -1) {
                    mh = new PojoMessageHandlerWholeBinary(pojo, m, session,
                            config, params, indexByteBuffer, false,
                            indexSession, false);
                } else if (indexInputStream != -1) {
                    mh = new PojoMessageHandlerWholeBinary(pojo, m, session,
                            config, params, indexInputStream, true, indexSession,
                            true);
                } else if (indexReader != -1) {
                    mh = new PojoMessageHandlerWholeText(pojo, m, session,
                            config, params, indexReader, true, indexSession);
                } else {
                    mh = new PojoMessageHandlerWholePong(pojo, m, session,
                            params, indexPong, false, indexSession);
                }
            } else {
                // ASync
                if (indexString != -1) {
                    mh = new PojoMessageHandlerPartialText(pojo, m, session,
                            params, indexString, false, indexBoolean,
                            indexSession);
                } else if (indexByteArray != -1) {
                    mh = new PojoMessageHandlerPartialBinary(pojo, m, session,
                            params, indexByteArray, true, indexBoolean,
                            indexSession);
                } else {
                    mh = new PojoMessageHandlerPartialBinary(pojo, m, session,
                            params, indexByteBuffer, false, indexBoolean,
                            indexSession);
                }
            }
            return mh;
        }
    }


    private static class DecoderEntry {

        private final Class<?> clazz;
        private final Decoder decoder;

        public DecoderEntry(Class<?> clazz, Decoder decoder) {
            this.clazz = clazz;
            this.decoder = decoder;
        }

        public Class<?> getClazz() {
            return clazz;
        }

        public Decoder getDecoder() {
            return decoder;
        }
    }


    private static enum MethodType {
        ON_OPEN,
        ON_CLOSE,
        ON_ERROR
    }
}
