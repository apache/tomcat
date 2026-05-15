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

import org.apache.tomcat.util.http.Method;

/**
 * Constants.
 */
public final class Constants {

    /**
     * Prevents instantiation.
     */
    private Constants() {
    }

    /**
     * Default connection timeout value.
     */
    public static final int DEFAULT_CONNECTION_TIMEOUT = -1;

    // Prefix codes for message types from server to container

    /**
     * AJP13 forward request message prefix code.
     */
    public static final byte JK_AJP13_FORWARD_REQUEST = 2;

    /**
     * AJP13 shutdown message prefix code.
     */
    public static final byte JK_AJP13_SHUTDOWN = 7; // XXX Unused

    /**
     * AJP13 ping request message prefix code.
     */
    public static final byte JK_AJP13_PING_REQUEST = 8; // XXX Unused

    /**
     * AJP13 cping request message prefix code.
     */
    public static final byte JK_AJP13_CPING_REQUEST = 10;

    // Prefix codes for message types from container to server

    /**
     * AJP13 send body chunk message prefix code.
     */
    public static final byte JK_AJP13_SEND_BODY_CHUNK = 3;

    /**
     * AJP13 send headers message prefix code.
     */
    public static final byte JK_AJP13_SEND_HEADERS = 4;

    /**
     * AJP13 end response message prefix code.
     */
    public static final byte JK_AJP13_END_RESPONSE = 5;

    /**
     * AJP13 get body chunk message prefix code.
     */
    public static final byte JK_AJP13_GET_BODY_CHUNK = 6;

    /**
     * AJP13 cpong reply message prefix code.
     */
    public static final byte JK_AJP13_CPONG_REPLY = 9;

    // Integer codes for common response header strings

    /**
     * Response code for Content-Type header.
     */
    public static final int SC_RESP_CONTENT_TYPE = 0xA001;

    /**
     * Response code for Content-Language header.
     */
    public static final int SC_RESP_CONTENT_LANGUAGE = 0xA002;

    /**
     * Response code for Content-Length header.
     */
    public static final int SC_RESP_CONTENT_LENGTH = 0xA003;

    /**
     * Response code for Date header.
     */
    public static final int SC_RESP_DATE = 0xA004;

    /**
     * Response code for Last-Modified header.
     */
    public static final int SC_RESP_LAST_MODIFIED = 0xA005;

    /**
     * Response code for Location header.
     */
    public static final int SC_RESP_LOCATION = 0xA006;

    /**
     * Response code for Set-Cookie header.
     */
    public static final int SC_RESP_SET_COOKIE = 0xA007;

    /**
     * Response code for Set-Cookie2 header.
     */
    public static final int SC_RESP_SET_COOKIE2 = 0xA008;

    /**
     * Response code for Servlet-Engine header.
     */
    public static final int SC_RESP_SERVLET_ENGINE = 0xA009;

    /**
     * Response code for Status header.
     */
    public static final int SC_RESP_STATUS = 0xA00A;

    /**
     * Response code for WWW-Authenticate header.
     */
    public static final int SC_RESP_WWW_AUTHENTICATE = 0xA00B;

    /**
     * Maximum number of response header codes.
     */
    public static final int SC_RESP_AJP13_MAX = 11;

    // Integer codes for common (optional) request attribute names

    /**
     * Attribute code for context.
     */
    public static final byte SC_A_CONTEXT = 1; // XXX Unused

    /**
     * Attribute code for servlet path.
     */
    public static final byte SC_A_SERVLET_PATH = 2; // XXX Unused

    /**
     * Attribute code for remote user.
     */
    public static final byte SC_A_REMOTE_USER = 3;

    /**
     * Attribute code for authentication type.
     */
    public static final byte SC_A_AUTH_TYPE = 4;

    /**
     * Attribute code for query string.
     */
    public static final byte SC_A_QUERY_STRING = 5;

    /**
     * Attribute code for JVM route.
     */
    public static final byte SC_A_JVM_ROUTE = 6;

    /**
     * Attribute code for SSL certificate.
     */
    public static final byte SC_A_SSL_CERT = 7;

    /**
     * Attribute code for SSL cipher.
     */
    public static final byte SC_A_SSL_CIPHER = 8;

    /**
     * Attribute code for SSL session.
     */
    public static final byte SC_A_SSL_SESSION = 9;

    /**
     * Attribute code for SSL key size.
     */
    public static final byte SC_A_SSL_KEY_SIZE = 11;

    /**
     * Attribute code for secret.
     */
    public static final byte SC_A_SECRET = 12;

