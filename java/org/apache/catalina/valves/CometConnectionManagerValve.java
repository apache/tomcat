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


package org.apache.catalina.valves;


import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import org.apache.catalina.CometEvent;
import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.connector.CometEventImpl;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.util.StringManager;


/**
 * <p>Implementation of a Valve that tracks Comet connections, and closes them
 * when the associated session expires or the webapp is reloaded.</p>
 *
 * <p>This Valve should be attached to a Context.</p>
 *
 * @author Remy Maucherat
 * @version $Revision$ $Date$
 */

public class CometConnectionManagerValve
    extends ValveBase
    implements Lifecycle, HttpSessionListener, LifecycleListener {


    // ----------------------------------------------------- Instance Variables


    /**
     * The descriptive information related to this implementation.
     */
    protected static final String info =
        "org.apache.catalina.valves.CometConnectionManagerValve/1.0";


    /**
     * The string manager for this package.
     */
    protected StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * The lifecycle event support for this component.
     */
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);


    /**
     * Has this component been started yet?
     */
    protected boolean started = false;

    
    /**
     * Connection list.
     */
    protected ConcurrentHashMap<String, ConnectionInfo[]> connections
        = new ConcurrentHashMap<String, ConnectionInfo[]>();
    

    // ------------------------------------------------------------- Properties

    
    // ------------------------------------------------------ Lifecycle Methods


    /**
     * Add a lifecycle event listener to this component.
     *
     * @param listener The listener to add
     */
    public void addLifecycleListener(LifecycleListener listener) {

        lifecycle.addLifecycleListener(listener);

    }


    /**
     * Get the lifecycle listeners associated with this lifecycle. If this
     * Lifecycle has no listeners registered, a zero-length array is returned.
     */
    public LifecycleListener[] findLifecycleListeners() {

        return lifecycle.findLifecycleListeners();

    }


    /**
     * Remove a lifecycle event listener from this component.
     *
     * @param listener The listener to add
     */
    public void removeLifecycleListener(LifecycleListener listener) {

        lifecycle.removeLifecycleListener(listener);

    }


    /**
     * Prepare for the beginning of active use of the public methods of this
     * component.  This method should be called after <code>configure()</code>,
     * and before any of the public methods of the component are utilized.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    public void start() throws LifecycleException {

        // Validate and update our current component state
        if (started)
            throw new LifecycleException
                (sm.getString("semaphoreValve.alreadyStarted"));
        lifecycle.fireLifecycleEvent(START_EVENT, null);
        started = true;

        if (container instanceof Context) {
            ((Lifecycle) container).addLifecycleListener(this);
        }
        
    }


    /**
     * Gracefully terminate the active use of the public methods of this
     * component.  This method should be the last one called on a given
     * instance of this component.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    public void stop() throws LifecycleException {

        // Validate and update our current component state
        if (!started)
            throw new LifecycleException
                (sm.getString("semaphoreValve.notStarted"));
        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        started = false;

        if (container instanceof Context) {
            ((Lifecycle) container).removeLifecycleListener(this);
        }

        // The webapp is getting stopped, so all current connections 
        // should be closed
        // Close all Comet connections associated with this session
        // Note: this will only be done if the container was not a Context
        // (otherwise, this needs to be done before stop, as the servlet would
        // be deallocated already)
        Iterator<ConnectionInfo[]> iterator = connections.values().iterator();
        while (iterator.hasNext()) {
            ConnectionInfo[] connectionInfos = iterator.next();
            if (connectionInfos != null) {
                for (int i = 0; i < connectionInfos.length; i++) {
                    ConnectionInfo connectionInfo = connectionInfos[i];
                    try {
                        connectionInfo.event.close();
                    } catch (Exception e) {
                        container.getLogger().warn(sm.getString("cometConnectionManagerValve.event"), e);
                    }
                }
            }
        }
        connections.clear();

    }

    
    public void lifecycleEvent(LifecycleEvent event) {
        if (event.getType() == Lifecycle.BEFORE_STOP_EVENT) {
            // The webapp is getting stopped, so all current connections 
            // should be closed
            // Close all Comet connections associated with this session
            Iterator<ConnectionInfo[]> iterator = connections.values().iterator();
            while (iterator.hasNext()) {
                ConnectionInfo[] connectionInfos = iterator.next();
                if (connectionInfos != null) {
                    for (int i = 0; i < connectionInfos.length; i++) {
                        ConnectionInfo connectionInfo = connectionInfos[i];
                        try {
                            ((CometEventImpl) connectionInfo.event).setEventType(CometEvent.EventType.END);
                            ((CometEventImpl) connectionInfo.event).setEventSubType(CometEvent.EventSubType.WEBAPP_RELOAD);
                            getNext().event(connectionInfo.request, connectionInfo.response, connectionInfo.event);
                            connectionInfo.event.close();
                        } catch (Exception e) {
                            container.getLogger().warn(sm.getString("cometConnectionManagerValve.event"), e);
                        }
                    }
                }
            }
            connections.clear();
        }
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Return descriptive information about this Valve implementation.
     */
    public String getInfo() {
        return (info);
    }


    /**
     * Register requests for tracking, whenever needed.
     *
     * @param request The servlet request to be processed
     * @param response The servlet response to be created
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    public void invoke(Request request, Response response)
        throws IOException, ServletException {
        // Perform the request
        getNext().invoke(request, response);
        
        if (request.isComet() && !response.isClosed()) {
            // Start tracking this connection, since this is a 
            // begin event, and Comet mode is on
            HttpSession session = request.getSession(true);
            ConnectionInfo newConnectionInfo = new ConnectionInfo();
            newConnectionInfo.request = request;
            newConnectionInfo.response = response;
            newConnectionInfo.event = request.getEvent();
            synchronized (session) {
                String id = session.getId();
                ConnectionInfo[] connectionInfos = connections.get(id);
                if (connectionInfos == null) {
                    connectionInfos = new ConnectionInfo[1];
                    connectionInfos[0] = newConnectionInfo;
                    connections.put(id, connectionInfos);
                } else {
                    ConnectionInfo[] newConnectionInfos = 
                        new ConnectionInfo[connectionInfos.length + 1];
                    for (int i = 0; i < connectionInfos.length; i++) {
                        newConnectionInfos[i] = connectionInfos[i];
                    }
                    newConnectionInfos[connectionInfos.length] = newConnectionInfo;
                    connections.put(id, newConnectionInfos);
                }
            }
        }
        
    }

    
    /**
     * Use events to update the connection state.
     *
     * @param request The servlet request to be processed
     * @param response The servlet response to be created
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    public void event(Request request, Response response, CometEvent event)
        throws IOException, ServletException {
        
        // Perform the request
        boolean ok = false;
        try {
            getNext().event(request, response, event);
            ok = true;
        } finally {
            if (!ok || response.isClosed() 
                    || (event.getEventType() == CometEvent.EventType.END)
                    || (event.getEventType() == CometEvent.EventType.ERROR
                            && !(event.getEventSubType() == CometEvent.EventSubType.TIMEOUT))) {
                // Remove from tracked list, the connection is done
                HttpSession session = request.getSession(true);
                synchronized (session) {
                    ConnectionInfo[] connectionInfos = connections.get(session.getId());
                    if (connectionInfos != null) {
                        boolean found = false;
                        for (int i = 0; !found && (i < connectionInfos.length); i++) {
                            found = (connectionInfos[i].request == request);
                        }
                        if (found) {
                            ConnectionInfo[] newConnectionInfos = 
                                new ConnectionInfo[connectionInfos.length - 1];
                            int pos = 0;
                            for (int i = 0; i < connectionInfos.length; i++) {
                                if (connectionInfos[i].request != request) {
                                    newConnectionInfos[pos++] = connectionInfos[i];
                                }
                            }
                            connections.put(session.getId(), newConnectionInfos);
                        }
                    }
                }                
            }
        }
        
    }


    public void sessionCreated(HttpSessionEvent se) {
    }


    public void sessionDestroyed(HttpSessionEvent se) {
        // Close all Comet connections associated with this session
        ConnectionInfo[] connectionInfos = connections.remove(se.getSession().getId());
        if (connectionInfos != null) {
            for (int i = 0; i < connectionInfos.length; i++) {
                ConnectionInfo connectionInfo = connectionInfos[i];
                try {
                    ((CometEventImpl) connectionInfo.event).setEventType(CometEvent.EventType.END);
                    ((CometEventImpl) connectionInfo.event).setEventSubType(CometEvent.EventSubType.SESSION_END);
                    getNext().event(connectionInfo.request, connectionInfo.response, connectionInfo.event);
                    connectionInfo.event.close();
                } catch (Exception e) {
                    container.getLogger().warn(sm.getString("cometConnectionManagerValve.event"), e);
                }
            }
        }
    }


    // --------------------------------------------- ConnectionInfo Inner Class

    
    protected class ConnectionInfo {
        public CometEvent event;
        public Request request;
        public Response response;
    }


}
