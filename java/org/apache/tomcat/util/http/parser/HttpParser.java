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
import java.util.LinkedHashMap;
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
 * - MediaType (used for Content-Type header)
 *
 * Support for additional headers will be provided as required.
 */
public class HttpParser {

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
     * @return  A map of directives and values as {@link String}s or
     *          <code>null</code> if a parsing error occurs. Although the
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

        if (skipConstant(input, "Digest") != SkipConstantResult.FOUND) {
            return null;
        }
        // All field names are valid tokens
        String field = readToken(input);
        if (field == null) {
            return null;
        }
        while (!field.equals("")) {
            if (skipConstant(input, "=") != SkipConstantResult.FOUND) {
                return null;
            }
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
                    value = readQuotedString(input, false);
                    break;
                case 2:
                    // FIELD_TYPE_TOKEN_OR_QUOTED_STRING
                    value = readTokenOrQuotedString(input, false);
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

            if (value == null) {
                return null;
            }
            result.put(field, value);

            if (skipConstant(input, ",") == SkipConstantResult.NOT_FOUND) {
                return null;
            }
            field = readToken(input);
            if (field == null) {
                return null;
            }
        }

        return result;
    }

    public static MediaType parseMediaType(StringReader input)
            throws IOException {

        // Type (required)
        String type = readToken(input);
        if (type == null || type.length() == 0) {
            return null;
        }

        if (skipConstant(input, "/") == SkipConstantResult.NOT_FOUND) {
            return null;
        }

        // Subtype (required)
        String subtype = readToken(input);
        if (subtype == null || subtype.length() == 0) {
            return null;
        }

        LinkedHashMap<String,String> parameters = new LinkedHashMap<>();

        SkipConstantResult lookForSemiColon = skipConstant(input, ";");
        if (lookForSemiColon == SkipConstantResult.NOT_FOUND) {
            return null;
        }
        while (lookForSemiColon == SkipConstantResult.FOUND) {
            String attribute = readToken(input);

            if (skipConstant(input, "=") == SkipConstantResult.FOUND) {
                String value = readTokenOrQuotedString(input, true);
                parameters.put(attribute.toLowerCase(Locale.US), value);
            } else {
                parameters.put(attribute.toLowerCase(Locale.US), "");
            }

            lookForSemiColon = skipConstant(input, ";");
            if (lookForSemiColon == SkipConstantResult.NOT_FOUND) {
                return null;
            }
        }

        return new MediaType(type, subtype, parameters);
    }

    public static String unquote(String input) {
        if (input == null || input.length() < 2 || input.charAt(0) != '"') {
            return input;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 1 ; i < (input.length() - 1); i++) {
            char c = input.charAt(i);
            if (input.charAt(i) == '\\') {
                i++;
                result.append(input.charAt(i));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static SkipConstantResult skipConstant(StringReader input,
            String constant) throws IOException {
        int len = constant.length();

        int c = input.read();
        while (c == 32 || c == 9) {
            c = input.read();
        }

        for (int i = 0; i < len; i++) {
            if (i == 0 && c == -1) {
                return SkipConstantResult.EOF;
            }
            if (c != constant.charAt(i)) {
                input.skip(-(i + 1));
                return SkipConstantResult.NOT_FOUND;
            }
            if (i != (len - 1)) {
                c = input.read();
            }
        }
        return SkipConstantResult.FOUND;
    }

    /**
     * @return  the token if one was found, the empty string if no data was
     *          available to read or <code>null</code> if data other than a
     *          token was found
     */
    private static String readToken(StringReader input) throws IOException {
        StringBuilder result = new StringBuilder();

        int c = input.read();

        // Skip lws
        while (c == 32 || c == 9) {
            c = input.read();
        }

        while (c != -1 && isToken[c]) {
            result.append((char) c);
            c = input.read();
        }
        // Skip back so non-token character is available for next read
        input.skip(-1);

        if (c != -1 && result.length() == 0) {
            return null;
        } else {
            return result.toString();
        }
    }

    /**
     * @return the quoted string if one was found, null if data other than a
     *         quoted string was found or null if the end of data was reached
     *         before the quoted string was terminated
     */
    private static String readQuotedString(StringReader input,
            boolean returnQuoted) throws IOException {

        int c = input.read();

        // Skip lws
        while (c == 32 || c == 9) {
            c = input.read();
        }

        if (c != '"') {
            return null;
        }

        StringBuilder result = new StringBuilder();
        if (returnQuoted) {
            result.append('\"');
        }
        c = input.read();

        while (c != '"') {
            if (c == -1) {
                return null;
            } else if (c == '\\') {
                c = input.read();
                if (returnQuoted) {
                    result.append('\\');
                }
                result.append(c);
            } else {
                result.append((char) c);
            }
            c = input.read();
        }
        if (returnQuoted) {
            result.append('\"');
        }

        return result.toString();
    }

    private static String readTokenOrQuotedString(StringReader input,
            boolean returnQuoted) throws IOException {
        input.mark(1);
        int c = input.read();
        input.reset();

        if (c == '"') {
            return readQuotedString(input, returnQuoted);
        } else {
            return readToken(input);
        }
    }

    /**
     * Parses lower case hex but permits upper case hex to be used (converting
     * it to lower case before returning).
     *
     * @return the lower case hex if present or <code>null</code> if data other
     *         than lower case hex was found
     */
    private static String readLhex(StringReader input) throws IOException {
        StringBuilder result = new StringBuilder();

        int c = input.read();

        // Skip lws
        while (c == 32 || c == 9) {
            c = input.read();
        }

        while (c != -1 && isHex[c]) {
            result.append((char) c);
            c = input.read();
        }
        // Skip back so non-hex character is available for next read
        input.skip(-1);

        if (result.length() == 0) {
            return null;
        } else {
            return result.toString().toLowerCase(Locale.US);
        }
    }

    private static String readQuotedLhex(StringReader input)
            throws IOException {

        if (skipConstant(input, "\"") != SkipConstantResult.FOUND) {
            return null;
        }
        String result = readLhex(input);
        if (skipConstant(input, "\"") == SkipConstantResult.NOT_FOUND) {
            return null;
        }

        return result;
    }

    private static enum SkipConstantResult {
        FOUND,
        NOT_FOUND,
        EOF
    }
}
