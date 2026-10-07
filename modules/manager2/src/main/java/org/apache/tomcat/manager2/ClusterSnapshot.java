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
package org.apache.tomcat.manager2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.catalina.Cluster;
import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.Manager;
import org.apache.catalina.Valve;
import org.apache.catalina.ha.CatalinaCluster;
import org.apache.catalina.ha.session.ClusterManagerBase;
import org.apache.catalina.ha.session.DeltaManager;
import org.apache.catalina.ha.tcp.ReplicationValve;
import org.apache.catalina.ha.tcp.SimpleTcpCluster;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipService;
import org.apache.catalina.tribes.group.GroupChannel;
import org.apache.catalina.tribes.membership.McastService;
import org.apache.catalina.tribes.membership.MemberImpl;


/**
 * Collects the cluster membership of this node, the membership configuration summary and the session replication
 * activity and turns them into {@code Map}/{@code List} structures that are JSON-serialized by {@link Json}. The
 * clusters are discovered through the containers: the {@link Engine} of the host the API is installed in and each of
 * its hosts, deduplicated by object identity since clusters are inherited down the container tree.
 *
 * <p>The byte-level traffic of the Tribes channel is not instrumented anywhere in Tribes itself, so the replication
 * activity is represented by the {@link ReplicationValve} and {@link DeltaManager} counters plus, per member, the
 * membership echo counter of {@link MemberImpl#getMsgCount()} (which stays {@code 0} under static membership).</p>
 */
public final class ClusterSnapshot {


