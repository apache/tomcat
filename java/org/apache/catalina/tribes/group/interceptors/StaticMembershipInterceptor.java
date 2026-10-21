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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelInterceptor;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.AbsoluteOrder;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.io.ChannelData;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Channel interceptor that manages static membership for a cluster.
 */
public class StaticMembershipInterceptor extends ChannelInterceptorBase implements StaticMembershipInterceptorMBean {

    private static final Log log = LogFactory.getLog(StaticMembershipInterceptor.class);
    /**
     * String manager for this class.
     */
    protected static final StringManager sm = StringManager.getManager(StaticMembershipInterceptor.class);

    /**
     * Payload sent when a member starts.
     */
    protected static final byte[] MEMBER_START = new byte[] { 76, 111, 99, 97, 108, 32, 83, 116, 97, 116, 105, 99, 77,
            101, 109, 98, 101, 114, 32, 78, 111, 116, 105, 102, 105, 99, 97, 116, 105, 111, 110, 32, 68, 97, 116, 97 };

    /**
     * Payload sent when a member shuts down.
     */
    protected static final byte[] MEMBER_STOP = new byte[] { 76, 111, 99, 97, 108, 32, 83, 116, 97, 116, 105, 99, 77,
            101, 109, 98, 101, 114, 32, 83, 104, 117, 116, 100, 111, 119, 110, 32, 68, 97, 116, 97 };

    /**
     * List of static members configured for this interceptor.
     */
    protected final ArrayList<Member> members = new ArrayList<>();
    /**
     * Static members that have been replaced by the dynamically discovered member for the same node, keyed by the
     * member that replaced them. Guarded by {@link #members}.
     */
    private final Map<Member,Member> replaced = new HashMap<>();
    /**
     * The local member for this interceptor.
     */
    protected Member localMember = null;

    /**
     * Default constructor.
     */
    public StaticMembershipInterceptor() {
        super();
    }

    /**
     * Adds a static member to the membership list.
     *
     * @param member the member to add
     */
    public void addStaticMember(Member member) {
        List<Member> withdrawn = new ArrayList<>();
        synchronized (members) {
            if (!members.contains(member) && !isDuplicateStaticMember(member)) {
                members.add(member);
                if (getNext() != null) {
                    // the node may already have been discovered dynamically
                    for (Member dynamic : super.getMembers()) {
                        withdrawn.addAll(replaceConfiguredMember(dynamic));
                    }
                }
            }
        }
        // outside of the lock, as the failure detector notifies the interceptors above
        withdrawConfiguredMembers(withdrawn);
    }

    /**
     * Checks if the static member to be added is for the same node as a static member that has been configured before,
     * including the ones that are currently replaced by a dynamically discovered member. The same host and enabled
     * ports with a different unique ID is a configuration error, and the later entry is ignored since the node would
     * otherwise receive every message twice. The same host with only some of the same enabled ports is also reported as
     * a configuration error, but both entries are kept. Must be called while holding the lock on {@link #members}.
     *
     * @param member the static member to be added
     *
     * @return {@code true} if a static member for the same node has been configured
     */
    private boolean isDuplicateStaticMember(Member member) {
        List<Member> configured = new ArrayList<>(members);
        configured.addAll(replaced.values());
        for (Member other : configured) {
            MemberMatch match = match(other, member);
            if (match == MemberMatch.SAME_NODE) {
                log.error(sm.getString("staticMembershipInterceptor.duplicateStaticMember", member, other));
                return true;
            } else if (match == MemberMatch.PORT_MISMATCH) {
                log.error(sm.getString("staticMembershipInterceptor.staticMemberPortMismatch", member, other));
            }
        }
        return false;
    }

    /**
     * Removes a static member from the membership list.
     *
     * @param member the member to remove
     */
    public void removeStaticMember(Member member) {
        synchronized (members) {
            members.remove(member);
            replaced.values().remove(member);
        }
    }

