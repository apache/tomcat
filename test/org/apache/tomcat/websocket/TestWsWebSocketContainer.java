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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.DefaultClientConfiguration;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.MessageHandler;
import javax.websocket.SendResult;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.websocket.WebSocketMessage;
import javax.websocket.server.DefaultServerConfiguration;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.coyote.http11.Http11Protocol;
import org.apache.tomcat.websocket.server.ServerContainerImpl;
import org.apache.tomcat.websocket.server.WsListener;

public class TestWsWebSocketContainer extends TomcatBaseTest {

    private static final String MESSAGE_STRING_1 = "qwerty";
    private static final String MESSAGE_TEXT_4K;
    private static final byte[] MESSAGE_BINARY_4K = new byte[4096];

    private static final long TIMEOUT_MS = 5 * 1000;

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
        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));
        ctx.addApplicationListener(EchoConfig.class.getName());

        tomcat.start();

        WebSocketContainer wsContainer =
                ContainerProvider.createClientContainer();
        Session wsSession = wsContainer.connectToServer(TesterEndpoint.class,
                new DefaultClientConfiguration(), new URI("http://localhost:" +
                        getPort() + EchoConfig.PATH_ASYNC));
        CountDownLatch latch = new CountDownLatch(1);
        TesterMessageHandlerText handler = new TesterMessageHandlerText(latch);
        wsSession.addMessageHandler(handler);
        wsSession.getRemote().sendString(MESSAGE_STRING_1);

        boolean latchResult = handler.getLatch().await(10, TimeUnit.SECONDS);

        Assert.assertTrue(latchResult);

        List<String> messages = handler.getMessages();
        Assert.assertEquals(1, messages.size());
        Assert.assertEquals(MESSAGE_STRING_1, messages.get(0));
    }


    @Test(expected=javax.websocket.DeploymentException.class)
    public void testConnectToServerEndpointInvalidScheme() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));
        ctx.addApplicationListener(EchoConfig.class.getName());

        tomcat.start();

        WebSocketContainer wsContainer =
                ContainerProvider.createClientContainer();
        wsContainer.connectToServer(TesterEndpoint.class,
                new DefaultClientConfiguration(), new URI("ftp://localhost:" +
                        getPort() + EchoConfig.PATH_ASYNC));
    }


    @Test(expected=javax.websocket.DeploymentException.class)
    public void testConnectToServerEndpointNoHost() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));
        ctx.addApplicationListener(EchoConfig.class.getName());

        tomcat.start();

        WebSocketContainer wsContainer =
                ContainerProvider.createClientContainer();
        wsContainer.connectToServer(TesterEndpoint.class,
                new DefaultClientConfiguration(),
                new URI("http://" + EchoConfig.PATH_ASYNC));
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


    private void doBufferTest(boolean isTextBuffer, boolean isServerBuffer,
            boolean isTextMessage, boolean pass) throws Exception {

        Tomcat tomcat = getTomcatInstance();
        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));
        ctx.addApplicationListener(EchoConfig.class.getName());

        WebSocketContainer wsContainer =
                ContainerProvider.createClientContainer();

        if (isServerBuffer) {
            if (isTextBuffer) {
                ctx.addParameter(
                        org.apache.tomcat.websocket.server.Constants.
                                TEXT_BUFFER_SIZE_SERVLET_CONTEXT_INIT_PARAM,
                        "1024");
            } else {
                ctx.addParameter(
                        org.apache.tomcat.websocket.server.Constants.
                                BINARY_BUFFER_SIZE_SERVLET_CONTEXT_INIT_PARAM,
                        "1024");
            }
        } else {
            if (isTextBuffer) {
                wsContainer.setMaxTextMessageBufferSize(1024);
            } else {
                wsContainer.setMaxBinaryMessageBufferSize(1024);
            }
        }

        tomcat.start();

        Session wsSession = wsContainer.connectToServer(TesterEndpoint.class,
                new DefaultClientConfiguration(), new URI("http://localhost:" +
                        getPort() + EchoConfig.PATH_BASIC));
        TesterMessageHandler<?> handler;
        CountDownLatch latch = new CountDownLatch(1);
        wsSession.getUserProperties().put("latch", latch);
        if (isTextMessage) {
            handler = new TesterMessageHandlerText(latch);
        } else {
            handler = new TesterMessageHandlerBinary(latch);
        }

        wsSession.addMessageHandler(handler);
        if (isTextMessage) {
            wsSession.getRemote().sendString(MESSAGE_TEXT_4K);
        } else {
            wsSession.getRemote().sendBytes(ByteBuffer.wrap(MESSAGE_BINARY_4K));
        }

        boolean latchResult = handler.getLatch().await(10, TimeUnit.SECONDS);

        Assert.assertTrue(latchResult);

        List<?> messages = handler.getMessages();
        if (pass) {
            Assert.assertEquals(1, messages.size());
            if (isTextMessage) {
                Assert.assertEquals(MESSAGE_TEXT_4K, messages.get(0));
            } else {
                Assert.assertEquals(ByteBuffer.wrap(MESSAGE_BINARY_4K),
                        messages.get(0));
            }
        } else {
            Assert.assertFalse(wsSession.isOpen());
        }
    }


    @Test
    public void testTimeoutClientContainer() throws Exception {
        doTestTimeoutClient(true);
    }


    @Test
    public void testTimeoutClientEndpoint() throws Exception {
        doTestTimeoutClient(false);
    }


    private void doTestTimeoutClient(boolean setTimeoutOnContainer)
            throws Exception {

        Tomcat tomcat = getTomcatInstance();
        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));
        ctx.addApplicationListener(BlockingConfig.class.getName());

        WebSocketContainer wsContainer =
                ContainerProvider.createClientContainer();

        // Set the async timeout
        if (setTimeoutOnContainer) {
            wsContainer.setAsyncSendTimeout(TIMEOUT_MS);
        }

        tomcat.start();

        Session wsSession = wsContainer.connectToServer(TesterEndpoint.class,
                new DefaultClientConfiguration(), new URI("http://localhost:" +
                        getPort() + BlockingConfig.PATH));

        if (!setTimeoutOnContainer) {
            wsSession.getRemote().setAsyncSendTimeout(TIMEOUT_MS);
        }

        long lastSend = 0;
        boolean isOK = true;
        SendResult sr = null;

        // Should send quickly until the network buffers fill up and then block
        // until the timeout kicks in
        while (isOK) {
            Future<SendResult> f = wsSession.getRemote().sendBytesByFuture(
                    ByteBuffer.wrap(MESSAGE_BINARY_4K));
            lastSend = System.currentTimeMillis();
            sr = f.get();
            isOK = sr.isOK();
        }

        long timeout = System.currentTimeMillis() - lastSend;

        // Check correct time passed
        Assert.assertTrue(timeout >= TIMEOUT_MS);

        // Check the timeout wasn't too long
        Assert.assertTrue(timeout < TIMEOUT_MS*2);

        if (sr == null) {
            Assert.fail();
        } else {
            Assert.assertNotNull(sr.getException());
        }
    }


    @Test
    public void testTimeoutServerContainer() throws Exception {
        doTestTimeoutServer(true);
    }


    @Test
    public void testTimeoutServerEndpoint() throws Exception {
        doTestTimeoutServer(false);
    }


    private static volatile boolean timoutOnContainer = false;

    private void doTestTimeoutServer(boolean setTimeoutOnContainer)
            throws Exception {

        /*
         * Note: There are all sorts of horrible uses of statics in this test
         *       because the API uses classes and the tests really need access
         *       to the instances which simply isn't possible.
         */
        timoutOnContainer = setTimeoutOnContainer;

        Tomcat tomcat = getTomcatInstance();

        if (getProtocol().equals(Http11Protocol.class.getName())) {
            // This will never work for BIO
            return;
        }

        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));
        ctx.addApplicationListener(WsListener.class.getName());
        ctx.addApplicationListener(ConstantTxConfig.class.getName());

        WebSocketContainer wsContainer =
                ContainerProvider.createClientContainer();

        tomcat.start();

        Session wsSession = wsContainer.connectToServer(TesterEndpoint.class,
                new DefaultClientConfiguration(), new URI("http://localhost:" +
                        getPort() + ConstantTxConfig.PATH));

        wsSession.addMessageHandler(new BlockingBinaryHandler());

        int loops = 0;
        while (loops < 60) {
            Thread.sleep(1000);
            if (!ConstantTxEndpoint.getRunning()) {
                break;
            }
        }

        // Check nothing really bad happened
        Assert.assertNull(ConstantTxEndpoint.getException());

        // Check correct time passed
        Assert.assertTrue(ConstantTxEndpoint.getTimeout() >= TIMEOUT_MS);

        // Check the timeout wasn't too long
        Assert.assertTrue(ConstantTxEndpoint.getTimeout() < TIMEOUT_MS*2);

        if (ConstantTxEndpoint.getSendResult() == null) {
            Assert.fail();
        } else {
            Assert.assertNotNull(
                    ConstantTxEndpoint.getSendResult().getException());
        }

    }


    private abstract static class TesterMessageHandler<T>
            implements MessageHandler.Basic<T> {

        private final CountDownLatch latch;

        private final List<T> messages = new CopyOnWriteArrayList<>();

        public TesterMessageHandler(CountDownLatch latch) {
            this.latch = latch;
        }

        public CountDownLatch getLatch() {
            return latch;
        }

        public List<T> getMessages() {
            return messages;
        }
    }

    private static class TesterMessageHandlerText
            extends TesterMessageHandler<String> {


        public TesterMessageHandlerText(CountDownLatch latch) {
            super(latch);
        }

        @Override
        public void onMessage(String message) {
            getMessages().add(message);
            if (getLatch() != null) {
                getLatch().countDown();
            }
        }
    }


    private static class TesterMessageHandlerBinary
            extends TesterMessageHandler<ByteBuffer> {

        public TesterMessageHandlerBinary(CountDownLatch latch) {
            super(latch);
        }

        @Override
        public void onMessage(ByteBuffer message) {
            getMessages().add(message);
            if (getLatch() != null) {
                getLatch().countDown();
            }
        }
    }


    public static class TesterEndpoint extends Endpoint {

        @Override
        public void onClose(Session session, CloseReason closeReason) {
            clearLatch(session);
        }

        @Override
        public void onError(Session session, Throwable throwable) {
            clearLatch(session);
        }

        private void clearLatch(Session session) {
            CountDownLatch latch =
                    (CountDownLatch) session.getUserProperties().get("latch");
            if (latch != null) {
                while (latch.getCount() > 0) {
                    latch.countDown();
                }
            }
        }

        @Override
        public void onOpen(Session session, EndpointConfiguration config) {
            // NO-OP
        }
    }


    public static class EchoConfig implements ServletContextListener {

        public static final String PATH_ASYNC = "/echoAsync";
        public static final String PATH_BASIC = "/echoBasic";

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            ServerContainerImpl sc = ServerContainerImpl.getServerContainer();
            sc.publishServer(
                    EchoAsync.class, sce.getServletContext(), PATH_ASYNC);
            sc.publishServer(
                    EchoBasic.class, sce.getServletContext(), PATH_BASIC);
        }

        @Override
        public void contextDestroyed(ServletContextEvent sce) {
            // NO-OP
        }
    }


    public static class EchoBasic {
        @WebSocketMessage
        public void echoTextMessage(Session session, String msg) {
            try {
                session.getRemote().sendString(msg);
            } catch (IOException e) {
                try {
                    session.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        }


        @WebSocketMessage
        public void echoBinaryMessage(Session session, ByteBuffer msg) {
            try {
                session.getRemote().sendBytes(msg);
            } catch (IOException e) {
                try {
                    session.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        }
    }


    public static class EchoAsync {

        @WebSocketMessage
        public void echoTextMessage(Session session, String msg, boolean last) {
            try {
                session.getRemote().sendPartialString(msg, last);
            } catch (IOException e) {
                try {
                    session.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        }


        @WebSocketMessage
        public void echoBinaryMessage(Session session, ByteBuffer msg,
                boolean last) {
            try {
                session.getRemote().sendPartialBytes(msg, last);
            } catch (IOException e) {
                try {
                    session.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        }
    }


    public static class BlockingConfig implements ServletContextListener {

        public static final String PATH = "/block";

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            ServerContainerImpl sc = ServerContainerImpl.getServerContainer();
            sc.publishServer(BlockingPojo.class, sce.getServletContext(), PATH);
        }

        @Override
        public void contextDestroyed(ServletContextEvent sce) {
            // NO-OP
        }
    }


    public static class BlockingPojo {
        @SuppressWarnings("unused")
        @WebSocketMessage
        public void echoTextMessage(Session session, String msg, boolean last) {
            try {
                Thread.sleep(60000);
            } catch (InterruptedException e) {
                // Ignore
            }
        }


        @SuppressWarnings("unused")
        @WebSocketMessage
        public void echoBinaryMessage(Session session, ByteBuffer msg,
                boolean last) {
            try {
                Thread.sleep(TIMEOUT_MS * 10);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
    }


    public static class BlockingBinaryHandler
            implements MessageHandler.Async<ByteBuffer> {

        @Override
        public void onMessage(ByteBuffer messagePart, boolean last) {
            try {
                Thread.sleep(TIMEOUT_MS * 10);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
    }


    public static class ConstantTxEndpoint extends Endpoint {

        // Have to be static to be able to retrieve results from test case
        private static volatile long timeout = -1;
        private static volatile boolean ok = true;
        private static volatile SendResult sr = null;
        private static volatile Exception exception = null;
        private static volatile boolean running = true;


        @Override
        public void onOpen(Session session, EndpointConfiguration config) {

            // Reset everything
            timeout = -1;
            ok = true;
            sr = null;
            exception = null;
            running = true;

            if (!TestWsWebSocketContainer.timoutOnContainer) {
                session.getRemote().setAsyncSendTimeout(TIMEOUT_MS);
            }

            long lastSend = 0;

            // Should send quickly until the network buffers fill up and then
            // block until the timeout kicks in
            try {
                while (ok) {
                    lastSend = System.currentTimeMillis();
                    Future<SendResult> f = session.getRemote().sendBytesByFuture(
                            ByteBuffer.wrap(MESSAGE_BINARY_4K));
                    sr = f.get();
                    ok = sr.isOK();
                }
            } catch (ExecutionException | InterruptedException e) {
                exception = e;
            }
            timeout = System.currentTimeMillis() - lastSend;
            running = false;
        }

        public static long getTimeout() {
            return timeout;
        }

        public static boolean isOK() {
            return ok;
        }

        public static SendResult getSendResult() {
            return sr;
        }

        public static Exception getException() {
            return exception;
        }

        public static boolean getRunning() {
            return running;
        }
    }


    public static class ConstantTxConfig implements ServletContextListener {

        private static final String PATH = "/test";

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            ServerContainerImpl sc = ServerContainerImpl.getServerContainer();
            sc.setServletContext(sce.getServletContext());
            try {
                sc.publishServer(ConstantTxEndpoint.class, PATH,
                        DefaultServerConfiguration.class);
                if (TestWsWebSocketContainer.timoutOnContainer) {
                    sc.setAsyncSendTimeout(TIMEOUT_MS);
                }
            } catch (DeploymentException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void contextDestroyed(ServletContextEvent sce) {
            // NO-OP
        }
    }
}
