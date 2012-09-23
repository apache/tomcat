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
package org.apache.catalina.valves;


/**
 * Manifest constants for the <code>org.apache.catalina.valves</code>
 * package.
 *
 * @author Craig R. McClanahan
 */

public final class Constants {

    public static final String Package = "org.apache.catalina.valves";

    // Constants for the AccessLogValve class
    public static final class AccessLog {
        public static final String COMMON_ALIAS = "common";
        public static final String COMMON_PATTERN = "%h %l %u %t \"%r\" %s %b";
        public static final String COMBINED_ALIAS = "combined";
        public static final String COMBINED_PATTERN = "%h %l %u %t \"%r\" %s %b \"%{Referer}i\" \"%{User-Agent}i\"";
    }

    public static final char[] SC_CONTINUE_CHAR = new char[] {'1', '0', '0'};
    public static final char[] SC_SWITCHING_PROTOCOLS_CHAR = new char[] {'1', '0', '1'};
    public static final char[] SC_OK_CHAR = new char[] {'2', '0', '0'};
    public static final char[] SC_CREATED_CHAR = new char[] {'2', '0', '1'};
    public static final char[] SC_ACCEPTED_CHAR = new char[] {'2', '0', '2'};
    public static final char[] SC_NON_AUTHORITATIVE_INFORMATION_CHAR = new char[] {'2', '0', '3'};
    public static final char[] SC_NO_CONTENT_CHAR = new char[] {'2', '0', '4'};
    public static final char[] SC_RESET_CONTENT_CHAR = new char[] {'2', '0', '5'};
    public static final char[] SC_PARTIAL_CONTENT_CHAR = new char[] {'2', '0', '6'};
    public static final char[] SC_MULTIPLE_CHOICES_CHAR = new char[] {'3', '0', '0'};
    public static final char[] SC_MOVED_PERMANENTLY_CHAR = new char[] {'3', '0', '1'};
    public static final char[] SC_MOVED_TEMPORARILY_CHAR = new char[] {'3', '0', '2'};
    public static final char[] SC_SEE_OTHER_CHAR = new char[] {'3', '0', '3'};
    public static final char[] SC_NOT_MODIFIED_CHAR = new char[] {'3', '0', '4'};
    public static final char[] SC_USE_PROXY_CHAR = new char[] {'3', '0', '5'};
    public static final char[] SC_TEMPORARY_REDIRECT_CHAR = new char[] {'3', '0', '7'};
    public static final char[] SC_BAD_REQUEST_CHAR = new char[] {'4', '0', '0'};
    public static final char[] SC_UNAUTHORIZED_CHAR = new char[] {'4', '0', '1'};
    public static final char[] SC_PAYMENT_REQUIRED_CHAR = new char[] {'4', '0', '2'};
    public static final char[] SC_FORBIDDEN_CHAR = new char[] {'4', '0', '3'};
    public static final char[] SC_NOT_FOUND_CHAR = new char[] {'4', '0', '4'};
    public static final char[] SC_METHOD_NOT_ALLOWED_CHAR = new char[] {'4', '0', '5'};
    public static final char[] SC_NOT_ACCEPTABLE_CHAR = new char[] {'4', '0', '6'};
    public static final char[] SC_PROXY_AUTHENTICATION_REQUIRED_CHAR = new char[] {'4', '0', '7'};
    public static final char[] SC_REQUEST_TIMEOUT_CHAR = new char[] {'4', '0', '8'};
    public static final char[] SC_CONFLICT_CHAR = new char[] {'4', '0', '9'};
    public static final char[] SC_GONE_CHAR = new char[] {'4', '1', '0'};
    public static final char[] SC_LENGTH_REQUIRED_CHAR = new char[] {'4', '1', '1'};
    public static final char[] SC_PRECONDITION_FAILED_CHAR = new char[] {'4', '1', '2'};
    public static final char[] SC_REQUEST_ENTITY_TOO_LARGE_CHAR = new char[] {'4', '1', '3'};
    public static final char[] SC_REQUEST_URI_TOO_LONG_CHAR = new char[] {'4', '1', '4'};
    public static final char[] SC_UNSUPPORTED_MEDIA_TYPE_CHAR = new char[] {'4', '1', '5'};
    public static final char[] SC_REQUESTED_RANGE_NOT_SATISFIABLE_CHAR = new char[] {'4', '1', '6'};
    public static final char[] SC_EXPECTATION_FAILED_CHAR = new char[] {'4', '1', '7'};
    public static final char[] SC_INTERNAL_SERVER_ERROR_CHAR = new char[] {'5', '0', '0'};
    public static final char[] SC_NOT_IMPLEMENTED_CHAR = new char[] {'5', '0', '1'};
    public static final char[] SC_BAD_GATEWAY_CHAR = new char[] {'5', '0', '2'};
    public static final char[] SC_SERVICE_UNAVAILABLE_CHAR = new char[] {'5', '0', '3'};
    public static final char[] SC_GATEWAY_TIMEOUT_CHAR = new char[] {'5', '0', '4'};
    public static final char[] SC_HTTP_VERSION_NOT_SUPPORTED_CHAR = new char[] {'5', '0', '5'};
}
