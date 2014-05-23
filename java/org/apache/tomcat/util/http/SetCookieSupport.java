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

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.BitSet;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.servlet.http.Cookie;

/**
 * Support class for generating Set-Cookie header values.
 */
public class SetCookieSupport {
    /**
     * If set to false, we don't use the IE6/7 Max-Age/Expires work around.
     * Default is usually true. If STRICT_SERVLET_COMPLIANCE==true then default
     * is false. Explicitly setting always takes priority.
     */
    private static final boolean ALWAYS_ADD_EXPIRES;
    static {
        String alwaysAddExpires = System.getProperty(
                "org.apache.tomcat.util.http.ServerCookie.ALWAYS_ADD_EXPIRES");
        if (alwaysAddExpires != null) {
            ALWAYS_ADD_EXPIRES = Boolean.valueOf(alwaysAddExpires).booleanValue();
        } else {
            ALWAYS_ADD_EXPIRES = !Boolean.getBoolean("org.apache.catalina.STRICT_SERVLET_COMPLIANCE");
        }
    }

    private static final BitSet ALLOWED_WITHOUT_QUOTES;
    static {
        boolean allowSeparatorsInV0 = Boolean.getBoolean(
                "org.apache.tomcat.util.http.ServerCookie.ALLOW_HTTP_SEPARATORS_IN_V0");
        String separators;
        if (allowSeparatorsInV0) {
            // comma, semi-colon and space as defined by netscape
            separators = ",; ";
        } else {
            // separators as defined by RFC2616
            separators = "()<>@,;:\\\"/[]?={} \t";
        }

        // all CHARs except CTLs or separators are allowed without quoting
        ALLOWED_WITHOUT_QUOTES = new BitSet(128);
        ALLOWED_WITHOUT_QUOTES.set(0x20, 0x7f);
        for (char ch : separators.toCharArray()) {
            ALLOWED_WITHOUT_QUOTES.clear(ch);
        }

        /**
         * Some browsers (e.g. IE6 and IE7) do not handle quoted Path values even
         * when Version is set to 1. To allow for this, we support a property
         * FWD_SLASH_IS_SEPARATOR which, when false, means a '/' character will not
         * be treated as a separator, potentially avoiding quoting and the ensuing
         * side effect of having the cookie upgraded to version 1.
         *
         * For now, we apply this rule globally rather than just to the Path attribute.
         */
        if (!allowSeparatorsInV0) {
            boolean allowSlash;
            String prop = System.getProperty(
                    "org.apache.tomcat.util.http.ServerCookie.FWD_SLASH_IS_SEPARATOR");
            if (prop != null) {
                allowSlash = !Boolean.parseBoolean(prop);
            } else {
                allowSlash = !Boolean.getBoolean("org.apache.catalina.STRICT_SERVLET_COMPLIANCE");
            }
            if (allowSlash) {
                ALLOWED_WITHOUT_QUOTES.set('/');
            }
        }
    }

