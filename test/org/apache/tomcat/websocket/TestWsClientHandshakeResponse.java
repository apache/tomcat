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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.LoggingBaseTest;

/**
 * RFC 6455, section 4.1 requires the client to fail the WebSocket connection if the server's handshake response is not
 * valid. A minimal server that sends the response that is under test is used so that responses a compliant server would
 * never send can be tested.
 */
public class TestWsClientHandshakeResponse extends LoggingBaseTest {

    private static final String WS_ACCEPT_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private static final String UPGRADE = "Upgrade: websocket\r\n";
    private static final String CONNECTION = "Connection: Upgrade\r\n";

    private static final int TIMEOUT_MS = 5000;

    private static final Pattern KEY_PATTERN = Pattern.compile("Sec-WebSocket-Key: (.*)\r\n");


    @Test
    public void testValid() throws Exception {
        Assert.assertTrue(connect(null, UPGRADE + CONNECTION + "%ACCEPT%"));
    }


    @Test
    public void testValidCaseAndConnectionList() throws Exception {
        Assert.assertTrue(connect(null, "upgrade: WebSocket\r\nconnection: keep-alive, upgrade\r\n%ACCEPT%"));
    }


    @Test
    public void testValidRequestedSubProtocol() throws Exception {
        Assert.assertTrue(connect("a", UPGRADE + CONNECTION + "%ACCEPT%Sec-WebSocket-Protocol: a\r\n"));
    }


    @Test
    public void testMissingUpgrade() throws Exception {
        Assert.assertFalse(connect(null, CONNECTION + "%ACCEPT%"));
    }


    @Test
    public void testInvalidUpgrade() throws Exception {
        Assert.assertFalse(connect(null, "Upgrade: foo\r\n" + CONNECTION + "%ACCEPT%"));
    }


    @Test
    public void testMissingConnection() throws Exception {
        Assert.assertFalse(connect(null, UPGRADE + "%ACCEPT%"));
    }


    @Test
    public void testInvalidConnection() throws Exception {
        Assert.assertFalse(connect(null, UPGRADE + "Connection: keep-alive\r\n%ACCEPT%"));
    }


    @Test
    public void testMissingAccept() throws Exception {
        Assert.assertFalse(connect(null, UPGRADE + CONNECTION));
    }


    @Test
    public void testInvalidAccept() throws Exception {
        Assert.assertFalse(
                connect(null, UPGRADE + CONNECTION + "Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\r\n"));
    }


    @Test
    public void testDuplicateAccept() throws Exception {
        Assert.assertFalse(connect(null, UPGRADE + CONNECTION + "%ACCEPT%%ACCEPT%"));
    }


    @Test
    public void testUnrequestedSubProtocol() throws Exception {
        Assert.assertFalse(connect(null, UPGRADE + CONNECTION + "%ACCEPT%Sec-WebSocket-Protocol: a\r\n"));
    }


    @Test
    public void testDifferentSubProtocol() throws Exception {
        Assert.assertFalse(connect("a", UPGRADE + CONNECTION + "%ACCEPT%Sec-WebSocket-Protocol: b\r\n"));
    }


    @Test
    public void testUnrequestedExtension() throws Exception {
        Assert.assertFalse(
                connect(null, UPGRADE + CONNECTION + "%ACCEPT%Sec-WebSocket-Extensions: permessage-deflate\r\n"));
    }


    /*
     * Returns true if the client connected, false if the client rejected the response. Every %ACCEPT% marker in the
     * response headers is replaced with the valid Sec-WebSocket-Accept header.
     */
    private boolean connect(String subProtocol, String responseHeaders) throws Exception {
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(TIMEOUT_MS);
            Thread server = new Thread(() -> {
                try {
                    respond(serverSocket, responseHeaders);
                } catch (Throwable t) {
                    serverFailure.set(t);
                }
            });
            server.start();

            try {
                ClientEndpointConfig.Builder builder = ClientEndpointConfig.Builder.create();
                if (subProtocol != null) {
                    builder.preferredSubprotocols(List.of(subProtocol));
                }
                WebSocketContainer container = ContainerProvider.getWebSocketContainer();
                URI uri = new URI("ws://localhost:" + serverSocket.getLocalPort() + "/test");
                try {
                    // The server drops the connection once it has sent the response
                    Session session = container.connectToServer(new Endpoint() {
                        @Override
                        public void onOpen(Session session, EndpointConfig config) {
                            // NO-OP
                        }
                    }, builder.build(), uri);
                    session.close();
                    return true;
                } catch (DeploymentException e) {
                    return false;
                }
            } finally {
                server.join(2 * TIMEOUT_MS);
                Assert.assertFalse("The server thread did not terminate", server.isAlive());
                Assert.assertNull("The server failed", serverFailure.get());
            }
        }
    }


    private static void respond(ServerSocket serverSocket, String responseHeaders) throws Exception {
        try (Socket socket = serverSocket.accept()) {
            socket.setSoTimeout(TIMEOUT_MS);
            InputStream is = socket.getInputStream();
            ByteArrayOutputStream request = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            while (!request.toString(StandardCharsets.ISO_8859_1).contains("\r\n\r\n")) {
                int read = is.read(buffer);
                if (read == -1) {
                    throw new IOException("The client closed the connection before it sent the request");
                }
                request.write(buffer, 0, read);
            }

            Matcher matcher = KEY_PATTERN.matcher(request.toString(StandardCharsets.ISO_8859_1));
            Assert.assertTrue(matcher.find());
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(
                    (matcher.group(1).trim() + WS_ACCEPT_GUID).getBytes(StandardCharsets.ISO_8859_1));
            String accept = "Sec-WebSocket-Accept: " + Base64.getEncoder().encodeToString(digest) + "\r\n";

            String response = "HTTP/1.1 101 Switching Protocols\r\n" + responseHeaders.replace("%ACCEPT%", accept) +
                    "\r\n";
            socket.getOutputStream().write(response.getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
        }
    }
}
