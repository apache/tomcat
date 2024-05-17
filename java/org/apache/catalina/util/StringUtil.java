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

import java.util.regex.Pattern;

public class StringUtil {
    /**
     * {@link Pattern} for a comma delimited string that support whitespace characters
     */
    private static final Pattern commaSeparatedValuesPattern = Pattern.compile("\\s*,\\s*");

    /**
     * Splits a comma-separated string into an array of String values. Whitespace around the commas is removed. Null or
     * empty values will return a zero-element array.
     *
     * @param s The string to split by commas.
     *
     * @return An array of String values.
     */
    public static String[] splitCommaSeparated(String s) {
        return (s == null || s.length() == 0) ? new String[0] : commaSeparatedValuesPattern.split(s);

    }
}