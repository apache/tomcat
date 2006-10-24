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


import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

import org.apache.catalina.tribes.MembershipListener;
import java.util.Arrays;
import java.net.SocketTimeoutException;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.Channel;
import java.net.InetSocketAddress;

/**
 * A <b>membership</b> implementation using simple multicast.
 * This is the representation of a multicast membership service.
 * This class is responsible for maintaining a list of active cluster nodes in the cluster.
 * If a node fails to send out a heartbeat, the node will be dismissed.
 * This is the low level implementation that handles the multicasting sockets.
 * Need to fix this, could use java.nio and only need one thread to send and receive, or
 * just use a timeout on the receive
 * @author Filip Hanik
 * @version $Revision$, $Date$
 */
public class McastServiceImpl
{
    private static org.apache.juli.logging.Log log =
        org.apache.juli.logging.LogFactory.getLog( McastService.class );
    
    protected static int MAX_PACKET_SIZE = 65535;
    /**
     * Internal flag used for the listen thread that listens to the multicasting socket.
     */
    protected boolean doRunSender = false;
    protected boolean doRunReceiver = false;
    protected int startLevel = 0;
    /**
     * Socket that we intend to listen to
     */
    protected MulticastSocket socket;
    /**
     * The local member that we intend to broad cast over and over again
     */
    protected MemberImpl member;
    /**
     * The multicast address
     */
    protected InetAddress address;
    /**
     * The multicast port
     */
    protected int port;
    /**
     * The time it takes for a member to expire.
     */
    protected long timeToExpiration;
    /**
     * How often to we send out a broadcast saying we are alive, must be smaller than timeToExpiration
     */
    protected long sendFrequency;
    /**
     * Reuse the sendPacket, no need to create a new one everytime
     */
    protected DatagramPacket sendPacket;
    /**
     * Reuse the receivePacket, no need to create a new one everytime
     */
    protected DatagramPacket receivePacket;
    /**
     * The membership, used so that we calculate memberships when they arrive or don't arrive
     */
    protected Membership membership;
    /**
     * The actual listener, for callback when shits goes down
     */
    protected MembershipListener service;
    /**
     * Thread to listen for pings
     */
    protected ReceiverThread receiver;
    /**
     * Thread to send pings
     */
    protected SenderThread sender;

    /**
     * When was the service started
     */
    protected long serviceStartTime = System.currentTimeMillis();
    
    /**
     * Time to live for the multicast packets that are being sent out
     */
    protected int mcastTTL = -1;
    /**
     * Read timeout on the mcast socket
     */
    protected int mcastSoTimeout = -1;
    /**
     * bind address
     */
    protected InetAddress mcastBindAddress = null;
    
    /**
     * Create a new mcast service impl
     * @param member - the local member
     * @param sendFrequency - the time (ms) in between pings sent out
     * @param expireTime - the time (ms) for a member to expire
     * @param port - the mcast port
     * @param bind - the bind address (not sure this is used yet)
     * @param mcastAddress - the mcast address
     * @param service - the callback service
     * @throws IOException
     */
    public McastServiceImpl(
        MemberImpl member,
        long sendFrequency,
        long expireTime,
        int port,
        InetAddress bind,
        InetAddress mcastAddress,
        int ttl,
        int soTimeout,
        MembershipListener service)
    throws IOException {
        this.member = member;
        this.address = mcastAddress;
        this.port = port;
        this.mcastSoTimeout = soTimeout;
        this.mcastTTL = ttl;
        this.mcastBindAddress = bind;
        this.timeToExpiration = expireTime;
        this.service = service;
        this.sendFrequency = sendFrequency;
        setupSocket();
        sendPacket = new DatagramPacket(new byte[MAX_PACKET_SIZE],MAX_PACKET_SIZE);
        sendPacket.setAddress(address);
        sendPacket.setPort(port);
        receivePacket = new DatagramPacket(new byte[MAX_PACKET_SIZE],MAX_PACKET_SIZE);
        receivePacket.setAddress(address);
        receivePacket.setPort(port);
        membership = new Membership(member);
    }
    
