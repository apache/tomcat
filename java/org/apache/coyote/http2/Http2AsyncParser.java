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
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.apache.tomcat.util.buf.ByteBufferUtils;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.net.SocketWrapperBase.BlockingMode;
import org.apache.tomcat.util.net.SocketWrapperBase.CompletionCheck;
import org.apache.tomcat.util.net.SocketWrapperBase.CompletionHandlerCall;
import org.apache.tomcat.util.net.SocketWrapperBase.CompletionState;

class Http2AsyncParser extends Http2Parser {

    protected final SocketWrapperBase<?> socketWrapper;
    protected final Http2AsyncUpgradeHandler upgradeHandler;
    private Throwable error = null;

    Http2AsyncParser(String connectionId, Input input, Output output, SocketWrapperBase<?> socketWrapper, Http2AsyncUpgradeHandler upgradeHandler) {
        super(connectionId, input, output);
        this.socketWrapper = socketWrapper;
        socketWrapper.getSocketBufferHandler().expand(input.getMaxFrameSize());
        this.upgradeHandler = upgradeHandler;
    }


    protected boolean readFrame(boolean block, FrameType expected)
            throws IOException, Http2Exception {
        if (block) {
            return super.readFrame(block, expected);
        }
        handleAsyncException();
        // TODO: examine if it could be possible to reuse byte buffers or loop over frame readings (= less unRead abuse)
        ByteBuffer header = ByteBuffer.allocate(9);
        ByteBuffer framePaylod = ByteBuffer.allocate(input.getMaxFrameSize());
        FrameCompletionHandler handler = new FrameCompletionHandler(expected, header, framePaylod);
        FrameCompletionCheck check = new FrameCompletionCheck(handler);
        CompletionState state =
                socketWrapper.read(BlockingMode.NON_BLOCK, socketWrapper.getWriteTimeout(), TimeUnit.MILLISECONDS, null, check, handler, header, framePaylod);
        if (state == CompletionState.ERROR || state == CompletionState.INLINE) {
            handleAsyncException();
            return true;
        } else {
            return false;
        }
    }

    private void handleAsyncException()
            throws IOException, Http2Exception {
        if (error != null) {
            Throwable error = this.error;
            this.error = null;
            if (error instanceof Http2Exception) {
                throw (Http2Exception) error;
            } else if (error instanceof IOException) {
                throw (IOException) error;
            } else {
                throw new RuntimeException(error);
            }
        }
    }

