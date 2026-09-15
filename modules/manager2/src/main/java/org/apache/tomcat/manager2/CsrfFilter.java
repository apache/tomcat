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
package org.apache.tomcat.manager2;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;


/**
 * CSRF protection filter for the Manager2 JSON API (synchronizer token pattern).
 * <p>
 * A 128-bit random token is generated per session and exposed to the client in the {@code X-CSRF-Token} response header
 * of every API response. State-changing requests (anything but GET, HEAD, OPTIONS and TRACE) must echo the token in the
 * {@code X-CSRF-Token} request header. Requests without a valid token are rejected with a 403.
 * <p>
 * As defence in depth, all mutation endpoints also require {@code Content-Type: application/json} (or a multipart
 * upload for the WAR upload endpoint) and the web application sets no CORS headers, so cross-origin requests are
 * additionally blocked by the browser same-origin policy.
 */
public class CsrfFilter implements Filter {


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = Strings.manager();

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int TOKEN_BYTES = 16;

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static final String[] SAFE_METHODS = { "GET", "HEAD", "OPTIONS", "TRACE" };


    private final Log log = LogFactory.getLog(CsrfFilter.class); // must not be static


    @Override
    public void init(FilterConfig filterConfig) {
        // Nothing to initialize
    }


    @Override
    public void destroy() {
        // Nothing to destroy
    }


    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        HttpSession session = req.getSession();
        String token = (String) session.getAttribute(Constants.CSRF_TOKEN_SESSION_KEY);
        if (token == null) {
            token = generateToken();
            session.setAttribute(Constants.CSRF_TOKEN_SESSION_KEY, token);
        }

        // Expose the token to the client. Header must be set before the
        // response is committed (i.e. before the servlet writes).
        resp.setHeader(Constants.CSRF_HEADER, token);

        if (isSafeMethod(req.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String provided = req.getHeader(Constants.CSRF_HEADER);
        if (provided == null || !constantTimeEquals(token, provided)) {
            log(req, sm.getString("csrfFilter.invalid"));
            Api.error(resp, HttpServletResponse.SC_FORBIDDEN, "CSRF", sm.getString("csrfFilter.invalid"));
            return;
        }

        chain.doFilter(request, response);
    }


    private static boolean isSafeMethod(String method) {
        for (String safe : SAFE_METHODS) {
            if (safe.equals(method)) {
                return true;
            }
        }
        return false;
    }


    private static boolean constantTimeEquals(String a, String b) {
        byte[] ab = a.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] bb = b.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(ab, bb);
    }


    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            chars[i * 2] = HEX[(bytes[i] >> 4) & 0xF];
            chars[i * 2 + 1] = HEX[bytes[i] & 0xF];
        }
        return new String(chars);
    }


    private void log(HttpServletRequest req, String message) {
        log.info(message + " method=[" + req.getMethod() + "] uri=[" + req.getRequestURI() + "] remoteAddr=[" +
                req.getRemoteAddr() + "] principal=[" + req.getUserPrincipal() + "]");
    }
}
