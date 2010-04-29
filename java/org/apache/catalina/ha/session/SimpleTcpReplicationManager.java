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

import java.io.IOException;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Session;
import org.apache.catalina.ha.CatalinaCluster;
import org.apache.catalina.ha.ClusterManager;
import org.apache.catalina.ha.ClusterMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.tribes.io.ReplicationStream;
import org.apache.catalina.util.LifecycleBase;

import java.io.ByteArrayInputStream;
import org.apache.catalina.Loader;

/**
 * Title:        Tomcat Session Replication for Tomcat 4.0 <BR>
 * Description:  A very simple straight forward implementation of
 *               session replication of servers in a cluster.<BR>
 *               This session replication is implemented "live". By live
 *               I mean, when a session attribute is added into a session on Node A
 *               a message is broadcasted to other messages and setAttribute is called on the
 *               replicated sessions.<BR>
 *               A full description of this implementation can be found under
 *               <href="http://www.filip.net/tomcat/">Filip's Tomcat Page</a><BR>
 *
 * Copyright:    See apache license<BR>
 * Company:      www.filip.net<BR>
 * 
 * Description: The InMemoryReplicationManager is a session manager that replicated
 * session information in memory. 
 * <BR><BR>
 * The InMemoryReplicationManager extends the StandardManager hence it allows for us
 * to inherit all the basic session management features like expiration, session listeners etc
 * <BR><BR>
 * To communicate with other nodes in the cluster, the InMemoryReplicationManager sends out 7 different type of multicast messages
 * all defined in the SessionMessage class.<BR>
 * When a session is replicated (not an attribute added/removed) the session is serialized into
 * a byte array using the StandardSession.readObjectData, StandardSession.writeObjectData methods.
 * 
 * @author  <a href="mailto:mail@filip.net">Filip Hanik</a>
 * @author Bela Ban (modifications for synchronous replication)
 * @version 1.0 for TC 4.0
 */
public class SimpleTcpReplicationManager extends StandardManager implements ClusterManager
{
    public static final org.apache.juli.logging.Log log = org.apache.juli.logging.LogFactory.getLog( SimpleTcpReplicationManager.class );

    //the channel configuration
    protected String mChannelConfig = null;

    //the group name
    protected String mGroupName = "TomcatReplication";

    //log to screen
    protected boolean mPrintToScreen = true;

    protected boolean defaultMode = false;

    /** Use synchronous rather than asynchronous replication. Every session modification (creation, change, removal etc)
     * will be sent to all members. The call will then wait for max milliseconds, or forever (if timeout is 0) for
     * all responses.
     */
    protected boolean synchronousReplication=true;

    /** Set to true if we don't want the sessions to expire on shutdown */
    protected boolean mExpireSessionsOnShutdown = true;

    protected boolean useDirtyFlag = false;

    protected String name;

    protected boolean distributable = true;

    protected CatalinaCluster cluster;

    protected java.util.HashMap<String, String> invalidatedSessions =
        new java.util.HashMap<String, String>();

    /**
     * Flag to keep track if the state has been transferred or not
     * Assumes false.
     */
    protected boolean stateTransferred = false;
    private boolean notifyListenersOnReplication;
    private boolean sendClusterDomainOnly = true ;

    /**
     * Constructor, just calls super()
     *
     */
    public SimpleTcpReplicationManager()
    {
        super();
    }

    public boolean doDomainReplication() {
        return sendClusterDomainOnly;
    }
    
    /**
     * @param sendClusterDomainOnly The sendClusterDomainOnly to set.
     */
    public void setDomainReplication(boolean sendClusterDomainOnly) {
        this.sendClusterDomainOnly = sendClusterDomainOnly;
    }
  
    /**
     * @return Returns the defaultMode.
     */
    public boolean isDefaultMode() {
        return defaultMode;
    }
    /**
     * @param defaultMode The defaultMode to set.
     */
    public void setDefaultMode(boolean defaultMode) {
        this.defaultMode = defaultMode;
    }
    
    public void setUseDirtyFlag(boolean usedirtyflag)
    {
        this.useDirtyFlag = usedirtyflag;
    }

    public void setExpireSessionsOnShutdown(boolean expireSessionsOnShutdown)
    {
        mExpireSessionsOnShutdown = expireSessionsOnShutdown;
    }

