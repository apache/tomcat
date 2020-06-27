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
package org.apache.catalina;

import java.beans.PropertyChangeListener;
import java.io.IOException;

/**
 * A <b>Manager</b> manages the pool of Sessions that are associated with a
 * particular Context. Different Manager implementations may support
 * value-added features such as the persistent storage of session data,
 * as well as migrating sessions for distributable web applications.
 * <p>
 * In order for a <code>Manager</code> implementation to successfully operate
 * with a <code>Context</code> implementation that implements reloading, it
 * must obey the following constraints:
 * <ul>
 * <li>Must implement <code>Lifecycle</code> so that the Context can indicate
 *     that a restart is required.
 * <li>Must allow a call to <code>stop()</code> to be followed by a call to
 *     <code>start()</code> on the same <code>Manager</code> instance.
 * </ul>
 *
 * @author Craig R. McClanahan
 */
public interface Manager {

    // ------------------------------------------------------------- Properties

    /**
     * Get the Context with which this Manager is associated.
     *
     * @return The associated Context
     */
    public Context getContext();


    /**
     * Set the Context with which this Manager is associated. The Context must
     * be set to a non-null value before the Manager is first used. Multiple
     * calls to this method before first use are permitted. Once the Manager has
     * been used, this method may not be used to change the Context (including
     * setting a {@code null} value) that the Manager is associated with.
     *
     * @param context The newly associated Context
     */
    public void setContext(Context context);


    /**
     * @return the session id generator
     */
    public SessionIdGenerator getSessionIdGenerator();


    /**
     * Sets the session id generator
     *
     * @param sessionIdGenerator The session id generator
     */
    public void setSessionIdGenerator(SessionIdGenerator sessionIdGenerator);


    /**
     * Returns the total number of sessions created by this manager.
     *
     * @return Total number of sessions created by this manager.
     */
    public long getSessionCounter();


    /**
     * Sets the total number of sessions created by this manager.
     *
     * @param sessionCounter Total number of sessions created by this manager.
     */
    public void setSessionCounter(long sessionCounter);


    /**
     * Gets the maximum number of sessions that have been active at the same
     * time.
     *
     * @return Maximum number of sessions that have been active at the same
     * time
     */
    public int getMaxActive();


    /**
     * (Re)sets the maximum number of sessions that have been active at the
     * same time.
     *
     * @param maxActive Maximum number of sessions that have been active at
     * the same time.
     */
    public void setMaxActive(int maxActive);


    /**
     * Gets the number of currently active sessions.
     *
     * @return Number of currently active sessions
     */
    public int getActiveSessions();


    /**
     * Gets the number of sessions that have expired.
     *
     * @return Number of sessions that have expired
     */
    public long getExpiredSessions();


    /**
     * Sets the number of sessions that have expired.
     *
     * @param expiredSessions Number of sessions that have expired
     */
    public void setExpiredSessions(long expiredSessions);


    /**
     * Gets the number of sessions that were not created because the maximum
     * number of active sessions was reached.
     *
     * @return Number of rejected sessions
     */
    public int getRejectedSessions();


    /**
     * Gets the longest time (in seconds) that an expired session had been
     * alive.
     *
     * @return Longest time (in seconds) that an expired session had been
     * alive.
     */
    public int getSessionMaxAliveTime();


    /**
     * Sets the longest time (in seconds) that an expired session had been
     * alive.
     *
     * @param sessionMaxAliveTime Longest time (in seconds) that an expired
     * session had been alive.
     */
    public void setSessionMaxAliveTime(int sessionMaxAliveTime);


    /**
     * Gets the average time (in seconds) that expired sessions had been
     * alive. This may be based on sample data.
     *
     * @return Average time (in seconds) that expired sessions had been
     * alive.
     */
    public int getSessionAverageAliveTime();


    /**
     * Gets the current rate of session creation (in session per minute). This
     * may be based on sample data.
     *
     * @return  The current rate (in sessions per minute) of session creation
     */
    public int getSessionCreateRate();


    /**
     * Gets the current rate of session expiration (in session per minute). This
     * may be based on sample data
     *
     * @return  The current rate (in sessions per minute) of session expiration
     */
    public int getSessionExpireRate();


    // --------------------------------------------------------- Public Methods

