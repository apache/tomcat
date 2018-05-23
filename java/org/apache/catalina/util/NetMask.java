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

import org.apache.tomcat.util.res.StringManager;

/**
 * A class representing a CIDR netmask.
 *
 * <p>
 * The constructor takes a string as an argument which represents a netmask, as
 * per the CIDR notation -- whether this netmask be IPv4 or IPv6. It then
 * extracts the network address (before the /) and the CIDR prefix (after the
 * /), and tells through the #matches() method whether a candidate
 * {@link InetAddress} object fits in the recorded range.
 * </p>
 *
 * <p>
 * As byte arrays as returned by <code>InetAddress.getByName()</code> are always
 * in network byte order, finding a match is therefore as simple as testing
 * whether the n first bits (where n is the CIDR) are the same in both byte
 * arrays (the one of the network address and the one of the candidate address).
 * We do that by first doing byte comparisons, then testing the last bits if any
 * (that is, if the remainder of the integer division of the CIDR by 8 is not
 * 0).
 * </p>
 *
 * <p>
 * As a bonus, if no '/' is found in the input, it is assumed that an exact
 * address match is required.
 * </p>
 */
public final class NetMask {

    private static final StringManager sm = StringManager.getManager(NetMask.class);

    /**
     * The argument to the constructor, used for .toString()
     */
    private final String expression;

    /**
     * The byte array representing the address extracted from the expression
     */
    private final byte[] netaddr;

    /**
     * The number of bytes to test for equality (CIDR / 8)
     */
    private final int nrBytes;

    /**
     * The right shift to apply to the last byte if CIDR % 8 is not 0; if it is
     * 0, this variable is set to 0
     */
    private final int lastByteShift;


    /**
     * Constructor
     *
     * @param input the CIDR netmask
     * @throws IllegalArgumentException if the netmask is not correct (invalid
     *             address specification, malformed CIDR prefix, etc)
     */
    public NetMask(final String input) {

        expression = input;

        final int idx = input.indexOf("/");

        /*
         * Handle the "IP only" case first
         */
        if (idx == -1) {
            try {
                netaddr = InetAddress.getByName(input).getAddress();
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException(sm.getString("netmask.invalidAddress", input));
            }
            nrBytes = netaddr.length;
            lastByteShift = 0;
            return;
        }

        /*
         * OK, we do have a netmask specified, so let's extract both the address
         * and the CIDR.
         */

        final String addressPart = input.substring(0, idx), cidrPart = input.substring(idx + 1);

        try {
            /*
             * The address first...
             */
            netaddr = InetAddress.getByName(addressPart).getAddress();
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(sm.getString("netmask.invalidAddress", addressPart));
        }

        final int addrlen = netaddr.length * 8;
        final int cidr;

        try {
            /*
             * And then the CIDR.
             */
            cidr = Integer.parseInt(cidrPart);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(sm.getString("netmask.cidrNotNumeric", cidrPart));
        }

        /*
         * We don't want a negative CIDR, nor do we want a CIDR which is greater
         * than the address length (consider 0.0.0.0/33, or ::/129)
         */
        if (cidr < 0) {
            throw new IllegalArgumentException(sm.getString("netmask.cidrNegative", cidrPart));
        }
        if (cidr > addrlen) {
            throw new IllegalArgumentException(
                    sm.getString("netmask.cidrTooBig", cidrPart, Integer.valueOf(addrlen)));
        }

        nrBytes = cidr / 8;

        /*
         * These last two lines could be shortened to:
         *
         * lastByteShift = (8 - (cidr % 8)) & 7;
         *
         * But... It's not worth it. In fact, explaining why it could work would
         * be too long to be worth the trouble, so let's do it the simple way...
         */

        final int remainder = cidr % 8;

        lastByteShift = (remainder == 0) ? 0 : 8 - remainder;
    }


    /**
     * Test if a given address matches this netmask.
     *
     * @param addr The {@link java.net.InetAddress} to test
     * @return true on match, false otherwise
     */
    public boolean matches(final InetAddress addr) {
        final byte[] candidate = addr.getAddress();

        /*
         * OK, remember that a CIDR prefix tells the number of BITS which should
         * be equal between this NetMask's recorded address (netaddr) and the
         * candidate address. One byte is 8 bits, no matter what, and IP
         * addresses, whether they be IPv4 or IPv6, are big endian, aka MSB,
         * Most Significant Byte (first).
         *
         * We therefore need to get the byte array of the candidate address,
         * compare as many bytes of the candidate address with the recorded
         * address as the CIDR prefix tells us to (that is, CIDR / 8), and then
         * deal with the remaining bits -- if any.
         *
         * But prior to that, a simple test can be done: we deal with IP
         * addresses here, which means IPv4 and IPv6. IPv4 addresses are encoded
         * on 4 bytes, IPv6 addresses are encoded on 16 bytes. If the candidate
         * address length is different than this NetMask's address, we don't
         * have a match.
         */
        if (candidate.length != netaddr.length) {
            return false;
        }


        /*
         * Now do the byte-compare. The constructor has recorded the number of
         * bytes to compare in nrBytes, use that. If any of the byte we have to
         * compare is different than what we expect, we don't have a match.
         *
         * If, on the opposite, after this loop, all bytes have been deemed
         * equal, then the loop variable i will point to the byte right after
         * that -- which we will need...
         */
        int i = 0;
        for (; i < nrBytes; i++) {
            if (netaddr[i] != candidate[i]) {
                return false;
            }
        }

        /*
         * ... if there are bits left to test. There aren't any if lastByteShift
         * is set to 0.
         */
        if (lastByteShift == 0) {
            return true;
        }

        /*
         * If it is not 0, however, we must test for the relevant bits in the
         * next byte (whatever is in the bytes after that doesn't matter). We do
         * it this way (remember that lastByteShift contains the amount of bits
         * we should _right_ shift the last byte):
         *
         * - grab both bytes at index i, both from the netmask address and the
         * candidate address; - xor them both.
         *
         * After the xor, it means that all the remaining bits of the CIDR
         * should be set to 0...
         */
        final int lastByte = netaddr[i] ^ candidate[i];

        /*
         * ... Which means that right shifting by lastByteShift should be 0.
         */
        return lastByte >> lastByteShift == 0;
    }


    @Override
    public String toString() {
        return expression;
    }
}
