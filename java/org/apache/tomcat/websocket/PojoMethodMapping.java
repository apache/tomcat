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
package org.apache.tomcat.websocket;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketClose;
import javax.websocket.WebSocketError;
import javax.websocket.WebSocketMessage;
import javax.websocket.WebSocketOpen;
import javax.websocket.WebSocketPathParam;

/**
 * For a POJO class annotated with {@link javax.websocket.WebSocketEndpoint}, an
 * instance of this class caches the method and parameter information for the
 * onXXX calls.
 */
public class PojoMethodMapping {

    private final Method onOpen;
    private final Method onClose;
    private final Method onError;
    private final PathParam[] onOpenParams;
    private final PathParam[] onCloseParams;
    private final PathParam[] onErrorParams;
    private final Set<MessageMethod> onMessage = new HashSet<>();
    private final UriTemplate template;


    public PojoMethodMapping(Class<?> clazzPojo, String path, String mappingPath) {
        Method open = null;
        Method close = null;
        Method error = null;
        for (Method method : clazzPojo.getMethods()) {
            if (open == null &&
                    method.getAnnotation(WebSocketOpen.class) != null) {
                open = method;
            } else if (close == null &&
                    method.getAnnotation(WebSocketClose.class) != null) {
                close = method;
            } else if (error == null &&
                    method.getAnnotation(WebSocketError.class) != null) {
                error = method;
            } else if (method.getAnnotation(WebSocketMessage.class) != null) {
                onMessage.add(new MessageMethod(method));
            }
        }
        this.onOpen = open;
        this.onClose = close;
        this.onError = error;
        if (path.length() > mappingPath.length()) {
            template = new UriTemplate(path.substring(mappingPath.length() - 2));
        } else {
            template = null;
        }
        onOpenParams = getPathParams(onOpen, false);
        onCloseParams = getPathParams(onClose, false);
        onErrorParams = getPathParams(onError, true);
    }


    public Method getOnOpen() {
        return onOpen;
    }


    public Object[] getOnOpenArgs(String pathInfo, Session session) {
        return buildArgs(onOpenParams, template, pathInfo, session, null);
    }


    public Method getOnClose() {
        return onClose;
    }


    public Object[] getOnCloseArgs(String pathInfo, Session session) {
        return buildArgs(onCloseParams, template, pathInfo, session, null);
    }


    public Method getOnError() {
        return onError;
    }


    public Object[] getOnErrorArgs(String pathInfo, Session session,
            Throwable throwable) {
        return buildArgs(onErrorParams, template, pathInfo, session, throwable);
    }


    public Set<MessageHandler> getMessageHandlers(Object pojo, String pathInfo,
            Session session) {
        Set<MessageHandler> result = new HashSet<>();
        for (MessageMethod messageMethod : onMessage) {
            result.add(buildMessageHandler(messageMethod, pojo, pathInfo,
                    session));
        }
        return result;
    }


    private static MessageHandler buildMessageHandler(
            MessageMethod messageMethod, Object pojo, String pathInfo,
            Session session) {
        return null;
    }


    private static PathParam[] getPathParams(Method m, boolean isError) {
        if (m == null) {
            return new PathParam[0];
        }
        boolean foundError = !isError;
        Class<?>[] types = m.getParameterTypes();
        Annotation[][] paramsAnnotations = m.getParameterAnnotations();
        PathParam[] result = new PathParam[types.length];
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            if (type.equals(Session.class)) {
                result[i] = new PathParam(type, null);
            } else if (type.equals(Throwable.class)) {
                foundError = true;
                result[i] = new PathParam(type, null);
            } else {
                Annotation[] paramAnnotations = paramsAnnotations[i];
                for (Annotation paramAnnotation : paramAnnotations) {
                    if (paramAnnotation.annotationType().equals(
                            WebSocketPathParam.class)) {
                        result[i] = new PathParam(type,
                                ((WebSocketPathParam) paramAnnotation).value());
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
            throw new IllegalArgumentException();
        }
        return result;
    }


    private static Object[] buildArgs(PathParam[] pathParams,
            UriTemplate template, String pathInfo, Session session,
            Throwable throwable) {
        Object[] result = new Object[pathParams.length];
        Map<String,String> pathValues = template.match(pathInfo);
        for (int i = 0; i < pathParams.length; i++) {
            Class<?> type = pathParams[i].getType();
            if (type.equals(Session.class)) {
                result[i] = session;
            } else if (type.equals(Throwable.class)) {
                result[i] = throwable;
            } else {
                String name = pathParams[i].getName();
                String value = pathValues.get(name);
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


        public MessageMethod(Method m) {
            this.m = m;
        }


        public Method getMethod() {
            return m;
        }


        public Object[] getParameters() {
            return null;
        }
    }
}
