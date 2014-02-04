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

import java.io.Serializable;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.Member;

/**
 * All-to-all replication for a hash map implementation. Each node in the cluster will carry an identical
 * copy of the map.<br><br>
 * This map implementation doesn't have a background thread running to replicate changes.
 * If you do have changes without invoking put/remove then you need to invoke one of the following methods:
 * <ul>
 * <li><code>replicate(Object,boolean)</code> - replicates only the object that belongs to the key</li>
 * <li><code>replicate(boolean)</code> - Scans the entire map for changes and replicates data</li>
 *  </ul>
 * the <code>boolean</code> value in the <code>replicate</code> method used to decide
 * whether to only replicate objects that implement the <code>ReplicatedMapEntry</code> interface
 * or to replicate all objects. If an object doesn't implement the <code>ReplicatedMapEntry</code> interface
 * each time the object gets replicated the entire object gets serialized, hence a call to <code>replicate(true)</code>
 * will replicate all objects in this map that are using this node as primary.
 *
 * <br><br><b>REMEMBER TO CALL <code>breakdown()</code> or <code>finalize()</code>
 * when you are done with the map to avoid memory leaks.</b><br><br>
 * TODO implement periodic sync/transfer thread<br>
 * TODO memberDisappeared, should do nothing except change map membership
 *       by default it relocates the primary objects
 *
 * @version 1.0
 */
public class ReplicatedMap<K,V> extends AbstractReplicatedMap<K,V> {

    private static final long serialVersionUID = 1L;

    //--------------------------------------------------------------------------
    //              CONSTRUCTORS / DESTRUCTORS
    //--------------------------------------------------------------------------
    /**
     * Creates a new map
     * @param channel The channel to use for communication
     * @param timeout long - timeout for RPC messags
     * @param mapContextName String - unique name for this map, to allow multiple maps per channel
     * @param initialCapacity int - the size of this map, see HashMap
     * @param loadFactor float - load factor, see HashMap
     */
    public ReplicatedMap(MapOwner owner, Channel channel, long timeout, String mapContextName, int initialCapacity,float loadFactor, ClassLoader[] cls) {
        super(owner,channel, timeout, mapContextName, initialCapacity, loadFactor, Channel.SEND_OPTIONS_DEFAULT, cls, true);
    }

    /**
     * Creates a new map
     * @param channel The channel to use for communication
     * @param timeout long - timeout for RPC messags
     * @param mapContextName String - unique name for this map, to allow multiple maps per channel
     * @param initialCapacity int - the size of this map, see HashMap
     */
    public ReplicatedMap(MapOwner owner, Channel channel, long timeout, String mapContextName, int initialCapacity, ClassLoader[] cls) {
        super(owner,channel, timeout, mapContextName, initialCapacity, AbstractReplicatedMap.DEFAULT_LOAD_FACTOR,Channel.SEND_OPTIONS_DEFAULT, cls, true);
    }

    /**
     * Creates a new map
     * @param channel The channel to use for communication
     * @param timeout long - timeout for RPC messags
     * @param mapContextName String - unique name for this map, to allow multiple maps per channel
     */
    public ReplicatedMap(MapOwner owner, Channel channel, long timeout, String mapContextName, ClassLoader[] cls) {
        super(owner, channel, timeout, mapContextName,AbstractReplicatedMap.DEFAULT_INITIAL_CAPACITY, AbstractReplicatedMap.DEFAULT_LOAD_FACTOR, Channel.SEND_OPTIONS_DEFAULT, cls, true);
    }

    /**
     * Creates a new map
     * @param channel The channel to use for communication
     * @param timeout long - timeout for RPC messags
     * @param mapContextName String - unique name for this map, to allow multiple maps per channel
     * @param terminate boolean - Flag for whether to terminate this map that failed to start.
     */
    public ReplicatedMap(MapOwner owner, Channel channel, long timeout, String mapContextName, ClassLoader[] cls, boolean terminate) {
        super(owner, channel, timeout, mapContextName,AbstractReplicatedMap.DEFAULT_INITIAL_CAPACITY,
                AbstractReplicatedMap.DEFAULT_LOAD_FACTOR, Channel.SEND_OPTIONS_DEFAULT, cls, terminate);
    }

//------------------------------------------------------------------------------
//              METHODS TO OVERRIDE
//------------------------------------------------------------------------------
    @Override
    protected int getStateMessageType() {
        return AbstractReplicatedMap.MapMessage.MSG_STATE_COPY;
    }

    /**
     * publish info about a map pair (key/value) to other nodes in the cluster
     * @param key Object
     * @param value Object
     * @return Member - the backup node
     * @throws ChannelException
     */
    @Override
    protected Member[] publishEntryInfo(Object key, Object value) throws ChannelException {
        if  (! (key instanceof Serializable && value instanceof Serializable)  ) return new Member[0];
        //select a backup node
        Member[] backup = getMapMembers();

        if (backup == null || backup.length == 0) return null;

        //publish the data out to all nodes
        MapMessage msg = new MapMessage(getMapContextName(), MapMessage.MSG_COPY, false,
                                        (Serializable) key, (Serializable) value, null,channel.getLocalMember(false), backup);

        getChannel().send(getMapMembers(), msg, getChannelSendOptions());

        return backup;
    }

}