    public void setCluster(CatalinaCluster cluster) {
        if(log.isDebugEnabled())
            log.debug("Cluster associated with SimpleTcpReplicationManager");
        this.cluster = cluster;
    }

    public boolean getExpireSessionsOnShutdown()
    {
        return mExpireSessionsOnShutdown;
    }

    public void setPrintToScreen(boolean printtoscreen)
    {
        if(log.isDebugEnabled())
            log.debug("Setting screen debug to:"+printtoscreen);
        mPrintToScreen = printtoscreen;
    }

    public void setSynchronousReplication(boolean flag)
    {
        synchronousReplication=flag;
    }

    /**
     * Override persistence since they don't go hand in hand with replication for now.
     */
    @Override
    public void unload() throws IOException {
        if ( !getDistributable() ) {
            super.unload();
        }
    }

    /**
     * Creates a HTTP session.
     * Most of the code in here is copied from the StandardManager.
     * This is not pretty, yeah I know, but it was necessary since the
     * StandardManager had hard coded the session instantiation to the a
     * StandardSession, when we actually want to instantiate a ReplicatedSession<BR>
     * If the call comes from the Tomcat servlet engine, a SessionMessage goes out to the other
     * nodes in the cluster that this session has been created.
     * @param notify - if set to true the other nodes in the cluster will be notified.
     *                 This flag is needed so that we can create a session before we deserialize
     *                 a replicated one
     *
     * @see ReplicatedSession
     */
    protected Session createSession(String sessionId, boolean notify, boolean setId)
    {

        //inherited from the basic manager
        if ((getMaxActiveSessions() >= 0) &&
           (sessions.size() >= getMaxActiveSessions()))
            throw new IllegalStateException(sm.getString("standardManager.createSession.ise"));


        Session session = new ReplicatedSession(this);

        // Initialize the properties of the new session and return it
        session.setNew(true);
        session.setValid(true);
        session.setCreationTime(System.currentTimeMillis());
        session.setMaxInactiveInterval(this.maxInactiveInterval);
        if(sessionId == null)
            sessionId = generateSessionId();
        if ( setId ) session.setId(sessionId);
        if ( notify && (cluster!=null) ) {
            ((ReplicatedSession)session).setIsDirty(true);
        }
        return (session);
    }//createSession

    //=========================================================================
    // OVERRIDE THESE METHODS TO IMPLEMENT THE REPLICATION
    //=========================================================================

    /**
     * Construct and return a new session object, based on the default
     * settings specified by this Manager's properties.  The session
     * id will be assigned by this method, and available via the getId()
     * method of the returned session.  If a new session cannot be created
     * for any reason, return <code>null</code>.
     *
     * @exception IllegalStateException if a new session cannot be
     *  instantiated for any reason
     */
    @Override
    public Session createSession(String sessionId)
    {
        //create a session and notify the other nodes in the cluster
        Session session =  createSession(sessionId,getDistributable(),true);
        add(session);
        return session;
    }

    public void sessionInvalidated(String sessionId) {
        synchronized ( invalidatedSessions ) {
            invalidatedSessions.put(sessionId, sessionId);
        }
    }

    public String[] getInvalidatedSessions() {
        synchronized ( invalidatedSessions ) {
            String[] result = new String[invalidatedSessions.size()];
            invalidatedSessions.values().toArray(result);
            return result;
        }

    }

