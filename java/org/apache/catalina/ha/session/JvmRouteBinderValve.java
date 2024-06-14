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

import java.io.IOException;

import jakarta.servlet.ServletException;

import org.apache.catalina.Cluster;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.authenticator.Constants;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.ha.CatalinaCluster;
import org.apache.catalina.ha.ClusterManager;
import org.apache.catalina.ha.ClusterValve;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.session.PersistentManager;
import org.apache.catalina.valves.ValveBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * Valve to handle Tomcat jvmRoute takeover using mod_jk module after node failure. After a node crashes, subsequent
 * requests go to other cluster nodes. That incurs a drop in performance. When this Valve is enabled on a backup node
 * and sees a request, which was intended for another (thus failed) node, it will rewrite the cookie jsessionid
 * information to use the route to this backup cluster node, that answered the request. After the response is delivered
 * to the client, all subsequent client requests will go directly to the backup node. The change of sessionid is also
 * sent to all other cluster nodes. After all that, the session stickiness will work directly to the backup node and the
 * traffic will not go back to the failed node after it is restarted!
 * <p>
 * Add this Valve to your cluster definition at conf/server.xml .
 *
 * <pre>
 *  &lt;Cluster&gt;
 *  &lt;Valve className=&quot;org.apache.catalina.ha.session.JvmRouteBinderValve&quot; /&gt;
 *  &lt;/Cluster&gt;
 * </pre>
 *
 * <em>A Trick:</em><br>
 * You can enable this mod_jk turnover mode via JMX before you drop a node to all backup nodes! Set enable true on all
 * JvmRouteBinderValve backups, disable worker at mod_jk and then drop node and restart it! Then enable mod_jk worker
 * and disable JvmRouteBinderValves again. This use case means that only requested sessions are migrated.
 *
 * @author Peter Rossbach
 */
public class JvmRouteBinderValve extends ValveBase implements ClusterValve {

    /*--Static Variables----------------------------------------*/
    public static final Log log = LogFactory.getLog(JvmRouteBinderValve.class);

    // ------------------------------------------------------ Constructor
    public JvmRouteBinderValve() {
        super(true);
    }

    /*--Instance Variables--------------------------------------*/

    /**
     * the cluster
     */
    protected CatalinaCluster cluster;

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(JvmRouteBinderValve.class);

    /**
     * enabled this component
     */
    protected boolean enabled = true;

    /**
     * number of session that no at this tomcat instance hosted
     */
    protected long numberOfSessions = 0;

    protected String sessionIdAttribute = "org.apache.catalina.ha.session.JvmRouteOriginalSessionID";


    /*--Logic---------------------------------------------------*/

    /**
     * set session id attribute to failed node for request.
     *
     * @return Returns the sessionIdAttribute.
     */
    public String getSessionIdAttribute() {
        return sessionIdAttribute;
    }

    /**
     * get name of failed request session attribute
     *
     * @param sessionIdAttribute The sessionIdAttribute to set.
     */
    public void setSessionIdAttribute(String sessionIdAttribute) {
        this.sessionIdAttribute = sessionIdAttribute;
    }

    /**
     * @return Returns the number of migrated sessions.
     */
    public long getNumberOfSessions() {
        return numberOfSessions;
    }

    /**
     * @return Returns the enabled.
     */
    public boolean getEnabled() {
        return enabled;
    }

    /**
     * @param enabled The enabled to set.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Detect possible the JVMRoute change at cluster backup node..
     *
     * @param request  tomcat request being processed
     * @param response tomcat response being processed
     *
     * @exception IOException      if an input/output error has occurred
     * @exception ServletException if a servlet error has occurred
     */
    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {

        if (getEnabled() && request.getContext() != null && request.getContext().getDistributable() &&
                !request.isAsyncDispatching()) {
            // valve cluster can access manager - other cluster handle turnover
            // at host level - hopefully!
            Manager manager = request.getContext().getManager();

            if (manager != null && ((manager instanceof ClusterManager && getCluster() != null &&
                    getCluster().getManager(((ClusterManager) manager).getName()) != null) ||
                    (manager instanceof PersistentManager))) {
                handlePossibleTurnover(request);
            }
        }
        // Pass this request on to the next valve in our pipeline
        getNext().invoke(request, response);
    }

