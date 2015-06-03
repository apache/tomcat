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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.coyote.http2.HpackDecoder.HeaderEmitter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

class Http2Parser {

    private static final Log log = LogFactory.getLog(Http2Parser.class);
    private static final StringManager sm = StringManager.getManager(Http2Parser.class);

    static final byte[] CLIENT_PREFACE_START =
            "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);

    private static final int FRAME_TYPE_DATA = 0;
    private static final int FRAME_TYPE_HEADERS = 1;
    private static final int FRAME_TYPE_PRIORITY = 2;
    private static final int FRAME_TYPE_SETTINGS = 4;
    private static final int FRAME_TYPE_PING = 6;
    private static final int FRAME_TYPE_GOAWAY = 7;
    private static final int FRAME_TYPE_WINDOW_UPDATE = 8;

    private final String connectionId;
    private final Input input;
    private final Output output;
    private final byte[] frameHeaderBuffer = new byte[9];

    private volatile HpackDecoder hpackDecoder;
    private final ByteBuffer headerReadBuffer = ByteBuffer.allocate(1024);

    private volatile boolean readPreface = false;
    private volatile int maxPayloadSize = ConnectionSettings.DEFAULT_MAX_FRAME_SIZE;


    Http2Parser(String connectionId, Input input, Output output) {
        this.connectionId = connectionId;
        this.input = input;
        this.output = output;
    }


    /**
     * Read and process a single frame. Once the start of a frame is read, the
     * remainder will be read using blocking IO.
     *
     * @param block Should this method block until a frame is available is no
     *              frame is available immediately?
     *
     * @return <code>true</code> if a frame was read otherwise
     *         <code>false</code>
     *
     * @throws IOException If an IO error occurs while trying to read a frame
     */
    boolean readFrame(boolean block) throws IOException {
        return readFrame(block, null);
    }

    private boolean readFrame(boolean block, Integer expected) throws IOException {
        if (!input.fill(block, frameHeaderBuffer)) {
            return false;
        }

        int payloadSize = ByteUtil.getThreeBytes(frameHeaderBuffer, 0);
        int frameType = ByteUtil.getOneByte(frameHeaderBuffer, 3);
        int flags = ByteUtil.getOneByte(frameHeaderBuffer, 4);
        int streamId = ByteUtil.get31Bits(frameHeaderBuffer, 5);

        if (expected != null && frameType != expected.intValue()) {
            throw new Http2Exception(sm.getString("http2Parser.processFrame.unexpectedType",
                    expected, Integer.toString(frameType)),
                    streamId, ErrorCode.PROTOCOL_ERROR);
        }

        if (payloadSize > maxPayloadSize) {
            throw new Http2Exception(sm.getString("http2Parser.payloadTooBig",
                    Integer.toString(payloadSize), Integer.toString(maxPayloadSize)),
                    streamId, ErrorCode.FRAME_SIZE_ERROR);
        }

        switch (frameType) {
        case FRAME_TYPE_DATA:
            readDataFrame(streamId, flags, payloadSize);
            break;
        case FRAME_TYPE_HEADERS:
            readHeadersFrame(streamId, flags, payloadSize);
            break;
        case FRAME_TYPE_PRIORITY:
            readPriorityFrame(streamId, flags, payloadSize);
            break;
        case FRAME_TYPE_SETTINGS:
            readSettingsFrame(streamId, flags, payloadSize);
            break;
        case FRAME_TYPE_PING:
            readPingFrame(streamId, flags, payloadSize);
            break;
        case FRAME_TYPE_GOAWAY:
            readGoawayFrame(streamId, flags, payloadSize);
            break;
        case FRAME_TYPE_WINDOW_UPDATE:
            readWindowUpdateFrame(streamId, flags, payloadSize);
            break;
        // TODO: Missing types
        default:
            readUnknownFrame(streamId, frameType, flags, payloadSize);
        }

        return true;
    }


