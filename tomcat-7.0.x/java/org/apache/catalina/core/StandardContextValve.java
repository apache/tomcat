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

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Container;
import org.apache.catalina.Wrapper;
import org.apache.catalina.comet.CometEvent;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
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

final class StandardContextValve
    extends ValveBase {

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
            notFound(response);
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
            notFound(response);
            return;
        } else if (wrapper.isUnavailable()) {
            // May be as a result of a reload, try and find the new wrapper
            wrapper = (Wrapper) container.findChild(wrapper.getName());
            if (wrapper == null) {
                notFound(response);
                return;
            }
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
                context.fireRequestDestroyEvent(request);
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

        // Normal request processing
        // FIXME: Firing request listeners could be an addition to the core
        // comet API
        
        //if (context.fireRequestInitEvent(request)) {
            wrapper.getPipeline().getFirst().event(request, response, event);
        //    context.fireRequestDestroyEvent(request);
        //}
    }


    // -------------------------------------------------------- Private Methods


    /**
     * Report a "not found" error for the specified resource.  FIXME:  We
     * should really be using the error reporting settings for this web
     * application, but currently that code runs at the wrapper level rather
     * than the context level.
     *
     * @param response The response we are creating
     */
    private void notFound(HttpServletResponse response) {

        try {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        } catch (IllegalStateException e) {
            // Ignore
        } catch (IOException e) {
            // Ignore
        }

    }


}
