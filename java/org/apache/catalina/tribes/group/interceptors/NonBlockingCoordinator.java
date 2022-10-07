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
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelInterceptor;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.UniqueId;
import org.apache.catalina.tribes.group.AbsoluteOrder;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.group.InterceptorPayload;
import org.apache.catalina.tribes.io.ChannelData;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.membership.MemberImpl;
import org.apache.catalina.tribes.membership.Membership;
import org.apache.catalina.tribes.util.Arrays;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.catalina.tribes.util.UUIDGenerator;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * <p>Title: Auto merging leader election algorithm</p>
 *
 * <p>Description: Implementation of a simple coordinator algorithm that not only selects a coordinator,
 *    it also merges groups automatically when members are discovered that weren't part of the
 *    </p>
 * <p>This algorithm is non blocking meaning it allows for transactions while the coordination phase is going on
 * </p>
 * <p>This implementation is based on a home brewed algorithm that uses the AbsoluteOrder of a membership
 * to pass a token ring of the current membership.<br>
 * This is not the same as just using AbsoluteOrder! Consider the following scenario:<br>
 * Nodes, A,B,C,D,E on a network, in that priority. AbsoluteOrder will only work if all
 * nodes are receiving pings from all the other nodes.
 * meaning, that node{i} receives pings from node{all}-node{i}<br>
 * but the following could happen if a multicast problem occurs.
 * A has members {B,C,D}<br>
 * B has members {A,C}<br>
 * C has members {D,E}<br>
 * D has members {A,B,C,E}<br>
 * E has members {A,C,D}<br>
 * Because the default Tribes membership implementation, relies on the multicast packets to
 * arrive at all nodes correctly, there is nothing guaranteeing that it will.<br>
 * <br>
 * To best explain how this algorithm works, lets take the above example:
 * For simplicity we assume that a send operation is O(1) for all nodes, although this algorithm will work
 * where messages overlap, as they all depend on absolute order<br>
 * Scenario 1: A,B,C,D,E all come online at the same time
 * Eval phase, A thinks of itself as leader, B thinks of A as leader,
 * C thinks of itself as leader, D,E think of A as leader<br>
 * Token phase:<br>
 * (1) A sends out a message X{A-ldr, A-src, mbrs-A,B,C,D} to B where X is the id for the message(and the view)<br>
 * (1) C sends out a message Y{C-ldr, C-src, mbrs-C,D,E} to D where Y is the id for the message(and the view)<br>
 * (2) B receives X{A-ldr, A-src, mbrs-A,B,C,D}, sends X{A-ldr, A-src, mbrs-A,B,C,D} to C <br>
 * (2) D receives Y{C-ldr, C-src, mbrs-C,D,E} D is aware of A,B, sends Y{A-ldr, C-src, mbrs-A,B,C,D,E} to E<br>
 * (3) C receives X{A-ldr, A-src, mbrs-A,B,C,D}, sends X{A-ldr, A-src, mbrs-A,B,C,D,E} to D<br>
 * (3) E receives Y{A-ldr, C-src, mbrs-A,B,C,D,E} sends Y{A-ldr, C-src, mbrs-A,B,C,D,E} to A<br>
 * (4) D receives X{A-ldr, A-src, mbrs-A,B,C,D,E} sends sends X{A-ldr, A-src, mbrs-A,B,C,D,E} to A<br>
 * (4) A receives Y{A-ldr, C-src, mbrs-A,B,C,D,E}, holds the message, add E to its list of members<br>
 * (5) A receives X{A-ldr, A-src, mbrs-A,B,C,D,E} <br>
 * At this point, the state looks like<br>
 * A - {A-ldr, mbrs-A,B,C,D,E, id=X}<br>
 * B - {A-ldr, mbrs-A,B,C,D, id=X}<br>
 * C - {A-ldr, mbrs-A,B,C,D,E, id=X}<br>
 * D - {A-ldr, mbrs-A,B,C,D,E, id=X}<br>
 * E - {A-ldr, mbrs-A,B,C,D,E, id=Y}<br>
 * <br>
 * A message doesn't stop until it reaches its original sender, unless its dropped by a higher leader.
 * As you can see, E still thinks the viewId=Y, which is not correct. But at this point we have
 * arrived at the same membership and all nodes are informed of each other.<br>
 * To synchronize the rest we simply perform the following check at A when A receives X:<br>
 * Original X{A-ldr, A-src, mbrs-A,B,C,D} == Arrived X{A-ldr, A-src, mbrs-A,B,C,D,E}<br>
 * Since the condition is false, A, will resend the token, and A sends X{A-ldr, A-src, mbrs-A,B,C,D,E} to B
 * When A receives X again, the token is complete. <br>
 * Optionally, A can send a message X{A-ldr, A-src, mbrs-A,B,C,D,E confirmed} to A,B,C,D,E who then
 * install and accept the view.
 * </p>
 * <p>
 * Lets assume that C1 arrives, C1 has lower priority than C, but higher priority than D.<br>
 * Lets also assume that C1 sees the following view {B,D,E}<br>
 * C1 waits for a token to arrive. When the token arrives, the same scenario as above will happen.<br>
 * In the scenario where C1 sees {D,E} and A,B,C cannot see C1, no token will ever arrive.<br>
 * In this case, C1 sends a Z{C1-ldr, C1-src, mbrs-C1,D,E} to D<br>
 * D receives Z{C1-ldr, C1-src, mbrs-C1,D,E} and sends Z{A-ldr, C1-src, mbrs-A,B,C,C1,D,E} to E<br>
 * E receives Z{A-ldr, C1-src, mbrs-A,B,C,C1,D,E} and sends it to A<br>
 * A sends Z{A-ldr, A-src, mbrs-A,B,C,C1,D,E} to B and the chain continues until A receives the token again.
 * At that time A optionally sends out Z{A-ldr, A-src, mbrs-A,B,C,C1,D,E, confirmed} to A,B,C,C1,D,E
 * </p>
 * <p>To ensure that the view gets implemented at all nodes at the same time,
 *    A will send out a VIEW_CONF message, this is the 'confirmed' message that is optional above.
 * <p>Ideally, the interceptor below this one would be the TcpFailureDetector to ensure correct memberships</p>
 *
 * <p>The example above, of course can be simplified with a finite statemachine:<br>
 * But I suck at writing state machines, my head gets all confused. One day I will document this algorithm though.<br>
 * Maybe I'll do a state diagram :)
 * </p>
 * <h2>State Diagrams</h2>
 * <a href="https://people.apache.org/~fhanik/tribes/docs/leader-election-initiate-election.jpg">Initiate an election</a><br><br>
 * <a href="https://people.apache.org/~fhanik/tribes/docs/leader-election-message-arrives.jpg">Receive an election message</a><br><br>
 */
