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
package org.apache.tomcat.util.http.fileupload.util.mime;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
/**
 * Utility class to decode/encode character set on HTTP Header fields based on RFC 2231.
 * This implementation adheres to RFC 5987 in particular, which was defined for HTTP headers
 *
 * RFC 5987 builds on RFC 2231, but has lesser scope like <a href="https://tools.ietf.org/html/rfc5987#section-3.2">mandatory charset definition</a>
 * and <a href="https://tools.ietf.org/html/rfc5987#section-4">no parameter continuation</a>
 *
 * <p>
 * @see <a href="https://tools.ietf.org/html/rfc2231">RFC 2231</a>
 * @see <a href="https://tools.ietf.org/html/rfc5987">RFC 5987</a>
 */
public final class RFC2231Utility {

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    private static final byte[] HEX_DECODE = new byte[0x80];

    // create a ASCII decoded array of Hexadecimal values
    static {
        for (int i = 0; i < HEX_DIGITS.length; i++) {
            HEX_DECODE[HEX_DIGITS[i]] = (byte) i;
            HEX_DECODE[Character.toLowerCase(HEX_DIGITS[i])] = (byte) i;
        }
    }

    /**
     * Checks if Asterisk (*) at the end of parameter name to indicate,
     * if it has charset and language information to decode the value
     * @param paramName The parameter, which is being checked.
     * @return {@code true}, if encoded as per RFC 2231, {@code false} otherwise
     */
    public static boolean hasEncodedValue(final String paramName) {
        if (paramName != null) {
            return paramName.lastIndexOf('*') == (paramName.length() - 1);
        }
        return false;
    }

    /**
     * If {@code paramName} has Asterisk (*) at the end, it will be stripped off,
     * else the passed value will be returned
     * @param paramName The parameter, which is being inspected.
     * @return stripped {@code paramName} of Asterisk (*), if RFC2231 encoded
     */
    public static String stripDelimiter(final String paramName) {
        if (hasEncodedValue(paramName)) {
            final StringBuilder paramBuilder = new StringBuilder(paramName);
            paramBuilder.deleteCharAt(paramName.lastIndexOf('*'));
            return paramBuilder.toString();
        }
        return paramName;
    }

    /**
     * Decode a string of text obtained from a HTTP header as per RFC 2231
     *
     * <b>Eg 1.</b> {@code us-ascii'en-us'This%20is%20%2A%2A%2Afun%2A%2A%2A}
     * will be decoded to {@code This is ***fun***}
     *
     * <b>Eg 2.</b> {@code iso-8859-1'en'%A3%20rate}
     * will be decoded to {@code £ rate}.
     *
     * <b>Eg 3.</b> {@code UTF-8''%c2%a3%20and%20%e2%82%ac%20rates}
     * will be decoded to {@code £ and € rates}.
     *
     * @param encodedText - Text to be decoded has a format of {@code <charset>'<language>'<encoded_value>} and ASCII only
     * @return Decoded text based on charset encoding
     * @throws UnsupportedEncodingException The requested character set wasn't found.
     */
    public static String decodeText(final String encodedText) throws UnsupportedEncodingException {
        final int langDelimitStart = encodedText.indexOf('\'');
        if (langDelimitStart == -1) {
            // missing charset
            return encodedText;
        }
        final String mimeCharset = encodedText.substring(0, langDelimitStart);
        final int langDelimitEnd = encodedText.indexOf('\'', langDelimitStart + 1);
        if (langDelimitEnd == -1) {
            // missing language
            return encodedText;
        }
        final byte[] bytes = fromHex(encodedText.substring(langDelimitEnd + 1));
        return new String(bytes, getJavaCharset(mimeCharset));
    }

    /**
     * Convert {@code text} to their corresponding Hex value
     * @param text - ASCII text input
     * @return Byte array of characters decoded from ASCII table
     */
    private static byte[] fromHex(final String text) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(text.length());
        for (int i = 0; i < text.length();) {
            final char c = text.charAt(i++);
            if (c == '%') {
                if (i > text.length() - 2) {
                    break; // unterminated sequence
                }
                final byte b1 = HEX_DECODE[text.charAt(i++) & 0x7f];
                final byte b2 = HEX_DECODE[text.charAt(i++) & 0x7f];
                out.write((b1 << 4) | b2);
            } else {
                out.write((byte) c);
            }
        }
        return out.toByteArray();
    }

    private static String getJavaCharset(final String mimeCharset) {
        // good enough for standard values
        return mimeCharset;
    }
}
