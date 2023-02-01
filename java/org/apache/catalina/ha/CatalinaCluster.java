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

import java.util.Map;

import org.apache.catalina.Cluster;
import org.apache.catalina.Manager;
import org.apache.catalina.Valve;
import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.Member;


/**
 * A <b>CatalinaCluster</b> interface allows to plug in and out the
 * different cluster implementations
 */
public interface CatalinaCluster extends Cluster {
    // ----------------------------------------------------- Instance Variables

    /**
     * Sends a message to all the members in the cluster
     * @param msg ClusterMessage
     */
    void send(ClusterMessage msg);

    /**
     * Sends a message to a specific member in the cluster.
     *
     * @param msg ClusterMessage
     * @param dest Member
     */
    void send(ClusterMessage msg, Member dest);

    /**
     * Sends a message with the specified sendOptions to a specific member in the cluster.
     *
     * @param msg ClusterMessage
     * @param dest Member
     * @param sendOptions sendOptions
     */
    void send(ClusterMessage msg, Member dest, int sendOptions);

    /**
     * @return <code>true</code> if the cluster has members.
     */
    boolean hasMembers();

    /**
     * @return an array containing all the members currently participating in the cluster.
     */
    Member[] getMembers();

    /**
     * @return the member that represents this node.
     */
    Member getLocalMember();

    void addValve(Valve valve);

    void addClusterListener(ClusterListener listener);

    void removeClusterListener(ClusterListener listener);

    void setClusterDeployer(ClusterDeployer deployer);

    ClusterDeployer getClusterDeployer();

    /**
     * @return The map of managers
     */
    Map<String,ClusterManager> getManagers();

    /**
     * Get Manager
     * @param name The manager name
     * @return The manager
     */
    Manager getManager(String name);

    /**
     * Get a new cluster name for a manager.
     * @param name Override name (optional)
     * @param manager The manager
     * @return the manager name in the cluster
     */
    String getManagerName(String name, Manager manager);

    Valve[] getValves();

    void setChannel(Channel channel);

    Channel getChannel();


}
