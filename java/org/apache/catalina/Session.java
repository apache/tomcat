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


import java.security.Principal;
import java.util.Iterator;

import jakarta.servlet.http.HttpSession;


/**
 * A <b>Session</b> is the Catalina-internal facade for an <code>HttpSession</code> that is used to maintain state
 * information between requests for a particular user of a web application.
 *
 * @author Craig R. McClanahan
 */
public interface Session {


    // ----------------------------------------------------- Manifest Constants


    /**
     * The SessionEvent event type when a session is created.
     */
    String SESSION_CREATED_EVENT = "createSession";


    /**
     * The SessionEvent event type when a session is destroyed.
     */
    String SESSION_DESTROYED_EVENT = "destroySession";


    /**
     * The SessionEvent event type when a session is activated.
     */
    String SESSION_ACTIVATED_EVENT = "activateSession";


    /**
     * The SessionEvent event type when a session is passivated.
     */
    String SESSION_PASSIVATED_EVENT = "passivateSession";


    // ------------------------------------------------------------- Properties


    /**
     * @return the authentication type used to authenticate our cached Principal, if any.
     */
    String getAuthType();


    /**
     * Set the authentication type used to authenticate our cached Principal, if any.
     *
     * @param authType The new cached authentication type
     */
    void setAuthType(String authType);


    /**
     * @return the creation time for this session.
     */
    long getCreationTime();


    /**
     * @return the creation time for this session, bypassing the session validity checks.
     */
    long getCreationTimeInternal();


    /**
     * Set the creation time for this session. This method is called by the Manager when an existing Session instance is
     * reused.
     *
     * @param time The new creation time
     */
    void setCreationTime(long time);


    /**
     * @return the session identifier for this session.
     */
    String getId();


    /**
     * @return the session identifier for this session.
     */
    String getIdInternal();


    /**
     * Set the session identifier for this session and notifies any associated listeners that a new session has been
     * created.
     *
     * @param id The new session identifier
     */
    void setId(String id);


    /**
     * Set the session identifier for this session and optionally notifies any associated listeners that a new session
     * has been created.
     *
     * @param id     The new session identifier
     * @param notify Should any associated listeners be notified that a new session has been created?
     */
    void setId(String id, boolean notify);


    /**
     * @return the last time the client sent a request associated with this session, as the number of milliseconds since
     *             midnight, January 1, 1970 GMT. Actions that your application takes, such as getting or setting a
     *             value associated with the session, do not affect the access time. This one gets updated whenever a
     *             request starts.
     */
    long getThisAccessedTime();

    /**
     * @return the last client access time without invalidation check
     *
     * @see #getThisAccessedTime()
     */
    long getThisAccessedTimeInternal();

    /**
     * @return the last time the client sent a request associated with this session, as the number of milliseconds since
     *             midnight, January 1, 1970 GMT. Actions that your application takes, such as getting or setting a
     *             value associated with the session, do not affect the access time. This one gets updated whenever a
     *             request finishes.
     */
    long getLastAccessedTime();

    /**
     * @return the last client access time without invalidation check
     *
     * @see #getLastAccessedTime()
     */
    long getLastAccessedTimeInternal();

    /**
     * @return the idle time (in milliseconds) from last client access time.
     */
    long getIdleTime();

    /**
     * @return the idle time from last client access time without invalidation check
     *
     * @see #getIdleTime()
     */
    long getIdleTimeInternal();

    /**
     * @return the Manager within which this Session is valid.
     */
    Manager getManager();


    /**
     * Set the Manager within which this Session is valid.
     *
     * @param manager The new Manager
     */
    void setManager(Manager manager);


    /**
     * @return the maximum time interval, in seconds, between client requests before the servlet container will
     *             invalidate the session. A negative time indicates that the session should never time out.
     */
    int getMaxInactiveInterval();


    /**
     * Set the maximum time interval, in seconds, between client requests before the servlet container will invalidate
     * the session. A negative time indicates that the session should never time out.
     *
     * @param interval The new maximum interval
     */
    void setMaxInactiveInterval(int interval);


