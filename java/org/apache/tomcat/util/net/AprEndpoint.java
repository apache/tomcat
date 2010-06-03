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

import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.RejectedExecutionException;

import org.apache.catalina.Globals;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jni.Address;
import org.apache.tomcat.jni.Error;
import org.apache.tomcat.jni.File;
import org.apache.tomcat.jni.Library;
import org.apache.tomcat.jni.OS;
import org.apache.tomcat.jni.Poll;
import org.apache.tomcat.jni.Pool;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLContext;
import org.apache.tomcat.jni.SSLSocket;
import org.apache.tomcat.jni.Socket;
import org.apache.tomcat.jni.Status;
import org.apache.catalina.core.AprLifecycleListener;


/**
 * APR tailored thread pool, providing the following services:
 * <ul>
 * <li>Socket acceptor thread</li>
 * <li>Socket poller thread</li>
 * <li>Sendfile thread</li>
 * <li>Worker threads pool</li>
 * </ul>
 *
 * When switching to Java 5, there's an opportunity to use the virtual
 * machine's thread pool.
 *
 * @author Mladen Turk
 * @author Remy Maucherat
 */
public class AprEndpoint extends AbstractEndpoint {


    // -------------------------------------------------------------- Constants


    private static final Log log = LogFactory.getLog(AprEndpoint.class);


    // ----------------------------------------------------------------- Fields
    /**
     * Root APR memory pool.
     */
    protected long rootPool = 0;


    /**
     * Server socket "pointer".
     */
    protected long serverSock = 0;


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
     * Defer accept.
     */
    protected boolean deferAccept = true;
    public void setDeferAccept(boolean deferAccept) { this.deferAccept = deferAccept; }
    public boolean getDeferAccept() { return deferAccept; }
    

    /**
     * Size of the socket poller.
     */
    protected int pollerSize = 8 * 1024;
    public void setPollerSize(int pollerSize) { this.pollerSize = pollerSize; }
    public int getPollerSize() { return pollerSize; }


    /**
     * Size of the sendfile (= concurrent files which can be served).
     */
    protected int sendfileSize = 1 * 1024;
    public void setSendfileSize(int sendfileSize) { this.sendfileSize = sendfileSize; }
    public int getSendfileSize() { return sendfileSize; }


    /**
     * Handling of accepted sockets.
     */
    protected Handler handler = null;
    public void setHandler(Handler handler ) { this.handler = handler; }
    public Handler getHandler() { return handler; }


    /**
     * Poll interval, in microseconds. The smaller the value, the more CPU the poller
     * will use, but the more responsive to activity it will be.
     */
    protected int pollTime = 2000;
    public int getPollTime() { return pollTime; }
    public void setPollTime(int pollTime) { if (pollTime > 0) { this.pollTime = pollTime; } }


    /**
     * Use sendfile for sending static files.
     */
    protected boolean useSendfile = Library.APR_HAS_SENDFILE;
    public void setUseSendfile(boolean useSendfile) { this.useSendfile = useSendfile; }
    public boolean getUseSendfile() { return useSendfile; }


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
     * Sendfile thread count.
     */
    protected int sendfileThreadCount = 0;
    public void setSendfileThreadCount(int sendfileThreadCount) { this.sendfileThreadCount = sendfileThreadCount; }
    public int getSendfileThreadCount() { return sendfileThreadCount; }


    /**
     * Poller thread count.
     */
    protected int pollerThreadCount = 0;
    public void setPollerThreadCount(int pollerThreadCount) { this.pollerThreadCount = pollerThreadCount; }
    public int getPollerThreadCount() { return pollerThreadCount; }


    /**
     * The socket poller.
     */
    protected Poller[] pollers = null;
    protected int pollerRoundRobin = 0;
    public Poller getPoller() {
        pollerRoundRobin = (pollerRoundRobin + 1) % pollers.length;
        return pollers[pollerRoundRobin];
    }


    /**
     * The socket poller used for Comet support.
     */
    protected Poller[] cometPollers = null;
    protected int cometPollerRoundRobin = 0;
    public Poller getCometPoller() {
        cometPollerRoundRobin = (cometPollerRoundRobin + 1) % cometPollers.length;
        return cometPollers[cometPollerRoundRobin];
    }


    /**
     * The static file sender.
     */
    protected Sendfile[] sendfiles = null;
    protected int sendfileRoundRobin = 0;
    public Sendfile getSendfile() {
        sendfileRoundRobin = (sendfileRoundRobin + 1) % sendfiles.length;
        return sendfiles[sendfileRoundRobin];
    }


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


