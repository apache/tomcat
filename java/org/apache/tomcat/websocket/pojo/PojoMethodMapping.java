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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.websocket.MessageHandler;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import javax.websocket.server.PathParam;

/**
 * For a POJO class annotated with
 * {@link javax.websocket.server.WebSocketEndpoint}, an instance of this class
 * caches the method handlers and the method and parameter information for the
 * onXXX calls.
 */
public class PojoMethodMapping {

    private final Method onOpen;
    private final Method onClose;
    private final Method onError;
    private final PojoPathParam[] onOpenParams;
    private final PojoPathParam[] onCloseParams;
    private final PojoPathParam[] onErrorParams;
    private final Set<MessageMethod> onMessage = new HashSet<>();
    private final String wsPath;


    public PojoMethodMapping(Class<?> clazzPojo, String wsPath) {
        this.wsPath = wsPath;
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
                onMessage.add(new MessageMethod(method));
            }
        }
        this.onOpen = open;
        this.onClose = close;
        this.onError = error;
        onOpenParams = getPathParams(onOpen, false);
        onCloseParams = getPathParams(onClose, false);
        onErrorParams = getPathParams(onError, true);
    }


    public String getWsPath() {
        return wsPath;
    }


    public Method getOnOpen() {
        return onOpen;
    }


    public Object[] getOnOpenArgs(Map<String,String> pathParameters,
            Session session) {
        return buildArgs(onOpenParams, pathParameters, session, null);
    }


    public Method getOnClose() {
        return onClose;
    }


    public Object[] getOnCloseArgs(Map<String,String> pathParameters,
            Session session) {
        return buildArgs(onCloseParams, pathParameters, session, null);
    }


    public Method getOnError() {
        return onError;
    }


    public Object[] getOnErrorArgs(Map<String,String> pathParameters,
            Session session, Throwable throwable) {
        return buildArgs(onErrorParams, pathParameters, session, throwable);
    }


    public Set<MessageHandler> getMessageHandlers(Object pojo,
            Map<String,String> pathParameters, Session session) {
        Set<MessageHandler> result = new HashSet<>();
        for (MessageMethod messageMethod : onMessage) {
            result.add(messageMethod.getMessageHandler(pojo, pathParameters, session));
        }
        return result;
    }


    private static PojoPathParam[] getPathParams(Method m, boolean isError) {
        if (m == null) {
            return new PojoPathParam[0];
        }
        boolean foundError = !isError;
        Class<?>[] types = m.getParameterTypes();
        Annotation[][] paramsAnnotations = m.getParameterAnnotations();
        PojoPathParam[] result = new PojoPathParam[types.length];
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            if (type.equals(Session.class)) {
                result[i] = new PojoPathParam(type, null);
            } else if (type.equals(Throwable.class)) {
                foundError = true;
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
        if (!foundError) {
            // TODO i18n
            throw new IllegalArgumentException();
        }
        return result;
    }


    private static Object[] buildArgs(PojoPathParam[] pathParams,
            Map<String,String> pathParameters, Session session,
            Throwable throwable) {
        Object[] result = new Object[pathParams.length];
        for (int i = 0; i < pathParams.length; i++) {
            Class<?> type = pathParams[i].getType();
            if (type.equals(Session.class)) {
                result[i] = session;
            } else if (type.equals(Throwable.class)) {
                result[i] = throwable;
            } else {
                String name = pathParams[i].getName();
                String value = pathParameters.get(name);
                if (value == null) {
                    result[i] = null;
                } else {
                    result[i] = coerceToType(type, value);
                }
            }
        }
        return result;
    }


    private static Object coerceToType(Class<?> type, String value) {
        if (type.equals(String.class)) {
            return value;
        } else if (type.equals(boolean.class) || type.equals(Boolean.class)) {
            return Boolean.valueOf(value);
        } else if (type.equals(byte.class) || type.equals(Byte.class)) {
            return Byte.valueOf(value);
        } else if (value.length() == 1 &&
                (type.equals(char.class) || type.equals(Character.class))) {
            return Character.valueOf(value.charAt(0));
        } else if (type.equals(double.class) || type.equals(Double.class)) {
            return Double.valueOf(value);
        } else if (type.equals(float.class) || type.equals(Float.class)) {
            return Float.valueOf(value);
        } else if (type.equals(int.class) || type.equals(Integer.class)) {
            return Integer.valueOf(value);
        } else if (type.equals(long.class) || type.equals(Long.class)) {
            return Long.valueOf(value);
        } else if (type.equals(short.class) || type.equals(Short.class)) {
            return Short.valueOf(value);
        } else {
            // TODO
            throw new IllegalArgumentException();
        }
    }

    private static class MessageMethod {

        private final Method m;
        private int indexString = -1;
        private int indexByteArray = -1;
        private int indexByteBuffer = -1;
        private int indexPong = -1;
        private int indexBoolean = -1;
        private int indexSession = -1;
        private Map<Integer,PojoPathParam> indexPathParams = new HashMap<>();
        private int indexPayload = -1;


        public MessageMethod(Method m) {
            this.m = m;

            Class<?>[] types = m.getParameterTypes();
            Annotation[][] paramsAnnotations = m.getParameterAnnotations();

            for (int i = 0; i < types.length; i++) {
                if (types[i] == String.class) {
                    Annotation[] paramAnnotations = paramsAnnotations[i];
                    for (Annotation paramAnnotation : paramAnnotations) {
                        if (paramAnnotation.annotationType().equals(
                                PathParam.class)) {
                            indexPathParams.put(
                                    Integer.valueOf(i), new PojoPathParam(types[i],
                                            ((PathParam) paramAnnotation).value()));
                            break;
                        }
                    }
                    if (indexString == -1) {
                        indexString = i;
                    } else {
                        // TODO i18n
                        throw new IllegalArgumentException();
                    }
                } else if (types[i] == boolean.class) {
                    if (indexBoolean == -1) {
                        indexBoolean = i;
                    } else {
                        // TODO i18n
                        throw new IllegalArgumentException();
                    }
                } else if (types[i] == ByteBuffer.class) {
                    if (indexByteBuffer == -1) {
                        indexByteBuffer = i;
                    } else {
                        // TODO i18n
                        throw new IllegalArgumentException();
                    }
                } else if (types[i] == byte[].class) {
                    if (indexByteArray == -1) {
                        indexByteArray = i;
                    } else {
                        // TODO i18n
                        throw new IllegalArgumentException();
                    }
                } else if (types[i] == Session.class) {
                    if (indexSession == -1) {
                        indexSession = i;
                    } else {
                        // TODO i18n
                        throw new IllegalArgumentException();
                    }
                } else if (types[i] == PongMessage.class) {
                    if (indexPong == -1) {
                        indexPong = i;
                    } else {
                        // TODO i18n
                        throw new IllegalArgumentException();
                    }
                }
            }
            // Additional checks required
            if (indexString != -1) {
                indexPayload = indexString;
            }
            if (indexByteArray != -1) {
                if (indexPayload != -1) {
                    // TODO i18n
                    throw new IllegalArgumentException();
                } else {
                    indexPayload = indexByteArray;
                }
            }
            if (indexByteBuffer != -1) {
                if (indexPayload != -1) {
                    // TODO i18n
                    throw new IllegalArgumentException();
                } else {
                    indexPayload = indexByteBuffer;
                }
            }
            if (indexPong != -1) {
                if (indexPayload != -1) {
                    // TODO i18n
                    throw new IllegalArgumentException();
                } else {
                    indexPayload = indexPong;
                }
            }
            if (indexPayload == -1) {
                // TODO i18n
                throw new IllegalArgumentException();
            }
            if (indexPong != -1 && indexBoolean != -1) {
                // TODO i18n
                throw new IllegalArgumentException();
            }
        }


        public MessageHandler getMessageHandler(Object pojo,
                Map<String,String> pathParameters, Session session) {
            Object[] params = new Object[m.getParameterTypes().length];

            for (Map.Entry<Integer,PojoPathParam> entry :
                    indexPathParams.entrySet()) {
                PojoPathParam pathParam = entry.getValue();
                String valueString = pathParameters.get(pathParam.getName());
                Object value = null;
                if (valueString != null) {
                    value = coerceToType(pathParam.getType(), valueString);
                }
                params[entry.getKey().intValue()] = value;
            }

            MessageHandler mh = null;
            if (indexBoolean == -1) {
                // Basic
                if (indexString != -1) {
                    mh = new PojoMessageHandlerBasicString(pojo, m,  session,
                            params, indexString, false, indexSession);
                } else if (indexByteArray != -1) {
                    mh = new PojoMessageHandlerBasicBinary(pojo, m, session,
                            params, indexByteArray, true, indexSession);
                } else if (indexByteBuffer != -1) {
                    mh = new PojoMessageHandlerBasicBinary(pojo, m, session,
                            params, indexByteBuffer, false, indexSession);
                } else {
                    mh = new PojoMessageHandlerBasicPong(pojo, m, session,
                            params, indexPong, false, indexSession);
                }
            } else {
                // ASync
                if (indexString != -1) {
                    mh = new PojoMessageHandlerAsyncString(pojo, m, session,
                            params, indexString, false, indexBoolean,
                            indexSession);
                } else if (indexByteArray != -1) {
                    mh = new PojoMessageHandlerAsyncBinary(pojo, m, session,
                            params, indexByteArray, true, indexBoolean,
                            indexSession);
                } else {
                    mh = new PojoMessageHandlerAsyncBinary(pojo, m, session,
                            params, indexByteBuffer, false, indexBoolean,
                            indexSession);
                }
            }
            return mh;
        }
    }
}
