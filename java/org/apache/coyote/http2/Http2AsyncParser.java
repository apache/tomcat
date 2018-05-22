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

import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.net.SocketWrapperBase.BlockingMode;
import org.apache.tomcat.util.net.SocketWrapperBase.CompletionCheck;
import org.apache.tomcat.util.net.SocketWrapperBase.CompletionHandlerCall;
import org.apache.tomcat.util.net.SocketWrapperBase.CompletionState;

class Http2AsyncParser extends Http2Parser {

    private final SocketWrapperBase<?> socketWrapper;
    private final Http2AsyncUpgradeHandler upgradeHandler;
    private Throwable error = null;
    private final ByteBuffer header;
    private final ByteBuffer framePaylod;

    Http2AsyncParser(String connectionId, Input input, Output output, SocketWrapperBase<?> socketWrapper, Http2AsyncUpgradeHandler upgradeHandler) {
        super(connectionId, input, output);
        this.socketWrapper = socketWrapper;
        socketWrapper.getSocketBufferHandler().expand(input.getMaxFrameSize());
        this.upgradeHandler = upgradeHandler;
        header = ByteBuffer.allocate(9);
        framePaylod = ByteBuffer.allocate(input.getMaxFrameSize());
    }


    @Override
    protected boolean readFrame(boolean block, FrameType expected)
            throws IOException, Http2Exception {
        if (block) {
            // Only used when reading the connection preface
            return super.readFrame(block, expected);
        }
        handleAsyncException();
        header.clear();
        framePaylod.clear();
        FrameCompletionHandler handler = new FrameCompletionHandler(expected, header, framePaylod);
        CompletionState state =
                socketWrapper.read(BlockingMode.NON_BLOCK, socketWrapper.getWriteTimeout(), TimeUnit.MILLISECONDS, null, handler, handler, header, framePaylod);
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

    private class FrameCompletionHandler implements CompletionCheck, CompletionHandler<Long, Void> {

        private boolean parsedFrameHeader = false;
        private boolean validated = false;

        private final FrameType expected;
        private final ByteBuffer[] buffers;
        private int payloadSize;
        private FrameType frameType;
        private int flags;
        private int streamId;
        private boolean streamException = false;
        private CompletionState state = null;

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
            if (!parsedFrameHeader) {
                // The first buffer should be 9 bytes long
                ByteBuffer frameHeaderBuffer = buffers[0];
                if (frameHeaderBuffer.position() < 9) {
                    return CompletionHandlerCall.CONTINUE;
                }
                parsedFrameHeader = true;
                payloadSize = ByteUtil.getThreeBytes(frameHeaderBuffer, 0);
                frameType = FrameType.valueOf(ByteUtil.getOneByte(frameHeaderBuffer, 3));
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

            if (buffers[1].position() < payloadSize) {
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
                            swallow(streamId, payloadSize, false, payload);
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
                                readPushPromiseFrame(streamId, payload);
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
                                readUnknownFrame(streamId, frameType, flags, payloadSize, payload);
                            }
                        }
                        // See if there is a new 9 byte header and continue parsing if possible
                        if (payload.remaining() >= 9) {
                            int position = payload.position();
                            payloadSize = ByteUtil.getThreeBytes(payload, position);
                            frameType = FrameType.valueOf(ByteUtil.getOneByte(payload, position + 3));
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
            if (state == null || state == CompletionState.DONE) {
                upgradeHandler.upgradeDispatch(SocketEvent.ERROR);
            }
        }

    }

}
