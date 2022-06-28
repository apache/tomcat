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
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.WebConnection;

import org.apache.coyote.ProtocolException;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.net.SocketWrapperBase.BlockingMode;
import org.apache.tomcat.util.net.SocketWrapperBase.CompletionCheck;
import org.apache.tomcat.util.net.SocketWrapperBase.CompletionHandlerCall;
import org.apache.tomcat.util.net.SocketWrapperBase.CompletionState;

class Http2AsyncParser extends Http2Parser {

    private final SocketWrapperBase<?> socketWrapper;
    private final Http2AsyncUpgradeHandler upgradeHandler;
    private volatile Throwable error = null;

    Http2AsyncParser(String connectionId, Input input, Output output, SocketWrapperBase<?> socketWrapper, Http2AsyncUpgradeHandler upgradeHandler) {
        super(connectionId, input, output);
        this.socketWrapper = socketWrapper;
        socketWrapper.getSocketBufferHandler().expand(input.getMaxFrameSize());
        this.upgradeHandler = upgradeHandler;
    }


    @Override
    void readConnectionPreface(WebConnection webConnection, Stream stream) throws Http2Exception {
        byte[] prefaceData = new byte[CLIENT_PREFACE_START.length];
        ByteBuffer preface = ByteBuffer.wrap(prefaceData);
        ByteBuffer header = ByteBuffer.allocate(9);
        ByteBuffer framePayload = ByteBuffer.allocate(input.getMaxFrameSize());
        PrefaceCompletionHandler handler = new PrefaceCompletionHandler(webConnection, stream, prefaceData, preface, header, framePayload);
        socketWrapper.read(BlockingMode.NON_BLOCK, socketWrapper.getReadTimeout(), TimeUnit.MILLISECONDS, null,
                handler, handler, preface, header, framePayload);
    }


    private class PrefaceCompletionHandler extends FrameCompletionHandler {

        private final WebConnection webConnection;
        private final Stream stream;
        private final byte[] prefaceData;

        private volatile boolean prefaceValidated = false;

        private PrefaceCompletionHandler(WebConnection webConnection, Stream stream, byte[] prefaceData, ByteBuffer... buffers) {
            super(FrameType.SETTINGS, buffers);
            this.webConnection = webConnection;
            this.stream = stream;
            this.prefaceData = prefaceData;
        }

        @Override
        public CompletionHandlerCall callHandler(CompletionState state, ByteBuffer[] buffers, int offset, int length) {
            if (offset != 0 || length != 3) {
                try {
                    throw new IllegalArgumentException(sm.getString("http2Parser.invalidBuffers"));
                } catch (IllegalArgumentException e) {
                    error = e;
                    return CompletionHandlerCall.DONE;
                }
            }
            if (!prefaceValidated) {
                if (buffers[0].hasRemaining()) {
                    // The preface must be fully read before being validated
                    return CompletionHandlerCall.CONTINUE;
                }
                // Validate preface content
                for (int i = 0; i < CLIENT_PREFACE_START.length; i++) {
                    if (CLIENT_PREFACE_START[i] != prefaceData[i]) {
                        error = new ProtocolException(sm.getString("http2Parser.preface.invalid"));
                        return CompletionHandlerCall.DONE;
                    }
                }
                prefaceValidated = true;
            }
            return validate(state, buffers[1], buffers[2]);
        }

        @Override
        public void completed(Long result, Void attachment) {
            if (streamException || error == null) {
                ByteBuffer payload = buffers[2];
                payload.flip();
                try {
                    if (streamException) {
                        swallowPayload(streamId, frameTypeId, payloadSize, false, payload);
                    } else {
                        readSettingsFrame(flags, payloadSize, payload);
                    }
                } catch (RuntimeException | IOException | Http2Exception e) {
                    error = e;
                }
                // Any extra frame is not processed yet, so put back any leftover data
                if (payload.hasRemaining()) {
                    socketWrapper.unRead(payload);
                }
                // Finish processing the connection
                upgradeHandler.processConnectionCallback(webConnection, stream);
            } else {
                upgradeHandler.closeConnection(new ConnectionException(error.getMessage(), Http2Error.PROTOCOL_ERROR, error));
            }
            // Continue reading frames
            upgradeHandler.upgradeDispatch(SocketEvent.OPEN_READ);
        }
    }

