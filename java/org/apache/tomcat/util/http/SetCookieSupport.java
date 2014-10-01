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
package org.apache.tomcat.util.http;

import javax.servlet.http.Cookie;

/**
 * Support class for generating Set-Cookie header values.
 *
 * @deprecated  Will be removed in Tomcat 9.
 */
@Deprecated
public class SetCookieSupport {
    /**
     * If set to false, we don't use the IE6/7 Max-Age/Expires work around.
     * Default is usually true. If STRICT_SERVLET_COMPLIANCE==true then default
     * is false. Explicitly setting always takes priority.
     */
    static final boolean ALWAYS_ADD_EXPIRES;
    static {
        String alwaysAddExpires = System.getProperty(
                "org.apache.tomcat.util.http.ServerCookie.ALWAYS_ADD_EXPIRES");
        if (alwaysAddExpires != null) {
            ALWAYS_ADD_EXPIRES = Boolean.valueOf(alwaysAddExpires).booleanValue();
        } else {
            ALWAYS_ADD_EXPIRES = !Boolean.getBoolean("org.apache.catalina.STRICT_SERVLET_COMPLIANCE");
        }
    }

    private static final CookieProcessor cookieProcessor = new LegacyCookieProcessor();

    public static String generateHeader(Cookie cookie) {
        return cookieProcessor.generateHeader(cookie);
    }
}
