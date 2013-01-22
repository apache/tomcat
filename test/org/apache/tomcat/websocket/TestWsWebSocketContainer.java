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
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.websocket.ContainerProvider;
import javax.websocket.DefaultClientConfiguration;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.websocket.WebSocketMessage;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.websocket.server.ServerContainerImpl;

public class TestWsWebSocketContainer extends TomcatBaseTest {

    private static final String MESSAGE_STRING_1 = "qwerty";

    @Test
    public void testConnectToServerEndpoint() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));
        ctx.addApplicationListener(EchoConfig.class.getName());

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider.getClientContainer();
        Session wsSession = wsContainer.connectToServer(TesterEndpoint.class,
                new DefaultClientConfiguration(), new URI("http://localhost:" +
                        getPort() + EchoConfig.PATH));
        TesterMessageHandlerString handler = new TesterMessageHandlerString(1);
        wsSession.addMessageHandler(handler);
        wsSession.getRemote().sendString(MESSAGE_STRING_1);

        boolean latchResult = handler.getLatch().await(100, TimeUnit.SECONDS);

        Assert.assertTrue(latchResult);

        List<String> messages = handler.getMessages();
        Assert.assertEquals(1, messages.size());
        Assert.assertEquals(MESSAGE_STRING_1, messages.get(0));
    }

    @Test(expected=javax.websocket.DeploymentException.class)
    public void testConnectToServerEndpointInvalidScheme() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));
        ctx.addApplicationListener(EchoConfig.class.getName());

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider.getClientContainer();
        wsContainer.connectToServer(TesterEndpoint.class,
                new DefaultClientConfiguration(), new URI("ftp://localhost:" +
                        getPort() + EchoConfig.PATH));
    }

    @Test(expected=javax.websocket.DeploymentException.class)
    public void testConnectToServerEndpointNoHost() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));
        ctx.addApplicationListener(EchoConfig.class.getName());

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider.getClientContainer();
        wsContainer.connectToServer(TesterEndpoint.class,
                new DefaultClientConfiguration(),
                new URI("http://" + EchoConfig.PATH));
    }

    private static class TesterMessageHandlerString
            implements MessageHandler.Basic<String> {

        private final CountDownLatch latch;

        private List<String> messages = new ArrayList<>();

        public TesterMessageHandlerString(int latchCount) {
            if (latchCount > 0) {
                latch = new CountDownLatch(latchCount);
            } else {
                latch = null;
            }
        }

        public List<String> getMessages() {
            return messages;
        }

        public CountDownLatch getLatch() {
            return latch;
        }

        @Override
        public void onMessage(String message) {
            if (latch != null) {
                latch.countDown();
            }
            messages.add(message);
        }
    }

    public static class TesterEndpoint extends Endpoint {

       @Override
        public void onOpen(Session session, EndpointConfiguration config) {
            // NO-OP
        }
    }

    public static class EchoConfig implements ServletContextListener {

        public static final String PATH = "/echo";

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            ServerContainerImpl sc = ServerContainerImpl.getServerContainer();
            sc.publishServer(Echo.class, sce.getServletContext(), PATH);
        }

        @Override
        public void contextDestroyed(ServletContextEvent sce) {
            // NO-OP
        }
    }

    public static class Echo {
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
    }
}
