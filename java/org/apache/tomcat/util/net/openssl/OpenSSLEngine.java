/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.net.openssl;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionBindingEvent;
import javax.net.ssl.SSLSessionBindingListener;
import javax.net.ssl.SSLSessionContext;
import javax.security.cert.CertificateException;
import javax.security.cert.X509Certificate;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jni.Buffer;
import org.apache.tomcat.jni.Pool;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLContext;
import org.apache.tomcat.util.buf.ByteBufferUtils;
import org.apache.tomcat.util.net.Constants;
import org.apache.tomcat.util.net.SSLUtil;
import org.apache.tomcat.util.res.StringManager;

/**
 * Implements a {@link SSLEngine} using
 * <a href="https://www.openssl.org/docs/crypto/BIO_s_bio.html#EXAMPLE">OpenSSL
 * BIO abstractions</a>.
 */
public final class OpenSSLEngine extends SSLEngine implements SSLUtil.ProtocolInfo {

    private static final Log logger = LogFactory.getLog(OpenSSLEngine.class);
    private static final StringManager sm = StringManager.getManager(OpenSSLEngine.class);

    private static final Certificate[] EMPTY_CERTIFICATES = new Certificate[0];
    private static final SSLException ENGINE_CLOSED = new SSLException(sm.getString("engine.engineClosed"));
    private static final SSLException RENEGOTIATION_UNSUPPORTED = new SSLException(sm.getString("engine.renegociationUnsupported"));
    private static final SSLException ENCRYPTED_PACKET_OVERSIZED = new SSLException(sm.getString("engine.oversizedPacket"));

    private static final Set<String> AVAILABLE_CIPHER_SUITES;

    static {
        final Set<String> availableCipherSuites = new LinkedHashSet<>(128);
        final long aprPool = Pool.create(0);
        try {
            final long sslCtx = SSLContext.make(aprPool, SSL.SSL_PROTOCOL_ALL, SSL.SSL_MODE_SERVER);
            try {
                SSLContext.setOptions(sslCtx, SSL.SSL_OP_ALL);
                SSLContext.setCipherSuite(sslCtx, "ALL");
                final long ssl = SSL.newSSL(sslCtx, true);
                try {
                    for (String c: SSL.getCiphers(ssl)) {
                        // Filter out bad input.
                        if (c == null || c.length() == 0 || availableCipherSuites.contains(c)) {
                            continue;
                        }
                        availableCipherSuites.add(CipherSuiteConverter.toJava(c, "ALL"));
                    }
                } finally {
                    SSL.freeSSL(ssl);
                }
            } finally {
                SSLContext.free(sslCtx);
            }
        } catch (Exception e) {
            logger.warn(sm.getString("engine.ciphersFailure"), e);
        } finally {
            Pool.destroy(aprPool);
        }
        AVAILABLE_CIPHER_SUITES = Collections.unmodifiableSet(availableCipherSuites);
    }

    static {
        ENGINE_CLOSED.setStackTrace(new StackTraceElement[0]);
        RENEGOTIATION_UNSUPPORTED.setStackTrace(new StackTraceElement[0]);
        ENCRYPTED_PACKET_OVERSIZED.setStackTrace(new StackTraceElement[0]);
        DESTROYED_UPDATER = AtomicIntegerFieldUpdater.newUpdater(OpenSSLEngine.class, "destroyed");
        SESSION_UPDATER = AtomicReferenceFieldUpdater.newUpdater(OpenSSLEngine.class, SSLSession.class, "session");
    }

    private static final int MAX_PLAINTEXT_LENGTH = 16 * 1024; // 2^14
    private static final int MAX_COMPRESSED_LENGTH = MAX_PLAINTEXT_LENGTH + 1024;
    private static final int MAX_CIPHERTEXT_LENGTH = MAX_COMPRESSED_LENGTH + 1024;

    // Protocols
    protected static final int VERIFY_DEPTH = 10;

    private static final String[] SUPPORTED_PROTOCOLS = {
        Constants.SSL_PROTO_SSLv2Hello,
        Constants.SSL_PROTO_SSLv2,
        Constants.SSL_PROTO_SSLv3,
        Constants.SSL_PROTO_TLSv1,
        Constants.SSL_PROTO_TLSv1_1,
        Constants.SSL_PROTO_TLSv1_2
    };
    private static final Set<String> SUPPORTED_PROTOCOLS_SET =
            new HashSet<>(Arrays.asList(SUPPORTED_PROTOCOLS));

    // Header (5) + Data (2^14) + Compression (1024) + Encryption (1024) + MAC (20) + Padding (256)
    static final int MAX_ENCRYPTED_PACKET_LENGTH = MAX_CIPHERTEXT_LENGTH + 5 + 20 + 256;

    static final int MAX_ENCRYPTION_OVERHEAD_LENGTH = MAX_ENCRYPTED_PACKET_LENGTH - MAX_PLAINTEXT_LENGTH;

    enum ClientAuthMode {
        NONE,
        OPTIONAL,
        REQUIRE,
    }

    private static final AtomicIntegerFieldUpdater<OpenSSLEngine> DESTROYED_UPDATER;
    private static final AtomicReferenceFieldUpdater<OpenSSLEngine, SSLSession> SESSION_UPDATER;

    private static final String INVALID_CIPHER = "SSL_NULL_WITH_NULL_NULL";

