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

import javax.websocket.server.DefaultServerConfiguration;

/**
 * Provides the configuration for POJOs annotated at WebSocket endpoints. It
 * provides the means, via casting, of new {@link WsEndpointPojo} instances
 * obtaining POJO endpoint specific configuration settings such as the mapping
 * of onXxx calls to POJO methods.
 */
public class PojoEndpointConfiguration extends DefaultServerConfiguration {

    private final Class<?> pojoClass;
    private final PojoMethodMapping methodMapping;
    private final String servletPath;
    private final String pathInfo;


    PojoEndpointConfiguration(Class<?> pojoClass,
            PojoMethodMapping methodMapping, String servletPath,
            String pathInfo) {
        super(WsEndpointPojo.class, methodMapping.getMappingPath());
        this.pojoClass = pojoClass;
        this.methodMapping = methodMapping;
        this.servletPath = servletPath;
        this.pathInfo = pathInfo;
    }


    @Override
    public boolean checkOrigin(String originHeaderValue) {
        // Allow all
        return true;
    }


    @Override
    public String getPath() {
        return servletPath;
    }


    public Object getPojo() {
        try {
            return pojoClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException(e);
        }
    }


    public String getPathInfo() {
        return pathInfo;
    }


    public PojoMethodMapping getMethodMapping() {
        return methodMapping;
    }
}
