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
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.group.GroupChannel;
import org.apache.catalina.tribes.membership.StaticMember;

/**
 * Verifies that the TCP liveness checks of the TcpFailureDetector are performed outside the
 * membership monitor, and that the check results are applied correctly once the monitor is
 * re-acquired.
 */
public class TestTcpFailureDetectorLivenessChecks {

    /*
     * Overrides memberAlive(Member) to record whether the membership monitor was held at the
     * moment of the call and to return a configurable result, so the tests do not need real
     * network I/O.
     */
    private static class CheckRecordingDetector extends TcpFailureDetector {

        private final List<Boolean> monitorHeld = Collections.synchronizedList(new ArrayList<>());
        private volatile boolean alive = true;

        void setAlive(boolean value) {
            alive = value;
        }

        @Override
        protected boolean memberAlive(Member mbr) {
            monitorHeld.add(Boolean.valueOf(Thread.holdsLock(membership)));
            return alive;
        }

        void assertNoCheckRanUnderMonitor() {
            synchronized (monitorHeld) {
                Assert.assertFalse("No liveness check may run under the membership monitor",
                        monitorHeld.isEmpty());
                for (Boolean held : monitorHeld) {
                    Assert.assertFalse("A liveness check ran while holding the membership monitor",
                            held.booleanValue());
                }
            }
        }

        int checkCount() {
            return monitorHeld.size();
        }
    }

    // Downstream stand-in: provides the channel membership view the detector checks
    private static class FakeMembershipChain extends ChannelInterceptorBase {

        volatile Member[] members = new Member[0];
        volatile Member localMember;

        @Override
        public Member[] getMembers() {
            return members;
        }

        @Override
        public Member getLocalMember(boolean incAlive) {
            return localMember;
        }
    }

    // Upstream stand-in: records the member events the detector forwards
    private static class RecordingInterceptor extends ChannelInterceptorBase {

        final List<Member> added = Collections.synchronizedList(new ArrayList<>());
        final List<Member> disappeared = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void memberAdded(Member member) {
            added.add(member);
        }

        @Override
        public void memberDisappeared(Member member) {
            disappeared.add(member);
        }
    }

    private static CheckRecordingDetector createDetector(FakeMembershipChain chain,
            RecordingInterceptor recorder) throws Exception {
        CheckRecordingDetector detector = new CheckRecordingDetector();
        detector.setChannel(new GroupChannel());
        detector.setNext(chain);
        detector.setPrevious(recorder);
        chain.localMember = new StaticMember("localhost", 5000, -1);
        return detector;
    }

    @Test
    public void testMemberAddedAndDisappearedChecksWithoutMonitor() throws Exception {
        FakeMembershipChain chain = new FakeMembershipChain();
        RecordingInterceptor recorder = new RecordingInterceptor();
        CheckRecordingDetector detector = createDetector(chain, recorder);
        StaticMember member = new StaticMember("localhost", 4000, -1);

        detector.setAlive(true);
        detector.memberAdded(member);
        Assert.assertNotNull("A verified live member must be added", detector.getMember(member));
        Assert.assertEquals("The add must be forwarded once", 1, recorder.added.size());

        // A disappearance of a live member only marks it as suspect
        detector.setAlive(true);
        detector.memberDisappeared(member);
        Assert.assertNotNull("A live member must not be removed", detector.getMember(member));
        Assert.assertTrue("A live member must not disappear upwards", recorder.disappeared.isEmpty());
        Assert.assertTrue("The live member must be a remove suspect", detector.removeSuspects.containsKey(member));

        // A disappearance of a dead member removes it and notifies
        detector.setAlive(false);
        detector.memberDisappeared(member);
        Assert.assertNull("A dead member must be removed", detector.getMember(member));
        Assert.assertEquals("The disappearance must be forwarded once", 1, recorder.disappeared.size());

        detector.assertNoCheckRanUnderMonitor();
    }

    @Test
    public void testBasicCheckWithoutMonitor() throws Exception {
        FakeMembershipChain chain = new FakeMembershipChain();
        RecordingInterceptor recorder = new RecordingInterceptor();
        CheckRecordingDetector detector = createDetector(chain, recorder);
        StaticMember member = new StaticMember("localhost", 4000, -1);

        // The channel knows the member but the local membership table does not:
        // the basic check must verify and add it
        chain.members = new Member[] { member };
        detector.setAlive(true);
        detector.checkMembers(false);
        Assert.assertNotNull("A verified live member must be added", detector.getMember(member));
        Assert.assertEquals("The add must be forwarded", 1, recorder.added.size());
        int afterFirst = detector.checkCount();

        // A suspect member that is now dead must be removed and reported
        detector.memberDisappeared(member);
        Assert.assertNotNull("The live suspect must still be present", detector.getMember(member));
        detector.setAlive(false);
        detector.checkMembers(false);
        Assert.assertNull("The dead suspect must be removed", detector.getMember(member));
        Assert.assertEquals("The disappearance must be forwarded", 1, recorder.disappeared.size());
        Assert.assertTrue("The dead static member must be an add suspect",
                detector.addSuspects.containsKey(member));

        // An add suspect that is alive again must be re-added and reported
        detector.setAlive(true);
        detector.checkMembers(false);
        Assert.assertNotNull("The recovered suspect must be re-added", detector.getMember(member));
        Assert.assertEquals("The re-add must be forwarded", 2, recorder.added.size());

        Assert.assertTrue("No suspect bookkeeping may remain",
                detector.removeSuspects.isEmpty() && detector.addSuspects.isEmpty());
        Assert.assertTrue("Each scenario must have run its checks", detector.checkCount() > afterFirst);
        detector.assertNoCheckRanUnderMonitor();
    }

    @Test
    public void testForcedCheckWithoutMonitor() throws Exception {
        FakeMembershipChain chain = new FakeMembershipChain();
        RecordingInterceptor recorder = new RecordingInterceptor();
        CheckRecordingDetector detector = createDetector(chain, recorder);
        StaticMember member = new StaticMember("localhost", 4000, -1);

        chain.members = new Member[] { member };
        detector.setAlive(true);
        detector.checkMembers(true);
        Assert.assertNotNull("A verified live member must be added", detector.getMember(member));
        Assert.assertEquals("The add must be forwarded", 1, recorder.added.size());

        detector.setAlive(false);
        detector.checkMembers(true);
        Assert.assertNull("A dead member must be removed", detector.getMember(member));
        Assert.assertEquals("The disappearance must be forwarded", 1, recorder.disappeared.size());

        detector.assertNoCheckRanUnderMonitor();
    }
}
