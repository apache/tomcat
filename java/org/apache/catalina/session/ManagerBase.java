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
package org.apache.catalina.session;


import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.catalina.util.SessionIdGenerator;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;


/**
 * Minimal implementation of the <b>Manager</b> interface that supports
 * no session persistence or distributable capabilities.  This class may
 * be subclassed to create more sophisticated Manager implementations.
 *
 * @author Craig R. McClanahan
 */
public abstract class ManagerBase extends LifecycleMBeanBase
        implements Manager, PropertyChangeListener {

    private final Log log = LogFactory.getLog(ManagerBase.class); // must not be static

    // ----------------------------------------------------- Instance Variables

    /**
     * The Context with which this Manager is associated.
     */
    private Context context;


    /**
     * The distributable flag for Sessions created by this Manager.  If this
     * flag is set to <code>true</code>, any user attributes added to a
     * session controlled by this Manager must be Serializable.
     */
    protected boolean distributable;


    /**
     * The descriptive name of this Manager implementation (for logging).
     */
    private static final String name = "ManagerBase";


    /**
     * The default maximum inactive interval for Sessions created by
     * this Manager.
     */
    protected int maxInactiveInterval = 30 * 60;


    /**
     * The session id length of Sessions created by this Manager.
     */
    protected int sessionIdLength = 16;


    /**
     * The Java class name of the secure random number generator class to be
     * used when generating session identifiers. The random number generator
     * class must be self-seeding and have a zero-argument constructor. If not
     * specified, an instance of {@link java.security.SecureRandom} will be
     * generated.
     */
    protected String secureRandomClass = null;

    /**
     * The name of the algorithm to use to create instances of
     * {@link java.security.SecureRandom} which are used to generate session IDs.
     * If no algorithm is specified, SHA1PRNG is used. To use the platform
     * default (which may be SHA1PRNG), specify the empty string. If an invalid
     * algorithm and/or provider is specified the SecureRandom instances will be
     * created using the defaults. If that fails, the SecureRandom instances
     * will be created using platform defaults.
     */
    protected String secureRandomAlgorithm = "SHA1PRNG";

    /**
     * The name of the provider to use to create instances of
     * {@link java.security.SecureRandom} which are used to generate session IDs.
     * If no algorithm is specified the of SHA1PRNG default is used. If an
     * invalid algorithm and/or provider is specified the SecureRandom instances
     * will be created using the defaults. If that fails, the SecureRandom
     * instances will be created using platform defaults.
     */
    protected String secureRandomProvider = null;

    protected SessionIdGenerator sessionIdGenerator = null;

    /**
     * The longest time (in seconds) that an expired session had been alive.
     */
    protected volatile int sessionMaxAliveTime;
    private final Object sessionMaxAliveTimeUpdateLock = new Object();


    protected static final int TIMING_STATS_CACHE_SIZE = 100;

    protected final Deque<SessionTiming> sessionCreationTiming =
            new LinkedList<>();

    protected final Deque<SessionTiming> sessionExpirationTiming =
            new LinkedList<>();

    /**
     * Number of sessions that have expired.
     */
    protected final AtomicLong expiredSessions = new AtomicLong(0);


    /**
     * The set of currently active Sessions for this Manager, keyed by
     * session identifier.
     */
    protected Map<String, Session> sessions = new ConcurrentHashMap<>();

    // Number of sessions created by this manager
    protected long sessionCounter=0;

    protected volatile int maxActive=0;

    private final Object maxActiveUpdateLock = new Object();

    /**
     * The maximum number of active Sessions allowed, or -1 for no limit.
     */
    protected int maxActiveSessions = -1;

    /**
     * Number of session creations that failed due to maxActiveSessions.
     */
    protected int rejectedSessions = 0;

    // number of duplicated session ids - anything >0 means we have problems
    protected volatile int duplicates=0;

    /**
     * Processing time during session expiration.
     */
    protected long processingTime = 0;

    /**
     * Iteration count for background processing.
     */
    private int count = 0;


    /**
     * Frequency of the session expiration, and related manager operations.
     * Manager operations will be done once for the specified amount of
     * backgrondProcess calls (ie, the lower the amount, the most often the
     * checks will occur).
     */
    protected int processExpiresFrequency = 6;

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);

    /**
     * The property change support for this component.
     */
    protected final PropertyChangeSupport support =
            new PropertyChangeSupport(this);


    // ------------------------------------------------------------- Properties

    @Override
    @Deprecated
    public Container getContainer() {
        return getContext();
    }


    @Override
    @Deprecated
    public void setContainer(Container container) {

        if (container instanceof Context || container == null) {
            setContext((Context) container);
        } else {
            log.warn(sm.getString("managerBase.container.noop"));
        }
    }


    @Override
    public Context getContext() {
        return context;
    }


    @Override
    public void setContext(Context context) {
        // De-register from the old Context (if any)
        if (this.context != null) {
            this.context.removePropertyChangeListener(this);
        }

        Context oldContext = this.context;
        this.context = context;
        support.firePropertyChange("context", oldContext, this.context);
        // TODO - delete the line below in Tomcat 9 onwards
        support.firePropertyChange("container", oldContext, this.context);

        // Register with the new Context (if any)
        if (this.context != null) {
            setMaxInactiveInterval(this.context.getSessionTimeout() * 60);
            this.context.addPropertyChangeListener(this);
        }
    }


    /** Returns the name of the implementation class.
     */
    public String getClassName() {
        return this.getClass().getName();
    }


    /**
     * Return the distributable flag for the sessions supported by
     * this Manager.
     */
    @Override
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
    @Override
    public void setDistributable(boolean distributable) {

        boolean oldDistributable = this.distributable;
        this.distributable = distributable;
        support.firePropertyChange("distributable",
                                   Boolean.valueOf(oldDistributable),
                                   Boolean.valueOf(this.distributable));
    }


    /**
     * Return the default maximum inactive interval (in seconds)
     * for Sessions created by this Manager.
     */
    @Override
    public int getMaxInactiveInterval() {

        return (this.maxInactiveInterval);

    }


    /**
     * Set the default maximum inactive interval (in seconds)
     * for Sessions created by this Manager.
     *
     * @param interval The new default value
     */
    @Override
    public void setMaxInactiveInterval(int interval) {

        int oldMaxInactiveInterval = this.maxInactiveInterval;
        this.maxInactiveInterval = interval;
        support.firePropertyChange("maxInactiveInterval",
                                   Integer.valueOf(oldMaxInactiveInterval),
                                   Integer.valueOf(this.maxInactiveInterval));

    }


    /**
     * Gets the session id length (in bytes) of Sessions created by
     * this Manager.
     *
     * @return The session id length
     */
    @Override
    public int getSessionIdLength() {

        return (this.sessionIdLength);

    }


    /**
     * Sets the session id length (in bytes) for Sessions created by this
     * Manager.
     *
     * @param idLength The session id length
     */
    @Override
    public void setSessionIdLength(int idLength) {

        int oldSessionIdLength = this.sessionIdLength;
        this.sessionIdLength = idLength;
        support.firePropertyChange("sessionIdLength",
                                   Integer.valueOf(oldSessionIdLength),
                                   Integer.valueOf(this.sessionIdLength));

    }


    /**
     * Return the descriptive short name of this Manager implementation.
     */
    public String getName() {

        return (name);

    }

    /**
     * Return the secure random number generator class name.
     */
    public String getSecureRandomClass() {

        return (this.secureRandomClass);

    }


    /**
     * Set the secure random number generator class name.
     *
     * @param secureRandomClass The new secure random number generator class
     *                          name
     */
    public void setSecureRandomClass(String secureRandomClass) {

        String oldSecureRandomClass = this.secureRandomClass;
        this.secureRandomClass = secureRandomClass;
        support.firePropertyChange("secureRandomClass", oldSecureRandomClass,
                                   this.secureRandomClass);

    }


    /**
     * Return the secure random number generator algorithm name.
     */
    public String getSecureRandomAlgorithm() {
        return secureRandomAlgorithm;
    }


    /**
     * Set the secure random number generator algorithm name.
     *
     * @param secureRandomAlgorithm The new secure random number generator
     *                              algorithm name
     */
    public void setSecureRandomAlgorithm(String secureRandomAlgorithm) {
        this.secureRandomAlgorithm = secureRandomAlgorithm;
    }


    /**
     * Return the secure random number generator provider name.
     */
    public String getSecureRandomProvider() {
        return secureRandomProvider;
    }


    /**
     * Set the secure random number generator provider name.
     *
     * @param secureRandomProvider The new secure random number generator
     *                             provider name
     */
    public void setSecureRandomProvider(String secureRandomProvider) {
        this.secureRandomProvider = secureRandomProvider;
    }


    /**
     * Number of session creations that failed due to maxActiveSessions
     *
     * @return The count
     */
    @Override
    public int getRejectedSessions() {
        return rejectedSessions;
    }

    /**
     * Gets the number of sessions that have expired.
     *
     * @return Number of sessions that have expired
     */
    @Override
    public long getExpiredSessions() {
        return expiredSessions.get();
    }


    /**
     * Sets the number of sessions that have expired.
     *
     * @param expiredSessions Number of sessions that have expired
     */
    @Override
    public void setExpiredSessions(long expiredSessions) {
        this.expiredSessions.set(expiredSessions);
    }

    public long getProcessingTime() {
        return processingTime;
    }


    public void setProcessingTime(long processingTime) {
        this.processingTime = processingTime;
    }

    /**
     * Return the frequency of manager checks.
     */
    public int getProcessExpiresFrequency() {

        return (this.processExpiresFrequency);

    }

    /**
     * Set the manager checks frequency.
     *
     * @param processExpiresFrequency the new manager checks frequency
     */
    public void setProcessExpiresFrequency(int processExpiresFrequency) {

        if (processExpiresFrequency <= 0) {
            return;
        }

        int oldProcessExpiresFrequency = this.processExpiresFrequency;
        this.processExpiresFrequency = processExpiresFrequency;
        support.firePropertyChange("processExpiresFrequency",
                                   Integer.valueOf(oldProcessExpiresFrequency),
                                   Integer.valueOf(this.processExpiresFrequency));

    }
    // --------------------------------------------------------- Public Methods


    /**
     * Implements the Manager interface, direct call to processExpires
     */
    @Override
    public void backgroundProcess() {
        count = (count + 1) % processExpiresFrequency;
        if (count == 0)
            processExpires();
    }

    /**
     * Invalidate all sessions that have expired.
     */
    public void processExpires() {

        long timeNow = System.currentTimeMillis();
        Session sessions[] = findSessions();
        int expireHere = 0 ;

        if(log.isDebugEnabled())
            log.debug("Start expire sessions " + getName() + " at " + timeNow + " sessioncount " + sessions.length);
        for (int i = 0; i < sessions.length; i++) {
            if (sessions[i]!=null && !sessions[i].isValid()) {
                expireHere++;
            }
        }
        long timeEnd = System.currentTimeMillis();
        if(log.isDebugEnabled())
             log.debug("End expire sessions " + getName() + " processingTime " + (timeEnd - timeNow) + " expired sessions: " + expireHere);
        processingTime += ( timeEnd - timeNow );

    }

    @Override
    protected void initInternal() throws LifecycleException {

        super.initInternal();

        setDistributable(getContext().getDistributable());
    }

    @Override
    protected void startInternal() throws LifecycleException {

        // Ensure caches for timing stats are the right size by filling with
        // nulls.
        while (sessionCreationTiming.size() < TIMING_STATS_CACHE_SIZE) {
            sessionCreationTiming.add(null);
        }
        while (sessionExpirationTiming.size() < TIMING_STATS_CACHE_SIZE) {
            sessionExpirationTiming.add(null);
        }

        sessionIdGenerator = new SessionIdGenerator();
        sessionIdGenerator.setJvmRoute(getJvmRoute());
        sessionIdGenerator.setSecureRandomAlgorithm(getSecureRandomAlgorithm());
        sessionIdGenerator.setSecureRandomClass(getSecureRandomClass());
        sessionIdGenerator.setSecureRandomProvider(getSecureRandomProvider());
        sessionIdGenerator.setSessionIdLength(getSessionIdLength());

        // Force initialization of the random number generator
        if (log.isDebugEnabled())
            log.debug("Force random number initialization starting");
        sessionIdGenerator.generateSessionId();
        if (log.isDebugEnabled())
            log.debug("Force random number initialization completed");
    }

    @Override
    protected void stopInternal() throws LifecycleException {
        this.sessionIdGenerator = null;
    }


    /**
     * Add this Session to the set of active Sessions for this Manager.
     *
     * @param session Session to be added
     */
    @Override
    public void add(Session session) {

        sessions.put(session.getIdInternal(), session);
        int size = getActiveSessions();
        if( size > maxActive ) {
            synchronized(maxActiveUpdateLock) {
                if( size > maxActive ) {
                    maxActive = size;
                }
            }
        }
    }


    /**
     * Add a property change listener to this component.
     *
     * @param listener The listener to add
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {

        support.addPropertyChangeListener(listener);

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
    @Override
    public Session createSession(String sessionId) {

        if ((maxActiveSessions >= 0) &&
                (getActiveSessions() >= maxActiveSessions)) {
            rejectedSessions++;
            throw new TooManyActiveSessionsException(
                    sm.getString("managerBase.createSession.ise"),
                    maxActiveSessions);
        }

        // Recycle or create a Session instance
        Session session = createEmptySession();

        // Initialize the properties of the new session and return it
        session.setNew(true);
        session.setValid(true);
        session.setCreationTime(System.currentTimeMillis());
        session.setMaxInactiveInterval(this.maxInactiveInterval);
        String id = sessionId;
        if (id == null) {
            id = generateSessionId();
        }
        session.setId(id);
        sessionCounter++;

        SessionTiming timing = new SessionTiming(session.getCreationTime(), 0);
        synchronized (sessionCreationTiming) {
            sessionCreationTiming.add(timing);
            sessionCreationTiming.poll();
        }
        return (session);

    }


    /**
     * Get a session from the recycled ones or create a new empty one.
     * The PersistentManager manager does not need to create session data
     * because it reads it from the Store.
     */
    @Override
    public Session createEmptySession() {
        return (getNewSession());
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
    @Override
    public Session findSession(String id) throws IOException {

        if (id == null)
            return (null);
        return sessions.get(id);

    }


    /**
     * Return the set of active Sessions associated with this Manager.
     * If this Manager has no active Sessions, a zero-length array is returned.
     */
    @Override
    public Session[] findSessions() {

        return sessions.values().toArray(new Session[0]);

    }


    /**
     * Remove this Session from the active Sessions for this Manager.
     *
     * @param session Session to be removed
     */
    @Override
    public void remove(Session session) {
        remove(session, false);
    }

    /**
     * Remove this Session from the active Sessions for this Manager.
     *
     * @param session   Session to be removed
     * @param update    Should the expiration statistics be updated
     */
    @Override
    public void remove(Session session, boolean update) {

        // If the session has expired - as opposed to just being removed from
        // the manager because it is being persisted - update the expired stats
        if (update) {
            long timeNow = System.currentTimeMillis();
            int timeAlive =
                (int) (timeNow - session.getCreationTimeInternal())/1000;
            updateSessionMaxAliveTime(timeAlive);
            expiredSessions.incrementAndGet();
            SessionTiming timing = new SessionTiming(timeNow, timeAlive);
            synchronized (sessionExpirationTiming) {
                sessionExpirationTiming.add(timing);
                sessionExpirationTiming.poll();
            }
        }

        if (session.getIdInternal() != null) {
            sessions.remove(session.getIdInternal());
        }
    }


    /**
     * Remove a property change listener from this component.
     *
     * @param listener The listener to remove
     */
    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {

        support.removePropertyChangeListener(listener);

    }


    /**
     * Change the session ID of the current session to a new randomly generated
     * session ID.
     *
     * @param session   The session to change the session ID for
     */
    @Override
    public void changeSessionId(Session session) {
        String newId = generateSessionId();
        changeSessionId(session, newId, true, true);
    }

    /**
     * Change the session ID of the current session to a specified session ID.
     *
     * @param session   The session to change the session ID for
     * @param newId   new session ID
     */
    @Override
    public void changeSessionId(Session session, String newId) {
        changeSessionId(session, newId, true, true);
    }

    protected void changeSessionId(Session session, String newId,
            boolean notifySessionListeners, boolean notifyContainerListeners) {
        String oldId = session.getIdInternal();
        session.setId(newId, false);
        session.tellChangedSessionId(newId, oldId,
                notifySessionListeners, notifyContainerListeners);
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Get new session class to be used in the doLoad() method.
     */
    protected StandardSession getNewSession() {
        return new StandardSession(this);
    }


    /**
     * Generate and return a new session identifier.
     */
    protected String generateSessionId() {

        String result = null;

        do {
            if (result != null) {
                // Not thread-safe but if one of multiple increments is lost
                // that is not a big deal since the fact that there was any
                // duplicate is a much bigger issue.
                duplicates++;
            }

            result = sessionIdGenerator.generateSessionId();

        } while (sessions.containsKey(result));

        return result;
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Retrieve the enclosing Engine for this Manager.
     *
     * @return an Engine object (or null).
     */
    public Engine getEngine() {
        Engine e = null;
        for (Container c = getContext(); e == null && c != null ; c = c.getParent()) {
            if (c instanceof Engine) {
                e = (Engine)c;
            }
        }
        return e;
    }


    /**
     * Retrieve the JvmRoute for the enclosing Engine.
     * @return the JvmRoute or null.
     */
    public String getJvmRoute() {
        Engine e = getEngine();
        return e == null ? null : e.getJvmRoute();
    }


    // -------------------------------------------------------- Package Methods


    @Override
    public void setSessionCounter(long sessionCounter) {
        this.sessionCounter = sessionCounter;
    }


    /**
     * Total sessions created by this manager.
     *
     * @return sessions created
     */
    @Override
    public long getSessionCounter() {
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


    public void setDuplicates(int duplicates) {
        this.duplicates = duplicates;
    }


    /**
     * Returns the number of active sessions
     *
     * @return number of sessions active
     */
    @Override
    public int getActiveSessions() {
        return sessions.size();
    }


    /**
     * Max number of concurrent active sessions
     *
     * @return The highest number of concurrent active sessions
     */
    @Override
    public int getMaxActive() {
        return maxActive;
    }


    @Override
    public void setMaxActive(int maxActive) {
        synchronized (maxActiveUpdateLock) {
            this.maxActive = maxActive;
        }
    }


    /**
     * Return the maximum number of active Sessions allowed, or -1 for
     * no limit.
     */
    public int getMaxActiveSessions() {

        return (this.maxActiveSessions);

    }


    /**
     * Set the maximum number of active Sessions allowed, or -1 for
     * no limit.
     *
     * @param max The new maximum number of sessions
     */
    public void setMaxActiveSessions(int max) {

        int oldMaxActiveSessions = this.maxActiveSessions;
        this.maxActiveSessions = max;
        support.firePropertyChange("maxActiveSessions",
                                   Integer.valueOf(oldMaxActiveSessions),
                                   Integer.valueOf(this.maxActiveSessions));

    }


    /**
     * Gets the longest time (in seconds) that an expired session had been
     * alive.
     *
     * @return Longest time (in seconds) that an expired session had been
     * alive.
     */
    @Override
    public int getSessionMaxAliveTime() {
        return sessionMaxAliveTime;
    }


    /**
     * Sets the longest time (in seconds) that an expired session had been
     * alive. Typically used for resetting the current value.
     *
     * @param sessionMaxAliveTime Longest time (in seconds) that an expired
     * session had been alive.
     */
    @Override
    public void setSessionMaxAliveTime(int sessionMaxAliveTime) {
        synchronized (sessionMaxAliveTimeUpdateLock) {
            this.sessionMaxAliveTime = sessionMaxAliveTime;
        }
    }


    /**
     * Updates the sessionMaxAliveTime attribute if the candidate value is
     * larger than the current value.
     *
     * @param sessionAliveTime  The candidate value (in seconds) for the new
     *                          sessionMaxAliveTime value.
     */
    public void updateSessionMaxAliveTime(int sessionAliveTime) {
        if (sessionAliveTime > this.sessionMaxAliveTime) {
            synchronized (sessionMaxAliveTimeUpdateLock) {
                if (sessionAliveTime > this.sessionMaxAliveTime) {
                    this.sessionMaxAliveTime = sessionAliveTime;
                }
            }
        }
    }

    /**
     * Gets the average time (in seconds) that expired sessions had been
     * alive based on the last 100 sessions to expire. If less than
     * 100 sessions have expired then all available data is used.
     *
     * @return Average time (in seconds) that expired sessions had been
     * alive.
     */
    @Override
    public int getSessionAverageAliveTime() {
        // Copy current stats
        List<SessionTiming> copy = new ArrayList<>();
        synchronized (sessionExpirationTiming) {
            copy.addAll(sessionExpirationTiming);
        }

        // Init
        int counter = 0;
        int result = 0;
        Iterator<SessionTiming> iter = copy.iterator();

        // Calculate average
        while (iter.hasNext()) {
            SessionTiming timing = iter.next();
            if (timing != null) {
                int timeAlive = timing.getDuration();
                counter++;
                // Very careful not to overflow - probably not necessary
                result =
                    (result * ((counter - 1)/counter)) + (timeAlive/counter);
            }
        }
        return result;
    }


    /**
     * Gets the current rate of session creation (in session per minute) based
     * on the creation time of the previous 100 sessions created. If less than
     * 100 sessions have been created then all available data is used.
     *
     * @return  The current rate (in sessions per minute) of session creation
     */
    @Override
    public int getSessionCreateRate() {
        long now = System.currentTimeMillis();
        // Copy current stats
        List<SessionTiming> copy = new ArrayList<>();
        synchronized (sessionCreationTiming) {
            copy.addAll(sessionCreationTiming);
        }

        // Init
        long oldest = now;
        int counter = 0;
        int result = 0;
        Iterator<SessionTiming> iter = copy.iterator();

        // Calculate rate
        while (iter.hasNext()) {
            SessionTiming timing = iter.next();
            if (timing != null) {
                counter++;
                if (timing.getTimestamp() < oldest) {
                    oldest = timing.getTimestamp();
                }
            }
        }
        if (counter > 0) {
            if (oldest < now) {
                result = (1000*60*counter)/(int) (now - oldest);
            } else {
                result = Integer.MAX_VALUE;
            }
        }
        return result;
    }


    /**
     * Gets the current rate of session expiration (in session per minute) based
     * on the expiry time of the previous 100 sessions expired. If less than
     * 100 sessions have expired then all available data is used.
     *
     * @return  The current rate (in sessions per minute) of session expiration
     */
    @Override
    public int getSessionExpireRate() {
        long now = System.currentTimeMillis();
        // Copy current stats
        List<SessionTiming> copy = new ArrayList<>();
        synchronized (sessionExpirationTiming) {
            copy.addAll(sessionExpirationTiming);
        }

        // Init
        long oldest = now;
        int counter = 0;
        int result = 0;
        Iterator<SessionTiming> iter = copy.iterator();

        // Calculate rate
        while (iter.hasNext()) {
            SessionTiming timing = iter.next();
            if (timing != null) {
                counter++;
                if (timing.getTimestamp() < oldest) {
                    oldest = timing.getTimestamp();
                }
            }
        }
        if (counter > 0) {
            if (oldest < now) {
                result = (1000*60*counter)/(int) (now - oldest);
            } else {
                // Better than reporting zero
                result = Integer.MAX_VALUE;
            }
        }
        return result;
    }


    /**
     * For debugging: return a list of all session ids currently active
     *
     */
    public String listSessionIds() {
        StringBuilder sb=new StringBuilder();
        Iterator<String> keys = sessions.keySet().iterator();
        while (keys.hasNext()) {
            sb.append(keys.next()).append(" ");
        }
        return sb.toString();
    }


    /**
     * For debugging: get a session attribute
     *
     * @param sessionId
     * @param key
     * @return The attribute value, if found, null otherwise
     */
    public String getSessionAttribute( String sessionId, String key ) {
        Session s = sessions.get(sessionId);
        if( s==null ) {
            if(log.isInfoEnabled())
                log.info("Session not found " + sessionId);
            return null;
        }
        Object o=s.getSession().getAttribute(key);
        if( o==null ) return null;
        return o.toString();
    }


    /**
     * Returns information about the session with the given session id.
     *
     * <p>The session information is organized as a HashMap, mapping
     * session attribute names to the String representation of their values.
     *
     * @param sessionId Session id
     *
     * @return HashMap mapping session attribute names to the String
     * representation of their values, or null if no session with the
     * specified id exists, or if the session does not have any attributes
     */
    public HashMap<String, String> getSession(String sessionId) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            if (log.isInfoEnabled()) {
                log.info("Session not found " + sessionId);
            }
            return null;
        }

        Enumeration<String> ee = s.getSession().getAttributeNames();
        if (ee == null || !ee.hasMoreElements()) {
            return null;
        }

        HashMap<String, String> map = new HashMap<>();
        while (ee.hasMoreElements()) {
            String attrName = ee.nextElement();
            map.put(attrName, getSessionAttribute(sessionId, attrName));
        }

        return map;
    }


    public void expireSession( String sessionId ) {
        Session s=sessions.get(sessionId);
        if( s==null ) {
            if(log.isInfoEnabled())
                log.info("Session not found " + sessionId);
            return;
        }
        s.expire();
    }

    public long getThisAccessedTimestamp( String sessionId ) {
        Session s=sessions.get(sessionId);
        if(s== null)
            return -1 ;
        return s.getThisAccessedTime();
    }

    public String getThisAccessedTime( String sessionId ) {
        Session s=sessions.get(sessionId);
        if( s==null ) {
            if(log.isInfoEnabled())
                log.info("Session not found " + sessionId);
            return "";
        }
        return new Date(s.getThisAccessedTime()).toString();
    }

    public long getLastAccessedTimestamp( String sessionId ) {
        Session s=sessions.get(sessionId);
        if(s== null)
            return -1 ;
        return s.getLastAccessedTime();
    }

    public String getLastAccessedTime( String sessionId ) {
        Session s=sessions.get(sessionId);
        if( s==null ) {
            if(log.isInfoEnabled())
                log.info("Session not found " + sessionId);
            return "";
        }
        return new Date(s.getLastAccessedTime()).toString();
    }

    public String getCreationTime( String sessionId ) {
        Session s=sessions.get(sessionId);
        if( s==null ) {
            if(log.isInfoEnabled())
                log.info("Session not found " + sessionId);
            return "";
        }
        return new Date(s.getCreationTime()).toString();
    }

    public long getCreationTimestamp( String sessionId ) {
        Session s=sessions.get(sessionId);
        if(s== null)
            return -1 ;
        return s.getCreationTime();
    }


    /**
     * Return a String rendering of this object.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.getClass().getName());
        sb.append('[');
        if (context == null) {
            sb.append("Context is null");
        } else {
            sb.append(context.getName());
        }
        sb.append(']');
        return sb.toString();
    }


    // -------------------- JMX and Registration  --------------------
    @Override
    public String getObjectNameKeyProperties() {

        StringBuilder name = new StringBuilder("type=Manager");

        name.append(",host=");
        name.append(context.getParent().getName());

        name.append(",context=");
        String contextName = context.getName();
        if (!contextName.startsWith("/")) {
            name.append('/');
        }
        name.append(contextName);

        return name.toString();
    }

    @Override
    public String getDomainInternal() {
        return context.getDomain();
    }

    // ----------------------------------------- PropertyChangeListener Methods

    /**
     * Process property change events from our associated Context.
     *
     * @param event
     *            The property change event that has occurred
     */
    @Override
    public void propertyChange(PropertyChangeEvent event) {

        // Validate the source of this event
        if (!(event.getSource() instanceof Context))
            return;

        // Process a relevant property change
        if (event.getPropertyName().equals("sessionTimeout")) {
            try {
                setMaxInactiveInterval(
                        ((Integer) event.getNewValue()).intValue() * 60);
            } catch (NumberFormatException e) {
                log.error(sm.getString("managerBase.sessionTimeout",
                        event.getNewValue()));
            }
        }
    }

    // ----------------------------------------------------------- Inner classes

    protected static final class SessionTiming {
        private final long timestamp;
        private final int duration;

        public SessionTiming(long timestamp, int duration) {
            this.timestamp = timestamp;
            this.duration = duration;
        }

        /**
         * Time stamp associated with this piece of timing information in
         * milliseconds.
         */
        public long getTimestamp() {
            return timestamp;
        }

        /**
         * Duration associated with this piece of timing information in seconds.
         */
        public int getDuration() {
            return duration;
        }
    }
}