    /**
     * Sets the local member for this interceptor.
     *
     * @param member the local member
     */
    public void setLocalMember(Member member) {
        this.localMember = member;
        localMember.setLocal(true);
    }

    @Override
    public void messageReceived(ChannelMessage msg) {
        if (msg.getMessage().getLength() == MEMBER_START.length &&
                Arrays.equals(MEMBER_START, msg.getMessage().getBytes())) {
            // receive member start
            Member member = getMember(msg.getAddress());
            if (member != null) {
                super.memberAdded(member);
            }
        } else if (msg.getMessage().getLength() == MEMBER_STOP.length &&
                Arrays.equals(MEMBER_STOP, msg.getMessage().getBytes())) {
            // receive member shutdown
            Member member = getMember(msg.getAddress());
            if (member != null) {
                try {
                    member.setCommand(Member.SHUTDOWN_PAYLOAD);
                    super.memberDisappeared(member);
                } finally {
                    member.setCommand(new byte[0]);
                }
            }
        } else {
            super.messageReceived(msg);
        }
    }

    @Override
    public boolean hasMembers() {
        return super.hasMembers() || (!members.isEmpty());
    }

    @Override
    public Member[] getMembers() {
        if (members.isEmpty()) {
            return super.getMembers();
        } else {
            synchronized (members) {
                Member[] others = super.getMembers();
                Member[] result = new Member[members.size() + others.length];
                System.arraycopy(others, 0, result, 0, others.length);
                for (int i = 0; i < members.size(); i++) {
                    result[i + others.length] = members.get(i);
                }
                AbsoluteOrder.absoluteOrder(result);
                return result;
            } // sync
        } // end if
    }

    /**
     * Replaces the statically configured member for a node with the member for the same node that was discovered
     * dynamically. The two represent the same node when the host and all of the enabled ports (TCP, secure and UDP) are
     * the same. The unique ID of a dynamically discovered member is generated when the node starts, so it can not be
     * known by the static configuration, and without the replacement the node would be present twice. If the host and
     * only some of the enabled ports match, the static configuration is not consistent with the node and is reported as
     * a configuration error.
     *
     * @param dynamic the member that was discovered dynamically
     *
     * @return the replaced configured members, which have to be withdrawn by the caller, outside of the lock on
     *             {@link #members}
     */
    private List<Member> replaceConfiguredMember(Member dynamic) {
        List<Member> withdrawn = new ArrayList<>();
        synchronized (members) {
            // The node may have been replaced earlier by a member that is no longer current, for example when the
            // node was restarted and now announces a new unique ID
            Member previous = null;
            Member previousConfigured = null;
            for (Map.Entry<Member,Member> entry : replaced.entrySet()) {
                MemberMatch match = match(entry.getValue(), dynamic);
                if (match == MemberMatch.SAME_NODE) {
                    previous = entry.getKey();
                    previousConfigured = entry.getValue();
                } else if (match == MemberMatch.PORT_MISMATCH) {
                    log.error(
                            sm.getString("staticMembershipInterceptor.memberPortMismatch", entry.getValue(), dynamic));
                }
            }
            if (previous != null) {
                replaced.remove(previous);
                replaced.put(dynamic, previousConfigured);
            }
            for (int i = 0; i < members.size();) {
                Member configured = members.get(i);
                MemberMatch match = match(configured, dynamic);
                if (match == MemberMatch.SAME_NODE) {
                    members.remove(i);
                    replaced.put(dynamic, configured);
                    withdrawn.add(configured);
                } else {
                    if (match == MemberMatch.PORT_MISMATCH) {
                        log.error(sm.getString("staticMembershipInterceptor.memberPortMismatch", configured, dynamic));
                    }
                    i++;
                }
            }
        }
        return withdrawn;
    }

