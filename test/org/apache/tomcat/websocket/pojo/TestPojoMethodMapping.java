/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.websocket.pojo;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.websocket.pojo.TesterUtil.ServerConfigListener;
import org.apache.tomcat.websocket.pojo.TesterUtil.SimpleClient;
import org.apache.tomcat.websocket.pojo.TesterUtil.SingletonConfigurator;

public class TestPojoMethodMapping extends TomcatBaseTest {

    private static final String PARAM_ONE = "abcde";
    private static final String PARAM_TWO = "12345";
    private static final String PARAM_THREE = "true";

    @Test
    public void test() throws Exception {

        // Set up utility classes
        Server server = new Server();
        SingletonConfigurator.setInstance(server);
        ServerConfigListener.setPojoClazz(Server.class);

        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = getProgrammaticRootContext();
        ctx.addApplicationListener(ServerConfigListener.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMapping("/", "default");

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();


        tomcat.start();

        SimpleClient client = new SimpleClient();
        URI uri = new URI("ws://localhost:" + getPort() + "/" + PARAM_ONE + "/" + PARAM_TWO + "/" + PARAM_THREE);

        Session session = wsContainer.connectToServer(client, uri);
        session.getBasicRemote().sendText("NO-OP");
        session.close();

        // Give server 20s to close. 5s should be plenty but the Gump VM is slow
        int count = 0;
        while (count < 200) {
            if (server.isClosed()) {
                break;
            }
            count++;
            Thread.sleep(100);
        }
        if (count == 50) {
            Assert.fail("Server did not process an onClose event within 5 " +
                    "seconds of the client sending a close message");
        }

        // Check no errors
        List<String> errors = server.getErrors();
        for (String error : errors) {
            System.err.println(error);
        }
        Assert.assertEquals("Found errors", 0, errors.size());
    }


    /*
     * Only String, byte[] and ByteBuffer payloads (plus the boolean last flag) are permitted for partial messages. A
     * primitive payload combined with a boolean last flag must be rejected at deployment time rather than resulting in
     * a partial message handler that fails when the first fragmented message arrives.
     */
    @Test
    public void testPrimitiveAndBooleanParameters() throws Exception {
        try {
            @SuppressWarnings("unused")
            PojoMethodMapping mapping = new PojoMethodMapping(PrimitiveAndBooleanServer.class, new ArrayList<>(),
                    "/primitiveAndBoolean", null);
            Assert.fail("Expected a DeploymentException");
        } catch (DeploymentException de) {
            Assert.assertTrue("Unexpected message: " + de.getMessage(), de.getMessage().contains("primitive type"));
        }
    }


    @ServerEndpoint("/primitiveAndBoolean")
    public static final class PrimitiveAndBooleanServer {

        @OnMessage
        public void onMessage(@SuppressWarnings("unused") int payload, @SuppressWarnings("unused") boolean last) {
            // No-Op
        }
    }


    /*
     * A sub-class method that overrides an annotated super class method using a covariant return type is still an
     * override. Re-declaring the annotation on the override must not be treated as a duplicate annotation and the
     * sub-class method must be the one that is mapped.
     */
    @Test
    public void testCovariantReturnOverride() throws Exception {
        PojoMethodMapping mapping =
                new PojoMethodMapping(CovariantReturnServer.class, new ArrayList<>(), "/covariantReturn", null);
        Assert.assertEquals(CovariantReturnServer.class.getMethod("onOpen", Session.class), mapping.getOnOpen());
    }


    /*
     * A non-annotated sub-class method that overrides an annotated super class method using a covariant return type
     * should result in the super class method being ignored. Note that a compiler generated bridge method previously
     * masked this in compiled code.
     */
    @Test
    public void testCovariantReturnOverrideWithoutAnnotation() throws Exception {
        PojoMethodMapping mapping = new PojoMethodMapping(CovariantReturnOverrideServer.class, new ArrayList<>(),
                "/covariantReturnOverride", null);
        Assert.assertNull(mapping.getOnOpen());
    }


    /*
     * A sub-class method that is not an override of the annotated super class method must still be rejected as a
     * duplicate annotation if it handles the same message type.
     */
    @Test
    public void testAnnotatedMethodNotAnOverride() throws Exception {
        try {
            @SuppressWarnings("unused")
            PojoMethodMapping mapping =
                    new PojoMethodMapping(NotAnOverrideServer.class, new ArrayList<>(), "/notAnOverride", null);
            Assert.fail("Expected a DeploymentException");
        } catch (DeploymentException de) {
            Assert.assertTrue("Unexpected message: " + de.getMessage(),
                    de.getMessage().contains("Duplicate annotations"));
        }
    }


    public static class CovariantReturnBase {

        @OnOpen
        public Object onOpen(Session session) {
            return session;
        }

        @OnMessage
        public Object onMessage(String message) {
            return message;
        }
    }


    @ServerEndpoint("/covariantReturn")
    public static final class CovariantReturnServer extends CovariantReturnBase {

        @Override
        @OnOpen
        public String onOpen(Session session) {
            return session.toString();
        }

        @Override
        @OnMessage
        public String onMessage(String message) {
            return message;
        }
    }


    @ServerEndpoint("/covariantReturnOverride")
    public static final class CovariantReturnOverrideServer extends CovariantReturnBase {

        @Override
        public String onOpen(Session session) {
            return session.toString();
        }

        @Override
        public String onMessage(String message) {
            return message;
        }
    }


    @ServerEndpoint("/notAnOverride")
    public static final class NotAnOverrideServer extends CovariantReturnBase {

        @OnMessage
        public String otherMessage(String message) {
            return message;
        }
    }


    @ServerEndpoint(value = "/{one}/{two}/{three}", configurator = SingletonConfigurator.class)
    public static final class Server {

        private final List<String> errors = new ArrayList<>();
        private volatile boolean closed;

        @OnOpen
        public void onOpen(@PathParam("one") String p1, @PathParam("two") int p2, @PathParam("three") boolean p3) {
            checkParams("onOpen", p1, p2, p3);
        }

        @OnMessage
        public void onMessage(@SuppressWarnings("unused") String msg, @PathParam("one") String p1,
                @PathParam("two") int p2, @PathParam("three") boolean p3) {
            checkParams("onMessage", p1, p2, p3);
        }

        @OnClose
        public void onClose(@PathParam("one") String p1, @PathParam("two") int p2, @PathParam("three") boolean p3) {
            checkParams("onClose", p1, p2, p3);
            closed = true;
        }

        public List<String> getErrors() {
            return errors;
        }

        public boolean isClosed() {
            return closed;
        }

        private void checkParams(String method, String p1, int p2, boolean p3) {
            checkParam(method, PARAM_ONE, p1);
            checkParam(method, PARAM_TWO, Integer.toString(p2));
            checkParam(method, PARAM_THREE, Boolean.toString(p3));
        }

        private void checkParam(String method, String expected, String actual) {
            if (!expected.equals(actual)) {
                errors.add("Method [" + method + "]. Expected [" + expected + "] was + [" + actual + "]");
            }
        }
    }
}
