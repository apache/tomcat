/*
 */
package org.apache.tomcat.servlets.sec;

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
