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
package org.apache.tomcat.websocket;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.ClientEndpointConfig.Configurator;
import javax.websocket.ContainerProvider;
import javax.websocket.HandshakeResponse;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.authenticator.AuthenticatorBase;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.apache.tomcat.websocket.TesterMessageCountClient.BasicText;
import org.apache.tomcat.websocket.TesterMessageCountClient.TesterEndpoint;
import org.apache.tomcat.websocket.TesterMessageCountClient.TesterProgrammaticEndpoint;

public class TestWebSocketFrameClient extends WebSocketBaseTest {

    private static final String USER = "Aladdin";
    private static final String PWD = "open sesame";
    private static final String ROLE = "role";
    private static final String URI_PROTECTED = "/foo";

    @Test
    public void testConnectToServerEndpoint() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = getProgrammaticRootContext();
        ctx.addApplicationListener(TesterFirehoseServer.ConfigInline.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMapping("/", "default");

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();

        // BZ 62596
        ClientEndpointConfig clientEndpointConfig =
                ClientEndpointConfig.Builder.create().configurator(new Configurator() {
                    @Override
                    public void beforeRequest(Map<String,List<String>> headers) {
                        headers.put("Dummy",
                                Collections.singletonList(String.join("", Collections.nCopies(4000, "A"))));
                        super.beforeRequest(headers);
                    }
                }).build();

        Session wsSession = wsContainer.connectToServer(TesterProgrammaticEndpoint.class, clientEndpointConfig,
                new URI("ws://localhost:" + getPort() + TesterFirehoseServer.PATH));
        CountDownLatch latch = new CountDownLatch(TesterFirehoseServer.MESSAGE_COUNT);
        BasicText handler = new BasicText(latch, TesterFirehoseServer.MESSAGE);
        wsSession.addMessageHandler(handler);
        wsSession.getBasicRemote().sendText("Hello");

        System.out.println("Sent Hello message, waiting for data");

        // Ignore the latch result as the message count test below will tell us
        // if the right number of messages arrived
        handler.getLatch().await(TesterFirehoseServer.WAIT_TIME_MILLIS, TimeUnit.MILLISECONDS);

        Assert.assertEquals(TesterFirehoseServer.MESSAGE_COUNT, handler.getMessageCount());
    }

    @Test
    public void testConnectToRootEndpoint() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = getProgrammaticRootContext();
        ctx.addApplicationListener(TesterEchoServer.Config.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMapping("/", "default");
        Context ctx2 = tomcat.addContext("/foo", null);
        ctx2.addApplicationListener(TesterEchoServer.Config.class.getName());
        Tomcat.addServlet(ctx2, "default", new DefaultServlet());
        ctx2.addServletMapping("/", "default");

        tomcat.start();

        echoTester("", null);
        echoTester("/", null);
        // This will trigger a redirect so there will be 5 requests logged
        echoTester("/foo", null);
        echoTester("/foo/", null);
    }

    public void echoTester(String path, ClientEndpointConfig clientEndpointConfig) throws Exception {
        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();

        if (clientEndpointConfig == null) {
            clientEndpointConfig = ClientEndpointConfig.Builder.create().build();
        }
        // Increase default timeout from 5s to 10s to try and reduce errors on
        // CI systems.
        clientEndpointConfig.getUserProperties().put(Constants.IO_TIMEOUT_MS_PROPERTY, "10000");

        Session wsSession = wsContainer.connectToServer(TesterProgrammaticEndpoint.class, clientEndpointConfig,
                new URI("ws://localhost:" + getPort() + path));
        CountDownLatch latch = new CountDownLatch(1);
        BasicText handler = new BasicText(latch);
        wsSession.addMessageHandler(handler);
        wsSession.getBasicRemote().sendText("Hello");

        boolean latchResult = handler.getLatch().await(10, TimeUnit.SECONDS);
        Assert.assertTrue(latchResult);

        Queue<String> messages = handler.getMessages();
        Assert.assertEquals(1, messages.size());
        for (String message : messages) {
            Assert.assertEquals("Hello", message);
        }
        wsSession.close();
    }

