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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.membership.MemberImpl;
import org.apache.catalina.tribes.membership.StaticMember;

public class TestStaticMembershipInterceptor {

    // Downstream stand-in: the dynamically discovered membership
    private static class DynamicMembershipChain extends ChannelInterceptorBase {

        private volatile Member[] members = new Member[0];

        @Override
        public Member[] getMembers() {
            return members;
        }

        @Override
        public Member getLocalMember(boolean incAlive) {
            return localMember;
        }

        private final Member localMember = createLocalMember();

        private static Member createLocalMember() {
            try {
                return new StaticMember("localhost", 5000, -1);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    // Upstream stand-in: records the member events the interceptor forwards
    private static class RecordingInterceptor extends ChannelInterceptorBase {

        final List<Member> added = Collections.synchronizedList(new ArrayList<>());
        final List<Member> disappeared = Collections.synchronizedList(new ArrayList<>());
        // The events in the order they were received
        final List<String> events = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void memberAdded(Member member) {
            added.add(member);
            events.add("added:" + member.getUniqueId()[0]);
        }

        @Override
        public void memberDisappeared(Member member) {
            disappeared.add(member);
            events.add("disappeared:" + member.getUniqueId()[0]);
        }
    }

    // Failure detector that does not use the network
    private static class AliveDetector extends TcpFailureDetector {

        volatile boolean alive = true;

        @Override
        protected boolean memberAlive(Member mbr) {
            return alive;
        }
    }

    // Failure detector that holds the withdrawal of a replaced member
    private static class GatedForgetDetector extends AliveDetector {

        final CountDownLatch forgetStarted = new CountDownLatch(1);
        final CountDownLatch releaseForget = new CountDownLatch(1);

        @Override
        public void forgetMember(Member member) {
            forgetStarted.countDown();
            try {
                Assert.assertTrue("Timed out waiting to release the withdrawal",
                        releaseForget.await(10, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            super.forgetMember(member);
        }
    }

    private final StaticMembershipInterceptor interceptor = new StaticMembershipInterceptor();
    private final DynamicMembershipChain chain = new DynamicMembershipChain();
    private final RecordingInterceptor recorder = new RecordingInterceptor();

    public TestStaticMembershipInterceptor() {
        interceptor.setNext(chain);
        interceptor.setPrevious(recorder);
    }

    private static StaticMember member(String host, int port, int uniqueIdStart) throws Exception {
        StaticMember member = new StaticMember(host, port, -1);
        byte[] uniqueId = new byte[16];
        for (int i = 0; i < uniqueId.length; i++) {
            uniqueId[i] = (byte) (uniqueIdStart + i);
        }
        member.setUniqueId(uniqueId);
        return member;
    }

    /*
     * The member the channel sees through dynamic discovery for the node that is configured statically as the
     * given member: the same host and ports, but a different unique id, since the unique id of a dynamically
     * discovered node is random per JVM start and can not be known by the static configuration.
     */
    private static MemberImpl discoveredCopy(MemberImpl configured, int uniqueIdStart) throws Exception {
        MemberImpl copy = new MemberImpl("localhost", configured.getPort(), -1);
        copy.setUniqueId(member("localhost", configured.getPort(), uniqueIdStart).getUniqueId());
        copy.setSecurePort(configured.getSecurePort());
        copy.setUdpPort(configured.getUdpPort());
        return copy;
    }

    // Puts a failure detector between the interceptor and the recorder, as in the documented stack
    private AliveDetector installDetector() {
        return installDetector(new AliveDetector());
    }

    private AliveDetector installDetector(AliveDetector detector) {
        detector.setNext(interceptor);
        detector.setPrevious(recorder);
        interceptor.setPrevious(detector);
        return detector;
    }

    private void discover(Member... discovered) {
        chain.members = discovered;
        for (Member member : discovered) {
            interceptor.memberAdded(member);
        }
    }

    @Test
    public void testDiscoveredMemberReplacesConfiguredMember() throws Exception {
        MemberImpl configured = member("localhost", 4001, 17);
        MemberImpl discovered = discoveredCopy(configured, 49);
        Assert.assertNotEquals("The two copies must not be equal by Member.equals", configured, discovered);
        interceptor.addStaticMember(configured);

        discover(discovered);

        Member[] result = interceptor.getMembers();
        Assert.assertEquals("The node must be returned exactly once", 1, result.length);
        Assert.assertSame("The discovered member must replace the configured member", discovered, result[0]);
        Assert.assertTrue("The configured member must have been replaced", interceptor.members.isEmpty());
        Assert.assertEquals("The discovered member must be forwarded", 1, recorder.added.size());
        Assert.assertSame(discovered, recorder.added.get(0));
        Assert.assertTrue("A configured member that was never announced must not be withdrawn",
                recorder.disappeared.isEmpty());
    }

    @Test
    public void testReplacedConfiguredMemberIsForgottenByFailureDetector() throws Exception {
        AliveDetector detector = installDetector();
        StaticMember configured = member("localhost", 4001, 17);
        MemberImpl discovered = discoveredCopy(configured, 49);
        interceptor.addStaticMember(configured);
        // announced as the interceptor does when it starts
        detector.memberAdded(configured);
        Assert.assertNotNull(detector.getMember(configured));

        discover(discovered);

        Member[] result = detector.getMembers();
        Assert.assertEquals("The failure detector must know the node once", 1, result.length);
        Assert.assertSame(discovered, result[0]);
        Assert.assertEquals("The configured member must be withdrawn before the discovered member is announced",
                Arrays.asList("added:17", "disappeared:17", "added:49"), recorder.events);
    }

    @Test
    public void testReplacedConfiguredMemberIsNotAddedAgainByFailureDetectorHeartbeat() throws Exception {
        AliveDetector detector = installDetector();
        StaticMember configured = member("localhost", 4001, 17);
        MemberImpl discovered = discoveredCopy(configured, 49);
        interceptor.addStaticMember(configured);
        detector.memberAdded(configured);
        discover(discovered);

        detector.checkMembers(false);
        detector.checkMembers(true);

        Member[] result = detector.getMembers();
        Assert.assertEquals("The configured member must not be added again", 1, result.length);
        Assert.assertSame(discovered, result[0]);
        Assert.assertEquals(Arrays.asList("added:17", "disappeared:17", "added:49"), recorder.events);
    }

    @Test
    public void testReplacedConfiguredMemberRejectedByFailureDetectorIsNotAddedLater() throws Exception {
        AliveDetector detector = installDetector();
        StaticMember configured = member("localhost", 4001, 17);
        MemberImpl discovered = discoveredCopy(configured, 49);
        interceptor.addStaticMember(configured);
        // the node is not reachable yet, so the failure detector keeps the configured member as a suspect
        detector.alive = false;
        detector.memberAdded(configured);
        Assert.assertNull(detector.getMember(configured));
        Assert.assertTrue(detector.addSuspects.containsKey(configured));

        detector.alive = true;
        discover(discovered);
        detector.checkMembers(false);

        Member[] result = detector.getMembers();
        Assert.assertEquals("The configured member must not be added later", 1, result.length);
        Assert.assertSame(discovered, result[0]);
        Assert.assertTrue(detector.addSuspects.isEmpty());
        Assert.assertEquals("Nothing was announced for the configured member", Arrays.asList("added:49"),
                recorder.events);
    }

    @Test
    public void testDisappearanceDuringReplacementLeavesOnlyConfiguredMember() throws Exception {
        GatedForgetDetector detector = new GatedForgetDetector();
        installDetector(detector);
        StaticMember configured = member("localhost", 4001, 17);
        MemberImpl discovered = discoveredCopy(configured, 49);
        interceptor.addStaticMember(configured);
        detector.memberAdded(configured);
        chain.members = new Member[] { discovered };

        // The replacement is held after the configured member was replaced, before the discovered member is
        // announced
        Thread replacementThread = new Thread(() -> interceptor.memberAdded(discovered));
        replacementThread.start();
        Assert.assertTrue("The withdrawal did not start", detector.forgetStarted.await(10, TimeUnit.SECONDS));
        Assert.assertTrue(interceptor.members.isEmpty());

        // The discovered member disappears meanwhile, without waiting for the replacement
        chain.members = new Member[0];
        interceptor.memberDisappeared(discovered);
        Assert.assertSame("The configured member is restored", configured, interceptor.members.get(0));

        detector.releaseForget.countDown();
        replacementThread.join(10000);
        Assert.assertFalse(replacementThread.isAlive());

        // The discovered member that was announced after it had disappeared must not be kept
        Assert.assertNull("The announcement of a member that is gone must be undone", detector.getMember(discovered));
        detector.checkMembers(false);
        Member[] result = detector.getMembers();
        Assert.assertEquals("Only the configured member remains", 1, result.length);
        Assert.assertSame(configured, result[0]);
    }

    @Test
    public void testDisappearanceDuringAddingStaticMemberLeavesOnlyConfiguredMember() throws Exception {
        GatedForgetDetector detector = new GatedForgetDetector();
        installDetector(detector);
        StaticMember configured = member("localhost", 4001, 17);
        MemberImpl discovered = discoveredCopy(configured, 49);
        detector.memberAdded(configured);
        chain.members = new Member[] { discovered };
        detector.memberAdded(discovered);

        Thread addThread = new Thread(() -> interceptor.addStaticMember(configured));
        addThread.start();
        Assert.assertTrue("The withdrawal did not start", detector.forgetStarted.await(10, TimeUnit.SECONDS));
        Assert.assertTrue(interceptor.members.isEmpty());

        // The discovered member disappears meanwhile, and is not alive
        chain.members = new Member[0];
        detector.alive = false;
        interceptor.memberDisappeared(discovered);
        Assert.assertSame("The configured member is restored", configured, interceptor.members.get(0));

        detector.alive = true;
        detector.releaseForget.countDown();
        addThread.join(10000);
        Assert.assertFalse(addThread.isAlive());

        // The withdrawal was applied to the restored member, the next heartbeat finds it again
        detector.checkMembers(false);
        Member[] result = detector.getMembers();
        Assert.assertEquals("Only the configured member remains", 1, result.length);
        Assert.assertSame(configured, result[0]);
    }

    @Test
    public void testDiscoveredMemberWithSameUniqueIdReplacesConfiguredMember() throws Exception {
        MemberImpl configured = member("localhost", 4001, 17);
        MemberImpl discovered = discoveredCopy(configured, 17);
        Assert.assertEquals(configured, discovered);
        interceptor.addStaticMember(configured);

        discover(discovered);

        Member[] result = interceptor.getMembers();
        Assert.assertEquals("The node must be returned exactly once", 1, result.length);
        Assert.assertSame(discovered, result[0]);
    }

    @Test
    public void testSecureAndUdpPortsMustAllMatch() throws Exception {
        MemberImpl configured = member("localhost", 4001, 17);
        configured.setSecurePort(4433);
        configured.setUdpPort(4500);
        MemberImpl discovered = discoveredCopy(configured, 49);
        interceptor.addStaticMember(configured);

        discover(discovered);

        Member[] result = interceptor.getMembers();
        Assert.assertEquals(1, result.length);
        Assert.assertSame(discovered, result[0]);
    }

    @Test
    public void testSecurePortOnlyMismatchIsNotReplaced() throws Exception {
        MemberImpl configured = member("localhost", 4001, 17);
        MemberImpl discovered = discoveredCopy(configured, 49);
        discovered.setSecurePort(4433);
        interceptor.addStaticMember(configured);

        discover(discovered);

        Assert.assertEquals("A partial port match is a configuration error and is not merged", 2,
                interceptor.getMembers().length);
        Assert.assertTrue(interceptor.members.contains(configured));
    }

    @Test
    public void testUdpPortOnlyMismatchIsNotReplaced() throws Exception {
        MemberImpl configured = member("localhost", 4001, 17);
        MemberImpl discovered = discoveredCopy(configured, 49);
        discovered.setUdpPort(4500);
        interceptor.addStaticMember(configured);

        discover(discovered);

        Assert.assertEquals(2, interceptor.getMembers().length);
        Assert.assertTrue(interceptor.members.contains(configured));
    }

    @Test
    public void testTcpPortOnlyMismatchIsNotReplaced() throws Exception {
        MemberImpl configured = member("localhost", 4001, 17);
        configured.setSecurePort(4433);
        MemberImpl discovered = member("localhost", 4002, 49);
        discovered.setSecurePort(4433);
        interceptor.addStaticMember(configured);

        discover(discovered);

        Assert.assertEquals(2, interceptor.getMembers().length);
        Assert.assertTrue(interceptor.members.contains(configured));
    }

    @Test
    public void testDifferentTcpPortOnSameHostIsDifferentNode() throws Exception {
        // Two nodes on one host that only differ in the TCP port, secure and UDP ports unused
        MemberImpl configured = member("localhost", 4001, 17);
        MemberImpl discovered = member("localhost", 4000, 1);
        interceptor.addStaticMember(configured);

        discover(discovered);

        Assert.assertEquals(2, interceptor.getMembers().length);
        Assert.assertTrue(interceptor.members.contains(configured));
    }

    @Test
    public void testSamePortOnDifferentHostsIsDifferentNode() throws Exception {
        MemberImpl configured = member("127.0.0.2", 4000, 17);
        MemberImpl discovered = member("127.0.0.1", 4000, 1);
        interceptor.addStaticMember(configured);

        discover(discovered);

        Assert.assertEquals("Two hosts may share a port", 2, interceptor.getMembers().length);
        Assert.assertTrue(interceptor.members.contains(configured));
    }

    @Test
    public void testConfiguredMemberRestoredWhenDiscoveredMemberDisappears() throws Exception {
        MemberImpl configured = member("localhost", 4001, 17);
        MemberImpl discovered = discoveredCopy(configured, 49);
        interceptor.addStaticMember(configured);
        discover(discovered);

        chain.members = new Member[0];
        interceptor.memberDisappeared(discovered);

        Member[] result = interceptor.getMembers();
        Assert.assertEquals("The configured node stays a member", 1, result.length);
        Assert.assertSame(configured, result[0]);
        Assert.assertEquals("The disappearance must be forwarded", 1, recorder.disappeared.size());
        Assert.assertSame(discovered, recorder.disappeared.get(0));
    }

    @Test
    public void testRestartedNodeReplacesConfiguredMemberAgain() throws Exception {
        MemberImpl configured = member("localhost", 4001, 17);
        MemberImpl before = discoveredCopy(configured, 49);
        MemberImpl after = discoveredCopy(configured, 65);
        interceptor.addStaticMember(configured);
        discover(before);

        // The restarted node announces a new unique id before the old one is dropped
        chain.members = new Member[] { before, after };
        interceptor.memberAdded(after);
        chain.members = new Member[] { after };
        interceptor.memberDisappeared(before);

        Member[] result = interceptor.getMembers();
        Assert.assertEquals("The node must be returned exactly once", 1, result.length);
        Assert.assertSame(after, result[0]);
        Assert.assertTrue("The configured member must not be restored", interceptor.members.isEmpty());
    }

    @Test
    public void testConfiguredMemberAddedAfterDiscoveryIsReplaced() throws Exception {
        MemberImpl configured = member("localhost", 4001, 17);
        MemberImpl discovered = discoveredCopy(configured, 49);
        chain.members = new Member[] { discovered };

        interceptor.addStaticMember(configured);

        Member[] result = interceptor.getMembers();
        Assert.assertEquals(1, result.length);
        Assert.assertSame(discovered, result[0]);
    }

    @Test
    public void testRemovedConfiguredMemberIsNotRestored() throws Exception {
        MemberImpl configured = member("localhost", 4001, 17);
        MemberImpl discovered = discoveredCopy(configured, 49);
        interceptor.addStaticMember(configured);
        discover(discovered);

        interceptor.removeStaticMember(configured);
        chain.members = new Member[0];
        interceptor.memberDisappeared(discovered);

        Assert.assertEquals(0, interceptor.getMembers().length);
    }

    @Test
    public void testGetMembersAllStatic() throws Exception {
        MemberImpl first = member("localhost", 4000, 1);
        MemberImpl second = member("localhost", 4001, 17);
        interceptor.addStaticMember(first);
        interceptor.addStaticMember(second);
        interceptor.addStaticMember(first);

        Assert.assertEquals("Each static member must be returned exactly once", 2, interceptor.getMembers().length);
    }

    @Test
    public void testGetMembersCollidesSameDestinationTwiceStatic() throws Exception {
        // The same node configured twice with different unique ids is a configuration error, the later entry is
        // ignored
        StaticMember first = member("localhost", 4000, 1);
        StaticMember second = member("localhost", 4000, 17);
        interceptor.addStaticMember(first);
        interceptor.addStaticMember(second);

        Member[] result = interceptor.getMembers();
        Assert.assertEquals("One destination must be returned once", 1, result.length);
        Assert.assertSame("The first entry is kept", first, result[0]);
    }

    @Test
    public void testPartiallyMatchingStaticMembersAreKept() throws Exception {
        // Reported as a configuration error, but not merged
        StaticMember first = member("localhost", 4000, 1);
        StaticMember second = member("localhost", 4000, 17);
        second.setSecurePort(4433);
        interceptor.addStaticMember(first);
        interceptor.addStaticMember(second);

        Assert.assertEquals(2, interceptor.getMembers().length);
    }

    @Test
    public void testStaticMembersOnSameHostWithDifferentTcpPortsAreKept() throws Exception {
        interceptor.addStaticMember(member("localhost", 4000, 1));
        interceptor.addStaticMember(member("localhost", 4001, 17));

        Assert.assertEquals(2, interceptor.getMembers().length);
    }

    @Test
    public void testDuplicateOfReplacedStaticMemberIsIgnored() throws Exception {
        StaticMember configured = member("localhost", 4001, 17);
        StaticMember duplicate = member("localhost", 4001, 33);
        MemberImpl discovered = discoveredCopy(configured, 49);
        interceptor.addStaticMember(configured);
        discover(discovered);

        // The first entry is currently replaced by the discovered member, the duplicate must not come back
        interceptor.addStaticMember(duplicate);

        Member[] result = interceptor.getMembers();
        Assert.assertEquals(1, result.length);
        Assert.assertSame(discovered, result[0]);
        Assert.assertTrue(interceptor.members.isEmpty());

        chain.members = new Member[0];
        interceptor.memberDisappeared(discovered);
        result = interceptor.getMembers();
        Assert.assertEquals(1, result.length);
        Assert.assertSame("The first entry is restored", configured, result[0]);
    }

    @Test
    public void testGetMembersAllDynamic() throws Exception {
        MemberImpl first = member("localhost", 4000, 1);
        MemberImpl second = member("localhost", 4001, 17);
        discover(first, second);

        Assert.assertEquals(2, interceptor.getMembers().length);
        Assert.assertEquals(2, recorder.added.size());
    }

    @Test
    public void testGetMembersMergesStaticAndDynamic() throws Exception {
        MemberImpl staticOnly = member("localhost", 4000, 1);
        MemberImpl dynamicOnly = member("localhost", 4002, 33);
        interceptor.addStaticMember(staticOnly);

        discover(dynamicOnly);

        Assert.assertEquals(2, interceptor.getMembers().length);
    }
}
