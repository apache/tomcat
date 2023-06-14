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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.ServletRegistration.Dynamic;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.SessionTrackingMode;
import jakarta.servlet.descriptor.JspConfigDescriptor;


/**
 * Facade object which masks the internal <code>ApplicationContext</code> object from the web application.
 *
 * @author Remy Maucherat
 */
public class ApplicationContextFacade implements ServletContext {

    // ---------------------------------------------------------- Attributes
    /**
     * Cache Class object used for reflection.
     */
    private final Map<String,Class<?>[]> classCache;


    // ----------------------------------------------------------- Constructors

    /**
     * Construct a new instance of this class, associated with the specified Context instance.
     *
     * @param context The associated Context instance
     */
    public ApplicationContextFacade(ApplicationContext context) {
        super();
        this.context = context;

        classCache = new HashMap<>();
        initClassCache();
    }


    private void initClassCache() {
        Class<?>[] clazz = new Class[] { String.class };
        classCache.put("getContext", clazz);
        classCache.put("getMimeType", clazz);
        classCache.put("getResourcePaths", clazz);
        classCache.put("getResource", clazz);
        classCache.put("getResourceAsStream", clazz);
        classCache.put("getRequestDispatcher", clazz);
        classCache.put("getNamedDispatcher", clazz);
        classCache.put("getServlet", clazz);
        classCache.put("setInitParameter", new Class[] { String.class, String.class });
        classCache.put("createServlet", new Class[] { Class.class });
        classCache.put("addServlet", new Class[] { String.class, String.class });
        classCache.put("createFilter", new Class[] { Class.class });
        classCache.put("addFilter", new Class[] { String.class, String.class });
        classCache.put("createListener", new Class[] { Class.class });
        classCache.put("addListener", clazz);
        classCache.put("getFilterRegistration", clazz);
        classCache.put("getServletRegistration", clazz);
        classCache.put("getInitParameter", clazz);
        classCache.put("setAttribute", new Class[] { String.class, Object.class });
        classCache.put("removeAttribute", clazz);
        classCache.put("getRealPath", clazz);
        classCache.put("getAttribute", clazz);
        classCache.put("log", clazz);
        classCache.put("setSessionTrackingModes", new Class[] { Set.class });
        classCache.put("addJspFile", new Class[] { String.class, String.class });
        classCache.put("declareRoles", new Class[] { String[].class });
        classCache.put("setSessionTimeout", new Class[] { int.class });
        classCache.put("setRequestCharacterEncoding", new Class[] { String.class });
        classCache.put("setResponseCharacterEncoding", new Class[] { String.class });
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Wrapped application context.
     */
    private final ApplicationContext context;


    // ------------------------------------------------- ServletContext Methods


    @Override
    public ServletContext getContext(String uripath) {
        ServletContext theContext = context.getContext(uripath);
        if ((theContext != null) && (theContext instanceof ApplicationContext)) {
            theContext = ((ApplicationContext) theContext).getFacade();
        }
        return theContext;
    }


    @Override
    public int getMajorVersion() {
        return context.getMajorVersion();
    }


    @Override
    public int getMinorVersion() {
        return context.getMinorVersion();
    }


    @Override
    public String getMimeType(String file) {
        return context.getMimeType(file);
    }

    @Override
    public Set<String> getResourcePaths(String path) {
        return context.getResourcePaths(path);
    }


    @Override
    public URL getResource(String path) throws MalformedURLException {
        return context.getResource(path);
    }


    @Override
    public InputStream getResourceAsStream(String path) {
        return context.getResourceAsStream(path);
    }


    @Override
    public RequestDispatcher getRequestDispatcher(final String path) {
        return context.getRequestDispatcher(path);
    }


    @Override
    public RequestDispatcher getNamedDispatcher(String name) {
        return context.getNamedDispatcher(name);
    }


    @Override
    public void log(String msg) {
        context.log(msg);
    }


    @Override
    public void log(String message, Throwable throwable) {
        context.log(message, throwable);
    }


    @Override
    public String getRealPath(String path) {
        return context.getRealPath(path);
    }


    @Override
    public String getServerInfo() {
        return context.getServerInfo();
    }


    @Override
    public String getInitParameter(String name) {
        return context.getInitParameter(name);
    }


    @Override
    public Enumeration<String> getInitParameterNames() {
        return context.getInitParameterNames();
    }


    @Override
    public Object getAttribute(String name) {
        return context.getAttribute(name);
    }


    @Override
    public Enumeration<String> getAttributeNames() {
        return context.getAttributeNames();
    }


    @Override
    public void setAttribute(String name, Object object) {
        context.setAttribute(name, object);
    }


    @Override
    public void removeAttribute(String name) {
        context.removeAttribute(name);
    }


    @Override
    public String getServletContextName() {
        return context.getServletContextName();
    }


    @Override
    public String getContextPath() {
        return context.getContextPath();
    }


    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, String className) {
        return context.addFilter(filterName, className);
    }


    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
        return context.addFilter(filterName, filter);
    }


    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass) {
        return context.addFilter(filterName, filterClass);
    }

    @Override
    public <T extends Filter> T createFilter(Class<T> c) throws ServletException {
        return context.createFilter(c);
    }


    @Override
    public FilterRegistration getFilterRegistration(String filterName) {
        return context.getFilterRegistration(filterName);
    }


    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, String className) {
        return context.addServlet(servletName, className);
    }


    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet) {
        return context.addServlet(servletName, servlet);
    }


    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass) {
        return context.addServlet(servletName, servletClass);
    }


    @Override
    public Dynamic addJspFile(String jspName, String jspFile) {
        return context.addJspFile(jspName, jspFile);
    }


    @Override
    public <T extends Servlet> T createServlet(Class<T> c) throws ServletException {
        return context.createServlet(c);
    }


    @Override
    public ServletRegistration getServletRegistration(String servletName) {
        return context.getServletRegistration(servletName);
    }


    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        return context.getDefaultSessionTrackingModes();
    }

    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
        return context.getEffectiveSessionTrackingModes();
    }


    @Override
    public SessionCookieConfig getSessionCookieConfig() {
        return context.getSessionCookieConfig();
    }


    @Override
    public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {
        context.setSessionTrackingModes(sessionTrackingModes);
    }


    @Override
    public boolean setInitParameter(String name, String value) {
        return context.setInitParameter(name, value);
    }


    @Override
    public void addListener(Class<? extends EventListener> listenerClass) {
        context.addListener(listenerClass);
    }


    @Override
    public void addListener(String className) {
        context.addListener(className);
    }


    @Override
    public <T extends EventListener> void addListener(T t) {
        context.addListener(t);
    }


    @Override
    public <T extends EventListener> T createListener(Class<T> c) throws ServletException {
        return context.createListener(c);
    }


    @Override
    public void declareRoles(String... roleNames) {
        context.declareRoles(roleNames);
    }


    @Override
    public ClassLoader getClassLoader() {
        return context.getClassLoader();
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
        return context.getFilterRegistrations();
    }


    @Override
    public JspConfigDescriptor getJspConfigDescriptor() {
        return context.getJspConfigDescriptor();
    }


    @Override
    public Map<String,? extends ServletRegistration> getServletRegistrations() {
        return context.getServletRegistrations();
    }


    @Override
    public String getVirtualServerName() {
        return context.getVirtualServerName();
    }


    @Override
    public int getSessionTimeout() {
        return context.getSessionTimeout();
    }


    @Override
    public void setSessionTimeout(int sessionTimeout) {
        context.setSessionTimeout(sessionTimeout);
    }


    @Override
    public String getRequestCharacterEncoding() {
        return context.getRequestCharacterEncoding();
    }


    @Override
    public void setRequestCharacterEncoding(String encoding) {
        context.setRequestCharacterEncoding(encoding);
    }


    @Override
    public String getResponseCharacterEncoding() {
        return context.getResponseCharacterEncoding();
    }


    @Override
    public void setResponseCharacterEncoding(String encoding) {
        context.setResponseCharacterEncoding(encoding);
    }
}
