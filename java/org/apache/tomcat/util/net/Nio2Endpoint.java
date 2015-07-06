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
import java.nio.channels.ReadPendingException;
import java.nio.channels.WritePendingException;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
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
public class Nio2Endpoint extends AbstractJsseEndpoint<Nio2Channel> {


    // -------------------------------------------------------------- Constants


    private static final Log log = LogFactory.getLog(Nio2Endpoint.class);


    // ----------------------------------------------------------------- Fields

    /**
     * Server socket "pointer".
     */
    private AsynchronousServerSocketChannel serverSock = null;

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
     * Cache for SocketProcessor objects
     */
    private SynchronizedStack<SocketProcessor> processorCache;

    /**
     * Bytebuffer cache, each channel holds a set of buffers (two, except for SSL holds four)
     */
    private SynchronizedStack<Nio2Channel> nioChannels;


    // ------------------------------------------------------------- Properties


    /**
     * Handling of accepted sockets.
     */
    private Handler handler = null;
    public void setHandler(Handler handler ) { this.handler = handler; }
    @Override
    public Handler getHandler() { return handler; }


    public void setSocketProperties(SocketProperties socketProperties) {
        this.socketProperties = socketProperties;
    }

    /**
     * Is deferAccept supported?
     */
    @Override
    public boolean getDeferAccept() {
        // Not supported
        return false;
    }


    /**
     * Port in use.
     */
    @Override
    public int getLocalPort() {
        AsynchronousServerSocketChannel ssc = serverSock;
        if (ssc == null) {
            return -1;
        } else {
            try {
                SocketAddress sa = ssc.getLocalAddress();
                if (sa instanceof InetSocketAddress) {
                    return ((InetSocketAddress) sa).getPort();
                } else {
                    return -1;
                }
            } catch (IOException e) {
                return -1;
            }
        }
    }


    protected void releaseCaches() {
        this.nioChannels.clear();
        this.processorCache.clear();
        if ( handler != null ) handler.recycle();
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
        serverSock.bind(addr,getBacklog());

        // Initialize thread count defaults for acceptor, poller
        if (acceptorThreadCount != 1) {
            // NIO2 does not allow any form of IO concurrency
            acceptorThreadCount = 1;
        }

        // Initialize SSL if needed
        initialiseSsl();
    }


