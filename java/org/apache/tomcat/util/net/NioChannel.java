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
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import org.apache.tomcat.util.net.NioEndpoint.Poller;
import org.apache.tomcat.util.res.StringManager;

/**
 * Base class for a SocketChannel wrapper used by the endpoint.
 * This way, logic for a SSL socket channel remains the same as for
 * a non SSL, making sure we don't need to code for any exception cases.
 *
 * @version 1.0
 */
public class NioChannel implements ByteChannel {

    protected static final StringManager sm = StringManager.getManager(NioChannel.class);

    protected static final ByteBuffer emptyBuf = ByteBuffer.allocate(0);

    protected SocketChannel sc = null;
    protected SocketWrapperBase<NioChannel> socketWrapper = null;

    protected final SocketBufferHandler bufHandler;

    protected Poller poller;

    public NioChannel(SocketChannel channel, SocketBufferHandler bufHandler) {
        this.sc = channel;
        this.bufHandler = bufHandler;
    }

    /**
     * Reset the channel
     *
     * @throws IOException If a problem was encountered resetting the channel
     */
    public void reset() throws IOException {
        bufHandler.reset();
    }


    void setSocketWrapper(SocketWrapperBase<NioChannel> socketWrapper) {
        this.socketWrapper = socketWrapper;
    }

    /**
     * Free the channel memory
     */
    public void free() {
        bufHandler.free();
    }

    /**
     * Returns true if the network buffer has been flushed out and is empty.
     *
     * @param block     Unused. May be used when overridden
     * @param s         Unused. May be used when overridden
     * @param timeout   Unused. May be used when overridden
     * @return Always returns <code>true</code> since there is no network buffer
     *         in the regular channel
     *
     * @throws IOException Never for non-secure channel
     */
    public boolean flush(boolean block, Selector s, long timeout)
            throws IOException {
        return true;
    }


    /**
     * Closes this channel.
     *
     * @throws IOException If an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        getIOChannel().socket().close();
        getIOChannel().close();
    }

    /**
     * Close the connection.
     *
     * @param force Should the underlying socket be forcibly closed?
     *
     * @throws IOException If closing the secure channel fails.
     */
    public void close(boolean force) throws IOException {
        if (isOpen() || force ) close();
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
        return sc.write(src);
    }

    /**
     * Reads a sequence of bytes from this channel into the given buffer.
     *
     * @param dst The buffer into which bytes are to be transferred
     * @return The number of bytes read, possibly zero, or <tt>-1</tt> if the
     *         channel has reached end-of-stream
     * @throws IOException If some other I/O error occurs
     */
    @Override
    public int read(ByteBuffer dst) throws IOException {
        return sc.read(dst);
    }

    public Object getAttachment() {
        Poller pol = getPoller();
        Selector sel = pol!=null?pol.getSelector():null;
        SelectionKey key = sel!=null?getIOChannel().keyFor(sel):null;
        Object att = key!=null?key.attachment():null;
        return att;
    }

    public SocketBufferHandler getBufHandler() {
        return bufHandler;
    }

    public Poller getPoller() {
        return poller;
    }

    public SocketChannel getIOChannel() {
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

    public void setPoller(Poller poller) {
        this.poller = poller;
    }

    public void setIOChannel(SocketChannel IOChannel) {
        this.sc = IOChannel;
    }

    @Override
    public String toString() {
        return super.toString()+":"+this.sc.toString();
    }

    public int getOutboundRemaining() {
        return 0;
    }

    /**
     * Return true if the buffer wrote data. NO-OP for non-secure channel.
     *
     * @return Always returns {@code false} for non-secure channel
     *
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
}
