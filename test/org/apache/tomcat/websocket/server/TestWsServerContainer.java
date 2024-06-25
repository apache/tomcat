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
package org.apache.tomcat.websocket.server;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Decoder;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Encoder;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.filters.TesterHttpServletRequest;
import org.apache.catalina.filters.TesterHttpServletResponse;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.unittest.TesterServletContext;
import org.apache.tomcat.websocket.Constants;
import org.apache.tomcat.websocket.TesterEchoServer;
import org.apache.tomcat.websocket.TesterMessageCountClient.BasicText;
import org.apache.tomcat.websocket.WebSocketBaseTest;
import org.apache.tomcat.websocket.pojo.TesterUtil.SimpleClient;

public class TestWsServerContainer extends WebSocketBaseTest {

    @Test
    public void testBug54807() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = getProgrammaticRootContext();
        ctx.addApplicationListener(Bug54807Config.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        Assert.assertEquals(LifecycleState.STARTED, ctx.getState());
    }


    @Test
    public void testBug58232() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = getProgrammaticRootContext();
        ctx.addApplicationListener(Bug54807Config.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();

        tomcat.start();

        Assert.assertEquals(LifecycleState.STARTED, ctx.getState());

        SimpleClient client = new SimpleClient();
        URI uri = new URI("ws://localhost:" + getPort() + "/echoBasic");

        try (Session session = wsContainer.connectToServer(client, uri)) {
            CountDownLatch latch = new CountDownLatch(1);
            BasicText handler = new BasicText(latch);
            session.addMessageHandler(handler);
            session.getBasicRemote().sendText("echoBasic");

            boolean latchResult = handler.getLatch().await(10, TimeUnit.SECONDS);
            Assert.assertTrue(latchResult);

            Queue<String> messages = handler.getMessages();
            Assert.assertEquals(1, messages.size());
            for (String message : messages) {
                Assert.assertEquals("echoBasic", message);
            }
        }
    }


    public static class Bug54807Config extends TesterEndpointConfig {

        @Override
        protected ServerEndpointConfig getServerEndpointConfig() {
            return ServerEndpointConfig.Builder.create(TesterEchoServer.Basic.class, "/{param}").build();
        }
    }


    @Test
    public void testSpecExample3() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        ServerEndpointConfig configA = ServerEndpointConfig.Builder.create(Object.class, "/a/{var}/c").build();
        ServerEndpointConfig configB = ServerEndpointConfig.Builder.create(Object.class, "/a/b/c").build();
        ServerEndpointConfig configC = ServerEndpointConfig.Builder.create(Object.class, "/a/{var1}/{var2}").build();

        sc.addEndpoint(configA);
        sc.addEndpoint(configB);
        sc.addEndpoint(configC);

