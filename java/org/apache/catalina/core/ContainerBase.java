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
package org.apache.catalina.core;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.management.ObjectName;

import org.apache.catalina.AccessLog;
import org.apache.catalina.Cluster;
import org.apache.catalina.Container;
import org.apache.catalina.ContainerEvent;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Loader;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Realm;
import org.apache.catalina.Server;
import org.apache.catalina.Valve;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.ContextName;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.MultiThrowable;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.threads.InlineExecutorService;


/**
 * Abstract implementation of the <b>Container</b> interface, providing common functionality required by nearly every
 * implementation. Classes extending this base class must may implement a replacement for <code>invoke()</code>.
 * <p>
 * All subclasses of this abstract base class will include support for a Pipeline object that defines the processing to
 * be performed for each request received by the <code>invoke()</code> method of this class, utilizing the "Chain of
 * Responsibility" design pattern. A subclass should encapsulate its own processing functionality as a
 * <code>Valve</code>, and configure this Valve into the pipeline by calling <code>setBasic()</code>.
 * <p>
 * This implementation fires property change events, per the JavaBeans design pattern, for changes in singleton
 * properties. In addition, it fires the following <code>ContainerEvent</code> events to listeners who register
 * themselves with <code>addContainerListener()</code>:
 * <table border=1>
 * <caption>ContainerEvents fired by this implementation</caption>
 * <tr>
 * <th>Type</th>
 * <th>Data</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td><code>addChild</code></td>
 * <td><code>Container</code></td>
 * <td>Child container added to this Container.</td>
 * </tr>
 * <tr>
 * <td><code>{@link #getPipeline() pipeline}.addValve</code></td>
 * <td><code>Valve</code></td>
 * <td>Valve added to this Container.</td>
 * </tr>
 * <tr>
 * <td><code>removeChild</code></td>
 * <td><code>Container</code></td>
 * <td>Child container removed from this Container.</td>
 * </tr>
 * <tr>
 * <td><code>{@link #getPipeline() pipeline}.removeValve</code></td>
 * <td><code>Valve</code></td>
 * <td>Valve removed from this Container.</td>
 * </tr>
 * <tr>
 * <td><code>start</code></td>
 * <td><code>null</code></td>
 * <td>Container was started.</td>
 * </tr>
 * <tr>
 * <td><code>stop</code></td>
 * <td><code>null</code></td>
 * <td>Container was stopped.</td>
 * </tr>
 * </table>
 * Subclasses that fire additional events should document them in the class comments of the implementation class.
 *
 * @author Craig R. McClanahan
 */
public abstract class ContainerBase extends LifecycleMBeanBase implements Container {

    private static final Log log = LogFactory.getLog(ContainerBase.class);


    // ----------------------------------------------------- Instance Variables

    /**
     * The child Containers belonging to this Container, keyed by name.
     */
    protected final HashMap<String,Container> children = new HashMap<>();
    private final ReadWriteLock childrenLock = new ReentrantReadWriteLock();


    /**
     * The processor delay for this component.
     */
    protected int backgroundProcessorDelay = -1;


    /**
     * The future allowing control of the background processor.
     */
    protected ScheduledFuture<?> backgroundProcessorFuture;
    protected ScheduledFuture<?> monitorFuture;

    /**
     * The container event listeners for this Container. Implemented as a CopyOnWriteArrayList since listeners may
     * invoke methods to add/remove themselves or other listeners and with a ReadWriteLock that would trigger a
     * deadlock.
     */
    protected final List<ContainerListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * The Logger implementation with which this Container is associated.
     */
    protected Log logger = null;


    /**
     * Associated logger name.
     */
    protected String logName = null;


    /**
     * The cluster with which this Container is associated.
     */
    protected Cluster cluster = null;
    private final ReadWriteLock clusterLock = new ReentrantReadWriteLock();


    /**
     * The human-readable name of this Container.
     */
    protected String name = null;


