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

import java.io.Writer;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig.Builder;
import javax.websocket.ContainerProvider;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.websocket.TesterSingleMessageClient.AsyncHandler;
import org.apache.tomcat.websocket.TesterSingleMessageClient.AsyncText;
import org.apache.tomcat.websocket.TesterSingleMessageClient.TesterEndpoint;

public class TestWsRemoteEndpoint extends TomcatBaseTest {

    private static final String SEQUENCE = "ABCDE";
    private static final int S_LEN = SEQUENCE.length();
    private static final String TEST_MESSAGE_5K;

    static {
        StringBuilder sb = new StringBuilder(S_LEN * 1024);
        for (int i = 0; i < 1024; i++) {
            sb.append(SEQUENCE);
        }
        TEST_MESSAGE_5K = sb.toString();
    }

    @Test
    public void testWriter() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));
        ctx.addApplicationListener(TesterEchoServer.Config.class.getName());

        WebSocketContainer wsContainer =
                ContainerProvider.getWebSocketContainer();

        tomcat.start();

        Session wsSession = wsContainer.connectToServer(TesterEndpoint.class,
                Builder.create().build(),
                new URI("http://localhost:" + getPort() +
                        TesterEchoServer.Config.PATH_ASYNC));

        CountDownLatch latch = new CountDownLatch(1);
        wsSession.getUserProperties().put("latch", latch);
        AsyncHandler<?> handler = new AsyncText(latch);

        wsSession.addMessageHandler(handler);

        Writer w = wsSession.getBasicRemote().getSendWriter();

        for (int i = 0; i < 8; i++) {
            w.write(TEST_MESSAGE_5K);
        }

        w.close();

        boolean latchResult = handler.getLatch().await(10, TimeUnit.SECONDS);

        Assert.assertTrue(latchResult);

        List<String> messages = (List<String>) handler.getMessages();

        int offset = 0;
        int i = 0;
        for (String message : messages) {
            // First may be a fragment
            Assert.assertEquals(SEQUENCE.substring(offset, S_LEN),
                    message.substring(0, S_LEN - offset));
            i = S_LEN - offset;
            while (i + S_LEN < message.length()) {
                if (!SEQUENCE.equals(message.substring(i, i + S_LEN))) {
                    Assert.fail();
                }
                i += S_LEN;
            }
            offset = message.length() - i;
            if (!SEQUENCE.substring(0, offset).equals(message.substring(i))) {
                Assert.fail();
            }
        }
    }
}
