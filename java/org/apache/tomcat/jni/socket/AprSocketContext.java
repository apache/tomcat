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
package org.apache.tomcat.jni.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.tomcat.jni.Address;
import org.apache.tomcat.jni.Error;
import org.apache.tomcat.jni.Library;
import org.apache.tomcat.jni.OS;
import org.apache.tomcat.jni.Poll;
import org.apache.tomcat.jni.Pool;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLContext;
import org.apache.tomcat.jni.SSLExt;
import org.apache.tomcat.jni.Socket;
import org.apache.tomcat.jni.Status;

public class AprSocketContext {
    /**
     * Called when a chunk of data is sent or received. This is very low
     * level, used mostly for debugging or stats.
     */
    public static interface RawDataHandler {
        public void rawData(AprSocket ch, boolean input, byte[] data, int pos,
                int len, int requested, boolean closed);
    }

    /**
     * Called in SSL mode after the handshake is completed.
     *
     * @see AprSocketContext#customVerification(TlsCertVerifier)
     */
    public static interface TlsCertVerifier {
        public void handshakeDone(AprSocket ch);
    }

    /**
     * Delegates loading of persistent info about a host - public certs,
     * tickets, config, persistent info etc.
     */
    public static interface HostInfoLoader {
        public HostInfo getHostInfo(String name, int port, boolean ssl);
    }

    private static final Logger log = Logger.getLogger("AprSocketCtx");

    // If interrupt() or thread-safe poll update are not supported - the
    // poll updates will happen after the poll() timeout.
    // The poll timeout with interrupt/thread safe updates can be much higher/
    private static final int FALLBACK_POLL_TIME = 2000;

    // It seems to send the ticket, get server helo / ChangeCipherSpec, but than
    // SSL3_GET_RECORD:decryption failed or bad record mac in s3_pkt.c:480:
    // Either bug in openssl, or some combination of ciphers - needs more debugging.
    // ( this can save a roundtrip and CPU on TLS handshake )
    boolean USE_TICKETS = false;

    private final AprSocket END = new AprSocket(this);

    private static final AtomicInteger contextNumber = new AtomicInteger();
    private int contextId;

    private final AtomicInteger threadNumber = new AtomicInteger();

    /**
     * For now - single acceptor thread per connector.
     */
    private AcceptorThread acceptor;
    private AcceptorDispatchThread acceptorDispatch;

    // APR/JNI is thread safe
    private boolean threadSafe = true;

    /**
     * Pollers.
     */
    private final List<AprPoller> pollers = new ArrayList<>();

    // Set on all accepted or connected sockets.
    // TODO: add the other properties
    boolean tcpNoDelay = true;

    protected boolean running = true;

    protected boolean sslMode;

    // onSocket() will be called in accept thread.
    // If false: use executor ( but that may choke the acceptor thread )
    private boolean nonBlockingAccept = false;

    private final BlockingQueue<AprSocket> acceptedQueue =
            new LinkedBlockingQueue<>();

    /**
     * Root APR memory pool.
     */
    private long rootPool = 0;

    /**
     * SSL context.
     */
    private long sslCtx = 0;

    TlsCertVerifier tlsCertVerifier;

    //
    final int connectTimeout =  20000;
    final int defaultTimeout = 100000;
    // TODO: Use this
    final int keepAliveTimeout = 20000;

    final AtomicInteger open = new AtomicInteger();

    /**
     * Poll interval, in microseconds. If the platform doesn't support
     * poll interrupt - it'll take this time to stop the poller.
     *
     */
    private int pollTime = 5 * 1000000;

    private HostInfoLoader hostInfoLoader;

    final RawDataHandler rawDataHandler = null;

    // TODO: do we need this here ?
    private final Map<String, HostInfo> hosts = new HashMap<>();

    private String certFile;
    private String keyFile;

    private byte[] spdyNPN;

    private byte[] ticketKey;

    // For resolving DNS ( i.e. connect ), callbacks
    private ExecutorService threadPool;

    // Separate executor for connect/handshakes
    final ExecutorService connectExecutor;

    final boolean debugSSL = false;
    private boolean debugPoll = false;

    private boolean deferAccept = false;

    private int backlog = 100;

    private boolean useSendfile;

    private int sslProtocol = SSL.SSL_PROTOCOL_TLSV1 | SSL.SSL_PROTOCOL_SSLV3;

    /**
     * Max time spent in a callback ( will be longer for blocking )
     */
    final AtomicLong maxHandlerTime = new AtomicLong();
    final AtomicLong totalHandlerTime = new AtomicLong();
    final AtomicLong handlerCount = new AtomicLong();