    /**
     * The parent Container to which this Container is a child.
     */
    protected Container parent = null;


    /**
     * The parent class loader to be configured when we install a Loader.
     */
    protected ClassLoader parentClassLoader = null;


    /**
     * The Pipeline object with which this Container is associated.
     */
    protected final Pipeline pipeline = new StandardPipeline(this);


    /**
     * The Realm with which this Container is associated.
     */
    private volatile Realm realm = null;


    /**
     * Lock used to control access to the Realm.
     */
    private final ReadWriteLock realmLock = new ReentrantReadWriteLock();


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(ContainerBase.class);


    /**
     * Will children be started automatically when they are added.
     */
    protected boolean startChildren = true;

    /**
     * The property change support for this component.
     */
    protected final PropertyChangeSupport support = new PropertyChangeSupport(this);


    /**
     * The access log to use for requests normally handled by this container that have been handled earlier in the
     * processing chain.
     */
    protected volatile AccessLog accessLog = null;
    private volatile boolean accessLogScanComplete = false;


    /**
     * The number of threads available to process start and stop events for any children associated with this container.
     */
    private int startStopThreads = 1;
    protected ExecutorService startStopExecutor;


    // ------------------------------------------------------------- Properties

    @Override
    public int getStartStopThreads() {
        return startStopThreads;
    }

    @Override
    public void setStartStopThreads(int startStopThreads) {
        int oldStartStopThreads = this.startStopThreads;
        this.startStopThreads = startStopThreads;

        // Use local copies to ensure thread safety
        if (oldStartStopThreads != startStopThreads && startStopExecutor != null) {
            reconfigureStartStopExecutor(getStartStopThreads());
        }
    }


    @Override
    public int getBackgroundProcessorDelay() {
        return backgroundProcessorDelay;
    }


    @Override
    public void setBackgroundProcessorDelay(int delay) {
        backgroundProcessorDelay = delay;
    }


    @Override
    public Log getLogger() {
        if (logger != null) {
            return logger;
        }
        logger = LogFactory.getLog(getLogName());
        return logger;
    }


    @Override
    public String getLogName() {

        if (logName != null) {
            return logName;
        }
        String loggerName = null;
        Container current = this;
        while (current != null) {
            String name = current.getName();
            if ((name == null) || (name.equals(""))) {
                name = "/";
            } else if (name.startsWith("##")) {
                name = "/" + name;
            }
            loggerName = "[" + name + "]" + ((loggerName != null) ? ("." + loggerName) : "");
            current = current.getParent();
        }
        logName = ContainerBase.class.getName() + "." + loggerName;
        return logName;

    }


    @Override
    public Cluster getCluster() {
        Lock readLock = clusterLock.readLock();
        readLock.lock();
        try {
            if (cluster != null) {
                return cluster;
            }

            if (parent != null) {
                return parent.getCluster();
            }

            return null;
        } finally {
            readLock.unlock();
        }
    }


    /*
     * Provide access to just the cluster component attached to this container.
     */
    protected Cluster getClusterInternal() {
        Lock readLock = clusterLock.readLock();
        readLock.lock();
        try {
            return cluster;
        } finally {
            readLock.unlock();
        }
    }


    @Override
    public void setCluster(Cluster cluster) {

        Cluster oldCluster = null;
        Lock writeLock = clusterLock.writeLock();
        writeLock.lock();
        try {
            // Change components if necessary
            oldCluster = this.cluster;
            if (oldCluster == cluster) {
                return;
            }
            this.cluster = cluster;
            // Start the new component if necessary
            if (cluster != null) {
                cluster.setContainer(this);
            }
        } finally {
            writeLock.unlock();
        }

        // Stop the old component if necessary
        if (getState().isAvailable() && (oldCluster instanceof Lifecycle)) {
            try {
                ((Lifecycle) oldCluster).stop();
            } catch (LifecycleException e) {
                log.error(sm.getString("containerBase.cluster.stop"), e);
            }
        }

        if (getState().isAvailable() && (cluster instanceof Lifecycle)) {
            try {
                ((Lifecycle) cluster).start();
            } catch (LifecycleException e) {
                log.error(sm.getString("containerBase.cluster.start"), e);
            }
        }

        // Report this property change to interested listeners
        support.firePropertyChange("cluster", oldCluster, cluster);
    }


