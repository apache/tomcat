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
package org.apache.coyote.http2;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

import jakarta.servlet.ServletConnection;
import jakarta.servlet.http.WebConnection;

import org.apache.coyote.Adapter;
import org.apache.coyote.ProtocolException;
import org.apache.coyote.Request;
import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.coyote.http2.HpackDecoder.HeaderEmitter;
import org.apache.coyote.http2.HpackEncoder.State;
import org.apache.coyote.http2.Http2Parser.Input;
import org.apache.coyote.http2.Http2Parser.Output;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.http.parser.Priority;
import org.apache.tomcat.util.log.UserDataHelper;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SendfileState;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * This represents an HTTP/2 connection from a client to Tomcat. It is designed on the basis that there will never be
 * more than one thread performing I/O at a time. <br>
 * For reading, this implementation is blocking within frames and non-blocking between frames. <br>
 * Note:
 * <ul>
 * <li>You will need to nest an &lt;UpgradeProtocol className="org.apache.coyote.http2.Http2Protocol" /&gt; element
 * inside a TLS enabled Connector element in server.xml to enable HTTP/2 support.</li>
 * </ul>
 */
class Http2UpgradeHandler extends AbstractStream implements InternalHttpUpgradeHandler, Input, Output {

    protected static final Log log = LogFactory.getLog(Http2UpgradeHandler.class);
    protected static final StringManager sm = StringManager.getManager(Http2UpgradeHandler.class);

    private static final Integer STREAM_ID_ZERO = Integer.valueOf(0);

    protected static final int FLAG_END_OF_STREAM = 1;
    protected static final int FLAG_END_OF_HEADERS = 4;

    protected static final byte[] PING = { 0x00, 0x00, 0x08, 0x06, 0x00, 0x00, 0x00, 0x00, 0x00 };
    protected static final byte[] PING_ACK = { 0x00, 0x00, 0x08, 0x06, 0x01, 0x00, 0x00, 0x00, 0x00 };

    protected static final byte[] SETTINGS_ACK = { 0x00, 0x00, 0x00, 0x04, 0x01, 0x00, 0x00, 0x00, 0x00 };

    protected static final byte[] GOAWAY = { 0x07, 0x00, 0x00, 0x00, 0x00, 0x00 };

    private static final String HTTP2_SETTINGS_HEADER = "HTTP2-Settings";

    protected static final HeaderSink HEADER_SINK = new HeaderSink();

    protected final String connectionId;

    protected final Http2Protocol protocol;
    private final Adapter adapter;
    protected final SocketWrapperBase<?> socketWrapper;
    private volatile SSLSupport sslSupport;

    private volatile Http2Parser parser;

    // Simple state machine (sequence of states)
    private AtomicReference<ConnectionState> connectionState = new AtomicReference<>(ConnectionState.NEW);
    private volatile long pausedNanoTime = Long.MAX_VALUE;

    /**
     * Remote settings are settings defined by the client and sent to Tomcat that Tomcat must use when communicating
     * with the client.
     */
    private final ConnectionSettingsRemote remoteSettings;
    /**
     * Local settings are settings defined by Tomcat and sent to the client that the client must use when communicating
     * with Tomcat.
     */
    protected final ConnectionSettingsLocal localSettings;

    private HpackDecoder hpackDecoder;
    private HpackEncoder hpackEncoder;

    private final ConcurrentNavigableMap<Integer,AbstractNonZeroStream> streams = new ConcurrentSkipListMap<>();
    protected final AtomicInteger activeRemoteStreamCount = new AtomicInteger(0);
    private volatile int maxProcessedStreamId;
    private final PingManager pingManager = getPingManager();
    private volatile int newStreamsSinceLastPrune = 0;
    private final Set<Stream> backLogStreams = new HashSet<>();
    private long backLogSize = 0;
    // The time at which the connection will timeout unless data arrives before
    // then. -1 means no timeout.
    private volatile long connectionTimeout = -1;

    // Stream concurrency control
    private AtomicInteger streamConcurrency = null;
    private Queue<StreamRunnable> queuedRunnable = null;

    // Track 'overhead' frames vs 'request/response' frames
    private final AtomicLong overheadCount;
    private volatile int lastNonFinalDataPayload;
    private volatile int lastWindowUpdate;

    protected final UserDataHelper userDataHelper = new UserDataHelper(log);


    Http2UpgradeHandler(Http2Protocol protocol, Adapter adapter, Request coyoteRequest,
            SocketWrapperBase<?> socketWrapper) {
        super(STREAM_ID_ZERO);
        this.protocol = protocol;
        this.adapter = adapter;
        this.socketWrapper = socketWrapper;

        // Defaults to -10 * the count factor.
        // i.e. when the connection opens, 10 'overhead' frames in a row will
        // cause the connection to be closed.
        // Over time the count should be a slowly decreasing negative number.
        // Therefore, the longer a connection is 'well-behaved', the greater
        // tolerance it will have for a period of 'bad' behaviour.
        overheadCount = new AtomicLong(-10 * protocol.getOverheadCountFactor());

        lastNonFinalDataPayload = protocol.getOverheadDataThreshold() * 2;
        lastWindowUpdate = protocol.getOverheadWindowUpdateThreshold() * 2;

        connectionId = getServletConnection().getConnectionId();

        remoteSettings = new ConnectionSettingsRemote(connectionId);
        localSettings = new ConnectionSettingsLocal(connectionId);

        localSettings.set(Setting.MAX_CONCURRENT_STREAMS, protocol.getMaxConcurrentStreams());
        localSettings.set(Setting.INITIAL_WINDOW_SIZE, protocol.getInitialWindowSize());

        pingManager.initiateDisabled = protocol.getInitiatePingDisabled();

        // Initial HTTP request becomes stream 1.
        if (coyoteRequest != null) {
            if (log.isTraceEnabled()) {
                log.trace(sm.getString("upgradeHandler.upgrade", connectionId));
            }
            Integer key = Integer.valueOf(1);
            Stream stream = new Stream(key, this, coyoteRequest);
            streams.put(key, stream);
            activeRemoteStreamCount.set(1);
            maxProcessedStreamId = 1;
        }
    }


    protected PingManager getPingManager() {
        return new PingManager();
    }


