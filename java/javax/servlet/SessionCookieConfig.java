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
package javax.servlet;

/**
 * 
 * @since 3.0
 * $Id$
 */
public class SessionCookieConfig {
    private String domain;
    private String path;
    private String comment;
    private boolean httpOnly;
    private boolean secure;

    /**
     * 
     * @param domain      Domain to use for session cookies generated for a
     *                    {@link ServletContext} in which this
     *                    {@link SessionCookieConfig} has been set
     * @param path        Path to use for session cookies generated for a
     *                    {@link ServletContext} in which this
     *                    {@link SessionCookieConfig} has been set. If null
     *                    {@link ServletContext#getContextPath()} is used
     * @param comment     Comment to use for session cookies generated for a
     *                    {@link ServletContext} in which this
     *                    {@link SessionCookieConfig} has been set
     * @param isHttpOnly  HttpOnly flag to use for session cookies generated for
     *                    a {@link ServletContext} in which this
     *                    {@link SessionCookieConfig} has been set
     * @param isSecure    If <code>true</code>, the cookie will always be marked
     *                    as secure. If <code>false</code> the cookie will only
     *                    be marked as secure if the request is secure.
     */
    public SessionCookieConfig(String domain, String path, String comment,
            boolean isHttpOnly, boolean isSecure) {
        this.domain = domain;
        this.path = path;
        this.comment = comment;
        this.httpOnly = isHttpOnly;
        this.secure = isSecure;
    }
    
    public java.lang.String getDomain() {
        return domain;
    }
    
    public java.lang.String getPath() {
        return path;
    }
    
    public java.lang.String getComment() {
        return comment;
    }
    
    public boolean isHttpOnly() {
        return httpOnly;
    }
    
    public boolean isSecure() {
        return secure;
    }
}
