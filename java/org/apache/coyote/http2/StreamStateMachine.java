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

/**
 * See <a href="https://tools.ietf.org/html/rfc7540#section-5.1">state
 * diagram</a> in RFC 7540.
 * <br>
 * The following additions are supported by this state machine:
 * <ul>
 * <li>differentiate between closed (normal) and closed caused by reset</li>
 * <li>allow a transition from idle to closed if reset is sent or received</li>
 * </ul>
 *
 */
public class StreamStateMachine {

    private State state = State.IDLE;


    public synchronized void sendPushPromise() {
        if (state == State.IDLE) {
            state = State.RESERVED_LOCAL;
        } else {
            // TODO: ProtocolExcpetion? i18n
            throw new IllegalStateException();
        }
    }


    public synchronized void receivePushPromis() {
        if (state == State.IDLE) {
            state = State.RESERVED_REMOTE;
        } else {
            // TODO: ProtocolExcpetion? i18n
            throw new IllegalStateException();
        }
    }


    public synchronized void sendHeaders() {
        if (state == State.IDLE) {
            state = State.OPEN;
        } else if (state == State.RESERVED_LOCAL) {
            state = State.HALF_CLOSED_REMOTE;
        } else {
            // TODO: ProtocolExcpetion? i18n
            throw new IllegalStateException();
        }
    }


    public synchronized void receiveHeaders() {
        if (state == State.IDLE) {
            state = State.OPEN;
        } else if (state == State.RESERVED_REMOTE) {
            state = State.HALF_CLOSED_LOCAL;
        } else {
            // TODO: ProtocolExcpetion? i18n
            throw new IllegalStateException();
        }
    }


    public synchronized void sendEndOfStream() {
        if (state == State.OPEN) {
            state = State.HALF_CLOSED_LOCAL;
        } else if (state == State.HALF_CLOSED_REMOTE) {
            state = State.CLOSED;
        } else {
            // TODO: ProtocolExcpetion? i18n
            throw new IllegalStateException();
        }
    }


    public synchronized void recieveEndOfStream() {
        if (state == State.OPEN) {
            state = State.HALF_CLOSED_REMOTE;
        } else if (state == State.HALF_CLOSED_LOCAL) {
            state = State.CLOSED;
        } else {
            // TODO: ProtocolExcpetion? i18n
            throw new IllegalStateException();
        }
    }


    public synchronized void sendReset() {
        if (state == State.CLOSED) {
            // This should never happen
            // TODO: ProtocolExcpetion? i18n
            throw new IllegalStateException();
        }
        state = State.CLOSED_RESET;
    }


    public synchronized void recieveReset() {
        state = State.CLOSED_RESET;
    }


    public synchronized void receiveHeader() {
        // Doesn't change state (that happens at the end of the headers when
        // receiveHeaders() is called. This just checks that the stream is in a
        // valid state to receive headers.
        if (state == State.CLOSED_RESET) {
            // Allow this. Client may not know that stream has been reset.
        } else if (state == State.IDLE || state == State.RESERVED_REMOTE) {
            // Allow these. This is normal operation.
        } else {
            // TODO: ProtocolExcpetion? i18n
            throw new IllegalStateException();
        }
    }


    private enum State {
        IDLE,
        OPEN,
        RESERVED_LOCAL,
        RESERVED_REMOTE,
        HALF_CLOSED_LOCAL,
        HALF_CLOSED_REMOTE,
        CLOSED,
        CLOSED_RESET
    }
}
