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


package org.apache.tomcat.lite;


import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Map;

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
public final class FilterConfigImpl implements FilterConfig {

    public FilterConfigImpl(ServletContextImpl context) {
        this.context = context;
    }
    
    private ServletContextImpl context = null;

    /**
     * The application Filter we are configured for.
     */
    private transient Filter filter = null;

    private String filterName;

    private String filterClass;

    private Map<String, String> initParams;

    public void setData(String filterName, String filterClass,
                        Map<String, String> params) {
        this.filterName = filterName;
        this.filterClass = filterClass;
        this.initParams = params;
    }
    
    public void setFilter(Filter f) {
        filter = f;
    }
    
    public String getFilterName() {
        return filterName;
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
        return context;
    }

    /**
     * Return the application Filter we are configured for.
     */
    public Filter getFilter() throws ClassCastException, ClassNotFoundException,
        IllegalAccessException, InstantiationException, ServletException {

        // Return the existing filter instance, if any
        if (filter != null)
            return filter;

        ClassLoader classLoader = context.getClassLoader();

        ClassLoader oldCtxClassLoader =
            Thread.currentThread().getContextClassLoader();
        if (classLoader != oldCtxClassLoader) {
            Thread.currentThread().setContextClassLoader(classLoader);
        }
        try {
            Class clazz = classLoader.loadClass(filterClass);
            this.filter = (Filter) clazz.newInstance();
        } finally {        
            if (classLoader != oldCtxClassLoader) {
                Thread.currentThread().setContextClassLoader(oldCtxClassLoader);
            }
        }
        
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
}
