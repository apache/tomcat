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
package org.apache.catalina.ha.session;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Session;
import org.apache.catalina.ha.CatalinaCluster;
import org.apache.catalina.ha.ClusterManager;
import org.apache.catalina.ha.ClusterMessage;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.io.ReplicationStream;
import org.apache.catalina.tribes.tipis.LazyReplicatedMap;
import org.apache.catalina.tribes.tipis.AbstractReplicatedMap.MapOwner;
import org.apache.catalina.util.LifecycleBase;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 *@author Filip Hanik
 *@version 1.0
 */
public class BackupManager extends StandardManager implements ClusterManager, MapOwner
{
    private static final Log log = LogFactory.getLog( BackupManager.class );

    protected static long DEFAULT_REPL_TIMEOUT = 15000;//15 seconds

    /** Set to true if we don't want the sessions to expire on shutdown */
    protected boolean mExpireSessionsOnShutdown = true;
    
    /**
     * The name of this manager
     */
    protected String name;

    /**
     * A reference to the cluster
     */
    protected CatalinaCluster cluster;
    
    /**
     * Should listeners be notified?
     */
    private boolean notifyListenersOnReplication;
    /**
     * 
     */
    private int mapSendOptions = Channel.SEND_OPTIONS_SYNCHRONIZED_ACK|Channel.SEND_OPTIONS_USE_ACK;

    /**
     * Constructor, just calls super()
     *
     */
    public BackupManager() {
        super();
    }


//******************************************************************************/
//      ClusterManager Interface     
//******************************************************************************/

    public void messageDataReceived(ClusterMessage msg) {
    }

    public boolean doDomainReplication() {
        return false;
    }

    /**
     * @param sendClusterDomainOnly The sendClusterDomainOnly to set.
     */
    public void setDomainReplication(boolean sendClusterDomainOnly) {
    }

    /**
     * @return Returns the defaultMode.
     */
    public boolean isDefaultMode() {
        return false;
    }
    /**
     * @param defaultMode The defaultMode to set.
     */
    public void setDefaultMode(boolean defaultMode) {
    }

    public void setExpireSessionsOnShutdown(boolean expireSessionsOnShutdown)
    {
        mExpireSessionsOnShutdown = expireSessionsOnShutdown;
    }

    public void setCluster(CatalinaCluster cluster) {
        if(log.isDebugEnabled())
            log.debug("Cluster associated with BackupManager");
        this.cluster = cluster;
    }

    public boolean getExpireSessionsOnShutdown()
    {
        return mExpireSessionsOnShutdown;
    }


    /**
     * Override persistence since they don't go hand in hand with replication for now.
     */
    @Override
    public void unload() throws IOException {
    }
    
    public ClusterMessage requestCompleted(String sessionId) {
        if (!getState().isAvailable()) return null;
        LazyReplicatedMap map = (LazyReplicatedMap)sessions;
        map.replicate(sessionId,false);
        return null;
    }


//=========================================================================
// OVERRIDE THESE METHODS TO IMPLEMENT THE REPLICATION
//=========================================================================
    public void objectMadePrimay(Object key, Object value) {
        if (value!=null && value instanceof DeltaSession) {
            DeltaSession session = (DeltaSession)value;
            synchronized (session) {
                session.access();
                session.endAccess();
            }
        }
    }

    @Override
    public Session createEmptySession() {
        return new DeltaSession(this);
    }
    
    public ClassLoader[] getClassLoaders() {
        return ClusterManagerBase.getClassLoaders(this.container);
    }

    /**
     * Open Stream and use correct ClassLoader (Container) Switch
     * ThreadClassLoader
     * 
     * @param data
     * @return The object input stream
     * @throws IOException
     */
    public ReplicationStream getReplicationStream(byte[] data) throws IOException {
        return getReplicationStream(data,0,data.length);
    }

    public ReplicationStream getReplicationStream(byte[] data, int offset, int length) throws IOException {
        ByteArrayInputStream fis = new ByteArrayInputStream(data, offset, length);
        return new ReplicationStream(fis, getClassLoaders());
    }    




    @Override
    public String getName() {
        return this.name;
    }


    /**
     * Start this component and implement the requirements
     * of {@link LifecycleBase#startInternal()}.
     *
     * Starts the cluster communication channel, this will connect with the
     * other nodes in the cluster, and request the current session state to be
     * transferred to this node.
     * 
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {
        
        // Force initialization of the random number generator
        generateSessionId();

        try {
            cluster.registerManager(this);
            CatalinaCluster catclust = cluster;
            LazyReplicatedMap map = new LazyReplicatedMap(this,
                                                          catclust.getChannel(),
                                                          DEFAULT_REPL_TIMEOUT,
                                                          getMapName(),
                                                          getClassLoaders());
            map.setChannelSendOptions(mapSendOptions);
            this.sessions = map;
        }  catch ( Exception x ) {
            log.error("Unable to start BackupManager",x);
            throw new LifecycleException("Failed to start BackupManager",x);
        }
        setState(LifecycleState.STARTING);
    }
    
    public String getMapName() {
        CatalinaCluster catclust = cluster;
        String name = catclust.getManagerName(getName(),this)+"-"+"map";
        if ( log.isDebugEnabled() ) log.debug("Backup manager, Setting map name to:"+name);
        return name;
    }


    /**
     * Stop this component and implement the requirements
     * of {@link LifecycleBase#stopInternal()}.
     * 
     * This will disconnect the cluster communication channel and stop the
     * listener thread.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        if (log.isDebugEnabled())
            log.debug("Stopping");

        setState(LifecycleState.STOPPING);

        if (sessions instanceof LazyReplicatedMap) {
            LazyReplicatedMap map = (LazyReplicatedMap)sessions;
            map.breakdown();
        }

        cluster.removeManager(this);
        this.random = null;
    }

    @Override
    public void setDistributable(boolean dist) {
        this.distributable = dist;
    }

    @Override
    public boolean getDistributable() {
        return distributable;
    }

    public void setName(String name) {
        this.name = name;
    }
    public boolean isNotifyListenersOnReplication() {
        return notifyListenersOnReplication;
    }
    public void setNotifyListenersOnReplication(boolean notifyListenersOnReplication) {
        this.notifyListenersOnReplication = notifyListenersOnReplication;
    }

    public void setMapSendOptions(int mapSendOptions) {
        this.mapSendOptions = mapSendOptions;
    }

    /* 
     * @see org.apache.catalina.ha.ClusterManager#getCluster()
     */
    public CatalinaCluster getCluster() {
        return cluster;
    }

    public int getMapSendOptions() {
        return mapSendOptions;
    }

    public String[] getInvalidatedSessions() {
        return new String[0];
    }
    
    public ClusterManager cloneFromTemplate() {
        BackupManager result = new BackupManager();
        result.mExpireSessionsOnShutdown = mExpireSessionsOnShutdown;
        result.name = "Clone-from-"+name;
        result.cluster = cluster;
        result.notifyListenersOnReplication = notifyListenersOnReplication;
        result.mapSendOptions = mapSendOptions;
        return result;
    }

    public int getActiveSessionsFull() {
        LazyReplicatedMap map = (LazyReplicatedMap)sessions;
        return map.sizeFull();
    }

    public String listSessionIdsFull() {
        StringBuilder sb=new StringBuilder();
        LazyReplicatedMap map = (LazyReplicatedMap)sessions;
        @SuppressWarnings("unchecked") // sessions is of type Map<String, Session>
        Iterator<String> keys = map.keySetFull().iterator();
        while (keys.hasNext()) {
            sb.append(keys.next()).append(" ");
        }
        return sb.toString();
    }
}