public class NonBlockingCoordinator extends ChannelInterceptorBase {

    private static final Log log = LogFactory.getLog(NonBlockingCoordinator.class);
    protected static final StringManager sm = StringManager.getManager(NonBlockingCoordinator.class);

    /**
     * header for a coordination message
     */
    protected static final byte[] COORD_HEADER = new byte[] {-86, 38, -34, -29, -98, 90, 65, 63, -81, -122, -6, -110, 99, -54, 13, 63};
    /**
     * Coordination request
     */
    protected static final byte[] COORD_REQUEST = new byte[] {104, -95, -92, -42, 114, -36, 71, -19, -79, 20, 122, 101, -1, -48, -49, 30};
    /**
     * Coordination confirmation, for blocking installations
     */
    protected static final byte[] COORD_CONF = new byte[] {67, 88, 107, -86, 69, 23, 76, -70, -91, -23, -87, -25, -125, 86, 75, 20};

    /**
     * Alive message
     */
    protected static final byte[] COORD_ALIVE = new byte[] {79, -121, -25, -15, -59, 5, 64, 94, -77, 113, -119, -88, 52, 114, -56, -46,
                                                            -18, 102, 10, 34, -127, -9, 71, 115, -70, 72, -101, 88, 72, -124, 127, 111,
                                                            74, 76, -116, 50, 111, 103, 65, 3, -77, 51, -35, 0, 119, 117, 9, -26,
                                                            119, 50, -75, -105, -102, 36, 79, 37, -68, -84, -123, 15, -22, -109, 106, -55};
    /**
     * Time to wait for coordination timeout
     */
    protected final long waitForCoordMsgTimeout = 15000;
    /**
     * Our current view
     */
    protected volatile Membership view = null;
    /**
     * Out current viewId
     */
    protected UniqueId viewId;

