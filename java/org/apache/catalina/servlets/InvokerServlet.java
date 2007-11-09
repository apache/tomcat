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


import java.io.IOException;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.ContainerServlet;
import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Wrapper;
import org.apache.catalina.util.StringManager;


/**
 * The default servlet-invoking servlet for most web applications,
 * used to serve requests to servlets that have not been registered
 * in the web application deployment descriptor.
 *
 * @author Craig R. McClanahan
 * @version $Revision$ $Date$
 */

public final class InvokerServlet
    extends HttpServlet implements ContainerServlet {


    // ----------------------------------------------------- Instance Variables


    /**
     * The Context container associated with our web application.
     */
    private Context context = null;


    /**
     * The debugging detail level for this servlet.
     */
    private int debug = 0;


    /**
     * The string manager for this package.
     */
    private static StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * The Wrapper container associated with this servlet.
     */
    private Wrapper wrapper = null;


    // ----------------------------------------------- ContainerServlet Methods


    /**
     * Return the Wrapper with which we are associated.
     */
    public Wrapper getWrapper() {

        return (this.wrapper);

    }


    /**
     * Set the Wrapper with which we are associated.
     *
     * @param wrapper The new wrapper
     */
    public void setWrapper(Wrapper wrapper) {

        this.wrapper = wrapper;
        if (wrapper == null)
            context = null;
        else
            context = (Context) wrapper.getParent();

    }


    // --------------------------------------------------------- Public Methods


    /**
     * Finalize this servlet.
     */
    public void destroy() {

        ;       // No actions necessary

    }


    /**
     * Process a GET request for the specified resource.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
        throws IOException, ServletException {

        serveRequest(request, response);

    }


    /**
     * Process a HEAD request for the specified resource.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    public void doHead(HttpServletRequest request,
                       HttpServletResponse response)
        throws IOException, ServletException {

        serveRequest(request, response);

    }


    /**
     * Process a POST request for the specified resource.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
        throws IOException, ServletException {

        serveRequest(request, response);

    }


    /**
     * Initialize this servlet.
     */
    public void init() throws ServletException {

        // Ensure that our ContainerServlet properties have been set
        if ((wrapper == null) || (context == null))
            throw new UnavailableException
                (sm.getString("invokerServlet.noWrapper"));

        // Set our properties from the initialization parameters
        if (getServletConfig().getInitParameter("debug") != null)
            debug = Integer.parseInt(getServletConfig().getInitParameter("debug"));

        if (debug >= 1)
            log("init: Associated with Context '" + context.getPath() + "'");

    }



    // -------------------------------------------------------- Private Methods


    /**
     * Serve the specified request, creating the corresponding response.
     * After the first time a particular servlet class is requested, it will
     * be served directly (like any registered servlet) because it will have
     * been registered and mapped in our associated Context.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    public void serveRequest(HttpServletRequest request,
                             HttpServletResponse response)
        throws IOException, ServletException {

        // Disallow calling this servlet via a named dispatcher
        if (request.getAttribute(Globals.NAMED_DISPATCHER_ATTR) != null)
            throw new ServletException
                (sm.getString("invokerServlet.notNamed"));

        // Identify the input parameters and our "included" state
        String inRequestURI = null;
        String inServletPath = null;
        String inPathInfo = null;
        boolean included =
            (request.getAttribute(Globals.INCLUDE_REQUEST_URI_ATTR) != null);

        if (included) {
            inRequestURI =
                (String) request.getAttribute(Globals.INCLUDE_REQUEST_URI_ATTR);
            inServletPath =
                (String) request.getAttribute(Globals.INCLUDE_SERVLET_PATH_ATTR);
            inPathInfo =
                (String) request.getAttribute(Globals.INCLUDE_PATH_INFO_ATTR);
        } else {
            inRequestURI = request.getRequestURI();
            inServletPath = request.getServletPath();
            inPathInfo = request.getPathInfo();
        }
        if (debug >= 1) {
            log("included='" + included + "', requestURI='" +
                inRequestURI + "'");
            log("  servletPath='" + inServletPath + "', pathInfo='" +
                inPathInfo + "'");
        }

        // Make sure a servlet name or class name was specified
        if (inPathInfo == null) {
            if (debug >= 1)
                log("Invalid pathInfo '" + inPathInfo + "'");
            if (included)
                throw new ServletException
                    (sm.getString("invokerServlet.invalidPath", inRequestURI));
            else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                                   inRequestURI);
                return;
            }
        }

        // Identify the outgoing servlet name or class, and outgoing path info
        String pathInfo = inPathInfo;
        String servletClass = pathInfo.substring(1);
        int slash = servletClass.indexOf('/');
        if (slash >= 0) {
            pathInfo = servletClass.substring(slash);
            servletClass = servletClass.substring(0, slash);
        } else {
            pathInfo = "";
        }

        if (servletClass.startsWith("org.apache.catalina")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                               inRequestURI);
            return;
        }

        if (debug >= 1)
            log("Processing servlet '" + servletClass +
                "' with path info '" + pathInfo + "'");
        String name = "org.apache.catalina.INVOKER." + servletClass;
        String pattern = inServletPath + "/" + servletClass + "/*";
        Wrapper wrapper = null;

        // Synchronize to avoid race conditions when multiple requests
        // try to initialize the same servlet at the same time
        synchronized (this) {

            // Are we referencing an existing servlet class or name?
            wrapper = (Wrapper) context.findChild(servletClass);
            if (wrapper == null)
                wrapper = (Wrapper) context.findChild(name);
            if (wrapper != null) {
                String actualServletClass = wrapper.getServletClass();
                if ((actualServletClass != null)
                    && (actualServletClass.startsWith
                        ("org.apache.catalina"))) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND,
                                       inRequestURI);
                    return;
                }
                if (debug >= 1)
                    log("Using wrapper for servlet '" +
                        wrapper.getName() + "' with mapping '" +
                        pattern + "'");
                context.addServletMapping(pattern, wrapper.getName());
            }

            // No, create a new wrapper for the specified servlet class
            else {

                if (debug >= 1)
                    log("Creating wrapper for '" + servletClass +
                        "' with mapping '" + pattern + "'");

                try {
                    wrapper = context.createWrapper();
                    wrapper.setName(name);
                    wrapper.setLoadOnStartup(1);
                    wrapper.setServletClass(servletClass);
                    context.addChild(wrapper);
                    context.addServletMapping(pattern, name);
                } catch (Exception e) {
                    log(sm.getString("invokerServlet.cannotCreate",
                                     inRequestURI), e);
                    context.removeServletMapping(pattern);
                    context.removeChild(wrapper);
                    if (included)
                        throw new ServletException
                            (sm.getString("invokerServlet.cannotCreate",
                                          inRequestURI), e);
                    else {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND,
                                           inRequestURI);
                        return;
                    }
                }
            }

        }

        // Create a request wrapper to pass on to the invoked servlet
        InvokerHttpRequest wrequest =
            new InvokerHttpRequest(request);
        wrequest.setRequestURI(inRequestURI);
        StringBuffer sb = new StringBuffer(inServletPath);
        sb.append("/");
        sb.append(servletClass);
        wrequest.setServletPath(sb.toString());
        if ((pathInfo == null) || (pathInfo.length() < 1)) {
            wrequest.setPathInfo(null);
            wrequest.setPathTranslated(null);
        } else {
            wrequest.setPathInfo(pathInfo);
            wrequest.setPathTranslated
                (getServletContext().getRealPath(pathInfo));
        }

        // Allocate a servlet instance to perform this request
        Servlet instance = null;
        try {
            instance = wrapper.allocate();
        } catch (ServletException e) {
            log(sm.getString("invokerServlet.allocate", inRequestURI), e);
            context.removeServletMapping(pattern);
            context.removeChild(wrapper);
            Throwable rootCause = e.getRootCause();
            if (rootCause == null)
                rootCause = e;
            if (rootCause instanceof ClassNotFoundException) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                                   inRequestURI);
                return;
            } else if (rootCause instanceof IOException) {
                throw (IOException) rootCause;
            } else if (rootCause instanceof RuntimeException) {
                throw (RuntimeException) rootCause;
            } else if (rootCause instanceof ServletException) {
                throw (ServletException) rootCause;
            } else {
                throw new ServletException
                    (sm.getString("invokerServlet.allocate", inRequestURI),
                     rootCause);
            }
        }

        // After loading the wrapper, restore some of the fields when including
        if (included) {
            wrequest.setRequestURI(request.getRequestURI());
            wrequest.setPathInfo(request.getPathInfo());
            wrequest.setServletPath(request.getServletPath());
        }

        // Invoke the service() method of the allocated servlet
        try {
            String jspFile = wrapper.getJspFile();
            if (jspFile != null)
                request.setAttribute(Globals.JSP_FILE_ATTR, jspFile);
            else
                request.removeAttribute(Globals.JSP_FILE_ATTR);
            request.setAttribute(Globals.INVOKED_ATTR,
                                 request.getServletPath());
            instance.service(wrequest, response);
        } catch (UnavailableException e) {
            context.removeServletMapping(pattern);
            throw e;
        } finally {
            request.removeAttribute(Globals.INVOKED_ATTR);
            request.removeAttribute(Globals.JSP_FILE_ATTR);
            // Deallocate the allocated servlet instance
            try {
                wrapper.deallocate(instance);
            } catch (ServletException e) {
                log(sm.getString("invokerServlet.deallocate", inRequestURI), e);
                throw e;
            }
        }

    }


}
