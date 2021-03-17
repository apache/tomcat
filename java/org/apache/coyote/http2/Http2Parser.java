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

import javax.servlet.http.WebConnection;

import org.apache.coyote.ProtocolException;
import org.apache.coyote.http2.HpackDecoder.HeaderEmitter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteBufferUtils;
import org.apache.tomcat.util.res.StringManager;

class Http2Parser {

    protected static final Log log = LogFactory.getLog(Http2Parser.class);
    protected static final StringManager sm = StringManager.getManager(Http2Parser.class);

    static final byte[] CLIENT_PREFACE_START =
            "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);

    protected final String connectionId;
    protected final Input input;
    private final Output output;
    private final byte[] frameHeaderBuffer = new byte[9];

    private volatile HpackDecoder hpackDecoder;
    private volatile ByteBuffer headerReadBuffer =
            ByteBuffer.allocate(Constants.DEFAULT_HEADER_READ_BUFFER_SIZE);
    private volatile int headersCurrentStream = -1;
    private volatile boolean headersEndStream = false;

    Http2Parser(String connectionId, Input input, Output output) {
        this.connectionId = connectionId;
        this.input = input;
        this.output = output;
    }


    /**
     * Read and process a single frame. Once the start of a frame is read, the
     * remainder will be read using blocking IO.
     *
     * @param block Should this method block until a frame is available if no
     *              frame is available immediately?
     *
     * @return <code>true</code> if a frame was read otherwise
     *         <code>false</code>
     *
     * @throws IOException If an IO error occurs while trying to read a frame
     */
    boolean readFrame(boolean block) throws Http2Exception, IOException {
        return readFrame(block, null);
    }


    protected boolean readFrame(boolean block, FrameType expected)
            throws IOException, Http2Exception {

        if (!input.fill(block, frameHeaderBuffer)) {
            return false;
        }

        int payloadSize = ByteUtil.getThreeBytes(frameHeaderBuffer, 0);
        int frameTypeId = ByteUtil.getOneByte(frameHeaderBuffer, 3);
        FrameType frameType = FrameType.valueOf(frameTypeId);
        int flags = ByteUtil.getOneByte(frameHeaderBuffer, 4);
        int streamId = ByteUtil.get31Bits(frameHeaderBuffer, 5);

        try {
            validateFrame(expected, frameType, streamId, flags, payloadSize);
        } catch (StreamException se) {
            swallowPayload(streamId, frameTypeId, payloadSize, false, null);
            throw se;
        }

        switch (frameType) {
        case DATA:
            readDataFrame(streamId, flags, payloadSize, null);
            break;
        case HEADERS:
            readHeadersFrame(streamId, flags, payloadSize, null);
            break;
        case PRIORITY:
            readPriorityFrame(streamId, null);
            break;
        case RST:
            readRstFrame(streamId, null);
            break;
        case SETTINGS:
            readSettingsFrame(flags, payloadSize, null);
            break;
        case PUSH_PROMISE:
            readPushPromiseFrame(streamId, null);
            break;
        case PING:
            readPingFrame(flags, null);
            break;
        case GOAWAY:
            readGoawayFrame(payloadSize, null);
            break;
        case WINDOW_UPDATE:
            readWindowUpdateFrame(streamId, null);
            break;
        case CONTINUATION:
            readContinuationFrame(streamId, flags, payloadSize, null);
            break;
        case UNKNOWN:
            readUnknownFrame(streamId, frameTypeId, flags, payloadSize, null);
        }

        return true;
    }

