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


import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.SessionIdGenerator;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.catalina.util.SessionIdGeneratorBase;
import org.apache.catalina.util.StandardSessionIdGenerator;
import org.apache.catalina.util.ToStringUtil;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;


/**
 * Minimal implementation of the <b>Manager</b> interface that supports no session persistence or distributable
 * capabilities. This class may be subclassed to create more sophisticated Manager implementations.
 *
 * @author Craig R. McClanahan
 */
public abstract class ManagerBase extends LifecycleMBeanBase implements Manager {

    private final Log log = LogFactory.getLog(ManagerBase.class); // must not be static

    // ----------------------------------------------------- Instance Variables

    /**
     * The Context with which this Manager is associated.
     */
    private Context context;


    /**
     * The descriptive name of this Manager implementation (for logging).
     */
    private static final String name = "ManagerBase";


    /**
     * The Java class name of the secure random number generator class to be used when generating session identifiers.
     * The random number generator class must be self-seeding and have a zero-argument constructor. If not specified, an
     * instance of {@link java.security.SecureRandom} will be generated.
     */
    protected String secureRandomClass = null;

    /**
     * The name of the algorithm to use to create instances of {@link java.security.SecureRandom} which are used to
     * generate session IDs. If no algorithm is specified, SHA1PRNG is used. If SHA1PRNG is not available, the platform
     * default will be used. To use the platform default (which may be SHA1PRNG), specify the empty string. If an
     * invalid algorithm and/or provider is specified the SecureRandom instances will be created using the defaults. If
     * that fails, the SecureRandom instances will be created using platform defaults.
     */
    protected String secureRandomAlgorithm = SessionIdGeneratorBase.DEFAULT_SECURE_RANDOM_ALGORITHM;

    /**
     * The name of the provider to use to create instances of {@link java.security.SecureRandom} which are used to
     * generate session IDs. If no provider is specified the platform default is used. If an invalid algorithm and/or
     * provider is specified the SecureRandom instances will be created using the defaults. If that fails, the
     * SecureRandom instances will be created using platform defaults.
     */
    protected String secureRandomProvider = null;

    protected SessionIdGenerator sessionIdGenerator = null;
    protected Class<? extends SessionIdGenerator> sessionIdGeneratorClass = null;

    /**
     * The longest time (in seconds) that an expired session had been alive.
     */
    protected volatile int sessionMaxAliveTime;
    private final Object sessionMaxAliveTimeUpdateLock = new Object();


    protected static final int TIMING_STATS_CACHE_SIZE = 100;

    // Use LinkedList as the Deques are initialised by filling with null
    protected final Deque<SessionTiming> sessionCreationTiming = new LinkedList<>();
    protected final Deque<SessionTiming> sessionExpirationTiming = new LinkedList<>();

    /**
     * Number of sessions that have expired.
     */
    protected final AtomicLong expiredSessions = new AtomicLong(0);


    /**
     * The set of currently active Sessions for this Manager, keyed by session identifier.
     */
    protected Map<String,Session> sessions = new ConcurrentHashMap<>();

    protected volatile int maxActive = 0;

    private final Object maxActiveUpdateLock = new Object();

    /**
     * The maximum number of active Sessions allowed, or -1 for no limit.
     */
    protected int maxActiveSessions = -1;

    /**
     * Number of session creations that failed due to maxActiveSessions.
     */
    protected int rejectedSessions = 0;

    /**
     * Processing time during session expiration.
     */
    protected long processingTime = 0;

    /**
     * Iteration count for background processing.
     */
    private int count = 0;


    /**
     * Frequency of the session expiration, and related manager operations. Manager operations will be done once for the
     * specified amount of backgroundProcess calls (ie, the lower the amount, the most often the checks will occur).
     */
    protected int processExpiresFrequency = 6;

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(ManagerBase.class);

    /**
     * The property change support for this component.
     */
    protected final PropertyChangeSupport support = new PropertyChangeSupport(this);

    private Pattern sessionAttributeNamePattern;

    private Pattern sessionAttributeValueClassNamePattern;

    private boolean warnOnSessionAttributeFilterFailure;

    private boolean notifyBindingListenerOnUnchangedValue;

    private boolean notifyAttributeListenerOnUnchangedValue = true;