    private static final long EMPTY_ADDR = Buffer.address(ByteBuffer.allocate(0));

    // OpenSSL state
    private long ssl;
    private long networkBIO;

    /**
     * 0 - not accepted, 1 - accepted implicitly via wrap()/unwrap(), 2 -
     * accepted explicitly via beginHandshake() call
     */
    private int accepted;
    private boolean handshakeFinished;
    private boolean receivedShutdown;
    private volatile int destroyed;

    // Use an invalid cipherSuite until the handshake is completed
    // See http://docs.oracle.com/javase/7/docs/api/javax/net/ssl/SSLEngine.html#getSession()
    private volatile String cipher;
    private volatile String applicationProtocol;

    // We store this outside of the SslSession so we not need to create an instance during verifyCertificates(...)
    private volatile Certificate[] peerCerts;
    private volatile ClientAuthMode clientAuth = ClientAuthMode.NONE;

    // SSL Engine status variables
    private boolean isInboundDone;
    private boolean isOutboundDone;
    private boolean engineClosed;

    private final boolean clientMode;
    private final String fallbackApplicationProtocol;
    private final OpenSSLSessionContext sessionContext;
    private final boolean alpn;

    private String selectedProtocol = null;

    private volatile SSLSession session;

    /**
     * Creates a new instance
     *
     * @param sslCtx an OpenSSL {@code SSL_CTX} object
     * @param alloc the {@link ByteBufAllocator} that will be used by this
     * engine
     * @param clientMode {@code true} if this is used for clients, {@code false}
     * otherwise
     * @param sessionContext the {@link OpenSslSessionContext} this
     * {@link SSLEngine} belongs to.
     */
    OpenSSLEngine(long sslCtx, String fallbackApplicationProtocol,
            boolean clientMode, OpenSSLSessionContext sessionContext, boolean alpn) {
        if (sslCtx == 0) {
            throw new IllegalArgumentException(sm.getString("engine.noSSLContext"));
        }
        ssl = SSL.newSSL(sslCtx, !clientMode);
        networkBIO = SSL.makeNetworkBIO(ssl);
        this.fallbackApplicationProtocol = fallbackApplicationProtocol;
        this.clientMode = clientMode;
        this.sessionContext = sessionContext;
        this.alpn = alpn;
    }

    @Override
    public String getNegotiatedProtocol() {
        return selectedProtocol;
    }

    /**
     * Destroys this engine.
     */
    public synchronized void shutdown() {
        if (DESTROYED_UPDATER.compareAndSet(this, 0, 1)) {
            SSL.freeSSL(ssl);
            SSL.freeBIO(networkBIO);
            ssl = networkBIO = 0;

            // internal errors can cause shutdown without marking the engine closed
            isInboundDone = isOutboundDone = engineClosed = true;
        }
    }

    /**
     * Write plaintext data to the OpenSSL internal BIO
     *
     * Calling this function with src.remaining == 0 is undefined.
     */
    private int writePlaintextData(final ByteBuffer src) {
        final int pos = src.position();
        final int limit = src.limit();
        final int len = Math.min(limit - pos, MAX_PLAINTEXT_LENGTH);
        final int sslWrote;

        if (src.isDirect()) {
            final long addr = Buffer.address(src) + pos;
            sslWrote = SSL.writeToSSL(ssl, addr, len);
            if (sslWrote >= 0) {
                src.position(pos + sslWrote);
                return sslWrote;
            }
        } else {
            ByteBuffer buf = ByteBuffer.allocateDirect(len);
            try {
                final long addr = memoryAddress(buf);

                src.limit(pos + len);

                buf.put(src);
                src.limit(limit);

                sslWrote = SSL.writeToSSL(ssl, addr, len);
                if (sslWrote >= 0) {
                    src.position(pos + sslWrote);
                    return sslWrote;
                } else {
                    src.position(pos);
                }
            } finally {
                buf.clear();
                ByteBufferUtils.cleanDirectBuffer(buf);
            }
        }

        throw new IllegalStateException(
                sm.getString("engine.writeToSSLFailed", Integer.toString(sslWrote)));
    }

    /**
     * Write encrypted data to the OpenSSL network BIO.
     */
    private int writeEncryptedData(final ByteBuffer src) {
        final int pos = src.position();
        final int len = src.remaining();
        if (src.isDirect()) {
            final long addr = Buffer.address(src) + pos;
            final int netWrote = SSL.writeToBIO(networkBIO, addr, len);
            if (netWrote >= 0) {
                src.position(pos + netWrote);
                return netWrote;
            }
        } else {
            ByteBuffer buf = ByteBuffer.allocateDirect(len);
            try {
                final long addr = memoryAddress(buf);

                buf.put(src);

                final int netWrote = SSL.writeToBIO(networkBIO, addr, len);
                if (netWrote >= 0) {
                    src.position(pos + netWrote);
                    return netWrote;
                } else {
                    src.position(pos);
                }
            } finally {
                buf.clear();
                ByteBufferUtils.cleanDirectBuffer(buf);
            }
        }

        return -1;
    }

