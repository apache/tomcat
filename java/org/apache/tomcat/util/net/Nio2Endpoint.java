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
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileChannel;
import java.nio.channels.NetworkChannel;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.collections.SynchronizedStack;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.Acceptor.AcceptorState;
import org.apache.tomcat.util.net.jsse.JSSESupport;

/**
 * NIO2 endpoint.
 */
public class Nio2Endpoint extends AbstractJsseEndpoint<Nio2Channel,AsynchronousSocketChannel> {


    // -------------------------------------------------------------- Constants


    private static final Log log = LogFactory.getLog(Nio2Endpoint.class);


    // ----------------------------------------------------------------- Fields

    /**
     * Server socket "pointer".
     */
    private volatile AsynchronousServerSocketChannel serverSock = null;

    /**
     * Allows detecting if a completion handler completes inline.
     */
    private static ThreadLocal<Boolean> inlineCompletion = new ThreadLocal<>();

    /**
     * Thread group associated with the server socket.
     */
    private AsynchronousChannelGroup threadGroup = null;

    private volatile boolean allClosed;

    /**
     * Bytebuffer cache, each channel holds a set of buffers (two, except for SSL holds four)
     */
    private SynchronizedStack<Nio2Channel> nioChannels;

    // --------------------------------------------------------- Public Methods


    /**
     * Number of keep-alive sockets.
     *
     * @return Always returns -1.
     */
    public int getKeepAliveCount() {
        // For this connector, only the overall connection count is relevant
        return -1;
    }


    // ----------------------------------------------- Public Lifecycle Methods


    /**
     * Initialize the endpoint.
     */
    @Override
    public void bind() throws Exception {

        // Create worker collection
        if (getExecutor() == null) {
            createExecutor();
        }
        if (getExecutor() instanceof ExecutorService) {
            threadGroup = AsynchronousChannelGroup.withThreadPool((ExecutorService) getExecutor());
        }
        // AsynchronousChannelGroup needs exclusive access to its executor service
        if (!internalExecutor) {
            log.warn(sm.getString("endpoint.nio2.exclusiveExecutor"));
        }

        serverSock = AsynchronousServerSocketChannel.open(threadGroup);
        socketProperties.setProperties(serverSock);
        InetSocketAddress addr = new InetSocketAddress(getAddress(), getPortWithOffset());
        serverSock.bind(addr, getAcceptCount());

        // Initialize SSL if needed
        initialiseSsl();
    }


    /**
     * Start the NIO2 endpoint, creating acceptor.
     */
    @Override
    public void startInternal() throws Exception {

        if (!running) {
            allClosed = false;
            running = true;
            paused = false;

            if (socketProperties.getProcessorCache() != 0) {
                processorCache = new SynchronizedStack<>(SynchronizedStack.DEFAULT_SIZE,
                        socketProperties.getProcessorCache());
            }
            int actualBufferPool =
                    socketProperties.getActualBufferPool(isSSLEnabled() ? getSniParseLimit() * 2 : 0);
            if (actualBufferPool != 0) {
                nioChannels = new SynchronizedStack<>(SynchronizedStack.DEFAULT_SIZE,
                        actualBufferPool);
            }
            // Create worker collection
            if (getExecutor() == null) {
                createExecutor();
            }

            initializeConnectionLatch();
            startAcceptorThread();
        }
    }

    @Override
    protected void startAcceptorThread() {
        // Instead of starting a real acceptor thread, this will instead call
        // an asynchronous accept operation
        if (acceptor == null) {
            acceptor = new Nio2Acceptor(this);
            acceptor.setThreadName(getName() + "-Acceptor");
        }
        acceptor.state = AcceptorState.RUNNING;
        getExecutor().execute(acceptor);
    }

    @Override
    public void resume() {
        super.resume();
        if (isRunning()) {
            acceptor.state = AcceptorState.RUNNING;
            getExecutor().execute(acceptor);
        }
    }

    /**
     * Stop the endpoint. This will cause all processing threads to stop.
     */
    @Override
    public void stopInternal() {
        if (!paused) {
            pause();
        }
        if (running) {
            running = false;
            acceptor.stop(10);
            // Use the executor to avoid binding the main thread if something bad
            // occurs and unbind will also wait for a bit for it to complete
            getExecutor().execute(() -> {
                // Then close all active connections if any remain
                try {
                    for (SocketWrapperBase<Nio2Channel> wrapper : getConnections()) {
                        wrapper.close();
                    }
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                } finally {
                    allClosed = true;
                }
            });
            if (nioChannels != null) {
                nioChannels.clear();
                nioChannels = null;
            }
            if (processorCache != null) {
                processorCache.clear();
                processorCache = null;
            }
        }
    }


    /**
     * Deallocate NIO memory pools, and close server socket.
     */
    @Override
    public void unbind() throws Exception {
        if (running) {
            stop();
        }
        doCloseServerSocket();
        destroySsl();
        super.unbind();
        // Unlike other connectors, the thread pool is tied to the server socket
        shutdownExecutor();
        if (getHandler() != null) {
            getHandler().recycle();
        }
    }


    @Override
    protected void doCloseServerSocket() throws IOException {
        // Close server socket
        if (serverSock != null) {
            serverSock.close();
            serverSock = null;
        }
    }


