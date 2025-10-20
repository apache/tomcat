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
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class TestNetMask {

    @Parameter(0)
    public String mask;

    @Parameter(1)
    public String input;

    @Parameter(2)
    public Boolean valid;

    @Parameter(3)
    public Boolean matches;


    @Parameters(name = "{index}: mask [{0}], input [{1}]")
    public static Collection<Object[]> inputs() {
        List<Object[]> result = new ArrayList<>();

        // Invalid IPv4 netmasks
        result.add(new Object[] { "260.1.1.1", null, Boolean.FALSE, null });
        result.add(new Object[] { "1.2.3.4/foo", null, Boolean.FALSE, null });
        result.add(new Object[] { "1.2.3.4/-1", null, Boolean.FALSE, null });
        result.add(new Object[] { "1.2.3.4/33", null, Boolean.FALSE, null });

        // Invalid IPv6 netmasks
        result.add(new Object[] { "fffff::/71", null, Boolean.FALSE, null });
        result.add(new Object[] { "ae31::27:ef2:1/foo", null, Boolean.FALSE, null });
        result.add(new Object[] { "ae31::27:ef2:1/-1", null, Boolean.FALSE, null });
        result.add(new Object[] { "ae31::27:ef2:1/129", null, Boolean.FALSE, null });

        // Invalid port regex suffix after ";"
        result.add(new Object[] { "1.2.3.4;[", null, Boolean.FALSE, null });

        // IPv4
        result.add(new Object[] { "1.2.3.4", "1.2.3.4", Boolean.TRUE, Boolean.TRUE });

        result.add(new Object[] { "1.2.3.4/32", "1.2.3.3", Boolean.TRUE, Boolean.FALSE });
        result.add(new Object[] { "1.2.3.4/32", "1.2.3.4", Boolean.TRUE, Boolean.TRUE });
        result.add(new Object[] { "1.2.3.4/32", "1.2.3.5", Boolean.TRUE, Boolean.FALSE });

        result.add(new Object[] { "1.2.3.4/31", "1.2.3.3", Boolean.TRUE, Boolean.FALSE });
        result.add(new Object[] { "1.2.3.4/31", "1.2.3.4", Boolean.TRUE, Boolean.TRUE });
        result.add(new Object[] { "1.2.3.4/31", "1.2.3.5", Boolean.TRUE, Boolean.TRUE });
        result.add(new Object[] { "1.2.3.4/31", "1.2.3.6", Boolean.TRUE, Boolean.FALSE });

        result.add(new Object[] { "10.0.0.0/22", "9.255.255.255", Boolean.TRUE, Boolean.FALSE });
        result.add(new Object[] { "10.0.0.0/22", "10.0.0.0", Boolean.TRUE, Boolean.TRUE });
        result.add(new Object[] { "10.0.0.0/22", "10.0.3.255", Boolean.TRUE, Boolean.TRUE });
        result.add(new Object[] { "10.0.0.0/22", "10.0.4.0", Boolean.TRUE, Boolean.FALSE });

        // IPv6
        result.add(new Object[] { "::5:1", "::5:1", Boolean.TRUE, Boolean.TRUE });

        result.add(new Object[] { "::5:1/128", "::4:ffff", Boolean.TRUE, Boolean.FALSE });
        result.add(new Object[] { "::5:1/128", "::5:1", Boolean.TRUE, Boolean.TRUE });
        result.add(new Object[] { "::5:1/128", "::5:2", Boolean.TRUE, Boolean.FALSE });

        result.add(new Object[] { "::5:1/127", "::4:ffff", Boolean.TRUE, Boolean.FALSE });
        result.add(new Object[] { "::5:1/127", "::5:0", Boolean.TRUE, Boolean.TRUE });
        result.add(new Object[] { "::5:1/127", "::5:1", Boolean.TRUE, Boolean.TRUE });
        result.add(new Object[] { "::5:1/127", "::5:2", Boolean.TRUE, Boolean.FALSE });

        result.add(new Object[] { "a::5:1/42", "9:ffff:ffff:ffff:ffff:ffff:ffff:ffff", Boolean.TRUE, Boolean.FALSE });
        result.add(new Object[] { "a::5:1/42", "a::0", Boolean.TRUE, Boolean.TRUE });
        result.add(new Object[] { "a::5:1/42", "a:0:3f:ffff:ffff:ffff:ffff:ffff", Boolean.TRUE, Boolean.TRUE });
        result.add(new Object[] { "a::5:1/42", "a:0:40::", Boolean.TRUE, Boolean.FALSE });

        // Mixed
        result.add(new Object[] { "10.0.0.0/22", "::1", Boolean.TRUE, Boolean.FALSE });

        // port
        result.add(new Object[] { "1.2.3.4;8080", "1.2.3.4", Boolean.TRUE, Boolean.FALSE });
        result.add(new Object[] { "1.2.3.4", "1.2.3.4;8080", Boolean.TRUE, Boolean.FALSE });
        result.add(new Object[] { "1.2.3.4;", "1.2.3.4;8080", Boolean.TRUE, Boolean.FALSE });
        result.add(new Object[] { "1.2.3.4;8080", "1.2.3.4;8080", Boolean.TRUE, Boolean.TRUE });
        result.add(new Object[] { "1.2.3.4;8080", "1.2.3.4;8009", Boolean.TRUE, Boolean.FALSE });
        result.add(new Object[] { "1.2.3.4;.*", "1.2.3.4;8080", Boolean.TRUE, Boolean.TRUE });
        result.add(new Object[] { "1.2.3.4;8\\d+", "1.2.3.4;8080", Boolean.TRUE, Boolean.TRUE });
        result.add(new Object[] { "1.2.3.4;8\\d+", "1.2.3.4;9090", Boolean.TRUE, Boolean.FALSE });

        return result;
    }


    @Test
    public void testNetMask() {
        Exception exception = null;
        NetMask netMask = null;
        try {
            netMask = new NetMask(mask);
        } catch (Exception e) {
            exception = e;
        }

        if (valid.booleanValue()) {
            Assert.assertNull(exception);
            Assert.assertNotNull(netMask);
        } else {
            Assert.assertNotNull(exception);
            Assert.assertEquals(IllegalArgumentException.class.getName(), exception.getClass().getName());
            return;
        }

        final int portIdx = input.indexOf(";");
        final boolean usePort = portIdx >= 0 || mask.indexOf(";") >= 0;
        final int port;
        final String nonPortPart;

        if (portIdx == -1) {
            port = -1;
            nonPortPart = input;
        } else {
            port = Integer.parseInt(input.substring(portIdx + 1));
            nonPortPart = input.substring(0, portIdx);
        }

        InetAddress inetAddress = null;
        try {
            inetAddress = InetAddress.getByName(nonPortPart);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            Assert.fail();
        }

        if (usePort) {
            Assert.assertEquals(matches, Boolean.valueOf(netMask.matches(inetAddress, port)));
        } else {
            Assert.assertEquals(matches, Boolean.valueOf(netMask.matches(inetAddress)));
        }

        Assert.assertEquals(mask, netMask.toString());

        NetMask nm1, nm2, nm3;
        nm1 = new NetMask("192.168.0.0/24");
        nm2 = new NetMask("192.168.0.0/24");
        nm3 = new NetMask("192.168.1.0/24");

        Assert.assertEquals(nm1, nm2);
        Assert.assertEquals(nm1.hashCode(), nm2.hashCode());

        Assert.assertNotEquals(nm1, nm3);
    }
}
