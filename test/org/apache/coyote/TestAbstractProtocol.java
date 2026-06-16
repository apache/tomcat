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
package org.apache.coyote;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.function.BooleanSupplier;

import javax.servlet.ServletContextEvent;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.apache.coyote.http11.upgrade.UpgradeGroupInfo;
import org.apache.coyote.http11.upgrade.UpgradeInfo;
import org.apache.tomcat.websocket.WebSocketBaseTest;
import org.apache.tomcat.websocket.server.Constants;
import org.apache.tomcat.websocket.server.WsContextListener;

/**
 * Reproducer for UpgradeInfo leak in AbstractProtocol.ConnectionHandler.
 * <p>
 * When a WebSocket endpoint's onOpen() throws a RuntimeException, the exception propagates out of
 * WsHttpUpgradeHandler.init(), which is called at AbstractProtocol.ConnectionHandler.process() line 945 — after the
 * UpgradeProcessorInternal (with its UpgradeInfo) was already created at line 933. The exception is caught by the catch
 * blocks (lines 1037-1068), which fall through to release(processor) without calling httpUpgradeHandler.destroy(). The
 * UpgradeInfo is never removed from the UpgradeGroupInfo set.
 * <p>
 * The same issue exists in ConnectionHandler.release(SocketWrapperBase), which is called when the NIO poller closes a
 * socket directly (e.g., on thread pool rejection, invalid key, or CancelledKeyException).
 */
public class TestAbstractProtocol extends WebSocketBaseTest {

    private static final String PATH = "/throwOnOpen";

    @Test
    public void testUpgradeInfoLeakOnOpenException() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = getProgrammaticRootContext();
        ctx.addApplicationListener(Config.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        AbstractHttp11Protocol<?> protocol = (AbstractHttp11Protocol<?>) tomcat.getConnector().getProtocolHandler();

        UpgradeGroupInfo groupInfo = protocol.getUpgradeGroupInfo("websocket");
        Set<UpgradeInfo> upgradeInfos = getUpgradeInfoSet(groupInfo);

        int sizeBeforeTest = upgradeInfos.size();
        int numConnections = 5;

        for (int i = 0; i < numConnections; i++) {
            connectWebSocket(getPort(), PATH);
        }

        // Wait for server to process the connections
        Thread.sleep(1000);
        waitForCondition(() -> protocol.getWaitingProcessorCount() == 0, 10000);

        int leakedCount = upgradeInfos.size() - sizeBeforeTest;
        Assert.assertEquals("UpgradeInfo leaked! Found " + leakedCount + " leaked objects. " +
                "When onOpen() throws, the exception path in " +
                "ConnectionHandler.process() calls release(processor) without " +
                "calling httpUpgradeHandler.destroy().", sizeBeforeTest, upgradeInfos.size());
    }


    /**
     * Performs a WebSocket handshake and then closes the socket.
     */
    private void connectWebSocket(int port, String path) throws Exception {
        Socket socket = new Socket("localhost", port);
        socket.setSoLinger(true, 0);
        socket.setSoTimeout(5000);

        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

        byte[] keyBytes = new byte[16];
        for (int i = 0; i < keyBytes.length; i++) {
            keyBytes[i] = (byte) (Math.random() * 256);
        }
        String wsKey = Base64.getEncoder().encodeToString(keyBytes);

        //@formatter:off
        String request =
                "GET " + path + " HTTP/1.1\r\n" +
                "Host: localhost:" + port + "\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: " + wsKey + "\r\n" +
                "Sec-WebSocket-Version: 13\r\n" + "\r\n";
        //@formatter:on

        out.write(request.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();

        // Read response (may be 101 before the exception, or an error)
        StringBuilder response = new StringBuilder();
        try {
            int b;
            while ((b = in.read()) != -1) {
                response.append((char) b);
                if (response.toString().endsWith("\r\n\r\n")) {
                    break;
                }
            }
        } catch (IOException e) {
            // Server may close the connection
        }

        try {
            socket.close();
        } catch (IOException e) {
            // Ignore
        }
    }


    /**
     * WebSocket endpoint that throws RuntimeException from onOpen(). This is a programmatic endpoint — not wrapped by
     * PojoEndpointBase, so the exception propagates directly out of WsHttpUpgradeHandler.init().
     */
    public static class ThrowingEndpoint extends Endpoint {
        @Override
        public void onOpen(Session session, EndpointConfig config) {
            throw new RuntimeException("Simulated onOpen failure");
        }
    }


    /**
     * Registers the ThrowingEndpoint as a programmatic WebSocket endpoint.
     */
    public static class Config extends WsContextListener {
        @Override
        public void contextInitialized(ServletContextEvent sce) {
            super.contextInitialized(sce);
            ServerContainer sc = (ServerContainer) sce.getServletContext()
                    .getAttribute(Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE);
            try {
                ServerEndpointConfig sec = ServerEndpointConfig.Builder.create(ThrowingEndpoint.class, PATH).build();
                sc.addEndpoint(sec);
            } catch (DeploymentException e) {
                throw new IllegalStateException(e);
            }
        }
    }


    @SuppressWarnings("unchecked")
    private static Set<UpgradeInfo> getUpgradeInfoSet(UpgradeGroupInfo groupInfo) throws Exception {
        Field upgradeInfosField = UpgradeGroupInfo.class.getDeclaredField("upgradeInfos");
        upgradeInfosField.setAccessible(true);
        return (Set<UpgradeInfo>) upgradeInfosField.get(groupInfo);
    }


    private static void waitForCondition(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
    }
}