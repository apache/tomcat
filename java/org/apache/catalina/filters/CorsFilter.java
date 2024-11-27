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
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.GenericFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.Globals;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.http.RequestUtil;
import org.apache.tomcat.util.http.ResponseUtil;
import org.apache.tomcat.util.http.parser.MediaType;
import org.apache.tomcat.util.res.StringManager;

/**
 * <p>
 * A {@link jakarta.servlet.Filter} that enable client-side cross-origin requests by implementing W3C's CORS
 * (<b>C</b>ross-<b>O</b>rigin <b>R</b>esource <b>S</b>haring) specification for resources. Each
 * {@link HttpServletRequest} request is inspected as per specification, and appropriate response headers are added to
 * {@link HttpServletResponse}.
 * </p>
 * <p>
 * By default, it also sets following request attributes, that help to determine the nature of the request downstream.
 * </p>
 * <ul>
 * <li><b>cors.isCorsRequest:</b> Flag to determine if the request is a CORS request. Set to <code>true</code> if a CORS
 * request; <code>false</code> otherwise.</li>
 * <li><b>cors.request.origin:</b> The Origin URL, i.e. the URL of the page from where the request is originated.</li>
 * <li><b>cors.request.type:</b> Type of request. Possible values:
 * <ul>
 * <li>SIMPLE: A request which is not preceded by a pre-flight request.</li>
 * <li>ACTUAL: A request which is preceded by a pre-flight request.</li>
 * <li>PRE_FLIGHT: A pre-flight request.</li>
 * <li>NOT_CORS: A normal same-origin request.</li>
 * <li>INVALID_CORS: A cross-origin request which is invalid.</li>
 * </ul>
 * </li>
 * <li><b>cors.request.headers:</b> Request headers sent as 'Access-Control-Request-Headers' header, for pre-flight
 * request.</li>
 * </ul>
 * If you extend this class and override one or more of the getXxx() methods, consider whether you also need to override
 * {@link CorsFilter#doFilter(ServletRequest, ServletResponse, FilterChain)} and add appropriate locking so that the
 * {@code doFilter()} method executes with a consistent configuration.
 *
 * @see <a href="http://www.w3.org/TR/cors/">CORS specification</a>
 */
public class CorsFilter extends GenericFilter {

    private static final long serialVersionUID = 1L;
    private static final StringManager sm = StringManager.getManager(CorsFilter.class);

    private transient Log log = LogFactory.getLog(CorsFilter.class); // must not be static


    /**
     * A {@link Collection} of origins consisting of zero or more origins that are allowed access to the resource.
     */
    private final Collection<String> allowedOrigins = new HashSet<>();

    /**
     * Determines if any origin is allowed to make request.
     */
    private boolean anyOriginAllowed;

    /**
     * A {@link Collection} of methods consisting of zero or more methods that are supported by the resource.
     */
    private final Collection<String> allowedHttpMethods = new HashSet<>();

    /**
     * A {@link Collection} of headers consisting of zero or more header field names that are supported by the resource.
     */
    private final Collection<String> allowedHttpHeaders = new HashSet<>();

    /**
     * A {@link Collection} of exposed headers consisting of zero or more header field names of headers other than the
     * simple response headers that the resource might use and can be exposed.
     */
    private final Collection<String> exposedHeaders = new HashSet<>();

    /**
     * A supports credentials flag that indicates whether the resource supports user credentials in the request. It is
     * true when the resource does and false otherwise.
     */
    private boolean supportsCredentials;

    /**
     * Indicates (in seconds) how long the results of a pre-flight request can be cached in a pre-flight result cache.
     */
    private long preflightMaxAge;

    /**
     * Determines if the request should be decorated or not.
     */
    private boolean decorateRequest;


