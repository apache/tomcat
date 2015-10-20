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
import java.nio.channels.CompletionHandler;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.apache.tomcat.util.buf.ByteBufferHolder;
import org.apache.tomcat.util.res.StringManager;

public abstract class SocketWrapperBase<E> {

    protected static final StringManager sm = StringManager.getManager(SocketWrapperBase.class);

    private volatile E socket;
    private final AbstractEndpoint<E> endpoint;

    // Volatile because I/O and setting the timeout values occurs on a different
    // thread to the thread checking the timeout.
    private volatile long readTimeout = -1;
    private volatile long writeTimeout = -1;

    private volatile int keepAliveLeft = 100;
    private boolean keptAlive = false;
    private volatile boolean upgraded = false;
    private boolean secure = false;
    private String negotiatedProtocol = null;
    /*
     * Following cached for speed / reduced GC
     */
    protected String localAddr = null;
    protected String localName = null;
    protected int localPort = -1;
    protected String remoteAddr = null;
    protected String remoteHost = null;
    protected int remotePort = -1;
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

    /**
     * The buffers used for communicating with the socket.
     */
    protected volatile SocketBufferHandler socketBufferHandler = null;

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

    public boolean isUpgraded() { return upgraded; }
    public void setUpgraded(boolean upgraded) { this.upgraded = upgraded; }
    public boolean isSecure() { return secure; }
    public void setSecure(boolean secure) { this.secure = secure; }
    public String getNegotiatedProtocol() { return negotiatedProtocol; }
    public void setNegotiatedProtocol(String negotiatedProtocol) {
        this.negotiatedProtocol = negotiatedProtocol;
    }

    /**
     * Set the timeout for reading. Values of zero or less will be changed to]
     * -1.
     *
     * @param readTimeout The timeout in milliseconds. A value of -1 indicates
     *                    an infinite timeout.
     */
    public void setReadTimeout(long readTimeout) {
        if (readTimeout > 0) {
            this.readTimeout = readTimeout;
        } else {
            this.readTimeout = -1;
        }
    }

    public long getReadTimeout() {
        return this.readTimeout;
    }

    /**
     * Set the timeout for writing. Values of zero or less will be changed to
     * -1.
     *
     * @param writeTimeout The timeout in milliseconds. A value of zero or less
     *                    indicates an infinite timeout.
     */
    public void setWriteTimeout(long writeTimeout) {
        if (writeTimeout > 0) {
            this.writeTimeout = writeTimeout;
        } else {
            this.writeTimeout = -1;
        }
    }

    public long getWriteTimeout() {
        return this.writeTimeout;
    }


    public void setKeepAliveLeft(int keepAliveLeft) { this.keepAliveLeft = keepAliveLeft;}
    public int decrementKeepAlive() { return (--keepAliveLeft);}
    public boolean isKeptAlive() {return keptAlive;}
    public void setKeptAlive(boolean keptAlive) {this.keptAlive = keptAlive;}

    public String getRemoteHost() {
        if (remoteHost == null) {
            populateRemoteHost();
        }
        return remoteHost;
    }
    protected abstract void populateRemoteHost();

    public String getRemoteAddr() {
        if (remoteAddr == null) {
            populateRemoteAddr();
        }
        return remoteAddr;
    }
    protected abstract void populateRemoteAddr();

    public int getRemotePort() {
        if (remotePort == -1) {
            populateRemotePort();
        }
        return remotePort;
    }
    protected abstract void populateRemotePort();

    public String getLocalName() {
        if (localName == null) {
            populateLocalName();
        }
        return localName;
    }
    protected abstract void populateLocalName();

    public String getLocalAddr() {
        if (localAddr == null) {
            populateLocalAddr();
        }
        return localAddr;
    }
    protected abstract void populateLocalAddr();

    public int getLocalPort() {
        if (localPort == -1) {
            populateLocalPort();
        }
        return localPort;
    }
    protected abstract void populateLocalPort();

