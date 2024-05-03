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

import java.io.Externalizable;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.WriteAbortedException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.catalina.Manager;
import org.apache.catalina.SessionListener;
import org.apache.catalina.ha.CatalinaCluster;
import org.apache.catalina.ha.ClusterManager;
import org.apache.catalina.ha.ClusterMessage;
import org.apache.catalina.ha.ClusterSession;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.session.StandardSession;
import org.apache.catalina.tribes.io.ReplicationStream;
import org.apache.catalina.tribes.tipis.ReplicatedMapEntry;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.collections.SynchronizedStack;
import org.apache.tomcat.util.res.StringManager;

/**
 * Similar to the StandardSession except that this session will keep track of deltas during a request.
 */
public class DeltaSession extends StandardSession implements Externalizable, ClusterSession, ReplicatedMapEntry {

    public static final Log log = LogFactory.getLog(DeltaSession.class);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(DeltaSession.class);

    // ----------------------------------------------------- Instance Variables

    /**
     * only the primary session will expire, or be able to expire due to inactivity. This is set to false as soon as I
     * receive this session over the wire in a session message. That means that someone else has made a request on
     * another server.
     */
    private transient boolean isPrimarySession = true;

    /**
     * The delta request contains all the action info
     */
    private transient DeltaRequest deltaRequest = null;

    /**
     * Last time the session was replicated, used for distributed expiring of session
     */
    private transient long lastTimeReplicated = System.currentTimeMillis();


    protected final Lock diffLock = new ReentrantReadWriteLock().writeLock();

    private long version;

    // ----------------------------------------------------------- Constructors

    public DeltaSession() {
        this(null);
    }

    /**
     * Construct a new Session associated with the specified Manager.
     *
     * @param manager The manager with which this Session is associated
     */
    public DeltaSession(Manager manager) {
        super(manager);
        boolean recordAllActions =
                manager instanceof ClusterManagerBase && ((ClusterManagerBase) manager).isRecordAllActions();
        deltaRequest = createRequest(getIdInternal(), recordAllActions);
    }

    private DeltaRequest createRequest() {
        return createRequest(null, false);
    }

    /*
     * DeltaRequest instances are created via this protected method to enable sub-classes to over-ride the method to use
     * custom DeltaRequest implementations.
     */
    protected DeltaRequest createRequest(String sessionId, boolean recordAllActions) {
        return new DeltaRequest(sessionId, recordAllActions);
    }

    // ----------------------------------------------------- ReplicatedMapEntry

    @Override
    public boolean isDirty() {
        return deltaRequest.getSize() > 0;
    }

    @Override
    public boolean isDiffable() {
        return true;
    }

    @Override
    public byte[] getDiff() throws IOException {
        SynchronizedStack<DeltaRequest> deltaRequestPool = null;
        DeltaRequest newDeltaRequest = null;

        if (manager instanceof ClusterManagerBase) {
            deltaRequestPool = ((ClusterManagerBase) manager).getDeltaRequestPool();
            newDeltaRequest = deltaRequestPool.pop();
            if (newDeltaRequest == null) {
                newDeltaRequest = createRequest(null, ((ClusterManagerBase) manager).isRecordAllActions());
            }
        } else {
            newDeltaRequest = createRequest();
        }

        DeltaRequest oldDeltaRequest = replaceDeltaRequest(newDeltaRequest);

        byte[] result = oldDeltaRequest.serialize();

        if (deltaRequestPool != null) {
            // Only need to reset the old request if it is going to be pooled.
            // Otherwise let GC do its thing.
            oldDeltaRequest.reset();
            deltaRequestPool.push(oldDeltaRequest);
        }

        return result;
    }

    public ClassLoader[] getClassLoaders() {
        if (manager instanceof ClusterManagerBase) {
            return ((ClusterManagerBase) manager).getClassLoaders();
        } else if (manager instanceof ManagerBase) {
            ManagerBase mb = (ManagerBase) manager;
            return ClusterManagerBase.getClassLoaders(mb.getContext());
        }
        return null;
    }

