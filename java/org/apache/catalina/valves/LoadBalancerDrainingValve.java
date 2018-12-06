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
package org.apache.catalina.valves;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.SessionConfig;

/**
 * <p>A Valve to detect situations where a load-balanced node receiving a
 * request has been deactivated by the load balancer (JK_LB_ACTIVATION=DIS)
 * and the incoming request has no valid session.</p>
 *
 * <p>In these cases, the user's session cookie should be removed if it exists,
 * any ";jsessionid" parameter should be removed from the request URI,
 * and the client should be redirected to the same URI. This will cause the
 * load-balanced to re-balance the client to another server.</p>
 *
 * <p>All this work is required because when the activation state of a node is
 * DISABLED, the load-balancer will still send requests to the node if they
 * appear to have a session on that node. Since mod_jk doesn't actually know
 * whether the session id is valid, it will send the request blindly to
 * the disabled node, which makes it take much longer to drain the node
 * than strictly necessary.</p>
 *
 * <p>For testing purposes, a special cookie can be configured and used
 * by a client to ignore the normal behavior of this Valve and allow
 * a client to get a new session on a DISABLED node. See
 * {@link #setIgnoreCookieName} and {@link #setIgnoreCookieValue}
 * to configure those values.</p>
 *
 * <p>This Valve should be installed earlier in the Valve pipeline than any
 * authentication valves, as the redirection should take place before an
 * authentication valve would save a request to a protected resource.</p>
 *
 * @see <a href="https://tomcat.apache.org/connectors-doc/generic_howto/loadbalancers.html">Load
 *      balancer documentation</a>
 */
public class LoadBalancerDrainingValve extends ValveBase {

    /**
     * The request attribute key where the load-balancer's activation state
     * can be found.
     */
    public static final String ATTRIBUTE_KEY_JK_LB_ACTIVATION = "JK_LB_ACTIVATION";

    /**
     * The HTTP response code that will be used to redirect the request
     * back to the load-balancer for re-balancing. Defaults to 307
     * (TEMPORARY_REDIRECT).
     *
     * HTTP status code 305 (USE_PROXY) might be an option, here. too.
     */
    private int _redirectStatusCode = HttpServletResponse.SC_TEMPORARY_REDIRECT;

    /**
     * The name of the cookie which can be set to ignore the "draining" action
     * of this Filter. This will allow a client to contact the server without
     * being re-balanced to another server. The expected cookie value can be set
     * in the {@link #_ignoreCookieValue}. The cookie name and value must match
     * to avoid being re-balanced.
     */
    private String _ignoreCookieName;

    /**
     * The value of the cookie which can be set to ignore the "draining" action
     * of this Filter. This will allow a client to contact the server without
     * being re-balanced to another server. The expected cookie name can be set
     * in the {@link #_ignoreCookieName}. The cookie name and value must match
     * to avoid being re-balanced.
     */
    private String _ignoreCookieValue;

    public LoadBalancerDrainingValve() {
        super(true); // Supports async
    }

    //
    // Configuration parameters
    //

    /**
     * Sets the HTTP response code that will be used to redirect the request
     * back to the load-balancer for re-balancing. Defaults to 307
     * (TEMPORARY_REDIRECT).
     *
     * @param code The code to use for the redirect
     */
    public void setRedirectStatusCode(int code) {
        _redirectStatusCode = code;
    }

    /**
     * Gets the name of the cookie that can be used to override the
     * re-balancing behavior of this Valve when the current node is
     * in the DISABLED activation state.
     *
     * @return The cookie name used to ignore normal processing rules.
     *
     * @see #setIgnoreCookieValue
     */
    public String getIgnoreCookieName() {
        return _ignoreCookieName;
    }