    @Override
    public void shutdownExecutor() {
        if (threadGroup != null && internalExecutor) {
            try {
                long timeout = getExecutorTerminationTimeoutMillis();
                while (timeout > 0 && !allClosed) {
                    timeout -= 100;
                    Thread.sleep(100);
                }
                threadGroup.shutdownNow();
                if (timeout > 0) {
                    threadGroup.awaitTermination(timeout, TimeUnit.MILLISECONDS);
                }
            } catch (IOException e) {
                getLog().warn(sm.getString("endpoint.warn.executorShutdown", getName()), e);
            } catch (InterruptedException e) {
                // Ignore
            }
            if (!threadGroup.isTerminated()) {
                getLog().warn(sm.getString("endpoint.warn.executorShutdown", getName()));
            }
            threadGroup = null;
        }
        // Mostly to cleanup references
        super.shutdownExecutor();
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * Process the specified connection.
     * @param socket The socket channel
     * @return <code>true</code> if the socket was correctly configured
     *  and processing may continue, <code>false</code> if the socket needs to be
     *  close immediately
     */
    @Override
    protected boolean setSocketOptions(AsynchronousSocketChannel socket) {
        Nio2SocketWrapper socketWrapper = null;
        try {
            // Allocate channel and wrapper
            Nio2Channel channel = null;
            if (nioChannels != null) {
                channel = nioChannels.pop();
            }
            if (channel == null) {
                SocketBufferHandler bufhandler = new SocketBufferHandler(
                        socketProperties.getAppReadBufSize(),
                        socketProperties.getAppWriteBufSize(),
                        socketProperties.getDirectBuffer());
                if (isSSLEnabled()) {
                    channel = new SecureNio2Channel(bufhandler, this);
                } else {
                    channel = new Nio2Channel(bufhandler);
                }
            }
            Nio2SocketWrapper newWrapper = new Nio2SocketWrapper(channel, this);
            channel.reset(socket, newWrapper);
            connections.put(socket, newWrapper);
            socketWrapper = newWrapper;

            // Set socket properties
            socketProperties.setProperties(socket);

            socketWrapper.setReadTimeout(getConnectionTimeout());
            socketWrapper.setWriteTimeout(getConnectionTimeout());
            socketWrapper.setKeepAliveLeft(Nio2Endpoint.this.getMaxKeepAliveRequests());
            // Continue processing on the same thread as the acceptor is async
            return processSocket(socketWrapper, SocketEvent.OPEN_READ, false);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("endpoint.socketOptionsError"), t);
            if (socketWrapper == null) {
                destroySocket(socket);
            }
        }
        // Tell to close the socket if needed
        return false;
    }


