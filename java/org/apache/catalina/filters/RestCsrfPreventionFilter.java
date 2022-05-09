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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Provides basic CSRF protection for REST APIs. The filter assumes that the
 * clients have adapted the transfer of the nonce through the 'X-CSRF-Token'
 * header.
 *
 * <pre>
 * Positive scenario:
 *           Client                            Server
 *              |                                 |
 *              | GET Fetch Request              \| JSESSIONID
 *              |---------------------------------| X-CSRF-Token
 *              |                                /| pair generation
 *              |/Response to Fetch Request       |
 *              |---------------------------------|
 * JSESSIONID   |\                                |
 * X-CSRF-Token |                                 |
 * pair cached  | POST Request with valid nonce  \| JSESSIONID
 *              |---------------------------------| X-CSRF-Token
 *              |                                /| pair validation
 *              |/ Response to POST Request       |
 *              |---------------------------------|
 *              |\                                |
 *
 * Negative scenario:
 *           Client                            Server
 *              |                                 |
 *              | POST Request without nonce     \| JSESSIONID
 *              |---------------------------------| X-CSRF-Token
 *              |                                /| pair validation
 *              |/Request is rejected             |
 *              |---------------------------------|
 *              |\                                |
 *
 *           Client                            Server
 *              |                                 |
 *              | POST Request with invalid nonce\| JSESSIONID
 *              |---------------------------------| X-CSRF-Token
 *              |                                /| pair validation
 *              |/Request is rejected             |
 *              |---------------------------------|
 *              |\                                |
 * </pre>
 */
public class RestCsrfPreventionFilter extends CsrfPreventionFilterBase {
    private enum MethodType {
        NON_MODIFYING_METHOD, MODIFYING_METHOD
    }

    private static final Pattern NON_MODIFYING_METHODS_PATTERN = Pattern.compile("GET|HEAD|OPTIONS");
    private static final Predicate<String> nonModifyingMethods = m -> Objects.nonNull(m) &&
            NON_MODIFYING_METHODS_PATTERN.matcher(m).matches();

    private Set<String> pathsAcceptingParams = new HashSet<>();

    private String pathsDelimiter = ",";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Set the parameters
        super.init(filterConfig);

