/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.websocket.server;

import javax.servlet.ServletContextEvent;
import javax.websocket.DeploymentException;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;

public abstract class TesterEndpointConfig extends WsContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        super.contextInitialized(sce);

        ServerContainer sc = (ServerContainer) sce.getServletContext().getAttribute(
                Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE);

        try {
            ServerEndpointConfig sec = getServerEndpointConfig();
            if (sec == null) {
                sc.addEndpoint(getEndpointClass());
            } else {
                sc.addEndpoint(sec);
            }
        } catch (DeploymentException e) {
            throw new RuntimeException(e);
        }
    }


    protected Class<?> getEndpointClass() {
        return null;
    }


    protected ServerEndpointConfig getServerEndpointConfig() {
        return null;
    }
}
