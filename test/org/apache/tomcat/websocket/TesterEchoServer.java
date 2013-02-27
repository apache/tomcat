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
package org.apache.tomcat.websocket;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.servlet.ServletContextEvent;
import javax.websocket.DeploymentException;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.apache.tomcat.websocket.server.ServerContainerImpl;
import org.apache.tomcat.websocket.server.WsListener;

public class TesterEchoServer {

    public static class Config extends WsListener {

        public static final String PATH_ASYNC = "/echoAsync";
        public static final String PATH_BASIC = "/echoBasic";

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            super.contextInitialized(sce);
            ServerContainerImpl sc = ServerContainerImpl.getServerContainer();
            sc.setServletContext(sce.getServletContext());
            try {
                sc.deploy(Async.class);
                sc.deploy(Basic.class);
            } catch (DeploymentException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @ServerEndpoint("/echoAsync")
    public static class Async {

        @OnMessage
        public void echoTextMessage(Session session, String msg, boolean last) {
            try {
                session.getBasicRemote().sendText(msg, last);
            } catch (IOException e) {
                try {
                    session.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        }


        @OnMessage
        public void echoBinaryMessage(Session session, ByteBuffer msg,
                boolean last) {
            try {
                session.getBasicRemote().sendBinary(msg, last);
            } catch (IOException e) {
                try {
                    session.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        }
    }


    @ServerEndpoint("/echoBasic")
    public static class Basic {
        @OnMessage
        public void echoTextMessage(Session session, String msg) {
            try {
                session.getBasicRemote().sendText(msg);
            } catch (IOException e) {
                try {
                    session.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        }


        @OnMessage
        public void echoBinaryMessage(Session session, ByteBuffer msg) {
            try {
                session.getBasicRemote().sendBinary(msg);
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
