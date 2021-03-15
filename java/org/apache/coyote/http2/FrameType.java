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

enum FrameType {

    DATA          (0,   false,  true, null,               false),
    HEADERS       (1,   false,  true, null,                true),
    PRIORITY      (2,   false,  true, equals(5),          false),
    RST           (3,   false,  true, equals(4),          false),
    SETTINGS      (4,    true, false, dividableBy(6),      true),
    PUSH_PROMISE  (5,   false,  true, greaterOrEquals(4),  true),
    PING          (6,    true, false, equals(8),          false),
    GOAWAY        (7,    true, false, greaterOrEquals(8), false),
    WINDOW_UPDATE (8,    true,  true, equals(4),           true),
    CONTINUATION  (9,   false,  true, null,                true),
    UNKNOWN       (256,  true,  true, null,               false);

    private static final StringManager sm = StringManager.getManager(FrameType.class);

    private final int id;
    private final boolean streamZero;
    private final boolean streamNonZero;
    private final IntPredicate payloadSizeValidator;
    private final boolean payloadErrorFatal;


    private FrameType(int id, boolean streamZero, boolean streamNonZero,
            IntPredicate payloadSizeValidator,  boolean payloadErrorFatal) {
        this.id = id;
        this.streamZero = streamZero;
        this.streamNonZero = streamNonZero;
        this.payloadSizeValidator =  payloadSizeValidator;
        this.payloadErrorFatal = payloadErrorFatal;
    }


    int getId() {
        return id;
    }


    byte getIdByte() {
        return (byte) id;
    }


    void check(int streamId, int payloadSize) throws Http2Exception {
        // Is FrameType valid for the given stream?
        if (streamId == 0 && !streamZero || streamId != 0 && !streamNonZero) {
            throw new ConnectionException(sm.getString("frameType.checkStream", this),
                    Http2Error.PROTOCOL_ERROR);
        }

        // Is the payload size valid for the given FrameType
        if (payloadSizeValidator != null && !payloadSizeValidator.test(payloadSize)) {
            if (payloadErrorFatal || streamId == 0) {
                throw new ConnectionException(sm.getString("frameType.checkPayloadSize",
                        Integer.toString(payloadSize), this),
                        Http2Error.FRAME_SIZE_ERROR);
            } else {
                throw new StreamException(sm.getString("frameType.checkPayloadSize",
                        Integer.toString(payloadSize), this),
                        Http2Error.FRAME_SIZE_ERROR, streamId);
            }
        }
    }


    static FrameType valueOf(int i) {
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

    private interface IntPredicate {
        boolean test(int x);
    }

    private static IntPredicate greaterOrEquals(final int y) {
        return new IntPredicate() {
            @Override
            public boolean test(int x) {
                return x >= y;
            }
        };
    }

    private static IntPredicate equals(final int y) {
        return new IntPredicate() {
            @Override
            public boolean test(int x) {
                return x == y;
            }
        };
    }

    private static IntPredicate dividableBy(final int y) {
        return new IntPredicate() {
            @Override
            public boolean test(int x) {
                return x % y == 0;
            }
        };
    }
}
