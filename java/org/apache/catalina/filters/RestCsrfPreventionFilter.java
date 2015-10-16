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
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Provides basic CSRF protection for REST APIs.
 * The filter assumes that:
 * <ul>
 * <li>The filter is mapped to /*</li>
 * <li>The clients have adapted the transfer of the nonce through the 'X-CSRF-Token' header.</li>
 * </ul>
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
    private static enum MethodType {
        NON_MODIFYING_METHOD, MODIFYING_METHOD
    }

    private static final Pattern NON_MODIFYING_METHODS_PATTERN = Pattern.compile("GET|HEAD|OPTIONS");
    private static final Predicate<String> nonModifyingMethods = m -> m != null &&
            NON_MODIFYING_METHODS_PATTERN.matcher(m).matches();

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
        static final NonceSupplier<HttpServletRequest> nonceFromRequest = (r, k) -> r.getHeader(k);
        static final NonceSupplier<HttpSession> nonceFromSession = (s, k) -> Objects.isNull(s) ? null
                : (String) s.getAttribute(k);

        static final NonceConsumer<HttpServletResponse> nonceToResponse = (r, k, v) -> r.setHeader(
                k, v);
        static final NonceConsumer<HttpSession> nonceToSession = (s, k, v) -> s.setAttribute(k, v);

        boolean apply(HttpServletRequest request, HttpServletResponse response) throws IOException;
    }

    private class StateChangingRequest implements RestCsrfPreventionStrategy {

        @Override
        public boolean apply(HttpServletRequest request, HttpServletResponse response)
                throws IOException {
            if (isValidStateChangingRequest(
                    nonceFromRequest.getNonce(request, Constants.CSRF_REST_NONCE_HEADER_NAME),
                    nonceFromSession.getNonce(request.getSession(false), Constants.CSRF_REST_NONCE_SESSION_ATTR_NAME))) {
                return true;
            }

            nonceToResponse.setNonce(response, Constants.CSRF_REST_NONCE_HEADER_NAME,
                    Constants.CSRF_REST_NONCE_HEADER_REQUIRED_VALUE);
            response.sendError(getDenyStatus(),
                    sm.getString("restCsrfPreventionFilter.invalidNonce"));
            return false;
        }

        private boolean isValidStateChangingRequest(String reqNonce, String sessionNonce) {
            return Objects.nonNull(reqNonce) && Objects.nonNull(sessionNonce)
                    && Objects.equals(reqNonce, sessionNonce);
        }
    }

    private class FetchRequest implements RestCsrfPreventionStrategy {
        private final Predicate<String> fetchRequest = s -> Constants.CSRF_REST_NONCE_HEADER_FETCH_VALUE
                .equalsIgnoreCase(s);

        @Override
        public boolean apply(HttpServletRequest request, HttpServletResponse response) {
            if (fetchRequest.test(
                    nonceFromRequest.getNonce(request, Constants.CSRF_REST_NONCE_HEADER_NAME))) {
                String nonceFromSessionStr = nonceFromSession.getNonce(request.getSession(false),
                        Constants.CSRF_REST_NONCE_SESSION_ATTR_NAME);
                if (nonceFromSessionStr == null) {
                    nonceFromSessionStr = generateNonce();
                    nonceToSession.setNonce(Objects.requireNonNull(request.getSession(true)),
                            Constants.CSRF_REST_NONCE_SESSION_ATTR_NAME, nonceFromSessionStr);
                }
                nonceToResponse.setNonce(response, Constants.CSRF_REST_NONCE_HEADER_NAME,
                        nonceFromSessionStr);
            }
            return true;
        }

    }

    @FunctionalInterface
    private static interface NonceSupplier<T> {
        String getNonce(T supplier, String key);
    }

    @FunctionalInterface
    private static interface NonceConsumer<T> {
        void setNonce(T consumer, String key, String value);
    }
}
