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

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * HTTP header value parser implementation. Parsing HTTP headers as per RFC2616
 * is not always as simple as it first appears. For headers that only use tokens
 * the simple approach will normally be sufficient. However, for the other
 * headers, while simple code meets 99.9% of cases, there are often some edge
 * cases that make things far more complicated.
 *
 * The purpose of this parser is to let the parser worry about the edge cases.
 * It provides tolerant (where safe to do so) parsing of HTTP header values
 * assuming that wrapped header lines have already been unwrapped. (The Tomcat
 * header processing code does the unwrapping.)
 *
 * Provides parsing of the following HTTP header values as per RFC 2616:
 * - Authorization for DIGEST authentication
 *
 * Support for additional headers will be provided as required.
 *
 * TODO: Check the performance of this parser against the current Digest header
 *       parsing code.
 *
 * TODO: Add support for parsing content-type and replace HttpParser
 */
public class HttpParser2 {

    private static final Integer FIELD_TYPE_TOKEN = Integer.valueOf(0);
    private static final Integer FIELD_TYPE_QUOTED_STRING = Integer.valueOf(1);
    private static final Integer FIELD_TYPE_TOKEN_OR_QUOTED_STRING = Integer.valueOf(2);
    private static final Integer FIELD_TYPE_LHEX = Integer.valueOf(3);
    private static final Integer FIELD_TYPE_QUOTED_LHEX = Integer.valueOf(4);

    private static final Map<String,Integer> fieldTypes = new HashMap<>();

    private static final boolean isToken[] = new boolean[128];
    private static final boolean isHex[] = new boolean[128];

    static {
        // Digest field types
        fieldTypes.put("username", FIELD_TYPE_QUOTED_STRING);
        fieldTypes.put("realm", FIELD_TYPE_QUOTED_STRING);
        fieldTypes.put("nonce", FIELD_TYPE_QUOTED_STRING);
        fieldTypes.put("digest-uri", FIELD_TYPE_QUOTED_STRING);
        fieldTypes.put("response", FIELD_TYPE_QUOTED_LHEX);
        fieldTypes.put("algorithm", FIELD_TYPE_TOKEN);
        fieldTypes.put("cnonce", FIELD_TYPE_QUOTED_STRING);
        fieldTypes.put("opaque", FIELD_TYPE_QUOTED_STRING);
        fieldTypes.put("qop", FIELD_TYPE_TOKEN);
        fieldTypes.put("nc", FIELD_TYPE_LHEX);

        // Setup the flag arrays
        for (int i = 0; i < 128; i++) {
            if (i < 32) {
                isToken[i] = false;
            } else if (i == '(' || i == ')' || i == '<' || i == '>'  || i == '@'  ||
                       i == ',' || i == ';' || i == ':' || i == '\\' || i == '\"' ||
                       i == '/' || i == '[' || i == ']' || i == '?'  || i == '='  ||
                       i == '{' || i == '}' || i == ' ' || i == '\t') {
                isToken[i] = false;
            } else {
                isToken[i] = true;
            }

            if (i >= '0' && i <= '9' || i >= 'A' && i <= 'F' ||
                    i >= 'a' && i <= 'f') {
                isHex[i] = true;
            } else {
                isHex[i] = false;
            }
        }
    }

