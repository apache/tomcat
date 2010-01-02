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


package org.apache.tomcat.servlets.session;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;


// TODO: move 'expiring objects' to a separate utility class
// TODO: hook the background thread

/**
 * Minimal implementation of the <b>Manager</b> interface that supports
 * no session persistence or distributable capabilities.  This class may
 * be subclassed to create more sophisticated Manager implementations.
 * 
 * @author Costin Manolache
 * @author Craig R. McClanahan
 */
public class SimpleSessionManager implements UserSessionManager {
    static final Logger log = Logger.getLogger(SimpleSessionManager.class.getName());

    protected RandomGenerator randomG = new RandomGenerator();
    
    protected ServletContext context;


    /**
     * The distributable flag for Sessions created by this Manager.  If this
     * flag is set to <code>true</code>, any user attributes added to a
     * session controlled by this Manager must be Serializable. 
     * 
     * This is for compliance with the spec - tomcat-lite is not intended for
     * session replication ( use a full version for that )
     */
    protected boolean distributable;

    /**
     * The default maximum inactive interval for Sessions created by
     * this Manager.
     */
    protected int maxInactiveInterval = 60;

    /**
     * The longest time (in seconds) that an expired session had been alive.
     */
    protected int sessionMaxAliveTime;


    /**
     * Average time (in seconds) that expired sessions had been alive.
     */
    protected int sessionAverageAliveTime;


    /**
     * Number of sessions that have expired.
     */
    protected int expiredSessions = 0;

    static class SessionLRU extends LinkedHashMap {
//        protected boolean removeEldestEntry(Map.Entry eldest) {
//            HttpSessionImpl s = (HttpSessionImpl)eldest.getValue();
//            int size = this.size();
//            
//            // TODO: check if eldest is expired or if we're above the limit.
//            // if eldest is expired, turn a flag to check for more.
//            
//            // Note: this doesn't work well for sessions that set shorter
//            // expiry time, or longer expiry times. 
//            return false;
//        }

    }
    
    /**
     * The set of currently active Sessions for this Manager, keyed by
     * session identifier.
     */
    protected LinkedHashMap sessions = new SessionLRU();
        
    // Number of sessions created by this manager
    protected int sessionCounter=0;

    protected int maxActive=0;

    // number of duplicated session ids - anything >0 means we have problems
    protected int duplicates=0;

    protected boolean initialized=false;
    
    /**
     * Processing time during session expiration.
     */
    protected long processingTime = 0;
    
    static List EMPTY_LIST = new ArrayList();

    // One per machine - it has an internal pool, can schedule 
    // tasks for multiple webapps.
    static Timer timer = new Timer();
    boolean active = false;
    
    TimerTask task = new TimerTask() {
        public void run() {
            processExpires();
            synchronized (sessions) {
                // We don't want a timer thread running around if 
                // there is no activity
                if (sessions.size() == 0) {
                    active = false;
                    this.cancel();
                }
            }
        }
    };

    public List getEventListeners() {
        List l = 
            (List) context.getAttribute("context-listeners");
        if (l == null) return EMPTY_LIST;
        return l;
    }
    
    /** 
     * Total sessions created by this manager.
     */
    public int getSessionCounter() {
        return sessionCounter;
    }


    /** 
     * Number of duplicated session IDs generated by the random source.
     * Anything bigger than 0 means problems.
     *
     * @return The count of duplicates
     */
    public int getDuplicates() {
        return duplicates;
    }


    /** 
     * Returns the number of active sessions
     *
     * @return number of sessions active
     */
    public int getActiveSessions() {
        return sessions.size();
    }


    /**
     * Max number of concurrent active sessions
     *
     * @return The highest number of concurrent active sessions
     */
    public int getMaxActive() {
        return maxActive;
    }


    public void setMaxActive(int maxActive) {
        this.maxActive = maxActive;
    }


    /**
     * Gets the longest time (in seconds) that an expired session had been
     * alive.
     *
     * @return Longest time (in seconds) that an expired session had been
     * alive.
     */
    public int getSessionMaxAliveTime() {
        return sessionMaxAliveTime;
    }

