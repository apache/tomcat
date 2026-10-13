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

import org.junit.Assert;
import org.junit.Test;

public class TestStreamStateMachine {

    private static final String CONNECTION_ID = "-1";
    private static final String STREAM_ID = "3";

    /*
     * RFC 9113 section 5.1: an endpoint that sends the frame that closed the stream might receive a WINDOW_UPDATE or
     * RST_STREAM frame from its peer in the time before the peer receives and processes that closing frame. Receipt of
     * such frames must not be considered an error. These are the same frames permitted for the closed state reached
     * when the final frame is sent rather than received.
     */
    @Test
    public void closedRxAcceptsGraceFrames() throws Exception {
        StreamStateMachine state = new StreamStateMachine(CONNECTION_ID, STREAM_ID);

        // Close the stream by sending then receiving the end of stream.
        state.receivedStartOfHeaders();
        state.sentEndOfStream();
        state.receivedEndOfStream();

        state.checkFrameType(FrameType.PRIORITY);
        state.checkFrameType(FrameType.RST);
        state.checkFrameType(FrameType.WINDOW_UPDATE);
    }


    /*
     * RFC 9113 section 5.1: receipt of any other frame type on a closed stream may be treated as a connection error of
     * type STREAM_CLOSED.
     */
    @Test
    public void closedRxRejectsOtherFrames() throws Exception {
        StreamStateMachine state = new StreamStateMachine(CONNECTION_ID, STREAM_ID);

        // Close the stream by sending then receiving the end of stream.
        state.receivedStartOfHeaders();
        state.sentEndOfStream();
        state.receivedEndOfStream();

        try {
            state.checkFrameType(FrameType.DATA);
            Assert.fail("Expected a connection error for a DATA frame received in the closed state");
        } catch (ConnectionException ce) {
            Assert.assertEquals(Http2Error.STREAM_CLOSED, ce.getError());
        }
    }
}
