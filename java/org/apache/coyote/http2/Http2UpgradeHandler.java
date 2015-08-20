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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.http.WebConnection;

import org.apache.coyote.Adapter;
import org.apache.coyote.ProtocolException;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.coyote.http2.HpackDecoder.HeaderEmitter;
import org.apache.coyote.http2.HpackEncoder.State;
import org.apache.coyote.http2.Http2Parser.Input;
import org.apache.coyote.http2.Http2Parser.Output;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.codec.binary.Base64;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * This represents an HTTP/2 connection from a client to Tomcat. It is designed
 * on the basis that there will never be more than one thread performing I/O at
 * a time.
 * <br>
 * For reading, this implementation is blocking within frames and non-blocking
 * between frames.
 * <br>
 * Note:
 * <ul>
 * <li>Unless Tomcat is configured with an ECC certificate, FireFox (tested with
 *     v37.0.2) needs to be configured with
 *     network.http.spdy.enforce-tls-profile=false in order for FireFox to be
 *     able to connect.</li>
 * <li>You will need to nest an &lt;UpgradeProtocol
 *     className="org.apache.coyote.http2.Http2Protocol" /&gt; element inside
 *     a TLS enabled Connector element in server.xml to enable HTTP/2 support.
 *     </li>
 * </ul>
 *
 * TODO: Review cookie parsing
 */
