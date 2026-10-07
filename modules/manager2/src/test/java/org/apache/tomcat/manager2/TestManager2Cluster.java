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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.ha.session.DeltaManager;
import org.apache.catalina.ha.tcp.SimpleTcpCluster;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.tribes.group.GroupChannel;
import org.apache.catalina.tribes.membership.StaticMember;
import org.apache.catalina.tribes.membership.StaticMembershipService;
import org.apache.catalina.tribes.transport.ReplicationTransmitter;
import org.apache.catalina.tribes.transport.nio.NioReceiver;
import org.apache.catalina.tribes.transport.nio.PooledParallelSender;

/**
 * Integration test for the cluster view against a live cluster: the default host of the test instance gets a
 * {@code SimpleTcpCluster} with a static membership service before the server starts, and a tiny distributable web
 * application provides the clustered context. A second bare {@code GroupChannel} (not a Tomcat instance) runs as the
 * peer node: static membership only registers members that answer the membership exchange, so a live peer is needed
 * for the remote member to appear. Deliberately static membership, loopback bind only and no multicast, keeping the
 * test CI safe.
 */
public class TestManager2Cluster extends TomcatBaseTest {

    @Test
    public void testClusterSnapshotWithLiveStaticMembership() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.setAddDefaultWebXmlToWebapp(false);

        // The tiny clustered application: its distributable web.xml makes the
        // context use the cluster's DeltaManager template.
        File appDir = new File(getTemporaryDirectory(), "clustered-app");
        createClusteredWebapp(appDir);
        Context clustered = tomcat.addWebapp(null, "/clustered", appDir.getAbsolutePath());

        int nodePort = freePort();
        int peerPort = freePort();

        // --- The cluster of the Tomcat host ---------------------------------
        SimpleTcpCluster cluster = new SimpleTcpCluster();
        cluster.setClusterName("test-cluster");

        GroupChannel channel = new GroupChannel();
        // The cluster names the channel "<clusterName>-Channel" on start; the
        // channel name seeds the membership id.

        NioReceiver receiver = new NioReceiver();
        receiver.setAddress("127.0.0.1");
        receiver.setPort(nodePort);
        channel.setChannelReceiver(receiver);

        ReplicationTransmitter transmitter = new ReplicationTransmitter();
        transmitter.setTransport(new PooledParallelSender());
        channel.setChannelSender(transmitter);

        StaticMembershipService membership = new StaticMembershipService();
        // Static membership must know the local member (it is not registered
        // among the static ones); the peer is the one remote member.
        membership.setLocalMember(new StaticMember("127.0.0.1", nodePort, -1L));
        membership.addStaticMember(new StaticMember("127.0.0.1", peerPort, -1L));
        channel.setMembershipService(membership);

        cluster.setChannel(channel);
        cluster.setManagerTemplate(new DeltaManager());

        Host host = tomcat.getHost();
        host.setCluster(cluster);

        // The base test class pre-creates a StandardManager for every context
        // at start, which prevents StandardContext from picking up the
        // cluster's manager template on its own; assign it explicitly.
        clustered.setManager(cluster.createManager(clustered.getName()));

        tomcat.start();

