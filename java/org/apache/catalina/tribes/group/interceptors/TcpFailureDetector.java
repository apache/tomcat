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

import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelException.FaultyMember;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.RemoteProcessException;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.group.GroupChannel;
import org.apache.catalina.tribes.group.InterceptorPayload;
import org.apache.catalina.tribes.io.ChannelData;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.membership.Membership;
import org.apache.catalina.tribes.membership.StaticMember;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * The TcpFailureDetector is a useful interceptor that adds reliability to the membership layer.
 * <p>
 * If the network is busy, or the system is busy so that the membership receiver thread is not getting enough time to
 * update its table, members can be &quot;timed out&quot; This failure detector will intercept the memberDisappeared
 * message(unless its a true shutdown message) and connect to the member using TCP.
 * <p>
 * The TcpFailureDetector works in two ways:
 * <ol>
 * <li>It intercepts memberDisappeared events</li>
 * <li>It catches send errors</li>
 * </ol>
 */
public class TcpFailureDetector extends ChannelInterceptorBase implements TcpFailureDetectorMBean {

    /**
     * Constructs a new TcpFailureDetector.
     */
    public TcpFailureDetector() {
    }

    private static final Log log = LogFactory.getLog(TcpFailureDetector.class);
    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(TcpFailureDetector.class);

    /**
     * Test payload sent during member liveness checks.
     */
    protected static final byte[] TCP_FAIL_DETECT = new byte[] { 79, -89, 115, 72, 121, -126, 67, -55, -97, 111, -119,
            -128, -95, 91, 7, 20, 125, -39, 82, 91, -21, -15, 67, -102, -73, 126, -66, -113, -127, 103, 30, -74, 55, 21,
            -66, -121, 69, 126, 76, -88, -65, 10, 77, 19, 83, 56, 21, 50, 85, -10, -108, -73, 58, -6, 64, 120, -111, 4,
            125, -41, 114, -124, -64, -43 };

    /**
     * Connection timeout in milliseconds for member checks.
     */
    protected long connectTimeout = 1000;// 1 second default

    /**
     * Whether to perform TCP send tests during member checks.
     */
    protected boolean performSendTest = true;

    /**
     * Whether to perform TCP read tests during member checks.
     */
    protected boolean performReadTest = false;

    /**
     * Read test timeout in milliseconds.
     */
    protected long readTestTimeout = 5000;// 5 seconds

    /**
     * The membership tracker.
     */
    protected Membership membership = null;

    /**
     * The sequence number of the last membership event of each member, used to detect that a member event
     * was processed while a liveness check of the same member was running. Only populated while at least
     * one liveness check is running. Guarded by {@link #membership}.
     */
    private final HashMap<Member,Long> lastMemberEvent = new HashMap<>();

    /**
     * The last membership event sequence number issued. Guarded by {@link #membership}.
     */
    private long memberEventSequence = 0;

    /**
     * The number of liveness checks that have started and not yet been applied. Guarded by
     * {@link #membership}.
     */
    private int checksInFlight = 0;

    /**
     * Map of members suspected of being dead and their detection time.
     */
    protected final HashMap<Member,Long> removeSuspects = new HashMap<>();

    /**
     * Map of members suspected of rejoining and their detection time.
     */
    protected final HashMap<Member,Long> addSuspects = new HashMap<>();

    /**
     * Timeout in seconds for removing suspected dead members.
     */
    protected int removeSuspectsTimeout = 300; // 5 minutes

