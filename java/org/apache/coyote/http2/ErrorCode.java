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

public enum ErrorCode {

    NO_ERROR            (0x00),
    PROTOCOL_ERROR      (0x01),
    INTERNAL_ERROR      (0x02),
    FLOW_CONTROL_ERROR  (0x03),
    SETTINGS_TIMEOUT    (0x04),
    STREAM_CLOSED       (0x05),
    FRAME_SIZE_ERROR    (0x06),
    REFUSED_STREAM      (0x07),
    CANCEL              (0x08),
    COMPRESSION_ERROR   (0x09),
    CONNECT_ERROR       (0x0a),
    ENHANCE_YOUR_CALM   (0x0b),
    INADEQUATE_SECURITY (0x0c),
    HTTP_1_1_REQUIRED   (0x0d);

    private final long errorCode;

    private ErrorCode(long errorCode) {
        this.errorCode = errorCode;
    }


    public long getErrorCode() {
        return errorCode;
    }


    public byte[] getErrorCodeBytes() {
        byte[] errorCodeByte = new byte[4];
        ByteUtil.setFourBytes(errorCodeByte, 0, errorCode);
        return errorCodeByte;
    }
}
