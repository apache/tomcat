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
import java.nio.charset.StandardCharsets;
import java.text.FieldPosition;
import java.util.BitSet;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.parser.Cookie;
import org.apache.tomcat.util.res.StringManager;

public class Rfc6265CookieProcessor extends CookieProcessorBase {

    private static final Log log = LogFactory.getLog(Rfc6265CookieProcessor.class);

    private static final StringManager sm =
            StringManager.getManager(Rfc6265CookieProcessor.class.getPackage().getName());

    private static final BitSet domainValid = new BitSet(128);

    static {
        for (char c = '0'; c <= '9'; c++) {
            domainValid.set(c);
        }
        for (char c = 'a'; c <= 'z'; c++) {
            domainValid.set(c);
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            domainValid.set(c);
        }
        domainValid.set('.');
        domainValid.set('-');
    }


    @Override
    public Charset getCharset() {
        return StandardCharsets.UTF_8;
    }


    @Override
    public void parseCookieHeader(MimeHeaders headers,
            ServerCookies serverCookies) {

        if (headers == null) {
            // nothing to process
            return;
        }

        // process each "cookie" header
        int pos = headers.findHeader("Cookie", 0);
        while (pos >= 0) {
            MessageBytes cookieValue = headers.getValue(pos);

            if (cookieValue != null && !cookieValue.isNull() ) {
                if (cookieValue.getType() != MessageBytes.T_BYTES ) {
                    if (log.isDebugEnabled()) {
                        Exception e = new Exception();
                        // TODO: Review this in light of HTTP/2
                        log.debug("Cookies: Parsing cookie as String. Expected bytes.", e);
                    }
                    cookieValue.toBytes();
                }
                if (log.isDebugEnabled()) {
                    log.debug("Cookies: Parsing b[]: " + cookieValue.toString());
                }
                ByteChunk bc = cookieValue.getByteChunk();

                Cookie.parseCookie(bc.getBytes(), bc.getOffset(), bc.getLength(),
                        serverCookies);
            }

            // search from the next position
            pos = headers.findHeader("Cookie", ++pos);
        }
    }


    @Override
    public String generateHeader(javax.servlet.http.Cookie cookie) {
        return generateHeader(cookie, null);
    }


    @Override
    public String generateHeader(javax.servlet.http.Cookie cookie, HttpServletRequest request) {

        // Can't use StringBuilder due to DateFormat
        StringBuffer header = new StringBuffer();

        // TODO: Name validation takes place in Cookie and cannot be configured
        //       per Context. Moving it to here would allow per Context config
        //       but delay validation until the header is generated. However,
        //       the spec requires an IllegalArgumentException on Cookie
        //       generation.
        header.append(cookie.getName());
        header.append('=');
        String value = cookie.getValue();
        if (value != null && value.length() > 0) {
            validateCookieValue(value);
            header.append(value);
        }

        // RFC 6265 prefers Max-Age to Expires but... (see below)
        int maxAge = cookie.getMaxAge();
        if (maxAge > -1) {
            // Negative Max-Age is equivalent to no Max-Age
            header.append("; Max-Age=");
            header.append(maxAge);

            // Microsoft IE and Microsoft Edge don't understand Max-Age so send
            // expires as well. Without this, persistent cookies fail with those
            // browsers. See http://tomcat.markmail.org/thread/g6sipbofsjossacn

            // Wdy, DD-Mon-YY HH:MM:SS GMT ( Expires Netscape format )
            header.append ("; Expires=");
            // To expire immediately we need to set the time in past
            if (maxAge == 0) {
                header.append(ANCIENT_DATE);
            } else {
                COOKIE_DATE_FORMAT.get().format(
                        new Date(System.currentTimeMillis() + maxAge * 1000L),
                        header,
                        new FieldPosition(0));
            }
        }

        String domain = cookie.getDomain();
        if (domain != null && domain.length() > 0) {
            validateDomain(domain);
            header.append("; Domain=");
            header.append(domain);
        }

        String path = cookie.getPath();
        if (path != null && path.length() > 0) {
            validatePath(path);
            header.append("; Path=");
            header.append(path);
        }

        if (cookie.getSecure()) {
            header.append("; Secure");
        }

        if (cookie.isHttpOnly()) {
            header.append("; HttpOnly");
        }

        SameSiteCookies sameSiteCookiesValue = getSameSiteCookies();

        if (!sameSiteCookiesValue.equals(SameSiteCookies.UNSET)) {
            header.append("; SameSite=");
            header.append(sameSiteCookiesValue.getValue());
        }

        return header.toString();
    }


    private void validateCookieValue(String value) {
        int start = 0;
        int end = value.length();

        if (end > 1 && value.charAt(0) == '"' && value.charAt(end - 1) == '"') {
            start = 1;
            end--;
        }

        char[] chars = value.toCharArray();
        for (int i = start; i < end; i++) {
            char c = chars[i];
            if (c < 0x21 || c == 0x22 || c == 0x2c || c == 0x3b || c == 0x5c || c == 0x7f) {
                throw new IllegalArgumentException(sm.getString(
                        "rfc6265CookieProcessor.invalidCharInValue", Integer.toString(c)));
            }
        }
    }


    private void validateDomain(String domain) {
        int i = 0;
        int prev = -1;
        int cur = -1;
        char[] chars = domain.toCharArray();
        while (i < chars.length) {
            prev = cur;
            cur = chars[i];
            if (!domainValid.get(cur)) {
                throw new IllegalArgumentException(sm.getString(
                        "rfc6265CookieProcessor.invalidDomain", domain));
            }
            // labels must start with a letter or number
            if ((prev == '.' || prev == -1) && (cur == '.' || cur == '-')) {
                throw new IllegalArgumentException(sm.getString(
                        "rfc6265CookieProcessor.invalidDomain", domain));
            }
            // labels must end with a letter or number
            if (prev == '-' && cur == '.') {
                throw new IllegalArgumentException(sm.getString(
                        "rfc6265CookieProcessor.invalidDomain", domain));
            }
            i++;
        }
        // domain must end with a label
        if (cur == '.' || cur == '-') {
            throw new IllegalArgumentException(sm.getString(
                    "rfc6265CookieProcessor.invalidDomain", domain));
        }
    }


    private void validatePath(String path) {
        char[] chars = path.toCharArray();

        for (char ch : chars) {
            if (ch < 0x20 || ch > 0x7E || ch == ';') {
                throw new IllegalArgumentException(sm.getString(
                        "rfc6265CookieProcessor.invalidPath", path));
            }
        }
    }
}
