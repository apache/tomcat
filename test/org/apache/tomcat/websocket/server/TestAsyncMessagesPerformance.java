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
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.websocket.TesterAsyncTiming;
import org.apache.tomcat.websocket.TesterMessageCountClient.TesterProgrammaticEndpoint;

/*
 * This test is very timing sensitive. Any failures need to be checked to see if the thresholds just need adjusting to
 * support a wider range of platforms and/or Java versions or if the failure is an indication of a performance drop in
 * the WebSocket implementation.
 */
public class TestAsyncMessagesPerformance extends TomcatBaseTest {

    @Test
    public void testAsyncTiming() throws Exception {

        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = getProgrammaticRootContext();
        ctx.addApplicationListener(TesterAsyncTiming.Config.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();
        ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().build();
        Session wsSession = wsContainer.connectToServer(TesterProgrammaticEndpoint.class, clientEndpointConfig,
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
        private long seqZeroTimingFailureCount = 0;
        private long seqOneTimingFailureCount = 0;
        private long seqTwoTimingFailureCount = 0;

        private CountDownLatch latch = new CountDownLatch(1);
        private volatile boolean fail = false;

        @Override
        public void onMessage(ByteBuffer message, boolean last) {
            // Expected received data is:
            // 1 * 16k message in 2 * 8k chunks
            // 1 * 4k message in 1 * 4k chunk
            // 50 ms pause
            // loop
            if (lastMessage == 0) {
                // First message. Don't check
                sequence++;
                lastMessage = System.nanoTime();
            } else {
                long newTime = System.nanoTime();
                long diff = newTime - lastMessage;
                lastMessage = newTime;

                if (sequence == 0) {
                    sequence++;
                    if (message.capacity() != 8192) {
                        System.out.println(
                                "SEQ0: Expected size 8192 but was [" + message.capacity() + "], count [" + count + "]");
                        fail = true;
                    }
                    if (diff < 40000000) {
                        System.out.println("SEQ0: Expected diff > 40ms but was [" + diff + "], count [" + count + "]");
                        seqZeroTimingFailureCount++;
                    }
                } else if (sequence == 1) {
                    sequence++;
                    if (message.capacity() != 8192) {
                        System.out.println(
                                "SEQ1: Expected size 8192 but was [" + message.capacity() + "], count [" + count + "]");
                        fail = true;
                    }
                    // Gap between 2* 8k chunks of 16k message expected to be less than 0.5ms
                    if (diff > 500000) {
                        System.out.println("SEQ1: Expected diff < 500,000 but was [" + diff + "], count [" + count + "]");
                        seqOneTimingFailureCount++;
                    }
                } else if (sequence == 2) {
                    sequence = 0;
                    if (message.capacity() != 4096) {
                        System.out.println(
                                "SEQ2: Expected size 4096 but was [" + message.capacity() + "], count [" + count + "]");
                        fail = true;
                    }
                    // Gap between 16k message and 4k message expected to be less than 0.5ms
                    if (diff > 500000) {
                        System.out.println("SEQ2: Expected diff < 500,000 but was [" + diff + "], count [" + count + "]");
                        seqTwoTimingFailureCount++;
                    }
                }
            }

            count++;
            if (count >= TesterAsyncTiming.Config.ITERATIONS * 3) {
                latch.countDown();
            }
        }

        public void waitForLatch() throws InterruptedException {
            latch.await();
        }

        public boolean hasFailed() {
            // Total iterations are 1500
            if (!fail) {
                if (seqZeroTimingFailureCount > 1) {
                    // The 50ms pause after the short message may very rarely appear to be less than 40ms
                    fail = true;
                } else if (seqOneTimingFailureCount > 10) {
                    // The two chunks of the 16k message may rarely be more than 0.5ms apart
                    fail = true;
                } else if (seqTwoTimingFailureCount > 100) {
                    // The short message may often be more than 0.5ms after the long message
                    fail = true;
                }
            }
            return fail;
        }
    }
}
