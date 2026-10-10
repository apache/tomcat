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
package org.apache.catalina.tribes.group.interceptors;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.membership.MemberImpl;

public class TestStaticMembershipInterceptor {

    // Downstream stand-in: the dynamically discovered membership
    private static class DynamicMembershipChain extends ChannelInterceptorBase {

        private volatile Member[] members = new Member[0];

        @Override
        public Member[] getMembers() {
            return members;
        }
    }

    private static MemberImpl member(String host, int port) throws Exception {
        return new MemberImpl(host, port, -1);
    }

    /*
     * The copy the channel sees through dynamic discovery for the node represented by the
     * given statically configured member: the same host and TCP port but a different unique
     * id, since the unique id of a node that does not pin one explicitly is random per JVM
     * start and cannot match the statically configured copy.
     */
    private static MemberImpl discoveredCopy(MemberImpl configured) throws Exception {
        return member("localhost", configured.getPort());
    }

    private static void setUniqueId(MemberImpl member, int first) {
        byte[] uniqueId = new byte[16];
        for (int i = 0; i < uniqueId.length; i++) {
            uniqueId[i] = (byte) (first + i);
        }
        member.setUniqueId(uniqueId);
    }

    private static int countOfPort(Member[] members, int port) {
        int count = 0;
        for (Member m : members) {
            int effectivePort = m.getPort() >= 0 ? m.getPort() : m.getSecurePort();
            if (effectivePort == port) {
                count++;
            }
        }
        return count;
    }

    @Test
    public void testGetMembersDeduplicatesStaticAndDynamic() throws Exception {
        StaticMembershipInterceptor interceptor = new StaticMembershipInterceptor();
        DynamicMembershipChain chain = new DynamicMembershipChain();
        interceptor.setNext(chain);

        MemberImpl staticOnly = member("localhost", 4000);
        setUniqueId(staticOnly, 1);
        MemberImpl bothConfigured = member("localhost", 4001);
        setUniqueId(bothConfigured, 17);
        MemberImpl dynamicOnly = member("localhost", 4002);
        setUniqueId(dynamicOnly, 33);
        // The statically configured node is also discovered dynamically, as a
        // different instance of the same node with a different unique id, so
        // Member.equals does not match the two copies
        MemberImpl bothDiscovered = discoveredCopy(bothConfigured);
        setUniqueId(bothDiscovered, 49);
        Assert.assertNotEquals("The two copies must not be equal by Member.equals",
                bothConfigured, bothDiscovered);
        chain.members = new Member[] { dynamicOnly, bothDiscovered };
        interceptor.addStaticMember(bothConfigured);
        interceptor.addStaticMember(staticOnly);

        Member[] result = interceptor.getMembers();
        Assert.assertEquals("Each destination must be returned exactly once", 3, result.length);
        Assert.assertEquals(1, countOfPort(result, 4000));
        Assert.assertEquals(1, countOfPort(result, 4001));
        Assert.assertEquals(1, countOfPort(result, 4002));
    }

    @Test
    public void testGetMembersDeduplicatesSecurePortOnlyListeners() throws Exception {
        StaticMembershipInterceptor interceptor = new StaticMembershipInterceptor();
        DynamicMembershipChain chain = new DynamicMembershipChain();
        interceptor.setNext(chain);

        MemberImpl bothConfigured = new MemberImpl("localhost", -1, -1);
        bothConfigured.setSecurePort(4433);
        setUniqueId(bothConfigured, 17);
        MemberImpl bothDiscovered = new MemberImpl("localhost", -1, -1);
        bothDiscovered.setSecurePort(4433);
        setUniqueId(bothDiscovered, 49);
        chain.members = new Member[] { bothDiscovered };
        interceptor.addStaticMember(bothConfigured);

        Member[] result = interceptor.getMembers();
        Assert.assertEquals("The secure port destination must be returned once", 1, result.length);
    }

