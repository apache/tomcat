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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Stack;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.SingleThreadModel;
import javax.servlet.UnavailableException;

import org.apache.PeriodicEventListener;
import org.apache.catalina.Container;
import org.apache.catalina.ContainerServlet;
import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.InstanceEvent;
import org.apache.catalina.InstanceListener;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.InstanceManager;
import org.apache.catalina.security.SecurityUtil;
import org.apache.catalina.util.Enumerator;
import org.apache.catalina.util.InstanceSupport;
import org.apache.tomcat.util.log.SystemLogHandler;
import org.apache.tomcat.util.modeler.Registry;

/**
 * Standard implementation of the <b>Wrapper</b> interface that represents
 * an individual servlet definition.  No child Containers are allowed, and
 * the parent Container must be a Context.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @version $Revision$ $Date$
 */
public class StandardWrapper
    extends ContainerBase
    implements ServletConfig, Wrapper, NotificationEmitter {

    protected static org.apache.juli.logging.Log log=
        org.apache.juli.logging.LogFactory.getLog( StandardWrapper.class );

    protected static final String[] DEFAULT_SERVLET_METHODS = new String[] {
                                                    "GET", "HEAD", "POST" };

    // ----------------------------------------------------------- Constructors


    /**
     * Create a new StandardWrapper component with the default basic Valve.
     */
    public StandardWrapper() {

        super();
        swValve=new StandardWrapperValve();
        pipeline.setBasic(swValve);
        broadcaster = new NotificationBroadcasterSupport();

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The date and time at which this servlet will become available (in
     * milliseconds since the epoch), or zero if the servlet is available.
     * If this value equals Long.MAX_VALUE, the unavailability of this
     * servlet is considered permanent.
     */
    protected long available = 0L;
    
    /**
     * The broadcaster that sends j2ee notifications. 
     */
    protected NotificationBroadcasterSupport broadcaster = null;
    
    /**
     * The count of allocations that are currently active (even if they
     * are for the same instance, as will be true on a non-STM servlet).
     */
    protected int countAllocated = 0;


    /**
     * The facade associated with this wrapper.
     */
    protected StandardWrapperFacade facade =
        new StandardWrapperFacade(this);


    /**
     * The descriptive information string for this implementation.
     */
    protected static final String info =
        "org.apache.catalina.core.StandardWrapper/1.0";


    /**
     * The (single) initialized instance of this servlet.
     */
    protected Servlet instance = null;


    /**
     * The support object for our instance listeners.
     */
    protected InstanceSupport instanceSupport = new InstanceSupport(this);


    /**
     * The context-relative URI of the JSP file for this servlet.
     */
    protected String jspFile = null;


    /**
     * The load-on-startup order value (negative value means load on
     * first call) for this servlet.
     */
    protected int loadOnStartup = -1;


    /**
     * Mappings associated with the wrapper.
     */
    protected ArrayList mappings = new ArrayList();


    /**
     * The initialization parameters for this servlet, keyed by
     * parameter name.
     */
    protected HashMap parameters = new HashMap();


    /**
     * The security role references for this servlet, keyed by role name
     * used in the servlet.  The corresponding value is the role name of
     * the web application itself.
     */
    protected HashMap references = new HashMap();


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
     * Does this servlet implement the SingleThreadModel interface?
     */
    protected boolean singleThreadModel = false;


    /**
     * Are we unloading our servlet instance at the moment?
     */
    protected boolean unloading = false;


    /**
     * Maximum number of STM instances.
     */
    protected int maxInstances = 20;


    /**
     * Number of instances currently loaded for a STM servlet.
     */
    protected int nInstances = 0;


    /**
     * Stack containing the STM instances.
     */
    protected Stack instancePool = null;

    
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
    protected long loadTime=0;
    protected int classLoadTime=0;
    
    /**
     * Static class array used when the SecurityManager is turned on and 
     * <code>Servlet.init</code> is invoked.
     */
    protected static Class[] classType = new Class[]{ServletConfig.class};
    
    
    /**
     * Static class array used when the SecurityManager is turned on and 
     * <code>Servlet.service</code>  is invoked.
     */                                                 
    protected static Class[] classTypeUsedInService = new Class[]{
                                                         ServletRequest.class,
                                                         ServletResponse.class};
    

    // ------------------------------------------------------------- Properties


    /**
     * Return the available date/time for this servlet, in milliseconds since
     * the epoch.  If this date/time is Long.MAX_VALUE, it is considered to mean
     * that unavailability is permanent and any request for this servlet will return
     * an SC_NOT_FOUND error.  If this date/time is in the future, any request for
     * this servlet will return an SC_SERVICE_UNAVAILABLE error.  If it is zero,
     * the servlet is currently available.
     */
    public long getAvailable() {

        return (this.available);

    }


    /**
     * Set the available date/time for this servlet, in milliseconds since the
     * epoch.  If this date/time is Long.MAX_VALUE, it is considered to mean
     * that unavailability is permanent and any request for this servlet will return
     * an SC_NOT_FOUND error. If this date/time is in the future, any request for
     * this servlet will return an SC_SERVICE_UNAVAILABLE error.
     *
     * @param available The new available date/time
     */
    public void setAvailable(long available) {

        long oldAvailable = this.available;
        if (available > System.currentTimeMillis())
            this.available = available;
        else
            this.available = 0L;
        support.firePropertyChange("available", new Long(oldAvailable),
                                   new Long(this.available));

    }


    /**
     * Return the number of active allocations of this servlet, even if they
     * are all for the same instance (as will be true for servlets that do
     * not implement <code>SingleThreadModel</code>.
     */
    public int getCountAllocated() {

        return (this.countAllocated);

    }


    public String getEngineName() {
        return ((StandardContext)getParent()).getEngineName();
    }


    /**
     * Return descriptive information about this Container implementation and
     * the corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {

        return (info);

    }


    /**
     * Return the InstanceSupport object for this Wrapper instance.
     */
    public InstanceSupport getInstanceSupport() {

        return (this.instanceSupport);

    }


    /**
     * Return the context-relative URI of the JSP file for this servlet.
     */
    public String getJspFile() {

        return (this.jspFile);

    }


    /**
     * Set the context-relative URI of the JSP file for this servlet.
     *
     * @param jspFile JSP file URI
     */
    public void setJspFile(String jspFile) {

        String oldJspFile = this.jspFile;
        this.jspFile = jspFile;
        support.firePropertyChange("jspFile", oldJspFile, this.jspFile);

        // Each jsp-file needs to be represented by its own JspServlet and
        // corresponding JspMonitoring mbean, because it may be initialized
        // with its own init params
        isJspServlet = true;

    }


    /**
     * Return the load-on-startup order value (negative value means
     * load on first call).
     */
    public int getLoadOnStartup() {

        if (isJspServlet && loadOnStartup < 0) {
            /*
             * JspServlet must always be preloaded, because its instance is
             * used during registerJMX (when registering the JSP
             * monitoring mbean)
             */
             return Integer.MAX_VALUE;
        } else {
            return (this.loadOnStartup);
        }
    }


    /**
     * Set the load-on-startup order value (negative value means
     * load on first call).
     *
     * @param value New load-on-startup value
     */
    public void setLoadOnStartup(int value) {

        int oldLoadOnStartup = this.loadOnStartup;
        this.loadOnStartup = value;
        support.firePropertyChange("loadOnStartup",
                                   new Integer(oldLoadOnStartup),
                                   new Integer(this.loadOnStartup));

    }



    /**
     * Set the load-on-startup order value from a (possibly null) string.
     * Per the specification, any missing or non-numeric value is converted
     * to a zero, so that this servlet will still be loaded at startup
     * time, but in an arbitrary order.
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

    public String getLoadOnStartupString() {
        return Integer.toString( getLoadOnStartup());
    }


    /**
     * Return maximum number of instances that will be allocated when a single
     * thread model servlet is used.
     */
    public int getMaxInstances() {

        return (this.maxInstances);

    }


    /**
     * Set the maximum number of instances that will be allocated when a single
     * thread model servlet is used.
     *
     * @param maxInstances New value of maxInstances
     */
    public void setMaxInstances(int maxInstances) {

        int oldMaxInstances = this.maxInstances;
        this.maxInstances = maxInstances;
        support.firePropertyChange("maxInstances", oldMaxInstances,
                                   this.maxInstances);

    }


    /**
     * Set the parent Container of this Wrapper, but only if it is a Context.
     *
     * @param container Proposed parent Container
     */
    public void setParent(Container container) {

        if ((container != null) &&
            !(container instanceof Context))
            throw new IllegalArgumentException
                (sm.getString("standardWrapper.notContext"));
        if (container instanceof StandardContext) {
            swallowOutput = ((StandardContext)container).getSwallowOutput();
            unloadDelay = ((StandardContext)container).getUnloadDelay();
        }
        super.setParent(container);

    }


    /**
     * Return the run-as identity for this servlet.
     */
    public String getRunAs() {

        return (this.runAs);

    }


    /**
     * Set the run-as identity for this servlet.
     *
     * @param runAs New run-as identity value
     */
    public void setRunAs(String runAs) {

        String oldRunAs = this.runAs;
        this.runAs = runAs;
        support.firePropertyChange("runAs", oldRunAs, this.runAs);

    }


    /**
     * Return the fully qualified servlet class name for this servlet.
     */
    public String getServletClass() {

        return (this.servletClass);

    }


    /**
     * Set the fully qualified servlet class name for this servlet.
     *
     * @param servletClass Servlet class name
     */
    public void setServletClass(String servletClass) {

        String oldServletClass = this.servletClass;
        this.servletClass = servletClass;
        support.firePropertyChange("servletClass", oldServletClass,
                                   this.servletClass);
        if (Constants.JSP_SERVLET_CLASS.equals(servletClass)) {
            isJspServlet = true;
        }
    }



    /**
     * Set the name of this servlet.  This is an alias for the normal
     * <code>Container.setName()</code> method, and complements the
     * <code>getServletName()</code> method required by the
     * <code>ServletConfig</code> interface.
     *
     * @param name The new name of this servlet
     */
    public void setServletName(String name) {

        setName(name);

    }


    /**
     * Return <code>true</code> if the servlet class represented by this
     * component implements the <code>SingleThreadModel</code> interface.
     */
    public boolean isSingleThreadModel() {

        try {
            loadServlet();
        } catch (Throwable t) {
            ;
        }
        return (singleThreadModel);

    }


    /**
     * Is this servlet currently unavailable?
     */
    public boolean isUnavailable() {

        if (available == 0L)
            return (false);
        else if (available <= System.currentTimeMillis()) {
            available = 0L;
            return (false);
        } else
            return (true);

    }


    /**
     * Gets the names of the methods supported by the underlying servlet.
     *
     * This is the same set of methods included in the Allow response header
     * in response to an OPTIONS request method processed by the underlying
     * servlet.
     *
     * @return Array of names of the methods supported by the underlying
     * servlet
     */
    public String[] getServletMethods() throws ServletException {

        Class servletClazz = loadServlet().getClass();
        if (!javax.servlet.http.HttpServlet.class.isAssignableFrom(
                                                        servletClazz)) {
            return DEFAULT_SERVLET_METHODS;
        }

        HashSet allow = new HashSet();
        allow.add("TRACE");
        allow.add("OPTIONS");
	
        Method[] methods = getAllDeclaredMethods(servletClazz);
        for (int i=0; methods != null && i<methods.length; i++) {
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

        String[] methodNames = new String[allow.size()];
        return (String[]) allow.toArray(methodNames);

    }


    // --------------------------------------------------------- Public Methods


    /**
     * Execute a periodic task, such as reloading, etc. This method will be
     * invoked inside the classloading context of this container. Unexpected
     * throwables will be caught and logged.
     */
    public void backgroundProcess() {
        super.backgroundProcess();
        
        if (!started)
            return;
        
        if (getServlet() != null && (getServlet() instanceof PeriodicEventListener)) {
            ((PeriodicEventListener) getServlet()).periodicEvent();
        }
    }
    
    
    /**
     * Extract the root cause from a servlet exception.
     * 
     * @param e The servlet exception
     */
    public static Throwable getRootCause(ServletException e) {
        Throwable rootCause = e;
        Throwable rootCauseCheck = null;
        // Extra aggressive rootCause finding
        int loops = 0;
        do {
            loops++;
            rootCauseCheck = rootCause.getCause();
            if (rootCauseCheck != null)
                rootCause = rootCauseCheck;
        } while (rootCauseCheck != null && (loops < 20));
        return rootCause;
    }


    /**
     * Refuse to add a child Container, because Wrappers are the lowest level
     * of the Container hierarchy.
     *
     * @param child Child container to be added
     */
    public void addChild(Container child) {

        throw new IllegalStateException
            (sm.getString("standardWrapper.notChild"));

    }


    /**
     * Add a new servlet initialization parameter for this servlet.
     *
     * @param name Name of this initialization parameter to add
     * @param value Value of this initialization parameter to add
     */
    public void addInitParameter(String name, String value) {

        synchronized (parameters) {
            parameters.put(name, value);
        }
        fireContainerEvent("addInitParameter", name);

    }


    /**
     * Add a new listener interested in InstanceEvents.
     *
     * @param listener The new listener
     */
    public void addInstanceListener(InstanceListener listener) {

        instanceSupport.addInstanceListener(listener);

    }


    /**
     * Add a mapping associated with the Wrapper.
     *
     * @param mapping The new wrapper mapping
     */
    public void addMapping(String mapping) {

        synchronized (mappings) {
            mappings.add(mapping);
        }
        fireContainerEvent("addMapping", mapping);

    }


    /**
     * Add a new security role reference record to the set of records for
     * this servlet.
     *
     * @param name Role name used within this servlet
     * @param link Role name used within the web application
     */
    public void addSecurityReference(String name, String link) {

        synchronized (references) {
            references.put(name, link);
        }
        fireContainerEvent("addSecurityReference", name);

    }


    /**
     * Return the associated servlet instance.
     */
    public Servlet getServlet() {
        return instance;
    }
    
    
    /**
     * Allocate an initialized instance of this Servlet that is ready to have
     * its <code>service()</code> method called.  If the servlet class does
     * not implement <code>SingleThreadModel</code>, the (only) initialized
     * instance may be returned immediately.  If the servlet class implements
     * <code>SingleThreadModel</code>, the Wrapper implementation must ensure
     * that this instance is not allocated again until it is deallocated by a
     * call to <code>deallocate()</code>.
     *
     * @exception ServletException if the servlet init() method threw
     *  an exception
     * @exception ServletException if a loading error occurs
     */
    public Servlet allocate() throws ServletException {

        // If we are currently unloading this servlet, throw an exception
        if (unloading)
            throw new ServletException
              (sm.getString("standardWrapper.unloading", getName()));

        // If not SingleThreadedModel, return the same instance every time
        if (!singleThreadModel) {

            // Load and initialize our instance if necessary
            if (instance == null) {
                synchronized (this) {
                    if (instance == null) {
                        try {
                            if (log.isDebugEnabled())
                                log.debug("Allocating non-STM instance");

                            instance = loadServlet();
                        } catch (ServletException e) {
                            throw e;
                        } catch (Throwable e) {
                            throw new ServletException
                                (sm.getString("standardWrapper.allocate"), e);
                        }
                    }
                }
            }

            if (!singleThreadModel) {
                if (log.isTraceEnabled())
                    log.trace("  Returning non-STM instance");
                countAllocated++;
                return (instance);
            }

        }

        synchronized (instancePool) {

            while (countAllocated >= nInstances) {
                // Allocate a new instance if possible, or else wait
                if (nInstances < maxInstances) {
                    try {
                        instancePool.push(loadServlet());
                        nInstances++;
                    } catch (ServletException e) {
                        throw e;
                    } catch (Throwable e) {
                        throw new ServletException
                            (sm.getString("standardWrapper.allocate"), e);
                    }
                } else {
                    try {
                        instancePool.wait();
                    } catch (InterruptedException e) {
                        ;
                    }
                }
            }
            if (log.isTraceEnabled())
                log.trace("  Returning allocated STM instance");
            countAllocated++;
            return (Servlet) instancePool.pop();

        }

    }


    /**
     * Return this previously allocated servlet to the pool of available
     * instances.  If this servlet class does not implement SingleThreadModel,
     * no action is actually required.
     *
     * @param servlet The servlet to be returned
     *
     * @exception ServletException if a deallocation error occurs
     */
    public void deallocate(Servlet servlet) throws ServletException {

        // If not SingleThreadModel, no action is required
        if (!singleThreadModel) {
            countAllocated--;
            return;
        }

        // Unlock and free this instance
        synchronized (instancePool) {
            countAllocated--;
            instancePool.push(servlet);
            instancePool.notify();
        }

    }


    /**
     * Return the value for the specified initialization parameter name,
     * if any; otherwise return <code>null</code>.
     *
     * @param name Name of the requested initialization parameter
     */
    public String findInitParameter(String name) {

        synchronized (parameters) {
            return ((String) parameters.get(name));
        }

    }


    /**
     * Return the names of all defined initialization parameters for this
     * servlet.
     */
    public String[] findInitParameters() {

        synchronized (parameters) {
            String results[] = new String[parameters.size()];
            return ((String[]) parameters.keySet().toArray(results));
        }

    }


    /**
     * Return the mappings associated with this wrapper.
     */
    public String[] findMappings() {

        synchronized (mappings) {
            return (String[]) mappings.toArray(new String[mappings.size()]);
        }

    }


    /**
     * Return the security role link for the specified security role
     * reference name, if any; otherwise return <code>null</code>.
     *
     * @param name Security role reference used within this servlet
     */
    public String findSecurityReference(String name) {

        synchronized (references) {
            return ((String) references.get(name));
        }

    }


    /**
     * Return the set of security role reference names associated with
     * this servlet, if any; otherwise return a zero-length array.
     */
    public String[] findSecurityReferences() {

        synchronized (references) {
            String results[] = new String[references.size()];
            return ((String[]) references.keySet().toArray(results));
        }

    }


    /**
     * FIXME: Fooling introspection ...
     */
    public Wrapper findMappingObject() {
        return (Wrapper) getMappingObject();
    }


    /**
     * Load and initialize an instance of this servlet, if there is not already
     * at least one initialized instance.  This can be used, for example, to
     * load servlets that are marked in the deployment descriptor to be loaded
     * at server startup time.
     * <p>
     * <b>IMPLEMENTATION NOTE</b>:  Servlets whose classnames begin with
     * <code>org.apache.catalina.</code> (so-called "container" servlets)
     * are loaded by the same classloader that loaded this class, rather than
     * the classloader for the current web application.
     * This gives such classes access to Catalina internals, which are
     * prevented for classes loaded for web applications.
     *
     * @exception ServletException if the servlet init() method threw
     *  an exception
     * @exception ServletException if some other loading problem occurs
     */
    public synchronized void load() throws ServletException {
        instance = loadServlet();
    }


    /**
     * Load and initialize an instance of this servlet, if there is not already
     * at least one initialized instance.  This can be used, for example, to
     * load servlets that are marked in the deployment descriptor to be loaded
     * at server startup time.
     */
    public synchronized Servlet loadServlet() throws ServletException {

        // Nothing to do if we already have an instance or an instance pool
        if (!singleThreadModel && (instance != null))
            return instance;

        PrintStream out = System.out;
        if (swallowOutput) {
            SystemLogHandler.startCapture();
        }

        Servlet servlet;
        try {
            long t1=System.currentTimeMillis();
            // If this "servlet" is really a JSP file, get the right class.
            // HOLD YOUR NOSE - this is a kludge that avoids having to do special
            // case Catalina-specific code in Jasper - it also requires that the
            // servlet path be replaced by the <jsp-file> element content in
            // order to be completely effective
            String actualClass = servletClass;
            if ((actualClass == null) && (jspFile != null)) {
                Wrapper jspWrapper = (Wrapper)
                    ((Context) getParent()).findChild(Constants.JSP_SERVLET_NAME);
                if (jspWrapper != null) {
                    actualClass = jspWrapper.getServletClass();
                    // Merge init parameters
                    String paramNames[] = jspWrapper.findInitParameters();
                    for (int i = 0; i < paramNames.length; i++) {
                        if (parameters.get(paramNames[i]) == null) {
                            parameters.put
                                (paramNames[i], 
                                 jspWrapper.findInitParameter(paramNames[i]));
                        }
                    }
                }
            }

            // Complain if no servlet class has been specified
            if (actualClass == null) {
                unavailable(null);
                throw new ServletException
                    (sm.getString("standardWrapper.notClass", getName()));
            }

            InstanceManager instanceManager = ((StandardContext)getParent()).getInstanceManager();
            try {
                servlet = (Servlet) instanceManager.newInstance(actualClass);
            } catch (ClassCastException e) {
                unavailable(null);
                // Restore the context ClassLoader
                throw new ServletException
                    (sm.getString("standardWrapper.notServlet", actualClass), e);
            } catch (Throwable e) {
                unavailable(null);

                // Added extra log statement for Bugzilla 36630:
                // http://issues.apache.org/bugzilla/show_bug.cgi?id=36630
                if(log.isDebugEnabled()) {
                    log.debug(sm.getString("standardWrapper.instantiate", actualClass), e);
                }

                // Restore the context ClassLoader
                throw new ServletException
                    (sm.getString("standardWrapper.instantiate", actualClass), e);
            }

            // Special handling for ContainerServlet instances
            if ((servlet instanceof ContainerServlet) &&
                  (isContainerProvidedServlet(actualClass) ||
                    ((Context)getParent()).getPrivileged() )) {
                ((ContainerServlet) servlet).setWrapper(this);
            }

            classLoadTime=(int) (System.currentTimeMillis() -t1);
            // Call the initialization method of this servlet
            try {
                instanceSupport.fireInstanceEvent(InstanceEvent.BEFORE_INIT_EVENT,
                                                  servlet);

                if( Globals.IS_SECURITY_ENABLED) {

                    Object[] args = new Object[]{((ServletConfig)facade)};
                    SecurityUtil.doAsPrivilege("init",
                                               servlet,
                                               classType,
                                               args);
                    args = null;
                } else {
                    servlet.init(facade);
                }

                // Invoke jspInit on JSP pages
                if ((loadOnStartup >= 0) && (jspFile != null)) {
                    // Invoking jspInit
                    DummyRequest req = new DummyRequest();
                    req.setServletPath(jspFile);
                    req.setQueryString("jsp_precompile=true");
                    DummyResponse res = new DummyResponse();

                    if( Globals.IS_SECURITY_ENABLED) {
                        Object[] args = new Object[]{req, res};
                        SecurityUtil.doAsPrivilege("service",
                                                   servlet,
                                                   classTypeUsedInService,
                                                   args);
                        args = null;
                    } else {
                        servlet.service(req, res);
                    }
                }
                instanceSupport.fireInstanceEvent(InstanceEvent.AFTER_INIT_EVENT,
                                                  servlet);
            } catch (UnavailableException f) {
                instanceSupport.fireInstanceEvent(InstanceEvent.AFTER_INIT_EVENT,
                                                  servlet, f);
                unavailable(f);
                throw f;
            } catch (ServletException f) {
                instanceSupport.fireInstanceEvent(InstanceEvent.AFTER_INIT_EVENT,
                                                  servlet, f);
                // If the servlet wanted to be unavailable it would have
                // said so, so do not call unavailable(null).
                throw f;
            } catch (Throwable f) {
                getServletContext().log("StandardWrapper.Throwable", f );
                instanceSupport.fireInstanceEvent(InstanceEvent.AFTER_INIT_EVENT,
                                                  servlet, f);
                // If the servlet wanted to be unavailable it would have
                // said so, so do not call unavailable(null).
                throw new ServletException
                    (sm.getString("standardWrapper.initException", getName()), f);
            }

            // Register our newly initialized instance
            singleThreadModel = servlet instanceof SingleThreadModel;
            if (singleThreadModel) {
                if (instancePool == null)
                    instancePool = new Stack();
            }
            fireContainerEvent("load", this);

            loadTime=System.currentTimeMillis() -t1;
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


    /**
     * Remove the specified initialization parameter from this servlet.
     *
     * @param name Name of the initialization parameter to remove
     */
    public void removeInitParameter(String name) {

        synchronized (parameters) {
            parameters.remove(name);
        }
        fireContainerEvent("removeInitParameter", name);

    }


    /**
     * Remove a listener no longer interested in InstanceEvents.
     *
     * @param listener The listener to remove
     */
    public void removeInstanceListener(InstanceListener listener) {

        instanceSupport.removeInstanceListener(listener);

    }


    /**
     * Remove a mapping associated with the wrapper.
     *
     * @param mapping The pattern to remove
     */
    public void removeMapping(String mapping) {

        synchronized (mappings) {
            mappings.remove(mapping);
        }
        fireContainerEvent("removeMapping", mapping);

    }


    /**
     * Remove any security role reference for the specified role name.
     *
     * @param name Security role used within this servlet to be removed
     */
    public void removeSecurityReference(String name) {

        synchronized (references) {
            references.remove(name);
        }
        fireContainerEvent("removeSecurityReference", name);

    }


    /**
     * Return a String representation of this component.
     */
    public String toString() {

        StringBuffer sb = new StringBuffer();
        if (getParent() != null) {
            sb.append(getParent().toString());
            sb.append(".");
        }
        sb.append("StandardWrapper[");
        sb.append(getName());
        sb.append("]");
        return (sb.toString());

    }


    /**
     * Process an UnavailableException, marking this servlet as unavailable
     * for the specified amount of time.
     *
     * @param unavailable The exception that occurred, or <code>null</code>
     *  to mark this servlet as permanently unavailable
     */
    public void unavailable(UnavailableException unavailable) {
        getServletContext().log(sm.getString("standardWrapper.unavailable", getName()));
        if (unavailable == null)
            setAvailable(Long.MAX_VALUE);
        else if (unavailable.isPermanent())
            setAvailable(Long.MAX_VALUE);
        else {
            int unavailableSeconds = unavailable.getUnavailableSeconds();
            if (unavailableSeconds <= 0)
                unavailableSeconds = 60;        // Arbitrary default
            setAvailable(System.currentTimeMillis() +
                         (unavailableSeconds * 1000L));
        }

    }


    /**
     * Unload all initialized instances of this servlet, after calling the
     * <code>destroy()</code> method for each instance.  This can be used,
     * for example, prior to shutting down the entire servlet engine, or
     * prior to reloading all of the classes from the Loader associated with
     * our Loader's repository.
     *
     * @exception ServletException if an exception is thrown by the
     *  destroy() method
     */
    public synchronized void unload() throws ServletException {

        // Nothing to do if we have never loaded the instance
        if (!singleThreadModel && (instance == null))
            return;
        unloading = true;

        // Loaf a while if the current instance is allocated
        // (possibly more than once if non-STM)
        if (countAllocated > 0) {
            int nRetries = 0;
            long delay = unloadDelay / 20;
            while ((nRetries < 21) && (countAllocated > 0)) {
                if ((nRetries % 10) == 0) {
                    log.info(sm.getString("standardWrapper.waiting",
                                          new Integer(countAllocated)));
                }
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    ;
                }
                nRetries++;
            }
        }

        PrintStream out = System.out;
        if (swallowOutput) {
            SystemLogHandler.startCapture();
        }

        // Call the servlet destroy() method
        try {
            instanceSupport.fireInstanceEvent
              (InstanceEvent.BEFORE_DESTROY_EVENT, instance);

            if( Globals.IS_SECURITY_ENABLED) {
                SecurityUtil.doAsPrivilege("destroy",
                                           instance);
                SecurityUtil.remove(instance);                           
            } else {
                instance.destroy();
            }
            
            instanceSupport.fireInstanceEvent
              (InstanceEvent.AFTER_DESTROY_EVENT, instance);

            // Annotation processing
            if (!((Context) getParent()).getIgnoreAnnotations()) {
               ((StandardContext)getParent()).getInstanceManager().destroyInstance(instance);
            }

        } catch (Throwable t) {
            instanceSupport.fireInstanceEvent
              (InstanceEvent.AFTER_DESTROY_EVENT, instance, t);
            instance = null;
            instancePool = null;
            nInstances = 0;
            fireContainerEvent("unload", this);
            unloading = false;
            throw new ServletException
                (sm.getString("standardWrapper.destroyException", getName()),
                 t);
        } finally {
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
        }

        // Deregister the destroyed instance
        instance = null;

        if (singleThreadModel && (instancePool != null)) {
            try {
                while (!instancePool.isEmpty()) {
                    Servlet s = (Servlet) instancePool.pop();
                    if (Globals.IS_SECURITY_ENABLED) {
                        SecurityUtil.doAsPrivilege("destroy", s);
                        SecurityUtil.remove(instance);                           
                    } else {
                        s.destroy();
                    }
                    // Annotation processing
                    if (!((Context) getParent()).getIgnoreAnnotations()) {
                       ((StandardContext)getParent()).getInstanceManager().destroyInstance(s);
                    }
                }
            } catch (Throwable t) {
                instancePool = null;
                nInstances = 0;
                unloading = false;
                fireContainerEvent("unload", this);
                throw new ServletException
                    (sm.getString("standardWrapper.destroyException",
                                  getName()), t);
            }
            instancePool = null;
            nInstances = 0;
        }

        singleThreadModel = false;

        unloading = false;
        fireContainerEvent("unload", this);

    }


    // -------------------------------------------------- ServletConfig Methods


    /**
     * Return the initialization parameter value for the specified name,
     * if any; otherwise return <code>null</code>.
     *
     * @param name Name of the initialization parameter to retrieve
     */
    public String getInitParameter(String name) {

        return (findInitParameter(name));

    }


    /**
     * Return the set of initialization parameter names defined for this
     * servlet.  If none are defined, an empty Enumeration is returned.
     */
    public Enumeration getInitParameterNames() {

        synchronized (parameters) {
            return (new Enumerator(parameters.keySet()));
        }

    }


    /**
     * Return the servlet context with which this servlet is associated.
     */
    public ServletContext getServletContext() {

        if (parent == null)
            return (null);
        else if (!(parent instanceof Context))
            return (null);
        else
            return (((Context) parent).getServletContext());

    }


    /**
     * Return the name of this servlet.
     */
    public String getServletName() {

        return (getName());

    }

    public long getProcessingTime() {
        return swValve.getProcessingTime();
    }

    public void setProcessingTime(long processingTime) {
        swValve.setProcessingTime(processingTime);
    }

    public long getMaxTime() {
        return swValve.getMaxTime();
    }

    public void setMaxTime(long maxTime) {
        swValve.setMaxTime(maxTime);
    }

    public long getMinTime() {
        return swValve.getMinTime();
    }

    public void setMinTime(long minTime) {
        swValve.setMinTime(minTime);
    }

    public int getRequestCount() {
        return swValve.getRequestCount();
    }

    public void setRequestCount(int requestCount) {
        swValve.setRequestCount(requestCount);
    }

    public int getErrorCount() {
        return swValve.getErrorCount();
    }

    public void setErrorCount(int errorCount) {
           swValve.setErrorCount(errorCount);
    }

    /**
     * Increment the error count used for monitoring.
     */
    public void incrementErrorCount(){
        swValve.setErrorCount(swValve.getErrorCount() + 1);
    }

    public long getLoadTime() {
        return loadTime;
    }

    public void setLoadTime(long loadTime) {
        this.loadTime = loadTime;
    }

    public int getClassLoadTime() {
        return classLoadTime;
    }

    // -------------------------------------------------------- Package Methods


    // -------------------------------------------------------- protected Methods


    /**
     * Add a default Mapper implementation if none have been configured
     * explicitly.
     *
     * @param mapperClass Java class name of the default Mapper
     */
    protected void addDefaultMapper(String mapperClass) {

        ;       // No need for a default Mapper on a Wrapper

    }


    /**
     * Return <code>true</code> if the specified class name represents a
     * container provided servlet class that should be loaded by the
     * server class loader.
     *
     * @param classname Name of the class to be checked
     */
    protected boolean isContainerProvidedServlet(String classname) {

        if (classname.startsWith("org.apache.catalina.")) {
            return (true);
        }
        try {
            Class clazz =
                this.getClass().getClassLoader().loadClass(classname);
            return (ContainerServlet.class.isAssignableFrom(clazz));
        } catch (Throwable t) {
            return (false);
        }

    }


    protected Method[] getAllDeclaredMethods(Class c) {

        if (c.equals(javax.servlet.http.HttpServlet.class)) {
            return null;
        }

        Method[] parentMethods = getAllDeclaredMethods(c.getSuperclass());

        Method[] thisMethods = c.getDeclaredMethods();
        if (thisMethods == null) {
            return parentMethods;
        }

        if ((parentMethods != null) && (parentMethods.length > 0)) {
            Method[] allMethods =
                new Method[parentMethods.length + thisMethods.length];
	    System.arraycopy(parentMethods, 0, allMethods, 0,
                             parentMethods.length);
	    System.arraycopy(thisMethods, 0, allMethods, parentMethods.length,
                             thisMethods.length);

	    thisMethods = allMethods;
	}

	return thisMethods;
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * Start this component, pre-loading the servlet if the load-on-startup
     * value is set appropriately.
     *
     * @exception LifecycleException if a fatal error occurs during startup
     */
    public void start() throws LifecycleException {
    
        // Send j2ee.state.starting notification 
        if (this.getObjectName() != null) {
            Notification notification = new Notification("j2ee.state.starting", 
                                                        this.getObjectName(), 
                                                        sequenceNumber++);
            broadcaster.sendNotification(notification);
        }
        
        // Start up this component
        super.start();

        if( oname != null )
            registerJMX((StandardContext)getParent());
        
        // Load and initialize an instance of this servlet if requested
        // MOVED TO StandardContext START() METHOD

        setAvailable(0L);
        
        // Send j2ee.state.running notification 
        if (this.getObjectName() != null) {
            Notification notification = 
                new Notification("j2ee.state.running", this.getObjectName(), 
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }

    }


    /**
     * Stop this component, gracefully shutting down the servlet if it has
     * been initialized.
     *
     * @exception LifecycleException if a fatal error occurs during shutdown
     */
    public void stop() throws LifecycleException {

        setAvailable(Long.MAX_VALUE);
        
        // Send j2ee.state.stopping notification 
        if (this.getObjectName() != null) {
            Notification notification = 
                new Notification("j2ee.state.stopping", this.getObjectName(), 
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }
        
        // Shut down our servlet instance (if it has been initialized)
        try {
            unload();
        } catch (ServletException e) {
            getServletContext().log(sm.getString
                      ("standardWrapper.unloadException", getName()), e);
        }

        // Shut down this component
        super.stop();

        // Send j2ee.state.stoppped notification 
        if (this.getObjectName() != null) {
            Notification notification = 
                new Notification("j2ee.state.stopped", this.getObjectName(), 
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }
        
        if( oname != null ) {
            Registry.getRegistry(null, null).unregisterComponent(oname);
            
            // Send j2ee.object.deleted notification 
            Notification notification = 
                new Notification("j2ee.object.deleted", this.getObjectName(), 
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }

        if (isJspServlet && jspMonitorON != null ) {
            Registry.getRegistry(null, null).unregisterComponent(jspMonitorON);
        }

    }

    protected void registerJMX(StandardContext ctx) {

        String parentName = ctx.getName();
        parentName = ("".equals(parentName)) ? "/" : parentName;

        String hostName = ctx.getParent().getName();
        hostName = (hostName==null) ? "DEFAULT" : hostName;

        String domain = ctx.getDomain();

        String webMod= "//" + hostName + parentName;
        String onameStr = domain + ":j2eeType=Servlet,name=" + getName() +
                          ",WebModule=" + webMod + ",J2EEApplication=" +
                          ctx.getJ2EEApplication() + ",J2EEServer=" +
                          ctx.getJ2EEServer();
        try {
            oname=new ObjectName(onameStr);
            controller=oname;
            Registry.getRegistry(null, null)
                .registerComponent(this, oname, null );
            
            // Send j2ee.object.created notification 
            if (this.getObjectName() != null) {
                Notification notification = new Notification(
                                                "j2ee.object.created", 
                                                this.getObjectName(), 
                                                sequenceNumber++);
                broadcaster.sendNotification(notification);
            }
        } catch( Exception ex ) {
            log.info("Error registering servlet with jmx " + this);
        }

        if (isJspServlet) {
            // Register JSP monitoring mbean
            onameStr = domain + ":type=JspMonitor,name=" + getName()
                       + ",WebModule=" + webMod
                       + ",J2EEApplication=" + ctx.getJ2EEApplication()
                       + ",J2EEServer=" + ctx.getJ2EEServer();
            try {
                jspMonitorON = new ObjectName(onameStr);
                Registry.getRegistry(null, null)
                    .registerComponent(instance, jspMonitorON, null);
            } catch( Exception ex ) {
                log.info("Error registering JSP monitoring with jmx " +
                         instance);
            }
        }
    }
    

    /* Remove a JMX notficationListener 
     * @see javax.management.NotificationEmitter#removeNotificationListener(javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
     */
    public void removeNotificationListener(NotificationListener listener, 
    		NotificationFilter filter, Object object) throws ListenerNotFoundException {
    	broadcaster.removeNotificationListener(listener,filter,object);
    	
    }
    
    protected MBeanNotificationInfo[] notificationInfo;
    
    /* Get JMX Broadcaster Info
     * @TODO use StringManager for international support!
     * @TODO This two events we not send j2ee.state.failed and j2ee.attribute.changed!
     * @see javax.management.NotificationBroadcaster#getNotificationInfo()
     */
    public MBeanNotificationInfo[] getNotificationInfo() {
    	
    	if(notificationInfo == null) {
    		notificationInfo = new MBeanNotificationInfo[]{
    				new MBeanNotificationInfo(new String[] {
    				"j2ee.object.created"},
					Notification.class.getName(),
					"servlet is created"
    				), 
					new MBeanNotificationInfo(new String[] {
					"j2ee.state.starting"},
					Notification.class.getName(),
					"servlet is starting"
					),
					new MBeanNotificationInfo(new String[] {
					"j2ee.state.running"},
					Notification.class.getName(),
					"servlet is running"
					),
					new MBeanNotificationInfo(new String[] {
					"j2ee.state.stopped"},
					Notification.class.getName(),
					"servlet start to stopped"
					),
					new MBeanNotificationInfo(new String[] {
					"j2ee.object.stopped"},
					Notification.class.getName(),
					"servlet is stopped"
					),
					new MBeanNotificationInfo(new String[] {
					"j2ee.object.deleted"},
					Notification.class.getName(),
					"servlet is deleted"
					)
    		};
    		
    	}
    	
    	return notificationInfo;
    }
    
    
    /* Add a JMX-NotificationListener
     * @see javax.management.NotificationBroadcaster#addNotificationListener(javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
     */
    public void addNotificationListener(NotificationListener listener, 
            NotificationFilter filter, Object object) throws IllegalArgumentException {
    	broadcaster.addNotificationListener(listener,filter,object);
    }
    
    
    /**
     * Remove a JMX-NotificationListener 
     * @see javax.management.NotificationBroadcaster#removeNotificationListener(javax.management.NotificationListener)
     */
    public void removeNotificationListener(NotificationListener listener) 
        throws ListenerNotFoundException {
    	broadcaster.removeNotificationListener(listener);
    }
    
    
     // ------------------------------------------------------------- Attributes
        
        
    public boolean isEventProvider() {
        return false;
    }
    
    public boolean isStateManageable() {
        return false;
    }
    
    public boolean isStatisticsProvider() {
        return false;
    }
        
        
}
