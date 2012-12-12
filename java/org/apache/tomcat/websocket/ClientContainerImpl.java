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

import java.net.URI;
import java.util.Set;

import javax.websocket.ClientContainer;
import javax.websocket.ClientEndpointConfiguration;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.Session;

public class ClientContainerImpl implements ClientContainer {

    @Override
    public Session connectToServer(Object endpoint, URI path)
            throws DeploymentException {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public Session connectToServer(Endpoint endpoint,
            ClientEndpointConfiguration clientEndpointConfiguration, URI path)
            throws DeploymentException {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public Set<Session> getOpenSessions() {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public long getMaxSessionIdleTimeout() {
        // TODO Auto-generated method stub
        return 0;
    }


    @Override
    public void setMaxSessionIdleTimeout(long timeout) {
        // TODO Auto-generated method stub
    }


    @Override
    public long getMaxBinaryMessageBufferSize() {
        // TODO Auto-generated method stub
        return 0;
    }


    @Override
    public void setMaxBinaryMessageBufferSize(long max) {
        // TODO Auto-generated method stub
    }


    @Override
    public long getMaxTextMessageBufferSize() {
        // TODO Auto-generated method stub
        return 0;
    }


    @Override
    public void setMaxTextMessageBufferSize(long max) {
        // TODO Auto-generated method stub
    }


    @Override
    public Set<String> getInstalledExtensions() {
        // TODO Auto-generated method stub
        return null;
    }
}
