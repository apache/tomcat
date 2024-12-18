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

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletRequest;

import org.apache.catalina.Globals;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Request;
import org.apache.catalina.util.FilterUtil;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.tomcat.util.res.StringManager;

/**
 * Factory for the creation and caching of Filters and creation of Filter Chains.
 *
 * @author Greg Murray
 * @author Remy Maucherat
 */
public final class ApplicationFilterFactory {

    private static final Log log = LogFactory.getLog(ApplicationFilterFactory.class);
    private static final StringManager sm = StringManager.getManager(ApplicationFilterFactory.class);

    private ApplicationFilterFactory() {
        // Prevent instance creation. This is a utility class.
    }


    /**
     * Construct a FilterChain implementation that will wrap the execution of the specified servlet instance.
     *
     * @param request The servlet request we are processing
     * @param wrapper The wrapper managing the servlet instance
     * @param servlet The servlet instance to be wrapped
     *
     * @return The configured FilterChain instance or null if none is to be executed.
     */
    public static ApplicationFilterChain createFilterChain(ServletRequest request, Wrapper wrapper, Servlet servlet) {

        // If there is no servlet to execute, return null
        if (servlet == null) {
            return null;
        }

        // Create and initialize a filter chain object
        ApplicationFilterChain filterChain = null;
        if (request instanceof Request) {
            Request req = (Request) request;
            filterChain = (ApplicationFilterChain) req.getFilterChain();
            if (filterChain == null) {
                filterChain = new ApplicationFilterChain();
                req.setFilterChain(filterChain);
            }
        } else {
            // Request dispatcher in use
            filterChain = new ApplicationFilterChain();
        }

        filterChain.setServlet(servlet);
        filterChain.setServletSupportsAsync(wrapper.isAsyncSupported());

        // Acquire the filter mappings for this Context
        StandardContext context = (StandardContext) wrapper.getParent();
        filterChain.setDispatcherWrapsSameObject(context.getDispatcherWrapsSameObject());
        FilterMap filterMaps[] = context.findFilterMaps();

        // If there are no filter mappings, we are done
        if (filterMaps == null || filterMaps.length == 0) {
            return filterChain;
        }

        // Acquire the information we will need to match filter mappings
        DispatcherType dispatcher = (DispatcherType) request.getAttribute(Globals.DISPATCHER_TYPE_ATTR);

        String requestPath = FilterUtil.getRequestPath(request);

        String servletName = wrapper.getName();

        // Add the relevant path-mapped filters to this filter chain
        for (FilterMap filterMap : filterMaps) {
            if (!matchDispatcher(filterMap, dispatcher)) {
                continue;
            }
            if (!FilterUtil.matchFiltersURL(filterMap, requestPath)) {
                continue;
            }
            ApplicationFilterConfig filterConfig =
                    (ApplicationFilterConfig) context.findFilterConfig(filterMap.getFilterName());
            if (filterConfig == null) {
                log.warn(sm.getString("applicationFilterFactory.noFilterConfig", filterMap.getFilterName()));
                continue;
            }
            filterChain.addFilter(filterConfig);
        }

        // Add filters that match on servlet name second
        for (FilterMap filterMap : filterMaps) {
            if (!matchDispatcher(filterMap, dispatcher)) {
                continue;
            }
            if (!matchFiltersServlet(filterMap, servletName)) {
                continue;
            }
            ApplicationFilterConfig filterConfig =
                    (ApplicationFilterConfig) context.findFilterConfig(filterMap.getFilterName());
            if (filterConfig == null) {
                log.warn(sm.getString("applicationFilterFactory.noFilterConfig", filterMap.getFilterName()));
                continue;
            }
            filterChain.addFilter(filterConfig);
        }

        // Return the completed filter chain
        return filterChain;
    }


    // -------------------------------------------------------- Private Methods


    /**
     * Return <code>true</code> if the specified servlet name matches the requirements of the specified filter mapping;
     * otherwise return <code>false</code>.
     *
     * @param filterMap   Filter mapping being checked
     * @param servletName Servlet name being checked
     */
    private static boolean matchFiltersServlet(FilterMap filterMap, String servletName) {

        if (servletName == null) {
            return false;
        }
        // Check the specific "*" special servlet name
        else if (filterMap.getMatchAllServletNames()) {
            return true;
        } else {
            String[] servletNames = filterMap.getServletNames();
            for (String name : servletNames) {
                if (servletName.equals(name)) {
                    return true;
                }
            }
            return false;
        }

    }


    /**
     * Convenience method which returns true if the dispatcher type matches the dispatcher types specified in the
     * FilterMap
     */
    private static boolean matchDispatcher(FilterMap filterMap, DispatcherType type) {
        switch (type) {
            case FORWARD:
                if ((filterMap.getDispatcherMapping() & FilterMap.FORWARD) != 0) {
                    return true;
                }
                break;
            case INCLUDE:
                if ((filterMap.getDispatcherMapping() & FilterMap.INCLUDE) != 0) {
                    return true;
                }
                break;
            case REQUEST:
                if ((filterMap.getDispatcherMapping() & FilterMap.REQUEST) != 0) {
                    return true;
                }
                break;
            case ERROR:
                if ((filterMap.getDispatcherMapping() & FilterMap.ERROR) != 0) {
                    return true;
                }
                break;
            case ASYNC:
                if ((filterMap.getDispatcherMapping() & FilterMap.ASYNC) != 0) {
                    return true;
                }
                break;
        }
        return false;
    }
}
