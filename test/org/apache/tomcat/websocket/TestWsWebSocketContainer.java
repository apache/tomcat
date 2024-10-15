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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.ServletContextEvent;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Extension;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpoint;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.websocket.TesterMessageCountClient.BasicBinary;
import org.apache.tomcat.websocket.TesterMessageCountClient.BasicHandler;
import org.apache.tomcat.websocket.TesterMessageCountClient.BasicText;
import org.apache.tomcat.websocket.TesterMessageCountClient.TesterEndpoint;
import org.apache.tomcat.websocket.TesterMessageCountClient.TesterProgrammaticEndpoint;
import org.apache.tomcat.websocket.server.WsContextListener;

public class TestWsWebSocketContainer extends WsWebSocketContainerBaseTest {

    private static final String MESSAGE_EMPTY = "";
    private static final String MESSAGE_STRING_1 = "qwerty";
    private static final String MESSAGE_TEXT_4K;

    // 5s should be plenty but Gump can be a lot slower
    private static final long START_STOP_WAIT = 60 * 1000;

    static {
        StringBuilder sb = new StringBuilder(4096);
        for (int i = 0; i < 4096; i++) {
            sb.append('*');
        }
        MESSAGE_TEXT_4K = sb.toString();
    }


    @Test
    public void testConnectToServerEndpoint() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = getProgrammaticRootContext();
        ctx.addApplicationListener(TesterEchoServer.Config.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();
        // Set this artificially small to trigger
        // https://bz.apache.org/bugzilla/show_bug.cgi?id=57054
        wsContainer.setDefaultMaxBinaryMessageBufferSize(64);
        Session wsSession = wsContainer.connectToServer(TesterProgrammaticEndpoint.class,
                ClientEndpointConfig.Builder.create().build(),
                new URI("ws://" + getHostName() + ":" + getPort() + TesterEchoServer.Config.PATH_ASYNC));
        CountDownLatch latch = new CountDownLatch(1);
        BasicText handler = new BasicText(latch);
        wsSession.addMessageHandler(handler);
        wsSession.getBasicRemote().sendText(MESSAGE_STRING_1);

        boolean latchResult = handler.getLatch().await(10, TimeUnit.SECONDS);

        Assert.assertTrue(latchResult);

        Queue<String> messages = handler.getMessages();
        Assert.assertEquals(1, messages.size());
        Assert.assertEquals(MESSAGE_STRING_1, messages.peek());

        ((WsWebSocketContainer) wsContainer).destroy();
    }