    /**
     * Returns whether the session was created during the current request.
     *
     * @return {@code true} if the session was created during the current request.
     */
    boolean isNew();


    /**
     * Set the <code>isNew</code> flag for this session.
     *
     * @param isNew The new value for the <code>isNew</code> flag
     */
    void setNew(boolean isNew);


    /**
     * @return the authenticated Principal that is associated with this Session. This provides an
     *             <code>Authenticator</code> with a means to cache a previously authenticated Principal, and avoid
     *             potentially expensive <code>Realm.authenticate()</code> calls on every request. If there is no
     *             current associated Principal, return <code>null</code>.
     */
    Principal getPrincipal();


    /**
     * Set the authenticated Principal that is associated with this Session. This provides an <code>Authenticator</code>
     * with a means to cache a previously authenticated Principal, and avoid potentially expensive
     * <code>Realm.authenticate()</code> calls on every request.
     *
     * @param principal The new Principal, or <code>null</code> if none
     */
    void setPrincipal(Principal principal);


    /**
     * @return the <code>HttpSession</code> for which this object is the facade.
     */
    HttpSession getSession();


    /**
     * Set the <code>isValid</code> flag for this session.
     *
     * @param isValid The new value for the <code>isValid</code> flag
     */
    void setValid(boolean isValid);


    /**
     * @return <code>true</code> if the session is still valid
     */
    boolean isValid();


    // --------------------------------------------------------- Public Methods


    /**
     * Update the accessed time information for this session. This method should be called by the context when a request
     * comes in for a particular session, even if the application does not reference it.
     */
    void access();


    /**
     * Add a session event listener to this component.
     *
     * @param listener the SessionListener instance that should be notified for session events
     */
    void addSessionListener(SessionListener listener);


    /**
     * End access to the session.
     */
    void endAccess();


    /**
     * Perform the internal processing required to invalidate this session, without triggering an exception if the
     * session has already expired.
     */
    void expire();


    /**
     * @return the object bound with the specified name to the internal notes for this session, or <code>null</code> if
     *             no such binding exists.
     *
     * @param name Name of the note to be returned
     */
    Object getNote(String name);


    /**
     * @return an Iterator containing the String names of all notes bindings that exist for this session.
     */
    Iterator<String> getNoteNames();


    /**
     * Release all object references, and initialize instance variables, in preparation for reuse of this object.
     */
    void recycle();


    /**
     * Remove any object bound to the specified name in the internal notes for this session.
     *
     * @param name Name of the note to be removed
     */
    void removeNote(String name);


    /**
     * Remove a session event listener from this component.
     *
     * @param listener remove the session listener, which will no longer be notified
     */
    void removeSessionListener(SessionListener listener);


    /**
     * Bind an object to a specified name in the internal notes associated with this session, replacing any existing
     * binding for this name.
     *
     * @param name  Name to which the object should be bound
     * @param value Object to be bound to the specified name
     */
    void setNote(String name, Object value);


    /**
     * Inform the listeners about the change session ID.
     *
     * @param newId                    new session ID
     * @param oldId                    old session ID
     * @param notifySessionListeners   Should any associated sessionListeners be notified that session ID has been
     *                                     changed?
     * @param notifyContainerListeners Should any associated ContainerListeners be notified that session ID has been
     *                                     changed?
     */
    void tellChangedSessionId(String newId, String oldId, boolean notifySessionListeners,
            boolean notifyContainerListeners);


    /**
     * Does the session implementation support the distributing of the given attribute? If the Manager is marked as
     * distributable, then this method must be used to check attributes before adding them to a session and an
     * {@link IllegalArgumentException} thrown if the proposed attribute is not distributable.
     * <p>
     * Note that the {@link Manager} implementation may further restrict which attributes are distributed but a
     * {@link Manager} level restriction should not trigger an {@link IllegalArgumentException} in
     * {@link HttpSession#setAttribute(String, Object)}
     *
     * @param name  The attribute name
     * @param value The attribute value
     *
     * @return {@code true} if distribution is supported, otherwise {@code
     *         false}
     */
    boolean isAttributeDistributable(String name, Object value);
}