    /**
     * Read plaintext data from the OpenSSL internal BIO
     */
    private int readPlaintextData(final ByteBuffer dst) {
        if (dst.isDirect()) {
            final int pos = dst.position();
            final long addr = Buffer.address(dst) + pos;
            final int len = dst.limit() - pos;
            final int sslRead = SSL.readFromSSL(ssl, addr, len);
            if (sslRead > 0) {
                dst.position(pos + sslRead);
                return sslRead;
            }
        } else {
            final int pos = dst.position();
            final int limit = dst.limit();
            final int len = Math.min(MAX_ENCRYPTED_PACKET_LENGTH, limit - pos);
            final ByteBuffer buf = ByteBuffer.allocateDirect(len);
            try {
                final long addr = memoryAddress(buf);

                final int sslRead = SSL.readFromSSL(ssl, addr, len);
                if (sslRead > 0) {
                    buf.limit(sslRead);
                    dst.limit(pos + sslRead);
                    dst.put(buf);
                    dst.limit(limit);
                    return sslRead;
                }
            } finally {
                buf.clear();
                ByteBufferUtils.cleanDirectBuffer(buf);
            }
        }

        return 0;
    }

    /**
     * Read encrypted data from the OpenSSL network BIO
     */
    private int readEncryptedData(final ByteBuffer dst, final int pending) {
        if (dst.isDirect() && dst.remaining() >= pending) {
            final int pos = dst.position();
            final long addr = Buffer.address(dst) + pos;
            final int bioRead = SSL.readFromBIO(networkBIO, addr, pending);
            if (bioRead > 0) {
                dst.position(pos + bioRead);
                return bioRead;
            }
        } else {
            final ByteBuffer buf = ByteBuffer.allocateDirect(pending);
            try {
                final long addr = memoryAddress(buf);

                final int bioRead = SSL.readFromBIO(networkBIO, addr, pending);
                if (bioRead > 0) {
                    buf.limit(bioRead);
                    int oldLimit = dst.limit();
                    dst.limit(dst.position() + bioRead);
                    dst.put(buf);
                    dst.limit(oldLimit);
                    return bioRead;
                }
            } finally {
                buf.clear();
                ByteBufferUtils.cleanDirectBuffer(buf);
            }
        }

        return 0;
    }

    @Override
    public synchronized SSLEngineResult wrap(final ByteBuffer[] srcs, final int offset, final int length, final ByteBuffer dst) throws SSLException {

        // Check to make sure the engine has not been closed
        if (destroyed != 0) {
            return new SSLEngineResult(SSLEngineResult.Status.CLOSED, SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, 0, 0);
        }

        // Throw required runtime exceptions
        if (srcs == null) {
            throw new IllegalArgumentException(sm.getString("engine.nullBuffer"));
        }
        if (dst == null) {
            throw new IllegalArgumentException(sm.getString("engine.nullBuffer"));
        }

        if (offset >= srcs.length || offset + length > srcs.length) {
            throw new IndexOutOfBoundsException(sm.getString("engine.invalidBufferArray",
                    Integer.toString(offset), Integer.toString(length),
                    Integer.toString(srcs.length)));
        }

        if (dst.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }

        // Prepare OpenSSL to work in server mode and receive handshake
        if (accepted == 0) {
            beginHandshakeImplicitly();
        }

        // In handshake or close_notify stages, check if call to wrap was made
        // without regard to the handshake status.
        SSLEngineResult.HandshakeStatus handshakeStatus = getHandshakeStatus();

        if ((!handshakeFinished || engineClosed) && handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
            return new SSLEngineResult(getEngineStatus(), SSLEngineResult.HandshakeStatus.NEED_UNWRAP, 0, 0);
        }

        int bytesProduced = 0;
        int pendingNet;

        // Check for pending data in the network BIO
        pendingNet = SSL.pendingWrittenBytesInBIO(networkBIO);
        if (pendingNet > 0) {
            // Do we have enough room in dst to write encrypted data?
            int capacity = dst.remaining();
            if (capacity < pendingNet) {
                return new SSLEngineResult(SSLEngineResult.Status.BUFFER_OVERFLOW, handshakeStatus, 0, bytesProduced);
            }

            // Write the pending data from the network BIO into the dst buffer
            try {
                bytesProduced += readEncryptedData(dst, pendingNet);
            } catch (Exception e) {
                throw new SSLException(e);
            }

            // If isOuboundDone is set, then the data from the network BIO
            // was the close_notify message -- we are not required to wait
            // for the receipt the peer's close_notify message -- shutdown.
            if (isOutboundDone) {
                shutdown();
            }

            return new SSLEngineResult(getEngineStatus(), getHandshakeStatus(), 0, bytesProduced);
        }

        // There was no pending data in the network BIO -- encrypt any application data
        int bytesConsumed = 0;
        int endOffset = offset + length;
        for (int i = offset; i < endOffset; ++i) {
            final ByteBuffer src = srcs[i];
            if (src == null) {
                throw new IllegalArgumentException(sm.getString("engine.nullBufferInArray"));
            }
            while (src.hasRemaining()) {

                // Write plaintext application data to the SSL engine
                try {
                    bytesConsumed += writePlaintextData(src);
                } catch (Exception e) {
                    throw new SSLException(e);
                }

                // Check to see if the engine wrote data into the network BIO
                pendingNet = SSL.pendingWrittenBytesInBIO(networkBIO);
                if (pendingNet > 0) {
                    // Do we have enough room in dst to write encrypted data?
                    int capacity = dst.remaining();
                    if (capacity < pendingNet) {
                        return new SSLEngineResult(
                                SSLEngineResult.Status.BUFFER_OVERFLOW, getHandshakeStatus(), bytesConsumed, bytesProduced);
                    }

                    // Write the pending data from the network BIO into the dst buffer
                    try {
                        bytesProduced += readEncryptedData(dst, pendingNet);
                    } catch (Exception e) {
                        throw new SSLException(e);
                    }

                    return new SSLEngineResult(getEngineStatus(), getHandshakeStatus(), bytesConsumed, bytesProduced);
                }
            }
        }
        return new SSLEngineResult(getEngineStatus(), getHandshakeStatus(), bytesConsumed, bytesProduced);
    }

