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
package org.apache.tomcat.websocket.server;

import java.net.URI;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.websocket.TesterMessageCountClient.BasicText;
import org.apache.tomcat.websocket.TesterMessageCountClient.TesterProgrammaticEndpoint;

public class TestWsHandshakeRequest extends TomcatBaseTest {

    @Test
    public void doTestgetRequestUriWithQueryString() throws Exception {
        doTestgetRequestUri(true);
    }

    @Test
    public void doTestgetRequestUriWithoutQueryString() throws Exception {
        doTestgetRequestUri(false);
    }

    public void doTestgetRequestUri(boolean withQueryString) throws Exception {

        Tomcat tomcat = getTomcatInstance();
        // Must have a real docBase - just use temp
        Context ctx = tomcat.addContext("", System.getProperty("java.io.tmpdir"));
        ctx.addApplicationListener(TesterUriServer.Config.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMapping("/", "default");

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();
        ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().build();
        String target = "ws://localhost:" + getPort() + TesterUriServer.PATH;
        if (withQueryString) {
            target += "?a=b";
        }
        URI uri = new URI(target);
        Session wsSession = wsContainer.connectToServer(TesterProgrammaticEndpoint.class,
                clientEndpointConfig, uri);

        CountDownLatch latch = new CountDownLatch(1);
        BasicText handler = new BasicText(latch);
        wsSession.addMessageHandler(handler);
        wsSession.getBasicRemote().sendText("Hello");

        System.out.println("Sent Hello message, waiting for data");

        // Ignore the latch result as the message count test below will tell us
        // if the right number of messages arrived
        handler.getLatch().await(60, TimeUnit.SECONDS);

        Queue<String> messages = handler.getMessages();
        Assert.assertEquals(1, messages.size());
        for (String message : messages) {
            Assert.assertEquals(uri.toString(), message);
        }
    }
}
