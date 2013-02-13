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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.server.ServerEndpointConfiguration;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.WsSession;
import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.apache.tomcat.websocket.pojo.PojoEndpointConfiguration;
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
public class ServerContainerImpl extends WsWebSocketContainer {

    // Needs to be a WeakHashMap to prevent memory leaks when a context is
    // stopped
    private static Map<ClassLoader,ServerContainerImpl> classLoaderContainerMap =
            new WeakHashMap<>();
    private static Object classLoaderContainerMapLock = new Object();
    private static StringManager sm =
            StringManager.getManager(Constants.PACKAGE_NAME);
    protected final Log log = LogFactory.getLog(ServerContainerImpl.class);


    public static ServerContainerImpl getServerContainer() {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        ServerContainerImpl result = null;
        synchronized (classLoaderContainerMapLock) {
            result = classLoaderContainerMap.get(tccl);
            if (result == null) {
                result = new ServerContainerImpl();
                classLoaderContainerMap.put(tccl, result);
            }
        }
        return result;
    }

    private final WsTimeout wsTimeout;
    private final Thread timeoutThread;

    private volatile ServletContext servletContext = null;
    private Map<String,ServerEndpointConfiguration> configMap =
            new ConcurrentHashMap<>();
    private Map<String,Class<?>> pojoMap = new ConcurrentHashMap<>();
    private Map<Class<?>,PojoMethodMapping> pojoMethodMap =
            new ConcurrentHashMap<>();


    private ServerContainerImpl() {
        wsTimeout = new WsTimeout();
        timeoutThread = new Thread(wsTimeout);
        timeoutThread.setName(WsTimeout.THREAD_NAME_PREFIX + this);
        timeoutThread.start();
    }


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

        // Update the timeout thread name
        timeoutThread.setName(
                WsTimeout.THREAD_NAME_PREFIX + servletContext.getContextPath());
    }


    /**
     * Published the provided endpoint implementation at the specified path with
     * the specified configuration. {@link #setServletContext(ServletContext)}
     * must be called before calling this method.
     *
     * @param endpointClass The WebSocket server implementation to publish
     * @param path          The path to publish the implementation at
     * @param configClass   The configuration to use when creating endpoint
     *                          instances
     * @throws DeploymentException
     */
    public void publishServer(Class<? extends Endpoint> endpointClass,
            String path,
            Class<? extends ServerEndpointConfiguration> configClass)
            throws DeploymentException {
        if (servletContext == null) {
            throw new IllegalArgumentException(
                    sm.getString("serverContainer.servletContextMissing"));
        }
        ServerEndpointConfiguration sec = null;
        try {
            Constructor<? extends ServerEndpointConfiguration> c =
                    configClass.getConstructor(Class.class, String.class);
            sec = c.newInstance(endpointClass, path);
        } catch (InstantiationException | IllegalAccessException |
                NoSuchMethodException | SecurityException |
                IllegalArgumentException | InvocationTargetException e) {
            throw new DeploymentException(sm.getString("sci.newInstance.fail",
                    endpointClass.getName()), e);
        }
        String servletPath = getServletPath(path);
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("serverContainer.endpointDeploy",
                    endpointClass.getName(), path,
                    servletContext.getContextPath()));
        }
        configMap.put(servletPath.substring(0, servletPath.length() - 2), sec);
        addWsServletMapping(servletPath);
    }


    /**
     * Provides the equivalent of {@link #publishServer(Class,String,Class)} for
     * publishing plain old java objects (POJOs) that have been annotated as
     * WebSocket endpoints.
     *
     * @param pojo   The annotated POJO
     * @param ctxt   The ServletContext the endpoint is to be published in
     * @param wsPath The path at which the endpoint is to be published
     */
    public void publishServer(Class<?> pojo, ServletContext ctxt,
            String wsPath) {
        if (ctxt == null) {
            throw new IllegalArgumentException(
                    sm.getString("serverContainer.servletContextMissing"));
        }
        // Set the ServletContext if it hasn't already been set
        if (servletContext == null) {
            setServletContext(ctxt);
        } else if (ctxt != servletContext) {
            // Should never happen
            throw new IllegalStateException(sm.getString(
                    "serverContainer.servletContextMismatch", wsPath,
                    servletContext.getContextPath(), ctxt.getContextPath()));
        }
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("serverContainer.pojoDeploy",
                    pojo.getName(), wsPath, servletContext.getContextPath()));
        }
        String servletPath = getServletPath(wsPath);
        // Remove the trailing /* before adding it to the map
        pojoMap.put(servletPath.substring(0, servletPath.length() - 2), pojo);
        pojoMethodMap.put(pojo,
                new PojoMethodMapping(pojo, wsPath, servletPath));
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


    public ServerEndpointConfiguration getServerEndpointConfiguration(
            String servletPath, String pathInfo) {
        ServerEndpointConfiguration sec = configMap.get(servletPath);
        if (sec != null) {
            return sec;
        }
        Class<?> pojo = pojoMap.get(servletPath);
        if (pojo != null) {
            PojoMethodMapping methodMapping = pojoMethodMap.get(pojo);
            if (methodMapping != null) {
                PojoEndpointConfiguration pojoSec =
                        new PojoEndpointConfiguration(pojo, methodMapping,
                                pathInfo);
                return pojoSec;
            }
        }
        throw new IllegalStateException(sm.getString(
                "serverContainer.missingEndpoint", servletPath));
    }


    protected WsTimeout getTimeout() {
        return wsTimeout;
    }


    protected void stop() {
        wsTimeout.stop();
        int count = 0;
        while (count < 50 && timeoutThread.isAlive()) {
            count ++;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
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