    /**
     * Our nonblocking membership
     */
    protected Membership membership = null;

    /**
     * indicates that we are running an election
     * and this is the one we are running
     */
    protected UniqueId suggestedviewId;
    protected volatile Membership suggestedView;

    protected volatile boolean started = false;
    protected final int startsvc = 0xFFFF;

    protected final Object electionMutex = new Object();

    protected final AtomicBoolean coordMsgReceived = new AtomicBoolean(false);

    public NonBlockingCoordinator() {
        super();
    }

//============================================================================================================
//              COORDINATION HANDLING
//============================================================================================================

    public void startElection(boolean force) throws ChannelException {
        synchronized (electionMutex) {
            Member local = getLocalMember(false);
            Member[] others = membership.getMembers();
            fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_START_ELECT,this,"Election initiated"));
            if ( others.length == 0 ) {
                this.viewId = new UniqueId(UUIDGenerator.randomUUID(false));
                this.view = new Membership(local,AbsoluteOrder.comp, true);
                this.handleViewConf(createElectionMsg(local,others,local), view);
                return; //the only member, no need for an election
            }
            if ( suggestedviewId != null ) {

                if ( view != null && Arrays.diff(view,suggestedView,local).length == 0 &&  Arrays.diff(suggestedView,view,local).length == 0) {
                    suggestedviewId = null;
                    suggestedView = null;
                    fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_ELECT_ABANDONED,this,"Election abandoned, running election matches view"));
                } else {
                    fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_ELECT_ABANDONED,this,"Election abandoned, election running"));
                }
                return; //election already running, I'm not allowed to have two of them
            }
            if ( view != null && Arrays.diff(view,membership,local).length == 0 &&  Arrays.diff(membership,view,local).length == 0) {
                fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_ELECT_ABANDONED,this,"Election abandoned, view matches membership"));
                return; //already have this view installed
            }
            int prio = AbsoluteOrder.comp.compare(local,others[0]);
            Member leader = ( prio < 0 )?local:others[0];//am I the leader in my view?
            if ( local.equals(leader) || force ) {
                CoordinationMessage msg = createElectionMsg(local, others, leader);
                suggestedviewId = msg.getId();
                suggestedView = new Membership(local,AbsoluteOrder.comp,true);
                Arrays.fill(suggestedView,msg.getMembers());
                fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_PROCESS_ELECT,this,"Election, sending request"));
                sendElectionMsg(local,others[0],msg);
            } else {
                try {
                    coordMsgReceived.set(false);
                    fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_WAIT_FOR_MSG,this,"Election, waiting for request"));
                    electionMutex.wait(waitForCoordMsgTimeout);
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                }
                String msg;
                if (suggestedviewId == null && !coordMsgReceived.get()) {
                    if (Thread.interrupted()) {
                        msg = "Election abandoned, waiting interrupted.";
                    } else {
                        msg = "Election abandoned, waiting timed out.";
                    }
                } else {
                    msg = "Election abandoned, received a message";
                }
                fireInterceptorEvent(new CoordinationEvent(
                        CoordinationEvent.EVT_ELECT_ABANDONED, this, msg));
            }
        }
    }

    private CoordinationMessage createElectionMsg(Member local, Member[] others, Member leader) {
        Membership m = new Membership(local,AbsoluteOrder.comp,true);
        Arrays.fill(m,others);
        Member[] mbrs = m.getMembers();
        m.reset();
        CoordinationMessage msg = new CoordinationMessage(leader, local, mbrs,new UniqueId(UUIDGenerator.randomUUID(true)), COORD_REQUEST);
        return msg;
    }

    protected void sendElectionMsg(Member local, Member next, CoordinationMessage msg) throws ChannelException {
        fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_SEND_MSG,this,"Sending election message to("+next.getName()+")"));
        super.sendMessage(new Member[] {next}, createData(msg, local), null);
    }

    protected void sendElectionMsgToNextInline(Member local, CoordinationMessage msg) throws ChannelException {
        int next = Arrays.nextIndex(local,msg.getMembers());
        int current = next;
        msg.leader = msg.getMembers()[0];
        boolean sent =  false;
        while ( !sent && current >= 0 ) {
            try {
                sendElectionMsg(local, msg.getMembers()[current], msg);
                sent = true;
            }catch ( ChannelException x  ) {
                log.warn(sm.getString("nonBlockingCoordinator.electionMessage.sendfailed", msg.getMembers()[current]));
                current = Arrays.nextIndex(msg.getMembers()[current],msg.getMembers());
                if ( current == next ) {
                    throw x;
                }
            }
        }
    }

    public ChannelData createData(CoordinationMessage msg, Member local) {
        msg.write();
        ChannelData data = new ChannelData(true);
        data.setAddress(local);
        data.setMessage(msg.getBuffer());
        data.setOptions(Channel.SEND_OPTIONS_USE_ACK);
        data.setTimestamp(System.currentTimeMillis());
        return data;
    }

    protected boolean alive(Member mbr) {
        return memberAlive(mbr, waitForCoordMsgTimeout);
    }

    protected boolean memberAlive(Member mbr, long conTimeout) {
        //could be a shutdown notification
        if ( Arrays.equals(mbr.getCommand(),Member.SHUTDOWN_PAYLOAD) ) {
            return false;
        }

        try (Socket socket = new Socket()) {
            InetAddress ia = InetAddress.getByAddress(mbr.getHost());
            InetSocketAddress addr = new InetSocketAddress(ia, mbr.getPort());
            socket.connect(addr, (int) conTimeout);
            return true;
        } catch (SocketTimeoutException | ConnectException x) {
            //do nothing, we couldn't connect
        } catch (Exception x) {
            log.error(sm.getString("nonBlockingCoordinator.memberAlive.failed"),x);
        }
        return false;
    }

    protected Membership mergeOnArrive(CoordinationMessage msg) {
        fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_PRE_MERGE,this,"Pre merge"));
        Member local = getLocalMember(false);
        Membership merged = new Membership(local,AbsoluteOrder.comp,true);
        Arrays.fill(merged,msg.getMembers());
        Arrays.fill(merged,getMembers());
        Member[] diff = Arrays.diff(merged,membership,local);
        for (Member member : diff) {
            if (!alive(member)) {
                merged.removeMember(member);
            } else {
                memberAdded(member, false);
            }
        }
        fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_POST_MERGE,this,"Post merge"));
        return merged;
    }

    protected void processCoordMessage(CoordinationMessage msg) throws ChannelException {
        if ( !coordMsgReceived.get() ) {
            coordMsgReceived.set(true);
            synchronized (electionMutex) { electionMutex.notifyAll();}
        }
        Membership merged = mergeOnArrive(msg);
        if (isViewConf(msg)) {
            handleViewConf(msg, merged);
        } else {
            handleToken(msg, merged);
        }
    }

    protected void handleToken(CoordinationMessage msg, Membership merged) throws ChannelException {
        Member local = getLocalMember(false);
        if ( local.equals(msg.getSource()) ) {
            //my message msg.src=local
            handleMyToken(local, msg, merged);
        } else {
            handleOtherToken(local, msg, merged);
        }
    }

    protected void handleMyToken(Member local, CoordinationMessage msg, Membership merged) throws ChannelException {
        if ( local.equals(msg.getLeader()) ) {
            //no leadership change
            if ( Arrays.sameMembers(msg.getMembers(),merged.getMembers()) ) {
                msg.type = COORD_CONF;
                super.sendMessage(Arrays.remove(msg.getMembers(),local),createData(msg,local),null);
                handleViewConf(msg, merged);
            } else {
                //membership change
                suggestedView = new Membership(local,AbsoluteOrder.comp,true);
                suggestedviewId = msg.getId();
                Arrays.fill(suggestedView,merged.getMembers());
                msg.view = merged.getMembers();
                sendElectionMsgToNextInline(local,msg);
            }
        } else {
            //leadership change
            suggestedView = null;
            suggestedviewId = null;
            msg.view = merged.getMembers();
            sendElectionMsgToNextInline(local,msg);
        }
    }

    protected void handleOtherToken(Member local, CoordinationMessage msg, Membership merged) throws ChannelException {
        if ( local.equals(msg.getLeader()) ) {
            //I am the new leader
            //startElection(false);
        } else {
            msg.view = merged.getMembers();
            sendElectionMsgToNextInline(local,msg);
        }
    }

    protected void handleViewConf(CoordinationMessage msg, Membership merged) throws ChannelException {
        if ( viewId != null && msg.getId().equals(viewId) )
         {
            return;//we already have this view
        }
        view = new Membership(getLocalMember(false),AbsoluteOrder.comp,true);
        Arrays.fill(view,msg.getMembers());
        viewId = msg.getId();

        if ( viewId.equals(suggestedviewId) ) {
            suggestedView = null;
            suggestedviewId = null;
        }

        if (suggestedView != null && AbsoluteOrder.comp.compare(suggestedView.getMembers()[0],merged.getMembers()[0])<0 ) {
            suggestedView = null;
            suggestedviewId = null;
        }

        fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_CONF_RX,this,"Accepted View"));

        if ( suggestedviewId == null && hasHigherPriority(merged.getMembers(),membership.getMembers()) ) {
            startElection(false);
        }
    }

    protected boolean isViewConf(CoordinationMessage msg) {
        return Arrays.contains(msg.getType(),0,COORD_CONF,0,COORD_CONF.length);
    }

    protected boolean hasHigherPriority(Member[] complete, Member[] local) {
        if ( local == null || local.length == 0 ) {
            return false;
        }
        if ( complete == null || complete.length == 0 ) {
            return true;
        }
        AbsoluteOrder.absoluteOrder(complete);
        AbsoluteOrder.absoluteOrder(local);
        return (AbsoluteOrder.comp.compare(complete[0],local[0]) > 0);

    }


    /**
     * Returns coordinator if one is available
     * @return Member
     */
    public Member getCoordinator() {
        return (view != null && view.hasMembers()) ? view.getMembers()[0] : null;
    }

    public Member[] getView() {
        return (view != null && view.hasMembers()) ? view.getMembers() : new Member[0];
    }

    public UniqueId getViewId() {
        return viewId;
    }

    /**
    * Block in/out messages while a election is going on
    */
   protected void halt() {

   }

   /**
    * Release lock for in/out messages election is completed
    */
   protected void release() {

   }

   /**
    * Wait for an election to end
    */
   protected void waitForRelease() {

   }


