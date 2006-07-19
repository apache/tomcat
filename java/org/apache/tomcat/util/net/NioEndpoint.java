/*
 *  Copyright 2005-2006 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.Executor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tomcat.jni.Error;
import org.apache.tomcat.jni.Library;
import org.apache.tomcat.jni.Poll;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.Status;
import org.apache.tomcat.util.res.StringManager;

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


    /**
     * Root APR memory pool.
     */
    protected long rootPool = 0;


    /**
     * Server socket "pointer".
     */
    protected ServerSocketChannel serverSock = null;


    /**
     * APR memory pool for the server socket.
     */
    protected long serverSockPool = 0;


    /**
     * SSL context.
     */
    protected long sslContext = 0;


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
    protected int maxThreads = 40;
    public void setMaxThreads(int maxThreads) { this.maxThreads = maxThreads; }
    public int getMaxThreads() { return maxThreads; }


    /**
     * Priority of the acceptor and poller threads.
     */
    protected int threadPriority = Thread.NORM_PRIORITY;
    public void setThreadPriority(int threadPriority) { this.threadPriority = threadPriority; }
    public int getThreadPriority() { return threadPriority; }


    /**
     * Size of the socket poller.
     */
    protected int pollerSize = 8 * 1024;
    public void setPollerSize(int pollerSize) { this.pollerSize = pollerSize; }
    public int getPollerSize() { return pollerSize; }



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


    /**
     * Socket TCP no delay.
     */
    protected boolean tcpNoDelay = false;
    public boolean getTcpNoDelay() { return tcpNoDelay; }
    public void setTcpNoDelay(boolean tcpNoDelay) { this.tcpNoDelay = tcpNoDelay; }


    /**
     * Socket linger.
     */
    protected int soLinger = 100;
    public int getSoLinger() { return soLinger; }
    public void setSoLinger(int soLinger) { this.soLinger = soLinger; }


    /**
     * Socket timeout.
     */
    protected int soTimeout = -1;
    public int getSoTimeout() { return soTimeout; }
    public void setSoTimeout(int soTimeout) { this.soTimeout = soTimeout; }


    /**
     * Timeout on first request read before going to the poller, in ms.
     */
    protected int firstReadTimeout = 60000;
    public int getFirstReadTimeout() { return firstReadTimeout; }
    public void setFirstReadTimeout(int firstReadTimeout) { this.firstReadTimeout = firstReadTimeout; }


    /**
     * Poll interval, in microseconds. The smaller the value, the more CPU the poller
     * will use, but the more responsive to activity it will be.
     */
    protected int pollTime = 2000;
    public int getPollTime() { return pollTime; }
    public void setPollTime(int pollTime) { if (pollTime > 0) { this.pollTime = pollTime; } }


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
    public Poller getPoller() {
        pollerRoundRobin = (pollerRoundRobin + 1) % pollers.length;
        Poller poller = pollers[pollerRoundRobin];
        return poller;
    }


    /**
     * The socket poller used for Comet support.
     */
    public Poller getCometPoller() {
        Poller poller = getPoller();
        return poller;
    }


    /**
     * Dummy maxSpareThreads property.
     */
    public int getMaxSpareThreads() { return 0; }


    /**
     * Dummy minSpareThreads property.
     */
    public int getMinSpareThreads() { return 0; }


    /**
     * SSL engine.
     */
    protected String SSLEngine = "off";
    public String getSSLEngine() { return SSLEngine; }
    public void setSSLEngine(String SSLEngine) { this.SSLEngine = SSLEngine; }


    /**
     * SSL protocols.
     */
    protected String SSLProtocol = "all";
    public String getSSLProtocol() { return SSLProtocol; }
    public void setSSLProtocol(String SSLProtocol) { this.SSLProtocol = SSLProtocol; }


    /**
     * SSL password (if a cert is encrypted, and no password has been provided, a callback
     * will ask for a password).
     */
    protected String SSLPassword = null;
    public String getSSLPassword() { return SSLPassword; }
    public void setSSLPassword(String SSLPassword) { this.SSLPassword = SSLPassword; }


    /**
     * SSL cipher suite.
     */
    protected String SSLCipherSuite = "ALL";
    public String getSSLCipherSuite() { return SSLCipherSuite; }
    public void setSSLCipherSuite(String SSLCipherSuite) { this.SSLCipherSuite = SSLCipherSuite; }


    /**
     * SSL certificate file.
     */
    protected String SSLCertificateFile = null;
    public String getSSLCertificateFile() { return SSLCertificateFile; }
    public void setSSLCertificateFile(String SSLCertificateFile) { this.SSLCertificateFile = SSLCertificateFile; }


    /**
     * SSL certificate key file.
     */
    protected String SSLCertificateKeyFile = null;
    public String getSSLCertificateKeyFile() { return SSLCertificateKeyFile; }
    public void setSSLCertificateKeyFile(String SSLCertificateKeyFile) { this.SSLCertificateKeyFile = SSLCertificateKeyFile; }


    /**
     * SSL certificate chain file.
     */
    protected String SSLCertificateChainFile = null;
    public String getSSLCertificateChainFile() { return SSLCertificateChainFile; }
    public void setSSLCertificateChainFile(String SSLCertificateChainFile) { this.SSLCertificateChainFile = SSLCertificateChainFile; }


    /**
     * SSL CA certificate path.
     */
    protected String SSLCACertificatePath = null;
    public String getSSLCACertificatePath() { return SSLCACertificatePath; }
    public void setSSLCACertificatePath(String SSLCACertificatePath) { this.SSLCACertificatePath = SSLCACertificatePath; }


    /**
     * SSL CA certificate file.
     */
    protected String SSLCACertificateFile = null;
    public String getSSLCACertificateFile() { return SSLCACertificateFile; }
    public void setSSLCACertificateFile(String SSLCACertificateFile) { this.SSLCACertificateFile = SSLCACertificateFile; }


    /**
     * SSL CA revocation path.
     */
    protected String SSLCARevocationPath = null;
    public String getSSLCARevocationPath() { return SSLCARevocationPath; }
    public void setSSLCARevocationPath(String SSLCARevocationPath) { this.SSLCARevocationPath = SSLCARevocationPath; }


    /**
     * SSL CA revocation file.
     */
    protected String SSLCARevocationFile = null;
    public String getSSLCARevocationFile() { return SSLCARevocationFile; }
    public void setSSLCARevocationFile(String SSLCARevocationFile) { this.SSLCARevocationFile = SSLCARevocationFile; }


    /**
     * SSL verify client.
     */
    protected String SSLVerifyClient = "none";
    public String getSSLVerifyClient() { return SSLVerifyClient; }
    public void setSSLVerifyClient(String SSLVerifyClient) { this.SSLVerifyClient = SSLVerifyClient; }


    /**
     * SSL verify depth.
     */
    protected int SSLVerifyDepth = 10;
    public int getSSLVerifyDepth() { return SSLVerifyDepth; }
    public void setSSLVerifyDepth(int SSLVerifyDepth) { this.SSLVerifyDepth = SSLVerifyDepth; }


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
        if (pollerThreadCount != 1) {
            // limit to one poller, no need for others
            pollerThreadCount = 1;
        }

        // Initialize SSL if needed
        if (!"off".equalsIgnoreCase(SSLEngine)) {
            // Initialize SSL
            // FIXME: one per VM call ?
            if ("on".equalsIgnoreCase(SSLEngine)) {
                SSL.initialize(null);
            } else {
                SSL.initialize(SSLEngine);
            }
            // SSL protocol
            int value = SSL.SSL_PROTOCOL_ALL;
            if ("SSLv2".equalsIgnoreCase(SSLProtocol)) {
                value = SSL.SSL_PROTOCOL_SSLV2;
            } else if ("SSLv3".equalsIgnoreCase(SSLProtocol)) {
                value = SSL.SSL_PROTOCOL_SSLV3;
            } else if ("TLSv1".equalsIgnoreCase(SSLProtocol)) {
                value = SSL.SSL_PROTOCOL_TLSV1;
            } else if ("SSLv2+SSLv3".equalsIgnoreCase(SSLProtocol)) {
                value = SSL.SSL_PROTOCOL_SSLV2 | SSL.SSL_PROTOCOL_SSLV3;
            }
//            // Create SSL Context
//            sslContext = SSLContext.make(rootPool, value, SSL.SSL_MODE_SERVER);
//            // List the ciphers that the client is permitted to negotiate
//            SSLContext.setCipherSuite(sslContext, SSLCipherSuite);
//            // Load Server key and certificate
//            SSLContext.setCertificate(sslContext, SSLCertificateFile, SSLCertificateKeyFile, SSLPassword, SSL.SSL_AIDX_RSA);
//            // Set certificate chain file
//            SSLContext.setCertificateChainFile(sslContext, SSLCertificateChainFile, false);
//            // Support Client Certificates
//            SSLContext.setCACertificate(sslContext, SSLCACertificateFile, SSLCACertificatePath);
//            // Set revocation
//            SSLContext.setCARevocation(sslContext, SSLCARevocationFile, SSLCARevocationPath);
//            // Client certificate verification
//            value = SSL.SSL_CVERIFY_NONE;
//            if ("optional".equalsIgnoreCase(SSLVerifyClient)) {
//                value = SSL.SSL_CVERIFY_OPTIONAL;
//            } else if ("require".equalsIgnoreCase(SSLVerifyClient)) {
//                value = SSL.SSL_CVERIFY_REQUIRE;
//            } else if ("optionalNoCA".equalsIgnoreCase(SSLVerifyClient)) {
//                value = SSL.SSL_CVERIFY_OPTIONAL_NO_CA;
//            }
//            SSLContext.setVerify(sslContext, value, SSLVerifyDepth);
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
        sslContext = 0;
        initialized = false;
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Get a sequence number used for thread naming.
     */
    protected int getSequence() {
        return sequence++;
    }


    /**
     * Unlock the server socket accept using a bugus connection.
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

            // 1: Set socket options: timeout, linger, etc
            if (soLinger >= 0)
                socket.socket().setSoLinger(true,soLinger);
            if (tcpNoDelay)
                socket.socket().setTcpNoDelay(true);
            if (soTimeout > 0)
                socket.socket().setSoTimeout(soTimeout);


            // 2: SSL handshake
            step = 2;
            if (sslContext != 0) {
//                SSLSocket.attach(sslContext, socket);
//                if (SSLSocket.handshake(socket) != 0) {
//                    if (log.isDebugEnabled()) {
//                        log.debug(sm.getString("endpoint.err.handshake") + ": " + SSL.getLastError());
//                    }
//                    return false;
//                }
            }
            
            getPoller().register(socket);

        } catch (Throwable t) {
            if (log.isDebugEnabled()) {
                if (step == 2) {
                    log.debug(sm.getString("endpoint.err.handshake"), t);
                } else {
                    log.debug(sm.getString("endpoint.err.unexpected"), t);
                }
            }
            // Tell to close the socket
            return false;
        }
        return true;
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
                    workers.wait();
                }
            } catch (InterruptedException e) {
                // Ignore
            }
            workerThread = createWorkerThread();
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


    /**
     * Allocate a new poller of the specified size.
     */
    protected long allocatePoller(int size, long pool, int timeout) {
        try {
            return Poll.create(size, pool, 0, timeout * 1000);
        } catch (Error e) {
            if (Status.APR_STATUS_IS_EINVAL(e.getError())) {
                log.info(sm.getString("endpoint.poll.limitedpollsize", "" + size));
                return 0;
            } else {
                log.error(sm.getString("endpoint.poll.initfail"), e);
                return -1;
            }
        }
    }


    /**
     * Process given socket.
     */
    protected boolean processSocket(SocketChannel socket) {
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
    protected boolean processSocket(SocketChannel socket, boolean error) {
        try {
            if (executor == null) {
                getWorkerThread().assign(socket, error);
            } else {
                executor.execute(new SocketEventProcessor(socket, error));
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
                    if(!setSocketOptions(socket))
                    {
                        // Close socket right away
                        socket.socket().close();
                        socket.close();
                    }
                } catch (Throwable t) {
                    log.error(sm.getString("endpoint.accept.fail"), t);
                }

                // The processor will recycle itself when it finishes

            }

        }

    }


    // ----------------------------------------------------- Poller Inner Class


    /**
     * Poller class.
     */
    public class Poller implements Runnable {

        protected Selector selector;
        protected LinkedList<Runnable> events = new LinkedList<Runnable>();
        protected boolean close = false;
        protected long nextExpiration = 0;//optimize expiration handling

        protected int keepAliveCount = 0;
        public int getKeepAliveCount() { return keepAliveCount; }



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
            try {
                synchronized (this) {
                    this.wait(pollTime / 1000);
                }
            } catch (InterruptedException e) {
                // Ignore
            }
            close = true;
        }
        
        public void addEvent(Runnable event) {
            synchronized (events) {
                events.add(event);
            }
            selector.wakeup();
        }

        /**
         * Add specified socket and associated pool to the poller. The socket will
         * be added to a temporary array, and polled first after a maximum amount
         * of time equal to pollTime (in most cases, latency will be much lower,
         * however).
         *
         * @param socket to add to the poller
         */
        public void add(final SocketChannel socket) {
            final SelectionKey key = socket.keyFor(selector);
            KeyAttachment att = (KeyAttachment)key.attachment();
            if ( att != null ) att.setWakeUp(false);
            Runnable r = new Runnable() {
                public void run() {
                    try {
                        if (key != null) key.interestOps(SelectionKey.OP_READ);
                    }catch ( CancelledKeyException ckx ) {
                        try {
                            if ( key != null && key.attachment() != null ) {
                                KeyAttachment ka = (KeyAttachment)key.attachment();
                                ka.setError(true); //set to collect this socket immediately
                            }
                            socket.socket().close();
                            socket.close();
                        } catch ( Exception ignore ) {}
                    }
                }
            };
            addEvent(r);
        }

        public boolean events() {
            boolean result = false;
            synchronized (events) {
                Runnable r = null;
                result = (events.size() > 0);
                while ( (events.size() > 0) && (r = events.removeFirst()) != null ) {
                    try {
                        r.run();
                    } catch ( Exception x ) {
                        log.error("",x);
                    }
                }
                events.clear();
            }
            return result;
        }
        
        public void register(final SocketChannel socket)
        {
            SelectionKey key = socket.keyFor(selector);
            Runnable r = new Runnable() {
                public void run() {
                    try {
                        socket.register(selector, SelectionKey.OP_READ, new KeyAttachment());
                    } catch (Exception x) {
                        log.error("", x);
                    }
                }
    
            };
            synchronized (events) {
                events.add(r);
            }
            selector.wakeup();
        }
        
        public void cancelledKey(SelectionKey key) {
            try {
                KeyAttachment ka = (KeyAttachment) key.attachment();
                key.cancel();
                if (ka != null && ka.getComet()) processSocket( (SocketChannel) key.channel(), true);
                key.channel().close();
            } catch (IOException e) {
                if ( log.isDebugEnabled() ) log.debug("",e);
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
                } catch (Throwable x) {
                    log.error("",x);
                    continue;
                }

                //either we timed out or we woke up, process events first
                if ( keyCount == 0 ) hasEvents = (hasEvents | events());

                //if (keyCount == 0) continue;

                Iterator iterator = keyCount > 0 ? selector.selectedKeys().iterator() : null;
                // Walk through the collection of ready keys and dispatch
                // any active event.
                while (iterator != null && iterator.hasNext()) {
                    SelectionKey sk = (SelectionKey) iterator.next();
                    iterator.remove();
                    KeyAttachment attachment = (KeyAttachment)sk.attachment();
                    try {
                        if(attachment == null) attachment = new KeyAttachment();
                        attachment.access();
                        sk.attach(attachment);

                        int readyOps = sk.readyOps();
                        sk.interestOps(sk.interestOps() & ~readyOps);
                        SocketChannel channel = (SocketChannel)sk.channel();
                        boolean read = sk.isReadable();
                        if (read) {
                            if ( attachment.getWakeUp() ) {
                                attachment.setWakeUp(false);
                                synchronized (attachment.getMutex()) {attachment.getMutex().notifyAll();}
                            } else if ( attachment.getComet() ) {
                                if (!processSocket(channel,false)) processSocket(channel,true);
                            } else {
                                boolean close = (!processSocket(channel));
                                if ( close ) {
                                    channel.socket().close();
                                    channel.close();
                                }
                            }
                        }
                        if (sk.isValid() && sk.isWritable()) {
                        }
                    } catch ( CancelledKeyException ckx ) {
                        if (attachment!=null && attachment.getComet()) processSocket( (SocketChannel) sk.channel(), true);
                        try {
                            sk.channel().close();
                        }catch ( Exception ignore){}
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
            nextExpiration = now + (long)soTimeout;
            //timeout
            Set<SelectionKey> keys = selector.keys();
            for (Iterator<SelectionKey> iter = keys.iterator(); iter.hasNext(); ) {
                SelectionKey key = iter.next();
                try {
                    KeyAttachment ka = (KeyAttachment) key.attachment();
                    if ( ka == null ) {
                        cancelledKey(key); //we don't support any keys without attachments
                    } else if ( ka.getError() ) {
                        cancelledKey(key);
                    }else if ((key.interestOps()&SelectionKey.OP_READ) == SelectionKey.OP_READ) {
                        //only timeout sockets that we are waiting for a read from
                        long delta = now - ka.getLastAccess();
                        long timeout = (ka.getTimeout()==-1)?((long) soTimeout):(ka.getTimeout());
                        boolean isTimedout = delta > timeout;
                        if (isTimedout) {
                            cancelledKey(key);
                        } else {
                            long nextTime = now+(timeout-delta);
                            nextExpiration = (nextTime < nextExpiration)?nextTime:nextExpiration;
                        }
                    }//end if
                }catch ( CancelledKeyException ckx ) {
                    cancelledKey(key);
                }
            }//for
        }
    }
    
    public static class KeyAttachment {

        public long getLastAccess() { return lastAccess; }
        public void access() { access(System.currentTimeMillis()); }
        public void access(long access) { lastAccess = access; }
        public void setComet(boolean comet) { this.comet = comet; }
        public boolean getComet() { return comet; }
        public boolean getCurrentAccess() { return currentAccess; }
        public void setCurrentAccess(boolean access) { currentAccess = access; }
        public boolean getWakeUp() { return wakeUp; }
        public void setWakeUp(boolean wakeUp) { this.wakeUp = wakeUp; }
        public Object getMutex() {return mutex;}
        public void setTimeout(long timeout) {this.timeout = timeout;}
        public long getTimeout() {return this.timeout;}
        public boolean getError() { return error; }
        public void setError(boolean error) { this.error = error; }
        protected Object mutex = new Object();
        protected boolean wakeUp = false;
        protected long lastAccess = System.currentTimeMillis();
        protected boolean currentAccess = false;
        protected boolean comet = false;
        protected long timeout = -1;
        protected boolean error = false;

    }



    // ----------------------------------------------------- Worker Inner Class


    /**
     * Server processor class.
     */
    protected class Worker implements Runnable {


        protected Thread thread = null;
        protected boolean available = false;
        protected SocketChannel socket = null;
        protected boolean event = false;
        protected boolean error = false;


        /**
         * Process an incoming TCP/IP connection on the specified socket.  Any
         * exception that occurs during processing must be logged and swallowed.
         * <b>NOTE</b>:  This method is called from our Connector's thread.  We
         * must assign it to our own thread so that multiple simultaneous
         * requests can be handled.
         *
         * @param socket TCP socket to process
         */
        protected synchronized void assign(SocketChannel socket) {

            // Wait for the Processor to get the previous Socket
            while (available) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }

            // Store the newly available Socket and notify our thread
            this.socket = socket;
            event = false;
            error = false;
            available = true;
            notifyAll();

        }


        protected synchronized void assign(SocketChannel socket, boolean error) {

            // Wait for the Processor to get the previous Socket
            while (available) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }

            // Store the newly available Socket and notify our thread
            this.socket = socket;
            event = true;
            this.error = error;
            available = true;
            notifyAll();
        }


        /**
         * Await a newly assigned Socket from our Connector, or <code>null</code>
         * if we are supposed to shut down.
         */
        protected synchronized SocketChannel await() {

            // Wait for the Connector to provide a new Socket
            while (!available) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }

            // Notify the Connector that we have received this Socket
            SocketChannel socket = this.socket;
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

                // Wait for the next socket to be assigned
                SocketChannel socket = await();
                if (socket == null)
                    continue;

                // Process the request from this socket
                if ((event) && (handler.event(socket, error) == Handler.SocketState.CLOSED)) {
                    // Close socket and pool
                    try {
                        socket.socket().close();
                        socket.close();
                    }catch ( Exception x ) {
                        log.error("",x);
                    }
                } else if ((!event) && (handler.process(socket) == Handler.SocketState.CLOSED)) {
                    // Close socket and pool
                    try {
                        socket.socket().close();
                        socket.close();
                    }catch ( Exception x ) {
                        log.error("",x);
                    }
                }

                // Finish up this request
                recycleWorkerThread(this);

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
        public SocketState process(SocketChannel socket);
        public SocketState event(SocketChannel socket, boolean error);
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


    // ---------------------------------------------- SocketProcessor Inner Class


    /**
     * This class is the equivalent of the Worker, but will simply use in an
     * external Executor thread pool.
     */
    protected class SocketProcessor implements Runnable {

        protected SocketChannel socket = null;

        public SocketProcessor(SocketChannel socket) {
            this.socket = socket;
        }

        public void run() {

            // Process the request from this socket
            if (handler.process(socket) == Handler.SocketState.CLOSED) {
                // Close socket and pool
                try {
                    socket.socket().close();
                    socket.close();
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

        protected SocketChannel socket = null;
        protected boolean error = false; 

        public SocketEventProcessor(SocketChannel socket, boolean error) {
            this.socket = socket;
            this.error = error;
        }

        public void run() {

            // Process the request from this socket
            if (handler.event(socket, error) == Handler.SocketState.CLOSED) {
                // Close socket and pool
                try {
                    socket.socket().close();
                    socket.close();
                } catch ( Exception x ) {
                    log.error("",x);
                }
                socket = null;
            }

        }

    }


}
