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
package org.apache.coyote.servlet;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeEvent;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.FilterRegistration.Dynamic;
import javax.servlet.descriptor.JspConfigDescriptor;

import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.servlets.session.UserSessionManager;
import org.apache.tomcat.integration.ObjectManager;
import org.apache.tomcat.servlets.config.ConfigLoader;
import org.apache.tomcat.servlets.config.ServletContextConfig;
import org.apache.tomcat.servlets.config.ServletContextConfig.FilterData;
import org.apache.tomcat.servlets.config.ServletContextConfig.FilterMappingData;
import org.apache.tomcat.servlets.config.ServletContextConfig.ServletData;
import org.apache.tomcat.servlets.config.deploy.WarDeploy;
import org.apache.tomcat.servlets.util.Enumerator;
import org.apache.tomcat.servlets.util.RequestUtil;
import org.apache.tomcat.servlets.util.UrlUtils;
import org.apache.tomcat.util.http.MimeMap;


/**
 * Context - initialized from web.xml or using APIs.
 *
 * Initialization order:
 *
 *  - add all listeners
 *  - add all filters
 *  - add all servlets
 *
 *  - session parameters
 *  -
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @version $Revision$ $Date$
 */

public class ServletContextImpl implements ServletContext {

    /**
     * Empty collection to serve as the basis for empty enumerations.
     */
    private transient static final ArrayList empty = new ArrayList();

    transient Logger log;

    /**
     * Base path - the directory root of the webapp
     */
    protected String basePath = null;

    protected String contextPath;

    // All config from web.xml
    protected ServletContextConfig contextConfig = new ServletContextConfig();

    MimeMap contentTypes = new MimeMap();

    /**
     * The context attributes for this context.
     */
    protected transient Map<String, Object> attributes = new HashMap<String, Object>();

    /**
     * List of read only attributes for this context.
     * In catalina - used to protect workdir att. We trust the app, so no need
     * for extra complexity.
     */
    //protected transient HashMap readOnlyAttributes = new HashMap();

    protected transient ArrayList<EventListener> lifecycleListeners = new ArrayList();

    protected UserSessionManager manager;

    HashMap<String, FilterConfigImpl> filters = new HashMap<String, FilterConfigImpl>();

    HashMap<String, ServletConfigImpl> servlets = new HashMap<String, ServletConfigImpl>();

    ArrayList<String> securityRoles = new ArrayList<String>();

    /** Mapper for filters.
     */
    protected WebappFilterMapper webappFilterMapper;

    /** Internal mapper for request dispatcher, must have all
     *  context mappings.
     */
    protected WebappServletMapper mapper;

    transient Locale2Charset charsetMapper = new Locale2Charset();

    transient TomcatLite facade;

    ObjectManager om;

    private String hostname;


    boolean initDone = false;

    boolean startDone = false;
    
    String jspcServlet = "org.apache.tomcat.servlets.jspc.JspcServlet";


    // ------------------------------------------------- ServletContext Methods
    public ServletContextImpl() {
    }

    public void setTomcat(TomcatLite facade) {
        this.facade = facade;
    }

    /**
     * Registry/framework interface associated with the context.
     * Also available as a context attribute.
     * @return
     */
    public ObjectManager getObjectManager() {
        if (om == null) {
            om = facade.getObjectManager();
        }
        return om;
    }

    public void setObjectManager(ObjectManager om) {
        this.om = om;
    }

    public Locale2Charset getCharsetMapper() {
        return charsetMapper;
    }