    /**
     * Build the cluster view served by {@code GET /api/status/cluster}.
     *
     * @param host the Host the API is installed in (may be null, in which case nothing can be discovered)
     *
     * @return the snapshot: {@code {"clustered": boolean, "clusters": [...]}}
     */
    public static Map<String, Object> snapshot(Host host) {
        List<Map<String, Object>> clusters = new ArrayList<>();

        Engine engine = host != null && host.getParent() instanceof Engine e ? e : null;
        if (engine != null) {
            // A cluster set on a parent container is inherited by its children: the same instance is seen through
            // every container below it, so the discovery deduplicates by identity and keeps the owner where the
            // cluster is actually attached.
            Set<Cluster> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            addCluster(clusters, seen, engine.getCluster(), "engine " + engine.getName(), engine);
            for (Container child : engine.findChildren()) {
                if (child instanceof Host childHost) {
                    addCluster(clusters, seen, childHost.getCluster(), "host " + childHost.getName(), childHost);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clustered", Boolean.valueOf(!clusters.isEmpty()));
        result.put("clusters", clusters);
        return result;
    }


    private static void addCluster(List<Map<String, Object>> clusters, Set<Cluster> seen, Cluster cluster,
            String owner, Container ownerContainer) {
        if (cluster == null || !seen.add(cluster)) {
            return;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", cluster.getClusterName());
        result.put("className", cluster.getClass().getName());
        result.put("owner", owner);
        if (cluster instanceof Lifecycle lifecycle) {
            result.put("state", lifecycle.getState().toString());
        }
        if (cluster instanceof SimpleTcpCluster simpleTcpCluster) {
            result.put("sendOptions", simpleTcpCluster.getChannelSendOptionsName());
        }

        if (cluster instanceof CatalinaCluster catalinaCluster) {
            result.put("membership", membership(catalinaCluster));
            Member localMember = catalinaCluster.getLocalMember();
            result.put("localMember", localMember != null ? localMember.getName() : null);
            List<Map<String, Object>> members = new ArrayList<>();
            // Tribes' member lists exclude the local member; show it first so
            // that the view always includes the node itself.
            if (localMember != null) {
                members.add(member(localMember));
            }
            Member[] memberArray = catalinaCluster.getMembers();
            if (memberArray != null) {
                for (Member member : memberArray) {
                    if (member != null && (localMember == null || !member.equals(localMember))) {
                        members.add(member(member));
                    }
                }
            }
            result.put("members", members);
            Map<String, Object> replication = replication(catalinaCluster, ownerContainer);
            if (replication != null) {
                result.put("replication", replication);
            }
        }

        clusters.add(result);
    }


    /**
     * The membership service summary: its class name and, for a multicast service, the multicast address and port.
     *
     * @param cluster the cluster
     *
     * @return the summary, or {@code null} when the channel exposes no membership service
     */
    private static Map<String, Object> membership(CatalinaCluster cluster) {
        Object channel = cluster.getChannel();
        if (!(channel instanceof GroupChannel groupChannel)) {
            return null;
        }
        MembershipService membershipService = groupChannel.getMembershipService();
        if (membershipService == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("className", membershipService.getClass().getName());
        if (membershipService instanceof McastService mcastService) {
            result.put("mcastAddr", mcastService.getAddress());
            result.put("mcastPort", Integer.valueOf(mcastService.getPort()));
        }
        return result;
    }


    private static Map<String, Object> member(Member member) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", member.getName());
        if (member instanceof MemberImpl memberImpl) {
            result.put("host", memberImpl.getHostname());
        } else {
            result.put("host", hostFromName(member.getName()));
        }
        result.put("port", Integer.valueOf(member.getPort()));
        result.put("securePort", Integer.valueOf(member.getSecurePort()));
        result.put("udpPort", Integer.valueOf(member.getUdpPort()));
        result.put("local", Boolean.valueOf(member.isLocal()));
        result.put("ready", Boolean.valueOf(member.isReady()));
        result.put("suspect", Boolean.valueOf(member.isSuspect()));
        result.put("failing", Boolean.valueOf(member.isFailing()));
        result.put("aliveMs", Long.valueOf(member.getMemberAliveTime()));
        if (member instanceof MemberImpl memberImpl) {
            result.put("serviceStartTime", Long.valueOf(memberImpl.getServiceStartTime()));
            result.put("msgs", Integer.valueOf(memberImpl.getMsgCount()));
        }
        return result;
    }


    /**
     * The host part of a member name in the {@code tcp://host:port} format, for members that are not
     * {@link MemberImpl} and expose no hostname of their own.
     */
    private static String hostFromName(String name) {
        if (name == null) {
            return null;
        }
        String rest = name;
        int scheme = rest.indexOf("://");
        if (scheme >= 0) {
            rest = rest.substring(scheme + 3);
        }
        int slash = rest.indexOf('/');
        if (slash >= 0) {
            rest = rest.substring(0, slash);
        }
        int colon = rest.lastIndexOf(':');
        return colon >= 0 ? rest.substring(0, colon) : rest;
    }


    /**
     * The aggregate replication activity of a cluster: the {@link ReplicationValve} counters of the pipeline the
     * cluster registered its valves in (a cluster registers its valves on its own container, which covers all the
     * contexts below it) plus the message counters and health indicators of every clustered (cluster manager) context
     * under the owner container.
     *
     * @param cluster        the cluster
     * @param ownerContainer the container the cluster is attached to
     *
     * @return the aggregate, or {@code null} when no clustered context exists
     */
    private static Map<String, Object> replication(CatalinaCluster cluster, Container ownerContainer) {
        long requests = 0;
        long sendRequests = 0;
        long filteredRequests = 0;
        long crossContextSends = 0;
        long totalSendTimeMs = 0;
        long lastSendTime = 0;
        for (Valve valve : ownerContainer.getPipeline().getValves()) {
            if (valve instanceof ReplicationValve replicationValve) {
                requests += replicationValve.getNrOfRequests();
                sendRequests += replicationValve.getNrOfSendRequests();
                filteredRequests += replicationValve.getNrOfFilterRequests();
                crossContextSends += replicationValve.getNrOfCrossContextSendRequests();
                totalSendTimeMs += replicationValve.getTotalSendTime();
                lastSendTime = Math.max(lastSendTime, replicationValve.getLastSendTime());
            }
        }

        int contexts = 0;
        long messagesSent = 0;
        long messagesReceived = 0;
        int receivedQueueSize = 0;
        long rejectedSessions = 0;
        long duplicates = 0;
        for (Context context : contextsUnder(ownerContainer)) {
            Manager manager = context.getManager();
            if (manager instanceof DeltaManager deltaManager && usesCluster(deltaManager, cluster, context)) {
                contexts++;
                messagesSent += deltaManager.getCounterSend_EVT_GET_ALL_SESSIONS();
                messagesSent += deltaManager.getCounterSend_EVT_SESSION_ACCESSED();
                messagesSent += deltaManager.getCounterSend_EVT_SESSION_CREATED();
                messagesSent += deltaManager.getCounterSend_EVT_SESSION_DELTA();
                messagesSent += deltaManager.getCounterSend_EVT_SESSION_EXPIRED();
                messagesSent += deltaManager.getCounterSend_EVT_ALL_SESSION_DATA();
                messagesSent += deltaManager.getCounterSend_EVT_ALL_SESSION_TRANSFERCOMPLETE();
                messagesSent += deltaManager.getCounterSend_EVT_CHANGE_SESSION_ID();
                messagesReceived += deltaManager.getCounterReceive_EVT_ALL_SESSION_DATA();
                messagesReceived += deltaManager.getCounterReceive_EVT_GET_ALL_SESSIONS();
                messagesReceived += deltaManager.getCounterReceive_EVT_SESSION_ACCESSED();
                messagesReceived += deltaManager.getCounterReceive_EVT_SESSION_CREATED();
                messagesReceived += deltaManager.getCounterReceive_EVT_SESSION_DELTA();
                messagesReceived += deltaManager.getCounterReceive_EVT_SESSION_EXPIRED();
                messagesReceived += deltaManager.getCounterReceive_EVT_ALL_SESSION_TRANSFERCOMPLETE();
                messagesReceived += deltaManager.getCounterReceive_EVT_CHANGE_SESSION_ID();
                messagesReceived += deltaManager.getCounterReceive_EVT_ALL_SESSION_NOCONTEXTMANAGER();
                receivedQueueSize += deltaManager.getReceivedQueueSize();
                rejectedSessions += deltaManager.getRejectedSessions();
                // The only duplicated-session-id counter the DeltaManager exposes: sessions that were replaced by a
                // replicated session with the same id (the "duplicates" of the manager's MBean descriptor).
                duplicates += deltaManager.getSessionReplaceCounter();
            }
        }
        if (contexts == 0) {
            return null;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contexts", Integer.valueOf(contexts));
        result.put("requests", Long.valueOf(requests));
        result.put("sendRequests", Long.valueOf(sendRequests));
        result.put("filteredRequests", Long.valueOf(filteredRequests));
        result.put("crossContextSends", Long.valueOf(crossContextSends));
        result.put("totalSendTimeMs", Long.valueOf(totalSendTimeMs));
        // The replication valve does not record an average itself; compute it from the aggregate.
        result.put("avgSendTimeMs", sendRequests > 0
                ? Double.valueOf(Math.round((double) totalSendTimeMs / (double) sendRequests * 100) / 100.0) : null);
        result.put("lastSendTime", lastSendTime > 0 ? Long.valueOf(lastSendTime) : null);
        result.put("messagesSent", Long.valueOf(messagesSent));
        result.put("messagesReceived", Long.valueOf(messagesReceived));
        result.put("receivedQueueSize", Integer.valueOf(receivedQueueSize));
        result.put("rejectedSessions", Long.valueOf(rejectedSessions));
        result.put("duplicates", Long.valueOf(duplicates));
        return result;
    }


    /**
     * Whether the context's cluster manager belongs to the given cluster. A manager that has not started yet falls
     * back to the cluster inherited by its context.
     */
    private static boolean usesCluster(ClusterManagerBase manager, Cluster cluster, Context context) {
        if (manager.getCluster() != null) {
            return manager.getCluster() == cluster;
        }
        return context.getCluster() == cluster;
    }


    private static List<Context> contextsUnder(Container container) {
        List<Context> result = new ArrayList<>();
        for (Container child : container.findChildren()) {
            if (child instanceof Context context) {
                result.add(context);
            } else {
                result.addAll(contextsUnder(child));
            }
        }
        return result;
    }


    private ClusterSnapshot() {
        // Utility class, do not instantiate
    }
}
