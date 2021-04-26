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
package org.apache.tomcat.util.http;

public class HeaderUtil {

    /**
     * Converts an HTTP header line in byte form to a printable String.
     * Bytes corresponding to visible ASCII characters will converted to those
     * characters. All other bytes (0x00 to 0x1F, 0x7F to OxFF) will be
     * represented in 0xNN form.
     *
     * @param bytes  Contains an HTTP header line
     * @param offset The start position of the header line in the array
     * @param len    The length of the HTTP header line
     *
     * @return A String with non-printing characters replaced by the 0xNN
     *         equivalent
     */
    public static String toPrintableString(byte[] bytes, int offset, int len) {
        StringBuilder result = new StringBuilder();
        for (int i = offset; i < offset + len; i++) {
            char c = (char) (bytes[i] & 0xFF);
            if (c < 0x20 || c > 0x7E) {
                result.append("0x");
                result.append(Character.forDigit((c >> 4) & 0xF, 16));
                result.append(Character.forDigit((c) & 0xF, 16));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }


    private HeaderUtil() {
        // Utility class. Hide default constructor.
    }
}
