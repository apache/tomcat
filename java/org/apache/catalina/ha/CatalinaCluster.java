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
    public void send(ClusterMessage msg);

    /**
     * Sends a message to a specific member in the cluster.
     *
     * @param msg ClusterMessage
     * @param dest Member
     */
    public void send(ClusterMessage msg, Member dest);

    /**
     * Sends a message with the specified sendOptions to a specific member in the cluster.
     *
     * @param msg ClusterMessage
     * @param dest Member
     * @param sendOptions sendOptions
     */
    public void send(ClusterMessage msg, Member dest, int sendOptions);

    /**
     * @return <code>true</code> if the cluster has members.
     */
    public boolean hasMembers();

    /**
     * @return an array containing all the members currently participating in the cluster.
     */
    public Member[] getMembers();

    /**
     * @return the member that represents this node.
     */
    public Member getLocalMember();

    public void addValve(Valve valve);

    public void addClusterListener(ClusterListener listener);

    public void removeClusterListener(ClusterListener listener);

    public void setClusterDeployer(ClusterDeployer deployer);

    public ClusterDeployer getClusterDeployer();

    /**
     * @return The map of managers
     */
    public Map<String,ClusterManager> getManagers();

    /**
     * Get Manager
     * @param name The manager name
     * @return The manager
     */
    public Manager getManager(String name);

    /**
     * Get a new cluster name for a manager.
     * @param name Override name (optional)
     * @param manager The manager
     * @return the manager name in the cluster
     */
    public String getManagerName(String name, Manager manager);

    public Valve[] getValves();

    public void setChannel(Channel channel);

    public Channel getChannel();


}
