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

import javax.servlet.SessionCookieConfig;
import javax.servlet.http.Cookie;

import org.apache.catalina.Globals;

public class ApplicationSessionCookieConfig implements SessionCookieConfig {

    private boolean httpOnly;
    private boolean secure;
    private int maxAge = -1;
    private String comment;
    private String domain;
    private String name;
    private String path;
    
    @Override
    public String getComment() {
        return comment;
    }

    @Override
    public String getDomain() {
        return domain;
    }

    @Override
    public int getMaxAge() {
        return maxAge;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public boolean isHttpOnly() {
        return httpOnly;
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public void setComment(String comment) {
        this.comment = comment;
    }

    @Override
    public void setDomain(String domain) {
        this.domain = domain;
    }

    @Override
    public void setHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
    }

    @Override
    public void setMaxAge(int maxAge) {
        this.maxAge = maxAge;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    /**
     * Creates a new session cookie for the given session ID
     *
     * @param scc         The default session cookie configuration
     * @param sessionId   The ID of the session for which the cookie will be
     *                    created
     * @param secure      Should session cookie be configured as secure
     * @param httpOnly    Should session cookie be configured as httpOnly
     * @param emptyPath   Should session cookie be configured with empty path
     * @param contextPath Context path to use if required       
     * @param domain      Domain to use for the session cookie. If null, use the
     *                    domain specified by the scc parameter.
     */
    public static Cookie createSessionCookie(SessionCookieConfig scc,
            String sessionId, boolean secure, boolean httpOnly,
            boolean emptyPath, String contextPath, String domain) {

       // Session config can over-ride default name  
       String cookieName = scc.getName();
       if (cookieName == null) {
           cookieName = Globals.SESSION_COOKIE_NAME;
       }
       Cookie cookie = new Cookie(cookieName, sessionId);
       
       // Just apply the defaults.
       cookie.setMaxAge(scc.getMaxAge());
       cookie.setComment(scc.getComment());
       
       if (domain == null) {
           // Avoid possible NPE
           if (scc.getDomain() != null) {
               cookie.setDomain(scc.getDomain());
           }
       } else {
           cookie.setDomain(domain);
       }

       // Always set secure if the request is secure
       if (scc.isSecure() || secure) {
           cookie.setSecure(true);
       }

       // Always set httpOnly if the context is configured for that
       if (scc.isHttpOnly() || httpOnly) {
           cookie.setHttpOnly(true);
       }
       
       // Don't set the path if the connector is configured to over-ride
       if (!emptyPath && scc.getPath() != null) {
           cookie.setPath(scc.getPath());
       } else {
           if (!emptyPath && contextPath != null && (contextPath.length() > 0)) {
               cookie.setPath(contextPath);
           } else {
               cookie.setPath("/");
           }
       }
       return cookie;
   }
   
 
}
