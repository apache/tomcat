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

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.NamingException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextAttributeEvent;
import jakarta.servlet.ServletContextAttributeListener;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.ServletRegistration.Dynamic;
import jakarta.servlet.ServletRequestAttributeListener;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.ServletSecurityElement;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.SessionTrackingMode;
import jakarta.servlet.annotation.ServletSecurity;
import jakarta.servlet.descriptor.JspConfigDescriptor;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionIdListener;
import jakarta.servlet.http.HttpSessionListener;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Service;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.mapper.MappingData;
import org.apache.catalina.util.Introspection;
import org.apache.catalina.util.ServerInfo;
import org.apache.catalina.util.URLEncoder;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.buf.UDecoder;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.http.RequestUtil;
import org.apache.tomcat.util.res.StringManager;


/**
 * Standard implementation of <code>ServletContext</code> that represents a web application's execution environment. An
 * instance of this class is associated with each instance of <code>StandardContext</code>.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public class ApplicationContext implements ServletContext {

    // ----------------------------------------------------------- Constructors

    /**
     * Construct a new instance of this class, associated with the specified Context instance.
     *
     * @param context The associated Context instance
     */
    public ApplicationContext(StandardContext context) {
        super();
        this.context = context;
        this.service = ((Engine) context.getParent().getParent()).getService();
        this.sessionCookieConfig = new ApplicationSessionCookieConfig(context);

        // Populate session tracking modes
        populateSessionTrackingModes();
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The context attributes for this context.
     */
    protected Map<String,Object> attributes = new ConcurrentHashMap<>();


    /**
     * List of read only attributes for this context.
     */
    private final Map<String,String> readOnlyAttributes = new ConcurrentHashMap<>();


    /**
     * The Context instance with which we are associated.
     */
    private final StandardContext context;


    /**
     * The Service instance with which we are associated.
     */
    private final Service service;


    /**
     * The facade around this object.
     */
    private final ServletContext facade = new ApplicationContextFacade(this);


    /**
     * The merged context initialization parameters for this Context.
     */
    private final Map<String,String> parameters = new ConcurrentHashMap<>();


    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(ApplicationContext.class);


    /**
     * Thread local data used during request dispatch.
     */
    private final ThreadLocal<DispatchData> dispatchData = new ThreadLocal<>();


    /**
     * Session Cookie config
     */
    private SessionCookieConfig sessionCookieConfig;

    /**
     * Session tracking modes
     */
    private Set<SessionTrackingMode> sessionTrackingModes = null;
    private Set<SessionTrackingMode> defaultSessionTrackingModes = null;
    private Set<SessionTrackingMode> supportedSessionTrackingModes = null;

    /**
     * Flag that indicates if a new {@link ServletContextListener} may be added to the application. Once the first
     * {@link ServletContextListener} is called, no more may be added.
     */
    private boolean newServletContextListenerAllowed = true;


    // ------------------------------------------------- ServletContext Methods

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }


    @Override
    public Enumeration<String> getAttributeNames() {
        Set<String> names = new HashSet<>(attributes.keySet());
        return Collections.enumeration(names);
    }


    @Override
    public ServletContext getContext(String uri) {

        // Validate the format of the specified argument
        if (uri == null || !uri.startsWith("/")) {
            return null;
        }

        Context child = null;
        try {
            // Look for an exact match
            Container host = context.getParent();
            child = (Context) host.findChild(uri);

            // Non-running contexts should be ignored.
            if (child != null && !child.getState().isAvailable()) {
                child = null;
            }

            // Remove any version information and use the mapper
            if (child == null) {
                int i = uri.indexOf("##");
                if (i > -1) {
                    uri = uri.substring(0, i);
                }
                // Note: This could be more efficient with a dedicated Mapper
                // method but such an implementation would require some
                // refactoring of the Mapper to avoid copy/paste of
                // existing code.
                MessageBytes hostMB = MessageBytes.newInstance();
                hostMB.setString(host.getName());

                MessageBytes pathMB = MessageBytes.newInstance();
                pathMB.setString(uri);

                MappingData mappingData = new MappingData();
                service.getMapper().map(hostMB, pathMB, null, mappingData);
                child = mappingData.context;
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            return null;
        }

        if (child == null) {
            return null;
        }

        if (context.getCrossContext()) {
            // If crossContext is enabled, can always return the context
            return child.getServletContext();
        } else if (child == context) {
            // Can still return the current context
            return context.getServletContext();
        } else {
            // Nothing to return
            return null;
        }
    }


    @Override
    public String getContextPath() {
        return context.getPath();
    }


    @Override
    public String getInitParameter(final String name) {
        // Special handling for XML settings as the context setting must
        // always override anything that might have been set by an application.
        if (Globals.JASPER_XML_VALIDATION_TLD_INIT_PARAM.equals(name) && context.getTldValidation()) {
            return "true";
        }
        if (Globals.JASPER_XML_BLOCK_EXTERNAL_INIT_PARAM.equals(name)) {
            if (!context.getXmlBlockExternal()) {
                // System admin has explicitly changed the default
                return "false";
            }
        }
        return parameters.get(name);
    }


    @Override
    public Enumeration<String> getInitParameterNames() {
        Set<String> names = new HashSet<>(parameters.keySet());
        // Special handling for XML settings as these attributes will always be
        // available if they have been set on the context
        if (context.getTldValidation()) {
            names.add(Globals.JASPER_XML_VALIDATION_TLD_INIT_PARAM);
        }
        if (!context.getXmlBlockExternal()) {
            names.add(Globals.JASPER_XML_BLOCK_EXTERNAL_INIT_PARAM);
        }
        return Collections.enumeration(names);
    }


    @Override
    public int getMajorVersion() {
        return Constants.MAJOR_VERSION;
    }


    @Override
    public int getMinorVersion() {
        return Constants.MINOR_VERSION;
    }


    @Override
    public String getMimeType(String file) {

        if (file == null) {
            return null;
        }
        int period = file.lastIndexOf('.');
        if (period < 0) {
            return null;
        }
        String extension = file.substring(period + 1);
        if (extension.length() < 1) {
            return null;
        }
        return context.findMimeMapping(extension);

    }


    @Override
    public RequestDispatcher getNamedDispatcher(String name) {

        // Validate the name argument
        if (name == null) {
            return null;
        }

        // Create and return a corresponding request dispatcher
        Wrapper wrapper = (Wrapper) context.findChild(name);
        if (wrapper == null) {
            return null;
        }

        return new ApplicationDispatcher(wrapper, null, null, null, null, null, name);

    }


    @Override
    public String getRealPath(String path) {
        String validatedPath = validateResourcePath(path, true);
        return context.getRealPath(validatedPath);
    }


    @Override
    public RequestDispatcher getRequestDispatcher(final String path) {

        // Validate the path argument
        if (path == null) {
            return null;
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException(sm.getString("applicationContext.requestDispatcher.iae", path));
        }

        // Same processing order as InputBuffer / CoyoteAdapter
        // First remove query string
        String uri;
        String queryString;
        int pos = path.indexOf('?');
        if (pos >= 0) {
            uri = path.substring(0, pos);
            queryString = path.substring(pos + 1);
        } else {
            uri = path;
            queryString = null;
        }

        // Remove path parameters
        String uriNoParams = stripPathParams(uri);

        // Then normalize
        String normalizedUri = RequestUtil.normalize(uriNoParams);
        if (normalizedUri == null) {
            return null;
        }

        // Mapping is against the normalized uri

        if (getContext().getDispatchersUseEncodedPaths()) {
            // Decode
            String decodedUri = UDecoder.URLDecode(normalizedUri, StandardCharsets.UTF_8);

            // Security check to catch attempts to encode /../ sequences
            normalizedUri = RequestUtil.normalize(decodedUri);
            if (!decodedUri.equals(normalizedUri)) {
                getContext().getLogger().warn(sm.getString("applicationContext.illegalDispatchPath", path),
                        new IllegalArgumentException());
                return null;
            }

            // URI needs to include the context path
            uri = URLEncoder.DEFAULT.encode(getContextPath(), StandardCharsets.UTF_8) + uri;
        } else {
            // uri is passed to the constructor for ApplicationDispatcher and is
            // ultimately used as the value for getRequestURI() which returns
            // encoded values. Therefore, since the value passed in for path
            // was decoded, encode uri here.
            uri = URLEncoder.DEFAULT.encode(getContextPath() + uri, StandardCharsets.UTF_8);
        }

        // Use the thread local URI and mapping data
        DispatchData dd = dispatchData.get();
        if (dd == null) {
            dd = new DispatchData();
            dispatchData.set(dd);
        }

        // Use the thread local mapping data
        MessageBytes uriMB = dd.uriMB;
        MappingData mappingData = dd.mappingData;

        try {
            // Map the URI
            uriMB.setChars(MessageBytes.EMPTY_CHAR_ARRAY, 0, 0);
            CharChunk uriCC = uriMB.getCharChunk();
            try {
                uriCC.append(context.getPath());
                uriCC.append(normalizedUri);
                service.getMapper().map(context, uriMB, mappingData);
                if (mappingData.wrapper == null) {
                    return null;
                }
            } catch (Exception e) {
                // Should never happen
                log(sm.getString("applicationContext.mapping.error"), e);
                return null;
            }

            Wrapper wrapper = mappingData.wrapper;
            String wrapperPath = mappingData.wrapperPath.toString();
            String pathInfo = mappingData.pathInfo.toString();
            HttpServletMapping mapping = new ApplicationMapping(mappingData).getHttpServletMapping();

            // Construct a RequestDispatcher to process this request
            return new ApplicationDispatcher(wrapper, uri, wrapperPath, pathInfo, queryString, mapping, null);
        } finally {
            // Recycle thread local data at the end of the request so references
            // are not held to a completed request as there is potential for
            // that to trigger a memory leak if a context is unloaded. Not
            // strictly necessary here for uriMB but it needs to be recycled at
            // some point so do it here for consistency with mappingData which
            // must be recycled here.
            uriMB.recycle();
            mappingData.recycle();
        }
    }


    // Package private to facilitate testing
    static String stripPathParams(String input) {
        // Shortcut
        if (input.indexOf(';') < 0) {
            return input;
        }

        StringBuilder sb = new StringBuilder(input.length());
        int pos = 0;
        int limit = input.length();
        while (pos < limit) {
            int nextSemiColon = input.indexOf(';', pos);
            if (nextSemiColon < 0) {
                nextSemiColon = limit;
            }
            sb.append(input.substring(pos, nextSemiColon));
            int followingSlash = input.indexOf('/', nextSemiColon);
            if (followingSlash < 0) {
                pos = limit;
            } else {
                pos = followingSlash;
            }
        }

        return sb.toString();
    }


    @Override
    public URL getResource(String path) throws MalformedURLException {

        String validatedPath = validateResourcePath(path, !context.getContextGetResourceRequiresSlash());

        if (validatedPath == null) {
            throw new MalformedURLException(sm.getString("applicationContext.requestDispatcher.iae", path));
        }

        WebResourceRoot resources = context.getResources();
        if (resources != null) {
            return resources.getResource(validatedPath).getURL();
        }

        return null;
    }


    @Override
    public InputStream getResourceAsStream(String path) {

        String validatedPath = validateResourcePath(path, !context.getContextGetResourceRequiresSlash());

        if (validatedPath == null) {
            return null;
        }

        WebResourceRoot resources = context.getResources();
        if (resources != null) {
            return resources.getResource(validatedPath).getInputStream();
        }

        return null;
    }


    /*
     * Returns null if the input path is not valid or a path that will be acceptable to resources.getResource().
     */
    private String validateResourcePath(String path, boolean addMissingInitialSlash) {
        if (path == null) {
            return null;
        }

        if (!path.startsWith("/")) {
            if (addMissingInitialSlash) {
                return "/" + path;
            } else {
                return null;
            }
        }

        return path;
    }


    @Override
    public Set<String> getResourcePaths(String path) {

        // Validate the path argument
        if (path == null) {
            return null;
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException(sm.getString("applicationContext.resourcePaths.iae", path));
        }

        WebResourceRoot resources = context.getResources();
        if (resources != null) {
            return resources.listWebAppPaths(path);
        }

        return null;
    }


    @Override
    public String getServerInfo() {
        return ServerInfo.getServerInfo();
    }


    @Override
    public String getServletContextName() {
        return context.getDisplayName();
    }


    @Override
    public void log(String message) {
        context.getLogger().info(message);
    }


    @Override
    public void log(String message, Throwable throwable) {
        context.getLogger().error(message, throwable);
    }


    @Override
    public void removeAttribute(String name) {

        Object value = null;

        // Remove the specified attribute
        // Check for read only attribute
        if (readOnlyAttributes.containsKey(name)) {
            return;
        }
        value = attributes.remove(name);
        if (value == null) {
            return;
        }

        // Notify interested application event listeners
        Object listeners[] = context.getApplicationEventListeners();
        if (listeners == null || listeners.length == 0) {
            return;
        }
        ServletContextAttributeEvent event = new ServletContextAttributeEvent(context.getServletContext(), name, value);
        for (Object obj : listeners) {
            if (!(obj instanceof ServletContextAttributeListener)) {
                continue;
            }
            ServletContextAttributeListener listener = (ServletContextAttributeListener) obj;
            try {
                context.fireContainerEvent("beforeContextAttributeRemoved", listener);
                listener.attributeRemoved(event);
                context.fireContainerEvent("afterContextAttributeRemoved", listener);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                context.fireContainerEvent("afterContextAttributeRemoved", listener);
                log(sm.getString("applicationContext.attributeEvent"), t);
            }
        }
    }


    @Override
    public void setAttribute(String name, Object value) {
        // Name cannot be null
        if (name == null) {
            throw new NullPointerException(sm.getString("applicationContext.setAttribute.namenull"));
        }

        // Null value is the same as removeAttribute()
        if (value == null) {
            removeAttribute(name);
            return;
        }

        // Add or replace the specified attribute
        // Check for read only attribute
        if (readOnlyAttributes.containsKey(name)) {
            return;
        }

        Object oldValue = attributes.put(name, value);
        boolean replaced = oldValue != null;

        // Notify interested application event listeners
        Object listeners[] = context.getApplicationEventListeners();
        if (listeners == null || listeners.length == 0) {
            return;
        }
        ServletContextAttributeEvent event = null;
        if (replaced) {
            event = new ServletContextAttributeEvent(context.getServletContext(), name, oldValue);
        } else {
            event = new ServletContextAttributeEvent(context.getServletContext(), name, value);
        }

        for (Object obj : listeners) {
            if (!(obj instanceof ServletContextAttributeListener)) {
                continue;
            }
            ServletContextAttributeListener listener = (ServletContextAttributeListener) obj;
            try {
                if (replaced) {
                    context.fireContainerEvent("beforeContextAttributeReplaced", listener);
                    listener.attributeReplaced(event);
                    context.fireContainerEvent("afterContextAttributeReplaced", listener);
                } else {
                    context.fireContainerEvent("beforeContextAttributeAdded", listener);
                    listener.attributeAdded(event);
                    context.fireContainerEvent("afterContextAttributeAdded", listener);
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                if (replaced) {
                    context.fireContainerEvent("afterContextAttributeReplaced", listener);
                } else {
                    context.fireContainerEvent("afterContextAttributeAdded", listener);
                }
                log(sm.getString("applicationContext.attributeEvent"), t);
            }
        }
    }


    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, String className) {
        return addFilter(filterName, className, null);
    }


    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
        return addFilter(filterName, null, filter);
    }


    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass) {
        return addFilter(filterName, filterClass.getName(), null);
    }


    private FilterRegistration.Dynamic addFilter(String filterName, String filterClass, Filter filter)
            throws IllegalStateException {

        if (filterName == null || filterName.equals("")) {
            throw new IllegalArgumentException(sm.getString("applicationContext.invalidFilterName", filterName));
        }

        // TODO Spec breaking enhancement to ignore this restriction
        checkState("applicationContext.addFilter.ise");

        FilterDef filterDef = context.findFilterDef(filterName);

        // Assume a 'complete' FilterRegistration is one that has a class and
        // a name
        if (filterDef == null) {
            filterDef = new FilterDef();
            filterDef.setFilterName(filterName);
            context.addFilterDef(filterDef);
        } else {
            if (filterDef.getFilterName() != null && filterDef.getFilterClass() != null) {
                return null;
            }
        }

        if (filter == null) {
            filterDef.setFilterClass(filterClass);
        } else {
            filterDef.setFilterClass(filter.getClass().getName());
            filterDef.setFilter(filter);
        }

        return new ApplicationFilterRegistration(filterDef, context);
    }


    @Override
    public <T extends Filter> T createFilter(Class<T> c) throws ServletException {
        try {
            @SuppressWarnings("unchecked")
            T filter = (T) context.getInstanceManager().newInstance(c.getName());
            return filter;
        } catch (InvocationTargetException e) {
            ExceptionUtils.handleThrowable(e.getCause());
            throw new ServletException(e);
        } catch (ReflectiveOperationException | NamingException e) {
            throw new ServletException(e);
        }
    }


    @Override
    public FilterRegistration getFilterRegistration(String filterName) {
        FilterDef filterDef = context.findFilterDef(filterName);
        if (filterDef == null) {
            return null;
        }
        return new ApplicationFilterRegistration(filterDef, context);
    }


    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, String className) {
        return addServlet(servletName, className, null, null);
    }


    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet) {
        return addServlet(servletName, null, servlet, null);
    }


    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass) {
        return addServlet(servletName, servletClass.getName(), null, null);
    }


    @Override
    public Dynamic addJspFile(String jspName, String jspFile) {

        // jspName is validated in addServlet()
        if (jspFile == null || !jspFile.startsWith("/")) {
            throw new IllegalArgumentException(sm.getString("applicationContext.addJspFile.iae", jspFile));
        }

        String jspServletClassName = null;
        Map<String,String> jspFileInitParams = new HashMap<>();

        Wrapper jspServlet = (Wrapper) context.findChild("jsp");

        if (jspServlet == null) {
            // No JSP servlet currently defined.
            // Use default JSP Servlet class name
            jspServletClassName = Constants.JSP_SERVLET_CLASS;
        } else {
            // JSP Servlet defined.
            // Use same JSP Servlet class name
            jspServletClassName = jspServlet.getServletClass();
            // Use same init parameters
            String[] params = jspServlet.findInitParameters();
            for (String param : params) {
                jspFileInitParams.put(param, jspServlet.findInitParameter(param));
            }
        }

        // Add init parameter to specify JSP file
        jspFileInitParams.put("jspFile", jspFile);

        return addServlet(jspName, jspServletClassName, null, jspFileInitParams);
    }


    private ServletRegistration.Dynamic addServlet(String servletName, String servletClass, Servlet servlet,
            Map<String,String> initParams) throws IllegalStateException {

        if (servletName == null || servletName.equals("")) {
            throw new IllegalArgumentException(sm.getString("applicationContext.invalidServletName", servletName));
        }

        // TODO Spec breaking enhancement to ignore this restriction
        checkState("applicationContext.addServlet.ise");

        Wrapper wrapper = (Wrapper) context.findChild(servletName);

        // Assume a 'complete' ServletRegistration is one that has a class and
        // a name
        if (wrapper == null) {
            wrapper = context.createWrapper();
            wrapper.setName(servletName);
            context.addChild(wrapper);
        } else {
            if (wrapper.getName() != null && wrapper.getServletClass() != null) {
                if (wrapper.isOverridable()) {
                    wrapper.setOverridable(false);
                } else {
                    return null;
                }
            }
        }

        ServletSecurity annotation = null;
        if (servlet == null) {
            wrapper.setServletClass(servletClass);
            Class<?> clazz = Introspection.loadClass(context, servletClass);
            if (clazz != null) {
                annotation = clazz.getAnnotation(ServletSecurity.class);
            }
        } else {
            wrapper.setServletClass(servlet.getClass().getName());
            wrapper.setServlet(servlet);
            if (context.wasCreatedDynamicServlet(servlet)) {
                annotation = servlet.getClass().getAnnotation(ServletSecurity.class);
            }
        }

        if (initParams != null) {
            for (Map.Entry<String,String> initParam : initParams.entrySet()) {
                wrapper.addInitParameter(initParam.getKey(), initParam.getValue());
            }
        }

        ServletRegistration.Dynamic registration = new ApplicationServletRegistration(wrapper, context);
        if (annotation != null) {
            registration.setServletSecurity(new ServletSecurityElement(annotation));
        }
        return registration;
    }


    @Override
    public <T extends Servlet> T createServlet(Class<T> c) throws ServletException {
        try {
            @SuppressWarnings("unchecked")
            T servlet = (T) context.getInstanceManager().newInstance(c.getName());
            context.dynamicServletCreated(servlet);
            return servlet;
        } catch (InvocationTargetException e) {
            ExceptionUtils.handleThrowable(e.getCause());
            throw new ServletException(e);
        } catch (ReflectiveOperationException | NamingException e) {
            throw new ServletException(e);
        }
    }


    @Override
    public ServletRegistration getServletRegistration(String servletName) {
        Wrapper wrapper = (Wrapper) context.findChild(servletName);
        if (wrapper == null) {
            return null;
        }

        return new ApplicationServletRegistration(wrapper, context);
    }


    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        return defaultSessionTrackingModes;
    }


    private void populateSessionTrackingModes() {
        // URL re-writing is always enabled by default
        defaultSessionTrackingModes = EnumSet.of(SessionTrackingMode.URL);
        supportedSessionTrackingModes = EnumSet.of(SessionTrackingMode.URL);

        if (context.getCookies()) {
            defaultSessionTrackingModes.add(SessionTrackingMode.COOKIE);
            supportedSessionTrackingModes.add(SessionTrackingMode.COOKIE);
        }

        // SSL not enabled by default as it can only used on its own
        // Context > Host > Engine > Service
        Connector[] connectors = service.findConnectors();
        // Need at least one SSL enabled connector to use the SSL session ID.
        for (Connector connector : connectors) {
            if (Boolean.TRUE.equals(connector.getProperty("SSLEnabled"))) {
                supportedSessionTrackingModes.add(SessionTrackingMode.SSL);
                break;
            }
        }
    }


    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
        if (sessionTrackingModes != null) {
            return sessionTrackingModes;
        }
        return defaultSessionTrackingModes;
    }


    @Override
    public SessionCookieConfig getSessionCookieConfig() {
        return sessionCookieConfig;
    }


    @Override
    public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {

        checkState("applicationContext.setSessionTracking.ise");

        // Check that only supported tracking modes have been requested
        for (SessionTrackingMode sessionTrackingMode : sessionTrackingModes) {
            if (!supportedSessionTrackingModes.contains(sessionTrackingMode)) {
                throw new IllegalArgumentException(sm.getString("applicationContext.setSessionTracking.iae.invalid",
                        sessionTrackingMode.toString(), getContextPath()));
            }
        }

        // Check SSL has not be configured with anything else
        if (sessionTrackingModes.contains(SessionTrackingMode.SSL)) {
            if (sessionTrackingModes.size() > 1) {
                throw new IllegalArgumentException(
                        sm.getString("applicationContext.setSessionTracking.iae.ssl", getContextPath()));
            }
        }

        this.sessionTrackingModes = sessionTrackingModes;
    }


    @Override
    public boolean setInitParameter(String name, String value) {
        // Name cannot be null
        if (name == null) {
            throw new NullPointerException(sm.getString("applicationContext.setAttribute.namenull"));
        }
        checkState("applicationContext.setInitParam.ise");
        return parameters.putIfAbsent(name, value) == null;
    }


    @Override
    public void addListener(Class<? extends EventListener> listenerClass) {
        EventListener listener;
        try {
            listener = createListener(listenerClass);
        } catch (ServletException e) {
            throw new IllegalArgumentException(
                    sm.getString("applicationContext.addListener.iae.init", listenerClass.getName()), e);
        }
        addListener(listener);
    }


    @Override
    public void addListener(String className) {

        try {
            if (context.getInstanceManager() != null) {
                Object obj = context.getInstanceManager().newInstance(className);

                if (!(obj instanceof EventListener)) {
                    throw new IllegalArgumentException(
                            sm.getString("applicationContext.addListener.iae.wrongType", className));
                }

                EventListener listener = (EventListener) obj;
                addListener(listener);
            }
        } catch (InvocationTargetException e) {
            ExceptionUtils.handleThrowable(e.getCause());
            throw new IllegalArgumentException(sm.getString("applicationContext.addListener.iae.cnfe", className), e);
        } catch (ReflectiveOperationException | NamingException e) {
            throw new IllegalArgumentException(sm.getString("applicationContext.addListener.iae.cnfe", className), e);
        }

    }


    @Override
    public <T extends EventListener> void addListener(T t) {
        checkState("applicationContext.addListener.ise");

        boolean match = false;
        if (t instanceof ServletContextAttributeListener || t instanceof ServletRequestListener ||
                t instanceof ServletRequestAttributeListener || t instanceof HttpSessionIdListener ||
                t instanceof HttpSessionAttributeListener) {
            context.addApplicationEventListener(t);
            match = true;
        }

        if (t instanceof HttpSessionListener ||
                (t instanceof ServletContextListener && newServletContextListenerAllowed)) {
            // Add listener directly to the list of instances rather than to
            // the list of class names.
            context.addApplicationLifecycleListener(t);
            match = true;
        }

        if (match) {
            return;
        }

        if (t instanceof ServletContextListener) {
            throw new IllegalArgumentException(
                    sm.getString("applicationContext.addListener.iae.sclNotAllowed", t.getClass().getName()));
        } else {
            throw new IllegalArgumentException(
                    sm.getString("applicationContext.addListener.iae.wrongType", t.getClass().getName()));
        }
    }


    @Override
    public <T extends EventListener> T createListener(Class<T> c) throws ServletException {
        try {
            @SuppressWarnings("unchecked")
            T listener = (T) context.getInstanceManager().newInstance(c);
            if (listener instanceof ServletContextListener || listener instanceof ServletContextAttributeListener ||
                    listener instanceof ServletRequestListener || listener instanceof ServletRequestAttributeListener ||
                    listener instanceof HttpSessionListener || listener instanceof HttpSessionIdListener ||
                    listener instanceof HttpSessionAttributeListener) {
                return listener;
            }
            throw new IllegalArgumentException(
                    sm.getString("applicationContext.addListener.iae.wrongType", listener.getClass().getName()));
        } catch (InvocationTargetException e) {
            ExceptionUtils.handleThrowable(e.getCause());
            throw new ServletException(e);
        } catch (ReflectiveOperationException | NamingException e) {
            throw new ServletException(e);
        }
    }


    @Override
    public void declareRoles(String... roleNames) {

        // TODO Spec breaking enhancement to ignore this restriction
        checkState("applicationContext.addRole.ise");

        if (roleNames == null) {
            throw new IllegalArgumentException(sm.getString("applicationContext.roles.iae", getContextPath()));
        }

        for (String role : roleNames) {
            if (role == null || role.isEmpty()) {
                throw new IllegalArgumentException(sm.getString("applicationContext.role.iae", getContextPath()));
            }
            context.addSecurityRole(role);
        }
    }


    @Override
    public ClassLoader getClassLoader() {
        return context.getLoader().getClassLoader();
    }


    @Override
    public int getEffectiveMajorVersion() {
        return context.getEffectiveMajorVersion();
    }


    @Override
    public int getEffectiveMinorVersion() {
        return context.getEffectiveMinorVersion();
    }


    @Override
    public Map<String,? extends FilterRegistration> getFilterRegistrations() {
        Map<String,ApplicationFilterRegistration> result = new HashMap<>();

        FilterDef[] filterDefs = context.findFilterDefs();
        for (FilterDef filterDef : filterDefs) {
            result.put(filterDef.getFilterName(), new ApplicationFilterRegistration(filterDef, context));
        }

        return result;
    }


    @Override
    public JspConfigDescriptor getJspConfigDescriptor() {
        return context.getJspConfigDescriptor();
    }


    @Override
    public Map<String,? extends ServletRegistration> getServletRegistrations() {
        Map<String,ApplicationServletRegistration> result = new HashMap<>();

        Container[] wrappers = context.findChildren();
        for (Container wrapper : wrappers) {
            result.put(wrapper.getName(), new ApplicationServletRegistration((Wrapper) wrapper, context));
        }

        return result;
    }


    @Override
    public String getVirtualServerName() {
        // Constructor will fail if context or its parent is null
        Container host = context.getParent();
        Container engine = host.getParent();
        return engine.getName() + "/" + host.getName();
    }


    @Override
    public int getSessionTimeout() {
        return context.getSessionTimeout();
    }


    @Override
    public void setSessionTimeout(int sessionTimeout) {
        checkState("applicationContext.setSessionTimeout.ise");
        context.setSessionTimeout(sessionTimeout);
    }


    @Override
    public String getRequestCharacterEncoding() {
        return context.getRequestCharacterEncoding();
    }


    @Override
    public void setRequestCharacterEncoding(String encoding) {
        checkState("applicationContext.setRequestEncoding.ise");
        context.setRequestCharacterEncoding(encoding);
    }


    @Override
    public String getResponseCharacterEncoding() {
        return context.getResponseCharacterEncoding();
    }


    @Override
    public void setResponseCharacterEncoding(String encoding) {
        checkState("applicationContext.setResponseEncoding.ise");
        context.setResponseCharacterEncoding(encoding);
    }


    private void checkState(String messageKey) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(sm.getString(messageKey, getContextPath()));
        }
    }


    // -------------------------------------------------------- Package Methods
    protected StandardContext getContext() {
        return this.context;
    }

    /**
     * Clear all application-created attributes.
     */
    protected void clearAttributes() {

        // Create list of attributes to be removed
        List<String> list = new ArrayList<>(attributes.keySet());

        // Remove application originated attributes
        // (read only attributes will be left in place)
        for (String key : list) {
            removeAttribute(key);
        }

    }


    /**
     * @return the facade associated with this ApplicationContext.
     */
    protected ServletContext getFacade() {
        return this.facade;
    }


    /**
     * Set an attribute as read only.
     */
    void setAttributeReadOnly(String name) {

        if (attributes.containsKey(name)) {
            readOnlyAttributes.put(name, name);
        }

    }


    protected void setNewServletContextListenerAllowed(boolean allowed) {
        this.newServletContextListenerAllowed = allowed;
    }

    /**
     * Internal class used as thread-local storage when doing path mapping during dispatch.
     */
    private static final class DispatchData {

        public MessageBytes uriMB;
        public MappingData mappingData;

        DispatchData() {
            uriMB = MessageBytes.newInstance();
            CharChunk uriCC = uriMB.getCharChunk();
            uriCC.setLimit(-1);
            mappingData = new MappingData();
        }
    }
}
