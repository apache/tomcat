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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * This class maintains a Set of NetMask objects and allows to check if
 * a given IP address is matched by any of the NetMasks, making it easy
 * to create Allow and Deny lists of CIDR networks and hosts.
 */
public class NetMaskSet {

    private final Set<NetMask> netmasks = new HashSet<>();

    /**
     * returns true if the passed inetAddress is matched by any of the {@link NetMask}s in the set
     *
     * @param inetAddress An InetAddress to check
     * @return
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
     * returns true if the passed inetAddress is matched by any of the {@link NetMask}s in the set
     *
     * @param ipAddress an IP address to check
     * @return
     *
     * @throws UnknownHostException if the passed input is not a valid IP address
     */
    public boolean contains(String ipAddress) throws UnknownHostException {

        InetAddress inetAddress = InetAddress.getByName(ipAddress);
        return this.contains(inetAddress);
    }

    /**
     * adds a NetMask object to the set if the set does not contain it
     *
     * @param netmask
     * @return true if the object was added
     */
    public boolean add(NetMask netmask) {
        return netmasks.add(netmask);
    }

    /**
     * creates a NetMask object from the input string and adds it to the set.
     * throws UnknownHostException if the input is not a valid CIDR format.
     *
     * @param input
     * @return true if the object was added
     */
    public boolean add(String input) {
        NetMask netmask = new NetMask(input);
        return netmasks.add(netmask);
    }

    /**
     * removes all entries from the set
     */
    public void clear() {
        netmasks.clear();
    }

    /**
     * returns true if the set is empty
     *
     * @return
     */
    public boolean isEmpty() {
        return netmasks.isEmpty();
    }

    /**
     * Adds a {@link NetMask} list from a string input containing a comma-separated list of (hopefully valid)
     * {@link NetMask}s.
     *
     * @param input  The input string
     * @return a list of processing error messages (empty when no errors)
     */
    public List<String> addAll(String input) {

        if (input == null || input.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> errMessages = new ArrayList<>();

        for (String s : input.split("\\s*,\\s*")) {
            try {
                this.add(s);
            } catch (IllegalArgumentException e) {
                errMessages.add(s + ": " + e.getMessage());
            }
        }

        return Collections.unmodifiableList(errMessages);
    }

    /**
     * returns a comma separated list of the <code>NetMask</code>s in this set
     *
     * @return
     */
    @Override
    public String toString() {

        String result = netmasks.toString();

        // remove the open and close brackets
        return result.substring(1, result.length() -1);
    }

}
