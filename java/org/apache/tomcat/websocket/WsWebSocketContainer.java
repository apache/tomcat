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

import java.io.IOException;
import java.net.URI;
import java.nio.channels.AsynchronousChannelGroup;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.pojo.PojoEndpointClient;

public class WsWebSocketContainer implements WebSocketContainer, BackgroundProcess {

    private static final StringManager sm = StringManager.getManager(WsWebSocketContainer.class);

    private volatile AsynchronousChannelGroup asynchronousChannelGroup = null;
    private final Object asynchronousChannelGroupLock = new Object();

    private final Log log = LogFactory.getLog(WsWebSocketContainer.class);
    private final Map<Endpoint, Set<WsSession>> endpointSessionMap =
            new HashMap<>();
    private final Map<WsSession,WsSession> sessions = new ConcurrentHashMap<>();
    private final Object endPointSessionMapLock = new Object();

    private long defaultAsyncTimeout = -1;
    private int maxBinaryMessageBufferSize = Constants.DEFAULT_BUFFER_SIZE;
    private int maxTextMessageBufferSize = Constants.DEFAULT_BUFFER_SIZE;
    private volatile long defaultMaxSessionIdleTimeout = 0;
    private int backgroundProcessCount = 0;
    private int processPeriod = Constants.DEFAULT_PROCESS_PERIOD;

    private InstanceManager instanceManager;

    InstanceManager getInstanceManager() {
        return instanceManager;
    }

    protected void setInstanceManager(InstanceManager instanceManager) {
        this.instanceManager = instanceManager;
    }

    @Override
    public Session connectToServer(Object pojo, URI path)
            throws DeploymentException {

        ClientEndpoint annotation =
                pojo.getClass().getAnnotation(ClientEndpoint.class);
        if (annotation == null) {
            throw new DeploymentException(
                    sm.getString("wsWebSocketContainer.missingAnnotation",
                            pojo.getClass().getName()));
        }

        Endpoint ep = new PojoEndpointClient(pojo, Arrays.asList(annotation.decoders()));

        Class<? extends ClientEndpointConfig.Configurator> configuratorClazz =
                annotation.configurator();

        ClientEndpointConfig.Configurator configurator = null;
        if (!ClientEndpointConfig.Configurator.class.equals(
                configuratorClazz)) {
            try {
                configurator = configuratorClazz.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new DeploymentException(sm.getString(
                        "wsWebSocketContainer.defaultConfiguratorFail"), e);
            }
        }

        ClientEndpointConfig.Builder builder = ClientEndpointConfig.Builder.create();
        // Avoid NPE when using RI API JAR - see BZ 56343
        if (configurator != null) {
            builder.configurator(configurator);
        }
        ClientEndpointConfig config = builder.
                decoders(Arrays.asList(annotation.decoders())).
                encoders(Arrays.asList(annotation.encoders())).
                preferredSubprotocols(Arrays.asList(annotation.subprotocols())).
                build();
        return connectToServer(ep, config, path);
    }


    @Override
    public Session connectToServer(Class<?> annotatedEndpointClass, URI path)
            throws DeploymentException {

        Object pojo;
        try {
            pojo = annotatedEndpointClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new DeploymentException(sm.getString(
                    "wsWebSocketContainer.endpointCreateFail",
                    annotatedEndpointClass.getName()), e);
        }

        return connectToServer(pojo, path);
    }


    @Override
    public Session connectToServer(Class<? extends Endpoint> clazz,
            ClientEndpointConfig clientEndpointConfiguration, URI path)
            throws DeploymentException {

        Endpoint endpoint;
        try {
            endpoint = clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new DeploymentException(sm.getString(
                    "wsWebSocketContainer.endpointCreateFail", clazz.getName()),
                    e);
        }

        return connectToServer(endpoint, clientEndpointConfiguration, path);
    }


    @Override
    public Session connectToServer(Endpoint endpoint,
            ClientEndpointConfig clientEndpointConfiguration, URI path)
            throws DeploymentException {

        WsWebSocketClient client = new WsWebSocketClient();
        return client.connectToServer(endpoint, clientEndpointConfiguration, path, this);
    }

    protected void registerSession(Endpoint endpoint, WsSession wsSession) {

        if (!wsSession.isOpen()) {
            // The session was closed during onOpen. No need to register it.
            return;
        }
        synchronized (endPointSessionMapLock) {
            if (endpointSessionMap.size() == 0) {
                BackgroundProcessManager.getInstance().register(this);
            }
            Set<WsSession> wsSessions = endpointSessionMap.get(endpoint);
            if (wsSessions == null) {
                wsSessions = new HashSet<>();
                endpointSessionMap.put(endpoint, wsSessions);
            }
            wsSessions.add(wsSession);
        }
        sessions.put(wsSession, wsSession);
    }


