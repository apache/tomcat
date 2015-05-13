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
import java.util.Map;

import javax.servlet.http.WebConnection;

import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
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
 * Note that unless Tomcat is configured with an ECC certificate, FireFox
 * (tested with v37.0.2) needs to be configured with
 * network.http.spdy.enforce-tls-profile=false in order for FireFox to be able
 * to connect.
 */
public class Http2UpgradeHandler extends AbstractStream implements InternalHttpUpgradeHandler {

    private static final Log log = LogFactory.getLog(Http2UpgradeHandler.class);
    private static final StringManager sm = StringManager.getManager(Http2UpgradeHandler.class);

    private static final Integer STREAM_ID_ZERO = Integer.valueOf(0);

    private static final int FRAME_TYPE_HEADERS = 1;
    private static final int FRAME_TYPE_PRIORITY = 2;
    private static final int FRAME_TYPE_SETTINGS = 4;
    private static final int FRAME_TYPE_WINDOW_UPDATE = 8;

    private static final byte[] SETTINGS_EMPTY = { 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00 };
    private static final byte[] SETTINGS_ACK = { 0x00, 0x00, 0x00, 0x04, 0x01, 0x00, 0x00, 0x00, 0x00 };
    private static final byte[] GOAWAY = { 0x07, 0x00, 0x00, 0x00, 0x00 };

    private volatile SocketWrapperBase<?> socketWrapper;
    private volatile boolean initialized = false;
    private volatile ConnectionPrefaceParser connectionPrefaceParser =
            new ConnectionPrefaceParser();
    private volatile boolean firstFrame = true;

    private final ConnectionSettings remoteSettings = new ConnectionSettings();
    private final ConnectionSettings localSettings = new ConnectionSettings();
    private volatile long flowControlWindowSize = ConnectionSettings.DEFAULT_WINDOW_SIZE;
    private volatile int maxRemoteStreamId = 0;

    private HpackDecoder hpackDecoder;
    private ByteBuffer headerReadBuffer = ByteBuffer.allocate(1024);
    private HpackEncoder hpackEncoder;

    private final Map<Integer,Stream> streams = new HashMap<>();

    public Http2UpgradeHandler() {
        super (STREAM_ID_ZERO);
    }


    @Override
    public void init(WebConnection unused) {
        initialized = true;

        // Send the initial settings frame
        try {
            socketWrapper.write(true, SETTINGS_EMPTY, 0, SETTINGS_EMPTY.length);
            socketWrapper.flush(true);
        } catch (IOException ioe) {
            throw new IllegalStateException(sm.getString("upgradeHandler.sendPrefaceFail"), ioe);
        }
    }


    @Override
    public void setSocketWrapper(SocketWrapperBase<?> wrapper) {
        this.socketWrapper = wrapper;
    }


