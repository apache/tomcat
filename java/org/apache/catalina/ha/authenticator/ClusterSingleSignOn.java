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


import java.security.Principal;

import org.apache.catalina.Cluster;
import org.apache.catalina.Container;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.authenticator.SingleSignOn;
import org.apache.catalina.ha.CatalinaCluster;
import org.apache.catalina.ha.ClusterManager;
import org.apache.catalina.ha.session.SerializablePrincipal;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.tomcat.util.ExceptionUtils;



/**
 * A <strong>Valve</strong> that supports a "single sign on" user experience on
 * each nodes of a cluster, where the security identity of a user who successfully
 * authenticates to one web application is propagated to other web applications and
 * to other nodes cluster in the same security domain.  For successful use, the following
 * requirements must be met:
 * <ul>
 * <li>This Valve must be configured on the Container that represents a
 *     virtual host (typically an implementation of <code>Host</code>).</li>
 * <li>The <code>Realm</code> that contains the shared user and role
 *     information must be configured on the same Container (or a higher
 *     one), and not overridden at the web application level.</li>
 * <li>The web applications themselves must use one of the standard
 *     Authenticators found in the
 *     <code>org.apache.catalina.authenticator</code> package.</li>
 * </ul>
 *
 * @author Fabien Carrion
 */

