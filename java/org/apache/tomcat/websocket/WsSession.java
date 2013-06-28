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
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.Principal;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Extension;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

public class WsSession implements Session {

    private static final Charset UTF8 = Charset.forName("UTF8");
    private static final StringManager sm =
            StringManager.getManager(Constants.PACKAGE_NAME);
    private static AtomicLong ids = new AtomicLong(0);

    private final Log log = LogFactory.getLog(WsSession.class);

    private final Endpoint localEndpoint;
    private final WsRemoteEndpointImplBase wsRemoteEndpoint;
    private final RemoteEndpoint.Async remoteEndpointAsync;
    private final RemoteEndpoint.Basic remoteEndpointBasic;
    private final ClassLoader applicationClassLoader;
    private final WsWebSocketContainer webSocketContainer;
    private final URI requestUri;
    private final Map<String,List<String>> requestParameterMap;
    private final String queryString;
    private final Principal userPrincipal;
    private final EndpointConfig endpointConfig;

    private final String subProtocol;
    private final Map<String,String> pathParameters;
    private final boolean secure;
    private final String httpSessionId;
    private final String id;

    // Expected to handle message types of <String> only
    private MessageHandler textMessageHandler = null;
    // Expected to handle message types of <ByteBuffer> only
    private MessageHandler binaryMessageHandler = null;
    private MessageHandler.Whole<PongMessage> pongMessageHandler = null;
    private volatile State state = State.OPEN;
    private final Object stateLock = new Object();
    private final Map<String,Object> userProperties = new ConcurrentHashMap<>();
    private volatile int maxBinaryMessageBufferSize =
            Constants.DEFAULT_BUFFER_SIZE;
    private volatile int maxTextMessageBufferSize =
            Constants.DEFAULT_BUFFER_SIZE;
    private volatile long maxIdleTimeout = 0;
    private volatile long lastActive = System.currentTimeMillis();

    /**
     * Creates a new WebSocket session for communication between the two
     * provided end points. The result of {@link Thread#getContextClassLoader()}
     * at the time this constructor is called will be used when calling
     * {@link Endpoint#onClose(Session, CloseReason)}.
     *
     * @param localEndpoint
     * @param wsRemoteEndpoint
     * @throws DeploymentException
     */
    public WsSession(Endpoint localEndpoint,
            WsRemoteEndpointImplBase wsRemoteEndpoint,
            WsWebSocketContainer wsWebSocketContainer,
            URI requestUri, Map<String,List<String>> requestParameterMap,
            String queryString, Principal userPrincipal, String httpSessionId,
            String subProtocol, Map<String,String> pathParameters,
            boolean secure, EndpointConfig endpointConfig)
                    throws DeploymentException {
        this.localEndpoint = localEndpoint;
        this.wsRemoteEndpoint = wsRemoteEndpoint;
        this.wsRemoteEndpoint.setSession(this);
        this.remoteEndpointAsync = new WsRemoteEndpointAsync(wsRemoteEndpoint);
        this.remoteEndpointBasic = new WsRemoteEndpointBasic(wsRemoteEndpoint);
        this.webSocketContainer = wsWebSocketContainer;
        applicationClassLoader = Thread.currentThread().getContextClassLoader();
        wsRemoteEndpoint.setSendTimeout(
                wsWebSocketContainer.getDefaultAsyncSendTimeout());
        this.maxBinaryMessageBufferSize =
                webSocketContainer.getDefaultMaxBinaryMessageBufferSize();
        this.maxTextMessageBufferSize =
                webSocketContainer.getDefaultMaxTextMessageBufferSize();
        this.maxIdleTimeout =
                webSocketContainer.getDefaultMaxSessionIdleTimeout();
        this.requestUri = requestUri;
        if (requestParameterMap == null) {
            this.requestParameterMap = Collections.EMPTY_MAP;
        } else {
            this.requestParameterMap = requestParameterMap;
        }
        this.queryString = queryString;
        this.userPrincipal = userPrincipal;
        this.httpSessionId = httpSessionId;
        if (subProtocol == null) {
            this.subProtocol = "";
        } else {
            this.subProtocol = subProtocol;
        }
        this.pathParameters = pathParameters;
        this.secure = secure;
        this.wsRemoteEndpoint.setEncoders(endpointConfig);
        this.endpointConfig = endpointConfig;

        this.userProperties.putAll(endpointConfig.getUserProperties());
        this.id = Long.toHexString(ids.getAndIncrement());
    }