    @Override
    protected boolean readFrame(boolean block, FrameType expected)
            throws IOException, Http2Exception {
        handleAsyncException();
        ByteBuffer header = ByteBuffer.allocate(9);
        ByteBuffer framePayload = ByteBuffer.allocate(input.getMaxFrameSize());
        FrameCompletionHandler handler = new FrameCompletionHandler(expected, header, framePayload);
        CompletionState state =
                socketWrapper.read(block ? BlockingMode.BLOCK : BlockingMode.NON_BLOCK, socketWrapper.getReadTimeout(), TimeUnit.MILLISECONDS, null, handler, handler, header, framePayload);
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
            } else if (error instanceof RuntimeException) {
                throw (RuntimeException) error;
            } else {
                throw new RuntimeException(error);
            }
        }
    }

    private class FrameCompletionHandler implements CompletionCheck, CompletionHandler<Long, Void> {

        private final FrameType expected;
        protected final ByteBuffer[] buffers;

        private volatile boolean parsedFrameHeader = false;
        private volatile boolean validated = false;
        private volatile CompletionState state = null;
        protected volatile int payloadSize;
        protected volatile int frameTypeId;
        protected volatile FrameType frameType;
        protected volatile int flags;
        protected volatile int streamId;
        protected volatile boolean streamException = false;

        private FrameCompletionHandler(FrameType expected, ByteBuffer... buffers) {
            this.expected = expected;
            this.buffers = buffers;
        }

        @Override
        public CompletionHandlerCall callHandler(CompletionState state,
                ByteBuffer[] buffers, int offset, int length) {
            if (offset != 0 || length != 2) {
                try {
                    throw new IllegalArgumentException(sm.getString("http2Parser.invalidBuffers"));
                } catch (IllegalArgumentException e) {
                    error = e;
                    return CompletionHandlerCall.DONE;
                }
            }
            return validate(state, buffers[0], buffers[1]);
        }

        protected CompletionHandlerCall validate(CompletionState state, ByteBuffer frameHeaderBuffer, ByteBuffer payload) {
            if (!parsedFrameHeader) {
                // The first buffer should be 9 bytes long
                if (frameHeaderBuffer.position() < 9) {
                    return CompletionHandlerCall.CONTINUE;
                }
                parsedFrameHeader = true;
                payloadSize = ByteUtil.getThreeBytes(frameHeaderBuffer, 0);
                frameTypeId = ByteUtil.getOneByte(frameHeaderBuffer, 3);
                frameType = FrameType.valueOf(frameTypeId);
                flags = ByteUtil.getOneByte(frameHeaderBuffer, 4);
                streamId = ByteUtil.get31Bits(frameHeaderBuffer, 5);
            }
            this.state = state;

            if (!validated) {
                validated = true;
                try {
                    validateFrame(expected, frameType, streamId, flags, payloadSize);
                } catch (StreamException e) {
                    error = e;
                    streamException = true;
                } catch (Http2Exception e) {
                    error = e;
                    // The problem will be handled later, consider the frame read is done
                    return CompletionHandlerCall.DONE;
                }
            }

            if (payload.position() < payloadSize) {
                return CompletionHandlerCall.CONTINUE;
            }

            return CompletionHandlerCall.DONE;
        }

        @Override
        public void completed(Long result, Void attachment) {
            if (streamException || error == null) {
                ByteBuffer payload = buffers[1];
                payload.flip();
                try {
                    boolean continueParsing;
                    do {
                        continueParsing = false;
                        if (streamException) {
                            swallowPayload(streamId, frameTypeId, payloadSize, false, payload);
                        } else {
                            switch (frameType) {
                            case DATA:
                                readDataFrame(streamId, flags, payloadSize, payload);
                                break;
                            case HEADERS:
                                readHeadersFrame(streamId, flags, payloadSize, payload);
                                break;
                            case PRIORITY:
                                readPriorityFrame(streamId, payload);
                                break;
                            case RST:
                                readRstFrame(streamId, payload);
                                break;
                            case SETTINGS:
                                readSettingsFrame(flags, payloadSize, payload);
                                break;
                            case PUSH_PROMISE:
                                readPushPromiseFrame(streamId, flags, payloadSize, payload);
                                break;
                            case PING:
                                readPingFrame(flags, payload);
                                break;
                            case GOAWAY:
                                readGoawayFrame(payloadSize, payload);
                                break;
                            case WINDOW_UPDATE:
                                readWindowUpdateFrame(streamId, payload);
                                break;
                            case CONTINUATION:
                                readContinuationFrame(streamId, flags, payloadSize, payload);
                                break;
                            case UNKNOWN:
                                readUnknownFrame(streamId, frameTypeId, flags, payloadSize, payload);
                            }
                        }
                        // See if there is a new 9 byte header and continue parsing if possible
                        if (payload.remaining() >= 9) {
                            int position = payload.position();
                            payloadSize = ByteUtil.getThreeBytes(payload, position);
                            frameTypeId = ByteUtil.getOneByte(payload, position + 3);
                            frameType = FrameType.valueOf(frameTypeId);
                            flags = ByteUtil.getOneByte(payload, position + 4);
                            streamId = ByteUtil.get31Bits(payload, position + 5);
                            streamException = false;
                            if (payload.remaining() - 9 >= payloadSize) {
                                continueParsing = true;
                                // Now go over frame header
                                payload.position(payload.position() + 9);
                                try {
                                    validateFrame(null, frameType, streamId, flags, payloadSize);
                                } catch (StreamException e) {
                                    error = e;
                                    streamException = true;
                                } catch (Http2Exception e) {
                                    error = e;
                                    continueParsing = false;
                                }
                            }
                        }
                    } while (continueParsing);
                } catch (RuntimeException | IOException | Http2Exception e) {
                    error = e;
                }
                if (payload.hasRemaining()) {
                    socketWrapper.unRead(payload);
                }
            }
            if (state == CompletionState.DONE) {
                // The call was not completed inline, so must start reading new frames
                // or process the stream exception
                upgradeHandler.upgradeDispatch(SocketEvent.OPEN_READ);
            }
        }

        @Override
        public void failed(Throwable e, Void attachment) {
            // Always a fatal IO error
            error = e;
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("http2Parser.error", connectionId, Integer.valueOf(streamId), frameType), e);
            }
            if (state == null || state == CompletionState.DONE) {
                upgradeHandler.upgradeDispatch(SocketEvent.ERROR);
            }
        }

    }

}
