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
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.naming.NamingException;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.CloseReason;
import jakarta.websocket.CloseReason.CloseCode;
import jakarta.websocket.CloseReason.CloseCodes;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Extension;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.MessageHandler.Partial;
import jakarta.websocket.MessageHandler.Whole;
import jakarta.websocket.PongMessage;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.SendResult;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import jakarta.websocket.server.ServerEndpointConfig;
import jakarta.websocket.server.ServerEndpointConfig.Configurator;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.pojo.PojoEndpointServer;
import org.apache.tomcat.websocket.server.DefaultServerEndpointConfigurator;

public class WsSession implements Session {

    private final Log log = LogFactory.getLog(WsSession.class); // must not be static
    private static final StringManager sm = StringManager.getManager(WsSession.class);

    // An ellipsis is a single character that looks like three periods in a row
    // and is used to indicate a continuation.
    private static final byte[] ELLIPSIS_BYTES = "\u2026".getBytes(StandardCharsets.UTF_8);
    // An ellipsis is three bytes in UTF-8
    private static final int ELLIPSIS_BYTES_LEN = ELLIPSIS_BYTES.length;

    private static final boolean SEC_CONFIGURATOR_USES_IMPL_DEFAULT;

    private static AtomicLong ids = new AtomicLong(0);

    static {
        // Use fake end point and path. They are never used, they just need to
        // be sufficient to pass the validation tests.
        ServerEndpointConfig.Builder builder = ServerEndpointConfig.Builder.create(Object.class, "/");
        ServerEndpointConfig sec = builder.build();
        SEC_CONFIGURATOR_USES_IMPL_DEFAULT = sec.getConfigurator().getClass()
                .equals(DefaultServerEndpointConfigurator.class);
    }

    private final Endpoint localEndpoint;
    private final WsRemoteEndpointImplBase wsRemoteEndpoint;
    private final RemoteEndpoint.Async remoteEndpointAsync;
    private final RemoteEndpoint.Basic remoteEndpointBasic;
    private final ClassLoader applicationClassLoader;
    private final WsWebSocketContainer webSocketContainer;
    private final URI requestUri;
    private final Map<String, List<String>> requestParameterMap;
    private final String queryString;
    private final Principal userPrincipal;
    private final EndpointConfig endpointConfig;

    private final List<Extension> negotiatedExtensions;
    private final String subProtocol;
    private final Map<String, String> pathParameters;
    private final boolean secure;
    private final String httpSessionId;
    private final String id;

    // Expected to handle message types of <String> only
    private volatile MessageHandler textMessageHandler = null;
    // Expected to handle message types of <ByteBuffer> only
    private volatile MessageHandler binaryMessageHandler = null;
    private volatile MessageHandler.Whole<PongMessage> pongMessageHandler = null;
    private AtomicReference<State> state = new AtomicReference<>(State.OPEN);
    private final Map<String, Object> userProperties = new ConcurrentHashMap<>();
    private volatile int maxBinaryMessageBufferSize = Constants.DEFAULT_BUFFER_SIZE;
    private volatile int maxTextMessageBufferSize = Constants.DEFAULT_BUFFER_SIZE;
    private volatile long maxIdleTimeout = 0;
    private volatile long lastActiveRead = System.currentTimeMillis();
    private volatile long lastActiveWrite = System.currentTimeMillis();
    private Map<FutureToSendHandler, FutureToSendHandler> futures = new ConcurrentHashMap<>();
    private volatile Long sessionCloseTimeoutExpiry;


