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
package org.apache.catalina.tribes.membership;

import java.io.Serial;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelException.FaultyMember;
import org.apache.catalina.tribes.ChannelListener;
import org.apache.catalina.tribes.Heartbeat;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.Response;
import org.apache.catalina.tribes.group.RpcCallback;
import org.apache.catalina.tribes.group.RpcChannel;
import org.apache.catalina.tribes.util.Arrays;
import org.apache.catalina.tribes.util.ExceptionUtils;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Provider that manages static membership for a cluster.
 */
public class StaticMembershipProvider extends MembershipProviderBase
        implements RpcCallback, ChannelListener, Heartbeat {

    /**
     * String manager for this class.
     */
    protected static final StringManager sm = StringManager.getManager(StaticMembershipProvider.class);
    private static final Log log = LogFactory.getLog(StaticMembershipProvider.class);

    /**
     * The channel associated with this provider.
     */
    protected Channel channel;
    /**
     * RPC channel for sending messages.
     */
    protected RpcChannel rpcChannel;
    /**
     * Name of this membership group.
     */
    private String membershipName = null;
    /**
     * Byte array identifier for this membership group.
     */
    private byte[] membershipId = null;
    /**
     * List of static members for this cluster.
     */
    protected ArrayList<StaticMember> staticMembers;
    /**
     * Send options flag for channel messages.
     */
    protected int sendOptions = Channel.SEND_OPTIONS_ASYNCHRONOUS;
    /**
     * Time in milliseconds after which a member is considered expired.
     */
    protected long expirationTime = 5000;
    /**
     * Socket connection timeout in milliseconds.
     */
    protected int connectTimeout = 500;
    /**
     * RPC operation timeout in milliseconds.
     */
    protected long rpcTimeout = 3000;
    /**
     * Current start level bitmask.
     */
    protected int startLevel = 0;
    // for ping thread
    /**
     * Whether to use a background thread for pinging.
     */
    protected boolean useThread = false;
    /**
     * Interval between ping messages in milliseconds.
     */
    protected long pingInterval = 1000;
    /**
     * Whether the ping thread is currently running.
     */
    protected volatile boolean running = true;
    /**
     * Background ping thread instance.
     */
    protected PingThread thread = null;

    /**
     * Default constructor.
     */
    public StaticMembershipProvider() {
    }

    @Override
    public void init(Properties properties) throws Exception {
        String expirationTimeStr = properties.getProperty("expirationTime");
        this.expirationTime = Long.parseLong(expirationTimeStr);
        String connectTimeoutStr = properties.getProperty("connectTimeout");
        this.connectTimeout = Integer.parseInt(connectTimeoutStr);
        String rpcTimeouStr = properties.getProperty("rpcTimeout");
        this.rpcTimeout = Long.parseLong(rpcTimeouStr);
        this.membershipName = properties.getProperty("membershipName");
        this.membershipId = membershipName.getBytes(StandardCharsets.ISO_8859_1);
        membership = new Membership(service.getLocalMember(true));
        this.rpcChannel = new RpcChannel(this.membershipId, channel, this);
        this.channel.addChannelListener(this);
        String useThreadStr = properties.getProperty("useThread");
        this.useThread = Boolean.parseBoolean(useThreadStr);
        String pingIntervalStr = properties.getProperty("pingInterval");
        this.pingInterval = Long.parseLong(pingIntervalStr);
    }

    @Override
    public void start(int level) throws Exception {
        if (Channel.MBR_RX_SEQ == (level & Channel.MBR_RX_SEQ)) {
            // no-op
        }
        if (Channel.MBR_TX_SEQ == (level & Channel.MBR_TX_SEQ)) {
            // no-op
        }
        startLevel = (startLevel | level);
        if (startLevel == (Channel.MBR_RX_SEQ | Channel.MBR_TX_SEQ)) {
            startMembership(getAliveMembers(staticMembers.toArray(new Member[0])));
            running = true;
            if (thread == null && useThread) {
                thread = new PingThread();
                thread.setDaemon(true);
                thread.setName("StaticMembership.PingThread[" + this.channel.getName() + "]");
                thread.start();
            }
        }
    }

    @Override
    public boolean stop(int level) throws Exception {
        if (Channel.MBR_RX_SEQ == (level & Channel.MBR_RX_SEQ)) {
            // no-op
        }
        if (Channel.MBR_TX_SEQ == (level & Channel.MBR_TX_SEQ)) {
            // no-op
        }
        startLevel = (startLevel & (~level));
        if (startLevel == 0) {
            running = false;
            if (thread != null) {
                thread.interrupt();
                thread = null;
            }
            if (this.rpcChannel != null) {
                this.rpcChannel.breakdown();
            }
            if (this.channel != null) {
                try {
                    stopMembership(this.getMembers());
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    // Otherwise ignore
                }
                this.channel.removeChannelListener(this);
                this.channel = null;
            }
            this.rpcChannel = null;
            this.membership.reset();
        }
        return (startLevel == 0);
    }

    /**
     * Sends start messages to the given members.
     * @param members the members to notify
     * @throws ChannelException if sending fails
     */
    protected void startMembership(Member[] members) throws ChannelException {
        if (members.length == 0) {
            return;
        }
        MemberMessage msg = new MemberMessage(membershipId, MemberMessage.MSG_START, service.getLocalMember(true));
        Response[] resp = rpcChannel.send(members, msg, RpcChannel.ALL_REPLY, sendOptions, rpcTimeout);
        if (resp.length > 0) {
            for (Response response : resp) {
                messageReceived(response.getMessage(), response.getSource());
            }
        } else {
            log.warn(sm.getString("staticMembershipProvider.startMembership.noReplies"));
        }
    }

    /**
     * Sets up a member after it joins. Override for customization.
     * @param mbr the member to set up
     * @return the configured member
     */
    protected Member setupMember(Member mbr) {
        // no-op
        return mbr;
    }

    /**
     * Handles the addition of a new member to the cluster.
     * @param member the member that was added
     */
    protected void memberAdded(Member member) {
        Member mbr = setupMember(member);
        if (membership.memberAlive(mbr)) {
            Runnable r = () -> {
                Thread currentThread = Thread.currentThread();
                String name = currentThread.getName();
                try {
                    currentThread.setName("StaticMembership-memberAdded");
                    membershipListener.memberAdded(mbr);
                } finally {
                    currentThread.setName(name);
                }
            };
            executor.execute(r);
        }
    }

    /**
     * Handles the disappearance of a member from the cluster.
     * @param member the member that disappeared
     */
    protected void memberDisappeared(Member member) {
        membership.removeMember(member);
        Runnable r = () -> {
            Thread currentThread = Thread.currentThread();
            String name = currentThread.getName();
            try {
                currentThread.setName("StaticMembership-memberDisappeared");
                membershipListener.memberDisappeared(member);
            } finally {
                currentThread.setName(name);
            }
        };
        executor.execute(r);
    }

    /**
     * Updates the alive status of a member.
     * @param member the member to update
     */
    protected void memberAlive(Member member) {
        if (!membership.contains(member)) {
            memberAdded(member);
        }
        membership.memberAlive(member);
    }

    /**
     * Sends stop messages to the given members.
     * @param members the members to notify
     */
    protected void stopMembership(Member[] members) {
        if (members.length == 0) {
            return;
        }
        Member localmember = service.getLocalMember(false);
        localmember.setCommand(Member.SHUTDOWN_PAYLOAD);
        MemberMessage msg = new MemberMessage(membershipId, MemberMessage.MSG_STOP, localmember);
        try {
            channel.send(members, msg, sendOptions);
        } catch (ChannelException e) {
            log.error(sm.getString("staticMembershipProvider.stopMembership.sendFailed"), e);
        }
    }

    @Override
    public void messageReceived(Serializable msg, Member sender) {
        MemberMessage memMsg = (MemberMessage) msg;
        Member member = memMsg.getMember();
        if (memMsg.getMsgtype() == MemberMessage.MSG_START) {
            memberAdded(member);
        } else if (memMsg.getMsgtype() == MemberMessage.MSG_STOP) {
            memberDisappeared(member);
        } else if (memMsg.getMsgtype() == MemberMessage.MSG_PING) {
            memberAlive(member);
        }
    }

    @Override
    public boolean accept(Serializable msg, Member sender) {
        boolean result = false;
        if (msg instanceof MemberMessage) {
            result = Arrays.equals(this.membershipId, ((MemberMessage) msg).getMembershipId());
        }
        return result;
    }

    @Override
    public Serializable replyRequest(Serializable msg, final Member sender) {
        if (!(msg instanceof MemberMessage memMsg)) {
            return null;
        }
        if (memMsg.getMsgtype() == MemberMessage.MSG_START) {
            messageReceived(memMsg, sender);
            memMsg.setMember(service.getLocalMember(true));
            return memMsg;
        } else if (memMsg.getMsgtype() == MemberMessage.MSG_PING) {
            messageReceived(memMsg, sender);
            memMsg.setMember(service.getLocalMember(true));
            return memMsg;
        } else {
            // other messages are ignored.
            if (log.isInfoEnabled()) {
                log.info(sm.getString("staticMembershipProvider.replyRequest.ignored", memMsg.getTypeDesc()));
            }
            return null;
        }
    }

    @Override
    public void leftOver(Serializable msg, Member sender) {
        if (!(msg instanceof MemberMessage memMsg)) {
            return;
        }
        if (memMsg.getMsgtype() == MemberMessage.MSG_START) {
            messageReceived(memMsg, sender);
        } else if (memMsg.getMsgtype() == MemberMessage.MSG_PING) {
            messageReceived(memMsg, sender);
        } else {
            // other messages are ignored.
            if (log.isInfoEnabled()) {
                log.info(sm.getString("staticMembershipProvider.leftOver.ignored", memMsg.getTypeDesc()));
            }
        }
    }

    @Override
    public void heartbeat() {
        try {
            if (!useThread) {
                ping();
            }
        } catch (ChannelException e) {
            log.warn(sm.getString("staticMembershipProvider.heartbeat.failed"), e);
        }
    }

    /**
     * Sends ping messages to all static members and checks for expired members.
     * @throws ChannelException if pinging fails
     */
    protected void ping() throws ChannelException {
        // send ping
        Member[] members = getAliveMembers(staticMembers.toArray(new Member[0]));
        if (members.length > 0) {
            try {
                MemberMessage msg =
                        new MemberMessage(membershipId, MemberMessage.MSG_PING, service.getLocalMember(true));
                Response[] resp = rpcChannel.send(members, msg, RpcChannel.ALL_REPLY, sendOptions, rpcTimeout);
                for (Response response : resp) {
                    messageReceived(response.getMessage(), response.getSource());
                }
            } catch (ChannelException ce) {
                // Handle known failed members
                FaultyMember[] faultyMembers = ce.getFaultyMembers();
                for (FaultyMember faultyMember : faultyMembers) {
                    memberDisappeared(faultyMember.getMember());
                }
                throw ce;
            }
        }
        // expire
        checkExpired();
    }

    /**
     * Checks for and removes expired members.
     */
    protected void checkExpired() {
        Member[] expired = membership.expire(expirationTime);
        for (Member member : expired) {
            membershipListener.memberDisappeared(member);
        }
    }

    /**
     * Sets the channel for this provider.
     * @param channel the channel
     */
    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    /**
     * Sets the static members for this cluster.
     * @param staticMembers the list of static members
     */
    public void setStaticMembers(ArrayList<StaticMember> staticMembers) {
        this.staticMembers = staticMembers;
    }

    private Member[] getAliveMembers(Member[] members) {
        List<Member> aliveMembers = new ArrayList<>();
        for (Member member : members) {
            try (Socket socket = new Socket()) {
                InetAddress ia = InetAddress.getByAddress(member.getHost());
                InetSocketAddress addr = new InetSocketAddress(ia, member.getPort());
                socket.connect(addr, connectTimeout);
                aliveMembers.add(member);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                // Otherwise ignore
            }
        }
        return aliveMembers.toArray(new Member[0]);
    }

    // ------------------------------------------------------------------------------
    // member message to send to and from other memberships
    // ------------------------------------------------------------------------------
    /**
     * Message sent between membership providers to coordinate membership state.
     */
    public static class MemberMessage implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /**
         * Message type for member start.
         */
        public static final int MSG_START = 1;
        /**
         * Message type for member stop.
         */
        public static final int MSG_STOP = 2;
        /**
         * Message type for member ping.
         */
        public static final int MSG_PING = 3;
        private final int msgtype;
        private final byte[] membershipId;
        private Member member;

        /**
         * Creates a new MemberMessage.
         * @param membershipId the membership group identifier
         * @param msgtype the message type
         * @param member the member associated with this message
         */
        public MemberMessage(byte[] membershipId, int msgtype, Member member) {
            this.membershipId = membershipId;
            this.msgtype = msgtype;
            this.member = member;
        }

        /**
         * Returns the message type.
         * @return the message type constant
         */
        public int getMsgtype() {
            return msgtype;
        }

        /**
         * Returns the membership group identifier.
         * @return the membership ID byte array
         */
        public byte[] getMembershipId() {
            return membershipId;
        }

        /**
         * Returns the member associated with this message.
         * @return the member
         */
        public Member getMember() {
            return member;
        }

        /**
         * Sets the member for this message.
         * @param local the member
         */
        public void setMember(Member local) {
            this.member = local;
        }

        @Override
        public String toString() {
            return "MemberMessage[" + "name=" + new String(membershipId) + "; type=" + getTypeDesc() + "; member=" +
                    member + ']';
        }

        /**
         * Returns a human-readable description of the message type.
         * @return the type description string
         */
        protected String getTypeDesc() {
            return switch (msgtype) {
                case MSG_START -> "MSG_START";
                case MSG_STOP -> "MSG_STOP";
                case MSG_PING -> "MSG_PING";
                default -> "UNKNOWN";
            };
        }
    }

    /**
     * Background thread that periodically sends ping messages to cluster members.
     */
    protected class PingThread extends Thread {
        /**
         * Default constructor.
         */
        public PingThread() {
        }

        @Override
        public void run() {
            while (running) {
                try {
                    sleep(pingInterval);
                    ping();
                } catch (InterruptedException ix) {
                    // Ignore
                } catch (Exception e) {
                    log.warn(sm.getString("staticMembershipProvider.pingThread.failed"), e);
                }
            }
        }
    }
}