    @Override
    public synchronized SSLEngineResult unwrap(final ByteBuffer src, final ByteBuffer[] dsts, final int offset, final int length) throws SSLException {
        // Check to make sure the engine has not been closed
        if (destroyed != 0) {
            return new SSLEngineResult(SSLEngineResult.Status.CLOSED, SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, 0, 0);
        }

        // Throw requried runtime exceptions
        if (src == null) {
            throw new IllegalArgumentException(sm.getString("engine.nullBuffer"));
        }
        if (dsts == null) {
            throw new IllegalArgumentException(sm.getString("engine.nullBuffer"));
        }
        if (offset >= dsts.length || offset + length > dsts.length) {
            throw new IndexOutOfBoundsException(sm.getString("engine.invalidBufferArray",
                    Integer.toString(offset), Integer.toString(length),
                    Integer.toString(dsts.length)));
        }

        int capacity = 0;
        final int endOffset = offset + length;
        for (int i = offset; i < endOffset; i++) {
            ByteBuffer dst = dsts[i];
            if (dst == null) {
                throw new IllegalArgumentException(sm.getString("engine.nullBufferInArray"));
            }
            if (dst.isReadOnly()) {
                throw new ReadOnlyBufferException();
            }
            capacity += dst.remaining();
        }

        // Prepare OpenSSL to work in server mode and receive handshake
        if (accepted == 0) {
            beginHandshakeImplicitly();
        }

        // In handshake or close_notify stages, check if call to unwrap was made
        // without regard to the handshake status.
        SSLEngineResult.HandshakeStatus handshakeStatus = getHandshakeStatus();
        if ((!handshakeFinished || engineClosed) && handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
            return new SSLEngineResult(getEngineStatus(), SSLEngineResult.HandshakeStatus.NEED_WRAP, 0, 0);
        }

        int len = src.remaining();

        // protect against protocol overflow attack vector
        if (len > MAX_ENCRYPTED_PACKET_LENGTH) {
            isInboundDone = true;
            isOutboundDone = true;
            engineClosed = true;
            shutdown();
            throw ENCRYPTED_PACKET_OVERSIZED;
        }

        // Write encrypted data to network BIO
        int bytesConsumed = -1;
        try {
            int written = writeEncryptedData(src);
            if (written >= 0) {
                if (bytesConsumed == -1) {
                    bytesConsumed = written;
                } else {
                    bytesConsumed += written;
                }
            }
        } catch (Exception e) {
            throw new SSLException(e);
        }
        if (bytesConsumed >= 0) {
            int lastPrimingReadResult = SSL.readFromSSL(ssl, EMPTY_ADDR, 0); // priming read
            // check if SSL_read returned <= 0. In this case we need to check the error and see if it was something
            // fatal.
            if (lastPrimingReadResult <= 0) {
                // Check for OpenSSL errors caused by the priming read
                long error = SSL.getLastErrorNumber();
                if (error != SSL.SSL_ERROR_NONE) {
                    String err = SSL.getErrorString(error);
                    if (logger.isDebugEnabled()) {
                        logger.debug(sm.getString("engine.readFromSSLFailed", Long.toString(error),
                                Integer.toString(lastPrimingReadResult), err));
                    }
                    // There was an internal error -- shutdown
                    shutdown();
                    throw new SSLException(err);
                }
            }
        } else {
            // Reset to 0 as -1 is used to signal that nothing was written and no priming read needs to be done
            bytesConsumed = 0;
        }

        // There won't be any application data until we're done handshaking
        //
        // We first check handshakeFinished to eliminate the overhead of extra JNI call if possible.
        int pendingApp = (handshakeFinished || SSL.isInInit(ssl) == 0) ? SSL.pendingReadableBytesInSSL(ssl) : 0;
        int bytesProduced = 0;

        if (pendingApp > 0) {
            // Do we have enough room in dsts to write decrypted data?
            if (capacity < pendingApp) {
                return new SSLEngineResult(SSLEngineResult.Status.BUFFER_OVERFLOW, getHandshakeStatus(), bytesConsumed, 0);
            }

            // Write decrypted data to dsts buffers
            int idx = offset;
            while (idx < endOffset) {
                ByteBuffer dst = dsts[idx];
                if (!dst.hasRemaining()) {
                    idx++;
                    continue;
                }

                if (pendingApp <= 0) {
                    break;
                }

                int bytesRead;
                try {
                    bytesRead = readPlaintextData(dst);
                } catch (Exception e) {
                    throw new SSLException(e);
                }

                if (bytesRead == 0) {
                    break;
                }

                bytesProduced += bytesRead;
                pendingApp -= bytesRead;

                if (!dst.hasRemaining()) {
                    idx++;
                }
            }
        }

        // Check to see if we received a close_notify message from the peer
        if (!receivedShutdown && (SSL.getShutdown(ssl) & SSL.SSL_RECEIVED_SHUTDOWN) == SSL.SSL_RECEIVED_SHUTDOWN) {
            receivedShutdown = true;
            closeOutbound();
            closeInbound();
        }
        if (bytesProduced == 0 && bytesConsumed == 0) {
            return new SSLEngineResult(SSLEngineResult.Status.BUFFER_UNDERFLOW, getHandshakeStatus(), bytesConsumed, bytesProduced);
        } else {
            return new SSLEngineResult(getEngineStatus(), getHandshakeStatus(), bytesConsumed, bytesProduced);
        }
    }

