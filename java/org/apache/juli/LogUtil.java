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
package org.apache.juli;

public class LogUtil {

    private LogUtil() {
        // Utility class. Hide default constructor
    }


    /**
     * Escape a string so it can be displayed in a readable format. Characters that may not be printable in some/all of
     * the contexts in which log messages will be viewed will be escaped using Java \\uNNNN escaping.
     * <p>
     * All control characters are escaped apart from horizontal tab (\\u0009), new line (\\u000a) and carriage return
     * (\\u000d).
     *
     * @param input The string to escape
     *
     * @return The escaped form of the input string
     */
    @SuppressWarnings("null") // sb is not null when used
    public static String escape(final String input) {
        final int len = input.length();
        int i = 0;
        int lastControl = -1;
        StringBuilder sb = null;
        while (i < len) {
            char c = input.charAt(i);
            if (Character.getType(c) == Character.CONTROL) {
                if (!(c == '\t' || c == '\n' || c == '\r')) {
                    if (lastControl == -1) {
                        sb = new StringBuilder(len + 20);
                    }
                    sb.append(input.substring(lastControl + 1, i));
                    sb.append(String.format("\\u%1$04x", Integer.valueOf(c)));
                    lastControl = i;
                }
            }
            i++;
        }
        if (lastControl == -1) {
            return input;
        } else {
            sb.append(input.substring(lastControl + 1, len));
            return sb.toString();
        }
    }
}
