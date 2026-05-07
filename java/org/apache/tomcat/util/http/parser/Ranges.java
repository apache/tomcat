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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Represents the value of an HTTP Range header.
 */
public class Ranges {

    private final String units;
    private final List<Entry> entries;


    /**
     * Creates a new Ranges instance.
     *
     * @param units the range units (e.g., "bytes"), or null
     * @param entries the list of range entries
     */
    public Ranges(String units, List<Entry> entries) {
        // Units are lower case (RFC 9110, section 14.1)
        if (units == null) {
            this.units = null;
        } else {
            this.units = units.toLowerCase(Locale.ENGLISH);
        }
        this.entries = Collections.unmodifiableList(entries);
    }


    /**
     * Returns the list of range entries.
     *
     * @return an unmodifiable list of range entries
     */
    public List<Entry> getEntries() {
        return entries;
    }

    /**
     * Returns the range units (e.g., "bytes").
     *
     * @return the range units, or null if not specified
     */
    public String getUnits() {
        return units;
    }


    /**
     * Represents a single range entry with a start and end position.
     */
    public static class Entry {

        private final long start;
        private final long end;


        /**
         * Creates a new range entry.
         *
         * @param start the start position of the range
         * @param end the end position of the range, or -1 if absent
         */
        public Entry(long start, long end) {
            this.start = start;
            this.end = end;
        }


        /**
         * Returns the start position of the range.
         *
         * @return the start position
         */
        public long getStart() {
            return start;
        }


        /**
         * Returns the end position of the range.
         *
         * @return the end position, or -1 if absent
         */
        public long getEnd() {
            return end;
        }
    }


    /**
     * Parses a Range header from an HTTP header.
     *
     * @param input a reader over the header text
     *
     * @return a set of ranges parsed from the input, or null if not valid
     *
     * @throws IOException if there was a problem reading the input
     */
    public static Ranges parse(StringReader input) throws IOException {

        // Units (required)
        String units = HttpParser.readToken(input);
        if (units == null || units.isEmpty()) {
            return null;
        }

        // Must be followed by '='
        if (HttpParser.skipConstant(input, "=") != SkipResult.FOUND) {
            return null;
        }

        // Range entries
        List<Entry> entries = new ArrayList<>();

        SkipResult skipResult;
        do {
            long start = HttpParser.readLong(input);
            // Must be followed by '-'
            if (HttpParser.skipConstant(input, "-") != SkipResult.FOUND) {
                return null;
            }
            long end = HttpParser.readLong(input);

            if (start == -1 && end == -1) {
                // Invalid range
                return null;
            }

            entries.add(new Entry(start, end));

            skipResult = HttpParser.skipConstant(input, ",");
            if (skipResult == SkipResult.NOT_FOUND) {
                // Invalid range
                return null;
            }
        } while (skipResult == SkipResult.FOUND);

        return new Ranges(units, entries);
    }
}
