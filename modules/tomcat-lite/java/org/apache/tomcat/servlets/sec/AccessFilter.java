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


package org.apache.tomcat.servlets.sec;


import java.io.IOException;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;



/**
 * Access control. 
 * 
 * In Catalina, the AuthenticatorBase.invoke() will apply the security
 * filters based on LoginConfig. All constraints are applied in the 
 * valve. For sessions - the Principal will be cached. The TTL needs to be
 * indicated by the authenticator.
 * 
 * This works differently - it's a regular filter, could be modified and 
 * set in web.xml explicitely, or set by the container when reading web.xml
 * 
 * 
 * Mappings:
 * 
 *  /[FORM]/j_security_check ( with j_password, j_username params )
 *   -> authentication servlet
 *   Assert: no other j_security_check mapping
 *   
 *  
 * For each security rule we define one AccessFilter, configured with the 
 * right init-params.  
 * 
 * 1. For each security_constraint, create a (base) filter named: _access_nnn, 
 * and add init-params for roles 
 * 
 * 1.1 For each web-resource-collection, take the method set, sort it. Create
 * one filter for each set of methods, named _access_nnn_methodlist
 * 
 * 2.For each pattern in each web-resource-collection, add a mapping to 
 * the appropriate filter, at the end of web.xml ( after normal filters
 * and servlets ).
 *   
 * 
 * @author Costin Manolache
 */
public class AccessFilter implements Filter {
  
    // web-resource-collection: name -> url+method rules
    // Since filters don't match method, for each method subset we need a new
    // filter instance
    private String[] methods; 
    
    // Wildcard on roles - anyone authenticated can access the resource
    private boolean allRoles = false;

    // if false - no access control needed. 
    // if true - must check roles - either allRoles or specific list
    private boolean authConstraint = false;

    // roles to check
    private String authRoles[] = new String[0];

    /**
     * The user data constraint for this security constraint.  Must be NONE,
     * INTEGRAL, or CONFIDENTIAL.
     */
    private String userConstraint = "NONE";

    
    public void destroy() {
    }

    public static class AuthToken {
        public String headerValue; // key
        public long expiry; 
        public Principal principal;
    }

    Map cachedTokens = new HashMap();

    UserAuthentication auth;
    String method;
    
    public void doFilter(ServletRequest request, 
                         ServletResponse servletResponse, 
                         FilterChain chain) 
            throws IOException, ServletException {
        
        HttpServletResponse res = (HttpServletResponse)servletResponse;
        HttpServletRequest req = (HttpServletRequest)request;

        String method = req.getMethod();
        // exclude the context path
        String uri = req.getServletPath() + req.getPathInfo(); 
        
        // no authorization or principal not found. 
        // Call the auth servlet.
        // TODO: use URL or RD.include.
        
        Principal p = auth.authenticate(req, res, method);
        // set the principal on req!
        if (p == null) {
            // couldn't authenticate - response is set.
            return;
        } else {
            // we are ok - cache the response, forward
            
            chain.doFilter(req, res);
        }
        
    }



    public void init(FilterConfig filterConfig) throws ServletException {
    }


}