    public ClusterMessage requestCompleted(String sessionId)
    {
        if (  !getDistributable() ) {
            log.warn("Received requestCompleted message, although this context["+
                     getName()+"] is not distributable. Ignoring message");
            return null;
        }
        try
        {
            if ( invalidatedSessions.get(sessionId) != null ) {
                synchronized ( invalidatedSessions ) {
                    invalidatedSessions.remove(sessionId);
                    SessionMessage msg = new SessionMessageImpl(name,
                    SessionMessage.EVT_SESSION_EXPIRED,
                    null,
                    sessionId,
                    sessionId);
                return msg;
                }
            } else {
                ReplicatedSession session = (ReplicatedSession) findSession(
                    sessionId);
                if (session != null) {
                    //return immediately if the session is not dirty
                    if (useDirtyFlag && (!session.isDirty())) {
                        //but before we return doing nothing,
                        //see if we should send
                        //an updated last access message so that
                        //sessions across cluster dont expire
                        long interval = session.getMaxInactiveInterval();
                        long lastaccdist = System.currentTimeMillis() -
                            session.getLastAccessWasDistributed();
                        if ( ((interval*1000) / lastaccdist)< 3 ) {
                            SessionMessage accmsg = new SessionMessageImpl(name,
                                SessionMessage.EVT_SESSION_ACCESSED,
                                null,
                                sessionId,
                                sessionId);
                            session.setLastAccessWasDistributed(System.currentTimeMillis());
                            return accmsg;
                        }
                        return null;
                    }

                    session.setIsDirty(false);
                    if (log.isDebugEnabled()) {
                        try {
                            log.debug("Sending session to cluster=" + session);
                        }
                        catch (Exception ignore) {}
                    }
                    SessionMessage msg = new SessionMessageImpl(name,
                        SessionMessage.EVT_SESSION_CREATED,
                        writeSession(session),
                        session.getIdInternal(),
                        session.getIdInternal());
                    return msg;
                } //end if
            }//end if
        }
        catch (Exception x )
        {
            log.error("Unable to replicate session",x);
        }
        return null;
    }

    /**
     * Serialize a session into a byte array<BR>
     * This method simple calls the writeObjectData method on the session
     * and returns the byte data from that call
     * @param session - the session to be serialized
     * @return a byte array containing the session data, null if the serialization failed
     */
    protected byte[] writeSession( Session session )
    {
        try
        {
            java.io.ByteArrayOutputStream session_data = new java.io.ByteArrayOutputStream();
            java.io.ObjectOutputStream session_out = new java.io.ObjectOutputStream(session_data);
            session_out.flush();
            boolean hasPrincipal = session.getPrincipal() != null;
            session_out.writeBoolean(hasPrincipal);
            if ( hasPrincipal )
            {
                session_out.writeObject(SerializablePrincipal.createPrincipal((GenericPrincipal)session.getPrincipal()));
            }//end if
            ((ReplicatedSession)session).writeObjectData(session_out);
            return session_data.toByteArray();

        }
        catch ( Exception x )
        {
            log.error("Failed to serialize the session!",x);
        }
        return null;
    }
    
    /**
     * Open Stream and use correct ClassLoader (Container) Switch
     * ThreadClassLoader
     * 
     * @param data
     * @return The object input stream
     * @throws IOException
     */
    public ReplicationStream getReplicationStream(byte[] data) throws IOException {
        return getReplicationStream(data,0,data.length);
    }
    
    public ReplicationStream getReplicationStream(byte[] data, int offset, int length) throws IOException {
        ByteArrayInputStream fis =null;
        ReplicationStream ois = null;
        Loader loader = null;
        ClassLoader classLoader = null;
        //fix to be able to run the DeltaManager
        //stand alone without a container.
        //use the Threads context class loader
        if (container != null)
            loader = container.getLoader();
        if (loader != null)
            classLoader = loader.getClassLoader();
        else
            classLoader = Thread.currentThread().getContextClassLoader();
        //end fix
        fis = new ByteArrayInputStream(data, offset, length);
        if ( classLoader == Thread.currentThread().getContextClassLoader() ) {
            ois = new ReplicationStream(fis, new ClassLoader[] {classLoader});
        } else {
            ois = new ReplicationStream(fis, new ClassLoader[] {classLoader,Thread.currentThread().getContextClassLoader()});
        }
        return ois;
    }    


    