    /**
     * Start the NIO endpoint, creating acceptor, poller threads.
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

            setAsyncTimeout(new AsyncTimeout());
            Thread timeoutThread = new Thread(getAsyncTimeout(), getName() + "-AsyncTimeout");
            timeoutThread.setPriority(threadPriority);
            timeoutThread.setDaemon(true);
            timeoutThread.start();
        }
    }


    /**
     * Stop the endpoint. This will cause all processing threads to stop.
     */
    @Override
    public void stopInternal() {
        releaseConnectionLatch();
        if (!paused) {
            pause();
        }
        if (running) {
            running = false;
            getAsyncTimeout().stop();
            unlockAccept();
            // Use the executor to avoid binding the main thread if something bad
            // occurs and unbind will also wait for a bit for it to complete
            getExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    // Timeout any pending async request
                    for (SocketWrapperBase<Nio2Channel> socket : waitingRequests) {
                        processSocket(socket, SocketStatus.TIMEOUT, false);
                    }
                    // Then close all active connections if any remain
                    try {
                        handler.closeAll();
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
        // Close server socket
        serverSock.close();
        serverSock = null;
        super.unbind();
        // Unlike other connectors, the thread pool is tied to the server socket
        shutdownExecutor();
        releaseCaches();
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


    public int getWriteBufSize() {
        return socketProperties.getTxBufSize();
    }

    public int getReadBufSize() {
        return socketProperties.getRxBufSize();
    }

    @Override
    protected AbstractEndpoint.Acceptor createAcceptor() {
        return new Acceptor();
    }

    /**
     * Process the specified connection.
     */
    protected boolean setSocketOptions(AsynchronousSocketChannel socket) {
        // Process the connection
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
            socketWrapper.setReadTimeout(getSocketProperties().getSoTimeout());
            socketWrapper.setWriteTimeout(getSocketProperties().getSoTimeout());
            socketWrapper.setKeepAliveLeft(Nio2Endpoint.this.getMaxKeepAliveRequests());
            socketWrapper.setSecure(isSSLEnabled());
            socketWrapper.setReadTimeout(getSoTimeout());
            socketWrapper.setWriteTimeout(getSoTimeout());
            // Continue processing on another thread
            processSocket(socketWrapper, SocketStatus.OPEN_READ, true);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            try {
                log.error("",t);
            } catch (Throwable tt) {
                ExceptionUtils.handleThrowable(t);
            }
            // Tell to close the socket
            return false;
        }
        return true;
    }


    /**
     * Returns true if a worker thread is available for processing.
     * @return boolean
     */
    protected boolean isWorkerAvailable() {
        return true;
    }

    @Override
    public void processSocket(SocketWrapperBase<Nio2Channel> socketWrapper,
            SocketStatus socketStatus, boolean dispatch) {
        processSocket0(socketWrapper, socketStatus, dispatch);
    }

    protected boolean processSocket0(SocketWrapperBase<Nio2Channel> socketWrapper, SocketStatus status, boolean dispatch) {
        try {
            waitingRequests.remove(socketWrapper);
            SocketProcessor sc = processorCache.pop();
            if (sc == null) {
                sc = new SocketProcessor(socketWrapper, status);
            } else {
                sc.reset(socketWrapper, status);
            }
            Executor executor = getExecutor();
            if (dispatch && executor != null) {
                executor.execute(sc);
            } else {
                sc.run();
            }
        } catch (RejectedExecutionException ree) {
            log.debug(sm.getString("endpoint.executor.fail", socketWrapper), ree);
            return false;
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // This means we got an OOM or similar creating a thread, or that
            // the pool and its queue are full
            log.error(sm.getString("endpoint.process.fail"), t);
            return false;
        }
        return true;
    }

    public void closeSocket(SocketWrapperBase<Nio2Channel> socket) {
        if (log.isDebugEnabled()) {
            log.debug("Calling [" + this + "].closeSocket([" + socket + "],[" + socket.getSocket() + "])",
                    new Exception());
        }
        if (socket == null) {
            return;
        }
        try {
            handler.release(socket);
            try {
                if (socket.getSocket() != null) {
                    socket.getSocket().close(true);
                }
            } catch (Exception e){
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString(
                            "endpoint.debug.socketCloseFail"), e);
                }
            }
            Nio2SocketWrapper nio2Socket = (Nio2SocketWrapper) socket;
            try {
                if (nio2Socket.getSendfileData() != null
                        && nio2Socket.getSendfileData().fchannel != null
                        && nio2Socket.getSendfileData().fchannel.isOpen()) {
                    nio2Socket.getSendfileData().fchannel.close();
                }
            } catch (Exception ignore) {
            }
            countDownConnection();
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            if (log.isDebugEnabled()) log.error("",e);
        }
    }

    @Override
    protected Log getLog() {
        return log;
    }


    // --------------------------------------------------- Acceptor Inner Class

    /**
     * With NIO2, the main acceptor thread only initiates the initial accept
     * but periodically checks that the connector is still accepting (if not
     * it will attempt to start again). It is also responsible for periodic
     * checks of async timeouts, rather than use a dedicated thread for that.
     */
    protected class Acceptor extends AbstractEndpoint.Acceptor {

        @Override
        public void run() {

            int errorDelay = 0;

            // Loop until we receive a shutdown command
            while (running) {

                // Loop if endpoint is paused
                while (paused && running) {
                    state = AcceptorState.PAUSED;
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }

                if (!running) {
                    break;
                }
                state = AcceptorState.RUNNING;

                try {
                    //if we have reached max connections, wait
                    countUpOrAwaitConnection();

                    AsynchronousSocketChannel socket = null;
                    try {
                        // Accept the next incoming connection from the server
                        // socket
                        socket = serverSock.accept().get();
                    } catch (Exception e) {
                        countDownConnection();
                        if (running) {
                            // Introduce delay if necessary
                            errorDelay = handleExceptionWithDelay(errorDelay);
                            // re-throw
                            throw e;
                        } else {
                            break;
                        }
                    }
                    // Successful accept, reset the error delay
                    errorDelay = 0;

                    // Configure the socket
                    if (running && !paused) {
                        // Hand this socket off to an appropriate processor
                        if (!setSocketOptions(socket)) {
                            countDownConnection();
                            closeSocket(socket);
                        }
                    } else {
                        countDownConnection();
                        // Close socket right away
                        closeSocket(socket);
                    }
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    log.error(sm.getString("endpoint.accept.fail"), t);
                }
            }
            state = AcceptorState.ENDED;
        }

    }


