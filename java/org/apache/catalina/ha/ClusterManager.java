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
package org.apache.catalina.ha;


import java.io.IOException;

import org.apache.catalina.Manager;
import org.apache.catalina.tribes.io.ReplicationStream;


/**
 * The common interface used by all cluster manager. This is so that we can have a more pluggable way of swapping
 * session managers for different algorithms.
 *
 * @author Peter Rossbach
 */
public interface ClusterManager extends Manager {

    /**
     * A message was received from another node, this is the callback method to implement if you are interested in
     * receiving replication messages.
     *
     * @param msg - the message received.
     */
    void messageDataReceived(ClusterMessage msg);

    /**
     * When the request has been completed, the replication valve will notify the manager, and the manager will decide
     * whether any replication is needed or not. If there is a need for replication, the manager will create a session
     * message and that will be replicated. The cluster determines where it gets sent.
     *
     * @param sessionId - the sessionId that just completed.
     *
     * @return a SessionMessage to be sent.
     */
    ClusterMessage requestCompleted(String sessionId);

    /**
     * When the manager expires session not tied to a request. The cluster will periodically ask for a list of sessions
     * that should expire and that should be sent across the wire.
     *
     * @return String[] The invalidated sessions
     */
    String[] getInvalidatedSessions();

    /**
     * Return the name of the manager, at host /context name and at engine hostname+/context.
     *
     * @return String
     *
     * @since 5.5.10
     */
    String getName();

    /**
     * Set the name of the manager, at host /context name and at engine hostname+/context
     *
     * @param name The manager name
     *
     * @since 5.5.10
     */
    void setName(String name);

    /**
     * @return the cluster associated with this manager
     */
    CatalinaCluster getCluster();

    /**
     * Set the cluster associated with this manager.
     *
     * @param cluster the cluster
     */
    void setCluster(CatalinaCluster cluster);

    /**
     * Open stream and use correct ClassLoader (Container), switching thread context class loader.
     *
     * @param data the data
     *
     * @return the object input stream
     *
     * @throws IOException An error occurred
     */
    ReplicationStream getReplicationStream(byte[] data) throws IOException;

    /**
     * Open stream and use correct ClassLoader (Container), switching thread context class loader.
     *
     * @param data   the data
     * @param offset the offset in the data array
     * @param length the data length
     *
     * @return the object input stream
     *
     * @throws IOException An error occurred
     */
    ReplicationStream getReplicationStream(byte[] data, int offset, int length) throws IOException;

    /**
     * @return {@code true} if listeners are notified on replication
     */
    boolean isNotifyListenersOnReplication();

    /**
     * @return a clone of a template manager configuration
     */
    ClusterManager cloneFromTemplate();
}