    @Override
    public void applyDiff(byte[] diff, int offset, int length) throws IOException, ClassNotFoundException {
        Thread currentThread = Thread.currentThread();
        ClassLoader contextLoader = currentThread.getContextClassLoader();
        lockInternal();
        try (ObjectInputStream stream = ((ClusterManager) getManager()).getReplicationStream(diff, offset, length)) {
            ClassLoader[] loaders = getClassLoaders();
            if (loaders != null && loaders.length > 0) {
                currentThread.setContextClassLoader(loaders[0]);
            }
            deltaRequest.readExternal(stream);
            deltaRequest.execute(this, ((ClusterManager) getManager()).isNotifyListenersOnReplication());
        } finally {
            unlockInternal();
            currentThread.setContextClassLoader(contextLoader);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is a NO-OP. The diff is reset in {@link #getDiff()}.
     */
    @Override
    public void resetDiff() {
        resetDeltaRequest();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is a NO-OP. Any required locking takes place in the methods that make modifications.
     */
    @Override
    public void lock() {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is a NO-OP. Any required unlocking takes place in the methods that make modifications.
     */
    @Override
    public void unlock() {
        // NO-OP
    }

    /**
     * Lock during serialization.
     */
    private void lockInternal() {
        diffLock.lock();
    }

    /**
     * Unlock after serialization.
     */
    private void unlockInternal() {
        diffLock.unlock();
    }

    @Override
    public void setOwner(Object owner) {
        if (owner instanceof ClusterManager && getManager() == null) {
            ClusterManager cm = (ClusterManager) owner;
            this.setManager(cm);
            this.setValid(true);
            this.setPrimarySession(false);
            this.access();
            this.resetDeltaRequest();
            this.endAccess();
        }
    }

    @Override
    public boolean isAccessReplicate() {
        long replDelta = System.currentTimeMillis() - getLastTimeReplicated();
        if (maxInactiveInterval >= 0 && replDelta > (maxInactiveInterval * 1000L)) {
            return true;
        }
        return false;
    }

    @Override
    public void accessEntry() {
        this.access();
        this.setPrimarySession(false);
        this.endAccess();
    }

    // ----------------------------------------------------- Session Properties

    @Override
    public boolean isPrimarySession() {
        return isPrimarySession;
    }

    @Override
    public void setPrimarySession(boolean primarySession) {
        this.isPrimarySession = primarySession;
    }


    @Override
    public void setId(String id, boolean notify) {
        super.setId(id, notify);
        lockInternal();
        try {
            deltaRequest.setSessionId(getIdInternal());
        } finally {
            unlockInternal();
        }
    }


    @Override
    public void setId(String id) {
        setId(id, true);
    }


    @Override
    public void setMaxInactiveInterval(int interval) {
        this.setMaxInactiveInterval(interval, true);
    }


    public void setMaxInactiveInterval(int interval, boolean addDeltaRequest) {
        super.maxInactiveInterval = interval;
        if (addDeltaRequest) {
            lockInternal();
            try {
                deltaRequest.setMaxInactiveInterval(interval);
            } finally {
                unlockInternal();
            }
        }
    }

    @Override
    public void setNew(boolean isNew) {
        setNew(isNew, true);
    }

    public void setNew(boolean isNew, boolean addDeltaRequest) {
        super.setNew(isNew);
        if (addDeltaRequest) {
            lockInternal();
            try {
                deltaRequest.setNew(isNew);
            } finally {
                unlockInternal();
            }
        }
    }

    @Override
    public void setPrincipal(Principal principal) {
        setPrincipal(principal, true);
    }

    public void setPrincipal(Principal principal, boolean addDeltaRequest) {
        lockInternal();
        try {
            super.setPrincipal(principal);
            if (addDeltaRequest) {
                deltaRequest.setPrincipal(principal);
            }
        } finally {
            unlockInternal();
        }
    }

    @Override
    public void setAuthType(String authType) {
        setAuthType(authType, true);
    }

    public void setAuthType(String authType, boolean addDeltaRequest) {
        lockInternal();
        try {
            super.setAuthType(authType);
            if (addDeltaRequest) {
                deltaRequest.setAuthType(authType);
            }
        } finally {
            unlockInternal();
        }
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
            if (isPrimarySession()) {
                if (timeIdle >= maxInactiveInterval) {
                    expire(true);
                }
            } else {
                if (timeIdle >= (2 * maxInactiveInterval)) {
                    // if the session has been idle twice as long as allowed,
                    // the primary session has probably crashed, and no other
                    // requests are coming in. that is why we do this. otherwise
                    // we would have a memory leak
                    expire(true, false);
                }
            }
        }

        return this.isValid;
    }

    @Override
    public void endAccess() {
        super.endAccess();
        if (manager instanceof ClusterManagerBase) {
            ((ClusterManagerBase) manager).registerSessionAtReplicationValve(this);
        }
    }

    // ------------------------------------------------- Session Public Methods

    @Override
    public void expire(boolean notify) {
        expire(notify, true);
    }

    public void expire(boolean notify, boolean notifyCluster) {

        // Check to see if session has already been invalidated.
        // Do not check expiring at this point as expire should not return until
        // isValid is false
        if (!isValid) {
            return;
        }

        synchronized (this) {
            // Check again, now we are inside the sync so this code only runs once
            // Double check locking - isValid needs to be volatile
            if (!isValid) {
                return;
            }

            if (manager == null) {
                return;
            }

            String expiredId = getIdInternal();

            if (notifyCluster && expiredId != null && manager instanceof DeltaManager) {
                DeltaManager dmanager = (DeltaManager) manager;
                CatalinaCluster cluster = dmanager.getCluster();
                ClusterMessage msg = dmanager.requestCompleted(expiredId, true);
                if (msg != null) {
                    cluster.send(msg);
                }
            }

            super.expire(notify);

            if (notifyCluster) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("deltaSession.notifying", ((ClusterManager) manager).getName(),
                            Boolean.valueOf(isPrimarySession()), expiredId));
                }
                if (manager instanceof DeltaManager) {
                    ((DeltaManager) manager).sessionExpired(expiredId);
                }
            }
        }
    }

    @Override
    public void recycle() {
        lockInternal();
        try {
            super.recycle();
            deltaRequest.clear();
        } finally {
            unlockInternal();
        }
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DeltaSession[");
        sb.append(id);
        sb.append(']');
        return sb.toString();
    }

    @Override
    public void addSessionListener(SessionListener listener) {
        addSessionListener(listener, true);
    }

    public void addSessionListener(SessionListener listener, boolean addDeltaRequest) {
        lockInternal();
        try {
            super.addSessionListener(listener);
            if (addDeltaRequest && listener instanceof ReplicatedSessionListener) {
                deltaRequest.addSessionListener(listener);
            }
        } finally {
            unlockInternal();
        }
    }

    @Override
    public void removeSessionListener(SessionListener listener) {
        removeSessionListener(listener, true);
    }

    public void removeSessionListener(SessionListener listener, boolean addDeltaRequest) {
        lockInternal();
        try {
            super.removeSessionListener(listener);
            if (addDeltaRequest && listener instanceof ReplicatedSessionListener) {
                deltaRequest.removeSessionListener(listener);
            }
        } finally {
            unlockInternal();
        }
    }


    // ------------------------------------------------ Session Package Methods

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        lockInternal();
        try {
            readObjectData(in);
        } finally {
            unlockInternal();
        }
    }


    @Override
    public void readObjectData(ObjectInputStream stream) throws ClassNotFoundException, IOException {
        doReadObject((ObjectInput) stream);
    }

    public void readObjectData(ObjectInput stream) throws ClassNotFoundException, IOException {
        doReadObject(stream);
    }

    @Override
    public void writeObjectData(ObjectOutputStream stream) throws IOException {
        writeObjectData((ObjectOutput) stream);
    }

    public void writeObjectData(ObjectOutput stream) throws IOException {
        doWriteObject(stream);
    }

    public void resetDeltaRequest() {
        lockInternal();
        try {
            deltaRequest.reset();
            deltaRequest.setSessionId(getIdInternal());
        } finally {
            unlockInternal();
        }
    }

    /**
     * Get the request.
     *
     * @return the request
     *
     * @deprecated Unused. This method will be removed in Tomcat 12.
     */
    @Deprecated
    public DeltaRequest getDeltaRequest() {
        return deltaRequest;
    }

    /**
     * Replace the existing deltaRequest with the provided replacement.
     *
     * @param deltaRequest The new deltaRequest. Expected to be either a newly created object or an instance that has
     *                         been reset.
     *
     * @return The old deltaRequest
     */
    DeltaRequest replaceDeltaRequest(DeltaRequest deltaRequest) {
        lockInternal();
        try {
            DeltaRequest oldDeltaRequest = this.deltaRequest;
            this.deltaRequest = deltaRequest;
            this.deltaRequest.setSessionId(getIdInternal());
            return oldDeltaRequest;
        } finally {
            unlockInternal();
        }
    }


    protected void deserializeAndExecuteDeltaRequest(byte[] delta) throws IOException, ClassNotFoundException {
        if (manager instanceof ClusterManagerBase) {
            SynchronizedStack<DeltaRequest> deltaRequestPool = ((ClusterManagerBase) manager).getDeltaRequestPool();

            DeltaRequest newDeltaRequest = deltaRequestPool.pop();
            if (newDeltaRequest == null) {
                newDeltaRequest = createRequest(null, ((ClusterManagerBase) manager).isRecordAllActions());
            }

            ReplicationStream ois = ((ClusterManagerBase) manager).getReplicationStream(delta);
            newDeltaRequest.readExternal(ois);
            ois.close();

            DeltaRequest oldDeltaRequest = null;
            lockInternal();
            try {
                oldDeltaRequest = replaceDeltaRequest(newDeltaRequest);
                newDeltaRequest.execute(this, ((ClusterManagerBase) manager).isNotifyListenersOnReplication());
                setPrimarySession(false);
            } finally {
                unlockInternal();
                if (oldDeltaRequest != null) {
                    oldDeltaRequest.reset();
                    deltaRequestPool.push(oldDeltaRequest);
                }
            }
        }
    }
    // ------------------------------------------------- HttpSession Properties

    // ----------------------------------------------HttpSession Public Methods

    @Override
    public void removeAttribute(String name, boolean notify) {
        removeAttribute(name, notify, true);
    }

    public void removeAttribute(String name, boolean notify, boolean addDeltaRequest) {
        // Validate our current state
        if (!isValid()) {
            throw new IllegalStateException(sm.getString("standardSession.removeAttribute.ise"));
        }
        removeAttributeInternal(name, notify, addDeltaRequest);
    }

    @Override
    public void setAttribute(String name, Object value) {
        setAttribute(name, value, true, true);
    }

    public void setAttribute(String name, Object value, boolean notify, boolean addDeltaRequest) {

        // Name cannot be null
        if (name == null) {
            throw new IllegalArgumentException(sm.getString("standardSession.setAttribute.namenull"));
        }

        // Null value is the same as removeAttribute()
        if (value == null) {
            removeAttribute(name);
            return;
        }

        lockInternal();
        try {
            super.setAttribute(name, value, notify);
            if (addDeltaRequest && !exclude(name, value)) {
                deltaRequest.setAttribute(name, value);
            }
        } finally {
            unlockInternal();
        }
    }


    @Override
    public void removeNote(String name) {
        removeNote(name, true);
    }

    public void removeNote(String name, boolean addDeltaRequest) {
        lockInternal();
        try {
            super.removeNote(name);
            if (addDeltaRequest) {
                deltaRequest.removeNote(name);
            }
        } finally {
            unlockInternal();
        }
    }


    @Override
    public void setNote(String name, Object value) {
        setNote(name, value, true);
    }

    public void setNote(String name, Object value, boolean addDeltaRequest) {

        if (value == null) {
            removeNote(name, addDeltaRequest);
            return;
        }

        lockInternal();
        try {
            super.setNote(name, value);
            if (addDeltaRequest) {
                deltaRequest.setNote(name, value);
            }
        } finally {
            unlockInternal();
        }
    }


    // -------------------------------------------- HttpSession Private Methods

    @Override
    protected void doReadObject(ObjectInputStream stream) throws ClassNotFoundException, IOException {
        doReadObject((ObjectInput) stream);
    }

    private void doReadObject(ObjectInput stream) throws ClassNotFoundException, IOException {

        // Deserialize the scalar instance variables (except Manager)
        authType = null; // Transient only
        creationTime = ((Long) stream.readObject()).longValue();
        lastAccessedTime = ((Long) stream.readObject()).longValue();
        maxInactiveInterval = ((Integer) stream.readObject()).intValue();
        isNew = ((Boolean) stream.readObject()).booleanValue();
        isValid = ((Boolean) stream.readObject()).booleanValue();
        thisAccessedTime = ((Long) stream.readObject()).longValue();
        version = ((Long) stream.readObject()).longValue();
        boolean hasPrincipal = stream.readBoolean();
        principal = null;
        if (hasPrincipal) {
            principal = (Principal) stream.readObject();
        }

        id = (String) stream.readObject();
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("deltaSession.readSession", id));
        }

