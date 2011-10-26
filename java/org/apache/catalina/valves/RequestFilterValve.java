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
package org.apache.catalina.valves;


import java.io.IOException;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;

/**
 * Implementation of a Valve that performs filtering based on comparing the
 * appropriate request property (selected based on which subclass you choose
 * to configure into your Container's pipeline) against the regular expressions
 * configured for this Valve.
 * <p>
 * This valve is configured by setting the <code>allow</code> and/or
 * <code>deny</code> properties to a regular expressions (in the syntax
 * supported by {@link Pattern}) to which the appropriate request property will
 * be compared. Evaluation proceeds as follows:
 * <ul>
 * <li>The subclass extracts the request property to be filtered, and
 *     calls the common <code>process()</code> method.
 * <li>If there is a deny expression configured, the property will be compared
 *     to the expression. If a match is found, this request will be rejected
 *     with a "Forbidden" HTTP response.</li>
 * <li>If there is a allow expression configured, the property will be compared
 *     to each such expression.  If a match is found, this request will be
 *     allowed to pass through to the next Valve in the current pipeline.</li>
 * <li>If a deny expression was specified but no allow expression, allow this
 *     request to pass through (because none of the deny expressions matched
 *     it).
 * <li>The request will be rejected with a "Forbidden" HTTP response.</li>
 * </ul>
 * <p>
 * This Valve may be attached to any Container, depending on the granularity
 * of the filtering you wish to perform.
 *
 * @author Craig R. McClanahan
 * @version $Id$
 */
public abstract class RequestFilterValve extends ValveBase {

    //------------------------------------------------------ Constructor
    public RequestFilterValve() {
        super(true);
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * The regular expression used to test for allowed requests.
     */
    protected volatile Pattern allow = null;

    /**
     * Helper variable to catch configuration errors.
     * It is <code>true</code> by default, but becomes <code>false</code>
     * if there was an attempt to assign an invalid value to the
     * <code>allow</code> pattern.
     */
    protected volatile boolean allowValid = true;


    /**
     * The regular expression used to test for denied requests.
     */
    protected volatile Pattern deny = null;

    /**
     * Helper variable to catch configuration errors.
     * It is <code>true</code> by default, but becomes <code>false</code>
     * if there was an attempt to assign an invalid value to the
     * <code>deny</code> pattern.
     */
    protected volatile boolean denyValid = true;


    // ------------------------------------------------------------- Properties


    /**
     * Return the regular expression used to test for allowed requests for this
     * Valve, if any; otherwise, return <code>null</code>.
     */
    public String getAllow() {
        // Use local copies for thread safety
        Pattern allow = this.allow;
        if (allow == null) {
            return null;
        }
        return allow.toString();
    }


    /**
     * Set the regular expression used to test for allowed requests for this
     * Valve, if any.
     *
     * @param allow The new allow expression
     */
    public void setAllow(String allow) {
        if (allow == null || allow.length() == 0) {
            this.allow = null;
            allowValid = true;
        } else {
            boolean success = false;
            try {
                this.allow = Pattern.compile(allow);
                success = true;
            } finally {
                allowValid = success;
            }
        }
    }


    /**
     * Return the regular expression used to test for denied requests for this
     * Valve, if any; otherwise, return <code>null</code>.
     */
    public String getDeny() {
        // Use local copies for thread safety
        Pattern deny = this.deny;
        if (deny == null) {
            return null;
        }
        return deny.toString();
    }


    /**
     * Set the regular expression used to test for denied requests for this
     * Valve, if any.
     *
     * @param deny The new deny expression
     */
    public void setDeny(String deny) {
        if (deny == null || deny.length() == 0) {
            this.deny = null;
            denyValid = true;
        } else {
            boolean success = false;
            try {
                this.deny = Pattern.compile(deny);
                success = true;
            } finally {
                denyValid = success;
            }
        }
    }


    // --------------------------------------------------------- Public Methods

    /**
     * Extract the desired request property, and pass it (along with the
     * specified request and response objects) to the protected
     * <code>process()</code> method to perform the actual filtering.
     * This method must be implemented by a concrete subclass.
     *
     * @param request The servlet request to be processed
     * @param response The servlet response to be created
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    @Override
    public abstract void invoke(Request request, Response response)
        throws IOException, ServletException;


    // ------------------------------------------------------ Protected Methods


    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();
        if (!allowValid || !denyValid) {
            throw new LifecycleException(
                    sm.getString("requestFilterValve.configInvalid"));
        }
    }


    /**
     * Perform the filtering that has been configured for this Valve, matching
     * against the specified request property.
     *
     * @param property The request property on which to filter
     * @param request The servlet request to be processed
     * @param response The servlet response to be processed
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    protected void process(String property,
                           Request request, Response response)
        throws IOException, ServletException {

        // Use local copies for thread safety
        Pattern deny = this.deny;
        Pattern allow = this.allow;

        // Check the deny patterns, if any
        if (deny != null && deny.matcher(property).matches()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // Check the allow patterns, if any
        if (allow != null && allow.matcher(property).matches()) {
            getNext().invoke(request, response);
            return;
        }

        // Allow if denies specified but not allows
        if (deny != null && allow == null) {
            getNext().invoke(request, response);
            return;
        }

        // Deny this request
        response.sendError(HttpServletResponse.SC_FORBIDDEN);

    }
}