    // Other fields
    private static final String OLD_COOKIE_PATTERN = "EEE, dd-MMM-yyyy HH:mm:ss z";
    private static final ThreadLocal<DateFormat> OLD_COOKIE_FORMAT =
        new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
            DateFormat df =
                new SimpleDateFormat(OLD_COOKIE_PATTERN, Locale.US);
            df.setTimeZone(TimeZone.getTimeZone("GMT"));
            return df;
        }
    };
    private static final String ancientDate;

    static {
        ancientDate = OLD_COOKIE_FORMAT.get().format(new Date(10000));
    }

    public static String generateHeader(Cookie cookie) {
        /*
         * The spec allows some latitude on when to send the version attribute
         * with a Set-Cookie header. To be nice to clients, we'll make sure the
         * version attribute is first. That means checking the various things
         * that can cause us to switch to a v1 cookie first.
         *
         * Note that by checking for tokens we will also throw an exception if a
         * control character is encountered.
         */
        int version = cookie.getVersion();
        String value = cookie.getValue();
        String path = cookie.getPath();
        String domain = cookie.getDomain();
        String comment = cookie.getComment();

        if (version == 0) {
            // Check for the things that require a v1 cookie
            if (needsQuotes(value) || comment != null || needsQuotes(path) || needsQuotes(domain)) {
                version = 1;
            }
        }

        // Now build the cookie header
        StringBuffer buf = new StringBuffer(); // can't use StringBuilder due to DateFormat

        // Just use the name supplied in the Cookie
        buf.append(cookie.getName());
        buf.append("=");

        // Value
        maybeQuote(buf, value);

        // Add version 1 specific information
        if (version == 1) {
            // Version=1 ... required
            buf.append ("; Version=1");

            // Comment=comment
            if (comment != null) {
                buf.append ("; Comment=");
                maybeQuote(buf, comment);
            }
        }

        // Add domain information, if present
        if (domain != null) {
            buf.append("; Domain=");
            maybeQuote(buf, domain);
        }

        // Max-Age=secs ... or use old "Expires" format
        int maxAge = cookie.getMaxAge();
        if (maxAge >= 0) {
            if (version > 0) {
                buf.append ("; Max-Age=");
                buf.append (maxAge);
            }
            // IE6, IE7 and possibly other browsers don't understand Max-Age.
            // They do understand Expires, even with V1 cookies!
            if (version == 0 || ALWAYS_ADD_EXPIRES) {
                // Wdy, DD-Mon-YY HH:MM:SS GMT ( Expires Netscape format )
                buf.append ("; Expires=");
                // To expire immediately we need to set the time in past
                if (maxAge == 0) {
                    buf.append( ancientDate );
                } else {
                    OLD_COOKIE_FORMAT.get().format(
                            new Date(System.currentTimeMillis() + maxAge * 1000L),
                            buf,
                            new FieldPosition(0));
                }
            }
        }

        // Path=path
        if (path!=null) {
            buf.append ("; Path=");
            maybeQuote(buf, path);
        }

        // Secure
        if (cookie.getSecure()) {
          buf.append ("; Secure");
        }

        // HttpOnly
        if (cookie.isHttpOnly()) {
            buf.append("; HttpOnly");
        }
        return buf.toString();
    }

    private static void maybeQuote(StringBuffer buf, String value) {
        if (value == null || value.length() == 0) {
            buf.append("\"\"");
        } else if (alreadyQuoted(value)) {
            buf.append('"');
            escapeDoubleQuotes(buf, value,1,value.length()-1);
            buf.append('"');
        } else if (needsQuotes(value)) {
            buf.append('"');
            escapeDoubleQuotes(buf, value,0,value.length());
            buf.append('"');
        } else {
            buf.append(value);
        }
    }

    private static void escapeDoubleQuotes(StringBuffer b, String s, int beginIndex, int endIndex) {
        if (s.indexOf('"') == -1 && s.indexOf('\\') == -1) {
            b.append(s);
            return;
        }

        for (int i = beginIndex; i < endIndex; i++) {
            char c = s.charAt(i);
            if (c == '\\' ) {
                b.append('\\').append('\\');
            } else if (c == '"') {
                b.append('\\').append('"');
            } else {
                b.append(c);
            }
        }
    }

    private static boolean needsQuotes(String value) {
        if (value == null) {
            return false;
        }

        int i = 0;
        int len = value.length();

        if (alreadyQuoted(value)) {
            i++;
            len--;
        }

        for (; i < len; i++) {
            char c = value.charAt(i);
            if ((c < 0x20 && c != '\t') || c >= 0x7f) {
                throw new IllegalArgumentException(
                        "Control character in cookie value or attribute.");
            }
            if (!ALLOWED_WITHOUT_QUOTES.get(c)) {
                return true;
            }
        }
        return false;
    }


    private static boolean alreadyQuoted (String value) {
        return value.length() >= 2 &&
                value.charAt(0) == '\"' &&
                value.charAt(value.length() - 1) == '\"';
    }
}
