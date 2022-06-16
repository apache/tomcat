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
package org.apache.catalina.filters;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import jakarta.servlet.http.HttpSession;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Provides basic CSRF protection for a web application. The filter assumes
 * that:
 * <ul>
 * <li>The filter is mapped to /*</li>
 * <li>{@link HttpServletResponse#encodeRedirectURL(String)} and
 * {@link HttpServletResponse#encodeURL(String)} are used to encode all URLs
 * returned to the client
 * </ul>
 */
public class CsrfPreventionFilter extends CsrfPreventionFilterBase {
    private final Log log = LogFactory.getLog(CsrfPreventionFilter.class);

    private final Set<String> entryPoints = new HashSet<>();

    private int nonceCacheSize = 5;

    private String nonceRequestParameterName = Constants.CSRF_NONCE_REQUEST_PARAM;

    /**
     * Entry points are URLs that will not be tested for the presence of a valid
     * nonce. They are used to provide a way to navigate back to a protected
     * application after navigating away from it. Entry points will be limited
     * to HTTP GET requests and should not trigger any security sensitive
     * actions.
     *
     * @param entryPoints   Comma separated list of URLs to be configured as
     *                      entry points.
     */
    public void setEntryPoints(String entryPoints) {
        String values[] = entryPoints.split(",");
        for (String value : values) {
            this.entryPoints.add(value.trim());
        }
    }

    /**
     * Sets the number of previously issued nonces that will be cached on a LRU
     * basis to support parallel requests, limited use of the refresh and back
     * in the browser and similar behaviors that may result in the submission
     * of a previous nonce rather than the current one. If not set, the default
     * value of 5 will be used.
     *
     * @param nonceCacheSize    The number of nonces to cache
     */
    public void setNonceCacheSize(int nonceCacheSize) {
        this.nonceCacheSize = nonceCacheSize;
    }

    /**
     * Sets the request parameter name to use for CSRF nonces.
     *
     * @param parameterName The request parameter name to use
     *        for CSRF nonces.
     */
    public void setNonceRequestParameterName(String parameterName) {
        this.nonceRequestParameterName = parameterName;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Set the parameters
        super.init(filterConfig);

        // Put the expected request parameter name into the application scope
        filterConfig.getServletContext().setAttribute(
                Constants.CSRF_NONCE_REQUEST_PARAM_NAME_KEY,
                nonceRequestParameterName);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        ServletResponse wResponse = null;

        if (request instanceof HttpServletRequest &&
                response instanceof HttpServletResponse) {

            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse res = (HttpServletResponse) response;

            HttpSession session = req.getSession(false);

            boolean skipNonceCheck = skipNonceCheck(req);
            NonceCache<String> nonceCache = null;

            if (!skipNonceCheck) {
                String previousNonce = req.getParameter(nonceRequestParameterName);

                if (previousNonce == null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Rejecting request for " + getRequestedPath(req)
                                  + ", session "
                                  + (null == session ? "(none)" : session.getId())
                                  + " with no CSRF nonce found in request");
                    }

                    res.sendError(getDenyStatus());
                    return;
                }

                nonceCache = getNonceCache(req, session);
                if (nonceCache == null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Rejecting request for " + getRequestedPath(req)
                                  + ", session "
                                  + (null == session ? "(none)" : session.getId())
                                  + " due to empty / missing nonce cache");
                    }

