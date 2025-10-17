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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.websocket.CloseReason;
import jakarta.websocket.CloseReason.CloseCode;
import jakarta.websocket.CloseReason.CloseCodes;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.websocket.WebSocketBaseTest;

/**
 * Test the behavior of closing websockets under various conditions.
 */
public class TestClose extends WebSocketBaseTest {

    private static Log log = LogFactory.getLog(TestClose.class);

    // These are static because it is simpler than trying to inject them into
    // the endpoint
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

        // This is set to true for tests where the @OnMessage should send a message
        public volatile boolean onMessageSends = false;
    }


    private static void awaitLatch(CountDownLatch latch, String failMessage) {
        try {
            if (!latch.await(5000, TimeUnit.MILLISECONDS)) {
                Assert.fail(failMessage);
            }
        } catch (InterruptedException e) {
            // Won't happen
            throw new RuntimeException(e);
        }
    }


    public static void awaitOnClose(CloseCode... codes) {
        Set<CloseCode> set = new HashSet<>(Arrays.asList(codes));
        awaitOnClose(set);
    }


    public static void awaitOnClose(Set<CloseCode> codes) {
        awaitLatch(events.onCloseCalled, "onClose not called");
        CloseCode received = events.closeReason.getCloseCode();
        Assert.assertTrue("Rx: " + received, codes.contains(received));
    }


    public static void awaitOnError(Class<? extends Throwable> exceptionClazz) {
        awaitLatch(events.onErrorCalled, "onError not called");
        Assert.assertTrue(events.onErrorThrowable.getClass().getName(),
                exceptionClazz.isAssignableFrom(events.onErrorThrowable.getClass()));
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

        TesterWsClient client = new TesterWsClient("localhost", getPort());
        client.httpUpgrade(BaseEndpointConfig.PATH);
        client.closeSocket();

        awaitOnClose(CloseCodes.CLOSED_ABNORMALLY);
    }


    @Test
    public void testTcpReset() throws Exception {
        startServer(TestEndpointConfig.class);

        TesterWsClient client = new TesterWsClient("localhost", getPort());
        client.httpUpgrade(BaseEndpointConfig.PATH);
        client.forceCloseSocket();

        // TODO: I'm not entirely sure when onError should be called
        awaitOnError(IOException.class);
        awaitOnClose(CloseCodes.CLOSED_ABNORMALLY);
    }


    @Test
    public void testWsCloseThenTcpClose() throws Exception {
        startServer(TestEndpointConfig.class);

        TesterWsClient client = new TesterWsClient("localhost", getPort());
        client.httpUpgrade(BaseEndpointConfig.PATH);
        client.sendCloseFrame(CloseCodes.GOING_AWAY);
        client.closeSocket();

        awaitOnClose(CloseCodes.GOING_AWAY);
    }


    @Test
    public void testWsCloseThenTcpReset() throws Exception {
        startServer(TestEndpointConfig.class);

        TesterWsClient client = new TesterWsClient("localhost", getPort());
        client.httpUpgrade(BaseEndpointConfig.PATH);
        client.sendCloseFrame(CloseCodes.GOING_AWAY);
        client.forceCloseSocket();

        // WebSocket 1.1, section 2.1.5 requires this to be CLOSED_ABNORMALLY if
        // the container initiates the close and the close code from the client
        // if the client initiates it. When the client resets the TCP connection
        // after sending the close, different operating systems react different
        // ways. Some present the close message then drop the connection, some
        // just drop the connection. Therefore, this test has to handle both
        // close codes.
        awaitOnClose(CloseCodes.CLOSED_ABNORMALLY, CloseCodes.GOING_AWAY);
    }


    @Test
    public void testTcpCloseInOnMessage() throws Exception {
        startServer(TestEndpointConfig.class);

        TesterWsClient client = new TesterWsClient("localhost", getPort());
        client.httpUpgrade(BaseEndpointConfig.PATH);
        client.sendTextMessage("Test");
        awaitLatch(events.onMessageCalled, "onMessage not called");

        client.closeSocket();
        events.onMessageWait.countDown();

        awaitOnClose(CloseCodes.CLOSED_ABNORMALLY);
    }


    @Test
    public void testTcpResetInOnMessage() throws Exception {
        startServer(TestEndpointConfig.class);

        TesterWsClient client = new TesterWsClient("localhost", getPort());
        client.httpUpgrade(BaseEndpointConfig.PATH);
        client.sendTextMessage("Test");
        awaitLatch(events.onMessageCalled, "onMessage not called");

        client.forceCloseSocket();
        events.onMessageWait.countDown();

        awaitOnError(IOException.class);
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

        startServer(TestEndpointConfig.class);

        TesterWsClient client = new TesterWsClient("localhost", getPort());
        client.httpUpgrade(BaseEndpointConfig.PATH);
        client.sendTextMessage("Test");
        awaitLatch(events.onMessageCalled, "onMessage not called");

        client.sendCloseFrame(CloseCodes.NORMAL_CLOSURE);
        client.closeSocket();
        events.onMessageWait.countDown();

        awaitOnClose(CloseCodes.CLOSED_ABNORMALLY);
    }


    @Test
    public void testWsCloseThenTcpResetWhenOnMessageSends() throws Exception {
        events.onMessageSends = true;

        startServer(TestEndpointConfig.class);

        TesterWsClient client = new TesterWsClient("localhost", getPort());
        client.httpUpgrade(BaseEndpointConfig.PATH);
        client.sendTextMessage("Test");
        awaitLatch(events.onMessageCalled, "onMessage not called");

        client.sendCloseFrame(CloseCodes.NORMAL_CLOSURE);
        client.forceCloseSocket();
        events.onMessageWait.countDown();

        awaitOnClose(CloseCodes.CLOSED_ABNORMALLY);
    }


    public static class TestEndpoint {

        @OnOpen
        public void onOpen() {
            log.info("Session opened");
        }

        @OnMessage
        public void onMessage(Session session, String message) {
            log.info("Message received: " + message);
            events.onMessageCalled.countDown();
            awaitLatch(events.onMessageWait, "onMessageWait not triggered");

            if (events.onMessageSends) {
                try {
                    int count = 0;
                    // The latches above are meant to ensure the correct
                    // sequence of events but in some cases there is a short
                    // delay between the client closing / resetting the
                    // connection and the server recognising that fact. This
                    // loop tries to ensure that it lasts much longer than that
                    // delay so any close / reset from the client triggers an
                    // error here.
                    while (count < 10) {
                        count++;
                        session.getBasicRemote().sendText("Test reply");
                        Thread.sleep(500);
                    }
                } catch (IOException | InterruptedException e) {
                    // Expected to fail
                }
            }
        }

        @OnError
        public void onError(Throwable t) {
            log.info("onError", t);
            events.onErrorThrowable = t;
            events.onErrorCalled.countDown();
        }

        @OnClose
        public void onClose(CloseReason cr) {
            log.info("onClose: " + cr);
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


    public abstract static class BaseEndpointConfig extends TesterEndpointConfig {

        public static final String PATH = "/test";

        @Override
        protected ServerEndpointConfig getServerEndpointConfig() {
            return ServerEndpointConfig.Builder.create(getEndpointClass(), PATH).build();
        }
    }
}
