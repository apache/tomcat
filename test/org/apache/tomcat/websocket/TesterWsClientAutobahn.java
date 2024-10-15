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
import java.util.concurrent.CountDownLatch;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Endpoint;
import jakarta.websocket.Extension;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;

import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.websocket.pojo.PojoEndpointClient;

/**
 * Runs the Autobahn test suite in client mode for testing the WebSocket client implementation.
 */
public class TesterWsClientAutobahn {

    private static final String HOST = "localhost";
    private static final int PORT = 9001;
    private static final String USER_AGENT = "ApacheTomcat8WebSocketClient";


    public static void main(String[] args) throws Exception {

        WebSocketContainer wsc = ContainerProvider.getWebSocketContainer();

        int testCaseCount = getTestCaseCount(wsc);
        System.out.println("There are " + testCaseCount + " test cases");
        for (int testCase = 1; testCase <= testCaseCount; testCase++) {
            if (testCase % 50 == 0) {
                System.out.println(testCase);
            } else {
                System.out.print('.');
            }
            try {
                executeTestCase(wsc, testCase);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                t.printStackTrace();
            }

        }
        System.out.println("Testing complete");
        updateReports(wsc);
    }


    private static int getTestCaseCount(WebSocketContainer wsc) throws Exception {

        URI uri = new URI("ws://" + HOST + ":" + PORT + "/getCaseCount");
        CaseCountClient caseCountClient = new CaseCountClient();
        wsc.connectToServer(caseCountClient, uri);
        return caseCountClient.getCaseCount();
    }


    private static void executeTestCase(WebSocketContainer wsc, int testCase) throws Exception {
        URI uri = new URI("ws://" + HOST + ":" + PORT + "/runCase?case=" + testCase + "&agent=" + USER_AGENT);
        TestCaseClient testCaseClient = new TestCaseClient();

        Extension permessageDeflate = new WsExtension("permessage-deflate");
        // Advertise support for client_max_window_bits
        // Client only supports some values so there will be some failures here
        // Note Autobahn returns a 400 response if you provide a value for
        // client_max_window_bits
        permessageDeflate.getParameters().add(new WsExtensionParameter("client_max_window_bits", null));
        List<Extension> extensions = new ArrayList<>(1);
        extensions.add(permessageDeflate);

        Endpoint ep = new PojoEndpointClient(testCaseClient, null, null);
        ClientEndpointConfig.Builder builder = ClientEndpointConfig.Builder.create();
        ClientEndpointConfig config = builder.extensions(extensions).build();

        wsc.connectToServer(ep, config, uri);
        testCaseClient.waitForClose();
    }


    private static void updateReports(WebSocketContainer wsc) throws Exception {

        URI uri = new URI("ws://" + HOST + ":" + PORT + "/updateReports?agent=" + USER_AGENT);
        UpdateReportsClient updateReportsClient = new UpdateReportsClient();
        wsc.connectToServer(updateReportsClient, uri);
    }


    @ClientEndpoint
    public static class CaseCountClient {

        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile int caseCount = 0;

        // Need to wait for message
        public int getCaseCount() throws InterruptedException {
            latch.await();
            return caseCount;
        }

        @OnMessage
        public void onMessage(String msg) {
            latch.countDown();
            caseCount = Integer.parseInt(msg);
        }


        @OnError
        public void onError(Throwable t) {
            latch.countDown();
            t.printStackTrace();
        }
    }


    @ClientEndpoint
    public static class TestCaseClient {

        private final CountDownLatch latch = new CountDownLatch(1);

        public void waitForClose() throws InterruptedException {
            latch.await();
        }

        @OnMessage
        public void echoTextMessage(Session session, String msg, boolean last) {
            try {
                if (session.isOpen()) {
                    session.getBasicRemote().sendText(msg, last);
                }
            } catch (IOException e) {
                try {
                    session.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        }

        @OnMessage
        public void echoBinaryMessage(Session session, ByteBuffer bb, boolean last) {
            try {
                if (session.isOpen()) {
                    session.getBasicRemote().sendBinary(bb, last);
                }
            } catch (IOException e) {
                try {
                    session.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        }

        @OnClose
        public void releaseLatch() {
            latch.countDown();
        }
    }


    @ClientEndpoint
    public static class UpdateReportsClient {

        private final CountDownLatch latch = new CountDownLatch(1);

        public void waitForClose() throws InterruptedException {
            latch.await();
        }

        @OnClose
        public void onClose() {
            latch.countDown();
        }
    }
}
