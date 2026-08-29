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
package org.apache.catalina.ha.session;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.ha.CatalinaCluster;
import org.apache.catalina.tribes.Member;

/**
 * Tests that {@link DeltaManager#getAllClusterSessions()} resets the
 * {@code noContextManagerReceived} flag before requesting session state. If
 * the flag is not reset, a previously received
 * {@code EVT_ALL_SESSION_NOCONTEXTMANAGER} causes the state transfer wait to
 * exit immediately on every subsequent request, even when the cluster node
 * now has a context manager and sends session state.
 */
public class TestDeltaManagerStateTransfer {

    private static CatalinaCluster createClusterWithOneMember() {
        // findSessionMasterMember() uses members[0] as the state transfer
        // source so the array must contain a non-null member.
        Member member = EasyMock.createNiceMock(Member.class);
        CatalinaCluster cluster = EasyMock.createNiceMock(CatalinaCluster.class);
        EasyMock.expect(cluster.getMembers()).andReturn(new Member[] { member }).anyTimes();
        // send() is a void method: a nice mock performs no action.
        EasyMock.replay(cluster);
        return cluster;
    }

    @Test
    public void testNoContextManagerFlagResetOnNewStateRequest() throws Exception {
        DeltaManager manager = new DeltaManager();
        manager.setCluster(createClusterWithOneMember());
        // Keep the wait short: the test does not provide a real reply so the
        // wait will end via the timeout path.
        manager.setStateTransferTimeout(1);

        // Simulate a previously received EVT_ALL_SESSION_NOCONTEXTMANAGER
        manager.setNoContextManagerReceived(true);

        manager.getAllClusterSessions();

        // The new state request must reset the flag so the wait is not
        // skipped immediately on this and subsequent transfers.
        Assert.assertFalse("noContextManagerReceived should be reset when requesting session state",
                manager.isNoContextManagerReceived());
    }

    @Test
    public void testStateTransferredResetOnNewStateRequest() throws Exception {
        DeltaManager manager = new DeltaManager();
        manager.setCluster(createClusterWithOneMember());
        manager.setStateTransferTimeout(1);

        // Simulate a completed previous transfer
        manager.setStateTransferred(true);

        manager.getAllClusterSessions();

        Assert.assertFalse("stateTransferred should be reset when requesting session state",
                manager.getStateTransferred());
    }
}
