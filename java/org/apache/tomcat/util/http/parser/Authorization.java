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
 * Parser for an "Authorization" header.
 */
public class Authorization {

    private static final Map<String,FieldType> fieldTypes = new HashMap<>();

    static {
        // Digest field types.
        // Note: These are more relaxed than RFC2617. This adheres to the
        // recommendation of RFC2616 that servers are tolerant of buggy
        // clients when they can be so without ambiguity.
        fieldTypes.put("username", FieldType.QUOTED_STRING);
        fieldTypes.put("realm", FieldType.QUOTED_STRING);
        fieldTypes.put("nonce", FieldType.QUOTED_STRING);
        fieldTypes.put("digest-uri", FieldType.QUOTED_STRING);
        // RFC2617 says response is <">32LHEX<">. 32LHEX will also be accepted
        fieldTypes.put("response", FieldType.LHEX);
        // RFC2617 says algorithm is token. <">token<"> will also be accepted
        fieldTypes.put("algorithm", FieldType.QUOTED_TOKEN);
        fieldTypes.put("cnonce", FieldType.QUOTED_STRING);
        fieldTypes.put("opaque", FieldType.QUOTED_STRING);
        // RFC2617 says qop is token. <">token<"> will also be accepted
        fieldTypes.put("qop", FieldType.QUOTED_TOKEN);
        // RFC2617 says nc is 8LHEX. <">8LHEX<"> will also be accepted
        fieldTypes.put("nc", FieldType.LHEX);

    }


    private Authorization() {
        // Utility class. Hide default constructor.
    }


    /**
     * Parses an HTTP Authorization header for DIGEST authentication as per RFC 2617 section 3.2.2.
     *
     * @param input The header value to parse
     *
     * @return A map of directives and values as {@link String}s or <code>null</code> if a parsing error occurs.
     *             Although the values returned are {@link String}s they will have been validated to ensure that they
     *             conform to RFC 2617.
     *
     * @throws IllegalArgumentException If the header does not conform to RFC 2617
     * @throws IOException              If an error occurs while reading the input
     */
    public static Map<String,String> parseAuthorizationDigest(StringReader input)
            throws IllegalArgumentException, IOException {

        Map<String,String> result = new HashMap<>();

        if (HttpParser.skipConstant(input, "Digest") != SkipResult.FOUND) {
            return null;
        }
        // All field names are valid tokens
        String field = HttpParser.readToken(input);
        if (field == null) {
            return null;
        }
        while (!field.equals("")) {
            if (HttpParser.skipConstant(input, "=") != SkipResult.FOUND) {
                return null;
            }
            String value = null;
            FieldType type = fieldTypes.get(field.toLowerCase(Locale.ENGLISH));
            if (type == null) {
                // auth-param = token "=" ( token | quoted-string )
                type = FieldType.TOKEN_OR_QUOTED_STRING;
            }
            switch (type) {
                case QUOTED_STRING:
                    value = HttpParser.readQuotedString(input, false);
                    break;
                case TOKEN_OR_QUOTED_STRING:
                    value = HttpParser.readTokenOrQuotedString(input, false);
                    break;
                case LHEX:
                    value = HttpParser.readLhex(input);
                    break;
                case QUOTED_TOKEN:
                    value = HttpParser.readQuotedToken(input);
                    break;
            }

            if (value == null) {
                return null;
            }
            result.put(field, value);

            if (HttpParser.skipConstant(input, ",") == SkipResult.NOT_FOUND) {
                return null;
            }
            field = HttpParser.readToken(input);
            if (field == null) {
                return null;
            }
        }

        return result;
    }


    private enum FieldType {
        // Unused due to buggy clients
        // TOKEN,
        QUOTED_STRING,
        TOKEN_OR_QUOTED_STRING,
        LHEX,
        QUOTED_TOKEN;
    }
}
