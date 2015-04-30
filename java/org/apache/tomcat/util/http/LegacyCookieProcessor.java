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
import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.BitSet;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.servlet.http.Cookie;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.log.UserDataHelper;
import org.apache.tomcat.util.res.StringManager;

/**
 * The legacy (up to early Tomcat 8 releases) cookie parser based on RFC6265,
 * RFC2109 and RFC2616.
 *
 * This class is not thread-safe.
 *
 * @author Costin Manolache
 * @author kevin seguin
 */
public final class LegacyCookieProcessor implements CookieProcessor {

    private static final Log log = LogFactory.getLog(LegacyCookieProcessor.class);

    private static final UserDataHelper userDataLog = new UserDataHelper(log);

    private static final StringManager sm =
            StringManager.getManager("org.apache.tomcat.util.http");

    private static final char[] V0_SEPARATORS = {',', ';', ' ', '\t'};
    private static final BitSet V0_SEPARATOR_FLAGS = new BitSet(128);

    // Excludes '/' since configuration controls whether or not to treat '/' as
    // a separator
    private static final char[] HTTP_SEPARATORS = new char[] {
            '\t', ' ', '\"', '(', ')', ',', ':', ';', '<', '=', '>', '?', '@',
            '[', '\\', ']', '{', '}' };

