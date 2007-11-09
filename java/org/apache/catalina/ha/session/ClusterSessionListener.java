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

import java.util.Map;

import org.apache.catalina.ha.ClusterManager;
import org.apache.catalina.ha.ClusterMessage;
import org.apache.catalina.ha.*;

/**
 * Receive replicated SessionMessage form other cluster node.
 * @author Filip Hanik
 * @author Peter Rossbach
 * @version $Revision$ $Date$
 */
public class ClusterSessionListener extends ClusterListener {
 
    /**
     * The descriptive information about this implementation.
     */
    protected static final String info = "org.apache.catalina.session.ClusterSessionListener/1.1";

    //--Constructor---------------------------------------------

    public ClusterSessionListener() {
    }

    //--Logic---------------------------------------------------

    /**
     * Return descriptive information about this implementation.
     */
    public String getInfo() {

        return (info);

    }

    /**
     * Callback from the cluster, when a message is received, The cluster will
     * broadcast it invoking the messageReceived on the receiver.
     * 
     * @param myobj
     *            ClusterMessage - the message received from the cluster
     */
    public void messageReceived(ClusterMessage myobj) {
        if (myobj != null && myobj instanceof SessionMessage) {
            SessionMessage msg = (SessionMessage) myobj;
            String ctxname = msg.getContextName();
            //check if the message is a EVT_GET_ALL_SESSIONS,
            //if so, wait until we are fully started up
            Map managers = cluster.getManagers() ;
            if (ctxname == null) {
                java.util.Iterator i = managers.keySet().iterator();
                while (i.hasNext()) {
                    String key = (String) i.next();
                    ClusterManager mgr = (ClusterManager) managers.get(key);
                    if (mgr != null)
                        mgr.messageDataReceived(msg);
                    else {
                        //this happens a lot before the system has started
                        // up
                        if (log.isDebugEnabled())
                            log.debug("Context manager doesn't exist:"
                                    + key);
                    }
                }
            } else {
                ClusterManager mgr = (ClusterManager) managers.get(ctxname);
                if (mgr != null)
                    mgr.messageDataReceived(msg);
                else if (log.isWarnEnabled())
                    log.warn("Context manager doesn't exist:" + ctxname);
            }
        }
        return;
    }

    /**
     * Accept only SessionMessage
     * 
     * @param msg
     *            ClusterMessage
     * @return boolean - returns true to indicate that messageReceived should be
     *         invoked. If false is returned, the messageReceived method will
     *         not be invoked.
     */
    public boolean accept(ClusterMessage msg) {
        return (msg instanceof SessionMessage);
    }
}

