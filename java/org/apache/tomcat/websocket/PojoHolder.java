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

import javax.naming.NamingException;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;

import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.pojo.PojoEndpointClient;

public class PojoHolder implements ClientEndpointHolder {

    private static final StringManager sm = StringManager.getManager(PojoHolder.class);

    private final Object pojo;
    private final ClientEndpointConfig clientEndpointConfig;


    public PojoHolder(Object pojo, ClientEndpointConfig clientEndpointConfig) {
        this.pojo = pojo;
        this.clientEndpointConfig = clientEndpointConfig;
    }


    @Override
    public String getClassName() {
        return pojo.getClass().getName();
    }


    @Override
    public Endpoint getInstance(InstanceManager instanceManager) throws DeploymentException {
        if (instanceManager != null) {
            try {
                instanceManager.newInstance(pojo);
            } catch (ReflectiveOperationException | NamingException e) {
                throw new DeploymentException(sm.getString("clientEndpointHolder.instanceRegistrationFailed"), e);
            }
        }
        return new PojoEndpointClient(pojo, clientEndpointConfig.getDecoders(), instanceManager);
    }
}
