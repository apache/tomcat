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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.websocket.CloseReason;
import javax.websocket.DefaultServerConfiguration;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.Session;
import javax.websocket.WebSocketClose;
import javax.websocket.WebSocketError;
import javax.websocket.WebSocketOpen;

public class WsEndpointPojo extends Endpoint {

    private final Object pojo;
    private final EndpointConfiguration config;
    private final Method onOpen;
    private final Method onClose;
    private final Method onError;

    public WsEndpointPojo(Class<?> clazzPojo, String path)
            throws InstantiationException, IllegalAccessException {
        this.pojo = clazzPojo.newInstance();
        this.config = new DefaultServerConfiguration(path) {

            @Override
            public boolean checkOrigin(String originHeaderValue) {
                return true;
            }

        };

        // TODO - Don't want to have to do this on every connection
        Method open = null;
        Method close = null;
        Method error = null;
        Method[] methods = clazzPojo.getMethods();
        for (int i = 0; i < methods.length; i++) {
            if (open == null &&
                    methods[i].getAnnotation(WebSocketOpen.class) != null) {
                open = methods[i];
            } else if (close == null &&
                    methods[i].getAnnotation(WebSocketClose.class) != null) {
                close = methods[i];
            } else if (error == null &&
                    methods[i].getAnnotation(WebSocketError.class) != null) {
                error = methods[i];
            }
        }
        this.onOpen = open;
        this.onClose = close;
        this.onError = error;
    }

    @Override
    public EndpointConfiguration getEndpointConfiguration() {
        return config;
    }

    @Override
    public void onOpen(Session session) {
        if (onOpen != null) {

            try {
                onOpen.invoke(pojo, session);
            } catch (IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onClose(CloseReason closeReason) {
        if (onClose != null) {
            try {
                onClose.invoke(pojo, (Object[]) null);
            } catch (IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onError(Throwable throwable) {
        if (onError != null) {
            try {
                onError.invoke(pojo, throwable);
            } catch (IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}
