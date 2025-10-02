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
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import javax.net.ssl.KeyManager;

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
import org.apache.tomcat.util.collections.SynchronizedStack;
import org.apache.tomcat.util.compat.JrePlatform;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.Acceptor.AcceptorState;
import org.apache.tomcat.util.net.openssl.OpenSSLContext;
import org.apache.tomcat.util.net.openssl.OpenSSLUtil;


/**
 * APR tailored thread pool, providing the following services:
 * <ul>
 * <li>Socket acceptor thread</li>
 * <li>Socket poller thread</li>
 * <li>Sendfile thread</li>
 * <li>Worker threads pool</li>
 * </ul>
 *
 * @deprecated The APR/Native Connector will be removed in Tomcat 9.1.x onwards and has been removed from Tomcat 10.1.x
 *                 onwards.
 */
@Deprecated
public class AprEndpoint extends AbstractEndpoint<Long,Long> implements SNICallBack {

    // -------------------------------------------------------------- Constants

    private static final Log log = LogFactory.getLog(AprEndpoint.class);
    private static final Log logCertificate = LogFactory.getLog(AprEndpoint.class.getName() + ".certificate");

    // ----------------------------------------------------------------- Fields

    /**
     * Root APR memory pool.
     */
    protected long rootPool = 0;


    /**
     * Server socket "pointer".
     */
    protected volatile long serverSock = 0;


    /**
     * APR memory pool for the server socket.
     */
    protected long serverSockPool = 0;


    /**
     * Default SSL context. SNI callback may select a different SSL context.
     */
    protected volatile long sslContext = 0;


    private int previousAcceptedPort = -1;
    private String previousAcceptedAddress = null;
    private long previousAcceptedSocketNanoTime = 0;


    // ------------------------------------------------------------ Constructor

    public AprEndpoint() {
        // Asynchronous IO has significantly lower performance with APR:
        // - no IO vectoring
        // - mandatory use of direct buffers forces output buffering
        // - needs extra output flushes due to buffering
        setUseAsyncIO(false);
    }


    // ------------------------------------------------------------- Properties

    /**
     * Defer accept.
     */
    protected boolean deferAccept = true;

    public void setDeferAccept(boolean deferAccept) {
        this.deferAccept = deferAccept;
    }

    @Override
    public boolean getDeferAccept() {
        return deferAccept;
    }


    private boolean ipv6v6only = false;

    public void setIpv6v6only(boolean ipv6v6only) {
        this.ipv6v6only = ipv6v6only;
    }

    public boolean getIpv6v6only() {
        return ipv6v6only;
    }


    /**
     * Size of the sendfile (= concurrent files which can be served).
     */
    protected int sendfileSize = 1 * 1024;

    public void setSendfileSize(int sendfileSize) {
        this.sendfileSize = sendfileSize;
    }

    public int getSendfileSize() {
        return sendfileSize;
    }


    /**
     * Poll interval, in microseconds. The smaller the value, the more CPU the poller will use, but the more responsive
     * to activity it will be.
     */
    protected int pollTime = 2000;

    public int getPollTime() {
        return pollTime;
    }

    public void setPollTime(int pollTime) {
        if (pollTime > 0) {
            this.pollTime = pollTime;
        }
    }


    /*
     * When the endpoint is created and configured, the APR library will not have been initialised. This flag is used to
     * determine if the default value of useSendFile should be changed if the APR library indicates it supports send
     * file once it has been initialised. If useSendFile is set by configuration, that configuration will always take
     * priority.
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
    public InetSocketAddress getLocalAddress() throws IOException {
        long s = serverSock;
        if (s == 0) {
            return null;
        } else {
            long sa;
            try {
                sa = Address.get(Socket.APR_LOCAL, s);
            } catch (IOException ioe) {
                // re-throw
                throw ioe;
            } catch (Exception e) {
                // wrap
                throw new IOException(e);
            }
            Sockaddr addr = Address.getInfo(sa);
            if (addr.hostname == null) {
                // any local address
                if (addr.family == Socket.APR_INET6) {
                    return new InetSocketAddress("::", addr.port);
                } else {
                    return new InetSocketAddress("0.0.0.0", addr.port);
                }
            }
            return new InetSocketAddress(addr.hostname, addr.port);
        }
    }


    /**
     * This endpoint does not support <code>-1</code> for unlimited connections, nor does it support setting this
     * attribute while the endpoint is running. {@inheritDoc}
     */
    @Override
    public void setMaxConnections(int maxConnections) {
        if (maxConnections == -1) {
            log.warn(sm.getString("endpoint.apr.maxConnections.unlimited", Integer.valueOf(getMaxConnections())));
            return;
        }
        if (running) {
            log.warn(sm.getString("endpoint.apr.maxConnections.running", Integer.valueOf(getMaxConnections())));
            return;
        }
        super.setMaxConnections(maxConnections);
    }


    /**
     * Path for the Unix Domain Socket, used to create the socket address.
     */
    private String unixDomainSocketPath = null;

    public String getUnixDomainSocketPath() {
        return this.unixDomainSocketPath;
    }

    public void setUnixDomainSocketPath(String unixDomainSocketPath) {
        this.unixDomainSocketPath = unixDomainSocketPath;
    }


    /**
     * Permissions which will be set on the Unix Domain Socket if it is created.
     */
    private String unixDomainSocketPathPermissions = null;

    public String getUnixDomainSocketPathPermissions() {
        return this.unixDomainSocketPathPermissions;
    }

    public void setUnixDomainSocketPathPermissions(String unixDomainSocketPathPermissions) {
        this.unixDomainSocketPathPermissions = unixDomainSocketPathPermissions;
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


    @Override
    public String getId() {
        if (getUnixDomainSocketPath() != null) {
            return getUnixDomainSocketPath();
        } else {
            return null;
        }
    }


    // ----------------------------------------------- Public Lifecycle Methods


    /**
     * Initialize the endpoint.
     */
    @Override
    public void bind() throws Exception {

        int family;
        String hostname = null;

        // Create the root APR memory pool
        try {
            rootPool = Pool.create(0);
        } catch (UnsatisfiedLinkError e) {
            throw new Exception(sm.getString("endpoint.init.notavail"));
        }

        // Create the pool for the server socket
        serverSockPool = Pool.create(rootPool);

        // Create the APR address that will be bound
        if (getUnixDomainSocketPath() != null) {
            if (Library.APR_HAVE_UNIX) {
                hostname = getUnixDomainSocketPath();
                family = Socket.APR_UNIX;
            } else {
                throw new Exception(sm.getString("endpoint.init.unixnotavail"));
            }
        } else {

            if (getAddress() != null) {
                hostname = getAddress().getHostAddress();
            }
            family = Socket.APR_UNSPEC;
        }

        long sockAddress = Address.info(hostname, family, getPortWithOffset(), 0, rootPool);

        // Create the APR server socket
        if (family == Socket.APR_UNIX) {
            serverSock = Socket.create(family, Socket.SOCK_STREAM, 0, rootPool);
        } else {
            int saFamily = Address.getInfo(sockAddress).family;
            serverSock = Socket.create(saFamily, Socket.SOCK_STREAM, Socket.APR_PROTO_TCP, rootPool);
            if (OS.IS_UNIX) {
                Socket.optSet(serverSock, Socket.APR_SO_REUSEADDR, 1);
            }
            if (Library.APR_HAVE_IPV6 && saFamily == Socket.APR_INET6) {
                if (getIpv6v6only()) {
                    Socket.optSet(serverSock, Socket.APR_IPV6_V6ONLY, 1);
                } else {
                    Socket.optSet(serverSock, Socket.APR_IPV6_V6ONLY, 0);
                }
            }
            // Deal with the firewalls that tend to drop the inactive sockets
            Socket.optSet(serverSock, Socket.APR_SO_KEEPALIVE, 1);
        }

        // Bind the server socket
        int ret = Socket.bind(serverSock, sockAddress);
        if (ret != 0) {
            throw new Exception(sm.getString("endpoint.init.bind", "" + ret, Error.strerror(ret)));
        }

        // Start listening on the server socket
        ret = Socket.listen(serverSock, getAcceptCount());
        if (ret != 0) {
            throw new Exception(sm.getString("endpoint.init.listen", "" + ret, Error.strerror(ret)));
        }

        if (family == Socket.APR_UNIX) {
            if (getUnixDomainSocketPathPermissions() != null) {
                FileAttribute<Set<PosixFilePermission>> attrs = PosixFilePermissions
                        .asFileAttribute(PosixFilePermissions.fromString(getUnixDomainSocketPathPermissions()));
                Path path = Paths.get(getUnixDomainSocketPath());
                Files.setAttribute(path, attrs.name(), attrs.value());
            }
        } else {
            if (OS.IS_WIN32 || OS.IS_WIN64) {
                // On Windows set the reuseaddr flag after the bind/listen
                Socket.optSet(serverSock, Socket.APR_SO_REUSEADDR, 1);
            }
        }

        // Enable Sendfile by default if it has not been configured but usage on
        // systems which don't support it cause major problems
        if (!useSendFileSet) {
            setUseSendfileInternal(Library.APR_HAS_SENDFILE);
        } else if (getUseSendfile() && !Library.APR_HAS_SENDFILE) {
            setUseSendfileInternal(false);
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
                createSSLContext(sslHostConfig);
            }
            SSLHostConfig defaultSSLHostConfig = sslHostConfigs.get(getDefaultSSLHostConfigName());
            if (defaultSSLHostConfig == null) {
                throw new IllegalArgumentException(
                        sm.getString("endpoint.noSslHostConfig", getDefaultSSLHostConfigName(), getName()));
            }
            setDefaultSslHostConfig(defaultSSLHostConfig);

            // For now, sendfile is not supported with SSL
            if (getUseSendfile()) {
                setUseSendfileInternal(false);
                if (useSendFileSet) {
                    log.warn(sm.getString("endpoint.apr.noSendfileWithSSL"));
                }
            }
        }
    }


