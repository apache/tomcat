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

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.X509KeyManager;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.ByteBufferHolder;
import org.apache.tomcat.util.collections.SynchronizedStack;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.jsse.JSSESupport;
import org.apache.tomcat.util.net.jsse.NioX509KeyManager;

/**
 * NIO2 endpoint.
 */
public class Nio2Endpoint extends AbstractEndpoint<Nio2Channel> {


    // -------------------------------------------------------------- Constants


    private static final Log log = LogFactory.getLog(Nio2Endpoint.class);


    // ----------------------------------------------------------------- Fields

    /**
     * Server socket "pointer".
     */
    private AsynchronousServerSocketChannel serverSock = null;

    /**
     * The size of the OOM parachute.
     */
    private int oomParachute = 1024*1024;

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
     * The oom parachute, when an OOM error happens,
     * will release the data, giving the JVM instantly
     * a chunk of data to be able to recover with.
     */
    private byte[] oomParachuteData = null;

    /**
     * Make sure this string has already been allocated
     */
    private static final String oomParachuteMsg =
        "SEVERE:Memory usage is low, parachute is non existent, your system may start failing.";

    /**
     * Keep track of OOM warning messages.
     */
    private long lastParachuteCheck = System.currentTimeMillis();

    /**
     * Cache for SocketProcessor objects
     */
    private SynchronizedStack<SocketProcessor> processorCache;

    /**
     * Cache for socket wrapper objects
     */
    private SynchronizedStack<Nio2SocketWrapper> socketWrapperCache;

    /**
     * Bytebuffer cache, each channel holds a set of buffers (two, except for SSL holds four)
     */
    private SynchronizedStack<Nio2Channel> nioChannels;


    // ------------------------------------------------------------- Properties


    /**
     * Use the object caches to reduce GC at the expense of additional memory use.
     */
    private boolean useCaches = false;
    public void setUseCaches(boolean useCaches) { this.useCaches = useCaches; }
    public boolean getUseCaches() { return useCaches; }


    /**
     * Handling of accepted sockets.
     */
    private Handler handler = null;
    public void setHandler(Handler handler ) { this.handler = handler; }
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

    public void setOomParachute(int oomParachute) {
        this.oomParachute = oomParachute;
    }

    public void setOomParachuteData(byte[] oomParachuteData) {
        this.oomParachuteData = oomParachuteData;
    }


    private SSLImplementation sslImplementation = null;
    private SSLContext sslContext = null;
    public SSLContext getSSLContext() { return sslContext;}
    public void setSSLContext(SSLContext c) { sslContext = c;}
    private String[] enabledCiphers;
    private String[] enabledProtocols;

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


    public SSLImplementation getSslImplementation() {
        return sslImplementation;
    }


    @Override
    public String[] getCiphersUsed() {
        return enabledCiphers;
    }


    // --------------------------------------------------------- OOM Parachute Methods

