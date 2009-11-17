/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tomcat.addons;

import java.io.IOException;
import java.security.Principal;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Plugin for user auth.
 * 
 * This interface should support all common forms of auth, 
 * including Basic, Digest, Form and various other auth 
 * standards - the plugin has full control over request and
 * response.  
 * 
 * Container will verify the security constraints on URLs and 
 * call this for all URLs that have constraints. The plugin can
 * either authenticate and return the principal, or change 
 * the response - redirect, add headers, send content. 
 * 
 * Alternative: a simple Filter can do the same, with some conventions
 * to support it ( attributes ).
 * 
 * @author Costin Manolache
 */
public interface UserAuthentication {

    /**
     * If req has all the info - return the principal.
     * Otherwise set the challenge in response.
     * 
     * @param requestedMethod auth method from web.xml. Spec
     *  complain plugins must support it. 
     * @throws IOException 
     */
    public Principal authenticate(HttpServletRequest req, 
                                  HttpServletResponse res, 
                                  String requestedMethod) throws IOException;
    
    
    public boolean isUserInRole(HttpServletRequest req,
                                Principal p,
                                String role);
}
