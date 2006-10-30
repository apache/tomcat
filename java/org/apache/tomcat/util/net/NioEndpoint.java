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

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.net.SecureNioChannel.ApplicationBufferHandler;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.net.NioEndpoint.KeyAttachment;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NIO tailored thread pool, providing the following services:
 * <ul>
 * <li>Socket acceptor thread</li>
 * <li>Socket poller thread</li>
 * <li>Worker threads pool</li>
 * </ul>
 *
 * When switching to Java 5, there's an opportunity to use the virtual
 * machine's thread pool.
 *
 * @author Mladen Turk
 * @author Remy Maucherat
 * @author Filip Hanik
 */
public class NioEndpoint {


    // -------------------------------------------------------------- Constants


    protected static Log log = LogFactory.getLog(NioEndpoint.class);

    protected static StringManager sm =
        StringManager.getManager("org.apache.tomcat.util.net.res");


    /**
     * The Request attribute key for the cipher suite.
     */
    public static final String CIPHER_SUITE_KEY = "javax.servlet.request.cipher_suite";

    /**
     * The Request attribute key for the key size.
     */
    public static final String KEY_SIZE_KEY = "javax.servlet.request.key_size";

    /**
     * The Request attribute key for the client certificate chain.
     */
    public static final String CERTIFICATE_KEY = "javax.servlet.request.X509Certificate";

    /**
     * The Request attribute key for the session id.
     * This one is a Tomcat extension to the Servlet spec.
     */
    public static final String SESSION_ID_KEY = "javax.servlet.request.ssl_session";

    public static final int OP_REGISTER = -1; //register interest op
    // ----------------------------------------------------------------- Fields


    /**
     * Available workers.
     */
    protected WorkerStack workers = null;


    /**
     * Running state of the endpoint.
     */
    protected volatile boolean running = false;


    /**
     * Will be set to true whenever the endpoint is paused.
     */
    protected volatile boolean paused = false;


    /**
     * Track the initialization state of the endpoint.
     */
    protected boolean initialized = false;


    /**
     * Current worker threads busy count.
     */
    protected int curThreadsBusy = 0;


    /**
     * Current worker threads count.
     */
    protected int curThreads = 0;


    /**
     * Sequence number used to generate thread names.
     */
    protected int sequence = 0;
    
    protected NioSelectorPool selectorPool = new NioSelectorPool();
    
    /**
     * Server socket "pointer".
     */
    protected ServerSocketChannel serverSock = null;

    /**
     * Cache for key attachment objects
     */
    protected ConcurrentLinkedQueue<KeyAttachment> keyCache = new ConcurrentLinkedQueue<KeyAttachment>();
    
    /**
     * Cache for poller events
     */
    protected ConcurrentLinkedQueue<PollerEvent> eventCache = new ConcurrentLinkedQueue<PollerEvent>();

    /**
     * Bytebuffer cache, each channel holds a set of buffers (two, except for SSL holds four)
     */
    protected ConcurrentLinkedQueue<NioChannel> nioChannels = new ConcurrentLinkedQueue<NioChannel>() {
        protected AtomicInteger size = new AtomicInteger(0);
        protected AtomicInteger bytes = new AtomicInteger(0);
        public boolean offer(NioChannel socket, KeyAttachment att) {
            boolean offer = socketProperties.getBufferPool()==-1?true:size.get()<socketProperties.getBufferPool();
            offer = offer && (socketProperties.getBufferPoolSize()==-1?true:(bytes.get()+socket.getBufferSize())<socketProperties.getBufferPoolSize());
            //avoid over growing our cache or add after we have stopped
            if ( running && (!paused) && (offer) ) {
                boolean result = super.offer(socket);
                if ( result ) {
                    size.incrementAndGet();
                    bytes.addAndGet(socket.getBufferSize());
                }
                return result;
            }
            else return false;
        }
        
        public NioChannel poll() {
            NioChannel result = super.poll();
            if ( result != null ) {
                size.decrementAndGet();
                bytes.addAndGet(-result.getBufferSize());
            }
            return result;
        }
        
        public void clear() {
            super.clear();
            size.set(0);
        }
    };

    

    // ------------------------------------------------------------- Properties


    /**
     * External Executor based thread pool.
     */
    protected Executor executor = null;
    public void setExecutor(Executor executor) { this.executor = executor; }
    public Executor getExecutor() { return executor; }


    /**
     * Maximum amount of worker threads.
     */
    protected int maxThreads = 400;
    public void setMaxThreads(int maxThreads) { this.maxThreads = maxThreads; }
    public int getMaxThreads() { return maxThreads; }


    /**
     * Priority of the acceptor and poller threads.
     */
    protected int threadPriority = Thread.NORM_PRIORITY;
    public void setThreadPriority(int threadPriority) { this.threadPriority = threadPriority; }
    public int getThreadPriority() { return threadPriority; }


    /**
     * Server socket port.
     */
    protected int port;
    public int getPort() { return port; }
    public void setPort(int port ) { this.port=port; }


    /**
     * Address for the server socket.
     */
    protected InetAddress address;
    public InetAddress getAddress() { return address; }
    public void setAddress(InetAddress address) { this.address = address; }


    /**
     * Handling of accepted sockets.
     */
    protected Handler handler = null;
    public void setHandler(Handler handler ) { this.handler = handler; }
    public Handler getHandler() { return handler; }


    /**
     * Allows the server developer to specify the backlog that
     * should be used for server sockets. By default, this value
     * is 100.
     */
    protected int backlog = 100;
    public void setBacklog(int backlog) { if (backlog > 0) this.backlog = backlog; }
    public int getBacklog() { return backlog; }

