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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.membership.MemberImpl;
import org.apache.catalina.tribes.membership.Membership;

public class TestNonBlockingCoordinatorPriority {

    @Test
    public void testHasHigherPriority() throws Exception {
        NonBlockingCoordinator coordinator = new NonBlockingCoordinator();

        // Under absolute ordering, lower host bytes mean a higher rank
        Member high = new MemberImpl("10.0.0.1", 4000, -1);
        Member low = new MemberImpl("10.0.0.2", 4000, -1);

        Assert.assertTrue("A membership with the higher priority member must win",
                coordinator.hasHigherPriority(new Member[] { low, high }, new Member[] { low }));
        Assert.assertFalse("A membership with the lower priority member must not win",
                coordinator.hasHigherPriority(new Member[] { low }, new Member[] { high, low }));
        Assert.assertFalse("Identical leaders must not win",
                coordinator.hasHigherPriority(new Member[] { high }, new Member[] { high }));
        Assert.assertFalse("An empty local membership must never be outranked",
                coordinator.hasHigherPriority(new Member[] { high }, new Member[0]));
        Assert.assertTrue("An empty complete membership is treated as higher priority",
                coordinator.hasHigherPriority(null, new Member[] { high }));
        Assert.assertFalse("A null local membership must never be outranked",
                coordinator.hasHigherPriority(new Member[] { high }, null));
    }

    @Test
    public void testIsHighestUsesSingleMembershipSnapshot() throws Exception {
        Member local = new MemberImpl("10.0.0.1", 4000, -1);
        Member other = new MemberImpl("10.0.0.2", 4000, -1);
        AtomicInteger snapshots = new AtomicInteger();
        Membership changingMembership = new Membership(local) {

            @Override
            public Member[] getMembers() {
                if (snapshots.incrementAndGet() == 1) {
                    return new Member[] { other };
                }
                return new Member[0];
            }
        };
        NonBlockingCoordinator coordinator = new NonBlockingCoordinator() {

            {
                membership = changingMembership;
            }

            @Override
            public Member getLocalMember(boolean incAlive) {
                return local;
            }
        };

        Assert.assertTrue(coordinator.isHighest());
        Assert.assertEquals(1, snapshots.get());
    }

    @Test
    public void testGetCoordinatorUsesSingleViewSnapshot() throws Exception {
        Member local = new MemberImpl("10.0.0.1", 4000, -1);
        Member other = new MemberImpl("10.0.0.2", 4000, -1);
        AtomicInteger snapshots = new AtomicInteger();
        Membership changingView = new Membership(local) {

            @Override
            public Member[] getMembers() {
                if (snapshots.incrementAndGet() == 1) {
                    return new Member[] { other };
                }
                return new Member[0];
            }
        };
        NonBlockingCoordinator coordinator = new NonBlockingCoordinator();

        Assert.assertNull(coordinator.getCoordinator());

        coordinator.view = changingView;
        Assert.assertSame(other, coordinator.getCoordinator());
        Assert.assertEquals(1, snapshots.get());

        coordinator.view = new Membership(local);
        Assert.assertNull(coordinator.getCoordinator());
    }

    @Test
    public void testGetViewUsesSingleViewSnapshot() throws Exception {
        Member local = new MemberImpl("10.0.0.1", 4000, -1);
        Member other = new MemberImpl("10.0.0.2", 4000, -1);
        AtomicReference<NonBlockingCoordinator> coordinatorReference = new AtomicReference<>();
        Membership changingView = new Membership(local) {

            @Override
            public boolean hasMembers() {
                coordinatorReference.get().view = null;
                return true;
            }

            @Override
            public Member[] getMembers() {
                return new Member[] { other };
            }
        };
        NonBlockingCoordinator coordinator = new NonBlockingCoordinator();
        coordinator.view = changingView;
        coordinatorReference.set(coordinator);

        Assert.assertArrayEquals(new Member[] { other }, coordinator.getView());
        Assert.assertSame(changingView, coordinator.view);
    }
}
