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
 * Parser for {@link AcceptLanguage} that parses an Accept-Language header
 * value using the deprecated {@link Locale#Locale(String, String, String)}
 * constructor, combined with a custom BCP 47 subtag parser that maps
 * the language tag components onto the language/country/variant triple that
 * the deprecated constructor accepts. This is done for better efficiency.
 * <p>
 * Known, accepted degradations compared with
 * {@code Locale.forLanguageTag()}:
 * <ul>
 *   <li>Unicode, other and private use extensions (e.g. {@code -u-ca-buddhist},
 *       {@code -x-priv}) are discarded rather than recorded on the Locale.</li>
 *   <li>Script subtags (e.g. {@code -Hant-}) cannot be represented by the
 *       deprecated constructor and are mapped to the Locale variant, in upper
 *       case, instead of to the Locale script.</li>
 *   <li>Tags that are syntactically invalid after the language subtag retain
 *   the components parsed up to that point rather than being replaced with
 *   {@link Locale#ROOT}.</li>
 * </ul>
 */
@SuppressWarnings("javadoc")
public final class AcceptLanguage2 {

    private AcceptLanguage2() {
        // Utility class
    }

    /**
     * Parses an Accept-Language header value, reading at most the specified
     * number of entries. See
     * {@link AcceptLanguage#parse(StringReader, int)}.
     *
     * @param input       The StringReader containing the header value
     * @param maxElements The maximum number of entries to parse or
     *                    <code>-1</code> for no limit
     *
     * @return A list of AcceptLanguage entries. If the limit was reached, the
     *         returned list has exactly <code>maxElements</code> entries and
     *         any remaining entries were not parsed.
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
            // are treated as Locale.ROOT by toLegacyLocale(), matching
            // Locale.forLanguageTag().
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
                result.add(new AcceptLanguage(toLegacyLocale(languageTag), quality));
            }
        } while (true);

        return result;
    }


    /**
     * Maps a BCP 47 language tag onto a Locale using the deprecated Locale
     * constructor.
     * <p>
     * Component mapping: the primary subtag becomes the language, the region
     * subtag (2 alpha or 3 digits) becomes the country, the script subtag (4
     * alpha) is upper-cased and mapped to the variant, any other variant
     * subtags are appended to the variant unchanged (variant values are case
     * sensitive in Locale) with '_' as separator, and extension/private use
     * subtags are dropped.
     *
     * @param tag The language tag
     *
     * @return The Locale for the tag, or {@link Locale#ROOT} if the tag is
     *         syntactically invalid
     */
    @SuppressWarnings("deprecation")
    static Locale toLegacyLocale(String tag) {
        int len = tag.length();
        if (len == 0) {
            return Locale.ROOT;
        }

        // Primary subtag: 2 to 8 ASCII letters, case insensitive
        int pos = 0;
        int end = tag.indexOf('-', 0);
        if (end < 0) {
            end = len;
        }
        int primaryLen = end;
        if (primaryLen < 2 || primaryLen > 8 || !allAsciiAlpha(tag, 0, end)) {
            return Locale.ROOT;
        }
        String language = lower(tag, 0, end);

        String country = "";
        StringBuilder variant = new StringBuilder();
        boolean scriptSeen = false;
        boolean countrySeen = false;

        pos = end;
        while (pos < len) {
            // Skip the '-'
            pos++;
            end = tag.indexOf('-', pos);
            if (end < 0) {
                end = len;
            }
            int subLen = end - pos;
            if (subLen == 0) {
                // Empty subtag, malformed, drop the remainder
                break;
            }

            if (subLen == 2 && !countrySeen && allAsciiAlpha(tag, pos, end)) {
                // Region subtag (alpha-2)
                country = upper(tag, pos, end);
                countrySeen = true;
            } else if (subLen == 3 && !countrySeen && allAsciiDigits(tag, pos, end)) {
                // Region subtag (numeric-3)
                country = tag.substring(pos, end);
                countrySeen = true;
            } else if (subLen == 4 && !scriptSeen && !countrySeen && allAsciiAlpha(tag, pos, end)) {
                // Script subtag. The deprecated constructor has no script
                // component so it is mapped to the variant instead. The
                // subtag is upper-cased to clearly distinguish it from
                // regular variant subtags which are kept unchanged.
                appendVariant(variant, upper(tag, pos, end));
                scriptSeen = true;
            } else if (isVariantSubtag(tag, pos, end, subLen)) {
                // Variant subtag. Locale variants are case sensitive and
                // Locale.forLanguageTag() does not change their case, so
                // this subtag is used unchanged.
                appendVariant(variant, tag.substring(pos, end));
            } else if (subLen == 1) {
                // Singleton (extension or private use sequence), drop the
                // remainder
                break;
            } else {
                // Unexpected subtag, drop the remainder
                break;
            }

            pos = end;
        }

        return new Locale(language, country, variant.toString());
    }


    private static void appendVariant(StringBuilder variant, String subtag) {
        // Bound the variant size. The header size limit already bounds this
        // indirectly but be explicit.
        if (variant.length() + subtag.length() + 1 <= 64) {
            if (variant.length() > 0) {
                variant.append('_');
            }
            variant.append(subtag);
        }
    }


    /*
     * BCP 47 variant subtag: 5 to 8 alphanumeric characters, or 4 characters
     * where the first is a digit and the rest are alphanumeric.
     */
    private static boolean isVariantSubtag(String tag, int start, int end, int subLen) {
        if (subLen >= 5 && subLen <= 8) {
            return allAsciiAlnum(tag, start, end);
        }
        if (subLen == 4) {
            char first = tag.charAt(start);
            if (first < '0' || first > '9') {
                return false;
            }
            return allAsciiAlnum(tag, start + 1, end);
        }
        return false;
    }


    private static String lower(String tag, int start, int end) {
        StringBuilder sb = new StringBuilder(end - start);
        for (int i = start; i < end; i++) {
            char c = tag.charAt(i);
            sb.append(c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c);
        }
        return sb.toString();
    }


    private static String upper(String tag, int start, int end) {
        StringBuilder sb = new StringBuilder(end - start);
        for (int i = start; i < end; i++) {
            char c = tag.charAt(i);
            sb.append(c >= 'a' && c <= 'z' ? (char) (c - ('a' - 'A')) : c);
        }
        return sb.toString();
    }


    private static boolean allAsciiAlpha(String tag, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = tag.charAt(i);
            if ((c < 'a' || c > 'z') && (c < 'A' || c > 'Z')) {
                return false;
            }
        }
        return true;
    }


    private static boolean allAsciiDigits(String tag, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = tag.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }


    private static boolean allAsciiAlnum(String tag, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = tag.charAt(i);
            if ((c < 'a' || c > 'z') && (c < 'A' || c > 'Z') && (c < '0' || c > '9')) {
                return false;
            }
        }
        return true;
    }
}
