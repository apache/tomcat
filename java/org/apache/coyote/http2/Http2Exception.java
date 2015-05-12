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

public class Http2Exception extends IOException {

    private static final long serialVersionUID = 1L;

    public static final byte[] NO_ERROR = { 0x00, 0x00, 0x00, 0x00 };
    public static final byte[] PROTOCOL_ERROR = { 0x00, 0x00, 0x00, 0x01 };
    public static final byte[] INTERNAL_ERROR = { 0x00, 0x00, 0x00, 0x02 };
    public static final byte[] FLOW_CONTROL_ERROR = { 0x00, 0x00, 0x00, 0x03 };
    public static final byte[] SETTINGS_TIMEOUT = { 0x00, 0x00, 0x00, 0x04 };
    public static final byte[] STREAM_CLOSED = { 0x00, 0x00, 0x00, 0x05 };
    public static final byte[] FRAME_SIZE_ERROR = { 0x00, 0x00, 0x00, 0x06};
    public static final byte[] REFUSED_STREAM = { 0x00, 0x00, 0x00, 0x07};
    public static final byte[] CANCEL = { 0x00, 0x00, 0x00, 0x08};
    public static final byte[] COMPRESSION_ERROR= { 0x00, 0x00, 0x00, 0x09};
    public static final byte[] CONNECT_ERROR = { 0x00, 0x00, 0x00, 0x0a};
    public static final byte[] ENHANCE_YOUR_CALM = { 0x00, 0x00, 0x00, 0x0b};
    public static final byte[] INADEQUATE_SECURITY = { 0x00, 0x00, 0x00, 0x0c};
    public static final byte[]  HTTP_1_1_REQUIRED = { 0x00, 0x00, 0x00, 0x0d};


    private final int streamId;
    private final byte[] errorCode;


    public Http2Exception(String msg, int streamId, byte[] errorCode) {
        super(msg);
        this.streamId = streamId;
        this.errorCode = errorCode;
    }


    public int getStreamId() {
        return streamId;
    }


    public byte[] getErrorCode() {
        return errorCode;
    }
}
