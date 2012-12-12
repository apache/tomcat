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

import javax.websocket.DefaultServerConfiguration;
import javax.websocket.Endpoint;
import javax.websocket.EndpointFactory;

public class PojoServerEndpointConfiguration extends
        DefaultServerConfiguration<Endpoint> {

    @Override
    public boolean checkOrigin(String originHeaderValue) {
        // Allow all
        return true;
    }
    private final EndpointFactory<Endpoint> endpointFactory;
    private final String servletPath;


    PojoServerEndpointConfiguration(Class<?> pojo,
            PojoMethodMapping methodMapping, String servletPath, String pathInfo) {
        this.endpointFactory = new PojoEndpointFactory(pojo, methodMapping,
                pathInfo);
        this.servletPath = servletPath;
    }


    @Override
    public EndpointFactory<Endpoint> getEndpointFactory() {
        return endpointFactory;
    }


    @Override
    public String getPath() {
        return servletPath;
    }

    private static class PojoEndpointFactory implements
            EndpointFactory<Endpoint> {

        private final Class<?> pojo;
        private final PojoMethodMapping methodMapping;
        private final String pathInfo;


        public PojoEndpointFactory(Class<?> pojo,
                PojoMethodMapping methodMapping, String pathInfo) {
            this.pojo = pojo;
            this.methodMapping = methodMapping;
            this.pathInfo = pathInfo;
        }


        @Override
        public Endpoint createEndpoint() {
            Endpoint ep;
            try {
                ep = new WsEndpointPojo(pojo, methodMapping, pathInfo);
            } catch (InstantiationException | IllegalAccessException e) {
                throw new IllegalArgumentException(e);
            }
            return ep;
        }
    }
}