    protected SocketProperties socketProperties = new SocketProperties();

    /**
     * Socket TCP no delay.
     */
    public boolean getTcpNoDelay() { return socketProperties.getTcpNoDelay();}
    public void setTcpNoDelay(boolean tcpNoDelay) { socketProperties.setTcpNoDelay(tcpNoDelay); }


    /**
     * Socket linger.
     */
    public int getSoLinger() { return socketProperties.getSoLingerTime(); }
    public void setSoLinger(int soLinger) { 
        socketProperties.setSoLingerTime(soLinger);
        socketProperties.setSoLingerOn(soLinger>=0);
    }


    /**
     * Socket timeout.
     */
    public int getSoTimeout() { return socketProperties.getSoTimeout(); }
    public void setSoTimeout(int soTimeout) { socketProperties.setSoTimeout(soTimeout); }


    /**
     * Timeout on first request read before going to the poller, in ms.
     */
    protected int firstReadTimeout = 60000;
    public int getFirstReadTimeout() { return firstReadTimeout; }
    public void setFirstReadTimeout(int firstReadTimeout) { this.firstReadTimeout = firstReadTimeout; }


    /**
     * The default is true - the created threads will be
     *  in daemon mode. If set to false, the control thread
     *  will not be daemon - and will keep the process alive.
     */
    protected boolean daemon = true;
    public void setDaemon(boolean b) { daemon = b; }
    public boolean getDaemon() { return daemon; }


    /**
     * Name of the thread pool, which will be used for naming child threads.
     */
    protected String name = "TP";
    public void setName(String name) { this.name = name; }
    public String getName() { return name; }



    /**
     * Allow comet request handling.
     */
    protected boolean useComet = true;
    public void setUseComet(boolean useComet) { this.useComet = useComet; }
    public boolean getUseComet() { return useComet; }


    /**
     * Acceptor thread count.
     */
    protected int acceptorThreadCount = 0;
    public void setAcceptorThreadCount(int acceptorThreadCount) { this.acceptorThreadCount = acceptorThreadCount; }
    public int getAcceptorThreadCount() { return acceptorThreadCount; }



    /**
     * Poller thread count.
     */
    protected int pollerThreadCount = 0;
    public void setPollerThreadCount(int pollerThreadCount) { this.pollerThreadCount = pollerThreadCount; }
    public int getPollerThreadCount() { return pollerThreadCount; }

    protected long selectorTimeout = 1000;
    public void setSelectorTimeout(long timeout){ this.selectorTimeout = timeout;}
    public long getSelectorTimeout(){ return this.selectorTimeout; }
    /**
     * The socket poller.
     */
    protected Poller[] pollers = null;
    protected int pollerRoundRobin = 0;
    public Poller getPoller0() {
        pollerRoundRobin = (pollerRoundRobin + 1) % pollers.length;
        Poller poller = pollers[pollerRoundRobin];
        return poller;
    }


    /**
     * The socket poller used for Comet support.
     */
    public Poller getCometPoller0() {
        Poller poller = getPoller0();
        return poller;
    }


    /**
     * Dummy maxSpareThreads property.
     */
    public int getMaxSpareThreads() { return Math.min(getMaxThreads(),5); }


    /**
     * Dummy minSpareThreads property.
     */
    public int getMinSpareThreads() { return Math.min(getMaxThreads(),5); }
    
    /**
     * Generic properties, introspected
     */
    public void setProperty(String name, String value) {
        final String selectorPoolName = "selectorPool.";
        final String socketName = "socket.";
        try {
            if (name.startsWith(selectorPoolName)) {
                IntrospectionUtils.setProperty(selectorPool, name.substring(selectorPoolName.length()), value);
            } else if (name.startsWith(socketName)) {
                IntrospectionUtils.setProperty(socketProperties, name.substring(socketName.length()), value);
            }
        }catch ( Exception x ) {
            log.error("Unable to set attribute \""+name+"\" to \""+value+"\"",x);
        }
    }


    // --------------------  SSL related properties --------------------
    protected String keystoreFile = System.getProperty("user.home")+"/.keystore";
    public String getKeystoreFile() { return keystoreFile;}
    public void setKeystoreFile(String s ) { this.keystoreFile = s; }
    public void setKeystore(String s ) { setKeystoreFile(s);}
    public String getKeystore() { return getKeystoreFile();}
    
    protected String algorithm = "SunX509";
    public String getAlgorithm() { return algorithm;}
    public void setAlgorithm(String s ) { this.algorithm = s;}

    protected boolean clientAuth = false;
    public boolean getClientAuth() { return clientAuth;}
    public void setClientAuth(boolean b ) { this.clientAuth = b;}
    
    protected String keystorePass = "changeit";
    public String getKeystorePass() { return keystorePass;}
    public void setKeystorePass(String s ) { this.keystorePass = s;}
    
    protected String keystoreType = "JKS";
    public String getKeystoreType() { return keystoreType;}
    public void setKeystoreType(String s ) { this.keystoreType = s;}

    protected String sslProtocol = "TLS"; 
    
    public String getSslProtocol() { return sslProtocol;}
    public void setSslProtocol(String s) { sslProtocol = s;}
    
