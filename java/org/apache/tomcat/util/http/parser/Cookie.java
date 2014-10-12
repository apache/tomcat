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
package org.apache.tomcat.util.http.parser;

import java.nio.charset.StandardCharsets;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.http.ServerCookie;
import org.apache.tomcat.util.http.ServerCookies;
import org.apache.tomcat.util.log.UserDataHelper;
import org.apache.tomcat.util.res.StringManager;


/**
 * <p>Cookie header parser based on RFC6265 and RFC2109.</p>
 * <p>The parsing of cookies using RFC6265 is more relaxed that the
 * specification in the following ways:</p>
 * <ul>
 *   <li>Values 0x80 to 0xFF are permitted in cookie-octet to support the use of
 *       UTF-8 in cookie values as used by HTML 5.</li>
 *   <li>For cookies without a value, the '=' is not required after the name as
 *       some browsers do not sent it.</li>
 * </ul>
 * <p>The parsing of cookies using RFC2109 is more relaxed that the
 * specification in the following ways:</p>
 * <ul>
 *   <li>Values for the path attribute that contain a / character do not have to
 *       be quoted even though / is not permitted in a token.</li>
 * </ul>
 *
 * <p>Implementation note:<br>
 * This class has been carefully tuned to ensure that it has equal or better
 * performance than the original Netscape/RFC2109 cookie parser. Before
 * committing and changes, ensure that the TesterCookiePerformance unit test
 * continues to give results within 1% for the old and new parsers.</p>
 */
public class Cookie {

    private static final Log log = LogFactory.getLog(Cookie.class);
    private static final UserDataHelper invalidCookieVersionLog = new UserDataHelper(log);
    private static final UserDataHelper invalidCookieLog = new UserDataHelper(log);
    private static final StringManager sm =
            StringManager.getManager("org.apache.tomcat.util.http.parser");

