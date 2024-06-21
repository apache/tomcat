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

import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import jakarta.websocket.server.ServerEndpointConfig;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.websocket.WebSocketBaseTest;
import org.apache.tomcat.websocket.WsSession;

/*
 * https://bz.apache.org/bugzilla/show_bug.cgi?id=66508
 *
 * If the client sends a close while the server waiting for the client before sending the rest of a message, the
 * processing of the close from the client can hang until the sending of the message times out.
 *
 * This is packaged in a separate class to allow test specific parameterisation.
 */
@RunWith(Parameterized.class)
public class TestWsRemoteEndpointImplServerDeadlock extends WebSocketBaseTest {

    @Parameterized.Parameters(name = "{index}: useAsyncIO[{0}], sendOnContainerThread[{1}]")
    public static Collection<Object[]> parameters() {

        List<Object[]> parameterSets = new ArrayList<>();

        for (Boolean useAsyncIO : booleans) {
            for (Boolean sendOnContainerThread : booleans) {
                parameterSets.add(new Object[] { useAsyncIO, sendOnContainerThread });
            }
        }

        return parameterSets;
    }

    @Parameter(0)
    public Boolean useAsyncIO;

    @Parameter(1)
    public Boolean sendOnContainerThread;

    /*
     * Statics used to pass state to instances that are configured and created by class name so there is no easy way to
     * configure the created instances directly.
     *
     * Every component that uses these statics takes a local copy ASAP to avoid issues with previous test runs retaining
     * references to the instance stored in the static and interfering with the current test run.
     */
    private static volatile boolean initialSendOnContainerThread;
    private static volatile CountDownLatch initialServerSendLatch;
    private static volatile CountDownLatch initialClientReceiveLatch;

    @Test
    public void testTemporaryDeadlockOnClientClose() throws Exception {
        // Configure the statics
        initialSendOnContainerThread = sendOnContainerThread.booleanValue();
        initialServerSendLatch = new CountDownLatch(1);
        initialClientReceiveLatch = new CountDownLatch(1);

        // Local copies of the statics used in this method
        CountDownLatch serverSendLatch = initialServerSendLatch;
        CountDownLatch clientReceiveLatch = initialClientReceiveLatch;

        Tomcat tomcat = getTomcatInstance();
        Assert.assertTrue(tomcat.getConnector().setProperty("useAsyncIO", useAsyncIO.toString()));

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();
        ctx.addApplicationListener(Bug66508Config.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();

        tomcat.start();

        Bug66508Client client = new Bug66508Client();
        URI uri = new URI("ws://localhost:" + getPort() + Bug66508Config.PATH);

        Session session = wsContainer.connectToServer(client, uri);
        // Server starts to send messages.
        // Wait for server sending to block.
        serverSendLatch.await();
        // Server buffers are full. Server cannot send any more messages.
        // Server is now blocked waiting for the client to read the messages.

        // Set a short session close timeout (milliseconds)
        session.getUserProperties().put(
            org.apache.tomcat.websocket.Constants.SESSION_CLOSE_TIMEOUT_PROPERTY, Long.valueOf(2000));
        // Close the session from the client
        session.close();

        // Wait for server to complete sending the close message
        // This is the process that deadlocks when the bug is experienced
        Field f = WsSession.class.getDeclaredField("state");
        f.setAccessible(true);
        Object state = f.get(Bug66508Endpoint.serverSession);
        int count = 0;
        long start = System.nanoTime();
        // Send times out after 20s so test should complete in less than that. Allow large margin as VMs can sometimes
        // be slow when running tests.
        while (!"CLOSED".equals(state.toString()) && count < 190) {
            count++;
            Thread.sleep(100);
            state = f.get(Bug66508Endpoint.serverSession);
            if (count == 10) {
                // If deadlock is present, this should be long enough to trigger it.
                // Release the client latch so it starts processing messages again else the server will never be able to
                // send the close message.
                clientReceiveLatch.countDown();
            }
        }
        long closeDelay = System.nanoTime() - start;

        Assert.assertTrue("Close delay was [" + closeDelay + "] ns", closeDelay < 10_000_000_000L);

    }

    public static class Bug66508Config extends TesterEndpointConfig {

        public static final String PATH = "/bug66508";


        @Override
        protected ServerEndpointConfig getServerEndpointConfig() {
            return ServerEndpointConfig.Builder.create(Bug66508Endpoint.class, PATH).build();
        }
    }

    public static class Bug66508Endpoint {

        // 1024k message
        private static final String MSG = "a".repeat(1024 * 8);

        private static volatile Session serverSession = null;
        private CountDownLatch serverSendLatch = initialServerSendLatch;
        private boolean sendOnContainerThread = initialSendOnContainerThread;

        @OnOpen
        public void onOpen(Session session) {
            serverSession = session;
            // Send messages to the client until they appear to hang
            // Need to do this on a non-container thread
            Runnable r = () -> {
                Future<Void> sendMessageFuture;
                while (true) {
                    sendMessageFuture = session.getAsyncRemote().sendText(MSG);
                    try {
                        sendMessageFuture.get(2, TimeUnit.SECONDS);
                    } catch (InterruptedException | ExecutionException | TimeoutException e) {
                        break;
                    }
                }
                serverSendLatch.countDown();
            };
            if (sendOnContainerThread) {
                r.run();
            } else {
                new Thread(r).start();
            }
        }

        @OnError
        public void onError(@SuppressWarnings("unused") Throwable t) {
            // Expected. Swallow the error.
        }
    }

    @ClientEndpoint
    public static class Bug66508Client {

        private CountDownLatch clientReceiveLatch = initialClientReceiveLatch;

        @OnMessage
        public void onMessage(@SuppressWarnings("unused") String msg) {
            try {
                // Block client from processing messages
                clientReceiveLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