    public boolean getBlockingStatus() { return blockingStatus; }
    public void setBlockingStatus(boolean blockingStatus) {
        this.blockingStatus = blockingStatus;
    }
    public Lock getBlockingStatusReadLock() { return blockingStatusReadLock; }
    public WriteLock getBlockingStatusWriteLock() {
        return blockingStatusWriteLock;
    }
    public Object getWriteThreadLock() { return writeThreadLock; }
    public SocketBufferHandler getSocketBufferHandler() { return socketBufferHandler; }
    public abstract boolean isReadPending();
    public abstract boolean isWritePending();

    public boolean hasDataToWrite() {
        return !socketBufferHandler.isWriteBufferEmpty() || bufferedWrites.size() > 0;
    }

    /**
     * Checks to see if there are any writes pending and if there are calls
     * {@link #registerWriteInterest()} to trigger a callback once the pending
     * writes have completed.
     * <p>
     * Note: Once this method has returned <code>false</code> it <b>MUST NOT</b>
     *       be called again until the pending write has completed and the
     *       callback has been fired.
     *       TODO: Modify {@link #registerWriteInterest()} so the above
     *       restriction is enforced there rather than relying on the caller.
     *
     * @return <code>true</code> if no writes are pending and data can be
     *         written otherwise <code>false</code>
     */
    public boolean isReadyForWrite() {
        boolean result = canWrite();
        if (!result) {
            registerWriteInterest();
        }
        return result;
    }


