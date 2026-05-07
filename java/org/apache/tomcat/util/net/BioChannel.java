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
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

import org.apache.tomcat.util.net.BioEndpoint.BioSocketWrapper;
import org.apache.tomcat.util.res.StringManager;

/**
 * Base class for a SocketChannel wrapper used by the endpoint.
 * This way, logic for an SSL socket channel remains the same as for
 * a non SSL, making sure we don't need to code for any exception cases.
 */
public class BioChannel implements ByteChannel, ScatteringByteChannel, GatheringByteChannel {

    protected static final StringManager sm = StringManager.getManager(BioChannel.class);

    protected static final ByteBuffer emptyBuf = ByteBuffer.allocate(0);

    protected final SocketBufferHandler bufHandler;
    protected Socket sc = null;
    protected BioSocketWrapper socketWrapper = null;

    public BioChannel(SocketBufferHandler bufHandler) {
        this.bufHandler = bufHandler;
    }

    /**
     * Reset the channel
     *
     * @param socket the socket
     * @param socketWrapper the socket wrapper
     * @throws IOException If a problem was encountered resetting the channel
     */
    public void reset(Socket socket, BioSocketWrapper socketWrapper) throws IOException {
        this.sc = socket;
        this.socketWrapper = socketWrapper;
        bufHandler.reset();
    }

    /**
     * @return the socketWrapper
     */
    BioSocketWrapper getSocketWrapper() {
        return socketWrapper;
    }

    /**
     * Free the channel memory
     */
    public void free() {
        bufHandler.free();
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
     * @return <code>true</code> if, and only if, this channel is open
     */
    @Override
    public boolean isOpen() {
        return !sc.isClosed();
    }

    /**
     * Writes a sequence of bytes to this channel from the given buffer.
     *
     * @param src The buffer from which bytes are to be retrieved
     * @return The number of bytes written, possibly zero
     * @throws IOException If some other I/O error occurs
     */
    @Override
    public int write(ByteBuffer src) throws IOException {
        checkInterruptStatus();
        if (!src.hasRemaining()) {
            // Nothing left to write
            return 0;
        }
        int len = src.remaining();
        sc.getOutputStream().write(src.array(), src.position(), src.remaining());
        return len;
    }

    @Override
    public long write(ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length)
            throws IOException {
        checkInterruptStatus();
        long n = 0;
        for (int i = 0; i < length; i++) {
            n += write(srcs[i + offset]);
        }
        return n;
    }

    /**
     * Reads a sequence of bytes from this channel into the given buffer.
     *
     * @param dst The buffer into which bytes are to be transferred
     * @return The number of bytes read, possibly zero, or <code>-1</code> if
     *         the channel has reached end-of-stream
     * @throws IOException If some other I/O error occurs
     */
    @Override
    public int read(ByteBuffer dst) throws IOException {
        return sc.getInputStream().read(dst.array(), dst.position(), dst.remaining());
    }

    @Override
    public long read(ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length)
            throws IOException {
        // TODO
        return read(dsts[offset]);
    }

    public SocketBufferHandler getBufHandler() {
        return bufHandler;
    }

    public Socket getIOChannel() {
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
     * @param read  Unused in non-secure implementation
     * @param write Unused in non-secure implementation
     * @return Always returns zero
     * @throws IOException Never for non-secure channel
     */
    public int handshake(boolean read, boolean write) throws IOException {
        return 0;
    }

    @Override
    public String toString() {
        return super.toString() + ":" + sc;
    }

    public int getOutboundRemaining() {
        return 0;
    }

    /**
     * Return true if the buffer wrote data. NO-OP for non-secure channel.
     *
     * @return Always returns {@code false} for non-secure channel
     * @throws IOException Never for non-secure channel
     */
    public boolean flushOutbound() throws IOException {
        return false;
    }

    /**
     * This method should be used to check the interrupt status before
     * attempting a write.
     *
     * If a thread has been interrupted and the interrupt has not been cleared
     * then an attempt to write to the socket will fail. When this happens the
     * socket is removed from the poller without the socket being selected. This
     * results in a connection limit leak for NIO as the endpoint expects the
     * socket to be selected even in error conditions.
     *
     * @throws IOException If the current thread was interrupted
     */
    protected void checkInterruptStatus() throws IOException {
        if (Thread.interrupted()) {
            throw new IOException(sm.getString("channel.nio.interrupted"));
        }
    }

    private ApplicationBufferHandler appReadBufHandler;
    public void setAppReadBufHandler(ApplicationBufferHandler handler) {
        this.appReadBufHandler = handler;
    }
    protected ApplicationBufferHandler getAppReadBufHandler() {
        return appReadBufHandler;
    }

    static final BioChannel CLOSED_BIO_CHANNEL = new BioChannel(SocketBufferHandler.EMPTY) {
        @Override
        public void close() throws IOException {
        }
        @Override
        public boolean isOpen() {
            return false;
        }
        @Override
        public void reset(Socket socket, BioSocketWrapper socketWrapper) throws IOException {
        }
        @Override
        public void free() {
        }
        @Override
        protected ApplicationBufferHandler getAppReadBufHandler() {
            return ApplicationBufferHandler.EMPTY;
        }
        @Override
        public void setAppReadBufHandler(ApplicationBufferHandler handler) {
        }
        @Override
        public int read(ByteBuffer dst) throws IOException {
            return -1;
        }
        @Override
        public long read(ByteBuffer[] dsts, int offset, int length)
                throws IOException {
            return -1L;
        }
        @Override
        public int write(ByteBuffer src) throws IOException {
            checkInterruptStatus();
            throw new ClosedChannelException();
        }
        @Override
        public long write(ByteBuffer[] srcs, int offset, int length)
                throws IOException {
            throw new ClosedChannelException();
        }
        @Override
        public String toString() {
            return "Closed NioChannel";
        }
    };

}
