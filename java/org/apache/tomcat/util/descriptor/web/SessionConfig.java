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
import java.util.Map;
import java.util.TreeMap;

import jakarta.servlet.SessionTrackingMode;

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
    private final Map<String,String> cookieAttributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
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
        return getCookieAttribute(Constants.COOKIE_DOMAIN_ATTR);
    }

    /**
     * Sets the cookie domain.
     * @param cookieDomain the cookie domain
     */
    public void setCookieDomain(String cookieDomain) {
        setCookieAttribute(Constants.COOKIE_DOMAIN_ATTR, cookieDomain);
    }

    /**
     * Returns the cookie path.
     * @return the cookie path
     */
    public String getCookiePath() {
        return getCookieAttribute(Constants.COOKIE_PATH_ATTR);
    }

    /**
     * Sets the cookie path.
     * @param cookiePath the cookie path
     */
    public void setCookiePath(String cookiePath) {
        setCookieAttribute(Constants.COOKIE_PATH_ATTR, cookiePath);
    }

    /**
     * Returns the cookie comment.
     * @return the cookie comment
     */
    public String getCookieComment() {
        return getCookieAttribute(Constants.COOKIE_COMMENT_ATTR);
    }

    /**
     * Sets the cookie comment.
     * @param cookieComment the cookie comment
     */
    public void setCookieComment(String cookieComment) {
        setCookieAttribute(Constants.COOKIE_COMMENT_ATTR, cookieComment);
    }

    /**
     * Returns the cookie HTTP-only flag.
     * @return the HTTP-only flag
     */
    public Boolean getCookieHttpOnly() {
        String httpOnly = getCookieAttribute(Constants.COOKIE_HTTP_ONLY_ATTR);
        if (httpOnly == null) {
            return null;
        }
        return Boolean.valueOf(httpOnly);
    }

    /**
     * Sets the cookie HTTP-only flag.
     * @param cookieHttpOnly the HTTP-only flag
     */
    public void setCookieHttpOnly(String cookieHttpOnly) {
        setCookieAttribute(Constants.COOKIE_HTTP_ONLY_ATTR, cookieHttpOnly);
    }

    /**
     * Returns the cookie secure flag.
     * @return the secure flag
     */
    public Boolean getCookieSecure() {
        String secure = getCookieAttribute(Constants.COOKIE_SECURE_ATTR);
        if (secure == null) {
            return null;
        }
        return Boolean.valueOf(secure);
    }

    /**
     * Sets the cookie secure flag.
     * @param cookieSecure the secure flag
     */
    public void setCookieSecure(String cookieSecure) {
        setCookieAttribute(Constants.COOKIE_SECURE_ATTR, cookieSecure);
    }

    /**
     * Returns the cookie max age.
     * @return the max age
     */
    public Integer getCookieMaxAge() {
        String maxAge = getCookieAttribute(Constants.COOKIE_MAX_AGE_ATTR);
        if (maxAge == null) {
            return null;
        }
        return Integer.valueOf(maxAge);
    }

    /**
     * Sets the cookie max age.
     * @param cookieMaxAge the max age
     */
    public void setCookieMaxAge(String cookieMaxAge) {
        setCookieAttribute(Constants.COOKIE_MAX_AGE_ATTR, cookieMaxAge);
    }

    /**
     * Returns the cookie attributes map.
     * @return the cookie attributes
     */
    public Map<String,String> getCookieAttributes() {
        return cookieAttributes;
    }

    /**
     * Sets a cookie attribute.
     * @param name the attribute name
     * @param value the attribute value
     */
    public void setCookieAttribute(String name, String value) {
        cookieAttributes.put(name, value);
    }

    /**
     * Returns a cookie attribute value.
     * @param name the attribute name
     * @return the attribute value
     */
    public String getCookieAttribute(String name) {
        return cookieAttributes.get(name);
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