    private void readDataFrame(int streamId, int flags, int payloadSize) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("http2Parser.processFrame", connectionId,
                    Integer.toString(streamId), Integer.toString(flags),
                    Integer.toString(payloadSize)));
        }

        // Validate the stream
        if (streamId == 0) {
            throw new Http2Exception(sm.getString("http2Parser.processFrameData.invalidStream"),
                    0, ErrorCode.PROTOCOL_ERROR);
        }

        // Process the Stream
        int padLength = 0;

        boolean endOfStream = (flags & 0x01) > 0;
        boolean padding = (flags & 0x08) > 0;

        if (padding) {
            byte[] b = new byte[1];
            input.fill(true, b);
            padLength = b[0] & 0xFF;
        }

        // TODO Flow control
        ByteBuffer dest = output.getInputByteBuffer(streamId, payloadSize);
        if (dest == null) {
            swallow(payloadSize);
            if (endOfStream) {
                output.endOfStream(streamId);
            }
        } else {
            synchronized (dest) {
                input.fill(true, dest, payloadSize);
                if (endOfStream) {
                    output.endOfStream(streamId);
                }
                dest.notifyAll();
            }
        }
        swallow(padLength);
    }


    private void readHeadersFrame(int streamId, int flags, int payloadSize) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("http2Parser.processFrame", connectionId,
                    Integer.toString(streamId), Integer.toString(flags),
                    Integer.toString(payloadSize)));
        }

        // Validate the stream
        if (streamId == 0) {
            throw new Http2Exception(sm.getString("http2Parser.processFrameHeaders.invalidStream"),
                    0, ErrorCode.PROTOCOL_ERROR);
        }

        // TODO Handle end of headers flag
        // TODO Handle end of stream flag
        // TODO Handle continuation frames

        if (hpackDecoder == null) {
            hpackDecoder = output.getHpackDecoder();
        }
        hpackDecoder.setHeaderEmitter(output.headersStart(streamId));

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
            input.fill(true, optional);
            int optionalPos = 0;
            if (padding) {
                padLength = ByteUtil.getOneByte(optional, optionalPos++);
            }
            if (priority) {
                boolean exclusive = ByteUtil.isBit7Set(optional[optionalPos]);
                int parentStreamId = ByteUtil.get31Bits(optional, optionalPos);
                int weight = ByteUtil.getOneByte(optional, optionalPos + 4) + 1;
                output.reprioritise(streamId, parentStreamId, exclusive, weight);
            }

            payloadSize -= optionalLen;
        }

        while (payloadSize > 0) {
            int toRead = Math.min(headerReadBuffer.remaining(), payloadSize);
            // headerReadBuffer in write mode
            input.fill(true, headerReadBuffer, toRead);
            // switch to read mode
            headerReadBuffer.flip();
            try {
                hpackDecoder.decode(headerReadBuffer);
            } catch (HpackException hpe) {
                throw new Http2Exception(
                        sm.getString("http2Parser.processFrameHeaders.decodingFailed"),
                        0, ErrorCode.COMPRESSION_ERROR);
            }
            // switches to write mode
            headerReadBuffer.compact();
            payloadSize -= toRead;
        }
        // Should be empty at this point
        if (headerReadBuffer.position() > 0) {
            throw new Http2Exception(
                    sm.getString("http2Parser.processFrameHeaders.decodingDataLeft"),
                    0, ErrorCode.COMPRESSION_ERROR);
        }

        swallow(padLength);

        output.headersEnd(streamId);
    }


    private void readPriorityFrame(int streamId, int flags, int payloadSize) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("http2Parser.processFrame", connectionId,
                    Integer.toString(streamId), Integer.toString(flags),
                    Integer.toString(payloadSize)));
        }
        // Validate the frame
        if (streamId == 0) {
            throw new Http2Exception(sm.getString("http2Parser.processFramePriority.invalidStream"),
                    0, ErrorCode.PROTOCOL_ERROR);
        }
        if (payloadSize != 5) {
            throw new Http2Exception(sm.getString("http2Parser.processFramePriority.invalidPayloadSize",
                    Integer.toString(payloadSize)), streamId, ErrorCode.FRAME_SIZE_ERROR);
        }

        byte[] payload = new byte[5];
        input.fill(true, payload);

        boolean exclusive = ByteUtil.isBit7Set(payload[0]);
        int parentStreamId = ByteUtil.get31Bits(payload, 0);
        int weight = ByteUtil.getOneByte(payload, 4) + 1;

        output.reprioritise(streamId, parentStreamId, exclusive, weight);
    }


    private void readSettingsFrame(int streamId, int flags, int payloadSize) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("http2Parser.processFrame", connectionId,
                    Integer.toString(streamId), Integer.toString(flags),
                    Integer.toString(payloadSize)));
        }

        // Validate the frame
        if (streamId != 0) {
            throw new Http2Exception(sm.getString("http2Parser.processFrameSettings.invalidStream",
                    Integer.toString(streamId)), 0, ErrorCode.FRAME_SIZE_ERROR);
        }
        if (payloadSize % 6 != 0) {
            throw new Http2Exception(sm.getString("http2Parser.processFrameSettings.invalidPayloadSize",
                    Integer.toString(payloadSize)), 0, ErrorCode.FRAME_SIZE_ERROR);
        }
        boolean ack = (flags & 0x1) != 0;
        if (payloadSize > 0 && ack) {
            throw new Http2Exception(sm.getString("http2Parser.processFrameSettings.ackWithNonZeroPayload"),
                    0, ErrorCode.FRAME_SIZE_ERROR);
        }

        if (payloadSize != 0) {
            // Process the settings
            byte[] setting = new byte[6];
            for (int i = 0; i < payloadSize / 6; i++) {
                input.fill(true, setting);
                int id = ByteUtil.getTwoBytes(setting, 0);
                long value = ByteUtil.getFourBytes(setting, 2);
                output.setting(id, value);
            }
        }
        output.settingsEnd(ack);
    }


    private void readPingFrame(int streamId, int flags, int payloadSize)
            throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("http2Parser.processFrame", connectionId,
                    Integer.toString(streamId), Integer.toString(flags),
                    Integer.toString(payloadSize)));
        }
        // Validate the frame
        if (streamId != 0) {
            throw new Http2Exception(sm.getString("http2Parser.processFramePing.invalidStream",
                    Integer.toString(streamId)), 0, ErrorCode.FRAME_SIZE_ERROR);
        }
        if (payloadSize != 8) {
            throw new Http2Exception(sm.getString("http2Parser.processFramePing.invalidPayloadSize",
                    Integer.toString(payloadSize)), 0, ErrorCode.FRAME_SIZE_ERROR);
        }
        if ((flags & 0x1) == 0) {
            // Read the payload
            byte[] payload = new byte[8];
            input.fill(true, payload);
            output.pingReceive(payload);
        } else {
            output.pingAck();
        }
    }


    private void readGoawayFrame(int streamId, int flags, int payloadSize)
            throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("http2Parser.processFrame", connectionId,
                    Integer.toString(streamId), Integer.toString(flags),
                    Integer.toString(payloadSize)));
        }

        // Validate the frame
        if (streamId != 0) {
            throw new Http2Exception(sm.getString("http2Parser.processFrameGoaway.invalidStream",
                    Integer.toString(streamId)), 0, ErrorCode.PROTOCOL_ERROR);
        }

        if (payloadSize < 8) {
            throw new Http2Exception(sm.getString("http2Parser.processFrameGoaway.payloadTooSmall",
                    connectionId, Integer.toString(payloadSize)), 0, ErrorCode.FRAME_SIZE_ERROR);
        }

        byte[] payload = new byte[payloadSize];
        input.fill(true, payload);

        int lastStreamId = ByteUtil.get31Bits(payload, 0);
        long errorCode = ByteUtil.getFourBytes(payload, 4);
        String debugData = null;
        if (payloadSize > 8) {
            debugData = new String(payload, 8, payloadSize - 8, StandardCharsets.UTF_8);
        }
        output.goaway(lastStreamId, errorCode, debugData);
    }

    private void readWindowUpdateFrame(int streamId, int flags, int payloadSize)
            throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("http2Parser.processFrame", connectionId,
                    Integer.toString(streamId), Integer.toString(flags),
                    Integer.toString(payloadSize)));
        }
        // Validate the frame
        if (payloadSize != 4) {
            // Use stream 0 since this is always a connection error
            throw new Http2Exception(sm.getString("http2Parser.processFrameWindowUpdate.invalidPayloadSize",
                    Integer.toString(payloadSize)), 0, ErrorCode.FRAME_SIZE_ERROR);
        }

        byte[] payload = new byte[4];
        input.fill(true,  payload);
        int windowSizeIncrement = ByteUtil.get31Bits(payload, 0);

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("http2Parser.processFrameWindowUpdate.debug", connectionId,
                    Integer.toString(streamId), Integer.toString(windowSizeIncrement)));
        }

        // Validate the data
        if (windowSizeIncrement == 0) {
            throw new Http2Exception("http2Parser.processFrameWindowUpdate.invalidIncrement",
                    streamId, ErrorCode.PROTOCOL_ERROR);
        }

        output.incrementWindowSize(streamId, windowSizeIncrement);
    }


    private void readUnknownFrame(int streamId, int frameType, int flags, int payloadSize)
            throws IOException {
        output.swallow(streamId, frameType, flags, payloadSize);
        swallow(payloadSize);
    }


    private void swallow(int len) throws IOException {
        if (len == 0) {
            return;
        }
        int read = 0;
        byte[] buffer = new byte[1024];
        while (read < len) {
            int thisTime = Math.min(buffer.length, len - read);
            input.fill(true, buffer, 0, thisTime);
            read += thisTime;
        }
    }


    /**
     * Read and validate the connection preface from input using blocking IO.
     *
     * @return <code>true</code> if a valid preface was read, otherwise false.
     */
    boolean readConnectionPreface() throws IOException {
        if (readPreface) {
            return true;
        }

        byte[] data = new byte[CLIENT_PREFACE_START.length];
        try {
            input.fill(true, data);
        } catch (IOException ioe) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("http2Parser.preface.io"), ioe);
            }
            return false;
        }

        for (int i = 0; i < CLIENT_PREFACE_START.length; i++) {
            if (CLIENT_PREFACE_START[i] != data[i]) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("http2Parser.preface.invalid",
                            new String(data, StandardCharsets.ISO_8859_1)));
                }
                return false;
            }
        }

        // Must always be followed by a settings frame
        readFrame(true, Integer.valueOf(FRAME_TYPE_SETTINGS));

        readPreface = true;
        return true;
    }


    void setHpackDecoder(HpackDecoder hpackDecoder) {
        this.hpackDecoder = hpackDecoder;
    }


    /**
     * Interface that must be implemented by the source of data for the parser.
     */
    static interface Input {

        /**
         * Fill the given array with data unless non-blocking is requested and
         * no data is available. If any data is available then the buffer will
         * be filled using blocking I/O.
         *
         * @param block Should the first read into the provided buffer be a
         *              blocking read or not.
         * @param data  Buffer to fill
         * @param offset Position in buffer to start writing
         * @param length Number of bytes to read
         *
         * @return <code>true</code> if the buffer was filled otherwise
         *         <code>false</code>
         *
         * @throws IOException If an I/O occurred while obtaining data with
         *                     which to fill the buffer
         */
        boolean fill(boolean block, byte[] data, int offset, int length) throws IOException;

        default boolean fill(boolean block, byte[] data) throws IOException {
            return fill(block, data, 0, data.length);
        }

        default boolean fill(boolean block, ByteBuffer data, int len) throws IOException {
            boolean result = fill(block, data.array(), data.arrayOffset(), len);
            if (result) {
                data.position(data.position() + len);
            }
            return result;
        }
    }


    /**
     * Interface that must be implemented to receive notifications from the
     * parser as it processes incoming frames.
     */
    static interface Output {

        HpackDecoder getHpackDecoder();

        // Data frames
        ByteBuffer getInputByteBuffer(int streamId, int payloadSize);
        void endOfStream(int streamId);

        // Header frames
        HeaderEmitter headersStart(int streamId);
        void reprioritise(int streamId, int parentStreamId, boolean exclusive, int weight);
        void headersEnd(int streamId);

        // Settings frames
        void setting(int identifier, long value) throws IOException;
        void settingsEnd(boolean ack) throws IOException;

        // Ping frames
        void pingReceive(byte[] payload) throws IOException;
        void pingAck();

        // Goaway
        void goaway(int lastStreamId, long errorCode, String debugData);

        // Window size
        void incrementWindowSize(int streamId, int increment);

        // Testing
        void swallow(int streamId, int frameType, int flags, int size) throws IOException;
    }
}
