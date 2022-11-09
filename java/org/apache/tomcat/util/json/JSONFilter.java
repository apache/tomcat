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
package org.apache.tomcat.util.json;

/**
 * Provides escaping of values so they can be included in a JSON document.
 * Escaping is based on the definition of JSON found in
 * <a href="https://www.rfc-editor.org/rfc/rfc8259.html">RFC 8259</a>.
 */
public class JSONFilter {

    private JSONFilter() {
        // Utility class. Hide the default constructor.
    }

    public static String escape(String input) {
        /*
         * While any character MAY be escaped, only U+0000 to U+001F (control
         * characters), U+0022 (quotation mark) and U+005C (reverse solidus)
         * MUST be escaped.
         */
        char[] chars = input.toCharArray();
        StringBuffer escaped = null;
        int lastUnescapedStart = 0;
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] < 0x20 || chars[i] == 0x22 || chars[i] == 0x5c) {
                if (escaped == null) {
                    escaped = new StringBuffer(chars.length + 20);
                }
                if (lastUnescapedStart < i) {
                    escaped.append(input.subSequence(lastUnescapedStart, i));
                }
                lastUnescapedStart = i + 1;
                escaped.append("\\u");
                escaped.append(String.format("%04X", Integer.valueOf(chars[i])));
            }
        }
        if (escaped == null) {
            return input;
        } else {
            if (lastUnescapedStart < chars.length) {
                escaped.append(input.subSequence(lastUnescapedStart, chars.length));
            }
            return escaped.toString();
        }
    }
}
