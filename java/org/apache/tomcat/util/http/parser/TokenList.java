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
import java.io.Reader;
import java.util.Collection;
import java.util.Locale;

import org.apache.tomcat.util.http.parser.HttpParser.SkipResult;

public class TokenList {

    private TokenList() {
        // Utility class. Hide default constructor.
    }


    /**
     * Parses a header of the form 1#token, forcing all parsed values to lower
     * case. This is typically used when header values are case-insensitive.
     *
     * @param input  The header to parse
     * @param result The Collection (usually a list of a set) to which the
     *                   parsed token should be added
     *
     * @throws IOException If an I/O error occurs reading the header
     */
    public static void parseTokenList(Reader input, Collection<String> result) throws IOException {
        parseTokenList(input, true, result);
    }


    /**
     * Parses a header of the form 1#token.
     *
     * @param input          The header to parse
     * @param forceLowerCase Should parsed tokens be forced to lower case? This
     *                           is intended for headers where the values are
     *                           case-insensitive
     * @param result         The Collection (usually a list of a set) to which
     *                           the parsed token should be added
     *
     * @throws IOException If an I/O error occurs reading the header
     */
    public static void parseTokenList(Reader input, boolean forceLowerCase, Collection<String> result)
            throws IOException {

        do {
            String fieldName = HttpParser.readToken(input);
            if (fieldName == null) {
                // Invalid field-name, skip to the next one
                HttpParser.skipUntil(input, 0, ',');
                continue;
            }

            if (fieldName.length() == 0) {
                // No more data to read
                break;
            }

            SkipResult skipResult = HttpParser.skipConstant(input, ",");
            if (skipResult == SkipResult.EOF) {
                // EOF
                if (forceLowerCase) {
                    result.add(fieldName.toLowerCase(Locale.ENGLISH));
                } else {
                    result.add(fieldName);
                }
                break;
            } else if (skipResult == SkipResult.FOUND) {
                if (forceLowerCase) {
                    result.add(fieldName.toLowerCase(Locale.ENGLISH));
                } else {
                    result.add(fieldName);
                }
                continue;
            } else {
                // Not a token - ignore it
                HttpParser.skipUntil(input, 0, ',');
                continue;
            }
        } while (true);
    }
}
