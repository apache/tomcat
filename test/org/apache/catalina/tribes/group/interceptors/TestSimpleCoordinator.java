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
import org.apache.catalina.tribes.membership.MemberImpl;

public class TestSimpleCoordinator {

    private static class RecordingCoordinator extends SimpleCoordinator {

        private final List<Member[]> views = Collections.synchronizedList(new ArrayList<>());

        @Override
        protected void viewChange(Member[] view) {
            views.add(view);
        }
    }

    // Downstream stand-in: the dynamically discovered membership
    private static class DynamicMembershipChain extends ChannelInterceptorBase {

        private volatile Member[] members = new Member[0];
        private volatile Member localMember;

        @Override
        public Member[] getMembers() {
            return members;
        }

        @Override
        public Member getLocalMember(boolean incAlive) {
            return localMember;
        }
    }

    private static RecordingCoordinator createCoordinator(Member local) {
        RecordingCoordinator coordinator = new RecordingCoordinator();
        DynamicMembershipChain chain = new DynamicMembershipChain();
        chain.localMember = local;
        coordinator.setNext(chain);
        return coordinator;
    }

    @Test
    public void testViewInstallAndAccessors() throws Exception {
        MemberImpl local = new MemberImpl("localhost", 4000, -1);
        MemberImpl remote = new MemberImpl("localhost", 4001, -1);
        RecordingCoordinator coordinator = createCoordinator(local);
        DynamicMembershipChain chain = (DynamicMembershipChain) coordinator.getNext();
        chain.members = new Member[] { remote };

        Assert.assertNull("No view before the first install", coordinator.getView());

        coordinator.memberAdded(remote);

        Member[] view = coordinator.getView();
        Assert.assertNotNull("The view must be installed", view);
        Assert.assertEquals("The view holds the remote member and the local member", 2, view.length);
        Assert.assertSame(view[0], coordinator.getCoordinator());
        Assert.assertEquals("isCoordinator must agree with the view",
                local.equals(view[0]), coordinator.isCoordinator());
        Assert.assertEquals("A single view change must be reported", 1, coordinator.views.size());
        Assert.assertSame(view, coordinator.views.get(0));
    }

    @Test
    public void testConcurrentInstallsOfSameViewNotifyOnce() throws Exception {
        MemberImpl local = new MemberImpl("localhost", 4000, -1);
        MemberImpl remote = new MemberImpl("localhost", 4001, -1);
        RecordingCoordinator coordinator = createCoordinator(local);
        DynamicMembershipChain chain = (DynamicMembershipChain) coordinator.getNext();
        chain.members = new Member[] { remote };

        // Two membership callbacks on different threads computing the same view
        Thread first = new Thread(() -> coordinator.memberAdded(remote));
        Thread second = new Thread(() -> coordinator.memberAdded(remote));
        first.start();
        second.start();
        first.join(60000);
        second.join(60000);
        Assert.assertFalse("First install thread did not finish", first.isAlive());
        Assert.assertFalse("Second install thread did not finish", second.isAlive());

        Assert.assertEquals("The view must be installed", 2, coordinator.getView().length);
        Assert.assertEquals("Installing the same view concurrently must notify once",
                1, coordinator.views.size());
    }
}