    @Override
    public String getName() {
        return name;
    }


    @Override
    public void setName(String name) {
        if (name == null) {
            throw new IllegalArgumentException(sm.getString("containerBase.nullName"));
        }
        String oldName = this.name;
        this.name = name;
        support.firePropertyChange("name", oldName, this.name);
    }


    /**
     * Return if children of this container will be started automatically when they are added to this container.
     *
     * @return <code>true</code> if the children will be started
     */
    public boolean getStartChildren() {
        return startChildren;
    }


    /**
     * Set if children of this container will be started automatically when they are added to this container.
     *
     * @param startChildren New value of the startChildren flag
     */
    public void setStartChildren(boolean startChildren) {

        boolean oldStartChildren = this.startChildren;
        this.startChildren = startChildren;
        support.firePropertyChange("startChildren", oldStartChildren, this.startChildren);
    }


    @Override
    public Container getParent() {
        return parent;
    }


    @Override
    public void setParent(Container container) {

        Container oldParent = this.parent;
        this.parent = container;
        support.firePropertyChange("parent", oldParent, this.parent);

    }


    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null) {
            return parentClassLoader;
        }
        if (parent != null) {
            return parent.getParentClassLoader();
        }
        return ClassLoader.getSystemClassLoader();
    }


    @Override
    public void setParentClassLoader(ClassLoader parent) {
        ClassLoader oldParentClassLoader = this.parentClassLoader;
        this.parentClassLoader = parent;
        support.firePropertyChange("parentClassLoader", oldParentClassLoader, this.parentClassLoader);

    }


    @Override
    public Pipeline getPipeline() {
        return this.pipeline;
    }


    @Override
    public Realm getRealm() {

        Lock l = realmLock.readLock();
        l.lock();
        try {
            if (realm != null) {
                return realm;
            }
            if (parent != null) {
                return parent.getRealm();
            }
            return null;
        } finally {
            l.unlock();
        }
    }


    protected Realm getRealmInternal() {
        Lock l = realmLock.readLock();
        l.lock();
        try {
            return realm;
        } finally {
            l.unlock();
        }
    }

    @Override
    public void setRealm(Realm realm) {

        Realm oldRealm = null;
        Lock l = realmLock.writeLock();
        l.lock();
        try {
            // Change components if necessary
            oldRealm = this.realm;
            if (oldRealm == realm) {
                return;
            }
            this.realm = realm;

            // Start the new component if necessary
            if (realm != null) {
                realm.setContainer(this);
            }
        } finally {
            l.unlock();
        }

        // Stop the old component if necessary
        if (getState().isAvailable() && oldRealm instanceof Lifecycle) {
            try {
                ((Lifecycle) oldRealm).stop();
            } catch (LifecycleException e) {
                log.error(sm.getString("containerBase.realm.stop"), e);
            }
        }

        if (getState().isAvailable() && realm instanceof Lifecycle) {
            try {
                ((Lifecycle) realm).start();
            } catch (LifecycleException e) {
                log.error(sm.getString("containerBase.realm.start"), e);
            }
        }

        // Report this property change to interested listeners
        support.firePropertyChange("realm", oldRealm, this.realm);
    }


    // ------------------------------------------------------ Container Methods


    @Override
    public void addChild(Container child) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("containerBase.child.add", child, this));
        }

        childrenLock.writeLock().lock();
        try {
            if (children.get(child.getName()) != null) {
                throw new IllegalArgumentException(sm.getString("containerBase.child.notUnique", child.getName()));
            }
            child.setParent(this); // May throw IAE
            children.put(child.getName(), child);
        } finally {
            childrenLock.writeLock().unlock();
        }

        fireContainerEvent(ADD_CHILD_EVENT, child);

        // Start child
        // Don't do this inside sync block - start can be a slow process and
        // locking the children object can cause problems elsewhere
        try {
            if ((getState().isAvailable() || LifecycleState.STARTING_PREP.equals(getState())) && startChildren) {
                child.start();
            }
        } catch (LifecycleException e) {
            throw new IllegalStateException(sm.getString("containerBase.child.start"), e);
        }
    }


    @Override
    public void addContainerListener(ContainerListener listener) {
        listeners.add(listener);
    }


    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }


    @Override
    public Container findChild(String name) {
        if (name == null) {
            return null;
        }
        childrenLock.readLock().lock();
        try {
            return children.get(name);
        } finally {
            childrenLock.readLock().unlock();
        }
    }


    @Override
    public Container[] findChildren() {
        childrenLock.readLock().lock();
        try {
            return children.values().toArray(new Container[0]);
        } finally {
            childrenLock.readLock().unlock();
        }
    }


    @Override
    public ContainerListener[] findContainerListeners() {
        return listeners.toArray(new ContainerListener[0]);
    }


    @Override
    public void removeChild(Container child) {

        if (child == null) {
            return;
        }

        try {
            if (child.getState().isAvailable()) {
                child.stop();
            }
        } catch (LifecycleException e) {
            log.error(sm.getString("containerBase.child.stop"), e);
        }

        boolean destroy = false;
        try {
            // child.destroy() may have already been called which would have
            // triggered this call. If that is the case, no need to destroy the
            // child again.
            if (!LifecycleState.DESTROYING.equals(child.getState())) {
                child.destroy();
                destroy = true;
            }
        } catch (LifecycleException e) {
            log.error(sm.getString("containerBase.child.destroy"), e);
        }

        if (!destroy) {
            fireContainerEvent(REMOVE_CHILD_EVENT, child);
        }

        childrenLock.writeLock().lock();
        try {
            children.remove(child.getName());
        } finally {
            childrenLock.writeLock().unlock();
        }

    }


    @Override
    public void removeContainerListener(ContainerListener listener) {
        listeners.remove(listener);
    }


    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {

        support.removePropertyChangeListener(listener);

    }


    private void reconfigureStartStopExecutor(int threads) {
        if (threads == 1) {
            // Use a fake executor
            if (!(startStopExecutor instanceof InlineExecutorService)) {
                startStopExecutor = new InlineExecutorService();
            }
        } else {
            // Delegate utility execution to the Service
            Server server = Container.getService(this).getServer();
            server.setUtilityThreads(threads);
            startStopExecutor = server.getUtilityExecutor();
        }
    }


    /**
     * Start this component and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error that prevents this component from being
     *                                   used
     */
    @Override
    protected void startInternal() throws LifecycleException {

        reconfigureStartStopExecutor(getStartStopThreads());

        // Start our subordinate components, if any
        logger = null;
        getLogger();
        Cluster cluster = getClusterInternal();
        if (cluster instanceof Lifecycle) {
            ((Lifecycle) cluster).start();
        }
        Realm realm = getRealmInternal();
        if (realm instanceof Lifecycle) {
            ((Lifecycle) realm).start();
        }

        // Start our child containers, if any
        Container[] children = findChildren();
        List<Future<Void>> results = new ArrayList<>(children.length);
        for (Container child : children) {
            results.add(startStopExecutor.submit(new StartChild(child)));
        }

        MultiThrowable multiThrowable = null;

        for (Future<Void> result : results) {
            try {
                result.get();
            } catch (Throwable e) {
                log.error(sm.getString("containerBase.threadedStartFailed"), e);
                if (multiThrowable == null) {
                    multiThrowable = new MultiThrowable();
                }
                multiThrowable.add(e);
            }

        }
        if (multiThrowable != null) {
            throw new LifecycleException(sm.getString("containerBase.threadedStartFailed"),
                    multiThrowable.getThrowable());
        }

        // Start the Valves in our pipeline (including the basic), if any
        if (pipeline instanceof Lifecycle) {
            ((Lifecycle) pipeline).start();
        }

        setState(LifecycleState.STARTING);

        // Start our thread
        if (backgroundProcessorDelay > 0) {
            monitorFuture = Container.getService(ContainerBase.this).getServer().getUtilityExecutor()
                    .scheduleWithFixedDelay(new ContainerBackgroundProcessorMonitor(), 0, 60, TimeUnit.SECONDS);
        }
    }


    /**
     * Stop this component and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error that prevents this component from being
     *                                   used
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        // Stop our thread
        if (monitorFuture != null) {
            monitorFuture.cancel(true);
            monitorFuture = null;
        }
        threadStop();

        setState(LifecycleState.STOPPING);

        // Stop the Valves in our pipeline (including the basic), if any
        if (pipeline instanceof Lifecycle && ((Lifecycle) pipeline).getState().isAvailable()) {
            ((Lifecycle) pipeline).stop();
        }

        // Stop our child containers, if any
        Container[] children = findChildren();
        List<Future<Void>> results = new ArrayList<>(children.length);
        for (Container child : children) {
            results.add(startStopExecutor.submit(new StopChild(child)));
        }

        boolean fail = false;
        for (Future<Void> result : results) {
            try {
                result.get();
            } catch (Exception e) {
                log.error(sm.getString("containerBase.threadedStopFailed"), e);
                fail = true;
            }
        }
        if (fail) {
            throw new LifecycleException(sm.getString("containerBase.threadedStopFailed"));
        }

        // Stop our subordinate components, if any
        Realm realm = getRealmInternal();
        if (realm instanceof Lifecycle) {
            ((Lifecycle) realm).stop();
        }
        Cluster cluster = getClusterInternal();
        if (cluster instanceof Lifecycle) {
            ((Lifecycle) cluster).stop();
        }

        // If init fails, this may be null
        if (startStopExecutor != null) {
            startStopExecutor.shutdownNow();
            startStopExecutor = null;
        }
    }

    @Override
    protected void destroyInternal() throws LifecycleException {

        Realm realm = getRealmInternal();
        if (realm instanceof Lifecycle) {
            ((Lifecycle) realm).destroy();
        }
        Cluster cluster = getClusterInternal();
        if (cluster instanceof Lifecycle) {
            ((Lifecycle) cluster).destroy();
        }

        // Stop the Valves in our pipeline (including the basic), if any
        if (pipeline instanceof Lifecycle) {
            ((Lifecycle) pipeline).destroy();
        }

        // Remove children now this container is being destroyed
        for (Container child : findChildren()) {
            removeChild(child);
        }

        // Required if the child is destroyed directly.
        if (parent != null) {
            parent.removeChild(this);
        }

        super.destroyInternal();
    }


    @Override
    public void logAccess(Request request, Response response, long time, boolean useDefault) {

        boolean logged = false;

        if (getAccessLog() != null) {
            getAccessLog().log(request, response, time);
            logged = true;
        }

        if (getParent() != null) {
            // No need to use default logger once request/response has been logged
            // once
            getParent().logAccess(request, response, time, (useDefault && !logged));
        }
    }

    @Override
    public AccessLog getAccessLog() {

        if (accessLogScanComplete) {
            return accessLog;
        }

        AccessLogAdapter adapter = null;
        Valve[] valves = getPipeline().getValves();
        for (Valve valve : valves) {
            if (valve instanceof AccessLog) {
                if (adapter == null) {
                    adapter = new AccessLogAdapter((AccessLog) valve);
                } else {
                    adapter.add((AccessLog) valve);
                }
            }
        }
        if (adapter != null) {
            accessLog = adapter;
        }
        accessLogScanComplete = true;
        return accessLog;
    }

    // ------------------------------------------------------- Pipeline Methods


    /**
     * Convenience method, intended for use by the digester to simplify the process of adding Valves to containers. See
     * {@link Pipeline#addValve(Valve)} for full details. Components other than the digester should use
     * {@link #getPipeline()}.{@link #addValve(Valve)} in case a future implementation provides an alternative method
     * for the digester to use.
     *
     * @param valve Valve to be added
     *
     * @exception IllegalArgumentException if this Container refused to accept the specified Valve
     * @exception IllegalArgumentException if the specified Valve refuses to be associated with this Container
     * @exception IllegalStateException    if the specified Valve is already associated with a different Container
     */
    public synchronized void addValve(Valve valve) {

        pipeline.addValve(valve);
    }


    @Override
    public synchronized void backgroundProcess() {

        if (!getState().isAvailable()) {
            return;
        }

        Cluster cluster = getClusterInternal();
        if (cluster != null) {
            try {
                cluster.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString("containerBase.backgroundProcess.cluster", cluster), e);
            }
        }
        Realm realm = getRealmInternal();
        if (realm != null) {
            try {
                realm.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString("containerBase.backgroundProcess.realm", realm), e);
            }
        }
        Valve current = pipeline.getFirst();
        while (current != null) {
            try {
                current.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString("containerBase.backgroundProcess.valve", current), e);
            }
            current = current.getNext();
        }
        fireLifecycleEvent(PERIODIC_EVENT, null);
    }


    @Override
    public File getCatalinaBase() {

        if (parent == null) {
            return null;
        }

        return parent.getCatalinaBase();
    }


    @Override
    public File getCatalinaHome() {

        if (parent == null) {
            return null;
        }

        return parent.getCatalinaHome();
    }


    // ------------------------------------------------------ Protected Methods

    @Override
    public void fireContainerEvent(String type, Object data) {

        if (listeners.size() < 1) {
            return;
        }

        ContainerEvent event = new ContainerEvent(this, type, data);
        // Note for each uses an iterator internally so this is safe
        for (ContainerListener listener : listeners) {
            listener.containerEvent(event);
        }
    }


    // -------------------- JMX and Registration --------------------

    @Override
    protected String getDomainInternal() {

        Container p = this.getParent();
        if (p == null) {
            return null;
        } else {
            return p.getDomain();
        }
    }


    @Override
    public String getMBeanKeyProperties() {
        Container c = this;
        StringBuilder keyProperties = new StringBuilder();
        int containerCount = 0;

        // Work up container hierarchy, add a component to the name for
        // each container
        while (!(c instanceof Engine)) {
            if (c instanceof Wrapper) {
                keyProperties.insert(0, ",servlet=");
                keyProperties.insert(9, c.getName());
            } else if (c instanceof Context) {
                keyProperties.insert(0, ",context=");
                ContextName cn = new ContextName(c.getName(), false);
                keyProperties.insert(9, cn.getDisplayName());
            } else if (c instanceof Host) {
                keyProperties.insert(0, ",host=");
                keyProperties.insert(6, c.getName());
            } else if (c == null) {
                // May happen in unit testing and/or some embedding scenarios
                keyProperties.append(",container");
                keyProperties.append(containerCount++);
                keyProperties.append("=null");
                break;
            } else {
                // Should never happen...
                keyProperties.append(",container");
                keyProperties.append(containerCount++);
                keyProperties.append('=');
                keyProperties.append(c.getName());
            }
            c = c.getParent();
        }
        return keyProperties.toString();
    }


    public ObjectName[] getChildren() {
        List<ObjectName> names;
        childrenLock.readLock().lock();
        try {
            names = new ArrayList<>(children.size());
            for (Container next : children.values()) {
                if (next instanceof ContainerBase) {
                    names.add(next.getObjectName());
                }
            }
        } finally {
            childrenLock.readLock().unlock();
        }
        return names.toArray(new ObjectName[0]);
    }


    // -------------------- Background Thread --------------------

    /**
     * Start the background thread that will periodically check for session timeouts.
     */
    protected void threadStart() {
        if (backgroundProcessorDelay > 0 &&
                (getState().isAvailable() || LifecycleState.STARTING_PREP.equals(getState())) &&
                (backgroundProcessorFuture == null || backgroundProcessorFuture.isDone())) {
            if (backgroundProcessorFuture != null && backgroundProcessorFuture.isDone()) {
                // There was an error executing the scheduled task, get it and log it
                try {
                    backgroundProcessorFuture.get();
                } catch (InterruptedException | ExecutionException e) {
                    log.error(sm.getString("containerBase.backgroundProcess.error"), e);
                }
            }
            backgroundProcessorFuture = Container.getService(this).getServer().getUtilityExecutor()
                    .scheduleWithFixedDelay(new ContainerBackgroundProcessor(), backgroundProcessorDelay,
                            backgroundProcessorDelay, TimeUnit.SECONDS);
        }
    }


    /**
     * Stop the background thread that is periodically checking for session timeouts.
     */
    protected void threadStop() {
        if (backgroundProcessorFuture != null) {
            backgroundProcessorFuture.cancel(true);
            backgroundProcessorFuture = null;
        }
    }


    @Override
    public final String toString() {
        StringBuilder sb = new StringBuilder();
        Container parent = getParent();
        if (parent != null) {
            sb.append(parent.toString());
            sb.append('.');
        }
        sb.append(this.getClass().getSimpleName());
        sb.append('[');
        sb.append(getName());
        sb.append(']');
        return sb.toString();
    }

    // ------------------------------- ContainerBackgroundProcessor Inner Class

    protected class ContainerBackgroundProcessorMonitor implements Runnable {
        @Override
        public void run() {
            if (getState().isAvailable()) {
                threadStart();
            }
        }
    }

    /**
     * Private runnable class to invoke the backgroundProcess method of this container and its children after a fixed
     * delay.
     */
    protected class ContainerBackgroundProcessor implements Runnable {

        @Override
        public void run() {
            processChildren(ContainerBase.this);
        }

        protected void processChildren(Container container) {
            ClassLoader originalClassLoader = null;

            try {
                if (container instanceof Context) {
                    Loader loader = ((Context) container).getLoader();
                    // Loader will be null for FailedContext instances
                    if (loader == null) {
                        return;
                    }

                    // Ensure background processing for Contexts and Wrappers
                    // is performed under the web app's class loader
                    originalClassLoader = ((Context) container).bind(null);
                }
                container.backgroundProcess();
                Container[] children = container.findChildren();
                for (Container child : children) {
                    if (child.getBackgroundProcessorDelay() <= 0) {
                        processChildren(child);
                    }
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.error(sm.getString("containerBase.backgroundProcess.error"), t);
            } finally {
                if (container instanceof Context) {
                    ((Context) container).unbind(originalClassLoader);
                }
            }
        }
    }


    // ---------------------------- Inner classes used with start/stop Executor

    private static class StartChild implements Callable<Void> {

        private Container child;

        StartChild(Container child) {
            this.child = child;
        }

        @Override
        public Void call() throws LifecycleException {
            child.start();
            return null;
        }
    }

    private static class StopChild implements Callable<Void> {

        private Container child;

        StopChild(Container child) {
            this.child = child;
        }

        @Override
        public Void call() throws LifecycleException {
            if (child.getState().isAvailable()) {
                child.stop();
            }
            return null;
        }
    }

}
