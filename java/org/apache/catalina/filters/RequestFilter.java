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


package org.apache.catalina.filters;


import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.comet.CometEvent;
import org.apache.catalina.comet.CometFilter;
import org.apache.catalina.comet.CometFilterChain;

/**
 * Implementation of a Filter that performs filtering based on comparing the
 * appropriate request property (selected based on which subclass you choose
 * to configure into your Container's pipeline) against a set of regular
 * expressions configured for this Filter.
 * <p>
 * This filter is configured by setting the <code>allow</code> and/or
 * <code>deny</code> properties to a comma-delimited list of regular
 * expressions (in the syntax supported by the jakarta-regexp library) to
 * which the appropriate request property will be compared.  Evaluation
 * proceeds as follows:
 * <ul>
 * <li>The subclass extracts the request property to be filtered, and
 *     calls the common <code>process()</code> method.
 * <li>If there are any deny expressions configured, the property will
 *     be compared to each such expression.  If a match is found, this
 *     request will be rejected with a "Forbidden" HTTP response.</li>
 * <li>If there are any allow expressions configured, the property will
 *     be compared to each such expression.  If a match is found, this
 *     request will be allowed to pass through to the next filter in the
 *     current pipeline.</li>
 * <li>If one or more deny expressions was specified but no allow expressions,
 *     allow this request to pass through (because none of the deny
 *     expressions matched it).
 * <li>The request will be rejected with a "Forbidden" HTTP response.</li>
 * </ul>
 * <p>
 * This Filter may be attached to any Container, depending on the granularity
 * of the filtering you wish to perform.
 *
 * @author Craig R. McClanahan
 * 
 */

public abstract class RequestFilter
    extends FilterBase implements CometFilter {


    // ----------------------------------------------------- Instance Variables

    /**
     * The comma-delimited set of <code>allow</code> expressions.
     */
    protected String allow = null;


    /**
     * The set of <code>allow</code> regular expressions we will evaluate.
     */
    protected Pattern allows[] = new Pattern[0];


    /**
     * The set of <code>deny</code> regular expressions we will evaluate.
     */
    protected Pattern denies[] = new Pattern[0];


    /**
     * The comma-delimited set of <code>deny</code> expressions.
     */
    protected String deny = null;
    
    /**
     * mime type -- "text/plain"
     */
    private static final String PLAIN_TEXT_MIME_TYPE = "text/plain";


    // ------------------------------------------------------------- Properties


    /**
     * Return a comma-delimited set of the <code>allow</code> expressions
     * configured for this Filter, if any; otherwise, return <code>null</code>.
     */
    public String getAllow() {

        return (this.allow);

    }


    /**
     * Set the comma-delimited set of the <code>allow</code> expressions
     * configured for this Filter, if any.
     *
     * @param allow The new set of allow expressions
     */
    public void setAllow(String allow) {

        this.allow = allow;
        this.allows = precalculate(allow);
        
    }


    /**
     * Return a comma-delimited set of the <code>deny</code> expressions
     * configured for this Filter, if any; otherwise, return <code>null</code>.
     */
    public String getDeny() {

        return (this.deny);

    }


    /**
     * Set the comma-delimited set of the <code>deny</code> expressions
     * configured for this Filter, if any.
     *
     * @param deny The new set of deny expressions
     */
    public void setDeny(String deny) {


        this.deny = deny;
        this.denies = precalculate(deny);
        
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
     * @param chain The filter chain
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    public abstract void doFilter(ServletRequest request,
            ServletResponse response, FilterChain chain) throws IOException,
            ServletException;

      
    // ------------------------------------------------------ Protected Methods


    /**
     * Return an array of regular expression objects initialized from the
     * specified argument, which must be <code>null</code> or a comma-delimited
     * list of regular expression patterns.
     *
     * @param list The comma-separated list of patterns
     *
     * @exception IllegalArgumentException if one of the patterns has
     *  invalid syntax
     */
    protected Pattern[] precalculate(String list) {

        if (list == null)
            return (new Pattern[0]);
        list = list.trim();
        if (list.length() < 1)
            return (new Pattern[0]);
        list += ",";

        ArrayList<Pattern> reList = new ArrayList<Pattern>();
        while (list.length() > 0) {
            int comma = list.indexOf(',');
            if (comma < 0)
                break;
            String pattern = list.substring(0, comma).trim();
            try {
                reList.add(Pattern.compile(pattern));
            } catch (PatternSyntaxException e) {
                IllegalArgumentException iae = new IllegalArgumentException
                    (sm.getString("requestFilterFilter.syntax", pattern));
                iae.initCause(e);
                throw iae;
            }
            list = list.substring(comma + 1);
        }

        Pattern reArray[] = new Pattern[reList.size()];
        return reList.toArray(reArray);

    }


    /**
     * Perform the filtering that has been configured for this Filter, matching
     * against the specified request property.
     *
     * @param property The request property on which to filter
     * @param request The servlet request to be processed
     * @param response The servlet response to be processed
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    protected void process(String property, ServletRequest request,
            ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (isAllowed(property)) {
            chain.doFilter(request, response);
        } else {
            if (response instanceof HttpServletResponse) {
                ((HttpServletResponse) response)
                        .sendError(HttpServletResponse.SC_FORBIDDEN);
            } else {
                sendErrorWhenNotHttp(response);
            }
        }
    }

    /**
     * Perform the filtering that has been configured for this Filter, matching
     * against the specified request property.
     * 
     * @param property  The property to check against the allow/deny rules
     * @param event     The comet event to be filtered
     * @param chain     The comet filter chain
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    protected void processCometEvent(String property, CometEvent event, CometFilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse response = event.getHttpServletResponse();
        
        if (isAllowed(property)) {
            chain.doFilterEvent(event);
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            event.close();
        }
    }

    /**
     * Process the allow and deny rules for the provided property.
     * 
     * @param property  The property to test against the allow and deny lists
     * @return          <code>true</code> if this request should be allowed,
     *                  <code>false</code> otherwise
     */
    private boolean isAllowed(String property) {
        for (int i = 0; i < this.denies.length; i++) {
            if (this.denies[i].matcher(property).matches()) {
                return false;
            }
        }
     
        // Check the allow patterns, if any
        for (int i = 0; i < this.allows.length; i++) {
            if (this.allows[i].matcher(property).matches()) {
                return true;
            }
        }

        // Allow if denies specified but not allows
        if ((this.denies.length > 0) && (this.allows.length == 0)) {
            return true;
        }

        // Deny this request
        return false;
    }

    private void sendErrorWhenNotHttp(ServletResponse response)
            throws IOException {
        response.setContentType(PLAIN_TEXT_MIME_TYPE);
        response.getWriter().write(sm.getString("http.403"));
        response.getWriter().flush();
    }


}
