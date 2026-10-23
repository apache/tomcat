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

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.group.GroupChannel;
import org.apache.catalina.tribes.membership.MemberImpl;
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

    private static final int GATED_PORT = 4000;

    private static class GatedAddDetector extends TcpFailureDetector {

        private final CountDownLatch checkStarted = new CountDownLatch(1);
        private final CountDownLatch releaseCheck = new CountDownLatch(1);
        private volatile boolean gatedResult = true;
        // Result of the gated thread's checks after the first one, which was held
        private volatile boolean laterGatedResult = true;
        private final AtomicInteger gatedChecks = new AtomicInteger();
        // When set, only checks run by this thread are gated and all others return otherResult
        private volatile Thread gatedThread;
        private volatile boolean otherResult = true;

        @Override
        protected boolean memberAlive(Member mbr) {
            if (gatedThread != null) {
                if (Thread.currentThread() != gatedThread) {
                    return otherResult;
                }
            } else if (mbr.getPort() != GATED_PORT) {
                return true;
            }
            if (gatedChecks.incrementAndGet() > 1 && gatedThread != null) {
                return laterGatedResult;
            }
            checkStarted.countDown();
            try {
                if (!releaseCheck.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release liveness check");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return gatedResult;
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

    @Test
    public void testShutdownInvalidatesInFlightAddCheck() throws Exception {
        FakeMembershipChain chain = new FakeMembershipChain();
        RecordingInterceptor recorder = new RecordingInterceptor();
        GatedAddDetector detector = new GatedAddDetector();
        detector.setChannel(new GroupChannel());
        detector.setNext(chain);
        detector.setPrevious(recorder);
        chain.localMember = new StaticMember("localhost", 5000, -1);
        StaticMember member = new StaticMember("localhost", 4000, -1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread addThread = new Thread(() -> {
            try {
                detector.memberAdded(member);
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        addThread.start();
        Assert.assertTrue("Liveness check did not start",
                detector.checkStarted.await(10, TimeUnit.SECONDS));

        member.setCommand(Member.SHUTDOWN_PAYLOAD);
        detector.memberDisappeared(member);
        detector.releaseCheck.countDown();
        addThread.join(10000);

        Assert.assertFalse("Member add thread did not finish", addThread.isAlive());
        Assert.assertNull("Member add failed unexpectedly", failure.get());
        Assert.assertNull("A stale successful check must not resurrect a member after shutdown",
                detector.getMember(member));
        Assert.assertTrue("A stale add must not be forwarded", recorder.added.isEmpty());
    }

    @Test
    public void testEventOfOtherMemberDoesNotInvalidateAddCheck() throws Exception {
        FakeMembershipChain chain = new FakeMembershipChain();
        RecordingInterceptor recorder = new RecordingInterceptor();
        GatedAddDetector detector = new GatedAddDetector();
        detector.setChannel(new GroupChannel());
        detector.setNext(chain);
        detector.setPrevious(recorder);
        chain.localMember = new StaticMember("localhost", 5000, -1);
        StaticMember gated = new StaticMember("localhost", GATED_PORT, -1);
        StaticMember other = new StaticMember("localhost", 4001, -1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread addThread = new Thread(() -> {
            try {
                detector.memberAdded(gated);
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        addThread.start();
        Assert.assertTrue("Liveness check did not start",
                detector.checkStarted.await(10, TimeUnit.SECONDS));

        // Events of other members, including a disappearance, are processed meanwhile
        detector.memberAdded(other);
        detector.memberDisappeared(other);
        detector.releaseCheck.countDown();
        addThread.join(10000);

        Assert.assertFalse("Member add thread did not finish", addThread.isAlive());
        Assert.assertNull("Member add failed unexpectedly", failure.get());
        Assert.assertNotNull("An event of another member must not drop the add", detector.getMember(gated));
        Assert.assertTrue("The add must be forwarded", recorder.added.contains(gated));
    }

    @Test
    public void testLaterEventOfSameMemberInvalidatesAddCheck() throws Exception {
        FakeMembershipChain chain = new FakeMembershipChain();
        RecordingInterceptor recorder = new RecordingInterceptor();
        GatedAddDetector detector = new GatedAddDetector();
        detector.setChannel(new GroupChannel());
        detector.setNext(chain);
        detector.setPrevious(recorder);
        chain.localMember = new StaticMember("localhost", 5000, -1);
        StaticMember member = new StaticMember("localhost", GATED_PORT, -1);

        Thread addThread = new Thread(() -> detector.memberAdded(member));
        addThread.start();
        Assert.assertTrue("Liveness check did not start",
                detector.checkStarted.await(10, TimeUnit.SECONDS));

        // A non-shutdown disappearance of a member not yet in the table is ignored but still
        // supersedes the running add check
        detector.memberDisappeared(member);
        detector.releaseCheck.countDown();
        addThread.join(10000);

        Assert.assertFalse("Member add thread did not finish", addThread.isAlive());
        Assert.assertNull("A later event for the member supersedes the running check",
                detector.getMember(member));
        Assert.assertTrue("A superseded add must not be forwarded", recorder.added.isEmpty());
    }

    /*
     * Runs a basic check whose probe of the member is held, while the same member is reported by the
     * membership service, which runs its own (ungated) check with the given result. Then the held probe is
     * released with its own result.
     */
    private void runBasicCheckWithConcurrentAdd(boolean probeAlive, boolean eventAlive, GatedAddDetector detector,
            RecordingInterceptor recorder, FakeMembershipChain chain, StaticMember member) throws Exception {
        detector.setChannel(new GroupChannel());
        detector.setNext(chain);
        detector.setPrevious(recorder);
        chain.localMember = new StaticMember("localhost", 5000, -1);
        chain.members = new Member[] { member };
        detector.gatedResult = probeAlive;
        // the same heartbeat re-checks add suspects after the held probe, which must agree with the event
        detector.laterGatedResult = eventAlive;
        detector.otherResult = eventAlive;
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread checkThread = new Thread(() -> {
            try {
                detector.checkMembers(false);
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        detector.gatedThread = checkThread;
        checkThread.start();
        Assert.assertTrue("Liveness check did not start",
                detector.checkStarted.await(10, TimeUnit.SECONDS));

        // The basic check is verifying the member, which must not be visible until verified
        Assert.assertNull("An unverified member must not be in the table", detector.getMember(member));
        detector.memberAdded(member);
        detector.releaseCheck.countDown();
        checkThread.join(10000);

        Assert.assertFalse("Check thread did not finish", checkThread.isAlive());
        Assert.assertNull("Check failed unexpectedly", failure.get());
    }

    @Test
    public void testBasicCheckAddsVerifiedMemberOnlyAfterProbe() throws Exception {
        FakeMembershipChain chain = new FakeMembershipChain();
        RecordingInterceptor recorder = new RecordingInterceptor();
        GatedAddDetector detector = new GatedAddDetector();
        detector.setChannel(new GroupChannel());
        detector.setNext(chain);
        detector.setPrevious(recorder);
        chain.localMember = new StaticMember("localhost", 5000, -1);
        StaticMember member = new StaticMember("localhost", GATED_PORT, -1);
        chain.members = new Member[] { member };
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread checkThread = new Thread(() -> {
            try {
                detector.checkMembers(false);
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        detector.gatedThread = checkThread;
        checkThread.start();
        Assert.assertTrue("Liveness check did not start",
                detector.checkStarted.await(10, TimeUnit.SECONDS));
        Assert.assertNull("An unverified member must not be in the table", detector.getMember(member));
        Assert.assertTrue("An unverified member must not be reported", recorder.added.isEmpty());

        detector.releaseCheck.countDown();
        checkThread.join(10000);

        Assert.assertFalse("Check thread did not finish", checkThread.isAlive());
        Assert.assertNull("Check failed unexpectedly", failure.get());
        Assert.assertNotNull("A verified member must be added", detector.getMember(member));
        Assert.assertEquals("A verified member must be reported once", 1, recorder.added.size());
    }

    @Test
    public void testBasicCheckDeadProbeDoesNotOverrideLaterAdd() throws Exception {
        FakeMembershipChain chain = new FakeMembershipChain();
        RecordingInterceptor recorder = new RecordingInterceptor();
        GatedAddDetector detector = new GatedAddDetector();
        StaticMember member = new StaticMember("localhost", GATED_PORT, -1);

        runBasicCheckWithConcurrentAdd(false, true, detector, recorder, chain, member);

        Assert.assertNotNull("The later add must win over the older dead probe", detector.getMember(member));
        Assert.assertEquals("The member must be reported exactly once", 1, recorder.added.size());
    }

    @Test
    public void testBasicCheckLiveProbeDoesNotOverrideLaterAdd() throws Exception {
        FakeMembershipChain chain = new FakeMembershipChain();
        RecordingInterceptor recorder = new RecordingInterceptor();
        GatedAddDetector detector = new GatedAddDetector();
        StaticMember member = new StaticMember("localhost", GATED_PORT, -1);

        runBasicCheckWithConcurrentAdd(true, true, detector, recorder, chain, member);

        Assert.assertNotNull("The member must stay in the table", detector.getMember(member));
        Assert.assertEquals("The member must be reported exactly once, by the later add", 1,
                recorder.added.size());
    }

    @Test
    public void testBasicCheckLiveProbeDoesNotOverrideLaterFailedAdd() throws Exception {
        FakeMembershipChain chain = new FakeMembershipChain();
        RecordingInterceptor recorder = new RecordingInterceptor();
        GatedAddDetector detector = new GatedAddDetector();
        StaticMember member = new StaticMember("localhost", GATED_PORT, -1);

        runBasicCheckWithConcurrentAdd(true, false, detector, recorder, chain, member);

        Assert.assertNull("The later failed add must win over the older live probe", detector.getMember(member));
        Assert.assertTrue("A superseded probe must not be reported", recorder.added.isEmpty());
        Assert.assertTrue("The failed static member must be an add suspect",
                detector.addSuspects.containsKey(member));
    }

    @Test
    public void testHeartbeatChangesInvalidateInFlightAddCheck() throws Exception {
        FakeMembershipChain chain = new FakeMembershipChain();
        RecordingInterceptor recorder = new RecordingInterceptor();
        GatedAddDetector detector = new GatedAddDetector();
        detector.setChannel(new GroupChannel());
        detector.setNext(chain);
        detector.setPrevious(recorder);
        chain.localMember = new StaticMember("localhost", 5000, -1);
        StaticMember member = new StaticMember("localhost", GATED_PORT, -1);
        chain.members = new Member[] { member };
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread addThread = new Thread(() -> {
            try {
                detector.memberAdded(member);
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        detector.gatedThread = addThread;
        addThread.start();
        Assert.assertTrue("Liveness check did not start",
                detector.checkStarted.await(10, TimeUnit.SECONDS));

        // Forced heartbeat checks add the member and then find it dead again, while the
        // check started by the member event is still running
        detector.otherResult = true;
        detector.checkMembers(true);
        Assert.assertNotNull("The forced check must add the member", detector.getMember(member));
        detector.otherResult = false;
        detector.checkMembers(true);
        Assert.assertNull("The forced check must remove the member", detector.getMember(member));

        detector.releaseCheck.countDown();
        addThread.join(10000);

        Assert.assertFalse("Member add thread did not finish", addThread.isAlive());
        Assert.assertNull("Member add failed unexpectedly", failure.get());
        Assert.assertNull("A stale live result must not resurrect a member the heartbeat found dead",
                detector.getMember(member));
        Assert.assertEquals("Only the forced check may have reported the member", 1, recorder.added.size());
    }

    @Test
    public void testForgetMemberIsNotAddedAgainByHeartbeat() throws Exception {
        FakeMembershipChain chain = new FakeMembershipChain();
        RecordingInterceptor recorder = new RecordingInterceptor();
        CheckRecordingDetector detector = createDetector(chain, recorder);
        StaticMember member = new StaticMember("localhost", 4000, -1);
        detector.setAlive(true);
        detector.memberAdded(member);
        Assert.assertNotNull(detector.getMember(member));

        detector.forgetMember(member);

        Assert.assertNull("The member must be removed", detector.getMember(member));
        Assert.assertEquals("The disappearance must be forwarded once", 1, recorder.disappeared.size());
        Assert.assertTrue("The member must not become a suspect", detector.addSuspects.isEmpty());
        Assert.assertTrue(detector.removeSuspects.isEmpty());
        detector.checkMembers(false);
        Assert.assertNull("The heartbeat must not add the member again", detector.getMember(member));
        Assert.assertEquals(1, recorder.added.size());
    }

    @Test
    public void testForgetMemberClearsSuspectThatWasNeverAdded() throws Exception {
        FakeMembershipChain chain = new FakeMembershipChain();
        RecordingInterceptor recorder = new RecordingInterceptor();
        CheckRecordingDetector detector = createDetector(chain, recorder);
        StaticMember member = new StaticMember("localhost", 4000, -1);
        detector.setAlive(false);
        detector.memberAdded(member);
        Assert.assertTrue(detector.addSuspects.containsKey(member));

        detector.forgetMember(member);

        Assert.assertTrue("The suspect must be forgotten", detector.addSuspects.isEmpty());
        Assert.assertTrue("A member the detector never accepted is not reported", recorder.disappeared.isEmpty());
        detector.setAlive(true);
        detector.checkMembers(false);
        Assert.assertNull("The heartbeat must not add the member", detector.getMember(member));
        Assert.assertTrue(recorder.added.isEmpty());
    }

    @Test
    public void testForgetMemberSupersedesRunningAddCheck() throws Exception {
        FakeMembershipChain chain = new FakeMembershipChain();
        RecordingInterceptor recorder = new RecordingInterceptor();
        GatedAddDetector detector = new GatedAddDetector();
        detector.setChannel(new GroupChannel());
        detector.setNext(chain);
        detector.setPrevious(recorder);
        chain.localMember = new StaticMember("localhost", 5000, -1);
        StaticMember member = new StaticMember("localhost", GATED_PORT, -1);
        Thread addThread = new Thread(() -> detector.memberAdded(member));
        addThread.start();
        Assert.assertTrue("Liveness check did not start",
                detector.checkStarted.await(10, TimeUnit.SECONDS));

        detector.forgetMember(member);
        detector.releaseCheck.countDown();
        addThread.join(10000);

        Assert.assertFalse("Member add thread did not finish", addThread.isAlive());
        Assert.assertNull("A forgotten member must not be added by a running check", detector.getMember(member));
        Assert.assertTrue(recorder.added.isEmpty());
    }

    /*
     * Timeouts too large for the int socket timeouts must be capped rather than overflow to a negative value that
     * the socket rejects, which would make a live member look dead.
     */
    @Test
    public void testMemberAliveWithTimeoutsAboveIntegerMax() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            TcpFailureDetector detector = new TcpFailureDetector();
            detector.setChannel(new GroupChannel());
            MemberImpl member = new MemberImpl("127.0.0.1", server.getLocalPort(), 0);

            Assert.assertTrue(detector.memberAlive(member, new byte[0], false, false,
                    Long.MAX_VALUE, Long.MAX_VALUE, 0));
        }
    }
}
