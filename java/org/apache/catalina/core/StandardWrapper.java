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

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.UnavailableException;
import jakarta.servlet.annotation.MultipartConfig;

import org.apache.catalina.Container;
import org.apache.catalina.ContainerServlet;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Wrapper;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.PeriodicEventListener;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.log.SystemLogHandler;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.modeler.Util;

/**
 * Standard implementation of the <b>Wrapper</b> interface that represents an individual servlet definition. No child
 * Containers are allowed, and the parent Container must be a Context.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public class StandardWrapper extends ContainerBase implements ServletConfig, Wrapper, NotificationEmitter {

    private final Log log = LogFactory.getLog(StandardWrapper.class); // must not be static

    protected static final String[] DEFAULT_SERVLET_METHODS = new String[] { "GET", "HEAD", "POST" };

    // ----------------------------------------------------------- Constructors


    /**
     * Create a new StandardWrapper component with the default basic Valve.
     */
    public StandardWrapper() {

        super();
        swValve = new StandardWrapperValve();
        pipeline.setBasic(swValve);
        broadcaster = new NotificationBroadcasterSupport();

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The date and time at which this servlet will become available (in milliseconds since the epoch), or zero if the
     * servlet is available. If this value equals Long.MAX_VALUE, the unavailability of this servlet is considered
     * permanent.
     */
    protected long available = 0L;

    /**
     * The broadcaster that sends j2ee notifications.
     */
    protected final NotificationBroadcasterSupport broadcaster;

    /**
     * The count of allocations that are currently active.
     */
    protected final AtomicInteger countAllocated = new AtomicInteger(0);


    /**
     * The facade associated with this wrapper.
     */
    protected final StandardWrapperFacade facade = new StandardWrapperFacade(this);


    /**
     * The (single) possibly uninitialized instance of this servlet.
     */
    protected volatile Servlet instance = null;


    /**
     * Flag that indicates if this instance has been initialized
     */
    protected volatile boolean instanceInitialized = false;


    /**
     * The load-on-startup order value (negative value means load on first call) for this servlet.
     */
    protected int loadOnStartup = -1;


    /**
     * Mappings associated with the wrapper.
     */
    protected final ArrayList<String> mappings = new ArrayList<>();


    /**
     * The initialization parameters for this servlet, keyed by parameter name.
     */
    protected HashMap<String,String> parameters = new HashMap<>();


    /**
     * The security role references for this servlet, keyed by role name used in the servlet. The corresponding value is
     * the role name of the web application itself.
     */
    protected HashMap<String,String> references = new HashMap<>();


    /**
     * The run-as identity for this servlet.
     */
    protected String runAs = null;

    /**
     * The notification sequence number.
     */
    protected long sequenceNumber = 0;

    /**
     * The fully qualified servlet class name for this servlet.
     */
    protected String servletClass = null;


    /**
     * Are we unloading our servlet instance at the moment?
     */
    protected volatile boolean unloading = false;


    /**
     * Wait time for servlet unload in ms.
     */
    protected long unloadDelay = 2000;


    /**
     * True if this StandardWrapper is for the JspServlet
     */
    protected boolean isJspServlet;


    /**
     * The ObjectName of the JSP monitoring mbean
     */
    protected ObjectName jspMonitorON;


    /**
     * Should we swallow System.out
     */
    protected boolean swallowOutput = false;

    // To support jmx attributes
    protected StandardWrapperValve swValve;
    protected long loadTime = 0;
    protected int classLoadTime = 0;

    /**
     * Multipart config
     */
    protected MultipartConfigElement multipartConfigElement = null;

    /**
     * Async support
     */
    protected boolean asyncSupported = false;

    /**
     * Enabled
     */
    protected boolean enabled = true;

    private boolean overridable = false;

    private final ReentrantReadWriteLock parametersLock = new ReentrantReadWriteLock();

    private final ReentrantReadWriteLock mappingsLock = new ReentrantReadWriteLock();

    private final ReentrantReadWriteLock referencesLock = new ReentrantReadWriteLock();


    // ------------------------------------------------------------- Properties

    @Override
    public boolean isOverridable() {
        return overridable;
    }

    @Override
    public void setOverridable(boolean overridable) {
        this.overridable = overridable;
    }

    @Override
    public long getAvailable() {
        return this.available;
    }


    @Override
    public void setAvailable(long available) {
        long oldAvailable = this.available;
        if (available > System.currentTimeMillis()) {
            this.available = available;
        } else {
            this.available = 0L;
        }
        support.firePropertyChange("available", Long.valueOf(oldAvailable), Long.valueOf(this.available));
    }


    /**
     * @return the number of active allocations of this servlet.
     */
    public int getCountAllocated() {
        return this.countAllocated.get();
    }


    @Override
    public int getLoadOnStartup() {

        if (isJspServlet && loadOnStartup == -1) {
            /*
             * JspServlet must always be preloaded, because its instance is used during registerJMX (when registering
             * the JSP monitoring mbean)
             */
            return Integer.MAX_VALUE;
        } else {
            return this.loadOnStartup;
        }
    }


    @Override
    public void setLoadOnStartup(int value) {

        int oldLoadOnStartup = this.loadOnStartup;
        this.loadOnStartup = value;
        support.firePropertyChange("loadOnStartup", Integer.valueOf(oldLoadOnStartup),
                Integer.valueOf(this.loadOnStartup));

    }


    /**
     * Set the load-on-startup order value from a (possibly null) string. Per the specification, any missing or
     * non-numeric value is converted to a zero, so that this servlet will still be loaded at startup time, but in an
     * arbitrary order.
     *
     * @param value New load-on-startup value
     */
    public void setLoadOnStartupString(String value) {

        try {
            setLoadOnStartup(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            setLoadOnStartup(0);
        }
    }

    /**
     * @return the load-on-startup value that was parsed
     */
    public String getLoadOnStartupString() {
        return Integer.toString(getLoadOnStartup());
    }


    /**
     * Set the parent Container of this Wrapper, but only if it is a Context.
     *
     * @param container Proposed parent Container
     */
    @Override
    public void setParent(Container container) {

        if ((container != null) && !(container instanceof Context)) {
            throw new IllegalArgumentException(sm.getString("standardWrapper.notContext"));
        }
        if (container instanceof StandardContext) {
            swallowOutput = ((StandardContext) container).getSwallowOutput();
            unloadDelay = ((StandardContext) container).getUnloadDelay();
        }
        super.setParent(container);

    }


    @Override
    public String getRunAs() {
        return this.runAs;
    }


    @Override
    public void setRunAs(String runAs) {

        String oldRunAs = this.runAs;
        this.runAs = runAs;
        support.firePropertyChange("runAs", oldRunAs, this.runAs);

    }


    @Override
    public String getServletClass() {
        return this.servletClass;
    }


    @Override
    public void setServletClass(String servletClass) {

        String oldServletClass = this.servletClass;
        this.servletClass = servletClass;
        support.firePropertyChange("servletClass", oldServletClass, this.servletClass);
        if (Constants.JSP_SERVLET_CLASS.equals(servletClass)) {
            isJspServlet = true;
        }
    }


    /**
     * Set the name of this servlet. This is an alias for the normal <code>Container.setName()</code> method, and
     * complements the <code>getServletName()</code> method required by the <code>ServletConfig</code> interface.
     *
     * @param name The new name of this servlet
     */
    public void setServletName(String name) {

        setName(name);

    }


    @Override
    public boolean isUnavailable() {

        if (!isEnabled()) {
            return true;
        } else if (available == 0L) {
            return false;
        } else if (available <= System.currentTimeMillis()) {
            available = 0L;
            return false;
        } else {
            return true;
        }

    }


    @Override
    public String[] getServletMethods() throws ServletException {

        instance = loadServlet();

        Class<? extends Servlet> servletClazz = instance.getClass();
        if (!jakarta.servlet.http.HttpServlet.class.isAssignableFrom(servletClazz)) {
            return DEFAULT_SERVLET_METHODS;
        }

        Set<String> allow = new HashSet<>();
        allow.add("OPTIONS");

        if (isJspServlet) {
            allow.add("GET");
            allow.add("HEAD");
            allow.add("POST");
        } else {
            allow.add("TRACE");

            Method[] methods = getAllDeclaredMethods(servletClazz);
            for (int i = 0; methods != null && i < methods.length; i++) {
                Method m = methods[i];

                if (m.getName().equals("doGet")) {
                    allow.add("GET");
                    allow.add("HEAD");
                } else if (m.getName().equals("doPost")) {
                    allow.add("POST");
                } else if (m.getName().equals("doPut")) {
                    allow.add("PUT");
                } else if (m.getName().equals("doDelete")) {
                    allow.add("DELETE");
                }
            }
        }

        return allow.toArray(new String[0]);
    }


    @Override
    public Servlet getServlet() {
        return instance;
    }


    @Override
    public void setServlet(Servlet servlet) {
        instance = servlet;
    }


    // --------------------------------------------------------- Public Methods

    @Override
    public synchronized void backgroundProcess() {
        super.backgroundProcess();

        if (!getState().isAvailable()) {
            return;
        }

        if (getServlet() instanceof PeriodicEventListener) {
            ((PeriodicEventListener) getServlet()).periodicEvent();
        }
    }


    /**
     * Extract the root cause from a servlet exception.
     *
     * @param e The servlet exception
     *
     * @return the root cause of the Servlet exception
     */
    public static Throwable getRootCause(ServletException e) {
        Throwable rootCause = e;
        Throwable rootCauseCheck = null;
        // Extra aggressive rootCause finding
        int loops = 0;
        do {
            loops++;
            rootCauseCheck = rootCause.getCause();
            if (rootCauseCheck != null) {
                rootCause = rootCauseCheck;
            }
        } while (rootCauseCheck != null && (loops < 20));
        return rootCause;
    }


    /**
     * Refuse to add a child Container, because Wrappers are the lowest level of the Container hierarchy.
     *
     * @param child Child container to be added
     */
    @Override
    public void addChild(Container child) {

        throw new IllegalStateException(sm.getString("standardWrapper.notChild"));

    }


    @Override
    public void addInitParameter(String name, String value) {

        parametersLock.writeLock().lock();
        try {
            parameters.put(name, value);
        } finally {
            parametersLock.writeLock().unlock();
        }
        fireContainerEvent("addInitParameter", name);

    }


    @Override
    public void addMapping(String mapping) {

        mappingsLock.writeLock().lock();
        try {
            mappings.add(mapping);
        } finally {
            mappingsLock.writeLock().unlock();
        }
        if (parent.getState().equals(LifecycleState.STARTED)) {
            fireContainerEvent(ADD_MAPPING_EVENT, mapping);
        }

    }


    @Override
    public void addSecurityReference(String name, String link) {

        referencesLock.writeLock().lock();
        try {
            references.put(name, link);
        } finally {
            referencesLock.writeLock().unlock();
        }
        fireContainerEvent("addSecurityReference", name);

    }


    @Override
    public Servlet allocate() throws ServletException {

        // If we are currently unloading this servlet, throw an exception
        if (unloading) {
            throw new ServletException(sm.getString("standardWrapper.unloading", getName()));
        }

        boolean newInstance = false;

        // Load and initialize our instance if necessary
        if (instance == null || !instanceInitialized) {
            synchronized (this) {
                if (instance == null) {
                    try {
                        if (log.isTraceEnabled()) {
                            log.trace("Allocating instance");
                        }
                        instance = loadServlet();
                        newInstance = true;
                        // Increment here to prevent a race condition
                        // with unload. Bug 43683, test case #3
                        countAllocated.incrementAndGet();
                    } catch (ServletException e) {
                        throw e;
                    } catch (Throwable e) {
                        ExceptionUtils.handleThrowable(e);
                        throw new ServletException(sm.getString("standardWrapper.allocate"), e);
                    }
                }
                if (!instanceInitialized) {
                    initServlet(instance);
                }
            }
        }

        if (log.isTraceEnabled()) {
            log.trace("  Returning instance");
        }
        // For new instances, count will have been incremented at the
        // time of creation
        if (!newInstance) {
            countAllocated.incrementAndGet();
        }
        return instance;
    }


    @Override
    public void deallocate(Servlet servlet) throws ServletException {
        countAllocated.decrementAndGet();
    }


    @Override
    public String findInitParameter(String name) {

        parametersLock.readLock().lock();
        try {
            return parameters.get(name);
        } finally {
            parametersLock.readLock().unlock();
        }

    }


    @Override
    public String[] findInitParameters() {

        parametersLock.readLock().lock();
        try {
            return parameters.keySet().toArray(new String[0]);
        } finally {
            parametersLock.readLock().unlock();
        }

    }


    @Override
    public String[] findMappings() {

        mappingsLock.readLock().lock();
        try {
            return mappings.toArray(new String[0]);
        } finally {
            mappingsLock.readLock().unlock();
        }

    }


    @Override
    public String findSecurityReference(String name) {
        String reference = null;

        referencesLock.readLock().lock();
        try {
            reference = references.get(name);
        } finally {
            referencesLock.readLock().unlock();
        }

        // If not specified on the Wrapper, check the Context
        if (getParent() instanceof Context) {
            Context context = (Context) getParent();
            if (reference != null) {
                reference = context.findRoleMapping(reference);
            } else {
                reference = context.findRoleMapping(name);
            }
        }

        return reference;
    }


    @Override
    public String[] findSecurityReferences() {

        referencesLock.readLock().lock();
        try {
            return references.keySet().toArray(new String[0]);
        } finally {
            referencesLock.readLock().unlock();
        }

    }


    /**
     * {@inheritDoc}
     * <p>
     * <b>IMPLEMENTATION NOTE</b>: Servlets whose classnames begin with <code>org.apache.catalina.</code> (so-called
     * "container" servlets) are loaded by the same classloader that loaded this class, rather than the classloader for
     * the current web application. This gives such classes access to Catalina internals, which are prevented for
     * classes loaded for web applications.
     */
    @Override
    public synchronized void load() throws ServletException {
        instance = loadServlet();

        if (!instanceInitialized) {
            initServlet(instance);
        }

        if (isJspServlet) {
            StringBuilder oname = new StringBuilder(getDomain());

            oname.append(":type=JspMonitor");

            oname.append(getWebModuleKeyProperties());

            oname.append(",name=");
            oname.append(getName());

            oname.append(getJ2EEKeyProperties());

            try {
                jspMonitorON = new ObjectName(oname.toString());
                Registry.getRegistry(null, null).registerComponent(instance, jspMonitorON, null);
            } catch (Exception ex) {
                log.warn(sm.getString("standardWrapper.jspMonitorError", instance));
            }
        }
    }


    /**
     * Load and initialize an instance of this servlet, if there is not already an initialized instance. This can be
     * used, for example, to load servlets that are marked in the deployment descriptor to be loaded at server startup
     * time.
     *
     * @return the loaded Servlet instance
     *
     * @throws ServletException for a Servlet load error
     */
    public synchronized Servlet loadServlet() throws ServletException {

        // Nothing to do if we already have an instance or an instance pool
        if (instance != null) {
            return instance;
        }

        PrintStream out = System.out;
        if (swallowOutput) {
            SystemLogHandler.startCapture();
        }

        Servlet servlet;
        try {
            long t1 = System.currentTimeMillis();
            // Complain if no servlet class has been specified
            if (servletClass == null) {
                unavailable(null);
                throw new ServletException(sm.getString("standardWrapper.notClass", getName()));
            }

            InstanceManager instanceManager = ((StandardContext) getParent()).getInstanceManager();
            try {
                servlet = (Servlet) instanceManager.newInstance(servletClass);
            } catch (ClassCastException e) {
                unavailable(null);
                // Restore the context ClassLoader
                throw new ServletException(sm.getString("standardWrapper.notServlet", servletClass), e);
            } catch (Throwable e) {
                e = ExceptionUtils.unwrapInvocationTargetException(e);
                ExceptionUtils.handleThrowable(e);
                unavailable(null);

                // Added extra log statement for Bugzilla 36630:
                // https://bz.apache.org/bugzilla/show_bug.cgi?id=36630
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("standardWrapper.instantiate", servletClass), e);
                }

                // Restore the context ClassLoader
                throw new ServletException(sm.getString("standardWrapper.instantiate", servletClass), e);
            }

            if (multipartConfigElement == null) {
                MultipartConfig annotation = servlet.getClass().getAnnotation(MultipartConfig.class);
                if (annotation != null) {
                    multipartConfigElement = new MultipartConfigElement(annotation);
                }
            }

            // Special handling for ContainerServlet instances
            // Note: The InstanceManager checks if the application is permitted
            // to load ContainerServlets
            if (servlet instanceof ContainerServlet) {
                ((ContainerServlet) servlet).setWrapper(this);
            }

            classLoadTime = (int) (System.currentTimeMillis() - t1);

            initServlet(servlet);

            fireContainerEvent("load", this);

            loadTime = System.currentTimeMillis() - t1;
        } finally {
            if (swallowOutput) {
                String log = SystemLogHandler.stopCapture();
                if (log != null && log.length() > 0) {
                    if (getServletContext() != null) {
                        getServletContext().log(log);
                    } else {
                        out.println(log);
                    }
                }
            }
        }
        return servlet;

    }


    private synchronized void initServlet(Servlet servlet) throws ServletException {

        if (instanceInitialized) {
            return;
        }

        // Call the initialization method of this servlet
        try {
            servlet.init(facade);
            instanceInitialized = true;
        } catch (UnavailableException f) {
            unavailable(f);
            throw f;
        } catch (ServletException f) {
            // If the servlet wanted to be unavailable it would have
            // said so, so do not call unavailable(null).
            throw f;
        } catch (Throwable f) {
            ExceptionUtils.handleThrowable(f);
            getServletContext().log(sm.getString("standardWrapper.initException", getName()), f);
            // If the servlet wanted to be unavailable it would have
            // said so, so do not call unavailable(null).
            throw new ServletException(sm.getString("standardWrapper.initException", getName()), f);
        }
    }

    @Override
    public void removeInitParameter(String name) {

        parametersLock.writeLock().lock();
        try {
            parameters.remove(name);
        } finally {
            parametersLock.writeLock().unlock();
        }
        fireContainerEvent("removeInitParameter", name);

    }


    @Override
    public void removeMapping(String mapping) {

        mappingsLock.writeLock().lock();
        try {
            mappings.remove(mapping);
        } finally {
            mappingsLock.writeLock().unlock();
        }
        if (parent.getState().equals(LifecycleState.STARTED)) {
            fireContainerEvent(REMOVE_MAPPING_EVENT, mapping);
        }

    }


    @Override
    public void removeSecurityReference(String name) {

        referencesLock.writeLock().lock();
        try {
            references.remove(name);
        } finally {
            referencesLock.writeLock().unlock();
        }
        fireContainerEvent("removeSecurityReference", name);

    }


    @Override
    public void unavailable(UnavailableException unavailable) {
        getServletContext().log(sm.getString("standardWrapper.unavailable", getName()));
        if (unavailable == null) {
            setAvailable(Long.MAX_VALUE);
        } else if (unavailable.isPermanent()) {
            setAvailable(Long.MAX_VALUE);
        } else {
            int unavailableSeconds = unavailable.getUnavailableSeconds();
            if (unavailableSeconds <= 0) {
                unavailableSeconds = 60; // Arbitrary default
            }
            setAvailable(System.currentTimeMillis() + (unavailableSeconds * 1000L));
        }

    }


    @Override
    public synchronized void unload() throws ServletException {

        // Nothing to do if we have never loaded the instance
        if (instance == null) {
            return;
        }
        unloading = true;

        // Loaf a while if the current instance is allocated
        if (countAllocated.get() > 0) {
            int nRetries = 0;
            long delay = unloadDelay / 20;
            while ((nRetries < 21) && (countAllocated.get() > 0)) {
                if ((nRetries % 10) == 0) {
                    log.info(sm.getString("standardWrapper.waiting", countAllocated.toString(), getName()));
                }
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    // Ignore
                }
                nRetries++;
            }
        }

        if (instanceInitialized) {
            PrintStream out = System.out;
            if (swallowOutput) {
                SystemLogHandler.startCapture();
            }

            // Call the servlet destroy() method
            try {
                instance.destroy();
            } catch (Throwable t) {
                t = ExceptionUtils.unwrapInvocationTargetException(t);
                ExceptionUtils.handleThrowable(t);
                fireContainerEvent("unload", this);
                unloading = false;
                throw new ServletException(sm.getString("standardWrapper.destroyException", getName()), t);
            } finally {
                // Annotation processing
                if (!((Context) getParent()).getIgnoreAnnotations()) {
                    try {
                        ((Context) getParent()).getInstanceManager().destroyInstance(instance);
                    } catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                        log.error(sm.getString("standardWrapper.destroyInstance", getName()), t);
                    }
                }
                // Write captured output
                if (swallowOutput) {
                    String log = SystemLogHandler.stopCapture();
                    if (log != null && log.length() > 0) {
                        if (getServletContext() != null) {
                            getServletContext().log(log);
                        } else {
                            out.println(log);
                        }
                    }
                }
                instance = null;
                instanceInitialized = false;
            }
        }

        // Deregister the destroyed instance
        instance = null;

        if (isJspServlet && jspMonitorON != null) {
            Registry.getRegistry(null, null).unregisterComponent(jspMonitorON);
        }

        unloading = false;
        fireContainerEvent("unload", this);
    }


    // -------------------------------------------------- ServletConfig Methods


    @Override
    public String getInitParameter(String name) {
        return findInitParameter(name);
    }


    @Override
    public Enumeration<String> getInitParameterNames() {

        parametersLock.readLock().lock();
        try {
            return Collections.enumeration(parameters.keySet());
        } finally {
            parametersLock.readLock().unlock();
        }

    }


    @Override
    public ServletContext getServletContext() {
        if (parent == null) {
            return null;
        } else if (!(parent instanceof Context)) {
            return null;
        } else {
            return ((Context) parent).getServletContext();
        }
    }


    @Override
    public String getServletName() {
        return getName();
    }

    public long getProcessingTime() {
        return swValve.getProcessingTime();
    }

    public long getMaxTime() {
        return swValve.getMaxTime();
    }

    public long getMinTime() {
        return swValve.getMinTime();
    }

    /**
     * Returns the number of requests processed by the wrapper.
     *
     * @return the number of requests processed by the wrapper.
     */
    public long getRequestCount() {
        return swValve.getRequestCount();
    }

    /**
     * Returns the number of requests processed by the wrapper that resulted in an error.
     *
     * @return the number of requests processed by the wrapper that resulted in an error.
     */
    public long getErrorCount() {
        return swValve.getErrorCount();
    }

    /**
     * Increment the error count used for monitoring.
     */
    @Override
    public void incrementErrorCount() {
        swValve.incrementErrorCount();
    }

    public long getLoadTime() {
        return loadTime;
    }

    public int getClassLoadTime() {
        return classLoadTime;
    }

    @Override
    public MultipartConfigElement getMultipartConfigElement() {
        return multipartConfigElement;
    }

    @Override
    public void setMultipartConfigElement(MultipartConfigElement multipartConfigElement) {
        this.multipartConfigElement = multipartConfigElement;
    }

    @Override
    public boolean isAsyncSupported() {
        return asyncSupported;
    }

    @Override
    public void setAsyncSupported(boolean asyncSupported) {
        this.asyncSupported = asyncSupported;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // -------------------------------------------------------- Package Methods


    // -------------------------------------------------------- protected Methods


    protected Method[] getAllDeclaredMethods(Class<?> c) {

        if (c.equals(jakarta.servlet.http.HttpServlet.class)) {
            return null;
        }

        Method[] parentMethods = getAllDeclaredMethods(c.getSuperclass());

        Method[] thisMethods = c.getDeclaredMethods();
        if (thisMethods.length == 0) {
            return parentMethods;
        }

        if ((parentMethods != null) && (parentMethods.length > 0)) {
            Method[] allMethods = new Method[parentMethods.length + thisMethods.length];
            System.arraycopy(parentMethods, 0, allMethods, 0, parentMethods.length);
            System.arraycopy(thisMethods, 0, allMethods, parentMethods.length, thisMethods.length);

            thisMethods = allMethods;
        }

        return thisMethods;
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * Start this component and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error that prevents this component from being
     *                                   used
     */
    @Override
    protected void startInternal() throws LifecycleException {

        // Send j2ee.state.starting notification
        if (this.getObjectName() != null) {
            Notification notification = new Notification("j2ee.state.starting", this.getObjectName(), sequenceNumber++);
            broadcaster.sendNotification(notification);
        }

        // Start up this component
        super.startInternal();

        setAvailable(0L);

        // Send j2ee.state.running notification
        if (this.getObjectName() != null) {
            Notification notification = new Notification("j2ee.state.running", this.getObjectName(), sequenceNumber++);
            broadcaster.sendNotification(notification);
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

        setAvailable(Long.MAX_VALUE);

        // Send j2ee.state.stopping notification
        if (this.getObjectName() != null) {
            Notification notification = new Notification("j2ee.state.stopping", this.getObjectName(), sequenceNumber++);
            broadcaster.sendNotification(notification);
        }

        // Shut down our servlet instance (if it has been initialized)
        try {
            unload();
        } catch (ServletException e) {
            getServletContext().log(sm.getString("standardWrapper.unloadException", getName()), e);
        }

        // Shut down this component
        super.stopInternal();

        // Send j2ee.state.stopped notification
        if (this.getObjectName() != null) {
            Notification notification = new Notification("j2ee.state.stopped", this.getObjectName(), sequenceNumber++);
            broadcaster.sendNotification(notification);
        }

        // Send j2ee.object.deleted notification
        Notification notification = new Notification("j2ee.object.deleted", this.getObjectName(), sequenceNumber++);
        broadcaster.sendNotification(notification);

    }


    @Override
    protected String getObjectNameKeyProperties() {

        StringBuilder keyProperties = new StringBuilder("j2eeType=Servlet");

        keyProperties.append(getWebModuleKeyProperties());

        keyProperties.append(",name=");

        String name = getName();
        if (Util.objectNameValueNeedsQuote(name)) {
            name = ObjectName.quote(name);
        }
        keyProperties.append(name);

        keyProperties.append(getJ2EEKeyProperties());

        return keyProperties.toString();
    }


    private String getWebModuleKeyProperties() {

        StringBuilder keyProperties = new StringBuilder(",WebModule=//");
        String hostName = getParent().getParent().getName();
        if (hostName == null) {
            keyProperties.append("DEFAULT");
        } else {
            keyProperties.append(hostName);
        }

        String contextName = getParent().getName();
        if (!contextName.startsWith("/")) {
            keyProperties.append('/');
        }
        keyProperties.append(contextName);

        return keyProperties.toString();
    }

    private String getJ2EEKeyProperties() {

        StringBuilder keyProperties = new StringBuilder(",J2EEApplication=");

        StandardContext ctx = null;
        if (parent instanceof StandardContext) {
            ctx = (StandardContext) getParent();
        }

        if (ctx == null) {
            keyProperties.append("none");
        } else {
            keyProperties.append(ctx.getJ2EEApplication());
        }
        keyProperties.append(",J2EEServer=");
        if (ctx == null) {
            keyProperties.append("none");
        } else {
            keyProperties.append(ctx.getJ2EEServer());
        }

        return keyProperties.toString();
    }


    @Override
    public void removeNotificationListener(NotificationListener listener, NotificationFilter filter, Object object)
            throws ListenerNotFoundException {
        broadcaster.removeNotificationListener(listener, filter, object);
    }

    protected MBeanNotificationInfo[] notificationInfo;

    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        if (notificationInfo == null) {
            notificationInfo = new MBeanNotificationInfo[] {
                    new MBeanNotificationInfo(new String[] { "j2ee.object.created" }, Notification.class.getName(),
                            "servlet is created"),
                    new MBeanNotificationInfo(new String[] { "j2ee.state.starting" }, Notification.class.getName(),
                            "servlet is starting"),
                    new MBeanNotificationInfo(new String[] { "j2ee.state.running" }, Notification.class.getName(),
                            "servlet is running"),
                    new MBeanNotificationInfo(new String[] { "j2ee.state.stopped" }, Notification.class.getName(),
                            "servlet start to stopped"),
                    new MBeanNotificationInfo(new String[] { "j2ee.object.stopped" }, Notification.class.getName(),
                            "servlet is stopped"),
                    new MBeanNotificationInfo(new String[] { "j2ee.object.deleted" }, Notification.class.getName(),
                            "servlet is deleted") };
        }
        return notificationInfo;
    }


    @Override
    public void addNotificationListener(NotificationListener listener, NotificationFilter filter, Object object)
            throws IllegalArgumentException {
        broadcaster.addNotificationListener(listener, filter, object);
    }


    @Override
    public void removeNotificationListener(NotificationListener listener) throws ListenerNotFoundException {
        broadcaster.removeNotificationListener(listener);
    }
}
