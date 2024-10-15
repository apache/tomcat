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

import java.lang.ref.Cleaner;
import java.lang.ref.Cleaner.Cleanable;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionBindingEvent;
import javax.net.ssl.SSLSessionBindingListener;
import javax.net.ssl.SSLSessionContext;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jni.Buffer;
import org.apache.tomcat.jni.Pool;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLContext;
import org.apache.tomcat.util.buf.ByteBufferUtils;
import org.apache.tomcat.util.net.Constants;
import org.apache.tomcat.util.net.SSLUtil;
import org.apache.tomcat.util.net.openssl.ciphers.OpenSSLCipherConfigurationParser;
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

    public static final Set<String> AVAILABLE_CIPHER_SUITES;

    public static final Set<String> IMPLEMENTED_PROTOCOLS_SET;

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
                        availableCipherSuites.add(OpenSSLCipherConfigurationParser.openSSLToJsse(c));
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

        HashSet<String> protocols = new HashSet<>();
        protocols.add(Constants.SSL_PROTO_SSLv2Hello);
        protocols.add(Constants.SSL_PROTO_SSLv2);
        protocols.add(Constants.SSL_PROTO_SSLv3);
        protocols.add(Constants.SSL_PROTO_TLSv1);
        protocols.add(Constants.SSL_PROTO_TLSv1_1);
        protocols.add(Constants.SSL_PROTO_TLSv1_2);
        if (SSL.version() >= 0x1010100f) {
            protocols.add(Constants.SSL_PROTO_TLSv1_3);
        }

        IMPLEMENTED_PROTOCOLS_SET = Collections.unmodifiableSet(protocols);
    }

    private static final int MAX_PLAINTEXT_LENGTH = 16 * 1024; // 2^14
    private static final int MAX_COMPRESSED_LENGTH = MAX_PLAINTEXT_LENGTH + 1024;
    private static final int MAX_CIPHERTEXT_LENGTH = MAX_COMPRESSED_LENGTH + 1024;

    // Protocols
    static final int VERIFY_DEPTH = 10;

    // Header (5) + Data (2^14) + Compression (1024) + Encryption (1024) + MAC (20) + Padding (256)
    static final int MAX_ENCRYPTED_PACKET_LENGTH = MAX_CIPHERTEXT_LENGTH + 5 + 20 + 256;

    static final int MAX_ENCRYPTION_OVERHEAD_LENGTH = MAX_ENCRYPTED_PACKET_LENGTH - MAX_PLAINTEXT_LENGTH;

    enum ClientAuthMode {
        NONE,
        OPTIONAL,
        REQUIRE,
    }

    private static final String INVALID_CIPHER = "SSL_NULL_WITH_NULL_NULL";

    private static final long EMPTY_ADDR = Buffer.address(ByteBuffer.allocate(0));

    private final OpenSSLState state;
    private final Cleanable cleanable;
    private ByteBuffer buf = ByteBuffer.allocateDirect(MAX_ENCRYPTED_PACKET_LENGTH);

    private enum Accepted { NOT, IMPLICIT, EXPLICIT }
    private Accepted accepted = Accepted.NOT;
    private boolean handshakeFinished;
    private int currentHandshake;
    private boolean receivedShutdown;
    private volatile boolean destroyed;

    // Use an invalid cipherSuite until the handshake is completed
    // See https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SSLEngine.html#getSession()
    private volatile String version;
    private volatile String cipher;
    private volatile String applicationProtocol;

    private volatile Certificate[] peerCerts;
    private volatile ClientAuthMode clientAuth = ClientAuthMode.NONE;

    // SSL Engine status variables
    private boolean isInboundDone;
    private boolean isOutboundDone;
    private boolean engineClosed;
    private boolean sendHandshakeError = false;

    private final boolean clientMode;
    private final String fallbackApplicationProtocol;
    private final OpenSSLSessionContext sessionContext;
    private final boolean alpn;
    private final boolean initialized;
    private final int certificateVerificationDepth;
    private final boolean certificateVerificationOptionalNoCA;

    private String selectedProtocol = null;

    private final OpenSSLSession session;

    /**
     * Creates a new instance
     *
     * @param cleaner   Used to clean up references to instances before they are
     *                  garbage collected
     * @param sslCtx an OpenSSL {@code SSL_CTX} object
     * @param fallbackApplicationProtocol the fallback application protocol
     * @param clientMode {@code true} if this is used for clients, {@code false}
     * otherwise
     * @param sessionContext the {@link OpenSSLSessionContext} this
     * {@link SSLEngine} belongs to.
     * @param alpn {@code true} if alpn should be used, {@code false}
     * otherwise
     * @param initialized {@code true} if this instance gets its protocol,
     * cipher and client verification from the {@code SSL_CTX} {@code sslCtx}
     * @param certificateVerificationDepth Certificate verification depth
     * @param certificateVerificationOptionalNoCA Skip CA verification in
     *   optional mode
     */
    OpenSSLEngine(Cleaner cleaner, long sslCtx, String fallbackApplicationProtocol,
            boolean clientMode, OpenSSLSessionContext sessionContext, boolean alpn,
            boolean initialized, int certificateVerificationDepth,
            boolean certificateVerificationOptionalNoCA) {
        if (sslCtx == 0) {
            throw new IllegalArgumentException(sm.getString("engine.noSSLContext"));
        }
        session = new OpenSSLSession();
        long ssl = SSL.newSSL(sslCtx, !clientMode);
        long networkBIO = SSL.makeNetworkBIO(ssl);
        state = new OpenSSLState(ssl, networkBIO);
        cleanable = cleaner.register(this, state);
        this.fallbackApplicationProtocol = fallbackApplicationProtocol;
        this.clientMode = clientMode;
        this.sessionContext = sessionContext;
        this.alpn = alpn;
        this.initialized = initialized;
        this.certificateVerificationDepth = certificateVerificationDepth;
        this.certificateVerificationOptionalNoCA = certificateVerificationOptionalNoCA;
    }

    @Override
    public String getNegotiatedProtocol() {
        return selectedProtocol;
    }

    /**
     * Destroys this engine.
     */
    public synchronized void shutdown() {
        if (!destroyed) {
            destroyed = true;
            cleanable.clean();
            // internal errors can cause shutdown without marking the engine closed
            isInboundDone = isOutboundDone = engineClosed = true;
            ByteBufferUtils.cleanDirectBuffer(buf);
        }
    }

    /**
     * Write plain text data to the OpenSSL internal BIO
     *
     * Calling this function with src.remaining == 0 is undefined.
     * @throws SSLException if the OpenSSL error check fails
     */
    private int writePlaintextData(final long ssl, final ByteBuffer src) throws SSLException {
        clearLastError();
        final int pos = src.position();
        final int limit = src.limit();
        final int len = Math.min(limit - pos, MAX_PLAINTEXT_LENGTH);
        final int sslWrote;

        if (src.isDirect()) {
            final long addr = Buffer.address(src) + pos;
            sslWrote = SSL.writeToSSL(ssl, addr, len);
            if (sslWrote <= 0) {
                checkLastError();
            }
            if (sslWrote >= 0) {
                src.position(pos + sslWrote);
                return sslWrote;
            }
        } else {
            try {
                final long addr = Buffer.address(buf);

                src.limit(pos + len);

                buf.put(src);
                src.limit(limit);

                sslWrote = SSL.writeToSSL(ssl, addr, len);
                if (sslWrote <= 0) {
                    checkLastError();
                }
                if (sslWrote >= 0) {
                    src.position(pos + sslWrote);
                    return sslWrote;
                } else {
                    src.position(pos);
                }
            } finally {
                buf.clear();
            }
        }

        throw new IllegalStateException(
                sm.getString("engine.writeToSSLFailed", Integer.toString(sslWrote)));
    }

    /**
     * Write encrypted data to the OpenSSL network BIO.
     * @throws SSLException if the OpenSSL error check fails
     */
    private int writeEncryptedData(final long networkBIO, final ByteBuffer src) throws SSLException {
        clearLastError();
        final int pos = src.position();
        final int len = src.remaining();
        if (src.isDirect()) {
            final long addr = Buffer.address(src) + pos;
            final int netWrote = SSL.writeToBIO(networkBIO, addr, len);
            if (netWrote <= 0) {
                checkLastError();
            }
            if (netWrote >= 0) {
                src.position(pos + netWrote);
                return netWrote;
            }
        } else {
            try {
                final long addr = Buffer.address(buf);

                buf.put(src);

                final int netWrote = SSL.writeToBIO(networkBIO, addr, len);
                if (netWrote <= 0) {
                    checkLastError();
                }
                if (netWrote >= 0) {
                    src.position(pos + netWrote);
                    return netWrote;
                } else {
                    src.position(pos);
                }
            } finally {
                buf.clear();
            }
        }

        return 0;
    }

    /**
     * Read plain text data from the OpenSSL internal BIO
     * @throws SSLException if the OpenSSL error check fails
     */
    private int readPlaintextData(final long ssl, final ByteBuffer dst) throws SSLException {
        clearLastError();
        if (dst.isDirect()) {
            final int pos = dst.position();
            final long addr = Buffer.address(dst) + pos;
            final int len = dst.limit() - pos;
            final int sslRead = SSL.readFromSSL(ssl, addr, len);
            if (sslRead > 0) {
                dst.position(pos + sslRead);
                return sslRead;
            } else {
                checkLastError();
            }
        } else {
            final int pos = dst.position();
            final int limit = dst.limit();
            final int len = Math.min(MAX_ENCRYPTED_PACKET_LENGTH, limit - pos);
            try {
                final long addr = Buffer.address(buf);

                final int sslRead = SSL.readFromSSL(ssl, addr, len);
                if (sslRead > 0) {
                    buf.limit(sslRead);
                    dst.limit(pos + sslRead);
                    dst.put(buf);
                    dst.limit(limit);
                    return sslRead;
                } else {
                    checkLastError();
                }
            } finally {
                buf.clear();
            }
        }

        return 0;
    }

    /**
     * Read encrypted data from the OpenSSL network BIO
     * @throws SSLException if the OpenSSL error check fails
     */
    private int readEncryptedData(final long networkBIO, final ByteBuffer dst, final int pending) throws SSLException {
        clearLastError();
        if (dst.isDirect() && dst.remaining() >= pending) {
            final int pos = dst.position();
            final long addr = Buffer.address(dst) + pos;
            final int bioRead = SSL.readFromBIO(networkBIO, addr, pending);
            if (bioRead > 0) {
                dst.position(pos + bioRead);
                return bioRead;
            } else {
                checkLastError();
            }
        } else {
            try {
                final long addr = Buffer.address(buf);

                final int bioRead = SSL.readFromBIO(networkBIO, addr, pending);
                if (bioRead > 0) {
                    buf.limit(bioRead);
                    int oldLimit = dst.limit();
                    dst.limit(dst.position() + bioRead);
                    dst.put(buf);
                    dst.limit(oldLimit);
                    return bioRead;
                } else {
                    checkLastError();
                }
            } finally {
                buf.clear();
            }
        }

        return 0;
    }

    @Override
    public synchronized SSLEngineResult wrap(final ByteBuffer[] srcs, final int offset, final int length, final ByteBuffer dst) throws SSLException {

        // Check to make sure the engine has not been closed
        if (destroyed) {
            return new SSLEngineResult(SSLEngineResult.Status.CLOSED, SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, 0, 0);
        }

        // Throw required runtime exceptions
        if (srcs == null || dst == null) {
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
        if (accepted == Accepted.NOT) {
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
        pendingNet = SSL.pendingWrittenBytesInBIO(state.networkBIO);
        if (pendingNet > 0) {
            // Do we have enough room in destination to write encrypted data?
            int capacity = dst.remaining();
            if (capacity < pendingNet) {
                return new SSLEngineResult(SSLEngineResult.Status.BUFFER_OVERFLOW, handshakeStatus, 0, 0);
            }

            // Write the pending data from the network BIO into the dst buffer
            try {
                bytesProduced = readEncryptedData(state.networkBIO, dst, pendingNet);
            } catch (Exception e) {
                throw new SSLException(e);
            }

            // If isOutboundDone is set, then the data from the network BIO
            // was the close_notify message -- we are not required to wait
            // for the receipt of the peer's close_notify message -- shutdown.
            if (isOutboundDone()) {
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

                // Write plain text application data to the SSL engine
                try {
                    bytesConsumed += writePlaintextData(state.ssl, src);
                } catch (Exception e) {
                    throw new SSLException(e);
                }

                // Check to see if the engine wrote data into the network BIO
                pendingNet = SSL.pendingWrittenBytesInBIO(state.networkBIO);
                if (pendingNet > 0) {
                    // Do we have enough room in dst to write encrypted data?
                    int capacity = dst.remaining();
                    if (capacity < pendingNet) {
                        return new SSLEngineResult(
                                SSLEngineResult.Status.BUFFER_OVERFLOW, getHandshakeStatus(), bytesConsumed, bytesProduced);
                    }

                    // Write the pending data from the network BIO into the dst buffer
                    try {
                        bytesProduced += readEncryptedData(state.networkBIO, dst, pendingNet);
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
        if (destroyed) {
            return new SSLEngineResult(SSLEngineResult.Status.CLOSED, SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, 0, 0);
        }

        // Throw required runtime exceptions
        if (src == null || dsts == null) {
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
        if (accepted == Accepted.NOT) {
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
            shutdown();
            throw new SSLException(sm.getString("engine.oversizedPacket"));
        }

        // Write encrypted data to network BIO
        int written = 0;
        try {
            written = writeEncryptedData(state.networkBIO, src);
        } catch (Exception e) {
            throw new SSLException(e);
        }

        // There won't be any application data until we're done handshaking
        //
        // We first check handshakeFinished to eliminate the overhead of extra JNI call if possible.
        int pendingApp = pendingReadableBytesInSSL();
        if (!handshakeFinished) {
            pendingApp = 0;
        }
        int bytesProduced = 0;
        int idx = offset;
        // Do we have enough room in dsts to write decrypted data?
        if (capacity == 0) {
            return new SSLEngineResult(SSLEngineResult.Status.BUFFER_OVERFLOW, getHandshakeStatus(), written, 0);
        }

        while (pendingApp > 0) {
            if (idx == endOffset) {
                // Destination buffer state changed (no remaining space although
                // capacity is still available), so break loop with an error
                throw new IllegalStateException(sm.getString("engine.invalidDestinationBuffersState"));
            }
            // Write decrypted data to dsts buffers
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
                    bytesRead = readPlaintextData(state.ssl, dst);
                } catch (Exception e) {
                    throw new SSLException(e);
                }

                if (bytesRead == 0) {
                    // This should not be possible. pendingApp is positive
                    // therefore the read should have read at least one byte.
                    throw new IllegalStateException(sm.getString("engine.failedToReadAvailableBytes"));
                }

                bytesProduced += bytesRead;
                pendingApp -= bytesRead;
                capacity -= bytesRead;

                if (!dst.hasRemaining()) {
                    idx++;
                }
            }
            if (capacity == 0) {
                break;
            } else if (pendingApp == 0) {
                pendingApp = pendingReadableBytesInSSL();
            }
        }

        // Check to see if we received a close_notify message from the peer
        if (!receivedShutdown && (SSL.getShutdown(state.ssl) & SSL.SSL_RECEIVED_SHUTDOWN) == SSL.SSL_RECEIVED_SHUTDOWN) {
            receivedShutdown = true;
            closeInbound();
        }
        if (bytesProduced == 0 && (written == 0 || (written > 0 && !src.hasRemaining() && handshakeFinished))) {
            return new SSLEngineResult(SSLEngineResult.Status.BUFFER_UNDERFLOW, getHandshakeStatus(), written, 0);
        } else {
            return new SSLEngineResult(getEngineStatus(), getHandshakeStatus(), written, bytesProduced);
        }
    }

    private int pendingReadableBytesInSSL()
            throws SSLException {
        // NOTE: Calling a fake read is necessary before calling pendingReadableBytesInSSL because
        // SSL_pending will return 0 if OpenSSL has not started the current TLS record
        // See https://www.openssl.org/docs/manmaster/man3/SSL_pending.html
        clearLastError();
        int lastPrimingReadResult = SSL.readFromSSL(state.ssl, EMPTY_ADDR, 0); // priming read
        // check if SSL_read returned <= 0. In this case we need to check the error and see if it was something
        // fatal.
        if (lastPrimingReadResult <= 0) {
            checkLastError();
        }
        int pendingReadableBytesInSSL = SSL.pendingReadableBytesInSSL(state.ssl);

        // TLS 1.0 needs additional handling
        // TODO Figure out why this is necessary and if a simpler / better
        // solution is available
        if (Constants.SSL_PROTO_TLSv1.equals(version) && lastPrimingReadResult == 0 &&
                pendingReadableBytesInSSL == 0) {
            // Perform another priming read
            lastPrimingReadResult = SSL.readFromSSL(state.ssl, EMPTY_ADDR, 0);
            if (lastPrimingReadResult <= 0) {
                checkLastError();
            }
            pendingReadableBytesInSSL = SSL.pendingReadableBytesInSSL(state.ssl);
        }

        return pendingReadableBytesInSSL;
    }

    @Override
    public Runnable getDelegatedTask() {
        // Currently, we do not delegate SSL computation tasks
        return null;
    }

    @Override
    public synchronized void closeInbound() throws SSLException {
        if (isInboundDone) {
            return;
        }

        isInboundDone = true;
        engineClosed = true;

        if (isOutboundDone()) {
            // Only call shutdown if there is no outbound data pending.
            shutdown();
        }

        if (accepted != Accepted.NOT && !receivedShutdown) {
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

        if (accepted != Accepted.NOT && !destroyed) {
            int mode = SSL.getShutdown(state.ssl);
            if ((mode & SSL.SSL_SENT_SHUTDOWN) != SSL.SSL_SENT_SHUTDOWN) {
                SSL.shutdownSSL(state.ssl);
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
        return availableCipherSuites.toArray(new String[0]);
    }

    @Override
    public synchronized String[] getEnabledCipherSuites() {
        if (destroyed) {
            return new String[0];
        }
        String[] enabled = SSL.getCiphers(state.ssl);
        if (enabled == null) {
            return new String[0];
        } else {
            for (int i = 0; i < enabled.length; i++) {
                String mapped = OpenSSLCipherConfigurationParser.openSSLToJsse(enabled[i]);
                if (mapped != null) {
                    enabled[i] = mapped;
                }
            }
            return enabled;
        }
    }

    @Override
    public synchronized void setEnabledCipherSuites(String[] cipherSuites) {
        if (initialized) {
            return;
        }
        if (cipherSuites == null) {
            throw new IllegalArgumentException(sm.getString("engine.nullCipherSuite"));
        }
        if (destroyed) {
            return;
        }
        final StringBuilder buf = new StringBuilder();
        for (String cipherSuite : cipherSuites) {
            if (cipherSuite == null) {
                break;
            }
            String converted = OpenSSLCipherConfigurationParser.jsseToOpenSSL(cipherSuite);
            if (!AVAILABLE_CIPHER_SUITES.contains(cipherSuite)) {
                logger.debug(sm.getString("engine.unsupportedCipher", cipherSuite, converted));
            }
            if (converted != null) {
                cipherSuite = converted;
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
            SSL.setCipherSuites(state.ssl, cipherSuiteSpec);
        } catch (Exception e) {
            throw new IllegalStateException(sm.getString("engine.failedCipherSuite", cipherSuiteSpec), e);
        }
    }

    @Override
    public String[] getSupportedProtocols() {
        return IMPLEMENTED_PROTOCOLS_SET.toArray(new String[0]);
    }

    @Override
    public synchronized String[] getEnabledProtocols() {
        if (destroyed) {
            return new String[0];
        }
        List<String> enabled = new ArrayList<>();
        // Seems like there is no way to explicitly disable SSLv2Hello in OpenSSL so it is always enabled
        enabled.add(Constants.SSL_PROTO_SSLv2Hello);
        int opts = SSL.getOptions(state.ssl);
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
        return enabled.toArray(new String[0]);
    }

    @Override
    public synchronized void setEnabledProtocols(String[] protocols) {
        if (initialized) {
            return;
        }
        if (protocols == null) {
            // This is correct from the API docs
            throw new IllegalArgumentException();
        }
        if (destroyed) {
            return;
        }
        boolean sslv2 = false;
        boolean sslv3 = false;
        boolean tlsv1 = false;
        boolean tlsv1_1 = false;
        boolean tlsv1_2 = false;
        for (String p : protocols) {
            if (!IMPLEMENTED_PROTOCOLS_SET.contains(p)) {
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
        SSL.setOptions(state.ssl, SSL.SSL_OP_ALL);

        if (!sslv2) {
            SSL.setOptions(state.ssl, SSL.SSL_OP_NO_SSLv2);
        }
        if (!sslv3) {
            SSL.setOptions(state.ssl, SSL.SSL_OP_NO_SSLv3);
        }
        if (!tlsv1) {
            SSL.setOptions(state.ssl, SSL.SSL_OP_NO_TLSv1);
        }
        if (!tlsv1_1) {
            SSL.setOptions(state.ssl, SSL.SSL_OP_NO_TLSv1_1);
        }
        if (!tlsv1_2) {
            SSL.setOptions(state.ssl, SSL.SSL_OP_NO_TLSv1_2);
        }
    }

    @Override
    public SSLSession getSession() {
        return session;
    }

    @Override
    public synchronized void beginHandshake() throws SSLException {
        if (engineClosed || destroyed) {
            throw new SSLException(sm.getString("engine.engineClosed"));
        }
        switch (accepted) {
        case NOT:
            handshake();
            accepted = Accepted.EXPLICIT;
            break;
        case IMPLICIT:
            // A user did not start handshake by calling this method by themselves,
            // but handshake has been started already by wrap() or unwrap() implicitly.
            // Because it's the user's first time to call this method, it is unfair to
            // raise an exception.  From the user's standpoint, they never asked for
            // renegotiation.

            accepted = Accepted.EXPLICIT; // Next time this method is invoked by the user, we should raise an exception.
            break;
        case EXPLICIT:
            renegotiate();
            break;
        }
    }

    private void beginHandshakeImplicitly() throws SSLException {
        handshake();
        accepted = Accepted.IMPLICIT;
    }

    private void handshake() throws SSLException {
        currentHandshake = SSL.getHandshakeCount(state.ssl);
        clearLastError();
        int code = SSL.doHandshake(state.ssl);
        if (code <= 0) {
            checkLastError();
        } else {
            if (alpn) {
                selectedProtocol = SSL.getAlpnSelected(state.ssl);
            }
            session.lastAccessedTime = System.currentTimeMillis();
            // if SSL_do_handshake returns > 0 it means the handshake was finished. This means we can update
            // handshakeFinished directly and so eliminate unnecessary calls to SSL.isInInit(...)
            handshakeFinished = true;
        }
    }

    private synchronized void renegotiate() throws SSLException {
        clearLastError();
        int code;
        if (SSL.getVersion(state.ssl).equals(Constants.SSL_PROTO_TLSv1_3)) {
            code = SSL.verifyClientPostHandshake(state.ssl);
        } else {
            code = SSL.renegotiate(state.ssl);
        }
        if (code <= 0) {
            checkLastError();
        }
        handshakeFinished = false;
        peerCerts = null;
        currentHandshake = SSL.getHandshakeCount(state.ssl);
        int code2 = SSL.doHandshake(state.ssl);
        if (code2 <= 0) {
            checkLastError();
        }
    }

    private void checkLastError() throws SSLException {
        String sslError = getLastError();
        if (sslError != null) {
            // Many errors can occur during handshake and need to be reported
            if (!handshakeFinished) {
                sendHandshakeError = true;
            } else {
                throw new SSLException(sslError);
            }
        }
    }


    /**
     * Clear out any errors, but log a warning.
     */
    private static void clearLastError() {
        getLastError();
    }

    /**
     * Many calls to SSL methods do not check the last error. Those that do
     * check the last error need to ensure that any previously ignored error is
     * cleared prior to the method call else errors may be falsely reported.
     * Ideally, before any SSL_read, SSL_write, clearLastError should always
     * be called, and getLastError should be called after on any negative or
     * zero result.
     * @return the first error in the stack
     */
    private static String getLastError() {
        String sslError = null;
        long error;
        while ((error = SSL.getLastErrorNumber()) != SSL.SSL_ERROR_NONE) {
            // Loop until getLastErrorNumber() returns SSL_ERROR_NONE
            String err = SSL.getErrorString(error);
            if (sslError == null) {
                sslError = err;
            }
            if (logger.isDebugEnabled()) {
                logger.debug(sm.getString("engine.openSSLError", Long.toString(error), err));
            }
        }
        return sslError;
    }

    private SSLEngineResult.Status getEngineStatus() {
        return engineClosed ? SSLEngineResult.Status.CLOSED : SSLEngineResult.Status.OK;
    }

    @Override
    public synchronized SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        if (accepted == Accepted.NOT || destroyed) {
            return SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
        }

        // Check if we are in the initial handshake phase
        if (!handshakeFinished) {

            // There is pending data in the network BIO -- call wrap
            if (sendHandshakeError || SSL.pendingWrittenBytesInBIO(state.networkBIO) != 0) {
                if (sendHandshakeError) {
                    // After a last wrap, consider it is going to be done
                    sendHandshakeError = false;
                    currentHandshake++;
                }
                return SSLEngineResult.HandshakeStatus.NEED_WRAP;
            }

            /*
             * Tomcat Native stores a count of the completed handshakes in the
             * SSL instance and increments it every time a handshake is
             * completed. Comparing the handshake count when the handshake
             * started to the current handshake count enables this code to
             * detect when the handshake has completed.
             *
             * Obtaining client certificates after the connection has been
             * established requires additional checks. We need to trigger
             * additional reads until the certificates have been read but we
             * don't know how many reads we will need as it depends on both
             * client and network behaviour.
             *
             * The additional reads are triggered by returning NEED_UNWRAP
             * rather than FINISHED. This allows the standard I/O code to be
             * used.
             *
             * For TLSv1.2 and below, the handshake completes before the
             * renegotiation. We therefore use SSL.renegotiatePending() to
             * check on the current status of the renegotiation and return
             * NEED_UNWRAP until it completes which means the client
             * certificates will have been read from the client.
             *
             * For TLSv1.3, Tomcat Native sets a flag when post handshake
             * authentication is started and updates it once the client
             * certificate has been received. We therefore use
             * SSL.getPostHandshakeAuthInProgress() to check the current status
             * and return NEED_UNWRAP until that methods indicates that PHA is
             * no longer in progress.
             */

            // No pending data to be sent to the peer
            // Check to see if we have finished handshaking
            int handshakeCount = SSL.getHandshakeCount(state.ssl);
            if (handshakeCount != currentHandshake && SSL.renegotiatePending(state.ssl) == 0 &&
                    (SSL.getPostHandshakeAuthInProgress(state.ssl) == 0)) {
                if (alpn) {
                    selectedProtocol = SSL.getAlpnSelected(state.ssl);
                }
                session.lastAccessedTime = System.currentTimeMillis();
                version = SSL.getVersion(state.ssl);
                handshakeFinished = true;
                return SSLEngineResult.HandshakeStatus.FINISHED;
            }

            // No pending data
            // Still handshaking / renegotiation / post-handshake auth pending
            // Must be waiting on the peer to send more data
            return SSLEngineResult.HandshakeStatus.NEED_UNWRAP;
        }

        // Check if we are in the shutdown phase
        if (engineClosed) {
            if (SSL.pendingWrittenBytesInBIO(state.networkBIO) != 0) {
                // Waiting to send the close_notify message
                return SSLEngineResult.HandshakeStatus.NEED_WRAP;
            }

            if (!isInboundDone()) {
                // Must be waiting to receive the close_notify message
                return SSLEngineResult.HandshakeStatus.NEED_UNWRAP;
            }
        }

        return SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
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
                    SSL.setVerify(state.ssl, SSL.SSL_CVERIFY_NONE, certificateVerificationDepth);
                    break;
                case REQUIRE:
                    SSL.setVerify(state.ssl, SSL.SSL_CVERIFY_REQUIRE, certificateVerificationDepth);
                    break;
                case OPTIONAL:
                    SSL.setVerify(state.ssl,
                            certificateVerificationOptionalNoCA ? SSL.SSL_CVERIFY_OPTIONAL_NO_CA : SSL.SSL_CVERIFY_OPTIONAL,
                            certificateVerificationDepth);
                    break;
            }
            clientAuth = mode;
        }
    }

    @Override
    public void setEnableSessionCreation(boolean b) {
        if (!b) {
            String msg = sm.getString("engine.noRestrictSessionCreation");
            throw new UnsupportedOperationException(msg);
        }
    }

    @Override
    public boolean getEnableSessionCreation() {
        return true;
    }


    private class OpenSSLSession implements SSLSession {

        // lazy init for memory reasons
        private Map<String, Object> values;

        // Last accessed time
        private long lastAccessedTime = -1;

        @Override
        public byte[] getId() {
            byte[] id = null;
            synchronized (OpenSSLEngine.this) {
                if (!destroyed) {
                    id = SSL.getSessionId(state.ssl);
                }
            }

            return id;
        }

        @Override
        public SSLSessionContext getSessionContext() {
            return sessionContext;
        }

        @Override
        public long getCreationTime() {
            // We need to multiply by 1000 as OpenSSL uses seconds and we need milliseconds.
            long creationTime = 0;
            synchronized (OpenSSLEngine.this) {
                if (!destroyed) {
                    creationTime = SSL.getTime(state.ssl);
                }
            }
            return creationTime * 1000L;
        }

        @Override
        public long getLastAccessedTime() {
            return (lastAccessedTime > 0) ? lastAccessedTime : getCreationTime();
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
            return values.keySet().toArray(new String[0]);
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
                byte[] clientCert;
                byte[][] chain;
                synchronized (OpenSSLEngine.this) {
                    if (destroyed || SSL.isInInit(state.ssl) != 0) {
                        throw new SSLPeerUnverifiedException(sm.getString("engine.unverifiedPeer"));
                    }
                    chain = SSL.getPeerCertChain(state.ssl);
                    if (!clientMode) {
                        // if used on the server side SSL_get_peer_cert_chain(...) will not include the remote peer certificate.
                        // We use SSL_get_peer_certificate to get it in this case and add it to our array later.
                        //
                        // See https://www.openssl.org/docs/ssl/SSL_get_peer_cert_chain.html
                        clientCert = SSL.getPeerCertificate(state.ssl);
                    } else {
                        clientCert = null;
                    }
                }
                if (chain == null && clientCert == null) {
                    return null;
                }
                int len = 0;
                if (chain != null) {
                    len += chain.length;
                }

                int i = 0;
                Certificate[] certificates;
                if (clientCert != null) {
                    len++;
                    certificates = new Certificate[len];
                    certificates[i++] = new OpenSSLX509Certificate(clientCert);
                } else {
                    certificates = new Certificate[len];
                }
                if (chain != null) {
                    int a = 0;
                    for (; i < certificates.length; i++) {
                        certificates[i] = new OpenSSLX509Certificate(chain[a++]);
                    }
                }
                c = peerCerts = certificates;
            }
            return c;
        }

        @Override
        public Certificate[] getLocalCertificates() {
            // FIXME (if possible): Not available in the OpenSSL API
            return EMPTY_CERTIFICATES;
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
            if (cipher == null) {
                String ciphers;
                synchronized (OpenSSLEngine.this) {
                    if (!handshakeFinished) {
                        return INVALID_CIPHER;
                    }
                    if (destroyed) {
                        return INVALID_CIPHER;
                    }
                    ciphers = SSL.getCipherForSSL(state.ssl);
                }
                String c = OpenSSLCipherConfigurationParser.openSSLToJsse(ciphers);
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
                applicationProtocol = fallbackApplicationProtocol;
                if (applicationProtocol != null) {
                    OpenSSLEngine.this.applicationProtocol = applicationProtocol.replace(':', '_');
                } else {
                    OpenSSLEngine.this.applicationProtocol = applicationProtocol = "";
                }
            }
            String version = null;
            synchronized (OpenSSLEngine.this) {
                if (!destroyed) {
                    version = SSL.getVersion(state.ssl);
                }
            }
            if (applicationProtocol.isEmpty()) {
                return version;
            } else {
                return version + ':' + applicationProtocol;
            }
        }

        @Override
        public String getPeerHost() {
            // Not available for now in Tomcat (needs to be passed during engine creation)
            return null;
        }

        @Override
        public int getPeerPort() {
            // Not available for now in Tomcat (needs to be passed during engine creation)
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

    }


    private static class OpenSSLState implements Runnable {

        private final long ssl;
        private final long networkBIO;

        private OpenSSLState(long ssl, long networkBIO) {
            this.ssl = ssl;
            this.networkBIO = networkBIO;
        }

        @Override
        public void run() {
            if (networkBIO != 0) {
                SSL.freeBIO(networkBIO);
            }
            if (ssl != 0) {
                SSL.freeSSL(ssl);
            }
        }
    }
}