        // Put the expected request header name into the application scope
        filterConfig.getServletContext().setAttribute(
                Constants.CSRF_REST_NONCE_HEADER_NAME_KEY,
                Constants.CSRF_REST_NONCE_HEADER_NAME);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (request instanceof HttpServletRequest &&
                response instanceof HttpServletResponse) {
            MethodType mType = MethodType.MODIFYING_METHOD;
            if (nonModifyingMethods.test(((HttpServletRequest) request).getMethod())) {
                mType = MethodType.NON_MODIFYING_METHOD;
            }

            RestCsrfPreventionStrategy strategy;
            switch (mType) {
            case NON_MODIFYING_METHOD:
                strategy = new FetchRequest();
                break;
            default:
                strategy = new StateChangingRequest();
                break;
            }

            if (!strategy.apply((HttpServletRequest) request, (HttpServletResponse) response)) {
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static interface RestCsrfPreventionStrategy {
        static final NonceSupplier<HttpServletRequest, String> nonceFromRequestHeader = HttpServletRequest::getHeader;
        static final NonceSupplier<HttpServletRequest, String[]> nonceFromRequestParams = ServletRequest::getParameterValues;
        static final NonceSupplier<HttpSession, String> nonceFromSession = (s, k) -> Objects
                .isNull(s) ? null : (String) s.getAttribute(k);

        static final NonceConsumer<HttpServletResponse> nonceToResponse = HttpServletResponse::setHeader;
        static final NonceConsumer<HttpSession> nonceToSession = HttpSession::setAttribute;

        boolean apply(HttpServletRequest request, HttpServletResponse response) throws IOException;
    }

    private class StateChangingRequest implements RestCsrfPreventionStrategy {

        @Override
        public boolean apply(HttpServletRequest request, HttpServletResponse response)
                throws IOException {

            String nonceRequest = extractNonceFromRequest(request);
            HttpSession session = request.getSession(false);
            String nonceSession = nonceFromSession.getNonce(session, Constants.CSRF_REST_NONCE_SESSION_ATTR_NAME);

            if (isValidStateChangingRequest(nonceRequest, nonceSession)) {
                return true;
            }

            nonceToResponse.setNonce(response, Constants.CSRF_REST_NONCE_HEADER_NAME,
                    Constants.CSRF_REST_NONCE_HEADER_REQUIRED_VALUE);
            response.sendError(getDenyStatus(), sm.getString("restCsrfPreventionFilter.invalidNonce"));

            if (getLogger().isDebugEnabled()) {
                getLogger().debug(sm.getString("restCsrfPreventionFilter.invalidNonce.debug", request.getMethod(),
                        request.getRequestURI(), Boolean.valueOf(request.getRequestedSessionId() != null),
                        session, Boolean.valueOf(nonceRequest != null), Boolean.valueOf(nonceSession != null)));
            }
            return false;
        }

        private boolean isValidStateChangingRequest(String reqNonce, String sessionNonce) {
            return Objects.nonNull(reqNonce) && Objects.nonNull(sessionNonce)
                    && Objects.equals(reqNonce, sessionNonce);
        }

        private String extractNonceFromRequest(HttpServletRequest request) {
            String nonceFromRequest = nonceFromRequestHeader.getNonce(request,
                    Constants.CSRF_REST_NONCE_HEADER_NAME);
            if ((Objects.isNull(nonceFromRequest) || Objects.equals("", nonceFromRequest))
                    && !getPathsAcceptingParams().isEmpty()
                    && getPathsAcceptingParams().contains(getRequestedPath(request))) {
                nonceFromRequest = extractNonceFromRequestParams(request);
            }
            return nonceFromRequest;
        }

        private String extractNonceFromRequestParams(HttpServletRequest request) {
            String[] params = nonceFromRequestParams.getNonce(request, Constants.CSRF_REST_NONCE_HEADER_NAME);
            if (Objects.nonNull(params) && params.length > 0) {
                String nonce = params[0];
                for (String param : params) {
                    if (!Objects.equals(param, nonce)) {
                        if (getLogger().isDebugEnabled()) {
                            getLogger().debug(sm.getString("restCsrfPreventionFilter.multipleNonce.debug",
                                    request.getMethod(), request.getRequestURI()));
                        }
                        return null;
                    }
                }
                return nonce;
            }
            return null;
        }
    }

    private class FetchRequest implements RestCsrfPreventionStrategy {
        private final Predicate<String> fetchRequest = Constants.CSRF_REST_NONCE_HEADER_FETCH_VALUE::equalsIgnoreCase;

        @Override
        public boolean apply(HttpServletRequest request, HttpServletResponse response) {
            if (fetchRequest.test(
                    nonceFromRequestHeader.getNonce(request, Constants.CSRF_REST_NONCE_HEADER_NAME))) {
                String nonceFromSessionStr = nonceFromSession.getNonce(request.getSession(false),
                        Constants.CSRF_REST_NONCE_SESSION_ATTR_NAME);
                if (nonceFromSessionStr == null) {
                    nonceFromSessionStr = generateNonce(request);
                    nonceToSession.setNonce(Objects.requireNonNull(request.getSession(true)),
                            Constants.CSRF_REST_NONCE_SESSION_ATTR_NAME, nonceFromSessionStr);
                }
                nonceToResponse.setNonce(response, Constants.CSRF_REST_NONCE_HEADER_NAME,
                        nonceFromSessionStr);
                if (getLogger().isDebugEnabled()) {
                    getLogger().debug(sm.getString("restCsrfPreventionFilter.fetch.debug",
                            request.getMethod(), request.getRequestURI()));
                }
            }
            return true;
        }

    }

    @FunctionalInterface
    private static interface NonceSupplier<T, R> {
        R getNonce(T supplier, String key);
    }

    @FunctionalInterface
    private static interface NonceConsumer<T> {
        void setNonce(T consumer, String key, String value);
    }

    /**
     * A comma separated list of URLs that can accept nonces via request
     * parameter 'X-CSRF-Token'. For use cases when a nonce information cannot
     * be provided via header, one can provide it via request parameters. If
     * there is a X-CSRF-Token header, it will be taken with preference over any
     * parameter with the same name in the request. Request parameters cannot be
     * used to fetch new nonce, only header.
     *
     * @param pathsList
     *            Comma separated list of URLs to be configured as paths
     *            accepting request parameters with nonce information.
     */
    public void setPathsAcceptingParams(String pathsList) {
        if (Objects.nonNull(pathsList)) {
            Arrays.asList(pathsList.split(pathsDelimiter)).forEach(
                    e -> pathsAcceptingParams.add(e.trim()));
        }
    }

    public Set<String> getPathsAcceptingParams() {
        return pathsAcceptingParams;
    }
}
