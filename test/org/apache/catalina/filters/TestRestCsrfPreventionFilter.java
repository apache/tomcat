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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;

import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class TestRestCsrfPreventionFilter {

    private static final String NONCE = "nonce";

    private static final String INVALID_NONCE = "invalid-nonce";

    private static final String GET_METHOD = "GET";

    private static final String POST_METHOD = "POST";

    private RestCsrfPreventionFilter filter;

    private TesterRequest request;

    private TesterResponse response;

    private TesterFilterChain filterChain;

    private HttpSession session;

    @Before
    public void setUp() {
        filter = new RestCsrfPreventionFilter() {
            @Override
            protected String generateNonce() {
                return NONCE;
            }
        };
        request = new TesterRequest();
        response = new TesterResponse();
        filterChain = new TesterFilterChain();
        session = new TesterSession();
    }

    @Test
    public void testGetRequestNoSessionNoNonce() throws Exception {
        setRequestExpectations(GET_METHOD, null, null);
        filter.doFilter(request, response, filterChain);
        verifyContinueChain();
    }

    @Test
    public void testPostRequestNoSessionNoNonce() throws Exception {
        setRequestExpectations(POST_METHOD, null, null);
        filter.doFilter(request, response, filterChain);
        verifyDenyResponse(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    public void testPostRequestSessionNoNonce1() throws Exception {
        setRequestExpectations(POST_METHOD, session, null);
        filter.doFilter(request, response, filterChain);
        verifyDenyResponse(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    public void testPostRequestSessionNoNonce2() throws Exception {
        setRequestExpectations(POST_METHOD, session, null);
        session.setAttribute(Constants.CSRF_REST_NONCE_SESSION_ATTR_NAME, NONCE);
        filter.doFilter(request, response, filterChain);
        verifyDenyResponse(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    public void testPostRequestSessionInvalidNonce() throws Exception {
        setRequestExpectations(POST_METHOD, session, INVALID_NONCE);
        session.setAttribute(Constants.CSRF_REST_NONCE_SESSION_ATTR_NAME, NONCE);
        filter.doFilter(request, response, filterChain);
        verifyDenyResponse(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    public void testPostRequestSessionValidNonce() throws Exception {
        setRequestExpectations(POST_METHOD, session, NONCE);
        session.setAttribute(Constants.CSRF_REST_NONCE_SESSION_ATTR_NAME, NONCE);
        filter.doFilter(request, response, filterChain);
        verifyContinueChain();
    }

    @Test
    public void testGetFetchRequestSessionNoNonce() throws Exception {
        setRequestExpectations(GET_METHOD, session, Constants.CSRF_REST_NONCE_HEADER_FETCH_VALUE);
        filter.doFilter(request, response, filterChain);
        verifyContinueChainNonceAvailable();
    }

    @Test
    public void testPostFetchRequestSessionNoNonce() throws Exception {
        setRequestExpectations(POST_METHOD, session, Constants.CSRF_REST_NONCE_HEADER_FETCH_VALUE);
        filter.doFilter(request, response, filterChain);
        verifyDenyResponse(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    public void testGetFetchRequestSessionNonce() throws Exception {
        setRequestExpectations(GET_METHOD, session, Constants.CSRF_REST_NONCE_HEADER_FETCH_VALUE);
        session.setAttribute(Constants.CSRF_REST_NONCE_SESSION_ATTR_NAME, NONCE);
        filter.doFilter(request, response, filterChain);
        verifyContinueChainNonceAvailable();
    }

    @Test
    public void testPostFetchRequestSessionNonce() throws Exception {
        setRequestExpectations(POST_METHOD, session, Constants.CSRF_REST_NONCE_HEADER_FETCH_VALUE);
        session.setAttribute(Constants.CSRF_REST_NONCE_SESSION_ATTR_NAME, NONCE);
        filter.doFilter(request, response, filterChain);
        verifyDenyResponse(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    public void testPostRequestCustomDenyStatus() throws Exception {
        setRequestExpectations(POST_METHOD, null, null);
        filter.setDenyStatus(HttpServletResponse.SC_BAD_REQUEST);
        filter.doFilter(request, response, filterChain);
        verifyDenyResponse(HttpServletResponse.SC_BAD_REQUEST);
    }

    private void setRequestExpectations(String method, HttpSession session, String headerValue) {
        request.setMethod(method);
        request.setSession(session);
        request.setHeader(Constants.CSRF_REST_NONCE_HEADER_NAME, headerValue);
    }

    private void verifyContinueChain() {
        assertTrue(filterChain.isVisited());
    }

    private void verifyContinueChainNonceAvailable() {
        assertTrue(NONCE.equals(response.getHeader(Constants.CSRF_REST_NONCE_HEADER_NAME)));
        verifyContinueChain();
    }

    private void verifyDenyResponse(int statusCode) {
        assertTrue(Constants.CSRF_REST_NONCE_HEADER_REQUIRED_VALUE.equals(response
                .getHeader(Constants.CSRF_REST_NONCE_HEADER_NAME)));
        assertTrue(statusCode == response.getStatus());
        assertTrue(!filterChain.isVisited());
    }

    private static class TesterFilterChain implements FilterChain {
        private boolean visited = false;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException,
                ServletException {
            visited = true;
        }

        boolean isVisited() {
            return visited;
        }
    }

    private static class TesterRequest extends TesterHttpServletRequest {
        private HttpSession session;

        void setSession(HttpSession session) {
            this.session = session;
        }

        @Override
        public HttpSession getSession(boolean create) {
            return session;
        }
    }

    private static class TesterResponse extends TesterHttpServletResponse {
        @Override
        public void sendError(int status, String message) throws IOException {
            setStatus(status);
        }
    }

    private static class TesterSession implements HttpSession {
        Map<String,Object> attributes = new HashMap<String, Object>();
 
        @Override
        public long getCreationTime() {
            return 0;
        }

        @Override
        public String getId() {
            return null;
        }

        @Override
        public long getLastAccessedTime() {
            return 0;
        }

        @Override
        public ServletContext getServletContext() {
            return null;
        }

        @Override
        public void setMaxInactiveInterval(int interval) {
        }

        @Override
        public int getMaxInactiveInterval() {
            return 0;
        }

        @Override
        public HttpSessionContext getSessionContext() {
            return null;
        }

        @Override
        public Object getAttribute(String name) {
            return attributes.get(name);
        }

        @Override
        public Object getValue(String name) {
            return null;
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return null;
        }

        @Override
        public String[] getValueNames() {
            return null;
        }

        @Override
        public void setAttribute(String name, Object value) {
            attributes.put(name, value);
        }

        @Override
        public void putValue(String name, Object value) {
        }

        @Override
        public void removeAttribute(String name) {
        }

        @Override
        public void removeValue(String name) {
        }

        @Override
        public void invalidate() {
        }

        @Override
        public boolean isNew() {
            return false;
        }
        
    }
}
