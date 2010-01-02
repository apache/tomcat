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


import java.io.Serializable;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionContext;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import org.apache.tomcat.servlets.util.Enumerator;

/**
 * Standard implementation of the <b>Session</b> interface.  
 * 
 * This is a minimal, non-serializable HttpSession. You can use a different 
 * session manager, but you should keep in mind that persistent or distributed
 * sessions are a bad thing. 
 * 
 * The session is best for caching data across requests, and tracking the 
 * user flow. For any data you don't want to lose or is worth preserving - 
 * use a transaction manager, or any form of storage that provides the 
 * set of ACID characteristics you need. 
 * 
 * Even the most sophisticated sessions managers can't guarantee data integrity
 * in 100% of cases, and can't notify you of the cases where a replication 
 * failed. Using such a manager might fool users into making incorrect 
 * assumptions. Computers and networks do crash at random points, and all
 * the theory on transactions exists for a good reason.
 * 
 * Note: this is a user-space implementation, i.e. this can be used in any
 * container by using the WebappSessionManager class.
 *
 * @author Costin Manolache - removed most of the code
 * @author Craig R. McClanahan
 * @author Sean Legassick
 * @author <a href="mailto:jon@latchkey.com">Jon S. Stevens</a>
 */
public class HttpSessionImpl  implements HttpSession, Serializable {

    /**
     * Type array, used as param to toArray()
     */
    protected static final String EMPTY_ARRAY[] = new String[0];

    /**
     * The HTTP session context associated with this session.
     */
    protected HttpSessionContext sessionContext = null;


    /**
     * The Manager with which this Session is associated.
     */
    protected transient SimpleSessionManager manager = null;

    /**
     * The session identifier of this Session.
     */
    protected String id = null;

    /**
     * The collection of user data attributes associated with this Session.
     */
    protected Map attributes = new HashMap();

    /**
     * The time this session was created, in milliseconds since midnight,
     * January 1, 1970 GMT.
     */
    protected long creationTime = 0L;

    /**
     * The last accessed time for this Session.
     */
    protected long lastAccessedTime = creationTime;

    /**
     * The current accessed time for this session.
     */
    protected long thisAccessedTime = creationTime;

    /**
     * The maximum time interval, in seconds, between client requests before
     * the servlet container may invalidate this session.  A negative time
     * indicates that the session should never time out.
     */
    protected int maxInactiveInterval = -1;

    /**
     * The access count for this session - how many requests are using this
     * session ( so we can prevent expiry )
     */
    protected transient int accessCount = 0;

    /**
     * We are currently processing a session expiration, so bypass
     * certain IllegalStateException tests.  
     */
    protected transient boolean expiring = false;


    /**
     * Flag indicating whether this session is new or not.
     */
    protected boolean isNew = false;


    /**
     * Flag indicating whether this session is valid or not.
     */
    protected boolean isValid = false;


    /** Only the manager can create sessions, so it knows about them.
     */
    HttpSessionImpl(SimpleSessionManager manager) {
        this.manager = manager;
    }

    // ----------   API methods ---------

    /**
     * Return the session identifier for this session.
     */
    public String getId() {
        return this.id;
    }

    /**
     * Return the last time the client sent a request associated with this
     * session, as the number of milliseconds since midnight, January 1, 1970
     * GMT.  Actions that your application takes, such as getting or setting
     * a value associated with the session, do not affect the access time.
     */
    public long getLastAccessedTime() {
        checkValid();
         return this.lastAccessedTime;

    }

    /**
     * Return the maximum time interval, in seconds, between client requests
     * before the servlet container will invalidate the session.  A negative
     * time indicates that the session should never time out.
     */
    public int getMaxInactiveInterval() {
        return this.maxInactiveInterval;
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
     * Return the time when this session was created, in milliseconds since
     * midnight, January 1, 1970 GMT.
     *
     * @exception IllegalStateException if this method is called on an
     *  invalidated session
     */
    public long getCreationTime() {
        checkValid();
        return this.creationTime;

    }

    public ServletContext getServletContext() {
        if (manager == null)
            return null; // Should never happen
        ServletContext context = (ServletContext)manager.getContext();
        return context;
    }


    public HttpSessionContext getSessionContext() {
        if (sessionContext == null)
            sessionContext = new StandardSessionContext();
        return (sessionContext);
    }

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
        checkValid();
        return attributes.get(name);
    }


