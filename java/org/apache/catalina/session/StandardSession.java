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
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.io.WriteAbortedException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionActivationListener;
import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionIdListener;
import jakarta.servlet.http.HttpSessionListener;

import org.apache.catalina.Context;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.SessionEvent;
import org.apache.catalina.SessionListener;
import org.apache.catalina.TomcatPrincipal;
import org.apache.catalina.authenticator.SavedRequest;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * Standard implementation of the <b>Session</b> interface. This object is serializable, so that it can be stored in
 * persistent storage or transferred to a different JVM for distributable session support.
 * <p>
 * <b>IMPLEMENTATION NOTE</b>: An instance of this class represents both the internal (Session) and application level
 * (HttpSession) view of the session. However, because the class itself is not declared public, Java logic outside of
 * the <code>org.apache.catalina.session</code> package cannot cast an HttpSession view of this instance back to a
 * Session view.
 * <p>
 * <b>IMPLEMENTATION NOTE</b>: If you add fields to this class, you must make sure that you carry them over in the
 * read/writeObject methods so that this class is properly serialized.
 *
 * @author Craig R. McClanahan
 * @author Sean Legassick
 * @author <a href="mailto:jon@latchkey.com">Jon S. Stevens</a>
 */
public class StandardSession implements HttpSession, Session, Serializable {

    private static final long serialVersionUID = 1L;

    // ----------------------------------------------------------- Constructors


