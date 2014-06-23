/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.websocket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.websocket.Extension;

/**
 * Internal implementation constants.
 */
public class Constants {

    protected static final String PACKAGE_NAME =
            Constants.class.getPackage().getName();
    // OP Codes
    public static final byte OPCODE_CONTINUATION = 0x00;
    public static final byte OPCODE_TEXT = 0x01;
    public static final byte OPCODE_BINARY = 0x02;
    public static final byte OPCODE_CLOSE = 0x08;
    public static final byte OPCODE_PING = 0x09;
    public static final byte OPCODE_PONG = 0x0A;

    // Internal OP Codes
    // RFC 6455 limits OP Codes to 4 bits so these should never clash
    // Always set bit 4 so these will be treated as control codes
    static final byte INTERNAL_OPCODE_FLUSH = 0x18;

    // Buffers
    static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    // Client connection
    public static final String HOST_HEADER_NAME = "Host";
    public static final String UPGRADE_HEADER_NAME = "Upgrade";
    public static final String UPGRADE_HEADER_VALUE = "websocket";
    public static final String CONNECTION_HEADER_NAME = "Connection";
    public static final String CONNECTION_HEADER_VALUE = "upgrade";
    public static final String WS_VERSION_HEADER_NAME = "Sec-WebSocket-Version";
    public static final String WS_VERSION_HEADER_VALUE = "13";
    public static final String WS_KEY_HEADER_NAME = "Sec-WebSocket-Key";
    public static final String WS_PROTOCOL_HEADER_NAME =
            "Sec-WebSocket-Protocol";
    public static final String WS_PROTOCOL_HEADER_NAME_LOWER =
            WS_PROTOCOL_HEADER_NAME.toLowerCase(Locale.ENGLISH);
    public static final String WS_EXTENSIONS_HEADER_NAME =
            "Sec-WebSocket-Extensions";

    public static final boolean STRICT_SPEC_COMPLIANCE =
            Boolean.getBoolean(
                    "org.apache.tomcat.websocket.STRICT_SPEC_COMPLIANCE");

    public static final List<Extension> INSTALLED_EXTENSIONS;

    static {
        List<Extension> installed = new ArrayList<>(1);
        installed.add(new WsExtension("permessage-deflate"));
        INSTALLED_EXTENSIONS = Collections.unmodifiableList(installed);
    }

    private Constants() {
        // Hide default constructor
    }
}
