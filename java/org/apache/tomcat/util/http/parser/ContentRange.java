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

public class ContentRange {

    private final String units;
    private final long start;
    private final long end;
    private final long length;


    public ContentRange(String units, long start, long end, long length) {
        this.units = units;
        this.start = start;
        this.end = end;
        this.length = length;
    }


    public String getUnits() {
        return units;
    }


    public long getStart() {
        return start;
    }


    public long getEnd() {
        return end;
    }


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
        if (units == null || units.length() == 0) {
            return null;
        }

        // Must be followed by '='
        if (HttpParser.skipConstant(input, "=") == SkipResult.NOT_FOUND) {
            return null;
        }

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

        return new ContentRange(units, start, end, length);
    }
}
