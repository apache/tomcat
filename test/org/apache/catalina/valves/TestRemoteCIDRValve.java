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
package org.apache.catalina.valves;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link RemoteCIDRValve}.
 */
public class TestRemoteCIDRValve {

    @Test
    public void testAllowSingleIPv4() {
        RemoteCIDRValve valve = new RemoteCIDRValve();
        valve.setAllow("127.0.0.0/8");

        Assert.assertTrue(valve.isAllowed("127.0.0.1"));
        Assert.assertFalse(valve.isAllowed("192.168.1.1"));
    }

    @Test
    public void testDenyOverridesAllow() {
        RemoteCIDRValve valve = new RemoteCIDRValve();
        valve.setAllow("192.168.0.0/16");
        valve.setDeny("192.168.1.0/24");

        // Deny is checked first
        Assert.assertFalse(valve.isAllowed("192.168.1.1"));
        Assert.assertTrue(valve.isAllowed("192.168.2.1"));
    }

    @Test
    public void testAllowEmptyDenyNotEmpty() {
        RemoteCIDRValve valve = new RemoteCIDRValve();
        valve.setDeny("10.0.0.0/8");
        // allow is empty, deny is set → allow everything not in deny

        Assert.assertTrue(valve.isAllowed("192.168.1.1"));
        Assert.assertTrue(valve.isAllowed("172.16.0.1"));
        Assert.assertFalse(valve.isAllowed("10.1.2.3"));
    }

    @Test
    public void testDefaultDenyAll() {
        RemoteCIDRValve valve = new RemoteCIDRValve();
        // No allow, no deny → deny all
        Assert.assertFalse(valve.isAllowed("127.0.0.1"));
        Assert.assertFalse(valve.isAllowed("192.168.1.1"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidAllow() {
        RemoteCIDRValve valve = new RemoteCIDRValve();
        valve.setAllow("not-a-valid-cidr");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidDeny() {
        RemoteCIDRValve valve = new RemoteCIDRValve();
        valve.setDeny("not-a-valid-cidr");
    }

    @Test
    public void testClearAllow() {
        RemoteCIDRValve valve = new RemoteCIDRValve();
        valve.setAllow("127.0.0.0/8");
        Assert.assertFalse(valve.getAllow().isEmpty());

        // Both null and empty string clear the allow list
        valve.setAllow(null);
        Assert.assertEquals("", valve.getAllow());

        valve.setAllow("127.0.0.0/8");
        valve.setAllow("");
        Assert.assertEquals("", valve.getAllow());
    }

    @Test
    public void testNullDeny() {
        RemoteCIDRValve valve = new RemoteCIDRValve();
        valve.setDeny("10.0.0.0/8");
        Assert.assertFalse(valve.getDeny().isEmpty());

        valve.setDeny(null);
        Assert.assertEquals("", valve.getDeny());
    }

    @Test
    public void testGetAllowGetDeny() {
        RemoteCIDRValve valve = new RemoteCIDRValve();
        valve.setAllow("127.0.0.0/8, 192.168.0.0/16");
        valve.setDeny("10.0.0.0/8");

        String allow = valve.getAllow();
        Assert.assertTrue(allow.contains("127.0.0.0/8"));
        Assert.assertTrue(allow.contains("192.168.0.0/16"));

        String deny = valve.getDeny();
        Assert.assertTrue(deny.contains("10.0.0.0/8"));
    }

    @Test
    public void testMultipleAllowCIDRs() {
        RemoteCIDRValve valve = new RemoteCIDRValve();
        valve.setAllow("127.0.0.0/8, 10.0.0.0/8");

        Assert.assertTrue(valve.isAllowed("127.0.0.1"));
        Assert.assertTrue(valve.isAllowed("10.1.2.3"));
        Assert.assertFalse(valve.isAllowed("192.168.1.1"));
    }

    @Test
    public void testIPv6Allow() {
        RemoteCIDRValve valve = new RemoteCIDRValve();
        valve.setAllow("::1/128");

        Assert.assertTrue(valve.isAllowed("::1"));
        Assert.assertFalse(valve.isAllowed("::2"));
    }

    @Test
    public void testIsAllowedWithPort() {
        RemoteCIDRValve valve = new RemoteCIDRValve();
        valve.setAddConnectorPort(true);
        valve.setAllow("127.0.0.0/8;8080");

        Assert.assertTrue(valve.isAllowed("127.0.0.1;8080"));
        Assert.assertFalse(valve.isAllowed("127.0.0.1;9090"));
    }

    @Test
    public void testIsAllowedNoPortWhenExpected() {
        RemoteCIDRValve valve = new RemoteCIDRValve();
        valve.setAddConnectorPort(true);
        valve.setAllow("127.0.0.0/8;8080");

        // No port provided when addConnectorPort is true
        Assert.assertFalse(valve.isAllowed("127.0.0.1"));
    }

    @Test
    public void testIsAllowedUnexpectedPort() {
        RemoteCIDRValve valve = new RemoteCIDRValve();
        valve.setAddConnectorPort(false);
        valve.setAllow("127.0.0.0/8");

        // Port provided when addConnectorPort is false
        Assert.assertFalse(valve.isAllowed("127.0.0.1;8080"));
    }
}
