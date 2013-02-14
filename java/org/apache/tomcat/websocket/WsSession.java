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
package org.apache.tomcat.websocket;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.apache.tomcat.util.res.StringManager;

public class WsSession implements Session {

    private static final Charset UTF8 = Charset.forName("UTF8");
    private static final StringManager sm =
            StringManager.getManager(Constants.PACKAGE_NAME);

    private final Endpoint localEndpoint;
    private final WsRemoteEndpointBase wsRemoteEndpoint;
    private final ClassLoader applicationClassLoader;
    private final WsWebSocketContainer webSocketContainer;

    private MessageHandler textMessageHandler = null;
    private MessageHandler binaryMessageHandler = null;
    private MessageHandler.Basic<PongMessage> pongMessageHandler = null;
    private volatile boolean open = true;
    private final Object closeLock = new Object();
    private Map<String,Object> userProperties = new ConcurrentHashMap<>();
    private volatile int maxBinaryMessageBufferSize =
            Constants.DEFAULT_BUFFER_SIZE;
    private volatile int maxTextMessageBufferSize =
            Constants.DEFAULT_BUFFER_SIZE;
    private volatile long sessionIdleTimeout = 0;
    private volatile long lastActive = System.currentTimeMillis();


    /**
     * Creates a new WebSocket session for communication between the two
     * provided end points. The result of {@link Thread#getContextClassLoader()}
     * at the time this constructor is called will be used when calling
     * {@link Endpoint#onClose(Session, CloseReason)}.
     *
     * @param localEndpoint
     * @param wsRemoteEndpoint
     */
    public WsSession(Endpoint localEndpoint,
            WsRemoteEndpointBase wsRemoteEndpoint,
            WsWebSocketContainer wsWebSocketContainer) {
        this.localEndpoint = localEndpoint;
        this.wsRemoteEndpoint = wsRemoteEndpoint;
        this.wsRemoteEndpoint.setSession(this);
        this.webSocketContainer = wsWebSocketContainer;
        applicationClassLoader = Thread.currentThread().getContextClassLoader();
        wsRemoteEndpoint.setAsyncSendTimeout(
                wsWebSocketContainer.getDefaultAsyncSendTimeout());
        this.maxBinaryMessageBufferSize =
                webSocketContainer.getDefaultMaxBinaryMessageBufferSize();
        this.maxTextMessageBufferSize =
                webSocketContainer.getDefaultMaxTextMessageBufferSize();
        this.sessionIdleTimeout =
                webSocketContainer.getMaxSessionIdleTimeout();
    }


    @Override
    public WebSocketContainer getContainer() {
        return webSocketContainer;
    }


    @SuppressWarnings("unchecked")
    @Override
    public void addMessageHandler(MessageHandler listener) {
        Type t = getMessageType(listener);

        if (t.equals(String.class)) {
            if (textMessageHandler != null) {
                throw new IllegalStateException(
                        sm.getString("wsSession.duplicateHandlerText"));
            }
            textMessageHandler = listener;
        } else if (t.equals(ByteBuffer.class)) {
            if (binaryMessageHandler != null) {
                throw new IllegalStateException(
                        sm.getString("wsSession.duplicateHandlerBinary"));
            }
            binaryMessageHandler = listener;
        } else if (t.equals(PongMessage.class)) {
            if (pongMessageHandler != null) {
                throw new IllegalStateException(
                        sm.getString("wsSession.duplicateHandlerPong"));
            }
            if (listener instanceof MessageHandler.Basic<?>) {
                pongMessageHandler =
                        (MessageHandler.Basic<PongMessage>) listener;
            } else {
                throw new IllegalStateException(
                        sm.getString("wsSession.invalidHandlerTypePong"));
            }
        } else {
            throw new IllegalArgumentException(
                    sm.getString("wsSession.unknownHandler", listener, t));
        }
    }


    @Override
    public Set<MessageHandler> getMessageHandlers() {
        Set<MessageHandler> result = new HashSet<>();
        if (binaryMessageHandler != null) {
            result.add(binaryMessageHandler);
        }
        if (textMessageHandler != null) {
            result.add(textMessageHandler);
        }
        if (pongMessageHandler != null) {
            result.add(pongMessageHandler);
        }
        return result;
    }


    @Override
    public void removeMessageHandler(MessageHandler listener) {
        if (listener == null) {
            return;
        }
        if (listener.equals(textMessageHandler)) {
            textMessageHandler = null;
        } else if (listener.equals(binaryMessageHandler)) {
            binaryMessageHandler = null;
        } else if (listener.equals(pongMessageHandler)) {
            pongMessageHandler = null;
        }

        // ISE for now. Could swallow this silently / log this if the ISE
        // becomes a problem
        throw new IllegalStateException(
                sm.getString("wsSession.removeHandlerFailed", listener));
    }


    @Override
    public String getProtocolVersion() {
        return Constants.WS_VERSION_HEADER_VALUE;
    }


