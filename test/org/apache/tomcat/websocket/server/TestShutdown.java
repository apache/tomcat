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

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.EndpointConfig;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.websocket.server.ServerEndpoint;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.websocket.TesterMessageCountClient.BasicText;
import org.apache.tomcat.websocket.TesterMessageCountClient.TesterProgrammaticEndpoint;
import org.apache.tomcat.websocket.WebSocketBaseTest;

/**
 * Tests inspired by https://bz.apache.org/bugzilla/show_bug.cgi?id=58835 to
 * check that WebSocket connections are closed gracefully on Tomcat shutdown.
 */
public class TestShutdown extends WebSocketBaseTest {

    @Test
    public void testShutdownBufferedMessages() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = tomcat.addContext("", null);
        ctx.addApplicationListener(EchoBufferedConfig.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMapping("/", "default");

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();
        ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().build();
        Session wsSession = wsContainer.connectToServer(
                TesterProgrammaticEndpoint.class,
                clientEndpointConfig,
                new URI("ws://localhost:" + getPort() + "/test"));
        CountDownLatch latch = new CountDownLatch(1);
        BasicText handler = new BasicText(latch);
        wsSession.addMessageHandler(handler);
        wsSession.getBasicRemote().sendText("Hello");

        int count = 0;
        while (count < 10 && EchoBufferedEndpoint.messageCount.get() == 0) {
            Thread.sleep(200);
            count++;
        }
        Assert.assertNotEquals("Message not received by server",
                EchoBufferedEndpoint.messageCount.get(), 0);

        tomcat.stop();

        Assert.assertTrue("Latch expired waiting for message", latch.await(10, TimeUnit.SECONDS));
    }

    public static class EchoBufferedConfig extends TesterEndpointConfig {

        @Override
        protected Class<?> getEndpointClass() {
            return EchoBufferedEndpoint.class;
        }

    }

    @ServerEndpoint("/test")
    public static class EchoBufferedEndpoint {

        private static AtomicLong messageCount = new AtomicLong(0);

        @OnOpen
        public void onOpen(Session session, @SuppressWarnings("unused") EndpointConfig  epc)
                throws IOException {
            session.getAsyncRemote().setBatchingAllowed(true);
        }

        @OnMessage
        public void onMessage(Session session, String msg) throws IOException {
            messageCount.incrementAndGet();
            session.getBasicRemote().sendText(msg);
        }
    }
}
