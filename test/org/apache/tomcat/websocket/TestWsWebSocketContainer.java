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

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ContainerProvider;
import javax.websocket.DefaultClientConfiguration;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.junit.Assert;
//import org.junit.Test;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestWsWebSocketContainer extends TomcatBaseTest {

    private static final String MESSAGE_STRING_1 = "qwerty";

    //@Test Disable until client implementation progresses
    public void testConnectToServerEndpoint() throws Exception {
        // Examples app includes WebSocket Echo endpoint
        Tomcat tomcat = getTomcatInstance();
        File appDir = new File(getBuildDirectory(), "webapps/examples");
        tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider.getClientContainer();
        Session wsSession = wsContainer.connectToServer(TesterEndpoint.class,
                new DefaultClientConfiguration(), new URI("http://localhost:" +
                        getPort() + "/examples/echoAnnotation"));
        TesterMessageHandlerString handler = new TesterMessageHandlerString(1);
        wsSession.addMessageHandler(handler);
        wsSession.getRemote().sendString(MESSAGE_STRING_1);

        boolean latchResult = handler.getLatch().await(10, TimeUnit.SECONDS);

        Assert.assertTrue(latchResult);

        List<String> messages = handler.getMessages();
        Assert.assertEquals(1, messages.size());
        Assert.assertEquals(MESSAGE_STRING_1, messages.get(0));
    }

    private static class TesterMessageHandlerString
            implements MessageHandler.Basic<String> {

        private final CountDownLatch latch;

        private List<String> messages = new ArrayList<>();

        public TesterMessageHandlerString(int latchCount) {
            if (latchCount > 0) {
                latch = new CountDownLatch(latchCount);
            } else {
                latch = null;
            }
        }

        public List<String> getMessages() {
            return messages;
        }

        public CountDownLatch getLatch() {
            return latch;
        }

        @Override
        public void onMessage(String message) {
            if (latch != null) {
                latch.countDown();
            }
            messages.add(message);
        }
    }

    private static class TesterEndpoint extends Endpoint {

        @Override
        public void onOpen(Session session, EndpointConfiguration config) {
            // TODO Auto-generated method stub
        }
    }
}
