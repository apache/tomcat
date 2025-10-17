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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TE {

    private final String encoding;
    private final Map<String,String> parameters;
    private final double quality;

    protected TE(String encoding, Map<String,String> parameters, double quality) {
        this.encoding = encoding;
        this.parameters = parameters;
        this.quality = quality;
    }

    public String getEncoding() {
        return encoding;
    }

    public Map<String,String> getParameters() {
        return parameters;
    }

    public double getQuality() {
        return quality;
    }


    public static List<TE> parse(StringReader input) throws IOException {

        List<TE> result = new ArrayList<>();

        do {
            String encoding = HttpParser.readToken(input);
            if (encoding == null) {
                // Invalid encoding, skip to the next one
                HttpParser.skipUntil(input, 0, ',');
                continue;
            }

            if (encoding.isEmpty()) {
                // No more data to read
                break;
            }

            Map<String,String> parameters = new HashMap<>();

            // Parse parameters (including optional quality)
            while (HttpParser.skipConstant(input, ";") == SkipResult.FOUND) {
                String name = HttpParser.readToken(input);
                String value = null;
                if (HttpParser.skipConstant(input, "=") == SkipResult.FOUND) {
                    value = HttpParser.readTokenOrQuotedString(input, true);
                }
                if (name != null && value != null) {
                    parameters.put(name, value);
                }
            }

            // See if a quality has been provided in the parameters
            double quality = 1;
            String q = parameters.remove("q");
            if (q != null) {
                quality = HttpParser.readWeight(new StringReader("q=" + q), ',');
            }

            if (quality > 0) {
                result.add(new TE(encoding, parameters, quality));
            }
        } while (true);

        return result;
    }
}
