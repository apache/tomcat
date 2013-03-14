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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.tomcat.jni.Address;
import org.apache.tomcat.jni.Error;
import org.apache.tomcat.jni.Poll;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLExt;
import org.apache.tomcat.jni.SSLSocket;
import org.apache.tomcat.jni.Sockaddr;
import org.apache.tomcat.jni.Socket;
import org.apache.tomcat.jni.Status;
import org.apache.tomcat.jni.socket.AprSocketContext.AprPoller;
import org.apache.tomcat.jni.socket.AprSocketContext.BlockingPollHandler;

/**
 * Native socket, using JNI + APR + openssl.
 *
 * The socket is non-blocking - you can register either a blocking or non
 * blocking callback.
 *
 * There is no explicit method to register/unregister poll interest -
 * it is done automatically, when read/write methods return 0.
 *
 * To keep the socket polling you must read all the available data, until
 * read() returns 0. If you want to pause - don't read all input. To resume -
 * read again until it returns 0.
 *
 * Same for write - when write() returns 0 the socket is registered for
 * write interest.
 *
 * You can also use the blocking read/write methods.
 */
public class AprSocket implements Runnable {

    private static final Logger log =
            Logger.getLogger("org.apache.tomcat.jni.socket.AprSocket");

    private static final byte[][] NO_CERTS = new byte[0][];

    static final int CONNECTING = 0x1;
    static final int CONNECTED = 0x2;

    // Current ( real ) poll status
    static final int POLLIN_ACTIVE = 0x4;
    static final int POLLOUT_ACTIVE = 0x8;

    static final int POLL = 0x10;

    static final int SSL_ATTACHED = 0x40;

    // Requested poll status. Set by read/write when needed.
    // Cleared when polled
    static final int POLLIN = 0x80;
    static final int POLLOUT = 0x100;

    static final int ACCEPTED = 0x200;
    static final int ERROR = 0x400;
    static final int CLOSED = 0x800;

    static final int READING = 0x1000;
    static final int WRITING = 0x2000;

    // Not null
    private final AprSocketContext context;

    // only one - to save per/socket memory - context has similar callbacks.
    BlockingPollHandler handler;

    // Set while it's associated with a poller - it'll stay associated after
    // connect until close. Destroy will happen in the poller.
    // POLL bit indicates if the socket is actually polling.
    AprPoller poller;

    // Bit field indicating the status and socket should only be accessed with
    // socketLock protection
    private int status;

    long socket;

    //long to = 10000;

    // Persistent info about the peer ( SSL, etc )
    private HostInfo hostInfo;

    AprSocket(AprSocketContext context) {
        this.context = context;
    }

    public void recycle() {
        status = 0;
        hostInfo = null;
        handler = null;
        socket = 0;
        poller = null;
    }

    @Override
    public String toString() {
        return (context.isServer() ? "AprSrv-" : "AprCli-") +
                Long.toHexString(socket) + " " + Integer.toHexString(status);
    }

    public void setHandler(BlockingPollHandler l) {
        handler = l;
    }

    private void setNonBlocking() {
        if (socket != 0 && context.running) {
            Socket.optSet(socket, Socket.APR_SO_NONBLOCK, 1);
            Socket.timeoutSet(socket, 0);
        }
    }

    /**
     * Check if the socket is currently registered with a poller.
     */
    public boolean isPolling() {
        synchronized (this) {
            return (status & POLL) != 0;
        }
    }

    public BlockingPollHandler getHandler() {
        return handler;
    }

    public AprSocketContext getContext() {
        return context;
    }

    AprSocket setHost(HostInfo hi) {
        hostInfo = hi;
        return this;
    }

    /**
     */
    public void connect() throws IOException {
        if (isBlocking()) {
            // will call handleConnected() at the end.
            context.connectBlocking(this);
        } else {
            synchronized(this) {
                if ((status & CONNECTING) != 0) {
                    return;
                }
                status |= CONNECTING;
            }
            context.connectExecutor.execute(this);
        }
    }


