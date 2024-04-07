/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.CloseReason;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import jakarta.websocket.server.ServerEndpointConfig;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.websocket.TesterMessageCountClient.TesterProgrammaticEndpoint;
import org.apache.tomcat.websocket.server.Constants;
import org.apache.tomcat.websocket.server.TesterEndpointConfig;
import org.apache.tomcat.websocket.server.WsServerContainer;

public class TestWsSessionSuspendResume extends WebSocketBaseTest {

    @Test
    public void testSuspendResume() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx = getProgrammaticRootContext();
        ctx.addApplicationListener(SuspendResumeConfig.class.getName());

        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();

        ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().build();
        Session wsSession = wsContainer.connectToServer(TesterProgrammaticEndpoint.class, clientEndpointConfig,
                new URI("ws://localhost:" + getPort() + SuspendResumeConfig.PATH));

        CountDownLatch latch = new CountDownLatch(2);
        wsSession.addMessageHandler(String.class, message -> {
            Assert.assertTrue("[echo, echo, echo]".equals(message));
            latch.countDown();
        });
        for (int i = 0; i < 8; i++) {
            wsSession.getBasicRemote().sendText("echo");
        }

        boolean latchResult = latch.await(30, TimeUnit.SECONDS);
        Assert.assertTrue(latchResult);

        wsSession.close();
    }


    public static final class SuspendResumeConfig extends TesterEndpointConfig {
        private static final String PATH = "/echo";

        @Override
        protected Class<?> getEndpointClass() {
            return SuspendResumeEndpoint.class;
        }

        @Override
        protected ServerEndpointConfig getServerEndpointConfig() {
            return ServerEndpointConfig.Builder.create(getEndpointClass(), PATH).build();
        }
    }


    public static final class SuspendResumeEndpoint extends Endpoint {

        @Override
        public void onOpen(Session session, EndpointConfig epc) {
            SuspendResumeMessageProcessor processor = new SuspendResumeMessageProcessor(session, 3);
            session.addMessageHandler(String.class, message -> processor.addMessage(message));
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {
            try {
                session.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onError(Session session, Throwable t) {
            t.printStackTrace();
        }
    }


    private static final class SuspendResumeMessageProcessor {
        private final Session session;
        private final int count;
        private final List<String> messages = new ArrayList<>();

        SuspendResumeMessageProcessor(Session session, int count) {
            this.session = session;
            this.count = count;
        }

        void addMessage(String message) {
            if (messages.size() == count) {
                ((WsSession) session).suspend();
                try {
                    session.getBasicRemote().sendText(messages.toString());
                    messages.clear();
                    ((WsSession) session).resume();
                } catch (IOException e) {
                    Assert.fail();
                }
            } else {
                messages.add(message);
            }
        }
    }


    @Test
    public void testSuspendThenClose() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx = getProgrammaticRootContext();
        ctx.addApplicationListener(SuspendCloseConfig.class.getName());
        ctx.addApplicationListener(WebSocketFastServerTimeout.class.getName());

        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();

        ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().build();
        Session wsSession = wsContainer.connectToServer(TesterProgrammaticEndpoint.class, clientEndpointConfig,
                new URI("ws://localhost:" + getPort() + SuspendResumeConfig.PATH));

        wsSession.getBasicRemote().sendText("start test");

        // Wait for the client response to be received by the server
        int count = 0;
        while (count < 50 && !SuspendCloseEndpoint.isServerSessionFullyClosed()) {
            Thread.sleep(100);
            count ++;
        }
        Assert.assertTrue(SuspendCloseEndpoint.isServerSessionFullyClosed());
    }


    public static final class SuspendCloseConfig extends TesterEndpointConfig {
        private static final String PATH = "/echo";

        @Override
        protected Class<?> getEndpointClass() {
            return SuspendCloseEndpoint.class;
        }

        @Override
        protected ServerEndpointConfig getServerEndpointConfig() {
            return ServerEndpointConfig.Builder.create(getEndpointClass(), PATH).build();
        }
    }


    public static final class SuspendCloseEndpoint extends Endpoint {

        // Yes, a static variable is a hack.
        private static volatile WsSession serverSession;

        @Override
        public void onOpen(Session session, EndpointConfig epc) {
            serverSession = (WsSession) session;
            // Set a short session close timeout (milliseconds)
            serverSession.getUserProperties().put(
                    org.apache.tomcat.websocket.Constants.SESSION_CLOSE_TIMEOUT_PROPERTY, Long.valueOf(2000));
            // Any message will trigger the suspend then close
            serverSession.addMessageHandler(String.class, message -> {
                try {
                    serverSession.getBasicRemote().sendText("server session open");
                    serverSession.getBasicRemote().sendText("suspending server session");
                    serverSession.suspend();
                    serverSession.getBasicRemote().sendText("closing server session");
                    serverSession.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                    // Attempt to make the failure more obvious
                    throw new RuntimeException(ioe);
                }
            });
        }

        @Override
        public void onError(Session session, Throwable t) {
            t.printStackTrace();
        }

        public static boolean isServerSessionFullyClosed() {
            return serverSession != null && serverSession.isClosed();
        }
    }


    public static class WebSocketFastServerTimeout implements ServletContextListener {

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            WsServerContainer container = (WsServerContainer) sce.getServletContext().getAttribute(
                    Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE);
            container.setProcessPeriod(0);
        }
    }
}