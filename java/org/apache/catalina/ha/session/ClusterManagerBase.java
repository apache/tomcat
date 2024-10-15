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

import org.apache.catalina.Cluster;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Loader;
import org.apache.catalina.SessionIdGenerator;
import org.apache.catalina.Valve;
import org.apache.catalina.ha.CatalinaCluster;
import org.apache.catalina.ha.ClusterManager;
import org.apache.catalina.ha.tcp.ReplicationValve;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.tribes.io.ReplicationStream;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.collections.SynchronizedStack;
import org.apache.tomcat.util.res.StringManager;

public abstract class ClusterManagerBase extends ManagerBase implements ClusterManager {

    protected static final StringManager sm = StringManager.getManager(ClusterManagerBase.class);
    private final Log log = LogFactory.getLog(ClusterManagerBase.class); // must not be static

    /**
     * A reference to the cluster
     */
    protected CatalinaCluster cluster = null;

    /**
     * Should listeners be notified?
     */
    private boolean notifyListenersOnReplication = true;

    /**
     * cached replication valve cluster container!
     */
    private volatile ReplicationValve replicationValve = null;

    /**
     * send all actions of session attributes.
     */
    private boolean recordAllActions = false;

    private SynchronizedStack<DeltaRequest> deltaRequestPool = new SynchronizedStack<>();


    protected SynchronizedStack<DeltaRequest> getDeltaRequestPool() {
        return deltaRequestPool;
    }


    @Override
    public CatalinaCluster getCluster() {
        return cluster;
    }

    @Override
    public void setCluster(CatalinaCluster cluster) {
        this.cluster = cluster;
    }

    @Override
    public boolean isNotifyListenersOnReplication() {
        return notifyListenersOnReplication;
    }

    public void setNotifyListenersOnReplication(boolean notifyListenersOnReplication) {
        this.notifyListenersOnReplication = notifyListenersOnReplication;
    }


    public boolean isRecordAllActions() {
        return recordAllActions;
    }

    public void setRecordAllActions(boolean recordAllActions) {
        this.recordAllActions = recordAllActions;
    }


    public static ClassLoader[] getClassLoaders(Context context) {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        Loader loader = context.getLoader();
        ClassLoader classLoader = null;
        if (loader != null) {
            classLoader = loader.getClassLoader();
        }
        if (classLoader == null) {
            classLoader = tccl;
        }
        if (classLoader == tccl) {
            return new ClassLoader[] { classLoader };
        } else {
            return new ClassLoader[] { classLoader, tccl };
        }
    }


    public ClassLoader[] getClassLoaders() {
        return getClassLoaders(getContext());
    }

    @Override
    public ReplicationStream getReplicationStream(byte[] data) throws IOException {
        return getReplicationStream(data, 0, data.length);
    }

    @Override
    public ReplicationStream getReplicationStream(byte[] data, int offset, int length) throws IOException {
        ByteArrayInputStream fis = new ByteArrayInputStream(data, offset, length);
        return new ReplicationStream(fis, getClassLoaders());
    }


    // ---------------------------------------------------- persistence handler

    /**
     * {@link org.apache.catalina.Manager} implementations that also implement {@link ClusterManager} do not support
     * local session persistence.
     */
    @Override
    public void load() {
        // NOOP
    }

    /**
     * {@link org.apache.catalina.Manager} implementations that also implement {@link ClusterManager} do not support
     * local session persistence.
     */
    @Override
    public void unload() {
        // NOOP
    }

    protected void clone(ClusterManagerBase copy) {
        copy.setName("Clone-from-" + getName());
        copy.setMaxActiveSessions(getMaxActiveSessions());
        copy.setProcessExpiresFrequency(getProcessExpiresFrequency());
        copy.setNotifyListenersOnReplication(isNotifyListenersOnReplication());
        copy.setSessionAttributeNameFilter(getSessionAttributeNameFilter());
        copy.setSessionAttributeValueClassNameFilter(getSessionAttributeValueClassNameFilter());
        copy.setWarnOnSessionAttributeFilterFailure(getWarnOnSessionAttributeFilterFailure());
        copy.setSecureRandomClass(getSecureRandomClass());
        copy.setSecureRandomProvider(getSecureRandomProvider());
        copy.setSecureRandomAlgorithm(getSecureRandomAlgorithm());
        if (getSessionIdGenerator() != null) {
            try {
                SessionIdGenerator copyIdGenerator = sessionIdGeneratorClass.getConstructor().newInstance();
                copyIdGenerator.setSessionIdLength(getSessionIdGenerator().getSessionIdLength());
                copyIdGenerator.setJvmRoute(getSessionIdGenerator().getJvmRoute());
                copy.setSessionIdGenerator(copyIdGenerator);
            } catch (ReflectiveOperationException e) {
                // Ignore
            }
        }
        copy.setRecordAllActions(isRecordAllActions());
    }

    /**
     * Register cross context session at replication valve thread local
     *
     * @param session cross context session
     */
    protected void registerSessionAtReplicationValve(DeltaSession session) {
        if (replicationValve == null) {
            CatalinaCluster cluster = getCluster();
            if (cluster != null) {
                Valve[] valves = cluster.getValves();
                if (valves != null && valves.length > 0) {
                    for (int i = 0; replicationValve == null && i < valves.length; i++) {
                        if (valves[i] instanceof ReplicationValve) {
                            replicationValve = (ReplicationValve) valves[i];
                        }
                    } // for

                    if (replicationValve == null && log.isDebugEnabled()) {
                        log.debug(sm.getString("clusterManager.noValve"));
                    } // endif
                } // end if
            } // endif
        } // end if
        if (replicationValve != null) {
            replicationValve.registerReplicationSession(session);
        }
    }

    @Override
    protected void startInternal() throws LifecycleException {
        super.startInternal();
        if (getCluster() == null) {
            Cluster cluster = getContext().getCluster();
            if (cluster instanceof CatalinaCluster) {
                setCluster((CatalinaCluster) cluster);
            }
        }
        if (cluster != null) {
            cluster.registerManager(this);
        }
    }

    @Override
    protected void stopInternal() throws LifecycleException {
        if (cluster != null) {
            cluster.removeManager(this);
        }
        replicationValve = null;
        super.stopInternal();
    }
}