    @Override
    public String getNegotiatedSubprotocol() {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public List<Extension> getNegotiatedExtensions() {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public boolean isSecure() {
        // TODO Auto-generated method stub
        return false;
    }


    @Override
    public boolean isOpen() {
        return open;
    }


    @Override
    public long getTimeout() {
        return sessionIdleTimeout;
    }


    @Override
    public void setTimeout(long timeout) {
        this.sessionIdleTimeout = timeout;
    }


    @Override
    public void setMaxBinaryMessageBufferSize(int max) {
        this.maxBinaryMessageBufferSize = max;
    }


    @Override
    public int getMaxBinaryMessageBufferSize() {
        return maxBinaryMessageBufferSize;
    }


    @Override
    public void setMaxTextMessageBufferSize(int max) {
        this.maxTextMessageBufferSize = max;
    }


    @Override
    public int getMaxTextMessageBufferSize() {
        return maxTextMessageBufferSize;
    }


    @Override
    public Set<Session> getOpenSessions() {
        return webSocketContainer.getOpenSession(localEndpoint.getClass());
    }


    @Override
    public RemoteEndpoint getRemote() {
        return wsRemoteEndpoint;
    }


    @Override
    public void close() throws IOException {
        close(new CloseReason(CloseCodes.NORMAL_CLOSURE, ""));
    }


    @Override
    public void close(CloseReason closeReason) throws IOException {
        // Double-checked locking. OK because open is volatile
        if (!open) {
            return;
        }
        synchronized (closeLock) {
            if (!open) {
                return;
            }
            open = false;

            // Send the close message
            // 125 is maximum size for the payload of a control message
            ByteBuffer msg = ByteBuffer.allocate(125);
            msg.putShort((short) closeReason.getCloseCode().getCode());
            String reason = closeReason.getReasonPhrase();
            if (reason != null && reason.length() > 0) {
                msg.put(reason.getBytes(UTF8));
            }
            msg.flip();
            try {
                wsRemoteEndpoint.startMessageBlock(
                        Constants.OPCODE_CLOSE, msg, true);
            } catch (IOException ioe) {
                // Unable to send close message.
                // TODO - Ignore?
            }

            webSocketContainer.unregisterSession(
                    localEndpoint.getClass(), this);

            // Fire the onClose event
            Thread t = Thread.currentThread();
            ClassLoader cl = t.getContextClassLoader();
            t.setContextClassLoader(applicationClassLoader);
            try {
                localEndpoint.onClose(this, closeReason);
            } finally {
                t.setContextClassLoader(cl);
            }
        }
    }


    @Override
    public URI getRequestURI() {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public Map<String,List<String>> getRequestParameterMap() {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public String getQueryString() {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public Map<String,String> getPathParameters() {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public String getId() {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public Principal getUserPrincipal() {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public Map<String,Object> getUserProperties() {
        return userProperties;
    }


    protected MessageHandler getTextMessageHandler() {
        return textMessageHandler;
    }


    protected MessageHandler getBinaryMessageHandler() {
        return binaryMessageHandler;
    }


    protected MessageHandler.Basic<PongMessage> getPongMessageHandler() {
        return pongMessageHandler;
    }


    protected void updateLastActive() {
        lastActive = System.currentTimeMillis();
    }

    protected void expire() {
        long timeout = sessionIdleTimeout;
        if (timeout < 1) {
            return;
        }

        if (System.currentTimeMillis() - lastActive > timeout) {
            try {
                close(new CloseReason(CloseCodes.GOING_AWAY,
                        sm.getString("wsSession.timeout")));
            } catch (IOException e) {
                // TODO Log this?
            }
        }
    }

    // Protected so unit tests can use it
    protected static Class<?> getMessageType(MessageHandler listener) {
        return (Class<?>) getMessageType(listener.getClass());
    }


    private static Object getMessageType(Class<? extends MessageHandler> clazz) {

        // Look to see if this class implements the generic MessageHandler<>
        // interface

        // Get all the interfaces
        Type[] interfaces = clazz.getGenericInterfaces();
        for (Type iface : interfaces) {
            // Only need to check interfaces that use generics
            if (iface instanceof ParameterizedType) {
                ParameterizedType pi = (ParameterizedType) iface;
                // Look for the MessageHandler<> interface
                if (pi.getRawType().equals(MessageHandler.Basic.class)
                        || pi.getRawType().equals(MessageHandler.Async.class)) {
                    // Whichever interface it is, there is only one generic
                    // type.
                    return getTypeParameter(
                            clazz, pi.getActualTypeArguments()[0]);
                }
            }
        }

        // Interface not found on this class. Look at the superclass.
        Class<? extends MessageHandler> superClazz =
                (Class<? extends MessageHandler>) clazz.getSuperclass();

        Object result = getMessageType(superClazz);
        if (result instanceof Class<?>) {
            // Superclass implements interface and defines explicit type for
            // MessageHandler<>
            return result;
        } else if (result instanceof Integer) {
            // Superclass implements interface and defines unknown type for
            // MessageHandler<>
            // Map that unknown type to the generic types defined in this class
            ParameterizedType superClassType =
                    (ParameterizedType) clazz.getGenericSuperclass();
            return getTypeParameter(clazz,
                    superClassType.getActualTypeArguments()[
                            ((Integer) result).intValue()]);
        } else {
            // TODO: Something went wrong. Log an error.
            return null;
        }
    }


    /*
     * For a generic parameter, return either the Class used or if the type
     * is unknown, the index for the type in definition of the class
     */
    private static Object getTypeParameter(Class<?> clazz, Type argType) {
        if (argType instanceof Class<?>) {
            return argType;
        } else {
            TypeVariable<?>[] tvs = clazz.getTypeParameters();
            for (int i = 0; i < tvs.length; i++) {
                if (tvs[i].equals(argType)) {
                    return Integer.valueOf(i);
                }
            }
            return null;
        }
    }
}
