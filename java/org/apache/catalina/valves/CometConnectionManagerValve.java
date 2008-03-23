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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import org.apache.catalina.CometEvent;
import org.apache.catalina.CometProcessor;
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
     * List of current Coment connections.
     */
    protected List<Request> cometRequests =
        Collections.synchronizedList(new ArrayList<Request>());
    

    /**
     * Name of session attribute used to store list of comet connections.
     */
    protected String cometRequestsAttribute =
        "org.apache.tomcat.comet.connectionList";


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

    }

    
    public void lifecycleEvent(LifecycleEvent event) {
        if (event.getType() == Lifecycle.BEFORE_STOP_EVENT) {
            // The container is getting stopped, close all current connections 
            Iterator<Request> iterator = cometRequests.iterator();
            while (iterator.hasNext()) {
                Request request = iterator.next();
                // Remove the session tracking attribute as it isn't
                // serializable or required.
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.removeAttribute(cometRequestsAttribute);
                }
                // Close the comet connection
                try {
                    CometEventImpl cometEvent = request.getEvent();
                    cometEvent.setEventType(CometEvent.EventType.END);
                    cometEvent.setEventSubType(
                            CometEvent.EventSubType.WEBAPP_RELOAD);
                    getNext().event(request, request.getResponse(), cometEvent);
                    cometEvent.close();
                } catch (Exception e) {
                    container.getLogger().warn(
                            sm.getString("cometConnectionManagerValve.event"),
                            e);
                }
            }
            cometRequests.clear();
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
            
            // Track the conection for webapp reload
            cometRequests.add(request);
            
            // Track the connection for session expiration
            synchronized (session) {
                Request[] requests = (Request[])
                        session.getAttribute(cometRequestsAttribute);
                if (requests == null) {
                    requests = new Request[1];
                    requests[0] = request;
                    session.setAttribute(cometRequestsAttribute,
                            requests);
                } else {
                    Request[] newRequests = 
                        new Request[requests.length + 1];
                    for (int i = 0; i < requests.length; i++) {
                        newRequests[i] = requests[i];
                    }
                    newRequests[requests.length] = request;
                    session.setAttribute(cometRequestsAttribute, newRequests);
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
                            && !(event.getEventSubType() ==
                                CometEvent.EventSubType.TIMEOUT))) {
                
                // Remove the connection from webapp reload tracking
                cometRequests.remove(request);
                
                // Remove connection from session expiration tracking
                // Note: can't get the session if it has been invalidated but
                // OK since session listener will have done clean-up
                HttpSession session = request.getSession(false);
                if (session != null) {
                    synchronized (session) {
                        Request[] reqs = (Request[])
                            session.getAttribute(cometRequestsAttribute);
                        if (reqs != null) {
                            boolean found = false;
                            for (int i = 0; !found && (i < reqs.length); i++) {
                                found = (reqs[i] == request);
                            }
                            if (found) {
                                if (reqs.length > 1) {
                                    Request[] newConnectionInfos = 
                                        new Request[reqs.length - 1];
                                    int pos = 0;
                                    for (int i = 0; i < reqs.length; i++) {
                                        if (reqs[i] != request) {
                                            newConnectionInfos[pos++] = reqs[i];
                                        }
                                    }
                                    session.setAttribute(cometRequestsAttribute,
                                            newConnectionInfos);
                                } else {
                                    session.removeAttribute(
                                            cometRequestsAttribute);
                                }
                            }
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
        Request[] reqs = (Request[])
            se.getSession().getAttribute(cometRequestsAttribute);
        if (reqs != null) {
            for (int i = 0; i < reqs.length; i++) {
                Request req = reqs[i];
                try {
                    CometEventImpl event = req.getEvent();
                    event.setEventType(CometEvent.EventType.END);
                    event.setEventSubType(CometEvent.EventSubType.SESSION_END);
                    ((CometProcessor)
                            req.getWrapper().getServlet()).event(event);
                    event.close();
                } catch (Exception e) {
                    req.getWrapper().getParent().getLogger().warn(sm.getString(
                            "cometConnectionManagerValve.listenerEvent"), e);
                }
            }
        }
    }

}
