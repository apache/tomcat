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
import java.io.StringReader;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Locale;

import org.apache.tomcat.util.http.parser.HttpParser.SkipResult;

public class TokenList {

    private TokenList() {
        // Utility class. Hide default constructor.
    }


    /**
     * Parses an enumeration of header values of the form 1#token, forcing all
     * parsed values to lower case.
     *
     * @param inputs     The headers to parse
     * @param collection The Collection (usually a list of a set) to which the
     *                       parsed tokens should be added
     *
     * @return {@code} true if the header values were parsed cleanly, otherwise
     *         {@code false} (e.g. if a non-token value was encountered)
     *
     * @throws IOException If an I/O error occurs reading the header
     */
    public static boolean parseTokenList(Enumeration<String> inputs, Collection<String> collection) throws IOException {
        boolean result = true;
        while (inputs.hasMoreElements()) {
            String nextHeaderValue = inputs.nextElement();
            if (nextHeaderValue != null) {
                if (!TokenList.parseTokenList(new StringReader(nextHeaderValue), collection)) {
                    result = false;
                }
            }
        }
        return result;
    }


    /**
     * Parses a header of the form 1#token, forcing all parsed values to lower
     * case. This is typically used when header values are case-insensitive.
     *
     * @param input      The header to parse
     * @param collection The Collection (usually a list of a set) to which the
     *                       parsed tokens should be added
     *
     * @return {@code} true if the header was parsed cleanly, otherwise
     *         {@code false} (e.g. if a non-token value was encountered)
     *
     * @throws IOException If an I/O error occurs reading the header
     */
    public static boolean parseTokenList(Reader input, Collection<String> collection) throws IOException {
        boolean invalid = false;
        boolean valid = false;

        do {
            String fieldName = HttpParser.readToken(input);
            if (fieldName == null) {
                // Invalid field-name, skip to the next one
                invalid = true;
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
                valid = true;
                collection.add(fieldName.toLowerCase(Locale.ENGLISH));
                break;
            } else if (skipResult == SkipResult.FOUND) {
                valid = true;
                collection.add(fieldName.toLowerCase(Locale.ENGLISH));
                continue;
            } else {
                // Not a token - ignore it
                invalid = true;
                HttpParser.skipUntil(input, 0, ',');
                continue;
            }
        } while (true);

        // Only return true if at least one valid token was read and no invalid
        // entries were found
        return valid && !invalid;
    }
}
