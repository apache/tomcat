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


package org.apache.catalina.core;


import javax.servlet.Servlet;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

import org.apache.catalina.Globals;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Request;
import org.apache.catalina.deploy.FilterMap;

/**
 * Factory for the creation and caching of Filters and creationg 
 * of Filter Chains.
 *
 * @author Greg Murray
 * @author Remy Maucherat
 * @version $Revision: 1.0
 */

public final class ApplicationFilterFactory {


    // -------------------------------------------------------------- Constants


    public static final int ERROR = 1;
    public static final Integer ERROR_INTEGER = new Integer(ERROR);
    public static final int FORWARD = 2;
    public static final Integer FORWARD_INTEGER = new Integer(FORWARD);
    public static final int INCLUDE = 4;
    public static final Integer INCLUDE_INTEGER = new Integer(INCLUDE);
    public static final int REQUEST = 8;
    public static final Integer REQUEST_INTEGER = new Integer(REQUEST);

    public static final String DISPATCHER_TYPE_ATTR = 
        Globals.DISPATCHER_TYPE_ATTR;
    public static final String DISPATCHER_REQUEST_PATH_ATTR = 
        Globals.DISPATCHER_REQUEST_PATH_ATTR;

    private static final SecurityManager securityManager = 
        System.getSecurityManager();

    private static ApplicationFilterFactory factory = null;;


    // ----------------------------------------------------------- Constructors


