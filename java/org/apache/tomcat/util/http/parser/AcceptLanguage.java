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
import java.util.Locale;

/**
 * Represents a single language entry from an Accept-Language header.
 */
public class AcceptLanguage {

    private final Locale locale;
    private final double quality;

    /**
     * Constructs a new AcceptLanguage.
     *
     * @param locale The locale of this language entry
     * @param quality The quality value for this language entry
     */
    protected AcceptLanguage(Locale locale, double quality) {
        this.locale = locale;
        this.quality = quality;
    }

    /**
     * Returns the locale.
     *
     * @return The locale of this language entry
     */
    public Locale getLocale() {
        return locale;
    }

    /**
     * Returns the quality value.
     *
     * @return The quality value of this language entry
     */
    public double getQuality() {
        return quality;
    }


    /**
     * Parses an Accept-Language header value.
     *
     * @param input The StringReader containing the header value
     * @return A list of AcceptLanguage entries sorted by quality
     * @throws IOException If an I/O error occurs while reading the input
     */
    public static List<AcceptLanguage> parse(StringReader input) throws IOException {

        List<AcceptLanguage> result = new ArrayList<>();

        do {
            // Token is broader than what is permitted in a language tag
            // (alphanumeric + '-') but any invalid values that slip through
            // will be caught later
            String languageTag = HttpParser.readToken(input);
            if (languageTag == null) {
                // Invalid tag, skip to the next one
                HttpParser.skipUntil(input, 0, ',');
                continue;
            }

            if (languageTag.isEmpty()) {
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
                result.add(new AcceptLanguage(Locale.forLanguageTag(languageTag), quality));
            }
        } while (true);

        return result;
    }
}