    public boolean canWrite() {
        if (socketBufferHandler == null) {
            throw new IllegalStateException(sm.getString("socket.closed"));
        }
        return socketBufferHandler.isWriteBufferWritable() && bufferedWrites.size() == 0;
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
    public abstract boolean isReadyForRead() throws IOException;

    /**
     * Return input that has been read to the input buffer for re-reading by the
     * correct component. There are times when a component may read more data
     * than it needs before it passes control to another component. One example
     * of this is during HTTP upgrade. If an (arguably misbehaving client) sends
     * data associated with the upgraded protocol before the HTTP upgrade
     * completes, the HTTP handler may read it. This method provides a way for
     * that data to be returned so it can be processed by the correct component.
     *
     * @param returnedInput The input to return to the input buffer.
     */
    public void unRead(ByteBuffer returnedInput) {
        if (returnedInput != null) {
            socketBufferHandler.configureReadBufferForWrite();
            socketBufferHandler.getReadBuffer().put(returnedInput);
        }
    }


    public abstract void close() throws IOException;


    /**
     * Writes the provided data to the socket, buffering any remaining data if
     * used in non-blocking mode.
     *
     * @param block <code>true</code> if a blocking write should be used,
     *                  otherwise a non-blocking write will be used
     * @param buf   The byte array containing the data to be written
     * @param off   The offset within the byte array of the data to be written
     * @param len   The length of the data to be written
     *
     * @throws IOException If an IO error occurs during the write
     */
    public final void write(boolean block, byte[] buf, int off, int len) throws IOException {
        if (len == 0 || buf == null || getSocket() == null) {
            return;
        }

        // While the implementations for blocking and non-blocking writes are
        // very similar they have been split into separate methods to allow
        // sub-classes to override them individually. NIO2, for example,
        // overrides the non-blocking write but not the blocking write.
        if (block) {
            writeBlocking(buf, off, len);
        } else {
            writeNonBlocking(buf, off, len);
        }
    }


    /**
     * Transfers the data to the socket write buffer (writing that data to the
     * socket if the buffer fills up using a blocking write) until all the data
     * has been transferred and space remains in the socket write buffer.
     *
     * @param buf   The byte array containing the data to be written
     * @param off   The offset within the byte array of the data to be written
     * @param len   The length of the data to be written
     *
     * @throws IOException If an IO error occurs during the write
     */
    protected void writeBlocking(byte[] buf, int off, int len) throws IOException {
        // Note: There is an implementation assumption that if the switch from
        //       non-blocking to blocking has been made then any pending
        //       non-blocking writes were flushed at the time the switch
        //       occurred.

        // Keep writing until all the data has been transferred to the socket
        // write buffer and space remains in that buffer
        socketBufferHandler.configureWriteBufferForWrite();
        int thisTime = transfer(buf, off, len, socketBufferHandler.getWriteBuffer());
        while (socketBufferHandler.getWriteBuffer().remaining() == 0) {
            len = len - thisTime;
            off = off + thisTime;
            doWrite(true);
            socketBufferHandler.configureWriteBufferForWrite();
            thisTime = transfer(buf, off, len, socketBufferHandler.getWriteBuffer());
        }
    }


    /**
     * Transfers the data to the socket write buffer (writing that data to the
     * socket if the buffer fills up using a non-blocking write) until either
     * all the data has been transferred and space remains in the socket write
     * buffer or a non-blocking write leaves data in the socket write buffer.
     *
     * @param buf   The byte array containing the data to be written
     * @param off   The offset within the byte array of the data to be written
     * @param len   The length of the data to be written
     *
     * @throws IOException If an IO error occurs during the write
     */
    protected void writeNonBlocking(byte[] buf, int off, int len) throws IOException {
        if (bufferedWrites.size() == 0 && socketBufferHandler.isWriteBufferWritable()) {
            socketBufferHandler.configureWriteBufferForWrite();
            int thisTime = transfer(buf, off, len, socketBufferHandler.getWriteBuffer());
            len = len - thisTime;
            while (!socketBufferHandler.isWriteBufferWritable()) {
                off = off + thisTime;
                doWrite(false);
                if (len > 0 && socketBufferHandler.isWriteBufferWritable()) {
                    socketBufferHandler.configureWriteBufferForWrite();
                    thisTime = transfer(buf, off, len, socketBufferHandler.getWriteBuffer());
                } else {
                    // Didn't write any data in the last non-blocking write.
                    // Therefore the write buffer will still be full. Nothing
                    // else to do here. Exit the loop.
                    break;
                }
                len = len - thisTime;
            }
        }

        if (len > 0) {
            // Remaining data must be buffered
            addToBuffers(buf, off, len);
        }
    }


    /**
     * Writes as much data as possible from any that remains in the buffers.
     *
     * @param block <code>true</code> if a blocking write should be used,
     *                  otherwise a non-blocking write will be used
     *
     * @return <code>true</code> if data remains to be flushed after this method
     *         completes, otherwise <code>false</code>. In blocking mode
     *         therefore, the return value should always be <code>false</code>
     *
     * @throws IOException If an IO error occurs during the write
     */
    public boolean flush(boolean block) throws IOException {
        if (getSocket() == null) {
            return false;
        }

        boolean result = false;
        if (block) {
            // A blocking flush will always empty the buffer.
            flushBlocking();
        } else {
            result = flushNonBlocking();
        }

        return result;
    }


    protected void flushBlocking() throws IOException {
        doWrite(true);

        if (bufferedWrites.size() > 0) {
            Iterator<ByteBufferHolder> bufIter = bufferedWrites.iterator();
            while (socketBufferHandler.isWriteBufferEmpty() && bufIter.hasNext()) {
                ByteBufferHolder buffer = bufIter.next();
                buffer.flip();
                while (socketBufferHandler.isWriteBufferEmpty() && buffer.getBuf().remaining()>0) {
                    socketBufferHandler.configureWriteBufferForWrite();
                    transfer(buffer.getBuf(), socketBufferHandler.getWriteBuffer());
                    if (buffer.getBuf().remaining() == 0) {
                        bufIter.remove();
                    }
                    doWrite(true);
                }
            }
        }

    }


    protected boolean flushNonBlocking() throws IOException {
        boolean dataLeft = !socketBufferHandler.isWriteBufferEmpty();

        // Write to the socket, if there is anything to write
        if (dataLeft) {
            doWrite(false);
        }

        dataLeft = !socketBufferHandler.isWriteBufferEmpty();

        if (!dataLeft && bufferedWrites.size() > 0) {
            Iterator<ByteBufferHolder> bufIter = bufferedWrites.iterator();
            while (socketBufferHandler.isWriteBufferEmpty() && bufIter.hasNext()) {
                ByteBufferHolder buffer = bufIter.next();
                buffer.flip();
                while (socketBufferHandler.isWriteBufferEmpty() && buffer.getBuf().remaining() > 0) {
                    socketBufferHandler.configureWriteBufferForWrite();
                    transfer(buffer.getBuf(), socketBufferHandler.getWriteBuffer());
                    if (buffer.getBuf().remaining() == 0) {
                        bufIter.remove();
                    }
                    doWrite(false);
                }
            }
        }

        return !socketBufferHandler.isWriteBufferEmpty();
    }

    /**
     * Write the contents of the socketWriteBuffer to the socket. For blocking
     * writes either then entire contents of the buffer will be written or an
     * IOException will be thrown. Partial blocking writes will not occur.
     *
     * @param block Should the write be blocking or not?
     *
     * @throws IOException If an I/O error such as a timeout occurs during the
     *                     write
     */
    protected final void doWrite(boolean block) throws IOException {
        doWriteInternal(block);
    }

    /**
     * Write the contents of the socketWriteBuffer to the socket. For blocking
     * writes either then entire contents of the buffer will be written or an
     * IOException will be thrown. Partial blocking writes will not occur.
     *
     * @param block Should the write be blocking or not?
     *
     * @throws IOException If an I/O error such as a timeout occurs during the
     *                     write
     */
    protected abstract void doWriteInternal(boolean block) throws IOException;


    protected void addToBuffers(byte[] buf, int offset, int length) {
        ByteBufferHolder holder = bufferedWrites.peekLast();
        if (holder==null || holder.isFlipped() || holder.getBuf().remaining()<length) {
            ByteBuffer buffer = ByteBuffer.allocate(Math.max(bufferedWriteSize,length));
            holder = new ByteBufferHolder(buffer,false);
            bufferedWrites.add(holder);
        }
        holder.getBuf().put(buf,offset,length);
    }


    public void processSocket(SocketStatus socketStatus, boolean dispatch) {
        endpoint.processSocket(this, socketStatus, dispatch);
    }


    public synchronized void executeNonBlockingDispatches(Iterator<DispatchType> dispatches) {
        /*
         * This method is called when non-blocking IO is initiated by defining
         * a read and/or write listener in a non-container thread. It is called
         * once the non-container thread completes so that the first calls to
         * onWritePossible() and/or onDataAvailable() as appropriate are made by
         * the container.
         *
         * Processing the dispatches requires (for APR/native at least)
         * that the socket has been added to the waitingRequests queue. This may
         * not have occurred by the time that the non-container thread completes
         * triggering the call to this method. Therefore, the coded syncs on the
         * SocketWrapper as the container thread that initiated this
         * non-container thread holds a lock on the SocketWrapper. The container
         * thread will add the socket to the waitingRequests queue before
         * releasing the lock on the socketWrapper. Therefore, by obtaining the
         * lock on socketWrapper before processing the dispatches, we can be
         * sure that the socket has been added to the waitingRequests queue.
         */
        while (dispatches != null && dispatches.hasNext()) {
            DispatchType dispatchType = dispatches.next();
            processSocket(dispatchType.getSocketStatus(), false);
        }
    }


    public abstract void registerReadInterest();

    public abstract void registerWriteInterest();

    public abstract SendfileDataBase createSendfileData(String filename, long pos, long length);

    /**
     * Starts the sendfile process. It is expected that if the sendfile process
     * does not complete during this call that the caller <b>will not</b> add
     * the socket to the poller (or equivalent). That is the responsibility of
     * this method.
     *
     * @param sendfileData Data representing the file to send
     *
     * @return The state of the sendfile process after the first write.
     */
    public abstract SendfileState processSendfile(SendfileDataBase sendfileData);

    /**
     * Require the client to perform CLIENT-CERT authentication if it hasn't
     * already done so.
     *
     * @param sslSupport The SSL/TLS support instance currently being used by
     *                   the connection that may need updating after the client
     *                   authentication
     */
    public abstract void doClientAuth(SSLSupport sslSupport);

    public abstract SSLSupport getSslSupport(String clientCertProvider);


    // ------------------------------------------------------- NIO 2 style APIs


    public enum CompletionState {
        /**
         * Operation is still pending.
         */
        PENDING,
        /**
         * The operation completed inline.
         */
        INLINE,
        /**
         * The operation completed inline but failed.
         */
        ERROR,
        /**
         * The operation completed, but not inline.
         */
        DONE
    }

    public enum CompletionHandlerCall {
        /**
         * Operation should continue, the completion handler shouldn't be
         * called.
         */
        CONTINUE,
        /**
         * The operation completed but the completion handler shouldn't be
         * called.
         */
        NONE,
        /**
         * The operation is complete, the completion handler should be
         * called.
         */
        DONE
    }

    public interface CompletionCheck {
        /**
         * Return true if enough data has been read or written and the
         * handler should be notified. Return false if the IO is
         * incomplete (data has not been fully written while it should,
         * or more data read is needed for further processing) and should
         * be continued before the completion handler is called.
         *
         * @param state of the operation (done or done inline since the
         *        IO call is done)
         * @param buffers ByteBuffer[] that has been passed to the
         *        original IO call
         * @param offset that has been passed to the original IO call
         * @param length that has been passed to the original IO call
         */
        public CompletionHandlerCall callHandler(CompletionState state, ByteBuffer[] buffers, int offset, int length);
    }

    /**
     * This utility CompletionCheck will cause the write to fully write
     * all remaining data. If the operation completes inline, the
     * completion handler will not be called.
     */
    public static final CompletionCheck COMPLETE_WRITE = new CompletionCheck() {
        @Override
        public CompletionHandlerCall callHandler(CompletionState state, ByteBuffer[] buffers, int offset, int length) {
            for (int i = 0; i < offset; i++) {
                if (buffers[i].remaining() > 0) {
                    return CompletionHandlerCall.CONTINUE;
                }
            }
            return (state == CompletionState.DONE) ? CompletionHandlerCall.DONE : CompletionHandlerCall.NONE;
        }
    };

    /**
     * This utility CompletionCheck will cause the completion handler
     * to be called once some data has been read. If the operation
     * completes inline, the completion handler will not be called.
     */
    public static final CompletionCheck READ_DATA = new CompletionCheck() {
        @Override
        public CompletionHandlerCall callHandler(CompletionState state, ByteBuffer[] buffers, int offset, int length) {
            return (state == CompletionState.DONE) ? CompletionHandlerCall.DONE : CompletionHandlerCall.NONE;
        }
    };

    /**
     * Scatter read. The completion handler will be called once some
     * data has been read or an error occurred. If a CompletionCheck
     * object has been provided, the completion handler will only be
     * called if the callHandler method returned true. If no
     * CompletionCheck object has been provided, the default NIO2
     * behavior is used: the completion handler will be called as soon
     * as some data has been read, even if the read has completed inline.
     *
     * @param block true to block until any pending read is done, if the
     *        timeout occurs and a read is still pending, a
     *        ReadPendingException will be thrown; false to
     *        not block but any pending read operation will cause
     *        a ReadPendingException
     * @param timeout
     * @param unit
     * @param attachment
     * @param check for the IO operation completion
     * @param handler to call when the IO is complete
     * @param dsts buffers
     * @return the completion state (done, done inline, or still pending)
     */
    public <A> CompletionState read(boolean block, long timeout, TimeUnit unit, A attachment,
            CompletionCheck check, CompletionHandler<Long, ? super A> handler,
            ByteBuffer... dsts) {
        if (dsts == null) {
            throw new IllegalArgumentException();
        }
        return read(dsts, 0, dsts.length, block, timeout, unit, attachment, check, handler);
    }

    /**
     * Scatter read. The completion handler will be called once some
     * data has been read or an error occurred. If a CompletionCheck
     * object has been provided, the completion handler will only be
     * called if the callHandler method returned true. If no
     * CompletionCheck object has been provided, the default NIO2
     * behavior is used: the completion handler will be called as soon
     * as some data has been read, even if the read has completed inline.
     *
     * @param dsts buffers
     * @param offset in the buffer array
     * @param length in the buffer array
     * @param block true to block until any pending read is done, if the
     *        timeout occurs and a read is still pending, a
     *        ReadPendingException will be thrown; false to
     *        not block but any pending read operation will cause
     *        a ReadPendingException
     * @param timeout
     * @param unit
     * @param attachment
     * @param check for the IO operation completion
     * @param handler to call when the IO is complete
     * @return the completion state (done, done inline, or still pending)
     */
    public abstract <A> CompletionState read(ByteBuffer[] dsts, int offset, int length,
            boolean block, long timeout, TimeUnit unit, A attachment,
            CompletionCheck check, CompletionHandler<Long, ? super A> handler);

    /**
     * Gather write. The completion handler will be called once some
     * data has been written or an error occurred. If a CompletionCheck
     * object has been provided, the completion handler will only be
     * called if the callHandler method returned true. If no
     * CompletionCheck object has been provided, the default NIO2
     * behavior is used: the completion handler will be called, even
     * if the write is incomplete and data remains in the buffers, or
     * if the write completed inline.
     *
     * @param block true to block until any pending write is done, if the
     *        timeout occurs and a write is still pending, a
     *        WritePendingException will be thrown; false to
     *        not block but any pending write operation will cause
     *        a WritePendingException
     * @param timeout
     * @param unit
     * @param attachment
     * @param check for the IO operation completion
     * @param handler to call when the IO is complete
     * @param srcs buffers
     * @return the completion state (done, done inline, or still pending)
     */
    public <A> CompletionState write(boolean block, long timeout, TimeUnit unit, A attachment,
            CompletionCheck check, CompletionHandler<Long, ? super A> handler,
            ByteBuffer... srcs) {
        if (srcs == null) {
            throw new IllegalArgumentException();
        }
        return write(srcs, 0, srcs.length, block, timeout, unit, attachment, check, handler);
    }

    /**
     * Gather write. The completion handler will be called once some
     * data has been written or an error occurred. If a CompletionCheck
     * object has been provided, the completion handler will only be
     * called if the callHandler method returned true. If no
     * CompletionCheck object has been provided, the default NIO2
     * behavior is used: the completion handler will be called, even
     * if the write is incomplete and data remains in the buffers, or
     * if the write completed inline.
     *
     * @param srcs buffers
     * @param offset in the buffer array
     * @param length in the buffer array
     * @param block true to block until any pending write is done, if the
     *        timeout occurs and a write is still pending, a
     *        WritePendingException will be thrown; false to
     *        not block but any pending write operation will cause
     *        a WritePendingException
     * @param timeout
     * @param unit
     * @param attachment
     * @param check for the IO operation completion
     * @param handler to call when the IO is complete
     * @return the completion state (done, done inline, or still pending)
     */
    public abstract <A> CompletionState write(ByteBuffer[] srcs, int offset, int length,
            boolean block, long timeout, TimeUnit unit, A attachment,
            CompletionCheck check, CompletionHandler<Long, ? super A> handler);


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