    /**
     * Construct a new Session associated with the specified Manager.
     *
     * @param manager The manager with which this Session is associated
     */
    public StandardSession(Manager manager) {

        super();
        this.manager = manager;

        if (manager != null) {
            // Manager could be null in test environments
            activityCheck = manager.getSessionActivityCheck();
            lastAccessAtStart = manager.getSessionLastAccessAtStart();
        }

        // Initialize access count
        if (activityCheck) {
            accessCount = new AtomicInteger();
        }

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Type array.
     */
    protected static final String EMPTY_ARRAY[] = new String[0];


    /**
     * The collection of user data attributes associated with this Session.
     */
    protected ConcurrentMap<String,Object> attributes = new ConcurrentHashMap<>();


    /**
     * The authentication type used to authenticate our cached Principal, if any. NOTE: This value is not included in
     * the serialized version of this object.
     */
    protected transient String authType = null;


    /**
     * The time this session was created, in milliseconds since midnight, January 1, 1970 GMT.
     */
    protected long creationTime = 0L;


    /**
     * We are currently processing a session expiration, so bypass certain IllegalStateException tests. NOTE: This value
     * is not included in the serialized version of this object.
     */
    protected transient volatile boolean expiring = false;


    /**
     * The facade associated with this session. NOTE: This value is not included in the serialized version of this
     * object.
     */
    protected transient StandardSessionFacade facade = null;


    /**
     * The session identifier of this Session.
     */
    protected String id = null;


    /**
     * The last accessed time for this Session.
     */
    protected volatile long lastAccessedTime = creationTime;


    /**
     * The session event listeners for this Session.
     */
    protected transient ArrayList<SessionListener> listeners = new ArrayList<>();


    /**
     * The Manager with which this Session is associated.
     */
    protected transient Manager manager = null;


    /**
     * The maximum time interval, in seconds, between client requests before the servlet container may invalidate this
     * session. A negative time indicates that the session should never time out.
     */
    protected volatile int maxInactiveInterval = -1;


    /**
     * Flag indicating whether this session is new or not.
     */
    protected volatile boolean isNew = false;


    /**
     * Flag indicating whether this session is valid or not.
     */
    protected volatile boolean isValid = false;


    /**
     * Internal notes associated with this session by Catalina components and event listeners. <b>IMPLEMENTATION
     * NOTE:</b> This object is <em>not</em> saved and restored across session serializations!
     */
    protected transient Map<String,Object> notes = new ConcurrentHashMap<>();


    /**
     * The authenticated Principal associated with this session, if any. <b>IMPLEMENTATION NOTE:</b> This object is
     * <i>not</i> saved and restored across session serializations!
     */
    protected transient Principal principal = null;


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(StandardSession.class);


    /**
     * The property change support for this component. NOTE: This value is not included in the serialized version of
     * this object.
     */
    protected final transient PropertyChangeSupport support = new PropertyChangeSupport(this);


    /**
     * The current accessed time for this session.
     */
    protected volatile long thisAccessedTime = creationTime;


    /**
     * The access count for this session.
     */
    protected transient AtomicInteger accessCount = null;


    /**
     * The activity check for this session.
     */
    protected transient boolean activityCheck;


    /**
     * The behavior of the last access check.
     */
    protected transient boolean lastAccessAtStart;


    // ----------------------------------------------------- Session Properties


    @Override
    public String getAuthType() {
        return this.authType;
    }


    @Override
    public void setAuthType(String authType) {
        String oldAuthType = this.authType;
        this.authType = authType;
        support.firePropertyChange("authType", oldAuthType, this.authType);
    }


    @Override
    public void setCreationTime(long time) {

        this.creationTime = time;
        this.lastAccessedTime = time;
        this.thisAccessedTime = time;

    }


    @Override
    public String getId() {
        return this.id;
    }


    @Override
    public String getIdInternal() {
        return this.id;
    }


    @Override
    public void setId(String id) {
        setId(id, true);
    }


    @Override
    public void setId(String id, boolean notify) {

        if ((this.id != null) && (manager != null)) {
            manager.remove(this);
        }

        this.id = id;

        if (manager != null) {
            manager.add(this);
        }

        if (notify) {
            tellNew();
        }
    }


    /**
     * Inform the listeners about the new session.
     */
    public void tellNew() {

        // Notify interested session event listeners
        fireSessionEvent(SESSION_CREATED_EVENT, null);

        // Notify interested application event listeners
        Context context = manager.getContext();
        Object listeners[] = context.getApplicationLifecycleListeners();
        if (listeners != null && listeners.length > 0) {
            HttpSessionEvent event = new HttpSessionEvent(getSession());
            for (Object o : listeners) {
                if (!(o instanceof HttpSessionListener)) {
                    continue;
                }
                HttpSessionListener listener = (HttpSessionListener) o;
                try {
                    context.fireContainerEvent("beforeSessionCreated", listener);
                    listener.sessionCreated(event);
                    context.fireContainerEvent("afterSessionCreated", listener);
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    try {
                        context.fireContainerEvent("afterSessionCreated", listener);
                    } catch (Exception e) {
                        // Ignore
                    }
                    manager.getContext().getLogger().error(sm.getString("standardSession.sessionEvent"), t);
                }
            }
        }

    }

    @Override
    public void tellChangedSessionId(String newId, String oldId, boolean notifySessionListeners,
            boolean notifyContainerListeners) {
        Context context = manager.getContext();
        // notify ContainerListeners
        if (notifyContainerListeners) {
            context.fireContainerEvent(Context.CHANGE_SESSION_ID_EVENT, new String[] { oldId, newId });
        }

        // notify HttpSessionIdListener
        if (notifySessionListeners) {
            Object listeners[] = context.getApplicationEventListeners();
            if (listeners != null && listeners.length > 0) {
                HttpSessionEvent event = new HttpSessionEvent(getSession());

                for (Object listener : listeners) {
                    if (!(listener instanceof HttpSessionIdListener)) {
                        continue;
                    }

                    HttpSessionIdListener idListener = (HttpSessionIdListener) listener;
                    try {
                        idListener.sessionIdChanged(event, oldId);
                    } catch (Throwable t) {
                        manager.getContext().getLogger().error(sm.getString("standardSession.sessionEvent"), t);
                    }
                }
            }
        }
    }


    @Override
    public long getThisAccessedTime() {

        if (!isValidInternal()) {
            throw new IllegalStateException(sm.getString("standardSession.getThisAccessedTime.ise"));
        }

        return this.thisAccessedTime;
    }

    @Override
    public long getThisAccessedTimeInternal() {
        return this.thisAccessedTime;
    }

    @Override
    public long getLastAccessedTime() {

        if (!isValidInternal()) {
            throw new IllegalStateException(sm.getString("standardSession.getLastAccessedTime.ise"));
        }

        return this.lastAccessedTime;
    }

    @Override
    public long getLastAccessedTimeInternal() {
        return this.lastAccessedTime;
    }

    @Override
    public long getIdleTime() {

        if (!isValidInternal()) {
            throw new IllegalStateException(sm.getString("standardSession.getIdleTime.ise"));
        }

        return getIdleTimeInternal();
    }

    @Override
    public long getIdleTimeInternal() {
        long timeNow = System.currentTimeMillis();
        long timeIdle;
        if (lastAccessAtStart) {
            timeIdle = timeNow - lastAccessedTime;
        } else {
            timeIdle = timeNow - thisAccessedTime;
        }
        return timeIdle;
    }

    @Override
    public Manager getManager() {
        return this.manager;
    }


    @Override
    public void setManager(Manager manager) {
        this.manager = manager;
    }


    @Override
    public int getMaxInactiveInterval() {
        return this.maxInactiveInterval;
    }


    @Override
    public void setMaxInactiveInterval(int interval) {
        this.maxInactiveInterval = interval;
    }


    @Override
    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }


