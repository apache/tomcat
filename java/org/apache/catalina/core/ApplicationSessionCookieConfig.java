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

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.http.Cookie;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.util.SessionConfig;
import org.apache.tomcat.util.descriptor.web.Constants;
import org.apache.tomcat.util.res.StringManager;

public class ApplicationSessionCookieConfig implements SessionCookieConfig {

    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(ApplicationSessionCookieConfig.class);

    private static final int DEFAULT_MAX_AGE = -1;
    private static final boolean DEFAULT_HTTP_ONLY = false;
    private static final boolean DEFAULT_SECURE = false;

    private final Map<String,String> attributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    private String name;
    private StandardContext context;

    public ApplicationSessionCookieConfig(StandardContext context) {
        this.context = context;
    }

    @Override
    public String getComment() {
        return null;
    }

    @Override
    public String getDomain() {
        return getAttribute(Constants.COOKIE_DOMAIN_ATTR);
    }

    @Override
    public int getMaxAge() {
        String maxAge = getAttribute(Constants.COOKIE_MAX_AGE_ATTR);
        if (maxAge == null) {
            return DEFAULT_MAX_AGE;
        }
        return Integer.parseInt(maxAge);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPath() {
        return getAttribute(Constants.COOKIE_PATH_ATTR);
    }

    @Override
    public boolean isHttpOnly() {
        String httpOnly = getAttribute(Constants.COOKIE_HTTP_ONLY_ATTR);
        if (httpOnly == null) {
            return DEFAULT_HTTP_ONLY;
        }
        return Boolean.parseBoolean(httpOnly);
    }

    @Override
    public boolean isSecure() {
        String secure = getAttribute(Constants.COOKIE_SECURE_ATTR);
        if (secure == null) {
            return DEFAULT_SECURE;
        }
        return Boolean.parseBoolean(secure);
    }

    @Override
    public void setComment(String comment) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(sm.getString(
                    "applicationSessionCookieConfig.ise", "comment",
                    context.getPath()));
        }
    }

    @Override
    public void setDomain(String domain) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(sm.getString(
                    "applicationSessionCookieConfig.ise", "domain name",
                    context.getPath()));
        }
        setAttribute(Constants.COOKIE_DOMAIN_ATTR, domain);
    }

    @Override
    public void setHttpOnly(boolean httpOnly) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(sm.getString(
                    "applicationSessionCookieConfig.ise", "HttpOnly",
                    context.getPath()));
        }
        setAttribute(Constants.COOKIE_HTTP_ONLY_ATTR, Boolean.toString(httpOnly));
    }

    @Override
    public void setMaxAge(int maxAge) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(sm.getString(
                    "applicationSessionCookieConfig.ise", "max age",
                    context.getPath()));
        }
        setAttribute(Constants.COOKIE_MAX_AGE_ATTR, Integer.toString(maxAge));
    }

    @Override
    public void setName(String name) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(sm.getString(
                    "applicationSessionCookieConfig.ise", "name",
                    context.getPath()));
        }
        this.name = name;
    }

    @Override
    public void setPath(String path) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(sm.getString(
                    "applicationSessionCookieConfig.ise", "path",
                    context.getPath()));
        }
        setAttribute(Constants.COOKIE_PATH_ATTR, path);
    }

    @Override
    public void setSecure(boolean secure) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(sm.getString(
                    "applicationSessionCookieConfig.ise", "secure",
                    context.getPath()));
        }
        setAttribute(Constants.COOKIE_SECURE_ATTR, Boolean.toString(secure));
    }


    @Override
    public void setAttribute(String name, String value) {
        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(sm.getString(
                    "applicationSessionCookieConfig.ise", name,
                    context.getPath()));
        }
        attributes.put(name, value);
    }

    @Override
    public String getAttribute(String name) {
        return attributes.get(name);
    }


    @Override
    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * Creates a new session cookie for the given session ID
     *
     * @param context     The Context for the web application
     * @param sessionId   The ID of the session for which the cookie will be
     *                    created
     * @param secure      Should session cookie be configured as secure
     * @return the cookie for the session
     */
    public static Cookie createSessionCookie(Context context,
            String sessionId, boolean secure) {

        SessionCookieConfig scc =
            context.getServletContext().getSessionCookieConfig();

        // NOTE: The priority order for session cookie configuration is:
        //       1. Context level configuration
        //       2. Values from SessionCookieConfig
        //       3. Defaults

        Cookie cookie = new Cookie(
                SessionConfig.getSessionCookieName(context), sessionId);

        // Just apply the defaults.
        cookie.setMaxAge(scc.getMaxAge());

        if (context.getSessionCookieDomain() == null) {
            // Avoid possible NPE
            if (scc.getDomain() != null) {
                cookie.setDomain(scc.getDomain());
            }
        } else {
            cookie.setDomain(context.getSessionCookieDomain());
        }

        // Always set secure if the request is secure
        if (scc.isSecure() || secure) {
            cookie.setSecure(true);
        }

        // Always set httpOnly if the context is configured for that
        if (scc.isHttpOnly() || context.getUseHttpOnly()) {
            cookie.setHttpOnly(true);
        }

        cookie.setPath(SessionConfig.getSessionCookiePath(context));

        // Other attributes
        for (Map.Entry<String,String> attribute : scc.getAttributes().entrySet()) {
            switch (attribute.getKey()) {
            case Constants.COOKIE_COMMENT_ATTR:
            case Constants.COOKIE_DOMAIN_ATTR:
            case Constants.COOKIE_MAX_AGE_ATTR:
            case Constants.COOKIE_PATH_ATTR:
            case Constants.COOKIE_SECURE_ATTR:
            case Constants.COOKIE_HTTP_ONLY_ATTR:
                // Handled above so NO-OP
                break;
            default: {
                cookie.setAttribute(attribute.getKey(), attribute.getValue());
            }
            }
        }

        return cookie;
    }
}
