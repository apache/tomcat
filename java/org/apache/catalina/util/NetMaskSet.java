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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.tomcat.util.buf.StringUtils;
import org.apache.tomcat.util.res.StringManager;


/**
 * This class maintains a Set of NetMask objects and allows to check if a given IP address is matched by any of the
 * NetMasks, making it easy to create Allow and Deny lists of CIDR networks and hosts.
 */
public class NetMaskSet {

    /**
     * Default constructor.
     */
    public NetMaskSet() {
    }

    private static final StringManager sm = StringManager.getManager(NetMaskSet.class);

    private final Set<NetMask> netmasks = new HashSet<>();

    /**
     * Tests if the provided InetAddress matches any of the {@link NetMask}s in the set.
     *
     * @param inetAddress An InetAddress to check
     *
     * @return {@code true} if the passed inetAddress is matched by any of the {@link NetMask}s in the set
     */
    public boolean contains(InetAddress inetAddress) {

        for (NetMask nm : netmasks) {
            if (nm.matches(inetAddress)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Tests if the provided IP address matches any of the {@link NetMask}s in the set. The input is never resolved
     * since it may come from an untrusted source such as a request header.
     *
     * @param ipAddress an IPv4 or IPv6 address literal to check
     *
     * @return {@code true} if the passed IP address is matched by any of the {@link NetMask}s in the set
     *
     * @throws UnknownHostException if the passed input is not an IP address literal
     */
    public boolean contains(String ipAddress) throws UnknownHostException {

        byte[] address = parseAddress(ipAddress);
        if (address == null) {
            throw new UnknownHostException(sm.getString("netmaskSet.invalidAddress", ipAddress));
        }
        return this.contains(InetAddress.getByAddress(address));
    }


    /*
     * Parses an IPv4 or IPv6 address literal without any name resolution. An IPv6 address may be enclosed in square
     * brackets and may have a zone ID which is ignored.
     */
    private static byte[] parseAddress(String input) {
        if (input == null) {
            return null;
        }
        String address = input;
        if (address.length() > 2 && address.charAt(0) == '[' && address.charAt(address.length() - 1) == ']') {
            address = address.substring(1, address.length() - 1);
            if (address.indexOf(':') < 0) {
                // Only an IPv6 address may be enclosed in square brackets
                return null;
            }
        }
        if (address.indexOf(':') < 0) {
            return parseIPv4(address);
        }
        int zoneStart = address.indexOf('%');
        if (zoneStart >= 0) {
            if (!isZoneId(address, zoneStart + 1)) {
                return null;
            }
            address = address.substring(0, zoneStart);
        }
        return parseIPv6(address);
    }


    /*
     * Checks that the characters from the start position onwards are a zone ID as defined by RFC 6874.
     */
    private static boolean isZoneId(String address, int start) {
        if (start >= address.length()) {
            return false;
        }
        for (int i = start; i < address.length(); i++) {
            char c = address.charAt(i);
            if (!(c >= '0' && c <= '9' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '.' || c == '_' ||
                    c == '~' || c == '-')) {
                return false;
            }
        }
        return true;
    }


    private static byte[] parseIPv4(String address) {
        byte[] result = new byte[4];
        int octet = 0;
        int digits = 0;
        int value = 0;
        for (int i = 0; i < address.length(); i++) {
            char c = address.charAt(i);
            if (c >= '0' && c <= '9') {
                value = value * 10 + (c - '0');
                digits++;
                if (digits > 3 || value > 255) {
                    return null;
                }
            } else if (c == '.' && digits > 0 && octet < 3) {
                result[octet++] = (byte) value;
                value = 0;
                digits = 0;
            } else {
                return null;
            }
        }
        if (digits == 0 || octet != 3) {
            return null;
        }
        result[3] = (byte) value;
        return result;
    }


    private static byte[] parseIPv6(String address) {
        int compress = address.indexOf("::");
        if (compress < 0) {
            byte[] result = parseIPv6Groups(address, true);
            return result != null && result.length == 16 ? result : null;
        }
        if (address.indexOf("::", compress + 1) >= 0) {
            // More than one "::"
            return null;
        }
        byte[] head = parseIPv6Groups(address.substring(0, compress), false);
        byte[] tail = parseIPv6Groups(address.substring(compress + 2), true);
        // "::" stands for at least one group of zeros
        if (head == null || tail == null || head.length + tail.length > 14) {
            return null;
        }
        byte[] result = new byte[16];
        System.arraycopy(head, 0, result, 0, head.length);
        System.arraycopy(tail, 0, result, 16 - tail.length, tail.length);
        return result;
    }


    /*
     * Parses colon separated groups of one to four hex digits. If allowed, the last group may be an IPv4 address.
     */
    private static byte[] parseIPv6Groups(String groups, boolean allowIPv4) {
        if (groups.isEmpty()) {
            return new byte[0];
        }
        String[] parts = groups.split(":", -1);
        byte[] buffer = new byte[16];
        int length = 0;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (allowIPv4 && i == parts.length - 1 && part.indexOf('.') >= 0) {
                byte[] ipv4 = parseIPv4(part);
                if (ipv4 == null || length + 4 > buffer.length) {
                    return null;
                }
                System.arraycopy(ipv4, 0, buffer, length, 4);
                length += 4;
            } else {
                if (part.isEmpty() || part.length() > 4 || length + 2 > buffer.length) {
                    return null;
                }
                int value = 0;
                for (int j = 0; j < part.length(); j++) {
                    char c = part.charAt(j);
                    if (c >= '0' && c <= '9') {
                        value = value * 16 + (c - '0');
                    } else if (c >= 'a' && c <= 'f') {
                        value = value * 16 + (c - 'a' + 10);
                    } else if (c >= 'A' && c <= 'F') {
                        value = value * 16 + (c - 'A' + 10);
                    } else {
                        return null;
                    }
                }
                buffer[length++] = (byte) (value >> 8);
                buffer[length++] = (byte) value;
            }
        }
        return Arrays.copyOf(buffer, length);
    }

    /**
     * Adds a NetMask object to the set if the set does not contain it
     *
     * @param netmask The NetMask to add
     *
     * @return true if the object was added
     */
    public boolean add(NetMask netmask) {
        return netmasks.add(netmask);
    }

    /**
     * Creates a NetMask object from the input string and adds it to the set.
     *
     * @param input The string from which to construct the NetMask
     *
     * @return true if the object was added
     *
     * @throws IllegalArgumentException if the input is not a valid CIDR format.
     */
    public boolean add(String input) {
        NetMask netmask = new NetMask(input);
        return netmasks.add(netmask);
    }

    /**
     * Removes all entries from the set.
     */
    public void clear() {
        netmasks.clear();
    }

    /**
     * Tests if the set is empty.
     *
     * @return {@code true} if the set is empty, otherwise {@code false}
     */
    public boolean isEmpty() {
        return netmasks.isEmpty();
    }

    /**
     * Adds a {@link NetMask} list from a string input containing a comma-separated list of (hopefully valid)
     * {@link NetMask}s.
     *
     * @param input The input string
     *
     * @return a list of processing error messages (empty when no errors)
     */
    public List<String> addAll(String input) {

        if (input == null || input.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> errMessages = new ArrayList<>();

        for (String s : StringUtils.splitCommaSeparated(input)) {
            try {
                this.add(s);
            } catch (IllegalArgumentException e) {
                errMessages.add(s + ": " + e.getMessage());
            }
        }

        return Collections.unmodifiableList(errMessages);
    }

    /**
     * Provides a string representation of this NetMaskSet. The format of the String is not guaranteed to remain fixed.
     *
     * @return a comma separated list of the <code>NetMask</code>s in this set
     */
    @Override
    public String toString() {

        String result = netmasks.toString();

        // remove open and close brackets if exist
        if (result.startsWith("[")) {
            result = result.substring(1);
        }

        if (result.endsWith("]")) {
            result = result.substring(0, result.length() - 1);
        }

        return result;
    }


    /**
     * Parses a comma-separated list of IP addresses and CIDR ranges into a NetMaskSet.
     *
     * @param input The comma-separated string of IP addresses and CIDR ranges
     *
     * @return A new NetMaskSet containing the parsed entries
     *
     * @throws IllegalArgumentException If the input contains invalid entries
     */
    public static NetMaskSet parse(String input) {
        NetMaskSet result = new NetMaskSet();

        List<String> errors = result.addAll(input);
        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String error : errors) {
                sb.append(error).append("; ");
            }
            throw new IllegalArgumentException(sm.getString("netmaskSet.invalidNetMask", sb.toString()));
        }

        return result;
    }
}