    /**
     * Return an <code>Enumeration</code> of <code>String</code> objects
     * containing the names of the objects bound to this session.
     *
     * @exception IllegalStateException if this method is called on an
     *  invalidated session
     */
    public Enumeration getAttributeNames() {
        checkValid();
        return new Enumerator(attributes.keySet(), true);
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
        checkValid();
        return keys(); // same, but no check for validity
    }


    /**
     * Invalidates this session and unbinds any objects bound to it.
     *
     * @exception IllegalStateException if this method is called on
     *  an invalidated session
     */
    public void invalidate() {
        checkValid();
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
        checkValid();
        return (this.isNew);
    }

    public void putValue(String name, Object value) {
        setAttribute(name, value);
    }

    public void removeAttribute(String name) {
        checkValid();
        removeAttributeInternal(name, true);
    }

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
        // Name cannot be null
        if (name == null) return;

        // Null value is the same as removeAttribute()
        if (value == null) {
            removeAttribute(name);
            return;
        }

        checkValid();
        
        if ((manager != null) && manager.getDistributable() &&
          !(value instanceof Serializable))
            throw new IllegalArgumentException("setAttribute() not serializable");

        // Construct an event with the new value
        HttpSessionBindingEvent event = null;

        // Call the valueBound() method if necessary
        if (value instanceof HttpSessionBindingListener) {
            // Don't call any notification if replacing with the same value
            Object oldValue = attributes.get(name);
            if (value != oldValue) {
                event = new HttpSessionBindingEvent(getSession(), name, value);
                try {
                    ((HttpSessionBindingListener) value).valueBound(event);
                } catch (Throwable t){
                    manager.log.log(Level.SEVERE, "Listener valueBound() error", t); 
                }
            }
        }

        // Replace or add this attribute
        Object unbound = attributes.put(name, value);

        // Call the valueUnbound() method if necessary
        if ((unbound != null) && (unbound != value) &&
            (unbound instanceof HttpSessionBindingListener)) {
            try {
                ((HttpSessionBindingListener) unbound).valueUnbound
                    (new HttpSessionBindingEvent(getSession(), name));
            } catch (Throwable t) {
                manager.log.log(Level.SEVERE, "Listener valueUnbound()", t);
            }
        }