    private void closeSocket(AsynchronousSocketChannel socket) {
        try {
            socket.close();
        } catch (IOException ioe) {
            if (log.isDebugEnabled()) {
                log.debug("", ioe);
            }
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

        private CompletionHandler<Integer, SocketWrapperBase<Nio2Channel>> awaitBytesHandler
                = new CompletionHandler<Integer, SocketWrapperBase<Nio2Channel>>() {

            @Override
            public void completed(Integer nBytes, SocketWrapperBase<Nio2Channel> attachment) {
                if (nBytes.intValue() < 0) {
                    failed(new ClosedChannelException(), attachment);
                    return;
                }
                getEndpoint().processSocket(attachment, SocketStatus.OPEN_READ, Nio2Endpoint.isInline());
            }

            @Override
            public void failed(Throwable exc, SocketWrapperBase<Nio2Channel> attachment) {
                getEndpoint().processSocket(attachment, SocketStatus.DISCONNECT, true);
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
                        if (attachment.keepAlive) {
                            if (!isInline()) {
                                awaitBytes();
                            } else {
                                attachment.doneInline = true;
                            }
                        } else {
                            if (!isInline()) {
                                getEndpoint().processSocket(Nio2SocketWrapper.this, SocketStatus.DISCONNECT, false);
                            } else {
                                attachment.doneInline = true;
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
                getSocket().write(buffer, getNio2WriteTimeout(), TimeUnit.MILLISECONDS, attachment, this);
            }

            @Override
            public void failed(Throwable exc, SendfileData attachment) {
                try {
                    attachment.fchannel.close();
                } catch (IOException e) {
                    // Ignore
                }
                if (!isInline()) {
                    getEndpoint().processSocket(Nio2SocketWrapper.this, SocketStatus.ERROR, false);
                } else {
                    attachment.doneInline = true;
                    attachment.error = true;
                }
            }
        };

        public Nio2SocketWrapper(Nio2Channel channel, Nio2Endpoint endpoint) {
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
                        getEndpoint().processSocket(attachment, SocketStatus.OPEN_READ, false);
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
                    Nio2SocketWrapper.this.setError(ioe);
                    if (exc instanceof AsynchronousCloseException) {
                        // Release here since there will be no
                        // notify/dispatch to do the release.
                        readPending.release();
                        // If already closed, don't call onError and close again
                        return;
                    }
                    getEndpoint().processSocket(attachment, SocketStatus.ERROR, true);
                }
            };

            this.writeCompletionHandler = new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer nBytes, ByteBuffer attachment) {
                    writeNotify = false;
                    synchronized (writeCompletionHandler) {
                        if (nBytes.intValue() < 0) {
                            failed(new EOFException(sm.getString("iob.failedwrite")), attachment);
                        } else if (Nio2SocketWrapper.this.bufferedWrites.size() > 0) {
                            nestedWriteCompletionCount.get().incrementAndGet();
                            // Continue writing data using a gathering write
                            ArrayList<ByteBuffer> arrayList = new ArrayList<>();
                            if (attachment.hasRemaining()) {
                                arrayList.add(attachment);
                            }
                            for (ByteBufferHolder buffer : Nio2SocketWrapper.this.bufferedWrites) {
                                buffer.flip();
                                arrayList.add(buffer.getBuf());
                            }
                            Nio2SocketWrapper.this.bufferedWrites.clear();
                            ByteBuffer[] array = arrayList.toArray(new ByteBuffer[arrayList.size()]);
                            Nio2SocketWrapper.this.getSocket().write(array, 0, array.length,
                                    Nio2SocketWrapper.this.getNio2WriteTimeout(), TimeUnit.MILLISECONDS,
                                    array, gatheringWriteCompletionHandler);
                            nestedWriteCompletionCount.get().decrementAndGet();
                        } else if (attachment.hasRemaining()) {
                            // Regular write
                            nestedWriteCompletionCount.get().incrementAndGet();
                            Nio2SocketWrapper.this.getSocket().write(attachment,
                                    Nio2SocketWrapper.this.getNio2WriteTimeout(),
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
                        endpoint.processSocket(Nio2SocketWrapper.this, SocketStatus.OPEN_WRITE, Nio2Endpoint.isInline());
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
                    Nio2SocketWrapper.this.setError(ioe);
                    writePending.release();
                    endpoint.processSocket(Nio2SocketWrapper.this, SocketStatus.OPEN_WRITE, true);
                }
            };

            gatheringWriteCompletionHandler = new CompletionHandler<Long, ByteBuffer[]>() {
                @Override
                public void completed(Long nBytes, ByteBuffer[] attachment) {
                    writeNotify = false;
                    synchronized (writeCompletionHandler) {
                        if (nBytes.longValue() < 0) {
                            failed(new EOFException(sm.getString("iob.failedwrite")), attachment);
                        } else if (Nio2SocketWrapper.this.bufferedWrites.size() > 0 || arrayHasData(attachment)) {
                            // Continue writing data
                            nestedWriteCompletionCount.get().incrementAndGet();
                            ArrayList<ByteBuffer> arrayList = new ArrayList<>();
                            for (ByteBuffer buffer : attachment) {
                                if (buffer.hasRemaining()) {
                                    arrayList.add(buffer);
                                }
                            }
                            for (ByteBufferHolder buffer : Nio2SocketWrapper.this.bufferedWrites) {
                                buffer.flip();
                                arrayList.add(buffer.getBuf());
                            }
                            Nio2SocketWrapper.this.bufferedWrites.clear();
                            ByteBuffer[] array = arrayList.toArray(new ByteBuffer[arrayList.size()]);
                            Nio2SocketWrapper.this.getSocket().write(array, 0, array.length,
                                    Nio2SocketWrapper.this.getNio2WriteTimeout(), TimeUnit.MILLISECONDS,
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
                        endpoint.processSocket(Nio2SocketWrapper.this, SocketStatus.OPEN_WRITE, Nio2Endpoint.isInline());
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
                    Nio2SocketWrapper.this.setError(ioe);
                    writePending.release();
                    endpoint.processSocket(Nio2SocketWrapper.this, SocketStatus.OPEN_WRITE, true);
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
            if (getError() != null) {
                throw getError();
            }

            if (log.isDebugEnabled()) {
                log.debug("Socket: [" + this + "], block: [" + block + "], length: [" + len + "]");
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

            if (socketBufferHandler == null) {
                throw new IOException(sm.getString("socket.closed"));
            }
            socketBufferHandler.configureReadBufferForRead();

            int remaining = socketBufferHandler.getReadBuffer().remaining();

            // Is there enough data in the read buffer to satisfy this request?
            if (remaining >= len) {
                socketBufferHandler.getReadBuffer().get(b, off, len);
                if (log.isDebugEnabled()) {
                    log.debug("Socket: [" + this + "], Read from buffer: [" + len + "]");
                }
                // No read is going to take place so release here.
                readPending.release();
                return len;
            }

            // Copy what data there is in the read buffer to the byte array
            if (remaining > 0) {
                socketBufferHandler.getReadBuffer().get(b, off, remaining);
                // This may be sufficient to complete the request and we
                // don't want to trigger another read since if there is no
                // more data to read and this request takes a while to
                // process the read will timeout triggering an error.
                readPending.release();
                return remaining;
            }

            synchronized (readCompletionHandler) {
                // Fill the read buffer as best we can.
                int nRead = fillReadBuffer(block);

                // Fill as much of the remaining byte array as possible with the
                // data that was just read
                if (nRead > 0) {
                    socketBufferHandler.configureReadBufferForRead();
                    if (nRead > len) {
                        socketBufferHandler.getReadBuffer().get(b, off, len);
                    } else {
                        socketBufferHandler.getReadBuffer().get(b, off, nRead);
                    }
                } else if (nRead == 0 && !block) {
                    readInterest = true;
                } else if (nRead == -1) {
                    return -1;
                }

                if (log.isDebugEnabled()) {
                    log.debug("Socket: [" + this + "], Read: [" + nRead + "]");
                }
                return nRead;
            }
        }


        @Override
        public void close() throws IOException {
            Nio2Channel socket = getSocket();
            if (socket != null) {
                socket.close();
            }
        }

        /**
         * Internal state tracker for scatter/gather operations.
         */
        private class OperationState<A> {
            private final ByteBuffer[] buffers;
            private final int offset;
            private final int length;
            private final A attachment;
            private final long timeout;
            private final TimeUnit unit;
            private final CompletionCheck check;
            private final CompletionHandler<Long, ? super A> handler;
            private OperationState(ByteBuffer[] buffers, int offset, int length,
                    long timeout, TimeUnit unit, A attachment, CompletionCheck check,
                    CompletionHandler<Long, ? super A> handler) {
                this.buffers = buffers;
                this.offset = offset;
                this.length = length;
                this.timeout = timeout;
                this.unit = unit;
                this.attachment = attachment;
                this.check = check;
                this.handler = handler;
            }
            private long nBytes = 0;
            private CompletionState state = CompletionState.PENDING;
        }

        private class ScatterReadCompletionHandler<A> implements CompletionHandler<Long, OperationState<A>> {
            @Override
            public void completed(Long nBytes, OperationState<A> state) {
                if (nBytes.intValue() < 0) {
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
                        readPending.release();
                        state.state = currentState;
                        if (completion) {
                            state.handler.completed(Long.valueOf(state.nBytes), state.attachment);
                        }
                    } else {
                        getSocket().read(state.buffers, state.offset, state.length,
                                state.timeout, state.unit, state, this);
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
                Nio2SocketWrapper.this.setError(ioe);
                readPending.release();
                if (exc instanceof AsynchronousCloseException) {
                    // If already closed, don't call onError and close again
                    return;
                }
                state.state = Nio2Endpoint.isInline() ? CompletionState.INLINE : CompletionState.DONE;
                state.handler.failed(ioe, state.attachment);
            }
        }

        private class GatherWriteCompletionHandler<A> implements CompletionHandler<Long, OperationState<A>> {
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
                        writePending.release();
                        state.state = currentState;
                        if (completion) {
                            state.handler.completed(Long.valueOf(state.nBytes), state.attachment);
                        }
                    } else {
                        getSocket().write(state.buffers, state.offset, state.length,
                                state.timeout, state.unit, state, this);
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
                Nio2SocketWrapper.this.setError(ioe);
                writePending.release();
                state.state = Nio2Endpoint.isInline() ? CompletionState.INLINE : CompletionState.DONE;
                state.handler.failed(ioe, state.attachment);
            }
        }

        @Override
        public <A> CompletionState read(ByteBuffer[] dsts, int offset, int length,
                boolean block, long timeout, TimeUnit unit, A attachment,
                CompletionCheck check, CompletionHandler<Long, ? super A> handler) {
            OperationState<A> state = new OperationState<>(dsts, offset, length, timeout, unit, attachment, check, handler);
            try {
                if ((!block && readPending.tryAcquire()) || (block && readPending.tryAcquire(timeout, unit))) {
                    Nio2Endpoint.startInline();
                    getSocket().read(dsts, offset, length, timeout, unit, state, new ScatterReadCompletionHandler<>());
                    Nio2Endpoint.endInline();
                } else {
                    throw new ReadPendingException();
                }
            } catch (InterruptedException e) {
                handler.failed(e, attachment);
            }
            return state.state;
        }

        @Override
        public boolean isWritePending() {
            synchronized (writeCompletionHandler) {
                return writePending.availablePermits() == 0;
            }
        }

        @Override
        public <A> CompletionState write(ByteBuffer[] srcs, int offset, int length,
                boolean block, long timeout, TimeUnit unit, A attachment,
                CompletionCheck check, CompletionHandler<Long, ? super A> handler) {
            OperationState<A> state = new OperationState<>(srcs, offset, length, timeout, unit, attachment, check, handler);
            try {
                if ((!block && writePending.tryAcquire()) || (block && writePending.tryAcquire(timeout, unit))) {
                    Nio2Endpoint.startInline();
                    getSocket().write(srcs, offset, length, timeout, unit, state, new GatherWriteCompletionHandler<>());
                    Nio2Endpoint.endInline();
                } else {
                    throw new WritePendingException();
                }
            } catch (InterruptedException e) {
                handler.failed(e, attachment);
            }
            return state.state;
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
            int nRead = 0;
            if (block) {
                try {
                    nRead = getSocket().read(socketBufferHandler.getReadBuffer()).get(
                            getNio2ReadTimeout(), TimeUnit.MILLISECONDS).intValue();
                    // Blocking read so need to release here since there will
                    // not be a callback to a completion handler.
                    readPending.release();
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof IOException) {
                        throw (IOException) e.getCause();
                    } else {
                        throw new IOException(e);
                    }
                } catch (InterruptedException e) {
                    throw new IOException(e);
                } catch (TimeoutException e) {
                    SocketTimeoutException ex = new SocketTimeoutException();
                    throw ex;
                }
            } else {
                Nio2Endpoint.startInline();
                getSocket().read(socketBufferHandler.getReadBuffer(), getNio2ReadTimeout(),
                        TimeUnit.MILLISECONDS, this, readCompletionHandler);
                Nio2Endpoint.endInline();
                if (readPending.availablePermits() == 1) {
                    nRead = socketBufferHandler.getReadBuffer().position();
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
         * @param block Ignored since this method is only called in the
         *              blocking case
         */
        @Override
        protected void doWriteInternal(boolean block) throws IOException {
            try {
                socketBufferHandler.configureWriteBufferForRead();
                do {
                    if (getSocket().write(socketBufferHandler.getWriteBuffer()).get(
                            getNio2WriteTimeout(), TimeUnit.MILLISECONDS).intValue() < 0) {
                        throw new EOFException(sm.getString("iob.failedwrite"));
                    }
                } while (socketBufferHandler.getWriteBuffer().hasRemaining());
            } catch (ExecutionException e) {
                if (e.getCause() instanceof IOException) {
                    throw (IOException) e.getCause();
                } else {
                    throw new IOException(e);
                }
            } catch (InterruptedException e) {
                throw new IOException(e);
            } catch (TimeoutException e) {
                throw new SocketTimeoutException();
            }
        }


        @Override
        protected void flushBlocking() throws IOException {
            // Before doing a blocking flush, make sure that any pending non
            // blocking write has completed.
            try {
                if (writePending.tryAcquire(getNio2WriteTimeout(), TimeUnit.MILLISECONDS)) {
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
        protected boolean flushNonBlocking() {
            return flushNonBlocking(false);
        }

        private boolean flushNonBlocking(boolean hasPermit) {
            synchronized (writeCompletionHandler) {
                if (hasPermit || writePending.tryAcquire()) {
                    socketBufferHandler.configureWriteBufferForRead();
                    if (bufferedWrites.size() > 0) {
                        // Gathering write of the main buffer plus all leftovers
                        ArrayList<ByteBuffer> arrayList = new ArrayList<>();
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
                        getSocket().write(array, 0, array.length, getNio2WriteTimeout(),
                                TimeUnit.MILLISECONDS, array, gatheringWriteCompletionHandler);
                        Nio2Endpoint.endInline();
                    } else if (socketBufferHandler.getWriteBuffer().hasRemaining()) {
                        // Regular write
                        Nio2Endpoint.startInline();
                        getSocket().write(socketBufferHandler.getWriteBuffer(), getNio2WriteTimeout(),
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
                    getEndpoint().processSocket(this, SocketStatus.OPEN_WRITE, true);
                }
            }
        }


        public void awaitBytes() {
            if (getSocket() == null) {
                return;
            }
            // NO-OP is there is already a read in progress.
            if (readPending.tryAcquire()) {
                getSocket().getBufHandler().configureReadBufferForWrite();
                Nio2Endpoint.startInline();
                getSocket().read(getSocket().getBufHandler().getReadBuffer(),
                        getNio2ReadTimeout(), TimeUnit.MILLISECONDS, this, awaitBytesHandler);
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
                getSocket().write(buffer, getNio2WriteTimeout(), TimeUnit.MILLISECONDS,
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


        private long getNio2ReadTimeout() {
            long readTimeout = getReadTimeout();
            if (readTimeout > 0) {
                return readTimeout;
            }
            // NIO2 can't do infinite timeout so use Long.MAX_VALUE
            return Long.MAX_VALUE;
        }


        private long getNio2WriteTimeout() {
            long writeTimeout = getWriteTimeout();
            if (writeTimeout > 0) {
                return writeTimeout;
            }
            // NIO2 can't do infinite timeout so use Long.MAX_VALUE
            return Long.MAX_VALUE;
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
        public void doClientAuth(SSLSupport sslSupport) {
            SecureNio2Channel sslChannel = (SecureNio2Channel) getSocket();
            SSLEngine engine = sslChannel.getSslEngine();
            if (!engine.getNeedClientAuth()) {
                // Need to re-negotiate SSL connection
                engine.setNeedClientAuth(true);
                try {
                    sslChannel.rehandshake();
                    ((JSSESupport) sslSupport).setSession(engine.getSession());
                } catch (IOException ioe) {
                    log.warn(sm.getString("http11processor.socket.sslreneg"), ioe);
                }
            }
        }
    }


    // ------------------------------------------------ Handler Inner Interface

    /**
     * Bare bones interface used for socket processing. Per thread data is to be
     * stored in the ThreadWithAttributes extra folders, or alternately in
     * thread local fields.
     */
    public interface Handler extends AbstractEndpoint.Handler<Nio2Channel> {
        public void closeAll();
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
    protected class SocketProcessor implements Runnable {

        private SocketWrapperBase<Nio2Channel> socket = null;
        private SocketStatus status = null;

        public SocketProcessor(SocketWrapperBase<Nio2Channel> socket, SocketStatus status) {
            reset(socket,status);
        }

        public void reset(SocketWrapperBase<Nio2Channel> socket, SocketStatus status) {
            this.socket = socket;
            this.status = status;
        }

        @Override
        public void run() {
            synchronized (socket) {
                if (SocketStatus.OPEN_WRITE != status) {
                    // Anything other than OPEN_WRITE is a genuine read or an
                    // error condition so for all of those release the semaphore
                    ((Nio2SocketWrapper) socket).releaseReadPending();
                }
                boolean launch = false;
                try {
                    int handshake = -1;

                    try {
                        if (socket.getSocket() != null) {
                            // For STOP there is no point trying to handshake as the
                            // Poller has been stopped.
                            if (socket.getSocket().isHandshakeComplete() ||
                                    status == SocketStatus.STOP) {
                                handshake = 0;
                            } else {
                                handshake = socket.getSocket().handshake();
                                // The handshake process reads/writes from/to the
                                // socket. status may therefore be OPEN_WRITE once
                                // the handshake completes. However, the handshake
                                // happens when the socket is opened so the status
                                // must always be OPEN_READ after it completes. It
                                // is OK to always set this as it is only used if
                                // the handshake completes.
                                status = SocketStatus.OPEN_READ;
                            }
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
                        if (status == null) {
                            state = handler.process(socket, SocketStatus.OPEN_READ);
                        } else {
                            state = handler.process(socket, status);
                        }
                        if (state == SocketState.CLOSED) {
                            // Close socket and pool
                            closeSocket(socket);
                            if (running && !paused) {
                                if (!nioChannels.push(socket.getSocket())) {
                                    socket.getSocket().free();
                                }
                            }
                        } else if (state == Handler.SocketState.LONG) {
                            if (socket.isAsync()) {
                                waitingRequests.add(socket);
                            }
                        } else if (state == SocketState.UPGRADING) {
                            socket.setKeptAlive(true);
                            launch = true;
                        }
                    } else if (handshake == -1 ) {
                        closeSocket(socket);
                        if (running && !paused) {
                            if (!nioChannels.push(socket.getSocket())) {
                                socket.getSocket().free();
                            }
                        }
                    }
                } catch (VirtualMachineError vme) {
                    ExceptionUtils.handleThrowable(vme);
                } catch (Throwable t) {
                    log.error(sm.getString("endpoint.processing.fail"), t);
                    if (socket != null) {
                        closeSocket(socket);
                    }
                } finally {
                    if (launch) {
                        try {
                            getExecutor().execute(new SocketProcessor(socket, SocketStatus.OPEN_READ));
                        } catch (NullPointerException npe) {
                            if (running) {
                                log.error(sm.getString("endpoint.launch.fail"),
                                        npe);
                            }
                        }
                    }
                    socket = null;
                    status = null;
                    //return to cache
                    if (running && !paused) {
                        processorCache.push(this);
                    }
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