                    res.sendError(getDenyStatus());
                    return;
                } else if (!nonceCache.contains(previousNonce)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Rejecting request for " + getRequestedPath(req)
                                  + ", session "
                                  + (null == session ? "(none)" : session.getId())
                                  + " due to invalid nonce " + previousNonce);
                    }

                    res.sendError(getDenyStatus());
                    return;
                }
                if (log.isTraceEnabled()) {
                    log.trace("Allowing request to " + getRequestedPath(req)
                               + " with valid CSRF nonce " + previousNonce);
                }
            }

            if (!skipNonceGeneration(req)) {
                if (skipNonceCheck) {
                    // Didn't look up nonce cache earlier so look it up now.
                    nonceCache = getNonceCache(req, session);
                }
                if (nonceCache == null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Creating new CSRF nonce cache with size=" + nonceCacheSize + " for session " + (null == session ? "(will create)" : session.getId()));
                    }

                    if (session == null) {
                        if (log.isDebugEnabled()) {
                             log.debug("Creating new session to store CSRF nonce cache");
                        }

                        session = req.getSession(true);
                    }

                    nonceCache = createNonceCache(req, session);
                }

                String newNonce = generateNonce(req);

                nonceCache.add(newNonce);

                // Take this request's nonce and put it into the request
                // attributes so pages can make direct use of it, rather than
                // requiring the use of response.encodeURL.
                request.setAttribute(Constants.CSRF_NONCE_REQUEST_ATTR_NAME, newNonce);

                wResponse = new CsrfResponseWrapper(res, nonceRequestParameterName, newNonce);
            }
        }

        chain.doFilter(request, wResponse == null ? response : wResponse);
    }


    protected boolean skipNonceCheck(HttpServletRequest request) {
        if (!Constants.METHOD_GET.equals(request.getMethod())) {
            return false;
        }

        String requestedPath = getRequestedPath(request);

        if (!entryPoints.contains(requestedPath)) {
            return false;
        }

        if (log.isTraceEnabled()) {
            log.trace("Skipping CSRF nonce-check for GET request to entry point " + requestedPath);
        }

        return true;
    }


    /**
     * Determines whether a nonce should be created. This method is provided
     * primarily for the benefit of sub-classes that wish to customise this
     * behaviour.
     *
     * @param request   The request that triggered the need to potentially
     *                      create the nonce.
     *
     * @return {@code true} if a nonce should be created, otherwise
     *              {@code false}
     */
    protected boolean skipNonceGeneration(HttpServletRequest request) {
        return false;
    }


    /**
     * Create a new {@link NonceCache} and store in the {@link HttpSession}.
     * This method is provided primarily for the benefit of sub-classes that
     * wish to customise this behaviour.
     *
     * @param request   The request that triggered the need to create the nonce
     *                      cache. Unused by the default implementation.
     * @param session   The session associated with the request.
     *
     * @return A newly created {@link NonceCache}
     */
    protected NonceCache<String> createNonceCache(HttpServletRequest request, HttpSession session) {

        NonceCache<String> nonceCache = new LruCache<>(nonceCacheSize);

        session.setAttribute(Constants.CSRF_NONCE_SESSION_ATTR_NAME, nonceCache);

        return nonceCache;
    }


    /**
     * Obtain the {@link NonceCache} associated with the request and/or session.
     * This method is provided primarily for the benefit of sub-classes that
     * wish to customise this behaviour.
     *
     * @param request   The request that triggered the need to obtain the nonce
     *                      cache. Unused by the default implementation.
     * @param session   The session associated with the request.
     *
     * @return The {@link NonceCache} currently associated with the request
     *         and/or session
     */
    protected NonceCache<String> getNonceCache(HttpServletRequest request, HttpSession session) {
        if (session == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        NonceCache<String> nonceCache =
                (NonceCache<String>) session.getAttribute(Constants.CSRF_NONCE_SESSION_ATTR_NAME);
        return nonceCache;
    }

    protected static class CsrfResponseWrapper
            extends HttpServletResponseWrapper {

        private final String nonceRequestParameterName;
        private final String nonce;

        public CsrfResponseWrapper(HttpServletResponse response, String nonceRequestParameterName, String nonce) {
            super(response);
            this.nonceRequestParameterName = nonceRequestParameterName;
            this.nonce = nonce;
        }

        @Override
        public String encodeRedirectURL(String url) {
            return addNonce(super.encodeRedirectURL(url));
        }

        @Override
        public String encodeURL(String url) {
            return addNonce(super.encodeURL(url));
        }

        /*
         * Return the specified URL with the nonce added to the query string.
         *
         * @param url URL to be modified
         */
        private String addNonce(String url) {

            if ((url == null) || (nonce == null)) {
                return url;
            }

            String path = url;
            String query = "";
            String anchor = "";
            int pound = path.indexOf('#');
            if (pound >= 0) {
                anchor = path.substring(pound);
                path = path.substring(0, pound);
            }
            int question = path.indexOf('?');
            if (question >= 0) {
                query = path.substring(question);
                path = path.substring(0, question);
            }
            StringBuilder sb = new StringBuilder(path);
            if (query.length() >0) {
                sb.append(query);
                sb.append('&');
            } else {
                sb.append('?');
            }
            sb.append(nonceRequestParameterName);
            sb.append('=');
            sb.append(nonce);
            sb.append(anchor);
            return sb.toString();
        }
    }


    protected static interface NonceCache<T> extends Serializable {
        void add(T nonce);

        boolean contains(T nonce);
    }


    protected static class LruCache<T> implements NonceCache<T> {

        private static final long serialVersionUID = 1L;

        // Although the internal implementation uses a Map, this cache
        // implementation is only concerned with the keys.
        private final Map<T,T> cache;

        public LruCache(final int cacheSize) {
            cache = new LinkedHashMap<>() {
                private static final long serialVersionUID = 1L;
                @Override
                protected boolean removeEldestEntry(Map.Entry<T,T> eldest) {
                    if (size() > cacheSize) {
                        return true;
                    }
                    return false;
                }
            };
        }

        @Override
        public void add(T key) {
            synchronized (cache) {
                cache.put(key, null);
            }
        }

        @Override
        public boolean contains(T key) {
            synchronized (cache) {
                return cache.containsKey(key);
            }
        }
    }
}
