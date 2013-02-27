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

import java.util.Set;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.HandlesTypes;
import javax.websocket.server.ServerEndpoint;

/**
 * Registers an interest in any class that is annotated with
 * {@link ServerEndpoint} so that Endpoint can be published via the WebSocket
 * server.
 */
@HandlesTypes({ServerEndpoint.class})
public class WsSci implements ServletContainerInitializer {

    @Override
    public void onStartup(Set<Class<?>> clazzes, ServletContext ctx)
            throws ServletException {

        ctx.addListener(WsListener.class);

        if (clazzes == null || clazzes.size() == 0) {
            return;
        }

        ServerContainerImpl sc = ServerContainerImpl.getServerContainer();
        sc.setServletContext(ctx);
        for (Class<?> clazz : clazzes) {
            sc.publishServer(clazz);
        }
    }
}