    @Override
    public Runnable getDelegatedTask() {
        // Currently, we do not delegate SSL computation tasks
        // TODO: in the future, possibly create tasks to do encrypt / decrypt async
        return null;
    }

    @Override
    public synchronized void closeInbound() throws SSLException {
        if (isInboundDone) {
            return;
        }

        isInboundDone = true;
        engineClosed = true;

        shutdown();

        if (accepted != 0 && !receivedShutdown) {
            throw new SSLException(sm.getString("engine.inboundClose"));
        }
    }

    @Override
    public synchronized boolean isInboundDone() {
        return isInboundDone || engineClosed;
    }

    @Override
    public synchronized void closeOutbound() {
        if (isOutboundDone) {
            return;
        }

        isOutboundDone = true;
        engineClosed = true;

        if (accepted != 0 && destroyed == 0) {
            int mode = SSL.getShutdown(ssl);
            if ((mode & SSL.SSL_SENT_SHUTDOWN) != SSL.SSL_SENT_SHUTDOWN) {
                SSL.shutdownSSL(ssl);
            }
        } else {
            // engine closing before initial handshake
            shutdown();
        }
    }

    @Override
    public synchronized boolean isOutboundDone() {
        return isOutboundDone;
    }

    @Override
    public String[] getSupportedCipherSuites() {
        Set<String> availableCipherSuites = AVAILABLE_CIPHER_SUITES;
        return availableCipherSuites.toArray(new String[availableCipherSuites.size()]);
    }

    @Override
    public String[] getEnabledCipherSuites() {
        String[] enabled = SSL.getCiphers(ssl);
        if (enabled == null) {
            return new String[0];
        } else {
            for (int i = 0; i < enabled.length; i++) {
                String mapped = toJavaCipherSuite(enabled[i]);
                if (mapped != null) {
                    enabled[i] = mapped;
                }
            }
            return enabled;
        }
    }

    @Override
    public void setEnabledCipherSuites(String[] cipherSuites) {
        if (cipherSuites == null) {
            throw new IllegalArgumentException(sm.getString("engine.nullCipherSuite"));
        }
        final StringBuilder buf = new StringBuilder();
        for (String cipherSuite : cipherSuites) {
            if (cipherSuite == null) {
                break;
            }
            String converted = CipherSuiteConverter.toOpenSsl(cipherSuite);
            if (converted != null) {
                cipherSuite = converted;
            }
            if (!AVAILABLE_CIPHER_SUITES.contains(cipherSuite)) {
                logger.debug(sm.getString("engine.unsupportedCipher", cipherSuite, converted));
            }

            buf.append(cipherSuite);
            buf.append(':');
        }

        if (buf.length() == 0) {
            throw new IllegalArgumentException(sm.getString("engine.emptyCipherSuite"));
        }
        buf.setLength(buf.length() - 1);

        final String cipherSuiteSpec = buf.toString();
        try {
            SSL.setCipherSuites(ssl, cipherSuiteSpec);
        } catch (Exception e) {
            throw new IllegalStateException(sm.getString("engine.failedCipherSuite", cipherSuiteSpec), e);
        }
    }

    @Override
    public String[] getSupportedProtocols() {
        return SUPPORTED_PROTOCOLS.clone();
    }

    @Override
    public String[] getEnabledProtocols() {
        List<String> enabled = new ArrayList<>();
        // Seems like there is no way to explict disable SSLv2Hello in openssl so it is always enabled
        enabled.add(Constants.SSL_PROTO_SSLv2Hello);
        int opts = SSL.getOptions(ssl);
        if ((opts & SSL.SSL_OP_NO_TLSv1) == 0) {
            enabled.add(Constants.SSL_PROTO_TLSv1);
        }
        if ((opts & SSL.SSL_OP_NO_TLSv1_1) == 0) {
            enabled.add(Constants.SSL_PROTO_TLSv1_1);
        }
        if ((opts & SSL.SSL_OP_NO_TLSv1_2) == 0) {
            enabled.add(Constants.SSL_PROTO_TLSv1_2);
        }
        if ((opts & SSL.SSL_OP_NO_SSLv2) == 0) {
            enabled.add(Constants.SSL_PROTO_SSLv2);
        }
        if ((opts & SSL.SSL_OP_NO_SSLv3) == 0) {
            enabled.add(Constants.SSL_PROTO_SSLv3);
        }
        int size = enabled.size();
        if (size == 0) {
            return new String[0];
        } else {
            return enabled.toArray(new String[size]);
        }
    }

