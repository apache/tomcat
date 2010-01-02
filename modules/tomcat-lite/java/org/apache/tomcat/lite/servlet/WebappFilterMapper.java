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


package org.apache.tomcat.lite.servlet;


import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.tomcat.servlets.util.RequestUtil;

/**
 * First filter after the context and servlet are mapped. It will add 
 * web.xml-defined filters. 
 * 
 * costin: This is another mapping - done in RequestDispatcher or initial 
 * mapping.
 * Also: StandardHostValve - sets attribute for error pages,
 *   StandardWrapperValve - mapping per invocation
 *
 * @author Greg Murray
 * @author Remy Maucherat
 */
public class WebappFilterMapper implements Filter {


    // -------------------------------------------------------------- Constants


    public static final int ERROR = 1;
    public static final Integer ERROR_INTEGER = new Integer(ERROR);
    public static final int FORWARD = 2;
    public static final Integer FORWARD_INTEGER = new Integer(FORWARD);
    public static final int INCLUDE = 4;
    public static final Integer INCLUDE_INTEGER = new Integer(INCLUDE);
    public static final int REQUEST = 8;
    public static final Integer REQUEST_INTEGER = new Integer(REQUEST);

    /**
     * Request dispatcher state.
     */
    public static final String DISPATCHER_TYPE_ATTR = 
        "org.apache.catalina.core.DISPATCHER_TYPE";

    /**
     * Request dispatcher path.
     */
    public static final String DISPATCHER_REQUEST_PATH_ATTR = 
        "org.apache.catalina.core.DISPATCHER_REQUEST_PATH";


    // ----------------------------------------------------------- Constructors
    ServletContextImpl servletContext;

    public WebappFilterMapper() {
    }

    public WebappFilterMapper(ServletContextImpl impl) {
        servletContext = impl;
    }

    public void setServletContext(ServletContextImpl sc) {
        servletContext = sc;
    }

    // --------------------------------------------------------- Public Methods

    ArrayList<FilterMap> filterMaps = new ArrayList();
    
    public void addMapping(String filterName, 
                           String url, 
                           String servletName, 
                           String type[], boolean isMatchAfter) {
        FilterMap map = new FilterMap();
        map.setURLPattern(url);
        map.setFilterName(filterName);
        map.setServletName(servletName);
        if (isMatchAfter) {
            filterMaps.add(map);
        } else {
            filterMaps.add(0, map);
        }
    }