    @Override
    public WebSocketContainer getContainer() {
        checkState();
        return webSocketContainer;
    }


    @SuppressWarnings("unchecked")
    @Override
    public void addMessageHandler(MessageHandler listener) {

        checkState();

        // Message handlers that require decoders may map to text messages,
        // binary messages, both or neither.

        // The frame processing code expects binary message handlers to
        // accept ByteBuffer

        // Use the POJO message handler wrappers as they are designed to wrap
        // arbitrary objects with MessageHandlers and can wrap MessageHandlers
        // just as easily.

        Set<MessageHandlerResult> mhResults =
                Util.getMessageHandlers(listener, endpointConfig);

        for (MessageHandlerResult mhResult : mhResults) {
            switch (mhResult.getType()) {
                case TEXT: {
                    if (textMessageHandler != null) {
                        throw new IllegalStateException(
                                sm.getString("wsSession.duplicateHandlerText"));
                    }
                    textMessageHandler = mhResult.getHandler();
                    break;
                }
                case BINARY: {
                    if (binaryMessageHandler != null) {
                        throw new IllegalStateException(
                                sm.getString("wsSession.duplicateHandlerBinary"));
                    }
                    binaryMessageHandler = mhResult.getHandler();
                    break;
                }
                case PONG: {
                    if (pongMessageHandler != null) {
                        throw new IllegalStateException(
                                sm.getString("wsSession.duplicateHandlerPong"));
                    }
                    MessageHandler handler = mhResult.getHandler();
                    if (handler instanceof MessageHandler.Whole<?>) {
                        pongMessageHandler =
                                (MessageHandler.Whole<PongMessage>) handler;
                    } else {
                        throw new IllegalStateException(
                                sm.getString("wsSession.invalidHandlerTypePong"));
                    }

                    break;
                }
                default: {
                    throw new IllegalArgumentException(sm.getString(
                            "wsSession.unknownHandlerType", listener,
                            mhResult.getType()));
                }
            }
        }
    }


    @Override
    public Set<MessageHandler> getMessageHandlers() {
        checkState();
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
        checkState();
        if (listener == null) {
            return;
        }

        MessageHandler wrapped = null;

        if (listener instanceof WrappedMessageHandler) {
            wrapped = ((WrappedMessageHandler) listener).getWrappedHandler();
        }

        if (wrapped == null) {
            wrapped = listener;
        }

        boolean removed = false;
        if (wrapped.equals(textMessageHandler) ||
                listener.equals(textMessageHandler)) {
            textMessageHandler = null;
            removed = true;
        }

        if (listener.equals(binaryMessageHandler) ||
                listener.equals(binaryMessageHandler)) {
            binaryMessageHandler = null;
            removed = true;
        }

        if (listener.equals(pongMessageHandler) ||
                listener.equals(pongMessageHandler)) {
            pongMessageHandler = null;
            removed = true;
        }

        if (!removed) {
            // ISE for now. Could swallow this silently / log this if the ISE
            // becomes a problem
            throw new IllegalStateException(
                    sm.getString("wsSession.removeHandlerFailed", listener));
        }
    }


    @Override
    public String getProtocolVersion() {
        checkState();
        return Constants.WS_VERSION_HEADER_VALUE;
    }


    @Override
    public String getNegotiatedSubprotocol() {
        checkState();
        return subProtocol;
    }


    @Override
    public List<Extension> getNegotiatedExtensions() {
        checkState();
        return Collections.EMPTY_LIST;
    }


    @Override
    public boolean isSecure() {
        checkState();
        return secure;
    }


    @Override
    public boolean isOpen() {
        return state == State.OPEN;
    }


    @Override
    public long getMaxIdleTimeout() {
        checkState();
        return maxIdleTimeout;
    }


    @Override
    public void setMaxIdleTimeout(long timeout) {
        checkState();
        this.maxIdleTimeout = timeout;
    }


    @Override
    public void setMaxBinaryMessageBufferSize(int max) {
        checkState();
        this.maxBinaryMessageBufferSize = max;
    }


    @Override
    public int getMaxBinaryMessageBufferSize() {
        checkState();
        return maxBinaryMessageBufferSize;
    }


