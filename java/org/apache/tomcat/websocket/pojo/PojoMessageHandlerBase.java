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
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import javax.websocket.EncodeException;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;

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

    /**
     * The POJO instance that contains the message handler method.
     */
    protected final Object pojo;

    /**
     * The method to invoke when a message is received.
     */
    protected final Method method;

    /**
     * The WebSocket session associated with this handler.
     */
    protected final Session session;

    /**
     * The parameter array used for method invocation.
     */
    protected final Object[] params;

    /**
     * The index in the params array where the payload is stored.
     */
    protected final int indexPayload;

    /**
     * Whether the message payload should be converted to the method parameter type.
     */
    protected final boolean convert;

    /**
     * The index in the params array where the session is stored.
     */
    protected final int indexSession;

    /**
     * The maximum message size supported by this handler.
     */
    protected final long maxMessageSize;

    /**
     * Constructs a new PojoMessageHandlerBase.
     *
     * @param pojo the POJO instance containing the message handler method
     * @param method the method to invoke when a message is received
     * @param session the WebSocket session
     * @param params the parameter array for method invocation
     * @param indexPayload the index in the params array for the payload
     * @param convert whether to convert the payload to the method parameter type
     * @param indexSession the index in the params array for the session
     * @param maxMessageSize the maximum message size
     */
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


    /**
     * Processes the result of a POJO message handler method invocation by sending it back
     * to the remote endpoint.
     *
     * @param result the result object to send
     */
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


    protected final void handlePojoMethodException(Throwable t) {
        t = ExceptionUtils.unwrapInvocationTargetException(t);
        ExceptionUtils.handleThrowable(t);
        if (t instanceof EncodeException) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("pojoMessageHandlerBase.encodeFail", pojo.getClass().getName(), session.getId()),
                        t);
            }
            ((WsSession) session).getLocal().onError(session, t);
        } else if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        } else {
            throw new RuntimeException(t.getMessage(), t);
        }
    }
}
