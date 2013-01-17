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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

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
import org.apache.tomcat.jni.Sockaddr;
import org.apache.tomcat.jni.Socket;
import org.apache.tomcat.jni.Status;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.net.AbstractEndpoint.Acceptor.AcceptorState;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;


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


    protected ConcurrentLinkedQueue<SocketWrapper<Long>> waitingRequests =
        new ConcurrentLinkedQueue<SocketWrapper<Long>>();

    // ------------------------------------------------------------ Constructor

    public AprEndpoint() {
        // Need to override the default for maxConnections to align it with what
        // was pollerSize (before the two were merged)
        setMaxConnections(8 * 1024);
    }

    // ------------------------------------------------------------- Properties


    /**
     * Defer accept.
     */
    protected boolean deferAccept = true;
    public void setDeferAccept(boolean deferAccept) { this.deferAccept = deferAccept; }
    @Override
    public boolean getDeferAccept() { return deferAccept; }


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
    @Override
    public boolean getUseSendfile() { return useSendfile; }


    /**
     * Allow comet request handling.
     */
    protected boolean useComet = true;
    public void setUseComet(boolean useComet) { this.useComet = useComet; }
    @Override
    public boolean getUseComet() { return useComet; }
    @Override
    public boolean getUseCometTimeout() { return false; } // Not supported
    @Override
    public boolean getUsePolling() { return true; } // Always supported


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

    protected boolean SSLHonorCipherOrder = false;
    /**
     * Set to <code>true</code> to enforce the <i>server's</i> cipher order
     * instead of the default which is to allow the client to choose a
     * preferred cipher.
     */
    public void setSSLHonorCipherOrder(boolean SSLHonorCipherOrder) { this.SSLHonorCipherOrder = SSLHonorCipherOrder; }
    public boolean getSSLHonorCipherOrder() { return SSLHonorCipherOrder; }

    /**
     * Disables compression of the SSL stream. This thwarts CRIME attack
     * and possibly improves performance by not compressing uncompressible
     * content such as JPEG, etc.
     */
    protected boolean SSLDisableCompression = false;

    /**
     * Set to <code>true</code> to disable SSL compression. This thwarts CRIME
     * attack.
     */
    public void setSSLDisableCompression(boolean SSLDisableCompression) { this.SSLDisableCompression = SSLDisableCompression; }
    public boolean getSSLDisableCompression() { return SSLDisableCompression; }

    /**
     * Port in use.
     */
    @Override
    public int getLocalPort() {
        long s = serverSock;
        if (s == 0) {
            return -1;
        } else {
            long sa;
            try {
                sa = Address.get(Socket.APR_LOCAL, s);
                Sockaddr addr = Address.getInfo(sa);
                return addr.port;
            } catch (Exception e) {
                return -1;
            }
        }
    }


    // --------------------------------------------------------- Public Methods

    /**
     * Number of keepalive sockets.
     */
    public int getKeepAliveCount() {
        if (pollers == null) {
            return 0;
        }

        int keepAliveCount = 0;
        for (int i = 0; i < pollers.length; i++) {
            keepAliveCount += pollers[i].getKeepAliveCount();
        }
        return keepAliveCount;
    }


    /**
     * Number of sendfile sockets.
     */
    public int getSendfileCount() {
        if (sendfiles == null) {
            return 0;
        }

        int sendfileCount = 0;
        for (int i = 0; i < sendfiles.length; i++) {
            sendfileCount += sendfiles[i].getSendfileCount();
        }
        return sendfileCount;
    }


    // ----------------------------------------------- Public Lifecycle Methods


    /**
     * Initialize the endpoint.
     */
    @Override
    public void bind() throws Exception {

        // Create the root APR memory pool
        try {
            rootPool = Pool.create(0);
        } catch (UnsatisfiedLinkError e) {
            throw new Exception(sm.getString("endpoint.init.notavail"));
        }

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
            if ((OS.IS_WIN32 || OS.IS_WIN64) && (getMaxConnections() > 1024)) {
                // The maximum per poller to get reasonable performance is 1024
                pollerThreadCount = getMaxConnections() / 1024;
                // Adjust poller size so that it won't reach the limit
                setMaxConnections(
                        getMaxConnections() - (getMaxConnections() % 1024));
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

            if (SSLCertificateFile == null) {
                // This is required
                throw new Exception(sm.getString("endpoint.apr.noSslCertFile"));
            }

            // SSL protocol
            int value = SSL.SSL_PROTOCOL_NONE;
            if (SSLProtocol == null || SSLProtocol.length() == 0) {
                value = SSL.SSL_PROTOCOL_ALL;
            } else {
                for (String protocol : SSLProtocol.split("\\+")) {
                    protocol = protocol.trim();
                    if ("SSLv2".equalsIgnoreCase(protocol)) {
                        value |= SSL.SSL_PROTOCOL_SSLV2;
                    } else if ("SSLv3".equalsIgnoreCase(protocol)) {
                        value |= SSL.SSL_PROTOCOL_SSLV3;
                    } else if ("TLSv1".equalsIgnoreCase(protocol)) {
                        value |= SSL.SSL_PROTOCOL_TLSV1;
                    } else if ("all".equalsIgnoreCase(protocol)) {
                        value |= SSL.SSL_PROTOCOL_ALL;
                    } else {
                        // Protocol not recognized, fail to start as it is safer than
                        // continuing with the default which might enable more than the
                        // is required
                        throw new Exception(sm.getString(
                                "endpoint.apr.invalidSslProtocol", SSLProtocol));
                    }
                }
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

            // Set cipher order: client (default) or server
            if (SSLHonorCipherOrder) {
                boolean orderCiphersSupported = false;
                try {
                    orderCiphersSupported = SSL.hasOp(SSL.SSL_OP_CIPHER_SERVER_PREFERENCE);
                    if (orderCiphersSupported)
                        SSLContext.setOptions(sslContext, SSL.SSL_OP_CIPHER_SERVER_PREFERENCE);
                } catch (UnsatisfiedLinkError e) {
                    // Ignore
                }
                if (!orderCiphersSupported) {
                    // OpenSSL does not support ciphers ordering.
                    log.warn(sm.getString("endpoint.warn.noHonorCipherOrder",
                                          SSL.versionString()));
                }
            }

            // Disable compression if requested
            if (SSLDisableCompression) {
                boolean disableCompressionSupported = false;
                try {
                    disableCompressionSupported = SSL.hasOp(SSL.SSL_OP_NO_COMPRESSION);
                    if (disableCompressionSupported)
                        SSLContext.setOptions(sslContext, SSL.SSL_OP_NO_COMPRESSION);
                } catch (UnsatisfiedLinkError e) {
                    // Ignore
                }
                if (!disableCompressionSupported) {
                    // OpenSSL does not support ciphers ordering.
                    log.warn(sm.getString("endpoint.warn.noDisableCompression",
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
    }


    /**
     * Start the APR endpoint, creating acceptor, poller and sendfile threads.
     */
    @Override
    public void startInternal() throws Exception {

        if (!running) {
            running = true;
            paused = false;

            // Create worker collection
            if (getExecutor() == null) {
                createExecutor();
            }

            initializeConnectionLatch();

            // Start poller threads
            pollers = new Poller[pollerThreadCount];
            for (int i = 0; i < pollerThreadCount; i++) {
                pollers[i] = new Poller(false);
                pollers[i].init();
                pollers[i].setName(getName() + "-Poller-" + i);
                pollers[i].setPriority(threadPriority);
                pollers[i].setDaemon(true);
                pollers[i].start();
            }

            // Start comet poller threads
            cometPollers = new Poller[pollerThreadCount];
            for (int i = 0; i < pollerThreadCount; i++) {
                cometPollers[i] = new Poller(true);
                cometPollers[i].init();
                cometPollers[i].setName(getName() + "-CometPoller-" + i);
                cometPollers[i].setPriority(threadPriority);
                cometPollers[i].setDaemon(true);
                cometPollers[i].start();
            }

            // Start sendfile threads
            if (useSendfile) {
                sendfiles = new Sendfile[sendfileThreadCount];
                for (int i = 0; i < sendfileThreadCount; i++) {
                    sendfiles[i] = new Sendfile();
                    sendfiles[i].init();
                    sendfiles[i].setName(getName() + "-Sendfile-" + i);
                    sendfiles[i].setPriority(threadPriority);
                    sendfiles[i].setDaemon(true);
                    sendfiles[i].start();
                }
            }

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
            for (AbstractEndpoint.Acceptor acceptor : acceptors) {
                long waitLeft = 10000;
                while (waitLeft > 0 &&
                        acceptor.getState() != AcceptorState.ENDED &&
                        serverSock != 0) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        // Ignore and clean the interrupt flag
                        Thread.interrupted();
                    }
                    waitLeft -= 50;
                }
                if (waitLeft == 0) {
                    log.warn(sm.getString("endpoint.warn.unlockAcceptorFailed",
                            acceptor.getThreadName()));
                   // If the Acceptor is still running force
                   // the hard socket close.
                   if (serverSock != 0) {
                       Socket.shutdown(serverSock, Socket.APR_SHUTDOWN_READ);
                       serverSock = 0;
                   }
                }
            }
            for (int i = 0; i < pollers.length; i++) {
                try {
                    pollers[i].destroy();
                } catch (Exception e) {
                    // Ignore
                }
            }
            pollers = null;
            for (int i = 0; i < cometPollers.length; i++) {
                try {
                    cometPollers[i].destroy();
                } catch (Exception e) {
                    // Ignore
                }
            }
            cometPollers = null;
            if (useSendfile) {
                for (int i = 0; i < sendfiles.length; i++) {
                    try {
                        sendfiles[i].destroy();
                    } catch (Exception e) {
                        // Ignore
                    }
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
    public void unbind() throws Exception {
        if (running) {
            stop();
        }

        // Destroy pool if it was initialised
        if (serverSockPool != 0) {
            Pool.destroy(serverSockPool);
            serverSockPool = 0;
        }

        // Close server socket if it was initialised
        if (serverSock != 0) {
            Socket.close(serverSock);
            serverSock = 0;
        }

        sslContext = 0;

        // Close all APR memory pools and resources if initialised
        if (rootPool != 0) {
            Pool.destroy(rootPool);
            rootPool = 0;
        }

        handler.recycle();
    }


    // ------------------------------------------------------ Protected Methods

    @Override
    protected AbstractEndpoint.Acceptor createAcceptor() {
        return new Acceptor();
    }


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
            ExceptionUtils.handleThrowable(t);
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
            // During shutdown, executor may be null - avoid NPE
            if (running) {
                SocketWrapper<Long> wrapper =
                    new SocketWrapper<Long>(Long.valueOf(socket));
                getExecutor().execute(new SocketWithOptionsProcessor(wrapper));
            }
        } catch (RejectedExecutionException x) {
            log.warn("Socket processing request was rejected for:"+socket,x);
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


    /**
     * Process given socket.
     */
    protected boolean processSocket(long socket) {
        try {
            Executor executor = getExecutor();
            if (executor == null) {
                log.warn(sm.getString("endpoint.warn.noExector",
                        Long.valueOf(socket), null));
            } else {
                SocketWrapper<Long> wrapper =
                    new SocketWrapper<Long>(Long.valueOf(socket));
                executor.execute(new SocketProcessor(wrapper, null));
            }
        } catch (RejectedExecutionException x) {
            log.warn("Socket processing request was rejected for:"+socket,x);
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


    /**
     * Process given socket for an event.
     */
    public boolean processSocket(long socket, SocketStatus status) {
        try {
            Executor executor = getExecutor();
            if (executor == null) {
                log.warn(sm.getString("endpoint.warn.noExector",
                        Long.valueOf(socket), status));
            } else {
                SocketWrapper<Long> wrapper =
                        new SocketWrapper<Long>(Long.valueOf(socket));
                executor.execute(new SocketEventProcessor(wrapper, status));
            }
        } catch (RejectedExecutionException x) {
            log.warn("Socket processing request was rejected for:"+socket,x);
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

    public boolean processSocketAsync(SocketWrapper<Long> socket,
            SocketStatus status) {
        try {
            synchronized (socket) {
                if (waitingRequests.remove(socket)) {
                    SocketProcessor proc = new SocketProcessor(socket, status);
                    ClassLoader loader = Thread.currentThread().getContextClassLoader();
                    try {
                        //threads should not be created by the webapp classloader
                        if (Constants.IS_SECURITY_ENABLED) {
                            PrivilegedAction<Void> pa = new PrivilegedSetTccl(
                                    getClass().getClassLoader());
                            AccessController.doPrivileged(pa);
                        } else {
                            Thread.currentThread().setContextClassLoader(
                                    getClass().getClassLoader());
                        }
                        Executor executor = getExecutor();
                        if (executor == null) {
                            log.warn(sm.getString("endpoint.warn.noExector",
                                    socket, status));
                            return false;
                        } else {
                            executor.execute(proc);
                        }
                    } finally {
                        if (Constants.IS_SECURITY_ENABLED) {
                            PrivilegedAction<Void> pa = new PrivilegedSetTccl(loader);
                            AccessController.doPrivileged(pa);
                        } else {
                            Thread.currentThread().setContextClassLoader(loader);
                        }
                    }
                }
            }
        } catch (RejectedExecutionException x) {
            log.warn("Socket processing request was rejected for: "+socket, x);
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

    private void destroySocket(long socket) {
        // If not running the socket will be destroyed by
        // parent pool or acceptor socket.
        // In any case disable double free which would cause JVM core.

        // While the connector is running, destroySocket() will call
        // countDownConnection(). Once the connector is stopped, the latch is
        // removed so it does not matter that destroySocket() does not call
        // countDownConnection() in that case
        destroySocket(socket, running);
    }

    private void destroySocket(long socket, boolean doIt) {
        // Be VERY careful if you call this method directly. If it is called
        // twice for the same socket the JVM will core. Currently this is only
        // called from Poller.closePollset() to ensure kept alive connections
        // are closed when calling stop() followed by start().
        if (doIt && socket != 0) {
            Socket.destroy(socket);
            countDownConnection();
        }
    }

    @Override
    protected Log getLog() {
        return log;
    }


    // --------------------------------------------------- Acceptor Inner Class
    /**
     * The background thread that listens for incoming TCP/IP connections and
     * hands them off to an appropriate processor.
     */
    protected class Acceptor extends AbstractEndpoint.Acceptor {

        private final Log log = LogFactory.getLog(AprEndpoint.Acceptor.class);

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

                    long socket = 0;
                    try {
                        // Accept the next incoming connection from the server
                        // socket
                        socket = Socket.accept(serverSock);
                    } catch (Exception e) {
                        //we didn't get a socket
                        countDownConnection();
                        // Introduce delay if necessary
                        errorDelay = handleExceptionWithDelay(errorDelay);
                        // re-throw
                        throw e;
                    }
                    // Successful accept, reset the error delay
                    errorDelay = 0;

                    if (running && !paused) {
                        // Hand this socket off to an appropriate processor
                        if (!processSocketWithOptions(socket)) {
                            // Close socket and pool right away
                            destroySocket(socket);
                        }
                    } else {
                        // Close socket and pool right away
                        destroySocket(socket);
                    }
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    if (running) {
                        String msg = sm.getString("endpoint.accept.fail");
                        if (t instanceof Error) {
                            Error e = (Error) t;
                            if (e.getError() == 233) {
                                // Not an error on HP-UX so log as a warning
                                // so it can be filtered out on that platform
                                // See bug 50273
                                log.warn(msg, t);
                            } else {
                                log.error(msg, t);
                            }
                        } else {
                                log.error(msg, t);
                        }
                    }
                }
                // The processor will recycle itself when it finishes
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
                Iterator<SocketWrapper<Long>> sockets =
                    waitingRequests.iterator();
                while (sockets.hasNext()) {
                    SocketWrapper<Long> socket = sockets.next();
                    if (socket.async) {
                        long access = socket.getLastAccess();
                        if (socket.getTimeout() > 0 &&
                                (now-access)>socket.getTimeout()) {
                            processSocketAsync(socket,SocketStatus.TIMEOUT);
                        }
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


    // ----------------------------------------------------- Poller Inner Class
    /**
     * Poller class.
     */
    public class Poller extends Thread {

        public static final int FLAGS_READ = Poll.APR_POLLIN;
        public static final int FLAGS_WRITE  = Poll.APR_POLLOUT;

        // Need two pollsets since the socketTimeout and the keep-alive timeout
        // can have different values.
        private long connectionPollset = 0;
        private long pool = 0;
        private long[] desc;

        private long[] addSocket;
        private int[] addSocketTimeout;
        private int[] addSocketFlags;

        private volatile int addCount = 0;

        private boolean comet = true;

        protected volatile int keepAliveCount = 0;
        public int getKeepAliveCount() { return keepAliveCount; }

        public Poller(boolean comet) {
            this.comet = comet;
        }

        /**
         * Create the poller. With some versions of APR, the maximum poller size
         * will be 62 (recompiling APR is necessary to remove this limitation).
         */
        protected void init() {
            pool = Pool.create(serverSockPool);
            int size = getMaxConnections() / pollerThreadCount;
            int socketTimeout = socketProperties.getSoTimeout();
            connectionPollset = allocatePoller(size, pool, socketTimeout);
            if (connectionPollset == 0 && size > 1024) {
                size = 1024;
                connectionPollset = allocatePoller(size, pool, socketTimeout);
            }
            if (connectionPollset == 0) {
                size = 62;
                connectionPollset = allocatePoller(size, pool, socketTimeout);
            }
            desc = new long[size * 2];
            keepAliveCount = 0;
            addSocket = new long[size];
            addSocketTimeout = new int[size];
            addSocketFlags = new int[size];
            addCount = 0;
        }

        /**
         * Destroy the poller.
         */
        @Override
        public void destroy() {
            // Close all sockets in the add queue
            for (int i = 0; i < addCount; i++) {
                if (comet) {
                    processSocket(addSocket[i], SocketStatus.STOP);
                } else {
                    destroySocket(addSocket[i]);
                }
            }
            // Close all sockets still in the poller
            closePollset(connectionPollset);
            Pool.destroy(pool);
            keepAliveCount = 0;
            addCount = 0;
            try {
                while (this.isAlive()) {
                    this.interrupt();
                    this.join(1000);
                }
            } catch (InterruptedException e) {
                // Ignore
            }
        }

        private void closePollset(long pollset) {
            int rv = Poll.pollset(pollset, desc);
            if (rv > 0) {
                for (int n = 0; n < rv; n++) {
                    if (comet) {
                        processSocket(desc[n*2+1], SocketStatus.STOP);
                    } else {
                        destroySocket(desc[n*2+1], true);
                    }
                }
            }
        }

        /**
         * Add specified socket and associated pool to the poller. The socket
         * will be added to a temporary array, and polled first after a maximum
         * amount of time equal to pollTime (in most cases, latency will be much
         * lower, however).
         *
         * @param socket    to add to the poller
         * @param timeout   read timeout (in milliseconds) to use with this
         *                  socket. Use -1 for infinite timeout
         * @param flags     flags that define the events that are to be polled
         *                  for
         */
        public void add(long socket, int timeout, int flags) {
            synchronized (this) {
                // Add socket to the list. Newly added sockets will wait
                // at most for pollTime before being polled
                if (addCount >= addSocket.length) {
                    // Can't do anything: close the socket right away
                    if (comet) {
                        processSocket(socket, SocketStatus.ERROR);
                    } else {
                        destroySocket(socket);
                    }
                    return;
                }
                addSocket[addCount] = socket;
                addSocketTimeout[addCount] = timeout;
                addSocketFlags[addCount] = flags;
                addCount++;
                this.notify();
            }
        }

        /**
         * The background thread that listens for incoming TCP/IP connections and
         * hands them off to an appropriate processor.
         */
        @Override
        public void run() {

            long maintainTime = 0;
            // Loop until we receive a shutdown command
            while (running) {
                // Loop if endpoint is paused
                while (paused && running) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }

                if (!running) {
                    break;
                }
                if (keepAliveCount < 1 && addCount < 1) {
                    synchronized (this) {
                        while (keepAliveCount < 1 && addCount < 1 && running) {
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

                if (!running) {
                    break;
                }
                try {
                    // Add sockets which are waiting to the poller
                    if (addCount > 0) {
                        synchronized (this) {
                            int successCount = 0;
                            try {
                                for (int i = (addCount - 1); i >= 0; i--) {
                                    int timeout = addSocketTimeout[i];
                                    if (timeout > 0) {
                                        // Convert milliseconds to microseconds
                                        timeout = timeout * 1000;
                                    }
                                    int rv = Poll.addWithTimeout(
                                            connectionPollset, addSocket[i],
                                            addSocketFlags[i], timeout);
                                    if (rv == Status.APR_SUCCESS) {
                                        successCount++;
                                    } else {
                                        // Can't do anything: close the socket right away
                                        if (comet) {
                                            processSocket(addSocket[i], SocketStatus.ERROR);
                                        } else {
                                            destroySocket(addSocket[i]);
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
                    // Poll for the specified interval
                    if (doPoll(connectionPollset)) {
                        continue;
                    }

                    // Check timeouts (much less frequently that polling)
                    if (maintainTime > 1000000L && running) {
                        maintainTime = 0;
                        if (socketProperties.getSoTimeout() > 0) {
                            doTimeout(connectionPollset);
                        }
                    }
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    log.error(sm.getString("endpoint.poll.error"), t);
                }

            }

            synchronized (this) {
                this.notifyAll();
            }

        }

        private boolean doPoll(long pollset) {
            int rv = Poll.poll(pollset, pollTime, desc, true);
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
                            destroySocket(desc[n*2+1]);
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
                    return true;
                }
            }
            return false;
        }

        private void doTimeout(long pollset) {
            int rv = Poll.maintain(pollset, desc, true);
            if (rv > 0) {
                keepAliveCount -= rv;
                for (int n = 0; n < rv; n++) {
                    // Close socket and clear pool
                    if (comet) {
                        processSocket(desc[n], SocketStatus.TIMEOUT);
                    } else {
                        destroySocket(desc[n]);
                    }
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
    public class Sendfile extends Thread {

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
        @Override
        public void destroy() {
            // Close any socket remaining in the add queue
            addCount = 0;
            for (int i = (addS.size() - 1); i >= 0; i--) {
                SendfileData data = addS.get(i);
                destroySocket(data.socket);
            }
            addS.clear();
            // Close all sockets still in the poller
            int rv = Poll.pollset(sendfilePollset, desc);
            if (rv > 0) {
                for (int n = 0; n < rv; n++) {
                    destroySocket(desc[n*2+1]);
                }
            }
            Pool.destroy(pool);
            sendfileData.clear();
            try {
                while (this.isAlive()) {
                    this.interrupt();
                    this.join(1000);
                }
            } catch (InterruptedException e) {
                // Ignore
            }
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
            } catch (Exception e) {
                // Pool not created so no need to destroy it.
                log.error(sm.getString("endpoint.sendfile.error"), e);
                data.socket = 0;
                return false;
            }
            try {
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
                            Pool.destroy(data.fdpool);
                            // No need to close socket, this will be done by
                            // calling code since data.socket == 0
                            data.socket = 0;
                            return false;
                        } else {
                            // Break the loop and add the socket to poller.
                            break;
                        }
                    }

                    data.pos = data.pos + nw;
                    if (data.pos >= data.end) {
                        // Entire file has been sent
                        Pool.destroy(data.fdpool);
                        // Set back socket to blocking mode
                        Socket.timeoutSet(data.socket, socketProperties.getSoTimeout() * 1000);
                        return true;
                    }
                }
            } catch (Exception e) {
                log.error(sm.getString("endpoint.sendfile.error"), e);
                Pool.destroy(data.fdpool);
                data.socket = 0;
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
            sendfileData.remove(Long.valueOf(data.socket));
        }

        /**
         * The background thread that listens for incoming TCP/IP connections and
         * hands them off to an appropriate processor.
         */
        @Override
        public void run() {

            long maintainTime = 0;
            // Loop until we receive a shutdown command
            while (running) {

                // Loop if endpoint is paused
                while (paused && running) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }

                if (!running) {
                    break;
                }
                if (sendfileCount < 1 && addCount < 1) {
                    synchronized (this) {
                        while (sendfileCount < 1 && addS.size() < 1 && running) {
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

                if (!running) {
                    break;
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
                                        sendfileData.put(Long.valueOf(data.socket), data);
                                        successCount++;
                                    } else {
                                        log.warn(sm.getString("endpoint.sendfile.addfail", "" + rv, Error.strerror(rv)));
                                        // Can't do anything: close the socket right away
                                        destroySocket(data.socket);
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
                                sendfileData.get(Long.valueOf(desc[n*2+1]));
                            // Problem events
                            if (((desc[n*2] & Poll.APR_POLLHUP) == Poll.APR_POLLHUP)
                                    || ((desc[n*2] & Poll.APR_POLLERR) == Poll.APR_POLLERR)) {
                                // Close socket and clear pool
                                remove(state);
                                // Destroy file descriptor pool, which should close the file
                                // Close the socket, as the response would be incomplete
                                destroySocket(state.socket);
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
                                destroySocket(state.socket);
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
                                    getPoller().add(state.socket,
                                            getKeepAliveTimeout(),
                                            Poller.FLAGS_READ);
                                } else {
                                    // Close the socket since this is
                                    // the end of not keep-alive request.
                                    destroySocket(state.socket);
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
                                SendfileData state = sendfileData.get(Long.valueOf(desc[n]));
                                // Close socket and clear pool
                                remove(state);
                                // Destroy file descriptor pool, which should close the file
                                // Close the socket, as the response would be incomplete
                                destroySocket(state.socket);
                            }
                        }
                    }
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
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
        public SocketState process(SocketWrapper<Long> socket,
                SocketStatus status);
    }


    // --------------------------------- SocketWithOptionsProcessor Inner Class

    /**
     * This class is the equivalent of the Worker, but will simply use in an
     * external Executor thread pool. This will also set the socket options
     * and do the handshake.
     */
    protected class SocketWithOptionsProcessor implements Runnable {

        protected SocketWrapper<Long> socket = null;


        public SocketWithOptionsProcessor(SocketWrapper<Long> socket) {
            this.socket = socket;
        }

        @Override
        public void run() {

            synchronized (socket) {
                if (!deferAccept) {
                    if (setSocketOptions(socket.getSocket().longValue())) {
                        getPoller().add(socket.getSocket().longValue(),
                                getSoTimeout(), Poller.FLAGS_READ);
                    } else {
                        // Close socket and pool
                        destroySocket(socket.getSocket().longValue());
                        socket = null;
                    }
                } else {
                    // Process the request from this socket
                    if (!setSocketOptions(socket.getSocket().longValue())) {
                        // Close socket and pool
                        destroySocket(socket.getSocket().longValue());
                        socket = null;
                        return;
                    }
                    // Process the request from this socket
                    Handler.SocketState state = handler.process(socket,
                            SocketStatus.OPEN);
                    if (state == Handler.SocketState.CLOSED) {
                        // Close socket and pool
                        destroySocket(socket.getSocket().longValue());
                        socket = null;
                    } else if (state == Handler.SocketState.LONG) {
                        socket.access();
                        if (socket.async) {
                            waitingRequests.add(socket);
                        }
                    }
                }
            }
        }
    }


    // -------------------------------------------- SocketProcessor Inner Class


    /**
     * This class is the equivalent of the Worker, but will simply use in an
     * external Executor thread pool.
     */
    protected class SocketProcessor implements Runnable {

        protected SocketWrapper<Long> socket = null;
        protected SocketStatus status = null;

        public SocketProcessor(SocketWrapper<Long> socket,
                SocketStatus status) {
            this.socket = socket;
            this.status = status;
        }

        @Override
        public void run() {
            synchronized (socket) {
                // Process the request from this socket
                SocketState state = SocketState.OPEN;
                if (status == null) {
                    state = handler.process(socket,SocketStatus.OPEN);
                } else {
                    state = handler.process(socket, status);
                }
                if (state == Handler.SocketState.CLOSED) {
                    // Close socket and pool
                    destroySocket(socket.getSocket().longValue());
                    socket = null;
                } else if (state == Handler.SocketState.LONG) {
                    socket.access();
                    if (socket.async) {
                        waitingRequests.add(socket);
                    }
                } else if (state == Handler.SocketState.ASYNC_END) {
                    socket.access();
                    SocketProcessor proc = new SocketProcessor(socket, SocketStatus.OPEN);
                    getExecutor().execute(proc);
                }
            }
        }
    }


    // --------------------------------------- SocketEventProcessor Inner Class


    /**
     * This class is the equivalent of the Worker, but will simply use in an
     * external Executor thread pool.
     */
    protected class SocketEventProcessor implements Runnable {

        protected SocketWrapper<Long> socket = null;
        protected SocketStatus status = null;

        public SocketEventProcessor(SocketWrapper<Long> socket,
                SocketStatus status) {
            this.socket = socket;
            this.status = status;
        }

        @Override
        public void run() {
            synchronized (socket) {
                // Process the request from this socket
                Handler.SocketState state = handler.process(socket, status);
                if (state == Handler.SocketState.CLOSED) {
                    // Close socket and pool
                    destroySocket(socket.getSocket().longValue());
                    socket = null;
                }
            }
        }
    }

    private static class PrivilegedSetTccl implements PrivilegedAction<Void> {

        private ClassLoader cl;

        PrivilegedSetTccl(ClassLoader cl) {
            this.cl = cl;
        }

        @Override
        public Void run() {
            Thread.currentThread().setContextClassLoader(cl);
            return null;
        }
    }
}