    protected void checkParachute() {
        boolean para = reclaimParachute(false);
        if (!para && (System.currentTimeMillis()-lastParachuteCheck)>10000) {
            try {
                log.fatal(oomParachuteMsg);
            }catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                System.err.println(oomParachuteMsg);
            }
            lastParachuteCheck = System.currentTimeMillis();
        }
    }

    protected boolean reclaimParachute(boolean force) {
        if ( oomParachuteData != null ) return true;
        if ( oomParachute > 0 && ( force || (Runtime.getRuntime().freeMemory() > (oomParachute*2))) )
            oomParachuteData = new byte[oomParachute];
        return oomParachuteData != null;
    }

    protected void releaseCaches() {
        if (useCaches) {
            this.socketWrapperCache.clear();
            this.nioChannels.clear();
            this.processorCache.clear();
        }
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
        if (acceptorThreadCount == 0) {
            // NIO2 does not allow any form of IO concurrency
            acceptorThreadCount = 1;
        }

        // Initialize SSL if needed
        if (isSSLEnabled()) {
            sslImplementation = SSLImplementation.getInstance(getSslImplementationName());
            SSLUtil sslUtil = sslImplementation.getSSLUtil(this);

            sslContext = sslUtil.createSSLContext();
            sslContext.init(wrap(sslUtil.getKeyManagers()),
                    sslUtil.getTrustManagers(), null);

            SSLSessionContext sessionContext =
                sslContext.getServerSessionContext();
            if (sessionContext != null) {
                sslUtil.configureSessionContext(sessionContext);
            }
            // Determine which cipher suites and protocols to enable
            enabledCiphers = sslUtil.getEnableableCiphers(sslContext);
            enabledProtocols = sslUtil.getEnableableProtocols(sslContext);
        }

        if (oomParachute>0) reclaimParachute(true);
    }

    public KeyManager[] wrap(KeyManager[] managers) {
        if (managers==null) return null;
        KeyManager[] result = new KeyManager[managers.length];
        for (int i=0; i<result.length; i++) {
            if (managers[i] instanceof X509KeyManager && getKeyAlias()!=null) {
                result[i] = new NioX509KeyManager((X509KeyManager)managers[i],getKeyAlias());
            } else {
                result[i] = managers[i];
            }
        }
        return result;
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

            if (useCaches) {
                processorCache = new SynchronizedStack<>(SynchronizedStack.DEFAULT_SIZE,
                        socketProperties.getProcessorCache());
                socketWrapperCache = new SynchronizedStack<>(SynchronizedStack.DEFAULT_SIZE,
                        socketProperties.getSocketWrapperCache());
                nioChannels = new SynchronizedStack<>(SynchronizedStack.DEFAULT_SIZE,
                        socketProperties.getBufferPool());
            }

            sslImplementation = SSLImplementation.getInstance(getSslImplementationName());

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
                    // Then close all active connections if any remains
                    try {
                        handler.closeAll();
                    } catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                    } finally {
                        allClosed = true;
                    }
                }
            });
            if (useCaches) {
                socketWrapperCache.clear();
                nioChannels.clear();
                processorCache.clear();
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
        // Close server socket
        serverSock.close();
        serverSock = null;
        sslContext = null;
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

    public int getOomParachute() {
        return oomParachute;
    }

    public byte[] getOomParachuteData() {
        return oomParachuteData;
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

            Nio2Channel channel = (useCaches) ? nioChannels.pop() : null;
            if (channel == null) {
                // SSL setup
                if (sslContext != null) {
                    SSLEngine engine = createSSLEngine();
                    int appBufferSize = engine.getSession().getApplicationBufferSize();
                    SocketBufferHandler bufhandler = new SocketBufferHandler(
                            Math.max(appBufferSize, socketProperties.getAppReadBufSize()),
                            Math.max(appBufferSize, socketProperties.getAppWriteBufSize()),
                            socketProperties.getDirectBuffer());
                    channel = new SecureNio2Channel(engine, bufhandler, this);
                } else {
                    SocketBufferHandler bufhandler = new SocketBufferHandler(
                            socketProperties.getAppReadBufSize(),
                            socketProperties.getAppWriteBufSize(),
                            socketProperties.getDirectBuffer());
                    channel = new Nio2Channel(bufhandler);
                }
            } else {
                if (sslContext != null) {
                    SSLEngine engine = createSSLEngine();
                    ((SecureNio2Channel) channel).setSSLEngine(engine);
                }
            }
            Nio2SocketWrapper socketWrapper = (useCaches) ? socketWrapperCache.pop() : null;
            if (socketWrapper == null) {
                socketWrapper = new Nio2SocketWrapper(channel, this);
            }
            channel.reset(socket, socketWrapper);
            socketWrapper.reset(channel, getSocketProperties().getSoTimeout());
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

    protected SSLEngine createSSLEngine() {
        SSLEngine engine = sslContext.createSSLEngine();
        if ("false".equals(getClientAuth())) {
            engine.setNeedClientAuth(false);
            engine.setWantClientAuth(false);
        } else if ("true".equals(getClientAuth()) || "yes".equals(getClientAuth())){
            engine.setNeedClientAuth(true);
        } else if ("want".equals(getClientAuth())) {
            engine.setWantClientAuth(true);
        }
        engine.setUseClientMode(false);
        engine.setEnabledCipherSuites(enabledCiphers);
        engine.setEnabledProtocols(enabledProtocols);

        configureUseServerCipherSuitesOrder(engine);

        return engine;
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
            SocketProcessor sc = (useCaches) ? processorCache.pop() : null;
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
            nio2Socket.reset(null, -1);
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
        private final Semaphore writePending = new Semaphore(1); // Guarded by writeCompletionHandler
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
                readPending.release();
                getEndpoint().processSocket(attachment, SocketStatus.OPEN_READ, Nio2Endpoint.isInline());
            }

            @Override
            public void failed(Throwable exc, SocketWrapperBase<Nio2Channel> attachment) {
                readPending.release();
                getEndpoint().processSocket(attachment, SocketStatus.DISCONNECT, true);
            }
        };

        public Nio2SocketWrapper(Nio2Channel channel, Nio2Endpoint endpoint) {
            super(channel, endpoint);

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
                            readPending.release();
                            if (readInterest && !Nio2Endpoint.isInline()) {
                                readInterest = false;
                                notify = true;
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
                    readPending.release();
                    if (exc instanceof AsynchronousCloseException) {
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

        @Override
        public void reset(Nio2Channel channel, long soTimeout) {
            if (log.isDebugEnabled()) {
                log.debug("Calling [" + this + "].reset([" + channel + "],[" + soTimeout + "])",
                        new Exception());
            }
            super.reset(channel, soTimeout);
            sendfileData = null;
        }


        @Override
        protected void resetSocketBufferHandler(Nio2Channel socket) {
            if (socket == null) {
                socketBufferHandler = null;
            } else {
                socketBufferHandler = socket.getBufHandler();
            }
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
        public void unRead(ByteBuffer returnedInput) {
            if (returnedInput != null) {
                socketBufferHandler.configureReadBufferForWrite();
                socketBufferHandler.getReadBuffer().put(returnedInput);
            }
        }


        @Override
        public void close() throws IOException {
            Nio2Channel socket = getSocket();
            if (socket != null) {
                socket.close();
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
            int nRead = 0;
            if (block) {
                try {
                    nRead = getSocket().read(socketBufferHandler.getReadBuffer()).get(
                            getNio2ReadTimeout(), TimeUnit.MILLISECONDS).intValue();
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
                synchronized(writeCompletionHandler) {
                    if (writePending.tryAcquire(getNio2WriteTimeout(), TimeUnit.MILLISECONDS)) {
                        writePending.release();
                    } else {
                        throw new SocketTimeoutException();
                    }
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


        @Override
        public void registerReadInterest() {
            synchronized (readCompletionHandler) {
                if (readPending.availablePermits() == 0) {
                    readInterest = true;
                } else {
                    // If no read is pending, notify
                    getEndpoint().processSocket(this, SocketStatus.OPEN_READ, true);
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
            setSendfileData((SendfileData) sendfileData);
            return ((Nio2Endpoint) getEndpoint()).processSendfile(this);
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
        public void release(SocketWrapperBase<Nio2Channel> socket);
        public void closeAll();
    }

    public void addTimeout(SocketWrapperBase<Nio2Channel> socket) {
        waitingRequests.add(socket);
    }

    public boolean removeTimeout(SocketWrapperBase<Nio2Channel> socket) {
        return waitingRequests.remove(socket);
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

    private CompletionHandler<Integer, SendfileData> sendfile = new CompletionHandler<Integer, SendfileData>() {

        @Override
        public void completed(Integer nWrite, SendfileData attachment) {
            if (nWrite.intValue() < 0) { // Reach the end of stream
                failed(new EOFException(), attachment);
                return;
            }
            // TODO: Lots of direct access to the socketWriteBuffer.
            //       Refactor to use socketBufferHandler
            attachment.pos += nWrite.intValue();
            if (!attachment.buffer.hasRemaining()) {
                if (attachment.length <= 0) {
                    // All data has now been written
                    attachment.socket.setSendfileData(null);
                    try {
                        attachment.fchannel.close();
                    } catch (IOException e) {
                        // Ignore
                    }
                    if (attachment.keepAlive) {
                        if (!isInline()) {
                            attachment.socket.awaitBytes();
                        } else {
                            attachment.doneInline = true;
                        }
                    } else {
                        if (!isInline()) {
                            processSocket(attachment.socket, SocketStatus.DISCONNECT, false);
                        } else {
                            attachment.doneInline = true;
                        }
                    }
                    return;
                } else {
                    attachment.buffer.clear();
                    int nRead = -1;
                    try {
                        nRead = attachment.fchannel.read(attachment.buffer);
                    } catch (IOException e) {
                        failed(e, attachment);
                        return;
                    }
                    if (nRead > 0) {
                        attachment.buffer.flip();
                        if (attachment.length < attachment.buffer.remaining()) {
                            attachment.buffer.limit(attachment.buffer.limit() - attachment.buffer.remaining() + (int) attachment.length);
                        }
                        attachment.length -= nRead;
                    } else {
                        failed(new EOFException(), attachment);
                        return;
                    }
                }
            }
            attachment.socket.getSocket().write(attachment.buffer,
                    attachment.socket.getNio2WriteTimeout(), TimeUnit.MILLISECONDS,
                    attachment, this);
        }

        @Override
        public void failed(Throwable exc, SendfileData attachment) {
            try {
                attachment.fchannel.close();
            } catch (IOException e) {
                // Ignore
            }
            if (!isInline()) {
                processSocket(attachment.socket, SocketStatus.ERROR, false);
            } else {
                attachment.doneInline = true;
                attachment.error = true;
            }
        }
    };

    public SendfileState processSendfile(Nio2SocketWrapper socket) {

        // Configure the send file data
        SendfileData data = socket.getSendfileData();
        if (data.fchannel == null || !data.fchannel.isOpen()) {
            java.nio.file.Path path = new File(data.fileName).toPath();
            try {
                data.fchannel = java.nio.channels.FileChannel
                        .open(path, StandardOpenOption.READ).position(data.pos);
            } catch (IOException e) {
                return SendfileState.ERROR;
            }
        }
        socket.getSocket().getBufHandler().configureWriteBufferForWrite();
        ByteBuffer buffer = socket.getSocket().getBufHandler().getWriteBuffer();
        int nRead = -1;
        try {
            nRead = data.fchannel.read(buffer);
        } catch (IOException e1) {
            return SendfileState.ERROR;
        }

        if (nRead >= 0) {
            data.socket = socket;
            data.buffer = buffer;
            data.length -= nRead;
            socket.getSocket().getBufHandler().configureWriteBufferForRead();
            Nio2Endpoint.startInline();
            socket.getSocket().write(buffer, socket.getNio2WriteTimeout(), TimeUnit.MILLISECONDS,
                    data, sendfile);
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
            // Upgraded connections using an internal upgrade handler are
            // allowed concurrent read/writes
            if (socket.isInternalUpgrade() && SocketStatus.OPEN_WRITE == status) {
                synchronized (socket.getWriteThreadLock()) {
                    doRun();
                }
            } else {
                synchronized (socket) {
                    doRun();
                }
            }
        }

        private void doRun() {
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
                        if (useCaches && running && !paused) {
                            nioChannels.push(socket.getSocket());
                            socketWrapperCache.push((Nio2SocketWrapper) socket);
                        }
                    } else if (state == SocketState.UPGRADING) {
                        socket.setKeptAlive(true);
                        launch = true;
                    }
                } else if (handshake == -1 ) {
                    closeSocket(socket);
                    if (useCaches && running && !paused) {
                        nioChannels.push(socket.getSocket());
                        socketWrapperCache.push(((Nio2SocketWrapper) socket));
                    }
                }
            } catch (OutOfMemoryError oom) {
                try {
                    oomParachuteData = null;
                    log.error("", oom);
                    closeSocket(socket);
                    releaseCaches();
                } catch (Throwable oomt) {
                    try {
                        System.err.println(oomParachuteMsg);
                        oomt.printStackTrace();
                    } catch (Throwable letsHopeWeDontGetHere){
                        ExceptionUtils.handleThrowable(letsHopeWeDontGetHere);
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
                if (useCaches && running && !paused) {
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
        protected FileChannel fchannel;
        // Internal use only
        private Nio2SocketWrapper socket;
        private ByteBuffer buffer;
        private boolean doneInline = false;
        private boolean error = false;

        public SendfileData(String filename, long pos, long length) {
            super(filename, pos, length);
        }
    }
}
