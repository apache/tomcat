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

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;
import javax.websocket.DeploymentException;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.WsSession;
import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.apache.tomcat.websocket.pojo.PojoEndpoint;
import org.apache.tomcat.websocket.pojo.PojoMethodMapping;

/**
 * Provides a per class loader (i.e. per web application) instance of a
 * ServerContainer. Web application wide defaults may be configured by setting
 * the following servlet context initialisation parameters to the desired
 * values.
 * <ul>
 * <li>{@link Constants#BINARY_BUFFER_SIZE_SERVLET_CONTEXT_INIT_PARAM}</li>
 * <li>{@link Constants#TEXT_BUFFER_SIZE_SERVLET_CONTEXT_INIT_PARAM}</li>
 * </ul>
 */
public class WsServerContainer extends WsWebSocketContainer
        implements ServerContainer {

    // Needs to be a WeakHashMap to prevent memory leaks when a context is
    // stopped
    private static final Map<ClassLoader,WsServerContainer>
            classLoaderContainerMap = new WeakHashMap<>();
    private static final Object classLoaderContainerMapLock = new Object();
    private static final StringManager sm =
            StringManager.getManager(Constants.PACKAGE_NAME);
    private final Log log = LogFactory.getLog(WsServerContainer.class);


    public static WsServerContainer getServerContainer() {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        WsServerContainer result = null;
        synchronized (classLoaderContainerMapLock) {
            result = classLoaderContainerMap.get(tccl);
            if (result == null) {
                result = new WsServerContainer();
                classLoaderContainerMap.put(tccl, result);
            }
        }
        return result;
    }

    private final WsWriteTimeout wsWriteTimeout = new WsWriteTimeout();

    private volatile ServletContext servletContext = null;
    private final Map<String,ServerEndpointConfig> configMap =
            new ConcurrentHashMap<>();
    private final Map<String,UriTemplate> templateMap =
            new ConcurrentHashMap<>();
    private final Map<String,Class<?>> pojoMap = new ConcurrentHashMap<>();
    private final Map<Class<?>,PojoMethodMapping> pojoMethodMap =
            new ConcurrentHashMap<>();


    public void setServletContext(ServletContext servletContext) {

        if (this.servletContext == servletContext) {
            return;
        }

        this.servletContext = servletContext;

        // Configure servlet context wide defaults
        String value = servletContext.getInitParameter(
                Constants.BINARY_BUFFER_SIZE_SERVLET_CONTEXT_INIT_PARAM);
        if (value != null) {
            setDefaultMaxBinaryMessageBufferSize(Integer.parseInt(value));
        }

        value = servletContext.getInitParameter(
                Constants.TEXT_BUFFER_SIZE_SERVLET_CONTEXT_INIT_PARAM);
        if (value != null) {
            setDefaultMaxTextMessageBufferSize(Integer.parseInt(value));
        }
    }


    /**
     * Published the provided endpoint implementation at the specified path with
     * the specified configuration. {@link #setServletContext(ServletContext)}
     * must be called before calling this method.
     *
     * @param sec   The configuration to use when creating endpoint instances
     * @throws DeploymentException
     */
    @Override
    public void addEndpoint(ServerEndpointConfig sec)
            throws DeploymentException {
        if (servletContext == null) {
            throw new DeploymentException(
                    sm.getString("serverContainer.servletContextMissing"));
        }
        String path = sec.getPath();
        String servletPath = getServletPath(path);
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("serverContainer.endpointDeploy",
                    sec.getEndpointClass(), path,
                    servletContext.getContextPath()));
        }

        // Remove the trailing /* before adding it to the map
        String mapPath = servletPath.substring(0, servletPath.length() - 2);

        if (path.length() > servletPath.length()) {
            templateMap.put(mapPath,
                    new UriTemplate(path.substring(mapPath.length())));
        }

        configMap.put(mapPath, sec);
        addWsServletMapping(servletPath);
    }


    /**
     * Provides the equivalent of {@link #addEndpoint(ServerEndpointConfig)}
     * for publishing plain old java objects (POJOs) that have been annotated as
     * WebSocket endpoints.
     *
     * @param pojo   The annotated POJO
     */
    @Override
    public void addEndpoint(Class<?> pojo) throws DeploymentException {

        ServerEndpoint annotation = pojo.getAnnotation(ServerEndpoint.class);
        if (annotation == null) {
            throw new DeploymentException(
                    sm.getString("serverContainer.missingAnnotation",
                            pojo.getName()));
        }
        String wsPath = annotation.value();

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("serverContainer.pojoDeploy",
                    pojo.getName(), wsPath, servletContext.getContextPath()));
        }

        String servletPath = getServletPath(wsPath);
        // Remove the trailing /* before adding it to the map
        String mapPath = servletPath.substring(0, servletPath.length() - 2);

        if (wsPath.length() > servletPath.length()) {
            templateMap.put(mapPath,
                    new UriTemplate(wsPath.substring(mapPath.length())));
        }

        pojoMap.put(mapPath, pojo);
        pojoMethodMap.put(pojo, new PojoMethodMapping(pojo, wsPath));
        addWsServletMapping(servletPath);
    }


    private void addWsServletMapping(String servletPath) {
        ServletRegistration sr =
                servletContext.getServletRegistration(Constants.SERVLET_NAME);
        if (sr == null) {
            sr = servletContext.addServlet(Constants.SERVLET_NAME,
                    WsServlet.class);
        }
        sr.addMapping(servletPath);
    }


    public ServerEndpointConfig getServerEndpointConfiguration(
            String servletPath, Map<String,String> pathParameters) {
        ServerEndpointConfig sec = configMap.get(servletPath);
        if (sec != null) {
            return sec;
        }
        Class<?> pojo = pojoMap.get(servletPath);
        if (pojo != null) {
            PojoMethodMapping methodMapping = pojoMethodMap.get(pojo);
            if (methodMapping != null) {
                sec = ServerEndpointConfig.Builder.create(
                        pojo, methodMapping.getWsPath()).build();
                sec.getUserProperties().put(
                        PojoEndpoint.POJO_PATH_PARAM_KEY, pathParameters);
                sec.getUserProperties().put(
                        PojoEndpoint.POJO_METHOD_MAPPING_KEY, methodMapping);
                return sec;
            }
        }
        throw new IllegalStateException(sm.getString(
                "serverContainer.missingEndpoint", servletPath));
    }


    public Map<String,String> getPathParameters(String servletPath,
            String pathInfo) {
        UriTemplate template = templateMap.get(servletPath);
        if (template == null) {
            return Collections.EMPTY_MAP;
        } else {
            return template.match(pathInfo);
        }
    }


    protected WsWriteTimeout getTimeout() {
        return wsWriteTimeout;
    }


    /**
     * {@inheritDoc}
     *
     * Overridden to make them visible to other classes in this package.
     */
    @Override
    protected void registerSession(Class<?> endpoint, WsSession wsSession) {
        super.registerSession(endpoint, wsSession);
    }


    /**
     * {@inheritDoc}
     *
     * Overridden to make them visible to other classes in this package.
     */
    @Override
    protected void unregisterSession(Class<?> endpoint, WsSession wsSession) {
        super.unregisterSession(endpoint, wsSession);
    }


    /**
     * Converts a path defined for a WebSocket endpoint into a path that can be
     * used as a servlet mapping.
     *
     * @param wsPath The WebSocket endpoint path to convert
     * @return The servlet mapping
     */
    static String getServletPath(String wsPath) {
        int templateStart = wsPath.indexOf('{');
        if (templateStart == -1) {
            if (wsPath.charAt(wsPath.length() - 1) == '/') {
                return wsPath + '*';
            } else {
                return wsPath + "/*";
            }
        } else {
            String temp = wsPath.substring(0, templateStart);
            if (temp.charAt(temp.length() - 1) == '/') {
                return temp + '*';
            } else {
                return temp.substring(0, temp.lastIndexOf('/') + 1) + '*';
            }
        }
    }
}