    @Override
    public SocketState upgradeDispatch(SocketStatus status) {
        if (!initialized) {
            // WebConnection is not used so passing null here is fine
            init(null);
        }

        switch(status) {
        case OPEN_READ:
            // Gets set to null once the connection preface has been
            // successfully parsed.
            if (connectionPrefaceParser != null) {
                if (!connectionPrefaceParser.parse(socketWrapper)) {
                    if (connectionPrefaceParser.isError()) {
                        // Any errors will have already been logged.
                        close();
                        return SocketState.CLOSED;
                    } else {
                        // Incomplete
                        return SocketState.LONG;
                    }
                }
            }
            connectionPrefaceParser = null;

            try {
                while (processFrame()) {
                }
            } catch (Http2Exception h2e) {
                if (h2e.getStreamId() == 0) {
                    // Connection error
                    log.warn(sm.getString("upgradeHandler.connectionError"), h2e);
                    close(h2e);
                    return SocketState.CLOSED;
                } else {
                    // Stream error
                    // TODO Reset stream
                }
            } catch (IOException ioe) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("upgradeHandler.processFrame.ioerror"), ioe);
                }
                close();
                return SocketState.CLOSED;
            }

            return SocketState.LONG;

        case OPEN_WRITE:
            // TODO
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
            return SocketState.CLOSED;
        }

        // TODO This is for debug purposes to make sure ALPN is working.
        log.fatal("TODO: Handle SocketStatus: " + status);
        close();
        return SocketState.CLOSED;
    }


    private boolean processFrame() throws IOException {
        // TODO: Consider refactoring and making this a field to reduce GC.
        byte[] frameHeader = new byte[9];
        if (!getFrameHeader(frameHeader)) {
            return false;
        }

        int frameType = getFrameType(frameHeader);
        int flags = ByteUtil.getOneByte(frameHeader, 4);
        int streamId = ByteUtil.get31Bits(frameHeader, 5);
        int payloadSize = getPayloadSize(streamId, frameHeader);

        switch (frameType) {
        case FRAME_TYPE_HEADERS:
            processFrameHeaders(flags, streamId, payloadSize);
            break;
        case FRAME_TYPE_PRIORITY:
            processFramePriority(flags, streamId, payloadSize);
            break;
        case FRAME_TYPE_SETTINGS:
            processFrameSettings(flags, streamId, payloadSize);
            break;
        case FRAME_TYPE_WINDOW_UPDATE:
            processFrameWindowUpdate(flags, streamId, payloadSize);
            break;
        default:
            // Unknown frame type.
            processFrameUnknown(streamId, frameType, payloadSize);
        }
        return true;
    }


    private void processFrameHeaders(int flags, int streamId, int payloadSize) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.processFrame",
                    Integer.toString(FRAME_TYPE_HEADERS), Integer.toString(flags),
                    Integer.toString(streamId), Integer.toString(payloadSize)));
        }

        // Validate the stream
        if (streamId == 0) {
            throw new Http2Exception(sm.getString("upgradeHandler.processFrameHeaders.invalidStream"),
                    0, Http2Exception.PROTOCOL_ERROR);
        }

        // Process the stream
        // TODO Handle end of headers flag
        // TODO Handle end of stream flag
        // TODO Handle continutation frames
        Stream stream = getStream(streamId);
        int padLength = 0;

        boolean padding = (flags & 0x08) > 0;
        boolean priority = (flags & 0x20) > 0;
        int optionalLen = 0;
        if (padding) {
            optionalLen = 1;
        }
        if (priority) {
            optionalLen += 5;
        }
        if (optionalLen > 0) {
            byte[] optional = new byte[optionalLen];
            readFully(optional);
            int optionalPos = 0;
            if (padding) {
                padLength = ByteUtil.getOneByte(optional, optionalPos++);
            }
            if (priority) {
                boolean exclusive = ByteUtil.isBit7Set(optional[optionalPos]);
                int parentStreamId = ByteUtil.get31Bits(optional, optionalPos);
                int weight = ByteUtil.getOneByte(optional, optionalPos + 4) + 1;
                AbstractStream parentStream = getStream(parentStreamId);
                if (parentStream == null) {
                    parentStream = this;
                }
                stream.rePrioritise(parentStream, exclusive, weight);
            }

            payloadSize -= optionalLen;
        }

        hpackDecoder.setHeaderEmitter(stream);
        while (payloadSize > 0) {
            int toRead = Math.min(headerReadBuffer.remaining(), payloadSize);
            // headerReadBuffer in write mode
            readFully(headerReadBuffer, toRead);
            // switch to read mode
            headerReadBuffer.flip();
            try {
                hpackDecoder.decode(headerReadBuffer);
            } catch (HpackException hpe) {
                // TODO i18n
                throw new Http2Exception("", 0, Http2Exception.PROTOCOL_ERROR);
            }
            // switches to write mode
            headerReadBuffer.compact();
            payloadSize -= toRead;
        }
        // Should be empty at this point
        if (headerReadBuffer.position() > 0) {
            // TODO i18n
            throw new Http2Exception("", 0, Http2Exception.PROTOCOL_ERROR);
        }

        if (padLength > 0) {
            swallowPayload(padLength);
        }
    }


    private void processFramePriority(int flags, int streamId, int payloadSize) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.processFrame",
                    Integer.toString(FRAME_TYPE_PRIORITY), Integer.toString(flags),
                    Integer.toString(streamId), Integer.toString(payloadSize)));
        }
        // Validate the frame
        if (streamId == 0) {
            throw new Http2Exception(sm.getString("upgradeHandler.processFramePriority.invalidStream"),
                    0, Http2Exception.PROTOCOL_ERROR);
        }
        if (payloadSize != 5) {
            throw new Http2Exception(sm.getString("upgradeHandler.processFramePriority.invalidPayloadSize",
                    Integer.toString(payloadSize)), streamId, Http2Exception.FRAME_SIZE_ERROR);
        }

        byte[] payload = new byte[5];
        readFully(payload);

        boolean exclusive = ByteUtil.isBit7Set(payload[0]);
        int parentStreamId = ByteUtil.get31Bits(payload, 0);
        int weight = ByteUtil.getOneByte(payload, 4) + 1;

        Stream stream = getStream(streamId);
        AbstractStream parentStream;
        if (parentStreamId == 0) {
            parentStream = this;
        } else {
            parentStream = getStream(parentStreamId);
            if (parentStream == null) {
                parentStream = this;
                weight = Constants.DEFAULT_WEIGHT;
                exclusive = false;
            }
        }

        if (stream == null) {
            // Old stream. Already closed and dropped from the stream map.
        } else {
            stream.rePrioritise(parentStream, exclusive, weight);
        }
    }


    private void processFrameSettings(int flags, int streamId, int payloadSize) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.processFrame",
                    Integer.toString(FRAME_TYPE_SETTINGS), Integer.toString(flags),
                    Integer.toString(streamId), Integer.toString(payloadSize)));
        }
        // Validate the frame
        if (streamId != 0) {
            throw new Http2Exception(sm.getString("upgradeHandler.processFrameSettings.invalidStream",
                    Integer.toString(streamId)), 0, Http2Exception.FRAME_SIZE_ERROR);
        }
        if (payloadSize % 6 != 0) {
            throw new Http2Exception(sm.getString("upgradeHandler.processFrameSettings.invalidPayloadSize",
                    Integer.toString(payloadSize)), 0, Http2Exception.FRAME_SIZE_ERROR);
        }
        if (payloadSize > 0 && (flags & 0x1) != 0) {
            throw new Http2Exception(sm.getString("upgradeHandler.processFrameSettings.ackWithNonZeroPayload"),
                    0, Http2Exception.FRAME_SIZE_ERROR);
        }

        if (payloadSize == 0) {
            // Either an ACK or an empty settings frame
            if ((flags & 0x1) != 0) {
                // TODO process ACK
            }
        } else {
            // Process the settings
            byte[] setting = new byte[6];
            for (int i = 0; i < payloadSize / 6; i++) {
                readFully(setting);
                int id = ByteUtil.getTwoBytes(setting, 0);
                int value = ByteUtil.getFourBytes(setting, 2);
                remoteSettings.set(id, value);
            }
        }
        if (firstFrame) {
            firstFrame = false;
            hpackDecoder = new HpackDecoder(remoteSettings.getHeaderTableSize());
            hpackEncoder = new HpackEncoder(localSettings.getHeaderTableSize());
        }

        // Acknowledge the settings
        // TODO Need to coordinate writes with other threads
        socketWrapper.write(true, SETTINGS_ACK, 0, SETTINGS_ACK.length);
        socketWrapper.flush(true);
    }


    private void processFrameWindowUpdate(int flags, int streamId, int payloadSize)
            throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.processFrame",
                    Integer.toString(FRAME_TYPE_WINDOW_UPDATE), Integer.toString(flags),
                    Integer.toString(streamId), Integer.toString(payloadSize)));
        }
        // Validate the frame
        if (payloadSize != 4) {
            // Use stream 0 since this is always a connection error
            throw new Http2Exception(sm.getString("upgradeHandler.processFrameWindowUpdate.invalidPayloadSize",
                    Integer.toString(payloadSize)), 0, Http2Exception.FRAME_SIZE_ERROR);
        }

        byte[] payload = new byte[4];
        readFully(payload);
        int windowSizeIncrement = ByteUtil.get31Bits(payload, 0);

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.processFrameWindowUpdate.debug",
                    Integer.toString(streamId), Integer.toString(windowSizeIncrement)));
        }

        // Validate the data
        if (windowSizeIncrement == 0) {
            throw new Http2Exception("upgradeHandler.processFrameWindowUpdate.invalidIncrement",
                    streamId, Http2Exception.PROTOCOL_ERROR);
        }
        if (streamId == 0) {
            flowControlWindowSize += windowSizeIncrement;
        } else {
            Stream stream = getStream(streamId);
            if (stream == null) {
                // Old stream already closed.
                // Ignore
            } else {
                stream.incrementWindowSize(windowSizeIncrement);
            }
        }
    }


    private void processFrameUnknown(int streamId, int type, int payloadSize) throws IOException {
        // Swallow the payload
        log.info("Swallowing [" + payloadSize + "] bytes of unknown frame type [" + type +
                "] from stream [" + streamId + "]");
        swallowPayload(payloadSize);
    }


    private void swallowPayload(int payloadSize) throws IOException {
        int read = 0;
        byte[] buffer = new byte[1024];
        while (read < payloadSize) {
            int toRead = Math.min(buffer.length, payloadSize - read);
            int thisTime = socketWrapper.read(true, buffer, 0, toRead);
            if (thisTime == -1) {
                throw new IOException("TODO: i18n");
            }
            read += thisTime;
        }
    }


    private boolean getFrameHeader(byte[] frameHeader) throws IOException {
        // All frames start with a fixed size header.
        int headerBytesRead = socketWrapper.read(false, frameHeader, 0, frameHeader.length);

        // No frame header read. Non-blocking between frames, so return.
        if (headerBytesRead == 0) {
            return false;
        }

        // Partial header read. Blocking within a frame to block while the
        // remainder is read.
        while (headerBytesRead < frameHeader.length) {
            int read = socketWrapper.read(true, frameHeader, headerBytesRead,
                    frameHeader.length - headerBytesRead);
            if (read == -1) {
                throw new EOFException(sm.getString("upgradeHandler.unexpectedEos"));
            }
        }

        return true;
    }


    private int getFrameType(byte[] frameHeader) throws IOException {
        int frameType = ByteUtil.getOneByte(frameHeader, 3);
        // Make sure the first frame is a settings frame
        if (firstFrame) {
            if (frameType != FRAME_TYPE_SETTINGS) {
                throw new Http2Exception(sm.getString("upgradeHandler.receivePrefaceNotSettings"),
                        0, Http2Exception.PROTOCOL_ERROR);
            }
        }
        return frameType;
    }


    private int getPayloadSize(int streamId, byte[] frameHeader) throws IOException {
        // Make sure the payload size is valid
        int payloadSize = ByteUtil.getThreeBytes(frameHeader, 0);

        if (payloadSize > remoteSettings.getMaxFrameSize()) {
            swallowPayload(payloadSize);
            throw new Http2Exception(sm.getString("upgradeHandler.payloadTooBig",
                    Integer.toString(payloadSize), Long.toString(remoteSettings.getMaxFrameSize())),
                    streamId, Http2Exception.FRAME_SIZE_ERROR);
        }

        return payloadSize;
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
        byte[] payloadLength = getPayloadLength(payload);

        try {
            socketWrapper.write(true, payloadLength, 0, payloadLength.length);
            socketWrapper.write(true, GOAWAY, 0, GOAWAY.length);
            socketWrapper.write(true, payload, 0,  payload.length);
            socketWrapper.flush(true);
        } catch (IOException ioe) {
            // Ignore. GOAWAY is sent on a best efforts basis and the original
            // error has already been logged.
        }
        close();
    }


    private byte[] getPayloadLength(byte[] payload) {
        byte[] result = new byte[3];
        int len = payload.length;
        result[2] = (byte) (len & 0xFF);
        len = len >>> 8;
        result[1] = (byte) (len & 0xFF);
        len = len >>> 8;
        result[0] = (byte) (len & 0xFF);

        return result;
    }


    private void readFully(byte[] dest) throws IOException {
        int read = 0;
        while (read < dest.length) {
            int thisTime = socketWrapper.read(true, dest, read, dest.length - read);
            if (thisTime == -1) {
                throw new EOFException(sm.getString("upgradeHandler.unexpectedEos"));
            }
            read += thisTime;
        }
    }


    private void readFully(ByteBuffer dest, int len) throws IOException {
        int read = 0;
        while (read < len) {
            int thisTime = socketWrapper.read(true, dest.array(), dest.arrayOffset(), len -read);
            if (thisTime == -1) {
                throw new EOFException(sm.getString("upgradeHandler.unexpectedEos"));
            }
            read += thisTime;
        }
        dest.position(dest.position() + read);
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


    @Override
    protected final Log getLog() {
        return log;
    }
}