    // after connection is done, called from a thread pool ( not IO thread )
    // may block for handshake.
    void afterConnect() throws IOException {
        if (hostInfo.secure) {
            blockingStartTLS();
        }

        setNonBlocking(); // call again, to set the bits ( connect was blocking )

        setStatus(CONNECTED);
        clearStatus(CONNECTING);

        notifyConnected(false);
    }

    public HostInfo getHost() {
        return hostInfo;
    }

    /**
     * Write as much data as possible to the socket.
     *
     * @param data
     * @param off
     * @param len
     * @return  For both blocking and non-blocking, returns the number of bytes
     *          written. If no data can be written (e.g. if the buffers are
     *          full) 0 will be returned.
     * @throws IOException
     */
    public int write(byte[] data, int off, int len, long to) throws IOException {
        long max = System.currentTimeMillis() + to;

        while (true) {
            int rc = writeInternal(data, off, len);
            if (rc < 0) {
                throw new IOException("Write error " + rc);
            } else if (rc == 0) {
                // need poll out - do we need to update polling ?
                context.findPollerAndAdd(this);
            } else {
                return rc;
            }

            try {
                long waitTime = max - System.currentTimeMillis();
                if (waitTime <= 0) {
                    return 0;
                }
                wait(waitTime);
            } catch (InterruptedException e) {
                return 0;
            }
        }
    }

    public int write(byte[] data, int off, int len) throws IOException {
        // In SSL mode, read/write can't be called at the same time.
        int rc = writeInternal(data, off, len);
        if (rc < 0) {
            throw new IOException("Write error " + rc);
        } else if (rc == 0) {
            // need poll out - do we need to update polling ?
            synchronized (this) {
                context.findPollerAndAdd(this);
            }
        }
        return rc;
    }

    private int writeInternal(byte[] data, int off, int len) throws IOException {
        int rt = 0;
        int sent = 0;
        synchronized(this) {
            if ((status & CLOSED) != 0
                    || socket == 0
                    || !context.running) {
                throw new IOException("Closed");
            }
            if ((status & WRITING) != 0) {
                throw new IOException("Write from 2 threads not allowed");
            }
            status |= WRITING;

            while (len > 0) {
                sent = Socket.send(socket, data, off, len);
                if (sent <= 0) {
                    break;
                }
                off += sent;
                len -= sent;
            }

            status &= ~WRITING;
        }

        if (context.rawDataHandler != null) {
            context.rawData(this, false, data, off, sent, len, false);
        }

        if (sent <= 0) {
            if (sent == -Status.TIMEUP || sent == -Status.EAGAIN || sent == 0) {
                setStatus(POLLOUT);
                updatePolling();
                return rt;
            }
            log.warning("apr.send(): Failed to send, closing " + sent);
            reset();
            throw new IOException("Error sending " + sent + " " + Error.strerror(-sent));
        } else {
            off += sent;
            len -= sent;
            rt += sent;
            return sent;
        }
    }

    public int read(byte[] data, int off, int len, long to) throws IOException {
            int rd = readNB(data, off, len);
            if (rd == 0) {
                synchronized(this) {
                    try {
                        wait(to);
                    } catch (InterruptedException e) {
                        return 0;
                    }
                }
                rd = readNB(data, off, len);
            }
            return processReadResult(data, off, len, rd);
    }

    public int read(byte[] data, int off, int len) throws IOException {
        return readNB(data, off, len);
    }

    private int processReadResult(byte[] data, int off, int len, int read)
            throws IOException {
        if (context.rawDataHandler != null) {
            context.rawData(this, true, data, off, read, len, false);
        }

        if (read > 0) {
            return read;
        }

        if (read == 0 || read == -Status.TIMEUP || read == -Status.ETIMEDOUT
                || read == -Status.EAGAIN) {
            read = 0;
            setStatus(POLLIN);
            updatePolling();
            return 0;
        }

        if (read == -Status.APR_EOF || read == -1) {
            close();
            return -1;
        }
        // abrupt close
        reset();
        throw new IOException("apr.read(): " + read + " " + Error.strerror(-read));
    }