    /**
     * Set the context path, starting with "/" - "/" for ROOT
     * @param path
     */
    public void setContextPath(String path) {
        this.contextPath = path;
        log = Logger.getLogger("webapp" + path.replace('/', '.'));
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getHostname() {
        return hostname;
    }

    /** The directory where this app is based. May be null.
     *
     * @param basePath
     */
    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public ServletContextConfig getContextConfig() {
        return contextConfig;
    }

    /** The directory where this app is based.
     *
     * @param basePath
     */
    public String getBasePath() {
        return basePath;
    }

    public String getEncodedPath() {
        return null;
    }


    public boolean getCookies() {
        return false;
    }


    public ServletContext getServletContext() {
        return this;
    }

    public List<EventListener> getListeners() {
        return lifecycleListeners;
    }

    public void addListener(Class <? extends EventListener> listenerClass) {
        // implement me
    }

    public void addListener(String className) {
        // implement me
    }

    public <T extends EventListener> void addListener(T t) {
      lifecycleListeners.add(t);
    }


    public void removeListener(EventListener listener) {
      lifecycleListeners.remove(listener);
    }



    public Logger getLogger() {
        return log;
    }

    public long getUnloadDelay() {
        return 0;
    }

    public ServletConfigImpl getServletConfig(String jsp_servlet_name) {
        return (ServletConfigImpl)servlets.get(jsp_servlet_name);
    }

    public Map getServletConfigs() {
        return servlets;
    }

    /**
     *  Add a servlet to the context.
     *  Called from processWebAppData()
     *
     * @param servletConfig
     */
    public void addServletConfig(ServletConfigImpl servletConfig) {
        servlets.put(servletConfig.getServletName(), servletConfig);
    }

    public boolean getPrivileged() {
        return false;
    }


    public Map getFilters() {
        return filters;
    }


    protected boolean getCrossContext() {
        return true;
    }

    public void addMimeType(String ext, String type) {
        contentTypes.addContentType(ext, type);
    }

    public WebappServletMapper getMapper() {
        if (mapper == null) {
            Object customMapper = getObjectManager().get(WebappServletMapper.class);
            if (customMapper == null) {
                mapper = new WebappServletMapper();
            } else {
                mapper = (WebappServletMapper) customMapper;
            }
            mapper.setServletContext(this);
        }

        return mapper;
    }

    public WebappFilterMapper getFilterMapper() {
        if (webappFilterMapper == null) {
            Object customMapper = getObjectManager().get(WebappFilterMapper.class);
            if (customMapper == null) {
                webappFilterMapper = new WebappFilterMapper();
            } else {
                webappFilterMapper = (WebappFilterMapper) customMapper;
            }
            webappFilterMapper.setServletContext(this);
        }

        return webappFilterMapper ;
    }

    public FilterConfigImpl getFilter(String name) {
        return (FilterConfigImpl)filters.get(name);
    }

    /**
     * Return the value of the specified context attribute, if any;
     * otherwise return <code>null</code>.
     *
     * @param name Name of the context attribute to return
     */
    public Object getAttribute(String name) {
        if ("ObjectManager".equals(name)) {
            return om;
        }
        if ("context-listeners".equals(name)) {
            return lifecycleListeners;
        }
        return (attributes.get(name));
    }

    /**
     * Return an enumeration of the names of the context attributes
     * associated with this context.
     */
    public Enumeration getAttributeNames() {
        return new Enumerator(attributes.keySet(), true);
    }


    public void addSecurityRole(String role) {
        securityRoles.add(role);
    }

    public List getSecurityRoles() {
        return securityRoles;
    }

    /**
     * Return a <code>ServletContext</code> object that corresponds to a
     * specified URI on the server.  This method allows servlets to gain
     * access to the context for various parts of the server, and as needed
     * obtain <code>RequestDispatcher</code> objects or resources from the
     * context.  The given path must be absolute (beginning with a "/"),
     * and is interpreted based on our virtual host's document root.
     *
     * @param uri Absolute URI of a resource on the server
     */
    public ServletContext getContext(String uri) {
        // TODO: support real uri ( http://host/path )
        // Validate the format of the specified argument
        if ((uri == null) || (!uri.startsWith("/")))
            return (null);

        ServletContextImpl child = null;
        try {
            child = facade.getContext(this, uri);
        } catch (IOException e) {
        } catch (ServletException e) {
        }

        if (child == null)
            return (null);

        if (this.getCrossContext()) {
            // If crossContext is enabled, can always return the context
            return child.getServletContext();
        } else if (child == this) {
            // Can still return the current context
            return this.getServletContext();
        } else {
            // Nothing to return
            return (null);
        }
    }


    /**
     * Return the main path associated with this context.
     */
    public String getContextPath() {
        return contextPath;
    }


    /**
     * Return the value of the specified initialization parameter, or
     * <code>null</code> if this parameter does not exist.
     *
     * @param name Name of the initialization parameter to retrieve
     */
    public String getInitParameter(final String name) {
        return ((String) contextConfig.contextParam.get(name));
    }


    /**
     * Return the names of the context's initialization parameters, or an
     * empty enumeration if the context has no initialization parameters.
     */
    public Enumeration getInitParameterNames() {
        return (new Enumerator(contextConfig.contextParam.keySet()));
    }

    public void setContextParams(Map newParams) {
      contextConfig.contextParam = (HashMap) newParams;
    }

    /**
     * Return the major version of the Java Servlet API that we implement.
     */
    public int getMajorVersion() {
        return 3;
    }


    /**
     * Return the minor version of the Java Servlet API that we implement.
     */
    public int getMinorVersion() {
        return 0;
    }


    /**
     * Return the MIME type of the specified file, or <code>null</code> if
     * the MIME type cannot be determined.
     *
     * @param file Filename for which to identify a MIME type
     */
    public String getMimeType(String file) {
        return contentTypes.getMimeType(file);
    }

    /**
     * Return the real path for a given virtual path, if possible; otherwise
     * return <code>null</code>.
     *
     * @param path The path to the desired resource
     */
    public String getRealPath(String path) {
        if (path == null) {
            return null;
        }

        File file = new File(basePath, path);
        return (file.getAbsolutePath());
    }

    /**
     * Return a <code>RequestDispatcher</code> object that acts as a
     * wrapper for the named servlet.
     *
     * @param name Name of the servlet for which a dispatcher is requested
     */
    public RequestDispatcher getNamedDispatcher(String name) {
        if (name == null) return null;
        ServletConfigImpl wrapper =
            (ServletConfigImpl) this.getServletConfig(name);
        if (wrapper == null) return null;

        return new RequestDispatcherImpl(wrapper, name);
    }


    /**
     * Return a <code>RequestDispatcher</code> instance that acts as a
     * wrapper for the resource at the given path.  The path must begin
     * with a "/" and is interpreted as relative to the current context root.
     *
     * @param path The path to the desired resource.
     */
    public RequestDispatcher getRequestDispatcher(String path) {
        if (path == null) return null;

        if (!path.startsWith("/"))
            throw new IllegalArgumentException(path);

        path = UrlUtils.normalize(path);
        if (path == null)  return (null);


        return new RequestDispatcherImpl(this, path);
    }

    public RequestDispatcher getRequestDispatcher(String path,
                                                  int type,
                                                  String dispatcherPath) {
        RequestDispatcher dispatcher = getRequestDispatcher(path);
        //((RequestDispatcherImpl)dispatcher);
        return dispatcher;
    }

    ThreadLocal requestDispatcherStack = new ThreadLocal();

    private String classPath;
    
    protected ClassLoader classLoader;

//    protected RequestDispatcherImpl getRequestDispatcher() {
//        ArrayList/*<RequestDispatcherImpl>*/ list =
//            (ArrayList)requestDispatcherStack.get();
//        if (list == null) {
//            list = new ArrayList();
//            requestDispatcherStack.set(list);
//        }
//
//
//        return null;
//    }

    public void resetDispatcherStack() {

    }

    /**
     * Return the URL to the resource that is mapped to a specified path.
     * The path must begin with a "/" and is interpreted as relative to the
     * current context root.
     *
     * @param path The path to the desired resource
     *
     * @exception MalformedURLException if the path is not given
     *  in the correct form
     */
    public URL getResource(String path)
        throws MalformedURLException {

        if (path == null || !path.startsWith("/")) {
            throw new MalformedURLException("getResource() " + path);
        }

        path = UrlUtils.normalize(path);
        if (path == null)
            return (null);

        String libPath = "/WEB-INF/lib/";
        if ((path.startsWith(libPath)) && (path.endsWith(".jar"))) {
            File jarFile = null;
            jarFile = new File(basePath, path);
            if (jarFile.exists()) {
                return jarFile.toURL();
            } else {
                return null;
            }
        } else {
            File resFile = new File(basePath + path);
            if (resFile.exists()) {
                return resFile.toURL();
            }
        }

        return (null);

    }

    /**
     * Return the requested resource as an <code>InputStream</code>.  The
     * path must be specified according to the rules described under
     * <code>getResource</code>.  If no such resource can be identified,
     * return <code>null</code>.
     *
     * @param path The path to the desired resource.
     */
    public InputStream getResourceAsStream(String path) {

        path = UrlUtils.normalize(path);
        if (path == null)
            return (null);

        File resFile = new File(basePath + path);
        if (!resFile.exists())
            return null;

        try {
            return new FileInputStream(resFile);
        } catch (FileNotFoundException e) {
            return null;
        }

    }


    /**
     * Return a Set containing the resource paths of resources member of the
     * specified collection. Each path will be a String starting with
     * a "/" character. The returned set is immutable.
     *
     * @param path Collection path
     */
    public Set getResourcePaths(String path) {

        // Validate the path argument
        if (path == null) {
            return null;
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("getResourcePaths() " + path);
        }

        path = UrlUtils.normalize(path);
        if (path == null)
            return (null);

        File f = new File(basePath + path);
        File[] files = f.listFiles();
        if (files == null) return null;
        if (!path.endsWith("/")) {
          path = path + "/";
        }

        HashSet result = new HashSet();
        for (int i=0; i < files.length; i++) {
            if (files[i].isDirectory() ) {
                result.add(path + files[i].getName() + "/");
            } else {
                result.add(path + files[i].getName());
            }
        }
        return result;
    }



    /**
     * Return the name and version of the servlet container.
     */
    public String getServerInfo() {
        return "Apache Tomcat Lite";
    }

    /**
     * @deprecated As of Java Servlet API 2.1, with no direct replacement.
     */
    public Servlet getServlet(String name) {
        return (null);
    }


    /**
     * Return the display name of this web application.
     */
    public String getServletContextName() {
        return contextConfig.displayName;
    }


    /**
     * @deprecated As of Java Servlet API 2.1, with no direct replacement.
     */
    public Enumeration getServletNames() {
        return (new Enumerator(empty));
    }


    /**
     * @deprecated As of Java Servlet API 2.1, with no direct replacement.
     */
    public Enumeration getServlets() {
        return (new Enumerator(empty));
    }


    /**
     * Writes the specified message to a servlet log file.
     *
     * @param message Message to be written
     */
    public void log(String message) {
        this.getLogger().info(message);
    }


    /**
     * Writes the specified exception and message to a servlet log file.
     *
     * @param exception Exception to be reported
     * @param message Message to be written
     *
     * @deprecated As of Java Servlet API 2.1, use
     *  <code>log(String, Throwable)</code> instead
     */
    public void log(Exception exception, String message) {
        this.getLogger().log(Level.INFO, message, exception);
    }


    /**
     * Writes the specified message and exception to a servlet log file.
     *
     * @param message Message to be written
     * @param throwable Exception to be reported
     */
    public void log(String message, Throwable throwable) {
        this.getLogger().log(Level.INFO, message, throwable);
    }

    /**
     * Remove the context attribute with the specified name, if any.
     *
     * @param name Name of the context attribute to be removed
     */
    public void removeAttribute(String name) {

        Object value = null;
        boolean found = false;

        // Remove the specified attribute
        // Check for read only attribute
        found = attributes.containsKey(name);
        if (found) {
            value = attributes.get(name);
            attributes.remove(name);
        } else {
            return;
        }

        // Notify interested application event listeners
        List listeners = this.getListeners();
        if (listeners.size() == 0)
            return;
        ServletContextAttributeEvent event = null;
        for (int i = 0; i < listeners.size(); i++) {
            if (!(listeners.get(i) instanceof ServletContextAttributeListener))
                continue;
            ServletContextAttributeListener listener =
                (ServletContextAttributeListener) listeners.get(i);
            try {
                if (event == null) {
                    event = new ServletContextAttributeEvent(this.getServletContext(),
                            name, value);

                }
                listener.attributeRemoved(event);
            } catch (Throwable t) {
                // FIXME - should we do anything besides log these?
                log("ServletContextAttributeListener", t);
            }
        }
    }


    /**
     * Bind the specified value with the specified context attribute name,
     * replacing any existing value for that name.
     *
     * @param name Attribute name to be bound
     * @param value New attribute value to be bound
     */
    public void setAttribute(String name, Object value) {
        // Name cannot be null
        if (name == null)
            throw new IllegalArgumentException
                ("name == null");

        // Null value is the same as removeAttribute()
        if (value == null) {
            removeAttribute(name);
            return;
        }

        Object oldValue = null;
        boolean replaced = false;

        // Add or replace the specified attribute
        synchronized (attributes) {
            // Check for read only attribute
            oldValue = attributes.get(name);
            if (oldValue != null)
                replaced = true;
            attributes.put(name, value);
        }

        // Notify interested application event listeners
        List listeners = this.getListeners();
        if (listeners.size() == 0)
            return;
        ServletContextAttributeEvent event = null;
        for (int i = 0; i < listeners.size(); i++) {
            if (!(listeners.get(i) instanceof ServletContextAttributeListener))
                continue;
            ServletContextAttributeListener listener =
                (ServletContextAttributeListener) listeners.get(i);
            try {
                if (event == null) {
                    if (replaced)
                        event =
                            new ServletContextAttributeEvent(this.getServletContext(),
                                                             name, oldValue);
                    else
                        event =
                            new ServletContextAttributeEvent(this.getServletContext(),
                                                             name, value);

                }
                if (replaced) {
                    listener.attributeReplaced(event);
                } else {
                    listener.attributeAdded(event);
                }
            } catch (Throwable t) {
                // FIXME - should we do anything besides log these?
                log("ServletContextAttributeListener error", t);
            }
        }

    }

    /**
     * Clear all application-created attributes.
     */
    void clearAttributes() {
        // Create list of attributes to be removed
        ArrayList list = new ArrayList();
        synchronized (attributes) {
            Iterator iter = attributes.keySet().iterator();
            while (iter.hasNext()) {
                list.add(iter.next());
            }
        }

        // Remove application originated attributes
        // (read only attributes will be left in place)
        Iterator keys = list.iterator();
        while (keys.hasNext()) {
            String key = (String) keys.next();
            removeAttribute(key);
        }
    }

    public void initFilters() throws ServletException {
        Iterator fI = getFilters().values().iterator();
        while (fI.hasNext()) {
            FilterConfigImpl fc = (FilterConfigImpl)fI.next();
            try {
                fc.getFilter(); // will triger init()
            } catch (Throwable e) {
                log.log(Level.WARNING, getContextPath() + " Filter.init() " +
                        fc.getFilterName(), e);
            }

        }
    }

    public void initServlets() throws ServletException {
        Iterator fI = getServletConfigs().values().iterator();
        Map/*<Integer, List<ServletConfigImpl>>*/ onStartup =
            new TreeMap/*<Integer, List<ServletConfigImpl>>*/();
        while (fI.hasNext()) {
            ServletConfigImpl fc = (ServletConfigImpl)fI.next();
            if (fc.getLoadOnStartup() > 0 ) {
                Integer i = new Integer(fc.getLoadOnStartup());
                List/*<ServletConfigImpl>*/ old = (List)onStartup.get(i);
                if (old == null) {
                    old = new ArrayList/*<ServletConfigImpl>*/();
                    onStartup.put(i, old);
                }
                old.add(fc);
            }
        }
        Iterator keys = onStartup.keySet().iterator();
        while (keys.hasNext()) {
            Integer key = (Integer)keys.next();
            List/*<ServletConfigImpl>*/ servlets = (List)onStartup.get(key);
            Iterator servletsI = servlets.iterator();
            while (servletsI.hasNext()) {
                ServletConfigImpl fc = (ServletConfigImpl) servletsI.next();
                try {
                    fc.loadServlet();
                } catch (Throwable e) {
                    log.log(Level.WARNING, "Error initializing  " + fc.getServletName(), e);
                }
            }
        }
    }

    public void initListeners() throws ServletException {
        Iterator fI = contextConfig.listenerClass.iterator();
        while (fI.hasNext()) {
            String listenerClass = (String)fI.next();
            try {
                Object l =
                    getClassLoader().loadClass(listenerClass).newInstance();
                lifecycleListeners.add((EventListener) l);
            } catch (Throwable e) {
                log.log(Level.WARNING, "Error initializing listener " + listenerClass, e);
            }
        }
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void addMapping(String path, String name) {
      ServletConfigImpl wrapper = getServletConfig(name);
      addMapping(path, wrapper);
    }

    public void addMapping(String path, ServletConfig wrapper) {
        getMapper().addWrapper(getMapper().contextMapElement, path, wrapper);
    }



    public void setWelcomeFiles(String[] name) {
      getMapper().contextMapElement.welcomeResources = name;
    }

    public String[] getWelcomeFiles() {
      return getMapper().contextMapElement.welcomeResources;
    }

    public void setSessionTimeout(int to) {
      getManager().setSessionTimeout(to);
    }

    /**
     * Initialize the context from the parsed config.
     *
     * Note that WebAppData is serializable.
     */
    public void processWebAppData(ServletContextConfig d) throws ServletException {
        this.contextConfig = d;

        for (String k: d.mimeMapping.keySet()) {
            addMimeType(k, d.mimeMapping.get(k));
        }

        String[] wFiles = (String[])d.welcomeFileList.toArray(new String[0]);
        if (wFiles.length == 0) {
            wFiles = new String[] {"index.html" };
        }
        if (basePath != null) {
          getMapper().contextMapElement.resources = new File(getBasePath());
        }
        setWelcomeFiles(wFiles);

        Iterator i2 = d.filters.values().iterator();
        while (i2.hasNext()) {
            FilterData fd = (FilterData)i2.next();
            addFilter(fd.name, fd.className, fd.initParams);
        }

        Iterator i3 = d.servlets.values().iterator();
        while (i3.hasNext()) {
            ServletData sd = (ServletData) i3.next();
            // jsp-file
            if (sd.className == null) {
                if (sd.jspFile == null) {
                    log.log(Level.WARNING, "Missing servlet class for " + sd.name);
                    continue;
                }
            }

            ServletConfigImpl sw =
              new ServletConfigImpl(this, sd.name, sd.className);
            sw.setConfig(sd.initParams);
            sw.setJspFile(sd.jspFile);
            sw.setLoadOnStartup(sd.loadOnStartup);
            //sw.setRunAs(sd.runAs);
            sw.setSecurityRoleRef(sd.securityRoleRef);

            addServletConfig(sw);
        }

        for (String k: d.servletMapping.keySet()) {
            addMapping(k, d.servletMapping.get(k));
        }

        Iterator i5 = d.filterMappings.iterator();
        while (i5.hasNext()) {
            FilterMappingData k = (FilterMappingData) i5.next();
            String[] disp = new String[k.dispatcher.size()];
            if (k.urlPattern != null) {
              addFilterMapping(k.urlPattern,
                  k.filterName,
                  (String[])k.dispatcher.toArray(disp));
            }
            if (k.servletName != null) {
              addFilterServletMapping(k.servletName,
                  k.filterName,
                  (String[])k.dispatcher.toArray(disp));
            }
         }

        for (String n: d.localeEncodingMapping.keySet()) {
            getCharsetMapper().addCharsetMapping(n,
                    d.localeEncodingMapping.get(n));
        }
    }

    public void addServlet(String servletName, String servletClass,
                           String jspFile, Map params) {
      ServletConfigImpl sc = new ServletConfigImpl(this, servletName,
          servletClass);
      sc.setJspFile(jspFile);
      sc.setConfig(params);
      addServletConfig(sc);
    }

    @Override
    public javax.servlet.ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet) {
      ServletConfigImpl sc = new ServletConfigImpl(this, servletName, null);
      sc.setServlet(servlet);
      addServletConfig(sc);
      return sc.getDynamic();
    }

    public void addServletSec(String serlvetName, String runAs, Map roles) {
      // TODO
    }



    public void addFilterMapping(String path, String filterName,
                                 String[] dispatcher) {
      getFilterMapper().addMapping(filterName,
          path, null, dispatcher, true);

    }

    public void addFilterServletMapping(String servlet,
                                        String filterName,
                                        String[] dispatcher) {
      getFilterMapper().addMapping(filterName,
          null, servlet,
          dispatcher, true);
    }

    /**
     * Called from TomcatLite.init(), required before start.
     *
     * Will initialize defaults and load web.xml unless webAppData is
     * already set and recent. No other processing is done except reading
     * the config - you can add or alter it before start() is called.
     *
     * @throws ServletException
     */
    public void init() throws ServletException {
        if (initDone) {
            return;
        }
        initDone = true;
        // Load global init params from the facade
        initEngineDefaults();

        initTempDir();


        // Merge in web.xml - or other config source ( programmatic, etc )
        ConfigLoader cfg = new WarDeploy(); 
        contextConfig = cfg.loadConfig(getBasePath());
        
        processWebAppData(contextConfig);

        // if not defined yet:
        addDefaultServlets();
    }


    protected void initTempDir() throws ServletException {
        // We need a base path - at least for temp files, req. by spec
        if (basePath == null) {
            basePath = ("/".equals(contextPath)) ?
                    facade.getWork().getAbsolutePath() + "/ROOT" :
                    facade.getWork().getAbsolutePath() + contextPath;
        }

        File f = new File(basePath + "/WEB-INF/tmp");
        f.mkdirs();
        setAttribute("javax.servlet.context.tempdir", f);
    }

    /**
     * Static file handler ( default )
     * *.jsp support
     *
     */
    protected void addDefaultServlets() throws ServletException {
        if (servlets.get("default") == null) {
            ServletConfigImpl fileS = new ServletConfigImpl(this,
                    "default", null);
            addServletConfig(fileS);
            addMapping("/", fileS);
        }

        // *.jsp support
        if (servlets.get("jspwildcard") == null) {
            ServletConfigImpl fileS = new ServletConfigImpl(this,
                        "jspwildcard", null);
            fileS.initParams.put("mapper", JspLoader.class.getName());            
            addServletConfig(fileS);
            addMapping("*.jsp", fileS);
        }
        
        ServletConfigImpl jspcS = new ServletConfigImpl(this,
                "jspc", jspcServlet);
        addServletConfig(jspcS);
    }

    protected void initEngineDefaults() throws ServletException {

        // TODO: make this customizable, avoid loading it on startup
        // Set the class name as default in the addon support
        for (String sname: facade.ctxDefaultInitParam.keySet()) {
            String path = facade.ctxDefaultInitParam.get(sname);
            contextConfig.contextParam.put(sname, path);
        }

        for (String sname: facade.preloadServlets.keySet()) {
            String sclass = facade.preloadServlets.get(sname);
            ServletConfigImpl fileS = new ServletConfigImpl(this, sname, sclass);
            addServletConfig(fileS);
        }

        for (String sname: facade.preloadMappings.keySet()) {
            String path = facade.preloadMappings.get(sname);
            ServletConfigImpl servletConfig = getServletConfig(sname);
            addMapping(path, servletConfig);
        }

        // JSP support 
        setAttribute(InstanceManager.class.getName(),
                new LiteInstanceManager(getObjectManager()));
        
    }

    private void addClasspathLib(ArrayList res, File directory) {
        
        if (!directory.isDirectory() || !directory.exists()
                || !directory.canRead()) {
            return;
        }
        
        File[] jars = directory.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
              return name.endsWith(".jar");
            }
          });
        
        for (int j = 0; j < jars.length; j++) {
            try {
                URL url = jars[j].toURL();
                res.add(url);
            } catch (MalformedURLException e) {
            }
        }
    }
    
    private void addClasspathDir(ArrayList res, File classesDir) {
        
        if (classesDir.isDirectory() && classesDir.exists() &&
                classesDir.canRead()) {
            try {
                URL url = classesDir.toURL();
                res.add(url);
            } catch (MalformedURLException e) {
            }
        }
    }
    public String getClassPath() {
        return classPath;
    }

    public void start() throws ServletException {
        if (startDone) {
            return;
        }
        String base = getBasePath();

        ArrayList urls = new ArrayList();
        
        addClasspathDir(urls, new File(base + "/WEB-INF/classes"));
        addClasspathDir(urls, new File(base + "/WEB-INF/tmp"));
        addClasspathLib(urls, new File(base + "/WEB-INF/lib"));
        
        URL[] urlsA = new URL[urls.size()];
        urls.toArray(urlsA);

        URLClassLoader parentLoader =
            getEngine().getContextParentLoader();

        // create a class loader.
        // TODO: reimplement special 'deploy' dirs

        /*
          Repository ctxRepo = new Repository();
          ctxRepo.setParentClassLoader(parentLoader);
          ctxRepo.addURL(urlsA);
          repository = ctxRepo;
        */

        StringBuilder cp = new StringBuilder();
        
        for (URL cpUrl : urlsA) {
            cp.append(":").append(cpUrl.getFile());
        }
        classPath = cp.toString();
        
        classLoader = new URLClassLoader(urlsA, parentLoader);

        // JMX should know about us ( TODO: is it too early ? )
        facade.notifyAdd(this);

        initListeners();

        List listeners = this.getListeners();
        ServletContextEvent event = null;
        for (int i = 0; i < listeners.size(); i++) {
            if (!(listeners.get(i) instanceof ServletContextListener))
                continue;
            ServletContextListener listener =
                (ServletContextListener) listeners.get(i);
            if (event == null) {
                event = new ServletContextEvent(this);
            }
            try {
                // May add servlets/filters
                listener.contextInitialized(event);
            } catch (Throwable t) {
                log.log(Level.WARNING, "Context.init() contextInitialized() error:", t);
            }
        }


        initFilters();
        initServlets();

        startDone = true;
    }

    public UserSessionManager getManager() {
      if (manager == null) {
          manager = (UserSessionManager) getObjectManager().get(
                  UserSessionManager.class);
          manager.setContext(this);
          if (contextConfig.sessionTimeout > 0 ) {
              manager.setSessionTimeout(contextConfig.sessionTimeout);
          }
      }
      return manager;
    }


    // TODO: configurable ? init-params
    public String getSessionCookieName() {
        return "JSESSIONID";
    }



    public void destroy() throws ServletException {
        // destroy filters
        Iterator fI = filters.values().iterator();
        while(fI.hasNext()) {
            FilterConfigImpl fc = (FilterConfigImpl) fI.next();
            try {
                fc.getFilter().destroy();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // destroy servlets
        fI = servlets.values().iterator();
        while(fI.hasNext()) {
            ServletConfigImpl fc = (ServletConfigImpl) fI.next();
            try {
                fc.unload();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public TomcatLite getEngine() {
        return facade;
    }

    public String findStatusPage(int status) {
        if (contextConfig.errorPageCode.size() == 0) {
            return null;
        }
        if (status == 200) {
            return null;
        }

        return (String) contextConfig.errorPageCode.get(Integer.toString(status));
    }

    public void handleStatusPage(ServletRequestImpl req,
                                 ServletResponseImpl res,
                                 int status,
                                 String statusPage) {
       String message = RequestUtil.filter(res.getMessage());
       if (message == null)
           message = "";
       setErrorAttributes(req, status, message);
       dispatchError(req, res, statusPage);
   }

   protected void setErrorAttributes(ServletRequestImpl req,
                                   int status,
                                   String message) {
       req.setAttribute("javax.servlet.error.status_code",
               new Integer(status));
       if (req.getWrapper() != null) {
           req.setAttribute("javax.servlet.error.servlet_name",
                   req.getWrapper().servletName);
       }
       req.setAttribute("javax.servlet.error.request_uri",
               req.getRequestURI());
       req.setAttribute("javax.servlet.error.message",
               message);

   }

   public void handleError(ServletRequestImpl req,
                           ServletResponseImpl res,
                           Throwable t) {
       Throwable realError = t;
       if (realError instanceof ServletException) {
           realError = ((ServletException) realError).getRootCause();
           if (realError == null) {
               realError = t;
           }
       }
      //if (realError instanceof ClientAbortException ) {

       String errorPage = findErrorPage(t);
       if ((errorPage == null) && (realError != t)) {
           errorPage = findErrorPage(realError);
       }

       if (errorPage != null) {
           setErrorAttributes(req, 500, t.getMessage());
           req.setAttribute("javax.servlet.error.exception", realError);
           req.setAttribute("javax.servlet.error.exception_type",
                   realError.getClass());
           dispatchError(req, res, errorPage);
       } else {
           log("Unhandled error", t);
           if (t instanceof ServletException &&
                   ((ServletException)t).getRootCause() != null) {
               log("RootCause:", ((ServletException)t).getRootCause());
           }
           if (res.getStatus() < 500) {
               res.setStatus(500);
           }
       }
   }

   protected void dispatchError(ServletRequestImpl req,
                              ServletResponseImpl res,
                              String errorPage) {
       RequestDispatcher rd =
           getRequestDispatcher(errorPage);
       try {
           // will clean up the buffer
           rd.forward(req, res);
           return; // handled
       } catch (ServletException e) {
           // TODO
       } catch (IOException e) {
           // TODO
       }
   }

   protected String findErrorPage(Throwable exception) {
       if (contextConfig.errorPageException.size() == 0) {
           return null;
       }
       if (exception == null)
           return (null);
       Class clazz = exception.getClass();
       String name = clazz.getName();
       while (!Object.class.equals(clazz)) {
           String page = (String)contextConfig.errorPageException.get(name);
           if (page != null)
               return (page);
           clazz = clazz.getSuperclass();
           if (clazz == null)
               break;
           name = clazz.getName();
       }
       return (null);

   }


   @Override
   public EnumSet<SessionTrackingMode> getDefaultSessionTrackingModes() {
       return null;
   }


   @Override
   public EnumSet<SessionTrackingMode> getEffectiveSessionTrackingModes() {
       return null;
   }


   @Override
   public SessionCookieConfig getSessionCookieConfig() {
       return null;
   }


   @Override
   public void setSessionTrackingModes(EnumSet<SessionTrackingMode> sessionTrackingModes) {
   }


   public void addFilter(String filterName, String filterClass,
                         Map params) {
       FilterConfigImpl fc = new FilterConfigImpl(this);
       fc.setData(filterName, filterClass, params);
       filters.put(filterName, fc);
   }


   @Override
   public Dynamic addFilter(String filterName, String className) {
       FilterConfigImpl fc = new FilterConfigImpl(this);
       fc.setData(filterName, className, new HashMap());
       filters.put(filterName, fc);
       return fc.getDynamic();
   }


   @Override
   public Dynamic addFilter(String filterName, Filter filter) {
       FilterConfigImpl fc = new FilterConfigImpl(this);
       fc.setData(filterName, null, new HashMap());
       fc.setFilter(filter);
       filters.put(filterName, fc);
       return fc.getDynamic();
   }


   @Override
   public Dynamic addFilter(String filterName, Class<? extends Filter> filterClass) {
       FilterConfigImpl fc = new FilterConfigImpl(this);
       fc.setData(filterName, null, new HashMap());
       fc.setFilterClass(filterClass);
       filters.put(filterName, fc);
       return fc.getDynamic();
   }


   @Override
   public javax.servlet.ServletRegistration.Dynamic addServlet(String servletName,
                                                        String className) {
       ServletConfigImpl sc = new ServletConfigImpl(this, servletName, className);
       addServletConfig(sc);
       return sc.getDynamic();
   }


   @Override
   public javax.servlet.ServletRegistration.Dynamic addServlet(String servletName,
                                                        Class<? extends Servlet> servletClass) {
       ServletConfigImpl sc = new ServletConfigImpl(this, servletName, servletClass.getName());
       sc.setServletClass(servletClass);
       addServletConfig(sc);
       return sc.getDynamic();
   }

   // That's tricky - this filter will have no name. We need to generate one
   // because our code relies on names.
   AtomicInteger autoName = new AtomicInteger();


   @Override
   public <T extends Filter> T createFilter(Class<T> c) throws ServletException {
       FilterConfigImpl fc = new FilterConfigImpl(this);
       String filterName = "_tomcat_auto_filter_" + autoName.incrementAndGet();
       fc.setData(filterName, null, new HashMap());
       fc.setFilterClass(c);
       filters.put(filterName, fc);

       try {
           return (T) fc.createFilter();
       } catch (ClassCastException e) {
           throw new ServletException(e);
       } catch (ClassNotFoundException e) {
           throw new ServletException(e);
       } catch (IllegalAccessException e) {
           throw new ServletException(e);
       } catch (InstantiationException e) {
           throw new ServletException(e);
       }
   }


   @Override
   public <T extends Servlet> T createServlet(Class<T> c) throws ServletException {
       String filterName = "_tomcat_auto_servlet_" + autoName.incrementAndGet();
       ServletConfigImpl fc = new ServletConfigImpl(this, filterName, null);
       fc.setServletClass(c);
       servlets.put(filterName, fc);

       try {
           return (T) fc.newInstance();
       } catch (ClassCastException e) {
           throw new ServletException(e);
       }
   }


   public FilterRegistration findFilterRegistration(String filterName) {
       return filters.get(filterName);
   }


   public ServletRegistration findServletRegistration(String servletName) {
       return servlets.get(servletName);
   }


   @Override
   public boolean setInitParameter(String name, String value) {
       HashMap<String, String> params = contextConfig.contextParam;
       return setInitParameter(this, params, name, value);
   }


   static Set<String> setInitParameters(ServletContextImpl ctx,
           Map<String, String> params,
           Map<String, String> initParameters)
           throws IllegalArgumentException, IllegalStateException {
       if (ctx.startDone) {
           throw new IllegalStateException();
       }
       Set<String> result = new HashSet<String>();
       for (String name: initParameters.keySet()) {
           String value = initParameters.get(name);
           if (name == null || value == null) {
               throw new IllegalArgumentException();
           }
           if (!setInitParameter(ctx, params, name, value)) {
               result.add(name);
           }
       }
       return result;
   }

   /**
    * true if the context initialization parameter with the given name and value was set successfully on this ServletContext, and false if it was not set because this ServletContext already contains a context initialization parameter with a matching name
    * Throws:
    * java.lang.IllegalStateException - if this ServletContext has already been initialized
    */
   static boolean setInitParameter(ServletContextImpl ctx, Map<String, String> params,
                                   String name, String value) {
       if (name == null || value == null) {
           throw new IllegalArgumentException();
       }
       if (ctx.startDone) {
           throw new IllegalStateException();
       }
       String oldValue = params.get(name);
       if (oldValue != null) {
           return false;
       } else {
           params.put(name, value);
           return true;
       }
   }

    public JspConfigDescriptor getJspConfigDescriptor() {
        // fix me - just here to compile
        return null;
    }



    public void declareRoles(String... roleNames) {
        // implement me
    }

    public <T extends EventListener> T createListener(Class<T> c) throws ServletException {
        // implement me
        return null;
    }

    public Collection<String> getMappings() {
        // implement me
        return null;
    }

    public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
        // implement me
        return null;
    }

    public FilterRegistration getFilterRegistration(String filterName) {
        // implement me
        return null;
    }

    public Map<String, ? extends ServletRegistration> getServletRegistrations() {
        // implement me
        return null;
    }

    public ServletRegistration getServletRegistration(String servletName) {
        // implement me
        return null;
    }

    public int getEffectiveMinorVersion() {
        // implement me
        return -1;
    }

    public int getEffectiveMajorVersion() {
        // implement me
        return -1;
    }

    private final class LiteInstanceManager implements InstanceManager {
        private ObjectManager om;

        public LiteInstanceManager(ObjectManager objectManager) {
            this.om = objectManager;
        }

        @Override
        public void destroyInstance(Object o)
                throws IllegalAccessException,
                InvocationTargetException {
        }

        @Override
        public Object newInstance(String className)
                throws IllegalAccessException,
                InvocationTargetException, NamingException,
                InstantiationException,
                ClassNotFoundException {
            return om.get(className);
        }

        @Override
        public Object newInstance(String fqcn,
                ClassLoader classLoader)
                throws IllegalAccessException,
                InvocationTargetException, NamingException,
                InstantiationException,
                ClassNotFoundException {
            return om.get(fqcn);
        }

        @Override
        public void newInstance(Object o)
                throws IllegalAccessException,
                InvocationTargetException, NamingException {
            om.bind(o.getClass().getName(), o);
        }
    }
    
}