    protected String sslEnabledProtocols=null; //"TLSv1,SSLv3,SSLv2Hello"
    protected String[] sslEnabledProtocolsarr =  new String[0];
    public void setSslEnabledProtocols(String s) {
        this.sslEnabledProtocols = s;
        StringTokenizer t = new StringTokenizer(s,",");
        sslEnabledProtocolsarr = new String[t.countTokens()];
        for (int i=0; i<sslEnabledProtocolsarr.length; i++ ) sslEnabledProtocolsarr[i] = t.nextToken();
    }
    
    
    protected String ciphers = null;
    protected String[] ciphersarr = new String[0];
    public String getCiphers() { return ciphers;}
    public void setCiphers(String s) { 
        ciphers = s;
        if ( s == null ) ciphersarr = new String[0];
        else {
            StringTokenizer t = new StringTokenizer(s,",");
            ciphersarr = new String[t.countTokens()];
            for (int i=0; i<ciphersarr.length; i++ ) ciphersarr[i] = t.nextToken();
        }
    }
    
    /**
     * SSL engine.
     */
    protected boolean SSLEnabled = false;
    public boolean isSSLEnabled() { return SSLEnabled;}
    public void setSSLEnabled(boolean SSLEnabled) {this.SSLEnabled = SSLEnabled;}

    protected boolean secure = false;
    public boolean getSecure() { return secure;}
    public void setSecure(boolean b) { secure = b;}

    public void setSelectorPool(NioSelectorPool selectorPool) {
        this.selectorPool = selectorPool;
    }

    public void setSocketProperties(SocketProperties socketProperties) {
        this.socketProperties = socketProperties;
    }

    protected SSLContext sslContext = null;
    public SSLContext getSSLContext() { return sslContext;}
    public void setSSLContext(SSLContext c) { sslContext = c;}
    
    // --------------------------------------------------------- Public Methods


    /**
     * Number of keepalive sockets.
     */
    public int getKeepAliveCount() {
        if (pollers == null) {
            return 0;
        } else {
            int keepAliveCount = 0;
            for (int i = 0; i < pollers.length; i++) {
                keepAliveCount += pollers[i].getKeepAliveCount();
            }
            return keepAliveCount;
        }
    }



    /**
     * Return the amount of threads that are managed by the pool.
     *
     * @return the amount of threads that are managed by the pool
     */
    public int getCurrentThreadCount() {
        return curThreads;
    }


    /**
     * Return the amount of threads currently busy.
     *
     * @return the amount of threads currently busy
     */
    public int getCurrentThreadsBusy() {
        return curThreadsBusy;
    }


    /**
     * Return the state of the endpoint.
     *
     * @return true if the endpoint is running, false otherwise
     */
    public boolean isRunning() {
        return running;
    }


    /**
     * Return the state of the endpoint.
     *
     * @return true if the endpoint is paused, false otherwise
     */
    public boolean isPaused() {
        return paused;
    }


    // ----------------------------------------------- Public Lifecycle Methods


    /**
     * Initialize the endpoint.
     */
    public void init()
        throws Exception {

        if (initialized)
            return;

        serverSock = ServerSocketChannel.open();
        InetSocketAddress addr = (address!=null?new InetSocketAddress(address,port):new InetSocketAddress(port));
        serverSock.socket().bind(addr,100); //todo, set backlog value
        serverSock.configureBlocking(true); //mimic APR behavior

        // Initialize thread count defaults for acceptor, poller and sendfile
        if (acceptorThreadCount == 0) {
            // FIXME: Doesn't seem to work that well with multiple accept threads
            acceptorThreadCount = 1;
        }
        if (pollerThreadCount <= 0) {
            //minimum one poller thread
            pollerThreadCount = 1;
        }

        // Initialize SSL if needed
        if (isSSLEnabled()) {
            // Initialize SSL
            char[] passphrase = getKeystorePass().toCharArray();

            KeyStore ks = KeyStore.getInstance(getKeystoreType());
            ks.load(new FileInputStream(getKeystoreFile()), passphrase);
            KeyStore ts = KeyStore.getInstance(getKeystoreType());
            ts.load(new FileInputStream(getKeystoreFile()), passphrase);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(getAlgorithm());
            kmf.init(ks, passphrase);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(getAlgorithm());
            tmf.init(ts);

            sslContext = SSLContext.getInstance(getSslProtocol());
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        }

        initialized = true;

    }


    /**
     * Start the APR endpoint, creating acceptor, poller threads.
     */
    public void start()
        throws Exception {
        // Initialize socket if not done before
        if (!initialized) {
            init();
        }
        if (!running) {
            running = true;
            paused = false;
            
            // Create worker collection
            if (executor == null) {
                workers = new WorkerStack(maxThreads);
                //executor = new ThreadPoolExecutor(getMinSpareThreads(),getMaxThreads(),5000,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<Runnable>());
            }

            // Start acceptor threads
            for (int i = 0; i < acceptorThreadCount; i++) {
                Thread acceptorThread = new Thread(new Acceptor(), getName() + "-Acceptor-" + i);
                acceptorThread.setPriority(threadPriority);
                acceptorThread.setDaemon(daemon);
                acceptorThread.start();
            }

            // Start poller threads
            pollers = new Poller[pollerThreadCount];
            for (int i = 0; i < pollerThreadCount; i++) {
                pollers[i] = new Poller();
                pollers[i].init();
                Thread pollerThread = new Thread(pollers[i], getName() + "-Poller-" + i);
                pollerThread.setPriority(threadPriority);
                pollerThread.setDaemon(true);
                pollerThread.start();
            }
        }
    }


    /**
     * Pause the endpoint, which will make it stop accepting new sockets.
     */
    public void pause() {
        if (running && !paused) {
            paused = true;
            unlockAccept();
        }
    }


    /**
     * Resume the endpoint, which will make it start accepting new sockets
     * again.
     */
    public void resume() {
        if (running) {
            paused = false;
        }
    }


