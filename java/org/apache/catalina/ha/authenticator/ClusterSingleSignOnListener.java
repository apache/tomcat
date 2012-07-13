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

package org.apache.catalina.ha.authenticator;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;

import org.apache.catalina.Session;
import org.apache.catalina.ha.ClusterListener;
import org.apache.catalina.ha.ClusterManager;
import org.apache.catalina.ha.ClusterMessage;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Receive replicated SingleSignOnMessage form other cluster node.
 *
 * @author Fabien Carrion
 */
public class ClusterSingleSignOnListener extends ClusterListener {

    private static final Log log =
        LogFactory.getLog(ClusterSingleSignOnListener.class);

    // ------------------------------------------------------------- Properties

    private final ClusterSingleSignOn clusterSSO;


    //--Constructor---------------------------------------------

    public ClusterSingleSignOnListener(ClusterSingleSignOn clusterSSO) {
        this.clusterSSO = clusterSSO;
    }

    /**
     * Callback from the cluster, when a message is received, The cluster will
     * broadcast it invoking the messageReceived on the receiver.
     *
     * @param myobj
     *            ClusterMessage - the message received from the cluster
     */
    @Override
    public void messageReceived(ClusterMessage myobj) {
        if (myobj != null && myobj instanceof SingleSignOnMessage) {
            SingleSignOnMessage msg = (SingleSignOnMessage) myobj;
            int action = msg.getAction();
            Session session = null;
            Principal principal = null;

            if (log.isDebugEnabled())
                log.debug("SingleSignOnMessage Received with action "
                          + msg.getAction());

            switch(action) {
            case SingleSignOnMessage.ADD_SESSION:
                session = getSession(msg.getSessionId(),
                                     msg.getContextName());
                if (session != null)
                    clusterSSO.associateLocal(msg.getSsoId(), session);
                break;
            case SingleSignOnMessage.DEREGISTER_SESSION:
                session = getSession(msg.getSessionId(),
                                     msg.getContextName());
                if (session != null)
                    clusterSSO.deregisterLocal(msg.getSsoId(), session);
                break;
            case SingleSignOnMessage.LOGOUT_SESSION:
                clusterSSO.deregisterLocal(msg.getSsoId());
                break;
            case SingleSignOnMessage.REGISTER_SESSION:
                if (msg.getPrincipal() != null) {
                    principal = msg.getPrincipal().getPrincipal();
                }
                clusterSSO.registerLocal(msg.getSsoId(), principal, msg.getAuthType(),
                                         msg.getUsername(), msg.getPassword());
                break;
            case SingleSignOnMessage.UPDATE_SESSION:
                if (msg.getPrincipal() != null) {
                    principal = msg.getPrincipal().getPrincipal();
                }
                clusterSSO.updateLocal(msg.getSsoId(), principal, msg.getAuthType(),
                                       msg.getUsername(), msg.getPassword());
                break;
            case SingleSignOnMessage.REMOVE_SESSION:
                session = getSession(msg.getSessionId(),
                                     msg.getContextName());
                if (session != null)
                    clusterSSO.removeSessionLocal(msg.getSsoId(), session);
                break;
            }
        }
    }

    /**
     * Accept only SingleSignOnMessage
     *
     * @param msg
     *            ClusterMessage
     * @return boolean - returns true to indicate that messageReceived should be
     *         invoked. If false is returned, the messageReceived method will
     *         not be invoked.
     */
    @Override
    public boolean accept(ClusterMessage msg) {
        return (msg instanceof SingleSignOnMessage);
    }


    private Session getSession(String sessionId, String ctxname) {

        Map<String,ClusterManager> managers = clusterSSO.getCluster().getManagers();
        Session session = null;

        if (ctxname == null) {
            for (Map.Entry<String, ClusterManager> entry : managers.entrySet()) {
                if (entry.getValue() != null) {
                    try {
                        session = entry.getValue().findSession(sessionId);
                    } catch (IOException io) {
                        log.error("Session doesn't exist:" + io);
                    }
                    return session;
                }
                //this happens a lot before the system has started
                // up
                if (log.isDebugEnabled())
                    log.debug("Context manager doesn't exist:"
                              + entry.getKey());
            }
        } else {
            ClusterManager mgr = managers.get(ctxname);
            if (mgr != null) {
                try {
                    session = mgr.findSession(sessionId);
                } catch (IOException io) {
                    log.error("Session doesn't exist:" + io);
                }
                return session;
            } else if (log.isErrorEnabled())
                log.error("Context manager doesn't exist:" + ctxname);
        }

        return null;
    }
}