    /**
     * Creates a new WebSocket session for communication between the provided client and remote end points. The result
     * of {@link Thread#getContextClassLoader()} at the time this constructor is called will be used when calling
     * {@link Endpoint#onClose(Session, CloseReason)}.
     *
     * @param clientEndpointHolder The end point managed by this code
     * @param wsRemoteEndpoint     The other / remote end point
     * @param wsWebSocketContainer The container that created this session
     * @param negotiatedExtensions The agreed extensions to use for this session
     * @param subProtocol          The agreed sub-protocol to use for this session
     * @param pathParameters       The path parameters associated with the request that initiated this session or
     *                                 <code>null</code> if this is a client session
     * @param secure               Was this session initiated over a secure connection?
     * @param clientEndpointConfig The configuration information for the client end point
     *
     * @throws DeploymentException if an invalid encode is specified
     */
    public WsSession(ClientEndpointHolder clientEndpointHolder, WsRemoteEndpointImplBase wsRemoteEndpoint,
            WsWebSocketContainer wsWebSocketContainer, List<Extension> negotiatedExtensions, String subProtocol,
            Map<String, String> pathParameters, boolean secure, ClientEndpointConfig clientEndpointConfig)
            throws DeploymentException {
        this.wsRemoteEndpoint = wsRemoteEndpoint;
        this.wsRemoteEndpoint.setSession(this);
        this.remoteEndpointAsync = new WsRemoteEndpointAsync(wsRemoteEndpoint);
        this.remoteEndpointBasic = new WsRemoteEndpointBasic(wsRemoteEndpoint);
        this.webSocketContainer = wsWebSocketContainer;
        applicationClassLoader = Thread.currentThread().getContextClassLoader();
        wsRemoteEndpoint.setSendTimeout(wsWebSocketContainer.getDefaultAsyncSendTimeout());
        this.maxBinaryMessageBufferSize = webSocketContainer.getDefaultMaxBinaryMessageBufferSize();
        this.maxTextMessageBufferSize = webSocketContainer.getDefaultMaxTextMessageBufferSize();
        this.maxIdleTimeout = webSocketContainer.getDefaultMaxSessionIdleTimeout();
        this.requestUri = null;
        this.requestParameterMap = Collections.emptyMap();
        this.queryString = null;
        this.userPrincipal = null;
        this.httpSessionId = null;
        this.negotiatedExtensions = negotiatedExtensions;
        if (subProtocol == null) {
            this.subProtocol = "";
        } else {
            this.subProtocol = subProtocol;
        }
        this.pathParameters = pathParameters;
        this.secure = secure;
        this.wsRemoteEndpoint.setEncoders(clientEndpointConfig);
        this.endpointConfig = clientEndpointConfig;

        this.userProperties.putAll(endpointConfig.getUserProperties());
        this.id = Long.toHexString(ids.getAndIncrement());

        this.localEndpoint = clientEndpointHolder.getInstance(getInstanceManager());

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("wsSession.created", id));
        }
    }


    /**
     * Creates a new WebSocket session for communication between the provided server and remote end points. The result
     * of {@link Thread#getContextClassLoader()} at the time this constructor is called will be used when calling
     * {@link Endpoint#onClose(Session, CloseReason)}.
     *
     * @param wsRemoteEndpoint     The other / remote end point
     * @param wsWebSocketContainer The container that created this session
     * @param requestUri           The URI used to connect to this end point or <code>null</code> if this is a client
     *                                 session
     * @param requestParameterMap  The parameters associated with the request that initiated this session or
     *                                 <code>null</code> if this is a client session
     * @param queryString          The query string associated with the request that initiated this session or
     *                                 <code>null</code> if this is a client session
     * @param userPrincipal        The principal associated with the request that initiated this session or
     *                                 <code>null</code> if this is a client session
     * @param httpSessionId        The HTTP session ID associated with the request that initiated this session or
     *                                 <code>null</code> if this is a client session
     * @param negotiatedExtensions The agreed extensions to use for this session
     * @param subProtocol          The agreed sub-protocol to use for this session
     * @param pathParameters       The path parameters associated with the request that initiated this session or
     *                                 <code>null</code> if this is a client session
     * @param secure               Was this session initiated over a secure connection?
     * @param serverEndpointConfig The configuration information for the server end point
     *
     * @throws DeploymentException if an invalid encode is specified
     */
    public WsSession(WsRemoteEndpointImplBase wsRemoteEndpoint, WsWebSocketContainer wsWebSocketContainer,
            URI requestUri, Map<String, List<String>> requestParameterMap, String queryString, Principal userPrincipal,
            String httpSessionId, List<Extension> negotiatedExtensions, String subProtocol,
            Map<String, String> pathParameters, boolean secure, ServerEndpointConfig serverEndpointConfig)
            throws DeploymentException {

        this.wsRemoteEndpoint = wsRemoteEndpoint;
        this.wsRemoteEndpoint.setSession(this);
        this.remoteEndpointAsync = new WsRemoteEndpointAsync(wsRemoteEndpoint);
        this.remoteEndpointBasic = new WsRemoteEndpointBasic(wsRemoteEndpoint);
        this.webSocketContainer = wsWebSocketContainer;
        applicationClassLoader = Thread.currentThread().getContextClassLoader();
        wsRemoteEndpoint.setSendTimeout(wsWebSocketContainer.getDefaultAsyncSendTimeout());
        this.maxBinaryMessageBufferSize = webSocketContainer.getDefaultMaxBinaryMessageBufferSize();
        this.maxTextMessageBufferSize = webSocketContainer.getDefaultMaxTextMessageBufferSize();
        this.maxIdleTimeout = webSocketContainer.getDefaultMaxSessionIdleTimeout();
        this.requestUri = requestUri;
        if (requestParameterMap == null) {
            this.requestParameterMap = Collections.emptyMap();
        } else {
            this.requestParameterMap = requestParameterMap;
        }
        this.queryString = queryString;
        this.userPrincipal = userPrincipal;
        this.httpSessionId = httpSessionId;
        this.negotiatedExtensions = negotiatedExtensions;
        if (subProtocol == null) {
            this.subProtocol = "";
        } else {
            this.subProtocol = subProtocol;
        }
        this.pathParameters = pathParameters;
        this.secure = secure;
        this.wsRemoteEndpoint.setEncoders(serverEndpointConfig);
        this.endpointConfig = serverEndpointConfig;

        this.userProperties.putAll(endpointConfig.getUserProperties());
        this.id = Long.toHexString(ids.getAndIncrement());

        InstanceManager instanceManager = getInstanceManager();
        Configurator configurator = serverEndpointConfig.getConfigurator();
        Class<?> clazz = serverEndpointConfig.getEndpointClass();

        Object endpointInstance;
        try {
            if (instanceManager == null || !isDefaultConfigurator(configurator)) {
                endpointInstance = configurator.getEndpointInstance(clazz);
                if (instanceManager != null) {
                    try {
                        instanceManager.newInstance(endpointInstance);
                    } catch (ReflectiveOperationException | NamingException e) {
                        throw new DeploymentException(sm.getString("wsSession.instanceNew"), e);
                    }
                }
            } else {
                endpointInstance = instanceManager.newInstance(clazz);
            }
        } catch (ReflectiveOperationException | NamingException e) {
            throw new DeploymentException(sm.getString("wsSession.instanceCreateFailed"), e);
        }

        if (endpointInstance instanceof Endpoint) {
            this.localEndpoint = (Endpoint) endpointInstance;
        } else {
            this.localEndpoint = new PojoEndpointServer(pathParameters, endpointInstance);
        }

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("wsSession.created", id));
        }
    }


    private boolean isDefaultConfigurator(Configurator configurator) {
        if (configurator.getClass().equals(DefaultServerEndpointConfigurator.class)) {
            return true;
        }
        if (SEC_CONFIGURATOR_USES_IMPL_DEFAULT &&
                configurator.getClass().equals(ServerEndpointConfig.Configurator.class)) {
            return true;
        }
        return false;
    }


    public InstanceManager getInstanceManager() {
        return webSocketContainer.getInstanceManager(applicationClassLoader);
    }


    @Override
    public WebSocketContainer getContainer() {
        checkState();
        return webSocketContainer;
    }


    @Override
    public void addMessageHandler(MessageHandler listener) {
        Class<?> target = Util.getMessageType(listener);
        doAddMessageHandler(target, listener);
    }


    @Override
    public <T> void addMessageHandler(Class<T> clazz, Partial<T> handler) throws IllegalStateException {
        doAddMessageHandler(clazz, handler);
    }


    @Override
    public <T> void addMessageHandler(Class<T> clazz, Whole<T> handler) throws IllegalStateException {
        doAddMessageHandler(clazz, handler);
    }


    @SuppressWarnings("unchecked")
    private void doAddMessageHandler(Class<?> target, MessageHandler listener) {
        checkState();

        // Message handlers that require decoders may map to text messages,
        // binary messages, both or neither.

        // The frame processing code expects binary message handlers to
        // accept ByteBuffer

        // Use the POJO message handler wrappers as they are designed to wrap
        // arbitrary objects with MessageHandlers and can wrap MessageHandlers
        // just as easily.

        Set<MessageHandlerResult> mhResults = Util.getMessageHandlers(target, listener, endpointConfig, this);

        for (MessageHandlerResult mhResult : mhResults) {
            switch (mhResult.getType()) {
                case TEXT: {
                    if (textMessageHandler != null) {
                        throw new IllegalStateException(sm.getString("wsSession.duplicateHandlerText"));
                    }
                    textMessageHandler = mhResult.getHandler();
                    break;
                }
                case BINARY: {
                    if (binaryMessageHandler != null) {
                        throw new IllegalStateException(sm.getString("wsSession.duplicateHandlerBinary"));
                    }
                    binaryMessageHandler = mhResult.getHandler();
                    break;
                }
                case PONG: {
                    if (pongMessageHandler != null) {
                        throw new IllegalStateException(sm.getString("wsSession.duplicateHandlerPong"));
                    }
                    MessageHandler handler = mhResult.getHandler();
                    if (handler instanceof MessageHandler.Whole<?>) {
                        pongMessageHandler = (MessageHandler.Whole<PongMessage>) handler;
                    } else {
                        throw new IllegalStateException(sm.getString("wsSession.invalidHandlerTypePong"));
                    }

                    break;
                }
                default: {
                    throw new IllegalArgumentException(
                            sm.getString("wsSession.unknownHandlerType", listener, mhResult.getType()));
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
        if (wrapped.equals(textMessageHandler) || listener.equals(textMessageHandler)) {
            textMessageHandler = null;
            removed = true;
        }

        if (wrapped.equals(binaryMessageHandler) || listener.equals(binaryMessageHandler)) {
            binaryMessageHandler = null;
            removed = true;
        }

        if (wrapped.equals(pongMessageHandler) || listener.equals(pongMessageHandler)) {
            pongMessageHandler = null;
            removed = true;
        }

        if (!removed) {
            // ISE for now. Could swallow this silently / log this if the ISE
            // becomes a problem
            throw new IllegalStateException(sm.getString("wsSession.removeHandlerFailed", listener));
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
        return negotiatedExtensions;
    }


    @Override
    public boolean isSecure() {
        checkState();
        return secure;
    }


    @Override
    public boolean isOpen() {
        return state.get() == State.OPEN || state.get() == State.OUTPUT_CLOSING || state.get() == State.CLOSING;
    }


    public boolean isClosed() {
        return state.get() == State.CLOSED;
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
        return webSocketContainer.getOpenSessions(getSessionMapKey());
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
     * WebSocket 1.0. Section 2.1.5. Need internal close method as spec requires that the local endpoint receives a 1006
     * on timeout.
     *
     * @param closeReasonMessage The close reason to pass to the remote endpoint
     * @param closeReasonLocal   The close reason to pass to the local endpoint
     */
    public void doClose(CloseReason closeReasonMessage, CloseReason closeReasonLocal) {
        doClose(closeReasonMessage, closeReasonLocal, false);
    }


    /**
     * WebSocket 1.0. Section 2.1.5. Need internal close method as spec requires that the local endpoint receives a 1006
     * on timeout.
     *
     * @param closeReasonMessage The close reason to pass to the remote endpoint
     * @param closeReasonLocal   The close reason to pass to the local endpoint
     * @param closeSocket        Should the socket be closed immediately rather than waiting for the server to respond
     */
    public void doClose(CloseReason closeReasonMessage, CloseReason closeReasonLocal, boolean closeSocket) {

        if (!state.compareAndSet(State.OPEN, State.OUTPUT_CLOSING)) {
            // Close process has already been started. Don't start it again.
            return;
        }

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("wsSession.doClose", id));
        }

        // Flush any batched messages not yet sent.
        try {
            wsRemoteEndpoint.setBatchingAllowed(false);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.warn(sm.getString("wsSession.flushFailOnClose"), t);
            fireEndpointOnError(t);
        }

        // Send the close message to the remote endpoint.
        sendCloseMessage(closeReasonMessage);
        fireEndpointOnClose(closeReasonLocal);
        if (!state.compareAndSet(State.OUTPUT_CLOSING, State.OUTPUT_CLOSED) || closeSocket) {
            /*
             * A close message was received in another thread or this is handling an error condition. Either way, no
             * further close message is expected to be received. Mark the session as fully closed...
             */
            state.set(State.CLOSED);
            // ... and close the network connection.
            closeConnection();
        } else {
            /*
             * Set close timeout. If the client fails to send a close message response within the timeout, the session
             * and the connection will be closed when the timeout expires.
             */
            sessionCloseTimeoutExpiry =
                    Long.valueOf(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(getSessionCloseTimeout()));
        }

        // Fail any uncompleted messages.
        IOException ioe = new IOException(sm.getString("wsSession.messageFailed"));
        SendResult sr = new SendResult(this, ioe);
        for (FutureToSendHandler f2sh : futures.keySet()) {
            f2sh.onResult(sr);
        }
    }


    /**
     * Called when a close message is received. Should only ever happen once. Also called after a protocol error when
     * the ProtocolHandler needs to force the closing of the connection.
     *
     * @param closeReason The reason contained within the received close message.
     */
    public void onClose(CloseReason closeReason) {
        if (state.compareAndSet(State.OPEN, State.CLOSING)) {
            // Standard close.

            // Flush any batched messages not yet sent.
            try {
                wsRemoteEndpoint.setBatchingAllowed(false);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.warn(sm.getString("wsSession.flushFailOnClose"), t);
                fireEndpointOnError(t);
            }

            // Send the close message response to the remote endpoint.
            sendCloseMessage(closeReason);
            fireEndpointOnClose(closeReason);

            // Mark the session as fully closed.
            state.set(State.CLOSED);

            // Close the network connection.
            closeConnection();
        } else if (state.compareAndSet(State.OUTPUT_CLOSING, State.CLOSING)) {
            /*
             * The local endpoint sent a close message the the same time as the remote endpoint. The local close is
             * still being processed. Update the state so the the local close process will also close the network
             * connection once it has finished sending a close message.
             */
        } else if (state.compareAndSet(State.OUTPUT_CLOSED, State.CLOSED)) {
            /*
             * The local endpoint sent the first close message. The remote endpoint has now responded with its own close
             * message so mark the session as fully closed and close the network connection.
             */
            closeConnection();
        }
        // CLOSING and CLOSED are NO-OPs
    }


    private void closeConnection() {
        /*
         * Close the network connection.
         */
        wsRemoteEndpoint.close();
        /*
         * Don't unregister the session until the connection is fully closed since webSocketContainer is responsible for
         * tracking the session close timeout.
         */
        webSocketContainer.unregisterSession(getSessionMapKey(), this);
    }


    /*
     * Returns the session close timeout in milliseconds
     */
    protected long getSessionCloseTimeout() {
        long result = 0;
        Object obj = userProperties.get(Constants.SESSION_CLOSE_TIMEOUT_PROPERTY);
        if (obj instanceof Long) {
            result = ((Long) obj).intValue();
        }
        if (result <= 0) {
            result = Constants.DEFAULT_SESSION_CLOSE_TIMEOUT;
        }
        return result;
    }


    /*
     * Returns the session close timeout in milliseconds
     */
    private long getAbnormalSessionCloseSendTimeout() {
        long result = 0;
        Object obj = userProperties.get(Constants.ABNORMAL_SESSION_CLOSE_SEND_TIMEOUT_PROPERTY);
        if (obj instanceof Long) {
            result = ((Long) obj).longValue();
        }
        if (result <= 0) {
            result = Constants.DEFAULT_ABNORMAL_SESSION_CLOSE_SEND_TIMEOUT;
        }
        return result;
    }


    protected void checkCloseTimeout() {
        // Skip the check if no session close timeout has been set.
        if (sessionCloseTimeoutExpiry != null) {
            // Check if the timeout has expired.
            if (System.nanoTime() - sessionCloseTimeoutExpiry.longValue() > 0) {
                // Check if the session has been closed in another thread while the timeout was being processed.
                if (state.compareAndSet(State.OUTPUT_CLOSED, State.CLOSED)) {
                    closeConnection();
                }
            }
        }
    }


    private void fireEndpointOnClose(CloseReason closeReason) {

        // Fire the onClose event
        Throwable throwable = null;
        InstanceManager instanceManager = getInstanceManager();
        Thread t = Thread.currentThread();
        ClassLoader cl = t.getContextClassLoader();
        t.setContextClassLoader(applicationClassLoader);
        try {
            localEndpoint.onClose(this, closeReason);
        } catch (Throwable t1) {
            ExceptionUtils.handleThrowable(t1);
            throwable = t1;
        } finally {
            if (instanceManager != null) {
                try {
                    instanceManager.destroyInstance(localEndpoint);
                } catch (Throwable t2) {
                    ExceptionUtils.handleThrowable(t2);
                    if (throwable == null) {
                        throwable = t2;
                    }
                }
            }
            t.setContextClassLoader(cl);
        }

        if (throwable != null) {
            fireEndpointOnError(throwable);
        }
    }


    private void fireEndpointOnError(Throwable throwable) {

        // Fire the onError event
        Thread t = Thread.currentThread();
        ClassLoader cl = t.getContextClassLoader();
        t.setContextClassLoader(applicationClassLoader);
        try {
            localEndpoint.onError(this, throwable);
        } finally {
            t.setContextClassLoader(cl);
        }
    }


    private void sendCloseMessage(CloseReason closeReason) {
        // 125 is maximum size for the payload of a control message
        ByteBuffer msg = ByteBuffer.allocate(125);
        CloseCode closeCode = closeReason.getCloseCode();
        // CLOSED_ABNORMALLY should not be put on the wire
        if (closeCode == CloseCodes.CLOSED_ABNORMALLY) {
            // PROTOCOL_ERROR is probably better than GOING_AWAY here
            msg.putShort((short) CloseCodes.PROTOCOL_ERROR.getCode());
        } else {
            msg.putShort((short) closeCode.getCode());
        }

        String reason = closeReason.getReasonPhrase();
        if (reason != null && reason.length() > 0) {
            appendCloseReasonWithTruncation(msg, reason);
        }
        msg.flip();
        try {
            if (closeCode == CloseCodes.NORMAL_CLOSURE) {
                wsRemoteEndpoint.sendMessageBlock(Constants.OPCODE_CLOSE, msg, true);
            } else {
                wsRemoteEndpoint.sendMessageBlock(Constants.OPCODE_CLOSE, msg, true,
                        getAbnormalSessionCloseSendTimeout());
            }
        } catch (IOException | IllegalStateException e) {
            // Failed to send close message. Close the socket and let the caller
            // deal with the Exception
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("wsSession.sendCloseFail", id), e);
            }
            closeConnection();
            // Failure to send a close message is not unexpected in the case of
            // an abnormal closure (usually triggered by a failure to read/write
            // from/to the client. In this case do not trigger the endpoint's
            // error handling
            if (closeCode != CloseCodes.CLOSED_ABNORMALLY) {
                localEndpoint.onError(this, e);
            }
        }
    }


    private Object getSessionMapKey() {
        if (endpointConfig instanceof ServerEndpointConfig) {
            // Server
            return ((ServerEndpointConfig) endpointConfig).getPath();
        } else {
            // Client
            return localEndpoint;
        }
    }

    /**
     * Use protected so unit tests can access this method directly.
     *
     * @param msg    The message
     * @param reason The reason
     */
    protected static void appendCloseReasonWithTruncation(ByteBuffer msg, String reason) {
        // Once the close code has been added there are a maximum of 123 bytes
        // left for the reason phrase. If it is truncated then care needs to be
        // taken to ensure the bytes are not truncated in the middle of a
        // multi-byte UTF-8 character.
        byte[] reasonBytes = reason.getBytes(StandardCharsets.UTF_8);

        if (reasonBytes.length <= 123) {
            // No need to truncate
            msg.put(reasonBytes);
        } else {
            // Need to truncate
            int remaining = 123 - ELLIPSIS_BYTES_LEN;
            int pos = 0;
            byte[] bytesNext = reason.substring(pos, pos + 1).getBytes(StandardCharsets.UTF_8);
            while (remaining >= bytesNext.length) {
                msg.put(bytesNext);
                remaining -= bytesNext.length;
                pos++;
                bytesNext = reason.substring(pos, pos + 1).getBytes(StandardCharsets.UTF_8);
            }
            msg.put(ELLIPSIS_BYTES);
        }
    }


    /**
     * Make the session aware of a {@link FutureToSendHandler} that will need to be forcibly closed if the session
     * closes before the {@link FutureToSendHandler} completes.
     *
     * @param f2sh The handler
     */
    protected void registerFuture(FutureToSendHandler f2sh) {
        // Ideally, this code should sync on stateLock so that the correct
        // action is taken based on the current state of the connection.
        // However, a sync on stateLock can't be used here as it will create the
        // possibility of a dead-lock. See BZ 61183.
        // Therefore, a slightly less efficient approach is used.

        // Always register the future.
        futures.put(f2sh, f2sh);

        if (isOpen()) {
            // The session is open. The future has been registered with the open
            // session. Normal processing continues.
            return;
        }

        // The session is closing / closed. The future may or may not have been registered
        // in time for it to be processed during session closure.

        if (f2sh.isDone()) {
            // The future has completed. It is not known if the future was
            // completed normally by the I/O layer or in error by doClose(). It
            // doesn't matter which. There is nothing more to do here.
            return;
        }

        // The session is closing / closed. The Future had not completed when last checked.
        // There is a small timing window that means the Future may have been
        // completed since the last check. There is also the possibility that
        // the Future was not registered in time to be cleaned up during session
        // close.
        // Attempt to complete the Future with an error result as this ensures
        // that the Future completes and any client code waiting on it does not
        // hang. It is slightly inefficient since the Future may have been
        // completed in another thread or another thread may be about to
        // complete the Future but knowing if this is the case requires the sync
        // on stateLock (see above).
        // Note: If multiple attempts are made to complete the Future, the
        // second and subsequent attempts are ignored.

        IOException ioe = new IOException(sm.getString("wsSession.messageFailed"));
        SendResult sr = new SendResult(this, ioe);
        f2sh.onResult(sr);
    }


    /**
     * Remove a {@link FutureToSendHandler} from the set of tracked instances.
     *
     * @param f2sh The handler
     */
    protected void unregisterFuture(FutureToSendHandler f2sh) {
        futures.remove(f2sh);
    }


    @Override
    public URI getRequestURI() {
        checkState();
        return requestUri;
    }


    @Override
    public Map<String, List<String>> getRequestParameterMap() {
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
        return getUserPrincipalInternal();
    }


    public Principal getUserPrincipalInternal() {
        return userPrincipal;
    }


    @Override
    public Map<String, String> getPathParameters() {
        checkState();
        return pathParameters;
    }


    @Override
    public String getId() {
        return id;
    }


    @Override
    public Map<String, Object> getUserProperties() {
        checkState();
        return userProperties;
    }


    public Endpoint getLocal() {
        return localEndpoint;
    }


    public String getHttpSessionId() {
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


    protected void updateLastActiveRead() {
        lastActiveRead = System.currentTimeMillis();
    }


    protected void updateLastActiveWrite() {
        lastActiveWrite = System.currentTimeMillis();
    }


    protected void checkExpiration() {
        // Local copies to ensure consistent behaviour during method execution
        long timeout = maxIdleTimeout;
        long timeoutRead = getMaxIdleTimeoutRead();
        long timeoutWrite = getMaxIdleTimeoutWrite();

        long currentTime = System.currentTimeMillis();
        String key = null;

        if (timeoutRead > 0 && (currentTime - lastActiveRead) > timeoutRead) {
            key = "wsSession.timeoutRead";
        } else if (timeoutWrite > 0 && (currentTime - lastActiveWrite) > timeoutWrite) {
            key = "wsSession.timeoutWrite";
        } else if (timeout > 0 && (currentTime - lastActiveRead) > timeout &&
                (currentTime - lastActiveWrite) > timeout) {
            key = "wsSession.timeout";
        }

        if (key != null) {
            String msg = sm.getString(key, getId());
            if (log.isDebugEnabled()) {
                log.debug(msg);
            }
            doClose(new CloseReason(CloseCodes.GOING_AWAY, msg), new CloseReason(CloseCodes.CLOSED_ABNORMALLY, msg));
        }
    }


    private long getMaxIdleTimeoutRead() {
        Object timeout = userProperties.get(Constants.READ_IDLE_TIMEOUT_MS);
        if (timeout instanceof Long) {
            return ((Long) timeout).longValue();
        }
        return 0;
    }


    private long getMaxIdleTimeoutWrite() {
        Object timeout = userProperties.get(Constants.WRITE_IDLE_TIMEOUT_MS);
        if (timeout instanceof Long) {
            return ((Long) timeout).longValue();
        }
        return 0;
    }


    private void checkState() {
        if (isClosed()) {
            /*
             * As per RFC 6455, a WebSocket connection is considered to be closed once a peer has sent and received a
             * WebSocket close frame.
             */
            throw new IllegalStateException(sm.getString("wsSession.closed", id));
        }
    }

    private enum State {
        OPEN,
        OUTPUT_CLOSING,
        OUTPUT_CLOSED,
        CLOSING,
        CLOSED
    }


    private WsFrameBase wsFrame;

    void setWsFrame(WsFrameBase wsFrame) {
        this.wsFrame = wsFrame;
    }


    /**
     * Suspends the reading of the incoming messages.
     */
    public void suspend() {
        wsFrame.suspend();
    }


    /**
     * Resumes the reading of the incoming messages.
     */
    public void resume() {
        wsFrame.resume();
    }
}
