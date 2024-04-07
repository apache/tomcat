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
package org.apache.tomcat.util.json;

/**
 * Provides escaping of values so they can be included in a JSON document.
 * Escaping is based on the definition of JSON found in
 * <a href="https://www.rfc-editor.org/rfc/rfc8259.html">RFC 8259</a>.
 */
public class JSONFilter {

    /**
     * Escape the given char.
     * @param c the char
     * @return a char array with the escaped sequence
     */
    public static char[] escape(char c) {
        if (c < 0x20 || c == 0x22 || c == 0x5c || Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) {
            char popular = getPopularChar(c);
            if (popular > 0) {
                return new char[] { '\\', popular };
            } else {
                StringBuilder escaped = new StringBuilder(6);
                escaped.append("\\u");
                escaped.append(String.format("%04X", Integer.valueOf(c)));
                return escaped.toString().toCharArray();
            }
        } else {
            char[] result = new char[1];
            result[0] = c;
            return result;
        }
    }

    /**
     * Escape the given string.
     * @param input the string
     * @return the escaped string
     */
    public static String escape(String input) {
        return escape(input, 0, input.length()).toString();
    }

    /**
     * Escape the given char sequence.
     * @param input the char sequence
     * @return the escaped char sequence
     */
    public static CharSequence escape(CharSequence input) {
        return escape(input, 0, input.length());
    }

    /**
     * Escape the given char sequence.
     * @param input the char sequence
     * @param off the offset on which escaping will start
     * @param length the length which should be escaped
     * @return the escaped char sequence corresponding to the specified range
     */
    public static CharSequence escape(CharSequence input, int off, int length) {
        /*
         * While any character MAY be escaped, only U+0000 to U+001F (control
         * characters), U+0022 (quotation mark) and U+005C (reverse solidus)
         * MUST be escaped.
         */
        StringBuilder escaped = null;
        int lastUnescapedStart = off;
        for (int i = off; i < length; i++) {
            char c = input.charAt(i);
            if (c < 0x20 || c == 0x22 || c == 0x5c || Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) {
                if (escaped == null) {
                    escaped = new StringBuilder(length + 20);
                }
                if (lastUnescapedStart < i) {
                    escaped.append(input.subSequence(lastUnescapedStart, i));
                }
                lastUnescapedStart = i + 1;
                char popular = getPopularChar(c);
                if (popular > 0) {
                    escaped.append('\\').append(popular);
                } else {
                    escaped.append("\\u");
                    escaped.append(String.format("%04X", Integer.valueOf(c)));
                }
            }
        }
        if (escaped == null) {
            if (off == 0 && length == input.length()) {
                return input;
            } else {
                return input.subSequence(off, length - off);
            }
        } else {
            if (lastUnescapedStart < length) {
                escaped.append(input.subSequence(lastUnescapedStart, length));
            }
            return escaped.toString();
        }
    }

    private JSONFilter() {
        // Utility class. Hide the default constructor.
    }

    private static char getPopularChar(char c) {
        switch (c) {
            case '"':
            case '\\':
            case '/':
                return c;
            case 0x8:
                return 'b';
            case 0xc:
                return 'f';
            case 0xa:
                return 'n';
            case 0xd:
                return 'r';
            case 0x9:
                return 't';
            default:
                return 0;
        }
    }

}
