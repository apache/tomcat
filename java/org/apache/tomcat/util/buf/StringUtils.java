/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.buf;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;

/**
 * Utility methods to build a separated list from a given set (not
 * java.util.Set) of inputs and return that list as a string or append it to an
 * existing StringBuilder. If the given set is null or empty, an empty string
 * will be returned.
 */
public final class StringUtils {

    private static final String EMPTY_STRING = "";

    private StringUtils() {
        // Utility class
    }


    public static String join(String[] array) {
        if (array == null) {
            return EMPTY_STRING;
        }
        return join(Arrays.asList(array));
    }


    public static void join(String[] array, char separator, StringBuilder sb) {
        if (array == null) {
            return;
        }
        join(Arrays.asList(array), separator, sb);
    }


    public static String join(Collection<String> collection) {
        return join(collection, ',');
    }


    public static String join(Collection<String> collection, char separator) {
        // Shortcut
        if (collection == null || collection.isEmpty()) {
            return EMPTY_STRING;
        }

        StringBuilder result = new StringBuilder();
        join(collection, separator, result);
        return result.toString();
    }


    public static void join(Iterable<String> iterable, char separator, StringBuilder sb) {
        join(iterable, separator, (x) -> x, sb);
    }


    public static <T> void join(T[] array, char separator, Function<T,String> function,
            StringBuilder sb) {
        if (array == null) {
            return;
        }
        join(Arrays.asList(array), separator, function, sb);
    }


    public static <T> void join(Iterable<T> iterable, char separator, Function<T,String> function,
            StringBuilder sb) {
        if (iterable == null) {
            return;
        }
        boolean first = true;
        for (T value : iterable) {
            if (first) {
                first = false;
            } else {
                sb.append(separator);
            }
            sb.append(function.apply(value));
        }
    }
}
