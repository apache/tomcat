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

enum Setting {
    HEADER_TABLE_SIZE(1),
    ENABLE_PUSH(2),
    MAX_CONCURRENT_STREAMS(3),
    INITIAL_WINDOW_SIZE(4),
    MAX_FRAME_SIZE(5),
    MAX_HEADER_LIST_SIZE(6),
    ENABLE_CONNECT_PROTOCOL(8),
    NO_RFC7540_PRIORITIES(9),
    TLS_RENEG_PERMITTED(10),
    UNKNOWN(Integer.MAX_VALUE);

    private final int id;

    Setting(int id) {
        this.id = id;
    }

    final int getId() {
        return id;
    }

    @Override
    public final String toString() {
        return Integer.toString(id);
    }

    static Setting valueOf(int i) {
        return switch (i) {
            case 1 -> HEADER_TABLE_SIZE;
            case 2 -> ENABLE_PUSH;
            case 3 -> MAX_CONCURRENT_STREAMS;
            case 4 -> INITIAL_WINDOW_SIZE;
            case 5 -> MAX_FRAME_SIZE;
            case 6 -> MAX_HEADER_LIST_SIZE;
            case 8 -> ENABLE_CONNECT_PROTOCOL;
            case 9 -> NO_RFC7540_PRIORITIES;
            case 10 -> TLS_RENEG_PERMITTED;
            default -> UNKNOWN;
        };
    }
}