    /**
     * SSL allow insecure renegotiation for the the client that does not
     * support the secure renegotiation.
     */
    protected boolean SSLInsecureRenegotiation = false;
    public void setSSLInsecureRenegotiation(boolean SSLInsecureRenegotiation) { this.SSLInsecureRenegotiation = SSLInsecureRenegotiation; }
    public boolean getSSLInsecureRenegotiation() { return SSLInsecureRenegotiation; }

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
     * Number of sendfile sockets.
     */
    public int getSendfileCount() {
        if (sendfiles == null) {
            return 0;
        } else {
            int sendfileCount = 0;
            for (int i = 0; i < sendfiles.length; i++) {
                sendfileCount += sendfiles[i].getSendfileCount();
            }
            return sendfileCount;
        }
    }


    // ----------------------------------------------- Public Lifecycle Methods


    /**
     * Initialize the endpoint.
     */
    @Override
    public void init()
        throws Exception {

        if (initialized)
            return;
        if (!AprLifecycleListener.isAprAvailable()) {
            throw new Exception(sm.getString("endpoint.init.notavail"));
        }
        
        // Create the root APR memory pool
        rootPool = Pool.create(0);
        // Create the pool for the server socket
        serverSockPool = Pool.create(rootPool);
        // Create the APR address that will be bound
        String addressStr = null;
        if (getAddress() != null) {
            addressStr = getAddress().getHostAddress();
        }
        int family = Socket.APR_INET;
        if (Library.APR_HAVE_IPV6) {
            if (addressStr == null) {
                if (!OS.IS_BSD && !OS.IS_WIN32 && !OS.IS_WIN64)
                    family = Socket.APR_UNSPEC;
            } else if (addressStr.indexOf(':') >= 0) {
                family = Socket.APR_UNSPEC;
            }
         }

        long inetAddress = Address.info(addressStr, family,
                getPort(), 0, rootPool);
        // Create the APR server socket
        serverSock = Socket.create(Address.getInfo(inetAddress).family,
                Socket.SOCK_STREAM,
                Socket.APR_PROTO_TCP, rootPool);
        if (OS.IS_UNIX) {
            Socket.optSet(serverSock, Socket.APR_SO_REUSEADDR, 1);
        }
        // Deal with the firewalls that tend to drop the inactive sockets
        Socket.optSet(serverSock, Socket.APR_SO_KEEPALIVE, 1);
        // Bind the server socket
        int ret = Socket.bind(serverSock, inetAddress);
        if (ret != 0) {
            throw new Exception(sm.getString("endpoint.init.bind", "" + ret, Error.strerror(ret)));
        }
        // Start listening on the server socket
        ret = Socket.listen(serverSock, getBacklog());
        if (ret != 0) {
            throw new Exception(sm.getString("endpoint.init.listen", "" + ret, Error.strerror(ret)));
        }
        if (OS.IS_WIN32 || OS.IS_WIN64) {
            // On Windows set the reuseaddr flag after the bind/listen
            Socket.optSet(serverSock, Socket.APR_SO_REUSEADDR, 1);
        }

        // Sendfile usage on systems which don't support it cause major problems
        if (useSendfile && !Library.APR_HAS_SENDFILE) {
            useSendfile = false;
        }

        // Initialize thread count defaults for acceptor, poller and sendfile
        if (acceptorThreadCount == 0) {
            // FIXME: Doesn't seem to work that well with multiple accept threads
            acceptorThreadCount = 1;
        }
        if (pollerThreadCount == 0) {
            if ((OS.IS_WIN32 || OS.IS_WIN64) && (pollerSize > 1024)) {
                // The maximum per poller to get reasonable performance is 1024
                pollerThreadCount = pollerSize / 1024;
                // Adjust poller size so that it won't reach the limit
                pollerSize = pollerSize - (pollerSize % 1024);
            } else {
                // No explicit poller size limitation
                pollerThreadCount = 1;
            }
        }
        if (sendfileThreadCount == 0) {
            if ((OS.IS_WIN32 || OS.IS_WIN64) && (sendfileSize > 1024)) {
                // The maximum per poller to get reasonable performance is 1024
                sendfileThreadCount = sendfileSize / 1024;
                // Adjust poller size so that it won't reach the limit
                sendfileSize = sendfileSize - (sendfileSize % 1024);
            } else {
                // No explicit poller size limitation
                // FIXME: Default to one per CPU ?
                sendfileThreadCount = 1;
            }
        }
        
        // Delay accepting of new connections until data is available
        // Only Linux kernels 2.4 + have that implemented
        // on other platforms this call is noop and will return APR_ENOTIMPL.
        if (deferAccept) {
            if (Socket.optSet(serverSock, Socket.APR_TCP_DEFER_ACCEPT, 1) == Status.APR_ENOTIMPL) {
                deferAccept = false;
            }
        }

        // Initialize SSL if needed
        if (isSSLEnabled()) {
            
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
            // Create SSL Context
            sslContext = SSLContext.make(rootPool, value, SSL.SSL_MODE_SERVER);
            if (SSLInsecureRenegotiation) {
                boolean legacyRenegSupported = false;
                try {
                    legacyRenegSupported = SSL.hasOp(SSL.SSL_OP_ALLOW_UNSAFE_LEGACY_RENEGOTIATION);
                    if (legacyRenegSupported)
                        SSLContext.setOptions(sslContext, SSL.SSL_OP_ALLOW_UNSAFE_LEGACY_RENEGOTIATION);
                } catch (UnsatisfiedLinkError e) {
                    // Ignore
                }
                if (!legacyRenegSupported) {
                    // OpenSSL does not support unsafe legacy renegotiation.
                    log.warn(sm.getString("endpoint.warn.noInsecureReneg",
                                          SSL.versionString()));
                }
            }
            // List the ciphers that the client is permitted to negotiate
            SSLContext.setCipherSuite(sslContext, SSLCipherSuite);
            // Load Server key and certificate
            SSLContext.setCertificate(sslContext, SSLCertificateFile, SSLCertificateKeyFile, SSLPassword, SSL.SSL_AIDX_RSA);
            // Set certificate chain file
            SSLContext.setCertificateChainFile(sslContext, SSLCertificateChainFile, false);
            // Support Client Certificates
            SSLContext.setCACertificate(sslContext, SSLCACertificateFile, SSLCACertificatePath);
            // Set revocation
            SSLContext.setCARevocation(sslContext, SSLCARevocationFile, SSLCARevocationPath);
            // Client certificate verification
            value = SSL.SSL_CVERIFY_NONE;
            if ("optional".equalsIgnoreCase(SSLVerifyClient)) {
                value = SSL.SSL_CVERIFY_OPTIONAL;
            } else if ("require".equalsIgnoreCase(SSLVerifyClient)) {
                value = SSL.SSL_CVERIFY_REQUIRE;
            } else if ("optionalNoCA".equalsIgnoreCase(SSLVerifyClient)) {
                value = SSL.SSL_CVERIFY_OPTIONAL_NO_CA;
            }
            SSLContext.setVerify(sslContext, value, SSLVerifyDepth);
            // For now, sendfile is not supported with SSL
            useSendfile = false;
        }

        initialized = true;

    }


