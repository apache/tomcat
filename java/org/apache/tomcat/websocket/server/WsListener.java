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

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * This will be added automatically to a {@link javax.servlet.ServletContext} by
 * the {@link WsSci}. If the {@link WsSci} is disabled, this listener must be
 * added manually to every {@link javax.servlet.ServletContext} that uses
 * WebSocket.
 */
public class WsListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServerContainerImpl sc = ServerContainerImpl.getServerContainer();
        sc.setServletContext(sce.getServletContext());
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        ServerContainerImpl sc = ServerContainerImpl.getServerContainer();
        sc.stop();
    }
}