    /**
     * handle possible session turn over.
     *
     * @see JvmRouteBinderValve#handleJvmRoute(Request, String, String)
     *
     * @param request current request
     */
    protected void handlePossibleTurnover(Request request) {
        String sessionID = request.getRequestedSessionId();
        if (sessionID != null) {
            long t1 = System.currentTimeMillis();
            String jvmRoute = getLocalJvmRoute(request);
            if (jvmRoute == null) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("jvmRoute.missingJvmRouteAttribute"));
                }
                return;
            }
            handleJvmRoute(request, sessionID, jvmRoute);
            if (log.isTraceEnabled()) {
                long t2 = System.currentTimeMillis();
                long time = t2 - t1;
                log.trace(sm.getString("jvmRoute.turnoverInfo", Long.valueOf(time)));
            }
        }
    }

    /**
     * get jvmroute from engine
     *
     * @param request current request
     *
     * @return return jvmRoute from ManagerBase or null
     */
    protected String getLocalJvmRoute(Request request) {
        Manager manager = getManager(request);
        if (manager instanceof ManagerBase) {
            return ((ManagerBase) manager).getJvmRoute();
        }
        return null;
    }

    /**
     * get ClusterManager
     *
     * @param request current request
     *
     * @return manager or null
     */
    protected Manager getManager(Request request) {
        Manager manager = request.getContext().getManager();
        if (log.isDebugEnabled()) {
            if (manager != null) {
                log.trace(sm.getString("jvmRoute.foundManager", manager, request.getContext().getName()));
            } else {
                log.debug(sm.getString("jvmRoute.notFoundManager", request.getContext().getName()));
            }
        }
        return manager;
    }

    @Override
    public CatalinaCluster getCluster() {
        return cluster;
    }

    @Override
    public void setCluster(CatalinaCluster cluster) {
        this.cluster = cluster;
    }

    /**
     * Handle jvmRoute stickiness after tomcat instance failed. After this correction a new Cookie send to client with
     * new jvmRoute and the SessionID change propagate to the other cluster nodes.
     *
     * @param request       current request
     * @param sessionId     request SessionID from Cookie
     * @param localJvmRoute local jvmRoute
     */
    protected void handleJvmRoute(Request request, String sessionId, String localJvmRoute) {
        // get requested jvmRoute.
        String requestJvmRoute = null;
        int index = sessionId.indexOf('.');
        if (index > 0) {
            requestJvmRoute = sessionId.substring(index + 1);
        }
        if (requestJvmRoute != null && !requestJvmRoute.equals(localJvmRoute)) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("jvmRoute.failover", requestJvmRoute, localJvmRoute, sessionId));
            }
            Session catalinaSession = null;
            try {
                catalinaSession = getManager(request).findSession(sessionId);
            } catch (IOException e) {
                // Hups!
            }
            String id = sessionId.substring(0, index);
            String newSessionID = id + "." + localJvmRoute;
            // OK - turnover the session and inform other cluster nodes
            if (catalinaSession != null) {
                changeSessionID(request, sessionId, newSessionID, catalinaSession);
                numberOfSessions++;
            } else {
                try {
                    catalinaSession = getManager(request).findSession(newSessionID);
                } catch (IOException e) {
                    // Hups!
                }
                if (catalinaSession != null) {
                    // session is rewrite at other request, rewrite this also
                    changeRequestSessionID(request, sessionId, newSessionID);
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("jvmRoute.cannotFindSession", sessionId));
                    }
                }
            }
        }
    }

    /**
     * change session id and send to all cluster nodes
     *
     * @param request         current request
     * @param sessionId       original session id
     * @param newSessionID    new session id for node migration
     * @param catalinaSession current session with original session id
     */
    protected void changeSessionID(Request request, String sessionId, String newSessionID, Session catalinaSession) {
        fireLifecycleEvent("Before session migration", catalinaSession);
        getManager(request).changeSessionId(catalinaSession, newSessionID);
        changeRequestSessionID(request, sessionId, newSessionID);
        changeSessionAuthenticationNote(sessionId, newSessionID, catalinaSession);
        fireLifecycleEvent("After session migration", catalinaSession);
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("jvmRoute.changeSession", sessionId, newSessionID));
        }
    }

    /**
     * Change Request Session id
     *
     * @param request      current request
     * @param sessionId    original session id
     * @param newSessionID new session id for node migration
     */
    protected void changeRequestSessionID(Request request, String sessionId, String newSessionID) {
        request.changeSessionId(newSessionID);

        // set original sessionid at request, to allow application detect the
        // change
        if (sessionIdAttribute != null && !sessionIdAttribute.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("jvmRoute.set.originalsessionid", sessionIdAttribute, sessionId));
            }
            request.setAttribute(sessionIdAttribute, sessionId);
        }
    }


    /**
     * Change the current session ID that is stored in a session note during authentication. It is part of the CSRF
     * protection.
     *
     * @param sessionId       The original session ID
     * @param newSessionID    The new session ID for node migration
     * @param catalinaSession The session object (that will be using the new session ID at the point this method is
     *                            called)
     */
    protected void changeSessionAuthenticationNote(String sessionId, String newSessionID, Session catalinaSession) {
        if (sessionId.equals(catalinaSession.getNote(Constants.SESSION_ID_NOTE))) {
            catalinaSession.setNote(Constants.SESSION_ID_NOTE, newSessionID);
        }
    }


    /**
     * Start this component and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error that prevents this component from being
     *                                   used
     */
    @Override
    protected void startInternal() throws LifecycleException {

        if (cluster == null) {
            Cluster containerCluster = getContainer().getCluster();
            if (containerCluster instanceof CatalinaCluster) {
                setCluster((CatalinaCluster) containerCluster);
            }
        }

        if (log.isInfoEnabled()) {
            log.info(sm.getString("jvmRoute.valve.started"));
            if (cluster == null) {
                log.info(sm.getString("jvmRoute.noCluster"));
            }
        }

        super.startInternal();
    }


    /**
     * Stop this component and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error that prevents this component from being
     *                                   used
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        super.stopInternal();

        cluster = null;
        numberOfSessions = 0;
        if (log.isInfoEnabled()) {
            log.info(sm.getString("jvmRoute.valve.stopped"));
        }

    }

}
