/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.ServletContextEvent;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.CloseReason;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.websocket.server.Constants;
import org.apache.tomcat.websocket.server.WsContextListener;

/*
 * This method is split out into a separate class to make it easier to track the
 * various permutations and combinations of client and server endpoints.
 *
 * Each test uses 2 client endpoint and 2 server endpoints with each client
 * connecting to each server for a total of four connections (note sometimes
 * the two clients and/or the two servers will be the same).
 */
public class TestWsWebSocketContainerGetOpenSessions extends WebSocketBaseTest {

    @Test
    public void testClientAClientAPojoAPojoA() throws Exception {
        Endpoint client1 = new ClientEndpointA();
        Endpoint client2 = new ClientEndpointA();

        doTest(client1, client2, "/pojoA", "/pojoA", 2, 2, 4, 4);
    }


    @Test
    public void testClientAClientBPojoAPojoA() throws Exception {
        Endpoint client1 = new ClientEndpointA();
        Endpoint client2 = new ClientEndpointB();

        doTest(client1, client2, "/pojoA", "/pojoA", 2, 2, 4, 4);
    }


    @Test
    public void testClientAClientAPojoAPojoB() throws Exception {
        Endpoint client1 = new ClientEndpointA();
        Endpoint client2 = new ClientEndpointA();

        doTest(client1, client2, "/pojoA", "/pojoB", 2, 2, 2, 2);
    }


    @Test
    public void testClientAClientBPojoAPojoB() throws Exception {
        Endpoint client1 = new ClientEndpointA();
        Endpoint client2 = new ClientEndpointB();

        doTest(client1, client2, "/pojoA", "/pojoB", 2, 2, 2, 2);
    }


    @Test
    public void testClientAClientAProgAProgA() throws Exception {
        Endpoint client1 = new ClientEndpointA();
        Endpoint client2 = new ClientEndpointA();

        doTest(client1, client2, "/progA", "/progA", 2, 2, 4, 4);
    }


    @Test
    public void testClientAClientBProgAProgA() throws Exception {
        Endpoint client1 = new ClientEndpointA();
        Endpoint client2 = new ClientEndpointB();

        doTest(client1, client2, "/progA", "/progA", 2, 2, 4, 4);
    }


    @Test
    public void testClientAClientAProgAProgB() throws Exception {
        Endpoint client1 = new ClientEndpointA();
        Endpoint client2 = new ClientEndpointA();

        doTest(client1, client2, "/progA", "/progB", 2, 2, 2, 2);
    }


    @Test
    public void testClientAClientBProgAProgB() throws Exception {
        Endpoint client1 = new ClientEndpointA();
        Endpoint client2 = new ClientEndpointB();

        doTest(client1, client2, "/progA", "/progB", 2, 2, 2, 2);
    }


    @Test
    public void testClientAClientAPojoAProgA() throws Exception {
        Endpoint client1 = new ClientEndpointA();
        Endpoint client2 = new ClientEndpointA();

        doTest(client1, client2, "/pojoA", "/progA", 2, 2, 2, 2);
    }


    @Test
    public void testClientAClientBPojoAProgA() throws Exception {
        Endpoint client1 = new ClientEndpointA();
        Endpoint client2 = new ClientEndpointB();

        doTest(client1, client2, "/pojoA", "/progA", 2, 2, 2, 2);
    }


    @Test
    public void testClientAClientAPojoAProgB() throws Exception {
        Endpoint client1 = new ClientEndpointA();
        Endpoint client2 = new ClientEndpointA();

        doTest(client1, client2, "/pojoA", "/progB", 2, 2, 2, 2);
    }


    @Test
    public void testClientAClientBPojoAProgB() throws Exception {
        Endpoint client1 = new ClientEndpointA();
        Endpoint client2 = new ClientEndpointB();

        doTest(client1, client2, "/pojoA", "/progB", 2, 2, 2, 2);
    }