    /**
     * Start the APR endpoint, creating acceptor, poller and sendfile threads.
     */
    @Override
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
            if (getExecutor() == null) {
                createExecutor();
            }

            // Start poller threads
            pollers = new Poller[pollerThreadCount];
            for (int i = 0; i < pollerThreadCount; i++) {
                pollers[i] = new Poller(false);
                pollers[i].init();
                Thread pollerThread = new Thread(pollers[i], getName() + "-Poller-" + i);
                pollerThread.setPriority(threadPriority);
                pollerThread.setDaemon(true);
                pollerThread.start();
            }

            // Start comet poller threads
            cometPollers = new Poller[pollerThreadCount];
            for (int i = 0; i < pollerThreadCount; i++) {
                cometPollers[i] = new Poller(true);
                cometPollers[i].init();
                Thread pollerThread = new Thread(cometPollers[i], getName() + "-CometPoller-" + i);
                pollerThread.setPriority(threadPriority);
                pollerThread.setDaemon(true);
                pollerThread.start();
            }

            // Start sendfile threads
            if (useSendfile) {
                sendfiles = new Sendfile[sendfileThreadCount];
                for (int i = 0; i < sendfileThreadCount; i++) {
                    sendfiles[i] = new Sendfile();
                    sendfiles[i].init();
                    Thread sendfileThread = new Thread(sendfiles[i], getName() + "-Sendfile-" + i);
                    sendfileThread.setPriority(threadPriority);
                    sendfileThread.setDaemon(true);
                    sendfileThread.start();
                }
            }