    private static final String COOKIE_DATE_PATTERN = "EEE, dd-MMM-yyyy HH:mm:ss z";
    private static final ThreadLocal<DateFormat> COOKIE_DATE_FORMAT =
        new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
            DateFormat df =
                new SimpleDateFormat(COOKIE_DATE_PATTERN, Locale.US);
            df.setTimeZone(TimeZone.getTimeZone("GMT"));
            return df;
        }
    };

    private static final String ANCIENT_DATE;

    static {
        for (char c : V0_SEPARATORS) {
            V0_SEPARATOR_FLAGS.set(c);
        }

        ANCIENT_DATE = COOKIE_DATE_FORMAT.get().format(new Date(10000));
    }


    @SuppressWarnings("deprecation") // Default to false when deprecated code is removed
    private boolean allowEqualsInValue = CookieSupport.ALLOW_EQUALS_IN_VALUE;

    @SuppressWarnings("deprecation") // Default to false when deprecated code is removed
    private boolean allowNameOnly = CookieSupport.ALLOW_NAME_ONLY;

    @SuppressWarnings("deprecation") // Default to false when deprecated code is removed
    private boolean allowHttpSepsInV0 = CookieSupport.ALLOW_HTTP_SEPARATORS_IN_V0;

    @SuppressWarnings("deprecation") // Default to STRICT_SERVLET_COMPLIANCE
                                     // when deprecated code is removed
    private boolean presserveCookieHeader = CookieSupport.PRESERVE_COOKIE_HEADER;

    @SuppressWarnings("deprecation") // Default to !STRICT_SERVLET_COMPLIANCE
                                     // when deprecated code is removed
    private boolean alwaysAddExpires = SetCookieSupport.ALWAYS_ADD_EXPIRES;

    private final BitSet httpSeparatorFlags = new BitSet(128);

    private final BitSet allowedWithoutQuotes = new BitSet(128);


    public LegacyCookieProcessor() {
        // BitSet elements will default to false
        for (char c : HTTP_SEPARATORS) {
            httpSeparatorFlags.set(c);
        }
        @SuppressWarnings("deprecation") // Default to STRICT_SERVLET_COMPLIANCE
                                         // when deprecated code is removed
        boolean b = CookieSupport.FWD_SLASH_IS_SEPARATOR;
        if (b) {
            httpSeparatorFlags.set('/');
        }

        String separators;
        if (getAllowHttpSepsInV0()) {
            // comma, semi-colon and space as defined by netscape
            separators = ",; ";
        } else {
            // separators as defined by RFC2616
            separators = "()<>@,;:\\\"/[]?={} \t";
        }

        // all CHARs except CTLs or separators are allowed without quoting
        allowedWithoutQuotes.set(0x20, 0x7f);
        for (char ch : separators.toCharArray()) {
            allowedWithoutQuotes.clear(ch);
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
        if (!getAllowHttpSepsInV0() && !getForwardSlashIsSeparator()) {
            allowedWithoutQuotes.set('/');
        }
    }


    public boolean getAllowEqualsInValue() {
        return allowEqualsInValue;
    }


    public void setAllowEqualsInValue(boolean allowEqualsInValue) {
        this.allowEqualsInValue = allowEqualsInValue;
    }


    public boolean getAllowNameOnly() {
        return allowNameOnly;
    }


    public void setAllowNameOnly(boolean allowNameOnly) {
        this.allowNameOnly = allowNameOnly;
    }


    public boolean getAllowHttpSepsInV0() {
        return allowHttpSepsInV0;
    }


    public void setAllowHttpSepsInV0(boolean allowHttpSepsInV0) {
        this.allowHttpSepsInV0 = allowHttpSepsInV0;
        // HTTP separators less comma, semicolon and space since the Netscape
        // spec defines those as separators too.
        // '/' is also treated as a special case
        char[] seps = "()<>@:\\\"[]?={}\t".toCharArray();
        for (char sep : seps) {
            if (allowHttpSepsInV0) {
                allowedWithoutQuotes.set(sep);
            } else {
                allowedWithoutQuotes.clear(sep);
            }
        }
        if (getForwardSlashIsSeparator() && !allowHttpSepsInV0) {
            allowedWithoutQuotes.set('/');
        } else {
            allowedWithoutQuotes.clear('/');
        }
    }


    public boolean getPreserveCookieHeader() {
        return presserveCookieHeader;
    }


    public void setPreserveCookieHeader(boolean presserveCookieHeader) {
        this.presserveCookieHeader = presserveCookieHeader;
    }


    public boolean getForwardSlashIsSeparator() {
        return httpSeparatorFlags.get('/');
    }


    public void setForwardSlashIsSeparator(boolean forwardSlashIsSeparator) {
        if (forwardSlashIsSeparator) {
            httpSeparatorFlags.set('/');
        } else {
            httpSeparatorFlags.clear('/');
        }
        if (forwardSlashIsSeparator && !getAllowHttpSepsInV0()) {
            allowedWithoutQuotes.set('/');
        } else {
            allowedWithoutQuotes.clear('/');
        }
    }


    public boolean getAlwaysAddExpires() {
        return alwaysAddExpires;
    }


    public void setAlwaysAddExpires(boolean alwaysAddExpires) {
        this.alwaysAddExpires = alwaysAddExpires;
    }


    @Override
    public Charset getCharset() {
        return StandardCharsets.ISO_8859_1;
    }


    @Override
    public void parseCookieHeader(MimeHeaders headers, ServerCookies serverCookies) {

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
                    Exception e = new Exception();
                    log.warn("Cookies: Parsing cookie as String. Expected bytes.", e);
                    cookieValue.toBytes();
                }
                if (log.isDebugEnabled()) {
                    log.debug("Cookies: Parsing b[]: " + cookieValue.toString());
                }
                ByteChunk bc = cookieValue.getByteChunk();
                if (getPreserveCookieHeader()) {
                    int len = bc.getLength();
                    if (len > 0) {
                        byte[] buf = new byte[len];
                        System.arraycopy(bc.getBytes(), bc.getOffset(), buf, 0, len);
                        processCookieHeader(buf, 0, len, serverCookies);
                    }
                } else {
                    processCookieHeader(bc.getBytes(), bc.getOffset(), bc.getLength(),
                            serverCookies);
                }
            }

            // search from the next position
            pos = headers.findHeader("Cookie", ++pos);
        }
    }


    @Override
    public String generateHeader(Cookie cookie) {
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
            if (needsQuotes(value, 0) || comment != null || needsQuotes(path, 0) || needsQuotes(domain, 0)) {
                version = 1;
            }
        }

        // Now build the cookie header
        StringBuffer buf = new StringBuffer(); // can't use StringBuilder due to DateFormat

        // Just use the name supplied in the Cookie
        buf.append(cookie.getName());
        buf.append("=");

        // Value
        maybeQuote(buf, value, version);

        // Add version 1 specific information
        if (version == 1) {
            // Version=1 ... required
            buf.append ("; Version=1");

            // Comment=comment
            if (comment != null) {
                buf.append ("; Comment=");
                maybeQuote(buf, comment, version);
            }
        }

        // Add domain information, if present
        if (domain != null) {
            buf.append("; Domain=");
            maybeQuote(buf, domain, version);
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
            if (version == 0 || getAlwaysAddExpires()) {
                // Wdy, DD-Mon-YY HH:MM:SS GMT ( Expires Netscape format )
                buf.append ("; Expires=");
                // To expire immediately we need to set the time in past
                if (maxAge == 0) {
                    buf.append( ANCIENT_DATE );
                } else {
                    COOKIE_DATE_FORMAT.get().format(
                            new Date(System.currentTimeMillis() + maxAge * 1000L),
                            buf,
                            new FieldPosition(0));
                }
            }
        }

        // Path=path
        if (path!=null) {
            buf.append ("; Path=");
            maybeQuote(buf, path, version);
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


    private void maybeQuote(StringBuffer buf, String value, int version) {
        if (value == null || value.length() == 0) {
            buf.append("\"\"");
        } else if (alreadyQuoted(value)) {
            buf.append('"');
            escapeDoubleQuotes(buf, value,1,value.length()-1);
            buf.append('"');
        } else if (needsQuotes(value, version)) {
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


    private boolean needsQuotes(String value, int version) {
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
            if (version == 0 && !allowedWithoutQuotes.get(c) ||
                    version == 1 && isHttpSeparator(c)) {
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


    /**
     * Parses a cookie header after the initial "Cookie:"
     * [WS][$]token[WS]=[WS](token|QV)[;|,]
     * RFC 2965 / RFC 2109
     * JVK
     */
    private final void processCookieHeader(byte bytes[], int off, int len,
            ServerCookies serverCookies) {

        if (len <= 0 || bytes == null) {
            return;
        }
        int end = off + len;
        int pos = off;
        int nameStart = 0;
        int nameEnd = 0;
        int valueStart = 0;
        int valueEnd = 0;
        int version = 0;
        ServerCookie sc = null;
        boolean isSpecial;
        boolean isQuoted;

        while (pos < end) {
            isSpecial = false;
            isQuoted = false;

            // Skip whitespace and non-token characters (separators)
            while (pos < end &&
                   (isHttpSeparator((char) bytes[pos]) &&
                           !getAllowHttpSepsInV0() ||
                    isV0Separator((char) bytes[pos]) ||
                    isWhiteSpace(bytes[pos])))
                {pos++; }

            if (pos >= end) {
                return;
            }

            // Detect Special cookies
            if (bytes[pos] == '$') {
                isSpecial = true;
                pos++;
            }

            // Get the cookie/attribute name. This must be a token
            valueEnd = valueStart = nameStart = pos;
            pos = nameEnd = getTokenEndPosition(bytes,pos,end,version,true);

            // Skip whitespace
            while (pos < end && isWhiteSpace(bytes[pos])) {pos++; }


            // Check for an '=' -- This could also be a name-only
            // cookie at the end of the cookie header, so if we
            // are past the end of the header, but we have a name
            // skip to the name-only part.
            if (pos < (end - 1) && bytes[pos] == '=') {

                // Skip whitespace
                do {
                    pos++;
                } while (pos < end && isWhiteSpace(bytes[pos]));

                if (pos >= end) {
                    return;
                }

                // Determine what type of value this is, quoted value,
                // token, name-only with an '=', or other (bad)
                switch (bytes[pos]) {
                case '"': // Quoted Value
                    isQuoted = true;
                    valueStart = pos + 1; // strip "
                    // getQuotedValue returns the position before
                    // at the last quote. This must be dealt with
                    // when the bytes are copied into the cookie
                    valueEnd = getQuotedValueEndPosition(bytes, valueStart, end);
                    // We need pos to advance
                    pos = valueEnd;
                    // Handles cases where the quoted value is
                    // unterminated and at the end of the header,
                    // e.g. [myname="value]
                    if (pos >= end) {
                        return;
                    }
                    break;
                case ';':
                case ',':
                    // Name-only cookie with an '=' after the name token
                    // This may not be RFC compliant
                    valueStart = valueEnd = -1;
                    // The position is OK (On a delimiter)
                    break;
                default:
                    if (version == 0 &&
                                isV0Separator((char)bytes[pos]) &&
                                getAllowHttpSepsInV0() ||
                            !isHttpSeparator((char)bytes[pos]) ||
                            bytes[pos] == '=') {
                        // Token
                        valueStart = pos;
                        // getToken returns the position at the delimiter
                        // or other non-token character
                        valueEnd = getTokenEndPosition(bytes, valueStart, end, version, false);
                        // We need pos to advance
                        pos = valueEnd;
                        // Edge case. If value starts with '=' but this is not
                        // allowed in a value make sure we treat this as no
                        // value being present
                        if (valueStart == valueEnd) {
                            valueStart = -1;
                            valueEnd = -1;
                        }
                    } else  {
                        // INVALID COOKIE, advance to next delimiter
                        // The starting character of the cookie value was
                        // not valid.
                        UserDataHelper.Mode logMode = userDataLog.getNextMode();
                        if (logMode != null) {
                            String message = sm.getString(
                                    "cookies.invalidCookieToken");
                            switch (logMode) {
                                case INFO_THEN_DEBUG:
                                    message += sm.getString(
                                            "cookies.fallToDebug");
                                    //$FALL-THROUGH$
                                case INFO:
                                    log.info(message);
                                    break;
                                case DEBUG:
                                    log.debug(message);
                            }
                        }
                        while (pos < end && bytes[pos] != ';' &&
                               bytes[pos] != ',')
                            {pos++; }
                        pos++;
                        // Make sure no special avpairs can be attributed to
                        // the previous cookie by setting the current cookie
                        // to null
                        sc = null;
                        continue;
                    }
                }
            } else {
                // Name only cookie
                valueStart = valueEnd = -1;
                pos = nameEnd;

            }

            // We should have an avpair or name-only cookie at this
            // point. Perform some basic checks to make sure we are
            // in a good state.

            // Skip whitespace
            while (pos < end && isWhiteSpace(bytes[pos])) {pos++; }


            // Make sure that after the cookie we have a separator. This
            // is only important if this is not the last cookie pair
            while (pos < end && bytes[pos] != ';' && bytes[pos] != ',') {
                pos++;
            }

            pos++;

            // All checks passed. Add the cookie, start with the
            // special avpairs first
            if (isSpecial) {
                isSpecial = false;
                // $Version must be the first avpair in the cookie header
                // (sc must be null)
                if (equals( "Version", bytes, nameStart, nameEnd) &&
                    sc == null) {
                    // Set version
                    if( bytes[valueStart] =='1' && valueEnd == (valueStart+1)) {
                        version=1;
                    } else {
                        // unknown version (Versioning is not very strict)
                    }
                    continue;
                }

                // We need an active cookie for Path/Port/etc.
                if (sc == null) {
                    continue;
                }

                // Domain is more common, so it goes first
                if (equals( "Domain", bytes, nameStart, nameEnd)) {
                    sc.getDomain().setBytes( bytes,
                                           valueStart,
                                           valueEnd-valueStart);
                    continue;
                }

                if (equals( "Path", bytes, nameStart, nameEnd)) {
                    sc.getPath().setBytes( bytes,
                                           valueStart,
                                           valueEnd-valueStart);
                    continue;
                }

                // v2 cookie attributes - skip them
                if (equals( "Port", bytes, nameStart, nameEnd)) {
                    continue;
                }
                if (equals( "CommentURL", bytes, nameStart, nameEnd)) {
                    continue;
                }

                // Unknown cookie, complain
                UserDataHelper.Mode logMode = userDataLog.getNextMode();
                if (logMode != null) {
                    String message = sm.getString("cookies.invalidSpecial");
                    switch (logMode) {
                        case INFO_THEN_DEBUG:
                            message += sm.getString("cookies.fallToDebug");
                            //$FALL-THROUGH$
                        case INFO:
                            log.info(message);
                            break;
                        case DEBUG:
                            log.debug(message);
                    }
                }
            } else { // Normal Cookie
                if (valueStart == -1 && !getAllowNameOnly()) {
                    // Skip name only cookies if not supported
                    continue;
                }

                sc = serverCookies.addCookie();
                sc.setVersion( version );
                sc.getName().setBytes( bytes, nameStart,
                                       nameEnd-nameStart);

                if (valueStart != -1) { // Normal AVPair
                    sc.getValue().setBytes( bytes, valueStart,
                            valueEnd-valueStart);
                    if (isQuoted) {
                        // We know this is a byte value so this is safe
                        unescapeDoubleQuotes(sc.getValue().getByteChunk());
                    }
                } else {
                    // Name Only
                    sc.getValue().setString("");
                }
                continue;
            }
        }
    }


    /**
     * Given the starting position of a token, this gets the end of the
     * token, with no separator characters in between.
     * JVK
     */
    private final int getTokenEndPosition(byte bytes[], int off, int end,
            int version, boolean isName){
        int pos = off;
        while (pos < end &&
                (!isHttpSeparator((char)bytes[pos]) ||
                 version == 0 && getAllowHttpSepsInV0() && bytes[pos] != '=' &&
                        !isV0Separator((char)bytes[pos]) ||
                 !isName && bytes[pos] == '=' && getAllowEqualsInValue())) {
            pos++;
        }

        if (pos > end) {
            return end;
        }
        return pos;
    }


    private boolean isHttpSeparator(final char c) {
        if (c < 0x20 || c >= 0x7f) {
            if (c != 0x09) {
                throw new IllegalArgumentException(
                        "Control character in cookie value or attribute.");
            }
        }

        return httpSeparatorFlags.get(c);
    }


    /**
     * Returns true if the byte is a separator as defined by V0 of the cookie
     * spec.
     */
    private static boolean isV0Separator(final char c) {
        if (c < 0x20 || c >= 0x7f) {
            if (c != 0x09) {
                throw new IllegalArgumentException(
                        "Control character in cookie value or attribute.");
            }
        }

        return V0_SEPARATOR_FLAGS.get(c);
    }


    /**
     * Given a starting position after an initial quote character, this gets
     * the position of the end quote. This escapes anything after a '\' char
     * JVK RFC 2616
     */
    private static final int getQuotedValueEndPosition(byte bytes[], int off, int end){
        int pos = off;
        while (pos < end) {
            if (bytes[pos] == '"') {
                return pos;
            } else if (bytes[pos] == '\\' && pos < (end - 1)) {
                pos+=2;
            } else {
                pos++;
            }
        }
        // Error, we have reached the end of the header w/o a end quote
        return end;
    }


    private static final boolean equals(String s, byte b[], int start, int end) {
        int blen = end-start;
        if (b == null || blen != s.length()) {
            return false;
        }
        int boff = start;
        for (int i = 0; i < blen; i++) {
            if (b[boff++] != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }


    /**
     * Returns true if the byte is a whitespace character as
     * defined in RFC2619
     * JVK
     */
    private static final boolean isWhiteSpace(final byte c) {
        // This switch statement is slightly slower
        // for my vm than the if statement.
        // Java(TM) 2 Runtime Environment, Standard Edition (build 1.5.0_07-164)
        /*
        switch (c) {
        case ' ':;
        case '\t':;
        case '\n':;
        case '\r':;
        case '\f':;
            return true;
        default:;
            return false;
        }
        */
        if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f') {
            return true;
        } else {
            return false;
        }
    }


    /**
     * Unescapes any double quotes in the given cookie value.
     *
     * @param bc The cookie value to modify
     */
    private static final void unescapeDoubleQuotes(ByteChunk bc) {

        if (bc == null || bc.getLength() == 0 || bc.indexOf('"', 0) == -1) {
            return;
        }

        int src = bc.getStart();
        int end = bc.getEnd();
        int dest = src;
        byte[] buffer = bc.getBuffer();

        while (src < end) {
            if (buffer[src] == '\\' && src < end && buffer[src+1]  == '"') {
                src++;
            }
            buffer[dest] = buffer[src];
            dest ++;
            src ++;
        }
        bc.setEnd(dest);
    }
}