    @Test
    public void testConnectToBasicEndpoint() throws Exception {

        Tomcat tomcat = getTomcatInstance();
        Context ctx = tomcat.addContext(URI_PROTECTED, null);
        ctx.addApplicationListener(TesterEchoServer.Config.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMapping("/", "default");

        SecurityCollection collection = new SecurityCollection();
        collection.addPattern("/");
        String utf8User = "test";
        String utf8Pass = "123\u00A3"; // pound sign

        tomcat.addUser(utf8User, utf8Pass);
        tomcat.addRole(utf8User, ROLE);

        SecurityConstraint sc = new SecurityConstraint();
        sc.addAuthRole(ROLE);
        sc.addCollection(collection);
        ctx.addConstraint(sc);

        LoginConfig lc = new LoginConfig();
        lc.setAuthMethod("BASIC");
        ctx.setLoginConfig(lc);

        AuthenticatorBase basicAuthenticator = new org.apache.catalina.authenticator.BasicAuthenticator();
        ctx.getPipeline().addValve(basicAuthenticator);

        tomcat.start();

        ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().build();
        clientEndpointConfig.getUserProperties().put(Constants.WS_AUTHENTICATION_USER_NAME, utf8User);
        clientEndpointConfig.getUserProperties().put(Constants.WS_AUTHENTICATION_PASSWORD, utf8Pass);

        echoTester(URI_PROTECTED, clientEndpointConfig);
    }

    @Test
    public void testAuthenticatedWebSocketClosedWhenHttpSessionEndsWithoutRotatedSession() throws Exception {
        doTestAuthenticatedWebSocketClosedWhenHttpSessionEnds(false);
    }


    @Test
    public void testAuthenticatedWebSocketClosedWhenHttpSessionEndsWithRotatedSession() throws Exception {
        doTestAuthenticatedWebSocketClosedWhenHttpSessionEnds(true);
    }


    private void doTestAuthenticatedWebSocketClosedWhenHttpSessionEnds(boolean rotateSessionID) throws Exception {

        Tomcat tomcat = getTomcatInstance();
        Context ctx = tomcat.addContext(URI_PROTECTED, null);
        ctx.addApplicationListener(TesterEchoServer.Config.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMapping("/", "default");
        Tomcat.addServlet(ctx, "invalidate", new HttpServlet() {

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                req.getSession(false).invalidate();
            }
        });
        ctx.addServletMapping("/invalidate", "invalidate");
        Tomcat.addServlet(ctx, "changeSessionID", new HttpServlet() {

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                req.changeSessionId();
            }
        });
        ctx.addServletMapping("/changeSessionID", "changeSessionID");

        SecurityCollection collection = new SecurityCollection();
        collection.addPattern("/*");

        tomcat.addUser(USER, PWD);
        tomcat.addRole(USER, ROLE);

        SecurityConstraint sc = new SecurityConstraint();
        sc.addAuthRole(ROLE);
        sc.addCollection(collection);
        ctx.addConstraint(sc);

        LoginConfig lc = new LoginConfig();
        lc.setAuthMethod("BASIC");
        ctx.setLoginConfig(lc);

        AuthenticatorBase basicAuthenticator = new org.apache.catalina.authenticator.BasicAuthenticator();
        basicAuthenticator.setAlwaysUseSession(true);
        ctx.getPipeline().addValve(basicAuthenticator);

        tomcat.start();

        AtomicReference<String> sessionCookie = new AtomicReference<>();
        ClientEndpointConfig clientEndpointConfig =
                ClientEndpointConfig.Builder.create().configurator(new Configurator() {

                    @Override
                    public void afterResponse(HandshakeResponse hr) {
                        List<String> cookies = hr.getHeaders().get("Set-Cookie");
                        if (cookies != null) {
                            for (String cookie : cookies) {
                                if (cookie.startsWith("JSESSIONID=")) {
                                    sessionCookie.set(cookie.split(";", 2)[0]);
                                    break;
                                }
                            }
                        }
                    }
                }).build();
        clientEndpointConfig.getUserProperties().put(Constants.WS_AUTHENTICATION_USER_NAME, USER);
        clientEndpointConfig.getUserProperties().put(Constants.WS_AUTHENTICATION_PASSWORD, PWD);

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();
        Session wsSession = wsContainer.connectToServer(TesterProgrammaticEndpoint.class, clientEndpointConfig,
                new URI("ws://localhost:" + getPort() + URI_PROTECTED + TesterEchoServer.Config.PATH_BASIC));

        CountDownLatch messageLatch = new CountDownLatch(1);
        BasicText handler = new BasicText(messageLatch);
        wsSession.addMessageHandler(handler);
        wsSession.getBasicRemote().sendText("Hello");
        Assert.assertTrue(messageLatch.await(10, TimeUnit.SECONDS));
        Assert.assertEquals("Hello", handler.getMessages().poll());

        if (rotateSessionID) {
            Assert.assertNotNull(sessionCookie.get());
            Map<String,List<String>> requestHeaders = new HashMap<>();
            requestHeaders.put("Cookie", List.of(sessionCookie.get()));
            Map<String,List<String>> responseHeaders = new HashMap<>();
            int status = getUrl("http://localhost:" + getPort() + URI_PROTECTED + "/changeSessionID", new ByteChunk(),
                    requestHeaders, responseHeaders);
            List<String> cookies = responseHeaders.get("Set-Cookie");
            Assert.assertNotNull(cookies);
            Assert.assertEquals(1, cookies.size());
            sessionCookie.set(cookies.get(0).split(";", 2)[0]);
            Assert.assertEquals(HttpServletResponse.SC_OK, status);

            // CyclicBarrier would be cleaner but that requires a larger refactoring
            wsSession.removeMessageHandler(handler);
            CountDownLatch messageLatch2 = new CountDownLatch(1);
            BasicText handler2 = new BasicText(messageLatch2);
            wsSession.addMessageHandler(handler2);
            wsSession.getBasicRemote().sendText("Hello");
            Assert.assertTrue(messageLatch2.await(10, TimeUnit.SECONDS));
            Assert.assertEquals("Hello", handler2.getMessages().poll());
        }

        CountDownLatch closeLatch = new CountDownLatch(1);
        TesterEndpoint endpoint = (TesterEndpoint) wsSession.getUserProperties().get("endpoint");
        endpoint.setLatch(closeLatch);

        Assert.assertNotNull(sessionCookie.get());
        Map<String,List<String>> requestHeaders = new HashMap<>();
        requestHeaders.put("Cookie", List.of(sessionCookie.get()));
        int status = getUrl("http://localhost:" + getPort() + URI_PROTECTED + "/invalidate", new ByteChunk(),
                requestHeaders, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, status);

        Assert.assertTrue(closeLatch.await(10, TimeUnit.SECONDS));
        Assert.assertFalse(wsSession.isOpen());
    }