    /**
     * Stop the endpoint. This will cause all processing threads to stop.
     */
    public void stop() {
        if (running) {
            running = false;
            unlockAccept();
            for (int i = 0; i < pollers.length; i++) {
                pollers[i].destroy();
            }
            pollers = null;
        }
        eventCache.clear();
        keyCache.clear();
        nioChannels.clear();
    }


    /**
     * Deallocate APR memory pools, and close server socket.
     */
    public void destroy() throws Exception {
        if (running) {
            stop();
        }
        // Close server socket
        serverSock.socket().close();
        serverSock.close();
        serverSock = null;
        sslContext = null;
        initialized = false;
        nioChannels.clear();
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Get a sequence number used for thread naming.
     */
    protected int getSequence() {
        return sequence++;
    }

    public int getWriteBufSize() {
        return socketProperties.getTxBufSize();
    }

    public int getReadBufSize() {
        return socketProperties.getRxBufSize();
    }

    public NioSelectorPool getSelectorPool() {
        return selectorPool;
    }

    public SocketProperties getSocketProperties() {
        return socketProperties;
    }

    /**
     * Unlock the server socket accept using a bogus connection.
     */
    protected void unlockAccept() {
        java.net.Socket s = null;
        try {
            // Need to create a connection to unlock the accept();
            if (address == null) {
                s = new java.net.Socket("127.0.0.1", port);
            } else {
                s = new java.net.Socket(address, port);
                // setting soLinger to a small value will help shutdown the
                // connection quicker
                s.setSoLinger(true, 0);
            }
        } catch(Exception e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("endpoint.debug.unlock", "" + port), e);
            }
        } finally {
            if (s != null) {
                try {
                    s.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }


    /**
     * Process the specified connection.
     */
    protected boolean setSocketOptions(SocketChannel socket) {
        // Process the connection
        int step = 1;
        try {
            //disable blocking, APR style, we are gonna be polling it
            socket.configureBlocking(false);
            Socket sock = socket.socket();
            socketProperties.setProperties(sock);

            NioChannel channel = nioChannels.poll();
            if ( channel == null ) {
                // 2: SSL setup
                step = 2;

                if (sslContext != null) {
                    SSLEngine engine = createSSLEngine();
                    int appbufsize = engine.getSession().getApplicationBufferSize();
                    NioBufferHandler bufhandler = new NioBufferHandler(Math.max(appbufsize,getReadBufSize()),
                                                                       Math.max(appbufsize,getWriteBufSize()),
                                                                       socketProperties.getDirectBuffer());
                    channel = new SecureNioChannel(socket, engine, bufhandler, selectorPool);
                } else {
                    NioBufferHandler bufhandler = new NioBufferHandler(getReadBufSize(),
                                                                       getWriteBufSize(),
                                                                       socketProperties.getDirectBuffer());

                    channel = new NioChannel(socket, bufhandler);
                }
            } else {
                
                channel.setIOChannel(socket);
                if ( channel instanceof SecureNioChannel ) {
                    SSLEngine engine = createSSLEngine();
                    ((SecureNioChannel)channel).reset(engine);
                } else {
                    channel.reset();
                }
            }
            getPoller0().register(channel);

        } catch (Throwable t) {
            try {
                log.error("",t);
            }catch ( Throwable tt){}
            // Tell to close the socket
            return false;
        }
        return true;
    }

    protected SSLEngine createSSLEngine() {
        SSLEngine engine = sslContext.createSSLEngine();
        engine.setNeedClientAuth(getClientAuth());
        engine.setUseClientMode(false);
        if ( ciphersarr.length > 0 ) engine.setEnabledCipherSuites(ciphersarr);
        if ( sslEnabledProtocolsarr.length > 0 ) engine.setEnabledProtocols(sslEnabledProtocolsarr);
        
        return engine;
    }


    /**
     * Create (or allocate) and return an available processor for use in
     * processing a specific HTTP request, if possible.  If the maximum
     * allowed processors have already been created and are in use, return
     * <code>null</code> instead.
     */
    protected Worker createWorkerThread() {

        synchronized (workers) {
            if (workers.size() > 0) {
                curThreadsBusy++;
                return (workers.pop());
            }
            if ((maxThreads > 0) && (curThreads < maxThreads)) {
                curThreadsBusy++;
                return (newWorkerThread());
            } else {
                if (maxThreads < 0) {
                    curThreadsBusy++;
                    return (newWorkerThread());
                } else {
                    return (null);
                }
            }
        }

    }


    /**
     * Create and return a new processor suitable for processing HTTP
     * requests and returning the corresponding responses.
     */
    protected Worker newWorkerThread() {

        Worker workerThread = new Worker();
        workerThread.start();
        return (workerThread);

    }


    /**
     * Return a new worker thread, and block while to worker is available.
     */
    protected Worker getWorkerThread() {
        // Allocate a new worker thread
        Worker workerThread = createWorkerThread();
        while (workerThread == null) {
            try {
                synchronized (workers) {
                    workerThread = createWorkerThread();
                    if ( workerThread == null ) workers.wait();
                }
            } catch (InterruptedException e) {
                // Ignore
            }
            if ( workerThread == null ) workerThread = createWorkerThread();
        }
        return workerThread;
    }


    /**
     * Recycle the specified Processor so that it can be used again.
     *
     * @param workerThread The processor to be recycled
     */
    protected void recycleWorkerThread(Worker workerThread) {
        synchronized (workers) {
            workers.push(workerThread);
            curThreadsBusy--;
            workers.notify();
        }
    }


    protected boolean processSocket(SocketChannel socket) {
        try {
            if (executor == null) {
                getWorkerThread().assign(socket);
            }  else {
                executor.execute(new SocketOptionsProcessor(socket));
            }
        } catch (Throwable t) {
            // This means we got an OOM or similar creating a thread, or that
            // the pool and its queue are full
            log.error(sm.getString("endpoint.process.fail"), t);
            return false;
        }
        return true;
    }
    /**
     * Process given socket.
     */
    protected boolean processSocket(NioChannel socket) {
        try {
            if (executor == null) {
                getWorkerThread().assign(socket);
            }  else {
                executor.execute(new SocketProcessor(socket));
            }
        } catch (Throwable t) {
            // This means we got an OOM or similar creating a thread, or that
            // the pool and its queue are full
            log.error(sm.getString("endpoint.process.fail"), t);
            return false;
        }
        return true;
    }


    /**
     * Process given socket for an event.
     */
    protected boolean processSocket(NioChannel socket, SocketStatus status) {
        try {
            if (executor == null) {
                getWorkerThread().assign(socket, status);
            } else {
                executor.execute(new SocketEventProcessor(socket, status));
            }
        } catch (Throwable t) {
            // This means we got an OOM or similar creating a thread, or that
            // the pool and its queue are full
            log.error(sm.getString("endpoint.process.fail"), t);
            return false;
        }
        return true;
    }


    // --------------------------------------------------- Acceptor Inner Class


    /**
     * Server socket acceptor thread.
     */
    protected class Acceptor implements Runnable {


        /**
         * The background thread that listens for incoming TCP/IP connections and
         * hands them off to an appropriate processor.
         */
        public void run() {

            // Loop until we receive a shutdown command
            while (running) {

                // Loop if endpoint is paused
                while (paused) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }

                try {
                    // Accept the next incoming connection from the server socket
                    SocketChannel socket = serverSock.accept();
                    // Hand this socket off to an appropriate processor
                    if ( running && (!paused) && socket != null ) processSocket(socket);
                } catch (Throwable t) {
                    log.error(sm.getString("endpoint.accept.fail"), t);
                }

                // The processor will recycle itself when it finishes

            }

        }

    }


    // ----------------------------------------------------- Poller Inner Classes

    /**
     * 
     * PollerEvent, cacheable object for poller events to avoid GC
     */
    public class PollerEvent implements Runnable {
        
        protected NioChannel socket;
        protected int interestOps;
        protected KeyAttachment key;
        public PollerEvent(NioChannel ch, KeyAttachment k, int intOps) {
            reset(ch, k, intOps);
        }
    
        public void reset(NioChannel ch, KeyAttachment k, int intOps) {
            socket = ch;
            interestOps = intOps;
            key = k;
        }
    
        public void reset() {
            reset(null, null, 0);
        }
    
        public void run() {
            if ( interestOps == OP_REGISTER ) {
                try {
                    socket.getIOChannel().register(socket.getPoller().getSelector(), SelectionKey.OP_READ, key);
                } catch (Exception x) {
                    log.error("", x);
                }
            } else {
                final SelectionKey key = socket.getIOChannel().keyFor(socket.getPoller().getSelector());
                final KeyAttachment att = (KeyAttachment) key.attachment();
                try {
                    if (key != null) {
                        key.interestOps(interestOps);
                        att.interestOps(interestOps);
                    }
                }
                catch (CancelledKeyException ckx) {
                    try {
                        if (key != null && key.attachment() != null) {
                            KeyAttachment ka = (KeyAttachment) key.attachment();
                            ka.setError(true); //set to collect this socket immediately
                        }
                        try {
                            socket.close();
                        }
                        catch (Exception ignore) {}
                        if (socket.isOpen())
                            socket.close(true);
                    }
                    catch (Exception ignore) {}
                }
            }//end if
        }//run
        
        public String toString() {
            return super.toString()+"[intOps="+this.interestOps+"]";
        }
    }
    /**
     * Poller class.
     */
    public class Poller implements Runnable {

        protected Selector selector;
        protected ConcurrentLinkedQueue<Runnable> events = new ConcurrentLinkedQueue<Runnable>();
        
        protected boolean close = false;
        protected long nextExpiration = 0;//optimize expiration handling

        protected int keepAliveCount = 0;
        public int getKeepAliveCount() { return keepAliveCount; }
        
        protected AtomicLong wakeupCounter = new AtomicLong(0l);



        public Poller() throws IOException {
            this.selector = Selector.open();
        }
        
        public Selector getSelector() { return selector;}

        /**
         * Create the poller. With some versions of APR, the maximum poller size will
         * be 62 (reocmpiling APR is necessary to remove this limitation).
         */
        protected void init() {
            keepAliveCount = 0;
        }

        /**
         * Destroy the poller.
         */
        protected void destroy() {
            // Wait for polltime before doing anything, so that the poller threads
            // exit, otherwise parallel descturction of sockets which are still
            // in the poller can cause problems
            close = true;
            events.clear();
            selector.wakeup();
        }
        
        public void addEvent(Runnable event) {
            events.offer(event);
            if ( wakeupCounter.incrementAndGet() < 3 ) selector.wakeup();
        }

        /**
         * Add specified socket and associated pool to the poller. The socket will
         * be added to a temporary array, and polled first after a maximum amount
         * of time equal to pollTime (in most cases, latency will be much lower,
         * however).
         *
         * @param socket to add to the poller
         */
        public void add(final NioChannel socket) {
            add(socket,SelectionKey.OP_READ);
        }
        
        public void add(final NioChannel socket, final int interestOps) {
            PollerEvent r = eventCache.poll();
            if ( r==null) r = new PollerEvent(socket,null,interestOps);
            else r.reset(socket,null,interestOps);
            addEvent(r);
        }
        
        public boolean events() {
            boolean result = false;
            //synchronized (events) {
                Runnable r = null;
                result = (events.size() > 0);
                while ( (r = (Runnable)events.poll()) != null ) {
                    try {
                        r.run();
                        if ( r instanceof PollerEvent ) {
                            ((PollerEvent)r).reset();
                            eventCache.offer((PollerEvent)r);
                        }
                    } catch ( Exception x ) {
                        log.error("",x);
                    }
                }
                //events.clear();
            //}
            return result;
        }
        
        public void register(final NioChannel socket)
        {
            socket.setPoller(this);
            KeyAttachment key = keyCache.poll();
            final KeyAttachment ka = key!=null?key:new KeyAttachment();
            ka.reset(this,socket);
            PollerEvent r = eventCache.poll();
            ka.interestOps(SelectionKey.OP_READ);//this is what OP_REGISTER turns into.
            if ( r==null) r = new PollerEvent(socket,ka,OP_REGISTER);
            else r.reset(socket,ka,OP_REGISTER);
            addEvent(r);
        }
        
        public void cancelledKey(SelectionKey key, SocketStatus status) {
            try {
                KeyAttachment ka = (KeyAttachment) key.attachment();
                if (ka != null && ka.getComet()) {
                    //the comet event takes care of clean up
                    processSocket(ka.getChannel(), status);
                }else {
                    if (key.isValid()) key.cancel();
                    if (key.channel().isOpen()) key.channel().close();
                    key.attach(null);
                }
            } catch (Throwable e) {
                if ( log.isDebugEnabled() ) log.error("",e);
                // Ignore
            }
        }
        /**
         * The background thread that listens for incoming TCP/IP connections and
         * hands them off to an appropriate processor.
         */
        public void run() {
            // Loop until we receive a shutdown command
            while (running) {
                // Loop if endpoint is paused
                while (paused) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }
                boolean hasEvents = false;

                hasEvents = (hasEvents | events());
                // Time to terminate?
                if (close) return;

                int keyCount = 0;
                try {
                    keyCount = selector.select(selectorTimeout);
                    wakeupCounter.set(0);
                    if ( close ) { selector.close(); return; }
                } catch ( NullPointerException x ) {
                    //sun bug 5076772 on windows JDK 1.5
                    if ( wakeupCounter == null || selector == null ) throw x;
                    continue;
                } catch ( CancelledKeyException x ) {
                    //sun bug 5076772 on windows JDK 1.5
                    if ( wakeupCounter == null || selector == null ) throw x;
                    continue;
                } catch (Throwable x) {
                    log.error("",x);
                    continue;
                }
                //either we timed out or we woke up, process events first
                if ( keyCount == 0 ) hasEvents = (hasEvents | events());

                Iterator iterator = keyCount > 0 ? selector.selectedKeys().iterator() : null;
                // Walk through the collection of ready keys and dispatch
                // any active event.
                while (iterator != null && iterator.hasNext()) {
                    SelectionKey sk = (SelectionKey) iterator.next();
                    iterator.remove();
                    KeyAttachment attachment = (KeyAttachment)sk.attachment();
                    try {
                        if ( sk.isValid() && attachment != null ) {
                            attachment.access();
                            sk.attach(attachment);
                            sk.interestOps(0); //this is a must, so that we don't have multiple threads messing with the socket
                            attachment.interestOps(0);
                            NioChannel channel = attachment.getChannel();
                            if (sk.isReadable() || sk.isWritable() ) {
                                if ( attachment.getComet() ) {
                                    if (!processSocket(channel, SocketStatus.OPEN))
                                        processSocket(channel, SocketStatus.DISCONNECT);
                                } else {
                                    boolean close = (!processSocket(channel));
                                    if ( close ) {
                                        channel.close();
                                        channel.getIOChannel().socket().close();
                                    }
                                }
                            } 
                        } else {
                            //invalid key
                            cancelledKey(sk, SocketStatus.ERROR);
                        }
                    } catch ( CancelledKeyException ckx ) {
                        cancelledKey(sk, SocketStatus.ERROR);
                    } catch (Throwable t) {
                        log.error("",t);
                    }
                }//while
                //process timeouts
                timeout(keyCount,hasEvents);
            }//while
            synchronized (this) {
                this.notifyAll();
            }

        }
        protected void timeout(int keyCount, boolean hasEvents) {
            long now = System.currentTimeMillis();
            //don't process timeouts too frequently, but if the selector simply timed out
            //then we can check timeouts to avoid gaps
            if ( (now < nextExpiration) && (keyCount>0 || hasEvents) ) return;
            nextExpiration = now + (long)socketProperties.getSoTimeout();
            //timeout
            Set<SelectionKey> keys = selector.keys();
            for (Iterator<SelectionKey> iter = keys.iterator(); iter.hasNext(); ) {
                SelectionKey key = iter.next();
                try {
                    KeyAttachment ka = (KeyAttachment) key.attachment();
                    if ( ka == null ) {
                        cancelledKey(key, SocketStatus.ERROR); //we don't support any keys without attachments
                    } else if ( ka.getError() ) {
                        cancelledKey(key, SocketStatus.DISCONNECT);
                    }else if ((ka.interestOps()&SelectionKey.OP_READ) == SelectionKey.OP_READ) {
                        //only timeout sockets that we are waiting for a read from
                        long delta = now - ka.getLastAccess();
                        long timeout = (ka.getTimeout()==-1)?((long) socketProperties.getSoTimeout()):(ka.getTimeout());
                        boolean isTimedout = delta > timeout;
                        if (isTimedout) {
                            key.interestOps(0); 
                            ka.interestOps(0); //avoid duplicate timeout calls
                            cancelledKey(key, SocketStatus.TIMEOUT);
                        } else {
                            long nextTime = now+(timeout-delta);
                            nextExpiration = (nextTime < nextExpiration)?nextTime:nextExpiration;
                        }
                    }//end if
                }catch ( CancelledKeyException ckx ) {
                    cancelledKey(key, SocketStatus.ERROR);
                }
            }//for
        }
    }
    