    protected void readDataFrame(int streamId, int flags, int payloadSize, ByteBuffer buffer)
            throws Http2Exception, IOException {
        // Process the Stream
        int padLength = 0;

        boolean endOfStream = Flags.isEndOfStream(flags);

        int dataLength;
        if (Flags.hasPadding(flags)) {
            if (buffer == null) {
                byte[] b = new byte[1];
                input.fill(true, b);
                padLength = b[0] & 0xFF;
            } else {
                padLength = buffer.get() & 0xFF;
            }

            if (padLength >= payloadSize) {
                throw new ConnectionException(
                        sm.getString("http2Parser.processFrame.tooMuchPadding", connectionId,
                                Integer.toString(streamId), Integer.toString(padLength),
                                Integer.toString(payloadSize)), Http2Error.PROTOCOL_ERROR);
            }
            // +1 is for the padding length byte we just read above
            dataLength = payloadSize - (padLength + 1);
        } else {
            dataLength = payloadSize;
        }

        if (log.isDebugEnabled()) {
            String padding;
            if (Flags.hasPadding(flags)) {
                padding = Integer.toString(padLength);
            } else {
                padding = "none";
            }
            log.debug(sm.getString("http2Parser.processFrameData.lengths", connectionId,
                    Integer.toString(streamId), Integer.toString(dataLength), padding));
        }

        ByteBuffer dest = output.startRequestBodyFrame(streamId, payloadSize, endOfStream);
        if (dest == null) {
            swallowPayload(streamId, FrameType.DATA.getId(), dataLength, false, buffer);
            // Process padding before sending any notifications in case padding
            // is invalid.
            if (Flags.hasPadding(flags)) {
                swallowPayload(streamId, FrameType.DATA.getId(), padLength, true, buffer);
            }
            if (endOfStream) {
                output.receivedEndOfStream(streamId);
            }
        } else {
            synchronized (dest) {
                if (dest.remaining() < payloadSize) {
                    // Client has sent more data than permitted by Window size
                    swallowPayload(streamId, FrameType.DATA.getId(), dataLength, false, buffer);
                    if (Flags.hasPadding(flags)) {
                        swallowPayload(streamId, FrameType.DATA.getId(), padLength, true, buffer);
                    }
                    throw new StreamException(sm.getString("http2Parser.processFrameData.window", connectionId),
                            Http2Error.FLOW_CONTROL_ERROR, streamId);
                }
                if (buffer == null) {
                    input.fill(true, dest, dataLength);
                } else {
                    int oldLimit = buffer.limit();
                    buffer.limit(buffer.position() + dataLength);
                    dest.put(buffer);
                    buffer.limit(oldLimit);
                }
                // Process padding before sending any notifications in case
                // padding is invalid.
                if (Flags.hasPadding(flags)) {
                    swallowPayload(streamId, FrameType.DATA.getId(), padLength, true, buffer);
                }
                if (endOfStream) {
                    output.receivedEndOfStream(streamId);
                }
                output.endRequestBodyFrame(streamId, dataLength);
            }
        }
    }


    protected void readHeadersFrame(int streamId, int flags, int payloadSize, ByteBuffer buffer)
            throws Http2Exception, IOException {

        headersEndStream = Flags.isEndOfStream(flags);

        if (hpackDecoder == null) {
            hpackDecoder = output.getHpackDecoder();
        }
        try {
            hpackDecoder.setHeaderEmitter(output.headersStart(streamId, headersEndStream));
        } catch (StreamException se) {
            swallowPayload(streamId, FrameType.HEADERS.getId(), payloadSize, false, buffer);
            throw se;
        }

        int padLength = 0;
        boolean padding = Flags.hasPadding(flags);
        boolean priority = Flags.hasPriority(flags);
        int optionalLen = 0;
        if (padding) {
            optionalLen = 1;
        }
        if (priority) {
            optionalLen += 5;
        }
        if (optionalLen > 0) {
            byte[] optional = new byte[optionalLen];
            if (buffer == null) {
                input.fill(true, optional);
            } else {
                buffer.get(optional);
            }
            int optionalPos = 0;
            if (padding) {
                padLength = ByteUtil.getOneByte(optional, optionalPos++);
                if (padLength >= payloadSize) {
                    throw new ConnectionException(
                            sm.getString("http2Parser.processFrame.tooMuchPadding", connectionId,
                                    Integer.toString(streamId), Integer.toString(padLength),
                                    Integer.toString(payloadSize)), Http2Error.PROTOCOL_ERROR);
                }
            }
            if (priority) {
                boolean exclusive = ByteUtil.isBit7Set(optional[optionalPos]);
                int parentStreamId = ByteUtil.get31Bits(optional, optionalPos);
                int weight = ByteUtil.getOneByte(optional, optionalPos + 4) + 1;
                output.reprioritise(streamId, parentStreamId, exclusive, weight);
            }

            payloadSize -= optionalLen;
            payloadSize -= padLength;
        }

        readHeaderPayload(streamId, payloadSize, buffer);

        swallowPayload(streamId, FrameType.HEADERS.getId(), padLength, true, buffer);

        if (Flags.isEndOfHeaders(flags)) {
            onHeadersComplete(streamId);
        } else {
            headersCurrentStream = streamId;
        }
    }


