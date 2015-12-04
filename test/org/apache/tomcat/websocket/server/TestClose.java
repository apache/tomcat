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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContextEvent;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCode;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.DeploymentException;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

/**
 * Test the behavior of closing websockets under various conditions.
 */
@Ignore // Only because they don't pass at the moment.
public class TestClose extends TomcatBaseTest {

    // TODO: These are static because I'm not sure how to inject them to the
    // endpoint
    private static volatile Events events;


    public static class Events {
        // Used to block in the @OnMessage
        public final CountDownLatch onMessageWait = new CountDownLatch(1);

        // Used to check which methods of a server endpoint were called
        public final CountDownLatch onErrorCalled = new CountDownLatch(1);
        public final CountDownLatch onMessageCalled = new CountDownLatch(1);
        public final CountDownLatch onCloseCalled = new CountDownLatch(1);

        // Parameter of an @OnClose call
        public volatile CloseReason closeReason = null;
        // Parameter of an @OnError call
        public volatile Throwable onErrorThrowable = null;

        //This is set to true for tests where the @OnMessage should send a message
        public volatile boolean onMessageSends = false;
    }


    private static void awaitLatch(CountDownLatch latch, String failMessage) {
        try {
            if (!latch.await(3000, TimeUnit.MILLISECONDS)) {
                Assert.fail(failMessage);
            }
        } catch (InterruptedException e) {
            // Won't happen
            throw new RuntimeException(e);
        }
    }


    public static void awaitOnClose(CloseCode code) {
        awaitLatch(events.onCloseCalled, "onClose not called");
        Assert.assertEquals(code.getCode(), events.closeReason.getCloseCode()
                .getCode());
    }