    @Override
    public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload)
            throws ChannelException {
        try {
            super.sendMessage(destination, msg, payload);
        } catch (ChannelException cx) {
            FaultyMember[] mbrs = cx.getFaultyMembers();
            for (FaultyMember mbr : mbrs) {
                if (mbr.getCause() != null && (!(mbr.getCause() instanceof RemoteProcessException))) {// RemoteProcessException's
                                                                                                      // are ok
                    this.memberDisappeared(mbr.getMember());
                } // end if
            } // for
            throw cx;
        }
    }

    @Override
    public void messageReceived(ChannelMessage msg) {
        // catch incoming
        boolean process = true;
        if (okToProcess(msg.getOptions())) {
            // check to see if it is a testMessage, if so, process = false
            process = ((msg.getMessage().getLength() != TCP_FAIL_DETECT.length) ||
                    (!Arrays.equals(TCP_FAIL_DETECT, msg.getMessage().getBytes())));
        } // end if

        // ignore the message, it doesn't have the flag set
        if (process) {
            super.messageReceived(msg);
        } else if (log.isDebugEnabled()) {
            log.debug(sm.getString("tcpFailureDetector.receivedPacket", msg));
        }
    }// messageReceived


    @Override
    public void memberAdded(Member member) {
        if (membership == null) {
            setupMembership();
        }
        boolean notify = false;
        boolean verify = false;
        long stamp = 0;
        synchronized (membership) {
            recordMemberEvent(member);
            if (removeSuspects.containsKey(member)) {
                // previously marked suspect, system below picked up the member again
                removeSuspects.remove(member);
            } else if (membership.getMember(member) == null) {
                verify = true;
                stamp = beginCheck(member);
            }
        }
        if (verify) {
            // if we add it here, then add it upwards too
            // check to see if it is alive, outside the membership monitor so
            // that slow TCP connects do not block other membership operations
            boolean alive = memberAliveForCheck(member, stamp);
            synchronized (membership) {
                // the state may have changed while the member was being checked
                if (endCheck(member, stamp) && membership.getMember(member) == null) {
                    if (alive) {
                        membership.memberAlive(member);
                        recordMemberEvent(member);
                        addSuspects.remove(member);
                        notify = true;
                    } else if (member instanceof StaticMember) {
                        addSuspects.put(member, Long.valueOf(System.currentTimeMillis()));
                    }
                }
            }
        }
        if (notify) {
            super.memberAdded(member);
        }
    }

    /**
     * Forgets a member that is represented by another member from now on, for example a statically configured
     * member whose node has been discovered dynamically. Unlike {@link #memberDisappeared(Member)}, the member is
     * not kept as a suspect, so it is not added again by the member checks. If the member was a member of this
     * detector, the interceptors above are told that it disappeared.
     *
     * @param member the member to forget
     */
    public void forgetMember(Member member) {
        if (membership == null) {
            setupMembership();
        }
        boolean removed;
        synchronized (membership) {
            // supersedes running checks of the member
            recordMemberEvent(member);
            removed = membership.contains(member);
            if (removed) {
                membership.removeMember(member);
            }
            removeSuspects.remove(member);
            addSuspects.remove(member);
        }
        if (removed) {
            super.memberDisappeared(member);
        }
    }

    @Override
    public void memberDisappeared(Member member) {
        if (membership == null) {
            setupMembership();
        }
        boolean shutdown = Arrays.equals(member.getCommand(), Member.SHUTDOWN_PAYLOAD);
        long stamp = 0;
        if (shutdown) {
            synchronized (membership) {
                recordMemberEvent(member);
                if (!membership.contains(member)) {
                    return;
                }
                membership.removeMember(member);
                removeSuspects.remove(member);
                if (member instanceof StaticMember) {
                    addSuspects.put(member, Long.valueOf(System.currentTimeMillis()));
                }
            }
            super.memberDisappeared(member);
        } else {
            boolean verify;
            synchronized (membership) {
                recordMemberEvent(member);
                verify = membership.contains(member);
                if (verify) {
                    stamp = beginCheck(member);
                }
            }
            if (!verify) {
                if (log.isInfoEnabled()) {
                    log.info(sm.getString("tcpFailureDetector.already.disappeared", member));
                }
                return;
            }
            boolean notify = false;
            boolean applied = false;
            if (log.isInfoEnabled()) {
                log.info(sm.getString("tcpFailureDetector.memberDisappeared.verify", member));
            }
            // check to see if the member really is gone, outside the membership
            // monitor so that slow TCP connects do not block other membership
            // operations
            boolean alive = memberAliveForCheck(member, stamp);
            synchronized (membership) {
                // the state may have changed while the member was being checked
                if (endCheck(member, stamp) && membership.contains(member)) {
                    applied = true;
                    if (alive) {
                        // add the member as suspect
                        removeSuspects.put(member, Long.valueOf(System.currentTimeMillis()));
                        recordMemberEvent(member);
                    } else {
                        // not correct, we need to maintain the map
                        membership.removeMember(member);
                        recordMemberEvent(member);
                        removeSuspects.remove(member);
                        if (member instanceof StaticMember) {
                            addSuspects.put(member, Long.valueOf(System.currentTimeMillis()));
                        }
                        notify = true;
                    }
                }
            }
            if (notify) {
                if (log.isInfoEnabled()) {
                    log.info(sm.getString("tcpFailureDetector.member.disappeared", member));
                }
                super.memberDisappeared(member);
            } else if (applied) {
                if (log.isInfoEnabled()) {
                    log.info(sm.getString("tcpFailureDetector.still.alive", member));
                }
            }
        }
    }

    @Override
    public boolean hasMembers() {
        if (membership == null) {
            setupMembership();
        }
        return membership.hasMembers();
    }

    @Override
    public Member[] getMembers() {
        if (membership == null) {
            setupMembership();
        }
        return membership.getMembers();
    }

    @Override
    public Member getMember(Member mbr) {
        if (membership == null) {
            setupMembership();
        }
        return membership.getMember(mbr);
    }

    @Override
    public Member getLocalMember(boolean incAlive) {
        return super.getLocalMember(incAlive);
    }

    @Override
    public void heartbeat() {
        super.heartbeat();
        checkMembers(false);
    }

    @Override
    public void checkMembers(boolean checkAll) {
        try {
            if (membership == null) {
                setupMembership();
            }
            if (!checkAll) {
                performBasicCheck();
            } else {
                performForcedCheck();
            }
        } catch (Exception e) {
            log.warn(sm.getString("tcpFailureDetector.heartbeat.failed"), e);
        }
    }

    /**
     * Performs a forced check of all cluster members. The TCP liveness checks run outside the
     * membership monitor and the results are only applied after re-acquiring the monitor and
     * re-checking that the current state still matches the decision.
     */
    protected void performForcedCheck() {
        // update all alive times
        Member[] members = super.getMembers();
        for (int i = 0; members != null && i < members.length; i++) {
            Member member = members[i];
            long stamp;
            synchronized (membership) {
                stamp = beginCheck(member);
            }
            boolean alive = memberAliveForCheck(member, stamp);
            boolean added = false;
            boolean disappeared = false;
            synchronized (membership) {
                if (!endCheck(member, stamp)) {
                    // a member event for this member was processed meanwhile and wins
                    continue;
                }
                if (alive) {
                    if (membership.memberAlive(member)) {
                        added = true;
                        recordMemberEvent(member);
                    }
                    addSuspects.remove(member);
                } else {
                    if (membership.getMember(member) != null) {
                        membership.removeMember(member);
                        recordMemberEvent(member);
                        removeSuspects.remove(member);
                        if (member instanceof StaticMember) {
                            addSuspects.put(member, Long.valueOf(System.currentTimeMillis()));
                        }
                        disappeared = true;
                    }
                } // end if
            }
            if (added) {
                super.memberAdded(member);
            } else if (disappeared) {
                super.memberDisappeared(member);
            }
        } // for

    }

    /**
     * Performs a basic check of suspected members. The TCP liveness checks run outside the
     * membership monitor and the results are only applied after re-acquiring the monitor and
     * re-checking that the current state still matches the decision.
     */
    protected void performBasicCheck() {
        // update all alive times
        Member[] members = super.getMembers();
        // Members unknown to the tracker and the stamps of their running checks. They are only added to the
        // tracker once the check has found them alive, so an unverified member is never visible and a stale
        // result can simply be discarded.
        Map<Member,Long> toCheck = new LinkedHashMap<>();
        try {
            for (int i = 0; members != null && i < members.length; i++) {
                synchronized (membership) {
                    if (membership.getMember(members[i]) != null) {
                        // known member, just update its alive time
                        membership.memberAlive(members[i]);
                    } else if (!addSuspects.containsKey(members[i])) {
                        // we don't have this one in our membership, check to see if the member is alive
                        // (members in addSuspects are not added temporarily, see below)
                        toCheck.put(members[i], Long.valueOf(beginCheck(members[i])));
                    }
                }
            } // for

            // The liveness checks run outside the monitor. A member event or a change made by another check
            // for the same member while it runs supersedes the result, which is then discarded.
            for (Iterator<Map.Entry<Member,Long>> it = toCheck.entrySet().iterator(); it.hasNext();) {
                Map.Entry<Member,Long> entry = it.next();
                it.remove();
                Member member = entry.getKey();
                long stamp = entry.getValue().longValue();
                boolean alive = memberAliveForCheck(member, stamp);
                boolean notify = false;
                synchronized (membership) {
                    if (endCheck(member, stamp) && alive && membership.getMember(member) == null) {
                        membership.memberAlive(member);
                        recordMemberEvent(member);
                        notify = true;
                    }
                }
                if (notify) {
                    log.warn(sm.getString("tcpFailureDetector.performBasicCheck.memberAdded", member));
                    super.memberAdded(member);
                }
            }
        } finally {
            if (!toCheck.isEmpty()) {
                // an exception interrupted the checks, release the ones that did not run
                synchronized (membership) {
                    for (Map.Entry<Member,Long> entry : toCheck.entrySet()) {
                        endCheck(entry.getKey(), entry.getValue().longValue());
                    }
                }
            }
        }

        // check suspect members if they are still alive,
        // if not, simply issue the memberDisappeared message
        Member[] keys;
        synchronized (membership) {
            keys = removeSuspects.keySet().toArray(new Member[0]);
        }
        for (Member m : keys) {
            boolean verify;
            long stamp = 0;
            synchronized (membership) {
                verify = membership.getMember(m) != null;
                if (verify) {
                    stamp = beginCheck(m);
                }
            }
            boolean disappeared = false;
            if (verify) {
                boolean alive = memberAliveForCheck(m, stamp);
                synchronized (membership) {
                    // the state may have changed while the member was being checked
                    boolean current = endCheck(m, stamp);
                    if (current && alive) {
                        expireRemoveSuspect(m);
                    } else if (current && membership.getMember(m) != null) {
                        membership.removeMember(m);
                        recordMemberEvent(m);
                        if (m instanceof StaticMember) {
                            addSuspects.put(m, Long.valueOf(System.currentTimeMillis()));
                        }
                        removeSuspects.remove(m);
                        disappeared = true;
                    }
                }
            } else {
                synchronized (membership) {
                    expireRemoveSuspect(m);
                }
            }
            if (disappeared) {
                super.memberDisappeared(m);
                if (log.isInfoEnabled()) {
                    log.info(sm.getString("tcpFailureDetector.suspectMember.dead", m));
                }
            }
        }

        // check add suspects members if they are alive now,
        // if they are, simply issue the memberAdded message
        synchronized (membership) {
            keys = addSuspects.keySet().toArray(new Member[0]);
        }
        for (Member m : keys) {
            boolean verify;
            long stamp = 0;
            synchronized (membership) {
                verify = membership.getMember(m) == null;
                if (verify) {
                    stamp = beginCheck(m);
                }
            }
            if (!verify) {
                continue;
            }
            boolean alive = memberAliveForCheck(m, stamp);
            boolean notify = false;
            synchronized (membership) {
                // the state may have changed while the member was being checked
                if (endCheck(m, stamp) && alive && membership.getMember(m) == null) {
                    membership.memberAlive(m);
                    recordMemberEvent(m);
                    addSuspects.remove(m);
                    notify = true;
                }
            }
            if (notify) {
                super.memberAdded(m);
                if (log.isInfoEnabled()) {
                    log.info(sm.getString("tcpFailureDetector.suspectMember.alive", m));
                }
            } // end if
        }
    }

    /**
     * Records that a member event was processed or a change was made to the membership state of the member. A
     * liveness check of the same member that started earlier is then stale and must not be applied, since the
     * later event or change wins. Events of other members do not affect it. Must be called while holding the
     * membership monitor.
     *
     * @param member the member the event is for
     */
    private void recordMemberEvent(Member member) {
        if (checksInFlight > 0) {
            lastMemberEvent.put(member, Long.valueOf(++memberEventSequence));
        }
    }

    /**
     * Registers the start of a liveness check of the member. Must be called while holding the membership
     * monitor and followed by a call to {@link #endCheck(Member, long)} when the result is applied.
     *
     * @param member the member to be checked
     * @return the stamp to pass to {@link #endCheck(Member, long)}
     */
    private long beginCheck(Member member) {
        checksInFlight++;
        Long last = lastMemberEvent.get(member);
        return last == null ? 0 : last.longValue();
    }

    /**
     * Registers the end of a liveness check of the member and reports if its result is still current. Must
     * be called while holding the membership monitor.
     *
     * @param member the member that was checked
     * @param stamp the stamp returned by {@link #beginCheck(Member)}
     * @return {@code true} if no member event for the member was processed since the check started
     */
    private boolean endCheck(Member member, long stamp) {
        Long last = lastMemberEvent.get(member);
        boolean current = (last == null ? 0 : last.longValue()) == stamp;
        if (--checksInFlight == 0) {
            // no running check can compare against the recorded events any more
            lastMemberEvent.clear();
        }
        return current;
    }

    /**
     * Performs a registered liveness check and releases its registration if the check fails before its result can be
     * applied.
     *
     * @param member the member to check
     * @param stamp the stamp returned by {@link #beginCheck(Member)}
     * @return {@code true} if the member is alive
     */
    private boolean memberAliveForCheck(Member member, long stamp) {
        try {
            return memberAlive(member);
        } catch (RuntimeException | Error e) {
            synchronized (membership) {
                endCheck(member, stamp);
            }
            throw e;
        }
    }

    /**
     * Removes the member from the remove suspects map if it has been suspect for longer than the
     * configured timeout. Must be called while holding the membership monitor.
     *
     * @param m the member to expire
     */
    protected void expireRemoveSuspect(Member m) {
        if (removeSuspectsTimeout > 0) {
            Long suspectTime = removeSuspects.get(m);
            // the member may have been removed from the map while it was being checked
            if (suspectTime != null) {
                long timeNow = System.currentTimeMillis();
                int timeIdle = (int) ((timeNow - suspectTime.longValue()) / 1000L);
                if (timeIdle > removeSuspectsTimeout) {
                    removeSuspects.remove(m); // remove suspect member
                }
            }
        }
    }

    /**
     * Initializes the membership tracker if not already set up.
     */
    protected synchronized void setupMembership() {
        if (membership == null) {
            membership = new Membership(super.getLocalMember(true));
        }

    }

    /**
     * Checks whether the given member is alive using the default test parameters.
     *
     * @param mbr the member to check
     * @return true if the member is alive
     */
    protected boolean memberAlive(Member mbr) {
        return memberAlive(mbr, TCP_FAIL_DETECT, performSendTest, performReadTest, readTestTimeout, connectTimeout,
                getOptionFlag());
    }

    /**
     * Checks whether the given member is alive using the specified test parameters.
     *
     * @param mbr the member to check
     * @param msgData the test message data
     * @param sendTest whether to perform a send test
     * @param readTest whether to perform a read test
     * @param readTimeout the read timeout in milliseconds
     * @param conTimeout the connection timeout in milliseconds
     * @param optionFlag the socket option flag
     * @return true if the member is alive
     */
    protected boolean memberAlive(Member mbr, byte[] msgData, boolean sendTest, boolean readTest, long readTimeout,
            long conTimeout, int optionFlag) {
        // could be a shutdown notification
        if (Arrays.equals(mbr.getCommand(), Member.SHUTDOWN_PAYLOAD)) {
            return false;
        }

        boolean channelSecure = getChannel() instanceof GroupChannel groupChannel && groupChannel.getSecure();
        boolean useSecurePort = channelSecure || mbr.getSecurePort() >= 0 && mbr.getPort() < 0;
        int port = useSecurePort ? mbr.getSecurePort() : mbr.getPort();
        if (port < 0) {
            // no usable port to connect to, so the member can't be checked
            return false;
        }
        // the plaintext test message can't be spoken to a TLS-only listener, so a secure port check is
        // limited to a plain TCP connect
        if (useSecurePort) {
            sendTest = false;
            readTest = false;
        }

        try (Socket socket = new Socket()) {
            InetAddress ia = InetAddress.getByAddress(mbr.getHost());
            InetSocketAddress addr = new InetSocketAddress(ia, port);
            // The timeouts are longs but the socket takes ints. Cap them rather than let a large value overflow to a
            // negative one, which the socket rejects.
            socket.setSoTimeout((int) Math.min(readTimeout, Integer.MAX_VALUE));
            socket.connect(addr, (int) Math.min(conTimeout, Integer.MAX_VALUE));
            if (sendTest) {
                ChannelData data = new ChannelData(true);
                data.setAddress(getLocalMember(false));
                data.setMessage(new XByteBuffer(msgData, false));
                data.setTimestamp(System.currentTimeMillis());
                int options = optionFlag | Channel.SEND_OPTIONS_BYTE_MESSAGE;
                if (readTest) {
                    options = (options | Channel.SEND_OPTIONS_USE_ACK);
                } else {
                    options = (options & (~Channel.SEND_OPTIONS_USE_ACK));
                }
                data.setOptions(options);
                byte[] message = XByteBuffer.createDataPackage(data);
                socket.getOutputStream().write(message);
                if (readTest) {
                    int length = socket.getInputStream().read(message);
                    return length > 0;
                }
            } // end if
            return true;
        } catch (SocketTimeoutException | ConnectException | NoRouteToHostException noop) {
            // do nothing, we couldn't connect
        } catch (Exception e) {
            log.error(sm.getString("tcpFailureDetector.failureDetection.failed", mbr), e);
        }
        return false;
    }

    @Override
    public long getReadTestTimeout() {
        return readTestTimeout;
    }

    @Override
    public boolean getPerformSendTest() {
        return performSendTest;
    }

    @Override
    public boolean getPerformReadTest() {
        return performReadTest;
    }

    @Override
    public long getConnectTimeout() {
        return connectTimeout;
    }

    @Override
    public int getRemoveSuspectsTimeout() {
        return removeSuspectsTimeout;
    }

    @Override
    public void setPerformReadTest(boolean performReadTest) {
        this.performReadTest = performReadTest;
    }

    @Override
    public void setPerformSendTest(boolean performSendTest) {
        this.performSendTest = performSendTest;
    }

    @Override
    public void setReadTestTimeout(long readTestTimeout) {
        this.readTestTimeout = readTestTimeout;
    }

    @Override
    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    @Override
    public void setRemoveSuspectsTimeout(int removeSuspectsTimeout) {
        this.removeSuspectsTimeout = removeSuspectsTimeout;
    }

}