//============================================================================================================
//              OVERRIDDEN METHODS FROM CHANNEL INTERCEPTOR BASE
//============================================================================================================
    @Override
    public void start(int svc) throws ChannelException {
            if (membership == null) {
                setupMembership();
            }
            if (started) {
                return;
            }
            fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_START, this, "Before start"));
            super.start(startsvc);
            started = true;
            if (view == null) {
                view = new Membership(super.getLocalMember(true), AbsoluteOrder.comp, true);
            }
            fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_START, this, "After start"));
            startElection(false);
    }

    @Override
    public void stop(int svc) throws ChannelException {
        try {
            halt();
            synchronized (electionMutex) {
                if (!started) {
                    return;
                }
                started = false;
                fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_STOP, this, "Before stop"));
                super.stop(startsvc);
                this.view = null;
                this.viewId = null;
                this.suggestedView = null;
                this.suggestedviewId = null;
                this.membership.reset();
                fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_STOP, this, "After stop"));
            }
        }finally {
            release();
        }
    }


    @Override
    public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload) throws ChannelException {
        waitForRelease();
        super.sendMessage(destination, msg, payload);
    }

    @Override
    public void messageReceived(ChannelMessage msg) {
        if ( Arrays.contains(msg.getMessage().getBytesDirect(),0,COORD_ALIVE,0,COORD_ALIVE.length) ) {
            //ignore message, its an alive message
            fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_MSG_ARRIVE,this,"Alive Message"));

        } else if ( Arrays.contains(msg.getMessage().getBytesDirect(),0,COORD_HEADER,0,COORD_HEADER.length) ) {
            try {
                CoordinationMessage cmsg = new CoordinationMessage(msg.getMessage());
                Member[] cmbr = cmsg.getMembers();
                fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_MSG_ARRIVE,this,"Coord Msg Arrived("+Arrays.toNameString(cmbr)+")"));
                processCoordMessage(cmsg);
            }catch ( ChannelException x ) {
                log.error(sm.getString("nonBlockingCoordinator.processCoordinationMessage.failed"),x);
            }
        } else {
            super.messageReceived(msg);
        }
    }

    @Override
    public void memberAdded(Member member) {
        memberAdded(member,true);
    }

    public void memberAdded(Member member,boolean elect) {
        if (membership == null) {
            setupMembership();
        }
        if (membership.memberAlive(member)) {
            super.memberAdded(member);
        }
        try {
            fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_MBR_ADD,this,"Member add("+member.getName()+")"));
            if (started && elect) {
                startElection(false);
            }
        } catch (ChannelException x) {
            log.error(sm.getString("nonBlockingCoordinator.memberAdded.failed"),x);
        }
    }

    @Override
    public void memberDisappeared(Member member) {
        membership.removeMember(member);
        super.memberDisappeared(member);
        try {
            fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_MBR_DEL,this,"Member remove("+member.getName()+")"));
            if (started && (isCoordinator() || isHighest()))
             {
                startElection(true); //to do, if a member disappears, only the coordinator can start
            }
        } catch (ChannelException x) {
            log.error(sm.getString("nonBlockingCoordinator.memberDisappeared.failed"),x);
        }
    }

    public boolean isHighest() {
        Member local = getLocalMember(false);
        if ( membership.getMembers().length == 0 ) {
            return true;
        } else {
            return AbsoluteOrder.comp.compare(local,membership.getMembers()[0])<=0;
        }
    }

    public boolean isCoordinator() {
        Member coord = getCoordinator();
        return coord != null && getLocalMember(false).equals(coord);
    }

    @Override
    public void heartbeat() {
        try {
            Member local = getLocalMember(false);
            if ( view != null && (Arrays.diff(view,membership,local).length != 0 ||  Arrays.diff(membership,view,local).length != 0) ) {
                if ( isHighest() ) {
                    fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_START_ELECT, this,
                            sm.getString("nonBlockingCoordinator.heartbeat.inconsistency")));
                    startElection(true);
                }
            }
        } catch ( Exception x  ){
            log.error(sm.getString("nonBlockingCoordinator.heartbeat.failed"),x);
        } finally {
            super.heartbeat();
        }
    }

    /**
     * has members
     */
    @Override
    public boolean hasMembers() {

        return membership.hasMembers();
    }

    /**
     * Get all current cluster members
     * @return all members or empty array
     */
    @Override
    public Member[] getMembers() {

        return membership.getMembers();
    }

    /**
     *
     * @param mbr Member
     * @return Member
     */
    @Override
    public Member getMember(Member mbr) {

        return membership.getMember(mbr);
    }

    /**
     * Return the member that represents this node.
     *
     * @return Member
     */
    @Override
    public Member getLocalMember(boolean incAlive) {
        Member local = super.getLocalMember(incAlive);
        if ( view == null && (local != null)) {
            setupMembership();
        }
        return local;
    }

    protected synchronized void setupMembership() {
        if ( membership == null ) {
            membership  = new Membership(super.getLocalMember(true),AbsoluteOrder.comp,false);
        }
    }


