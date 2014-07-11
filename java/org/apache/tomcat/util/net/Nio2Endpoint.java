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
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.X509KeyManager;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.collections.SynchronizedStack;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SecureNio2Channel.ApplicationBufferHandler;
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
     * use send file
     */
    private boolean useSendfile = true;

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
     * Priority of the poller threads.
     */
    private int pollerThreadPriority = Thread.NORM_PRIORITY;
    public void setPollerThreadPriority(int pollerThreadPriority) { this.pollerThreadPriority = pollerThreadPriority; }
    public int getPollerThreadPriority() { return pollerThreadPriority; }


    /**
     * Handling of accepted sockets.
     */
    private Handler handler = null;
    public void setHandler(Handler handler ) { this.handler = handler; }
    public Handler getHandler() { return handler; }


    /**
     * Allow comet request handling.
     */
    private boolean useComet = true;
    public void setUseComet(boolean useComet) { this.useComet = useComet; }
    @Override
    public boolean getUseComet() { return useComet; }
    @Override
    public boolean getUseCometTimeout() { return getUseComet(); }
    @Override
    public boolean getUsePolling() { return true; } // Always supported

    public void setSocketProperties(SocketProperties socketProperties) {
        this.socketProperties = socketProperties;
    }

    public void setUseSendfile(boolean useSendfile) {
        this.useSendfile = useSendfile;
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
                if (sa != null && sa instanceof InetSocketAddress) {
                    return ((InetSocketAddress) sa).getPort();
                } else {
                    return -1;
                }
            } catch (IOException e) {
                return -1;
            }
        }
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
     * Number of keepalive sockets.
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
            SSLUtil sslUtil = handler.getSslImplementation().getSSLUtil(this);

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
                    for (SocketWrapper<Nio2Channel> socket : waitingRequests) {
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

    @Override
    public boolean getUseSendfile() {
        return useSendfile;
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
                    NioBufferHandler bufhandler = new NioBufferHandler(
                            Math.max(appBufferSize, socketProperties.getAppReadBufSize()),
                            Math.max(appBufferSize, socketProperties.getAppWriteBufSize()),
                            socketProperties.getDirectBuffer());
                    channel = new SecureNio2Channel(engine, bufhandler, this);
                } else {
                    NioBufferHandler bufhandler = new NioBufferHandler(
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
                socketWrapper = new Nio2SocketWrapper(channel);
            }
            channel.reset(socket, socketWrapper);
            socketWrapper.reset(channel, getSocketProperties().getSoTimeout());
            socketWrapper.setKeepAliveLeft(Nio2Endpoint.this.getMaxKeepAliveRequests());
            socketWrapper.setSecure(isSSLEnabled());
            if (sslContext != null) {
                // Use the regular processing, as the first handshake needs to be done there
                processSocket(socketWrapper, SocketStatus.OPEN_READ, true);
            } else {
                // Wait until some bytes are available to start the real processing
                awaitBytes(socketWrapper);
            }
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

        handler.onCreateSSLEngine(engine);
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
    public void processSocket(SocketWrapper<Nio2Channel> socketWrapper,
            SocketStatus socketStatus, boolean dispatch) {
        processSocket0(socketWrapper, socketStatus, dispatch);
    }

    protected boolean processSocket0(SocketWrapper<Nio2Channel> socketWrapper, SocketStatus status, boolean dispatch) {
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

    public void closeSocket(SocketWrapper<Nio2Channel> socket, SocketStatus status) {
        if (socket == null) {
            return;
        }
        try {
            if (socket.isComet() && status != null) {
                socket.setComet(false);//to avoid a loop
                if (status == SocketStatus.TIMEOUT) {
                    if (processSocket0(socket, status, true)) {
                        return; // don't close on comet timeout
                    }
                } else {
                    // Don't dispatch if the lines below are canceling the key
                    processSocket0(socket, status, false);
                }
            }
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


    public static class Nio2SocketWrapper extends SocketWrapper<Nio2Channel> {

        private SendfileData sendfileData = null;
        private boolean upgradeInit = false;

        public Nio2SocketWrapper(Nio2Channel channel) {
            super(channel);
        }

        @Override
        public void reset(Nio2Channel channel, long soTimeout) {
            super.reset(channel, soTimeout);
            upgradeInit = false;
            sendfileData = null;
        }

        @Override
        public long getTimeout() {
            long timeout = super.getTimeout();
            return (timeout > 0) ? timeout : Long.MAX_VALUE;
        }

        @Override
        public void setUpgraded(boolean upgraded) {
            if (upgraded && !isUpgraded()) {
                upgradeInit = true;
            }
            super.setUpgraded(upgraded);
        }

        public boolean isUpgradeInit() {
            boolean value = upgradeInit;
            upgradeInit = false;
            return value;
        }

        public void setSendfileData(SendfileData sf) { this.sendfileData = sf; }
        public SendfileData getSendfileData() { return this.sendfileData; }

    }

    // ------------------------------------------------ Application Buffer Handler
    public static class NioBufferHandler implements ApplicationBufferHandler {
        private ByteBuffer readbuf = null;
        private ByteBuffer writebuf = null;

        public NioBufferHandler(int readsize, int writesize, boolean direct) {
            if ( direct ) {
                readbuf = ByteBuffer.allocateDirect(readsize);
                writebuf = ByteBuffer.allocateDirect(writesize);
            }else {
                readbuf = ByteBuffer.allocate(readsize);
                writebuf = ByteBuffer.allocate(writesize);
            }
        }

        @Override
        public ByteBuffer getReadBuffer() {return readbuf;}
        @Override
        public ByteBuffer getWriteBuffer() {return writebuf;}

    }

    // ------------------------------------------------ Handler Inner Interface


    /**
     * Bare bones interface used for socket processing. Per thread data is to be
     * stored in the ThreadWithAttributes extra folders, or alternately in
     * thread local fields.
     */
    public interface Handler extends AbstractEndpoint.Handler {
        public SocketState process(SocketWrapper<Nio2Channel> socket,
                SocketStatus status);
        public void release(SocketWrapper<Nio2Channel> socket);
        public void closeAll();
        public SSLImplementation getSslImplementation();
        public void onCreateSSLEngine(SSLEngine engine);
    }

    /**
     * The completion handler used for asynchronous read operations
     */
    private CompletionHandler<Integer, SocketWrapper<Nio2Channel>> awaitBytes
            = new CompletionHandler<Integer, SocketWrapper<Nio2Channel>>() {

        @Override
        public synchronized void completed(Integer nBytes, SocketWrapper<Nio2Channel> attachment) {
            if (nBytes.intValue() < 0) {
                failed(new ClosedChannelException(), attachment);
                return;
            }
            processSocket0(attachment, SocketStatus.OPEN_READ, true);
        }

        @Override
        public void failed(Throwable exc, SocketWrapper<Nio2Channel> attachment) {
            processSocket0(attachment, SocketStatus.DISCONNECT, true);
        }
    };

    public void addTimeout(SocketWrapper<Nio2Channel> socket) {
        waitingRequests.add(socket);
    }

    public boolean removeTimeout(SocketWrapper<Nio2Channel> socket) {
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

    public void awaitBytes(SocketWrapper<Nio2Channel> socket) {
        if (socket == null || socket.getSocket() == null) {
            return;
        }
        ByteBuffer byteBuffer = socket.getSocket().getBufHandler().getReadBuffer();
        byteBuffer.clear();
        socket.getSocket().read(byteBuffer, socket.getTimeout(),
               TimeUnit.MILLISECONDS, socket, awaitBytes);
    }

    public enum SendfileState {
        PENDING, DONE, ERROR
    }

    private CompletionHandler<Integer, SendfileData> sendfile = new CompletionHandler<Integer, SendfileData>() {

        @Override
        public void completed(Integer nWrite, SendfileData attachment) {
            if (nWrite.intValue() < 0) { // Reach the end of stream
                failed(new EOFException(), attachment);
                return;
            }
            attachment.pos += nWrite.intValue();
            if (!attachment.buffer.hasRemaining()) {
                if (attachment.length <= 0) {
                    // All data has now been written
                    attachment.socket.setSendfileData(null);
                    attachment.buffer.clear();
                    try {
                        attachment.fchannel.close();
                    } catch (IOException e) {
                        // Ignore
                    }
                    if (attachment.keepAlive) {
                        if (!isInline()) {
                            awaitBytes(attachment.socket);
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
            attachment.socket.getSocket().write(attachment.buffer, attachment.socket.getTimeout(),
                    TimeUnit.MILLISECONDS, attachment, this);
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
        ByteBuffer buffer = socket.getSocket().getBufHandler().getWriteBuffer();
        buffer.clear();
        int nRead = -1;
        try {
            nRead = data.fchannel.read(buffer);
        } catch (IOException e1) {
            return SendfileState.ERROR;
        }

        if (nRead >= 0) {
            buffer.flip();
            data.socket = socket;
            data.buffer = buffer;
            data.length -= nRead;
            startInline();
            try {
                socket.getSocket().write(buffer, socket.getTimeout(), TimeUnit.MILLISECONDS,
                        data, sendfile);
            } finally {
                endInline();
            }
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

        private SocketWrapper<Nio2Channel> socket = null;
        private SocketStatus status = null;

        public SocketProcessor(SocketWrapper<Nio2Channel> socket, SocketStatus status) {
            reset(socket,status);
        }

        public void reset(SocketWrapper<Nio2Channel> socket, SocketStatus status) {
            this.socket = socket;
            this.status = status;
        }

        @Override
        public void run() {
            // Upgraded connections need to allow multiple threads to access the
            // connection at the same time to enable blocking IO to be used when
            // NIO has been configured
            if (socket.isUpgraded() &&
                    SocketStatus.OPEN_WRITE == status) {
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
                        socket.setComet(false);
                        closeSocket(socket, SocketStatus.ERROR);
                        if (useCaches && running && !paused) {
                            nioChannels.push(socket.getSocket());
                        }
                        if (useCaches && running && !paused && socket != null) {
                            socketWrapperCache.push((Nio2SocketWrapper) socket);
                        }
                    } else if (state == SocketState.UPGRADING) {
                        socket.setKeptAlive(true);
                        socket.access();
                        launch = true;
                    }
                } else if (handshake == -1 ) {
                    closeSocket(socket, SocketStatus.DISCONNECT);
                    if (useCaches && running && !paused) {
                        nioChannels.push(socket.getSocket());
                    }
                    if (useCaches && running && !paused && socket != null) {
                        socketWrapperCache.push(((Nio2SocketWrapper) socket));
                    }
                }
            } catch (OutOfMemoryError oom) {
                try {
                    oomParachuteData = null;
                    log.error("", oom);
                    closeSocket(socket, SocketStatus.ERROR);
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
                    closeSocket(socket, SocketStatus.ERROR);
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
    public static class SendfileData {
        // File
        public String fileName;
        public FileChannel fchannel;
        public long pos;
        public long length;
        // KeepAlive flag
        public boolean keepAlive;
        // Internal use only
        private Nio2SocketWrapper socket;
        private ByteBuffer buffer;
        private boolean doneInline = false;
        private boolean error = false;
    }
}