    public static class KeyAttachment {
        
        public KeyAttachment() {
            
        }
        public void reset(Poller poller, NioChannel channel) {
            this.channel = channel;
            this.poller = poller;
            lastAccess = System.currentTimeMillis();
            currentAccess = false;
            comet = false;
            timeout = -1;
            error = false;
        }
        
        public void reset() {
            reset(null,null);
        }
        
        public Poller getPoller() { return poller;}
        public void setPoller(Poller poller){this.poller = poller;}
        public long getLastAccess() { return lastAccess; }
        public void access() { access(System.currentTimeMillis()); }
        public void access(long access) { lastAccess = access; }
        public void setComet(boolean comet) { this.comet = comet; }
        public boolean getComet() { return comet; }
        public boolean getCurrentAccess() { return currentAccess; }
        public void setCurrentAccess(boolean access) { currentAccess = access; }
        public Object getMutex() {return mutex;}
        public void setTimeout(long timeout) {this.timeout = timeout;}
        public long getTimeout() {return this.timeout;}
        public boolean getError() { return error; }
        public void setError(boolean error) { this.error = error; }
        public NioChannel getChannel() { return channel;}
        public void setChannel(NioChannel channel) { this.channel = channel;}
        protected Poller poller = null;
        protected int interestOps = 0;
        public int interestOps() { return interestOps;}
        public int interestOps(int ops) { this.interestOps  = ops; return ops; }
        protected Object mutex = new Object();
        protected long lastAccess = -1;
        protected boolean currentAccess = false;
        protected boolean comet = false;
        protected long timeout = -1;
        protected boolean error = false;
        protected NioChannel channel = null;

    }



