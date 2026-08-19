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
package org.apache.tomcat.websocket.server;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import javax.naming.NamingException;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.websocket.CloseReason;
import jakarta.websocket.CloseReason.CloseCodes;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Encoder;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;
import jakarta.websocket.server.ServerEndpointConfig.Configurator;

import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.WsSession;
import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.apache.tomcat.websocket.pojo.PojoMethodMapping;

/**
 * Provides a per class loader (i.e. per web application) instance of a ServerContainer. Web application wide defaults
 * may be configured by setting the following servlet context initialisation parameters to the desired values.
 * <ul>
 * <li>{@link Constants#BINARY_BUFFER_SIZE_SERVLET_CONTEXT_INIT_PARAM}</li>
 * <li>{@link Constants#TEXT_BUFFER_SIZE_SERVLET_CONTEXT_INIT_PARAM}</li>
 * </ul>
 */
public class WsServerContainer extends WsWebSocketContainer implements ServerContainer {

    private static final StringManager sm = StringManager.getManager(WsServerContainer.class);

    private static final CloseReason AUTHENTICATED_HTTP_SESSION_CLOSED = new CloseReason(CloseCodes.VIOLATED_POLICY,
            "This connection was established under an authenticated " + "HTTP session that has ended.");

    private final WsWriteTimeout wsWriteTimeout = new WsWriteTimeout();

    private final ServletContext servletContext;
    private final Map<String,ExactPathMatch> configExactMatchMap = new ConcurrentHashMap<>();
    private final Map<Integer,ConcurrentSkipListMap<String,TemplatePathMatch>> configTemplateMatchMap =
            new ConcurrentHashMap<>();
    private final Object authenticatedSessionMapLock = new Object();
    private final Map<String,Set<WsSession>> httpSessionKeyToWebSocketSession = new HashMap<>();
    private final Map<WsSession,String> webSocketSessionToHttpSessionKey = new HashMap<>();
    private volatile boolean endpointsRegistered = false;
    private volatile boolean deploymentFailed = false;

