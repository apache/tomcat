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

import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class TestNetMaskSet {

    @Test
    public void testContainsIPv4Literals() throws UnknownHostException {
        NetMaskSet nms = new NetMaskSet();
        nms.addAll("10.0.0.0/8, 127.0.0.0/8");

        Assert.assertTrue(nms.contains("10.1.2.3"));
        Assert.assertTrue(nms.contains("127.0.0.1"));
        Assert.assertFalse(nms.contains("192.0.2.1"));
    }


    @Test
    public void testContainsIPv6Literals() throws UnknownHostException {
        NetMaskSet nms = new NetMaskSet();
        nms.addAll("::1, fc00::/7, 10.0.0.0/8");

        Assert.assertTrue(nms.contains("::1"));
        Assert.assertTrue(nms.contains("0:0:0:0:0:0:0:1"));
        Assert.assertTrue(nms.contains("[::1]"));
        Assert.assertTrue(nms.contains("fd12:3456:789a::1"));
        Assert.assertTrue(nms.contains("FD12:3456:789A::1"));
        Assert.assertTrue(nms.contains("fe80::1%eth0") == nms.contains("fe80::1"));
        Assert.assertTrue(nms.contains("[::1%eth0]"));
        // IPv4 mapped and embedded in IPv6 addresses
        Assert.assertTrue(nms.contains("::ffff:10.1.2.3"));
        Assert.assertTrue(nms.contains("0:0:0:0:0:ffff:10.1.2.3"));
        Assert.assertTrue(nms.contains("::ffff:a01:203"));
        NetMaskSet embedded = new NetMaskSet();
        embedded.addAll("::a01:203/128, 64:ff9b::a01:203/128");
        Assert.assertTrue(embedded.contains("::10.1.2.3"));
        Assert.assertTrue(embedded.contains("0:0:0:0:0:0:10.1.2.3"));
        Assert.assertTrue(embedded.contains("64:ff9b::10.1.2.3"));
        Assert.assertFalse(embedded.contains("64:ff9b::10.1.2.4"));
        Assert.assertFalse(nms.contains("::ffff:192.0.2.1"));
        Assert.assertFalse(nms.contains("2001:db8::1"));
        Assert.assertFalse(nms.contains("::2"));
    }


    /*
     * Values that are not IP address literals must be rejected without being resolved. localhost is resolved by every
     * resolver to an address that is in the set so it would match if the value were resolved.
     */
    @Test
    public void testContainsDoesNotResolveNames() throws UnknownHostException {
        NetMaskSet nms = new NetMaskSet();
        nms.addAll("127.0.0.0/8, ::1");

        String[] notLiterals = { "localhost", "unknown", "_hidden", "unknown:80", "deadbeef", "a.b.c", "", " ", "10.0.0",
                "10.0.0.256", "10.0.0.1.2", "10.0.0.1:8080", "1.2.3.", ".1.2.3", "01234.1.1.1", "2130706433", "127.1",
                "0x7f.0.0.1", ":::1", "1::2::3", "1:2:3:4:5:6:7:8:9", "1:2:3:4:5:6:7", "12345::1", "g::1", "::1:", ":1::1",
                "1:2:3:4:5:6:7::8", "::1.2.3", "::1.2.3.4.5", "[::1", "::1]", "[]", "[127.0.0.1]", "1.2.3.4::", "::1.2.3.4::",
                "1:2:1.2.3.4::5", "1.2.3.4::1.2.3.4", "1:2:3:4:5:6:7:1.2.3.4", "::1%", "::1%a:b", "::1%%", "::1%a b",
                "::1%\u00e9" };
        for (String notLiteral : notLiterals) {
            try {
                nms.contains(notLiteral);
                Assert.fail("[" + notLiteral + "] was accepted");
            } catch (UnknownHostException expected) {
                // Expected
            }
        }
    }


    @Test
    public void testContainsNull() {
        try {
            new NetMaskSet().contains((String) null);
            Assert.fail();
        } catch (UnknownHostException expected) {
            // Expected
        }
    }


    @Test
    public void testNetMaskSet() throws UnknownHostException {

        NetMaskSet nms = new NetMaskSet();
        nms.addAll("192.168.0.0/24, 192.168.1.0/27, 192.168.2.2, 10.0.0.0/8");

        Assert.assertTrue(nms.contains("192.168.0.5"));
        Assert.assertTrue(nms.contains("192.168.0.255"));

        Assert.assertTrue(nms.contains("192.168.1.0"));
        Assert.assertTrue(nms.contains("192.168.1.1"));
        Assert.assertTrue(nms.contains("192.168.1.31"));
        Assert.assertFalse(nms.contains("192.168.1.32"));

        Assert.assertTrue(nms.contains("192.168.2.2"));
        Assert.assertFalse(nms.contains("192.168.2.1"));
        Assert.assertFalse(nms.contains("192.168.2.3"));

        Assert.assertTrue(nms.contains("10.10.10.10"));
        Assert.assertTrue(nms.contains("10.20.30.40"));
        Assert.assertFalse(nms.contains("9.10.10.10"));
        Assert.assertFalse(nms.contains("11.10.10.10"));

        String s = nms.toString();
        Assert.assertTrue(s.indexOf('[') == -1);
        Assert.assertTrue(s.indexOf(']') == -1);

        List<String> list = Arrays.asList(s.split("\\s*,\\s*"));
        Assert.assertTrue(list.contains("192.168.0.0/24"));
        Assert.assertTrue(list.contains("192.168.1.0/27"));
        Assert.assertTrue(list.contains("192.168.2.2"));
        Assert.assertTrue(list.contains("10.0.0.0/8"));
    }
}
