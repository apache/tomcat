/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.websocket;

import javax.websocket.CloseReason.CloseCode;
import javax.websocket.CloseReason.CloseCodes;

/**
 * Utility class for internal use only within the
 * {@link org.apache.tomcat.websocket} package.
 */
class Util {

    private Util() {
        // Hide default constructor
    }


    /**
     * Converts a path defined for a WebSocket endpoint into a path that can be
     * used as a servlet mapping.
     *
     * @param wsPath The WebSocket endpoint path to convert
     * @return The servlet mapping
     */
    static String getServletMappingPath(String wsPath) {
        int templateStart = wsPath.indexOf('{');
        if (templateStart == -1) {
            if (wsPath.charAt(wsPath.length() - 1) == '/') {
                return wsPath + '*';
            } else {
                return wsPath + "/*";
            }
        } else {
            String temp = wsPath.substring(0, templateStart);
            if (temp.charAt(temp.length() - 1) == '/') {
                return temp + '*';
            } else {
                return temp.substring(0, temp.lastIndexOf('/') + 1) + '*';
            }
        }
    }


    static CloseCode getCloseCode(int code) {
        switch (code) {
            case 1000:
                return CloseCodes.NORMAL_CLOSURE;
            case 1001:
                return CloseCodes.GOING_AWAY;
            case 1002:
                return CloseCodes.PROTOCOL_ERROR;
            case 1003:
                return CloseCodes.CANNOT_ACCEPT;
            case 1004:
                return CloseCodes.RESERVED;
            case 1005:
                return CloseCodes.NO_STATUS_CODE;
            case 1006:
                return CloseCodes.CLOSED_ABNORMALLY;
            case 1007:
                return CloseCodes.NOT_CONSISTENT;
            case 1008:
                return CloseCodes.VIOLATED_POLICY;
            case 1009:
                return CloseCodes.TOO_BIG;
            case 1010:
                return CloseCodes.NO_EXTENSION;
            case 1011:
                return CloseCodes.UNEXPECTED_CONDITION;
            case 1012:
                return CloseCodes.SERVICE_RESTART;
            case 1013:
                return CloseCodes.TRY_AGAIN_LATER;
            case 1015:
                return CloseCodes.TLS_HANDSHAKE_FAILURE;
            default:
                return CloseCodes.PROTOCOL_ERROR;
        }
    }
}
