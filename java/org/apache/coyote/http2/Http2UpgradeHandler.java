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
public class Http2UpgradeHandler implements InternalHttpUpgradeHandler {

    private static final Log log = LogFactory.getLog(Http2UpgradeHandler.class);
    private static final StringManager sm = StringManager.getManager(Http2UpgradeHandler.class);

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
    private volatile long flowControlWindowSize = ConnectionSettings.DEFAULT_WINDOW_SIZE;

    private final Map<Integer,Stream> streams = new HashMap<>();

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
        int flags = frameHeader[4] & 0xFF;
        int streamId = getStreamIdentifier(frameHeader);
        int payloadSize = getPayloadSize(streamId, frameHeader);

        switch (frameType) {
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
                int id = ((setting[0] & 0xFF) << 8) + (setting[1] & 0xFF);
                long value = ((setting[2] & 0xFF) << 24) + ((setting[3] & 0xFF) << 16) +
                        ((setting[4] & 0xFF) << 8) + (setting[5] & 0xFF);
                remoteSettings.set(id, value);
            }
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
        int windowSizeIncrement = ((payload[0] & 0x7F) << 24) + ((payload[1] & 0xFF) << 16) +
                ((payload[2] & 0xFF) << 8) + (payload[3] & 0xFF);

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
            Integer key = Integer.valueOf(streamId);
            Stream stream = streams.get(key);
            if (stream == null) {
                stream = new Stream(key, remoteSettings.getInitialWindowSize());
            }
            stream.incrementWindowSize(windowSizeIncrement);
        }
    }


    private void processFrameUnknown(int streamId, int type, int payloadSize) throws IOException {
        // Swallow the payload
        log.info("Swallowing [" + payloadSize + "] bytes of unknown frame type + [" + type +
                "] from stream [" + streamId + "]");
        swallowPayload(payloadSize);
    }


    private void swallowPayload(int payloadSize) throws IOException {
        int read = 0;
        byte[] buffer = new byte[8 * 1024];
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
        int frameType = frameHeader[3] & 0xFF;
        // Make sure the first frame is a settings frame
        if (firstFrame) {
            if (frameType != FRAME_TYPE_SETTINGS) {
                throw new Http2Exception(sm.getString("upgradeHandler.receivePrefaceNotSettings"),
                        0, Http2Exception.PROTOCOL_ERROR);
            } else {
                firstFrame = false;
            }
        }
        return frameType;
    }


    private int getStreamIdentifier(byte[] frameHeader) {
        // MSB of [5] is reserved and must be ignored.
        return ((frameHeader[5] & 0x7F) << 24) + ((frameHeader[6] & 0xFF) << 16) +
                ((frameHeader[7] & 0xFF) << 8) + (frameHeader[8] & 0xFF);
    }


    private int getPayloadSize(int streamId, byte[] frameHeader) throws IOException {
        // Make sure the payload size is valid
        int payloadSize = ((frameHeader[0] & 0xFF) << 16) +
                ((frameHeader[1] & 0xFF) << 8) +
                (frameHeader[2] & 0xFF);

        if (payloadSize > remoteSettings.getMaxFrameSize()) {
            swallowPayload(payloadSize);
            throw new Http2Exception(sm.getString("upgradeHandler.payloadTooBig",
                    Integer.toString(payloadSize), Long.toString(remoteSettings.getMaxFrameSize())),
                    streamId, Http2Exception.FRAME_SIZE_ERROR);
        }

        return payloadSize;
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


    private void close() {
        try {
            socketWrapper.close();
        } catch (IOException ioe) {
            log.debug(sm.getString("upgradeHandler.socketCloseFailed"), ioe);
        }
    }
}
