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
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.coyote.ActionCode;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.descriptor.web.ErrorPage;
import org.apache.tomcat.util.res.StringManager;

/**
 * Valve that implements the default basic behavior for the <code>StandardHost</code> container implementation.
 * <p>
 * <b>USAGE CONSTRAINT</b>: This implementation is likely to be useful only when processing HTTP requests.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
final class StandardHostValve extends ValveBase {

    private static final Log log = LogFactory.getLog(StandardHostValve.class);
    private static final StringManager sm = StringManager.getManager(StandardHostValve.class);

    // Saves a call to getClassLoader() on very request. Under high load these
    // calls took just long enough to appear as a hot spot (although a very
    // minor one) in a profiler.
    private static final ClassLoader MY_CLASSLOADER = StandardHostValve.class.getClassLoader();

    // ------------------------------------------------------ Constructor

    StandardHostValve() {
        super(true);
    }


    // --------------------------------------------------------- Public Methods

    /**
     * Select the appropriate child Context to process this request, based on the specified request URI. If no matching
     * Context can be found, return an appropriate HTTP error.
     *
     * @param request  Request to be processed
     * @param response Response to be produced
     *
     * @exception IOException      if an input/output error occurred
     * @exception ServletException if a servlet error occurred
     */
    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {

        // Select the Context to be used for this Request
        Context context = request.getContext();
        if (context == null) {
            // Don't overwrite an existing error
            if (!response.isError()) {
                response.sendError(404);
            }
            return;
        }

        if (request.isAsyncSupported()) {
            request.setAsyncSupported(context.getPipeline().isAsyncSupported());
        }

        boolean asyncAtStart = request.isAsync();

        try {
            context.bind(MY_CLASSLOADER);

            if (!asyncAtStart && !context.fireRequestInitEvent(request.getRequest())) {
                // Don't fire listeners during async processing (the listener
                // fired for the request that called startAsync()).
                // If a request init listener throws an exception, the request
                // is aborted.
                return;
            }

            // Ask this Context to process this request. Requests that are
            // already in error must have been routed here to check for
            // application defined error pages so DO NOT forward them to the
            // application for processing.
            try {
                if (!response.isErrorReportRequired()) {
                    context.getPipeline().getFirst().invoke(request, response);
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                container.getLogger().error(sm.getString("standardHostValve.exception", request.getRequestURI()), t);
                // If a new error occurred while trying to report a previous
                // error allow the original error to be reported.
                if (!response.isErrorReportRequired()) {
                    request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, t);
                    throwable(request, response, t);
                }
            }

            // Now that the request/response pair is back under container
            // control lift the suspension so that the error handling can
            // complete and/or the container can flush any remaining data
            response.setSuspended(false);

            Throwable t = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

            // Protect against NPEs if the context was destroyed during a
            // long running request.
            if (!context.getState().isAvailable()) {
                return;
            }

            // Look for (and render if found) an application level error page
            if (response.isErrorReportRequired()) {
                // If an error has occurred that prevents further I/O, don't waste time
                // producing an error report that will never be read
                AtomicBoolean result = new AtomicBoolean(false);
                response.getCoyoteResponse().action(ActionCode.IS_IO_ALLOWED, result);
                if (result.get()) {
                    if (t != null) {
                        throwable(request, response, t);
                    } else {
                        status(request, response);
                    }
                }
            }

            if (!request.isAsync() && !asyncAtStart) {
                context.fireRequestDestroyEvent(request.getRequest());
            }
        } finally {
            // Access a session (if present) to update last accessed time, based
            // on a strict interpretation of the specification
            if (context.getAlwaysAccessSession()) {
                request.getSession(false);
            }

            context.unbind(MY_CLASSLOADER);
        }
    }


    // -------------------------------------------------------- Private Methods

    /**
     * Handle the HTTP status code (and corresponding message) generated while processing the specified Request to
     * produce the specified Response. Any exceptions that occur during generation of the error report are logged and
     * swallowed.
     *
     * @param request  The request being processed
     * @param response The response being generated
     */
    private void status(Request request, Response response) {

        int statusCode = response.getStatus();

        // Handle a custom error page for this status code
        Context context = request.getContext();
        if (context == null) {
            return;
        }

        /*
         * Only look for error pages when isError() is set. isError() is set when response.sendError() is invoked. This
         * allows custom error pages without relying on default from web.xml.
         */
        if (!response.isError()) {
            return;
        }

        ErrorPage errorPage = context.findErrorPage(statusCode);
        if (errorPage == null) {
            // Look for a default error page
            errorPage = context.findErrorPage(0);
        }
        if (errorPage != null && response.isErrorReportRequired()) {
            response.setAppCommitted(false);
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, Integer.valueOf(statusCode));

            String message = response.getMessage();
            if (message == null) {
                message = "";
            }
            request.setAttribute(RequestDispatcher.ERROR_MESSAGE, message);
            request.setAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR, errorPage.getLocation());
            request.setAttribute(Globals.DISPATCHER_TYPE_ATTR, DispatcherType.ERROR);


            Wrapper wrapper = request.getWrapper();
            if (wrapper != null) {
                request.setAttribute(RequestDispatcher.ERROR_SERVLET_NAME, wrapper.getName());
            }
            request.setAttribute(RequestDispatcher.ERROR_METHOD, request.getMethod());
            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, request.getRequestURI());
            request.setAttribute(RequestDispatcher.ERROR_QUERY_STRING, request.getQueryString());
            if (custom(request, response, errorPage)) {
                response.setErrorReported();
                try {
                    response.finishResponse();
                } catch (ClientAbortException e) {
                    // Ignore
                } catch (IOException e) {
                    container.getLogger().warn(sm.getString("standardHostValve.exception", errorPage), e);
                }
            }
        }
    }


    /**
     * Handle the specified Throwable encountered while processing the specified Request to produce the specified
     * Response. Any exceptions that occur during generation of the exception report are logged and swallowed.
     *
     * @param request   The request being processed
     * @param response  The response being generated
     * @param throwable The exception that occurred (which possibly wraps a root cause exception
     */
    protected void throwable(Request request, Response response, Throwable throwable) {
        Context context = request.getContext();
        if (context == null) {
            return;
        }

        Throwable realError = throwable;

        if (realError instanceof ServletException) {
            realError = ((ServletException) realError).getRootCause();
            if (realError == null) {
                realError = throwable;
            }
        }

        // If this is an aborted request from a client just log it and return
        if (realError instanceof ClientAbortException) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("standardHost.clientAbort", realError.getCause().getMessage()));
            }
            return;
        }

        ErrorPage errorPage = context.findErrorPage(throwable);
        if ((errorPage == null) && (realError != throwable)) {
            errorPage = context.findErrorPage(realError);
        }

        if (errorPage != null) {
            if (response.setErrorReported()) {
                response.setAppCommitted(false);
                request.setAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR, errorPage.getLocation());
                request.setAttribute(Globals.DISPATCHER_TYPE_ATTR, DispatcherType.ERROR);
                request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE,
                        Integer.valueOf(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
                request.setAttribute(RequestDispatcher.ERROR_MESSAGE, throwable.getMessage());
                request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, realError);
                Wrapper wrapper = request.getWrapper();
                if (wrapper != null) {
                    request.setAttribute(RequestDispatcher.ERROR_SERVLET_NAME, wrapper.getName());
                }
                request.setAttribute(RequestDispatcher.ERROR_METHOD, request.getMethod());
                request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, request.getRequestURI());
                request.setAttribute(RequestDispatcher.ERROR_QUERY_STRING, request.getQueryString());
                request.setAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE, realError.getClass());
                if (custom(request, response, errorPage)) {
                    try {
                        response.finishResponse();
                    } catch (IOException e) {
                        container.getLogger().warn(sm.getString("standardHostValve.exception", errorPage), e);
                    }
                }
            }
        } else {
            /*
             * A custom error-page has not been defined for the exception that was thrown during request processing. Set
             * the status to 500 if an error status has not already been set and check for custom error-page for the
             * status.
             */
            if (response.getStatus() < HttpServletResponse.SC_BAD_REQUEST) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            // The response is an error
            response.setError();

            status(request, response);
        }
    }


    /**
     * Handle an HTTP status code or Java exception by forwarding control to the location included in the specified
     * errorPage object. It is assumed that the caller has already recorded any request attributes that are to be
     * forwarded to this page. Return <code>true</code> if we successfully utilized the specified error page location,
     * or <code>false</code> if the default error report should be rendered.
     *
     * @param request   The request being processed
     * @param response  The response being generated
     * @param errorPage The errorPage directive we are obeying
     */
    private boolean custom(Request request, Response response, ErrorPage errorPage) {

        if (container.getLogger().isTraceEnabled()) {
            container.getLogger().trace("Processing " + errorPage);
        }

        try {
            // Forward control to the specified location
            ServletContext servletContext = request.getContext().getServletContext();
            RequestDispatcher rd = servletContext.getRequestDispatcher(errorPage.getLocation());

            if (rd == null) {
                container.getLogger()
                        .error(sm.getString("standardHostValve.customStatusFailed", errorPage.getLocation()));
                return false;
            }

            if (response.isCommitted()) {
                // Response is committed - including the error page is the
                // best we can do
                rd.include(request.getRequest(), response.getResponse());

                // Ensure the combined incomplete response and error page is
                // written to the client
                try {
                    response.flushBuffer();
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                }

                // Now close immediately as an additional signal to the client
                // that something went wrong
                response.getCoyoteResponse().action(ActionCode.CLOSE_NOW,
                        request.getAttribute(RequestDispatcher.ERROR_EXCEPTION));
            } else {
                // Reset the response (keeping the real error code and message)
                response.resetBuffer(true);
                response.setContentLength(-1);

                rd.forward(request.getRequest(), response.getResponse());

                // If we forward, the response is suspended again
                response.setSuspended(false);
            }

            // Indicate that we have successfully processed this custom page
            return true;

        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // Report our failure to process this custom page
            container.getLogger().error(sm.getString("standardHostValve.exception", errorPage), t);
            return false;
        }
    }
}
