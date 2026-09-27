/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.manager2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Container;
import org.apache.catalina.Manager;
import org.apache.catalina.Valve;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.ha.CatalinaCluster;
import org.apache.catalina.ha.ClusterListener;
import org.apache.catalina.ha.ClusterManager;
import org.apache.catalina.ha.session.DeltaManager;
import org.apache.catalina.ha.tcp.ReplicationValve;
import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.GroupChannel;
import org.apache.catalina.tribes.membership.McastService;
import org.apache.catalina.tribes.membership.MemberImpl;
import org.apache.catalina.tribes.membership.StaticMembershipService;

/**
 * Unit tests for {@link ClusterSnapshot} against hand-written fakes (Tomcat-test style, no mocking library) and real
 * constructor-built {@link MemberImpl} instances. No channel is started.
 */
public class TestClusterSnapshot {

    @Test
    public void testUnclusteredShape() {
        // An engine without a cluster: not clustered.
        StandardHost host = hostWithEngine();
        Map<String, Object> snapshot = ClusterSnapshot.snapshot(host);
        Assert.assertEquals(Boolean.FALSE, snapshot.get("clustered"));
        Assert.assertEquals(new ArrayList<>(), snapshot.get("clusters"));
    }


    @Test
    public void testEmptyClusterShape() {
        StandardHost host = hostWithEngine();
        FakeCluster cluster = new FakeCluster("empty-cluster");
        host.setCluster(cluster);

        List<Map<String, Object>> clusters = clusters(ClusterSnapshot.snapshot(host));
        Assert.assertEquals(1, clusters.size());

        Map<String, Object> entry = clusters.get(0);
        Assert.assertEquals("empty-cluster", entry.get("name"));
        Assert.assertEquals(FakeCluster.class.getName(), entry.get("className"));
        Assert.assertEquals("host localhost", entry.get("owner"));
        // The fake is not a Lifecycle and has no channel: no state, no
        // membership summary.
        Assert.assertFalse(entry.containsKey("state"));
        Assert.assertNull(entry.get("membership"));
        Assert.assertNull(entry.get("localMember"));
        Assert.assertEquals(new ArrayList<>(), entry.get("members"));
        // No clustered context: no replication block.
        Assert.assertFalse(entry.containsKey("replication"));
    }


