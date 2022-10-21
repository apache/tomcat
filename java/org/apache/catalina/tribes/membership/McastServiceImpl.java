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
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipListener;
import org.apache.catalina.tribes.MessageListener;
import org.apache.catalina.tribes.io.ChannelData;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * A <b>membership</b> implementation using simple multicast.
 * This is the representation of a multicast membership service.
 * This class is responsible for maintaining a list of active cluster nodes in the cluster.
 * If a node fails to send out a heartbeat, the node will be dismissed.
 * This is the low level implementation that handles the multicasting sockets.
 * Need to fix this, could use java.nio and only need one thread to send and receive, or
 * just use a timeout on the receive
 */
public class McastServiceImpl extends MembershipProviderBase {

    private static final Log log = LogFactory.getLog(McastService.class);

    protected static final int MAX_PACKET_SIZE = 65535;

    protected static final StringManager sm = StringManager.getManager(Constants.Package);
    /**
     * Internal flag used for the listen thread that listens to the multicasting socket.
     */
    protected volatile boolean doRunSender = false;
    protected volatile boolean doRunReceiver = false;
    protected volatile int startLevel = 0;
    /**
     * Socket that we intend to listen to
     */
    protected MulticastSocket socket;
    /**
     * The local member that we intend to broad cast over and over again
     */
    protected final MemberImpl member;
    /**
     * The multicast address
     */
    protected final InetAddress address;
    /**
     * The multicast port
     */
    protected final int port;
    /**
     * The time it takes for a member to expire.
     */
    protected final long timeToExpiration;
    /**
     * How often to we send out a broadcast saying we are alive, must be smaller than timeToExpiration
     */
    protected final long sendFrequency;
    /**
     * Reuse the sendPacket, no need to create a new one every time
     */
    protected DatagramPacket sendPacket;
    /**
     * Reuse the receivePacket, no need to create a new one every time
     */
    protected DatagramPacket receivePacket;

    /**
     * The actual listener, for callback when stuff goes down
     */
    protected final MembershipListener service;
    /**
     * The actual listener for broadcast callbacks
     */
    protected final MessageListener msgservice;
    /**
     * Thread to listen for pings
     */
    protected ReceiverThread receiver;
    /**
     * Thread to send pings
     */
    protected SenderThread sender;

    /**
     * Time to live for the multicast packets that are being sent out
     */
    protected final int mcastTTL;
    /**
     * Read timeout on the mcast socket
     */
    protected int mcastSoTimeout = -1;
    /**
     * bind address
     */
    protected final InetAddress mcastBindAddress;

    /**
     * nr of times the system has to fail before a recovery is initiated
     */
    protected int recoveryCounter = 10;

    /**
     * The time the recovery thread sleeps between recovery attempts
     */
    protected long recoverySleepTime = 5000;

    /**
     * Add the ability to turn on/off recovery
     */
    protected boolean recoveryEnabled = true;

    /**
     * disable/enable local loopback message
     */
    protected final boolean localLoopbackDisabled;

    private Channel channel;

