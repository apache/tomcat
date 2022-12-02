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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * See <a href="https://tools.ietf.org/html/rfc7540#section-5.1">state
 * diagram</a> in RFC 7540.
 * <br>
 * The following additions are supported by this state machine:
 * <ul>
 * <li>differentiate between closed (normal) and closed caused by reset</li>
 * </ul>
 *
 */
class StreamStateMachine {

    private static final Log log = LogFactory.getLog(StreamStateMachine.class);
    private static final StringManager sm = StringManager.getManager(StreamStateMachine.class);

    private final String connectionId;
    private final String streamId;

    private State state;


    StreamStateMachine(String connectionId, String streamId) {
        this.connectionId = connectionId;
        this.streamId = streamId;
        stateChange(null, State.IDLE);
    }


    final synchronized void sentPushPromise() {
        stateChange(State.IDLE, State.RESERVED_LOCAL);
    }


    final synchronized void sentHeaders() {
        // No change if currently OPEN
        stateChange(State.RESERVED_LOCAL, State.HALF_CLOSED_REMOTE);
    }


    final synchronized void receivedStartOfHeaders() {
        stateChange(State.IDLE, State.OPEN);
        stateChange(State.RESERVED_REMOTE, State.HALF_CLOSED_LOCAL);
    }


    final synchronized void sentEndOfStream() {
        stateChange(State.OPEN, State.HALF_CLOSED_LOCAL);
        stateChange(State.HALF_CLOSED_REMOTE, State.CLOSED_TX);
    }


    final synchronized void receivedEndOfStream() {
        stateChange(State.OPEN, State.HALF_CLOSED_REMOTE);
        stateChange(State.HALF_CLOSED_LOCAL, State.CLOSED_RX);
    }


    /**
     * Marks the stream as reset. This method will not change the stream state
     * if:
     * <ul>
     * <li>The stream is already reset</li>
     * <li>The stream is already closed</li>
     * </ul>
     *
     * @throws IllegalStateException If the stream is in a state that does not
     *         permit resets
     */
    public synchronized void sendReset() {
        if (state == State.IDLE) {
            throw new IllegalStateException(sm.getString("streamStateMachine.debug.change",
                    connectionId, streamId, state));
        }
        if (state.canReset()) {
            stateChange(state, State.CLOSED_RST_TX);
        }
    }


    final synchronized void receivedReset() {
        stateChange(state, State.CLOSED_RST_RX);
    }


    private void stateChange(State oldState, State newState) {
        if (state == oldState) {
            state = newState;
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("streamStateMachine.debug.change", connectionId,
                        streamId, oldState, newState));
            }
        }
    }


    final synchronized void checkFrameType(FrameType frameType) throws Http2Exception {
        // No state change. Checks that receiving the frame type is valid for
        // the current state of this stream.
        if (!isFrameTypePermitted(frameType)) {
            if (state.connectionErrorForInvalidFrame) {
                throw new ConnectionException(sm.getString("streamStateMachine.invalidFrame",
                        connectionId, streamId, state, frameType),
                        state.errorCodeForInvalidFrame);
            } else {
                throw new StreamException(sm.getString("streamStateMachine.invalidFrame",
                        connectionId, streamId, state, frameType),
                        state.errorCodeForInvalidFrame, Integer.parseInt(streamId));
            }
        }
    }


    final synchronized boolean isFrameTypePermitted(FrameType frameType) {
        return state.isFrameTypePermitted(frameType);
    }


    final synchronized boolean isActive() {
        return state.isActive();
    }


    final synchronized boolean canRead() {
        return state.canRead();
    }


    final synchronized boolean canWrite() {
        return state.canWrite();
    }


    final synchronized boolean isClosedFinal() {
        return state == State.CLOSED_FINAL;
    }

    final synchronized void closeIfIdle() {
        stateChange(State.IDLE, State.CLOSED_FINAL);
    }

    final synchronized String getCurrentStateName() {
        return state.name();
    }

    private enum State {
        IDLE               (false, false, false, true,
                            Http2Error.PROTOCOL_ERROR, FrameType.HEADERS,
                                                       FrameType.PRIORITY),
        OPEN               (true,  true,  true,  true,
                            Http2Error.PROTOCOL_ERROR, FrameType.DATA,
                                                       FrameType.HEADERS,
                                                       FrameType.PRIORITY,
                                                       FrameType.RST,
                                                       FrameType.PUSH_PROMISE,
                                                       FrameType.WINDOW_UPDATE),
        RESERVED_LOCAL     (false, false, true,  true,
                            Http2Error.PROTOCOL_ERROR, FrameType.PRIORITY,
                                                       FrameType.RST,
                                                       FrameType.WINDOW_UPDATE),
        RESERVED_REMOTE    (false,  true, true,  true,
                            Http2Error.PROTOCOL_ERROR, FrameType.HEADERS,
                                                       FrameType.PRIORITY,
                                                       FrameType.RST),
        HALF_CLOSED_LOCAL  (true,  false, true,  true,
                            Http2Error.PROTOCOL_ERROR, FrameType.DATA,
                                                       FrameType.HEADERS,
                                                       FrameType.PRIORITY,
                                                       FrameType.RST,
                                                       FrameType.PUSH_PROMISE,
                                                       FrameType.WINDOW_UPDATE),
        HALF_CLOSED_REMOTE (false, true,  true,  true,
                            Http2Error.STREAM_CLOSED,  FrameType.PRIORITY,
                                                       FrameType.RST,
                                                       FrameType.WINDOW_UPDATE),
        CLOSED_RX          (false, false, false, true,
                            Http2Error.STREAM_CLOSED,  FrameType.PRIORITY),
        CLOSED_TX          (false, false, false, true,
                            Http2Error.STREAM_CLOSED,  FrameType.PRIORITY,
                                                       FrameType.RST,
                                                       FrameType.WINDOW_UPDATE),
        CLOSED_RST_RX      (false, false, false, false,
                            Http2Error.STREAM_CLOSED,  FrameType.PRIORITY),
        CLOSED_RST_TX      (false, false, false, false,
                            Http2Error.STREAM_CLOSED,  FrameType.DATA,
                                                       FrameType.HEADERS,
                                                       FrameType.PRIORITY,
                                                       FrameType.RST,
                                                       FrameType.PUSH_PROMISE,
                                                       FrameType.WINDOW_UPDATE),
        CLOSED_FINAL       (false, false, false, true,
                            Http2Error.PROTOCOL_ERROR, FrameType.PRIORITY);

        private final boolean canRead;
        private final boolean canWrite;
        private final boolean canReset;
        private final boolean connectionErrorForInvalidFrame;
        private final Http2Error errorCodeForInvalidFrame;
        private final Set<FrameType> frameTypesPermitted;

        private State(boolean canRead, boolean canWrite, boolean canReset,
                boolean connectionErrorForInvalidFrame, Http2Error errorCode,
                FrameType... frameTypes) {
            this.canRead = canRead;
            this.canWrite = canWrite;
            this.canReset = canReset;
            this.connectionErrorForInvalidFrame = connectionErrorForInvalidFrame;
            this.errorCodeForInvalidFrame = errorCode;
            frameTypesPermitted = new HashSet<>(Arrays.asList(frameTypes));
        }

        public boolean isActive() {
            return canWrite || canRead;
        }

        public boolean canRead() {
            return canRead;
        }

        public boolean canWrite() {
            return canWrite;
        }

        public boolean canReset() {
            return canReset;
        }

        public boolean isFrameTypePermitted(FrameType frameType) {
            return frameTypesPermitted.contains(frameType);
        }
    }
}