    /**
     * Reinstantiates a serialized session from the data passed in.
     * This will first call createSession() so that we get a fresh instance with all
     * the managers set and all the transient fields validated.
     * Then it calls Session.readObjectData(byte[]) to deserialize the object
     * @param data - a byte array containing session data
     * @return a valid Session object, null if an error occurs
     *
     */
    protected Session readSession( byte[] data, String sessionId )
    {
        try
        {
            ReplicationStream session_in = getReplicationStream(data);

            Session session = sessionId!=null?this.findSession(sessionId):null;
            boolean isNew = (session==null);
            //clear the old values from the existing session
            if ( session!=null ) {
                ReplicatedSession rs = (ReplicatedSession)session;
                rs.expire(false);  //cleans up the previous values, since we are not doing removes
                session = null;
            }//end if

            if (session==null) {
                session = createSession(null,false, false);
                sessions.remove(session.getIdInternal());
            }
            
            
            boolean hasPrincipal = session_in.readBoolean();
            SerializablePrincipal p = null;
            if ( hasPrincipal )
                p = (SerializablePrincipal)session_in.readObject();
            ((ReplicatedSession)session).readObjectData(session_in);
            if ( hasPrincipal )
                session.setPrincipal(p.getPrincipal());
            ((ReplicatedSession)session).setId(sessionId,isNew);
            ReplicatedSession rsession = (ReplicatedSession)session; 
            rsession.setAccessCount(1);
            session.setManager(this);
            session.setValid(true);
            rsession.setLastAccessedTime(System.currentTimeMillis());
            rsession.setThisAccessedTime(System.currentTimeMillis());
            ((ReplicatedSession)session).setAccessCount(0);
            session.setNew(false);
            if(log.isTraceEnabled())
                 log.trace("Session loaded id="+sessionId +
                               " actualId="+session.getId()+ 
                               " exists="+this.sessions.containsKey(sessionId)+
                               " valid="+rsession.isValid());
            return session;

        }
        catch ( Exception x )
        {
            log.error("Failed to deserialize the session!",x);
        }
        return null;
    }

    @Override
    public String getName() {
        return this.name;
    }