    /**
     * Total connections handled ( accepted or connected ).
     */
    private final AtomicInteger connectionsCount = new AtomicInteger();


    public AprSocketContext() {
        connectExecutor =new ThreadPoolExecutor(0, 64, 5, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), new RejectedExecutionHandler() {
                    @Override
                    public void rejectedExecution(Runnable r,
                            java.util.concurrent.ThreadPoolExecutor executor) {
                        AprSocket s = (AprSocket) r;
                        log.severe("Rejecting " + s);
                        s.reset();
                    }
                });
        contextId = contextNumber.incrementAndGet();
    }

    /**
     * Poller thread count.
     */
    private int pollerThreadCount = 4;
    public void setPollerThreadCount(int pollerThreadCount) { this.pollerThreadCount = pollerThreadCount; }
    public int getPollerThreadCount() { return pollerThreadCount; }

    // to test the limits - default should be lower
    private int maxConnections = 64 * 1024;
    public void setMaxconnections(int maxCon) {
        this.maxConnections = maxCon;
    }

    public void setBacklog(int backlog) { if (backlog > 0) this.backlog = backlog; }
    public int getBacklog() { return backlog; }

    /**
     * Defer accept.
     */
    public void setDeferAccept(boolean deferAccept) { this.deferAccept = deferAccept; }
    public boolean getDeferAccept() { return deferAccept; }

    /**
     * For client:
     *   - ClientHello will include the npn extension ( the ID == 0x3374)
     *   - if ServerHello includes a list of protocols - select one
     *   - send it after ChangeCipherSpec and before Finish
     *
     *  For server:
     *   - if ClientHello includes the npn extension
     *    -- will send this string as list of supported protocols in ServerHello
     *   - read the selection before Finish.
     * @param npn
     */
    public void setNpn(String npn) {
        byte[] data = npn.getBytes();
        byte[] npnB = new byte[data.length + 2];

        System.arraycopy(data, 0, npnB, 1, data.length);
        npnB[0] = (byte) data.length;
        npnB[npnB.length - 1] = 0;
        spdyNPN = npnB;

    }

    public void setNpn(byte[] data) {
        spdyNPN = data;
    }

    public void setHostLoader(HostInfoLoader handler) {
        this.hostInfoLoader = handler;
    }

    public boolean isServer() {
        return acceptor != null;
    }

    protected Executor getExecutor() {
        if (threadPool == null) {
            threadPool = Executors.newCachedThreadPool(new ThreadFactory( ) {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "AprThread-" + contextId + "-" +
                            threadNumber.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            });
        }
        return threadPool;
    }

    /**
     * All accepted/connected sockets will start handshake automatically.
     */
    public AprSocketContext setTls() {
        this.sslMode = true;
        return this;
    }

    public void setTcpNoDelay(boolean b) {
        tcpNoDelay = b;
    }

    public void setSslProtocol(String protocol) {
        protocol = protocol.trim();
        if ("SSLv2".equalsIgnoreCase(protocol)) {
            sslProtocol = SSL.SSL_PROTOCOL_SSLV2;
        } else if ("SSLv3".equalsIgnoreCase(protocol)) {
            sslProtocol = SSL.SSL_PROTOCOL_SSLV3;
        } else if ("TLSv1".equalsIgnoreCase(protocol)) {
            sslProtocol = SSL.SSL_PROTOCOL_TLSV1;
        } else if ("all".equalsIgnoreCase(protocol)) {
            sslProtocol = SSL.SSL_PROTOCOL_ALL;
        }
    }

    public void setTicketKey(byte[] key48Bytes) {
        if(key48Bytes.length != 48) {
            throw new RuntimeException("Key must be 48 bytes");
        }
        this.ticketKey = key48Bytes;
    }

    public void customVerification(TlsCertVerifier verifier) {
        tlsCertVerifier = verifier;
    }

    // TODO: should have a separate method for switching to tls later.
    /**
     * Set certificate, will also enable TLS mode.
     */
    public AprSocketContext setKeys(String certPemFile, String keyDerFile) {
        this.sslMode = true;
        setTls();
        certFile = certPemFile;
        keyFile = keyDerFile;
        return this;
    }

    /**
     * SSL cipher suite.
     */
    private String SSLCipherSuite = "ALL";
    public String getSSLCipherSuite() { return SSLCipherSuite; }
    public void setSSLCipherSuite(String SSLCipherSuite) { this.SSLCipherSuite = SSLCipherSuite; }

    /**
     * Override or use hostInfoLoader to implement persistent/memcache storage.
     */
    public HostInfo getHostInfo(String host, int port, boolean ssl) {
        if (hostInfoLoader != null) {
            return hostInfoLoader.getHostInfo(host, port, ssl);
        }
        // Use local cache
        String key = host + ":" + port;
        HostInfo pi = hosts.get(key);
        if (pi != null) {
            return pi;
        }
        pi = new HostInfo(host, port, ssl);
        hosts.put(key, pi);
        return pi;
    }

    protected void rawData(AprSocket ch, boolean inp, byte[] data, int pos,
            int len, int requested, boolean closed) {
        if (rawDataHandler != null) {
            rawDataHandler.rawData(ch, inp, data, pos, len, requested, closed);
        }
    }

    public void listen(final int port) throws IOException {
        if (acceptor != null) {
            throw new IOException("Already accepting on " + acceptor.port);
        }
        if (sslMode && certFile == null) {
            throw new IOException("Missing certificates for server");
        }
        if (sslMode || !nonBlockingAccept) {
            acceptorDispatch = new AcceptorDispatchThread();
            acceptorDispatch.setName("AprAcceptorDispatch-" + port);
            acceptorDispatch.start();
        }

        acceptor = new AcceptorThread(port);
        acceptor.prepare();
        acceptor.setName("AprAcceptor-" + port);
        acceptor.start();


    }

    /**
     * Get a socket for connectiong to host:port.
     */
    public AprSocket socket(String host, int port, boolean ssl) {
        HostInfo hi = getHostInfo(host, port, ssl);
        return socket(hi);
    }

    public AprSocket socket(HostInfo hi) {
        AprSocket sock = newSocket(this);
        sock.setHost(hi);
        return sock;
    }

    public AprSocket socket(long socket) {
        AprSocket sock = newSocket(this);
        // Tomcat doesn't set this
        SSLExt.sslSetMode(socket, SSLExt.SSL_MODE_ENABLE_PARTIAL_WRITE |
                SSLExt.SSL_MODE_ACCEPT_MOVING_WRITE_BUFFER);
        sock.setStatus(AprSocket.ACCEPTED);
        sock.socket = socket;
        return sock;
    }


    void destroySocket(AprSocket socket) {
        // TODO: does it need to be done in io thread ?
        synchronized (socket) {
            if (socket.socket != 0) {
                long s = socket.socket;
                socket.socket = 0;
                log.info("DESTROY: " + Long.toHexString(s));
                Socket.destroy(s);
            }
        }
    }

    protected void connectBlocking(AprSocket apr) throws IOException {
        try {
            if (!running) {
                throw new IOException("Stopped");
            }
            HostInfo hi = apr.getHost();

            long clientSockP;
            synchronized (pollers) {
                long socketpool = Pool.create(getRootPool());

                int family = Socket.APR_INET;

                clientSockP = Socket.create(family,
                        Socket.SOCK_STREAM,
                        Socket.APR_PROTO_TCP, socketpool); // or rootPool ?
            }
            Socket.timeoutSet(clientSockP, connectTimeout * 1000);
            if (OS.IS_UNIX) {
                Socket.optSet(clientSockP, Socket.APR_SO_REUSEADDR, 1);
            }

            Socket.optSet(clientSockP, Socket.APR_SO_KEEPALIVE, 1);

            // Blocking
            // TODO: use socket pool
            // TODO: cache it ( and TTL ) in hi
            long inetAddress = Address.info(hi.host, Socket.APR_INET,
                  hi.port, 0, rootPool);
            // this may take a long time - stop/destroy must wait
            // at least connect timeout
            int rc = Socket.connect(clientSockP, inetAddress);

            if (rc != 0) {
                synchronized (pollers) {
                    Socket.close(clientSockP);
                    Socket.destroy(clientSockP);
                }
                /////Pool.destroy(socketpool);
                throw new IOException("Socket.connect(): " + rc + " " + Error.strerror(rc) + " " + connectTimeout);
            }
            if (!running) {
                throw new IOException("Stopped");
            }

            connectionsCount.incrementAndGet();
            if (tcpNoDelay) {
                Socket.optSet(clientSockP, Socket.APR_TCP_NODELAY, 1);
            }

            Socket.timeoutSet(clientSockP, defaultTimeout * 1000);

            apr.socket = clientSockP;

            apr.afterConnect();
        } catch (IOException e) {
            apr.reset();
            throw e;
        } catch (Throwable e) {
            apr.reset();
            e.printStackTrace();
            throw new IOException(e);
        }
    }

    AprSocket newSocket(AprSocketContext context) {
        return new AprSocket(context);
    }

    /**
     * To clean the pools - we could track if all channels are
     * closed, but this seems simpler and safer.
     */
    @Override
    protected void finalize() {
        if (rootPool != 0) {
            log.warning(this + " GC without stop()");
            try {
                stop();
            } catch (Exception e) {
                //TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }


    public void stop() {
        synchronized (pollers) {
            if (!running) {
                return;
            }
            running = false;
        }

        if (rootPool != 0) {
            if (acceptor != null) {
                try {
                    acceptor.unblock();
                    acceptor.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (acceptorDispatch != null) {
                acceptedQueue.add(END);
                try {
                    acceptorDispatch.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (threadPool != null) {
                threadPool.shutdownNow();
            }

            log.info("Stopping pollers " + contextId);

            while (true) {
                AprPoller a;
                synchronized (pollers) {
                    if (pollers.size() == 0) {
                        break;
                    }
                    a = pollers.remove(0);
                }
                a.interruptPoll();
                try {
                    a.join();
                    log.info("Poller " + a.id + " done ");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    // Called when the last poller has been destroyed.
    void destroy() {
        synchronized (pollers) {
            if (pollers.size() != 0) {
                return;
            }

            if (rootPool == 0) {
                return;
            }
            log.info("Destroy root pool " + rootPool);
            //Pool.destroy(rootPool);
            //rootPool = 0;
        }
    }

    private static IOException noApr;
    static {

        try {
            Library.initialize(null);
            SSL.initialize(null);
        } catch (Exception e) {
            noApr = new IOException("APR not present", e);
        }

    }

    private long getRootPool() throws IOException {
        if (rootPool == 0) {
            if (noApr != null) {
                throw noApr;
            }
            // Create the root APR memory pool
            rootPool = Pool.create(0);

            // Adjust poller sizes
            if ((OS.IS_WIN32 || OS.IS_WIN64) && (maxConnections > 1024)) {
                // The maximum per poller to get reasonable performance is 1024
                pollerThreadCount = maxConnections / 1024;
                // Adjust poller size so that it won't reach the limit
                maxConnections = maxConnections - (maxConnections % 1024);
            }
        }
        return rootPool;
    }

    long getSslCtx() throws Exception {
        if (sslCtx == 0) {
            synchronized (AprSocketContext.class) {

            boolean serverMode = acceptor != null;
            sslCtx = SSLContext.make(getRootPool(),
                    sslProtocol,
                    serverMode ? SSL.SSL_MODE_SERVER : SSL.SSL_MODE_CLIENT);


            // SSL.SSL_OP_NO_SSLv3
            int opts = SSL.SSL_OP_NO_SSLv2 |
                SSL.SSL_OP_SINGLE_DH_USE;

            if (!USE_TICKETS || serverMode && ticketKey == null) {
                opts |= SSL.SSL_OP_NO_TICKET;
            }

            SSLContext.setOptions(sslCtx, opts);
            // Set revocation
            //        SSLContext.setCARevocation(sslContext, SSLCARevocationFile, SSLCARevocationPath);

            // Client certificate verification - maybe make it option
            try {
                SSLContext.setCipherSuite(sslCtx, SSLCipherSuite);


                if (serverMode) {
                    if (ticketKey != null) {
                        //SSLExt.setTicketKeys(sslCtx, ticketKey, ticketKey.length);
                    }
                    if (certFile != null) {
                        boolean rc = SSLContext.setCertificate(sslCtx,
                                certFile,
                                keyFile, null, SSL.SSL_AIDX_DSA);
                        if (!rc) {
                            throw new IOException("Can't set keys");
                        }
                    }
                    SSLContext.setVerify(sslCtx, SSL.SSL_CVERIFY_NONE, 10);

                    if (spdyNPN != null) {
                        SSLExt.setNPN(sslCtx, spdyNPN, spdyNPN.length);
                    }
                } else {
                    if (tlsCertVerifier != null) {
                        // NONE ?
                        SSLContext.setVerify(sslCtx,
                                SSL.SSL_CVERIFY_NONE, 10);
                    } else {
                        SSLContext.setCACertificate(sslCtx,
                                "/etc/ssl/certs/ca-certificates.crt",
                                "/etc/ssl/certs");
                        SSLContext.setVerify(sslCtx,
                                SSL.SSL_CVERIFY_REQUIRE, 10);
                    }

                    if (spdyNPN != null) {
                        SSLExt.setNPN(sslCtx, spdyNPN, spdyNPN.length);
                    }
                }
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }

            // TODO: try release buffers
            }
        }
        return sslCtx;
    }

    void findPollerAndAdd(AprSocket ch) throws IOException {
        if (ch.poller != null) {
            ch.poller.requestUpdate(ch);
            return;
        }
        assignPoller(ch);
    }

    void assignPoller(AprSocket ch) throws IOException {
        AprPoller target = null;
        synchronized (pollers) {
            // Make sure we have min number of pollers
            int needPollers = pollerThreadCount - pollers.size();
            if (needPollers > 0) {
                for (int i = needPollers; i > 0; i--) {
                    pollers.add(allocatePoller());
                }
            }
            int max = 0;
            for (AprPoller poller: pollers) {
                int rem = poller.remaining();
                if (rem > max) {
                    target = poller;
                    max = rem;
                }
            }
        }
        if (target != null && target.add(ch)) {
            return;
        }

        // can't be added - add a new poller
        synchronized (pollers) {
            AprPoller poller = allocatePoller();
            poller.add(ch);
            pollers.add(poller);
        }
    }

    /**
     * Called on each accepted socket (for servers) or after connection (client)
     * after handshake.
     */
    protected void onSocket(@SuppressWarnings("unused") AprSocket s) {
        // Defaults to NO-OP. Parameter is used by sub-classes.
    }

    private class AcceptorThread extends Thread {
        private final int port;
        private long serverSockPool = 0;
        private long serverSock = 0;

        private long inetAddress;

        AcceptorThread(int port) {
            this.port = port;
            setDaemon(true);
        }

        void prepare() throws IOException {
            try {
                // Create the pool for the server socket
                serverSockPool = Pool.create(getRootPool());

                int family = Socket.APR_INET;
                inetAddress =
                        Address.info(null, family, port, 0, serverSockPool);

                // Create the APR server socket
                serverSock = Socket.create(family,
                        Socket.SOCK_STREAM,
                        Socket.APR_PROTO_TCP, serverSockPool);

                if (OS.IS_UNIX) {
                    Socket.optSet(serverSock, Socket.APR_SO_REUSEADDR, 1);
                }
                // Deal with the firewalls that tend to drop the inactive sockets
                Socket.optSet(serverSock, Socket.APR_SO_KEEPALIVE, 1);
                // Bind the server socket
                int ret = Socket.bind(serverSock, inetAddress);
                if (ret != 0) {
                    throw new IOException("Socket.bind " + ret + " " +
                            Error.strerror(ret) + " port=" + port);
                }
                // Start listening on the server socket
                ret = Socket.listen(serverSock, backlog );
                if (ret != 0) {
                    throw new IOException("endpoint.init.listen"
                            + ret + " " + Error.strerror(ret));
                }
                if (OS.IS_WIN32 || OS.IS_WIN64) {
                    // On Windows set the reuseaddr flag after the bind/listen
                    Socket.optSet(serverSock, Socket.APR_SO_REUSEADDR, 1);
                }

                // Sendfile usage on systems which don't support it cause major problems
                if (useSendfile && !Library.APR_HAS_SENDFILE) {
                    useSendfile = false;
                }

                // Delay accepting of new connections until data is available
                // Only Linux kernels 2.4 + have that implemented
                // on other platforms this call is noop and will return APR_ENOTIMPL.
                if (deferAccept) {
                    if (Socket.optSet(serverSock, Socket.APR_TCP_DEFER_ACCEPT, 1) == Status.APR_ENOTIMPL) {
                        deferAccept = false;
                    }
                }
            } catch (Throwable t) {
                throw new IOException(t);
            }
        }

        void unblock() {
            try (java.net.Socket sock = new java.net.Socket()) {
                // Easiest ( maybe safest ) way to interrupt accept
                // we could have it in non-blocking mode, etc
                sock.connect(new InetSocketAddress("127.0.0.1", port));
            } catch (Exception ex) {
                // ignore - the acceptor may have shut down by itself.
            }
        }

        @Override
        public void run() {
            while (running) {
                try {
                    // each socket has a pool.
                    final AprSocket ch = newSocket(AprSocketContext.this);
                    ch.setStatus(AprSocket.ACCEPTED);

                    ch.socket = Socket.accept(serverSock);
                    if (!running) {
                        break;
                    }
                    connectionsCount.incrementAndGet();
                    if (connectionsCount.get() % 1000 == 0) {
                        System.err.println("Accepted: " + connectionsCount.get());
                    }

                    if (nonBlockingAccept && !sslMode) {
                        ch.setStatus(AprSocket.CONNECTED);
                        // TODO: SSL really needs a thread.
                        onSocket(ch);
                    } else {
                        acceptedQueue.add(ch);
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
            Socket.close(serverSock);
        }
    }

    private class AcceptorDispatchThread extends Thread {

        AcceptorDispatchThread() {
            setDaemon(true);
        }

        @Override
        public void run() {
            while(running) {
                try {
                    AprSocket ch = acceptedQueue.take();
                    if (ch == END) {
                        return;
                    }
                    connectExecutor.execute(ch);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    /**
     * Create the poller. With some versions of APR, the maximum poller size will
     * be 62 (recompiling APR is necessary to remove this limitation).
     * @throws IOException
     */
    AprPoller allocatePoller() throws IOException {
        long pool = Pool.create(getRootPool());
        int size = maxConnections / pollerThreadCount;

        long serverPollset = allocatePoller(size, pool);

        if (serverPollset == 0 && size > 1024) {
            log.severe("Falling back to 1024-sized poll, won't scale");
            size = 1024;
            serverPollset = allocatePoller(size, pool);
        }
        if (serverPollset == 0) {
            log.severe("Falling back to 62-sized poll, won't scale");
            size = 62;
            serverPollset = allocatePoller(size, pool);
        }

        AprPoller res = new AprPoller();
        res.pool = pool;
        res.serverPollset = serverPollset;
        res.desc = new long[size * 2];
        res.size = size;
        res.id = contextId++;
        res.setDaemon(true);
        res.setName("AprPoller-" + res.id);
        res.start();
        if (debugPoll && !sizeLogged) {
            sizeLogged = true;
            log.info("Poller size " + (res.desc.length / 2));
        }
        return res;
    }

    // Removed the 'thread safe' updates for now, to simplify the code
    // last test shows a small improvement, can switch later.
    private static boolean sizeLogged = false;

    protected long allocatePoller(int size, long pool) {
        int flag = threadSafe ? Poll.APR_POLLSET_THREADSAFE: 0;
        for (int i = 0; i < 2; i++) {
            try {
                //  timeout must be -1 - or ttl will take effect, strange results.
                return Poll.create(size, pool, flag, -1);
            } catch (Error e) {
                e.printStackTrace();
                if (Status.APR_STATUS_IS_EINVAL(e.getError())) {
                    log.info(" endpoint.poll.limitedpollsize " + size);
                    return 0;
                } else if (Status.APR_STATUS_IS_ENOTIMPL(e.getError())) {
                    // thread safe not supported
                    log.severe("THREAD SAFE NOT SUPPORTED" + e);
                    threadSafe = false;
                    // try again without the flags
                    continue;
                } else {
                    log.severe("endpoint.poll.initfail" + e);
                    return 0;
                }
            }
        }
        log.severe("Unexpected ENOTIMPL with flag==0");
        return 0;
    }

    class AprPoller extends Thread {

        private int id;
        private int size;
        private long serverPollset = 0;
        private long pool = 0;
        private long[] desc;

        private long lastPoll;
        private long lastPollTime;
        private final AtomicBoolean inPoll = new AtomicBoolean(false);

        // Should be replaced with socket data.
        // used only to lookup by socket
        private final Map<Long, AprSocket> channels = new HashMap<>();

        // Active + pending, must be < desc.length / 2
        // The channel will also have poller=this when active or pending
        // How many sockets have poller == this
        private final AtomicInteger keepAliveCount = new AtomicInteger();
        // Tracks desc, how many sockets are actively polled
        private final AtomicInteger polledCount = new AtomicInteger();

        private final AtomicInteger pollCount = new AtomicInteger();

        private final List<AprSocket> updates = new ArrayList<>();

        @Override
        public void run() {
            if (!running) {
                return;
            }
            if (debugPoll) {
                log.info("Starting poller " + id + " " + (isServer() ? "SRV ": "CLI "));
            }
            long t0 = System.currentTimeMillis();
            while (running) {
                try {
                    updates();

                    // Pool for the specified interval. Remove signaled sockets
                    synchronized (this) {
                        inPoll.set(true);
                    }
                    // if updates are added after updates and poll - interrupt will have still
                    // work

                    int rv = Poll.poll(serverPollset, pollTime, desc, true);
                    synchronized (this) {
                        inPoll.set(false);
                        if (!running) {
                            break;
                        }
                    }

                    pollCount.incrementAndGet();
                    lastPoll = System.currentTimeMillis();
                    lastPollTime = lastPoll - t0;

                    if (rv > 0) {
                        if (debugPoll) {
                            log.info(" Poll() id=" + id + " rv=" + rv + " keepAliveCount=" + keepAliveCount +
                                    " polled = " + polledCount.get()
                                    + " time=" + lastPollTime);
                        }
                        polledCount.addAndGet(-rv);
                        for (int pollIdx = 0; pollIdx < rv; pollIdx++) {
                            long sock = desc[pollIdx * 2 + 1];
                            AprSocket ch;
                            boolean blocking = false;

                            synchronized (channels) {
                                ch = channels.get(Long.valueOf(sock));
                                if (ch != null) {
                                    blocking = ch.isBlocking();
                                } else {
                                    log.severe("Polled socket not found !!!!!" + Long.toHexString(sock));
                                    // TODO: destroy/close the raw socket
                                    continue;
                                }
                            }
                            // was removed from polling
                            ch.clearStatus(AprSocket.POLL);

                            // We just removed it ( see last param to poll()).
                            // Check for failed sockets and hand this socket off to a worker
                            long mask = desc[pollIdx * 2];

                            boolean err = ((mask & Poll.APR_POLLERR) == Poll.APR_POLLERR);
                            boolean nval = ((mask & Poll.APR_POLLNVAL) != 0);
                            if (err || nval) {
                                System.err.println("ERR " + err + " NVAL " + nval);
                            }

                            boolean out = (mask & Poll.APR_POLLOUT) == Poll.APR_POLLOUT;
                            boolean in = (mask & Poll.APR_POLLIN) == Poll.APR_POLLIN;
                            if (debugPoll) {
                                log.info(" Poll channel: " + Long.toHexString(mask) +
                                        (out ? " OUT" :"") +
                                        (in ? " IN": "") +
                                        (err ? " ERR" : "") +
                                        " Ch: " + ch);
                            }

                            // will be set again in process(), if all read/write is done
                            ch.clearStatus(AprSocket.POLLOUT);
                            ch.clearStatus(AprSocket.POLLIN);

                            // try to send if needed
                            if (blocking) {
                                synchronized (ch) {
                                    ch.notifyAll();
                                }
                                getExecutor().execute(ch);
                            } else {
                                ((AprSocketContext.NonBlockingPollHandler) ch.handler).process(ch, in, out, false);

                                // Update polling for the channel (in IO thread, safe)
                                updateIOThread(ch);
                            }
                        }
                    } else if (rv < 0) {
                        int errn = -rv;
                        if (errn == Status.TIMEUP) {
                            // to or interrupt
//                            if (debugPoll) {
//                                log.info(" Poll() timeup" + " keepAliveCount=" + keepAliveCount +
//                                        " polled = " + polledCount.get()
//                                        + " time=" + lastPollTime);
//                            }
                        } else if (errn == Status.EINTR) {
                            // interrupt - no need to log
                        } else {
                            if (debugPoll) {
                                log.info(" Poll() rv=" + rv + " keepAliveCount=" + keepAliveCount +
                                        " polled = " + polledCount.get()
                                        + " time=" + lastPollTime);
                            }
                            /* Any non timeup or interrupted error is critical */
                            if (errn >  Status.APR_OS_START_USERERR) {
                                errn -=  Status.APR_OS_START_USERERR;
                            }
                            log.severe("endpoint.poll.fail " + errn + " " + Error.strerror(errn));
                            // Handle poll critical failure
                            synchronized (this) {
                                destroyPoller(); // will close all sockets
                            }
                            continue;
                        }
                    }
                    // TODO: timeouts
                } catch (Throwable t) {
                    log.log(Level.SEVERE, "endpoint.poll.error", t);
                }

            }
            if (!running) {
                destroyPoller();
            }
        }

        /**
                 * Destroy the poller.
                 */
        protected void destroyPoller() {
            synchronized (pollers) {
                pollers.remove(this);
            }
            log.info("Poller stopped after cnt=" +
                    pollCount.get() +
                    " sockets=" + channels.size() +
                    " lastPoll=" + lastPoll);

            // Close all sockets
            synchronized (this)  {
                if (serverPollset == 0) {
                    return;
                }

//                for (AprSocket ch: channels.values()) {
//                    ch.poller = null;
//                    ch.reset();
//                }
                keepAliveCount.set(0);
                log.warning("Destroy pollset");
                //serverPollset = 0;
            }
            Pool.destroy(pool);
            pool = 0;
            synchronized (pollers) {
                // Now we can destroy the root pool
                if (pollers.size() == 0 && !running) {
                    log.info("Destroy server context");
//                    AprSocketContext.this.destroy();
                }
            }
        }

        /**
         * Called only in poller thread, only used if not thread safe
         * @throws IOException
         */
        protected void updates() throws IOException {
            synchronized (this) {
                for (AprSocket up: updates) {
                    updateIOThread(up);
                }
                updates.clear();
            }
        }

        void interruptPoll() {
            try {
                int rc = Status.APR_SUCCESS;
                synchronized (this) {
                    if (serverPollset != 0) {
                        rc = Poll.interrupt(serverPollset);
                    } else {
                        log.severe("Interrupt with closed pollset");
                    }
                }
                if (rc != Status.APR_SUCCESS) {
                    log.severe("Failed interrupt and not thread safe");
                }
            } catch (Throwable t) {
                t.printStackTrace();
                if (pollTime > FALLBACK_POLL_TIME) {
                    pollTime = FALLBACK_POLL_TIME;
                }
            }
        }


        int remaining() {
            synchronized (channels) {
                return (desc.length - channels.size() * 2);
            }
        }



        /**
         * Called from any thread, return true if we could add it
         * to pending.
         */
        boolean add(AprSocket ch) throws IOException {
            synchronized (this) {
                if (!running) {
                    return false;
                }
                if (keepAliveCount.get() >= size) {
                    return false;
                }
                keepAliveCount.incrementAndGet();
                ch.poller = this;
            }

            requestUpdate(ch);

            return true;
        }

        /**
         * May be called outside of IOThread.
         */
        protected void requestUpdate(AprSocket ch) throws IOException {
            synchronized (this) {
                if (!running) {
                    return;
                }
            }
            if (isPollerThread()) {
                updateIOThread(ch);
            } else {
                synchronized (this) {
                    if (!updates.contains(ch)) {
                        updates.add(ch);
                    }
                    interruptPoll();
                }
                if (debugPoll) {
                    log.info("Poll: requestUpdate " + id + " " + ch);
                }
            }
        }

        private void updateIOThread(AprSocket ch) throws IOException {
            if (!running || ch.socket == 0) {
                return;
            }
            // called from IO thread, either in 'updates' or after
            // poll.
            //synchronized (ch)
            boolean polling = ch.checkPreConnect(AprSocket.POLL);

            int requested = ch.requestedPolling();
            if (requested == 0) {
                if (polling) {
                    removeSafe(ch);
                }
                if (ch.isClosed()) {
                    synchronized (channels) {
                        ch.poller = null;
                        channels.remove(Long.valueOf(ch.socket));
                    }
                    keepAliveCount.decrementAndGet();
                    ch.reset();
                }
            } else {
                if (polling) {
                    removeSafe(ch);
                }
                // will close if error
                pollAdd(ch, requested);
            }
            if (debugPoll) {
                log.info("Poll: updated=" + id + " " + ch);
            }
        }

        /**
         * Called only from IO thread
         */
        private void pollAdd(AprSocket up, int req) throws IOException {
            boolean failed = false;
            int rv;
            synchronized (channels) {
                if (up.isClosed()) {
                    return;
                }
                rv = Poll.add(serverPollset, up.socket, req);
                if (rv != Status.APR_SUCCESS) {
                    up.poller = null;
                    keepAliveCount.decrementAndGet();
                    failed = true;
                } else {
                    polledCount.incrementAndGet();
                    channels.put(Long.valueOf(up.socket), up);
                    up.setStatus(AprSocket.POLL);
                }
            }
            if (failed) {
                up.reset();
                throw new IOException("poll add error " +  rv + " " + up + " " + Error.strerror(rv));
            }
        }

        /**
         * Called only from IO thread. Remove from Poll and channels,
         * set POLL bit to false.
         */
        private void removeSafe(AprSocket up) {
            int rv = Status.APR_EGENERAL;
            if (running && serverPollset != 0 && up.socket != 0
                    && !up.isClosed()) {
                rv = Poll.remove(serverPollset, up.socket);
            }
            up.clearStatus(AprSocket.POLL);

            if (rv != Status.APR_SUCCESS) {
                log.severe("poll remove error " +  Error.strerror(rv) + " " + up);
            } else {
                polledCount.decrementAndGet();
            }
        }


        public boolean isPollerThread() {
            return Thread.currentThread() == this;
        }

    }

    /**
     * Callback for poll events, will be invoked in a thread pool.
     *
     */
    public static interface BlockingPollHandler {

        /**
         * Called when the socket has been polled for in, out or closed.
         *
         *
         */
        public void process(AprSocket ch, boolean in, boolean out, boolean close);


        /**
         *  Called just before the socket is destroyed
         */
        public void closed(AprSocket ch);
    }

    /**
     *  Additional callbacks for non-blocking.
     *  This can be much faster - but it's harder to code, should be used only
     *  for low-level protocol implementation, proxies, etc.
     *
     *  The model is restricted in many ways to avoid complexity and bugs:
     *
     *  - read can only happen in the IO thread associated with the poller
     *  - user doesn't control poll interest - it is set automatically based
     *  on read/write results
     *  - it is only possible to suspend read, for TCP flow control - also
     *  only from the IO thread. Resume can happen from any thread.
     *  - it is also possible to call write() from any thread
     */
    public static interface NonBlockingPollHandler extends BlockingPollHandler {

        /**
         * Called after connection is established, in a thread pool.
         * Process will be called next.
         */
        public void connected(AprSocket ch);

        /**
         * Before close, if an exception happens.
         */
        public void error(AprSocket ch, Throwable t);
    }

}
