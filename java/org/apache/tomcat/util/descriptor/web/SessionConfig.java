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
package org.apache.tomcat.util.descriptor.web;

import java.util.EnumSet;

import javax.servlet.SessionTrackingMode;

/**
 * Representation of a session configuration element for a web application, as represented in a
 * <code>&lt;session-config&gt;</code> element in the deployment descriptor.
 */
public class SessionConfig {

    /**
     * Default constructor.
     */
    public SessionConfig() {
    }

    private Integer sessionTimeout;
    private String cookieName;
    private String cookieDomain;
    private String cookiePath;
    private String cookieComment;
    private Boolean cookieHttpOnly;
    private Boolean cookieSecure;
    private Integer cookieMaxAge;
    private final EnumSet<SessionTrackingMode> sessionTrackingModes = EnumSet.noneOf(SessionTrackingMode.class);

    /**
     * Returns the session timeout.
     * @return the session timeout
     */
    public Integer getSessionTimeout() {
        return sessionTimeout;
    }

    /**
     * Sets the session timeout.
     * @param sessionTimeout the session timeout
     */
    public void setSessionTimeout(String sessionTimeout) {
        this.sessionTimeout = Integer.valueOf(sessionTimeout);
    }

    /**
     * Returns the cookie name.
     * @return the cookie name
     */
    public String getCookieName() {
        return cookieName;
    }

    /**
     * Sets the cookie name.
     * @param cookieName the cookie name
     */
    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    /**
     * Returns the cookie domain.
     * @return the cookie domain
     */
    public String getCookieDomain() {
        return cookieDomain;
    }

    /**
     * Sets the cookie domain.
     * @param cookieDomain the cookie domain
     */
    public void setCookieDomain(String cookieDomain) {
        this.cookieDomain = cookieDomain;
    }

    /**
     * Returns the cookie path.
     * @return the cookie path
     */
    public String getCookiePath() {
        return cookiePath;
    }

    /**
     * Sets the cookie path.
     * @param cookiePath the cookie path
     */
    public void setCookiePath(String cookiePath) {
        this.cookiePath = cookiePath;
    }

    /**
     * Returns the cookie comment.
     * @return the cookie comment
     */
    public String getCookieComment() {
        return cookieComment;
    }

    /**
     * Sets the cookie comment.
     * @param cookieComment the cookie comment
     */
    public void setCookieComment(String cookieComment) {
        this.cookieComment = cookieComment;
    }

    /**
     * Returns the cookie HTTP-only flag.
     * @return the HTTP-only flag
     */
    public Boolean getCookieHttpOnly() {
        return cookieHttpOnly;
    }

    /**
     * Sets the cookie HTTP-only flag.
     * @param cookieHttpOnly the HTTP-only flag
     */
    public void setCookieHttpOnly(String cookieHttpOnly) {
        this.cookieHttpOnly = Boolean.valueOf(cookieHttpOnly);
    }

    /**
     * Returns the cookie secure flag.
     * @return the secure flag
     */
    public Boolean getCookieSecure() {
        return cookieSecure;
    }

    /**
     * Sets the cookie secure flag.
     * @param cookieSecure the secure flag
     */
    public void setCookieSecure(String cookieSecure) {
        this.cookieSecure = Boolean.valueOf(cookieSecure);
    }

    /**
     * Returns the cookie max age.
     * @return the max age
     */
    public Integer getCookieMaxAge() {
        return cookieMaxAge;
    }

    /**
     * Sets the cookie max age.
     * @param cookieMaxAge the max age
     */
    public void setCookieMaxAge(String cookieMaxAge) {
        this.cookieMaxAge = Integer.valueOf(cookieMaxAge);
    }

    /**
     * Returns the session tracking modes.
     * @return the session tracking modes
     */
    public EnumSet<SessionTrackingMode> getSessionTrackingModes() {
        return sessionTrackingModes;
    }

    /**
     * Adds a session tracking mode.
     * @param sessionTrackingMode the tracking mode to add
     */
    public void addSessionTrackingMode(String sessionTrackingMode) {
        sessionTrackingModes.add(SessionTrackingMode.valueOf(sessionTrackingMode));
    }
}
