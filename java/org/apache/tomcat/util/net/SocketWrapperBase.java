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

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.channels.InterruptedByTimeoutException;
import java.nio.channels.ReadPendingException;
import java.nio.channels.WritePendingException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.servlet.ServletConnection;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

public abstract class SocketWrapperBase<E> {

    private static final Log log = LogFactory.getLog(SocketWrapperBase.class);

    protected static final StringManager sm = StringManager.getManager(SocketWrapperBase.class);

    /*
     * At 100,000 connections a second there are enough IDs here for ~3,000,000
     * years before it overflows (and then we have another 3,000,000 years
     * before it gets back to zero).
     *
     * Local testing shows that 5 threads can obtain 60,000,000+ IDs a second
     * from a single AtomicLong. That is about about 17ns per request. It does
     * not appear that the introduction of this counter will cause a bottleneck
     * for connection processing.
     */
    private static final AtomicLong connectionIdGenerator = new AtomicLong(0);

    private E socket;
    private final AbstractEndpoint<E,?> endpoint;
    private final Lock lock = new ReentrantLock();

    protected final AtomicBoolean closed = new AtomicBoolean(false);

    // Volatile because I/O and setting the timeout values occurs on a different
    // thread to the thread checking the timeout.
    private volatile long readTimeout = -1;
    private volatile long writeTimeout = -1;

    protected volatile IOException previousIOException = null;

    private volatile int keepAliveLeft = 100;
    private String negotiatedProtocol = null;

    private final String connectionId;

    /*
     * Following cached for speed / reduced GC
     */
    protected String localAddr = null;
    protected String localName = null;
    protected int localPort = -1;
    protected String remoteAddr = null;
    protected String remoteHost = null;
    protected int remotePort = -1;
    protected volatile ServletConnection servletConnection = null;

    /**
     * Used to record the first IOException that occurs during non-blocking
     * read/writes that can't be usefully propagated up the stack since there is
     * no user code or appropriate container code in the stack to handle it.
     */
    private volatile IOException error = null;

    /**
     * The buffers used for communicating with the socket.
     */
    protected volatile SocketBufferHandler socketBufferHandler = null;

    /**
     * The max size of the individual buffered write buffers
     */
    protected int bufferedWriteSize = 64 * 1024; // 64k default write buffer

    /**
     * Additional buffer used for non-blocking writes. Non-blocking writes need
     * to return immediately even if the data cannot be written immediately but
     * the socket buffer may not be big enough to hold all of the unwritten
     * data. This structure provides an additional buffer to hold the data until
     * it can be written.
     * Not that while the Servlet API only allows one non-blocking write at a
     * time, due to buffering and the possible need to write HTTP headers, this
     * layer may see multiple writes.
     */
    protected final WriteBuffer nonBlockingWriteBuffer = new WriteBuffer(bufferedWriteSize);

    /*
     * Asynchronous operations.
     */
    protected final Semaphore readPending;
    protected volatile OperationState<?> readOperation = null;
    protected final Semaphore writePending;
    protected volatile OperationState<?> writeOperation = null;

    /**
     * The org.apache.coyote.Processor instance currently associated with the
     * wrapper. Only populated when required to maintain wrapper<->Processor
     * mapping between calls to
     * {@link AbstractEndpoint.Handler#process(SocketWrapperBase, SocketEvent)}.
     */
    private final AtomicReference<Object> currentProcessor = new AtomicReference<>();

    public SocketWrapperBase(E socket, AbstractEndpoint<E,?> endpoint) {
        this.socket = socket;
        this.endpoint = endpoint;
        if (endpoint.getUseAsyncIO() || needSemaphores()) {
            readPending = new Semaphore(1);
            writePending = new Semaphore(1);
        } else {
            readPending = null;
            writePending = null;
        }
        connectionId = Long.toHexString(connectionIdGenerator.getAndIncrement());
    }

    public E getSocket() {
        return socket;
    }

    protected void reset(E closedSocket) {
        socket = closedSocket;
    }

    protected AbstractEndpoint<E,?> getEndpoint() {
        return endpoint;
    }

    public Lock getLock() {
        return lock;
    }

    public Object getCurrentProcessor() {
        return currentProcessor.get();
    }

    public void setCurrentProcessor(Object currentProcessor) {
        this.currentProcessor.set(currentProcessor);
    }

    public Object takeCurrentProcessor() {
        return currentProcessor.getAndSet(null);
    }

    /**
     * Transfers processing to a container thread.
     *
     * @param runnable The actions to process on a container thread
     *
     * @throws RejectedExecutionException If the runnable cannot be executed
     */
    public void execute(Runnable runnable) {
        Executor executor = endpoint.getExecutor();
        if (!endpoint.isRunning() || executor == null) {
            throw new RejectedExecutionException();
        }
        executor.execute(runnable);
    }

