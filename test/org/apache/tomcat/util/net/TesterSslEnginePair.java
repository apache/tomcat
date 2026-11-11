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
package org.apache.tomcat.util.net;

import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;

import org.junit.Assert;

/**
 * A client and a server {@link SSLEngine} that have completed a handshake in
 * memory. Tests use the client to encrypt records that the server side reads.
 */
public class TesterSslEnginePair {

    private final SSLEngine client;
    private final SSLEngine server;


    public TesterSslEnginePair() throws Exception {
        SSLContext serverCtx = SSLContext.getInstance("TLS");
        serverCtx.init(TesterSupport.getUserKeyManagers(TesterSupport.LOCALHOST_RSA_JKS), null, null);
        SSLContext clientCtx = SSLContext.getInstance("TLS");
        clientCtx.init(null, TesterSupport.getTrustManagers(), null);

        server = serverCtx.createSSLEngine();
        server.setUseClientMode(false);
        client = clientCtx.createSSLEngine("localhost", 443);
        client.setUseClientMode(true);
        handshake();
    }


    public SSLEngine getServer() {
        return server;
    }


    public int getApplicationBufferSize() {
        return server.getSession().getApplicationBufferSize();
    }


    /**
     * Encrypts one TLS record of up to {@code size} plaintext bytes into
     * {@code netIn}.
     *
     * @param size   The number of plaintext bytes to try to send
     * @param netIn  The buffer to write the encrypted record to
     *
     * @return the plaintext that the record holds, which may be shorter than
     *         {@code size}
     *
     * @throws Exception if the record cannot be encrypted
     */
    public byte[] wrapRecord(int size, ByteBuffer netIn) throws Exception {
        byte[] payload = new byte[size];
        Arrays.fill(payload, (byte) 'a');
        SSLEngineResult result = client.wrap(ByteBuffer.wrap(payload), netIn);
        Assert.assertEquals(Status.OK, result.getStatus());
        return Arrays.copyOf(payload, result.bytesConsumed());
    }


    /**
     * Asserts that a read buffer, still in write mode, holds exactly
     * {@code expected}.
     *
     * @param expected The plaintext that was sent
     * @param dst      The buffer the channel read into
     */
    public static void assertContent(byte[] expected, ByteBuffer dst) {
        dst.flip();
        byte[] actual = new byte[dst.remaining()];
        dst.get(actual);
        Assert.assertArrayEquals(expected, actual);
    }


    private void handshake() throws Exception {
        int pkt = Math.max(client.getSession().getPacketBufferSize(), server.getSession().getPacketBufferSize());
        int app = Math.max(client.getSession().getApplicationBufferSize(),
                server.getSession().getApplicationBufferSize());
        ByteBuffer c2s = ByteBuffer.allocate(pkt * 4);
        ByteBuffer s2c = ByteBuffer.allocate(pkt * 4);
        ByteBuffer clientApp = ByteBuffer.allocate(app);
        ByteBuffer serverApp = ByteBuffer.allocate(app);
        client.beginHandshake();
        server.beginHandshake();
        for (int i = 0; i < 1000; i++) {
            boolean clientDone = step(client, c2s, s2c, clientApp);
            boolean serverDone = step(server, s2c, c2s, serverApp);
            if (clientDone && serverDone && c2s.position() == 0 && s2c.position() == 0) {
                return;
            }
        }
        Assert.fail("Handshake did not complete");
    }


    private static boolean step(SSLEngine engine, ByteBuffer out, ByteBuffer in, ByteBuffer app) throws Exception {
        switch (engine.getHandshakeStatus()) {
            case NEED_WRAP:
                engine.wrap(ByteBuffer.allocate(0), out);
                return false;
            case NEED_UNWRAP:
                unwrap(engine, in, app);
                return false;
            case NEED_TASK:
                Runnable task;
                while ((task = engine.getDelegatedTask()) != null) {
                    task.run();
                }
                return false;
            default:
                // Finished or not handshaking. Consume any final message,
                // such as a TLS 1.3 session ticket.
                if (in.position() > 0) {
                    unwrap(engine, in, app);
                }
                return engine.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING;
        }
    }


    private static void unwrap(SSLEngine engine, ByteBuffer in, ByteBuffer app) throws Exception {
        in.flip();
        engine.unwrap(in, app);
        in.compact();
        app.clear();
    }
}