    protected void unregisterSession(Endpoint endpoint, WsSession wsSession) {

        synchronized (endPointSessionMapLock) {
            Set<WsSession> wsSessions = endpointSessionMap.get(endpoint);
            if (wsSessions != null) {
                wsSessions.remove(wsSession);
                if (wsSessions.size() == 0) {
                    endpointSessionMap.remove(endpoint);
                }
            }
            if (endpointSessionMap.size() == 0) {
                BackgroundProcessManager.getInstance().unregister(this);
            }
        }
        sessions.remove(wsSession);
    }


    Set<Session> getOpenSessions(Endpoint endpoint) {
        Set<Session> result = new HashSet<>();
        synchronized (endPointSessionMapLock) {
            Set<WsSession> sessions = endpointSessionMap.get(endpoint);
            if (sessions != null) {
                result.addAll(sessions);
            }
        }
        return result;
    }

    @Override
    public long getDefaultMaxSessionIdleTimeout() {
        return defaultMaxSessionIdleTimeout;
    }


    @Override
    public void setDefaultMaxSessionIdleTimeout(long timeout) {
        this.defaultMaxSessionIdleTimeout = timeout;
    }


    @Override
    public int getDefaultMaxBinaryMessageBufferSize() {
        return maxBinaryMessageBufferSize;
    }


    @Override
    public void setDefaultMaxBinaryMessageBufferSize(int max) {
        maxBinaryMessageBufferSize = max;
    }


    @Override
    public int getDefaultMaxTextMessageBufferSize() {
        return maxTextMessageBufferSize;
    }


    @Override
    public void setDefaultMaxTextMessageBufferSize(int max) {
        maxTextMessageBufferSize = max;
    }


    /**
     * {@inheritDoc}
     *
     * Currently, this implementation does not support any extensions.
     */
    @Override
    public Set<Extension> getInstalledExtensions() {
        return Collections.emptySet();
    }


    /**
     * {@inheritDoc}
     *
     * The default value for this implementation is -1.
     */
    @Override
    public long getDefaultAsyncSendTimeout() {
        return defaultAsyncTimeout;
    }


    /**
     * {@inheritDoc}
     *
     * The default value for this implementation is -1.
     */
    @Override
    public void setAsyncSendTimeout(long timeout) {
        this.defaultAsyncTimeout = timeout;
    }


    /**
     * Cleans up the resources still in use by WebSocket sessions created from
     * this container. This includes closing sessions and cancelling
     * {@link Future}s associated with blocking read/writes.
     */
    public void destroy() {
        CloseReason cr = new CloseReason(
                CloseCodes.GOING_AWAY, sm.getString("wsWebSocketContainer.shutdown"));

        for (WsSession session : sessions.keySet()) {
            try {
                session.close(cr);
            } catch (IOException ioe) {
                log.debug(sm.getString(
                        "wsWebSocketContainer.sessionCloseFail", session.getId()), ioe);
            }
        }

        // Only unregister with AsyncChannelGroupUtil if this instance
        // registered with it
        if (asynchronousChannelGroup != null) {
            synchronized (asynchronousChannelGroupLock) {
                if (asynchronousChannelGroup != null) {
                    AsyncChannelGroupUtil.unregister();
                    asynchronousChannelGroup = null;
                }
            }
        }
    }


    protected AsynchronousChannelGroup getAsynchronousChannelGroup() {
        // Use AsyncChannelGroupUtil to share a common group amongst all
        // WebSocket clients
        AsynchronousChannelGroup result = asynchronousChannelGroup;
        if (result == null) {
            synchronized (asynchronousChannelGroupLock) {
                if (asynchronousChannelGroup == null) {
                    asynchronousChannelGroup = AsyncChannelGroupUtil.register();
                }
                result = asynchronousChannelGroup;
            }
        }
        return result;
    }


    // ----------------------------------------------- BackgroundProcess methods

    @Override
    public void backgroundProcess() {
        // This method gets called once a second.
        backgroundProcessCount ++;
        if (backgroundProcessCount >= processPeriod) {
            backgroundProcessCount = 0;

            for (WsSession wsSession : sessions.keySet()) {
                wsSession.checkExpiration();
            }
        }

    }


    @Override
    public void setProcessPeriod(int period) {
        this.processPeriod = period;
    }


    /**
     * {@inheritDoc}
     *
     * The default value is 10 which means session expirations are processed
     * every 10 seconds.
     */
    @Override
    public int getProcessPeriod() {
        return processPeriod;
    }

}