    private void doTest(Endpoint client1, Endpoint client2, String server1, String server2,
            int client1Count, int client2Count, int server1Count, int server2Count) throws Exception {
        Tracker.reset();
        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = tomcat.addContext("", null);
        ctx.addApplicationListener(Config.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();

        Session sClient1Server1 = createSession(wsContainer, client1, "client1", server1);
        Session sClient1Server2 = createSession(wsContainer, client1, "client1", server2);
        Session sClient2Server1 = createSession(wsContainer, client2, "client2", server1);
        Session sClient2Server2 = createSession(wsContainer, client2, "client2", server2);


        int delayCount = 0;
        // Wait for up to 20s for this to complete. It should be a lot faster
        // but some CI systems get be slow at times.
        while (Tracker.getUpdateCount() < 8 && delayCount < 400) {
            Thread.sleep(50);
            delayCount++;
        }

        Assert.assertTrue(Tracker.dump(), Tracker.checkRecord("client1", client1Count));
        Assert.assertTrue(Tracker.dump(), Tracker.checkRecord("client2", client2Count));
        // Note: need to strip leading '/' from path
        Assert.assertTrue(Tracker.dump(), Tracker.checkRecord(server1.substring(1), server1Count));
        Assert.assertTrue(Tracker.dump(), Tracker.checkRecord(server2.substring(1), server2Count));

        sClient1Server1.close();
        sClient1Server2.close();
        sClient2Server1.close();
        sClient2Server2.close();
    }


    private Session createSession(WebSocketContainer wsContainer, Endpoint client,
            String clientName, String server)
            throws DeploymentException, IOException, URISyntaxException {

        Session s = wsContainer.connectToServer(client,
                ClientEndpointConfig.Builder.create().build(),
                new URI("ws://localhost:" + getPort() + server));
        Tracker.addRecord(clientName, s.getOpenSessions().size());
        s.getBasicRemote().sendText("X");
        return s;
    }


    public static class Config extends WsContextListener {

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            super.contextInitialized(sce);
            ServerContainer sc =
                    (ServerContainer) sce.getServletContext().getAttribute(
                            Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE);

            try {
                sc.addEndpoint(PojoEndpointA.class);
                sc.addEndpoint(PojoEndpointB.class);
                sc.addEndpoint(ServerEndpointConfig.Builder.create(
                        ServerEndpointA.class, "/progA").build());
                sc.addEndpoint(ServerEndpointConfig.Builder.create(
                        ServerEndpointB.class, "/progB").build());
            } catch (DeploymentException e) {
                throw new IllegalStateException(e);
            }
        }
    }


    public abstract static class PojoEndpointBase {

        @OnMessage
        public void onMessage(@SuppressWarnings("unused") String msg, Session session) {
            Tracker.addRecord(getTrackingName(), session.getOpenSessions().size());
        }

        protected abstract String getTrackingName();
    }


    @ServerEndpoint("/pojoA")
    public static class PojoEndpointA extends PojoEndpointBase {

        @Override
        protected String getTrackingName() {
            return "pojoA";
        }
    }


    @ServerEndpoint("/pojoB")
    public static class PojoEndpointB extends PojoEndpointBase {

        @Override
        protected String getTrackingName() {
            return "pojoB";
        }
    }


    public abstract static class ServerEndpointBase extends Endpoint{

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            session.addMessageHandler(new TrackerMessageHandler(session, getTrackingName()));
        }

        protected abstract String getTrackingName();
    }


    public static final class ServerEndpointA extends ServerEndpointBase {

        @Override
        protected String getTrackingName() {
            return "progA";
        }
    }


    public static final class ServerEndpointB extends ServerEndpointBase {

        @Override
        protected String getTrackingName() {
            return "progB";
        }
    }


    public static final class TrackerMessageHandler implements MessageHandler.Whole<String> {

        private final Session session;
        private final String trackingName;

        public TrackerMessageHandler(Session session, String trackingName) {
            this.session = session;
            this.trackingName = trackingName;
        }

        @Override
        public void onMessage(String message) {
            Tracker.addRecord(trackingName, session.getOpenSessions().size());
        }
    }


    public abstract static class ClientEndpointBase extends Endpoint {

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            // NO-OP
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {
            // NO-OP
        }

        protected abstract String getTrackingName();
    }


    public static final class ClientEndpointA extends ClientEndpointBase {

        @Override
        protected String getTrackingName() {
            return "clientA";
        }
    }


    public static final class ClientEndpointB extends ClientEndpointBase {

        @Override
        protected String getTrackingName() {
            return "clientB";
        }
    }


    public static class Tracker {

        private static final Map<String, Integer> records = new HashMap<>();
        private static int updateCount = 0;

        public static synchronized void addRecord(String key, int count) {
            // Need to avoid out of order updates to the Map. If out of order
            // updates occur, keep the one with the highest count.
            Integer oldCount = records.get(key);
            if (oldCount == null || oldCount.intValue() < count) {
                records.put(key, Integer.valueOf(count));
            }
            updateCount++;
        }

        public static synchronized boolean checkRecord(String key, int expectedCount) {
            Integer actualCount = records.get(key);
            if (actualCount == null) {
                if (expectedCount == 0) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return actualCount.intValue() == expectedCount;
            }
        }

        public static synchronized int getUpdateCount() {
            return updateCount;
        }

        public static synchronized void reset() {
            records.clear();
            updateCount = 0;
        }

        public static synchronized String dump() {
            return records.toString();
        }
    }
}
