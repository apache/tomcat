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

import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import jakarta.servlet.ServletContextEvent;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpointConfig;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.websocket.TestWsWebSocketContainer.BlockingBinaryHandler;
import org.apache.tomcat.websocket.TesterMessageCountClient.TesterProgrammaticEndpoint;
import org.apache.tomcat.websocket.server.WsContextListener;

/*
 * Moved to separate test class to improve test concurrency. These tests are
 * some of the last tests to start and having them all in a single class
 * significantly extends the length of a test run when using multiple test
 * threads.
 */
public class TestWsWebSocketContainerTimeoutServer  extends WsWebSocketContainerBaseTest {

    @Test
    public void testWriteTimeoutServerContainer() throws Exception {
        doTestWriteTimeoutServer(true);
    }


    @Test
    public void testWriteTimeoutServerEndpoint() throws Exception {
        doTestWriteTimeoutServer(false);
    }


    private static volatile boolean timeoutOnContainer = false;

    private void doTestWriteTimeoutServer(boolean setTimeoutOnContainer)
            throws Exception {

        /*
         * Note: There are all sorts of horrible uses of statics in this test
         *       because the API uses classes and the tests really need access
         *       to the instances which simply isn't possible.
         */
        timeoutOnContainer = setTimeoutOnContainer;

        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);
        ctx.addApplicationListener(ConstantTxConfig.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        WebSocketContainer wsContainer =
                ContainerProvider.getWebSocketContainer();

        tomcat.start();

        Session wsSession = wsContainer.connectToServer(
                TesterProgrammaticEndpoint.class,
                ClientEndpointConfig.Builder.create().build(),
                new URI("ws://" + getHostName() + ":" + getPort() +
                        ConstantTxConfig.PATH));

        wsSession.addMessageHandler(new BlockingBinaryHandler());

        int loops = 0;
        while (loops < 15) {
            Thread.sleep(1000);
            if (!ConstantTxEndpoint.getRunning()) {
                break;
            }
            loops++;
        }

        // Close the client session, primarily to allow the
        // BackgroundProcessManager to shut down.
        wsSession.close();

        // Check the right exception was thrown
        Assert.assertNotNull(ConstantTxEndpoint.getException());
        Assert.assertEquals(ExecutionException.class,
                ConstantTxEndpoint.getException().getClass());
        Assert.assertNotNull(ConstantTxEndpoint.getException().getCause());
        Assert.assertEquals(SocketTimeoutException.class,
                ConstantTxEndpoint.getException().getCause().getClass());

        // Check correct time passed
        Assert.assertTrue(ConstantTxEndpoint.getTimeout() >= TIMEOUT_MS);

        // Check the timeout wasn't too long
        Assert.assertTrue(ConstantTxEndpoint.getTimeout() < TIMEOUT_MS*2);
    }


    public static class ConstantTxConfig extends WsContextListener {

        private static final String PATH = "/test";

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            super.contextInitialized(sce);
            ServerContainer sc =
                    (ServerContainer) sce.getServletContext().getAttribute(
                            org.apache.tomcat.websocket.server.Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE);
            try {
                sc.addEndpoint(ServerEndpointConfig.Builder.create(
                        ConstantTxEndpoint.class, PATH).build());
                if (timeoutOnContainer) {
                    sc.setAsyncSendTimeout(TIMEOUT_MS);
                }
            } catch (DeploymentException e) {
                throw new IllegalStateException(e);
            }
        }
    }


    public static class ConstantTxEndpoint extends Endpoint {

        // Have to be static to be able to retrieve results from test case
        private static volatile long timeout = -1;
        private static volatile Exception exception = null;
        private static volatile boolean running = true;


        @Override
        public void onOpen(Session session, EndpointConfig config) {

            // Reset everything
            timeout = -1;
            exception = null;
            running = true;

            if (!timeoutOnContainer) {
                session.getAsyncRemote().setSendTimeout(TIMEOUT_MS);
            }

            long lastSend = 0;

            // Should send quickly until the network buffers fill up and then
            // block until the timeout kicks in
            try {
                while (true) {
                    lastSend = System.currentTimeMillis();
                    Future<Void> f = session.getAsyncRemote().sendBinary(
                            ByteBuffer.wrap(MESSAGE_BINARY_4K));
                    f.get();
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

        public static Exception getException() {
            return exception;
        }

        public static boolean getRunning() {
            return running;
        }
    }
}