    /**
     * Withdraws configured members that have been replaced by a dynamically discovered member from the failure detector
     * above this interceptor. The failure detector has to forget them, as it would otherwise keep them as suspects and
     * add them again next to the discovered member, and it tells the interceptors above it that they disappeared.
     * Without a failure detector the configured members are not withdrawn.
     *
     * @param withdrawn the configured members to withdraw
     */
    private void withdrawConfiguredMembers(List<Member> withdrawn) {
        if (withdrawn.isEmpty()) {
            return;
        }
        TcpFailureDetector failureDetector = getFailureDetector();
        if (failureDetector != null) {
            for (Member configured : withdrawn) {
                failureDetector.forgetMember(configured);
            }
        }
    }

    /**
     * Finds the closest failure detector above this interceptor.
     *
     * @return the failure detector, or {@code null} if there is none
     */
    private TcpFailureDetector getFailureDetector() {
        ChannelInterceptor prev = getPrevious();
        while (prev != null) {
            if (prev instanceof TcpFailureDetector failureDetector) {
                return failureDetector;
            }
            prev = prev.getPrevious();
        }
        return null;
    }

    private enum MemberMatch {
        /** Different nodes. */
        DIFFERENT_NODE,
        /** The host and all ports match. */
        SAME_NODE,
        /** The host matches and some, but not all, of the ports match. */
        PORT_MISMATCH
    }

    /*
     * Only ports that are used by at least one of the members are compared. A port that neither member uses is -1 on
     * both, and must not make two nodes with different TCP ports on the same host look partially identical.
     */
    private static MemberMatch match(Member configured, Member dynamic) {
        if (!Arrays.equals(configured.getHost(), dynamic.getHost())) {
            return MemberMatch.DIFFERENT_NODE;
        }
        int used = 0;
        int matching = 0;
        int[][] ports =
                { { configured.getPort(), dynamic.getPort() }, { configured.getSecurePort(), dynamic.getSecurePort() },
                        { configured.getUdpPort(), dynamic.getUdpPort() } };
        for (int[] pair : ports) {
            if (pair[0] >= 0 || pair[1] >= 0) {
                used++;
                if (pair[0] == pair[1]) {
                    matching++;
                }
            }
        }
        if (used == 0 || matching == 0) {
            return MemberMatch.DIFFERENT_NODE;
        } else if (matching == used) {
            return MemberMatch.SAME_NODE;
        } else {
            return MemberMatch.PORT_MISMATCH;
        }
    }

    @Override
    public void memberAdded(Member member) {
        // The lock on members is only held to update the state. The failure detector verifies the member over the
        // network and notifies the interceptors above, which must not happen while holding the lock.
        List<Member> withdrawn;
        boolean replacing;
        synchronized (members) {
            withdrawn = replaceConfiguredMember(member);
            replacing = !withdrawn.isEmpty() || replaced.containsKey(member);
        }
        // the configured member is withdrawn before the member replacing it is announced
        withdrawConfiguredMembers(withdrawn);
        super.memberAdded(member);
        if (replacing) {
            // The member may have disappeared while it was being announced, and the disappearance may have been
            // handled before the announcement. The failure detector would then keep a member that is gone.
            boolean stale;
            synchronized (members) {
                stale = !replaced.containsKey(member);
            }
            if (stale) {
                TcpFailureDetector failureDetector = getFailureDetector();
                if (failureDetector != null) {
                    failureDetector.forgetMember(member);
                }
            }
        }
    }

    @Override
    public void memberDisappeared(Member member) {
        synchronized (members) {
            // the dynamic member is gone, the node stays a member as it was configured
            Member configured = replaced.remove(member);
            if (configured != null && !members.contains(configured)) {
                members.add(configured);
            }
        }
        super.memberDisappeared(member);
    }

    @Override
    public Member getMember(Member mbr) {
        synchronized (members) {
            int index = members.indexOf(mbr);
            if (index >= 0) {
                return members.get(index);
            }
        }
        return super.getMember(mbr);
    }