    public int readNB(byte[] data, int off, int len) throws IOException {
        int read;
        synchronized(this) {
            if ((status & CLOSED) != 0
                    || socket == 0
                    || !context.running) {
                return -1;
            }
            if ((status & READING) != 0) {
                throw new IOException("Read from 2 threads not allowed");
            }
            status |= READING;

            read = Socket.recv(socket, data, off, len);
            status &= ~READING;
        }
        return processReadResult(data, off, len, read);
    }

    /*
      No support for shutdownOutput: SSL is quite tricky.
      Use close() instead - no read/write will be allowed after.

     */

    public void close() {
        synchronized (this) {
            if ((status & CLOSED) != 0 || socket == 0) {
                return;
            }
            status |= CLOSED;
            status &= ~POLLIN;
            status &= ~POLLOUT;
        }
        if (context.rawDataHandler != null) {
            context.rawDataHandler.rawData(this, false, null, 0, 0, 0, true);
        }
        Socket.close(socket);
        if (poller == null) {
            maybeDestroy();
        } else  {
            try {
                poller.requestUpdate(this);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    void maybeDestroy() {
        synchronized(this) {
            if (socket == 0 ||
                    (status & CONNECTING) != 0 || !context.running) {
                // closed or operation in progress
                // if context stopped, pool will be destroyed and close
                // all sockets automatically.
                return;
            }
            if ((status & CLOSED) == 0) {
                return; // not closed
            }
            if ((status & (WRITING | READING)) != 0) {
                return; // not closed
            }

            if (context.rawDataHandler != null) {
                context.rawDataHandler.rawData(this, false, null, -1, -1, -1, true);
            }
            if (log.isLoggable(Level.FINE)) {
                log.info("closing: context.open=" + context.open.get() + " " + this);
            }

            context.open.decrementAndGet();

            if (socket != 0 && (status & CLOSED) == 0) {
                Socket.close(socket);
                status |= CLOSED;
            }

            if (handler != null) {
                if (isBlocking()) {
                    context.getExecutor().execute(this);
                } else {
                    handler.closed(this);
                }
            }

            context.destroySocket(this);
        }
    }



    /**
     * Close input and output, potentially sending RST, than close the socket.
     *
     * The proper way to close when gracefully done is by calling writeEnd() and
     * reading all remaining input until -1 (EOF) is received.
     *
     * If EOF is received, the proper way to close is send whatever is remaining and
     * call writeEnd();
     */
    public void reset() {
        setStatus(ERROR);
        close();
    }


    /**
     */
    public boolean isClosed() {
        synchronized(this) {
            if ((status & CLOSED) != 0 || socket == 0 || !context.running) {
                return true;
            }
            return false;
        }
    }

    public long getIOTimeout() throws IOException {
        if (socket != 0 && context.running) {
            try {
                return Socket.timeoutGet(socket) / 1000;
            } catch (Exception e) {
                throw new IOException(e);
            }
        } else {
            throw new IOException("Socket is closed");
        }
    }

    // Cert is in DER format
    // Called after handshake
    public byte[][] getPeerCert(boolean check) throws IOException {
        getHost();
        if (hostInfo.certs != null && hostInfo.certs != NO_CERTS && !check) {
            return hostInfo.certs;
        }
        if (!checkBitAndSocket(SSL_ATTACHED)) {
            return NO_CERTS;
        }
        try {
            int certLength = SSLSocket.getInfoI(socket,
                    SSL.SSL_INFO_CLIENT_CERT_CHAIN);
            // TODO: if resumed, old certs are good.
            // If not - warn if certs changed, remember first cert, etc.
            if (certLength <= 0) {
                // Can also happen on session resume - keep the old
                if (hostInfo.certs == null) {
                    hostInfo.certs = NO_CERTS;
                }
                return hostInfo.certs;
            }
            hostInfo.certs = new byte[certLength + 1][];

            hostInfo.certs[0] = SSLSocket.getInfoB(socket,
                    SSL.SSL_INFO_CLIENT_CERT);
            for (int i = 0; i < certLength; i++) {
                hostInfo.certs[i + 1] = SSLSocket.getInfoB(socket,
                        SSL.SSL_INFO_CLIENT_CERT_CHAIN + i);
            }
            return hostInfo.certs;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public X509Certificate[] getPeerX509Cert() throws IOException {
        byte[][] certs = getPeerCert(false);
        X509Certificate[] xcerts = new X509Certificate[certs.length];
        if (certs.length == 0) {
            return xcerts;
        }
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            for (int i = 0; i < certs.length; i++) {
                if (certs[i] != null) {
                    ByteArrayInputStream bis = new ByteArrayInputStream(
                            certs[i]);
                    xcerts[i] = (X509Certificate) cf.generateCertificate(bis);
                    bis.close();
                }
            }
        } catch (CertificateException ex) {
            throw new IOException(ex);
        }
        return xcerts;
    }

    public String getCipherSuite() throws IOException {
        if (checkBitAndSocket(SSL_ATTACHED)) {
            return null;
        }
        try {
            return SSLSocket.getInfoS(socket, SSL.SSL_INFO_CIPHER);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public int getKeySize() throws IOException {
        if (checkBitAndSocket(SSL_ATTACHED)) {
            return -1;
        }
        try {
            return SSLSocket.getInfoI(socket, SSL.SSL_INFO_CIPHER_USEKEYSIZE);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public int getRemotePort() throws IOException {
        if (socket != 0 && context.running) {
            try {
                long sa = Address.get(Socket.APR_REMOTE, socket);
                Sockaddr addr = Address.getInfo(sa);
                return addr.port;
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }
        throw new IOException("Socket closed");
    }

    public String getRemoteAddress() throws IOException {
        if (socket != 0 && context.running) {
            try {
                long sa = Address.get(Socket.APR_REMOTE, socket);
                return Address.getip(sa);
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }
        throw new IOException("Socket closed");
    }

    public String getRemoteHostname() throws IOException {
        if (socket != 0 && context.running) {
            try {
                long sa = Address.get(Socket.APR_REMOTE, socket);
                String remoteHost = Address.getnameinfo(sa, 0);
                if (remoteHost == null) {
                    remoteHost = Address.getip(sa);
                }
                return remoteHost;
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }
        throw new IOException("Socket closed");
    }

    public int getLocalPort() throws IOException {
        if (socket != 0 && context.running) {
            try {
                long sa = Address.get(Socket.APR_LOCAL, socket);
                Sockaddr addr = Address.getInfo(sa);
                return addr.port;
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }
        throw new IOException("Socket closed");
    }

    public String getLocalAddress() throws IOException {
        if (socket != 0 && context.running) {
            try {
                long sa = Address.get(Socket.APR_LOCAL, socket);
                return Address.getip(sa);
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }
        throw new IOException("Socket closed");
    }

    public String getLocalHostname() throws IOException {
        if (socket != 0 && context.running) {
            try {
                long sa = Address.get(Socket.APR_LOCAL, socket);
                return Address.getnameinfo(sa, 0);
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }
        throw new IOException("Socket closed");
    }

    public boolean isBlocking() {
        return ! (handler instanceof AprSocketContext.NonBlockingPollHandler);
    }

    public boolean isError() {
        return checkPreConnect(ERROR);
    }

    void notifyError(Throwable err, boolean needsThread) {
        if (handler instanceof AprSocketContext.NonBlockingPollHandler) {
            if (err != null) {
                ((AprSocketContext.NonBlockingPollHandler) handler).error(this, err);
            }
        } else {
            // poller destroyed, etc
            if (needsThread) {
                context.getExecutor().execute(this);
            } else {
                try {
                    notifyIO();
                } catch (IOException e) {
                    log.log(Level.SEVERE, this + " error ", e);
                }
            }
        }
    }

    // Called after connect and from poller.
    void notifyIO() throws IOException {
        long t0 = System.currentTimeMillis();
        try {
            if (handler != null) {
                handler.process(this, true, false, false);
            }
        } catch (Throwable t) {
            throw new IOException(t);
        } finally {
            long t1 = System.currentTimeMillis();
            t1 -= t0;
            if (t1 > context.maxHandlerTime.get()) {
                context.maxHandlerTime.set(t1);
            }
            context.totalHandlerTime.addAndGet(t1);
            context.handlerCount.incrementAndGet();
        }
    }

    private void notifyConnected(boolean server) throws IOException {
        // Will set the handler on the channel for accepted
        context.onSocket(this);

        if (handler instanceof AprSocketContext.NonBlockingPollHandler) {
            ((AprSocketContext.NonBlockingPollHandler) handler).connected(this);

            ((AprSocketContext.NonBlockingPollHandler) handler).process(this, true, true, false);
            // Now register for polling - unless process() set suspendRead and
            // doesn't need out notifications
            updatePolling();
        } else {
            if (server) {
                // client will block in connect().
                // Server: call process();
                notifyIO();
            }
        }
    }

    private void updatePolling() throws IOException {
        synchronized (this) {
            if ((status & CLOSED) != 0) {
                maybeDestroy();
                return;
            }
        }
        context.findPollerAndAdd(this);
    }

    @Override
    public void run() {
        if (!context.running) {
            return;
        }
        if (checkPreConnect(CLOSED)) {
            if (handler != null) {
                handler.closed(this);
            }
            return;
        }
        if (!checkPreConnect(CONNECTED)) {
            if (checkBitAndSocket(ACCEPTED)) {
                try {
                    context.open.incrementAndGet();

                    if (log.isLoggable(Level.FINE)) {
                        log.info("Accept: " + context.open.get() + " " + this + " " +
                                getRemotePort());
                    }
                    if (context.tcpNoDelay) {
                        Socket.optSet(socket, Socket.APR_TCP_NODELAY, 1);
                    }

                    setStatus(CONNECTED);
                    if (context.sslMode) {
                        Socket.timeoutSet(socket,
                                context.connectTimeout * 1000L);
                        blockingStartTLS();
                    }
                    setNonBlocking(); // call again, to set the bits ( connect was blocking )

                    notifyConnected(true);
                    return;
                } catch (Throwable t) {
                    t.printStackTrace(); // no error handler yet
                    reset();
                    notifyError(t, false);
                    return;
                }
            }
            if (checkPreConnect(CONNECTING)) {
                // Non-blocking connect - will call 'afterConnection' at the end.
                try {
                    context.connectBlocking(this);
                } catch (IOException t) {
                    reset(); // also sets status ERROR
                    if (handler instanceof AprSocketContext.NonBlockingPollHandler) {
                        ((AprSocketContext.NonBlockingPollHandler) handler).process(this, false, false, true);
                    }
                    notifyError(t, false);
                }
            }
        } else {
            if (handler != null) {
                try {
                    notifyIO();
                } catch (Throwable e) {
                    log.log(Level.SEVERE, this + " error ", e);
                    reset();
                    // no notifyIO - just did it.
                }
            }
        }
    }

    /**
     * This is a blocking call ! ( can be made non-blocking, but too complex )
     *
     * Will be called automatically after connect() or accept if 'secure' is
     * true.
     *
     * Can be called manually to upgrade the channel
     * @throws IOException
     */
    public void blockingStartTLS() throws IOException {
        synchronized(this) {
            if (socket == 0 || !context.running) {
                return;
            }
            if ((status & SSL_ATTACHED) != 0) {
                return;
            }
            status |= SSL_ATTACHED;
        }

        try {
            if (log.isLoggable(Level.FINE)) {
                log.info(this + " StartSSL");
            }

            AprSocketContext aprCon = context;
            SSLSocket.attach(aprCon.getSslCtx(), socket);

            if (context.debugSSL) {
                SSLExt.debug(socket);
            }
            if (!getContext().isServer()) {
                if (context.USE_TICKETS && hostInfo.ticketLen > 0) {
                    SSLExt.setTicket(socket, hostInfo.ticket,
                            hostInfo.ticketLen);
                } else if (hostInfo.sessDer != null) {
                    SSLExt.setSessionData(socket, hostInfo.sessDer,
                            hostInfo.sessDer.length);
                }
            }
            SSLExt.sslSetMode(socket, SSLExt.SSL_MODE_ENABLE_PARTIAL_WRITE |
                    SSLExt.SSL_MODE_ACCEPT_MOVING_WRITE_BUFFER);

            int rc = SSLSocket.handshake(socket);

            // At this point we have the session ID, remote certs, etc
            // we can lookup host info
            if (hostInfo == null) {
                hostInfo = new HostInfo();
            }

            if (rc != Status.APR_SUCCESS) {
                throw new IOException(this + " Handshake failed " + rc + " "
                        + Error.strerror(rc) + " SSLL "
                        + SSL.getLastError());
            } else { // SUCCESS
                handshakeDone();
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private void handshakeDone() throws IOException {
        getHost();
        if (socket == 0 || !context.running) {
            throw new IOException("Socket closed");
        }
        if (context.USE_TICKETS && ! context.isServer()) {
            if (hostInfo.ticket == null) {
                hostInfo.ticket = new byte[2048];
            }
            int ticketLen = SSLExt.getTicket(socket, hostInfo.ticket);
            if (ticketLen > 0) {
                hostInfo.ticketLen = ticketLen;
                if (log.isLoggable(Level.FINE)) {
                    log.info("Received ticket: " + ticketLen);
                }
            }
        }

        // TODO: if the ticket, session id or session changed - callback to
        // save the session again
        try {
            hostInfo.sessDer = SSLExt.getSessionData(socket);
            getPeerCert(true);
            hostInfo.sessionId = SSLSocket.getInfoS(socket,
                    SSL.SSL_INFO_SESSION_ID);
        } catch (Exception e) {
            throw new IOException(e);
        }

        hostInfo.npn = new byte[32];
        hostInfo.npnLen = SSLExt.getNPN(socket, hostInfo.npn);

        // If custom verification is used - should check the certificates
        if (context.tlsCertVerifier != null) {
            context.tlsCertVerifier.handshakeDone(this);
        }
    }

    int requestedPolling() {
        synchronized(this) {
            if (socket == 0 || ((status & CLOSED) != 0)) {
                return 0;
            }
            // Implicit:
            //Poll.APR_POLLNVAL | Poll.APR_POLLHUP | Poll.APR_POLLERR |
            int res = 0;
            if ((status & POLLIN) != 0) {
                res = Poll.APR_POLLIN;
            }
            if ((status & POLLOUT) != 0) {
                res |= Poll.APR_POLLOUT;
            }
            return res;
        }
    }

    boolean checkBitAndSocket(int bit) {
        synchronized (this) {
            return ((status & bit) != 0 && socket != 0 &&
                    (status & CLOSED) == 0 && context.running);
        }
    }

    boolean checkPreConnect(int bit) {
        synchronized (this) {
            return ((status & bit) != 0);
        }
    }

    void clearStatus(int bit) {
        synchronized (this) {
            status &= ~bit;
        }
    }

    boolean setStatus(int bit) {
        synchronized (this) {
            int old = status & bit;
            status |= bit;
            return old != 0;
        }
    }


}