    /**
     * Determines whether sessions managed by this manager shall persist (serialize) authentication information or not.
     */
    private boolean persistAuthentication = false;

    private boolean sessionActivityCheck = Globals.STRICT_SERVLET_COMPLIANCE;

    private boolean sessionLastAccessAtStart = Globals.STRICT_SERVLET_COMPLIANCE;

    // -------------------------------------------------------------- Properties

    @Override
    public boolean getNotifyAttributeListenerOnUnchangedValue() {
        return notifyAttributeListenerOnUnchangedValue;
    }


    @Override
    public void setNotifyAttributeListenerOnUnchangedValue(boolean notifyAttributeListenerOnUnchangedValue) {
        this.notifyAttributeListenerOnUnchangedValue = notifyAttributeListenerOnUnchangedValue;
    }


    @Override
    public boolean getNotifyBindingListenerOnUnchangedValue() {
        return notifyBindingListenerOnUnchangedValue;
    }


    @Override
    public void setNotifyBindingListenerOnUnchangedValue(boolean notifyBindingListenerOnUnchangedValue) {
        this.notifyBindingListenerOnUnchangedValue = notifyBindingListenerOnUnchangedValue;
    }


    @Override
    public boolean getSessionActivityCheck() {
        return sessionActivityCheck;
    }


    @Override
    public void setSessionActivityCheck(boolean sessionActivityCheck) {
        this.sessionActivityCheck = sessionActivityCheck;
    }


    @Override
    public boolean getSessionLastAccessAtStart() {
        return sessionLastAccessAtStart;
    }


    @Override
    public void setSessionLastAccessAtStart(boolean sessionLastAccessAtStart) {
        this.sessionLastAccessAtStart = sessionLastAccessAtStart;
    }


    /**
     * Obtain the regular expression used to filter session attribute based on attribute name. The regular expression is
     * anchored so it must match the entire name
     *
     * @return The regular expression currently used to filter attribute names. {@code null} means no filter is applied.
     *             If an empty string is specified then no names will match the filter and all attributes will be
     *             blocked.
     */
    public String getSessionAttributeNameFilter() {
        if (sessionAttributeNamePattern == null) {
            return null;
        }
        return sessionAttributeNamePattern.toString();
    }


    /**
     * Set the regular expression to use to filter session attributes based on attribute name. The regular expression is
     * anchored so it must match the entire name.
     *
     * @param sessionAttributeNameFilter The regular expression to use to filter session attributes based on attribute
     *                                       name. Use {@code null} if no filtering is required. If an empty string is
     *                                       specified then no names will match the filter and all attributes will be
     *                                       blocked.
     *
     * @throws PatternSyntaxException If the expression is not valid
     */
    public void setSessionAttributeNameFilter(String sessionAttributeNameFilter) throws PatternSyntaxException {
        if (sessionAttributeNameFilter == null || sessionAttributeNameFilter.length() == 0) {
            sessionAttributeNamePattern = null;
        } else {
            sessionAttributeNamePattern = Pattern.compile(sessionAttributeNameFilter);
        }
    }


    /**
     * Provides {@link #getSessionAttributeNameFilter()} as a pre-compiled regular expression pattern.
     *
     * @return The pre-compiled pattern used to filter session attributes based on attribute name. {@code null} means no
     *             filter is applied.
     */
    protected Pattern getSessionAttributeNamePattern() {
        return sessionAttributeNamePattern;
    }


    /**
     * Obtain the regular expression used to filter session attribute based on the implementation class of the value.
     * The regular expression is anchored and must match the fully qualified class name.
     *
     * @return The regular expression currently used to filter class names. {@code null} means no filter is applied. If
     *             an empty string is specified then no names will match the filter and all attributes will be blocked.
     */
    public String getSessionAttributeValueClassNameFilter() {
        if (sessionAttributeValueClassNamePattern == null) {
            return null;
        }
        return sessionAttributeValueClassNamePattern.toString();
    }


    /**
     * Provides {@link #getSessionAttributeValueClassNameFilter()} as a pre-compiled regular expression pattern.
     *
     * @return The pre-compiled pattern used to filter session attributes based on the implementation class name of the
     *             value. {@code null} means no filter is applied.
     */
    protected Pattern getSessionAttributeValueClassNamePattern() {
        return sessionAttributeValueClassNamePattern;
    }