    /**
     * Start this component and implement the requirements
     * of {@link LifecycleBase#startInternal()}.
     *
     * Starts the cluster communication channel, this will connect with the
     * other nodes in the cluster, and request the current session state to be
     * transferred to this node.
     * 
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        try {
            if(log.isInfoEnabled())
                log.info("Starting clustering manager...:"+getName());
            if ( cluster == null ) {
                log.error("Starting... no cluster associated with this context:"+getName());
                setState(LifecycleState.FAILED);
                return;
            }
            cluster.registerManager(this);

            if (cluster.getMembers().length > 0) {
                Member mbr = cluster.getMembers()[0];
                SessionMessage msg =
                    new SessionMessageImpl(this.getName(),
                                       SessionMessage.EVT_GET_ALL_SESSIONS,
                                       null,
                                       "GET-ALL",
                                       "GET-ALL-"+this.getName());
                cluster.send(msg, mbr);
                if(log.isWarnEnabled())
                     log.warn("Manager["+getName()+"], requesting session state from "+mbr+
                         ". This operation will timeout if no session state has been received within "+
                         "60 seconds");
                long reqStart = System.currentTimeMillis();
                long reqNow = 0;
                boolean isTimeout=false;
                do {
                    try {
                        Thread.sleep(100);
                    }catch ( Exception sleep) {}
                    reqNow = System.currentTimeMillis();
                    isTimeout=((reqNow-reqStart)>(1000*60));
                } while ( (!isStateTransferred()) && (!isTimeout));
                if ( isTimeout || (!isStateTransferred()) ) {
                    log.error("Manager["+getName()+"], No session state received, timing out.");
                }else {
                    if(log.isInfoEnabled())
                        log.info("Manager["+getName()+"], session state received in "+(reqNow-reqStart)+" ms.");
                }
            } else {
                if(log.isInfoEnabled())
                    log.info("Manager["+getName()+"], skipping state transfer. No members active in cluster group.");
            }//end if
            super.startInternal();
        }  catch ( Exception x ) {
            log.error("Unable to start SimpleTcpReplicationManager",x);
            setState(LifecycleState.FAILED);
        }
    }


    /**
     * Stop this component and implement the requirements
     * of {@link LifecycleBase#stopInternal()}.
     *
     * This will disconnect the cluster communication channel and stop the
     * listener thread.
     * 
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        super.stopInternal();
        try
        {
            this.sessions.clear();
            cluster.removeManager(this);
        }
        catch ( Exception x )
        {
            log.error("Unable to stop SimpleTcpReplicationManager",x);
        }
    }

    @Override
    public void setDistributable(boolean dist) {
        this.distributable = dist;
    }

    @Override
    public boolean getDistributable() {
        return distributable;
    }

    /**
     * This method is called by the received thread when a SessionMessage has
     * been received from one of the other nodes in the cluster.
     * @param msg - the message received
     * @param sender - the sender of the message, this is used if we receive a
     *                 EVT_GET_ALL_SESSION message, so that we only reply to
     *                 the requesting node
     */
    protected void messageReceived( SessionMessage msg, Member sender ) {
        try  {
            if(log.isInfoEnabled()) {
                log.debug("Received SessionMessage of type="+msg.getEventTypeString());
                log.debug("Received SessionMessage sender="+sender);
            }
            switch ( msg.getEventType() ) {
                case SessionMessage.EVT_GET_ALL_SESSIONS: {
                    //get a list of all the session from this manager
                    Object[] sessions = findSessions();
                    java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream();
                    java.io.ObjectOutputStream oout = new java.io.ObjectOutputStream(bout);
                    oout.writeInt(sessions.length);
                    for (int i=0; i<sessions.length; i++){
                        ReplicatedSession ses = (ReplicatedSession)sessions[i];
                        oout.writeUTF(ses.getIdInternal());
                        byte[] data = writeSession(ses);
                        oout.writeObject(data);
                    }//for
                    //don't send a message if we don't have to
                    oout.flush();
                    oout.close();
                    byte[] data = bout.toByteArray();
                    SessionMessage newmsg = new SessionMessageImpl(name,
                        SessionMessage.EVT_ALL_SESSION_DATA,
                        data, "SESSION-STATE","SESSION-STATE-"+getName());
                    cluster.send(newmsg, sender);
                    break;
                }
                case SessionMessage.EVT_ALL_SESSION_DATA: {
                    java.io.ByteArrayInputStream bin =
                        new java.io.ByteArrayInputStream(msg.getSession());
                    java.io.ObjectInputStream oin = new java.io.ObjectInputStream(bin);
                    int size = oin.readInt();
                    for ( int i=0; i<size; i++) {
                        String id = oin.readUTF();
                        byte[] data = (byte[])oin.readObject();
                        readSession(data,id);
                    }//for
                    stateTransferred=true;
                    break;
                }
                case SessionMessage.EVT_SESSION_CREATED: {
                    Session session = this.readSession(msg.getSession(),msg.getSessionID());
                    if ( log.isDebugEnabled() ) {
                        log.debug("Received replicated session=" + session +
                            " isValid=" + session.isValid());
                    }
                    break;
                }
                case SessionMessage.EVT_SESSION_EXPIRED: {
                    Session session = findSession(msg.getSessionID());
                    if ( session != null ) {
                        session.expire();
                        this.remove(session);
                    }//end if
                    break;
                }
                case SessionMessage.EVT_SESSION_ACCESSED :{
                    Session session = findSession(msg.getSessionID());
                    if ( session != null ) {
                        session.access();
                        session.endAccess();
                    }
                    break;
                }
                default:  {
                    //we didn't recognize the message type, do nothing
                    break;
                }
            }//switch
        }
        catch ( Exception x )
        {
            log.error("Unable to receive message through TCP channel",x);
        }
    }

    public void messageDataReceived(ClusterMessage cmsg) {
        try {
            if ( cmsg instanceof SessionMessage ) {
                SessionMessage msg = (SessionMessage)cmsg;
                messageReceived(msg,
                                msg.getAddress() != null ? (Member) msg.getAddress() : null);
            }
        } catch(Throwable ex){
            log.error("InMemoryReplicationManager.messageDataReceived()", ex);
        }//catch
    }

    public boolean isStateTransferred() {
        return stateTransferred;
    }

    public void setName(String name) {
        this.name = name;
    }
    public boolean isNotifyListenersOnReplication() {
        return notifyListenersOnReplication;
    }
    public void setNotifyListenersOnReplication(boolean notifyListenersOnReplication) {
        this.notifyListenersOnReplication = notifyListenersOnReplication;
    }


    /* 
     * @see org.apache.catalina.ha.ClusterManager#getCluster()
     */
    public CatalinaCluster getCluster() {
        return cluster;
    }

    public ClusterManager cloneFromTemplate() {
        throw new UnsupportedOperationException();
    }

}