    @Override
    public void setMaxTextMessageBufferSize(int max) {
        checkState();
        this.maxTextMessageBufferSize = max;
    }


    @Override
    public int getMaxTextMessageBufferSize() {
        checkState();
        return maxTextMessageBufferSize;
    }


    @Override
    public Set<Session> getOpenSessions() {
        checkState();
        return webSocketContainer.getOpenSessions(localEndpoint.getClass());
    }


    @Override
    public RemoteEndpoint.Async getAsyncRemote() {
        checkState();
        return remoteEndpointAsync;
    }


    @Override
    public RemoteEndpoint.Basic getBasicRemote() {
        checkState();
        return remoteEndpointBasic;
    }


    @Override
    public void close() throws IOException {
        close(new CloseReason(CloseCodes.NORMAL_CLOSURE, ""));
    }


    @Override
    public void close(CloseReason closeReason) throws IOException {
        doClose(closeReason, closeReason);
    }


    /**
     * WebSocket 1.0. Section 2.1.5.
     * Need internal close method as spec requires that the local endpoint
     * receives a 1006 on timeout.
     */
    private void doClose(CloseReason closeReasonMessage,
            CloseReason closeReasonLocal) {
        // Double-checked locking. OK because state is volatile
        if (state != State.OPEN) {
            return;
        }

        synchronized (stateLock) {
            if (state != State.OPEN) {
                return;
            }

            state = State.CLOSING;

            sendCloseMessage(closeReasonMessage);
            fireEndpointOnClose(closeReasonLocal);

            state = State.CLOSED;
        }
    }


    /**
     * Called when a close message is received. Should only ever happen once.
     * Also called after a protocol error when the ProtocolHandler needs to
     * force the closing of the connection.
     */
    public void onClose(CloseReason closeReason) {

        synchronized (stateLock) {
            if (state == State.OPEN) {
                sendCloseMessage(closeReason);
                fireEndpointOnClose(closeReason);
                state = State.CLOSED;
            }

            // Close the socket
            wsRemoteEndpoint.close();
        }
    }


    private void fireEndpointOnClose(CloseReason closeReason) {

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

    private void sendCloseMessage(CloseReason closeReason) {
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
            // Failed to send close message. Close the socket and let the caller
            // deal with the Exception
            log.error(sm.getString("wsSession.sendCloseFail"), ioe);
            wsRemoteEndpoint.close();
            localEndpoint.onError(this, ioe);
        } finally {
            webSocketContainer.unregisterSession(
                    localEndpoint.getClass(), this);
        }

    }


    @Override
    public URI getRequestURI() {
        checkState();
        return requestUri;
    }


    @Override
    public Map<String,List<String>> getRequestParameterMap() {
        checkState();
        return requestParameterMap;
    }


    @Override
    public String getQueryString() {
        checkState();
        return queryString;
    }


    @Override
    public Principal getUserPrincipal() {
        checkState();
        return userPrincipal;
    }


    @Override
    public Map<String,String> getPathParameters() {
        checkState();
        return pathParameters;
    }


    @Override
    public String getId() {
        return id;
    }


    @Override
    public Map<String,Object> getUserProperties() {
        checkState();
        return userProperties;
    }


    public Endpoint getLocal() {
        return localEndpoint;
    }


    protected String getHttpSessionId() {
        return httpSessionId;
    }


    protected MessageHandler getTextMessageHandler() {
        return textMessageHandler;
    }


    protected MessageHandler getBinaryMessageHandler() {
        return binaryMessageHandler;
    }


    protected MessageHandler.Whole<PongMessage> getPongMessageHandler() {
        return pongMessageHandler;
    }


    protected void updateLastActive() {
        lastActive = System.currentTimeMillis();
    }


    protected void expire() {
        long timeout = maxIdleTimeout;
        if (timeout < 1) {
            return;
        }

        if (System.currentTimeMillis() - lastActive > timeout) {
            String msg = sm.getString("wsSession.timeout");
            doClose(new CloseReason(CloseCodes.GOING_AWAY, msg),
                    new CloseReason(CloseCodes.CLOSED_ABNORMALLY, msg));
        }
    }


    private void checkState() {
        if (state == State.CLOSED) {
            throw new IllegalStateException(sm.getString("wsSession.closed"));
        }
    }

    private static enum State {
        OPEN,
        CLOSING,
        CLOSED
    }
}
