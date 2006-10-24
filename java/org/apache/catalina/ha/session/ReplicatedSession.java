
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

package org.apache.catalina.ha.session;

/**
 * Title:        Tomcat Session Replication for Tomcat 4.0 <BR>
 * Description:  A very simple straight forward implementation of
 *               session replication of servers in a cluster.<BR>
 *               This session replication is implemented "live". By live
 *               I mean, when a session attribute is added into a session on Node A
 *               a message is broadcasted to other messages and setAttribute is called on the replicated
 *               sessions.<BR>
 *               A full description of this implementation can be found under
 *               <href="http://www.filip.net/tomcat/">Filip's Tomcat Page</a><BR>
 *
 * Copyright:    See apache license
 * @author  Filip Hanik
 * @version $Revision$ $Date$
 * Description:<BR>
 * The ReplicatedSession class is a simple extension of the StandardSession class
 * It overrides a few methods (setAttribute, removeAttribute, expire, access) and has
 * hooks into the InMemoryReplicationManager to broadcast and receive events from the cluster.<BR>
 * This class inherits the readObjectData and writeObject data methods from the StandardSession
 * and does not contain any serializable elements in addition to the inherited ones from the StandardSession
 *
 */
import org.apache.catalina.Manager;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.Principal;

public class ReplicatedSession extends org.apache.catalina.session.StandardSession
implements org.apache.catalina.ha.ClusterSession{

    private transient Manager mManager = null;
    protected boolean isDirty = false;
    private transient long lastAccessWasDistributed = System.currentTimeMillis();
    private boolean isPrimarySession=true;
    

    public ReplicatedSession(Manager manager) {
        super(manager);
        mManager = manager;
    }


    public boolean isDirty()
    {
        return isDirty;
    }

    public void setIsDirty(boolean dirty)
    {
        isDirty = dirty;
    }


    public void setLastAccessWasDistributed(long time) {
        lastAccessWasDistributed = time;
    }

    public long getLastAccessWasDistributed() {
        return lastAccessWasDistributed;
    }


    public void removeAttribute(String name) {
        setIsDirty(true);
        super.removeAttribute(name);
    }

    /**
     * see parent description,
     * plus we also notify other nodes in the cluster
     */
    public void removeAttribute(String name, boolean notify) {
        setIsDirty(true);
        super.removeAttribute(name,notify);
    }


    /**
     * Sets an attribute and notifies the other nodes in the cluster
     */
    public void setAttribute(String name, Object value)
    {
        if ( value == null ) {
          removeAttribute(name);
          return;
        }
        if (!(value instanceof java.io.Serializable))
            throw new java.lang.IllegalArgumentException("Value for attribute "+name+" is not serializable.");
        setIsDirty(true);
        super.setAttribute(name,value);
    }

    public void setMaxInactiveInterval(int interval) {
        setIsDirty(true);
        super.setMaxInactiveInterval(interval);
    }


    /**
     * Sets the manager for this session
     * @param mgr - the servers InMemoryReplicationManager
     */
    public void setManager(SimpleTcpReplicationManager mgr)
    {
        mManager = mgr;
        super.setManager(mgr);
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
        super.setPrincipal(principal);
        setIsDirty(true);
    }

    public void expire() {
        SimpleTcpReplicationManager mgr =(SimpleTcpReplicationManager)getManager();
        mgr.sessionInvalidated(getIdInternal());
        setIsDirty(true);
        super.expire();
    }

    public void invalidate() {
        SimpleTcpReplicationManager mgr =(SimpleTcpReplicationManager)getManager();
        mgr.sessionInvalidated(getIdInternal());
        setIsDirty(true);
        super.invalidate();
    }


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

        super.readObjectData(stream);

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

        super.writeObjectData(stream);

    }
    
    public void setId(String id, boolean tellNew) {

        if ((this.id != null) && (manager != null))
            manager.remove(this);

        this.id = id;

        if (manager != null)
            manager.add(this);
        if (tellNew) tellNew();
    }
    
    






    /**
     * returns true if this session is the primary session, if that is the
     * case, the manager can expire it upon timeout.
     */
    public boolean isPrimarySession() {
        return isPrimarySession;
    }

    /**
     * Sets whether this is the primary session or not.
     * @param primarySession Flag value
     */
    public void setPrimarySession(boolean primarySession) {
        this.isPrimarySession=primarySession;
    }




    /**
     * Implements a log method to log through the manager
     */
    protected void log(String message) {

        if ((mManager != null) && (mManager instanceof SimpleTcpReplicationManager)) {
            ((SimpleTcpReplicationManager) mManager).log.debug("ReplicatedSession: " + message);
        } else {
            System.out.println("ReplicatedSession: " + message);
        }

    }

    protected void log(String message, Throwable x) {

        if ((mManager != null) && (mManager instanceof SimpleTcpReplicationManager)) {
            ((SimpleTcpReplicationManager) mManager).log.error("ReplicatedSession: " + message,x);
        } else {
            System.out.println("ReplicatedSession: " + message);
            x.printStackTrace();
        }

    }

    public String toString() {
        StringBuffer buf = new StringBuffer("ReplicatedSession id=");
        buf.append(getIdInternal()).append(" ref=").append(super.toString()).append("\n");
        java.util.Enumeration e = getAttributeNames();
        while ( e.hasMoreElements() ) {
            String name = (String)e.nextElement();
            Object value = getAttribute(name);
            buf.append("\tname=").append(name).append("; value=").append(value).append("\n");
        }
        buf.append("\tLastAccess=").append(getLastAccessedTime()).append("\n");
        return buf.toString();
    }
    public int getAccessCount() {
        return accessCount.get();
    }
    public void setAccessCount(int accessCount) {
        this.accessCount.set(accessCount);
    }
    public long getLastAccessedTime() {
        return lastAccessedTime;
    }
    public void setLastAccessedTime(long lastAccessedTime) {
        this.lastAccessedTime = lastAccessedTime;
    }
    public long getThisAccessedTime() {
        return thisAccessedTime;
    }
    public void setThisAccessedTime(long thisAccessedTime) {
        this.thisAccessedTime = thisAccessedTime;
    }

}
