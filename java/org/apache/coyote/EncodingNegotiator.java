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
package org.apache.coyote;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import org.apache.coyote.http11.filters.OutputFilterFactory;
import org.apache.tomcat.util.http.parser.AcceptEncoding;
import org.apache.tomcat.util.http.parser.TE;

/**
 * Utility class for negotiating transfer/content encodings using TE and
 * Accept-Encoding headers.
 */
public class EncodingNegotiator {

    /**
     * Selects the best {@link OutputFilterFactory} for the HTTP {@code TE} header.
     * <p>Rules:
     * - Prefer the entry with the highest quality (q) value; entries with {@code q <= 0} are ignored.
     * - Wildcard ({@code *}) matches any server factory.
     * - On quality ties, prefer the factory with the lowest server priority (its index in {@code factories}).
     * - Encoding name matching is case-insensitive.
     *
     * @param factories The available factories in server-priority order (index 0 is highest)
     * @param entries    The parsed {@code TE} header entries
     * @return The selected factory or {@code null} if no match
     */
    public static OutputFilterFactory negotiateTE(List<OutputFilterFactory> factories, List<TE> entries) {
        return negotiateEncodings(factories, entries, TE::getEncoding, TE::getQuality);
    }

    /**
     * Selects the best {@link OutputFilterFactory} for the HTTP {@code Accept-Encoding} header.
     * <p>Rules:
     * - Prefer the entry with the highest quality (q) value; entries with {@code q <= 0} are ignored.
     * - Wildcard ({@code *}) matches any server factory.
     * - On quality ties, prefer the factory with the lowest server priority (its index in {@code factories}).
     * - Encoding name matching is case-insensitive.
     *
     * @param factories       The available factories in server-priority order (index 0 is highest)
     * @param acceptEncodings The parsed {@code Accept-Encoding} entries
     * @return The selected factory or {@code null} if no match
     */
    public static OutputFilterFactory negotiateAcceptEncoding(
            List<OutputFilterFactory> factories, List<AcceptEncoding> acceptEncodings) {
        return negotiateEncodings(factories, acceptEncodings, AcceptEncoding::getEncoding, AcceptEncoding::getQuality);
    }

    private static <T> OutputFilterFactory negotiateEncodings(
            List<OutputFilterFactory> factories,
            List<T> entries,
            Function<T,String> encodingFn,
            ToDoubleFunction<T> qualityFn) {
        OutputFilterFactory bestFactory = null;
        double bestQuality = 0;
        int bestServerPriority = Integer.MAX_VALUE;

        for (int i = 0; i < factories.size(); i++) {
            OutputFilterFactory factory = factories.get(i);
            String factoryEncoding = factory.getEncodingName().toLowerCase(Locale.ENGLISH);

            for (T entry : entries) {
                String entryEncoding = encodingFn.apply(entry).toLowerCase(Locale.ENGLISH);
                double quality = qualityFn.applyAsDouble(entry);

                if (quality <= 0) {
                    continue;
                }

                if (factoryEncoding.equals(entryEncoding) || "*".equals(entryEncoding)) {
                    if (quality > bestQuality || (quality == bestQuality && i < bestServerPriority)) {
                        bestFactory = factory;
                        bestQuality = quality;
                        bestServerPriority = i;
                    }
                }
            }
        }

        return bestFactory;
    }
}