    /**
     * Add this Session to the set of active Sessions for this Manager.
     *
     * @param session Session to be added
     */
    public void add(Session session);


    /**
     * Add a property change listener to this component.
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener);


    /**
     * Change the session ID of the current session to a new randomly generated
     * session ID.
     *
     * @param session   The session to change the session ID for
     *
     * @return  The new session ID
     */
    public default String rotateSessionId(Session session) {
        String newSessionId = null;
        // Assume there new Id is a duplicate until we prove it isn't. The
        // chances of a duplicate are extremely low but the current ManagerBase
        // code protects against duplicates so this default method does too.
        boolean duplicate = true;
        do {
            newSessionId = getSessionIdGenerator().generateSessionId();
            try {
                if (findSession(newSessionId) == null) {
                    duplicate = false;
                }
            } catch (IOException ioe) {
                // Swallow. An IOE means the ID was known so continue looping
            }
        } while (duplicate);
        changeSessionId(session, newSessionId);
        return newSessionId;
    }


    /**
     * Change the session ID of the current session to a specified session ID.
     *
     * @param session   The session to change the session ID for
     * @param newId   new session ID
     */
    public void changeSessionId(Session session, String newId);


    /**
     * Get a session from the recycled ones or create a new empty one.
     * The PersistentManager manager does not need to create session data
     * because it reads it from the Store.
     *
     * @return An empty Session object
     */
    public Session createEmptySession();


    /**
     * Construct and return a new session object, based on the default
     * settings specified by this Manager's properties.  The session
     * id specified will be used as the session id.
     * If a new session cannot be created for any reason, return
     * <code>null</code>.
     *
     * @param sessionId The session id which should be used to create the
     *  new session; if <code>null</code>, the session
     *  id will be assigned by this method, and available via the getId()
     *  method of the returned session.
     * @exception IllegalStateException if a new session cannot be
     *  instantiated for any reason
     *
     * @return An empty Session object with the given ID or a newly created
     *         session ID if none was specified
     */
    public Session createSession(String sessionId);


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
     *
     * @return the request session or {@code null} if a session with the
     *         requested ID could not be found
     */
    public Session findSession(String id) throws IOException;


    /**
     * Return the set of active Sessions associated with this Manager.
     * If this Manager has no active Sessions, a zero-length array is returned.
     *
     * @return All the currently active sessions managed by this manager
     */
    public Session[] findSessions();


    /**
     * Load any currently active sessions that were previously unloaded
     * to the appropriate persistence mechanism, if any.  If persistence is not
     * supported, this method returns without doing anything.
     *
     * @exception ClassNotFoundException if a serialized class cannot be
     *  found during the reload
     * @exception IOException if an input/output error occurs
     */
    public void load() throws ClassNotFoundException, IOException;


    /**
     * Remove this Session from the active Sessions for this Manager.
     *
     * @param session Session to be removed
     */
    public void remove(Session session);


    /**
     * Remove this Session from the active Sessions for this Manager.
     *
     * @param session   Session to be removed
     * @param update    Should the expiration statistics be updated
     */
    public void remove(Session session, boolean update);


    /**
     * Remove a property change listener from this component.
     *
     * @param listener The listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener);


    /**
     * Save any currently active sessions in the appropriate persistence
     * mechanism, if any.  If persistence is not supported, this method
     * returns without doing anything.
     *
     * @exception IOException if an input/output error occurs
     */
    public void unload() throws IOException;


    /**
     * This method will be invoked by the context/container on a periodic
     * basis and allows the manager to implement
     * a method that executes periodic tasks, such as expiring sessions etc.
     */
    public void backgroundProcess();


    /**
     * Would the Manager distribute the given session attribute? Manager
     * implementations may provide additional configuration options to control
     * which attributes are distributable.
     *
     * @param name  The attribute name
     * @param value The attribute value
     *
     * @return {@code true} if the Manager would distribute the given attribute
     *         otherwise {@code false}
     */
    public boolean willAttributeDistribute(String name, Object value);


