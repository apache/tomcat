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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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

    private static final int FRAME_TYPE_DATA = 0;
    private static final int FRAME_TYPE_HEADERS = 1;
    private static final int FRAME_TYPE_CONTINUATION = 9;

    private static final byte[] PING_ACK = { 0x00, 0x00, 0x08, 0x06, 0x01, 0x00, 0x00, 0x00, 0x00 };

    private static final byte[] SETTINGS_EMPTY = { 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00 };
    private static final byte[] SETTINGS_ACK = { 0x00, 0x00, 0x00, 0x04, 0x01, 0x00, 0x00, 0x00, 0x00 };

    private static final byte[] GOAWAY = { 0x07, 0x00, 0x00, 0x00, 0x00 };

    private static final String HTTP2_SETTINGS_HEADER = "HTTP2-Settings";
    private static final byte[] HTTP2_UPGRADE_ACK = ("HTTP/1.1 101 Switching Protocols\r\n" +
                "Connection: Upgrade\r\nUpgrade: h2c\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1);

    private final String connectionId;

    private final Adapter adapter;
    private volatile SocketWrapperBase<?> socketWrapper;
    private volatile SSLSupport sslSupport;

    private volatile Http2Parser parser;

    private volatile boolean initialized = false;

    private final ConnectionSettings remoteSettings = new ConnectionSettings();
    private final ConnectionSettings localSettings = new ConnectionSettings();
    private volatile int maxRemoteStreamId = 0;

    private HpackDecoder hpackDecoder;
    private HpackEncoder hpackEncoder;

    // All timeouts in milliseconds
    private long readTimeout = 10000;
    private long keepAliveTimeout = 30000;
    private long writeTimeout = 10000;

    private final Map<Integer,Stream> streams = new HashMap<>();

    // Tracking for when the connection is blocked (windowSize < 1)
    private final Object backLogLock = new Object();
    private final Map<AbstractStream,int[]> backLogStreams = new ConcurrentHashMap<>();
    private long backLogSize = 0;


    public Http2UpgradeHandler(Adapter adapter, Request coyoteRequest) {
        super (STREAM_ID_ZERO);
        this.adapter = adapter;
        this.connectionId = Integer.toString(connectionIdGenerator.getAndIncrement());

        // Initial HTTP request becomes stream 0.
        if (coyoteRequest != null) {
            Integer key = Integer.valueOf(1);
            Stream stream = new Stream(key, this, coyoteRequest);
            streams.put(key, stream);
            maxRemoteStreamId = 1;
        }
    }


    @Override
    public void init(WebConnection webConnection) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.init", connectionId));
        }

        parser = new Http2Parser(connectionId, this, this);

        initialized = true;
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
                stream = getStream(1);
                String base64Settings = stream.getCoyoteRequest().getHeader(HTTP2_SETTINGS_HEADER);
                byte[] settings = Base64.decodeBase64(base64Settings);

                if (settings.length % 6 != 0) {
                    // Invalid payload length for settings
                    // TODO i18n
                    throw new ProtocolException();
                }

                for (int i = 0; i < settings.length % 6; i++) {
                    int id = ByteUtil.getTwoBytes(settings, i * 6);
                    long value = ByteUtil.getFourBytes(settings, (i * 6) + 2);
                    remoteSettings.set(id, value);
                }
            } catch (IOException ioe) {
                // TODO i18n
                throw new ProtocolException();
            }
        }

        // Send the initial settings frame
        try {
            socketWrapper.write(true, SETTINGS_EMPTY, 0, SETTINGS_EMPTY.length);
            socketWrapper.flush(true);
        } catch (IOException ioe) {
            throw new IllegalStateException(sm.getString("upgradeHandler.sendPrefaceFail"), ioe);
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

        if (!initialized) {
            // WebConnection is not used so passing null here is fine
            init(null);
        }

        SocketState result = SocketState.CLOSED;

        switch(status) {
        case OPEN_READ:
            try {
                if (!parser.readConnectionPreface()) {
                    close();
                    result = SocketState.CLOSED;
                    break;
                }

                while (parser.readFrame(false)) {
                }
            } catch (Http2Exception h2e) {
                if (h2e.getStreamId() == 0) {
                    // Connection error
                    log.warn(sm.getString("upgradeHandler.connectionError"), h2e);
                    close(h2e);
                    break;
                } else {
                    // Stream error
                    // TODO Reset stream
                }
            } catch (IOException ioe) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("upgradeHandler.ioerror", connectionId), ioe);
                }
                close();
                result = SocketState.CLOSED;
                break;
            }

            result = SocketState.UPGRADED;
            break;

        case OPEN_WRITE:
            try {
                processWrites();
            } catch (Http2Exception h2e) {
                if (h2e.getStreamId() == 0) {
                    // Connection error
                    log.warn(sm.getString("upgradeHandler.connectionError"), h2e);
                    close(h2e);
                    break;
                } else {
                    // Stream error
                    // TODO Reset stream
                }
            } catch (IOException ioe) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("upgradeHandler.ioerror", connectionId), ioe);
                }
                close();
                result = SocketState.CLOSED;
                break;
            }

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
            result = SocketState.CLOSED;
            break;
        }

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.upgradeDispatch.exit", connectionId, result));
        }
        return result;
    }


    private void swallow(int len) throws IOException {
        if (len == 0) {
            return;
        }
        int read = 0;
        byte[] buffer = new byte[1024];
        while (read < len) {
            int toRead = Math.min(buffer.length, len - read);
            int thisTime = socketWrapper.read(true, buffer, 0, toRead);
            if (thisTime == -1) {
                throw new IOException("TODO: i18n");
            }
            read += thisTime;
        }
    }


    ConnectionSettings getRemoteSettings() {
        return remoteSettings;
    }


    @Override
    public void destroy() {
        // NO-OP
    }


    private void close(Http2Exception h2e) {
        // Write a GOAWAY frame.
        byte[] payload = h2e.getMessage().getBytes(StandardCharsets.UTF_8);
        byte[] payloadLength = new byte[3];
        ByteUtil.setThreeBytes(payloadLength, 0, payload.length);

        try {
            synchronized (socketWrapper) {
                socketWrapper.write(true, payloadLength, 0, payloadLength.length);
                socketWrapper.write(true, GOAWAY, 0, GOAWAY.length);
                socketWrapper.write(true, payload, 0,  payload.length);
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
                    header[3] = FRAME_TYPE_HEADERS;
                    if (stream.getOutputBuffer().hasNoBody()) {
                        header[4] = FLAG_END_OF_STREAM;
                    }
                } else {
                    header[3] = FRAME_TYPE_CONTINUATION;
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


    void writeBody(Stream stream, ByteBuffer data, int len) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.writeBody", connectionId, stream.getIdentifier(),
                    Integer.toString(data.remaining())));
        }
        synchronized (socketWrapper) {
            byte[] header = new byte[9];
            ByteUtil.setThreeBytes(header, 0, len);
            header[3] = FRAME_TYPE_DATA;
            if (stream.getOutputBuffer().isFinished()) {
                header[4] = FLAG_END_OF_STREAM;
            }
            ByteUtil.set31Bits(header, 5, stream.getIdentifier().intValue());
            socketWrapper.write(true, header, 0, header.length);
            socketWrapper.write(true, data.array(), data.arrayOffset() + data.position(),
                    len);
            socketWrapper.flush(true);
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


    int reserveWindowSize(Stream stream, int toWrite) {
        int result;
        synchronized (backLogLock) {
            long windowSize = getWindowSize();
            if (windowSize < 1 || backLogSize > 0) {
                // Has this stream been granted an allocation
                int[] value = backLogStreams.remove(stream);
                if (value[1] > 0) {
                    result = value[1];
                    value[0] = 0;
                    value[1] = 1;
                } else {
                    value = new int[] { toWrite, 0 };
                    backLogStreams.put(stream, value);
                    backLogSize += toWrite;
                    // Add the parents as well
                    AbstractStream parent = stream.getParentStream();
                    while (parent != null && backLogStreams.putIfAbsent(parent, new int[2]) == null) {
                        parent = parent.getParentStream();
                    }
                    result = 0;
                }
            } else if (windowSize < toWrite) {
                result = (int) windowSize;
            } else {
                result = toWrite;
            }
            incrementWindowSize(-result);
        }
        return result;
    }



    @Override
    protected void incrementWindowSize(int increment) {
        synchronized (backLogLock) {
            if (getWindowSize() == 0) {
                releaseBackLog(increment);
            }
            super.incrementWindowSize(increment);
        }
    }


    private void releaseBackLog(int increment) {
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
            allocate(this, increment);
            for (Entry<AbstractStream,int[]> entry : backLogStreams.entrySet()) {
                int allocation = entry.getValue()[1];
                if (allocation > 0) {
                    backLogSize =- allocation;
                    synchronized (entry.getKey()) {
                        entry.getKey().notifyAll();
                    }
                }
            }
        }
    }


    private int allocate(AbstractStream stream, int allocation) {
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
                totalWeight += recipient.getWeight();
            }

            // Use an Iterator so fully allocated children/recipients can be
            // removed.
            Iterator<AbstractStream> iter = recipients.iterator();
            while (iter.hasNext()) {
                AbstractStream recipient = iter.next();
                // +1 is to avoid rounding issues triggering an infinite loop.
                // Will cause a very slight over allocation but HTTP/2 should
                // cope with that.
                int share = 1 + leftToAllocate * recipient.getWeight() / totalWeight;
                int remainder = allocate(recipient, share);
                // Remove recipients that receive their full allocation so that
                // they are excluded from the next allocation round.
                if (remainder > 0) {
                    iter.remove();
                }
                leftToAllocate -= (share - remainder);
            }
        }

        return 0;
    }


    private Stream getStream(int streamId) {
        Integer key = Integer.valueOf(streamId);

        if (streamId > maxRemoteStreamId) {
            // Must be a new stream
            maxRemoteStreamId = streamId;
            Stream stream = new Stream(key, this);
            streams.put(key, stream);
            return stream;
        } else {
            return streams.get(key);
        }
    }


    private void close() {
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
    protected final Log getLog() {
        return log;
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


    // ---------------------------------------------- Http2Parser.Output methods

    @Override
    public HpackDecoder getHpackDecoder() {
        if (hpackDecoder == null) {
            hpackDecoder = new HpackDecoder(remoteSettings.getHeaderTableSize());
        }
        return hpackDecoder;
    }


    @Override
    public ByteBuffer getInputByteBuffer(int streamId, int payloadSize) {
        Stream stream = getStream(streamId);
        if (stream == null) {
            return null;
        }
        return stream.getInputByteBuffer();
    }


    @Override
    public void endOfStream(int streamId) {
        Stream stream = getStream(streamId);
        if (stream != null) {
            stream.setEndOfStream();
        }
    }


    @Override
    public HeaderEmitter headersStart(int streamId) {
        return getStream(streamId);
    }


    @Override
    public void reprioritise(int streamId, int parentStreamId,
            boolean exclusive, int weight) {
        Stream stream = getStream(streamId);
        AbstractStream parentStream = getStream(parentStreamId);
        if (parentStream == null) {
            parentStream = this;
        }
        stream.rePrioritise(parentStream, exclusive, weight);
    }


    @Override
    public void headersEnd(int streamId) {
        // Process this stream on a container thread
        StreamProcessor streamProcessor = new StreamProcessor(
                getStream(streamId), adapter, socketWrapper);
        streamProcessor.setSslSupport(sslSupport);
        socketWrapper.getEndpoint().getExecutor().execute(streamProcessor);
    }


    @Override
    public void setting(int identifier, long value) throws IOException {
        remoteSettings.set(identifier, value);
    }


    @Override
    public void settingsEnd(boolean ack) throws IOException {
        if (ack) {
            // TODO Process ACK
        } else {
            synchronized (socketWrapper) {
                socketWrapper.write(true, SETTINGS_ACK, 0, SETTINGS_ACK.length);
                socketWrapper.flush(true);
            }
        }
    }


    @Override
    public void pingReceive(byte[] payload) throws IOException {
        // Echo it back
        synchronized (socketWrapper) {
            socketWrapper.write(true, PING_ACK, 0, PING_ACK.length);
            socketWrapper.write(true, payload, 0, payload.length);
            socketWrapper.flush(true);
        }
    }


    @Override
    public void pingAck() {
        // TODO Auto-generated method stub
    }


    @Override
    public void incrementWindowSize(int streamId, int increment) {
        AbstractStream stream = getStream(streamId);
        if (stream != null) {
            stream.incrementWindowSize(increment);
        }
    }


    @Override
    public void swallow(int streamId, int frameType, int flags, int size) throws IOException {
        swallow(size);
    }
}
