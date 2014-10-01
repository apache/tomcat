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
package org.apache.tomcat.util.http;

import java.nio.charset.Charset;

import javax.servlet.http.Cookie;

public interface CookieProcessor {

    /**
     * Parse the provided headers into server cookie objects.
     */
    void parseCookieHeader(MimeHeaders headers, ServerCookies serverCookies);

    /**
     * Generate the HTTP header value for the given Cookie.
     */
    String generateHeader(Cookie cookie);

    /**
     * The character set that will be used when converting between bytes and
     * characters when parsing and/or generating HTTP headers for cookies.
     */
    Charset getCharset();
}