    @Test
    public void testMemberFieldMappingAndFlags() throws Exception {
        StandardHost host = hostWithEngine();
        FakeCluster cluster = new FakeCluster("mapped");

        MemberImpl local = new MemberImpl("10.20.30.40", 4000, 123456L);
        local.setLocal(true);
        local.setSecurePort(4443);
        local.setUdpPort(4001);
        local.setServiceStartTime(1690000000000L);

        TestMemberImpl suspect = new TestMemberImpl("10.20.30.41", 4000, 999L);
        suspect.setSuspectFlag(true);
        TestMemberImpl failing = new TestMemberImpl("10.20.30.42", 4000, 1L);
        failing.setFailingFlag(true);
        TestMemberImpl notReady = new TestMemberImpl("10.20.30.43", 4000, 2L);
        notReady.setReadyFlag(false);

        cluster.members = new Member[] { local, suspect, failing, notReady, new FakeMember() };
        cluster.localMember = local;
        host.setCluster(cluster);

        Map<String, Object> entry = clusters(ClusterSnapshot.snapshot(host)).get(0);
        Assert.assertEquals("tcp://10.20.30.40:4000", entry.get("localMember"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members = (List<Map<String, Object>>) entry.get("members");
        Assert.assertEquals(5, members.size());

        Map<String, Object> localMap = members.get(0);
        Assert.assertEquals("tcp://10.20.30.40:4000", localMap.get("name"));
        Assert.assertEquals("10.20.30.40", localMap.get("host"));
        Assert.assertEquals(Integer.valueOf(4000), localMap.get("port"));
        Assert.assertEquals(Integer.valueOf(4443), localMap.get("securePort"));
        Assert.assertEquals(Integer.valueOf(4001), localMap.get("udpPort"));
        Assert.assertEquals(Boolean.TRUE, localMap.get("local"));
        Assert.assertEquals(Boolean.TRUE, localMap.get("ready"));
        Assert.assertEquals(Boolean.FALSE, localMap.get("suspect"));
        Assert.assertEquals(Boolean.FALSE, localMap.get("failing"));
        Assert.assertEquals(Long.valueOf(123456L), localMap.get("aliveMs"));
        Assert.assertEquals(Long.valueOf(1690000000000L), localMap.get("serviceStartTime"));
        Assert.assertEquals(Integer.valueOf(0), localMap.get("msgs"));

        Assert.assertEquals(Boolean.TRUE, members.get(1).get("suspect"));
        Assert.assertEquals(Boolean.TRUE, members.get(1).get("ready"));
        Assert.assertEquals(Boolean.TRUE, members.get(2).get("failing"));
        Assert.assertEquals(Boolean.FALSE, members.get(3).get("ready"));

        // A plain Member implementation: the hostname is parsed from the name
        // and the MemberImpl extras (serviceStartTime, msgs) are absent.
        Map<String, Object> plain = members.get(4);
        Assert.assertEquals("tcp://10.20.30.99:4000", plain.get("name"));
        Assert.assertEquals("10.20.30.99", plain.get("host"));
        Assert.assertEquals(Integer.valueOf(-1), plain.get("securePort"));
        Assert.assertEquals(Boolean.FALSE, plain.get("local"));
        Assert.assertFalse(plain.containsKey("serviceStartTime"));
        Assert.assertFalse(plain.containsKey("msgs"));
    }


    @Test
    public void testMembershipSummary() {
        StandardHost host = hostWithEngine();
        FakeCluster cluster = new FakeCluster("mcast");

        GroupChannel channel = new GroupChannel();
        McastService mcast = new McastService();
        mcast.setAddress("224.5.6.7");
        mcast.setPort(12345);
        channel.setMembershipService(mcast);
        cluster.channel = channel;
        host.setCluster(cluster);

        Map<String, Object> membership =
                membershipOf(clusters(ClusterSnapshot.snapshot(host)).get(0));
        Assert.assertEquals(McastService.class.getName(), membership.get("className"));
        Assert.assertEquals("224.5.6.7", membership.get("mcastAddr"));
        Assert.assertEquals(Integer.valueOf(12345), membership.get("mcastPort"));

        // A static membership service: the summary carries only the class.
        FakeCluster staticCluster = new FakeCluster("static");
        GroupChannel staticChannel = new GroupChannel();
        StaticMembershipService staticService = new StaticMembershipService();
        staticChannel.setMembershipService(staticService);
        staticCluster.channel = staticChannel;

        StandardHost host2 = hostWithEngine();
        host2.setCluster(staticCluster);
        Map<String, Object> staticMembership =
                membershipOf(clusters(ClusterSnapshot.snapshot(host2)).get(0));
        Assert.assertEquals(StaticMembershipService.class.getName(), staticMembership.get("className"));
        Assert.assertFalse(staticMembership.containsKey("mcastAddr"));
        Assert.assertFalse(staticMembership.containsKey("mcastPort"));

        // No channel at all: no summary.
        StandardHost host3 = hostWithEngine();
        host3.setCluster(new FakeCluster("nochannel"));
        Map<String, Object> entry = clusters(ClusterSnapshot.snapshot(host3)).get(0);
        Assert.assertNull(entry.get("membership"));
    }


    @Test
    public void testIdentityDeduplicationAcrossEngineAndHosts() {
        StandardEngine engine = new StandardEngine();
        engine.setName("Catalina");
        StandardHost hostA = host("localhost");
        StandardHost hostB = host("example.com");
        engine.addChild(hostA);
        engine.addChild(hostB);

        FakeCluster shared = new FakeCluster("shared");
        // A cluster set on the host is inherited by sibling lookups only
        // through the parent walk, so attach it to the engine: both hosts then
        // report the very same instance.
        engine.setCluster(shared);

        List<Map<String, Object>> clusters = clusters(ClusterSnapshot.snapshot(hostA));
        Assert.assertEquals(1, clusters.size());
        Assert.assertEquals("engine Catalina", clusters.get(0).get("owner"));

        // Distinct clusters per host are separate entries with the host owner.
        StandardEngine engine2 = new StandardEngine();
        engine2.setName("Catalina");
        StandardHost hostC = host("alpha");
        StandardHost hostD = host("beta");
        engine2.addChild(hostC);
        engine2.addChild(hostD);
        hostC.setCluster(new FakeCluster("cluster-c"));
        hostD.setCluster(new FakeCluster("cluster-d"));

        clusters = clusters(ClusterSnapshot.snapshot(hostC));
        Assert.assertEquals(2, clusters.size());
        Assert.assertEquals("host alpha", clusters.get(0).get("owner"));
        Assert.assertEquals("host beta", clusters.get(1).get("owner"));
    }


    @Test
    public void testReplicationBlock() {
        StandardHost host = hostWithEngine();
        FakeCluster cluster = new FakeCluster("repl");
        host.setCluster(cluster);
        host.getPipeline().addValve(new ReplicationValve());

        StandardContext context = new StandardContext();
        context.setPath("/clustered");
        DeltaManager manager = new DeltaManager();
        context.setManager(manager);
        host.addChild(context);

        Map<String, Object> entry = clusters(ClusterSnapshot.snapshot(host)).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> replication = (Map<String, Object>) entry.get("replication");
        Assert.assertNotNull("Expected a replication block", replication);
        Assert.assertEquals(Integer.valueOf(1), replication.get("contexts"));
        Assert.assertEquals(Long.valueOf(0), replication.get("requests"));
        Assert.assertEquals(Long.valueOf(0), replication.get("sendRequests"));
        Assert.assertEquals(Long.valueOf(0), replication.get("filteredRequests"));
        Assert.assertEquals(Long.valueOf(0), replication.get("crossContextSends"));
        Assert.assertEquals(Long.valueOf(0), replication.get("totalSendTimeMs"));
        // No send requests yet: no average, no last send.
        Assert.assertNull(replication.get("avgSendTimeMs"));
        Assert.assertNull(replication.get("lastSendTime"));
        Assert.assertEquals(Long.valueOf(0), replication.get("messagesSent"));
        Assert.assertEquals(Long.valueOf(0), replication.get("messagesReceived"));
        Assert.assertEquals(Integer.valueOf(0), replication.get("receivedQueueSize"));
        Assert.assertEquals(Long.valueOf(0), replication.get("rejectedSessions"));
        Assert.assertEquals(Long.valueOf(0), replication.get("duplicates"));
    }


    @Test
    public void testReplicationOmittedWithoutClusteredContext() {
        StandardHost host = hostWithEngine();
        FakeCluster cluster = new FakeCluster("norepl");
        host.setCluster(cluster);

        // A context with a regular manager is not a clustered one.
        StandardContext context = new StandardContext();
        context.setPath("/plain");
        host.addChild(context);

        Map<String, Object> entry = clusters(ClusterSnapshot.snapshot(host)).get(0);
        Assert.assertFalse(entry.containsKey("replication"));
    }


    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static StandardHost host(String name) {
        StandardHost host = new StandardHost();
        host.setName(name);
        return host;
    }


    /**
     * A host attached to a fresh (unstarted) engine without a cluster.
     */
    private static StandardHost hostWithEngine() {
        StandardEngine engine = new StandardEngine();
        engine.setName("Catalina");
        StandardHost host = host("localhost");
        engine.addChild(host);
        return host;
    }


    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> clusters(Map<String, Object> snapshot) {
        return (List<Map<String, Object>>) snapshot.get("clusters");
    }


    @SuppressWarnings("unchecked")
    private static Map<String, Object> membershipOf(Map<String, Object> clusterEntry) {
        return (Map<String, Object>) clusterEntry.get("membership");
    }


    // ------------------------------------------------------------------
    // Fakes
    // ------------------------------------------------------------------

    /**
     * A minimal {@link CatalinaCluster} implementation: only the accessors the
     * snapshot uses are functional, everything else is inert.
     */
    private static class FakeCluster implements CatalinaCluster {

        private final String name;
        private Container container;
        private Channel channel;
        private Member[] members = new Member[0];
        private Member localMember;

        FakeCluster(String name) {
            this.name = name;
        }

        @Override
        public String getClusterName() {
            return name;
        }

        @Override
        public void setClusterName(String clusterName) {
            // not needed
        }

        @Override
        public Container getContainer() {
            return container;
        }

        @Override
        public void setContainer(Container container) {
            this.container = container;
        }

        @Override
        public Channel getChannel() {
            return channel;
        }

        @Override
        public void setChannel(Channel channel) {
            this.channel = channel;
        }

        @Override
        public Member[] getMembers() {
            return members;
        }

        @Override
        public Member getLocalMember() {
            return localMember;
        }

        @Override
        public boolean hasMembers() {
            return members.length > 0;
        }

        @Override
        public void send(org.apache.catalina.ha.ClusterMessage msg) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(org.apache.catalina.ha.ClusterMessage msg, Member dest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(org.apache.catalina.ha.ClusterMessage msg, Member dest, int sendOptions) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addValve(Valve valve) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addClusterListener(ClusterListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeClusterListener(ClusterListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setClusterDeployer(org.apache.catalina.ha.ClusterDeployer deployer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public org.apache.catalina.ha.ClusterDeployer getClusterDeployer() {
            return null;
        }

        @Override
        public Map<String,ClusterManager> getManagers() {
            return Map.of();
        }

        @Override
        public Manager getManager(String name) {
            return null;
        }

        @Override
        public String getManagerName(String name, Manager manager) {
            return name;
        }

        @Override
        public Valve[] getValves() {
            return new Valve[0];
        }

        @Override
        public Manager createManager(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registerManager(Manager manager) {
            // not needed
        }

        @Override
        public void removeManager(Manager manager) {
            // not needed
        }

        @Override
        public void backgroundProcess() {
            // not needed
        }
    }


    /**
     * A {@link MemberImpl} with explicit state flags (the real flags come
     * from the sender state).
     */
    private static class TestMemberImpl extends MemberImpl {

        private static final long serialVersionUID = 1L;

        private boolean readyFlag = true;
        private boolean suspectFlag;
        private boolean failingFlag;

        public TestMemberImpl() {
            // Due to Externalizable
        }

        TestMemberImpl(String host, int port, long aliveTime) throws IOException {
            super(host, port, aliveTime);
        }

        void setReadyFlag(boolean ready) {
            readyFlag = ready;
        }

        void setSuspectFlag(boolean suspect) {
            suspectFlag = suspect;
        }

        void setFailingFlag(boolean failing) {
            failingFlag = failing;
        }

        @Override
        public boolean isReady() {
            return readyFlag;
        }

        @Override
        public boolean isSuspect() {
            return suspectFlag;
        }

        @Override
        public boolean isFailing() {
            return failingFlag;
        }
    }


    /**
     * A plain {@link Member} implementation that is not a {@link MemberImpl}.
     */
    private static class FakeMember implements Member {

        private static final long serialVersionUID = 1L;

        @Override
        public String getName() {
            return "tcp://10.20.30.99:4000";
        }

        @Override
        public int getPort() {
            return 4000;
        }

        @Override
        public int getSecurePort() {
            return -1;
        }

        @Override
        public int getUdpPort() {
            return -1;
        }

        @Override
        public long getMemberAliveTime() {
            return 7L;
        }

        @Override
        public void setMemberAliveTime(long memberAliveTime) {
            // not needed
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public boolean isSuspect() {
            return false;
        }

        @Override
        public boolean isFailing() {
            return false;
        }

        @Override
        public int getDataLength() {
            return 0;
        }

        @Override
        public boolean isLocal() {
            return false;
        }

        @Override
        public void setLocal(boolean local) {
            // not needed
        }

        @Override
        public byte[] getHost() {
            return new byte[] { 10, 20, 30, 99 };
        }

        @Override
        public byte[] getUniqueId() {
            return new byte[0];
        }

        @Override
        public byte[] getPayload() {
            return new byte[0];
        }

        @Override
        public void setPayload(byte[] payload) {
            // not needed
        }

        @Override
        public byte[] getCommand() {
            return new byte[0];
        }

        @Override
        public void setCommand(byte[] command) {
            // not needed
        }

        @Override
        public byte[] getDomain() {
            return new byte[0];
        }

        @Override
        public byte[] getData(boolean getalive) {
            return new byte[0];
        }

        @Override
        public byte[] getData(boolean getalive, boolean reset) {
            return new byte[0];
        }
    }
}