    protected void setupSocket() throws IOException {
        if (mcastBindAddress != null) socket = new MulticastSocket(new InetSocketAddress(mcastBindAddress, port));
        else socket = new MulticastSocket(port);
        socket.setLoopbackMode(false); //hint that we don't need loop back messages
        if (mcastBindAddress != null) {
			if(log.isInfoEnabled())
                log.info("Setting multihome multicast interface to:" +mcastBindAddress);
            socket.setInterface(mcastBindAddress);
        } //end if
        //force a so timeout so that we don't block forever
        if ( mcastSoTimeout <= 0 ) mcastSoTimeout = (int)sendFrequency;
        if(log.isInfoEnabled())
            log.info("Setting cluster mcast soTimeout to "+mcastSoTimeout);
        socket.setSoTimeout(mcastSoTimeout);

        if ( mcastTTL >= 0 ) {
			if(log.isInfoEnabled())
                log.info("Setting cluster mcast TTL to " + mcastTTL);
            socket.setTimeToLive(mcastTTL);
        }
    }
    
    

    /**
     * Start the service
     * @param level 1 starts the receiver, level 2 starts the sender
     * @throws IOException if the service fails to start
     * @throws IllegalStateException if the service is already started
     */
    public synchronized void start(int level) throws IOException {
        boolean valid = false;
        if ( (level & Channel.MBR_RX_SEQ)==Channel.MBR_RX_SEQ ) {
            if ( receiver != null ) throw new IllegalStateException("McastService.receive already running.");
            if ( sender == null ) socket.joinGroup(address);
            doRunReceiver = true;
            receiver = new ReceiverThread();
            receiver.setDaemon(true);
            receiver.start();
            valid = true;
        } 
        if ( (level & Channel.MBR_TX_SEQ)==Channel.MBR_TX_SEQ ) {
            if ( sender != null ) throw new IllegalStateException("McastService.send already running.");
            if ( receiver == null ) socket.joinGroup(address);
            //make sure at least one packet gets out there
            send(false);
            doRunSender = true;
            serviceStartTime = System.currentTimeMillis();
            sender = new SenderThread(sendFrequency);
            sender.setDaemon(true);
            sender.start();
            //we have started the receiver, but not yet waited for membership to establish
            valid = true;
        } 
        if (!valid) {
            throw new IllegalArgumentException("Invalid start level. Only acceptable levels are Channel.MBR_RX_SEQ and Channel.MBR_TX_SEQ");
        }
        //pause, once or twice
        waitForMembers(level);
        startLevel = (startLevel | level);
    }

    private void waitForMembers(int level) {
        long memberwait = sendFrequency*2;
        if(log.isInfoEnabled())
            log.info("Sleeping for "+memberwait+" milliseconds to establish cluster membership, start level:"+level);
        try {Thread.sleep(memberwait);}catch (InterruptedException ignore){}
        if(log.isInfoEnabled())
            log.info("Done sleeping, membership established, start level:"+level);
    }

    /**
     * Stops the service
     * @throws IOException if the service fails to disconnect from the sockets
     */
    public synchronized boolean stop(int level) throws IOException {
        boolean valid = false;
        
        if ( (level & Channel.MBR_RX_SEQ)==Channel.MBR_RX_SEQ ) {
            valid = true;
            doRunReceiver = false;
            if ( receiver !=null ) receiver.interrupt();
            receiver = null;
        } 
        if ( (level & Channel.MBR_TX_SEQ)==Channel.MBR_TX_SEQ ) {
            valid = true;
            doRunSender = false;
            if ( sender != null )sender.interrupt();
            sender = null;
        } 
        
        if (!valid) {
            throw new IllegalArgumentException("Invalid stop level. Only acceptable levels are Channel.MBR_RX_SEQ and Channel.MBR_TX_SEQ");
        }
        startLevel = (startLevel & (~level));
        //we're shutting down, send a shutdown message and close the socket
        if ( startLevel == 0 ) {
            //send a stop message
            member.setCommand(Member.SHUTDOWN_PAYLOAD);
            member.getData(true, true);
            send(false);
            //leave mcast group
            try {socket.leaveGroup(address);}catch ( Exception ignore){}
            serviceStartTime = Long.MAX_VALUE;
        }
        return (startLevel == 0);
    }

