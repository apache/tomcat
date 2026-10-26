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
import java.util.function.Function;

/**
 * Represents a single language entry from an Accept-Language header.
 */
public class AcceptLanguage {

    private final String languageTag;
    private final Function<String,Locale> localeFactory;
    private final double quality;
    private Locale locale;

    /**
     * Constructs a new AcceptLanguage.
     *
     * @param locale The locale of this language entry
     * @param quality The quality value for this language entry
     */
    protected AcceptLanguage(Locale locale, double quality) {
        this.languageTag = null;
        this.localeFactory = null;
        this.quality = quality;
        this.locale = locale;
    }

    /**
     * Constructs a new AcceptLanguage for which the Locale is only created
     * when it is first requested. This allows callers to select entries based
     * on the quality value without creating Locale objects for the entries
     * that are not used.
     *
     * @param languageTag   The language tag of this language entry
     * @param quality       The quality value for this language entry
     * @param localeFactory The function used to create the Locale from the
     *                          language tag
     */
    AcceptLanguage(String languageTag, double quality, Function<String,Locale> localeFactory) {
        this.languageTag = languageTag;
        this.localeFactory = localeFactory;
        this.quality = quality;
    }

    /**
     * Returns the locale.
     *
     * @return The locale of this language entry
     */
    public Locale getLocale() {
        Locale result = locale;
        if (result == null && localeFactory != null) {
            result = localeFactory.apply(languageTag);
            locale = result;
        }
        return result;
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
     * @return A list of AcceptLanguage entries in the order they appear in the header
     * @throws IOException If an I/O error occurs while reading the input
     */
    public static List<AcceptLanguage> parse(StringReader input) throws IOException {
        return parse(input, -1);
    }


    /**
     * Parses an Accept-Language header value, reading at most the specified
     * number of entries. Parsing stops as soon as the maximum number of
     * entries has been read. This limits the number of Locale objects created
     * when the caller has no use for entries beyond the limit (e.g. when a
     * container level limit means the request is going to be rejected).
     *
     * @param input       The StringReader containing the header value
     * @param maxElements The maximum number of entries to parse or
     *                    <code>-1</code> for no limit
     *
     * @return A list of AcceptLanguage entries sorted by quality. If the limit
     *         was reached, the returned list has exactly
     *         <code>maxElements</code> entries and any remaining entries were
     *         not parsed.
     *
     * @throws IOException If an I/O error occurs while reading the input
     */
    public static List<AcceptLanguage> parse(StringReader input, int maxElements) throws IOException {

        List<AcceptLanguage> result = new ArrayList<>();

        do {
            if (maxElements >= 0 && result.size() >= maxElements) {
                // Enough entries have been read. Do not parse any further
                // entries to avoid unnecessary object creation.
                break;
            }

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
                result.add(new AcceptLanguage(languageTag, quality, Locale::forLanguageTag));
            }
        } while (true);

        return result;
    }
}
