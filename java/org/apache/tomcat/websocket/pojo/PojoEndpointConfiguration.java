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
package org.apache.tomcat.websocket.pojo;

import java.util.Map;

import javax.websocket.server.DefaultServerConfiguration;

/**
 * Provides the configuration for POJOs annotated at WebSocket endpoints. It
 * provides the means, via casting, of new {@link PojoEndpoint} instances
 * obtaining POJO endpoint specific configuration settings such as the mapping
 * of onXxx calls to POJO methods.
 */
public class PojoEndpointConfiguration extends DefaultServerConfiguration {

    private final Class<?> pojoClass;
    private final PojoMethodMapping methodMapping;
    private final Map<String,String> pathParameters;


    public PojoEndpointConfiguration(Class<?> pojoClass,
            PojoMethodMapping methodMapping, Map<String,String> pathParameters) {
        super(PojoEndpoint.class, methodMapping.getWsPath());
        this.pojoClass = pojoClass;
        this.methodMapping = methodMapping;
        this.pathParameters = pathParameters;
    }


    public Object createPojo() {
        try {
            return pojoClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException(e);
        }
    }


    public Map<String,String> getPathParameters() {
        return pathParameters;
    }


    public PojoMethodMapping getMethodMapping() {
        return methodMapping;
    }
}
