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

import java.util.HashSet;
import java.util.Set;

import org.apache.catalina.DistributedManager;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Session;
import org.apache.catalina.ha.ClusterManager;
import org.apache.catalina.ha.ClusterMessage;
import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.tipis.AbstractReplicatedMap.MapOwner;
import org.apache.catalina.tribes.tipis.LazyReplicatedMap;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

public class BackupManager extends ClusterManagerBase implements MapOwner, DistributedManager {

    private final Log log = LogFactory.getLog(BackupManager.class); // must not be static

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(BackupManager.class);

    protected static final long DEFAULT_REPL_TIMEOUT = 15000;// 15 seconds

    /**
     * The name of this manager
     */
    protected String name;

    /**
     * Flag for how this map sends messages.
     */
    private int mapSendOptions = Channel.SEND_OPTIONS_SYNCHRONIZED_ACK | Channel.SEND_OPTIONS_USE_ACK;

    /**
     * Timeout for RPC messages.
     */
    private long rpcTimeout = DEFAULT_REPL_TIMEOUT;

    /**
     * Flag for whether to terminate this map that failed to start.
     */
    private boolean terminateOnStartFailure = false;

    /**
     * The timeout for a ping message in replication map.
     */
    private long accessTimeout = 5000;

    /**
     * Constructor, just calls super()
     */
    public BackupManager() {
        super();
    }


    // ******************************************************************************/
    // ClusterManager Interface
    // ******************************************************************************/

    @Override
    public void messageDataReceived(ClusterMessage msg) {
    }

    @Override
    public ClusterMessage requestCompleted(String sessionId) {
        if (!getState().isAvailable()) {
            return null;
        }
        LazyReplicatedMap<String,Session> map = (LazyReplicatedMap<String,Session>) sessions;
        map.replicate(sessionId, false);
        return null;
    }


    // =========================================================================
    // OVERRIDE THESE METHODS TO IMPLEMENT THE REPLICATION
    // =========================================================================
    @Override
    public void objectMadePrimary(Object key, Object value) {
        if (value instanceof DeltaSession) {
            DeltaSession session = (DeltaSession) value;
            synchronized (session) {
                session.access();
                session.setPrimarySession(true);
                session.endAccess();
            }
        }
    }

    @Override
    public Session createEmptySession() {
        return new DeltaSession(this);
    }


    @Override
    public String getName() {
        return this.name;
    }


    /**
     * Start this component and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#startInternal()}. Starts the cluster communication channel, this
     * will connect with the other nodes in the cluster, and request the current session state to be transferred to this
     * node.
     *
     * @exception LifecycleException if this component detects a fatal error that prevents this component from being
     *                                   used
     */
    @Override
    protected void startInternal() throws LifecycleException {

        super.startInternal();

        try {
            if (cluster == null) {
                throw new LifecycleException(sm.getString("backupManager.noCluster", getName()));
            }
            LazyReplicatedMap<String,Session> map = new LazyReplicatedMap<>(this, cluster.getChannel(), rpcTimeout,
                    getMapName(), getClassLoaders(), terminateOnStartFailure);
            map.setChannelSendOptions(mapSendOptions);
            map.setAccessTimeout(accessTimeout);
            this.sessions = map;
        } catch (Exception x) {
            log.error(sm.getString("backupManager.startUnable", getName()), x);
            throw new LifecycleException(sm.getString("backupManager.startFailed", getName()), x);
        }
        setState(LifecycleState.STARTING);
    }

    public String getMapName() {
        String name = cluster.getManagerName(getName(), this) + "-" + "map";
        if (log.isTraceEnabled()) {
            log.trace("Backup manager, Setting map name to:" + name);
        }
        return name;
    }


    /**
     * Stop this component and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#stopInternal()}. This will disconnect the cluster communication
     * channel and stop the listener thread.
     *
     * @exception LifecycleException if this component detects a fatal error that prevents this component from being
     *                                   used
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("backupManager.stopped", getName()));
        }

        setState(LifecycleState.STOPPING);

        if (sessions instanceof LazyReplicatedMap) {
            LazyReplicatedMap<String,Session> map = (LazyReplicatedMap<String,Session>) sessions;
            map.breakdown();
        }

        super.stopInternal();
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    public void setMapSendOptions(int mapSendOptions) {
        this.mapSendOptions = mapSendOptions;
    }

    public void setMapSendOptions(String mapSendOptions) {

        int value = Channel.parseSendOptions(mapSendOptions);
        if (value > 0) {
            this.setMapSendOptions(value);
        }
    }

    public int getMapSendOptions() {
        return mapSendOptions;
    }

    /**
     * returns the SendOptions as a comma separated list of names
     *
     * @return a comma separated list of the option names
     */
    public String getMapSendOptionsName() {
        return Channel.getSendOptionsAsString(mapSendOptions);
    }

    public void setRpcTimeout(long rpcTimeout) {
        this.rpcTimeout = rpcTimeout;
    }

    public long getRpcTimeout() {
        return rpcTimeout;
    }

    public void setTerminateOnStartFailure(boolean terminateOnStartFailure) {
        this.terminateOnStartFailure = terminateOnStartFailure;
    }

    public boolean isTerminateOnStartFailure() {
        return terminateOnStartFailure;
    }

    public long getAccessTimeout() {
        return accessTimeout;
    }

    public void setAccessTimeout(long accessTimeout) {
        this.accessTimeout = accessTimeout;
    }

    @Override
    public String[] getInvalidatedSessions() {
        return new String[0];
    }

    @Override
    public ClusterManager cloneFromTemplate() {
        BackupManager result = new BackupManager();
        clone(result);
        result.mapSendOptions = mapSendOptions;
        result.rpcTimeout = rpcTimeout;
        result.terminateOnStartFailure = terminateOnStartFailure;
        result.accessTimeout = accessTimeout;
        return result;
    }

    @Override
    public int getActiveSessionsFull() {
        LazyReplicatedMap<String,Session> map = (LazyReplicatedMap<String,Session>) sessions;
        return map.sizeFull();
    }

    @Override
    public Set<String> getSessionIdsFull() {
        LazyReplicatedMap<String,Session> map = (LazyReplicatedMap<String,Session>) sessions;
        Set<String> sessionIds = new HashSet<>(map.keySetFull());
        return sessionIds;
    }

}
