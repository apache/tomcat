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
package org.apache.catalina.util;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.ToLongFunction;

import org.apache.catalina.Session;

/**
 * Utility class for building {@link Comparator}s over {@link Session}s.
 */
public class SessionComparators {

    private SessionComparators() {
        // Utility class. Hide the default constructor.
    }


    /**
     * Builds a {@link Comparator} equivalent to {@link Comparator#comparingLong(ToLongFunction)}, except that each
     * session's extracted value is only read once and then cached for the remainder of the sort. Some values used to
     * sort sessions (e.g. last accessed time, or values derived from the current time) can change while a sort is in
     * progress, either because the session is concurrently accessed or simply because time passes. Without caching,
     * that can make the comparator return inconsistent results for the same pair of sessions across different
     * comparisons in the same sort, which trips {@code java.util.TimSort}'s consistency check.
     *
     * @param keyExtractor Function that extracts the sort key from a session
     *
     * @return a comparator that sorts sessions by the (cached) extracted key
     */
    public static Comparator<Session> comparingLongSnapshot(ToLongFunction<Session> keyExtractor) {
        Map<Session,Long> cache = new IdentityHashMap<>();
        return Comparator.comparingLong(s -> cache.computeIfAbsent(s, keyExtractor::applyAsLong).longValue());
    }
}