    private static final boolean isCookieOctet[] = new boolean[256];
    private static final boolean isText[] = new boolean[256];
    private static final byte[] VERSION_BYTES = "$Version".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] PATH_BYTES = "$Path".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] DOMAIN_BYTES = "$Domain".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final byte TAB_BYTE = (byte) 0x09;
    private static final byte SPACE_BYTE = (byte) 0x20;
    private static final byte QUOTE_BYTE = (byte) 0x22;
    private static final byte COMMA_BYTE = (byte) 0x2C;
    private static final byte FORWARDSLASH_BYTE = (byte) 0x2F;
    private static final byte SEMICOLON_BYTE = (byte) 0x3B;
    private static final byte EQUALS_BYTE = (byte) 0x3D;
    private static final byte SLASH_BYTE = (byte) 0x5C;
    private static final byte DEL_BYTE = (byte) 0x7F;


    static {
        // %x21 / %x23-2B / %x2D-3A / %x3C-5B / %x5D-7E (RFC6265)
        // %x80 to %xFF                                 (UTF-8)
        for (int i = 0; i < 256; i++) {
            if (i < 0x21 || i == QUOTE_BYTE || i == COMMA_BYTE ||
                    i == SEMICOLON_BYTE || i == SLASH_BYTE || i == DEL_BYTE) {
                isCookieOctet[i] = false;
            } else {
                isCookieOctet[i] = true;
            }
        }
        for (int i = 0; i < 256; i++) {
            if (i < 0x21 || i == DEL_BYTE) {
                isText[i] = false;
            } else {
                isText[i] = true;
            }
        }
    }


    private Cookie() {
        // Hide default constructor
    }


    public static void parseCookie(byte[] bytes, int offset, int len,
            ServerCookies serverCookies) {

        // ByteBuffer is used throughout this parser as it allows the byte[]
        // and position information to be easily passed between parsing methods
        ByteBuffer bb = new ByteBuffer(bytes, offset, len);

        // Using RFC6265 parsing rules, check to see if the header starts with a
        // version marker. An RFC2109 version marker may be read using RFC6265
        // parsing rules. If version 1, use RFC2109. Else use RFC6265.

        skipLWS(bb);

        // Record position in case we need to return.
        int mark = bb.position();

        SkipResult skipResult = skipBytes(bb, VERSION_BYTES);
        if (skipResult != SkipResult.FOUND) {
            // No need to reset position since skipConstant() will have done it
            parseCookieRfc6265(bb, serverCookies);
            return;
        }

        skipLWS(bb);

        skipResult = skipByte(bb, EQUALS_BYTE);
        if (skipResult != SkipResult.FOUND) {
            // Need to reset position as skipConstant() will only have reset to
            // position before it was called
            bb.position(mark);
            parseCookieRfc6265(bb, serverCookies);
            return;
        }

        skipLWS(bb);

        ByteBuffer value = readCookieValue(bb);
        if (value != null && value.remaining() == 1) {
            if (value.get() == (byte) 49) {
                // $Version=1 -> RFC2109
                skipLWS(bb);
                byte b = bb.get();
                if (b == SEMICOLON_BYTE || b == COMMA_BYTE) {
                    parseCookieRfc2109(bb, serverCookies);
                }
                return;
            } else {
                // Unrecognised version.
                // Ignore this header.
                value.rewind();
                logInvalidVersion(value);
            }
        } else {
            // Unrecognised version.
            // Ignore this header.
            logInvalidVersion(value);
        }
    }


    public static String unescapeCookieValueRfc2109(String input) {
        if (input == null || input.length() < 2) {
            return input;
        }
        if (input.charAt(0) != '"' && input.charAt(input.length() - 1) != '"') {
            return input;
        }

        StringBuilder sb = new StringBuilder(input.length());
        char[] chars = input.toCharArray();
        boolean escaped = false;

        for (int i = 1; i < input.length() - 1; i++) {
            if (chars[i] == '\\') {
                escaped = true;
            } else if (escaped) {
                escaped = false;
                if (chars[i] < 128) {
                    sb.append(chars[i]);
                } else {
                    sb.append('\\');
                    sb.append(chars[i]);
                }
            } else {
                sb.append(chars[i]);
            }
        }
        return sb.toString();
    }


    private static void parseCookieRfc6265(ByteBuffer bb, ServerCookies serverCookies) {

        boolean moreToProcess = true;

        while (moreToProcess) {
            skipLWS(bb);

            ByteBuffer name = readToken(bb);
            ByteBuffer value = null;

            skipLWS(bb);

            SkipResult skipResult = skipByte(bb, EQUALS_BYTE);
            if (skipResult == SkipResult.FOUND) {
                skipLWS(bb);
                value = readCookieValueRfc6265(bb);
                if (value == null) {
                    logInvalidHeader(bb);
                    // Invalid cookie value. Skip to the next semi-colon
                    skipUntilSemiColon(bb);
                    continue;
                }
                skipLWS(bb);
            }

            skipResult = skipByte(bb, SEMICOLON_BYTE);
            if (skipResult == SkipResult.FOUND) {
                // NO-OP
            } else if (skipResult == SkipResult.NOT_FOUND) {
                logInvalidHeader(bb);
                // Invalid cookie. Ignore it and skip to the next semi-colon
                skipUntilSemiColon(bb);
                continue;
            } else {
                // SkipResult.EOF
                moreToProcess = false;
            }

            if (name.hasRemaining()) {
                ServerCookie sc = serverCookies.addCookie();
                sc.getName().setBytes(name.array(), name.position(), name.remaining());
                if (value == null) {
                    sc.getValue().setBytes(EMPTY_BYTES, 0, EMPTY_BYTES.length);
                } else {
                    sc.getValue().setBytes(value.array(), value.position(), value.remaining());
                }
            }
        }
    }


    private static void parseCookieRfc2109(ByteBuffer bb, ServerCookies serverCookies) {

        boolean moreToProcess = true;

        while (moreToProcess) {
            skipLWS(bb);

            boolean parseAttributes = true;

            ByteBuffer name = readToken(bb);
            ByteBuffer value = null;
            ByteBuffer path = null;
            ByteBuffer domain = null;

            skipLWS(bb);

            SkipResult skipResult = skipByte(bb, EQUALS_BYTE);
            if (skipResult == SkipResult.FOUND) {
                skipLWS(bb);
                value = readCookieValueRfc2109(bb, false);
                if (value == null) {
                    skipInvalidCookie(bb);
                    continue;
                }
                skipLWS(bb);
            }

            skipResult = skipByte(bb, COMMA_BYTE);
            if (skipResult == SkipResult.FOUND) {
                parseAttributes = false;
            }
            skipResult = skipByte(bb, SEMICOLON_BYTE);
            if (skipResult == SkipResult.EOF) {
                parseAttributes = false;
                moreToProcess = false;
            } else if (skipResult == SkipResult.NOT_FOUND) {
                skipInvalidCookie(bb);
                continue;
            }

            if (parseAttributes) {
                skipResult = skipBytes(bb, PATH_BYTES);
                if (skipResult == SkipResult.FOUND) {
                    skipLWS(bb);
                    skipResult = skipByte(bb, EQUALS_BYTE);
                    if (skipResult != SkipResult.FOUND) {
                        skipInvalidCookie(bb);
                        continue;
                    }
                    path = readCookieValueRfc2109(bb, true);
                    if (path == null) {
                        skipInvalidCookie(bb);
                        continue;
                    }
                    skipLWS(bb);

                    skipResult = skipByte(bb, COMMA_BYTE);
                    if (skipResult == SkipResult.FOUND) {
                        parseAttributes = false;
                    }
                    skipResult = skipByte(bb, SEMICOLON_BYTE);
                    if (skipResult == SkipResult.EOF) {
                        parseAttributes = false;
                        moreToProcess = false;
                    } else if (skipResult == SkipResult.NOT_FOUND) {
                        skipInvalidCookie(bb);
                        continue;
                    }
                }
            }

            if (parseAttributes) {
                skipResult = skipBytes(bb, DOMAIN_BYTES);
                if (skipResult == SkipResult.FOUND) {
                    skipLWS(bb);
                    skipResult = skipByte(bb, EQUALS_BYTE);
                    if (skipResult != SkipResult.FOUND) {
                        skipInvalidCookie(bb);
                        continue;
                    }
                    domain = readCookieValueRfc2109(bb, false);
                    if (domain == null) {
                        skipInvalidCookie(bb);
                        continue;
                    }

                    skipResult = skipByte(bb, COMMA_BYTE);
                    if (skipResult == SkipResult.FOUND) {
                        parseAttributes = false;
                    }
                    skipResult = skipByte(bb, SEMICOLON_BYTE);
                    if (skipResult == SkipResult.EOF) {
                        parseAttributes = false;
                        moreToProcess = false;
                    } else if (skipResult == SkipResult.NOT_FOUND) {
                        skipInvalidCookie(bb);
                        continue;
                    }
                }
            }

            if (name.hasRemaining() && value != null && value.hasRemaining()) {
                ServerCookie sc = serverCookies.addCookie();
                sc.setVersion(1);
                sc.getName().setBytes(name.array(), name.position(), name.remaining());
                sc.getValue().setBytes(value.array(), value.position(), value.remaining());
                if (domain != null) {
                    sc.getDomain().setBytes(domain.array(),  domain.position(),  domain.remaining());
                }
                if (path != null) {
                    sc.getPath().setBytes(path.array(),  path.position(),  path.remaining());
                }
            }
        }
    }


    private static void skipInvalidCookie(ByteBuffer bb) {
        logInvalidHeader(bb);
        // Invalid cookie value. Skip to the next semi-colon
        skipUntilSemiColonOrComma(bb);
    }


    private static void skipLWS(ByteBuffer bb) {
        while(bb.hasRemaining()) {
            byte b = bb.get();
            if (b != TAB_BYTE && b != SPACE_BYTE) {
                bb.rewind();
                break;
            }
        }
    }


    private static void skipUntilSemiColon(ByteBuffer bb) {
        while(bb.hasRemaining()) {
            if (bb.get() == SEMICOLON_BYTE) {
                break;
            }
        }
    }


    private static void skipUntilSemiColonOrComma(ByteBuffer bb) {
        while(bb.hasRemaining()) {
            byte b = bb.get();
            if (b == SEMICOLON_BYTE || b == COMMA_BYTE) {
                break;
            }
        }
    }


    private static SkipResult skipByte(ByteBuffer bb, byte target) {

        if (!bb.hasRemaining()) {
            return SkipResult.EOF;
        }
        if (bb.get() == target) {
            return SkipResult.FOUND;
        }

        bb.rewind();
        return SkipResult.NOT_FOUND;
    }


    private static SkipResult skipBytes(ByteBuffer bb, byte[] target) {
        int mark = bb.position();

        for (int i = 0; i < target.length; i++) {
            if (!bb.hasRemaining()) {
                bb.position(mark);
                return SkipResult.EOF;
            }
            if (bb.get() != target[i]) {
                bb.position(mark);
                return SkipResult.NOT_FOUND;
            }
        }
        return SkipResult.FOUND;
    }


    /**
     * Similar to readCookieValueRfc6265() but also allows a comma to terminate
     * the value (as permitted by RFC2109).
     */
    private static ByteBuffer readCookieValue(ByteBuffer bb) {
        boolean quoted = false;
        if (bb.hasRemaining()) {
            if (bb.get() == QUOTE_BYTE) {
                quoted = true;
            } else {
                bb.rewind();
            }
        }
        int start = bb.position();
        int end = bb.limit();
        while (bb.hasRemaining()) {
            byte b = bb.get();
            if (isCookieOctet[(b & 0xFF)]) {
                // NO-OP
            } else if (b == SEMICOLON_BYTE || b == COMMA_BYTE || b == SPACE_BYTE || b == TAB_BYTE) {
                end = bb.position() - 1;
                bb.position(end);
                break;
            } else if (quoted && b == QUOTE_BYTE) {
                end = bb.position() - 1;
                break;
            } else {
                // Invalid cookie
                return null;
            }
        }

        return new ByteBuffer(bb.bytes, start, end - start);
    }


    /**
     * Similar to readCookieValue() but treats a comma as part of an invalid
     * value.
     */
    private static ByteBuffer readCookieValueRfc6265(ByteBuffer bb) {
        boolean quoted = false;
        if (bb.hasRemaining()) {
            if (bb.get() == QUOTE_BYTE) {
                quoted = true;
            } else {
                bb.rewind();
            }
        }
        int start = bb.position();
        int end = bb.limit();
        while (bb.hasRemaining()) {
            byte b = bb.get();
            if (isCookieOctet[(b & 0xFF)]) {
                // NO-OP
            } else if (b == SEMICOLON_BYTE || b == SPACE_BYTE || b == TAB_BYTE) {
                end = bb.position() - 1;
                bb.position(end);
                break;
            } else if (quoted && b == QUOTE_BYTE) {
                end = bb.position() - 1;
                break;
            } else {
                // Invalid cookie
                return null;
            }
        }

        return new ByteBuffer(bb.bytes, start, end - start);
    }


    private static ByteBuffer readCookieValueRfc2109(ByteBuffer bb, boolean allowForwardSlash) {
        if (!bb.hasRemaining()) {
            return null;
        }

        if (bb.peek() == QUOTE_BYTE) {
            return readQuotedString(bb);
        } else {
            if (allowForwardSlash) {
                return readTokenAllowForwardSlash(bb);
            } else {
                return readToken(bb);
            }
        }
    }


    private static ByteBuffer readToken(ByteBuffer bb) {
        final int start = bb.position();
        int end = bb.limit();
        while (bb.hasRemaining()) {
            if (!HttpParser.isToken(bb.get())) {
                end = bb.position() - 1;
                bb.position(end);
                break;
            }
        }

        return new ByteBuffer(bb.bytes, start, end - start);
    }


    private static ByteBuffer readTokenAllowForwardSlash(ByteBuffer bb) {
        final int start = bb.position();
        int end = bb.limit();
        while (bb.hasRemaining()) {
            byte b = bb.get();
            if (b != FORWARDSLASH_BYTE && !HttpParser.isToken(b)) {
                end = bb.position() - 1;
                bb.position(end);
                break;
            }
        }

        return new ByteBuffer(bb.bytes, start, end - start);
    }


    private static ByteBuffer readQuotedString(ByteBuffer bb) {
        int start = bb.position();

        // Read the opening quote
        bb.get();
        boolean escaped = false;
        while (bb.hasRemaining()) {
            byte b = bb.get();
            if (b == SLASH_BYTE) {
                // Escaping another character
                escaped = true;
            } else if (escaped && b > (byte) -1) {
                escaped = false;
            } else if (b == QUOTE_BYTE) {
                return new ByteBuffer(bb.bytes, start, bb.position() - start);
            } else if (isText[b & 0xFF]) {
                escaped = false;
            } else {
                return null;
            }
        }

        return null;
    }


    private static void logInvalidHeader(ByteBuffer bb) {
        UserDataHelper.Mode logMode = invalidCookieLog.getNextMode();
        if (logMode != null) {
            String headerValue = new String(bb.array(), bb.position(), bb.limit() - bb.position(),
                        StandardCharsets.UTF_8);
            String message = sm.getString("cookie.invalidCookieValue", headerValue);
            switch (logMode) {
                case INFO_THEN_DEBUG:
                    message += sm.getString("cookie.fallToDebug");
                    //$FALL-THROUGH$
                case INFO:
                    log.info(message);
                    break;
                case DEBUG:
                    log.debug(message);
            }
        }
    }


    private static void logInvalidVersion(ByteBuffer value) {
        UserDataHelper.Mode logMode = invalidCookieVersionLog.getNextMode();
        if (logMode != null) {
            String version;
            if (value == null) {
                version = sm.getString("cookie.valueNotPresent");
            } else {
                version = new String(value.bytes, value.position(),
                        value.limit() - value.position(), StandardCharsets.UTF_8);
            }
            String message = sm.getString("cookie.invalidCookieVersion", version);
            switch (logMode) {
                case INFO_THEN_DEBUG:
                    message += sm.getString("cookie.fallToDebug");
                    //$FALL-THROUGH$
                case INFO:
                    log.info(message);
                    break;
                case DEBUG:
                    log.debug(message);
            }
        }
    }


    /**
     * Custom implementation that skips many of the safety checks in
     * {@link javax.nio.ByteBuffer}.
     */
    private static class ByteBuffer {

        private final byte[] bytes;
        private int limit;
        private int position = 0;

        public ByteBuffer(byte[] bytes, int offset, int len) {
            this.bytes = bytes;
            this.position = offset;
            this.limit = offset + len;
        }

        public int position() {
            return position;
        }

        public void position(int position) {
            this.position = position;
        }

        public int limit() {
            return limit;
        }

        public int remaining() {
            return limit - position;
        }

        public boolean hasRemaining() {
            return position < limit;
        }

        public byte get() {
            return bytes[position++];
        }

        public byte peek() {
            return bytes[position];
        }

        public void rewind() {
            position--;
        }

        public byte[] array() {
            return bytes;
        }

        // For debug purposes
        @Override
        public String toString() {
            return "position [" + position + "], limit [" + limit + "]";
        }
    }
}
