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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import jakarta.websocket.EncodeException;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.WrappedMessageHandler;
import org.apache.tomcat.websocket.WsSession;

/**
 * Common implementation code for the POJO message handlers.
 *
 * @param <T> The type of message to handle
 */
public abstract class PojoMessageHandlerBase<T> implements WrappedMessageHandler {

    private final Log log = LogFactory.getLog(PojoMessageHandlerBase.class); // must not be static
    private static final StringManager sm = StringManager.getManager(PojoMessageHandlerBase.class);

    protected final Object pojo;
    protected final Method method;
    protected final Session session;
    protected final Object[] params;
    protected final int indexPayload;
    protected final boolean convert;
    protected final int indexSession;
    protected final long maxMessageSize;

    public PojoMessageHandlerBase(Object pojo, Method method, Session session, Object[] params, int indexPayload,
            boolean convert, int indexSession, long maxMessageSize) {
        this.pojo = pojo;
        this.method = method;
        // TODO: The method should already be accessible here but the following
        // code seems to be necessary in some as yet not fully understood cases.
        try {
            this.method.setAccessible(true);
        } catch (Exception e) {
            // It is better to make sure the method is accessible, but
            // ignore exceptions and hope for the best
        }
        this.session = session;
        this.params = params;
        this.indexPayload = indexPayload;
        this.convert = convert;
        this.indexSession = indexSession;
        this.maxMessageSize = maxMessageSize;
    }


    protected final void processResult(Object result) {
        if (result == null) {
            return;
        }

        RemoteEndpoint.Basic remoteEndpoint = session.getBasicRemote();
        try {
            if (result instanceof String) {
                remoteEndpoint.sendText((String) result);
            } else if (result instanceof ByteBuffer) {
                remoteEndpoint.sendBinary((ByteBuffer) result);
            } else if (result instanceof byte[]) {
                remoteEndpoint.sendBinary(ByteBuffer.wrap((byte[]) result));
            } else {
                remoteEndpoint.sendObject(result);
            }
        } catch (IOException | EncodeException ioe) {
            throw new IllegalStateException(ioe);
        }
    }


    /**
     * Expose the POJO if it is a message handler so the Session is able to match requests to remove handlers if the
     * original handler has been wrapped.
     */
    @Override
    public final MessageHandler getWrappedHandler() {
        if (pojo instanceof MessageHandler) {
            return (MessageHandler) pojo;
        } else {
            return null;
        }
    }


    @Override
    public final long getMaxMessageSize() {
        return maxMessageSize;
    }


    protected final void handlePojoMethodInvocationTargetException(InvocationTargetException e) {
        /*
         * This is a failure during the execution of onMessage. This does not normally need to trigger the failure of
         * the WebSocket connection.
         */
        Throwable t = ExceptionUtils.unwrapInvocationTargetException(e);
        // Check for JVM wide issues
        ExceptionUtils.handleThrowable(t);
        // Log at debug level since this is an application issue and the application should be handling this.
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("pojoMessageHandlerBase.onMessafeFail", pojo.getClass().getName(), session.getId()),
                    t);
        }
        // Notify the application of the issue so it can handle it.
        ((WsSession) session).getLocal().onError(session, t);
    }
}