//============================================================================================================
//              HELPER CLASSES FOR COORDINATION
//============================================================================================================


    public static class CoordinationMessage {
        //X{A-ldr, A-src, mbrs-A,B,C,D}
        protected final XByteBuffer buf;
        protected Member leader;
        protected Member source;
        protected Member[] view;
        protected UniqueId id;
        protected byte[] type;

        public CoordinationMessage(XByteBuffer buf) {
            this.buf = buf;
            parse();
        }

        public CoordinationMessage(Member leader,
                                   Member source,
                                   Member[] view,
                                   UniqueId id,
                                   byte[] type) {
            this.buf = new XByteBuffer(4096,false);
            this.leader = leader;
            this.source = source;
            this.view = view;
            this.id = id;
            this.type = type;
            this.write();
        }


        public byte[] getHeader() {
            return NonBlockingCoordinator.COORD_HEADER;
        }

        public Member getLeader() {
            if ( leader == null ) {
                parse();
            }
            return leader;
        }

        public Member getSource() {
            if ( source == null ) {
                parse();
            }
            return source;
        }

        public UniqueId getId() {
            if ( id == null ) {
                parse();
            }
            return id;
        }

        public Member[] getMembers() {
            if ( view == null ) {
                parse();
            }
            return view;
        }

        public byte[] getType() {
            if (type == null ) {
                parse();
            }
            return type;
        }

        public XByteBuffer getBuffer() {
            return this.buf;
        }

        public void parse() {
            //header
            int offset = 16;
            //leader
            int ldrLen = XByteBuffer.toInt(buf.getBytesDirect(),offset);
            offset += 4;
            byte[] ldr = new byte[ldrLen];
            System.arraycopy(buf.getBytesDirect(),offset,ldr,0,ldrLen);
            leader = MemberImpl.getMember(ldr);
            offset += ldrLen;
            //source
            int srcLen = XByteBuffer.toInt(buf.getBytesDirect(),offset);
            offset += 4;
            byte[] src = new byte[srcLen];
            System.arraycopy(buf.getBytesDirect(),offset,src,0,srcLen);
            source = MemberImpl.getMember(src);
            offset += srcLen;
            //view
            int mbrCount = XByteBuffer.toInt(buf.getBytesDirect(),offset);
            offset += 4;
            view = new Member[mbrCount];
            for (int i=0; i<view.length; i++ ) {
                int mbrLen = XByteBuffer.toInt(buf.getBytesDirect(),offset);
                offset += 4;
                byte[] mbr = new byte[mbrLen];
                System.arraycopy(buf.getBytesDirect(), offset, mbr, 0, mbrLen);
                view[i] = MemberImpl.getMember(mbr);
                offset += mbrLen;
            }
            //id
            this.id = new UniqueId(buf.getBytesDirect(),offset,16);
            offset += 16;
            type = new byte[16];
            System.arraycopy(buf.getBytesDirect(), offset, type, 0, type.length);
            offset += 16;

        }

        public void write() {
            buf.reset();
            //header
            buf.append(COORD_HEADER,0,COORD_HEADER.length);
            //leader
            byte[] ldr = leader.getData(false,false);
            buf.append(ldr.length);
            buf.append(ldr,0,ldr.length);
            ldr = null;
            //source
            byte[] src = source.getData(false,false);
            buf.append(src.length);
            buf.append(src,0,src.length);
            src = null;
            //view
            buf.append(view.length);
            for (Member member : view) {
                byte[] mbr = member.getData(false, false);
                buf.append(mbr.length);
                buf.append(mbr, 0, mbr.length);
            }
            //id
            buf.append(id.getBytes(),0,id.getBytes().length);
            buf.append(type,0,type.length);
        }
    }

    @Override
    public void fireInterceptorEvent(InterceptorEvent event) {
        if (event instanceof CoordinationEvent) {
            if (((CoordinationEvent) event).type == CoordinationEvent.EVT_CONF_RX) {
                log.info(event);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug(event);
                }
            }
        }
    }

    public static class CoordinationEvent implements InterceptorEvent {
        public static final int EVT_START = 1;
        public static final int EVT_MBR_ADD = 2;
        public static final int EVT_MBR_DEL = 3;
        public static final int EVT_START_ELECT = 4;
        public static final int EVT_PROCESS_ELECT = 5;
        public static final int EVT_MSG_ARRIVE = 6;
        public static final int EVT_PRE_MERGE = 7;
        public static final int EVT_POST_MERGE = 8;
        public static final int EVT_WAIT_FOR_MSG = 9;
        public static final int EVT_SEND_MSG = 10;
        public static final int EVT_STOP = 11;
        public static final int EVT_CONF_RX = 12;
        public static final int EVT_ELECT_ABANDONED = 13;

        final int type;
        final ChannelInterceptor interceptor;
        final Member coord;
        final Member[] mbrs;
        final String info;
        final Membership view;
        final Membership suggestedView;
        public CoordinationEvent(int type,ChannelInterceptor interceptor, String info) {
            this.type = type;
            this.interceptor = interceptor;
            this.coord = ((NonBlockingCoordinator)interceptor).getCoordinator();
            this.mbrs = ((NonBlockingCoordinator)interceptor).membership.getMembers();
            this.info = info;
            this.view = ((NonBlockingCoordinator)interceptor).view;
            this.suggestedView = ((NonBlockingCoordinator)interceptor).suggestedView;
        }

        @Override
        public int getEventType() {
            return type;
        }

        @Override
        public String getEventTypeDesc() {
            switch (type) {
                case  EVT_START: return "EVT_START:"+info;
                case  EVT_MBR_ADD: return "EVT_MBR_ADD:"+info;
                case  EVT_MBR_DEL: return "EVT_MBR_DEL:"+info;
                case  EVT_START_ELECT: return "EVT_START_ELECT:"+info;
                case  EVT_PROCESS_ELECT: return "EVT_PROCESS_ELECT:"+info;
                case  EVT_MSG_ARRIVE: return "EVT_MSG_ARRIVE:"+info;
                case  EVT_PRE_MERGE: return "EVT_PRE_MERGE:"+info;
                case  EVT_POST_MERGE: return "EVT_POST_MERGE:"+info;
                case  EVT_WAIT_FOR_MSG: return "EVT_WAIT_FOR_MSG:"+info;
                case  EVT_SEND_MSG: return "EVT_SEND_MSG:"+info;
                case  EVT_STOP: return "EVT_STOP:"+info;
                case  EVT_CONF_RX: return "EVT_CONF_RX:"+info;
                case EVT_ELECT_ABANDONED: return "EVT_ELECT_ABANDONED:"+info;
                default: return "Unknown";
            }
        }

        @Override
        public ChannelInterceptor getInterceptor() {
            return interceptor;
        }

        @Override
        public String toString() {
            Member local = interceptor.getLocalMember(false);
            return sm.getString("nonBlockingCoordinator.report", Integer.valueOf(type),
                    (local != null ? local.getName() : ""),
                    (coord != null ? coord.getName() : ""),
                    Arrays.toNameString(view != null ? view.getMembers() : null),
                    Arrays.toNameString(suggestedView != null ? suggestedView.getMembers() : null),
                    Arrays.toNameString(mbrs), info);
        }
    }


}