    /**
     * Set the regular expression to use to filter classes used for session attributes. The regular expression is
     * anchored and must match the fully qualified class name.
     *
     * @param sessionAttributeValueClassNameFilter The regular expression to use to filter session attributes based on
     *                                                 class name. Use {@code
     *            null}                         if no filtering is required. If an empty string is specified then no
     *                                                 names will match the filter and all attributes will be blocked.
     *
     * @throws PatternSyntaxException If the expression is not valid
     */
    public void setSessionAttributeValueClassNameFilter(String sessionAttributeValueClassNameFilter)
            throws PatternSyntaxException {
        if (sessionAttributeValueClassNameFilter == null || sessionAttributeValueClassNameFilter.length() == 0) {
            sessionAttributeValueClassNamePattern = null;
        } else {
            sessionAttributeValueClassNamePattern = Pattern.compile(sessionAttributeValueClassNameFilter);
        }
    }


    /**
     * Should a warn level log message be generated if a session attribute is not persisted / replicated / restored.
     *
     * @return {@code true} if a warn level log message should be generated
     */
    public boolean getWarnOnSessionAttributeFilterFailure() {
        return warnOnSessionAttributeFilterFailure;
    }


    /**
     * Configure whether or not a warn level log message should be generated if a session attribute is not persisted /
     * replicated / restored.
     *
     * @param warnOnSessionAttributeFilterFailure {@code true} if the warn level message should be generated
     */
    public void setWarnOnSessionAttributeFilterFailure(boolean warnOnSessionAttributeFilterFailure) {
        this.warnOnSessionAttributeFilterFailure = warnOnSessionAttributeFilterFailure;
    }


    @Override
    public Context getContext() {
        return context;
    }


    @Override
    public void setContext(Context context) {
        if (this.context == context) {
            // NO-OP
            return;
        }
        if (!getState().equals(LifecycleState.NEW)) {
            throw new IllegalStateException(sm.getString("managerBase.setContextNotNew"));
        }
        Context oldContext = this.context;
        this.context = context;
        support.firePropertyChange("context", oldContext, this.context);
    }


    /**
     * @return The name of the implementation class.
     */
    public String getClassName() {
        return this.getClass().getName();
    }


    @Override
    public SessionIdGenerator getSessionIdGenerator() {
        if (sessionIdGenerator != null) {
            return sessionIdGenerator;
        } else if (sessionIdGeneratorClass != null) {
            try {
                sessionIdGenerator = sessionIdGeneratorClass.getConstructor().newInstance();
                return sessionIdGenerator;
            } catch (ReflectiveOperationException ex) {
                // Ignore
            }
        }
        return null;
    }


    @Override
    public void setSessionIdGenerator(SessionIdGenerator sessionIdGenerator) {
        this.sessionIdGenerator = sessionIdGenerator;
        sessionIdGeneratorClass = sessionIdGenerator.getClass();
    }


    /**
     * @return The descriptive short name of this Manager implementation.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The secure random number generator class name.
     */
    public String getSecureRandomClass() {
        return this.secureRandomClass;
    }


    /**
     * Set the secure random number generator class name.
     *
     * @param secureRandomClass The new secure random number generator class name
     */
    public void setSecureRandomClass(String secureRandomClass) {

        String oldSecureRandomClass = this.secureRandomClass;
        this.secureRandomClass = secureRandomClass;
        support.firePropertyChange("secureRandomClass", oldSecureRandomClass, this.secureRandomClass);

    }


    /**
     * @return The secure random number generator algorithm name.
     */
    public String getSecureRandomAlgorithm() {
        return secureRandomAlgorithm;
    }


    /**
     * Set the secure random number generator algorithm name.
     *
     * @param secureRandomAlgorithm The new secure random number generator algorithm name
     */
    public void setSecureRandomAlgorithm(String secureRandomAlgorithm) {
        this.secureRandomAlgorithm = secureRandomAlgorithm;
    }


    /**
     * @return The secure random number generator provider name.
     */
    public String getSecureRandomProvider() {
        return secureRandomProvider;
    }


    /**
     * Set the secure random number generator provider name.
     *
     * @param secureRandomProvider The new secure random number generator provider name
     */
    public void setSecureRandomProvider(String secureRandomProvider) {
        this.secureRandomProvider = secureRandomProvider;
    }


