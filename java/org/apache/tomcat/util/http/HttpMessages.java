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

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tomcat.util.res.StringManager;

/**
 * Handle (internationalized) HTTP messages.
 *
 * @author James Duncan Davidson [duncan@eng.sun.com]
 * @author James Todd [gonzo@eng.sun.com]
 * @author Jason Hunter [jch@eng.sun.com]
 * @author Harish Prabandham
 * @author costin@eng.sun.com
 */
public class HttpMessages {

    private static final Map<Locale,HttpMessages> instances =
            new ConcurrentHashMap<>();

    private static final HttpMessages DEFAULT = new HttpMessages(
            StringManager.getManager("org.apache.tomcat.util.http.res",
                    Locale.getDefault()));


    // XXX move message resources in this package
    private final StringManager sm;

    private String st_200 = null;
    private String st_302 = null;
    private String st_400 = null;
    private String st_404 = null;
    private String st_500 = null;

    private HttpMessages(StringManager sm) {
        this.sm = sm;
    }


    /**
     * Get the status string associated with a status code. Common messages are
     * cached.
     *
     * @param status The HTTP status code to retrieve the message for
     *
     * @return The HTTP status string that conforms to the requirements of the
     *         HTTP specification
     */
    public String getMessage(int status) {
        // method from Response.

        // Does HTTP requires/allow international messages or
        // are pre-defined? The user doesn't see them most of the time
        switch( status ) {
        case 200:
            if(st_200 == null ) {
                st_200 = sm.getString("sc.200");
            }
            return st_200;
        case 302:
            if(st_302 == null ) {
                st_302 = sm.getString("sc.302");
            }
            return st_302;
        case 400:
            if(st_400 == null ) {
                st_400 = sm.getString("sc.400");
            }
            return st_400;
        case 404:
            if(st_404 == null ) {
                st_404 = sm.getString("sc.404");
            }
            return st_404;
        case 500:
            if (st_500 == null) {
                st_500 = sm.getString("sc.500");
            }
            return st_500;
        }
        return sm.getString("sc."+ status);
    }


    public static HttpMessages getInstance(Locale locale) {
        HttpMessages result = instances.get(locale);
        if (result == null) {
            StringManager sm = StringManager.getManager(
                    "org.apache.tomcat.util.http.res", locale);
            if (Locale.getDefault().equals(sm.getLocale())) {
                result = DEFAULT;
            } else {
                result = new HttpMessages(sm);
            }
            instances.put(locale, result);
        }
        return result;
    }


    /**
     * Is the provided message safe to use in an HTTP header. Safe messages must
     * meet the requirements of RFC2616 - i.e. must consist only of TEXT.
     *
     * @param msg   The message to test
     * @return      <code>true</code> if the message is safe to use in an HTTP
     *              header else <code>false</code>
     */
    public static boolean isSafeInHttpHeader(String msg) {
        // Nulls are fine. It is up to the calling code to address any NPE
        // concerns
        if (msg == null) {
            return true;
        }

        // Reason-Phrase is defined as *<TEXT, excluding CR, LF>
        // TEXT is defined as any OCTET except CTLs, but including LWS
        // OCTET is defined as an 8-bit sequence of data
        // CTL is defined as octets 0-31 and 127
        // LWS, if we exclude CR LF pairs, is defined as SP or HT (32, 9)
        final int len = msg.length();
        for (int i = 0; i < len; i++) {
            char c = msg.charAt(i);
            if (32 <= c && c <= 126 || 128 <= c && c <= 255 || c == 9) {
                continue;
            }
            return false;
        }

        return true;
    }
}