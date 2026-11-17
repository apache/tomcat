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

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.apache.tomcat.util.http.parser.HttpParser;

/**
 * Utility methods for HTTP header processing.
 */
public class HeaderUtil {

    private static final Set<String> disallowedTrailerFieldNames = new HashSet<>();

    static {
        // Always add these in lower case
        disallowedTrailerFieldNames.add("age");
        disallowedTrailerFieldNames.add("cache-control");
        disallowedTrailerFieldNames.add("connection");
        disallowedTrailerFieldNames.add("content-length");
        disallowedTrailerFieldNames.add("content-encoding");
        disallowedTrailerFieldNames.add("content-range");
        disallowedTrailerFieldNames.add("content-type");
        disallowedTrailerFieldNames.add("date");
        disallowedTrailerFieldNames.add("expires");
        disallowedTrailerFieldNames.add("host");
        disallowedTrailerFieldNames.add("location");
        disallowedTrailerFieldNames.add("retry-after");
        disallowedTrailerFieldNames.add("te");
        disallowedTrailerFieldNames.add("trailer");
        disallowedTrailerFieldNames.add("transfer-encoding");
        disallowedTrailerFieldNames.add("upgrade");
        disallowedTrailerFieldNames.add("vary");
        disallowedTrailerFieldNames.add("warning");
        disallowedTrailerFieldNames.add("www-authenticate");
    }


    /**
     * Check if the specified header name is disallowed in HTTP trailers.
     *
     * @param headerName The header name to check
     *
     * @return {@code true} if the header is disallowed in trailers
     */
    public static boolean isHeaderDisallowedInTrailers(String headerName) {
        return disallowedTrailerFieldNames.contains(headerName.toLowerCase(Locale.ENGLISH));
    }


    /**
     * Corrects an HTTP header field value so that it is valid when sent as ISO-8859-1. Control characters other than
     * tab, and DEL, are replaced with a space and leading and trailing whitespace is removed. A value that does not
     * need to be corrected is returned unchanged without being copied.
     *
     * @param value The value to correct
     *
     * @return The same instance if the value was valid, the corrected value if it was not or {@code null} if the value
     *             contains a character that is not in the range 0 to 255 and so cannot be sent
     */
    public static String sanitizeFieldValue(String value) {
        int length = value.length();
        boolean valid = true;
        if (length > 0 &&
                (HttpParser.isWhiteSpace(value.charAt(0)) || HttpParser.isWhiteSpace(value.charAt(length - 1)))) {
            valid = false;
        }
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            if (c > 255) {
                return null;
            }
            // Control characters other than tab are not valid
            if (!HttpParser.isFieldContent(c)) {
                valid = false;
            }
        }
        if (valid) {
            return value;
        }

        char[] result = value.toCharArray();
        int first = 0;
        int last = length;
        for (int i = 0; i < length; i++) {
            if (!HttpParser.isFieldContent(result[i])) {
                result[i] = ' ';
            }
        }
        while (first < last && HttpParser.isWhiteSpace(result[first])) {
            first++;
        }
        while (last > first && HttpParser.isWhiteSpace(result[last - 1])) {
            last--;
        }
        return new String(result, first, last - first);
    }


    /**
     * Converts an HTTP header line in byte form to a printable String. Bytes corresponding to visible ASCII characters
     * will be converted to those characters. All other bytes (0x00 to 0x1F, 0x7F to 0xFF) will be represented in 0xNN
     * form.
     *
     * @param bytes  Contains an HTTP header line
     * @param offset The start position of the header line in the array
     * @param len    The length of the HTTP header line
     *
     * @return A String with non-printing characters replaced by the 0xNN equivalent
     */
    public static String toPrintableString(byte[] bytes, int offset, int len) {
        StringBuilder result = new StringBuilder();
        for (int i = offset; i < offset + len; i++) {
            char c = (char) (bytes[i] & 0xFF);
            if (c < 0x20 || c > 0x7E) {
                result.append("0x");
                result.append(Character.forDigit((c >> 4) & 0xF, 16));
                result.append(Character.forDigit((c) & 0xF, 16));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }


    private HeaderUtil() {
        // Utility class. Hide default constructor.
    }
}
