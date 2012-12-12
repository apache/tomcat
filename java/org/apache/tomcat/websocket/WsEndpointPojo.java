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

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

/**
 * Wrapper class for instances of POJOs annotated with
 * {@link javax.websocket.WebSocketEndpoint} so they appear as standard
 * {@link Endpoint} instances.
 */
public class WsEndpointPojo extends Endpoint {

    private final Object pojo;
    private final String pathInfo;
    private final PojoMethodMapping methodMapping;
    private Session session = null;


    public WsEndpointPojo(Class<?> clazzPojo, PojoMethodMapping methodMapping,
            String pathInfo) throws InstantiationException,
            IllegalAccessException {
        // TODO Use factory from annotation if present
        this.pojo = clazzPojo.newInstance();
        this.methodMapping = methodMapping;
        this.pathInfo = pathInfo;
    }


    @Override
    public void onOpen(Session session) {
        this.session = session;
        if (methodMapping.getOnOpen() != null) {
            try {
                methodMapping.getOnOpen().invoke(pojo,
                        methodMapping.getOnOpenArgs(pathInfo, session));
            } catch (IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        for (MessageHandler mh : methodMapping.getMessageHandlers(pojo,
                pathInfo, session)) {
            session.addMessageHandler(mh);
        }
    }


    @Override
    public void onClose(CloseReason closeReason) {
        if (methodMapping.getOnClose() != null) {
            try {
                methodMapping.getOnClose().invoke(pojo,
                        methodMapping.getOnCloseArgs(pathInfo, session));
            } catch (IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }


    @Override
    public void onError(Throwable throwable) {
        if (methodMapping.getOnError() != null) {
            try {
                methodMapping.getOnError().invoke(
                        pojo,
                        methodMapping.getOnErrorArgs(pathInfo, session,
                                throwable));
            } catch (IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}
