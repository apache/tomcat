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

import java.net.URI;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.AbstractProtocol;
import org.apache.tomcat.websocket.TesterFirehoseServer;
import org.apache.tomcat.websocket.TesterMessageCountClient.TesterProgrammaticEndpoint;
import org.apache.tomcat.websocket.WebSocketBaseTest;

public class TestSlowClient extends WebSocketBaseTest {

    @Test
    public void testSendingFromAppThread() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = tomcat.addContext("", null);
        // Server side endpoint that sends a stream of messages on a new thread
        // in response to any message received.
        ctx.addApplicationListener(TesterFirehoseServer.ConfigThread.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        // WebSocket client
        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();
        Session wsSession = wsContainer.connectToServer(TesterProgrammaticEndpoint.class,
                ClientEndpointConfig.Builder.create().build(), new URI("ws://localhost:" + getPort() + TesterFirehoseServer.PATH));
        // Configure a handler designed to create a backlog causing the server
        // side write to time out.
        wsSession.addMessageHandler(new VerySlowHandler());

        // Trigger the sending of the messages from the server
        wsSession.getBasicRemote().sendText("start");

        // Wait for server to close connection (it shouldn't)
        // 20s should be long enough even for the slowest CI system. May need to
        // extend this if not.
        int count = 0;
        while (wsSession.isOpen() && count < 200) {
            Thread.sleep(100);
            count++;
        }
        Assert.assertTrue(wsSession.isOpen());
        wsSession.close();

        // BZ 64848 (non-container thread variant)
        // Confirm there are no waiting processors
        AbstractProtocol<?> protocol = (AbstractProtocol<?>) tomcat.getConnector().getProtocolHandler();
        Assert.assertEquals(0, protocol.getWaitingProcessorCount());
    }


    public static class VerySlowHandler implements MessageHandler.Whole<String> {

        @Override
        public void onMessage(String message) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
    }
}
