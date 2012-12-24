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

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.websocket.DeploymentException;
import javax.websocket.server.DefaultServerConfiguration;

import org.apache.tomcat.websocket.ServerContainerImpl;

@WebListener
public class WsConfigListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServerContainerImpl sc = ServerContainerImpl.getServerContainer();
        ServletContext servletContext = sce.getServletContext();
        sc.setServletContext(servletContext);
        try {
            sc.publishServer(EchoEndpoint.class, "/websocket/echoProgrammatic",
                    DefaultServerConfiguration.class);
        } catch (DeploymentException e) {
            throw new IllegalStateException(e);
        }

        String strReadBufferSize =
                servletContext.getInitParameter("wsReadBufferSize");
        if (strReadBufferSize != null) {
            sc.setReadBufferSize(Integer.valueOf(strReadBufferSize).intValue());
        }
    }


    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // NO-OP
    }
}
