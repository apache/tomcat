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

import org.junit.Assert;
import org.junit.Test;


/**
 * Mostly examples from RFC 5952
 */
public class IPv6UtilsTest {

    @Test
    public void testMayBeIPv6Address() {
        Assert.assertFalse(IPv6Utils.mayBeIPv6Address(null));

        Assert.assertTrue(IPv6Utils.mayBeIPv6Address("::1"));
        Assert.assertTrue(IPv6Utils.mayBeIPv6Address("::"));
        Assert.assertTrue(IPv6Utils.mayBeIPv6Address("2001:db8:0:0:1:0:0:1"));

        Assert.assertFalse(IPv6Utils.mayBeIPv6Address(""));
        Assert.assertFalse(IPv6Utils.mayBeIPv6Address(":1"));
        Assert.assertFalse(IPv6Utils.mayBeIPv6Address("123.123.123.123"));
        Assert.assertFalse(IPv6Utils.mayBeIPv6Address("tomcat.eu.apache.org:443"));
    }

    @Test
    public void testCanonize() {
        Assert.assertNull(IPv6Utils.canonize(null));
        Assert.assertEquals("", IPv6Utils.canonize(""));

        // IPv4-safe
        Assert.assertEquals("123.123.123.123", IPv6Utils.canonize("123.123.123.123"));
        Assert.assertEquals("123.1.2.23", IPv6Utils.canonize("123.1.2.23"));

        // Introductory RFC 5952 examples
        Assert.assertEquals("2001:db8::1:0:0:1", IPv6Utils.canonize("2001:db8:0:0:1:0:0:1"));
        Assert.assertEquals("2001:db8::1:0:0:1", IPv6Utils.canonize("2001:0db8:0:0:1:0:0:1"));
        Assert.assertEquals("2001:db8::1:0:0:1", IPv6Utils.canonize("2001:db8::1:0:0:1"));
        Assert.assertEquals("2001:db8::1:0:0:1", IPv6Utils.canonize("2001:db8::0:1:0:0:1"));
        Assert.assertEquals("2001:db8::1:0:0:1", IPv6Utils.canonize("2001:0db8::1:0:0:1"));
        Assert.assertEquals("2001:db8::1:0:0:1", IPv6Utils.canonize("2001:db8:0:0:1::1"));
        Assert.assertEquals("2001:db8::1:0:0:1", IPv6Utils.canonize("2001:db8:0000:0:1::1"));
        Assert.assertEquals("2001:db8::1:0:0:1", IPv6Utils.canonize("2001:DB8:0:0:1::1"));

        // Strip leading zeros (2.1)
        Assert.assertEquals("2001:db8:aaaa:bbbb:cccc:dddd:eeee:1", IPv6Utils.canonize("2001:db8:aaaa:bbbb:cccc:dddd:eeee:0001"));
        Assert.assertEquals("2001:db8:aaaa:bbbb:cccc:dddd:eeee:1", IPv6Utils.canonize("2001:db8:aaaa:bbbb:cccc:dddd:eeee:001"));
        Assert.assertEquals("2001:db8:aaaa:bbbb:cccc:dddd:eeee:1", IPv6Utils.canonize("2001:db8:aaaa:bbbb:cccc:dddd:eeee:01"));
        Assert.assertEquals("2001:db8:aaaa:bbbb:cccc:dddd:eeee:1", IPv6Utils.canonize("2001:db8:aaaa:bbbb:cccc:dddd:eeee:1"));

        // Zero compression (2.2)
        Assert.assertEquals("2001:db8:aaaa:bbbb:cccc:dddd:0:1", IPv6Utils.canonize("2001:db8:aaaa:bbbb:cccc:dddd::1"));
        Assert.assertEquals("2001:db8:aaaa:bbbb:cccc:dddd:0:1", IPv6Utils.canonize("2001:db8:aaaa:bbbb:cccc:dddd:0:1"));

        Assert.assertEquals("2001:db8::1", IPv6Utils.canonize("2001:db8:0:0:0::1"));
        Assert.assertEquals("2001:db8::1", IPv6Utils.canonize("2001:db8:0:0::1"));
        Assert.assertEquals("2001:db8::1", IPv6Utils.canonize("2001:db8:0::1"));
        Assert.assertEquals("2001:db8::1", IPv6Utils.canonize("2001:db8::1"));

        Assert.assertEquals("2001:db8::aaaa:0:0:1", IPv6Utils.canonize("2001:db8::aaaa:0:0:1"));
        Assert.assertEquals("2001:db8::aaaa:0:0:1", IPv6Utils.canonize("2001:db8:0:0:aaaa::1"));

        // Uppercase or lowercase (2.3)
        Assert.assertEquals("2001:db8:aaaa:bbbb:cccc:dddd:eeee:aaaa", IPv6Utils.canonize("2001:db8:aaaa:bbbb:cccc:dddd:eeee:aaaa"));
        Assert.assertEquals("2001:db8:aaaa:bbbb:cccc:dddd:eeee:aaaa", IPv6Utils.canonize("2001:db8:aaaa:bbbb:cccc:dddd:eeee:AAAA"));
        Assert.assertEquals("2001:db8:aaaa:bbbb:cccc:dddd:eeee:aaaa", IPv6Utils.canonize("2001:db8:aaaa:bbbb:cccc:dddd:eeee:AaAa"));

        // Some more zero compression for localhost addresses
        Assert.assertEquals("::1", IPv6Utils.canonize("0:0:0:0:0:0:0:1"));
        Assert.assertEquals("::1", IPv6Utils.canonize("0000:0:0:0:0:0:0:0001"));
        Assert.assertEquals("::1", IPv6Utils.canonize("00:00:0:0:00:00:0:01"));
        Assert.assertEquals("::1", IPv6Utils.canonize("::0001"));
        Assert.assertEquals("::1", IPv6Utils.canonize("::1"));

        // IPv6 unspecified address
        Assert.assertEquals("::", IPv6Utils.canonize("0:0:0:0:0:0:0:0"));
        Assert.assertEquals("::", IPv6Utils.canonize("0000:0:0:0:0:0:0:0000"));
        Assert.assertEquals("::", IPv6Utils.canonize("00:00:0:0:00:00:0:00"));
        Assert.assertEquals("::", IPv6Utils.canonize("::0000"));
        Assert.assertEquals("::", IPv6Utils.canonize("::0"));
        Assert.assertEquals("::", IPv6Utils.canonize("::"));

        // Leading zeros (4.1)
        Assert.assertEquals("2001:db8::1", IPv6Utils.canonize("2001:0db8::0001"));

        // Shorten as much as possible (4.2.1)
        Assert.assertEquals("2001:db8::2:1", IPv6Utils.canonize("2001:db8:0:0:0:0:2:1"));
        Assert.assertEquals("2001:db8::", IPv6Utils.canonize("2001:db8:0:0:0:0:0:0"));

        // Handling One 16-Bit 0 Field (4.2.2)
        Assert.assertEquals("2001:db8:0:1:1:1:1:1", IPv6Utils.canonize("2001:db8:0:1:1:1:1:1"));
        Assert.assertEquals("2001:db8:0:1:1:1:1:1", IPv6Utils.canonize("2001:db8::1:1:1:1:1"));

        // Choice in Placement of "::" (4.2.3)
        Assert.assertEquals("2001:0:0:1::1", IPv6Utils.canonize("2001:0:0:1:0:0:0:1"));
        Assert.assertEquals("2001:db8::1:0:0:1", IPv6Utils.canonize("2001:db8:0:0:1:0:0:1"));

        // IPv4 inside IPv6
        Assert.assertEquals("::ffff:192.0.2.1", IPv6Utils.canonize("::ffff:192.0.2.1"));
        Assert.assertEquals("::ffff:192.0.2.1", IPv6Utils.canonize("0:0:0:0:0:ffff:192.0.2.1"));
        Assert.assertEquals("::192.0.2.1", IPv6Utils.canonize("::192.0.2.1"));
        Assert.assertEquals("::192.0.2.1", IPv6Utils.canonize("0:0:0:0:0:0:192.0.2.1"));

        // Zone ID
        Assert.assertEquals("fe80::f0f0:c0c0:1919:1234%4", IPv6Utils.canonize("fe80::f0f0:c0c0:1919:1234%4"));
        Assert.assertEquals("fe80::f0f0:c0c0:1919:1234%4", IPv6Utils.canonize("fe80:0:0:0:f0f0:c0c0:1919:1234%4"));

        Assert.assertEquals("::%4", IPv6Utils.canonize("::%4"));
        Assert.assertEquals("::%4", IPv6Utils.canonize("::0%4"));
        Assert.assertEquals("::%4", IPv6Utils.canonize("0:0::0%4"));
        Assert.assertEquals("::%4", IPv6Utils.canonize("0:0:0:0:0:0:0:0%4"));

        Assert.assertEquals("::1%4", IPv6Utils.canonize("::1%4"));
        Assert.assertEquals("::1%4", IPv6Utils.canonize("0:0::1%4"));
        Assert.assertEquals("::1%4", IPv6Utils.canonize("0:0:0:0:0:0:0:1%4"));

        Assert.assertEquals("::1%eth0", IPv6Utils.canonize("::1%eth0"));
        Assert.assertEquals("::1%eth0", IPv6Utils.canonize("0:0::1%eth0"));
        Assert.assertEquals("::1%eth0", IPv6Utils.canonize("0:0:0:0:0:0:0:1%eth0"));

        // Hostname safety
        Assert.assertEquals("www.apache.org", IPv6Utils.canonize("www.apache.org"));
        Assert.assertEquals("ipv6.google.com", IPv6Utils.canonize("ipv6.google.com"));
    }
}
