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

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import org.easymock.EasyMock;

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
        session = EasyMock.createMock(HttpSession.class);
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
        EasyMock.expect(session.getAttribute(Constants.CSRF_REST_NONCE_SESSION_ATTR_NAME))
                .andReturn(null);
        EasyMock.replay(session);
        filter.doFilter(request, response, filterChain);
        verifyDenyResponse(HttpServletResponse.SC_FORBIDDEN);
        EasyMock.verify(session);
    }

    @Test
    public void testPostRequestSessionNoNonce2() throws Exception {
        setRequestExpectations(POST_METHOD, session, null);
        EasyMock.expect(session.getAttribute(Constants.CSRF_REST_NONCE_SESSION_ATTR_NAME))
                .andReturn(NONCE);
        EasyMock.replay(session);
        filter.doFilter(request, response, filterChain);
        verifyDenyResponse(HttpServletResponse.SC_FORBIDDEN);
        EasyMock.verify(session);
    }

    @Test
    public void testPostRequestSessionInvalidNonce() throws Exception {
        setRequestExpectations(POST_METHOD, session, INVALID_NONCE);
        EasyMock.expect(session.getAttribute(Constants.CSRF_REST_NONCE_SESSION_ATTR_NAME))
                .andReturn(NONCE);
        EasyMock.replay(session);
        filter.doFilter(request, response, filterChain);
        verifyDenyResponse(HttpServletResponse.SC_FORBIDDEN);
        EasyMock.verify(session);
    }

    @Test
    public void testPostRequestSessionValidNonce() throws Exception {
        setRequestExpectations(POST_METHOD, session, NONCE);
        EasyMock.expect(session.getAttribute(Constants.CSRF_REST_NONCE_SESSION_ATTR_NAME))
                .andReturn(NONCE);
        EasyMock.replay(session);
        filter.doFilter(request, response, filterChain);
        verifyContinueChain();
        EasyMock.verify(session);
    }

    @Test
    public void testGetFetchRequestSessionNoNonce() throws Exception {
        setRequestExpectations(GET_METHOD, session, Constants.CSRF_REST_NONCE_HEADER_FETCH_VALUE);
        EasyMock.expect(session.getAttribute(Constants.CSRF_REST_NONCE_SESSION_ATTR_NAME))
                .andReturn(null);
        session.setAttribute(Constants.CSRF_REST_NONCE_SESSION_ATTR_NAME, NONCE);
        EasyMock.expectLastCall();
        EasyMock.replay(session);
        filter.doFilter(request, response, filterChain);
        verifyContinueChainNonceAvailable();
        EasyMock.verify(session);
    }

    @Test
    public void testPostFetchRequestSessionNoNonce() throws Exception {
        setRequestExpectations(POST_METHOD, session, Constants.CSRF_REST_NONCE_HEADER_FETCH_VALUE);
        EasyMock.expect(session.getAttribute(Constants.CSRF_REST_NONCE_SESSION_ATTR_NAME))
                .andReturn(null);
        EasyMock.replay(session);
        filter.doFilter(request, response, filterChain);
        verifyDenyResponse(HttpServletResponse.SC_FORBIDDEN);
        EasyMock.verify(session);
    }

    @Test
    public void testGetFetchRequestSessionNonce() throws Exception {
        setRequestExpectations(GET_METHOD, session, Constants.CSRF_REST_NONCE_HEADER_FETCH_VALUE);
        EasyMock.expect(session.getAttribute(Constants.CSRF_REST_NONCE_SESSION_ATTR_NAME))
                .andReturn(NONCE);
        EasyMock.replay(session);
        filter.doFilter(request, response, filterChain);
        verifyContinueChainNonceAvailable();
        EasyMock.verify(session);
    }

    @Test
    public void testPostFetchRequestSessionNonce() throws Exception {
        setRequestExpectations(POST_METHOD, session, Constants.CSRF_REST_NONCE_HEADER_FETCH_VALUE);
        EasyMock.expect(session.getAttribute(Constants.CSRF_REST_NONCE_SESSION_ATTR_NAME))
                .andReturn(NONCE);
        EasyMock.replay(session);
        filter.doFilter(request, response, filterChain);
        verifyDenyResponse(HttpServletResponse.SC_FORBIDDEN);
        EasyMock.verify(session);
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
}