    @Override
    public Principal getPrincipal() {
        return this.principal;
    }


    @Override
    public void setPrincipal(Principal principal) {

        Principal oldPrincipal = this.principal;
        this.principal = principal;
        support.firePropertyChange("principal", oldPrincipal, this.principal);

    }


    @Override
    public HttpSession getSession() {
        if (facade == null) {
            facade = new StandardSessionFacade(this);
        }
        return facade;
    }


    @Override
    public boolean isValid() {

        if (!this.isValid) {
            return false;
        }

        if (this.expiring) {
            return true;
        }

        if (activityCheck && accessCount.get() > 0) {
            return true;
        }

        if (maxInactiveInterval > 0) {
            int timeIdle = (int) (getIdleTimeInternal() / 1000L);
            if (timeIdle >= maxInactiveInterval) {
                expire(true);
            }
        }

        return this.isValid;
    }


    @Override
    public void setValid(boolean isValid) {
        this.isValid = isValid;
    }


    @Override
    public Accessor getAccessor() {
        if (!isValidInternal()) {
            throw new IllegalStateException(sm.getString("standardSession.getAccessor.ise"));
        }

        return new StandardSessionAccessor(getManager(), getId());
    }


    // ------------------------------------------------- Session Public Methods


    @Override
    public void access() {

        this.thisAccessedTime = System.currentTimeMillis();

        if (activityCheck) {
            accessCount.incrementAndGet();
        }

    }


    @Override
    public void endAccess() {

        isNew = false;

        /*
         * The servlet spec mandates to ignore request handling time in lastAccessedTime.
         */
        if (lastAccessAtStart) {
            this.lastAccessedTime = this.thisAccessedTime;
            this.thisAccessedTime = System.currentTimeMillis();
        } else {
            this.thisAccessedTime = System.currentTimeMillis();
            this.lastAccessedTime = this.thisAccessedTime;
        }

        if (activityCheck) {
            accessCount.decrementAndGet();
        }

    }


    @Override
    public void addSessionListener(SessionListener listener) {

        listeners.add(listener);

    }


    @Override
    public void expire() {

        expire(true);

    }


    /**
     * Perform the internal processing required to invalidate this session, without triggering an exception if the
     * session has already expired.
     *
     * @param notify Should we notify listeners about the demise of this session?
     */
    public void expire(boolean notify) {

        // Check to see if session has already been invalidated.
        // Do not check expiring at this point as expire should not return until
        // isValid is false
        if (!isValid) {
            return;
        }

        synchronized (this) {
            // Check again, now we are inside the sync so this code only runs once
            // Double check locking - isValid needs to be volatile
            // The check of expiring is to ensure that an infinite loop is not
            // entered as per bug 56339
            if (expiring || !isValid) {
                return;
            }

            if (manager == null) {
                return;
            }

            // Mark this session as "being expired"
            expiring = true;

            // Notify interested application event listeners
            // Call listeners in reverse order
            Context context = manager.getContext();

            // The call to expire() may not have been triggered by the webapp.
            // Make sure the webapp's class loader is set when calling the
            // listeners
            if (notify) {
                ClassLoader oldContextClassLoader = null;
                try {
                    oldContextClassLoader = context.bind(null);
                    Object listeners[] = context.getApplicationLifecycleListeners();
                    if (listeners != null && listeners.length > 0) {
                        HttpSessionEvent event = new HttpSessionEvent(getSession());
                        for (int i = 0; i < listeners.length; i++) {
                            int j = (listeners.length - 1) - i;
                            if (!(listeners[j] instanceof HttpSessionListener)) {
                                continue;
                            }
                            HttpSessionListener listener = (HttpSessionListener) listeners[j];
                            try {
                                context.fireContainerEvent("beforeSessionDestroyed", listener);
                                listener.sessionDestroyed(event);
                                context.fireContainerEvent("afterSessionDestroyed", listener);
                            } catch (Throwable t) {
                                ExceptionUtils.handleThrowable(t);
                                try {
                                    context.fireContainerEvent("afterSessionDestroyed", listener);
                                } catch (Exception e) {
                                    // Ignore
                                }
                                manager.getContext().getLogger().error(sm.getString("standardSession.sessionEvent"), t);
                            }
                        }
                    }
                } finally {
                    context.unbind(oldContextClassLoader);
                }
            }

            if (activityCheck) {
                accessCount.set(0);
            }

            // Remove this session from our manager's active sessions
            manager.remove(this, true);

            // Notify interested session event listeners
            if (notify) {
                fireSessionEvent(SESSION_DESTROYED_EVENT, null);
            }

            // Call the logout method
            if (principal instanceof TomcatPrincipal) {
                TomcatPrincipal gp = (TomcatPrincipal) principal;
                try {
                    gp.logout();
                } catch (Exception e) {
                    manager.getContext().getLogger().error(sm.getString("standardSession.logoutfail"), e);
                }
            }

            // We have completed expire of this session
            setValid(false);
            expiring = false;

            // Unbind any objects associated with this session
            String keys[] = keys();
            ClassLoader oldContextClassLoader = null;
            try {
                oldContextClassLoader = context.bind(null);
                for (String key : keys) {
                    removeAttributeInternal(key, notify);
                }
            } finally {
                context.unbind(oldContextClassLoader);
            }
        }

    }


