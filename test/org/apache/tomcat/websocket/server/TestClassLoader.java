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
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.ClientEndpoint;
import javax.websocket.ContainerProvider;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.websocket.server.ServerEndpoint;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.loader.WebappClassLoaderBase;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.websocket.WebSocketBaseTest;

/**
 * Tests endpoint methods are called with the correct class loader.
 */
public class TestClassLoader extends WebSocketBaseTest {

    private static final String PASS = "PASS";
    private static final String FAIL = "FAIL";


    /*
     * Checks class loader for the server endpoint during onOpen and onMessage
     */
    @Test
    public void testSimple() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = tomcat.addContext("", null);
        ctx.addApplicationListener(Config.class.getName());

        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();

        Client client = new Client();

        Session wsSession = wsContainer.connectToServer(client,
                new URI("ws://localhost:" + getPort() + "/test"));

        Assert.assertTrue(wsSession.isOpen());

        // Wait up to 5s for a message
        int count = 0;
        while (count < 50 && client.getMsgCount() < 1) {
            Thread.sleep(100);
        }

        // Check it
        Assert.assertEquals(1,  client.getMsgCount());
        Assert.assertFalse(client.hasFailed());

        wsSession.getBasicRemote().sendText("Testing");

        // Wait up to 5s for a message
        count = 0;
        while (count < 50 && client.getMsgCount() < 2) {
            Thread.sleep(100);
        }

        Assert.assertEquals(2,  client.getMsgCount());
        Assert.assertFalse(client.hasFailed());

        wsSession.close();
    }

    @ClientEndpoint
    public static class Client {

        private final AtomicInteger msgCount = new AtomicInteger(0);
        private boolean failed = false;

        public boolean hasFailed() {
            return failed;
        }

        public int getMsgCount() {
            return msgCount.get();
        }

        @OnMessage
        public void onMessage(String msg) {
            if (!failed && !PASS.equals(msg)) {
                failed = true;
            }
            msgCount.incrementAndGet();
        }
    }


    @ServerEndpoint("/test")
    public static class ClassLoaderEndpoint {

        @OnOpen
        public void onOpen(Session session) throws IOException {
            if (Thread.currentThread().getContextClassLoader() instanceof WebappClassLoaderBase) {
                session.getBasicRemote().sendText(PASS);
            } else {
                session.getBasicRemote().sendText(FAIL);
            }
        }

        @OnMessage
        public String onMessage(@SuppressWarnings("unused") String msg) {
            if (Thread.currentThread().getContextClassLoader() instanceof WebappClassLoaderBase) {
                return PASS;
            } else {
                return FAIL;
            }
        }
    }

    public static class Config extends TesterEndpointConfig {

        @Override
        protected Class<?> getEndpointClass() {
            return ClassLoaderEndpoint.class;
        }

    }
}
