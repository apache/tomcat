/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.util.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.apache.tomcat.util.buf.ByteBufferHolder;

public abstract class SocketWrapperBase<E> {

    private volatile E socket;
    private final AbstractEndpoint<E> endpoint;

    private volatile long lastAccess = System.currentTimeMillis();
    private volatile long lastAsyncStart = 0;
    private volatile long asyncTimeout = -1;
    private long timeout = -1;
    private IOException error = null;
    private volatile int keepAliveLeft = 100;
    private volatile boolean async = false;
    private boolean keptAlive = false;
    private volatile boolean upgraded = false;
    private boolean secure = false;
    /*
     * Following cached for speed / reduced GC
     */
    private String localAddr = null;
    private String localName = null;
    private int localPort = -1;
    private String remoteAddr = null;
    private String remoteHost = null;
    private int remotePort = -1;
    /*
     * Used if block/non-blocking is set at the socket level. The client is
     * responsible for the thread-safe use of this field via the locks provided.
     */
    private volatile boolean blockingStatus = true;
    private final Lock blockingStatusReadLock;
    private final WriteLock blockingStatusWriteLock;

    /*
     * In normal servlet processing only one thread is allowed to access the
     * socket at a time. That is controlled by a lock on the socket for both
     * read and writes). When HTTP upgrade is used, one read thread and one
     * write thread are allowed to access the socket concurrently. In this case
     * the lock on the socket is used for reads and the lock below is used for
     * writes.
     */
    private final Object writeThreadLock = new Object();

    protected volatile ByteBuffer socketWriteBuffer;
    protected volatile boolean writeBufferFlipped;

    /**
     * For "non-blocking" writes use an external set of buffers. Although the
     * API only allows one non-blocking write at a time, due to buffering and
     * the possible need to write HTTP headers, there may be more than one write
     * to the OutputBuffer.
     */
    protected final LinkedBlockingDeque<ByteBufferHolder> bufferedWrites =
            new LinkedBlockingDeque<>();

    /**
     * The max size of the buffered write buffer
     */
    protected int bufferedWriteSize = 64*1024; //64k default write buffer

    private Set<DispatchType> dispatches = new CopyOnWriteArraySet<>();

    public SocketWrapperBase(E socket, AbstractEndpoint<E> endpoint) {
        this.socket = socket;
        this.endpoint = endpoint;
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        this.blockingStatusReadLock = lock.readLock();
        this.blockingStatusWriteLock = lock.writeLock();
    }

    public E getSocket() {
        return socket;
    }

    public AbstractEndpoint<E> getEndpoint() {
        return endpoint;
    }

    public boolean isAsync() { return async; }
    /**
     * Sets the async flag for this connection. If this call causes the
     * connection to transition from non-async to async then the lastAsyncStart
     * property will be set using the current time. This property is used as the
     * start time when calculating the async timeout. As per the Servlet spec
     * the async timeout applies once the dispatch where startAsync() was called
     * has returned to the container (which is when this method is currently
     * called).
     *
     * @param async The new value of for the async flag
     */
    public void setAsync(boolean async) {
        if (!this.async && async) {
            lastAsyncStart = System.currentTimeMillis();
        }
        this.async = async;
    }
    /**
     * Obtain the time that this connection last transitioned to async
     * processing.
     *
     * @return The time (as returned by {@link System#currentTimeMillis()}) that
     *         this connection last transitioned to async
     */
    public long getLastAsyncStart() {
       return lastAsyncStart;
    }
    public void setAsyncTimeout(long timeout) {
        asyncTimeout = timeout;
    }
    public long getAsyncTimeout() {
        return asyncTimeout;
    }
    public boolean isUpgraded() { return upgraded; }
    public void setUpgraded(boolean upgraded) { this.upgraded = upgraded; }
    public boolean isSecure() { return secure; }
    public void setSecure(boolean secure) { this.secure = secure; }
    public long getLastAccess() { return lastAccess; }
    public void access() {
        access(System.currentTimeMillis());
    }
    void access(long access) { lastAccess = access; }
    public void setTimeout(long timeout) {this.timeout = timeout;}
    public long getTimeout() {return this.timeout;}
    public IOException getError() { return error; }
    public void setError(IOException error) { this.error = error; }
    public void setKeepAliveLeft(int keepAliveLeft) { this.keepAliveLeft = keepAliveLeft;}
    public int decrementKeepAlive() { return (--keepAliveLeft);}
    public boolean isKeptAlive() {return keptAlive;}
    public void setKeptAlive(boolean keptAlive) {this.keptAlive = keptAlive;}
    public int getLocalPort() { return localPort; }
    public void setLocalPort(int localPort) {this.localPort = localPort; }
    public String getLocalName() { return localName; }
    public void setLocalName(String localName) {this.localName = localName; }
    public String getLocalAddr() { return localAddr; }
    public void setLocalAddr(String localAddr) {this.localAddr = localAddr; }
    public int getRemotePort() { return remotePort; }
    public void setRemotePort(int remotePort) {this.remotePort = remotePort; }
    public String getRemoteHost() { return remoteHost; }
    public void setRemoteHost(String remoteHost) {this.remoteHost = remoteHost; }
    public String getRemoteAddr() { return remoteAddr; }
    public void setRemoteAddr(String remoteAddr) {this.remoteAddr = remoteAddr; }
    public boolean getBlockingStatus() { return blockingStatus; }
    public void setBlockingStatus(boolean blockingStatus) {
        this.blockingStatus = blockingStatus;
    }
    public Lock getBlockingStatusReadLock() { return blockingStatusReadLock; }
    public WriteLock getBlockingStatusWriteLock() {
        return blockingStatusWriteLock;
    }
    public Object getWriteThreadLock() { return writeThreadLock; }