    @Override
    public void setEnabledProtocols(String[] protocols) {
        if (protocols == null) {
            // This is correct from the API docs
            throw new IllegalArgumentException();
        }
        boolean sslv2 = false;
        boolean sslv3 = false;
        boolean tlsv1 = false;
        boolean tlsv1_1 = false;
        boolean tlsv1_2 = false;
        for (String p : protocols) {
            if (!SUPPORTED_PROTOCOLS_SET.contains(p)) {
                throw new IllegalArgumentException(sm.getString("engine.unsupportedProtocol", p));
            }
            if (p.equals(Constants.SSL_PROTO_SSLv2)) {
                sslv2 = true;
            } else if (p.equals(Constants.SSL_PROTO_SSLv3)) {
                sslv3 = true;
            } else if (p.equals(Constants.SSL_PROTO_TLSv1)) {
                tlsv1 = true;
            } else if (p.equals(Constants.SSL_PROTO_TLSv1_1)) {
                tlsv1_1 = true;
            } else if (p.equals(Constants.SSL_PROTO_TLSv1_2)) {
                tlsv1_2 = true;
            }
        }
        // Enable all and then disable what we not want
        SSL.setOptions(ssl, SSL.SSL_OP_ALL);

        if (!sslv2) {
            SSL.setOptions(ssl, SSL.SSL_OP_NO_SSLv2);
        }
        if (!sslv3) {
            SSL.setOptions(ssl, SSL.SSL_OP_NO_SSLv3);
        }
        if (!tlsv1) {
            SSL.setOptions(ssl, SSL.SSL_OP_NO_TLSv1);
        }
        if (!tlsv1_1) {
            SSL.setOptions(ssl, SSL.SSL_OP_NO_TLSv1_1);
        }
        if (!tlsv1_2) {
            SSL.setOptions(ssl, SSL.SSL_OP_NO_TLSv1_2);
        }
    }

    private Certificate[] initPeerCertChain() throws SSLPeerUnverifiedException {
        byte[][] chain = SSL.getPeerCertChain(ssl);
        byte[] clientCert;
        if (!clientMode) {
            // if used on the server side SSL_get_peer_cert_chain(...) will not include the remote peer certificate.
            // We use SSL_get_peer_certificate to get it in this case and add it to our array later.
            //
            // See https://www.openssl.org/docs/ssl/SSL_get_peer_cert_chain.html
            clientCert = SSL.getPeerCertificate(ssl);
        } else {
            clientCert = null;
        }

        if (chain == null && clientCert == null) {
            throw new SSLPeerUnverifiedException(sm.getString("engine.unverifiedPeer"));
        }
        int len = 0;
        if (chain != null) {
            len += chain.length;
        }

        int i = 0;
        Certificate[] peerCerts;
        if (clientCert != null) {
            len++;
            peerCerts = new Certificate[len];
            peerCerts[i++] = new OpenSslX509Certificate(clientCert);
        } else {
            peerCerts = new Certificate[len];
        }
        if (chain != null) {
            int a = 0;
            for (; i < peerCerts.length; i++) {
                peerCerts[i] = new OpenSslX509Certificate(chain[a++]);
            }
        }
        return peerCerts;
    }

    @Override
    public SSLSession getSession() {
        // A other methods on SSLEngine are thread-safe we also need to make this thread-safe...
        SSLSession session = this.session;
        if (session == null) {
            session = new SSLSession() {
                // SSLSession implementation seems to not need to be thread-safe so no need for volatile etc.
                private X509Certificate[] x509PeerCerts;

                // lazy init for memory reasons
                private Map<String, Object> values;

                @Override
                public byte[] getId() {
                    // We don't cache that to keep memory usage to a minimum.
                    byte[] id = SSL.getSessionId(ssl);
                    if (id == null) {
                        // The id should never be null, if it was null then the SESSION itself was not valid.
                        throw new IllegalStateException(sm.getString("engine.noSession"));
                    }
                    return id;
                }

                @Override
                public SSLSessionContext getSessionContext() {
                    return sessionContext;
                }

                @Override
                public long getCreationTime() {
                    // We need ot multiple by 1000 as openssl uses seconds and we need milli-seconds.
                    return SSL.getTime(ssl) * 1000L;
                }

                @Override
                public long getLastAccessedTime() {
                    // TODO: Add proper implementation
                    return getCreationTime();
                }

                @Override
                public void invalidate() {
                    // NOOP
                }

                @Override
                public boolean isValid() {
                    return false;
                }

                @Override
                public void putValue(String name, Object value) {
                    if (name == null) {
                        throw new IllegalArgumentException(sm.getString("engine.nullName"));
                    }
                    if (value == null) {
                        throw new IllegalArgumentException(sm.getString("engine.nullValue"));
                    }
                    Map<String, Object> values = this.values;
                    if (values == null) {
                        // Use size of 2 to keep the memory overhead small
                        values = this.values = new HashMap<>(2);
                    }
                    Object old = values.put(name, value);
                    if (value instanceof SSLSessionBindingListener) {
                        ((SSLSessionBindingListener) value).valueBound(new SSLSessionBindingEvent(this, name));
                    }
                    notifyUnbound(old, name);
                }

                @Override
                public Object getValue(String name) {
                    if (name == null) {
                        throw new IllegalArgumentException(sm.getString("engine.nullName"));
                    }
                    if (values == null) {
                        return null;
                    }
                    return values.get(name);
                }

                @Override
                public void removeValue(String name) {
                    if (name == null) {
                        throw new IllegalArgumentException(sm.getString("engine.nullName"));
                    }
                    Map<String, Object> values = this.values;
                    if (values == null) {
                        return;
                    }
                    Object old = values.remove(name);
                    notifyUnbound(old, name);
                }

                @Override
                public String[] getValueNames() {
                    Map<String, Object> values = this.values;
                    if (values == null || values.isEmpty()) {
                        return new String[0];
                    }
                    return values.keySet().toArray(new String[values.size()]);
                }

                private void notifyUnbound(Object value, String name) {
                    if (value instanceof SSLSessionBindingListener) {
                        ((SSLSessionBindingListener) value).valueUnbound(new SSLSessionBindingEvent(this, name));
                    }
                }

                @Override
                public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
                    // these are lazy created to reduce memory overhead
                    Certificate[] c = peerCerts;
                    if (c == null) {
                        if (SSL.isInInit(ssl) != 0) {
                            throw new SSLPeerUnverifiedException(sm.getString("engine.unverifiedPeer"));
                        }
                        c = peerCerts = initPeerCertChain();
                    }
                    return c;
                }

                @Override
                public Certificate[] getLocalCertificates() {
                    // TODO: Find out how to get these
                    return EMPTY_CERTIFICATES;
                }

                @Override
                public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
                    // these are lazy created to reduce memory overhead
                    X509Certificate[] c = x509PeerCerts;
                    if (c == null) {
                        if (SSL.isInInit(ssl) != 0) {
                            throw new SSLPeerUnverifiedException(sm.getString("engine.unverifiedPeer"));
                        }
                        byte[][] chain = SSL.getPeerCertChain(ssl);
                        if (chain == null) {
                            throw new SSLPeerUnverifiedException(sm.getString("engine.unverifiedPeer"));
                        }
                        X509Certificate[] peerCerts = new X509Certificate[chain.length];
                        for (int i = 0; i < peerCerts.length; i++) {
                            try {
                                peerCerts[i] = X509Certificate.getInstance(chain[i]);
                            } catch (CertificateException e) {
                                throw new IllegalStateException(e);
                            }
                        }
                        c = x509PeerCerts = peerCerts;
                    }
                    return c;
                }

                @Override
                public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
                    Certificate[] peer = getPeerCertificates();
                    if (peer == null || peer.length == 0) {
                        return null;
                    }
                    return principal(peer);
                }

