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

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Session;
import org.apache.catalina.ha.CatalinaCluster;
import org.apache.catalina.tribes.Member;
import org.easymock.EasyMock;


/**
 * Tests for the <code>sendAllSessionsSize</code> validation in
 * {@link DeltaManager} and the batched session sending in
 * {@link DeltaManager#handleGET_ALL_SESSIONS}.
 */
public class TestDeltaManagerBatch {

    /**
     * Tracks how many times {@link #sendSessions(Member, Session[], long)} is
     * invoked and what payloads it receives.
     */
    private static class TesterDeltaManager extends DeltaManager {

        private int sendCount;
        private int lastBatchSize = -1;
        private final Session[] sessions;

        TesterDeltaManager(Session[] sessions) {
            this.sessions = sessions;
        }

        @Override
        public Session[] findSessions() {
            return sessions;
        }

        @Override
        protected void sendSessions(Member sender, Session[] currentSessions, long sendTimestamp)
                throws IOException {
            sendCount++;
            lastBatchSize = currentSessions.length;
        }
    }

    private static Session[] createSessions(int count) {
        Session[] result = new Session[count];
        TesterDeltaManager manager = new TesterDeltaManager(new Session[0]);
        for (int i = 0; i < count; i++) {
            result[i] = new DeltaSession(manager);
        }
        return result;
    }

    private static TesterDeltaManager createManager(Session[] sessions, boolean sendAllSessions,
            int sendAllSessionsSize) {
        TesterDeltaManager manager = new TesterDeltaManager(sessions);
        manager.setSendAllSessions(sendAllSessions);
        manager.setSendAllSessionsSize(sendAllSessionsSize);
        // Avoid the inter-batch sleep in the batching loop so the test does not
        // depend on the default sendAllSessionsWaitTime.
        manager.setSendAllSessionsWaitTime(0);

        // The send operations at the end of handleGET_ALL_SESSIONS require a
        // cluster reference. Use a mock so no real cluster is needed.
        CatalinaCluster cluster = EasyMock.createNiceMock(CatalinaCluster.class);
        EasyMock.replay(cluster);
        manager.setCluster(cluster);
        return manager;
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSendAllSessionsSizeZeroRejected() {
        new TesterDeltaManager(new Session[0]).setSendAllSessionsSize(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSendAllSessionsSizeNegativeRejected() {
        new TesterDeltaManager(new Session[0]).setSendAllSessionsSize(-5);
    }

    @Test
    public void testSendAllSessionsSizePositiveAccepted() {
        TesterDeltaManager manager = new TesterDeltaManager(new Session[0]);
        manager.setSendAllSessionsSize(1);
        Assert.assertEquals(1, manager.getSendAllSessionsSize());
    }

    @Test
    public void testPositiveBatchSizeSplitsSessions() throws Exception {
        TesterDeltaManager manager = createManager(createSessions(5), false, 2);

        manager.handleGET_ALL_SESSIONS(null, null);

        // 5 sessions in batches of 2 => 2+2+1 = 3 sends
        Assert.assertEquals(3, manager.sendCount);
    }

    @Test
    public void testSendAllSessionsIgnoresBatchSize() throws Exception {
        TesterDeltaManager manager = createManager(createSessions(3), true, 1);

        manager.handleGET_ALL_SESSIONS(null, null);

        // sendAllSessions is true so all sessions are sent in a single batch
        Assert.assertEquals(1, manager.sendCount);
        Assert.assertEquals(3, manager.lastBatchSize);
    }

    @Test
    public void testNoSessionsNotSent() throws Exception {
        TesterDeltaManager manager = createManager(createSessions(0), false, 2);

        manager.handleGET_ALL_SESSIONS(null, null);

        // No sessions means the batching loop body never runs
        Assert.assertEquals(0, manager.sendCount);
    }
}