    @Override
    protected void createSSLContext(SSLHostConfig sslHostConfig) throws Exception {
        OpenSSLContext sslContext = null;
        Set<SSLHostConfigCertificate> certificates = sslHostConfig.getCertificates(true);
        for (SSLHostConfigCertificate certificate : certificates) {
            if (sslContext == null) {
                SSLUtil sslUtil = new OpenSSLUtil(certificate);
                sslHostConfig.setEnabledProtocols(sslUtil.getEnabledProtocols());
                sslHostConfig.setEnabledCiphers(sslUtil.getEnabledCiphers());

                try {
                    sslContext = (OpenSSLContext) sslUtil.createSSLContext(negotiableProtocols);
                } catch (Exception e) {
                    throw new IllegalArgumentException(e.getMessage(), e);
                }
                try {
                    KeyManager[] kms = sslUtil.getKeyManagers();
                    certificate.setCertificateKeyManager(OpenSSLUtil.chooseKeyManager(kms));
                } catch (Exception e) {
                    log.debug(sm.getString("endpoint.apr.keyManagerError"), e);
                }
            } else {
                SSLUtil sslUtil = new OpenSSLUtil(certificate);
                KeyManager[] kms = sslUtil.getKeyManagers();
                certificate.setCertificateKeyManager(OpenSSLUtil.chooseKeyManager(kms));
                sslContext.addCertificate(certificate);
            }

            certificate.setSslContextGenerated(sslContext);
            logCertificate(certificate);
        }

        if (certificates.size() > 2) {
            // TODO: Can this limitation be removed?
            throw new Exception(sm.getString("endpoint.apr.tooManyCertFiles"));
        }
    }


    @Override
    public long getSslContext(String sniHostName) {
        SSLHostConfig sslHostConfig = getSSLHostConfig(sniHostName);
        Long ctx = sslHostConfig.getOpenSslContext();
        if (ctx != null) {
            return ctx.longValue();
        }
        // Default
        return 0;
    }


    @Override
    protected void setDefaultSslHostConfig(SSLHostConfig sslHostConfig) {
        Long ctx = sslHostConfig.getOpenSslContext();
        sslContext = ctx.longValue();
        SSLContext.registerDefault(ctx, this);
    }


    @Override
    public boolean isAlpnSupported() {
        // The APR/native connector always supports ALPN if TLS is in use
        // because OpenSSL supports ALPN. Therefore, this is equivalent to
        // testing of SSL is enabled.
        return isSSLEnabled();
    }