    // ----------------------------------------------------- Worker Inner Class


    /**
     * Server processor class.
     */
    protected class Worker implements Runnable {


        protected Thread thread = null;
        protected boolean available = false;
        protected Object socket = null;
        protected SocketStatus status = null;


        /**
         * Process an incoming TCP/IP connection on the specified socket.  Any
         * exception that occurs during processing must be logged and swallowed.
         * <b>NOTE</b>:  This method is called from our Connector's thread.  We
         * must assign it to our own thread so that multiple simultaneous
         * requests can be handled.
         *
         * @param socket TCP socket to process
         */
        protected synchronized void assign(Object socket) {

            // Wait for the Processor to get the previous Socket
            while (available) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
            // Store the newly available Socket and notify our thread
            this.socket = socket;
            status = null;
            available = true;
            notifyAll();

        }


        protected synchronized void assign(Object socket, SocketStatus status) {

            // Wait for the Processor to get the previous Socket
            while (available) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }

            // Store the newly available Socket and notify our thread
            this.socket = socket;
            this.status = status;
            available = true;
            notifyAll();
        }


        /**
         * Await a newly assigned Socket from our Connector, or <code>null</code>
         * if we are supposed to shut down.
         */
        protected synchronized Object await() {

            // Wait for the Connector to provide a new Socket
            while (!available) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }

