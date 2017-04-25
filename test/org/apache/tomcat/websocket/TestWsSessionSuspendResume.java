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

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.websocket.server.ServerEndpointConfig;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.websocket.TesterMessageCountClient.TesterProgrammaticEndpoint;
import org.apache.tomcat.websocket.server.TesterEndpointConfig;

public class TestWsSessionSuspendResume extends WebSocketBaseTest {

    @Test
    public void test() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx = tomcat.addContext("", null);
        ctx.addApplicationListener(Config.class.getName());

        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();

        ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().build();
        Session wsSession = wsContainer.connectToServer(
                TesterProgrammaticEndpoint.class,
                clientEndpointConfig,
                new URI("ws://localhost:" + getPort() + Config.PATH));

        CountDownLatch latch = new CountDownLatch(2);
        wsSession.addMessageHandler(String.class, message -> {
            Assert.assertTrue("[echo, echo, echo]".equals(message));
            latch.countDown();
        });
        for (int i = 0; i < 8; i++) {
            wsSession.getAsyncRemote().sendText("echo");
        }

        boolean latchResult = latch.await(30, TimeUnit.SECONDS);
        Assert.assertTrue(latchResult);

        wsSession.close();
    }


    public static final class Config extends TesterEndpointConfig {
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
        public void onOpen(Session session, EndpointConfig  epc) {
            MessageProcessor processor = new MessageProcessor(session, 3);
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


    private static final class MessageProcessor {
        private final Session session;
        private final int count;
        private final List<String> messages = new ArrayList<>();

        MessageProcessor(Session session, int count) {
            this.session = session;
            this.count = count;
        }

        void addMessage(String message) {
            if (messages.size() == count) {
                ((WsSession) session).suspend();
                session.getAsyncRemote().sendText(messages.toString(), result -> {
                    ((WsSession) session).resume();
                    Assert.assertTrue(result.isOK());
                });
                messages.clear();
            } else {
                messages.add(message);
            }
        }
    }
}