    protected boolean hasMoreDataToFlush() {
        return (writeBufferFlipped && socketWriteBuffer.remaining() > 0) ||
        (!writeBufferFlipped && socketWriteBuffer.position() > 0);
    }

    public boolean hasDataToWrite() {
        return hasMoreDataToFlush() || bufferedWrites.size() > 0;
    }

    public boolean isReadyForWrite() {
        boolean result = !hasDataToWrite();
        if (!result) {
            registerWriteInterest();
        }
        return result;
    }

    public void addDispatch(DispatchType dispatchType) {
        synchronized (dispatches) {
            dispatches.add(dispatchType);
        }
    }
    public Iterator<DispatchType> getIteratorAndClearDispatches() {
        // Note: Logic in AbstractProtocol depends on this method only returning
        // a non-null value if the iterator is non-empty. i.e. it should never
        // return an empty iterator.
        Iterator<DispatchType> result;
        synchronized (dispatches) {
            // Synchronized as the generation of the iterator and the clearing
            // of dispatches needs to be an atomic operation.
            result = dispatches.iterator();
            if (result.hasNext()) {
                dispatches.clear();
            } else {
                result = null;
            }
        }
        return result;
    }
    public void clearDispatches() {
        synchronized (dispatches) {
            dispatches.clear();
        }
    }

    public void reset(E socket, long timeout) {
        async = false;
        blockingStatus = true;
        dispatches.clear();
        error = null;
        keepAliveLeft = 100;
        lastAccess = System.currentTimeMillis();
        lastAsyncStart = 0;
        asyncTimeout = -1;
        localAddr = null;
        localName = null;
        localPort = -1;
        remoteAddr = null;
        remoteHost = null;
        remotePort = -1;
        this.socket = socket;
        this.timeout = timeout;
        upgraded = false;
    }

    /**
     * Overridden for debug purposes. No guarantees are made about the format of
     * this message which may vary significantly between point releases.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return super.toString() + ":" + String.valueOf(socket);
    }


    public abstract int read(boolean block, byte[] b, int off, int len) throws IOException;
    public abstract boolean isReady() throws IOException;

    /**
     * Return input that has been read to the input buffer for re-reading by the
     * correct component. There are times when a component may read more data
     * than it needs before it passes control to another component. One example
     * of this is during HTTP upgrade. If an (arguably misbehaving client) sends
     * data associated with the upgraded protocol before the HTTP upgrade
     * completes, the HTTP handler may read it. This method provides a way for
     * that data to be returned so it can be processed by the correct component.
     *
     * @param input The input to return to the input buffer.
     */
    public abstract void unRead(ByteBuffer input);
    public abstract void close() throws IOException;