            // Notify the Connector that we have received this Socket
            Object socket = this.socket;
            available = false;
            notifyAll();

            return (socket);

        }


        /**
         * The background thread that listens for incoming TCP/IP connections and
         * hands them off to an appropriate processor.
         */
        public void run() {

            // Process requests until we receive a shutdown signal
            while (running) {
                try {
                    // Wait for the next socket to be assigned
                    Object channel = await();
                    if (channel == null)
                        continue;

                    if ( channel instanceof SocketChannel) {
                        SocketChannel sc = (SocketChannel)channel;
                        if ( !setSocketOptions(sc) ) {
                            try {
                                sc.socket().close();
                                sc.close();
                            }catch ( IOException ix ) {
                                if ( log.isDebugEnabled() ) log.debug("",ix);
                            }
                        } else {
                            //now we have it registered, remove it from the cache
                            
                        }
                    } else {
                        
                        NioChannel socket = (NioChannel)channel;

                        SelectionKey key = socket.getIOChannel().keyFor(socket.getPoller().getSelector());
                        int handshake = -1;
                        try {
                            handshake = socket.handshake(key.isReadable(), key.isWritable());
                        }catch ( IOException x ) {
                            handshake = -1;
                            if ( log.isDebugEnabled() ) log.debug("Error during SSL handshake",x);
                        }catch ( CancelledKeyException ckx ) {
                            handshake = -1;
                        }
                        if ( handshake == 0 ) {
                            // Process the request from this socket
                            if ((status != null) && (handler.event(socket, status) == Handler.SocketState.CLOSED)) {
                                // Close socket and pool
                                try {
                                    KeyAttachment att = (KeyAttachment)socket.getAttachment(true);
                                    try {socket.close();}catch (Exception ignore){}
                                    if ( socket.isOpen() ) socket.close(true);
                                    key.cancel();
                                    key.attach(null);
                                    nioChannels.offer(socket);
                                    if ( att!=null ) keyCache.offer(att);
                                }catch ( Exception x ) {
                                    log.error("",x);
                                }
                            } else if ((status == null) && (handler.process(socket) == Handler.SocketState.CLOSED)) {
                                // Close socket and pool
                                try {
                                    KeyAttachment att = (KeyAttachment)socket.getAttachment(true);
                                    try {socket.close();}catch (Exception ignore){}
                                    if ( socket.isOpen() ) socket.close(true);
                                    key.cancel();
                                    key.attach(null);
                                    nioChannels.offer(socket);
                                    if ( att!=null ) keyCache.offer(att);
                                }catch ( Exception x ) {
                                    log.error("",x);
                                }
                            }
                        } else if (handshake == -1 ) {
                            socket.getPoller().cancelledKey(key,SocketStatus.DISCONNECT);
                            try {socket.close(true);}catch (IOException ignore){}
                            nioChannels.offer(socket);
                        } else {
                            final SelectionKey fk = key;
                            final int intops = handshake;
                            final KeyAttachment ka = (KeyAttachment)fk.attachment();
                            ka.getPoller().add(socket,intops);
                        }
                    }
                } finally {
                    //dereference socket to let GC do its job
                    socket = null;
                    // Finish up this request
                    recycleWorkerThread(this);
                }
            }
        }


        /**
         * Start the background processing thread.
         */
        public void start() {
            thread = new Thread(this);
            thread.setName(getName() + "-" + (++curThreads));
            thread.setDaemon(true);
            thread.start();
        }


    }

    // ------------------------------------------------ Application Buffer Handler
    public class NioBufferHandler implements ApplicationBufferHandler {
        protected ByteBuffer readbuf = null;
        protected ByteBuffer writebuf = null;
        
        public NioBufferHandler(int readsize, int writesize, boolean direct) {
            if ( direct ) {
                readbuf = ByteBuffer.allocateDirect(readsize);
                writebuf = ByteBuffer.allocateDirect(writesize);
            }else {
                readbuf = ByteBuffer.allocate(readsize);
                writebuf = ByteBuffer.allocate(writesize);
            }
        }
        
        public ByteBuffer expand(ByteBuffer buffer, int remaining) {return buffer;}
        public ByteBuffer getReadBuffer() {return readbuf;}
        public ByteBuffer getWriteBuffer() {return writebuf;}

    }

    // ------------------------------------------------ Handler Inner Interface


    /**
     * Bare bones interface used for socket processing. Per thread data is to be
     * stored in the ThreadWithAttributes extra folders, or alternately in
     * thread local fields.
     */
    public interface Handler {
        public enum SocketState {
            OPEN, CLOSED, LONG
        }
        public SocketState process(NioChannel socket);
        public SocketState event(NioChannel socket, SocketStatus status);
    }


    // ------------------------------------------------- WorkerStack Inner Class


    public class WorkerStack {

        protected Worker[] workers = null;
        protected int end = 0;

        public WorkerStack(int size) {
            workers = new Worker[size];
        }

        /** 
         * Put the object into the queue.
         * 
         * @param   object      the object to be appended to the queue (first element). 
         */
        public void push(Worker worker) {
            workers[end++] = worker;
        }

        /**
         * Get the first object out of the queue. Return null if the queue
         * is empty. 
         */
        public Worker pop() {
            if (end > 0) {
                return workers[--end];
            }
            return null;
        }

        /**
         * Get the first object out of the queue, Return null if the queue
         * is empty.
         */
        public Worker peek() {
            return workers[end];
        }

        /**
         * Is the queue empty?
         */
        public boolean isEmpty() {
            return (end == 0);
        }

        /**
         * How many elements are there in this queue?
         */
        public int size() {
            return (end);
        }
    }


    // ---------------------------------------------- SocketOptionsProcessor Inner Class


    /**
     * This class is the equivalent of the Worker, but will simply use in an
     * external Executor thread pool.
     */
    protected class SocketOptionsProcessor implements Runnable {

        protected SocketChannel sc = null;

        public SocketOptionsProcessor(SocketChannel socket) {
            this.sc = socket;
        }

        public void run() {
            if ( !setSocketOptions(sc) ) {
                try {
                    sc.socket().close();
                    sc.close();
                }catch ( IOException ix ) {
                    if ( log.isDebugEnabled() ) log.debug("",ix);
                }
            }
        }
    }
    // ---------------------------------------------- SocketProcessor Inner Class


    /**
     * This class is the equivalent of the Worker, but will simply use in an
     * external Executor thread pool.
     */
    protected class SocketProcessor implements Runnable {

        protected NioChannel socket = null;

        public SocketProcessor(NioChannel socket) {
            this.socket = socket;
        }

        public void run() {

            // Process the request from this socket
            if (handler.process(socket) == Handler.SocketState.CLOSED) {
                // Close socket and pool
                try {
                    try {socket.close();}catch (Exception ignore){}
                    if ( socket.isOpen() ) socket.close(true);
                } catch ( Exception x ) {
                    log.error("",x);
                }
                socket = null;
            }

        }

    }


    // --------------------------------------- SocketEventProcessor Inner Class


    /**
     * This class is the equivalent of the Worker, but will simply use in an
     * external Executor thread pool.
     */
    protected class SocketEventProcessor implements Runnable {

        protected NioChannel socket = null;
        protected SocketStatus status = null; 

        public SocketEventProcessor(NioChannel socket, SocketStatus status) {
            this.socket = socket;
            this.status = status;
        }

        public void run() {

            // Process the request from this socket
            if (handler.event(socket, status) == Handler.SocketState.CLOSED) {
                // Close socket and pool
                try {
                    try {socket.close();}catch (Exception ignore){}
                    if ( socket.isOpen() ) socket.close(true);
                } catch ( Exception x ) {
                    log.error("",x);
                }
                socket = null;
            }

        }

    }


}