    @Override
    public void init(WebConnection webConnection) {
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("upgradeHandler.init", connectionId, connectionState.get()));
        }

        if (!connectionState.compareAndSet(ConnectionState.NEW, ConnectionState.CONNECTED)) {
            return;
        }

        // Init concurrency control if needed
        if (protocol.getMaxConcurrentStreamExecution() < localSettings.getMaxConcurrentStreams()) {
            streamConcurrency = new AtomicInteger(0);
            queuedRunnable = new ConcurrentLinkedQueue<>();
        }

        parser = getParser(connectionId);

        Stream stream = null;

        socketWrapper.setReadTimeout(protocol.getReadTimeout());
        socketWrapper.setWriteTimeout(protocol.getWriteTimeout());

        if (webConnection != null) {
            // HTTP/2 started via HTTP upgrade.
            // The initial HTTP/1.1 request is available as Stream 1.

            try {
                // Process the initial settings frame
                stream = getStream(1, true);
                String base64Settings = stream.getCoyoteRequest().getHeader(HTTP2_SETTINGS_HEADER);
                byte[] settings = Base64.getUrlDecoder().decode(base64Settings);

                // Settings are only valid on stream 0
                FrameType.SETTINGS.check(0, settings.length);

                for (int i = 0; i < settings.length % 6; i++) {
                    int id = ByteUtil.getTwoBytes(settings, i * 6);
                    long value = ByteUtil.getFourBytes(settings, (i * 6) + 2);
                    Setting key = Setting.valueOf(id);
                    if (key == Setting.UNKNOWN) {
                        log.warn(sm.getString("connectionSettings.unknown", connectionId, Integer.toString(id),
                                Long.toString(value)));
                    }
                    remoteSettings.set(key, value);
                }
            } catch (Http2Exception e) {
                throw new ProtocolException(sm.getString("upgradeHandler.upgrade.fail", connectionId));
            }
        }

        // Send the initial settings frame
        writeSettings();

        // Make sure the client has sent a valid connection preface before we
        // send the response to the original request over HTTP/2.
        try {
            parser.readConnectionPreface(webConnection, stream);
        } catch (Http2Exception e) {
            String msg = sm.getString("upgradeHandler.invalidPreface", connectionId);
            if (log.isDebugEnabled()) {
                log.debug(msg, e);
            }
            throw new ProtocolException(msg);
        }
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("upgradeHandler.prefaceReceived", connectionId));
        }

        // Allow streams and connection to determine timeouts
        socketWrapper.setReadTimeout(-1);
        socketWrapper.setWriteTimeout(-1);

        processConnection(webConnection, stream);
    }

    protected void processConnection(WebConnection webConnection, Stream stream) {
        // Send a ping to get an idea of round trip time as early as possible
        try {
            pingManager.sendPing(true);
        } catch (IOException ioe) {
            throw new ProtocolException(sm.getString("upgradeHandler.pingFailed", connectionId), ioe);
        }

        if (webConnection != null) {
            processStreamOnContainerThread(stream);
        }
    }

    protected Http2Parser getParser(String connectionId) {
        return new Http2Parser(connectionId, this, this);
    }


    protected void processStreamOnContainerThread(Stream stream) {
        StreamProcessor streamProcessor = new StreamProcessor(this, stream, adapter, socketWrapper);
        streamProcessor.setSslSupport(sslSupport);
        processStreamOnContainerThread(streamProcessor, SocketEvent.OPEN_READ);
    }


    protected void decrementActiveRemoteStreamCount(Stream stream) {
        if (stream != null) {
            setConnectionTimeoutForStreamCount(stream.decrementAndGetActiveRemoteStreamCount());
        }
    }


    void processStreamOnContainerThread(StreamProcessor streamProcessor, SocketEvent event) {
        StreamRunnable streamRunnable = new StreamRunnable(streamProcessor, event);
        if (streamConcurrency == null) {
            socketWrapper.execute(streamRunnable);
        } else {
            if (getStreamConcurrency() < protocol.getMaxConcurrentStreamExecution()) {
                increaseStreamConcurrency();
                socketWrapper.execute(streamRunnable);
            } else {
                queuedRunnable.offer(streamRunnable);
            }
        }
    }


    @Override
    public void setSocketWrapper(SocketWrapperBase<?> wrapper) {
        // NO-OP. It is passed via the constructor
    }


    @Override
    public void setSslSupport(SSLSupport sslSupport) {
        this.sslSupport = sslSupport;
    }


    @Override
    public SocketState upgradeDispatch(SocketEvent status) {
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("upgradeHandler.upgradeDispatch.entry", connectionId, status));
        }

        // WebConnection is not used so passing null here is fine
        // Might not be necessary. init() will handle that.
        init(null);

        SocketState result = SocketState.CLOSED;

        try {
            switch (status) {
                case OPEN_READ:
                    socketWrapper.getLock().lock();
                    try {
                        if (socketWrapper.canWrite()) {
                            // Only send a ping if there is no other data waiting to be sent.
                            // Ping manager will ensure they aren't sent too frequently.
                            pingManager.sendPing(false);
                        }
                    } finally {
                        socketWrapper.getLock().unlock();
                    }
                    try {
                        // Disable the connection timeout while frames are processed
                        setConnectionTimeout(-1);
                        while (true) {
                            try {
                                if (!parser.readFrame()) {
                                    break;
                                }
                            } catch (StreamException se) {
                                // Log the Stream error but not necessarily all of
                                // them
                                UserDataHelper.Mode logMode = userDataHelper.getNextMode();
                                if (logMode != null) {
                                    String message = sm.getString("upgradeHandler.stream.error", connectionId,
                                            Integer.toString(se.getStreamId()));
                                    switch (logMode) {
                                        case INFO_THEN_DEBUG:
                                            message += sm.getString("upgradeHandler.fallToDebug");
                                            //$FALL-THROUGH$
                                        case INFO:
                                            log.info(message, se);
                                            break;
                                        case DEBUG:
                                            log.debug(message, se);
                                    }
                                }
                                // Stream errors are not fatal to the connection so
                                // continue reading frames
                                Stream stream = getStream(se.getStreamId(), false);
                                if (stream == null) {
                                    sendStreamReset(null, se);
                                } else {
                                    stream.close(se);
                                }
                            } finally {
                                if (isOverheadLimitExceeded()) {
                                    throw new ConnectionException(
                                            sm.getString("upgradeHandler.tooMuchOverhead", connectionId),
                                            Http2Error.ENHANCE_YOUR_CALM);
                                }
                            }
                        }

                        // Need to know the correct timeout before starting the read
                        // but that may not be known at this time if one or more
                        // requests are currently being processed so don't set a
                        // timeout for the socket...
                        socketWrapper.setReadTimeout(-1);

                        // ...set a timeout on the connection
                        setConnectionTimeoutForStreamCount(activeRemoteStreamCount.get());

                    } catch (Http2Exception ce) {
                        // Really ConnectionException
                        if (log.isDebugEnabled()) {
                            log.debug(sm.getString("upgradeHandler.connectionError"), ce);
                        }
                        closeConnection(ce);
                        break;
                    }

                    if (connectionState.get() != ConnectionState.CLOSED) {
                        if (socketWrapper.hasAsyncIO()) {
                            result = SocketState.ASYNC_IO;
                        } else {
                            result = SocketState.UPGRADED;
                        }
                    }
                    break;

                case OPEN_WRITE:
                    processWrites();
                    if (socketWrapper.hasAsyncIO()) {
                        result = SocketState.ASYNC_IO;
                    } else {
                        result = SocketState.UPGRADED;
                    }
                    break;

                case TIMEOUT:
                    closeConnection(null);
                    break;

                case DISCONNECT:
                case ERROR:
                case STOP:
                case CONNECT_FAIL:
                    close();
                    break;
            }
        } catch (IOException ioe) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("upgradeHandler.ioerror", connectionId), ioe);
            }
            close();
        }

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("upgradeHandler.upgradeDispatch.exit", connectionId, result));
        }
        return result;
    }


    /*
     * Sets the connection timeout based on the current number of active streams.
     */
    protected void setConnectionTimeoutForStreamCount(int streamCount) {
        if (streamCount == 0) {
            // No streams currently active. Use the keep-alive
            // timeout for the connection.
            long keepAliveTimeout = protocol.getKeepAliveTimeout();
            if (keepAliveTimeout == -1) {
                setConnectionTimeout(-1);
            } else {
                setConnectionTimeout(System.currentTimeMillis() + keepAliveTimeout);
            }
        } else {
            // Streams currently active. Individual streams have
            // timeouts so keep the connection open.
            setConnectionTimeout(-1);
        }
    }


    private void setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }


    @Override
    public void timeoutAsync(long now) {
        long connectionTimeout = this.connectionTimeout;
        if (now == -1 || connectionTimeout > -1 && now > connectionTimeout) {
            // Have to dispatch as this will be executed from a non-container
            // thread.
            socketWrapper.processSocket(SocketEvent.TIMEOUT, true);
        }
    }


    ConnectionSettingsRemote getRemoteSettings() {
        return remoteSettings;
    }


    ConnectionSettingsLocal getLocalSettings() {
        return localSettings;
    }


    Http2Protocol getProtocol() {
        return protocol;
    }


    @Override
    public void pause() {
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("upgradeHandler.pause.entry", connectionId));
        }

        if (connectionState.compareAndSet(ConnectionState.CONNECTED, ConnectionState.PAUSING)) {
            pausedNanoTime = System.nanoTime();

            try {
                writeGoAwayFrame((1 << 31) - 1, Http2Error.NO_ERROR.getCode(), null);
            } catch (IOException ioe) {
                // This is fatal for the connection. Ignore it here. There will be
                // further attempts at I/O in upgradeDispatch() and it can better
                // handle the IO errors.
            }
        }
    }


    @Override
    public void destroy() {
        // NO-OP
    }


    void checkPauseState() throws IOException {
        if (connectionState.get() == ConnectionState.PAUSING) {
            if (pausedNanoTime + pingManager.getRoundTripTimeNano() < System.nanoTime()) {
                connectionState.compareAndSet(ConnectionState.PAUSING, ConnectionState.PAUSED);
                writeGoAwayFrame(maxProcessedStreamId, Http2Error.NO_ERROR.getCode(), null);
            }
        }
    }


    private int increaseStreamConcurrency() {
        return streamConcurrency.incrementAndGet();
    }

    private int decreaseStreamConcurrency() {
        return streamConcurrency.decrementAndGet();
    }

    private int getStreamConcurrency() {
        return streamConcurrency.get();
    }

    void executeQueuedStream() {
        if (streamConcurrency == null) {
            return;
        }
        decreaseStreamConcurrency();
        if (getStreamConcurrency() < protocol.getMaxConcurrentStreamExecution()) {
            StreamRunnable streamRunnable = queuedRunnable.poll();
            if (streamRunnable != null) {
                increaseStreamConcurrency();
                socketWrapper.execute(streamRunnable);
            }
        }
    }


    void sendStreamReset(StreamStateMachine state, StreamException se) throws IOException {

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("upgradeHandler.rst.debug", connectionId, Integer.toString(se.getStreamId()),
                    se.getError(), se.getMessage()));
        }

        // Write a RST frame
        byte[] rstFrame = new byte[13];
        // Length
        ByteUtil.setThreeBytes(rstFrame, 0, 4);
        // Type
        rstFrame[3] = FrameType.RST.getIdByte();
        // No flags
        // Stream ID
        ByteUtil.set31Bits(rstFrame, 5, se.getStreamId());
        // Payload
        ByteUtil.setFourBytes(rstFrame, 9, se.getError().getCode());

        // Need to update state atomically with the sending of the RST
        // frame else other threads currently working with this stream
        // may see the state change and send a RST frame before the RST
        // frame triggered by this thread. If that happens the client
        // may see out of order RST frames which may hard to follow if
        // the client is unaware the RST frames may be received out of
        // order.
        socketWrapper.getLock().lock();
        try {
            if (state != null) {
                boolean active = state.isActive();
                state.sendReset();
                if (active) {
                    decrementActiveRemoteStreamCount(getStream(se.getStreamId()));
                }
            }
            socketWrapper.write(true, rstFrame, 0, rstFrame.length);
            socketWrapper.flush(true);
        } finally {
            socketWrapper.getLock().unlock();
        }
    }


    void closeConnection(Http2Exception ce) {
        long code;
        byte[] msg;
        if (ce == null) {
            code = Http2Error.NO_ERROR.getCode();
            msg = null;
        } else {
            code = ce.getError().getCode();
            msg = ce.getMessage().getBytes(StandardCharsets.UTF_8);
        }
        try {
            writeGoAwayFrame(maxProcessedStreamId, code, msg);
        } catch (IOException ioe) {
            // Ignore. GOAWAY is sent on a best efforts basis and the original
            // error has already been logged.
        }
        close();
    }


    /**
     * Write the initial settings frame and any necessary supporting frames. If the initial settings increase the
     * initial window size, it will also be necessary to send a WINDOW_UPDATE frame to increase the size of the flow
     * control window for the connection (stream 0).
     */
    protected void writeSettings() {
        // Send the initial settings frame
        try {
            byte[] settings = localSettings.getSettingsFrameForPending();
            socketWrapper.write(true, settings, 0, settings.length);
            byte[] windowUpdateFrame = createWindowUpdateForSettings();
            if (windowUpdateFrame.length > 0) {
                socketWrapper.write(true, windowUpdateFrame, 0, windowUpdateFrame.length);
            }
            socketWrapper.flush(true);
        } catch (IOException ioe) {
            String msg = sm.getString("upgradeHandler.sendPrefaceFail", connectionId);
            if (log.isDebugEnabled()) {
                log.debug(msg);
            }
            throw new ProtocolException(msg, ioe);
        }
    }


    /**
     * @return The WINDOW_UPDATE frame if one is required or an empty array if no WINDOW_UPDATE is required.
     */
    protected byte[] createWindowUpdateForSettings() {
        // Build a WINDOW_UPDATE frame if one is required. If not, create an
        // empty byte array.
        byte[] windowUpdateFrame;
        int increment = protocol.getInitialWindowSize() - ConnectionSettingsBase.DEFAULT_INITIAL_WINDOW_SIZE;
        if (increment > 0) {
            // Build window update frame for stream 0
            windowUpdateFrame = new byte[13];
            ByteUtil.setThreeBytes(windowUpdateFrame, 0, 4);
            windowUpdateFrame[3] = FrameType.WINDOW_UPDATE.getIdByte();
            ByteUtil.set31Bits(windowUpdateFrame, 9, increment);
        } else {
            windowUpdateFrame = new byte[0];
        }

        return windowUpdateFrame;
    }


    protected void writeGoAwayFrame(int maxStreamId, long errorCode, byte[] debugMsg) throws IOException {
        byte[] fixedPayload = new byte[8];
        ByteUtil.set31Bits(fixedPayload, 0, maxStreamId);
        ByteUtil.setFourBytes(fixedPayload, 4, errorCode);
        int len = 8;
        if (debugMsg != null) {
            len += debugMsg.length;
        }
        byte[] payloadLength = new byte[3];
        ByteUtil.setThreeBytes(payloadLength, 0, len);

        Lock lock = socketWrapper.getLock();
        lock.lock();
        try {
            socketWrapper.write(true, payloadLength, 0, payloadLength.length);
            socketWrapper.write(true, GOAWAY, 0, GOAWAY.length);
            socketWrapper.write(true, fixedPayload, 0, 8);
            if (debugMsg != null) {
                socketWrapper.write(true, debugMsg, 0, debugMsg.length);
            }
            socketWrapper.flush(true);
        } finally {
            lock.unlock();
        }
    }

    void writeHeaders(Stream stream, MimeHeaders mimeHeaders, boolean endOfStream, int payloadSize) throws IOException {
        // This ensures the Stream processing thread has control of the socket.
        Lock lock = socketWrapper.getLock();
        lock.lock();
        try {
            doWriteHeaders(stream, mimeHeaders, endOfStream, payloadSize);
        } finally {
            lock.unlock();
        }
        stream.sentHeaders();
        if (endOfStream) {
            sentEndOfStream(stream);
        }
    }


    /*
     * Separate method to allow Http2AsyncUpgradeHandler to call this code without synchronizing on socketWrapper since
     * it doesn't need to.
     */
    protected HeaderFrameBuffers doWriteHeaders(Stream stream, MimeHeaders mimeHeaders, boolean endOfStream,
            int payloadSize) throws IOException {

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("upgradeHandler.writeHeaders", connectionId, stream.getIdAsString(),
                    Boolean.valueOf(endOfStream)));
        }

        if (!stream.canWrite()) {
            return null;
        }

        HeaderFrameBuffers headerFrameBuffers = getHeaderFrameBuffers(payloadSize);

        boolean first = true;
        State state = null;

        while (state != State.COMPLETE) {
            headerFrameBuffers.startFrame();
            state = getHpackEncoder().encode(mimeHeaders, headerFrameBuffers.getPayload());
            headerFrameBuffers.getPayload().flip();
            if (state == State.COMPLETE || headerFrameBuffers.getPayload().limit() > 0) {
                ByteUtil.setThreeBytes(headerFrameBuffers.getHeader(), 0, headerFrameBuffers.getPayload().limit());
                if (first) {
                    first = false;
                    headerFrameBuffers.getHeader()[3] = FrameType.HEADERS.getIdByte();
                    if (endOfStream) {
                        headerFrameBuffers.getHeader()[4] = FLAG_END_OF_STREAM;
                    }
                } else {
                    headerFrameBuffers.getHeader()[3] = FrameType.CONTINUATION.getIdByte();
                }
                if (state == State.COMPLETE) {
                    headerFrameBuffers.getHeader()[4] += FLAG_END_OF_HEADERS;
                }
                if (log.isTraceEnabled()) {
                    log.trace(headerFrameBuffers.getPayload().limit() + " bytes");
                }
                ByteUtil.set31Bits(headerFrameBuffers.getHeader(), 5, stream.getIdAsInt());
                headerFrameBuffers.endFrame();
            } else if (state == State.UNDERFLOW) {
                headerFrameBuffers.expandPayload();
            }
        }
        headerFrameBuffers.endHeaders();
        return headerFrameBuffers;
    }

    protected HeaderFrameBuffers getHeaderFrameBuffers(int initialPayloadSize) {
        return new DefaultHeaderFrameBuffers(initialPayloadSize);
    }


    protected HpackEncoder getHpackEncoder() {
        if (hpackEncoder == null) {
            hpackEncoder = new HpackEncoder();
        }
        // Ensure latest agreed table size is used
        hpackEncoder.setMaxTableSize(remoteSettings.getHeaderTableSize());
        return hpackEncoder;
    }


    void writeBody(Stream stream, ByteBuffer data, int len, boolean finished) throws IOException {
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("upgradeHandler.writeBody", connectionId, stream.getIdAsString(),
                    Integer.toString(len), Boolean.valueOf(finished)));
        }

        reduceOverheadCount(FrameType.DATA);

        // Need to check this now since sending end of stream will change this.
        boolean writable = stream.canWrite();
        byte[] header = new byte[9];
        ByteUtil.setThreeBytes(header, 0, len);
        header[3] = FrameType.DATA.getIdByte();
        if (finished) {
            header[4] = FLAG_END_OF_STREAM;
            sentEndOfStream(stream);
        }
        if (writable) {
            ByteUtil.set31Bits(header, 5, stream.getIdAsInt());
            socketWrapper.getLock().lock();
            try {
                socketWrapper.write(true, header, 0, header.length);
                int orgLimit = data.limit();
                data.limit(data.position() + len);
                socketWrapper.write(true, data);
                data.limit(orgLimit);
                socketWrapper.flush(true);
            } catch (IOException ioe) {
                handleAppInitiatedIOException(ioe);
            } finally {
                socketWrapper.getLock().unlock();
            }
        }
    }


    protected void sentEndOfStream(Stream stream) {
        stream.sentEndOfStream();
        if (!stream.isActive()) {
            decrementActiveRemoteStreamCount(stream);
        }
    }


    /*
     * Handles an I/O error on the socket underlying the HTTP/2 connection when it is triggered by application code
     * (usually reading the request or writing the response). Such I/O errors are fatal so the connection is closed. The
     * exception is re-thrown to make the client code aware of the problem.
     *
     * Note: We can not rely on this exception reaching the socket processor since the application code may swallow it.
     */
    protected void handleAppInitiatedIOException(IOException ioe) throws IOException {
        close();
        throw ioe;
    }


    /*
     * Needs to know if this was application initiated since that affects the error handling.
     */
    void writeWindowUpdate(AbstractNonZeroStream stream, int increment, boolean applicationInitiated)
            throws IOException {
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("upgradeHandler.windowUpdateConnection", getConnectionId(),
                    Integer.valueOf(increment)));
        }
        socketWrapper.getLock().lock();
        try {
            // Build window update frame for stream 0
            byte[] frame = new byte[13];
            ByteUtil.setThreeBytes(frame, 0, 4);
            frame[3] = FrameType.WINDOW_UPDATE.getIdByte();
            ByteUtil.set31Bits(frame, 9, increment);
            socketWrapper.write(true, frame, 0, frame.length);
            boolean needFlush = true;
            // No need to send update from closed stream
            if (stream instanceof Stream && ((Stream) stream).canWrite()) {
                int streamIncrement = ((Stream) stream).getWindowUpdateSizeToWrite(increment);
                if (streamIncrement > 0) {
                    if (log.isTraceEnabled()) {
                        log.trace(sm.getString("upgradeHandler.windowUpdateStream", getConnectionId(), getIdAsString(),
                                Integer.valueOf(streamIncrement)));
                    }
                    // Re-use buffer as connection update has already been written
                    ByteUtil.set31Bits(frame, 5, stream.getIdAsInt());
                    ByteUtil.set31Bits(frame, 9, streamIncrement);
                    try {
                        socketWrapper.write(true, frame, 0, frame.length);
                        socketWrapper.flush(true);
                        needFlush = false;
                    } catch (IOException ioe) {
                        if (applicationInitiated) {
                            handleAppInitiatedIOException(ioe);
                        } else {
                            throw ioe;
                        }
                    }
                }
            }
            if (needFlush) {
                socketWrapper.flush(true);
            }
        } finally {
            socketWrapper.getLock().unlock();
        }
    }


    protected void processWrites() throws IOException {
        Lock lock = socketWrapper.getLock();
        lock.lock();
        try {
            if (socketWrapper.flush(false)) {
                socketWrapper.registerWriteInterest();
            } else {
                // Only send a ping if there is no other data waiting to be sent.
                // Ping manager will ensure they aren't sent too frequently.
                pingManager.sendPing(false);
            }
        } finally {
            lock.unlock();
        }
    }


    /*
     * Requesting an allocation from the connection window for the specified stream.
     */
    int reserveWindowSize(Stream stream, int reservation, boolean block) throws IOException {
        /*
         * Need to be holding the stream lock so releaseBacklog() can't notify this thread until after this thread
         * enters wait().
         */
        int allocation = 0;
        stream.windowAllocationLock.lock();
        try {
            windowAllocationLock.lock();
            try {
                if (!stream.canWrite()) {
                    stream.doStreamCancel(
                            sm.getString("upgradeHandler.stream.notWritable", stream.getConnectionId(),
                                    stream.getIdAsString(), stream.state.getCurrentStateName()),
                            Http2Error.STREAM_CLOSED);
                }
                long windowSize = getWindowSize();
                if (stream.getConnectionAllocationMade() > 0) {
                    // The stream is/was in the backlog and has been granted an allocation - use it.
                    allocation = stream.getConnectionAllocationMade();
                    stream.setConnectionAllocationMade(0);
                } else if (windowSize < 1) {
                    /*
                     * The connection window has no capacity. If the stream has not been granted an allocation, and the
                     * stream was not already added to the backlog due to an partial reservation (see next else if
                     * block) add it to the backlog so it can obtain an allocation when capacity is available.
                     */
                    if (stream.getConnectionAllocationMade() == 0 && stream.getConnectionAllocationRequested() == 0) {
                        stream.setConnectionAllocationRequested(reservation);
                        backLogSize += reservation;
                        backLogStreams.add(stream);
                    }
                } else if (windowSize < reservation) {
                    /*
                     * The connection window has some capacity but not enough to fill this reservation. Allocate what
                     * capacity is available and add the stream to the backlog so it can obtain a further allocation
                     * when capacity is available.
                     */
                    allocation = (int) windowSize;
                    decrementWindowSize(allocation);
                    int reservationRemaining = reservation - allocation;
                    stream.setConnectionAllocationRequested(reservationRemaining);
                    backLogSize += reservationRemaining;
                    backLogStreams.add(stream);

                } else {
                    // The connection window has sufficient capacity for this reservation. Allocate the full amount.
                    allocation = reservation;
                    decrementWindowSize(allocation);
                }
            } finally {
                windowAllocationLock.unlock();
            }
            if (allocation == 0) {
                if (block) {
                    try {
                        // Connection level window is empty. Although this
                        // request is for a stream, use the connection
                        // timeout
                        long writeTimeout = protocol.getWriteTimeout();
                        stream.waitForConnectionAllocation(writeTimeout);
                        // Has this stream been granted an allocation
                        if (stream.getConnectionAllocationMade() == 0) {
                            String msg;
                            Http2Error error;
                            if (stream.isActive()) {
                                if (log.isDebugEnabled()) {
                                    log.debug(sm.getString("upgradeHandler.noAllocation", connectionId,
                                            stream.getIdAsString()));
                                }
                                // No allocation
                                // Close the connection. Do this first since
                                // closing the stream will raise an exception.
                                close();
                                msg = sm.getString("stream.writeTimeout");
                                error = Http2Error.ENHANCE_YOUR_CALM;
                            } else {
                                msg = sm.getString("upgradeHandler.clientCancel");
                                error = Http2Error.STREAM_CLOSED;
                            }
                            // Close the stream
                            // This thread is in application code so need
                            // to signal to the application that the
                            // stream is closing
                            stream.doStreamCancel(msg, error);
                        } else {
                            allocation = stream.getConnectionAllocationMade();
                            stream.setConnectionAllocationMade(0);
                        }
                    } catch (InterruptedException e) {
                        throw new IOException(sm.getString("upgradeHandler.windowSizeReservationInterrupted",
                                connectionId, stream.getIdAsString(), Integer.toString(reservation)), e);
                    }
                } else {
                    stream.waitForConnectionAllocationNonBlocking();
                    return 0;
                }
            }
        } finally {
            stream.windowAllocationLock.unlock();
        }
        return allocation;
    }


    @Override
    protected void incrementWindowSize(int increment) throws Http2Exception {
        Set<AbstractStream> streamsToNotify = null;

        windowAllocationLock.lock();
        try {
            long windowSize = getWindowSize();
            if (windowSize < 1 && windowSize + increment > 0) {
                // Connection window is exhausted. Assume there will be streams
                // to notify. The overhead is minimal if there are none.
                streamsToNotify = releaseBackLog((int) (windowSize + increment));
            } else {
                super.incrementWindowSize(increment);
            }
        } finally {
            windowAllocationLock.unlock();
        }

        if (streamsToNotify != null) {
            for (AbstractStream stream : streamsToNotify) {
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("upgradeHandler.releaseBacklog", connectionId, stream.getIdAsString()));
                }
                // There is never any O/P on stream zero but it is included in
                // the backlog as it simplifies the code. Skip it if it appears
                // here.
                if (this == stream) {
                    continue;
                }
                ((Stream) stream).notifyConnection();
            }
        }
    }


    /**
     * Process send file (if supported) for the given stream. The appropriate request attributes should be set before
     * calling this method.
     *
     * @param sendfileData The stream and associated data to process
     *
     * @return The result of the send file processing
     */
    protected SendfileState processSendfile(SendfileData sendfileData) {
        return SendfileState.DONE;
    }


    private Set<AbstractStream> releaseBackLog(int increment) throws Http2Exception {
        windowAllocationLock.lock();
        try {
            Set<AbstractStream> result = new HashSet<>();
            if (backLogSize < increment) {
                // Can clear the whole backlog
                for (AbstractStream stream : backLogStreams) {
                    if (stream.getConnectionAllocationRequested() > 0) {
                        stream.setConnectionAllocationMade(stream.getConnectionAllocationRequested());
                        stream.setConnectionAllocationRequested(0);
                        result.add(stream);
                    }
                }
                // Cast is safe due to test above
                int remaining = increment - (int) backLogSize;
                backLogSize = 0;
                super.incrementWindowSize(remaining);

                backLogStreams.clear();
            } else {
                // Can't clear the whole backlog.
                // Need streams in priority order
                Set<Stream> orderedStreams = new ConcurrentSkipListSet<>(Comparator.comparingInt(Stream::getUrgency)
                        .thenComparing(Stream::getIncremental).thenComparing(Stream::getIdAsInt));
                orderedStreams.addAll(backLogStreams);

                // Iteration 1. Need to work out how much we can clear.
                long urgencyWhereAllocationIsExhausted = 0;
                long requestedAllocationForIncrementalStreams = 0;
                int remaining = increment;
                Iterator<Stream> orderedStreamsIterator = orderedStreams.iterator();
                while (orderedStreamsIterator.hasNext()) {
                    Stream s = orderedStreamsIterator.next();
                    if (urgencyWhereAllocationIsExhausted < s.getUrgency()) {
                        if (remaining < 1) {
                            break;
                        }
                        requestedAllocationForIncrementalStreams = 0;
                    }
                    urgencyWhereAllocationIsExhausted = s.getUrgency();
                    if (s.getIncremental()) {
                        requestedAllocationForIncrementalStreams += s.getConnectionAllocationRequested();
                        remaining -= s.getConnectionAllocationRequested();
                    } else {
                        remaining -= s.getConnectionAllocationRequested();
                        if (remaining < 1) {
                            break;
                        }
                    }
                }

                // Iteration 2. Allocate.
                // Reset for second iteration
                remaining = increment;
                orderedStreamsIterator = orderedStreams.iterator();
                while (orderedStreamsIterator.hasNext()) {
                    Stream s = orderedStreamsIterator.next();
                    if (s.getUrgency() < urgencyWhereAllocationIsExhausted) {
                        // Can fully allocate
                        remaining = allocate(s, remaining);
                        result.add(s);
                        orderedStreamsIterator.remove();
                        backLogStreams.remove(s);
                    } else if (requestedAllocationForIncrementalStreams == 0) {
                        // Allocation ran out in non-incremental streams so fully
                        // allocate in iterator order until allocation is exhausted
                        remaining = allocate(s, remaining);
                        result.add(s);
                        if (s.getConnectionAllocationRequested() == 0) {
                            // Fully allocated
                            orderedStreamsIterator.remove();
                            backLogStreams.remove(s);
                        }
                        if (remaining < 1) {
                            break;
                        }
                    } else {
                        // Allocation ran out in incremental streams. Distribute
                        // remaining allocation between the incremental streams at
                        // this urgency level.
                        if (s.getUrgency() != urgencyWhereAllocationIsExhausted) {
                            break;
                        }

                        int share = (int) (s.getConnectionAllocationRequested() * remaining /
                                requestedAllocationForIncrementalStreams);
                        if (share == 0) {
                            share = 1;
                        }
                        allocate(s, share);
                        result.add(s);
                        if (s.getConnectionAllocationRequested() == 0) {
                            // Fully allocated (unlikely but possible due to
                            // rounding if only a few bytes required).
                            orderedStreamsIterator.remove();
                            backLogStreams.remove(s);
                        }
                    }
                }
            }
            return result;
        } finally {
            windowAllocationLock.unlock();
        }
    }


    private int allocate(AbstractStream stream, int allocation) {
        windowAllocationLock.lock();
        try {
            if (log.isTraceEnabled()) {
                log.trace(sm.getString("upgradeHandler.allocate.debug", getConnectionId(), stream.getIdAsString(),
                        Integer.toString(allocation)));
            }

            int leftToAllocate = allocation;

            if (stream.getConnectionAllocationRequested() > 0) {
                int allocatedThisTime;
                if (allocation >= stream.getConnectionAllocationRequested()) {
                    allocatedThisTime = stream.getConnectionAllocationRequested();
                } else {
                    allocatedThisTime = allocation;
                }
                stream.setConnectionAllocationRequested(stream.getConnectionAllocationRequested() - allocatedThisTime);
                stream.setConnectionAllocationMade(stream.getConnectionAllocationMade() + allocatedThisTime);
                leftToAllocate = leftToAllocate - allocatedThisTime;
            }

            if (log.isTraceEnabled()) {
                log.trace(sm.getString("upgradeHandler.allocate.left", getConnectionId(), stream.getIdAsString(),
                        Integer.toString(leftToAllocate)));
            }

            return leftToAllocate;
        } finally {
            windowAllocationLock.unlock();
        }
    }


    Stream getStream(int streamId) {
        Integer key = Integer.valueOf(streamId);
        AbstractStream result = streams.get(key);
        if (result instanceof Stream) {
            return (Stream) result;
        }
        return null;
    }


    private Stream getStream(int streamId, boolean unknownIsError) throws ConnectionException {
        Stream result = getStream(streamId);
        if (result == null && unknownIsError) {
            // Stream has been closed and removed from the map
            throw new ConnectionException(sm.getString("upgradeHandler.stream.closed", Integer.toString(streamId)),
                    Http2Error.PROTOCOL_ERROR);
        }
        return result;
    }


    private AbstractNonZeroStream getAbstractNonZeroStream(int streamId) {
        Integer key = Integer.valueOf(streamId);
        return streams.get(key);
    }


    private AbstractNonZeroStream getAbstractNonZeroStream(int streamId, boolean unknownIsError)
            throws ConnectionException {
        AbstractNonZeroStream result = getAbstractNonZeroStream(streamId);
        if (result == null && unknownIsError) {
            // Stream has been closed and removed from the map
            throw new ConnectionException(sm.getString("upgradeHandler.stream.closed", Integer.toString(streamId)),
                    Http2Error.PROTOCOL_ERROR);
        }
        return result;
    }


    private Stream createRemoteStream(int streamId) throws ConnectionException {
        Integer key = Integer.valueOf(streamId);

        if (streamId % 2 != 1) {
            throw new ConnectionException(sm.getString("upgradeHandler.stream.even", key), Http2Error.PROTOCOL_ERROR);
        }

        pruneClosedStreams(streamId);

        Stream result = new Stream(key, this);
        streams.put(key, result);
        return result;
    }


    private void close() {
        ConnectionState previous = connectionState.getAndSet(ConnectionState.CLOSED);
        if (previous == ConnectionState.CLOSED) {
            // Already closed
            return;
        }

        for (AbstractNonZeroStream stream : streams.values()) {
            if (stream instanceof Stream) {
                // The connection is closing. Close the associated streams as no
                // longer required (also notifies any threads waiting for allocations).
                ((Stream) stream).receiveReset(Http2Error.CANCEL.getCode());
            }
        }
        try {
            socketWrapper.close();
        } catch (Exception e) {
            log.debug(sm.getString("upgradeHandler.socketCloseFailed"), e);
        }
    }


    private void pruneClosedStreams(int streamId) {
        // Only prune every 10 new streams
        if (newStreamsSinceLastPrune < 9) {
            // Not atomic. Increments may be lost. Not a problem.
            newStreamsSinceLastPrune++;
            return;
        }
        // Reset counter
        newStreamsSinceLastPrune = 0;

        // RFC 7540, 5.3.4 endpoints should maintain state for at least the
        // maximum number of concurrent streams.
        long max = localSettings.getMaxConcurrentStreams();

        // Ideally need to retain information for a "significant" amount of time
        // after sending END_STREAM (RFC 7540, page 20) so we detect potential
        // connection error. 5x seems reasonable. The client will have had
        // plenty of opportunity to process the END_STREAM if another 5x max
        // concurrent streams have been processed.
        max = max * 5;
        if (max > Integer.MAX_VALUE) {
            max = Integer.MAX_VALUE;
        }

        final int size = streams.size();
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("upgradeHandler.pruneStart", connectionId, Long.toString(max),
                    Integer.toString(size)));
        }

        int toClose = size - (int) max;

        // Need to try and prune some streams. Prune streams starting with the
        // oldest. Pruning stops as soon as enough streams have been pruned.
        // Iterator is in key order.
        for (AbstractNonZeroStream stream : streams.values()) {
            if (toClose < 1) {
                return;
            }
            if (stream instanceof Stream && ((Stream) stream).isActive()) {
                continue;
            }
            streams.remove(stream.getIdentifier());
            toClose--;
            if (log.isTraceEnabled()) {
                log.trace(sm.getString("upgradeHandler.pruned", connectionId, stream.getIdAsString()));
            }

        }

        if (toClose > 0) {
            log.warn(sm.getString("upgradeHandler.pruneIncomplete", connectionId, Integer.toString(streamId),
                    Integer.toString(toClose)));
        }
    }


    @Override
    protected final String getConnectionId() {
        return connectionId;
    }


    void reduceOverheadCount(FrameType frameType) {
        // A non-overhead frame reduces the overhead count by
        // Http2Protocol.DEFAULT_OVERHEAD_REDUCTION_FACTOR. A simple browser
        // request is likely to have one non-overhead frame (HEADERS) and one
        // overhead frame (REPRIORITISE). With the default settings the overhead
        // count will reduce by 10 for each simple request.
        // Requests and responses with bodies will create additional
        // non-overhead frames, further reducing the overhead count.
        updateOverheadCount(frameType, Http2Protocol.DEFAULT_OVERHEAD_REDUCTION_FACTOR);
    }


    @Override
    public void increaseOverheadCount(FrameType frameType) {
        // An overhead frame increases the overhead count by
        // overheadCountFactor. By default, this means an overhead frame
        // increases the overhead count by 10. A simple browser request is
        // likely to have one non-overhead frame (HEADERS) and one overhead
        // frame (REPRIORITISE). With the default settings the overhead count
        // will reduce by 10 for each simple request.
        updateOverheadCount(frameType, getProtocol().getOverheadCountFactor());
    }


    private void increaseOverheadCount(FrameType frameType, int increment) {
        // Overhead frames that indicate inefficient (and potentially malicious)
        // use of small frames trigger an increase that is inversely
        // proportional to size. The default threshold for all three potential
        // areas for abuse (HEADERS, DATA, WINDOW_UPDATE) is 1024 bytes. Frames
        // with sizes smaller than this will trigger an increase of
        // threshold/size.
        // DATA and WINDOW_UPDATE take an average over the last two non-final
        // frames to allow for client buffering schemes that can result in some
        // small DATA payloads.
        updateOverheadCount(frameType, increment);
    }


    private void updateOverheadCount(FrameType frameType, int increment) {
        long newOverheadCount = overheadCount.addAndGet(increment);
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("upgradeHandler.overheadChange", connectionId, getIdAsString(), frameType.name(),
                    Long.valueOf(newOverheadCount)));
        }
    }


    boolean isOverheadLimitExceeded() {
        return overheadCount.get() > 0;
    }


    // ----------------------------------------------- Http2Parser.Input methods

    @Override
    public boolean fill(boolean block, byte[] data, int offset, int length) throws IOException {
        int len = length;
        int pos = offset;
        boolean nextReadBlock = block;
        int thisRead = 0;

        while (len > 0) {
            // Blocking reads use the protocol level read timeout. Non-blocking
            // reads do not timeout. The intention is that once a frame has
            // started to be read, the read timeout applies until it is
            // completely read.
            if (nextReadBlock) {
                socketWrapper.setReadTimeout(protocol.getReadTimeout());
            } else {
                socketWrapper.setReadTimeout(-1);
            }
            thisRead = socketWrapper.read(nextReadBlock, data, pos, len);
            if (thisRead == 0) {
                if (nextReadBlock) {
                    // Should never happen
                    throw new IllegalStateException();
                } else {
                    return false;
                }
            } else if (thisRead == -1) {
                if (connectionState.get().isNewStreamAllowed()) {
                    throw new EOFException();
                } else {
                    return false;
                }
            } else {
                pos += thisRead;
                len -= thisRead;
                nextReadBlock = true;
            }
        }

        return true;
    }


    @Override
    public int getMaxFrameSize() {
        return localSettings.getMaxFrameSize();
    }


    // ---------------------------------------------- Http2Parser.Output methods

    @Override
    public HpackDecoder getHpackDecoder() {
        if (hpackDecoder == null) {
            hpackDecoder = new HpackDecoder(localSettings.getHeaderTableSize());
        }
        return hpackDecoder;
    }


    @Override
    public ByteBuffer startRequestBodyFrame(int streamId, int payloadSize, boolean endOfStream) throws Http2Exception {
        // DATA frames reduce the overhead count ...
        reduceOverheadCount(FrameType.DATA);

        // .. but lots of small payloads are inefficient so that will increase
        // the overhead count unless it is the final DATA frame where small
        // payloads are expected.

        // See also https://bz.apache.org/bugzilla/show_bug.cgi?id=63690
        // The buffering behaviour of some clients means that small data frames
        // are much more frequent (roughly 1 in 20) than expected. Use an
        // average over two frames to avoid false positives.
        if (!endOfStream) {
            int overheadThreshold = protocol.getOverheadDataThreshold();
            int average = (lastNonFinalDataPayload >> 1) + (payloadSize >> 1);
            lastNonFinalDataPayload = payloadSize;
            // Avoid division by zero
            if (average == 0) {
                average = 1;
            }
            if (average < overheadThreshold) {
                increaseOverheadCount(FrameType.DATA, overheadThreshold / average);
            }
        }

        AbstractNonZeroStream abstractNonZeroStream = getAbstractNonZeroStream(streamId, true);
        abstractNonZeroStream.checkState(FrameType.DATA);
        abstractNonZeroStream.receivedData(payloadSize);
        ByteBuffer result = abstractNonZeroStream.getInputByteBuffer(true);

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("upgradeHandler.startRequestBodyFrame.result", getConnectionId(),
                    abstractNonZeroStream.getIdAsString(), result));
        }

        return result;
    }


    @Override
    public void endRequestBodyFrame(int streamId, int dataLength) throws Http2Exception, IOException {
        AbstractNonZeroStream abstractNonZeroStream = getAbstractNonZeroStream(streamId, true);
        if (abstractNonZeroStream instanceof Stream) {
            ((Stream) abstractNonZeroStream).getInputBuffer().onDataAvailable();
        } else {
            // The Stream was recycled between the call in Http2Parser to
            // startRequestBodyFrame() and the synchronized block that contains
            // the call to this method. This means the bytes read will have been
            // written to the original stream and, effectively, swallowed.
            // Therefore, need to notify that those bytes were swallowed here.
            if (dataLength > 0) {
                onSwallowedDataFramePayload(streamId, dataLength);
            }
        }
    }


    @Override
    public void onSwallowedDataFramePayload(int streamId, int swallowedDataBytesCount) throws IOException {
        AbstractNonZeroStream abstractNonZeroStream = getAbstractNonZeroStream(streamId);
        writeWindowUpdate(abstractNonZeroStream, swallowedDataBytesCount, false);
    }


    @Override
    public HeaderEmitter headersStart(int streamId, boolean headersEndStream) throws Http2Exception, IOException {

        Stream stream = getStream(streamId, false);
        if (stream == null) {
            // New stream

            // Check the pause state before processing headers since the pause state
            // determines if a new stream is created or if this stream is ignored.
            checkPauseState();

            if (connectionState.get().isNewStreamAllowed()) {
                if (streamId > maxProcessedStreamId) {
                    stream = createRemoteStream(streamId);
                    activeRemoteStreamCount.incrementAndGet();
                } else {
                    // ID for new stream must always be greater than any previous stream
                    throw new ConnectionException(sm.getString("upgradeHandler.stream.old", Integer.valueOf(streamId),
                            Integer.valueOf(maxProcessedStreamId)), Http2Error.PROTOCOL_ERROR);
                }
            } else {
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("upgradeHandler.noNewStreams", connectionId, Integer.toString(streamId)));
                }
                reduceOverheadCount(FrameType.HEADERS);
                // Stateless so a static can be used to save on GC
                return HEADER_SINK;
            }
        }

        stream.checkState(FrameType.HEADERS);
        stream.receivedStartOfHeaders(headersEndStream);
        return stream;
    }


    @Override
    public void headersContinue(int payloadSize, boolean endOfHeaders) {
        // Generally, continuation frames don't impact the overhead count but if
        // they are small and the frame isn't the final header frame then that
        // is indicative of an abusive client
        if (!endOfHeaders) {
            int overheadThreshold = getProtocol().getOverheadContinuationThreshold();
            if (payloadSize < overheadThreshold) {
                if (payloadSize == 0) {
                    // Avoid division by zero
                    increaseOverheadCount(FrameType.HEADERS, overheadThreshold);
                } else {
                    increaseOverheadCount(FrameType.HEADERS, overheadThreshold / payloadSize);
                }
            }
        }
    }


    @Override
    public void headersEnd(int streamId, boolean endOfStream) throws Http2Exception {
        AbstractNonZeroStream abstractNonZeroStream =
                getAbstractNonZeroStream(streamId, connectionState.get().isNewStreamAllowed());
        if (abstractNonZeroStream instanceof Stream) {
            boolean processStream = false;
            setMaxProcessedStream(streamId);
            Stream stream = (Stream) abstractNonZeroStream;
            if (stream.isActive()) {
                if (stream.receivedEndOfHeaders()) {
                    if (localSettings.getMaxConcurrentStreams() < activeRemoteStreamCount.get()) {
                        decrementActiveRemoteStreamCount(stream);
                        // Ignoring maxConcurrentStreams increases the overhead count
                        increaseOverheadCount(FrameType.HEADERS);
                        throw new StreamException(
                                sm.getString("upgradeHandler.tooManyRemoteStreams",
                                        Long.toString(localSettings.getMaxConcurrentStreams())),
                                Http2Error.REFUSED_STREAM, streamId);
                    }
                    // Valid new stream reduces the overhead count
                    reduceOverheadCount(FrameType.HEADERS);

                    processStream = true;
                }
            }
            /*
             * Need to process end of stream before calling processStreamOnContainerThread to avoid a race condition
             * where the container thread finishes before end of stream is processed, thinks the request hasn't been
             * fully read so issues a RST with error code 0 (NO_ERROR) to tell the client not to send the request body,
             * if any. This breaks tests and generates unnecessary RST messages for standard clients.
             */
            if (endOfStream) {
                receivedEndOfStream(stream);
            }
            if (processStream) {
                processStreamOnContainerThread(stream);
            }
        }
    }


    @Override
    public void receivedEndOfStream(int streamId) throws ConnectionException {
        AbstractNonZeroStream abstractNonZeroStream =
                getAbstractNonZeroStream(streamId, connectionState.get().isNewStreamAllowed());
        if (abstractNonZeroStream instanceof Stream) {
            Stream stream = (Stream) abstractNonZeroStream;
            receivedEndOfStream(stream);
        }
    }


    private void receivedEndOfStream(Stream stream) throws ConnectionException {
        stream.receivedEndOfStream();
        if (!stream.isActive()) {
            decrementActiveRemoteStreamCount(stream);
        }
    }


    private void setMaxProcessedStream(int streamId) {
        if (maxProcessedStreamId < streamId) {
            maxProcessedStreamId = streamId;
        }
    }


    @Override
    public void reset(int streamId, long errorCode) throws Http2Exception {
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("upgradeHandler.reset.receive", getConnectionId(), Integer.toString(streamId),
                    Long.toString(errorCode)));
        }
        increaseOverheadCount(FrameType.RST, getProtocol().getOverheadResetFactor());
        AbstractNonZeroStream abstractNonZeroStream = getAbstractNonZeroStream(streamId, true);
        abstractNonZeroStream.checkState(FrameType.RST);
        if (abstractNonZeroStream instanceof Stream) {
            Stream stream = (Stream) abstractNonZeroStream;
            boolean active = stream.isActive();
            stream.receiveReset(errorCode);
            if (active) {
                decrementActiveRemoteStreamCount(stream);
            }
        }
    }


    @Override
    public void setting(Setting setting, long value) throws ConnectionException {

        increaseOverheadCount(FrameType.SETTINGS);

        // Possible with empty settings frame
        if (setting == null) {
            return;
        }

        // Special handling required
        if (setting == Setting.INITIAL_WINDOW_SIZE) {
            long oldValue = remoteSettings.getInitialWindowSize();
            // Do this first in case new value is invalid
            remoteSettings.set(setting, value);
            int diff = (int) (value - oldValue);
            for (AbstractNonZeroStream stream : streams.values()) {
                try {
                    stream.incrementWindowSize(diff);
                } catch (Http2Exception h2e) {
                    ((Stream) stream).close(new StreamException(
                            sm.getString("upgradeHandler.windowSizeTooBig", connectionId, stream.getIdAsString()),
                            h2e.getError(), stream.getIdAsInt()));
                }
            }
        } else if (setting == Setting.NO_RFC7540_PRIORITIES) {
            // This should not be changed after the initial setting
            if (value != ConnectionSettingsBase.DEFAULT_NO_RFC7540_PRIORITIES) {
                throw new ConnectionException(sm.getString("upgradeHandler.enableRfc7450Priorities", connectionId),
                        Http2Error.PROTOCOL_ERROR);
            }
        } else {
            remoteSettings.set(setting, value);
        }
    }


    @Override
    public void settingsEnd(boolean ack) throws IOException {
        if (ack) {
            if (!localSettings.ack()) {
                // Ack was unexpected
                log.warn(sm.getString("upgradeHandler.unexpectedAck", connectionId, getIdAsString()));
            }
        } else {
            socketWrapper.getLock().lock();
            try {
                socketWrapper.write(true, SETTINGS_ACK, 0, SETTINGS_ACK.length);
                socketWrapper.flush(true);
            } finally {
                socketWrapper.getLock().unlock();
            }
        }
    }


    @Override
    public void pingReceive(byte[] payload, boolean ack) throws IOException {
        if (!ack) {
            increaseOverheadCount(FrameType.PING);
        }
        pingManager.receivePing(payload, ack);
    }


    @Override
    public void goaway(int lastStreamId, long errorCode, String debugData) {
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("upgradeHandler.goaway.debug", connectionId, Integer.toString(lastStreamId),
                    Long.toHexString(errorCode), debugData));
        }
        close();
    }


    @Override
    public void incrementWindowSize(int streamId, int increment) throws Http2Exception {
        // See also https://bz.apache.org/bugzilla/show_bug.cgi?id=63690
        // The buffering behaviour of some clients means that small data frames
        // are much more frequent (roughly 1 in 20) than expected. Some clients
        // issue a Window update for every DATA frame so a similar pattern may
        // be observed. Use an average over two frames to avoid false positives.

        int average = (lastWindowUpdate >> 1) + (increment >> 1);
        int overheadThreshold = protocol.getOverheadWindowUpdateThreshold();
        lastWindowUpdate = increment;
        // Avoid division by zero
        if (average == 0) {
            average = 1;
        }

        if (streamId == 0) {
            // Check for small increments which are inefficient
            if (average < overheadThreshold) {
                // The smaller the increment, the larger the overhead
                increaseOverheadCount(FrameType.WINDOW_UPDATE, overheadThreshold / average);
            }

            incrementWindowSize(increment);
        } else {
            AbstractNonZeroStream stream = getAbstractNonZeroStream(streamId, true);

            // Check for small increments which are inefficient
            if (average < overheadThreshold) {
                // For Streams, client might only release the minimum so check
                // against current demand
                if (increment < stream.getConnectionAllocationRequested()) {
                    // The smaller the increment, the larger the overhead
                    increaseOverheadCount(FrameType.WINDOW_UPDATE, overheadThreshold / average);
                }
            }

            stream.checkState(FrameType.WINDOW_UPDATE);
            stream.incrementWindowSize(increment);
        }
    }


    @Override
    public void priorityUpdate(int prioritizedStreamID, Priority p) throws Http2Exception {
        increaseOverheadCount(FrameType.PRIORITY_UPDATE);
        AbstractNonZeroStream abstractNonZeroStream = getAbstractNonZeroStream(prioritizedStreamID, true);
        if (abstractNonZeroStream instanceof Stream) {
            Stream stream = (Stream) abstractNonZeroStream;
            stream.setUrgency(p.getUrgency());
            stream.setIncremental(p.getIncremental());
        }
    }


    @Override
    public void onSwallowedUnknownFrame(int streamId, int frameTypeId, int flags, int size) throws IOException {
        // NO-OP.
    }


    void replaceStream(AbstractNonZeroStream original, AbstractNonZeroStream replacement) {
        AbstractNonZeroStream current = streams.get(original.getIdentifier());
        /*
         * Only replace the Stream once. No point replacing one RecycledStream instance with another.
         *
         * This method is called from both StreamProcessor and Http2UpgradeHandler which may be operating on the Stream
         * concurrently. It is therefore expected that there will be duplicate calls to this method - primarily
         * triggered by stream errors when processing incoming frames.
         */
        if (current instanceof Stream) {
            if (log.isTraceEnabled()) {
                log.trace(sm.getString("upgradeHandler.replace.first", getConnectionId(), original.getIdAsString()));
            }
            streams.put(original.getIdentifier(), replacement);
        } else {
            if (log.isTraceEnabled()) {
                log.trace(
                        sm.getString("upgradeHandler.replace.duplicate", getConnectionId(), original.getIdAsString()));
            }
        }
    }


    public ServletConnection getServletConnection() {
        if (socketWrapper.getSslSupport() == null) {
            return socketWrapper.getServletConnection("h2c", "");
        } else {
            return socketWrapper.getServletConnection("h2", "");
        }
    }

    protected class PingManager {

        protected boolean initiateDisabled = false;

        // 10 seconds
        protected final long pingIntervalNano = 10000000000L;

        protected int sequence = 0;
        protected long lastPingNanoTime = Long.MIN_VALUE;

        protected Queue<PingRecord> inflightPings = new ConcurrentLinkedQueue<>();
        protected Queue<Long> roundTripTimes = new ConcurrentLinkedQueue<>();

        /**
         * Check to see if a ping was sent recently and, if not, send one.
         *
         * @param force Send a ping, even if one was sent recently
         *
         * @throws IOException If an I/O issue prevents the ping from being sent
         */
        public void sendPing(boolean force) throws IOException {
            if (initiateDisabled) {
                return;
            }
            long now = System.nanoTime();
            if (force || now - lastPingNanoTime > pingIntervalNano) {
                lastPingNanoTime = now;
                byte[] payload = new byte[8];
                socketWrapper.getLock().lock();
                try {
                    int sentSequence = ++sequence;
                    PingRecord pingRecord = new PingRecord(sentSequence, now);
                    inflightPings.add(pingRecord);
                    ByteUtil.set31Bits(payload, 4, sentSequence);
                    socketWrapper.write(true, PING, 0, PING.length);
                    socketWrapper.write(true, payload, 0, payload.length);
                    socketWrapper.flush(true);
                } finally {
                    socketWrapper.getLock().unlock();
                }
            }
        }

        public void receivePing(byte[] payload, boolean ack) throws IOException {
            if (ack) {
                // Extract the sequence from the payload
                int receivedSequence = ByteUtil.get31Bits(payload, 4);
                PingRecord pingRecord = inflightPings.poll();
                while (pingRecord != null && pingRecord.getSequence() < receivedSequence) {
                    pingRecord = inflightPings.poll();
                }
                if (pingRecord == null) {
                    // Unexpected ACK. Log it.
                } else {
                    long roundTripTime = System.nanoTime() - pingRecord.getSentNanoTime();
                    roundTripTimes.add(Long.valueOf(roundTripTime));
                    while (roundTripTimes.size() > 3) {
                        // Ignore the returned value as we just want to reduce
                        // the queue to 3 entries to use for the rolling average.
                        roundTripTimes.poll();
                    }
                    if (log.isTraceEnabled()) {
                        log.trace(sm.getString("pingManager.roundTripTime", connectionId, Long.valueOf(roundTripTime)));
                    }
                }

            } else {
                // Client originated ping. Echo it back.
                socketWrapper.getLock().lock();
                try {
                    socketWrapper.write(true, PING_ACK, 0, PING_ACK.length);
                    socketWrapper.write(true, payload, 0, payload.length);
                    socketWrapper.flush(true);
                } finally {
                    socketWrapper.getLock().unlock();
                }
            }
        }

        public long getRoundTripTimeNano() {
            return (long) roundTripTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        }
    }


    protected static class PingRecord {

        private final int sequence;
        private final long sentNanoTime;

        public PingRecord(int sequence, long sentNanoTime) {
            this.sequence = sequence;
            this.sentNanoTime = sentNanoTime;
        }

        public int getSequence() {
            return sequence;
        }

        public long getSentNanoTime() {
            return sentNanoTime;
        }
    }


    private enum ConnectionState {

        NEW(true),
        CONNECTED(true),
        PAUSING(true),
        PAUSED(false),
        CLOSED(false);

        private final boolean newStreamsAllowed;

        ConnectionState(boolean newStreamsAllowed) {
            this.newStreamsAllowed = newStreamsAllowed;
        }

        public boolean isNewStreamAllowed() {
            return newStreamsAllowed;
        }
    }


    protected interface HeaderFrameBuffers {
        void startFrame();

        void endFrame() throws IOException;

        void endHeaders() throws IOException;

        byte[] getHeader();

        ByteBuffer getPayload();

        void expandPayload();
    }


    private class DefaultHeaderFrameBuffers implements HeaderFrameBuffers {

        private final byte[] header;
        private ByteBuffer payload;

        DefaultHeaderFrameBuffers(int initialPayloadSize) {
            header = new byte[9];
            payload = ByteBuffer.allocate(initialPayloadSize);
        }

        @Override
        public void startFrame() {
            // NO-OP
        }


        @Override
        public void endFrame() throws IOException {
            try {
                socketWrapper.write(true, header, 0, header.length);
                socketWrapper.write(true, payload);
                socketWrapper.flush(true);
            } catch (IOException ioe) {
                handleAppInitiatedIOException(ioe);
            }
            payload.clear();
        }

        @Override
        public void endHeaders() {
            // NO-OP
        }

        @Override
        public byte[] getHeader() {
            return header;
        }

        @Override
        public ByteBuffer getPayload() {
            return payload;
        }

        @Override
        public void expandPayload() {
            payload = ByteBuffer.allocate(payload.capacity() * 2);
        }
    }
}
