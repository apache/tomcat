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
package org.apache.catalina.tribes.transport.nio;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLEngine;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import org.apache.catalina.tribes.group.TribesSslContext;

public class TestTlsChannel {

    @Test
    public void testPskRoundTrip() throws Exception {
        doTestPskRoundTrip(createContext("SHA256", "TLSv1.3"), "TLSv1.3");
    }

    @Test
    public void testPskRoundTripTls13Sha384() throws Exception {
        doTestPskRoundTrip(createContext("SHA384", "TLSv1.3"), "TLSv1.3");
    }

    @Test
    public void testPskRoundTripTls12() throws Exception {
        doTestPskRoundTrip(createContext("SHA256", "TLSv1.2"), "TLSv1.2");
    }

    private static void doTestPskRoundTrip(TribesSslContext context, String protocol) throws Exception {
        try (context;
                ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress("localhost", 0));
            FutureTask<Void> serverTask = new FutureTask<>(() -> {
                SSLEngine engine = context.createServerEngine();
                try (SocketChannel socket = server.accept(); TlsChannel tls = new TlsChannel(socket.socket(), engine)) {
                    Assert.assertTrue(engine.getSession().getProtocol().startsWith(protocol));
                    Assert.assertEquals("request", read(tls));
                    tls.write(ByteBuffer.wrap("response".getBytes(StandardCharsets.UTF_8)));
                }
                return null;
            });
            Thread serverThread = new Thread(serverTask);
            serverThread.start();
            SSLEngine engine = context.createClientEngine();
            try {
                try (SocketChannel socket = SocketChannel.open(server.getLocalAddress());
                        TlsChannel tls = new TlsChannel(socket.socket(), engine)) {
                    Assert.assertTrue(engine.getSession().getProtocol().startsWith(protocol));
                    tls.write(ByteBuffer.wrap("request".getBytes(StandardCharsets.UTF_8)));
                    Assert.assertEquals("response", read(tls));
                }
                serverTask.get();
            } catch (Exception e) {
                try {
                    serverTask.get(5, TimeUnit.SECONDS);
                } catch (Exception serverException) {
                    e.addSuppressed(serverException);
                }
                throw e;
            }
        }
    }

    @Test
    public void testMultipleRecords() throws Exception {
        byte[] payload = new byte[128 * 1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }
        try (TribesSslContext context = createContext();
                ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress("localhost", 0));
            FutureTask<Void> serverTask = new FutureTask<>(() -> {
                try (SocketChannel socket = server.accept()) {
                    socket.socket().setSoTimeout(5000);
                    try (TlsChannel tls = new TlsChannel(socket.socket(), context.createServerEngine())) {
                        Assert.assertArrayEquals(payload, read(tls, payload.length));
                        tls.write(ByteBuffer.wrap(payload));
                    }
                }
                return null;
            });
            new Thread(serverTask).start();
            try (SocketChannel socket = SocketChannel.open(server.getLocalAddress())) {
                socket.socket().setSoTimeout(5000);
                try (TlsChannel tls = new TlsChannel(socket.socket(), context.createClientEngine())) {
                    tls.write(ByteBuffer.wrap(payload));
                    Assert.assertArrayEquals(payload, read(tls, payload.length));
                }
            }
            serverTask.get();
        }
    }

    private static TribesSslContext createContext() {
        return createContext("SHA256", "TLSv1.3");
    }

    private static TribesSslContext createContext(String digest, String protocol) {
        try {
            return new TribesSslContext("tribes-test",
                    "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f", digest, protocol);
        } catch (Exception e) {
            Assume.assumeNoException(e);
            return null;
        }
    }

    private static String read(TlsChannel channel) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(32);
        while (buffer.position() == 0) {
            Assert.assertTrue(channel.read(buffer) >= 0);
        }
        buffer.flip();
        return StandardCharsets.UTF_8.decode(buffer).toString();
    }

    private static byte[] read(TlsChannel channel, int length) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        while (buffer.hasRemaining()) {
            Assert.assertTrue(channel.read(buffer) >= 0);
        }
        return buffer.array();
    }
}