    /**
     * Perform the internal processing required to passivate this session.
     */
    public void passivate() {

        // Notify interested session event listeners
        fireSessionEvent(SESSION_PASSIVATED_EVENT, null);

        // Notify ActivationListeners
        HttpSessionEvent event = null;
        String keys[] = keys();
        for (String key : keys) {
            Object attribute = attributes.get(key);
            if (attribute instanceof HttpSessionActivationListener) {
                if (event == null) {
                    event = new HttpSessionEvent(getSession());
                }
                try {
                    ((HttpSessionActivationListener) attribute).sessionWillPassivate(event);
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    manager.getContext().getLogger().error(sm.getString("standardSession.attributeEvent"), t);
                }
            }
        }

    }


    /**
     * Perform internal processing required to activate this session.
     */
    public void activate() {

        // Initialize access count
        if (activityCheck) {
            accessCount = new AtomicInteger();
        }

        // Notify interested session event listeners
        fireSessionEvent(SESSION_ACTIVATED_EVENT, null);

        // Notify ActivationListeners
        HttpSessionEvent event = null;
        String keys[] = keys();
        for (String key : keys) {
            Object attribute = attributes.get(key);
            if (attribute instanceof HttpSessionActivationListener) {
                if (event == null) {
                    event = new HttpSessionEvent(getSession());
                }
                try {
                    ((HttpSessionActivationListener) attribute).sessionDidActivate(event);
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    manager.getContext().getLogger().error(sm.getString("standardSession.attributeEvent"), t);
                }
            }
        }
    }


    @Override
    public Object getNote(String name) {
        return notes.get(name);
    }


    @Override
    public Iterator<String> getNoteNames() {
        return notes.keySet().iterator();
    }


    @Override
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


    @Override
    public void removeNote(String name) {

        notes.remove(name);

    }


    @Override
    public void removeSessionListener(SessionListener listener) {

        listeners.remove(listener);

    }


    @Override
    public void setNote(String name, Object value) {

        notes.put(name, value);

    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("StandardSession[");
        sb.append(id);
        sb.append(']');
        return sb.toString();
    }


    // ------------------------------------------------ Session Package Methods


    /**
     * Read a serialized version of the contents of this session object from the specified object input stream, without
     * requiring that the StandardSession itself have been serialized.
     *
     * @param stream The object input stream to read from
     *
     * @exception ClassNotFoundException if an unknown class is specified
     * @exception IOException            if an input/output error occurs
     */
    public void readObjectData(ObjectInputStream stream) throws ClassNotFoundException, IOException {

        doReadObject(stream);

    }


    /**
     * Write a serialized version of the contents of this session object to the specified object output stream, without
     * requiring that the StandardSession itself have been serialized.
     *
     * @param stream The object output stream to write to
     *
     * @exception IOException if an input/output error occurs
     */
    public void writeObjectData(ObjectOutputStream stream) throws IOException {

        doWriteObject(stream);

    }


    // ------------------------------------------------- HttpSession Properties


    @Override
    public long getCreationTime() {
        if (!isValidInternal()) {
            throw new IllegalStateException(sm.getString("standardSession.getCreationTime.ise"));
        }

        return this.creationTime;
    }


    @Override
    public long getCreationTimeInternal() {
        return this.creationTime;
    }


    @Override
    public ServletContext getServletContext() {
        if (manager == null) {
            return null;
        }
        Context context = manager.getContext();
        return context.getServletContext();
    }


    // ----------------------------------------------HttpSession Public Methods

