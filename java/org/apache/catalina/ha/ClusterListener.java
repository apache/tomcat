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

import java.io.Serializable;

import org.apache.catalina.tribes.ChannelListener;
import org.apache.catalina.tribes.Member;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


/**
 * Receive SessionID cluster change from other backup node after primary session node is failed.
 *
 * @author Peter Rossbach
 */
public abstract class ClusterListener implements ChannelListener {

    private static final Log log = LogFactory.getLog(ClusterListener.class);

    // --Instance Variables--------------------------------------

    protected CatalinaCluster cluster = null;

    // --Constructor---------------------------------------------

    public ClusterListener() {
        // NO-OP
    }

    // --Instance Getters/Setters--------------------------------

    public CatalinaCluster getCluster() {
        return cluster;
    }

    public void setCluster(CatalinaCluster cluster) {
        if (log.isTraceEnabled()) {
            if (cluster != null) {
                log.trace("add ClusterListener " + this.toString() + " to cluster" + cluster);
            } else {
                log.trace("remove ClusterListener " + this.toString() + " from cluster");
            }
        }
        this.cluster = cluster;
    }

    // --Logic---------------------------------------------------

    @Override
    public final void messageReceived(Serializable msg, Member member) {
        if (msg instanceof ClusterMessage) {
            messageReceived((ClusterMessage) msg);
        }
    }

    @Override
    public final boolean accept(Serializable msg, Member member) {
        if (msg instanceof ClusterMessage) {
            return true;
        }
        return false;
    }


    /**
     * Callback from the cluster, when a message is received, The cluster will broadcast it invoking the messageReceived
     * on the receiver.
     *
     * @param msg the message received from the cluster
     */
    public abstract void messageReceived(ClusterMessage msg);


    /**
     * Accept only a certain type of messages.
     *
     * @param msg the message
     *
     * @return {@code true} to indicate that messageReceived should be invoked. If {@code false} is returned, the
     *             messageReceived method will not be invoked.
     */
    public abstract boolean accept(ClusterMessage msg);

}
