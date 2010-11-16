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
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.mbeans.MBeanUtils;
import org.apache.catalina.util.Base64;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;


/**
 * Minimal implementation of the <b>Manager</b> interface that supports
 * no session persistence or distributable capabilities.  This class may
 * be subclassed to create more sophisticated Manager implementations.
 *
 * @author Craig R. McClanahan
 * @version $Id$
 */

public abstract class ManagerBase extends LifecycleMBeanBase
        implements Manager, PropertyChangeListener {

    private final Log log = LogFactory.getLog(ManagerBase.class); // must not be static

    // ----------------------------------------------------- Instance Variables

    protected DataInputStream randomIS = null;
    protected String devRandomSource = "/dev/urandom";
    protected boolean devRandomSourceIsValid = true;

    /**
     * The default message digest algorithm to use if we cannot use
     * the requested one.
     */
    protected static final String DEFAULT_ALGORITHM = "MD5";


    /**
     * The message digest algorithm to be used when generating session
     * identifiers.  This must be an algorithm supported by the
     * <code>java.security.MessageDigest</code> class on your platform.
     */
    protected String algorithm = DEFAULT_ALGORITHM;


    /**
     * The Container with which this Manager is associated.
     */
    protected Container container;


    /**
     * Return the MessageDigest implementation to be used when
     * creating session identifiers.
     */
    protected Queue<MessageDigest> digests =
        new ConcurrentLinkedQueue<MessageDigest>();


    /**
     * The distributable flag for Sessions created by this Manager.  If this
     * flag is set to <code>true</code>, any user attributes added to a
     * session controlled by this Manager must be Serializable.
     */
    protected boolean distributable;


    /**
     * A String initialization parameter used to increase the entropy of
     * the initialization of our random number generator.
     */
    protected String entropy = null;


    /**
     * The descriptive information string for this implementation.
     */
    private static final String info = "ManagerBase/1.0";


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
     * The descriptive name of this Manager implementation (for logging).
     */
    protected static String name = "ManagerBase";


    /**
     * A random number generator to use when generating session identifiers.
     */
    protected Random random = null;


    /**
     * The Java class name of the random number generator class to be used
     * when generating session identifiers.
     */
    protected String randomClass = "java.security.SecureRandom";


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
    protected long expiredSessions = 0;


    /**
     * The set of currently active Sessions for this Manager, keyed by
     * session identifier.
     */
    protected Map<String, Session> sessions = new ConcurrentHashMap<String, Session>();

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
    protected PropertyChangeSupport support = new PropertyChangeSupport(this);
    

    // ------------------------------------------------------------- Security classes


    private class PrivilegedSetRandomFile implements PrivilegedAction<DataInputStream> {
        
        public PrivilegedSetRandomFile(String s) {
            devRandomSource = s;
        }
        
        @Override
        public DataInputStream run(){
            devRandomSourceIsValid = true;
            try {
                File f=new File( devRandomSource );
                if( ! f.exists() ) {
                    devRandomSourceIsValid = false;
                    return null;
                }
                randomIS= new DataInputStream( new FileInputStream(f));
                randomIS.readLong();
                if( log.isDebugEnabled() )
                    log.debug( "Opening " + devRandomSource );
                return randomIS;
            } catch (IOException ex){
                log.warn("Error reading " + devRandomSource, ex);
                if (randomIS != null) {
                    try {
                        randomIS.close();
                    } catch (Exception e) {
                        log.warn("Failed to close randomIS.");
                    }
                }
                devRandomSourceIsValid = false;
                randomIS=null;
                return null;
            }            
        }
    }


    // ------------------------------------------------------------- Properties

    /**
     * Return the message digest algorithm for this Manager.
     */
    public String getAlgorithm() {

        return (this.algorithm);

    }


    /**
     * Set the message digest algorithm for this Manager.
     *
     * @param algorithm The new message digest algorithm
     */
    public void setAlgorithm(String algorithm) {

        String oldAlgorithm = this.algorithm;
        this.algorithm = algorithm;
        support.firePropertyChange("algorithm", oldAlgorithm, this.algorithm);

    }


    /**
     * Return the Container with which this Manager is associated.
     */
    @Override
    public Container getContainer() {

        return (this.container);

    }


    /**
     * Set the Container with which this Manager is associated.
     *
     * @param container The newly associated Container
     */
    @Override
    public void setContainer(Container container) {

        // De-register from the old Container (if any)
        if ((this.container != null) && (this.container instanceof Context))
            ((Context) this.container).removePropertyChangeListener(this);

        Container oldContainer = this.container;
        this.container = container;
        support.firePropertyChange("container", oldContainer, this.container);

        // Register with the new Container (if any)
        if ((this.container != null) && (this.container instanceof Context)) {
            setMaxInactiveInterval
                ( ((Context) this.container).getSessionTimeout()*60 );
            ((Context) this.container).addPropertyChangeListener(this);
        }

    }


    /** Returns the name of the implementation class.
     */
    public String getClassName() {
        return this.getClass().getName();
    }


    /**
     * Return the MessageDigest object to be used for calculating
     * session identifiers.  If none has been created yet, initialize
     * one the first time this method is called.
     */
    protected MessageDigest createDigest() {

        MessageDigest result;
        
        long t1=System.currentTimeMillis();
        if (log.isDebugEnabled())
            log.debug(sm.getString("managerBase.getting", algorithm));
        try {
            result = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            log.error(sm.getString("managerBase.digest", algorithm), e);
            try {
                result = MessageDigest.getInstance(DEFAULT_ALGORITHM);
            } catch (NoSuchAlgorithmException f) {
                log.error(sm.getString("managerBase.digest",
                                 DEFAULT_ALGORITHM), e);
                result = null;
            }
        }
        if (log.isDebugEnabled())
            log.debug(sm.getString("managerBase.gotten"));
        long t2=System.currentTimeMillis();
        if( log.isDebugEnabled() )
            log.debug("getDigest() " + (t2-t1));

        return result;
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
     * Return the entropy increaser value, or compute a semi-useful value
     * if this String has not yet been set.
     */
    public String getEntropy() {

        // Calculate a semi-useful value if this has not been set
        if (this.entropy == null) {
            // Use APR to get a crypto secure entropy value
            byte[] result = new byte[32];
            boolean apr = false;
            try {
                String methodName = "random";
                Class<?> paramTypes[] = new Class[2];
                paramTypes[0] = result.getClass();
                paramTypes[1] = int.class;
                Object paramValues[] = new Object[2];
                paramValues[0] = result;
                paramValues[1] = Integer.valueOf(32);
                Method method = Class.forName("org.apache.tomcat.jni.OS")
                    .getMethod(methodName, paramTypes);
                method.invoke(null, paramValues);
                apr = true;
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
            }
            if (apr) {
                setEntropy(Base64.encode(result));
            } else {
                setEntropy(this.toString());
            }
        }

        return (this.entropy);

    }


    /**
     * Set the entropy increaser value.
     *
     * @param entropy The new entropy increaser value
     */
    public void setEntropy(String entropy) {

        String oldEntropy = entropy;
        this.entropy = entropy;
        support.firePropertyChange("entropy", oldEntropy, this.entropy);

    }


    /**
     * Return descriptive information about this Manager implementation and
     * the corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    @Override
    public String getInfo() {

        return (info);

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
     * Use /dev/random-type special device. This is new code, but may reduce
     * the big delay in generating the random.
     *
     *  You must specify a path to a random generator file. Use /dev/urandom
     *  for linux ( or similar ) systems. Use /dev/random for maximum security
     *  ( it may block if not enough "random" exist ). You can also use
     *  a pipe that generates random.
     *
     *  The code will check if the file exists, and default to java Random
     *  if not found. There is a significant performance difference, very
     *  visible on the first call to getSession ( like in the first JSP )
     *  - so use it if available.
     */
    public void setRandomFile( String s ) {
        // Assume the source is valid until proved otherwise
        devRandomSourceIsValid = true;
        // as a hack, you can use a static file - and generate the same
        // session ids ( good for strange debugging )
        if (Globals.IS_SECURITY_ENABLED){
            randomIS = AccessController.doPrivileged(new PrivilegedSetRandomFile(s));
        } else {
            try{
                devRandomSource=s;
                File f=new File( devRandomSource );
                if (!f.exists()) {
                    devRandomSourceIsValid = false;
                    return;
                }
                randomIS= new DataInputStream( new FileInputStream(f));
                randomIS.readLong();
                if( log.isDebugEnabled() )
                    log.debug( "Opening " + devRandomSource );
            } catch( IOException ex ) {
                log.warn("Error reading " + devRandomSource, ex);
                if (randomIS != null) {
                    try {
                        randomIS.close();
                    } catch (Exception e) {
                        log.warn("Failed to close randomIS.");
                    }
                }
                devRandomSourceIsValid = false;
                randomIS=null;
            }
        }
    }

    public String getRandomFile() {
        return devRandomSource;
    }


    /**
     * Return the random number generator instance we should use for
     * generating session identifiers.  If there is no such generator
     * currently defined, construct and seed a new one.
     */
    public Random getRandom() {
        if (this.random == null) {
            // Calculate the new random number generator seed
            long seed = System.currentTimeMillis();
            long t1 = seed;
            char entropy[] = getEntropy().toCharArray();
            for (int i = 0; i < entropy.length; i++) {
                long update = ((byte) entropy[i]) << ((i % 8) * 8);
                seed ^= update;
            }
            try {
                // Construct and seed a new random number generator
                Class<?> clazz = Class.forName(randomClass);
                this.random = (Random) clazz.newInstance();
                this.random.setSeed(seed);
            } catch (Exception e) {
                // Fall back to the simple case
                log.error(sm.getString("managerBase.random", randomClass),
                        e);
                this.random = new java.util.Random();
                this.random.setSeed(seed);
            }
            if(log.isDebugEnabled()) {
                long t2=System.currentTimeMillis();
                if( (t2-t1) > 100 )
                    log.debug(sm.getString("managerBase.seeding", randomClass) + " " + (t2-t1));
            }
        }
        
        return (this.random);

    }


    /**
     * Return the random number generator class name.
     */
    public String getRandomClass() {

        return (this.randomClass);

    }


    /**
     * Set the random number generator class name.
     *
     * @param randomClass The new random number generator class name
     */
    public void setRandomClass(String randomClass) {

        String oldRandomClass = this.randomClass;
        this.randomClass = randomClass;
        support.firePropertyChange("randomClass", oldRandomClass,
                                   this.randomClass);

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
        return expiredSessions;
    }


    /**
     * Sets the number of sessions that have expired.
     *
     * @param expiredSessions Number of sessions that have expired
     */
    @Override
    public void setExpiredSessions(long expiredSessions) {
        this.expiredSessions = expiredSessions;
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
    protected void destroyInternal() throws LifecycleException {

        if (randomIS!=null) {
            try {
                randomIS.close();
            } catch (IOException ioe) {
                log.warn("Failed to close randomIS.");
            }
            randomIS=null;
        }
        
        super.destroyInternal();
    }
    
    @Override
    protected void initInternal() throws LifecycleException {
        
        super.initInternal();
        
        setDistributable(((Context) getContainer()).getDistributable());

        // Initialize random number generation
        getRandomBytes(new byte[16]);
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
            throw new IllegalStateException(
                    sm.getString("managerBase.createSession.ise"));
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
        session.setId(generateSessionId());
    }
    
    
    // ------------------------------------------------------ Protected Methods


    /**
     * Get new session class to be used in the doLoad() method.
     */
    protected StandardSession getNewSession() {
        return new StandardSession(this);
    }


    protected synchronized void getRandomBytes(byte bytes[]) {
        // Generate a byte array containing a session identifier
        if (devRandomSourceIsValid && randomIS == null) {
            setRandomFile(devRandomSource);
        }
        if (randomIS != null) {
            try {
                int len = randomIS.read(bytes);
                if (len == bytes.length) {
                    return;
                }
                if(log.isDebugEnabled())
                    log.debug("Got " + len + " " + bytes.length );
            } catch (Exception ex) {
                // Ignore
            }
            devRandomSourceIsValid = false;
            
            try {
                randomIS.close();
            } catch (Exception e) {
                log.warn("Failed to close randomIS.");
            }
            
            randomIS = null;
        }
        getRandom().nextBytes(bytes);
    }


    /**
     * Generate and return a new session identifier.
     */
    protected String generateSessionId() {

        byte random[] = new byte[16];
        String jvmRoute = getJvmRoute();
        String result = null;

        // Render the result as a String of hexadecimal digits
        StringBuilder buffer = new StringBuilder();
        do {
            int resultLenBytes = 0;
            if (result != null) {
                buffer = new StringBuilder();
                duplicates++;
            }

            while (resultLenBytes < this.sessionIdLength) {
                getRandomBytes(random);
                MessageDigest md = digests.poll();
                if (md == null) {
                    // If this fails, NPEs will follow. This should never fail
                    // since if it falls back to the default digest
                    md = createDigest();
                }
                random = md.digest(random);
                digests.add(md);
                for (int j = 0;
                j < random.length && resultLenBytes < this.sessionIdLength;
                j++) {
                    byte b1 = (byte) ((random[j] & 0xf0) >> 4);
                    byte b2 = (byte) (random[j] & 0x0f);
                    if (b1 < 10)
                        buffer.append((char) ('0' + b1));
                    else
                        buffer.append((char) ('A' + (b1 - 10)));
                    if (b2 < 10)
                        buffer.append((char) ('0' + b2));
                    else
                        buffer.append((char) ('A' + (b2 - 10)));
                    resultLenBytes++;
                }
            }
            if (jvmRoute != null) {
                buffer.append('.').append(jvmRoute);
            }
            result = buffer.toString();
        } while (sessions.containsKey(result));
        return (result);

    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Retrieve the enclosing Engine for this Manager.
     *
     * @return an Engine object (or null).
     */
    public Engine getEngine() {
        Engine e = null;
        for (Container c = getContainer(); e == null && c != null ; c = c.getParent()) {
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
                                   new Integer(oldMaxActiveSessions),
                                   new Integer(this.maxActiveSessions));

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
     * alive.
     *
     * @param sessionMaxAliveTime Longest time (in seconds) that an expired
     * session had been alive.
     */
    @Override
    public void setSessionMaxAliveTime(int sessionMaxAliveTime) {
        this.sessionMaxAliveTime = sessionMaxAliveTime;
    }


    /**
     * Gets the average time (in seconds) that expired sessions had been
     * alive.
     *
     * @return Average time (in seconds) that expired sessions had been
     * alive.
     */
    @Override
    public int getSessionAverageAliveTime() {
        return sessionAverageAliveTime;
    }


    /**
     * Sets the average time (in seconds) that expired sessions had been
     * alive.
     *
     * @param sessionAverageAliveTime Average time (in seconds) that expired
     * sessions had been alive.
     */
    @Override
    public void setSessionAverageAliveTime(int sessionAverageAliveTime) {
        this.sessionAverageAliveTime = sessionAverageAliveTime;
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

        HashMap<String, String> map = new HashMap<String, String>();
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
        if (container == null) {
            sb.append("Container is null");
        } else {
            sb.append(container.getName());
        }
        sb.append(']');
        return sb.toString();
    }
    
    
    // -------------------- JMX and Registration  --------------------
    @Override
    public String getObjectNameKeyProperties() {
        
        StringBuilder name = new StringBuilder("type=Manager");
        
        if (container instanceof Context) {
            name.append(",context=");
            String contextName = container.getName();
            if (!contextName.startsWith("/")) {
                name.append('/');
            }
            name.append(contextName);
            
            Context context = (Context) container;
            name.append(",host=");
            name.append(context.getParent().getName());
        } else {
            // Unlikely / impossible? Handle it to be safe
            name.append(",container=");
            name.append(container.getName());
        }

        return name.toString();
    }

    @Override
    public String getDomainInternal() {
        return MBeanUtils.getDomain(container);
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
}