public class Http2UpgradeHandler extends AbstractStream implements InternalHttpUpgradeHandler,
        Input, Output {

    private static final Log log = LogFactory.getLog(Http2UpgradeHandler.class);
    private static final StringManager sm = StringManager.getManager(Http2UpgradeHandler.class);

    private static final AtomicInteger connectionIdGenerator = new AtomicInteger(0);
    private static final Integer STREAM_ID_ZERO = Integer.valueOf(0);

    private static final int FLAG_END_OF_STREAM = 1;
    private static final int FLAG_END_OF_HEADERS = 4;

    private static final byte[] PING = { 0x00, 0x00, 0x08, 0x06, 0x00, 0x00, 0x00, 0x00, 0x00};
    private static final byte[] PING_ACK = { 0x00, 0x00, 0x08, 0x06, 0x01, 0x00, 0x00, 0x00, 0x00 };

    private static final byte[] SETTINGS_ACK = { 0x00, 0x00, 0x00, 0x04, 0x01, 0x00, 0x00, 0x00, 0x00 };

    private static final byte[] GOAWAY = { 0x07, 0x00, 0x00, 0x00, 0x00, 0x00 };

    private static final String HTTP2_SETTINGS_HEADER = "HTTP2-Settings";
    private static final byte[] HTTP2_UPGRADE_ACK = ("HTTP/1.1 101 Switching Protocols\r\n" +
                "Connection: Upgrade\r\nUpgrade: h2c\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1);

    private static final HeaderSink HEADER_SINK = new HeaderSink();

    private final String connectionId;

    private final Adapter adapter;
    private volatile SocketWrapperBase<?> socketWrapper;
    private volatile SSLSupport sslSupport;

    private volatile Http2Parser parser;

    // Simple state machine (sequence of states)
    private AtomicReference<ConnectionState> connectionState =
            new AtomicReference<>(ConnectionState.NEW);
    private volatile long pausedNanoTime = Long.MAX_VALUE;

    private final ConnectionSettingsRemote remoteSettings = new ConnectionSettingsRemote();
    private final ConnectionSettingsLocal localSettings = new ConnectionSettingsLocal();

    private HpackDecoder hpackDecoder;
    private HpackEncoder hpackEncoder;

    // All timeouts in milliseconds
    private long readTimeout = Http2Protocol.DEFAULT_READ_TIMEOUT;
    private long keepAliveTimeout = Http2Protocol.DEFAULT_KEEP_ALIVE_TIMEOUT;
    private long writeTimeout = Http2Protocol.DEFAULT_WRITE_TIMEOUT;

    private final Map<Integer,Stream> streams = new HashMap<>();
    private final AtomicInteger activeRemoteStreamCount = new AtomicInteger(0);
    private volatile int maxRemoteStreamId = 0;
    // Start at -1 so the 'add 2' logic in closeIdleStreams() works
    private volatile int maxActiveRemoteStreamId = -1;
    private volatile int maxProcessedStreamId;
    private final PingManager pingManager = new PingManager();

    // Tracking for when the connection is blocked (windowSize < 1)
    private final Map<AbstractStream,int[]> backLogStreams = new ConcurrentHashMap<>();
    private long backLogSize = 0;


    public Http2UpgradeHandler(Adapter adapter, Request coyoteRequest) {
        super (STREAM_ID_ZERO);
        this.adapter = adapter;
        this.connectionId = Integer.toString(connectionIdGenerator.getAndIncrement());

        // Initial HTTP request becomes stream 1.
        if (coyoteRequest != null) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("upgradeHandler.upgrade", connectionId));
            }
            Integer key = Integer.valueOf(1);
            Stream stream = new Stream(key, this, coyoteRequest);
            streams.put(key, stream);
            maxRemoteStreamId = 1;
            maxActiveRemoteStreamId = 1;
            activeRemoteStreamCount.set(1);
            maxProcessedStreamId = 1;
        }
    }


    @Override
    public void init(WebConnection webConnection) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.init", connectionId));
        }

        if (!connectionState.compareAndSet(ConnectionState.NEW, ConnectionState.CONNECTED)) {
            return;
        }

        parser = new Http2Parser(connectionId, this, this);

        Stream stream = null;

        socketWrapper.setReadTimeout(getReadTimeout());
        socketWrapper.setWriteTimeout(getWriteTimeout());

        if (webConnection != null) {
            // HTTP/2 started via HTTP upgrade.
            // The initial HTTP/1.1 request is available as Stream 1.

            try {
                // Acknowledge the upgrade request
                socketWrapper.write(true, HTTP2_UPGRADE_ACK, 0, HTTP2_UPGRADE_ACK.length);
                socketWrapper.flush(true);

                // Process the initial settings frame
                stream = getStream(1, true);
                String base64Settings = stream.getCoyoteRequest().getHeader(HTTP2_SETTINGS_HEADER);
                byte[] settings = Base64.decodeBase64(base64Settings);

                // Settings are only valid on stream 0
                FrameType.SETTINGS.check(0, settings.length);

                for (int i = 0; i < settings.length % 6; i++) {
                    int id = ByteUtil.getTwoBytes(settings, i * 6);
                    long value = ByteUtil.getFourBytes(settings, (i * 6) + 2);
                    remoteSettings.set(Setting.valueOf(id), value);
                }
            } catch (Http2Exception | IOException ioe) {
                throw new ProtocolException(
                        sm.getString("upgradeHandler.upgrade.fail", connectionId));
            }
        }

        // Send the initial settings frame
        try {
            byte[] settings = localSettings.getSettingsFrameForPending();
            socketWrapper.write(true, settings, 0, settings.length);
            socketWrapper.flush(true);
        } catch (IOException ioe) {
            throw new IllegalStateException(sm.getString("upgradeHandler.sendPrefaceFail"), ioe);
        }

        // Make sure the client has sent a valid connection preface before we
        // send the response to the original request over HTTP/2.
        try {
            parser.readConnectionPreface();
        } catch (Http2Exception e) {
            throw new ProtocolException(
                    sm.getString("upgradeHandler.invalidPreface", connectionId));
        }

        // Send a ping to get an idea of round trip time as early as possible
        try {
            pingManager.sendPing(true);
        } catch (IOException ioe) {
            throw new ProtocolException(sm.getString("upgradeHandler.pingFailed"), ioe);
        }

        if (webConnection != null) {
            // Process the initial request on a container thread
            StreamProcessor streamProcessor = new StreamProcessor(stream, adapter, socketWrapper);
            streamProcessor.setSslSupport(sslSupport);
            socketWrapper.getEndpoint().getExecutor().execute(streamProcessor);
        }
    }


    @Override
    public void setSocketWrapper(SocketWrapperBase<?> wrapper) {
        this.socketWrapper = wrapper;
    }


    @Override
    public void setSslSupport(SSLSupport sslSupport) {
        this.sslSupport = sslSupport;
    }


    @Override
    public SocketState upgradeDispatch(SocketStatus status) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.upgradeDispatch.entry", connectionId, status));
        }

        // WebConnection is not used so passing null here is fine
        // Might not be necessary. init() will handle that.
        init(null);


        SocketState result = SocketState.CLOSED;

        try {
            pingManager.sendPing(false);

            checkPauseState();

            switch(status) {
            case OPEN_READ:
                try {

                    while (true) {
                        try {
                            if (!parser.readFrame(false)) {
                                break;
                            }
                        } catch (StreamException se) {
                            // Stream errors are not fatal to the connection so
                            // continue reading frames
                            closeStream(se);
                        }
                    }
                } catch (Http2Exception ce) {
                    // Really ConnectionError
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("upgradeHandler.connectionError"), ce);
                    }
                    closeConnection(ce);
                    break;
                }

                result = SocketState.UPGRADED;
                break;

                case OPEN_WRITE:
                processWrites();

                result = SocketState.UPGRADED;
                break;

            case ASYNC_READ_ERROR:
            case ASYNC_WRITE_ERROR:
            case CLOSE_NOW:
                // This should never happen and will be fatal for this connection.
                // Add the exception to trace how this point was reached.
                log.error(sm.getString("upgradeHandler.unexpectedStatus", status),
                        new IllegalStateException());
                //$FALL-THROUGH$
            case DISCONNECT:
            case ERROR:
            case TIMEOUT:
            case STOP:
                // For all of the above, including the unexpected values, close the
                // connection.
                close();
                break;
            }
        } catch (IOException ioe) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("upgradeHandler.ioerror", connectionId), ioe);
            }
            close();
        }

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.upgradeDispatch.exit", connectionId, result));
        }
        return result;
    }


    ConnectionSettingsRemote getRemoteSettings() {
        return remoteSettings;
    }


    @Override
    public void pause() {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.pause.entry", connectionId));
        }

        if (connectionState.compareAndSet(ConnectionState.CONNECTED, ConnectionState.PAUSING)) {
            pausedNanoTime = System.nanoTime();

            // Write a GOAWAY frame.
            byte[] fixedPayload = new byte[8];
            ByteUtil.set31Bits(fixedPayload, 0, (1 << 31) - 1);
            ByteUtil.setFourBytes(fixedPayload, 4, Http2Error.NO_ERROR.getCode());
            byte[] payloadLength = new byte[3];
            ByteUtil.setThreeBytes(payloadLength, 0, 8);

            try {
                synchronized (socketWrapper) {
                    socketWrapper.write(true, payloadLength, 0, payloadLength.length);
                    socketWrapper.write(true, GOAWAY, 0, GOAWAY.length);
                    socketWrapper.write(true, fixedPayload, 0, 8);
                    socketWrapper.flush(true);
                }
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


    private void checkPauseState() throws IOException {
        if (connectionState.get() == ConnectionState.PAUSING) {
            if (pausedNanoTime + pingManager.getRoundTripTimeNano() < System.nanoTime()) {
                connectionState.compareAndSet(ConnectionState.PAUSING, ConnectionState.PAUSED);

                // Write a GOAWAY frame.
                byte[] fixedPayload = new byte[8];
                ByteUtil.set31Bits(fixedPayload, 0, maxProcessedStreamId);
                ByteUtil.setFourBytes(fixedPayload, 4, Http2Error.NO_ERROR.getCode());
                byte[] payloadLength = new byte[3];
                ByteUtil.setThreeBytes(payloadLength, 0, 8);

                synchronized (socketWrapper) {
                    socketWrapper.write(true, payloadLength, 0, payloadLength.length);
                    socketWrapper.write(true, GOAWAY, 0, GOAWAY.length);
                    socketWrapper.write(true, fixedPayload, 0, 8);
                    socketWrapper.flush(true);
                }

            }
        }
    }


    private void closeStream(StreamException se) throws ConnectionException, IOException {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.rst.debug", connectionId,
                    Integer.toString(se.getStreamId()), se.getError()));
        }

        Stream stream = getStream(se.getStreamId(), false);
        if (stream != null) {
            stream.sendRst();
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

        synchronized (socketWrapper) {
            socketWrapper.write(true, rstFrame, 0, rstFrame.length);
            socketWrapper.flush(true);
        }
    }


    private void closeConnection(Http2Exception ce) {
        // Write a GOAWAY frame.
        byte[] fixedPayload = new byte[8];
        ByteUtil.set31Bits(fixedPayload, 0, maxProcessedStreamId);
        ByteUtil.setFourBytes(fixedPayload, 4, ce.getError().getCode());
        byte[] debugMessage = ce.getMessage().getBytes(StandardCharsets.UTF_8);
        byte[] payloadLength = new byte[3];
        ByteUtil.setThreeBytes(payloadLength, 0, debugMessage.length + 8);

        try {
            synchronized (socketWrapper) {
                socketWrapper.write(true, payloadLength, 0, payloadLength.length);
                socketWrapper.write(true, GOAWAY, 0, GOAWAY.length);
                socketWrapper.write(true, fixedPayload, 0, 8);
                socketWrapper.write(true, debugMessage, 0, debugMessage.length);
                socketWrapper.flush(true);
            }
        } catch (IOException ioe) {
            // Ignore. GOAWAY is sent on a best efforts basis and the original
            // error has already been logged.
        }
        close();
    }


    void writeHeaders(Stream stream, Response coyoteResponse) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.writeHeaders", connectionId,
                    stream.getIdentifier()));
        }
        MimeHeaders headers = coyoteResponse.getMimeHeaders();
        // Add the pseudo header for status
        headers.addValue(":status").setString(Integer.toString(coyoteResponse.getStatus()));
        // This ensures the Stream processing thread has control of the socket.
        synchronized (socketWrapper) {
            // Frame sizes are allowed to be bigger than 4k but for headers that
            // should be plenty
            byte[] header = new byte[9];
            ByteBuffer target = ByteBuffer.allocate(4 * 1024);
            boolean first = true;
            State state = null;
            while (state != State.COMPLETE) {
                state = getHpackEncoder().encode(coyoteResponse.getMimeHeaders(), target);
                target.flip();
                ByteUtil.setThreeBytes(header, 0, target.limit());
                if (first) {
                    first = false;
                    header[3] = FrameType.HEADERS.getIdByte();
                    if (stream.getOutputBuffer().hasNoBody()) {
                        header[4] = FLAG_END_OF_STREAM;
                    }
                } else {
                    header[3] = FrameType.CONTINUATION.getIdByte();
                }
                if (state == State.COMPLETE) {
                    header[4] += FLAG_END_OF_HEADERS;
                }
                if (log.isDebugEnabled()) {
                    log.debug(target.limit() + " bytes");
                }
                ByteUtil.set31Bits(header, 5, stream.getIdentifier().intValue());
                socketWrapper.write(true, header, 0, header.length);
                socketWrapper.write(true, target.array(), target.arrayOffset(), target.limit());
                socketWrapper.flush(true);
            }
        }
    }


    private HpackEncoder getHpackEncoder() {
        if (hpackEncoder == null) {
            hpackEncoder = new HpackEncoder(localSettings.getHeaderTableSize());
        }
        return hpackEncoder;
    }


    void writeBody(Stream stream, ByteBuffer data, int len, boolean finished) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.writeBody", connectionId, stream.getIdentifier(),
                    Integer.toString(len)));
        }
        synchronized (socketWrapper) {
            byte[] header = new byte[9];
            ByteUtil.setThreeBytes(header, 0, len);
            header[3] = FrameType.DATA.getIdByte();
            if (finished) {
                header[4] = FLAG_END_OF_STREAM;
                stream.sentEndOfStream();
                if (!stream.isActive()) {
                    activeRemoteStreamCount.decrementAndGet();
                }
             }
            ByteUtil.set31Bits(header, 5, stream.getIdentifier().intValue());
            socketWrapper.write(true, header, 0, header.length);
            socketWrapper.write(true, data.array(), data.arrayOffset() + data.position(),
                    len);
            socketWrapper.flush(true);
        }
    }


    void writeWindowUpdate(Stream stream, int increment) throws IOException {
        synchronized (socketWrapper) {
            // Build window update frame for stream 0
            byte[] frame = new byte[13];
            ByteUtil.setThreeBytes(frame, 0,  4);
            frame[3] = FrameType.WINDOW_UPDATE.getIdByte();
            ByteUtil.set31Bits(frame, 9, increment);
            socketWrapper.write(true, frame, 0, frame.length);
            // Change stream Id and re-use
            ByteUtil.set31Bits(frame, 5, stream.getIdentifier().intValue());
            socketWrapper.write(true, frame, 0, frame.length);
        }
    }


    private void processWrites() throws IOException {
        synchronized (socketWrapper) {
            if (socketWrapper.flush(false)) {
                socketWrapper.registerWriteInterest();
                return;
            }
        }
    }


    int reserveWindowSize(Stream stream, int reservation) throws IOException {
        // Need to be holding the stream lock so releaseBacklog() can't notify
        // this thread until after this thread enters wait()
        int allocation = 0;
        synchronized (stream) {
            do {
                synchronized (this) {
                    long windowSize = getWindowSize();
                    if (windowSize < 1 || backLogSize > 0) {
                        // Has this stream been granted an allocation
                        int[] value = backLogStreams.remove(stream);
                        if (value != null && value[1] > 0) {
                            allocation = value[1];
                            decrementWindowSize(allocation);
                        } else {
                            value = new int[] { reservation, 0 };
                            backLogStreams.put(stream, value);
                            backLogSize += reservation;
                            // Add the parents as well
                            AbstractStream parent = stream.getParentStream();
                            while (parent != null && backLogStreams.putIfAbsent(parent, new int[2]) == null) {
                                parent = parent.getParentStream();
                            }
                        }
                    } else if (windowSize < reservation) {
                        allocation = (int) windowSize;
                        decrementWindowSize(allocation);
                    } else {
                        allocation = reservation;
                        decrementWindowSize(allocation);
                    }
                }
                if (allocation == 0) {
                    try {
                        stream.wait();
                    } catch (InterruptedException e) {
                        throw new IOException(sm.getString(
                                "upgradeHandler.windowSizeReservationInterrupted", connectionId,
                                stream.getIdentifier(), Integer.toString(reservation)), e);
                    }
                }
            } while (allocation == 0);
        }
        return allocation;
    }



    @Override
    protected synchronized void incrementWindowSize(int increment) throws Http2Exception {
        long windowSize = getWindowSize();
        if (windowSize < 1 && windowSize + increment > 0) {
            releaseBackLog(increment);
        }
        super.incrementWindowSize(increment);
    }


    private synchronized void releaseBackLog(int increment) {
        if (backLogSize < increment) {
            // Can clear the whole backlog
            for (AbstractStream stream : backLogStreams.keySet()) {
                synchronized (stream) {
                    stream.notifyAll();
                }
            }
            backLogStreams.clear();
            backLogSize = 0;
        } else {
            int leftToAllocate = increment;
            while (leftToAllocate > 0) {
                leftToAllocate = allocate(this, leftToAllocate);
            }
            for (Entry<AbstractStream,int[]> entry : backLogStreams.entrySet()) {
                int allocation = entry.getValue()[1];
                if (allocation > 0) {
                    backLogSize -= allocation;
                    synchronized (entry.getKey()) {
                        entry.getKey().notifyAll();
                    }
                }
            }
        }
    }


    private int allocate(AbstractStream stream, int allocation) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.allocate.debug", getConnectionId(),
                    stream.getIdentifier(), Integer.toString(allocation)));
        }
        // Allocate to the specified stream
        int[] value = backLogStreams.get(stream);
        if (value[0] >= allocation) {
            value[0] -= allocation;
            value[1] = allocation;
            return 0;
        }

        // There was some left over so allocate that to the children of the
        // stream.
        int leftToAllocate = allocation;
        value[1] = value[0];
        value[0] = 0;
        leftToAllocate -= value[1];

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.allocate.left",
                    getConnectionId(), stream.getIdentifier(), Integer.toString(leftToAllocate)));
        }

        // Recipients are children of the current stream that are in the
        // backlog.
        Set<AbstractStream> recipients = new HashSet<>();
        recipients.addAll(stream.getChildStreams());
        recipients.retainAll(backLogStreams.keySet());

        // Loop until we run out of allocation or recipients
        while (leftToAllocate > 0) {
            if (recipients.size() == 0) {
                backLogStreams.remove(stream);
                return leftToAllocate;
            }

            int totalWeight = 0;
            for (AbstractStream recipient : recipients) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("upgradeHandler.allocate.recipient",
                            getConnectionId(), stream.getIdentifier(), recipient.getIdentifier(),
                            Integer.toString(recipient.getWeight())));
                }
                totalWeight += recipient.getWeight();
            }

            // Use an Iterator so fully allocated children/recipients can be
            // removed.
            Iterator<AbstractStream> iter = recipients.iterator();
            int allocated = 0;
            while (iter.hasNext()) {
                AbstractStream recipient = iter.next();
                int share = leftToAllocate * recipient.getWeight() / totalWeight;
                if (share == 0) {
                    // This is to avoid rounding issues triggering an infinite
                    // loop. It will cause a very slight over allocation but
                    // HTTP/2 should cope with that.
                    share = 1;
                }
                int remainder = allocate(recipient, share);
                // Remove recipients that receive their full allocation so that
                // they are excluded from the next allocation round.
                if (remainder > 0) {
                    iter.remove();
                }
                allocated += (share - remainder);
            }
            leftToAllocate -= allocated;
        }

        return 0;
    }


    private Stream getStream(int streamId, boolean unknownIsError) throws ConnectionException {
        Integer key = Integer.valueOf(streamId);
        Stream result = streams.get(key);
        if (result == null && unknownIsError) {
            // Stream has been closed and removed from the map
            throw new ConnectionException(sm.getString("upgradeHandler.stream.closed", key),
                    Http2Error.PROTOCOL_ERROR);
        }
        return result;
    }


    private Stream createRemoteStream(int streamId) throws ConnectionException {
        Integer key = Integer.valueOf(streamId);

        if (streamId %2 != 1) {
            throw new ConnectionException(
                    sm.getString("upgradeHandler.stream.even", key), Http2Error.PROTOCOL_ERROR);
        }

        if (streamId <= maxRemoteStreamId) {
            throw new ConnectionException(sm.getString("upgradeHandler.stream.old", key,
                    Integer.valueOf(maxRemoteStreamId)), Http2Error.PROTOCOL_ERROR);
        }

        // TODO Implement periodic pruning of closed streams

        Stream result = new Stream(key, this);
        streams.put(key, result);
        maxRemoteStreamId = streamId;
        return result;
    }


    private void close() {
        connectionState.set(ConnectionState.CLOSED);
        try {
            socketWrapper.close();
        } catch (IOException ioe) {
            log.debug(sm.getString("upgradeHandler.socketCloseFailed"), ioe);
        }
    }


    String getProperty(String key) {
        return socketWrapper.getEndpoint().getProperty(key);
    }


    @Override
    protected final String getConnectionId() {
        return connectionId;
    }


    @Override
    protected final int getWeight() {
        return 0;
    }


    // ------------------------------------------- Configuration getters/setters

    public long getReadTimeout() {
        return readTimeout;
    }


    public void setReadTimeout(long readTimeout) {
        this.readTimeout = readTimeout;
    }


    public long getKeepAliveTimeout() {
        return keepAliveTimeout;
    }


    public void setKeepAliveTimeout(long keepAliveTimeout) {
        this.keepAliveTimeout = keepAliveTimeout;
    }


    public long getWriteTimeout() {
        return writeTimeout;
    }


    public void setWriteTimeout(long writeTimeout) {
        this.writeTimeout = writeTimeout;
    }


    public void setMaxConcurrentStreams(long maxConcurrentStreams) {
        localSettings.set(Setting.MAX_CONCURRENT_STREAMS, maxConcurrentStreams);
    }


    public void setInitialWindowSize(int initialWindowSize) {
        localSettings.set(Setting.INITIAL_WINDOW_SIZE, initialWindowSize);
    }


    // ----------------------------------------------- Http2Parser.Input methods

    @Override
    public boolean fill(boolean block, byte[] data, int offset, int length) throws IOException {
        int len = length;
        int pos = offset;
        boolean nextReadBlock = block;
        int thisRead = 0;

        while (len > 0) {
            thisRead = socketWrapper.read(nextReadBlock, data, pos, len);
            if (thisRead == 0) {
                if (nextReadBlock) {
                    // Should never happen
                    throw new IllegalStateException();
                } else {
                    return false;
                }
            } else if (thisRead == -1) {
                throw new EOFException();
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
            hpackDecoder = new HpackDecoder(remoteSettings.getHeaderTableSize());
        }
        return hpackDecoder;
    }


    @Override
    public ByteBuffer getInputByteBuffer(int streamId, int payloadSize) throws Http2Exception {
        Stream stream = getStream(streamId, true);
        stream.checkState(FrameType.DATA);
        return stream.getInputByteBuffer();
    }


    @Override
    public void receiveEndOfStream(int streamId) throws ConnectionException {
        Stream stream = getStream(streamId, connectionState.get().isNewStreamAllowed());
        if (stream != null) {
            stream.receivedEndOfStream();
            if (!stream.isActive()) {
                activeRemoteStreamCount.decrementAndGet();
            }
        }
    }


    @Override
    public void swallowedPadding(int streamId, int paddingLength) throws
            ConnectionException, IOException {
        Stream stream = getStream(streamId, true);
        // +1 is for the payload byte used to define the padding length
        writeWindowUpdate(stream, paddingLength + 1);
    }


    @Override
    public HeaderEmitter headersStart(int streamId) throws Http2Exception {
        if (connectionState.get().isNewStreamAllowed()) {
            Stream stream = getStream(streamId, false);
            if (stream == null) {
                stream = createRemoteStream(streamId);
            }
            stream.checkState(FrameType.HEADERS);
            stream.receivedStartOfHeaders();
            closeIdleStreams(streamId);
            if (localSettings.getMaxConcurrentStreams() < activeRemoteStreamCount.incrementAndGet()) {
                activeRemoteStreamCount.decrementAndGet();
                throw new StreamException(sm.getString("upgradeHandler.tooManyRemoteStreams",
                        Long.toString(localSettings.getMaxConcurrentStreams())),
                        Http2Error.REFUSED_STREAM, streamId);
            }
            return stream;
        } else {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("upgradeHandler.noNewStreams",
                        connectionId, Integer.toString(streamId)));
            }
            // Stateless so a static can be used to save on GC
            return HEADER_SINK;
        }
    }


    private void closeIdleStreams(int newMaxActiveRemoteStreamId) throws Http2Exception {
        for (int i = maxActiveRemoteStreamId + 2; i < newMaxActiveRemoteStreamId; i += 2) {
            Stream stream = getStream(i, false);
            if (stream != null) {
                stream.closeIfIdle();
            }
        }
        maxActiveRemoteStreamId = newMaxActiveRemoteStreamId;
    }


    @Override
    public void reprioritise(int streamId, int parentStreamId,
            boolean exclusive, int weight) throws Http2Exception {
        Stream stream = getStream(streamId, false);
        if (stream == null) {
            stream = createRemoteStream(streamId);
        }
        stream.checkState(FrameType.PRIORITY);
        AbstractStream parentStream = getStream(parentStreamId, false);
        if (parentStream == null) {
            parentStream = this;
        }
        stream.rePrioritise(parentStream, exclusive, weight);
    }


    @Override
    public void headersEnd(int streamId) throws ConnectionException {
        setMaxProcessedStream(streamId);
        Stream stream = getStream(streamId, connectionState.get().isNewStreamAllowed());
        if (stream != null) {
            // Process this stream on a container thread
            StreamProcessor streamProcessor = new StreamProcessor(stream, adapter, socketWrapper);
            streamProcessor.setSslSupport(sslSupport);
            socketWrapper.getEndpoint().getExecutor().execute(streamProcessor);
        }
    }


    private void setMaxProcessedStream(int streamId) {
        if (maxProcessedStreamId < streamId) {
            maxProcessedStreamId = streamId;
        }
    }


    @Override
    public void reset(int streamId, long errorCode) throws Http2Exception  {
        Stream stream = getStream(streamId, true);
        stream.checkState(FrameType.RST);
        stream.reset(errorCode);
    }


    @Override
    public void setting(Setting setting, long value) throws ConnectionException {
        // Special handling required
        if (setting == Setting.INITIAL_WINDOW_SIZE) {
            long oldValue = remoteSettings.getInitialWindowSize();
            // Do this first in case new value is invalid
            remoteSettings.set(setting, value);
            int diff = (int) (value - oldValue);
            for (Stream stream : streams.values()) {
                try {
                    stream.incrementWindowSize(diff);
                } catch (Http2Exception e) {
                    // Should never happen since the diff should always be valid
                }
            }
        } else {
            remoteSettings.set(setting, value);
        }
    }


    @Override
    public void settingsEnd(boolean ack) throws IOException {
        if (ack) {
            localSettings.ack();
        } else {
            synchronized (socketWrapper) {
                socketWrapper.write(true, SETTINGS_ACK, 0, SETTINGS_ACK.length);
                socketWrapper.flush(true);
            }
        }
    }


    @Override
    public void pingReceive(byte[] payload, boolean ack) throws IOException {
        pingManager.receivePing(payload, ack);
    }


    @Override
    public void goaway(int lastStreamId, long errorCode, String debugData) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.goaway.debug", connectionId,
                    Integer.toString(lastStreamId), Long.toHexString(errorCode), debugData));
        }
    }


    @Override
    public void incrementWindowSize(int streamId, int increment) throws Http2Exception {
        if (streamId == 0) {
            incrementWindowSize(increment);
        } else {
            Stream stream = getStream(streamId, true);
            stream.checkState(FrameType.WINDOW_UPDATE);
            stream.incrementWindowSize(increment);
        }
    }


    @Override
    public void swallowed(int streamId, FrameType frameType, int flags, int size)
            throws IOException {
        // NO-OP.
    }


    private class PingManager {

        // 10 seconds
        private final long pingIntervalNano = 10000000000L;

        private int sequence = 0;
        private long lastPingNanoTime = Long.MIN_VALUE;

        private Queue<PingRecord> inflightPings = new ConcurrentLinkedQueue<>();
        private Queue<Long> roundTripTimes = new ConcurrentLinkedQueue<>();

        /**
         * Check to see if a ping was sent recently and, if not, send one.
         *
         * @param force Send a ping, even if one was sent recently
         *
         * @throws IOException If an I/O issue prevents the ping from being sent
         */
        public void sendPing(boolean force) throws IOException {
            long now = System.nanoTime();
            if (force || now - lastPingNanoTime > pingIntervalNano) {
                lastPingNanoTime = now;
                byte[] payload = new byte[8];
                synchronized (socketWrapper) {
                    int sentSequence = ++sequence;
                    PingRecord pingRecord = new PingRecord(sentSequence, now);
                    inflightPings.add(pingRecord);
                    ByteUtil.set31Bits(payload, 4, sentSequence);
                    socketWrapper.write(true, PING, 0, PING.length);
                    socketWrapper.write(true, payload, 0, payload.length);
                    socketWrapper.flush(true);
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
                        roundTripTimes.poll();
                    }
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("pingManager.roundTripTime",
                                connectionId, Long.valueOf(roundTripTime)));
                    }
                }

            } else {
                // Client originated ping. Echo it back.
                synchronized (socketWrapper) {
                    socketWrapper.write(true, PING_ACK, 0, PING_ACK.length);
                    socketWrapper.write(true, payload, 0, payload.length);
                    socketWrapper.flush(true);
                }
            }
        }

        public long getRoundTripTimeNano() {
            return (long) roundTripTimes.stream().mapToLong(x -> x.longValue()).average().orElse(0);
        }
    }


    private static class PingRecord {

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

        private ConnectionState(boolean newStreamsAllowed) {
            this.newStreamsAllowed = newStreamsAllowed;
        }

        public boolean isNewStreamAllowed() {
            return newStreamsAllowed;
        }
     }
}
