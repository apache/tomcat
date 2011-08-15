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


import java.io.IOException;

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.comet.CometEvent;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.deploy.ErrorPage;
import org.apache.catalina.valves.ValveBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.MessageBytes;

/**
 * Valve that implements the default basic behavior for the
 * <code>StandardContext</code> container implementation.
 * <p>
 * <b>USAGE CONSTRAINT</b>:  This implementation is likely to be useful only
 * when processing HTTP requests.
 *
 * @author Craig R. McClanahan
 * @version $Id$
 */

final class StandardContextValve extends ValveBase {
    
    private static final Log log = LogFactory.getLog(StandardHostValve.class);

    //------------------------------------------------------ Constructor
    public StandardContextValve() {
        super(true);
    }

    // ----------------------------------------------------- Instance Variables


    /**
     * The descriptive information related to this implementation.
     */
    private static final String info =
        "org.apache.catalina.core.StandardContextValve/1.0";


    private StandardContext context = null;
    

    // ------------------------------------------------------------- Properties


    /**
     * Return descriptive information about this Valve implementation.
     */
    @Override
    public String getInfo() {

        return (info);

    }


    // --------------------------------------------------------- Public Methods


    /**
     * Cast to a StandardContext right away, as it will be needed later.
     * 
     * @see org.apache.catalina.Contained#setContainer(org.apache.catalina.Container)
     */
    @Override
    public void setContainer(Container container) {
        super.setContainer(container);
        context = (StandardContext) container;
    }

    
    /**
     * Select the appropriate child Wrapper to process this request,
     * based on the specified request URI.  If no matching Wrapper can
     * be found, return an appropriate HTTP error.
     *
     * @param request Request to be processed
     * @param response Response to be produced
     *
     * @exception IOException if an input/output error occurred
     * @exception ServletException if a servlet error occurred
     */
    @Override
    public final void invoke(Request request, Response response)
        throws IOException, ServletException {

        // Disallow any direct access to resources under WEB-INF or META-INF
        MessageBytes requestPathMB = request.getRequestPathMB();
        if ((requestPathMB.startsWithIgnoreCase("/META-INF/", 0))
                || (requestPathMB.equalsIgnoreCase("/META-INF"))
                || (requestPathMB.startsWithIgnoreCase("/WEB-INF/", 0))
                || (requestPathMB.equalsIgnoreCase("/WEB-INF"))) {
            error(request, response, HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // Wait if we are reloading
        boolean reloaded = false;
        while (context.getPaused()) {
            reloaded = true;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // Ignore
            }
        }

        // Reloading will have stopped the old webappclassloader and
        // created a new one
        if (reloaded &&
                context.getLoader() != null &&
                context.getLoader().getClassLoader() != null) {
            Thread.currentThread().setContextClassLoader(
                    context.getLoader().getClassLoader());
        }

        // Select the Wrapper to be used for this Request
        Wrapper wrapper = request.getWrapper();
        if (wrapper == null) {
            error(request, response, HttpServletResponse.SC_NOT_FOUND);
            return;
        } else if (wrapper.isUnavailable()) {
            // May be as a result of a reload, try and find the new wrapper
            wrapper = (Wrapper) container.findChild(wrapper.getName());
            if (wrapper == null) {
                error(request, response, HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        }

        // Acknowledge the request
        try {
            response.sendAcknowledgement();
        } catch (IOException ioe) {
            container.getLogger().error(sm.getString(
                    "standardContextValve.acknowledgeException"), ioe);
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, ioe);
            error(request, response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        
        // Don't fire listeners during async processing
        // If a request init listener throws an exception, the request is
        // aborted
        boolean asyncAtStart = request.isAsync(); 
        if (asyncAtStart || context.fireRequestInitEvent(request)) {
            if (request.isAsyncSupported()) {
                request.setAsyncSupported(wrapper.getPipeline().isAsyncSupported());
            }
            wrapper.getPipeline().getFirst().invoke(request, response);

            // If the request was async at the start and an error occurred then
            // the async error handling will kick-in and that will fire the
            // request destroyed event *after* the error handling has taken
            // place
            if (!(request.isAsync() || (asyncAtStart && request.getAttribute(
                        RequestDispatcher.ERROR_EXCEPTION) != null))) {
                // Protect against NPEs if context was destroyed during a long
                // running request.
                StandardContext c = context;
                if (c != null && c.getState().isAvailable()) {
                    // Error page processing
                    response.setSuspended(false);

                    Throwable t = (Throwable) request.getAttribute(
                            RequestDispatcher.ERROR_EXCEPTION);

                    if (t != null) {
                        throwable(request, response, t);
                    } else {
                        status(request, response);
                    }

                    context.fireRequestDestroyEvent(request);
                }
            }
        }
    }


    /**
     * Select the appropriate child Wrapper to process this request,
     * based on the specified request URI.  If no matching Wrapper can
     * be found, return an appropriate HTTP error.
     *
     * @param request Request to be processed
     * @param response Response to be produced
     * @param event
     *
     * @exception IOException if an input/output error occurred
     * @exception ServletException if a servlet error occurred
     */
    @Override
    public final void event(Request request, Response response, CometEvent event)
        throws IOException, ServletException {

        // Select the Wrapper to be used for this Request
        Wrapper wrapper = request.getWrapper();

        wrapper.getPipeline().getFirst().event(request, response, event);
            
        // Error page processing
        response.setSuspended(false);

        Throwable t = (Throwable) request.getAttribute(
                RequestDispatcher.ERROR_EXCEPTION);

        if (t != null) {
            throwable(request, response, t);
        } else {
            status(request, response);
        }
    }


    // -------------------------------------------------------- Private Methods


    /**
     * Report an error for the specified resource.
     *
     * @param response The response we are creating
     */
    private void error(Request request, Response response, int status) {

        context.fireRequestInitEvent(request);
        
        try {
            response.sendError(status);
        } catch (IllegalStateException e) {
            // Ignore
        } catch (IOException e) {
            // Ignore
        }

        response.setSuspended(false);
        status(request, response);
        
        context.fireRequestDestroyEvent(request);
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
    private void status(Request request, Response response) {

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
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE,
                              Integer.valueOf(statusCode));

            String message = response.getMessage();
            if (message == null)
                message = "";
            request.setAttribute(RequestDispatcher.ERROR_MESSAGE, message);
            request.setAttribute
                (ApplicationFilterFactory.DISPATCHER_REQUEST_PATH_ATTR,
                 errorPage.getLocation());
            request.setAttribute(ApplicationFilterFactory.DISPATCHER_TYPE_ATTR,
                              DispatcherType.ERROR);


            Wrapper wrapper = request.getWrapper();
            if (wrapper != null)
                request.setAttribute(RequestDispatcher.ERROR_SERVLET_NAME,
                                  wrapper.getName());
            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI,
                                 request.getRequestURI());
            if (custom(request, response, errorPage)) {
                try {
                    response.flushBuffer();
                } catch (ClientAbortException e) {
                    // Ignore
                } catch (IOException e) {
                    container.getLogger().warn("Exception Processing " + errorPage, e);
                }
            }
        }
    }

    
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
    private void throwable(Request request, Response response,
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
                              DispatcherType.ERROR);
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE,
                    new Integer(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
            request.setAttribute(RequestDispatcher.ERROR_MESSAGE,
                              throwable.getMessage());
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION,
                              realError);
            Wrapper wrapper = request.getWrapper();
            if (wrapper != null)
                request.setAttribute(RequestDispatcher.ERROR_SERVLET_NAME,
                                  wrapper.getName());
            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI,
                                 request.getRequestURI());
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE,
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
    private boolean custom(Request request, Response response,
                             ErrorPage errorPage) {

        if (container.getLogger().isDebugEnabled())
            container.getLogger().debug("Processing " + errorPage);

        request.setPathInfo(errorPage.getLocation());

        try {
            // Forward control to the specified location
            ServletContext servletContext =
                request.getContext().getServletContext();
            RequestDispatcher rd =
                servletContext.getRequestDispatcher(errorPage.getLocation());

            if (response.isCommitted()) {
                // Response is committed - including the error page is the
                // best we can do 
                rd.include(request.getRequest(), response.getResponse());
            } else {
                // Reset the response (keeping the real error code and message)
                response.resetBuffer(true);

                rd.forward(request.getRequest(), response.getResponse());

                // If we forward, the response is suspended again
                response.setSuspended(false);
            }

            // Indicate that we have successfully processed this custom page
            return (true);

        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // Report our failure to process this custom page
            container.getLogger().error("Exception Processing " + errorPage, t);
            return (false);

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
    private static ErrorPage findErrorPage
        (Context context, Throwable exception) {

        if (exception == null)
            return (null);
        Class<?> clazz = exception.getClass();
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
}