    WsServerContainer(ServletContext servletContext) {

        this.servletContext = servletContext;
        setInstanceManager((InstanceManager) servletContext.getAttribute(InstanceManager.class.getName()));

        // Configure servlet context wide defaults
        String value = servletContext.getInitParameter(Constants.BINARY_BUFFER_SIZE_SERVLET_CONTEXT_INIT_PARAM);
        if (value != null) {
            setDefaultMaxBinaryMessageBufferSize(Integer.parseInt(value));
        }

        value = servletContext.getInitParameter(Constants.TEXT_BUFFER_SIZE_SERVLET_CONTEXT_INIT_PARAM);
        if (value != null) {
            setDefaultMaxTextMessageBufferSize(Integer.parseInt(value));
        }

        FilterRegistration.Dynamic fr = servletContext.addFilter("Tomcat WebSocket (JSR356) Filter", new WsFilter());
        if (fr != null) {
            fr.setAsyncSupported(true);

            EnumSet<DispatcherType> types = EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD);

            fr.addMappingForUrlPatterns(types, true, "/*");
        }
    }


    /**
     * Published the provided endpoint implementation at the specified path with the specified configuration.
     * {@link #WsServerContainer(ServletContext)} must be called before calling this method.
     *
     * @param sec The configuration to use when creating endpoint instances
     *
     * @throws DeploymentException if the endpoint cannot be published as requested
     */
    @Override
    public void addEndpoint(ServerEndpointConfig sec) throws DeploymentException {
        addEndpoint(sec, false);
    }


    void addEndpoint(ServerEndpointConfig sec, boolean fromAnnotatedPojo) throws DeploymentException {

        if (servletContext == null) {
            throw new DeploymentException(sm.getString("serverContainer.servletContextMissing"));
        }

        if (deploymentFailed) {
            throw new DeploymentException(sm.getString("serverContainer.failedDeployment",
                    servletContext.getContextPath(), servletContext.getVirtualServerName()));
        }

        try {
            String path = sec.getPath();

            // Add method mapping to user properties
            PojoMethodMapping methodMapping = new PojoMethodMapping(sec.getEndpointClass(), sec.getDecoders(), path,
                    getInstanceManager(Thread.currentThread().getContextClassLoader()));
            if (methodMapping.getOnClose() != null || methodMapping.getOnOpen() != null ||
                    methodMapping.getOnError() != null || methodMapping.hasMessageHandlers()) {
                sec.getUserProperties().put(org.apache.tomcat.websocket.pojo.Constants.POJO_METHOD_MAPPING_KEY,
                        methodMapping);
            }

            UriTemplate uriTemplate = new UriTemplate(path);
            if (uriTemplate.hasParameters()) {
                Integer key = Integer.valueOf(uriTemplate.getSegmentCount());
                ConcurrentSkipListMap<String,TemplatePathMatch> templateMatches = configTemplateMatchMap.get(key);
                if (templateMatches == null) {
                    // Ensure that if concurrent threads execute this block they
                    // all end up using the same ConcurrentSkipListMap instance
                    templateMatches = new ConcurrentSkipListMap<>();
                    configTemplateMatchMap.putIfAbsent(key, templateMatches);
                    templateMatches = configTemplateMatchMap.get(key);
                }
                TemplatePathMatch newMatch = new TemplatePathMatch(sec, uriTemplate, fromAnnotatedPojo);
                TemplatePathMatch oldMatch = templateMatches.putIfAbsent(uriTemplate.getNormalizedPath(), newMatch);
                if (oldMatch != null) {
                    // Note: This depends on Endpoint instances being added
                    // before POJOs in WsSci#onStartup()
                    if (oldMatch.isFromAnnotatedPojo() && !newMatch.isFromAnnotatedPojo() &&
                            oldMatch.getConfig().getEndpointClass() == newMatch.getConfig().getEndpointClass()) {
                        // The WebSocket spec says to ignore the new match in this case
                        templateMatches.put(path, oldMatch);
                    } else {
                        // Duplicate uriTemplate;
                        throw new DeploymentException(sm.getString("serverContainer.duplicatePaths", path,
                                sec.getEndpointClass(), sec.getEndpointClass()));
                    }
                }
            } else {
                // Exact match
                ExactPathMatch newMatch = new ExactPathMatch(sec, fromAnnotatedPojo);
                ExactPathMatch oldMatch = configExactMatchMap.put(path, newMatch);
                if (oldMatch != null) {
                    // Note: This depends on Endpoint instances being added
                    // before POJOs in WsSci#onStartup()
                    if (oldMatch.isFromAnnotatedPojo() && !newMatch.isFromAnnotatedPojo() &&
                            oldMatch.getConfig().getEndpointClass() == newMatch.getConfig().getEndpointClass()) {
                        // The WebSocket spec says to ignore the new match in this case
                        configExactMatchMap.put(path, oldMatch);
                    } else {
                        // Duplicate path mappings
                        throw new DeploymentException(sm.getString("serverContainer.duplicatePaths", path,
                                oldMatch.getConfig().getEndpointClass(), sec.getEndpointClass()));
                    }
                }
            }

            endpointsRegistered = true;
        } catch (DeploymentException de) {
            failDeployment();
            throw de;
        }
    }


    /**
     * Provides the equivalent of {@link #addEndpoint(ServerEndpointConfig)} for publishing plain old java objects
     * (POJOs) that have been annotated as WebSocket endpoints.
     *
     * @param pojo The annotated POJO
     */
    @Override
    public void addEndpoint(Class<?> pojo) throws DeploymentException {
        addEndpoint(pojo, false);
    }


    void addEndpoint(Class<?> pojo, boolean fromAnnotatedPojo) throws DeploymentException {

        if (deploymentFailed) {
            throw new DeploymentException(sm.getString("serverContainer.failedDeployment",
                    servletContext.getContextPath(), servletContext.getVirtualServerName()));
        }

        ServerEndpointConfig sec;

        try {
            ServerEndpoint annotation = pojo.getAnnotation(ServerEndpoint.class);
            if (annotation == null) {
                throw new DeploymentException(sm.getString("serverContainer.missingAnnotation", pojo.getName()));
            }
            String path = annotation.value();

            // Validate encoders
            validateEncoders(annotation.encoders(), getInstanceManager(Thread.currentThread().getContextClassLoader()));

            // ServerEndpointConfig
            Class<? extends Configurator> configuratorClazz = annotation.configurator();
            Configurator configurator = null;
            if (!configuratorClazz.equals(Configurator.class)) {
                try {
                    configurator = annotation.configurator().getConstructor().newInstance();
                } catch (ReflectiveOperationException e) {
                    throw new DeploymentException(sm.getString("serverContainer.configuratorFail",
                            annotation.configurator().getName(), pojo.getName()), e);
                }
            }
            sec = ServerEndpointConfig.Builder.create(pojo, path).decoders(Arrays.asList(annotation.decoders()))
                    .encoders(Arrays.asList(annotation.encoders()))
                    .subprotocols(Arrays.asList(annotation.subprotocols())).configurator(configurator).build();
        } catch (DeploymentException de) {
            failDeployment();
            throw de;
        }

        addEndpoint(sec, fromAnnotatedPojo);
    }


    void failDeployment() {
        deploymentFailed = true;

        // Clear all existing deployments
        endpointsRegistered = false;
        configExactMatchMap.clear();
        configTemplateMatchMap.clear();
    }


    boolean areEndpointsRegistered() {
        return endpointsRegistered;
    }


    @Override
    public void upgradeHttpToWebSocket(Object httpServletRequest, Object httpServletResponse, ServerEndpointConfig sec,
            Map<String,String> pathParameters) throws IOException, DeploymentException {
        try {
            UpgradeUtil.doUpgrade(this, (HttpServletRequest) httpServletRequest,
                    (HttpServletResponse) httpServletResponse, sec, pathParameters);
        } catch (ServletException e) {
            throw new DeploymentException(e.getMessage(), e);
        }
    }


    /**
     * Finds the endpoint configuration that matches the given path.
     *
     * @param path the URI path to match
     *
     * @return the mapping result, or null if no match is found
     */
    public WsMappingResult findMapping(String path) {

        // Check an exact match. Simple case as there are no templates.
        ExactPathMatch match = configExactMatchMap.get(path);
        if (match != null) {
            return new WsMappingResult(match.getConfig(), Collections.emptyMap());
        }

        // No exact match. Need to look for template matches.
        UriTemplate pathUriTemplate;
        try {
            pathUriTemplate = new UriTemplate(path, false);
        } catch (DeploymentException e) {
            // Path is not valid so can't be matched to a WebSocketEndpoint
            return null;
        }

        // Number of segments has to match
        Integer key = Integer.valueOf(pathUriTemplate.getSegmentCount());
        ConcurrentSkipListMap<String,TemplatePathMatch> templateMatches = configTemplateMatchMap.get(key);

        if (templateMatches == null) {
            // No templates with an equal number of segments so there will be
            // no matches
            return null;
        }

        // List is in alphabetical order of normalised templates.
        // Correct match is the first one that matches.
        ServerEndpointConfig sec = null;
        Map<String,String> pathParams = null;
        for (TemplatePathMatch templateMatch : templateMatches.values()) {
            pathParams = templateMatch.getUriTemplate().match(pathUriTemplate);
            if (pathParams != null) {
                sec = templateMatch.getConfig();
                break;
            }
        }

        if (sec == null) {
            // No match
            return null;
        }

        return new WsMappingResult(sec, pathParams);
    }


    /**
     * Returns the write timeout handler.
     *
     * @return the write timeout handler
     */
    protected WsWriteTimeout getTimeout() {
        return wsWriteTimeout;
    }


    /**
     * {@inheritDoc} Overridden to make it visible to other classes in this package.
     */
    @Override
    protected InstanceManager getInstanceManager(ClassLoader classLoader) {
        return super.getInstanceManager(classLoader);
    }


    /**
     * Register the link between the HTTP session and WebSocket session if the HTTP session is authenticated.
     *
     * @param key         The key to use to identify the HTTP session (session IDs can change)
     * @param wsSession   The WebSocket session
     * @param httpSession The HTTP session
     */
    protected void registerSession(Object key, WsSession wsSession, Object httpSession) {
        super.registerSession(key, wsSession);
        if (wsSession.isOpen() && wsSession.getUserPrincipal() != null && httpSession != null) {
            registerAuthenticatedSession(wsSession, (HttpSession) httpSession);
        }
    }


    /**
     * {@inheritDoc} Overridden to make it visible to other classes in this package.
     */
    @Override
    protected void unregisterSession(Object key, WsSession wsSession) {
        if (wsSession.getUserPrincipalInternal() != null) {
            unregisterAuthenticatedSession(wsSession);
        }
        super.unregisterSession(key, wsSession);
    }


    private void registerAuthenticatedSession(WsSession wsSession, HttpSession httpSession) {
        boolean mustCloseWsSession = false;
        String httpSessionKey = null;

        synchronized (authenticatedSessionMapLock) {
            try {
                boolean mustAddSessionAttribute = false;
                WsHttpSessionBindingListener listener = (WsHttpSessionBindingListener) httpSession
                        .getAttribute(WsHttpSessionBindingListener.class.getCanonicalName());
                if (listener == null) {
                    httpSessionKey = httpSession.getId();
                    mustAddSessionAttribute = true;
                } else {
                    httpSessionKey = listener.getKey();
                }
                if (mustAddSessionAttribute) {
                    /*
                     * It is possible that the session expires concurrently with the attribute being added. Depending on
                     * the exact timing, one of two things will happen. Either an IllegalStateException will be thrown
                     * or the attribute will be added and then immediately removed from the session. Handle both of
                     * these scenarios here.
                     */
                    httpSession.setAttribute(WsHttpSessionBindingListener.class.getCanonicalName(),
                            new WsHttpSessionBindingListener(httpSessionKey));
                    if (httpSession.getAttribute(WsHttpSessionBindingListener.class.getCanonicalName()) == null) {
                        mustCloseWsSession = true;
                    }
                }
            } catch (IllegalStateException ise) {
                // Failing to set the attribute indicates that the session has already expired
                mustCloseWsSession = true;
            }

            if (!mustCloseWsSession) {
                Set<WsSession> wsSessions = httpSessionKeyToWebSocketSession.get(httpSessionKey);
                if (wsSessions == null) {
                    wsSessions = new HashSet<>();
                    httpSessionKeyToWebSocketSession.put(httpSessionKey, wsSessions);
                }
                wsSessions.add(wsSession);
                webSocketSessionToHttpSessionKey.put(wsSession, httpSessionKey);
            }
        }

        if (mustCloseWsSession) {
            closeAuthenticatedWebSocketSession(wsSession);
        }
    }


    private void unregisterAuthenticatedSession(WsSession wsSession) {
        synchronized (authenticatedSessionMapLock) {
            String httpSessionKey = webSocketSessionToHttpSessionKey.remove(wsSession);
            if (httpSessionKey != null) {
                Set<WsSession> wsSessions = httpSessionKeyToWebSocketSession.get(httpSessionKey);
                if (wsSessions != null) {
                    wsSessions.remove(wsSession);
                }
            }
        }
    }


    /**
     * Closes all WebSocket sessions associated with the given authenticated HTTP session.
     *
     * @param httpSessionKey the HTTP session key
     */
    public void handleHttpSessionKeyUnbound(String httpSessionKey) {
        Set<WsSession> wsSessions;

        synchronized (authenticatedSessionMapLock) {
            wsSessions = httpSessionKeyToWebSocketSession.remove(httpSessionKey);
        }

        if (wsSessions != null) {
            for (WsSession wsSession : wsSessions) {
                closeAuthenticatedWebSocketSession(wsSession);
            }
        }
    }


    private void closeAuthenticatedWebSocketSession(WsSession wsSession) {
        try {
            wsSession.close(AUTHENTICATED_HTTP_SESSION_CLOSED);
        } catch (IOException ignore) {
            // Any IOExceptions during close will have been caught and the
            // onError method called.
        }
    }


    private static void validateEncoders(Class<? extends Encoder>[] encoders, InstanceManager instanceManager)
            throws DeploymentException {

        for (Class<? extends Encoder> encoder : encoders) {
            // Need to instantiate encoder to ensure it is valid and that
            // deployment can be failed if it is not. The encoder is then
            // discarded immediately.
            Encoder instance;
            try {
                if (instanceManager == null) {
                    instance = encoder.getConstructor().newInstance();
                } else {
                    instance = (Encoder) instanceManager.newInstance(encoder);
                    instanceManager.destroyInstance(instance);
                }
            } catch (ReflectiveOperationException | NamingException e) {
                throw new DeploymentException(sm.getString("serverContainer.encoderFail", encoder.getName()), e);
            }
        }
    }


    private static class TemplatePathMatch {
        private final ServerEndpointConfig config;
        private final UriTemplate uriTemplate;
        private final boolean fromAnnotatedPojo;

        TemplatePathMatch(ServerEndpointConfig config, UriTemplate uriTemplate, boolean fromAnnotatedPojo) {
            this.config = config;
            this.uriTemplate = uriTemplate;
            this.fromAnnotatedPojo = fromAnnotatedPojo;
        }


        public ServerEndpointConfig getConfig() {
            return config;
        }


        public UriTemplate getUriTemplate() {
            return uriTemplate;
        }


        public boolean isFromAnnotatedPojo() {
            return fromAnnotatedPojo;
        }
    }


    private static class ExactPathMatch {
        private final ServerEndpointConfig config;
        private final boolean fromAnnotatedPojo;

        ExactPathMatch(ServerEndpointConfig config, boolean fromAnnotatedPojo) {
            this.config = config;
            this.fromAnnotatedPojo = fromAnnotatedPojo;
        }


        public ServerEndpointConfig getConfig() {
            return config;
        }


        public boolean isFromAnnotatedPojo() {
            return fromAnnotatedPojo;
        }
    }
}
