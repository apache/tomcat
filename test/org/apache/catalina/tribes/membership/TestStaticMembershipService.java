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
package org.apache.catalina.tribes.membership;

import java.util.ArrayList;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.group.GroupChannel;

public class TestStaticMembershipService {

    /*
     * A non-positive ping interval makes the ping thread spin without a pause, and a non-positive
     * expiration time expires every member on every cycle. Such configuration must be rejected
     * when the service initializes its provider.
     */
    @Test
    public void testStartRejectsNonPositiveTimingProperties() throws Exception {
        String[] invalid = { "pingInterval=0", "pingInterval=-1", "expirationTime=0", "expirationTime=-5" };
        for (String entry : invalid) {
            String[] property = entry.split("=");
            Properties props = new Properties();
            props.setProperty(property[0], property[1]);

            GroupChannel channel = new GroupChannel();
            channel.setName("TestStaticMembershipService-" + System.nanoTime());

            StaticMembershipService service = new StaticMembershipService();
            service.setChannel(channel);
            service.setProperties(props);
            // The local member must be part of the static member list
            service.addStaticMember(new StaticMember("127.0.0.1", 4001, -1));
            service.setLocalMemberProperties("127.0.0.1", 4001, 0, 0);
            try {
                service.start(Channel.MBR_TX_SEQ);
                Assert.fail("start() must reject a non-positive " + property[0]);
            } catch (IllegalArgumentException expected) {
                // The configuration must be rejected before the provider is built
            }
        }
    }

    /*
     * A valid configuration, as setDefaults provides it, must still start and stop cleanly.
     */
    @Test
    public void testValidTimingPropertiesStartAndStop() throws Exception {
        GroupChannel channel = new GroupChannel();
        channel.setName("TestStaticMembershipService-" + System.nanoTime());

        StaticMembershipService service = new StaticMembershipService();
        service.setChannel(channel);
        service.addStaticMember(new StaticMember("127.0.0.1", 4002, -1));
        service.setLocalMemberProperties("127.0.0.1", 4002, 0, 0);

        int level = Channel.MBR_RX_SEQ | Channel.MBR_TX_SEQ;
        service.start(level);
        service.stop(level);
    }

    /*
     * The local member is resolved when the channel receiver starts, or is configured
     * explicitly. Starting the membership alone, without either, must fail with a clear error
     * rather than the null pointer exception that dereferencing the missing member caused.
     */
    @Test
    public void testStartWithoutLocalMemberFails() throws Exception {
        GroupChannel channel = new GroupChannel();
        channel.setName("TestStaticMembershipService-" + System.nanoTime());

        StaticMembershipService service = new StaticMembershipService();
        service.setChannel(channel);
        try {
            service.start(Channel.MBR_TX_SEQ);
            Assert.fail("start() must fail when the local member is unknown");
        } catch (IllegalStateException expected) {
            // A clear configuration error rather than a null pointer exception
        }
        Assert.assertNull(service.getMembershipProvider());
    }

    /*
     * The membership name is derived for the provider alone. It must not leak into the
     * configuration properties that the service exposes, while the provider must still receive
     * it, which the start itself proves, as it would fail without the name.
     */
    @Test
    public void testMembershipNameStaysOutOfTheExposedProperties() throws Exception {
        GroupChannel channel = new GroupChannel();
        channel.setName("TestStaticMembershipService-" + System.nanoTime());

        StaticMembershipService service = new StaticMembershipService();
        service.setChannel(channel);
        service.addStaticMember(new StaticMember("127.0.0.1", 4004, -1));
        service.setLocalMemberProperties("127.0.0.1", 4004, 0, 0);

        int level = Channel.MBR_RX_SEQ | Channel.MBR_TX_SEQ;
        service.start(level);
        try {
            Assert.assertFalse("the internal membership name must not leak into the properties",
                    service.getProperties().containsKey("membershipName"));
        } finally {
            service.stop(level);
        }
    }

    /*
     * The accessor hands out the list that the provider iterates while it runs. A caller that
     * mutated the returned list raced that iteration, so the accessor must hand out a copy
     * instead of the live list.
     */
    @Test
    public void testGetStaticMembersReturnsACopy() throws Exception {
        StaticMembershipService service = new StaticMembershipService();
        StaticMember member = new StaticMember("127.0.0.1", 4005, -1);
        service.addStaticMember(member);

        ArrayList<StaticMember> members = service.getStaticMembers();
        Assert.assertNotSame("the accessor must not hand out the same list twice", members,
                service.getStaticMembers());
        Assert.assertTrue(members.remove(member));
        Assert.assertFalse(members.contains(member));

        // The live list that the provider reads must be untouched by the copy mutation
        Assert.assertTrue(service.getStaticMembers().contains(member));
    }
}
