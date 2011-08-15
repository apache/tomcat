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
import java.security.AccessController;
import java.security.PrivilegedAction;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.comet.CometEvent;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.tomcat.util.res.StringManager;


/**
 * Valve that implements the default basic behavior for the
 * <code>StandardHost</code> container implementation.
 * <p>
 * <b>USAGE CONSTRAINT</b>:  This implementation is likely to be useful only
 * when processing HTTP requests.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @version $Id$
 */

final class StandardHostValve extends ValveBase {

    protected static final boolean STRICT_SERVLET_COMPLIANCE;

    protected static final boolean ACCESS_SESSION;

    static {
        STRICT_SERVLET_COMPLIANCE = Globals.STRICT_SERVLET_COMPLIANCE;
        
        String accessSession = System.getProperty(
                "org.apache.catalina.core.StandardHostValve.ACCESS_SESSION");
        if (accessSession == null) {
            ACCESS_SESSION = STRICT_SERVLET_COMPLIANCE;
        } else {
            ACCESS_SESSION =
                Boolean.valueOf(accessSession).booleanValue();
        }
    }

    //------------------------------------------------------ Constructor
    public StandardHostValve() {
        super(true);
    }

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
    @Override
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
     *
     * @exception IOException if an input/output error occurred
     * @exception ServletException if a servlet error occurred
     */
    @Override
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
            if (Globals.IS_SECURITY_ENABLED) {
                PrivilegedAction<Void> pa = new PrivilegedSetTccl(
                        context.getLoader().getClassLoader());
                AccessController.doPrivileged(pa);                
            } else {
                Thread.currentThread().setContextClassLoader
                        (context.getLoader().getClassLoader());
            }
        }
        if (request.isAsyncSupported()) {
            request.setAsyncSupported(context.getPipeline().isAsyncSupported());
        }


        // Ask this Context to process this request
        context.getPipeline().getFirst().invoke(request, response);

        // Access a session (if present) to update last accessed time, based on a
        // strict interpretation of the specification
        if (ACCESS_SESSION) {
            request.getSession(false);
        }

        // Restore the context classloader
        if (Globals.IS_SECURITY_ENABLED) {
            PrivilegedAction<Void> pa = new PrivilegedSetTccl(
                    StandardHostValve.class.getClassLoader());
            AccessController.doPrivileged(pa);                
        } else {
            Thread.currentThread().setContextClassLoader
                    (StandardHostValve.class.getClassLoader());
        }

    }


    /**
     * Process Comet event.
     *
     * @param request Request to be processed
     * @param response Response to be produced
     * @param event the event
     *
     * @exception IOException if an input/output error occurred
     * @exception ServletException if a servlet error occurred
     */
    @Override
    public final void event(Request request, Response response, CometEvent event)
        throws IOException, ServletException {

        // Select the Context to be used for this Request
        Context context = request.getContext();

        // Bind the context CL to the current thread
        if( context.getLoader() != null ) {
            // Not started - it should check for availability first
            // This should eventually move to Engine, it's generic.
            Thread.currentThread().setContextClassLoader
                    (context.getLoader().getClassLoader());
        }

        // Ask this Context to process this request
        context.getPipeline().getFirst().event(request, response, event);

        // Access a session (if present) to update last accessed time, based on a
        // strict interpretation of the specification
        if (ACCESS_SESSION) {
            request.getSession(false);
        }

        // Restore the context classloader
        Thread.currentThread().setContextClassLoader
            (StandardHostValve.class.getClassLoader());

    }


    private static class PrivilegedSetTccl implements PrivilegedAction<Void> {

        private ClassLoader cl;

        PrivilegedSetTccl(ClassLoader cl) {
            this.cl = cl;
        }

        @Override
        public Void run() {
            Thread.currentThread().setContextClassLoader(cl);
            return null;
        }
    }
}
