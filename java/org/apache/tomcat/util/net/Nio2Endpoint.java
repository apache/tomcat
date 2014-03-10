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

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadPendingException;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
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


    public static final int OP_REGISTER = 0x100; //register interest op
    public static final int OP_CALLBACK = 0x200; //callback interest op
    public static final int OP_READ = 0x400; //read interest op
    public static final int OP_WRITE = 0x800; //write interest op

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
    private final SynchronizedStack<SocketProcessor> processorCache =
            new SynchronizedStack<>(SynchronizedStack.DEFAULT_SIZE,
                    socketProperties.getProcessorCache());

    /**
     * Cache for key attachment objects
     */
    private final SynchronizedStack<Nio2SocketWrapper> socketWrapperCache =
            new SynchronizedStack<>(SynchronizedStack.DEFAULT_SIZE,
                    socketProperties.getSocketWrapperCache());

    /**
     * Bytebuffer cache, each channel holds a set of buffers (two, except for SSL holds four)
     */
    private final SynchronizedStack<Nio2Channel> nioChannels =
            new SynchronizedStack<>(SynchronizedStack.DEFAULT_SIZE,
                    socketProperties.getBufferPoolSize());


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
        return 0;
        // FIXME: would need some specific statistics gathering
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
        AsynchronousChannelGroup threadGroup = null;
        if (getExecutor() instanceof ExecutorService) {
            threadGroup = AsynchronousChannelGroup.withThreadPool((ExecutorService) getExecutor());
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
            running = true;
            paused = false;

            // FIXME: remove when more stable
            log.warn("The NIO2 connector is currently EXPERIMENTAL and should not be used in production");

            // Create worker collection
            if ( getExecutor() == null ) {
                createExecutor();
            }

            initializeConnectionLatch();
            startAcceptorThreads();

            // Start async timeout thread
            Thread timeoutThread = new Thread(new AsyncTimeout(),
                    getName() + "-AsyncTimeout");
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
            unlockAccept();
        }
        if (useCaches) {
            socketWrapperCache.clear();
            nioChannels.clear();
            processorCache.clear();
        }
    }


    /**
     * Deallocate NIO memory pools, and close server socket.
     */
    @Override
    public void unbind() throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Destroy initiated for "+new InetSocketAddress(getAddress(),getPort()));
        }
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
        if (log.isDebugEnabled()) {
            log.debug("Destroy completed for "+new InetSocketAddress(getAddress(),getPort()));
        }
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
                    int appbufsize = engine.getSession().getApplicationBufferSize();
                    NioBufferHandler bufhandler = new NioBufferHandler(Math.max(appbufsize,socketProperties.getAppReadBufSize()),
                            socketProperties.getAppWriteBufSize(),
                            socketProperties.getDirectBuffer());
                    channel = new SecureNio2Channel(socket, engine, bufhandler, this);
                } else {
                    // normal tcp setup
                    NioBufferHandler bufhandler = new NioBufferHandler(socketProperties.getAppReadBufSize(),
                                                                       socketProperties.getAppWriteBufSize(),
                                                                       socketProperties.getDirectBuffer());

                    channel = new Nio2Channel(socket, bufhandler);
                }
            } else {
                channel.setIOChannel(socket);
                if ( channel instanceof SecureNio2Channel ) {
                    SSLEngine engine = createSSLEngine();
                    ((SecureNio2Channel)channel).reset(engine);
                } else {
                    channel.reset();
                }
            }
            Nio2SocketWrapper socketWrapper = (useCaches) ? socketWrapperCache.pop() : null;
            if (socketWrapper == null) {
                socketWrapper = new Nio2SocketWrapper(channel);
            }
            socketWrapper.reset(channel, getSocketProperties().getSoTimeout());
            socketWrapper.setKeepAliveLeft(Nio2Endpoint.this.getMaxKeepAliveRequests());
            socketWrapper.setSecure(isSSLEnabled());
            if (sslContext != null) {
                ((SecureNio2Channel) channel).setSocket(socketWrapper);
            }
            processSocket(socketWrapper, SocketStatus.OPEN_READ, true);
            // FIXME: In theory, awaitBytes is better, but the SSL handshake is done by processSocket
            //awaitBytes(socketWrapper);
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

    protected boolean processSocket0(SocketWrapper<Nio2Channel> socket, SocketStatus status, boolean dispatch) {
        try {
            ((Nio2SocketWrapper) socket).setCometNotify(false); //will get reset upon next reg
            SocketProcessor sc = (useCaches) ? processorCache.pop() : null;
            if (sc == null) {
                sc = new SocketProcessor(socket, status);
            } else {
                sc.reset(socket, status);
            }
            Executor executor = getExecutor();
            if (dispatch && executor != null) {
                executor.execute(sc);
            } else {
                sc.run();
            }
        } catch (RejectedExecutionException ree) {
            log.warn(sm.getString("endpoint.executor.fail", socket), ree);
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
        try {
            Nio2SocketWrapper ka = (Nio2SocketWrapper) socket;
            if (socket != null && socket.isComet() && status != null) {
                socket.setComet(false);//to avoid a loop
                if (status == SocketStatus.TIMEOUT ) {
                    if (processSocket0(socket, status, true)) {
                        return; // don't close on comet timeout
                    }
                } else {
                    // Don't dispatch if the lines below are canceling the key
                    processSocket0(socket, status, false);
                }
            }
            if (socket!=null) handler.release(socket);
            try {
                if (socket!=null) {
                    socket.getSocket().close(true);
                }
            } catch (Exception e){
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString(
                            "endpoint.debug.socketCloseFail"), e);
                }
            }
            try {
                if (ka != null && ka.getSendfileData() != null
                        && ka.getSendfileData().fchannel != null
                        && ka.getSendfileData().fchannel.isOpen()) {
                    ka.getSendfileData().fchannel.close();
                }
            } catch (Exception ignore) {
            }
            if (ka!=null) {
                ka.reset();
                countDownConnection();
            }
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
                        // Introduce delay if necessary
                        errorDelay = handleExceptionWithDelay(errorDelay);
                        // re-throw
                        throw e;
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
                } catch (NullPointerException npe) {
                    if (running) {
                        log.error(sm.getString("endpoint.accept.fail"), npe);
                    }
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    log.error(sm.getString("endpoint.accept.fail"), t);
                }
            }
            state = AcceptorState.ENDED;
        }

    }

    /**
     * Async timeout thread
     */
    protected class AsyncTimeout implements Runnable {
        /**
         * The background thread that checks async requests and fires the
         * timeout if there has been no activity.
         */
        @Override
        public void run() {

            // Loop until we receive a shutdown command
            while (running) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Ignore
                }
                long now = System.currentTimeMillis();
                Iterator<SocketWrapper<Nio2Channel>> sockets =
                    waitingRequests.keySet().iterator();
                while (sockets.hasNext()) {
                    SocketWrapper<Nio2Channel> socket = sockets.next();
                    long access = socket.getLastAccess();
                    if (socket.getTimeout() > 0 &&
                            (now-access)>socket.getTimeout()) {
                        processSocket(socket, SocketStatus.TIMEOUT, true);
                    }
                }

                // Loop if endpoint is paused
                while (paused && running) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }

            }
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

        public Nio2SocketWrapper(Nio2Channel channel) {
            super(channel);
        }

        @Override
        public void reset(Nio2Channel channel, long soTimeout) {
            super.reset(channel, soTimeout);
            upgradeInit = false;
            cometNotify = false;
            interestOps = 0;
            sendfileData = null;
            if (readLatch != null) {
                try {
                    for (int i = 0; i < (int) readLatch.getCount(); i++) {
                        readLatch.countDown();
                    }
                } catch (Exception ignore) {
                }
            }
            readLatch = null;
            sendfileData = null;
            if (writeLatch != null) {
                try {
                    for (int i = 0; i < (int) writeLatch.getCount(); i++) {
                        writeLatch.countDown();
                    }
                } catch (Exception ignore) {
                }
            }
            writeLatch = null;
            setWriteTimeout(soTimeout);
        }

        public void reset() {
            reset(null, -1);
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
        public void setCometNotify(boolean notify) { this.cometNotify = notify; }
        public boolean getCometNotify() { return cometNotify; }
        public Nio2Channel getChannel() { return getSocket();}
        public int interestOps() { return interestOps;}
        public int interestOps(int ops) { this.interestOps  = ops; return ops; }
        public CountDownLatch getReadLatch() { return readLatch; }
        public CountDownLatch getWriteLatch() { return writeLatch; }
        protected CountDownLatch resetLatch(CountDownLatch latch) {
            if ( latch==null || latch.getCount() == 0 ) return null;
            else throw new IllegalStateException("Latch must be at count 0");
        }
        public void resetReadLatch() { readLatch = resetLatch(readLatch); }
        public void resetWriteLatch() { writeLatch = resetLatch(writeLatch); }

        protected CountDownLatch startLatch(CountDownLatch latch, int cnt) {
            if ( latch == null || latch.getCount() == 0 ) {
                return new CountDownLatch(cnt);
            }
            else throw new IllegalStateException("Latch must be at count 0 or null.");
        }
        public void startReadLatch(int cnt) { readLatch = startLatch(readLatch,cnt);}
        public void startWriteLatch(int cnt) { writeLatch = startLatch(writeLatch,cnt);}

        protected void awaitLatch(CountDownLatch latch, long timeout, TimeUnit unit) throws InterruptedException {
            if ( latch == null ) throw new IllegalStateException("Latch cannot be null");
            // Note: While the return value is ignored if the latch does time
            //       out, logic further up the call stack will trigger a
            //       SocketTimeoutException
            latch.await(timeout,unit);
        }
        public void awaitReadLatch(long timeout, TimeUnit unit) throws InterruptedException { awaitLatch(readLatch,timeout,unit);}
        public void awaitWriteLatch(long timeout, TimeUnit unit) throws InterruptedException { awaitLatch(writeLatch,timeout,unit);}

        public void setSendfileData(SendfileData sf) { this.sendfileData = sf;}
        public SendfileData getSendfileData() { return this.sendfileData;}

        public void setWriteTimeout(long writeTimeout) {
            if (writeTimeout <= 0) {
                this.writeTimeout = Long.MAX_VALUE;
            } else {
                this.writeTimeout = writeTimeout;
            }
        }
        public long getWriteTimeout() {return this.writeTimeout;}

        private int interestOps = 0;
        private boolean cometNotify = false;
        private CountDownLatch readLatch = null;
        private CountDownLatch writeLatch = null;
        private SendfileData sendfileData = null;
        private long writeTimeout = -1;
        private boolean upgradeInit = false;

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
        public ByteBuffer expand(ByteBuffer buffer, int remaining) {return buffer;}
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
        public SSLImplementation getSslImplementation();
        public void onCreateSSLEngine(SSLEngine engine);
    }

    protected ConcurrentHashMap<SocketWrapper<Nio2Channel>, SocketWrapper<Nio2Channel>> waitingRequests =
            new ConcurrentHashMap<>();

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
        waitingRequests.put(socket, socket);
    }

    public boolean removeTimeout(SocketWrapper<Nio2Channel> socket) {
        return waitingRequests.remove(socket) != null;
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
        try {
            socket.getSocket().read(byteBuffer, socket.getTimeout(),
                    TimeUnit.MILLISECONDS, socket, awaitBytes);
        } catch (ReadPendingException e) {
            // Ignore
        }
    }

    public boolean processSendfile(final Nio2SocketWrapper socket) {

        // Configure the send file data
        SendfileData data = socket.getSendfileData();
        if (data.fchannel == null || !data.fchannel.isOpen()) {
            java.nio.file.Path path = new File(data.fileName).toPath();
            try {
                data.fchannel = java.nio.channels.FileChannel
                        .open(path, StandardOpenOption.READ).position(data.pos);
            } catch (IOException e) {
                closeSocket(socket, SocketStatus.ERROR);
                return false;
            }
        }

        final ByteBuffer buffer;
        if (!socketProperties.getDirectBuffer() && sslContext == null) {
            // If not using SSL and direct buffers are not used, the
            // idea of sendfile is to avoid memory copies, so allocate a
            // direct buffer
            int bufferSize;
            try {
                Integer bufferSizeInteger = socket.getSocket().getIOChannel().getOption(StandardSocketOptions.SO_SNDBUF);
                if (bufferSizeInteger != null) {
                    bufferSize = bufferSizeInteger.intValue();
                } else {
                    bufferSize = 8192;
                }
            } catch (IOException e) {
                bufferSize = 8192;
            }
            buffer = ByteBuffer.allocateDirect(bufferSize);
        } else {
            buffer = socket.getSocket().getBufHandler().getWriteBuffer();
        }
        int nr = -1;
        try {
            nr = data.fchannel.read(buffer);
        } catch (IOException e1) {
            closeSocket(socket, SocketStatus.ERROR);
            return false;
        }

        if (nr >= 0) {
            socket.getSocket().setSendFile(true);
            buffer.flip();
            socket.getSocket().write(buffer, data, new CompletionHandler<Integer, SendfileData>() {

                @Override
                public void completed(Integer nw, SendfileData attachment) {
                    if (nw.intValue() < 0) { // Reach the end of stream
                        closeSocket(socket, SocketStatus.DISCONNECT);
                        try {
                            attachment.fchannel.close();
                        } catch (IOException e) {
                            // Ignore
                        }
                        return;
                    }

                    attachment.pos += nw.intValue();
                    attachment.length -= nw.intValue();

                    if (attachment.length <= 0) {
                        socket.setSendfileData(null);
                        try {
                            attachment.fchannel.close();
                        } catch (IOException e) {
                            // Ignore
                        }
                        if (attachment.keepAlive) {
                            socket.getSocket().setSendFile(false);
                            awaitBytes(socket);
                        } else {
                            closeSocket(socket, SocketStatus.DISCONNECT);
                        }
                        return;
                    }

                    boolean ok = true;

                    if (!buffer.hasRemaining()) {
                        // This means that all data in the buffer has
                        // been
                        // written => Empty the buffer and read again
                        buffer.clear();
                        try {
                            if (attachment.fchannel.read(buffer) >= 0) {
                                buffer.flip();
                                if (attachment.length < buffer.remaining()) {
                                    buffer.limit(buffer.limit() - buffer.remaining() + (int) attachment.length);
                                }
                            } else {
                                // Reach the EOF
                                ok = false;
                            }
                        } catch (Throwable th) {
                            if ( log.isDebugEnabled() ) log.debug("Unable to complete sendfile request:", th);
                            ok = false;
                        }
                    }

                    if (ok) {
                        socket.getSocket().write(buffer, attachment, this);
                    } else {
                        try {
                            attachment.fchannel.close();
                        } catch (IOException e) {
                            // Ignore
                        }
                        closeSocket(socket, SocketStatus.ERROR);
                    }
                }

                @Override
                public void failed(Throwable exc, SendfileData attachment) {
                    // Closing channels
                    closeSocket(socket, SocketStatus.ERROR);
                    try {
                        attachment.fchannel.close();
                    } catch (IOException e) {
                        // Ignore
                    }
                }
            });
            return true;
        } else {
            return false;
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
            if (socket != null && socket.isUpgraded() &&
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
                    if (socket != null && socket.getSocket() != null) {
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
                    if (log.isDebugEnabled()) log.debug("Error during SSL handshake",x);
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
                        try {
                            socket.setComet(false);
                            closeSocket(socket, SocketStatus.ERROR);
                            if (useCaches && running && !paused) {
                                nioChannels.push(socket.getSocket());
                            }
                            if (useCaches && running && !paused && socket != null) {
                                socketWrapperCache.push((Nio2SocketWrapper) socket);
                            }
                        } catch (Exception x) {
                            log.error("",x);
                        }
                    } else if (state == SocketState.UPGRADING) {
                        socket.setKeptAlive(true);
                        socket.access();
                        launch = true;
                    }
                } else if (handshake == -1 ) {
                    if (socket != null) {
                        closeSocket(socket, SocketStatus.DISCONNECT);
                    }
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
                    if (socket != null) {
                        closeSocket(socket, SocketStatus.ERROR);
                    }
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
                log.error("", t);
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
    }
}
