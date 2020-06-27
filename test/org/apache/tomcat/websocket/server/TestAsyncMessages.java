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
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.websocket.TesterAsyncTiming;
import org.apache.tomcat.websocket.TesterMessageCountClient.TesterProgrammaticEndpoint;

@Ignore // Test passes but GC delays can introduce false failures.
public class TestAsyncMessages extends TomcatBaseTest {

    @Test
    public void testAsyncTiming() throws Exception {

        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = tomcat.addContext("", null);
        ctx.addApplicationListener(TesterAsyncTiming.Config.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();
        ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().build();
        Session wsSession = wsContainer.connectToServer(
                TesterProgrammaticEndpoint.class,
                clientEndpointConfig,
                new URI("ws://localhost:" + getPort() + TesterAsyncTiming.Config.PATH));

        AsyncTimingClientHandler handler = new AsyncTimingClientHandler();
        wsSession.addMessageHandler(ByteBuffer.class, handler);
        wsSession.getBasicRemote().sendText("Hello");

        System.out.println("Sent Hello message, waiting for data");
        handler.waitForLatch();
        Assert.assertFalse(handler.hasFailed());
    }

    private static class AsyncTimingClientHandler implements MessageHandler.Partial<ByteBuffer> {

        private long lastMessage = 0;
        private int sequence = 0;
        private int count = 0;
        private CountDownLatch latch = new CountDownLatch(1);
        private volatile boolean fail = false;

        @Override
        public void onMessage(ByteBuffer message, boolean last) {
            if (lastMessage == 0) {
                // First message. Don't check
                sequence ++;
                lastMessage = System.nanoTime();
            } else {
                long newTime = System.nanoTime();
                long diff = newTime - lastMessage;
                lastMessage = newTime;

                if (sequence == 0) {
                    sequence ++;
                    if (message.capacity() != 8192) {
                        System.out.println("Expected size 8192 but was [" + message.capacity() + "], count [" + count + "]");
                        fail = true;
                    }
                    if (diff < 40000000) {
                        System.out.println("Expected diff > 40ms but was [" + diff + "], count [" + count + "]");
                        fail = true;
                    }
                } else if (sequence == 1) {
                    sequence ++;
                    if (message.capacity() != 8192) {
                        System.out.println("Expected size 8192 but was [" + message.capacity() + "], count [" + count + "]");
                        fail = true;
                    }
                    if (diff > 500000) {
                        System.out.println("Expected diff < 500,000 but was [" + diff + "], count [" + count + "]");
                        fail = true;
                    }
                } else if (sequence == 2) {
                    sequence = 0;
                    if (message.capacity() != 4096) {
                        System.out.println("Expected size 4096 but was [" + message.capacity() + "], count [" + count + "]");
                        fail = true;
                    }
                    if (diff > 500000) {
                        System.out.println("Expected diff < 500,000 but was [" + diff + "], count [" + count + "]");
                        fail = true;
                    }
                }
            }

            count ++;
            if (count >= TesterAsyncTiming.Config.ITERATIONS * 3) {
                latch.countDown();
            }
        }

        public void waitForLatch() throws InterruptedException {
            latch.await();
        }

        public boolean hasFailed() {
            return fail;
        }
    }
}