    @Override
    public Object getAttribute(String name) {
        if (!isValidInternal()) {
            throw new IllegalStateException(sm.getString("standardSession.getAttribute.ise"));
        }

        if (name == null) {
            return null;
        }

        return attributes.get(name);
    }


    @Override
    public Enumeration<String> getAttributeNames() {

        if (!isValidInternal()) {
            throw new IllegalStateException(sm.getString("standardSession.getAttributeNames.ise"));
        }

        Set<String> names = new HashSet<>(attributes.keySet());
        return Collections.enumeration(names);
    }


    @Override
    public void invalidate() {

        if (!isValidInternal()) {
            throw new IllegalStateException(sm.getString("standardSession.invalidate.ise"));
        }

        // Cause this session to expire
        expire();

    }


    @Override
    public boolean isNew() {
        if (!isValidInternal()) {
            throw new IllegalStateException(sm.getString("standardSession.isNew.ise"));
        }

        return this.isNew;
    }


    @Override
    public void removeAttribute(String name) {

        removeAttribute(name, true);

    }


    /**
     * Remove the object bound with the specified name from this session. If the session does not have an object bound
     * with this name, this method does nothing.
     * <p>
     * After this method executes, and if the object implements <code>HttpSessionBindingListener</code>, the container
     * calls <code>valueUnbound()</code> on the object.
     *
     * @param name   Name of the object to remove from this session.
     * @param notify Should we notify interested listeners that this attribute is being removed?
     *
     * @exception IllegalStateException if this method is called on an invalidated session
     */
    public void removeAttribute(String name, boolean notify) {

        // Validate our current state
        if (!isValidInternal()) {
            throw new IllegalStateException(sm.getString("standardSession.removeAttribute.ise"));
        }

        removeAttributeInternal(name, notify);

    }


    @Override
    public void setAttribute(String name, Object value) {
        setAttribute(name, value, true);
    }


