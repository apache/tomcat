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

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;
import javax.websocket.DeploymentException;
import javax.websocket.ServerContainer;
import javax.websocket.ServerEndpointConfiguration;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * Provides a per class loader (i.e. per web application) instance of a
 * {@link ServerContainer}.
 */
public class ServerContainerImpl extends ClientContainerImpl implements
        ServerContainer {

    // Needs to be a WeakHashMap to prevent memory leaks when a context is
    // stopped
    private static Map<ClassLoader,ServerContainerImpl> classLoaderContainerMap = new WeakHashMap<>();
    private static Object classLoaderContainerMapLock = new Object();
    private static StringManager sm = StringManager.getManager(Constants.PACKAGE_NAME);
    protected Log log = LogFactory.getLog(ServerContainerImpl.class);


    /**
     * Intended to be used by implementations of
     * {@link javax.websocket.ContainerProvider#getServerContainer()} to obtain
     * the correct {@link ServerContainer} instance.
     */
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
    private volatile ServletContext servletContext = null;
    private Map<String,ServerEndpointConfiguration<?>> configMap = new ConcurrentHashMap<>();
    private Map<String,Class<?>> pojoMap = new ConcurrentHashMap<>();
    private Map<Class<?>,PojoMethodMapping> pojoMethodMap = new ConcurrentHashMap<>();


    private ServerContainerImpl() {
        // Hide default constructor
    }


    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }


    @Override
    public void publishServer(
            Class<? extends ServerEndpointConfiguration<?>> clazz)
            throws DeploymentException {
        if (servletContext == null) {
            throw new IllegalArgumentException(
                    sm.getString("serverContainer.servletContextMissing"));
        }
        ServerEndpointConfiguration<?> sec = null;
        try {
            sec = clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new DeploymentException(sm.getString("sci.newInstance.fail",
                    clazz.getName()), e);
        }
        String path = sec.getPath();
        String mappingPath = Util.getServletMappingPath(path);
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("serverContainer.endpointDeploy",
                    clazz.getName(), path, servletContext.getContextPath()));
        }
        configMap.put(mappingPath.substring(0, mappingPath.length() - 2), sec);
        addWsServletMapping(mappingPath);
    }


    /**
     * Provides the equivalent of {@link #publishServer(Class)} for publishing
     * plain old java objects (POJOs) that have been annotated as WebSocket
     * endpoints.
     *
     * @param pojo The annotated POJO
     * @param ctxt The ServletContext the endpoint is to be published in
     * @param path The path at which the endpoint is to be published
     */
    public void publishServer(Class<?> pojo, ServletContext ctxt, String path) {
        if (ctxt == null) {
            throw new IllegalArgumentException(
                    sm.getString("serverContainer.servletContextMissing"));
        }
        // Set the ServletContext if it hasn't already been set
        if (servletContext == null) {
            servletContext = ctxt;
        } else if (ctxt != servletContext) {
            // Should never happen
            throw new IllegalStateException(sm.getString(
                    "serverContainer.servletContextMismatch", path,
                    servletContext.getContextPath(), ctxt.getContextPath()));
        }
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("serverContainer.pojoDeploy",
                    pojo.getName(), path, servletContext.getContextPath()));
        }
        String mappingPath = Util.getServletMappingPath(path);
        pojoMap.put(mappingPath.substring(0, mappingPath.length() - 2), pojo);
        pojoMethodMap.put(pojo, new PojoMethodMapping(pojo, path, mappingPath));
        addWsServletMapping(mappingPath);
    }


    private void addWsServletMapping(String mapping) {
        ServletRegistration sr = servletContext.getServletRegistration(Constants.SERVLET_NAME);
        if (sr == null) {
            sr = servletContext.addServlet(Constants.SERVLET_NAME,
                    WsServlet.class);
        }
        sr.addMapping(mapping);
    }


    public ServerEndpointConfiguration<?> getServerEndpointConfiguration(
            String servletPath, String pathInfo) {
        ServerEndpointConfiguration<?> sec = configMap.get(servletPath);
        if (sec != null) {
            return sec;
        }
        Class<?> pojo = pojoMap.get(servletPath);
        if (pojo != null) {
            PojoMethodMapping mapping = pojoMethodMap.get(pojo);
            if (mapping != null) {
                PojoServerEndpointConfiguration pojoSec = new PojoServerEndpointConfiguration(
                        pojo, mapping, servletPath, pathInfo);
                return pojoSec;
            }
        }
        throw new IllegalStateException(sm.getString(
                "serverContainer.missingEndpoint", servletPath));
    }
}