        // --- The peer node: a bare channel on the second port --------------
        GroupChannel peerChannel = new GroupChannel();
        // Same channel name (and therefore the same membership id) as the
        // cluster channel of the Tomcat node.
        peerChannel.setName(channel.getName());
        NioReceiver peerReceiver = new NioReceiver();
        peerReceiver.setAddress("127.0.0.1");
        peerReceiver.setPort(peerPort);
        peerChannel.setChannelReceiver(peerReceiver);
        ReplicationTransmitter peerTransmitter = new ReplicationTransmitter();
        peerTransmitter.setTransport(new PooledParallelSender());
        peerChannel.setChannelSender(peerTransmitter);
        StaticMembershipService peerMembership = new StaticMembershipService();
        peerMembership.setLocalMember(new StaticMember("127.0.0.1", peerPort, -1L));
        peerMembership.addStaticMember(new StaticMember("127.0.0.1", nodePort, -1L));
        peerChannel.setMembershipService(peerMembership);
        peerChannel.start(org.apache.catalina.tribes.Channel.DEFAULT);
        try {
            // The membership exchange is asynchronous: wait for the peer.
            long deadline = System.currentTimeMillis() + 15000;
            Map<String, Object> snapshot = ClusterSnapshot.snapshot(host);
            while (System.currentTimeMillis() < deadline && findMember(snapshot, peerPort) == null) {
                Thread.sleep(200);
                snapshot = ClusterSnapshot.snapshot(host);
            }

            Assert.assertEquals(Boolean.TRUE, snapshot.get("clustered"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> clusters = (List<Map<String, Object>>) snapshot.get("clusters");
            Assert.assertEquals(1, clusters.size());
            Map<String, Object> entry = clusters.get(0);
            Assert.assertEquals("test-cluster", entry.get("name"));
            Assert.assertEquals(SimpleTcpCluster.class.getName(), entry.get("className"));
            Assert.assertEquals("host localhost", entry.get("owner"));
            Assert.assertEquals("STARTED", entry.get("state"));
            Assert.assertEquals("async", entry.get("sendOptions"));

            @SuppressWarnings("unchecked")
            Map<String, Object> membershipSummary = (Map<String, Object>) entry.get("membership");
            Assert.assertNotNull(membershipSummary);
            Assert.assertEquals(StaticMembershipService.class.getName(), membershipSummary.get("className"));
            Assert.assertFalse("static membership has no multicast address",
                    membershipSummary.containsKey("mcastAddr"));

            Assert.assertEquals("tcp://127.0.0.1:" + nodePort, entry.get("localMember"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> members = (List<Map<String, Object>>) entry.get("members");
            Map<String, Object> local = findMemberByPort(members, nodePort);
            Assert.assertNotNull("the local member must be listed", local);
            Assert.assertEquals(Boolean.TRUE, local.get("local"));
            Assert.assertEquals(Boolean.TRUE, local.get("ready"));

            // The remote static member, once it has answered the membership
            // exchange.
            Map<String, Object> remote = findMemberByPort(members, peerPort);
            Assert.assertNotNull("the live static peer must be listed", remote);
            // The member was received over the wire: its hostname comes back
            // in the byte representation, so match the name only in part.
            String remoteName = (String) remote.get("name");
            Assert.assertTrue("unexpected member name " + remoteName,
                    remoteName.startsWith("tcp://") && remoteName.endsWith(":" + peerPort));
            Assert.assertNotNull(remote.get("host"));
            Assert.assertEquals(Integer.valueOf(peerPort), remote.get("port"));
            Assert.assertEquals(Integer.valueOf(-1), remote.get("securePort"));
            Assert.assertEquals(Integer.valueOf(-1), remote.get("udpPort"));
            Assert.assertEquals(Boolean.FALSE, remote.get("local"));
            Assert.assertEquals(Boolean.TRUE, remote.get("ready"));
            Assert.assertEquals(Boolean.FALSE, remote.get("suspect"));
            Assert.assertEquals(Boolean.FALSE, remote.get("failing"));
            // Static membership does not count membership pings.
            Assert.assertEquals(Integer.valueOf(0), remote.get("msgs"));

            @SuppressWarnings("unchecked")
            Map<String, Object> replication = (Map<String, Object>) entry.get("replication");
            Assert.assertNotNull("the distributable context must produce a replication block", replication);
            Assert.assertEquals(Integer.valueOf(1), replication.get("contexts"));
            Assert.assertEquals(Long.valueOf(0), replication.get("requests"));
            Assert.assertEquals(Long.valueOf(0), replication.get("sendRequests"));
            Assert.assertEquals(Long.valueOf(0), replication.get("filteredRequests"));
            Assert.assertEquals(Long.valueOf(0), replication.get("crossContextSends"));
            Assert.assertEquals(Long.valueOf(0), replication.get("totalSendTimeMs"));
            Assert.assertNull(replication.get("avgSendTimeMs"));
            Assert.assertNull(replication.get("lastSendTime"));
            Assert.assertEquals(Long.valueOf(0), replication.get("messagesSent"));
            Assert.assertEquals(Long.valueOf(0), replication.get("messagesReceived"));
            Assert.assertEquals(Integer.valueOf(0), replication.get("receivedQueueSize"));
            Assert.assertEquals(Long.valueOf(0), replication.get("rejectedSessions"));
            Assert.assertEquals(Long.valueOf(0), replication.get("duplicates"));
        } finally {
            peerChannel.stop(org.apache.catalina.tribes.Channel.DEFAULT);
        }
    }


    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findMember(Map<String, Object> snapshot, int port) {
        List<Map<String, Object>> clusters = (List<Map<String, Object>>) snapshot.get("clusters");
        if (clusters.isEmpty()) {
            return null;
        }
        return findMemberByPort((List<Map<String, Object>>) clusters.get(0).get("members"), port);
    }


    private static Map<String, Object> findMemberByPort(List<Map<String, Object>> members, int port) {
        if (members == null) {
            return null;
        }
        for (Map<String, Object> member : members) {
            if (Integer.valueOf(port).equals(member.get("port"))) {
                return member;
            }
        }
        return null;
    }


    /**
     * Reserve a free loopback port.
     */
    private static int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return probe.getLocalPort();
        }
    }


    /**
     * A minimal web application: distributable (so the context uses the cluster's manager template) with a default
     * servlet, since the test instance has no global web.xml.
     */
    private static void createClusteredWebapp(File dir) throws IOException {
        File webInf = new File(dir, "WEB-INF");
        Assert.assertTrue(webInf.mkdirs());

        try (PrintWriter pw = new PrintWriter(new File(webInf, "web.xml"), StandardCharsets.UTF_8)) {
            pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            pw.println("<web-app xmlns=\"https://jakarta.ee/xml/ns/jakartaee\"");
            pw.println("        version=\"6.2\" metadata-complete=\"true\">");
            pw.println("  <distributable/>");
            pw.println("  <servlet>");
            pw.println("    <servlet-name>default</servlet-name>");
            pw.println("    <servlet-class>org.apache.catalina.servlets.DefaultServlet</servlet-class>");
            pw.println("  </servlet>");
            pw.println("  <servlet-mapping>");
            pw.println("    <servlet-name>default</servlet-name>");
            pw.println("    <url-pattern>/</url-pattern>");
            pw.println("  </servlet-mapping>");
            pw.println("</web-app>");
        }

        try (PrintWriter pw = new PrintWriter(new File(dir, "index.html"), StandardCharsets.UTF_8)) {
            pw.println("<html><body>clustered test app</body></html>");
        }
    }
}
