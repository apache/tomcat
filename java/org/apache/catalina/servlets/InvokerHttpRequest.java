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


package org.apache.catalina.servlets;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;


/**
 * Wrapper around a <code>javax.servlet.http.HttpServletRequest</code>
 * utilized when <code>InvokerServlet</code> processes the initial request
 * for an invoked servlet.  Subsequent requests will be mapped directly
 * to the servlet, because a new servlet mapping will have been created.
 *
 * @author Craig R. McClanahan
 * @version $Revision$ $Date$
 */

class InvokerHttpRequest extends HttpServletRequestWrapper {


    // ----------------------------------------------------------- Constructors


    /**
     * Construct a new wrapped request around the specified servlet request.
     *
     * @param request The servlet request being wrapped
     */
    public InvokerHttpRequest(HttpServletRequest request) {

        super(request);
        this.pathInfo = request.getPathInfo();
        this.pathTranslated = request.getPathTranslated();
        this.requestURI = request.getRequestURI();
        this.servletPath = request.getServletPath();

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Descriptive information about this implementation.
     */
    protected static final String info =
        "org.apache.catalina.servlets.InvokerHttpRequest/1.0";


    /**
     * The path information for this request.
     */
    protected String pathInfo = null;


    /**
     * The translated path information for this request.
     */
    protected String pathTranslated = null;


    /**
     * The request URI for this request.
     */
    protected String requestURI = null;


    /**
     * The servlet path for this request.
     */
    protected String servletPath = null;


    // --------------------------------------------- HttpServletRequest Methods


    /**
     * Override the <code>getPathInfo()</code> method of the wrapped request.
     */
    public String getPathInfo() {

        return (this.pathInfo);

    }


    /**
     * Override the <code>getPathTranslated()</code> method of the
     * wrapped request.
     */
    public String getPathTranslated() {

        return (this.pathTranslated);

    }


    /**
     * Override the <code>getRequestURI()</code> method of the wrapped request.
     */
    public String getRequestURI() {

        return (this.requestURI);

    }


    /**
     * Override the <code>getServletPath()</code> method of the wrapped
     * request.
     */
    public String getServletPath() {

        return (this.servletPath);

    }


    // -------------------------------------------------------- Package Methods



    /**
     * Return descriptive information about this implementation.
     */
    public String getInfo() {

        return (info);

    }


    /**
     * Set the path information for this request.
     *
     * @param pathInfo The new path info
     */
    void setPathInfo(String pathInfo) {

        this.pathInfo = pathInfo;

    }


    /**
     * Set the translated path info for this request.
     *
     * @param pathTranslated The new translated path info
     */
    void setPathTranslated(String pathTranslated) {

        this.pathTranslated = pathTranslated;

    }


    /**
     * Set the request URI for this request.
     *
     * @param requestURI The new request URI
     */
    void setRequestURI(String requestURI) {

        this.requestURI = requestURI;

    }


    /**
     * Set the servlet path for this request.
     *
     * @param servletPath The new servlet path
     */
    void setServletPath(String servletPath) {

        this.servletPath = servletPath;

    }


}