    // TODO: see how to refactor to avoid duplication
    private void readDataFrame(int streamId, int flags, int payloadSize, ByteBuffer buffer)
            throws Http2Exception, IOException {
        // Process the Stream
        int padLength = 0;

        boolean endOfStream = Flags.isEndOfStream(flags);

        int dataLength;
        if (Flags.hasPadding(flags)) {
            padLength = buffer.get() & 0xFF;

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

        ByteBuffer dest = output.startRequestBodyFrame(streamId, payloadSize);
        if (dest == null) {
            swallow(streamId, dataLength, false, buffer);
            // Process padding before sending any notifications in case padding
            // is invalid.
            if (padLength > 0) {
                swallow(streamId, padLength, true, buffer);
            }
            if (endOfStream) {
                output.receivedEndOfStream(streamId);
            }
        } else {
            synchronized (dest) {
                if (dest.remaining() < dataLength) {
                    swallow(streamId, dataLength, false, buffer);
                    // Client has sent more data than permitted by Window size
                    throw new StreamException("Client sent more data than stream window allowed", Http2Error.FLOW_CONTROL_ERROR, streamId);
                }
                int oldLimit = buffer.limit();
                buffer.limit(buffer.position() + dataLength);
                dest.put(buffer);
                buffer.limit(oldLimit);
                // Process padding before sending any notifications in case
                // padding is invalid.
                if (padLength > 0) {
                    swallow(streamId, padLength, true, buffer);
                }
                if (endOfStream) {
                    output.receivedEndOfStream(streamId);
                }
                output.endRequestBodyFrame(streamId);
            }
        }
        if (buffer.hasRemaining()) {
            socketWrapper.unRead(buffer);
        }
        if (padLength > 0) {
            output.swallowedPadding(streamId, padLength);
        }
    }


    private void readHeadersFrame(int streamId, int flags, int payloadSize, ByteBuffer buffer)
            throws Http2Exception, IOException {

        headersEndStream = Flags.isEndOfStream(flags);

        if (hpackDecoder == null) {
            hpackDecoder = output.getHpackDecoder();
        }

        try {
            hpackDecoder.setHeaderEmitter(output.headersStart(streamId, headersEndStream));
        } catch (StreamException se) {
            swallow(streamId, payloadSize, false, buffer);
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
            buffer.get(optional);
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

        swallow(streamId, padLength, true, buffer);

        if (buffer.hasRemaining()) {
            socketWrapper.unRead(buffer);
        }

        if (Flags.isEndOfHeaders(flags)) {
            onHeadersComplete(streamId);
        } else {
            headersCurrentStream = streamId;
        }
    }


    private void readPriorityFrame(int streamId, ByteBuffer buffer) throws Http2Exception, IOException {
        byte[] payload = new byte[5];
        buffer.get(payload);
        if (buffer.hasRemaining()) {
            socketWrapper.unRead(buffer);
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


    private void readRstFrame(int streamId, ByteBuffer buffer) throws Http2Exception, IOException {
        byte[] payload = new byte[4];
        buffer.get(payload);
        if (buffer.hasRemaining()) {
            socketWrapper.unRead(buffer);
        }

        long errorCode = ByteUtil.getFourBytes(payload, 0);
        output.reset(streamId, errorCode);
        headersCurrentStream = -1;
        headersEndStream = false;
    }


    private void readSettingsFrame(int flags, int payloadSize, ByteBuffer buffer) throws Http2Exception, IOException {
        boolean ack = Flags.isAck(flags);
        if (payloadSize > 0 && ack) {
            throw new ConnectionException(sm.getString(
                    "http2Parser.processFrameSettings.ackWithNonZeroPayload"),
                    Http2Error.FRAME_SIZE_ERROR);
        }

        if (payloadSize != 0) {
            // Process the settings
            byte[] setting = new byte[6];
            for (int i = 0; i < payloadSize / 6; i++) {
                buffer.get(setting);
                int id = ByteUtil.getTwoBytes(setting, 0);
                long value = ByteUtil.getFourBytes(setting, 2);
                output.setting(Setting.valueOf(id), value);
            }
        }
        if (buffer.hasRemaining()) {
            socketWrapper.unRead(buffer);
        }
        output.settingsEnd(ack);
    }


    private void readPingFrame(int flags, ByteBuffer buffer) throws IOException {
        // Read the payload
        byte[] payload = new byte[8];
        buffer.get(payload);
        if (buffer.hasRemaining()) {
            socketWrapper.unRead(buffer);
        }
        output.pingReceive(payload, Flags.isAck(flags));
    }


    private void readGoawayFrame(int payloadSize, ByteBuffer buffer) throws IOException {
        byte[] payload = new byte[payloadSize];
        buffer.get(payload);
        if (buffer.hasRemaining()) {
            socketWrapper.unRead(buffer);
        }
        int lastStreamId = ByteUtil.get31Bits(payload, 0);
        long errorCode = ByteUtil.getFourBytes(payload, 4);
        String debugData = null;
        if (payloadSize > 8) {
            debugData = new String(payload, 8, payloadSize - 8, StandardCharsets.UTF_8);
        }
        output.goaway(lastStreamId, errorCode, debugData);
    }


    private void readPushPromiseFrame(int streamId, ByteBuffer buffer) throws Http2Exception {
        if (buffer.hasRemaining()) {
            socketWrapper.unRead(buffer);
        }
        throw new ConnectionException(sm.getString("http2Parser.processFramePushPromise",
                connectionId, Integer.valueOf(streamId)), Http2Error.PROTOCOL_ERROR);
    }


    private void readWindowUpdateFrame(int streamId, ByteBuffer buffer) throws Http2Exception, IOException {
        byte[] payload = new byte[4];
        buffer.get(payload);
        if (buffer.hasRemaining()) {
            socketWrapper.unRead(buffer);
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


    private void readContinuationFrame(int streamId, int flags, int payloadSize, ByteBuffer buffer)
            throws Http2Exception, IOException {
        if (headersCurrentStream == -1) {
            // No headers to continue
            throw new ConnectionException(sm.getString(
                    "http2Parser.processFrameContinuation.notExpected", connectionId,
                    Integer.toString(streamId)), Http2Error.PROTOCOL_ERROR);
        }

        readHeaderPayload(streamId, payloadSize, buffer);
        if (buffer.hasRemaining()) {
            socketWrapper.unRead(buffer);
        }

        if (Flags.isEndOfHeaders(flags)) {
            headersCurrentStream = -1;
            onHeadersComplete(streamId);
        }
    }


    private void readHeaderPayload(int streamId, int payloadSize, ByteBuffer buffer)
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
            int oldLimit = buffer.limit();
            buffer.limit(buffer.position() + toRead);
            headerReadBuffer.put(buffer);
            buffer.limit(oldLimit);
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


    private void readUnknownFrame(int streamId, FrameType frameType, int flags, int payloadSize, ByteBuffer buffer)
            throws IOException {
        try {
            swallow(streamId, payloadSize, false, buffer);
        } catch (ConnectionException e) {
            // Will never happen because swallow() is called with mustBeZero set
            // to false
        }
        if (buffer.hasRemaining()) {
            socketWrapper.unRead(buffer);
        }
        output.swallowed(streamId, frameType, flags, payloadSize);
    }


    private void swallow(int streamId, int len, boolean mustBeZero, ByteBuffer buffer)
            throws IOException, ConnectionException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("http2Parser.swallow.debug", connectionId,
                    Integer.toString(streamId), Integer.toString(len)));
        }
        if (len == 0) {
            return;
        }
        if (!mustBeZero) {
            buffer.position(buffer.position() + len);
        } else {
            int read = 0;
            byte[] buf = new byte[1024];
            while (read < len) {
                int thisTime = Math.min(buf.length, len - read);
                buffer.get(buf, 0, thisTime);
                // Validate the padding is zero since receiving non-zero padding
                // is a strong indication of either a faulty client or a server
                // side bug.
                for (int i = 0; i < thisTime; i++) {
                    if (buf[i] != 0) {
                        throw new ConnectionException(sm.getString("http2Parser.nonZeroPadding",
                                connectionId, Integer.toString(streamId)), Http2Error.PROTOCOL_ERROR);
                    }
                }
                read += thisTime;
            }
        }
    }


    protected class FrameCompletionCheck implements CompletionCheck {
        final FrameCompletionHandler handler;
        boolean validated = false;
        protected FrameCompletionCheck(FrameCompletionHandler handler) {
            this.handler = handler;
        }
        @Override
        public CompletionHandlerCall callHandler(CompletionState state,
                ByteBuffer[] buffers, int offset, int length) {
            // The first buffer should be 9 bytes long
            ByteBuffer frameHeaderBuffer = buffers[offset];
            if (frameHeaderBuffer.position() < 9) {
                return CompletionHandlerCall.CONTINUE;
            }

            handler.payloadSize = ByteUtil.getThreeBytes(frameHeaderBuffer, 0);
            handler.frameType = FrameType.valueOf(ByteUtil.getOneByte(frameHeaderBuffer, 3));
            handler.flags = ByteUtil.getOneByte(frameHeaderBuffer, 4);
            handler.streamId = ByteUtil.get31Bits(frameHeaderBuffer, 5);
            handler.state = state;

            if (!validated) {
                validated = true;
                try {
                    validateFrame(handler.expected, handler.frameType, handler.streamId, handler.flags, handler.payloadSize);
                } catch (StreamException e) {
                    error = e;
                    handler.streamException = true;
                } catch (Http2Exception e) {
                    error = e;
                    // The problem will be handled later, consider the frame read is done
                    return CompletionHandlerCall.DONE;
                }
            }

            if (buffers[offset + 1].position() < handler.payloadSize) {
                try {
                    upgradeHandler.checkPauseState();
                } catch (IOException e) {
                    error = e;
                }
                return CompletionHandlerCall.CONTINUE;
            }

            return CompletionHandlerCall.DONE;
        }

    }

    protected class FrameCompletionHandler implements CompletionHandler<Long, Void> {
        private final FrameType expected;
        private final ByteBuffer[] buffers;
        private int payloadSize;
        private FrameType frameType;
        private int flags;
        private int streamId;
        private boolean streamException = false;
        private CompletionState state = null;

        protected FrameCompletionHandler(FrameType expected, ByteBuffer... buffers) {
            this.expected = expected;
            this.buffers = buffers;
        }

        @Override
        public void completed(Long result, Void attachment) {
            if (streamException || error == null) {
                buffers[1].flip();
                try {
                    if (streamException) {
                        swallow(streamId, payloadSize, false, buffers[1]);
                    } else {
                        switch (frameType) {
                        case DATA:
                            readDataFrame(streamId, flags, payloadSize, buffers[1]);
                            break;
                        case HEADERS:
                            readHeadersFrame(streamId, flags, payloadSize, buffers[1]);
                            break;
                        case PRIORITY:
                            readPriorityFrame(streamId, buffers[1]);
                            break;
                        case RST:
                            readRstFrame(streamId, buffers[1]);
                            break;
                        case SETTINGS:
                            readSettingsFrame(flags, payloadSize, buffers[1]);
                            break;
                        case PUSH_PROMISE:
                            readPushPromiseFrame(streamId, buffers[1]);
                            break;
                        case PING:
                            readPingFrame(flags, buffers[1]);
                            break;
                        case GOAWAY:
                            readGoawayFrame(payloadSize, buffers[1]);
                            break;
                        case WINDOW_UPDATE:
                            readWindowUpdateFrame(streamId, buffers[1]);
                            break;
                        case CONTINUATION:
                            readContinuationFrame(streamId, flags, payloadSize, buffers[1]);
                            break;
                        case UNKNOWN:
                            readUnknownFrame(streamId, frameType, flags, payloadSize, buffers[1]);
                        }
                    }
                } catch (Exception e) {
                    failed(e, attachment);
                    return;
                }
            }
            if (state == CompletionState.DONE) {
                // The call was not completed inline, so must start reading new frames
                // or process any error
                upgradeHandler.upgradeDispatch(SocketEvent.OPEN_READ);
            }
        }

        @Override
        public void failed(Throwable exc, Void attachment) {
            error = exc;
            if (state == CompletionState.DONE) {
                // The call was not completed inline, so must start reading new frames
                // or process any error
                upgradeHandler.upgradeDispatch(SocketEvent.OPEN_READ);
            }
        }
        
    }

}