    /**
     * Bind an object to this session, using the specified name. If an object of the same name is already bound to this
     * session, the object is replaced.
     * <p>
     * After this method executes, and if the object implements <code>HttpSessionBindingListener</code>, the container
     * calls <code>valueBound()</code> on the object.
     *
     * @param name   Name to which the object is bound, cannot be null
     * @param value  Object to be bound, cannot be null
     * @param notify whether to notify session listeners
     *
     * @exception IllegalArgumentException if an attempt is made to add a non-serializable object in an environment
     *                                         marked distributable.
     * @exception IllegalStateException    if this method is called on an invalidated session
     */
    public void setAttribute(String name, Object value, boolean notify) {

        // Name cannot be null
        if (name == null) {
            throw new IllegalArgumentException(sm.getString("standardSession.setAttribute.namenull"));
        }

        // Null value is the same as removeAttribute()
        if (value == null) {
            removeAttribute(name);
            return;
        }

        // Validate our current state
        if (!isValidInternal()) {
            throw new IllegalStateException(sm.getString("standardSession.setAttribute.ise", getIdInternal()));
        }

        Context context = manager.getContext();

        if (context.getDistributable() && !isAttributeDistributable(name, value) && !exclude(name, value)) {
            throw new IllegalArgumentException(sm.getString("standardSession.setAttribute.iae", name));
        }
        // Construct an event with the new value
        HttpSessionBindingEvent event = null;

        // Call the valueBound() method if necessary
        if (notify && value instanceof HttpSessionBindingListener) {
            // Don't call any notification if replacing with the same value
            // unless configured to do so
            Object oldValue = attributes.get(name);
            if (value != oldValue || manager.getNotifyBindingListenerOnUnchangedValue()) {
                event = new HttpSessionBindingEvent(getSession(), name, value);
                try {
                    ((HttpSessionBindingListener) value).valueBound(event);
                } catch (Throwable t) {
                    manager.getContext().getLogger().error(sm.getString("standardSession.bindingEvent"), t);
                }
            }
        }

        // Replace or add this attribute
        Object unbound = attributes.put(name, value);

        // Call the valueUnbound() method if necessary
        if (notify && unbound instanceof HttpSessionBindingListener) {
            // Don't call any notification if replacing with the same value
            // unless configured to do so
            if (unbound != value || manager.getNotifyBindingListenerOnUnchangedValue()) {
                try {
                    ((HttpSessionBindingListener) unbound)
                            .valueUnbound(new HttpSessionBindingEvent(getSession(), name));
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    manager.getContext().getLogger().error(sm.getString("standardSession.bindingEvent"), t);
                }
            }
        }

        if (!notify) {
            return;
        }

        // Notify interested application event listeners
        Object listeners[] = context.getApplicationEventListeners();
        if (listeners == null) {
            return;
        }
        for (Object o : listeners) {
            if (!(o instanceof HttpSessionAttributeListener)) {
                continue;
            }
            HttpSessionAttributeListener listener = (HttpSessionAttributeListener) o;
            try {
                if (unbound != null) {
                    if (unbound != value || manager.getNotifyAttributeListenerOnUnchangedValue()) {
                        context.fireContainerEvent("beforeSessionAttributeReplaced", listener);
                        if (event == null) {
                            event = new HttpSessionBindingEvent(getSession(), name, unbound);
                        }
                        listener.attributeReplaced(event);
                        context.fireContainerEvent("afterSessionAttributeReplaced", listener);
                    }
                } else {
                    context.fireContainerEvent("beforeSessionAttributeAdded", listener);
                    if (event == null) {
                        event = new HttpSessionBindingEvent(getSession(), name, value);
                    }
                    listener.attributeAdded(event);
                    context.fireContainerEvent("afterSessionAttributeAdded", listener);
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                try {
                    if (unbound != null) {
                        if (unbound != value || manager.getNotifyAttributeListenerOnUnchangedValue()) {
                            context.fireContainerEvent("afterSessionAttributeReplaced", listener);
                        }
                    } else {
                        context.fireContainerEvent("afterSessionAttributeAdded", listener);
                    }
                } catch (Exception e) {
                    // Ignore
                }
                manager.getContext().getLogger().error(sm.getString("standardSession.attributeEvent"), t);
            }
        }
    }


    // ------------------------------------------ HttpSession Protected Methods

    /**
     * @return the <code>isValid</code> flag for this session without any expiration check.
     */
    protected boolean isValidInternal() {
        return this.isValid;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation simply checks the value for serializability. Sub-classes might use other distribution
     * technology not based on serialization and can override this check.
     */
    @Override
    public boolean isAttributeDistributable(String name, Object value) {
        return value instanceof Serializable;
    }


    /**
     * Read a serialized version of this session object from the specified object input stream.
     * <p>
     * <b>IMPLEMENTATION NOTE</b>: The reference to the owning Manager is not restored by this method, and must be set
     * explicitly.
     *
     * @param stream The input stream to read from
     *
     * @exception ClassNotFoundException if an unknown class is specified
     * @exception IOException            if an input/output error occurs
     */
    protected void doReadObject(ObjectInputStream stream) throws ClassNotFoundException, IOException {

        // Deserialize the scalar instance variables (except Manager)
        authType = null; // Transient (may be set later)
        creationTime = ((Long) stream.readObject()).longValue();
        lastAccessedTime = ((Long) stream.readObject()).longValue();
        maxInactiveInterval = ((Integer) stream.readObject()).intValue();
        isNew = ((Boolean) stream.readObject()).booleanValue();
        isValid = ((Boolean) stream.readObject()).booleanValue();
        thisAccessedTime = ((Long) stream.readObject()).longValue();
        principal = null; // Transient (may be set later)
        // setId((String) stream.readObject());
        id = (String) stream.readObject();
        if (manager.getContext().getLogger().isTraceEnabled()) {
            manager.getContext().getLogger().trace("readObject() loading session " + id);
        }

        if (notes == null) {
            notes = new ConcurrentHashMap<>();
        }
        /*
         * The next object read could either be the number of attributes (Integer) or if authentication information is
         * persisted then: - authType (String) - always present - Principal object - always present - expected session
         * ID - present if BZ 66120 is fixed - saved request - present if BZ 66120 is fixed
         *
         * Note: Some, all or none of the above objects may be null
         */
        Object nextObject = stream.readObject();
        if (!(nextObject instanceof Integer)) {
            // Not an Integer so the next two objects will be authType and
            // Principal
            setAuthType((String) nextObject);
            try {
                setPrincipal((Principal) stream.readObject());
            } catch (ClassNotFoundException | ObjectStreamException e) {
                String msg = sm.getString("standardSession.principalNotDeserializable", id);
                if (manager.getContext().getLogger().isDebugEnabled()) {
                    manager.getContext().getLogger().debug(msg, e);
                } else {
                    manager.getContext().getLogger().warn(msg);
                }
                throw e;
            }

            nextObject = stream.readObject();
            if (!(nextObject instanceof Integer)) {
                // Not an Integer so the next two objects will be
                // 'expected session ID' and 'saved request'
                if (nextObject != null) {
                    notes.put(org.apache.catalina.authenticator.Constants.SESSION_ID_NOTE, nextObject);
                }
                nextObject = stream.readObject();
                if (nextObject != null) {
                    notes.put(org.apache.catalina.authenticator.Constants.FORM_REQUEST_NOTE, nextObject);
                }

                // Next object will be the number of attributes
                nextObject = stream.readObject();
            }
        }

        // Deserialize the attribute count and attribute values
        if (attributes == null) {
            attributes = new ConcurrentHashMap<>();
        }
        int n = ((Integer) nextObject).intValue();
        boolean isValidSave = isValid;
        isValid = true;
        for (int i = 0; i < n; i++) {
            String name = (String) stream.readObject();
            final Object value;
            try {
                value = stream.readObject();
            } catch (WriteAbortedException wae) {
                if (wae.getCause() instanceof NotSerializableException) {
                    String msg = sm.getString("standardSession.notDeserializable", name, id);
                    if (manager.getContext().getLogger().isDebugEnabled()) {
                        manager.getContext().getLogger().debug(msg, wae);
                    } else {
                        manager.getContext().getLogger().warn(msg);
                    }
                    // Skip non serializable attributes
                    continue;
                }
                throw wae;
            }
            if (manager.getContext().getLogger().isTraceEnabled()) {
                manager.getContext().getLogger().trace("  loading attribute '" + name + "' with value '" + value + "'");
            }
            // Handle the case where the filter configuration was changed while
            // the web application was stopped.
            if (exclude(name, value)) {
                continue;
            }
            // ConcurrentHashMap does not allow null keys or values
            if (null != value) {
                attributes.put(name, value);
            }
        }
        isValid = isValidSave;

        if (listeners == null) {
            listeners = new ArrayList<>();
        }
    }


    /**
     * Write a serialized version of this session object to the specified object output stream.
     * <p>
     * <b>IMPLEMENTATION NOTE</b>: The owning Manager will not be stored in the serialized representation of this
     * Session. After calling <code>readObject()</code>, you must set the associated Manager explicitly.
     * <p>
     * <b>IMPLEMENTATION NOTE</b>: Any attribute that is not Serializable will be unbound from the session, with
     * appropriate actions if it implements HttpSessionBindingListener. If you do not want any such attributes, be sure
     * the <code>distributable</code> property of the associated Manager is set to <code>true</code>.
     *
     * @param stream The output stream to write to
     *
     * @exception IOException if an input/output error occurs
     */
    protected void doWriteObject(ObjectOutputStream stream) throws IOException {

        // Write the scalar instance variables (except Manager)
        stream.writeObject(Long.valueOf(creationTime));
        stream.writeObject(Long.valueOf(lastAccessedTime));
        stream.writeObject(Integer.valueOf(maxInactiveInterval));
        stream.writeObject(Boolean.valueOf(isNew));
        stream.writeObject(Boolean.valueOf(isValid));
        stream.writeObject(Long.valueOf(thisAccessedTime));
        stream.writeObject(id);
        if (manager.getContext().getLogger().isTraceEnabled()) {
            manager.getContext().getLogger().trace("writeObject() storing session " + id);
        }

        // Gather authentication information (if configured)
        String sessionAuthType = null;
        Principal sessionPrincipal = null;
        String expectedSessionId = null;
        SavedRequest savedRequest = null;
        if (getPersistAuthentication()) {
            sessionAuthType = getAuthType();
            sessionPrincipal = getPrincipal();
            if (sessionPrincipal != null && !(sessionPrincipal instanceof Serializable)) {
                sessionPrincipal = null;
                manager.getContext().getLogger().warn(sm.getString("standardSession.principalNotSerializable", id));
            }
            expectedSessionId = (String) notes.get(org.apache.catalina.authenticator.Constants.SESSION_ID_NOTE);
            savedRequest = (SavedRequest) notes.get(org.apache.catalina.authenticator.Constants.FORM_REQUEST_NOTE);
        }

        // Write authentication information (may be null values)
        stream.writeObject(sessionAuthType);
        try {
            stream.writeObject(sessionPrincipal);
        } catch (NotSerializableException e) {
            manager.getContext().getLogger().warn(sm.getString("standardSession.principalNotSerializable", id), e);
        }
        stream.writeObject(expectedSessionId);
        stream.writeObject(savedRequest);

        // Accumulate the names of serializable and non-serializable attributes
        String keys[] = keys();
        List<String> saveNames = new ArrayList<>();
        List<Object> saveValues = new ArrayList<>();
        for (String key : keys) {
            Object value = attributes.get(key);
            if (value == null) {
                continue;
            } else if (isAttributeDistributable(key, value) && !exclude(key, value)) {
                saveNames.add(key);
                saveValues.add(value);
            } else {
                removeAttributeInternal(key, true);
            }
        }

        // Serialize the attribute count and the Serializable attributes
        int n = saveNames.size();
        stream.writeObject(Integer.valueOf(n));
        for (int i = 0; i < n; i++) {
            stream.writeObject(saveNames.get(i));
            try {
                stream.writeObject(saveValues.get(i));
                if (manager.getContext().getLogger().isTraceEnabled()) {
                    manager.getContext().getLogger().trace(
                            "  storing attribute '" + saveNames.get(i) + "' with value '" + saveValues.get(i) + "'");
                }
            } catch (NotSerializableException e) {
                manager.getContext().getLogger()
                        .warn(sm.getString("standardSession.notSerializable", saveNames.get(i), id), e);
            }
        }

    }

    /**
     * Return whether authentication information shall be persisted or not.
     *
     * @return {@code true}, if authentication information shall be persisted; {@code false} otherwise
     */
    private boolean getPersistAuthentication() {
        if (manager instanceof ManagerBase) {
            return ((ManagerBase) manager).getPersistAuthentication();
        }
        return false;
    }

    /**
     * Should the given session attribute be excluded? This implementation checks:
     * <ul>
     * <li>{@link Constants#excludedAttributeNames}</li>
     * <li>{@link Manager#willAttributeDistribute(String, Object)}</li>
     * </ul>
     * Note: This method deliberately does not check {@link #isAttributeDistributable(String, Object)} which is kept
     * separate to support the checks required in {@link #setAttribute(String, Object, boolean)}
     *
     * @param name  The attribute name
     * @param value The attribute value
     *
     * @return {@code true} if the attribute should be excluded from distribution, otherwise {@code false}
     */
    protected boolean exclude(String name, Object value) {
        if (Constants.excludedAttributeNames.contains(name)) {
            return true;
        }

        // Manager is required for remaining check
        Manager manager = getManager();
        if (manager == null) {
            // Manager may be null during replication of new sessions in a
            // cluster. Avoid the NPE.
            return false;
        }

        // Last check so use a short-cut
        return !manager.willAttributeDistribute(name, value);
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * Notify all session event listeners that a particular event has occurred for this Session. The default
     * implementation performs this notification synchronously using the calling thread.
     *
     * @param type Event type
     * @param data Event data
     */
    public void fireSessionEvent(String type, Object data) {
        if (listeners.size() < 1) {
            return;
        }
        SessionEvent event = new SessionEvent(this, type, data);
        SessionListener list[] = new SessionListener[0];
        synchronized (listeners) {
            list = listeners.toArray(list);
        }

        for (SessionListener sessionListener : list) {
            sessionListener.sessionEvent(event);
        }

    }


    /**
     * @return the names of all currently defined session attributes as an array of Strings. If there are no defined
     *             attributes, a zero-length array is returned.
     */
    protected String[] keys() {

        return attributes.keySet().toArray(EMPTY_ARRAY);

    }


    /**
     * Remove the object bound with the specified name from this session. If the session does not have an object bound
     * with this name, this method does nothing.
     * <p>
     * After this method executes, and if the object implements <code>HttpSessionBindingListener</code>, the container
     * calls <code>valueUnbound()</code> on the object.
     *
     * @param name   Name of the object to remove from this session.
     * @param notify Should we notify interested listeners that this attribute is being removed?
     */
    protected void removeAttributeInternal(String name, boolean notify) {

        // Avoid NPE
        if (name == null) {
            return;
        }

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
        Context context = manager.getContext();
        Object listeners[] = context.getApplicationEventListeners();
        if (listeners == null) {
            return;
        }
        for (Object o : listeners) {
            if (!(o instanceof HttpSessionAttributeListener)) {
                continue;
            }
            HttpSessionAttributeListener listener = (HttpSessionAttributeListener) o;
            try {
                context.fireContainerEvent("beforeSessionAttributeRemoved", listener);
                if (event == null) {
                    event = new HttpSessionBindingEvent(getSession(), name, value);
                }
                listener.attributeRemoved(event);
                context.fireContainerEvent("afterSessionAttributeRemoved", listener);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                try {
                    context.fireContainerEvent("afterSessionAttributeRemoved", listener);
                } catch (Exception e) {
                    // Ignore
                }
                manager.getContext().getLogger().error(sm.getString("standardSession.attributeEvent"), t);
            }
        }
    }
}