    /*
     * Prevent instanciation outside of the getInstanceMethod().
     */
    private ApplicationFilterFactory() {
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Return the fqctory instance.
     */
    public static ApplicationFilterFactory getInstance() {
        if (factory == null) {
            factory = new ApplicationFilterFactory();
        }
        return factory;
    }


    /**
     * Construct and return a FilterChain implementation that will wrap the
     * execution of the specified servlet instance.  If we should not execute
     * a filter chain at all, return <code>null</code>.
     *
     * @param request The servlet request we are processing
     * @param servlet The servlet instance to be wrapped
     */
    public ApplicationFilterChain createFilterChain
        (ServletRequest request, Wrapper wrapper, Servlet servlet) {

        // get the dispatcher type
        int dispatcher = -1; 
        if (request.getAttribute(DISPATCHER_TYPE_ATTR) != null) {
            Integer dispatcherInt = 
                (Integer) request.getAttribute(DISPATCHER_TYPE_ATTR);
            dispatcher = dispatcherInt.intValue();
        }
        String requestPath = null;
        Object attribute = request.getAttribute(DISPATCHER_REQUEST_PATH_ATTR);
        
        if (attribute != null){
            requestPath = attribute.toString();
        }
        
        HttpServletRequest hreq = null;
        if (request instanceof HttpServletRequest) 
            hreq = (HttpServletRequest)request;
        // If there is no servlet to execute, return null
        if (servlet == null)
            return (null);

        // Create and initialize a filter chain object
        ApplicationFilterChain filterChain = null;
        if ((securityManager == null) && (request instanceof Request)) {
            Request req = (Request) request;
            filterChain = (ApplicationFilterChain) req.getFilterChain();
            if (filterChain == null) {
                filterChain = new ApplicationFilterChain();
                req.setFilterChain(filterChain);
            }
        } else {
            // Security: Do not recycle
            filterChain = new ApplicationFilterChain();
        }

        filterChain.setServlet(servlet);

        filterChain.setSupport
            (((StandardWrapper)wrapper).getInstanceSupport());

        // Acquire the filter mappings for this Context
        StandardContext context = (StandardContext) wrapper.getParent();
        FilterMap filterMaps[] = context.findFilterMaps();

        // If there are no filter mappings, we are done
        if ((filterMaps == null) || (filterMaps.length == 0))
            return (filterChain);

        // Acquire the information we will need to match filter mappings
        String servletName = wrapper.getName();

        int n = 0;

        // Add the relevant path-mapped filters to this filter chain
        for (int i = 0; i < filterMaps.length; i++) {
            if (!matchDispatcher(filterMaps[i] ,dispatcher)) {
                continue;
            }
            if (!matchFiltersURL(filterMaps[i], requestPath))
                continue;
            ApplicationFilterConfig filterConfig = (ApplicationFilterConfig)
                context.findFilterConfig(filterMaps[i].getFilterName());
            if (filterConfig == null) {
                ;       // FIXME - log configuration problem
                continue;
            }
            filterChain.addFilter(filterConfig);
            n++;
        }

        // Add filters that match on servlet name second
        for (int i = 0; i < filterMaps.length; i++) {
            if (!matchDispatcher(filterMaps[i] ,dispatcher)) {
                continue;
            }
            if (!matchFiltersServlet(filterMaps[i], servletName))
                continue;
            ApplicationFilterConfig filterConfig = (ApplicationFilterConfig)
                context.findFilterConfig(filterMaps[i].getFilterName());
            if (filterConfig == null) {
                ;       // FIXME - log configuration problem
                continue;
            }
            filterChain.addFilter(filterConfig);
            n++;
        }

        // Return the completed filter chain
        return (filterChain);

    }


    // -------------------------------------------------------- Private Methods


    /**
     * Return <code>true</code> if the context-relative request path
     * matches the requirements of the specified filter mapping;
     * otherwise, return <code>false</code>.
     *
     * @param filterMap Filter mapping being checked
     * @param requestPath Context-relative request path of this request
     */
    private boolean matchFiltersURL(FilterMap filterMap, String requestPath) {

        // Check the specific "*" special URL pattern, which also matches
        // named dispatches
        if (filterMap.getAllMatch())
            return (true);
        
        if (requestPath == null)
            return (false);

        // Match on context relative request path
        String[] testPaths = filterMap.getURLPatterns();
        
        for (int i = 0; i < testPaths.length; i++) {
            if (matchFiltersURL(testPaths[i], requestPath)) {
                return (true);
            }
        }
        
        // No match
        return (false);
        
    }
    

    /**
     * Return <code>true</code> if the context-relative request path
     * matches the requirements of the specified filter mapping;
     * otherwise, return <code>false</code>.
     *
     * @param testPath URL mapping being checked
     * @param requestPath Context-relative request path of this request
     */
    private boolean matchFiltersURL(String testPath, String requestPath) {
        
        if (testPath == null)
            return (false);

        // Case 1 - Exact Match
        if (testPath.equals(requestPath))
            return (true);

        // Case 2 - Path Match ("/.../*")
        if (testPath.equals("/*"))
            return (true);
        if (testPath.endsWith("/*")) {
            if (testPath.regionMatches(0, requestPath, 0, 
                                       testPath.length() - 2)) {
                if (requestPath.length() == (testPath.length() - 2)) {
                    return (true);
                } else if ('/' == requestPath.charAt(testPath.length() - 2)) {
                    return (true);
                }
            }
            return (false);
        }

        // Case 3 - Extension Match
        if (testPath.startsWith("*.")) {
            int slash = requestPath.lastIndexOf('/');
            int period = requestPath.lastIndexOf('.');
            if ((slash >= 0) && (period > slash) 
                && (period != requestPath.length() - 1)
                && ((requestPath.length() - period) 
                    == (testPath.length() - 1))) {
                return (testPath.regionMatches(2, requestPath, period + 1,
                                               testPath.length() - 2));
            }
        }

        // Case 4 - "Default" Match
        return (false); // NOTE - Not relevant for selecting filters

    }


    /**
     * Return <code>true</code> if the specified servlet name matches
     * the requirements of the specified filter mapping; otherwise
     * return <code>false</code>.
     *
     * @param filterMap Filter mapping being checked
     * @param servletName Servlet name being checked
     */
    private boolean matchFiltersServlet(FilterMap filterMap, 
                                        String servletName) {

        if (servletName == null) {
            return (false);
        } else {
            String[] servletNames = filterMap.getServletNames();
            for (int i = 0; i < servletNames.length; i++) {
                if (servletName.equals(servletNames[i])) {
                    return (true);
                }
            }
            return false;
        }

    }


    /**
     * Convienience method which returns true if  the dispatcher type
     * matches the dispatcher types specified in the FilterMap
     */
    private boolean matchDispatcher(FilterMap filterMap, int dispatcher) {
        switch (dispatcher) {
            case FORWARD : {
                if (filterMap.getDispatcherMapping() == FilterMap.FORWARD ||
                    filterMap.getDispatcherMapping() == FilterMap.FORWARD_ERROR ||
                    filterMap.getDispatcherMapping() == FilterMap.INCLUDE_FORWARD ||
                    filterMap.getDispatcherMapping() == FilterMap.INCLUDE_ERROR_FORWARD ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_FORWARD ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_ERROR_FORWARD ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_ERROR_FORWARD_INCLUDE ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_FORWARD_INCLUDE) {
                        return true;
                }
                break;
            }
            case INCLUDE : {
                if (filterMap.getDispatcherMapping() == FilterMap.INCLUDE ||
                    filterMap.getDispatcherMapping() == FilterMap.INCLUDE_ERROR ||
                    filterMap.getDispatcherMapping() == FilterMap.INCLUDE_FORWARD ||
                    filterMap.getDispatcherMapping() == FilterMap.INCLUDE_ERROR_FORWARD ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_INCLUDE ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_ERROR_INCLUDE ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_ERROR_FORWARD_INCLUDE ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_FORWARD_INCLUDE) {
                        return true;
                }
                break;
            }
            case REQUEST : {
                if (filterMap.getDispatcherMapping() == FilterMap.REQUEST ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_ERROR ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_INCLUDE ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_ERROR_INCLUDE ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_FORWARD ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_ERROR_FORWARD ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_FORWARD_INCLUDE ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_ERROR_FORWARD_INCLUDE) {
                        return true;
                }
                break;
            }
            case ERROR : {
                if (filterMap.getDispatcherMapping() == FilterMap.ERROR ||
                    filterMap.getDispatcherMapping() == FilterMap.FORWARD_ERROR || 
                    filterMap.getDispatcherMapping() == FilterMap.INCLUDE_ERROR || 
                    filterMap.getDispatcherMapping() == FilterMap.INCLUDE_ERROR_FORWARD || 
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_ERROR ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_ERROR_FORWARD ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_ERROR_FORWARD_INCLUDE ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_ERROR_INCLUDE) {
                        return true;
                }
                break;
            }
        }
        return false;
    }


}