    public IOException getError() { return error; }
    public void setError(IOException error) {
        // Not perfectly thread-safe but good enough. Just needs to ensure that
        // once this.error is non-null, it can never be null.
        if (this.error != null) {
            return;
        }
        this.error = error;
    }
    public void checkError() throws IOException {
        if (error != null) {
            throw error;
        }
    }

    public String getNegotiatedProtocol() { return negotiatedProtocol; }
    public void setNegotiatedProtocol(String negotiatedProtocol) {
        this.negotiatedProtocol = negotiatedProtocol;
    }

    /**
     * Set the timeout for reading. Values of zero or less will be changed to
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


    public void setKeepAliveLeft(int keepAliveLeft) { this.keepAliveLeft = keepAliveLeft; }
    public int decrementKeepAlive() { return (--keepAliveLeft); }

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

    public SocketBufferHandler getSocketBufferHandler() { return socketBufferHandler; }

    public boolean hasDataToRead() {
        // Return true because it is always safe to make a read attempt
        return true;
    }

    public boolean hasDataToWrite() {
        return !socketBufferHandler.isWriteBufferEmpty() || !nonBlockingWriteBuffer.isEmpty();
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
        return socketBufferHandler.isWriteBufferWritable() && nonBlockingWriteBuffer.isEmpty();
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
    public abstract int read(boolean block, ByteBuffer to) throws IOException;
    public abstract boolean isReadyForRead() throws IOException;
    public abstract void setAppReadBufHandler(ApplicationBufferHandler handler);

    protected int populateReadBuffer(byte[] b, int off, int len) {
        socketBufferHandler.configureReadBufferForRead();
        ByteBuffer readBuffer = socketBufferHandler.getReadBuffer();
        int remaining = readBuffer.remaining();

        // Is there enough data in the read buffer to satisfy this request?
        // Copy what data there is in the read buffer to the byte array
        if (remaining > 0) {
            remaining = Math.min(remaining, len);
            readBuffer.get(b, off, remaining);

            if (log.isDebugEnabled()) {
                log.debug("Socket: [" + this + "], Read from buffer: [" + remaining + "]");
            }
        }
        return remaining;
    }


    protected int populateReadBuffer(ByteBuffer to) {
        // Is there enough data in the read buffer to satisfy this request?
        // Copy what data there is in the read buffer to the byte array
        socketBufferHandler.configureReadBufferForRead();
        int nRead = transfer(socketBufferHandler.getReadBuffer(), to);

        if (log.isDebugEnabled()) {
            log.debug("Socket: [" + this + "], Read from buffer: [" + nRead + "]");
        }
        return nRead;
    }


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
            socketBufferHandler.unReadReadBuffer(returnedInput);
        }
    }


    /**
     * Close the socket wrapper.
     */
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                getEndpoint().getHandler().release(this);
            } catch (Throwable e) {
                ExceptionUtils.handleThrowable(e);
                if (log.isDebugEnabled()) {
                    log.error(sm.getString("endpoint.debug.handlerRelease"), e);
                }
            } finally {
                getEndpoint().countDownConnection();
                doClose();
            }
        }
    }

    /**
     * Perform the actual close. The closed atomic boolean guarantees this will
     * be called only once per wrapper.
     */
    protected abstract void doClose();

    /**
     * @return true if the wrapper has been closed
     */
    public boolean isClosed() {
        return closed.get();
    }


    /**
     * Writes the provided data to the socket write buffer. If the socket write
     * buffer fills during the write, the content of the socket write buffer is
     * written to the network and this method starts to fill the socket write
     * buffer again. Depending on the size of the data to write, there may be
     * multiple writes to the network.
     * <p>
     * Non-blocking writes must return immediately and the byte array holding
     * the data to be written must be immediately available for re-use. It may
     * not be possible to write sufficient data to the network to allow this to
     * happen. In this case data that cannot be written to the network and
     * cannot be held by the socket buffer is stored in the non-blocking write
     * buffer.
     * <p>
     * Note: There is an implementation assumption that, before switching from
     *       non-blocking writes to blocking writes, any data remaining in the
     *       non-blocking write buffer will have been written to the network.
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
        if (len == 0 || buf == null) {
            return;
        }

        /*
         * While the implementations for blocking and non-blocking writes are
         * very similar they have been split into separate methods:
         * - To allow sub-classes to override them individually. NIO2, for
         *   example, overrides the non-blocking write but not the blocking
         *   write.
         * - To enable a marginally more efficient implemented for blocking
         *   writes which do not require the additional checks related to the
         *   use of the non-blocking write buffer
         */
        if (block) {
            writeBlocking(buf, off, len);
        } else {
            writeNonBlocking(buf, off, len);
        }
    }


    /**
     * Writes the provided data to the socket write buffer. If the socket write
     * buffer fills during the write, the content of the socket write buffer is
     * written to the network and this method starts to fill the socket write
     * buffer again. Depending on the size of the data to write, there may be
     * multiple writes to the network.
     * <p>
     * Non-blocking writes must return immediately and the ByteBuffer holding
     * the data to be written must be immediately available for re-use. It may
     * not be possible to write sufficient data to the network to allow this to
     * happen. In this case data that cannot be written to the network and
     * cannot be held by the socket buffer is stored in the non-blocking write
     * buffer.
     * <p>
     * Note: There is an implementation assumption that, before switching from
     *       non-blocking writes to blocking writes, any data remaining in the
     *       non-blocking write buffer will have been written to the network.
     *
     * @param block  <code>true</code> if a blocking write should be used,
     *               otherwise a non-blocking write will be used
     * @param from   The ByteBuffer containing the data to be written
     *
     * @throws IOException If an IO error occurs during the write
     */
    public final void write(boolean block, ByteBuffer from) throws IOException {
        if (from == null || from.remaining() == 0) {
            return;
        }

        /*
         * While the implementations for blocking and non-blocking writes are
         * very similar they have been split into separate methods:
         * - To allow sub-classes to override them individually. NIO2, for
         *   example, overrides the non-blocking write but not the blocking
         *   write.
         * - To enable a marginally more efficient implemented for blocking
         *   writes which do not require the additional checks related to the
         *   use of the non-blocking write buffer
         */
        if (block) {
            writeBlocking(from);
        } else {
            writeNonBlocking(from);
        }
    }


    /**
     * Writes the provided data to the socket write buffer. If the socket write
     * buffer fills during the write, the content of the socket write buffer is
     * written to the network using a blocking write. Once that blocking write
     * is complete, this method starts to fill the socket write buffer again.
     * Depending on the size of the data to write, there may be multiple writes
     * to the network. On completion of this method there will always be space
     * remaining in the socket write buffer.
     *
     * @param buf   The byte array containing the data to be written
     * @param off   The offset within the byte array of the data to be written
     * @param len   The length of the data to be written
     *
     * @throws IOException If an IO error occurs during the write
     */
    protected void writeBlocking(byte[] buf, int off, int len) throws IOException {
        if (len > 0) {
            socketBufferHandler.configureWriteBufferForWrite();
            int thisTime = transfer(buf, off, len, socketBufferHandler.getWriteBuffer());
            len -= thisTime;
            while (len > 0) {
                off += thisTime;
                doWrite(true);
                socketBufferHandler.configureWriteBufferForWrite();
                thisTime = transfer(buf, off, len, socketBufferHandler.getWriteBuffer());
                len -= thisTime;
            }
        }
    }


    /**
     * Writes the provided data to the socket write buffer. If the socket write
     * buffer fills during the write, the content of the socket write buffer is
     * written to the network using a blocking write. Once that blocking write
     * is complete, this method starts to fill the socket write buffer again.
     * Depending on the size of the data to write, there may be multiple writes
     * to the network. On completion of this method there will always be space
     * remaining in the socket write buffer.
     *
     * @param from The ByteBuffer containing the data to be written
     *
     * @throws IOException If an IO error occurs during the write
     */
    protected void writeBlocking(ByteBuffer from) throws IOException {
        if (from.hasRemaining()) {
            socketBufferHandler.configureWriteBufferForWrite();
            transfer(from, socketBufferHandler.getWriteBuffer());
            while (from.hasRemaining()) {
                doWrite(true);
                socketBufferHandler.configureWriteBufferForWrite();
                transfer(from, socketBufferHandler.getWriteBuffer());
            }
        }
    }


    /**
     * Transfers the data to the socket write buffer (writing that data to the
     * socket if the buffer fills up using a non-blocking write) until either
     * all the data has been transferred and space remains in the socket write
     * buffer or a non-blocking write leaves data in the socket write buffer.
     * After an incomplete write, any data remaining to be transferred to the
     * socket write buffer will be copied to the socket write buffer. If the
     * remaining data is too big for the socket write buffer, the socket write
     * buffer will be filled and the additional data written to the non-blocking
     * write buffer.
     *
     * @param buf   The byte array containing the data to be written
     * @param off   The offset within the byte array of the data to be written
     * @param len   The length of the data to be written
     *
     * @throws IOException If an IO error occurs during the write
     */
    protected void writeNonBlocking(byte[] buf, int off, int len) throws IOException {
        if (len > 0 && nonBlockingWriteBuffer.isEmpty()
                && socketBufferHandler.isWriteBufferWritable()) {
            socketBufferHandler.configureWriteBufferForWrite();
            int thisTime = transfer(buf, off, len, socketBufferHandler.getWriteBuffer());
            len -= thisTime;
            while (len > 0) {
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
                len -= thisTime;
            }
        }

        if (len > 0) {
            // Remaining data must be buffered
            nonBlockingWriteBuffer.add(buf, off, len);
        }
    }


    /**
     * Transfers the data to the socket write buffer (writing that data to the
     * socket if the buffer fills up using a non-blocking write) until either
     * all the data has been transferred and space remains in the socket write
     * buffer or a non-blocking write leaves data in the socket write buffer.
     * After an incomplete write, any data remaining to be transferred to the
     * socket write buffer will be copied to the socket write buffer. If the
     * remaining data is too big for the socket write buffer, the socket write
     * buffer will be filled and the additional data written to the non-blocking
     * write buffer.
     *
     * @param from The ByteBuffer containing the data to be written
     *
     * @throws IOException If an IO error occurs during the write
     */
    protected void writeNonBlocking(ByteBuffer from)
            throws IOException {

        if (from.hasRemaining() && nonBlockingWriteBuffer.isEmpty()
                && socketBufferHandler.isWriteBufferWritable()) {
            writeNonBlockingInternal(from);
        }

        if (from.hasRemaining()) {
            // Remaining data must be buffered
            nonBlockingWriteBuffer.add(from);
        }
    }


    /**
     * Separate method so it can be re-used by the socket write buffer to write
     * data to the network
     *
     * @param from The ByteBuffer containing the data to be written
     *
     * @throws IOException If an IO error occurs during the write
     */
    protected void writeNonBlockingInternal(ByteBuffer from) throws IOException {
        socketBufferHandler.configureWriteBufferForWrite();
        transfer(from, socketBufferHandler.getWriteBuffer());
        while (from.hasRemaining()) {
            doWrite(false);
            if (socketBufferHandler.isWriteBufferWritable()) {
                socketBufferHandler.configureWriteBufferForWrite();
                transfer(from, socketBufferHandler.getWriteBuffer());
            } else {
                break;
            }
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
        boolean result = false;
        if (block) {
            // A blocking flush will always empty the buffer.
            flushBlocking();
        } else {
            result = flushNonBlocking();
        }

        return result;
    }


    /**
     * Writes all remaining data from the buffers and blocks until the write is
     * complete.
     *
     * @throws IOException If an IO error occurs during the write
     */
    protected void flushBlocking() throws IOException {
        doWrite(true);

        if (!nonBlockingWriteBuffer.isEmpty()) {
            nonBlockingWriteBuffer.write(this, true);

            if (!socketBufferHandler.isWriteBufferEmpty()) {
                doWrite(true);
            }
        }

    }


    /**
     * Writes as much data as possible from any that remains in the buffers.
     *
     * @return <code>true</code> if data remains to be flushed after this method
     *         completes, otherwise <code>false</code>.
     *
     * @throws IOException If an IO error occurs during the write
     */
    protected abstract boolean flushNonBlocking() throws IOException;


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
    protected void doWrite(boolean block) throws IOException {
        socketBufferHandler.configureWriteBufferForRead();
        doWrite(block, socketBufferHandler.getWriteBuffer());
    }


    /**
     * Write the contents of the ByteBuffer to the socket. For blocking writes
     * either then entire contents of the buffer will be written or an
     * IOException will be thrown. Partial blocking writes will not occur.
     *
     * @param block Should the write be blocking or not?
     * @param from the ByteBuffer containing the data to be written
     *
     * @throws IOException If an I/O error such as a timeout occurs during the
     *                     write
     */
    protected abstract void doWrite(boolean block, ByteBuffer from) throws IOException;


    public void processSocket(SocketEvent socketStatus, boolean dispatch) {
        endpoint.processSocket(this, socketStatus, dispatch);
    }


    public abstract void registerReadInterest();

    public abstract void registerWriteInterest();

    public abstract SendfileDataBase createSendfileData(String filename, long pos, long length);

    /**
     * Starts the sendfile process. It is expected that if the sendfile process
     * does not complete during this call and does not report an error, that the
     * caller <b>will not</b> add the socket to the poller (or equivalent). That
     * is the responsibility of this method.
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
     *
     * @throws IOException If authentication is required then there will be I/O
     *                     with the client and this exception will be thrown if
     *                     that goes wrong
     */
    public abstract void doClientAuth(SSLSupport sslSupport) throws IOException;

    /**
     * Obtain an SSLSupport instance for this socket.
     *
     * @return An SSLSupport instance for this socket.
     */
    public abstract SSLSupport getSslSupport();


    // ------------------------------------------------------- NIO 2 style APIs


    public enum BlockingMode {
        /**
         * The operation will not block. If there are pending operations,
         * the operation will throw a pending exception.
         */
        CLASSIC,
        /**
         * The operation will not block. If there are pending operations,
         * the operation will return CompletionState.NOT_DONE.
         */
        NON_BLOCK,
        /**
         * The operation will block until pending operations are completed, but
         * will not block after performing it.
         */
        SEMI_BLOCK,
        /**
         * The operation will block until completed.
         */
        BLOCK
    }

    public enum CompletionState {
        /**
         * Operation is still pending.
         */
        PENDING,
        /**
         * Operation was pending and non blocking.
         */
        NOT_DONE,
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
         * Determine what call, if any, should be made to the completion
         * handler.
         *
         * @param state of the operation (done or done in-line since the
         *        IO call is done)
         * @param buffers ByteBuffer[] that has been passed to the
         *        original IO call
         * @param offset that has been passed to the original IO call
         * @param length that has been passed to the original IO call
         *
         * @return The call, if any, to make to the completion handler
         */
        public CompletionHandlerCall callHandler(CompletionState state, ByteBuffer[] buffers,
                int offset, int length);
    }

    /**
     * This utility CompletionCheck will cause the write to fully write
     * all remaining data. If the operation completes inline, the
     * completion handler will not be called.
     */
    public static final CompletionCheck COMPLETE_WRITE = new CompletionCheck() {
        @Override
        public CompletionHandlerCall callHandler(CompletionState state, ByteBuffer[] buffers,
                int offset, int length) {
            for (int i = 0; i < length; i++) {
                if (buffers[offset + i].hasRemaining()) {
                    return CompletionHandlerCall.CONTINUE;
                }
            }
            return (state == CompletionState.DONE) ? CompletionHandlerCall.DONE
                    : CompletionHandlerCall.NONE;
        }
    };

    /**
     * This utility CompletionCheck will cause the write to fully write
     * all remaining data. The completion handler will then be called.
     */
    public static final CompletionCheck COMPLETE_WRITE_WITH_COMPLETION = new CompletionCheck() {
        @Override
        public CompletionHandlerCall callHandler(CompletionState state, ByteBuffer[] buffers,
                int offset, int length) {
            for (int i = 0; i < length; i++) {
                if (buffers[offset + i].hasRemaining()) {
                    return CompletionHandlerCall.CONTINUE;
                }
            }
            return CompletionHandlerCall.DONE;
        }
    };

    /**
     * This utility CompletionCheck will cause the completion handler
     * to be called once some data has been read. If the operation
     * completes inline, the completion handler will not be called.
     */
    public static final CompletionCheck READ_DATA = new CompletionCheck() {
        @Override
        public CompletionHandlerCall callHandler(CompletionState state, ByteBuffer[] buffers,
                int offset, int length) {
            return (state == CompletionState.DONE) ? CompletionHandlerCall.DONE
                    : CompletionHandlerCall.NONE;
        }
    };

    /**
     * This utility CompletionCheck will cause the completion handler
     * to be called once the given buffers are full. The completion
     * handler will then be called.
     */
    public static final CompletionCheck COMPLETE_READ_WITH_COMPLETION = COMPLETE_WRITE_WITH_COMPLETION;

    /**
     * This utility CompletionCheck will cause the completion handler
     * to be called once the given buffers are full. If the operation
     * completes inline, the completion handler will not be called.
     */
    public static final CompletionCheck COMPLETE_READ = COMPLETE_WRITE;

    /**
     * Internal state tracker for vectored operations.
     */
    protected abstract class OperationState<A> implements Runnable {
        protected final boolean read;
        protected final ByteBuffer[] buffers;
        protected final int offset;
        protected final int length;
        protected final A attachment;
        protected final long timeout;
        protected final TimeUnit unit;
        protected final BlockingMode block;
        protected final CompletionCheck check;
        protected final CompletionHandler<Long, ? super A> handler;
        protected final Semaphore semaphore;
        protected final VectoredIOCompletionHandler<A> completion;
        protected final AtomicBoolean callHandler;
        protected OperationState(boolean read, ByteBuffer[] buffers, int offset, int length,
                BlockingMode block, long timeout, TimeUnit unit, A attachment,
                CompletionCheck check, CompletionHandler<Long, ? super A> handler,
                Semaphore semaphore, VectoredIOCompletionHandler<A> completion) {
            this.read = read;
            this.buffers = buffers;
            this.offset = offset;
            this.length = length;
            this.block = block;
            this.timeout = timeout;
            this.unit = unit;
            this.attachment = attachment;
            this.check = check;
            this.handler = handler;
            this.semaphore = semaphore;
            this.completion = completion;
            callHandler = (handler != null) ? new AtomicBoolean(true) : null;
        }
        protected volatile long nBytes = 0;
        protected volatile CompletionState state = CompletionState.PENDING;
        protected boolean completionDone = true;

        /**
         * @return true if the operation is still inline, false if the operation
         *   is running on a thread that is not the original caller
         */
        protected abstract boolean isInline();

        protected boolean hasOutboundRemaining() {
            // NIO2 and APR never have remaining outbound data when the
            // completion handler is called. NIO needs to override this.
            return false;
        }


        /**
         * Process the operation using the connector executor.
         * @return true if the operation was accepted, false if the executor
         *     rejected execution
         */
        protected boolean process() {
            try {
                getEndpoint().getExecutor().execute(this);
                return true;
            } catch (RejectedExecutionException ree) {
                log.warn(sm.getString("endpoint.executor.fail", SocketWrapperBase.this) , ree);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                // This means we got an OOM or similar creating a thread, or that
                // the pool and its queue are full
                log.error(sm.getString("endpoint.process.fail"), t);
            }
            return false;
        }

        /**
         * Start the operation, this will typically call run.
         */
        protected void start() {
            run();
        }

        /**
         * End the operation.
         */
        protected void end() {
        }

    }

    /**
     * Completion handler for vectored operations. This will check the completion of the operation,
     * then either continue or call the user provided completion handler.
     */
    protected class VectoredIOCompletionHandler<A> implements CompletionHandler<Long, OperationState<A>> {
        @Override
        public void completed(Long nBytes, OperationState<A> state) {
            if (nBytes.longValue() < 0) {
                failed(new EOFException(), state);
            } else {
                state.nBytes += nBytes.longValue();
                CompletionState currentState = state.isInline() ? CompletionState.INLINE : CompletionState.DONE;
                boolean complete = true;
                boolean completion = true;
                if (state.check != null) {
                    CompletionHandlerCall call = state.check.callHandler(currentState, state.buffers, state.offset, state.length);
                    if (call == CompletionHandlerCall.CONTINUE || (!state.read && state.hasOutboundRemaining())) {
                        complete = false;
                    } else if (call == CompletionHandlerCall.NONE) {
                        completion = false;
                    }
                }
                if (complete) {
                    boolean notify = false;
                    if (state.read) {
                        readOperation = null;
                    } else {
                        writeOperation = null;
                    }
                    // Semaphore must be released after [read|write]Operation is cleared
                    // to ensure that the next thread to hold the semaphore hasn't
                    // written a new value to [read|write]Operation by the time it is
                    // cleared.
                    state.semaphore.release();
                    if (state.block == BlockingMode.BLOCK && currentState != CompletionState.INLINE) {
                        notify = true;
                    } else {
                        state.state = currentState;
                    }
                    state.end();
                    if (completion && state.handler != null && state.callHandler.compareAndSet(true, false)) {
                        state.handler.completed(Long.valueOf(state.nBytes), state.attachment);
                    }
                    synchronized (state) {
                        state.completionDone = true;
                        if (notify) {
                            state.state = currentState;
                            state.notify();
                        }
                    }
                } else {
                    synchronized (state) {
                        state.completionDone = true;
                    }
                    state.run();
                }
            }
        }
        @Override
        public void failed(Throwable exc, OperationState<A> state) {
            IOException ioe = null;
            if (exc instanceof InterruptedByTimeoutException) {
                ioe = new SocketTimeoutException();
                exc = ioe;
            } else if (exc instanceof IOException) {
                ioe = (IOException) exc;
            }
            setError(ioe);
            boolean notify = false;
            if (state.read) {
                readOperation = null;
            } else {
                writeOperation = null;
            }
            // Semaphore must be released after [read|write]Operation is cleared
            // to ensure that the next thread to hold the semaphore hasn't
            // written a new value to [read|write]Operation by the time it is
            // cleared.
            state.semaphore.release();
            if (state.block == BlockingMode.BLOCK) {
                notify = true;
            } else {
                state.state = state.isInline() ? CompletionState.ERROR : CompletionState.DONE;
            }
            state.end();
            if (state.handler != null && state.callHandler.compareAndSet(true, false)) {
                state.handler.failed(exc, state.attachment);
            }
            synchronized (state) {
                state.completionDone = true;
                if (notify) {
                    state.state = state.isInline() ? CompletionState.ERROR : CompletionState.DONE;
                    state.notify();
                }
            }
        }
    }

    /**
     * Allows using NIO2 style read/write.
     *
     * @return {@code true} if the connector has the capability enabled
     */
    public boolean hasAsyncIO() {
        // The semaphores are only created if async IO is enabled
        return (readPending != null);
    }

    /**
     * Allows indicating if the connector needs semaphores.
     *
     * @return This default implementation always returns {@code false}
     */
    public boolean needSemaphores() {
        return false;
    }

    /**
     * Allows indicating if the connector supports per operation timeout.
     *
     * @return This default implementation always returns {@code false}
     */
    public boolean hasPerOperationTimeout() {
        return false;
    }

    /**
     * Allows checking if an asynchronous read operation is currently pending.
     * @return <code>true</code> if the endpoint supports asynchronous IO and
     *  a read operation is being processed asynchronously
     */
    public boolean isReadPending() {
        return false;
    }

    /**
     * Allows checking if an asynchronous write operation is currently pending.
     * @return <code>true</code> if the endpoint supports asynchronous IO and
     *  a write operation is being processed asynchronously
     */
    public boolean isWritePending() {
        return false;
    }

    /**
     * Scatter read. The completion handler will be called once some
     * data has been read or an error occurred. The default NIO2
     * behavior is used: the completion handler will be called as soon
     * as some data has been read, even if the read has completed inline.
     *
     * @param timeout timeout duration for the read
     * @param unit units for the timeout duration
     * @param attachment an object to attach to the I/O operation that will be
     *        used when calling the completion handler
     * @param handler to call when the IO is complete
     * @param dsts buffers
     * @param <A> The attachment type
     * @return the completion state (done, done inline, or still pending)
     */
    public final <A> CompletionState read(long timeout, TimeUnit unit, A attachment,
            CompletionHandler<Long, ? super A> handler, ByteBuffer... dsts) {
        if (dsts == null) {
            throw new IllegalArgumentException();
        }
        return read(dsts, 0, dsts.length, BlockingMode.CLASSIC, timeout, unit, attachment, null, handler);
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
     * @param block is the blocking mode that will be used for this operation
     * @param timeout timeout duration for the read
     * @param unit units for the timeout duration
     * @param attachment an object to attach to the I/O operation that will be
     *        used when calling the completion handler
     * @param check for the IO operation completion
     * @param handler to call when the IO is complete
     * @param dsts buffers
     * @param <A> The attachment type
     * @return the completion state (done, done inline, or still pending)
     */
    public final <A> CompletionState read(BlockingMode block, long timeout,
            TimeUnit unit, A attachment, CompletionCheck check,
            CompletionHandler<Long, ? super A> handler, ByteBuffer... dsts) {
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
     * @param block is the blocking mode that will be used for this operation
     * @param timeout timeout duration for the read
     * @param unit units for the timeout duration
     * @param attachment an object to attach to the I/O operation that will be
     *        used when calling the completion handler
     * @param check for the IO operation completion
     * @param handler to call when the IO is complete
     * @param <A> The attachment type
     * @return the completion state (done, done inline, or still pending)
     */
    public final <A> CompletionState read(ByteBuffer[] dsts, int offset, int length,
            BlockingMode block, long timeout, TimeUnit unit, A attachment,
            CompletionCheck check, CompletionHandler<Long, ? super A> handler) {
        return vectoredOperation(true, dsts, offset, length, block, timeout, unit, attachment, check, handler);
    }

    /**
     * Gather write. The completion handler will be called once some
     * data has been written or an error occurred. The default NIO2
     * behavior is used: the completion handler will be called, even
     * if the write is incomplete and data remains in the buffers, or
     * if the write completed inline.
     *
     * @param timeout timeout duration for the write
     * @param unit units for the timeout duration
     * @param attachment an object to attach to the I/O operation that will be
     *        used when calling the completion handler
     * @param handler to call when the IO is complete
     * @param srcs buffers
     * @param <A> The attachment type
     * @return the completion state (done, done inline, or still pending)
     */
    public final <A> CompletionState write(long timeout, TimeUnit unit, A attachment,
            CompletionHandler<Long, ? super A> handler, ByteBuffer... srcs) {
        if (srcs == null) {
            throw new IllegalArgumentException();
        }
        return write(srcs, 0, srcs.length, BlockingMode.CLASSIC, timeout, unit, attachment, null, handler);
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
     * @param block is the blocking mode that will be used for this operation
     * @param timeout timeout duration for the write
     * @param unit units for the timeout duration
     * @param attachment an object to attach to the I/O operation that will be
     *        used when calling the completion handler
     * @param check for the IO operation completion
     * @param handler to call when the IO is complete
     * @param srcs buffers
     * @param <A> The attachment type
     * @return the completion state (done, done inline, or still pending)
     */
    public final <A> CompletionState write(BlockingMode block, long timeout,
            TimeUnit unit, A attachment, CompletionCheck check,
            CompletionHandler<Long, ? super A> handler, ByteBuffer... srcs) {
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
     * @param block is the blocking mode that will be used for this operation
     * @param timeout timeout duration for the write
     * @param unit units for the timeout duration
     * @param attachment an object to attach to the I/O operation that will be
     *        used when calling the completion handler
     * @param check for the IO operation completion
     * @param handler to call when the IO is complete
     * @param <A> The attachment type
     * @return the completion state (done, done inline, or still pending)
     */
    public final <A> CompletionState write(ByteBuffer[] srcs, int offset, int length,
            BlockingMode block, long timeout, TimeUnit unit, A attachment,
            CompletionCheck check, CompletionHandler<Long, ? super A> handler) {
        return vectoredOperation(false, srcs, offset, length, block, timeout, unit, attachment, check, handler);
    }


    /**
     * Vectored operation. The completion handler will be called once
     * the operation is complete or an error occurred. If a CompletionCheck
     * object has been provided, the completion handler will only be
     * called if the callHandler method returned true. If no
     * CompletionCheck object has been provided, the default NIO2
     * behavior is used: the completion handler will be called, even
     * if the operation is incomplete, or if the operation completed inline.
     *
     * @param read true if the operation is a read, false if it is a write
     * @param buffers buffers
     * @param offset in the buffer array
     * @param length in the buffer array
     * @param block is the blocking mode that will be used for this operation
     * @param timeout timeout duration for the write
     * @param unit units for the timeout duration
     * @param attachment an object to attach to the I/O operation that will be
     *        used when calling the completion handler
     * @param check for the IO operation completion
     * @param handler to call when the IO is complete
     * @param <A> The attachment type
     * @return the completion state (done, done inline, or still pending)
     */
    protected final <A> CompletionState vectoredOperation(boolean read,
            ByteBuffer[] buffers, int offset, int length,
            BlockingMode block, long timeout, TimeUnit unit, A attachment,
            CompletionCheck check, CompletionHandler<Long, ? super A> handler) {
        IOException ioe = getError();
        if (ioe != null) {
            handler.failed(ioe, attachment);
            return CompletionState.ERROR;
        }
        if (timeout == -1) {
            timeout = AbstractEndpoint.toTimeout(read ? getReadTimeout() : getWriteTimeout());
            unit = TimeUnit.MILLISECONDS;
        } else if (!hasPerOperationTimeout() && (unit.toMillis(timeout) != (read ? getReadTimeout() : getWriteTimeout()))) {
            if (read) {
                setReadTimeout(unit.toMillis(timeout));
            } else {
                setWriteTimeout(unit.toMillis(timeout));
            }
        }
        if (block == BlockingMode.BLOCK || block == BlockingMode.SEMI_BLOCK) {
            try {
                if (read ? !readPending.tryAcquire(timeout, unit) : !writePending.tryAcquire(timeout, unit)) {
                    handler.failed(new SocketTimeoutException(), attachment);
                    return CompletionState.ERROR;
                }
            } catch (InterruptedException e) {
                handler.failed(e, attachment);
                return CompletionState.ERROR;
            }
        } else {
            if (read ? !readPending.tryAcquire() : !writePending.tryAcquire()) {
                if (block == BlockingMode.NON_BLOCK) {
                    return CompletionState.NOT_DONE;
                } else {
                    handler.failed(read ? new ReadPendingException() : new WritePendingException(), attachment);
                    return CompletionState.ERROR;
                }
            }
        }
        VectoredIOCompletionHandler<A> completion = new VectoredIOCompletionHandler<>();
        OperationState<A> state = newOperationState(read, buffers, offset, length, block, timeout, unit,
                attachment, check, handler, read ? readPending : writePending, completion);
        if (read) {
            readOperation = state;
        } else {
            writeOperation = state;
        }
        state.start();
        if (block == BlockingMode.BLOCK) {
            synchronized (state) {
                if (state.state == CompletionState.PENDING) {
                    try {
                        state.wait(unit.toMillis(timeout));
                        if (state.state == CompletionState.PENDING) {
                            if (handler != null && state.callHandler.compareAndSet(true, false)) {
                                handler.failed(new SocketTimeoutException(getTimeoutMsg(read)), attachment);
                            }
                            return CompletionState.ERROR;
                        }
                    } catch (InterruptedException e) {
                        if (handler != null && state.callHandler.compareAndSet(true, false)) {
                            handler.failed(new SocketTimeoutException(getTimeoutMsg(read)), attachment);
                        }
                        return CompletionState.ERROR;
                    }
                }
            }
        }
        return state.state;
    }


    private String getTimeoutMsg(boolean read) {
        if (read) {
            return sm.getString("socketWrapper.readTimeout");
        } else {
            return sm.getString("socketWrapper.writeTimeout");
        }
    }


    protected abstract <A> OperationState<A> newOperationState(boolean read,
            ByteBuffer[] buffers, int offset, int length,
            BlockingMode block, long timeout, TimeUnit unit, A attachment,
            CompletionCheck check, CompletionHandler<Long, ? super A> handler,
            Semaphore semaphore, VectoredIOCompletionHandler<A> completion);


    // --------------------------------------------------------- Utility methods

    protected static int transfer(byte[] from, int offset, int length, ByteBuffer to) {
        int max = Math.min(length, to.remaining());
        if (max > 0) {
            to.put(from, offset, max);
        }
        return max;
    }

    protected static int transfer(ByteBuffer from, ByteBuffer to) {
        int max = Math.min(from.remaining(), to.remaining());
        if (max > 0) {
            int fromLimit = from.limit();
            from.limit(from.position() + max);
            to.put(from);
            from.limit(fromLimit);
        }
        return max;
    }

    protected static boolean buffersArrayHasRemaining(ByteBuffer[] buffers, int offset, int length) {
        for (int pos = offset; pos < offset + length; pos++) {
            if (buffers[pos].hasRemaining()) {
                return true;
            }
        }
        return false;
    }


    // -------------------------------------------------------------- ID methods

    public ServletConnection getServletConnection(String protocol, String protocolConnectionId) {
        if (servletConnection == null) {
            servletConnection = new ServletConnectionImpl(
                    connectionId, protocol, protocolConnectionId, endpoint.isSSLEnabled());
        }
        return servletConnection;
    }
}