public class ClusterSingleSignOn
    extends SingleSignOn {


    // ----------------------------------------------------- Instance Variables


    /**
     * Descriptive information about this Valve implementation.
     */
    protected static final String info =
        "org.apache.catalina.ha.authenticator.ClusterSingleSignOn";

    protected int messageNumber = 0;

    private ClusterSingleSignOnListener clusterSSOListener = null;


    // ------------------------------------------------------------- Properties

    private CatalinaCluster cluster = null;



    /**
     * Return descriptive information about this Valve implementation.
     */
    @Override
    public String getInfo() {

        return (info);

    }

    public CatalinaCluster getCluster() {

        return cluster;

    }

    public void setCluster(CatalinaCluster cluster) {

        this.cluster = cluster;

    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * Start this component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {
        
        clusterSSOListener = new ClusterSingleSignOnListener();
        clusterSSOListener.setClusterSSO(this);

        // Load the cluster component, if any
        try {
            //the channel is already running
            Cluster cluster = getCluster();
            // stop remove cluster binding
            if(cluster == null) {
                Container host = getContainer();
                if(host != null && host instanceof Host) {
                    cluster = host.getCluster();
                    if(cluster != null && cluster instanceof CatalinaCluster) {
                        setCluster((CatalinaCluster) cluster);
                        getCluster().addClusterListener(clusterSSOListener);
                    } else {
                        Container engine = host.getParent();
                        if(engine != null && engine instanceof Engine) {
                            cluster = engine.getCluster();
                            if(cluster != null && cluster instanceof CatalinaCluster) {
                                setCluster((CatalinaCluster) cluster);
                                getCluster().addClusterListener(clusterSSOListener);
                            }
                        } else {
                            cluster = null;
                        }
                    }
                }
            }
            if (cluster == null) {
                throw new LifecycleException(
                        "There is no Cluster for ClusterSingleSignOn");
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            throw new LifecycleException(
                    "ClusterSingleSignOn exception during clusterLoad " + t);
        }

        super.startInternal();
    }


    /**
     * Stop this component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        super.stopInternal();

        if (getCluster() != null) {
            getCluster().removeClusterListener(clusterSSOListener);
        }
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Notify the cluster of the addition of a Session to
     * an SSO session and associate the specified single
     * sign on identifier with the specified Session on the
     * local node.
     *
     * @param ssoId Single sign on identifier
     * @param session Session to be associated
     */
    @Override
    protected void associate(String ssoId, Session session) {

        if (cluster != null && cluster.getMembers().length > 0) {
            messageNumber++;
            SingleSignOnMessage msg =
                new SingleSignOnMessage(cluster.getLocalMember(),
                                        ssoId, session.getId());
            Manager mgr = session.getManager();
            if ((mgr != null) && (mgr instanceof ClusterManager))
                msg.setContextName(((ClusterManager) mgr).getName());

            msg.setAction(SingleSignOnMessage.ADD_SESSION);

            cluster.send(msg);

            if (containerLog.isDebugEnabled())
                containerLog.debug("SingleSignOnMessage Send with action "
                                   + msg.getAction());
        }

        associateLocal(ssoId, session);

    }

    protected void associateLocal(String ssoId, Session session) {

        super.associate(ssoId, session);

    }

    /**
     * Notify the cluster of the removal of a Session from an
     * SSO session and deregister the specified session. If it is the last
     * session, then also get rid of the single sign on identifier on the
     * local node.
     *
     * @param ssoId Single sign on identifier
     * @param session Session to be deregistered
     */
    @Override
    protected void deregister(String ssoId, Session session) {

        if (cluster != null && cluster.getMembers().length > 0) {
            messageNumber++;
            SingleSignOnMessage msg =
                new SingleSignOnMessage(cluster.getLocalMember(),
                                        ssoId, session.getId());
            Manager mgr = session.getManager();
            if ((mgr != null) && (mgr instanceof ClusterManager))
                msg.setContextName(((ClusterManager) mgr).getName());

            msg.setAction(SingleSignOnMessage.DEREGISTER_SESSION);

            cluster.send(msg);
            if (containerLog.isDebugEnabled())
                containerLog.debug("SingleSignOnMessage Send with action "
                                   + msg.getAction());
        }

        deregisterLocal(ssoId, session);

    }

    protected void deregisterLocal(String ssoId, Session session) {

        super.deregister(ssoId, session);

    }

    /**
     * Notifies the cluster that a single sign on session
     * has been terminated due to a user logout, deregister
     * the specified single sign on identifier, and invalidate
     * any associated sessions on the local node.
     *
     * @param ssoId Single sign on identifier to deregister
     */
    @Override
    protected void deregister(String ssoId) {

        if (cluster != null && cluster.getMembers().length > 0) {
            messageNumber++;
            SingleSignOnMessage msg =
                new SingleSignOnMessage(cluster.getLocalMember(),
                                        ssoId, null);
            msg.setAction(SingleSignOnMessage.LOGOUT_SESSION);

            cluster.send(msg);
            if (containerLog.isDebugEnabled())
                containerLog.debug("SingleSignOnMessage Send with action "
                                   + msg.getAction());
        }

        deregisterLocal(ssoId);

    }

    protected void deregisterLocal(String ssoId) {

        super.deregister(ssoId);

    }

    /**
     * Notifies the cluster of the creation of a new SSO entry
     * and register the specified Principal as being associated
     * with the specified value for the single sign on identifier.
     *
     * @param ssoId Single sign on identifier to register
     * @param principal Associated user principal that is identified
     * @param authType Authentication type used to authenticate this
     *  user principal
     * @param username Username used to authenticate this user
     * @param password Password used to authenticate this user
     */
    @Override
    protected void register(String ssoId, Principal principal, String authType,
                  String username, String password) {

        if (cluster != null && cluster.getMembers().length > 0) {
            messageNumber++;
            SingleSignOnMessage msg =
                new SingleSignOnMessage(cluster.getLocalMember(),
                                        ssoId, null);
            msg.setAction(SingleSignOnMessage.REGISTER_SESSION);
            msg.setAuthType(authType);
            msg.setUsername(username);
            msg.setPassword(password);

            SerializablePrincipal sp = null;
            if (principal instanceof GenericPrincipal) {
                sp = SerializablePrincipal.createPrincipal((GenericPrincipal) principal);
                msg.setPrincipal(sp);
            }

            cluster.send(msg);
            if (containerLog.isDebugEnabled())
                containerLog.debug("SingleSignOnMessage Send with action "
                                   + msg.getAction());
        }

        registerLocal(ssoId, principal, authType, username, password);

    }

    protected void registerLocal(String ssoId, Principal principal, String authType,
                  String username, String password) {

        super.register(ssoId, principal, authType, username, password);

    }


    /**
     * Notifies the cluster of an update of the security credentials
     * associated with an SSO session. Updates any <code>SingleSignOnEntry</code>
     * found under key <code>ssoId</code> with the given authentication data.
     * <p>
     * The purpose of this method is to allow an SSO entry that was
     * established without a username/password combination (i.e. established
     * following DIGEST or CLIENT-CERT authentication) to be updated with
     * a username and password if one becomes available through a subsequent
     * BASIC or FORM authentication.  The SSO entry will then be usable for
     * reauthentication.
     * <p>
     * <b>NOTE:</b> Only updates the SSO entry if a call to
     * <code>SingleSignOnEntry.getCanReauthenticate()</code> returns
     * <code>false</code>; otherwise, it is assumed that the SSO entry already
     * has sufficient information to allow reauthentication and that no update
     * is needed.
     *
     * @param ssoId     identifier of Single sign to be updated
     * @param principal the <code>Principal</code> returned by the latest
     *                  call to <code>Realm.authenticate</code>.
     * @param authType  the type of authenticator used (BASIC, CLIENT-CERT,
     *                  DIGEST or FORM)
     * @param username  the username (if any) used for the authentication
     * @param password  the password (if any) used for the authentication
     */
    @Override
    protected void update(String ssoId, Principal principal, String authType,
                          String username, String password) {

        if (cluster != null && cluster.getMembers().length > 0) {
            messageNumber++;
            SingleSignOnMessage msg =
                new SingleSignOnMessage(cluster.getLocalMember(),
                                        ssoId, null);
            msg.setAction(SingleSignOnMessage.UPDATE_SESSION);
            msg.setAuthType(authType);
            msg.setUsername(username);
            msg.setPassword(password);

            SerializablePrincipal sp = null;
            if (principal instanceof GenericPrincipal) {
                sp = SerializablePrincipal.createPrincipal((GenericPrincipal) principal);
                msg.setPrincipal(sp);
            }

            cluster.send(msg);
            if (containerLog.isDebugEnabled())
                containerLog.debug("SingleSignOnMessage Send with action "
                                   + msg.getAction());
        }

        updateLocal(ssoId, principal, authType, username, password);

    }

    protected void updateLocal(String ssoId, Principal principal, String authType,
                          String username, String password) {

        super.update(ssoId, principal, authType, username, password);

    }


    /**
     * Remove a single Session from a SingleSignOn and notify the cluster
     * of the removal. Called when a session is timed out and no longer active.
     *
     * @param ssoId Single sign on identifier from which to remove the session.
     * @param session the session to be removed.
     */
    @Override
    protected void removeSession(String ssoId, Session session) {

        if (cluster != null && cluster.getMembers().length > 0) {
            messageNumber++;
            SingleSignOnMessage msg =
                new SingleSignOnMessage(cluster.getLocalMember(),
                                        ssoId, session.getId());

            Manager mgr = session.getManager();
            if ((mgr != null) && (mgr instanceof ClusterManager))
                msg.setContextName(((ClusterManager) mgr).getName());

            msg.setAction(SingleSignOnMessage.REMOVE_SESSION);

            cluster.send(msg);
            if (containerLog.isDebugEnabled())
                containerLog.debug("SingleSignOnMessage Send with action "
                                   + msg.getAction());
        }

        removeSessionLocal(ssoId, session);
    }

    protected void removeSessionLocal(String ssoId, Session session) {

        super.removeSession(ssoId, session);
        
    }

}