                @Override
                public Principal getLocalPrincipal() {
                    Certificate[] local = getLocalCertificates();
                    if (local == null || local.length == 0) {
                        return null;
                    }
                    return principal(local);
                }

                private Principal principal(Certificate[] certs) {
                    return ((java.security.cert.X509Certificate) certs[0]).getIssuerX500Principal();
                }

                @Override
                public String getCipherSuite() {
                    if (!handshakeFinished) {
                        return INVALID_CIPHER;
                    }
                    if (cipher == null) {
                        String c = toJavaCipherSuite(SSL.getCipherForSSL(ssl));
                        if (c != null) {
                            cipher = c;
                        }
                    }
                    return cipher;
                }

                @Override
                public String getProtocol() {
                    String applicationProtocol = OpenSSLEngine.this.applicationProtocol;
                    if (applicationProtocol == null) {
                        applicationProtocol = SSL.getNextProtoNegotiated(ssl);
                        if (applicationProtocol == null) {
                            applicationProtocol = fallbackApplicationProtocol;
                        }
                        if (applicationProtocol != null) {
                            OpenSSLEngine.this.applicationProtocol = applicationProtocol.replace(':', '_');
                        } else {
                            OpenSSLEngine.this.applicationProtocol = applicationProtocol = "";
                        }
                    }
                    String version = SSL.getVersion(ssl);
                    if (applicationProtocol.isEmpty()) {
                        return version;
                    } else {
                        return version + ':' + applicationProtocol;
                    }
                }

                @Override
                public String getPeerHost() {
                    return null;
                }

                @Override
                public int getPeerPort() {
                    return 0;
                }

                @Override
                public int getPacketBufferSize() {
                    return MAX_ENCRYPTED_PACKET_LENGTH;
                }

