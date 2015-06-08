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
public class StreamStateMachine {

    private static final Log log = LogFactory.getLog(StreamStateMachine.class);
    private static final StringManager sm = StringManager.getManager(StreamStateMachine.class);

    private final Stream stream;
    private State state;


    public StreamStateMachine(Stream stream) {
        this.stream = stream;
        stateChange(null, State.IDLE);
    }


    public synchronized void sentPushPromise() {
        stateChange(State.IDLE, State.RESERVED_LOCAL);
    }


    public synchronized void receivedPushPromis() {
        stateChange(State.IDLE, State.RESERVED_REMOTE);
    }


    public synchronized void sentStartOfHeaders() {
        stateChange(State.IDLE, State.OPEN);
        stateChange(State.RESERVED_LOCAL, State.HALF_CLOSED_REMOTE);
    }


    public synchronized void receivedStartOfHeaders() {
        stateChange(State.IDLE, State.OPEN);
        stateChange(State.RESERVED_REMOTE, State.HALF_CLOSED_LOCAL);
    }


    public synchronized void sentEndOfStream() {
        stateChange(State.OPEN, State.HALF_CLOSED_LOCAL);
        stateChange(State.HALF_CLOSED_REMOTE, State.CLOSED_TX);
    }


    public synchronized void recievedEndOfStream() {
        stateChange(State.OPEN, State.HALF_CLOSED_REMOTE);
        stateChange(State.HALF_CLOSED_LOCAL, State.CLOSED_RX);
    }


    public synchronized void sendReset() {
        stateChange(state, State.CLOSED_TX);
    }


    public synchronized void receiveReset() {
        stateChange(state, State.CLOSED_RST);
    }


    private void stateChange(State oldState, State newState) {
        if (state == oldState) {
            state = newState;
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("streamStateMachine.debug.change", stream.getConnectionId(),
                        stream.getIdentifier(), oldState, newState));
            }
        }
    }


    public synchronized void checkFrameType(FrameType frameType) throws Http2Exception {
        // No state change. Checks that the frame type is valid for the current
        // state of this stream.
        if (!isFrameTypePermitted(frameType)) {
            if (state.connectionErrorForInvalidFrame) {
                throw new ConnectionError(sm.getString("streamStateMachine.invalidFrame",
                        stream.getConnectionId(), stream.getIdentifier(), state, frameType),
                        state.errorCodeForInvalidFrame);
            } else {
                throw new StreamError(sm.getString("streamStateMachine.invalidFrame",
                        stream.getConnectionId(), stream.getIdentifier(), state, frameType),
                        state.errorCodeForInvalidFrame, stream.getIdentifier().intValue());
            }
        }
    }


    public synchronized boolean isFrameTypePermitted(FrameType frameType) {
        return state.isFrameTypePermitted(frameType);
    }


    private enum State {
        IDLE               (true,  Error.PROTOCOL_ERROR, FrameType.HEADERS, FrameType.PRIORITY),
        OPEN               (true,  Error.PROTOCOL_ERROR, FrameType.DATA, FrameType.HEADERS,
                                    FrameType.PRIORITY, FrameType.RST, FrameType.PUSH_PROMISE,
                                    FrameType.WINDOW_UPDATE),
        RESERVED_LOCAL     (true,  Error.PROTOCOL_ERROR, FrameType.PRIORITY, FrameType.RST,
                                    FrameType.WINDOW_UPDATE),
        RESERVED_REMOTE    (true,  Error.PROTOCOL_ERROR, FrameType.HEADERS, FrameType.PRIORITY,
                                    FrameType.RST),
        HALF_CLOSED_LOCAL  (true,  Error.PROTOCOL_ERROR, FrameType.DATA, FrameType.HEADERS,
                                    FrameType.PRIORITY, FrameType.RST, FrameType.PUSH_PROMISE,
                                    FrameType.WINDOW_UPDATE),
        HALF_CLOSED_REMOTE (true,  Error.STREAM_CLOSED, FrameType.PRIORITY, FrameType.RST,
                                    FrameType.WINDOW_UPDATE),
        CLOSED_RX          (true,  Error.STREAM_CLOSED, FrameType.PRIORITY),
        CLOSED_RST         (false, Error.STREAM_CLOSED, FrameType.PRIORITY),
        CLOSED_TX          (true,  Error.STREAM_CLOSED, FrameType.PRIORITY, FrameType.RST,
                                    FrameType.WINDOW_UPDATE);

        private final boolean connectionErrorForInvalidFrame;
        private final Error errorCodeForInvalidFrame;
        private final Set<FrameType> frameTypesPermitted = new HashSet<>();

        private State(boolean connectionErrorForInvalidFrame, Error errorCode,
                FrameType... frameTypes) {
            this.connectionErrorForInvalidFrame = connectionErrorForInvalidFrame;
            this.errorCodeForInvalidFrame = errorCode;
            for (FrameType frameType : frameTypes) {
                frameTypesPermitted.add(frameType);
            }
        }

        public boolean isFrameTypePermitted(FrameType frameType) {
            return frameTypesPermitted.contains(frameType);
        }
    }
}
