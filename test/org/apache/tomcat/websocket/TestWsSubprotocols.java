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

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;

import org.junit.Assert;
import org.junit.Test;


import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.websocket.TesterMessageCountClient.TesterProgrammaticEndpoint;
import org.apache.tomcat.websocket.server.TesterEndpointConfig;

public class TestWsSubprotocols extends WebSocketBaseTest {

    @Test
    public void testWsSubprotocols() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = tomcat.addContext("", null);
        ctx.addApplicationListener(Config.class.getName());

        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider
                .getWebSocketContainer();

        tomcat.start();

        Session wsSession = wsContainer.connectToServer(
                TesterProgrammaticEndpoint.class, ClientEndpointConfig.Builder
                        .create().preferredSubprotocols(Arrays.asList("sp3"))
                        .build(), new URI("ws://localhost:" + getPort()
                        + SubProtocolsEndpoint.PATH_BASIC));

        Assert.assertTrue(wsSession.isOpen());
        if (wsSession.getNegotiatedSubprotocol() != null) {
            Assert.assertTrue(wsSession.getNegotiatedSubprotocol().isEmpty());
        }
        wsSession.close();
        SubProtocolsEndpoint.recycle();

        wsSession = wsContainer.connectToServer(
                TesterProgrammaticEndpoint.class, ClientEndpointConfig.Builder
                        .create().preferredSubprotocols(Arrays.asList("sp2"))
                        .build(), new URI("ws://localhost:" + getPort()
                        + SubProtocolsEndpoint.PATH_BASIC));

        Assert.assertTrue(wsSession.isOpen());
        Assert.assertEquals("sp2", wsSession.getNegotiatedSubprotocol());
        // Client thread might move faster than server. Wait for up to 5s for the
        // subProtocols to be set
        int count = 0;
        while (count < 50 && SubProtocolsEndpoint.subprotocols == null) {
            count++;
            Thread.sleep(100);
        }
        Assert.assertNotNull(SubProtocolsEndpoint.subprotocols);
        Assert.assertArrayEquals(new String[]{"sp1","sp2"},
                SubProtocolsEndpoint.subprotocols.toArray(new String[0]));
        wsSession.close();
        SubProtocolsEndpoint.recycle();
    }

    @ServerEndpoint(value = "/echo", subprotocols = {"sp1","sp2"})
    public static class SubProtocolsEndpoint {
        public static final String PATH_BASIC = "/echo";
        public static volatile List<String> subprotocols;

        @OnOpen
        public void processOpen(@SuppressWarnings("unused") Session session,
                EndpointConfig  epc) {
            subprotocols = ((ServerEndpointConfig)epc).getSubprotocols();
        }

        public static void recycle() {
            subprotocols = null;
        }

    }

    public static class Config extends TesterEndpointConfig {

        @Override
        protected Class<?> getEndpointClass() {
            return SubProtocolsEndpoint.class;
        }
    }
}