                @Override
                public int getApplicationBufferSize() {
                    return MAX_PLAINTEXT_LENGTH;
                }
            };

            if (!SESSION_UPDATER.compareAndSet(this, null, session)) {
                // Was lazy created in the meantime so get the current reference.
                session = this.session;
            }
        }

        return session;
    }

    @Override
    public synchronized void beginHandshake() throws SSLException {
        if (engineClosed || destroyed != 0) {
            throw ENGINE_CLOSED;
        }
        switch (accepted) {
            case 0:
                handshake();
                accepted = 2;
                break;
            case 1:
                // A user did not start handshake by calling this method by him/herself,
                // but handshake has been started already by wrap() or unwrap() implicitly.
                // Because it's the user's first time to call this method, it is unfair to
                // raise an exception.  From the user's standpoint, he or she never asked
                // for renegotiation.

                accepted = 2; // Next time this method is invoked by the user, we should raise an exception.
                break;
            case 2:
                throw RENEGOTIATION_UNSUPPORTED;
            default:
                throw new Error();
        }
    }

    private void beginHandshakeImplicitly() throws SSLException {
        if (engineClosed || destroyed != 0) {
            throw ENGINE_CLOSED;
        }

        if (accepted == 0) {
            handshake();
            accepted = 1;
        }
    }

    private void handshake() throws SSLException {
        int code = SSL.doHandshake(ssl);
        if (code <= 0) {
            // Check for OpenSSL errors caused by the handshake
            long error = SSL.getLastErrorNumber();
            if (error != SSL.SSL_ERROR_NONE) {
                String err = SSL.getErrorString(error);
                if (logger.isDebugEnabled()) {
                    logger.debug(sm.getString("engine.handshakeFailure", err));
                }
                // There was an internal error -- shutdown
                shutdown();
                throw new SSLException(err);
            }
        } else {
            if (alpn) {
                selectedProtocol = SSL.getAlpnSelected(ssl);
                if (selectedProtocol == null) {
                    selectedProtocol = SSL.getNextProtoNegotiated(ssl);
                }
            }
            // if SSL_do_handshake returns > 0 it means the handshake was finished. This means we can update
            // handshakeFinished directly and so eliminate unnecessary calls to SSL.isInInit(...)
            handshakeFinished = true;
        }
    }

    private static long memoryAddress(ByteBuffer buf) {
        return Buffer.address(buf);
    }

    private SSLEngineResult.Status getEngineStatus() {
        return engineClosed ? SSLEngineResult.Status.CLOSED : SSLEngineResult.Status.OK;
    }

    @Override
    public synchronized SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        if (accepted == 0 || destroyed != 0) {
            return SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
        }

        // Check if we are in the initial handshake phase
        if (!handshakeFinished) {
            // There is pending data in the network BIO -- call wrap
            if (SSL.pendingWrittenBytesInBIO(networkBIO) != 0) {
                return SSLEngineResult.HandshakeStatus.NEED_WRAP;
            }

            // No pending data to be sent to the peer
            // Check to see if we have finished handshaking
            if (SSL.isInInit(ssl) == 0) {
                if (alpn) {
                    selectedProtocol = SSL.getAlpnSelected(ssl);
                    if (selectedProtocol == null) {
                        selectedProtocol = SSL.getNextProtoNegotiated(ssl);
                    }
                }
                handshakeFinished = true;
                return SSLEngineResult.HandshakeStatus.FINISHED;
            }

            // No pending data and still handshaking
            // Must be waiting on the peer to send more data
            return SSLEngineResult.HandshakeStatus.NEED_UNWRAP;
        }

        // Check if we are in the shutdown phase
        if (engineClosed) {
            // Waiting to send the close_notify message
            if (SSL.pendingWrittenBytesInBIO(networkBIO) != 0) {
                return SSLEngineResult.HandshakeStatus.NEED_WRAP;
            }

            // Must be waiting to receive the close_notify message
            return SSLEngineResult.HandshakeStatus.NEED_UNWRAP;
        }

        return SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
    }

    /**
     * Converts the specified OpenSSL cipher suite to the Java cipher suite.
     */
    private String toJavaCipherSuite(String openSslCipherSuite) {
        if (openSslCipherSuite == null) {
            return null;
        }

        String prefix = toJavaCipherSuitePrefix(SSL.getVersion(ssl));
        return CipherSuiteConverter.toJava(openSslCipherSuite, prefix);
    }

    /**
     * Converts the protocol version string returned by
     * {@link SSL#getVersion(long)} to protocol family string.
     */
    private static String toJavaCipherSuitePrefix(String protocolVersion) {
        final char c;
        if (protocolVersion == null || protocolVersion.length() == 0) {
            c = 0;
        } else {
            c = protocolVersion.charAt(0);
        }

        switch (c) {
            case 'T':
                return "TLS";
            case 'S':
                return "SSL";
            default:
                return "UNKNOWN";
        }
    }

    @Override
    public void setUseClientMode(boolean clientMode) {
        if (clientMode != this.clientMode) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean getUseClientMode() {
        return clientMode;
    }

    @Override
    public void setNeedClientAuth(boolean b) {
        setClientAuth(b ? ClientAuthMode.REQUIRE : ClientAuthMode.NONE);
    }

    @Override
    public boolean getNeedClientAuth() {
        return clientAuth == ClientAuthMode.REQUIRE;
    }

    @Override
    public void setWantClientAuth(boolean b) {
        setClientAuth(b ? ClientAuthMode.OPTIONAL : ClientAuthMode.NONE);
    }

    @Override
    public boolean getWantClientAuth() {
        return clientAuth == ClientAuthMode.OPTIONAL;
    }

    private void setClientAuth(ClientAuthMode mode) {
        if (clientMode) {
            return;
        }
        synchronized (this) {
            if (clientAuth == mode) {
                // No need to issue any JNI calls if the mode is the same
                return;
            }
            switch (mode) {
                case NONE:
                    SSL.setVerify(ssl, SSL.SSL_CVERIFY_NONE, VERIFY_DEPTH);
                    break;
                case REQUIRE:
                    SSL.setVerify(ssl, SSL.SSL_CVERIFY_REQUIRE, VERIFY_DEPTH);
                    break;
                case OPTIONAL:
                    SSL.setVerify(ssl, SSL.SSL_CVERIFY_OPTIONAL, VERIFY_DEPTH);
                    break;
            }
            clientAuth = mode;
        }
    }

    @Override
    public void setEnableSessionCreation(boolean b) {
        if (b) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean getEnableSessionCreation() {
        return false;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        // Call shutdown as the user may have created the OpenSslEngine and not used it at all.
        shutdown();
    }
}