    @Override
    public Member getLocalMember(boolean incAlive) {
        if (this.localMember != null) {
            return localMember;
        } else {
            return super.getLocalMember(incAlive);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sends notifications upwards.
     */
    @Override
    public void start(int svc) throws ChannelException {
        if ((Channel.SND_RX_SEQ & svc) == Channel.SND_RX_SEQ) {
            super.start(Channel.SND_RX_SEQ);
        }
        if ((Channel.SND_TX_SEQ & svc) == Channel.SND_TX_SEQ) {
            super.start(Channel.SND_TX_SEQ);
        }
        ScheduledExecutorService executor = getChannel().getUtilityExecutor();
        Member[] configured;
        synchronized (members) {
            configured = members.toArray(new Member[0]);
        }
        for (final Member member : configured) {
            Runnable r = () -> {
                synchronized (members) {
                    if (!members.contains(member)) {
                        // replaced by the dynamically discovered member in the meantime
                        return;
                    }
                }
                // the configured member is not a dynamically discovered member
                StaticMembershipInterceptor.super.memberAdded(member);
                if (getfirstInterceptor().getMember(member) != null) {
                    sendLocalMember(new Member[] { member });
                }
            };
            executor.execute(r);
        }
        super.start(svc & (~Channel.SND_RX_SEQ) & (~Channel.SND_TX_SEQ));

        // check required interceptors
        TcpFailureDetector failureDetector = getFailureDetector();
        TcpPingInterceptor pingInterceptor = null;
        ChannelInterceptor prev = getPrevious();
        while (prev != null) {
            if (prev instanceof TcpPingInterceptor) {
                pingInterceptor = (TcpPingInterceptor) prev;
            }
            prev = prev.getPrevious();
        }
        if (failureDetector == null) {
            log.warn(sm.getString("staticMembershipInterceptor.no.failureDetector"));
        }
        if (pingInterceptor == null) {
            log.warn(sm.getString("staticMembershipInterceptor.no.pingInterceptor"));
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sends local member shutdown.
     */
    @Override
    public void stop(int svc) throws ChannelException {
        // Sends local member shutdown.
        Member[] members = getfirstInterceptor().getMembers();
        sendShutdown(members);
        super.stop(svc);
    }

    /**
     * Sends a local member start message to the given members.
     *
     * @param members the members to notify
     */
    protected void sendLocalMember(Member[] members) {
        try {
            sendMemberMessage(members, MEMBER_START);
        } catch (ChannelException cx) {
            log.warn(sm.getString("staticMembershipInterceptor.sendLocalMember.failed"), cx);
        }
    }

    /**
     * Sends a shutdown message to the given members.
     *
     * @param members the members to notify
     */
    protected void sendShutdown(Member[] members) {
        try {
            sendMemberMessage(members, MEMBER_STOP);
        } catch (ChannelException cx) {
            log.warn(sm.getString("staticMembershipInterceptor.sendShutdown.failed"), cx);
        }
    }

    /**
     * Returns the first (bottom-most) interceptor in the chain.
     *
     * @return the first channel interceptor
     */
    protected ChannelInterceptor getfirstInterceptor() {
        ChannelInterceptor result;
        ChannelInterceptor now = this;
        do {
            result = now;
            now = now.getPrevious();
        } while (now.getPrevious() != null);
        return result;
    }

    /**
     * Sends a member message to the given members.
     *
     * @param members the members to send to
     * @param message the message payload
     *
     * @throws ChannelException if sending fails
     */
    protected void sendMemberMessage(Member[] members, byte[] message) throws ChannelException {
        if (members == null || members.length == 0) {
            return;
        }
        ChannelData data = new ChannelData(true);
        data.setAddress(getLocalMember(false));
        data.setTimestamp(System.currentTimeMillis());
        data.setOptions(getOptionFlag());
        data.setMessage(new XByteBuffer(message, false));
        super.sendMessage(members, data, null);
    }
}