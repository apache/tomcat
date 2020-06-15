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
package org.apache.tomcat.util.net;

/**
 * <p>IPv6 utilities.
 * <p>For the moment, it only contains function to canonicalize IPv6 address
 * into RFC 5952 form.
 */
public class IPv6Utils {

    private static final int MAX_NUMBER_OF_GROUPS = 8;
    private static final int MAX_GROUP_LENGTH = 4;

    /**
     * <p>Convert IPv6 address into RFC 5952 form.
     * E.g. 2001:db8:0:1:0:0:0:1 -&gt; 2001:db8:0:1::1</p>
     *
     * <p>Method is null safe, and if IPv4 address or host name is passed to the
     * method it is returned without any processing.</p>
     *
     * <p>Method also supports IPv4 in IPv6 (e.g. 0:0:0:0:0:ffff:192.0.2.1 -&gt;
     * ::ffff:192.0.2.1), and zone ID (e.g. fe80:0:0:0:f0f0:c0c0:1919:1234%4
     * -&gt; fe80::f0f0:c0c0:1919:1234%4).</p>
     *
     * <p>The behaviour of this method is undefined if an invalid IPv6 address
     * is passed in as input.</p>
     *
     * @param ipv6Address String representing valid IPv6 address.
     * @return String representing IPv6 in canonical form.
     * @throws IllegalArgumentException if IPv6 format is unacceptable.
     */
    public static String canonize(String ipv6Address) throws IllegalArgumentException {

        if (ipv6Address == null) {
            return null;
        }

        // Definitely not an IPv6, return untouched input.
        if (!mayBeIPv6Address(ipv6Address)) {
            return ipv6Address;
        }

        // Length without zone ID (%zone) or IPv4 address
        int ipv6AddressLength = ipv6Address.length();
        if (ipv6Address.contains(".")) {
            // IPv4 in IPv6
            // e.g. 0:0:0:0:0:FFFF:127.0.0.1
            int lastColonPos = ipv6Address.lastIndexOf(":");
            int lastColonsPos = ipv6Address.lastIndexOf("::");
            if (lastColonsPos >= 0 && lastColonPos == lastColonsPos + 1) {
                /*
                 *  IPv6 part ends with two consecutive colons,
                 *  last colon is part of IPv6 format.
                 *  e.g. ::127.0.0.1
                 */
                ipv6AddressLength = lastColonPos + 1;
            } else {
                /*
                 *  IPv6 part ends with only one colon,
                 *  last colon is not part of IPv6 format.
                 *  e.g. ::FFFF:127.0.0.1
                 */
                ipv6AddressLength = lastColonPos;
            }
        } else if (ipv6Address.contains("%")) {
            // Zone ID
            // e.g. fe80:0:0:0:f0f0:c0c0:1919:1234%4
            ipv6AddressLength = ipv6Address.lastIndexOf("%");
        }

        StringBuilder result = new StringBuilder();
        char [][] groups = new char[MAX_NUMBER_OF_GROUPS][MAX_GROUP_LENGTH];
        int groupCounter = 0;
        int charInGroupCounter = 0;

        // Index of the current zeroGroup, -1 means not found.
        int zeroGroupIndex = -1;
        int zeroGroupLength = 0;

        // maximum length zero group, if there is more then one, then first one
        int maxZeroGroupIndex = -1;
        int maxZeroGroupLength = 0;

        boolean isZero = true;
        boolean groupStart = true;

        /*
         *  Two consecutive colons, initial expansion.
         *  e.g. 2001:db8:0:0:1::1 -> 2001:db8:0:0:1:0:0:1
         */

        StringBuilder expanded = new StringBuilder(ipv6Address);
        int colonsPos = ipv6Address.indexOf("::");
        int length = ipv6AddressLength;
        int change = 0;

        if (colonsPos >= 0 && colonsPos < ipv6AddressLength - 1) {
            int colonCounter = 0;
            for (int i = 0; i < ipv6AddressLength; i++) {
                if (ipv6Address.charAt(i) == ':') {
                    colonCounter++;
                }
            }

            if (colonsPos == 0) {
                expanded.insert(0, "0");
                change = change + 1;
            }

            for (int i = 0; i < MAX_NUMBER_OF_GROUPS - colonCounter; i++) {
                expanded.insert(colonsPos + 1, "0:");
                change = change + 2;
            }


            if (colonsPos == ipv6AddressLength - 2) {
                expanded.setCharAt(colonsPos + change + 1, '0');
            } else {
                expanded.deleteCharAt(colonsPos + change + 1);
                change = change - 1;
            }
            length = length + change;
        }


        // Processing one char at the time
        for (int charCounter = 0; charCounter < length; charCounter++) {
            char c = expanded.charAt(charCounter);
            if (c >= 'A' && c <= 'F') {
                c = (char) (c + 32);
            }
            if (c != ':') {
                groups[groupCounter][charInGroupCounter] = c;
                if (!(groupStart && c == '0')) {
                    ++charInGroupCounter;
                    groupStart = false;
                }
                if (c != '0') {
                    isZero = false;
                }
            }
            if (c == ':' || charCounter == (length - 1)) {
                // We reached end of current group
                if (isZero) {
                    ++zeroGroupLength;
                    if (zeroGroupIndex == -1) {
                        zeroGroupIndex = groupCounter;
                    }
                }

                if (!isZero || charCounter == (length - 1)) {
                    // We reached end of zero group
                    if (zeroGroupLength > maxZeroGroupLength) {
                        maxZeroGroupLength = zeroGroupLength;
                        maxZeroGroupIndex = zeroGroupIndex;
                    }
                    zeroGroupLength = 0;
                    zeroGroupIndex = -1;
                }
                ++groupCounter;
                charInGroupCounter = 0;
                isZero = true;
                groupStart = true;
            }
        }

        int numberOfGroups = groupCounter;

        // Output results
        for (groupCounter = 0; groupCounter < numberOfGroups; groupCounter++) {
            if (maxZeroGroupLength <= 1 || groupCounter < maxZeroGroupIndex
                    || groupCounter >= maxZeroGroupIndex + maxZeroGroupLength) {
                for (int j = 0; j < MAX_GROUP_LENGTH; j++) {
                    if (groups[groupCounter][j] != 0) {
                        result.append(groups[groupCounter][j]);
                    }
                }
                if (groupCounter < (numberOfGroups - 1)
                        && (groupCounter != maxZeroGroupIndex - 1
                                || maxZeroGroupLength <= 1)) {
                    result.append(':');
                }
            } else if (groupCounter == maxZeroGroupIndex) {
                result.append("::");
            }
        }

        // Solve problem with three colons in IPv4 in IPv6 format
        // e.g. 0:0:0:0:0:0:127.0.0.1 -> :::127.0.0.1 -> ::127.0.0.1
        int resultLength = result.length();
        if (result.charAt(resultLength - 1) == ':' && ipv6AddressLength < ipv6Address.length()
                && ipv6Address.charAt(ipv6AddressLength) == ':') {
            result.delete(resultLength - 1, resultLength);
        }

        /*
         * Append IPv4 from IPv4-in-IPv6 format or Zone ID
         */
        for (int i = ipv6AddressLength; i < ipv6Address.length(); i++) {
            result.append(ipv6Address.charAt(i));
        }

        return result.toString();
    }

    /**
     * Heuristic check if string might be an IPv6 address.
     *
     * @param input Any string or null
     * @return true, if input string contains only hex digits and at least two colons, before '.' or '%' character
     */
    static boolean mayBeIPv6Address(String input) {
        if (input == null) {
            return false;
        }

        int colonsCounter = 0;
        int length = input.length();
        for (int i = 0; i < length; i++) {
            char c = input.charAt(i);
            if (c == '.' || c == '%') {
                // IPv4 in IPv6 or Zone ID detected, end of checking.
                break;
            }
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F') || c == ':')) {
                return false;
            } else if (c == ':') {
                colonsCounter++;
            }
        }
        if (colonsCounter < 2) {
            return false;
        }
        return true;
    }
}
