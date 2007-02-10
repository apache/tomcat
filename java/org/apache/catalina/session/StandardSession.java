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


import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionContext;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.SessionEvent;
import org.apache.catalina.SessionListener;
import org.apache.catalina.util.Enumerator;
import org.apache.catalina.util.StringManager;

import org.apache.catalina.security.SecurityUtil;

/**
 * Standard implementation of the <b>Session</b> interface.  This object is
 * serializable, so that it can be stored in persistent storage or transferred
 * to a different JVM for distributable session support.
 * <p>
 * <b>IMPLEMENTATION NOTE</b>:  An instance of this class represents both the
 * internal (Session) and application level (HttpSession) view of the session.
 * However, because the class itself is not declared public, Java logic outside
 * of the <code>org.apache.catalina.session</code> package cannot cast an
 * HttpSession view of this instance back to a Session view.
 * <p>
 * <b>IMPLEMENTATION NOTE</b>:  If you add fields to this class, you must
 * make sure that you carry them over in the read/writeObject methods so
 * that this class is properly serialized.
 *
 * @author Craig R. McClanahan
 * @author Sean Legassick
 * @author <a href="mailto:jon@latchkey.com">Jon S. Stevens</a>
 * @version $Revision$ $Date$
 */

public class StandardSession
    implements HttpSession, Session, Serializable {


    protected static final boolean ACTIVITY_CHECK = 
        Globals.STRICT_SERVLET_COMPLIANCE
        || Boolean.valueOf(System.getProperty("org.apache.catalina.session.StandardSession.ACTIVITY_CHECK", "false")).booleanValue();


    // ----------------------------------------------------------- Constructors


    /**
     * Construct a new Session associated with the specified Manager.
     *
     * @param manager The manager with which this Session is associated
     */
    public StandardSession(Manager manager) {

        super();
        this.manager = manager;

        // Initialize access count
        if (ACTIVITY_CHECK) {
            accessCount = new AtomicInteger();
        }

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Type array.
     */
    protected static final String EMPTY_ARRAY[] = new String[0];


    /**
     * The dummy attribute value serialized when a NotSerializableException is
     * encountered in <code>writeObject()</code>.
     */
    protected static final String NOT_SERIALIZED =
        "___NOT_SERIALIZABLE_EXCEPTION___";


    /**
     * The collection of user data attributes associated with this Session.
     */
    protected Map attributes = new ConcurrentHashMap();


    /**
     * The authentication type used to authenticate our cached Principal,
     * if any.  NOTE:  This value is not included in the serialized
     * version of this object.
     */
    protected transient String authType = null;


    /**
     * The <code>java.lang.Method</code> for the
     * <code>fireContainerEvent()</code> method of the
     * <code>org.apache.catalina.core.StandardContext</code> method,
     * if our Context implementation is of this class.  This value is
     * computed dynamically the first time it is needed, or after
     * a session reload (since it is declared transient).
     */
    protected transient Method containerEventMethod = null;


    /**
     * The method signature for the <code>fireContainerEvent</code> method.
     */
    protected static final Class containerEventTypes[] =
    { String.class, Object.class };


    /**
     * The time this session was created, in milliseconds since midnight,
     * January 1, 1970 GMT.
     */
    protected long creationTime = 0L;


    /**
     * Set of attribute names which are not allowed to be persisted.
     */
    protected static final String[] excludedAttributes = {
        Globals.SUBJECT_ATTR
    };


    /**
     * We are currently processing a session expiration, so bypass
     * certain IllegalStateException tests.  NOTE:  This value is not
     * included in the serialized version of this object.
     */
    protected transient boolean expiring = false;


    /**
     * The facade associated with this session.  NOTE:  This value is not
     * included in the serialized version of this object.
     */
    protected transient StandardSessionFacade facade = null;


    /**
     * The session identifier of this Session.
     */
    protected String id = null;


    /**
     * Descriptive information describing this Session implementation.
     */
    protected static final String info = "StandardSession/1.0";


    /**
     * The last accessed time for this Session.
     */
    protected long lastAccessedTime = creationTime;


    /**
     * The session event listeners for this Session.
     */
    protected transient ArrayList listeners = new ArrayList();


    /**
     * The Manager with which this Session is associated.
     */
    protected transient Manager manager = null;


    /**
     * The maximum time interval, in seconds, between client requests before
     * the servlet container may invalidate this session.  A negative time
     * indicates that the session should never time out.
     */
    protected int maxInactiveInterval = -1;


    /**
     * Flag indicating whether this session is new or not.
     */
    protected boolean isNew = false;


    /**
     * Flag indicating whether this session is valid or not.
     */
    protected boolean isValid = false;

    
    /**
     * Internal notes associated with this session by Catalina components
     * and event listeners.  <b>IMPLEMENTATION NOTE:</b> This object is
     * <em>not</em> saved and restored across session serializations!
     */
    protected transient Map notes = new Hashtable();


    /**
     * The authenticated Principal associated with this session, if any.
     * <b>IMPLEMENTATION NOTE:</b>  This object is <i>not</i> saved and
     * restored across session serializations!
     */
    protected transient Principal principal = null;


    /**
     * The string manager for this package.
     */
    protected static StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * The HTTP session context associated with this session.
     */
    protected static HttpSessionContext sessionContext = null;


    /**
     * The property change support for this component.  NOTE:  This value
     * is not included in the serialized version of this object.
     */
    protected transient PropertyChangeSupport support =
        new PropertyChangeSupport(this);


    /**
     * The current accessed time for this session.
     */
    protected long thisAccessedTime = creationTime;


    /**
     * The access count for this session.
     */
    protected transient AtomicInteger accessCount = null;

    
    // ----------------------------------------------------- Session Properties


    /**
     * Return the authentication type used to authenticate our cached
     * Principal, if any.
     */
    public String getAuthType() {

        return (this.authType);

    }


    /**
     * Set the authentication type used to authenticate our cached
     * Principal, if any.
     *
     * @param authType The new cached authentication type
     */
    public void setAuthType(String authType) {

        String oldAuthType = this.authType;
        this.authType = authType;
        support.firePropertyChange("authType", oldAuthType, this.authType);

    }


    /**
     * Set the creation time for this session.  This method is called by the
     * Manager when an existing Session instance is reused.
     *
     * @param time The new creation time
     */
    public void setCreationTime(long time) {

        this.creationTime = time;
        this.lastAccessedTime = time;
        this.thisAccessedTime = time;

    }


    /**
     * Return the session identifier for this session.
     */
    public String getId() {

        return (this.id);

    }


    /**
     * Return the session identifier for this session.
     */
    public String getIdInternal() {

        return (this.id);

    }


    /**
     * Set the session identifier for this session.
     *
     * @param id The new session identifier
     */
    public void setId(String id) {

        if ((this.id != null) && (manager != null))
            manager.remove(this);

        this.id = id;

        if (manager != null)
            manager.add(this);
        tellNew();
    }


    /**
     * Inform the listeners about the new session.
     *
     */
    public void tellNew() {

        // Notify interested session event listeners
        fireSessionEvent(Session.SESSION_CREATED_EVENT, null);

        // Notify interested application event listeners
        Context context = (Context) manager.getContainer();
        Object listeners[] = context.getApplicationLifecycleListeners();
        if (listeners != null) {
            HttpSessionEvent event =
                new HttpSessionEvent(getSession());
            for (int i = 0; i < listeners.length; i++) {
                if (!(listeners[i] instanceof HttpSessionListener))
                    continue;
                HttpSessionListener listener =
                    (HttpSessionListener) listeners[i];
                try {
                    fireContainerEvent(context,
                                       "beforeSessionCreated",
                                       listener);
                    listener.sessionCreated(event);
                    fireContainerEvent(context,
                                       "afterSessionCreated",
                                       listener);
                } catch (Throwable t) {
                    try {
                        fireContainerEvent(context,
                                           "afterSessionCreated",
                                           listener);
                    } catch (Exception e) {
                        ;
                    }
                    manager.getContainer().getLogger().error
                        (sm.getString("standardSession.sessionEvent"), t);
                }
            }
        }

    }


    /**
     * Return descriptive information about this Session implementation and
     * the corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {

        return (info);

    }


    /**
     * Return the last time the client sent a request associated with this
     * session, as the number of milliseconds since midnight, January 1, 1970
     * GMT.  Actions that your application takes, such as getting or setting
     * a value associated with the session, do not affect the access time.
     */
    public long getLastAccessedTime() {

        if (!isValidInternal()) {
            throw new IllegalStateException
                (sm.getString("standardSession.getLastAccessedTime.ise"));
        }

        return (this.lastAccessedTime);
    }

    /**
     * Return the last client access time without invalidation check
     * @see #getLastAccessedTime().
     */
    public long getLastAccessedTimeInternal() {
        return (this.lastAccessedTime);
    }

    /**
     * Return the Manager within which this Session is valid.
     */
    public Manager getManager() {

        return (this.manager);

    }


    /**
     * Set the Manager within which this Session is valid.
     *
     * @param manager The new Manager
     */
    public void setManager(Manager manager) {

        this.manager = manager;

    }


    /**
     * Return the maximum time interval, in seconds, between client requests
     * before the servlet container will invalidate the session.  A negative
     * time indicates that the session should never time out.
     */
    public int getMaxInactiveInterval() {

        return (this.maxInactiveInterval);

    }


    /**
     * Set the maximum time interval, in seconds, between client requests
     * before the servlet container will invalidate the session.  A negative
     * time indicates that the session should never time out.
     *
     * @param interval The new maximum interval
     */
    public void setMaxInactiveInterval(int interval) {

        this.maxInactiveInterval = interval;
        if (isValid && interval == 0) {
            expire();
        }

    }


    /**
     * Set the <code>isNew</code> flag for this session.
     *
     * @param isNew The new value for the <code>isNew</code> flag
     */
    public void setNew(boolean isNew) {

        this.isNew = isNew;

    }


    /**
     * Return the authenticated Principal that is associated with this Session.
     * This provides an <code>Authenticator</code> with a means to cache a
     * previously authenticated Principal, and avoid potentially expensive
     * <code>Realm.authenticate()</code> calls on every request.  If there
     * is no current associated Principal, return <code>null</code>.
     */
    public Principal getPrincipal() {

        return (this.principal);

    }


    /**
     * Set the authenticated Principal that is associated with this Session.
     * This provides an <code>Authenticator</code> with a means to cache a
     * previously authenticated Principal, and avoid potentially expensive
     * <code>Realm.authenticate()</code> calls on every request.
     *
     * @param principal The new Principal, or <code>null</code> if none
     */
    public void setPrincipal(Principal principal) {

        Principal oldPrincipal = this.principal;
        this.principal = principal;
        support.firePropertyChange("principal", oldPrincipal, this.principal);

    }


    /**
     * Return the <code>HttpSession</code> for which this object
     * is the facade.
     */
    public HttpSession getSession() {

        if (facade == null){
            if (SecurityUtil.isPackageProtectionEnabled()){
                final StandardSession fsession = this;
                facade = (StandardSessionFacade)AccessController.doPrivileged(new PrivilegedAction(){
                    public Object run(){
                        return new StandardSessionFacade(fsession);
                    }
                });
            } else {
                facade = new StandardSessionFacade(this);
            }
        }
        return (facade);

    }


    /**
     * Return the <code>isValid</code> flag for this session.
     */
    public boolean isValid() {

        if (this.expiring) {
            return true;
        }

        if (!this.isValid) {
            return false;
        }

        if (ACTIVITY_CHECK && accessCount.get() > 0) {
            return true;
        }

        if (maxInactiveInterval >= 0) { 
            long timeNow = System.currentTimeMillis();
            int timeIdle = (int) ((timeNow - thisAccessedTime) / 1000L);
            if (timeIdle >= maxInactiveInterval) {
                expire(true);
            }
        }

        return (this.isValid);
    }


    /**
     * Set the <code>isValid</code> flag for this session.
     *
     * @param isValid The new value for the <code>isValid</code> flag
     */
    public void setValid(boolean isValid) {
        this.isValid = isValid;
    }


    // ------------------------------------------------- Session Public Methods


    /**
     * Update the accessed time information for this session.  This method
     * should be called by the context when a request comes in for a particular
     * session, even if the application does not reference it.
     */
    public void access() {

        this.lastAccessedTime = this.thisAccessedTime;
        this.thisAccessedTime = System.currentTimeMillis();
        
        if (ACTIVITY_CHECK) {
            accessCount.incrementAndGet();
        }

    }


    /**
     * End the access.
     */
    public void endAccess() {

        isNew = false;

        if (ACTIVITY_CHECK) {
            accessCount.decrementAndGet();
        }

    }


    /**
     * Add a session event listener to this component.
     */
    public void addSessionListener(SessionListener listener) {

        listeners.add(listener);

    }


    /**
     * Perform the internal processing required to invalidate this session,
     * without triggering an exception if the session has already expired.
     */
    public void expire() {

        expire(true);

    }


    /**
     * Perform the internal processing required to invalidate this session,
     * without triggering an exception if the session has already expired.
     *
     * @param notify Should we notify listeners about the demise of
     *  this session?
     */
    public void expire(boolean notify) {

        // Mark this session as "being expired" if needed
        if (expiring)
            return;

        synchronized (this) {

            if (manager == null)
                return;

            expiring = true;
        
            // Notify interested application event listeners
            // FIXME - Assumes we call listeners in reverse order
            Context context = (Context) manager.getContainer();
            Object listeners[] = context.getApplicationLifecycleListeners();
            if (notify && (listeners != null)) {
                HttpSessionEvent event =
                    new HttpSessionEvent(getSession());
                for (int i = 0; i < listeners.length; i++) {
                    int j = (listeners.length - 1) - i;
                    if (!(listeners[j] instanceof HttpSessionListener))
                        continue;
                    HttpSessionListener listener =
                        (HttpSessionListener) listeners[j];
                    try {
                        fireContainerEvent(context,
                                           "beforeSessionDestroyed",
                                           listener);
                        listener.sessionDestroyed(event);
                        fireContainerEvent(context,
                                           "afterSessionDestroyed",
                                           listener);
                    } catch (Throwable t) {
                        try {
                            fireContainerEvent(context,
                                               "afterSessionDestroyed",
                                               listener);
                        } catch (Exception e) {
                            ;
                        }
                        manager.getContainer().getLogger().error
                            (sm.getString("standardSession.sessionEvent"), t);
                    }
                }
            }
            if (ACTIVITY_CHECK) {
                accessCount.set(0);
            }
            setValid(false);

            /*
             * Compute how long this session has been alive, and update
             * session manager's related properties accordingly
             */
            long timeNow = System.currentTimeMillis();
            int timeAlive = (int) ((timeNow - creationTime)/1000);
            synchronized (manager) {
                if (timeAlive > manager.getSessionMaxAliveTime()) {
                    manager.setSessionMaxAliveTime(timeAlive);
                }
                int numExpired = manager.getExpiredSessions();
                numExpired++;
                manager.setExpiredSessions(numExpired);
                int average = manager.getSessionAverageAliveTime();
                average = ((average * (numExpired-1)) + timeAlive)/numExpired;
                manager.setSessionAverageAliveTime(average);
            }

            // Remove this session from our manager's active sessions
            manager.remove(this);

            // Notify interested session event listeners
            if (notify) {
                fireSessionEvent(Session.SESSION_DESTROYED_EVENT, null);
            }

            // We have completed expire of this session
            expiring = false;

            // Unbind any objects associated with this session
            String keys[] = keys();
            for (int i = 0; i < keys.length; i++)
                removeAttributeInternal(keys[i], notify);

        }

    }


    /**
     * Perform the internal processing required to passivate
     * this session.
     */
    public void passivate() {

        // Notify interested session event listeners
        fireSessionEvent(Session.SESSION_PASSIVATED_EVENT, null);

        // Notify ActivationListeners
        HttpSessionEvent event = null;
        String keys[] = keys();
        for (int i = 0; i < keys.length; i++) {
            Object attribute = attributes.get(keys[i]);
            if (attribute instanceof HttpSessionActivationListener) {
                if (event == null)
                    event = new HttpSessionEvent(getSession());
                try {
                    ((HttpSessionActivationListener)attribute)
                        .sessionWillPassivate(event);
                } catch (Throwable t) {
                    manager.getContainer().getLogger().error
                        (sm.getString("standardSession.attributeEvent"), t);
                }
            }
        }

    }


    /**
     * Perform internal processing required to activate this
     * session.
     */
    public void activate() {

        // Initialize access count
        if (ACTIVITY_CHECK) {
            accessCount = new AtomicInteger();
        }
        
        // Notify interested session event listeners
        fireSessionEvent(Session.SESSION_ACTIVATED_EVENT, null);

        // Notify ActivationListeners
        HttpSessionEvent event = null;
        String keys[] = keys();
        for (int i = 0; i < keys.length; i++) {
            Object attribute = attributes.get(keys[i]);
            if (attribute instanceof HttpSessionActivationListener) {
                if (event == null)
                    event = new HttpSessionEvent(getSession());
                try {
                    ((HttpSessionActivationListener)attribute)
                        .sessionDidActivate(event);
                } catch (Throwable t) {
                    manager.getContainer().getLogger().error
                        (sm.getString("standardSession.attributeEvent"), t);
                }
            }
        }

    }


    /**
     * Return the object bound with the specified name to the internal notes
     * for this session, or <code>null</code> if no such binding exists.
     *
     * @param name Name of the note to be returned
     */
    public Object getNote(String name) {

        return (notes.get(name));

    }


    /**
     * Return an Iterator containing the String names of all notes bindings
     * that exist for this session.
     */
    public Iterator getNoteNames() {

        return (notes.keySet().iterator());

    }


    /**
     * Release all object references, and initialize instance variables, in
     * preparation for reuse of this object.
     */
    public void recycle() {

        // Reset the instance variables associated with this Session
        attributes.clear();
        setAuthType(null);
        creationTime = 0L;
        expiring = false;
        id = null;
        lastAccessedTime = 0L;
        maxInactiveInterval = -1;
        notes.clear();
        setPrincipal(null);
        isNew = false;
        isValid = false;
        manager = null;

    }


    /**
     * Remove any object bound to the specified name in the internal notes
     * for this session.
     *
     * @param name Name of the note to be removed
     */
    public void removeNote(String name) {

        notes.remove(name);

    }


    /**
     * Remove a session event listener from this component.
     */
    public void removeSessionListener(SessionListener listener) {

        listeners.remove(listener);

    }


    /**
     * Bind an object to a specified name in the internal notes associated
     * with this session, replacing any existing binding for this name.
     *
     * @param name Name to which the object should be bound
     * @param value Object to be bound to the specified name
     */
    public void setNote(String name, Object value) {

        notes.put(name, value);

    }


    /**
     * Return a string representation of this object.
     */
    public String toString() {

        StringBuffer sb = new StringBuffer();
        sb.append("StandardSession[");
        sb.append(id);
        sb.append("]");
        return (sb.toString());

    }


    // ------------------------------------------------ Session Package Methods


    /**
     * Read a serialized version of the contents of this session object from
     * the specified object input stream, without requiring that the
     * StandardSession itself have been serialized.
     *
     * @param stream The object input stream to read from
     *
     * @exception ClassNotFoundException if an unknown class is specified
     * @exception IOException if an input/output error occurs
     */
    public void readObjectData(ObjectInputStream stream)
        throws ClassNotFoundException, IOException {

        readObject(stream);

    }


    /**
     * Write a serialized version of the contents of this session object to
     * the specified object output stream, without requiring that the
     * StandardSession itself have been serialized.
     *
     * @param stream The object output stream to write to
     *
     * @exception IOException if an input/output error occurs
     */
    public void writeObjectData(ObjectOutputStream stream)
        throws IOException {

        writeObject(stream);

    }


    // ------------------------------------------------- HttpSession Properties


    /**
     * Return the time when this session was created, in milliseconds since
     * midnight, January 1, 1970 GMT.
     *
     * @exception IllegalStateException if this method is called on an
     *  invalidated session
     */
    public long getCreationTime() {

        if (!isValidInternal())
            throw new IllegalStateException
                (sm.getString("standardSession.getCreationTime.ise"));

        return (this.creationTime);

    }


    /**
     * Return the ServletContext to which this session belongs.
     */
    public ServletContext getServletContext() {

        if (manager == null)
            return (null);
        Context context = (Context) manager.getContainer();
        if (context == null)
            return (null);
        else
            return (context.getServletContext());

    }


    /**
     * Return the session context with which this session is associated.
     *
     * @deprecated As of Version 2.1, this method is deprecated and has no
     *  replacement.  It will be removed in a future version of the
     *  Java Servlet API.
     */
    public HttpSessionContext getSessionContext() {

        if (sessionContext == null)
            sessionContext = new StandardSessionContext();
        return (sessionContext);

    }


    // ----------------------------------------------HttpSession Public Methods


    /**
     * Return the object bound with the specified name in this session, or
     * <code>null</code> if no object is bound with that name.
     *
     * @param name Name of the attribute to be returned
     *
     * @exception IllegalStateException if this method is called on an
     *  invalidated session
     */
    public Object getAttribute(String name) {

        if (!isValidInternal())
            throw new IllegalStateException
                (sm.getString("standardSession.getAttribute.ise"));

        return (attributes.get(name));

    }


    /**
     * Return an <code>Enumeration</code> of <code>String</code> objects
     * containing the names of the objects bound to this session.
     *
     * @exception IllegalStateException if this method is called on an
     *  invalidated session
     */
    public Enumeration getAttributeNames() {

        if (!isValidInternal())
            throw new IllegalStateException
                (sm.getString("standardSession.getAttributeNames.ise"));

        return (new Enumerator(attributes.keySet(), true));

    }


    /**
     * Return the object bound with the specified name in this session, or
     * <code>null</code> if no object is bound with that name.
     *
     * @param name Name of the value to be returned
     *
     * @exception IllegalStateException if this method is called on an
     *  invalidated session
     *
     * @deprecated As of Version 2.2, this method is replaced by
     *  <code>getAttribute()</code>
     */
    public Object getValue(String name) {

        return (getAttribute(name));

    }


    /**
     * Return the set of names of objects bound to this session.  If there
     * are no such objects, a zero-length array is returned.
     *
     * @exception IllegalStateException if this method is called on an
     *  invalidated session
     *
     * @deprecated As of Version 2.2, this method is replaced by
     *  <code>getAttributeNames()</code>
     */
    public String[] getValueNames() {

        if (!isValidInternal())
            throw new IllegalStateException
                (sm.getString("standardSession.getValueNames.ise"));

        return (keys());

    }


    /**
     * Invalidates this session and unbinds any objects bound to it.
     *
     * @exception IllegalStateException if this method is called on
     *  an invalidated session
     */
    public void invalidate() {

        if (!isValidInternal())
            throw new IllegalStateException
                (sm.getString("standardSession.invalidate.ise"));

        // Cause this session to expire
        expire();

    }


    /**
     * Return <code>true</code> if the client does not yet know about the
     * session, or if the client chooses not to join the session.  For
     * example, if the server used only cookie-based sessions, and the client
     * has disabled the use of cookies, then a session would be new on each
     * request.
     *
     * @exception IllegalStateException if this method is called on an
     *  invalidated session
     */
    public boolean isNew() {

        if (!isValidInternal())
            throw new IllegalStateException
                (sm.getString("standardSession.isNew.ise"));

        return (this.isNew);

    }



    /**
     * Bind an object to this session, using the specified name.  If an object
     * of the same name is already bound to this session, the object is
     * replaced.
     * <p>
     * After this method executes, and if the object implements
     * <code>HttpSessionBindingListener</code>, the container calls
     * <code>valueBound()</code> on the object.
     *
     * @param name Name to which the object is bound, cannot be null
     * @param value Object to be bound, cannot be null
     *
     * @exception IllegalStateException if this method is called on an
     *  invalidated session
     *
     * @deprecated As of Version 2.2, this method is replaced by
     *  <code>setAttribute()</code>
     */
    public void putValue(String name, Object value) {

        setAttribute(name, value);

    }


    /**
     * Remove the object bound with the specified name from this session.  If
     * the session does not have an object bound with this name, this method
     * does nothing.
     * <p>
     * After this method executes, and if the object implements
     * <code>HttpSessionBindingListener</code>, the container calls
     * <code>valueUnbound()</code> on the object.
     *
     * @param name Name of the object to remove from this session.
     *
     * @exception IllegalStateException if this method is called on an
     *  invalidated session
     */
    public void removeAttribute(String name) {

        removeAttribute(name, true);

    }


    /**
     * Remove the object bound with the specified name from this session.  If
     * the session does not have an object bound with this name, this method
     * does nothing.
     * <p>
     * After this method executes, and if the object implements
     * <code>HttpSessionBindingListener</code>, the container calls
     * <code>valueUnbound()</code> on the object.
     *
     * @param name Name of the object to remove from this session.
     * @param notify Should we notify interested listeners that this
     *  attribute is being removed?
     *
     * @exception IllegalStateException if this method is called on an
     *  invalidated session
     */
    public void removeAttribute(String name, boolean notify) {

        // Validate our current state
        if (!isValidInternal())
            throw new IllegalStateException
                (sm.getString("standardSession.removeAttribute.ise"));

        removeAttributeInternal(name, notify);

    }


    /**
     * Remove the object bound with the specified name from this session.  If
     * the session does not have an object bound with this name, this method
     * does nothing.
     * <p>
     * After this method executes, and if the object implements
     * <code>HttpSessionBindingListener</code>, the container calls
     * <code>valueUnbound()</code> on the object.
     *
     * @param name Name of the object to remove from this session.
     *
     * @exception IllegalStateException if this method is called on an
     *  invalidated session
     *
     * @deprecated As of Version 2.2, this method is replaced by
     *  <code>removeAttribute()</code>
     */
    public void removeValue(String name) {

        removeAttribute(name);

    }


    /**
     * Bind an object to this session, using the specified name.  If an object
     * of the same name is already bound to this session, the object is
     * replaced.
     * <p>
     * After this method executes, and if the object implements
     * <code>HttpSessionBindingListener</code>, the container calls
     * <code>valueBound()</code> on the object.
     *
     * @param name Name to which the object is bound, cannot be null
     * @param value Object to be bound, cannot be null
     *
     * @exception IllegalArgumentException if an attempt is made to add a
     *  non-serializable object in an environment marked distributable.
     * @exception IllegalStateException if this method is called on an
     *  invalidated session
     */
    public void setAttribute(String name, Object value) {
        setAttribute(name,value,true);
    }
    /**
     * Bind an object to this session, using the specified name.  If an object
     * of the same name is already bound to this session, the object is
     * replaced.
     * <p>
     * After this method executes, and if the object implements
     * <code>HttpSessionBindingListener</code>, the container calls
     * <code>valueBound()</code> on the object.
     *
     * @param name Name to which the object is bound, cannot be null
     * @param value Object to be bound, cannot be null
     * @param notify whether to notify session listeners
     * @exception IllegalArgumentException if an attempt is made to add a
     *  non-serializable object in an environment marked distributable.
     * @exception IllegalStateException if this method is called on an
     *  invalidated session
     */

    public void setAttribute(String name, Object value, boolean notify) {

        // Name cannot be null
        if (name == null)
            throw new IllegalArgumentException
                (sm.getString("standardSession.setAttribute.namenull"));

        // Null value is the same as removeAttribute()
        if (value == null) {
            removeAttribute(name);
            return;
        }

        // Validate our current state
        if (!isValidInternal())
            throw new IllegalStateException
                (sm.getString("standardSession.setAttribute.ise"));
        if ((manager != null) && manager.getDistributable() &&
          !(value instanceof Serializable))
            throw new IllegalArgumentException
                (sm.getString("standardSession.setAttribute.iae"));

        // Construct an event with the new value
        HttpSessionBindingEvent event = null;

        // Call the valueBound() method if necessary
        if (notify && value instanceof HttpSessionBindingListener) {
            // Don't call any notification if replacing with the same value
            Object oldValue = attributes.get(name);
            if (value != oldValue) {
                event = new HttpSessionBindingEvent(getSession(), name, value);
                try {
                    ((HttpSessionBindingListener) value).valueBound(event);
                } catch (Throwable t){
                    manager.getContainer().getLogger().error
                    (sm.getString("standardSession.bindingEvent"), t); 
                }
            }
        }

        // Replace or add this attribute
        Object unbound = attributes.put(name, value);

        // Call the valueUnbound() method if necessary
        if (notify && (unbound != null) && (unbound != value) &&
            (unbound instanceof HttpSessionBindingListener)) {
            try {
                ((HttpSessionBindingListener) unbound).valueUnbound
                    (new HttpSessionBindingEvent(getSession(), name));
            } catch (Throwable t) {
                manager.getContainer().getLogger().error
                    (sm.getString("standardSession.bindingEvent"), t);
            }
        }
        
        if ( !notify ) return;
        
        // Notify interested application event listeners
        Context context = (Context) manager.getContainer();
        Object listeners[] = context.getApplicationEventListeners();
        if (listeners == null)
            return;
        for (int i = 0; i < listeners.length; i++) {
            if (!(listeners[i] instanceof HttpSessionAttributeListener))
                continue;
            HttpSessionAttributeListener listener =
                (HttpSessionAttributeListener) listeners[i];
            try {
                if (unbound != null) {
                    fireContainerEvent(context,
                                       "beforeSessionAttributeReplaced",
                                       listener);
                    if (event == null) {
                        event = new HttpSessionBindingEvent
                            (getSession(), name, unbound);
                    }
                    listener.attributeReplaced(event);
                    fireContainerEvent(context,
                                       "afterSessionAttributeReplaced",
                                       listener);
                } else {
                    fireContainerEvent(context,
                                       "beforeSessionAttributeAdded",
                                       listener);
                    if (event == null) {
                        event = new HttpSessionBindingEvent
                            (getSession(), name, value);
                    }
                    listener.attributeAdded(event);
                    fireContainerEvent(context,
                                       "afterSessionAttributeAdded",
                                       listener);
                }
            } catch (Throwable t) {
                try {
                    if (unbound != null) {
                        fireContainerEvent(context,
                                           "afterSessionAttributeReplaced",
                                           listener);
                    } else {
                        fireContainerEvent(context,
                                           "afterSessionAttributeAdded",
                                           listener);
                    }
                } catch (Exception e) {
                    ;
                }
                manager.getContainer().getLogger().error
                    (sm.getString("standardSession.attributeEvent"), t);
            }
        }

    }


    // ------------------------------------------ HttpSession Protected Methods


    /**
     * Return the <code>isValid</code> flag for this session without any expiration
     * check.
     */
    protected boolean isValidInternal() {
        return (this.isValid || this.expiring);
    }


    /**
     * Read a serialized version of this session object from the specified
     * object input stream.
     * <p>
     * <b>IMPLEMENTATION NOTE</b>:  The reference to the owning Manager
     * is not restored by this method, and must be set explicitly.
     *
     * @param stream The input stream to read from
     *
     * @exception ClassNotFoundException if an unknown class is specified
     * @exception IOException if an input/output error occurs
     */
    protected void readObject(ObjectInputStream stream)
        throws ClassNotFoundException, IOException {

        // Deserialize the scalar instance variables (except Manager)
        authType = null;        // Transient only
        creationTime = ((Long) stream.readObject()).longValue();
        lastAccessedTime = ((Long) stream.readObject()).longValue();
        maxInactiveInterval = ((Integer) stream.readObject()).intValue();
        isNew = ((Boolean) stream.readObject()).booleanValue();
        isValid = ((Boolean) stream.readObject()).booleanValue();
        thisAccessedTime = ((Long) stream.readObject()).longValue();
        principal = null;        // Transient only
        //        setId((String) stream.readObject());
        id = (String) stream.readObject();
        if (manager.getContainer().getLogger().isDebugEnabled())
            manager.getContainer().getLogger().debug
                ("readObject() loading session " + id);

        // Deserialize the attribute count and attribute values
        if (attributes == null)
            attributes = new Hashtable();
        int n = ((Integer) stream.readObject()).intValue();
        boolean isValidSave = isValid;
        isValid = true;
        for (int i = 0; i < n; i++) {
            String name = (String) stream.readObject();
            Object value = (Object) stream.readObject();
            if ((value instanceof String) && (value.equals(NOT_SERIALIZED)))
                continue;
            if (manager.getContainer().getLogger().isDebugEnabled())
                manager.getContainer().getLogger().debug("  loading attribute '" + name +
                    "' with value '" + value + "'");
            attributes.put(name, value);
        }
        isValid = isValidSave;

        if (listeners == null) {
            listeners = new ArrayList();
        }

        if (notes == null) {
            notes = new Hashtable();
        }
    }


    /**
     * Write a serialized version of this session object to the specified
     * object output stream.
     * <p>
     * <b>IMPLEMENTATION NOTE</b>:  The owning Manager will not be stored
     * in the serialized representation of this Session.  After calling
     * <code>readObject()</code>, you must set the associated Manager
     * explicitly.
     * <p>
     * <b>IMPLEMENTATION NOTE</b>:  Any attribute that is not Serializable
     * will be unbound from the session, with appropriate actions if it
     * implements HttpSessionBindingListener.  If you do not want any such
     * attributes, be sure the <code>distributable</code> property of the
     * associated Manager is set to <code>true</code>.
     *
     * @param stream The output stream to write to
     *
     * @exception IOException if an input/output error occurs
     */
    protected void writeObject(ObjectOutputStream stream) throws IOException {

        // Write the scalar instance variables (except Manager)
        stream.writeObject(new Long(creationTime));
        stream.writeObject(new Long(lastAccessedTime));
        stream.writeObject(new Integer(maxInactiveInterval));
        stream.writeObject(new Boolean(isNew));
        stream.writeObject(new Boolean(isValid));
        stream.writeObject(new Long(thisAccessedTime));
        stream.writeObject(id);
        if (manager.getContainer().getLogger().isDebugEnabled())
            manager.getContainer().getLogger().debug
                ("writeObject() storing session " + id);

        // Accumulate the names of serializable and non-serializable attributes
        String keys[] = keys();
        ArrayList saveNames = new ArrayList();
        ArrayList saveValues = new ArrayList();
        for (int i = 0; i < keys.length; i++) {
            Object value = attributes.get(keys[i]);
            if (value == null)
                continue;
            else if ( (value instanceof Serializable) 
                    && (!exclude(keys[i]) )) {
                saveNames.add(keys[i]);
                saveValues.add(value);
            } else {
                removeAttributeInternal(keys[i], true);
            }
        }

        // Serialize the attribute count and the Serializable attributes
        int n = saveNames.size();
        stream.writeObject(new Integer(n));
        for (int i = 0; i < n; i++) {
            stream.writeObject((String) saveNames.get(i));
            try {
                stream.writeObject(saveValues.get(i));
                if (manager.getContainer().getLogger().isDebugEnabled())
                    manager.getContainer().getLogger().debug
                        ("  storing attribute '" + saveNames.get(i) +
                        "' with value '" + saveValues.get(i) + "'");
            } catch (NotSerializableException e) {
                manager.getContainer().getLogger().warn
                    (sm.getString("standardSession.notSerializable",
                     saveNames.get(i), id), e);
                stream.writeObject(NOT_SERIALIZED);
                if (manager.getContainer().getLogger().isDebugEnabled())
                    manager.getContainer().getLogger().debug
                       ("  storing attribute '" + saveNames.get(i) +
                        "' with value NOT_SERIALIZED");
            }
        }

    }


    /**
     * Exclude attribute that cannot be serialized.
     * @param name the attribute's name
     */
    protected boolean exclude(String name){

        for (int i = 0; i < excludedAttributes.length; i++) {
            if (name.equalsIgnoreCase(excludedAttributes[i]))
                return true;
        }

        return false;
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Fire container events if the Context implementation is the
     * <code>org.apache.catalina.core.StandardContext</code>.
     *
     * @param context Context for which to fire events
     * @param type Event type
     * @param data Event data
     *
     * @exception Exception occurred during event firing
     */
    protected void fireContainerEvent(Context context,
                                    String type, Object data)
        throws Exception {

        if (!"org.apache.catalina.core.StandardContext".equals
            (context.getClass().getName())) {
            return; // Container events are not supported
        }
        // NOTE:  Race condition is harmless, so do not synchronize
        if (containerEventMethod == null) {
            containerEventMethod =
                context.getClass().getMethod("fireContainerEvent",
                                             containerEventTypes);
        }
        Object containerEventParams[] = new Object[2];
        containerEventParams[0] = type;
        containerEventParams[1] = data;
        containerEventMethod.invoke(context, containerEventParams);

    }
                                      


    /**
     * Notify all session event listeners that a particular event has
     * occurred for this Session.  The default implementation performs
     * this notification synchronously using the calling thread.
     *
     * @param type Event type
     * @param data Event data
     */
    public void fireSessionEvent(String type, Object data) {
        if (listeners.size() < 1)
            return;
        SessionEvent event = new SessionEvent(this, type, data);
        SessionListener list[] = new SessionListener[0];
        synchronized (listeners) {
            list = (SessionListener[]) listeners.toArray(list);
        }

        for (int i = 0; i < list.length; i++){
            ((SessionListener) list[i]).sessionEvent(event);
        }

    }


    /**
     * Return the names of all currently defined session attributes
     * as an array of Strings.  If there are no defined attributes, a
     * zero-length array is returned.
     */
    protected String[] keys() {

        return ((String[]) attributes.keySet().toArray(EMPTY_ARRAY));

    }


    /**
     * Remove the object bound with the specified name from this session.  If
     * the session does not have an object bound with this name, this method
     * does nothing.
     * <p>
     * After this method executes, and if the object implements
     * <code>HttpSessionBindingListener</code>, the container calls
     * <code>valueUnbound()</code> on the object.
     *
     * @param name Name of the object to remove from this session.
     * @param notify Should we notify interested listeners that this
     *  attribute is being removed?
     */
    protected void removeAttributeInternal(String name, boolean notify) {

        // Remove this attribute from our collection
        Object value = attributes.remove(name);

        // Do we need to do valueUnbound() and attributeRemoved() notification?
        if (!notify || (value == null)) {
            return;
        }

        // Call the valueUnbound() method if necessary
        HttpSessionBindingEvent event = null;
        if (value instanceof HttpSessionBindingListener) {
            event = new HttpSessionBindingEvent(getSession(), name, value);
            ((HttpSessionBindingListener) value).valueUnbound(event);
        }

        // Notify interested application event listeners
        Context context = (Context) manager.getContainer();
        Object listeners[] = context.getApplicationEventListeners();
        if (listeners == null)
            return;
        for (int i = 0; i < listeners.length; i++) {
            if (!(listeners[i] instanceof HttpSessionAttributeListener))
                continue;
            HttpSessionAttributeListener listener =
                (HttpSessionAttributeListener) listeners[i];
            try {
                fireContainerEvent(context,
                                   "beforeSessionAttributeRemoved",
                                   listener);
                if (event == null) {
                    event = new HttpSessionBindingEvent
                        (getSession(), name, value);
                }
                listener.attributeRemoved(event);
                fireContainerEvent(context,
                                   "afterSessionAttributeRemoved",
                                   listener);
            } catch (Throwable t) {
                try {
                    fireContainerEvent(context,
                                       "afterSessionAttributeRemoved",
                                       listener);
                } catch (Exception e) {
                    ;
                }
                manager.getContainer().getLogger().error
                    (sm.getString("standardSession.attributeEvent"), t);
            }
        }

    }


}


// ------------------------------------------------------------ Protected Class


/**
 * This class is a dummy implementation of the <code>HttpSessionContext</code>
 * interface, to conform to the requirement that such an object be returned
 * when <code>HttpSession.getSessionContext()</code> is called.
 *
 * @author Craig R. McClanahan
 *
 * @deprecated As of Java Servlet API 2.1 with no replacement.  The
 *  interface will be removed in a future version of this API.
 */

final class StandardSessionContext implements HttpSessionContext {


    protected HashMap dummy = new HashMap();

    /**
     * Return the session identifiers of all sessions defined
     * within this context.
     *
     * @deprecated As of Java Servlet API 2.1 with no replacement.
     *  This method must return an empty <code>Enumeration</code>
     *  and will be removed in a future version of the API.
     */
    public Enumeration getIds() {

        return (new Enumerator(dummy));

    }


    /**
     * Return the <code>HttpSession</code> associated with the
     * specified session identifier.
     *
     * @param id Session identifier for which to look up a session
     *
     * @deprecated As of Java Servlet API 2.1 with no replacement.
     *  This method must return null and will be removed in a
     *  future version of the API.
     */
    public HttpSession getSession(String id) {

        return (null);

    }



}
