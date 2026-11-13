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

/**
 * Utility class for parsing and comparing HTTP entity tags (ETags) as defined in RFC 7232.
 */
public class EntityTag {

    /**
     * Default constructor. This class is not intended to be instantiated.
     */
    public EntityTag() {
        // NO-OP
    }

    /**
     * Parse the given input as (per RFC 9110) #entity-tag. Compare an ETag header with a resource ETag as described in
     * RFC 9110 section 8.8.3.2.
     *
     * @param input        The input to parse
     * @param compareWeak  Use weak comparison e.g. match "etag" with W/"etag"
     * @param resourceETag Resource's ETag
     *
     * @return {@code true} if ETag matches, {@code false} if ETag doesn't match or {@code null} if the input is invalid
     *
     * @throws IOException If an I/O occurs during the parsing
     */
    public static Boolean compareEntityTag(StringReader input, boolean compareWeak, String resourceETag)
            throws IOException {

        Boolean result = Boolean.FALSE;

        // The resourceETag may be weak so to do weak comparison remove /W
        // before comparison
        String comparisonETag;
        if (resourceETag == null) {
            comparisonETag = null;
        } else if (compareWeak && resourceETag.startsWith("W/")) {
            comparisonETag = resourceETag.substring(2);
        } else {
            comparisonETag = resourceETag;
        }

        while (true) {
            boolean strong = false;
            // Empty list elements must be ignored (RFC 9110, section 5.6.1.2)
            while (HttpParser.skipConstant(input, ",") == SkipResult.FOUND) {
                // Skip
            }

            switch (HttpParser.skipConstant(input, "W/")) {
                case EOF:
                    // A list that contains no entity-tag is valid but nothing in it can match
                    return result;
                case NOT_FOUND:
                    strong = true;
                    break;
                case FOUND:
                    // Already set as strong = false;
                    break;
            }

            // Note: RFC 2616 allowed quoted string
            // RFC 7232 does not allow " in the entity-tag
            String value = HttpParser.readQuotedString(input, true);
            if (value == null) {
                // Not a quoted string so the header is invalid
                return null;
            }

            if (strong || compareWeak) {
                if (value.equals(comparisonETag)) {
                    result = Boolean.TRUE;
                }
            }

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
