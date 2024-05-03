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
package org.apache.catalina.ha.tcp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import jakarta.servlet.ServletException;

import org.apache.catalina.Cluster;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.ha.CatalinaCluster;
import org.apache.catalina.ha.ClusterManager;
import org.apache.catalina.ha.ClusterMessage;
import org.apache.catalina.ha.ClusterSession;
import org.apache.catalina.ha.ClusterValve;
import org.apache.catalina.ha.session.DeltaManager;
import org.apache.catalina.ha.session.DeltaSession;
import org.apache.catalina.valves.ValveBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * <p>
 * Implementation of a Valve that logs interesting contents from the specified Request (before processing) and the
 * corresponding Response (after processing). It is especially useful in debugging problems related to headers and
 * cookies.
 * </p>
 * <p>
 * This Valve may be attached to any Container, depending on the granularity of the logging you wish to perform.
 * </p>
 * <p>
 * primaryIndicator=true, then the request attribute <i>org.apache.catalina.ha.tcp.isPrimarySession.</i> is set true,
 * when request processing is at sessions primary node.
 * </p>
 *
 * @author Craig R. McClanahan
 * @author Peter Rossbach
 */
public class ReplicationValve extends ValveBase implements ClusterValve {

    private static final Log log = LogFactory.getLog(ReplicationValve.class);

    // ----------------------------------------------------- Instance Variables

    /**
     * The StringManager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    private CatalinaCluster cluster = null;

    /**
     * Filter expression
     */
    protected Pattern filter = null;

    /**
     * crossContext session container
     */
    protected final ThreadLocal<ArrayList<DeltaSession>> crossContextSessions = new ThreadLocal<>();

    /**
     * doProcessingStats (default = off)
     */
    protected boolean doProcessingStats = false;

    protected LongAdder totalRequestTime = new LongAdder();
    protected LongAdder totalSendTime = new LongAdder();
    protected LongAdder nrOfRequests = new LongAdder();
    protected AtomicLong lastSendTime = new AtomicLong();
    protected LongAdder nrOfFilterRequests = new LongAdder();
    protected LongAdder nrOfSendRequests = new LongAdder();
    protected LongAdder nrOfCrossContextSendRequests = new LongAdder();

    /**
     * must primary change indicator set
     */
    protected boolean primaryIndicator = false;

    /**
     * Name of primary change indicator as request attribute
     */
    protected String primaryIndicatorName = "org.apache.catalina.ha.tcp.isPrimarySession";

    // ------------------------------------------------------------- Properties

    public ReplicationValve() {
        super(true);
    }

    /**
     * @return the cluster.
     */
    @Override
    public CatalinaCluster getCluster() {
        return cluster;
    }

    /**
     * @param cluster The cluster to set.
     */
    @Override
    public void setCluster(CatalinaCluster cluster) {
        this.cluster = cluster;
    }

    /**
     * @return the filter
     */
    public String getFilter() {
        if (filter == null) {
            return null;
        }
        return filter.toString();
    }

