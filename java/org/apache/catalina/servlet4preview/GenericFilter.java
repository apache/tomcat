/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.servlet4preview;

import java.io.Serializable;
import java.util.Enumeration;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

/**
 * @deprecated This class is not included in Tomcat 9 onwards. Users of this
 *             class should normally upgrade to Tomcat 9 and switch to the
 *             Servlet 4.0 API. If the functionality is required in Tomcat 8.5,
 *             then the Tomcat implementation classes should be used directly.
 *             This class may be removed from Tomcat 8.5.x some time after 30
 *             September 2018.
 */
@Deprecated
public abstract class GenericFilter implements Filter, FilterConfig, Serializable {

    private static final long serialVersionUID = 1L;

    private volatile FilterConfig filterConfig;


    @Override
    public String getInitParameter(String name) {
        return getFilterConfig().getInitParameter(name);
    }


    @Override
    public Enumeration<String> getInitParameterNames() {
        return getFilterConfig().getInitParameterNames();
    }


    /**
     * Obtain the FilterConfig used to initialise this Filter instance.
     *
     * @return The config previously passed to the {@link #init(FilterConfig)}
     *         method
     */
    public FilterConfig getFilterConfig() {
        return filterConfig;
    }


    @Override
    public ServletContext getServletContext() {
        return getFilterConfig().getServletContext();
    }


    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.filterConfig  = filterConfig;
        init();
    }


    /**
     * Convenience method for sub-classes to save them having to call
     * <code>super.init(config)</code>. This is a NO-OP by default.
     *
     * @throws ServletException If an exception occurs that interrupts the
     *         Filter's normal operation
     */
    public void init() throws ServletException {
        // NO-OP
    }


    @Override
    public String getFilterName() {
        return getFilterConfig().getFilterName();
    }
}