    /**
     * Start the APR endpoint, creating acceptor, poller and sendfile threads.
     */
    @Override
    public void startInternal() throws Exception {

        if (!running) {
            running = true;
            paused = false;

            if (socketProperties.getProcessorCache() != 0) {
                processorCache =
                        new SynchronizedStack<>(SynchronizedStack.DEFAULT_SIZE, socketProperties.getProcessorCache());
            }

            // Create worker collection
            if (getExecutor() == null) {
                createExecutor();
            }

            initializeConnectionLatch();

            // Start poller thread
            poller = new Poller();
            poller.init();
            poller.start();

            // Start sendfile thread
            if (getUseSendfile()) {
                sendfile = new Sendfile();
                sendfile.init();
                sendfile.start();
            }

            startAcceptorThread();
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
            // Stop new connections being accepted.
            acceptor.stop(10);

            // Stop the Poller calling select
            poller.stop();

            if (getUseSendfile()) {
                sendfile.stop();
            }

            // Wait for the acceptor to shutdown
            if (acceptor.getState() != AcceptorState.ENDED && !getBindOnInit()) {
                log.warn(sm.getString("endpoint.warn.unlockAcceptorFailed", acceptor.getThreadName()));
                // If the Acceptor is still running force
                // the hard socket close.
                if (serverSock != 0) {
                    Socket.shutdown(serverSock, Socket.APR_SHUTDOWN_READ);
                    serverSock = 0;
                }
            }

            // Wait for Poller to stop
            int waitMillis = 0;
            try {
                while (poller.pollerThread.isAlive() && waitMillis < 10000) {
                    waitMillis++;
                    Thread.sleep(1);
                }
            } catch (InterruptedException e) {
                // Ignore
            }

            if (getUseSendfile()) {
                try {
                    // Wait for the sendfile thread to exit, otherwise parallel
                    // destruction of sockets which are still in the poller can cause
                    // problems.
                    waitMillis = 0;
                    try {
                        while (sendfile.sendfileThread.isAlive() && waitMillis < 10000) {
                            waitMillis++;
                            Thread.sleep(1);
                        }
                    } catch (InterruptedException e) {
                        // Ignore
                    }

                    if (sendfile.sendfileThread.isAlive()) {
                        log.warn(sm.getString("endpoint.sendfileThreadStop"));
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }

            // Close the SocketWrapper for each open connection - this should
            // trigger a IOException when the app (or container) tries to write.
            for (SocketWrapperBase<Long> socketWrapper : connections.values()) {
                socketWrapper.close();
            }

            for (Long socket : connections.keySet()) {
                // Close the APR Socket. Need to do this before destroying the
                // poller since that will also destroy the root pool for these
                // sockets.
                Socket.shutdown(socket.longValue(), Socket.APR_SHUTDOWN_READWRITE);
            }

            if (getUseSendfile()) {
                try {
                    sendfile.destroy();
                } catch (Exception e) {
                    // Ignore
                }
                sendfile = null;
            }

            try {
                poller.destroy();
            } catch (Exception e) {
                // Ignore
            }
            poller = null;
            connections.clear();
            if (processorCache != null) {
                processorCache.clear();
                processorCache = null;
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

        doCloseServerSocket();
        destroySsl();

        // Close all APR memory pools and resources if initialised
        if (rootPool != 0) {
            Pool.destroy(rootPool);
            rootPool = 0;
        }

        getHandler().recycle();
    }


    @Override
    protected void doCloseServerSocket() {
        // Close server socket if it was initialised
        if (serverSock != 0) {
            Socket.close(serverSock);
            serverSock = 0;
        }
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * Process the specified connection.
     *
     * @param socketWrapper The socket wrapper
     *
     * @return <code>true</code> if the socket was correctly configured and processing may continue, <code>false</code>
     *             if the socket needs to be close immediately
     */
    protected boolean setSocketOptions(SocketWrapperBase<Long> socketWrapper) {
        long socket = socketWrapper.getSocket().longValue();
        // Process the connection
        int step = 1;
        try {

            // 1: Set socket options: timeout, linger, etc
            if (socketProperties.getSoLingerOn() && socketProperties.getSoLingerTime() >= 0) {
                Socket.optSet(socket, Socket.APR_SO_LINGER, socketProperties.getSoLingerTime());
            }
            if (socketProperties.getTcpNoDelay()) {
                Socket.optSet(socket, Socket.APR_TCP_NODELAY, (socketProperties.getTcpNoDelay() ? 1 : 0));
            }
            Socket.timeoutSet(socket, socketProperties.getSoTimeout() * 1000);

            // 2: SSL handshake
            step = 2;
            if (sslContext != 0) {
                int rv = SSLSocket.attach(sslContext, socket);
                if (rv != Status.APR_SUCCESS) {
                    log.warn(sm.getString("endpoint.err.attach", Integer.valueOf(rv)));
                    return false;
                }

                // Need to make sure the socket doesn't get closed while the
                // handshake is in progress as that could trigger a JVM crash.
                // Like stopInternal(), use the blocking status write lock as a
                // proxy for a lock on writing to the socket.
                WriteLock wl = ((AprSocketWrapper) socketWrapper).getBlockingStatusWriteLock();
                wl.lock();
                try {
                    if (SSLSocket.handshake(socket) != 0) {
                        if (log.isDebugEnabled()) {
                            log.debug(sm.getString("endpoint.err.handshake") + ": " + SSL.getLastError());
                        }
                        return false;
                    }
                } finally {
                    wl.unlock();
                }

                if (negotiableProtocols.size() > 0) {
                    byte[] negotiated = new byte[256];
                    int len = SSLSocket.getALPN(socket, negotiated);
                    String negotiatedProtocol = new String(negotiated, 0, len, StandardCharsets.UTF_8);
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
     *
     * @param size    The size
     * @param pool    The pool from which the poller will be allocated
     * @param timeout The timeout
     *
     * @return the poller pointer
     */
    protected long allocatePoller(int size, long pool, int timeout) {
        try {
            return Poll.create(size, pool, 0, timeout * 1000);
        } catch (Error e) {
            if (Status.APR_STATUS_IS_EINVAL(e.getError())) {
                log.info(sm.getString("endpoint.poll.limitedpollsize", "" + size));
                throw new RuntimeException(e);
            } else {
                log.error(sm.getString("endpoint.poll.initfail"), e);
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Process given socket. This is called when the socket has been accepted.
     *
     * @param socket The socket
     *
     * @return <code>true</code> if the socket was correctly configured and processing may continue, <code>false</code>
     *             if the socket needs to be close immediately
     */
    @Override
    protected boolean setSocketOptions(Long socket) {
        try {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("endpoint.debug.socket", socket));
            }

            // Do the duplicate accept check here rather than in serverSocketaccept()
            // so we can cache the results in the SocketWrapper
            AprSocketWrapper wrapper = new AprSocketWrapper(socket, this);
            // Bug does not affect Windows platform and Unix Domain Socket. Skip the check.
            if (!JrePlatform.IS_WINDOWS && getUnixDomainSocketPath() == null) {
                long currentNanoTime = System.nanoTime();
                if (wrapper.getRemotePort() == previousAcceptedPort) {
                    if (wrapper.getRemoteAddr().equals(previousAcceptedAddress)) {
                        if (currentNanoTime - previousAcceptedSocketNanoTime < 1000) {
                            throw new IOException(sm.getString("endpoint.err.duplicateAccept"));
                        }
                    }
                }
                previousAcceptedPort = wrapper.getRemotePort();
                previousAcceptedAddress = wrapper.getRemoteAddr();
                previousAcceptedSocketNanoTime = currentNanoTime;
            }

            connections.put(socket, wrapper);
            wrapper.setKeepAliveLeft(getMaxKeepAliveRequests());
            wrapper.setReadTimeout(getConnectionTimeout());
            wrapper.setWriteTimeout(getConnectionTimeout());
            getExecutor().execute(new SocketWithOptionsProcessor(wrapper));
            return true;
        } catch (RejectedExecutionException x) {
            log.warn(sm.getString("endpoint.rejectedExecution", socket), x);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // This means we got an OOM or similar creating a thread, or that
            // the pool and its queue are full
            log.error(sm.getString("endpoint.process.fail"), t);
        }
        return false;
    }


    @Override
    protected Long serverSocketAccept() throws Exception {
        // See setSocketOptions(Long) for duplicate accept check
        long socket = Socket.accept(serverSock);
        if (socket == 0) {
            throw new IOException(sm.getString("endpoint.err.accept", getName()));
        }
        if (log.isDebugEnabled()) {
            long sa = Address.get(Socket.APR_REMOTE, socket);
            Sockaddr addr = Address.getInfo(sa);
            log.debug(sm.getString("endpoint.apr.remoteport", Long.valueOf(socket), Long.valueOf(addr.port)));
        }
        return Long.valueOf(socket);
    }


    /**
     * Process the given socket. Typically keep alive or upgraded protocol.
     *
     * @param socket The socket to process
     * @param event  The event to process
     *
     * @return <code>true</code> if the processing completed normally otherwise <code>false</code> which indicates an
     *             error occurred and that the socket should be closed
     */
    protected boolean processSocket(long socket, SocketEvent event) {
        SocketWrapperBase<Long> socketWrapper = connections.get(Long.valueOf(socket));
        if (socketWrapper == null) {
            // Socket probably closed from another thread. Triggering another
            // close in case won't cause an issue.
            return false;
        }
        if (event == SocketEvent.OPEN_READ && socketWrapper.readOperation != null) {
            return socketWrapper.readOperation.process();
        } else if (event == SocketEvent.OPEN_WRITE && socketWrapper.writeOperation != null) {
            return socketWrapper.writeOperation.process();
        } else {
            return processSocket(socketWrapper, event, true);
        }
    }


    @Override
    protected SocketProcessorBase<Long> createSocketProcessor(SocketWrapperBase<Long> socketWrapper,
            SocketEvent event) {
        return new SocketProcessor(socketWrapper, event);
    }


    private void closeSocketInternal(long socket) {
        closeSocket(Long.valueOf(socket));
    }

    @Override
    protected void destroySocket(Long socket) {
        countDownConnection();
        destroySocketInternal(socket.longValue());
    }

    private void destroySocketInternal(long socket) {
        if (log.isDebugEnabled()) {
            String msg = sm.getString("endpoint.debug.destroySocket", Long.valueOf(socket));
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
        }
    }

    @Override
    protected Log getLog() {
        return log;
    }

    @Override
    protected Log getLogCertificate() {
        return logCertificate;
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
            return ((flag1 & Poll.APR_POLLIN) | (flag2 & Poll.APR_POLLIN)) |
                    ((flag1 & Poll.APR_POLLOUT) | (flag2 & Poll.APR_POLLOUT));
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

    public static class SocketTimeouts {
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
         * @return The configured timeout for the socket or zero if the socket was not in the list of socket timeouts
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

    public static class SocketList {
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
                    flags[size] = flags[size - 1];
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
         * Pointer to the poller.
         */
        private long aprPoller;

        /**
         * Actual poller size.
         */
        private int pollerSize = 0;

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
        private SocketList addList = null; // Modifications guarded by this


        /**
         * List of sockets to be closed.
         */
        private SocketList closeList = null; // Modifications guarded by this


        /**
         * Structure used for storing timeouts.
         */
        private SocketTimeouts timeouts = null;


        /**
         * Last run of maintain. Maintain will run approximately once every one second (may be slightly longer between
         * runs).
         */
        private long lastMaintain = System.currentTimeMillis();


        /**
         * The number of connections currently inside this Poller. The correct operation of the Poller depends on this
         * figure being correct. If it is not, it is possible that the Poller will enter a wait loop where it waits for
         * the next connection to be added to the Poller before it calls poll when it should still be polling existing
         * connections. Although not necessary at the time of writing this comment, it has been implemented as an
         * AtomicInteger to ensure that it remains thread-safe.
         */
        private AtomicInteger connectionCount = new AtomicInteger(0);

        public int getConnectionCount() {
            return connectionCount.get();
        }

        private volatile Thread pollerThread;
        private volatile boolean pollerRunning = true;

        /**
         * Create the poller.
         */
        protected synchronized void init() {

            pool = Pool.create(serverSockPool);
            pollerSize = getMaxConnections();
            timeouts = new SocketTimeouts(pollerSize);

            // At the moment, setting the timeout is useless, but it could get
            // used again as the normal poller could be faster using maintain.
            // It might not be worth bothering though.
            aprPoller = allocatePoller(pollerSize, pool, -1);

            /*
             * x2 - One descriptor for the socket, one for the event(s). x2 - Some APR implementations return multiple
             * events for the same socket as different entries. Each socket is registered for a maximum of two events
             * (read and write) at any one time.
             *
             * Therefore size is poller size *4.
             */
            desc = new long[pollerSize * 4];
            connectionCount.set(0);
            addList = new SocketList(pollerSize);
            closeList = new SocketList(pollerSize);
        }


        protected void start() {
            pollerThread = new Thread(poller, getName() + "-Poller");
            pollerThread.setPriority(threadPriority);
            pollerThread.setDaemon(true);
            pollerThread.start();
        }


        /*
         * This method is synchronized so that it is not possible for a socket to be added to the Poller's addList once
         * this method has completed.
         */
        protected synchronized void stop() {
            pollerRunning = false;
            // In case the poller thread is in the idle wait
            this.notify();
        }


        /**
         * Destroy the poller.
         */
        protected synchronized void destroy() {
            // Wait for the poller thread to exit, otherwise parallel
            // destruction of sockets which are still in the poller can cause
            // problems.
            int loops = 50;
            while (loops > 0 && pollerThread.isAlive()) {
                try {
                    this.wait(pollTime / 1000);
                } catch (InterruptedException e) {
                    // Ignore
                }
                loops--;
            }
            if (pollerThread.isAlive()) {
                log.warn(sm.getString("endpoint.pollerThreadStop"));
            }
            // Close all sockets in the close queue
            SocketInfo info = closeList.get();
            while (info != null) {
                // Make sure we aren't trying add the socket as well as close it
                addList.remove(info.socket);
                // Make sure the socket isn't in the poller before we close it
                removeFromPoller(info.socket);
                // Poller isn't running at this point so use destroySocket()
                // directly
                closeSocketInternal(info.socket);
                destroySocketInternal(info.socket);
                info = closeList.get();
            }
            closeList.clear();
            // Close all sockets in the add queue
            info = addList.get();
            while (info != null) {
                // Make sure the socket isn't in the poller before we close it
                removeFromPoller(info.socket);
                // Poller isn't running at this point so use destroySocket()
                // directly
                closeSocketInternal(info.socket);
                destroySocketInternal(info.socket);
                info = addList.get();
            }
            addList.clear();
            // Close all sockets still in the poller
            int rv = Poll.pollset(aprPoller, desc);
            if (rv > 0) {
                for (int n = 0; n < rv; n++) {
                    closeSocketInternal(desc[n * 2 + 1]);
                    destroySocketInternal(desc[n * 2 + 1]);
                }
            }
            Pool.destroy(pool);
            connectionCount.set(0);
        }


        /**
         * Add specified socket and associated pool to the poller. The socket will be added to a temporary array, and
         * polled first after a maximum amount of time equal to pollTime (in most cases, latency will be much lower,
         * however). Note: If both read and write are false, the socket will only be checked for timeout; if the socket
         * was already present in the poller, a callback event will be generated and the socket will be removed from the
         * poller.
         *
         * @param socket  to add to the poller
         * @param timeout to use for this connection in milliseconds
         * @param flags   Events to poll for (Poll.APR_POLLIN and/or Poll.APR_POLLOUT)
         */
        private void add(long socket, long timeout, int flags) {
            if (log.isDebugEnabled()) {
                String msg = sm.getString("endpoint.debug.pollerAdd", Long.valueOf(socket), Long.valueOf(timeout),
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
                    // In case the poller thread is in the idle wait
                    this.notify();
                }
            }
        }


        /**
         * Add specified socket to one of the pollers. Must only be called from {@link Poller#run()}.
         */
        private boolean addToPoller(long socket, int events) {
            int rv = Poll.add(aprPoller, socket, events);
            if (rv == Status.APR_SUCCESS) {
                connectionCount.incrementAndGet();
                return true;
            }
            return false;
        }


        /*
         * This is only called from the SocketWrapper to ensure that it is only called once per socket. Calling it more
         * than once typically results in the JVM crash.
         */
        private synchronized void close(long socket) {
            closeList.add(socket, 0, 0);
            // In case the poller thread is in the idle wait
            this.notify();
        }


        /**
         * Remove specified socket from the pollers. Must only be called from {@link Poller#run()}.
         */
        private void removeFromPoller(long socket) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("endpoint.debug.pollerRemove", Long.valueOf(socket)));
            }
            int rv = Poll.remove(aprPoller, socket);
            if (rv != Status.APR_NOTFOUND) {
                connectionCount.decrementAndGet();
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("endpoint.debug.pollerRemoved", Long.valueOf(socket)));
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
                    log.debug(sm.getString("endpoint.debug.socketTimeout", Long.valueOf(socket)));
                }
                SocketWrapperBase<Long> socketWrapper = connections.get(Long.valueOf(socket));
                if (socketWrapper != null) {
                    socketWrapper.setError(new SocketTimeoutException());
                    if (socketWrapper.readOperation != null || socketWrapper.writeOperation != null) {
                        if (socketWrapper.readOperation != null) {
                            socketWrapper.readOperation.process();
                        } else {
                            socketWrapper.writeOperation.process();
                        }
                    } else {
                        processSocket(socketWrapper, SocketEvent.ERROR, true);
                    }
                }
                socket = timeouts.check(date);
            }

        }

        /**
         * Displays the list of sockets in the pollers.
         */
        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("Poller");
            long[] res = new long[pollerSize * 2];
            int count = Poll.pollset(aprPoller, res);
            buf.append(" [ ");
            for (int j = 0; j < count; j++) {
                buf.append(desc[2 * j + 1]).append(' ');
            }
            buf.append(']');
            return buf.toString();
        }

        /**
         * The background thread that adds sockets to the Poller, checks the poller for triggered events and hands the
         * associated socket off to an appropriate processor as events occur.
         */
        @Override
        public void run() {

            SocketList localAddList = new SocketList(getMaxConnections());
            SocketList localCloseList = new SocketList(getMaxConnections());

            // Loop until we receive a shutdown command
            while (pollerRunning) {

                // Check timeouts if the poller is empty.
                while (pollerRunning && connectionCount.get() < 1 && addList.size() < 1 && closeList.size() < 1) {
                    try {
                        if (getConnectionTimeout() > 0 && pollerRunning) {
                            maintain();
                        }
                        synchronized (this) {
                            // Make sure that no sockets have been placed in the
                            // addList or closeList since the check above.
                            // Without this check there could be a 10s pause
                            // with no processing since the notify() call in
                            // add()/close() would have no effect since it
                            // happened before this sync block was entered
                            if (pollerRunning && addList.size() < 1 && closeList.size() < 1) {
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
                            closeSocketInternal(info.socket);
                            destroySocketInternal(info.socket);
                            info = localCloseList.get();
                        }
                    }

                    // Add sockets which are waiting to the poller
                    if (localAddList.size() > 0) {
                        SocketInfo info = localAddList.get();
                        while (info != null) {
                            if (log.isDebugEnabled()) {
                                log.debug(sm.getString("endpoint.debug.pollerAddDo", Long.valueOf(info.socket)));
                            }
                            timeouts.remove(info.socket);
                            AprSocketWrapper wrapper = (AprSocketWrapper) connections.get(Long.valueOf(info.socket));
                            if (wrapper != null) {
                                if (info.read() || info.write()) {
                                    wrapper.pollerFlags = wrapper.pollerFlags | (info.read() ? Poll.APR_POLLIN : 0) |
                                            (info.write() ? Poll.APR_POLLOUT : 0);
                                    // A socket can only be added to the poller
                                    // once. Adding it twice will return an error
                                    // which will close the socket. Therefore make
                                    // sure the socket we are about to add isn't in
                                    // the poller.
                                    removeFromPoller(info.socket);
                                    if (!addToPoller(info.socket, wrapper.pollerFlags)) {
                                        wrapper.close();
                                    } else {
                                        timeouts.add(info.socket, System.currentTimeMillis() + info.timeout);
                                    }
                                } else {
                                    // Should never happen.
                                    wrapper.close();
                                    getLog().warn(sm.getString("endpoint.apr.pollAddInvalid", info));
                                }
                            }
                            info = localAddList.get();
                        }
                    }

                    // Flag to ask to reallocate the pool
                    boolean reset = false;

                    int rv = Poll.poll(aprPoller, pollTime, desc, true);
                    if (rv > 0) {
                        rv = mergeDescriptors(desc, rv);
                        connectionCount.addAndGet(-rv);
                        for (int n = 0; n < rv; n++) {
                            if (getLog().isDebugEnabled()) {
                                log.debug(sm.getString("endpoint.debug.pollerProcess", Long.valueOf(desc[n * 2 + 1]),
                                        Long.valueOf(desc[n * 2])));
                            }
                            long timeout = timeouts.remove(desc[n * 2 + 1]);
                            AprSocketWrapper wrapper =
                                    (AprSocketWrapper) connections.get(Long.valueOf(desc[n * 2 + 1]));
                            if (wrapper == null) {
                                // Socket was closed in another thread while still in
                                // the Poller but wasn't removed from the Poller before
                                // new data arrived.
                                continue;
                            }
                            wrapper.pollerFlags = wrapper.pollerFlags & ~((int) desc[n * 2]);
                            // Check for failed sockets and hand this socket off to a worker
                            if (((desc[n * 2] & Poll.APR_POLLHUP) == Poll.APR_POLLHUP) ||
                                    ((desc[n * 2] & Poll.APR_POLLERR) == Poll.APR_POLLERR) ||
                                    ((desc[n * 2] & Poll.APR_POLLNVAL) == Poll.APR_POLLNVAL)) {
                                // Need to trigger error handling. Poller may return error
                                // codes plus the flags it was waiting for or it may just
                                // return an error code. We could handle the error here but
                                // if we do, there will be no exception associated with the
                                // error in application code. By signalling read/write is
                                // possible, a read/write will be attempted, fail and that
                                // will trigger an exception the application will see.
                                // Check the return flags first, followed by what the socket
                                // was registered for
                                if ((desc[n * 2] & Poll.APR_POLLIN) == Poll.APR_POLLIN) {
                                    // Error probably occurred during a non-blocking read
                                    if (!processSocket(desc[n * 2 + 1], SocketEvent.OPEN_READ)) {
                                        // Close socket and clear pool
                                        wrapper.close();
                                    }
                                } else if ((desc[n * 2] & Poll.APR_POLLOUT) == Poll.APR_POLLOUT) {
                                    // Error probably occurred during a non-blocking write
                                    if (!processSocket(desc[n * 2 + 1], SocketEvent.OPEN_WRITE)) {
                                        // Close socket and clear pool
                                        wrapper.close();
                                    }
                                } else if ((wrapper.pollerFlags & Poll.APR_POLLIN) == Poll.APR_POLLIN) {
                                    // Can't tell what was happening when the error occurred but the
                                    // socket is registered for non-blocking read so use that
                                    if (!processSocket(desc[n * 2 + 1], SocketEvent.OPEN_READ)) {
                                        // Close socket and clear pool
                                        wrapper.close();
                                    }
                                } else if ((wrapper.pollerFlags & Poll.APR_POLLOUT) == Poll.APR_POLLOUT) {
                                    // Can't tell what was happening when the error occurred but the
                                    // socket is registered for non-blocking write so use that
                                    if (!processSocket(desc[n * 2 + 1], SocketEvent.OPEN_WRITE)) {
                                        // Close socket and clear pool
                                        wrapper.close();
                                    }
                                } else {
                                    // Close socket and clear pool
                                    wrapper.close();
                                }
                            } else if (((desc[n * 2] & Poll.APR_POLLIN) == Poll.APR_POLLIN) ||
                                    ((desc[n * 2] & Poll.APR_POLLOUT) == Poll.APR_POLLOUT)) {
                                boolean error = false;
                                if (((desc[n * 2] & Poll.APR_POLLIN) == Poll.APR_POLLIN) &&
                                        !processSocket(desc[n * 2 + 1], SocketEvent.OPEN_READ)) {
                                    error = true;
                                    // Close socket and clear pool
                                    wrapper.close();
                                }
                                if (!error && ((desc[n * 2] & Poll.APR_POLLOUT) == Poll.APR_POLLOUT) &&
                                        !processSocket(desc[n * 2 + 1], SocketEvent.OPEN_WRITE)) {
                                    // Close socket and clear pool
                                    error = true;
                                    wrapper.close();
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
                                    add(desc[n * 2 + 1], (int) timeout, wrapper.pollerFlags);
                                }
                            } else {
                                // Unknown event
                                getLog().warn(sm.getString("endpoint.apr.pollUnknownEvent", Long.valueOf(desc[n * 2])));
                                // Close socket and clear pool
                                wrapper.close();
                            }
                        }
                    } else if (rv < 0) {
                        int errn = -rv;
                        // Any non timeup or interrupted error is critical
                        if ((errn != Status.TIMEUP) && (errn != Status.EINTR)) {
                            if (errn > Status.APR_OS_START_USERERR) {
                                errn -= Status.APR_OS_START_USERERR;
                            }
                            getLog().error(sm.getString("endpoint.apr.pollError", Integer.valueOf(errn),
                                    Error.strerror(errn)));
                            // Destroy and reallocate the poller
                            reset = true;
                        }
                    }

                    if (reset && pollerRunning) {
                        // Reallocate the current poller
                        int count = Poll.pollset(aprPoller, desc);
                        long newPoller = allocatePoller(pollerSize, pool, -1);
                        // Don't restore connections for now, since I have not tested it
                        connectionCount.addAndGet(-count);
                        Poll.destroy(aprPoller);
                        aprPoller = newPoller;
                    }
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    getLog().warn(sm.getString("endpoint.poll.error"), t);
                }
                try {
                    // Process socket timeouts
                    if (getConnectionTimeout() > 0 && pollerRunning) {
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
             * https://bz.apache.org/bugzilla/show_bug.cgi?id=57653#c6 suggests this merging is only necessary on OSX
             * and BSD.
             *
             * https://bz.apache.org/bugzilla/show_bug.cgi?id=56313 suggests the same, or a similar, issue is happening
             * on Windows. Notes: Only the first startCount * 2 elements of the array are populated. The array is event,
             * socket, event, socket etc.
             */
            Map<Long,Long> merged = new HashMap<>(startCount);
            for (int n = 0; n < startCount; n++) {
                Long newValue = merged.merge(Long.valueOf(desc[2 * n + 1]), Long.valueOf(desc[2 * n]),
                        (v1, v2) -> Long.valueOf(v1.longValue() | v2.longValue()));
                if (log.isDebugEnabled()) {
                    if (newValue.longValue() != desc[2 * n]) {
                        log.debug(sm.getString("endpoint.apr.pollMergeEvents", Long.valueOf(desc[2 * n + 1]),
                                Long.valueOf(desc[2 * n]), newValue));
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
        protected HashMap<Long,SendfileData> sendfileData;

        protected int sendfileCount;

        public int getSendfileCount() {
            return sendfileCount;
        }

        protected ArrayList<SendfileData> addS;

        private volatile Thread sendfileThread;
        private volatile boolean sendfileRunning = true;

        /**
         * Create the sendfile poller.
         */
        protected void init() {
            pool = Pool.create(serverSockPool);
            int size = sendfileSize;
            if (size <= 0) {
                size = 16 * 1024;
            }
            sendfilePollset = allocatePoller(size, pool, getConnectionTimeout());
            desc = new long[size * 2];
            sendfileData = new HashMap<>(size);
            addS = new ArrayList<>();
        }

        protected void start() {
            sendfileThread = new Thread(sendfile, getName() + "-Sendfile");
            sendfileThread.setPriority(threadPriority);
            sendfileThread.setDaemon(true);
            sendfileThread.start();
        }

        protected synchronized void stop() {
            sendfileRunning = false;
            // In case the sendfile thread is in the idle wait
            this.notify();
        }

        /**
         * Destroy the poller.
         */
        protected void destroy() {
            // Close any socket remaining in the add queue
            for (int i = (addS.size() - 1); i >= 0; i--) {
                SendfileData data = addS.get(i);
                closeSocketInternal(data.socket);
            }
            // Close all sockets still in the poller
            int rv = Poll.pollset(sendfilePollset, desc);
            if (rv > 0) {
                for (int n = 0; n < rv; n++) {
                    closeSocketInternal(desc[n * 2 + 1]);
                }
            }
            Pool.destroy(pool);
            sendfileData.clear();
        }

        /**
         * Add the sendfile data to the sendfile poller. Note that in most cases, the initial non blocking calls to
         * sendfile will return right away, and will be handled asynchronously inside the kernel. As a result, the
         * poller will never be used.
         *
         * @param data containing the reference to the data which should be sent
         *
         * @return true if all the data has been sent right away, and false otherwise
         */
        public SendfileState add(SendfileData data) {

            SocketWrapperBase<Long> socketWrapper = connections.get(Long.valueOf(data.socket));

            // Use the blocking status write lock as a proxy for a lock on
            // writing to the socket. Don't want it to close in another thread
            // while this thread is writing as that could trigger a JVM crash.
            WriteLock wl = ((AprSocketWrapper) socketWrapper).getBlockingStatusWriteLock();
            wl.lock();

            // Initialize fd from data given
            try {
                data.fdpool = Socket.pool(data.socket);
                data.fd = File.open(data.fileName,
                        File.APR_FOPEN_READ | File.APR_FOPEN_SENDFILE_ENABLED | File.APR_FOPEN_BINARY, 0, data.fdpool);
                // Set the socket to nonblocking mode
                Socket.timeoutSet(data.socket, 0);
                while (sendfileRunning) {
                    long nw = Socket.sendfilen(data.socket, data.fd, data.pos, data.length, 0);
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
                            Socket.timeoutSet(data.socket, getConnectionTimeout() * 1000);
                            return SendfileState.DONE;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn(sm.getString("endpoint.sendfile.error"), e);
                return SendfileState.ERROR;
            } finally {
                wl.unlock();
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
            sendfileData.remove(Long.valueOf(data.socket));
        }

        /**
         * The background thread that listens for incoming TCP/IP connections and hands them off to an appropriate
         * processor.
         */
        @Override
        public void run() {

            long maintainTime = 0;
            // Loop until we receive a shutdown command
            while (sendfileRunning) {

                // Loop if poller is empty
                while (sendfileRunning && sendfileCount < 1 && addS.size() < 1) {
                    // Reset maintain time.
                    maintainTime = 0;
                    try {
                        synchronized (this) {
                            if (sendfileRunning && sendfileCount < 1 && addS.size() < 1) {
                                this.wait();
                            }
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
                                    sendfileData.put(Long.valueOf(data.socket), data);
                                    sendfileCount++;
                                } else {
                                    getLog().warn(sm.getString("endpoint.sendfile.addfail", Integer.valueOf(rv),
                                            Error.strerror(rv)));
                                    // Can't do anything: close the socket right away
                                    closeSocketInternal(data.socket);
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
                            SendfileData state = sendfileData.get(Long.valueOf(desc[n * 2 + 1]));
                            // Problem events
                            if (((desc[n * 2] & Poll.APR_POLLHUP) == Poll.APR_POLLHUP) ||
                                    ((desc[n * 2] & Poll.APR_POLLERR) == Poll.APR_POLLERR)) {
                                // Close socket and clear pool
                                remove(state);
                                // Destroy file descriptor pool, which should close the file
                                // Close the socket, as the response would be incomplete
                                closeSocketInternal(state.socket);
                                continue;
                            }
                            // Write some data using sendfile
                            long nw = Socket.sendfilen(state.socket, state.fd, state.pos, state.length, 0);
                            if (nw < 0) {
                                // Close socket and clear pool
                                remove(state);
                                // Close the socket, as the response would be incomplete
                                // This will close the file too.
                                closeSocketInternal(state.socket);
                                continue;
                            }

                            state.pos += nw;
                            state.length -= nw;
                            if (state.length == 0) {
                                remove(state);
                                switch (state.keepAliveState) {
                                    case NONE: {
                                        // Close the socket since this is
                                        // the end of the not keep-alive request.
                                        closeSocketInternal(state.socket);
                                        break;
                                    }
                                    case PIPELINED: {
                                        // Destroy file descriptor pool, which should close the file
                                        Pool.destroy(state.fdpool);
                                        Socket.timeoutSet(state.socket, getConnectionTimeout() * 1000);
                                        // Process the pipelined request data
                                        if (!processSocket(state.socket, SocketEvent.OPEN_READ)) {
                                            closeSocketInternal(state.socket);
                                        }
                                        break;
                                    }
                                    case OPEN: {
                                        // Destroy file descriptor pool, which should close the file
                                        Pool.destroy(state.fdpool);
                                        Socket.timeoutSet(state.socket, getConnectionTimeout() * 1000);
                                        // Put the socket back in the poller for
                                        // processing of further requests
                                        getPoller().add(state.socket, getKeepAliveTimeout(), Poll.APR_POLLIN);
                                        break;
                                    }
                                }
                            }
                        }
                    } else if (rv < 0) {
                        int errn = -rv;
                        /* Any non timeup or interrupted error is critical */
                        if ((errn != Status.TIMEUP) && (errn != Status.EINTR)) {
                            if (errn > Status.APR_OS_START_USERERR) {
                                errn -= Status.APR_OS_START_USERERR;
                            }
                            getLog().error(sm.getString("endpoint.apr.pollError", Integer.valueOf(errn),
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
                    if (getConnectionTimeout() > 0 && maintainTime > 1000000L && sendfileRunning) {
                        rv = Poll.maintain(sendfilePollset, desc, false);
                        maintainTime = 0;
                        if (rv > 0) {
                            for (int n = 0; n < rv; n++) {
                                // Get the sendfile state
                                SendfileData state = sendfileData.get(Long.valueOf(desc[n]));
                                // Close socket and clear pool
                                remove(state);
                                // Destroy file descriptor pool, which should close the file
                                // Close the socket, as the response would be incomplete
                                closeSocketInternal(state.socket);
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
     * This class is the equivalent of the Worker, but will simply use in an external Executor thread pool. This will
     * also set the socket options and do the handshake. This is called after an accept().
     */
    protected class SocketWithOptionsProcessor implements Runnable {

        protected SocketWrapperBase<Long> socket = null;


        public SocketWithOptionsProcessor(SocketWrapperBase<Long> socket) {
            this.socket = socket;
        }

        @Override
        public void run() {

            Lock lock = socket.getLock();
            lock.lock();
            try {
                if (!deferAccept) {
                    if (setSocketOptions(socket)) {
                        getPoller().add(socket.getSocket().longValue(), getConnectionTimeout(), Poll.APR_POLLIN);
                    } else {
                        // Close socket and pool
                        getHandler().process(socket, SocketEvent.CONNECT_FAIL);
                        socket.close();
                        socket = null;
                    }
                } else {
                    // Process the request from this socket
                    if (!setSocketOptions(socket)) {
                        // Close socket and pool
                        getHandler().process(socket, SocketEvent.CONNECT_FAIL);
                        socket.close();
                        socket = null;
                        return;
                    }
                    // Process the request from this socket
                    Handler.SocketState state = getHandler().process(socket, SocketEvent.OPEN_READ);
                    if (state == Handler.SocketState.CLOSED) {
                        // Close socket and pool
                        socket.close();
                        socket = null;
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }


    // -------------------------------------------- SocketProcessor Inner Class


    /**
     * This class is the equivalent of the Worker, but will simply use in an external Executor thread pool.
     */
    protected class SocketProcessor extends SocketProcessorBase<Long> {

        public SocketProcessor(SocketWrapperBase<Long> socketWrapper, SocketEvent event) {
            super(socketWrapper, event);
        }

        @Override
        protected void doRun() {
            try {
                // Process the request from this socket
                SocketState state = getHandler().process(socketWrapper, event);
                if (state == Handler.SocketState.CLOSED) {
                    // Close socket and pool
                    socketWrapper.close();
                }
            } finally {
                socketWrapper = null;
                event = null;
                // return to cache
                if (running && processorCache != null) {
                    processorCache.push(this);
                }
            }
        }
    }


    public static class AprSocketWrapper extends SocketWrapperBase<Long> {

        private static final int SSL_OUTPUT_BUFFER_SIZE = 8192;

        private final ByteBuffer sslOutputBuffer;

        // This field should only be used by Poller#run()
        private int pollerFlags = 0;

        /*
         * Used if block/non-blocking is set at the socket level. The client is responsible for the thread-safe use of
         * this field via the locks provided.
         */
        private volatile boolean blockingStatus = true;
        private final Lock blockingStatusReadLock;
        private final WriteLock blockingStatusWriteLock;

        public AprSocketWrapper(Long socket, AprEndpoint endpoint) {
            super(socket, endpoint);

            ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
            this.blockingStatusReadLock = lock.readLock();
            this.blockingStatusWriteLock = lock.writeLock();

            // TODO Make the socketWriteBuffer size configurable and align the
            // SSL and app buffer size settings with NIO & NIO2.
            if (endpoint.isSSLEnabled()) {
                sslOutputBuffer = ByteBuffer.allocateDirect(SSL_OUTPUT_BUFFER_SIZE);
                sslOutputBuffer.position(SSL_OUTPUT_BUFFER_SIZE);
            } else {
                sslOutputBuffer = null;
            }

            socketBufferHandler = new SocketBufferHandler(6 * 1500, 6 * 1500, true);
        }

        public boolean getBlockingStatus() {
            return blockingStatus;
        }

        public void setBlockingStatus(boolean blockingStatus) {
            this.blockingStatus = blockingStatus;
        }

        public Lock getBlockingStatusReadLock() {
            return blockingStatusReadLock;
        }

        public WriteLock getBlockingStatusWriteLock() {
            return blockingStatusWriteLock;
        }

        @Override
        public int read(boolean block, byte[] b, int off, int len) throws IOException {
            int nRead = populateReadBuffer(b, off, len);
            if (nRead > 0) {
                return nRead;
                /*
                 * Since more bytes may have arrived since the buffer was last filled, it is an option at this point to
                 * perform a non-blocking read. However correctly handling the case if that read returns end of stream
                 * adds complexity. Therefore, at the moment, the preference is for simplicity.
                 */
            }

            // Fill the read buffer as best we can.
            nRead = fillReadBuffer(block);

            // Fill as much of the remaining byte array as possible with the
            // data that was just read
            if (nRead > 0) {
                socketBufferHandler.configureReadBufferForRead();
                nRead = Math.min(nRead, len);
                socketBufferHandler.getReadBuffer().get(b, off, nRead);
            }
            return nRead;
        }


        @Override
        public int read(boolean block, ByteBuffer to) throws IOException {
            int nRead = populateReadBuffer(to);
            if (nRead > 0) {
                return nRead;
                /*
                 * Since more bytes may have arrived since the buffer was last filled, it is an option at this point to
                 * perform a non-blocking read. However correctly handling the case if that read returns end of stream
                 * adds complexity. Therefore, at the moment, the preference is for simplicity.
                 */
            }

            // The socket read buffer capacity is socket.appReadBufSize
            int limit = socketBufferHandler.getReadBuffer().capacity();
            if (to.isDirect() && to.remaining() >= limit) {
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
                }
            }
            return nRead;
        }


        private int fillReadBuffer(boolean block) throws IOException {
            socketBufferHandler.configureReadBufferForWrite();
            return fillReadBuffer(block, socketBufferHandler.getReadBuffer());
        }


        private int fillReadBuffer(boolean block, ByteBuffer to) throws IOException {
            Lock readLock = getBlockingStatusReadLock();
            WriteLock writeLock = getBlockingStatusWriteLock();

            boolean readDone = false;
            int result = 0;
            readLock.lock();
            try {
                checkClosed();
                if (getBlockingStatus() == block) {
                    if (block) {
                        Socket.timeoutSet(getSocket().longValue(), getReadTimeout() * 1000);
                    }
                    result = Socket.recvb(getSocket().longValue(), to, to.position(), to.remaining());
                    readDone = true;
                }
            } finally {
                readLock.unlock();
            }

            if (!readDone) {
                writeLock.lock();
                try {
                    checkClosed();
                    // Set the current settings for this socket
                    setBlockingStatus(block);
                    if (block) {
                        Socket.timeoutSet(getSocket().longValue(), getReadTimeout() * 1000);
                    } else {
                        Socket.timeoutSet(getSocket().longValue(), 0);
                    }
                    // Downgrade the lock
                    readLock.lock();
                    try {
                        writeLock.unlock();
                        result = Socket.recvb(getSocket().longValue(), to, to.position(), to.remaining());
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
                to.position(to.position() + result);
                return result;
            } else if (result == 0 || -result == Status.EAGAIN) {
                return 0;
            } else if ((-result) == Status.ETIMEDOUT || (-result) == Status.TIMEUP) {
                if (block) {
                    throw new SocketTimeoutException(sm.getString("iib.readtimeout"));
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
            } else if ((OS.IS_WIN32 || OS.IS_WIN64) && (-result == Status.APR_OS_START_SYSERR + 10053)) {
                // 10053 on Windows is connection aborted
                throw new EOFException(sm.getString("socket.apr.clientAbort"));
            } else {
                throw new IOException(
                        sm.getString("socket.apr.read.error", Integer.valueOf(-result), getSocket(), this));
            }
        }


        @Override
        public boolean isReadyForRead() throws IOException {
            socketBufferHandler.configureReadBufferForRead();

            if (socketBufferHandler.getReadBuffer().remaining() > 0) {
                return true;
            }

            int read = fillReadBuffer(false);

            boolean isReady = socketBufferHandler.getReadBuffer().position() > 0 || read == -1;
            return isReady;
        }


        private void checkClosed() throws IOException {
            if (isClosed()) {
                throw new IOException(sm.getString("socket.apr.closed", getSocket()));
            }
        }


        @Override
        public void close() {
            Lock lock = getLock();
            lock.lock();
            try {
                super.close();
            } finally {
                lock.unlock();
            }
        }

        @Override
        protected void doClose() {
            if (log.isDebugEnabled()) {
                log.debug("Calling [" + getEndpoint() + "].closeSocket([" + this + "])");
            }
            getEndpoint().connections.remove(getSocket());
            socketBufferHandler.free();
            socketBufferHandler = SocketBufferHandler.EMPTY;
            nonBlockingWriteBuffer.clear();
            if (sslOutputBuffer != null) {
                ByteBufferUtils.cleanDirectBuffer(sslOutputBuffer);
            }
            Poller poller = ((AprEndpoint) getEndpoint()).getPoller();
            if (poller != null) {
                poller.close(getSocket().longValue());
            }
        }


        @Override
        protected boolean flushNonBlocking() throws IOException {
            boolean dataLeft = !socketBufferHandler.isWriteBufferEmpty();

            // Write to the socket, if there is anything to write
            if (dataLeft) {
                doWrite(false);
                dataLeft = !socketBufferHandler.isWriteBufferEmpty();
            }

            if (!dataLeft && !nonBlockingWriteBuffer.isEmpty()) {
                dataLeft = nonBlockingWriteBuffer.write(this, false);

                if (!dataLeft && !socketBufferHandler.isWriteBufferEmpty()) {
                    doWrite(false);
                    dataLeft = !socketBufferHandler.isWriteBufferEmpty();
                }
            }

            return dataLeft;
        }


        @Override
        protected void doWrite(boolean block, ByteBuffer from) throws IOException {
            Lock readLock = getBlockingStatusReadLock();
            WriteLock writeLock = getBlockingStatusWriteLock();

            readLock.lock();
            try {
                checkClosed();
                if (getBlockingStatus() == block) {
                    if (block) {
                        Socket.timeoutSet(getSocket().longValue(), getWriteTimeout() * 1000);
                    }
                    doWriteInternal(from);
                    return;
                }
            } finally {
                readLock.unlock();
            }

            writeLock.lock();
            try {
                checkClosed();
                // Set the current settings for this socket
                setBlockingStatus(block);
                if (block) {
                    Socket.timeoutSet(getSocket().longValue(), getWriteTimeout() * 1000);
                } else {
                    Socket.timeoutSet(getSocket().longValue(), 0);
                }

                // Downgrade the lock
                readLock.lock();
                try {
                    writeLock.unlock();
                    doWriteInternal(from);
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


        private void doWriteInternal(ByteBuffer from) throws IOException {

            if (previousIOException != null) {
                /*
                 * Socket has previously seen an IOException on write.
                 *
                 * Blocking writes assume that buffer is always fully written so there is no code checking for
                 * incomplete writes, retaining the unwritten data and attempting to write it as part of a subsequent
                 * write call.
                 *
                 * Because of the above, when an IOException is triggered we need so skip subsequent attempts to write
                 * as otherwise it will appear to the client as if some data was dropped just before the connection is
                 * lost. It is better if the client just sees the dropped connection.
                 */
                throw new IOException(previousIOException);
            }

            int thisTime;

            do {
                thisTime = 0;
                if (getEndpoint().isSSLEnabled()) {
                    if (sslOutputBuffer.remaining() == 0) {
                        // Buffer was fully written last time around
                        sslOutputBuffer.clear();
                        transfer(from, sslOutputBuffer);
                        sslOutputBuffer.flip();
                    } else {
                        // Buffer still has data from previous attempt to write
                        // APR + SSL requires that exactly the same parameters are
                        // passed when re-attempting the write
                    }
                    thisTime = Socket.sendb(getSocket().longValue(), sslOutputBuffer, sslOutputBuffer.position(),
                            sslOutputBuffer.limit());
                    if (thisTime > 0) {
                        sslOutputBuffer.position(sslOutputBuffer.position() + thisTime);
                    }
                } else {
                    thisTime = Socket.sendb(getSocket().longValue(), from, from.position(), from.remaining());
                    if (thisTime > 0) {
                        from.position(from.position() + thisTime);
                    }
                }
                if (Status.APR_STATUS_IS_EAGAIN(-thisTime)) {
                    thisTime = 0;
                } else if (-thisTime == Status.APR_EOF) {
                    throw new EOFException(sm.getString("socket.apr.clientAbort"));
                } else if ((OS.IS_WIN32 || OS.IS_WIN64) && (-thisTime == Status.APR_OS_START_SYSERR + 10053)) {
                    // 10053 on Windows is connection aborted
                    throw new EOFException(sm.getString("socket.apr.clientAbort"));
                } else if (thisTime < 0) {
                    previousIOException = new IOException(
                            sm.getString("socket.apr.write.error", Integer.valueOf(-thisTime), getSocket(), this));
                    throw previousIOException;
                }
            } while ((thisTime > 0 || getBlockingStatus()) && from.hasRemaining());

            // If there is data left in the buffer the socket will be registered for
            // write further up the stack. This is to ensure the socket is only
            // registered for write once as both container and user code can trigger
            // write registration.
        }


        @Override
        public void registerReadInterest() {
            // Make sure an already closed socket is not added to the poller
            synchronized (closed) {
                if (isClosed()) {
                    return;
                }
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("endpoint.debug.registerRead", this));
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
            synchronized (closed) {
                if (isClosed()) {
                    return;
                }
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("endpoint.debug.registerWrite", this));
                }
                ((AprEndpoint) getEndpoint()).getPoller().add(getSocket().longValue(), getWriteTimeout(),
                        Poll.APR_POLLOUT);
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
            Lock lock = getLock();
            lock.lock();
            try {
                if (isClosed()) {
                    return;
                }
                long socket = getSocket().longValue();
                long sa = Address.get(Socket.APR_REMOTE, socket);
                remoteAddr = Address.getip(sa);
            } catch (Exception e) {
                log.warn(sm.getString("endpoint.warn.noRemoteAddr", getSocket()), e);
            } finally {
                lock.unlock();
            }
        }


        @Override
        protected void populateRemoteHost() {
            Lock lock = getLock();
            lock.lock();
            try {
                if (isClosed()) {
                    return;
                }
                long socket = getSocket().longValue();
                long sa = Address.get(Socket.APR_REMOTE, socket);
                remoteHost = Address.getnameinfo(sa, 0);
                if (remoteAddr == null) {
                    remoteAddr = Address.getip(sa);
                }
            } catch (Exception e) {
                log.warn(sm.getString("endpoint.warn.noRemoteHost", getSocket()), e);
            } finally {
                lock.unlock();
            }
        }


        @Override
        protected void populateRemotePort() {
            Lock lock = getLock();
            lock.lock();
            try {
                if (isClosed()) {
                    return;
                }
                long socket = getSocket().longValue();
                long sa = Address.get(Socket.APR_REMOTE, socket);
                Sockaddr addr = Address.getInfo(sa);
                remotePort = addr.port;
            } catch (Exception e) {
                log.warn(sm.getString("endpoint.warn.noRemotePort", getSocket()), e);
            } finally {
                lock.unlock();
            }
        }


        @Override
        protected void populateLocalName() {
            Lock lock = getLock();
            lock.lock();
            try {
                if (isClosed()) {
                    return;
                }
                long socket = getSocket().longValue();
                long sa = Address.get(Socket.APR_LOCAL, socket);
                localName = Address.getnameinfo(sa, 0);
            } catch (Exception e) {
                log.warn(sm.getString("endpoint.warn.noLocalName"), e);
            } finally {
                lock.unlock();
            }
        }


        @Override
        protected void populateLocalAddr() {
            Lock lock = getLock();
            lock.lock();
            try {
                if (isClosed()) {
                    return;
                }
                long socket = getSocket().longValue();
                long sa = Address.get(Socket.APR_LOCAL, socket);
                localAddr = Address.getip(sa);
            } catch (Exception e) {
                log.warn(sm.getString("endpoint.warn.noLocalAddr"), e);
            } finally {
                lock.unlock();
            }
        }


        @Override
        protected void populateLocalPort() {
            Lock lock = getLock();
            lock.lock();
            try {
                if (isClosed()) {
                    return;
                }
                long socket = getSocket().longValue();
                long sa = Address.get(Socket.APR_LOCAL, socket);
                Sockaddr addr = Address.getInfo(sa);
                localPort = addr.port;
            } catch (Exception e) {
                log.warn(sm.getString("endpoint.warn.noLocalPort"), e);
            } finally {
                lock.unlock();
            }
        }


        @Override
        public SSLSupport getSslSupport(String clientCertProvider) {
            if (getEndpoint().isSSLEnabled()) {
                return new AprSSLSupport(this, clientCertProvider);
            } else {
                return null;
            }
        }


        @Override
        public SSLSupport getSslSupport() {
            throw new UnsupportedOperationException();
        }


        @Override
        public void doClientAuth(SSLSupport sslSupport) throws IOException {
            long socket = getSocket().longValue();
            // Configure connection to require a certificate. This requires a
            // re-handshake and must block until the re-handshake completes.
            // Therefore, make sure socket is in blocking mode.
            Lock readLock = getBlockingStatusReadLock();
            WriteLock writeLock = getBlockingStatusWriteLock();
            boolean renegotiateDone = false;
            try {
                readLock.lock();
                try {
                    if (getBlockingStatus()) {
                        Socket.timeoutSet(getSocket().longValue(), getReadTimeout() * 1000);

                        SSLSocket.setVerify(socket, SSL.SSL_CVERIFY_REQUIRE, -1);
                        SSLSocket.renegotiate(socket);

                        renegotiateDone = true;
                    }
                } finally {
                    readLock.unlock();
                }

                if (!renegotiateDone) {
                    writeLock.lock();
                    try {
                        // Set the current settings for this socket
                        setBlockingStatus(true);
                        Socket.timeoutSet(getSocket().longValue(), getReadTimeout() * 1000);
                        // Downgrade the lock
                        readLock.lock();
                        try {
                            writeLock.unlock();
                            SSLSocket.setVerify(socket, SSL.SSL_CVERIFY_REQUIRE, -1);
                            SSLSocket.renegotiate(socket);
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
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                throw new IOException(sm.getString("socket.sslreneg"), t);
            }
        }


        @Override
        public void setAppReadBufHandler(ApplicationBufferHandler handler) {
            // no-op
        }

        String getSSLInfoS(int id) {
            synchronized (closed) {
                if (isClosed()) {
                    return null;
                }
                try {
                    return SSLSocket.getInfoS(getSocket().longValue(), id);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        int getSSLInfoI(int id) {
            synchronized (closed) {
                if (isClosed()) {
                    return 0;
                }
                try {
                    return SSLSocket.getInfoI(getSocket().longValue(), id);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        byte[] getSSLInfoB(int id) {
            synchronized (closed) {
                if (isClosed()) {
                    return null;
                }
                try {
                    return SSLSocket.getInfoB(getSocket().longValue(), id);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        @Override
        protected <A> OperationState<A> newOperationState(boolean read, ByteBuffer[] buffers, int offset, int length,
                BlockingMode block, long timeout, TimeUnit unit, A attachment, CompletionCheck check,
                CompletionHandler<Long,? super A> handler, Semaphore semaphore,
                VectoredIOCompletionHandler<A> completion) {
            return new AprOperationState<>(read, buffers, offset, length, block, timeout, unit, attachment, check,
                    handler, semaphore, completion);
        }

        private class AprOperationState<A> extends OperationState<A> {
            private volatile boolean inline = true;
            private volatile long flushBytes = 0;

            private AprOperationState(boolean read, ByteBuffer[] buffers, int offset, int length, BlockingMode block,
                    long timeout, TimeUnit unit, A attachment, CompletionCheck check,
                    CompletionHandler<Long,? super A> handler, Semaphore semaphore,
                    VectoredIOCompletionHandler<A> completion) {
                super(read, buffers, offset, length, block, timeout, unit, attachment, check, handler, semaphore,
                        completion);
            }

            @Override
            protected boolean isInline() {
                return inline;
            }

            @Override
            public void run() {
                // Perform the IO operation
                // Called from the poller to continue the IO operation
                long nBytes = 0;
                if (getError() == null) {
                    try {
                        synchronized (this) {
                            if (!completionDone) {
                                // This filters out same notification until processing
                                // of the current one is done
                                if (log.isDebugEnabled()) {
                                    log.debug("Skip concurrent " + (read ? "read" : "write") + " notification");
                                }
                                return;
                            }
                            // Find the buffer on which the operation will be performed (no vectoring with APR)
                            ByteBuffer buffer = null;
                            for (int i = 0; i < length; i++) {
                                if (buffers[i + offset].hasRemaining()) {
                                    buffer = buffers[i + offset];
                                    break;
                                }
                            }
                            if (buffer == null && flushBytes == 0) {
                                // Nothing to do
                                completion.completed(Long.valueOf(0), this);
                                return;
                            }
                            if (read) {
                                nBytes = read(false, buffer);
                            } else {
                                if (!flush(block == BlockingMode.BLOCK)) {
                                    if (flushBytes > 0) {
                                        // Flushing was done, continue processing
                                        nBytes = flushBytes;
                                        flushBytes = 0;
                                    } else {
                                        @SuppressWarnings("null") // Not possible
                                        int remaining = buffer.remaining();
                                        write(block == BlockingMode.BLOCK, buffer);
                                        nBytes = remaining - buffer.remaining();
                                        if (nBytes > 0 && flush(block == BlockingMode.BLOCK)) {
                                            // We have to flush and it's incomplete, save the bytes written until done
                                            inline = false;
                                            registerWriteInterest();
                                            flushBytes = nBytes;
                                            return;
                                        }
                                    }
                                } else {
                                    // Continue flushing
                                    inline = false;
                                    registerWriteInterest();
                                    return;
                                }
                            }
                            if (nBytes != 0) {
                                completionDone = false;
                            }
                        }
                    } catch (IOException e) {
                        setError(e);
                    }
                }
                if (nBytes > 0) {
                    // The bytes processed are only updated in the completion handler
                    completion.completed(Long.valueOf(nBytes), this);
                } else if (nBytes < 0 || getError() != null) {
                    IOException error = getError();
                    if (error == null) {
                        error = new EOFException();
                    }
                    completion.failed(error, this);
                } else {
                    // As soon as the operation uses the poller, it is no longer inline
                    inline = false;
                    if (read) {
                        registerReadInterest();
                    } else {
                        registerWriteInterest();
                    }
                }
            }
        }

    }
}
