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

    protected void unRead(ByteBuffer buffer) {
        if (buffer != null && buffer.hasRemaining()) {
            socketWrapper.unRead(buffer);
        }
    }

    protected void swallow(int streamId, int len, boolean mustBeZero, ByteBuffer buffer)
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
                        unRead(buffers[1]);
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
                    error = e;
                }
            }
            if (state == CompletionState.DONE) {
                // The call was not completed inline, so must start reading new frames
                // or process any error
                upgradeHandler.upgradeDispatch(SocketEvent.OPEN_READ);
            }
        }

        @Override
        public void failed(Throwable e, Void attachment) {
            error = e;
            if (state == CompletionState.DONE) {
                // The call was not completed inline, so must start reading new frames
                // or process any error
                upgradeHandler.upgradeDispatch(SocketEvent.OPEN_READ);
            }
        }
        
    }

}
