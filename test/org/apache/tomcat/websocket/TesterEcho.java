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
import javax.servlet.ServletContextListener;
import javax.websocket.Session;
import javax.websocket.WebSocketMessage;

import org.apache.tomcat.websocket.server.ServerContainerImpl;

public class TesterEcho {

    public static class Config implements ServletContextListener {

        public static final String PATH_ASYNC = "/echoAsync";
        public static final String PATH_BASIC = "/echoBasic";

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            ServerContainerImpl sc = ServerContainerImpl.getServerContainer();
            sc.publishServer(Async.class, sce.getServletContext(), PATH_ASYNC);
            sc.publishServer(
                    Basic.class, sce.getServletContext(), PATH_BASIC);
        }

        @Override
        public void contextDestroyed(ServletContextEvent sce) {
            // NO-OP
        }
    }

    public static class Async {

        @WebSocketMessage
        public void echoTextMessage(Session session, String msg, boolean last) {
            try {
                session.getRemote().sendPartialString(msg, last);
            } catch (IOException e) {
                try {
                    session.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        }


        @WebSocketMessage
        public void echoBinaryMessage(Session session, ByteBuffer msg,
                boolean last) {
            try {
                session.getRemote().sendPartialBytes(msg, last);
            } catch (IOException e) {
                try {
                    session.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        }
    }

    public static class Basic {
        @WebSocketMessage
        public void echoTextMessage(Session session, String msg) {
            try {
                session.getRemote().sendString(msg);
            } catch (IOException e) {
                try {
                    session.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        }


        @WebSocketMessage
        public void echoBinaryMessage(Session session, ByteBuffer msg) {
            try {
                session.getRemote().sendBytes(msg);
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