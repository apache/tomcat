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

import java.nio.ByteBuffer;

/**
 * Base class for all streams other than stream 0, the connection. Primarily provides functionality shared between full
 * Stream and RecycledStream.
 */
abstract class AbstractNonZeroStream extends AbstractStream {

    protected static final ByteBuffer ZERO_LENGTH_BYTEBUFFER = ByteBuffer.allocate(0);

    protected final StreamStateMachine state;


    AbstractNonZeroStream(String connectionId, Integer identifier) {
        super(identifier);
        this.state = new StreamStateMachine(connectionId, getIdAsString());
    }


    AbstractNonZeroStream(Integer identifier, StreamStateMachine state) {
        super(identifier);
        this.state = state;
    }


    /**
     * @return {@code true} if the state indicates a close
     */
    final boolean isClosedFinal() {
        return state.isClosedFinal();
    }


    /**
     * Check the frame type against the state
     *
     * @param frameType the type
     *
     * @throws Http2Exception if an error is detected
     */
    final void checkState(FrameType frameType) throws Http2Exception {
        state.checkFrameType(frameType);
    }


    /**
     * Obtain the ByteBuffer to store DATA frame payload data for this stream that has been received from the client.
     *
     * @return {@code null} if the DATA frame payload can be swallowed, or a ByteBuffer with at least enough space
     *             remaining for the current flow control window for stream data from the client.
     */
    abstract ByteBuffer getInputByteBuffer(boolean create);


    /**
     * Notify that some data has been received.
     *
     * @param payloadSize the byte count
     *
     * @throws Http2Exception if an error is detected
     */
    abstract void receivedData(int payloadSize) throws Http2Exception;
}
