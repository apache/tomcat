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

import java.io.IOException;

import javax.servlet.ServletContextEvent;
import javax.websocket.DeploymentException;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpoint;

public class TesterUriServer {

    public static final String PATH = "/uri";

    public static class Config extends WsContextListener {

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            super.contextInitialized(sce);
            ServerContainer sc =
                    (ServerContainer) sce.getServletContext().getAttribute(
                            Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE);
            try {
                sc.addEndpoint(Uri.class);
            } catch (DeploymentException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ServerEndpoint(PATH)
    public static class Uri {

        @OnMessage
        public void sendUri(Session session, @SuppressWarnings("unused") String message) {
            try {
                session.getBasicRemote().sendText(session.getRequestURI().toString());
            } catch (IOException e) {
                try {
                    session.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        }
    }
}