    /**
     * Gets the average time (in seconds) that expired sessions had been
     * alive.
     *
     * @return Average time (in seconds) that expired sessions had been
     * alive.
     */
    public int getSessionAverageAliveTime() {
        return sessionAverageAliveTime;
    }

    /**
     * Return the Container with which this Manager is associated.
     */
    public ServletContext getContext() {
        return (this.context);
    }


    /**
     * Set the Container with which this Manager is associated.
     *
     * @param container The newly associated Container
     */
    public void setContext(ServletContext container) {
        this.context = container;
    }

    /**
     * Return the distributable flag for the sessions supported by
     * this Manager.
     */
    public boolean getDistributable() {
        return (this.distributable);
    }

    /**
     * Set the distributable flag for the sessions supported by this
     * Manager.  If this flag is set, all user data objects added to
     * sessions associated with this manager must implement Serializable.
     *
     * @param distributable The new distributable flag
     */
    public void setDistributable(boolean distributable) {
        this.distributable = distributable;
    }

    /**
     * Return the default maximum inactive interval (in seconds)
     * for Sessions created by this Manager.
     */
    public int getSessionTimeout() {
        return (this.maxInactiveInterval);
    }

    public void setSessionTimeout(int stout) {
        maxInactiveInterval = stout;
    }

    /**
     * Gets the number of sessions that have expired.
     *
     * @return Number of sessions that have expired
     */
    public int getExpiredSessions() {
        return expiredSessions;
    }

    /**
     * Called when a session is expired, add the time to statistics
     */
    public void addExpiredSession(int timeAlive) {
        synchronized (this) {
            this.expiredSessions++; // should be atomic
            // not sure it's the best solution
            sessionAverageAliveTime = 
                ((sessionAverageAliveTime * (expiredSessions-1)) + 
                        timeAlive)/expiredSessions;
            if (timeAlive > sessionMaxAliveTime) {
                sessionMaxAliveTime = timeAlive;
            }
        }
    }

    public long getProcessingTime() {
        return processingTime;
    }

    private void enableTimer() {
        if (active) {
            return;
        }
        active = true;
        timer.scheduleAtFixedRate(task, getSessionTimeout(), getSessionTimeout());
    }

    
    
    /**
     * Invalidate all sessions that have expired.
     */
    public void processExpires() {

        long timeNow = System.currentTimeMillis();
        HttpSessionImpl sessions[] = findSessions();
        int expireHere = 0 ;
        
        if(log.isLoggable(Level.FINE))
            log.fine("Start expire sessions "  + " at " + timeNow + " sessioncount " + sessions.length);
        
        for (int i = 0; i < sessions.length; i++) {
            if (!sessions[i].isValid()) {
                expiredSessions++;
                expireHere++;
            }
        }
        
        long timeEnd = System.currentTimeMillis();
        if(log.isLoggable(Level.FINE))
             log.fine("End expire sessions " + " processingTime " + 
                       (timeEnd - timeNow) + " expired sessions: " + expireHere);
        
        processingTime += ( timeEnd - timeNow );

    }

    public SimpleSessionManager() {
        randomG.init();        
    }
    
    public void destroy() {
        initialized=false;
    }