    protected void readPriorityFrame(int streamId, ByteBuffer buffer) throws Http2Exception, IOException {
        byte[] payload = new byte[5];
        if (buffer == null) {
            input.fill(true, payload);
        } else {
            buffer.get(payload);
        }

        boolean exclusive = ByteUtil.isBit7Set(payload[0]);
        int parentStreamId = ByteUtil.get31Bits(payload, 0);
        int weight = ByteUtil.getOneByte(payload, 4) + 1;

        if (streamId == parentStreamId) {
            throw new StreamException(sm.getString("http2Parser.processFramePriority.invalidParent",
                    connectionId, Integer.valueOf(streamId)), Http2Error.PROTOCOL_ERROR, streamId);
        }

        output.reprioritise(streamId, parentStreamId, exclusive, weight);
    }


    protected void readRstFrame(int streamId, ByteBuffer buffer) throws Http2Exception, IOException {
        byte[] payload = new byte[4];
        if (buffer == null) {
            input.fill(true, payload);
        } else {
            buffer.get(payload);
        }

        long errorCode = ByteUtil.getFourBytes(payload, 0);
        output.reset(streamId, errorCode);
        headersCurrentStream = -1;
        headersEndStream = false;
    }


    protected void readSettingsFrame(int flags, int payloadSize, ByteBuffer buffer) throws Http2Exception, IOException {
        boolean ack = Flags.isAck(flags);
        if (payloadSize > 0 && ack) {
            throw new ConnectionException(sm.getString(
                    "http2Parser.processFrameSettings.ackWithNonZeroPayload"),
                    Http2Error.FRAME_SIZE_ERROR);
        }

        if (payloadSize == 0 && !ack) {
            // Ensure empty SETTINGS frame increments the overhead count
            output.setting(null, 0);
        } else {
            // Process the settings
            byte[] setting = new byte[6];
            for (int i = 0; i < payloadSize / 6; i++) {
                if (buffer == null) {
                    input.fill(true, setting);
                } else {
                    buffer.get(setting);
                }
                int id = ByteUtil.getTwoBytes(setting, 0);
                long value = ByteUtil.getFourBytes(setting, 2);
                output.setting(Setting.valueOf(id), value);
            }
        }
        output.settingsEnd(ack);
    }


    /**
     * This default server side implementation always throws an exception. If
     * re-used for client side parsing, this method should be overridden with an
     * appropriate implementation.
     *
     * @param streamId The pushed stream
     * @param buffer   The payload, if available
     *
     * @throws Http2Exception
     */
    protected void readPushPromiseFrame(int streamId, ByteBuffer buffer) throws Http2Exception {
        throw new ConnectionException(sm.getString("http2Parser.processFramePushPromise",
                connectionId, Integer.valueOf(streamId)), Http2Error.PROTOCOL_ERROR);
    }


    protected void readPingFrame(int flags, ByteBuffer buffer) throws IOException {
        // Read the payload
        byte[] payload = new byte[8];
        if (buffer == null) {
            input.fill(true, payload);
        } else {
            buffer.get(payload);
        }
        output.pingReceive(payload, Flags.isAck(flags));
    }


    protected void readGoawayFrame(int payloadSize, ByteBuffer buffer) throws IOException {
        byte[] payload = new byte[payloadSize];
        if (buffer == null) {
            input.fill(true, payload);
        } else {
            buffer.get(payload);
        }

        int lastStreamId = ByteUtil.get31Bits(payload, 0);
        long errorCode = ByteUtil.getFourBytes(payload, 4);
        String debugData = null;
        if (payloadSize > 8) {
            debugData = new String(payload, 8, payloadSize - 8, StandardCharsets.UTF_8);
        }
        output.goaway(lastStreamId, errorCode, debugData);
    }


