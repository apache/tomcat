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
package org.apache.catalina.tribes.tipis;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelException.FaultyMember;
import org.apache.catalina.tribes.ChannelListener;
import org.apache.catalina.tribes.Heartbeat;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipListener;
import org.apache.catalina.tribes.group.Response;
import org.apache.catalina.tribes.group.RpcCallback;
import org.apache.catalina.tribes.group.RpcChannel;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.util.Arrays;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * An abstract replicated map implementation.
 *
 * @param <K> The type of Key
 * @param <V> The type of Value
 */
public abstract class AbstractReplicatedMap<K, V>
        implements Map<K,V>, Serializable, RpcCallback, ChannelListener, MembershipListener, Heartbeat {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The string manager for packaging specific messages. */
    protected static final StringManager sm = StringManager.getManager(AbstractReplicatedMap.class);

    /** The logger instance. */
    private final Log log = LogFactory.getLog(AbstractReplicatedMap.class); // must not be static

    /**
     * The default initial capacity - MUST be a power of two.
     */
    public static final int DEFAULT_INITIAL_CAPACITY = 16;

    /**
     * The load factor used when none specified in constructor.
     **/
    public static final float DEFAULT_LOAD_FACTOR = 0.75f;


    // ------------------------------------------------------------------------------
    // INSTANCE VARIABLES
    // ------------------------------------------------------------------------------
    /** The underlying concurrent map storing entries. */
    protected final ConcurrentMap<K,MapEntry<K,V>> innerMap;

    /**
     * Gets the state message type.
     *
     * @return the state message type
     */
    protected abstract int getStateMessageType();

    /**
     * Gets the replicate message type.
     *
     * @return the replicate message type
     */
    protected abstract int getReplicateMessageType();


    /**
     * Timeout for RPC messages, how long we will wait for a reply
     */
    protected transient long rpcTimeout = 5000;
    /**
     * Reference to the channel for sending messages
     */
    protected transient Channel channel;
    /**
     * The RpcChannel to send RPC messages through
     */
    protected transient RpcChannel rpcChannel;
    /**
     * The Map context name makes this map unique, this allows us to have more than one map shared through one channel
     */
    protected transient byte[] mapContextName;
    /**
     * Has the state been transferred
     */
    protected transient boolean stateTransferred = false;
    /**
     * Simple lock object for transfers
     */
    protected final transient Object stateMutex = new Object();
    /**
     * A list of members in our map
     */
    protected final transient HashMap<Member,Long> mapMembers = new HashMap<>();
    /**
     * Our default send options
     */
    protected transient int channelSendOptions = Channel.SEND_OPTIONS_DEFAULT;
    /**
     * The owner of this map, ala a SessionManager for example
     */
    protected transient MapOwner mapOwner;
    /**
     * External class loaders if serialization and deserialization is to be performed successfully.
     */
    protected transient ClassLoader[] externalLoaders;

    /**
     * The node we are currently backing up data to, this index will rotate on a round robin basis
     */
    protected transient int currentNode = 0;

    /**
     * Since the map keeps internal membership this is the timeout for a ping message to be responded to If a remote map
     * doesn't respond within this timeframe, its considered dead.
     */
    protected transient long accessTimeout = 5000;

    /**
     * Readable string of the mapContextName value
     */
    protected transient String mapname = "";

    /**
     * State of this map
     */
    private transient volatile State state = State.NEW;

    // ------------------------------------------------------------------------------
    // map owner interface
    // ------------------------------------------------------------------------------

    /**
     * Interface for the owner of this replicated map.
     */
    public interface MapOwner {
        /**
         * Called when an object becomes primary on this node.
         *
         * @param key The key of the object
         * @param value The value of the object
         */
        void objectMadePrimary(Object key, Object value);
    }

    // ------------------------------------------------------------------------------
    // CONSTRUCTORS
    // ------------------------------------------------------------------------------

    /**
     * Creates a new map.
     *
     * @param owner              The map owner
     * @param channel            The channel to use for communication
     * @param timeout            long - timeout for RPC messages
     * @param mapContextName     String - unique name for this map, to allow multiple maps per channel
     * @param initialCapacity    int - the size of this map, see HashMap
     * @param loadFactor         float - load factor, see HashMap
     * @param channelSendOptions Send options
     * @param cls                - a list of classloaders to be used for deserialization of objects.
     * @param terminate          - Flag for whether to terminate this map that failed to start.
     */
    public AbstractReplicatedMap(MapOwner owner, Channel channel, long timeout, String mapContextName,
            int initialCapacity, float loadFactor, int channelSendOptions, ClassLoader[] cls, boolean terminate) {
        innerMap = new ConcurrentHashMap<>(initialCapacity, loadFactor, 15);
        init(owner, channel, mapContextName, timeout, channelSendOptions, cls, terminate);

    }

    /**
     * Helper methods, wraps a single member in an array
     *
     * @param m Member
     *
     * @return Member[]
     */
    protected Member[] wrap(Member m) {
        if (m == null) {
            return new Member[0];
        } else {
            return new Member[] { m };
        }
    }

    /**
     * Initializes the map by creating the RPC channel, registering itself as a channel listener This method is also
     * responsible for initiating the state transfer
     *
     * @param owner              Object
     * @param channel            Channel
     * @param mapContextName     String
     * @param timeout            long
     * @param channelSendOptions int
     * @param cls                ClassLoader[]
     * @param terminate          - Flag for whether to terminate this map that failed to start.
     */
    protected void init(MapOwner owner, Channel channel, String mapContextName, long timeout, int channelSendOptions,
            ClassLoader[] cls, boolean terminate) {
        long start = System.currentTimeMillis();
        if (log.isInfoEnabled()) {
            log.info(sm.getString("abstractReplicatedMap.init.start", mapContextName));
        }
        this.mapOwner = owner;
        this.externalLoaders = cls;
        this.channelSendOptions = channelSendOptions;
        this.channel = channel;
        this.rpcTimeout = timeout;

        this.mapname = mapContextName;
        // unique context is more efficient if it is stored as bytes
        this.mapContextName = mapContextName.getBytes(StandardCharsets.ISO_8859_1);
        if (log.isTraceEnabled()) {
            log.trace(
                    "Created Lazy Map with name:" + mapContextName + ", bytes:" + Arrays.toString(this.mapContextName));
        }

        // create a rpc channel and add the map as a listener
        this.rpcChannel = new RpcChannel(this.mapContextName, channel, this);
        // add this map as a message listener
        this.channel.addChannelListener(this);
        // listen for membership notifications
        this.channel.addMembershipListener(this);

        try {
            // broadcast our map, this just notifies other members of our existence
            broadcast(MapMessage.MSG_INIT, true);
            // transfer state from another map
            transferState();
            // state is transferred, we are ready for messaging
            broadcast(MapMessage.MSG_START, true);
        } catch (ChannelException x) {
            if (terminate) {
                // Exception is logged further up stack
                log.warn(sm.getString("abstractReplicatedMap.unableSend.startMessage"));
                breakdown();
                throw new RuntimeException(sm.getString("abstractReplicatedMap.unableStart"), x);
            } else {
                log.warn(sm.getString("abstractReplicatedMap.unableSend.startMessage"), x);
            }
        }
        this.state = State.INITIALIZED;
        long complete = System.currentTimeMillis() - start;
        if (log.isInfoEnabled()) {
            log.info(sm.getString("abstractReplicatedMap.init.completed", mapContextName, Long.toString(complete)));
        }
    }


    /**
     * Sends a ping out to all the members in the cluster, not just map members that this map is alive.
     *
     * @param timeout long
     *
     * @throws ChannelException Send error
     */
    protected void ping(long timeout) throws ChannelException {
        MapMessage msg = new MapMessage(this.mapContextName, MapMessage.MSG_PING, false, null, null, null,
                channel.getLocalMember(false), null);
        if (channel.getMembers().length > 0) {
            try {
                // send a ping, wait for all nodes to reply
                Response[] resp = rpcChannel.send(channel.getMembers(), msg, RpcChannel.ALL_REPLY, (channelSendOptions),
                        (int) accessTimeout);
                for (Response response : resp) {
                    MapMessage mapMsg = (MapMessage) response.getMessage();
                    try {
                        mapMsg.deserialize(getExternalLoaders());
                        Member member = response.getSource();
                        State state = (State) mapMsg.getValue();
                        if (state.isAvailable()) {
                            memberAlive(member);
                        } else if (state == State.STATETRANSFERRED) {
                            synchronized (mapMembers) {
                                if (log.isInfoEnabled()) {
                                    log.info(sm.getString("abstractReplicatedMap.ping.stateTransferredMember", member));
                                }
                                if (mapMembers.containsKey(member)) {
                                    mapMembers.put(member, Long.valueOf(System.currentTimeMillis()));
                                }
                            }
                        } else {
                            if (log.isInfoEnabled()) {
                                log.info(sm.getString("abstractReplicatedMap.mapMember.unavailable", member));
                            }
                        }
                    } catch (ClassNotFoundException | IOException e) {
                        log.error(sm.getString("abstractReplicatedMap.unable.deserialize.MapMessage"), e);
                    }
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
        // update our map of members, expire some if we didn't receive a ping back
        synchronized (mapMembers) {
            Member[] members = mapMembers.keySet().toArray(new Member[0]);
            long now = System.currentTimeMillis();
            for (Member member : members) {
                long access = mapMembers.get(member).longValue();
                if ((now - access) > timeout) {
                    log.warn(sm.getString("abstractReplicatedMap.ping.timeout", member, mapname));
                    memberDisappeared(member);
                }
            }
        } // synch
    }

    /**
     * We have received a member alive notification
     *
     * @param member Member
     */
    protected void memberAlive(Member member) {
        mapMemberAdded(member);
        synchronized (mapMembers) {
            mapMembers.put(member, Long.valueOf(System.currentTimeMillis()));
        }
    }

    /**
     * Helper method to broadcast a message to all members in a channel
     *
     * @param msgtype int
     * @param rpc     boolean
     *
     * @throws ChannelException Send error
     */
    protected void broadcast(int msgtype, boolean rpc) throws ChannelException {
        Member[] members = channel.getMembers();
        // No destination.
        if (members.length == 0) {
            return;
        }
        // send out a map membership message, only wait for the first reply
        MapMessage msg = new MapMessage(this.mapContextName, msgtype, false, null, null, null,
                channel.getLocalMember(false), null);
        if (rpc) {
            Response[] resp = rpcChannel.send(members, msg, RpcChannel.FIRST_REPLY, (channelSendOptions), rpcTimeout);
            if (resp.length > 0) {
                for (Response response : resp) {
                    mapMemberAdded(response.getSource());
                    messageReceived(response.getMessage(), response.getSource());
                }
            } else {
                log.warn(sm.getString("abstractReplicatedMap.broadcast.noReplies"));
            }
        } else {
            channel.send(channel.getMembers(), msg, channelSendOptions);
        }
    }

    /**
     * Breaks down the map, removing all entries and closing channels.
     */
    public void breakdown() {
        this.state = State.DESTROYED;
        if (this.rpcChannel != null) {
            this.rpcChannel.breakdown();
        }
        if (this.channel != null) {
            try {
                broadcast(MapMessage.MSG_STOP, false);
            } catch (Exception ignore) {
                // Ignore
            }
            // cleanup
            this.channel.removeChannelListener(this);
            this.channel.removeMembershipListener(this);
        }
        this.rpcChannel = null;
        this.channel = null;
        synchronized (mapMembers) {
            this.mapMembers.clear();
        }
        innerMap.clear();
        this.stateTransferred = false;
        this.externalLoaders = null;
    }

    /**
     * Returns the hash code for this map based on the map context name.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Arrays.hashCode(this.mapContextName);
    }

    /**
     * Checks if this map is equal to another object.
     *
     * @param o the object to compare
     *
     * @return {@code true} if the maps have the same context name
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AbstractReplicatedMap)) {
            return false;
        }
        if (!(o.getClass().equals(this.getClass()))) {
            return false;
        }
        @SuppressWarnings("unchecked")
        AbstractReplicatedMap<K,V> other = (AbstractReplicatedMap<K,V>) o;
        return Arrays.equals(mapContextName, other.mapContextName);
    }

    // ------------------------------------------------------------------------------
    // GROUP COM INTERFACES
    // ------------------------------------------------------------------------------
    /**
     * Gets the map members from the given map.
     *
     * @param members The member map
     *
     * @return an array of members
     */
    public Member[] getMapMembers(HashMap<Member,Long> members) {
        return members.keySet().toArray(new Member[0]);
    }

    /**
     * Gets the current map members.
     *
     * @return an array of members
     */
    public Member[] getMapMembers() {
        synchronized (mapMembers) {
            return getMapMembers(mapMembers);
        }
    }

    /**
     * Gets the map members excluding the given members.
     *
     * @param exclude Members to exclude from the result
     *
     * @return an array of members excluding the specified ones
     */
    public Member[] getMapMembersExcl(Member[] exclude) {
        if (exclude == null) {
            return null;
        }
        synchronized (mapMembers) {
            @SuppressWarnings("unchecked") // mapMembers has the correct type
            HashMap<Member,Long> list = (HashMap<Member,Long>) mapMembers.clone();
            for (Member member : exclude) {
                list.remove(member);
            }
            return getMapMembers(list);
        }
    }


    /**
     * Replicates any changes to the object since the last time The object has to be primary, ie, if the object is a
     * proxy or a backup, it will not be replicated<br>
     *
     * @param key      The object to replicate
     * @param complete - if set to true, the object is replicated to its backup if set to false, only objects that
     *                     implement ReplicatedMapEntry and the isDirty() returns true will be replicated
     */
    public void replicate(K key, boolean complete) {
        if (log.isTraceEnabled()) {
            log.trace("Replicate invoked on key:" + key);
        }
        MapEntry<K,V> entry = innerMap.get(key);
        if (entry == null) {
            return;
        }
        if (!entry.isSerializable()) {
            return;
        }
        if (entry.isPrimary() && entry.getBackupNodes() != null && entry.getBackupNodes().length > 0) {
            // check to see if we need to replicate this object isDirty()||complete || isAccessReplicate()
            ReplicatedMapEntry rentry = null;
            if (entry.getValue() instanceof ReplicatedMapEntry) {
                rentry = (ReplicatedMapEntry) entry.getValue();
            }
            boolean isDirty = rentry != null && rentry.isDirty();
            boolean isAccess = rentry != null && rentry.isAccessReplicate();
            boolean repl = complete || isDirty || isAccess;

            if (!repl) {
                if (log.isTraceEnabled()) {
                    log.trace("Not replicating:" + key + ", no change made");
                }

                return;
            }
            // check to see if the message is diffable
            MapMessage msg = null;
            if (rentry != null && rentry.isDiffable() && (isDirty || complete)) {
                rentry.lock();
                try {
                    // construct a diff message
                    msg = new MapMessage(mapContextName, getReplicateMessageType(), true, (Serializable) entry.getKey(),
                            null, rentry.getDiff(), entry.getPrimary(), entry.getBackupNodes());
                    rentry.resetDiff();
                } catch (IOException ioe) {
                    log.error(sm.getString("abstractReplicatedMap.unable.diffObject"), ioe);
                } finally {
                    rentry.unlock();
                }
            }
            if (msg == null && complete) {
                // construct a complete
                msg = new MapMessage(mapContextName, getReplicateMessageType(), false, (Serializable) entry.getKey(),
                        (Serializable) entry.getValue(), null, entry.getPrimary(), entry.getBackupNodes());
            }
            if (msg == null) {
                // construct an access message
                msg = new MapMessage(mapContextName, MapMessage.MSG_ACCESS, false, (Serializable) entry.getKey(), null,
                        null, entry.getPrimary(), entry.getBackupNodes());
            }
            try {
                if (channel != null && entry.getBackupNodes() != null && entry.getBackupNodes().length > 0) {
                    if (rentry != null) {
                        rentry.setLastTimeReplicated(System.currentTimeMillis());
                    }
                    channel.send(entry.getBackupNodes(), msg, channelSendOptions);
                }
            } catch (ChannelException x) {
                log.error(sm.getString("abstractReplicatedMap.unable.replicate"), x);
            }
        } // end if

    }

    /**
     * This can be invoked by a periodic thread to replicate out any changes. For maps that don't store objects that
     * implement ReplicatedMapEntry, this method should be used infrequently to avoid large amounts of data transfer
     *
     * @param complete boolean
     */
    public void replicate(boolean complete) {
        for (Entry<K,MapEntry<K,V>> e : innerMap.entrySet()) {
            replicate(e.getKey(), complete);
        }
    }

    /**
     * Transfers the current state from another map in the cluster.
     */
    public void transferState() {
        try {
            Member[] members = getMapMembers();
            Member backup = members.length > 0 ? members[0] : null;
            if (backup != null) {
                MapMessage msg =
                        new MapMessage(mapContextName, getStateMessageType(), false, null, null, null, null, null);
                Response[] resp = rpcChannel.send(new Member[] { backup }, msg, RpcChannel.FIRST_REPLY,
                        channelSendOptions, rpcTimeout);
                if (resp.length > 0) {
                    synchronized (stateMutex) {
                        msg = (MapMessage) resp[0].getMessage();
                        msg.deserialize(getExternalLoaders());
                        ArrayList<?> list = (ArrayList<?>) msg.getValue();
                        for (Object o : list) {
                            messageReceived((Serializable) o, resp[0].getSource());
                        } // for
                    }
                    stateTransferred = true;
                } else {
                    log.warn(sm.getString("abstractReplicatedMap.transferState.noReplies"));
                }
            }
        } catch (ChannelException | ClassNotFoundException | IOException x) {
            log.error(sm.getString("abstractReplicatedMap.unable.transferState"), x);
        }
        this.state = State.STATETRANSFERRED;
    }

    /**
     * Handles a reply request message.
     *
     * @param msg    The message
     * @param sender The sender
     *
     * @return the reply message or {@code null}
     */
    @Override
    public Serializable replyRequest(Serializable msg, final Member sender) {
        if (!(msg instanceof MapMessage mapmsg)) {
            return null;
        }

        // map init request
        if (mapmsg.getMsgType() == MapMessage.MSG_INIT) {
            mapmsg.setPrimary(channel.getLocalMember(false));
            return mapmsg;
        }

        // map start request
        if (mapmsg.getMsgType() == MapMessage.MSG_START) {
            mapmsg.setPrimary(channel.getLocalMember(false));
            mapMemberAdded(sender);
            return mapmsg;
        }

        // backup request
        if (mapmsg.getMsgType() == MapMessage.MSG_RETRIEVE_BACKUP) {
            MapEntry<K,V> entry = innerMap.get(mapmsg.getKey());
            if (entry == null || (!entry.isSerializable())) {
                return null;
            }
            mapmsg.setValue((Serializable) entry.getValue());
            return mapmsg;
        }

        // state transfer request
        if (mapmsg.getMsgType() == MapMessage.MSG_STATE || mapmsg.getMsgType() == MapMessage.MSG_STATE_COPY) {
            synchronized (stateMutex) { // make sure we don't do two things at the same time
                ArrayList<MapMessage> list = new ArrayList<>();
                for (Entry<K,MapEntry<K,V>> e : innerMap.entrySet()) {
                    MapEntry<K,V> entry = innerMap.get(e.getKey());
                    if (entry != null && entry.isSerializable()) {
                        boolean copy = (mapmsg.getMsgType() == MapMessage.MSG_STATE_COPY);
                        MapMessage me =
                                new MapMessage(mapContextName, copy ? MapMessage.MSG_COPY : MapMessage.MSG_PROXY, false,
                                        (Serializable) entry.getKey(), copy ? (Serializable) entry.getValue() : null,
                                        null, entry.getPrimary(), entry.getBackupNodes());
                        list.add(me);
                    }
                }
                mapmsg.setValue(list);
                return mapmsg;

            } // synchronized
        }

        // ping
        if (mapmsg.getMsgType() == MapMessage.MSG_PING) {
            mapmsg.setValue(state);
            mapmsg.setPrimary(channel.getLocalMember(false));
            return mapmsg;
        }

        return null;

    }

    /**
     * Handles a left over membership message.
     *
     * @param msg    The message
     * @param sender The sender
     */
    @Override
    public void leftOver(Serializable msg, Member sender) {
        // left over membership messages
        if (!(msg instanceof MapMessage mapmsg)) {
            return;
        }

        try {
            mapmsg.deserialize(getExternalLoaders());
            if (mapmsg.getMsgType() == MapMessage.MSG_START) {
                mapMemberAdded(mapmsg.getPrimary());
            } else if (mapmsg.getMsgType() == MapMessage.MSG_INIT) {
                memberAlive(mapmsg.getPrimary());
            } else if (mapmsg.getMsgType() == MapMessage.MSG_PING) {
                Member member = mapmsg.getPrimary();
                if (log.isInfoEnabled()) {
                    log.info(sm.getString("abstractReplicatedMap.leftOver.pingMsg", member));
                }
                State state = (State) mapmsg.getValue();
                if (state.isAvailable()) {
                    memberAlive(member);
                }
            } else {
                // other messages are ignored.
                if (log.isInfoEnabled()) {
                    log.info(sm.getString("abstractReplicatedMap.leftOver.ignored", mapmsg.getTypeDesc()));
                }
            }
        } catch (IOException | ClassNotFoundException x) {
            log.error(sm.getString("abstractReplicatedMap.unable.deserialize.MapMessage"), x);
        }
    }

    /**
     * Handles a received message.
     *
     * @param msg    The message
     * @param sender The sender
     */
    @SuppressWarnings("unchecked")
    @Override
    public void messageReceived(Serializable msg, Member sender) {
        if (!(msg instanceof MapMessage mapmsg)) {
            return;
        }

        if (log.isTraceEnabled()) {
            log.trace("Map[" + mapname + "] received message:" + mapmsg);
        }

        try {
            mapmsg.deserialize(getExternalLoaders());
        } catch (IOException | ClassNotFoundException x) {
            log.error(sm.getString("abstractReplicatedMap.unable.deserialize.MapMessage"), x);
            return;
        }
        if (log.isTraceEnabled()) {
            log.trace("Map message received from:" + sender.getName() + " msg:" + mapmsg);
        }
        if (mapmsg.getMsgType() == MapMessage.MSG_START) {
            mapMemberAdded(mapmsg.getPrimary());
        }

        if (mapmsg.getMsgType() == MapMessage.MSG_STOP) {
            memberDisappeared(mapmsg.getPrimary());
        }

        if (mapmsg.getMsgType() == MapMessage.MSG_PROXY) {
            MapEntry<K,V> entry = innerMap.get(mapmsg.getKey());
            if (entry == null) {
                entry = new MapEntry<>((K) mapmsg.getKey(), (V) mapmsg.getValue());
                MapEntry<K,V> old = innerMap.putIfAbsent(entry.getKey(), entry);
                if (old != null) {
                    entry = old;
                }
            }
            entry.setProxy(true);
            entry.setBackup(false);
            entry.setCopy(false);
            entry.setBackupNodes(mapmsg.getBackupNodes());
            entry.setPrimary(mapmsg.getPrimary());
        }

        if (mapmsg.getMsgType() == MapMessage.MSG_REMOVE) {
            innerMap.remove(mapmsg.getKey());
        }

        if (mapmsg.getMsgType() == MapMessage.MSG_BACKUP || mapmsg.getMsgType() == MapMessage.MSG_COPY) {
            MapEntry<K,V> entry = innerMap.get(mapmsg.getKey());
            if (entry == null) {
                entry = new MapEntry<>((K) mapmsg.getKey(), (V) mapmsg.getValue());
                entry.setBackup(mapmsg.getMsgType() == MapMessage.MSG_BACKUP);
                entry.setProxy(false);
                entry.setCopy(mapmsg.getMsgType() == MapMessage.MSG_COPY);
                entry.setBackupNodes(mapmsg.getBackupNodes());
                entry.setPrimary(mapmsg.getPrimary());
                if (mapmsg.getValue() instanceof ReplicatedMapEntry) {
                    ((ReplicatedMapEntry) mapmsg.getValue()).setOwner(getMapOwner());
                }
            } else {
                entry.setBackup(mapmsg.getMsgType() == MapMessage.MSG_BACKUP);
                entry.setProxy(false);
                entry.setCopy(mapmsg.getMsgType() == MapMessage.MSG_COPY);
                entry.setBackupNodes(mapmsg.getBackupNodes());
                entry.setPrimary(mapmsg.getPrimary());
                if (entry.getValue() instanceof ReplicatedMapEntry diff) {
                    if (mapmsg.isDiff()) {
                        diff.lock();
                        try {
                            diff.applyDiff(mapmsg.getDiffValue(), 0, mapmsg.getDiffValue().length);
                        } catch (Exception e) {
                            log.error(sm.getString("abstractReplicatedMap.unableApply.diff", entry.getKey()), e);
                        } finally {
                            diff.unlock();
                        }
                    } else {
                        if (mapmsg.getValue() != null) {
                            if (mapmsg.getValue() instanceof ReplicatedMapEntry re) {
                                re.setOwner(getMapOwner());
                                entry.setValue((V) re);
                            } else {
                                entry.setValue((V) mapmsg.getValue());
                            }
                        } else {
                            ((ReplicatedMapEntry) entry.getValue()).setOwner(getMapOwner());
                        }
                    } // end if
                } else if (mapmsg.getValue() instanceof ReplicatedMapEntry re) {
                    re.setOwner(getMapOwner());
                    entry.setValue((V) re);
                } else {
                    if (mapmsg.getValue() != null) {
                        entry.setValue((V) mapmsg.getValue());
                    }
                } // end if
            } // end if
            innerMap.put(entry.getKey(), entry);
        } // end if

        if (mapmsg.getMsgType() == MapMessage.MSG_ACCESS) {
            MapEntry<K,V> entry = innerMap.get(mapmsg.getKey());
            if (entry != null) {
                entry.setBackupNodes(mapmsg.getBackupNodes());
                entry.setPrimary(mapmsg.getPrimary());
                if (entry.getValue() instanceof ReplicatedMapEntry) {
                    ((ReplicatedMapEntry) entry.getValue()).accessEntry();
                }
            }
        }

        if (mapmsg.getMsgType() == MapMessage.MSG_NOTIFY_MAPMEMBER) {
            MapEntry<K,V> entry = innerMap.get(mapmsg.getKey());
            if (entry != null) {
                entry.setBackupNodes(mapmsg.getBackupNodes());
                entry.setPrimary(mapmsg.getPrimary());
                if (entry.getValue() instanceof ReplicatedMapEntry) {
                    ((ReplicatedMapEntry) entry.getValue()).accessEntry();
                }
            }
        }
    }

    /**
     * Accepts or rejects a message based on the map context.
     *
     * @param msg    The message
     * @param sender The sender
     *
     * @return {@code true} if the message is accepted
     */
    @Override
    public boolean accept(Serializable msg, Member sender) {
        boolean result = false;
        if (msg instanceof MapMessage) {
            if (log.isTraceEnabled()) {
                log.trace("Map[" + mapname + "] accepting...." + msg);
            }
            result = Arrays.equals(mapContextName, ((MapMessage) msg).getMapId());
            if (log.isTraceEnabled()) {
                log.trace("Msg[" + mapname + "] accepted[" + result + "]...." + msg);
            }
        }
        return result;
    }

    /**
     * Adds a member to this map.
     *
     * @param member The member to add
     */
    public void mapMemberAdded(Member member) {
        if (member.equals(getChannel().getLocalMember(false))) {
            return;
        }
        boolean memberAdded = false;
        // select a backup node if we don't have one
        Member mapMember = getChannel().getMember(member);
        if (mapMember == null) {
            log.warn(sm.getString("abstractReplicatedMap.mapMemberAdded.nullMember", member));
            return;
        }
        synchronized (mapMembers) {
            if (!mapMembers.containsKey(mapMember)) {
                if (log.isInfoEnabled()) {
                    log.info(sm.getString("abstractReplicatedMap.mapMemberAdded.added", mapMember));
                }
                mapMembers.put(mapMember, Long.valueOf(System.currentTimeMillis()));
                memberAdded = true;
            }
        }
        if (memberAdded) {
            synchronized (stateMutex) {
                for (Entry<K,MapEntry<K,V>> e : innerMap.entrySet()) {
                    MapEntry<K,V> entry = innerMap.get(e.getKey());
                    if (entry == null) {
                        continue;
                    }
                    if (entry.isPrimary() && (entry.getBackupNodes() == null || entry.getBackupNodes().length == 0)) {
                        try {
                            Member[] backup = publishEntryInfo(entry.getKey(), entry.getValue());
                            entry.setBackupNodes(backup);
                            entry.setPrimary(channel.getLocalMember(false));
                        } catch (ChannelException x) {
                            log.error(sm.getString("abstractReplicatedMap.unableSelect.backup"), x);
                        } // catch
                    } // end if
                } // while
            } // synchronized
        } // end if
    }

    /**
     * Checks if a member is in the given set.
     *
     * @param m  The member to check
     * @param set The set to check against
     *
     * @return {@code true} if the member is in the set
     */
    public boolean inSet(Member m, Member[] set) {
        if (set == null) {
            return false;
        }
        boolean result = false;
        for (Member member : set) {
            if (m.equals(member)) {
                result = true;
                break;
            }
        }
        return result;
    }

    /**
     * Excludes members from the given set.
     *
     * @param mbrs The members to exclude
     * @param set  The set to exclude from
     *
     * @return The resulting set after exclusion
     */
    public Member[] excludeFromSet(Member[] mbrs, Member[] set) {
        List<Member> result = new ArrayList<>();
        for (Member member : set) {
            boolean include = true;
            for (Member mbr : mbrs) {
                if (mbr.equals(member)) {
                    include = false;
                    break;
                }
            }
            if (include) {
                result.add(member);
            }
        }
        return result.toArray(new Member[0]);
    }

    /**
     * Called when a member is added to the channel.
     *
     * @param member The member that was added
     */
    @Override
    public void memberAdded(Member member) {
        // do nothing
    }

    /**
     * Called when a member disappears from the channel.
     *
     * @param member The member that disappeared
     */
    @Override
    public void memberDisappeared(Member member) {
        synchronized (mapMembers) {
            boolean removed = (mapMembers.remove(member) != null);
            if (!removed) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("replicatedMap.member.disappeared.unknown", member));
                }
                return; // the member was not part of our map.
            }
        }
        if (log.isInfoEnabled()) {
            log.info(sm.getString("replicatedMap.member.disappeared", member));
        }
        long start = System.currentTimeMillis();
        Iterator<Map.Entry<K,MapEntry<K,V>>> i = innerMap.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry<K,MapEntry<K,V>> e = i.next();
            MapEntry<K,V> entry = innerMap.get(e.getKey());
            if (entry == null) {
                continue;
            }
            if (entry.isPrimary() && inSet(member, entry.getBackupNodes())) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("abstractReplicatedMap.newBackup"));
                }
                try {
                    Member[] backup = publishEntryInfo(entry.getKey(), entry.getValue());
                    entry.setBackupNodes(backup);
                    entry.setPrimary(channel.getLocalMember(false));
                } catch (ChannelException x) {
                    log.error(sm.getString("abstractReplicatedMap.unable.relocate", entry.getKey()), x);
                }
            } else if (member.equals(entry.getPrimary())) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("abstractReplicatedMap.primaryDisappeared"));
                }
                entry.setPrimary(null);
            } // end if

            if (entry.isProxy() && entry.getPrimary() == null && entry.getBackupNodes() != null &&
                    entry.getBackupNodes().length == 1 && entry.getBackupNodes()[0].equals(member)) {
                // remove proxies that have no backup nor primaries
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("abstractReplicatedMap.removeOrphan"));
                }
                i.remove();
            } else if (entry.getPrimary() == null && entry.isBackup() && entry.getBackupNodes() != null &&
                    entry.getBackupNodes().length == 1 &&
                    entry.getBackupNodes()[0].equals(channel.getLocalMember(false))) {
                try {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("abstractReplicatedMap.newPrimary"));
                    }
                    entry.setPrimary(channel.getLocalMember(false));
                    entry.setBackup(false);
                    entry.setProxy(false);
                    entry.setCopy(false);
                    Member[] backup = publishEntryInfo(entry.getKey(), entry.getValue());
                    entry.setBackupNodes(backup);
                    if (mapOwner != null) {
                        mapOwner.objectMadePrimary(entry.getKey(), entry.getValue());
                    }

                } catch (ChannelException x) {
                    log.error(sm.getString("abstractReplicatedMap.unable.relocate", entry.getKey()), x);
                }
            }

        } // while
        long complete = System.currentTimeMillis() - start;
        if (log.isInfoEnabled()) {
            log.info(sm.getString("abstractReplicatedMap.relocate.complete", Long.toString(complete)));
        }
    }

    /**
     * Gets the next backup index using round-robin rotation.
     *
     * @return The next backup index, or -1 if no members exist
     */
    public int getNextBackupIndex() {
        synchronized (mapMembers) {
            int size = mapMembers.size();
            if (mapMembers.isEmpty()) {
                return -1;
            }
            int node = currentNode++;
            if (node >= size) {
                node = 0;
                currentNode = 1;
            }
            return node;
        }
    }

    /**
     * Gets the next backup node using round-robin rotation.
     *
     * @return The next backup node, or {@code null} if no members exist
     */
    public Member getNextBackupNode() {
        Member[] members = getMapMembers();
        int node = getNextBackupIndex();
        if (members.length == 0 || node == -1) {
            return null;
        }
        if (node >= members.length) {
            node = 0;
        }
        return members[node];
    }

    /**
     * Publish info about a map pair (key/value) to other nodes in the cluster.
     *
     * @param key   Object
     * @param value Object
     *
     * @return Member - the backup node
     *
     * @throws ChannelException Cluster error
     */
    protected abstract Member[] publishEntryInfo(Object key, Object value) throws ChannelException;

    /**
     * Sends a heartbeat to all members in the cluster.
     */
    @Override
    public void heartbeat() {
        try {
            if (this.state.isAvailable()) {
                ping(accessTimeout);
            }
        } catch (Exception e) {
            log.error(sm.getString("abstractReplicatedMap.heartbeat.failed"), e);
        }
    }

   // ------------------------------------------------------------------------------
    // METHODS TO OVERRIDE
    // ------------------------------------------------------------------------------

    /**
     * Removes the entry for the specified key from the map.
     *
     * @param key The key to remove
     *
     * @return The previous value associated with the key, or {@code null}
     */
    @Override
    public V remove(Object key) {
        return remove(key, true);
    }

    /**
     * Removes the entry for the specified key from the map.
     *
     * @param key    The key to remove
     * @param notify Whether to notify other members
     *
     * @return The previous value associated with the key, or {@code null}
     */
    public V remove(Object key, boolean notify) {
        MapEntry<K,V> entry = innerMap.remove(key);

        try {
            if (getMapMembers().length > 0 && notify) {
                MapMessage msg = new MapMessage(getMapContextName(), MapMessage.MSG_REMOVE, false, (Serializable) key,
                        null, null, null, null);
                getChannel().send(getMapMembers(), msg, getChannelSendOptions());
            }
        } catch (ChannelException x) {
            log.error(sm.getString("abstractReplicatedMap.unable.remove"), x);
        }
        return entry != null ? entry.getValue() : null;
    }

    /**
     * Gets the internal map entry for a key.
     *
     * @param key The key
     *
     * @return the internal map entry, or {@code null} if not found
     */
    public MapEntry<K,V> getInternal(Object key) {
        return innerMap.get(key);
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get(Object key) {
        MapEntry<K,V> entry = innerMap.get(key);
        if (log.isTraceEnabled()) {
            log.trace("Requesting id:" + key + " entry:" + entry);
        }
        if (entry == null) {
            return null;
        }
        if (!entry.isPrimary()) {
            // if the message is not primary, we need to retrieve the latest value
            try {
                Member[] backup = null;
                MapMessage msg;
                if (entry.isBackup()) {
                    // select a new backup node
                    backup = publishEntryInfo(key, entry.getValue());
                } else if (entry.isProxy()) {
                    // make sure we don't retrieve from ourselves
                    msg = new MapMessage(getMapContextName(), MapMessage.MSG_RETRIEVE_BACKUP, false, (Serializable) key,
                            null, null, null, null);
                    Response[] resp = getRpcChannel().send(entry.getBackupNodes(), msg, RpcChannel.FIRST_REPLY,
                            getChannelSendOptions(), getRpcTimeout());
                    if (resp == null || resp.length == 0 || resp[0].getMessage() == null) {
                        // no responses
                        log.warn(sm.getString("abstractReplicatedMap.unable.retrieve", key));
                        return null;
                    }
                    msg = (MapMessage) resp[0].getMessage();
                    msg.deserialize(getExternalLoaders());
                    backup = entry.getBackupNodes();
                    if (msg.getValue() != null) {
                        entry.setValue((V) msg.getValue());
                    }

                    // notify member
                    msg = new MapMessage(getMapContextName(), MapMessage.MSG_NOTIFY_MAPMEMBER, false,
                            (Serializable) entry.getKey(), null, null, channel.getLocalMember(false), backup);
                    if (backup != null && backup.length > 0) {
                        getChannel().send(backup, msg, getChannelSendOptions());
                    }

                    // invalidate the previous primary
                    msg = new MapMessage(getMapContextName(), MapMessage.MSG_PROXY, false, (Serializable) key, null,
                            null, channel.getLocalMember(false), backup);
                    Member[] dest = getMapMembersExcl(backup);
                    if (dest != null && dest.length > 0) {
                        getChannel().send(dest, msg, getChannelSendOptions());
                    }
                    if (entry.getValue() instanceof ReplicatedMapEntry val) {
                        val.setOwner(getMapOwner());
                    }
                } else if (entry.isCopy()) {
                    backup = getMapMembers();
                    if (backup.length > 0) {
                        msg = new MapMessage(getMapContextName(), MapMessage.MSG_NOTIFY_MAPMEMBER, false,
                                (Serializable) key, null, null, channel.getLocalMember(false), backup);
                        getChannel().send(backup, msg, getChannelSendOptions());
                    }
                }
                entry.setPrimary(channel.getLocalMember(false));
                entry.setBackupNodes(backup);
                entry.setBackup(false);
                entry.setProxy(false);
                entry.setCopy(false);
                if (getMapOwner() != null) {
                    getMapOwner().objectMadePrimary(key, entry.getValue());
                }

            } catch (RuntimeException | ChannelException | ClassNotFoundException | IOException x) {
                log.error(sm.getString("abstractReplicatedMap.unable.get"), x);
                return null;
            }
        }
        if (log.isTraceEnabled()) {
            log.trace("Requesting id:" + key + " result:" + entry.getValue());
        }
        return entry.getValue();
    }


    protected void printMap(String header) {
        try {
            System.out.println("\nDEBUG MAP:" + header);
            System.out.println(
                    "Map[" + new String(mapContextName, StandardCharsets.ISO_8859_1) + ", Map Size:" + innerMap.size());
            Member[] mbrs = getMapMembers();
            for (int i = 0; i < mbrs.length; i++) {
                System.out.println("Mbr[" + (i + 1) + "=" + mbrs[i].getName());
            }
            Iterator<Map.Entry<K,MapEntry<K,V>>> i = innerMap.entrySet().iterator();
            int cnt = 0;

            while (i.hasNext()) {
                Map.Entry<K,?> e = i.next();
                System.out.println((++cnt) + ". " + innerMap.get(e.getKey()));
            }
            System.out.println("EndMap]\n\n");
        } catch (Exception e) {
            if (log.isTraceEnabled()) {
                log.trace("Error printing map", e);
            }
        }
    }

    /**
     * Returns true if the key has an entry in the map. The entry can be a proxy or a backup entry, invoking
     * <code>get(key)</code> will make this entry primary for the group
     *
     * @param key Object
     *
     * @return boolean
     */
    @Override
    public boolean containsKey(Object key) {
        return innerMap.containsKey(key);
    }

    /**
     * Puts a key-value pair into the map.
     *
     * @param key   The key
     * @param value The value
     *
     * @return The previous value associated with the key, or {@code null}
     */
    @Override
    public V put(K key, V value) {
        return put(key, value, true);
    }

    /**
     * Puts a key-value pair into the map.
     *
     * @param key    The key
     * @param value  The value
     * @param notify Whether to notify other members
     *
     * @return The previous value associated with the key, or {@code null}
     */
    public V put(K key, V value, boolean notify) {
        MapEntry<K,V> entry = new MapEntry<>(key, value);
        entry.setBackup(false);
        entry.setProxy(false);
        entry.setCopy(false);
        entry.setPrimary(channel.getLocalMember(false));

        V old = null;

        // make sure that any old values get removed
        if (containsKey(key)) {
            old = remove(key);
        }
        try {
            if (notify) {
                Member[] backup = publishEntryInfo(key, value);
                entry.setBackupNodes(backup);
            }
        } catch (ChannelException x) {
            log.error(sm.getString("abstractReplicatedMap.unable.put"), x);
        }
        innerMap.put(key, entry);
        return old;
    }


    /**
     * Copies all mappings from the specified map to this map.
     *
     * @param m The map whose mappings are to be copied
     */
    @Override
    public void putAll(Map<? extends K,? extends V> m) {
        for (Entry<? extends K,? extends V> value : m.entrySet()) {
            @SuppressWarnings("unchecked")
            Entry<K,V> entry = (Entry<K,V>) value;
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        clear(true);
    }

    /**
     * Clears entries from the map.
     *
     * @param notify Whether to notify other members
     */
    public void clear(boolean notify) {
        if (notify) {
            // only delete active keys
            for (K k : keySet()) {
                remove(k);
            }
        } else {
            innerMap.clear();
        }
    }

    @Override
    public boolean containsValue(Object value) {
        Objects.requireNonNull(value);
        for (Entry<K,MapEntry<K,V>> e : innerMap.entrySet()) {
            MapEntry<K,V> entry = innerMap.get(e.getKey());
            if (entry != null && entry.isActive() && value.equals(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the entire contents of the map Map.Entry.getValue() will return a LazyReplicatedMap.MapEntry object
     * containing all the information about the object.
     *
     * @return Set
     */
    public Set<Map.Entry<K,MapEntry<K,V>>> entrySetFull() {
        return innerMap.entrySet();
    }

    /**
     * Gets the complete set of keys in the map.
     *
     * @return The complete set of keys
     */
    public Set<K> keySetFull() {
        return innerMap.keySet();
    }

    /**
     * Gets the complete size of the map.
     *
     * @return The complete size of the map
     */
    public int sizeFull() {
        return innerMap.size();
    }

    /**
     * Returns a set view of the mappings contained in this map.
     *
     * @return a set view of the mappings
     */
    @Override
    public Set<Map.Entry<K,V>> entrySet() {
        LinkedHashSet<Map.Entry<K,V>> set = new LinkedHashSet<>(innerMap.size());
        for (Entry<K,MapEntry<K,V>> e : innerMap.entrySet()) {
            MapEntry<K,V> entry = innerMap.get(e.getKey());
            if (entry != null && entry.isActive()) {
                set.add(entry);
            }
        }
        return Collections.unmodifiableSet(set);
    }

    /**
     * Returns a set view of the keys contained in this map.
     *
     * @return a set view of the keys
     */
    @Override
    public Set<K> keySet() {
        // todo implement
        // should only return keys where this is active.
        LinkedHashSet<K> set = new LinkedHashSet<>(innerMap.size());
        for (Entry<K,MapEntry<K,V>> e : innerMap.entrySet()) {
            K key = e.getKey();
            MapEntry<K,V> entry = innerMap.get(key);
            if (entry != null && entry.isActive()) {
                set.add(key);
            }
        }
        return Collections.unmodifiableSet(set);

    }


    /**
     * Returns the number of active entries in this map.
     *
     * @return the number of active entries
     */
    @Override
    public int size() {
        // todo, implement a counter variable instead
        // only count active members in this node
        int counter = 0;
        for (Entry<K,?> e : innerMap.entrySet()) {
            if (e != null) {
                MapEntry<K,V> entry = innerMap.get(e.getKey());
                if (entry != null && entry.isActive() && entry.getValue() != null) {
                    counter++;
                }
            }
        }
        return counter;
    }

    /**
     * Checks if this map is empty.
     *
     * @return {@code true} if the map is empty
     */
    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Returns a collection view of the values contained in this map.
     *
     * @return a collection view of the values
     */
    @Override
    public Collection<V> values() {
        List<V> values = new ArrayList<>();
        for (Entry<K,MapEntry<K,V>> e : innerMap.entrySet()) {
            MapEntry<K,V> entry = innerMap.get(e.getKey());
            if (entry != null && entry.isActive() && entry.getValue() != null) {
                values.add(entry.getValue());
            }
        }
        return Collections.unmodifiableCollection(values);
    }


    // ------------------------------------------------------------------------------
    // Map Entry class
    // ------------------------------------------------------------------------------
    /**
     * Represents an entry in the replicated map, including metadata about its role (primary, backup, proxy).
     *
     * @param <K> The type of keys maintained by this map
     * @param <V> The type of mapped values
     */
    public static class MapEntry<K, V> implements Map.Entry<K,V> {
        /** Whether this entry is a backup. */
        private boolean backup;
        /** Whether this entry is a proxy. */
        private boolean proxy;
        /** Whether this entry is a copy. */
        private boolean copy;
        /** The backup nodes for this entry. */
        private Member[] backupNodes;
        /** The primary member for this entry. */
        private Member primary;
        /** The key for this entry. */
        private K key;
        /** The value for this entry. */
        private V value;

        /**
         * Creates a new map entry with the specified key and value.
         *
         * @param key   The key
         * @param value The value
         */
        public MapEntry(K key, V value) {
            setKey(key);
            setValue(value);

        }

        /**
         * Checks if the key is serializable.
         *
         * @return {@code true} if the key is serializable or null
         */
        public boolean isKeySerializable() {
            return (key == null) || (key instanceof Serializable);
        }

        /**
         * Checks if the value is serializable.
         *
         * @return {@code true} if the value is serializable or null
         */
        public boolean isValueSerializable() {
            return (value == null) || (value instanceof Serializable);
        }

        /**
         * Checks if both the key and value are serializable.
         *
         * @return {@code true} if both key and value are serializable
         */
        public boolean isSerializable() {
            return isKeySerializable() && isValueSerializable();
        }

        /**
         * Checks if this entry is a backup.
         *
         * @return {@code true} if this entry is a backup
         */
        public boolean isBackup() {
            return backup;
        }

        /**
         * Sets whether this entry is a backup.
         *
         * @param backup {@code true} if this entry is a backup
         */
        public void setBackup(boolean backup) {
            this.backup = backup;
        }

        /**
         * Checks if this entry is a proxy.
         *
         * @return {@code true} if this entry is a proxy
         */
        public boolean isProxy() {
            return proxy;
        }

        /**
         * Checks if this entry is primary.
         *
         * @return {@code true} if this entry is primary
         */
        public boolean isPrimary() {
            return (!proxy && !backup && !copy);
        }

        /**
         * Checks if this entry is active.
         *
         * @return {@code true} if this entry is active
         */
        public boolean isActive() {
            return !proxy;
        }

        /**
         * Sets whether this entry is a proxy.
         *
         * @param proxy {@code true} if this entry is a proxy
         */
        public void setProxy(boolean proxy) {
            this.proxy = proxy;
        }

        /**
         * Checks if this entry is a copy.
         *
         * @return {@code true} if this entry is a copy
         */
        public boolean isCopy() {
            return copy;
        }

        /**
         * Sets whether this entry is a copy.
         *
         * @param copy {@code true} if this entry is a copy
         */
        public void setCopy(boolean copy) {
            this.copy = copy;
        }

        /**
         * Checks if this entry is diffable.
         *
         * @return {@code true} if this entry is diffable
         */
        public boolean isDiffable() {
            return (value instanceof ReplicatedMapEntry) && ((ReplicatedMapEntry) value).isDiffable();
        }

        /**
         * Sets the backup nodes for this entry.
         *
         * @param nodes The backup nodes
         */
        public void setBackupNodes(Member[] nodes) {
            this.backupNodes = nodes;
        }

        /**
         * Gets the backup nodes for this entry.
         *
         * @return The backup nodes
         */
        public Member[] getBackupNodes() {
            return backupNodes;
        }

        /**
         * Sets the primary member for this entry.
         *
         * @param m The primary member
         */
        public void setPrimary(Member m) {
            primary = m;
        }

        /**
         * Gets the primary member for this entry.
         *
         * @return The primary member
         */
        public Member getPrimary() {
            return primary;
        }

        /**
         * Gets the value for this entry.
         *
         * @return The value
         */
        @Override
        public V getValue() {
            return value;
        }

        /**
         * Sets the value for this entry.
         *
         * @param value The new value
         *
         * @return The previous value
         */
        @Override
        public V setValue(V value) {
            V old = this.value;
            this.value = value;
            return old;
        }

        /**
         * Gets the key for this entry.
         *
         * @return The key
         */
        @Override
        public K getKey() {
            return key;
        }

        /**
         * Sets the key for this entry.
         *
         * @param key The new key
         *
         * @return The previous key
         */
        public K setKey(K key) {
            K old = this.key;
            this.key = key;
            return old;
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return key.equals(o);
        }

        /**
         * apply a diff, or an entire object
         *
         * @param data   byte[]
         * @param offset int
         * @param length int
         * @param diff   boolean
         *
         * @throws IOException            IO error
         * @throws ClassNotFoundException Deserialization error
         */
        @SuppressWarnings("unchecked")
        public void apply(byte[] data, int offset, int length, boolean diff)
                throws IOException, ClassNotFoundException {
            if (isDiffable() && diff) {
                ReplicatedMapEntry rentry = (ReplicatedMapEntry) value;
                rentry.lock();
                try {
                    rentry.applyDiff(data, offset, length);
                } finally {
                    rentry.unlock();
                }
            } else if (length == 0) {
                value = null;
                proxy = true;
            } else {
                value = (V) XByteBuffer.deserialize(data, offset, length);
            }
        }

        /**
         * Returns a string representation of this map entry.
         *
         * @return a string representation of this map entry
         */
        @Override
        public String toString() {
            return "MapEntry[key:" + getKey() + "; " + "value:" + getValue() + "; " + "primary:" + isPrimary() + "; " +
                    "backup:" + isBackup() + "; " + "proxy:" + isProxy() + ";]";
        }

    }

    // ------------------------------------------------------------------------------
    // map message to send to and from other maps
    // ------------------------------------------------------------------------------

    /**
     * Represents a message sent between replicated map instances.
     */
    public static class MapMessage implements Serializable, Cloneable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** Message type: backup. */
        public static final int MSG_BACKUP = 1;
        /** Message type: retrieve backup. */
        public static final int MSG_RETRIEVE_BACKUP = 2;
        /** Message type: proxy. */
        public static final int MSG_PROXY = 3;
        /** Message type: remove. */
        public static final int MSG_REMOVE = 4;
        /** Message type: state. */
        public static final int MSG_STATE = 5;
        /** Message type: start. */
        public static final int MSG_START = 6;
        /** Message type: stop. */
        public static final int MSG_STOP = 7;
        /** Message type: init. */
        public static final int MSG_INIT = 8;
        /** Message type: copy. */
        public static final int MSG_COPY = 9;
        /** Message type: state copy. */
        public static final int MSG_STATE_COPY = 10;
        /** Message type: access. */
        public static final int MSG_ACCESS = 11;
        /** Message type: notify map member. */
        public static final int MSG_NOTIFY_MAPMEMBER = 12;
        /** Message type: ping. */
        public static final int MSG_PING = 13;

        /** The map identifier. */
        private final byte[] mapId;
        /** The message type. */
        private final int msgtype;
        /** Whether this is a diff message. */
        private final boolean diff;
        /** The key for this message. */
        private transient Serializable key;
        /** The value for this message. */
        private transient Serializable value;
        /** The serialized value data. */
        private byte[] valuedata;
        /** The serialized key data. */
        private byte[] keydata;
        /** The diff value data. */
        private final byte[] diffvalue;
        /** The backup nodes. */
        private final Member[] nodes;
        /** The primary member. */
        private Member primary;

        /**
         * Returns a string representation of this map message.
         *
         * @return a string representation of this map message
         */
        @Override
        public String toString() {
            return "MapMessage[context=" + new String(mapId) + "; type=" + getTypeDesc() + "; key=" + key + "; value=" +
                    value + ']';
        }

        /**
         * Gets a description of the message type.
         *
         * @return A string description of the message type
         */
        public String getTypeDesc() {
            return switch (msgtype) {
                case MSG_BACKUP -> "MSG_BACKUP";
                case MSG_RETRIEVE_BACKUP -> "MSG_RETRIEVE_BACKUP";
                case MSG_PROXY -> "MSG_PROXY";
                case MSG_REMOVE -> "MSG_REMOVE";
                case MSG_STATE -> "MSG_STATE";
                case MSG_START -> "MSG_START";
                case MSG_STOP -> "MSG_STOP";
                case MSG_INIT -> "MSG_INIT";
                case MSG_STATE_COPY -> "MSG_STATE_COPY";
                case MSG_COPY -> "MSG_COPY";
                case MSG_ACCESS -> "MSG_ACCESS";
                case MSG_NOTIFY_MAPMEMBER -> "MSG_NOTIFY_MAPMEMBER";
                case MSG_PING -> "MSG_PING";
                default -> "UNKNOWN";
            };
        }

        /**
         * Creates a new map message with the specified parameters.
         *
         * @param mapId    The map identifier
         * @param msgtype  The message type
         * @param diff     Whether this is a diff message
         * @param key      The key
         * @param value    The value
         * @param diffvalue The serialized diff value
         * @param primary  The primary member
         * @param nodes    The backup nodes
         */
        public MapMessage(byte[] mapId, int msgtype, boolean diff, Serializable key, Serializable value,
                byte[] diffvalue, Member primary, Member[] nodes) {
            this.mapId = mapId;
            this.msgtype = msgtype;
            this.diff = diff;
            this.key = key;
            this.value = value;
            this.diffvalue = diffvalue;
            this.nodes = nodes;
            this.primary = primary;
            setValue(value);
            setKey(key);
        }

        /**
         * Deserializes the key and value using the given class loaders.
         *
         * @param cls The class loaders to use for deserialization
         *
         * @throws IOException            If deserialization fails
         * @throws ClassNotFoundException If a class is not found
         */
        public void deserialize(ClassLoader[] cls) throws IOException, ClassNotFoundException {
            key(cls);
            value(cls);
        }

        /**
         * Gets the message type.
         *
         * @return the message type
         */
        public int getMsgType() {
            return msgtype;
        }

        /**
         * Checks if this is a diff message.
         *
         * @return {@code true} if this is a diff message
         */
        public boolean isDiff() {
            return diff;
        }

        /**
         * Gets the key for this message.
         *
         * @return The key
         */
        public Serializable getKey() {
            try {
                return key(null);
            } catch (Exception e) {
                throw new RuntimeException(sm.getString("mapMessage.deserialize.error.key"), e);
            }
        }

        /**
         * Deserializes the key using the given class loaders.
         *
         * @param cls The class loaders to use for deserialization
         *
         * @return The deserialized key
         *
         * @throws IOException            If deserialization fails
         * @throws ClassNotFoundException If the key class is not found
         */
        public Serializable key(ClassLoader[] cls) throws IOException, ClassNotFoundException {
            if (key != null) {
                return key;
            }
            if (keydata == null || keydata.length == 0) {
                return null;
            }
            key = XByteBuffer.deserialize(keydata, 0, keydata.length, cls);
            keydata = null;
            return key;
        }

        /**
         * Gets the serialized key data.
         *
         * @return The serialized key data
         */
        public byte[] getKeyData() {
            return keydata;
        }

        /**
         * Gets the value for this message.
         *
         * @return The value
         */
        public Serializable getValue() {
            try {
                return value(null);
            } catch (Exception e) {
                throw new RuntimeException(sm.getString("mapMessage.deserialize.error.value"), e);
            }
        }

        /**
         * Deserializes the value using the given class loaders.
         *
         * @param cls The class loaders to use for deserialization
         *
         * @return The deserialized value
         *
         * @throws IOException            If deserialization fails
         * @throws ClassNotFoundException If the value class is not found
         */
        public Serializable value(ClassLoader[] cls) throws IOException, ClassNotFoundException {
            if (value != null) {
                return value;
            }
            if (valuedata == null || valuedata.length == 0) {
                return null;
            }
            value = XByteBuffer.deserialize(valuedata, 0, valuedata.length, cls);
            valuedata = null;
            return value;
        }

        /**
         * Gets the serialized value data.
         *
         * @return The serialized value data
         */
        public byte[] getValueData() {
            return valuedata;
        }

        /**
         * Gets the diff value data.
         *
         * @return The diff value data
         */
        public byte[] getDiffValue() {
            return diffvalue;
        }

        /**
         * Gets the backup nodes.
         *
         * @return The backup nodes
         */
        public Member[] getBackupNodes() {
            return nodes;
        }

        /**
         * Gets the primary member.
         *
         * @return The primary member
         */
        public Member getPrimary() {
            return primary;
        }

        /**
         * Sets the primary member.
         *
         * @param m The primary member
         */
        private void setPrimary(Member m) {
            primary = m;
        }

        /**
         * Gets the map identifier.
         *
         * @return the map identifier
         */
        public byte[] getMapId() {
            return mapId;
        }

        /**
         * Sets the value for this message.
         *
         * @param value The value
         */
        public void setValue(Serializable value) {
            try {
                if (value != null) {
                    valuedata = XByteBuffer.serialize(value);
                }
                this.value = value;
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        /**
         * Sets the key for this message.
         *
         * @param key The key
         */
        public void setKey(Serializable key) {
            try {
                if (key != null) {
                    keydata = XByteBuffer.serialize(key);
                }
                this.key = key;
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        /**
         * Creates a shallow copy of this map message.
         *
         * @return a shallow copy of this map message
         */
        @Override
        public MapMessage clone() {
            try {
                return (MapMessage) super.clone();
            } catch (CloneNotSupportedException e) {
                // Not possible
                throw new AssertionError();
            }
        }
    } // MapMessage


    /**
     * Gets the channel used for communication.
     *
     * @return the channel
     */
    public Channel getChannel() {
        return channel;
    }

    /**
     * Gets the map context name.
     *
     * @return the map context name as bytes
     */
    public byte[] getMapContextName() {
        return mapContextName;
    }

    /**
     * Gets the RPC channel.
     *
     * @return the RPC channel
     */
    public RpcChannel getRpcChannel() {
        return rpcChannel;
    }

    /**
     * Gets the RPC timeout.
     *
     * @return the RPC timeout in milliseconds
     */
    public long getRpcTimeout() {
        return rpcTimeout;
    }

    /**
     * Gets the state mutex object.
     *
     * @return the state mutex
     */
    public Object getStateMutex() {
        return stateMutex;
    }

    /**
     * Checks if state has been transferred.
     *
     * @return {@code true} if state has been transferred
     */
    public boolean isStateTransferred() {
        return stateTransferred;
    }

    /**
     * Gets the map owner.
     *
     * @return the map owner
     */
    public MapOwner getMapOwner() {
        return mapOwner;
    }

    /**
     * Gets the external class loaders.
     *
     * @return the external class loaders
     */
    public ClassLoader[] getExternalLoaders() {
        return externalLoaders;
    }

    /**
     * Gets the channel send options.
     *
     * @return the channel send options
     */
    public int getChannelSendOptions() {
        return channelSendOptions;
    }

    /**
     * Gets the access timeout.
     *
     * @return the access timeout in milliseconds
     */
    public long getAccessTimeout() {
        return accessTimeout;
    }

    /**
     * Sets the map owner.
     *
     * @param mapOwner The map owner
     */
    public void setMapOwner(MapOwner mapOwner) {
        this.mapOwner = mapOwner;
    }

    /**
     * Sets the external class loaders.
     *
     * @param externalLoaders The external class loaders
     */
    public void setExternalLoaders(ClassLoader[] externalLoaders) {
        this.externalLoaders = externalLoaders;
    }

    /**
     * Sets the channel send options.
     *
     * @param channelSendOptions The channel send options
     */
    public void setChannelSendOptions(int channelSendOptions) {
        this.channelSendOptions = channelSendOptions;
    }

    /**
     * Sets the access timeout.
     *
     * @param accessTimeout The access timeout in milliseconds
     */
    public void setAccessTimeout(long accessTimeout) {
        this.accessTimeout = accessTimeout;
    }

    /**
     * Represents the state of this replicated map.
     */
    private enum State {
        /** The map has been created but not yet initialized. */
        NEW(false),
        /** The map has received state from another map but is not yet ready. */
        STATETRANSFERRED(false),
        /** The map is initialized and ready for messaging. */
        INITIALIZED(true),
        /** The map has been destroyed. */
        DESTROYED(false);

        /** Whether this state accepts messages. */
        private final boolean available;

        /**
         * Creates a new state with the specified availability.
         *
         * @param available whether this state accepts messages
         */
        State(boolean available) {
            this.available = available;
        }

        /**
         * Checks if this state accepts messages.
         *
         * @return {@code true} if this state accepts messages
         */
        public boolean isAvailable() {
            return available;
        }
    }
}