    /**
     * Add this Session to the set of active Sessions for this Manager.
     *
     * @param session Session to be added
     */
    public void add(HttpSessionImpl session) {
        synchronized (sessions) {
            sessions.put(session.getId(), session);
            if( sessions.size() > maxActive ) {
                maxActive=sessions.size();
            }
            
            // Make sure the timer is set.
            enableTimer();
        }
    }

    
    /**
     * Construct and return a new session object, based on the default
     * settings specified by this Manager's properties.  The session
     * id specified will be used as the session id.  
     * If a new session cannot be created for any reason, return 
     * <code>null</code>.
     * 
     * @param sessionId The session id which should be used to create the
     *  new session; if <code>null</code>, a new session id will be
     *  generated
     * @exception IllegalStateException if a new session cannot be
     *  instantiated for any reason
     */
    public HttpSessionImpl createSession(String sessionId) {
        
        // Recycle or create a Session instance
        HttpSessionImpl session = createEmptySession();

        // Initialize the properties of the new session and return it
        session.setNew(true);
        session.setValid(true);
        session.setCreationTime(System.currentTimeMillis());
        session.setMaxInactiveInterval(this.maxInactiveInterval);
        if (sessionId == null ) {
            while (sessionId == null) {
                sessionId = randomG.generateSessionId();
                if (sessions.get(sessionId) != null) {
                    duplicates++;
                    sessionId = null;    
                }
            }
        }
/*        } else {
        // FIXME: Code to be used in case route replacement is needed
            String jvmRoute = randomG.jvmRoute;
            if (jvmRoute != null) {
                String requestJvmRoute = null;
                int index = sessionId.indexOf(".");
                if (index > 0) {
                    requestJvmRoute = sessionId
                            .substring(index + 1, sessionId.length());
                }
                if (requestJvmRoute != null && !requestJvmRoute.equals(jvmRoute)) {
                    sessionId = sessionId.substring(0, index) + "." + jvmRoute;
                }
            }
*/
        session.setId(sessionId);
        sessionCounter++;
        return (session);
    }
    
    
    /**
     * Get a session from the recycled ones or create a new empty one.
     * The PersistentManager manager does not need to create session data
     * because it reads it from the Store.
     */
    public HttpSessionImpl createEmptySession() {
        return new HttpSessionImpl(this);
    }


    /**
     * Return the active Session, associated with this Manager, with the
     * specified session id (if any); otherwise return <code>null</code>.
     *
     * @param id The session id for the session to be returned
     *
     * @exception IllegalStateException if a new session cannot be
     *  instantiated for any reason
     * @exception IOException if an input/output error occurs while
     *  processing this request
     */
    public HttpSessionImpl findSession(String id) throws IOException {

        if (id == null)
            return (null);
        synchronized (sessions) {
            HttpSessionImpl session = (HttpSessionImpl) sessions.get(id);
            return (session);
        }

    }


    /**
     * Return the set of active Sessions associated with this Manager.
     * If this Manager has no active Sessions, a zero-length array is returned.
     */
    public HttpSessionImpl[] findSessions() {

        HttpSessionImpl results[] = null;
        synchronized (sessions) {
            results = new HttpSessionImpl[sessions.size()];
            results = (HttpSessionImpl[]) sessions.values().toArray(results);
        }
        return (results);

    }


    /**
     * Remove this Session from the active Sessions for this Manager.
     *
     * @param session Session to be removed
     */
    public void remove(HttpSessionImpl session) {

        synchronized (sessions) {
            sessions.remove(session.getId());
        }

    }

    // ------------------------------------------------------ Protected Methods

    /** JMX and debugging
     */
    public void expireSession( String sessionId ) {
        HttpSessionImpl s=(HttpSessionImpl)sessions.get(sessionId);
        if( s==null ) {
            return;
        }
        s.expire();
    }


    /** JMX method or debugging
     */
    public String getLastAccessedTime( String sessionId ) {
        HttpSessionImpl s=(HttpSessionImpl)sessions.get(sessionId);
        if( s==null ) {
            return "";
        }
        return new Date(s.getLastAccessedTime()).toString();
    }

    ThreadLocal httpSession;
    
    public String getSessionCookieName() {
        return "JSESSIONID";
    }
    
    
    /** Parse the cookies. Since multiple session cookies could be set ( 
     * different paths for example ), we need to lookup and find a valid one
     * for our context. 
     * 
     * If none is found - the last (bad) session id is returned.
     * 
     * As side effect, an attribute is set on the req with the session ( we 
     * already looked it up while searching ).
     */
    public HttpSession getRequestedSessionId(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        String cn = getSessionCookieName();
        for (int i=0; i<cookies.length; i++) {
            if (cn.equals(cookies[i].getName())) {
                String id = cookies[i].getValue();
                // TODO: mark session from cookie, check validity
            }
        }
        return null;
    }
    
    public String getSessionIdFromUrl(HttpServletRequest req) {
        
        return null;
    }
    
    
    public boolean isValid(HttpSession session) {
      return ((HttpSessionImpl) session).isValid();
    }

    public void endAccess(HttpSession session) {
      ((HttpSessionImpl) session).endAccess();
    }

    public void access(HttpSession session) {
      ((HttpSessionImpl) session).access();
    }
}