    @Override
    public int getRejectedSessions() {
        return rejectedSessions;
    }


    @Override
    public long getExpiredSessions() {
        return expiredSessions.get();
    }


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
     * @return The frequency of manager checks.
     */
    public int getProcessExpiresFrequency() {
        return this.processExpiresFrequency;
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
        support.firePropertyChange("processExpiresFrequency", Integer.valueOf(oldProcessExpiresFrequency),
                Integer.valueOf(this.processExpiresFrequency));

    }


    /**
     * Return whether sessions managed by this manager shall persist authentication information or not.
     *
     * @return {@code true}, sessions managed by this manager shall persist authentication information; {@code false}
     *             otherwise
     */
    public boolean getPersistAuthentication() {
        return this.persistAuthentication;
    }

    /**
     * Set whether sessions managed by this manager shall persist authentication information or not.
     *
     * @param persistAuthentication if {@code true}, sessions managed by this manager shall persist authentication
     *                                  information
     */
    public void setPersistAuthentication(boolean persistAuthentication) {
        this.persistAuthentication = persistAuthentication;
    }


    // --------------------------------------------------------- Public Methods

    /**
     * {@inheritDoc}
     * <p>
     * Direct call to {@link #processExpires()}
     */
    @Override
    public void backgroundProcess() {
        count = (count + 1) % processExpiresFrequency;
        if (count == 0) {
            processExpires();
        }
    }

    /**
     * Invalidate all sessions that have expired.
     */
    public void processExpires() {

        long timeNow = System.currentTimeMillis();
        Session sessions[] = findSessions();
        int expireHere = 0;

        if (log.isTraceEnabled()) {
            log.trace("Start expire sessions " + getName() + " at " + timeNow + " sessioncount " + sessions.length);
        }
        for (Session session : sessions) {
            if (session != null && !session.isValid()) {
                expireHere++;
            }
        }
        long timeEnd = System.currentTimeMillis();
        if (log.isTraceEnabled()) {
            log.trace("End expire sessions " + getName() + " processingTime " + (timeEnd - timeNow) +
                    " expired sessions: " + expireHere);
        }
        processingTime += (timeEnd - timeNow);

    }


    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();