        Assert.assertEquals(configB, sc.findMapping("/a/b/c").getConfig());
        Assert.assertEquals(configA, sc.findMapping("/a/d/c").getConfig());
        Assert.assertEquals(configC, sc.findMapping("/a/x/y").getConfig());
    }


    @Test
    public void testSpecExample4() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        ServerEndpointConfig configA = ServerEndpointConfig.Builder.create(Object.class, "/{var1}/d").build();
        ServerEndpointConfig configB = ServerEndpointConfig.Builder.create(Object.class, "/b/{var2}").build();

        sc.addEndpoint(configA);
        sc.addEndpoint(configB);

        Assert.assertEquals(configB, sc.findMapping("/b/d").getConfig());
    }

    @Test
    public void testSpecExample5() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        ServerEndpointConfig configA = ServerEndpointConfig.Builder.create(Object.class, "/a").build();

        sc.addEndpoint(configA);

        Assert.assertNull(sc.findMapping("invalidPath"));
    }

    @Test
    public void testSpecExample6() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        ServerEndpointConfig configA = ServerEndpointConfig.Builder.create(Object.class, "/a").build();

        sc.addEndpoint(configA);

        Assert.assertNull(sc.findMapping("/b"));
    }

    @Test
    public void testSpecExample7() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        // Use reflection to set the configTemplateMatchMap field to have a key "2" with value an empty ConcurrentSkipListMap
        Field configTemplateMatchMapField = WsServerContainer.class.getDeclaredField("configTemplateMatchMap");
        configTemplateMatchMapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Integer, ConcurrentSkipListMap<String, Object>> configTemplateMatchMap =
            (Map<Integer, ConcurrentSkipListMap<String, Object>>) configTemplateMatchMapField.get(sc);

        ConcurrentSkipListMap<String, Object> newSkipListMap = new ConcurrentSkipListMap<>();

        configTemplateMatchMap.put(Integer.valueOf(2), newSkipListMap);

        Assert.assertNull(sc.findMapping("/a/0"));
    }


    @Test(expected = DeploymentException.class)
    public void testDuplicatePaths01() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        ServerEndpointConfig configA = ServerEndpointConfig.Builder.create(Object.class, "/a/b/c").build();
        ServerEndpointConfig configB = ServerEndpointConfig.Builder.create(Object.class, "/a/b/c").build();

        sc.addEndpoint(configA);
        sc.addEndpoint(configB);
    }


    @Test(expected = DeploymentException.class)
    public void testDuplicatePaths02() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        ServerEndpointConfig configA = ServerEndpointConfig.Builder.create(Object.class, "/a/b/{var}").build();
        ServerEndpointConfig configB = ServerEndpointConfig.Builder.create(Object.class, "/a/b/{var}").build();

        sc.addEndpoint(configA);
        sc.addEndpoint(configB);
    }


    @Test(expected = DeploymentException.class)
    public void testDuplicatePaths03() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        ServerEndpointConfig configA = ServerEndpointConfig.Builder.create(Object.class, "/a/b/{var1}").build();
        ServerEndpointConfig configB = ServerEndpointConfig.Builder.create(Object.class, "/a/b/{var2}").build();

        sc.addEndpoint(configA);
        sc.addEndpoint(configB);
    }


    @Test
    public void testDuplicatePaths04() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        ServerEndpointConfig configA = ServerEndpointConfig.Builder.create(Object.class, "/a/{var1}/{var2}").build();
        ServerEndpointConfig configB = ServerEndpointConfig.Builder.create(Object.class, "/a/b/{var2}").build();

        sc.addEndpoint(configA);
        sc.addEndpoint(configB);

        Assert.assertEquals(configA, sc.findMapping("/a/x/y").getConfig());
        Assert.assertEquals(configB, sc.findMapping("/a/b/y").getConfig());
    }


    /*
     * Simulates a class that gets picked up for extending Endpoint and for being annotated.
     */
    @Test(expected = DeploymentException.class)
    public void testDuplicatePaths11() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        ServerEndpointConfig configA = ServerEndpointConfig.Builder.create(Pojo.class, "/foo").build();

        sc.addEndpoint(configA, false);
        sc.addEndpoint(Pojo.class, true);
    }


    /*
     * POJO auto deployment followed by programmatic duplicate. Keep POJO.
     */
    @Test
    public void testDuplicatePaths12() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        ServerEndpointConfig configA = ServerEndpointConfig.Builder.create(Pojo.class, "/foo").build();

        sc.addEndpoint(Pojo.class, true);
        sc.addEndpoint(configA);

        Assert.assertNotEquals(configA, sc.findMapping("/foo").getConfig());
    }


    /*
     * POJO programmatic followed by programmatic duplicate.
     */
    @Test(expected = DeploymentException.class)
    public void testDuplicatePaths13() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        ServerEndpointConfig configA = ServerEndpointConfig.Builder.create(Pojo.class, "/foo").build();

        sc.addEndpoint(Pojo.class);
        sc.addEndpoint(configA);
    }


    /*
     * POJO auto deployment followed by programmatic on same path.
     */
    @Test(expected = DeploymentException.class)
    public void testDuplicatePaths14() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        ServerEndpointConfig configA = ServerEndpointConfig.Builder.create(Object.class, "/foo").build();

        sc.addEndpoint(Pojo.class, true);
        sc.addEndpoint(configA);
    }

    @Test(expected = DeploymentException.class)
    public void testDuplicatePaths15() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        ServerEndpointConfig configA = ServerEndpointConfig.Builder.create(Object.class, "/foo").build();

        sc.addEndpoint(configA);
        sc.addEndpoint(Pojo.class, true);
    }

    @Test(expected = DeploymentException.class)
    public void testDuplicatePaths16() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        ServerEndpointConfig configA = ServerEndpointConfig.Builder.create(Object.class, "/foo").build();

        sc.addEndpoint(configA);
        sc.addEndpoint(Pojo.class, false);
    }

    @Test(expected = DeploymentException.class)
    public void testDuplicatePaths17() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        sc.addEndpoint(Pojo.class, true);
        sc.addEndpoint(Pojo.class, true);
    }

    @Test(expected = DeploymentException.class)
    public void testDuplicatePaths18() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        ServerEndpointConfig configA = ServerEndpointConfig.Builder.create(Object.class, "/foo").build();

        sc.addEndpoint(Pojo.class, true);
        sc.addEndpoint(configA, true);
    }

    /*
     * Simulates a class that gets picked up for extending Endpoint and for being annotated.
     */
    @Test(expected = DeploymentException.class)
    public void testDuplicatePaths21() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        ServerEndpointConfig configA = ServerEndpointConfig.Builder.create(PojoTemplate.class, "/foo/{a}").build();

        sc.addEndpoint(configA, false);
        sc.addEndpoint(PojoTemplate.class, true);
    }


    /*
     * POJO auto deployment followed by programmatic duplicate. Keep POJO.
     */
    @Test
    public void testDuplicatePaths22() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        ServerEndpointConfig configA = ServerEndpointConfig.Builder.create(PojoTemplate.class, "/foo/{a}").build();

        sc.addEndpoint(PojoTemplate.class, true);
        sc.addEndpoint(configA);

        Assert.assertNotEquals(configA, sc.findMapping("/foo/{a}").getConfig());
    }


    /*
     * POJO programmatic followed by programmatic duplicate.
     */
    @Test(expected = DeploymentException.class)
    public void testDuplicatePaths23() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        ServerEndpointConfig configA = ServerEndpointConfig.Builder.create(PojoTemplate.class, "/foo/{a}").build();

        sc.addEndpoint(PojoTemplate.class);
        sc.addEndpoint(configA);
    }


    /*
     * POJO auto deployment followed by programmatic on same path.
     */
    @Test(expected = DeploymentException.class)
    public void testDuplicatePaths24() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        ServerEndpointConfig configA = ServerEndpointConfig.Builder.create(Object.class, "/foo/{a}").build();

        sc.addEndpoint(PojoTemplate.class, true);
        sc.addEndpoint(configA);
    }

    @Test(expected = DeploymentException.class)
    public void testDuplicatePaths25() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        ServerEndpointConfig configA = ServerEndpointConfig.Builder.create(Object.class, "/foo/{a}").build();

        sc.addEndpoint(configA);
        sc.addEndpoint(PojoTemplate.class, true);
    }

    @Test(expected = DeploymentException.class)
    public void testDuplicatePaths26() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        ServerEndpointConfig configA = ServerEndpointConfig.Builder.create(Object.class, "/foo/{a}").build();

        sc.addEndpoint(configA);
        sc.addEndpoint(PojoTemplate.class, false);
    }

    @Test(expected = DeploymentException.class)
    public void testDuplicatePaths27() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        sc.addEndpoint(PojoTemplate.class, true);
        sc.addEndpoint(PojoTemplate.class, true);
    }

    @Test(expected = DeploymentException.class)
    public void testDuplicatePaths28() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        ServerEndpointConfig configA = ServerEndpointConfig.Builder.create(Object.class, "/foo/{a}").build();

        sc.addEndpoint(PojoTemplate.class, true);
        sc.addEndpoint(configA, true);
    }

    @ServerEndpoint("/foo")
    public static class Pojo {
    }


    @ServerEndpoint("/foo/{a}")
    public static class PojoTemplate {
    }

    @Test
    public void testUpgradeHttpToWebSocket01() throws Exception {
        TesterHttpServletRequest request = new TesterHttpServletRequest();

        request.setScheme("http");

        request.setHeader(Constants.CONNECTION_HEADER_NAME, Constants.CONNECTION_HEADER_VALUE);
        request.setHeader(Constants.WS_VERSION_HEADER_NAME, Constants.WS_VERSION_HEADER_VALUE);
        //Random 16-byte value encoded in 24 base64 characters.
        request.setHeader(Constants.WS_KEY_HEADER_NAME, "1b4pqvztbM4cor6UUZSDqw==");
        request.setHeader(Constants.WS_PROTOCOL_HEADER_NAME, "testProtocol");
        request.setHeader(Constants.WS_EXTENSIONS_HEADER_NAME, "testExtension");

        HttpServletResponse response = new TesterHttpServletResponse();

        ServerEndpointConfig sec = ServerEndpointConfig.Builder.create(Object.class, "/").subprotocols(List.of("testProtocol")).build();

        WsServerContainer container = new WsServerContainer(new TesterServletContext());

        container.upgradeHttpToWebSocket(request, response, sec, new HashMap<>());

        Assert.assertEquals(Constants.UPGRADE_HEADER_VALUE, response.getHeader(Constants.UPGRADE_HEADER_NAME));
        Assert.assertEquals("aJvXj0bbnSSeXm32ngvbBilP0lE=", response.getHeader(HandshakeResponse.SEC_WEBSOCKET_ACCEPT));
    }

    @Test(expected = DeploymentException.class)
    public void testUpgradeHttpToWebSocket02() throws Exception {
        TesterHttpServletRequest request = new TesterHttpServletRequest();

        request.setHeader(Constants.CONNECTION_HEADER_NAME, Constants.CONNECTION_HEADER_VALUE);
        request.setHeader(Constants.WS_VERSION_HEADER_NAME, Constants.WS_VERSION_HEADER_VALUE);
        //Random 16-byte value encoded in 24 base64 characters.
        request.setHeader(Constants.WS_KEY_HEADER_NAME, "1b4pqvztbM4cor6UUZSDqw==");
        request.setHeader(Constants.WS_PROTOCOL_HEADER_NAME, "testProtocol");
        request.setHeader(Constants.WS_EXTENSIONS_HEADER_NAME, "testExtension");

        HttpServletResponse response = new TesterHttpServletResponse();

        ServerEndpointConfig sec = ServerEndpointConfig.Builder.create(Object.class, "/").decoders(List.of(DummyDecoder.class)).build();

        WsServerContainer container = new WsServerContainer(new TesterServletContext());

        container.upgradeHttpToWebSocket(request, response, sec, new HashMap<>());
    }

    private static class DummyDecoder implements Decoder {
        @SuppressWarnings("unused")
        DummyDecoder(String ignoredParam) {
        }
    }

    @Test
    public void testFilterRegistrationFailure() {
        @SuppressWarnings("unused")
        Object obj = new WsServerContainer(new TesterServletContext(){
            @Override
            public FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
                return null;
            }
        });

    }

    @Test(expected = DeploymentException.class)
    public void testAddEndpointNullServletContext() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        ServerEndpointConfig configA = ServerEndpointConfig.Builder.create(Object.class, "/").build();

        // Use reflection to set the servletContext field to null
        Field servletContextField = WsServerContainer.class.getDeclaredField("servletContext");
        servletContextField.setAccessible(true);
        servletContextField.set(sc, null);

        sc.addEndpoint(configA);
    }

    @Test(expected = DeploymentException.class)
    public void testAddEndpointDeploymentFailed01() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        ServerEndpointConfig configA = ServerEndpointConfig.Builder.create(Object.class, "/a/b/c").build();
        ServerEndpointConfig configB = ServerEndpointConfig.Builder.create(Object.class, "/a/b/c").build();

        try {
            sc.addEndpoint(configA);
            sc.addEndpoint(configB);
        }catch (DeploymentException ignore){
            sc.addEndpoint(configB);
        }

    }

    @Test(expected = DeploymentException.class)
    public void testAddEndpointDeploymentFailed02() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        try {
            sc.addEndpoint(Pojo.class, true);
            sc.addEndpoint(Pojo.class, true);
        }catch (DeploymentException ignore){
            sc.addEndpoint(Pojo.class, true);
        }

    }

    @Test(expected = DeploymentException.class)
    public void testAddEndpointMissingAnnotationFromPojoClass() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        sc.addEndpoint(DummyPojo.class, true);

    }

    public static class DummyPojo {
    }

    @Test(expected = DeploymentException.class)
    public void testAddEndpointConfiguratorFail() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        sc.addEndpoint(DummyPojo2.class, true);

    }

    @ServerEndpoint(value = "/foo", configurator = DummyConfigurator.class)
    public static class DummyPojo2 {
    }

    private static class DummyConfigurator extends ServerEndpointConfig.Configurator {
        @SuppressWarnings("unused")
        DummyConfigurator(String ignoredParam) {
        }

    }
    @Test
    public void testValidateEncoders01() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        sc.addEndpoint(DummyPojo3.class);

    }

    @ServerEndpoint(value = "/foo", encoders = DummyEncoder.class)
    public static class DummyPojo3 {
    }

    public static class DummyEncoder implements Encoder {
        public DummyEncoder() {
        }
    }

    @Test(expected = DeploymentException.class)
    public void testValidateEncoders02() throws Exception {
        WsServerContainer sc = new WsServerContainer(new TesterServletContext());

        sc.addEndpoint(DummyPojo4.class);

    }

    @ServerEndpoint(value = "/foo", encoders = Encoder.class)
    public static class DummyPojo4 {
    }

}