    /**
     * When an attribute that is already present in the session is added again
     * under the same name and the attribute implements {@link
     * jakarta.servlet.http.HttpSessionBindingListener}, should
     * {@link jakarta.servlet.http.HttpSessionBindingListener#valueUnbound(jakarta.servlet.http.HttpSessionBindingEvent)}
     * be called followed by
     * {@link jakarta.servlet.http.HttpSessionBindingListener#valueBound(jakarta.servlet.http.HttpSessionBindingEvent)}?
     * <p>
     * The default value is {@code false}.
     *
     * @return {@code true} if the listener will be notified, {@code false} if
     *         it will not
     */
    public default boolean getNotifyBindingListenerOnUnchangedValue() {
        return false;
    }


    /**
     * Configure if
     * {@link jakarta.servlet.http.HttpSessionBindingListener#valueUnbound(jakarta.servlet.http.HttpSessionBindingEvent)}
     * be called followed by
     * {@link jakarta.servlet.http.HttpSessionBindingListener#valueBound(jakarta.servlet.http.HttpSessionBindingEvent)}
     * when an attribute that is already present in the session is added again
     * under the same name and the attribute implements {@link
     * jakarta.servlet.http.HttpSessionBindingListener}.
     *
     * @param notifyBindingListenerOnUnchangedValue {@code true} the listener
     *                                              will be called, {@code
     *                                              false} it will not
     */
    public void setNotifyBindingListenerOnUnchangedValue(
            boolean notifyBindingListenerOnUnchangedValue);


    /**
     * When an attribute that is already present in the session is added again
     * under the same name and a {@link
     * jakarta.servlet.http.HttpSessionAttributeListener} is configured for the
     * session should
     * {@link jakarta.servlet.http.HttpSessionAttributeListener#attributeReplaced(jakarta.servlet.http.HttpSessionBindingEvent)}
     * be called?
     * <p>
     * The default value is {@code true}.
     *
     * @return {@code true} if the listener will be notified, {@code false} if
     *         it will not
     */
    public default boolean getNotifyAttributeListenerOnUnchangedValue() {
        return true;
    }


    /**
     * Configure if
     * {@link jakarta.servlet.http.HttpSessionAttributeListener#attributeReplaced(jakarta.servlet.http.HttpSessionBindingEvent)}
     * when an attribute that is already present in the session is added again
     * under the same name and a {@link
     * jakarta.servlet.http.HttpSessionAttributeListener} is configured for the
     * session.
     *
     * @param notifyAttributeListenerOnUnchangedValue {@code true} the listener
     *                                                will be called, {@code
     *                                                false} it will not
     */
    public void setNotifyAttributeListenerOnUnchangedValue(
            boolean notifyAttributeListenerOnUnchangedValue);


    /**
     * If this is <code>true</code>, Tomcat will track the number of active
     * requests for each session. When determining if a session is valid, any
     * session with at least one active request will always be considered valid.
     * If <code>org.apache.catalina.STRICT_SERVLET_COMPLIANCE</code> is set to
     * <code>true</code>, the default of this setting will be <code>true</code>,
     * else the default value will be <code>false</code>.
     * @return the flag value
     */
    public default boolean getSessionActivityCheck() {
        return Globals.STRICT_SERVLET_COMPLIANCE;
    }


    /**
     * Configure if Tomcat will track the number of active requests for each
     * session. When determining if a session is valid, any session with at
     * least one active request will always be considered valid.
     * @param sessionActivityCheck the new flag value
     */
    public void setSessionActivityCheck(boolean sessionActivityCheck);


    /**
     * If this is <code>true</code>, the last accessed time for sessions will
     * be calculated from the beginning of the previous request. If
     * <code>false</code>, the last accessed time for sessions will be calculated
     * from the end of the previous request. This also affects how the idle time
     * is calculated.
     * If <code>org.apache.catalina.STRICT_SERVLET_COMPLIANCE</code> is set to
     * <code>true</code>, the default of this setting will be <code>true</code>,
     * else the default value will be <code>false</code>.
     * @return the flag value
     */
    public default boolean getSessionLastAccessAtStart() {
        return Globals.STRICT_SERVLET_COMPLIANCE;
    }


    /**
     * Configure if the last accessed time for sessions will
     * be calculated from the beginning of the previous request. If
     * <code>false</code>, the last accessed time for sessions will be calculated
     * from the end of the previous request. This also affects how the idle time
     * is calculated.
     * @param sessionLastAccessAtStart the new flag value
     */
    public void setSessionLastAccessAtStart(boolean sessionLastAccessAtStart);

}
