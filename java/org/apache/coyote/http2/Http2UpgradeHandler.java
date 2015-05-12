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

    private static final int FRAME_SETTINGS = 4;

    private volatile SocketWrapperBase<?> socketWrapper;
    private volatile boolean initialized = false;
    private volatile ConnectionPrefaceParser connectionPrefaceParser =
            new ConnectionPrefaceParser();
    private volatile boolean firstFrame = true;
    private volatile boolean open = true;

    private volatile int settingsMaxFrameSize = 16 * 1024;


    @Override
    public void init(WebConnection unused) {
        initialized = true;
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
                // TODO Catch the Http2Exception and reset the stream / close
                // the connection as appropriate
            } catch (IOException ioe) {
                log.error("TODO: i18n - Frame processing error", ioe);
                open = false;
            }

            if (open) {
                return SocketState.LONG;
            }
            break;

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
        int streamId = getStreamIdentifier(frameHeader);
        int payloadSize = getPayloadSize(streamId, frameHeader);

        switch (frameType) {
        case FRAME_SETTINGS:
            processFrameSettings(streamId, payloadSize);
            break;
        default:
            // Unknown frame type.
            processFrameUnknown(streamId, frameType, payloadSize);
        }
        return false;
    }


    private void processFrameSettings(int streamId, int payloadSize) throws IOException {
        if (streamId != 0) {
            // TODO i18n
            throw new Http2Exception("", 0, Http2Exception.FRAME_SIZE_ERROR);
        }

        if (payloadSize % 6 != 0) {
            // TODO i18n
            throw new Http2Exception("", 0, Http2Exception.FRAME_SIZE_ERROR);
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
        if (headerBytesRead < frameHeader.length) {
            int read = socketWrapper.read(true, frameHeader, headerBytesRead,
                    frameHeader.length - headerBytesRead);
            if (read == -1) {
                // TODO i18n
                throw new EOFException();
            }
        }

        return true;
    }


    private int getFrameType(byte[] frameHeader) throws IOException {
        int frameType = frameHeader[3] & 0xFF;
        // Make sure the first frame is a settings frame
        if (firstFrame) {
            if (frameType != FRAME_SETTINGS) {
                // TODO i18n
                throw new Http2Exception("", 0, Http2Exception.PROTOCOL_ERROR);
            } else {
                firstFrame = false;
            }
        }
        return frameType;
    }


    private int getStreamIdentifier(byte[] frameHeader) {
        // MSB of [5] is reserved and must be ignored.
        return ((frameHeader[5] & 0x7F) << 24) + ((frameHeader[6] & 0xFF) << 16) +
                ((frameHeader[7] & 0xFF) << 6) + (frameHeader[8] & 0xFF);
    }


    private int getPayloadSize(int streamId, byte[] frameHeader) throws IOException {
        // Make sure the payload size is valid
        int payloadSize = ((frameHeader[0] & 0xFF) << 16) +
                ((frameHeader[1] & 0xFF) << 8) +
                (frameHeader[2] & 0xFF);

        if (payloadSize > settingsMaxFrameSize) {
            swallowPayload(payloadSize);
            // TODO i18n
            throw new Http2Exception("", streamId, Http2Exception.FRAME_SIZE_ERROR);
        }

        return payloadSize;
    }


    @Override
    public void destroy() {
        // NO-OP
    }


    private void close() {
        try {
            socketWrapper.close();
        } catch (IOException ioe) {
            log.debug(sm.getString("upgradeHandler.socketCloseFailed"), ioe);
        }
    }
}
