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
package org.apache.tomcat.util.http.parser;

import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;

/**
 * Represents a parsed Content-Range HTTP header value as defined by RFC 9110.
 */
public class ContentRange {

    private final String units;
    private final long start;
    private final long end;
    private final long length;


    /**
     * Creates a new ContentRange with the specified values.
     *
     * @param units  the range units (e.g., "bytes"), lowercased
     * @param start  the start position of the range
     * @param end    the end position of the range
     * @param length the total length of the resource
     */
    public ContentRange(String units, long start, long end, long length) {
        // Units are lower case (RFC 9110, section 14.1)
        if (units == null) {
            this.units = null;
        } else {
            this.units = units.toLowerCase(Locale.ENGLISH);
        }
        this.start = start;
        this.end = end;
        this.length = length;
    }


    /**
     * Returns the range units (e.g., "bytes") in lower case.
     *
     * @return the range units, or {@code null} if not set
     */
    public String getUnits() {
        return units;
    }


    /**
     * Returns the start position of the content range.
     *
     * @return the start position
     */
    public long getStart() {
        return start;
    }


    /**
     * Returns the end position of the content range.
     *
     * @return the end position
     */
    public long getEnd() {
        return end;
    }


    /**
     * Returns the total length of the resource.
     *
     * @return the total resource length
     */
    public long getLength() {
        return length;
    }


    /**
     * Parses a Content-Range header from an HTTP header.
     *
     * @param input a reader over the header text
     *
     * @return the range parsed from the input, or null if not valid
     *
     * @throws IOException if there was a problem reading the input
     */
    public static ContentRange parse(StringReader input) throws IOException {
        // Units (required)
        String units = HttpParser.readToken(input);
        if (units == null || units.isEmpty()) {
            return null;
        }

        // Must be followed by SP. Parser is lenient and accepts any LWS here.
        // No need for explicit check as something must have terminated the
        // token and if that something was anything other than LWS the following
        // call to readLong() will fail.

        // Start
        long start = HttpParser.readLong(input);

        // Must be followed by '-'
        if (HttpParser.skipConstant(input, "-") == SkipResult.NOT_FOUND) {
            return null;
        }

        // End
        long end = HttpParser.readLong(input);

        // Must be followed by '/'
        if (HttpParser.skipConstant(input, "/") == SkipResult.NOT_FOUND) {
            return null;
        }

        // Length
        long length = HttpParser.readLong(input);

        // Doesn't matter what we look for, result should be EOF
        SkipResult skipResult = HttpParser.skipConstant(input, "X");

        if (skipResult != SkipResult.EOF) {
            // Invalid range
            return null;
        }

        ContentRange contentRange = new ContentRange(units, start, end, length);
        if (!contentRange.isValid()) {
            // Invalid content range
            return null;
        }
        return contentRange;
    }


    /**
     * Validates this content range according to RFC 9110 section 14.4.
     *
     * @return {@code true} if start &gt;= 0, end &gt;= start, and length &gt; end
     */
    public boolean isValid() {
        return start >= 0 && end >= start && length > end;
    }
}