    /**
     * Construct and return a FilterChain implementation that will wrap the
     * execution of the specified servlet instance.  If we should not execute
     * a filter chain at all, return <code>null</code>.
     *
     * @param request The servlet request we are processing
     * @param servlet The servlet instance to be wrapped
     */
    public FilterChainImpl createFilterChain(ServletRequest request, 
                                             ServletConfigImpl wrapper, 
                                             Servlet servlet) {

        // If there is no servlet to execute, return null
        if (servlet == null)
            return (null);

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

        // Create and initialize a filter chain object
        FilterChainImpl filterChain = null;
        if ((request instanceof ServletRequestImpl)) {
            ServletRequestImpl req = (ServletRequestImpl) request;
            filterChain = (FilterChainImpl) req.getFilterChain();
            filterChain.release();
        } else {
            // Security: Do not recycle
            filterChain = new FilterChainImpl();
        }

        filterChain.setServlet(wrapper, servlet);

        // If there are no filter mappings, we are done
        if ((filterMaps.size() == 0))
            return (filterChain);

        // Acquire the information we will need to match filter mappings
        String servletName = wrapper.getServletName();

        int n = 0;

        // TODO(costin): optimize: separate in 2 lists, one for url-mapped, one for
        // servlet-name. Maybe even separate list for dispatcher and 
        // non-dispatcher
        
        // TODO(costin): optimize: set the FilterConfig in the FilterMap, to 
        // avoid second hash lookup
        
        // Add the relevant path-mapped filters to this filter chain
        for (int i = 0; i < filterMaps.size(); i++) {
            FilterMap filterMap = (FilterMap)filterMaps.get(i);
            if (!matchDispatcher(filterMap ,dispatcher)) {
                continue;
            }
            if (!matchFiltersURL(filterMap, requestPath))
                continue;
            FilterConfigImpl filterConfig = 
                servletContext.getFilter(filterMap.getFilterName());
            if (filterConfig == null) {
                // FIXME - log configuration problem
                continue;
            }
            filterChain.addFilter(filterConfig);
            n++;
        }

        // Add filters that match on servlet name second
        for (int i = 0; i < filterMaps.size(); i++) {
            FilterMap filterMap = (FilterMap)filterMaps.get(i);
            if (!matchDispatcher(filterMap ,dispatcher)) {
                continue;
            }
            if (!matchFiltersServlet(filterMap, servletName))
                continue;
            FilterConfigImpl filterConfig = 
                servletContext.getFilter(filterMap.getFilterName());
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
     * otherwise, return <code>null</code>.
     *
     * @param filterMap Filter mapping being checked
     * @param requestPath Context-relative request path of this request
     */
    private boolean matchFiltersURL(FilterMap filterMap, String requestPath) {

        if (requestPath == null)
            return (false);

        // Match on context relative request path
        String testPath = filterMap.getURLPattern();
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
            if (servletName.equals(filterMap.getServletName())) {
                return (true);
            } else {
                return false;
            }
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


    // -------------------- Map elements -----------------------
    
    public static class FilterMap implements Serializable {


        // ------------------------------------------------------------- Properties


        /**
         * The name of this filter to be executed when this mapping matches
         * a particular request.
         */
        
        public static final int ERROR = 1;
        public static final int FORWARD = 2;
        public static final int FORWARD_ERROR =3;  
        public static final int INCLUDE = 4;
        public static final int INCLUDE_ERROR  = 5;
        public static final int INCLUDE_ERROR_FORWARD  =6;
        public static final int INCLUDE_FORWARD  = 7;
        public static final int REQUEST = 8;
        public static final int REQUEST_ERROR = 9;
        public static final int REQUEST_ERROR_FORWARD = 10;
        public static final int REQUEST_ERROR_FORWARD_INCLUDE = 11;
        public static final int REQUEST_ERROR_INCLUDE = 12;
        public static final int REQUEST_FORWARD = 13;
        public static final int REQUEST_INCLUDE = 14;
        public static final int REQUEST_FORWARD_INCLUDE= 15;
        
        // represents nothing having been set. This will be seen 
        // as equal to a REQUEST
        private static final int NOT_SET = -1;
        
        private int dispatcherMapping=NOT_SET;
        
        private String filterName = null;    

        /**
         * The URL pattern this mapping matches.
         */
        private String urlPattern = null;

        /**
         * The servlet name this mapping matches.
         */
        private String servletName = null;



        public String getFilterName() {
            return (this.filterName);
        }

        public void setFilterName(String filterName) {
            this.filterName = filterName;
        }


        public String getServletName() {
            return (this.servletName);
        }

        public void setServletName(String servletName) {
            this.servletName = servletName;
        }


        public String getURLPattern() {
            return (this.urlPattern);
        }

        public void setURLPattern(String urlPattern) {
            this.urlPattern = RequestUtil.URLDecode(urlPattern);
        }
        
        /**
         *
         * This method will be used to set the current state of the FilterMap
         * representing the state of when filters should be applied:
         *
         *        ERROR
         *        FORWARD
         *        FORWARD_ERROR
         *        INCLUDE
         *        INCLUDE_ERROR
         *        INCLUDE_ERROR_FORWARD
         *        REQUEST
         *        REQUEST_ERROR
         *        REQUEST_ERROR_INCLUDE
         *        REQUEST_ERROR_FORWARD_INCLUDE
         *        REQUEST_INCLUDE
         *        REQUEST_FORWARD,
         *        REQUEST_FORWARD_INCLUDE
         *
         */
        public void setDispatcher(String dispatcherString) {
            String dispatcher = dispatcherString.toUpperCase();
            
            if (dispatcher.equals("FORWARD")) {

                // apply FORWARD to the global dispatcherMapping.
                switch (dispatcherMapping) {
                    case NOT_SET  :  dispatcherMapping = FORWARD; break;
                    case ERROR : dispatcherMapping = FORWARD_ERROR; break;
                    case INCLUDE  :  dispatcherMapping = INCLUDE_FORWARD; break;
                    case INCLUDE_ERROR  :  dispatcherMapping = INCLUDE_ERROR_FORWARD; break;
                    case REQUEST : dispatcherMapping = REQUEST_FORWARD; break;
                    case REQUEST_ERROR : dispatcherMapping = REQUEST_ERROR_FORWARD; break;
                    case REQUEST_ERROR_INCLUDE : dispatcherMapping = REQUEST_ERROR_FORWARD_INCLUDE; break;
                    case REQUEST_INCLUDE : dispatcherMapping = REQUEST_FORWARD_INCLUDE; break;
                }
            } else if (dispatcher.equals("INCLUDE")) {
                // apply INCLUDE to the global dispatcherMapping.
                switch (dispatcherMapping) {
                    case NOT_SET  :  dispatcherMapping = INCLUDE; break;
                    case ERROR : dispatcherMapping = INCLUDE_ERROR; break;
                    case FORWARD  :  dispatcherMapping = INCLUDE_FORWARD; break;
                    case FORWARD_ERROR  :  dispatcherMapping = INCLUDE_ERROR_FORWARD; break;
                    case REQUEST : dispatcherMapping = REQUEST_INCLUDE; break;
                    case REQUEST_ERROR : dispatcherMapping = REQUEST_ERROR_INCLUDE; break;
                    case REQUEST_ERROR_FORWARD : dispatcherMapping = REQUEST_ERROR_FORWARD_INCLUDE; break;
                    case REQUEST_FORWARD : dispatcherMapping = REQUEST_FORWARD_INCLUDE; break;
                }
            } else if (dispatcher.equals("REQUEST")) {
                // apply REQUEST to the global dispatcherMapping.
                switch (dispatcherMapping) {
                    case NOT_SET  :  dispatcherMapping = REQUEST; break;
                    case ERROR : dispatcherMapping = REQUEST_ERROR; break;
                    case FORWARD  :  dispatcherMapping = REQUEST_FORWARD; break;
                    case FORWARD_ERROR  :  dispatcherMapping = REQUEST_ERROR_FORWARD; break;
                    case INCLUDE  :  dispatcherMapping = REQUEST_INCLUDE; break;
                    case INCLUDE_ERROR  :  dispatcherMapping = REQUEST_ERROR_INCLUDE; break;
                    case INCLUDE_FORWARD : dispatcherMapping = REQUEST_FORWARD_INCLUDE; break;
                    case INCLUDE_ERROR_FORWARD : dispatcherMapping = REQUEST_ERROR_FORWARD_INCLUDE; break;
                }
            }  else if (dispatcher.equals("ERROR")) {
                // apply ERROR to the global dispatcherMapping.
                switch (dispatcherMapping) {
                    case NOT_SET  :  dispatcherMapping = ERROR; break;
                    case FORWARD  :  dispatcherMapping = FORWARD_ERROR; break;
                    case INCLUDE  :  dispatcherMapping = INCLUDE_ERROR; break;
                    case INCLUDE_FORWARD : dispatcherMapping = INCLUDE_ERROR_FORWARD; break;
                    case REQUEST : dispatcherMapping = REQUEST_ERROR; break;
                    case REQUEST_INCLUDE : dispatcherMapping = REQUEST_ERROR_INCLUDE; break;
                    case REQUEST_FORWARD : dispatcherMapping = REQUEST_ERROR_FORWARD; break;
                    case REQUEST_FORWARD_INCLUDE : dispatcherMapping = REQUEST_ERROR_FORWARD_INCLUDE; break;
                }
            }
        }
        
        public int getDispatcherMapping() {
            // per the SRV.6.2.5 absence of any dispatcher elements is
            // equivelant to a REQUEST value
            if (dispatcherMapping == NOT_SET) return REQUEST;
            else return dispatcherMapping; 
        }

    }


    public void init(FilterConfig filterConfig) throws ServletException {
    }


    public void doFilter(ServletRequest request, ServletResponse response, 
                         FilterChain chain) 
            throws IOException, ServletException {
    }


    public void destroy() {
    }

}