    /**
     * Receive a datagram packet, locking wait
     * @throws IOException
     */
    public void receive() throws IOException {
        try {
            socket.receive(receivePacket);
            if(receivePacket.getLength() > MAX_PACKET_SIZE) {
                log.error("Multicast packet received was too long, dropping package:"+receivePacket.getLength());
            } else {
                byte[] data = new byte[receivePacket.getLength()];
                System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), data, 0, data.length);
                final MemberImpl m = MemberImpl.getMember(data);
                if (log.isTraceEnabled()) log.trace("Mcast receive ping from member " + m);
                Thread t = null;
                if (Arrays.equals(m.getCommand(), Member.SHUTDOWN_PAYLOAD)) {
                    if (log.isDebugEnabled()) log.debug("Member has shutdown:" + m);
                    membership.removeMember(m);
                    t = new Thread() {
                        public void run() {
                            service.memberDisappeared(m);
                        }
                    };
                } else if (membership.memberAlive(m)) {
                    if (log.isDebugEnabled()) log.debug("Mcast add member " + m);
                    t = new Thread() {
                        public void run() {
                            service.memberAdded(m);
                        }
                    };
                } //end if
                if ( t != null ) t.start();
            }
        } catch (SocketTimeoutException x ) { 
            //do nothing, this is normal, we don't want to block forever
            //since the receive thread is the same thread
            //that does membership expiration
        }
        checkExpired();
    }
    
    protected Object expiredMutex = new Object();
    protected void checkExpired() {
        synchronized (expiredMutex) {
            MemberImpl[] expired = membership.expire(timeToExpiration);
            for (int i = 0; i < expired.length; i++) {
                final MemberImpl member = expired[i];
                if (log.isDebugEnabled())
                    log.debug("Mcast exipre  member " + expired[i]);
                try {
                    Thread t = new Thread() {
                        public void run() {
                            service.memberDisappeared(member);
                        }
                    };
                    t.start();
                } catch (Exception x) {
                    log.error("Unable to process member disappeared message.", x);
                }
            }
        }
    }

    /**
     * Send a ping
     * @throws Exception
     */ 
    public void send(boolean checkexpired) throws IOException{
        //ignore if we haven't started the sender
        //if ( (startLevel&Channel.MBR_TX_SEQ) != Channel.MBR_TX_SEQ ) return;
        member.inc();
        if(log.isTraceEnabled())
            log.trace("Mcast send ping from member " + member);
        byte[] data = member.getData();
        DatagramPacket p = new DatagramPacket(data,data.length);
        p.setAddress(address);
        p.setPort(port);
        socket.send(p);
        if ( checkexpired ) checkExpired();
    }

    public long getServiceStartTime() {
       return this.serviceStartTime;
    }


    public class ReceiverThread extends Thread {
        public ReceiverThread() {
            super();
            setName("Cluster-MembershipReceiver");
        }
        public void run() {
            while ( doRunReceiver ) {
                try {
                    receive();
                } catch ( ArrayIndexOutOfBoundsException ax ) {
                    //we can ignore this, as it means we have an invalid package
                    //but we will log it to debug
                    if ( log.isDebugEnabled() )
                        log.debug("Invalid member mcast package.",ax);
                } catch ( Exception x ) {
                    log.warn("Error receiving mcast package. Sleeping 500ms",x);
                    try { Thread.sleep(500); } catch ( Exception ignore ){}
                    
                }
            }
        }
    }//class ReceiverThread

    public class SenderThread extends Thread {
        long time;
        public SenderThread(long time) {
            this.time = time;
            setName("Cluster-MembershipSender");

        }
        public void run() {
            while ( doRunSender ) {
                try {
                    send(true);
                } catch ( Exception x ) {
                    log.warn("Unable to send mcast message.",x);
                }
                try { Thread.sleep(time); } catch ( Exception ignore ) {}
            }
        }
    }//class SenderThread
}
