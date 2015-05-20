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

import org.apache.tomcat.util.res.StringManager;

/**
 * TODO. ASCII art state diagram once the state machine is stable.
 */
public class WriteStateMachine {

    private static final StringManager sm = StringManager.getManager(WriteStateMachine.class);

    private WriteState state = WriteState.IDLE;


    synchronized void startRead() {
        switch(state) {
        case IDLE: {
            state = WriteState.READ_IN_PROGRESS;
            break;
        }
        case WRITE_PENDING: {
            // NO-OP. Race condition between stream calling write() and poller
            // triggering OPEN_READ
            break;
        }
        case WRITE_PENDING_BLOCKED_IO:
        case WRITE_PENDING_BLOCKED_FLOW_CONNECTION:
        case WRITE_PENDING_BLOCKED_FLOW_STREAMS: {
            // NO-OP for now. Incoming data may unblock flow control blocks
            break;
        }
        case READ_IN_PROGRESS:
        case WRITING: {
            throw new IllegalStateException(
                    sm.getString("writeStateMachine.ise", "startRead", state));
        }
        }
    }

    /**
     * @return <code>true</code> if the state changed to WRITING.
     */
    synchronized boolean endRead() {
        switch(state) {
        case READ_IN_PROGRESS: {
            state = WriteState.IDLE;
            return false;
        }
        case WRITE_PENDING: {
            state = WriteState.WRITING;
            return true;
        }
        case IDLE:
        case WRITING:
        case WRITE_PENDING_BLOCKED_IO:
        case WRITE_PENDING_BLOCKED_FLOW_STREAMS:
        case WRITE_PENDING_BLOCKED_FLOW_CONNECTION:
            throw new IllegalStateException(
                    sm.getString("writeStateMachine.ise", "endRead", state));
        }
        // Never reaches here. This is just to keep the compiler happy.
        return false;
    }


    synchronized void windowOpenedStream() {
        switch (state) {
        case WRITE_PENDING_BLOCKED_FLOW_STREAMS:
            state = WriteState.WRITE_PENDING;
            break;
        case READ_IN_PROGRESS:
        case WRITE_PENDING:
        case WRITE_PENDING_BLOCKED_IO:
        case WRITE_PENDING_BLOCKED_FLOW_CONNECTION:
            // NO-OP
            break;
        case IDLE:
        case WRITING:
            throw new IllegalStateException(
                    sm.getString("writeStateMachine.ise", "windowOpenedStream", state));
        }
    }


    synchronized void windowOpenedConnection() {
        switch (state) {
        case WRITE_PENDING_BLOCKED_FLOW_CONNECTION:
            state = WriteState.WRITE_PENDING;
            break;
        case READ_IN_PROGRESS:
        case WRITE_PENDING:
        case WRITE_PENDING_BLOCKED_IO:
        case WRITE_PENDING_BLOCKED_FLOW_STREAMS:
            // NO-OP
            break;
        case IDLE:
        case WRITING:
            throw new IllegalStateException(
                    sm.getString("writeStateMachine.ise", "windowOpenedConnection", state));
        }
    }


    synchronized boolean startWrite() {
        switch (state) {
        case WRITE_PENDING:
        case WRITE_PENDING_BLOCKED_IO: {
            state = WriteState.WRITING;
            return true;
        }
        case IDLE:
        case WRITE_PENDING_BLOCKED_FLOW_CONNECTION:
        case WRITE_PENDING_BLOCKED_FLOW_STREAMS: {
            // NO-OP. Race condition between stream calling write() and poller
            // triggering OPEN_READ
            return false;
        }
        case READ_IN_PROGRESS:
        case WRITING:
            throw new IllegalStateException(
                    sm.getString("writeStateMachine.ise", "startWrite", state));
        }
        // Never reaches here. This is just to keep the compiler happy.
        return false;
    }


    synchronized void endWrite(WriteState newState) {
        switch (state) {
        case WRITING: {
            switch (newState) {
            case IDLE:
            case WRITE_PENDING_BLOCKED_IO:
            case WRITE_PENDING_BLOCKED_FLOW_STREAMS:
            case WRITE_PENDING_BLOCKED_FLOW_CONNECTION: {
                state = newState;
                break;
            }
            case WRITE_PENDING:
            case WRITING:
            case READ_IN_PROGRESS:
                throw new IllegalStateException(
                        sm.getString("writeStateMachine.endWrite.ise", newState));
            }
            break;
        }
        case IDLE:
        case WRITE_PENDING:
        case WRITE_PENDING_BLOCKED_FLOW_CONNECTION:
        case WRITE_PENDING_BLOCKED_FLOW_STREAMS:
        case WRITE_PENDING_BLOCKED_IO:
        case READ_IN_PROGRESS:
            throw new IllegalStateException(
                    sm.getString("writeStateMachine.ise", "endWrite", state));
        }
    }


    /**
     * @return <code>true</code> if there needs to be a dispatch for OPEN_WRITE
     *         to trigger the actual write.
     */
    synchronized boolean addWrite() {
        switch(state) {
        case IDLE:
        case READ_IN_PROGRESS:
        case WRITE_PENDING_BLOCKED_FLOW_STREAMS: {
            state = WriteState.WRITE_PENDING;
            return true;
        }
        case WRITE_PENDING:
        case WRITE_PENDING_BLOCKED_FLOW_CONNECTION:
        case WRITE_PENDING_BLOCKED_IO:
        case WRITING:
            // NO-OP
            return false;
        }
        // Never reaches here. This is just to keep the compiler happy.
        return false;
    }

    static enum WriteState {
        IDLE,
        READ_IN_PROGRESS,
        WRITE_PENDING,
        WRITE_PENDING_BLOCKED_IO,
        WRITE_PENDING_BLOCKED_FLOW_STREAMS,
        WRITE_PENDING_BLOCKED_FLOW_CONNECTION,
        WRITING
    }
}
