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
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.catalina.mbeans.MBeanFactory;
import org.apache.catalina.startup.Catalina;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.catalina.util.ServerInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.StringCache;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.threads.TaskThreadFactory;


/**
 * Standard implementation of the <b>Server</b> interface, available for use (but not required) when deploying and
 * starting Catalina.
 *
 * @author Craig R. McClanahan
 */
public final class StandardServer extends LifecycleMBeanBase implements Server {

    private static final Log log = LogFactory.getLog(StandardServer.class);
    private static final StringManager sm = StringManager.getManager(StandardServer.class);


    // ------------------------------------------------------------ Constructor

    /**
     * Construct a default instance of this class.
     */
    public StandardServer() {

        super();

        globalNamingResources = new NamingResourcesImpl();
        globalNamingResources.setContainer(this);

        if (isUseNaming()) {
            namingContextListener = new NamingContextListener();
            addLifecycleListener(namingContextListener);
        } else {
            namingContextListener = null;
        }

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Global naming resources context.
     */
    private javax.naming.Context globalNamingContext = null;


    /**
     * Global naming resources.
     */
    private NamingResourcesImpl globalNamingResources = null;


    /**
     * The naming context listener for this web application.
     */
    private final NamingContextListener namingContextListener;


    /**
     * The port number on which we wait for shutdown commands.
     */
    private int port = 8005;

    private int portOffset = 0;

    /**
     * The address on which we wait for shutdown commands.
     */
    private String address = "localhost";


    /**
     * A random number generator that is <strong>only</strong> used if the shutdown command string is longer than 1024
     * characters.
     */
    private Random random = null;


    /**
     * The set of Services associated with this Server.
     */
    private Service[] services = new Service[0];

    private final ReentrantReadWriteLock servicesLock = new ReentrantReadWriteLock();
    private final Lock servicesReadLock = servicesLock.readLock();
    private final Lock servicesWriteLock = servicesLock.writeLock();

    /**
     * The shutdown command string we are looking for.
     */
    private String shutdown = "SHUTDOWN";


    /**
     * The property change support for this component.
     */
    final PropertyChangeSupport support = new PropertyChangeSupport(this);

    private volatile boolean stopAwait = false;

    private Catalina catalina = null;

    private ClassLoader parentClassLoader = null;

    /**
     * Thread that currently is inside our await() method.
     */
    private volatile Thread awaitThread = null;

    /**
     * Server socket that is used to wait for the shutdown command.
     */
    private volatile ServerSocket awaitSocket = null;

    private File catalinaHome = null;

    private File catalinaBase = null;

    private final Object namingToken = new Object();

    /**
     * The number of threads available to process utility tasks in this service.
     */
    private int utilityThreads = 2;

    /**
     * The utility threads daemon flag.
     */
    private boolean utilityThreadsAsDaemon = false;

    /**
     * Utility executor with scheduling capabilities.
     */
    private ScheduledThreadPoolExecutor utilityExecutor = null;
    private final Object utilityExecutorLock = new Object();

    /**
     * Utility executor wrapper.
     */
    private ScheduledExecutorService utilityExecutorWrapper = null;


    /**
     * Controller for the periodic lifecycle event.
     */
    private ScheduledFuture<?> periodicLifecycleEventFuture = null;
    private ScheduledFuture<?> monitorFuture;


    /**
     * The lifecycle event period in seconds.
     */
    private int periodicEventDelay = 10;


    // ------------------------------------------------------------- Properties

    @Override
    public Object getNamingToken() {
        return namingToken;
    }


    @Override
    public javax.naming.Context getGlobalNamingContext() {
        return this.globalNamingContext;
    }


    /**
     * Set the global naming resources context.
     *
     * @param globalNamingContext The new global naming resource context
     */
    public void setGlobalNamingContext(javax.naming.Context globalNamingContext) {
        this.globalNamingContext = globalNamingContext;
    }


    @Override
    public NamingResourcesImpl getGlobalNamingResources() {
        return this.globalNamingResources;
    }


    @Override
    public void setGlobalNamingResources(NamingResourcesImpl globalNamingResources) {

        NamingResourcesImpl oldGlobalNamingResources = this.globalNamingResources;
        this.globalNamingResources = globalNamingResources;
        this.globalNamingResources.setContainer(this);
        support.firePropertyChange("globalNamingResources", oldGlobalNamingResources, this.globalNamingResources);

    }


    /**
     * Report the current Tomcat Server Release number
     *
     * @return Tomcat release identifier
     */
    public String getServerInfo() {
        return ServerInfo.getServerInfo();
    }


    /**
     * Return the current server built timestamp
     *
     * @return server built timestamp.
     */
    public String getServerBuilt() {
        return ServerInfo.getServerBuilt();
    }


    /**
     * Return the current server's version number.
     *
     * @return server's version number.
     */
    public String getServerNumber() {
        return ServerInfo.getServerNumber();
    }


    @Override
    public int getPort() {
        return this.port;
    }


    @Override
    public void setPort(int port) {
        this.port = port;
    }


    @Override
    public int getPortOffset() {
        return portOffset;
    }


    @Override
    public void setPortOffset(int portOffset) {
        if (portOffset < 0) {
            throw new IllegalArgumentException(
                    sm.getString("standardServer.portOffset.invalid", Integer.valueOf(portOffset)));
        }
        this.portOffset = portOffset;
    }


    @Override
    public int getPortWithOffset() {
        // Non-positive port values have special meanings and the offset should
        // not apply.
        int port = getPort();
        if (port > 0) {
            return port + getPortOffset();
        } else {
            return port;
        }
    }


    @Override
    public String getAddress() {
        return this.address;
    }


    @Override
    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public String getShutdown() {
        return this.shutdown;
    }


    @Override
    public void setShutdown(String shutdown) {
        this.shutdown = shutdown;
    }


    @Override
    public Catalina getCatalina() {
        return catalina;
    }


    @Override
    public void setCatalina(Catalina catalina) {
        this.catalina = catalina;
    }


    @Override
    public int getUtilityThreads() {
        return utilityThreads;
    }


    /**
     * Handles the special values.
     */
    private static int getUtilityThreadsInternal(int utilityThreads) {
        int result = utilityThreads;
        if (result <= 0) {
            result = Runtime.getRuntime().availableProcessors() + result;
            if (result < 2) {
                result = 2;
            }
        }
        return result;
    }


    @Override
    public void setUtilityThreads(int utilityThreads) {
        // Use local copies to ensure thread safety
        int oldUtilityThreads = this.utilityThreads;
        if (getUtilityThreadsInternal(utilityThreads) < getUtilityThreadsInternal(oldUtilityThreads)) {
            return;
        }
        this.utilityThreads = utilityThreads;
        synchronized (utilityExecutorLock) {
            if (oldUtilityThreads != utilityThreads && utilityExecutor != null) {
                reconfigureUtilityExecutor(getUtilityThreadsInternal(utilityThreads));
            }
        }
    }


    /*
     * Callers must be holding utilityExecutorLock
     */
    private void reconfigureUtilityExecutor(int threads) {
        // The ScheduledThreadPoolExecutor doesn't use MaximumPoolSize, only CorePoolSize is available
        if (utilityExecutor != null) {
            utilityExecutor.setCorePoolSize(threads);
        } else {
            ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(threads,
                    new TaskThreadFactory("Catalina-utility-", utilityThreadsAsDaemon, Thread.MIN_PRIORITY));
            scheduledThreadPoolExecutor.setKeepAliveTime(10, TimeUnit.SECONDS);
            scheduledThreadPoolExecutor.setRemoveOnCancelPolicy(true);
            scheduledThreadPoolExecutor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            utilityExecutor = scheduledThreadPoolExecutor;
            utilityExecutorWrapper = new org.apache.tomcat.util.threads.ScheduledThreadPoolExecutor(utilityExecutor);
        }
    }


    /**
     * Get if the utility threads are daemon threads.
     *
     * @return the threads daemon flag
     */
    public boolean getUtilityThreadsAsDaemon() {
        return utilityThreadsAsDaemon;
    }


    /**
     * Set the utility threads daemon flag. The default value is true.
     *
     * @param utilityThreadsAsDaemon the new thread daemon flag
     */
    public void setUtilityThreadsAsDaemon(boolean utilityThreadsAsDaemon) {
        this.utilityThreadsAsDaemon = utilityThreadsAsDaemon;
    }


    /**
     * @return The period between two lifecycle events, in seconds
     */
    public int getPeriodicEventDelay() {
        return periodicEventDelay;
    }


    /**
     * Set the new period between two lifecycle events in seconds.
     *
     * @param periodicEventDelay The period in seconds, negative or zero will disable events
     */
    public void setPeriodicEventDelay(int periodicEventDelay) {
        this.periodicEventDelay = periodicEventDelay;
    }


    // --------------------------------------------------------- Server Methods


    @Override
    public void addService(Service service) {

        service.setServer(this);

        servicesWriteLock.lock();
        try {
            Service results[] = new Service[services.length + 1];
            System.arraycopy(services, 0, results, 0, services.length);
            results[services.length] = service;
            services = results;
        } finally {
            servicesWriteLock.unlock();
        }

        if (getState().isAvailable()) {
            try {
                service.start();
            } catch (LifecycleException e) {
                // Ignore
            }
        }

        // Report this property change to interested listeners
        support.firePropertyChange("service", null, service);
    }

    public void stopAwait() {
        stopAwait = true;
        Thread t = awaitThread;
        if (t != null) {
            ServerSocket s = awaitSocket;
            if (s != null) {
                awaitSocket = null;
                try {
                    s.close();
                } catch (IOException e) {
                    // Ignored
                }
            }
            t.interrupt();
            try {
                t.join(1000);
            } catch (InterruptedException e) {
                // Ignored
            }
        }
    }

    @Override
    public void await() {
        // Negative values - don't wait on port - tomcat is embedded or we just don't like ports
        if (getPortWithOffset() == -2) {
            // undocumented yet - for embedding apps that are around, alive.
            return;
        }
        Thread currentThread = Thread.currentThread();
        if (getPortWithOffset() == -1) {
            try {
                awaitThread = currentThread;
                while (!stopAwait) {
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException ex) {
                        // continue and check the flag
                    }
                }
            } finally {
                awaitThread = null;
            }
            return;
        }

        // Set up a server socket to wait on
        try {
            awaitSocket = new ServerSocket(getPortWithOffset(), 1, InetAddress.getByName(address));
        } catch (IOException e) {
            log.error(sm.getString("standardServer.awaitSocket.fail", address, String.valueOf(getPortWithOffset()),
                    String.valueOf(getPort()), String.valueOf(getPortOffset())), e);
            return;
        }

        try {
            awaitThread = currentThread;

            // Loop waiting for a connection and a valid command
            while (!stopAwait) {
                ServerSocket serverSocket = awaitSocket;
                if (serverSocket == null) {
                    break;
                }

                // Wait for the next connection
                Socket socket = null;
                StringBuilder command = new StringBuilder();
                try {
                    InputStream stream;
                    long acceptStartTime = System.currentTimeMillis();
                    try {
                        socket = serverSocket.accept();
                        socket.setSoTimeout(10 * 1000); // Ten seconds
                        stream = socket.getInputStream();
                    } catch (SocketTimeoutException ste) {
                        // This should never happen but bug 56684 suggests that
                        // it does.
                        log.warn(sm.getString("standardServer.accept.timeout",
                                Long.valueOf(System.currentTimeMillis() - acceptStartTime)), ste);
                        continue;
                    } catch (IOException e) {
                        if (stopAwait) {
                            // Wait was aborted with socket.close()
                            break;
                        }
                        log.error(sm.getString("standardServer.accept.error"), e);
                        break;
                    }

                    // Read a set of characters from the socket
                    int expected = 1024; // Cut off to avoid DoS attack
                    while (expected < shutdown.length()) {
                        if (random == null) {
                            random = new Random();
                        }
                        expected += random.nextInt() % 1024;
                    }
                    while (expected > 0) {
                        int ch = -1;
                        try {
                            ch = stream.read();
                        } catch (IOException e) {
                            log.warn(sm.getString("standardServer.accept.readError"), e);
                            ch = -1;
                        }
                        // Control character or EOF (-1) terminates loop
                        if (ch < 32 || ch == 127) {
                            break;
                        }
                        command.append((char) ch);
                        expected--;
                    }
                } finally {
                    // Close the socket now that we are done with it
                    try {
                        if (socket != null) {
                            socket.close();
                        }
                    } catch (IOException e) {
                        // Ignore
                    }
                }

                // Match against our command string
                boolean match = command.toString().equals(shutdown);
                if (match) {
                    log.info(sm.getString("standardServer.shutdownViaPort"));
                    break;
                } else {
                    log.warn(sm.getString("standardServer.invalidShutdownCommand", command.toString()));
                }
            }
        } finally {
            ServerSocket serverSocket = awaitSocket;
            awaitThread = null;
            awaitSocket = null;

            // Close the server socket and return
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }


    @Override
    public Service findService(String name) {
        if (name == null) {
            return null;
        }
        servicesReadLock.lock();

        try {
            for (Service service : services) {
                if (name.equals(service.getName())) {
                    return service;
                }
            }
        } finally {
            servicesReadLock.unlock();
        }

        return null;
    }


    @Override
    public Service[] findServices() {
        servicesReadLock.lock();

        try {
            return services.clone();
        } finally {
            servicesReadLock.unlock();
        }
    }

    /**
     * @return the JMX service names.
     */
    public ObjectName[] getServiceNames() {
        servicesReadLock.lock();

        try {
            ObjectName[] onames = new ObjectName[services.length];
            for (int i = 0; i < services.length; i++) {
                onames[i] = ((StandardService) services[i]).getObjectName();
            }
            return onames;
        } finally {
            servicesReadLock.unlock();
        }
    }


    @Override
    public void removeService(Service service) {

        servicesWriteLock.lock();

        try {
            int j = -1;
            for (int i = 0; i < services.length; i++) {
                if (service == services[i]) {
                    j = i;
                    break;
                }
            }
            if (j < 0) {
                return;
            }
            int k = 0;
            Service[] results = new Service[services.length - 1];
            for (int i = 0; i < services.length; i++) {
                if (i != j) {
                    results[k++] = services[i];
                }
            }
            services = results;
        } finally {
            servicesWriteLock.unlock();
        }

        try {
            service.stop();
        } catch (LifecycleException e) {
            // Ignore
        }

        // Report this property change to interested listeners
        support.firePropertyChange("service", service, null);
    }


    @Override
    public File getCatalinaBase() {
        if (catalinaBase != null) {
            return catalinaBase;
        }

        catalinaBase = getCatalinaHome();
        return catalinaBase;
    }


    @Override
    public void setCatalinaBase(File catalinaBase) {
        this.catalinaBase = catalinaBase;
    }


    @Override
    public File getCatalinaHome() {
        return catalinaHome;
    }


    @Override
    public void setCatalinaHome(File catalinaHome) {
        this.catalinaHome = catalinaHome;
    }


    // --------------------------------------------------------- Public Methods

    /**
     * Add a property change listener to this component.
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {

        support.addPropertyChangeListener(listener);

    }


    /**
     * Remove a property change listener from this component.
     *
     * @param listener The listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {

        support.removePropertyChangeListener(listener);

    }


    @Override
    public String toString() {
        return "StandardServer[" + getPort() + ']';
    }


    /**
     * Write the configuration information for this entire <code>Server</code> out to the server.xml configuration file.
     *
     * @exception InstanceNotFoundException                   if the managed resource object cannot be found
     * @exception MBeanException                              if the initializer of the object throws an exception, or
     *                                                            persistence is not supported
     * @exception javax.management.RuntimeOperationsException if an exception is reported by the persistence mechanism
     */
    public synchronized void storeConfig() throws InstanceNotFoundException, MBeanException {
        try {
            // Note: Hard-coded domain used since this object is per Server/JVM
            ObjectName sname = new ObjectName("Catalina:type=StoreConfig");
            MBeanServer server = Registry.getRegistry(null, null).getMBeanServer();
            if (server.isRegistered(sname)) {
                server.invoke(sname, "storeConfig", null, null);
            } else {
                log.error(sm.getString("standardServer.storeConfig.notAvailable", sname));
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("standardServer.storeConfig.error"), t);
        }
    }


    /**
     * Write the configuration information for <code>Context</code> out to the specified configuration file.
     *
     * @param context the context which should save its configuration
     *
     * @exception InstanceNotFoundException                   if the managed resource object cannot be found
     * @exception MBeanException                              if the initializer of the object throws an exception or
     *                                                            persistence is not supported
     * @exception javax.management.RuntimeOperationsException if an exception is reported by the persistence mechanism
     */
    public synchronized void storeContext(Context context) throws InstanceNotFoundException, MBeanException {
        try {
            // Note: Hard-coded domain used since this object is per Server/JVM
            ObjectName sname = new ObjectName("Catalina:type=StoreConfig");
            MBeanServer server = Registry.getRegistry(null, null).getMBeanServer();
            if (server.isRegistered(sname)) {
                server.invoke(sname, "store", new Object[] { context }, new String[] { "java.lang.String" });
            } else {
                log.error(sm.getString("standardServer.storeConfig.notAvailable", sname));
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("standardServer.storeConfig.contextError", context.getName()), t);
        }
    }


    /**
     * @return <code>true</code> if naming should be used.
     */
    private boolean isUseNaming() {
        boolean useNaming = true;
        // Reading the "catalina.useNaming" environment variable
        String useNamingProperty = System.getProperty("catalina.useNaming");
        if (useNamingProperty != null && useNamingProperty.equals("false")) {
            useNaming = false;
        }
        return useNaming;
    }


    @Override
    protected void startInternal() throws LifecycleException {

        fireLifecycleEvent(CONFIGURE_START_EVENT, null);
        setState(LifecycleState.STARTING);

        // Initialize utility executor
        synchronized (utilityExecutorLock) {
            reconfigureUtilityExecutor(getUtilityThreadsInternal(utilityThreads));
            register(utilityExecutor, "type=UtilityExecutor");
        }

        globalNamingResources.start();

        // Start our defined Services
        for (Service service : findServices()) {
            service.start();
        }

        if (periodicEventDelay > 0) {
            monitorFuture = getUtilityExecutor().scheduleWithFixedDelay(this::startPeriodicLifecycleEvent, 0, 60,
                    TimeUnit.SECONDS);
        }
    }


    private void startPeriodicLifecycleEvent() {
        if (periodicLifecycleEventFuture == null || periodicLifecycleEventFuture.isDone()) {
            if (periodicLifecycleEventFuture != null && periodicLifecycleEventFuture.isDone()) {
                // There was an error executing the scheduled task, get it and log it
                try {
                    periodicLifecycleEventFuture.get();
                } catch (InterruptedException | ExecutionException e) {
                    log.error(sm.getString("standardServer.periodicEventError"), e);
                }
            }
            periodicLifecycleEventFuture =
                    getUtilityExecutor().scheduleAtFixedRate(() -> fireLifecycleEvent(PERIODIC_EVENT, null),
                            periodicEventDelay, periodicEventDelay, TimeUnit.SECONDS);
        }
    }


    @Override
    protected void stopInternal() throws LifecycleException {

        setState(LifecycleState.STOPPING);

        if (monitorFuture != null) {
            monitorFuture.cancel(true);
            monitorFuture = null;
        }
        if (periodicLifecycleEventFuture != null) {
            periodicLifecycleEventFuture.cancel(false);
            periodicLifecycleEventFuture = null;
        }

        fireLifecycleEvent(CONFIGURE_STOP_EVENT, null);

        // Stop our defined Services
        for (Service service : findServices()) {
            service.stop();
        }

        synchronized (utilityExecutorLock) {
            if (utilityExecutor != null) {
                utilityExecutor.shutdownNow();
                unregister("type=UtilityExecutor");
                utilityExecutor = null;
            }
        }

        globalNamingResources.stop();

        stopAwait();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This is used to allow connectors to bind to restricted ports under Unix operating environments.
     */
    @Override
    protected void initInternal() throws LifecycleException {

        super.initInternal();

        // Register global String cache
        // Note although the cache is global, if there are multiple Servers
        // present in the JVM (may happen when embedding) then the same cache
        // will be registered under multiple names
        onameStringCache = register(new StringCache(), "type=StringCache");

        // Register the MBeanFactory
        MBeanFactory factory = new MBeanFactory();
        factory.setContainer(this);
        onameMBeanFactory = register(factory, "type=MBeanFactory");

        // Register the naming resources
        globalNamingResources.init();

        // Initialize our defined Services
        for (Service service : findServices()) {
            service.init();
        }
    }

    @Override
    protected void destroyInternal() throws LifecycleException {
        // Destroy our defined Services
        for (Service service : findServices()) {
            service.destroy();
        }

        globalNamingResources.destroy();

        unregister(onameMBeanFactory);

        unregister(onameStringCache);

        super.destroyInternal();
    }

    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null) {
            return parentClassLoader;
        }
        if (catalina != null) {
            return catalina.getParentClassLoader();
        }
        return ClassLoader.getSystemClassLoader();
    }

    @Override
    public void setParentClassLoader(ClassLoader parent) {
        ClassLoader oldParentClassLoader = this.parentClassLoader;
        this.parentClassLoader = parent;
        support.firePropertyChange("parentClassLoader", oldParentClassLoader, this.parentClassLoader);
    }


    private ObjectName onameStringCache;
    private ObjectName onameMBeanFactory;

    /**
     * @return the MBean domain for this server. The domain is obtained using the following search order:
     *             <ol>
     *             <li>Name of first {@link org.apache.catalina.Engine}.</li>
     *             <li>Name of first {@link Service}.</li>
     *             </ol>
     */
    @Override
    protected String getDomainInternal() {

        String domain = null;

        Service[] services = findServices();
        if (services.length > 0) {
            Service service = services[0];
            if (service != null) {
                domain = service.getDomain();
            }
        }
        return domain;
    }


    @Override
    protected String getObjectNameKeyProperties() {
        return "type=Server";
    }

    @Override
    public ScheduledExecutorService getUtilityExecutor() {
        return utilityExecutorWrapper;
    }
}