    @Override
    protected void destroySocket(AsynchronousSocketChannel socket) {
        countDownConnection();
        try {
            socket.close();
        } catch (IOException ioe) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("endpoint.err.close"), ioe);
            }
        }
    }


    protected SynchronizedStack<Nio2Channel> getNioChannels() {
        return nioChannels;
    }


    @Override
    protected NetworkChannel getServerSocket() {
        return serverSock;
    }


    @Override
    protected AsynchronousSocketChannel serverSocketAccept() throws Exception {
        return serverSock.accept().get();
    }


    @Override
    protected Log getLog() {
        return log;
    }


    @Override
    protected SocketProcessorBase<Nio2Channel> createSocketProcessor(
            SocketWrapperBase<Nio2Channel> socketWrapper, SocketEvent event) {
        return new SocketProcessor(socketWrapper, event);
    }


    protected class Nio2Acceptor extends Acceptor<AsynchronousSocketChannel>
        implements CompletionHandler<AsynchronousSocketChannel, Void> {

        protected int errorDelay = 0;

        public Nio2Acceptor(AbstractEndpoint<?, AsynchronousSocketChannel> endpoint) {
            super(endpoint);
        }

        @Override
        public void run() {
            // The initial accept will be called in a separate utility thread
            if (!isPaused()) {
                //if we have reached max connections, wait
                try {
                    countUpOrAwaitConnection();
                } catch (InterruptedException e) {
                    // Ignore
                }
                if (!isPaused()) {
                    // Note: as a special behavior, the completion handler for accept is
                    // always called in a separate thread.
                    serverSock.accept(null, this);
                } else {
                    state = AcceptorState.PAUSED;
                }
            } else {
                state = AcceptorState.PAUSED;
            }
        }

        /**
         * Signals the Acceptor to stop.
         *
         * @param waitSeconds Ignored for NIO2.
         *
         */
        @Override
        public void stop(int waitSeconds) {
            acceptor.state = AcceptorState.ENDED;
        }

        @Override
        public void completed(AsynchronousSocketChannel socket,
                Void attachment) {
            // Successful accept, reset the error delay
            errorDelay = 0;
            // Continue processing the socket on the current thread
            // Configure the socket
            if (isRunning() && !isPaused()) {
                if (getMaxConnections() == -1) {
                    serverSock.accept(null, this);
                } else if (getConnectionCount() < getMaxConnections()) {
                    try {
                        // This will not block
                        countUpOrAwaitConnection();
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                    serverSock.accept(null, this);
                } else {
                    // Accept again on a new thread since countUpOrAwaitConnection may block
                    getExecutor().execute(this);
                }
                if (!setSocketOptions(socket)) {
                    closeSocket(socket);
                }
            } else {
                if (isRunning()) {
                    state = AcceptorState.PAUSED;
                }
                destroySocket(socket);
            }
        }

        @Override
        public void failed(Throwable t, Void attachment) {
            if (isRunning()) {
                if (!isPaused()) {
                    if (getMaxConnections() == -1) {
                        serverSock.accept(null, this);
                    } else {
                        // Accept again on a new thread since countUpOrAwaitConnection may block
                        getExecutor().execute(this);
                    }
                } else {
                    state = AcceptorState.PAUSED;
                }
                // We didn't get a socket
                countDownConnection();
                // Introduce delay if necessary
                errorDelay = handleExceptionWithDelay(errorDelay);
                ExceptionUtils.handleThrowable(t);
                log.error(sm.getString("endpoint.accept.fail"), t);
            } else {
                // We didn't get a socket
                countDownConnection();
            }
        }

    }

    public static class Nio2SocketWrapper extends SocketWrapperBase<Nio2Channel> {

        private final SynchronizedStack<Nio2Channel> nioChannels;

        private SendfileData sendfileData = null;

        private final CompletionHandler<Integer, ByteBuffer> readCompletionHandler;
        private boolean readInterest = false; // Guarded by readCompletionHandler
        private boolean readNotify = false;

        private final CompletionHandler<Integer, ByteBuffer> writeCompletionHandler;
        private final CompletionHandler<Long, ByteBuffer[]> gatheringWriteCompletionHandler;
        private boolean writeInterest = false; // Guarded by writeCompletionHandler
        private boolean writeNotify = false;

        private CompletionHandler<Integer, SendfileData> sendfileHandler
            = new CompletionHandler<Integer, SendfileData>() {

            @Override
            public void completed(Integer nWrite, SendfileData attachment) {
                if (nWrite.intValue() < 0) {
                    failed(new EOFException(), attachment);
                    return;
                }
                attachment.pos += nWrite.intValue();
                ByteBuffer buffer = getSocket().getBufHandler().getWriteBuffer();
                if (!buffer.hasRemaining()) {
                    if (attachment.length <= 0) {
                        // All data has now been written
                        setSendfileData(null);
                        try {
                            attachment.fchannel.close();
                        } catch (IOException e) {
                            // Ignore
                        }
                        if (isInline()) {
                            attachment.doneInline = true;
                        } else {
                            switch (attachment.keepAliveState) {
                            case NONE: {
                                getEndpoint().processSocket(Nio2SocketWrapper.this,
                                        SocketEvent.DISCONNECT, false);
                                break;
                            }
                            case PIPELINED: {
                                if (!getEndpoint().processSocket(Nio2SocketWrapper.this, SocketEvent.OPEN_READ, true)) {
                                    close();
                                }
                                break;
                            }
                            case OPEN: {
                                registerReadInterest();
                                break;
                            }
                            }
                        }
                        return;
                    } else {
                        getSocket().getBufHandler().configureWriteBufferForWrite();
                        int nRead = -1;
                        try {
                            nRead = attachment.fchannel.read(buffer);
                        } catch (IOException e) {
                            failed(e, attachment);
                            return;
                        }
                        if (nRead > 0) {
                            getSocket().getBufHandler().configureWriteBufferForRead();
                            if (attachment.length < buffer.remaining()) {
                                buffer.limit(buffer.limit() - buffer.remaining() + (int) attachment.length);
                            }
                            attachment.length -= nRead;
                        } else {
                            failed(new EOFException(), attachment);
                            return;
                        }
                    }
                }
                getSocket().write(buffer, toTimeout(getWriteTimeout()), TimeUnit.MILLISECONDS, attachment, this);
            }

            @Override
            public void failed(Throwable exc, SendfileData attachment) {
                try {
                    attachment.fchannel.close();
                } catch (IOException e) {
                    // Ignore
                }
                if (!isInline()) {
                    getEndpoint().processSocket(Nio2SocketWrapper.this, SocketEvent.ERROR, false);
                } else {
                    attachment.doneInline = true;
                    attachment.error = true;
                }
            }
        };

        public Nio2SocketWrapper(Nio2Channel channel, final Nio2Endpoint endpoint) {
            super(channel, endpoint);
            nioChannels = endpoint.getNioChannels();
            socketBufferHandler = channel.getBufHandler();

            this.readCompletionHandler = new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer nBytes, ByteBuffer attachment) {
                    if (log.isDebugEnabled()) {
                        log.debug("Socket: [" + Nio2SocketWrapper.this + "], Interest: [" + readInterest + "]");
                    }
                    readNotify = false;
                    synchronized (readCompletionHandler) {
                        if (nBytes.intValue() < 0) {
                            failed(new EOFException(), attachment);
                        } else {
                            if (readInterest && !Nio2Endpoint.isInline()) {
                                readNotify = true;
                            } else {
                                // Release here since there will be no
                                // notify/dispatch to do the release.
                                readPending.release();
                            }
                            readInterest = false;
                        }
                    }
                    if (readNotify) {
                        getEndpoint().processSocket(Nio2SocketWrapper.this, SocketEvent.OPEN_READ, false);
                    }
                }
                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    IOException ioe;
                    if (exc instanceof IOException) {
                        ioe = (IOException) exc;
                    } else {
                        ioe = new IOException(exc);
                    }
                    setError(ioe);
                    if (exc instanceof AsynchronousCloseException) {
                        // Release here since there will be no
                        // notify/dispatch to do the release.
                        readPending.release();
                        // If already closed, don't call onError and close again
                        getEndpoint().processSocket(Nio2SocketWrapper.this, SocketEvent.STOP, false);
                    } else if (!getEndpoint().processSocket(Nio2SocketWrapper.this, SocketEvent.ERROR, true)) {
                        close();
                    }
                }
            };

            this.writeCompletionHandler = new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer nBytes, ByteBuffer attachment) {
                    writeNotify = false;
                    boolean notify = false;
                    synchronized (writeCompletionHandler) {
                        if (nBytes.intValue() < 0) {
                            failed(new EOFException(sm.getString("iob.failedwrite")), attachment);
                        } else if (!nonBlockingWriteBuffer.isEmpty()) {
                            // Continue writing data using a gathering write
                            ByteBuffer[] array = nonBlockingWriteBuffer.toArray(attachment);
                            getSocket().write(array, 0, array.length,
                                    toTimeout(getWriteTimeout()), TimeUnit.MILLISECONDS,
                                    array, gatheringWriteCompletionHandler);
                        } else if (attachment.hasRemaining()) {
                            // Regular write
                            getSocket().write(attachment, toTimeout(getWriteTimeout()),
                                    TimeUnit.MILLISECONDS, attachment, writeCompletionHandler);
                        } else {
                            // All data has been written
                            if (writeInterest && !Nio2Endpoint.isInline()) {
                                writeNotify = true;
                                // Set extra flag so that write nesting does not cause multiple notifications
                                notify = true;
                            } else {
                                // Release here since there will be no
                                // notify/dispatch to do the release.
                                writePending.release();
                            }
                            writeInterest = false;
                        }
                    }
                    if (notify) {
                        if (!endpoint.processSocket(Nio2SocketWrapper.this, SocketEvent.OPEN_WRITE, true)) {
                            close();
                        }
                    }
                }
                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    IOException ioe;
                    if (exc instanceof IOException) {
                        ioe = (IOException) exc;
                    } else {
                        ioe = new IOException(exc);
                    }
                    setError(ioe);
                    writePending.release();
                    if (!endpoint.processSocket(Nio2SocketWrapper.this, SocketEvent.ERROR, true)) {
                        close();
                    }
                }
            };

            gatheringWriteCompletionHandler = new CompletionHandler<Long, ByteBuffer[]>() {
                @Override
                public void completed(Long nBytes, ByteBuffer[] attachment) {
                    writeNotify = false;
                    boolean notify = false;
                    synchronized (writeCompletionHandler) {
                        if (nBytes.longValue() < 0) {
                            failed(new EOFException(sm.getString("iob.failedwrite")), attachment);
                        } else if (!nonBlockingWriteBuffer.isEmpty() || buffersArrayHasRemaining(attachment, 0, attachment.length)) {
                            // Continue writing data using a gathering write
                            ByteBuffer[] array = nonBlockingWriteBuffer.toArray(attachment);
                            getSocket().write(array, 0, array.length,
                                    toTimeout(getWriteTimeout()), TimeUnit.MILLISECONDS,
                                    array, gatheringWriteCompletionHandler);
                        } else {
                            // All data has been written
                            if (writeInterest && !Nio2Endpoint.isInline()) {
                                writeNotify = true;
                                // Set extra flag so that write nesting does not cause multiple notifications
                                notify = true;
                            } else {
                                // Release here since there will be no
                                // notify/dispatch to do the release.
                                writePending.release();
                            }
                            writeInterest = false;
                        }
                    }
                    if (notify) {
                        if (!endpoint.processSocket(Nio2SocketWrapper.this, SocketEvent.OPEN_WRITE, true)) {
                            close();
                        }
                    }
                }
                @Override
                public void failed(Throwable exc, ByteBuffer[] attachment) {
                    IOException ioe;
                    if (exc instanceof IOException) {
                        ioe = (IOException) exc;
                    } else {
                        ioe = new IOException(exc);
                    }
                    setError(ioe);
                    writePending.release();
                    if (!endpoint.processSocket(Nio2SocketWrapper.this, SocketEvent.ERROR, true)) {
                        close();
                    }
               }
            };

        }


        public void setSendfileData(SendfileData sf) { this.sendfileData = sf; }
        public SendfileData getSendfileData() { return this.sendfileData; }

        @Override
        public boolean isReadyForRead() throws IOException {
            synchronized (readCompletionHandler) {
                // A notification has been sent, it is possible to read at least once
                if (readNotify) {
                    return true;
                }
                // If a read is pending, reading is not possible until a notification is sent
                if (!readPending.tryAcquire()) {
                    readInterest = true;
                    return false;
                }
                // It is possible to read directly from the buffer contents
                if (!socketBufferHandler.isReadBufferEmpty()) {
                    readPending.release();
                    return true;
                }
                // Try to read some data
                boolean isReady = fillReadBuffer(false) > 0;
                if (!isReady) {
                    readInterest = true;
                }
                return isReady;
            }
        }


        @Override
        public boolean isReadyForWrite() {
            synchronized (writeCompletionHandler) {
                // A notification has been sent, it is possible to write at least once
                if (writeNotify) {
                    return true;
                }
                // If a write is pending, writing is not possible until a notification is sent
                if (!writePending.tryAcquire()) {
                    writeInterest = true;
                    return false;
                }
                // If the buffer is empty, it is possible to write to it
                if (socketBufferHandler.isWriteBufferEmpty() && nonBlockingWriteBuffer.isEmpty()) {
                    writePending.release();
                    return true;
                }
                // Try to flush all data
                boolean isReady = !flushNonBlockingInternal(true);
                if (!isReady) {
                    writeInterest = true;
                }
                return isReady;
            }
        }


        @Override
        public int read(boolean block, byte[] b, int off, int len) throws IOException {
            checkError();

            if (log.isDebugEnabled()) {
                log.debug("Socket: [" + this + "], block: [" + block + "], length: [" + len + "]");
            }

            if (socketBufferHandler == null) {
                throw new IOException(sm.getString("socket.closed"));
            }

            if (!readNotify) {
                if (block) {
                    try {
                        readPending.acquire();
                    } catch (InterruptedException e) {
                        throw new IOException(e);
                    }
                } else {
                    if (!readPending.tryAcquire()) {
                        if (log.isDebugEnabled()) {
                            log.debug("Socket: [" + this + "], Read in progress. Returning [0]");
                        }
                        return 0;
                    }
                }
            }

            int nRead = populateReadBuffer(b, off, len);
            if (nRead > 0) {
                // The code that was notified is now reading its data
                readNotify = false;
                // This may be sufficient to complete the request and we
                // don't want to trigger another read since if there is no
                // more data to read and this request takes a while to
                // process the read will timeout triggering an error.
                readPending.release();
                return nRead;
            }

            synchronized (readCompletionHandler) {
                // Fill the read buffer as best we can.
                nRead = fillReadBuffer(block);
                // Fill as much of the remaining byte array as possible with the
                // data that was just read
                if (nRead > 0) {
                    socketBufferHandler.configureReadBufferForRead();
                    nRead = Math.min(nRead, len);
                    socketBufferHandler.getReadBuffer().get(b, off, nRead);
                } else if (nRead == 0 && !block) {
                    readInterest = true;
                }
                if (log.isDebugEnabled()) {
                    log.debug("Socket: [" + this + "], Read: [" + nRead + "]");
                }
                return nRead;
            }
        }


        @Override
        public int read(boolean block, ByteBuffer to) throws IOException {
            checkError();

            if (socketBufferHandler == null) {
                throw new IOException(sm.getString("socket.closed"));
            }

            if (!readNotify) {
                if (block) {
                    try {
                        readPending.acquire();
                    } catch (InterruptedException e) {
                        throw new IOException(e);
                    }
                } else {
                    if (!readPending.tryAcquire()) {
                        if (log.isDebugEnabled()) {
                            log.debug("Socket: [" + this + "], Read in progress. Returning [0]");
                        }
                        return 0;
                    }
                }
            }

            int nRead = populateReadBuffer(to);
            if (nRead > 0) {
                // The code that was notified is now reading its data
                readNotify = false;
                // This may be sufficient to complete the request and we
                // don't want to trigger another read since if there is no
                // more data to read and this request takes a while to
                // process the read will timeout triggering an error.
                readPending.release();
                return nRead;
            }

            synchronized (readCompletionHandler) {
                // The socket read buffer capacity is socket.appReadBufSize
                int limit = socketBufferHandler.getReadBuffer().capacity();
                if (block && to.remaining() >= limit) {
                    to.limit(to.position() + limit);
                    nRead = fillReadBuffer(block, to);
                    if (log.isDebugEnabled()) {
                        log.debug("Socket: [" + this + "], Read direct from socket: [" + nRead + "]");
                    }
                } else {
                    // Fill the read buffer as best we can.
                    nRead = fillReadBuffer(block);
                    if (log.isDebugEnabled()) {
                        log.debug("Socket: [" + this + "], Read into buffer: [" + nRead + "]");
                    }
                    // Fill as much of the remaining byte array as possible with the
                    // data that was just read
                    if (nRead > 0) {
                        nRead = populateReadBuffer(to);
                    } else if (nRead == 0 && !block) {
                        readInterest = true;
                    }
                }
                return nRead;
            }
        }


        @Override
        protected void doClose() {
            if (log.isDebugEnabled()) {
                log.debug("Calling [" + getEndpoint() + "].closeSocket([" + this + "])");
            }
            try {
                getEndpoint().connections.remove(getSocket().getIOChannel());
                if (getSocket().isOpen()) {
                    getSocket().close(true);
                }
                if (getEndpoint().running && !getEndpoint().paused) {
                    if (nioChannels == null || !nioChannels.push(getSocket())) {
                        getSocket().free();
                    }
                }
            } catch (Throwable e) {
                ExceptionUtils.handleThrowable(e);
                if (log.isDebugEnabled()) {
                    log.error(sm.getString("endpoint.debug.channelCloseFail"), e);
                }
            } finally {
                socketBufferHandler = SocketBufferHandler.EMPTY;
                nonBlockingWriteBuffer.clear();
                reset(Nio2Channel.CLOSED_NIO2_CHANNEL);
            }
            try {
                SendfileData data = getSendfileData();
                if (data != null && data.fchannel != null && data.fchannel.isOpen()) {
                    data.fchannel.close();
                }
            } catch (Throwable e) {
                ExceptionUtils.handleThrowable(e);
                if (log.isDebugEnabled()) {
                    log.error(sm.getString("endpoint.sendfile.closeError"), e);
                }
            }
        }

        @Override
        public boolean hasAsyncIO() {
            return getEndpoint().getUseAsyncIO();
        }

        @Override
        public boolean needSemaphores() {
            return true;
        }

        @Override
        public boolean hasPerOperationTimeout() {
            return true;
        }

        @Override
        protected <A> OperationState<A> newOperationState(boolean read,
                ByteBuffer[] buffers, int offset, int length,
                BlockingMode block, long timeout, TimeUnit unit, A attachment,
                CompletionCheck check, CompletionHandler<Long, ? super A> handler,
                Semaphore semaphore, VectoredIOCompletionHandler<A> completion) {
            return new Nio2OperationState<>(read, buffers, offset, length, block,
                    timeout, unit, attachment, check, handler, semaphore, completion);
        }

        private class Nio2OperationState<A> extends OperationState<A> {
            private Nio2OperationState(boolean read, ByteBuffer[] buffers, int offset, int length,
                    BlockingMode block, long timeout, TimeUnit unit, A attachment,
                    CompletionCheck check, CompletionHandler<Long, ? super A> handler,
                    Semaphore semaphore, VectoredIOCompletionHandler<A> completion) {
                super(read, buffers, offset, length, block,
                    timeout, unit, attachment, check, handler, semaphore, completion);
            }

            @Override
            protected boolean isInline() {
                return Nio2Endpoint.isInline();
            }

            @Override
            protected void start() {
                if (read) {
                    // Disable any regular read notifications caused by registerReadInterest
                    readNotify = true;
                } else {
                    // Disable any regular write notifications caused by registerWriteInterest
                    writeNotify = true;
                }
                Nio2Endpoint.startInline();
                run();
                Nio2Endpoint.endInline();
            }

            @Override
            public void run() {
                if (read) {
                    long nBytes = 0;
                    // If there is still data inside the main read buffer, it needs to be read first
                    if (!socketBufferHandler.isReadBufferEmpty()) {
                        synchronized (readCompletionHandler) {
                            socketBufferHandler.configureReadBufferForRead();
                            for (int i = 0; i < length && !socketBufferHandler.isReadBufferEmpty(); i++) {
                                nBytes += transfer(socketBufferHandler.getReadBuffer(), buffers[offset + i]);
                            }
                        }
                        if (nBytes > 0) {
                            completion.completed(Long.valueOf(nBytes), this);
                        }
                    }
                    if (nBytes == 0) {
                        getSocket().read(buffers, offset, length, timeout, unit, this, completion);
                    }
                } else {
                    // If there is still data inside the main write buffer, it needs to be written first
                    if (!socketBufferHandler.isWriteBufferEmpty()) {
                        synchronized (writeCompletionHandler) {
                            socketBufferHandler.configureWriteBufferForRead();
                            ByteBuffer[] array = nonBlockingWriteBuffer.toArray(socketBufferHandler.getWriteBuffer());
                            if (buffersArrayHasRemaining(array, 0, array.length)) {
                                getSocket().write(array, 0, array.length, timeout, unit,
                                        array, new CompletionHandler<Long, ByteBuffer[]>() {
                                            @Override
                                            public void completed(Long nBytes, ByteBuffer[] buffers) {
                                                if (nBytes.longValue() < 0) {
                                                    failed(new EOFException(), null);
                                                } else if (buffersArrayHasRemaining(buffers, 0, buffers.length)) {
                                                    getSocket().write(buffers, 0, buffers.length, toTimeout(getWriteTimeout()),
                                                            TimeUnit.MILLISECONDS, buffers, this);
                                                } else {
                                                    // Continue until everything is written
                                                    process();
                                                }
                                            }
                                            @Override
                                            public void failed(Throwable exc, ByteBuffer[] buffers) {
                                                completion.failed(exc, Nio2OperationState.this);
                                            }
                                        });
                                return;
                            }
                        }
                    }
                    getSocket().write(buffers, offset, length, timeout, unit, this, completion);
                }
            }
        }

        /* Callers of this method must:
         * - have acquired the readPending semaphore
         * - have acquired a lock on readCompletionHandler
         *
         * This method will release (or arrange for the release of) the
         * readPending semaphore once the read has completed.
         */
        private int fillReadBuffer(boolean block) throws IOException {
            socketBufferHandler.configureReadBufferForWrite();
            return fillReadBuffer(block, socketBufferHandler.getReadBuffer());
        }

        private int fillReadBuffer(boolean block, ByteBuffer to) throws IOException {
            int nRead = 0;
            Future<Integer> integer = null;
            if (block) {
                try {
                    integer = getSocket().read(to);
                    long timeout = getReadTimeout();
                    if (timeout > 0) {
                        nRead = integer.get(timeout, TimeUnit.MILLISECONDS).intValue();
                    } else {
                        nRead = integer.get().intValue();
                    }
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof IOException) {
                        throw (IOException) e.getCause();
                    } else {
                        throw new IOException(e);
                    }
                } catch (InterruptedException e) {
                    throw new IOException(e);
                } catch (TimeoutException e) {
                    integer.cancel(true);
                    throw new SocketTimeoutException();
                } finally {
                    // Blocking read so need to release here since there will
                    // not be a callback to a completion handler.
                    readPending.release();
                }
            } else {
                Nio2Endpoint.startInline();
                getSocket().read(to, toTimeout(getReadTimeout()), TimeUnit.MILLISECONDS, to,
                        readCompletionHandler);
                Nio2Endpoint.endInline();
                if (readPending.availablePermits() == 1) {
                    nRead = to.position();
                }
            }
            return nRead;
        }


        /**
         * {@inheritDoc}
         * <p>
         * Overridden for NIO2 to enable a gathering write to be used to write
         * all of the remaining data in a single additional write should a
         * non-blocking write leave data in the buffer.
         */
        @Override
        protected void writeNonBlocking(byte[] buf, int off, int len) throws IOException {
            // Note: Possible alternate behavior:
            // If there's non blocking abuse (like a test writing 1MB in a single
            // "non blocking" write), then block until the previous write is
            // done rather than continue buffering
            // Also allows doing autoblocking
            // Could be "smart" with coordination with the main CoyoteOutputStream to
            // indicate the end of a write
            // Uses: if (writePending.tryAcquire(socketWrapper.getTimeout(), TimeUnit.MILLISECONDS))
            synchronized (writeCompletionHandler) {
                checkError();
                if (writeNotify || writePending.tryAcquire()) {
                    // No pending completion handler, so writing to the main buffer
                    // is possible
                    socketBufferHandler.configureWriteBufferForWrite();
                    int thisTime = transfer(buf, off, len, socketBufferHandler.getWriteBuffer());
                    len = len - thisTime;
                    off = off + thisTime;
                    if (len > 0) {
                        // Remaining data must be buffered
                        nonBlockingWriteBuffer.add(buf, off, len);
                    }
                    flushNonBlockingInternal(true);
                } else {
                    nonBlockingWriteBuffer.add(buf, off, len);
                }
            }
        }


        /**
         * {@inheritDoc}
         * <p>
         * Overridden for NIO2 to enable a gathering write to be used to write
         * all of the remaining data in a single additional write should a
         * non-blocking write leave data in the buffer.
         */
        @Override
        protected void writeNonBlocking(ByteBuffer from) throws IOException {
            writeNonBlockingInternal(from);
        }


        /**
         * {@inheritDoc}
         * <p>
         * Overridden for NIO2 to enable a gathering write to be used to write
         * all of the remaining data in a single additional write should a
         * non-blocking write leave data in the buffer.
         */
        @Override
        protected void writeNonBlockingInternal(ByteBuffer from) throws IOException {
            // Note: Possible alternate behavior:
            // If there's non blocking abuse (like a test writing 1MB in a single
            // "non blocking" write), then block until the previous write is
            // done rather than continue buffering
            // Also allows doing autoblocking
            // Could be "smart" with coordination with the main CoyoteOutputStream to
            // indicate the end of a write
            // Uses: if (writePending.tryAcquire(socketWrapper.getTimeout(), TimeUnit.MILLISECONDS))
            synchronized (writeCompletionHandler) {
                checkError();
                if (writeNotify || writePending.tryAcquire()) {
                    // No pending completion handler, so writing to the main buffer
                    // is possible
                    socketBufferHandler.configureWriteBufferForWrite();
                    transfer(from, socketBufferHandler.getWriteBuffer());
                    if (from.remaining() > 0) {
                        // Remaining data must be buffered
                        nonBlockingWriteBuffer.add(from);
                    }
                    flushNonBlockingInternal(true);
                } else {
                    nonBlockingWriteBuffer.add(from);
                }
            }
        }


        /**
         * @param block Ignored since this method is only called in the
         *              blocking case
         */
        @Override
        protected void doWrite(boolean block, ByteBuffer from) throws IOException {
            Future<Integer> integer = null;
            try {
                do {
                    integer = getSocket().write(from);
                    long timeout = getWriteTimeout();
                    if (timeout > 0) {
                        if (integer.get(timeout, TimeUnit.MILLISECONDS).intValue() < 0) {
                            throw new EOFException(sm.getString("iob.failedwrite"));
                        }
                    } else {
                        if (integer.get().intValue() < 0) {
                            throw new EOFException(sm.getString("iob.failedwrite"));
                        }
                    }
                } while (from.hasRemaining());
            } catch (ExecutionException e) {
                if (e.getCause() instanceof IOException) {
                    throw (IOException) e.getCause();
                } else {
                    throw new IOException(e);
                }
            } catch (InterruptedException e) {
                throw new IOException(e);
            } catch (TimeoutException e) {
                integer.cancel(true);
                throw new SocketTimeoutException();
            }
        }


        @Override
        protected void flushBlocking() throws IOException {
            checkError();

            // Before doing a blocking flush, make sure that any pending non
            // blocking write has completed.
            try {
                if (writePending.tryAcquire(toTimeout(getWriteTimeout()), TimeUnit.MILLISECONDS)) {
                    writePending.release();
                } else {
                    throw new SocketTimeoutException();
                }
            } catch (InterruptedException e) {
                // Ignore
            }

            super.flushBlocking();
        }

        @Override
        protected boolean flushNonBlocking() throws IOException {
            checkError();
            return flushNonBlockingInternal(false);
        }

        private boolean flushNonBlockingInternal(boolean hasPermit) {
            synchronized (writeCompletionHandler) {
                if (writeNotify || hasPermit || writePending.tryAcquire()) {
                    // The code that was notified is now writing its data
                    writeNotify = false;
                    socketBufferHandler.configureWriteBufferForRead();
                    if (!nonBlockingWriteBuffer.isEmpty()) {
                        ByteBuffer[] array = nonBlockingWriteBuffer.toArray(socketBufferHandler.getWriteBuffer());
                        Nio2Endpoint.startInline();
                        getSocket().write(array, 0, array.length, toTimeout(getWriteTimeout()),
                                TimeUnit.MILLISECONDS, array, gatheringWriteCompletionHandler);
                        Nio2Endpoint.endInline();
                    } else if (socketBufferHandler.getWriteBuffer().hasRemaining()) {
                        // Regular write
                        Nio2Endpoint.startInline();
                        getSocket().write(socketBufferHandler.getWriteBuffer(), toTimeout(getWriteTimeout()),
                                TimeUnit.MILLISECONDS, socketBufferHandler.getWriteBuffer(),
                                writeCompletionHandler);
                        Nio2Endpoint.endInline();
                    } else {
                        // Nothing was written
                        if (!hasPermit) {
                            writePending.release();
                        }
                        writeInterest = false;
                    }
                }
                return hasDataToWrite();
            }
        }


        @Override
        public boolean hasDataToRead() {
            synchronized (readCompletionHandler) {
                return !socketBufferHandler.isReadBufferEmpty()
                        || readNotify || getError() != null;
            }
        }


        @Override
        public boolean hasDataToWrite() {
            synchronized (writeCompletionHandler) {
                return !socketBufferHandler.isWriteBufferEmpty() || !nonBlockingWriteBuffer.isEmpty()
                        || writeNotify || writePending.availablePermits() == 0 || getError() != null;
            }
        }


        @Override
        public boolean isReadPending() {
            synchronized (readCompletionHandler) {
                return readPending.availablePermits() == 0;
            }
        }


        @Override
        public boolean isWritePending() {
            synchronized (writeCompletionHandler) {
                return writePending.availablePermits() == 0;
            }
        }


        @Override
        public void registerReadInterest() {
            synchronized (readCompletionHandler) {
                // A notification is already being sent
                if (readNotify) {
                    return;
                }
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("endpoint.debug.registerRead", this));
                }
                readInterest = true;
                if (readPending.tryAcquire()) {
                    // No read pending, so do a read
                    try {
                        if (fillReadBuffer(false) > 0) {
                            // Special case where the read completed inline, there is no notification
                            // in that case so it has to be done here
                            if (!getEndpoint().processSocket(this, SocketEvent.OPEN_READ, true)) {
                                close();
                            }
                        }
                    } catch (IOException e) {
                        // Will never happen
                        setError(e);
                    }
                }
            }
        }


        @Override
        public void registerWriteInterest() {
            synchronized (writeCompletionHandler) {
                // A notification is already being sent
                if (writeNotify) {
                    return;
                }
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("endpoint.debug.registerWrite", this));
                }
                writeInterest = true;
                if (writePending.availablePermits() == 1) {
                    // If no write is pending, notify that writing is possible
                    if (!getEndpoint().processSocket(this, SocketEvent.OPEN_WRITE, true)) {
                        close();
                    }
                }
            }
        }


        @Override
        public SendfileDataBase createSendfileData(String filename, long pos, long length) {
            return new SendfileData(filename, pos, length);
        }


        @Override
        public SendfileState processSendfile(SendfileDataBase sendfileData) {
            SendfileData data = (SendfileData) sendfileData;
            setSendfileData(data);
            // Configure the send file data
            if (data.fchannel == null || !data.fchannel.isOpen()) {
                java.nio.file.Path path = new File(sendfileData.fileName).toPath();
                try {
                    data.fchannel = java.nio.channels.FileChannel
                            .open(path, StandardOpenOption.READ).position(sendfileData.pos);
                } catch (IOException e) {
                    return SendfileState.ERROR;
                }
            }
            getSocket().getBufHandler().configureWriteBufferForWrite();
            ByteBuffer buffer = getSocket().getBufHandler().getWriteBuffer();
            int nRead = -1;
            try {
                nRead = data.fchannel.read(buffer);
            } catch (IOException e1) {
                return SendfileState.ERROR;
            }

            if (nRead >= 0) {
                data.length -= nRead;
                getSocket().getBufHandler().configureWriteBufferForRead();
                Nio2Endpoint.startInline();
                getSocket().write(buffer, toTimeout(getWriteTimeout()), TimeUnit.MILLISECONDS,
                        data, sendfileHandler);
                Nio2Endpoint.endInline();
                if (data.doneInline) {
                    if (data.error) {
                        return SendfileState.ERROR;
                    } else {
                        return SendfileState.DONE;
                    }
                } else {
                    return SendfileState.PENDING;
                }
            } else {
                return SendfileState.ERROR;
            }
        }


        @Override
        protected void populateRemoteAddr() {
            AsynchronousSocketChannel sc = getSocket().getIOChannel();
            if (sc != null) {
                SocketAddress socketAddress = null;
                try {
                    socketAddress = sc.getRemoteAddress();
                } catch (IOException e) {
                    // Ignore
                }
                if (socketAddress instanceof InetSocketAddress) {
                    remoteAddr = ((InetSocketAddress) socketAddress).getAddress().getHostAddress();
                }
            }
        }


        @Override
        protected void populateRemoteHost() {
            AsynchronousSocketChannel sc = getSocket().getIOChannel();
            if (sc != null) {
                SocketAddress socketAddress = null;
                try {
                    socketAddress = sc.getRemoteAddress();
                } catch (IOException e) {
                    log.warn(sm.getString("endpoint.warn.noRemoteHost", getSocket()), e);
                }
                if (socketAddress instanceof InetSocketAddress) {
                    remoteHost = ((InetSocketAddress) socketAddress).getAddress().getHostName();
                    if (remoteAddr == null) {
                        remoteAddr = ((InetSocketAddress) socketAddress).getAddress().getHostAddress();
                    }
                }
            }
        }


        @Override
        protected void populateRemotePort() {
            AsynchronousSocketChannel sc = getSocket().getIOChannel();
            if (sc != null) {
                SocketAddress socketAddress = null;
                try {
                    socketAddress = sc.getRemoteAddress();
                } catch (IOException e) {
                    log.warn(sm.getString("endpoint.warn.noRemotePort", getSocket()), e);
                }
                if (socketAddress instanceof InetSocketAddress) {
                    remotePort = ((InetSocketAddress) socketAddress).getPort();
                }
            }
        }


        @Override
        protected void populateLocalName() {
            AsynchronousSocketChannel sc = getSocket().getIOChannel();
            if (sc != null) {
                SocketAddress socketAddress = null;
                try {
                    socketAddress = sc.getLocalAddress();
                } catch (IOException e) {
                    log.warn(sm.getString("endpoint.warn.noLocalName", getSocket()), e);
                }
                if (socketAddress instanceof InetSocketAddress) {
                    localName = ((InetSocketAddress) socketAddress).getHostName();
                }
            }
        }


        @Override
        protected void populateLocalAddr() {
            AsynchronousSocketChannel sc = getSocket().getIOChannel();
            if (sc != null) {
                SocketAddress socketAddress = null;
                try {
                    socketAddress = sc.getLocalAddress();
                } catch (IOException e) {
                    log.warn(sm.getString("endpoint.warn.noLocalAddr", getSocket()), e);
                }
                if (socketAddress instanceof InetSocketAddress) {
                    localAddr = ((InetSocketAddress) socketAddress).getAddress().getHostAddress();
                }
            }
        }


        @Override
        protected void populateLocalPort() {
            AsynchronousSocketChannel sc = getSocket().getIOChannel();
            if (sc != null) {
                SocketAddress socketAddress = null;
                try {
                    socketAddress = sc.getLocalAddress();
                } catch (IOException e) {
                    log.warn(sm.getString("endpoint.warn.noLocalPort", getSocket()), e);
                }
                if (socketAddress instanceof InetSocketAddress) {
                    localPort = ((InetSocketAddress) socketAddress).getPort();
                }
            }
        }


        /**
         * {@inheritDoc}
         * @param clientCertProvider Ignored for this implementation
         */
        @Override
        public SSLSupport getSslSupport(String clientCertProvider) {
            if (getSocket() instanceof SecureNio2Channel) {
                SecureNio2Channel ch = (SecureNio2Channel) getSocket();
                SSLEngine sslEngine = ch.getSslEngine();
                if (sslEngine != null) {
                    SSLSession session = sslEngine.getSession();
                    return ((Nio2Endpoint) getEndpoint()).getSslImplementation().getSSLSupport(session);
                }
            }
            return null;
        }


        @Override
        public void doClientAuth(SSLSupport sslSupport) throws IOException {
            SecureNio2Channel sslChannel = (SecureNio2Channel) getSocket();
            SSLEngine engine = sslChannel.getSslEngine();
            if (!engine.getNeedClientAuth()) {
                // Need to re-negotiate SSL connection
                engine.setNeedClientAuth(true);
                sslChannel.rehandshake();
                ((JSSESupport) sslSupport).setSession(engine.getSession());
            }
        }


        @Override
        public void setAppReadBufHandler(ApplicationBufferHandler handler) {
            getSocket().setAppReadBufHandler(handler);
        }
    }

    public static void startInline() {
        inlineCompletion.set(Boolean.TRUE);
    }

    public static void endInline() {
        inlineCompletion.set(Boolean.FALSE);
    }

    public static boolean isInline() {
        Boolean flag = inlineCompletion.get();
        if (flag == null) {
            return false;
        } else {
            return flag.booleanValue();
        }
    }


    // ---------------------------------------------- SocketProcessor Inner Class
    /**
     * This class is the equivalent of the Worker, but will simply use in an
     * external Executor thread pool.
     */
    protected class SocketProcessor extends SocketProcessorBase<Nio2Channel> {

        public SocketProcessor(SocketWrapperBase<Nio2Channel> socketWrapper, SocketEvent event) {
            super(socketWrapper, event);
        }

        @Override
        protected void doRun() {
            boolean launch = false;
            try {
                int handshake = -1;

                try {
                    if (socketWrapper.getSocket().isHandshakeComplete()) {
                        // No TLS handshaking required. Let the handler
                        // process this socket / event combination.
                        handshake = 0;
                    } else if (event == SocketEvent.STOP || event == SocketEvent.DISCONNECT ||
                            event == SocketEvent.ERROR) {
                        // Unable to complete the TLS handshake. Treat it as
                        // if the handshake failed.
                        handshake = -1;
                    } else {
                        handshake = socketWrapper.getSocket().handshake();
                        // The handshake process reads/writes from/to the
                        // socket. status may therefore be OPEN_WRITE once
                        // the handshake completes. However, the handshake
                        // happens when the socket is opened so the status
                        // must always be OPEN_READ after it completes. It
                        // is OK to always set this as it is only used if
                        // the handshake completes.
                        event = SocketEvent.OPEN_READ;
                    }
                } catch (IOException x) {
                    handshake = -1;
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("endpoint.err.handshake"), x);
                    }
                }
                if (handshake == 0) {
                    SocketState state = SocketState.OPEN;
                    // Process the request from this socket
                    if (event == null) {
                        state = getHandler().process(socketWrapper, SocketEvent.OPEN_READ);
                    } else {
                        state = getHandler().process(socketWrapper, event);
                    }
                    if (state == SocketState.CLOSED) {
                        // Close socket and pool
                        socketWrapper.close();
                    } else if (state == SocketState.UPGRADING) {
                        launch = true;
                    }
                } else if (handshake == -1 ) {
                    getHandler().process(socketWrapper, SocketEvent.CONNECT_FAIL);
                    socketWrapper.close();
                }
            } catch (VirtualMachineError vme) {
                ExceptionUtils.handleThrowable(vme);
            } catch (Throwable t) {
                log.error(sm.getString("endpoint.processing.fail"), t);
                if (socketWrapper != null) {
                    ((Nio2SocketWrapper) socketWrapper).close();
                }
            } finally {
                if (launch) {
                    try {
                        getExecutor().execute(new SocketProcessor(socketWrapper, SocketEvent.OPEN_READ));
                    } catch (NullPointerException npe) {
                        if (running) {
                            log.error(sm.getString("endpoint.launch.fail"),
                                    npe);
                        }
                    }
                }
                socketWrapper = null;
                event = null;
                //return to cache
                if (running && !paused && processorCache != null) {
                    processorCache.push(this);
                }
            }
        }
    }

    // ----------------------------------------------- SendfileData Inner Class
    /**
     * SendfileData class.
     */
    public static class SendfileData extends SendfileDataBase {
        private FileChannel fchannel;
        // Internal use only
        private boolean doneInline = false;
        private boolean error = false;

        public SendfileData(String filename, long pos, long length) {
            super(filename, pos, length);
        }
    }
}