    @Override
    public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse,
            final FilterChain filterChain) throws IOException, ServletException {
        if (!(servletRequest instanceof HttpServletRequest) || !(servletResponse instanceof HttpServletResponse)) {
            throw new ServletException(sm.getString("corsFilter.onlyHttp"));
        }

        // Safe to downcast at this point.
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // Determines the CORS request type.
        CorsFilter.CORSRequestType requestType = checkRequestType(request);

        // Adds CORS specific attributes to request.
        if (isDecorateRequest()) {
            decorateCORSProperties(request, requestType);
        }
        switch (requestType) {
            case SIMPLE:
                // Handles a Simple CORS request.
            case ACTUAL:
                // Handles an Actual CORS request.
                this.handleSimpleCORS(request, response, filterChain);
                break;
            case PRE_FLIGHT:
                // Handles a Pre-flight CORS request.
                this.handlePreflightCORS(request, response, filterChain);
                break;
            case NOT_CORS:
                // Handles a Normal request that is not a cross-origin request.
                this.handleNonCORS(request, response, filterChain);
                break;
            default:
                // Handles a CORS request that violates specification.
                this.handleInvalidCORS(request, response, filterChain);
                break;
        }
    }


    @Override
    public void init() throws ServletException {
        parseAndStore(getInitParameter(PARAM_CORS_ALLOWED_ORIGINS, DEFAULT_ALLOWED_ORIGINS),
                getInitParameter(PARAM_CORS_ALLOWED_METHODS, DEFAULT_ALLOWED_HTTP_METHODS),
                getInitParameter(PARAM_CORS_ALLOWED_HEADERS, DEFAULT_ALLOWED_HTTP_HEADERS),
                getInitParameter(PARAM_CORS_EXPOSED_HEADERS, DEFAULT_EXPOSED_HEADERS),
                getInitParameter(PARAM_CORS_SUPPORT_CREDENTIALS, DEFAULT_SUPPORTS_CREDENTIALS),
                getInitParameter(PARAM_CORS_PREFLIGHT_MAXAGE, DEFAULT_PREFLIGHT_MAXAGE),
                getInitParameter(PARAM_CORS_REQUEST_DECORATE, DEFAULT_DECORATE_REQUEST));
    }


    /**
     * This method returns the parameter's value if it exists, or defaultValue if not.
     *
     * @param name         The parameter's name
     * @param defaultValue The default value to return if the parameter does not exist
     *
     * @return The parameter's value or the default value if the parameter does not exist
     */
    private String getInitParameter(String name, String defaultValue) {

        String value = getInitParameter(name);
        if (value != null) {
            return value;
        }

        return defaultValue;
    }


    /**
     * Handles a CORS request of type {@link CORSRequestType}.SIMPLE.
     *
     * @param request     The {@link HttpServletRequest} object.
     * @param response    The {@link HttpServletResponse} object.
     * @param filterChain The {@link FilterChain} object.
     *
     * @throws IOException      an IO error occurred
     * @throws ServletException Servlet error propagation
     *
     * @see <a href="http://www.w3.org/TR/cors/#resource-requests">Simple Cross-Origin Request, Actual Request, and
     *          Redirects</a>
     */
    protected void handleSimpleCORS(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain filterChain) throws IOException, ServletException {

        CorsFilter.CORSRequestType requestType = checkRequestType(request);
        if (!(requestType == CorsFilter.CORSRequestType.SIMPLE || requestType == CorsFilter.CORSRequestType.ACTUAL)) {
            throw new IllegalArgumentException(sm.getString("corsFilter.wrongType2", CorsFilter.CORSRequestType.SIMPLE,
                    CorsFilter.CORSRequestType.ACTUAL));
        }

        final String origin = request.getHeader(REQUEST_HEADER_ORIGIN);
        final String method = request.getMethod();

        // Section 6.1.2
        if (!isOriginAllowed(origin)) {
            handleInvalidCORS(request, response, filterChain);
            return;
        }

        if (!getAllowedHttpMethods().contains(method)) {
            handleInvalidCORS(request, response, filterChain);
            return;
        }

        addStandardHeaders(request, response);

        // Forward the request down the filter chain.
        filterChain.doFilter(request, response);
    }


    /**
     * Handles CORS pre-flight request.
     *
     * @param request     The {@link HttpServletRequest} object.
     * @param response    The {@link HttpServletResponse} object.
     * @param filterChain The {@link FilterChain} object.
     *
     * @throws IOException      an IO error occurred
     * @throws ServletException Servlet error propagation
     */
    protected void handlePreflightCORS(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain filterChain) throws IOException, ServletException {

        CORSRequestType requestType = checkRequestType(request);
        if (requestType != CORSRequestType.PRE_FLIGHT) {
            throw new IllegalArgumentException(sm.getString("corsFilter.wrongType1",
                    CORSRequestType.PRE_FLIGHT.name().toLowerCase(Locale.ENGLISH)));
        }

        final String origin = request.getHeader(REQUEST_HEADER_ORIGIN);

        // Section 6.2.2
        if (!isOriginAllowed(origin)) {
            handleInvalidCORS(request, response, filterChain);
            return;
        }

        // Section 6.2.3
        String accessControlRequestMethod = request.getHeader(REQUEST_HEADER_ACCESS_CONTROL_REQUEST_METHOD);
        if (accessControlRequestMethod == null) {
            handleInvalidCORS(request, response, filterChain);
            return;
        } else {
            accessControlRequestMethod = accessControlRequestMethod.trim();
        }

        // Section 6.2.4
        String accessControlRequestHeadersHeader = request.getHeader(REQUEST_HEADER_ACCESS_CONTROL_REQUEST_HEADERS);
        List<String> accessControlRequestHeaders = new ArrayList<>();
        if (accessControlRequestHeadersHeader != null && !accessControlRequestHeadersHeader.trim().isEmpty()) {
            String[] headers = accessControlRequestHeadersHeader.trim().split(",");
            for (String header : headers) {
                accessControlRequestHeaders.add(header.trim().toLowerCase(Locale.ENGLISH));
            }
        }

        // Section 6.2.5
        if (!getAllowedHttpMethods().contains(accessControlRequestMethod)) {
            handleInvalidCORS(request, response, filterChain);
            return;
        }

        // Section 6.2.6
        if (!accessControlRequestHeaders.isEmpty()) {
            for (String header : accessControlRequestHeaders) {
                if (!getAllowedHttpHeaders().contains(header)) {
                    handleInvalidCORS(request, response, filterChain);
                    return;
                }
            }
        }

        addStandardHeaders(request, response);

        // Do not forward the request down the filter chain.
    }


    /**
     * Handles a request, that's not a CORS request, but is a valid request i.e. it is not a cross-origin request. This
     * implementation, just forwards the request down the filter chain.
     *
     * @param request     The {@link HttpServletRequest} object.
     * @param response    The {@link HttpServletResponse} object.
     * @param filterChain The {@link FilterChain} object.
     *
     * @throws IOException      an IO error occurred
     * @throws ServletException Servlet error propagation
     */
    private void handleNonCORS(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain filterChain) throws IOException, ServletException {

        if (!isAnyOriginAllowed()) {
            // If only specific origins are allowed, the response will vary by
            // origin
            ResponseUtil.addVaryFieldName(response, REQUEST_HEADER_ORIGIN);
        }

        // Let request pass.
        filterChain.doFilter(request, response);
    }


    /**
     * Handles a CORS request that violates specification.
     *
     * @param request     The {@link HttpServletRequest} object.
     * @param response    The {@link HttpServletResponse} object.
     * @param filterChain The {@link FilterChain} object.
     */
    private void handleInvalidCORS(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain filterChain) {
        String origin = request.getHeader(REQUEST_HEADER_ORIGIN);
        String method = request.getMethod();
        String accessControlRequestHeaders = request.getHeader(REQUEST_HEADER_ACCESS_CONTROL_REQUEST_HEADERS);

        response.setContentType("text/plain");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.resetBuffer();

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("corsFilter.invalidRequest", origin, method, accessControlRequestHeaders));
        }
    }


    /*
     * Sets a standard set of headers to reduce response variation which in turn is intended to aid caching.
     */
    private void addStandardHeaders(final HttpServletRequest request, final HttpServletResponse response) {

        final String method = request.getMethod();
        final String origin = request.getHeader(REQUEST_HEADER_ORIGIN);

        // Local copy to avoid concurrency issues if isAnyOriginAllowed()
        // is overridden.
        boolean anyOriginAllowed = isAnyOriginAllowed();
        if (!anyOriginAllowed) {
            // If only specific origins are allowed, the response will vary by
            // origin
            ResponseUtil.addVaryFieldName(response, REQUEST_HEADER_ORIGIN);
        }

        // CORS requests (SIMPLE, ACTUAL, PRE_FLIGHT) set the following headers
        // although non-CORS requests do not need to. The headers are always set
        // as a) they do no harm in the non-CORS case and b) it allows the same
        // response to be cached for CORS and non-CORS requests.

        // Add a single Access-Control-Allow-Origin header.
        if (anyOriginAllowed) {
            // If any origin is allowed, return header with '*'.
            response.addHeader(RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        } else {
            // Add a single Access-Control-Allow-Origin header, with the value
            // of the Origin header as value.
            response.addHeader(RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        }

        // If the resource supports credentials, add a single
        // Access-Control-Allow-Credentials header with the case-sensitive
        // string "true" as value.
        if (isSupportsCredentials()) {
            response.addHeader(RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }

        // If the list of exposed headers is not empty add one or more
        // Access-Control-Expose-Headers headers, with as values the header
        // field names given in the list of exposed headers.
        // Local copy to avoid concurrency issues if getExposedHeaders()
        // is overridden.
        Collection<String> exposedHeaders = getExposedHeaders();
        if (exposedHeaders != null && exposedHeaders.size() > 0) {
            String exposedHeadersString = join(exposedHeaders, ",");
            response.addHeader(RESPONSE_HEADER_ACCESS_CONTROL_EXPOSE_HEADERS, exposedHeadersString);
        }

        if ("OPTIONS".equals(method)) {
            // For an OPTIONS request, the response will vary based on the
            // value or absence of the following headers. Hence they need be be
            // included in the Vary header.
            ResponseUtil.addVaryFieldName(response, REQUEST_HEADER_ACCESS_CONTROL_REQUEST_METHOD);
            ResponseUtil.addVaryFieldName(response, REQUEST_HEADER_ACCESS_CONTROL_REQUEST_HEADERS);

            // CORS PRE_FLIGHT (OPTIONS) requests set the following headers although
            // non-CORS OPTIONS requests do not need to. The headers are always set
            // as a) they do no harm in the non-CORS case and b) it allows the same
            // response to be cached for CORS and non-CORS requests.
            // Local copy to avoid concurrency issues if getPreflightMaxAge()
            // is overridden.
            long preflightMaxAge = getPreflightMaxAge();
            if (preflightMaxAge > 0) {
                response.addHeader(RESPONSE_HEADER_ACCESS_CONTROL_MAX_AGE, String.valueOf(preflightMaxAge));
            }

            // Local copy to avoid concurrency issues if getAllowedHttpMethods()
            // is overridden.
            Collection<String> allowedHttpMethods = getAllowedHttpMethods();
            if (allowedHttpMethods != null && !allowedHttpMethods.isEmpty()) {
                response.addHeader(RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_METHODS, join(allowedHttpMethods, ","));
            }

            // Local copy to avoid concurrency issues if getAllowedHttpHeaders()
            // is overridden.
            Collection<String> allowedHttpHeaders = getAllowedHttpHeaders();
            if (allowedHttpHeaders != null && !allowedHttpHeaders.isEmpty()) {
                response.addHeader(RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_HEADERS, join(allowedHttpHeaders, ","));
            }
        }
    }


    /**
     * Decorates the {@link HttpServletRequest}, with CORS attributes.
     * <ul>
     * <li><b>cors.isCorsRequest:</b> Flag to determine if request is a CORS request. Set to <code>true</code> if CORS
     * request; <code>false</code> otherwise.</li>
     * <li><b>cors.request.origin:</b> The Origin URL.</li>
     * <li><b>cors.request.type:</b> Type of request. Values: <code>simple</code> or <code>preflight</code> or
     * <code>not_cors</code> or <code>invalid_cors</code></li>
     * <li><b>cors.request.headers:</b> Request headers sent as 'Access-Control-Request-Headers' header, for pre-flight
     * request.</li>
     * </ul>
     *
     * @param request         The {@link HttpServletRequest} object.
     * @param corsRequestType The {@link CORSRequestType} object.
     */
    protected static void decorateCORSProperties(final HttpServletRequest request,
            final CORSRequestType corsRequestType) {
        if (request == null) {
            throw new IllegalArgumentException(sm.getString("corsFilter.nullRequest"));
        }

        if (corsRequestType == null) {
            throw new IllegalArgumentException(sm.getString("corsFilter.nullRequestType"));
        }

        switch (corsRequestType) {
            case SIMPLE:
            case ACTUAL:
                request.setAttribute(HTTP_REQUEST_ATTRIBUTE_IS_CORS_REQUEST, Boolean.TRUE);
                request.setAttribute(HTTP_REQUEST_ATTRIBUTE_ORIGIN, request.getHeader(REQUEST_HEADER_ORIGIN));
                request.setAttribute(HTTP_REQUEST_ATTRIBUTE_REQUEST_TYPE,
                        corsRequestType.name().toLowerCase(Locale.ENGLISH));
                break;
            case PRE_FLIGHT:
                request.setAttribute(HTTP_REQUEST_ATTRIBUTE_IS_CORS_REQUEST, Boolean.TRUE);
                request.setAttribute(HTTP_REQUEST_ATTRIBUTE_ORIGIN, request.getHeader(REQUEST_HEADER_ORIGIN));
                request.setAttribute(HTTP_REQUEST_ATTRIBUTE_REQUEST_TYPE,
                        corsRequestType.name().toLowerCase(Locale.ENGLISH));
                String headers = request.getHeader(REQUEST_HEADER_ACCESS_CONTROL_REQUEST_HEADERS);
                if (headers == null) {
                    headers = "";
                }
                request.setAttribute(HTTP_REQUEST_ATTRIBUTE_REQUEST_HEADERS, headers);
                break;
            case NOT_CORS:
                request.setAttribute(HTTP_REQUEST_ATTRIBUTE_IS_CORS_REQUEST, Boolean.FALSE);
                break;
            default:
                // Don't set any attributes
                break;
        }
    }


    /**
     * Joins elements of {@link Set} into a string, where each element is separated by the provided separator.
     *
     * @param elements      The {@link Set} containing elements to join together.
     * @param joinSeparator The character to be used for separating elements.
     *
     * @return The joined {@link String}; <code>null</code> if elements {@link Set} is null.
     */
    protected static String join(final Collection<String> elements, final String joinSeparator) {
        String separator = ",";
        if (elements == null) {
            return null;
        }
        if (joinSeparator != null) {
            separator = joinSeparator;
        }
        StringBuilder buffer = new StringBuilder();
        boolean isFirst = true;
        for (String element : elements) {
            if (!isFirst) {
                buffer.append(separator);
            } else {
                isFirst = false;
            }

            if (element != null) {
                buffer.append(element);
            }
        }

        return buffer.toString();
    }


    /**
     * Determines the request type.
     *
     * @param request The HTTP Servlet request
     *
     * @return the CORS type
     */
    protected CORSRequestType checkRequestType(final HttpServletRequest request) {
        CORSRequestType requestType = CORSRequestType.INVALID_CORS;
        if (request == null) {
            throw new IllegalArgumentException(sm.getString("corsFilter.nullRequest"));
        }
        String originHeader = request.getHeader(REQUEST_HEADER_ORIGIN);
        // Section 6.1.1 and Section 6.2.1
        if (originHeader != null) {
            if (originHeader.isEmpty()) {
                requestType = CORSRequestType.INVALID_CORS;
            } else if (!RequestUtil.isValidOrigin(originHeader)) {
                requestType = CORSRequestType.INVALID_CORS;
            } else if (RequestUtil.isSameOrigin(request, originHeader)) {
                return CORSRequestType.NOT_CORS;
            } else {
                String method = request.getMethod();
                if (method != null) {
                    if ("OPTIONS".equals(method)) {
                        String accessControlRequestMethodHeader =
                                request.getHeader(REQUEST_HEADER_ACCESS_CONTROL_REQUEST_METHOD);
                        if (accessControlRequestMethodHeader != null && !accessControlRequestMethodHeader.isEmpty()) {
                            requestType = CORSRequestType.PRE_FLIGHT;
                        } else if (accessControlRequestMethodHeader != null &&
                                accessControlRequestMethodHeader.isEmpty()) {
                            requestType = CORSRequestType.INVALID_CORS;
                        } else {
                            requestType = CORSRequestType.ACTUAL;
                        }
                    } else if ("GET".equals(method) || "HEAD".equals(method)) {
                        requestType = CORSRequestType.SIMPLE;
                    } else if ("POST".equals(method)) {
                        String mediaType = MediaType.parseMediaTypeOnly(request.getContentType());
                        if (mediaType == null) {
                            requestType = CORSRequestType.SIMPLE;
                        } else {
                            if (SIMPLE_HTTP_REQUEST_CONTENT_TYPE_VALUES.contains(mediaType)) {
                                requestType = CORSRequestType.SIMPLE;
                            } else {
                                requestType = CORSRequestType.ACTUAL;
                            }
                        }
                    } else {
                        requestType = CORSRequestType.ACTUAL;
                    }
                }
            }
        } else {
            requestType = CORSRequestType.NOT_CORS;
        }

        return requestType;
    }


    /**
     * Checks if the Origin is allowed to make a CORS request.
     *
     * @param origin The Origin.
     *
     * @return <code>true</code> if origin is allowed; <code>false</code> otherwise.
     */
    private boolean isOriginAllowed(final String origin) {
        if (isAnyOriginAllowed()) {
            return true;
        }

        // If 'Origin' header is a case-sensitive match of any of allowed
        // origins, then return true, else return false.
        return getAllowedOrigins().contains(origin);
    }


    /**
     * Parses each param-value and populates configuration variables. If a param is provided, it overrides the default.
     *
     * @param allowedOrigins      A {@link String} of comma separated origins.
     * @param allowedHttpMethods  A {@link String} of comma separated HTTP methods.
     * @param allowedHttpHeaders  A {@link String} of comma separated HTTP headers.
     * @param exposedHeaders      A {@link String} of comma separated headers that needs to be exposed.
     * @param supportsCredentials "true" if support credentials needs to be enabled.
     * @param preflightMaxAge     The amount of seconds the user agent is allowed to cache the result of the pre-flight
     *                                request.
     *
     * @throws ServletException If the configuration is invalid
     */
    private void parseAndStore(final String allowedOrigins, final String allowedHttpMethods,
            final String allowedHttpHeaders, final String exposedHeaders, final String supportsCredentials,
            final String preflightMaxAge, final String decorateRequest) throws ServletException {

        if (allowedOrigins.trim().equals("*")) {
            this.anyOriginAllowed = true;
        } else {
            this.anyOriginAllowed = false;
            Set<String> setAllowedOrigins = parseStringToSet(allowedOrigins);
            this.allowedOrigins.clear();
            this.allowedOrigins.addAll(setAllowedOrigins);
        }

        Set<String> setAllowedHttpMethods = parseStringToSet(allowedHttpMethods);
        this.allowedHttpMethods.clear();
        this.allowedHttpMethods.addAll(setAllowedHttpMethods);

        Set<String> setAllowedHttpHeaders = parseStringToSet(allowedHttpHeaders);
        Set<String> lowerCaseHeaders = new HashSet<>();
        for (String header : setAllowedHttpHeaders) {
            String lowerCase = header.toLowerCase(Locale.ENGLISH);
            lowerCaseHeaders.add(lowerCase);
        }
        this.allowedHttpHeaders.clear();
        this.allowedHttpHeaders.addAll(lowerCaseHeaders);

        Set<String> setExposedHeaders = parseStringToSet(exposedHeaders);
        this.exposedHeaders.clear();
        this.exposedHeaders.addAll(setExposedHeaders);

        // For any value other than 'true' this will be false.
        this.supportsCredentials = Boolean.parseBoolean(supportsCredentials);

        if (this.supportsCredentials && this.anyOriginAllowed) {
            throw new ServletException(sm.getString("corsFilter.invalidSupportsCredentials"));
        }

        try {
            if (!preflightMaxAge.isEmpty()) {
                this.preflightMaxAge = Long.parseLong(preflightMaxAge);
            } else {
                this.preflightMaxAge = 0L;
            }
        } catch (NumberFormatException e) {
            throw new ServletException(sm.getString("corsFilter.invalidPreflightMaxAge"), e);
        }

        // For any value other than 'true' this will be false.
        this.decorateRequest = Boolean.parseBoolean(decorateRequest);
    }

    /**
     * Takes a comma separated list and returns a Set&lt;String>.
     *
     * @param data A comma separated list of strings.
     *
     * @return Set$lt;String>
     */
    private Set<String> parseStringToSet(final String data) {
        String[] splits;

        if (data != null && data.length() > 0) {
            splits = data.split(",");
        } else {
            splits = new String[] {};
        }

        Set<String> set = new HashSet<>();
        if (splits.length > 0) {
            for (String split : splits) {
                set.add(split.trim());
            }
        }

        return set;
    }


    /**
     * Determines if any origin is allowed to make CORS request.
     *
     * @return <code>true</code> if it's enabled; false otherwise.
     */
    public boolean isAnyOriginAllowed() {
        return anyOriginAllowed;
    }


    /**
     * Obtain the headers to expose.
     *
     * @return the headers that should be exposed by browser.
     */
    public Collection<String> getExposedHeaders() {
        return exposedHeaders;
    }


    /**
     * Determines is supports credentials is enabled.
     *
     * @return <code>true</code> if the use of credentials is supported otherwise <code>false</code>
     */
    public boolean isSupportsCredentials() {
        return supportsCredentials;
    }


    /**
     * Returns the preflight response cache time in seconds.
     *
     * @return Time to cache in seconds.
     */
    public long getPreflightMaxAge() {
        return preflightMaxAge;
    }


    /**
     * Returns the {@link Set} of allowed origins that are allowed to make requests.
     *
     * @return {@link Set}
     */
    public Collection<String> getAllowedOrigins() {
        return allowedOrigins;
    }


    /**
     * Returns a {@link Set} of HTTP methods that are allowed to make requests.
     *
     * @return {@link Set}
     */
    public Collection<String> getAllowedHttpMethods() {
        return allowedHttpMethods;
    }


    /**
     * Returns a {@link Set} of headers support by resource.
     *
     * @return {@link Set}
     */
    public Collection<String> getAllowedHttpHeaders() {
        return allowedHttpHeaders;
    }


    /**
     * Should CORS specific attributes be added to the request.
     *
     * @return {@code true} if the request should be decorated, otherwise {@code false}
     */
    public boolean isDecorateRequest() {
        return decorateRequest;
    }


    /*
     * Log objects are not Serializable but this Filter is because it extends GenericFilter. Tomcat won't serialize a
     * Filter but in case something else does...
     */
    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        ois.defaultReadObject();
        log = LogFactory.getLog(CorsFilter.class);
    }


    // -------------------------------------------------- CORS Response Headers

    /**
     * The Access-Control-Allow-Origin header indicates whether a resource can be shared based by returning the value of
     * the Origin request header in the response.
     */
    public static final String RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";

    /**
     * The Access-Control-Allow-Credentials header indicates whether the response to request can be exposed when the
     * omit credentials flag is unset. When part of the response to a preflight request it indicates that the actual
     * request can include user credentials.
     */
    public static final String RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";

    /**
     * The Access-Control-Expose-Headers header indicates which headers are safe to expose to the API of a CORS API
     * specification
     */
    public static final String RESPONSE_HEADER_ACCESS_CONTROL_EXPOSE_HEADERS = "Access-Control-Expose-Headers";

    /**
     * The Access-Control-Max-Age header indicates how long the results of a preflight request can be cached in a
     * preflight result cache.
     */
    public static final String RESPONSE_HEADER_ACCESS_CONTROL_MAX_AGE = "Access-Control-Max-Age";

    /**
     * The Access-Control-Allow-Methods header indicates, as part of the response to a preflight request, which methods
     * can be used during the actual request.
     */
    public static final String RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";

    /**
     * The Access-Control-Allow-Headers header indicates, as part of the response to a preflight request, which header
     * field names can be used during the actual request.
     */
    public static final String RESPONSE_HEADER_ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";

    // -------------------------------------------------- CORS Request Headers

    /**
     * The Origin header indicates where the cross-origin request or preflight request originates from.
     */
    public static final String REQUEST_HEADER_ORIGIN = "Origin";

    /**
     * The Access-Control-Request-Method header indicates which method will be used in the actual request as part of the
     * preflight request.
     */
    public static final String REQUEST_HEADER_ACCESS_CONTROL_REQUEST_METHOD = "Access-Control-Request-Method";

    /**
     * The Access-Control-Request-Headers header indicates which headers will be used in the actual request as part of
     * the preflight request.
     */
    public static final String REQUEST_HEADER_ACCESS_CONTROL_REQUEST_HEADERS = "Access-Control-Request-Headers";

    // ----------------------------------------------------- Request attributes
    /**
     * The prefix to a CORS request attribute.
     */
    public static final String HTTP_REQUEST_ATTRIBUTE_PREFIX = "cors.";

    /**
     * Attribute that contains the origin of the request.
     */
    public static final String HTTP_REQUEST_ATTRIBUTE_ORIGIN = HTTP_REQUEST_ATTRIBUTE_PREFIX + "request.origin";

    /**
     * Boolean value, suggesting if the request is a CORS request or not.
     */
    public static final String HTTP_REQUEST_ATTRIBUTE_IS_CORS_REQUEST = HTTP_REQUEST_ATTRIBUTE_PREFIX + "isCorsRequest";

    /**
     * Type of CORS request, of type {@link CORSRequestType}.
     */
    public static final String HTTP_REQUEST_ATTRIBUTE_REQUEST_TYPE = HTTP_REQUEST_ATTRIBUTE_PREFIX + "request.type";

    /**
     * Request headers sent as 'Access-Control-Request-Headers' header, for pre-flight request.
     */
    public static final String HTTP_REQUEST_ATTRIBUTE_REQUEST_HEADERS =
            HTTP_REQUEST_ATTRIBUTE_PREFIX + "request.headers";

    // -------------------------------------------------------------- Constants
    /**
     * Enumerates varies types of CORS requests. Also, provides utility methods to determine the request type.
     */
    protected enum CORSRequestType {
        /**
         * A simple HTTP request, i.e. it shouldn't be pre-flighted.
         */
        SIMPLE,
        /**
         * An HTTP request that needs to be pre-flighted.
         */
        ACTUAL,
        /**
         * A pre-flight CORS request, to get meta information, before a non-simple HTTP request is sent.
         */
        PRE_FLIGHT,
        /**
         * Not a CORS request, but a normal request.
         */
        NOT_CORS,
        /**
         * An invalid CORS request, i.e. it qualifies to be a CORS request, but fails to be a valid one.
         */
        INVALID_CORS
    }

    /**
     * {@link Collection} of media type values for the Content-Type header that will be treated as 'simple'. Note
     * media-type values are compared ignoring parameters and in a case-insensitive manner.
     *
     * @see <a href="http://www.w3.org/TR/cors/#terminology" >http://www.w3.org/TR/cors/#terminology</a>
     */
    public static final Collection<String> SIMPLE_HTTP_REQUEST_CONTENT_TYPE_VALUES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(Globals.CONTENT_TYPE_FORM_URL_ENCODING, "multipart/form-data", "text/plain")));

    // ------------------------------------------------ Configuration Defaults
    /**
     * By default, no origins are allowed to make requests.
     */
    public static final String DEFAULT_ALLOWED_ORIGINS = "";

    /**
     * By default, following methods are supported: GET, POST, HEAD and OPTIONS.
     */
    public static final String DEFAULT_ALLOWED_HTTP_METHODS = "GET,POST,HEAD,OPTIONS";

    /**
     * By default, time duration to cache pre-flight response is 30 mins.
     */
    public static final String DEFAULT_PREFLIGHT_MAXAGE = "1800";

    /**
     * By default, support credentials is disabled.
     */
    public static final String DEFAULT_SUPPORTS_CREDENTIALS = "false";

    /**
     * By default, following headers are supported: Origin,Accept,X-Requested-With, Content-Type,
     * Access-Control-Request-Method, and Access-Control-Request-Headers.
     */
    public static final String DEFAULT_ALLOWED_HTTP_HEADERS = "Origin,Accept,X-Requested-With,Content-Type," +
            "Access-Control-Request-Method,Access-Control-Request-Headers";

    /**
     * By default, none of the headers are exposed in response.
     */
    public static final String DEFAULT_EXPOSED_HEADERS = "";

    /**
     * By default, request is decorated with CORS attributes.
     */
    public static final String DEFAULT_DECORATE_REQUEST = "true";

    // ----------------------------------------Filter Config Init param-name(s)
    /**
     * Key to retrieve allowed origins from {@link jakarta.servlet.FilterConfig}.
     */
    public static final String PARAM_CORS_ALLOWED_ORIGINS = "cors.allowed.origins";

    /**
     * Key to retrieve support credentials from {@link jakarta.servlet.FilterConfig}.
     */
    public static final String PARAM_CORS_SUPPORT_CREDENTIALS = "cors.support.credentials";

    /**
     * Key to retrieve exposed headers from {@link jakarta.servlet.FilterConfig}.
     */
    public static final String PARAM_CORS_EXPOSED_HEADERS = "cors.exposed.headers";

    /**
     * Key to retrieve allowed headers from {@link jakarta.servlet.FilterConfig}.
     */
    public static final String PARAM_CORS_ALLOWED_HEADERS = "cors.allowed.headers";

    /**
     * Key to retrieve allowed methods from {@link jakarta.servlet.FilterConfig}.
     */
    public static final String PARAM_CORS_ALLOWED_METHODS = "cors.allowed.methods";

    /**
     * Key to retrieve preflight max age from {@link jakarta.servlet.FilterConfig}.
     */
    public static final String PARAM_CORS_PREFLIGHT_MAXAGE = "cors.preflight.maxage";

    /**
     * Key to determine if request should be decorated.
     */
    public static final String PARAM_CORS_REQUEST_DECORATE = "cors.request.decorate";
}