    /**
     * Sets the name of the cookie that can be used to override the
     * re-balancing behavior of this Valve when the current node is
     * in the DISABLED activation state.
     *
     * There is no default value for this setting: the ability to override
     * the re-balancing behavior of this Valve is <i>disabled</i> by default.
     *
     * @param cookieName The cookie name to use to ignore normal
     *                   processing rules.
     *
     * @see #getIgnoreCookieValue
     */
    public void setIgnoreCookieName(String cookieName) {
        _ignoreCookieName = cookieName;
    }

    /**
     * Gets the expected value of the cookie that can be used to override the
     * re-balancing behavior of this Valve when the current node is
     * in the DISABLED activation state.
     *
     * @return The cookie value used to ignore normal processing rules.
     *
     * @see #setIgnoreCookieValue
     */
    public String getIgnoreCookieValue() {
        return _ignoreCookieValue;
    }

    /**
     * Sets the expected value of the cookie that can be used to override the
     * re-balancing behavior of this Valve when the current node is
     * in the DISABLED activation state. The "ignore" cookie's value
     * <b>must</b> be exactly equal to this value in order to allow
     * the client to override the re-balancing behavior.
     *
     * @param cookieValue The cookie value to use to ignore normal
     *                    processing rules.
     *
     * @see #getIgnoreCookieValue
     */
    public void setIgnoreCookieValue(String cookieValue) {
        _ignoreCookieValue = cookieValue;
    }

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        if  ("DIS".equals(request.getAttribute(ATTRIBUTE_KEY_JK_LB_ACTIVATION)) &&
                !request.isRequestedSessionIdValid()) {

            if (containerLog.isDebugEnabled()) {
                containerLog.debug("Load-balancer is in DISABLED state; draining this node");
            }

            boolean ignoreRebalance = false;
            Cookie sessionCookie = null;

            final Cookie[] cookies = request.getCookies();

            final String sessionCookieName = SessionConfig.getSessionCookieName(request.getContext());

            if (null != cookies) {
                for (Cookie cookie : cookies) {
                    final String cookieName = cookie.getName();
                    if (containerLog.isTraceEnabled()) {
                        containerLog.trace("Checking cookie " + cookieName + "=" + cookie.getValue());
                    }

                    if (sessionCookieName.equals(cookieName) &&
                            request.getRequestedSessionId().equals(cookie.getValue())) {
                        sessionCookie = cookie;
                    } else if (null != _ignoreCookieName &&
                            _ignoreCookieName.equals(cookieName) &&
                            null != _ignoreCookieValue &&
                            _ignoreCookieValue.equals(cookie.getValue())) {
                        // The client presenting a valid ignore-cookie value?
                        ignoreRebalance = true;
                    }
                }
            }

            if (ignoreRebalance) {
                if (containerLog.isDebugEnabled()) {
                    containerLog.debug("Client is presenting a valid " + _ignoreCookieName +
                            " cookie, re-balancing is being skipped");
                }

                getNext().invoke(request, response);

                return;
            }

            // Kill any session cookie that was found
            // TODO: Consider implications of SSO cookies
            if (null != sessionCookie) {
                sessionCookie.setPath(SessionConfig.getSessionCookiePath(request.getContext()));
                sessionCookie.setMaxAge(0); // Delete
                sessionCookie.setValue(""); // Purge the cookie's value
                response.addCookie(sessionCookie);
            }

            // Re-write the URI if it contains a ;jsessionid parameter
            String uri = request.getRequestURI();
            String sessionURIParamName = SessionConfig.getSessionUriParamName(request.getContext());
            if (uri.contains(";" + sessionURIParamName + "=")) {
                uri = uri.replaceFirst(";" + sessionURIParamName + "=[^&?]*", "");
            }

            String queryString = request.getQueryString();

            if (null != queryString) {
                uri = uri + "?" + queryString;
            }

            // NOTE: Do not call response.encodeRedirectURL or the bad
            // sessionid will be restored
            response.setHeader("Location", uri);
            response.setStatus(_redirectStatusCode);
        } else {
            getNext().invoke(request, response);
        }
    }
}