    @Test
    public void testConnectToDigestEndpoint() throws Exception {

        Tomcat tomcat = getTomcatInstance();
        Context ctx = tomcat.addContext(URI_PROTECTED, null);
        ctx.addApplicationListener(TesterEchoServer.Config.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMapping("/", "default");

        SecurityCollection collection = new SecurityCollection();
        collection.addPattern("/*");

        tomcat.addUser(USER, PWD);
        tomcat.addRole(USER, ROLE);

        SecurityConstraint sc = new SecurityConstraint();
        sc.addAuthRole(ROLE);
        sc.addCollection(collection);
        ctx.addConstraint(sc);

        LoginConfig lc = new LoginConfig();
        lc.setAuthMethod("DIGEST");
        ctx.setLoginConfig(lc);

        AuthenticatorBase digestAuthenticator = new org.apache.catalina.authenticator.DigestAuthenticator();
        ctx.getPipeline().addValve(digestAuthenticator);

        tomcat.start();

        ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().build();
        clientEndpointConfig.getUserProperties().put(Constants.WS_AUTHENTICATION_USER_NAME, USER);
        clientEndpointConfig.getUserProperties().put(Constants.WS_AUTHENTICATION_PASSWORD, PWD);

        echoTester(URI_PROTECTED, clientEndpointConfig);
    }
}
