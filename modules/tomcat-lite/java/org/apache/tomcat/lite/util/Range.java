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

package org.apache.tomcat.lite.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 * Utils to process HTTP/1.1 ranges. Used by default servlet, could
 * be used by any servlet that needs to deal with ranges.
 *
 * It is very good to support ranges if you have large content. In most
 * cases supporting one range is enough - getting multiple ranges doesn't
 * seem very common, and it's complex (multipart response).
 *
 * @author Costin Manolache
 * @author Remy Maucherat
 * @author - see DefaultServlet in Catalin for other contributors
 */
public class Range {

    public long start;
    public long end;
    public long length;

    /**
     * Validate range.
     */
    public boolean validate() {
        if (end >= length)
            end = length - 1;
        return ( (start >= 0) && (end >= 0) && (start <= end)
                 && (length > 0) );
    }

    public void recycle() {
        start = 0;
        end = 0;
        length = 0;
    }

    /** Parse ranges.
     *
     * @return null if the range is invalid or can't be parsed
     */
    public static ArrayList parseRanges(long fileLength,
                                        String rangeHeader) throws IOException {
        ArrayList result = new ArrayList();
        StringTokenizer commaTokenizer = new StringTokenizer(rangeHeader, ",");

        // Parsing the range list
        while (commaTokenizer.hasMoreTokens()) {
            String rangeDefinition = commaTokenizer.nextToken().trim();

            Range currentRange = new Range();
            currentRange.length = fileLength;

            int dashPos = rangeDefinition.indexOf('-');

            if (dashPos == -1) {
                return null;
            }

            if (dashPos == 0) {
                try {
                    long offset = Long.parseLong(rangeDefinition);
                    currentRange.start = fileLength + offset;
                    currentRange.end = fileLength - 1;
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {

                try {
                    currentRange.start = Long.parseLong
                        (rangeDefinition.substring(0, dashPos));
                    if (dashPos < rangeDefinition.length() - 1)
                        currentRange.end = Long.parseLong
                            (rangeDefinition.substring
                             (dashPos + 1, rangeDefinition.length()));
                    else
                        currentRange.end = fileLength - 1;
                } catch (NumberFormatException e) {
                    return null;
                }

            }
            if (!currentRange.validate()) {
                return null;
            }
            result.add(currentRange);
        }
        return result;
    }


    /**
     * Parse the Content-Range header. Used with PUT or in response.
     *
     * @return Range
     */
    public static Range parseContentRange(String rangeHeader)
            throws IOException {
        if (rangeHeader == null)
            return null;

        // bytes is the only range unit supported
        if (!rangeHeader.startsWith("bytes")) {
            return null;
        }

        rangeHeader = rangeHeader.substring(6).trim();

        int dashPos = rangeHeader.indexOf('-');
        int slashPos = rangeHeader.indexOf('/');

        if (dashPos == -1) {
            return null;
        }

        if (slashPos == -1) {
            return null;
        }

        Range range = new Range();

        try {
            range.start = Long.parseLong(rangeHeader.substring(0, dashPos));
            range.end =
                Long.parseLong(rangeHeader.substring(dashPos + 1, slashPos));
            range.length = Long.parseLong
                (rangeHeader.substring(slashPos + 1, rangeHeader.length()));
        } catch (NumberFormatException e) {
            return null;
        }

        if (!range.validate()) {
            return null;
        }

        return range;
    }

}