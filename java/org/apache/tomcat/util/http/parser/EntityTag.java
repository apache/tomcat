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

public class EntityTag {

    /**
     * Parse the given input as (per RFC 7232) 1#entity-tag.
     * Compare an ETag header with a resource ETag as described in RFC 7232
     * section 2.3.2.
     *
     * @param input        The input to parse
     * @param compareWeak  Use weak comparison e.g. match "etag" with W/"etag"
     * @param resourceETag Resource's ETag
     *
     * @return {@code true} if ETag matches, {@code false} if ETag doesn't match
     *         or {@code null} if the input is invalid
     *
     * @throws IOException If an I/O occurs during the parsing
     */
    public static Boolean compareEntityTag(StringReader input, boolean compareWeak, String resourceETag)
            throws IOException {
        // The resourceETag may be weak so to do weak comparison remove /W
        // before comparison
        String comparisonETag;
        if (compareWeak && resourceETag.startsWith("W/")) {
            comparisonETag = resourceETag.substring(2);
        } else {
            comparisonETag = resourceETag;
        }

        Boolean result = Boolean.FALSE;

        while (true) {
            boolean strong = false;
            HttpParser.skipLws(input);

            switch (HttpParser.skipConstant(input, "W/")) {
                case EOF:
                    // Empty values are invalid
                    return null;
                case NOT_FOUND:
                    strong = true;
                    break;
                case FOUND:
                    strong = false;
                    break;
            }

            // Note: RFC 2616 allowed quoted string
            //       RFC 7232 does not allow " in the entity-tag
            String value = HttpParser.readQuotedString(input, true);
            if (value == null) {
                // Not a quoted string so the header is invalid
                return null;
            }

            if (strong || compareWeak) {
                if (comparisonETag.equals(value)) {
                    result = Boolean.TRUE;
                }
            }

            HttpParser.skipLws(input);

            switch (HttpParser.skipConstant(input, ",")) {
                case EOF:
                    return result;
                case NOT_FOUND:
                    // Not EOF and not "," so must be invalid
                    return null;
                case FOUND:
                    // Parse next entry
                    break;
            }
        }
    }
}