    /**
     * Attribute code for stored method.
     */
    public static final byte SC_A_STORED_METHOD = 13;

    // Used for attributes which are not in the list above

    /**
     * Attribute code for request attributes not in the predefined list.
     */
    public static final byte SC_A_REQ_ATTRIBUTE = 10;

    /**
     * AJP private request attribute for local address.
     */
    public static final String SC_A_REQ_LOCAL_ADDR = "AJP_LOCAL_ADDR";

    /**
     * AJP private request attribute for remote port.
     */
    public static final String SC_A_REQ_REMOTE_PORT = "AJP_REMOTE_PORT";

    /**
     * AJP private request attribute for SSL protocol.
     */
    public static final String SC_A_SSL_PROTOCOL = "AJP_SSL_PROTOCOL";

    // Terminates list of attributes

    /**
     * Terminator byte for the list of attributes.
     */
    public static final byte SC_A_ARE_DONE = (byte) 0xFF;

    /**
     * Default maximum total byte size for an AJP packet.
     */
    public static final int MAX_PACKET_SIZE = 8192;

    /**
     * Size of basic packet header.
     */
    public static final int H_SIZE = 4;

    /**
     * Size of the read header metadata.
     */
    public static final int READ_HEAD_LEN = 6;

    /**
     * Size of the send header metadata.
     */
    public static final int SEND_HEAD_LEN = 8;

    /**
     * Default maximum size of data that can be read in one packet.
     */
    public static final int MAX_READ_SIZE = MAX_PACKET_SIZE - READ_HEAD_LEN;

    /**
     * Default maximum size of data that can be sent in one packet.
     */
    public static final int MAX_SEND_SIZE = MAX_PACKET_SIZE - SEND_HEAD_LEN;

    // Translates integer codes to names of HTTP methods
    private static final String[] methodTransArray = { Method.OPTIONS, Method.GET, Method.HEAD, Method.POST, Method.PUT,
            Method.DELETE, Method.TRACE, Method.PROPFIND, Method.PROPPATCH, Method.MKCOL, Method.COPY, Method.MOVE,
            Method.LOCK, Method.UNLOCK, "ACL", "REPORT", "VERSION-CONTROL", "CHECKIN", "CHECKOUT", "UNCHECKOUT",
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

    /**
     * Stored method code value.
     */
    public static final int SC_M_JK_STORED = (byte) 0xFF;

    // id's for common request headers

    /**
     * Request header code for Accept.
     */
    public static final int SC_REQ_ACCEPT = 1;

    /**
     * Request header code for Accept-Charset.
     */
    public static final int SC_REQ_ACCEPT_CHARSET = 2;

    /**
     * Request header code for Accept-Encoding.
     */
    public static final int SC_REQ_ACCEPT_ENCODING = 3;

    /**
     * Request header code for Accept-Language.
     */
    public static final int SC_REQ_ACCEPT_LANGUAGE = 4;

    /**
     * Request header code for Authorization.
     */
    public static final int SC_REQ_AUTHORIZATION = 5;

    /**
     * Request header code for Connection.
     */
    public static final int SC_REQ_CONNECTION = 6;

    /**
     * Request header code for Content-Type.
     */
    public static final int SC_REQ_CONTENT_TYPE = 7;

    /**
     * Request header code for Content-Length.
     */
    public static final int SC_REQ_CONTENT_LENGTH = 8;

    /**
     * Request header code for Cookie.
     */
    public static final int SC_REQ_COOKIE = 9;

    /**
     * Request header code for Cookie2.
     */
    public static final int SC_REQ_COOKIE2 = 10;

    /**
     * Request header code for Host.
     */
    public static final int SC_REQ_HOST = 11;

    /**
     * Request header code for Pragma.
     */
    public static final int SC_REQ_PRAGMA = 12;

    /**
     * Request header code for Referer.
     */
    public static final int SC_REQ_REFERER = 13;

    /**
     * Request header code for User-Agent.
     */
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
        for (int i = 0; i < SC_RESP_AJP13_MAX; i++) {
            responseTransMap.put(getResponseHeaderForCode(i), Integer.valueOf(0xA001 + i));
        }
    }

    /**
     * Get the AJP response header index for the given header name.
     *
     * @param header The HTTP response header name
     *
     * @return the AJP integer code for the header, or 0 if not found
     */
    public static int getResponseAjpIndex(String header) {
        Integer i = responseTransMap.get(header);
        if (i == null) {
            return 0;
        } else {
            return i.intValue();
        }
    }
}