    public static void awaitOnError(Class<? extends Throwable> exceptionClazz) {
        awaitLatch(events.onErrorCalled, "onError not called");
        Assert.assertEquals(exceptionClazz, events.onErrorThrowable.getClass());
    }


    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        events = new Events();
    }


    @Test
    public void testTcpClose() throws Exception {
        startServer(TestEndpointConfig.class);

        TesterWsCloseClient client = new TesterWsCloseClient("localhost", getPort());
        client.httpUpgrade(BaseEndpointConfig.PATH);
        client.closeSocket();

        awaitOnClose(CloseCodes.CLOSED_ABNORMALLY);
    }


    @Test
    public void testTcpReset() throws Exception {
        startServer(TestEndpointConfig.class);

        TesterWsCloseClient client = new TesterWsCloseClient("localhost", getPort());
        client.httpUpgrade(BaseEndpointConfig.PATH);
        client.forceCloseSocket();

        // TODO: I'm not entirely sure when onError should be called
        awaitOnError(IOException.class);
        awaitOnClose(CloseCodes.CLOSED_ABNORMALLY);
    }


    @Test
    public void testWsCloseThenTcpClose() throws Exception {
        startServer(TestEndpointConfig.class);

        TesterWsCloseClient client = new TesterWsCloseClient("localhost", getPort());
        client.httpUpgrade(BaseEndpointConfig.PATH);
        client.sendCloseFrame(CloseCodes.GOING_AWAY);
        client.closeSocket();

        awaitOnClose(CloseCodes.GOING_AWAY);
    }


    @Test
    public void testWsCloseThenTcpReset() throws Exception {
        startServer(TestEndpointConfig.class);

        TesterWsCloseClient client = new TesterWsCloseClient("localhost", getPort());
        client.httpUpgrade(BaseEndpointConfig.PATH);
        client.sendCloseFrame(CloseCodes.GOING_AWAY);
        client.forceCloseSocket();

        //TODO: Why CLOSED_ABNORMALLY when above is GOING_AWAY?
        awaitOnClose(CloseCodes.CLOSED_ABNORMALLY);
    }


    @Test
    public void testTcpCloseInOnMessage() throws Exception {
        startServer(TestEndpointConfig.class);

        TesterWsCloseClient client = new TesterWsCloseClient("localhost", getPort());
        client.httpUpgrade(BaseEndpointConfig.PATH);
        client.sendMessage("Test");
        awaitLatch(events.onMessageCalled, "onMessage not called");

        client.closeSocket();
        events.onMessageWait.countDown();

        awaitOnClose(CloseCodes.CLOSED_ABNORMALLY);
    }


    @Test
    public void testTcpResetInOnMessage() throws Exception {
        startServer(TestEndpointConfig.class);

        TesterWsCloseClient client = new TesterWsCloseClient("localhost", getPort());
        client.httpUpgrade(BaseEndpointConfig.PATH);
        client.sendMessage("Test");
        awaitLatch(events.onMessageCalled, "onMessage not called");

        client.forceCloseSocket();
        events.onMessageWait.countDown();

        awaitOnError(IOException.class);
        awaitOnClose(CloseCodes.CLOSED_ABNORMALLY);
    }


    @Test
    public void testWsCloseThenTcpCloseInOnMessage() throws Exception {
        startServer(TestEndpointConfig.class);

        TesterWsCloseClient client = new TesterWsCloseClient("localhost", getPort());
        client.httpUpgrade(BaseEndpointConfig.PATH);
        client.sendMessage("Test");
        awaitLatch(events.onMessageCalled, "onMessage not called");

        client.sendCloseFrame(CloseCodes.NORMAL_CLOSURE);
        client.closeSocket();
        events.onMessageWait.countDown();

        awaitOnClose(CloseCodes.CLOSED_ABNORMALLY);
    }


    @Test
    public void testWsCloseThenTcpResetInOnMessage() throws Exception {
        startServer(TestEndpointConfig.class);

        TesterWsCloseClient client = new TesterWsCloseClient("localhost", getPort());
        client.httpUpgrade(BaseEndpointConfig.PATH);
        client.sendMessage("Test");
        awaitLatch(events.onMessageCalled, "onMessage not called");

        client.sendCloseFrame(CloseCodes.NORMAL_CLOSURE);
        client.closeSocket();
        events.onMessageWait.countDown();

        awaitOnClose(CloseCodes.CLOSED_ABNORMALLY);
    }


    @Test
    public void testTcpCloseWhenOnMessageSends() throws Exception {
        events.onMessageSends = true;
        testTcpCloseInOnMessage();
    }


    @Test
    public void testTcpResetWhenOnMessageSends() throws Exception {
        events.onMessageSends = true;
        testTcpResetInOnMessage();
    }


    @Test
    public void testWsCloseThenTcpCloseWhenOnMessageSends() throws Exception {
        events.onMessageSends = true;
        testWsCloseThenTcpCloseInOnMessage();
    }


    @Test
    public void testWsCloseThenTcpResetWhenOnMessageSends() throws Exception {
        events.onMessageSends = true;
        testWsCloseThenTcpResetInOnMessage();
    }


    public static class TestEndpoint {

        @OnOpen
        public void onOpen() {
            System.out.println("Session opened");
        }

        @OnMessage
        public void onMessage(Session session, String message) {
            System.out.println("Message received: " + message);
            events.onMessageCalled.countDown();
            awaitLatch(events.onMessageWait, "onMessageWait not triggered");

            if (events.onMessageSends) {
                try {
                    session.getBasicRemote().sendText("Test reply");
                } catch (IOException e) {
                    // Expected to fail
                }
            }
        }

        @OnError
        public void onError(Throwable t) {
            System.out.println("onError: " + t.getMessage());
            events.onErrorThrowable = t;
            events.onErrorCalled.countDown();
        }

        @OnClose
        public void onClose(CloseReason cr) {
            System.out.println("onClose: " + cr);
            events.closeReason = cr;
            events.onCloseCalled.countDown();
        }
    }


    public static class TestEndpointConfig extends BaseEndpointConfig {

        @Override
        protected Class<?> getEndpointClass() {
            return TestEndpoint.class;
        }

    }


    private Tomcat startServer(
            final Class<? extends WsContextListener> configClass)
            throws LifecycleException {

        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = tomcat.addContext("", null);
        ctx.addApplicationListener(configClass.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMapping("/", "default");

        tomcat.start();
        return tomcat;
    }


    public abstract static class BaseEndpointConfig extends WsContextListener {

        public static final String PATH = "/test";

        protected abstract Class<?> getEndpointClass();

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            super.contextInitialized(sce);

            ServerContainer sc = (ServerContainer) sce
                    .getServletContext()
                    .getAttribute(
                            Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE);

            ServerEndpointConfig sec = ServerEndpointConfig.Builder.create(
                    getEndpointClass(), PATH).build();

            try {
                sc.addEndpoint(sec);
            } catch (DeploymentException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
