/*
 * Copyright 1999-2001,2004 The Apache Software Foundation.
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


import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.deploy.ErrorPage;
import org.apache.catalina.util.RequestUtil;
import org.apache.catalina.util.StringManager;
import org.apache.catalina.valves.ValveBase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * Valve that implements the default basic behavior for the
 * <code>StandardHost</code> container implementation.
 * <p>
 * <b>USAGE CONSTRAINT</b>:  This implementation is likely to be useful only
 * when processing HTTP requests.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @version $Revision: 304105 $ $Date: 2005-09-27 21:20:54 +0200 (mar., 27 sept. 2005) $
 */

final class StandardHostValve
    extends ValveBase {


    private static Log log = LogFactory.getLog(StandardHostValve.class);

    // ----------------------------------------------------- Instance Variables


    /**
     * The descriptive information related to this implementation.
     */
    private static final String info =
        "org.apache.catalina.core.StandardHostValve/1.0";


    /**
     * The string manager for this package.
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);


    // ------------------------------------------------------------- Properties


    /**
     * Return descriptive information about this Valve implementation.
     */
    public String getInfo() {

        return (info);

    }


    // --------------------------------------------------------- Public Methods


    /**
     * Select the appropriate child Context to process this request,
     * based on the specified request URI.  If no matching Context can
     * be found, return an appropriate HTTP error.
     *
     * @param request Request to be processed
     * @param response Response to be produced
     * @param valveContext Valve context used to forward to the next Valve
     *
     * @exception IOException if an input/output error occurred
     * @exception ServletException if a servlet error occurred
     */
    public final void invoke(Request request, Response response)
        throws IOException, ServletException {

        // Select the Context to be used for this Request
        Context context = request.getContext();
        if (context == null) {
            response.sendError
                (HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                 sm.getString("standardHost.noContext"));
            return;
        }

        // Bind the context CL to the current thread
        if( context.getLoader() != null ) {
            // Not started - it should check for availability first
            // This should eventually move to Engine, it's generic.
            Thread.currentThread().setContextClassLoader
                    (context.getLoader().getClassLoader());
        }

        // Ask this Context to process this request
        context.getPipeline().getFirst().invoke(request, response);

        // Error page processing
        response.setSuspended(false);

        Throwable t = (Throwable) request.getAttribute(Globals.EXCEPTION_ATTR);

        if (t != null) {
            throwable(request, response, t);
        } else {
            status(request, response);
        }

        // Restore the context classloader
        Thread.currentThread().setContextClassLoader
            (StandardHostValve.class.getClassLoader());

    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Handle the specified Throwable encountered while processing
     * the specified Request to produce the specified Response.  Any
     * exceptions that occur during generation of the exception report are
     * logged and swallowed.
     *
     * @param request The request being processed
     * @param response The response being generated
     * @param throwable The exception that occurred (which possibly wraps
     *  a root cause exception
     */
    protected void throwable(Request request, Response response,
                             Throwable throwable) {
        Context context = request.getContext();
        if (context == null)
            return;

        Throwable realError = throwable;

        if (realError instanceof ServletException) {
            realError = ((ServletException) realError).getRootCause();
            if (realError == null) {
                realError = throwable;
            }
        }

        // If this is an aborted request from a client just log it and return
        if (realError instanceof ClientAbortException ) {
            if (log.isDebugEnabled()) {
                log.debug
                    (sm.getString("standardHost.clientAbort",
                        realError.getCause().getMessage()));
            }
            return;
        }

        ErrorPage errorPage = findErrorPage(context, throwable);
        if ((errorPage == null) && (realError != throwable)) {
            errorPage = findErrorPage(context, realError);
        }

        if (errorPage != null) {
            response.setAppCommitted(false);
            request.setAttribute
                (ApplicationFilterFactory.DISPATCHER_REQUEST_PATH_ATTR,
                 errorPage.getLocation());
            request.setAttribute(ApplicationFilterFactory.DISPATCHER_TYPE_ATTR,
                              new Integer(ApplicationFilterFactory.ERROR));
            request.setAttribute
                (Globals.STATUS_CODE_ATTR,
                 new Integer(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
            request.setAttribute(Globals.ERROR_MESSAGE_ATTR,
                              throwable.getMessage());
            request.setAttribute(Globals.EXCEPTION_ATTR,
                              realError);
            Wrapper wrapper = request.getWrapper();
            if (wrapper != null)
                request.setAttribute(Globals.SERVLET_NAME_ATTR,
                                  wrapper.getName());
            request.setAttribute(Globals.EXCEPTION_PAGE_ATTR,
                                 request.getRequestURI());
            request.setAttribute(Globals.EXCEPTION_TYPE_ATTR,
                              realError.getClass());
            if (custom(request, response, errorPage)) {
                try {
                    response.flushBuffer();
                } catch (IOException e) {
                    container.getLogger().warn("Exception Processing " + errorPage, e);
                }
            }
        } else {
            // A custom error-page has not been defined for the exception
            // that was thrown during request processing. Check if an
            // error-page for error code 500 was specified and if so,
            // send that page back as the response.
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            // The response is an error
            response.setError();

            status(request, response);
        }


    }


    /**
     * Handle the HTTP status code (and corresponding message) generated
     * while processing the specified Request to produce the specified
     * Response.  Any exceptions that occur during generation of the error
     * report are logged and swallowed.
     *
     * @param request The request being processed
     * @param response The response being generated
     */
    protected void status(Request request, Response response) {

        int statusCode = response.getStatus();

        // Handle a custom error page for this status code
        Context context = request.getContext();
        if (context == null)
            return;

        /* Only look for error pages when isError() is set.
         * isError() is set when response.sendError() is invoked. This
         * allows custom error pages without relying on default from
         * web.xml.
         */
        if (!response.isError())
            return;

        ErrorPage errorPage = context.findErrorPage(statusCode);
        if (errorPage != null) {
            response.setAppCommitted(false);
            request.setAttribute(Globals.STATUS_CODE_ATTR,
                              new Integer(statusCode));

            String message = RequestUtil.filter(response.getMessage());
            if (message == null)
                message = "";
            request.setAttribute(Globals.ERROR_MESSAGE_ATTR, message);
            request.setAttribute
                (ApplicationFilterFactory.DISPATCHER_REQUEST_PATH_ATTR,
                 errorPage.getLocation());
            request.setAttribute(ApplicationFilterFactory.DISPATCHER_TYPE_ATTR,
                              new Integer(ApplicationFilterFactory.ERROR));


            Wrapper wrapper = request.getWrapper();
            if (wrapper != null)
                request.setAttribute(Globals.SERVLET_NAME_ATTR,
                                  wrapper.getName());
            request.setAttribute(Globals.EXCEPTION_PAGE_ATTR,
                                 request.getRequestURI());
            if (custom(request, response, errorPage)) {
                try {
                    response.flushBuffer();
                } catch (IOException e) {
                    container.getLogger().warn("Exception Processing " + errorPage, e);
                }
            }
        }

    }


    /**
     * Find and return the ErrorPage instance for the specified exception's
     * class, or an ErrorPage instance for the closest superclass for which
     * there is such a definition.  If no associated ErrorPage instance is
     * found, return <code>null</code>.
     *
     * @param context The Context in which to search
     * @param exception The exception for which to find an ErrorPage
     */
    protected static ErrorPage findErrorPage
        (Context context, Throwable exception) {

        if (exception == null)
            return (null);
        Class clazz = exception.getClass();
        String name = clazz.getName();
        while (!Object.class.equals(clazz)) {
            ErrorPage errorPage = context.findErrorPage(name);
            if (errorPage != null)
                return (errorPage);
            clazz = clazz.getSuperclass();
            if (clazz == null)
                break;
            name = clazz.getName();
        }
        return (null);

    }


    /**
     * Handle an HTTP status code or Java exception by forwarding control
     * to the location included in the specified errorPage object.  It is
     * assumed that the caller has already recorded any request attributes
     * that are to be forwarded to this page.  Return <code>true</code> if
     * we successfully utilized the specified error page location, or
     * <code>false</code> if the default error report should be rendered.
     *
     * @param request The request being processed
     * @param response The response being generated
     * @param errorPage The errorPage directive we are obeying
     */
    protected boolean custom(Request request, Response response,
                             ErrorPage errorPage) {

        if (container.getLogger().isDebugEnabled())
            container.getLogger().debug("Processing " + errorPage);

        request.setPathInfo(errorPage.getLocation());

        try {

            // Reset the response if possible (else IllegalStateException)
            //hres.reset();
            // Reset the response (keeping the real error code and message)
            Integer statusCodeObj =
                (Integer) request.getAttribute(Globals.STATUS_CODE_ATTR);
            int statusCode = statusCodeObj.intValue();
            String message =
                (String) request.getAttribute(Globals.ERROR_MESSAGE_ATTR);
            response.reset(statusCode, message);

            // Forward control to the specified location
            ServletContext servletContext =
                request.getContext().getServletContext();
            RequestDispatcher rd =
                servletContext.getRequestDispatcher(errorPage.getLocation());
            rd.forward(request.getRequest(), response.getResponse());

            // If we forward, the response is suspended again
            response.setSuspended(false);

            // Indicate that we have successfully processed this custom page
            return (true);

        } catch (Throwable t) {

            // Report our failure to process this custom page
            container.getLogger().error("Exception Processing " + errorPage, t);
            return (false);

        }

    }


}