    @Test(expected = DeploymentException.class)
    public void testConnectToServerEndpointInvalidScheme() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = getProgrammaticRootContext();
        ctx.addApplicationListener(TesterEchoServer.Config.class.getName());

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();
        wsContainer.connectToServer(TesterProgrammaticEndpoint.class, ClientEndpointConfig.Builder.create().build(),
                new URI("ftp://" + getHostName() + ":" + getPort() + TesterEchoServer.Config.PATH_ASYNC));
    }


    @Test(expected = DeploymentException.class)
    public void testConnectToServerEndpointNoHost() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = getProgrammaticRootContext();
        ctx.addApplicationListener(TesterEchoServer.Config.class.getName());

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();
        wsContainer.connectToServer(TesterProgrammaticEndpoint.class, ClientEndpointConfig.Builder.create().build(),
                new URI("ws://" + TesterEchoServer.Config.PATH_ASYNC));
    }


    @Test
    public void testSmallTextBufferClientTextMessage() throws Exception {
        doBufferTest(true, false, true, false);
    }


    @Test
    public void testSmallTextBufferClientBinaryMessage() throws Exception {
        doBufferTest(true, false, false, true);
    }


    @Test
    public void testSmallTextBufferServerTextMessage() throws Exception {
        doBufferTest(true, true, true, false);
    }


    @Test
    public void testSmallTextBufferServerBinaryMessage() throws Exception {
        doBufferTest(true, true, false, true);
    }


    @Test
    public void testSmallBinaryBufferClientTextMessage() throws Exception {
        doBufferTest(false, false, true, true);
    }


    @Test
    public void testSmallBinaryBufferClientBinaryMessage() throws Exception {
        doBufferTest(false, false, false, false);
    }


    @Test
    public void testSmallBinaryBufferServerTextMessage() throws Exception {
        doBufferTest(false, true, true, true);
    }


    @Test
    public void testSmallBinaryBufferServerBinaryMessage() throws Exception {
        doBufferTest(false, true, false, false);
    }


    private void doBufferTest(boolean isTextBuffer, boolean isServerBuffer, boolean isTextMessage, boolean pass)
            throws Exception {

        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = getProgrammaticRootContext();
        ctx.addApplicationListener(TesterEchoServer.Config.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();

        if (isServerBuffer) {
            if (isTextBuffer) {
                ctx.addParameter(
                        org.apache.tomcat.websocket.server.Constants.TEXT_BUFFER_SIZE_SERVLET_CONTEXT_INIT_PARAM,
                        "1024");
            } else {
                ctx.addParameter(
                        org.apache.tomcat.websocket.server.Constants.BINARY_BUFFER_SIZE_SERVLET_CONTEXT_INIT_PARAM,
                        "1024");
            }
        } else {
            if (isTextBuffer) {
                wsContainer.setDefaultMaxTextMessageBufferSize(1024);
            } else {
                wsContainer.setDefaultMaxBinaryMessageBufferSize(1024);
            }
        }

        tomcat.start();

        Session wsSession = wsContainer.connectToServer(TesterProgrammaticEndpoint.class,
                ClientEndpointConfig.Builder.create().build(),
                new URI("ws://" + getHostName() + ":" + getPort() + TesterEchoServer.Config.PATH_BASIC));
        BasicHandler<?> handler;
        CountDownLatch latch = new CountDownLatch(1);
        TesterEndpoint tep = (TesterEndpoint) wsSession.getUserProperties().get("endpoint");
        tep.setLatch(latch);
        if (isTextMessage) {
            handler = new BasicText(latch);
        } else {
            handler = new BasicBinary(latch);
        }

        wsSession.addMessageHandler(handler);
        try {
            if (isTextMessage) {
                wsSession.getBasicRemote().sendText(MESSAGE_TEXT_4K);
            } else {
                wsSession.getBasicRemote().sendBinary(ByteBuffer.wrap(MESSAGE_BINARY_4K));
            }
        } catch (IOException ioe) {
            // Some messages sends are expected to fail. Assertions further on
            // in this method will check for the correct behaviour so ignore any
            // exception here.
        }

        boolean latchResult = handler.getLatch().await(10, TimeUnit.SECONDS);

        Assert.assertTrue(latchResult);

        Queue<?> messages = handler.getMessages();
        if (pass) {
            Assert.assertEquals(1, messages.size());
            if (isTextMessage) {
                Assert.assertEquals(MESSAGE_TEXT_4K, messages.peek());
            } else {
                Assert.assertEquals(ByteBuffer.wrap(MESSAGE_BINARY_4K), messages.peek());
            }
        } else {
            // When the message exceeds the buffer size, the WebSocket is
            // closed. The endpoint ensures that the latch is cleared when the
            // WebSocket closes. However, the session isn't marked as closed
            // until after the onClose() method completes so there is a small
            // window where this test could fail. Therefore, wait briefly to
            // give the session a chance to complete the close process.
            for (int i = 0; i < 500; i++) {
                if (!wsSession.isOpen()) {
                    break;
                }
                Thread.sleep(10);
            }
            Assert.assertFalse(wsSession.isOpen());
        }
    }


    public static class BlockingConfig extends WsContextListener {

        public static final String PATH = "/block";

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            super.contextInitialized(sce);
            ServerContainer sc = (ServerContainer) sce.getServletContext().getAttribute(
                    org.apache.tomcat.websocket.server.Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE);
            try {
                // Reset blocking state
                BlockingPojo.resetBlock();
                sc.addEndpoint(BlockingPojo.class);
            } catch (DeploymentException e) {
                throw new IllegalStateException(e);
            }
        }
    }


    @ServerEndpoint("/block")
    public static class BlockingPojo {

        private static Object monitor = new Object();
        // Enable blocking by default
        private static boolean block = true;

        /**
         * Clear any current block.
         */
        public static void clearBlock() {
            synchronized (monitor) {
                block = false;
                monitor.notifyAll();
            }
        }

        public static void resetBlock() {
            synchronized (monitor) {
                block = true;
            }
        }

        @SuppressWarnings("unused")
        @OnMessage
        public void echoTextMessage(Session session, String msg, boolean last) {
            try {
                synchronized (monitor) {
                    while (block) {
                        monitor.wait();
                    }
                }
            } catch (InterruptedException e) {
                // Ignore
            }
        }


        @SuppressWarnings("unused")
        @OnMessage
        public void echoBinaryMessage(Session session, ByteBuffer msg, boolean last) {
            try {
                synchronized (monitor) {
                    while (block) {
                        monitor.wait();
                    }
                }
            } catch (InterruptedException e) {
                // Ignore
            }
        }
    }


    public static class BlockingBinaryHandler implements MessageHandler.Partial<ByteBuffer> {

        @Override
        public void onMessage(ByteBuffer messagePart, boolean last) {
            try {
                Thread.sleep(TIMEOUT_MS * 10);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
    }


    @Test
    public void testGetOpenSessions() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = getProgrammaticRootContext();
        ctx.addApplicationListener(TesterEchoServer.Config.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();

        EndpointA endpointA = new EndpointA();
        Session s1a = connectToEchoServer(wsContainer, endpointA, TesterEchoServer.Config.PATH_BASIC);
        Session s2a = connectToEchoServer(wsContainer, endpointA, TesterEchoServer.Config.PATH_BASIC);
        Session s3a = connectToEchoServer(wsContainer, endpointA, TesterEchoServer.Config.PATH_BASIC);

        EndpointB endpointB = new EndpointB();
        Session s1b = connectToEchoServer(wsContainer, endpointB, TesterEchoServer.Config.PATH_BASIC);
        Session s2b = connectToEchoServer(wsContainer, endpointB, TesterEchoServer.Config.PATH_BASIC);

        Set<Session> setA = s3a.getOpenSessions();
        Assert.assertEquals(3, setA.size());
        Assert.assertTrue(setA.remove(s1a));
        Assert.assertTrue(setA.remove(s2a));
        Assert.assertTrue(setA.remove(s3a));

        s1a.close();

        setA = s3a.getOpenSessions();
        Assert.assertEquals(2, setA.size());
        Assert.assertFalse(setA.remove(s1a));
        Assert.assertTrue(setA.remove(s2a));
        Assert.assertTrue(setA.remove(s3a));

        Set<Session> setB = s1b.getOpenSessions();
        Assert.assertEquals(2, setB.size());
        Assert.assertTrue(setB.remove(s1b));
        Assert.assertTrue(setB.remove(s2b));

        // Close sessions explicitly as Gump reports a session remains open at
        // the end of this test
        s2a.close();
        s3a.close();
        s1b.close();
        s2b.close();
    }


    @Test
    public void testSessionExpiryOnUserPropertyReadIdleTimeout() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = getProgrammaticRootContext();
        ctx.addApplicationListener(TesterEchoServer.Config.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        // Need access to implementation methods for configuring unit tests
        WsWebSocketContainer wsContainer = (WsWebSocketContainer) ContainerProvider.getWebSocketContainer();

        wsContainer.setDefaultMaxSessionIdleTimeout(90000);
        wsContainer.setProcessPeriod(1);

        EndpointA endpointA = new EndpointA();
        Session s1a = connectToEchoServer(wsContainer, endpointA, TesterEchoServer.Config.PATH_BASIC);
        s1a.setMaxIdleTimeout(90000);

        s1a.getUserProperties().put(Constants.READ_IDLE_TIMEOUT_MS, Long.valueOf(5000));

        // maxIdleTimeout is 90s but the readIdleTimeout is 5s. The session
        // should get closed after 5 seconds as nothing is read on it.

        // First confirm the session has been opened.
        Assert.assertEquals(1, s1a.getOpenSessions().size());

        // Now wait for it to close. Allow up to 30s as some CI systems are slow
        // but that is still well under the 90s configured for the session.
        int count = 0;
        while (count < 300 && s1a.isOpen()) {
            count++;
            Thread.sleep(100);
        }
        Assert.assertFalse(s1a.isOpen());
    }


    @Test
    public void testSessionExpiryOnUserPropertyWriteIdleTimeout() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = getProgrammaticRootContext();
        ctx.addApplicationListener(TesterEchoServer.Config.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        // Need access to implementation methods for configuring unit tests
        WsWebSocketContainer wsContainer = (WsWebSocketContainer) ContainerProvider.getWebSocketContainer();

        wsContainer.setDefaultMaxSessionIdleTimeout(90000);
        wsContainer.setProcessPeriod(1);

        EndpointA endpointA = new EndpointA();
        Session s1a = connectToEchoServer(wsContainer, endpointA, TesterEchoServer.Config.PATH_BASIC);
        s1a.setMaxIdleTimeout(90000);

        s1a.getUserProperties().put(Constants.WRITE_IDLE_TIMEOUT_MS, Long.valueOf(5000));

        // maxIdleTimeout is 90s but the writeIdleTimeout is 5s. The session
        // should get closed after 5 seconds as nothing is written on it.

        // First confirm the session has been opened.
        Assert.assertEquals(1, s1a.getOpenSessions().size());

        // Now wait for it to close. Allow up to 30s as some CI systems are slow
        // but that is still well under the 90s configured for the session.
        int count = 0;
        while (count < 300 && s1a.isOpen()) {
            count++;
            Thread.sleep(100);
        }
        Assert.assertFalse(s1a.isOpen());
    }


    public static final class EndpointA extends Endpoint {

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            // NO-OP
        }
    }


    public static final class EndpointB extends Endpoint {

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            // NO-OP
        }
    }


    @Test
    public void testMaxMessageSize01() throws Exception {
        doMaxMessageSize(TesterEchoServer.Config.PATH_BASIC_LIMIT_LOW, TesterEchoServer.BasicLimitLow.MAX_SIZE - 1,
                true);
    }


    @Test
    public void testMaxMessageSize02() throws Exception {
        doMaxMessageSize(TesterEchoServer.Config.PATH_BASIC_LIMIT_LOW, TesterEchoServer.BasicLimitLow.MAX_SIZE, true);
    }


    @Test
    public void testMaxMessageSize03() throws Exception {
        doMaxMessageSize(TesterEchoServer.Config.PATH_BASIC_LIMIT_LOW, TesterEchoServer.BasicLimitLow.MAX_SIZE + 1,
                false);
    }


    @Test
    public void testMaxMessageSize04() throws Exception {
        doMaxMessageSize(TesterEchoServer.Config.PATH_BASIC_LIMIT_HIGH, TesterEchoServer.BasicLimitHigh.MAX_SIZE - 1,
                true);
    }


    @Test
    public void testMaxMessageSize05() throws Exception {
        doMaxMessageSize(TesterEchoServer.Config.PATH_BASIC_LIMIT_HIGH, TesterEchoServer.BasicLimitHigh.MAX_SIZE, true);
    }


    @Test
    public void testMaxMessageSize06() throws Exception {
        doMaxMessageSize(TesterEchoServer.Config.PATH_BASIC_LIMIT_HIGH, TesterEchoServer.BasicLimitHigh.MAX_SIZE + 1,
                false);
    }


    private void doMaxMessageSize(String path, long size, boolean expectOpen) throws Exception {

        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = getProgrammaticRootContext();
        ctx.addApplicationListener(TesterEchoServer.Config.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();

        Session s = connectToEchoServer(wsContainer, new EndpointA(), path);

        // One for the client, one for the server
        validateBackgroundProcessCount(2);

        StringBuilder msg = new StringBuilder();
        for (long i = 0; i < size; i++) {
            msg.append('x');
        }

        s.getBasicRemote().sendText(msg.toString());

        // Wait for up to 5 seconds for the client session to open
        boolean open = s.isOpen();
        int count = 0;
        while (open != expectOpen && count < 50) {
            Thread.sleep(100);
            count++;
            open = s.isOpen();
        }

        Assert.assertEquals(Boolean.valueOf(expectOpen), Boolean.valueOf(s.isOpen()));

        // Close the session if it is expected to be open
        if (expectOpen) {
            s.close();
        }

        // Ensure both server and client have shutdown
        validateBackgroundProcessCount(0);
    }


    private void validateBackgroundProcessCount(int expected) throws Exception {
        int count = 0;
        while (count < (START_STOP_WAIT / 100)) {
            if (BackgroundProcessManager.getInstance().getProcessCount() == expected) {
                break;
            }
            Thread.sleep(100);
            count++;
        }
        Assert.assertEquals(expected, BackgroundProcessManager.getInstance().getProcessCount());

    }

    @Test
    public void testPerMessageDeflateClient01() throws Exception {
        doTestPerMessageDeflateClient(MESSAGE_STRING_1, 1);
    }


    @Test
    public void testPerMessageDeflateClient02() throws Exception {
        doTestPerMessageDeflateClient(MESSAGE_EMPTY, 1);
    }


    @Test
    public void testPerMessageDeflateClient03() throws Exception {
        doTestPerMessageDeflateClient(MESSAGE_STRING_1, 2);
    }


    @Test
    public void testPerMessageDeflateClient04() throws Exception {
        doTestPerMessageDeflateClient(MESSAGE_EMPTY, 2);
    }


    private void doTestPerMessageDeflateClient(String msg, int count) throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = getProgrammaticRootContext();
        ctx.addApplicationListener(TesterEchoServer.Config.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        Extension perMessageDeflate = new WsExtension(PerMessageDeflate.NAME);
        List<Extension> extensions = new ArrayList<>(1);
        extensions.add(perMessageDeflate);

        ClientEndpointConfig clientConfig = ClientEndpointConfig.Builder.create().extensions(extensions).build();

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();
        Session wsSession = wsContainer.connectToServer(TesterProgrammaticEndpoint.class, clientConfig,
                new URI("ws://" + getHostName() + ":" + getPort() + TesterEchoServer.Config.PATH_ASYNC));
        CountDownLatch latch = new CountDownLatch(count);
        BasicText handler = new BasicText(latch, msg);
        wsSession.addMessageHandler(handler);
        for (int i = 0; i < count; i++) {
            wsSession.getBasicRemote().sendText(msg);
        }

        boolean latchResult = handler.getLatch().await(10, TimeUnit.SECONDS);

        Assert.assertTrue(latchResult);

        ((WsWebSocketContainer) wsContainer).destroy();
    }
}
