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
package org.apache.jasper.servlet;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.jasper.EmbeddedServletOptions;
import org.apache.jasper.Options;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.runtime.ExceptionUtils;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.PeriodicEventListener;
import org.apache.tomcat.util.security.Escape;

/**
 * The JSP engine (a.k.a Jasper).
 *
 * The servlet container is responsible for providing a
 * URLClassLoader for the web application context Jasper
 * is being used in. Jasper will try get the Tomcat
 * ServletContext attribute for its ServletContext class
 * loader, if that fails, it uses the parent class loader.
 * In either case, it must be a URLClassLoader.
 *
 * @author Anil K. Vijendran
 * @author Harish Prabandham
 * @author Remy Maucherat
 * @author Kin-man Chung
 * @author Glenn Nielsen
 */
public class JspServlet extends HttpServlet implements PeriodicEventListener {

    private static final long serialVersionUID = 1L;

    // Logger
    private final transient Log log = LogFactory.getLog(JspServlet.class);

    private transient ServletContext context;
    private ServletConfig config;
    private transient Options options;
    private transient JspRuntimeContext rctxt;
    // jspFile for a jsp configured explicitly as a servlet, in environments where this
    // configuration is translated into an init-param for this servlet.
    private String jspFile;


    @Override
    public void init(ServletConfig config) throws ServletException {

        super.init(config);
        this.config = config;
        this.context = config.getServletContext();

        // Initialize the JSP Runtime Context
        // Check for a custom Options implementation
        String engineOptionsName = config.getInitParameter("engineOptionsClass");
        if (engineOptionsName != null) {
            // Instantiate the indicated Options implementation
            try {
                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                Class<?> engineOptionsClass = loader.loadClass(engineOptionsName);
                Class<?>[] ctorSig = { ServletConfig.class, ServletContext.class };
                Constructor<?> ctor = engineOptionsClass.getConstructor(ctorSig);
                Object[] args = { config, context };
                options = (Options) ctor.newInstance(args);
            } catch (Throwable e) {
                e = ExceptionUtils.unwrapInvocationTargetException(e);
                ExceptionUtils.handleThrowable(e);
                // Need to localize this.
                log.warn(Localizer.getMessage("jsp.warning.engineOptionsClass", engineOptionsName), e);
                // Use the default Options implementation
                options = new EmbeddedServletOptions(config, context);
            }
        } else {
            // Use the default Options implementation
            options = new EmbeddedServletOptions(config, context);
        }
        rctxt = new JspRuntimeContext(context, options);
        if (config.getInitParameter("jspFile") != null) {
            jspFile = config.getInitParameter("jspFile");
            try {
                if (null == context.getResource(jspFile)) {
                    return;
                }
            } catch (MalformedURLException e) {
                throw new ServletException(Localizer.getMessage("jsp.error.no.jsp", jspFile), e);
            }
            try {
                serviceJspFile(null, null, jspFile, true);
            } catch (IOException e) {
                throw new ServletException(Localizer.getMessage("jsp.error.precompilation", jspFile), e);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug(Localizer.getMessage("jsp.message.scratch.dir.is",
                    options.getScratchDir().toString()));
            log.debug(Localizer.getMessage("jsp.message.dont.modify.servlets"));
        }
    }


    /**
     * Returns the number of JSPs for which JspServletWrappers exist, i.e.,
     * the number of JSPs that have been loaded into the webapp with which
     * this JspServlet is associated.
     *
     * <p>This info may be used for monitoring purposes.
     *
     * @return The number of JSPs that have been loaded into the webapp with
     * which this JspServlet is associated
     */
    public int getJspCount() {
        return this.rctxt.getJspCount();
    }


    /**
     * Resets the JSP reload counter.
     *
     * @param count Value to which to reset the JSP reload counter
     */
    public void setJspReloadCount(int count) {
        this.rctxt.setJspReloadCount(count);
    }


    /**
     * Gets the number of JSPs that have been reloaded.
     *
     * <p>This info may be used for monitoring purposes.
     *
     * @return The number of JSPs (in the webapp with which this JspServlet is
     * associated) that have been reloaded
     */
    public int getJspReloadCount() {
        return this.rctxt.getJspReloadCount();
    }


    /**
     * Gets the number of JSPs that are in the JSP limiter queue
     *
     * <p>This info may be used for monitoring purposes.
     *
     * @return The number of JSPs (in the webapp with which this JspServlet is
     * associated) that are in the JSP limiter queue
     */
    public int getJspQueueLength() {
        return this.rctxt.getJspQueueLength();
    }


    /**
     * Gets the number of JSPs that have been unloaded.
     *
     * <p>This info may be used for monitoring purposes.
     *
     * @return The number of JSPs (in the webapp with which this JspServlet is
     * associated) that have been unloaded
     */
    public int getJspUnloadCount() {
        return this.rctxt.getJspUnloadCount();
    }


    /**
     * <p>Look for a <em>precompilation request</em> as described in
     * Section 8.4.2 of the JSP 1.2 Specification.  <strong>WARNING</strong> -
     * we cannot use <code>request.getParameter()</code> for this, because
     * that will trigger parsing all of the request parameters, and not give
     * a servlet the opportunity to call
     * <code>request.setCharacterEncoding()</code> first.</p>
     *
     * @param request The servlet request we are processing
     *
     * @exception ServletException if an invalid parameter value for the
     *  <code>jsp_precompile</code> parameter name is specified
     */
    boolean preCompile(HttpServletRequest request) throws ServletException {

        String precompileParameter = rctxt.getOptions().getJspPrecompilationQueryParameter();
        String queryString = request.getQueryString();
        if (queryString == null) {
            return false;
        }
        int start = queryString.indexOf(precompileParameter);
        if (start < 0) {
            return false;
        }
        queryString =
            queryString.substring(start + precompileParameter.length());
        if (queryString.length() == 0) {
            return true;             // ?jsp_precompile
        }
        if (queryString.startsWith("&")) {
            return true;             // ?jsp_precompile&foo=bar...
        }
        if (!queryString.startsWith("=")) {
            return false;            // part of some other name or value
        }
        int limit = queryString.length();
        int ampersand = queryString.indexOf('&');
        if (ampersand > 0) {
            limit = ampersand;
        }
        String value = queryString.substring(1, limit);
        if (value.equals("true")) {
            return true;             // ?jsp_precompile=true
        } else if (value.equals("false")) {
            // Spec says if jsp_precompile=false, the request should not
            // be delivered to the JSP page; the easiest way to implement
            // this is to set the flag to true, and precompile the page anyway.
            // This still conforms to the spec, since it says the
            // precompilation request can be ignored.
            return true;             // ?jsp_precompile=false
        } else {
            throw new ServletException(Localizer.getMessage("jsp.error.precompilation.parameter",
                    precompileParameter, value));
        }

    }


    @Override
    public void service (HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // jspFile may be configured as an init-param for this servlet instance
        String jspUri = jspFile;

        if (jspUri == null) {
            /*
             * Check to see if the requested JSP has been the target of a
             * RequestDispatcher.include()
             */
            jspUri = (String) request.getAttribute(
                    RequestDispatcher.INCLUDE_SERVLET_PATH);
            if (jspUri != null) {
                /*
                 * Requested JSP has been target of
                 * RequestDispatcher.include(). Its path is assembled from the
                 * relevant jakarta.servlet.include.* request attributes
                 */
                String pathInfo = (String) request.getAttribute(
                        RequestDispatcher.INCLUDE_PATH_INFO);
                if (pathInfo != null) {
                    jspUri += pathInfo;
                }
            } else {
                /*
                 * Requested JSP has not been the target of a
                 * RequestDispatcher.include(). Reconstruct its path from the
                 * request's getServletPath() and getPathInfo()
                 */
                jspUri = request.getServletPath();
                String pathInfo = request.getPathInfo();
                if (pathInfo != null) {
                    jspUri += pathInfo;
                }
            }
        }

        if (log.isTraceEnabled()) {
            log.trace("JspEngine --> " + jspUri);
            log.trace("\t     ServletPath: " + request.getServletPath());
            log.trace("\t        PathInfo: " + request.getPathInfo());
            log.trace("\t        RealPath: " + context.getRealPath(jspUri));
            log.trace("\t      RequestURI: " + request.getRequestURI());
            log.trace("\t     QueryString: " + request.getQueryString());
        }

        try {
            boolean precompile = preCompile(request);
            serviceJspFile(request, response, jspUri, precompile);
        } catch (RuntimeException | IOException | ServletException e) {
            throw e;
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            throw new ServletException(e);
        }

    }

    @Override
    public void destroy() {
        if (log.isTraceEnabled()) {
            log.trace("JspServlet.destroy()");
        }

        rctxt.destroy();
    }


    @Override
    public void periodicEvent() {
        rctxt.checkUnload();
        rctxt.checkCompile();
    }

    // -------------------------------------------------------- Private Methods

    private void serviceJspFile(HttpServletRequest request,
                                HttpServletResponse response, String jspUri,
                                boolean precompile)
        throws ServletException, IOException {

        JspServletWrapper wrapper = rctxt.getWrapper(jspUri);
        if (wrapper == null) {
            synchronized(this) {
                wrapper = rctxt.getWrapper(jspUri);
                if (wrapper == null) {
                    // Check if the requested JSP page exists, to avoid
                    // creating unnecessary directories and files.
                    if (null == context.getResource(jspUri)) {
                        handleMissingResource(request, response, jspUri);
                        return;
                    }
                    wrapper = new JspServletWrapper(config, options, jspUri,
                                                    rctxt);
                    rctxt.addWrapper(jspUri,wrapper);
                }
            }
        }

        try {
            wrapper.service(request, response, precompile);
        } catch (FileNotFoundException fnfe) {
            handleMissingResource(request, response, jspUri);
        }

    }


    private void handleMissingResource(HttpServletRequest request,
            HttpServletResponse response, String jspUri)
            throws ServletException, IOException {

        String includeRequestUri =
            (String)request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI);

        String msg = Localizer.getMessage("jsp.error.file.not.found",jspUri);
        if (includeRequestUri != null) {
            // This file was included. Throw an exception as
            // a response.sendError() will be ignored
            // Strictly, filtering this is an application
            // responsibility but just in case...
            throw new ServletException(Escape.htmlElementContent(msg));
        } else {
            try {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, msg);
            } catch (IllegalStateException ise) {
                log.error(msg);
            }
        }
    }


}