        if (context == null) {
            throw new LifecycleException(sm.getString("managerBase.contextNull"));
        }
    }


    @Override
    protected void startInternal() throws LifecycleException {

        // Ensure caches for timing stats are the right size by filling with
        // nulls.
        synchronized (sessionCreationTiming) {
            while (sessionCreationTiming.size() < TIMING_STATS_CACHE_SIZE) {
                sessionCreationTiming.add(null);
            }
        }
        synchronized (sessionExpirationTiming) {
            while (sessionExpirationTiming.size() < TIMING_STATS_CACHE_SIZE) {
                sessionExpirationTiming.add(null);
            }
        }

        /* Create sessionIdGenerator if not explicitly configured */
        SessionIdGenerator sessionIdGenerator = getSessionIdGenerator();
        if (sessionIdGenerator == null) {
            sessionIdGenerator = new StandardSessionIdGenerator();
            setSessionIdGenerator(sessionIdGenerator);
        }

        sessionIdGenerator.setJvmRoute(getJvmRoute());
        if (sessionIdGenerator instanceof SessionIdGeneratorBase) {
            SessionIdGeneratorBase sig = (SessionIdGeneratorBase) sessionIdGenerator;
            sig.setSecureRandomAlgorithm(getSecureRandomAlgorithm());
            sig.setSecureRandomClass(getSecureRandomClass());
            sig.setSecureRandomProvider(getSecureRandomProvider());
        }

        if (sessionIdGenerator instanceof Lifecycle) {
            ((Lifecycle) sessionIdGenerator).start();
        } else {
            // Force initialization of the random number generator
            if (log.isTraceEnabled()) {
                log.trace("Force random number initialization starting");
            }
            sessionIdGenerator.generateSessionId();
            if (log.isTraceEnabled()) {
                log.trace("Force random number initialization completed");
            }
        }
    }


    @Override
    protected void stopInternal() throws LifecycleException {
        if (sessionIdGenerator instanceof Lifecycle) {
            ((Lifecycle) sessionIdGenerator).stop();
        }
    }


    @Override
    public void add(Session session) {
        sessions.put(session.getIdInternal(), session);
        int size = getActiveSessions();
        if (size > maxActive) {
            synchronized (maxActiveUpdateLock) {
                if (size > maxActive) {
                    maxActive = size;
                }
            }
        }
    }


    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }


    @Override
    public Session createSession(String sessionId) {

        if ((maxActiveSessions >= 0) && (getActiveSessions() >= maxActiveSessions)) {
            rejectedSessions++;
            throw new TooManyActiveSessionsException(sm.getString("managerBase.createSession.ise"), maxActiveSessions);
        }

        // Recycle or create a Session instance
        Session session = createEmptySession();

        // Initialize the properties of the new session and return it
        session.setNew(true);
        session.setValid(true);
        session.setCreationTime(System.currentTimeMillis());
        session.setMaxInactiveInterval(getContext().getSessionTimeout() * 60);
        String id = sessionId;
        if (id == null) {
            id = generateSessionId();
        }
        session.setId(id);

        SessionTiming timing = new SessionTiming(session.getCreationTime(), 0);
        synchronized (sessionCreationTiming) {
            sessionCreationTiming.add(timing);
            sessionCreationTiming.poll();
        }
        return session;
    }


    @Override
    public Session createEmptySession() {
        return getNewSession();
    }


    @Override
    public Session findSession(String id) throws IOException {
        if (id == null) {
            return null;
        }
        return sessions.get(id);
    }


    @Override
    public Session[] findSessions() {
        return sessions.values().toArray(new Session[0]);
    }


    @Override
    public void remove(Session session) {
        remove(session, false);
    }


    @Override
    public void remove(Session session, boolean update) {
        // If the session has expired - as opposed to just being removed from
        // the manager because it is being persisted - update the expired stats
        if (update) {
            long timeNow = System.currentTimeMillis();
            int timeAlive = (int) (timeNow - session.getCreationTimeInternal()) / 1000;
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


    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }


    @Override
    public String rotateSessionId(Session session) {
        String newId = generateSessionId();
        changeSessionId(session, newId, true, true);
        return newId;
    }


    @Override
    public void changeSessionId(Session session, String newId) {
        changeSessionId(session, newId, true, true);
    }


    protected void changeSessionId(Session session, String newId, boolean notifySessionListeners,
            boolean notifyContainerListeners) {
        String oldId = session.getIdInternal();
        session.setId(newId, false);
        session.tellChangedSessionId(newId, oldId, notifySessionListeners, notifyContainerListeners);
    }


    /**
     * {@inheritDoc}
     * <p>
     * This implementation excludes session attributes from distribution if the:
     * <ul>
     * <li>attribute name matches {@link #getSessionAttributeNameFilter()}</li>
     * </ul>
     */
    @Override
    public boolean willAttributeDistribute(String name, Object value) {
        Pattern sessionAttributeNamePattern = getSessionAttributeNamePattern();
        if (sessionAttributeNamePattern != null) {
            if (!sessionAttributeNamePattern.matcher(name).matches()) {
                if (getWarnOnSessionAttributeFilterFailure() || log.isDebugEnabled()) {
                    String msg =
                            sm.getString("managerBase.sessionAttributeNameFilter", name, sessionAttributeNamePattern);
                    if (getWarnOnSessionAttributeFilterFailure()) {
                        log.warn(msg);
                    } else {
                        log.debug(msg);
                    }
                }
                return false;
            }
        }

        Pattern sessionAttributeValueClassNamePattern = getSessionAttributeValueClassNamePattern();
        if (value != null && sessionAttributeValueClassNamePattern != null) {
            if (!sessionAttributeValueClassNamePattern.matcher(value.getClass().getName()).matches()) {
                if (getWarnOnSessionAttributeFilterFailure() || log.isDebugEnabled()) {
                    String msg = sm.getString("managerBase.sessionAttributeValueClassNameFilter", name,
                            value.getClass().getName(), sessionAttributeValueClassNamePattern);
                    if (getWarnOnSessionAttributeFilterFailure()) {
                        log.warn(msg);
                    } else {
                        log.debug(msg);
                    }
                }
                return false;
            }
        }

        return true;
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Get new session class to be used in the doLoad() method.
     *
     * @return a new session for use with this manager
     */
    protected StandardSession getNewSession() {
        return new StandardSession(this);
    }


    /**
     * Generate and return a new session identifier.
     *
     * @return a new session id
     */
    protected String generateSessionId() {
        return sessionIdGenerator.generateSessionId();
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Retrieve the enclosing Engine for this Manager.
     *
     * @return an Engine object (or null).
     */
    public Engine getEngine() {
        Engine e = null;
        for (Container c = getContext(); e == null && c != null; c = c.getParent()) {
            if (c instanceof Engine) {
                e = (Engine) c;
            }
        }
        return e;
    }


    /**
     * Retrieve the JvmRoute for the enclosing Engine.
     *
     * @return the JvmRoute or null.
     */
    public String getJvmRoute() {
        Engine e = getEngine();
        return e == null ? null : e.getJvmRoute();
    }


    // -------------------------------------------------------- Package Methods


    @Override
    public long getSessionCounter() {
        return getActiveSessions() + getExpiredSessions();
    }


    @Override
    public int getActiveSessions() {
        return sessions.size();
    }


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
     * @return The maximum number of active Sessions allowed, or -1 for no limit.
     */
    public int getMaxActiveSessions() {
        return this.maxActiveSessions;
    }


    /**
     * Set the maximum number of active Sessions allowed, or -1 for no limit.
     *
     * @param max The new maximum number of sessions
     */
    public void setMaxActiveSessions(int max) {

        int oldMaxActiveSessions = this.maxActiveSessions;
        this.maxActiveSessions = max;
        support.firePropertyChange("maxActiveSessions", Integer.valueOf(oldMaxActiveSessions),
                Integer.valueOf(this.maxActiveSessions));

    }


    @Override
    public int getSessionMaxAliveTime() {
        return sessionMaxAliveTime;
    }


    @Override
    public void setSessionMaxAliveTime(int sessionMaxAliveTime) {
        synchronized (sessionMaxAliveTimeUpdateLock) {
            this.sessionMaxAliveTime = sessionMaxAliveTime;
        }
    }


    /**
     * Updates the sessionMaxAliveTime attribute if the candidate value is larger than the current value.
     *
     * @param sessionAliveTime The candidate value (in seconds) for the new sessionMaxAliveTime value.
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
     * {@inheritDoc}
     * <p>
     * Based on the last 100 sessions to expire. If less than 100 sessions have expired then all available data is used.
     */
    @Override
    public int getSessionAverageAliveTime() {
        // Copy current stats
        List<SessionTiming> copy;
        synchronized (sessionExpirationTiming) {
            copy = new ArrayList<>(sessionExpirationTiming);
        }

        // Init
        int counter = 0;
        int result = 0;

        // Calculate average
        for (SessionTiming timing : copy) {
            if (timing != null) {
                int timeAlive = timing.getDuration();
                counter++;
                // Very careful not to overflow - probably not necessary
                result = (result * ((counter - 1) / counter)) + (timeAlive / counter);
            }
        }
        return result;
    }


    /**
     * {@inheritDoc}
     * <p>
     * Based on the creation time of the previous 100 sessions created. If less than 100 sessions have been created then
     * all available data is used.
     */
    @Override
    public int getSessionCreateRate() {
        // Copy current stats
        List<SessionTiming> copy;
        synchronized (sessionCreationTiming) {
            copy = new ArrayList<>(sessionCreationTiming);
        }

        return calculateRate(copy);
    }


    /**
     * {@inheritDoc}
     * <p>
     * Based on the expiry time of the previous 100 sessions expired. If less than 100 sessions have expired then all
     * available data is used.
     *
     * @return The current rate (in sessions per minute) of session expiration
     */
    @Override
    public int getSessionExpireRate() {
        // Copy current stats
        List<SessionTiming> copy;
        synchronized (sessionExpirationTiming) {
            copy = new ArrayList<>(sessionExpirationTiming);
        }

        return calculateRate(copy);
    }


    private static int calculateRate(List<SessionTiming> sessionTiming) {
        // Init
        long now = System.currentTimeMillis();
        long oldest = now;
        int counter = 0;
        int result = 0;

        // Calculate rate
        for (SessionTiming timing : sessionTiming) {
            if (timing != null) {
                counter++;
                if (timing.getTimestamp() < oldest) {
                    oldest = timing.getTimestamp();
                }
            }
        }
        if (counter > 0) {
            if (oldest < now) {
                result = (1000 * 60 * counter) / (int) (now - oldest);
            } else {
                // Better than reporting zero
                result = Integer.MAX_VALUE;
            }
        }
        return result;
    }


    /**
     * For debugging.
     *
     * @return A space separated list of all session IDs currently active
     */
    public String listSessionIds() {
        StringBuilder sb = new StringBuilder();
        for (String s : sessions.keySet()) {
            sb.append(s).append(' ');
        }
        return sb.toString();
    }


    /**
     * For debugging.
     *
     * @param sessionId The ID for the session of interest
     * @param key       The key for the attribute to obtain
     *
     * @return The attribute value for the specified session, if found, null otherwise
     */
    public String getSessionAttribute(String sessionId, String key) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            if (log.isInfoEnabled()) {
                log.info(sm.getString("managerBase.sessionNotFound", sessionId));
            }
            return null;
        }
        Object o = s.getSession().getAttribute(key);
        if (o == null) {
            return null;
        }
        return o.toString();
    }


    /**
     * Returns information about the session with the given session id.
     * <p>
     * The session information is organized as a HashMap, mapping session attribute names to the String representation
     * of their values.
     *
     * @param sessionId Session id
     *
     * @return HashMap mapping session attribute names to the String representation of their values, or null if no
     *             session with the specified id exists, or if the session does not have any attributes
     */
    public HashMap<String,String> getSession(String sessionId) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            if (log.isInfoEnabled()) {
                log.info(sm.getString("managerBase.sessionNotFound", sessionId));
            }
            return null;
        }

        Enumeration<String> ee = s.getSession().getAttributeNames();
        if (ee == null || !ee.hasMoreElements()) {
            return null;
        }

        HashMap<String,String> map = new HashMap<>();
        while (ee.hasMoreElements()) {
            String attrName = ee.nextElement();
            map.put(attrName, getSessionAttribute(sessionId, attrName));
        }

        return map;
    }


    public void expireSession(String sessionId) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            if (log.isInfoEnabled()) {
                log.info(sm.getString("managerBase.sessionNotFound", sessionId));
            }
            return;
        }
        s.expire();
    }

    public long getThisAccessedTimestamp(String sessionId) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            if (log.isInfoEnabled()) {
                log.info(sm.getString("managerBase.sessionNotFound", sessionId));
            }
            return -1;
        }
        return s.getThisAccessedTime();
    }

    public String getThisAccessedTime(String sessionId) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            if (log.isInfoEnabled()) {
                log.info(sm.getString("managerBase.sessionNotFound", sessionId));
            }
            return "";
        }
        return new Date(s.getThisAccessedTime()).toString();
    }

    public long getLastAccessedTimestamp(String sessionId) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            if (log.isInfoEnabled()) {
                log.info(sm.getString("managerBase.sessionNotFound", sessionId));
            }
            return -1;
        }
        return s.getLastAccessedTime();
    }

    public String getLastAccessedTime(String sessionId) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            if (log.isInfoEnabled()) {
                log.info(sm.getString("managerBase.sessionNotFound", sessionId));
            }
            return "";
        }
        return new Date(s.getLastAccessedTime()).toString();
    }

    public String getCreationTime(String sessionId) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            if (log.isInfoEnabled()) {
                log.info(sm.getString("managerBase.sessionNotFound", sessionId));
            }
            return "";
        }
        return new Date(s.getCreationTime()).toString();
    }

    public long getCreationTimestamp(String sessionId) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            if (log.isInfoEnabled()) {
                log.info(sm.getString("managerBase.sessionNotFound", sessionId));
            }
            return -1;
        }
        return s.getCreationTime();
    }


    @Override
    public String toString() {
        return ToStringUtil.toString(this, context);
    }


    // -------------------- JMX and Registration --------------------
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


    // ----------------------------------------------------------- Inner classes

    protected static final class SessionTiming {
        private final long timestamp;
        private final int duration;

        public SessionTiming(long timestamp, int duration) {
            this.timestamp = timestamp;
            this.duration = duration;
        }

        /**
         * @return Time stamp associated with this piece of timing information in milliseconds.
         */
        public long getTimestamp() {
            return timestamp;
        }

        /**
         * @return Duration associated with this piece of timing information in seconds.
         */
        public int getDuration() {
            return duration;
        }
    }
}
