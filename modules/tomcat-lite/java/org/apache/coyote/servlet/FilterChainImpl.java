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


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * Wraps the list of filters for the current request. One instance 
 * associated with each RequestImpl, reused. 
 * 
 * Populated by the mapper ( WebappFilterMapper for example ), which
 * determines the filters for the current request.
 * 
 * Not thread safe.
 */
public final class FilterChainImpl implements FilterChain {
    private List<FilterConfigImpl> filters =  new ArrayList<FilterConfigImpl>();


    /**
     * The int which is used to maintain the current position 
     * in the filter chain.
     */
    private int pos = 0;

    /**
     * The servlet instance to be executed by this chain.
     */
    private Servlet servlet = null;


    private ServletConfigImpl wrapper;


    public FilterChainImpl() {
        super();
    }


    /**
     * Invoke the next filter in this chain, passing the specified request
     * and response.  If there are no more filters in this chain, invoke
     * the <code>service()</code> method of the servlet itself.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet exception occurs
     */
    public void doFilter(ServletRequest request, ServletResponse response)
        throws IOException, ServletException {


        // Call the next filter if there is one
        if (pos < filters.size()) {
            FilterConfigImpl filterConfig = filters.get(pos++);
            Filter filter = null;
            try {
                filter = filterConfig.getFilter();
                filter.doFilter(request, response, this);
            } catch (IOException e) {
                throw e;
            } catch (ServletException e) {
                throw e;
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable e) {
                e.printStackTrace();
                throw new ServletException("Throwable", e);
            }
            return;
        }

        // We fell off the end of the chain -- call the servlet instance
        try {
            if (servlet != null) 
                servlet.service(request, response);
        } catch (IOException e) {
            throw e;
        } catch (ServletException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new ServletException("Throwable", e);
        }
    }


    // -------------------------------------------------------- Package Methods



    /**
     * Add a filter to the set of filters that will be executed in this chain.
     *
     * @param filterConfig The FilterConfig for the servlet to be executed
     */
    public void addFilter(FilterConfigImpl filterConfig) {
        filters.add(filterConfig);
    }


    /**
     * Release references to the filters and wrapper executed by this chain.
     */
    public void release() {
        filters.clear();
        pos = 0;
        servlet = null;
    }


    /**
     * Set the servlet that will be executed at the end of this chain.
     * Set by the mapper filter 
     */
    public void setServlet(ServletConfigImpl wrapper, Servlet servlet) {
        this.wrapper = wrapper;
        this.servlet = servlet;
    }

    // ------ Getters for information ------------ 
    
    public int getSize() {
        return filters.size();
    }
    
    public FilterConfigImpl getFilter(int i) {
        return filters.get(i);
    }
    
    public Servlet getServlet() {
        return servlet;
    }
    
    public ServletConfigImpl getServletConfig() {
        return wrapper;
    }
    
    public int getPos() {
        return pos;
    }
}