    @Test
    public void testGetMembersKeepsDifferentDestinations() throws Exception {
        StaticMembershipInterceptor interceptor = new StaticMembershipInterceptor();
        DynamicMembershipChain chain = new DynamicMembershipChain();
        interceptor.setNext(chain);

        // Same host, different port: distinct nodes, both must be kept
        MemberImpl dynamic = member("localhost", 4000);
        setUniqueId(dynamic, 1);
        MemberImpl staticOtherPort = member("localhost", 4001);
        setUniqueId(staticOtherPort, 17);
        chain.members = new Member[] { dynamic };
        interceptor.addStaticMember(staticOtherPort);

        Member[] result = interceptor.getMembers();
        Assert.assertEquals(2, result.length);
        Assert.assertEquals(1, countOfPort(result, 4000));
        Assert.assertEquals(1, countOfPort(result, 4001));
    }

    @Test
    public void testGetMembersKeepsSamePortOnDifferentHosts() throws Exception {
        StaticMembershipInterceptor interceptor = new StaticMembershipInterceptor();
        DynamicMembershipChain chain = new DynamicMembershipChain();
        interceptor.setNext(chain);

        MemberImpl dynamic = member("127.0.0.1", 4000);
        setUniqueId(dynamic, 1);
        MemberImpl staticOtherHost = member("127.0.0.2", 4000);
        setUniqueId(staticOtherHost, 17);
        chain.members = new Member[] { dynamic };
        interceptor.addStaticMember(staticOtherHost);

        Member[] result = interceptor.getMembers();
        Assert.assertEquals("Two hosts may share a port", 2, result.length);
    }

    @Test
    public void testGetMembersAllStatic() throws Exception {
        StaticMembershipInterceptor interceptor = new StaticMembershipInterceptor();
        DynamicMembershipChain chain = new DynamicMembershipChain();
        interceptor.setNext(chain);

        MemberImpl first = member("localhost", 4000);
        setUniqueId(first, 1);
        MemberImpl second = member("localhost", 4001);
        setUniqueId(second, 17);
        interceptor.addStaticMember(first);
        interceptor.addStaticMember(second);
        interceptor.addStaticMember(first);

        Member[] result = interceptor.getMembers();
        Assert.assertEquals("Each static destination must be returned exactly once", 2, result.length);
        Assert.assertEquals(1, countOfPort(result, 4000));
        Assert.assertEquals(1, countOfPort(result, 4001));
    }

    @Test
    public void testGetMembersCollidesSameDestinationTwiceStatic() throws Exception {
        StaticMembershipInterceptor interceptor = new StaticMembershipInterceptor();
        DynamicMembershipChain chain = new DynamicMembershipChain();
        interceptor.setNext(chain);

        // The same node configured twice with different unique ids: a
        // destination misconfiguration, collapsed to one entry
        MemberImpl first = member("localhost", 4000);
        setUniqueId(first, 1);
        MemberImpl second = member("localhost", 4000);
        setUniqueId(second, 17);
        interceptor.addStaticMember(first);
        interceptor.addStaticMember(second);

        Member[] result = interceptor.getMembers();
        Assert.assertEquals("One destination must be returned once", 1, result.length);
    }

    @Test
    public void testGetMembersAllDynamic() throws Exception {
        StaticMembershipInterceptor interceptor = new StaticMembershipInterceptor();
        DynamicMembershipChain chain = new DynamicMembershipChain();
        interceptor.setNext(chain);

        MemberImpl first = member("localhost", 4000);
        setUniqueId(first, 1);
        MemberImpl second = member("localhost", 4001);
        setUniqueId(second, 17);
        chain.members = new Member[] { first, second };

        Member[] result = interceptor.getMembers();
        Assert.assertEquals(2, result.length);
        Assert.assertEquals(1, countOfPort(result, 4000));
        Assert.assertEquals(1, countOfPort(result, 4001));
    }
}