        // Notify interested application event listeners
        ServletContext context = manager.getContext();
        List listeners = manager.getEventListeners();
        if (listeners.size() == 0)
            return;
        for (int i = 0; i < listeners.size(); i++) {
            if (!(listeners.get(i) instanceof HttpSessionAttributeListener))
                continue;
            HttpSessionAttributeListener listener =
                (HttpSessionAttributeListener) listeners.get(i);
            try {
                if (unbound != null) {
                    if (event == null) {
                        event = new HttpSessionBindingEvent
                            (getSession(), name, unbound);
                    }
                    listener.attributeReplaced(event);
                } else {
                    if (event == null) {
                        event = new HttpSessionBindingEvent
                            (getSession(), name, value);
                    }
                    listener.attributeAdded(event);
                }
            } catch (Throwable t) {
                manager.log.log(Level.SEVERE, "Listener attibuteAdded/Replaced()", t);
            }
        }

    }

    // -------- Implementation - interactions with SessionManager -----
    
    /**
     * Set the creation time for this session.  This method is called by the
     * Manager when an existing Session instance is reused.
     */
    public void setCreationTime(long time) {
        this.creationTime = time;
        this.lastAccessedTime = time;
        this.thisAccessedTime = time;
    }

    /**
     * Set the session identifier for this session and notify listeners about
     * new session
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
     */
    public void tellNew() {
        // Notify interested application event listeners
        ServletContext context = manager.getContext();
        List listeners = manager.getEventListeners();
        if (listeners.size() > 0) {
            HttpSessionEvent event =
                new HttpSessionEvent(getSession());
            for (int i = 0; i < listeners.size(); i++) {
                Object listenerObj = listeners.get(i);
                if (!(listenerObj instanceof HttpSessionListener))
                    continue;
                HttpSessionListener listener =
                    (HttpSessionListener) listenerObj;
                try {
                    listener.sessionCreated(event);
                } catch (Throwable t) {
                    manager.log.log(Level.SEVERE, "listener.sessionCreated()", t);
                }
            }
        }

    }

    /**
     * Return the Manager within which this Session is valid.
     */
    public SimpleSessionManager getManager() {
        return (this.manager);
    }



    /**
     * Set the <code>isNew</code> flag for this session.
     *
     * @param isNew The new value for the <code>isNew</code> flag
     */
    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }

    public HttpSession getSession() {
        return this;
    }

    private void checkValid() {
        if ( !isValid() ) {
            throw new IllegalStateException("checkValid");
        }
    }

    /**
     * Return the <code>isValid</code> flag for this session.
     */
    public boolean isValid() {
        if (this.expiring) {
            return true;
        }
        if (!this.isValid ) {
            return false;
        }
        if (accessCount > 0) {
            return true;
        }
        if (maxInactiveInterval >= 0) { 
            long timeNow = System.currentTimeMillis();
            int timeIdle = (int) ((timeNow - thisAccessedTime) / 1000L);
            if (timeIdle >= maxInactiveInterval) {
                expire(true);
            }
        }
        return this.isValid;
    }

    public void setValid(boolean isValid) {
        this.isValid = isValid;
    }

    /**
     * Update the accessed time information for this session.  This method
     * should be called by the context when a request comes in for a particular
     * session, even if the application does not reference it.
     */
    public void access() {
        this.lastAccessedTime = this.thisAccessedTime;
        this.thisAccessedTime = System.currentTimeMillis();

        evaluateIfValid();
        accessCount++;
    }


    /**
     * End the access.
     */
    public void endAccess() {
        isNew = false;
        accessCount--;
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
            ServletContext context = manager.getContext();
            List listeners = manager.getEventListeners();
            if (notify && (listeners.size() > 0)) {
                HttpSessionEvent event =
                    new HttpSessionEvent(getSession());
                for (int i = 0; i < listeners.size(); i++) {
                    Object listenerObj = listeners.get(i);
                    int j = (listeners.size() - 1) - i;
                    if (!(listenerObj instanceof HttpSessionListener))
                        continue;
                    HttpSessionListener listener =
                        (HttpSessionListener) listenerObj;
                    try {
                        listener.sessionDestroyed(event);
                    } catch (Throwable t) {
                        manager.log.log(Level.SEVERE, "listener.sessionDestroyed", t);
                    }
                }
            }
            accessCount = 0;
            isValid = false;

            /*
             * Compute how long this session has been alive, and update
             * session manager's related properties accordingly
             */
            long timeNow = System.currentTimeMillis();
            int timeAlive = (int) ((timeNow - creationTime)/1000);
            manager.addExpiredSession(timeAlive);

            // Remove this session from our manager's active sessions
            manager.remove(this);

            expiring = false;

            // Unbind any objects associated with this session
            String keys[] = keys();
            for (int i = 0; i < keys.length; i++)
                removeAttributeInternal(keys[i], notify);
        }
    }


    /**
     * Release all object references, and initialize instance variables, in
     * preparation for reuse of this object.
     */
    public void recycle() {

        // Reset the instance variables associated with this Session
        attributes.clear();
        creationTime = 0L;
        expiring = false;
        id = null;
        lastAccessedTime = 0L;
        maxInactiveInterval = -1;
        accessCount = 0;
        isNew = false;
        isValid = false;
        manager = null;

    }

    /**
     * Return a string representation of this object.
     */
    public String toString() {

        StringBuilder sb = new StringBuilder();
        sb.append("StandardSession[");
        sb.append(id);
        sb.append("]");
        return (sb.toString());
    }

    protected void evaluateIfValid() {
        /*
         * If this session has expired or is in the process of expiring or
         * will never expire, return
         */
        if (!this.isValid || expiring || maxInactiveInterval < 0)
            return;

        isValid();

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
        ServletContext context = manager.getContext();
        List listeners = manager.getEventListeners();
        if (listeners.size() == 0)
            return;
        for (int i = 0; i < listeners.size(); i++) {
            if (!(listeners.get(i) instanceof HttpSessionAttributeListener))
                continue;
            HttpSessionAttributeListener listener =
                (HttpSessionAttributeListener) listeners.get(i);
            try {
                if (event == null) {
                    event = new HttpSessionBindingEvent
                        (getSession(), name, value);
                }
                listener.attributeRemoved(event);
            } catch (Throwable t) {
                manager.log.log(Level.SEVERE, "listener.attributeRemoved", t);
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