        Object nextObject = stream.readObject();

        // Compatibility with versions that do not persist the authentication
        // notes
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
                    // Skip non serializable attributes
                    continue;
                }
                throw wae;
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

        // Session listeners
        n = ((Integer) stream.readObject()).intValue();
        if (listeners == null || n > 0) {
            listeners = new ArrayList<>();
        }
        for (int i = 0; i < n; i++) {
            SessionListener listener = (SessionListener) stream.readObject();
            listeners.add(listener);
        }

        if (notes == null) {
            notes = new ConcurrentHashMap<>();
        }
        activate();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        lockInternal();
        try {
            doWriteObject(out);
        } finally {
            unlockInternal();
        }
    }


    @Override
    protected void doWriteObject(ObjectOutputStream stream) throws IOException {
        doWriteObject((ObjectOutput) stream);
    }

    private void doWriteObject(ObjectOutput stream) throws IOException {
        // Write the scalar instance variables (except Manager)
        stream.writeObject(Long.valueOf(creationTime));
        stream.writeObject(Long.valueOf(lastAccessedTime));
        stream.writeObject(Integer.valueOf(maxInactiveInterval));
        stream.writeObject(Boolean.valueOf(isNew));
        stream.writeObject(Boolean.valueOf(isValid));
        stream.writeObject(Long.valueOf(thisAccessedTime));
        stream.writeObject(Long.valueOf(version));
        stream.writeBoolean(getPrincipal() instanceof Serializable);
        if (getPrincipal() instanceof Serializable) {
            stream.writeObject(getPrincipal());
        }

        stream.writeObject(id);
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("deltaSession.writeSession", id));
        }

        // Write the notes associated with authentication. Without these,
        // authentication can fail without sticky sessions or if there is a
        // fail-over during authentication.
        stream.writeObject(notes.get(org.apache.catalina.authenticator.Constants.SESSION_ID_NOTE));
        stream.writeObject(notes.get(org.apache.catalina.authenticator.Constants.FORM_REQUEST_NOTE));

        // Accumulate the names of serializable and non-serializable attributes
        String keys[] = keys();
        List<String> saveNames = new ArrayList<>();
        List<Object> saveValues = new ArrayList<>();
        for (String key : keys) {
            Object value = null;
            value = attributes.get(key);
            if (value != null && !exclude(key, value) && isAttributeDistributable(key, value)) {
                saveNames.add(key);
                saveValues.add(value);
            }
        }

        // Serialize the attribute count and the Serializable attributes
        int n = saveNames.size();
        stream.writeObject(Integer.valueOf(n));
        for (int i = 0; i < n; i++) {
            stream.writeObject(saveNames.get(i));
            try {
                stream.writeObject(saveValues.get(i));
            } catch (NotSerializableException e) {
                log.error(sm.getString("standardSession.notSerializable", saveNames.get(i), id), e);
            }
        }

        // Serializable listeners
        ArrayList<SessionListener> saveListeners = new ArrayList<>();
        for (SessionListener listener : listeners) {
            if (listener instanceof ReplicatedSessionListener) {
                saveListeners.add(listener);
            }
        }
        stream.writeObject(Integer.valueOf(saveListeners.size()));
        for (SessionListener listener : saveListeners) {
            stream.writeObject(listener);
        }
    }


    // -------------------------------------------------------- Private Methods

    protected void removeAttributeInternal(String name, boolean notify, boolean addDeltaRequest) {
        lockInternal();
        try {
            // Remove this attribute from our collection
            Object value = attributes.get(name);
            if (value == null) {
                return;
            }

            super.removeAttributeInternal(name, notify);
            if (addDeltaRequest && !exclude(name, null)) {
                deltaRequest.removeAttribute(name);
            }

        } finally {
            unlockInternal();
        }
    }

    @Override
    public long getLastTimeReplicated() {
        return lastTimeReplicated;
    }

    @Override
    public long getVersion() {
        return version;
    }

    @Override
    public void setLastTimeReplicated(long lastTimeReplicated) {
        this.lastTimeReplicated = lastTimeReplicated;
    }

    @Override
    public void setVersion(long version) {
        this.version = version;
    }

    protected void setAccessCount(int count) {
        if (accessCount == null && activityCheck) {
            accessCount = new AtomicInteger();
        }
        if (accessCount != null) {
            accessCount.set(count);
        }
    }
}
