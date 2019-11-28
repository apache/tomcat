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

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.HandlesTypes;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Endpoint;
import jakarta.websocket.server.ServerApplicationConfig;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;

import org.apache.tomcat.util.compat.JreCompat;

/**
 * Registers an interest in any class that is annotated with
 * {@link ServerEndpoint} so that Endpoint can be published via the WebSocket
 * server.
 */
@HandlesTypes({ServerEndpoint.class, ServerApplicationConfig.class,
        Endpoint.class})
public class WsSci implements ServletContainerInitializer {

    @Override
    public void onStartup(Set<Class<?>> clazzes, ServletContext ctx)
            throws ServletException {

        WsServerContainer sc = init(ctx, true);

        if (clazzes == null || clazzes.size() == 0) {
            return;
        }

        // Group the discovered classes by type
        Set<ServerApplicationConfig> serverApplicationConfigs = new HashSet<>();
        Set<Class<? extends Endpoint>> scannedEndpointClazzes = new HashSet<>();
        Set<Class<?>> scannedPojoEndpoints = new HashSet<>();

        try {
            // wsPackage is "jakarta.websocket."
            String wsPackage = ContainerProvider.class.getName();
            wsPackage = wsPackage.substring(0, wsPackage.lastIndexOf('.') + 1);
            for (Class<?> clazz : clazzes) {
                JreCompat jreCompat = JreCompat.getInstance();
                int modifiers = clazz.getModifiers();
                if (!Modifier.isPublic(modifiers) ||
                        Modifier.isAbstract(modifiers) ||
                        Modifier.isInterface(modifiers) ||
                        !jreCompat.isExported(clazz)) {
                    // Non-public, abstract, interface or not in an exported
                    // package (Java 9+) - skip it.
                    continue;
                }
                // Protect against scanning the WebSocket API JARs
                if (clazz.getName().startsWith(wsPackage)) {
                    continue;
                }
                if (ServerApplicationConfig.class.isAssignableFrom(clazz)) {
                    serverApplicationConfigs.add(
                            (ServerApplicationConfig) clazz.getConstructor().newInstance());
                }
                if (Endpoint.class.isAssignableFrom(clazz)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends Endpoint> endpoint =
                            (Class<? extends Endpoint>) clazz;
                    scannedEndpointClazzes.add(endpoint);
                }
                if (clazz.isAnnotationPresent(ServerEndpoint.class)) {
                    scannedPojoEndpoints.add(clazz);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new ServletException(e);
        }

        // Filter the results
        Set<ServerEndpointConfig> filteredEndpointConfigs = new HashSet<>();
        Set<Class<?>> filteredPojoEndpoints = new HashSet<>();

        if (serverApplicationConfigs.isEmpty()) {
            filteredPojoEndpoints.addAll(scannedPojoEndpoints);
        } else {
            for (ServerApplicationConfig config : serverApplicationConfigs) {
                Set<ServerEndpointConfig> configFilteredEndpoints =
                        config.getEndpointConfigs(scannedEndpointClazzes);
                if (configFilteredEndpoints != null) {
                    filteredEndpointConfigs.addAll(configFilteredEndpoints);
                }
                Set<Class<?>> configFilteredPojos =
                        config.getAnnotatedEndpointClasses(
                                scannedPojoEndpoints);
                if (configFilteredPojos != null) {
                    filteredPojoEndpoints.addAll(configFilteredPojos);
                }
            }
        }

        try {
            // Deploy endpoints
            for (ServerEndpointConfig config : filteredEndpointConfigs) {
                sc.addEndpoint(config);
            }
            // Deploy POJOs
            for (Class<?> clazz : filteredPojoEndpoints) {
                sc.addEndpoint(clazz, true);
            }
        } catch (DeploymentException e) {
            throw new ServletException(e);
        }
    }


    static WsServerContainer init(ServletContext servletContext,
            boolean initBySciMechanism) {

        WsServerContainer sc = new WsServerContainer(servletContext);

        servletContext.setAttribute(
                Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE, sc);

        servletContext.addListener(new WsSessionListener(sc));
        // Can't register the ContextListener again if the ContextListener is
        // calling this method
        if (initBySciMechanism) {
            servletContext.addListener(new WsContextListener());
        }

        return sc;
    }
}