    /**
     * compile filter string to regular expression
     *
     * @see Pattern#compile(String)
     *
     * @param filter The filter to set.
     */
    public void setFilter(String filter) {
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("ReplicationValve.filter.loading", filter));
        }

        if (filter == null || filter.length() == 0) {
            this.filter = null;
        } else {
            try {
                this.filter = Pattern.compile(filter);
            } catch (PatternSyntaxException pse) {
                log.error(sm.getString("ReplicationValve.filter.failure", filter), pse);
            }
        }
    }

    /**
     * @return the primaryIndicator.
     */
    public boolean isPrimaryIndicator() {
        return primaryIndicator;
    }

    /**
     * @param primaryIndicator The primaryIndicator to set.
     */
    public void setPrimaryIndicator(boolean primaryIndicator) {
        this.primaryIndicator = primaryIndicator;
    }

    /**
     * @return the primaryIndicatorName.
     */
    public String getPrimaryIndicatorName() {
        return primaryIndicatorName;
    }

    /**
     * @param primaryIndicatorName The primaryIndicatorName to set.
     */
    public void setPrimaryIndicatorName(String primaryIndicatorName) {
        this.primaryIndicatorName = primaryIndicatorName;
    }

    /**
     * Calc processing stats
     *
     * @return <code>true</code> if statistics are enabled
     */
    public boolean doStatistics() {
        return doProcessingStats;
    }

    /**
     * Set Calc processing stats
     *
     * @param doProcessingStats New flag value
     *
     * @see #resetStatistics()
     */
    public void setStatistics(boolean doProcessingStats) {
        this.doProcessingStats = doProcessingStats;
    }

    /**
     * @return the lastSendTime.
     */
    public long getLastSendTime() {
        return lastSendTime.longValue();
    }

    /**
     * @return the nrOfRequests.
     */
    public long getNrOfRequests() {
        return nrOfRequests.longValue();
    }

    /**
     * @return the nrOfFilterRequests.
     */
    public long getNrOfFilterRequests() {
        return nrOfFilterRequests.longValue();
    }

    /**
     * @return the nrOfCrossContextSendRequests.
     */
    public long getNrOfCrossContextSendRequests() {
        return nrOfCrossContextSendRequests.longValue();
    }

    /**
     * @return the nrOfSendRequests.
     */
    public long getNrOfSendRequests() {
        return nrOfSendRequests.longValue();
    }

    /**
     * @return the totalRequestTime.
     */
    public long getTotalRequestTime() {
        return totalRequestTime.longValue();
    }

    /**
     * @return the totalSendTime.
     */
    public long getTotalSendTime() {
        return totalSendTime.longValue();
    }

    // --------------------------------------------------------- Public Methods

    /**
     * Register all cross context sessions inside endAccess. Use a list with contains check, that the Portlet API can
     * include a lot of fragments from same or different applications with session changes.
     *
     * @param session cross context session
     */
    public void registerReplicationSession(DeltaSession session) {
        List<DeltaSession> sessions = crossContextSessions.get();
        if (sessions != null) {
            if (!sessions.contains(session)) {
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("ReplicationValve.crossContext.registerSession", session.getIdInternal(),
                            session.getManager().getContext().getName()));
                }
                sessions.add(session);
            }
        }
    }

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        long totalstart = 0;

        // this happens before the request
        if (doStatistics()) {
            totalstart = System.currentTimeMillis();
        }
        if (primaryIndicator) {
            createPrimaryIndicator(request);
        }
        Context context = request.getContext();
        boolean isCrossContext = context != null && context instanceof StandardContext && context.getCrossContext();
        boolean isAsync = request.getAsyncContextInternal() != null;
        try {
            if (isCrossContext) {
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("ReplicationValve.crossContext.add"));
                }
                crossContextSessions.set(new ArrayList<>());
            }
            getNext().invoke(request, response);
            if (context != null && cluster != null && context.getManager() instanceof ClusterManager) {
                ClusterManager clusterManager = (ClusterManager) context.getManager();

                // valve cluster can access manager - other cluster handle replication
                // at host level - hopefully!
                if (cluster.getManager(clusterManager.getName()) == null) {
                    return;
                }
                if (cluster.hasMembers()) {
                    sendReplicationMessage(request, totalstart, isCrossContext, isAsync, clusterManager);
                } else {
                    resetReplicationRequest(request, isCrossContext);
                }
            }
        } finally {
            // Array must be remove: Current master request send endAccess at recycle.
            // Don't register this request session again!
            if (isCrossContext) {
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("ReplicationValve.crossContext.remove"));
                }
                crossContextSessions.remove();
            }
        }
    }


    /**
     * reset the active statistics
     */
    public void resetStatistics() {
        totalRequestTime.reset();
        totalSendTime.reset();
        lastSendTime.set(0);
        nrOfFilterRequests.reset();
        nrOfRequests.reset();
        nrOfSendRequests.reset();
        nrOfCrossContextSendRequests.reset();
    }

    @Override
    protected void startInternal() throws LifecycleException {
        if (cluster == null) {
            Cluster containerCluster = getContainer().getCluster();
            if (containerCluster instanceof CatalinaCluster) {
                setCluster((CatalinaCluster) containerCluster);
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(sm.getString("ReplicationValve.nocluster"));
                }
            }
        }
        super.startInternal();
    }


    // --------------------------------------------------------- Protected Methods

    protected void sendReplicationMessage(Request request, long totalstart, boolean isCrossContext, boolean isAsync,
            ClusterManager clusterManager) {
        // this happens after the request
        long start = 0;
        if (doStatistics()) {
            start = System.currentTimeMillis();
        }
        try {
            // send invalid sessions
            sendInvalidSessions(clusterManager);
            // send replication
            sendSessionReplicationMessage(request, clusterManager);
            if (isCrossContext) {
                sendCrossContextSession();
            }
        } catch (Exception x) {
            // FIXME we have a lot of sends, but the trouble with one node stops the correct replication to other nodes!
            log.error(sm.getString("ReplicationValve.send.failure"), x);
        } finally {
            if (doStatistics()) {
                updateStats(totalstart, start, isAsync);
            }
        }
    }

    /**
     * Send all changed cross context sessions to backups
     */
    protected void sendCrossContextSession() {
        List<DeltaSession> sessions = crossContextSessions.get();
        if (sessions != null && sessions.size() > 0) {
            for (DeltaSession session : sessions) {
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("ReplicationValve.crossContext.sendDelta",
                            session.getManager().getContext().getName()));
                }
                sendMessage(session, (ClusterManager) session.getManager());
                if (doStatistics()) {
                    nrOfCrossContextSendRequests.increment();
                }
            }
        }
    }

    /**
     * Fix memory leak for long sessions with many changes, when no backup member exists!
     *
     * @param request        current request after response is generated
     * @param isCrossContext check crosscontext threadlocal
     */
    protected void resetReplicationRequest(Request request, boolean isCrossContext) {
        Session contextSession = request.getSessionInternal(false);
        if (contextSession instanceof DeltaSession) {
            resetDeltaRequest(contextSession);
            ((DeltaSession) contextSession).setPrimarySession(true);
        }
        if (isCrossContext) {
            List<DeltaSession> sessions = crossContextSessions.get();
            if (sessions != null && sessions.size() > 0) {
                Iterator<DeltaSession> iter = sessions.iterator();
                for (; iter.hasNext();) {
                    Session session = iter.next();
                    resetDeltaRequest(session);
                    if (session instanceof DeltaSession) {
                        ((DeltaSession) contextSession).setPrimarySession(true);
                    }

                }
            }
        }
    }

    /**
     * Reset DeltaRequest from session
     *
     * @param session HttpSession from current request or cross context session
     */
    protected void resetDeltaRequest(Session session) {
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("ReplicationValve.resetDeltaRequest", session.getManager().getContext().getName()));
        }
        ((DeltaSession) session).resetDeltaRequest();
    }

    /**
     * Send Cluster Replication Request
     *
     * @param request current request
     * @param manager session manager
     */
    protected void sendSessionReplicationMessage(Request request, ClusterManager manager) {
        Session session = request.getSessionInternal(false);
        if (session != null) {
            String uri = request.getDecodedRequestURI();
            // request without session change
            if (!isRequestWithoutSessionChange(uri)) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("ReplicationValve.invoke.uri", uri));
                }
                sendMessage(session, manager);
            } else if (doStatistics()) {
                nrOfFilterRequests.increment();
            }
        }

    }

    /**
     * Send message delta message from request session
     *
     * @param session current session
     * @param manager session manager
     */
    protected void sendMessage(Session session, ClusterManager manager) {
        String id = session.getIdInternal();
        if (id != null) {
            send(manager, id);
        }
    }

    /**
     * send manager requestCompleted message to cluster
     *
     * @param manager   SessionManager
     * @param sessionId sessionid from the manager
     *
     * @see DeltaManager#requestCompleted(String)
     * @see SimpleTcpCluster#send(ClusterMessage)
     */
    protected void send(ClusterManager manager, String sessionId) {
        ClusterMessage msg = manager.requestCompleted(sessionId);
        if (msg != null && cluster != null) {
            cluster.send(msg);
            if (doStatistics()) {
                nrOfSendRequests.increment();
            }
        }
    }

    /**
     * check for session invalidations
     *
     * @param manager Associated manager
     */
    protected void sendInvalidSessions(ClusterManager manager) {
        String[] invalidIds = manager.getInvalidatedSessions();
        if (invalidIds.length > 0) {
            for (String invalidId : invalidIds) {
                try {
                    send(manager, invalidId);
                } catch (Exception x) {
                    log.error(sm.getString("ReplicationValve.send.invalid.failure", invalidId), x);
                }
            }
        }
    }

    /**
     * is request without possible session change
     *
     * @param uri The request uri
     *
     * @return True if no session change
     */
    protected boolean isRequestWithoutSessionChange(String uri) {
        Pattern f = filter;
        return f != null && f.matcher(uri).matches();
    }

    /**
     * Protocol cluster replications stats
     *
     * @param requestTime Request time
     * @param clusterTime Cluster time
     * @param isAsync     if the request was in async mode
     */
    protected void updateStats(long requestTime, long clusterTime, boolean isAsync) {
        long currentTime = System.currentTimeMillis();
        lastSendTime.set(currentTime);
        totalSendTime.add(currentTime - clusterTime);
        totalRequestTime.add(currentTime - requestTime);
        if (!isAsync) {
            nrOfRequests.increment();
            if (log.isDebugEnabled()) {
                if ((nrOfRequests.longValue() % 100) == 0) {
                    log.debug(sm.getString("ReplicationValve.stats",
                            new Object[] { Long.valueOf(totalRequestTime.longValue() / nrOfRequests.longValue()),
                                    Long.valueOf(totalSendTime.longValue() / nrOfRequests.longValue()),
                                    Long.valueOf(nrOfRequests.longValue()), Long.valueOf(nrOfSendRequests.longValue()),
                                    Long.valueOf(nrOfCrossContextSendRequests.longValue()),
                                    Long.valueOf(nrOfFilterRequests.longValue()),
                                    Long.valueOf(totalRequestTime.longValue()),
                                    Long.valueOf(totalSendTime.longValue()) }));
                }
            }
        }
    }


    /**
     * Mark Request that processed at primary node with attribute primaryIndicatorName
     *
     * @param request The Servlet request
     *
     * @throws IOException IO error finding session
     */
    protected void createPrimaryIndicator(Request request) throws IOException {
        String id = request.getRequestedSessionId();
        if ((id != null) && (id.length() > 0)) {
            Manager manager = request.getContext().getManager();
            Session session = manager.findSession(id);
            if (session instanceof ClusterSession) {
                ClusterSession cses = (ClusterSession) session;
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("ReplicationValve.session.indicator", request.getContext().getName(), id,
                            primaryIndicatorName, Boolean.valueOf(cses.isPrimarySession())));
                }
                request.setAttribute(primaryIndicatorName, cses.isPrimarySession() ? Boolean.TRUE : Boolean.FALSE);
            } else {
                if (log.isDebugEnabled()) {
                    if (session != null) {
                        log.debug(sm.getString("ReplicationValve.session.found", request.getContext().getName(), id));
                    } else {
                        log.debug(sm.getString("ReplicationValve.session.invalid", request.getContext().getName(), id));
                    }
                }
            }
        }
    }

}
