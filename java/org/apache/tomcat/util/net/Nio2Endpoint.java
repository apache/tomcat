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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileChannel;
import java.nio.channels.NetworkChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.ByteBufferHolder;
import org.apache.tomcat.util.collections.SynchronizedStack;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
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


    public Nio2Endpoint() {
        // Override the defaults for NIO2
        // Disable maxConnections by default for NIO2 (see BZ58103)
        setMaxConnections(-1);
    }


    // ------------------------------------------------------------- Properties

    /**
     * Is deferAccept supported?
     */
    @Override
    public boolean getDeferAccept() {
        // Not supported
        return false;
    }


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
        if ( getExecutor() == null ) {
            createExecutor();
        }
        if (getExecutor() instanceof ExecutorService) {
            threadGroup = AsynchronousChannelGroup.withThreadPool((ExecutorService) getExecutor());
        }
        // AsynchronousChannelGroup currently needs exclusive access to its executor service
        if (!internalExecutor) {
            log.warn(sm.getString("endpoint.nio2.exclusiveExecutor"));
        }

        serverSock = AsynchronousServerSocketChannel.open(threadGroup);
        socketProperties.setProperties(serverSock);
        InetSocketAddress addr = (getAddress()!=null?new InetSocketAddress(getAddress(),getPort()):new InetSocketAddress(getPort()));
        serverSock.bind(addr,getAcceptCount());

        // Initialize thread count defaults for acceptor, poller
        if (acceptorThreadCount != 1) {
            // NIO2 does not allow any form of IO concurrency
            acceptorThreadCount = 1;
        }

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

            processorCache = new SynchronizedStack<>(SynchronizedStack.DEFAULT_SIZE,
                    socketProperties.getProcessorCache());
            nioChannels = new SynchronizedStack<>(SynchronizedStack.DEFAULT_SIZE,
                    socketProperties.getBufferPool());

            // Create worker collection
            if ( getExecutor() == null ) {
                createExecutor();
            }

            initializeConnectionLatch();
            startAcceptorThreads();
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
            // Use the executor to avoid binding the main thread if something bad
            // occurs and unbind will also wait for a bit for it to complete
            getExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    // Then close all active connections if any remain
                    try {
                        for (Nio2Channel channel : getHandler().getOpenSockets()) {
                            closeSocket(channel.getSocket());
                        }
                    } catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                    } finally {
                        allClosed = true;
                    }
                }
            });
            nioChannels.clear();
            processorCache.clear();
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
        try {
            socketProperties.setProperties(socket);
            Nio2Channel channel = nioChannels.pop();
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
            Nio2SocketWrapper socketWrapper = new Nio2SocketWrapper(channel, this);
            channel.reset(socket, socketWrapper);
            socketWrapper.setReadTimeout(getConnectionTimeout());
            socketWrapper.setWriteTimeout(getConnectionTimeout());
            socketWrapper.setKeepAliveLeft(Nio2Endpoint.this.getMaxKeepAliveRequests());
            socketWrapper.setSecure(isSSLEnabled());
            // Continue processing on another thread
            return processSocket(socketWrapper, SocketEvent.OPEN_READ, true);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error("",t);
        }
        // Tell to close the socket
        return false;
    }


    @Override
    protected void closeSocket(AsynchronousSocketChannel socket) {
        countDownConnection();
        try {
            socket.close();
        } catch (IOException ioe) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("endpoint.err.close"), ioe);
            }
        }
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


    private void closeSocket(SocketWrapperBase<Nio2Channel> socket) {
        if (log.isDebugEnabled()) {
            log.debug("Calling [" + this + "].closeSocket([" + socket + "],[" + socket.getSocket() + "])",
                    new Exception());
        }
        if (socket == null) {
            return;
        }
        try {
            getHandler().release(socket);
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            if (log.isDebugEnabled()) log.error("",e);
        }
        Nio2SocketWrapper nio2Socket = (Nio2SocketWrapper) socket;
        try {
            synchronized (socket.getSocket()) {
                if (!nio2Socket.closed) {
                    nio2Socket.closed = true;
                    countDownConnection();
                }
                if (socket.getSocket().isOpen()) {
                    socket.getSocket().close(true);
                }
            }
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            if (log.isDebugEnabled()) log.error("",e);
        }
        try {
            if (nio2Socket.getSendfileData() != null
                    && nio2Socket.getSendfileData().fchannel != null
                    && nio2Socket.getSendfileData().fchannel.isOpen()) {
                nio2Socket.getSendfileData().fchannel.close();
            }
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            if (log.isDebugEnabled()) log.error("",e);
        }
    }

    public static class Nio2SocketWrapper extends SocketWrapperBase<Nio2Channel> {

        private static final ThreadLocal<AtomicInteger> nestedWriteCompletionCount =
                new ThreadLocal<AtomicInteger>() {
            @Override
            protected AtomicInteger initialValue() {
                return new AtomicInteger(0);
            }
        };

        private SendfileData sendfileData = null;

        private final CompletionHandler<Integer, SocketWrapperBase<Nio2Channel>> readCompletionHandler;
        private final Semaphore readPending = new Semaphore(1);
        private boolean readInterest = false; // Guarded by readCompletionHandler

        private final CompletionHandler<Integer, ByteBuffer> writeCompletionHandler;
        private final CompletionHandler<Long, ByteBuffer[]> gatheringWriteCompletionHandler;
        private final Semaphore writePending = new Semaphore(1);
        private boolean writeInterest = false; // Guarded by writeCompletionHandler
        private boolean writeNotify = false;
        private volatile boolean closed = false;

        private CompletionHandler<Integer, SocketWrapperBase<Nio2Channel>> awaitBytesHandler
                = new CompletionHandler<Integer, SocketWrapperBase<Nio2Channel>>() {

            @Override
            public void completed(Integer nBytes, SocketWrapperBase<Nio2Channel> attachment) {
                if (nBytes.intValue() < 0) {
                    failed(new ClosedChannelException(), attachment);
                    return;
                }
                getEndpoint().processSocket(attachment, SocketEvent.OPEN_READ, Nio2Endpoint.isInline());
            }

            @Override
            public void failed(Throwable exc, SocketWrapperBase<Nio2Channel> attachment) {
                getEndpoint().processSocket(attachment, SocketEvent.DISCONNECT, true);
            }
        };

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
                                getEndpoint().processSocket(Nio2SocketWrapper.this,
                                        SocketEvent.OPEN_READ, true);
                                break;
                            }
                            case OPEN: {
                                awaitBytes();
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
                getSocket().write(buffer, toNio2Timeout(getWriteTimeout()), TimeUnit.MILLISECONDS, attachment, this);
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
            socketBufferHandler = channel.getBufHandler();

            this.readCompletionHandler = new CompletionHandler<Integer, SocketWrapperBase<Nio2Channel>>() {
                @Override
                public void completed(Integer nBytes, SocketWrapperBase<Nio2Channel> attachment) {
                    boolean notify = false;
                    if (log.isDebugEnabled()) {
                        log.debug("Socket: [" + attachment + "], Interest: [" + readInterest + "]");
                    }
                    synchronized (readCompletionHandler) {
                        if (nBytes.intValue() < 0) {
                            failed(new EOFException(), attachment);
                        } else {
                            if (readInterest && !Nio2Endpoint.isInline()) {
                                readInterest = false;
                                notify = true;
                            } else {
                                // Release here since there will be no
                                // notify/dispatch to do the release.
                                readPending.release();
                            }
                        }
                    }
                    if (notify) {
                        getEndpoint().processSocket(attachment, SocketEvent.OPEN_READ, false);
                    }
                }
                @Override
                public void failed(Throwable exc, SocketWrapperBase<Nio2Channel> attachment) {
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
                        return;
                    }
                    getEndpoint().processSocket(attachment, SocketEvent.ERROR, true);
                }
            };

            this.writeCompletionHandler = new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer nBytes, ByteBuffer attachment) {
                    writeNotify = false;
                    synchronized (writeCompletionHandler) {
                        if (nBytes.intValue() < 0) {
                            failed(new EOFException(sm.getString("iob.failedwrite")), attachment);
                        } else if (bufferedWrites.size() > 0) {
                            nestedWriteCompletionCount.get().incrementAndGet();
                            // Continue writing data using a gathering write
                            List<ByteBuffer> arrayList = new ArrayList<>();
                            if (attachment.hasRemaining()) {
                                arrayList.add(attachment);
                            }
                            for (ByteBufferHolder buffer : bufferedWrites) {
                                buffer.flip();
                                arrayList.add(buffer.getBuf());
                            }
                            bufferedWrites.clear();
                            ByteBuffer[] array = arrayList.toArray(new ByteBuffer[arrayList.size()]);
                            getSocket().write(array, 0, array.length,
                                    toNio2Timeout(getWriteTimeout()), TimeUnit.MILLISECONDS,
                                    array, gatheringWriteCompletionHandler);
                            nestedWriteCompletionCount.get().decrementAndGet();
                        } else if (attachment.hasRemaining()) {
                            // Regular write
                            nestedWriteCompletionCount.get().incrementAndGet();
                            getSocket().write(attachment, toNio2Timeout(getWriteTimeout()),
                                    TimeUnit.MILLISECONDS, attachment, writeCompletionHandler);
                            nestedWriteCompletionCount.get().decrementAndGet();
                        } else {
                            // All data has been written
                            if (writeInterest) {
                                writeInterest = false;
                                writeNotify = true;
                            }
                            writePending.release();
                        }
                    }
                    if (writeNotify && nestedWriteCompletionCount.get().get() == 0) {
                        endpoint.processSocket(Nio2SocketWrapper.this, SocketEvent.OPEN_WRITE, Nio2Endpoint.isInline());
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
                    endpoint.processSocket(Nio2SocketWrapper.this, SocketEvent.ERROR, true);
                }
            };

            gatheringWriteCompletionHandler = new CompletionHandler<Long, ByteBuffer[]>() {
                @Override
                public void completed(Long nBytes, ByteBuffer[] attachment) {
                    writeNotify = false;
                    synchronized (writeCompletionHandler) {
                        if (nBytes.longValue() < 0) {
                            failed(new EOFException(sm.getString("iob.failedwrite")), attachment);
                        } else if (bufferedWrites.size() > 0 || arrayHasData(attachment)) {
                            // Continue writing data
                            nestedWriteCompletionCount.get().incrementAndGet();
                            List<ByteBuffer> arrayList = new ArrayList<>();
                            for (ByteBuffer buffer : attachment) {
                                if (buffer.hasRemaining()) {
                                    arrayList.add(buffer);
                                }
                            }
                            for (ByteBufferHolder buffer : bufferedWrites) {
                                buffer.flip();
                                arrayList.add(buffer.getBuf());
                            }
                            bufferedWrites.clear();
                            ByteBuffer[] array = arrayList.toArray(new ByteBuffer[arrayList.size()]);
                            getSocket().write(array, 0, array.length,
                                    toNio2Timeout(getWriteTimeout()), TimeUnit.MILLISECONDS,
                                    array, gatheringWriteCompletionHandler);
                            nestedWriteCompletionCount.get().decrementAndGet();
                        } else {
                            // All data has been written
                            if (writeInterest) {
                                writeInterest = false;
                                writeNotify = true;
                            }
                            writePending.release();
                        }
                    }
                    if (writeNotify && nestedWriteCompletionCount.get().get() == 0) {
                        endpoint.processSocket(Nio2SocketWrapper.this, SocketEvent.OPEN_WRITE, Nio2Endpoint.isInline());
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
                    endpoint.processSocket(Nio2SocketWrapper.this, SocketEvent.ERROR, true);
               }
            };

        }

        private static boolean arrayHasData(ByteBuffer[] byteBuffers) {
            for (ByteBuffer byteBuffer : byteBuffers) {
                if (byteBuffer.hasRemaining()) {
                    return true;
                }
            }
            return false;
        }


        public void setSendfileData(SendfileData sf) { this.sendfileData = sf; }
        public SendfileData getSendfileData() { return this.sendfileData; }

        @Override
        public boolean isReadyForRead() throws IOException {
            synchronized (readCompletionHandler) {
                if (!readPending.tryAcquire()) {
                    readInterest = true;
                    return false;
                }

                if (!socketBufferHandler.isReadBufferEmpty()) {
                    readPending.release();
                    return true;
                }

                int nRead = fillReadBuffer(false);

                boolean isReady = nRead > 0;

                if (!isReady) {
                    readInterest = true;
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

            int nRead = populateReadBuffer(b, off, len);
            if (nRead > 0) {
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

            int nRead = populateReadBuffer(to);
            if (nRead > 0) {
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
        public void close() throws IOException {
            getSocket().close();
        }


        @Override
        public boolean isClosed() {
            return closed || !getSocket().isOpen();
        }


        @Override
        public boolean isWritePending() {
            synchronized (writeCompletionHandler) {
                return writePending.availablePermits() == 0;
            }
        }

        @Override
        public boolean hasAsyncIO() {
            return true;
        }

        /**
         * Internal state tracker for scatter/gather operations.
         */
        private static class OperationState<A> {
            private final boolean read;
            private final ByteBuffer[] buffers;
            private final int offset;
            private final int length;
            private final A attachment;
            private final long timeout;
            private final TimeUnit unit;
            private final BlockingMode block;
            private final CompletionCheck check;
            private final CompletionHandler<Long, ? super A> handler;
            private final Semaphore semaphore;
            private OperationState(boolean read, ByteBuffer[] buffers, int offset, int length,
                    BlockingMode block, long timeout, TimeUnit unit, A attachment,
                    CompletionCheck check, CompletionHandler<Long, ? super A> handler,
                    Semaphore semaphore) {
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
            }
            private volatile long nBytes = 0;
            private volatile CompletionState state = CompletionState.PENDING;
        }

        @Override
        public <A> CompletionState read(ByteBuffer[] dsts, int offset, int length,
                BlockingMode block, long timeout, TimeUnit unit, A attachment,
                CompletionCheck check, CompletionHandler<Long, ? super A> handler) {
            IOException ioe = getError();
            if (ioe != null) {
                handler.failed(ioe, attachment);
                return CompletionState.ERROR;
            }
            if (timeout == -1) {
                timeout = toNio2Timeout(getReadTimeout());
            }
            if (block != BlockingMode.NON_BLOCK) {
                try {
                    if (!readPending.tryAcquire(timeout, unit)) {
                        handler.failed(new SocketTimeoutException(), attachment);
                        return CompletionState.ERROR;
                    }
                } catch (InterruptedException e) {
                    handler.failed(e, attachment);
                    return CompletionState.ERROR;
                }
            } else {
                if (!readPending.tryAcquire()) {
                    return CompletionState.NOT_DONE;
                }
            }
            OperationState<A> state = new OperationState<>(true, dsts, offset, length, block,
                    timeout, unit, attachment, check, handler, readPending);
            VectoredIOCompletionHandler<A> completion = new VectoredIOCompletionHandler<>();
            Nio2Endpoint.startInline();
            long nBytes = 0;
            if (!socketBufferHandler.isReadBufferEmpty()) {
                // There is still data inside the main read buffer, use it to fill out the destination buffers
                synchronized (readCompletionHandler) {
                    // Note: It is not necessary to put this code in the completion handler
                    socketBufferHandler.configureReadBufferForRead();
                    for (int i = 0; i < length && !socketBufferHandler.isReadBufferEmpty(); i++) {
                        nBytes += transfer(socketBufferHandler.getReadBuffer(), dsts[offset + i]);
                    }
                }
                if (nBytes > 0) {
                    completion.completed(Long.valueOf(nBytes), state);
                }
            }
            if (nBytes == 0) {
                getSocket().read(dsts, offset, length, timeout, unit, state, completion);
            }
            Nio2Endpoint.endInline();
            if (block == BlockingMode.BLOCK) {
                synchronized (state) {
                    if (state.state == CompletionState.PENDING) {
                        try {
                            state.wait(unit.toMillis(timeout));
                            if (state.state == CompletionState.PENDING) {
                                return CompletionState.ERROR;
                            }
                        } catch (InterruptedException e) {
                            handler.failed(new SocketTimeoutException(), attachment);
                            return CompletionState.ERROR;
                        }
                    }
                }
            }
            return state.state;
        }

        @Override
        public <A> CompletionState write(ByteBuffer[] srcs, int offset, int length,
                BlockingMode block, long timeout, TimeUnit unit, A attachment,
                CompletionCheck check, CompletionHandler<Long, ? super A> handler) {
            IOException ioe = getError();
            if (ioe != null) {
                handler.failed(ioe, attachment);
                return CompletionState.ERROR;
            }
            if (timeout == -1) {
                timeout = toNio2Timeout(getWriteTimeout());
            }
            if (block != BlockingMode.NON_BLOCK) {
                try {
                    if (!writePending.tryAcquire(timeout, unit)) {
                        handler.failed(new SocketTimeoutException(), attachment);
                        return CompletionState.ERROR;
                    }
                } catch (InterruptedException e) {
                    handler.failed(e, attachment);
                    return CompletionState.ERROR;
                }
            } else {
                if (!writePending.tryAcquire()) {
                    return CompletionState.NOT_DONE;
                }
            }
            if (!socketBufferHandler.isWriteBufferEmpty()) {
                // First flush the main buffer as needed
                try {
                    doWrite(true);
                } catch (IOException e) {
                    handler.failed(e, attachment);
                    return CompletionState.ERROR;
                }
            }
            OperationState<A> state = new OperationState<>(false, srcs, offset, length, block,
                    timeout, unit, attachment, check, handler, writePending);
            VectoredIOCompletionHandler<A> completion = new VectoredIOCompletionHandler<>();
            Nio2Endpoint.startInline();
            // It should be less necessary to check the buffer state as it is easy to flush before
            getSocket().write(srcs, offset, length, timeout, unit, state, completion);
            Nio2Endpoint.endInline();
            if (block == BlockingMode.BLOCK) {
                synchronized (state) {
                    if (state.state == CompletionState.PENDING) {
                        try {
                            state.wait(unit.toMillis(timeout));
                            if (state.state == CompletionState.PENDING) {
                                return CompletionState.ERROR;
                            }
                        } catch (InterruptedException e) {
                            handler.failed(new SocketTimeoutException(), attachment);
                            return CompletionState.ERROR;
                        }
                    }
                }
            }
            return state.state;
        }

        private class VectoredIOCompletionHandler<A> implements CompletionHandler<Long, OperationState<A>> {
            @Override
            public void completed(Long nBytes, OperationState<A> state) {
                if (nBytes.longValue() < 0) {
                    failed(new EOFException(), state);
                } else {
                    state.nBytes += nBytes.longValue();
                    CompletionState currentState = Nio2Endpoint.isInline() ? CompletionState.INLINE : CompletionState.DONE;
                    boolean complete = true;
                    boolean completion = true;
                    if (state.check != null) {
                        switch (state.check.callHandler(currentState, state.buffers, state.offset, state.length)) {
                        case CONTINUE:
                            complete = false;
                            break;
                        case DONE:
                            break;
                        case NONE:
                            completion = false;
                            break;
                        }
                    }
                    if (complete) {
                        boolean notify = false;
                        state.semaphore.release();
                        if (state.block == BlockingMode.BLOCK && currentState != CompletionState.INLINE) {
                            notify = true;
                        } else {
                            state.state = currentState;
                        }
                        if (completion && state.handler != null) {
                            state.handler.completed(Long.valueOf(state.nBytes), state.attachment);
                        }
                        if (notify) {
                            synchronized (state) {
                                state.state = currentState;
                                state.notify();
                            }
                        }
                    } else {
                        if (state.read) {
                            getSocket().read(state.buffers, state.offset, state.length,
                                    state.timeout, state.unit, state, this);
                        } else {
                            getSocket().write(state.buffers, state.offset, state.length,
                                    state.timeout, state.unit, state, this);
                        }
                    }
                }
            }
            @Override
            public void failed(Throwable exc, OperationState<A> state) {
                IOException ioe;
                if (exc instanceof IOException) {
                    ioe = (IOException) exc;
                } else {
                    ioe = new IOException(exc);
                }
                setError(ioe);
                boolean notify = false;
                state.semaphore.release();
                if (state.block == BlockingMode.BLOCK) {
                    notify = true;
                } else {
                    state.state = Nio2Endpoint.isInline() ? CompletionState.ERROR : CompletionState.DONE;
                }
                if (state.handler != null) {
                    state.handler.failed(ioe, state.attachment);
                }
                if (notify) {
                    synchronized (state) {
                        state.state = Nio2Endpoint.isInline() ? CompletionState.ERROR : CompletionState.DONE;
                        state.notify();
                    }
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
                getSocket().read(to, toNio2Timeout(getReadTimeout()), TimeUnit.MILLISECONDS, this,
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
                if (writePending.tryAcquire()) {
                    // No pending completion handler, so writing to the main buffer
                    // is possible
                    socketBufferHandler.configureWriteBufferForWrite();
                    int thisTime = transfer(buf, off, len, socketBufferHandler.getWriteBuffer());
                    len = len - thisTime;
                    off = off + thisTime;
                    if (len > 0) {
                        // Remaining data must be buffered
                        addToBuffers(buf, off, len);
                    }
                    flushNonBlocking(true);
                } else {
                    addToBuffers(buf, off, len);
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
            // Note: Possible alternate behavior:
            // If there's non blocking abuse (like a test writing 1MB in a single
            // "non blocking" write), then block until the previous write is
            // done rather than continue buffering
            // Also allows doing autoblocking
            // Could be "smart" with coordination with the main CoyoteOutputStream to
            // indicate the end of a write
            // Uses: if (writePending.tryAcquire(socketWrapper.getTimeout(), TimeUnit.MILLISECONDS))
            synchronized (writeCompletionHandler) {
                if (writePending.tryAcquire()) {
                    // No pending completion handler, so writing to the main buffer
                    // is possible
                    socketBufferHandler.configureWriteBufferForWrite();
                    transfer(from, socketBufferHandler.getWriteBuffer());
                    if (from.remaining() > 0) {
                        // Remaining data must be buffered
                        addToBuffers(from);
                    }
                    flushNonBlocking(true);
                } else {
                    addToBuffers(from);
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
                if (writePending.tryAcquire(toNio2Timeout(getWriteTimeout()), TimeUnit.MILLISECONDS)) {
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
            return flushNonBlocking(false);
        }

        private boolean flushNonBlocking(boolean hasPermit) throws IOException {
            checkError();
            synchronized (writeCompletionHandler) {
                if (hasPermit || writePending.tryAcquire()) {
                    socketBufferHandler.configureWriteBufferForRead();
                    if (bufferedWrites.size() > 0) {
                        // Gathering write of the main buffer plus all leftovers
                        List<ByteBuffer> arrayList = new ArrayList<>();
                        if (socketBufferHandler.getWriteBuffer().hasRemaining()) {
                            arrayList.add(socketBufferHandler.getWriteBuffer());
                        }
                        for (ByteBufferHolder buffer : bufferedWrites) {
                            buffer.flip();
                            arrayList.add(buffer.getBuf());
                        }
                        bufferedWrites.clear();
                        ByteBuffer[] array = arrayList.toArray(new ByteBuffer[arrayList.size()]);
                        Nio2Endpoint.startInline();
                        getSocket().write(array, 0, array.length, toNio2Timeout(getWriteTimeout()),
                                TimeUnit.MILLISECONDS, array, gatheringWriteCompletionHandler);
                        Nio2Endpoint.endInline();
                    } else if (socketBufferHandler.getWriteBuffer().hasRemaining()) {
                        // Regular write
                        Nio2Endpoint.startInline();
                        getSocket().write(socketBufferHandler.getWriteBuffer(), toNio2Timeout(getWriteTimeout()),
                                TimeUnit.MILLISECONDS, socketBufferHandler.getWriteBuffer(),
                                writeCompletionHandler);
                        Nio2Endpoint.endInline();
                    } else {
                        // Nothing was written
                        if (!hasPermit) {
                            writePending.release();
                        }
                    }
                }
                return hasDataToWrite();
            }
        }


        @Override
        public boolean hasDataToWrite() {
            synchronized (writeCompletionHandler) {
                return !socketBufferHandler.isWriteBufferEmpty() ||
                        bufferedWrites.size() > 0 || getError() != null;
            }
        }


        @Override
        public boolean isReadPending() {
            synchronized (readCompletionHandler) {
                return readPending.availablePermits() == 0;
            }
        }


        @Override
        public boolean awaitReadComplete(long timeout, TimeUnit unit) {
            try {
                if (readPending.tryAcquire(timeout, unit)) {
                    readPending.release();
                    return true;
                } else {
                    return false;
                }
            } catch (InterruptedException e) {
                return false;
            }
        }


        @Override
        public boolean awaitWriteComplete(long timeout, TimeUnit unit) {
            try {
                if (writePending.tryAcquire(timeout, unit)) {
                    writePending.release();
                    return true;
                } else {
                    return false;
                }
            } catch (InterruptedException e) {
                return false;
            }
        }

        /*
         * This should only be called from a thread that currently holds a lock
         * on the socket. This prevents a race condition between a pending read
         * being completed and processed and a thread triggering a new read.
         */
        void releaseReadPending() {
            synchronized (readCompletionHandler) {
                if (readPending.availablePermits() == 0) {
                    readPending.release();
                }
            }
        }


        @Override
        public void registerReadInterest() {
            synchronized (readCompletionHandler) {
                if (readPending.availablePermits() == 0) {
                    readInterest = true;
                } else {
                    // If no read is pending, start waiting for data
                    awaitBytes();
                }
            }
        }


        @Override
        public void registerWriteInterest() {
            synchronized (writeCompletionHandler) {
                if (writePending.availablePermits() == 0) {
                    writeInterest = true;
                } else {
                    // If no write is pending, notify
                    getEndpoint().processSocket(this, SocketEvent.OPEN_WRITE, true);
                }
            }
        }


        public void awaitBytes() {
            // NO-OP is there is already a read in progress.
            if (readPending.tryAcquire()) {
                getSocket().getBufHandler().configureReadBufferForWrite();
                Nio2Endpoint.startInline();
                getSocket().read(getSocket().getBufHandler().getReadBuffer(),
                        toNio2Timeout(getReadTimeout()), TimeUnit.MILLISECONDS, this, awaitBytesHandler);
                Nio2Endpoint.endInline();
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
                getSocket().write(buffer, toNio2Timeout(getWriteTimeout()), TimeUnit.MILLISECONDS,
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
            SocketAddress socketAddress = null;
            try {
                socketAddress = getSocket().getIOChannel().getRemoteAddress();
            } catch (IOException e) {
                // Ignore
            }
            if (socketAddress instanceof InetSocketAddress) {
                remoteAddr = ((InetSocketAddress) socketAddress).getAddress().getHostAddress();
            }
        }


        @Override
        protected void populateRemoteHost() {
            SocketAddress socketAddress = null;
            try {
                socketAddress = getSocket().getIOChannel().getRemoteAddress();
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


        @Override
        protected void populateRemotePort() {
            SocketAddress socketAddress = null;
            try {
                socketAddress = getSocket().getIOChannel().getRemoteAddress();
            } catch (IOException e) {
                log.warn(sm.getString("endpoint.warn.noRemotePort", getSocket()), e);
            }
            if (socketAddress instanceof InetSocketAddress) {
                remotePort = ((InetSocketAddress) socketAddress).getPort();
            }
        }


        @Override
        protected void populateLocalName() {
            SocketAddress socketAddress = null;
            try {
                socketAddress = getSocket().getIOChannel().getLocalAddress();
            } catch (IOException e) {
                log.warn(sm.getString("endpoint.warn.noLocalName", getSocket()), e);
            }
            if (socketAddress instanceof InetSocketAddress) {
                localName = ((InetSocketAddress) socketAddress).getHostName();
            }
        }


        @Override
        protected void populateLocalAddr() {
            SocketAddress socketAddress = null;
            try {
                socketAddress = getSocket().getIOChannel().getLocalAddress();
            } catch (IOException e) {
                log.warn(sm.getString("endpoint.warn.noLocalAddr", getSocket()), e);
            }
            if (socketAddress instanceof InetSocketAddress) {
                localAddr = ((InetSocketAddress) socketAddress).getAddress().getHostAddress();
            }
        }


        @Override
        protected void populateLocalPort() {
            SocketAddress socketAddress = null;
            try {
                socketAddress = getSocket().getIOChannel().getLocalAddress();
            } catch (IOException e) {
                log.warn(sm.getString("endpoint.warn.noLocalPort", getSocket()), e);
            }
            if (socketAddress instanceof InetSocketAddress) {
                localPort = ((InetSocketAddress) socketAddress).getPort();
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
                SSLSession session = ch.getSslEngine().getSession();
                return ((Nio2Endpoint) getEndpoint()).getSslImplementation().getSSLSupport(session);
            } else {
                return null;
            }
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

    public static long toNio2Timeout(long timeout) {
        // NIO2 can't do infinite timeout so use Long.MAX_VALUE if timeout is <= 0
        return (timeout > 0) ? timeout : Long.MAX_VALUE;
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
            if (SocketEvent.OPEN_WRITE != event) {
                // Anything other than OPEN_WRITE is a genuine read or an
                // error condition so for all of those release the semaphore
                ((Nio2SocketWrapper) socketWrapper).releaseReadPending();
            }
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
                        closeSocket(socketWrapper);
                        if (running && !paused) {
                            if (!nioChannels.push(socketWrapper.getSocket())) {
                                socketWrapper.getSocket().free();
                            }
                        }
                    } else if (state == SocketState.UPGRADING) {
                        launch = true;
                    }
                } else if (handshake == -1 ) {
                    closeSocket(socketWrapper);
                    if (running && !paused) {
                        if (!nioChannels.push(socketWrapper.getSocket())) {
                            socketWrapper.getSocket().free();
                        }
                    }
                }
            } catch (VirtualMachineError vme) {
                ExceptionUtils.handleThrowable(vme);
            } catch (Throwable t) {
                log.error(sm.getString("endpoint.processing.fail"), t);
                if (socketWrapper != null) {
                    closeSocket(socketWrapper);
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
                if (running && !paused) {
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