    protected void readWindowUpdateFrame(int streamId, ByteBuffer buffer) throws Http2Exception, IOException {
        byte[] payload = new byte[4];
        if (buffer == null) {
            input.fill(true, payload);
        } else {
            buffer.get(payload);
        }
        int windowSizeIncrement = ByteUtil.get31Bits(payload, 0);

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("http2Parser.processFrameWindowUpdate.debug", connectionId,
                    Integer.toString(streamId), Integer.toString(windowSizeIncrement)));
        }

        // Validate the data
        if (windowSizeIncrement == 0) {
            if (streamId == 0) {
                throw new ConnectionException(
                        sm.getString("http2Parser.processFrameWindowUpdate.invalidIncrement"),
                        Http2Error.PROTOCOL_ERROR);
            } else {
                throw new StreamException(
                        sm.getString("http2Parser.processFrameWindowUpdate.invalidIncrement"),
                        Http2Error.PROTOCOL_ERROR, streamId);
            }
        }

        output.incrementWindowSize(streamId, windowSizeIncrement);
    }


    protected void readContinuationFrame(int streamId, int flags, int payloadSize, ByteBuffer buffer)
            throws Http2Exception, IOException {
        if (headersCurrentStream == -1) {
            // No headers to continue
            throw new ConnectionException(sm.getString(
                    "http2Parser.processFrameContinuation.notExpected", connectionId,
                    Integer.toString(streamId)), Http2Error.PROTOCOL_ERROR);
        }

        boolean endOfHeaders = Flags.isEndOfHeaders(flags);

        // Used to detect abusive clients sending large numbers of small
        // continuation frames
        output.headersContinue(payloadSize, endOfHeaders);

        readHeaderPayload(streamId, payloadSize, buffer);

        if (endOfHeaders) {
            headersCurrentStream = -1;
            onHeadersComplete(streamId);
        }
    }


    protected void readHeaderPayload(int streamId, int payloadSize, ByteBuffer buffer)
            throws Http2Exception, IOException {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("http2Parser.processFrameHeaders.payload", connectionId,
                    Integer.valueOf(streamId), Integer.valueOf(payloadSize)));
        }

        int remaining = payloadSize;

        while (remaining > 0) {
            if (headerReadBuffer.remaining() == 0) {
                // Buffer needs expansion
                int newSize;
                if (headerReadBuffer.capacity() < payloadSize) {
                    // First step, expand to the current payload. That should
                    // cover most cases.
                    newSize = payloadSize;
                } else {
                    // Header must be spread over multiple frames. Keep doubling
                    // buffer size until the header can be read.
                    newSize = headerReadBuffer.capacity() * 2;
                }
                headerReadBuffer = ByteBufferUtils.expand(headerReadBuffer, newSize);
            }
            int toRead = Math.min(headerReadBuffer.remaining(), remaining);
            // headerReadBuffer in write mode
            if (buffer == null) {
                input.fill(true, headerReadBuffer, toRead);
            } else {
                int oldLimit = buffer.limit();
                buffer.limit(buffer.position() + toRead);
                headerReadBuffer.put(buffer);
                buffer.limit(oldLimit);
            }
            // switch to read mode
            headerReadBuffer.flip();
            try {
                hpackDecoder.decode(headerReadBuffer);
            } catch (HpackException hpe) {
                throw new ConnectionException(
                        sm.getString("http2Parser.processFrameHeaders.decodingFailed"),
                        Http2Error.COMPRESSION_ERROR, hpe);
            }

            // switches to write mode
            headerReadBuffer.compact();
            remaining -= toRead;

            if (hpackDecoder.isHeaderCountExceeded()) {
                StreamException headerException = new StreamException(sm.getString(
                        "http2Parser.headerLimitCount", connectionId, Integer.valueOf(streamId)),
                        Http2Error.ENHANCE_YOUR_CALM, streamId);
                hpackDecoder.getHeaderEmitter().setHeaderException(headerException);
            }

            if (hpackDecoder.isHeaderSizeExceeded(headerReadBuffer.position())) {
                StreamException headerException = new StreamException(sm.getString(
                        "http2Parser.headerLimitSize", connectionId, Integer.valueOf(streamId)),
                        Http2Error.ENHANCE_YOUR_CALM, streamId);
                hpackDecoder.getHeaderEmitter().setHeaderException(headerException);
            }

            if (hpackDecoder.isHeaderSwallowSizeExceeded(headerReadBuffer.position())) {
                throw new ConnectionException(sm.getString("http2Parser.headerLimitSize",
                        connectionId, Integer.valueOf(streamId)), Http2Error.ENHANCE_YOUR_CALM);
            }
        }
    }


    protected void readUnknownFrame(int streamId, int frameTypeId, int flags, int payloadSize, ByteBuffer buffer)
            throws IOException {
        try {
            swallowPayload(streamId, frameTypeId, payloadSize, false, buffer);
        } catch (ConnectionException e) {
            // Will never happen because swallowPayload() is called with isPadding set
            // to false
        } finally {
            output.onSwallowedUnknownFrame(streamId, frameTypeId, flags, payloadSize);
        }
    }


    /**
     * Swallow some or all of the bytes from the payload of an HTTP/2 frame.
     *
     * @param streamId      Stream being swallowed
     * @param frameTypeId   Type of HTTP/2 frame for which the bytes will be
     *                      swallowed
     * @param len           Number of bytes to swallow
     * @param isPadding     Are the bytes to be swallowed padding bytes?
     * @param byteBuffer    Used with {@link Http2AsyncParser} to access the
     *                      data that has already been read
     *
     * @throws IOException If an I/O error occurs reading additional bytes into
     *                     the input buffer.
     * @throws ConnectionException If the swallowed bytes are expected to have a
     *                             value of zero but do not
     */
    protected void swallowPayload(int streamId, int frameTypeId, int len, boolean isPadding, ByteBuffer byteBuffer)
            throws IOException, ConnectionException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("http2Parser.swallow.debug", connectionId,
                    Integer.toString(streamId), Integer.toString(len)));
        }
        try {
            if (len == 0) {
                return;
            }
            if (!isPadding && byteBuffer != null) {
                byteBuffer.position(byteBuffer.position() + len);
            } else {
                int read = 0;
                byte[] buffer = new byte[1024];
                while (read < len) {
                    int thisTime = Math.min(buffer.length, len - read);
                    if (byteBuffer == null) {
                        input.fill(true, buffer, 0, thisTime);
                    } else {
                        byteBuffer.get(buffer, 0, thisTime);
                    }
                    if (isPadding) {
                        // Validate the padding is zero since receiving non-zero padding
                        // is a strong indication of either a faulty client or a server
                        // side bug.
                        for (int i = 0; i < thisTime; i++) {
                            if (buffer[i] != 0) {
                                throw new ConnectionException(sm.getString("http2Parser.nonZeroPadding",
                                        connectionId, Integer.toString(streamId)), Http2Error.PROTOCOL_ERROR);
                            }
                        }
                    }
                    read += thisTime;
                }
            }
        } finally {
            if (FrameType.DATA.getIdByte() == frameTypeId) {
                if (isPadding) {
                    // Need to add 1 for the padding length bytes that was also
                    // part of the payload.
                    len += 1;
                }
                if (len > 0) {
                    output.onSwallowedDataFramePayload(streamId, len);
                }
            }
        }
    }


    protected void onHeadersComplete(int streamId) throws Http2Exception {
        // Any left over data is a compression error
        if (headerReadBuffer.position() > 0) {
            throw new ConnectionException(
                    sm.getString("http2Parser.processFrameHeaders.decodingDataLeft"),
                    Http2Error.COMPRESSION_ERROR);
        }

        // Delay validation (and triggering any exception) until this point
        // since all the headers still have to be read if a StreamException is
        // going to be thrown.
        hpackDecoder.getHeaderEmitter().validateHeaders();

        synchronized (output) {
            output.headersEnd(streamId);

            if (headersEndStream) {
                output.receivedEndOfStream(streamId);
                headersEndStream = false;
            }
        }

        // Reset size for new request if the buffer was previously expanded
        if (headerReadBuffer.capacity() > Constants.DEFAULT_HEADER_READ_BUFFER_SIZE) {
            headerReadBuffer = ByteBuffer.allocate(Constants.DEFAULT_HEADER_READ_BUFFER_SIZE);
        }
    }


    /*
     * Implementation note:
     * Validation applicable to all incoming frames should be implemented here.
     * Frame type specific validation should be performed in the appropriate
     * readXxxFrame() method.
     * For validation applicable to some but not all frame types, use your
     * judgement.
     */
    protected void validateFrame(FrameType expected, FrameType frameType, int streamId, int flags,
            int payloadSize) throws Http2Exception {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("http2Parser.processFrame", connectionId,
                    Integer.toString(streamId), frameType, Integer.toString(flags),
                    Integer.toString(payloadSize)));
        }

        if (expected != null && frameType != expected) {
            throw new StreamException(sm.getString("http2Parser.processFrame.unexpectedType",
                    expected, frameType), Http2Error.PROTOCOL_ERROR, streamId);
        }

        int maxFrameSize = input.getMaxFrameSize();
        if (payloadSize > maxFrameSize) {
            throw new ConnectionException(sm.getString("http2Parser.payloadTooBig",
                    Integer.toString(payloadSize), Integer.toString(maxFrameSize)),
                    Http2Error.FRAME_SIZE_ERROR);
        }

        if (headersCurrentStream != -1) {
            if (headersCurrentStream != streamId) {
                throw new ConnectionException(sm.getString("http2Parser.headers.wrongStream",
                        connectionId, Integer.toString(headersCurrentStream),
                        Integer.toString(streamId)), Http2Error.COMPRESSION_ERROR);
            }
            if (frameType == FrameType.RST) {
                // NO-OP: RST is OK here
            } else if (frameType != FrameType.CONTINUATION) {
                throw new ConnectionException(sm.getString("http2Parser.headers.wrongFrameType",
                        connectionId, Integer.toString(headersCurrentStream),
                        frameType), Http2Error.COMPRESSION_ERROR);
            }
        }

        frameType.check(streamId, payloadSize);
    }


    /**
     * Read and validate the connection preface from input using blocking IO.
     * @param webConnection The connection
     * @param stream The current stream
     */
    void readConnectionPreface(WebConnection webConnection, Stream stream) throws Http2Exception {
        byte[] data = new byte[CLIENT_PREFACE_START.length];
        try {
            input.fill(true, data);

            for (int i = 0; i < CLIENT_PREFACE_START.length; i++) {
                if (CLIENT_PREFACE_START[i] != data[i]) {
                    throw new ProtocolException(sm.getString("http2Parser.preface.invalid"));
                }
            }

            // Must always be followed by a settings frame
            readFrame(true, FrameType.SETTINGS);
        } catch (IOException ioe) {
            throw new ProtocolException(sm.getString("http2Parser.preface.io"), ioe);
        }
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
            boolean result = fill(block, data.array(), data.arrayOffset() + data.position(), len);
            if (result) {
                data.position(data.position() + len);
            }
            return result;
        }

        int getMaxFrameSize();
    }


    /**
     * Interface that must be implemented to receive notifications from the
     * parser as it processes incoming frames.
     */
    static interface Output {

        HpackDecoder getHpackDecoder();

        // Data frames
        ByteBuffer startRequestBodyFrame(int streamId, int payloadSize, boolean endOfStream) throws Http2Exception;
        void endRequestBodyFrame(int streamId, int dataLength) throws Http2Exception, IOException;
        void receivedEndOfStream(int streamId) throws ConnectionException;
        /**
         * Notification triggered when the parser swallows some or all of a DATA
         * frame payload without writing it to the ByteBuffer returned by
         * {@link #startRequestBodyFrame(int, int, boolean)}.
         *
         * @param streamId  The stream on which the payload that has been
         *                  swallowed was received
         * @param swallowedDataBytesCount   The number of bytes that the parser
         *                                  swallowed.
         *
         * @throws ConnectionException If an error fatal to the HTTP/2
 *                 connection occurs while swallowing the payload
         * @throws IOException If an I/O occurred while swallowing the payload
         */
        void onSwallowedDataFramePayload(int streamId, int swallowedDataBytesCount) throws ConnectionException, IOException;

        // Header frames
        HeaderEmitter headersStart(int streamId, boolean headersEndStream)
                throws Http2Exception, IOException;
        void headersContinue(int payloadSize, boolean endOfHeaders);
        void headersEnd(int streamId) throws Http2Exception;

        // Priority frames (also headers)
        void reprioritise(int streamId, int parentStreamId, boolean exclusive, int weight)
                throws Http2Exception;

        // Reset frames
        void reset(int streamId, long errorCode) throws Http2Exception;

        // Settings frames
        void setting(Setting setting, long value) throws ConnectionException;
        void settingsEnd(boolean ack) throws IOException;

        // Ping frames
        void pingReceive(byte[] payload, boolean ack) throws IOException;

        // Goaway
        void goaway(int lastStreamId, long errorCode, String debugData);

        // Window size
        void incrementWindowSize(int streamId, int increment) throws Http2Exception;

        /**
         * Notification triggered when the parser swallows the payload of an
         * unknown frame.
         *
         * @param streamId      The stream on which the swallowed frame was
         *                      received
         * @param frameTypeId   The (unrecognised) type of swallowed frame
         * @param flags         The flags set in the header of the swallowed
         *                      frame
         * @param size          The payload size of the swallowed frame
         *
         * @throws IOException If an I/O occurred while swallowing the unknown
         *         frame
         */
        void onSwallowedUnknownFrame(int streamId, int frameTypeId, int flags, int size) throws IOException;
    }
}
