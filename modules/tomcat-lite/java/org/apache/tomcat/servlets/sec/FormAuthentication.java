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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;



/**
 * Implements Form authentication.
 */
public class FormAuthentication implements UserAuthentication {

    String realm;
    String url;
    
    @Override
    public Principal authenticate(HttpServletRequest request,
                                  HttpServletResponse response,
                                  String requestedMethod) throws IOException {
            
        if (realm == null)
            realm = request.getServerName() + ":" + request.getServerPort();

        // Validate any credentials already included with this request
        String authorization = request.getHeader("authorization");
        if (authorization != null) {
            Principal principal = null; // processDigestHeader(request, authorization);
            if (principal != null) {
                return principal;
            }
        }

        String domain = request.getContextPath();
        String authHeader = getAuthenticateHeader(domain, false);
        
        response.setHeader("WWW-Authenticate", authHeader);
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return null;
    }
    
    
    /** Generate the auth header
     */
    protected String getAuthenticateHeader(String domain,
                                           boolean stale) {

        long currentTime = System.currentTimeMillis();
        return "";
    }

    // ------------------------------------------------------ Protected Methods


    public Principal authenticate(final String username, String clientDigest,
                                  String nOnce, String nc, String cnonce,
                                  String qop, String realm,
                                  String md5a2) {

        
        String serverDigest = null;

        return null;
    }


    @Override
    public boolean isUserInRole(HttpServletRequest req, Principal p, String role) {
        return false;
    }
    
}
