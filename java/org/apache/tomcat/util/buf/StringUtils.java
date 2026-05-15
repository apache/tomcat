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
 * Utility methods to build a separated list from a given set (not java.util.Set) of inputs and return that list as a
 * string or append it to an existing StringBuilder. If the given set is null or empty, an empty string will be
 * returned.
 */
public final class StringUtils {

    private static final String EMPTY_STRING = "";

    private StringUtils() {
        // Utility class
    }


    /**
     * Joins two strings with a comma separator.
     *
     * @param a the first string
     * @param b the second string
     * @return the joined string
     */
    public static String join(String a, String b) {
        return join(new String[] { a, b });
    }


    /**
     * Joins the elements of a string array with a comma separator.
     *
     * @param array the array of strings to join
     * @return the joined string
     */
    public static String join(String[] array) {
        if (array == null) {
            return EMPTY_STRING;
        }
        return join(Arrays.asList(array));
    }


    /**
     * Joins the elements of a string array with the specified separator
     * and appends the result to the given StringBuilder.
     *
     * @param array the array of strings to join
     * @param separator the separator character
     * @param sb the StringBuilder to append to
     */
    public static void join(String[] array, char separator, StringBuilder sb) {
        if (array == null) {
            return;
        }
        join(Arrays.asList(array), separator, sb);
    }


    /**
     * Joins the elements of a collection with a comma separator.
     *
     * @param collection the collection of strings to join
     * @return the joined string
     */
    public static String join(Collection<String> collection) {
        return join(collection, ',');
    }


    /**
     * Joins the elements of a collection with the specified separator.
     *
     * @param collection the collection of strings to join
     * @param separator the separator character
     * @return the joined string
     */
    public static String join(Collection<String> collection, char separator) {
        // Shortcut
        if (collection == null || collection.isEmpty()) {
            return EMPTY_STRING;
        }

        StringBuilder result = new StringBuilder();
        join(collection, separator, result);
        return result.toString();
    }


    /**
     * Joins the elements of an iterable with the specified separator
     * and appends the result to the given StringBuilder.
     *
     * @param iterable the iterable of strings to join
     * @param separator the separator character
     * @param sb the StringBuilder to append to
     */
    public static void join(Iterable<String> iterable, char separator, StringBuilder sb) {
        join(iterable, separator, (x) -> x, sb);
    }


    /**
     * Joins the elements of an array using the specified separator and function,
     * and appends the result to the given StringBuilder.
     *
     * @param array the array to join
     * @param separator the separator character
     * @param function the function to apply to each element
     * @param sb the StringBuilder to append to
     * @param <T> the element type
     */
    public static <T> void join(T[] array, char separator, Function<T,String> function, StringBuilder sb) {
        if (array == null) {
            return;
        }
        join(Arrays.asList(array), separator, function, sb);
    }


    /**
     * Joins the elements of an iterable using the specified separator and function,
     * and appends the result to the given StringBuilder.
     *
     * @param iterable the iterable to join
     * @param separator the separator character
     * @param function the function to apply to each element
     * @param sb the StringBuilder to append to
     * @param <T> the element type
     */
    public static <T> void join(Iterable<T> iterable, char separator, Function<T,String> function, StringBuilder sb) {
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

    /**
     * Splits a comma-separated string into an array of String values. Whitespace around the commas is removed. Null or
     * empty values will return a zero-element array.
     *
     * @param s The string to split by commas.
     *
     * @return An array of String values.
     */
    public static String[] splitCommaSeparated(String s) {
        if (s == null || s.isEmpty()) {
            return new String[0];
        }

        String[] splits = s.split(",");
        for (int i = 0; i < splits.length; ++i) {
            splits[i] = splits[i].trim();
        }

        return splits;
    }
}
