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
package websocket.echo;

import javax.servlet.ServletContextEvent;
import javax.servlet.annotation.WebListener;
import javax.websocket.DeploymentException;
import javax.websocket.server.ServerEndpointConfigurationBuilder;

import org.apache.tomcat.websocket.server.ServerContainerImpl;
import org.apache.tomcat.websocket.server.WsListener;

@WebListener
public class WsConfigListener extends WsListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        super.contextInitialized(sce);
        ServerContainerImpl sc = ServerContainerImpl.getServerContainer();
        try {
            sc.deploy(ServerEndpointConfigurationBuilder.create(
                    EchoEndpoint.class, "/websocket/echoProgrammatic").build());
        } catch (DeploymentException e) {
            throw new IllegalStateException(e);
        }
    }
}
