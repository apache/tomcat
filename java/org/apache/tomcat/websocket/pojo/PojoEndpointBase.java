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

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * Base implementation (client and server have different concrete
 * implementations) of the wrapper that converts a POJO instance into a
 * WebSocket endpoint instance.
 */
public abstract class PojoEndpointBase extends Endpoint {

    private static final Log log = LogFactory.getLog(PojoEndpointBase.class);
    private static final StringManager sm =
            StringManager.getManager(Constants.PACKAGE_NAME);

    private Object pojo;
    private Map<String,String> pathParameters;
    private PojoMethodMapping methodMapping;


    protected final void doOnOpen(Session session, EndpointConfig config) {
        PojoMethodMapping methodMapping = getMethodMapping();
        Object pojo = getPojo();
        Map<String,String> pathParameters = getPathParameters();

        if (methodMapping.getOnOpen() != null) {
            try {
                methodMapping.getOnOpen().invoke(pojo,
                        methodMapping.getOnOpenArgs(pathParameters, session));
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalArgumentException(sm.getString(
                        "pojoEndpointBase.onOpenFail",
                        pojo.getClass().getName()), e);
            }
        }
        for (MessageHandler mh : methodMapping.getMessageHandlers(pojo,
                pathParameters, session, config)) {
            session.addMessageHandler(mh);
        }
    }


    @Override
    public final void onClose(Session session, CloseReason closeReason) {

        if (methodMapping.getOnClose() != null) {
            try {
                methodMapping.getOnClose().invoke(pojo,
                        methodMapping.getOnCloseArgs(pathParameters, session));
            } catch (IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException e) {
                log.error(sm.getString("pojoEndpointBase.onCloseFail",
                        pojo.getClass().getName()), e);
            }
        }

        // Trigger the destroy method for any associated decoders
        Set<MessageHandler> messageHandlers = session.getMessageHandlers();
        for (MessageHandler messageHandler : messageHandlers) {
            if (messageHandler instanceof PojoMessageHandlerWholeBase<?>) {
                ((PojoMessageHandlerWholeBase<?>) messageHandler).onClose();
            }
        }
    }


    @Override
    public final void onError(Session session, Throwable throwable) {

        if (methodMapping.getOnError() != null) {
            try {
                methodMapping.getOnError().invoke(
                        pojo,
                        methodMapping.getOnErrorArgs(pathParameters, session,
                                throwable));
            } catch (IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException e) {
                log.error(sm.getString("pojoEndpointBase.onErrorFail",
                        pojo.getClass().getName()), e);
            }
        }
    }

    protected Object getPojo() { return pojo; }
    protected void setPojo(Object pojo) { this.pojo = pojo; }


    protected Map<String,String> getPathParameters() { return pathParameters; }
    protected void setPathParameters(Map<String,String> pathParameters) {
        this.pathParameters = pathParameters;
    }


    protected PojoMethodMapping getMethodMapping() { return methodMapping; }
    protected void setMethodMapping(PojoMethodMapping methodMapping) {
        this.methodMapping = methodMapping;
    }
}
