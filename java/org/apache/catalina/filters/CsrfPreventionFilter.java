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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
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
 * Provides basic CSRF protection for a web application. The filter assumes that:
 * <ul>
 * <li>The filter is mapped to /*</li>
 * <li>{@link HttpServletResponse#encodeRedirectURL(String)} and {@link HttpServletResponse#encodeURL(String)} are used
 * to encode all URLs returned to the client
 * </ul>
 * <p>
 * CSRF protection is enabled by generating random nonce values which are stored in the client's HTTP session. Each URL
 * encoded using {@link HttpServletResponse#encodeURL(String)} has a URL parameter added which, when sent to the server
 * in a future request, will be checked against this stored set of nonces for validity.
 * </p>
 * <p>
 * Some URLs should be accessible even without a valid nonce parameter value. These URLs are known as "entry points"
 * because clients should be able to "enter" the application without first establishing any valid tokens. These are
 * configured with the <code>entryPoints</code> filter <code>init-param</code>.
 * </p>
 * <p>
 * Some URLs should not have nonce parameters added to them at all
 */
public class CsrfPreventionFilter extends CsrfPreventionFilterBase {

    /**
     * The default set of URL patterns for which nonces will not be appended.
     */
    private static final String DEFAULT_NO_NONCE_URL_PATTERNS =
            "*.css, *.js, *.gif, *.png, *.jpg, *.svg, *.ico, *.jpeg, *.mjs";

    /**
     * The servlet context in which this Filter is operating.
     */
    private ServletContext context;

    private final Log log = LogFactory.getLog(CsrfPreventionFilter.class);

    private final Set<String> entryPoints = new HashSet<>();

    private int nonceCacheSize = 5;

    private String nonceRequestParameterName = Constants.CSRF_NONCE_REQUEST_PARAM;

    /**
     * Flag which determines whether this Filter is in "enforcement" mode (the default) or in "reporting" mode.
     */
    private boolean enforce = true;

    /**
     * A set of comma-separated URL patterns which will have no nonce parameters added to them.
     */
    private String noNoncePatterns = DEFAULT_NO_NONCE_URL_PATTERNS;

    private Collection<Predicate<String>> noNoncePredicates;

    /**
     * Entry points are URLs that will not be tested for the presence of a valid nonce. They are used to provide a way
     * to navigate back to a protected application after navigating away from it. Entry points will be limited to HTTP
     * GET requests and should not trigger any security sensitive actions.
     *
     * @param entryPoints Comma separated list of URLs to be configured as entry points.
     */
    public void setEntryPoints(String entryPoints) {
        String values[] = entryPoints.split(",");
        for (String value : values) {
            this.entryPoints.add(value.trim());
        }
    }

    /**
     * Sets the number of previously issued nonces that will be cached on a LRU basis to support parallel requests,
     * limited use of the refresh and back in the browser and similar behaviors that may result in the submission of a
     * previous nonce rather than the current one. If not set, the default value of 5 will be used.
     *
     * @param nonceCacheSize The number of nonces to cache
     */
    public void setNonceCacheSize(int nonceCacheSize) {
        this.nonceCacheSize = nonceCacheSize;
    }

    /**
     * Sets the request parameter name to use for CSRF nonces.
     *
     * @param parameterName The request parameter name to use for CSRF nonces.
     */
    public void setNonceRequestParameterName(String parameterName) {
        this.nonceRequestParameterName = parameterName;
    }

    /**
     * Sets the flag to enforce CSRF protection or just log failures as DEBUG messages.
     *
     * @param enforce <code>true</code> to enforce CSRF protection or <code>false</code> to log DEBUG messages and allow
     *                    all requests.
     */
    public void setEnforce(boolean enforce) {
        this.enforce = enforce;
    }

    /**
     * Gets the flag to enforce CSRF protection or just log failures as DEBUG messages.
     *
     * @return <code>true</code> if CSRF protection will be enforced or <code>false</code> if all requests will be
     *             allowed and failures will be logged as DEBUG messages.
     */
    public boolean isEnforce() {
        return this.enforce;
    }