    /**
     * Parses an HTTP Authorization header for DIGEST authentication as per RFC
     * 2617 section 3.2.2.
     *
     * @param input The header value to parse
     *
     * @return  A map of directives and values as {@link String}s. Although the
     *          values returned are {@link String}s they will have been
     *          validated to ensure that they conform to RFC 2617.
     *
     * @throws IllegalArgumentException If the header does not conform to RFC
     *                                  2617
     * @throws IOException If an error occurs while reading the input
     */
    public static Map<String,String> parseAuthorizationDigest (
            StringReader input) throws IllegalArgumentException, IOException {

        Map<String,String> result = new HashMap<>();

        swallowConstant(input, "Digest", false);
        skipLws(input);
        // All field names are valid tokens
        String field = readToken(input);
        while (field != null) {
            skipLws(input);
            swallowConstant(input, "=", false);
            skipLws(input);
            String value = null;
            Integer type = fieldTypes.get(field.toLowerCase(Locale.US));
            if (type == null) {
                // auth-param = token "=" ( token | quoted-string )
                type = FIELD_TYPE_TOKEN_OR_QUOTED_STRING;
            }
            switch (type.intValue()) {
                case 0:
                    // FIELD_TYPE_TOKEN
                    value = readToken(input);
                    break;
                case 1:
                    // FIELD_TYPE_QUOTED_STRING
                    value = readQuotedString(input);
                    break;
                case 2:
                    // FIELD_TYPE_TOKEN_OR_QUOTED_STRING
                    value = readTokenOrQuotedString(input);
                    break;
                case 3:
                    // FIELD_TYPE_LHEX
                    value = readLhex(input);
                    break;
                case 4:
                    // FIELD_TYPE_QUOTED_LHEX
                    value = readQuotedLhex(input);
                    break;
                default:
                    // Error
                    throw new IllegalArgumentException(
                            "TODO i18n: Unsupported type");
            }

            result.put(field, value);

            skipLws(input);
            if (!swallowConstant(input, ",", true)) {
                break;
            }
            skipLws(input);
            field = readToken(input);
        }

        return result;
    }

    private static boolean swallowConstant(StringReader input, String constant,
            boolean optional) throws IOException {
        int len = constant.length();

        for (int i = 0; i < len; i++) {
            int c = input.read();
            if (c != constant.charAt(i)) {
                if (optional) {
                    input.skip(i);
                    return false;
                } else {
                    throw new IllegalArgumentException(
                            "TODO I18N: Failed to parse input for [" + constant +
                            "]");
                }
            }
        }
        return true;
    }

    private static void skipLws(StringReader input) throws IOException {
        char c = (char) input.read();
        while (c == 32 || c == 9) {
            c = (char) input.read();
        }

        // Skip back so non-LWS character is available for next read
        input.skip(-1);
    }

    private static String readToken(StringReader input) throws IOException {
        StringBuilder result = new StringBuilder();

        char c = (char) input.read();
        while (c != 65535 && isToken[c]) {
            result.append(c);
            c = (char) input.read();
        }
        // Skip back so non-token character is available for next read
        input.skip(-1);

        return result.toString();
    }

    private static String readQuotedString(StringReader input)
            throws IOException {

        char c = (char) input.read();
        if (c != '"') {
            throw new IllegalArgumentException(
                    "TODO i18n: Quoted string must start with a quote");
        }

        StringBuilder result = new StringBuilder();

        c = (char) input.read();
        while (c != '"') {
            if (c == '\\') {
                c = (char) input.read();
                result.append(c);
            } else {
                result.append(c);
            }
            c = (char) input.read();
        }

        return result.toString();
    }

    private static String readTokenOrQuotedString(StringReader input)
            throws IOException {
        char c = (char) input.read();
        input.skip(-1);

        if (c == '"') {
            return readQuotedString(input);
        } else {
            return readToken(input);
        }
    }

    /*
     * Parses lower case hex but permits upper case hex to be used (converting
     * it to lower case before returning).
     */
    private static String readLhex(StringReader input) throws IOException {
        StringBuilder result = new StringBuilder();

        char c = (char) input.read();
        while (isHex[c]) {
            result.append(c);
            c = (char) input.read();
        }
        // Skip back so non-hex character is available for next read
        input.skip(-1);

        return result.toString().toLowerCase();
    }

    private static String readQuotedLhex(StringReader input)
            throws IOException {

        swallowConstant(input, "\"", false);
        String result = readLhex(input);
        swallowConstant(input, "\"", false);

        return result;
    }
}
