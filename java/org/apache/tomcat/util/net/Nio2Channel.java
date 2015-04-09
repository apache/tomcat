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
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Base class for a SocketChannel wrapper used by the endpoint.
 * This way, logic for a SSL socket channel remains the same as for
 * a non SSL, making sure we don't need to code for any exception cases.
 */
public class Nio2Channel implements AsynchronousByteChannel {

    protected static ByteBuffer emptyBuf = ByteBuffer.allocate(0);

    protected AsynchronousSocketChannel sc = null;
    protected SocketWrapperBase<Nio2Channel> socket = null;
    protected final SocketBufferHandler bufHandler;

    public Nio2Channel(SocketBufferHandler bufHandler) {
        this.bufHandler = bufHandler;
    }

    /**
     * Reset the channel
     *
     * @throws IOException If a problem was encountered resetting the channel
     */
    public void reset(AsynchronousSocketChannel channel, SocketWrapperBase<Nio2Channel> socket)
            throws IOException {
        this.sc = channel;
        this.socket = socket;
        bufHandler.reset();
    }

    public SocketWrapperBase<Nio2Channel> getSocket() {
        return socket;
    }


    /**
     * Closes this channel.
     *
     * @throws IOException If an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        sc.close();
    }


    /**
     * Close the connection.
     *
     * @param force Should the underlying socket be forcibly closed?
     *
     * @throws IOException If closing the secure channel fails.
     */
    public void close(boolean force) throws IOException {
        if (isOpen() || force) {
            close();
        }
    }


    /**
     * Tells whether or not this channel is open.
     *
     * @return <tt>true</tt> if, and only if, this channel is open
     */
    @Override
    public boolean isOpen() {
        return sc.isOpen();
    }

    public SocketBufferHandler getBufHandler() {
        return bufHandler;
    }

    public AsynchronousSocketChannel getIOChannel() {
        return sc;
    }

    public boolean isClosing() {
        return false;
    }

    public boolean isHandshakeComplete() {
        return true;
    }

    /**
     * Performs SSL handshake hence is a no-op for the non-secure
     * implementation.
     *
     * @return Always returns zero
     * @throws IOException
     */
    public int handshake() throws IOException {
        return 0;
    }

    @Override
    public String toString() {
        return super.toString()+":"+this.sc.toString();
    }

    @Override
    public Future<Integer> read(ByteBuffer dst) {
        return sc.read(dst);
    }

    @Override
    public <A> void read(ByteBuffer dst, A attachment,
            CompletionHandler<Integer, ? super A> handler) {
        read(dst, Integer.MAX_VALUE, TimeUnit.MILLISECONDS, attachment, handler);
    }

    public <A> void read(ByteBuffer dst,
            long timeout, TimeUnit unit, A attachment,
            CompletionHandler<Integer, ? super A> handler) {
        sc.read(dst, timeout, unit, attachment, handler);
    }

    public <A> void read(ByteBuffer[] dsts,
            int offset, int length, long timeout, TimeUnit unit,
            A attachment, CompletionHandler<Long,? super A> handler) {
        sc.read(dsts, offset, length, timeout, unit, attachment, handler);
    }

    @Override
    public Future<Integer> write(ByteBuffer src) {
        return sc.write(src);
    }

    @Override
    public <A> void write(ByteBuffer src, A attachment,
            CompletionHandler<Integer, ? super A> handler) {
        write(src, Integer.MAX_VALUE, TimeUnit.MILLISECONDS, attachment, handler);
    }

    public <A> void write(ByteBuffer src, long timeout, TimeUnit unit, A attachment,
            CompletionHandler<Integer, ? super A> handler) {
        sc.write(src, timeout, unit, attachment, handler);
    }

    public <A> void write(ByteBuffer[] srcs, int offset, int length,
            long timeout, TimeUnit unit, A attachment,
            CompletionHandler<Long,? super A> handler) {
        sc.write(srcs, offset, length, timeout, unit, attachment, handler);
    }

    private static final Future<Boolean> DONE = new Future<Boolean>() {
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }
        @Override
        public boolean isCancelled() {
            return false;
        }
        @Override
        public boolean isDone() {
            return true;
        }
        @Override
        public Boolean get() throws InterruptedException,
                ExecutionException {
            return Boolean.TRUE;
        }
        @Override
        public Boolean get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException,
                TimeoutException {
            return Boolean.TRUE;
        }
    };

    public Future<Boolean> flush() {
        return DONE;
    }

}
