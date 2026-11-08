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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.DecodeException;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import jakarta.websocket.server.ServerEndpoint;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.websocket.pojo.TesterUtil.ServerConfigListener;
import org.apache.tomcat.websocket.pojo.TesterUtil.SingletonConfigurator;

public class TestPojoMessageHandlerWholeText extends TomcatBaseTest {

    private static final long TIMEOUT_SECONDS = 10;

    /*
     * A @OnMessage method with a char parameter cannot be satisfied by an empty text message. That must be reported to
     * the endpoint as a DecodeException via onError and must not close the connection.
     */
    @Test
    public void testEmptyTextForCharParameter() throws Exception {
        CharServer server = new CharServer();

        Session session = connect(server, CharServer.class);

        session.getBasicRemote().sendText("");

        Throwable error = server.errors.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Assert.assertNotNull("No error reported to the endpoint", error);
        Assert.assertTrue("Expected a DecodeException, got: " + error, error instanceof DecodeException);

        // The connection must still be usable after the decode error.
        session.getBasicRemote().sendText("z");
        Character received = server.messages.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Assert.assertEquals("Endpoint did not receive the message sent after the decode error", Character.valueOf('z'),
                received);

        session.close();
    }


    /*
     * A @OnMessage method with an int parameter cannot be satisfied by a non-numeric text message. That must be
     * reported to the endpoint as a DecodeException via onError and must not close the connection.
     */
    @Test
    public void testNonNumericTextForIntParameter() throws Exception {
        IntServer server = new IntServer();

        Session session = connect(server, IntServer.class);

        session.getBasicRemote().sendText("abc");

        Throwable error = server.errors.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Assert.assertNotNull("No error reported to the endpoint", error);
        Assert.assertTrue("Expected a DecodeException, got: " + error, error instanceof DecodeException);

        // The connection must still be usable after the decode error.
        session.getBasicRemote().sendText("123");
        Integer received = server.messages.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Assert.assertEquals("Endpoint did not receive the message sent after the decode error",
                Integer.valueOf(123), received);

        session.close();
    }


    private Session connect(Object pojo, Class<?> pojoClass) throws Exception {
        SingletonConfigurator.setInstance(pojo);
        ServerConfigListener.setPojoClazz(pojoClass);

        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = getProgrammaticRootContext();
        ctx.addApplicationListener(ServerConfigListener.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMapping("/", "default");

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();

        tomcat.start();

        return wsContainer.connectToServer(new TesterUtil.SimpleClient(),
                new URI("ws://localhost:" + getPort() + "/" + pojoClass.getSimpleName().toLowerCase()));
    }


    @ServerEndpoint(value = "/charserver", configurator = SingletonConfigurator.class)
    public static class CharServer {

        final BlockingQueue<Throwable> errors = new LinkedBlockingQueue<>();
        final BlockingQueue<Character> messages = new LinkedBlockingQueue<>();

        @OnMessage
        public void text(char c) {
            messages.add(Character.valueOf(c));
        }


        @OnError
        public void error(Throwable t) {
            errors.add(t);
        }
    }


    @ServerEndpoint(value = "/intserver", configurator = SingletonConfigurator.class)
    public static class IntServer {

        final BlockingQueue<Throwable> errors = new LinkedBlockingQueue<>();
        final BlockingQueue<Integer> messages = new LinkedBlockingQueue<>();

        @OnMessage
        public void text(int i) {
            messages.add(Integer.valueOf(i));
        }


        @OnError
        public void error(Throwable t) {
            errors.add(t);
        }
    }
}
