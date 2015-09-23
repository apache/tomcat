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
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

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
import org.apache.tomcat.jni.SSLContext.SNICallBack;
import org.apache.tomcat.jni.SSLSocket;
import org.apache.tomcat.jni.Sockaddr;
import org.apache.tomcat.jni.Socket;
import org.apache.tomcat.jni.Status;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.ByteBufferUtils;
import org.apache.tomcat.util.net.AbstractEndpoint.Acceptor.AcceptorState;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLHostConfig.Type;


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
public class AprEndpoint extends AbstractEndpoint<Long> implements SNICallBack {

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


    private final Map<Long,AprSocketWrapper> connections = new ConcurrentHashMap<>();

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
    protected Handler<Long> handler = null;
    public void setHandler(Handler<Long> handler ) { this.handler = handler; }
    @Override
    public Handler<Long> getHandler() { return handler; }


    /**
     * Poll interval, in microseconds. The smaller the value, the more CPU the poller
     * will use, but the more responsive to activity it will be.
     */
    protected int pollTime = 2000;
    public int getPollTime() { return pollTime; }
    public void setPollTime(int pollTime) { if (pollTime > 0) { this.pollTime = pollTime; } }


    /*
     * When the endpoint is created and configured, the APR library will not
     * have been initialised. This flag is used to determine if the default
     * value of useSendFile should be changed if the APR library indicates it
     * supports send file once it has been initialised. If useSendFile is set
     * by configuration, that configuration will always take priority.
     */
    private boolean useSendFileSet = false;
    @Override
    public void setUseSendfile(boolean useSendfile) {
        useSendFileSet = true;
        super.setUseSendfile(useSendfile);
    }
    /*
     * For internal use to avoid setting the useSendFileSet flag
     */
    private void setUseSendfileInternal(boolean useSendfile) {
        super.setUseSendfile(useSendfile);
    }


    /**
     * Sendfile thread count.
     */
    protected int sendfileThreadCount = 0;
    public void setSendfileThreadCount(int sendfileThreadCount) { this.sendfileThreadCount = sendfileThreadCount; }
    public int getSendfileThreadCount() { return sendfileThreadCount; }


    /**
     * The socket poller.
     */
    protected Poller poller = null;
    public Poller getPoller() {
        return poller;
    }


    /**
     * The static file sender.
     */
    protected Sendfile sendfile = null;
    public Sendfile getSendfile() {
        return sendfile;
    }


    @Override
    protected Type getSslConfigType() {
        return SSLHostConfig.Type.OPENSSL;
    }


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


    /**
     * This endpoint does not support <code>-1</code> for unlimited connections,
     * nor does it support setting this attribute while the endpoint is running.
     *
     * {@inheritDoc}
     */
    @Override
    public void setMaxConnections(int maxConnections) {
        if (maxConnections == -1) {
            log.warn(sm.getString("endpoint.apr.maxConnections.unlimited",
                    Integer.valueOf(getMaxConnections())));
            return;
        }
        if (running) {
            log.warn(sm.getString("endpoint.apr.maxConnections.running",
                    Integer.valueOf(getMaxConnections())));
            return;
        }
        super.setMaxConnections(maxConnections);
    }


    // --------------------------------------------------------- Public Methods

    /**
     * Obtain the number of kept alive sockets.
     *
     * @return The number of open sockets currently managed by the Poller
     */
    public int getKeepAliveCount() {
        if (poller == null) {
            return 0;
        }

        return poller.getConnectionCount();
    }


    /**
     * Obtain the number of sendfile sockets.
     *
     * @return The number of sockets currently managed by the Sendfile poller.
     */
    public int getSendfileCount() {
        if (sendfile == null) {
            return 0;
        }

        return sendfile.getSendfileCount();
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

        // Enable Sendfile by default if it has not been configured but usage on
        // systems which don't support it cause major problems
        if (!useSendFileSet) {
            setUseSendfileInternal(Library.APR_HAS_SENDFILE);
        } else if (getUseSendfile() && !Library.APR_HAS_SENDFILE) {
            setUseSendfileInternal(false);
        }

        // Initialize thread count default for acceptor
        if (acceptorThreadCount == 0) {
            // FIXME: Doesn't seem to work that well with multiple accept threads
            acceptorThreadCount = 1;
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
            for (SSLHostConfig sslHostConfig : sslHostConfigs.values()) {

                for (SSLHostConfigCertificate certificate : sslHostConfig.getCertificates(true)) {
                    if (SSLHostConfig.adjustRelativePath(certificate.getCertificateFile()) == null) {
                        // This is required
                        throw new Exception(sm.getString("endpoint.apr.noSslCertFile"));
                    }
                }
                if (sslHostConfig.getCertificates().size() > 2) {
                    // TODO: Can this limitation be removed?
                    throw new Exception(sm.getString("endpoint.apr.tooManyCertFiles"));
                }

                // SSL protocol
                int value = SSL.SSL_PROTOCOL_NONE;
                if (sslHostConfig.getProtocols().size() == 0) {
                    // Native fallback used if protocols=""
                    value = SSL.SSL_PROTOCOL_ALL;
                } else {
                    for (String protocol : sslHostConfig.getProtocols()) {
                        if (Constants.SSL_PROTO_SSLv2Hello.equalsIgnoreCase(protocol)) {
                            // NO-OP. OpenSSL always supports SSLv2Hello
                        } else if (Constants.SSL_PROTO_SSLv2.equalsIgnoreCase(protocol)) {
                            value |= SSL.SSL_PROTOCOL_SSLV2;
                        } else if (Constants.SSL_PROTO_SSLv3.equalsIgnoreCase(protocol)) {
                            value |= SSL.SSL_PROTOCOL_SSLV3;
                        } else if (Constants.SSL_PROTO_TLSv1.equalsIgnoreCase(protocol)) {
                            value |= SSL.SSL_PROTOCOL_TLSV1;
                        } else if (Constants.SSL_PROTO_TLSv1_1.equalsIgnoreCase(protocol)) {
                            value |= SSL.SSL_PROTOCOL_TLSV1_1;
                        } else if (Constants.SSL_PROTO_TLSv1_2.equalsIgnoreCase(protocol)) {
                            value |= SSL.SSL_PROTOCOL_TLSV1_2;
                        } else {
                            // Protocol not recognized, fail to start as it is safer than
                            // continuing with the default which might enable more than the
                            // is required
                            throw new Exception(sm.getString(
                                    "endpoint.apr.invalidSslProtocol", protocol));
                        }
                    }
                }

                // Create SSL Context
                long ctx = 0;
                try {
                    ctx = SSLContext.make(rootPool, value, SSL.SSL_MODE_SERVER);
                } catch (Exception e) {
                    // If the sslEngine is disabled on the AprLifecycleListener
                    // there will be an Exception here but there is no way to check
                    // the AprLifecycleListener settings from here
                    throw new Exception(
                            sm.getString("endpoint.apr.failSslContextMake"), e);
                }

                boolean legacyRenegSupported = false;
                try {
                    legacyRenegSupported = SSL.hasOp(SSL.SSL_OP_ALLOW_UNSAFE_LEGACY_RENEGOTIATION);
                    if (legacyRenegSupported)
                        if (sslHostConfig.getInsecureRenegotiation()) {
                            SSLContext.setOptions(ctx, SSL.SSL_OP_ALLOW_UNSAFE_LEGACY_RENEGOTIATION);
                        } else {
                            SSLContext.clearOptions(ctx, SSL.SSL_OP_ALLOW_UNSAFE_LEGACY_RENEGOTIATION);
                        }
                } catch (UnsatisfiedLinkError e) {
                    // Ignore
                }
                if (!legacyRenegSupported) {
                    // OpenSSL does not support unsafe legacy renegotiation.
                    log.warn(sm.getString("endpoint.warn.noInsecureReneg",
                                          SSL.versionString()));
                }

                // Use server's preference order for ciphers (rather than
                // client's)
                boolean orderCiphersSupported = false;
                try {
                    orderCiphersSupported = SSL.hasOp(SSL.SSL_OP_CIPHER_SERVER_PREFERENCE);
                    if (orderCiphersSupported) {
                        if (sslHostConfig.getHonorCipherOrder()) {
                            SSLContext.setOptions(ctx, SSL.SSL_OP_CIPHER_SERVER_PREFERENCE);
                        } else {
                            SSLContext.clearOptions(ctx, SSL.SSL_OP_CIPHER_SERVER_PREFERENCE);
                        }
                    }
                } catch (UnsatisfiedLinkError e) {
                    // Ignore
                }
                if (!orderCiphersSupported) {
                    // OpenSSL does not support ciphers ordering.
                    log.warn(sm.getString("endpoint.warn.noHonorCipherOrder",
                                          SSL.versionString()));
                }

                // Disable compression if requested
                boolean disableCompressionSupported = false;
                try {
                    disableCompressionSupported = SSL.hasOp(SSL.SSL_OP_NO_COMPRESSION);
                    if (disableCompressionSupported) {
                        if (sslHostConfig.getDisableCompression()) {
                            SSLContext.setOptions(ctx, SSL.SSL_OP_NO_COMPRESSION);
                        } else {
                            SSLContext.clearOptions(ctx, SSL.SSL_OP_NO_COMPRESSION);
                        }
                    }
                } catch (UnsatisfiedLinkError e) {
                    // Ignore
                }
                if (!disableCompressionSupported) {
                    // OpenSSL does not support ciphers ordering.
                    log.warn(sm.getString("endpoint.warn.noDisableCompression",
                                          SSL.versionString()));
                }

                // Disable TLS Session Tickets (RFC4507) to protect perfect forward secrecy
                boolean disableSessionTicketsSupported = false;
                try {
                    disableSessionTicketsSupported = SSL.hasOp(SSL.SSL_OP_NO_TICKET);
                    if (disableSessionTicketsSupported) {
                        if (sslHostConfig.getDisableSessionTickets()) {
                            SSLContext.setOptions(ctx, SSL.SSL_OP_NO_TICKET);
                        } else {
                            SSLContext.clearOptions(ctx, SSL.SSL_OP_NO_TICKET);
                        }
                    }
                } catch (UnsatisfiedLinkError e) {
                    // Ignore
                }
                if (!disableSessionTicketsSupported) {
                    // OpenSSL is too old to support TLS Session Tickets.
                    log.warn(sm.getString("endpoint.warn.noDisableSessionTickets",
                                          SSL.versionString()));
                }

                // List the ciphers that the client is permitted to negotiate
                SSLContext.setCipherSuite(ctx, sslHostConfig.getCiphers());
                // Load Server key and certificate
                // TODO: Confirm assumption that idx is not specific to
                //       key/certificate type
                int idx = 0;
                for (SSLHostConfigCertificate certificate : sslHostConfig.getCertificates(true)) {
                    SSLContext.setCertificate(ctx,
                            SSLHostConfig.adjustRelativePath(certificate.getCertificateFile()),
                            SSLHostConfig.adjustRelativePath(certificate.getCertificateKeyFile()),
                            certificate.getCertificateKeyPassword(), idx++);
                }
                // Support Client Certificates
                SSLContext.setCACertificate(ctx,
                        SSLHostConfig.adjustRelativePath(sslHostConfig.getCaCertificateFile()),
                        SSLHostConfig.adjustRelativePath(sslHostConfig.getCaCertificatePath()));
                // Set revocation
                SSLContext.setCARevocation(ctx,
                        SSLHostConfig.adjustRelativePath(
                                sslHostConfig.getCertificateRevocationListFile()),
                        SSLHostConfig.adjustRelativePath(
                                sslHostConfig.getCertificateRevocationListPath()));
                // Client certificate verification
                switch (sslHostConfig.getCertificateVerification()) {
                case NONE:
                    value = SSL.SSL_CVERIFY_NONE;
                    break;
                case OPTIONAL:
                    value = SSL.SSL_CVERIFY_OPTIONAL;
                    break;
                case OPTIONAL_NO_CA:
                    value = SSL.SSL_CVERIFY_OPTIONAL_NO_CA;
                    break;
                case REQUIRED:
                    value = SSL.SSL_CVERIFY_REQUIRE;
                    break;
                }
                SSLContext.setVerify(ctx, value, sslHostConfig.getCertificateVerificationDepth());
                // For now, sendfile is not supported with SSL
                if (getUseSendfile()) {
                    setUseSendfileInternal(false);
                    if (useSendFileSet) {
                        log.warn(sm.getString("endpoint.apr.noSendfileWithSSL"));
                    }
                }

                if (negotiableProtocols.size() > 0) {
                    ArrayList<String> protocols = new ArrayList<>();
                    protocols.addAll(negotiableProtocols);
                    protocols.add("http/1.1");
                    String[] protocolsArray = protocols.toArray(new String[0]);
                    SSLContext.setAlpnProtos(ctx, protocolsArray, SSL.SSL_SELECTOR_FAILURE_NO_ADVERTISE);
                }
                sslHostConfig.setOpenSslContext(Long.valueOf(ctx));
            }
            SSLHostConfig defaultSSLHostConfig = sslHostConfigs.get(getDefaultSSLHostConfigName());
            Long defaultSSLContext = (Long) defaultSSLHostConfig.getOpenSslContext();
            sslContext = defaultSSLContext.longValue();
            SSLContext.registerDefault(defaultSSLContext, this);
        }
    }


