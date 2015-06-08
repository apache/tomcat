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

import java.util.function.IntPredicate;

import org.apache.tomcat.util.res.StringManager;

public enum FrameType {

    DATA          (0,   false,  true, null),
    HEADERS       (1,   false,  true, null),
    PRIORITY      (2,   false,  true, (x) -> x == 5),
    RST           (3,   false,  true, (x) -> x == 4),
    SETTINGS      (4,    true, false, (x) -> x % 6 == 0),
    PUSH_PROMISE  (5,   false,  true, (x) -> x >= 4),
    PING          (6,    true, false, (x) -> x == 8),
    GOAWAY        (7,    true, false, (x) -> x >= 8),
    WINDOW_UPDATE (8,    true,  true, (x) -> x == 4),
    CONTINUATION  (9,   false,  true, null),
    UNKNOWN       (256,  true,  true, null);

    private static final StringManager sm = StringManager.getManager(FrameType.class);

    private final int id;
    private final boolean streamZero;
    private final boolean streamNonZero;
    private final IntPredicate payloadSizeValidator;


    private FrameType(int id, boolean streamZero, boolean streamNonZero,
            IntPredicate payloadSizeValidator) {
        this.id = id;
        this.streamZero = streamZero;
        this.streamNonZero = streamNonZero;
        this.payloadSizeValidator =  payloadSizeValidator;
    }


    public byte getIdByte() {
        return (byte) id;
    }


    public void checkStream(int streamId) throws Http2Exception {
        if (streamId == 0 && !streamZero || streamId != 0 && !streamNonZero) {
            throw new ConnectionError(sm.getString("frameType.checkStream", this),
                    Error.PROTOCOL_ERROR);
        }
    }


    public void checkPayloadSize(int payloadSize) throws Http2Exception {
        if (payloadSizeValidator != null && !payloadSizeValidator.test(payloadSize)) {
            throw new ConnectionError(sm.getString("frameType.checkPayloadSize",
                    Integer.toString(payloadSize), this),
                    Error.FRAME_SIZE_ERROR);
        }
    }


    public static FrameType valueOf(int i) {
        switch(i) {
        case 0:
            return DATA;
        case 1:
            return HEADERS;
        case 2:
            return PRIORITY;
        case 3:
            return RST;
        case 4:
            return SETTINGS;
        case 5:
            return PUSH_PROMISE;
        case 6:
            return PING;
        case 7:
            return GOAWAY;
        case 8:
            return WINDOW_UPDATE;
        case 9:
            return CONTINUATION;
        default:
            return UNKNOWN;
        }
    }
}
