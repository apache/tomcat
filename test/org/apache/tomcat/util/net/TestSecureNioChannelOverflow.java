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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests {@link SecureNioChannel#read(ByteBuffer)} when {@code unwrap()}
 * returns {@code BUFFER_OVERFLOW} and {@code expand()} does not make enough
 * room for the record.
 * <p>
 * Each test writes its TLS record straight into the channel's
 * {@code netInBuffer}. Nothing is sent over the socket, so the tests do not
 * depend on timing.
 */
public class TestSecureNioChannelOverflow {

    /*
     * Without the fix, the overflow tests loop forever on one core. The
     * timeout fails the test, but the looping thread keeps running until the
     * JVM for this test class exits. build.xml forks a JVM per test class.
     */
    private static final long TIMEOUT_MS = 10000;

    private TesterSslEnginePair engines;
    private ServerSocketChannel listener;
    private SocketChannel clientSock;
    private SocketChannel serverSock;


    @Before
    public void setUp() throws Exception {
        engines = new TesterSslEnginePair();
        listener = ServerSocketChannel.open();
        listener.bind(new InetSocketAddress("127.0.0.1", 0));
        clientSock = SocketChannel.open(listener.getLocalAddress());
        serverSock = listener.accept();
        // read() reads the socket first. Nothing is sent, so this returns 0.
        serverSock.configureBlocking(false);
    }


    @After
    public void tearDown() throws IOException {
        clientSock.close();
        serverSock.close();
        listener.close();
    }


    /*
     * Regression guard: passes with and without the fix.
     */
    @Test
    public void testNormalRead() throws Exception {
        int appSize = engines.getApplicationBufferSize();
        SocketBufferHandler bh = new SocketBufferHandler(appSize, appSize, false);
        SecureNioChannel ch = newChannel(bh);
        byte[] payload = engines.wrapRecord(1000, ch.netInBuffer);

        ByteBuffer dst = bh.getReadBuffer();
        Assert.assertEquals(payload.length, ch.read(dst));
        TesterSslEnginePair.assertContent(payload, dst);
    }


    /*
     * Regression guard: passes with and without the fix. expand() replaces the
     * socket read buffer with a larger one and the read succeeds.
     */
    @Test
    public void testExpandGrowsSocketBuffer() throws Exception {
        int appSize = engines.getApplicationBufferSize();
        SocketBufferHandler bh = new SocketBufferHandler(appSize / 2, appSize / 2, false);
        SecureNioChannel ch = newChannel(bh);
        byte[] payload = engines.wrapRecord(appSize, ch.netInBuffer);
        Assert.assertTrue(payload.length > appSize / 2);

        Assert.assertEquals(payload.length, ch.read(bh.getReadBuffer()));
        ByteBuffer dst = bh.getReadBuffer();
        Assert.assertTrue(dst.capacity() >= appSize);
        TesterSslEnginePair.assertContent(payload, dst);
    }


    /*
     * The socket read buffer is full and already at the application buffer
     * size, so expand() returns the same buffer.
     */
    @Test(timeout = TIMEOUT_MS)
    public void testNoRoomSocketBuffer() throws Exception {
        int appSize = engines.getApplicationBufferSize();
        SocketBufferHandler bh = new SocketBufferHandler(appSize, appSize, false);
        SecureNioChannel ch = newChannel(bh);
        engines.wrapRecord(appSize, ch.netInBuffer);

        ByteBuffer dst = bh.getReadBuffer();
        dst.position(dst.limit());
        assertReadFails(ch, dst);
    }


    /*
     * The application buffer has no room and expand() does nothing.
     */
    @Test(timeout = TIMEOUT_MS)
    public void testNoRoomAppBuffer() throws Exception {
        int appSize = engines.getApplicationBufferSize();
        SecureNioChannel ch = newChannel(new SocketBufferHandler(appSize, appSize, false));
        engines.wrapRecord(appSize, ch.netInBuffer);
        TesterAppBuffer app = new TesterAppBuffer(appSize, appSize, appSize, false);
        ch.setAppReadBufHandler(app);

        assertReadFails(ch, app.getByteBuffer());
    }


    /*
     * The application buffer is large enough but has little room left, and
     * expand() only moves the limit to the capacity, as
     * Http11InputBuffer.expand() does. The first expand() adds some room, but
     * not enough for the record, and the second adds none.
     */
    @Test(timeout = TIMEOUT_MS)
    public void testAppBufferExpandOnlyMovesLimit() throws Exception {
        int appSize = engines.getApplicationBufferSize();
        SecureNioChannel ch = newChannel(new SocketBufferHandler(appSize, appSize, false));
        engines.wrapRecord(appSize, ch.netInBuffer);
        TesterAppBuffer app = new TesterAppBuffer(appSize, appSize - 100, appSize - 92, true);
        ch.setAppReadBufHandler(app);

        assertReadFails(ch, app.getByteBuffer());
    }


    private SecureNioChannel newChannel(SocketBufferHandler bh) {
        SecureNioChannel ch = new SecureNioChannel(bh, new NioEndpoint());
        ch.sc = serverSock;
        ch.sslEngine = engines.getServer();
        ch.handshakeComplete = true;
        return ch;
    }


    private static void assertReadFails(SecureNioChannel ch, ByteBuffer dst) {
        try {
            ch.read(dst);
            Assert.fail("Expected IOException");
        } catch (IOException expected) {
            // The message must be unwrapFailNoRoom, not another failure such as
            // unwrapFailResize, so a stray IOException cannot pass this test.
            Assert.assertTrue(String.valueOf(expected.getMessage()),
                    expected.getMessage() != null && expected.getMessage().contains("no free space"));
        }
    }
}