    @Override
    public long getSslContext(String sniHostName) {
        SSLHostConfig sslHostConfig = getSSLHostConfig(sniHostName);
        Long ctx = (Long) sslHostConfig.getOpenSslContext();
        if (ctx != null) {
            return ctx.longValue();
        }
        // Default
        return 0;
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

            // Start poller thread
            poller = new Poller();
            poller.init();
            Thread pollerThread = new Thread(poller, getName() + "-Poller");
            pollerThread.setPriority(threadPriority);
            pollerThread.setDaemon(true);
            pollerThread.start();

            // Start sendfile thread
            if (getUseSendfile()) {
                sendfile = new Sendfile();
                sendfile.init();
                Thread sendfileThread =
                        new Thread(sendfile, getName() + "-Sendfile");
                sendfileThread.setPriority(threadPriority);
                sendfileThread.setDaemon(true);
                sendfileThread.start();
            }

            startAcceptorThreads();

            // Start async timeout thread
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
            poller.stop();
            for (SocketWrapperBase<Long> socketWrapper : connections.values()) {
                try {
                    socketWrapper.close();
                    handler.release(socketWrapper);
                } catch (IOException e) {
                    // Ignore
                }
            }
            getAsyncTimeout().stop();
            for (AbstractEndpoint.Acceptor acceptor : acceptors) {
                long waitLeft = 10000;
                while (waitLeft > 0 &&
                        acceptor.getState() != AcceptorState.ENDED &&
                        serverSock != 0) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        // Ignore
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
            try {
                poller.destroy();
            } catch (Exception e) {
                // Ignore
            }
            poller = null;
            connections.clear();
            if (getUseSendfile()) {
                try {
                    sendfile.destroy();
                } catch (Exception e) {
                    // Ignore
                }
                sendfile = null;
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

        if (sslContext != 0) {
            Long ctx = Long.valueOf(sslContext);
            SSLContext.unregisterDefault(ctx);
            for (SSLHostConfig sslHostConfig : sslHostConfigs.values()) {
                sslHostConfig.setOpenSslContext(null);
            }
            sslContext = 0;
        }

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
    protected boolean setSocketOptions(SocketWrapperBase<Long> socketWrapper) {
        long socket = socketWrapper.getSocket().longValue();
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

                if (negotiableProtocols.size() > 0) {
                    byte[] negotiated = new byte[256];
                    int len = SSLSocket.getALPN(socket, negotiated);
                    String negotiatedProtocol =
                            new String(negotiated, 0, len, StandardCharsets.UTF_8);
                    if (negotiatedProtocol.length() > 0) {
                        socketWrapper.setNegotiatedProtocol(negotiatedProtocol);
                        if (log.isDebugEnabled()) {
                            log.debug(sm.getString("endpoint.alpn.negotiated", negotiatedProtocol));
                        }
                    }
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
     * Process given socket. This is called when the socket has been
     * accepted.
     */
    protected boolean processSocketWithOptions(long socket) {
        try {
            // During shutdown, executor may be null - avoid NPE
            if (running) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("endpoint.debug.socket",
                            Long.valueOf(socket)));
                }
                AprSocketWrapper wrapper = new AprSocketWrapper(Long.valueOf(socket), this);
                wrapper.setKeepAliveLeft(getMaxKeepAliveRequests());
                wrapper.setSecure(isSSLEnabled());
                wrapper.setReadTimeout(getSoTimeout());
                wrapper.setWriteTimeout(getSoTimeout());
                connections.put(Long.valueOf(socket), wrapper);
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
     * Process the given socket. Typically keep alive or upgraded protocol.
     *
     * @param socket    The socket to process
     * @param status    The current status of the socket
     *
     * @return <code>true</code> if the processing completed normally otherwise
     *         <code>false</code> which indicates an error occurred and that the
     *         socket should be closed
     */
    public boolean processSocket(long socket, SocketStatus status) {
        try {
            Executor executor = getExecutor();
            if (executor == null) {
                log.warn(sm.getString("endpoint.warn.noExector",
                        Long.valueOf(socket), null));
            } else {
                SocketWrapperBase<Long> wrapper =
                        connections.get(Long.valueOf(socket));
                // Make sure connection hasn't been closed
                if (wrapper != null) {
                    executor.execute(new SocketProcessor(wrapper, status));
                }
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


    @Override
    public void processSocket(SocketWrapperBase<Long> socket, SocketStatus status,
            boolean dispatch) {
        try {
            // Synchronisation is required here as this code may be called as a
            // result of calling AsyncContext.dispatch() from a non-container
            // thread
            synchronized (socket) {
                waitingRequests.remove(socket);
                SocketProcessor proc = new SocketProcessor(socket, status);
                Executor executor = getExecutor();
                if (dispatch && executor != null) {
                    executor.execute(proc);
                } else {
                    proc.run();
                }
            }
        } catch (RejectedExecutionException ree) {
            log.warn(sm.getString("endpoint.executor.fail", socket) , ree);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // This means we got an OOM or similar creating a thread, or that
            // the pool and its queue are full
            log.error(sm.getString("endpoint.process.fail"), t);
        }
    }

    private void closeSocket(long socket) {
        // Once this is called, the mapping from socket to wrapper will no
        // longer be required.
        SocketWrapperBase<Long> wrapper = connections.remove(Long.valueOf(socket));
        if (wrapper != null) {
            // Cast to avoid having to catch an IOE that is never thrown.
            ((AprSocketWrapper) wrapper).close();
        }
    }

    /*
     * This method should only be called if there is no chance that the socket
     * is currently being used by the Poller. It is generally a bad idea to call
     * this directly from a known error condition.
     */
    private void destroySocket(long socket) {
        connections.remove(Long.valueOf(socket));
        if (log.isDebugEnabled()) {
            String msg = sm.getString("endpoint.debug.destroySocket",
                    Long.valueOf(socket));
            if (log.isTraceEnabled()) {
                log.trace(msg, new Exception());
            } else {
                log.debug(msg);
            }
        }
        // Be VERY careful if you call this method directly. If it is called
        // twice for the same socket the JVM will core. Currently this is only
        // called from Poller.closePollset() to ensure kept alive connections
        // are closed when calling stop() followed by start().
        if (socket != 0) {
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
                        if (log.isDebugEnabled()) {
                            long sa = Address.get(Socket.APR_REMOTE, socket);
                            Sockaddr addr = Address.getInfo(sa);
                            log.debug(sm.getString("endpoint.apr.remoteport",
                                    Long.valueOf(socket),
                                    Long.valueOf(addr.port)));
                        }
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
                            // Close socket right away
                            closeSocket(socket);
                        }
                    } else {
                        // Close socket right away
                        // No code path could have added the socket to the
                        // Poller so use destroySocket()
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


    // -------------------------------------------------- SocketInfo Inner Class

    public static class SocketInfo {
        public long socket;
        public long timeout;
        public int flags;
        public boolean read() {
            return (flags & Poll.APR_POLLIN) == Poll.APR_POLLIN;
        }
        public boolean write() {
            return (flags & Poll.APR_POLLOUT) == Poll.APR_POLLOUT;
        }
        public static int merge(int flag1, int flag2) {
            return ((flag1 & Poll.APR_POLLIN) | (flag2 & Poll.APR_POLLIN))
                | ((flag1 & Poll.APR_POLLOUT) | (flag2 & Poll.APR_POLLOUT));
        }
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Socket: [");
            sb.append(socket);
            sb.append("], timeout: [");
            sb.append(timeout);
            sb.append("], flags: [");
            sb.append(flags);
            return sb.toString();
        }
    }


    // ---------------------------------------------- SocketTimeouts Inner Class

    public class SocketTimeouts {
        protected int size;

        protected long[] sockets;
        protected long[] timeouts;
        protected int pos = 0;

        public SocketTimeouts(int size) {
            this.size = 0;
            sockets = new long[size];
            timeouts = new long[size];
        }

        public void add(long socket, long timeout) {
            sockets[size] = socket;
            timeouts[size] = timeout;
            size++;
        }

        /**
         * Removes the specified socket from the poller.
         *
         * @param socket The socket to remove
         *
         * @return The configured timeout for the socket or zero if the socket
         *         was not in the list of socket timeouts
         */
        public long remove(long socket) {
            long result = 0;
            for (int i = 0; i < size; i++) {
                if (sockets[i] == socket) {
                    result = timeouts[i];
                    sockets[i] = sockets[size - 1];
                    timeouts[i] = timeouts[size - 1];
                    size--;
                    break;
                }
            }
            return result;
        }

        public long check(long date) {
            while (pos < size) {
                if (date >= timeouts[pos]) {
                    long result = sockets[pos];
                    sockets[pos] = sockets[size - 1];
                    timeouts[pos] = timeouts[size - 1];
                    size--;
                    return result;
                }
                pos++;
            }
            pos = 0;
            return 0;
        }

    }


    // -------------------------------------------------- SocketList Inner Class

    public class SocketList {
        protected volatile int size;
        protected int pos;

        protected long[] sockets;
        protected long[] timeouts;
        protected int[] flags;

        protected SocketInfo info = new SocketInfo();

        public SocketList(int size) {
            this.size = 0;
            pos = 0;
            sockets = new long[size];
            timeouts = new long[size];
            flags = new int[size];
        }

        public int size() {
            return this.size;
        }

        public SocketInfo get() {
            if (pos == size) {
                return null;
            } else {
                info.socket = sockets[pos];
                info.timeout = timeouts[pos];
                info.flags = flags[pos];
                pos++;
                return info;
            }
        }

        public void clear() {
            size = 0;
            pos = 0;
        }

        public boolean add(long socket, long timeout, int flag) {
            if (size == sockets.length) {
                return false;
            } else {
                for (int i = 0; i < size; i++) {
                    if (sockets[i] == socket) {
                        flags[i] = SocketInfo.merge(flags[i], flag);
                        return true;
                    }
                }
                sockets[size] = socket;
                timeouts[size] = timeout;
                flags[size] = flag;
                size++;
                return true;
            }
        }

        public boolean remove(long socket) {
            for (int i = 0; i < size; i++) {
                if (sockets[i] == socket) {
                    sockets[i] = sockets[size - 1];
                    timeouts[i] = timeouts[size - 1];
                    flags[size] = flags[size -1];
                    size--;
                    return true;
                }
            }
            return false;
        }

        public void duplicate(SocketList copy) {
            copy.size = size;
            copy.pos = pos;
            System.arraycopy(sockets, 0, copy.sockets, 0, size);
            System.arraycopy(timeouts, 0, copy.timeouts, 0, size);
            System.arraycopy(flags, 0, copy.flags, 0, size);
        }

    }

    // ------------------------------------------------------ Poller Inner Class

    public class Poller implements Runnable {

        /**
         * Pointers to the pollers.
         */
        private long[] pollers = null;

        /**
         * Actual poller size.
         */
        private int actualPollerSize = 0;

        /**
         * Amount of spots left in the poller.
         */
        private int[] pollerSpace = null;

        /**
         * Amount of low level pollers in use by this poller.
         */
        private int pollerCount;

        /**
         * Timeout value for the poll call.
         */
        private int pollerTime;

        /**
         * Variable poller timeout that adjusts depending on how many poll sets
         * are in use so that the total poll time across all poll sets remains
         * equal to pollTime.
         */
        private int nextPollerTime;

        /**
         * Root pool.
         */
        private long pool = 0;

        /**
         * Socket descriptors.
         */
        private long[] desc;

        /**
         * List of sockets to be added to the poller.
         */
        private SocketList addList = null;  // Modifications guarded by this


        /**
         * List of sockets to be closed.
         */
        private SocketList closeList = null; // Modifications guarded by this


        /**
         * Structure used for storing timeouts.
         */
        private SocketTimeouts timeouts = null;


        /**
         * Last run of maintain. Maintain will run approximately once every one
         * second (may be slightly longer between runs).
         */
        private long lastMaintain = System.currentTimeMillis();


        /**
         * The number of connections currently inside this Poller. The correct
         * operation of the Poller depends on this figure being correct. If it
         * is not, it is possible that the Poller will enter a wait loop where
         * it waits for the next connection to be added to the Poller before it
         * calls poll when it should still be polling existing connections.
         * Although not necessary at the time of writing this comment, it has
         * been implemented as an AtomicInteger to ensure that it remains
         * thread-safe.
         */
        private AtomicInteger connectionCount = new AtomicInteger(0);
        public int getConnectionCount() { return connectionCount.get(); }


        private volatile boolean pollerRunning = true;

        /**
         * Create the poller. With some versions of APR, the maximum poller size
         * will be 62 (recompiling APR is necessary to remove this limitation).
         */
        protected synchronized void init() {

            pool = Pool.create(serverSockPool);

            // Single poller by default
            int defaultPollerSize = getMaxConnections();

            if ((OS.IS_WIN32 || OS.IS_WIN64) && (defaultPollerSize > 1024)) {
                // The maximum per poller to get reasonable performance is 1024
                // Adjust poller size so that it won't reach the limit. This is
                // a limitation of XP / Server 2003 that has been fixed in
                // Vista / Server 2008 onwards.
                actualPollerSize = 1024;
            } else {
                actualPollerSize = defaultPollerSize;
            }

            timeouts = new SocketTimeouts(defaultPollerSize);

            // At the moment, setting the timeout is useless, but it could get
            // used again as the normal poller could be faster using maintain.
            // It might not be worth bothering though.
            long pollset = allocatePoller(actualPollerSize, pool, -1);
            if (pollset == 0 && actualPollerSize > 1024) {
                actualPollerSize = 1024;
                pollset = allocatePoller(actualPollerSize, pool, -1);
            }
            if (pollset == 0) {
                actualPollerSize = 62;
                pollset = allocatePoller(actualPollerSize, pool, -1);
            }

            pollerCount = defaultPollerSize / actualPollerSize;
            pollerTime = pollTime / pollerCount;
            nextPollerTime = pollerTime;

            pollers = new long[pollerCount];
            pollers[0] = pollset;
            for (int i = 1; i < pollerCount; i++) {
                pollers[i] = allocatePoller(actualPollerSize, pool, -1);
            }

            pollerSpace = new int[pollerCount];
            for (int i = 0; i < pollerCount; i++) {
                pollerSpace[i] = actualPollerSize;
            }

            /*
             * x2 - One descriptor for the socket, one for the event(s).
             * x2 - Some APR implementations return multiple events for the
             *      same socket as different entries. Each socket is registered
             *      for a maximum of two events (read and write) at any one
             *      time.
             *
             * Therefore size is actual poller size *4.
             */
            desc = new long[actualPollerSize * 4];
            connectionCount.set(0);
            addList = new SocketList(defaultPollerSize);
            closeList = new SocketList(defaultPollerSize);
        }


        /*
         * This method is synchronized so that it is not possible for a socket
         * to be added to the Poller's addList once this method has completed.
         */
        protected synchronized void stop() {
            pollerRunning = false;
        }


        /**
         * Destroy the poller.
         */
        protected synchronized void destroy() {
            // Wait for pollerTime before doing anything, so that the poller
            // threads exit, otherwise parallel destruction of sockets which are
            // still in the poller can cause problems
            try {
                this.notify();
                this.wait(pollerCount * pollTime / 1000);
            } catch (InterruptedException e) {
                // Ignore
            }
            // Close all sockets in the close queue
            SocketInfo info = closeList.get();
            while (info != null) {
                // Make sure we aren't trying add the socket as well as close it
                addList.remove(info.socket);
                // Make sure the  socket isn't in the poller before we close it
                removeFromPoller(info.socket);
                // Poller isn't running at this point so use destroySocket()
                // directly
                destroySocket(info.socket);
                info = closeList.get();
            }
            closeList.clear();
            // Close all sockets in the add queue
            info = addList.get();
            while (info != null) {
                // Make sure the  socket isn't in the poller before we close it
                removeFromPoller(info.socket);
                // Poller isn't running at this point so use destroySocket()
                // directly
                destroySocket(info.socket);
                info = addList.get();
            }
            addList.clear();
            // Close all sockets still in the poller
            for (int i = 0; i < pollerCount; i++) {
                int rv = Poll.pollset(pollers[i], desc);
                if (rv > 0) {
                    for (int n = 0; n < rv; n++) {
                        destroySocket(desc[n*2+1]);
                    }
                }
            }
            Pool.destroy(pool);
            connectionCount.set(0);
        }


        /**
         * Add specified socket and associated pool to the poller. The socket
         * will be added to a temporary array, and polled first after a maximum
         * amount of time equal to pollTime (in most cases, latency will be much
         * lower, however). Note: If both read and write are false, the socket
         * will only be checked for timeout; if the socket was already present
         * in the poller, a callback event will be generated and the socket will
         * be removed from the poller.
         *
         * @param socket to add to the poller
         * @param timeout to use for this connection in milliseconds
         * @param flags Events to poll for (Poll.APR_POLLIN and/or
         *              Poll.APR_POLLOUT)
         */
        private void add(long socket, long timeout, int flags) {
            if (log.isDebugEnabled()) {
                String msg = sm.getString("endpoint.debug.pollerAdd",
                        Long.valueOf(socket), Long.valueOf(timeout),
                        Integer.valueOf(flags));
                if (log.isTraceEnabled()) {
                    log.trace(msg, new Exception());
                } else {
                    log.debug(msg);
                }
            }
            if (timeout <= 0) {
                // Always put a timeout in
                timeout = Integer.MAX_VALUE;
            }
            synchronized (this) {
                // Add socket to the list. Newly added sockets will wait
                // at most for pollTime before being polled.
                if (addList.add(socket, timeout, flags)) {
                    this.notify();
                }
            }
        }


        /**
         * Add specified socket to one of the pollers. Must only be called from
         * {@link Poller#run()}.
         */
        private boolean addToPoller(long socket, int events) {
            int rv = -1;
            for (int i = 0; i < pollers.length; i++) {
                if (pollerSpace[i] > 0) {
                    rv = Poll.add(pollers[i], socket, events);
                    if (rv == Status.APR_SUCCESS) {
                        pollerSpace[i]--;
                        connectionCount.incrementAndGet();
                        return true;
                    }
                }
            }
            return false;
        }


        /*
         * This is only called from the SocketWrapper to ensure that it is only
         * called once per socket. Calling it more than once typically results
         * in the JVM crash.
         */
        private synchronized void close(long socket) {
            closeList.add(socket, 0, 0);
            this.notify();
        }


        /**
         * Remove specified socket from the pollers. Must only be called from
         * {@link Poller#run()}.
         */
        private void removeFromPoller(long socket) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("endpoint.debug.pollerRemove",
                        Long.valueOf(socket)));
            }
            int rv = -1;
            for (int i = 0; i < pollers.length; i++) {
                if (pollerSpace[i] < actualPollerSize) {
                    rv = Poll.remove(pollers[i], socket);
                    if (rv != Status.APR_NOTFOUND) {
                        pollerSpace[i]++;
                        connectionCount.decrementAndGet();
                        if (log.isDebugEnabled()) {
                            log.debug(sm.getString("endpoint.debug.pollerRemoved",
                                    Long.valueOf(socket)));
                        }
                        break;
                    }
                }
            }
            timeouts.remove(socket);
        }

        /**
         * Timeout checks. Must only be called from {@link Poller#run()}.
         */
        private synchronized void maintain() {
            long date = System.currentTimeMillis();
            // Maintain runs at most once every 1s, although it will likely get
            // called more
            if ((date - lastMaintain) < 1000L) {
                return;
            } else {
                lastMaintain = date;
            }
            long socket = timeouts.check(date);
            while (socket != 0) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("endpoint.debug.socketTimeout",
                            Long.valueOf(socket)));
                }
                removeFromPoller(socket);
                destroySocket(socket);
                addList.remove(socket);
                closeList.remove(socket);
                socket = timeouts.check(date);
            }

        }

        /**
         * Displays the list of sockets in the pollers.
         */
        @Override
        public String toString() {
            StringBuffer buf = new StringBuffer();
            buf.append("Poller");
            long[] res = new long[actualPollerSize * 2];
            for (int i = 0; i < pollers.length; i++) {
                int count = Poll.pollset(pollers[i], res);
                buf.append(" [ ");
                for (int j = 0; j < count; j++) {
                    buf.append(desc[2*j+1]).append(" ");
                }
                buf.append("]");
            }
            return buf.toString();
        }

        /**
         * The background thread that adds sockets to the Poller, checks the
         * poller for triggered events and hands the associated socket off to an
         * appropriate processor as events occur.
         */
        @Override
        public void run() {

            SocketList localAddList = new SocketList(getMaxConnections());
            SocketList localCloseList = new SocketList(getMaxConnections());

            // Loop until we receive a shutdown command
            while (pollerRunning) {

                // Check timeouts if the poller is empty.
                while (pollerRunning && connectionCount.get() < 1 &&
                        addList.size() < 1 && closeList.size() < 1) {
                    try {
                        if (getSoTimeout() > 0 && pollerRunning) {
                            maintain();
                        }
                        synchronized (this) {
                            // Make sure that no sockets have been placed in the
                            // addList or closeList since the check above.
                            // Without this check there could be a 10s pause
                            // with no processing since the notify() call in
                            // add()/close() would have no effect since it
                            // happened before this sync block was entered
                            if (addList.size() < 1 && closeList.size() < 1) {
                                this.wait(10000);
                            }
                        }
                    } catch (InterruptedException e) {
                        // Ignore
                    } catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                        getLog().warn(sm.getString("endpoint.timeout.err"));
                    }
                }

                // Don't add or poll if the poller has been stopped
                if (!pollerRunning) {
                    break;
                }

                try {
                    // Duplicate the add and remove lists so that the syncs are
                    // minimised
                    synchronized (this) {
                        if (closeList.size() > 0) {
                            // Duplicate to another list, so that the syncing is
                            // minimal
                            closeList.duplicate(localCloseList);
                            closeList.clear();
                        } else {
                            localCloseList.clear();
                        }
                    }
                    synchronized (this) {
                        if (addList.size() > 0) {
                            // Duplicate to another list, so that the syncing is
                            // minimal
                            addList.duplicate(localAddList);
                            addList.clear();
                        } else {
                            localAddList.clear();
                        }
                    }

                    // Remove sockets
                    if (localCloseList.size() > 0) {
                        SocketInfo info = localCloseList.get();
                        while (info != null) {
                            localAddList.remove(info.socket);
                            removeFromPoller(info.socket);
                            destroySocket(info.socket);
                            info = localCloseList.get();
                        }
                    }

                    // Add sockets which are waiting to the poller
                    if (localAddList.size() > 0) {
                        SocketInfo info = localAddList.get();
                        while (info != null) {
                            if (log.isDebugEnabled()) {
                                log.debug(sm.getString(
                                        "endpoint.debug.pollerAddDo",
                                        Long.valueOf(info.socket)));
                            }
                            timeouts.remove(info.socket);
                            AprSocketWrapper wrapper = connections.get(
                                    Long.valueOf(info.socket));
                            if (wrapper == null) {
                                continue;
                            }
                            if (info.read() || info.write()) {
                                wrapper.pollerFlags = wrapper.pollerFlags |
                                        (info.read() ? Poll.APR_POLLIN : 0) |
                                        (info.write() ? Poll.APR_POLLOUT : 0);
                                // A socket can only be added to the poller
                                // once. Adding it twice will return an error
                                // which will close the socket. Therefore make
                                // sure the socket we are about to add isn't in
                                // the poller.
                                removeFromPoller(info.socket);
                                if (!addToPoller(info.socket, wrapper.pollerFlags)) {
                                    closeSocket(info.socket);
                                } else {
                                    timeouts.add(info.socket,
                                            System.currentTimeMillis() +
                                                    info.timeout);
                                }
                            } else {
                                // Should never happen.
                                closeSocket(info.socket);
                                getLog().warn(sm.getString(
                                        "endpoint.apr.pollAddInvalid", info));
                            }
                            info = localAddList.get();
                        }
                    }

                    // Poll for the specified interval
                    for (int i = 0; i < pollers.length; i++) {

                        // Flag to ask to reallocate the pool
                        boolean reset = false;

                        int rv = 0;
                        // Iterate on each pollers, but no need to poll empty pollers
                        if (pollerSpace[i] < actualPollerSize) {
                            rv = Poll.poll(pollers[i], nextPollerTime, desc, true);
                            // Reset the nextPollerTime
                            nextPollerTime = pollerTime;
                        } else {
                            // Skipping an empty poll set means skipping a wait
                            // time of pollerTime microseconds. If most of the
                            // poll sets are skipped then this loop will be
                            // tighter than expected which could lead to higher
                            // than expected CPU usage. Extending the
                            // nextPollerTime ensures that this loop always
                            // takes about the same time to execute.
                            nextPollerTime += pollerTime;
                        }
                        if (rv > 0) {
                            rv = mergeDescriptors(desc, rv);
                            pollerSpace[i] += rv;
                            connectionCount.addAndGet(-rv);
                            for (int n = 0; n < rv; n++) {
                                long timeout = timeouts.remove(desc[n*2+1]);
                                AprSocketWrapper wrapper = connections.get(
                                        Long.valueOf(desc[n*2+1]));
                                if (getLog().isDebugEnabled()) {
                                    log.debug(sm.getString(
                                            "endpoint.debug.pollerProcess",
                                            Long.valueOf(desc[n*2+1]),
                                            Long.valueOf(desc[n*2])));
                                }
                                wrapper.pollerFlags = wrapper.pollerFlags & ~((int) desc[n*2]);
                                // Check for failed sockets and hand this socket off to a worker
                                if (((desc[n*2] & Poll.APR_POLLHUP) == Poll.APR_POLLHUP)
                                        || ((desc[n*2] & Poll.APR_POLLERR) == Poll.APR_POLLERR)
                                        || ((desc[n*2] & Poll.APR_POLLNVAL) == Poll.APR_POLLNVAL)) {
                                    if (wrapper.isAsync() || wrapper.isUpgraded()) {
                                        // Must be using non-blocking IO for the socket to be in the
                                        // poller during async processing. Need to trigger error
                                        // handling. Poller may return error codes plus the flags it
                                        // was waiting for or it may just return an error code. We
                                        // could return ASYNC_[WRITE|READ]_ERROR here but if we do,
                                        // there will be no exception associated with the error in
                                        // application code. By signalling read/write is possible, a
                                        // read/write will be attempted, fail and that will trigger
                                        // an exception the application will see.
                                        // Check the return flags first, followed by what the socket
                                        // was registered for
                                        if ((desc[n*2] & Poll.APR_POLLIN) == Poll.APR_POLLIN) {
                                            // Error probably occurred during a non-blocking read
                                            if (!processSocket(desc[n*2+1], SocketStatus.OPEN_READ)) {
                                                // Close socket and clear pool
                                                closeSocket(desc[n*2+1]);
                                            }
                                        } else if ((desc[n*2] & Poll.APR_POLLOUT) == Poll.APR_POLLOUT) {
                                            // Error probably occurred during a non-blocking write
                                            if (!processSocket(desc[n*2+1], SocketStatus.OPEN_WRITE)) {
                                                // Close socket and clear pool
                                                closeSocket(desc[n*2+1]);
                                            }
                                        } else if ((wrapper.pollerFlags & Poll.APR_POLLIN) == Poll.APR_POLLIN) {
                                            // Can't tell what was happening when the error occurred but the
                                            // socket is registered for non-blocking read so use that
                                            if (!processSocket(desc[n*2+1], SocketStatus.OPEN_READ)) {
                                                // Close socket and clear pool
                                                closeSocket(desc[n*2+1]);
                                            }
                                        } else if ((wrapper.pollerFlags & Poll.APR_POLLOUT) == Poll.APR_POLLOUT) {
                                            // Can't tell what was happening when the error occurred but the
                                            // socket is registered for non-blocking write so use that
                                            if (!processSocket(desc[n*2+1], SocketStatus.OPEN_WRITE)) {
                                                // Close socket and clear pool
                                                closeSocket(desc[n*2+1]);
                                            }
                                        } else {
                                            // Close socket and clear pool
                                            closeSocket(desc[n*2+1]);
                                        }
                                    } else {
                                        // Close socket and clear pool
                                        closeSocket(desc[n*2+1]);
                                    }
                                } else if (((desc[n*2] & Poll.APR_POLLIN) == Poll.APR_POLLIN)
                                        || ((desc[n*2] & Poll.APR_POLLOUT) == Poll.APR_POLLOUT)) {
                                    boolean error = false;
                                    if (((desc[n*2] & Poll.APR_POLLIN) == Poll.APR_POLLIN) &&
                                            !processSocket(desc[n*2+1], SocketStatus.OPEN_READ)) {
                                        error = true;
                                        // Close socket and clear pool
                                        closeSocket(desc[n*2+1]);
                                    }
                                    if (!error &&
                                            ((desc[n*2] & Poll.APR_POLLOUT) == Poll.APR_POLLOUT) &&
                                            !processSocket(desc[n*2+1], SocketStatus.OPEN_WRITE)) {
                                        // Close socket and clear pool
                                        error = true;
                                        closeSocket(desc[n*2+1]);
                                    }
                                    if (!error && wrapper.pollerFlags != 0) {
                                        // If socket was registered for multiple events but
                                        // only some of the occurred, re-register for the
                                        // remaining events.
                                        // timeout is the value of System.currentTimeMillis() that
                                        // was set as the point that the socket will timeout. When
                                        // adding to the poller, the timeout from now in
                                        // milliseconds is required.
                                        // So first, subtract the current timestamp
                                        if (timeout > 0) {
                                            timeout = timeout - System.currentTimeMillis();
                                        }
                                        // If the socket should have already expired by now,
                                        // re-add it with a very short timeout
                                        if (timeout <= 0) {
                                            timeout = 1;
                                        }
                                        // Should be impossible but just in case since timeout will
                                        // be cast to an int.
                                        if (timeout > Integer.MAX_VALUE) {
                                            timeout = Integer.MAX_VALUE;
                                        }
                                        add(desc[n*2+1], (int) timeout, wrapper.pollerFlags);
                                    }
                                } else {
                                    // Unknown event
                                    getLog().warn(sm.getString(
                                            "endpoint.apr.pollUnknownEvent",
                                            Long.valueOf(desc[n*2])));
                                    // Close socket and clear pool
                                    closeSocket(desc[n*2+1]);
                                }
                            }
                        } else if (rv < 0) {
                            int errn = -rv;
                            // Any non timeup or interrupted error is critical
                            if ((errn != Status.TIMEUP) && (errn != Status.EINTR)) {
                                if (errn >  Status.APR_OS_START_USERERR) {
                                    errn -=  Status.APR_OS_START_USERERR;
                                }
                                getLog().error(sm.getString(
                                        "endpoint.apr.pollError",
                                        Integer.valueOf(errn),
                                        Error.strerror(errn)));
                                // Destroy and reallocate the poller
                                reset = true;
                            }
                        }

                        if (reset) {
                            // Reallocate the current poller
                            int count = Poll.pollset(pollers[i], desc);
                            long newPoller = allocatePoller(actualPollerSize, pool, -1);
                            // Don't restore connections for now, since I have not tested it
                            pollerSpace[i] = actualPollerSize;
                            connectionCount.addAndGet(-count);
                            Poll.destroy(pollers[i]);
                            pollers[i] = newPoller;
                        }

                    }
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    getLog().warn(sm.getString("endpoint.poll.error"), t);
                }
                try {
                    // Process socket timeouts
                    if (getSoTimeout() > 0 && pollerRunning) {
                        // This works and uses only one timeout mechanism for everything, but the
                        // non event poller might be a bit faster by using the old maintain.
                        maintain();
                    }
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    getLog().warn(sm.getString("endpoint.timeout.err"), t);
                }
            }

            synchronized (this) {
                this.notifyAll();
            }
        }


        private int mergeDescriptors(long[] desc, int startCount) {
            /*
             * https://bz.apache.org/bugzilla/show_bug.cgi?id=57653#c6 suggests
             * this merging is only necessary on OSX and BSD.
             *
             * https://bz.apache.org/bugzilla/show_bug.cgi?id=56313 suggests the
             * same, or a similar, issue is happening on Windows.
             * Notes: Only the first startCount * 2 elements of the array
             *        are populated.
             *        The array is event, socket, event, socket etc.
             */
            HashMap<Long,Long> merged = new HashMap<>(startCount);
            for (int n = 0; n < startCount; n++) {
                Long newValue = merged.merge(Long.valueOf(desc[2*n+1]), Long.valueOf(desc[2*n]),
                        (v1, v2) -> Long.valueOf(v1.longValue() | v2.longValue()));
                if (log.isDebugEnabled()) {
                    if (newValue.longValue() != desc[2*n]) {
                        log.debug(sm.getString("endpoint.apr.pollMergeEvents",
                                Long.valueOf(desc[2*n+1]), Long.valueOf(desc[2*n]), newValue));
                    }
                }
            }
            int i = 0;
            for (Map.Entry<Long,Long> entry : merged.entrySet()) {
                desc[i++] = entry.getValue().longValue();
                desc[i++] = entry.getKey().longValue();
            }
            return merged.size();
        }
    }


    // ----------------------------------------------- SendfileData Inner Class

    /**
     * SendfileData class.
     */
    public static class SendfileData extends SendfileDataBase {
        // File
        protected long fd;
        protected long fdpool;
        // Socket and socket pool
        protected long socket;

        public SendfileData(String filename, long pos, long length) {
            super(filename, pos, length);
        }
    }


    // --------------------------------------------------- Sendfile Inner Class

    public class Sendfile implements Runnable {

        protected long sendfilePollset = 0;
        protected long pool = 0;
        protected long[] desc;
        protected HashMap<Long, SendfileData> sendfileData;

        protected int sendfileCount;
        public int getSendfileCount() { return sendfileCount; }

        protected ArrayList<SendfileData> addS;

        private volatile boolean sendfileRunning = true;

        /**
         * Create the sendfile poller. With some versions of APR, the maximum
         * poller size will be 62 (recompiling APR is necessary to remove this
         * limitation).
         */
        protected void init() {
            pool = Pool.create(serverSockPool);
            int size = sendfileSize;
            if (size <= 0) {
                size = (OS.IS_WIN32 || OS.IS_WIN64) ? (1 * 1024) : (16 * 1024);
            }
            sendfilePollset = allocatePoller(size, pool, getSoTimeout());
            if (sendfilePollset == 0 && size > 1024) {
                size = 1024;
                sendfilePollset = allocatePoller(size, pool, getSoTimeout());
            }
            if (sendfilePollset == 0) {
                size = 62;
                sendfilePollset = allocatePoller(size, pool, getSoTimeout());
            }
            desc = new long[size * 2];
            sendfileData = new HashMap<>(size);
            addS = new ArrayList<>();
        }

        /**
         * Destroy the poller.
         */
        protected void destroy() {
            sendfileRunning = false;
            // Wait for polltime before doing anything, so that the poller threads
            // exit, otherwise parallel destruction of sockets which are still
            // in the poller can cause problems
            try {
                synchronized (this) {
                    this.notify();
                    this.wait(pollTime / 1000);
                }
            } catch (InterruptedException e) {
                // Ignore
            }
            // Close any socket remaining in the add queue
            for (int i = (addS.size() - 1); i >= 0; i--) {
                SendfileData data = addS.get(i);
                closeSocket(data.socket);
            }
            // Close all sockets still in the poller
            int rv = Poll.pollset(sendfilePollset, desc);
            if (rv > 0) {
                for (int n = 0; n < rv; n++) {
                    closeSocket(desc[n*2+1]);
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
         * @param data containing the reference to the data which should be snet
         * @return true if all the data has been sent right away, and false
         *              otherwise
         */
        public SendfileState add(SendfileData data) {
            // Initialize fd from data given
            try {
                data.fdpool = Socket.pool(data.socket);
                data.fd = File.open
                    (data.fileName, File.APR_FOPEN_READ
                     | File.APR_FOPEN_SENDFILE_ENABLED | File.APR_FOPEN_BINARY,
                     0, data.fdpool);
                // Set the socket to nonblocking mode
                Socket.timeoutSet(data.socket, 0);
                while (true) {
                    long nw = Socket.sendfilen(data.socket, data.fd,
                                               data.pos, data.length, 0);
                    if (nw < 0) {
                        if (!(-nw == Status.EAGAIN)) {
                            Pool.destroy(data.fdpool);
                            data.socket = 0;
                            return SendfileState.ERROR;
                        } else {
                            // Break the loop and add the socket to poller.
                            break;
                        }
                    } else {
                        data.pos += nw;
                        data.length -= nw;
                        if (data.length == 0) {
                            // Entire file has been sent
                            Pool.destroy(data.fdpool);
                            // Set back socket to blocking mode
                            Socket.timeoutSet(data.socket, getSoTimeout() * 1000);
                            return SendfileState.DONE;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn(sm.getString("endpoint.sendfile.error"), e);
                return SendfileState.ERROR;
            }
            // Add socket to the list. Newly added sockets will wait
            // at most for pollTime before being polled
            synchronized (this) {
                addS.add(data);
                this.notify();
            }
            return SendfileState.PENDING;
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
         * The background thread that listens for incoming TCP/IP connections
         * and hands them off to an appropriate processor.
         */
        @Override
        public void run() {

            long maintainTime = 0;
            // Loop until we receive a shutdown command
            while (sendfileRunning) {

                // Loop if endpoint is paused
                while (sendfileRunning && paused) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }
                // Loop if poller is empty
                while (sendfileRunning && sendfileCount < 1 && addS.size() < 1) {
                    // Reset maintain time.
                    maintainTime = 0;
                    try {
                        synchronized (this) {
                            this.wait();
                        }
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }

                // Don't add or poll if the poller has been stopped
                if (!sendfileRunning) {
                    break;
                }

                try {
                    // Add socket to the poller
                    if (addS.size() > 0) {
                        synchronized (this) {
                            for (int i = (addS.size() - 1); i >= 0; i--) {
                                SendfileData data = addS.get(i);
                                int rv = Poll.add(sendfilePollset, data.socket, Poll.APR_POLLOUT);
                                if (rv == Status.APR_SUCCESS) {
                                    sendfileData.put(new Long(data.socket), data);
                                    sendfileCount++;
                                } else {
                                    getLog().warn(sm.getString(
                                            "endpoint.sendfile.addfail",
                                            Integer.valueOf(rv),
                                            Error.strerror(rv)));
                                    // Can't do anything: close the socket right away
                                    closeSocket(data.socket);
                                }
                            }
                            addS.clear();
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
                                closeSocket(state.socket);
                                continue;
                            }
                            // Write some data using sendfile
                            long nw = Socket.sendfilen(state.socket, state.fd,
                                                       state.pos,
                                                       state.length, 0);
                            if (nw < 0) {
                                // Close socket and clear pool
                                remove(state);
                                // Close the socket, as the response would be incomplete
                                // This will close the file too.
                                closeSocket(state.socket);
                                continue;
                            }

                            state.pos += nw;
                            state.length -= nw;
                            if (state.length == 0) {
                                remove(state);
                                if (state.keepAlive) {
                                    // Destroy file descriptor pool, which should close the file
                                    Pool.destroy(state.fdpool);
                                    Socket.timeoutSet(state.socket,
                                            getSoTimeout() * 1000);
                                    // If all done put the socket back in the
                                    // poller for processing of further requests
                                    getPoller().add(
                                            state.socket, getKeepAliveTimeout(),
                                            Poll.APR_POLLIN);
                                } else {
                                    // Close the socket since this is
                                    // the end of not keep-alive request.
                                    closeSocket(state.socket);
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
                            getLog().error(sm.getString(
                                    "endpoint.apr.pollError",
                                    Integer.valueOf(errn),
                                    Error.strerror(errn)));
                            // Handle poll critical failure
                            synchronized (this) {
                                destroy();
                                init();
                            }
                            continue;
                        }
                    }
                    // Call maintain for the sendfile poller
                    if (getSoTimeout() > 0 &&
                            maintainTime > 1000000L && sendfileRunning) {
                        rv = Poll.maintain(sendfilePollset, desc, false);
                        maintainTime = 0;
                        if (rv > 0) {
                            for (int n = 0; n < rv; n++) {
                                // Get the sendfile state
                                SendfileData state = sendfileData.get(new Long(desc[n]));
                                // Close socket and clear pool
                                remove(state);
                                // Destroy file descriptor pool, which should close the file
                                // Close the socket, as the response would be incomplete
                                closeSocket(state.socket);
                            }
                        }
                    }
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    getLog().error(sm.getString("endpoint.poll.error"), t);
                }
            }

            synchronized (this) {
                this.notifyAll();
            }

        }

    }


    // --------------------------------- SocketWithOptionsProcessor Inner Class

    /**
     * This class is the equivalent of the Worker, but will simply use in an
     * external Executor thread pool. This will also set the socket options
     * and do the handshake.
     *
     * This is called after an accept().
     */
    protected class SocketWithOptionsProcessor implements Runnable {

        protected SocketWrapperBase<Long> socket = null;


        public SocketWithOptionsProcessor(SocketWrapperBase<Long> socket) {
            this.socket = socket;
        }

        @Override
        public void run() {

            synchronized (socket) {
                if (!deferAccept) {
                    if (setSocketOptions(socket)) {
                        getPoller().add(socket.getSocket().longValue(),
                                getSoTimeout(), Poll.APR_POLLIN);
                    } else {
                        // Close socket and pool
                        closeSocket(socket.getSocket().longValue());
                        socket = null;
                    }
                } else {
                    // Process the request from this socket
                    if (!setSocketOptions(socket)) {
                        // Close socket and pool
                        closeSocket(socket.getSocket().longValue());
                        socket = null;
                        return;
                    }
                    // Process the request from this socket
                    Handler.SocketState state = handler.process(socket,
                            SocketStatus.OPEN_READ);
                    if (state == Handler.SocketState.CLOSED) {
                        // Close socket and pool
                        closeSocket(socket.getSocket().longValue());
                        socket = null;
                    } else if (state == Handler.SocketState.LONG) {
                        if (socket.isAsync()) {
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

        private final SocketWrapperBase<Long> socket;
        private final SocketStatus status;

        public SocketProcessor(SocketWrapperBase<Long> socket,
                SocketStatus status) {
            this.socket = socket;
            if (status == null) {
                // Should never happen
                throw new NullPointerException();
            }
            this.status = status;
        }

        @Override
        public void run() {
            synchronized (socket) {
                // Process the request from this socket
                if (socket.getSocket() == null) {
                    // Closed in another thread
                    return;
                }
                SocketState state = handler.process(socket, status);
                if (state == Handler.SocketState.CLOSED) {
                    // Close socket and pool
                    closeSocket(socket.getSocket().longValue());
                } else if (state == Handler.SocketState.LONG) {
                    if (socket.isAsync()) {
                        waitingRequests.add(socket);
                    }
                } else if (state == Handler.SocketState.ASYNC_END) {
                    SocketProcessor proc = new SocketProcessor(socket,
                            SocketStatus.OPEN_READ);
                    getExecutor().execute(proc);
                }
            }
        }
    }


    public static class AprSocketWrapper extends SocketWrapperBase<Long> {

        private static final int SSL_OUTPUT_BUFFER_SIZE = 8192;

        private final ByteBuffer sslOutputBuffer;

        private final Object closedLock = new Object();
        private volatile boolean closed = false;

        // This field should only be used by Poller#run()
        private int pollerFlags = 0;


        public AprSocketWrapper(Long socket, AprEndpoint endpoint) {
            super(socket, endpoint);

            // TODO Make the socketWriteBuffer size configurable and align the
            //      SSL and app buffer size settings with NIO & NIO2.
            if (endpoint.isSSLEnabled()) {
                sslOutputBuffer = ByteBuffer.allocateDirect(SSL_OUTPUT_BUFFER_SIZE);
                sslOutputBuffer.position(SSL_OUTPUT_BUFFER_SIZE);
            } else {
                sslOutputBuffer = null;
            }

            socketBufferHandler = new SocketBufferHandler(6 * 1500, 6 * 1500, true);
        }


        @Override
        public int read(boolean block, byte[] b, int off, int len)
                throws IOException {

            socketBufferHandler.configureReadBufferForRead();
            ByteBuffer readBuffer = socketBufferHandler.getReadBuffer();
            int remaining = readBuffer.remaining();

            // Is there enough data in the read buffer to satisfy this request?
            if (remaining >= len) {
                readBuffer.get(b, off, len);
                return len;
            }

            // Copy what data there is in the read buffer to the byte array
            if (remaining > 0) {
                readBuffer.get(b, off, remaining);
                return remaining;
                /*
                 * Since more bytes may have arrived since the buffer was last
                 * filled, it is an option at this point to perform a
                 * non-blocking read. However correctly handling the case if
                 * that read returns end of stream adds complexity. Therefore,
                 * at the moment, the preference is for simplicity.
                 */
            }

            // Fill the read buffer as best we can.
            int nRead = fillReadBuffer(block);

            // Full as much of the remaining byte array as possible with the
            // data that was just read
            if (nRead > 0) {
                socketBufferHandler.configureReadBufferForRead();
                if (nRead > len) {
                    readBuffer.get(b, off, len);
                    return len;
                } else {
                    readBuffer.get(b, off, nRead);
                    return nRead;
                }
            } else {
                return nRead;
            }
        }


        private int fillReadBuffer(boolean block) throws IOException {
            if (closed) {
                throw new IOException(sm.getString("socket.apr.closed", getSocket()));
            }

            socketBufferHandler.configureReadBufferForWrite();
            ByteBuffer socketReadBuffer = socketBufferHandler.getReadBuffer();

            Lock readLock = getBlockingStatusReadLock();
            WriteLock writeLock = getBlockingStatusWriteLock();

            boolean readDone = false;
            int result = 0;
            readLock.lock();
            try {
                if (getBlockingStatus() == block) {
                    if (block) {
                        Socket.timeoutSet(getSocket().longValue(), getReadTimeout() * 1000);
                    }
                    result = Socket.recvb(getSocket().longValue(),
                            socketReadBuffer, socketReadBuffer.position(), socketReadBuffer.remaining());
                    readDone = true;
                }
            } finally {
                readLock.unlock();
            }

            if (!readDone) {
                writeLock.lock();
                try {
                    // Set the current settings for this socket
                    setBlockingStatus(block);
                    if (block) {
                        Socket.optSet(getSocket().longValue(), Socket.APR_SO_NONBLOCK, 0);
                        Socket.timeoutSet(getSocket().longValue(), getReadTimeout() * 1000);
                    } else {
                        Socket.optSet(getSocket().longValue(), Socket.APR_SO_NONBLOCK, 1);
                        Socket.timeoutSet(getSocket().longValue(), 0);
                    }
                    // Downgrade the lock
                    readLock.lock();
                    try {
                        writeLock.unlock();
                        result = Socket.recvb(getSocket().longValue(),
                                socketReadBuffer, socketReadBuffer.position(), socketReadBuffer.remaining());
                    } finally {
                        readLock.unlock();
                    }
                } finally {
                    // Should have been released above but may not have been on some
                    // exception paths
                    if (writeLock.isHeldByCurrentThread()) {
                        writeLock.unlock();
                    }
                }
            }

            if (result > 0) {
                socketReadBuffer.position(socketReadBuffer.position() + result);
                return result;
            } else if (result == 0 || -result == Status.EAGAIN) {
                return 0;
            } else if (-result == Status.APR_EGENERAL && isSecure()) {
                // Not entirely sure why this is necessary. Testing to date has not
                // identified any issues with this but log it so it can be tracked
                // if it is suspected of causing issues in the future.
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("socket.apr.read.sslGeneralError", getSocket(), this));
                }
                return 0;
            } else if ((-result) == Status.ETIMEDOUT || (-result) == Status.TIMEUP) {
                if (block) {
                    throw new SocketTimeoutException(
                            sm.getString("iib.readtimeout"));
                } else {
                    // Attempting to read from the socket when the poller
                    // has not signalled that there is data to read appears
                    // to behave like a blocking read with a short timeout
                    // on OSX rather than like a non-blocking read. If no
                    // data is read, treat the resulting timeout like a
                    // non-blocking read that returned no data.
                    return 0;
                }
            } else if (-result == Status.APR_EOF) {
                return -1;
            } else if ((OS.IS_WIN32 || OS.IS_WIN64) &&
                    (-result == Status.APR_OS_START_SYSERR + 10053)) {
                // 10053 on Windows is connection aborted
                throw new EOFException(sm.getString("socket.apr.clientAbort"));
            } else {
                throw new IOException(sm.getString("socket.apr.read.error",
                        Integer.valueOf(-result), getSocket(), this));
            }
        }


        @Override
        public boolean isReadyForRead() throws IOException {
            socketBufferHandler.configureReadBufferForRead();

            if (socketBufferHandler.getReadBuffer().remaining() > 0) {
                return true;
            }

            fillReadBuffer(false);

            boolean isReady = socketBufferHandler.getReadBuffer().position() > 0;
            return isReady;
        }


        @Override
        public void close() {
            synchronized (closedLock) {
                // APR typically crashes if the same socket is closed twice so
                // make sure that doesn't happen.
                if (closed) {
                    return;
                }
                closed = true;
                if (sslOutputBuffer != null) {
                    ByteBufferUtils.cleanDirectBuffer(sslOutputBuffer);
                }
                ((AprEndpoint) getEndpoint()).getPoller().close(getSocket().longValue());
            }
        }


        @Override
        protected void doWriteInternal(boolean block) throws IOException {
            if (closed) {
                throw new IOException(sm.getString("socket.apr.closed", getSocket()));
            }

            Lock readLock = getBlockingStatusReadLock();
            WriteLock writeLock = getBlockingStatusWriteLock();

            readLock.lock();
            try {
                if (getBlockingStatus() == block) {
                    if (block) {
                        Socket.timeoutSet(getSocket().longValue(), getWriteTimeout() * 1000);
                    }
                    doWriteInternal();
                }
            } finally {
                readLock.unlock();
            }

            writeLock.lock();
            try {
                // Set the current settings for this socket
                setBlockingStatus(block);
                if (block) {
                    Socket.optSet(getSocket().longValue(), Socket.APR_SO_NONBLOCK, 0);
                    Socket.timeoutSet(getSocket().longValue(), getWriteTimeout() * 1000);
                } else {
                    Socket.optSet(getSocket().longValue(), Socket.APR_SO_NONBLOCK, 1);
                    Socket.timeoutSet(getSocket().longValue(), 0);
                }

                // Downgrade the lock
                readLock.lock();
                try {
                    writeLock.unlock();
                    doWriteInternal();
                } finally {
                    readLock.unlock();
                }
            } finally {
                // Should have been released above but may not have been on some
                // exception paths
                if (writeLock.isHeldByCurrentThread()) {
                    writeLock.unlock();
                }
            }
        }


        private void doWriteInternal() throws IOException {
            int thisTime;

            ByteBuffer socketWriteBuffer = socketBufferHandler.getWriteBuffer();
            do {
                thisTime = 0;
                if (getEndpoint().isSSLEnabled()) {
                    if (sslOutputBuffer.remaining() == 0) {
                        // Buffer was fully written last time around
                        sslOutputBuffer.clear();
                        socketBufferHandler.configureWriteBufferForRead();
                        transfer(socketWriteBuffer, sslOutputBuffer);
                        sslOutputBuffer.flip();
                    } else {
                        // Buffer still has data from previous attempt to write
                        // APR + SSL requires that exactly the same parameters are
                        // passed when re-attempting the write
                    }
                    thisTime = Socket.sendb(getSocket().longValue(), sslOutputBuffer,
                            sslOutputBuffer.position(), sslOutputBuffer.limit());
                    if (thisTime > 0) {
                        sslOutputBuffer.position(sslOutputBuffer.position() + thisTime);
                    }
                } else {
                    socketBufferHandler.configureWriteBufferForRead();
                    thisTime = Socket.sendb(getSocket().longValue(), socketWriteBuffer,
                            socketWriteBuffer.position(), socketWriteBuffer.remaining());
                    if (thisTime > 0) {
                        socketWriteBuffer.position(socketWriteBuffer.position() + thisTime);
                    }
                }
                if (Status.APR_STATUS_IS_EAGAIN(-thisTime)) {
                    thisTime = 0;
                } else if (-thisTime == Status.APR_EOF) {
                    throw new EOFException(sm.getString("socket.apr.clientAbort"));
                } else if ((OS.IS_WIN32 || OS.IS_WIN64) &&
                        (-thisTime == Status.APR_OS_START_SYSERR + 10053)) {
                    // 10053 on Windows is connection aborted
                    throw new EOFException(sm.getString("socket.apr.clientAbort"));
                } else if (thisTime < 0) {
                    throw new IOException(sm.getString("socket.apr.write.error",
                            Integer.valueOf(-thisTime), getSocket(), this));
                }
            } while ((thisTime > 0 || getBlockingStatus()) && socketWriteBuffer.hasRemaining());

            // If there is data left in the buffer the socket will be registered for
            // write further up the stack. This is to ensure the socket is only
            // registered for write once as both container and user code can trigger
            // write registration.
        }


        @Override
        public boolean isReadPending() {
            return false;
        }


        @Override
        public void registerReadInterest() {
            // Make sure an already closed socket is not added to the poller
            synchronized (closedLock) {
                if (closed) {
                    return;
                }
                Poller p = ((AprEndpoint) getEndpoint()).getPoller();
                if (p != null) {
                    p.add(getSocket().longValue(), getReadTimeout(), Poll.APR_POLLIN);
                }
            }
        }


        @Override
        public void registerWriteInterest() {
            // Make sure an already closed socket is not added to the poller
            synchronized (closedLock) {
                if (closed) {
                    return;
                }
                ((AprEndpoint) getEndpoint()).getPoller().add(
                        getSocket().longValue(), getWriteTimeout(), Poll.APR_POLLOUT);
            }
        }


        @Override
        public SendfileDataBase createSendfileData(String filename, long pos, long length) {
            return new SendfileData(filename, pos, length);
        }


        @Override
        public SendfileState processSendfile(SendfileDataBase sendfileData) {
            ((SendfileData) sendfileData).socket = getSocket().longValue();
            return ((AprEndpoint) getEndpoint()).getSendfile().add((SendfileData) sendfileData);
        }


        @Override
        protected void populateRemoteAddr() {
            long socket = getSocket().longValue();
            if (socket == 0) {
                return;
            }
            try {
                long sa = Address.get(Socket.APR_REMOTE, socket);
                remoteAddr = Address.getip(sa);
            } catch (Exception e) {
                log.warn(sm.getString("endpoint.warn.noRemoteAddr", getSocket()), e);
            }
        }


        @Override
        protected void populateRemoteHost() {
            long socket = getSocket().longValue();
            if (socket == 0) {
                return;
            }
            try {
                long sa = Address.get(Socket.APR_REMOTE, socket);
                remoteHost = Address.getnameinfo(sa, 0);
                if (remoteAddr == null) {
                    remoteAddr = Address.getip(sa);
                }
            } catch (Exception e) {
                log.warn(sm.getString("endpoint.warn.noRemoteHost", getSocket()), e);
            }
        }


        @Override
        protected void populateRemotePort() {
            long socket = getSocket().longValue();
            if (socket == 0) {
                return;
            }
            try {
                long sa = Address.get(Socket.APR_REMOTE, socket);
                Sockaddr addr = Address.getInfo(sa);
                remotePort = addr.port;
            } catch (Exception e) {
                log.warn(sm.getString("endpoint.warn.noRemotePort", getSocket()), e);
            }
        }


        @Override
        protected void populateLocalName() {
            long socket = getSocket().longValue();
            if (socket == 0) {
                return;
            }
            try {
                long sa = Address.get(Socket.APR_LOCAL, socket);
                localName =Address.getnameinfo(sa, 0);
            } catch (Exception e) {
                log.warn(sm.getString("endpoint.warn.noLocalName"), e);
            }
        }


        @Override
        protected void populateLocalAddr() {
            long socket = getSocket().longValue();
            if (socket == 0) {
                return;
            }
            try {
                long sa = Address.get(Socket.APR_LOCAL, socket);
                localAddr = Address.getip(sa);
            } catch (Exception e) {
                log.warn(sm.getString("endpoint.warn.noLocalAddr"), e);
            }
        }


        @Override
        protected void populateLocalPort() {
            long socket = getSocket().longValue();
            if (socket == 0) {
                return;
            }
            try {
                long sa = Address.get(Socket.APR_LOCAL, socket);
                Sockaddr addr = Address.getInfo(sa);
                localPort = addr.port;
            } catch (Exception e) {
                log.warn(sm.getString("endpoint.warn.noLocalPort"), e);
            }
        }


        @Override
        public SSLSupport getSslSupport(String clientCertProvider) {
            if (getEndpoint().isSSLEnabled()) {
                return new  AprSSLSupport(this, clientCertProvider);
            } else {
                return null;
            }
        }


        @Override
        public void doClientAuth(SSLSupport sslSupport) {
            long socket = getSocket().longValue();
            // Configure connection to require a certificate
            SSLSocket.setVerify(socket, SSL.SSL_CVERIFY_REQUIRE, -1);
            SSLSocket.renegotiate(socket);
        }


        @Override
        public boolean isWritePending() {
            return false;
        }


        @Override
        public <A> CompletionState read(ByteBuffer[] dsts, int offset,
                int length, boolean block, long timeout, TimeUnit unit,
                A attachment, CompletionCheck check,
                CompletionHandler<Long, ? super A> handler) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException();
        }

        @Override
        public <A> CompletionState write(ByteBuffer[] srcs, int offset,
                int length, boolean block, long timeout, TimeUnit unit,
                A attachment, CompletionCheck check,
                CompletionHandler<Long, ? super A> handler) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException();
        }
    }
}
