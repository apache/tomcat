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
import java.util.Locale;
import java.util.Set;

public class Vary {

    private Vary() {
        // Utility class. Hide default constructor.
    }


    public static void parseVary(StringReader input, Set<String> result) throws IOException {

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
                result.add(fieldName.toLowerCase(Locale.ENGLISH));
                break;
            } else if (skipResult == SkipResult.FOUND) {
                result.add(fieldName.toLowerCase(Locale.ENGLISH));
                continue;
            } else {
                // Not a token - ignore it
                HttpParser.skipUntil(input, 0, ',');
                continue;
            }
        } while (true);
    }
}