    /**
     * Create a new mcast service instance.
     * @param member - the local member
     * @param sendFrequency - the time (ms) in between pings sent out
     * @param expireTime - the time (ms) for a member to expire
     * @param port - the mcast port
     * @param bind - the bind address (not sure this is used yet)
     * @param mcastAddress - the mcast address
     * @param ttl multicast ttl that will be set on the socket
     * @param soTimeout Socket timeout
     * @param service - the callback service
     * @param msgservice Message listener
     * @param localLoopbackDisabled - disable loopbackMode
     * @throws IOException Init error
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
        MembershipListener service,
        MessageListener msgservice,
        boolean localLoopbackDisabled)
    throws IOException {
        this.member = member;
        this.address = mcastAddress;
        this.port = port;
        this.mcastSoTimeout = soTimeout;
        this.mcastTTL = ttl;
        this.mcastBindAddress = bind;
        this.timeToExpiration = expireTime;
        this.service = service;
        this.msgservice = msgservice;
        this.sendFrequency = sendFrequency;
        this.localLoopbackDisabled = localLoopbackDisabled;
        init();
    }

    public void init() throws IOException {
        setupSocket();
        sendPacket = new DatagramPacket(new byte[MAX_PACKET_SIZE],MAX_PACKET_SIZE);
        sendPacket.setAddress(address);
        sendPacket.setPort(port);
        receivePacket = new DatagramPacket(new byte[MAX_PACKET_SIZE],MAX_PACKET_SIZE);
        receivePacket.setAddress(address);
        receivePacket.setPort(port);
        member.setCommand(new byte[0]);
        if ( membership == null ) {
            membership = new Membership(member);
        }
    }

    protected void setupSocket() throws IOException {
        if (mcastBindAddress != null) {
            try {
                log.info(sm.getString("mcastServiceImpl.bind", address, Integer.toString(port)));
                socket = new MulticastSocket(new InetSocketAddress(address,port));
            } catch (BindException e) {
                /*
                 * On some platforms (e.g. Linux) it is not possible to bind
                 * to the multicast address. In this case only bind to the
                 * port.
                 */
                log.info(sm.getString("mcastServiceImpl.bind.failed"));
                socket = new MulticastSocket(port);
            }
        } else {
            socket = new MulticastSocket(port);
        }
        socket.setLoopbackMode(localLoopbackDisabled); //hint if we want disable loop back(local machine) messages
        if (mcastBindAddress != null) {
            if(log.isInfoEnabled()) {
                log.info(sm.getString("mcastServiceImpl.setInterface", mcastBindAddress));
            }
            socket.setInterface(mcastBindAddress);
        } //end if
        //force a so timeout so that we don't block forever
        if (mcastSoTimeout <= 0) {
            mcastSoTimeout = (int)sendFrequency;
        }
        if (log.isInfoEnabled()) {
            log.info(sm.getString("mcastServiceImpl.setSoTimeout",
                    Integer.toString(mcastSoTimeout)));
        }
        socket.setSoTimeout(mcastSoTimeout);

        if ( mcastTTL >= 0 ) {
            if(log.isInfoEnabled()) {
                log.info(sm.getString("mcastServiceImpl.setTTL", Integer.toString(mcastTTL)));
            }
            socket.setTimeToLive(mcastTTL);
        }
    }


    /**
     * Start the service
     * @param level 1 starts the receiver, level 2 starts the sender
     * @throws IOException if the service fails to start
     * @throws IllegalStateException if the service is already started
     */
    @Override
    public synchronized void start(int level) throws IOException {
        boolean valid = false;
        if ( (level & Channel.MBR_RX_SEQ)==Channel.MBR_RX_SEQ ) {
            if ( receiver != null ) {
                throw new IllegalStateException(sm.getString("mcastServiceImpl.receive.running"));
            }
            try {
                if ( sender == null ) {
                    socket.joinGroup(address);
                }
            }catch (IOException iox) {
                log.error(sm.getString("mcastServiceImpl.unable.join"));
                throw iox;
            }
            doRunReceiver = true;
            receiver = new ReceiverThread();
            receiver.setDaemon(true);
            receiver.start();
            valid = true;
        }
        if ( (level & Channel.MBR_TX_SEQ)==Channel.MBR_TX_SEQ ) {
            if ( sender != null ) {
                throw new IllegalStateException(sm.getString("mcastServiceImpl.send.running"));
            }
            if ( receiver == null ) {
                socket.joinGroup(address);
            }
            //make sure at least one packet gets out there
            send(false);
            doRunSender = true;
            sender = new SenderThread(sendFrequency);
            sender.setDaemon(true);
            sender.start();
            //we have started the receiver, but not yet waited for membership to establish
            valid = true;
        }
        if (!valid) {
            throw new IllegalArgumentException(sm.getString("mcastServiceImpl.invalid.startLevel"));
        }
        //pause, once or twice
        waitForMembers(level);
        startLevel = (startLevel | level);
    }

    private void waitForMembers(int level) {
        long memberwait = sendFrequency*2;
        if(log.isInfoEnabled()) {
            log.info(sm.getString("mcastServiceImpl.waitForMembers.start",
                    Long.toString(memberwait), Integer.toString(level)));
        }
        try {Thread.sleep(memberwait);}catch (InterruptedException ignore){}
        if(log.isInfoEnabled()) {
            log.info(sm.getString("mcastServiceImpl.waitForMembers.done", Integer.toString(level)));
        }
    }

    /**
     * Stops the service.
     * @param level Stop status
     * @return <code>true</code> if the stop is complete
     * @throws IOException if the service fails to disconnect from the sockets
     */
    @Override
    public synchronized boolean stop(int level) throws IOException {
        boolean valid = false;

        if ( (level & Channel.MBR_RX_SEQ)==Channel.MBR_RX_SEQ ) {
            valid = true;
            doRunReceiver = false;
            if ( receiver !=null ) {
                receiver.interrupt();
            }
            receiver = null;
        }
        if ( (level & Channel.MBR_TX_SEQ)==Channel.MBR_TX_SEQ ) {
            valid = true;
            doRunSender = false;
            if ( sender != null ) {
                sender.interrupt();
            }
            sender = null;
        }

        if (!valid) {
            throw new IllegalArgumentException(sm.getString("mcastServiceImpl.invalid.stopLevel"));
        }
        startLevel = (startLevel & (~level));
        //we're shutting down, send a shutdown message and close the socket
        if ( startLevel == 0 ) {
            //send a stop message
            member.setCommand(Member.SHUTDOWN_PAYLOAD);
            send(false);
            //leave mcast group
            try {socket.leaveGroup(address);}catch ( Exception ignore){}
            try {socket.close();}catch ( Exception ignore){}
            member.setServiceStartTime(-1);
        }
        return (startLevel == 0);
    }

    /**
     * Receive a datagram packet, locking wait
     * @throws IOException Received failed
     */
    public void receive() throws IOException {
        boolean checkexpired = true;
        try {

            socket.receive(receivePacket);
            if(receivePacket.getLength() > MAX_PACKET_SIZE) {
                log.error(sm.getString("mcastServiceImpl.packet.tooLong",
                        Integer.toString(receivePacket.getLength())));
            } else {
                byte[] data = new byte[receivePacket.getLength()];
                System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), data, 0, data.length);
                if (XByteBuffer.firstIndexOf(data,0,MemberImpl.TRIBES_MBR_BEGIN)==0) {
                    memberDataReceived(data);
                } else {
                    memberBroadcastsReceived(data);
                }

            }
        } catch (SocketTimeoutException x ) {
            //do nothing, this is normal, we don't want to block forever
            //since the receive thread is the same thread
            //that does membership expiration
        }
        if (checkexpired) {
            checkExpired();
        }
    }

    private void memberDataReceived(byte[] data) {
        final Member m = MemberImpl.getMember(data);
        if (log.isTraceEnabled()) {
            log.trace("Mcast receive ping from member " + m);
        }
        Runnable t = null;
        if (Arrays.equals(m.getCommand(), Member.SHUTDOWN_PAYLOAD)) {
            if (log.isDebugEnabled()) {
                log.debug("Member has shutdown:" + m);
            }
            membership.removeMember(m);
            t = () -> {
                String name = Thread.currentThread().getName();
                try {
                    Thread.currentThread().setName("Membership-MemberDisappeared");
                    service.memberDisappeared(m);
                }finally {
                    Thread.currentThread().setName(name);
                }
            };
        } else if (membership.memberAlive(m)) {
            if (log.isDebugEnabled()) {
                log.debug("Mcast add member " + m);
            }
            t = () -> {
                String name = Thread.currentThread().getName();
                try {
                    Thread.currentThread().setName("Membership-MemberAdded");
                    service.memberAdded(m);
                }finally {
                    Thread.currentThread().setName(name);
                }
            };
        } //end if
        if ( t != null ) {
            executor.execute(t);
        }
    }

    private void memberBroadcastsReceived(final byte[] b) {
        if (log.isTraceEnabled()) {
            log.trace("Mcast received broadcasts.");
        }
        XByteBuffer buffer = new XByteBuffer(b,true);
        if (buffer.countPackages(true)>0) {
            int count = buffer.countPackages();
            final ChannelData[] data = new ChannelData[count];
            for (int i=0; i<count; i++) {
                try {
                    data[i] = buffer.extractPackage(true);
                }catch (IllegalStateException ise) {
                    log.debug("Unable to decode message.",ise);
                }
            }
            Runnable t = () -> {
                String name = Thread.currentThread().getName();
                try {
                    Thread.currentThread().setName("Membership-MemberAdded");
                    for (ChannelData datum : data) {
                        try {
                            if (datum != null && !member.equals(datum.getAddress())) {
                                msgservice.messageReceived(datum);
                            }
                        } catch (Throwable t1) {
                            if (t1 instanceof ThreadDeath) {
                                throw (ThreadDeath) t1;
                            }
                            if (t1 instanceof VirtualMachineError) {
                                throw (VirtualMachineError) t1;
                            }
                            log.error(sm.getString("mcastServiceImpl.unableReceive.broadcastMessage"), t1);
                        }
                    }
                }finally {
                    Thread.currentThread().setName(name);
                }
            };
            executor.execute(t);
        }
    }

    protected final Object expiredMutex = new Object();
    protected void checkExpired() {
        synchronized (expiredMutex) {
            Member[] expired = membership.expire(timeToExpiration);
            for (final Member member : expired) {
                if (log.isDebugEnabled()) {
                    log.debug("Mcast expire  member " + member);
                }
                try {
                    Runnable t = () -> {
                        String name = Thread.currentThread().getName();
                        try {
                            Thread.currentThread().setName("Membership-MemberExpired");
                            service.memberDisappeared(member);
                        } finally {
                            Thread.currentThread().setName(name);
                        }
                    };
                    executor.execute(t);
                } catch (Exception x) {
                    log.error(sm.getString("mcastServiceImpl.memberDisappeared.failed"), x);
                }
            }
        }
    }

    /**
     * Send a ping.
     * @param checkexpired <code>true</code> to check for expiration
     * @throws IOException Send error
     */
    public void send(boolean checkexpired) throws IOException {
        send(checkexpired,null);
    }

    private final Object sendLock = new Object();

    public void send(boolean checkexpired, DatagramPacket packet) throws IOException {
        checkexpired = (checkexpired && (packet==null));
        //ignore if we haven't started the sender
        //if ( (startLevel&Channel.MBR_TX_SEQ) != Channel.MBR_TX_SEQ ) return;
        if (packet==null) {
            member.inc();
            if(log.isTraceEnabled()) {
                log.trace("Mcast send ping from member " + member);
            }
            byte[] data = member.getData();
            packet = new DatagramPacket(data,data.length);
        } else if (log.isTraceEnabled()) {
            log.trace("Sending message broadcast "+packet.getLength()+ " bytes from "+ member);
        }
        packet.setAddress(address);
        packet.setPort(port);
        //TODO this operation is not thread safe
        synchronized (sendLock) {
            socket.send(packet);
        }
        if ( checkexpired ) {
            checkExpired();
        }
    }

    public long getServiceStartTime() {
        return (member!=null) ? member.getServiceStartTime() : -1l;
    }

    public int getRecoveryCounter() {
        return recoveryCounter;
    }

    public boolean isRecoveryEnabled() {
        return recoveryEnabled;
    }

    public long getRecoverySleepTime() {
        return recoverySleepTime;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public class ReceiverThread extends Thread {
        int errorCounter = 0;
        public ReceiverThread() {
            super();
            String channelName = "";
            if (channel.getName() != null) {
                channelName = "[" + channel.getName() + "]";
            }
            setName("Tribes-MembershipReceiver" + channelName);
        }
        @Override
        public void run() {
            while ( doRunReceiver ) {
                try {
                    receive();
                    errorCounter=0;
                } catch ( ArrayIndexOutOfBoundsException ax ) {
                    //we can ignore this, as it means we have an invalid package
                    //but we will log it to debug
                    if ( log.isDebugEnabled() ) {
                        log.debug("Invalid member mcast package.",ax);
                    }
                } catch ( Exception x ) {
                    if (errorCounter==0 && doRunReceiver) {
                        log.warn(sm.getString("mcastServiceImpl.error.receiving"),x);
                    } else if (log.isDebugEnabled()) {
                        log.debug("Error receiving mcast package"+(doRunReceiver?". Sleeping 500ms":"."),x);
                    }
                    if (doRunReceiver) {
                        try { Thread.sleep(500); } catch ( Exception ignore ){}
                        if ( (++errorCounter)>=recoveryCounter ) {
                            errorCounter=0;
                            RecoveryThread.recover(McastServiceImpl.this);
                        }
                    }
                }
            }
        }
    }//class ReceiverThread

    public class SenderThread extends Thread {
        final long time;
        int errorCounter=0;
        public SenderThread(long time) {
            this.time = time;
            String channelName = "";
            if (channel.getName() != null) {
                channelName = "[" + channel.getName() + "]";
            }
            setName("Tribes-MembershipSender" + channelName);

        }
        @Override
        public void run() {
            while ( doRunSender ) {
                try {
                    send(true);
                    errorCounter = 0;
                } catch ( Exception x ) {
                    if (errorCounter==0) {
                        log.warn(sm.getString("mcastServiceImpl.send.failed"),x);
                    } else {
                        log.debug("Unable to send mcast message.",x);
                    }
                    if ( (++errorCounter)>=recoveryCounter ) {
                        errorCounter=0;
                        RecoveryThread.recover(McastServiceImpl.this);
                    }
                }
                try { Thread.sleep(time); } catch ( Exception ignore ) {}
            }
        }
    }//class SenderThread

    protected static class RecoveryThread extends Thread {

        private static final AtomicBoolean running = new AtomicBoolean(false);

        public static synchronized void recover(McastServiceImpl parent) {

            if (!parent.isRecoveryEnabled()) {
                return;
            }

            if (!running.compareAndSet(false, true)) {
                return;
            }

            Thread t = new RecoveryThread(parent);
            String channelName = "";
            if (parent.channel.getName() != null) {
                channelName = "[" + parent.channel.getName() + "]";
            }
            t.setName("Tribes-MembershipRecovery" + channelName);
            t.setDaemon(true);
            t.start();
        }


        final McastServiceImpl parent;
        public RecoveryThread(McastServiceImpl parent) {
            this.parent = parent;
        }

        public boolean stopService() {
            try {
                parent.stop(Channel.MBR_RX_SEQ | Channel.MBR_TX_SEQ);
                return true;
            } catch (Exception x) {
                log.warn(sm.getString("mcastServiceImpl.recovery.stopFailed"), x);
                return false;
            }
        }
        public boolean startService() {
            try {
                parent.init();
                parent.start(Channel.MBR_RX_SEQ | Channel.MBR_TX_SEQ);
                return true;
            } catch (Exception x) {
                log.warn(sm.getString("mcastServiceImpl.recovery.startFailed"), x);
                return false;
            }
        }
        @Override
        public void run() {
            boolean success = false;
            int attempt = 0;
            try {
                while (!success) {
                    if(log.isInfoEnabled()) {
                        log.info(sm.getString("mcastServiceImpl.recovery"));
                    }
                    if (stopService() & startService()) {
                        success = true;
                        if(log.isInfoEnabled()) {
                            log.info(sm.getString("mcastServiceImpl.recovery.successful"));
                        }
                    }
                    try {
                        if (!success) {
                            if(log.isInfoEnabled()) {
                                log.info(sm.getString("mcastServiceImpl.recovery.failed",
                                        Integer.toString(++attempt),
                                        Long.toString(parent.recoverySleepTime)));
                            }
                            Thread.sleep(parent.recoverySleepTime);
                        }
                    }catch (InterruptedException ignore) {
                    }
                }
            }finally {
                running.set(false);
            }
        }
    }

    public void setRecoveryCounter(int recoveryCounter) {
        this.recoveryCounter = recoveryCounter;
    }

    public void setRecoveryEnabled(boolean recoveryEnabled) {
        this.recoveryEnabled = recoveryEnabled;
    }

    public void setRecoverySleepTime(long recoverySleepTime) {
        this.recoverySleepTime = recoverySleepTime;
    }
}
