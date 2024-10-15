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
package org.apache.coyote.ajp;

import java.util.HashMap;
import java.util.Map;

/**
 * Constants.
 *
 * @author Remy Maucherat
 */
public final class Constants {

    public static final int DEFAULT_CONNECTION_TIMEOUT = -1;

    // Prefix codes for message types from server to container
    public static final byte JK_AJP13_FORWARD_REQUEST = 2;
    public static final byte JK_AJP13_SHUTDOWN = 7; // XXX Unused
    public static final byte JK_AJP13_PING_REQUEST = 8; // XXX Unused
    public static final byte JK_AJP13_CPING_REQUEST = 10;

    // Prefix codes for message types from container to server
    public static final byte JK_AJP13_SEND_BODY_CHUNK = 3;
    public static final byte JK_AJP13_SEND_HEADERS = 4;
    public static final byte JK_AJP13_END_RESPONSE = 5;
    public static final byte JK_AJP13_GET_BODY_CHUNK = 6;
    public static final byte JK_AJP13_CPONG_REPLY = 9;

    // Integer codes for common response header strings
    public static final int SC_RESP_CONTENT_TYPE = 0xA001;
    public static final int SC_RESP_CONTENT_LANGUAGE = 0xA002;
    public static final int SC_RESP_CONTENT_LENGTH = 0xA003;
    public static final int SC_RESP_DATE = 0xA004;
    public static final int SC_RESP_LAST_MODIFIED = 0xA005;
    public static final int SC_RESP_LOCATION = 0xA006;
    public static final int SC_RESP_SET_COOKIE = 0xA007;
    public static final int SC_RESP_SET_COOKIE2 = 0xA008;
    public static final int SC_RESP_SERVLET_ENGINE = 0xA009;
    public static final int SC_RESP_STATUS = 0xA00A;
    public static final int SC_RESP_WWW_AUTHENTICATE = 0xA00B;
    public static final int SC_RESP_AJP13_MAX = 11;

    // Integer codes for common (optional) request attribute names
    public static final byte SC_A_CONTEXT = 1; // XXX Unused
    public static final byte SC_A_SERVLET_PATH = 2; // XXX Unused
    public static final byte SC_A_REMOTE_USER = 3;
    public static final byte SC_A_AUTH_TYPE = 4;
    public static final byte SC_A_QUERY_STRING = 5;
    public static final byte SC_A_JVM_ROUTE = 6;
    public static final byte SC_A_SSL_CERT = 7;
    public static final byte SC_A_SSL_CIPHER = 8;
    public static final byte SC_A_SSL_SESSION = 9;
    public static final byte SC_A_SSL_KEY_SIZE = 11;
    public static final byte SC_A_SECRET = 12;
    public static final byte SC_A_STORED_METHOD = 13;

    // Used for attributes which are not in the list above
    public static final byte SC_A_REQ_ATTRIBUTE = 10;

    /**
     * AJP private request attributes
     */
    public static final String SC_A_REQ_LOCAL_ADDR = "AJP_LOCAL_ADDR";
    public static final String SC_A_REQ_REMOTE_PORT = "AJP_REMOTE_PORT";
    public static final String SC_A_SSL_PROTOCOL = "AJP_SSL_PROTOCOL";

    // Terminates list of attributes
    public static final byte SC_A_ARE_DONE = (byte) 0xFF;

    /**
     * Default maximum total byte size for an AJP packet
     */
    public static final int MAX_PACKET_SIZE = 8192;
    /**
     * Size of basic packet header
     */
    public static final int H_SIZE = 4;

    /**
     * Size of the header metadata
     */
    public static final int READ_HEAD_LEN = 6;
    public static final int SEND_HEAD_LEN = 8;

    /**
     * Default maximum size of data that can be sent in one packet
     */
    public static final int MAX_READ_SIZE = MAX_PACKET_SIZE - READ_HEAD_LEN;
    public static final int MAX_SEND_SIZE = MAX_PACKET_SIZE - SEND_HEAD_LEN;

    // Translates integer codes to names of HTTP methods
    private static final String[] methodTransArray =
            { "OPTIONS", "GET", "HEAD", "POST", "PUT", "DELETE", "TRACE", "PROPFIND", "PROPPATCH", "MKCOL", "COPY",
                    "MOVE", "LOCK", "UNLOCK", "ACL", "REPORT", "VERSION-CONTROL", "CHECKIN", "CHECKOUT", "UNCHECKOUT",
                    "SEARCH", "MKWORKSPACE", "UPDATE", "LABEL", "MERGE", "BASELINE-CONTROL", "MKACTIVITY" };

    /**
     * Converts an AJP coded HTTP method to the method name.
     *
     * @param code the coded value
     *
     * @return the string value of the method
     */
    public static String getMethodForCode(final int code) {
        return methodTransArray[code];
    }

    public static final int SC_M_JK_STORED = (byte) 0xFF;

    // id's for common request headers
    public static final int SC_REQ_ACCEPT = 1;
    public static final int SC_REQ_ACCEPT_CHARSET = 2;
    public static final int SC_REQ_ACCEPT_ENCODING = 3;
    public static final int SC_REQ_ACCEPT_LANGUAGE = 4;
    public static final int SC_REQ_AUTHORIZATION = 5;
    public static final int SC_REQ_CONNECTION = 6;
    public static final int SC_REQ_CONTENT_TYPE = 7;
    public static final int SC_REQ_CONTENT_LENGTH = 8;
    public static final int SC_REQ_COOKIE = 9;
    public static final int SC_REQ_COOKIE2 = 10;
    public static final int SC_REQ_HOST = 11;
    public static final int SC_REQ_PRAGMA = 12;
    public static final int SC_REQ_REFERER = 13;
    public static final int SC_REQ_USER_AGENT = 14;

    // Translates integer codes to request header names
    private static final String[] headerTransArray =
            { "accept", "accept-charset", "accept-encoding", "accept-language", "authorization", "connection",
                    "content-type", "content-length", "cookie", "cookie2", "host", "pragma", "referer", "user-agent" };

    /**
     * Converts an AJP coded HTTP request header to the header name.
     *
     * @param code the coded value
     *
     * @return the string value of the header name
     */
    public static String getHeaderForCode(final int code) {
        return headerTransArray[code];
    }

    // Translates integer codes to response header names
    private static final String[] responseTransArray = { "Content-Type", "Content-Language", "Content-Length", "Date",
            "Last-Modified", "Location", "Set-Cookie", "Set-Cookie2", "Servlet-Engine", "Status", "WWW-Authenticate" };

    /**
     * Converts an AJP coded response header name to the HTTP response header name.
     *
     * @param code the coded value
     *
     * @return the string value of the header
     */
    public static String getResponseHeaderForCode(final int code) {
        return responseTransArray[code];
    }

    private static final Map<String,Integer> responseTransMap = new HashMap<>(20);

    static {
        try {
            for (int i = 0; i < SC_RESP_AJP13_MAX; i++) {
                responseTransMap.put(getResponseHeaderForCode(i), Integer.valueOf(0xA001 + i));
            }
        } catch (Exception e) {
            // Do nothing
        }
    }

    public static int getResponseAjpIndex(String header) {
        Integer i = responseTransMap.get(header);
        if (i == null) {
            return 0;
        } else {
            return i.intValue();
        }
    }
}