    /**
     * Sets the list of URL patterns to suppress nonce-addition for. Some URLs do not need nonces added to them such as
     * static resources. By <i>not</i> adding nonces to those URLs, HTTP caches can be more effective because the CSRF
     * prevention filter won't generate what look like unique URLs for those commonly-reused resources.
     *
     * @param patterns A comma-separated list of URL patterns that will not have nonces added to them. Patterns may
     *                     begin or end with a <code>*</code> character to denote a suffix-match or prefix-match. Any
     *                     matched URL will not have a CSRF nonce added to it when passed through
     *                     {@link HttpServletResponse#encodeURL(String)}.
     */
    public void setNoNonceURLPatterns(String patterns) {
        this.noNoncePatterns = patterns;

        if (null != context) {
            this.noNoncePredicates = createNoNoncePredicates(context, this.noNoncePatterns);
        }
    }

    /**
     * Creates a collection of matchers from a comma-separated string of patterns.
     *
     * @param context  the Servlet context
     * @param patterns A comma-separated string of URL matching patterns.
     *
     * @return A collection of predicates representing the URL patterns.
     */
    protected static Collection<Predicate<String>> createNoNoncePredicates(ServletContext context, String patterns) {
        if (null == patterns || 0 == patterns.trim().length()) {
            return null;
        }

        if (patterns.startsWith("/") && patterns.endsWith("/")) {
            return Collections.singleton(new PatternPredicate(patterns.substring(1, patterns.length() - 1)));
        }

        String values[] = patterns.split(",");

        ArrayList<Predicate<String>> matchers = new ArrayList<>(values.length);
        for (String value : values) {
            Predicate<String> p = createNoNoncePredicate(context, value.trim());

            if (null != p) {
                matchers.add(p);
            }
        }

        matchers.trimToSize();

        return matchers;
    }

    /**
     * Creates a predicate that can match the specified type of pattern.
     *
     * @param context the Servlet context
     * @param pattern The pattern to match e.g. <code>*.foo</code> or <code>/bar/*</code>.
     *
     * @return A Predicate which can match the specified pattern, or <code>null</code> if the pattern is null or blank.
     */
    protected static Predicate<String> createNoNoncePredicate(ServletContext context, String pattern) {
        if (null == pattern || 0 == pattern.trim().length()) {
            return null;
        }
        if (pattern.startsWith("mime:")) {
            return new MimePredicate(context, createNoNoncePredicate(context, pattern.substring(5)));
        } else if (pattern.startsWith("*")) {
            return new SuffixPredicate(pattern.substring(1));
        } else if (pattern.endsWith("*")) {
            return new PrefixPredicate(pattern.substring(0, pattern.length() - 1));
        } else if (pattern.startsWith("/") && pattern.endsWith("/")) {
            return new PatternPredicate(pattern.substring(1, pattern.length() - 1));
        } else {
            throw new IllegalArgumentException(sm.getString("csrfPrevention.unsupportedPattern", pattern));
        }
    }

    /**
     * A no-nonce Predicate that evaluates a MIME type instead of a URL. It can be used with any other Predicate for
     * matching the actual value of the MIME type.
     */
    protected static class MimePredicate implements Predicate<String> {
        private final ServletContext context;
        private final Predicate<String> predicate;

        public MimePredicate(ServletContext context, Predicate<String> predicate) {
            this.context = context;
            this.predicate = predicate;
        }

        @Override
        public boolean test(String t) {
            String mimeType = context.getMimeType(t);
            if (mimeType == null) {
                return false;
            }
            return predicate.test(mimeType);
        }

        public Predicate<String> getPredicate() {
            return predicate;
        }
    }

    /**
     * A no-nonce Predicate that matches a prefix.
     */
    protected static class PrefixPredicate implements Predicate<String> {
        private final String prefix;

        public PrefixPredicate(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public boolean test(String t) {
            return t.startsWith(this.prefix);
        }
    }

    /**
     * A no-nonce Predicate that matches a suffix.
     */
    protected static class SuffixPredicate implements Predicate<String> {
        private final String suffix;

        public SuffixPredicate(String suffix) {
            this.suffix = suffix;
        }

        @Override
        public boolean test(String t) {
            return t.endsWith(this.suffix);
        }
    }

    /**
     * A no-nonce Predicate that matches a regular expression.
     */
    protected static class PatternPredicate implements Predicate<String> {
        private final Pattern pattern;

        public PatternPredicate(String regex) {
            this.pattern = Pattern.compile(regex);
        }

