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

public class StaticMembershipProvider extends MembershipProviderBase implements RpcCallback, ChannelListener, Heartbeat {

    protected static final StringManager sm = StringManager.getManager(StaticMembershipProvider.class);
    private static final Log log = LogFactory.getLog(StaticMembershipProvider.class);

    protected Channel channel;
    protected RpcChannel rpcChannel;
    private String membershipName = null;
    private byte[] membershipId = null;
    protected ArrayList<StaticMember> staticMembers;
    protected int sendOptions = Channel.SEND_OPTIONS_ASYNCHRONOUS;
    protected long expirationTime = 5000;
    protected int connectTimeout = 500;
    protected long rpcTimeout = 3000;
    protected int startLevel = 0;
    // for ping thread
    protected boolean useThread = false;
    protected long pingInterval = 1000;
    protected volatile boolean running = true;
    protected PingThread thread = null;

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
        if (Channel.MBR_RX_SEQ==(level & Channel.MBR_RX_SEQ)) {
            //no-op
        }
        if (Channel.MBR_TX_SEQ==(level & Channel.MBR_TX_SEQ)) {
            //no-op
        }
        startLevel = (startLevel | level);
        if (startLevel == (Channel.MBR_RX_SEQ | Channel.MBR_TX_SEQ)) {
            startMembership(getAliveMembers(staticMembers.toArray(new Member[0])));
            running = true;
            if ( thread == null && useThread) {
                thread = new PingThread();
                thread.setDaemon(true);
                thread.setName("StaticMembership.PingThread[" + this.channel.getName() +"]");
                thread.start();
            }
        }
    }

    @Override
    public boolean stop(int level) throws Exception {
        if (Channel.MBR_RX_SEQ==(level & Channel.MBR_RX_SEQ)) {
            // no-op
        }
        if (Channel.MBR_TX_SEQ==(level & Channel.MBR_TX_SEQ)) {
            // no-op
        }
        startLevel = (startLevel & (~level));
        if ( startLevel == 0 ) {
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

    protected void startMembership(Member[] members) throws ChannelException {
        if (members.length == 0) return;
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

    protected Member setupMember(Member mbr) {
        // no-op
        return mbr;
    }

    protected void memberAdded(Member member) {
        Member mbr = setupMember(member);
        if(membership.memberAlive(mbr)) {
            Runnable r = () -> {
                String name = Thread.currentThread().getName();
                try {
                    Thread.currentThread().setName("StaticMembership-memberAdded");
                    membershipListener.memberAdded(mbr);
                } finally {
                    Thread.currentThread().setName(name);
                }
            };
            executor.execute(r);
        }
    }

    protected void memberDisappeared(Member member) {
        membership.removeMember(member);
        Runnable r = () -> {
            String name = Thread.currentThread().getName();
            try {
                Thread.currentThread().setName("StaticMembership-memberDisappeared");
                membershipListener.memberDisappeared(member);
            } finally {
                Thread.currentThread().setName(name);
            }
        };
        executor.execute(r);
    }

    protected void memberAlive(Member member) {
        if (!membership.contains(member)) memberAdded(member);
        membership.memberAlive(member);
    }

    protected void stopMembership(Member[] members) {
        if (members.length == 0 ) return;
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
        if (!(msg instanceof MemberMessage)) return null;
        MemberMessage memMsg = (MemberMessage) msg;
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
            if (log.isInfoEnabled())
                log.info(sm.getString("staticMembershipProvider.replyRequest.ignored",
                        memMsg.getTypeDesc()));
            return null;
        }
    }

    @Override
    public void leftOver(Serializable msg, Member sender) {
        if (!(msg instanceof MemberMessage)) return;
        MemberMessage memMsg = (MemberMessage) msg;
        if (memMsg.getMsgtype() == MemberMessage.MSG_START) {
            messageReceived(memMsg, sender);
        } else if (memMsg.getMsgtype() == MemberMessage.MSG_PING) {
            messageReceived(memMsg, sender);
        } else {
            // other messages are ignored.
            if (log.isInfoEnabled())
                log.info(sm.getString("staticMembershipProvider.leftOver.ignored",
                        memMsg.getTypeDesc()));
        }
    }

    @Override
    public void heartbeat() {
        try {
            if (!useThread) ping();
        } catch (ChannelException e) {
            log.warn(sm.getString("staticMembershipProvider.heartbeat.failed"), e);
        }
    }

    protected void ping() throws ChannelException {
        // send ping
        Member[] members = getAliveMembers(staticMembers.toArray(new Member[0]));
        if (members.length > 0) {
            try {
                MemberMessage msg = new MemberMessage(membershipId, MemberMessage.MSG_PING, service.getLocalMember(true));
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

    protected void checkExpired() {
        Member[] expired = membership.expire(expirationTime);
        for (Member member : expired) {
            membershipListener.memberDisappeared(member);
        }
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

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
    public static class MemberMessage implements Serializable {
        private static final long serialVersionUID = 1L;
        public static final int MSG_START = 1;
        public static final int MSG_STOP = 2;
        public static final int MSG_PING = 3;
        private final int msgtype;
        private final byte[] membershipId;
        private Member member;

        public MemberMessage(byte[] membershipId, int msgtype, Member member) {
            this.membershipId = membershipId;
            this.msgtype = msgtype;
            this.member = member;
        }

        public int getMsgtype() {
            return msgtype;
        }

        public byte[] getMembershipId() {
            return membershipId;
        }

        public Member getMember() {
            return member;
        }

        public void setMember(Member local) {
            this.member = local;
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder("MemberMessage[");
            buf.append("name=");
            buf.append(new String(membershipId));
            buf.append("; type=");
            buf.append(getTypeDesc());
            buf.append("; member=");
            buf.append(member);
            buf.append(']');
            return buf.toString();
        }

        protected String getTypeDesc() {
            switch (msgtype) {
            case MSG_START:
                return "MSG_START";
            case MSG_STOP:
                return "MSG_STOP";
            case MSG_PING:
                return "MSG_PING";
            default:
                return "UNKNOWN";
            }
        }
    }

    protected class PingThread extends Thread {
        @Override
        public void run() {
            while (running) {
                try {
                    sleep(pingInterval);
                    ping();
                }catch (InterruptedException ix) {
                }catch (Exception x) {
                    log.warn(sm.getString("staticMembershipProvider.pingThread.failed"),x);
                }
            }
        }
    }
}
