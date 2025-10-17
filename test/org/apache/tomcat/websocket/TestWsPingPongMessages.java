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
package org.apache.tomcat.websocket;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.PongMessage;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.websocket.TesterMessageCountClient.TesterEndpoint;
import org.apache.tomcat.websocket.TesterMessageCountClient.TesterProgrammaticEndpoint;


public class TestWsPingPongMessages extends WebSocketBaseTest {

    ByteBuffer applicationData = ByteBuffer.wrap("mydata".getBytes(StandardCharsets.UTF_8));

    @Test
    public void testPingPongMessages() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = getProgrammaticRootContext();
        ctx.addApplicationListener(TesterEchoServer.Config.class.getName());

        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();

        Session wsSession = wsContainer.connectToServer(TesterProgrammaticEndpoint.class,
                ClientEndpointConfig.Builder.create().build(),
                new URI("ws://localhost:" + getPort() + TesterEchoServer.Config.PATH_ASYNC));

        CountDownLatch latch = new CountDownLatch(1);
        TesterEndpoint tep = (TesterEndpoint) wsSession.getUserProperties().get("endpoint");
        tep.setLatch(latch);

        PongMessageHandler handler = new PongMessageHandler(latch);
        wsSession.addMessageHandler(handler);
        wsSession.getBasicRemote().sendPing(applicationData);

        boolean latchResult = handler.getLatch().await(10, TimeUnit.SECONDS);
        Assert.assertTrue(latchResult);
        Assert.assertArrayEquals(applicationData.array(), (handler.getMessages().peek()).getApplicationData().array());
    }

    public static class PongMessageHandler extends TesterMessageCountClient.BasicHandler<PongMessage> {
        public PongMessageHandler(CountDownLatch latch) {
            super(latch);
        }

        @Override
        public void onMessage(PongMessage message) {
            getMessages().add(message);
            if (getLatch() != null) {
                getLatch().countDown();
            }
        }
    }
}