        @Override
        public boolean test(String t) {
            return pattern.matcher(t).matches();
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Set the parameters
        super.init(filterConfig);

        this.context = filterConfig.getServletContext();

        this.noNoncePredicates = createNoNoncePredicates(context, this.noNoncePatterns);

        // Put the expected request parameter name into the application scope
        filterConfig.getServletContext().setAttribute(Constants.CSRF_NONCE_REQUEST_PARAM_NAME_KEY,
                nonceRequestParameterName);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        ServletResponse wResponse = null;

        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {

            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse res = (HttpServletResponse) response;

            HttpSession session = req.getSession(false);

            String requestedPath = getRequestedPath(req);
            boolean skipNonceCheck = skipNonceCheck(req);
            NonceCache<String> nonceCache = null;

            if (!skipNonceCheck) {
                String previousNonce = req.getParameter(nonceRequestParameterName);

                if (previousNonce == null) {
                    if (enforce(req, requestedPath)) {
                        if (log.isDebugEnabled()) {
                            log.debug(sm.getString("csrfPrevention.rejectNoNonce", getRequestedPath(req),
                                    (null == session ? "(null)" : session.getId())));
                        }

                        res.sendError(getDenyStatus());
                        return;
                    } else {
                        if (log.isTraceEnabled()) {
                            log.trace("Would have rejected request for " + getRequestedPath(req) + ", session " +
                                    (null == session ? "(null)" : session.getId()) +
                                    " with no CSRF nonce found in request");
                        }
                    }
                } else {
                    nonceCache = getNonceCache(req, session);
                    if (nonceCache == null) {
                        if (enforce(req, requestedPath)) {
                            if (log.isDebugEnabled()) {
                                log.debug(sm.getString("csrfPrevention.rejectNoCache", getRequestedPath(req),
                                        (null == session ? "(null)" : session.getId())));
                            }

                            res.sendError(getDenyStatus());
                            return;
                        } else {
                            if (log.isTraceEnabled()) {
                                log.trace("Would have rejecting request for " + getRequestedPath(req) + ", session " +
                                        (null == session ? "(null)" : session.getId()) +
                                        " due to empty / missing nonce cache");
                            }
                        }
                    } else if (!nonceCache.contains(previousNonce)) {
                        if (enforce(req, requestedPath)) {
                            if (log.isDebugEnabled()) {
                                log.debug(sm.getString("csrfPrevention.rejectInvalidNonce", getRequestedPath(req),
                                        (null == session ? "(null)" : session.getId()), previousNonce));
                            }

                            res.sendError(getDenyStatus());
                            return;
                        } else {
                            if (log.isTraceEnabled()) {
                                log.trace("Would have rejecting request for " + getRequestedPath(req) + ", session " +
                                        (null == session ? "(null)" : session.getId()) + " due to invalid nonce " +
                                        previousNonce);
                            }
                        }
                    } else {
                        if (log.isTraceEnabled()) {
                            log.trace("Allowing request to " + getRequestedPath(req) + " with valid CSRF nonce " +
                                    previousNonce);
                        }
                    }
                }
            }

            if (!skipNonceGeneration(req)) {
                if (skipNonceCheck) {
                    // Didn't look up nonce cache earlier so look it up now.
                    nonceCache = getNonceCache(req, session);
                }
                if (nonceCache == null) {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("csrfPrevention.createCache", Integer.valueOf(nonceCacheSize),
                                (null == session ? "(null)" : session.getId())));
                    }

                    if (session == null) {
                        if (log.isTraceEnabled()) {
                            log.trace("Creating new session to store CSRF nonce cache");
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

                wResponse = new CsrfResponseWrapper(res, nonceRequestParameterName, newNonce, noNoncePredicates);
            }
        }

        chain.doFilter(request, wResponse == null ? response : wResponse);
    }

    /**
     * Check to see if the request and path should be enforced or only observed and reported. Note that the
     * <code>requestedPath</code> parameter is purely a performance optimization to avoid calling
     * {@link #getRequestedPath(HttpServletRequest)} multiple times.
     *
     * @param req           The request.
     * @param requestedPath The path of the request being evaluated.
     *
     * @return <code>true</code> if the CSRF prevention should be enforced, <code>false</code> if the CSRF prevention
     *             should only be logged in DEBUG mode.
     */
    protected boolean enforce(HttpServletRequest req, String requestedPath) {
        return isEnforce();
    }

    protected boolean skipNonceCheck(HttpServletRequest request) {
        if (!Constants.METHOD_GET.equals(request.getMethod())) {
            return false;
        }

        String requestedPath = getRequestedPath(request);

        if (entryPoints.contains(requestedPath)) {
            if (log.isTraceEnabled()) {
                log.trace("Skipping CSRF nonce-check for GET request to entry point " + requestedPath);
            }

            return true;
        }

        if (null != noNoncePredicates && !noNoncePredicates.isEmpty()) {
            for (Predicate<String> p : noNoncePredicates) {
                if (p.test(requestedPath)) {
                    if (log.isTraceEnabled()) {
                        log.trace("Skipping CSRF nonce-check for GET request to no-nonce path " + requestedPath);
                    }

                    return true;
                }
            }
        }

        return false;
    }


    /**
     * Determines whether a nonce should be created. This method is provided primarily for the benefit of sub-classes
     * that wish to customise this behaviour.
     *
     * @param request The request that triggered the need to potentially create the nonce.
     *
     * @return {@code true} if a nonce should be created, otherwise {@code false}
     */
    protected boolean skipNonceGeneration(HttpServletRequest request) {
        return false;
    }


    /**
     * Create a new {@link NonceCache} and store in the {@link HttpSession}. This method is provided primarily for the
     * benefit of sub-classes that wish to customise this behaviour.
     *
     * @param request The request that triggered the need to create the nonce cache. Unused by the default
     *                    implementation.
     * @param session The session associated with the request.
     *
     * @return A newly created {@link NonceCache}
     */
    protected NonceCache<String> createNonceCache(HttpServletRequest request, HttpSession session) {

        NonceCache<String> nonceCache = new LruCache<>(nonceCacheSize);

        session.setAttribute(Constants.CSRF_NONCE_SESSION_ATTR_NAME, nonceCache);

        return nonceCache;
    }


    /**
     * Obtain the {@link NonceCache} associated with the request and/or session. This method is provided primarily for
     * the benefit of sub-classes that wish to customise this behaviour.
     *
     * @param request The request that triggered the need to obtain the nonce cache. Unused by the default
     *                    implementation.
     * @param session The session associated with the request.
     *
     * @return The {@link NonceCache} currently associated with the request and/or session
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

    protected static class CsrfResponseWrapper extends HttpServletResponseWrapper {

        private final String nonceRequestParameterName;
        private final String nonce;
        private final Collection<Predicate<String>> noNoncePatterns;

        public CsrfResponseWrapper(HttpServletResponse response, String nonceRequestParameterName, String nonce,
                Collection<Predicate<String>> noNoncePatterns) {
            super(response);
            this.nonceRequestParameterName = nonceRequestParameterName;
            this.nonce = nonce;
            this.noNoncePatterns = noNoncePatterns;
        }

        @Override
        public String encodeRedirectURL(String url) {
            if (shouldAddNonce(url)) {
                return addNonce(super.encodeRedirectURL(url));
            } else {
                return url;
            }
        }

        @Override
        public String encodeURL(String url) {
            if (shouldAddNonce(url)) {
                return addNonce(super.encodeURL(url));
            } else {
                return url;
            }
        }

        private boolean shouldAddNonce(String url) {
            if (null == noNoncePatterns || noNoncePatterns.isEmpty()) {
                return true;
            }

            if (null != noNoncePatterns) {
                for (Predicate<String> p : noNoncePatterns) {
                    if (p.test(url)) {
                        return false;
                    }
                }
            }

            return true;
        }

        /*
         * Return the specified URL with the nonce added to the query string.
         *
         * @param url URL to be modified
         */
        private String addNonce(String url) {

            if (url == null || nonce == null) {
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
            if (query.length() > 0) {
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


    protected interface NonceCache<T> extends Serializable {
        void add(T nonce);

        boolean contains(T nonce);
    }


    /**
     * Despite its name, this is a FIFO cache not an LRU cache. Using an older nonce should not delay its removal from
     * the cache in favour of more recent values.
     *
     * @param <T> The type held by this cache.
     */
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