    /**
     * Writes the provided data to the socket, buffering any remaining data if
     * used in non-blocking mode. If any data remains in the buffers from a
     * previous write then that data will be written before this data. It is
     * therefore unnecessary to call flush() before calling this method.
     *
     * @param block <code>true<code> if a blocking write should be used,
     *                  otherwise a non-blocking write will be used
     * @param b     The byte array containing the data to be written
     * @param off   The offset within the byte array of the data to be written
     * @param len   The length of the data to be written
     *
     * @throws IOException If an IO error occurs during the write
     */
    public void write(boolean block, byte[] b, int off, int len) throws IOException {
        // Always flush any data remaining in the buffers
        boolean dataLeft = flush(block, true);

        if (len == 0 || b == null) {
            return;
        }

        // Keep writing until all the data is written or a non-blocking write
        // leaves data in the buffer
        while (!dataLeft && len > 0) {
            int thisTime = transfer(b, off, len, socketWriteBuffer);
            len = len - thisTime;
            off = off + thisTime;
            int written = doWrite(socketWriteBuffer, block, true);
            if (written == 0) {
                dataLeft = true;
            } else {
                dataLeft = flush(block, true);
            }
        }

        // Prevent timeouts for just doing client writes
        access();

        if (!block && len > 0) {
            // Remaining data must be buffered
            addToBuffers(b, off, len);
        }
    }


    /**
     * Writes as much data as possible from any that remains in the buffers.
     *
     * @param block <code>true<code> if a blocking write should be used,
     *                  otherwise a non-blocking write will be used
     *
     * @return <code>true</code> if data remains to be flushed after this method
     *         completes, otherwise <code>false</code>. In blocking mode
     *         therefore, the return value should always be <code>false</code>
     *
     * @throws IOException If an IO error occurs during the write
     */
    public boolean flush(boolean block) throws IOException {
        return flush(block, false);
    }


    /**
     * Writes as much data as possible from any that remains in the buffers.
     * This method exists for those implementations (e.g. NIO2) that need
     * slightly different behaviour depending on if flush() was called directly
     * or by another method in this class or a sub-class.
     *
     * @param block    <code>true<code> if a blocking write should be used,
     *                     otherwise a non-blocking write will be used
     * @param internal <code>true<code> if flush() was called by another method
     *                     in class or sub-class
     *
     * @return <code>true</code> if data remains to be flushed after this method
     *         completes, otherwise <code>false</code>. In blocking mode
     *         therefore, the return value should always be <code>false</code>
     *
     * @throws IOException If an IO error occurs during the write
     */
    protected boolean flush(boolean block, boolean internal) throws IOException {

        // Prevent timeout for async
        access();

        boolean dataLeft = hasMoreDataToFlush();

        // Write to the socket, if there is anything to write
        if (dataLeft) {
            doWrite(socketWriteBuffer, block, !writeBufferFlipped);
        }

        dataLeft = hasMoreDataToFlush();

        if (!dataLeft && bufferedWrites.size() > 0) {
            Iterator<ByteBufferHolder> bufIter = bufferedWrites.iterator();
            while (!hasMoreDataToFlush() && bufIter.hasNext()) {
                ByteBufferHolder buffer = bufIter.next();
                buffer.flip();
                while (!hasMoreDataToFlush() && buffer.getBuf().remaining()>0) {
                    transfer(buffer.getBuf(), socketWriteBuffer);
                    if (buffer.getBuf().remaining() == 0) {
                        bufIter.remove();
                    }
                    doWrite(socketWriteBuffer, block, true);
                    //here we must break if we didn't finish the write
                }
            }
        }

        return hasMoreDataToFlush();
    }


    protected abstract int doWrite(ByteBuffer buffer, boolean block, boolean flip)
            throws IOException;


    protected void addToBuffers(byte[] buf, int offset, int length) {
        ByteBufferHolder holder = bufferedWrites.peekLast();
        if (holder==null || holder.isFlipped() || holder.getBuf().remaining()<length) {
            ByteBuffer buffer = ByteBuffer.allocate(Math.max(bufferedWriteSize,length));
            holder = new ByteBufferHolder(buffer,false);
            bufferedWrites.add(holder);
        }
        holder.getBuf().put(buf,offset,length);
    }

    public abstract void registerWriteInterest();

    public abstract void regsiterForEvent(boolean read, boolean write);


    // --------------------------------------------------------- Utility methods

    protected static int transfer(byte[] from, int offset, int length, ByteBuffer to) {
        int max = Math.min(length, to.remaining());
        to.put(from, offset, max);
        return max;
    }

    protected static void transfer(ByteBuffer from, ByteBuffer to) {
        int max = Math.min(from.remaining(), to.remaining());
        int fromLimit = from.limit();
        from.limit(from.position() + max);
        to.put(from);
        from.limit(fromLimit);
    }
}
