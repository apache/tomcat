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
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLEngineResult.Status;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.tomcat.util.res.StringManager;

/**
 * Tests the vectored {@link SecureNio2Channel#read(ByteBuffer[], int, int, long, TimeUnit, Object, CompletionHandler)}
 * when {@code unwrap()} returns {@code BUFFER_OVERFLOW} and {@code expand()} does not make enough room for the record.
 * <p>
 * Each test writes its TLS record straight into the channel's {@code netInBuffer}. Nothing is sent over the socket, so
 * the read completes on the calling thread and the tests do not depend on timing.
 */
public class TestSecureNio2ChannelOverflow {

    private static final StringManager sm = StringManager.getManager(SecureNio2Channel.class);

    /*
     * Without the fix, the overflow tests loop forever on one core. The timeout fails the test, but the looping thread
     * keeps running until the JVM for this test class exits. build.xml forks a JVM per test class.
     */
    private static final long TIMEOUT_MS = 10000;

    private TesterSslEnginePair engines;
    private AsynchronousServerSocketChannel listener;
    private AsynchronousSocketChannel clientSock;
    private AsynchronousSocketChannel serverSock;


    @Before
    public void setUp() throws Exception {
        engines = new TesterSslEnginePair();
        listener = AsynchronousServerSocketChannel.open();
        listener.bind(new InetSocketAddress("127.0.0.1", 0));
        clientSock = AsynchronousSocketChannel.open();
        clientSock.connect(listener.getLocalAddress()).get();
        serverSock = listener.accept().get();
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
    public void testNormalVectoredRead() throws Exception {
        int appSize = engines.getApplicationBufferSize();
        SocketBufferHandler bh = new SocketBufferHandler(appSize, appSize, false);
        SecureNio2Channel ch = newChannel(bh);
        byte[] payload = engines.wrapRecord(1000, ch.netInBuffer);

        ByteBuffer dst = bh.getReadBuffer();
        Result result = read(ch, dst);

        Assert.assertNull(result.failure);
        Assert.assertEquals(payload.length, result.read);
        TesterSslEnginePair.assertContent(payload, dst);
    }


    /*
     * Regression guard: passes with and without the fix. expand() replaces the socket read buffer with a larger one and
     * the read succeeds.
     */
    @Test
    public void testVectoredExpandGrowsSocketBuffer() throws Exception {
        int appSize = engines.getApplicationBufferSize();
        SocketBufferHandler bh = new SocketBufferHandler(appSize / 2, appSize / 2, false);
        SecureNio2Channel ch = newChannel(bh);
        byte[] payload = engines.wrapRecord(appSize, ch.netInBuffer);
        Assert.assertTrue(payload.length > appSize / 2);

        Result result = read(ch, bh.getReadBuffer());

        Assert.assertNull(result.failure);
        Assert.assertEquals(payload.length, result.read);
        ByteBuffer dst = bh.getReadBuffer();
        Assert.assertTrue(dst.capacity() >= appSize);
        TesterSslEnginePair.assertContent(payload, dst);
    }


    /*
     * The socket read buffer is full and already at the application buffer size, so expand() returns the same buffer.
     */
    @Test(timeout = TIMEOUT_MS)
    public void testVectoredNoRoomSocketBuffer() throws Exception {
        int appSize = engines.getApplicationBufferSize();
        SocketBufferHandler bh = new SocketBufferHandler(appSize, appSize, false);
        SecureNio2Channel ch = newChannel(bh);
        engines.wrapRecord(appSize, ch.netInBuffer);

        ByteBuffer dst = bh.getReadBuffer();
        dst.position(dst.limit());
        assertNoRoom(read(ch, dst));
    }


    /*
     * The application buffer has no room and expand() does nothing.
     */
    @Test(timeout = TIMEOUT_MS)
    public void testVectoredNoRoomAppBuffer() throws Exception {
        int appSize = engines.getApplicationBufferSize();
        SecureNio2Channel ch = newChannel(new SocketBufferHandler(appSize, appSize, false));
        engines.wrapRecord(appSize, ch.netInBuffer);
        TesterAppBuffer app = new TesterAppBuffer(appSize, appSize, appSize, false);
        ch.setAppReadBufHandler(app);

        assertNoRoom(read(ch, app.getByteBuffer()));
    }


    /*
     * The application buffer is large enough but has little room left, and expand() only moves the limit to the
     * capacity. The first expand() adds some room, but not enough for the record, and the second adds none.
     */
    @Test(timeout = TIMEOUT_MS)
    public void testVectoredAppBufferExpandOnlyMovesLimit() throws Exception {
        int appSize = engines.getApplicationBufferSize();
        SecureNio2Channel ch = newChannel(new SocketBufferHandler(appSize, appSize, false));
        engines.wrapRecord(appSize, ch.netInBuffer);
        TesterAppBuffer app = new TesterAppBuffer(appSize, appSize - 100, appSize - 92, true);
        ch.setAppReadBufHandler(app);

        assertNoRoom(read(ch, app.getByteBuffer()));
    }


    /*
     * The destination is not one of the channel's buffers so the socket read buffer is added as an overflow buffer.
     * It is too small for the record, so the next overflow must expand it (it is one of the destinations by then)
     * rather than add it again.
     */
    @Test(timeout = TIMEOUT_MS)
    public void testVectoredOverflowBufferTooSmall() throws Exception {
        int appSize = engines.getApplicationBufferSize();
        SocketBufferHandler bh = new SocketBufferHandler(appSize / 2, appSize / 2, false);
        SecureNio2Channel ch = newChannel(bh);
        byte[] payload = engines.wrapRecord(appSize, ch.netInBuffer);
        Assert.assertTrue(payload.length > appSize / 2);

        Result result = read(ch, ByteBuffer.allocate(0));

        // The read must complete rather than loop. Both outcomes are acceptable here: the overflow buffer was expanded
        // and the data is read later, or the read fails because no more room could be made.
        Assert.assertTrue(result.completed);
        Assert.assertTrue(bh.getReadBuffer().capacity() >= appSize);
    }


    private SecureNio2Channel newChannel(SocketBufferHandler bh) throws Exception {
        SecureNio2Channel ch = new SecureNio2Channel(bh, new Nio2Endpoint());
        ch.sc = serverSock;
        ch.sslEngine = engines.getServer();
        // The field is private. Setting it avoids performing a real handshake.
        Field handshakeComplete = SecureNio2Channel.class.getDeclaredField("handshakeComplete");
        handshakeComplete.setAccessible(true);
        handshakeComplete.setBoolean(ch, true);
        return ch;
    }


    private static Result read(SecureNio2Channel ch, ByteBuffer dst) {
        Result result = new Result();
        // netInBuffer holds the record so the read does not wait for the socket and completes on this thread.
        ch.read(new ByteBuffer[] { dst }, 0, 1, 1, TimeUnit.SECONDS, null, new CompletionHandler<Long,Object>() {
            @Override
            public void completed(Long read, Object attachment) {
                result.completed = true;
                result.read = read.longValue();
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                result.completed = true;
                result.failure = exc;
            }
        });
        return result;
    }


    /*
     * The message must be unwrapFailNoRoom, not another failure such as unwrapFail, so a stray IOException cannot pass
     * the tests. The expected message is looked up so the check does not depend on the locale.
     */
    private static void assertNoRoom(Result result) {
        Assert.assertTrue(result.completed);
        Assert.assertTrue(String.valueOf(result.failure), result.failure instanceof IOException);
        Assert.assertEquals(sm.getString("channel.nio.ssl.unwrapFailNoRoom", Status.BUFFER_OVERFLOW),
                result.failure.getMessage());
    }


    private static class Result {
        boolean completed;
        long read;
        Throwable failure;
    }
}
