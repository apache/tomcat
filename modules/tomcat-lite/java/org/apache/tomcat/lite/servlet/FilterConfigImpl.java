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


package org.apache.tomcat.lite.servlet;


import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

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
public class FilterConfigImpl implements FilterConfig {
    
    public FilterConfigImpl(ServletContextImpl context) {
        this.ctx = context;
    }
    
    boolean asyncSupported;
    
    ServletContextImpl ctx = null;

    /**
     * The application Filter we are configured for.
     */
    private transient Filter filter = null;
    
    String descryption;
    
    private String filterName;

    private String filterClassName;

    Map<String, String> initParams;

    private Class<? extends Filter> filterClass;

    private boolean initDone = false;

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
        if (!initDone ) {
            filter.init(this);
            initDone = true;
        }
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
}
