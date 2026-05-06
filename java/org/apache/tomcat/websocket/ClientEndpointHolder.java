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

import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;

import org.apache.tomcat.InstanceManager;

/**
 * Holds a client endpoint and provides access to its class name and instance.
 */
public interface ClientEndpointHolder {
    /**
     * Returns the fully qualified class name of the endpoint.
     *
     * @return the class name
     */
    String getClassName();

    /**
     * Returns an instance of the endpoint using the given instance manager.
     *
     * @param instanceManager the instance manager to create the endpoint
     *
     * @return the endpoint instance
     *
     * @throws DeploymentException if the endpoint cannot be instantiated
     */
    Endpoint getInstance(InstanceManager instanceManager) throws DeploymentException;
}
