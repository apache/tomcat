/*
 * Copyright 1999-2002,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.tomcat.lite.servlet;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.SingleThreadModel;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.lite.http.HttpChannel;
import org.apache.tomcat.lite.http.HttpRequest;
import org.apache.tomcat.lite.http.HttpResponse;
import org.apache.tomcat.lite.http.MappingData;
import org.apache.tomcat.servlets.jsp.BaseJspLoader;
import org.apache.tomcat.servlets.util.Enumerator;

/**
 * Based on Wrapper.
 * 
 * Standard implementation of the <b>Wrapper</b> interface that represents
 * an individual servlet definition.  No child Containers are allowed, and
 * the parent Container must be a Context.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
@SuppressWarnings("deprecation")
public class ServletConfigImpl implements ServletConfig, HttpChannel.HttpService {
    
    protected boolean asyncSupported;
    
    private static Logger log=
        Logger.getLogger(ServletConfigImpl.class.getName());

    private static final String[] DEFAULT_SERVLET_METHODS = new String[] {
                                                    "GET", "HEAD", "POST" };

    // TODO: refactor all 'stm' to separate class (not implemented)
    //    public static final String SINGLE_THREADED_PROXY =
    //             "org.apache.tomcat.servlets.jsp.SingleThreadedProxyServlet";

    protected String description;
    protected Map<String, String> initParams = new HashMap<String, String>();
    protected String servletName;
    protected String servletClassName;
    protected String jspFile;
    protected int loadOnStartup = -1;
    protected String runAs;
    
    protected Map securityRoleRef = new HashMap(); // roleName -> [roleLink]
    
    /**
     * The date and time at which this servlet will become available (in
     * milliseconds since the epoch), or zero if the servlet is available.
     * If this value equals Long.MAX_VALUE, the unavailability of this
     * servlet is considered permanent.
     */
    private transient long available = 0L;
    
    private ServletContextImpl ctx;

    /**
     * The (single) initialized instance of this servlet.
     */
    private transient Servlet instance = null;
    
    /**
     * Are we unloading our servlet instance at the moment?
     */
    private transient boolean unloading = false;

    private Class servletClass = null;

    // Support for SingleThreaded
    /**
     * The count of allocations that are currently active (even if they
     * are for the same instance, as will be true on a non-STM servlet).
     */
    private transient int countAllocated = 0;

    private transient boolean singleThreadModel = false;
    /**
     * Stack containing the STM instances.
     */
    private transient Stack instancePool = null;

    
    // Statistics
    private transient long loadTime=0;
    private transient int classLoadTime=0;

    public AtomicLong processingTime = new AtomicLong();
    public AtomicInteger maxTime = new AtomicInteger();
    public AtomicInteger requestCount = new AtomicInteger();
    public AtomicInteger errorCount = new AtomicInteger();

    // ------------------------------------------------------------- Properties
    public ServletConfigImpl(ServletContextImpl ctx, String name, 
                             String classname) {
        this.servletName = name;
        this.servletClassName = classname;
        this.ctx = ctx;
        ctx.lite.notifyAdd(this);
    }

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

    }


    /**
     * Return the number of active allocations of this servlet, even if they
     * are all for the same instance (as will be true for servlets that do
     * not implement <code>SingleThreadModel</code>.
     */
    public int getCountAllocated() {
        return (this.countAllocated);
    }

    /**
     * Return the jsp-file setting for this servlet.
     */
    public String getJspFile() {
        return jspFile;
    }

    public void setJspFile(String s) {
      this.jspFile = s;
    }
    
    /**
     * Return the load-on-startup order value (negative value means
     * load on first call).
     */
    public int getLoadOnStartup() {
        return loadOnStartup;
    }

    /**
     * Return the fully qualified servlet class name for this servlet.
     */
    public String getServletClass() {
        return servletClassName;
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
     * @throws IOException 
     */
    public String[] getServletMethods() throws ServletException, IOException {

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
     *  MUST be called before service()
     *  This method should be called to get the servlet. After 
     *  service(), dealocate should be called. This deals with STM and
     *  update use counters.
     *  
     *  Normally called from RequestDispatcher and TomcatLite.
     */
    public Servlet allocate() throws ServletException {
        // If we are currently unloading this servlet, throw an exception
        if (unloading) 
            throw new ServletException
              ("allocate() while unloading " + getServletName());

        Servlet servlet = null;
            // never loaded.
        synchronized (this) {
            if (instance == null && !singleThreadModel) {
                try {
                    servlet = loadServlet();
                } catch (ServletException e) {
                    throw e;
                } catch (Throwable e) {
                    throw new ServletException("loadServlet()", e);
                }

                if (servlet != null && !singleThreadModel) {
                    setServlet(servlet);
                }
            }
        }
    

        // If not SingleThreadedModel, return the same instance every time
        if (instance != null) {
            countAllocated++;
            return (instance);
        }
        
        // Simpler policy for ST: unbound number of servlets ( can grow to
        // one per thread )
        
        synchronized (instancePool) {
            if (instancePool.isEmpty()) {
                try {
                    if (servlet != null) {
                        // this is the first invocation
                        countAllocated++;
                        return servlet;
                    }
                    countAllocated++;
                    Servlet newServlet = loadServlet();
                    log.fine("New STM servet " + newServlet + " " + 
                            countAllocated);
                    return newServlet;
                } catch (ServletException e) {
                    throw e;
                } catch (Throwable e) {
                    throw new ServletException("allocate " + getServletName(),
                            e);
                }
            }
            log.fine("Get from pool " + instancePool.size() +  " " +
                    countAllocated);
            Servlet s = (Servlet) instancePool.pop();
            countAllocated++;
            log.fine("After get " + instancePool.size() + " " + s  + 
                    " " + countAllocated);
            return s;
        }
    }


    /**
     * MUST be called after service().
     */
    public void deallocate(Servlet servlet) {
        // If not SingleThreadModel, no action is required
        if (!singleThreadModel) {
            countAllocated--;
            return;
        }

        // Unlock and free this instance
        synchronized (instancePool) {
            countAllocated--;
            if (instancePool.contains(servlet)) {
                System.err.println("Aleady in pool " + servlet + " " 
                        + instancePool.size()+ " " + countAllocated);
                return;
            }
            System.err.println("return  pool " + servlet +  " " + 
                    instancePool.size() + " " + countAllocated);
            instancePool.push(servlet);
        }
    }

    public Servlet newInstance() throws ServletException, IOException {
        String actualClass = servletClassName;

        if (instance != null) {
            return instance;
        }
        if (actualClass == null) {
            // No explicit name. Try to use the framework
            if (jspFile != null) {
                BaseJspLoader mapper = new JspLoader();
                return mapper.loadProxy(jspFile, ctx, this);
            }
            if (actualClass == null) {
                // Object manager can manage servlets.
                Servlet res = (Servlet) ctx.getObjectManager().get( servletName +
                        "-servlet");
                if (res != null) {
                    servletClass = res.getClass();
                    actualClass = servletClass.getName();
                    return res;
                }
            }

            //ctx.getObjectManager().getObject(c);
            //ctx.getObjectManager().getObject(servletName);
        }
            
        
        if (servletClass == null) {
            // set classClass
            loadClass(actualClass);
        }

        
        // jsp-file case. Load the JspProxyServlet instead, with the 
        // right params. Note the JspProxyServlet is _not_ jasper, 
        // nor 'jsp' servlet - it is just a proxy with no special 
        // params. It calls the jsp servlet and jasper to generate the
        // real class.
        
        // this is quite different from catalina, where an ugly kludge was
        // used to use the same jsp servlet in 2 roles
        
        // the jsp proxy is replaced by the web.xml processor
        
        if (servletClass == null) {
            unavailable(null);
            throw new UnavailableException("ClassNotFound: " + actualClass);
        }
        
        // Instantiate and initialize an instance of the servlet class itself
        try {
            return (Servlet) servletClass.newInstance();
        } catch (ClassCastException e) {
            unavailable(null);
            throw new UnavailableException("ClassCast: (Servlet)" + 
                    actualClass);
        } catch (Throwable e) {
            unavailable(null);

            // Added extra log statement for Bugzilla 36630:
            // http://issues.apache.org/bugzilla/show_bug.cgi?id=36630
            if(log.isLoggable(Level.FINE)) {
                log.log(Level.FINE, "newInstance() error: servlet-name: " + 
                        getServletName() +
                        " servlet-class: " + actualClass, e);
            }

            // Restore the context ClassLoader
            throw new ServletException("newInstance() error " + getServletName() + 
                    " " + actualClass, e);
        }
    }

    /**
     * Load and initialize an instance of this servlet, if there is not already
     * at least one initialized instance.  This can be used, for example, to
     * load servlets that are marked in the deployment descriptor to be loaded
     * at server startup time.
     * @throws IOException 
     */
    public synchronized Servlet loadServlet() throws ServletException, IOException {
        // Nothing to do if we already have an instance or an instance pool
        if (!singleThreadModel && (instance != null))
            return instance;
        
        long t1=System.currentTimeMillis();

        Servlet servlet = newInstance();
        
        classLoadTime=(int) (System.currentTimeMillis() -t1);
        
        // Call the initialization method of this servlet
        try {
            servlet.init(this);
        } catch (UnavailableException f) {
            unavailable(f);
            throw f;
        } catch (ServletException f) {
            throw f;
        } catch (Throwable f) {
            getServletContext().log("StandardWrapper.Throwable", f );
            throw new ServletException("Servlet.init()", f);
        }

        // Register our newly initialized instance
        singleThreadModel = servlet instanceof SingleThreadModel;
        if (singleThreadModel) {
            if (instancePool == null)
                instancePool = new Stack();
        }
        loadTime=System.currentTimeMillis() -t1;
        
        return servlet;
    }


    private void loadClass(String actualClass) throws ServletException {
        // Complain if no servlet class has been specified
        if (actualClass == null) {
            unavailable(null);
            throw new ServletException("servlet-class missing " +  
                    getServletName());
        }
        
        ClassLoader classLoader = ctx.getClassLoader(); 
        if (classLoader == null ) 
            classLoader = this.getClass().getClassLoader();
        
        // Load the specified servlet class from the appropriate class loader
        try {
            servletClass = classLoader.loadClass(actualClass);
        } catch (ClassNotFoundException e) {
            servletClass = null;
        }
    }

    /**
     * Return a String representation of this component.
     */
    public String toString() {
        StringBuffer sb = new StringBuffer();
        if (ctx != null) {
            sb.append(ctx.toString());
            sb.append(".");
        }
        sb.append("Servlet[");
        sb.append(getServletName()).append(" ");
        sb.append(servletClassName);
        if (jspFile != null) {
            sb.append(" jsp=").append(jspFile);
        }
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
        getServletContext().log("UnavailableException:" + getServletName());
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
        setAvailable(Long.MAX_VALUE);        

        // Nothing to do if we have never loaded the instance
        if (!singleThreadModel && (instance == null))
            return;
        unloading = true;

        // Loaf a while if the current instance is allocated
        // (possibly more than once if non-STM)
        if (countAllocated > 0) {
            int nRetries = 0;
            long delay = ctx.getUnloadDelay() / 20;
            while ((nRetries < 21) && (countAllocated > 0)) {
                if ((nRetries % 10) == 0) {
                    log.info("Servlet.unload() timeout " + 
                            countAllocated);
                }
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    ;
                }
                nRetries++;
            }
        }

        ClassLoader oldCtxClassLoader =
            Thread.currentThread().getContextClassLoader();
        if (instance != null) {
            ClassLoader classLoader = instance.getClass().getClassLoader();
            
            PrintStream out = System.out;
            // Call the servlet destroy() method
            try {
                Thread.currentThread().setContextClassLoader(classLoader);
                instance.destroy();
            } catch (Throwable t) {
                instance = null;
                //instancePool = null;
                unloading = false;
                throw new ServletException("Servlet.destroy() " + 
                        getServletName(), t);
            } finally {
                // restore the context ClassLoader
                Thread.currentThread().setContextClassLoader(oldCtxClassLoader);
            }
            
            // Deregister the destroyed instance
            instance = null;
        }
        if (singleThreadModel && (instancePool != null)) {
            try {
                ClassLoader classLoader = ctx.getClassLoader();
                Thread.currentThread().setContextClassLoader(classLoader);
                while (!instancePool.isEmpty()) {
                    ((Servlet) instancePool.pop()).destroy();
                }
            } catch (Throwable t) {
                instancePool = null;
                unloading = false;
                throw new ServletException("Servlet.destroy() " + getServletName(), t);
            } finally {
                // restore the context ClassLoader
                Thread.currentThread().setContextClassLoader
                    (oldCtxClassLoader);
            }
            instancePool = null;
        }

        singleThreadModel = false;

        unloading = false;
    }
    
    
    /**
     * Return the initialization parameter value for the specified name,
     * if any; otherwise return <code>null</code>.
     *
     * @param name Name of the initialization parameter to retrieve
     */
    public String getInitParameter(String name) {
        return initParams.get(name);
    }


    /**
     * Return the set of initialization parameter names defined for this
     * servlet.  If none are defined, an empty Enumeration is returned.
     */
    public Enumeration getInitParameterNames() {
        synchronized (initParams) {
            return (new Enumerator(initParams.keySet()));
        }
    }


    /**
     * Return the servlet context with which this servlet is associated.
     */
    public ServletContext getServletContext() {
        return ctx;
    }


    /**
     * Return the name of this servlet.
     */
    public String getServletName() {
        return servletName;
    }

    private Method[] getAllDeclaredMethods(Class c) {

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

    /** Specify the instance. Avoids the class lookup, disables unloading.
     *  Use for embedded case, or to control the allocation.
     * 
     * @param servlet
     */
    public void setServlet(Servlet servlet) {
        instance = servlet;
        ctx.getObjectManager().bind("Servlet:" +
                ctx.getContextPath() + ":" + getServletName(), 
                this);
    }

    public String getSecurityRoleRef(String role) {
        return (String)securityRoleRef.get(role);
    }
    
    public void setSecurityRoleRef(Map securityRoles) {
      this.securityRoleRef = securityRoles;
    }

    public void setConfig(Map initParams) {
      this.initParams = initParams;
    }

    public void setLoadOnStartup(int loadOnStartup) {
      this.loadOnStartup = loadOnStartup;
    }

    public Set<String> addMapping(String... urlPatterns) {
        if (ctx.startDone) {
            // Use the context method instead of the servlet API to 
            // add mappings after context init.
            throw new IllegalStateException();
        }
        Set<String> failed = new HashSet<String>();
        for (String url: urlPatterns) {
            if (url == null) {
                throw new IllegalArgumentException();
            }
            if (ctx.contextConfig.servletMapping.get(url) != null) {
                failed.add(url);
            } else {
                ctx.contextConfig.servletMapping.put(url, getServletName());
                ctx.addMapping(url, this);
            }
        }
        return failed;
    }

    public boolean setInitParameter(String name, String value)
            throws IllegalArgumentException, IllegalStateException {
        return ServletContextImpl.setInitParameter(ctx, initParams, 
                name, value);
    }

    public Set<String> setInitParameters(Map<String, String> initParameters)
            throws IllegalArgumentException, IllegalStateException {
        return ServletContextImpl.setInitParameters(ctx, initParams, 
                initParameters);
    }

    public void setServletClass(Class<? extends Servlet> servletClass2) {
        servletClass = servletClass2;
    }

    @Override
    public void service(HttpRequest httpReq, HttpResponse httpRes)
            throws IOException {
        
        HttpChannel client = httpReq.getHttpChannel();
        ServletRequestImpl req = TomcatLite.getFacade(client.getRequest());
        ServletResponseImpl res = req.getResponse();
        
        // TODO
    }
    
    /** Coyote / mapper adapter. Result of the mapper.
     *  
     *  This replaces the valve chain, the path is: 
     *    1. coyote calls mapper -> result Adapter 
     *    2. service is called. Additional filters are set on the wrapper. 
     * @param mapRes 
     */
    void serviceServlet(ServletContextImpl ctx, 
            ServletRequestImpl req, 
            ServletResponseImpl res,
            ServletConfigImpl servletConfig, 
            MappingData mapRes) 
                throws IOException {
        
        requestCount.incrementAndGet();
        Servlet servlet = null;
        long t0 = System.currentTimeMillis();
        
        try {
            if (servletConfig.isUnavailable()) {
                handleUnavailable(res, servletConfig);
                return;
            }
            try {
                servlet = servletConfig.allocate();
            } catch(ServletException ex) {
                handleUnavailable(res, servletConfig);
                return;
            }
            WebappFilterMapper filterMap = ctx.getFilterMapper();
            FilterChainImpl chain = 
                filterMap.createFilterChain(req, servletConfig, servlet);
            
            try {
                if (chain == null) {
                    if (servlet != null) {
                        servlet.service(req, res);
                    } else {
                        System.err.println("No servlet " + req.getRequestURI());
                        res.sendError(404);
                    }
                } else {
                    chain.doFilter(req, res);
                }
            } catch(UnavailableException ex) {
                servletConfig.unavailable(ex);
                handleUnavailable(res, servletConfig);
                return;
            }
            
            // servlet completed without exception. Check status
            int status = res.getStatus();
            if (status != 200 && !res.isCommitted()) {
                String statusPage = ctx.findStatusPage(status);

                if (statusPage != null) {
                    ctx.handleStatusPage(req, res, status, statusPage);
                } else {
                    // Send a default message body.
                    // TODO: maybe other mechanism to customize default.
                    res.defaultStatusPage(status, res.getMessage());
                }
            }
            
        } catch (Throwable t) {
            errorCount.incrementAndGet();
            ctx.handleError(req, res, t);
        } finally {
            int time = (int) (System.currentTimeMillis() - t0);
            if (time > maxTime.get()) {
                maxTime.set(time);
            }
            processingTime.addAndGet(time);
            if (servlet != null) { // single-thread servlet
                servletConfig.deallocate(servlet);
            }
        }
    }

    private void handleUnavailable(ServletResponseImpl response, 
            ServletConfigImpl servletConfig) 
            throws IOException {
        long available = servletConfig.getAvailable();
        if ((available > 0L) && (available < Long.MAX_VALUE))
            response.setDateHeader("Retry-After", available);
        //  TODO: handle via error pages !
        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, 
            "Service unavailable");
    }

    
}