            // Start acceptor threads
            for (int i = 0; i < acceptorThreadCount; i++) {
                Thread acceptorThread = new Thread(new Acceptor(), getName() + "-Acceptor-" + i);
                acceptorThread.setPriority(threadPriority);
                acceptorThread.setDaemon(getDaemon());
                acceptorThread.start();
            }

        }
    }


    /**
     * Unlock the server socket accept using a bogus connection.
     */
    @Override
    protected void unlockAccept() {
        java.net.Socket s = null;
        InetSocketAddress saddr = null;
        try {
            // Need to create a connection to unlock the accept();
            if (getAddress() == null) {
                saddr = new InetSocketAddress("localhost", getPort());
            } else {
                saddr = new InetSocketAddress(getAddress(),getPort());
            }
            s = new java.net.Socket();
            s.setSoTimeout(getSocketProperties().getSoTimeout());
            // TODO Consider hard-coding to s.setSoLinger(true,0)
            s.setSoLinger(getSocketProperties().getSoLingerOn(),getSocketProperties().getSoLingerTime());
            if (log.isDebugEnabled()) {
                log.debug("About to unlock socket for:"+saddr);
            }
            s.connect(saddr,getSocketProperties().getUnlockTimeout());
            /*
             * In the case of a deferred accept / accept filters we need to
             * send data to wake up the accept. Send OPTIONS * to bypass even
             * BSD accept filters. The Acceptor will discard it.
             */
            if (deferAccept) {
                OutputStreamWriter sw;

                sw = new OutputStreamWriter(s.getOutputStream(), "ISO-8859-1");
                sw.write("OPTIONS * HTTP/1.0\r\n" +
                         "User-Agent: Tomcat wakeup connection\r\n\r\n");
                sw.flush();
            }
            if (log.isDebugEnabled()) {
                log.debug("Socket unlock completed for:"+saddr);
            }
        } catch(Exception e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("endpoint.debug.unlock", "" + getPort()), e);
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
     * Pause the endpoint, which will make it stop accepting new sockets.
     */
    @Override
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
    @Override
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
            for (int i = 0; i < cometPollers.length; i++) {
                cometPollers[i].destroy();
            }
            cometPollers = null;
            if (useSendfile) {
                for (int i = 0; i < sendfiles.length; i++) {
                    sendfiles[i].destroy();
                }
                sendfiles = null;
            }
        }
        shutdownExecutor();
    }


    /**
     * Deallocate APR memory pools, and close server socket.
     */
    @Override
    public void destroy() throws Exception {
        if (running) {
            stop();
        }
        Pool.destroy(serverSockPool);
        serverSockPool = 0;
        // Close server socket
        Socket.close(serverSock);
        serverSock = 0;
        sslContext = 0;
        // Close all APR memory pools and resources
        Pool.destroy(rootPool);
        rootPool = 0;
        initialized = false;
    }


    // ------------------------------------------------------ Protected Methods



    /**
     * Process the specified connection.
     */
    protected boolean setSocketOptions(long socket) {
        // Process the connection
        int step = 1;
        try {

            // 1: Set socket options: timeout, linger, etc
            if (socketProperties.getSoLingerOn() && socketProperties.getSoLingerTime() >= 0)
                Socket.optSet(socket, Socket.APR_SO_LINGER, socketProperties.getSoLingerTime());
            if (socketProperties.getTcpNoDelay())
                Socket.optSet(socket, Socket.APR_TCP_NODELAY, (socketProperties.getTcpNoDelay() ? 1 : 0));
            if (socketProperties.getSoTimeout() > 0)
                Socket.timeoutSet(socket, socketProperties.getSoTimeout() * 1000);

            // 2: SSL handshake
            step = 2;
            if (sslContext != 0) {
                SSLSocket.attach(sslContext, socket);
                if (SSLSocket.handshake(socket) != 0) {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("endpoint.err.handshake") + ": " + SSL.getLastError());
                    }
                    return false;
                }
            }

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
    protected boolean processSocketWithOptions(long socket) {
        try {
            getExecutor().execute(new SocketWithOptionsProcessor(socket));
        } catch (RejectedExecutionException x) {
            log.warn("Socket processing request was rejected for:"+socket,x);
            return false;
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
    protected boolean processSocket(long socket) {
        try {
            getExecutor().execute(new SocketProcessor(socket));
        } catch (RejectedExecutionException x) {
            log.warn("Socket processing request was rejected for:"+socket,x);
            return false;
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
    protected boolean processSocket(long socket, SocketStatus status) {
        try {
            if (status == SocketStatus.OPEN || status == SocketStatus.STOP ||
                    status == SocketStatus.TIMEOUT) {
                SocketEventProcessor proc =
                    new SocketEventProcessor(socket, status);
                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                try {
                    if (Globals.IS_SECURITY_ENABLED) {
                        PrivilegedAction<Void> pa = new PrivilegedSetTccl(
                                getClass().getClassLoader());
                        AccessController.doPrivileged(pa);
                    } else {
                        Thread.currentThread().setContextClassLoader(
                                getClass().getClassLoader());
                    }                
                    getExecutor().execute(proc);
                } finally {
                    if (Globals.IS_SECURITY_ENABLED) {
                        PrivilegedAction<Void> pa = new PrivilegedSetTccl(loader);
                        AccessController.doPrivileged(pa);
                    } else {
                        Thread.currentThread().setContextClassLoader(loader);
                    }
                }            }
        } catch (RejectedExecutionException x) {
            log.warn("Socket processing request was rejected for:"+socket,x);
            return false;
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
                    long socket = Socket.accept(serverSock);
                    /*
                     * In the case of a deferred accept unlockAccept needs to
                     * send data. This data will be rubbish, so destroy the
                     * socket and don't process it.
                     */
                    if (deferAccept && (paused || !running)) {
                        Socket.destroy(socket);
                        continue;
                    }
                    // Hand this socket off to an appropriate processor
                    if (!processSocketWithOptions(socket)) {
                        // Close socket and pool right away
                        Socket.destroy(socket);
                    }
                } catch (Throwable t) {
                    if (running) log.error(sm.getString("endpoint.accept.fail"), t);
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

        protected long serverPollset = 0;
        protected long pool = 0;
        protected long[] desc;

        protected long[] addS;
        protected volatile int addCount = 0;
        
        protected boolean comet = true;

        protected volatile int keepAliveCount = 0;
        public int getKeepAliveCount() { return keepAliveCount; }

        public Poller(boolean comet) {
            this.comet = comet;
        }
        
        /**
         * Create the poller. With some versions of APR, the maximum poller size will
         * be 62 (recompiling APR is necessary to remove this limitation).
         */
        protected void init() {
            pool = Pool.create(serverSockPool);
            int size = pollerSize / pollerThreadCount;
            int timeout = getKeepAliveTimeout();
            if (timeout <= 0) {
                timeout = socketProperties.getSoTimeout();
            }
            serverPollset = allocatePoller(size, pool, timeout);
            if (serverPollset == 0 && size > 1024) {
                size = 1024;
                serverPollset = allocatePoller(size, pool, timeout);
            }
            if (serverPollset == 0) {
                size = 62;
                serverPollset = allocatePoller(size, pool, timeout);
            }
            desc = new long[size * 2];
            keepAliveCount = 0;
            addS = new long[size];
            addCount = 0;
        }

        /**
         * Destroy the poller.
         */
        protected void destroy() {
            // Wait for polltime before doing anything, so that the poller threads
            // exit, otherwise parallel destruction of sockets which are still
            // in the poller can cause problems
            try {
                synchronized (this) {
                    this.wait(pollTime / 1000);
                }
            } catch (InterruptedException e) {
                // Ignore
            }
            // Close all sockets in the add queue
            for (int i = 0; i < addCount; i++) {
                if (comet) {
                    processSocket(addS[i], SocketStatus.STOP);
                } else {
                    Socket.destroy(addS[i]);
                }
            }
            // Close all sockets still in the poller
            int rv = Poll.pollset(serverPollset, desc);
            if (rv > 0) {
                for (int n = 0; n < rv; n++) {
                    if (comet) {
                        processSocket(desc[n*2+1], SocketStatus.STOP);
                    } else {
                        Socket.destroy(desc[n*2+1]);
                    }
                }
            }
            Pool.destroy(pool);
            keepAliveCount = 0;
            addCount = 0;
        }

        /**
         * Add specified socket and associated pool to the poller. The socket will
         * be added to a temporary array, and polled first after a maximum amount
         * of time equal to pollTime (in most cases, latency will be much lower,
         * however).
         *
         * @param socket to add to the poller
         */
        public void add(long socket) {
            synchronized (this) {
                // Add socket to the list. Newly added sockets will wait
                // at most for pollTime before being polled
                if (addCount >= addS.length) {
                    // Can't do anything: close the socket right away
                    if (comet) {
                        processSocket(socket, SocketStatus.ERROR);
                    } else {
                        Socket.destroy(socket);
                    }
                    return;
                }
                addS[addCount] = socket;
                addCount++;
                this.notify();
            }
        }

        /**
         * The background thread that listens for incoming TCP/IP connections and
         * hands them off to an appropriate processor.
         */
        public void run() {

            long maintainTime = 0;
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

                if (keepAliveCount < 1 && addCount < 1) {
                    synchronized (this) {
                        while (keepAliveCount < 1 && addCount < 1) {
                            // Reset maintain time.
                            maintainTime = 0;
                            try {
                                this.wait();
                            } catch (InterruptedException e) {
                                // Ignore
                            }
                        }
                    }
                }

                try {
                    // Add sockets which are waiting to the poller
                    if (addCount > 0) {
                        synchronized (this) {
                            int successCount = 0;
                            try {
                                for (int i = (addCount - 1); i >= 0; i--) {
                                    int rv = Poll.add
                                        (serverPollset, addS[i], Poll.APR_POLLIN);
                                    if (rv == Status.APR_SUCCESS) {
                                        successCount++;
                                    } else {
                                        // Can't do anything: close the socket right away
                                        if (comet) {
                                            processSocket(addS[i], SocketStatus.ERROR);
                                        } else {
                                            Socket.destroy(addS[i]);
                                        }
                                    }
                                }
                            } finally {
                                keepAliveCount += successCount;
                                addCount = 0;
                            }
                        }
                    }

                    maintainTime += pollTime;
                    // Pool for the specified interval
                    int rv = Poll.poll(serverPollset, pollTime, desc, true);
                    if (rv > 0) {
                        keepAliveCount -= rv;
                        for (int n = 0; n < rv; n++) {
                            // Check for failed sockets and hand this socket off to a worker
                            if (((desc[n*2] & Poll.APR_POLLHUP) == Poll.APR_POLLHUP)
                                    || ((desc[n*2] & Poll.APR_POLLERR) == Poll.APR_POLLERR)
                                    || (comet && (!processSocket(desc[n*2+1], SocketStatus.OPEN))) 
                                    || (!comet && (!processSocket(desc[n*2+1])))) {
                                // Close socket and clear pool
                                if (comet) {
                                    processSocket(desc[n*2+1], SocketStatus.DISCONNECT);
                                } else {
                                    Socket.destroy(desc[n*2+1]);
                                }
                                continue;
                            }
                        }
                    } else if (rv < 0) {
                        int errn = -rv;
                        /* Any non timeup or interrupted error is critical */
                        if ((errn != Status.TIMEUP) && (errn != Status.EINTR)) {
                            if (errn >  Status.APR_OS_START_USERERR) {
                                errn -=  Status.APR_OS_START_USERERR;
                            }
                            log.error(sm.getString("endpoint.poll.fail", "" + errn, Error.strerror(errn)));
                            // Handle poll critical failure
                            synchronized (this) {
                                destroy();
                                init();
                            }
                            continue;
                        }
                    }
                    if (socketProperties.getSoTimeout() > 0 && maintainTime > 1000000L && running) {
                        rv = Poll.maintain(serverPollset, desc, true);
                        maintainTime = 0;
                        if (rv > 0) {
                            keepAliveCount -= rv;
                            for (int n = 0; n < rv; n++) {
                                // Close socket and clear pool
                                if (comet) {
                                    processSocket(desc[n], SocketStatus.TIMEOUT);
                                } else {
                                    Socket.destroy(desc[n]);
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    log.error(sm.getString("endpoint.poll.error"), t);
                }

            }

            synchronized (this) {
                this.notifyAll();
            }

        }
        
    }


    // ----------------------------------------------------- Worker Inner Class




    // ----------------------------------------------- SendfileData Inner Class


    /**
     * SendfileData class.
     */
    public static class SendfileData {
        // File
        public String fileName;
        public long fd;
        public long fdpool;
        // Range information
        public long start;
        public long end;
        // Socket and socket pool
        public long socket;
        // Position
        public long pos;
        // KeepAlive flag
        public boolean keepAlive;
    }


    // --------------------------------------------------- Sendfile Inner Class


    /**
     * Sendfile class.
     */
    public class Sendfile implements Runnable {

        protected long sendfilePollset = 0;
        protected long pool = 0;
        protected long[] desc;
        protected HashMap<Long, SendfileData> sendfileData;
        
        protected volatile int sendfileCount;
        public int getSendfileCount() { return sendfileCount; }

        protected ArrayList<SendfileData> addS;
        protected volatile int addCount;

        /**
         * Create the sendfile poller. With some versions of APR, the maximum poller size will
         * be 62 (recompiling APR is necessary to remove this limitation).
         */
        protected void init() {
            pool = Pool.create(serverSockPool);
            int size = sendfileSize / sendfileThreadCount;
            sendfilePollset = allocatePoller(size, pool, socketProperties.getSoTimeout());
            if (sendfilePollset == 0 && size > 1024) {
                size = 1024;
                sendfilePollset = allocatePoller(size, pool, socketProperties.getSoTimeout());
            }
            if (sendfilePollset == 0) {
                size = 62;
                sendfilePollset = allocatePoller(size, pool, socketProperties.getSoTimeout());
            }
            desc = new long[size * 2];
            sendfileData = new HashMap<Long, SendfileData>(size);
            addS = new ArrayList<SendfileData>();
            addCount = 0;
        }

        /**
         * Destroy the poller.
         */
        protected void destroy() {
            // Wait for polltime before doing anything, so that the poller threads
            // exit, otherwise parallel destruction of sockets which are still
            // in the poller can cause problems
            try {
                synchronized (this) {
                    this.wait(pollTime / 1000);
                }
            } catch (InterruptedException e) {
                // Ignore
            }
            // Close any socket remaining in the add queue
            addCount = 0;
            for (int i = (addS.size() - 1); i >= 0; i--) {
                SendfileData data = addS.get(i);
                Socket.destroy(data.socket);
            }
            // Close all sockets still in the poller
            int rv = Poll.pollset(sendfilePollset, desc);
            if (rv > 0) {
                for (int n = 0; n < rv; n++) {
                    Socket.destroy(desc[n*2+1]);
                }
            }
            Pool.destroy(pool);
            sendfileData.clear();
        }

        /**
         * Add the sendfile data to the sendfile poller. Note that in most cases,
         * the initial non blocking calls to sendfile will return right away, and
         * will be handled asynchronously inside the kernel. As a result,
         * the poller will never be used.
         *
         * @param data containing the reference to the data which should be sent
         * @return true if all the data has been sent right away, and false
         *              otherwise
         */
        public boolean add(SendfileData data) {
            // Initialize fd from data given
            try {
                data.fdpool = Socket.pool(data.socket);
                data.fd = File.open
                    (data.fileName, File.APR_FOPEN_READ
                     | File.APR_FOPEN_SENDFILE_ENABLED | File.APR_FOPEN_BINARY,
                     0, data.fdpool);
                data.pos = data.start;
                // Set the socket to nonblocking mode
                Socket.timeoutSet(data.socket, 0);
                while (true) {
                    long nw = Socket.sendfilen(data.socket, data.fd,
                                               data.pos, data.end - data.pos, 0);
                    if (nw < 0) {
                        if (!(-nw == Status.EAGAIN)) {
                            Socket.destroy(data.socket);
                            data.socket = 0;
                            return false;
                        } else {
                            // Break the loop and add the socket to poller.
                            break;
                        }
                    } else {
                        data.pos = data.pos + nw;
                        if (data.pos >= data.end) {
                            // Entire file has been sent
                            Pool.destroy(data.fdpool);
                            // Set back socket to blocking mode
                            Socket.timeoutSet(data.socket, socketProperties.getSoTimeout() * 1000);
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                log.error(sm.getString("endpoint.sendfile.error"), e);
                return false;
            }
            // Add socket to the list. Newly added sockets will wait
            // at most for pollTime before being polled
            synchronized (this) {
                addS.add(data);
                addCount++;
                this.notify();
            }
            return false;
        }

        /**
         * Remove socket from the poller.
         *
         * @param data the sendfile data which should be removed
         */
        protected void remove(SendfileData data) {
            int rv = Poll.remove(sendfilePollset, data.socket);
            if (rv == Status.APR_SUCCESS) {
                sendfileCount--;
            }
            sendfileData.remove(new Long(data.socket));
        }

        /**
         * The background thread that listens for incoming TCP/IP connections and
         * hands them off to an appropriate processor.
         */
        public void run() {

            long maintainTime = 0;
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

                if (sendfileCount < 1 && addCount < 1) {
                    synchronized (this) {
                        while (sendfileCount < 1 && addS.size() < 1) {
                            // Reset maintain time.
                            maintainTime = 0;
                            try {
                                this.wait();
                            } catch (InterruptedException e) {
                                // Ignore
                            }
                        }
                    }
                }

                try {
                    // Add socket to the poller
                    if (addCount > 0) {
                        synchronized (this) {
                            int successCount = 0;
                            try {
                                for (int i = (addS.size() - 1); i >= 0; i--) {
                                    SendfileData data = addS.get(i);
                                    int rv = Poll.add(sendfilePollset, data.socket, Poll.APR_POLLOUT);
                                    if (rv == Status.APR_SUCCESS) {
                                        sendfileData.put(new Long(data.socket), data);
                                        successCount++;
                                    } else {
                                        log.warn(sm.getString("endpoint.sendfile.addfail", "" + rv, Error.strerror(rv)));
                                        // Can't do anything: close the socket right away
                                        Socket.destroy(data.socket);
                                    }
                                }
                            } finally {
                                sendfileCount += successCount;
                                addS.clear();
                                addCount = 0;
                            }
                        }
                    }

                    maintainTime += pollTime;
                    // Pool for the specified interval
                    int rv = Poll.poll(sendfilePollset, pollTime, desc, false);
                    if (rv > 0) {
                        for (int n = 0; n < rv; n++) {
                            // Get the sendfile state
                            SendfileData state =
                                sendfileData.get(new Long(desc[n*2+1]));
                            // Problem events
                            if (((desc[n*2] & Poll.APR_POLLHUP) == Poll.APR_POLLHUP)
                                    || ((desc[n*2] & Poll.APR_POLLERR) == Poll.APR_POLLERR)) {
                                // Close socket and clear pool
                                remove(state);
                                // Destroy file descriptor pool, which should close the file
                                // Close the socket, as the response would be incomplete
                                Socket.destroy(state.socket);
                                continue;
                            }
                            // Write some data using sendfile
                            long nw = Socket.sendfilen(state.socket, state.fd,
                                                       state.pos,
                                                       state.end - state.pos, 0);
                            if (nw < 0) {
                                // Close socket and clear pool
                                remove(state);
                                // Close the socket, as the response would be incomplete
                                // This will close the file too.
                                Socket.destroy(state.socket);
                                continue;
                            }

                            state.pos = state.pos + nw;
                            if (state.pos >= state.end) {
                                remove(state);
                                if (state.keepAlive) {
                                    // Destroy file descriptor pool, which should close the file
                                    Pool.destroy(state.fdpool);
                                    Socket.timeoutSet(state.socket, socketProperties.getSoTimeout() * 1000);
                                    // If all done put the socket back in the poller for
                                    // processing of further requests
                                    getPoller().add(state.socket);
                                } else {
                                    // Close the socket since this is
                                    // the end of not keep-alive request.
                                    Socket.destroy(state.socket);
                                }
                            }
                        }
                    } else if (rv < 0) {
                        int errn = -rv;
                        /* Any non timeup or interrupted error is critical */
                        if ((errn != Status.TIMEUP) && (errn != Status.EINTR)) {
                            if (errn >  Status.APR_OS_START_USERERR) {
                                errn -=  Status.APR_OS_START_USERERR;
                            }
                            log.error(sm.getString("endpoint.poll.fail", "" + errn, Error.strerror(errn)));
                            // Handle poll critical failure
                            synchronized (this) {
                                destroy();
                                init();
                            }
                            continue;
                        }
                    }
                    // Call maintain for the sendfile poller
                    if (socketProperties.getSoTimeout() > 0 && maintainTime > 1000000L && running) {
                        rv = Poll.maintain(sendfilePollset, desc, true);
                        maintainTime = 0;
                        if (rv > 0) {
                            for (int n = 0; n < rv; n++) {
                                // Get the sendfile state
                                SendfileData state = sendfileData.get(new Long(desc[n]));
                                // Close socket and clear pool
                                remove(state);
                                // Destroy file descriptor pool, which should close the file
                                // Close the socket, as the response would be incomplete
                                Socket.destroy(state.socket);
                            }
                        }
                    }
                } catch (Throwable t) {
                    log.error(sm.getString("endpoint.poll.error"), t);
                }
            }

            synchronized (this) {
                this.notifyAll();
            }

        }

    }


    // ------------------------------------------------ Handler Inner Interface


    /**
     * Bare bones interface used for socket processing. Per thread data is to be
     * stored in the ThreadWithAttributes extra folders, or alternately in
     * thread local fields.
     */
    public interface Handler extends AbstractEndpoint.Handler {
        public SocketState process(long socket);
        public SocketState event(long socket, SocketStatus status);
        public SocketState asyncDispatch(long socket, SocketStatus status);
    }



    // ---------------------------------------------- SocketProcessor Inner Class


    /**
     * This class is the equivalent of the Worker, but will simply use in an
     * external Executor thread pool. This will also set the socket options
     * and do the handshake.
     */
    protected class SocketWithOptionsProcessor implements Runnable {
        
        protected long socket = 0;
        
        public SocketWithOptionsProcessor(long socket) {
            this.socket = socket;
        }

        public void run() {

            if (!deferAccept) {
                if (setSocketOptions(socket)) {
                    getPoller().add(socket);
                } else {
                    // Close socket and pool
                    Socket.destroy(socket);
                    socket = 0;
                }
            } else {
                // Process the request from this socket
                if (!setSocketOptions(socket) 
                        || handler.process(socket) == Handler.SocketState.CLOSED) {
                    // Close socket and pool
                    Socket.destroy(socket);
                    socket = 0;
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
        
        protected long socket = 0;
        protected boolean async = false;
        protected SocketStatus status = SocketStatus.ERROR;
        
        public SocketProcessor(long socket) {
            this.socket = socket;
            this.async = false;
        }
        public SocketProcessor(long socket, boolean asyn, SocketStatus status) {
            this.socket = socket;
            this.async = asyn;
            this.status = status;
        }

        public void run() {

            // Process the request from this socket
            Handler.SocketState state = async?handler.asyncDispatch(socket, status):handler.process(socket);
            if (state == Handler.SocketState.CLOSED) {
                // Close socket and pool
                Socket.destroy(socket);
                socket = 0;
            }

        }
        
    }
    
    
    // --------------------------------------- SocketEventProcessor Inner Class


    /**
     * This class is the equivalent of the Worker, but will simply use in an
     * external Executor thread pool.
     */
    protected class SocketEventProcessor implements Runnable {
        
        protected long socket = 0;
        protected SocketStatus status = null; 
        
        public SocketEventProcessor(long socket, SocketStatus status) {
            this.socket = socket;
            this.status = status;
        }

        public void run() {

            // Process the request from this socket
            if (handler.event(socket, status) == Handler.SocketState.CLOSED) {
                // Close socket and pool
                Socket.destroy(socket);
                socket = 0;
            }

        }
        
    }
    
    private static class PrivilegedSetTccl implements PrivilegedAction<Void> {

        private ClassLoader cl;

        PrivilegedSetTccl(ClassLoader cl) {
            this.cl = cl;
        }

        public Void run() {
            Thread.currentThread().setContextClassLoader(cl);
            return null;
        }
    }    
}
