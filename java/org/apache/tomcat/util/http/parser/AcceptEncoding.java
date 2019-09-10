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
import java.util.List;

import org.apache.tomcat.util.http.parser.HttpParser.SkipResult;

public class AcceptEncoding {

    private final String encoding;
    private final double quality;

    protected AcceptEncoding(String encoding, double quality) {
        this.encoding = encoding;
        this.quality = quality;
    }

    public String getEncoding() {
        return encoding;
    }

    public double getQuality() {
        return quality;
    }


    public static List<AcceptEncoding> parse(StringReader input) throws IOException {

        List<AcceptEncoding> result = new ArrayList<AcceptEncoding>();

        do {
            String encoding = HttpParser.readToken(input);
            if (encoding == null) {
                // Invalid encoding, skip to the next one
                HttpParser.skipUntil(input, 0, ',');
                continue;
            }

            if (encoding.length() == 0) {
                // No more data to read
                break;
            }

            // See if a quality has been provided
            double quality = 1;
            SkipResult lookForSemiColon = HttpParser.skipConstant(input, ";");
            if (lookForSemiColon == SkipResult.FOUND) {
                quality = HttpParser.readWeight(input, ',');
            }

            if (quality > 0) {
                result.add(new AcceptEncoding(encoding, quality));
            }
        } while (true);

        return result;
    }
}
