/*
 * Copyright 1999,2004 The Apache Software Foundation.
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


package org.apache.coyote.servlet;


import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.FilterRegistration.Dynamic;

import org.apache.tomcat.servlets.util.Enumerator;


/**
 * A Filter is configured in web.xml by:
 *  - name - used in mappings
 *  - className - used to instantiate the filter
 *  - init params
 *  - other things not used in the servlet container ( icon, descr, etc )
 *
 * Alternatively, in API mode you can pass the actual filter.
 *
 * @see ServletConfigImpl
 */
public final class FilterConfigImpl implements FilterConfig, FilterRegistration {

    DynamicFilterRegistration dynamic = new DynamicFilterRegistration();

    public FilterConfigImpl(ServletContextImpl context) {
        this.ctx = context;
    }

    boolean asyncSupported;

    private ServletContextImpl ctx = null;

    /**
     * The application Filter we are configured for.
     */
    private transient Filter filter = null;

    String descryption;

    private String filterName;

    private String filterClassName;

    Map<String, String> initParams;

    private Class<? extends Filter> filterClass;

    public void setData(String filterName, String filterClass,
                        Map<String, String> params) {
        this.filterName = filterName;
        this.filterClassName = filterClass;
        this.initParams = params;
    }

    public void setFilter(Filter f) {
        filter = f;
    }

    public String getFilterName() {
        return filterName;
    }

    public void setFilterClass(Class<? extends Filter> filterClass2) {
        this.filterClass = filterClass2;
    }


    public String getInitParameter(String name) {
        if (initParams == null) return null;
        return initParams.get(name);
    }

    /**
     * Return an <code>Enumeration</code> of the names of the initialization
     * parameters for this Filter.
     */
    public Enumeration getInitParameterNames() {
        if (initParams == null)
            return (new Enumerator(new ArrayList()));
        else
            return (new Enumerator(initParams.keySet()));
    }


    /**
     * Return the ServletContext of our associated web application.
     */
    public ServletContext getServletContext() {
        return ctx;
    }

    /**
     * Return the application Filter we are configured for.
     */
    public Filter createFilter() throws ClassCastException, ClassNotFoundException,
        IllegalAccessException, InstantiationException, ServletException {

        // Return the existing filter instance, if any
        if (filter != null)
            return filter;

        ClassLoader classLoader = ctx.getClassLoader();

        ClassLoader oldCtxClassLoader =
            Thread.currentThread().getContextClassLoader();
        if (classLoader != oldCtxClassLoader) {
            Thread.currentThread().setContextClassLoader(classLoader);
        }
        try {
            if (filterClass == null) {
                filterClass = (Class<? extends Filter>) classLoader.loadClass(filterClassName);
            }
            this.filter = (Filter) filterClass.newInstance();
        } finally {
            if (classLoader != oldCtxClassLoader) {
                Thread.currentThread().setContextClassLoader(oldCtxClassLoader);
            }
        }

        // TODO: resource injection

        return filter;
    }

    public Filter getFilter() throws ClassCastException, ClassNotFoundException, IllegalAccessException, InstantiationException, ServletException {
        Filter filter = createFilter();
        filter.init(this);
        return (this.filter);
    }


    /**
     * Release the Filter instance associated with this FilterConfig,
     * if there is one.
     */
    public void release() {
        if (this.filter != null){
            filter.destroy();
        }
        this.filter = null;
     }


   @Override
    public void addMappingForServletNames(EnumSet<DispatcherType> dispatcherTypes,
                                          boolean isMatchAfter,
                                          String... servletNames) {
        if (ctx.startDone) {
            // Use the context method instead of the servlet API to
            // add mappings after context init.
            throw new IllegalStateException();
        }
        ArrayList<String> dispatchers = new ArrayList<String>();
        for (DispatcherType dt: dispatcherTypes) {
            dispatchers.add(dt.name());
        }
        for (String servletName: servletNames) {
            ctx.getFilterMapper().addMapping(getFilterName(),
                    null, servletName, (String[]) dispatchers.toArray(), isMatchAfter);
        }
    }


   @Override
    public void addMappingForUrlPatterns(EnumSet<DispatcherType> dispatcherTypes,
                                         boolean isMatchAfter,
                                         String... urlPatterns) {
        if (ctx.startDone) {
            // Use the context method instead of the servlet API to
            // add mappings after context init.
            throw new IllegalStateException();
        }
        ArrayList<String> dispatchers = new ArrayList<String>();
        for (DispatcherType dt: dispatcherTypes) {
            dispatchers.add(dt.name());
        }
        for (String url: urlPatterns) {
            ctx.getFilterMapper().addMapping(getFilterName(),
                    url, null, (String[]) dispatchers.toArray(), isMatchAfter);
        }
    }


   @Override
    public boolean setInitParameter(String name, String value)
            throws IllegalArgumentException, IllegalStateException {
        return ServletContextImpl.setInitParameter(ctx, initParams,
                name, value);
    }


   @Override
    public Set<String> setInitParameters(Map<String, String> initParameters)
    throws IllegalArgumentException, IllegalStateException {
        return ServletContextImpl.setInitParameters(ctx, initParams,
                initParameters);
    }

    public Dynamic getDynamic() {
        return dynamic;
    }

    public class DynamicFilterRegistration implements Dynamic {


       @Override
        public void addMappingForServletNames(EnumSet<DispatcherType> dispatcherTypes,
                                              boolean isMatchAfter,
                                              String... servletNames) {
            FilterConfigImpl.this.addMappingForServletNames(dispatcherTypes, isMatchAfter, servletNames);
        }


       @Override
        public void addMappingForUrlPatterns(EnumSet<DispatcherType> dispatcherTypes,
                                             boolean isMatchAfter,
                                             String... urlPatterns) {
            FilterConfigImpl.this.addMappingForUrlPatterns(dispatcherTypes, isMatchAfter, urlPatterns);
        }


       @Override
        public boolean setInitParameter(String name, String value)
                throws IllegalArgumentException, IllegalStateException {
            return ServletContextImpl.setInitParameter(ctx, initParams,
                    name, value);
        }


       @Override
        public Set<String> setInitParameters(Map<String, String> initParameters)
                throws IllegalArgumentException, IllegalStateException {
            return ServletContextImpl.setInitParameters(ctx, initParams,
                    initParameters);
        }


       @Override
        public void setAsyncSupported(boolean isAsyncSupported)
                throws IllegalStateException {
            asyncSupported = isAsyncSupported;
        }


        public void setDescription(String description)
                throws IllegalStateException {
            FilterConfigImpl.this.descryption = description;
        }

       @Override
        public Collection<String> getUrlPatternMappings() {
            // implement me
            return null;
        }

       @Override
        public Collection<String> getServletNameMappings() {
            // implement me
            return null;
        }

       @Override
        public Map<String, String> getInitParameters() {
            // implement me
            return null;
        }

       @Override
        public String getInitParameter(String name) {
            if (initParams == null) return null;
            return initParams.get(name);
        }

       @Override
        public String getClassName() {
            // implement me
            return null;
        }

       @Override
        public String getName() {
            // implement me
            return null;
        }
    }

   @Override
    public Collection<String> getUrlPatternMappings() {
        // implement me
        return null;
    }

   @Override
    public Collection<String> getServletNameMappings() {
        // implement me
        return null;
    }

   @Override
    public Map<String, String> getInitParameters() {
        // implement me
        return null;
    }

   @Override
    public String getClassName() {
        // implement me
        return null;
    }

   @Override
    public String getName() {
        // implement me
        return null;
    }

}
